package com.hrms.cms.repository.projection;

/**
 * Constructor-expression carriers for the closure-clause affinity rollup (Brief 21 §5.3.2).
 *
 * <p>Same reasoning as {@link AssistanceRailProjections}: {@code COMPLAINTS} has ~105 columns
 * including six {@code TEXT} bodies, and the refresh walks every clause-bearing row, so hydrating
 * {@code Complaint} entities to read seven scalars would make the job's memory scale with the widest
 * part of the table rather than with the part it uses. JPQL {@code SELECT new} rather than native SQL
 * because the deployed profiles run OracleDialect and only dev-local runs MySQL.
 */
public final class ClauseAffinityProjections {

    private ClauseAffinityProjections() {
    }

    /**
     * One past closure reduced to the dimensions the rollup keys on, plus its id.
     *
     * <p>Used ONLY by the scheduled refresh, never on a request. Carries no complainant name, email,
     * phone, address, subject or description — §4's PII rule enforced at the projection rather than at
     * the serialiser, so a future log line or a debugger session over this job cannot disclose what the
     * response already does not.
     *
     * <p>{@code complaintId} is carried for KEYSET PAGING and nothing else: the refresh walks
     * {@code WHERE c.id > :afterId ORDER BY c.id}, so each page needs the last id it saw. An
     * offset-based page would re-read rows as complaints close underneath a long refresh, and would
     * degrade on the deep pages — which is where the most recent closures live.
     *
     * <p>Every other field is NULLABLE and that is normal, not a defect to filter out. Measured on
     * {@code cms_db}: {@code groundOfComplaintId} is null on 100% of rows, {@code categoryId} on 93% of
     * clause-bearing rows, {@code maintainabilityDetermination} on 97%. The refresh maps each null to
     * the matching wildcard sentinel rather than discarding the row, because a closure with no category
     * is still evidence about the clause vocabulary at the levels that do not key on category.
     *
     * <p>{@code entityCode} is FREE TEXT and documented dirty — the same bank appears as
     * {@code 'Punjab National Bank'} and {@code 'PNB'}, and it holds {@code 'Test Bank Ltd'} on 876 of
     * 877 clause-bearing rows, which matches no registered entity. It is carried as the raw code and
     * resolved to an entity TYPE in Java against a small in-memory map, for two reasons: the
     * normalisation {@code RegulatedEntity.normalize} applies (strip non-alphanumerics, collapse
     * whitespace, upper-case) has no JPQL equivalent, and expressing it in SQL would mean wrapping an
     * indexed column in functions, which §6.2 forbids outright.
     */
    public record ClosureDimensions(Long complaintId,
                                    String schemeVersion,
                                    String department,
                                    Long categoryId,
                                    Long groundOfComplaintId,
                                    String entityCode,
                                    String maintainabilityDetermination,
                                    String closureClause) {
    }

    /**
     * One registered entity's normalised name and its type, for resolving {@code entity_code}.
     *
     * <p>{@code REGULATED_ENTITIES} holds 145 rows, so the refresh reads the whole (capped) set once
     * per pass into a map rather than issuing a lookup per closure — which would be an N+1 against a
     * table whose entire content fits in a few kilobytes.
     */
    public record EntityTypeRow(String nameNormalized, String entityType) {
    }
}
