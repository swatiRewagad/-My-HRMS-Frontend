package com.rbi.cms.mailintake.spi;

import lombok.extern.slf4j.Slf4j;

/**
 * Default handler — logs and drops. A real deployment supplies its own {@link InboundMailHandler}
 * bean (see the class-level Javadoc on that interface); this one only exists so the module boots
 * standalone without one configured. Registered via {@code InboundMailHandlerConfig}'s
 * {@code @Bean @ConditionalOnMissingBean} method rather than {@code @Component} directly on this
 * class — Spring's own guidance is that {@code @ConditionalOnMissingBean} on a component-scanned
 * class isn't reliably ordered relative to other beans that need to inject it; found the hard way
 * when ParserPipeline's real constructor dependency on InboundMailHandler failed to resolve.
 *
 * PII discipline (brief rule 6): never logs body content, attachment contents, or an unmasked
 * complainant address — only the inbound_email id, a masked sender, subject length, attachment
 * count, and which resolver fired.
 */
@Slf4j
public class LoggingInboundMailHandler implements InboundMailHandler {

    @Override
    public HandlerResult handle(NormalisedInboundMail mail) {
        log.info("inbound_email_id={} from={} subjectLength={} attachments={} resolvedBy={} complaintRef={} — "
                        + "no InboundMailHandler configured, message logged and not dispatched to CMS",
                mail.inboundEmailId(),
                mask(mail.originalFrom()),
                mail.originalSubject() == null ? 0 : mail.originalSubject().length(),
                mail.attachments() == null ? 0 : mail.attachments().size(),
                mail.resolvedBy(),
                mail.complaintRef());
        return HandlerResult.permanentFailure("No InboundMailHandler bean configured");
    }

    private static String mask(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = local.isEmpty() ? "" : local.charAt(0) + "***";
        return maskedLocal + "@" + domain;
    }
}
