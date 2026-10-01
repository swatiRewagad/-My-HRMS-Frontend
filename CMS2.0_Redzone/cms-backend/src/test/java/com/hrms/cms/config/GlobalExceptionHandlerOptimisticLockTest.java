package com.hrms.cms.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stale write must surface as 409, not as the generic 400 an unmapped RuntimeException produces.
 * S7 builds the UST675 record-locking UX on this status, so a 400 here would make a conflict
 * indistinguishable from a validation error.
 */
class GlobalExceptionHandlerOptimisticLockTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimisticLockFailureIsConflictNotBadRequest() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleOptimisticLock(new OptimisticLockingFailureException("stale"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("messageKey", "common.error_record_changed");
        assertThat(response.getBody()).containsEntry("retryable", true);
    }

    @Test
    void jakartaOptimisticLockExceptionIsAlsoConflict() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleOptimisticLock(new jakarta.persistence.OptimisticLockException("stale"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** The generic handler must still answer 400, so the two are genuinely distinguishable. */
    @Test
    void plainRuntimeExceptionRemainsBadRequest() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleRuntimeException(new RuntimeException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
