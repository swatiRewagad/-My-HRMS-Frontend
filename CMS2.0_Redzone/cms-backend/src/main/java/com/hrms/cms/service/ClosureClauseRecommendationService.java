package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceClauseAffinity;
import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.dto.ClosureClauseRecommendationResponse.ClauseRecommendation;
import com.hrms.cms.dto.ClosureClauseRecommendationResponse.ClauseRecommendations;
import com.hrms.cms.repository.AssistanceClauseAffinityRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ranks the closure-clause picker's options by historical co-occurrence (Brief 21 §5.3.2).
 *
 * <h2>What this is, and what it is NOT</h2>
 * §5.3.2 asks for "closure-clause recommendation, ranked by historical co-occurrence with (category,
 * ground, entity type, resolution path), honouring {@code restricted_to_roles}", reordering or
 * annotating the existing {@code <select>} rather than adding new UI.
 *
 * <p>It is NOT the shipped {@code entity-clause-precedent} rail prior, and the resemblance is the trap
 * the findings document records: that prior reports a COUNT beside the complaint — "this clause has
 * been cited N times for this entity" — and reorders nothing. This one ranks the picker's own options.
 * Neither replaces the other; a reader comparing them by name would conclude §5.3.2 shipped.
 *
 * <h2>§5.3.2 SAYS "REORDER". §5.1 FORBIDS REORDERING UNDER THE CURSOR. THE CONSTRAINT WINS.</h2>
 * This is a direct collision between a requirement and a constraint, and §9 settles the procedure: "when
 * a requirement and a performance constraint collide, the constraint wins and you report the collision".
 * §5.1 is explicit — "never block, never reorder under the cursor, never autofill without consent …
 * there is prior art of a rail moving fields under the cursor being treated as a defect — do not repeat
 * it" — and a {@code <select>} whose options resequence while an officer is reading it is the precise
 * shape of that defect. The closure picker is also the worst possible place to take the risk: it commits
 * a statutory closure, and a clause selected by a slip is a clause a citizen may then be unable to
 * appeal.
 *
 * <p>So the brief's "reorder OR annotate" is resolved to the ADDITIVE half of the disjunction:
 * <ul>
 *   <li>The recommended clauses are offered as a clearly-labelled GROUP ABOVE an otherwise UNTOUCHED
 *       list. The options the officer was already looking at keep their positions and their order; what
 *       appears is a new group above them. Additive, not a resequence.
 *   <li>The FULL permitted list remains reachable below the group, in the master's own order, including
 *       every clause that also appears in the group. Nothing is hidden and nothing is filtered by the
 *       ranking — only by {@link ClosureClauseAccessService}, which is the authorisation control and was
 *       there before.
 *   <li>The group is built ONCE, before the officer can have interacted with the control, and never
 *       rebuilt while the form is open. A recommendation arriving late does not move anything.
 *   <li>The server returns an ORDER and COUNTS. It does not return a selection and it does not return a
 *       default — there is no field in the response shape a client could read as one, so "never
 *       autofill without consent" is enforced by the TYPE rather than by client discipline.
 *   <li>The officer's existing selection can never change by itself. Structurally guaranteed server-side
 *       (the server is not told what is selected and has no way to express a selection) and asserted
 *       client-side.
 * </ul>
 * A true resequence of the existing list was considered and REJECTED on §5.1, even though §5.3.2's
 * wording invites it. Pure annotation with no grouping was also considered and rejected as too weak to
 * be worth the machinery: with 15 options an annotation buried at position 11 is not findable, and the
 * brief's purpose is to make the likely clause easy to reach. The group above is the shape that delivers
 * the ordering's value without moving anything the officer had already located.
 *
 * <h2>The ranking rule, with its tiebreak, stated</h2>
 * Within one cohort, clauses are ordered by:
 * <ol>
 *   <li>{@code occurrences} DESCENDING — the larger NUMERATOR first. Not the share: every clause in a
 *       cohort shares one denominator, so within a cohort the two orderings are identical and the count
 *       is the one that can be reported. Across the ladder the preference for a larger denominator is
 *       enforced differently, by {@link #recommend} stopping at the first cohort that clears the floor
 *       rather than merging levels — 100%-of-5 never competes with 85%-of-200 because they are never
 *       in the same list.
 *   <li>{@code clauseCode} ASCENDING as the final tiebreak. Arbitrary but STABLE, and that is the
 *       whole point: {@code HashMap} iteration order is unspecified, so without a total order two
 *       requests over identical data could return different orderings and the picker would appear to
 *       rearrange itself for no reason. A tie here has already cleared the floors, so both clauses are
 *       defensible.
 * </ol>
 * Clauses with NO history in the cohort keep the master's own alphabetical order, after every ranked
 * clause. They are not hidden and they are not reordered among themselves.
 *
 * <h2>{@code restricted_to_roles} is honoured by CONSTRUCTION, not by a filter on the rollup</h2>
 * The permitted clause list comes from {@link ClosureClauseAccessService#clausesFor}, which is the
 * existing authority — it reads {@code CLOSURE_CLAUSE_MASTER}, applies the complaint's own scheme
 * version and date bounds, and splits {@code restricted_to_roles} into whole tokens (a {@code contains}
 * test would match {@code ADMIN} inside {@code RBIO_ADMIN}). This service then INTERSECTS the rollup
 * against that list. A clause present in the rollup but absent from the permitted set is dropped, so a
 * restricted clause cannot reach a role through the ranking even though the rollup is role-blind by
 * design — the rollup counts what the register did, and history contains closures made by roles the
 * current caller is not.
 *
 * <p>The intersection direction matters and is the reason this is not "sort the rollup and return it":
 * the result is a permutation of the PERMITTED list, so the only clauses that can ever appear are ones
 * the access service already approved. Adding a clause is not an operation this service has.
 *
 * <h2>Degrades to silence, never to an error</h2>
 * If the rollup table does not exist — which is the live state, because the migration ships unapplied —
 * every read throws, this service logs at WARN and returns {@link ClauseRecommendations#unranked}: the
 * permitted clause list in the master's original order, with no annotations and
 * {@code ranked = false}. The picker stays fully functional. The same path covers an empty rollup, a
 * query timeout and a cohort below the sample floor. There is no code path here that returns a 500 or
 * lets an exception reach an officer.
 *
 * <h2>No PII</h2>
 * The response carries clause codes, labels, a count, a denominator and a share. There is no field for
 * a complainant name, phone, email, address or account number, and nothing in this class reads one —
 * the complaint is read for its six key dimensions only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClosureClauseRecommendationService {

    /**
     * Minimum closures in a cohort before its ordering is used.
     *
     * <p>Five, matching {@code AssistanceClauseAffinityRefreshService.MIN_COHORT_SAMPLE}. Enforced on
     * BOTH sides deliberately: the refresh declines to write a thin cohort, and the read declines to
     * trust one it finds. That is not redundancy — the rollup can hold a row written under an older,
     * lower floor, and the read is the side an officer is looking at.
     */
    static final long MIN_COHORT_SAMPLE = 5;

    /**
     * Minimum share before a clause is ANNOTATED as a recommendation.
     *
     * <p>5%, matching the refresh's {@code MIN_CLAUSE_SHARE}. A clause below it is still ordered ahead
     * of the unranked clauses if the rollup holds a row for it, but carries no count — so an officer is
     * never shown "cited in 1 of 800 comparable cases" as though that were a recommendation. Silence
     * about a thin clause is the correct degraded state; §5.1's rule is that a figure with no
     * denominator worth stating should not be stated.
     */
    static final double MIN_ANNOTATION_SHARE = 0.05d;

    private final AssistanceClauseAffinityRepository affinityRepository;
    private final ClosureClauseAccessService clauseAccessService;
    private final RegulatedEntityRepository regulatedEntityRepository;

    /**
     * The assistance-wide kill switch. BOTH this and {@link #clauseRankingEnabled} must be on.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * This feature's OWN §6.2 kill switch, defaulting to the safe state.
     *
     * <p>§6.2 requires that "every feature has an independent config kill switch, defaulting to the
     * safe state, honoured by the frontend as 'hide the affordance'". A second flag rather than reusing
     * {@code cms.assistance.enabled} alone, because this surface is not like the rest of assistance: the
     * rail is an ambient panel an officer can ignore, whereas this REORDERS a control that commits a
     * statutory closure. An operator who finds the ordering wrong — a mis-cited clause promoted by its
     * own history, say — must be able to restore the master's order on the closure form WITHOUT also
     * blinding every officer's rail. The reverse containment matters too: turning the rail off for an
     * unrelated incident should not silently change what the closure picker looks like.
     *
     * <p>ANDed with {@link #assistanceEnabled} rather than replacing it, so the global switch still
     * stops everything — an operator reaching for the big lever gets this feature too, which is the
     * property a kill switch exists to have.
     *
     * <p>Defaults to {@code false}. An environment that never set the key gets the master's order, which
     * is exactly what shipped before this feature existed. The migration is also unapplied as shipped,
     * so the default and the data agree.
     *
     * <p>The key is {@code cms.assistance.clause-recommendation.enabled} and NOT
     * {@code ...clause-ranking...}: the route is {@code /clause-recommendation}, the i18n namespace is
     * {@code clause-recommendation.*} across thirteen locales, and the scheduler's interval keys are
     * {@code cms.assistance.clause-recommendation.*}. One spelling for one feature, so an operator
     * grepping configuration for the switch finds the route and the translations with it.
     */
    @Value("${cms.assistance.clause-recommendation.enabled:false}")
    private boolean clauseRankingEnabled;

    /**
     * The permitted clauses for this complaint and these roles, ranked where history supports it.
     *
     * <p>Never throws, and never returns null. Every failure — an unresolvable complaint, an absent
     * rollup table, a timeout, a thin cohort, either switch being off — yields the permitted list in the
     * master's own order with {@code ranked = false}.
     *
     * <h3>The WHOLE role set, never a single "primary" role</h3>
     * {@code RequestIdentity.primaryRole} is {@code roles.iterator().next()} over a {@code HashSet}
     * ({@code RequestIdentityResolver:69}), so it names an ARBITRARY one of the caller's roles and can
     * name a different one across JVMs — iteration order of a {@code HashSet} is not part of its
     * contract. Keying a clause recommendation on it would make the picker's ordering appear to change
     * for no reason between two pods, and for an Ombudsman who also holds {@code RBIO_ADMIN} it could
     * resolve to the role that may cite FEWER clauses. So the caller passes the full set and
     * {@link #permittedClauses} unions the clause sets, which is the only reading of "what may this
     * caller cite" that is stable and correct.
     *
     * @param complaint the complaint being closed, or null. Null is not an error: the picker is also
     *                  opened with no complaint in scope, in which case the clause set comes from the
     *                  configured scheme and no cohort can be looked up, so the answer is unranked.
     * @param roles     the caller's resolved role set, from {@code RequestIdentity.getRoles()} and NEVER
     *                  from a request parameter — there is a recorded defect
     *                  ({@code EmailSyndicationApiController:451}) where a client-supplied owner
     *                  returned every row in the system. Used ONLY to filter the permitted clause set
     *                  through {@link ClosureClauseAccessService}; never to widen anything. A null or
     *                  empty set yields the unrestricted clauses only, because
     *                  {@code ClosureClauseAccessService} refuses a restricted clause to a blank role.
     */
    public ClauseRecommendations recommend(Complaint complaint, Set<String> roles) {
        List<ClosureClauseMaster> permitted = permittedClauses(complaint, roles);
        if (permitted.isEmpty()) {
            // Either the roles may cite nothing or the master is unseeded. Both are the access service's
            // answer, not this service's, and neither is improved by a ranking.
            return ClauseRecommendations.unranked(List.of());
        }

        // BOTH switches. See the fields for why this feature has its own in addition to the global one.
        if (!assistanceEnabled || !clauseRankingEnabled) {
            return ClauseRecommendations.unranked(permitted);
        }
        if (complaint == null) {
            // No complaint means no cohort key. Returning the permitted list unranked rather than
            // falling back to the scheme-wide cohort: a scheme-wide ordering is a statement about the
            // whole register, and presenting it as though it were about "this complaint" when there is
            // no complaint would be the ranking claiming more than it knows.
            return ClauseRecommendations.unranked(permitted);
        }

        try {
            Optional<List<AssistanceClauseAffinity>> cohort = findBestCohort(complaint);
            if (cohort.isEmpty()) {
                return ClauseRecommendations.unranked(permitted);
            }
            return rank(permitted, cohort.get());
        } catch (Exception e) {
            // The live state as shipped: V116 is unapplied, so the table does not exist and this is the
            // path every call takes. WARN and not ERROR — an unranked picker is fully functional, so
            // this is a missing convenience and not an incident.
            log.warn("Closure-clause recommendation unavailable; the picker is served in its original "
                    + "order. If V116 / oracle V114 has not been applied this is expected: {}",
                    e.toString());
            return ClauseRecommendations.unranked(permitted);
        }
    }

    /**
     * The clause set these roles may cite, from the existing authority, unioned across them.
     *
     * <h3>Delegated, never reimplemented</h3>
     * {@link ClosureClauseAccessService} holds the scheme-version scoping, the date bounds and the
     * whole-token {@code restricted_to_roles} split, and it is the same code the picker endpoint and the
     * closure REFUSAL already use. So a clause can only be ranked if it could have been offered, and a
     * change to the restriction rules cannot leave the ranking behind. Reimplementing the split here
     * would be a second copy of an authorisation rule, which is how the two come to disagree.
     *
     * <h3>The real format of {@code restricted_to_roles}, measured before relying on it</h3>
     * {@code varchar(300)}, and on the 15 seeded rows it is either NULL (9 rows, meaning unrestricted)
     * or the exact literal {@code 'OMBUDSMAN,RBIO_ADMIN,ADMIN'} (6 rows). Comma-delimited, no spaces
     * around the commas, upper-case, and NEVER the empty string — verified, {@code SUM(... = '')} is 0.
     * The access service nonetheless treats blank as unrestricted and splits on {@code ','} with a
     * per-token trim, which is the right tolerance for a hand-editable configuration column.
     *
     * <h3>UNION, not intersection, and the reason is what a role set MEANS</h3>
     * A caller holding {@code RBIO_OFFICER} and {@code RBIO_ADMIN} may cite anything either role may
     * cite — that is what holding two roles is. Intersecting would deny an Ombudsman the award clauses
     * for also holding a lesser role, which is a privilege REDUCTION invented here rather than
     * configured anywhere. The union is still a subset of the master: no role combination can produce a
     * clause the master does not hold, because every candidate comes from
     * {@code clausesFor} and nothing is added.
     *
     * <p>Deduplicated by clause CODE rather than by entity identity — the per-role calls return
     * separate instances of the same row, so a {@code Set} of entities would not collapse them. The
     * first occurrence wins, which preserves the master's own ordering from the first role's call.
     *
     * <p>Its own failure is absorbed here rather than propagated: if the master cannot be read there is
     * nothing to rank and nothing to offer, and this service's contract is to return a list.
     */
    private List<ClosureClauseMaster> permittedClauses(Complaint complaint, Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            // A blank role is not an error and is not a reason to widen anything. The access service
            // refuses every RESTRICTED clause to a blank role, so this yields the unrestricted set —
            // the same answer an unidentified caller gets from the picker endpoint today.
            return permittedForRole(complaint, null);
        }

        Map<String, ClosureClauseMaster> byCode = new LinkedHashMap<>();
        for (String role : new LinkedHashSet<>(roles)) {
            for (ClosureClauseMaster clause : permittedForRole(complaint, role)) {
                byCode.putIfAbsent(clause.getClauseCode(), clause);
            }
        }
        return List.copyOf(byCode.values());
    }

    /** One role's clause set, with the access service's failure absorbed to "nothing". */
    private List<ClosureClauseMaster> permittedForRole(Complaint complaint, String role) {
        try {
            return clauseAccessService.clausesFor(complaint, role);
        } catch (Exception e) {
            log.warn("Closure-clause recommendation could not read the permitted clause set: {}",
                    e.toString());
            return List.of();
        }
    }

    /**
     * Walks the cohort ladder from most specific to least, returning the first that clears the floor.
     *
     * <p>Five seeks in the worst case, one or two in practice. STOPS at the first level that answers —
     * it does not merge levels, and that is the mechanism by which the brief's "larger denominator
     * wins" is enforced: a 5-closure cohort at L4 and a 821-closure cohort at L1 are never in the same
     * ordering, so the thin one cannot outrank the thick one on share. The thin one wins only when it
     * is also MORE SPECIFIC, which is a different and defensible claim — it is evidence about cases
     * more like this one.
     *
     * <p>The ladder is the exact reverse of the one {@code AssistanceClauseAffinityRefreshService}
     * writes, which is why the two cannot disagree about what "more specific" means.
     *
     * <p>Every text parameter is upper-cased here, before it reaches SQL. §6.2 forbids wrapping an
     * indexed column in a function, and the cross-engine reason is that MySQL's collation folds case
     * for free while Oracle's does not — so a verbatim comparison would match in dev and miss in
     * production.
     */
    private Optional<List<AssistanceClauseAffinity>> findBestCohort(Complaint complaint) {
        String scheme = upperOr(complaint.getSchemeVersion(),
                AssistanceClauseAffinityRefreshService.DEFAULT_SCHEME);
        String department = upperOr(complaint.getDepartment(), AssistanceClauseAffinity.TEXT_ANY);
        String path = upperOr(complaint.getMaintainabilityDetermination(),
                AssistanceClauseAffinity.TEXT_ANY);
        String entityType = resolveEntityType(complaint.getEntityCode());
        long category = complaint.getCategoryId() == null
                ? AssistanceClauseAffinity.NUMERIC_ANY : complaint.getCategoryId();
        long ground = complaint.getGroundOfComplaintId() == null
                ? AssistanceClauseAffinity.NUMERIC_ANY : complaint.getGroundOfComplaintId();

        String any = AssistanceClauseAffinity.TEXT_ANY;
        long anyNum = AssistanceClauseAffinity.NUMERIC_ANY;

        List<AssistanceClauseAffinityRefreshService.CohortKey> ladder = List.of(
                new AssistanceClauseAffinityRefreshService.CohortKey(
                        scheme, department, category, ground, entityType, path),
                new AssistanceClauseAffinityRefreshService.CohortKey(
                        scheme, department, category, ground, entityType, any),
                new AssistanceClauseAffinityRefreshService.CohortKey(
                        scheme, department, category, ground, any, any),
                new AssistanceClauseAffinityRefreshService.CohortKey(
                        scheme, department, anyNum, anyNum, any, any),
                new AssistanceClauseAffinityRefreshService.CohortKey(
                        scheme, any, anyNum, anyNum, any, any));

        for (AssistanceClauseAffinityRefreshService.CohortKey rung : ladder) {
            List<AssistanceClauseAffinity> rows = affinityRepository.findCohortClauses(
                    rung.schemeVersion(), rung.department(), rung.categoryKey(), rung.groundKey(),
                    rung.entityType(), rung.resolutionPath(),
                    PageRequest.of(0, AssistanceClauseAffinity.MAX_CLAUSES_PER_COHORT));
            if (rows.isEmpty()) {
                continue;
            }
            // The denominator is carried on every row of a cohort, so any row answers for all of them.
            if (rows.get(0).getCohortTotal() == null
                    || rows.get(0).getCohortTotal() < MIN_COHORT_SAMPLE) {
                // A cohort written under an older, lower floor. Skipped rather than trusted, and the
                // walk CONTINUES to the next rung — a thin specific cohort must not shadow a thick
                // general one.
                continue;
            }
            return Optional.of(rows);
        }
        return Optional.empty();
    }

    /**
     * Applies the two stated ranking rules and produces the permutation of the permitted list.
     *
     * <p>Ranked clauses first, in the stated order; then every permitted clause with no row in the
     * cohort, in the master's own alphabetical order. The unranked tail is NOT reordered and NOT
     * hidden: a clause with no history is not a clause the officer may not cite, and dropping it would
     * turn a suggestion into a restriction — which is the access service's job, not this one's.
     *
     * <p>Only clauses the role may cite can appear, because the result is built by walking
     * {@code permitted} and consulting the rollup, never the other way round. A clause the rollup knows
     * and the role may not cite is unreachable from here.
     */
    private ClauseRecommendations rank(List<ClosureClauseMaster> permitted,
                                       List<AssistanceClauseAffinity> cohort) {
        Map<String, AssistanceClauseAffinity> byCode = new LinkedHashMap<>();
        for (AssistanceClauseAffinity row : cohort) {
            // Keyed on the clause code as the master spells it. The rollup stores the raw
            // COMPLAINTS.closure_clause value, so a historical junk value simply never matches a
            // permitted clause and is dropped — the master remains the only source of what may appear.
            byCode.put(row.getClauseCode(), row);
        }

        long total = cohort.get(0).getCohortTotal();

        List<ClauseRecommendation> ranked = new ArrayList<>();
        List<ClauseRecommendation> tail = new ArrayList<>();
        for (ClosureClauseMaster clause : permitted) {
            AssistanceClauseAffinity row = byCode.get(clause.getClauseCode());
            if (row == null) {
                tail.add(ClauseRecommendation.unranked(clause));
                continue;
            }
            boolean annotate = row.share() >= MIN_ANNOTATION_SHARE;
            ranked.add(ClauseRecommendation.ranked(clause, row.getOccurrences(), total, annotate,
                    row.specificity()));
        }

        // Rule 1 then rule 2. Spelled as an explicit comparator rather than an ORDER BY so the rule is
        // where a reader and a unit test can both see it; see the class javadoc for why the numerator
        // and not the share.
        ranked.sort(Comparator
                .comparingLong(ClauseRecommendation::occurrences).reversed()
                .thenComparing(ClauseRecommendation::clauseCode));

        List<ClauseRecommendation> all = new ArrayList<>(ranked.size() + tail.size());
        all.addAll(ranked);
        all.addAll(tail);
        return new ClauseRecommendations(all, !ranked.isEmpty(), total);
    }

    /**
     * The complaint's entity type, resolved through the entity register, or the wildcard.
     *
     * <p>ONE lookup by normalised name, never a {@code LIKE}. {@code entity_code} is free text and
     * documented dirty — the same bank appears as {@code 'Punjab National Bank'} and {@code 'PNB'} —
     * and {@code RegulatedEntity.normalize} is the normalisation the entity itself applied on write, so
     * applying it to the complaint's code is what makes the comparison meaningful. It will not resolve
     * an ALIAS, which is a data-migration problem owned elsewhere; an unresolved code yields the
     * wildcard and the ladder simply answers from a less specific rung.
     *
     * <p>Failure is absorbed to the wildcard rather than propagated: a missing entity register must
     * cost specificity, not the whole recommendation.
     */
    private String resolveEntityType(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) {
            return AssistanceClauseAffinity.TEXT_ANY;
        }
        try {
            return regulatedEntityRepository
                    .findByNameNormalized(RegulatedEntity.normalize(entityCode))
                    .map(RegulatedEntity::getEntityType)
                    .filter(type -> type != null && !type.isBlank())
                    .map(type -> type.trim().toUpperCase())
                    .orElse(AssistanceClauseAffinity.TEXT_ANY);
        } catch (Exception e) {
            log.debug("Closure-clause recommendation could not resolve entity type for {}: {}",
                    entityCode, e.toString());
            return AssistanceClauseAffinity.TEXT_ANY;
        }
    }

    private String upperOr(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }
}
