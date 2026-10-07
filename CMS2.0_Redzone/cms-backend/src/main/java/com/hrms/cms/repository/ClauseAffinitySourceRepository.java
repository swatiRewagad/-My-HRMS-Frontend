package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.projection.ClauseAffinityProjections;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The two reads the clause-affinity refresh makes over its SOURCE data (Brief 21 §5.3.2).
 *
 * <h2>Why this is its own interface and not two more methods on {@code ComplaintRepository}</h2>
 * {@code ComplaintRepository} is a chokepoint file several concurrent sessions edit, and these two
 * queries are read by exactly one scheduled job. Keeping them here makes the job's whole data access
 * reviewable in one place and means a change to it cannot conflict with a change to the complaint grid.
 * It also keeps a deliberate asymmetry visible: the rail's request-path reads live on
 * {@code ComplaintRepository} because they serve a screen, and these do not, because nothing on a
 * request path may touch them.
 *
 * <p>Extends {@link Repository} rather than {@code JpaRepository} on purpose. {@code JpaRepository}
 * would inherit {@code findAll()} and {@code count()} over the 105-column {@code COMPLAINTS} table —
 * unbounded finders that §6.2 forbids, sitting one autocomplete away from a caller who needed a row
 * cap. A marker-interface repository exposes only the two capped methods declared below, so the rule is
 * enforced by the type rather than by review.
 *
 * <h2>The REQUEST path never calls either of these</h2>
 * Both are {@code GROUP BY}-free scans of the closure history, which is exactly what §6.2 means by
 * "rollups are computed on a schedule, never on request". The read side of this feature talks to
 * {@code AssistanceClauseAffinityRepository} and to nothing else.
 */
public interface ClauseAffinitySourceRepository extends Repository<Complaint, Long> {

    /**
     * One page of past closures, keyset-paged, with only the columns the rollup keys on.
     *
     * <p>KEYSET, not offset. The caller passes the last id it saw. An {@code OFFSET} over this table is
     * wrong twice: complaints close during a long refresh and shift every later page (so a closure is
     * counted twice or missed), and deep offsets degrade because the database must walk and discard
     * everything before them. {@code id > :afterId ORDER BY id} has neither problem and needs no index
     * that does not already exist — it is a range scan on the primary key.
     *
     * <h3>The clause filter is the whole selectivity, and it is NOT folded with a function</h3>
     * {@code LENGTH(TRIM(closure_clause)) > 0} restricts the scan to the rows that
     * carry a citation. {@code TRIM} appears on the column here and that is survivable
     * precisely because this is the scheduled job and not a request: the job reads every qualifying row
     * by design, so there is no seek for a function wrap to defeat. §6.2's prohibition is about
     * request-path predicates that should have been index seeks. The equivalent wrap on the READ path
     * does not exist — see {@code AssistanceClauseAffinityRepository}, where every text comparison is
     * against a value the caller has already upper-cased.
     *
     * <p>NO STATUS FILTER, deliberately. "Closed" is not one value in this database —
     * {@code closed}, {@code resolved}, {@code adjudicated}, {@code rejected} and {@code withdrawn} all
     * appear, in mixed case, and 22 clause-bearing rows are not in {@code closed} at all. Carrying that
     * vocabulary here would make the rollup silently miss closures whenever a new terminal status was
     * added, and the vocabulary is exactly the kind of thing that gets added. The presence of a closure
     * CLAUSE is the better predicate for "this complaint was closed under a clause", because it is the
     * fact being counted rather than a proxy for it.
     *
     * @param afterId exclusive lower bound on the complaint id; pass 0 to start
     * @param page    the §6.2 row cap, supplied by the caller
     */
    @Query("SELECT new com.hrms.cms.repository.projection.ClauseAffinityProjections$ClosureDimensions("
            + "c.id, c.schemeVersion, c.department, c.categoryId, c.groundOfComplaintId, "
            + "c.entityCode, c.maintainabilityDetermination, c.closureClause) "
            + "FROM Complaint c "
            + "WHERE c.id > :afterId "
            + "AND LENGTH(TRIM(c.closureClause)) > 0 "
            + "ORDER BY c.id")
    List<ClauseAffinityProjections.ClosureDimensions> findClosuresForRollup(
            @Param("afterId") long afterId, Pageable page);

    /**
     * The registered entities' normalised names and types, for resolving a complaint's dirty
     * {@code entity_code} to an entity TYPE.
     *
     * <p>Read ONCE per refresh pass into a map, not per closure. {@code REGULATED_ENTITIES} holds 145
     * rows; a lookup per closure would be an N+1 against a table that fits in a few kilobytes, and the
     * lookup key needs {@code RegulatedEntity.normalize}'s transformation, which has no JPQL
     * equivalent.
     *
     * <p>The blank guards matter: a null or empty {@code name_normalized} would map to the blank
     * normalisation of any unmatched complaint code and pool unrelated entities under one type. A null
     * {@code entity_type} is excluded for the same reason — it would be indistinguishable from the
     * wildcard sentinel.
     *
     * <p>Spelled {@code LENGTH(TRIM(x)) > 0}, not {@code x IS NOT NULL AND TRIM(x) <> ''}: Oracle treats
     * {@code ''} as NULL, so {@code <> ''} is {@code <> NULL} and matches NOTHING, which would return an
     * empty type map and strip the entity dimension out of every clause recommendation in production.
     * The {@code LENGTH} form is identical on MySQL and correct on Oracle, and subsumes the null check.
     *
     * @param page the §6.2 row cap, supplied by the caller
     */
    @Query("SELECT new com.hrms.cms.repository.projection.ClauseAffinityProjections$EntityTypeRow("
            + "r.nameNormalized, r.entityType) "
            + "FROM RegulatedEntity r "
            + "WHERE LENGTH(TRIM(r.nameNormalized)) > 0 "
            + "AND LENGTH(TRIM(r.entityType)) > 0")
    List<ClauseAffinityProjections.EntityTypeRow> findEntityTypes(Pageable page);
}
