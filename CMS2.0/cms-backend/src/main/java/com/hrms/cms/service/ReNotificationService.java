package com.hrms.cms.service;

import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lightweight notification service for the RE (Regulated Entity) portal.
 * Currently logs notifications via timeline entries. Actual email/SMS integration is future scope.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReNotificationService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintTimelineRepository timelineRepository;

    /**
     * Logs a notification that a complaint has been forwarded to the RE.
     */
    @Transactional
    public void notifyComplaintForwarded(String complaintNumber, String entityCode) {
        complaintRepository.findByComplaintNumber(complaintNumber).ifPresent(complaint -> {
            ComplaintTimeline timeline = ComplaintTimeline.builder()
                    .complaintId(complaint.getId())
                    .action("RE_NOTIFICATION_FORWARDED")
                    .performedBy("SYSTEM")
                    .remarks("Complaint forwarded to regulated entity: " + entityCode)
                    .fromStatus(complaint.getStatus())
                    .toStatus(complaint.getStatus())
                    .build();
            timelineRepository.save(timeline);
            log.info("Notification logged: complaint {} forwarded to entity {}", complaintNumber, entityCode);
        });
    }

    /**
     * Logs a notification that the RE has responded to the complaint.
     */
    @Transactional
    public void notifyResponseReceived(String complaintNumber, String entityCode) {
        complaintRepository.findByComplaintNumber(complaintNumber).ifPresent(complaint -> {
            ComplaintTimeline timeline = ComplaintTimeline.builder()
                    .complaintId(complaint.getId())
                    .action("RE_NOTIFICATION_RESPONSE_RECEIVED")
                    .performedBy("SYSTEM")
                    .remarks("Response received from regulated entity: " + entityCode)
                    .fromStatus(complaint.getStatus())
                    .toStatus(complaint.getStatus())
                    .build();
            timelineRepository.save(timeline);
            log.info("Notification logged: response received for complaint {} from entity {}", complaintNumber, entityCode);
        });
    }

    /**
     * Logs a warning notification when response window is approaching breach (at 12 days).
     */
    @Transactional
    public void notifyWindowBreaching(String complaintNumber, String entityCode) {
        complaintRepository.findByComplaintNumber(complaintNumber).ifPresent(complaint -> {
            ComplaintTimeline timeline = ComplaintTimeline.builder()
                    .complaintId(complaint.getId())
                    .action("RE_NOTIFICATION_WINDOW_BREACH_WARNING")
                    .performedBy("SYSTEM")
                    .remarks("Response window breach warning (12 days elapsed) for entity: " + entityCode)
                    .fromStatus(complaint.getStatus())
                    .toStatus(complaint.getStatus())
                    .build();
            timelineRepository.save(timeline);
            log.warn("Breach warning: complaint {} nearing response window expiry for entity {}", complaintNumber, entityCode);
        });
    }
}
