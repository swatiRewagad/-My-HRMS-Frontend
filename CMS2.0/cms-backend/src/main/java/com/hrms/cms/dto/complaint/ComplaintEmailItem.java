package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintEmailItem {

    private Long id;
    private String subject;
    private String from;
    private String to;
    private String body;
    private String date;
    private String status;
    private String direction;
    @Builder.Default
    private List<EmailAttachmentRef> attachments = List.of();
}
