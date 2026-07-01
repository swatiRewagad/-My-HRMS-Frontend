package com.hrms.cms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AntiAutomationFilterTest {

    private AntiAutomationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setup() throws Exception {
        filter = new AntiAutomationFilter();

        var velocityField = AntiAutomationFilter.class.getDeclaredField("velocityWindowSeconds");
        velocityField.setAccessible(true);
        velocityField.setInt(filter, 60);

        var thresholdField = AntiAutomationFilter.class.getDeclaredField("velocityThreshold");
        thresholdField.setAccessible(true);
        thresholdField.setInt(filter, 30);

        var minFormField = AntiAutomationFilter.class.getDeclaredField("minFormSubmissionMs");
        minFormField.setAccessible(true);
        minFormField.setLong(filter, 3000L);

        var headersField = AntiAutomationFilter.class.getDeclaredField("requireStandardHeaders");
        headersField.setAccessible(true);
        headersField.setBoolean(filter, true);

        filterChain = mock(FilterChain.class);
    }

    @Nested
    @DisplayName("Honeypot detection")
    class Honeypot {

        @Test
        @DisplayName("should block when honeypot header is filled")
        void shouldBlockHoneypot() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/citizen/auth/send-otp");
            request.addHeader("X-HP-Field", "bot-filled-value");
            request.addHeader("User-Agent", "Mozilla/5.0");
            request.addHeader("Accept", "application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(429);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should pass when honeypot header is absent")
        void shouldPassWithoutHoneypot() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/citizen/auth/send-otp");
            request.addHeader("User-Agent", "Mozilla/5.0");
            request.addHeader("Accept", "application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Timing detection")
    class Timing {

        @Test
        @DisplayName("should flag when form submitted too fast")
        void shouldFlagTooFast() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/complaints");
            request.addHeader("X-Form-Start", String.valueOf(System.currentTimeMillis() - 500));
            request.addHeader("User-Agent", "Mozilla/5.0");
            request.addHeader("Accept", "application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader("X-Require-Captcha")).isEqualTo("true");
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should pass when form took normal time")
        void shouldPassNormalTiming() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/complaints");
            request.addHeader("X-Form-Start", String.valueOf(System.currentTimeMillis() - 30000));
            request.addHeader("User-Agent", "Mozilla/5.0");
            request.addHeader("Accept", "application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader("X-Require-Captcha")).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Header anomaly detection")
    class HeaderAnomaly {

        @Test
        @DisplayName("should flag requests with no User-Agent")
        void shouldFlagNoUserAgent() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/citizen/auth/send-otp");
            request.addHeader("Accept", "application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader("X-Require-Captcha")).isEqualTo("true");
        }

        @Test
        @DisplayName("should flag requests from known automation tools")
        void shouldFlagAutomationTools() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/citizen/auth/send-otp");
            request.addHeader("User-Agent", "python-requests/2.28.0");
            request.addHeader("Accept", "application/json");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader("X-Require-Captcha")).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("Velocity detection")
    class Velocity {

        @Test
        @DisplayName("should block after exceeding velocity threshold")
        void shouldBlockHighVelocity() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/citizen/auth/captcha");
            request.addHeader("User-Agent", "Mozilla/5.0");
            request.addHeader("Accept", "application/json");
            request.setRemoteAddr("10.0.0.1");

            for (int i = 0; i < 31; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, filterChain);
            }

            MockHttpServletResponse finalResponse = new MockHttpServletResponse();
            filter.doFilter(request, finalResponse, filterChain);

            assertThat(finalResponse.getStatus()).isEqualTo(429);
        }
    }

    @Nested
    @DisplayName("Non-protected paths")
    class NonProtected {

        @Test
        @DisplayName("should not apply to non-citizen paths")
        void shouldSkipNonProtected() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/dashboard");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
