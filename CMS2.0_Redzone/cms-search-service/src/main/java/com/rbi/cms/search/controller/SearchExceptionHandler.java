package com.rbi.cms.search.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.search.service.SearchUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class SearchExceptionHandler {

    /**
     * 503, not 500. A user-initiated search that Elasticsearch could not answer is a degraded
     * dependency, not a bug in this service: the caller should be told search is unavailable and
     * should retry, and the UI should say so rather than showing an error page. A 500 also gets
     * counted as a service defect by every dashboard in the stack, which hides the real signal.
     */
    @ExceptionHandler(SearchUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> onSearchUnavailable(SearchUnavailableException e) {
        log.warn("Search unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(ApiResponse.error("Search is temporarily unavailable. Please retry shortly."));
    }

    /**
     * An open circuit or a full bulkhead reaches here as the Resilience4j exception rather than as a
     * {@link SearchUnavailableException}, because Resilience4j short-circuits before the guarded
     * method runs at all.
     */
    @ExceptionHandler({
            io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
            io.github.resilience4j.bulkhead.BulkheadFullException.class})
    public ResponseEntity<ApiResponse<Void>> onSearchRejected(Exception e) {
        log.warn("Search call rejected by resilience guard: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(ApiResponse.error("Search is temporarily unavailable. Please retry shortly."));
    }
}
