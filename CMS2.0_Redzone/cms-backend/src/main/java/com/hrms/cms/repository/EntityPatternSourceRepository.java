package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.projection.EntityPatternProjections;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The ONE read the entity-pattern refresh makes over its source data (Brief 21 §5.3.5).
 *
 * <h2>Why this is its own interface rather than another method on {@code ComplaintRepository}</h2>
 * {@code ComplaintRepository} is a chokepoint file several concurrent sessions edit, and this query is
 * read by exactly one scheduled job. Keeping it here makes the job's whole data access reviewable in one
 * place and means a change to it cannot conflict with a change to the complaint grid. It also keeps a
 * deliberate asymmetry visible: the rail's request-path reads live on {@code ComplaintRepository}
 * because they serve a screen, and this does not, because nothing on a request path may touch it. Same
 * shape and same reasoning as {@code ClauseAffinitySourceRepository}.
 *
 * <p>Extends {@link Repository} rather than {@code JpaRepository} ON PURPOSE. {@code JpaRepository}
 * would inherit {@code findAll()} and {@code count()} over the 105-column {@code COMPLAINTS} table —
 * unbounded finders that §6.2 forbids, sitting one autocomplete away from a caller who needed a row cap.
 * A marker-interface repository exposes only the capped method declared below, so the rule is enforced
 * by the TYPE rather than by review.
 *
 * <h2>The REQUEST path never calls this</h2>
 * It is an unfiltered walk of the complaint register, which is exactly what §6.2 means by "rollups are
 * computed on a schedule, never on request". The read side of this feature talks to
 * {@code AssistanceEntityPatternRepository} and to nothing else.
 */
public interface EntityPatternSourceRepository extends Repository<Complaint, Long> {

    /**
     * One page of keyable complaints, with only the columns the rollup keys on.
     *
     * <h3>KEYSET, not offset</h3>
     * The caller passes the last id it saw. An {@code OFFSET} over this table is wrong twice:
     * complaints are filed during a long refresh and shift every later page (so a complaint is counted
     * twice or missed), and deep offsets degrade because the database must walk and discard everything
     * before them. {@code id > :afterId ORDER BY id} has neither problem and needs no index that does
     * not already exist — it is a range scan on the PRIMARY KEY.
     *
     * <h3>NO DATE PREDICATE, and that is a consequence of the key</h3>
     * {@code QUARTER_KEY} is part of the rollup's unique key and is the complaint's OWN filing quarter,
     * not "the current one". So every complaint in the register belongs to some window and the job has
     * no basis on which to exclude one — a cutoff here would silently stop maintaining the windows
     * BEHIND it, and the rail would go on serving whatever those windows last held while a complaint
     * filed in them was being closed. An earlier draft of this feature used a rolling 90-day window and
     * therefore needed {@code createdAt >= :cutoff}; keying on the quarter removed both the predicate
     * and the window-length column it would have had to store.
     *
     * <p>The cost is stated rather than hidden: the scan is the whole register (4,403 rows, 4,112 of
     * them keyable) every cycle, where the windowed version read 4,371. On this data that is no
     * difference at all, and on a register with years of history the stale sweep is what bounds the
     * OUTPUT while this read stays proportional to the input. If that ever becomes the job's cost, the
     * fix is a bound on which QUARTERS are refreshed — not a bound on which complaints are read, which
     * would corrupt the quarters it skipped.
     *
     * <h3>BLANK entity and department are filtered HERE, not in Java</h3>
     * Both are part of the rollup's KEY, and a row missing either cannot be keyed at all:
     * <ul>
     *   <li>290 of 4,403 complaints carry no usable {@code entity_code}, some as NULL and some as
     *       {@code ''}. Keying a window on the empty string would pool every entity-less complaint under
     *       one entity that does not exist, and then report its count to an officer as a pattern.
     *   <li>23 carry no {@code department}. Since the department is the TENANCY FENCE, a row with no
     *       department has no fence — it would have to be either dropped or placed in a department it
     *       does not belong to, and dropping it is the only honest option. Note this is why
     *       {@code DEPARTMENT} has no sentinel row written to it even though the column could hold one.
     * </ul>
     * Filtering in SQL rather than in Java is not an optimisation; it keeps the job's definition of "a
     * countable complaint" in one place, where the reason can be read beside the predicate. MEASURED:
     * 4,112 rows satisfy both.
     *
     * <p>{@code TRIM} appears on these columns and that is survivable here precisely because this is the
     * scheduled job and not a request: the job reads every qualifying row by design, so there is no seek
     * for a function wrap to defeat. §6.2's prohibition is about REQUEST-path predicates that should
     * have been index seeks. The equivalent wrap does not exist on the read path — see
     * {@code AssistanceEntityPatternRepository}, where every comparison is against a value the caller has
     * already normalised.
     *
     * <h3>NO STATUS FILTER, deliberately</h3>
     * The rollup needs BOTH the open count and the window total, so a status predicate here would throw
     * away the denominator. "Open" is also not one value in this database — eight statuses are closed
     * and the vocabulary lives in {@code RBIO_STATUS_MASTER} where an operator can correct it — so
     * carrying that list in this query would freeze it at the moment the query was written and make the
     * rollup silently wrong the next time a terminal status was added. The status comes back as a string
     * and {@code RbioStatusVocabulary} decides, once per pass.
     *
     * <h3>The blank guards are {@code LENGTH(TRIM(x)) > 0}, deliberately</h3>
     * NOT {@code x IS NOT NULL AND TRIM(x) <> ''}. Oracle treats {@code ''} as NULL, so {@code <> ''}
     * becomes {@code <> NULL} and evaluates to UNKNOWN for every row — the rollup would return NOTHING
     * on the production database and the entity-pattern rail would read as "no data" with no error
     * logged. {@code LENGTH(TRIM(x)) > 0} excludes NULL and blank on MySQL exactly as the old predicate
     * did, and is correct on Oracle, so it subsumes the {@code IS NOT NULL} conjunct.
     *
     * @param afterId exclusive lower bound on the complaint id; pass 0 to start
     * @param page    the §6.2 row cap, supplied by the caller
     */
    @Query("SELECT new com.hrms.cms.repository.projection.EntityPatternProjections$EntityCase("
            + "c.id, c.entityCode, c.department, c.createdAt, c.groundOfComplaintId, "
            + "c.rbioOfficeCode, c.status) "
            + "FROM Complaint c "
            + "WHERE c.id > :afterId "
            + "AND LENGTH(TRIM(c.entityCode)) > 0 "
            + "AND LENGTH(TRIM(c.department)) > 0 "
            + "ORDER BY c.id")
    List<EntityPatternProjections.EntityCase> findCasesForRollup(
            @Param("afterId") long afterId,
            Pageable page);
}
