package com.hrms.cms.exception;

import lombok.Getter;

/**
 * Refuses a closure or a restricted forward while a secure upload link is still live (UST603-604).
 *
 * <p>SERVER-SIDE ENFORCEMENT. This blocking previously existed ONLY in the browser —
 * {@code isClosureBlocked()} / {@code isForwardingRestricted()} in rbio-complaint-detail.component.ts,
 * rendering a lock icon that did not even disable the click handler. A grep of cms-backend for
 * "UploadLink" found only the controller and the expiry scheduler, so any client could close a
 * complaint straight through the API while the complainant was still uploading the documents they had
 * been asked for. A browser-only check is not a control.
 *
 * <p>Carries a {@code messageKey} rather than only English prose, following the
 * {@code UnmappedClauseException} / {@code AaHearingOrderController.error(...)} convention: the refusal
 * is shown to staff and must be translatable, and the reason must be specific enough to act on.
 *
 * <p>Deliberately NOT an {@code IllegalArgumentException}: {@code WorkflowController} maps that to
 * HTTP 200 with {@code success:false}, which would lose the message key and report a refusal as a
 * successful call. This maps to 409 CONFLICT.
 */
@Getter
public class UploadLinkActiveException extends RuntimeException {

    private final String messageKey;
    private final String complaintNumber;
    private final String linkExpiresAt;

    public UploadLinkActiveException(String messageKey, String message, String complaintNumber,
                                     String linkExpiresAt) {
        super(message);
        this.messageKey = messageKey;
        this.complaintNumber = complaintNumber;
        this.linkExpiresAt = linkExpiresAt;
    }
}
