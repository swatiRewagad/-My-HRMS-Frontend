package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AaCitizenNotice;
import com.hrms.cms.repository.AppealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single place AA workflow transitions turn into notifications.
 *
 * <p>Two surfaces, because the two audiences have very different guarantees:
 * <ul>
 *   <li><b>Staff</b> go through {@link NotificationService} -- an IN_APP_NOTIFICATIONS row plus a
 *       STOMP push to the bell. That channel genuinely works end to end, so "notified" is truthful.
 *   <li><b>Citizens and other parties</b> go to {@link AaCitizenNoticeService} as a PENDING
 *       obligation. There is no email or SMS gateway in cms-backend, so nothing is actually
 *       delivered, and no caller may claim it was.
 * </ul>
 *
 * <p>Everything is deferred to {@code afterCommit}. NotificationService.send is {@code @Async} on
 * another bean and commits immediately, so notifying inline would tell an appellant that an order had
 * issued and then roll the order back -- the single worst failure available in this module. When
 * there is no active transaction (a direct call from a unit test) it runs inline.
 *
 * <p>Notification failure NEVER fails the transition. A hearing correctly scheduled but unannounced is
 * recoverable from the outbox; a hearing lost because a notice could not be written is not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaWorkflowNotificationService {

    private static final String NOTIFICATION_TYPE = "AA_WORKFLOW";
    private static final String ENTITY_TYPE = "APPEAL";

    private final NotificationService notificationService;
    private final AaCitizenNoticeService citizenNoticeService;
    private final AppealRepository appealRepository;

    /**
     * Records the notice owed to the appellant for an event, and pushes nothing anywhere.
     *
     * <p>Deferred to afterCommit like the staff path: an obligation raised by a transition that then
     * rolled back is a notice we do not owe, and a future gateway would send it for real.
     */
    public void notifyAppellant(String appealNumber, AaNotifyEvent event, Map<String, String> params) {
        if (event == null || !event.notifiesAppellant()) {
            return;
        }
        afterCommit(() -> {
            Appeal appeal = appealRepository.findByAppealNumber(appealNumber).orElse(null);
            if (appeal == null) {
                log.warn("AA notify: no appeal {} to notify the appellant about", appealNumber);
                return;
            }
            Map<String, String> merged = withAppealParams(appealNumber, params);
            citizenNoticeService.record(appealNumber, event.name(),
                    AaCitizenNotice.PARTY_APPELLANT,
                    appeal.getAppellantName(), appeal.getAppellantEmail(), appeal.getAppellantPhone(),
                    event.appellantKey(), merged,
                    dedupeKey(event, params), actorOrSystem(params));
        });
    }

    /** Staff notification: bell + websocket. Safe to call with a role literal such as AA_ADMIN. */
    public void notifyOfficer(String userId, String appealNumber, AaNotifyEvent event) {
        if (userId == null || userId.isBlank() || event == null) {
            return;
        }
        afterCommit(() -> {
            try {
                notificationService.send(userId, NOTIFICATION_TYPE, event.officerKey(),
                        appealNumber, appealNumber, ENTITY_TYPE, "/aa/appeal/" + appealNumber);
            } catch (Exception e) {
                log.warn("AA notify: could not notify officer {} about {} on {}: {}",
                        userId, event, appealNumber, e.getMessage());
            }
        });
    }

    /**
     * Notifies the office an appeal is remanded back to.
     *
     * <p>The target is a ROLE literal, not a resolved user: RBIO/CEPC ownership of the parent
     * complaint is not modelled as a single accountable user id anywhere we can read from here, and
     * inventing one would misattribute the work. The bell already supports role-addressed rows.
     */
    public void notifyRemandTarget(String targetRoleOrUser, String complaintNumber, Map<String, String> params) {
        if (targetRoleOrUser == null || targetRoleOrUser.isBlank()) {
            return;
        }
        // Addressed by COMPLAINT number: the recipient works outside AA, and an appeal-scoped action
        // URL would send them to a screen they cannot open.
        afterCommit(() -> {
            try {
                notificationService.send(targetRoleOrUser, NOTIFICATION_TYPE,
                        AaNotifyEvent.APPEAL_REMANDED.officerKey(),
                        complaintNumber, complaintNumber, "COMPLAINT",
                        "/complaints/" + complaintNumber);
            } catch (Exception e) {
                log.warn("AA notify: could not notify remand target {} for complaint {}: {}",
                        targetRoleOrUser, complaintNumber, e.getMessage());
            }
        });
    }

    /**
     * Records notices for the parties selected on a hearing form.
     *
     * <p>The loop is HERE rather than inside the notice service on purpose: each recipient must land in
     * its own REQUIRES_NEW transaction, and a self-invoked call would silently lose that propagation,
     * collapsing all recipients back into one all-or-nothing unit.
     *
     * @param parties values from the UI: appellant / respondent / ombudsman
     * @return how many obligations were recorded
     */
    public int recordPartyNotices(String appealNumber, AaNotifyEvent event, List<String> parties,
                                 Map<String, String> params, String actor) {
        if (parties == null || parties.isEmpty() || event == null) {
            return 0;
        }
        Appeal appeal = appealRepository.findByAppealNumber(appealNumber).orElse(null);
        if (appeal == null) {
            return 0;
        }

        Map<String, String> merged = withAppealParams(appealNumber, params);
        String dedupe = dedupeKey(event, params);
        int recorded = 0;

        for (String party : parties) {
            if (party == null || party.isBlank()) continue;
            String role = normaliseParty(party);
            if (role == null) continue;

            AaCitizenNotice saved = switch (role) {
                case AaCitizenNotice.PARTY_APPELLANT -> citizenNoticeService.record(
                        appealNumber, event.name(), role,
                        appeal.getAppellantName(), appeal.getAppellantEmail(), appeal.getAppellantPhone(),
                        event.appellantKey(), merged, dedupe, actor);
                // The regulated entity and the Ombudsman office have no contact column reachable from
                // the appeal, so the obligation is recorded addressably-empty (channel NONE) rather
                // than skipped. An unaddressable notice is a finding for operations, not a no-op.
                case AaCitizenNotice.PARTY_RESPONDENT -> citizenNoticeService.record(
                        appealNumber, event.name(), role,
                        appeal.getEntityName(), null, null,
                        event.appellantKey(), merged, dedupe, actor);
                case AaCitizenNotice.PARTY_OMBUDSMAN -> citizenNoticeService.record(
                        appealNumber, event.name(), role,
                        appeal.getEntityRegion(), null, null,
                        event.appellantKey(), merged, dedupe, actor);
                default -> null;
            };
            if (saved != null) recorded++;
        }
        return recorded;
    }

    /** Maps the UI's lowercase party names onto the persisted vocabulary. */
    static String normaliseParty(String party) {
        return switch (party.trim().toLowerCase()) {
            case "appellant", "complainant" -> AaCitizenNotice.PARTY_APPELLANT;
            case "respondent", "entity", "re" -> AaCitizenNotice.PARTY_RESPONDENT;
            case "ombudsman", "rbio" -> AaCitizenNotice.PARTY_OMBUDSMAN;
            default -> null;
        };
    }

    private Map<String, String> withAppealParams(String appealNumber, Map<String, String> params) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("appealNumber", appealNumber);
        if (params != null) {
            params.forEach((key, value) -> {
                if (value != null) merged.put(key, value);
            });
        }
        merged.remove("actor");
        return merged;
    }

    /**
     * A stable key per logical event so a retried request cannot double-notify.
     *
     * <p>Callers pass {@code dedupe} (a hearing row id, an order revision). Falling back to the event
     * name alone would make a SECOND legitimate hearing on the same appeal look like a duplicate, so
     * the fallback includes the hearing date when one is present.
     */
    private String dedupeKey(AaNotifyEvent event, Map<String, String> params) {
        if (params != null) {
            String explicit = params.get("dedupe");
            if (explicit != null && !explicit.isBlank()) {
                return truncate(explicit, 100);
            }
            String when = params.get("hearingDate");
            if (when != null && !when.isBlank()) {
                return truncate(event.name() + ":" + when, 100);
            }
        }
        return truncate(event.name(), 100);
    }

    private static String actorOrSystem(Map<String, String> params) {
        if (params == null) return "system";
        String actor = params.get("actor");
        return (actor == null || actor.isBlank()) ? "system" : actor;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** The S2C deferral pattern: run after commit when in a transaction, inline when not. */
    private void afterCommit(Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (Exception e) {
                log.warn("AA notify: notification step failed: {}", e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guarded.run();
                }
            });
        } else {
            guarded.run();
        }
    }
}
