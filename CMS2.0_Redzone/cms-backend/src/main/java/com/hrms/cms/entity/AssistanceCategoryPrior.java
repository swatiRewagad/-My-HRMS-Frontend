package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * How often ONE word co-occurred with ONE category across the labelled complaint register.
 *
 * <h2>What a row MEANS, said precisely</h2>
 * "Of the {@link #tokenTotal} labelled complaints whose subject or description contains
 * {@link #token}, {@link #occurrences} of them were categorised as {@link #categoryKey}. That category
 * holds {@link #categoryTotal} of the {@link #labelledTotal} labelled complaints overall."
 *
 * <p>Nothing more. It is a COUNT of co-occurrence between a word and a label — no embedding, no
 * weights, no model artifact, nothing that has been trained. That is what keeps this inside the same
 * Tier 1 rule as {@link AssistanceClauseAffinity} and {@link AssistanceNextAction}, and it is why every
 * number this feature shows an officer arrives with its denominator attached.
 *
 * <h2>Why this rollup is the one that matters most</h2>
 * {@code COMPLAINTS.category_id} is populated on <b>273 of 4,403</b> rows (6.2%). That single gap is why
 * three already-shipped assistance features are measurably quiet: {@link AssistanceNextAction},
 * {@link AssistanceClauseAffinity} and the entity-pattern rollup all carry a {@code CATEGORY_KEY} column
 * that holds the wildcard sentinel {@code 0} on essentially every row they can write. None of the three
 * needs a code change to sharpen — they need {@code category_id} populated. {@code subject} and
 * {@code description} are populated on 4,343 of 4,403 (98.6%), so the INPUT side is rich and the LABEL
 * side is thin, and that asymmetry is the shape of the whole feature.
 *
 * <h2>TWO key columns, where the siblings have five and six — measured, not casual</h2>
 * The key is {@code (TOKEN, CATEGORY_KEY)}. Each candidate extra dimension was measured against
 * {@code cms_db} on 2026-10-07 and rejected:
 * <ul>
 *   <li>{@code SCHEME_VERSION} — rejected, though every sibling keeps it and never wildcards it. 4,092
 *       of 4,403 complaints carry none, so the dimension would be a constant. And a WORD is not
 *       scheme-scoped: "ATM" means the same thing under {@code RBIOS_2021} and {@code RBIOS_2026},
 *       whereas clause {@code 15(1)(a)} does not. Pooling schemes is a correctness bug for a clause
 *       vocabulary and free for a token. This is the one place this rollup's key legitimately diverges
 *       from its siblings', recorded here so it is not read as an omission.
 *   <li>{@code DEPARTMENT} — rejected on arithmetic. Splitting 273 rows across CEPC / RBIO / CRPC puts
 *       every token below the 3-document floor in the smaller two, so the rollup would hold CEPC rows
 *       and nothing else while LOOKING department-aware.
 *   <li>{@code GROUND_OF_COMPLAINT_ID} — rejected. Populated on 0 of 4,403 rows.
 *       {@link AssistanceClauseAffinity} keeps it as a sentinel-bearing key part because the brief names
 *       it in the clause key; nothing names it here, and a key column carried on faith is the kind of
 *       thing a later reader mistakes for a working dimension.
 * </ul>
 *
 * <h2>The sentinel rule applies, and this table never uses it</h2>
 * Composite unique keys in this schema use sentinels and never NULL, because a NULL in a composite
 * unique key prevents no duplicates on MySQL OR Oracle (so the key would be non-idempotent and every
 * refresh would append rather than update) and because {@code = NULL} is never true (so a nullable key
 * column is unreachable by any seek).
 *
 * <p>Both key columns here are {@code NOT NULL} and NEITHER is ever a wildcard. {@link #categoryKey}
 * always holds a real {@code complaint_categories.id}, because this rollup mines ONLY rows that carry
 * one — a complaint with no category is no evidence about categories. {@link #token} is never blank. So
 * the numeric sentinel {@code 0} is RESERVED and never written, and that is said out loud so a reader
 * comparing this class with {@link AssistanceClauseAffinity} does not go looking for wildcard rows that
 * cannot exist.
 *
 * <h2>PII: what is enforced structurally, and the residual risk that is accepted and named</h2>
 * Unlike its siblings, this rollup is derived from FREE TEXT a complainant wrote, so "the projection
 * carries no PII column" is not available as an argument. What is enforced instead:
 * <ul>
 *   <li>{@code AssistanceTextTokenizer} rejects any token containing a digit, which removes account
 *       numbers, card numbers, amounts, reference numbers and dates.
 *   <li>A token must appear in at least 3 labelled complaints to be stored — a k-anonymity floor with
 *       k=3, which is what makes any token unique to one complainant unstorable.
 *   <li>This table holds NO complaint id and no row-level reference of any kind, so a stored token
 *       cannot be joined back to the complaint it came from.
 * </ul>
 * The residual risk, stated rather than hidden: a common given name appearing in three or more
 * complaint bodies would survive all three guards and be stored as a token. A name-derived blocklist
 * was built and MEASURED and is deliberately NOT shipped — see {@code AssistanceTextTokenizer}, which
 * records why and what it would cost.
 *
 * @see com.hrms.cms.service.AssistanceCategoryPriorRefreshService the only writer
 * @see com.hrms.cms.service.CategorySuggestionService the only reader
 */
@Entity
@Table(name = "ASSISTANCE_CATEGORY_PRIOR",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ACP_TOKEN_CATEGORY",
                columnNames = {"TOKEN", "CATEGORY_KEY"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistanceCategoryPrior {

    /**
     * Row cap for one read.
     *
     * <p>{@code MAX_QUERY_TOKENS} (64) distinct tokens, each able to carry one row per category. The
     * register holds 10 categories today; 16 per token is headroom for a master that grows without the
     * cap becoming the thing that silently truncates a distribution, which is the failure mode a cap
     * chosen from a round number has. §6.2 requires a DECLARED cap on every query.
     *
     * <p>MEASURED: the whole table holds 141 rows on today's register, so this cap is not reachable
     * today. It is declared for the register that is 100× larger, not for this one.
     */
    public static final int MAX_PRIOR_ROWS = 64 * 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One normalised word from a labelled complaint's subject or description.
     *
     * <p>Written LOWER-cased, punctuation-stripped and digit-free by
     * {@code AssistanceTextTokenizer#tokenize}, which is the SAME class the read path applies to the
     * officer's text. One tokenizer, two callers — a second implementation on the read side is how a
     * rollup comes to be keyed on words no read can ever match.
     *
     * <p>LOWER, not UPPER, and this is the only text column in the assistance feature set that is not
     * upper-cased. The siblings upper-case because they carry controlled vocabularies (status names,
     * department codes) that already appear in mixed case in this database; a token is not a code, and
     * lower case is a word's natural written form. The case choice matters only in that both sides must
     * agree, which one shared class guarantees. The cross-engine point still holds and is the reason
     * normalisation happens in Java at all: MySQL's {@code utf8mb4_unicode_ci} folds comparisons for
     * free and Oracle's default collation does not, so a token stored verbatim and compared verbatim
     * would match in dev and MISS in production.
     *
     * <p>{@code length = 64} matches {@code AssistanceTextTokenizer.MAX_TOKEN_LENGTH} exactly, so a
     * token the tokenizer accepts can never be silently truncated on the way in — a truncated token
     * would key a row no read could seek, and MySQL in non-strict mode truncates without erroring while
     * Oracle raises. The tokenizer enforces the same bound in Java so the guarantee does not depend on
     * either engine's mode.
     */
    @Column(name = "TOKEN", nullable = false, length = 64)
    private String token;

    /**
     * A real {@code complaint_categories.id}. Never a sentinel, never null.
     *
     * <p>{@code complaint_categories}, NOT {@code category_master}. Both tables exist and both look
     * plausible; MEASURED, {@code category_master.category_name} reads {@code 'S1 authority e2e probe'}
     * for ids 1-6 and has no row at all for 7-10, while {@code complaint_categories} names the ten real
     * categories the register uses and its {@code id} is what {@code COMPLAINTS.category_id} resolves
     * against. A reader who joined the other master would conclude the data was garbage.
     */
    @Column(name = "CATEGORY_KEY", nullable = false)
    private Long categoryKey;

    /** How many labelled complaints containing {@link #token} were this category. The numerator. */
    @Column(name = "OCCURRENCES", nullable = false)
    private Long occurrences;

    /**
     * How many labelled complaints contain {@link #token} at all, across every category. The
     * denominator.
     *
     * <p>{@code nullable = false} because the brief's position is that "a bare recommendation with no
     * denominator will be distrusted, correctly", so the schema does not permit storing one without the
     * other. "matched 41 of 52 complaints containing 'withdrawal'" is a statement an officer can weigh;
     * "category: ATM" is not.
     *
     * <p>Denormalised onto every row of a token rather than derived by summing them, so the read needs
     * no second aggregate and so a partially-refreshed token cannot show a numerator from this pass
     * against a denominator built from the last.
     */
    @Column(name = "TOKEN_TOTAL", nullable = false)
    private Long tokenTotal;

    /**
     * How many labelled complaints this category holds in total. Half of the BASE RATE.
     *
     * <p>Carried so the read can compute LIFT without a second query. This is what stops the feature
     * from simply naming the majority class: category 1 holds 202 of 273 labels (74%), so a category
     * whose mean {@code P(category|token)} is 0.5 is doing WORSE than guessing and must not be
     * suggested. {@code CategorySuggestionService.MIN_LIFT} is enforced against
     * {@code categoryTotal / labelledTotal}, which is computable from ONE row — which is the entire
     * reason this is denormalised here rather than read from {@code complaint_categories} on request.
     * §6.2 forbids the request-time aggregate the alternative would need.
     */
    @Column(name = "CATEGORY_TOTAL", nullable = false)
    private Long categoryTotal;

    /**
     * How many labelled complaints the whole corpus holds. The other half of the base rate.
     *
     * <p>Constant across every row of one refresh pass, which looks like redundancy and is not: it
     * makes each row a self-contained, independently-interpretable statement, and it means a row read
     * DURING a refresh carries the base rate from the SAME pass as its own counts rather than from
     * whatever the corpus had grown to by the time the read happened.
     */
    @Column(name = "LABELLED_TOTAL", nullable = false)
    private Long labelledTotal;

    /**
     * When the refresh that wrote this row ran.
     *
     * <p>Read by nothing in the request path. It exists so a rollup that quietly stopped refreshing is
     * diagnosable, which is the one failure a scheduled job has that an endpoint does not: stale counts
     * look exactly like correct counts. It is also the stale sweep's only predicate.
     */
    @Column(name = "REFRESHED_AT", nullable = false)
    private LocalDateTime refreshedAt;

    /**
     * This category's share of the complaints containing this token, 0..1.
     *
     * <p>{@code P(category | token)}. Derived, never stored — the two counts are the truth, and a
     * stored ratio is a third number that can disagree with them.
     */
    @Transient
    public double share() {
        if (tokenTotal == null || tokenTotal <= 0 || occurrences == null) {
            return 0d;
        }
        return (double) occurrences / (double) tokenTotal;
    }

    /**
     * This category's share of the whole labelled corpus, 0..1. The BASE RATE.
     *
     * <p>The number a suggestion has to beat. A category that is 74% of the corpus is not evidenced by
     * a token that points at it 74% of the time.
     */
    @Transient
    public double baseRate() {
        if (labelledTotal == null || labelledTotal <= 0 || categoryTotal == null) {
            return 0d;
        }
        return (double) categoryTotal / (double) labelledTotal;
    }
}
