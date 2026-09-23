package com.rbi.cms.common.enums;

public enum ComplaintType {
    PORTAL("Portal"),
    EMAIL("Email"),
    LETTER("Letter");

    private final String value;

    ComplaintType(String value) { this.value = value; }

    public String getValue() { return this.value; }
}
