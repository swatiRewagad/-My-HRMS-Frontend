package com.rbi.cms.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("dev-local")
@Primary
public class DevLocalNotificationService extends NotificationService {

    public DevLocalNotificationService() {
        super(null);
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("[DEV-LOCAL NOTIFICATION] EMAIL to={}, subject={}", to, subject);
        log.debug("[DEV-LOCAL NOTIFICATION] Body: {}", body);
    }

    @Override
    public void sendSms(String phoneNumber, String message) {
        log.info("[DEV-LOCAL NOTIFICATION] SMS to={}, message={}", phoneNumber, message);
    }

    @Override
    public void sendAcknowledgement(String email, String phone, String complaintId) {
        log.info("[DEV-LOCAL NOTIFICATION] ACKNOWLEDGEMENT sent for complaint {} to email={}, phone={}",
                complaintId, email, phone);
    }

    @Override
    public void sendStatusUpdate(String email, String phone, String complaintId, String newStatus) {
        log.info("[DEV-LOCAL NOTIFICATION] STATUS UPDATE for complaint {} → {} sent to email={}, phone={}",
                complaintId, newStatus, email, phone);
    }
}
