package com.hrms.cms.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final int MAX_BUCKETS = 50_000;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 600_000)
    void evictBuckets() {
        if (buckets.size() > MAX_BUCKETS) {
            buckets.clear();
        }
    }

    @Value("${cms.rate-limit.api-requests-per-second:100}")
    private int requestsPerSecond;

    /**
     * The assistance rail's own allowance, per Brief 21 §6.2 ("Assistance gets its own, smaller
     * bucket"), so an ambient feature polling in the background can never starve the core traffic an
     * officer is waiting on.
     *
     * <p>Per MINUTE rather than per second, because the rail is event-driven, not sustained: a screen
     * open costs a {@code /status}, a {@code /rail} and later one {@code /rail/memory}, so a per-second
     * ceiling would have to be a fraction and would reject a legitimate screen load. Sixty is roughly
     * twenty screens a minute — well past human pace — while remaining 1/50th of the core
     * allowance's 3,000, which is the ratio that makes the separation mean something.
     */
    @Value("${cms.rate-limit.assistance-requests-per-minute:60}")
    private int assistanceRequestsPerMinute;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        boolean assistance = path.startsWith("/api/v1/assistance/");
        // Prefixed into the SAME map so the MAX_BUCKETS eviction keeps bounding total memory. A second
        // map would double the ceiling silently.
        String key = assistance ? "assist|" + clientIp : clientIp;
        Bucket bucket = buckets.computeIfAbsent(key,
                k -> assistance ? createAssistanceBucket() : createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
        }
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(requestsPerSecond, Duration.ofSeconds(1)))
                .addLimit(Bandwidth.simple(requestsPerSecond * 30L, Duration.ofMinutes(1)))
                .build();
    }

    /**
     * The assistance bucket: one limit, not two.
     *
     * <p>The core bucket pairs a per-second ceiling with a per-minute one to catch both a burst and a
     * sustained flood. Here the per-minute limit alone does both jobs — 60 tokens cannot be a flood at
     * any rate — and a second per-second limit would only reject the three-call burst of a single
     * screen load, which is exactly the traffic this feature is supposed to serve.
     *
     * <p>Exhausting it degrades the rail to no bulb, which is the §5.1 silent-failure contract already
     * in force for a transport error. That is the reason a bucket this small is safe to impose on a
     * staff screen: the officer loses an ambient hint, never the screen.
     */
    private Bucket createAssistanceBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(assistanceRequestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
