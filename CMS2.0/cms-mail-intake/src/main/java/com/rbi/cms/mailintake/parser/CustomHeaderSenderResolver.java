package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.SenderResolverType;
import lombok.RequiredArgsConstructor;
import org.apache.james.mime4j.dom.Message;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolver #2. If the mail team can add a custom header at the relay (configurable name, default
 * X-Original-Sender; X-Envelope-From is also always checked as a common convention), prefer it —
 * it's an explicit signal from infrastructure we control, more reliable than inferring from
 * message structure.
 */
@Component
@Order(2)
@RequiredArgsConstructor
class CustomHeaderSenderResolver implements SenderResolver {

    private final MailIntakeProperties properties;

    @Override
    public Optional<ResolvedSender> resolve(Message message, int depth) {
        String headerName = properties.getResolver().getOriginalSenderHeaderName();

        Optional<String> value = MimeHeaderUtils.getDecodedHeader(message, headerName)
                .or(() -> MimeHeaderUtils.getDecodedHeader(message, "X-Envelope-From"));
        if (value.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> address = MimeHeaderUtils.extractEmailAddress(value.get());
        if (address.isEmpty()) {
            return Optional.empty(); // header present but unparseable — try the next resolver
        }

        return Optional.of(ResolvedSender.builder()
                .resolverType(SenderResolverType.CUSTOM_HEADER)
                .originalFrom(address.get())
                .originalTo(MimeHeaderUtils.toAddressStrings(message.getTo()))
                .originalSubject(message.getSubject())
                .originalSentAt(MimeHeaderUtils.toInstant(message.getDate()))
                .replyTo(MimeHeaderUtils.firstAddress(message.getReplyTo()))
                .canonicalMessage(message)
                .build());
    }
}
