package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINTS", indexes = {
    @Index(name = "idx_complaint_number", columnList = "complaintNumber", unique = true),
    @Index(name = "idx_complaint_status", columnList = "status"),
    @Index(name = "idx_complaint_priority", columnList = "priority"),
    @Index(name = "idx_complaint_email", columnList = "complainantEmail"),
    @Index(name = "idx_complaint_category", columnList = "CATEGORY_ID"),
    @Index(name = "idx_complaint_bank", columnList = "BANK_ID"),
    @Index(name = "idx_complaint_created", columnList = "createdAt"),
    @Index(name = "idx_complaint_status_created", columnList = "status,createdAt"),
    @Index(name = "idx_complaint_re_date", columnList = "reComplaintDate"),
    @Index(name = "idx_complaint_triage", columnList = "triageSignal"),
    @Index(name = "idx_complaint_maintainability", columnList = "maintainabilityDetermination"),
    @Index(name = "idx_complaint_award", columnList = "awardAmount")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String complaintNumber;

    @Column(nullable = false, length = 200)
    private String complainantName;

    @Column(length = 200)
    private String complainantEmail;

    @Column(length = 20)
    private String complainantPhone;

    @Column(length = 500)
    private String complainantAddress;

    @Column(length = 100)
    private String complainantState;

    @Column(length = 100)
    private String complainantDistrict;

    @Column(name = "BANK_ID")
    private Long bankId;

    @Column(length = 300)
    private String bankBranch;

    @Column(length = 100)
    private String accountNumber;

    @Column(name = "CATEGORY_ID")
    private Long categoryId;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String reliefSought;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(length = 20)
    private String priority;

    @Column(length = 50)
    private String filingType;

    @Column(length = 200)
    private String bankComplaintReference;

    private LocalDateTime bankComplaintDate;

    @Column(length = 200)
    private String assignedOfficer;

    @Column(length = 20)
    private String department;

    @Column(length = 50)
    private String assignedRole;

    @Column(length = 50)
    private String entityCode;

    @Column(length = 50)
    private String workflowStage;

    /**
     * The coarse phase a citizen is told about ("your complaint is in Conciliation"), as opposed to
     * {@code status}/{@code workflowStage}, which are the fine-grained internal state. FK to
     * RBIO_MILESTONE_MASTER (V58/V56).
     *
     * <p>NULLABLE, and null on every pre-existing row. A milestone is derived from the transition that
     * last moved the complaint, and back-filling one for historical records would mean inventing which
     * phase a closed complaint was in — a guess presented to a citizen as fact.
     */
    @Column(name = "milestone", length = 30)
    private String milestone;

    /**
     * Optimistic-lock version, backing the UST675 record-locking UX.
     *
     * <p>Two officers opening the same complaint and saving in turn previously produced a silent
     * last-write-wins: the second save overwrote the first officer's decision with no error and no
     * record that anything was lost. With this column the second save fails and the API answers
     * HTTP 409 (see {@code GlobalExceptionHandler}) so the UI can tell the officer to reload rather
     * than discarding their colleague's work.
     *
     * <p><b>Primitive {@code long}, not {@code Long}, and that is load-bearing.</b> Every pre-existing row
     * has {@code record_version = NULL}, because the column is added nullable (a NOT NULL default would
     * be permanent for every other session on this shared database). Hibernate does NOT lazily initialise
     * a null wrapper version: it reads null and then increments it, so the first write to ANY legacy
     * complaint failed with {@code Cannot invoke "java.lang.Long.longValue()" because "current" is null} —
     * i.e. adding optimistic locking broke every RBIO workflow action on existing data. Verified against a
     * live database, not theorised.
     *
     * <p>A primitive defaults to 0 when the column is null, so legacy rows enter versioning at 0 on their
     * first write and no backfill UPDATE is needed. V58/V56 additionally default the column to 0 for rows
     * created after the migration.
     */
    @Version
    @Column(name = "record_version", nullable = false)
    @Builder.Default
    private long recordVersion = 0L;

    /**
     * Originating RBIO office, resolved from OFFICE_CODE_MASTER (V36/V34).
     *
     * The office was previously only recoverable by substringing the complaint number
     * (N{FY:6}{office:3}{seq:6}), which is unindexable and mis-parses the legacy 'CMP-'/'CMS-DEMO-'
     * formats. NULL is a legitimate value: legacy numbers carry no office at all, and a guessed office
     * would be a guessed territorial jurisdiction on a citizen's complaint.
     */
    @Column(name = "rbio_office_code", length = 10)
    private String rbioOfficeCode;

    /** FK to GROUND_OF_COMPLAINT_MASTER (V36/V34). Held as an id, matching categoryId/bankId here. */
    @Column(name = "ground_of_complaint_id")
    private Long groundOfComplaintId;

    // Prior RE complaint details (RB-IOS Q16/Q17/Q18)
    private Boolean priorReComplaint;

    private LocalDate reComplaintDate;

    @Column(length = 200)
    private String reComplaintReference;

    private Boolean reRepliedAndDissatisfied;

    /**
     * The date the RE's reply reached the complainant (RB-IOS Q18 follow-up).
     *
     * The filing window runs from THIS date once the RE has replied, so it has to be stored: the wizard
     * collected it, validated it, and then dropped it, leaving the server unable to tell a timely filing
     * from a stale one. Null where the RE never replied — that case is governed by reComplaintDate and
     * the grievance filing window instead.
     */
    private LocalDate reReplyDate;

    /**
     * UST5: the step-5 declaration accepted at filing time. Null for intake channels that never
     * showed a checkbox (email, physical letter, walk-in), so absence is not a compliance gap.
     */
    private Boolean declarationAccepted;

    // Maintainability triage (Phase 2/5)
    @Column(length = 10)
    private String triageSignal;

    @Column(columnDefinition = "TEXT")
    private String triageFlags;

    @Column(columnDefinition = "TEXT")
    private String eligibilityTimeline;

    @Column(length = 30)
    private String maintainabilityDetermination;

    @Column(length = 200)
    private String maintainabilityDeterminedBy;

    private LocalDateTime maintainabilityDeterminedAt;

    /**
     * The Deputy Ombudsman's decision on a complaint within delegated authority — FACILITATION or
     * REJECTION (UST483-484).
     *
     * <p>Distinct from {@code maintainabilityDetermination}: a complaint can be maintainable AND rejected
     * on its merits, so collapsing the two would make those two outcomes indistinguishable.
     *
     * <p>Nullable, like every column added on this shared database: ddl-auto never drops a column, so a
     * NOT NULL here would be permanent for all sessions and would break their inserts.
     */
    @Column(name = "deputy_decision", length = 30)
    private String deputyDecision;

    /** The regulatory body a complaint outside RBI's remit was forwarded to (UST FORWARD_TO_REGULATORY_BODY). */
    @Column(name = "regulatory_body_name", length = 200)
    private String regulatoryBodyName;

    /**
     * The RBI department a complaint was forwarded to (UST761, 534, 527-528).
     *
     * <p>Its own column rather than {@code assignedOfficer}, which is what the CEPC forward arms use — that
     * leaves a department NAME in a user column, so the complaint appears to be owned by a department and
     * "who is working on this" has no answer. Nullable, like every column added on this shared database.
     */
    @Column(name = "forwarded_to_department", length = 100)
    private String forwardedToDepartment;

    /** When the advisory issued under this complaint was confirmed complied with (UST535-538). */
    @Column(name = "advisory_complied_at")
    private LocalDateTime advisoryCompliedAt;

    @Column(precision = 15, scale = 2)
    private BigDecimal awardAmount;

    // ═══ SLA fields ═══
    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    @Column(name = "sla_priority", length = 10)
    private String slaPriority;

    // ═══ Conciliation/Adjudication fields (CEPC) ═══
    @Column(name = "conciliation_date")
    private LocalDateTime conciliationDate;

    @Column(name = "conciliation_outcome", length = 100)
    private String conciliationOutcome;

    @Column(name = "adjudication_date")
    private LocalDateTime adjudicationDate;

    @Column(name = "adjudication_outcome", length = 100)
    private String adjudicationOutcome;

    // ═══ Closure tracking ═══
    @Column(name = "closure_cause", length = 50)
    private String closureCause;

    @Column(name = "custom_closure_text", length = 2000)
    private String customClosureText;

    @Column(name = "closure_letter_sent_at")
    private LocalDateTime closureLetterSentAt;

    /**
     * Date of Sending of the closure letter (UST507-509, 757, 763).
     *
     * <p>The closure screen collected and POSTed this from the outset, but the server never read it —
     * {@code dateOfSending} appeared nowhere in cms-backend — so it was silently dropped on every closure.
     * This is the date that evidences when the complainant was informed, so it has to be durable.
     *
     * <p>Nullable because this runs on a shared database under {@code ddl-auto: update} where closed
     * complaints already exist, and because the requirement is armed by configuration rather than applied
     * retroactively. UST757/763 require it to be non-editable once recorded, so
     * {@code RbioWorkflowService} writes it only when it is currently null.
     */
    @Column(name = "date_of_sending")
    private java.time.LocalDate dateOfSending;

    /**
     * When the Award Passed status was recorded (UST543).
     *
     * <p>Distinct from {@link #adjudicationDate}, which is shared with award REJECTION and so cannot answer
     * "when was an award passed" without also matching rejections. The implemented/not-implemented/lapse
     * dates below hang off this one for reporting.
     */
    @Column(name = "award_passed_date")
    private java.time.LocalDate awardPassedDate;

    /** When the entity confirmed it had implemented the award (UST543). */
    @Column(name = "award_implemented_date")
    private java.time.LocalDate awardImplementedDate;

    /** When the award was recorded as not implemented (UST543). */
    @Column(name = "award_not_implemented_date")
    private java.time.LocalDate awardNotImplementedDate;

    /** When the award lapsed (UST543). */
    @Column(name = "award_lapse_date")
    private java.time.LocalDate awardLapseDate;

    @Column(name = "closure_clause", length = 100)
    private String closureClause;

    @Column(name = "closure_authority_name", length = 200)
    private String closureAuthorityName;

    @Column(name = "closure_authority_designation", length = 200)
    private String closureAuthorityDesignation;

    // ═══ Withdrawal fields ═══
    @Column(name = "withdrawal_reason", length = 500)
    private String withdrawalReason;

    @Column(name = "withdrawal_date")
    private LocalDateTime withdrawalDate;

    @Column(name = "withdrawn_by", length = 200)
    private String withdrawnBy;

    // ═══ Reopen tracking ═══
    @Column(name = "reopen_count")
    @Builder.Default
    private Integer reopenCount = 0;

    @Column(name = "last_reopened_at")
    private LocalDateTime lastReopenedAt;

    /**
     * Explicit reopen timestamp (V31/V29). Distinct from lastReopenedAt, which only the CEPC/RBIO
     * reopen paths maintain; this column exists so the AA parent search can order and filter on
     * reopening without depending on which module performed it.
     */
    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    /**
     * Why the complaint was reopened — one of the configured reasons (UST551).
     *
     * <p>The permitted vocabulary lives in SYSTEM_CONFIG under {@code cms.rbio.reopen.reasons}, not in an
     * enum here, so adding a reason is configuration rather than a release.
     */
    @Column(name = "reopen_reason", length = 50)
    private String reopenReason;

    /**
     * The free-text justification accompanying the reason (UST551).
     *
     * <p>Mandatory in addition to the reason: "Court Order" alone does not say which order or why it
     * requires reopening, and a reopen reverses a concluded statutory proceeding.
     */
    @Column(name = "reopen_justification", columnDefinition = "TEXT")
    private String reopenJustification;

    // ═══ RBIO-specific fields ═══
    @Column(name = "advisory_text", columnDefinition = "TEXT")
    private String advisoryText;

    @Column(name = "advisory_issued_at")
    private LocalDateTime advisoryIssuedAt;

    @Column(name = "notice_13_1_issued_at")
    private LocalDateTime notice131IssuedAt;

    /**
     * Who the 13(1) notice was addressed to.
     *
     * <p>The impleading screen has always sent {@code targetParty} and the side effect never read it, so the
     * addressee of a statutory communication was discarded while the request answered 200. A notice whose
     * recipient is unrecorded cannot be evidenced later.
     */
    @Column(name = "notice_13_1_target_party", length = 250)
    private String notice131TargetParty;

    @Column(name = "impleaded_parties", length = 1000)
    private String impleadedParties;

    @Column(name = "compensation_type", length = 30)
    private String compensationType;

    @Column(name = "scheme_version", length = 20)
    private String schemeVersion;

    /**
     * UST473: the Scheme-coverage determination that decided this complaint's department.
     *
     * <p>COVERED, NOT_COVERED, AMBIGUOUS or UNKNOWN — see {@code MreEntityCoverageService.CoverageStatus}.
     * Recorded because the story requires the CHECK RESULT on the complaint, not merely implied by the
     * department: RBIO and CEPC are also reachable by manual transfer, so the department alone cannot tell
     * a reviewer whether a machine determined coverage or a human moved the file.
     *
     * <p>Deliberately NOT folded into {@code maintainabilityDetermination}. That column is a two-value
     * human decision (MAINTAINABLE / NON_MAINTAINABLE) read by Drools compensation-cap rules, so a third
     * value would silently fall out of every {@code == "MAINTAINABLE"} guard. It also carries a
     * {@code determinedBy} naming a person, whereas this is a server determination made before any officer
     * sees the file.
     *
     * <p>Nullable: the shared dev database runs ddl-auto=update, where NOT NULL would be permanent for
     * every other session and would break their inserts. NULL means the complaint predates this check.
     */
    @Column(name = "scheme_coverage_status", length = 20)
    private String schemeCoverageStatus;

    /** Why coverage resolved as it did — which entity matched, or why no single entity could be. */
    @Column(name = "scheme_coverage_reason", length = 500)
    private String schemeCoverageReason;

    @Column(name = "current_stage_deadline")
    private LocalDateTime currentStageDeadline;

    @Column(name = "stage_assigned_at")
    private LocalDateTime stageAssignedAt;

    // ═══ RE Response & Status Tracking ═══
    @Column(name = "re_response_deadline")
    private LocalDate reResponseDeadline;

    /**
     * UST637: whether the entity has missed its response deadline, decided on the SERVER.
     *
     * <p>Persisted rather than computed per request for two reasons. The complaint grid whitelists sortable
     * columns, so an overdue value derived in Java after the page is fetched could not be ordered by the
     * database — "show the overdue ones first" would silently not work. And a browser comparing two dates
     * can be wrong about the timezone or working from a stale page, which is not a basis for a statutory
     * window.
     *
     * <p>Maintained by the sweep, which CLEARS it as well as setting it, so the highlight disappears by
     * itself once the entity responds rather than needing a separate step somebody could forget.
     *
     * <p>{@code Boolean} rather than {@code boolean}: the column is nullable on a shared ddl-auto database,
     * and NULL legitimately means "never evaluated" on a complaint with no deadline.
     */
    @Column(name = "re_response_overdue")
    private Boolean reResponseOverdue;

    @Column(name = "last_status_change_date")
    private LocalDateTime lastStatusChangeDate;

    // ═══ RE Activity Status ladder (UST846, UST850) ═══
    // Distinct from `status` above: this is the entity's progress signal, not the regulatory state.
    // Written only through ReActivityStatusService so the forward-only rule and the paired timeline
    // entry cannot be bypassed by a stray setter call.
    @Enumerated(EnumType.STRING)
    @Column(name = "re_activity_status", length = 30)
    private ReActivityStatus reActivityStatus;

    @Column(name = "re_activity_changed_at")
    private LocalDateTime reActivityChangedAt;

    // The nudge threshold in force when the record ENTERED its current activity status, snapshotted
    // so that later editing of the SYSTEM_CONFIG default cannot retroactively make historical
    // records nudge-due, nor silently forgive ones already past due.
    @Column(name = "re_activity_nudge_days")
    private Integer reActivityNudgeDays;

    // Set when a nudge has been sent for the CURRENT status, cleared on every transition, so a
    // record that legitimately advances becomes nudgeable again at the next level.
    @Column(name = "re_activity_nudged_at")
    private LocalDateTime reActivityNudgedAt;

    // ═══ Authorised Representative (D7) ═══
    // The filing wizard validates nine representative fields as mandatory once the citizen answers
    // "yes" to hasAuthRep, so they must be persisted — an officer cannot correspond with, or verify
    // the authority of, a representative whose details were discarded at registration.
    @Column(name = "has_auth_rep")
    private Boolean hasAuthRep;

    @Column(name = "through_advocate")
    private Boolean throughAdvocate;

    @Column(name = "rep_name", length = 200)
    private String repName;

    @Column(name = "rep_phone", length = 20)
    private String repPhone;

    @Column(name = "rep_email", length = 254)
    private String repEmail;

    @Column(name = "rep_address", length = 500)
    private String repAddress;

    @Column(name = "rep_state", length = 100)
    private String repState;

    @Column(name = "rep_district", length = 100)
    private String repDistrict;

    @Column(name = "rep_city", length = 100)
    private String repCity;

    @Column(name = "rep_pincode", length = 10)
    private String repPincode;

    // ═══ Timestamps ═══
    private LocalDateTime filedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime escalatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.filedAt = LocalDateTime.now();
        this.lastStatusChangeDate = LocalDateTime.now();
        if (this.status == null) this.status = "pending";
        if (this.priority == null) this.priority = "medium";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
