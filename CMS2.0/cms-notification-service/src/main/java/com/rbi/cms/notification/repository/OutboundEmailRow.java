package com.rbi.cms.notification.repository;

import lombok.Builder;
import lombok.Getter;

/**
 * The columns of {@code SIMULATED_EMAILS} this service reads in order to dispatch one mail.
 *
 * <p>Deliberately not a copy of cms-backend's {@code SimulatedEmail} entity: the narrow projection is the
 * contract, and it documents exactly which columns this service depends on.</p>
 */
@Getter
@Builder
public class OutboundEmailRow {

    private final Long id;
    private final String messageId;
    private final String fromEmail;
    private final String toEmail;
    private final String ccEmail;
    private final String bccEmail;
    private final String subject;
    private final String body;
    private final String complaintNumber;
    /** Raw column value; may be null for a legacy row, or a value this service does not recognise. */
    private final String deliveryStatus;
}
