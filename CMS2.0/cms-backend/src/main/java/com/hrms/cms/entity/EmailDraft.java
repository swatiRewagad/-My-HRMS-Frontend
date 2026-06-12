package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "EMAIL_DRAFTS", indexes = {
    @Index(name = "idx_draft_thread", columnList = "threadId"),
    @Index(name = "idx_draft_status", columnList = "status"),
    @Index(name = "idx_draft_assigned", columnList = "assignedTo"),
    @Index(name = "idx_draft_sender", columnList = "senderEmail")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String draftId;

    @Column(length = 100)
    private String threadId;

    @Column(length = 200)
    private String messageId;

    @Column(length = 200)
    private String senderEmail;

    @Column(length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(length = 200)
    private String complainantName;

    @Column(length = 20)
    private String complainantPhone;

    @Column(length = 500)
    private String complainantAddress;

    @Column(length = 100)
    private String complainantState;

    @Column(length = 100)
    private String complainantDistrict;

    @Column(length = 10)
    private String complainantPincode;

    @Column(length = 50)
    private String cpgramsNumber;

    @Column(length = 500)
    private String complaintSummary;

    @Column(length = 50)
    private String category;

    @Column(length = 30)
    private String modeOfReceipt;

    @Column(length = 30)
    private String status;

    @Column(length = 200)
    private String assignedTo;

    @Column(length = 50)
    private String parentComplaintId;

    private boolean isDuplicate;

    private boolean ocrProcessed;

    private int ocrConfidence;

    @Column(columnDefinition = "TEXT")
    private String ocrExtractedFieldsJson;

    @Column(length = 100)
    private String entityName;

    @Column(length = 30)
    private String entityType;

    private Double amountInvolved;

    @Column(length = 200)
    private String processedBy;

    @Column(length = 30)
    private String deoDecision;

    @Column(columnDefinition = "TEXT")
    private String deoRemarks;

    @Column(length = 100)
    private String nonMaintainableReason;

    @Column(length = 200)
    private String reviewerAssignedTo;

    @Column(length = 30)
    private String reviewerDecision;

    @Column(columnDefinition = "TEXT")
    private String reviewerRemarks;

    @Column(length = 100)
    private String targetOffice;

    @Column(length = 50)
    private String convertedComplaintId;

    @Column(length = 50)
    private String detectedLanguage;

    @Column(length = 100)
    private String languageName;

    private boolean isVernacular;

    private Double translationConfidence;

    @Column(columnDefinition = "TEXT")
    private String translatedBody;

    private LocalDateTime receivedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "ASSIGNED";
        if (this.draftId == null || this.draftId.isBlank()) {
            this.draftId = "DRF-" + String.format("%06d", System.nanoTime() % 1000000);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
