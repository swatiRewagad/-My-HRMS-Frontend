package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One complaint, reduced to the keys a complainant's filing history is looked up by.
 *
 * <h2>What a row MEANS, said precisely</h2>
 * "Complaint {@link #complaintNumber} was filed at {@link #filedAt} by the complainant whose normalised
 * email is {@link #emailKey} and whose normalised phone is {@link #phoneKey}, against entity
 * {@link #entityKey}, in department {@link #department}, and it {@link #nonMaintainable} closed as
 * non-maintainable." Nothing more. No score, no judgement, no verdict about whether the complainant is
 * vexatious — that determination is the officer's and this row is one piece of the evidence for it.
 *
 * <h2>A PROJECTION, not a rollup — and that is a deliberate departure from its three siblings</h2>
 * {@link AssistanceNextAction}, {@link AssistanceClauseAffinity} and {@code AssistanceEntityPattern}
 * each store a {@code GROUP BY}'s output: one row per cohort, holding a numerator and a denominator.
 * This one stores one row per COMPLAINT, and the reason is the deliverable rather than taste.
 *
 * <p>The duplicate signal must NAME the earlier complaints — "3 earlier complaints against HDFC Bank in
 * the last 30 days" is not actionable unless the officer can open them — so the read's output is a LIST
 * OF COMPLAINT NUMBERS. A cohort row cannot hold a list, and a rollup keyed on
 * (complainant, entity, window) would need one row per possible as-of date, which is every date.
 *
 * <p>So the read is TWO INDEX SEEKS, one per contact key, each a leading-equality seek with a
 * {@link #filedAt} range, capped at {@link #MAX_HISTORY_ROWS} and unioned in Java. That is NOT a
 * request-path aggregate and the distinction is exact: no {@code GROUP BY}, no {@code COUNT(*)}, no
 * window function, no scan. It is the same shape {@code AssistanceClauseAffinityRepository}'s read
 * already has — that one seeks a cohort and sums and sorts at most 64 rows in Java — except keyed on a
 * complainant rather than a cohort. The measured bound is tight: the largest number of complaints held
 * by any one email in this register is 10, and by any one phone surviving the fan-out guard, 19.
 *
 * <p>The one genuine AGGREGATE the feature needs — the contact fan-out guard, a {@code COUNT(DISTINCT)}
 * over the whole register — runs in {@code AssistanceComplainantHistoryRefreshService} on a schedule and
 * never on request.
 *
 * <h2>PRIVACY IS ENFORCED HERE, BY THE ABSENCE OF COLUMNS</h2>
 * This is the most PII-sensitive read in the assistance set: it surfaces one person's OTHER complaints.
 * Two PII leaks have already had to be fixed in this repo. The rule is structural, not procedural:
 * <ul>
 *   <li>There is NO field for a complainant NAME, ADDRESS, ACCOUNT NUMBER or CARD NUMBER. Not masked —
 *       ABSENT. An entity cannot leak what it does not hold, and no future serialiser change can
 *       reintroduce what has no column.
 *   <li>There is NO field for the complaint SUBJECT or DESCRIPTION. The brief permits a snippet and this
 *       feature declines even that: a narrative snippet from someone's other complaint is the single
 *       most identifying thing that could appear on the screen, and the signal does not need it — a
 *       complaint number, a date and an entity already let the officer open the case properly, where
 *       the existing per-screen PII controls apply.
 *   <li>{@link #emailKey} and {@link #phoneKey} DO hold contact data, because they are the join keys and
 *       cannot be dropped. They are therefore NEVER SERIALISED: {@code DuplicateFilingResponse} has no
 *       field for either, and the read seeks BY them rather than returning them. The only email an
 *       officer sees is the one already on the complaint in front of them, which this feature did not
 *       give them.
 * </ul>
 *
 * <h2>THE ENTITY AND THE MIGRATION MUST MATCH CHARACTER FOR CHARACTER</h2>
 * Dev runs {@code ddl-auto: update} and production runs {@code ddl-auto: validate}, so a
 * {@code @Table} or {@code @Column} name that differs from the migration's {@code CREATE TABLE} works
 * silently in dev and FAILS PRODUCTION BOOT. Every name below is the migration's own spelling:
 * {@code ASSISTANCE_COMPLAINANT_HISTORY}, {@code UK_ACH_COMPLAINT}, {@code IDX_ACH_EMAIL},
 * {@code IDX_ACH_PHONE} and the ten columns, from
 * {@code database/V121__assistance_complainant_history.sql} and its Oracle twin
 * {@code database/oracle/V119__assistance_complainant_history.sql}.
 *
 * <p>The two {@code @Index} declarations are present for the same reason: under {@code ddl-auto: update}
 * Hibernate creates the indexes it is told about, so declaring them here is what keeps a dev schema
 * identical to the one the migration builds. Without them dev would run index-less and the read's plan
 * would be measured on a schema production does not have.
 *
 * <h2>{@link #nonMaintainable} is a {@code String}, and that is not an oversight</h2>
 * {@code 'Y'} / {@code 'N'} against {@code VARCHAR(1)} / {@code VARCHAR2(1)}. A {@code Boolean} would be
 * inferred as {@code TINYINT(1)} by the MySQL dialect and {@code NUMBER(1)} by the Oracle one, which is
 * exactly the class of divergence {@code ddl-auto: validate} exists to catch and exactly the one that is
 * invisible in dev. A {@code String} against a character column has one unambiguous mapping on both
 * engines and needs no dialect to agree about anything.
 *
 * @see com.hrms.cms.service.AssistanceComplainantHistoryRefreshService the only writer
 * @see com.hrms.cms.service.DuplicateFilingDetectionService the only reader
 */
@Entity
@Table(name = "ASSISTANCE_COMPLAINANT_HISTORY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ACH_COMPLAINT",
                columnNames = {"COMPLAINT_NUMBER"}),
        indexes = {
                @Index(name = "IDX_ACH_EMAIL", columnList = "EMAIL_KEY, FILED_AT"),
                @Index(name = "IDX_ACH_PHONE", columnList = "PHONE_KEY, FILED_AT")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistanceComplainantHistory {

    /**
     * The "this row has no value for this key" sentinel, for every text key column.
     *
     * <p>A SENTINEL and never NULL, for two independent reasons that both bite. {@code = null} is never
     * true, so a nullable key column makes its rows unreachable by any seek — a complaint with no email
     * would be invisible to the {@link #phoneKey} read that should have found it. And a composite or
     * unique key containing a NULL is not enforced across those rows on either MySQL or Oracle.
     *
     * <p>{@code '*'} rather than {@code ''} additionally because on ORACLE an empty string IS NULL, so
     * {@code ''} is not even available as an alternative — and because {@code ''} is what the dirty
     * source columns already use for "absent" on 21 rows. Reusing it would group every one of those
     * complainants together as one person, which is the precise defect
     * {@code ComplaintRepository.countOtherComplaintsByComplainantEmail} documents its own blank guard
     * against.
     *
     * <p>It cannot collide with real data: {@code '*'} is not a valid email local part, not a digit, and
     * {@code AssistanceEntityAliasNormaliser.normalise} returns {@code null} rather than {@code '*'} for
     * a blank entity code, so the sentinel is only ever written by this feature's own normalisation.
     */
    public static final String KEY_ABSENT = "*";

    /** {@link #nonMaintainable} when the complaint was closed as non-maintainable. */
    public static final String FLAG_YES = "Y";

    /** {@link #nonMaintainable} otherwise — including every complaint that is not closed at all. */
    public static final String FLAG_NO = "N";

    /**
     * Row cap on ONE contact key's history read.
     *
     * <p>200. MEASURED headroom rather than a round number: the largest history behind a single email in
     * this register is 10 complaints, and behind a single phone that survives the fan-out guard, 19. 200
     * is an order of magnitude above the worst real case, so it is not a limit the feature operates
     * near — which matters, because a cap that truncates would make the DENOMINATOR wrong rather than
     * merely incomplete, and the denominator is the number {@code §5.1} says an officer must be able to
     * trust. "8 lifetime filings" computed from a truncated page would be a lie, not a short answer.
     *
     * <p>{@code §6.2} requires a DECLARED cap on every query, and the reader WARNS when a read comes back
     * full rather than silently reporting the truncated count — see
     * {@code DuplicateFilingDetectionService}. A read at the cap means either the fan-out guard has a
     * hole or a genuinely extraordinary complainant exists, and both are things an operator should see
     * rather than infer from an odd-looking total months later.
     */
    public static final int MAX_HISTORY_ROWS = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The complaint this row projects. The idempotency key, via {@code UK_ACH_COMPLAINT}.
     *
     * <p>The complaint NUMBER and not its id, because the number is what the read returns to the officer
     * and what every other assistance endpoint keys on. Carrying the id as well would be a second
     * identifier for one row with no reader.
     *
     * <p>{@code length = 50} matches {@code COMPLAINTS.complaint_number varchar(50)}, so a value cannot
     * be truncated on the way in and then fail to match the complaint it came from.
     */
    @Column(name = "COMPLAINT_NUMBER", nullable = false, length = 50)
    private String complaintNumber;

    /**
     * The complainant's email, lower-cased and trimmed on write, or {@link #KEY_ABSENT}.
     *
     * <p>NORMALISED ON WRITE, never folded on read, per {@code §6.2}. The cross-engine reason is
     * specific: MySQL's {@code utf8mb4_unicode_ci} collation folds case for free while Oracle's default
     * collation does not, so an address stored verbatim and compared verbatim would match in dev and
     * MISS in production. Normalising on write also means the read never needs {@code LOWER(column)},
     * which would defeat {@code IDX_ACH_EMAIL} on both engines.
     *
     * <p>Carries {@link #KEY_ABSENT} when the complaint has no email AND when the fan-out guard measured
     * this address as shared — see {@code AssistanceComplainantHistoryRefreshService.MAX_CONTACT_FANOUT}.
     *
     * <p>A JOIN KEY AND NEVER SERIALISED. {@code length = 200} matches
     * {@code COMPLAINTS.complainant_email varchar(200)}.
     */
    @Column(name = "EMAIL_KEY", nullable = false, length = 200)
    @Builder.Default
    private String emailKey = KEY_ABSENT;

    /**
     * The LAST 10 DIGITS of the complainant's phone, non-digits stripped, or {@link #KEY_ABSENT}.
     *
     * <p>The TAIL rather than the number as filed, so {@code '+91 98765-43210'}, {@code '09876543210'}
     * and {@code '9876543210'} are one identity. MEASURED: 0 of 4,354 non-blank phones hold fewer than
     * 10 digits, so the tail is never a partial number that could be padded against a different
     * person's. It is also a privacy property — any country code or separator the complainant typed is
     * dropped and never stored.
     *
     * <p>THE FAN-OUT GUARD WRITES {@link #KEY_ABSENT} HERE, and it is the single most load-bearing
     * decision in this feature. {@code complainant_phone} is poisoned by placeholders: {@code
     * 9876543210} appears on 3,527 complaints across 3,427 DISTINCT EMAILS. A value shared by 3,427
     * people is not an identity, and matching on it unguarded flags 3,849 of 4,403 complaints (87%) as
     * duplicate filings — against 259 (5.9%) with the guard applied. See the migration header.
     *
     * <p>A JOIN KEY AND NEVER SERIALISED. {@code length = 20} matches
     * {@code COMPLAINTS.complainant_phone varchar(20)} — wider than the 10 digits ever stored here,
     * deliberately, so the column can never be the reason a future normalisation has to migrate.
     */
    @Column(name = "PHONE_KEY", nullable = false, length = 20)
    @Builder.Default
    private String phoneKey = KEY_ABSENT;

    /**
     * The regulated entity complained against, canonicalised, or {@link #KEY_ABSENT}.
     *
     * <p>Resolved through {@code AssistanceEntityAliasNormaliser}, which ALREADY EXISTS and is REUSED
     * rather than reimplemented — it is the same normaliser {@code AssistanceEntityPatternRefreshService}
     * and {@code AssistanceRailService} put on both sides of the entity-pattern rollup.
     * {@code entity_code} is documented dirty: the register spells one bank {@code 'PNB'} and
     * {@code 'Punjab National Bank'}, and {@code UPPER()} merges neither with the other because they
     * share no character position. The normaliser is an explicit ALIAS TABLE (33 distinct spellings down
     * to 24) and is deliberately NOT fuzzy — an edit-distance match on a bank name merges two different
     * banks on a bad day, and "you have already complained about this entity" said of the WRONG entity
     * is worse than silence.
     *
     * <p>BOTH SIDES go through it: written here by the refresh, and applied to the subject complaint's
     * own {@code entity_code} on the read before seeking. If only one side did, this table would hold
     * PNB's history under one key and be asked for it under another — a WRONG count rather than a
     * missing one, because what came back would be one alias's partial tally presented as the entity's
     * total.
     *
     * <p>{@code length = 50} matches both {@code COMPLAINTS.entity_code varchar(50)} and
     * {@code AssistanceEntityAliasNormaliser.MAX_LENGTH}, which is what guarantees a canonical name can
     * never be truncated into a different key than the one the normaliser returned.
     */
    @Column(name = "ENTITY_KEY", nullable = false, length = 50)
    @Builder.Default
    private String entityKey = KEY_ABSENT;

    /**
     * THE SCOPE COLUMN. {@code CEPC} / {@code RBIO} / {@code CRPC}, upper-cased, or {@link #KEY_ABSENT}.
     *
     * <p>The tenancy boundary, not decoration. {@code DuplicateFilingDetectionService} filters every
     * candidate row against the departments the CALLER's resolved roles are responsible for, and FAILS
     * CLOSED: a caller whose roles map to no department receives an empty signal, never an unscoped one.
     *
     * <p>A {@link #KEY_ABSENT} row is out of scope for EVERY caller. MEASURED: 23 of 4,403 complaints
     * carry no department. A complaint whose department is unknown cannot be PROVEN to be in anyone's
     * scope, and the safe reading of an unprovable scope on a PII-bearing read is exclusion — the
     * alternative, showing it to everyone, is how a cross-department leak is introduced by a NULL.
     *
     * <p>Upper-cased on write for the {@link #emailKey} reason: the read compares it against values
     * derived from role names, and a verbatim comparison would fold in dev and not in production.
     *
     * <p>{@code length = 20} matches {@code COMPLAINTS.department varchar(20)}.
     */
    @Column(name = "DEPARTMENT", nullable = false, length = 20)
    @Builder.Default
    private String department = KEY_ABSENT;

    /**
     * When the complaint was filed: {@code COALESCE(filed_at, created_at)}.
     *
     * <p>The coalesce is what makes the window reliable rather than a tidiness: {@code created_at} is
     * populated on all 4,403 rows and {@code filed_at} is not, while {@code filed_at} is the more
     * correct answer where it exists. {@code Complaint}'s {@code @PrePersist} sets both, so they agree
     * on everything written since.
     *
     * <p>{@code nullable = false} and the range-and-sort column of both read indexes. A NULL would make
     * a complaint invisible to the duplicate WINDOW while still counting toward the LIFETIME total —
     * a numerator and a denominator that disagree about the same history, which is the one number
     * {@code §5.1} says an officer must be able to trust.
     */
    @Column(name = "FILED_AT", nullable = false)
    private LocalDateTime filedAt;

    /**
     * The complaint's status, verbatim from {@code COMPLAINTS.status}, or {@link #KEY_ABSENT}.
     *
     * <p>NOT case-folded, unlike {@link #emailKey} and {@link #department}, and the asymmetry is
     * deliberate: this column is DISPLAYED and never compared — the feature issues no predicate on it.
     * The register genuinely mixes cases ({@code 'pending'} beside {@code 'NOT_OPENED'}) and folding it
     * would show an officer a status spelled differently from the one on the complaint grid beside it,
     * which reads as a disagreement between two parts of the same screen.
     *
     * <p>{@code length = 30} matches {@code COMPLAINTS.status varchar(30)}.
     */
    @Column(name = "STATUS", nullable = false, length = 30)
    @Builder.Default
    private String status = KEY_ABSENT;

    /**
     * {@link #FLAG_YES} / {@link #FLAG_NO} — whether this complaint closed as NON-MAINTAINABLE.
     *
     * <p>The {@code 16(2)(b)} evidence, and the reason the repeat-complainant signal is evidence rather
     * than an accusation: an officer establishing "frivolous or vexatious" needs to know not just how
     * often this person has filed but how those filings were DISPOSED of, and today assembles that by
     * hand.
     *
     * <p>Set from THREE markers OR'd together, because no single column answers it:
     * {@code maintainability_determination = 'NON_MAINTAINABLE'}, {@code closure_cause =
     * 'NON_MAINTAINABLE'}, or a {@code closure_clause} under {@code 16(2)}.
     *
     * <p>MEASURED, and this is the honest weakness of the whole feature: the register holds 47
     * non-maintainable rows by ANY of those three markers, and only 6 of them belong to a complainant
     * with more than one filing. So this flag is {@link #FLAG_NO} on essentially every row, and the
     * signal reports a lifetime FILING count that is real beside a non-maintainable count that is almost
     * always zero. Nobody should read "0 of 8 closed as non-maintainable" as evidence AGAINST a
     * {@code 16(2)(b)} determination on this register — it is evidence that the determination is not
     * being recorded. Carried anyway for the {@link AssistanceClauseAffinity#groundKey} reason: it costs
     * nothing until it populates, at which point the signal sharpens with no migration.
     *
     * <p>A {@code String} and not a {@code Boolean} — see the class javadoc. The dialects disagree about
     * how to infer a boolean column and {@code ddl-auto: validate} is where that disagreement surfaces,
     * in production, having been invisible in dev.
     */
    @Column(name = "NON_MAINTAINABLE", nullable = false, length = 1)
    @Builder.Default
    private String nonMaintainable = FLAG_NO;

    /**
     * When the refresh that wrote this row ran.
     *
     * <p>Read by nothing in the request path. It exists so a projection that quietly stopped refreshing
     * is diagnosable, which is the one failure a scheduled job has that an endpoint does not: stale rows
     * look exactly like correct rows. It is also the stale sweep's only predicate — the job stamps every
     * row of a pass with ONE timestamp taken at the start, so "older than this run" is exactly "not
     * rewritten by this run".
     */
    @Column(name = "REFRESHED_AT", nullable = false)
    private LocalDateTime refreshedAt;

    /** Whether this row closed as non-maintainable, as a boolean, without exposing the storage form. */
    @Transient
    public boolean isNonMaintainableClosure() {
        return FLAG_YES.equals(nonMaintainable);
    }

    /** Whether this row carries a real entity rather than the sentinel. */
    @Transient
    public boolean hasEntity() {
        return entityKey != null && !KEY_ABSENT.equals(entityKey);
    }
}
