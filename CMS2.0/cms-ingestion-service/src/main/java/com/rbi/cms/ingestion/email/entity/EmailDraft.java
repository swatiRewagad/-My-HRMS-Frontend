package com.rbi.cms.ingestion.email.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "EMAIL_DRAFT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "DRAFT_ID", unique = true, nullable = false, length = 30)
    private String draftId;

    @Column(name = "MESSAGE_ID", unique = true, length = 500)
    private String messageId;

    @Column(name = "SENDER_EMAIL", nullable = false, length = 200)
    private String senderEmail;

    @Column(name = "SUBJECT", length = 1000)
    private String subject;

    @Column(name = "BODY", columnDefinition = "CLOB")
    private String body;

    @Column(name = "COMPLAINANT_NAME", length = 200)
    private String complainantName;

    @Column(name = "COMPLAINANT_PHONE", length = 20)
    private String complainantPhone;

    @Column(name = "CPGRAMS_NUMBER", length = 50)
    private String cpgramsNumber;

    @Column(name = "COMPLAINT_SUMMARY", length = 4000)
    private String complaintSummary;

    @Column(name = "CATEGORY", length = 30)
    private String category;

    @Column(name = "MODE_OF_RECEIPT", length = 30)
    @Builder.Default
    private String modeOfReceipt = "EMAIL";

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private EmailDraftStatus status = EmailDraftStatus.PENDING;

    @Column(name = "ASSIGNED_TO", length = 100)
    private String assignedTo;

    @Column(name = "PARENT_COMPLAINT_ID", length = 30)
    private String parentComplaintId;

    @Column(name = "IS_DUPLICATE")
    @Builder.Default
    private Boolean isDuplicate = false;

    @Column(name = "OCR_PROCESSED")
    @Builder.Default
    private Boolean ocrProcessed = false;

    @Column(name = "OCR_CONFIDENCE")
    private Double ocrConfidence;

    @Column(name = "RECEIVED_AT", nullable = false)
    private Instant receivedAt;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "PROCESSED_BY", length = 100)
    private String processedBy;

    @Column(name = "CONVERTED_COMPLAINT_ID", length = 30)
    private String convertedComplaintId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (receivedAt == null) receivedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
