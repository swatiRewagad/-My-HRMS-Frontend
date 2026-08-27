package com.rbi.cms.mailintake.admin.dto;

import com.rbi.cms.mailintake.entity.AdminAction;

import java.time.Instant;

public record AdminActionDto(
        Long id,
        Long emailId,
        String actionType,
        String status,
        String targetComplaintId,
        String requestedBy,
        Instant requestedAt,
        String requestReason,
        String decidedBy,
        Instant decidedAt,
        String decisionNote
) {
    public static AdminActionDto from(AdminAction action) {
        return new AdminActionDto(
                action.getId(),
                action.getEmailId(),
                action.getActionType().name(),
                action.getStatus().name(),
                action.getTargetComplaintId(),
                action.getRequestedBy(),
                action.getRequestedAt(),
                action.getRequestReason(),
                action.getDecidedBy(),
                action.getDecidedAt(),
                action.getDecisionNote());
    }
}
