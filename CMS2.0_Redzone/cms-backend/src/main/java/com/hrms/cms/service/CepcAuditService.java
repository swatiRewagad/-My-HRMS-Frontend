package com.hrms.cms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.AuditLog;
import com.hrms.cms.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Immutable audit logging service for CEPC workflow transitions.
 * Separate from the user-facing ComplaintTimeline — this captures
 * full actor/role/IP/state-change metadata for compliance and forensics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CepcAuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Log a workflow action with full metadata.
     */
    @Transactional
    public AuditLog logAction(String complaintNumber, String action, String actor,
                              String role, String remarks, Map<String, Object> metadata) {
        return logAction(complaintNumber, action, actor, role, remarks, metadata, null, null);
    }

    /**
     * Log a workflow action with full metadata including state transition.
     */
    @Transactional
    public AuditLog logAction(String complaintNumber, String action, String actor,
                              String role, String remarks, Map<String, Object> metadata,
                              String previousState, String newState) {
        String metadataJson = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit metadata for complaint {}: {}", complaintNumber, e.getMessage());
                metadataJson = "{}";
            }
        }

        AuditLog auditLog = AuditLog.builder()
                .complaintNumber(complaintNumber)
                .action(action)
                .actor(actor)
                .actorRole(role)
                .timestamp(LocalDateTime.now())
                .remarks(remarks)
                .metadata(metadataJson)
                .ipAddress(resolveIpAddress())
                .previousState(previousState)
                .newState(newState)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.debug("Audit logged: complaint={}, action={}, actor={}, role={}", complaintNumber, action, actor, role);
        return saved;
    }

    /**
     * Writes the audit row in its OWN transaction, so it survives the caller rolling back.
     *
     * <p>WHY THIS EXISTS: {@link #logAction} joins the caller's transaction. That is right for a
     * workflow action — the audit row and the change it describes must stand or fall together. It is
     * exactly WRONG for auditing a REFUSAL, where the caller is about to throw: the audit row would
     * be rolled back by the very exception it exists to record, and the attempt would leave no
     * trace. A refused attempt to destroy a record inside its retention period is precisely the
     * event an investigator has to be able to find.
     *
     * <p>Not {@code @Async}: the attempt must be on disk before the refusal is returned to the
     * caller, and an async write could lose the row if the JVM stops in between. Synchronous and
     * independently committed.
     *
     * <p>MUST be called through the bean, not {@code this} — a self-invocation bypasses the proxy and
     * silently joins the caller's transaction, reinstating the bug.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logInOwnTransaction(String complaintNumber, String action, String actor,
                                        String role, String remarks, Map<String, Object> metadata) {
        return logAction(complaintNumber, action, actor, role, remarks, metadata, null, null);
    }

    /**
     * Async variant for non-critical audit logging (fire-and-forget).
     */
    @Async("taskExecutor")
    @Transactional
    public void logActionAsync(String complaintNumber, String action, String actor,
                               String role, String remarks, Map<String, Object> metadata,
                               String previousState, String newState) {
        logAction(complaintNumber, action, actor, role, remarks, metadata, previousState, newState);
    }

    /**
     * Retrieve audit trail for a specific complaint.
     */
    public List<AuditLog> getAuditTrail(String complaintNumber) {
        return auditLogRepository.findByComplaintNumberOrderByTimestampDesc(complaintNumber);
    }

    /**
     * Retrieve audit trail for a specific actor.
     */
    public List<AuditLog> getAuditTrailByActor(String actor) {
        return auditLogRepository.findByActorOrderByTimestampDesc(actor);
    }

    private String resolveIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not resolve IP address: {}", e.getMessage());
        }
        return null;
    }
}
