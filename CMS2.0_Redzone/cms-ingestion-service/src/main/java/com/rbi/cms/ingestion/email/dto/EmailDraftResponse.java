package com.rbi.cms.ingestion.email.dto;

import com.rbi.cms.ingestion.email.entity.EmailDraftStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDraftResponse {

    private Long id;
    private String draftId;
    private String messageId;
    private String senderEmail;
    private String subject;
    private String body;
    private String complainantName;
    private String complainantPhone;
    private String cpgramsNumber;
    private String complaintSummary;
    private String category;
    private String modeOfReceipt;
    private EmailDraftStatus status;
    private String assignedTo;
    private String parentComplaintId;
    private Boolean isDuplicate;
    private Boolean ocrProcessed;
    private Double ocrConfidence;
    private Instant receivedAt;
    private Instant createdAt;
    private String processedBy;
    private String convertedComplaintId;
    private List<EmailAttachmentResponse> attachments;
    private List<EmailDraftResponse> suggestedRelated;
}
