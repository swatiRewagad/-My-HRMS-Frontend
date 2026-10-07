package com.hrms.cms.service;

import com.hrms.cms.dto.ClauseRecommendationResponse;
import com.hrms.cms.dto.ClauseRecommendationResponse.Recommendation;
import com.hrms.cms.entity.AssistanceClausePrior;
import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.repository.ClauseRecommendationRepository;
import com.hrms.cms.repository.ClauseRecommendationRepository.ClauseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ranks the closure-clause picker's options by historical co-occurrence (Brief 21 §5.3.2).
 *
 * <h2>What this is, and the thing it is constantly mistaken for</h2>
 * §5.3.2 asks for a "closure-clause recommendation, ranked by historical co-occurrence", honouring
 * {@code restricted_to_roles}, reordering or annotating the existing {@code <select>}.
 *
 * <p>It is NOT the shipped {@code entity-clause-precedent} rail signal. That one reports a single COUNT
 * beside the complaint — "23 earlier complaints against this entity cited 16(2)(a)" — and reorders
 * NOTHING. An earlier audit of this brief concluded §5.3.2 had shipped on the strength of the name
 * alone. A ranking needs a DISTRIBUTION, which is why {@link AssistanceClausePrior} stores one row per
 * (cohort, clause) rather than a single winner.
 *
 * <h2>The request path is ONE keyed lookup plus ONE row, per §6.2</h2>
 * {@code ClauseRecommendationRepository.findClauseContext} reads five scalars by complaint number on a
 * unique index; {@code findRankedCandidates} is one index range over {@code UK_ACP_COHORT_CLAUSE}
 * returning at most four cohorts' worth of rows. There is no {@code GROUP BY} here and no aggregate —
 * §6.2 forbids computing a rollup on request, and the measured cost of the equivalent {@code GROUP BY}
 * is a sort over 877 clause-bearing rows on every closure screen a staff user opens.
 *
 * <h2>The three ranking rules, stated where a test can pin them</h2>
 * Up to four cohorts come back from one range (specific, two one-sentinel fallbacks, fully agnostic).
 * They are NOT merged, and the preference between them is applied here rather than in an {@code ORDER
 * BY} — partly because adding one reintroduced {@code Using filesort} on an otherwise clean index range,
 * and mostly because a clause inside a query string is the wrong place to decide what an officer is
 * shown beside a control that commits a real closure.
 * <ol>
 *   <li><b>A narrower cohort beats a broader one.</b> {@link AssistanceClausePrior#specificity} is 2, 1
 *       or 0. A cohort narrowed to this complaint's category and entity is a better answer when the data
 *       supports one, and preferring it is what makes the rollup improve on its own as
 *       {@code category_id} and {@code entity_code} get populated — without a migration.
 *   <li><b>Then the larger denominator.</b> Between two cohorts at equal specificity, the one resting on
 *       more observations is the better-evidenced claim. NOT the higher share: 100% of 5 is weaker
 *       evidence than 95% of 815, and preferring the percentage would systematically surface the
 *       thinnest cohorts.
 *   <li><b>Then the clause code, alphabetically.</b> Arbitrary but STABLE, and that is the entire point.
 *       Without a total order, two requests over identical data could order two equally-evidenced
 *       clauses differently, and the picker would appear to rearrange itself between two focus events.
 * </ol>
 * The chosen cohort is used WHOLE. Levels are never merged, which is the mechanism by which rule 2 is
 * really enforced: a 5-closure specific cohort and an 815-closure agnostic one are never in the same
 * ordering, so the thin one cannot outrank the thick one on share. It wins only when it is also more
 * specific — a different and defensible claim, because it is evidence about cases more like this one.
 *
 * <h2>{@code restricted_to_roles} is honoured by CONSTRUCTION, not by filtering the rollup</h2>
 * The rollup is deliberately role-blind: it counts what the register DID, and the register contains
 * closures made by roles the current caller is not. So the permitted set comes from
 * {@link ClosureClauseAccessService}, which is the existing authority — it applies the complaint's own
 * scheme version, the effective-date bounds, and splits {@code restricted_to_roles} into WHOLE TOKENS
 * (a {@code LIKE} or {@code contains} would match {@code ADMIN} inside {@code RBIO_ADMIN} and quietly
 * widen every administrator-only clause). This service then builds its answer by walking the PERMITTED
 * list and consulting the rollup, never the reverse. Adding a clause is not an operation it has.
 *
 * <p>MEASURED CONSEQUENCE, stated because it makes the feature look broken: {@code 15(1)(a)} dominates
 * every cohort in the current register (833 of 877 clause-bearing rows) and is restricted to
 * {@code OMBUDSMAN,RBIO_ADMIN,ADMIN}. For every other role it is filtered out here — so the RBIO
 * cohorts, which contain only {@code 15(1)(a)}, come back EMPTY and an {@code RBIO_OFFICER} gets no
 * ranking on any of the 1238 RBIO complaints (28.1% of the register). That is the role rule working, not
 * a defect.
 *
 * <h2>Suggest, never auto-select (§5.1)</h2>
 * Enforced by the response SHAPE rather than by a convention the client could drift from:
 * {@link ClauseRecommendationResponse} has no {@code selected}, {@code default} or {@code preferred}
 * field, and this service is never told what the officer has already chosen. A frontend cannot
 * auto-select from this payload without inventing a field. The precedent is the shipped next-action
 * signal, which ships {@code link: null} so that the one-click application of a suggestion does not
 * exist to be clicked.
 *
 * <h2>Degrades to an unranked picker, never to an error</h2>
 * Every failure — the switch off, an unapplied migration (which is the live state, so this is the path
 * every call takes today), an empty rollup, a timeout, an unknown complaint, a cohort below the sample
 * floor, a complaint with no department — yields the permitted clauses in the master's own alphabetical
 * order with {@code ranked = false}. There is no path here that returns a 5xx or lets an exception reach
 * an officer, because the picker it annotates must stay usable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClauseRecommendationService {

    /**
     * Minimum closures in a cohort before its ordering is trusted on the READ side.
     *
     * <p>Five, the same number {@code ClauseRecommendationRefreshService.MIN_COHORT_SAMPLE} refuses to
     * write below. Enforced on BOTH sides deliberately, and that is not redundancy: the rollup can hold
     * a row written by an older build under a lower floor, and the read is the side an officer is
     * actually looking at. A floor that exists only in the writer is a floor that stops applying the
     * moment the writer changes.
     */
    static final long MIN_COHORT_SAMPLE = 5;

    /**
     * Minimum share before a clause's counts are PRINTED beside its option.
     *
     * <p>Five percent, deliberately higher than the refresh's 2% write floor, because the two floors
     * answer different questions. 2% is "this cohort really has used this clause, so the ORDER should
     * reflect that". 5% is "often enough that a figure printed next to it will be read as precedent". A
     * clause at 3% is a TRUE statement and a misleading display: "used in 24 of 815" sitting beside the
     * top option's "777 of 815" invites an officer to read the smaller figure as a recommendation rather
     * than as a tail. So it is ordered third and carries no number.
     *
     * <p>HONESTLY: on the current register this gate fires on nothing, because the distribution is
     * degenerate — {@code 15(1)(a)} holds 95.3% of the largest cohort and 100% of the other four, and
     * the only other ranked clause, {@code 16(2)(a)}, holds 4.7%... which this floor DOES suppress the
     * figure for. So it fires exactly once, on one clause, in one cohort. Said plainly rather than
     * reported as a passing test.
     */
    static final double MIN_ANNOTATION_SHARE = 0.05d;

    /**
     * How many of the caller's roles are consulted for the permitted set.
     *
     * <p>A bound, not a policy. Each role costs one {@code findAllInForce} read of ~15 master rows, and
     * a token carrying forty realm roles would otherwise turn one screen load into forty queries. Eight
     * is comfortably above the most any staff user in the configured realm holds; the overflow is logged
     * rather than silently dropped, because a caller whose roles were truncated could be shown a
     * narrower clause set than they may actually cite and the only honest failure is a visible one.
     */
    static final int MAX_ROLES_CONSULTED = 8;

    private final ClauseRecommendationRepository priorRepository;
    private final ClosureClauseAccessService clauseAccessService;

    /**
     * This feature's §6.2 kill switch, independent of the rail's.
     *
     * <p>The same key the refresh job reads, so one setting governs both the hourly scan and the read —
     * an operator must not be able to leave a rollup refreshing that nothing displays, or worse, leave a
     * STALE rollup being displayed by a read whose writer was turned off. A separate key from
     * {@code cms.assistance.enabled} because the risks differ: the rail is an ambient panel, whereas
     * this reorders a control that commits a real closure, and an operator who needs to stop influencing
     * closure decisions must be able to do that without also blinding the rail.
     *
     * <p>Defaults to {@code false}. The safe state here is "offer no opinion about which clause to
     * cite", and an environment that never set the key must not acquire one by default.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.clause-recommendation.enabled:false}")
    private boolean clauseRecommendationEnabled;

    /**
     * The clauses this caller may cite for this complaint, ranked where history supports it.
     *
     * <p>Never throws and never returns null.
     *
     * @param complaintNumber the complaint being closed. Blank or unknown is not an error — the picker
     *                        is also opened with no complaint in scope, in which case there is no cohort
     *                        key and the answer is the scheme's clause set, unranked
     * @param callerRoles     ALL the caller's roles, from {@code RequestIdentityResolver}. Never
     *                        {@code RequestIdentity.getPrimaryRole()}, which is
     *                        {@code roles.iterator().next()} over a {@code HashSet} and therefore names
     *                        an arbitrary role that can differ across JVMs — the picker would offer a
     *                        different clause set to the same officer on two pods. Used ONLY to narrow
     *                        the permitted set; nothing here widens anything from a role
     */
    public ClauseRecommendationResponse recommend(String complaintNumber,
                                                  Collection<String> callerRoles) {
        ClauseContext context = loadContext(complaintNumber);
        String schemeVersion = context == null ? null : context.schemeVersion();

        List<ClosureClauseMaster> permitted = permittedClauses(schemeVersion, callerRoles);
        if (permitted.isEmpty()) {
            // Either this caller may cite nothing — which on today's data is the measured state for an
            // RBIO_OFFICER — or CLOSURE_CLAUSE_MASTER is unseeded. Both are the access service's answer,
            // and neither is improved by a ranking.
            return ClauseRecommendationResponse.empty(complaintNumber);
        }

        if (!clauseRecommendationEnabled) {
            // Returns BEFORE any rollup query. The switch stops the work, not just the display.
            return ClauseRecommendationResponse.unranked(complaintNumber, tail(permitted));
        }
        if (context == null || context.department() == null || context.department().isBlank()) {
            // No complaint, or a complaint with no department. Department is the rollup's LEADING key
            // column, so there is no cohort to look up. Deliberately NOT falling back to a
            // department-wide or register-wide ordering: that would be a statement about the whole
            // register presented as though it were about this complaint, which is the ranking claiming
            // more than it knows. MEASURED: 20 of 1422 open complaints have no department.
            return ClauseRecommendationResponse.unranked(complaintNumber, tail(permitted));
        }

        try {
            Optional<Cohort> best = findBestCohort(context);
            if (best.isEmpty()) {
                return ClauseRecommendationResponse.unranked(complaintNumber, tail(permitted));
            }
            return rank(complaintNumber, permitted, best.get());
        } catch (Exception e) {
            // THE LIVE PATH AS SHIPPED: V116 / oracle V114 are unapplied, so ASSISTANCE_CLAUSE_PRIOR
            // does not exist and every read throws here. WARN and not ERROR — an unranked picker is
            // fully functional, so this is a missing convenience and not an incident.
            log.warn("Clause recommendation unavailable for {}; the picker is served in its original "
                            + "order. If V116 / oracle V114 has not been applied this is expected: {}",
                    complaintNumber, e.toString());
            return ClauseRecommendationResponse.unranked(complaintNumber, tail(permitted));
        }
    }

    /**
     * The complaint's five key facts, or null.
     *
     * <p>A blank complaint number is not looked up at all, and an unknown one is not an error: the
     * closure picker is reachable with no complaint in scope, and in both cases the correct answer is
     * the scheme's clause set in its own order.
     *
     * <p>Its failure is absorbed rather than propagated, so a database hiccup on this read costs the
     * RANKING and not the clause list — the permitted set comes from a different table.
     */
    private ClauseContext loadContext(String complaintNumber) {
        if (complaintNumber == null || complaintNumber.isBlank()) {
            return null;
        }
        try {
            return priorRepository.findClauseContext(complaintNumber.trim()).orElse(null);
        } catch (Exception e) {
            log.warn("Clause recommendation could not read the complaint context for {}: {}",
                    complaintNumber, e.toString());
            return null;
        }
    }

    /**
     * Every clause ANY of the caller's roles may cite, from the existing authority.
     *
     * <p>Delegated to {@link ClosureClauseAccessService#clausesForScheme} and never reimplemented. That
     * class holds the scheme-version scoping, the effective-date bounds and the whole-token
     * {@code restricted_to_roles} split, and it is the same code the picker endpoint and the closure
     * REFUSAL already use — so a clause can only be ranked if it could have been offered, and a change
     * to the restriction rules cannot leave the ranking behind.
     *
     * <h3>UNION across the caller's roles, and the honest consequence</h3>
     * A clause is offered when at least ONE held role may cite it. That matches the rail's precedent
     * ({@code AssistanceNextActionRepository.findRailCandidates} takes the whole role list) and it is
     * the right reading of "a clause this role may not apply must not be recommended to that role": an
     * officer who holds {@code RBIO_ADMIN} can in fact apply an admin-restricted clause.
     *
     * <p>What it costs, said rather than hidden: the WRITE path refuses on ONE role —
     * {@code RbioWorkflowService} passes {@code params.get("userRole")} to
     * {@code assertRoleMayUseClause}. So a multi-role officer who declares the narrower role on the
     * closure call can be offered a clause that call then refuses. The alternative is intersecting,
     * which would hide clauses the officer legitimately may cite on every multi-role account. Offering
     * and being refused is a visible, recoverable annoyance; being silently unable to see a clause you
     * are entitled to use is neither. The refusal remains the control (§4) and this list remains a
     * courtesy.
     *
     * <p>Ordering is the master's own {@code ORDER BY clause_code ASC}, preserved through a
     * {@link LinkedHashMap} keyed on clause code so the union neither duplicates a clause reachable
     * through two roles nor reshuffles the tail an officer reads top-first.
     */
    private List<ClosureClauseMaster> permittedClauses(String schemeVersion,
                                                       Collection<String> callerRoles) {
        List<String> roles = normaliseRoles(callerRoles);
        if (roles.isEmpty()) {
            // No role could be established. NOT a fallback to the unrestricted clauses: the access
            // service already refuses a restricted clause for a blank role, so asking it with null
            // would return exactly the unrestricted subset and present it as this caller's entitlement.
            // An unidentified caller gets no recommendation at all, which is the same decision
            // ClosureClauseAccessService.roleMayUse makes for the same reason.
            log.debug("Clause recommendation skipped: the caller's roles could not be established");
            return List.of();
        }

        Map<String, ClosureClauseMaster> union = new LinkedHashMap<>();
        for (String role : roles) {
            try {
                for (ClosureClauseMaster clause : clauseAccessService.clausesForScheme(schemeVersion, role)) {
                    union.putIfAbsent(clause.getClauseCode(), clause);
                }
            } catch (Exception e) {
                // One role failing must not cost the others. If they ALL fail the union is empty and the
                // caller is served an empty list, which is this service's documented degraded answer.
                log.warn("Clause recommendation could not read the permitted clause set for a role: {}",
                        e.toString());
            }
        }
        return new ArrayList<>(union.values());
    }

    /** Trims, drops blanks, de-duplicates and caps the caller's roles. */
    private List<String> normaliseRoles(Collection<String> callerRoles) {
        if (callerRoles == null) {
            return List.of();
        }
        // LinkedHashSet, so a duplicate role does not buy a second query and the order is reproducible
        // across requests — HashSet iteration order is the reason getPrimaryRole() is unusable.
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String role : callerRoles) {
            if (role != null && !role.isBlank()) {
                distinct.add(role.trim());
            }
        }
        if (distinct.size() <= MAX_ROLES_CONSULTED) {
            return new ArrayList<>(distinct);
        }
        log.warn("Clause recommendation consulted only {} of {} caller roles; the clause set may be "
                + "narrower than this caller may cite", MAX_ROLES_CONSULTED, distinct.size());
        return new ArrayList<>(distinct).subList(0, MAX_ROLES_CONSULTED);
    }

    /**
     * One index range, then rules 1 and 2 applied over the handful of rows it returns.
     *
     * <p>Both sentinel keys travel in the SAME query, so the specific cohort and its fallbacks come back
     * together. The alternative is up to four seeks on a screen load to answer one question, and four
     * seeks can return a torn mixture of two different refresh passes where one range cannot.
     *
     * <p>Rows are grouped back into cohorts here because the query cannot: it returns a flat list in
     * which a cohort is identified by two of its columns. Grouping is over at most
     * {@code MAX_CANDIDATE_ROWS} = 20 rows.
     */
    private Optional<Cohort> findBestCohort(ClauseContext context) {
        List<Long> categoryKeys = categoryKeys(context.categoryId());
        List<String> entityKeys = entityKeys(context.entityCode());

        List<AssistanceClausePrior> candidates = priorRepository.findRankedCandidates(
                context.department().trim(),
                categoryKeys,
                entityKeys,
                // The §6.2 row cap, as a Pageable because Spring Data has no annotation for
                // setMaxResults. Arithmetically unreachable (4 cohorts x 5 clauses = 20) unless the
                // refresh's per-cohort cap grows without this one, which is the case it guards.
                PageRequest.of(0, ClauseRecommendationRepository.MAX_CANDIDATE_ROWS));

        // Empty is NORMAL, not a failure: the rollup holds only cohorts that cleared the floors, and it
        // is also what an unapplied migration looks like once the table exists but has never refreshed.
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<CohortKey, List<AssistanceClausePrior>> grouped = new LinkedHashMap<>();
        for (AssistanceClausePrior row : candidates) {
            grouped.computeIfAbsent(new CohortKey(row.getCategoryKey(), row.getEntityKey()),
                    k -> new ArrayList<>()).add(row);
        }

        List<Cohort> cohorts = new ArrayList<>();
        for (List<AssistanceClausePrior> rows : grouped.values()) {
            // The denominator is carried on every row of a cohort, so any row answers for all of them.
            Long total = rows.get(0).getCohortTotal();
            if (total == null || total < MIN_COHORT_SAMPLE) {
                // Written under an older, lower floor. SKIPPED rather than trusted, and the remaining
                // cohorts are still considered — a thin specific cohort must not shadow a thick general
                // one, which is the whole reason the fallbacks were fetched in the same query.
                continue;
            }
            cohorts.add(new Cohort(rows.get(0).specificity(), total, rows));
        }
        // Rule 1 then rule 2. min() over an explicit comparator rather than a sort, because exactly one
        // cohort is used and the rule reads the same either way.
        return cohorts.stream().min(BEST_COHORT);
    }

    /**
     * Rule 1 then rule 2, as a comparator a unit test can call directly.
     *
     * <p>{@code specificity} descending, so {@code reversed()} on a natural-order int comparison and
     * {@code min()} picks the narrowest cohort. Then {@code cohortTotal} descending. There is
     * deliberately no third tier: two cohorts cannot share both a specificity and a denominator and
     * still be different cohorts worth distinguishing, and inventing a tiebreak would imply a preference
     * nobody has argued for.
     */
    static final Comparator<Cohort> BEST_COHORT = Comparator
            .comparingInt(Cohort::specificity).reversed()
            .thenComparing(Comparator.comparingLong(Cohort::total).reversed());

    /**
     * The complaint's category AND the sentinel, so the fallback costs no second round trip.
     *
     * <p>Just the sentinel when the complaint has no category, which is the common case — MEASURED: 64
     * of the 870 closed clause-bearing rows carry a {@code category_id}, all in one category. Passing
     * the sentinel twice would be harmless but makes the {@code IN} list a lie about what was asked.
     */
    private List<Long> categoryKeys(Long categoryId) {
        if (categoryId == null || categoryId == AssistanceClausePrior.CATEGORY_AGNOSTIC) {
            return List.of(AssistanceClausePrior.CATEGORY_AGNOSTIC);
        }
        return List.of(categoryId, AssistanceClausePrior.CATEGORY_AGNOSTIC);
    }

    /**
     * {@code UPPER(TRIM(entity_code))} AND the sentinel.
     *
     * <p>Normalised HERE, in Java, and not with {@code UPPER(TRIM(c.entityCode))} in the predicate —
     * §6.2's rule, and the measured reason is that wrapping an indexed column in a function is what
     * already defeats {@code idx_complaint_entity_code} at {@code ComplaintRepository:94}. The refresh
     * writes the same normalisation into {@code ENTITY_KEY}, so the two sides agree by construction.
     *
     * <p>This merges case and whitespace variants. It does NOT merge {@code 'Punjab National Bank'} with
     * {@code 'PNB'}; those remain two cohorts, which is the documented dirt surviving. Fixing it is a
     * data-migration project owned elsewhere and is not attempted here.
     */
    private List<String> entityKeys(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) {
            return List.of(AssistanceClausePrior.ENTITY_AGNOSTIC);
        }
        String normalised = entityCode.trim().toUpperCase();
        if (normalised.isEmpty() || AssistanceClausePrior.ENTITY_AGNOSTIC.equals(normalised)) {
            // A literal '*' in the source column would collide with the sentinel and silently read the
            // agnostic cohort as though it were this entity's. Refused rather than trusted; the refresh
            // declines to WRITE such a row for the same reason.
            return List.of(AssistanceClausePrior.ENTITY_AGNOSTIC);
        }
        return List.of(normalised, AssistanceClausePrior.ENTITY_AGNOSTIC);
    }

    /**
     * Builds the permutation: ranked clauses first, then the historyless tail untouched.
     *
     * <p>Walks {@code permitted} and consults the cohort, NEVER the reverse. That direction is what
     * makes {@code restricted_to_roles} structural rather than a filter that could be forgotten: the
     * result is a permutation of a list the access service approved, so a clause the rollup knows and
     * the role may not cite is unreachable from this method.
     *
     * <p>The tail is neither hidden nor reordered. A clause with no history is not a clause the officer
     * may not cite, and dropping it would turn a suggestion into a restriction — which is the access
     * service's job and not this one's. It keeps the master's own alphabetical order so the officer's
     * existing habits about where a rarely-used clause sits still hold.
     */
    private ClauseRecommendationResponse rank(String complaintNumber,
                                              List<ClosureClauseMaster> permitted,
                                              Cohort cohort) {
        Map<String, AssistanceClausePrior> byCode = new LinkedHashMap<>();
        for (AssistanceClausePrior row : cohort.rows()) {
            // Keyed on the RAW COMPLAINTS.closure_clause value the refresh stored. A historical junk
            // value therefore matches no permitted clause and is simply dropped — the master stays the
            // only source of what may appear in the picker.
            byCode.put(row.getClauseCode(), row);
        }

        List<Recommendation> ranked = new ArrayList<>();
        List<Recommendation> tail = new ArrayList<>();
        for (ClosureClauseMaster clause : permitted) {
            AssistanceClausePrior row = byCode.get(clause.getClauseCode());
            if (row == null) {
                tail.add(Recommendation.unranked(
                        clause.getClauseCode(), clause.getLabel(), clause.getLabelKey()));
                continue;
            }
            ranked.add(new Recommendation(
                    clause.getClauseCode(),
                    clause.getLabel(),
                    clause.getLabelKey(),
                    row.getOccurrences(),
                    cohort.total(),
                    row.share() >= MIN_ANNOTATION_SHARE));
        }

        if (ranked.isEmpty()) {
            // The cohort cleared every floor and still intersects nothing this role may cite. THE
            // MEASURED COMMON CASE, not an edge: the RBIO cohorts contain only 15(1)(a), which is
            // restricted to OMBUDSMAN,RBIO_ADMIN,ADMIN, so for every other role this branch is taken on
            // all 1238 RBIO complaints. Reported as unranked, which is exactly what it is.
            return ClauseRecommendationResponse.unranked(complaintNumber, tail);
        }

        // Rule 3's within-cohort half: the larger NUMERATOR first, then the clause code. Within one
        // cohort every clause shares one denominator, so ordering by count and ordering by share are the
        // same ordering — and the count is the figure that can be reported, so it is the one sorted on.
        ranked.sort(Comparator
                .comparingLong((Recommendation r) -> r.occurrences() == null ? 0L : r.occurrences())
                .reversed()
                .thenComparing(Recommendation::clauseCode));

        List<Recommendation> all = new ArrayList<>(ranked.size() + tail.size());
        all.addAll(ranked);
        all.addAll(tail);
        return new ClauseRecommendationResponse(complaintNumber, true, cohort.total(), all);
    }

    /** The permitted clauses as unranked entries, in the master's own order. The degraded answer. */
    private List<Recommendation> tail(List<ClosureClauseMaster> permitted) {
        List<Recommendation> out = new ArrayList<>(permitted.size());
        for (ClosureClauseMaster clause : permitted) {
            out.add(Recommendation.unranked(
                    clause.getClauseCode(), clause.getLabel(), clause.getLabelKey()));
        }
        return out;
    }

    /** Which cohort a candidate row belongs to, as a value so it can be a map key. */
    private record CohortKey(Long categoryKey, String entityKey) {
    }

    /** One cohort's rows with the two facts {@link #BEST_COHORT} compares. Package-private for tests. */
    record Cohort(int specificity, long total, List<AssistanceClausePrior> rows) {
    }
}
