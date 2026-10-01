package com.hrms.cms.service.report;

import java.util.Arrays;
import java.util.Optional;

/**
 * The report filter operators this server actually compiles (UST616-619, UST671-672).
 *
 * <h2>Why this enum exists</h2>
 * The Angular client has offered {@code EQUAL | BETWEEN | GREATER_THAN | LESS_THAN | LIKE | IN} for
 * some time, with per-field-type gating. The server recognised exactly one operator string,
 * {@code "RANGE"}, and sent every other value into an {@code else} branch that became
 * {@code cb.equal(...)}. The consequences were not degraded filtering but WRONG filtering:
 *
 * <ul>
 *   <li>{@code BETWEEN} arrived as the literal pipe-joined string {@code "from|to"} and was compared
 *       as a string, so it could never match a row.</li>
 *   <li>{@code IN} arrived as {@code "a, b, c"} and was compared whole, so it could never match.</li>
 *   <li>{@code GREATER_THAN} and {@code LESS_THAN} silently became equality — the dangerous case,
 *       because those DO return rows, just the wrong ones, and nothing looks broken.</li>
 *   <li>{@code LIKE} became equality with no wildcard.</li>
 * </ul>
 *
 * <h2>Unknown operators are refused, not defaulted</h2>
 * {@link #from(String)} returns empty for an unrecognised or null operator and the caller rejects the
 * query. Defaulting to equality is what made the original defect invisible: a typo, a new client
 * operator the server had not implemented, and a null all produced plausible output.
 */
public enum ReportFilterOperator {

    /** Exact match. For a PICKER field this is the identity comparison the UI implies. */
    EQUAL(1),

    /**
     * Inclusive range. Two parameters, delivered by the client as {@code "from|to"}.
     *
     * <p>The pipe encoding is the CLIENT's existing wire format (report-builder.component.ts builds it
     * in two places). It is honoured rather than changed so this rewrite needs no coordinated frontend
     * release; the parsing now happens instead of the string being compared whole.
     */
    BETWEEN(2),

    GREATER_THAN(1),

    LESS_THAN(1),

    /**
     * Partial match. Exactly ONE parameter per UST618.
     *
     * <p>The wire value carries no wildcards — neither client nor server added them before — so the
     * server supplies {@code %value%} itself. That is also why a multi-parameter LIKE has to be
     * refused rather than silently using the first value: a user who typed two things and got results
     * for one would reasonably believe both had been applied.
     */
    LIKE(1),

    /** Set membership. Comma-separated, capped server-side per UST618. */
    IN(-1),

    /**
     * A named relative window such as {@code LAST_30D}, resolved server-side.
     *
     * <p>Retained because {@code SemanticModelRegistry} pins this operator on its ten {@code time}
     * filters and saved dashboard widgets already hold it in their persisted query JSON. Dropping it
     * would break every scheduled report and saved widget in the database.
     */
    RANGE(1);

    /** Expected parameter count; -1 means variable. */
    private final int parameterCount;

    ReportFilterOperator(int parameterCount) {
        this.parameterCount = parameterCount;
    }

    public int parameterCount() {
        return parameterCount;
    }

    public boolean isVariableArity() {
        return parameterCount < 0;
    }

    /**
     * Resolves a wire operator string.
     *
     * <p>{@code "="} is accepted as an alias for {@link #EQUAL} because that is the literal
     * {@code SemanticModelRegistry} pins on its 41 non-time filters and therefore what every saved
     * widget and schedule in the database contains. Rejecting it would break persisted reports.
     */
    public static Optional<ReportFilterOperator> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalised = raw.trim().toUpperCase();
        if ("=".equals(normalised) || "EQUALS".equals(normalised)) {
            return Optional.of(EQUAL);
        }
        return Arrays.stream(values()).filter(op -> op.name().equals(normalised)).findFirst();
    }

    /** The operator names this server compiles, for the semantic-model response. */
    public static String[] names() {
        return Arrays.stream(values()).map(Enum::name).toArray(String[]::new);
    }
}
