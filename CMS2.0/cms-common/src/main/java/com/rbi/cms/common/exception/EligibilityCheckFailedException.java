package com.rbi.cms.common.exception;

import org.springframework.http.HttpStatus;

public class EligibilityCheckFailedException extends CmsException {

    public EligibilityCheckFailedException(String reason) {
        super(String.format("Eligibility check failed: %s", reason),
                HttpStatus.UNPROCESSABLE_ENTITY, "ELIGIBILITY_FAILED");
    }
}
