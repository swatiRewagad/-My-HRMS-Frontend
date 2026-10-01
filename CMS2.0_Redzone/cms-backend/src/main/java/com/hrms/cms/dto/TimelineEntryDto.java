package com.hrms.cms.dto;

import com.hrms.cms.entity.ComplaintTimeline;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * THE canonical timeline entry shape (UST594-596).
 *
 * <p>Exists because the four existing read paths disagreed about field names for the same rows:
 * {@code ComplaintController} returned the raw entity ({@code performedAt}), {@code RePortalController}
 * returned a hand-built map omitting {@code eventSource}, {@code ComplaintApiV1Controller} renamed
 * {@code performedAt} to {@code timestamp} and dropped {@code performedBy} entirely, and
 * {@code AppealController} used a richer shape again. A shared renderer was therefore impossible —
 * {@code cepc-timeline.component.ts} binds {@code timestamp} and so silently shows nothing when
 * pointed at the other two.
 *
 * <p>Returning a DTO rather than the entity also closes the tamper surface UST597 cares about: the
 * previous {@code /timeline} endpoint handed callers the live managed entity.
 *
 * <p>Both {@code performedAt} and {@code timestamp} are serialised, with the same value. Not
 * duplication for its own sake — {@code cepc-timeline.component.ts} already binds {@code timestamp}
 * and is owned by another module, so emitting only one name would break a working screen that this
 * batch has no mandate to modify.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEntryDto {

    private Long id;
    private String action;
    private String performedBy;
    private String performedByRole;
    private String remarks;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime performedAt;

    /** Alias of performedAt, for the existing CEPC renderer. See the class note. */
    private LocalDateTime timestamp;

    /** MANUAL | AUTOMATIC — as a string so the JSON contract does not depend on the Java enum. */
    private String eventSource;

    private String fieldName;
    private String oldValue;
    private String newValue;
    private String closureClause;
    private String destinationOffice;

    public static TimelineEntryDto from(ComplaintTimeline t) {
        return TimelineEntryDto.builder()
                .id(t.getId())
                .action(t.getAction())
                .performedBy(t.getPerformedBy())
                .performedByRole(t.getPerformedByRole())
                .remarks(t.getRemarks())
                .fromStatus(t.getFromStatus())
                .toStatus(t.getToStatus())
                .performedAt(t.getPerformedAt())
                .timestamp(t.getPerformedAt())
                .eventSource(t.getEventSource() == null ? null : t.getEventSource().name())
                .fieldName(t.getFieldName())
                .oldValue(t.getOldValue())
                .newValue(t.getNewValue())
                .closureClause(t.getClosureClause())
                .destinationOffice(t.getDestinationOffice())
                .build();
    }
}
