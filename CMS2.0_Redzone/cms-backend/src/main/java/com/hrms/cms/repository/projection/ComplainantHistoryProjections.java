package com.hrms.cms.repository.projection;

import java.time.LocalDateTime;

/**
 * The projections the complainant filing-history feature reads over its SOURCE data.
 *
 * <p>Records rather than interface projections, matching {@code ClauseAffinityProjections}: a record
 * constructor expression in JPQL fails at startup if a field is renamed, whereas an interface projection
 * silently returns null for a getter whose property no longer exists.
 *
 * <h2>Why the refresh reads a projection and not {@code Complaint}</h2>
 * {@code COMPLAINTS} carries ~105 columns and six {@code TEXT} bodies. The job needs nine scalars from
 * every one of 4,403 rows, and hydrating full entities to read nine columns would put the whole register
 * — narratives included — through the persistence context on every pass.
 *
 * <p>It also matters for PRIVACY, not only for cost: the job physically cannot copy a complainant NAME,
 * ADDRESS or ACCOUNT NUMBER into the projection table, because the projection it reads has no field for
 * one. The subject and description are likewise absent. See {@code AssistanceComplainantHistory} for the
 * full argument; this is the upstream half of it.
 */
public final class ComplainantHistoryProjections {

    private ComplainantHistoryProjections() {
    }

    /**
     * The nine columns the projection is built from, for ONE complaint.
     *
     * <p>Every field is raw and un-normalised — the normalisation happens in
     * {@code AssistanceComplainantHistoryRefreshService}, in Java, because it needs
     * {@code AssistanceEntityAliasNormaliser}'s alias table and a regex digit strip, neither of which has
     * a JPQL equivalent that would not wrap an indexed column.
     *
     * @param complaintId     the keyset cursor. The job pages on {@code id > :afterId ORDER BY id}, so
     *                        this is the only reason the id is read at all — it is never stored.
     * @param complaintNumber the projection's own key
     * @param complainantEmail raw, possibly null, possibly {@code ''} (21 rows), possibly mixed-case
     * @param complainantPhone raw, possibly null, with separators and a country code
     * @param entityCode      raw and documented-dirty; {@code 'PNB'} and {@code 'Punjab National Bank'}
     *                        are the same bank and only the alias normaliser merges them
     * @param department      the scope key, raw
     * @param status          displayed verbatim, so deliberately NOT folded downstream
     * @param filedAt         preferred filing instant; null on many rows, hence {@code createdAt}
     * @param createdAt       the fallback, complete on all 4,403 rows
     * @param maintainabilityDetermination first of the three non-maintainability markers
     * @param closureCause    second marker
     * @param closureClause   third marker — a citation under {@code 16(2)} counts
     */
    public record ComplainantRow(Long complaintId,
                                 String complaintNumber,
                                 String complainantEmail,
                                 String complainantPhone,
                                 String entityCode,
                                 String department,
                                 String status,
                                 LocalDateTime filedAt,
                                 LocalDateTime createdAt,
                                 String maintainabilityDetermination,
                                 String closureCause,
                                 String closureClause) {

        /**
         * The filing instant: {@code filedAt} if present, else {@code createdAt}.
         *
         * <p>Resolved here rather than with a JPQL {@code COALESCE} so that the fallback rule lives
         * somewhere a unit test can pin it. MEASURED: {@code createdAt} is populated on all 4,403 rows
         * while {@code filedAt} is not, and {@code Complaint}'s {@code @PrePersist} sets both, so the two
         * agree on everything written since. A row with NEITHER is skipped by the refresh rather than
         * stored with a fabricated date — see the refresh service.
         */
        public LocalDateTime effectiveFiledAt() {
            return filedAt != null ? filedAt : createdAt;
        }
    }

    // ─── THE CONTACT FAN-OUT GUARD HAS NO PROJECTION, AND THAT IS DELIBERATE ───
    //
    // The guard is the one true AGGREGATE this feature needs — "how many DISTINCT emails does this phone
    // appear beside" — and it is why a scheduled job exists at all rather than the read querying
    // COMPLAINTS directly. A phone above the threshold is a PLACEHOLDER, not an identity.
    //
    // MEASURED, and this is the measurement that decided the whole design: 9876543210 appears on 3,527
    // complaints across 3,427 distinct emails. Matching on "email OR phone" without the guard flags
    // 3,849 of 4,403 complaints (87%) as duplicate filings; with it, 259 (5.9%). An 87% hit rate is not a
    // signal, it is a banner an officer would learn to ignore within a day, and the 16(2)(b) count it
    // feeds would be evidence of nothing.
    //
    // It is computed IN JAVA, from a first pass over ComplainantRow, rather than as a GROUP BY in SQL.
    // The reason is correctness and not convenience: the guard must count DISTINCT NORMALISED peers, and
    // the normalisation is a 10-digit tail extracted by a regex digit-strip plus a lower-case trim.
    // Expressing that in JPQL is not possible, and expressing it in native SQL per engine would mean the
    // fan-out was measured by one implementation (MySQL REGEXP_REPLACE / Oracle REGEXP_REPLACE) while the
    // KEYS were written by another (Java) — so a single disagreement about, say, a trailing non-breaking
    // space would suppress a phone whose key was nonetheless written, or the reverse. One normaliser, one
    // pass, no possible divergence. The cost is a second scan of a table the job already scans in full.
    //
    // §6.2 forbids this shape in a REQUEST path, which is exactly where it does not run.
}
