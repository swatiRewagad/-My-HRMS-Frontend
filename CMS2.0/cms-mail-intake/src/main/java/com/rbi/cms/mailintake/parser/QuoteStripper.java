package com.rbi.cms.mailintake.parser;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Splits a plain-text body into "the new content" and "everything from the prior thread /
 * signature onward" — brief: "Strip signature blocks and prior-thread quoting into a separate
 * field rather than deleting them." Heuristic, not a parser: the first line matching any of the
 * common quote/signature markers ends the new-content section. Good enough for the common cases
 * (Outlook's "-----Original Message-----", Gmail/Apple Mail's "On ... wrote:", '>' quoting,
 * "-- " signature delimiter); anything more exotic just doesn't get split, which is the safe
 * failure mode — the whole body stays in textBody rather than being silently truncated.
 */
@Component
class QuoteStripper {

    private static final Pattern QUOTE_START = Pattern.compile(
            "^(-{3,}\\s*Original Message\\s*-{3,}"
                    + "|On .{0,200}wrote:\\s*$"
                    + "|>.*"
                    + "|--\\s*$"
                    + "|From:\\s*.+@.+)",
            Pattern.CASE_INSENSITIVE);

    record Split(String newContent, String quotedContent) {}

    Split split(String plainTextBody) {
        if (plainTextBody == null || plainTextBody.isBlank()) {
            return new Split(plainTextBody, null);
        }
        String[] lines = plainTextBody.split("\r\n|\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (QUOTE_START.matcher(lines[i].trim()).find()) {
                String newContent = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, i)).trim();
                String quoted = String.join("\n", java.util.Arrays.copyOfRange(lines, i, lines.length)).trim();
                // Require some actual new content before the marker — a message that's ALL quote
                // (e.g. a bounce) shouldn't have its only content moved out of textBody.
                if (!newContent.isBlank()) {
                    return new Split(newContent, quoted);
                }
            }
        }
        return new Split(plainTextBody, null);
    }
}
