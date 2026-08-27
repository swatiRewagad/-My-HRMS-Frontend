package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief rule 5: "The same message delivered twice ... must not create two complaints." Verified
 *  at this layer as: two independent SMTP deliveries of byte-identical content land on the same
 *  inbound_email row (same id in the "250 OK id=N" response), and exactly one row exists after
 *  both — this is what a relay retry or a dual-delivery from redirect+forward both look like on
 *  the wire. */
@SpringBootTest
@ActiveProfiles("dev-local")
class SmtpIdempotencyTest {

    @Autowired
    private SmtpServer smtpServer;

    @Autowired
    private InboundEmailRepository emailRepository;

    @DynamicPropertySource
    static void ephemeralPortAndStorage(DynamicPropertyRegistry registry) {
        registry.add("cms.mail.intake.listener.port", () -> "0");
        registry.add("cms.mail.intake.storage.raw-message-base-path", () -> "./target/mail-intake-idem-test");
    }

    @Test
    void sameBytesDeliveredTwiceProduceOneRow() throws Exception {
        long before = emailRepository.count();
        String body = "Subject: dup test\r\nFrom: citizen@example.com\r\n\r\nSame body every single time";

        String firstResponse;
        try (TestSmtpClient first = new TestSmtpClient(smtpServer.getBoundPort())) {
            firstResponse = first.deliver("citizen@example.com", "cms20bot@cms20.rbi.org.in", body);
        }
        String secondResponse;
        try (TestSmtpClient second = new TestSmtpClient(smtpServer.getBoundPort())) {
            secondResponse = second.deliver("citizen@example.com", "cms20bot@cms20.rbi.org.in", body);
        }

        assertThat(firstResponse).startsWith("250");
        assertThat(secondResponse).startsWith("250");
        assertThat(secondResponse)
                .as("the retry should ack referencing the SAME inbound_email id, not a new one")
                .isEqualTo(firstResponse);
        assertThat(emailRepository.count())
                .as("exactly one row after two deliveries of identical bytes")
                .isEqualTo(before + 1);
    }
}
