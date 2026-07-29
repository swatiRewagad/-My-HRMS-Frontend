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
}
