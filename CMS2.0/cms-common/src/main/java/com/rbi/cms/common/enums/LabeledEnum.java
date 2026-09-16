package com.rbi.cms.common.enums;

/**
 * Implemented by enums that carry a human-readable label alongside the constant name, so validators
 * and mappers can accept either form without reflecting over each enum's static helpers.
 */
public interface LabeledEnum {

    /** The display label, e.g. {@code "New Complaint"} for {@code NEW_COMPLAINT}. */
    String getValue();
}
