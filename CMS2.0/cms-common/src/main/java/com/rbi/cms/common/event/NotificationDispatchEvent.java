package com.rbi.cms.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rbi.cms.common.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A request to dispatch one outbound notification, published to {@code notification.requested} by
 * cms-backend and consumed by cms-notification-service.
 *
 * <p>A command rather than a fact — unlike {@link ComplaintEvent}, which announces something that already
 * happened. That is also why it is a separate class: {@code ComplaintEvent}'s typed fields are
 * complaint-status-shaped, so reusing it would mean nulling most of them and smuggling the dispatch
 * details through its opaque JSON {@code payload} string, where no compile-time contract exists.</p>
 *
 * <p><strong>Carries no message content</strong> — no body, subject, or recipients, only
 * {@link #recordId}. Three reasons: officer-composed mail on an ombudsman complaint contains complainant
 * PII, which would otherwise sit in Kafka log segments for the whole retention window and be copied
 * verbatim into the dead-letter topic on failure; the database then holds exactly one copy of the body,
 * so a redelivery after an edit cannot send stale text; and {@code {channel, recordId}} works unchanged
 * for SMS, whereas an event carrying mail fields would not.</p>
 *
 * <p>The consumer therefore requires database reachability to dispatch. That is already true, since it
 * must write the outcome back.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationDispatchEvent {

    private String eventId;

    /** Which handler should dispatch this. An unrecognised value is not retryable. */
    private NotificationChannel channel;

    /** Primary key of the row holding the content — {@code SIMULATED_EMAILS.ID} for {@code EMAIL}. */
    private Long recordId;

    /** Kafka message key, so all notifications for one complaint keep their relative order. */
    private String complaintNumber;

    /** Username of the officer who pressed Send; the only actor attribution available downstream. */
    private String requestedBy;

    private Instant occurredAt;

    private String correlationId;
}
