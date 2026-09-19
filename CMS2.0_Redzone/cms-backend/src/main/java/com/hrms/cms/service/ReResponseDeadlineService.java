package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Sets and evaluates the deadline a Regulated Entity has to answer a communication (UST780, UST637).
 *
 * <h2>Why this exists</h2>
 * {@code Complaint.reResponseDeadline} had exactly ONE writer in the entire repository —
 * {@code ComplaintQueryService}'s extension-grant path — so it was NULL on every complaint that had never
 * been granted an extension, which is all of them. The 30-minute sweep
 * {@code NotificationScheduledTasks.checkReResponseDeadline} filters on {@code reResponseDeadline IS NOT
 * NULL}, so it swept an empty set and no entity was ever chased. The deadline machinery existed and
 * nothing fed it.
 *
 * <p>UST780 requires the officer to pick the date when they initiate a 13(1) Notice or an
 * "Information Required" communication, and for it to be visible on BOTH the Nodal Officer record and the
 * complaint. That is why this writes to both rather than only to the complaint: the NO record is what the
 * staleness escalations read, and a deadline the escalations cannot see does not chase anybody.
 *
 * <h2>Why the officer's date is validated, not trusted</h2>
 * A date in the past would be instantly overdue, and a date on a Sunday or a gazetted holiday gives the
 * entity less time than the Scheme intends. Both are refused with a named reason rather than silently
 * adjusted: an officer who typed the wrong year should be told, not have a different date substituted
 * behind their back. Holiday awareness comes from {@link BusinessHoursService}, which already reads the
 * HOLIDAYS master — reinventing the arithmetic here would produce a second, divergent calendar.
 *
 * <h2>Overdue is computed on the SERVER</h2>
 * UST637 requires the comparison against the deadline to be a control, not a per-page-load client
 * calculation. A browser comparing two dates can be wrong about the timezone, can be stale, and cannot be
 * relied on for a statutory window. {@link #isOverdue(Complaint)} is the single definition, and it is what
 * both the sweep and the API response use.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReResponseDeadlineService {

    /**
     * Days the RE gets to respond when no explicit date is chosen.
     *
     * <p>Read from SYSTEM_CONFIG so RBI can change the window without a release. The key was seeded long
     * ago with a value of 15 and read by NOTHING — meanwhile {@code ReResponsivenessService} hardcoded 30,
     * so the two disagreed about the same statutory window. That is now resolved by making this class the
     * single definition: see {@link #responseWindowDays()} and {@link #windowEndFrom(LocalDateTime)}, which
     * the responsiveness tracker calls rather than keeping its own copy.
     */
    public static final String CFG_RESPONSE_DAYS = "timeline.re.response_deadline_days";
    private static final int DEFAULT_RESPONSE_DAYS = 15;

    private final ComplaintRepository complaintRepository;
    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;
    private final BusinessHoursService businessHoursService;
    private final SystemConfigService systemConfigService;

    /** The outcome of setting a deadline, so the caller can report precisely what was refused. */
    public record DeadlineResult(boolean accepted, LocalDate deadline, String reason) {

        static DeadlineResult set(LocalDate deadline, String reason) {
            return new DeadlineResult(true, deadline, reason);
        }

        static DeadlineResult refused(String reason) {
            return new DeadlineResult(false, null, reason);
        }
    }

    /**
     * Records the RE's response deadline for a communication on a complaint.
     *
     * @param chosenDate the date the officer picked, or null to use the configured window
     * @param communication what the deadline is for — "13(1) Notice", "Information Required" — recorded in
     *                     the reason so a reviewer can tell which communication set it
     */
    @Transactional
    public DeadlineResult setDeadline(Complaint complaint, LocalDate chosenDate, String communication) {
        DeadlineResult validation = validate(
                chosenDate != null ? chosenDate : defaultDeadline(), chosenDate != null);
        if (!validation.accepted()) {
            return validation;
        }

        // The VALIDATED date, not the requested one. A configured window that landed on a weekend or a
        // gazetted holiday is corrected forward inside validate(), and reading the original variable here
        // would discard that correction and store a deadline on a day the entity cannot act.
        LocalDate deadline = validation.deadline();

        complaint.setReResponseDeadline(deadline);
        complaintRepository.save(complaint);

        // UST780: also on the Nodal Officer record, which is what the staleness escalations read. A
        // deadline only on the complaint would be invisible to the job meant to act on it.
        Optional<NodalOfficerRecord> record =
                nodalOfficerRecordRepository.findFirstByComplaintNumber(complaint.getComplaintNumber());
        if (record.isPresent()) {
            record.get().setReResponseDeadline(deadline);
            record.get().setDeadlineCommunication(communication);
            nodalOfficerRecordRepository.save(record.get());
        } else {
            // Not an error: the complaint may predate nodal auto-creation. Said plainly because the
            // escalations will not chase this entity until a record exists.
            log.warn("Complaint {} has no Nodal Officer record, so the {} deadline {} is on the complaint "
                            + "only and the staleness sweep will not see it",
                    complaint.getComplaintNumber(), communication, deadline);
        }

        log.info("{} deadline for complaint {} set to {}{}",
                communication, complaint.getComplaintNumber(), deadline,
                chosenDate == null ? " (configured default window)" : " (chosen by the officer)");

        return DeadlineResult.set(deadline,
                communication + " response deadline set to " + deadline
                        + (chosenDate == null ? " using the configured window" : ""));
    }

    /**
     * Rejects a deadline that would be unusable, naming which rule it broke.
     *
     * <p>Only an officer-CHOSEN date is refused. A computed default is corrected forward instead: refusing
     * it would block the notice entirely because the officer supplied nothing wrong, and the fault would be
     * in configuration they cannot see.
     */
    private DeadlineResult validate(LocalDate deadline, boolean officerChosen) {
        LocalDate today = LocalDate.now();

        if (deadline.isBefore(today)) {
            return officerChosen
                    ? DeadlineResult.refused("The response deadline " + deadline + " is in the past. "
                            + "An entity cannot be given a window that has already closed.")
                    : DeadlineResult.set(nextBusinessDay(today), "Configured window fell in the past");
        }

        if (!businessHoursService.isBusinessDay(deadline)) {
            return officerChosen
                    ? DeadlineResult.refused("The response deadline " + deadline
                            + " is a weekend or a gazetted holiday, so the entity would have less time than "
                            + "the Scheme allows. Choose the next working day.")
                    : DeadlineResult.set(nextBusinessDay(deadline), "Configured window landed on a non-working day");
        }

        return DeadlineResult.set(deadline, "valid");
    }

    /**
     * The statutory window, in days, that a Regulated Entity gets to respond.
     *
     * <p>Public because it is the ONE definition of that window. {@code ReResponsivenessService} previously
     * hardcoded 30 while this key is seeded 15, so an entity was chased on one schedule and judged breached
     * on another. Both now read this.
     */
    public int responseWindowDays() {
        return systemConfigService != null
                ? systemConfigService.getInt(CFG_RESPONSE_DAYS, DEFAULT_RESPONSE_DAYS)
                : DEFAULT_RESPONSE_DAYS;
    }

    /**
     * When the configured window, started at {@code from}, runs out — holidays and weekends excluded.
     *
     * <p>The multiplier is {@link BusinessHoursService#getBusinessHoursPerDay()}, never a literal. A literal
     * 8 against a 9-hour working day (the configured default is 09:00–18:00) silently shortens the window by
     * an eighth: a nominal 30 days becomes about 26.7. Since this value decides both when an entity is
     * chased and when a complaint may proceed without their reply, a short window can cost the entity its
     * chance to answer.
     */
    public LocalDateTime windowEndFrom(LocalDateTime from) {
        return windowEndFrom(from, responseWindowDays());
    }

    /**
     * The same calculation for a window the caller has ALREADY read.
     *
     * <p>Exists so a caller that also stores the day count can read the config exactly once. Reading it
     * twice would let a mid-request config change produce a stored window length that disagrees with the
     * stored expiry date — a smaller version of the very split-brain this class was introduced to end.
     */
    public LocalDateTime windowEndFrom(LocalDateTime from, int windowDays) {
        return businessHoursService.calculateDueDate(
                from, windowDays * businessHoursService.getBusinessHoursPerDay());
    }

    /** The configured window from today, skipping non-working days. */
    private LocalDate defaultDeadline() {
        return windowEndFrom(LocalDate.now().atStartOfDay()).toLocalDate();
    }

    private LocalDate nextBusinessDay(LocalDate from) {
        LocalDate candidate = from.plusDays(1);
        // Bounded rather than while(true): a misconfigured HOLIDAYS table that marked every day a holiday
        // would otherwise hang the request thread that is trying to issue a statutory notice.
        for (int i = 0; i < 30 && !businessHoursService.isBusinessDay(candidate); i++) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    /**
     * Whether the RE has missed its deadline on this complaint — the single server-side definition (UST637).
     *
     * <p>Returns false once the entity has responded, which is what makes the UI highlight clear
     * automatically rather than needing a separate "un-highlight" step. Both conditions are checked because
     * neither field alone is sufficient: the portal response path sets {@code reRespondedAt} while the
     * triage path sets only the tracker, so trusting one would leave some answered complaints still flagged.
     */
    public boolean isOverdue(Complaint complaint) {
        if (complaint == null || complaint.getReResponseDeadline() == null) {
            return false;
        }
        if (hasResponded(complaint)) {
            return false;
        }
        return complaint.getReResponseDeadline().isBefore(LocalDate.now());
    }

    /** Days past the deadline, or 0 when not overdue. Exposed so the UI need not subtract dates itself. */
    public long daysOverdue(Complaint complaint) {
        if (!isOverdue(complaint)) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(complaint.getReResponseDeadline(), LocalDate.now());
    }

    private boolean hasResponded(Complaint complaint) {
        String status = complaint.getStatus();
        return status != null && ("re_responded".equalsIgnoreCase(status)
                || "resolved".equalsIgnoreCase(status)
                || "closed".equalsIgnoreCase(status));
    }
}
