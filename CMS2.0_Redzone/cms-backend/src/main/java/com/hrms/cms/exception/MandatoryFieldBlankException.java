package com.hrms.cms.exception;

import lombok.Getter;

/**
 * A mandatory field was submitted present-but-blank, which is an attempt to clear a required value
 * rather than to leave it untouched.
 *
 * <p>Carries the message the CEPC edit screen is specified to show verbatim, so the portal can render
 * the refusal without inventing its own wording.
 */
@Getter
public class MandatoryFieldBlankException extends RuntimeException {

    private final String field;

    public MandatoryFieldBlankException(String field) {
        super("Please fill mandatory fields");
        this.field = field;
    }
}
