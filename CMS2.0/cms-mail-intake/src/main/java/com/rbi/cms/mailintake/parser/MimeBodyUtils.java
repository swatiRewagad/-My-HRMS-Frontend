package com.rbi.cms.mailintake.parser;

import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Body-part extraction. {@link TextBody#getReader()} is already charset-decoded per the part's
 *  declared Content-Type charset (mime4j's job, not ours) — brief: "respect declared charsets and
 *  fall back to a configurable default with detection rather than mangling." mime4j falls back to
 *  US-ASCII when no charset is declared, which is the RFC 5322 default and a reasonable behaviour
 *  we don't override. */
final class MimeBodyUtils {

    private MimeBodyUtils() {}

    static Optional<String> findPlainText(Message message) {
        return findFirstPartByMimeType(message, "text/plain").flatMap(MimeBodyUtils::readTextBody);
    }

    static Optional<String> findHtml(Message message) {
        return findFirstPartByMimeType(message, "text/html").flatMap(MimeBodyUtils::readTextBody);
    }

    /** For callers (chiefly the inline-forward-block resolver) that just need *some* readable
     *  text and don't care whether it came from the plain or HTML part. */
    static String bestEffortPlainText(Message message) {
        Optional<String> plain = findPlainText(message);
        if (plain.isPresent()) return plain.get();

        Optional<String> html = findHtml(message);
        if (html.isPresent()) {
            try {
                return Jsoup.parse(html.get()).text();
            } catch (RuntimeException e) {
                return html.get(); // fall back to raw markup rather than losing the content
            }
        }
        return "";
    }

    private static Optional<String> readTextBody(Entity entity) {
        if (!(entity.getBody() instanceof TextBody textBody)) {
            return Optional.empty();
        }
        try (Reader reader = textBody.getReader()) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return Optional.of(sb.toString());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Every binary leaf part that isn't the primary text/plain or text/html body — the working
     *  definition of "attachment" this module uses. Walks the canonical message's own tree only;
     *  when sender resolution unwraps a nested/TNEF forward, the pipeline calls this on whichever
     *  message ended up canonical, so an original citizen attachment several forwards deep is
     *  still picked up correctly. */
    static List<RawAttachment> extractAttachments(Message message) {
        List<RawAttachment> attachments = new ArrayList<>();
        collectAttachments(message, attachments);
        return attachments;
    }

    private static void collectAttachments(Entity entity, List<RawAttachment> out) {
        if (entity.getBody() instanceof Multipart multipart) {
            for (Entity part : multipart.getBodyParts()) {
                collectAttachments(part, out);
            }
            return;
        }
        if ("text/plain".equalsIgnoreCase(entity.getMimeType())
                || "text/html".equalsIgnoreCase(entity.getMimeType())) {
            return; // primary body, not an attachment
        }
        if (entity.getBody() instanceof BinaryBody binaryBody) {
            try (InputStream in = binaryBody.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                out.add(new RawAttachment(entity.getFilename(), entity.getMimeType(), bytes));
            } catch (IOException ignored) {
                // A single unreadable part doesn't sink the whole message — rule 4 in spirit.
            }
        }
    }

    private static Optional<Entity> findFirstPartByMimeType(Entity entity, String mimeType) {
        if (mimeType.equalsIgnoreCase(entity.getMimeType())) {
            return Optional.of(entity);
        }
        if (entity.getBody() instanceof Multipart multipart) {
            for (Entity part : multipart.getBodyParts()) {
                Optional<Entity> found = findFirstPartByMimeType(part, mimeType);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }
}
