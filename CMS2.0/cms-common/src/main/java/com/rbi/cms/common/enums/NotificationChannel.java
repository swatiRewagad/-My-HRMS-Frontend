package com.rbi.cms.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * How an outbound notification is delivered. Carried on {@code NotificationDispatchEvent} so
 * cms-notification-service can route to the right handler.
 *
 * <p>Not to be confused with {@link Channel}, which records how a complaint <em>arrived</em>
 * ({@code WEB_PORTAL}, {@code EMAIL}, {@code API_CLIENT}). That is a different axis: {@code WEB_PORTAL} is
 * not a way to send anything, and adding {@code SMS} to it would corrupt the filing-channel domain.</p>
 *
 * <p>{@link #SMS} is defined before it is implemented, on purpose: it fixes the event schema now so
 * adding SMS later is one new handler component rather than a change to the event, the topic and the
 * consumer.</p>
 */
public enum NotificationChannel implements LabeledEnum {

    EMAIL("Email"),

    /** Declared but not yet dispatchable — no gateway integration exists. */
    SMS("SMS");

    private final String value;

    NotificationChannel(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return this.value;
    }

    /** Tolerant lookup by constant name or display label; empty rather than throwing for unknown input. */
    public static Optional<NotificationChannel> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (NotificationChannel channel : values()) {
            if (channel.name().equals(normalized)
                    || channel.value.toUpperCase(Locale.ROOT).replace(' ', '_').equals(normalized)) {
                return Optional.of(channel);
            }
        }
        return Optional.empty();
    }
}
