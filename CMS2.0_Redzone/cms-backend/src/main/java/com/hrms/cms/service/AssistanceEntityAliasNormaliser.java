package com.hrms.cms.service;

import java.util.Map;

/**
 * Resolves {@code COMPLAINTS.entity_code}'s aliases to one canonical name (Brief 21 §5.3.5, §5.4).
 *
 * <h2>Why a case fold was not enough, measured</h2>
 * Brief 21 §5.4 flags {@code entity_code} as dirty and the dirt is worse than mixed case. On the dev
 * register, 4,113 non-blank values resolve to 33 DISTINCT strings, among them these pairs that each
 * denote one entity:
 * <pre>
 *   HDFC / HDFC Bank              ICICI / ICICI Bank
 *   AXIS / Axis Bank              CANARA / Canara Bank
 *   PNB / Punjab National Bank    SBI / State Bank of India
 *   BOB / Bank of Baroda          UNION / Union Bank / Union Bank of India
 *   INDIAN                        KOTAK
 * </pre>
 * {@code UPPER()} merges NONE of them — {@code PNB} and {@code PUNJAB NATIONAL BANK} share no character
 * position. So the normalisation has to be an explicit ALIAS TABLE, and the measured effect of this one
 * is 33 distinct values down to 24.
 *
 * <h2>Both sides of the rollup go through here, and that is the point</h2>
 * {@code AssistanceEntityPatternRefreshService} normalises on the WRITE, and
 * {@link AssistanceRailService} normalises the complaint's own {@code entity_code} on the READ before
 * seeking. If only one side did it, the rollup would hold {@code PNB}'s count under one key and be asked
 * for it under another — which is a WRONG number rather than a missing one, because the count that comes
 * back would be the alias's own partial tally presented as the entity's total.
 *
 * <p>Normalising on the write side also removes a real cross-engine defect rather than a cosmetic one.
 * MySQL's {@code utf8mb4_0900_ai_ci} collation folds case for free, so {@code 'HDFC Bank'} and
 * {@code 'HDFC BANK'} are one key there; Oracle's default collation is case-SENSITIVE and would hold
 * them apart. Upper-casing before storing makes the two engines agree, which is the same argument
 * {@code ComplaintRepository} records for its own {@code entity_code} comparisons — except that here the
 * value is being WRITTEN, so it can be fixed properly instead of documented as a divergence.
 *
 * <h2>What this deliberately is NOT</h2>
 * <ul>
 *   <li><b>Not a global data fix.</b> §5.4: "Do not 'fix' {@code entity_code} globally — that is a
 *       data-migration project owned elsewhere. Handle the dirt where you read it, and report it."
 *       Nothing here issues an {@code UPDATE}.</li>
 *   <li><b>Not fuzzy.</b> No edit distance, no prefix match, no token overlap. A similarity match on an
 *       entity name merges two different banks on a bad day, and a wrong count presented as a pattern is
 *       worse than no signal at all. The table is finite and hand-checked against the register.</li>
 *   <li><b>Not an authority on who the entity IS.</b> It resolves spellings for the purpose of COUNTING.
 *       It is not consulted for jurisdiction, for notices, or for anything that names the entity to a
 *       party.</li>
 * </ul>
 *
 * <h2>The limitation, stated rather than implied</h2>
 * A new abbreviation typed into a complaint tomorrow is a NEW entity to this normaliser, and will
 * quietly split that entity's count between two keys. The failure is invisible — two cohorts where there
 * should be one, each with a smaller numerator, and both possibly falling under the sample floor so the
 * signal just goes quiet. That is the cost of refusing to guess, and it is the right trade here, but it
 * is a cost.
 */
public final class AssistanceEntityAliasNormaliser {

    /**
     * Alias in UPPER CASE to canonical name in UPPER CASE.
     *
     * <p>Keyed on the upper-cased form so one entry covers every casing of an alias
     * ({@code hdfc}, {@code HDFC}, {@code Hdfc}). The canonical side is the FULL name rather than the
     * abbreviation, because the full name is what an officer reads in the signal's own prose and because
     * {@code entity_code}'s column is 50 characters — long enough for every expansion here, so the
     * canonical value can never be the truncated one.
     *
     * <p>Each canonical name also maps to ITSELF. Not redundant: it makes the table the single statement
     * of what is canonical, so a reader can see that {@code HDFC BANK} is a destination rather than
     * having to infer it from the absence of an entry.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("HDFC", "HDFC BANK"),
            Map.entry("HDFC BANK", "HDFC BANK"),
            Map.entry("ICICI", "ICICI BANK"),
            Map.entry("ICICI BANK", "ICICI BANK"),
            Map.entry("AXIS", "AXIS BANK"),
            Map.entry("AXIS BANK", "AXIS BANK"),
            Map.entry("CANARA", "CANARA BANK"),
            Map.entry("CANARA BANK", "CANARA BANK"),
            Map.entry("PNB", "PUNJAB NATIONAL BANK"),
            Map.entry("PUNJAB NATIONAL BANK", "PUNJAB NATIONAL BANK"),
            Map.entry("SBI", "STATE BANK OF INDIA"),
            Map.entry("STATE BANK OF INDIA", "STATE BANK OF INDIA"),
            Map.entry("BOB", "BANK OF BARODA"),
            Map.entry("BANK OF BARODA", "BANK OF BARODA"),
            Map.entry("UNION", "UNION BANK OF INDIA"),
            Map.entry("UNION BANK", "UNION BANK OF INDIA"),
            Map.entry("UNION BANK OF INDIA", "UNION BANK OF INDIA"),
            Map.entry("INDIAN", "INDIAN BANK"),
            Map.entry("INDIAN BANK", "INDIAN BANK"),
            Map.entry("KOTAK", "KOTAK MAHINDRA BANK"),
            Map.entry("KOTAK MAHINDRA BANK", "KOTAK MAHINDRA BANK"));

    /** {@code ENTITY_KEY}'s column width. A canonical name longer than this would be truncated. */
    static final int MAX_LENGTH = 50;

    private AssistanceEntityAliasNormaliser() {
    }

    /**
     * The canonical key for one {@code entity_code} value, or null when there is nothing to key on.
     *
     * <p>Null for null and for blank, and the two are treated alike on purpose: 4,403 minus 4,113 rows
     * carry no usable entity, some as NULL and some as {@code ''}, and keying a cohort on the empty
     * string would pool every entity-less complaint under one entity that does not exist.
     *
     * <p>An UNKNOWN value is returned upper-cased and trimmed rather than rejected. That is the whole
     * reason the normaliser degrades well: a bank nobody wrote an alias for still gets a stable key and
     * still accumulates a cohort — it simply does not get merged with a differently-spelled version of
     * itself. Refusing unknown values would make the feature silent on every entity outside this table.
     *
     * <p>Truncated to {@link #MAX_LENGTH} as the last step. {@code entity_code} is {@code varchar(50)}
     * and so is {@code ENTITY_KEY}, so a pass-through value always fits; the guard exists because the
     * canonical side of the alias table is longer than the alias, and a future entry longer than 50
     * characters would otherwise fail the insert at refresh time rather than being caught here.
     */
    public static String normalise(String entityCode) {
        if (entityCode == null) {
            return null;
        }
        String trimmed = entityCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
        String canonical = ALIASES.getOrDefault(upper, upper);
        return canonical.length() <= MAX_LENGTH ? canonical : canonical.substring(0, MAX_LENGTH);
    }
}
