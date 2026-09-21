package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The RBIO complaint detail screen's entire payload. Deliberately not {@code @JsonInclude(NON_NULL)}:
 * most of these fields are unset on a freshly registered complaint and the screen binds to them
 * directly, so the keys have to keep appearing with a null value as they did when this was a
 * {@code LinkedHashMap}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintDetailResponse {

    private Long id;
    private String complaintId;
    private String complaintNumber;
    private String category;
    private String priority;
    private String status;
    private String subject;
    private String description;

    private String complainantName;
    private String complainantEmail;
    private String complainantPhone;
    private String complainantAddress;
    private String complainantState;
    private String complainantDistrict;
    private String complainantPincode;

    private String entityName;
    private Long regulatedEntityId;
    private String entityType;
    private String entityCategory;
    private String bsrCode;
    private String entityPincode;
    private String entityState;
    private String entityDistrict;
    private String entityCity;
    private String entityBranchName;
    private String entityBranchCategory;
    private String entityAddress;
    private String cosmosCode;
    private String schemeVersion;

    private BigDecimal amountInvolved;
    private String transactionDate;

    private String assignedTeam;
    /**
     * Stays a username: the detail screen compares it against the logged-in user to decide whether the
     * complaint is editable or read-only, which a display name cannot answer. {@link #assignedToName}
     * is what the screen should render.
     */
    private String assignedTo;
    private String assignedToName;

    private String registeredAt;
    private String createdAt;
    private String slaDueDate;
    /** Never populated yet; the key is kept so the screen's binding does not become undefined. */
    private String resolutionSummary;
    private String resolvedAt;

    private List<ComplaintTimelineItem> timeline;
    /** Served from the dedicated emails and attachments endpoints, not from here. */
    @Builder.Default
    private List<Object> communications = List.of();
    @Builder.Default
    private List<Object> documents = List.of();

    private String triageSignal;
    private String triageFlags;
    private String eligibilityTimeline;

    private String closureClause;
    private String proposedAction;
    private String proposedClause;
    private String forwardedOfficeCode;
    private String forwardedOfficeName;
    private String preForwardOfficer;
    private String preForwardRole;
    private String closureClauseDescription;
    private String complaintStatusOnPortal;
    private String speakingOrderGenerated;
    private String gistOfCase;
    private String gistOfCaseRegional;
}
