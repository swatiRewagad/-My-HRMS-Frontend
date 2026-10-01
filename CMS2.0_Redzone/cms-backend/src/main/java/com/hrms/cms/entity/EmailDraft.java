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

    /**
     * Unique so a redelivered message cannot create a second draft. cms-mail-intake supplies a
     * stable "mail-intake-<id>", which makes this idempotency real across restarts.
     */
    @Column(length = 200, unique = true)
    private String messageId;

    @Column(length = 200)
    private String senderEmail;

    // Recipient headers. Previously accepted by the API and discarded, which left every
    // ignore-list rule on To/CC/BCC unenforceable.
    @Column(length = 1000)
    private String toRecipients;

    @Column(length = 1000)
    private String ccRecipients;

    @Column(length = 1000)
    private String bccRecipients;

    @Column(length = 200)
    private String replyTo;

    @Column(length = 500)
    private String inReplyTo;

    @Column(columnDefinition = "TEXT")
    private String emailReferences;

    private Integer attachmentCount;

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

    @Column(length = 20)
    private String schemeVersion; // RBIOS_2021, RBIOS_2026

    @Column(length = 50)
    private String closureClause;

    @Column(columnDefinition = "TEXT")
    private String autoClosureResponsesJson;

    private boolean subJudice;

    @Column(length = 100)
    private String notAComplaintReason; // Appeal, Broadcast Message, Password Change, Suggestion, Others

    @Column(columnDefinition = "TEXT")
    private String notAComplaintOthersReason;

    @Column(length = 100)
    private String suggestionDepartment;

    @Column(length = 200)
    private String suggestionNature;

    @Column(length = 50)
    private String detectedLanguage;

    @Column(length = 100)
    private String languageName;

    private boolean isVernacular;

    private Double translationConfidence;

    @Column(columnDefinition = "TEXT")
    private String translatedBody;

    /**
     * Why OCR did not run, or why its output was not used to prefill. Null when OCR prefilled
     * normally. Drives the manual-entry routing shown to the DEO.
     */
    @Column(length = 40)
    private String ocrSkipReason;

    /** Set when vernacular or low-confidence content must be keyed by a human. */
    private boolean requiresManualEntry;

    // Suggested-related decision, persisted so the DO's accept/dismiss survives a reload.
    @Column(length = 100)
    private String suggestedRelatedDraftId;

    @Column(length = 20)
    private String suggestedRelatedDecision;

    @Column(length = 200)
    private String suggestedRelatedDecidedBy;

    private LocalDateTime suggestedRelatedDecidedAt;

    private LocalDateTime receivedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.updatedAt == null) this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = DraftStatus.ASSIGNED.name();
        if (this.draftId == null || this.draftId.isBlank()) {
            // A UUID suffix, not a truncated nanoTime: the old form collided under concurrent
            // ingests and draftId is unique.
            this.draftId = "DRF-" + java.util.UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
