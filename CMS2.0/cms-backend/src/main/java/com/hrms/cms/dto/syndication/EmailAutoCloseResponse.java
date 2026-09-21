package com.hrms.cms.dto.syndication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned by ingestion instead of a draft when the mail never becomes one: CRPC was only in CC/BCC,
 * or the sender is on the ignore list. No draft row is written in either case, so there is nothing to
 * return the draft shape for.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAutoCloseResponse implements EmailIngestResult {

    private String status;
    private String reason;
    private String senderEmail;
    private String subject;
    private Boolean autoClosed;
    private String closedAt;
}
