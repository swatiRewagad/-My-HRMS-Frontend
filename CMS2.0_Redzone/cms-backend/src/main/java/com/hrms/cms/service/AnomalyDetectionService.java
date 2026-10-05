package com.hrms.cms.service;

import com.hrms.cms.entity.SecurityAlert;
import com.hrms.cms.entity.SecurityEvent;
import com.hrms.cms.repository.SecurityAlertRepository;
import com.hrms.cms.repository.SecurityEventRepository;
import com.hrms.cms.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Counts security-relevant events and raises alerts when a tunable threshold is crossed (UST873).
 *
 * Before this, denied access was only ever written to a log line, so ten cross-entity probes and one
 * accidental misclick were indistinguishable. Events are now persisted and counted per subject.
 *
 * Every threshold and window comes from SYSTEM_CONFIG: an operator tightening limits during an
 * incident must not need a redeploy.
 *
 * Recording runs in its own transaction. Detection is observability, not the operation the user
 * asked for — a failure to record must not roll back the caller's request, and a rolled-back
 * request must still leave its denial recorded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    public static final String EVENT_CROSS_ENTITY = "CROSS_ENTITY_ACCESS_DENIED";
    public static final String EVENT_ACCESS_DENIED = "ACCESS_DENIED";
    public static final String EVENT_PII_REVEAL = "PII_REVEALED";
    public static final String EVENT_PII_REVEAL_DENIED = "PII_REVEAL_DENIED";
    public static final String EVENT_OWNERSHIP_DENIED = "OWNERSHIP_CHECK_DENIED";

    public static final String CFG_ENABLED = "cms.security.anomaly.enabled";
    public static final String CFG_WINDOW_MINUTES = "cms.security.anomaly.window_minutes";
    public static final String CFG_DENIED_THRESHOLD = "cms.security.anomaly.denied_access_threshold";
    public static final String CFG_CROSS_ENTITY_THRESHOLD = "cms.security.anomaly.cross_entity_threshold";
    public static final String CFG_REVEAL_THRESHOLD = "cms.security.anomaly.pii_reveal_threshold";
    public static final String CFG_DISTINCT_ENTITY_THRESHOLD = "cms.security.anomaly.distinct_entity_threshold";
    public static final String CFG_ALERT_SUPPRESSION_MINUTES = "cms.security.anomaly.alert_suppression_minutes";

    private final SecurityEventRepository securityEventRepository;
    private final SecurityAlertRepository securityAlertRepository;
    private final SystemConfigService systemConfigService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCrossEntityDenial(RequestIdentity identity, String attemptedEntityCode,
                                        String complaintNumber, HttpServletRequest request) {
        SecurityEvent event = baseEvent(EVENT_CROSS_ENTITY, identity, request);
        event.setComplaintNumber(complaintNumber);
        event.setEntityCode(attemptedEntityCode);
        event.setDetails("Caller scoped to " + (identity == null ? "unknown" : identity.getEntityCode())
                + " attempted access to " + attemptedEntityCode);
        record(event);

        evaluate(event.getSubject(), EVENT_CROSS_ENTITY, CFG_CROSS_ENTITY_THRESHOLD, 3,
                "CROSS_ENTITY_PROBING", "HIGH", event.getIpAddress());
        evaluateDistinctEntities(event.getSubject(), event.getIpAddress());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAccessDenied(RequestIdentity identity, String reason, HttpServletRequest request) {
        SecurityEvent event = baseEvent(EVENT_ACCESS_DENIED, identity, request);
        event.setDetails(reason);
        record(event);

        evaluate(event.getSubject(), EVENT_ACCESS_DENIED, CFG_DENIED_THRESHOLD, 10,
                "REPEATED_ACCESS_DENIED", "MEDIUM", event.getIpAddress());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOwnershipDenial(String actor, String complaintNumber, String reason,
                                      HttpServletRequest request) {
        SecurityEvent event = SecurityEvent.builder()
                .eventType(EVENT_OWNERSHIP_DENIED)
                .subject(actor == null ? resolveIp(request) : actor)
                .userId(actor)
                .complaintNumber(complaintNumber)
                .requestPath(request == null ? null : request.getRequestURI())
                .ipAddress(resolveIp(request))
                .details(reason)
                .occurredAt(LocalDateTime.now())
                .build();
        record(event);

        evaluate(event.getSubject(), EVENT_OWNERSHIP_DENIED, CFG_DENIED_THRESHOLD, 10,
                "REPEATED_OWNERSHIP_DENIAL", "MEDIUM", event.getIpAddress());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPiiReveal(RequestIdentity identity, String complaintNumber, HttpServletRequest request) {
        SecurityEvent event = baseEvent(EVENT_PII_REVEAL, identity, request);
        event.setComplaintNumber(complaintNumber);
        record(event);

        // Bulk reveals are the signature of harvesting rather than casework, so this is counted even
        // though each individual reveal was authorised.
        evaluate(event.getSubject(), EVENT_PII_REVEAL, CFG_REVEAL_THRESHOLD, 25,
                "HIGH_VOLUME_PII_REVEAL", "MEDIUM", event.getIpAddress());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeniedPiiReveal(RequestIdentity identity, String complaintNumber,
                                      HttpServletRequest request) {
        SecurityEvent event = baseEvent(EVENT_PII_REVEAL_DENIED, identity, request);
        event.setComplaintNumber(complaintNumber);
        event.setDetails("Reveal requested without a permitted role");
        record(event);

        evaluate(event.getSubject(), EVENT_PII_REVEAL_DENIED, CFG_CROSS_ENTITY_THRESHOLD, 3,
                "UNAUTHORISED_PII_REVEAL_ATTEMPTS", "HIGH", event.getIpAddress());
    }

    private SecurityEvent baseEvent(String type, RequestIdentity identity, HttpServletRequest request) {
        String ip = resolveIp(request);
        return SecurityEvent.builder()
                .eventType(type)
                // An unauthenticated caller has no user id, so the IP becomes the subject; without a
                // subject the event could not be counted at all.
                .subject(identity == null ? ip : identity.getUserId())
                .userId(identity == null ? null : identity.getUserId())
                .entityCode(identity == null ? null : identity.getEntityCode())
                .requestPath(request == null ? null : request.getRequestURI())
                .ipAddress(ip)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private void record(SecurityEvent event) {
        if (!systemConfigService.getBoolean(CFG_ENABLED, true)) {
            return;
        }
        if (event.getSubject() == null) {
            event.setSubject("UNKNOWN");
        }
        try {
            securityEventRepository.save(event);
        } catch (Exception e) {
            log.error("Could not record security event {} for {}: {}",
                    event.getEventType(), event.getSubject(), e.getMessage());
        }
    }

    private void evaluate(String subject, String eventType, String thresholdKey, int thresholdDefault,
                          String alertType, String severity, String ip) {
        if (!systemConfigService.getBoolean(CFG_ENABLED, true) || subject == null) {
            return;
        }
        int threshold = systemConfigService.getInt(thresholdKey, thresholdDefault);
        int windowMinutes = systemConfigService.getInt(CFG_WINDOW_MINUTES, 10);
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);

        long count = securityEventRepository
                .countBySubjectAndEventTypeAndOccurredAtAfter(subject, eventType, since);
        if (count < threshold) {
            return;
        }
        raise(alertType, severity, subject, (int) count, threshold,
                windowMinutes + " minutes",
                count + " " + eventType + " events within " + windowMinutes + " minutes", ip);
    }

    /**
     * A caller touching several entities in one window looks like enumeration even when each
     * individual denial is below the per-type threshold.
     */
    private void evaluateDistinctEntities(String subject, String ip) {
        int threshold = systemConfigService.getInt(CFG_DISTINCT_ENTITY_THRESHOLD, 3);
        int windowMinutes = systemConfigService.getInt(CFG_WINDOW_MINUTES, 10);
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);

        long distinct = securityEventRepository.countDistinctEntityCodes(subject, since);
        if (distinct < threshold) {
            return;
        }
        raise("MULTI_ENTITY_ENUMERATION", "HIGH", subject, (int) distinct, threshold,
                windowMinutes + " minutes",
                "Attempted access to " + distinct + " distinct entities within " + windowMinutes + " minutes", ip);
    }

    /**
     * Raises an alert for a violation that was OBSERVED DIRECTLY, rather than inferred from a count
     * of events crossing a threshold.
     *
     * <p>WHY THIS IS SEPARATE from the threshold evaluators above: those exist because no single
     * denial is conclusive, so they look for a pattern over a window. An attempt to permanently
     * destroy a record inside its statutory retention period needs no corroboration — one is already
     * the whole event — so it is reported with a count and threshold of 1 rather than being made to
     * wait for a second occurrence that must never be allowed to happen.
     *
     * <p>Suppression still applies, keyed on subject + alert type, so a script hammering the delete
     * endpoint cannot bury the console it is meant to inform. The AUDIT_LOG row written by the
     * refusing service is the per-attempt record; this is the signal that someone should look.
     *
     * <p>REQUIRES_NEW, like every other entry point on this class, and here it is load-bearing rather
     * than conventional: the caller is about to THROW the refusal. An alert written in the caller's
     * transaction would be rolled back by the very exception it exists to report, leaving no alert —
     * observed exactly that way before this annotation was added.
     *
     * @param subject who or what the alert is about — a complaint number for a retention breach
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void raiseDirectViolation(String alertType, String severity, String subject,
                                     String details) {
        raise(alertType, severity, subject, 1, 1, "single occurrence", details, null);
    }

    private void raise(String alertType, String severity, String subject, int count, int threshold,
                       String windowLabel, String details, String ip) {
        int suppressionMinutes = systemConfigService.getInt(CFG_ALERT_SUPPRESSION_MINUTES, 60);
        LocalDateTime suppressAfter = LocalDateTime.now().minusMinutes(suppressionMinutes);

        // Without suppression a sustained attack would create one alert per request and bury the
        // console it is meant to inform.
        if (securityAlertRepository.existsBySubjectAndAlertTypeAndRaisedAtAfter(subject, alertType, suppressAfter)) {
            return;
        }

        try {
            securityAlertRepository.save(SecurityAlert.builder()
                    .alertType(alertType)
                    .severity(severity)
                    .subject(subject)
                    .subjectType(subject != null && subject.contains(".") && subject.matches("[0-9.:a-fA-F]+")
                            ? "IP" : "USER")
                    .eventCount(count)
                    .threshold(threshold)
                    .windowLabel(windowLabel)
                    .details(details)
                    .ipAddress(ip)
                    .status("OPEN")
                    .raisedAt(LocalDateTime.now())
                    .build());
            log.warn("SECURITY ALERT {} [{}] subject={} count={} threshold={}",
                    alertType, severity, subject, count, threshold);
        } catch (Exception e) {
            log.error("Could not raise security alert {} for {}: {}", alertType, subject, e.getMessage());
        }
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return (realIp != null && !realIp.isBlank()) ? realIp : request.getRemoteAddr();
    }
}
