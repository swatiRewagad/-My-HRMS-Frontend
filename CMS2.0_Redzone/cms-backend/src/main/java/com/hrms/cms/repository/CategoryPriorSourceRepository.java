package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.projection.CategoryPriorProjections;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The one read the category-prior refresh makes over its SOURCE data.
 *
 * <h2>Why this is its own interface and not another method on {@code ComplaintRepository}</h2>
 * Same reasoning as {@code ClauseAffinitySourceRepository}: {@code ComplaintRepository} is a chokepoint
 * file several concurrent sessions edit, and this query is read by exactly one scheduled job. Keeping it
 * here makes the job's whole data access reviewable in one place and means a change to it cannot
 * conflict with a change to the complaint grid.
 *
 * <p>Extends {@link Repository} rather than {@code JpaRepository} on purpose. {@code JpaRepository}
 * would inherit {@code findAll()} and {@code count()} over the 105-column {@code COMPLAINTS} table —
 * unbounded finders that §6.2 forbids, sitting one autocomplete away from a caller who needed a row cap.
 * A marker-interface repository exposes only the capped method declared below, so the rule is enforced
 * by the type rather than by review. That matters more here than for the siblings, because this is the
 * one assistance query that returns complaint TEXT: an unbounded finder on this interface would be an
 * unbounded read of every complainant's prose.
 *
 * <h2>The REQUEST path never calls this</h2>
 * {@code CategorySuggestionService} reads {@code AssistanceCategoryPriorRepository} and, when the caller
 * passed a complaint number rather than raw text, fetches that ONE complaint through
 * {@code ComplaintRepository.findByComplaintNumber}. It never touches this interface, because tokenising
 * the labelled register on request is exactly what §6.2 means by "rollups are computed on a schedule,
 * never on request".
 */
public interface CategoryPriorSourceRepository extends Repository<Complaint, Long> {

    /**
     * One page of labelled complaints, keyset-paged, with only the label and the text.
     *
     * <p>KEYSET, not offset. The caller passes the last id it saw. An {@code OFFSET} over this table is
     * wrong twice: complaints are categorised during a long refresh and shift every later page, so a
     * complaint is counted twice or missed; and deep offsets degrade because the database must walk and
     * discard everything before them. {@code id > :afterId ORDER BY id} has neither problem and needs no
     * index that does not already exist — it is a range scan on the primary key.
     *
     * <p>For THIS rollup the offset hazard is not hypothetical. The feature's own success populates the
     * column it filters on: as officers accept suggestions, {@code category_id} fills in and the
     * qualifying slice grows, so the source set is expected to change under the job rather than merely
     * being able to.
     *
     * <h3>The two filters, and why one of them is a function on a column</h3>
     * {@code category_id IS NOT NULL} is the whole selectivity — it restricts the scan to 273 rows of
     * 4,403. A complaint with no category is no evidence about categories, so this is the fact being
     * counted and not a proxy for it.
     *
     * <p>The text filter uses {@code TRIM} on two columns, and that is survivable precisely because this
     * is the scheduled job and not a request: the job reads every qualifying row by design, so there is
     * no seek for a function wrap to defeat. §6.2's prohibition is about request-path predicates that
     * should have been index seeks. The equivalent wrap on the READ path does not exist — see
     * {@code AssistanceCategoryPriorRepository}, where the only text comparison is an {@code IN} against
     * values {@code AssistanceTextTokenizer} has already normalised.
     *
     * <p>MEASURED: the text filter excludes 0 of the 273 labelled rows today (all of them carry at least
     * a subject), so it is a guard against a future row rather than a live filter. It is kept because
     * without it such a row would be fetched, tokenised to the empty set, and counted into
     * {@code LABELLED_TOTAL} — inflating the corpus denominator with a complaint that contributed no
     * evidence, which would quietly depress every base rate and therefore every lift.
     *
     * <p>The text filter is {@code LENGTH(TRIM(x)) > 0}, NOT {@code x IS NOT NULL AND TRIM(x) <> ''}:
     * Oracle treats {@code ''} as NULL, so {@code <> ''} is {@code <> NULL} and is UNKNOWN for every
     * row — the whole OR group would match nothing and this rollup would be EMPTY in production while
     * green on MySQL. {@code LENGTH} behaves identically on both engines and subsumes the null checks.
     * {@code c.categoryId IS NOT NULL} is a DIFFERENT column and stays: it is not a blank guard, it is
     * the definition of "labelled", and {@code category_id} is numeric so it has no empty-string form.
     *
     * <p>NO STATUS FILTER, deliberately. A complaint's category is evidence about its words whether it
     * is open, closed, withdrawn or rejected, and "closed" is not one value in this database
     * ({@code closed}, {@code resolved}, {@code adjudicated}, {@code rejected}, {@code withdrawn} all
     * appear, in mixed case). Carrying that vocabulary here would make the rollup silently miss rows
     * whenever a new status was added — and unlike the clause rollup, which counts a closure event,
     * this one counts a classification, which has no lifecycle.
     *
     * @param afterId exclusive lower bound on the complaint id; pass 0 to start
     * @param page    the §6.2 row cap, supplied by the caller
     */
    @Query("SELECT new com.hrms.cms.repository.projection.CategoryPriorProjections$LabelledText("
            + "c.id, c.categoryId, c.subject, c.description) "
            + "FROM Complaint c "
            + "WHERE c.id > :afterId "
            + "AND c.categoryId IS NOT NULL "
            + "AND (LENGTH(TRIM(c.subject)) > 0 "
            + "  OR LENGTH(TRIM(c.description)) > 0) "
            + "ORDER BY c.id")
    List<CategoryPriorProjections.LabelledText> findLabelledForRollup(
            @Param("afterId") long afterId, Pageable page);
}
