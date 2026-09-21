package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The Conciliation tab: the live meeting plus the reschedule trail behind it.
 *
 * <p>{@link #current} is null when no meeting has been scheduled yet, and the key must stay present in
 * that case — so no {@code @JsonInclude(NON_NULL)} here, matching the {@code LinkedHashMap} this
 * replaced.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbioConciliationResponse {

    private Long complaintId;
    private String complaintNumber;

    /** The newest meeting, which is the one a save edits in place. Null until the first is scheduled. */
    private Meeting current;

    /** Oldest first, and includes {@link #current}. */
    @Builder.Default
    private List<Meeting> history = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meeting {
        private Long id;
        /** One of SCHEDULED, RESCHEDULED, COMPLETED, CANCELLED. */
        private String meetingStatus;
        /** Kept as a String rather than a date type to preserve the exact text the panel already parses. */
        private String meetingDate;
        /** HH:mm — Oracle has no TIME type, so this column is text. */
        private String meetingTime;
        private Boolean acceptedByComplainant;
        private Boolean acceptedByEntity;
        private Boolean conductedThroughVc;
        private String meetingComments;
        private String comments;
        private String createdBy;
        private String createdAt;
        private String updatedBy;
        private String updatedAt;
    }
}
