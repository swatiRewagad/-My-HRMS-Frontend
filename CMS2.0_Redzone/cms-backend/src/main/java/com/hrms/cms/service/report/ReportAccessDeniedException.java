package com.hrms.cms.service.report;

/**
 * The caller may not run or export this report (UST615, UST669, UST670).
 *
 * <h2>Why a distinct type rather than SecurityException</h2>
 * {@code SecurityException} is already used in this package for "that field is not in the semantic
 * model", which is a malformed-query condition and not an authorisation one. Conflating them would mean
 * the handler could not tell a caller who asked for something impossible from a caller who asked for
 * something they may not have — and those need different HTTP statuses and different messages. A user
 * told "field not allowed" when the real problem is their role cannot see reports at all has no idea
 * what to do next.
 *
 * <p>Mapped to 403 by {@code GlobalExceptionHandler}.
 */
public class ReportAccessDeniedException extends RuntimeException {

    public ReportAccessDeniedException(String message) {
        super(message);
    }
}
