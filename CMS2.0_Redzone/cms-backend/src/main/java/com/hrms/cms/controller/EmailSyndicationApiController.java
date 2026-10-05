package com.hrms.cms.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.DraftStatus;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.entity.EmailDraftAttachment;
import com.hrms.cms.entity.EmailIgnoreEntry;
import com.hrms.cms.entity.IgnoredEmailLog;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.EmailDraftAttachmentRepository;
import com.hrms.cms.repository.EmailDraftRepository;
import com.hrms.cms.repository.EmailIgnoreEntryRepository;
import com.hrms.cms.repository.IgnoredEmailLogRepository;
import com.hrms.cms.service.AcknowledgementPolicyService;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.EmailDeduplicationService;
import com.hrms.cms.service.EmailIgnoreListService;
import com.hrms.cms.service.EmailSimulationService;
import com.hrms.cms.service.IntakeAttachmentValidator;
import com.hrms.cms.service.KeycloakUserService;
import com.hrms.cms.service.LanguageTranslationService;
import com.hrms.cms.service.OcrEligibilityService;
import com.hrms.cms.service.OcrExtractionService;
import com.hrms.cms.service.RuleBasedExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/email-syndication")
@RequiredArgsConstructor
public class EmailSyndicationApiController {

    private final EmailSimulationService emailService;
    private final OcrExtractionService ocrService;
    private final RuleBasedExtractor ruleBasedExtractor;
    private final LanguageTranslationService translationService;
    private final KeycloakUserService keycloakUserService;
    private final EmailDraftRepository draftRepository;
    private final EmailDraftAttachmentRepository draftAttachmentRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintRoutingService routingService;
    private final ComplaintService complaintService;
    private final ObjectMapper objectMapper;
    private final EmailIgnoreListService ignoreListService;
    private final EmailIgnoreEntryRepository ignoreRepository;
    private final IgnoredEmailLogRepository ignoredEmailLogRepository;
    private final AcknowledgementPolicyService ackPolicyService;
    private final OcrEligibilityService ocrEligibilityService;
    private final EmailDeduplicationService deduplicationService;
    private final IntakeAttachmentValidator intakeAttachmentValidator;

    @Value("${cms.eligibility.scheme-version:RBIOS_2021}")
    private String schemeVersion;

    @Value("${cms.attachments.root-path:C:/cms-attachments}")
    private String attachmentsRootPath;

    private final AtomicInteger roundRobinPointer = new AtomicInteger(0);

    private List<Map<String, Object>> getDeoPool() {
        List<Map<String, Object>> keycloakDeos = keycloakUserService.getDeos();
        if (!keycloakDeos.isEmpty()) {
            return keycloakDeos;
        }
        return List.of(
                Map.of("userId", "deo_001", "displayName", "Amit Verma", "maxThreshold", 20),
                Map.of("userId", "deo_002", "displayName", "Sneha Patil", "maxThreshold", 15),
                Map.of("userId", "deo_003", "displayName", "Ramesh Iyer", "maxThreshold", 20)
        );
    }

    private String assignToNextDeo() {
        List<Map<String, Object>> pool = getDeoPool();
        if (pool.isEmpty()) return "Unassigned";
        int index = roundRobinPointer.getAndUpdate(i -> (i + 1) % pool.size());
        return (String) pool.get(index % pool.size()).get("userId");
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String name = Paths.get(originalFilename).getFileName().toString();
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return UUID.randomUUID().toString();
        }
        return name;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingestEmail(@RequestBody Map<String, Object> request) {
        String senderEmail = (String) request.getOrDefault("senderEmail", "");
        String subject = (String) request.getOrDefault("subject", "");
        String body = (String) request.getOrDefault("body", "");
        String messageId = (String) request.getOrDefault("messageId", UUID.randomUUID().toString());
        String toRecipients = (String) request.getOrDefault("toRecipients", "");
        String ccRecipients = (String) request.getOrDefault("ccRecipients", "");
        String bccRecipients = (String) request.getOrDefault("bccRecipients", "");

        // BRD Rule: If CRPC is only in CC/BCC (not primary TO), categorize as Non-Complaint and auto-close
        if (isCrpcOnlyInCcBcc(toRecipients, ccRecipients, bccRecipients)) {
            log.info("Email from {} has CRPC in CC/BCC only — auto-closing as Non-Complaint", senderEmail);
            Map<String, Object> autoClosedResult = new LinkedHashMap<>();
            autoClosedResult.put("status", "NON_COMPLAINT");
            autoClosedResult.put("reason", "CRPC in CC/BCC — not a direct complaint");
            autoClosedResult.put("senderEmail", senderEmail);
            autoClosedResult.put("subject", subject);
            autoClosedResult.put("autoClosed", true);
            autoClosedResult.put("closedAt", LocalDateTime.now().toString());
            return wrapResponse(autoClosedResult);
        }

        return processIngest(senderEmail, subject, body, messageId, null,
                toRecipients, ccRecipients, bccRecipients,
                (String) request.getOrDefault("replyTo", ""),
                (String) request.getOrDefault("inReplyTo", ""),
                (String) request.getOrDefault("references", ""));
    }

    @PostMapping(value = "/ingest-with-attachment", consumes = "multipart/form-data")
    public Map<String, Object> ingestEmailWithAttachment(
            @RequestParam("senderEmail") String senderEmail,
            @RequestParam("subject") String subject,
            @RequestParam(value = "body", required = false, defaultValue = "") String body,
            @RequestParam(value = "messageId", required = false, defaultValue = "") String messageId,
            @RequestParam(value = "toRecipients", required = false, defaultValue = "") String toRecipients,
            @RequestParam(value = "ccRecipients", required = false, defaultValue = "") String ccRecipients,
            @RequestParam(value = "bccRecipients", required = false, defaultValue = "") String bccRecipients,
            @RequestParam("attachment") MultipartFile attachment) {

        // NFR-006 before anything is stored or OCR'd. Client-declared content type is not trusted.
        intakeAttachmentValidator.validateSingle(attachment);

        return processIngest(senderEmail, subject, body, messageId, attachment,
                toRecipients, ccRecipients, bccRecipients, "", "", "");
    }

    private Map<String, Object> processIngest(String senderEmail, String subject, String body,
                                               String messageId, MultipartFile attachment,
                                               String toRecipients, String ccRecipients, String bccRecipients,
                                               String replyTo, String inReplyTo, String references) {
        // ── GATE 1: ignore list ──────────────────────────────────────────────
        // Runs before ANY side effect. The former order created a complaint and an acknowledgement
        // first, so a suppressed sender still produced both.
        Optional<EmailIgnoreEntry> matchedRule = ignoreListService.findMatchingRule(
                new EmailIgnoreListService.MatchContext(senderEmail, subject, toRecipients, ccRecipients, bccRecipients));
        if (matchedRule.isPresent()) {
            EmailIgnoreEntry rule = matchedRule.get();
            log.info("Email from {} suppressed by ignore rule id={} field={} pattern='{}' — no draft created",
                    senderEmail, rule.getId(), rule.getMatchField(), rule.getEmailPattern());

            ignoredEmailLogRepository.save(IgnoredEmailLog.builder()
                    .senderEmail(senderEmail)
                    .subject(subject)
                    .toRecipients(toRecipients)
                    .ccRecipients(ccRecipients)
                    .bccRecipients(bccRecipients)
                    .messageId(messageId)
                    .matchedRuleId(rule.getId())
                    .matchedRulePattern(rule.getEmailPattern())
                    .matchedRuleField(rule.getMatchField())
                    .matchedRuleType(rule.getPatternType())
                    .matchedRuleReason(rule.getReason())
                    .receivedAt(LocalDateTime.now())
                    .build());

            Map<String, Object> ignoredResult = new LinkedHashMap<>();
            ignoredResult.put("status", DraftStatus.IGNORED.name());
            ignoredResult.put("draftCreated", false);
            ignoredResult.put("messageKey", "intake.email_suppressed_by_rule");
            ignoredResult.put("matchedRuleId", rule.getId());
            ignoredResult.put("matchedRulePattern", rule.getEmailPattern());
            ignoredResult.put("matchedRuleField", rule.getMatchField());
            ignoredResult.put("senderEmail", senderEmail);
            ignoredResult.put("subject", subject);
            ignoredResult.put("suppressedAt", LocalDateTime.now().toString());
            return wrapResponse(ignoredResult);
        }

        // ── GATE 2: idempotency on messageId ─────────────────────────────────
        // A redelivered message must not create a second draft. messageId is now unique.
        if (messageId != null && !messageId.isBlank()) {
            Optional<EmailDraft> already = draftRepository.findByMessageId(messageId);
            if (already.isPresent()) {
                log.info("messageId {} already ingested as draft {} — returning existing draft",
                        messageId, already.get().getDraftId());
                Map<String, Object> existing = toResponseMap(already.get());
                existing.put("duplicateDelivery", true);
                existing.put("messageKey", "intake.duplicate_delivery_ignored");
                return wrapResponse(existing);
            }
        }

        // ── GATE 3: duplicate of a CLOSED parent complaint ───────────────────
        // Same sender AND exactly equal subject, parent CLOSED. No new draft: the email and its
        // attachments are linked to the existing parent instead.
        Optional<EmailDeduplicationService.DuplicateMatch> duplicate =
                deduplicationService.findDuplicate(senderEmail, subject);
        if (duplicate.isPresent()) {
            return wrapResponse(linkDuplicateToParent(duplicate.get(), senderEmail, subject, body,
                    messageId, attachment, toRecipients, ccRecipients, bccRecipients));
        }

        IncomingEmailRequest emailReq = new IncomingEmailRequest();
        emailReq.setFromEmail(senderEmail);
        emailReq.setFromName(extractName(senderEmail));
        emailReq.setSubject(subject);
        emailReq.setBody(body);
        // Story: suppress the automatic acknowledgement for internal RBI senders.
        emailReq.setSuppressAcknowledgement(!ackPolicyService.shouldAcknowledge(senderEmail));

        Map<String, Object> result = emailService.receiveEmail(emailReq);

        String complaintNumber = (String) result.get("complaintNumber");
        String threadId = (String) result.get("threadId");
        String assignedTo = assignToNextDeo();

        // RULE-BASED EXTRACTION: apply admin-defined regex/keyword rules to email text
        Map<String, String> ruleExtracted = ruleBasedExtractor.extract(subject, body);
        if (!ruleExtracted.isEmpty()) {
            log.info("Rule-based extraction produced {} fields for complaint {}", ruleExtracted.size(), complaintNumber);
        }

        // OCR processing if attachment is present
        boolean ocrProcessed = false;
        int ocrConfidence = 0;
        Map<String, String> ocrExtracted = Collections.emptyMap();
        String complainantName = sanitizeName(ruleExtracted.getOrDefault("complainantName", extractName(senderEmail)));
        String complainantPhone = ruleExtracted.getOrDefault("complainantPhone", "");
        String category = ruleExtracted.getOrDefault("category", "General");
        String complaintSummary = ruleExtracted.getOrDefault("subject", subject);

        // ── Language detection FIRST, so it can actually gate OCR ────────────
        // Previously OCR ran before detection, so vernacular content could never be excluded, and
        // the detected translation was written into the body as the record of the complaint.
        OcrEligibilityService.Assessment assessment = ocrEligibilityService.assessText(
                (subject == null ? "" : subject) + "\n" + (body == null ? "" : body));
        boolean isVernacular = assessment.vernacular();
        String ocrSkipReason = null;
        boolean requiresManualEntry = false;

        if (isVernacular) {
            // No OCR, no machine translation into the body. A skilled DEO handles it.
            ocrSkipReason = "VERNACULAR";
            requiresManualEntry = true;
            log.info("Vernacular content detected ({}) — OCR suppressed, routing to manual entry",
                    assessment.languageName());
        }

        if (attachment != null && !attachment.isEmpty() && assessment.ocrAllowed()) {
            String contentType = attachment.getContentType();
            Set<String> ocrableTypes = Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff");

            if (contentType != null && ocrableTypes.contains(contentType)) {
                try {
                    byte[] fileBytes = attachment.getBytes();
                    ocrExtracted = ocrService.extractFromImage(fileBytes, contentType);

                    if (!ocrExtracted.isEmpty()) {
                        // Second gate on the EXTRACTED text: a vernacular scanned letter behind an
                        // English covering email is invisible to a body-only check.
                        String extractedText = String.join("\n", ocrExtracted.values());
                        Integer providerConfidence = readProviderConfidence(ocrExtracted);
                        OcrEligibilityService.Assessment extractedAssessment =
                                ocrEligibilityService.assessExtractedText(extractedText, providerConfidence);

                        if (extractedAssessment.requiresManualEntry()) {
                            ocrSkipReason = extractedAssessment.decision().name();
                            requiresManualEntry = true;
                            ocrExtracted = Collections.emptyMap();
                            log.info("OCR output rejected for prefill ({}) — routing draft to manual entry",
                                    extractedAssessment.decision());
                        } else {
                            ocrProcessed = true;
                            ocrConfidence = providerConfidence == null ? 0 : providerConfidence;
                            ocrExtracted = withoutProviderMetadata(ocrExtracted);

                            if (nonBlank(ocrExtracted.get("complainantName"))) {
                                complainantName = ocrExtracted.get("complainantName");
                            }
                            if (nonBlank(ocrExtracted.get("complainantPhone"))) {
                                complainantPhone = ocrExtracted.get("complainantPhone");
                            }
                            if (nonBlank(ocrExtracted.get("category"))) {
                                category = ocrExtracted.get("category");
                            }
                            if (nonBlank(ocrExtracted.get("subject"))) {
                                complaintSummary = ocrExtracted.get("subject");
                            }

                            log.info("OCR extracted {} fields (confidence {}) from attachment for complaint {}",
                                    ocrExtracted.size(), ocrConfidence, complaintNumber);
                        }
                    }
                } catch (Exception e) {
                    // Fail closed: a human keys it rather than the record carrying a guess.
                    ocrSkipReason = "OCR_FAILED";
                    requiresManualEntry = true;
                    log.error("OCR processing failed for attachment: {}", e.getMessage());
                }
            } else if (contentType != null) {
                ocrSkipReason = "UNSUPPORTED_TYPE";
            }
        } else if (attachment != null && !attachment.isEmpty()) {
            log.info("Attachment present but OCR suppressed: {}", ocrSkipReason);
        }

        Map<String, Object> languageResult = new LinkedHashMap<>();
        languageResult.put("detectedLanguage", assessment.detectedLanguage());
        languageResult.put("languageName", assessment.languageName());

        // Merge: OCR fields as base, rule-extracted fields override
        Map<String, String> mergedFields = new LinkedHashMap<>(ocrExtracted);
        mergedFields.putAll(ruleExtracted);

        // Persist draft to database
        String ocrJson = "";
        if (!mergedFields.isEmpty()) {
            try {
                ocrJson = objectMapper.writeValueAsString(mergedFields);
            } catch (Exception e) {
                ocrJson = "";
            }
        }

        EmailDraft draft = EmailDraft.builder()
                .draftId(nextDraftId())
                .threadId(threadId)
                .messageId(messageId == null || messageId.isEmpty() ? UUID.randomUUID().toString() : messageId)
                .senderEmail(senderEmail)
                .subject(subject)
                // The citizen's own words are the record. A machine translation must never replace
                // the body of a complaint; it is offered alongside for the officer's convenience.
                .body(body)
                .toRecipients(toRecipients)
                .ccRecipients(ccRecipients)
                .bccRecipients(bccRecipients)
                .replyTo(replyTo)
                .inReplyTo(inReplyTo)
                .emailReferences(references)
                .attachmentCount(attachment != null && !attachment.isEmpty() ? 1 : 0)
                .ocrSkipReason(ocrSkipReason)
                .requiresManualEntry(requiresManualEntry)
                .complainantName(complainantName)
                .complainantPhone(complainantPhone)
                .complainantAddress(mergedFields.getOrDefault("complainantAddress", ""))
                .complainantState(mergedFields.getOrDefault("complainantState", ""))
                .complainantDistrict(mergedFields.getOrDefault("complainantDistrict", ""))
                .complainantPincode(mergedFields.getOrDefault("complainantPincode", ""))
                .cpgramsNumber(mergedFields.getOrDefault("cpgramsNumber", ""))
                .complaintSummary(complaintSummary)
                .category(category)
                .modeOfReceipt("EMAIL")
                .status(requiresManualEntry ? DraftStatus.PENDING_MANUAL_ENTRY.name() : DraftStatus.ASSIGNED.name())
                .assignedTo(assignedTo)
                .parentComplaintId(complaintNumber)
                .isDuplicate(false)
                .ocrProcessed(ocrProcessed)
                .ocrConfidence(ocrConfidence)
                .ocrExtractedFieldsJson(ocrJson)
                .entityName(mergedFields.getOrDefault("entityName", ""))
                .entityType(mergedFields.getOrDefault("entityType", ""))
                .amountInvolved(parseAmount(mergedFields.getOrDefault("amountInvolved", "")))
                .processedBy("")
                .convertedComplaintId("")
                .detectedLanguage((String) languageResult.get("detectedLanguage"))
                .languageName((String) languageResult.get("languageName"))
                .isVernacular(isVernacular)
                .translationConfidence(null)
                // Left null deliberately: no machine translation is stored for a vernacular
                // complaint. The original body IS the record and a human reads it.
                .translatedBody(null)
                .receivedAt(LocalDateTime.now())
                .build();

        // Scheme version comes from configuration. It was hardcoded "RBIOS_2026" — a Scheme year
        // that is not in force; the Scheme currently in force is the Integrated Ombudsman Scheme 2021
        // and its clause numbers are the only ones seeded in CLOSURE_CLAUSE_MASTER.
        draft.setSchemeVersion(schemeVersion);

        EmailDraft saved = draftRepository.save(draft);
        log.info("Draft saved to DB: id={}, draftId={}, assignedTo={}", saved.getId(), saved.getDraftId(), saved.getAssignedTo());

        // Save attachment to database and disk (after draft so we use the correct draftId)
        if (attachment != null && !attachment.isEmpty()) {
            String safeFileName = sanitizeFileName(attachment.getOriginalFilename());
            String storagePath = "";
            try {
                Path draftDir = Paths.get(attachmentsRootPath, "email-drafts", saved.getDraftId());
                Files.createDirectories(draftDir);
                Path targetFile = draftDir.resolve(safeFileName);
                attachment.transferTo(targetFile.toFile());
                storagePath = targetFile.toString();
                log.info("Attachment stored for draft: {}", saved.getDraftId());
            } catch (IOException e) {
                log.error("Failed to store attachment file to disk for draft: {}", saved.getDraftId());
            }

            EmailDraftAttachment att = EmailDraftAttachment.builder()
                    .draftId(saved.getDraftId())
                    .fileName(safeFileName)
                    .fileType(attachment.getContentType())
                    .fileSize(attachment.getSize())
                    .storagePath(storagePath)
                    .ocrText(ocrExtracted.isEmpty() ? "" : ocrExtracted.toString())
                    .ocrConfidence(ocrConfidence)
                    .uploadedBy("SYSTEM")
                    .build();
            draftAttachmentRepository.save(att);
        }

        return wrapResponse(toResponseMap(saved));
    }

    @GetMapping("/queue")
    public Map<String, Object> getQueue(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedTo) {
        List<EmailDraft> drafts;
        if (assignedTo != null && !assignedTo.isEmpty() && status != null && !status.isEmpty()) {
            drafts = draftRepository.findByAssignedToAndStatusOrderByCreatedAtDesc(assignedTo, status);
        } else if (assignedTo != null && !assignedTo.isEmpty()) {
            // Show drafts assigned to user OR processed by user (so DEO sees sent items)
            List<EmailDraft> assigned = draftRepository.findByAssignedToOrderByCreatedAtDesc(assignedTo);
            List<EmailDraft> processed = draftRepository.findByProcessedByOrderByCreatedAtDesc(assignedTo);
            java.util.Set<Long> seen = new java.util.HashSet<>();
            drafts = new java.util.ArrayList<>();
            for (EmailDraft d : assigned) { if (seen.add(d.getId())) drafts.add(d); }
            for (EmailDraft d : processed) { if (seen.add(d.getId())) drafts.add(d); }
        } else if (status != null && !status.isEmpty()) {
            drafts = draftRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            drafts = draftRepository.findAllByOrderByCreatedAtDesc();
        }

        List<Map<String, Object>> results = drafts.stream()
                .map(this::toResponseMap)
                .collect(Collectors.toList());

        return wrapResponse(results);
    }

    @GetMapping("/drafts/{draftId}")
    public Map<String, Object> getDraft(@PathVariable String draftId) {
        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);

        // Fallback: try looking up by displayId (e.g., C017 → id=17)
        if (draft == null && draftId.matches("C\\d+")) {
            try {
                long numericId = Long.parseLong(draftId.substring(1));
                draft = draftRepository.findById(numericId).orElse(null);
            } catch (NumberFormatException ignored) {}
        }

        if (draft != null) {
            Map<String, Object> response = toResponseMap(draft);
            return wrapResponse(response);
        }

        // Fallback: try to find from email thread (for legacy data before migration)
        try {
            Map<String, Object> thread = emailService.getThread(draftId);
            String emailBody = "";
            String senderEmail = (String) thread.get("fromEmail");
            String sentAt = "";
            List<?> emails = (List<?>) thread.get("emails");
            if (emails != null) {
                for (Object emailObj : emails) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> email = (Map<String, Object>) emailObj;
                    if ("INBOUND".equals(email.get("direction"))) {
                        emailBody = (String) email.getOrDefault("body", "");
                        if (email.get("sentAt") != null) sentAt = email.get("sentAt").toString();
                        break;
                    }
                }
            }

            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("id", 0);
            fallback.put("draftId", draftId);
            fallback.put("messageId", UUID.randomUUID().toString());
            fallback.put("senderEmail", senderEmail);
            fallback.put("subject", thread.get("subject"));
            fallback.put("body", emailBody);
            fallback.put("complainantName", extractName(senderEmail));
            fallback.put("complainantPhone", "");
            fallback.put("cpgramsNumber", "");
            fallback.put("complaintSummary", thread.get("subject"));
            fallback.put("category", "General");
            fallback.put("modeOfReceipt", "EMAIL");
            fallback.put("status", "ASSIGNED");
            fallback.put("assignedTo", assignToNextDeo());
            fallback.put("parentComplaintId", thread.get("complaintNumber"));
            fallback.put("isDuplicate", false);
            fallback.put("ocrProcessed", false);
            fallback.put("ocrConfidence", 0);
            fallback.put("receivedAt", sentAt.isEmpty() ? LocalDateTime.now().toString() : sentAt);
            fallback.put("createdAt", sentAt.isEmpty() ? LocalDateTime.now().toString() : sentAt);
            fallback.put("processedBy", "");
            fallback.put("convertedComplaintId", "");
            fallback.put("attachments", List.of());
            fallback.put("suggestedRelated", List.of());
            return wrapResponse(fallback);
        } catch (Exception e) {
            return wrapResponse(Map.of("error", "Draft not found: " + draftId));
        }
    }

    @PostMapping(value = "/drafts/physical-letter", consumes = "multipart/form-data")
    public Map<String, Object> createPhysicalLetterDraft(
            @RequestParam(value = "complainantName", required = false, defaultValue = "") String complainantName,
            @RequestParam(value = "complainantPhone", required = false, defaultValue = "") String complainantPhone,
            @RequestParam(value = "senderEmail", required = false, defaultValue = "") String senderEmail,
            @RequestParam(value = "complainantAddress", required = false, defaultValue = "") String complainantAddress,
            @RequestParam(value = "complainantState", required = false, defaultValue = "") String complainantState,
            @RequestParam(value = "complainantDistrict", required = false, defaultValue = "") String complainantDistrict,
            @RequestParam(value = "complainantPincode", required = false, defaultValue = "") String complainantPincode,
            @RequestParam(value = "category", required = false, defaultValue = "") String category,
            @RequestParam(value = "entityName", required = false, defaultValue = "") String entityName,
            @RequestParam(value = "entityType", required = false, defaultValue = "BANK") String entityType,
            @RequestParam(value = "subject", required = false, defaultValue = "") String subject,
            @RequestParam(value = "body", required = false, defaultValue = "") String body,
            @RequestParam(value = "amountInvolved", required = false) String amountInvolved,
            @RequestParam(value = "transactionDate", required = false, defaultValue = "") String transactionDate,
            @RequestParam(value = "letterDate", required = false, defaultValue = "") String letterDate,
            @RequestParam(value = "modeOfReceipt", required = false, defaultValue = "PHYSICAL_LETTER") String modeOfReceipt,
            @RequestParam(value = "status", required = false, defaultValue = "DRAFT") String status,
            @RequestParam(value = "assignedTo", required = false, defaultValue = "") String assignedTo,
            @RequestParam(value = "processedBy", required = false, defaultValue = "") String processedBy,
            @RequestParam(value = "receivedAt", required = false, defaultValue = "") String receivedAt,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment) {

        try {
            // NFR-006 on the scanned image before it is stored or read.
            if (attachment != null && !attachment.isEmpty()) {
                intakeAttachmentValidator.validateSingle(attachment);
            }

            // The scan is actually OCR'd here. This endpoint used to set ocrProcessed(true) while
            // storing ocrText("") and ocrConfidence(0) — it claimed an extraction that never ran.
            Map<String, String> ocrExtracted = Collections.emptyMap();
            boolean ocrProcessed = false;
            int ocrConfidence = 0;
            String ocrSkipReason = null;
            boolean requiresManualEntry = false;
            String detectedLanguage = "en";
            String languageName = "English";
            boolean isVernacular = false;

            if (attachment != null && !attachment.isEmpty()) {
                String contentType = attachment.getContentType();
                Set<String> ocrableTypes = Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff");
                if (contentType != null && ocrableTypes.contains(contentType)) {
                    try {
                        Map<String, String> extracted =
                                ocrService.extractFromImage(attachment.getBytes(), contentType);
                        if (extracted.isEmpty()) {
                            ocrSkipReason = "OCR_NO_TEXT";
                            requiresManualEntry = true;
                        } else {
                            String extractedText = String.join("\n", extracted.values());
                            Integer providerConfidence = readProviderConfidence(extracted);
                            OcrEligibilityService.Assessment letterAssessment =
                                    ocrEligibilityService.assessExtractedText(extractedText, providerConfidence);
                            detectedLanguage = letterAssessment.detectedLanguage();
                            languageName = letterAssessment.languageName();
                            isVernacular = letterAssessment.vernacular();

                            if (letterAssessment.requiresManualEntry()) {
                                // Vernacular or low-confidence scans are keyed by a human; prefill
                                // from an unreliable read would put a guess into a legal record.
                                ocrSkipReason = letterAssessment.decision().name();
                                requiresManualEntry = true;
                            } else {
                                ocrExtracted = withoutProviderMetadata(extracted);
                                ocrProcessed = true;
                                ocrConfidence = providerConfidence == null ? 0 : providerConfidence;
                            }
                        }
                    } catch (Exception e) {
                        ocrSkipReason = "OCR_FAILED";
                        requiresManualEntry = true;
                        log.error("Physical letter OCR failed: {}", e.getMessage());
                    }
                } else {
                    ocrSkipReason = "UNSUPPORTED_TYPE";
                    requiresManualEntry = true;
                }
            }

            // OCR prefills only the fields the operator left blank, so keyed values always win.
            String ocrJson = "";
            if (!ocrExtracted.isEmpty()) {
                try {
                    ocrJson = objectMapper.writeValueAsString(ocrExtracted);
                } catch (Exception e) {
                    ocrJson = "";
                }
            }

            String effectiveStatus = requiresManualEntry ? DraftStatus.PENDING_MANUAL_ENTRY.name()
                    : (DraftStatus.isValid(status) ? status : DraftStatus.DRAFT.name());

            EmailDraft draft = EmailDraft.builder()
                    .draftId(nextDraftId())
                    .threadId(UUID.randomUUID().toString())
                    .messageId(UUID.randomUUID().toString())
                    .senderEmail(senderEmail)
                    .subject(subject)
                    .body(body)
                    .complainantName(firstNonBlank(complainantName, ocrExtracted.get("complainantName")))
                    .complainantPhone(firstNonBlank(complainantPhone, ocrExtracted.get("complainantPhone")))
                    .complainantAddress(firstNonBlank(complainantAddress, ocrExtracted.get("complainantAddress")))
                    .complainantState(firstNonBlank(complainantState, ocrExtracted.get("complainantState")))
                    .complainantDistrict(firstNonBlank(complainantDistrict, ocrExtracted.get("complainantDistrict")))
                    .complainantPincode(firstNonBlank(complainantPincode, ocrExtracted.get("complainantPincode")))
                    .category(firstNonBlank(category, ocrExtracted.get("category")))
                    .entityName(firstNonBlank(entityName, ocrExtracted.get("entityName")))
                    .entityType(entityType)
                    .modeOfReceipt(modeOfReceipt)
                    .status(effectiveStatus)
                    .assignedTo(assignedTo)
                    .processedBy(processedBy)
                    .amountInvolved(parseAmount(amountInvolved != null ? amountInvolved : ""))
                    .isDuplicate(false)
                    .ocrProcessed(ocrProcessed)
                    .ocrConfidence(ocrConfidence)
                    .ocrExtractedFieldsJson(ocrJson)
                    .ocrSkipReason(ocrSkipReason)
                    .requiresManualEntry(requiresManualEntry)
                    .detectedLanguage(detectedLanguage)
                    .languageName(languageName)
                    .isVernacular(isVernacular)
                    .attachmentCount(attachment != null && !attachment.isEmpty() ? 1 : 0)
                    .schemeVersion(schemeVersion)
                    .receivedAt(java.time.LocalDateTime.now())
                    .build();

            EmailDraft saved = draftRepository.save(draft);
            log.info("Physical letter draft saved: draftId={}", saved.getDraftId());

            // Store attachment if present
            if (attachment != null && !attachment.isEmpty()) {
                String safeFileName = sanitizeFileName(attachment.getOriginalFilename());
                String storagePath = "";
                try {
                    Path draftDir = Paths.get(attachmentsRootPath, "email-drafts", saved.getDraftId());
                    Files.createDirectories(draftDir);
                    Path targetFile = draftDir.resolve(safeFileName);
                    attachment.transferTo(targetFile.toFile());
                    storagePath = targetFile.toString();
                    log.info("Physical letter attachment stored for draft: {}", saved.getDraftId());
                } catch (IOException e) {
                    log.error("Failed to store physical letter attachment for draft: {}", saved.getDraftId());
                }

                EmailDraftAttachment att = EmailDraftAttachment.builder()
                        .draftId(saved.getDraftId())
                        .fileName(safeFileName)
                        .fileType(attachment.getContentType())
                        .fileSize(attachment.getSize())
                        .storagePath(storagePath)
                        // The scanned image is the source document, so its extracted text and the
                        // provider's real confidence are stored rather than "" and 0.
                        .ocrText(ocrExtracted.isEmpty() ? "" : String.join("\n", ocrExtracted.values()))
                        .ocrConfidence(ocrConfidence)
                        .uploadedBy(assignedTo.isEmpty() ? "DEO" : assignedTo)
                        .build();
                draftAttachmentRepository.save(att);
            }

            return wrapResponse(toResponseMap(saved));
        } catch (Exception e) {
            log.error("Physical letter draft creation failed: {}", e.getMessage());
            return wrapResponse(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/drafts")
    public Map<String, Object> createDraft(@RequestBody Map<String, Object> request) {
        try {
            String draftId = (String) request.get("draftId");
            EmailDraft draft;
            if (draftId != null && !draftId.isBlank()) {
                draft = draftRepository.findByDraftId(draftId).orElse(new EmailDraft());
                draft.setDraftId(draftId);
            } else {
                draft = new EmailDraft();
                long nextSeq = draftRepository.count() + 1;
                draft.setDraftId("DRF-" + String.format("%06d", nextSeq));
            }
            draft.setStatus((String) request.getOrDefault("status", "DRAFT"));
            draft.setSubject((String) request.get("subject"));
            draft.setBody((String) request.get("body"));
            draft.setSenderEmail((String) request.get("senderEmail"));
            draft.setComplainantName((String) request.get("complainantName"));
            draft.setComplainantPhone((String) request.get("complainantPhone"));
            draft.setComplainantAddress((String) request.get("complainantAddress"));
            draft.setComplainantState((String) request.get("complainantState"));
            draft.setComplainantDistrict((String) request.get("complainantDistrict"));
            draft.setComplainantPincode((String) request.get("complainantPincode"));
            draft.setCategory((String) request.get("category"));
            draft.setEntityName((String) request.get("entityName"));
            draft.setEntityType((String) request.get("entityType"));
            draft.setModeOfReceipt((String) request.getOrDefault("modeOfReceipt", "PHYSICAL_LETTER"));
            draft.setAssignedTo((String) request.get("assignedTo"));
            draft.setProcessedBy((String) request.get("processedBy"));
            draft.setDeoDecision((String) request.get("deoDecision"));
            draft.setDeoRemarks((String) request.get("deoRemarks"));
            draft.setNonMaintainableReason((String) request.get("nonMaintainableReason"));
            if (request.containsKey("closureClause")) draft.setClosureClause((String) request.get("closureClause"));
            if (request.containsKey("autoClosureResponsesJson")) draft.setAutoClosureResponsesJson((String) request.get("autoClosureResponsesJson"));
            draft.setReceivedAt(java.time.LocalDateTime.now());

            draftRepository.save(draft);
            return wrapResponse(Map.of("draftId", draft.getDraftId(), "status", draft.getStatus()));
        } catch (Exception e) {
            return wrapResponse(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/drafts/{draftId}")
    public Map<String, Object> updateDraft(@PathVariable String draftId, @RequestBody Map<String, Object> request) {
        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);
        if (draft == null && draftId.matches("C\\d+")) {
            try {
                long numericId = Long.parseLong(draftId.substring(1));
                draft = draftRepository.findById(numericId).orElse(null);
            } catch (NumberFormatException ignored) {}
        }
        if (draft == null) {
            return wrapResponse(Map.of("error", "Draft not found"));
        }

        if (request.containsKey("subject")) draft.setSubject((String) request.get("subject"));
        if (request.containsKey("body")) draft.setBody((String) request.get("body"));
        if (request.containsKey("complainantName")) draft.setComplainantName((String) request.get("complainantName"));
        if (request.containsKey("complainantPhone")) draft.setComplainantPhone((String) request.get("complainantPhone"));
        if (request.containsKey("complainantAddress")) draft.setComplainantAddress((String) request.get("complainantAddress"));
        if (request.containsKey("complainantState")) draft.setComplainantState((String) request.get("complainantState"));
        if (request.containsKey("complainantDistrict")) draft.setComplainantDistrict((String) request.get("complainantDistrict"));
        if (request.containsKey("complainantPincode")) draft.setComplainantPincode((String) request.get("complainantPincode"));
        if (request.containsKey("cpgramsNumber")) draft.setCpgramsNumber((String) request.get("cpgramsNumber"));
        if (request.containsKey("complaintSummary")) draft.setComplaintSummary((String) request.get("complaintSummary"));
        if (request.containsKey("category")) draft.setCategory((String) request.get("category"));
        if (request.containsKey("entityName")) draft.setEntityName((String) request.get("entityName"));
        if (request.containsKey("entityType")) draft.setEntityType((String) request.get("entityType"));
        if (request.containsKey("status")) draft.setStatus((String) request.get("status"));
        if (request.containsKey("assignedTo")) draft.setAssignedTo((String) request.get("assignedTo"));
        if (request.containsKey("processedBy")) draft.setProcessedBy((String) request.get("processedBy"));
        if (request.containsKey("deoDecision")) draft.setDeoDecision((String) request.get("deoDecision"));
        if (request.containsKey("deoRemarks")) draft.setDeoRemarks((String) request.get("deoRemarks"));
        if (request.containsKey("nonMaintainableReason")) draft.setNonMaintainableReason((String) request.get("nonMaintainableReason"));
        if (request.containsKey("reviewerDecision")) draft.setReviewerDecision((String) request.get("reviewerDecision"));
        if (request.containsKey("reviewerRemarks")) draft.setReviewerRemarks((String) request.get("reviewerRemarks"));
        if (request.containsKey("targetOffice")) draft.setTargetOffice((String) request.get("targetOffice"));
        if (request.containsKey("schemeVersion")) draft.setSchemeVersion((String) request.get("schemeVersion"));
        if (request.containsKey("closureClause")) draft.setClosureClause((String) request.get("closureClause"));
        if (request.containsKey("autoClosureResponsesJson")) draft.setAutoClosureResponsesJson((String) request.get("autoClosureResponsesJson"));
        if (request.containsKey("subJudice")) draft.setSubJudice(Boolean.TRUE.equals(request.get("subJudice")));
        if (request.containsKey("notAComplaintReason")) draft.setNotAComplaintReason((String) request.get("notAComplaintReason"));
        if (request.containsKey("notAComplaintOthersReason")) draft.setNotAComplaintOthersReason((String) request.get("notAComplaintOthersReason"));
        if (request.containsKey("suggestionDepartment")) draft.setSuggestionDepartment((String) request.get("suggestionDepartment"));
        if (request.containsKey("suggestionNature")) draft.setSuggestionNature((String) request.get("suggestionNature"));

        draftRepository.save(draft);

        // When reviewer approves → create a Complaint record and route to RBIO/CEPC
        String newStatus = (String) request.get("status");
        if ("APPROVED_ROUTED".equals(newStatus)) {
            createComplaintFromDraft(draft);
        }

        return wrapResponse(toResponseMap(draft));
    }

    private void createComplaintFromDraft(EmailDraft draft) {
        String entityName = draft.getEntityName() != null ? draft.getEntityName() : "";
        String department = routingService.resolveDepartment(entityName);
        String targetOffice = draft.getTargetOffice() != null ? draft.getTargetOffice() : department;

        // Determine the department from the target office
        if (targetOffice.startsWith("RBIO")) department = "RBIO";
        else if (targetOffice.startsWith("CEPC") || targetOffice.equals("CEPC")) department = "CEPC";

        String assignedRole = "RBIO".equals(department) ? "RBIO_OFFICER" : "CEPC_DO";

        // Assign to a specific user via round robin
        String assignedUser = targetOffice;
        if ("CEPC".equals(department)) {
            List<Map<String, Object>> cepcDOs = keycloakUserService.getUsersByRole("CEPC_DO");
            if (!cepcDOs.isEmpty()) {
                assignedUser = roundRobinAssign(cepcDOs, "CEPC");
            }
        } else if ("RBIO".equals(department)) {
            List<Map<String, Object>> rbioOfficers = keycloakUserService.getUsersByRole("RBIO_OFFICER");
            List<Map<String, Object>> regionFiltered = rbioOfficers.stream()
                    .filter(u -> targetOffice == null || matchesRegion(u, targetOffice))
                    .collect(Collectors.toList());
            if (!regionFiltered.isEmpty()) {
                assignedUser = roundRobinAssign(regionFiltered, "RBIO_" + targetOffice);
            } else if (!rbioOfficers.isEmpty()) {
                assignedUser = roundRobinAssign(rbioOfficers, "RBIO");
            }
        }

        // Generate complaint number
        String dateStr = LocalDateTime.now().toString().substring(0, 10).replace("-", "");
        String rand = String.valueOf((int) (100000 + Math.random() * 900000));
        String complaintNumber = "CMP-" + dateStr + "-" + rand;

        Complaint complaint = Complaint.builder()
                .complaintNumber(complaintNumber)
                .complainantName(draft.getComplainantName() != null ? draft.getComplainantName() : "Unknown")
                .complainantEmail(draft.getSenderEmail())
                .complainantPhone(draft.getComplainantPhone())
                .complainantAddress(draft.getComplainantAddress())
                .subject(draft.getSubject() != null ? draft.getSubject() : "Email Complaint")
                .description(draft.getBody())
                .status("assigned")
                .priority("medium")
                .filingType(draft.getModeOfReceipt() != null ? draft.getModeOfReceipt() : "EMAIL")
                .department(department)
                .assignedRole(assignedRole)
                .assignedOfficer(assignedUser)
                .entityCode(entityName)
                .workflowStage("INITIAL_REVIEW")
                .build();

        Complaint saved = complaintRepository.save(complaint);

        // Update draft with generated complaint number and assignment
        draft.setConvertedComplaintId(complaintNumber);
        draft.setStatus("APPROVED_ROUTED");
        draft.setAssignedTo(assignedUser);
        draftRepository.save(draft);

        // Add timeline entry
        complaintService.addTimeline(saved.getId(), "CREATED_FROM_CRPC", "REVIEWER",
                "Complaint created from CRPC draft " + draft.getDraftId() + ". Routed to " + department + " (" + targetOffice + ")",
                null, "assigned");

        log.info("Complaint {} created from draft {} → routed to {} ({}) assigned to {}",
                complaintNumber, draft.getDraftId(), department, targetOffice, assignedUser);
    }

    private static final Map<String, Integer> roundRobinCounters = new java.util.concurrent.ConcurrentHashMap<>();

    private String roundRobinAssign(List<Map<String, Object>> users, String counterKey) {
        int index = roundRobinCounters.getOrDefault(counterKey, 0);
        if (index >= users.size()) index = 0;
        String userId = (String) users.get(index).get("userId");
        roundRobinCounters.put(counterKey, index + 1);
        return userId;
    }

    private boolean matchesRegion(Map<String, Object> user, String targetOffice) {
        String username = (String) user.getOrDefault("userId", "");
        if (targetOffice == null) return true;
        switch (targetOffice) {
            case "RBIO-MUM": return username.contains("mum");
            case "RBIO-DEL": return username.contains("del");
            case "RBIO-CHE": return username.contains("che");
            case "RBIO-KOL": return username.contains("kol");
            default: return true;
        }
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Operator-keyed values win over OCR: a human correction must never be overwritten. */
    private String firstNonBlank(String preferred, String fallback) {
        if (nonBlank(preferred)) return preferred;
        return fallback == null ? "" : fallback;
    }

    /**
     * draftId generation. The former "count() + 1" raced under concurrent ingests and draftId is
     * unique, so two simultaneous emails could collide and one would be rejected.
     */
    private String nextDraftId() {
        return "DRF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Confidence as reported by the OCR provider. Returns null when the provider gave none, so the
     * caller can fail closed rather than inherit the old hardcoded 85.
     */
    private Integer readProviderConfidence(Map<String, String> ocrExtracted) {
        String raw = ocrExtracted.get("_confidence");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            // Providers report either 0-1 or 0-100.
            return (int) Math.round(value <= 1.0 ? value * 100 : value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Provider metadata (_confidence, _typedDigital, _ocrSource) is not a complaint field. */
    private Map<String, String> withoutProviderMetadata(Map<String, String> extracted) {
        return extracted.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Story: on a confirmed duplicate create NO new draft; link the email and its attachments to the
     * existing parent complaint's attachment and email-communication records.
     */
    private Map<String, Object> linkDuplicateToParent(
            EmailDeduplicationService.DuplicateMatch match, String senderEmail, String subject,
            String body, String messageId, MultipartFile attachment,
            String toRecipients, String ccRecipients, String bccRecipients) {

        String parentNumber = match.parentComplaintNumber();
        String originDraftId = match.existingDraft().getDraftId();

        log.info("Duplicate email from {} (subject='{}') linked to CLOSED parent {} — no new draft created",
                senderEmail, subject, parentNumber);

        // Record the correspondence against the parent so the officer sees it in the email thread.
        IncomingEmailRequest emailReq = new IncomingEmailRequest();
        emailReq.setFromEmail(senderEmail);
        emailReq.setFromName(extractName(senderEmail));
        emailReq.setSubject(subject);
        emailReq.setBody(body);
        emailReq.setSuppressAcknowledgement(!ackPolicyService.shouldAcknowledge(senderEmail));
        emailReq.setLinkedComplaintNumber(parentNumber);
        int linkedEmails = emailService.linkEmailToComplaint(emailReq, parentNumber);

        int linkedAttachments = 0;
        if (attachment != null && !attachment.isEmpty()) {
            String safeFileName = sanitizeFileName(attachment.getOriginalFilename());
            String storagePath = "";
            try {
                Path parentDir = Paths.get(attachmentsRootPath, "complaints", parentNumber);
                Files.createDirectories(parentDir);
                Path targetFile = parentDir.resolve(safeFileName);
                attachment.transferTo(targetFile.toFile());
                storagePath = targetFile.toString();
            } catch (IOException e) {
                log.error("Failed to store duplicate-email attachment for parent {}", parentNumber);
            }

            draftAttachmentRepository.save(EmailDraftAttachment.builder()
                    .draftId(originDraftId)
                    .linkedComplaintNumber(parentNumber)
                    .fileName(safeFileName)
                    .fileType(attachment.getContentType())
                    .fileSize(attachment.getSize())
                    .storagePath(storagePath)
                    .ocrText("")
                    .ocrConfidence(0)
                    .uploadedBy("SYSTEM")
                    .build());
            linkedAttachments = 1;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", DraftStatus.DUPLICATE.name());
        response.put("draftCreated", false);
        response.put("isDuplicate", true);
        response.put("messageKey", "intake.duplicate_linked_to_parent");
        response.put("parentComplaintNumber", parentNumber);
        response.put("originDraftId", originDraftId);
        response.put("linkedAttachments", linkedAttachments);
        response.put("linkedEmails", linkedEmails);
        response.put("senderEmail", senderEmail);
        response.put("subject", subject);
        response.put("linkedAt", LocalDateTime.now().toString());
        return response;
    }

    @PostMapping("/drafts/{draftId}/convert")
    public Map<String, Object> convertDraft(@PathVariable String draftId) {
        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);
        if (draft == null) {
            return wrapResponse(Map.of("error", "Draft not found"));
        }

        draft.setStatus("CONVERTED");
        draft.setConvertedComplaintId(draft.getParentComplaintId());
        draft.setProcessedBy("System");
        draftRepository.save(draft);

        return wrapResponse(toResponseMap(draft));
    }

    @PostMapping("/drafts/{draftId}/reassign")
    public Map<String, Object> reassignDraft(@PathVariable String draftId, @RequestParam String targetDeoId) {
        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);
        if (draft == null) {
            return wrapResponse(Map.of("error", "Draft not found"));
        }

        draft.setAssignedTo(targetDeoId);
        draftRepository.save(draft);
        return wrapResponse(toResponseMap(draft));
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long total = draftRepository.count();
        long assigned = draftRepository.countByStatus("ASSIGNED");
        long converted = draftRepository.countByStatus("CONVERTED");
        long inProgress = draftRepository.countByStatus("IN_PROGRESS");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalDrafts", total);
        // These three were hardcoded zeros. ignoredCount in particular meant the suppression report
        // could never show that anything had been suppressed.
        stats.put("pendingCount", draftRepository.countByStatus(DraftStatus.PENDING_MANUAL_ENTRY.name()));
        stats.put("assignedCount", assigned);
        stats.put("inProgressCount", inProgress);
        stats.put("convertedCount", converted);
        stats.put("duplicateCount", draftRepository.countByIsDuplicateTrue());
        stats.put("ignoredCount", ignoredEmailLogRepository.count());
        stats.put("manualEntryCount", draftRepository.countByStatus(DraftStatus.PENDING_MANUAL_ENTRY.name()));
        stats.put("activeDeoCount", getDeoPool().size());

        return wrapResponse(stats);
    }

    // ─── Exceptional Email Master (ignore list) ───
    // Persisted in email_ignore_list. Rules were previously held in a synchronized ArrayList in this
    // controller and vanished on restart, so an admin's suppression silently stopped applying.

    @GetMapping("/ignore-list")
    public Map<String, Object> getIgnoreList() {
        List<Map<String, Object>> entries = ignoreRepository.findAll().stream()
                .map(this::toIgnoreEntryMap)
                .collect(Collectors.toList());
        return wrapResponse(entries);
    }

    @PostMapping("/ignore-list")
    public Map<String, Object> addToIgnoreList(@RequestBody Map<String, Object> request,
                                               jakarta.servlet.http.HttpServletRequest httpRequest) {
        EmailIgnoreEntry saved = ignoreRepository.save(buildIgnoreEntry(new EmailIgnoreEntry(), request, httpRequest));
        log.info("Ignore rule {} created by {}", saved.getId(), saved.getAddedBy());
        return wrapResponse(toIgnoreEntryMap(saved));
    }

    @PostMapping("/ignore-list/bulk")
    public Map<String, Object> bulkAddIgnoreList(@RequestBody List<Map<String, Object>> requests,
                                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> request : requests) {
            EmailIgnoreEntry saved = ignoreRepository.save(
                    buildIgnoreEntry(new EmailIgnoreEntry(), request, httpRequest));
            entries.add(toIgnoreEntryMap(saved));
        }
        return wrapResponse(entries);
    }

    private EmailIgnoreEntry buildIgnoreEntry(EmailIgnoreEntry entry, Map<String, Object> request,
                                              jakarta.servlet.http.HttpServletRequest httpRequest) {
        String pattern = (String) request.getOrDefault("emailPattern", request.getOrDefault("pattern", ""));
        String type = (String) request.getOrDefault("patternType", request.getOrDefault("type", "EXACT"));

        entry.setEmailPattern(pattern);
        entry.setPatternType(type == null ? "EXACT" : type.toUpperCase());
        entry.setMatchField(((String) request.getOrDefault("matchField", "FROM")).toUpperCase());
        entry.setToPattern((String) request.get("toPattern"));
        entry.setCcPattern((String) request.get("ccPattern"));
        entry.setBccPattern((String) request.get("bccPattern"));
        entry.setSubjectPattern((String) request.get("subjectPattern"));
        entry.setExceptionPattern((String) request.get("exceptionPattern"));
        entry.setReason((String) request.getOrDefault("reason", ""));
        if (request.containsKey("isActive")) {
            entry.setIsActive(Boolean.TRUE.equals(request.get("isActive")));
        } else if (entry.getIsActive() == null) {
            entry.setIsActive(true);
        }
        // Attributed to the caller, not the literal "admin" the old code stored for every row.
        if (entry.getId() == null) {
            entry.setAddedBy(resolveActor(httpRequest));
        }
        return entry;
    }

    private Map<String, Object> toIgnoreEntryMap(EmailIgnoreEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId());
        map.put("emailPattern", entry.getEmailPattern());
        map.put("pattern", entry.getEmailPattern());
        map.put("patternType", entry.getPatternType());
        map.put("type", entry.getPatternType());
        map.put("matchField", entry.getMatchField());
        map.put("toPattern", entry.getToPattern());
        map.put("ccPattern", entry.getCcPattern());
        map.put("bccPattern", entry.getBccPattern());
        map.put("subjectPattern", entry.getSubjectPattern());
        map.put("exceptionPattern", entry.getExceptionPattern());
        map.put("reason", entry.getReason());
        map.put("addedBy", entry.getAddedBy());
        map.put("isActive", Boolean.TRUE.equals(entry.getIsActive()));
        map.put("status", Boolean.TRUE.equals(entry.getIsActive()) ? "active" : "inactive");
        map.put("suppressedCount", entry.getId() == null ? 0
                : ignoredEmailLogRepository.countByMatchedRuleId(entry.getId()));
        map.put("createdAt", entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : "");
        return map;
    }

    private String resolveActor(jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (httpRequest == null) return "system";
        String user = httpRequest.getHeader("X-User-Id");
        return user == null || user.isBlank() ? "system" : user;
    }

    @GetMapping("/deo")
    public Map<String, Object> getDeos() {
        List<Map<String, Object>> keycloakDeos = keycloakUserService.getDeos();
        List<Map<String, Object>> deos = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> kc : keycloakDeos) {
            Map<String, Object> deo = new LinkedHashMap<>();
            deo.put("id", sortOrder);
            deo.put("userId", kc.get("userId"));
            deo.put("displayName", kc.get("displayName"));
            deo.put("email", kc.getOrDefault("email", ""));
            deo.put("isActive", Boolean.TRUE.equals(kc.get("enabled")));
            deo.put("isOnLeave", false);
            deo.put("maxThreshold", 20);
            deo.put("currentAssignedCount", draftRepository.findByAssignedToOrderByCreatedAtDesc(
                    (String) kc.get("displayName")).size());
            deo.put("sortOrder", sortOrder++);
            deos.add(deo);
        }
        return wrapResponse(deos);
    }

    @GetMapping("/deo/eligible")
    public Map<String, Object> getEligibleDeos() {
        return getDeos();
    }

    @PostMapping("/deo")
    public Map<String, Object> addDeo(@RequestBody Map<String, Object> request) {
        Map<String, Object> deo = new LinkedHashMap<>(request);
        deo.put("id", 4);
        deo.put("isActive", true);
        deo.put("isOnLeave", false);
        deo.put("currentAssignedCount", 0);
        deo.put("sortOrder", 4);
        return wrapResponse(deo);
    }

    @PutMapping("/deo/{userId}/threshold")
    public Map<String, Object> updateThreshold(@PathVariable String userId, @RequestParam int threshold) {
        Map<String, Object> deo = Map.of(
                "id", 1, "userId", userId, "displayName", "Updated User",
                "email", userId + "@rbi.org.in", "isActive", true, "isOnLeave", false,
                "maxThreshold", threshold, "currentAssignedCount", 0, "sortOrder", 1
        );
        return wrapResponse(deo);
    }

    @PutMapping("/deo/{userId}/status")
    public Map<String, Object> updateDeoStatus(@PathVariable String userId,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) Boolean onLeave) {
        Map<String, Object> deo = new LinkedHashMap<>();
        deo.put("id", 1);
        deo.put("userId", userId);
        deo.put("displayName", "Updated User");
        deo.put("email", userId + "@rbi.org.in");
        deo.put("isActive", active != null ? active : true);
        deo.put("isOnLeave", onLeave != null ? onLeave : false);
        deo.put("maxThreshold", 20);
        deo.put("currentAssignedCount", 0);
        deo.put("sortOrder", 1);
        return wrapResponse(deo);
    }

    @DeleteMapping("/deo/{userId}")
    public Map<String, Object> removeDeo(@PathVariable String userId) {
        return wrapResponse(null);
    }

    @PostMapping("/deo/reset-pointer")
    public Map<String, Object> resetPointer() {
        roundRobinPointer.set(0);
        return wrapResponse(Map.of("message", "Round-robin pointer reset", "pointer", 0));
    }

    @DeleteMapping("/ignore-list/{id}")
    public Map<String, Object> removeIgnoreEntry(@PathVariable Long id) {
        // Actually deletes. The previous implementation removed from an in-memory list, so the rule
        // reappeared on the next restart.
        if (!ignoreRepository.existsById(id)) {
            return wrapResponse(Map.of("deleted", false, "messageKey", "intake.ignore_rule_not_found"));
        }
        ignoreRepository.deleteById(id);
        log.info("Ignore rule {} deleted", id);
        return wrapResponse(Map.of("deleted", true, "id", id));
    }

    @PutMapping("/ignore-list/{id}")
    public Map<String, Object> updateIgnoreEntry(@PathVariable Long id, @RequestBody Map<String, Object> request,
                                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Previously an echo stub: it returned the request body and persisted nothing.
        EmailIgnoreEntry existing = ignoreRepository.findById(id).orElse(null);
        if (existing == null) {
            return wrapResponse(Map.of("updated", false, "messageKey", "intake.ignore_rule_not_found"));
        }
        EmailIgnoreEntry saved = ignoreRepository.save(buildIgnoreEntry(existing, request, httpRequest));
        log.info("Ignore rule {} updated", saved.getId());
        return wrapResponse(toIgnoreEntryMap(saved));
    }

    // ─── Ignored-email report (story: sender, subject, timestamp, matched rule + CSV export) ───

    @GetMapping("/ignored-emails")
    public Map<String, Object> getIgnoredEmails(
            @RequestParam(required = false) String senderEmail,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<IgnoredEmailLog> rows = ignoredEmailLogRepository.search(
                blankToNull(senderEmail), ruleId, parseDateTime(from, true), parseDateTime(to, false));

        List<Map<String, Object>> entries = rows.stream().map(this::toIgnoredEmailMap).collect(Collectors.toList());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", entries.size());
        payload.put("entries", entries);
        return wrapResponse(payload);
    }

    @GetMapping(value = "/ignored-emails/export", produces = "text/csv")
    public org.springframework.http.ResponseEntity<String> exportIgnoredEmails(
            @RequestParam(required = false) String senderEmail,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        List<IgnoredEmailLog> rows = ignoredEmailLogRepository.search(
                blankToNull(senderEmail), ruleId, parseDateTime(from, true), parseDateTime(to, false));

        StringBuilder csv = new StringBuilder();
        csv.append("Sender,Subject,Received At,Matched Rule ID,Matched Field,Matched Pattern,Pattern Type,Reason\n");
        for (IgnoredEmailLog row : rows) {
            csv.append(csvCell(row.getSenderEmail())).append(',')
               .append(csvCell(row.getSubject())).append(',')
               .append(csvCell(row.getReceivedAt() == null ? "" : row.getReceivedAt().toString())).append(',')
               .append(csvCell(row.getMatchedRuleId() == null ? "" : row.getMatchedRuleId().toString())).append(',')
               .append(csvCell(row.getMatchedRuleField())).append(',')
               .append(csvCell(row.getMatchedRulePattern())).append(',')
               .append(csvCell(row.getMatchedRuleType())).append(',')
               .append(csvCell(row.getMatchedRuleReason())).append('\n');
        }

        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"ignored-emails.csv\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csv.toString());
    }

    private Map<String, Object> toIgnoredEmailMap(IgnoredEmailLog row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row.getId());
        map.put("senderEmail", row.getSenderEmail());
        map.put("subject", row.getSubject());
        map.put("toRecipients", row.getToRecipients());
        map.put("ccRecipients", row.getCcRecipients());
        map.put("bccRecipients", row.getBccRecipients());
        map.put("messageId", row.getMessageId());
        map.put("matchedRuleId", row.getMatchedRuleId());
        map.put("matchedRulePattern", row.getMatchedRulePattern());
        map.put("matchedRuleField", row.getMatchedRuleField());
        map.put("matchedRuleType", row.getMatchedRuleType());
        map.put("matchedRuleReason", row.getMatchedRuleReason());
        map.put("receivedAt", row.getReceivedAt() == null ? "" : row.getReceivedAt().toString());
        return map;
    }

    /**
     * RFC 4180 quoting. A subject containing a comma or a quote would otherwise shift every later
     * column in the exported report.
     */
    private String csvCell(String value) {
        if (value == null || value.isEmpty()) return "";
        String escaped = value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        return '"' + escaped + '"';
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private LocalDateTime parseDateTime(String value, boolean startOfDay) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.length() <= 10) {
                java.time.LocalDate date = java.time.LocalDate.parse(value);
                return startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59);
            }
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Suggested related appeal (accept / dismiss) ───

    @PostMapping("/drafts/{draftId}/suggested-related")
    public Map<String, Object> decideSuggestedRelated(
            @PathVariable String draftId,
            @RequestBody Map<String, Object> request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);
        if (draft == null) {
            return wrapResponse(Map.of("error", "Draft not found", "messageKey", "intake.draft_not_found"));
        }

        String decision = String.valueOf(request.getOrDefault("decision", "")).toUpperCase();
        if (!"ACCEPTED".equals(decision) && !"DISMISSED".equals(decision)) {
            return wrapResponse(Map.of("error", "decision must be ACCEPTED or DISMISSED",
                    "messageKey", "intake.suggested_related_invalid_decision"));
        }

        draft.setSuggestedRelatedDraftId((String) request.get("relatedDraftId"));
        draft.setSuggestedRelatedDecision(decision);
        draft.setSuggestedRelatedDecidedBy(resolveActor(httpRequest));
        draft.setSuggestedRelatedDecidedAt(LocalDateTime.now());
        draftRepository.save(draft);

        return wrapResponse(toResponseMap(draft));
    }

    /**
     * Candidate related drafts: same complainant, similar matter. Was a hardcoded empty list.
     * Similarity is scored on the subject so an unrelated matter from the same sender ranks lower.
     */
    private List<Map<String, Object>> findSuggestedRelated(EmailDraft draft) {
        if (draft.getSenderEmail() == null || draft.getSenderEmail().isBlank()) {
            return List.of();
        }
        List<EmailDraft> candidates = draftRepository
                .findBySenderEmailIgnoreCaseAndDraftIdNotOrderByCreatedAtDesc(
                        draft.getSenderEmail(), draft.getDraftId());

        return candidates.stream()
                .map(c -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("draftId", c.getDraftId());
                    map.put("subject", c.getSubject());
                    map.put("category", c.getCategory());
                    map.put("status", c.getStatus());
                    map.put("modeOfReceipt", c.getModeOfReceipt());
                    map.put("parentComplaintId", c.getParentComplaintId());
                    map.put("convertedComplaintId", c.getConvertedComplaintId());
                    map.put("createdAt", c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());
                    map.put("similarityScore", similarityScore(draft, c));
                    map.put("link", "/aa/draft/" + c.getDraftId());
                    return map;
                })
                .sorted((a, b) -> Double.compare(
                        (Double) b.get("similarityScore"), (Double) a.get("similarityScore")))
                .limit(5)
                .collect(Collectors.toList());
    }

    /** Token overlap on subject, plus a bonus for the same category. */
    private double similarityScore(EmailDraft a, EmailDraft b) {
        double score = 0;
        if (a.getCategory() != null && a.getCategory().equalsIgnoreCase(b.getCategory())) {
            score += 0.4;
        }
        Set<String> tokensA = subjectTokens(a.getSubject());
        Set<String> tokensB = subjectTokens(b.getSubject());
        if (!tokensA.isEmpty() && !tokensB.isEmpty()) {
            Set<String> shared = new HashSet<>(tokensA);
            shared.retainAll(tokensB);
            Set<String> union = new HashSet<>(tokensA);
            union.addAll(tokensB);
            score += 0.6 * ((double) shared.size() / union.size());
        }
        return Math.round(score * 100) / 100.0;
    }

    private Set<String> subjectTokens(String subject) {
        if (subject == null || subject.isBlank()) return Set.of();
        return Arrays.stream(subject.toLowerCase().replaceAll("^(re|fwd|fw)\\s*:\\s*", "").split("[^a-z0-9]+"))
                .filter(t -> t.length() > 3)
                .collect(Collectors.toSet());
    }

    // ─── Helper methods ───

    private Map<String, Object> toResponseMap(EmailDraft draft) {
        List<EmailDraftAttachment> attachments = draftAttachmentRepository
                .findByDraftIdOrderByCreatedAtAsc(draft.getDraftId());

        List<Map<String, Object>> attachmentList = attachments.stream().map(a -> {
            Map<String, Object> att = new LinkedHashMap<>();
            att.put("id", "ATT-" + a.getId());
            att.put("fileName", a.getFileName());
            att.put("fileType", a.getFileType());
            att.put("fileSize", a.getFileSize());
            att.put("ocrText", a.getOcrText());
            att.put("ocrConfidence", a.getOcrConfidence());
            att.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : "");
            att.put("uploadedBy", a.getUploadedBy());
            return att;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", draft.getId());
        response.put("draftId", draft.getDraftId());
        response.put("displayId", "C" + String.format("%03d", draft.getId()));
        response.put("messageId", draft.getMessageId());
        response.put("senderEmail", draft.getSenderEmail());
        response.put("subject", draft.getSubject());
        response.put("body", draft.getBody());
        response.put("complainantName", draft.getComplainantName());
        response.put("complainantPhone", draft.getComplainantPhone());
        response.put("complainantAddress", draft.getComplainantAddress());
        response.put("complainantState", draft.getComplainantState());
        response.put("complainantDistrict", draft.getComplainantDistrict());
        response.put("complainantPincode", draft.getComplainantPincode());
        response.put("cpgramsNumber", draft.getCpgramsNumber());
        response.put("complaintSummary", draft.getComplaintSummary());
        response.put("category", draft.getCategory());
        response.put("modeOfReceipt", draft.getModeOfReceipt());
        response.put("status", draft.getStatus());
        response.put("assignedTo", draft.getAssignedTo());
        response.put("parentComplaintId", draft.getParentComplaintId());
        response.put("isDuplicate", draft.isDuplicate());
        response.put("ocrProcessed", draft.isOcrProcessed());
        response.put("ocrConfidence", draft.getOcrConfidence());
        response.put("entityName", draft.getEntityName());
        response.put("entityType", draft.getEntityType());
        response.put("amountInvolved", draft.getAmountInvolved());
        response.put("receivedAt", draft.getReceivedAt() != null ? draft.getReceivedAt().toString() : "");
        response.put("createdAt", draft.getCreatedAt() != null ? draft.getCreatedAt().toString() : "");
        response.put("processedBy", draft.getProcessedBy());
        response.put("convertedComplaintId", draft.getConvertedComplaintId());
        response.put("attachments", attachmentList);
        response.put("attachmentCount", draft.getAttachmentCount() != null
                ? draft.getAttachmentCount() : attachmentList.size());
        response.put("suggestedRelated", findSuggestedRelated(draft));
        response.put("suggestedRelatedDraftId", draft.getSuggestedRelatedDraftId());
        response.put("suggestedRelatedDecision", draft.getSuggestedRelatedDecision());
        response.put("suggestedRelatedDecidedBy", draft.getSuggestedRelatedDecidedBy());
        response.put("suggestedRelatedDecidedAt", draft.getSuggestedRelatedDecidedAt() == null ? ""
                : draft.getSuggestedRelatedDecidedAt().toString());

        // Recipient headers, now persisted so ignore rules on them are enforceable.
        response.put("toRecipients", draft.getToRecipients());
        response.put("ccRecipients", draft.getCcRecipients());
        response.put("bccRecipients", draft.getBccRecipients());
        response.put("replyTo", draft.getReplyTo());
        response.put("inReplyTo", draft.getInReplyTo());

        // OCR gating outcome, so the DO screen can explain why a field was not prefilled.
        response.put("ocrSkipReason", draft.getOcrSkipReason());
        response.put("requiresManualEntry", draft.isRequiresManualEntry());

        // DEO assessment
        response.put("deoDecision", draft.getDeoDecision());
        response.put("deoRemarks", draft.getDeoRemarks());
        response.put("nonMaintainableReason", draft.getNonMaintainableReason());

        // Reviewer
        response.put("reviewerDecision", draft.getReviewerDecision());
        response.put("reviewerRemarks", draft.getReviewerRemarks());
        response.put("targetOffice", draft.getTargetOffice());

        // Scheme & Auto-closure
        response.put("schemeVersion", draft.getSchemeVersion());
        response.put("closureClause", draft.getClosureClause());
        response.put("autoClosureResponsesJson", draft.getAutoClosureResponsesJson());
        response.put("subJudice", draft.isSubJudice());
        response.put("notAComplaintReason", draft.getNotAComplaintReason());
        response.put("notAComplaintOthersReason", draft.getNotAComplaintOthersReason());
        response.put("suggestionDepartment", draft.getSuggestionDepartment());
        response.put("suggestionNature", draft.getSuggestionNature());

        // Language info
        response.put("detectedLanguage", draft.getDetectedLanguage());
        response.put("languageName", draft.getLanguageName());
        response.put("isVernacular", draft.isVernacular());
        response.put("translationConfidence", draft.getTranslationConfidence());

        // OCR extracted fields
        if (draft.isOcrProcessed() && draft.getOcrExtractedFieldsJson() != null && !draft.getOcrExtractedFieldsJson().isEmpty()) {
            try {
                Map<String, String> ocrFields = objectMapper.readValue(
                        draft.getOcrExtractedFieldsJson(), new TypeReference<Map<String, String>>() {});
                response.put("ocrExtractedFields", ocrFields);
            } catch (Exception e) {
                log.warn("Failed to parse OCR fields JSON for draft {}", draft.getDraftId());
            }
        }

        return response;
    }

    private boolean isCrpcOnlyInCcBcc(String to, String cc, String bcc) {
        String crpcPattern = "crpc";
        boolean inTo = to != null && to.toLowerCase().contains(crpcPattern);
        boolean inCc = cc != null && cc.toLowerCase().contains(crpcPattern);
        boolean inBcc = bcc != null && bcc.toLowerCase().contains(crpcPattern);
        return !inTo && (inCc || inBcc);
    }

    private Map<String, Object> wrapResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    private static final Set<String> EMAIL_NOISE_WORDS = Set.of(
            "card", "mail", "email", "official", "work", "personal", "info",
            "contact", "help", "support", "noreply", "no-reply", "admin",
            "test", "user", "account", "service", "complaint", "grievance"
    );

    private String extractName(String email) {
        if (email == null || email.isEmpty()) return "Unknown";
        String local = email.split("@")[0];
        String[] parts = local.split("[._\\-]");
        String name = Arrays.stream(parts)
                .filter(p -> !p.isEmpty() && !p.matches("\\d+") && !EMAIL_NOISE_WORDS.contains(p.toLowerCase()))
                .map(p -> p.substring(0, 1).toUpperCase() + p.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
        return name.isEmpty() ? "Unknown" : name;
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "Unknown";
        // Take only the first line (signatures often have name on first line, then designation/noise below)
        String firstLine = name.split("[\\r\\n]")[0].trim();
        // Remove trailing noise words
        String[] words = firstLine.split("\\s+");
        List<String> cleaned = new ArrayList<>();
        for (String w : words) {
            if (EMAIL_NOISE_WORDS.contains(w.toLowerCase())) continue;
            cleaned.add(w);
        }
        String result = String.join(" ", cleaned).trim();
        return result.isEmpty() ? name.split("[\\r\\n]")[0].trim() : result;
    }

    private Double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) return null;
        try {
            return Double.parseDouble(amountStr.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
