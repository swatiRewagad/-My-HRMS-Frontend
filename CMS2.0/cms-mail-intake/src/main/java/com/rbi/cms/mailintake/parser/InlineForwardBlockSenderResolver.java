package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.SenderResolverType;
import lombok.RequiredArgsConstructor;
import org.apache.james.mime4j.dom.Message;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolver #5, last resort before giving up. Regexes the "-----Original Message-----" /
 * "From: … Sent: … To: … Subject:" block that Outlook inserts on a plain *forward* (as opposed to
 * a server-side redirect). Patterns are externally configurable per language — see
 * cms.mail.intake.resolver.inline-forward-patterns.
 */
@Component
@Order(5)
@RequiredArgsConstructor
class InlineForwardBlockSenderResolver implements SenderResolver {

    private final MailIntakeProperties properties;
    private final InlineForwardBlockParser blockParser;

    @Override
    public Optional<ResolvedSender> resolve(Message message, int depth) {
        String bodyText = MimeBodyUtils.bestEffortPlainText(message);
        Optional<InlineForwardMatch> match = blockParser.tryMatch(
                bodyText, properties.getResolver().getInlineForwardPatterns());
        if (match.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> from = MimeHeaderUtils.extractEmailAddress(match.get().from());
        if (from.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ResolvedSender.builder()
                .resolverType(SenderResolverType.INLINE_FORWARD_BLOCK)
                .originalFrom(from.get())
                .originalTo(match.get().to() != null
                        ? MimeHeaderUtils.extractEmailAddress(match.get().to()).map(java.util.List::of).orElse(java.util.List.of())
                        : java.util.List.of())
                .originalSubject(match.get().subject())
                // sentRaw is deliberately not parsed into an Instant: Outlook's inline-forward
                // date text varies too much by locale/client to parse reliably, and a wrong date
                // is worse than a missing one for a regulated audit trail.
                .originalSentAt(null)
                .replyTo(null)
                .canonicalMessage(message)
                .build());
    }
}
