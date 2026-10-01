package com.rbi.cms.search.service;

/**
 * Raised when a user-initiated search could not be answered.
 *
 * <p>Deliberately distinct from a generic failure so the controller advice can map it to 503 with a
 * "search unavailable" body. A user-initiated search that fails must never surface as a 500, and
 * must never silently fall back to a SQL {@code LIKE '%...%'} scan — that fallback is what turns a
 * degraded Elasticsearch into a database outage.
 */
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(String message) {
        super(message);
    }

    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
