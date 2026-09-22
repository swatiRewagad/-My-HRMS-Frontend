package com.rbi.cms.notification.service;

import com.rbi.cms.common.enums.NotificationChannel;
import com.rbi.cms.notification.config.NotificationProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * System-generated notifications to complainants, raised by the complaint-lifecycle listeners.
 *
 * <p>Gated by the same {@link NotificationProperties} mode as officer-composed mail, so there is one answer
 * to "does this deployment send email" rather than one per code path. The previous arrangement — a
 * {@code @Profile("dev-local") @Primary} subclass constructed with a null {@code JavaMailSender} — is gone:
 * it only suppressed sending on a profile that neither documented startup path actually activates.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationProperties properties;

    public void sendEmail(String to, String subject, String body) {
        if (!properties.dispatchesFor(NotificationChannel.EMAIL)) {
            log.info("[SIMULATED] email to={} subject={}", to, subject);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.error("No JavaMailSender configured; dropping email to={} subject={}", to, subject);
            return;
        }

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getEmail().getDefaultFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(mime);
            log.info("Email sent to: {}", to);
        } catch (Exception e) {
            // These are fire-and-forget lifecycle courtesies; a failure must not break event processing.
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    public void sendSms(String phoneNumber, String message) {
        if (!properties.dispatchesFor(NotificationChannel.SMS)) {
            log.info("[SIMULATED] SMS to={} message={}", phoneNumber, message);
            return;
        }
        // No gateway integration exists. Reaching here means SMS was enabled without one being built, so say
        // so rather than logging "sent" for a message that went nowhere.
        log.error("SMS is enabled but no gateway is implemented; dropping message to {}", phoneNumber);
    }

    public void sendAcknowledgement(String email, String phone, String complaintId) {
        String subject = "Complaint Registered - " + complaintId;
        String body = String.format(
                "Dear Complainant,\n\n" +
                        "Your complaint has been registered successfully.\n" +
                        "Reference Number: %s\n\n" +
                        "You can track the status at: https://cms.rbi.org.in/track/%s\n\n" +
                        "Expected resolution within 30 days.\n\n" +
                        "Regards,\nRBI CMS Team", complaintId, complaintId);

        if (email != null && !email.isBlank()) {
            sendEmail(email, subject, body);
        }
        if (phone != null && !phone.isBlank()) {
            sendSms(phone, "Complaint " + complaintId + " registered. Track at cms.rbi.org.in");
        }
    }

    public void sendStatusUpdate(String email, String phone, String complaintId, String newStatus) {
        String subject = "Complaint Update - " + complaintId;
        String body = String.format(
                "Dear Complainant,\n\n" +
                        "Your complaint %s has been updated.\n" +
                        "New Status: %s\n\n" +
                        "Track at: https://cms.rbi.org.in/track/%s\n\n" +
                        "Regards,\nRBI CMS Team", complaintId, newStatus, complaintId);

        if (email != null && !email.isBlank()) {
            sendEmail(email, subject, body);
        }
    }
}
