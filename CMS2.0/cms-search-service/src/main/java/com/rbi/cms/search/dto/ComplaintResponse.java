package com.rbi.cms.search.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

@Builder
public record ComplaintResponse(
        Long complaintId,
        String complaintNumber,
        String assignedTo,
        String slaBreachIn,
        String mode,
        String complainantName,
        String status,
        String entityName,
        String complaintCategory,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate createdDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate lastUpdatedDate,
        String priority,
        String subject,
        String nodalOfficer,
        String principalNodalOfficer,
        String complaintColor,
        Boolean isRead,
        String slaColor,
        String statusColor
) {
}

