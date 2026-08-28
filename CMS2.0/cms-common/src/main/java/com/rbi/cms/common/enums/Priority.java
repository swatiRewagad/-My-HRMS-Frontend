package com.rbi.cms.common.enums;

public enum Priority {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical");

    private final String value;

    Priority(String value) { this.value = value; }

    public String getValue() { return this.value; }
}
