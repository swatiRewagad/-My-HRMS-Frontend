package com.rbi.cms.sla.service;

import com.rbi.cms.common.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaMonitorService {

    private final EntityManager entityManager;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public int detectAndEscalateBreaches() {
        Query query = entityManager.createNativeQuery(
                "SELECT COMPLAINT_ID FROM COMPLAINT_MASTER " +
                        "WHERE STATUS NOT IN ('RESOLVED', 'CLOSED') " +
                        "AND SLA_DUE_DATE < :now");
        query.setParameter("now", Instant.now());

        @SuppressWarnings("unchecked")
        List<String> breachedIds = query.getResultList();

        for (String complaintId : breachedIds) {
            log.warn("SLA breach detected for complaint: {}", complaintId);
            kafkaTemplate.send(KafkaTopics.COMPLAINT_ESCALATED, complaintId,
                    "{\"complaintId\":\"" + complaintId + "\",\"reason\":\"SLA_BREACH\"}");
        }

        return breachedIds.size();
    }

    public int detectWarnings() {
        Query query = entityManager.createNativeQuery(
                "SELECT COMPLAINT_ID FROM COMPLAINT_MASTER " +
                        "WHERE STATUS NOT IN ('RESOLVED', 'CLOSED') " +
                        "AND SLA_DUE_DATE BETWEEN :now AND :warningThreshold");
        query.setParameter("now", Instant.now());
        query.setParameter("warningThreshold", Instant.now().plusSeconds(86400 * 3));

        @SuppressWarnings("unchecked")
        List<String> warningIds = query.getResultList();

        for (String complaintId : warningIds) {
            log.info("SLA warning: complaint {} approaching due date", complaintId);
        }

        return warningIds.size();
    }
}
