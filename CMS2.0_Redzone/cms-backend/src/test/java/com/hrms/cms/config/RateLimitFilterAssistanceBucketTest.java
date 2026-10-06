package com.hrms.cms.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The assistance rail's separate rate-limit bucket (Brief 21 §6.2).
 *
 * <p>The property under test is ISOLATION, not the numbers: exhausting the rail's allowance must not
 * consume any of the core allowance, and vice versa. The limits are configuration and will be tuned;
 * the separation is the guarantee, because the brief's reason for asking for a second bucket is that
 * an ambient background feature must not be able to starve the traffic an officer is waiting on.
 *
 * <p>Deliberate choice of small numbers via reflection rather than the real defaults: a test that
 * actually issued 3,000 requests to prove the core limit would be slow and would prove the same thing.
 */
class RateLimitFilterAssistanceBucketTest {

    private static final String ASSISTANCE_PATH = "/api/v1/assistance/rail";
    private static final String CORE_PATH = "/api/v1/complaints";
    private static final String IP = "203.0.113.7";

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setup() throws Exception {
        filter = new RateLimitFilter();
        set("requestsPerSecond", 100);
        set("assistanceRequestsPerMinute", 3);
        chain = mock(FilterChain.class);
    }

    private void set(String field, int value) throws Exception {
        var f = RateLimitFilter.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(filter, value);
    }

    /** @return the response status after one call through the filter */
    private int call(String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response.getStatus();
    }

    @Nested
    @DisplayName("The assistance bucket is separate from the core bucket")
    class Isolation {

        @Test
        @DisplayName("exhausting assistance leaves core traffic untouched")
        void exhaustingAssistanceDoesNotBlockCore() throws Exception {
            for (int i = 0; i < 3; i++) {
                assertThat(call(ASSISTANCE_PATH, IP)).isEqualTo(200);
            }
            assertThat(call(ASSISTANCE_PATH, IP)).isEqualTo(429);

            // The whole point of the second bucket. A rail polling in the background must never be
            // able to cost an officer the screen they are waiting on.
            assertThat(call(CORE_PATH, IP)).isEqualTo(200);
        }

        @Test
        @DisplayName("core traffic does not consume the assistance allowance")
        void coreDoesNotConsumeAssistance() throws Exception {
            for (int i = 0; i < 50; i++) {
                assertThat(call(CORE_PATH, IP)).isEqualTo(200);
            }
            // Still has its full allowance: 50 core calls took nothing from it.
            for (int i = 0; i < 3; i++) {
                assertThat(call(ASSISTANCE_PATH, IP)).isEqualTo(200);
            }
        }

        @Test
        @DisplayName("the assistance bucket is per client IP, like the core one")
        void bucketIsPerClient() throws Exception {
            for (int i = 0; i < 3; i++) {
                call(ASSISTANCE_PATH, IP);
            }
            assertThat(call(ASSISTANCE_PATH, IP)).isEqualTo(429);

            // A second officer behind a different address is unaffected. Were the bucket global, one
            // busy user would disable the rail for the whole office.
            assertThat(call(ASSISTANCE_PATH, "198.51.100.4")).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Which paths the assistance bucket covers")
    class PathMatching {

        @Test
        @DisplayName("all three assistance endpoints share the one bucket")
        void allAssistanceEndpointsShareTheBucket() throws Exception {
            assertThat(call("/api/v1/assistance/status", IP)).isEqualTo(200);
            assertThat(call("/api/v1/assistance/rail", IP)).isEqualTo(200);
            assertThat(call("/api/v1/assistance/rail/memory", IP)).isEqualTo(200);

            // Three calls, one allowance of three: the fourth is refused regardless of which endpoint
            // it is. One screen load costs exactly these three, which is why the limit is per minute.
            assertThat(call("/api/v1/assistance/status", IP)).isEqualTo(429);
        }

        @Test
        @DisplayName("a path merely containing 'assistance' is NOT given the smaller bucket")
        void onlyTheAssistanceNamespaceMatches() throws Exception {
            // Guards against a substring match. The namespace is a prefix, and a core endpoint that
            // happened to mention the word must keep the core allowance.
            for (int i = 0; i < 10; i++) {
                assertThat(call("/api/v1/complaints/assistance-requests", IP)).isEqualTo(200);
            }
        }

        @Test
        @DisplayName("non-API paths bypass both buckets")
        void nonApiPathsAreNotLimited() throws Exception {
            for (int i = 0; i < 10; i++) {
                assertThat(call("/actuator/health", IP)).isEqualTo(200);
            }
            verify(chain, times(10)).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("What a refusal looks like")
    class Refusal {

        @Test
        @DisplayName("a throttled assistance call does not reach the chain")
        void throttledCallDoesNotReachTheChain() throws Exception {
            for (int i = 0; i < 3; i++) {
                call(ASSISTANCE_PATH, IP);
            }
            reset(chain);

            assertThat(call(ASSISTANCE_PATH, IP)).isEqualTo(429);
            // The saving is the point: a refused rail read must cost no query, or the bucket would
            // limit only the response and not the load it exists to shed.
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("the 429 body is the same shape as the core bucket's")
        void refusalBodyIsUnchanged() throws Exception {
            for (int i = 0; i < 3; i++) {
                call(ASSISTANCE_PATH, IP);
            }

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(ASSISTANCE_PATH);
            request.setRemoteAddr(IP);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);

            // One shape for both buckets. The frontend degrades any non-200 to a hidden bulb, so a
            // bespoke body here would be a second thing to maintain that nothing reads.
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getContentType()).isEqualTo("application/json");
            assertThat(response.getContentAsString()).contains("Too many requests");
        }
    }
}
