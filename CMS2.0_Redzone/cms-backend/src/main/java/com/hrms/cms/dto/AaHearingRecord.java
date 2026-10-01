package com.hrms.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One hearing event on an appeal: a scheduling, a rescheduling, an adjournment or an outcome.
 *
 * A DTO rather than an entity so the state machine and the frontend do not couple to S3C's persistence
 * model. History is a LIST of these because a hearing is not a single mutable date — the previous
 * sitting having been vacated is itself a fact a party may need to rely on, and the current code
 * overwrites hearingDate in place, destroying it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AaHearingRecord {

    private Long id;

    private String appealNumber;

    /** SCHEDULED | RESCHEDULED | ADJOURNED | COMPLETED | CANCELLED. */
    private String eventType;

    private LocalDateTime scheduledFor;

    private String venue;

    /** IN_PERSON | VIDEO | HYBRID. Video hearings are routine and need a joining detail, not a room. */
    private String mode;

    /** Free-text outcome, set only on COMPLETED. */
    private String outcome;

    private String remarks;

    /** Who the notice was addressed to, and whether a record of it exists. */
    private List<String> partiesNotified;

    private String performedBy;

    private String performedByRole;

    private LocalDateTime performedAt;
}
