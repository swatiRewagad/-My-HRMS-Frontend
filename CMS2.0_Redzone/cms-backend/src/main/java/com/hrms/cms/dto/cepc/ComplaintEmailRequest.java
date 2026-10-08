package com.hrms.cms.dto.cepc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The compose form's body, for POST and PUT of {@code /api/v1/complaints/{cn}/emails}.
 *
 * <p><b>This replaces a contract nothing sent.</b> The endpoint previously read {@code recipients[]} and
 * a template id, while the only client posts {@code from/to/cc/bcc/subject/body/status}. Jackson
 * silently bound nothing, so {@code recipients} arrived empty on every send — and the service's own
 * "at least one recipient is required" refusal meant the officer's message was rejected as
 * addressed-to-nobody no matter what they typed.
 *
 * <p>{@code to}, {@code cc} and {@code bcc} are COMMA-SEPARATED STRINGS rather than arrays, matching both
 * the form and the columns they are stored in. Splitting them here and rejoining them on read would let the
 * separator convention drift between the two directions.
 *
 * <p>The patterns are the same address-list rule the form applies client-side, so a caller that bypasses the
 * form is held to the same constraint. Validation is deliberately identical for a DRAFT: the draft is stored
 * in the same columns, so accepting something here that cannot later be sent just moves the failure to the
 * point where the officer believes they are finished.
 */
public record ComplaintEmailRequest(

        @NotBlank(message = "From address is required.")
        @Pattern(regexp = EMAIL_LIST, message = "From must be a valid email address.")
        @Size(max = 200, message = "The from address must be at most 200 characters.")
        String from,

        @NotBlank(message = "At least one recipient is required.")
        @Pattern(regexp = EMAIL_LIST,
                message = "To must be a valid email address, or several separated by commas.")
        @Size(max = 200, message = "The recipient list must be at most 200 characters.")
        String to,

        @Pattern(regexp = EMAIL_LIST_OR_EMPTY,
                message = "CC must be a valid email address, or several separated by commas.")
        @Size(max = 1000, message = "The CC list must be at most 1000 characters.")
        String cc,

        @Pattern(regexp = EMAIL_LIST_OR_EMPTY,
                message = "BCC must be a valid email address, or several separated by commas.")
        @Size(max = 1000, message = "The BCC list must be at most 1000 characters.")
        String bcc,

        @NotBlank(message = "Subject is required.")
        @Size(max = 500, message = "The subject must be at most 500 characters.")
        String subject,

        @NotBlank(message = "The email body cannot be empty.")
        @Size(max = 20000, message = "The email body must be at most 20000 characters.")
        String body,

        /**
         * DRAFT to hold it, or SENT to dispatch it.
         *
         * <p>SENT here is a REQUEST to send, not an assertion that it went out — the server answers with
         * PENDING, SENT or FAILED according to what the dispatch actually did. A client cannot mark its own
         * mail delivered.
         */
        String status,

        /** The message being replied to or forwarded, so the thread and the quoted original are kept. */
        Long inReplyToId,

        /**
         * IDs of documents already uploaded to this complaint (via {@code /api/files/upload}) that should
         * travel with this message. Null/empty means no attachments — the compose form's "Add Attachment"
         * button uploads first and posts back only the resulting ids, so the write path never receives
         * file bytes directly.
         */
        List<Long> attachmentIds
) {

    /** One or more addresses separated by commas or semicolons. */
    static final String EMAIL_LIST =
            "\\s*[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}\\s*([,;]\\s*[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}\\s*)*";

    /** The same, but an absent optional field is not a violation. */
    static final String EMAIL_LIST_OR_EMPTY = "|\\s*|" + EMAIL_LIST;

    /** True when the caller asked for dispatch rather than to hold a draft. */
    public boolean wantsDispatch() {
        return !"DRAFT".equalsIgnoreCase(status == null ? "" : status.trim());
    }
}
