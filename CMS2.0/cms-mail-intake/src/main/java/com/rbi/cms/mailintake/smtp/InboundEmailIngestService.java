package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Orchestrates rule 1 (never ack before durability) and rule 5 (idempotency): compute the content
 * hash, check for an existing row, durably write raw bytes to disk BEFORE attempting the DB
 * insert, and fall back cleanly to the existing row if a concurrent delivery wins the race on the
 * unique constraint. Called synchronously from the SMTP DATA-end handler — see
 * {@link SmtpCommandHandler} — deliberately blocking: this is a correctness-over-throughput path
 * (brief: "Correctness and durability outrank throughput and elegance").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundEmailIngestService {

    private final InboundEmailRepository emailRepository;
    private final InboundEmailRowWriter rowWriter;
    private final RawMessageStore rawMessageStore;

    public record IngestOutcome(InboundEmail email, boolean alreadyExisted) {}

    /** Throws IOException if the raw bytes couldn't be durably stored, or any other exception if
     *  the DB write ultimately fails for a non-duplicate reason — callers must translate either
     *  into a 451, never a 250. */
    public IngestOutcome ingest(byte[] rawBytes, String envelopeFrom, String envelopeTo, String remoteIp)
            throws IOException {
        String sha256 = sha256Hex(rawBytes);

        Optional<InboundEmail> existing = emailRepository.findByContentSha256(sha256);
        if (existing.isPresent()) {
            log.info("Duplicate delivery (content_sha256 already present), inbound_email_id={} remoteIp={}",
                    existing.get().getId(), remoteIp);
            return new IngestOutcome(existing.get(), true);
        }

        // Durable write FIRST — if this throws, we never attempt the DB insert, and the caller
        // returns 451 having created no row and no orphaned reference.
        String storeUri = rawMessageStore.store(rawBytes);

        try {
            InboundEmail email = rowWriter.insertReceived(
                    sha256, envelopeFrom, envelopeTo, remoteIp, storeUri, rawBytes.length);
            return new IngestOutcome(email, false);
        } catch (DataIntegrityViolationException race) {
            log.info("Lost race on content_sha256={} to a concurrent delivery, falling back to its row", sha256);
            rawMessageStore.deleteBestEffort(storeUri);
            return emailRepository.findByContentSha256(sha256)
                    .map(e -> new IngestOutcome(e, true))
                    .orElseThrow(() -> race);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
