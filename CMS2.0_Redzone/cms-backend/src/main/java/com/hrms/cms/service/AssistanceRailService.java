package com.hrms.cms.service;

import com.hrms.cms.dto.AssistanceRailResponse;
import com.hrms.cms.dto.AssistanceRailResponse.Signal;
import com.hrms.cms.entity.AssistanceNextAction;
import com.hrms.cms.entity.AssistanceRailMemory;
import com.hrms.cms.repository.AssistanceNextActionRepository;
import com.hrms.cms.repository.AssistanceRailMemoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.projection.AssistanceRailProjections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The assistance rail: two tiers of cheap, already-known facts (Brief 21).
 *
 * <h2>The scope boundary is the design, not a limitation of it</h2>
 * TIER 0 is per-user continuity — what THIS officer was doing on THIS complaint last time. TIER 1 is
 * precomputed priors — aggregate counts the database can already answer with an index seek.
 *
 * <p>There is NO tier 2 and there is no scaffolding for one. No LLM call, no embedding, no kNN, no
 * per-request reasoning over case text, and no "provider" interface inviting one to be slotted in
 * later. The brief is explicit, and the reason is structural rather than stylistic: every signal here
 * must be answerable in the few milliseconds a screen load can spare, and anything needing inference
 * to decide whether it is worth saying cannot meet that. Signals considered and DROPPED for this
 * reason are recorded in {@link #computeTier1} so the next reader does not re-litigate them.
 *
 * <h2>A rail failure must never block an officer</h2>
 * {@link #rail} catches per-signal. One prior whose query times out costs that one signal; the rest of
 * the rail still renders. A total failure returns {@link AssistanceRailResponse#empty} — an empty
 * signal list, HTTP 200 — because the rail is an aid beside the real screen and an officer who cannot
 * read a count must still be able to work the complaint. That is also why no exception is allowed to
 * escape: {@code GlobalExceptionHandler} maps a bare {@code RuntimeException} to HTTP 400, so a
 * leaked failure here would not show as a degraded rail but as a client error on a valid request.
 *
 * <h2>Tier 0 is keyed on the resolved principal, never on a parameter</h2>
 * Every method takes {@code ownerUserId} already resolved by
 * {@link com.hrms.cms.security.RequestIdentityResolver} at the controller. No method accepts a user
 * from a request body, and {@link AssistanceRailMemoryRepository} has no finder that omits the owner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistanceRailService {

    // ─── Stable machine keys. The frontend maps these to icons and i18n keys; renaming one is a
    // ─── breaking API change even though no Java code reads the value.
    public static final String KIND_LAST_VIEWED = "last-viewed";
    public static final String KIND_UNSAVED_DRAFT = "unsaved-draft";
    public static final String KIND_LAST_SECTION = "last-section";
    public static final String KIND_COMPLAINANT_HISTORY = "complainant-history";
    public static final String KIND_ENTITY_CLAUSE_PRECEDENT = "entity-clause-precedent";
    public static final String KIND_CATEGORY_CLOSURE_TIME = "category-closure-time";
    public static final String KIND_NEXT_ACTION = "next-action";

    /**
     * The one kind that is NOT about the complaint on screen (§5.3.4).
     *
     * <p>"3 of your 14 cases breach within 48h" is a statement about the officer's QUEUE. It is carried
     * on the per-complaint rail response because the bulb lives on complaint-detail screens and there is
     * nowhere else to put it — see {@link #deadlineTriage} for the contract decision and its cost.
     */
    public static final String KIND_DEADLINE_TRIAGE = "deadline-triage";

    // ─── Keys inside Signal.params. Also a wire contract: the client looks up
    // ─── 'assistance.signal.<kind>' with the kind VERBATIM, hyphens and all
    // ─── (assistance.signal.last-section, NOT ...last_section) and substitutes {{name}} placeholders
    // ─── by these exact names. A rename here leaves the localised sentence with a hole, which the
    // ─── client DISCARDS in favour of the English title — so the locale silently reverts to English
    // ─── rather than showing a visibly broken string, which is the harder failure to notice. The
    // ─── matching i18n values are seeded by AssistanceRailTranslationSeeder; the client's hardcoded
    // ─── KIND_LABEL_KEYS map is the authority on key spelling.
    public static final String PARAM_SECTION = "section";
    public static final String PARAM_AGE = "age";
    public static final String PARAM_COUNT = "count";
    public static final String PARAM_CLAUSE = "clause";
    public static final String PARAM_DAYS = "days";
    public static final String PARAM_SAMPLE = "sample";

    /**
     * The next-action prior's extra params, beside the {@link #PARAM_COUNT} it shares.
     *
     * <p>{@code action} is the RAW timeline value ({@code SUBMIT_FOR_REVIEW}) and is deliberately NOT
     * translated in any of the 13 locales — it is the string an officer matches against the action
     * bar's buttons, so a localised verb would name a control that does not exist.
     *
     * <p>{@code count} / {@code total} / {@code percent} are numerator, denominator and share. All
     * three are sent even though the third is derivable, because the seeded sentence names all three
     * and a client computing the percentage itself would round it differently from the English title.
     * {@code count} keeps its established meaning here — the number the signal is ABOUT — which for
     * this prior is how many times the action was taken, not how many events were examined.
     */
    public static final String PARAM_ACTION = "action";
    public static final String PARAM_PERCENT = "percent";
    public static final String PARAM_TOTAL = "total";

    /**
     * The deadline-triage prior's params, beside the {@link #PARAM_COUNT} and {@link #PARAM_TOTAL} it
     * shares with the others.
     *
     * <p>{@code hours} is the HORIZON in hours ({@code 48}), not a time remaining. It is interpolated
     * rather than written into each of the thirteen sentences because {@link #DEADLINE_HORIZON} is
     * configurable: a hardcoded "48" in the Tamil string would go on saying 48 after an operator set the
     * window to 24, and the officer reading it has no way to tell.
     *
     * <p>{@code overdue} is a SUBSET of {@code count}, never a second bucket. It reaches the sentence as
     * its own placeholder so a locale can phrase "3 of your 14 cases breach within 48h, 2 of them
     * already past the deadline" naturally; a client that ADDED {@code count} and {@code overdue} would
     * double-count every overdue case. The English sentence and all twelve translations are phrased so
     * the subset relationship is explicit.
     */
    public static final String PARAM_HOURS = "hours";
    public static final String PARAM_OVERDUE = "overdue";

    /**
     * Rows the median-duration prior will look at, at most.
     *
     * <p>The query is a covering index range, so this bounds heap rather than IO. The largest category
     * holds 19 rows today; the cap exists for the volume this table will reach in production, where an
     * uncapped read would grow without anyone noticing until it was slow.
     */
    static final int CLOSURE_SAMPLE_CAP = 500;

    /**
     * Below this many closed complaints the median is NOT reported.
     *
     * <p>Measured reason: six of the ten populated categories have 3 or fewer closed complaints, and
     * one has none. A "median closure time" computed from two cases is a number with the authority of
     * a statistic and the content of an anecdote, and an officer has no way to tell from the rail
     * which they are looking at. So the signal is withheld, rather than shown with a caveat nobody
     * reads.
     *
     * <p>Public because {@code DemoDataSeeder}'s closed cohort is sized against it and
     * {@code DemoDataSeederClosureCohortTest} asserts the fixture clears it. Referencing the constant
     * rather than copying the number means raising this guard fails that test, instead of silently
     * leaving the fixture too small — which is how the prior came to have no sample to report at all.
     */
    public static final int MIN_CLOSURE_SAMPLE = 5;

    /** Suppresses the "last viewed" signal when the officer was here moments ago. */
    static final Duration LAST_VIEWED_SUPPRESS_WINDOW = Duration.ofMinutes(5);

    /**
     * The deadline-triage window. 48 hours, the figure Brief 21 §5.3.4 names.
     *
     * <p>Configurable because the right window is an operational judgement this code cannot make: a
     * CEPC dealing official working a 30-day SLA and an Ombudsman on a statutory clock want different
     * horizons, and "48h" in the brief is an illustration rather than a ruling. The value is
     * interpolated into the sentence via {@link #PARAM_HOURS} so changing it cannot leave thirteen
     * locales asserting a window that is no longer in force.
     *
     * <p>Clamped at read time: see {@link #horizonHours()}. A zero or negative window would make the
     * signal report only the already-overdue while still SAYING "within N hours", and a window of
     * years would make it report the whole queue as at risk — both are sentences that are false rather
     * than merely unhelpful.
     */
    static final long DEFAULT_HORIZON_HOURS = 48L;

    /** Narrowest and widest windows {@link #horizonHours()} will honour. */
    static final long MIN_HORIZON_HOURS = 1L;
    static final long MAX_HORIZON_HOURS = 24L * 30L;

    /**
     * Below this many open cases the triage signal is NOT reported.
     *
     * <p>§5.1's confidence floor applied to a count rather than to a probability. "1 of 1 of your cases
     * breaches within 48h" is a sentence about the complaint the officer is already looking at, dressed
     * up as a queue statistic — it tells them nothing the screen does not, and the rail's one fatal
     * failure mode is glowing for something not worth reading. Three is the smallest queue for which
     * "N of M" is a triage instruction rather than a restatement.
     */
    static final long MIN_QUEUE_FOR_TRIAGE = 3L;

    private final AssistanceRailMemoryRepository memoryRepository;
    private final ComplaintRepository complaintRepository;
    private final AssistanceNextActionRepository nextActionRepository;

    /**
     * The closed-status vocabulary, read from {@code RBIO_STATUS_MASTER} rather than held as a literal.
     *
     * <p>Not a convenience: two hardcoded copies of this list had already drifted apart in this
     * codebase — {@code WorkflowController} held six values and {@code NotificationScheduledTasks} four,
     * omitting {@code adjudicated} and {@code conciliated}, so a complaint closed by an award counted as
     * OPEN to the scheduler. A third copy here would put closed complaints into officers' at-risk
     * counts, which is the same bug pointed at the rail. The live table answers eight values where the
     * legacy fallback answers six (measured: 1186 open complaints against 1200).
     */
    private final RbioStatusVocabulary statusVocabulary;

    /** See {@link #DEFAULT_HORIZON_HOURS}. Field-injected per the {@code @RequiredArgsConstructor} trap. */
    @Value("${cms.assistance.deadline-triage.horizon-hours:48}")
    private long configuredHorizonHours = DEFAULT_HORIZON_HOURS;

    /**
     * Everything worth saying about one complaint to one officer.
     *
     * <p>Read-only and non-transactional by intent: a rail read must not be able to hold a write lock
     * or enlist in anything the officer's real work depends on.
     *
     * <h3>ONE signal here is about the officer's QUEUE and not about this complaint</h3>
     * {@link #KIND_DEADLINE_TRIAGE} (§5.3.4) answers "3 of your 14 cases breach within 48h". That is a
     * statement about {@code ownerUserId}'s whole workload, and the complaint on screen contributes
     * nothing to it beyond being the reason the rail was asked at all. It is carried on this
     * per-complaint response anyway, and {@link #deadlineTriage} records why — in short, the signature
     * ALREADY resolves the officer for Tier 0, so no new endpoint and no new input was needed.
     *
     * @param complaintNumber the complaint being viewed
     * @param ownerUserId     the RESOLVED caller. Tier 0 is scoped to this and nothing else — and since
     *                        §5.3.4 the queue-wide deadline prior is too, which is why Tier 1 now
     *                        receives it. A rail asked about a complaint by an officer it cannot name
     *                        reports neither.
     * @param callerRoles     the caller's RESOLVED roles, used only to key the next-action prior. May
     *                        be null or empty, in which case that one signal is omitted — see
     *                        {@link #nextAction}. Never used as an authorization decision: this method
     *                        is reached only through the staff-gated {@code /api/v1/assistance/**}
     *                        matcher, and a rail that made its own access judgements would be a second,
     *                        weaker control beside the real one.
     */
    public AssistanceRailResponse rail(String complaintNumber, String ownerUserId,
                                       Collection<String> callerRoles) {
        String complaint = AssistanceRailMemory.normaliseComplaint(complaintNumber);
        if (complaint == null) {
            // Nothing to be said about no complaint. An empty rail rather than a 400, because the
            // contract has one success shape and a screen with a missing id is the client's problem
            // to notice, not a reason to show an officer an error.
            return AssistanceRailResponse.empty(complaintNumber);
        }

        List<Signal> signals = new ArrayList<>();

        // Each tier is guarded separately. Tier 1 touching a slow index must not cost the officer
        // their own continuity state, which is the half they are most likely to miss.
        try {
            signals.addAll(computeTier0(complaint, ownerUserId));
        } catch (Exception e) {
            log.warn("Assistance rail tier 0 failed for {} (owner resolved: {}): {}",
                    complaint, ownerUserId != null, e.toString());
        }

        try {
            signals.addAll(computeTier1(complaint, ownerUserId, callerRoles));
        } catch (Exception e) {
            log.warn("Assistance rail tier 1 failed for {}: {}", complaint, e.toString());
        }

        return AssistanceRailResponse.of(complaint, signals);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════
    // TIER 0 — memory / continuity
    // ═══════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * What this officer was doing here last time.
     *
     * <p>Returns empty when the owner could not be resolved. It does NOT fall back to a complaint-only
     * lookup: that would hand whoever asked the most recent officer's unsaved text, which is the exact
     * disclosure Tier 0's per-user key exists to prevent.
     */
    private List<Signal> computeTier0(String complaintNumber, String ownerUserId) {
        String owner = AssistanceRailMemory.normaliseOwner(ownerUserId);
        if (owner == null) {
            return List.of();
        }

        Optional<AssistanceRailMemory> found =
                memoryRepository.findByOwnerUserIdAndComplaintNumber(owner, complaintNumber);
        if (found.isEmpty()) {
            return List.of();
        }
        AssistanceRailMemory memory = found.get();
        List<Signal> signals = new ArrayList<>(3);

        // The unsaved draft leads. Of the three Tier 0 facts it is the only one that represents work
        // the officer could still LOSE, so it is the one worth interrupting them for.
        if (memory.getDraftText() != null && !memory.getDraftText().isBlank()) {
            signals.add(Signal.memory(KIND_UNSAVED_DRAFT,
                    "You left unsaved text here",
                    preview(memory.getDraftText()),
                    null));
        }

        if (memory.getLastSection() != null && !memory.getLastSection().isBlank()) {
            String section = memory.getLastSection();
            signals.add(Signal.memory(KIND_LAST_SECTION,
                    "You were last in " + section,
                    null,
                    null,
                    // The section name is carried out separately because it is the only place it
                    // appears: a client rendering this in Tamil would otherwise have to recover it by
                    // stripping the English prefix off the title.
                    Map.of(PARAM_SECTION, section)));
        }

        // Suppressed for a recent visit. "You were last here 20 seconds ago" is noise, and a rail that
        // always glows is a rail nobody reads — the one failure mode that makes the whole feature
        // worthless rather than merely imperfect.
        LocalDateTime lastViewed = memory.getLastViewedAt();
        if (lastViewed != null && Duration.between(lastViewed, LocalDateTime.now())
                .compareTo(LAST_VIEWED_SUPPRESS_WINDOW) > 0) {
            String age = humaniseAge(lastViewed);
            signals.add(Signal.memory(KIND_LAST_VIEWED,
                    "You last opened this " + age,
                    lastViewed.toString(),
                    null,
                    // The humanised phrase, not the timestamp — detail already carries the ISO
                    // instant for a client that would rather format the age itself.
                    Map.of(PARAM_AGE, age)));
        }

        return signals;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════
    // TIER 1 — precomputed priors
    // ═══════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Aggregate facts the database already knows.
     *
     * <h3>SHIPPED</h3>
     * <ul>
     *   <li>{@code complainant-history} — earlier complaints by the same complainant. Covering seek on
     *       {@code idx_complaint_email}.
     *   <li>{@code entity-clause-precedent} — complaints against this entity closed under this clause.
     *       Covering seek on the composite {@code idx_arm_clause_entity} added by V112.
     *   <li>{@code category-closure-time} — median days to closure for this category. Covering range
     *       on {@code idx_arm_category_closed} added by V112, capped and sample-gated.
     *   <li>{@code next-action} — what this role usually did next from this status, with its
     *       denominator. A unique-key seek on the {@code ASSISTANCE_NEXT_ACTION} rollup added by V115 /
     *       oracle V113, which {@code AssistanceNextActionRefreshService} computes on a schedule
     *       because §6.2 forbids aggregating on request.
     *   <li>{@code deadline-triage} — how many of the OFFICER'S OWN open cases are near a deadline.
     *       Covering range on {@code idx_complaints_officer_status_deadline} added by V117 / oracle
     *       V115. The only prior keyed on the caller rather than on the complaint; see
     *       {@link #deadlineTriage}.
     * </ul>
     *
     * <h3>DROPPED, and why</h3>
     * <ul>
     *   <li><b>Complainant history matched on PHONE as well as email.</b> Better recall, and the data
     *       argues for it (2760 rows carry a phone, 2748 a non-blank email). Dropped because
     *       {@code complainant_phone} has no index and an {@code OR} across two columns cannot be
     *       index-sought regardless: measured 1 row examined on email alone against 2509 with the OR.
     *       An index on phone would fix it but is a PII-column index nobody else needs, so it is a
     *       decision to take deliberately rather than smuggle in under a rail. <b>The recall gap is
     *       real and is reported.</b>
     *   <li><b>"N complaints against this entity are still open."</b> Wanted, and cheap-looking.
     *       Dropped because {@code entity_code} CARRIES NO INDEX ({@code type=ALL}, 2509 rows) and the
     *       status predicate is an {@code IN} list, so there is no seek available. The clause
     *       precedent above only works because {@code closure_clause} is indexed and leads the
     *       composite.
     *   <li><b>Anything over complaint TEXT</b> — "similar wording", "this reads like the batch you
     *       closed last week". That is the excluded Tier 2, and the honest version needs inference.
     *       A {@code LIKE '%...%'} over a {@code TEXT} column is not a cheap substitute for it; it is
     *       a full table scan that also happens not to work.
     *   <li><b>Reopen history as its own signal.</b> Only 40 rows have {@code reopen_count > 0}, and
     *       it is already on the complaint record the officer is looking at. The rail would be
     *       repeating the screen.
     *   <li><b>Deadline triage over a ROLE QUEUE as well as an officer queue.</b> "Your team has 9
     *       breaching" is arguably the more actionable sentence for a supervisor. Dropped for a
     *       disclosure reason, not a performance one: {@code assigned_role} is indexed and the query
     *       would be the same shape, but a role queue is other officers' workload and the rail has no
     *       standing to decide which roles may see whose. Recorded as an open ask.
     * </ul>
     *
     * <h3>The officer id reaches Tier 1 since §5.3.4, and nothing else uses it</h3>
     * {@code ownerUserId} is passed through to {@link #deadlineTriage} alone. The other four priors are
     * deliberately NOT given it: they are facts about the register that do not change with who is
     * asking, and handing them an identity would invite one of them to start filtering by it, which is
     * the shape an authorisation decision takes when it is made in the wrong layer.
     */
    private List<Signal> computeTier1(String complaintNumber, String ownerUserId,
                                      Collection<String> callerRoles) {
        Optional<AssistanceRailProjections.RailContext> context =
                complaintRepository.findRailContext(complaintNumber);
        if (context.isEmpty()) {
            // An unknown complaint number gets an empty rail, not a 404. The rail does not arbitrate
            // whether a complaint exists — the screen beside it already did, and disagreeing with it
            // here would surface as an error on a page that loaded fine.
            return List.of();
        }
        AssistanceRailProjections.RailContext ctx = context.get();
        List<Signal> signals = new ArrayList<>(5);

        addSignal(signals, KIND_COMPLAINANT_HISTORY, () -> complainantHistory(ctx));
        addSignal(signals, KIND_ENTITY_CLAUSE_PRECEDENT, () -> entityClausePrecedent(ctx));
        addSignal(signals, KIND_CATEGORY_CLOSURE_TIME, () -> categoryClosureTime(ctx));
        addSignal(signals, KIND_NEXT_ACTION, () -> nextAction(ctx, callerRoles));
        // LAST, and the order is the point: this one is about the officer's queue rather than the
        // complaint, so it reads as a footnote to the four priors above rather than as a fact about the
        // case on screen. The frontend renders tier 1 in the order the server sends.
        addSignal(signals, KIND_DEADLINE_TRIAGE, () -> deadlineTriage(ownerUserId));

        return signals;
    }

    /** Earlier complaints from the same complainant. */
    private Optional<Signal> complainantHistory(AssistanceRailProjections.RailContext ctx) {
        String email = ctx.complainantEmail();
        // Both halves matter. 21 rows store '' rather than NULL, and keying on that would group every
        // one of those complainants together as if they were one person.
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        long others = complaintRepository.countOtherComplaintsByComplainantEmail(
                email, ctx.complaintNumber());
        if (others <= 0) {
            return Optional.empty();
        }

        return Optional.of(Signal.prior(KIND_COMPLAINANT_HISTORY,
                others == 1
                        ? "This complainant has 1 earlier complaint"
                        : "This complainant has " + others + " earlier complaints",
                null,
                others,
                // Relative route, and one that exists: cms-portal-frontend declares 'search'.
                // A link to a route the client does not have would render a dead signal.
                "/search?complainantEmail=" + encode(email),
                // Duplicated from count(), as a string, because the i18n value interpolates
                // {{count}} and the client should not have to special-case one param source.
                Map.of(PARAM_COUNT, Long.toString(others))));
    }

    /** Complaints against the same entity closed under the same clause. */
    private Optional<Signal> entityClausePrecedent(AssistanceRailProjections.RailContext ctx) {
        String clause = ctx.closureClause();
        String entity = ctx.entityCode();
        if (clause == null || clause.isBlank() || entity == null || entity.isBlank()) {
            // Expected for most complaints, not an edge case: 3526 of 4403 rows have no closure clause
            // (they are not closed yet), so this signal is silent on nearly every open complaint. That
            // is correct — there is no precedent to report until a clause has been chosen.
            return Optional.empty();
        }

        long same = complaintRepository.countClosedUnderSameClauseForEntity(
                clause, entity, ctx.complaintNumber());
        if (same <= 0) {
            return Optional.empty();
        }

        return Optional.of(Signal.prior(KIND_ENTITY_CLAUSE_PRECEDENT,
                same + (same == 1 ? " complaint" : " complaints")
                        + " against this entity closed under " + clause,
                null,
                same,
                null,
                // The clause is the part no other field carries. It is NOT translated — '15(1)(a)' is
                // a citation and must read identically in every locale.
                Map.of(PARAM_COUNT, Long.toString(same), PARAM_CLAUSE, clause)));
    }

    /**
     * Median days from filing to closure for this complaint's category.
     *
     * <p>Median, a non-negative filter and a sample floor, all three for measured reasons documented
     * on {@code findClosureWindowsForCategory} and {@link #MIN_CLOSURE_SAMPLE}.
     */
    private Optional<Signal> categoryClosureTime(AssistanceRailProjections.RailContext ctx) {
        Long categoryId = ctx.categoryId();
        if (categoryId == null) {
            // 4130 of 4403 rows have no category_id. This signal is therefore silent for almost every
            // complaint in the current data, which is a DATA gap and not a logic one — worth saying
            // plainly because a reader testing the rail will otherwise think the prior is broken.
            return Optional.empty();
        }

        List<AssistanceRailProjections.ClosureWindow> windows =
                complaintRepository.findClosureWindowsForCategory(
                        categoryId, PageRequest.of(0, CLOSURE_SAMPLE_CAP));

        List<Long> days = windows.stream()
                .map(w -> Duration.between(w.filedAt(), w.closedAt()).toDays())
                // Negative durations are real in this data (closed_at preceding created_at in seeds).
                // They are dropped rather than clamped to zero: clamping would quietly report a
                // same-day closure that never happened and drag the median toward it.
                .filter(d -> d >= 0)
                .sorted()
                .toList();

        if (days.size() < MIN_CLOSURE_SAMPLE) {
            return Optional.empty();
        }

        long median = median(days);
        return Optional.of(Signal.prior(KIND_CATEGORY_CLOSURE_TIME,
                "This category closes in " + median + (median == 1 ? " day" : " days") + " typically",
                "Median of " + days.size() + " closed complaints in this category",
                median,
                null,
                // 'sample' is the SURVIVING sample size, after negative durations were dropped — the
                // same number the detail sentence reports, not the row count the query returned.
                Map.of(PARAM_DAYS, Long.toString(median),
                        PARAM_SAMPLE, Integer.toString(days.size()))));
    }

    /**
     * What an officer in this role usually did next, from the status this complaint is in.
     *
     * <h3>Read, not computed</h3>
     * ONE statement — an index range on {@code ASSISTANCE_NEXT_ACTION} bounded by the caller's roles
     * and the two category keys. The aggregation happened hours ago in
     * {@code AssistanceNextActionRefreshService}, because §6.2's rule is that rollups are computed on a
     * schedule and never on request.
     *
     * <h3>The specific row wins; the sentinel is the fallback</h3>
     * Both category keys go into the same query and {@link #BEST_COHORT} prefers the category-specific
     * row over the agnostic one (keyed {@link AssistanceNextAction#CATEGORY_AGNOSTIC}). So the fallback
     * costs no extra round trip, and as categories get populated in the register the specific rows
     * start existing and this begins preferring them with no code or schema change.
     *
     * <h3>SUGGEST, do not auto-select — and here that is correctness, not taste</h3>
     * The rollup is mined from what HAPPENED, not from what the workflow permits. The RBIO machine's
     * from-status rules are advertisement only, so this can legitimately name an action the workflow
     * would now refuse. The signal therefore carries the action as a machine key for the client to
     * HIGHLIGHT, and nothing in this path commits anything; {@code workflow-action-bar} performs real
     * transitions and must keep deciding for itself what is allowed.
     *
     * <h3>Returns nothing when the role cannot be established</h3>
     * Not a fallback to a role-agnostic count, deliberately. "What people usually do from this status"
     * averaged across a dealing official and an Ombudsman is a number that describes neither, and the
     * rail has no room to caveat it.
     *
     * <h3>MEASURED: this signal is silent on most complaints, and that is the data not the logic</h3>
     * On the dev register 33 cohorts clear both floors, but per CURRENT complaint status only
     * {@code assigned} (556 complaints) and {@code in_progress} (58) have one for the front-line roles —
     * 614 of 4,403 complaints, so roughly 86% see nothing. {@code closed} (2,981), {@code pending}
     * (315), {@code withdrawn} (188) and {@code forwarded} (152) have no cohort at all for
     * {@code CEPC_DO} or {@code RBIO_DEALING_OFFICIAL}. The cause is upstream: {@code performed_by_role}
     * is populated on 7,531 of 17,433 timeline rows (43%) and is not backfillable, so over half the
     * register's history cannot be attributed to a role and never enters a cohort. Stated here because
     * a reader testing the rail will otherwise conclude the prior is broken.
     */
    private Optional<Signal> nextAction(AssistanceRailProjections.RailContext ctx,
                                        Collection<String> callerRoles) {
        String status = ctx.status();
        if (status == null || status.isBlank()) {
            return Optional.empty();
        }

        List<String> roles = callerRoles == null ? List.of() : callerRoles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .toList();
        if (roles.isEmpty()) {
            return Optional.empty();
        }

        // Both category keys in one list, so the fallback costs no second round trip. The sentinel is
        // always included: it is the row that exists for nearly every cohort today.
        List<Long> categoryKeys = ctx.categoryId() == null
                || ctx.categoryId() == AssistanceNextAction.CATEGORY_AGNOSTIC
                ? List.of(AssistanceNextAction.CATEGORY_AGNOSTIC)
                : List.of(ctx.categoryId(), AssistanceNextAction.CATEGORY_AGNOSTIC);

        List<AssistanceNextAction> candidates =
                nextActionRepository.findRailCandidates(status.trim(), roles, categoryKeys);

        // Empty is NORMAL and not a failure: the rollup only holds cohorts that cleared a sample and a
        // confidence floor, so a role which has not yet acted five times from this status has nothing
        // to report. It is also what an unapplied V115 looks like — an empty table, one fewer signal.
        return candidates.stream().min(BEST_COHORT).map(this::toSignal);
    }

    /**
     * Which of several matching cohorts the rail should report.
     *
     * <p>Ordered rather than ORDER BY'd so the rule is visible and testable. Three tiers:
     * <ol>
     *   <li><b>Category-specific beats category-agnostic.</b> The whole reason the sentinel exists is
     *       that a cohort narrowed to this complaint's category is a better answer when the data
     *       supports one; preferring it is what makes the rollup improve on its own as categories get
     *       populated.
     *   <li><b>Then the larger denominator.</b> Between two cohorts at the same specificity — which
     *       means the caller holds more than one role that has acted from this status — the one resting
     *       on more observations is the better-evidenced claim. NOT the higher percentage: a 100%
     *       winner out of 5 is weaker evidence than an 85% winner out of 200, and preferring the
     *       percentage would systematically surface the thinnest cohorts.
     *   <li><b>Then the action name.</b> Arbitrary but STABLE: without it two equally-good cohorts
     *       would be separated by result-set order, and the rail would appear to change its advice
     *       between identical requests.
     * </ol>
     */
    private static final Comparator<AssistanceNextAction> BEST_COHORT = Comparator
            // false sorts before true, so a category-SPECIFIC row sorts first and min() picks it.
            .comparing(AssistanceNextAction::isCategoryAgnostic)
            .thenComparing(Comparator.comparingLong(AssistanceNextAction::getCohortTotal).reversed())
            .thenComparing(AssistanceNextAction::getAction);

    /**
     * The cohort as the one sentence the rail shows, in the one shape all 13 locales were seeded to.
     *
     * <h3>{@code count} is the NUMERATOR, and that is forced by the client</h3>
     * The seeded value is "{@code {{action}} followed in {{count}} of {{total}} comparable cases}", so
     * {@code count} must be the numerator for the localised sentence to be true. It is also emitted
     * EXPLICITLY into {@code params} rather than left to be folded in from {@link Signal#count}:
     * {@code labelParams} in the component supplies {@code params['count']} from {@code Signal.count}
     * only when params does not already carry it, so a params map without {@code count} and a
     * {@code Signal.count} holding the denominator would have rendered "217 of 217" in every locale
     * while the English title read correctly — a defect visible only to the officers not reading
     * English.
     *
     * <p>{@code Signal.count} carries the numerator for the same reason, so the two cannot disagree.
     * The denominator is not lost: it is {@code total}, it is in the English title, and §5.1 requires
     * it to be shown — "a bare recommendation with no denominator will be distrusted, correctly".
     *
     * <h3>The English title is a REPORT, never an instruction</h3>
     * "followed in", not "do" and not "should". The rollup states a frequency over past events; a
     * sentence phrased as advice would convert that into a direction the server has no standing to
     * give, which matters more here than on the other priors because this one names an action an
     * officer can take with one click elsewhere on the screen.
     */
    private Signal toSignal(AssistanceNextAction cohort) {
        long percent = Math.round(cohort.confidence() * 100d);
        long occurrences = cohort.getOccurrences();
        long total = cohort.getCohortTotal();
        return Signal.prior(KIND_NEXT_ACTION,
                cohort.getAction() + " followed in " + occurrences + " of " + total
                        + " comparable cases (" + percent + "%)",
                // Says ADVISORY in the prose, not only in the javadoc. The reader of this sentence is
                // the officer deciding what to do next, and the rollup can name an action the workflow
                // would now refuse — so the caveat belongs where they will see it.
                "Based on " + total + " past actions from this status by this role. Advisory only.",
                occurrences,
                // No link. There is nowhere to send an officer that would not amount to the rail
                // steering a transition, and a route that pre-selected one is exactly what §5.1's
                // "do not auto-select" forbids.
                null,
                Map.of(PARAM_ACTION, cohort.getAction(),
                        PARAM_COUNT, Long.toString(occurrences),
                        PARAM_TOTAL, Long.toString(total),
                        PARAM_PERCENT, Long.toString(percent)));
    }

    /**
     * How many of THIS OFFICER'S open cases are close to a deadline (§5.3.4).
     *
     * <h2>═══ THE CONTRACT DECISION, AND WHY IT IS THE SMALLER CHANGE ═══</h2>
     * Every other prior describes the complaint on screen. This one describes the officer's WHOLE
     * QUEUE, so the brief's own analysis (findings §5e) predicted the problem: <i>"it is a statement
     * about the officer's whole queue, not about the complaint on screen, so it does not fit the rail's
     * existing per-complaint contract — {@code rail(complaintId, …)} has no queue-wide input and the
     * panel has no place to put a signal that belongs to no complaint. Whoever builds it should expect
     * to extend the contract, not just add a signal."</i>
     *
     * <p>The two options were a queue-wide signal on the EXISTING {@code /rail} endpoint, or a new
     * sibling under {@code /api/v1/assistance/}. This is the former, and the deciding fact is that
     * {@code rail(complaintNumber, ownerUserId, callerRoles)} ALREADY TAKES THE RESOLVED OFFICER — it
     * has to, because Tier 0 is per-user. So "no queue-wide input" turned out to be wrong about the
     * signature: the input was already there and only Tier 1 was not being given it. The extension is
     * one extra parameter on a private method, one more {@code kind}, and nothing new on the wire
     * beyond an additional element in a list the client already iterates.
     *
     * <p>A second endpoint would have cost: a second controller method with its own always-200 refusal
     * story, a second frontend service call and a second failure mode on four screens, a second entry
     * in the {@code RateLimitFilter} assistance bucket's accounting, and a new place for the
     * officer-resolution rule to be got wrong. It would have bought ONE thing — the ability to ask for
     * the queue signal without naming a complaint — and nothing wants that today, because the bulb
     * lives only on complaint-detail screens, which always have a complaint. If a queue DASHBOARD ever
     * wants this count, that is the moment to add the sibling endpoint, and this method is the body it
     * would call.
     *
     * <p><b>THE TRADE-OFF, STATED:</b> the signal is unreachable without a complaint number, and it
     * costs a query on every complaint-detail load even though its answer is identical across all of
     * them for a given officer. That is a real inefficiency — N screen loads issue N identical counts —
     * and it is accepted because the count is a single covering index seek measured at sub-millisecond
     * to 4.6ms, and because the alternative (caching per officer) would make a stale "0 of 14" outlive
     * an officer clearing their queue, which is worse than the query.
     *
     * <h2>═══ AGGREGATED AT REQUEST TIME, AGAINST §5.3's GOVERNING PRINCIPLE ═══</h2>
     * §5.3 says "precompute, never aggregate at request time", and this method aggregates at request
     * time. That is a deliberate, argued exception and not an oversight:
     * <ul>
     *   <li><b>The brief contradicts itself here</b>, and says so in the same sentence: §5.3.4 calls
     *       this "a count over an existing index". A count over an index IS a request-time aggregate.
     *   <li><b>A rollup cannot be keyed per complaint.</b> Every other Tier 1 prior is keyed on
     *       something the complaint carries, so a rollup row exists per key. This one is keyed on the
     *       OFFICER, and its answer changes the moment any one of their cases is reassigned, closed, or
     *       crosses its deadline — which happens continuously. A scheduled rollup would be a table of
     *       45 rows (the measured distinct-officer count) rewritten every few minutes to serve a
     *       query that takes 0.6ms, and between refreshes it would report a queue the officer has
     *       already worked. "3 of your 14 breach within 48h" that is an hour stale is not a cheaper
     *       version of the signal; it is a different and false one.
     *   <li><b>The constraint is satisfied by the thing the principle exists to protect.</b> §5.3's
     *       rule is a means to "answerable in the few milliseconds a screen load can spare". MEASURED:
     *       {@code type=range} on {@code idx_complaints_officer_status_deadline}, {@code rows=131},
     *       {@code filtered=100.00}, {@code Using index} — covering, so the 105-column row is never
     *       read — at 0.57ms (warm) to 4.6ms (cold) across the three largest real queues. §9 says the
     *       constraint wins over the requirement; here the constraint is MET and it is the mechanism
     *       that is being substituted, with the measurement as the justification.
     * </ul>
     *
     * <h2>═══ THE BRIEF'S PRESCRIBED FIELD IS EMPTY, WHICH IS THE MOST IMPORTANT FINDING ═══</h2>
     * §5.3.4 names {@code re_response_deadline} and {@code re_response_overdue}. MEASURED against
     * {@code cms_db}: {@code re_response_deadline} is populated on <b>13 of 4403 rows</b>, exactly ONE
     * of which is open, and {@code re_response_overdue} is {@code true} on <b>zero</b>. A faithful
     * implementation of the brief would have shipped a signal that is silent on 99.97% of the register
     * and looks broken. {@code sla_deadline} is the field with data: 3870 of 4403 rows, 811 of 1186
     * open. So the count is over {@code sla_deadline} OR {@code re_response_deadline}, with the brief's
     * field kept in the predicate (and in the index) so the signal sharpens by itself when the
     * entity-response path starts writing it — the same self-improving shape as the next-action
     * sentinel. {@code current_stage_deadline} was measured and REJECTED: it equals
     * {@code sla_deadline} on 173 of its 174 open rows and is earlier on none, so it would widen the
     * index to change no answer.
     *
     * <h2>═══ THE FLOOR, AND WHY IT IS ON THE DENOMINATOR AND THE NUMERATOR BOTH ═══</h2>
     * §5.1: do not glow for nothing. TWO gates, because they catch different worthless sentences:
     * <ul>
     *   <li>{@code atRisk == 0} — "0 of your 14 cases breach" is the brief's own example of a bulb
     *       nobody looks at. The rail says nothing rather than reassuring an officer who did not ask.
     *   <li>{@code queueSize < }{@link #MIN_QUEUE_FOR_TRIAGE} — "1 of 1 of your cases breaches" is a
     *       fact about the complaint on screen wearing a queue statistic's clothes. On the live
     *       register this gate is not theoretical: 20 of the 45 officers with an assignment hold fewer
     *       than three open cases.
     * </ul>
     * On today's data the whole signal fires for THREE officers ({@code cepc_do1} 10 of 126,
     * {@code rbio.officer} 4 of 26, {@code rbio_officer_001} 1 of 142) and every one of those at-risk
     * cases is already overdue, because the seeded SLA deadlines cluster 30+ days out. Said plainly
     * because a reader testing this will otherwise think it does not work.
     *
     * <h2>═══ NO LINK, AND THAT IS A MEASURED REFUSAL NOT AN OMISSION ═══</h2>
     * "3 of your 14 breach" wants to be clickable, and §5.3.4's value would roughly double if it were.
     * It is {@code null} because there is NO ROUTE to link to: {@code app.routes.ts} declares
     * {@code staff/rbio-tasks} and {@code cepc/dashboard}, but neither reads a deadline filter from the
     * query string, and {@code /search} reads no query parameters at all (recorded in the rail
     * template's own comment). A {@code link} to a route that silently ignores its filter would send an
     * officer to their unfiltered queue and leave them to find the three cases themselves — worse than
     * no link, because it looks like the rail did something. Per the DTO, an absolute URL is also not an
     * option: it would be an open-redirect surface.
     *
     * @param ownerUserId the RESOLVED caller, from {@code RequestIdentityResolver} at the controller.
     *                    A blank owner yields NO SIGNAL — never a count over everybody. That refusal is
     *                    the whole authorisation story for this prior and it is tested as a negative:
     *                    the repository finder has no all-officers mode, mirroring the recorded
     *                    {@code EmailSyndicationApiController:451} defect where a client-supplied owner,
     *                    when omitted, returned every row in the system.
     */
    private Optional<Signal> deadlineTriage(String ownerUserId) {
        // normaliseOwner trims and nulls a blank, the same helper Tier 0 keys on, so "no owner" means
        // exactly one thing across both tiers.
        String owner = AssistanceRailMemory.normaliseOwner(ownerUserId);
        if (owner == null) {
            // No identity, no queue. NOT a fallback to an unscoped count: an officer the server cannot
            // name has no cases, and reporting the register's total would be a cross-officer disclosure
            // dressed as a convenience.
            return Optional.empty();
        }

        long hours = horizonHours();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusHours(hours);

        AssistanceRailProjections.DeadlineTriage triage =
                complaintRepository.countDeadlineTriageForOfficer(
                        owner,
                        // From the status master, not a literal. See the field javadoc on
                        // statusVocabulary for the drift this avoids.
                        statusVocabulary.closedStatuses(),
                        now,
                        // The DATE horizons are derived here and passed in, so the JPQL compares
                        // re_response_deadline (a `date` column) against a date without wrapping the
                        // column in a cast — which would defeat the index.
                        now.toLocalDate(),
                        horizon,
                        horizon.toLocalDate());

        if (triage == null) {
            return Optional.empty();
        }

        long queueSize = triage.queueSize();
        long atRisk = triage.atRisk();
        if (atRisk <= 0 || queueSize < MIN_QUEUE_FOR_TRIAGE) {
            return Optional.empty();
        }

        long overdue = Math.min(triage.overdue(), atRisk);

        // The English title is the brief's own sentence. A REPORT of a count, with its denominator, and
        // no instruction: the officer decides what to do about it, and the rail has no idea which of
        // the three is most urgent.
        String title = atRisk + " of your " + queueSize + " open cases "
                + (atRisk == 1 ? "breaches" : "breach") + " within " + hours + "h";

        return Optional.of(Signal.prior(KIND_DEADLINE_TRIAGE,
                title,
                // The overdue qualifier goes in `detail` AND in params, because the component has no
                // detail-key map: the localised heading has to carry it or non-English officers never
                // see it. Phrased as "of them" so the subset relationship survives translation.
                overdue > 0
                        ? overdue + " of them " + (overdue == 1 ? "is" : "are")
                                + " already past the deadline."
                        : null,
                // count = atRisk, the NUMERATOR, matching every other prior and matching the
                // {{count}} placeholder in all thirteen seeded sentences. The trap the next-action
                // prior documents applies here too: the component folds Signal.count into
                // params['count'] only when params lacks it, so a count field holding the DENOMINATOR
                // would render "14 of 14" in twelve locales while the English title stayed correct.
                atRisk,
                // No link. See the javadoc above — there is no route that would honour the filter.
                null,
                Map.of(PARAM_COUNT, Long.toString(atRisk),
                        PARAM_TOTAL, Long.toString(queueSize),
                        PARAM_HOURS, Long.toString(hours),
                        PARAM_OVERDUE, Long.toString(overdue))));
    }

    /**
     * The configured horizon, clamped into a range in which the sentence can be true.
     *
     * <p>Clamped rather than validated-and-rejected because this runs inside the rail, whose contract is
     * to degrade rather than to fail: a typo'd ConfigMap value must cost a sensible window, not the
     * signal. {@code 0} or negative would report only the already-overdue while the sentence still said
     * "within 0h"; a window of years would report the officer's whole queue as at risk and the bulb
     * would glow permanently, which §5.1 names as the one failure that makes the feature worthless.
     */
    private long horizonHours() {
        if (configuredHorizonHours < MIN_HORIZON_HOURS) {
            return MIN_HORIZON_HOURS;
        }
        return Math.min(configuredHorizonHours, MAX_HORIZON_HOURS);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════
    // TIER 0 WRITE PATH
    // ═══════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Records where the officer was and what they left behind.
     *
     * <h3>Upsert, not read-then-write</h3>
     * The unique key on (OWNER_USER_ID, COMPLAINT_NUMBER) IS the multi-pod lock. Two pods handling the
     * same officer leaving two screens at once will both find no row and both insert; one loses on the
     * constraint. That loss is caught and retried as an update rather than propagated, because a
     * failed continuity write must not surface to an officer who has already navigated away.
     *
     * @param ownerUserId the RESOLVED caller. A blank owner is refused outright — a memory row whose
     *                    owner cannot be established is a row that cannot be kept private, and
     *                    attributing it to a placeholder would pool several officers' drafts under one
     *                    key.
     * @return true if the row was written
     */
    @Transactional
    public boolean rememberVisit(String complaintNumber, String ownerUserId,
                                 String section, String draftText) {

        String owner = AssistanceRailMemory.normaliseOwner(ownerUserId);
        String complaint = AssistanceRailMemory.normaliseComplaint(complaintNumber);
        if (owner == null || complaint == null) {
            log.debug("Assistance rail memory not written: owner resolved={}, complaint resolved={}",
                    owner != null, complaint != null);
            return false;
        }

        try {
            return upsert(owner, complaint, section, draftText);
        } catch (DataIntegrityViolationException e) {
            // Lost the insert race. The winner's row exists now, so update it.
            log.debug("Assistance rail memory insert raced for {}/{}, retrying as update",
                    owner, complaint);
            try {
                return upsert(owner, complaint, section, draftText);
            } catch (Exception retry) {
                log.warn("Assistance rail memory write failed after retry for {}: {}",
                        complaint, retry.toString());
                return false;
            }
        } catch (Exception e) {
            log.warn("Assistance rail memory write failed for {}: {}", complaint, e.toString());
            return false;
        }
    }

    private boolean upsert(String owner, String complaint, String section, String draftText) {
        AssistanceRailMemory memory = memoryRepository
                .findByOwnerUserIdAndComplaintNumber(owner, complaint)
                .orElseGet(() -> AssistanceRailMemory.builder()
                        .ownerUserId(owner)
                        .complaintNumber(complaint)
                        .build());

        // Both fields are overwritten unconditionally, INCLUDING with null. The frontend calls this as
        // the officer leaves a screen, so "the draft box is now empty" is a fact to record — treating
        // null as "leave the old value alone" would keep offering to restore text the officer has
        // already saved or deliberately cleared.
        memory.setLastSection(AssistanceRailMemory.clampSection(section));
        memory.setDraftText(AssistanceRailMemory.clampDraft(draftText));
        memory.setLastViewedAt(LocalDateTime.now());

        memoryRepository.save(memory);
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Adds a signal, swallowing a per-signal failure.
     *
     * <p>Per-signal rather than per-tier granularity so that one prior whose index is missing in a
     * given environment costs exactly that prior. The alternative — one try around all three — means
     * a single slow query silently empties the rail, which looks identical to "nothing to say".
     */
    private void addSignal(List<Signal> target, String kind, java.util.function.Supplier<Optional<Signal>> producer) {
        try {
            producer.get().ifPresent(target::add);
        } catch (Exception e) {
            log.warn("Assistance rail signal '{}' failed and was omitted: {}", kind, e.toString());
        }
    }

    private static long median(List<Long> sorted) {
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
    }

    /** A recognisable fragment of the unsaved text, not the whole of it. */
    private static String preview(String draft) {
        String flattened = draft.replaceAll("\\s+", " ").strip();
        return flattened.length() <= 120 ? flattened : flattened.substring(0, 117) + "...";
    }

    /** Coarse on purpose: the officer needs "a while ago", not a duration to the second. */
    private static String humaniseAge(LocalDateTime then) {
        Duration age = Duration.between(then, LocalDateTime.now());
        long days = age.toDays();
        if (days >= 2) {
            return days + " days ago";
        }
        if (days == 1) {
            return "yesterday";
        }
        long hours = age.toHours();
        if (hours >= 2) {
            return hours + " hours ago";
        }
        if (hours == 1) {
            return "an hour ago";
        }
        return Math.max(1, age.toMinutes()) + " minutes ago";
    }

    /**
     * URL-encodes a query-parameter value.
     *
     * <p>Load-bearing, not hygiene: the value is an email address and the link is handed to the client
     * to navigate to. An unencoded {@code +} in a local-part is decoded as a space, so the officer
     * would land on a search for a different complainant.
     */
    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
