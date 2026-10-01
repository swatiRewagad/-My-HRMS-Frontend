package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealHearing;
import com.hrms.cms.repository.AppealHearingRepository;
import com.hrms.cms.repository.AppealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hearing lifecycle for an appeal: schedule, reschedule, adjourn, complete.
 *
 * <p>Every operation APPENDS an {@link AppealHearing} row. Nothing here ever rewrites the particulars
 * of a past hearing event -- superseding stamps a link on the old row and leaves its date, venue and
 * actor exactly as the parties were told. That is the whole point of the table: a statutory hearing
 * that was moved twice has to be provably different from one that was always on its final date.
 *
 * <p>{@code APPEALS.hearing_date} / {@code hearing_venue} are still maintained as a DENORMALISED
 * mirror of the operative hearing, because list screens, the status endpoint and existing tests read
 * them. The table is the record of truth; those two columns are a cache of its live row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaHearingService {

    static final String CFG_CONFLICT_ENABLED = "cms.aa.hearing.conflict_check_enabled";
    static final String CFG_SLOT_MINUTES = "cms.aa.hearing.slot_minutes";
    static final String CFG_MIN_NOTICE_DAYS = "cms.aa.hearing.min_notice_days";

    /** A listing occupies the officer for this long unless configured otherwise. */
    private static final int DEFAULT_SLOT_MINUTES = 60;

    /**
     * Default minimum clear days between recording a hearing and the hearing itself.
     *
     * <p>NOT a Scheme figure -- the Scheme's notice period has not been supplied to this session, so
     * this is an operational default in SYSTEM_CONFIG and is flagged for legal sign-off. It is
     * deliberately advisory: it warns, it does not refuse, because refusing a hearing an officer has
     * genuinely agreed with the parties would be worse than a short notice period.
     */
    private static final int DEFAULT_MIN_NOTICE_DAYS = 7;

    private final AppealHearingRepository hearingRepository;
    private final AppealRepository appealRepository;
    private final SystemConfigService systemConfigService;

    /** Raised when a booking would double-list an officer. A control, not a warning. */
    public static class HearingConflictException extends RuntimeException {
        private final String conflictingAppeal;
        private final LocalDateTime conflictingAt;

        public HearingConflictException(String message, String conflictingAppeal, LocalDateTime conflictingAt) {
            super(message);
            this.conflictingAppeal = conflictingAppeal;
            this.conflictingAt = conflictingAt;
        }

        public String getConflictingAppeal() {
            return conflictingAppeal;
        }

        public LocalDateTime getConflictingAt() {
            return conflictingAt;
        }
    }

    /**
     * Fixes the first hearing, or moves an existing one.
     *
     * <p>Chooses SCHEDULED vs RESCHEDULED itself from whether an operative row already exists, so a
     * caller cannot mislabel a move as a fresh listing and lose the audit trail.
     */
    @Transactional
    public AppealHearing schedule(String appealNumber, String whenRaw, String venue, String mode,
                                  String reason, String actor, String actorRole) {
        Appeal appeal = requireAppeal(appealNumber);
        LocalDateTime when = parseHearingDateTime(whenRaw);

        String officer = appeal.getAssignedOfficer();
        assertNoConflict(officer, appealNumber, when);

        Optional<AppealHearing> existing = hearingRepository.findOperativeOne(appealNumber);
        boolean isReschedule = existing.isPresent();

        if (isReschedule && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(
                    "A reason is required to reschedule a hearing that the parties were already notified about");
        }

        AppealHearing event = hearingRepository.save(AppealHearing.builder()
                .appealNumber(appealNumber)
                .sequenceNo(hearingRepository.maxSequenceNo(appealNumber) + 1)
                .eventType(isReschedule ? AppealHearing.EVENT_RESCHEDULED : AppealHearing.EVENT_SCHEDULED)
                .hearingDate(when)
                .hearingVenue(trimToNull(venue))
                .hearingMode(normaliseMode(mode))
                .presidingOfficer(officer)
                .reason(trimToNull(reason))
                .performedBy(actor == null ? "system" : actor)
                .performedByRole(actorRole)
                .build());

        existing.ifPresent(prior -> supersede(prior, event.getId()));

        appeal.setHearingDate(when);
        appeal.setHearingVenue(trimToNull(venue));
        appealRepository.save(appeal);

        return event;
    }

    /**
     * Adjourns the operative hearing without fixing a new date.
     *
     * <p>Appends an ADJOURNED row and supersedes the listing, so the slot is released and the officer
     * is free. A new date is a separate schedule call -- adjourning and re-listing are distinct facts
     * and the parties may be told about them at different times.
     */
    @Transactional
    public AppealHearing adjourn(String appealNumber, String reason, String actor, String actorRole) {
        requireAppeal(appealNumber);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to adjourn a hearing");
        }

        AppealHearing operative = hearingRepository.findOperativeOne(appealNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "There is no scheduled hearing on " + appealNumber + " to adjourn"));

        AppealHearing event = hearingRepository.save(AppealHearing.builder()
                .appealNumber(appealNumber)
                .sequenceNo(hearingRepository.maxSequenceNo(appealNumber) + 1)
                .eventType(AppealHearing.EVENT_ADJOURNED)
                .hearingDate(operative.getHearingDate())
                .hearingVenue(operative.getHearingVenue())
                .hearingMode(operative.getHearingMode())
                .presidingOfficer(operative.getPresidingOfficer())
                .outcome("ADJOURNED")
                .reason(trimToNull(reason))
                .performedBy(actor == null ? "system" : actor)
                .performedByRole(actorRole)
                .build());

        supersede(operative, event.getId());
        return event;
    }

    /**
     * Records what happened at the hearing.
     *
     * <p>Appends a COMPLETED row carrying the outcome and supersedes the listing. The scheduled row is
     * NOT mutated to hold the outcome: the date and venue the parties were notified about must survive
     * verbatim, and a completed hearing must not keep occupying the officer's calendar.
     */
    @Transactional
    public AppealHearing recordOutcome(String appealNumber, String outcome, String remarks,
                                       String actor, String actorRole) {
        requireAppeal(appealNumber);
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("An outcome is required to record a hearing result");
        }

        AppealHearing operative = hearingRepository.findOperativeOne(appealNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "There is no scheduled hearing on " + appealNumber + " to record an outcome for"));

        AppealHearing event = hearingRepository.save(AppealHearing.builder()
                .appealNumber(appealNumber)
                .sequenceNo(hearingRepository.maxSequenceNo(appealNumber) + 1)
                .eventType(AppealHearing.EVENT_COMPLETED)
                .hearingDate(operative.getHearingDate())
                .hearingVenue(operative.getHearingVenue())
                .hearingMode(operative.getHearingMode())
                .presidingOfficer(operative.getPresidingOfficer())
                .outcome(outcome.trim().toUpperCase())
                .outcomeRemarks(trimToNull(remarks))
                .performedBy(actor == null ? "system" : actor)
                .performedByRole(actorRole)
                .build());

        supersede(operative, event.getId());
        return event;
    }

    @Transactional(readOnly = true)
    public List<AppealHearing> history(String appealNumber) {
        return hearingRepository.findByAppealNumberOrderBySequenceNoAscIdAsc(appealNumber);
    }

    @Transactional(readOnly = true)
    public Optional<AppealHearing> operative(String appealNumber) {
        return hearingRepository.findOperativeOne(appealNumber);
    }

    /**
     * Refuses a booking that would put the same officer in two hearings at once.
     *
     * <p>Server-side because a browser check is not a control: two officers on two tabs, a replayed
     * request or a direct API call all bypass the UI entirely. Config-gated so operations can stand it
     * down without a deploy if a genuine back-to-back listing is required.
     */
    void assertNoConflict(String officer, String appealNumber, LocalDateTime when) {
        if (officer == null || officer.isBlank()) {
            // Nothing to conflict with: an unassigned appeal occupies nobody's calendar.
            return;
        }
        if (!systemConfigService.getBoolean(CFG_CONFLICT_ENABLED, true)) {
            return;
        }

        int slotMinutes = systemConfigService.getInt(CFG_SLOT_MINUTES, DEFAULT_SLOT_MINUTES);
        if (slotMinutes <= 0) {
            log.warn("AA hearing: slot of {} minutes is not usable -- conflict check skipped", slotMinutes);
            return;
        }

        // Overlap test: an existing booking collides when it starts inside (when - slot, when + slot).
        LocalDateTime windowStart = when.minusMinutes(slotMinutes - 1L);
        LocalDateTime windowEnd = when.plusMinutes(slotMinutes);

        List<AppealHearing> clashes = hearingRepository.findOfficerBookingsInWindow(
                officer, appealNumber, windowStart, windowEnd);

        if (!clashes.isEmpty()) {
            AppealHearing clash = clashes.get(0);
            throw new HearingConflictException(
                    "Officer " + officer + " is already listed for appeal " + clash.getAppealNumber()
                            + " at " + clash.getHearingDate(),
                    clash.getAppealNumber(), clash.getHearingDate());
        }
    }

    /**
     * Accepts both a full timestamp and a date-only value.
     *
     * <p>The UI sends {@code 2026-09-30T11:00:00}, but seeded and API callers send {@code 2026-09-30},
     * on which {@code LocalDateTime.parse} throws {@code DateTimeParseException} -- an unchecked
     * exception the controller did not handle, so it surfaced as a 500. A date-only hearing is a real
     * intent ("that day, time to be confirmed"), so it resolves to 11:00 local rather than midnight,
     * which would imply a hearing nobody could attend.
     */
    static LocalDateTime parseHearingDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("A hearing date is required");
        }
        String value = raw.trim();
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException notATimestamp) {
            try {
                return LocalDate.parse(value).atTime(11, 0);
            } catch (DateTimeParseException notADate) {
                throw new IllegalArgumentException(
                        "Hearing date '" + raw + "' is not a valid date or date-time");
            }
        }
    }

    /** Advisory only -- see DEFAULT_MIN_NOTICE_DAYS. Exposed so callers can surface a warning. */
    public boolean isShortNotice(LocalDateTime when) {
        int minDays = systemConfigService.getInt(CFG_MIN_NOTICE_DAYS, DEFAULT_MIN_NOTICE_DAYS);
        if (minDays <= 0) return false;
        return Duration.between(LocalDateTime.now(), when).toDays() < minDays;
    }

    /** Interpolation values for a hearing notice. Kept here so every caller sends the same shape. */
    public Map<String, String> noticeParams(AppealHearing hearing) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("hearingDate", hearing.getHearingDate().toString());
        if (hearing.getHearingVenue() != null) params.put("hearingVenue", hearing.getHearingVenue());
        if (hearing.getHearingMode() != null) params.put("hearingMode", hearing.getHearingMode());
        params.put("dedupe", "H" + hearing.getId());
        return params;
    }

    private void supersede(AppealHearing prior, Long replacedBy) {
        prior.setSupersededAt(LocalDateTime.now());
        prior.setSupersededById(replacedBy);
        hearingRepository.save(prior);
    }

    private Appeal requireAppeal(String appealNumber) {
        return appealRepository.findByAppealNumber(appealNumber)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealNumber));
    }

    static String normaliseMode(String mode) {
        if (mode == null || mode.isBlank()) return AppealHearing.MODE_IN_PERSON;
        return switch (mode.trim().toUpperCase().replace('-', '_').replace(' ', '_')) {
            case "VIDEO", "VC", "VIRTUAL", "ONLINE" -> AppealHearing.MODE_VIDEO;
            case "HYBRID" -> AppealHearing.MODE_HYBRID;
            default -> AppealHearing.MODE_IN_PERSON;
        };
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
