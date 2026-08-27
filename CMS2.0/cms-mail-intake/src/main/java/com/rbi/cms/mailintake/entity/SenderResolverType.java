package com.rbi.cms.mailintake.entity;

/**
 * Which resolver in the ordered sender-resolution chain (Stage 4) produced the original sender —
 * recorded on {@code inbound_email.resolved_by} and in the audit trail so a wrong resolution can
 * be traced back to the rule that made it.
 */
public enum SenderResolverType {
    REDIRECT_HEADERS,
    CUSTOM_HEADER,
    NESTED_MESSAGE,
    TNEF,
    INLINE_FORWARD_BLOCK,
    UNRESOLVED
}
