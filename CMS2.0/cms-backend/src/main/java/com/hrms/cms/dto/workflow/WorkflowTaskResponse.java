package com.hrms.cms.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of any workflow task grid — shared by the RBIO/CEPC task, all-task, completed,
 * contact-person, my-actions and unassigned listings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTaskResponse {

    /**
     * The 6-digit CRPC draft id when this complaint was converted from one, otherwise the row's own
     * internal id. Always rendered as a string: the two sources have different Java types, and every
     * consumer treats it as an opaque identifier.
     */
    private String complaintId;
    private String complaintNumber;
    private String subject;
    private String complainantName;
    private String priority;
    private String status;
    private String assignedAt;
    private String slaDueDate;
    private String entityName;
    private String department;
    private String assignedRole;
    private String assignedOfficer;
    private String triageSignal;
    private boolean hasAttachments;
    /** True when the caller can see the complaint only because they acted on it previously. */
    private boolean viewOnly;
}
