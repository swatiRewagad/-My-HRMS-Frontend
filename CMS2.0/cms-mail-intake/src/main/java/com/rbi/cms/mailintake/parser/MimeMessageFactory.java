package com.rbi.cms.mailintake.parser;

import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * One place that builds a mime4j {@link Message} from raw bytes, configured for maximum leniency
 * — rule 4 (never fail-closed on parse errors) means a message with an unterminated boundary or a
 * missing Message-ID must still parse into *something* usable rather than throwing, so the
 * pipeline can extract whatever it can and only quarantine on a genuinely unrecoverable failure.
 */
@Component
public class MimeMessageFactory {

    public Message parse(byte[] rawBytes) throws IOException {
        DefaultMessageBuilder builder = new DefaultMessageBuilder();
        builder.setMimeEntityConfig(MimeConfig.custom()
                .setMaxLineLen(-1)          // no arbitrary line-length rejection
                .setMaxHeaderLen(-1)
                .setMaxHeaderCount(-1)
                .setMalformedHeaderStartsBody(false)
                .build());

        try (InputStream in = new ByteArrayInputStream(rawBytes)) {
            return (Message) builder.parseMessage(in);
        }
    }
}
