package com.hrms.cms.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FileComplaintRequest {
    private String complainantName;
    private String complainantEmail;
    private String complainantPhone;
    private String complainantAddress;
    private Long bankId;
    private String bankBranch;
    private String accountNumber;
    private Long categoryId;
    private String subject;
    private String description;
    private String reliefSought;
    private String priority;
    private String filingType;
    private String bankComplaintReference;
    private String bankComplaintDate;
}
