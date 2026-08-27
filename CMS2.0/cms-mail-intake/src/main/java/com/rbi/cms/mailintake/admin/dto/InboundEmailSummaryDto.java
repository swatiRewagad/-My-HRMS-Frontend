package com.rbi.cms.mailintake.admin.dto;

import com.rbi.cms.mailintake.admin.PiiMasking;
import com.rbi.cms.mailintake.entity.InboundEmail;

import java.time.Instant;

/** One row in the admin "list" views — deliberately excludes body/attachment content and masks
 *  the from-addresses (see {@link PiiMasking}); the full record is only ever returned from the
 *  single-item "view" and "download" endpoints, both of which are individually audited. */
public record InboundEmailSummaryDto(
        Long id,
        String maskedEnvelopeFrom,
        String maskedOriginalFrom,
        String originalSubject,
        String status,
        String quarantineReason,
        String resolvedBy,
        String complaintRef,
        String linkedComplaintId,
        Instant receivedAt,
        int attemptCount
) {
    public static InboundEmailSummaryDto from(InboundEmail email) {
        return new InboundEmailSummaryDto(
                email.getId(),
                PiiMasking.maskEmail(email.getEnvelopeFrom()),
                PiiMasking.maskEmail(email.getOriginalFrom()),
                email.getOriginalSubject(),
                email.getStatus().name(),
                email.getQuarantineReason() == null ? null : email.getQuarantineReason().name(),
                email.getResolvedBy() == null ? null : email.getResolvedBy().name(),
                email.getComplaintRef(),
                email.getLinkedComplaintId(),
                email.getReceivedAt(),
                email.getAttemptCount() == null ? 0 : email.getAttemptCount());
    }
}
