package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The full conversation behind one row of the Email Communication activity list: the mail that was
 * clicked, plus every message sharing its thread in chronological order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintEmailThreadResponse {

    private String threadId;
    private String complaintNumber;
    private String subject;
    private String status;
    private int messageCount;
    /** The mail the officer clicked, so the header can render without scanning messages. */
    private ComplaintEmailItem email;
    @Builder.Default
    private List<ComplaintEmailItem> messages = List.of();
}
