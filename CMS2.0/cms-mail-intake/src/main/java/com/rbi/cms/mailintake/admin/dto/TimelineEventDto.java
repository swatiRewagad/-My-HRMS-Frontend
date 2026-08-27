package com.rbi.cms.mailintake.admin.dto;

import com.rbi.cms.mailintake.entity.InboundEmailEvent;

import java.time.Instant;

public record TimelineEventDto(
        Instant eventAt,
        String fromStatus,
        String toStatus,
        String actor,
        String detail
) {
    public static TimelineEventDto from(InboundEmailEvent event) {
        return new TimelineEventDto(
                event.getEventAt(),
                event.getFromStatus() == null ? null : event.getFromStatus().name(),
                event.getToStatus().name(),
                event.getActor(),
                event.getDetail());
    }
}
