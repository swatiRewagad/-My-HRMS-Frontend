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

    private final RbioStatusVocabulary rbioStatusVocabulary;

    /**
     * Every interval and recipient list below comes from here rather than from a literal (UST663-668).
     *
     * <p>Before this, no scheduled job in this class referenced SYSTEM_CONFIG at all: the thresholds
     * were {@code minusDays(5)} / {@code minusDays(14)} and the recipients were bare strings, so the
     * Super-Admin config screen existed and governed nothing.
     */
    private final NotificationConfigService notificationConfig;

    /**
     * The closed-status vocabulary, from RBIO_STATUS_MASTER rather than a literal.
     *
     * <p>This was the NARROWER of the two disagreeing hardcoded lists: four values against
     * WorkflowController's six, omitting {@code adjudicated} and {@code conciliated}. Consequence — a
     * complaint closed by an award or by a successful conciliation was treated here as still OPEN, so the
     * scheduled reminders below kept nudging officers about cases already decided, and the RE-response
     * chaser kept chasing entities on settled matters.
     *
     * <p>Consolidating therefore CHANGES this class's behaviour, deliberately: it stops those nudges.
     * That is the point of one shared vocabulary — the alternative is to keep sending demonstrably wrong
     * reminders in order to preserve them.
     */
    private List<String> closedStatuses() {
        return rbioStatusVocabulary.closedStatuses();
    }

    /**
     * Daily at 9 AM: complaints whose status has not changed for the configured number of days (UST612).
     *
     * <p>The threshold is {@code notification.pending.nudge_days}, not a literal 5, so the method name
     * is now a historical label rather than a description of the interval.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void checkPendingFiveDays() {
        int days = notificationConfig.pendingNudgeDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<Complaint> staleComplaints =
                complaintRepository.findByStatusAndLastStatusChangeDateBefore("pending", cutoff);

        staleComplaints.forEach(complaint -> notificationService.raiseEvent(
                notificationConfig.pendingNudgeRecipients(),
                "PENDING_5DAY",
                "Complaint pending for " + days + "+ days",
                "Complaint " + complaint.getComplaintNumber() + " has been pending for " + days
                        + " days without action.",
                complaint.getComplaintNumber(),
                "COMPLAINT",
                "/complaint/" + complaint.getComplaintNumber(),
                complaint));

        log.info("checkPendingFiveDays: {} complaints stale for {}+ days", staleComplaints.size(), days);
    }

    /**
     * Daily at 9 AM: nodal-officer records stuck at INFORMATION_REQUIRED (UST611).
     *
     * <p>Two configurable thresholds — {@code notification.no_record.warn_days} and
     * {@code ..escalate_days} — with recipients per level from config.
     *
     * <p>SCOPE FIX: both queries now require the PARENT COMPLAINT to still be open. The previous
     * version filtered on the NO record's own status alone, so a complaint closed while its record
     * still read INFORMATION_REQUIRED escalated to an administrator every single day, forever: the
     * record never changes again, so it only ever gets staler. That is a live nuisance-alert bug, not
     * a theoretical one.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void checkNoRecordStale() {
        int warnDays = notificationConfig.noRecordWarnDays();
        int escalateDays = notificationConfig.noRecordEscalateDays();
        List<String> closed = closedStatuses();

        LocalDateTime warnCutoff = LocalDateTime.now().minusDays(warnDays);
        LocalDateTime escalateCutoff = LocalDateTime.now().minusDays(escalateDays);

        // Escalation level first, so a record past the higher threshold is not also warned about.
        List<NodalOfficerRecord> criticalStale = nodalOfficerRecordRepository
                .findStaleWithOpenComplaint("INFORMATION_REQUIRED", escalateCutoff, closed);

        criticalStale.forEach(record -> notificationService.raiseEvent(
                notificationConfig.noRecordEscalateRecipients(),
                "NO_STATUS_STALE",
                "NO record critically stale (" + escalateDays + "+ days)",
                "Nodal Officer record for complaint " + record.getComplaintNumber()
                        + " (entity: " + record.getEntityName() + ") has been unchanged for "
                        + escalateDays + "+ days. Immediate action required.",
                record.getComplaintNumber(),
                "NO_RECORD",
                "/complaint/" + record.getComplaintNumber(),
                complaintFor(record.getComplaintNumber())));

        List<NodalOfficerRecord> warningStale = nodalOfficerRecordRepository
                .findStaleWithOpenComplaint("INFORMATION_REQUIRED", warnCutoff, closed)
                .stream()
                .filter(r -> r.getLastModifiedAt() != null && r.getLastModifiedAt().isAfter(escalateCutoff))
                .toList();

        warningStale.forEach(record -> notificationService.raiseEvent(
                notificationConfig.noRecordWarnRecipients(),
                "NO_STATUS_STALE",
                "NO record stale (" + warnDays + "+ days)",
                "Nodal Officer record for complaint " + record.getComplaintNumber()
                        + " (entity: " + record.getEntityName() + ") has been unchanged for "
                        + warnDays + "+ days.",
                record.getComplaintNumber(),
                "NO_RECORD",
                "/complaint/" + record.getComplaintNumber(),
                complaintFor(record.getComplaintNumber())));

        log.info("checkNoRecordStale: {}-day escalations={}, {}-day warnings={}",
                escalateDays, criticalStale.size(), warnDays, warningStale.size());
    }

    /**
     * The complaint behind a nodal-officer record, for per-complaint recipient placeholders.
     *
     * <p>Nullable return is fine: {@link NotificationRecipientResolver} treats a null complaint as
     * "no per-complaint recipients resolvable" and still delivers to configured roles.
     */
    private Complaint complaintFor(String complaintNumber) {
        if (complaintNumber == null || complaintNumber.isBlank()) {
            return null;
        }
        return complaintRepository.findByComplaintNumber(complaintNumber).orElse(null);
    }

    /**
     * Daily at 8 AM: deactivate upload links past their expiry (UST776).
     *
     * <p>EVIDENCE-ERASURE FIX. This previously set {@code documentsSubmitted = false} unconditionally
     * on every lapsing link, while leaving {@code documentsSubmittedAt} populated. A complainant who
     * uploaded on day 3 therefore had the "documents received" flag wiped on day 7, leaving a row that
     * contradicted itself — a submission timestamp with a false submission flag — and a UST776 display
     * value of "No" for someone who demonstrably did submit. Losing the record of a citizen's
     * submission is materially worse than showing a lapsed link, so the flag is now left alone when a
     * submission actually happened.
     *
     * <p>Only a link that lapsed WITHOUT a submission is flagged as such, and only that case notifies.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void checkUploadLinkExpiry() {
        LocalDateTime now = LocalDateTime.now();
        List<UploadLink> expiredLinks = uploadLinkRepository.findByActiveTrueAndExpiresAtBefore(now);

        int lapsedWithoutSubmission = 0;

        for (UploadLink link : expiredLinks) {
            link.setActive(false);

            if (!link.isDocumentsSubmitted()) {
                lapsedWithoutSubmission++;
                uploadLinkRepository.save(link);

                notificationService.raiseEvent(
                        notificationConfig.uploadLinkExpiredRecipients(),
                        "UPLOAD_LINK_EXPIRED",
                        "Secure upload link expired without a submission",
                        "The secure upload link for complaint " + link.getComplaintNumber()
                                + " expired on " + link.getExpiresAt()
                                + " and no documents were submitted.",
                        link.getComplaintNumber(),
                        "COMPLAINT",
                        "/complaint/" + link.getComplaintNumber(),
                        complaintFor(link.getComplaintNumber()));
            } else {
                uploadLinkRepository.save(link);
                log.debug("Upload link for complaint {} lapsed but documents were already submitted at {} — "
                        + "submission flag preserved", link.getComplaintNumber(), link.getDocumentsSubmittedAt());
            }
        }

        log.info("checkUploadLinkExpiry: deactivated {} links, {} of them with no submission",
                expiredLinks.size(), lapsedWithoutSubmission);
    }

    /**
     * Daily at 10 AM: complainant reminders at two configurable ages (UST662).
     *
     * <p>Both thresholds come from config. Closed complaints are excluded by
     * {@code findOpenComplaintsOlderThan}, whose {@code status NOT IN :closedStatuses} clause is driven
     * by {@link RbioStatusVocabulary} — so a reminder stops as soon as the complaint closes. That
     * already held before this change; it is now covered by an explicit test rather than left as an
     * incidental property of a shared query.
     */
    @Scheduled(cron = "0 0 10 * * *")
    @Transactional
    public void sendComplainantReminders() {
        int firstDays = notificationConfig.complainantReminderFirstDays();
        int secondDays = notificationConfig.complainantReminderSecondDays();

        // Later threshold first, so a complaint crossing both on the same day gets the more advanced
        // reminder rather than two.
        remindComplainants(secondDays, "COMPLAINANT_REMINDER_21DAY");
        remindComplainants(firstDays, "COMPLAINANT_REMINDER_14DAY");

        log.info("sendComplainantReminders: processed {}-day and {}-day reminders", firstDays, secondDays);
    }

    /**
     * One reminder tier.
     *
     * <p>The one-day window around the cutoff is preserved from the original: it makes the job fire
     * once as a complaint crosses the threshold, rather than every day thereafter for the rest of the
     * complaint's life.
     */
    private void remindComplainants(int ageDays, String eventType) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(ageDays);

        List<Complaint> due = complaintRepository
                .findOpenComplaintsOlderThan(closedStatuses(), cutoff, "RBIO")
                .stream()
                .filter(c -> c.getCreatedAt() != null
                        && c.getCreatedAt().isAfter(cutoff.minusDays(1))
                        && c.getCreatedAt().isBefore(cutoff.plusDays(1)))
                .toList();

        due.forEach(complaint -> notificationService.raiseEvent(
                notificationConfig.pendingNudgeRecipients(),
                eventType,
                ageDays + "-day complainant reminder due",
                "Complaint " + complaint.getComplaintNumber() + " has been open for " + ageDays
                        + " days. Send reminder to complainant: " + complaint.getComplainantName(),
                complaint.getComplaintNumber(),
                "COMPLAINT",
                "/complaint/" + complaint.getComplaintNumber(),
                complaint));

        log.debug("{}: {} complaints due at {} days", eventType, due.size(), ageDays);
    }

    /**
     * Every 30 minutes: complaints past the RE response deadline, after a configurable grace period
     * (UST637-638).
     *
     * <p>UST638 requires the alert to be delayed by a configurable interval after the deadline lapses;
     * previously it fired the moment the deadline passed with no delay available. The grace period is
     * {@code notification.re_response.alert_delay_days}, defaulting to 0 so behaviour is unchanged until
     * an operator sets it — a non-zero default would silently suppress alerts that currently fire.
     *
     * <p>The DEADLINE itself is not configured here. It comes from
     * {@code timeline.re.response_deadline_days}, which is owned by another session and already seeded;
     * defining a second key for one deadline would guarantee the two disagree.
     */
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void checkReResponseDeadline() {
        int graceDays = notificationConfig.reResponseAlertDelayDays();

        // Shifting the comparison date back by the grace period is equivalent to requiring the deadline
        // to be at least graceDays old, and needs no new query.
        LocalDate alertThreshold = LocalDate.now().minusDays(graceDays);
        List<Complaint> overdue =
                complaintRepository.findPastReResponseDeadline(alertThreshold, closedStatuses());

        overdue.forEach(complaint -> notificationService.raiseEvent(
                notificationConfig.pendingNudgeRecipients(),
                "RE_RESPONSE_OVERDUE",
                "RE response deadline passed",
                "Complaint " + complaint.getComplaintNumber() + " has passed the RE response deadline ("
                        + complaint.getReResponseDeadline() + "). Consider ex-parte proceedings.",
                complaint.getComplaintNumber(),
                "COMPLAINT",
                "/complaint/" + complaint.getComplaintNumber(),
                complaint));

        log.info("checkReResponseDeadline: {} complaints past deadline (grace {} days)",
                overdue.size(), graceDays);
    }

    /**
     * UST609: NO_RECORD_ASSIGNED — Complaints with no officer assigned.
     * Runs daily at 9:30 AM on weekdays.
     * Notifies admin when complaints are sitting unassigned.
     */
    @Scheduled(cron = "0 30 9 * * MON-FRI")
    @Transactional
    public void checkNoRecordAssigned() {
        // Pending complaints with no department
        List<Complaint> unassigned = complaintRepository.findByStatusAndDepartmentIsNullOrderByCreatedAtDesc("pending");
        int count = 0;

        for (Complaint c : unassigned) {
            notificationService.raiseEvent(notificationConfig.noRecordAssignedRecipients(),
                    "NO_RECORD_ASSIGNED",
                    "Complaint has no record assigned",
                    "Complaint " + c.getComplaintNumber() + " (" + c.getSubject() + ") has no department or officer assigned.",
                    c.getComplaintNumber(), "COMPLAINT", "/workflow/unassigned", c);
            count++;
        }

        // Complaints that have a department but no assigned officer
        for (String dept : notificationConfig.scannedDepartments()) {
            List<Complaint> deptComplaints = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(
                    dept, closedStatuses());
            for (Complaint c : deptComplaints) {
                if (c.getAssignedOfficer() == null || c.getAssignedOfficer().isBlank()) {
                    notificationService.raiseEvent(notificationConfig.noRecordAssignedRecipients(),
                            "NO_RECORD_ASSIGNED",
                            "Complaint missing officer assignment",
                            "Complaint " + c.getComplaintNumber() + " in " + dept + " has no officer assigned.",
                            c.getComplaintNumber(), "COMPLAINT",
                            "/workflow/" + dept.toLowerCase() + "/complaint/" + c.getComplaintNumber(), c);
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
    @Transactional
    public void checkOnLeavePending() {
        for (String dept : notificationConfig.scannedDepartments()) {
            List<Complaint> deptComplaints = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(
                    dept, closedStatuses());

            for (Complaint c : deptComplaints) {
                if ("ON_LEAVE".equals(c.getWorkflowStage())) {
                    notificationService.raiseEvent(notificationConfig.onLeavePendingRecipients(dept),
                            "ON_LEAVE_PENDING",
                            "Officer on leave with open complaints",
                            "Complaint " + c.getComplaintNumber() + " is assigned to " + c.getAssignedOfficer()
                                    + " who is currently on leave. Reassignment may be needed.",
                            c.getComplaintNumber(), "COMPLAINT",
                            "/workflow/" + dept.toLowerCase() + "/complaint/" + c.getComplaintNumber(), c);
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
    @Transactional
    public void checkDuplicateDetected() {
        for (String dept : notificationConfig.scannedDepartments()) {
            List<Complaint> deptComplaints = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(
                    dept, closedStatuses());

            for (Complaint c : deptComplaints) {
                if (c.getTriageSignal() != null && c.getTriageSignal().contains("DUPLICATE")) {
                    notificationService.raiseEvent(notificationConfig.pendingNudgeRecipients(),
                            "DUPLICATE_DETECTED",
                            "Possible duplicate complaint",
                            "Complaint " + c.getComplaintNumber() + " has been flagged as a potential duplicate.",
                            c.getComplaintNumber(), "COMPLAINT",
                            "/workflow/" + dept.toLowerCase() + "/complaint/" + c.getComplaintNumber(), c);
                }
            }
        }
        log.info("UST607: Completed DUPLICATE_DETECTED notification scan");
    }
}
