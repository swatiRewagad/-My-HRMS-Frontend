package com.hrms.cms.service;

import com.hrms.cms.entity.AaCitizenNotice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * The real {@link AaWorkflowNotifier}: an adapter from S3A's seam onto
 * {@link AaWorkflowNotificationService}.
 *
 * <p>Declaring this bean replaces S3A's interim fallback automatically. S3A owns the event vocabulary
 * ({@link AaWorkflowEvent}); this class maps it onto S3C's persisted {@link AaNotifyEvent} by name and
 * delivers on the two asymmetric surfaces:
 * <ul>
 *   <li>staff -> in-app bell + STOMP, which works, so "notified" is truthful;
 *   <li>citizens -> a PENDING obligation in AA_CITIZEN_NOTICE, because there is no email or SMS
 *       transport at all. Nothing here ever marks a citizen notice SENT.
 * </ul>
 *
 * <p>Every method is non-throwing from the caller's perspective, as the seam requires: a notification
 * outage must never roll back a legitimate workflow transition. The appeal has moved whether or not the
 * message went out, and the obligation stays queryable either way.
 *
 * <p>Note the persisted {@code eventCode} deliberately uses S3C's own vocabulary rather than S3A's enum
 * name. It is an audit value on a legal record, so it must not shift if an interface constant is later
 * renamed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaWorkflowNotifierImpl implements AaWorkflowNotifier {

    private final AaWorkflowNotificationService delegate;

    @Override
    public void notifyAppellant(String appealNumber, AaWorkflowEvent event) {
        try {
            AaNotifyEvent mapped = map(event);
            if (mapped == null) {
                log.debug("AA notify: {} has no citizen-facing notice", event);
                return;
            }
            delegate.notifyAppellant(appealNumber, mapped, Map.of());
        } catch (Exception e) {
            // Never propagate: the seam's contract is that a notification failure cannot fail the
            // transition that caused it.
            log.warn("AA notify: could not record an appellant notice for {} on {}: {}",
                    event, appealNumber, e.getMessage());
        }
    }

    @Override
    public void notifyOfficer(String userId, String appealNumber, AaWorkflowEvent event) {
        try {
            AaNotifyEvent mapped = map(event);
            if (mapped == null) {
                return;
            }
            delegate.notifyOfficer(userId, appealNumber, mapped);
        } catch (Exception e) {
            log.warn("AA notify: could not notify officer {} of {} on {}: {}",
                    userId, event, appealNumber, e.getMessage());
        }
    }

    /**
     * Notifies the RBIO/CEPC owner picking a remanded complaint back up.
     *
     * <p>The message references the PARENT COMPLAINT, not the appeal: the recipient works outside the
     * AA module and an appeal number would send them somewhere they have no access to.
     */
    @Override
    public void notifyRemandTarget(String userId, String complaintNumber, String appealNumber) {
        try {
            delegate.notifyRemandTarget(userId, complaintNumber,
                    Map.of("appealNumber", appealNumber == null ? "" : appealNumber));
        } catch (Exception e) {
            log.warn("AA notify: could not notify remand target {} for complaint {}: {}",
                    userId, complaintNumber, e.getMessage());
        }
    }

    /**
     * Maps S3A's event vocabulary onto S3C's.
     *
     * <p>Returns null where S3C records no message for an event, so a new S3A constant degrades to
     * "nothing sent" rather than throwing on a workflow transition.
     */
    static AaNotifyEvent map(AaWorkflowEvent event) {
        if (event == null) return null;
        return switch (event) {
            case FILED -> AaNotifyEvent.APPEAL_FILED;
            case ACCEPTED -> AaNotifyEvent.APPEAL_ACCEPTED;
            case REJECTED -> AaNotifyEvent.APPEAL_REJECTED;
            case ASSIGNED_TO_REVIEWER -> AaNotifyEvent.ASSIGNED_TO_BENCH;
            case HEARING_SCHEDULED -> AaNotifyEvent.HEARING_SCHEDULED;
            case HEARING_RESCHEDULED -> AaNotifyEvent.HEARING_RESCHEDULED;
            case ORDER_PASSED -> AaNotifyEvent.ORDER_PASSED;
            case REMANDED -> AaNotifyEvent.APPEAL_REMANDED;
            case DISMISSED -> AaNotifyEvent.APPEAL_DISMISSED;
            case FORWARDED_TO_AUTHORITY -> AaNotifyEvent.FORWARDED_TO_AUTHORITY;
            case SENT_BACK -> AaNotifyEvent.SENT_BACK_REGISTRAR;
            case REASSIGNED -> AaNotifyEvent.REASSIGNED;
            case ESCALATED_TO_TIER2 -> AaNotifyEvent.ESCALATED_TO_TIER2;
            // DOCUMENTS_REQUESTED / CLOSED / REOPENED carry no S3C message yet.
            //
            // FILED is no longer here. Mapping it to null meant notifyOfficer returned early at the one
            // moment that matters most: the Designated Officer the appeal had just been placed with got
            // no bell. APPEAL_FILED is officer-only, so the appellant is still not double-notified.
            case DOCUMENTS_REQUESTED, CLOSED, REOPENED -> null;
        };
    }

    /** Exposed for tests: the status a citizen notice can reach in Phase 1. Never SENT. */
    public static String citizenNoticeTerminalStatus() {
        return AaCitizenNotice.STATUS_PENDING;
    }
}
