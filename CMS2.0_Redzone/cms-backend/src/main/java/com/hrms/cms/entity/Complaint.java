package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINTS", indexes = {
    @Index(name = "idx_complaint_number", columnList = "complaintNumber", unique = true),
    @Index(name = "idx_complaint_case_id", columnList = "caseId"),
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

    // Nullable: a complaint closed as Non-Maintainable at eligibility screening gets a caseId
    // instead (FR-G-013), never a complaintNumber.
    @Column(unique = true, length = 50)
    private String complaintNumber;

    // Set only for Non-Maintainable complaints; the citizen-facing identifier in place of a
    // complaintNumber (FR-G-013).
    @Column(name = "case_id", length = 50)
    private String caseId;

    // The screening-question clause code (e.g. "10(1)(j)") that made this complaint Non-Maintainable.
    @Column(name = "non_maintainable_clause_code", length = 100)
    private String nonMaintainableClauseCode;

    // The complaint this one duplicates, recorded when the citizen was shown the duplicate warning and
    // chose to file anyway (UST87 AC5). Null for every complaint that raised no duplicate match.
    @Column(name = "duplicate_of_complaint_number", length = 50)
    private String duplicateOfComplaintNumber;

    // The 6-digit "Complaint Id" the CRPC draft had before it was approved and converted into
    // this Complaint record - kept so the same identifier stays visible to RBIO/CEPC officers
    // instead of them seeing this row's own unrelated internal id.
    @Column(length = 100)
    private String originDraftId;

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

    @Column(length = 20)
    private String complainantPincode;

    @Column(name = "BANK_ID")
    private Long bankId;

    @Column(length = 300)
    private String entityName;

    @Column(length = 100)
    private String entityType;

    @Column(precision = 15, scale = 2)
    private BigDecimal amountInvolved;

    // Entity/branch detail fields carried over from the CRPC draft (email_drafts has the full
    // set; Complaint previously only tracked entityName/entityCode/bankBranch, so this data was
    // silently dropped on approval and RBIO officers never saw it).
    @Column(length = 100)
    private String entityCategory;

    @Column(length = 50)
    private String entityBsrCode;

    @Column(length = 20)
    private String entityPincode;

    @Column(length = 100)
    private String entityState;

    @Column(length = 100)
    private String entityDistrict;

    @Column(length = 100)
    private String entityCity;

    @Column(length = 300)
    private String entityBranchName;

    @Column(length = 100)
    private String entityBranchCategory;

    @Column(length = 500)
    private String entityAddress;

    @Column(length = 50)
    private String cosmosCode;

    @Column(length = 300)
    private String bankBranch;

    @Column(length = 100)
    private String accountNumber;

    @Column(name = "CATEGORY_ID")
    private Long categoryId;

    @Column(length = 200)
    private String categoryName;

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

    /**
     * Display copy of the value otherwise reachable only through {@code assignedOfficer}. Held on the row
     * because the dashboard grid sorts and column-filters on the name the officer reads, which a join to
     * the master cannot do without making every listing query fan out. {@code entityName} and
     * {@code categoryName} above are the same pattern.
     */
    @Column(name = "assigned_officer_name", length = 250)
    private String assignedOfficerName;

    /**
     * The office whose jurisdiction the complaint sits in, and the mandatory scope on every CEPC listing
     * request alongside {@code department}.
     *
     * <p>Distinct from {@link #rbioOfficeCode}, which is the RBIO intake office resolved from the complaint
     * number: that is NULL on every CEPC complaint, so it cannot carry CEPC scoping.
     */
    @Column(name = "regional_office", length = 100)
    private String regionalOffice;

    /**
     * Whether the complaint has been opened at all.
     *
     * <p>Not the dashboard's "unread" filter, which is deliberately per user and answered by
     * {@code COMPLAINT_READ_STATE} — the same grid shows a different unread count to each officer, and one
     * boolean on the shared row cannot express that.
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "has_attachment", nullable = false)
    @Builder.Default
    private Boolean hasAttachment = false;

    @Column(name = "created_by", length = 200)
    private String createdBy;

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

    @Column(precision = 15, scale = 2)
    private BigDecimal awardAmount;

    // ═══ SLA fields ═══
    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    @Column(name = "sla_priority", length = 10)
    private String slaPriority;

    /**
     * When the SLA-breach sweep raised an escalation for this complaint, and the sweep's once-only
     * marker (see {@link com.hrms.cms.service.SlaBreachEscalationService}).
     *
     * <p><b>Deliberately NOT {@link #escalatedAt}.</b> That column is the MANUAL/workflow escalation
     * stamp, written by four existing callers — {@code WorkflowController:824},
     * {@code CepcWorkflowService:449}, {@code ComplaintService:306} and
     * {@code RbioWorkflowService:979}. Reusing it would break both directions: a complaint an officer
     * had already escalated by hand would be permanently invisible to the breach sweep even when it
     * later blew its deadline, and the sweep's own writes would be indistinguishable from an
     * officer's action everywhere {@code escalatedAt} is surfaced (e.g.
     * {@code PastComplaintService:248}). The two facts are different facts, so they get different
     * columns.
     *
     * <p>Column name must match {@code database/V122__sla_breach_escalation_marker.sql} and its
     * Oracle twin {@code database/oracle/V120__...} EXACTLY: dev runs {@code ddl-auto: update} and
     * would silently add whatever this says, but prod runs {@code validate} and refuses to boot on a
     * mismatch.
     */
    @Column(name = "sla_breach_escalated_at")
    private LocalDateTime slaBreachEscalatedAt;

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

    @Column(name = "closure_clause", length = 100)
    private String closureClause;

    @Column(name = "proposed_action", length = 100)
    private String proposedAction;

    @Column(name = "proposed_clause", length = 100)
    private String proposedClause;

    @Column(name = "forwarded_office_code", length = 10)
    private String forwardedOfficeCode;

    @Column(name = "pre_forward_officer", length = 200)
    private String preForwardOfficer;

    @Column(name = "pre_forward_role", length = 50)
    private String preForwardRole;

    @Column(name = "closure_clause_description", columnDefinition = "TEXT")
    private String closureClauseDescription;

    @Column(name = "complaint_status_on_portal", length = 100)
    private String complaintStatusOnPortal;

    @Column(name = "speaking_order_generated", length = 10)
    private String speakingOrderGenerated;

    @Column(name = "gist_of_case", columnDefinition = "TEXT")
    private String gistOfCase;

    @Column(name = "gist_of_case_regional", columnDefinition = "TEXT")
    private String gistOfCaseRegional;

    @Column(name = "closure_authority_name", length = 200)
    private String closureAuthorityName;

    @Column(name = "closure_authority_designation", length = 200)
    private String closureAuthorityDesignation;

    // ═══ Reopen tracking ═══
    @Column(name = "reopen_count")
    @Builder.Default
    private Integer reopenCount = 0;

    @Column(name = "last_reopened_at")
    private LocalDateTime lastReopenedAt;

    // ═══ RBIO-specific fields ═══
    @Column(name = "advisory_text", columnDefinition = "TEXT")
    private String advisoryText;

    @Column(name = "advisory_issued_at")
    private LocalDateTime advisoryIssuedAt;

    @Column(name = "notice_13_1_issued_at")
    private LocalDateTime notice131IssuedAt;

    @Column(name = "impleaded_parties", length = 1000)
    private String impleadedParties;

    @Column(name = "compensation_type", length = 30)
    private String compensationType;

    @Column(name = "scheme_version", length = 20)
    private String schemeVersion;

    @Column(name = "current_stage_deadline")
    private LocalDateTime currentStageDeadline;

    @Column(name = "stage_assigned_at")
    private LocalDateTime stageAssignedAt;

    // ═══ RE Response & Status Tracking ═══
    @Column(name = "re_response_deadline")
    private LocalDate reResponseDeadline;

    @Column(name = "last_status_change_date")
    private LocalDateTime lastStatusChangeDate;

    // ═══ RBIO office routing ═══
    @Column(name = "date_of_sending")
    private LocalDate dateOfSending;

    @Column(name = "award_passed_date")
    private LocalDate awardPassedDate;

    @Column(name = "award_implemented_date")
    private LocalDate awardImplementedDate;

    @Column(name = "notice_13_1_target_party", length = 200)
    private String notice131TargetParty;

    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;

    // ═══ Withdrawal tracking (V8) ═══
    @Column(name = "withdrawal_date")
    private LocalDateTime withdrawalDate;

    @Column(name = "withdrawn_by", length = 200)
    private String withdrawnBy;

    @Column(name = "withdrawal_reason", length = 500)
    private String withdrawalReason;

    @Column(name = "reopen_justification", columnDefinition = "TEXT")
    private String reopenJustification;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    @Column(name = "deputy_decision", length = 50)
    private String deputyDecision;

    @Column(name = "advisory_complied_at")
    private LocalDateTime advisoryCompliedAt;

    @Column(name = "regulatory_body_name", length = 200)
    private String regulatoryBodyName;

    @Column(name = "forwarded_to_department", length = 50)
    private String forwardedToDepartment;

    // ═══ Authorised representative ═══
    // Held as columns rather than discarded at registration: a complaint can be filed through, and
    // the authority of, a representative whose details were discarded at registration.
    @Column(name = "has_auth_rep")
    private Boolean hasAuthRep;

    @Column(name = "through_advocate")
    private Boolean throughAdvocate;

    @Column(name = "rep_address", length = 500)
    private String repAddress;

    @Column(name = "rep_city", length = 100)
    private String repCity;

    @Column(name = "rep_district", length = 100)
    private String repDistrict;

    @Column(name = "rep_state", length = 100)
    private String repState;

    @Column(name = "rep_pincode", length = 10)
    private String repPincode;

    // Snapshot of the wizard answers as submitted. The normalised columns above only cover the
    // subset the workflow reads; the review screen has to show every question the citizen actually
    // answered, and those (eligibility Q&A, per-question sub-answers, the amount breakdown) have no
    // column of their own. Kept as JSON for the same reason COMPLAINT_DRAFTS does.
    @Column(columnDefinition = "TEXT")
    private String eligibilityAnswersJson;

    @Column(columnDefinition = "TEXT")
    private String wizardFormDataJson;

    // ═══ Authorized Representative ═══
    @Column(name = "rep_name", length = 200)
    private String repName;

    @Column(name = "rep_email", length = 200)
    private String repEmail;

    @Column(name = "rep_phone", length = 20)
    private String repPhone;

    // ═══ RE Activity Tracking ═══
    @Enumerated(EnumType.STRING)
    @Column(name = "re_activity_status", length = 40)
    private ReActivityStatus reActivityStatus;

    @Column(name = "re_activity_changed_at")
    private LocalDateTime reActivityChangedAt;

    @Column(name = "scheme_coverage_status", length = 30)
    private String schemeCoverageStatus;

    @Column(name = "scheme_coverage_reason", length = 500)
    private String schemeCoverageReason;

    @Column(name = "re_activity_nudge_days")
    @Builder.Default
    private Integer reActivityNudgeDays = 0;

    @Column(name = "re_activity_nudged_at")
    private LocalDateTime reActivityNudgedAt;

    @Column(name = "re_response_overdue")
    @Builder.Default
    private Boolean reResponseOverdue = false;

    // ═══ Timestamps ═══
    private LocalDateTime filedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime escalatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Defaults the audit timestamps, and only defaults them.
     *
     * <p>These four assignments used to be unconditional, which made a caller-supplied date
     * unwritable: the setter ran, {@code @PrePersist} then overwrote it, and the row landed with the
     * insert time. Nothing failed, so the loss was silent.
     *
     * <p>It had already cost us real data. {@code DemoDataSeeder} spreads its 60 complaints over 90
     * days on purpose; every row persisted inside the same 122 ms instead. Because those rows keep the
     * {@code closed_at} the seeder chose, closure then PRECEDED creation on most of them, and anything
     * measuring a filing-to-closure window had to discard them as negative — which is why the
     * assistance rail's category-closure prior had no usable sample to report.
     *
     * <p>Conditional assignment is the whole fix: a null field still gets {@code now()}, so the real
     * filing path is unchanged and no caller has to remember to set anything. Only a caller that
     * deliberately set a date keeps it.
     *
     * <p>{@code createdAt} is not merely cosmetic here — {@code AppealClassificationService} derives
     * the appeal window from it, so a complaint whose creation date is really its insert date carries
     * a wrong statutory deadline.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.filedAt == null) this.filedAt = now;
        if (this.lastStatusChangeDate == null) this.lastStatusChangeDate = now;
        if (this.status == null) this.status = "pending";
        if (this.priority == null) this.priority = "medium";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
