package com.hrms.cms.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowActionResponse {

    private String complaintNumber;
    private String action;
    private String newStatus;
    private String assignedRole;
    private String assignedOfficer;

    /**
     * Adapts the untyped result of {@code RbioWorkflowService}/{@code CepcWorkflowService}
     * {@code performAction}. Those services still return {@code Map<String, Object>} because
     * {@code AppealWorkflowService.performAction} shares the shape and is out of this refactor's
     * scope — retyping two of the three would leave the family inconsistent.
     */
    public static WorkflowActionResponse from(Map<String, Object> result) {
        return WorkflowActionResponse.builder()
                .complaintNumber((String) result.get("complaintNumber"))
                .action((String) result.get("action"))
                .newStatus((String) result.get("newStatus"))
                .assignedRole((String) result.get("assignedRole"))
                .assignedOfficer((String) result.get("assignedOfficer"))
                .build();
    }
}
