package com.rbi.cms.mailintake.spi;

import com.rbi.cms.mailintake.entity.SenderResolverType;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * The output of the parser pipeline (Stage 4) and the input to {@link InboundMailHandler}. This
 * is the module's public contract — CMS business rules (draft creation, DEO assignment,
 * duplicate-vs-thread decisions) live entirely outside this module, in whatever implements the
 * handler, not here.
 */
@Builder
public record NormalisedInboundMail(
        /** PK of the inbound_email row this was produced from — a handler can use this to
         *  correlate back into the audit trail, but should not need to query the table itself. */
        Long inboundEmailId,

        String originalFrom,
        List<String> originalTo,
        String originalSubject,
        Instant originalSentAt,
        String replyTo,

        /** Prefers text/plain; if the message was HTML-only, this is the jsoup-converted text
         *  and htmlBody below is also populated. */
        String textBody,
        String htmlBody,

        /** Signature / prior-thread-quote content, split out rather than deleted — see the
         *  content-handling section of the brief. */
        String trailingQuotedContent,

        SenderResolverType resolvedBy,

        /** Matched against cms.mail.intake.complaint-ref.regex — null if this doesn't look like a
         *  reply to an existing complaint. */
        String complaintRef,

        List<NormalisedAttachment> attachments
) {}
