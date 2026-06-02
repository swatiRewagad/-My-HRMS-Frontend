package com.rbi.cms.portal.dto.response;

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
@Schema(description = "Acknowledgement returned after successful complaint registration")
public class ComplaintAcknowledgementDto {

    @Schema(description = "Generated complaint reference ID", example = "CMP-20260525-000001")
    private String complaintId;

    @Schema(description = "Current status", example = "NEW")
    private String status;

    @Schema(description = "Timestamp of registration")
    private Instant registeredAt;

    @Schema(description = "Expected resolution date based on SLA")
    private Instant slaDueDate;

    @Schema(description = "Acknowledgement message for the user")
    private String acknowledgementMessage;
}
