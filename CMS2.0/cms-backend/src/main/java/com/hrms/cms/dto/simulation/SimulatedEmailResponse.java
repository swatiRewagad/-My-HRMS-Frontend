package com.hrms.cms.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One message in a simulated email thread.
 *
 * <p>This is now the only shape a simulated email takes on the wire. The inbox and sent endpoints used
 * to serialize the {@code SimulatedEmail} entity directly, which published three further columns —
 * {@code complaintId}, {@code receivedAt} and {@code processedAt} — so the same concept had two wire
 * shapes depending on which endpoint you asked. Nothing read those three keys; {@code complaintNumber}
 * is the identifier callers actually use.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulatedEmailResponse {

    private Long id;
    private String messageId;
    private String threadId;
    private String fromEmail;
    private String toEmail;
    private String subject;
    private String body;
    /** {@code INBOUND} or {@code OUTBOUND}. */
    private String direction;
    /** Delivery state of this one message — {@code PROCESSED}, {@code SENT} or {@code UNREAD}. */
    private String status;
    private String complaintNumber;
    private String attachmentUrl;
    private LocalDateTime sentAt;
}
