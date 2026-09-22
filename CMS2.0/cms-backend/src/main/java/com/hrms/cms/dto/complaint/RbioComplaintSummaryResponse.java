package com.hrms.cms.dto.complaint;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The consolidated RBIO officer view of one complaint, sectioned the way the officer form is laid out.
 *
 * <p>The sections are nested classes rather than separate files because none of them means anything on
 * its own — they exist only as parts of this payload, and keeping them together is what makes the whole
 * contract readable in one pass. That is the point of typing it: the shape used to live as string
 * literals spread across {@code RbioComplaintSummaryService}, where nothing stopped a key from being
 * renamed on one side only.
 *
 * <p><b>No class-level {@code @JsonInclude(NON_NULL)} anywhere in this file, deliberately.</b> The
 * service it replaced built {@code LinkedHashMap}s and called {@code put(key, null)} for every absent
 * child row, so the keys were present with null values on the wire. The officer form binds its controls
 * to those keys; omitting them would change what the screen receives.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbioComplaintSummaryResponse {

    private Long id;

    /**
     * Surfaced at the top level because the screens use it to decide between an editable form and a
     * read-only view; only the holder may write.
     */
    private String assignedOfficer;
    private String assignedOfficerName;
    private String assignedRole;

    private NavBar navBarDto;
    private BasicDetails basicDetailsDto;
    private Eligibility eligibility;
    private EntityDetails entityDetails;
    private ComplainDetails complainDetailsDto;

    /**
     * Filled in by the controller, not the service: it depends on who is asking, while everything else
     * here depends only on the complaint.
     */
    private Boolean canEdit;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NavBar {
        private String complaintNumber;
        private String complainantName;
        private String entityName;
        private String status;
        private String complaintCategory;
        private String slaBreachIn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasicDetails {
        private Long id;
        private String subject;
        private String emailId;
        private String complainantName;
        private LocalDate receiptDate;
        private String modeOfReceipt;
        private String comments;
        private Boolean complaintCpgram;
        private String cpgramNumber;
        private String complainDetails;
    }

    /**
     * Every answer is a tri-state: true, false, or null for "never asked". The columns behind them store
     * {@code "yes"}/{@code "no"}/NULL.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Eligibility {
        private Long id;
        private String proposedComplaintType;
        private Boolean entityRegulatedByRbi;
        private Boolean complaintNotDirectlyAddressedToOmbudsman;
        private Boolean complaintNotRegisteredWithEntity;
        private Boolean frivolousVexatiousThreatening;
        private Boolean subJudiceOrArbitration;
        private Boolean sameGrievancePendingBeforeCourt;
        private Boolean sameGrievanceSettledBeforeCourt;
        private Boolean complaintMadeThroughAdvocate;
        private Boolean complainantIsAdvocate;
        private Boolean sameGrievancePendingBeforeOmbudsman;
        private Boolean alreadyDealtWithByOmbudsman;
        private Boolean complaintAgainstManagement;

        // The acronym-bearing keys are pinned explicitly: Jackson's bean naming would have to be
        // reasoned about to predict them, and getting one wrong silently renames a field the form binds to.
        @JsonProperty("staffOfREEmployerRelationship")
        private Boolean staffOfREEmployerRelationship;

        @JsonProperty("complaintFiledWithCEPCOrRBI")
        private Boolean complaintFiledWithCEPCOrRBI;

        @JsonProperty("disputeBetweenREs")
        private Boolean disputeBetweenREs;

        private Boolean completeInformationUnavailable;

        @JsonProperty("writtenComplaintFiledWithRE")
        private Boolean writtenComplaintFiledWithRE;

        @JsonProperty("firstFiledWithREDate")
        private LocalDate firstFiledWithREDate;

        private Boolean receivedReplyFromEntity;
        private LocalDate replyDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityDetails {
        private Long id;
        private String entityName;
        private String moduleName;
        private String entityCategory;
        /** Read back from the regulated-entity master; never persisted from the payload. */
        private String entityType;
        private String bsrCode;
        private String pincode;
        private String country;
        private String state;
        private String district;
        private String city;
        private String branchName;
        private String branchCategory;
        private String branchCenterName;
        private String entityAddress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplainDetails {
        private BasicIdentification basicIdentificationDto;
        private ComplaintClassification complaintClassification;
        private FinancialDetails financialDetails;
        private LegalCaseDetails legalCaseDetails;
        private AdditionalInformation additionalInformation;
        private FlagsAndIndicators flagsAndIndicators;
        private ComplaintLinkage complaintLinkage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasicIdentification {
        private String emailId;
        private String entityName;
        private String otherEntityName;
        private LocalDate registrationWithRbiDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplaintClassification {
        /** The category id, not a label — {@link #complaintCategory} carries the display name. */
        private Long id;
        private String complaintCategory;
        private String complaintSubCategory1;
        private String complaintSubCategory2;
        private Boolean complaintRegistrationDateValid;
        private LocalDate dateOfFilingComplaint;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialDetails {
        private Long id;
        private Boolean reminderSent;
        private BigDecimal disputedAmount;
        private BigDecimal compensationSought;
        private LocalDate dateOfFiling;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalCaseDetails {
        private Long id;
        private Boolean legalCaseFiled;
        private LocalDate filingDate;
        private Boolean preEnquiryReceived;
        private Boolean highPriorityComplaint;
        private BigDecimal loanDisposalAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdditionalInformation {
        private Long id;
        private String comments;
        private String crpcProposedAction;
        private String vernacularLanguage;
        private LocalDate dateOfFiling;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlagsAndIndicators {
        private Long id;
        private Boolean complaintRegardingPension;
        private Boolean complaintAgainstBusinessCorrespondent;
        private Boolean atmCreditDebitCard;
        private String schemeFlag;
        private String rboCgpcOld;
        private String groundsFlag;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplaintLinkage {
        private Long id;
        private Boolean freeMarkedComplaint;
        private String currentComplaintNumber;
        /** Tri-state: "Yes" / "No" / "Not Applicable", or null when never answered. */
        private String replyWithin30Days;
    }
}
