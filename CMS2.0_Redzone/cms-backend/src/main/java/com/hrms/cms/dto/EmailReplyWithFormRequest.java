package com.hrms.cms.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EmailReplyWithFormRequest {
    private String threadId;
    private String fromEmail;
    private String subject;
    private String complainantName;
    private String complainantPhone;
    private String complainantAddress;
    private Long bankId;
    private String bankBranch;
    private String accountNumber;
    private Long categoryId;
    private String description;
    private String reliefSought;
}
