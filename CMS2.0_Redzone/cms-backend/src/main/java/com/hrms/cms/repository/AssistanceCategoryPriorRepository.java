package com.hrms.cms.repository;

import com.hrms.cms.entity.AssistanceCategoryPrior;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.QueryHint;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The token-to-category prior: one capped keyed read per request, and the refresh job's write paths.
 *
 * <h2>The read is a bounded set of index ranges, and that is why the table exists</h2>
 * {@link #findByTokens} seeks {@code UK_ACP_TOKEN_CATEGORY} once per token in the {@code IN} list and
 * returns each token's whole category distribution. The equivalent request-time computation is
 * "tokenise 273 complaints and group by word" — which is exactly what §6.2 means by "rollups are
 * computed on a schedule, never on request", and which would run on every keystroke-debounced call from
 * a filing form.
 *
 * <p>The REQUEST path therefore touches {@code COMPLAINTS} at most once, by {@code complaint_number},
 * and only when the caller passed a number instead of raw text. No finder here reads it.
 *
 * <h2>{@code IN}, not {@code LIKE}, and certainly not a leading wildcard</h2>
 * §6.2 forbids a leading-wildcard {@code LIKE} outright, and there is no temptation to use one here:
 * both sides of the comparison are produced by {@code AssistanceTextTokenizer}, so the match is exact
 * equality on a normalised value. There is no {@code LOWER(TOKEN)} either — the column is written
 * lower-cased, so wrapping it would defeat the index on both engines while changing no result.
 *
 * <h2>Why ONE query over an {@code IN} list and not one query per token</h2>
 * The inverse of the choice {@code AssistanceClauseAffinityRepository} documents. That repository walks
 * a LADDER and must stop at the first level that answers, so its several seeks are load-bearing: mixing
 * two specificity levels in one ordering would be wrong. This rollup has no ladder — every token is
 * independent evidence at the same level, and the service sums across all of them — so there is nothing
 * for separate round trips to buy, and a token-at-a-time loop would issue up to
 * {@link com.hrms.cms.service.AssistanceTextTokenizer#MAX_QUERY_TOKENS} statements to assemble one
 * answer.
 *
 * <h2>Every query here declares a cap AND a timeout</h2>
 * Per §6.2. The cap is a {@link Pageable} the caller must supply — there is no unbounded finder on this
 * interface — and the timeout is the same 5s ceiling the sibling rollups use.
 */
public interface AssistanceCategoryPriorRepository extends JpaRepository<AssistanceCategoryPrior, Long> {

    /** Shared with the sibling assistance repositories; spelled out because interfaces cannot inherit it. */
    String QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /**
     * The read budget, matching the other assistance reads' 5s.
     *
     * <p>A CEILING past which the caller would rather have no suggestion, not a latency target. A
     * tighter bound would make the suggestion flap between present and absent under incidental
     * contention, which is worse than being consistently absent: a filer who sees a hint appear on one
     * submission and not the next cannot build any expectation of it.
     */
    String READ_TIMEOUT_MS = "5000";

    /**
     * Every stored row for a bounded set of tokens.
     *
     * <p>Plan: one index range on {@code UK_ACP_TOKEN_CATEGORY} per value in the {@code IN} list, each
     * returning that token's rows contiguously because {@code CATEGORY_KEY} is the trailing key part.
     * The {@code ORDER BY} is the key's own order, so it is served by the index with no filesort.
     *
     * <p>Tokens must arrive ALREADY NORMALISED, from
     * {@link com.hrms.cms.service.AssistanceTextTokenizer#tokenize} — lower-cased, punctuation-stripped
     * and digit-free. A caller passing raw words would match nothing and the feature would be silent
     * rather than wrong, which is the hardest failure to notice, so the single normalisation point is
     * the contract and not a convenience.
     *
     * <p>The {@code IN} list must also be CAPPED by the caller, via
     * {@link com.hrms.cms.service.AssistanceTextTokenizer#capped}. A complaint description is a
     * {@code TEXT} column, so an uncapped list is an unbounded {@code IN} — and at some size every
     * optimiser abandons the index ranges for a scan, which would turn a declared-bounded read into a
     * table scan without any error to notice.
     *
     * <p>{@code ORDER BY} is deliberately NOT the ranking. It orders by the key purely so rows arrive
     * deterministically; the RANKING is four stated rules applied in {@code CategorySuggestionService},
     * where a reader can see them and a unit test can pin them, rather than being decided by a clause
     * inside a string.
     *
     * @param tokens normalised tokens, already capped. An empty collection is a caller error on both
     *               engines — Oracle rejects {@code IN ()} outright — so
     *               {@code CategorySuggestionService} returns the empty signal without calling this
     * @param page   the §6.2 row cap. Callers pass {@link AssistanceCategoryPrior#MAX_PRIOR_ROWS};
     *               there is no unbounded overload, so a cap cannot be forgotten at a call site
     */
    @Query("SELECT p FROM AssistanceCategoryPrior p WHERE p.token IN :tokens "
            + "ORDER BY p.token, p.categoryKey")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = READ_TIMEOUT_MS))
    List<AssistanceCategoryPrior> findByTokens(@Param("tokens") Collection<String> tokens,
                                               Pageable page);

    /**
     * One exact (token, category) row, for the REFRESH's insert-or-update decision.
     *
     * <p>Plan: unique-key seek on both key columns, one row or none.
     *
     * <p>No timeout hint, unlike {@link #findByTokens}, and the asymmetry is the point: a request would
     * rather give up than stall the form beside it, whereas the job would rather wait than skip a token
     * and leave the rollup reporting last cycle's counts. Borrowing the request budget here would make
     * the refresh abandon its work under exactly the contention it should tolerate.
     */
    @Query("SELECT p FROM AssistanceCategoryPrior p "
            + "WHERE p.token = :token AND p.categoryKey = :categoryKey")
    Optional<AssistanceCategoryPrior> findByTokenAndCategory(@Param("token") String token,
                                                             @Param("categoryKey") Long categoryKey);

    /**
     * Deletes rows the latest refresh did not rewrite.
     *
     * <p>Needed because the refresh UPSERTS rather than truncating-and-reloading — a truncate would
     * leave the feature silent for the duration of every refresh, which is a self-inflicted degradation
     * on a feature whose only failure mode is silence. The cost of upserting is that a token which has
     * fallen below the floors, or a word that has dropped out of the register's vocabulary entirely,
     * would otherwise keep its last computed row forever and go on being matched.
     *
     * <p>That matters more for THIS rollup than for its siblings, and for a reason specific to it: as
     * {@code category_id} gets populated, {@code LABELLED_TOTAL} and every {@code CATEGORY_TOTAL}
     * change on every pass, so a surviving stale row would carry an OLD base rate and its lift would be
     * computed against a corpus that no longer exists. The sweep is what keeps every row's base rate
     * from the same pass as its counts.
     *
     * <p>Keyed on {@code REFRESHED_AT} rather than on a list of surviving ids: the job stamps every row
     * it writes with ONE timestamp taken at the start of the pass, so "older than this run" is exactly
     * "not rewritten by this run" — no id list to carry and no second query to build it.
     *
     * <p>CALLED ONLY AFTER A COMPLETE PASS. That is a requirement on the caller, not a property of this
     * query: a pass that aborted halfway has stamped only the tokens it reached, so sweeping on its
     * stamp would delete the rest and the feature would go silent until the next successful run.
     * {@code AssistanceCategoryPriorRefreshService} therefore runs the sweep after the upsert loop
     * returns normally, and skips it on failure.
     */
    // @Transactional on the REPOSITORY method, not inherited from a caller. The refresh service
    // deliberately runs no transaction of its own — its recompute() is invoked through `this` from
    // refresh(), and Spring's proxy does not intercept a self-invocation, so a @Transactional there
    // would be INERT while reading as a boundary. Declared here, where the proxy IS the bean, so the
    // boundary exists wherever this is called from. Same resolution as
    // AssistanceClauseAffinityRepository#deleteStale.
    @Transactional
    @Modifying
    @Query("DELETE FROM AssistanceCategoryPrior p WHERE p.refreshedAt < :staleBefore")
    int deleteStale(@Param("staleBefore") LocalDateTime staleBefore);
}
