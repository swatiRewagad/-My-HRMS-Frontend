package com.rbi.cms.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.enums.ComplaintStatusDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComplaintEvent {

    private String eventId;
    private String complaintId;
    @JsonDeserialize(using = ComplaintStatusDeserializer.class)
    private ComplaintStatus previousStatus;
    @JsonDeserialize(using = ComplaintStatusDeserializer.class)
    private ComplaintStatus currentStatus;
    private String assignedTo;
    private String payload;
    private Instant occurredAt;
    private String correlationId;
}
