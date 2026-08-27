package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief rule 2 (never an open relay) and the "relay attempt rejected" / general protocol-surface
 *  security tests that are actually exercisable at the SMTP-transport layer this stage builds.
 *  path-traversal / zip-bomb / XXE tests are deferred to Stage 4 — the attachment/MIME code they
 *  target doesn't exist yet at this stage. */
@SpringBootTest
@ActiveProfiles("dev-local")
class SmtpRelaySecurityTest {

    @Autowired
    private SmtpServer smtpServer;

    @Autowired
    private InboundEmailRepository emailRepository;

    @DynamicPropertySource
    static void ephemeralPortAndStorage(DynamicPropertyRegistry registry) {
        registry.add("cms.mail.intake.listener.port", () -> "0");
        registry.add("cms.mail.intake.storage.raw-message-base-path", () -> "./target/mail-intake-relay-sec-test");
    }

    @Test
    void rcptToAnyAddressOtherThanTheConfiguredBotIsRejected() throws Exception {
        long before = emailRepository.count();
        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            client.readResponse();
            client.send("EHLO relay.rbi.org.in");
            client.readResponse();
            client.send("MAIL FROM:<attacker@evil.com>");
            client.readResponse();
            client.send("RCPT TO:<someone-else@gmail.com>");
            assertThat(client.readResponse()).startsWith("550");
        }
        assertThat(emailRepository.count()).as("a rejected RCPT TO must never create a row").isEqualTo(before);
    }

    @Test
    void secondRecipientInSameTransactionIsRejected() throws Exception {
        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            client.readResponse();
            client.send("EHLO relay.rbi.org.in");
            client.readResponse();
            client.send("MAIL FROM:<citizen@example.com>");
            client.readResponse();
            client.send("RCPT TO:<cms20bot@cms20.rbi.org.in>");
            assertThat(client.readResponse()).startsWith("250");
            client.send("RCPT TO:<cms20bot@cms20.rbi.org.in>");
            assertThat(client.readResponse())
                    .as("max-recipients-per-transaction=1 — a second RCPT TO must be refused")
                    .startsWith("452");
        }
    }

    @Test
    void authIsAlwaysRejected() throws Exception {
        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            client.readResponse();
            client.send("AUTH LOGIN");
            assertThat(client.readResponse()).startsWith("502");
        }
    }

    @Test
    void vrfyIsAlwaysRejected() throws Exception {
        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            client.readResponse();
            client.send("VRFY postmaster");
            assertThat(client.readResponse()).startsWith("502");
        }
    }

    @Test
    void rcptBeforeMailFromIsRejectedAsOutOfSequence() throws Exception {
        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            client.readResponse();
            client.send("EHLO relay.rbi.org.in");
            client.readResponse();
            client.send("RCPT TO:<cms20bot@cms20.rbi.org.in>");
            assertThat(client.readResponse()).startsWith("503");
        }
    }
}
