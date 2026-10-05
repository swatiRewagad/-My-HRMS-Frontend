package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Every notification interval and recipient, read from SYSTEM_CONFIG (UST663-668).
 *
 * <p>The scheduled jobs previously held their thresholds as literals — {@code minusDays(5)},
 * {@code minusDays(14)}, {@code LINK_EXPIRY_DAYS = 7} — and their recipients as bare strings
 * ({@code "RBIO_ADMIN"}, {@code dept + "_ADMIN"}). The Super-Admin config mechanism the stories
 * require already existed and was simply not wired to anything: SYSTEM_CONFIG plus
 * TimelineConfigController. So this class is deliberately thin — it is a KEY REGISTRY over the
 * existing {@link SystemConfigService}, not a new config mechanism.
 *
 * <p>Why a registry class at all, rather than each caller calling {@code getInt} with a literal key:
 * the keys are the contract with the Super-Admin screen, and a typo in a key string is invisible —
 * {@code getInt} falls back to the caller's default and the job keeps running on the compiled-in
 * number while an operator watches their edited config row have no effect. Naming every key in one
 * place makes that class of silent failure a compile error instead.
 *
 * <p>Fallbacks are the CURRENT hardcoded values, so an unseeded database behaves exactly as before
 * this class existed. That is the point: wiring configuration in must not itself change behaviour.
 *
 * <p>RE-deadline keys are deliberately absent. Session S2 owns {@code timeline.re.*} and those keys
 * are already seeded (V8); consuming theirs rather than defining a parallel
 * {@code notification.re.*} avoids two rows that disagree about one deadline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConfigService {

    // ═══ Interval keys ═══

    /** Days a complaint may sit with no status change before its owner is nudged (UST612). */
    public static final String KEY_PENDING_NUDGE_DAYS = "notification.pending.nudge_days";
    /** First no-record staleness warning (UST611). */
    public static final String KEY_NO_RECORD_WARN_DAYS = "notification.no_record.warn_days";
    /** Escalated no-record staleness (UST611). */
    public static final String KEY_NO_RECORD_ESCALATE_DAYS = "notification.no_record.escalate_days";
    /** First complainant reminder (UST662). */
    public static final String KEY_COMPLAINANT_REMINDER_FIRST_DAYS =
            "notification.complainant_reminder.first_days";
    /** Second complainant reminder (UST662). */
    public static final String KEY_COMPLAINANT_REMINDER_SECOND_DAYS =
            "notification.complainant_reminder.second_days";
    /** Secure upload link lifetime (UST601). */
    public static final String KEY_UPLOAD_LINK_EXPIRY_DAYS = "notification.upload_link.expiry_days";
    /**
     * Grace period after the RE response deadline lapses before the DO is told (UST638).
     *
     * <p>Zero preserves today's behaviour, which fires immediately on lapse. The story asks for the
     * delay to be configurable, not for a particular non-zero default — choosing one here would
     * silently suppress alerts that currently fire.
     */
    public static final String KEY_RE_RESPONSE_ALERT_DELAY_DAYS =
            "notification.re_response.alert_delay_days";

    // ═══ Recipient keys ═══
    // Values are comma-separated role names or user ids, read via getSet so one event may notify
    // several parties (UST663-668 all name more than one recipient).

    public static final String KEY_RECIPIENTS_PENDING_NUDGE = "notification.recipients.pending_nudge";
    public static final String KEY_RECIPIENTS_NO_RECORD_WARN = "notification.recipients.no_record_warn";
    public static final String KEY_RECIPIENTS_NO_RECORD_ESCALATE =
            "notification.recipients.no_record_escalate";
    public static final String KEY_RECIPIENTS_NO_RECORD_ASSIGNED =
            "notification.recipients.no_record_assigned";
    public static final String KEY_RECIPIENTS_ON_LEAVE_PENDING =
            "notification.recipients.on_leave_pending";
    public static final String KEY_RECIPIENTS_DOCUMENTS_UPLOADED =
            "notification.recipients.documents_uploaded";
    public static final String KEY_RECIPIENTS_UPLOAD_LINK_EXPIRED =
            "notification.recipients.upload_link_expired";

    /**
     * Who is told when a complainant withdraws a complaint (UST108 / FR-G-037).
     *
     * <p>Three parties by default, because all three are actively working a case that has just been
     * abandoned: the Processing Officer holding it, and the regulated entity's Nodal and Principal
     * Nodal Officers who were asked for comments. Nothing told any of them before — the withdrawal
     * handler published no event at all, so an officer went on working a dead complaint.
     *
     * <p>Note what is NOT in the default: the complainant. They performed the action and already get
     * the on-screen confirmation the withdrawal response promises, so adding them here would
     * double-notify. An administrator can still add COMPLAINANT.
     */
    public static final String KEY_RECIPIENTS_WITHDRAWAL = "notification.recipients.withdrawal";

    /**
     * Departments the department-scoped scans iterate.
     *
     * <p>Was {@code List.of("RBIO","CEPC","CRPC")} repeated in three methods, so standing up a fourth
     * department meant finding all three copies.
     */
    public static final String KEY_SCANNED_DEPARTMENTS = "notification.scanned_departments";

    /**
     * The token a recipient list uses to mean "whoever currently holds the complaint".
     *
     * <p>A placeholder rather than a role name because the answer is per-complaint and cannot be
     * expressed as a static config value. Callers substitute the complaint's assigned officer.
     */
    public static final String RECIPIENT_COMPLAINT_OWNER = "COMPLAINT_OWNER";

    /** Placeholder for the complainant — resolved per complaint, not a role. */
    public static final String RECIPIENT_COMPLAINANT = "COMPLAINANT";

    /** Placeholder for the regulated entity's nodal officer on the complaint. */
    public static final String RECIPIENT_NODAL_OFFICER = "NODAL_OFFICER";

    /** Placeholder for the regulated entity's principal nodal officer. */
    public static final String RECIPIENT_PNO = "PNO";

    private final SystemConfigService systemConfigService;

    // ═══ Intervals ═══

    public int pendingNudgeDays() {
        return positiveDays(KEY_PENDING_NUDGE_DAYS, 5);
    }

    public int noRecordWarnDays() {
        return positiveDays(KEY_NO_RECORD_WARN_DAYS, 15);
    }

    public int noRecordEscalateDays() {
        return positiveDays(KEY_NO_RECORD_ESCALATE_DAYS, 20);
    }

    public int complainantReminderFirstDays() {
        return positiveDays(KEY_COMPLAINANT_REMINDER_FIRST_DAYS, 14);
    }

    public int complainantReminderSecondDays() {
        return positiveDays(KEY_COMPLAINANT_REMINDER_SECOND_DAYS, 21);
    }

    public int uploadLinkExpiryDays() {
        return positiveDays(KEY_UPLOAD_LINK_EXPIRY_DAYS, 7);
    }

    /** Zero is legitimate here — it means "alert on the day the deadline lapses". */
    public int reResponseAlertDelayDays() {
        int value = systemConfigService.getInt(KEY_RE_RESPONSE_ALERT_DELAY_DAYS, 0);
        if (value < 0) {
            log.warn("SYSTEM_CONFIG {} is negative ({}) — treating as 0",
                    KEY_RE_RESPONSE_ALERT_DELAY_DAYS, value);
            return 0;
        }
        return value;
    }

    // ═══ Recipients ═══

    public Set<String> pendingNudgeRecipients() {
        return recipients(KEY_RECIPIENTS_PENDING_NUDGE, RECIPIENT_COMPLAINT_OWNER);
    }

    public Set<String> noRecordWarnRecipients() {
        return recipients(KEY_RECIPIENTS_NO_RECORD_WARN, "RBIO_SUPERVISOR");
    }

    public Set<String> noRecordEscalateRecipients() {
        return recipients(KEY_RECIPIENTS_NO_RECORD_ESCALATE, "RBIO_ADMIN");
    }

    public Set<String> noRecordAssignedRecipients() {
        return recipients(KEY_RECIPIENTS_NO_RECORD_ASSIGNED, "SYSTEM_ADMIN");
    }

    public Set<String> onLeavePendingRecipients(String department) {
        return recipients(KEY_RECIPIENTS_ON_LEAVE_PENDING, department + "_ADMIN");
    }

    public Set<String> documentsUploadedRecipients() {
        return recipients(KEY_RECIPIENTS_DOCUMENTS_UPLOADED, RECIPIENT_COMPLAINT_OWNER);
    }

    public Set<String> uploadLinkExpiredRecipients() {
        return recipients(KEY_RECIPIENTS_UPLOAD_LINK_EXPIRED, RECIPIENT_COMPLAINT_OWNER);
    }

    /** UST108: the Processing Officer plus the concerned entity's NO and PNO. */
    public Set<String> withdrawalRecipients() {
        return recipients(KEY_RECIPIENTS_WITHDRAWAL,
                RECIPIENT_COMPLAINT_OWNER, RECIPIENT_NODAL_OFFICER, RECIPIENT_PNO);
    }

    public Set<String> scannedDepartments() {
        return recipients(KEY_SCANNED_DEPARTMENTS, "RBIO", "CEPC", "CRPC");
    }

    /**
     * A day count that must be at least 1.
     *
     * <p>A zero or negative interval would make {@code minusDays} select the future, so a
     * "5 days without action" job would notify on every complaint ever filed. Falling back is safer
     * than honouring the row; the bad row is logged so it stays visible.
     */
    private int positiveDays(String key, int fallback) {
        int value = systemConfigService.getInt(key, fallback);
        if (value < 1) {
            log.warn("SYSTEM_CONFIG {} must be >= 1 but is {} — falling back to {}", key, value, fallback);
            return fallback;
        }
        return value;
    }

    /**
     * A LinkedHashSet rather than {@code Set.of}: order is preserved so the department scan runs in a
     * reproducible sequence, and a duplicate in the fallback is dropped rather than throwing.
     */
    private Set<String> recipients(String key, String... fallback) {
        return systemConfigService.getSet(key, new LinkedHashSet<>(Arrays.asList(fallback)));
    }
}
