package com.rbi.cms.common.enums;

/**
 * An enum constant that carries a human-readable label alongside its name, so a UI can render the
 * label without holding its own copy of the mapping.
 */
public interface LabeledEnum {

    /** The display label, e.g. {@code "Pending Office Head Approval"}. */
    String getValue();
}
