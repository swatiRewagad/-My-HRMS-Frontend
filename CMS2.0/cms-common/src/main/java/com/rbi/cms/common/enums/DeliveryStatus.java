package com.rbi.cms.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * Lifecycle of an outbound notification — today a mail composed by an officer against a complaint,
 * later an SMS. Stored in {@code SIMULATED_EMAILS.DELIVERY_STATUS}.
 *
 * <pre>
 * DRAFT ──send──&gt; PENDING ──dispatched──&gt; SENT
 *                    │
 *                    └──dispatch failed──&gt; FAILED ──retry──&gt; PENDING
 * </pre>
 *
 * <p>Deliberately separate from the legacy {@code SIMULATED_EMAILS.STATUS} column, which is overloaded
 * with inbound values ({@code UNREAD}, {@code PROCESSED}, {@code RECEIVED}) and therefore cannot be
 * typed as this enum without failing to read existing rows.</p>
 *
 * <p>This enum carries no transition logic. The only legal transitions are the four above, and each is
 * enforced where it happens: {@code DRAFT -> PENDING} and {@code FAILED -> PENDING} by the guards in
 * cms-backend's complaint email endpoints, and {@code PENDING -> SENT|FAILED} by a conditional UPDATE in
 * cms-notification-service whose {@code WHERE ... AND DELIVERY_STATUS = 'PENDING'} clause is the guard.
 * That conditional UPDATE is also what makes Kafka redelivery idempotent — a second attempt affects
 * zero rows.</p>
 */
public enum DeliveryStatus implements LabeledEnum {

    /** Saved but not sent. The only state in which the mail may still be edited. */
    DRAFT("Draft"),

    /** Queued for dispatch: the row is persisted and an event published, but nothing has been sent yet. */
    PENDING("Pending"),

    /** Dispatched successfully, or recorded as dispatched while running in simulate mode. */
    SENT("Sent"),

    /** Dispatch was attempted and failed; see the row's last error. Retryable. */
    FAILED("Failed");

    private final String value;

    DeliveryStatus(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return this.value;
    }

    /**
     * Tolerant lookup accepting either a constant name or a display label, in any case and with spaces or
     * underscores as the word separator.
     *
     * <p>Returning empty rather than throwing is load-bearing here, and has three callers that need it:
     * rows written before {@code DELIVERY_STATUS} existed hold {@code null} and fall back to the legacy
     * {@code STATUS} string; cms-notification-service reads the column as a raw JDBC {@code String};
     * and that legacy {@code STATUS} column holds values which are not lifecycle states at all
     * ({@code UNREAD}, {@code PROCESSED}, {@code RECEIVED}), for which "not a delivery status" is the
     * correct answer rather than an error.</p>
     */
    public static Optional<DeliveryStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (DeliveryStatus status : values()) {
            if (status.name().equals(normalized)
                    || status.value.toUpperCase(Locale.ROOT).replace(' ', '_').equals(normalized)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
