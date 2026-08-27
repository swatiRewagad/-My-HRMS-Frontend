package com.rbi.cms.mailintake.parser;

import com.rbi.cms.common.crypto.AesGcmPayloadEncryptionService;
import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.metrics.MailIntakeMetrics;
import com.rbi.cms.mailintake.smtp.RawMessageStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Corpus item: "20 MB attachment" — generated in-memory rather than checked into the repo as a
 *  20MB fixture file. Default max-individual-size-bytes is 10MB, so this exercises the rejection
 *  path specifically — a 20MB attachment must be rejected while the rest of the message (and any
 *  other attachments) still process normally. */
class AttachmentSizeLimitTest {

    @TempDir
    Path tempDir;

    private AttachmentProcessor attachmentProcessor;

    @BeforeEach
    void setUp() {
        MailIntakeProperties properties = new MailIntakeProperties();
        properties.getStorage().setRawMessageBasePath(tempDir.toString());

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        RawMessageStore rawMessageStore = new RawMessageStore(properties,
                AesGcmPayloadEncryptionService.fromBase64Key(Base64.getEncoder().encodeToString(key)));

        // recordAttachmentScanFailure is never reached by this test (both attachments either
        // fail the size check before scanning or come back CLEAN), so a repository-less instance
        // is safe here — nothing dereferences it.
        MailIntakeMetrics metrics = new MailIntakeMetrics(new SimpleMeterRegistry(), null);
        attachmentProcessor = new AttachmentProcessor(
                properties, rawMessageStore, new NoOpAttachmentScanner(), new ZipBombGuard(properties), metrics);
    }

    @Test
    void twentyMegabyteAttachmentIsRejectedButSmallerOneStillProcesses() {
        byte[] twentyMb = new byte[20 * 1024 * 1024];
        new Random(42).nextBytes(twentyMb); // non-repetitive so it's not incidentally caught by the zip-bomb guard

        byte[] smallOne = "This one is well within limits.".getBytes();

        List<AttachmentProcessor.ProcessedAttachment> processed = attachmentProcessor.process(List.of(
                new RawAttachment("huge-scan.bin", "application/octet-stream", twentyMb),
                new RawAttachment("note.txt", "text/plain", smallOne)
        ));

        assertThat(processed).hasSize(2);
        assertThat(processed.get(0).accepted()).isFalse();
        assertThat(processed.get(0).rejectionReason()).containsIgnoringCase("max-individual-size-bytes");
        assertThat(processed.get(1).accepted()).isTrue();
    }
}
