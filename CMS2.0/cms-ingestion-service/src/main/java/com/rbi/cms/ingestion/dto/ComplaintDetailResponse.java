package com.rbi.cms.ingestion.dto;

import com.rbi.cms.common.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed complaint information")
public class ComplaintDetailResponse {

    private String complaintId;
    private Channel channel;
    private ComplaintCategory category;
    private ComplaintStatus status;
    private Priority priority;
    private String complainantName;
    private String complainantEmail;
    private String complainantPhone;
    private String entityName;
    private String entityType;
    private String subject;
    private String description;
    private Double amountInvolved;
    private LocalDate transactionDate;
    private String jurisdictionCode;
    private String assignedTo;
    private String assignedTeam;
    private Instant slaDueDate;
    private String resolutionSummary;
    private Instant resolvedAt;
    private Instant closedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AttachmentResponse> attachments;
    private List<TimelineEntry> timeline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEntry {
        private String fromStatus;
        private String toStatus;
        private String action;
        private String remarks;
        private Instant timestamp;
    }
}
