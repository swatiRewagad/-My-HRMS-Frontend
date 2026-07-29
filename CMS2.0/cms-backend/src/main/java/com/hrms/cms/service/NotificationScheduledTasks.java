package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.UploadLink;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.UploadLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduledTasks {

    private final ComplaintRepository complaintRepository;
    private final NotificationService notificationService;
    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;
    private final UploadLinkRepository uploadLinkRepository;

    private static final List<String> CLOSED_STATUSES = List.of("closed", "resolved", "rejected", "withdrawn");

    /**
     * Daily at 9 AM: find complaints where status hasn't changed in 5+ days.
     * Notify complaint owner: "Complaint {number} has been pending for 5 days without action"
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void checkPendingFiveDays() {
        LocalDateTime fiveDaysAgo = LocalDateTime.now().minusDays(5);
        List<Complaint> staleComplaints = complaintRepository.findByStatusAndLastStatusChangeDateBefore("pending", fiveDaysAgo);

        staleComplaints.forEach(complaint -> {
            String target = complaint.getAssignedOfficer() != null ? complaint.getAssignedOfficer() : "UNASSIGNED_POOL";
            notificationService.send(
                    target,
                    "PENDING_5DAY",
                    "Complaint pending for 5+ days",
                    "Complaint " + complaint.getComplaintNumber() + " has been pending for 5 days without action.",
                    complaint.getComplaintNumber(),
                    "COMPLAINT",
                    "/complaint/" + complaint.getComplaintNumber()
            );
        });

        log.info("checkPendingFiveDays: notified {} complaint owners", staleComplaints.size());
    }

    /**
     * Daily at 9 AM: check NO (Nodal Officer) records for stale status.
     * 15 days unchanged -> notify all RBIO users
     * 20 days unchanged -> notify again (escalation)
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void checkNoRecordStale() {
        LocalDateTime fifteenDaysAgo = LocalDateTime.now().minusDays(15);
        LocalDateTime twentyDaysAgo = LocalDateTime.now().minusDays(20);

        // 20-day escalation (checked first to avoid duplicate for same record)
        List<NodalOfficerRecord> criticalStale = nodalOfficerRecordRepository
                .findByStatusAndLastModifiedAtBefore("INFORMATION_REQUIRED", twentyDaysAgo);

        criticalStale.forEach(record -> {
            notificationService.send(
                    "RBIO_ADMIN",
                    "NO_STATUS_STALE",
                    "NO record critically stale (20+ days)",
                    "Nodal Officer record for complaint " + record.getComplaintNumber()
                            + " (entity: " + record.getEntityName() + ") has been unchanged for 20+ days. Immediate action required.",
                    record.getComplaintNumber(),
                    "NO_RECORD",
                    "/complaint/" + record.getComplaintNumber()
            );
        });

        // 15-day warning (exclude those already at 20+ days)
        List<NodalOfficerRecord> warningStale = nodalOfficerRecordRepository
                .findByStatusAndLastModifiedAtBefore("INFORMATION_REQUIRED", fifteenDaysAgo);

        warningStale.stream()
                .filter(r -> r.getLastModifiedAt() != null && r.getLastModifiedAt().isAfter(twentyDaysAgo))
                .forEach(record -> {
                    notificationService.send(
                            "RBIO_SUPERVISOR",
                            "NO_STATUS_STALE",
                            "NO record stale (15+ days)",
                            "Nodal Officer record for complaint " + record.getComplaintNumber()
                                    + " (entity: " + record.getEntityName() + ") has been unchanged for 15+ days.",
                            record.getComplaintNumber(),
                            "NO_RECORD",
                            "/complaint/" + record.getComplaintNumber()
                    );
                });

        log.info("checkNoRecordStale: 20-day escalations={}, 15-day warnings={}",
                criticalStale.size(),
                warningStale.stream().filter(r -> r.getLastModifiedAt() != null && r.getLastModifiedAt().isAfter(twentyDaysAgo)).count());
    }

    /**
     * Daily at 8 AM: find active upload links that have expired (7 days).
     * Update documentsSubmitted flag to 'No', mark link as expired (inactive).
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void checkUploadLinkExpiry() {
        LocalDateTime now = LocalDateTime.now();
        List<UploadLink> expiredLinks = uploadLinkRepository.findByActiveTrueAndExpiresAtBefore(now);

        expiredLinks.forEach(link -> {
            link.setActive(false);
            link.setDocumentsSubmitted(false);
            uploadLinkRepository.save(link);

            log.debug("Expired upload link for complaint {}, token {}", link.getComplaintNumber(), link.getToken());
        });

        log.info("checkUploadLinkExpiry: expired {} upload links", expiredLinks.size());
    }

    /**
     * Daily at 10 AM: send complainant reminders for RBIO layout complaints still open.
     * 2-week reminder: complaints open for 14 days (UST662)
     * 3-week reminder: complaints open for 21 days (UST662)
     */
    @Scheduled(cron = "0 0 10 * * *")
    @Transactional(readOnly = true)
    public void sendComplainantReminders() {
        LocalDateTime fourteenDaysAgo = LocalDateTime.now().minusDays(14);
        LocalDateTime twentyOneDaysAgo = LocalDateTime.now().minusDays(21);

        // 3-week reminder
        List<Complaint> threeWeekOpen = complaintRepository.findOpenComplaintsOlderThan(
                CLOSED_STATUSES, twentyOneDaysAgo, "RBIO");

        threeWeekOpen.stream()
                .filter(c -> c.getCreatedAt() != null
                        && c.getCreatedAt().isAfter(twentyOneDaysAgo.minusDays(1))
                        && c.getCreatedAt().isBefore(twentyOneDaysAgo.plusDays(1)))
                .forEach(complaint -> {
                    notificationService.send(
                            complaint.getAssignedOfficer() != null ? complaint.getAssignedOfficer() : "RBIO_OFFICER",
                            "COMPLAINANT_REMINDER_21DAY",
                            "3-week complainant reminder due (UST662)",
                            "Complaint " + complaint.getComplaintNumber() + " has been open for 21 days. "
                                    + "Send reminder to complainant: " + complaint.getComplainantName(),
                            complaint.getComplaintNumber(),
                            "COMPLAINT",
                            "/complaint/" + complaint.getComplaintNumber()
                    );
                    log.debug("3-week reminder dispatched for complaint {}", complaint.getComplaintNumber());
                });

        // 2-week reminder
        List<Complaint> twoWeekOpen = complaintRepository.findOpenComplaintsOlderThan(
                CLOSED_STATUSES, fourteenDaysAgo, "RBIO");

        twoWeekOpen.stream()
                .filter(c -> c.getCreatedAt() != null
                        && c.getCreatedAt().isAfter(fourteenDaysAgo.minusDays(1))
                        && c.getCreatedAt().isBefore(fourteenDaysAgo.plusDays(1)))
                .forEach(complaint -> {
                    notificationService.send(
                            complaint.getAssignedOfficer() != null ? complaint.getAssignedOfficer() : "RBIO_OFFICER",
                            "COMPLAINANT_REMINDER_14DAY",
                            "2-week complainant reminder due (UST662)",
                            "Complaint " + complaint.getComplaintNumber() + " has been open for 14 days. "
                                    + "Send reminder to complainant: " + complaint.getComplainantName(),
                            complaint.getComplaintNumber(),
                            "COMPLAINT",
                            "/complaint/" + complaint.getComplaintNumber()
                    );
                    log.debug("2-week reminder dispatched for complaint {}", complaint.getComplaintNumber());
                });

        log.info("sendComplainantReminders: 14-day and 21-day reminders processed for RBIO complaints");
    }

    /**
     * Every 30 minutes: find complaints past RE response deadline.
     * Notify DO if not already notified (UST637-638).
     */
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional(readOnly = true)
    public void checkReResponseDeadline() {
        LocalDate today = LocalDate.now();
        List<Complaint> overdue = complaintRepository.findPastReResponseDeadline(today, CLOSED_STATUSES);

        overdue.forEach(complaint -> {
            String targetDO = complaint.getAssignedOfficer() != null ? complaint.getAssignedOfficer() : "RBIO_OFFICER";
            notificationService.send(
                    targetDO,
                    "RE_RESPONSE_OVERDUE",
                    "RE response deadline passed (UST637-638)",
                    "Complaint " + complaint.getComplaintNumber() + " has passed the RE response deadline ("
                            + complaint.getReResponseDeadline() + "). Consider ex-parte proceedings.",
                    complaint.getComplaintNumber(),
                    "COMPLAINT",
                    "/complaint/" + complaint.getComplaintNumber()
            );
        });

        log.info("checkReResponseDeadline: {} complaints past RE response deadline", overdue.size());
    }

    /**
     * UST609: NO_RECORD_ASSIGNED — Complaints with no officer assigned.
     * Runs daily at 9:30 AM on weekdays.
     * Notifies admin when complaints are sitting unassigned.
     */
    @Scheduled(cron = "0 30 9 * * MON-FRI")
    @Transactional(readOnly = true)
    public void checkNoRecordAssigned() {
        // Pending complaints with no department
        List<Complaint> unassigned = complaintRepository.findByStatusAndDepartmentIsNullOrderByCreatedAtDesc("pending");
        int count = 0;

        for (Complaint c : unassigned) {
            notificationService.send("SYSTEM_ADMIN", "NO_RECORD_ASSIGNED",
                    "Complaint has no record assigned",
                    "Complaint " + c.getComplaintNumber() + " (" + c.getSubject() + ") has no department or officer assigned.",
                    c.getComplaintNumber(), "COMPLAINT", "/workflow/unassigned");
            count++;
        }

        // Complaints that have a department but no assigned officer
        for (String dept : List.of("RBIO", "CEPC", "CRPC")) {
            List<Complaint> deptComplaints = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(
                    dept, CLOSED_STATUSES);
            for (Complaint c : deptComplaints) {
                if (c.getAssignedOfficer() == null || c.getAssignedOfficer().isBlank()) {
                    notificationService.send(dept + "_ADMIN", "NO_RECORD_ASSIGNED",
                            "Complaint missing officer assignment",
                            "Complaint " + c.getComplaintNumber() + " in " + dept + " has no officer assigned.",
                            c.getComplaintNumber(), "COMPLAINT",
                            "/workflow/" + dept.toLowerCase() + "/complaint/" + c.getComplaintNumber());
                    count++;
                }
            }
        }
        log.info("UST609: Sent NO_RECORD_ASSIGNED notifications for {} complaints", count);
    }

    /**
     * UST608: ON_LEAVE_PENDING — Notify admin when officers marked on-leave have open complaints.
     * Runs daily at 8:00 AM on weekdays.
     * Detects complaints whose workflowStage is ON_LEAVE (set when officer is marked on leave).
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    @Transactional(readOnly = true)
    public void checkOnLeavePending() {
        for (String dept : List.of("RBIO", "CEPC", "CRPC")) {
            List<Complaint> deptComplaints = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(
                    dept, CLOSED_STATUSES);

            for (Complaint c : deptComplaints) {
                if ("ON_LEAVE".equals(c.getWorkflowStage())) {
                    notificationService.send(dept + "_ADMIN", "ON_LEAVE_PENDING",
                            "Officer on leave with open complaints",
                            "Complaint " + c.getComplaintNumber() + " is assigned to " + c.getAssignedOfficer()
                                    + " who is currently on leave. Reassignment may be needed.",
                            c.getComplaintNumber(), "COMPLAINT",
                            "/workflow/" + dept.toLowerCase() + "/complaint/" + c.getComplaintNumber());
                }
            }
        }
        log.info("UST608: Completed ON_LEAVE_PENDING notification scan");
    }

    /**
     * UST607: DUPLICATE_DETECTED — Notify assigned officer when a complaint is flagged as a duplicate.
     * Runs daily at 10:00 AM on weekdays.
     * Checks triageSignal field for DUPLICATE flag.
     */
    @Scheduled(cron = "0 0 10 * * MON-FRI")
    @Transactional(readOnly = true)
    public void checkDuplicateDetected() {
        for (String dept : List.of("RBIO", "CEPC", "CRPC")) {
            List<Complaint> deptComplaints = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(
                    dept, CLOSED_STATUSES);

            for (Complaint c : deptComplaints) {
                if (c.getTriageSignal() != null && c.getTriageSignal().contains("DUPLICATE")) {
                    String officer = c.getAssignedOfficer();
                    if (officer != null && !officer.isBlank()) {
                        notificationService.send(officer, "DUPLICATE_DETECTED",
                                "Possible duplicate complaint",
                                "Complaint " + c.getComplaintNumber() + " has been flagged as a potential duplicate.",
                                c.getComplaintNumber(), "COMPLAINT",
                                "/workflow/" + dept.toLowerCase() + "/complaint/" + c.getComplaintNumber());
                    }
                }
            }
        }
        log.info("UST607: Completed DUPLICATE_DETECTED notification scan");
    }
}
