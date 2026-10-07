package com.hrms.cms.repository.projection;

import java.time.LocalDateTime;

/**
 * Constructor-expression carrier for the entity-pattern rollup (Brief 21 §5.3.5).
 *
 * <p>Same reasoning as {@link AssistanceRailProjections} and {@link ClauseAffinityProjections}:
 * {@code COMPLAINTS} has ~105 columns including six {@code TEXT} bodies, and this refresh walks every
 * complaint that can be keyed — 4,112 of 4,403 rows on the dev register. Hydrating {@code Complaint}
 * entities to read six scalars would make the job's memory scale with the widest part of the table rather
 * than with the part it uses, and would pull six text bodies through the persistence context for a job
 * that counts rows. JPQL {@code SELECT new} rather than native SQL because the deployed profiles run
 * OracleDialect and only dev-local runs MySQL.
 */
public final class EntityPatternProjections {

    private EntityPatternProjections() {
    }

    /**
     * One complaint reduced to the five things the rollup keys or counts on, plus its id.
     *
     * <h3>PII: there is nothing here to disclose, and that is checkable rather than asserted</h3>
     * No complainant name, email, phone, address, account or card number; no complaint number; no
     * subject and no description. §4's PII rule enforced at the PROJECTION rather than at the
     * serialiser, so a future log line, a heap dump or a debugger session over this job cannot disclose
     * what the rollup's own row already cannot. The rollup this feeds holds five keys, two counts and a
     * timestamp — so the constraint holds end to end, from the source read to the stored row.
     *
     * <p>Note the contrast with {@code AssistanceRailProjections.RailContext}, which DOES carry
     * {@code complainantEmail}: that one serves a request and needs the email as a join key for the
     * history prior. This one serves a scheduled job that aggregates across complainants, so it has no
     * business knowing who any of them are.
     *
     * <h3>{@code complaintId} is carried for KEYSET PAGING and for nothing else</h3>
     * The refresh walks {@code WHERE c.id > :afterId ORDER BY c.id}, so each page needs the last id it
     * saw. An offset-based page would re-read rows as complaints are filed underneath a long refresh,
     * and would degrade on the deep pages — which on an append-ordered register is where the recent
     * history lives. The id is never stored and never counted.
     *
     * <h3>{@code entityCode} is FREE TEXT and documented dirty</h3>
     * Carried RAW and normalised in Java by {@code AssistanceEntityAliasNormaliser}, for two reasons
     * that are both about keeping the two sides of the rollup in agreement. First, the normalisation is
     * an explicit ALIAS TABLE ({@code PNB} to {@code PUNJAB NATIONAL BANK}) and has no JPQL equivalent —
     * a {@code CASE} expression spelling it in SQL would have to be duplicated in the Oracle migration
     * and would then be able to drift from the Java one silently. Second, the READ path compares against
     * an already-normalised parameter, so normalising on the write side is what lets the read be an
     * index seek rather than a function wrap.
     *
     * <h3>{@code createdAt} is carried so the QUARTER is computed in JAVA</h3>
     * {@code QUARTER_KEY} is {@code yyyyQ} and the arithmetic is deliberately not in the query:
     * {@code QUARTER()} is MySQL's spelling and Oracle wants {@code TO_CHAR(..., 'Q')}, and this value
     * is part of a UNIQUE key, so the two engines producing even slightly different integers would mean
     * two different sets of rows for the same register. Computing it from this timestamp in one Java
     * method makes that impossible by construction. It is {@code created_at} and not any later date
     * because the brief's "this quarter" is the quarter the complaint was FILED in.
     *
     * <p>Never null in practice — {@code created_at} is written by the insert path on every row — but
     * typed as the nullable object anyway, because a JPQL constructor expression cannot express
     * "not null" and the job must not NPE on a row a migration left behind. The refresh skips a row
     * whose filing date it cannot read rather than guessing a quarter for it.
     *
     * <h3>{@code groundOfComplaintId} is NULL ON EVERY ROW TODAY</h3>
     * Measured: populated on 0 of 4,403. It is carried regardless, because it is the brief's OWN
     * dimension ("on this ground") and the rollup's key reserves a column for it with a sentinel. The
     * day the workflow starts writing it, this projection already supplies it and the signal sharpens
     * with no schema and no code change. Carrying a column that is always null is the cheap half of that
     * bargain.
     *
     * <h3>{@code rbioOfficeCode} is SPARSE, not empty</h3>
     * Measured: 410 of 4,403. A null maps to {@code AssistanceEntityPattern.SCOPE_AGNOSTIC} rather than
     * causing the row to be discarded — a complaint with no office is still evidence about the entity at
     * the office-agnostic level, which is the level 91% of the register lives at.
     *
     * <h3>{@code status} is the LEGACY LOWERCASE value</h3>
     * {@code COMPLAINTS.status} holds 22 distinct values, all lowercase, while the
     * {@code ComplaintStatus} enum is UPPERCASE. It is carried as a raw string and compared in Java
     * against {@code RbioStatusVocabulary}'s list — which is also lowercase, being read from
     * {@code RBIO_STATUS_MASTER.legacy_value} — with an explicit case-insensitive comparison, because a
     * predicate written in the enum's spelling matches nothing on MySQL's ai_ci collation except by
     * accident and matches NOTHING AT ALL on Oracle. Deciding it in Java makes the two engines agree by
     * construction instead of by collation.
     *
     * <p>{@code entityCode} and {@code department} are guaranteed non-blank by the query's own
     * predicates, so the job does not re-check them for blankness; {@code status} is NOT guaranteed and
     * the job treats a blank status as open, which is the conservative reading — see the refresh service.
     */
    public record EntityCase(Long complaintId,
                             String entityCode,
                             String department,
                             LocalDateTime createdAt,
                             Long groundOfComplaintId,
                             String rbioOfficeCode,
                             String status) {
    }
}
