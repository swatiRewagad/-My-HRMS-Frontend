package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import lombok.RequiredArgsConstructor;
import org.apache.james.mime4j.dom.Message;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Content-derived rejects — deliberately checked AFTER durable persist, during parsing, never at
 * the SMTP wire (see SmtpCommandHandler's class Javadoc for why: bouncing a message for content
 * reasons risks contributing to the very loop we're guarding against). A positive match here
 * quarantines rather than dispatches.
 */
@Component
@RequiredArgsConstructor
class LoopGuardChecker {

    private final MailIntakeProperties properties;

    /** Empty if the message is fine to continue; present with a human-readable reason if it
     *  should be quarantined as LOOP_DETECTED. */
    Optional<String> check(Message message) {
        MailIntakeProperties.LoopGuard cfg = properties.getLoopGuard();

        Optional<String> loopHeader = MimeHeaderUtils.getDecodedHeader(message, cfg.getHeaderName());
        if (loopHeader.isPresent()) {
            return Optional.of("Our own " + cfg.getHeaderName() + " header came back to us — routing loop");
        }

        if (cfg.isBlockAutoSubmitted()) {
            Optional<String> autoSubmitted = MimeHeaderUtils.getDecodedHeader(message, "Auto-Submitted");
            if (autoSubmitted.isPresent() && autoSubmitted.get().toLowerCase(Locale.ROOT).startsWith("auto")
                    && !autoSubmitted.get().equalsIgnoreCase("no")) {
                return Optional.of("Auto-Submitted: " + autoSubmitted.get());
            }
        }

        if (cfg.isBlockBulkPrecedence()) {
            Optional<String> precedence = MimeHeaderUtils.getDecodedHeader(message, "Precedence");
            if (precedence.isPresent()) {
                String value = precedence.get().toLowerCase(Locale.ROOT).trim();
                if (value.equals("bulk") || value.equals("list") || value.equals("junk")) {
                    return Optional.of("Precedence: " + precedence.get());
                }
            }
        }

        return Optional.empty();
    }
}
