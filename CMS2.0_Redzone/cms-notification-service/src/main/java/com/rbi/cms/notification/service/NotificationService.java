package com.rbi.cms.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    public void sendEmail(String to, String subject, String body) {
        log.info("Sending email to: {}, subject: {}", to, subject);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom("noreply@cms.rbi.org.in");

        mailSender.send(message);
        log.info("Email sent successfully to: {}", to);
    }

    public void sendSms(String phoneNumber, String message) {
        log.info("Sending SMS to: {}, message: {}", phoneNumber, message);
        // SMS gateway integration placeholder
        log.info("SMS sent to: {}", phoneNumber);
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
