package com.hrms.cms.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A thread with all of its messages.
 *
 * <p>One type serves both opening an existing thread and the result of receiving a new email or a form
 * reply. The two POST results used to omit {@link #fromEmail} and {@link #subject}, even though the
 * simulation screen renders a POST result and a fetched thread through the same view — so composing an
 * email left the thread header blank until the thread was reopened. Both are now always populated from
 * the earliest message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailThreadResponse {

    private String threadId;
    private String complaintNumber;
    private String fromEmail;
    private String subject;
    /** Thread-level state: {@code AWAITING_FORM} until a second inbound message arrives, then {@code COMPLETED}. */
    private String status;

    @Builder.Default
    private List<SimulatedEmailResponse> emails = List.of();
}
