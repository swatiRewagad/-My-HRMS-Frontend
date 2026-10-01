package com.rbi.cms.search.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape the strict mapping accepts.
 *
 * <p>This type exists because the mapping is {@code "dynamic": "strict"}. Previously documents were
 * assembled as an untyped {@code Map} straight out of a Kafka payload, so whatever keys the producer
 * happened to send became mapped fields. Under a strict mapping that same code would now fail every
 * write instead, so the document has to be a declared type whose fields match the mapping.
 *
 * <p>Notably absent: any field holding extracted document/annexure text. Attachment text is indexed
 * as a separate concern keyed by complaint number — concatenating a scanned annexure into the
 * complaint document would let one OCR'd page dominate term frequencies and wreck relevance for the
 * whole case.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplaintDocument {

    private String complaintId;
    private String complaintNumber;
    private String detectedLanguage;

    private String subject;
    private String description;
    private String advisoryText;
    private String withdrawalReason;
    private String schemeCoverageReason;
    private String reopenJustification;

    private String closureClause;
    private String reopenReason;

    private String categoryId;
    private String entityCode;
    private String department;
    private String status;
    private String workflowStage;
    private String milestone;
    private String rbioOfficeCode;
    private String groundOfComplaintId;
    private String compensationType;
    private String maintainabilityDetermination;
    private String priority;
    private String assignedOfficer;
    private String assignedRole;

    private Double awardAmount;

    private String createdAt;
    private String updatedAt;

    private List<TimelineEntry> timeline = new ArrayList<>();
    private List<AppealOrderEntry> appealOrders = new ArrayList<>();

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimelineEntry {
        private String action;
        private String fromStatus;
        private String toStatus;
        private String performedByRole;
        private String performedAt;
        private String remarks;
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppealOrderEntry {
        private String appealNumber;
        private String clauseCode;
        private String outcome;
        private String orderSummary;
        private String ground;
        private String correctionReason;
    }
}
