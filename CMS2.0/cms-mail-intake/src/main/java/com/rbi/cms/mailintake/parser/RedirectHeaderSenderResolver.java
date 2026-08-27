package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.entity.SenderResolverType;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolver #1 (fastest, most reliable — brief: "Fastest and most reliable"). A genuine Exchange or
 * M365 redirect rule preserves the original From:/To:/Date: and adds Resent-From, an
 * X-MS-Exchange-Inbox-Rules-Loop header, or an X-MS-Exchange-Organization- prefixed header,
 * rather than rewriting the envelope. When any of those markers are present, the top-level From:
 * IS the original sender — no further unwrapping needed.
 */
@Component
@Order(1)
class RedirectHeaderSenderResolver implements SenderResolver {

    @Override
    public Optional<ResolvedSender> resolve(Message message, int depth) {
        boolean looksLikeRedirect = message.getHeader().getField("Resent-From") != null
                || MimeHeaderUtils.hasHeaderStartingWith(message, "X-MS-Exchange-Inbox-Rules-Loop")
                || MimeHeaderUtils.hasHeaderStartingWith(message, "X-MS-Exchange-Organization-");
        if (!looksLikeRedirect) {
            return Optional.empty();
        }

        Mailbox from = message.getFrom() != null && !message.getFrom().isEmpty()
                ? message.getFrom().get(0) : null;
        if (from == null) {
            return Optional.empty(); // redirect markers present but no From: to trust — try next
        }

        return Optional.of(ResolvedSender.builder()
                .resolverType(SenderResolverType.REDIRECT_HEADERS)
                .originalFrom(from.getAddress())
                .originalTo(MimeHeaderUtils.toAddressStrings(message.getTo()))
                .originalSubject(message.getSubject())
                .originalSentAt(MimeHeaderUtils.toInstant(message.getDate()))
                .replyTo(MimeHeaderUtils.firstAddress(message.getReplyTo()))
                .canonicalMessage(message)
                .build());
    }
}
