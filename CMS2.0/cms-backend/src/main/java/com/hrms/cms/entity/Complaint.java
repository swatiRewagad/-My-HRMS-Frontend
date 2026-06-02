package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Index(name = "idx_complaint_status_created", columnList = "status,createdAt")
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
        if (this.status == null) this.status = "pending";
        if (this.priority == null) this.priority = "medium";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
