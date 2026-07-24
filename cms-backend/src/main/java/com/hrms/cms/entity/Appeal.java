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
    @Index(name = "idx_appeal_created", columnList = "createdAt")
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
     * IMMUTABLE after creation. Must be "APPEAL" or "REPRESENTATION".
     */
    @Column(nullable = false, length = 20)
    private String classificationType;

    @Column(columnDefinition = "TEXT")
    private String appealGround;

    @Column(columnDefinition = "TEXT")
    private String reliefSought;

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

    private LocalDateTime closedAt;

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
