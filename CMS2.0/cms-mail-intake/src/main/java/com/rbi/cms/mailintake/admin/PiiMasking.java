package com.rbi.cms.mailintake.admin;

/** Same masking shape as {@code LoggingInboundMailHandler}'s private helper (not shared directly
 *  — that one is log-line-specific and untouched here to avoid risking the already-verified Stage
 *  4 handler), used for the admin "list" endpoint's redacted metadata (brief: "list quarantined
 *  mail (redacted metadata)"). The full, unmasked address is still available from the "view one
 *  item" and "download raw" endpoints — both individually audited — so redaction here is about
 *  not exposing complainant PII in a bulk list view, not about hiding it from admins entirely. */
public final class PiiMasking {

    private PiiMasking() {
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = local.isEmpty() ? "" : local.charAt(0) + "***";
        return maskedLocal + "@" + domain;
    }
}
