package com.hrms.cms.config;

import com.hrms.cms.exception.UploadLinkActiveException;
import com.hrms.cms.service.AppealClassificationService;
import com.hrms.cms.service.ClauseConfigurationAlertService;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Optional on purpose. This advice is a @RestControllerAdvice, so it is loaded into every
     * @WebMvcTest slice — but a @Service is not, and a mandatory constructor dependency therefore made
     * every slice context fail to start. Setter injection with required=false keeps the advice
     * constructible everywhere; the alert is a side effect of the error response, never a condition of
     * producing it.
     */
    private ClauseConfigurationAlertService clauseConfigurationAlertService;

    @Autowired(required = false)
    public void setClauseConfigurationAlertService(ClauseConfigurationAlertService service) {
        this.clauseConfigurationAlertService = service;
    }

    /**
     * Preserves the status an explicitly-thrown {@link ResponseStatusException} carries.
     *
     * Without this, the RuntimeException handler below caught these too and rewrote every one as 400.
     * That mattered most for authorization: the role-guard aspects throw 403 FORBIDDEN, but a client
     * saw "400 Bad Request" — indistinguishable from a malformed payload, so a denied request looked
     * like a client mistake and could be neither detected nor alerted on.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (status.is4xxClientError()) {
            log.warn("Request rejected with {}: {}", status.value(), ex.getReason());
        } else {
            log.error("Request failed with {}: {}", status.value(), ex.getReason());
        }
        return buildResponse(status, ex.getReason() == null ? status.getReasonPhrase() : ex.getReason());
    }

    /**
     * Authorization failures raised outside the AOP guards — the entity-scope checks in the query and
     * RE-portal services throw SecurityException — must also reach the client as 403, not 400.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * A closure clause that is absent from CLOSURE_CLAUSE_MASTER, so an escalation cannot be
     * classified as an Appeal or a Representation.
     *
     * Failing closed here is deliberate and correct — guessing would risk denying a citizen statutory
     * recourse. But without this handler the generic catch turned it into a bare 500 "An unexpected
     * error occurred", which tells the citizen nothing and looks like a transient fault they should
     * retry identically forever. It is not transient: it is missing configuration, and only an AA
     * Admin can resolve it.
     *
     * 503 rather than 500 because the flow genuinely is retryable once the clause is configured, and
     * the body carries a translation key so the portal renders it in the citizen's own language.
     */
    @ExceptionHandler(AppealClassificationService.UnmappedClauseException.class)
    public ResponseEntity<Map<String, Object>> handleUnmappedClause(
            AppealClassificationService.UnmappedClauseException ex) {
        log.error("Closure clause '{}' is not configured for scheme {} — escalation blocked",
                ex.getClauseCode(), ex.getSchemeVersion(), ex);

        if (clauseConfigurationAlertService != null) {
            clauseConfigurationAlertService.raiseUnmappedClauseAlert(ex.getClauseCode(), ex.getSchemeVersion());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "This complaint's closure clause is not yet configured, so we cannot"
                + " determine whether it may be appealed. Our team has been notified — please try again"
                + " shortly.");
        response.put("messageKey", "appeal.error_clause_not_configured");
        response.put("retryable", true);
        response.put("clauseCode", ex.getClauseCode());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * A closure or restricted forward was refused because a secure upload link is still live
     * (UST603-604).
     *
     * <p>409 CONFLICT rather than 400: the request is well-formed and the caller is authorised — it
     * conflicts with the CURRENT STATE of the complaint, and becomes valid once the link lapses or is
     * revoked. Carries a translation key so the refusal renders in the officer's own language, plus the
     * link expiry so they know when the block lifts rather than having to guess.
     */
    @ExceptionHandler(UploadLinkActiveException.class)
    public ResponseEntity<Map<String, Object>> handleUploadLinkActive(UploadLinkActiveException ex) {
        log.warn("Action refused for complaint {} — secure upload link active until {}",
                ex.getComplaintNumber(), ex.getLinkExpiresAt());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", ex.getMessage());
        response.put("messageKey", ex.getMessageKey());
        response.put("complaintNumber", ex.getComplaintNumber());
        response.put("linkExpiresAt", ex.getLinkExpiresAt());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * A concurrent edit lost the race (UST675 record locking).
     *
     * <p>Spring dispatches on the most specific exception type, not on declaration order, so this wins
     * over the RuntimeException handler below. That distinction is the reason the handler exists:
     * {@code OptimisticLockingFailureException} IS a RuntimeException, so without this it fell into the
     * generic 400 and became indistinguishable from a validation error — the UI could only tell the
     * officer their input was bad, when in fact their input was fine and a colleague had saved first.
     *
     * <p>409 CONFLICT is the point: it is the one status meaning "reload, then retry", which is the only
     * recovery available. {@code retryable} is true only AFTER a reload — repeating the identical request
     * with the same stale version will conflict again.
     *
     * <p>{@code OptimisticLockingFailureException} rather than its
     * {@code ObjectOptimisticLockingFailureException} subclass so that the non-Hibernate and
     * hand-thrown Spring-data variants are covered too.
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(Exception ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "This record was changed by someone else while you were working on it."
                + " Please reload to see the latest version before saving again.");
        response.put("messageKey", "common.error_record_changed");
        response.put("retryable", true);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.warn("Runtime exception: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds the allowed limit");
    }

    /**
     * A malformed or incomplete request is the caller's error, so it must not be reported as 500.
     * These previously fell through to the catch-all Exception handler and surfaced as "Internal
     * Server Error", which hid the fact that the request itself was wrong.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class,
                       MissingServletRequestPartException.class,
                       MethodArgumentTypeMismatchException.class,
                       HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        log.warn("Malformed request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * A request for a path with no handler is a 404, not a server fault.
     *
     * Spring routes an unmatched /api/** path to the static-resource handler, which throws
     * NoResourceFoundException; that fell through to the catch-all below and was reported as 500
     * "An internal error occurred". A missing route then looks like a broken server, and the noise
     * buries genuine 500s — 21 of these appeared in one E2E run from a single absent endpoint.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        log.warn("No handler for {}", ex.getResourcePath());
        return buildResponse(HttpStatus.NOT_FOUND, "The requested resource does not exist.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
