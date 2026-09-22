package com.hrms.cms.exception;

import lombok.Getter;

/**
 * Refuses a closure whose statutory communication requirements are not yet satisfied
 * (UST507-509, 520, 549, 764).
 *
 * <p>SERVER-SIDE ENFORCEMENT. The blocking previously existed ONLY in the browser: the closure dialog in
 * task-action.component.html disabled its button until a Date of Sending and a signed-letter file were both
 * supplied, and re-checked it in the component. But a grep of cms-backend for {@code dateOfSending} returned
 * NOTHING — the field was posted and silently dropped into an unread {@code Map<String,String>}, and the
 * uploaded file never left the browser at all. Any direct API call therefore closed a complaint with no
 * letter, no send date and no email, and the complainant was never told their case had ended. A browser-only
 * check is not a control.
 *
 * <p>Carries a {@code messageKey} rather than only English prose, following the
 * {@code UploadLinkActiveException} / {@code UnmappedClauseException} convention: the refusal is shown to
 * staff, must be translatable across the ten supported locales, and must be specific enough to act on.
 *
 * <p>{@code missingRequirement} names the single unmet condition so the UI can focus the right field
 * instead of re-deriving it.
 *
 * <p>Deliberately NOT an {@code IllegalArgumentException}: {@code WorkflowController} maps that to HTTP 200
 * with {@code success:false}, which would lose the message key and report a statutory refusal as a
 * successful call. This maps to 409 CONFLICT.
 */
@Getter
public class ClosureCommunicationIncompleteException extends RuntimeException {

    /** Date of Sending was not supplied. */
    public static final String MISSING_SEND_DATE = "SEND_DATE";

    /** No signed closure letter has been uploaded against the complaint. */
    public static final String MISSING_SIGNED_LETTER = "SIGNED_LETTER";

    /** The complainant has no email address on record, so the letter cannot be dispatched. */
    public static final String MISSING_EMAIL = "COMPLAINANT_EMAIL";

    private final String messageKey;
    private final String complaintNumber;
    private final String missingRequirement;

    public ClosureCommunicationIncompleteException(String messageKey, String message,
                                                   String complaintNumber, String missingRequirement) {
        super(message);
        this.messageKey = messageKey;
        this.complaintNumber = complaintNumber;
        this.missingRequirement = missingRequirement;
    }
}
