package com.hrms.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.cepc.ComplaintEmailRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.SimulatedEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final ComplaintAttachmentRepository attachmentRepository;
    private final ObjectMapper objectMapper;

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
            LocalDateTime left = a.effectiveTime();
            LocalDateTime right = b.effectiveTime();
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
                    + "Sent: " + parent.get().effectiveTime() + "\n"
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

    // ══════════════════════════════════════════════════════════════════
    // The compose form's path: draft, send, edit, retry, thread
    // ══════════════════════════════════════════════════════════════════

    /**
     * Stores a composed message, dispatching it unless the officer asked to keep it as a draft.
     *
     * <p>Separate from {@link #sendEmail}, which the template-driven callers use with a
     * {@code recipients} list. This one takes the compose form's own shape — a from address the officer
     * chose, comma-separated TO/CC/BCC strings and a DRAFT/SENT intent — and is the only path that can
     * produce a DRAFT row.
     *
     * <p>THE FROM ADDRESS IS THE OFFICER'S, not the system's. {@link #sendEmail} substitutes the configured
     * {@code noreply} sender, which is right for an automated acknowledgement and wrong here: a reply the
     * complainant cannot answer defeats the purpose of the tab, and the officer's own address is what the
     * form collects.
     */
    @Transactional
    public SimulatedEmail compose(Complaint complaint, ComplaintEmailRequest request, String actor) {
        List<String> recipients = parseAddressList(request.to());
        boolean dispatch = request.wantsDispatch();

        // Format is checked either way — the columns are the same and a malformed address is a mistake
        // whenever it is made. The DOMAIN restriction is a dispatch rule (UST656) and is applied only when
        // the mail actually goes out, so an officer can park an unfinished draft without being refused for a
        // recipient they have not corrected yet.
        assertWellFormed(recipients);

        String renderedBody = request.body();

        Optional<SimulatedEmail> parent = request.inReplyToId() == null
                ? Optional.empty()
                : emailRepository.findById(request.inReplyToId());
        String threadId = parent.map(SimulatedEmail::getThreadId)
                .orElseGet(() -> "THREAD-" + complaint.getComplaintNumber() + "-" + UUID.randomUUID());
        if (parent.isPresent()) {
            renderedBody = quoteOriginal(renderedBody, parent.get());
        }

        SimulatedEmail email = emailRepository.save(SimulatedEmail.builder()
                .messageId("MSG-" + UUID.randomUUID())
                .threadId(threadId)
                .fromEmail(request.from().trim())
                .toEmail(String.join(", ", recipients))
                .ccRecipients(blankToNull(request.cc()))
                .bccRecipients(blankToNull(request.bcc()))
                .subject(request.subject().trim())
                .body(renderedBody)
                .direction(DIRECTION_OUTBOUND)
                // PENDING before the attempt, not SENT: a crash between persisting and dispatching would
                // otherwise leave a row claiming the complainant was written to when nothing left the box.
                .status(dispatch ? SimulatedEmail.STATUS_PENDING : SimulatedEmail.STATUS_DRAFT)
                .complaintId(complaint.getId())
                .complaintNumber(complaint.getComplaintNumber())
                // No template: the compose form is hand-written, and UST593's "which template" only has an
                // answer for the template-driven senders that call sendEmail.
                .assignedTo(actor)
                .inReplyToId(request.inReplyToId())
                .attachmentUrl(encodeAttachments(request.attachmentIds(), complaint.getId()))
                .retryCount(0)
                .sentAt(LocalDateTime.now())
                .build());

        return dispatch ? attemptDispatch(complaint, email, actor) : email;
    }

    /**
     * The email log for one contact person's own thread — separate from {@link #getComplaintEmails}, which
     * reads the complaint's shared log. Only {@code contactPersonId}-tagged rows come back, since a contact
     * person's thread is its own conversation, not a filtered view of the complaint's.
     */
    @Transactional(readOnly = true)
    public List<SimulatedEmail> getContactPersonEmails(Long contactPersonId) {
        return emailRepository.findByContactPersonIdOrderBySentAtAsc(contactPersonId);
    }

    /**
     * {@link #compose}, but for one contact person's own thread rather than the complaint's shared log.
     *
     * <p>Kept separate rather than adding a nullable parameter to {@code compose}: that method's thread-id
     * fallback names the complaint alone, which is right for the shared log and wrong here, where two
     * different contact persons on the same complaint must not collide onto one thread.
     */
    @Transactional
    public SimulatedEmail composeForContactPerson(Complaint complaint, Long contactPersonId,
                                                  ComplaintEmailRequest request, String actor) {
        List<String> recipients = parseAddressList(request.to());
        boolean dispatch = request.wantsDispatch();
        assertWellFormed(recipients);

        String renderedBody = request.body();

        Optional<SimulatedEmail> parent = request.inReplyToId() == null
                ? Optional.empty()
                : emailRepository.findById(request.inReplyToId());
        String threadId = parent.map(SimulatedEmail::getThreadId)
                .orElseGet(() -> "THREAD-CP-" + contactPersonId + "-" + UUID.randomUUID());
        if (parent.isPresent()) {
            renderedBody = quoteOriginal(renderedBody, parent.get());
        }

        SimulatedEmail email = emailRepository.save(SimulatedEmail.builder()
                .messageId("MSG-" + UUID.randomUUID())
                .threadId(threadId)
                .fromEmail(request.from().trim())
                .toEmail(String.join(", ", recipients))
                .ccRecipients(blankToNull(request.cc()))
                .bccRecipients(blankToNull(request.bcc()))
                .subject(request.subject().trim())
                .body(renderedBody)
                .direction(DIRECTION_OUTBOUND)
                .status(dispatch ? SimulatedEmail.STATUS_PENDING : SimulatedEmail.STATUS_DRAFT)
                .complaintId(complaint.getId())
                .complaintNumber(complaint.getComplaintNumber())
                .contactPersonId(contactPersonId)
                .assignedTo(actor)
                .inReplyToId(request.inReplyToId())
                .attachmentUrl(encodeAttachments(request.attachmentIds(), complaint.getId()))
                .retryCount(0)
                .sentAt(LocalDateTime.now())
                .build());

        return dispatch ? attemptDispatch(complaint, email, actor) : email;
    }

    /**
     * One email, scoped to the contact person whose URL asked for it — the {@code contactPersonId}
     * counterpart of {@link #findOnComplaint}, and for the same reason: the id is a global sequence, so
     * resolving it without checking the link would let one contact person's URL read another's thread.
     */
    @Transactional(readOnly = true)
    public Optional<SimulatedEmail> findOnContactPerson(Long contactPersonId, Long emailId) {
        return emailRepository.findById(emailId)
                .filter(e -> contactPersonId.equals(e.getContactPersonId()));
    }

    /**
     * Rewrites a DRAFT, optionally sending it.
     *
     * <p>Refuses anything that is not a draft. A SENT message is a record of what a citizen received and a
     * FAILED one is evidence for the retry; editing either in place would rewrite history — a failed mail is
     * corrected by editing its recipient through a NEW message, not by altering the attempt.
     */
    @Transactional
    public SimulatedEmail updateDraft(Complaint complaint, SimulatedEmail email,
                                      ComplaintEmailRequest request, String actor) {
        if (!SimulatedEmail.STATUS_DRAFT.equalsIgnoreCase(email.getStatus())) {
            throw new EmailNotEditableException("email.error_not_a_draft",
                    "Only a draft can be edited. This email has already been submitted for sending.");
        }

        List<String> recipients = parseAddressList(request.to());
        assertWellFormed(recipients);

        email.setFromEmail(request.from().trim());
        email.setToEmail(String.join(", ", recipients));
        email.setCcRecipients(blankToNull(request.cc()));
        email.setBccRecipients(blankToNull(request.bcc()));
        email.setSubject(request.subject());
        email.setBody(request.body());
        email.setAssignedTo(actor);
        email.setAttachmentUrl(encodeAttachments(request.attachmentIds(), complaint.getId()));

        boolean dispatch = request.wantsDispatch();
        email.setStatus(dispatch ? SimulatedEmail.STATUS_PENDING : SimulatedEmail.STATUS_DRAFT);
        if (dispatch) {
            email.setSentAt(LocalDateTime.now());
        }
        SimulatedEmail saved = emailRepository.save(email);

        return dispatch ? attemptDispatch(complaint, saved, actor) : saved;
    }

    /**
     * Queues a FAILED message for another attempt.
     *
     * <p>Only a FAILED message may be retried. Re-sending a SENT one would deliver the same message twice,
     * and a DRAFT has not been submitted at all — both are reachable by a client that ignores
     * {@code canRetry}, so the rule is enforced here rather than left to the button's disabled state.
     *
     * <p>{@code lastError} is cleared before the attempt so a success does not leave the previous failure
     * displayed beside a mail that has now gone out.
     */
    @Transactional
    public SimulatedEmail retry(Complaint complaint, SimulatedEmail email, String actor) {
        if (!SimulatedEmail.STATUS_FAILED.equalsIgnoreCase(email.getStatus())) {
            throw new EmailNotEditableException("email.error_not_retryable",
                    "Only an email whose last send attempt failed can be queued again.");
        }

        email.setStatus(SimulatedEmail.STATUS_PENDING);
        email.setLastError(null);
        email.setRetryCount(email.retryCountOrZero() + 1);
        email.setSentAt(LocalDateTime.now());
        return attemptDispatch(complaint, emailRepository.save(email), actor);
    }

    /**
     * Every message on one message's thread, oldest first.
     *
     * <p>Falls back to the message alone when its thread holds nothing else, so the reading pane always has
     * something to show rather than rendering an empty conversation around a mail that plainly exists.
     */
    @Transactional(readOnly = true)
    public List<SimulatedEmail> thread(SimulatedEmail email) {
        if (isBlank(email.getThreadId())) {
            return List.of(email);
        }
        List<SimulatedEmail> messages = emailRepository.findByThreadIdOrderBySentAtAsc(email.getThreadId());
        return messages.isEmpty() ? List.of(email) : messages;
    }

    /**
     * One email, scoped to the complaint whose URL asked for it.
     *
     * <p>The id is a global sequence, so resolving it without checking the link would let any complaint's URL
     * read any other complaint's correspondence — a citizen's private reply exposed through an id guess.
     */
    @Transactional(readOnly = true)
    public Optional<SimulatedEmail> findOnComplaint(Complaint complaint, Long emailId) {
        return emailRepository.findById(emailId)
                .filter(e -> complaint.getId().equals(e.getComplaintId())
                        || complaint.getComplaintNumber().equals(e.getComplaintNumber()));
    }

    /**
     * Dispatches a persisted message and records the outcome on its own row.
     *
     * <p>A dispatch failure becomes {@code FAILED} plus a readable {@code lastError}, which is what makes the
     * Retry button reachable. Previously the failure was logged and the row left saying SENT, so the officer
     * was told the complainant had been written to and had no way to find out otherwise.
     *
     * <p>Partial failure counts as FAILED. With several recipients, one refusal means the correspondence is
     * incomplete, and reporting SENT because the others succeeded hides exactly the recipient who needs
     * attention. The reason names how many.
     */
    private SimulatedEmail attemptDispatch(Complaint complaint, SimulatedEmail email, String actor) {
        List<String> recipients = parseAddressList(email.getToEmail());

        List<String> disallowed = recipients.stream().filter(r -> !isAllowedDomain(r)).toList();
        if (!disallowed.isEmpty()) {
            log.warn("UST656: outbound email refused for complaint {} by actor {} — {} recipient(s) "
                            + "outside the permitted domains: {}",
                    complaint.getComplaintNumber(), actor, disallowed.size(), maskAll(disallowed));
            // The row stays, as FAILED with a reason: the officer composed it and needs to see why it was
            // refused. Throwing away the draft would lose their work to a correctable mistake.
            email.setStatus(SimulatedEmail.STATUS_FAILED);
            email.setLastError("Email may only be sent to addresses on the permitted RBI domains ("
                    + String.join(", ", allowedDomains()) + "). Refused: "
                    + String.join(", ", maskAll(disallowed)));
            return emailRepository.save(email);
        }

        List<String> failures = new ArrayList<>();
        for (String recipient : recipients) {
            try {
                outboundMessagePort.send(NotificationService.CHANNEL_EMAIL, recipient,
                        email.getSubject(), email.getBody(), complaint.getComplaintNumber());
            } catch (Exception e) {
                log.warn("Email dispatch failed for complaint {}: {}",
                        complaint.getComplaintNumber(), e.getMessage());
                failures.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }

        if (failures.isEmpty()) {
            email.setStatus(SimulatedEmail.STATUS_SENT);
            email.setLastError(null);
            email.setSentAt(LocalDateTime.now());
        } else {
            email.setStatus(SimulatedEmail.STATUS_FAILED);
            email.setLastError(failures.size() + " of " + recipients.size()
                    + " recipient(s) could not be reached: " + failures.get(0));
        }
        return emailRepository.save(email);
    }

    /** UST592: a malformed address must not reach a gateway. */
    private void assertWellFormed(List<String> recipients) {
        if (recipients.isEmpty()) {
            throw new EmailRecipientRejectedException("email.error_no_recipients",
                    "At least one recipient is required.", List.of());
        }
        List<String> malformed = recipients.stream()
                .filter(r -> !EMAIL_FORMAT.matcher(r).matches())
                .toList();
        if (!malformed.isEmpty()) {
            throw new EmailRecipientRejectedException("email.error_malformed_recipient",
                    "One or more recipient addresses are not valid email addresses.",
                    maskAll(malformed));
        }
    }

    /** Splits the form's comma- or semicolon-separated address string. */
    public static List<String> parseAddressList(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;]"))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();
    }

    private static String quoteOriginal(String body, SimulatedEmail parent) {
        return body + "\n\n----- Original message -----\n"
                + "From: " + parent.getFromEmail() + "\n"
                + "Sent: " + parent.effectiveTime() + "\n"
                + "Subject: " + parent.getSubject() + "\n\n"
                + parent.getBody();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /**
     * Packs the compose form's attachment ids into {@code SimulatedEmail.attachmentUrl} as a small JSON
     * array, e.g. {@code [{"id":12,"name":"a.pdf","size":20480}]}.
     *
     * <p>The column was already there holding one bare URL and no size (see
     * {@code ComplaintCorrespondenceController.attachmentsOf}), and had never actually been written by
     * this compose path — nothing here changes what {@link #sendEmail} or the acknowledgement flow do
     * with it. This just gives the compose form's own attachments somewhere to live without a schema
     * change, and the controller reads either shape.
     *
     * <p>Ids are filtered to ones that belong to THIS complaint, so a caller cannot attach another
     * complaint's document by guessing its id.
     */
    private String encodeAttachments(List<Long> attachmentIds, Long complaintId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return null;
        }
        List<ComplaintAttachment> owned = attachmentRepository.findAllById(attachmentIds).stream()
                .filter(a -> complaintId.equals(a.getComplaintId()))
                .toList();
        if (owned.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> encoded = owned.stream().map(a -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", a.getId());
                item.put("name", a.getOriginalName());
                item.put("size", a.getFileSize());
                return item;
            }).toList();
            return objectMapper.writeValueAsString(encoded);
        } catch (Exception e) {
            log.warn("Could not encode {} attachment id(s) for complaint {}: {}",
                    attachmentIds.size(), complaintId, e.getMessage());
            return null;
        }
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * A message whose current state does not permit the requested change.
     *
     * <p>Distinct from {@link EmailRecipientRejectedException}, which is about the CONTENT of a request; this
     * is about the row's lifecycle, and the two deserve different HTTP statuses — 422 for content the server
     * will not accept, 409 for a request that conflicts with what the row has already become.
     */
    @lombok.Getter
    public static class EmailNotEditableException extends RuntimeException {
        private final String messageKey;

        public EmailNotEditableException(String messageKey, String message) {
            super(message);
            this.messageKey = messageKey;
        }
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
