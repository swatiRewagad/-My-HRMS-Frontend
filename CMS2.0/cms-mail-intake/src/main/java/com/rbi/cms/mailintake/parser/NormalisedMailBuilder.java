package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.InboundEmailAttachment;
import com.rbi.cms.mailintake.repository.InboundEmailAttachmentRepository;
import com.rbi.cms.mailintake.spi.NormalisedAttachment;
import com.rbi.cms.mailintake.spi.NormalisedInboundMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.dom.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Assembles the final {@link NormalisedInboundMail} the pipeline hands to
 *  {@link com.rbi.cms.mailintake.spi.InboundMailHandler} — the one place all of resolution, body
 *  extraction, quote-stripping, attachment processing, and complaint-ref matching come together.
 *  Also the one place {@link InboundEmailAttachment} rows get written — the durable blob write
 *  already happened inside {@link AttachmentProcessor}; this only persists the metadata row that
 *  the Stage 5 admin "view email" endpoint and the retention job both read back. */
@Slf4j
@Component
@RequiredArgsConstructor
class NormalisedMailBuilder {

    private final MailIntakeProperties properties;
    private final QuoteStripper quoteStripper;
    private final AttachmentProcessor attachmentProcessor;
    private final InboundEmailAttachmentRepository attachmentRepository;

    NormalisedInboundMail build(Long inboundEmailId, ResolvedSender resolved) {
        Message canonical = resolved.canonicalMessage();

        String plainText = MimeBodyUtils.findPlainText(canonical).orElse(null);
        String html = MimeBodyUtils.findHtml(canonical).orElse(null);
        String effectivePlainText = plainText != null ? plainText
                : (html != null ? org.jsoup.Jsoup.parse(html).text() : null);

        QuoteStripper.Split split = quoteStripper.split(effectivePlainText);

        List<RawAttachment> rawAttachments = MimeBodyUtils.extractAttachments(canonical);
        List<AttachmentProcessor.ProcessedAttachment> processed = attachmentProcessor.process(rawAttachments);
        persistAttachmentRows(inboundEmailId, processed);
        List<NormalisedAttachment> normalisedAttachments = processed.stream()
                .filter(AttachmentProcessor.ProcessedAttachment::accepted)
                .map(a -> NormalisedAttachment.builder()
                        .filename(a.filename())
                        .detectedContentType(a.detectedContentType())
                        .sizeBytes(a.sizeBytes())
                        .extractedText(a.extractedText())
                        .storeUri(a.storeUri())
                        .build())
                .collect(Collectors.toList());

        String complaintRef = matchComplaintRef(resolved.originalSubject(), effectivePlainText);

        return NormalisedInboundMail.builder()
                .inboundEmailId(inboundEmailId)
                .originalFrom(resolved.originalFrom())
                .originalTo(resolved.originalTo())
                .originalSubject(resolved.originalSubject())
                .originalSentAt(resolved.originalSentAt())
                .replyTo(resolved.replyTo())
                .textBody(split.newContent())
                .htmlBody(html)
                .trailingQuotedContent(split.quotedContent())
                .resolvedBy(resolved.resolverType())
                .complaintRef(complaintRef)
                .attachments(normalisedAttachments)
                .build();
    }

    /** Only accepted attachments get a row — a rejected one (over size limit, zip-bomb-suspected)
     *  has no durable blob to point STORE_URI at (that column is NOT NULL), so there's nothing
     *  persistable; the rejection itself is already visible in the parser pipeline's logs. */
    private void persistAttachmentRows(Long inboundEmailId, List<AttachmentProcessor.ProcessedAttachment> processed) {
        for (AttachmentProcessor.ProcessedAttachment p : processed) {
            if (!p.accepted()) {
                log.warn("inbound_email_id={}: attachment '{}' rejected: {}",
                        inboundEmailId, p.filename(), p.rejectionReason());
                continue;
            }
            attachmentRepository.save(InboundEmailAttachment.builder()
                    .emailId(inboundEmailId)
                    .filename(p.filename())
                    .declaredContentType(p.declaredContentType())
                    .detectedContentType(p.detectedContentType())
                    .sizeBytes(p.sizeBytes())
                    .contentSha256(p.contentSha256())
                    .storeUri(p.storeUri())
                    .scanStatus(p.scanStatus())
                    .build());
        }
    }

    private String matchComplaintRef(String subject, String body) {
        Pattern pattern = Pattern.compile(properties.getComplaintRef().getRegex());
        for (String haystack : new String[] {subject, body}) {
            if (haystack == null) continue;
            Matcher matcher = pattern.matcher(haystack);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }
}
