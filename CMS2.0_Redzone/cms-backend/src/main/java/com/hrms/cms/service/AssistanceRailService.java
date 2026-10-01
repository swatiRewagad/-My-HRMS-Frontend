package com.hrms.cms.service;

import com.hrms.cms.dto.AssistanceRailResponse;
import com.hrms.cms.dto.AssistanceRailResponse.Signal;
import com.hrms.cms.entity.AssistanceRailMemory;
import com.hrms.cms.repository.AssistanceRailMemoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.projection.AssistanceRailProjections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    private final AssistanceRailMemoryRepository memoryRepository;
    private final ComplaintRepository complaintRepository;

    /**
     * Everything worth saying about one complaint to one officer.
     *
     * <p>Read-only and non-transactional by intent: a rail read must not be able to hold a write lock
     * or enlist in anything the officer's real work depends on.
     *
     * @param complaintNumber the complaint being viewed
     * @param ownerUserId     the RESOLVED caller; Tier 0 is scoped to this and nothing else
     */
    public AssistanceRailResponse rail(String complaintNumber, String ownerUserId) {
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
            signals.addAll(computeTier1(complaint));
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
            signals.add(Signal.memory(KIND_LAST_SECTION,
                    "You were last in " + memory.getLastSection(),
                    null,
                    null));
        }

        // Suppressed for a recent visit. "You were last here 20 seconds ago" is noise, and a rail that
        // always glows is a rail nobody reads — the one failure mode that makes the whole feature
        // worthless rather than merely imperfect.
        LocalDateTime lastViewed = memory.getLastViewedAt();
        if (lastViewed != null && Duration.between(lastViewed, LocalDateTime.now())
                .compareTo(LAST_VIEWED_SUPPRESS_WINDOW) > 0) {
            signals.add(Signal.memory(KIND_LAST_VIEWED,
                    "You last opened this " + humaniseAge(lastViewed),
                    lastViewed.toString(),
                    null));
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
     * </ul>
     */
    private List<Signal> computeTier1(String complaintNumber) {
        Optional<AssistanceRailProjections.RailContext> context =
                complaintRepository.findRailContext(complaintNumber);
        if (context.isEmpty()) {
            // An unknown complaint number gets an empty rail, not a 404. The rail does not arbitrate
            // whether a complaint exists — the screen beside it already did, and disagreeing with it
            // here would surface as an error on a page that loaded fine.
            return List.of();
        }
        AssistanceRailProjections.RailContext ctx = context.get();
        List<Signal> signals = new ArrayList<>(3);

        addSignal(signals, "complainant-history", () -> complainantHistory(ctx));
        addSignal(signals, "entity-clause-precedent", () -> entityClausePrecedent(ctx));
        addSignal(signals, "category-closure-time", () -> categoryClosureTime(ctx));

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
                "/search?complainantEmail=" + encode(email)));
    }

    /** Complaints against the same entity closed under the same clause. */
    private Optional<Signal> entityClausePrecedent(AssistanceRailProjections.RailContext ctx) {
        String clause = ctx.closureClause();
        String entity = ctx.entityCode();
        if (clause == null || clause.isBlank() || entity == null || entity.isBlank()) {
            // Expected for most complaints, not an edge case: 2276 of 2769 rows have no closure clause
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
                null));
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
            // 2690 of 2769 rows have no category_id. This signal is therefore silent for almost every
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
                null));
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
