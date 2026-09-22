package com.hrms.cms.config;

/**
 * How pressing Send on a complaint email is completed, set by {@code cms.email.dispatch.mode}.
 */
public enum EmailDispatchMode {

    /**
     * Persist as PENDING and publish a dispatch request; cms-notification-service resolves the row to
     * SENT or FAILED. The real pipeline, and the default everywhere.
     */
    EVENT,

    /**
     * Mark the row SENT in the same request and publish nothing.
     *
     * <p>Exists for the dev-local quickstart, which is documented as needing no Kafka: without it a
     * developer running cms-backend alone would leave every sent mail stranded at PENDING. Nothing is
     * dispatched in either mode from this service - the difference is only whether a dispatch is
     * requested. Because this path is not the one production takes, integration testing must run with
     * EVENT.</p>
     */
    INLINE
}
