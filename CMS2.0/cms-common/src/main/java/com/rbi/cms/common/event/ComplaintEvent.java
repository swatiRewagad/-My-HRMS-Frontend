package com.rbi.cms.common.event;

import com.rbi.cms.common.enums.ComplaintStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintEvent {

    private String eventId;
    private String complaintId;
    private ComplaintStatus previousStatus;
    private ComplaintStatus currentStatus;
    private String assignedTo;
    private String payload;
    private Instant occurredAt;
    private String correlationId;
}
