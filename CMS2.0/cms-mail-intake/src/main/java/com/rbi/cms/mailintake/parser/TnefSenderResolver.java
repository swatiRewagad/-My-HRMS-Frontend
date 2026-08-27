package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.SenderResolverType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.poi.hmef.HMEFMessage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Resolver #4. winmail.dat (application/ms-tnef, or an octet-stream part literally named
 * winmail.dat — some senders mislabel the content type) is Outlook/Exchange's rich-text
 * transport encoding; the actual original-sender signal, when present at all, is usually the same
 * "-----Original Message-----" style block as resolver #5, just trapped inside the TNEF-encoded
 * body instead of a normal MIME text part. This decodes the TNEF body and re-runs the same
 * inline-forward-block matcher against it — "decode it and re-run resolution on the decoded
 * content" per the brief, made concrete.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
class TnefSenderResolver implements SenderResolver {

    private final MailIntakeProperties properties;
    private final InlineForwardBlockParser blockParser;

    @Override
    public Optional<ResolvedSender> resolve(Message message, int depth) {
        Optional<Entity> tnefPart = findTnefPart(message);
        if (tnefPart.isEmpty()) {
            return Optional.empty();
        }
        if (!(tnefPart.get().getBody() instanceof BinaryBody binaryBody)) {
            return Optional.empty();
        }

        String decodedBody;
        try (InputStream in = binaryBody.getInputStream()) {
            HMEFMessage tnef = new HMEFMessage(in);
            String body = tnef.getBody();
            decodedBody = body != null ? body : "";
        } catch (IOException | RuntimeException e) {
            // A TNEF part that fails to decode is not a parse failure for the whole message —
            // rule 4 — just a dead end for this one resolver; the chain moves on.
            log.debug("TNEF decode failed, moving to next resolver: {}", e.getMessage());
            return Optional.empty();
        }

        Optional<InlineForwardMatch> match = blockParser.tryMatch(
                decodedBody, properties.getResolver().getInlineForwardPatterns());
        if (match.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> from = MimeHeaderUtils.extractEmailAddress(match.get().from());
        if (from.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ResolvedSender.builder()
                .resolverType(SenderResolverType.TNEF)
                .originalFrom(from.get())
                .originalTo(java.util.List.of())
                .originalSubject(match.get().subject())
                .originalSentAt(null)
                .replyTo(null)
                .canonicalMessage(message)
                .build());
    }

    private static Optional<Entity> findTnefPart(Entity entity) {
        String mimeType = entity.getMimeType();
        String filename = entity.getFilename();
        boolean isTnef = (mimeType != null && (mimeType.equalsIgnoreCase("application/ms-tnef")
                        || mimeType.equalsIgnoreCase("application/vnd.ms-tnef")))
                || (filename != null && filename.equalsIgnoreCase("winmail.dat"));
        if (isTnef) {
            return Optional.of(entity);
        }
        if (entity.getBody() instanceof Multipart multipart) {
            for (Entity part : multipart.getBodyParts()) {
                Optional<Entity> found = findTnefPart(part);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }
}
