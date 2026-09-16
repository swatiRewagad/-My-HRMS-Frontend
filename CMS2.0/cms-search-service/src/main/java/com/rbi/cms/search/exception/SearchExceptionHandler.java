package com.rbi.cms.search.exception;

import com.rbi.cms.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Module-local advice, ordered ahead of {@code cms-common}'s {@code GlobalExceptionHandler}.
 *
 * <p>The precedence is load-bearing, not stylistic: the shared advice has an
 * {@code @ExceptionHandler(Exception.class)} catch-all that would otherwise claim every exception
 * below and report it as a 500 — turning an authorization failure into a server error and a bad
 * request into an outage-looking one.
 *
 * <p>Additive rather than an edit to the shared advice, which eight other services depend on.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SearchExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        SearchExceptionHandler::messageOf,
                        (first, second) -> first));

        log.warn("Rejected request to {}: {}", request.getRequestURI(), errors);
        return badRequest("Validation failed", errors, request);
    }

    /**
     * Covers a body Jackson could not bind at all — most often a date that does not match the
     * {@code dd-MM-yyyy} contract, or an enum-typed field with unknown text.
     *
     * <p>The parser message is logged but never returned: it echoes the submitted payload and the
     * internal type and field names, which does not help a caller and does leak structure.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed request body on {}", request.getRequestURI(), ex);
        return badRequest("Malformed request body. Check field types and that dates are dd-MM-yyyy.",
                Map.of(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "the expected type";
        log.warn("Type mismatch for parameter '{}' on {}", ex.getName(), request.getRequestURI());
        return badRequest("Invalid request parameter",
                Map.of(ex.getName(), "Value is not a valid " + expected), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message("You are not authorized to perform this action.")
                        .correlationId(correlationId(request))
                        .build());
    }

    /**
     * A search backend failure is not the caller's fault, so it must not read as one. 502 also keeps
     * it distinguishable from a bug in this service in dashboards and alerting.
     */
    @ExceptionHandler({OpenSearchException.class, IOException.class})
    public ResponseEntity<ApiResponse<Void>> handleSearchBackendFailure(
            Exception ex, HttpServletRequest request) {

        log.error("Search backend failure on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message("The search service is temporarily unavailable. Please retry.")
                        .correlationId(correlationId(request))
                        .build());
    }

    private ResponseEntity<ApiResponse<Map<String, String>>> badRequest(
            String message, Map<String, String> errors, HttpServletRequest request) {

        return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message(message)
                        .data(errors)
                        .correlationId(correlationId(request))
                        .build());
    }

    private static String messageOf(ConstraintViolation<?> violation) {
        return violation.getMessage() != null ? violation.getMessage() : "Invalid value";
    }

    private static String correlationId(HttpServletRequest request) {
        return request.getHeader("X-Correlation-ID");
    }
}
