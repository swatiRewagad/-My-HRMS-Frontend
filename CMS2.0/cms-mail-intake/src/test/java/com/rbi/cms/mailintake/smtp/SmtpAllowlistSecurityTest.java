package com.rbi.cms.mailintake.smtp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief PART 0 #4 / rule 3: the CIDR allowlist IS the authentication mechanism (SPF/DKIM fail by
 *  design on redirected mail). This deliberately configures an allowlist that does NOT include
 *  127.0.0.1 — the address every test-JVM connection arrives from — so a real off-allowlist
 *  rejection is exercised, not just asserted in principle. */
@SpringBootTest
@ActiveProfiles("dev-local")
class SmtpAllowlistSecurityTest {

    @Autowired
    private SmtpServer smtpServer;

    @DynamicPropertySource
    static void narrowAllowlist(DynamicPropertyRegistry registry) {
        registry.add("cms.mail.intake.allowlist.cidrs", () -> "10.0.0.0/8");
        registry.add("cms.mail.intake.listener.port", () -> "0");
        registry.add("cms.mail.intake.storage.raw-message-base-path", () -> "./target/mail-intake-allowlist-test");
    }

    @Test
    void connectionFromOffAllowlistIpIsRejectedBeforeAnyCommandIsAccepted() throws Exception {
        try (TestSmtpClient client = new TestSmtpClient(smtpServer.getBoundPort())) {
            String response = client.readLine();
            assertThat(response).as("connection-stage rejection, before MAIL FROM").startsWith("554");
        }
    }
}
