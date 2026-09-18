package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.SimulatedEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The per-complaint email log and the outbound send path (UST590-593, UST656-657).
 *
 * <p>WHY THIS CLASS EXISTS. There was no way to see the emails on a complaint and no way to send one.
 * The RBIO "Email Communication" tab was registered in the tab list but the template had no matching
 * block at all, so clicking it changed a CSS class and rendered nothing. The one compose UI elsewhere
 * in the product "sent" via {@code setTimeout} and pushed the result into a local signal — nothing was
 * persisted, and a page refresh lost it.
 *
 * <p>The data was already there: {@code SIMULATED_EMAILS} carries {@code COMPLAINT_ID} with an index,
 * written on ingestion, and no query ever read it.
 *
 * <p>UST656 ENFORCEMENT MOVED TO THE SEND PATH. A validate-email-recipients endpoint existed, but it
 * was a logging sink the client called AFTER deciding, with no server-side send path to intercept — so
 * the restriction was advisory and trivially skipped. Here it is a precondition of dispatch:
 * {@link #sendEmail} refuses before persisting or dispatching anything.
 *
 * <p>DIRECTION VOCABULARY. Three incompatible values were in use for the same concept — {@code SENT},
 * {@code RECEIVED} and {@code INBOUND}/{@code OUTBOUND}. This class writes and returns only
 * {@code INBOUND}/{@code OUTBOUND}, the values the entity's own persisted rows already use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintEmailService {

    public static final String DIRECTION_INBOUND = "INBOUND";
    public static final String DIRECTION_OUTBOUND = "OUTBOUND";

    /** Comma-separated domains outbound mail may be addressed to (UST656). */
    public static final String KEY_ALLOWED_RECIPIENT_DOMAINS = "email.outbound.allowed_domains";

    static final Set<String> DEFAULT_ALLOWED_DOMAINS = Set.of("rbi.org.in", "rbi.gov.in");

    /**
     * Complaint numbers embedded in a subject line, for re-linking (UST657).
     *
     * <p>Matches the formats the number generator produces. Deliberately anchored on a recognisable
     * prefix rather than any digit run, so an amount or a date in a subject cannot be mistaken for a
     * complaint reference and silently re-link an email to an unrelated case.
     */
    private static final Pattern COMPLAINT_REF = Pattern.compile(
            "\\b((?:CMS|RBIO|CEPC|CRPC|AA)[-/][A-Za-z0-9-/]+)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_FORMAT = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final SimulatedEmailRepository emailRepository;
    private final ComplaintRepository complaintRepository;
    private final SystemConfigService systemConfigService;
    private final OutboundMessagePort outboundMessagePort;
    private final CommunicationTemplateService templateService;

    /**
     * The chronological email log for one complaint (UST593).
     *
     * <p>Both id-linked and number-linked rows are collected, because the two ingestion paths disagree
     * about which they populate. Returning only one would show a partial correspondence history, and a
     * partial history is worse than none — an officer would believe they had seen everything.
     */
    @Transactional(readOnly = true)
    public List<SimulatedEmail> getComplaintEmails(Complaint complaint) {
        LinkedHashSet<SimulatedEmail> merged = new LinkedHashSet<>();
        merged.addAll(emailRepository.findByComplaintIdOrderBySentAtAsc(complaint.getId()));
        merged.addAll(emailRepository.findByComplaintNumberOrderBySentAtAsc(complaint.getComplaintNumber()));

        List<SimulatedEmail> all = new ArrayList<>(merged);
        all.sort((a, b) -> {
            LocalDateTime left = effectiveTime(a);
            LocalDateTime right = effectiveTime(b);
            if (left == null && right == null) return 0;
            if (left == null) return -1;
            if (right == null) return 1;
            return left.compareTo(right);
        });
        return all;
    }

    /**
     * Sends an email against a complaint, refusing non-RBI recipients (UST590-592, UST656).
     *
     * <p>Enforcement is here, before persistence and before dispatch, so a refused message leaves no
     * trace of a send that did not happen and cannot be retried into existence by a client that ignores
     * the response.
     *
     * @param recipients   TO addresses; every one must be a well-formed address on an allowed domain
     * @param templateId   optional template to render; recorded so UST593 can show which was used
     * @param inReplyToId  the email being replied to or forwarded, whose thread and quoted body are
     *                     preserved (UST591)
     */
    @Transactional
    public SimulatedEmail sendEmail(Complaint complaint, List<String> recipients, String subject,
                                    String body, String actor, Long templateId, Long inReplyToId) {

        if (recipients == null || recipients.isEmpty()) {
            throw new EmailRecipientRejectedException("email.error_no_recipients",
                    "At least one recipient is required.", List.of());
        }

        // UST592: format validation, server-side. A malformed address must not reach a gateway.
        List<String> malformed = recipients.stream()
                .filter(r -> r == null || !EMAIL_FORMAT.matcher(r.trim()).matches())
                .map(String::valueOf)
                .toList();
        if (!malformed.isEmpty()) {
            throw new EmailRecipientRejectedException("email.error_malformed_recipient",
                    "One or more recipient addresses are not valid email addresses.",
                    maskAll(malformed));
        }

        // UST656: dispatch is BLOCKED, not merely flagged.
        List<String> disallowed = recipients.stream()
                .map(String::trim)
                .filter(r -> !isAllowedDomain(r))
                .toList();
        if (!disallowed.isEmpty()) {
            // PII: the rejected addresses are MASKED in the log. The previous validation endpoint logged
            // them verbatim, which wrote complainant and third-party addresses into log storage on every
            // rejection — the very disclosure the domain restriction exists to prevent.
            log.warn("UST656: outbound email refused for complaint {} by actor {} — {} recipient(s) "
                            + "outside the permitted domains: {}",
                    complaint.getComplaintNumber(), actor, disallowed.size(), maskAll(disallowed));
            throw new EmailRecipientRejectedException("email.error_recipient_domain_not_allowed",
                    "Email may only be sent to addresses on the permitted RBI domains ("
                            + String.join(", ", allowedDomains()) + ").",
                    maskAll(disallowed));
        }

        Optional<SimulatedEmail> parent = inReplyToId == null
                ? Optional.empty()
                : emailRepository.findById(inReplyToId);

        String renderedBody = body;
        String renderedSubject = subject;
        String templateName = null;
        if (templateId != null) {
            // Rendered SERVER-side so the stored body is what was actually sent, rather than whatever
            // the client claimed it rendered. An officer's own edits still win over the template, which
            // is why the supplied values take precedence when present.
            var template = templateService.getById(templateId);
            var variables = templateVariables(complaint);
            renderedSubject = isBlank(subject)
                    ? templateService.renderSubject(template, variables) : subject;
            renderedBody = isBlank(body)
                    ? templateService.renderBody(template, variables) : body;
            templateName = template.getTemplateName();
        }

        // UST591: a reply or forward keeps the original thread and quotes what came before.
        String threadId = parent.map(SimulatedEmail::getThreadId)
                .orElseGet(() -> "THREAD-" + complaint.getComplaintNumber());
        if (parent.isPresent()) {
            renderedBody = renderedBody + "\n\n----- Original message -----\n"
                    + "From: " + parent.get().getFromEmail() + "\n"
                    + "Sent: " + effectiveTime(parent.get()) + "\n"
                    + "Subject: " + parent.get().getSubject() + "\n\n"
                    + parent.get().getBody();
        }

        SimulatedEmail email = emailRepository.save(SimulatedEmail.builder()
                .messageId("MSG-" + UUID.randomUUID())
                .threadId(threadId)
                .fromEmail(senderAddress())
                .toEmail(String.join(", ", recipients))
                .subject(renderedSubject)
                .body(renderedBody)
                .direction(DIRECTION_OUTBOUND)
                .status("SENT")
                .complaintId(complaint.getId())
                .complaintNumber(complaint.getComplaintNumber())
                .templateUsed(templateName)
                .sentAt(LocalDateTime.now())
                .build());

        for (String recipient : recipients) {
            try {
                outboundMessagePort.send(NotificationService.CHANNEL_EMAIL, recipient.trim(),
                        renderedSubject, email.getBody(), complaint.getComplaintNumber());
            } catch (Exception e) {
                // The row is kept on dispatch failure: the officer composed and sent it, and losing the
                // record would make the correspondence history disagree with what they did.
                log.warn("Email dispatch failed for complaint {} to a permitted recipient: {}",
                        complaint.getComplaintNumber(), e.getMessage());
            }
        }

        log.info("Outbound email {} recorded for complaint {} on thread {}",
                email.getMessageId(), complaint.getComplaintNumber(), threadId);
        return email;
    }

    /**
     * Re-links an email to the complaint named in its subject (UST657).
     *
     * <p>The product previously handled a changed subject line by ASKING USERS NOT TO CHANGE IT, twice,
     * in auto-reply body text. Nothing parsed the subject and nothing re-linked, so a complainant who
     * edited it — which mail clients encourage — had their reply orphaned from the case.
     *
     * <p>Returns the complaint it was linked to, or empty when the subject names none. Deliberately does
     * NOT guess: linking an email to the wrong complaint puts one citizen's correspondence on another
     * citizen's file, so an unrecognised subject leaves the email where it is.
     */
    @Transactional
    public Optional<Complaint> relinkBySubject(SimulatedEmail email) {
        if (email == null || isBlank(email.getSubject())) {
            return Optional.empty();
        }

        var matcher = COMPLAINT_REF.matcher(email.getSubject());
        while (matcher.find()) {
            String candidate = matcher.group(1);
            Optional<Complaint> found = complaintRepository.findByComplaintNumber(candidate);
            if (found.isPresent()) {
                Complaint complaint = found.get();
                if (complaint.getId().equals(email.getComplaintId())) {
                    return found;
                }
                email.setComplaintId(complaint.getId());
                email.setComplaintNumber(complaint.getComplaintNumber());
                // The thread is preserved, so the conversation stays findable from the Email
                // Communication tab rather than becoming a one-message orphan.
                emailRepository.save(email);
                log.info("Email {} re-linked to complaint {} from its subject line",
                        email.getMessageId(), complaint.getComplaintNumber());
                return found;
            }
        }
        return Optional.empty();
    }

    private java.util.Map<String, String> templateVariables(Complaint complaint) {
        var vars = new java.util.LinkedHashMap<String, String>();
        vars.put("complaintNumber", String.valueOf(complaint.getComplaintNumber()));
        vars.put("complainantName", String.valueOf(complaint.getComplainantName()));
        vars.put("status", String.valueOf(complaint.getStatus()));
        return vars;
    }

    private boolean isAllowedDomain(String address) {
        String lower = address.toLowerCase();
        return allowedDomains().stream().anyMatch(d -> lower.endsWith("@" + d));
    }

    private Set<String> allowedDomains() {
        return systemConfigService.getSet(KEY_ALLOWED_RECIPIENT_DOMAINS, DEFAULT_ALLOWED_DOMAINS);
    }

    private String senderAddress() {
        return systemConfigService.getString("email.outbound.from_address", "noreply@rbi.org.in");
    }

    /**
     * Masks the local part of an address so a rejection can be diagnosed without logging or returning
     * the address itself.
     */
    private List<String> maskAll(List<String> addresses) {
        return addresses.stream().map(a -> {
            if (a == null) return "null";
            int at = a.indexOf('@');
            if (at <= 0) return "***";
            String local = a.substring(0, at);
            String domain = a.substring(at);
            return (local.length() <= 2 ? "*" : local.charAt(0) + "***") + domain;
        }).toList();
    }

    private static LocalDateTime effectiveTime(SimulatedEmail e) {
        return e.getSentAt() != null ? e.getSentAt() : e.getReceivedAt();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Carries a translation key and MASKED addresses, so the refusal never echoes PII back. */
    @lombok.Getter
    public static class EmailRecipientRejectedException extends RuntimeException {
        private final String messageKey;
        private final List<String> rejectedMasked;

        public EmailRecipientRejectedException(String messageKey, String message,
                                              List<String> rejectedMasked) {
            super(message);
            this.messageKey = messageKey;
            this.rejectedMasked = rejectedMasked == null ? List.of() : List.copyOf(rejectedMasked);
        }
    }
}
