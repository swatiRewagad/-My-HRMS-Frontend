package com.rbi.cms.common.enums;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Reads a {@link ComplaintStatus} from either a constant name or a display label, case-insensitively.
 *
 * <p>Unknown input becomes {@code null}, never a substituted status. It previously fell back to
 * {@link ComplaintStatus#NEW}, which turned every unrecognised value into a plausible-looking one: an
 * event announcing a complaint had been closed was recorded as newly-filed, so the complaint reappeared
 * in pending queues. A null propagates to the consumer's own missing-status check, which drops the
 * message loudly instead of writing a wrong one.
 *
 * <p>Kept rather than deleted in favour of Jackson's default enum handling, because the default is
 * case-sensitive and {@code cms-backend} persists status as lower snake_case.
 */
public class ComplaintStatusDeserializer extends JsonDeserializer<ComplaintStatus> {

    private static final Logger log = LoggerFactory.getLogger(ComplaintStatusDeserializer.class);

    @Override
    public ComplaintStatus deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        ComplaintStatus parsed = ComplaintStatus.parse(value).orElse(null);
        if (parsed == null) {
            log.warn("Unrecognised complaint status '{}'; deserializing as null. Either the publisher "
                    + "is sending a status outside ComplaintStatus or the enum is missing a constant.", value);
        }
        return parsed;
    }
}
