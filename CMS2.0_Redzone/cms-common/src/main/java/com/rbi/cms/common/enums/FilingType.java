package com.rbi.cms.common.enums;

public enum FilingType implements LabeledEnum {

    PORTAL("Portal"),
    EMAIL("Email"),
    LETTER("Letter");

    private final String value;

    FilingType(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static String getKeyFromValue(String value) {
        for (FilingType type : FilingType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type.name();
            }
        }
        throw new IllegalArgumentException("No FilingType key found for value: " + value);
    }

    public static String getValueFromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        try {
            return FilingType.valueOf(key.toUpperCase().trim()).getValue();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No FilingType value found for key: " + key);
        }
    }
}
