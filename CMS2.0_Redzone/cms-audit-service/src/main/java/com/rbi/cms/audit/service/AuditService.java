package com.rbi.cms.audit.service;

import com.rbi.cms.audit.entity.AuditLog;
import com.rbi.cms.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog record(String entityType, String entityId, String action,
                           String previousValue, String newValue, String performedBy,
                           String correlationId) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .previousValue(previousValue)
                .newValue(newValue)
                .performedBy(performedBy)
                .correlationId(correlationId)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.debug("Audit recorded: {} {} on {}/{}", action, performedBy, entityType, entityId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrail(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditByUser(String performedBy, Pageable pageable) {
        return auditLogRepository.findByPerformedByOrderByCreatedAtDesc(performedBy, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditByDateRange(Instant from, Instant to, Pageable pageable) {
        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, pageable);
    }
}
