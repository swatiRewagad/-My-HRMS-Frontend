package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceComplainantHistory;
import com.hrms.cms.entity.AssistanceJobLock;
import com.hrms.cms.repository.AssistanceComplainantHistoryRepository;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.ComplainantHistorySourceRepository;
import com.hrms.cms.repository.projection.ComplainantHistoryProjections.ComplainantRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds the complainant filing-history projection from the register, on a schedule, under a lease.
 *
 * <h2>Why a job and not a query</h2>
 * {@code §6.2}'s governing rule is that "rollups are computed on a schedule, never on request". The
 * aggregate this job computes is the CONTACT FAN-OUT GUARD — a {@code COUNT(DISTINCT)} pairing of every
 * email against every phone in the register — and running that behind a complaint screen would put a full
 * scan on every officer who opened one.
 *
 * <p>Note what is NOT precomputed here: the duplicate LIST and the lifetime COUNT. Those are two bounded
 * index seeks in the request path, and the argument for that is made in
 * {@link AssistanceComplainantHistory}'s class javadoc and in the migration header — in short, the
 * duplicate signal must NAME the earlier complaints, and no cohort row can hold a list. What this job
 * precomputes is the NORMALISATION and the fan-out guard, which are the two things a request genuinely
 * cannot afford.
 *
 * <h2>THE FAN-OUT GUARD IS THE REASON THIS FEATURE WORKS AT ALL</h2>
 * The brief's rule — match on email OR phone, not email alone — is correct: the two columns DISAGREE
 * about identity in this register (16 phones carry more than one email, 1 email carries more than one
 * phone), and a repeat complainant who filed once by phone and once by email is one person.
 *
 * <p>Applied naively it destroys the feature. MEASURED 2026-10-07 against {@code cms_db}:
 * <pre>
 *   distinct emails ....... 4081 over 4352 rows   (mean 1.07 complaints per email)
 *   distinct phones ......... 468 over 4354 rows   (mean 9.30 complaints per phone)
 *
 *   9876543210 .. 3527 complaints across 3427 DISTINCT EMAILS
 *   9876500033 ..   86 complaints across   85 distinct emails
 *   9876500011 ..   76 complaints across   76 distinct emails
 *   9876500091 ..   10 complaints across   10 distinct emails
 * </pre>
 * Those four values cover 3,699 of 4,354 phones. A number shared by 3,427 different people is not an
 * identity, it is a default a seeder typed. And the consequence is not a rounding error:
 * <pre>
 *   complaints receiving a duplicate signal (same entity, 30-day window)
 *     email only ..................................  227  (5.2%)
 *     email OR phone, UNGUARDED ................... 3849  (87.4%)   &lt;- the feature is destroyed
 *     email OR phone, fan-out guard applied .......  259  (5.9%)   &lt;- ships
 * </pre>
 * 87% of the register flagged as duplicate filings is not a signal, it is a banner an officer would learn
 * to ignore within a day — and the {@code 16(2)(b)} count it feeds would be evidence of nothing. Worse
 * than useless: actively dangerous, because a "vexatious" determination resting on it would be resting on
 * a seeder's default value.
 *
 * <p>So a contact key whose fan-out exceeds {@link #MAX_CONTACT_FANOUT} is written as the
 * {@link AssistanceComplainantHistory#KEY_ABSENT} sentinel and contributes no match. Measured effect:
 * phone matching still adds 32 complaints of genuine recall over email alone (259 against 227) rather
 * than 3,622 of noise.
 *
 * <p>RE-MEASURED at the end of the session, because the register grew from 4,403 to 6,598 complaints
 * while this was being built — other sessions write to {@code cms_db} concurrently, so these absolutes
 * are snapshots. Against 6,598 rows: email only 280 (4.2%), unguarded 3,897 (59.1%), guarded 312 (4.7%).
 * The guard still cuts false positives by a factor of 12.5 and phone still adds +32 complaints of real
 * recall. The unguarded rate moved from 87% to 59% only because the new rows dilute the placeholder's
 * share of the register; it is still a banner rather than a signal.
 *
 * <h2>A CARDINALITY test, not a blocklist</h2>
 * A hardcoded {@code '9876543210'} would be correct today and silently wrong the first time a different
 * placeholder was seeded — and that failure is INVISIBLE, because the feature would simply start flagging
 * everything again and nobody would be told. Counting distinct peers detects any placeholder, including
 * ones nobody has seen, and needs no maintenance.
 *
 * <p>The guard is SYMMETRIC: it is applied to the email key against distinct phones as well, because a
 * shared address like a branch mailbox or {@code noreply@} is the same failure wearing the other hat.
 * Measured today the email direction is nearly inert — the largest fan-out behind one email is 4 distinct
 * phones and exactly 1 email trips the threshold — but that is a property of this dataset and not of
 * email addresses, and the asymmetric version would have had to be explained rather than merely stated.
 *
 * <p>THE SURVIVING COST, stated rather than implied: a phone legitimately shared by a large family or by
 * a village common service point is suppressed, and those complainants are matched on email alone. That
 * is the correct trade — a MISSED duplicate costs an officer a convenience, a FALSE one costs a citizen a
 * "vexatious" determination — but it is a cost, and it is why the threshold is a named constant rather
 * than a literal.
 *
 * <h2>TWO PASSES, and the reason is that one normaliser must do both halves</h2>
 * Pass 1 reads the register and builds the fan-out maps. Pass 2 reads it again and writes the rows,
 * consulting those maps. The scan is paid twice, deliberately.
 *
 * <p>The alternative — measuring the fan-out with a native {@code GROUP BY} and
 * {@code REGEXP_REPLACE} — was rejected because it would measure the fan-out with one implementation
 * while writing the KEYS with another. A single disagreement about, say, a trailing non-breaking space
 * would suppress a phone whose key was nonetheless written under its real value, or the reverse, and the
 * resulting signal would be wrong in a way no test over seeded data would catch. One normaliser, one
 * language, no possible divergence. 4,403 rows read twice is a few hundred milliseconds every six hours.
 *
 * <h2>This is a COUNT, not a prediction</h2>
 * No inference, no model, no case text, no score. The output is one normalised row per complaint, and the
 * reader turns that into "3 earlier complaints by this complainant against HDFC Bank in the last 30 days"
 * and "8 lifetime filings, 1 closed as non-maintainable". The officer decides whether that is vexatious;
 * this returns counts and never a verdict.
 *
 * <h2>What this projection cannot say today, measured</h2>
 * <ul>
 *   <li>{@code NON_MAINTAINABLE} is {@code 'N'} on essentially every row THAT MATTERS HERE. The
 *       register-wide count grew from 47 to 665 during the session, but only <b>6</b> of those belong to
 *       a complainant with more than one filing — against roughly 490 filings those repeat complainants
 *       account for. So the lifetime FILING count is real while the non-maintainable count is almost
 *       always zero for exactly the complainants the {@code 16(2)(b)} signal is about. That must NOT be
 *       read as evidence AGAINST a determination; it is evidence that the determination is not being
 *       recorded.
 *   <li>32 of 6,598 complaints carry neither email nor phone and are dropped entirely — they can match
 *       nothing and would only pool under the sentinel.
 *   <li>290 carry no {@code entity_code}, so they count toward a lifetime total but can never raise a
 *       SAME-ENTITY duplicate signal.
 *   <li>The 193 complaints subjected {@code 'QA pass-4 session C duplicate detection seed'} are NOT
 *       duplicate pairs: all 193 hold a distinct email and a distinct phone and only 18 carry an
 *       entity_code. A reader expecting a ready-made cluster from the subject line will not find one.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistanceComplainantHistoryRefreshService {

    /**
     * Complaints read per page.
     *
     * <p>Keyset-paged rather than read whole, so the job's heap does not scale with the register. 2,000
     * projections of twelve scalars is a few hundred kilobytes; the whole register at once is 4,403 rows
     * today and unbounded later, and "it fit in dev" is how a job comes to fail only in production.
     * Matches {@code AssistanceClauseAffinityRefreshService.PAGE_SIZE} deliberately — two jobs with the
     * same shape and different page sizes invite the reader to look for a reason there is not.
     */
    static final int PAGE_SIZE = 2_000;

    /**
     * Maximum distinct peer contacts before a contact key is treated as a PLACEHOLDER, not an identity.
     *
     * <p>THREE. The single most consequential constant in this feature — see the class javadoc for the
     * 87%-to-5.9% measurement it produces.
     *
     * <p>Three and not one, because a person legitimately has more than one email: the same complainant
     * filing from a work address and a personal one, across two complaints, is two distinct emails behind
     * one phone and must still be recognised as one person. Measured, 16 phones in this register carry
     * more than one email and the largest LEGITIMATE-looking fan-out is small, while the placeholders sit
     * three orders of magnitude away at 3,427 — so the threshold is not finely balanced and any value
     * from about 3 to about 50 separates the two populations identically on this data. Three is chosen as
     * the low end of that plateau because the asymmetry of harm points that way: suppressing a real
     * identity costs a missed convenience, admitting a placeholder costs a citizen a false "vexatious"
     * determination.
     *
     * <p>Three and not zero, which would be "a phone may never be an identity" — that is email-only
     * matching, which the brief explicitly rejects and which measurably loses 32 complaints of real
     * recall (259 against 227).
     *
     * <p>EXCLUSIVE: a key is suppressed when its distinct-peer count is STRICTLY GREATER than this, so a
     * contact with exactly three peers survives.
     */
    static final int MAX_CONTACT_FANOUT = 3;

    /**
     * How many digits of the phone form the identity key.
     *
     * <p>Ten, the length of an Indian mobile number, taken from the TAIL so that
     * {@code '+91 98765-43210'}, {@code '09876543210'} and {@code '9876543210'} are one identity.
     * MEASURED: 0 of 4,354 non-blank phones hold fewer than 10 digits once non-digits are stripped, so the
     * tail is never a partial number that could collide with a different person's.
     *
     * <p>A phone with FEWER than this many digits is rejected outright rather than padded or used short —
     * a 6-digit fragment would match far too many people, and this feature's whole failure mode is
     * matching strangers together.
     */
    static final int PHONE_KEY_DIGITS = 10;

    /**
     * How long a taken lease lasts.
     *
     * <p>Generous relative to the measured runtime (two passes over 4,403 rows is well under a second)
     * because the failure modes are asymmetric: a lease that expires while its holder is still working
     * readmits the concurrency it exists to prevent, whereas one held too long after a crash costs at most
     * one skipped cycle. Must stay comfortably BELOW the refresh interval, or a lapsed lease would still
     * be held when the next cycle came round.
     */
    static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    private final ComplainantHistorySourceRepository sourceRepository;
    private final AssistanceComplainantHistoryRepository projectionRepository;
    private final AssistanceJobLockRepository lockRepository;

    /**
     * The assistance-wide kill switch, shared with the rail and the other refresh jobs.
     *
     * <p>Defaults to {@code false} so an environment that never set the key does not quietly acquire a
     * scheduled full-table scan.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * This feature's OWN {@code §6.2} kill switch, {@code cms.assistance.duplicate-detection.enabled}.
     *
     * <h3>Why a second switch, and why this one matters more than its siblings'</h3>
     * Every other assistance surface reports an aggregate about the register. THIS one surfaces one
     * identified person's other complaints, and feeds a count into a statutory {@code 16(2)(b)}
     * "frivolous or vexatious" determination. If anything here is found to be wrong — a placeholder
     * contact the fan-out guard did not catch, a scope leak, a mis-stated denominator — an operator must
     * be able to stop it IMMEDIATELY without also blinding every officer's rail, and the reverse
     * containment matters too: turning the rail off for an unrelated incident must not silently remove a
     * control an officer has started relying on.
     *
     * <h3>Why the JOB honours it and not only the read</h3>
     * {@code DuplicateFilingDetectionService} checks the same pair, so turning the switch off makes the
     * signal disappear immediately. If only the read honoured it, this job would carry on scanning the
     * whole register twice every six hours to maintain a table nothing read — {@code §6.2}'s requirement
     * is that the switch "stops the queries and not merely the display".
     *
     * <p>ANDed with {@link #assistanceEnabled} rather than replacing it, so the global switch still stops
     * everything. An operator reaching for the big lever gets this feature too, which is the property a
     * kill switch exists to have.
     *
     * <p>The asymmetry that remains, stated rather than hidden: turning the switch back ON makes the read
     * live before this job has run, so the signal reports whatever the last pass left. That is correct —
     * the rows carry their own {@code REFRESHED_AT} and a filing from six hours ago is still a filing —
     * but it does mean the switch is not a way to clear the projection. For a PII-bearing table that is
     * worth knowing: disabling the feature stops the READS, it does not delete the rows.
     *
     * <p>Defaults to {@code false}. An environment that never set the key shows no duplicate signal,
     * which is exactly what shipped before this feature existed. The migration is also unapplied as
     * shipped, so the default and the data agree.
     */
    @Value("${cms.assistance.duplicate-detection.enabled:false}")
    private boolean duplicateDetectionEnabled;

    /** Diagnostics only. Identifies the pod in {@code LOCKED_BY} so a lease can be traced to a holder. */
    @Value("${HOSTNAME:unknown-host}")
    private String podIdentity;

    /**
     * One refresh cycle: take the lease, rebuild, release.
     *
     * <h3>Never throws</h3>
     * A scheduled task that throws is logged by Spring and then, in some configurations, silently never
     * runs again; more importantly this job has no caller to report to. So every failure is caught, logged
     * at WARN, and the lease released. The read degrades to an empty signal when the projection is stale,
     * empty or absent — which is the behaviour an unapplied migration relies on.
     *
     * @return the number of rows written, or 0 if the cycle did not run
     */
    public int refresh() {
        if (!assistanceEnabled || !duplicateDetectionEnabled) {
            // Not a warning. The switch being off is a choice, and a job that complained about it every
            // cycle would train operators to filter the log line that matters. BOTH keys are named in the
            // one line so an operator who turned one on and not the other can see which from the log
            // rather than by reading this class.
            log.debug("Assistance complainant-history refresh skipped: cms.assistance.enabled={}, "
                    + "cms.assistance.duplicate-detection.enabled={}", assistanceEnabled,
                    duplicateDetectionEnabled);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!tryAcquireLease(now)) {
            return 0;
        }

        try {
            int written = recompute(now);
            log.info("Assistance complainant-history projection refreshed: {} rows written", written);
            return written;
        } catch (Exception e) {
            // Includes the case where the migration has not been applied — the table does not exist,
            // every statement fails, and the correct outcome is a warning and a quiet signal rather than
            // a job that brings attention to itself by failing loudly every cycle.
            log.warn("Assistance complainant-history refresh failed; the projection is left as it was: "
                    + "{}", e.toString());
            return 0;
        } finally {
            releaseLease();
        }
    }

    /**
     * Wins or loses the lease, in one statement, and never throws.
     *
     * <p>A failure here is treated as "did not get the lock", which is the safe reading: the most likely
     * cause is the lock row not existing because the migration has not been applied, and in that state
     * the job must do NOTHING. Treating an error as permission to proceed would mean the one environment
     * where the lock is unavailable is the one where every pod rebuilds at once.
     */
    private boolean tryAcquireLease(LocalDateTime now) {
        try {
            int taken = lockRepository.acquire(
                    AssistanceJobLock.LOCK_NAME_COMPLAINANT_HISTORY_REFRESH,
                    now,
                    now.plus(LEASE_DURATION),
                    podIdentity);
            if (taken == 0) {
                log.debug("Assistance complainant-history refresh skipped: another pod holds the lease");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Assistance complainant-history refresh skipped: could not take the lease ({}). "
                    + "If V121 / oracle V119 has not been applied this is expected.", e.toString());
            return false;
        }
    }

    private void releaseLease() {
        try {
            // Expired one second ago rather than exactly now, so a clock that has not advanced between
            // the release and the next acquire still satisfies the acquire's `lockedUntil <= :now`.
            lockRepository.release(AssistanceJobLock.LOCK_NAME_COMPLAINANT_HISTORY_REFRESH,
                    LocalDateTime.now().minusSeconds(1));
        } catch (Exception e) {
            // Survivable by design: the lease expires on its own, so a failed release costs one idle
            // cycle rather than a stuck projection. This is the reason it is a lease and not a flag.
            log.warn("Assistance complainant-history lease release failed; it will expire on its own: {}",
                    e.toString());
        }
    }

    /**
     * Pass 1 then pass 2 then the sweep.
     *
     * <h3>UPSERT, not truncate-and-reload</h3>
     * A truncate would leave every complaint screen with no duplicate signal for the duration of every
     * refresh — a self-inflicted degradation on a feature whose contract is to be there when the screen
     * opens. {@code UK_ACH_COMPLAINT} is the idempotency, so each complaint's row is found and
     * overwritten.
     *
     * <h3>NO transaction spanning the pass, and the reason is specific</h3>
     * There is deliberately no {@code @Transactional} here and it is not an oversight. It could not work:
     * this method is called from {@link #refresh} on {@code this}, and a self-invocation does not pass
     * through the Spring proxy, so the annotation would be INERT while reading as a guarantee — the trap
     * that is only discovered when someone relies on the rollback. Annotating {@link #refresh} instead is
     * worse: it catches its own exceptions, so a failed pass would mark the transaction rollback-only and
     * the commit would then throw out of a method whose whole contract is never to throw. So the one
     * modifying query that needs a boundary — {@code deleteStale} — declares {@code @Transactional} on the
     * REPOSITORY method, where the proxy IS the bean, and each upsert commits on its own via
     * {@code SimpleJpaRepository.save}.
     *
     * <p>What that costs is cross-complaint atomicity: a reader during a refresh can see complaint A's row
     * updated and complaint B's not yet, so a lifetime count can momentarily be short by one. What it does
     * NOT cost is a WRONG count — every row is self-consistent, written by one save — and the signal's
     * contract is a count with its denominator, both of which are derived from the same read. An
     * incomplete history is a weaker signal, not a false one. It is also why the duplicate signal is a
     * SUGGESTION: an officer is never told a complaint is a duplicate, only that earlier complaints exist.
     *
     * @param stamp the single instant every row written by this pass carries. One value for the whole
     *              pass, because the stale sweep's predicate is "older than this run" — a per-row
     *              {@code now()} would make rows written late in the pass look newer than rows written
     *              early, and the sweep could not distinguish them.
     */
    int recompute(LocalDateTime stamp) {
        ContactFanoutIndex fanout = measureContactFanout();
        int written = writeRows(fanout, stamp);

        // Only after a COMPLETE pass. A failure above propagates out of this method, so the sweep is
        // never reached with a partial pass — which would delete the entire remainder of the register's
        // history and silently drop every lifetime count. That ordering is the whole guarantee: there is
        // no transaction to roll the upserts back, so "do not sweep on a failed pass" is enforced by
        // control flow and nothing else. See AssistanceComplainantHistoryRepository#deleteStale.
        int swept = projectionRepository.deleteStale(stamp);
        if (swept > 0) {
            log.info("Assistance complainant-history: {} rows no longer correspond to a complaint and "
                    + "were removed", swept);
        }
        return written;
    }

    /**
     * PASS 1: how many distinct peers each contact key appears beside.
     *
     * <p>The one true aggregate in this feature. Builds both directions at once from a single scan, so a
     * phone's email fan-out and an email's phone fan-out are measured over exactly the same row set —
     * two scans could disagree if a complaint were filed between them, and the disagreement would be
     * invisible.
     *
     * <p>Only rows carrying BOTH contacts contribute to a fan-out count, which is the correct reading of
     * the question. "How many distinct emails does this phone appear beside" cannot be answered by a
     * complaint that recorded no email, and counting such a row as a peer of nothing would make a
     * placeholder used on phone-only complaints look clean.
     */
    private ContactFanoutIndex measureContactFanout() {
        Map<String, Set<String>> emailsPerPhone = new HashMap<>();
        Map<String, Set<String>> phonesPerEmail = new HashMap<>();

        long afterId = 0L;
        while (true) {
            List<ComplainantRow> page = sourceRepository.findComplainantRows(
                    afterId, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }

            for (ComplainantRow row : page) {
                String email = normaliseEmail(row.complainantEmail());
                String phone = normalisePhone(row.complainantPhone());
                if (email == null || phone == null) {
                    // A row with only one contact tells us nothing about either one's fan-out.
                    continue;
                }
                emailsPerPhone.computeIfAbsent(phone, k -> new HashSet<>()).add(email);
                phonesPerEmail.computeIfAbsent(email, k -> new HashSet<>()).add(phone);
            }

            afterId = page.get(page.size() - 1).complaintId();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }

        // NOTE THE CROSS-OVER, which is the one genuinely confusing line in this class and was WRONG in
        // the first draft (the unit tests caught it). `emailsPerPhone` is keyed on PHONES, so the keys it
        // reports as over-threshold are the PHONES to suppress — and `phonesPerEmail` is keyed on EMAILS
        // and yields the EMAILS to suppress. Passing them in map order would hand the phone suppressions
        // to the email field and vice versa, which is worse than it sounds: on the real register the email
        // direction is nearly inert, so the swap would effectively DISABLE the phone guard and silently
        // restore the 87% false-positive rate while every "a placeholder is suppressed" assertion that did
        // not also check the surviving column still passed.
        ContactFanoutIndex index = new ContactFanoutIndex(
                suppressedKeys(phonesPerEmail), suppressedKeys(emailsPerPhone));
        if (!index.suppressedPhones().isEmpty() || !index.suppressedEmails().isEmpty()) {
            // INFO and not DEBUG. An operator must be able to see that the guard fired and on how many
            // keys, because a suppression that silently grew would make the feature quietly go dark, and
            // a suppression that silently stopped would make it flag the whole register. The KEYS
            // themselves are not logged — they are contact data, and a log line is a place PII escapes.
            log.info("Assistance complainant-history fan-out guard: {} phone keys and {} email keys "
                            + "suppressed as shared placeholders (threshold {} distinct peers)",
                    index.suppressedPhones().size(), index.suppressedEmails().size(),
                    MAX_CONTACT_FANOUT);
        }
        return index;
    }

    /** The keys whose peer count exceeds the threshold, i.e. the placeholders. */
    private Set<String> suppressedKeys(Map<String, Set<String>> peers) {
        Set<String> suppressed = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : peers.entrySet()) {
            if (entry.getValue().size() > MAX_CONTACT_FANOUT) {
                suppressed.add(entry.getKey());
            }
        }
        return suppressed;
    }

    /**
     * PASS 2: writes one normalised row per complaint, applying the fan-out guard.
     *
     * @return how many rows were written
     */
    private int writeRows(ContactFanoutIndex fanout, LocalDateTime stamp) {
        int written = 0;
        long afterId = 0L;

        while (true) {
            List<ComplainantRow> page = sourceRepository.findComplainantRows(
                    afterId, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }

            for (ComplainantRow row : page) {
                if (upsert(row, fanout, stamp)) {
                    written++;
                }
            }

            afterId = page.get(page.size() - 1).complaintId();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return written;
    }

    /**
     * Writes or overwrites ONE complaint's row, or declines to.
     *
     * <p>Declines in exactly two cases, both of which are correct rather than defensive:
     * <ul>
     *   <li>NO USABLE CONTACT after normalisation and suppression. 51 of 4,403 complaints carry neither
     *       email nor phone, and such a row could match nothing — it would only sit under the sentinel
     *       waiting to be pooled with every other contactless stranger if a reader ever sought it. Left
     *       out entirely, so the sentinel rows do not exist to be found. Note that it is the POST-guard
     *       state that decides: a complaint whose only contact was a suppressed placeholder is also
     *       dropped, which is the right answer — it has no identity this feature can speak about.
     *   <li>NO FILING INSTANT. Neither {@code filed_at} nor {@code created_at}, which does not occur on
     *       today's register ({@code created_at} is complete on all 4,403 rows) but is possible, and a
     *       fabricated date would put the complaint inside or outside a 30-day window arbitrarily.
     *       {@code FILED_AT} is also {@code NOT NULL} in the schema, so this guard is what keeps the
     *       insert from failing rather than merely what keeps it honest.
     * </ul>
     *
     * @return true if a row was written
     */
    private boolean upsert(ComplainantRow row, ContactFanoutIndex fanout, LocalDateTime stamp) {
        if (row.complaintNumber() == null || row.complaintNumber().isBlank()) {
            return false;
        }

        String emailKey = fanout.emailKeyFor(normaliseEmail(row.complainantEmail()));
        String phoneKey = fanout.phoneKeyFor(normalisePhone(row.complainantPhone()));
        if (AssistanceComplainantHistory.KEY_ABSENT.equals(emailKey)
                && AssistanceComplainantHistory.KEY_ABSENT.equals(phoneKey)) {
            return false;
        }

        LocalDateTime filedAt = row.effectiveFiledAt();
        if (filedAt == null) {
            return false;
        }

        AssistanceComplainantHistory entity = projectionRepository
                .findByComplaintNumber(row.complaintNumber().trim())
                .orElseGet(() -> AssistanceComplainantHistory.builder()
                        .complaintNumber(row.complaintNumber().trim())
                        .build());

        entity.setEmailKey(emailKey);
        entity.setPhoneKey(phoneKey);
        entity.setEntityKey(normaliseEntity(row.entityCode()));
        entity.setDepartment(normaliseUpper(row.department()));
        entity.setFiledAt(filedAt);
        // NOT folded, unlike every other text column here: STATUS is displayed and never compared, and
        // the register genuinely mixes cases ('pending' beside 'NOT_OPENED'). Folding it would show an
        // officer a status spelled differently from the one on the complaint grid beside it.
        entity.setStatus(blankToSentinel(row.status()));
        entity.setNonMaintainable(nonMaintainableFlag(row));
        entity.setRefreshedAt(stamp);
        projectionRepository.save(entity);
        return true;
    }

    /**
     * The email identity key: lower-cased and trimmed, or null when there is none.
     *
     * <p>NORMALISED ON WRITE per {@code §6.2}. The cross-engine reason is specific: MySQL's
     * {@code utf8mb4_unicode_ci} collation folds case for free while Oracle's default collation does not,
     * so an address stored verbatim and compared verbatim would match in dev and MISS in production.
     * Doing it here means the read never needs {@code LOWER(column)}, which would defeat
     * {@code IDX_ACH_EMAIL} on both engines.
     *
     * <p>{@code Locale.ROOT} and not the default locale: under a Turkish locale
     * {@code "I".toLowerCase()} is {@code "ı"}, so a JVM started with a different locale would key the
     * same address differently and split one complainant's history in two. The same choice
     * {@code AssistanceEntityAliasNormaliser} makes, for the same reason.
     *
     * <p>Returns null for blank as well as for null, and the two are treated alike on purpose: 21 rows
     * store {@code ''} rather than NULL, and keying on the empty string would group every one of those
     * complainants together as one person — the precise defect
     * {@code ComplaintRepository.countOtherComplaintsByComplainantEmail} documents its own blank guard
     * against.
     */
    static String normaliseEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        // Cannot exceed EMAIL_KEY's width: the source column is varchar(200) and so is the target, and
        // lower-casing never lengthens a string. Guarded anyway, because a truncated key would silently
        // merge two long addresses sharing a 200-character prefix into one identity.
        return lower.length() <= 200 ? lower : null;
    }

    /**
     * The phone identity key: the last {@link #PHONE_KEY_DIGITS} digits, or null when there is none.
     *
     * <p>Every non-digit is stripped first, so {@code '+91 98765-43210'}, {@code '09876543210'} and
     * {@code '9876543210'} are one identity. The TAIL is taken because the variable part of the input is
     * the prefix — a country code or a trunk zero — and the last ten digits are the subscriber number.
     *
     * <p>A number with FEWER than ten digits is REJECTED rather than padded or used short. MEASURED: 0 of
     * 4,354 non-blank phones hold fewer than ten digits, so this rejects nothing today. It exists because
     * a 6-digit fragment would match a great many people, and matching strangers together is this
     * feature's only serious failure mode — the one that would put someone else's complaints on an
     * officer's screen and someone else's filings into a {@code 16(2)(b)} count.
     */
    static String normalisePhone(String phone) {
        if (phone == null) {
            return null;
        }
        StringBuilder digits = new StringBuilder(phone.length());
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        if (digits.length() < PHONE_KEY_DIGITS) {
            return null;
        }
        return digits.substring(digits.length() - PHONE_KEY_DIGITS);
    }

    /**
     * The entity key, through the EXISTING alias normaliser, or the sentinel.
     *
     * <p>{@code AssistanceEntityAliasNormaliser} is REUSED and deliberately not reimplemented — it is the
     * same normaliser {@code AssistanceEntityPatternRefreshService} and {@code AssistanceRailService} put
     * on both sides of the entity-pattern rollup. {@code entity_code} is documented dirty: the register
     * spells one bank {@code 'PNB'} and {@code 'Punjab National Bank'}, and {@code UPPER()} merges
     * neither with the other because they share no character position. The normaliser is an explicit
     * ALIAS TABLE (33 distinct spellings down to 24) and is deliberately NOT fuzzy — an edit-distance
     * match on a bank name merges two different banks on a bad day, and "you have already complained
     * about this entity" said of the WRONG entity is worse than silence.
     *
     * <p>MEASURED relevance to exactly this read: the duplicate-bearing complaints name {@code 'ICICI'},
     * {@code 'INDIAN'}, {@code 'UNION'}, {@code 'KOTAK'} and {@code 'BOB'} as bare codes alongside
     * {@code 'HDFC BANK'} and {@code 'STATE BANK OF INDIA'} in full, so the alias table is doing real
     * work here and not merely being inherited.
     *
     * <p>The normaliser returns null for blank, which becomes the sentinel: a complaint naming no entity
     * still counts toward a lifetime total but can never raise a SAME-ENTITY duplicate signal, and the
     * reader enforces that by refusing to match the sentinel against itself.
     */
    private String normaliseEntity(String entityCode) {
        String normalised = AssistanceEntityAliasNormaliser.normalise(entityCode);
        return normalised == null ? AssistanceComplainantHistory.KEY_ABSENT : normalised;
    }

    /** Trims and upper-cases a compared dimension, substituting the sentinel when absent. */
    private String normaliseUpper(String value) {
        if (value == null || value.isBlank()) {
            return AssistanceComplainantHistory.KEY_ABSENT;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /** Trims a DISPLAYED value without folding its case, substituting the sentinel when absent. */
    private String blankToSentinel(String value) {
        if (value == null || value.isBlank()) {
            return AssistanceComplainantHistory.KEY_ABSENT;
        }
        String trimmed = value.trim();
        // STATUS is varchar(30) in both the source and the target, so this cannot trigger. Guarded
        // because a value longer than the column fails the INSERT at refresh time rather than here, and
        // a job that dies on one malformed row would leave the whole projection stale.
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30);
    }

    /**
     * Whether this complaint closed as NON-MAINTAINABLE — the {@code 16(2)(b)} evidence.
     *
     * <p>THREE markers OR'd together, because no single column answers the question:
     * <ul>
     *   <li>{@code maintainability_determination = 'NON_MAINTAINABLE'} — the determination proper, and the
     *       only one of the three that is decided deliberately rather than inferred.
     *   <li>{@code closure_cause = 'NON_MAINTAINABLE'} — set at closure by the CEPC and RBIO workflow
     *       services. Better populated than the determination.
     *   <li>a {@code closure_clause} citing {@code 16(2)} — the statutory ground itself. Included because
     *       a complaint closed under {@code 16(2)(a)} or {@code 16(2)(b)} IS a non-maintainable closure
     *       whatever the other two columns say, and this is the marker most likely to be present on an
     *       older row.
     * </ul>
     * Compared case-insensitively against trimmed values, because the vocabularies in this database mix
     * cases and these three columns are read rather than keyed.
     *
     * <p>MEASURED: 47 rows in the whole register satisfy ANY of the three, and only 6 belong to a
     * complainant with more than one filing. So this returns {@link AssistanceComplainantHistory#FLAG_NO}
     * on essentially every row. That is reported in the migration header rather than hidden, and it means
     * the honest reading of "0 of 8 closed as non-maintainable" is "the determination is not being
     * recorded", NOT "this complainant's filings were all sound".
     *
     * <p>The {@code 16(2)} test is a PREFIX check on a trimmed, upper-cased value and not a
     * leading-wildcard {@code LIKE} — it runs in Java over a value already in hand, so no indexed column
     * is wrapped and {@code §6.2}'s prohibition is not engaged.
     */
    private String nonMaintainableFlag(ComplainantRow row) {
        if (matchesNonMaintainable(row.maintainabilityDetermination())
                || matchesNonMaintainable(row.closureCause())
                || citesClause162(row.closureClause())) {
            return AssistanceComplainantHistory.FLAG_YES;
        }
        return AssistanceComplainantHistory.FLAG_NO;
    }

    private boolean matchesNonMaintainable(String value) {
        return value != null && "NON_MAINTAINABLE".equalsIgnoreCase(value.trim());
    }

    private boolean citesClause162(String clause) {
        return clause != null && clause.trim().startsWith("16(2)");
    }

    /**
     * The fan-out guard's verdict, as the two sets of keys it suppresses.
     *
     * <p>A record so the pass-2 write can consult the pass-1 measurement without either knowing how the
     * other is implemented, and so a unit test can construct a specific suppression without running a
     * scan. {@link #emailKeyFor} and {@link #phoneKeyFor} are the ONLY way a key reaches a row, so there
     * is no path that writes a real contact value while bypassing the guard.
     */
    record ContactFanoutIndex(Set<String> suppressedEmails, Set<String> suppressedPhones) {

        /** The email key to store: the sentinel when absent OR suppressed. */
        String emailKeyFor(String normalisedEmail) {
            if (normalisedEmail == null || suppressedEmails.contains(normalisedEmail)) {
                return AssistanceComplainantHistory.KEY_ABSENT;
            }
            return normalisedEmail;
        }

        /** The phone key to store: the sentinel when absent OR suppressed. */
        String phoneKeyFor(String normalisedPhone) {
            if (normalisedPhone == null || suppressedPhones.contains(normalisedPhone)) {
                return AssistanceComplainantHistory.KEY_ABSENT;
            }
            return normalisedPhone;
        }

        /** An index that suppresses nothing. For tests that are not about the guard. */
        static ContactFanoutIndex empty() {
            return new ContactFanoutIndex(new HashSet<>(), new HashSet<>());
        }
    }
}
