package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.SenderResolverType;
import lombok.RequiredArgsConstructor;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolver #3. If any part has content type message/rfc822, that inner message — its own From,
 * Subject, Date, body, and attachments — is canonical; it's what an Exchange *forward* (as
 * opposed to redirect) produces. Recurses to handle a forward-of-a-forward, self-limited by
 * cms.mail.intake.resolver.nested-message-depth-cap so a pathological chain can't recurse
 * unbounded.
 */
@Component
@Order(3)
@RequiredArgsConstructor
class NestedMessageSenderResolver implements SenderResolver {

    private final MailIntakeProperties properties;

    @Override
    public Optional<ResolvedSender> resolve(Message message, int depth) {
        if (depth >= properties.getResolver().getNestedMessageDepthCap()) {
            return Optional.empty();
        }

        Optional<Message> nested = findFirstNestedMessage(message);
        if (nested.isEmpty()) {
            return Optional.empty();
        }

        Message inner = nested.get();
        // Forward-of-a-forward: keep unwrapping as long as there's another message/rfc822 inside,
        // up to the depth cap enforced by the recursive call above.
        Message canonical = resolve(inner, depth + 1).map(ResolvedSender::canonicalMessage).orElse(inner);

        Mailbox from = canonical.getFrom() != null && !canonical.getFrom().isEmpty()
                ? canonical.getFrom().get(0) : null;
        if (from == null) {
            return Optional.empty();
        }

        return Optional.of(ResolvedSender.builder()
                .resolverType(SenderResolverType.NESTED_MESSAGE)
                .originalFrom(from.getAddress())
                .originalTo(MimeHeaderUtils.toAddressStrings(canonical.getTo()))
                .originalSubject(canonical.getSubject())
                .originalSentAt(MimeHeaderUtils.toInstant(canonical.getDate()))
                .replyTo(MimeHeaderUtils.firstAddress(canonical.getReplyTo()))
                .canonicalMessage(canonical)
                .build());
    }

    private static Optional<Message> findFirstNestedMessage(Entity entity) {
        if (entity.getBody() instanceof Multipart multipart) {
            for (Entity part : multipart.getBodyParts()) {
                if ("message/rfc822".equalsIgnoreCase(part.getMimeType()) && part.getBody() instanceof Message inner) {
                    return Optional.of(inner);
                }
                Optional<Message> deeper = findFirstNestedMessage(part);
                if (deeper.isPresent()) return deeper;
            }
        }
        return Optional.empty();
    }
}
