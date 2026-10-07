package com.hrms.cms.repository.projection;

/**
 * Constructor-expression carriers for the token-to-category prior.
 *
 * <p>Same reasoning as {@link ClauseAffinityProjections}: {@code COMPLAINTS} has ~105 columns including
 * six {@code TEXT} bodies, so hydrating {@code Complaint} entities would make the job's memory scale
 * with the widest part of the table. JPQL {@code SELECT new} rather than native SQL because the deployed
 * profiles run {@code OracleDialect} and only dev-local runs MySQL.
 *
 * <h2>This is the ONE assistance projection that deliberately carries free text, and PII is handled</h2>
 * The sibling projections can claim "no PII column exists here" structurally. This one cannot — the
 * whole feature is text-to-label, so {@code subject} and {@code description} have to arrive. The PII
 * position is therefore enforced downstream rather than by the projection's shape, and it is three
 * things rather than one: {@code AssistanceTextTokenizer} rejects every token containing a digit (which
 * removes account numbers, card numbers, amounts and dates), the refresh will not STORE a token
 * appearing in fewer than three labelled complaints (a k-anonymity floor with k=3), and
 * {@code ASSISTANCE_CATEGORY_PRIOR} holds no complaint id, so nothing persisted can be joined back to a
 * complainant.
 *
 * <p>What that leaves, named rather than hidden: this record is held in the scheduled job's heap for the
 * duration of one page, so a heap dump or a {@code toString()} in a future log line over THIS projection
 * would disclose complaint text — unlike over its siblings. Nothing logs it today; the refresh logs
 * counts and token names only. The rule for anyone extending that job is that the text may be TOKENISED
 * but never logged.
 */
public final class CategoryPriorProjections {

    private CategoryPriorProjections() {
    }

    /**
     * One labelled complaint reduced to its label and its text.
     *
     * <p>Used ONLY by the scheduled refresh, never on a request.
     *
     * <p>{@code complaintId} is carried for KEYSET PAGING and nothing else: the refresh walks
     * {@code WHERE c.id > :afterId ORDER BY c.id}, so each page needs the last id it saw. An
     * offset-based page would re-read rows as complaints are categorised underneath a long refresh —
     * which, for a rollup whose source slice grows precisely BECAUSE officers are accepting its
     * suggestions, is not a hypothetical.
     *
     * <p>{@code categoryId} is NEVER null here: the query filters on it being present, because a
     * complaint with no category is no evidence about categories. That is the opposite of
     * {@code ClauseAffinityProjections.ClosureDimensions}, where every dimension is nullable and a null
     * maps to a wildcard sentinel — there is no wildcard category to map to, so the row is excluded
     * instead of being counted against a sentinel.
     *
     * <p>{@code subject} and {@code description} are each independently nullable and that is normal.
     * MEASURED: 60 of 4,403 complaints have neither, and many have a subject with a null description.
     * {@code AssistanceTextTokenizer.tokenize} pools whatever is present and skips what is not, so a
     * subject-only complaint still contributes — a complaint with only a subject line is still evidence
     * about what words go with what category.
     */
    public record LabelledText(Long complaintId,
                               Long categoryId,
                               String subject,
                               String description) {
    }
}
