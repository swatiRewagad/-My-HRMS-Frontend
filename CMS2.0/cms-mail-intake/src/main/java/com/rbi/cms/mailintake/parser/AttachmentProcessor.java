package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.AttachmentScanStatus;
import com.rbi.cms.mailintake.metrics.MailIntakeMetrics;
import com.rbi.cms.mailintake.smtp.RawMessageStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Per-attachment: sanitise the display filename, enforce count/size limits, detect the REAL
 * content type with Tika (kept separate from the declared one — brief: never trust a declared
 * type over what the bytes actually are), guard against zip bombs before Tika ever decompresses
 * anything, run it through {@link AttachmentScanner}, extract text for clean attachments, and
 * durably store the bytes via the same {@link RawMessageStore} the raw message itself uses.
 *
 * XXE: this class never parses XML itself — all document parsing is delegated to Tika, which has
 * disabled external-entity/DTD resolution in its bundled XML-based parsers (OOXML, ODF, etc.) by
 * default since well before the 3.x line used here. The corpus test with an XXE payload
 * (SmtpParserCorpusTest) verifies that holds for the actual pinned version rather than just
 * trusting the changelog.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AttachmentProcessor {

    private static final int MAX_EXTRACTED_TEXT_CHARS = 200_000;

    private final MailIntakeProperties properties;
    private final RawMessageStore rawMessageStore;
    private final AttachmentScanner scanner;
    private final ZipBombGuard zipBombGuard;
    private final MailIntakeMetrics metrics;
    private final Tika tika = new Tika();

    List<ProcessedAttachment> process(List<RawAttachment> raw) {
        MailIntakeProperties.Attachments cfg = properties.getAttachments();

        List<RawAttachment> withinCount = raw.size() > cfg.getMaxCount()
                ? raw.subList(0, cfg.getMaxCount()) : raw;
        if (raw.size() > cfg.getMaxCount()) {
            log.warn("Message has {} attachments, exceeding max-count {} — processing only the first {}",
                    raw.size(), cfg.getMaxCount(), cfg.getMaxCount());
        }

        List<ProcessedAttachment> results = new ArrayList<>();
        long runningTotal = 0;
        for (RawAttachment attachment : withinCount) {
            runningTotal += attachment.content().length;
            results.add(processOne(attachment, runningTotal, cfg));
        }
        return results;
    }

    private ProcessedAttachment processOne(RawAttachment attachment, long runningTotal,
                                            MailIntakeProperties.Attachments cfg) {
        String safeFilename = sanitizeFilename(attachment.filename());
        byte[] content = attachment.content();

        if (content.length > cfg.getMaxIndividualSizeBytes()) {
            return ProcessedAttachment.rejected(safeFilename, attachment.declaredContentType(),
                    content.length, "exceeds max-individual-size-bytes");
        }
        if (runningTotal > cfg.getMaxTotalSizeBytes()) {
            return ProcessedAttachment.rejected(safeFilename, attachment.declaredContentType(),
                    content.length, "exceeds max-total-size-bytes for the message");
        }
        if (zipBombGuard.isSuspicious(content)) {
            return ProcessedAttachment.rejected(safeFilename, attachment.declaredContentType(),
                    content.length, "zip-bomb guard tripped (decompression ratio or nesting depth)");
        }

        String detectedContentType = detectContentType(content, safeFilename);

        AttachmentScanner.ScanResult scanResult = scanner.scan(content);
        AttachmentScanStatus scanStatus = switch (scanResult.verdict()) {
            case CLEAN -> AttachmentScanStatus.CLEAN;
            case INFECTED -> AttachmentScanStatus.INFECTED;
            case SCAN_UNAVAILABLE -> AttachmentScanStatus.SCAN_FAILED;
        };
        if (scanStatus != AttachmentScanStatus.CLEAN) {
            metrics.recordAttachmentScanFailure(scanStatus.name());
        }

        String extractedText = scanStatus == AttachmentScanStatus.CLEAN
                ? extractText(content, detectedContentType) : null;

        try {
            String storeUri = rawMessageStore.store(content);
            return new ProcessedAttachment(safeFilename, attachment.declaredContentType(), detectedContentType,
                    content.length, sha256Hex(content), storeUri, scanStatus, extractedText, true, null);
        } catch (IOException e) {
            log.error("Failed to durably store attachment {}: {}", safeFilename, e.getMessage());
            return ProcessedAttachment.rejected(safeFilename, attachment.declaredContentType(),
                    content.length, "storage failure: " + e.getMessage());
        }
    }

    private String detectContentType(byte[] content, String filename) {
        try {
            return tika.detect(content, filename);
        } catch (RuntimeException e) {
            return "application/octet-stream";
        }
    }

    private String extractText(byte[] content, String detectedContentType) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            String text = tika.parseToString(in);
            return text.length() > MAX_EXTRACTED_TEXT_CHARS
                    ? text.substring(0, MAX_EXTRACTED_TEXT_CHARS) : text;
        } catch (IOException | TikaException | RuntimeException e) {
            log.debug("Text extraction failed for content type {}: {}", detectedContentType, e.getMessage());
            return null; // extraction failure isn't a rejection — the attachment is still stored
        }
    }

    /** Strips path separators, control characters (including embedded NULs), leading dots, and
     *  any embedded ".." traversal sequence; caps length. This is a display-name concern only —
     *  the actual bytes are always stored by UUID via RawMessageStore, never by any name derived
     *  from the message itself — but a regulated system's audit/admin UI shouldn't ever have to
     *  render "../../../etc/passwd" as a filename either, so this is stripped everywhere in the
     *  string, not just at the start (a first pass that only stripped a leading run left later
     *  ".." sequences intact once "/" became "_" — caught by AttachmentSecurityTest). */
    static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "unnamed";
        }
        String noControlChars = rawFilename.chars()
                .filter(c -> c >= 0x20 && c != 0x7F)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        String noPathSeparators = noControlChars.replace('/', '_').replace('\\', '_');
        String noTraversal = noPathSeparators.replace("..", "_");
        String noLeadingDots = noTraversal.replaceFirst("^\\.+", "");
        String result = noLeadingDots.isBlank() ? "unnamed" : noLeadingDots;
        return result.length() > 255 ? result.substring(0, 255) : result;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    record ProcessedAttachment(String filename, String declaredContentType, String detectedContentType,
                                long sizeBytes, String contentSha256, String storeUri,
                                AttachmentScanStatus scanStatus, String extractedText,
                                boolean accepted, String rejectionReason) {

        static ProcessedAttachment rejected(String filename, String declaredContentType, long sizeBytes,
                                             String reason) {
            return new ProcessedAttachment(filename, declaredContentType, null, sizeBytes, null, null,
                    null, null, false, reason);
        }
    }
}
