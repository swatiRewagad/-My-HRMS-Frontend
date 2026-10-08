package com.hrms.cms.service;

/**
 * The seam for outbound email and SMS dispatch.
 *
 * <p>THIS IS A SEAM, NOT AN INTEGRATION. cms-backend has no outbound transport of any kind: grep for
 * {@code JavaMailSender}, {@code MimeMessage} or {@code mailSender} across cms-backend/src returns
 * nothing, even though {@code spring-boot-starter-mail} is declared in the POM. For SMS there is
 * nothing anywhere in the repository — no Twilio, MSG91, Gupshup, SmsClient or SmsGateway in any
 * module — and {@code cms-notification-service.sendSms} is a log-and-return placeholder, as is
 * {@code CitizenAuthController}'s OTP dispatch.
 *
 * <p>So this interface exists so that the notification, upload-link and email-communication features
 * can be built, reviewed and tested against a real dispatch decision — which recipient, which
 * channel, which content, and whether the send was permitted — without pretending a gateway exists.
 *
 * <p>The distinction matters for what "sent" means in this batch: a SENT delivery-log row records
 * that dispatch was ATTEMPTED and permitted, not that a message reached a handset or inbox. Swapping
 * {@link LoggingOutboundMessageAdapter} for a real gateway adapter is the only change needed to make
 * it mean delivery, and no caller changes.
 */
public interface OutboundMessagePort {

    /** What an accepted call actually achieved. */
    enum Outcome {
        /** Handed to a real gateway, or deliberately discarded by the no-op adapter. Terminal. */
        SENT,
        /** Queued for another service to send. The caller's record stays PENDING until settled. */
        QUEUED
    }

    /**
     * Dispatch without a correlation key, for callers that track delivery in their own tables
     * ({@code COMMUNICATION_OUTBOX}, upload links) rather than NOTIFICATION_DELIVERY_LOG.
     */
    default Outcome send(String channel, String recipient, String subject, String body,
                         String relatedReference) {
        return send(channel, recipient, subject, body, relatedReference, null);
    }

    /**
     * @param channel          EMAIL or SMS
     * @param recipient        user id, email address or mobile number
     * @param subject          subject line; ignored for SMS
     * @param body             message body
     * @param relatedReference complaint or appeal number, for correlation in logs
     * @param dispatchRef      key the sending service settles the caller's delivery row by; null when
     *                         the caller has no row to settle
     * @return whether the message was sent outright or only queued
     * @throws OutboundDispatchException when the message is refused or dispatch fails; the caller
     *                                   records a FAILED delivery attempt rather than losing the event
     */
    Outcome send(String channel, String recipient, String subject, String body,
                 String relatedReference, String dispatchRef);

    /** Thrown when a message is refused or cannot be dispatched. */
    class OutboundDispatchException extends RuntimeException {
        public OutboundDispatchException(String message) {
            super(message);
        }

        public OutboundDispatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
