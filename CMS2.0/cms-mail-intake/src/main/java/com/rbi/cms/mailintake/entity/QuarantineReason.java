package com.rbi.cms.mailintake.entity;

public enum QuarantineReason {
    /** Sender-resolution chain ran to the end without a confident match. */
    UNRESOLVED_ORIGINAL_SENDER,
    /** Our own X-CMS-Loop-Guard header came back to us. */
    LOOP_DETECTED,
    /** Attachment scanner (ClamAV or the no-op default) flagged infected content. */
    ATTACHMENT_INFECTED,
    /** Attachment count/size limits exceeded. */
    ATTACHMENT_LIMIT_EXCEEDED,
    /** Decompression ratio or nesting depth guard tripped. */
    ZIP_BOMB_SUSPECTED,
    /** MIME structure too damaged to parse even with the tolerant parser. */
    UNPARSEABLE_MESSAGE,
    /** FAILED retried past cms.mail.intake.retry.max-attempts. */
    MAX_ATTEMPTS_EXCEEDED,
    /** Operator-initiated via the admin replay/quarantine endpoints. */
    MANUAL,
    OTHER
}
