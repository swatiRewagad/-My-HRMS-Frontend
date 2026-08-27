package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.entity.SenderResolverType;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import com.rbi.cms.mailintake.smtp.SmtpServer;
import com.rbi.cms.mailintake.smtp.TestSmtpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The brief's "GreenMail integration test proving the full SMTP→persist→parse→dispatch path" —
 * built with {@link TestSmtpClient} instead (see its Javadoc for why GreenMail doesn't fit a
 * module that IS the SMTP server, not a client of one). Calls {@link ParserPipeline#process}
 * directly rather than waiting on {@link ParserScheduler}'s timer, for deterministic tests; the
 * scheduler wiring itself is exercised live in the Stage 4 manual verification, not re-proven
 * here.
 */
@SpringBootTest
@ActiveProfiles("dev-local")
class SmtpToParserPipelineIntegrationTest {

    @Autowired
    private SmtpServer smtpServer;

    @Autowired
    private InboundEmailRepository emailRepository;

    @Autowired
    private ParserPipeline pipeline;

    @DynamicPropertySource
    static void ephemeralPortAndStorage(DynamicPropertyRegistry registry) {
        registry.add("cms.mail.intake.listener.port", () -> "0");
        registry.add("cms.mail.intake.storage.raw-message-base-path", () -> "./target/mail-intake-pipeline-test");
    }

    @Test
    void redirectedComplaintGoesAllTheWayToDispatched() throws Exception {
        String body = "Subject: Unauthorised debit from my account\r\n"
                + "Resent-From: crpc@rbi.org.in\r\n"
                + "From: citizen.test@example.com\r\n"
                + "\r\n"
                + "Please investigate this unauthorised transaction.";

        Long emailId;
        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            String response = client.deliver("crpc@rbi.org.in", "cms20bot@cms20.rbi.org.in", body);
            assertThat(response).startsWith("250");
            emailId = Long.valueOf(response.substring(response.indexOf("id=") + 3).trim());
        }

        InboundEmail afterAccept = emailRepository.findById(emailId).orElseThrow();
        assertThat(afterAccept.getStatus()).isEqualTo(InboundEmailStatus.RECEIVED);

        // Drive the pipeline synchronously rather than waiting on the scheduler's poll interval.
        pipeline.process(emailId);

        InboundEmail afterProcessing = emailRepository.findById(emailId).orElseThrow();
        assertThat(afterProcessing.getResolvedBy()).isEqualTo(SenderResolverType.REDIRECT_HEADERS);
        assertThat(afterProcessing.getOriginalFrom()).isEqualTo("citizen.test@example.com");
        // No real InboundMailHandler is wired in this module (by design — see its SPI Javadoc),
        // so the default LoggingInboundMailHandler always reports failure; DISPATCHED really
        // happened (the audit trail proves it) even though the terminal state is QUARANTINED.
        assertThat(afterProcessing.getStatus()).isEqualTo(InboundEmailStatus.QUARANTINED);
    }

    @Test
    void unresolvableSenderIsQuarantinedNotGuessed() throws Exception {
        String body = "Subject: Plain non-forwarded message\r\n"
                + "From: whoever@example.com\r\n"
                + "\r\n"
                + "No redirect/forward signal here at all.";

        Long emailId;
        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            String response = client.deliver("whoever@example.com", "cms20bot@cms20.rbi.org.in", body);
            emailId = Long.valueOf(response.substring(response.indexOf("id=") + 3).trim());
        }

        pipeline.process(emailId);

        InboundEmail result = emailRepository.findById(emailId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(InboundEmailStatus.QUARANTINED);
        assertThat(result.getQuarantineReason().name()).isEqualTo("UNRESOLVED_ORIGINAL_SENDER");
    }
}
