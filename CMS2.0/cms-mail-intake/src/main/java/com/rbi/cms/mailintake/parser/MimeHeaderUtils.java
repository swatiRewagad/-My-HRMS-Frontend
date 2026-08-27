package com.rbi.cms.mailintake.parser;

import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.UnstructuredField;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.stream.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Header access helpers shared across the sender-resolution chain. Always RFC 2047-decodes
 *  (brief: "Decode RFC 2047 encoded-words in headers") regardless of which mime4j Field subtype
 *  the parser happened to produce for a given header name. */
final class MimeHeaderUtils {

    private static final Pattern ANGLE_ADDRESS = Pattern.compile("<([^<>@\\s]+@[^<>\\s]+)>");
    private static final Pattern BARE_ADDRESS = Pattern.compile("([^\\s<>@]+@[^\\s<>@]+\\.[^\\s<>@]+)");

    private MimeHeaderUtils() {}

    static Optional<String> getDecodedHeader(Message message, String name) {
        Field field = message.getHeader().getField(name);
        if (field == null) return Optional.empty();
        String raw = field instanceof UnstructuredField uf ? uf.getValue() : field.getBody();
        return Optional.ofNullable(decode(raw));
    }

    static String decode(String raw) {
        if (raw == null) return null;
        try {
            return DecoderUtil.decodeEncodedWords(raw, DecodeMonitor.SILENT);
        } catch (RuntimeException e) {
            return raw; // never let a malformed encoded-word take down the whole parse
        }
    }

    /** True if any header name (case-insensitive) starts with the given prefix — used for the
     *  X-MS-Exchange-Organization-* family, which varies in its exact suffix. */
    static boolean hasHeaderStartingWith(Message message, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (Field field : message.getHeader().getFields()) {
            if (field.getName().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                return true;
            }
        }
        return false;
    }

    static List<Field> allFields(Message message) {
        return message.getHeader().getFields();
    }

    /** Pulls a bare email address out of a "Name &lt;addr&gt;" or bare-address header value.
     *  Tries mime4j's own address parser first (handles quoted display names, comments, etc.
     *  correctly); falls back to a permissive regex rather than failing, consistent with rule 4. */
    static Optional<String> extractEmailAddress(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) return Optional.empty();

        try {
            Mailbox mailbox = LenientAddressParser.DEFAULT.parseMailbox(headerValue);
            if (mailbox != null && mailbox.getAddress() != null) {
                return Optional.of(mailbox.getAddress());
            }
        } catch (RuntimeException ignored) {
            // fall through to regex
        }

        Matcher angle = ANGLE_ADDRESS.matcher(headerValue);
        if (angle.find()) return Optional.of(angle.group(1));

        Matcher bare = BARE_ADDRESS.matcher(headerValue);
        if (bare.find()) return Optional.of(bare.group(1));

        return Optional.empty();
    }

    static List<String> toAddressStrings(MailboxList list) {
        if (list == null) return List.of();
        return list.stream().map(Mailbox::getAddress).collect(Collectors.toList());
    }

    static List<String> toAddressStrings(AddressList list) {
        if (list == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Address a : list) {
            if (a instanceof Mailbox m) {
                out.add(m.getAddress());
            } else if (a instanceof Group g) {
                for (Mailbox m : g.getMailboxes()) out.add(m.getAddress());
            }
        }
        return out;
    }

    static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    static String firstAddress(MailboxList list) {
        return list != null && !list.isEmpty() ? list.get(0).getAddress() : null;
    }

    static String firstAddress(AddressList list) {
        List<String> addresses = toAddressStrings(list);
        return addresses.isEmpty() ? null : addresses.get(0);
    }
}
