package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RevealAuditEntry;
import com.hrms.cms.repository.RevealAuditEntryRepository;
import com.hrms.cms.security.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serves an explicit, audited PII reveal (UST875).
 *
 * The audit row is written before the values are returned, so a reveal cannot be served without
 * leaving a trace: if persisting the record fails the caller gets an error instead of the PII.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PiiRevealService {

    public static final String CFG_REQUIRE_JUSTIFICATION = "cms.security.pii.require_justification";
    public static final String CFG_MIN_JUSTIFICATION_LENGTH = "cms.security.pii.min_justification_length";

    private final PiiMaskingService piiMaskingService;
    private final RevealAuditEntryRepository revealAuditEntryRepository;
    private final SystemConfigService systemConfigService;
    private final AnomalyDetectionService anomalyDetectionService;

    /** Thrown when the caller may not reveal; the controller maps this to 403. */
    public static class RevealNotPermittedException extends RuntimeException {
        public RevealNotPermittedException(String message) {
            super(message);
        }
    }

    /** Thrown when a required justification is missing or too short; maps to 400. */
    public static class JustificationRequiredException extends RuntimeException {
        public JustificationRequiredException(String message) {
            super(message);
        }
    }

    @Transactional
    public Map<String, Object> reveal(Complaint complaint,
                                      RequestIdentity identity,
                                      String justification,
                                      String context,
                                      HttpServletRequest request) {
        if (!piiMaskingService.canReveal(identity)) {
            // A refused reveal is itself a signal: it means someone without the role asked for PII.
            anomalyDetectionService.recordDeniedPiiReveal(identity, complaint.getComplaintNumber(), request);
            throw new RevealNotPermittedException("You are not permitted to reveal complainant details.");
        }

        if (systemConfigService.getBoolean(CFG_REQUIRE_JUSTIFICATION, true)) {
            int minLength = systemConfigService.getInt(CFG_MIN_JUSTIFICATION_LENGTH, 10);
            if (justification == null || justification.trim().length() < minLength) {
                throw new JustificationRequiredException(
                        "A reason of at least " + minLength + " characters is required to reveal complainant details.");
            }
        }

        Set<String> fields = piiMaskingService.maskedFields();

        Map<String, Object> revealed = new LinkedHashMap<>();
        for (String field : fields) {
            revealed.put(field, valueOf(complaint, field));
        }

        revealAuditEntryRepository.save(RevealAuditEntry.builder()
                .userId(identity.getUserId())
                .displayName(identity.getDisplayName())
                .roles(identity.getRoles() == null ? null
                        : identity.getRoles().stream().sorted().collect(Collectors.joining(",")))
                .complaintNumber(complaint.getComplaintNumber())
                .fieldsRevealed(String.join(",", fields))
                .context(context)
                .justification(justification == null ? null : justification.trim())
                .ipAddress(resolveIp(request))
                .revealedAt(LocalDateTime.now())
                .build());

        log.info("PII revealed: user={} complaint={} fields={}",
                identity.getUserId(), complaint.getComplaintNumber(), fields);

        anomalyDetectionService.recordPiiReveal(identity, complaint.getComplaintNumber(), request);

        return revealed;
    }

    private String valueOf(Complaint c, String field) {
        return switch (field) {
            case "complainantName" -> c.getComplainantName();
            case "complainantPhone" -> c.getComplainantPhone();
            case "complainantEmail" -> c.getComplainantEmail();
            case "complainantAddress" -> c.getComplainantAddress();
            case "accountNumber" -> c.getAccountNumber();
            case "repName" -> c.getRepName();
            case "repPhone" -> c.getRepPhone();
            case "repEmail" -> c.getRepEmail();
            default -> null;
        };
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
