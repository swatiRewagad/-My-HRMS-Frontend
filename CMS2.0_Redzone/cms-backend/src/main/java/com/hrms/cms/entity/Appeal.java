package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "APPEALS", indexes = {
    @Index(name = "idx_appeal_number", columnList = "appealNumber", unique = true),
    @Index(name = "idx_appeal_original_complaint", columnList = "originalComplaintNumber"),
    @Index(name = "idx_appeal_status", columnList = "status"),
    @Index(name = "idx_appeal_assigned_role", columnList = "assignedRole"),
    @Index(name = "idx_appeal_assigned_officer", columnList = "assignedOfficer"),
    @Index(name = "idx_appeal_created", columnList = "createdAt"),
    // The AA home views filter on these; without indexes every view is a full scan.
    @Index(name = "idx_appeal_classification", columnList = "classificationType"),
    @Index(name = "idx_appeal_created_by", columnList = "createdBy"),
    @Index(name = "idx_appeal_entity_code", columnList = "entityCode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Appeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String appealNumber;

    @Column(nullable = false, length = 50)
    private String originalComplaintNumber;

    /**
     * "APPEAL" or "REPRESENTATION", DERIVED server-side from the parent's closure clause and the
     * raising party — never accepted from the client.
     *
     * Not marked updatable=false: the Scheme requires a manual override path for edge cases. That
     * path is AppealWorkflowService.overrideClassification, which demands a reason and writes a
     * field-level audit row; the ordinary performAction flow still rejects any change outright.
     */
    @Column(nullable = false, length = 20)
    private String classificationType;

    /** True when an AA user set aside the clause-derived classification. */
    @Column(nullable = false)
    private boolean classificationOverridden;

    @Column(length = 1000)
    private String classificationOverrideReason;

    @Column(length = 200)
    private String classificationOverriddenBy;

    private LocalDateTime classificationOverriddenAt;

    @Column(columnDefinition = "TEXT")
    private String appealGround;

    @Column(columnDefinition = "TEXT")
    private String reliefSought;

    @Column(name = "reason_for_delay", length = 500)
    private String reasonForDelay;

    @Column(nullable = false, length = 200)
    private String appellantName;

    @Column(length = 200)
    private String appellantEmail;

    @Column(length = 20)
    private String appellantPhone;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(length = 200)
    private String assignedOfficer;

    @Column(length = 50)
    private String assignedRole;

    @Column(length = 20)
    private String priority;

    @Column(length = 50)
    private String workflowStage;

    private LocalDateTime filedAt;

    private LocalDateTime hearingDate;

    @Column(length = 500)
    private String hearingVenue;

    private LocalDateTime orderDate;

    @Column(columnDefinition = "TEXT")
    private String orderSummary;

    @Column(length = 30)
    private String orderOutcome;

    @Column(precision = 15, scale = 2)
    private BigDecimal awardModifiedAmount;

    @Column(length = 50)
    private String closureCause;

    /** Scheme clause the appeal is closed under; resolved from CLOSURE_CLAUSE_MASTER. */
    @Column(length = 40)
    private String closureClause;

    private LocalDateTime closedAt;

    // ═══════════════════════════════════════════════════════════════════════
    // Provenance — who created and who routed this record
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The user who registered this appeal. Without it the "Created By Me" view is impossible: only
     * createdAt existed, and the FILED timeline row is written with performedBy="SYSTEM", so there
     * was no way to attribute a record to the officer who registered it.
     */
    @Column(length = 200)
    private String createdBy;

    @Column(length = 50)
    private String createdByRole;

    /**
     * The AA user who last routed this record onward, and their role.
     *
     * Every "send back returns to the ORIGINAL X" requirement depends on this. Previously nothing
     * recorded who routed a record, so send-back re-assigned by round robin — i.e. to a random holder
     * of the role rather than the person who sent it.
     */
    @Column(length = 200)
    private String routedByUserId;

    @Column(length = 50)
    private String routedByRole;

    /** Reviewer this record was forwarded to via "Put up to"; send-back returns to the forwarder. */
    @Column(length = 200)
    private String putUpToUserId;

    // ═══════════════════════════════════════════════════════════════════════
    // Entity linkage — needed to auto-resolve the PNO from the Entity master
    // ═══════════════════════════════════════════════════════════════════════

    /** Links the appeal to a regulated entity. Appeal previously had no entity reference at all. */
    @Column(length = 100)
    private String entityCode;

    @Column(length = 200)
    private String resolvedPnoUserId;

    // ═══════════════════════════════════════════════════════════════════════
    // Register-milestone intake fields
    // ═══════════════════════════════════════════════════════════════════════

    /** COMPLAINANT or ENTITY — drives which clauses are appealable for this record. */
    @Column(length = 30)
    private String appealFiledBy;

    @Column(length = 60)
    private String sourceOfAppeal;

    /** PORTAL, EMAIL, PHYSICAL_LETTER. Auto-set from the intake channel and then read-only. */
    @Column(length = 30)
    private String modeOfReceipt;

    @Column(length = 300)
    private String appellantAddress1;

    @Column(length = 300)
    private String appellantAddress2;

    @Column(length = 100)
    private String appellantCity;

    @Column(length = 100)
    private String appellantDistrict;

    @Column(length = 100)
    private String appellantState;

    @Column(length = 100)
    private String appellantCountry;

    @Column(length = 10)
    private String appellantPincode;

    private Long categoryId;

    @Column(length = 300)
    private String entityName;

    @Column(length = 100)
    private String entityRegion;

    @Column(length = 100)
    private String entityCategory;

    @Column(length = 200)
    private String entityBranch;

    @Column(length = 40)
    private String bsrIfscCode;

    /**
     * Stored in full; masked to first-4 + last-4 at the API boundary by PiiMaskingService. Masking on
     * read rather than on write keeps the record usable for entity correspondence.
     */
    @Column(length = 60)
    private String accountNumber;

    @Column(length = 60)
    private String cardNumber;

    @Column(length = 200)
    private String nodalOfficerName;

    /** Distinct from Complaint.throughAdvocate, which means "filed THROUGH an advocate". */
    private Boolean isComplainantAdvocate;

    private Boolean hasRelatedCourtTrial;

    // ═══════════════════════════════════════════════════════════════════════
    // Regulated-entity sign-off (PNO-created appeals)
    // ═══════════════════════════════════════════════════════════════════════

    private Boolean edApprovalGiven;

    private LocalDateTime edApprovalDate;

    @Column(length = 2000)
    private String edApprovalComments;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.filedAt = LocalDateTime.now();
        if (this.status == null) this.status = "filed";
        if (this.priority == null) this.priority = "high";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
