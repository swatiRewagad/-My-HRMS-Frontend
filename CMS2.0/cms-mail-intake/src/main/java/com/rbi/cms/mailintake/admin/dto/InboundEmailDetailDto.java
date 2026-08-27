package com.rbi.cms.mailintake.admin.dto;

import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailAttachment;
import com.rbi.cms.mailintake.entity.InboundEmailEvent;

import java.time.Instant;
import java.util.List;

/** The "view one item + timeline" admin endpoint — unmasked (an operator looking at a single,
 *  specific item by id has already been through the audited "view" action; see
 *  AdminMailIntakeService#getDetail), including the attachment metadata and full event history. */
public record InboundEmailDetailDto(
        Long id,
        String smtpMessageId,
        String contentSha256,
        String envelopeFrom,
        String envelopeTo,
        String remoteIp,
        Instant receivedAt,
        long rawSizeBytes,
        boolean rawPurged,
        String status,
        String failedStage,
        String quarantineReason,
        int attemptCount,
        Instant nextAttemptAt,
        String lastError,
        String originalFrom,
        String originalSubject,
        Instant originalSentAt,
        String resolvedBy,
        String complaintRef,
        String linkedComplaintId,
        List<AttachmentDto> attachments,
        List<TimelineEventDto> timeline
) {
    public static InboundEmailDetailDto from(InboundEmail email, List<InboundEmailAttachment> attachments,
                                              List<InboundEmailEvent> events) {
        return new InboundEmailDetailDto(
                email.getId(),
                email.getSmtpMessageId(),
                email.getContentSha256(),
                email.getEnvelopeFrom(),
                email.getEnvelopeTo(),
                email.getRemoteIp(),
                email.getReceivedAt(),
                email.getRawSizeBytes() == null ? 0 : email.getRawSizeBytes(),
                email.getRawPurgedAt() != null,
                email.getStatus().name(),
                email.getFailedStage() == null ? null : email.getFailedStage().name(),
                email.getQuarantineReason() == null ? null : email.getQuarantineReason().name(),
                email.getAttemptCount() == null ? 0 : email.getAttemptCount(),
                email.getNextAttemptAt(),
                email.getLastError(),
                email.getOriginalFrom(),
                email.getOriginalSubject(),
                email.getOriginalSentAt(),
                email.getResolvedBy() == null ? null : email.getResolvedBy().name(),
                email.getComplaintRef(),
                email.getLinkedComplaintId(),
                attachments.stream().map(AttachmentDto::from).toList(),
                events.stream().map(TimelineEventDto::from).toList());
    }
}
