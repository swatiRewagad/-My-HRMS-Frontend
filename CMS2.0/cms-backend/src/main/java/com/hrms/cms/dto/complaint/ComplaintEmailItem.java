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
    private String messageId;
    private String threadId;
    private String complaintNumber;
    private String subject;
    private String from;
    private String to;
    private String cc;
    private String bcc;
    private String body;
    /** Display-formatted dd-MM-yyyy, kept for the activity list; sentAt carries the full timestamp. */
    private String date;
    private String status;
    private String direction;
    private String assignedTo;
    private String sentAt;
    private String updatedAt;
    /** Why the last dispatch attempt failed. Only populated while the mail is FAILED. */
    private String lastError;
    /** True only once the mail has actually gone out; a draft, queued or failed mail has nothing to reply to. */
    private boolean canReply;
    /** True while the mail is still editable, i.e. while it is a draft. */
    private boolean editable;
    /** True when a failed dispatch can be queued again. */
    private boolean canRetry;
    @Builder.Default
    private List<EmailAttachmentRef> attachments = List.of();
}
