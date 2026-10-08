package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The CEPC officer's assessment of a complaint — everything the Summary tab collects that is not part of
 * the complaint as the complainant filed it.
 *
 * <p><b>Why this is a side table and not columns on {@link Complaint}.</b> {@code Complaint} carries a
 * {@code @Version} column, so a write to it fails if a workflow transition has touched the row since it was
 * read. An officer typing into the Summary tab holds their copy for as long as the form is open, and every
 * forward, reminder and RE status change in that window would turn their save into a 409. The assessment is
 * also owned by a different actor and edited on a different rhythm than the workflow state, so keeping the
 * two rows apart means neither blocks the other.
 *
 * <p>One row per complaint, keyed on the complaint NUMBER rather than the id, because that is the
 * identifier every CEPC screen and endpoint passes around.
 *
 * <p><b>Every column is nullable and every boolean is a {@link Boolean}.</b> A blank assessment is the
 * normal state of a freshly registered complaint, and on a yes/no question "not yet answered" is a
 * different fact from "answered no" — a primitive would silently report every unanswered question as a no.
 *
 * <p>The entity and branch columns are a complaint-scoped SNAPSHOT, not a view of
 * {@link RegulatedEntity}. The Summary tab lets the officer correct the branch, pincode or BSR code for
 * this complaint, and writing that back to the master row would rewrite it for every other complaint
 * against the same entity. Read falls through to the master when the snapshot column is null.
 */
@Entity
@Table(name = "CEPC_COMPLAINT_ASSESSMENT",
        uniqueConstraints = @UniqueConstraint(name = "uk_cepc_assessment_complaint",
                columnNames = {"complaintNumber"}),
        indexes = @Index(name = "idx_cepc_assessment_complaint", columnList = "complaintNumber"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CepcComplaintAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String complaintNumber;

    // ── Basic details not held on the complaint ─────────────────────────────
    @Column(length = 2000)
    private String officerComments;

    private Boolean complaintCpgram;

    @Column(length = 60)
    private String cpgramNumber;

    // ── Entity and branch snapshot (see class javadoc) ──────────────────────
    private Long regulatedEntityId;

    @Column(length = 250)
    private String entityName;

    @Column(length = 120)
    private String moduleName;

    @Column(length = 120)
    private String entityCategory;

    @Column(length = 30)
    private String bsrCode;

    @Column(length = 10)
    private String entityPincode;

    @Column(length = 80)
    private String entityCountry;

    @Column(length = 80)
    private String entityState;

    @Column(length = 120)
    private String entityDistrict;

    @Column(length = 120)
    private String entityCity;

    @Column(length = 200)
    private String entityBranchName;

    @Column(length = 120)
    private String entityBranchCategory;

    @Column(length = 200)
    private String branchCenterName;

    @Column(length = 500)
    private String entityAddress;

    // ── Basic identification ────────────────────────────────────────────────
    @Column(length = 250)
    private String otherEntityName;

    private LocalDate registrationWithRbiDate;

    // ── Classification ──────────────────────────────────────────────────────
    /**
     * The category as free text.
     *
     * <p>Separate from {@link Complaint#getCategoryId()}, which is the master-table reference the intake
     * flow sets. The officer's classification is a reassessment and can disagree with what was filed;
     * overwriting the intake value would lose what the complainant chose.
     */
    @Column(length = 200)
    private String complaintCategoryText;

    @Column(length = 200)
    private String complaintSubCategory1;

    @Column(length = 200)
    private String complaintSubCategory2;

    private Boolean complaintRegistrationDateValid;

    private LocalDate dateOfFilingComplaint;

    // ── Financial ───────────────────────────────────────────────────────────
    private Boolean reminderSent;

    @Column(precision = 15, scale = 2)
    private BigDecimal disputedAmount;

    /** Sent as 0/1 by the client rather than a boolean, so it is stored as the integer it arrives as. */
    private Integer compensationSought;

    // ── Legal ───────────────────────────────────────────────────────────────
    private Boolean legalCaseFiled;

    private Boolean preEnquiryReceived;

    private Boolean highPriorityComplaint;

    @Column(precision = 15, scale = 2)
    private BigDecimal loanDisposalAmount;

    // ── Additional information ──────────────────────────────────────────────
    @Column(length = 2000)
    private String additionalComments;

    /**
     * The officer's proposed action — MAINTAINABLE, NON_MAINTAINABLE or a deputy decision.
     *
     * <p>Load-bearing beyond the Summary tab: the Conciliation tab is enabled only when this says the
     * complaint reached the maintainable stage, so a value that fails to round-trip here disables
     * conciliation for a complaint that qualifies for it.
     */
    @Column(length = 60)
    private String crpcProposedAction;

    @Column(length = 60)
    private String proposedClause;

    @Column(columnDefinition = "TEXT")
    private String speakingOrderContent;

    @Column(length = 60)
    private String vernacularLanguage;

    // ── Flags and indicators ────────────────────────────────────────────────
    private Boolean complaintRegardingPension;

    private Boolean complaintAgainstBusinessCorrespondent;

    private Boolean atmCreditDebitCard;

    @Column(length = 60)
    private String schemeFlag;

    @Column(length = 60)
    private String rboCgpcOld;

    @Column(length = 60)
    private String groundsFlag;

    // ── Linkage ─────────────────────────────────────────────────────────────
    private Boolean freeMarkedComplaint;

    @Column(length = 60)
    private String replyWithin30Days;

    // ── Final decision ──────────────────────────────────────────────────────
    //
    // The outcome dates and the closure clause itself live on COMPLAINTS, which already carries
    // award_passed_date, award_implemented_date, advisory_complied_at, closure_clause and
    // withdrawal_reason. What follows is the narrative and the figures around them, which have no column
    // there.

    // TEXT rather than varchar(4000) for both of these and for the speaking order above. MySQL counts a
    // varchar against its 65535-byte row limit at 4 bytes per character under utf8mb4, so three 4000-char
    // narrative columns on one row is 48KB of the budget on their own and the table stopped accepting new
    // columns. TEXT is stored away from the row and costs it a pointer.
    @Column(columnDefinition = "TEXT")
    private String gistOfCase;

    /** The gist in the regional language, sent alongside the English one for the regional office file. */
    @Column(columnDefinition = "TEXT")
    private String gistOfCaseRegional;

    private Boolean speakingOrderGenerated;

    /** What the complainant is shown on the public tracker, which need not equal the internal status. */
    @Column(length = 60)
    private String complaintStatusOnPortal;

    @Column(precision = 15, scale = 2)
    private BigDecimal compensationLoss;

    @Column(precision = 15, scale = 2)
    private BigDecimal compensationMental;

    @Column(length = 500)
    private String systemicIssue;

    /** The date by which the RE must comply with an advisory — not {@code advisory_complied_at}, which
     * records when they actually did. */
    private LocalDate advisoryComplianceDate;

    private LocalDate awardAcceptanceDate;

    /** Which of CLOSE / ADVISORY / AWARD / REJECT_WITHDRAW_SETTLE the officer is working towards. */
    @Column(length = 40)
    private String finalDecisionAction;

    @Column(length = 40)
    private String rejectWithdrawSettleSubAction;

    @Column(columnDefinition = "TEXT")
    private String rejectWithdrawSettleReason;

    @Column(columnDefinition = "TEXT")
    private String closureClauseDescription;

    /** Kept apart from {@code Complaint.closureClause}, which the terminal workflow arm owns: a draft save
     * must not overwrite a clause the closure already committed. Read falls through to the complaint's. */
    @Column(length = 60)
    private String closureClauseDraft;

    private LocalDate awardImplementationDate;

    // ── Forward draft ───────────────────────────────────────────────────────
    /** The Forward tab's in-progress selection, as JSON, before anything is dispatched.
     *
     * <p>One TEXT column rather than six scalars because four of the six are 200-char names and emails,
     * which is ~3.2KB of the row budget the class javadoc above says this table no longer has. Nothing
     * reports on a pre-dispatch scratchpad, so losing queryability costs nothing. */
    @Column(columnDefinition = "TEXT")
    private String forwardDraftJson;

    // ── Eligibility header ──────────────────────────────────────────────────
    /** The per-question answers live in {@link ComplaintEligibilityAnswer}; this is the panel's verdict. */
    @Column(length = 60)
    private String proposedComplaintType;

    @Column(length = 100)
    private String lastModifiedBy;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.lastModifiedAt == null) {
            this.lastModifiedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = LocalDateTime.now();
    }
}
