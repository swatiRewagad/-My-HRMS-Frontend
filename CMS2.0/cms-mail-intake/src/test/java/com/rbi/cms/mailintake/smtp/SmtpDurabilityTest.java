package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brief rule: "kill persistence mid-transaction, assert 451 returned and no orphan row." Here
 * "kill persistence" means the raw-message-store base path can't be created — a regular file
 * sits where the app wants a directory, so {@code Files.createDirectories} throws before any DB
 * write is even attempted. This exercises the exact ordering InboundEmailIngestService depends
 * on: durable write BEFORE the DB row, so a storage failure never leaves a dangling row pointing
 * at bytes that don't exist.
 */
@SpringBootTest
@ActiveProfiles("dev-local")
class SmtpDurabilityTest {

    private static Path blockerFile;

    @Autowired
    private SmtpServer smtpServer;

    @Autowired
    private InboundEmailRepository emailRepository;

    @DynamicPropertySource
    static void brokenStorage(DynamicPropertyRegistry registry) throws IOException {
        blockerFile = Files.createTempFile("mail-intake-durability-test", "");
        registry.add("cms.mail.intake.storage.raw-message-base-path", blockerFile::toString);
        registry.add("cms.mail.intake.listener.port", () -> "0");
    }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(blockerFile);
    }

    @Test
    void storageFailureReturns451AndLeavesNoOrphanRow() throws Exception {
        long before = emailRepository.count();

        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            String response = client.deliver("citizen@example.com", "cms20bot@cms20.rbi.org.in",
                    "Subject: durability test\r\nFrom: citizen@example.com\r\n\r\nBody");
            assertThat(response).as("DATA response when the raw store is broken").startsWith("451");
        }

        assertThat(emailRepository.count())
                .as("no row should exist for a message whose raw bytes never durably landed")
                .isEqualTo(before);
    }
}
