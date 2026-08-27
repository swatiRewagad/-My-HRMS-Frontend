package com.rbi.cms.mailintake.spi;

/**
 * Outcome of one {@link InboundMailHandler#handle(NormalisedInboundMail)} call. A thrown
 * exception is treated identically to {@link #failure} by the dispatcher — handlers aren't
 * required to catch their own errors, but returning {@link #failure} lets a handler give a
 * cleaner error message than a raw stack trace would.
 */
public sealed interface HandlerResult {

    record Success(String linkedComplaintId) implements HandlerResult {}

    record Failure(String reason, boolean retryable) implements HandlerResult {}

    static HandlerResult success(String linkedComplaintId) {
        return new Success(linkedComplaintId);
    }

    static HandlerResult retryableFailure(String reason) {
        return new Failure(reason, true);
    }

    static HandlerResult permanentFailure(String reason) {
        return new Failure(reason, false);
    }
}
