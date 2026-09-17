package com.hrms.cms.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class IncomingEmailRequest {
    private String fromEmail;
    private String fromName;
    private String subject;
    private String body;
    private String toRecipients;
    private String ccRecipients;
    private String bccRecipients;

    /** Set by the ack policy for internal senders; suppresses the automatic acknowledgement. */
    private boolean suppressAcknowledgement;

    /** Present when this email is being attached to an existing complaint rather than a new one. */
    private String linkedComplaintNumber;
}
