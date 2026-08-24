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

    @Column(length = 200)
    private String targetEntity;

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

    // ─── Eligibility & Proposed Action ───
    @Column(length = 50)
    private String proposedComplaintType;

    @Column(length = 200)
    private String notComplaintReason;

    @Column(columnDefinition = "TEXT")
    private String eligibilityQuestionsJson;

    // ─── Entity Details (expanded) ───
    @Column(length = 100)
    private String entityCategory;

    @Column(length = 100)
    private String entityTypeDetail;

    @Column(length = 50)
    private String entityBsrCode;

    @Column(length = 10)
    private String entityPincode;

    @Column(length = 100)
    private String entityCountry;

    @Column(length = 100)
    private String entityState;

    @Column(length = 100)
    private String entityDistrict;

    @Column(length = 100)
    private String entityCity;

    @Column(length = 200)
    private String entityBranchName;

    @Column(length = 100)
    private String entityBranchCategory;

    @Column(length = 500)
    private String entityAddress;

    @Column(length = 200)
    private String entityBranchCenterName;

    @Column(length = 50)
    private String cosmosCode;

    @Column(length = 50)
    private String assetSize;

    private Boolean isDepositTaking;

    private Boolean isAssetAbove100Cr;

    private Boolean isLiquidated;

    // ─── Complainant Extended Details ───
    @Column(length = 200)
    private String otherEntityName;

    @Column(length = 30)
    private String dateOfRegistrationWithRBI;

    @Column(length = 100)
    private String complaintCategory;

    @Column(length = 100)
    private String complaintSubCategory1;

    @Column(length = 100)
    private String complaintSubCategory2;

    @Column(length = 30)
    private String dateOfFilingComplaint;

    @Column(length = 10)
    private String complaintRegDateValid;

    @Column(length = 10)
    private String reminderSentByComplainant;

    @Column(length = 50)
    private String disputedAmountInvolved;

    @Column(length = 30)
    private String dateOfFilingForFinancial;

    @Column(length = 50)
    private String compensationSought;

    @Column(length = 50)
    private String loanDisposalAmount;

    @Column(columnDefinition = "TEXT")
    private String additionalComments;

    @Column(length = 100)
    private String crpcProposedAction;

    @Column(length = 200)
    private String vernacularLanguageDetail;

    // ─── Legal & Case Details ───
    @Column(length = 10)
    private String legalCaseFiled;

    @Column(length = 30)
    private String legalDateOfFiling;

    @Column(length = 10)
    private String preEnquiryReceived;

    // ─── Flags & Indicators ───
    @Column(length = 10)
    private String highPriorityComplaint;

    @Column(length = 10)
    private String isRegardingPension;

    @Column(length = 10)
    private String isAgainstBusinessCorrespondent;

    @Column(length = 10)
    private String isAtmCreditDebitCard;

    @Column(length = 10)
    private String schemeFlag;

    @Column(length = 10)
    private String isFreeMarkedComplaint;

    // ─── Complaint Linkage ───
    @Column(length = 100)
    private String currentComplaintNumber;

    @Column(length = 20)
    private String receivedReplyWithin30Days;

    // ─── Declaration ───
    private Boolean declarationAccepted;

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
