package com.rbi.cms.common.enums;

import java.util.Locale;
import java.util.Optional;

public enum Priority implements LabeledEnum {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical");

    private final String value;

    Priority(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static String getKeyFromValue(String value) {
        for (Priority priority : Priority.values()) {
            if (priority.value.equalsIgnoreCase(value)) {
                return priority.name();
            }
        }
        throw new IllegalArgumentException("No Priority key found for value: " + value);
    }

    public static String getValueFromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        try {
            return Priority.valueOf(key.toUpperCase().trim()).getValue();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No Priority value found for key: " + key);
        }
    }

    public static Optional<Priority> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (Priority priority : values()) {
            if (priority.name().equals(normalized)
                    || priority.value.toUpperCase(Locale.ROOT).replace(' ', '_').equals(normalized)) {
                return Optional.of(priority);
            }
        }
        return Optional.empty();
    }
}
