package com.rbi.cms.notification.config;

import com.rbi.cms.notification.repository.OutboundEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Two startup checks, both chosen to fail or warn loudly rather than misbehave later.
 *
 * <p>Whether this service really sends mail should never require reading code to find out, so the effective
 * mode is logged at WARN either way.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchStartupCheck implements ApplicationRunner {

    private final NotificationProperties properties;
    private final OutboundEmailRepository emailRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public void run(ApplicationArguments args) {
        assertDeliveryColumnsExist();
        announceMode();
        assertMailSenderPresentIfSending();
    }

    /**
     * There is no migration tool in this repo, so nothing guarantees V28/V29 ran. Refusing to start names the
     * problem once, instead of every dispatch failing with an opaque SQL error and landing in the DLQ.
     */
    private void assertDeliveryColumnsExist() {
        try {
            emailRepository.assertSchema();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "SIMULATED_EMAILS is missing the delivery columns this service writes. Apply"
                            + " database/V28__simulated_emails_cc_bcc_audit_columns.sql and"
                            + " database/V29__simulated_emails_delivery_status.sql (or their oracle/"
                            + " counterparts) before starting. Underlying error: " + e.getMessage(), e);
        }
    }

    private void announceMode() {
        if (properties.getMode() == DispatchMode.SIMULATE) {
            log.warn("[DISPATCH] mode=SIMULATE - no email or SMS will actually be sent; rows are marked SENT");
            return;
        }
        log.warn("[DISPATCH] mode=SEND - REAL notifications will be dispatched (email enabled={}, sms enabled={})",
                properties.isEmailEnabled(), properties.isSmsEnabled());
    }

    /**
     * dev-local excludes MailSenderAutoConfiguration, so asking for SEND there can never work. Better a
     * refused start than a failure on every message.
     */
    private void assertMailSenderPresentIfSending() {
        boolean wantsRealEmail = properties.getMode() == DispatchMode.SEND && properties.isEmailEnabled();
        if (wantsRealEmail && mailSenderProvider.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "cms.notification.mode=SEND with email-enabled=true, but no JavaMailSender bean exists."
                            + " Configure spring.mail.* and make sure MailSenderAutoConfiguration is not excluded"
                            + " (the dev-local profile excludes it).");
        }
    }
}
