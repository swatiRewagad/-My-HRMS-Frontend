package com.hrms.cms.dto.syndication;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * The full email/physical-letter draft as the CRPC screens consume it — the DEO queue, the DEO
 * assessment form, the reviewer assessment form and the physical-letter form all bind to this one
 * shape, which is why it is this wide.
 *
 * <p>Deliberately not {@code @JsonInclude(NON_NULL)} at class level: a freshly ingested draft has
 * almost none of the assessment fields set, and the forms bind to them directly, so the keys have to
 * keep appearing with a null value as they did when this was a {@code LinkedHashMap}. Only
 * {@link #ocrExtractedFields} was ever conditionally absent, so it carries the annotation itself.
 *
 * <p>The {@code Boolean} wrappers on the {@code isXxx} fields are load-bearing rather than about
 * nullability: Lombok names a primitive {@code boolean isDuplicate} accessor {@code isDuplicate()},
 * which Jackson would publish as {@code "duplicate"} and silently rename the key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDraftResponse implements EmailIngestResult {

    private Long id;
    private String draftId;
    /** Human-facing short id ({@code C017}). Absent on the legacy-thread fallback, which has no row. */
    private String displayId;
    private String messageId;
    private String senderEmail;
    private String subject;
    private String body;
    private String complainantName;
    private String complainantPhone;
    private String complainantAddress;
    private String complainantState;
    private String complainantDistrict;
    private String complainantPincode;
    private String cpgramsNumber;
    private String complaintSummary;
    private String category;
    private String modeOfReceipt;
    private String status;
    private String assignedTo;
    private String parentComplaintId;
    private Boolean isDuplicate;
    private Boolean ocrProcessed;
    private Integer ocrConfidence;
    private String entityName;
    private String entityType;
    private Double amountInvolved;
    private String receivedAt;
    private String createdAt;
    private String processedBy;
    private String convertedComplaintId;
    @Builder.Default
    private List<EmailDraftAttachmentResponse> attachments = List.of();
    /** Always empty — related-draft suggestion was never implemented, but the queue binds to the key. */
    @Builder.Default
    private List<Object> suggestedRelated = List.of();

    // DEO assessment
    private String deoDecision;
    private String deoRemarks;
    private String nonMaintainableReason;

    // Reviewer
    private String reviewerDecision;
    private String reviewerRemarks;
    private String targetOffice;

    // Scheme & auto-closure
    private String schemeVersion;
    private String closureClause;
    private String autoClosureResponsesJson;
    private Boolean subJudice;
    private String notAComplaintReason;
    private String notAComplaintOthersReason;
    private String suggestionDepartment;
    private String suggestionNature;

    // Language
    private String detectedLanguage;
    private String languageName;
    private Boolean isVernacular;
    private Double translationConfidence;

    // Eligibility
    private String proposedComplaintType;
    private String notComplaintReason;
    private String eligibilityQuestionsJson;

    // Entity details
    private String entityCategory;
    private String entityTypeDetail;
    private String entityBsrCode;
    private String entityPincode;
    private String entityCountry;
    private String entityState;
    private String entityDistrict;
    private String entityCity;
    private String entityBranchName;
    private String entityBranchCategory;
    private String entityAddress;
    private String entityBranchCenterName;
    private String cosmosCode;
    private String assetSize;
    private Boolean isDepositTaking;
    private Boolean isAssetAbove100Cr;
    private Boolean isLiquidated;

    // Complainant extended
    private String otherEntityName;
    private String dateOfRegistrationWithRBI;
    private String complaintCategory;
    private String complaintSubCategory1;
    private String complaintSubCategory2;
    private String dateOfFilingComplaint;
    private String complaintRegDateValid;
    private String reminderSentByComplainant;
    private String disputedAmountInvolved;
    private String dateOfFilingForFinancial;
    private String compensationSought;
    private String loanDisposalAmount;
    private String additionalComments;
    private String crpcProposedAction;
    private String vernacularLanguageDetail;

    // Legal & case
    private String legalCaseFiled;
    private String legalDateOfFiling;
    private String preEnquiryReceived;

    // Flags
    private String highPriorityComplaint;
    private String isRegardingPension;
    private String isAgainstBusinessCorrespondent;
    private String isAtmCreditDebitCard;
    private String schemeFlag;
    private String isFreeMarkedComplaint;

    // Linkage
    private String currentComplaintNumber;
    private String receivedReplyWithin30Days;

    // Declaration
    private Boolean declarationAccepted;

    /**
     * Only present when OCR ran, produced fields, and the stored JSON parsed — the assessment form
     * uses its absence to decide whether to show the "extracted from attachment" panel at all, so
     * unlike every other field here this one must stay absent rather than become null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> ocrExtractedFields;
}
