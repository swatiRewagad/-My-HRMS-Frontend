package com.hrms.cms.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A thread as it appears in a list, without its messages.
 *
 * <p><strong>{@link #status} carries two different vocabularies depending on which endpoint produced
 * the row, and that is deliberate.</strong> The simulation inbox listing reports a <em>thread</em>
 * status ({@code AWAITING_FORM} or {@code COMPLETED}) derived from how many inbound messages have
 * arrived. The per-complaint listing reports the <em>first message's</em> delivery status
 * ({@code PROCESSED} or {@code SENT}) instead. Unifying them would be wrong: the complaint details
 * screen buckets its email activity on {@code SENT}, so giving it the derived thread status would empty
 * that bucket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailThreadSummary {

    private String threadId;
    private String complaintNumber;
    /** Sender of the earliest message in the thread. */
    private String fromEmail;
    /** Subject of the earliest message in the thread. */
    private String subject;
    /** When the earliest message in the thread was sent. */
    private LocalDateTime sentAt;
    private int emailCount;
    /** See the class comment — the vocabulary depends on the endpoint. */
    private String status;
}
