package com.rbi.cms.portal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Complaint tracking status for the public portal")
public class ComplaintStatusDto {

    @Schema(description = "Complaint reference number")
    private String complaintId;

    @Schema(description = "Current workflow status")
    private String status;

    @Schema(description = "Category of the complaint")
    private String category;

    @Schema(description = "Date complaint was registered")
    private Instant registeredAt;

    @Schema(description = "SLA target date")
    private Instant slaDueDate;

    @Schema(description = "Assigned team/officer if public-visible")
    private String assignedTeam;

    @Schema(description = "Resolution summary once resolved")
    private String resolutionSummary;

    @Schema(description = "Timeline of status transitions")
    private List<StatusTransitionDto> timeline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusTransitionDto {
        private String fromStatus;
        private String toStatus;
        private String action;
        private Instant timestamp;
    }
}
