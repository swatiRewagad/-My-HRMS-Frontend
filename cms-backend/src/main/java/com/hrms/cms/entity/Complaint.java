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

    // Prior RE complaint details (RB-IOS Q16/Q17/Q18)
    private Boolean priorReComplaint;

    private LocalDate reComplaintDate;

    @Column(length = 200)
    private String reComplaintReference;

    private Boolean reRepliedAndDissatisfied;

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
