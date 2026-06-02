package com.rbi.cms.ingestion.dto;

import com.rbi.cms.common.enums.ComplaintStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complaint registration acknowledgement")
public class ComplaintRegistrationResponse {

    @Schema(description = "Generated complaint ID", example = "CMP-20260525-000001")
    private String complaintId;

    @Schema(description = "Current status of the complaint")
    private ComplaintStatus status;

    @Schema(description = "Timestamp of registration")
    private Instant registeredAt;

    @Schema(description = "Acknowledgement message")
    private String acknowledgement;

    @Schema(description = "Expected SLA due date")
    private Instant slaDueDate;
}
