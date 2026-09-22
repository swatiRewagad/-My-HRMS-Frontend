package com.hrms.cms.service;

import com.hrms.cms.dto.AaHearingRecord;
import com.hrms.cms.entity.AppealHearing;
import com.hrms.cms.security.AaIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The real {@link AaHearingPort}: an adapter from S3A's seam onto {@link AaHearingService}.
 *
 * <p>Declaring this bean replaces S3A's interim {@code @ConditionalOnMissingBean} fallback with no
 * change required on either side. The signatures are S3A's and are not altered here; this class only
 * translates between S3A's DTO and S3C's persistence model, and records the notices owed to the
 * parties that the seam's {@code partiesToNotify} argument names.
 *
 * <p>The interim implementation wrote the sitting to {@code Appeal.hearingDate}/{@code hearingVenue}
 * and so had no history. That column pair is still maintained, but only as a denormalised mirror of
 * the operative row: APPEAL_HEARING is now the record of truth and a reschedule preserves its
 * predecessor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaHearingPortImpl implements AaHearingPort {

    private final AaHearingService hearingService;
    private final AaWorkflowNotificationService notifier;
    private final AaCitizenNoticeService noticeService;
    private final AaIdentityResolver identityResolver;

    @Override
    public AaHearingRecord schedule(String appealNumber, LocalDateTime when, String venue, String mode,
                                    List<String> partiesToNotify) {
        return listSitting(appealNumber, when, venue, mode, null, partiesToNotify);
    }

    @Override
    public AaHearingRecord reschedule(String appealNumber, LocalDateTime when, String venue, String mode,
                                      String reason, List<String> partiesToNotify) {
        return listSitting(appealNumber, when, venue, mode, reason, partiesToNotify);
    }

    /**
     * Both seam methods funnel here.
     *
     * <p>{@link AaHearingService#schedule} decides SCHEDULED vs RESCHEDULED from whether a sitting is
     * already in force, rather than trusting which seam method the caller chose: a caller that used
     * {@code schedule} for a move would otherwise silently produce a second "first hearing" and lose
     * the fact that the earlier sitting was vacated.
     */
    private AaHearingRecord listSitting(String appealNumber, LocalDateTime when, String venue,
                                        String mode, String reason, List<String> partiesToNotify) {
        String actor = orSystem(identityResolver.resolveActor());
        String role = identityResolver.resolveAaRole();

        // The seam takes an already-parsed LocalDateTime, which is the point of its contract; the
        // service parses strings for the HTTP edge, so hand it a canonical ISO value.
        AppealHearing hearing = hearingService.schedule(
                appealNumber, when.toString(), venue, mode, reason, actor, role);

        boolean isReschedule = AppealHearing.EVENT_RESCHEDULED.equals(hearing.getEventType());
        AaNotifyEvent event = isReschedule
                ? AaNotifyEvent.HEARING_RESCHEDULED
                : AaNotifyEvent.HEARING_SCHEDULED;

        if (isReschedule) {
            // The parties must not be left holding two live dates for one sitting.
            noticeService.cancelPendingForEvent(appealNumber,
                    AaNotifyEvent.HEARING_SCHEDULED.name(), "Superseded by a rescheduled hearing");
        }

        Map<String, String> params = hearingService.noticeParams(hearing);
        params.put("actor", actor);

        List<String> parties = partiesToNotify == null ? List.of() : partiesToNotify;
        notifier.recordPartyNotices(appealNumber, event, parties, params, actor);

        if (hearing.getPresidingOfficer() != null) {
            notifier.notifyOfficer(hearing.getPresidingOfficer(), appealNumber, event);
        }

        return toRecord(hearing, parties);
    }

    @Override
    public AaHearingRecord recordOutcome(String appealNumber, String outcome, String remarks) {
        String actor = orSystem(identityResolver.resolveActor());
        String role = identityResolver.resolveAaRole();

        AppealHearing hearing = hearingService.recordOutcome(appealNumber, outcome, remarks, actor, role);

        if (hearing.getPresidingOfficer() != null) {
            notifier.notifyOfficer(hearing.getPresidingOfficer(), appealNumber,
                    AaNotifyEvent.HEARING_OUTCOME_RECORDED);
        }
        return toRecord(hearing, List.of());
    }

    /** Newest first, as the seam's javadoc specifies. Never null. */
    @Override
    public List<AaHearingRecord> history(String appealNumber) {
        List<AaHearingRecord> records = new ArrayList<>();
        for (AppealHearing hearing : hearingService.history(appealNumber)) {
            records.add(toRecord(hearing, List.of()));
        }
        records.sort(Comparator.comparing(AaHearingRecord::getId,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return records;
    }

    /** Null when no sitting is fixed or the last one was completed -- exactly the seam's contract. */
    @Override
    public AaHearingRecord current(String appealNumber) {
        return hearingService.operative(appealNumber)
                .map(hearing -> toRecord(hearing, List.of()))
                .orElse(null);
    }

    private AaHearingRecord toRecord(AppealHearing hearing, List<String> parties) {
        return AaHearingRecord.builder()
                .id(hearing.getId())
                .appealNumber(hearing.getAppealNumber())
                .eventType(hearing.getEventType())
                .scheduledFor(hearing.getHearingDate())
                .venue(hearing.getHearingVenue())
                .mode(hearing.getHearingMode())
                .outcome(hearing.getOutcome())
                .remarks(hearing.getOutcomeRemarks() != null
                        ? hearing.getOutcomeRemarks() : hearing.getReason())
                .partiesNotified(parties)
                .performedBy(hearing.getPerformedBy())
                .performedByRole(hearing.getPerformedByRole())
                .performedAt(hearing.getPerformedAt())
                .build();
    }

    private static String orSystem(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor;
    }
}
