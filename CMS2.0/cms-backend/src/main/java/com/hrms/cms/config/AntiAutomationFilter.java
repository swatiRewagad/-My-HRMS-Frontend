package com.hrms.cms.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@Order(2)
public class AntiAutomationFilter implements Filter {

    @Value("${cms.anti-automation.velocity-window-seconds:60}")
    private int velocityWindowSeconds;

    @Value("${cms.anti-automation.velocity-threshold:30}")
    private int velocityThreshold;

    @Value("${cms.anti-automation.min-form-submission-ms:3000}")
    private long minFormSubmissionMs;

    @Value("${cms.anti-automation.require-standard-headers:true}")
    private boolean requireStandardHeaders;

    private final Map<String, RequestCounter> requestCounters = new ConcurrentHashMap<>();

    private static final String FORM_START_HEADER = "X-Form-Start";
    private static final String HONEYPOT_HEADER = "X-HP-Field";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        if (!isProtectedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);
        AnomalyResult anomaly = detectAnomaly(httpRequest, clientIp);

        if (anomaly.blocked()) {
            log.warn("Anti-automation block: IP={} reason={} path={} UA={}",
                    clientIp, anomaly.reason(), path,
                    httpRequest.getHeader("User-Agent"));
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"error\":\"SUSPICIOUS_ACTIVITY\",\"message\":\"Request blocked. Please complete the CAPTCHA.\",\"requireCaptcha\":true}");
            return;
        }

        if (anomaly.flagged()) {
            log.info("Anti-automation flag: IP={} reason={} path={}",
                    clientIp, anomaly.reason(), path);
            httpResponse.setHeader("X-Require-Captcha", "true");
        }

        chain.doFilter(request, response);
    }

    private AnomalyResult detectAnomaly(HttpServletRequest request, String clientIp) {
        if (checkHoneypot(request)) {
            return new AnomalyResult(true, false, "HONEYPOT_TRIGGERED");
        }

        if (checkTimingAnomaly(request)) {
            return new AnomalyResult(false, true, "TOO_FAST_SUBMISSION");
        }

        if (requireStandardHeaders && checkHeaderAnomaly(request)) {
            return new AnomalyResult(false, true, "MISSING_STANDARD_HEADERS");
        }

        if (checkVelocityAnomaly(clientIp)) {
            return new AnomalyResult(true, false, "VELOCITY_EXCEEDED");
        }

        return new AnomalyResult(false, false, null);
    }

    private boolean checkHoneypot(HttpServletRequest request) {
        String honeypot = request.getHeader(HONEYPOT_HEADER);
        return honeypot != null && !honeypot.isBlank();
    }

    private boolean checkTimingAnomaly(HttpServletRequest request) {
        String formStart = request.getHeader(FORM_START_HEADER);
        if (formStart == null || formStart.isBlank()) return false;

        try {
            long startTime = Long.parseLong(formStart);
            long elapsed = System.currentTimeMillis() - startTime;
            return elapsed < minFormSubmissionMs;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean checkHeaderAnomaly(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String accept = request.getHeader("Accept");
        String acceptLanguage = request.getHeader("Accept-Language");

        if (userAgent == null || userAgent.isBlank()) return true;
        if (accept == null || accept.isBlank()) return true;

        String uaLower = userAgent.toLowerCase();
        if (uaLower.contains("curl") || uaLower.contains("wget") ||
            uaLower.contains("python-requests") || uaLower.contains("httpie") ||
            uaLower.contains("postman")) {
            return true;
        }

        return false;
    }

    private boolean checkVelocityAnomaly(String clientIp) {
        long now = Instant.now().getEpochSecond();
        RequestCounter counter = requestCounters.computeIfAbsent(clientIp, k -> new RequestCounter());

        if (now - counter.windowStart.get() > velocityWindowSeconds) {
            counter.count.set(1);
            counter.windowStart.set(now);
            return false;
        }

        int currentCount = counter.count.incrementAndGet();
        return currentCount > velocityThreshold;
    }

    private boolean isProtectedPath(String path) {
        return path.startsWith("/api/v1/citizen/") ||
               path.startsWith("/api/v1/complaints") ||
               path.startsWith("/api/complaints");
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

    private record AnomalyResult(boolean blocked, boolean flagged, String reason) {}

    private static class RequestCounter {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong windowStart = new AtomicLong(Instant.now().getEpochSecond());
    }
}
