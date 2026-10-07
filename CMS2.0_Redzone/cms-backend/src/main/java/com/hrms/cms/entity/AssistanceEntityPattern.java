package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One (entity, ground, quarter, department, office) window's open-case count, precomputed (§5.3.5).
 *
 * <h2>What a row MEANS, said precisely</h2>
 * "Of the {@link #windowTotal} complaints filed against {@link #entityKey} in quarter
 * {@link #quarterKey} inside {@link #department}, {@link #openCases} are still open." That is a COUNT
 * of two columns on the complaint register. No complainant is named, no complaint number is carried,
 * and no text of any kind is stored — see the PII section below, which is a constraint on this class
 * and not a note about it.
 *
 * <h2>DEPARTMENT IS A TENANCY FENCE, NOT A BREAKDOWN DIMENSION</h2>
 * This is the one thing about this table that must not be "simplified" away. {@code department} is
 * ANDed into every RBIO grid query as a hard tenancy predicate, and §5.3.5 is a <b>cross-complaint
 * disclosure</b>: it tells an officer about OTHER complainants' live cases against a named entity.
 * Brief 21 §4 requires the restrictive default until a human rules otherwise, so the department is
 * part of the KEY rather than a column the read may ignore:
 * <ul>
 *   <li>There is NO row aggregated across departments. A cross-department total is not stored, so no
 *       read can accidentally serve one — the restriction is a property of the schema, not of the
 *       query that happens to be written against it.
 *   <li>{@code AssistanceRailService} additionally refuses to read a department other than the one on
 *       the complaint in front of the officer, so the fence holds on both sides. Removing either half
 *       is a disclosure change and needs the ruling recorded in the findings as open ask 3, not a code
 *       review.
 * </ul>
 * A future ruling permitting cross-department visibility would add a {@link #SCOPE_AGNOSTIC} department
 * row the way {@link #officeCode} already carries one; it must not be implemented by dropping the
 * predicate.
 *
 * <h2>NOTHING COMPLAINANT-IDENTIFYING CAN REACH THIS TABLE</h2>
 * Verifiable by inspection of the column list: five keys, two counts, one timestamp. There is no
 * complainant name, phone, email, address, account or card number, no complaint number and no free
 * text — so a row cannot identify anyone even if the whole table were dumped. That is deliberate and
 * is the reason this prior is shippable at all while §5.3.5's underlying question is still open: the
 * payload is a count and a denominator about a REGULATED ENTITY, which is not personal data.
 *
 * <h2>ENTITY_KEY is normalised ON WRITE, and a case fold would not have done it</h2>
 * {@code COMPLAINTS.entity_code} is documented dirty and the dirt is worse than mixed case. MEASURED on
 * {@code cms_db}: 4,113 non-blank values hold 33 distinct strings, and case/whitespace folding collapses
 * 33 to <b>33</b> — i.e. no case collisions exist today, so {@code UPPER(TRIM())} alone buys nothing.
 * What actually costs recall is SYNONYMY: {@code PNB} and {@code PUNJAB NATIONAL BANK}, {@code SBI} and
 * {@code STATE BANK OF INDIA}, {@code HDFC} and {@code HDFC BANK}, plus {@code ICICI}, {@code AXIS},
 * {@code CANARA}, {@code BOB}, {@code KOTAK}, {@code INDIAN} and three spellings of Union Bank. Those
 * are alias pairs denoting ONE entity each, and no amount of case folding merges them.
 *
 * <p>So the normalisation is {@link com.hrms.cms.service.AssistanceEntityAliasNormaliser}, an explicit
 * hand-checked alias table applied on the WRITE side, which takes 33 distinct values to <b>24</b>. The
 * READ normalises the complaint's own {@code entity_code} through the SAME function before seeking —
 * both sides or neither, because a rollup keyed on normalised text and asked for raw text returns the
 * alias's partial tally presented as the entity's total, which is a WRONG number rather than a missing
 * one.
 *
 * <p>NOT a global fix. §5.4 states the {@code entity_code} data migration is owned elsewhere and
 * forbids doing it here; nothing in this feature issues an {@code UPDATE} against {@code COMPLAINTS}.
 * The residual gap is that an abbreviation nobody has written an alias for splits that entity's count
 * silently — reported, not papered over.
 *
 * <h2>GROUND_KEY is a SENTINEL, and today it is the ONLY value</h2>
 * The brief's prescribed dimension is "on this ground". {@code COMPLAINTS.ground_of_complaint_id} is
 * populated on <b>0 of 4403 rows</b>, so that dimension is not merely sparse, it is empty — the brief
 * cannot be satisfied as written. {@link #GROUND_AGNOSTIC} means "this window is not ground-specific",
 * every complaint counts into the agnostic row AND into its ground row where it has one (today: never),
 * and the read prefers the specific row. The day grounds start being written the signal sharpens with no
 * schema or code change, and the service's English says "across all grounds" rather than implying a
 * filter that is not happening.
 *
 * <p>NOT {@code null}: a composite UNIQUE key containing a NULL prevents nothing on MySQL or Oracle, so
 * a NULL ground would make this table non-idempotent and every refresh would append a fresh set of
 * agnostic rows. Verified no real ground id is 0 (AUTO_INCREMENT from 1). {@link #SCOPE_AGNOSTIC} is the
 * same argument for the two string keys, and no real office code is {@code '*'} (measured: 011, 013,
 * 014, 016, 017, 021, C01).
 *
 * @see com.hrms.cms.service.AssistanceEntityPatternRefreshService the only writer
 */
@Entity
@Table(name = "ASSISTANCE_ENTITY_PATTERN",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_AEP_COHORT",
                columnNames = {"ENTITY_KEY", "DEPARTMENT", "QUARTER_KEY", "GROUND_KEY", "OFFICE_CODE"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistanceEntityPattern {

    /** {@code GROUND_KEY} for a window that is not ground-specific. See the class javadoc. */
    public static final long GROUND_AGNOSTIC = 0L;

    /**
     * {@code OFFICE_CODE} / {@code DEPARTMENT} for a window that is not scoped to one of them.
     *
     * <p>A sentinel rather than NULL for the idempotency reason in the class javadoc, and {@code '*'}
     * rather than {@code ''} so that a reader of the table can tell "agnostic" from "blank string that
     * got in by accident" — the two would be indistinguishable, and one of them is a bug.
     */
    public static final String SCOPE_AGNOSTIC = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The alias-resolved, upper-cased entity. Width matched to the source column
     * ({@code COMPLAINTS.entity_code varchar(50)}) so a value cannot be silently truncated on the way
     * into the rollup.
     *
     * <p>Normalised by {@link com.hrms.cms.service.AssistanceEntityAliasNormaliser}, on the WRITE side
     * and again on the read side before seeking — never by wrapping this column in a function, which
     * §6.2 forbids. See the class javadoc.
     */
    @Column(name = "ENTITY_KEY", nullable = false, length = 50)
    private String entityKey;

    /** A real {@code ground_of_complaint_id}, or {@link #GROUND_AGNOSTIC}. Never null. */
    @Column(name = "GROUND_KEY", nullable = false)
    @Builder.Default
    private Long groundKey = GROUND_AGNOSTIC;

    /**
     * The filing quarter as {@code yyyyQ} — 20263 is 2026 Q3.
     *
     * <p>An integer rather than a pair of columns so the read is one equality and not two, and computed
     * in JAVA rather than by the database because {@code QUARTER()} and {@code TO_CHAR} are spelled
     * differently on MySQL and Oracle and this value has to be byte-identical on both. Keyed on when the
     * complaint was FILED ({@code created_at}), which is what the brief's "this quarter" means.
     */
    @Column(name = "QUARTER_KEY", nullable = false)
    private Integer quarterKey;

    /**
     * {@code UPPER(TRIM(COMPLAINTS.department))}, or {@link #SCOPE_AGNOSTIC}. Width matched to the
     * source column ({@code varchar(20)}).
     *
     * <p>THE TENANCY FENCE. Part of the key, so there is no cross-department row to serve. Read the
     * class javadoc before changing anything about this column.
     *
     * <p>MEASURED: populated on 4,380 of 4,403 complaints (CEPC 2,831 / RBIO 1,223 / CRPC 58 among the
     * entity-bearing rows), so the sentinel is reached by one row today and exists for correctness
     * rather than coverage.
     */
    @Column(name = "DEPARTMENT", nullable = false, length = 20)
    @Builder.Default
    private String department = SCOPE_AGNOSTIC;

    /**
     * {@code UPPER(TRIM(COMPLAINTS.rbio_office_code))}, or {@link #SCOPE_AGNOSTIC} for the
     * office-agnostic window. Width matched to the source column ({@code varchar(10)}).
     *
     * <p>Sentinelled for a COVERAGE reason where {@link #department} is sentinelled for a correctness
     * one: {@code rbio_office_code} is populated on 410 of 4,403 complaints, so keying on it as
     * REQUIRED would make this feature silent on 91% of the register. Both rows are therefore written —
     * agnostic always, office-specific where the office clears the floor on its own — and the read
     * prefers the specific one. MEASURED at the {@code >= 5} floor: 13 agnostic windows against 5
     * office-specific ones.
     */
    @Column(name = "OFFICE_CODE", nullable = false, length = 10)
    @Builder.Default
    private String officeCode = SCOPE_AGNOSTIC;

    /**
     * Complaints in this window that are NOT closed. The NUMERATOR, and the number the signal is about.
     *
     * <p>"Open" is the complement of {@code RBIO_STATUS_MASTER.IS_CLOSED='Y'}, resolved through
     * {@code RbioStatusVocabulary} rather than from a literal list — there were previously two hardcoded
     * copies of that vocabulary in this codebase and they disagreed, so a complaint closed by an award
     * counted as open to one of them. MEASURED on {@code cms_db}: eight statuses are closed
     * ({@code closed}, {@code resolved}, {@code rejected}, {@code withdrawn}, {@code adjudicated},
     * {@code conciliated}, {@code forwarded_external}, {@code forwarded_regulator}), leaving 927 of the
     * 4,112 rollup-eligible complaints open.
     */
    @Column(name = "OPEN_CASES", nullable = false)
    private Long openCases;

    /**
     * Complaints in this window, open or closed. The DENOMINATOR.
     *
     * <p>{@code nullable = false} because §5.1 names the failure directly — "a bare recommendation with
     * no denominator will be distrusted, correctly" — so the schema does not permit storing a count
     * without the thing that makes it readable. "14 open" and "14 of 16 open" are different claims, and
     * only the second one is checkable by the officer reading it.
     */
    @Column(name = "WINDOW_TOTAL", nullable = false)
    private Long windowTotal;

    /**
     * When the refresh that wrote this row ran.
     *
     * <p>Read by nothing in the request path, and load-bearing for two things that are not the request:
     * the stale sweep keys on it, and a rollup which quietly stopped refreshing is otherwise
     * undiagnosable — stale counts look exactly like correct counts.
     */
    @Column(name = "REFRESHED_AT", nullable = false)
    private LocalDateTime refreshedAt;

    /** True when this row is the ground-agnostic fallback rather than a ground-specific window. */
    @Transient
    public boolean isGroundAgnostic() {
        return groundKey == null || groundKey == GROUND_AGNOSTIC;
    }

    /** True when this row is the office-agnostic fallback rather than one office's window. */
    @Transient
    public boolean isOfficeAgnostic() {
        return officeCode == null || SCOPE_AGNOSTIC.equals(officeCode);
    }

    /** The open share of the window, 0..1. Derived, never stored — the two counts are the truth. */
    @Transient
    public double openShare() {
        if (windowTotal == null || windowTotal <= 0 || openCases == null) {
            return 0d;
        }
        return (double) openCases / (double) windowTotal;
    }
}
