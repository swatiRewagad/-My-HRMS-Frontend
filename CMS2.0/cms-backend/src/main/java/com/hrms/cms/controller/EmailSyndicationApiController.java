package com.hrms.cms.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.dto.simulation.EmailThreadResponse;
import com.hrms.cms.dto.simulation.SimulatedEmailResponse;
import com.hrms.cms.dto.syndication.DeoResponse;
import com.hrms.cms.dto.syndication.DraftCreatedResponse;
import com.hrms.cms.dto.syndication.EmailAutoCloseResponse;
import com.hrms.cms.dto.syndication.EmailDraftAttachmentResponse;
import com.hrms.cms.dto.syndication.EmailDraftResponse;
import com.hrms.cms.dto.syndication.EmailIngestResult;
import com.hrms.cms.dto.syndication.IgnoreListEntryResponse;
import com.hrms.cms.dto.syndication.PointerResetResponse;
import com.hrms.cms.dto.syndication.SyndicationStatsResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.entity.EmailDraftAttachment;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.EmailDraftAttachmentRepository;
import com.hrms.cms.repository.EmailDraftRepository;
import com.hrms.cms.service.ComplaintCreationFinalizer;
import com.hrms.cms.service.ComplaintNumberGeneratorService;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.EmailSimulationService;
import com.hrms.cms.service.KeycloakUserService;
import com.hrms.cms.service.LanguageTranslationService;
import com.hrms.cms.service.OcrExtractionService;
import com.hrms.cms.service.RbioComplaintSummaryService;
import com.hrms.cms.service.RuleBasedExtractor;
import com.rbi.cms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final ComplaintNumberGeneratorService complaintNumberGenerator;
    private final RbioComplaintSummaryService rbioComplaintSummaryService;
    private final ComplaintCreationFinalizer creationFinalizer;
    private final com.hrms.cms.service.DraftIdGeneratorService draftIdGeneratorService;
    private final com.hrms.cms.repository.OfficerAvailabilityRepository officerAvailabilityRepository;
    private final ObjectMapper objectMapper;

    @Value("${cms.attachments.root-path:C:/cms-attachments}")
    private String attachmentsRootPath;

    private final AtomicInteger roundRobinPointer = new AtomicInteger(0);
    private final List<IgnoreListEntryResponse> ignoreListStore = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger ignoreIdSeq = new AtomicInteger(1);

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
    public ResponseEntity<ApiResponse<EmailIngestResult>> ingestEmail(@RequestBody Map<String, Object> request) {
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
            return wrapResponse(autoClosed(senderEmail, subject, "CRPC in CC/BCC — not a direct complaint"));
        }

        return processIngest(senderEmail, subject, body, messageId, null);
    }

    @PostMapping(value = "/ingest-with-attachment", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<EmailIngestResult>> ingestEmailWithAttachment(
            @RequestParam("senderEmail") String senderEmail,
            @RequestParam("subject") String subject,
            @RequestParam(value = "body", required = false, defaultValue = "") String body,
            @RequestParam(value = "messageId", required = false, defaultValue = "") String messageId,
            @RequestParam("attachment") MultipartFile attachment) {

        return processIngest(senderEmail, subject, body, messageId, attachment);
    }

    private ResponseEntity<ApiResponse<EmailIngestResult>> processIngest(
            String senderEmail, String subject, String body, String messageId, MultipartFile attachment) {
        // Check ignore list — auto-close if sender matches
        if (isOnIgnoreList(senderEmail)) {
            log.info("Email from {} is on ignore list — auto-closing as Non-Complaint", senderEmail);
            return wrapResponse(autoClosed(senderEmail, subject, "Sender is on ignore list"));
        }

        IncomingEmailRequest emailReq = new IncomingEmailRequest();
        emailReq.setFromEmail(senderEmail);
        emailReq.setFromName(extractName(senderEmail));
        emailReq.setSubject(subject);
        emailReq.setBody(body);

        EmailThreadResponse result = emailService.receiveEmail(emailReq);

        String complaintNumber = result.getComplaintNumber();
        String threadId = result.getThreadId();
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

        if (attachment != null && !attachment.isEmpty()) {
            String contentType = attachment.getContentType();
            Set<String> allowedTypes = Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff");

            if (contentType != null && allowedTypes.contains(contentType)) {
                try {
                    byte[] fileBytes = attachment.getBytes();
                    ocrExtracted = ocrService.extractFromImage(fileBytes, contentType);

                    if (!ocrExtracted.isEmpty()) {
                        ocrProcessed = true;
                        ocrConfidence = 85;

                        if (ocrExtracted.containsKey("complainantName") && !ocrExtracted.get("complainantName").isEmpty()) {
                            complainantName = ocrExtracted.get("complainantName");
                        }
                        if (ocrExtracted.containsKey("complainantPhone") && !ocrExtracted.get("complainantPhone").isEmpty()) {
                            complainantPhone = ocrExtracted.get("complainantPhone");
                        }
                        if (ocrExtracted.containsKey("category") && !ocrExtracted.get("category").isEmpty()) {
                            category = ocrExtracted.get("category");
                        }
                        if (ocrExtracted.containsKey("subject") && !ocrExtracted.get("subject").isEmpty()) {
                            complaintSummary = ocrExtracted.get("subject");
                        }

                        log.info("OCR extracted {} fields from attachment for complaint {}",
                                ocrExtracted.size(), complaintNumber);
                    }
                } catch (Exception e) {
                    log.error("OCR processing failed for attachment: {}", e.getMessage());
                }
            }

        }

        // Language detection and translation
        Map<String, Object> languageResult = translationService.detectAndTranslate(body);
        boolean isVernacular = (boolean) languageResult.getOrDefault("isVernacular", false);
        String translatedBody = (String) languageResult.getOrDefault("translatedText", body);

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

        String generatedDraftId = draftIdGeneratorService.generateDraftId();

        EmailDraft draft = EmailDraft.builder()
                .draftId(generatedDraftId)
                .threadId(threadId)
                .messageId(messageId == null || messageId.isEmpty() ? UUID.randomUUID().toString() : messageId)
                .senderEmail(senderEmail)
                .subject(subject)
                .body(isVernacular ? translatedBody : body)
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
                .status("ASSIGNED")
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
                .translationConfidence(languageResult.get("confidence") != null ?
                        ((Number) languageResult.get("confidence")).doubleValue() : null)
                .translatedBody(isVernacular ? body : null)
                .receivedAt(LocalDateTime.now())
                .build();

        // Stamp scheme version at creation time (UST190)
        draft.setSchemeVersion("RBIOS_2026");

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

        return wrapResponse(toDraftResponse(saved));
    }

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<EmailDraftResponse>>> getQueue(
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

        return wrapResponse(drafts.stream()
                .map(this::toDraftResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> getDraft(@PathVariable String draftId) {
        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);

        // Fallback: try looking up by displayId (e.g., C017 → id=17)
        if (draft == null && draftId.matches("C\\d+")) {
            try {
                long numericId = Long.parseLong(draftId.substring(1));
                draft = draftRepository.findById(numericId).orElse(null);
            } catch (NumberFormatException ignored) {}
        }

        if (draft != null) {
            return wrapResponse(toDraftResponse(draft));
        }

        // Fallback: try to find from email thread (for legacy data before migration)
        try {
            EmailThreadResponse thread = emailService.getThread(draftId);
            String emailBody = "";
            String senderEmail = thread.getFromEmail();
            String sentAt = "";
            SimulatedEmailResponse inbound = thread.getEmails().stream()
                    .filter(e -> "INBOUND".equals(e.getDirection()))
                    .findFirst()
                    .orElse(null);
            if (inbound != null) {
                emailBody = inbound.getBody() != null ? inbound.getBody() : "";
                if (inbound.getSentAt() != null) sentAt = inbound.getSentAt().toString();
            }

            String timestamp = sentAt.isEmpty() ? LocalDateTime.now().toString() : sentAt;
            // Reconstructed from the thread, so only what the thread can answer is set and the rest stay
            // null. It used to emit a narrower key set than the row-backed branch above, which meant one
            // endpoint returned two different shapes; the shape is now the same either way.
            return wrapResponse(EmailDraftResponse.builder()
                    .id(0L)
                    .draftId(draftId)
                    .messageId(UUID.randomUUID().toString())
                    .senderEmail(senderEmail)
                    .subject(thread.getSubject())
                    .body(emailBody)
                    .complainantName(extractName(senderEmail))
                    .complainantPhone("")
                    .cpgramsNumber("")
                    .complaintSummary(thread.getSubject())
                    .category("General")
                    .modeOfReceipt("EMAIL")
                    .status("ASSIGNED")
                    .assignedTo(assignToNextDeo())
                    .parentComplaintId(thread.getComplaintNumber())
                    .isDuplicate(false)
                    .ocrProcessed(false)
                    .ocrConfidence(0)
                    .receivedAt(timestamp)
                    .createdAt(timestamp)
                    .processedBy("")
                    .convertedComplaintId("")
                    .build());
        } catch (Exception e) {
            // Was a 200 carrying {error: …} with success=true, so callers could not tell a missing draft
            // from a real one and silently rendered a blank form.
            return draftNotFound(draftId);
        }
    }

    private static ResponseEntity<ApiResponse<EmailDraftResponse>> draftNotFound(String draftId) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Draft not found: " + draftId));
    }

    @PostMapping(value = "/drafts/physical-letter", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> createPhysicalLetterDraft(
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
            // ─── Eligibility fields ───
            @RequestParam(value = "proposedComplaintType", required = false, defaultValue = "") String proposedComplaintType,
            @RequestParam(value = "notComplaintReason", required = false, defaultValue = "") String notComplaintReason,
            @RequestParam(value = "eligibilityQuestions", required = false, defaultValue = "") String eligibilityQuestions,
            // ─── Entity detail fields ───
            @RequestParam(value = "entityCategory", required = false, defaultValue = "") String entityCategory,
            @RequestParam(value = "entityTypeDetail", required = false, defaultValue = "") String entityTypeDetail,
            @RequestParam(value = "entityBsrCode", required = false, defaultValue = "") String entityBsrCode,
            @RequestParam(value = "entityPincode", required = false, defaultValue = "") String entityPincode,
            @RequestParam(value = "entityCountry", required = false, defaultValue = "") String entityCountry,
            @RequestParam(value = "entityState", required = false, defaultValue = "") String entityState,
            @RequestParam(value = "entityDistrict", required = false, defaultValue = "") String entityDistrict,
            @RequestParam(value = "entityCity", required = false, defaultValue = "") String entityCity,
            @RequestParam(value = "entityBranchName", required = false, defaultValue = "") String entityBranchName,
            @RequestParam(value = "entityBranchCategory", required = false, defaultValue = "") String entityBranchCategory,
            @RequestParam(value = "entityAddress", required = false, defaultValue = "") String entityAddress,
            @RequestParam(value = "entityBranchCenterName", required = false, defaultValue = "") String entityBranchCenterName,
            @RequestParam(value = "cosmosCode", required = false, defaultValue = "") String cosmosCode,
            @RequestParam(value = "assetSize", required = false, defaultValue = "") String assetSize,
            @RequestParam(value = "isDepositTaking", required = false) String isDepositTaking,
            @RequestParam(value = "isAssetAbove100Cr", required = false) String isAssetAbove100Cr,
            @RequestParam(value = "isLiquidated", required = false) String isLiquidated,
            // ─── Complainant extended fields ───
            @RequestParam(value = "otherEntityName", required = false, defaultValue = "") String otherEntityName,
            @RequestParam(value = "dateOfRegistrationWithRBI", required = false, defaultValue = "") String dateOfRegistrationWithRBI,
            @RequestParam(value = "complaintCategory", required = false, defaultValue = "") String complaintCategory,
            @RequestParam(value = "complaintSubCategory1", required = false, defaultValue = "") String complaintSubCategory1,
            @RequestParam(value = "complaintSubCategory2", required = false, defaultValue = "") String complaintSubCategory2,
            @RequestParam(value = "dateOfFilingComplaint", required = false, defaultValue = "") String dateOfFilingComplaint,
            @RequestParam(value = "complaintRegDateValid", required = false, defaultValue = "") String complaintRegDateValid,
            @RequestParam(value = "reminderSentByComplainant", required = false, defaultValue = "") String reminderSentByComplainant,
            @RequestParam(value = "disputedAmountInvolved", required = false, defaultValue = "") String disputedAmountInvolved,
            @RequestParam(value = "dateOfFilingForFinancial", required = false, defaultValue = "") String dateOfFilingForFinancial,
            @RequestParam(value = "compensationSought", required = false, defaultValue = "") String compensationSought,
            @RequestParam(value = "loanDisposalAmount", required = false, defaultValue = "") String loanDisposalAmount,
            @RequestParam(value = "additionalComments", required = false, defaultValue = "") String additionalComments,
            @RequestParam(value = "crpcProposedAction", required = false, defaultValue = "") String crpcProposedAction,
            @RequestParam(value = "vernacularLanguageDetail", required = false, defaultValue = "") String vernacularLanguageDetail,
            @RequestParam(value = "legalCaseFiled", required = false, defaultValue = "") String legalCaseFiled,
            @RequestParam(value = "legalDateOfFiling", required = false, defaultValue = "") String legalDateOfFiling,
            @RequestParam(value = "preEnquiryReceived", required = false, defaultValue = "") String preEnquiryReceived,
            @RequestParam(value = "highPriorityComplaint", required = false, defaultValue = "") String highPriorityComplaint,
            @RequestParam(value = "isRegardingPension", required = false, defaultValue = "") String isRegardingPension,
            @RequestParam(value = "isAgainstBusinessCorrespondent", required = false, defaultValue = "") String isAgainstBusinessCorrespondent,
            @RequestParam(value = "isAtmCreditDebitCard", required = false, defaultValue = "") String isAtmCreditDebitCard,
            @RequestParam(value = "schemeFlag", required = false, defaultValue = "") String schemeFlag,
            @RequestParam(value = "isFreeMarkedComplaint", required = false, defaultValue = "") String isFreeMarkedComplaint,
            @RequestParam(value = "currentComplaintNumber", required = false, defaultValue = "") String currentComplaintNumber,
            @RequestParam(value = "receivedReplyWithin30Days", required = false, defaultValue = "") String receivedReplyWithin30Days,
            @RequestParam(value = "declarationAccepted", required = false) String declarationAccepted,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment) {

        try {
            String draftId = draftIdGeneratorService.generateDraftId();

            EmailDraft draft = EmailDraft.builder()
                    .draftId(draftId)
                    .threadId(UUID.randomUUID().toString())
                    .messageId(UUID.randomUUID().toString())
                    .senderEmail(senderEmail)
                    .subject(subject)
                    .body(body)
                    .complainantName(complainantName)
                    .complainantPhone(complainantPhone)
                    .complainantAddress(complainantAddress)
                    .complainantState(complainantState)
                    .complainantDistrict(complainantDistrict)
                    .complainantPincode(complainantPincode)
                    .category(category)
                    .entityName(entityName)
                    .entityType(entityType)
                    .modeOfReceipt(modeOfReceipt)
                    .status(status)
                    .assignedTo(assignedTo)
                    .processedBy(processedBy)
                    .amountInvolved(parseAmount(amountInvolved != null ? amountInvolved : ""))
                    .isDuplicate(false)
                    .ocrProcessed(true)
                    // Eligibility
                    .proposedComplaintType(proposedComplaintType)
                    .notComplaintReason(notComplaintReason)
                    .eligibilityQuestionsJson(eligibilityQuestions)
                    // Entity details
                    .entityCategory(entityCategory)
                    .entityTypeDetail(entityTypeDetail)
                    .entityBsrCode(entityBsrCode)
                    .entityPincode(entityPincode)
                    .entityCountry(entityCountry)
                    .entityState(entityState)
                    .entityDistrict(entityDistrict)
                    .entityCity(entityCity)
                    .entityBranchName(entityBranchName)
                    .entityBranchCategory(entityBranchCategory)
                    .entityAddress(entityAddress)
                    .entityBranchCenterName(entityBranchCenterName)
                    .cosmosCode(cosmosCode)
                    .assetSize(assetSize)
                    .isDepositTaking(parseBoolean(isDepositTaking))
                    .isAssetAbove100Cr(parseBoolean(isAssetAbove100Cr))
                    .isLiquidated(parseBoolean(isLiquidated))
                    // Complainant extended
                    .otherEntityName(otherEntityName)
                    .dateOfRegistrationWithRBI(dateOfRegistrationWithRBI)
                    .complaintCategory(complaintCategory)
                    .complaintSubCategory1(complaintSubCategory1)
                    .complaintSubCategory2(complaintSubCategory2)
                    .dateOfFilingComplaint(dateOfFilingComplaint)
                    .complaintRegDateValid(complaintRegDateValid)
                    .reminderSentByComplainant(reminderSentByComplainant)
                    .disputedAmountInvolved(disputedAmountInvolved)
                    .dateOfFilingForFinancial(dateOfFilingForFinancial)
                    .compensationSought(compensationSought)
                    .loanDisposalAmount(loanDisposalAmount)
                    .additionalComments(additionalComments)
                    .crpcProposedAction(crpcProposedAction)
                    .vernacularLanguageDetail(vernacularLanguageDetail)
                    // Legal
                    .legalCaseFiled(legalCaseFiled)
                    .legalDateOfFiling(legalDateOfFiling)
                    .preEnquiryReceived(preEnquiryReceived)
                    // Flags
                    .highPriorityComplaint(highPriorityComplaint)
                    .isRegardingPension(isRegardingPension)
                    .isAgainstBusinessCorrespondent(isAgainstBusinessCorrespondent)
                    .isAtmCreditDebitCard(isAtmCreditDebitCard)
                    .schemeFlag(schemeFlag)
                    .isFreeMarkedComplaint(isFreeMarkedComplaint)
                    // Linkage
                    .currentComplaintNumber(currentComplaintNumber)
                    .receivedReplyWithin30Days(receivedReplyWithin30Days)
                    // Declaration
                    .declarationAccepted(parseBoolean(declarationAccepted))
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
                        .ocrText("")
                        .ocrConfidence(0)
                        .uploadedBy(assignedTo.isEmpty() ? "DEO" : assignedTo)
                        .build();
                draftAttachmentRepository.save(att);
            }

            return wrapResponse(toDraftResponse(saved));
        } catch (Exception e) {
            log.error("Physical letter draft creation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Could not create the physical letter draft"));
        }
    }

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<DraftCreatedResponse>> createDraft(@RequestBody Map<String, Object> request) {
        try {
            String draftId = (String) request.get("draftId");
            EmailDraft draft;
            if (draftId != null && !draftId.isBlank()) {
                draft = draftRepository.findByDraftId(draftId).orElse(new EmailDraft());
                draft.setDraftId(draftId);
            } else {
                draft = new EmailDraft();
                draft.setDraftId(draftIdGeneratorService.generateDraftId());
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
            draft.setReceivedAt(java.time.LocalDateTime.now());

            draftRepository.save(draft);
            return wrapResponse(DraftCreatedResponse.builder()
                    .draftId(draft.getDraftId())
                    .status(draft.getStatus())
                    .build());
        } catch (Exception e) {
            log.error("Draft creation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Could not save the draft"));
        }
    }

    @PutMapping("/drafts/{draftId}")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> updateDraft(
            @PathVariable String draftId, @RequestBody Map<String, Object> request) {
        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);
        if (draft == null && draftId.matches("C\\d+")) {
            try {
                long numericId = Long.parseLong(draftId.substring(1));
                draft = draftRepository.findById(numericId).orElse(null);
            } catch (NumberFormatException ignored) {}
        }
        if (draft == null) {
            return draftNotFound(draftId);
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

        // Eligibility
        if (request.containsKey("proposedComplaintType")) draft.setProposedComplaintType((String) request.get("proposedComplaintType"));
        if (request.containsKey("notComplaintReason")) draft.setNotComplaintReason((String) request.get("notComplaintReason"));
        if (request.containsKey("eligibilityQuestionsJson")) draft.setEligibilityQuestionsJson((String) request.get("eligibilityQuestionsJson"));

        // Entity details
        if (request.containsKey("entityCategory")) draft.setEntityCategory((String) request.get("entityCategory"));
        if (request.containsKey("entityTypeDetail")) draft.setEntityTypeDetail((String) request.get("entityTypeDetail"));
        if (request.containsKey("entityBsrCode")) draft.setEntityBsrCode((String) request.get("entityBsrCode"));
        if (request.containsKey("entityPincode")) draft.setEntityPincode((String) request.get("entityPincode"));
        if (request.containsKey("entityCountry")) draft.setEntityCountry((String) request.get("entityCountry"));
        if (request.containsKey("entityState")) draft.setEntityState((String) request.get("entityState"));
        if (request.containsKey("entityDistrict")) draft.setEntityDistrict((String) request.get("entityDistrict"));
        if (request.containsKey("entityCity")) draft.setEntityCity((String) request.get("entityCity"));
        if (request.containsKey("entityBranchName")) draft.setEntityBranchName((String) request.get("entityBranchName"));
        if (request.containsKey("entityBranchCategory")) draft.setEntityBranchCategory((String) request.get("entityBranchCategory"));
        if (request.containsKey("entityAddress")) draft.setEntityAddress((String) request.get("entityAddress"));
        if (request.containsKey("entityBranchCenterName")) draft.setEntityBranchCenterName((String) request.get("entityBranchCenterName"));
        if (request.containsKey("cosmosCode")) draft.setCosmosCode((String) request.get("cosmosCode"));
        if (request.containsKey("assetSize")) draft.setAssetSize((String) request.get("assetSize"));
        if (request.containsKey("isDepositTaking")) draft.setIsDepositTaking(Boolean.TRUE.equals(request.get("isDepositTaking")));
        if (request.containsKey("isAssetAbove100Cr")) draft.setIsAssetAbove100Cr(Boolean.TRUE.equals(request.get("isAssetAbove100Cr")));
        if (request.containsKey("isLiquidated")) draft.setIsLiquidated(Boolean.TRUE.equals(request.get("isLiquidated")));

        // Complainant extended
        if (request.containsKey("otherEntityName")) draft.setOtherEntityName((String) request.get("otherEntityName"));
        if (request.containsKey("dateOfRegistrationWithRBI")) draft.setDateOfRegistrationWithRBI((String) request.get("dateOfRegistrationWithRBI"));
        if (request.containsKey("complaintCategory")) draft.setComplaintCategory((String) request.get("complaintCategory"));
        if (request.containsKey("complaintSubCategory1")) draft.setComplaintSubCategory1((String) request.get("complaintSubCategory1"));
        if (request.containsKey("complaintSubCategory2")) draft.setComplaintSubCategory2((String) request.get("complaintSubCategory2"));
        if (request.containsKey("dateOfFilingComplaint")) draft.setDateOfFilingComplaint((String) request.get("dateOfFilingComplaint"));
        if (request.containsKey("complaintRegDateValid")) draft.setComplaintRegDateValid((String) request.get("complaintRegDateValid"));
        if (request.containsKey("reminderSentByComplainant")) draft.setReminderSentByComplainant((String) request.get("reminderSentByComplainant"));
        if (request.containsKey("disputedAmountInvolved")) draft.setDisputedAmountInvolved((String) request.get("disputedAmountInvolved"));
        if (request.containsKey("dateOfFilingForFinancial")) draft.setDateOfFilingForFinancial((String) request.get("dateOfFilingForFinancial"));
        if (request.containsKey("compensationSought")) draft.setCompensationSought((String) request.get("compensationSought"));
        if (request.containsKey("loanDisposalAmount")) draft.setLoanDisposalAmount((String) request.get("loanDisposalAmount"));
        if (request.containsKey("additionalComments")) draft.setAdditionalComments((String) request.get("additionalComments"));
        if (request.containsKey("crpcProposedAction")) draft.setCrpcProposedAction((String) request.get("crpcProposedAction"));
        if (request.containsKey("vernacularLanguageDetail")) draft.setVernacularLanguageDetail((String) request.get("vernacularLanguageDetail"));

        // Legal
        if (request.containsKey("legalCaseFiled")) draft.setLegalCaseFiled((String) request.get("legalCaseFiled"));
        if (request.containsKey("legalDateOfFiling")) draft.setLegalDateOfFiling((String) request.get("legalDateOfFiling"));
        if (request.containsKey("preEnquiryReceived")) draft.setPreEnquiryReceived((String) request.get("preEnquiryReceived"));

        // Flags
        if (request.containsKey("highPriorityComplaint")) draft.setHighPriorityComplaint((String) request.get("highPriorityComplaint"));
        if (request.containsKey("isRegardingPension")) draft.setIsRegardingPension((String) request.get("isRegardingPension"));
        if (request.containsKey("isAgainstBusinessCorrespondent")) draft.setIsAgainstBusinessCorrespondent((String) request.get("isAgainstBusinessCorrespondent"));
        if (request.containsKey("isAtmCreditDebitCard")) draft.setIsAtmCreditDebitCard((String) request.get("isAtmCreditDebitCard"));
        if (request.containsKey("schemeFlag")) draft.setSchemeFlag((String) request.get("schemeFlag"));
        if (request.containsKey("isFreeMarkedComplaint")) draft.setIsFreeMarkedComplaint((String) request.get("isFreeMarkedComplaint"));

        // Linkage
        if (request.containsKey("currentComplaintNumber")) draft.setCurrentComplaintNumber((String) request.get("currentComplaintNumber"));
        if (request.containsKey("receivedReplyWithin30Days")) draft.setReceivedReplyWithin30Days((String) request.get("receivedReplyWithin30Days"));

        // Declaration
        if (request.containsKey("declarationAccepted")) draft.setDeclarationAccepted(Boolean.TRUE.equals(request.get("declarationAccepted")));

        draftRepository.save(draft);

        // When reviewer approves (any of the 3 approval decisions the reviewer screen offers -
        // plain approve, approve+route to another dept, or approve+vernacular) → create a
        // Complaint record, generate its complaint number, and route to RBIO/CEPC. This used to
        // check only "APPROVED_ROUTED", so approving via the other two decisions silently never
        // generated a complaint number at all.
        String newStatus = (String) request.get("status");
        boolean isApproval = "APPROVED_ROUTED".equals(newStatus)
                || "APPROVED_SENT_TO_OTHER_DEPT".equals(newStatus)
                || "APPROVED_VERNACULAR".equals(newStatus);
        if (isApproval && (draft.getConvertedComplaintId() == null || draft.getConvertedComplaintId().isBlank())) {
            createComplaintFromDraft(draft);
        }

        return wrapResponse(toDraftResponse(draft));
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

        // Generate complaint number using RBIO-style sequence (N + FY + OfficeCode + Seq)
        String complaintNumber;
        try {
            complaintNumber = complaintNumberGenerator.generateComplaintNumber(
                    department,
                    draft.getComplainantState() != null ? draft.getComplainantState() : "",
                    draft.getComplainantDistrict() != null ? draft.getComplainantDistrict() : "",
                    true
            );
        } catch (Exception e) {
            log.warn("Complaint number generator failed, using fallback: {}", e.getMessage());
            String dateStr = LocalDateTime.now().toString().substring(0, 10).replace("-", "");
            String rand = String.valueOf((int) (100000 + Math.random() * 900000));
            complaintNumber = "CMP-" + dateStr + "-" + rand;
        }

        Complaint complaint = Complaint.builder()
                .complaintNumber(complaintNumber)
                .originDraftId(draft.getDraftId())
                .complainantName(draft.getComplainantName() != null ? draft.getComplainantName() : "Unknown")
                .complainantEmail(draft.getSenderEmail())
                .complainantPhone(draft.getComplainantPhone())
                .complainantAddress(draft.getComplainantAddress())
                .complainantState(draft.getComplainantState())
                .complainantDistrict(draft.getComplainantDistrict())
                .complainantPincode(draft.getComplainantPincode())
                .subject(draft.getSubject() != null ? draft.getSubject() : "Email Complaint")
                .description(draft.getBody())
                .categoryName(draft.getCategory())
                .status("assigned")
                .priority("medium")
                .filingType(draft.getModeOfReceipt() != null ? draft.getModeOfReceipt() : "EMAIL")
                .department(department)
                .assignedRole(assignedRole)
                .assignedOfficer(assignedUser)
                .entityCode(entityName)
                .entityCategory(draft.getEntityCategory())
                .entityBsrCode(draft.getEntityBsrCode())
                .entityPincode(draft.getEntityPincode())
                .entityState(draft.getEntityState())
                .entityDistrict(draft.getEntityDistrict())
                .entityCity(draft.getEntityCity())
                .entityBranchName(draft.getEntityBranchName())
                .entityBranchCategory(draft.getEntityBranchCategory())
                .entityAddress(draft.getEntityAddress())
                .cosmosCode(draft.getCosmosCode())
                .schemeVersion(draft.getSchemeVersion())
                .closureClause(draft.getClosureClause())
                .workflowStage("INITIAL_REVIEW")
                .build();

        creationFinalizer.applyOffice(complaint, department);

        Complaint saved = complaintRepository.save(complaint);
        rbioComplaintSummaryService.backfillFromEmailDraft(draft, saved.getId());
        creationFinalizer.afterSave(saved);

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

    private boolean isOnIgnoreList(String senderEmail) {
        if (senderEmail == null || senderEmail.isBlank()) return false;
        String emailLower = senderEmail.toLowerCase().trim();
        for (IgnoreListEntryResponse entry : ignoreListStore) {
            if (Boolean.FALSE.equals(entry.getIsActive())) continue;
            String pattern = entry.getEmailPattern() == null
                    ? "" : entry.getEmailPattern().toLowerCase().trim();
            if (pattern.isEmpty()) continue;
            String type = entry.getPatternType() == null
                    ? "EXACT" : entry.getPatternType().toUpperCase();
            switch (type) {
                case "EXACT":
                    if (emailLower.equals(pattern)) return true;
                    break;
                case "DOMAIN":
                    if (emailLower.endsWith("@" + pattern) || emailLower.endsWith("." + pattern)) return true;
                    break;
                case "CONTAINS":
                    if (emailLower.contains(pattern)) return true;
                    break;
            }
        }
        return false;
    }

    @PostMapping("/drafts/{draftId}/convert")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> convertDraft(@PathVariable String draftId) {
        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);
        if (draft == null) {
            return draftNotFound(draftId);
        }

        draft.setStatus("CONVERTED");
        draft.setConvertedComplaintId(draft.getParentComplaintId());
        draft.setProcessedBy("System");
        draftRepository.save(draft);

        return wrapResponse(toDraftResponse(draft));
    }

    @PostMapping("/drafts/{draftId}/reassign")
    public ResponseEntity<ApiResponse<EmailDraftResponse>> reassignDraft(
            @PathVariable String draftId, @RequestParam String targetDeoId) {
        EmailDraft draft = draftRepository.findByDraftId(draftId).orElse(null);
        if (draft == null) {
            return draftNotFound(draftId);
        }

        draft.setAssignedTo(targetDeoId);
        draftRepository.save(draft);
        return wrapResponse(toDraftResponse(draft));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<SyndicationStatsResponse>> getStats() {
        return wrapResponse(SyndicationStatsResponse.builder()
                .totalDrafts(draftRepository.count())
                .pendingCount(0)
                .assignedCount(draftRepository.countByStatus("ASSIGNED"))
                .inProgressCount(draftRepository.countByStatus("IN_PROGRESS"))
                .convertedCount(draftRepository.countByStatus("CONVERTED"))
                .duplicateCount(0)
                .ignoredCount(0)
                .activeDeoCount(getDeoPool().size())
                .build());
    }

    @GetMapping("/ignore-list")
    public ResponseEntity<ApiResponse<List<IgnoreListEntryResponse>>> getIgnoreList() {
        return wrapResponse(new ArrayList<>(ignoreListStore));
    }

    @PostMapping("/ignore-list")
    public ResponseEntity<ApiResponse<IgnoreListEntryResponse>> addToIgnoreList(
            @RequestBody Map<String, Object> request) {
        IgnoreListEntryResponse entry = IgnoreListEntryResponse.builder()
                .id(ignoreIdSeq.getAndIncrement())
                .emailPattern((String) request.getOrDefault("pattern",
                        request.getOrDefault("emailPattern", "")))
                .patternType((String) request.getOrDefault("type",
                        request.getOrDefault("patternType", "EXACT")))
                .reason((String) request.getOrDefault("reason", ""))
                .addedBy("admin")
                .isActive(true)
                .createdAt(LocalDateTime.now().toString())
                .build();
        ignoreListStore.add(entry);
        return wrapResponse(entry);
    }

    @PostMapping("/ignore-list/bulk")
    public ResponseEntity<ApiResponse<List<IgnoreListEntryResponse>>> bulkAddIgnoreList(
            @RequestBody List<Map<String, Object>> requests) {
        // Stub: builds the entries but never adds them to the ignore list, so a bulk import has no
        // effect on what ingestion auto-closes. Nothing calls this yet.
        List<IgnoreListEntryResponse> entries = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> source = requests.get(i);
            entries.add(IgnoreListEntryResponse.builder()
                    .id(i + 1)
                    .emailPattern((String) source.getOrDefault("emailPattern", ""))
                    .patternType((String) source.getOrDefault("patternType", "EXACT"))
                    .reason((String) source.getOrDefault("reason", ""))
                    .addedBy("admin")
                    .isActive(true)
                    .createdAt(LocalDateTime.now().toString())
                    .build());
        }
        return wrapResponse(entries);
    }

    @GetMapping("/deo")
    public ResponseEntity<ApiResponse<List<DeoResponse>>> getDeos() {
        // Keycloak owns the roster; OFFICER_AVAILABILITY owns leave state and the per-officer threshold.
        // A DEO with no availability row has never been configured in Team Management, so it falls back to
        // being available on the default threshold rather than being hidden from the pool.
        Map<String, com.hrms.cms.entity.OfficerAvailability> availabilityByUser =
                officerAvailabilityRepository.findByRole("DEO").stream()
                        .filter(a -> a.getUserId() != null)
                        .collect(Collectors.toMap(
                                com.hrms.cms.entity.OfficerAvailability::getUserId, a -> a, (a, b) -> a));

        List<DeoResponse> deos = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> kc : keycloakUserService.getDeos()) {
            String userId = (String) kc.get("userId");
            com.hrms.cms.entity.OfficerAvailability availability = availabilityByUser.get(userId);
            // Live count of drafts sitting with this DEO, which is what the assignment screens balance on.
            int currentLoad = draftRepository.findByAssignedToOrderByCreatedAtDesc(
                    (String) kc.get("displayName")).size();
            int position = sortOrder++;

            deos.add(DeoResponse.builder()
                    .id(position)
                    .userId(userId)
                    .displayName((String) kc.get("displayName"))
                    .email((String) kc.getOrDefault("email", ""))
                    .isActive(Boolean.TRUE.equals(kc.get("enabled"))
                            && (availability == null || availability.isActive()))
                    .isOnLeave(availability != null && availability.isOnLeave())
                    .leaveReason(availability != null ? availability.getLeaveReason() : null)
                    .officeCode(availability != null ? availability.getOfficeCode() : null)
                    .maxThreshold(availability != null && availability.getMaxWorkload() > 0
                            ? availability.getMaxWorkload() : 20)
                    .currentLoad(currentLoad)
                    .currentAssignedCount(currentLoad)
                    .sortOrder(position)
                    .build());
        }
        return wrapResponse(deos);
    }

    @GetMapping("/deo/eligible")
    public ResponseEntity<ApiResponse<List<DeoResponse>>> getEligibleDeos() {
        return getDeos();
    }

    @PostMapping("/deo")
    public ResponseEntity<ApiResponse<DeoResponse>> addDeo(@RequestBody Map<String, Object> request) {
        // Stub: echoes the submitted DEO back with placeholder ids and persists nothing. The roster lives
        // in Keycloak, so adding one here never had anywhere to write to.
        return wrapResponse(DeoResponse.builder()
                .id(4)
                .userId((String) request.get("userId"))
                .displayName((String) request.get("displayName"))
                .email((String) request.get("email"))
                .isActive(true)
                .isOnLeave(false)
                .maxThreshold(request.get("maxThreshold") instanceof Number n ? n.intValue() : null)
                .currentAssignedCount(0)
                .sortOrder(4)
                .build());
    }

    @PutMapping("/deo/{userId}/threshold")
    public ResponseEntity<ApiResponse<DeoResponse>> updateThreshold(@PathVariable String userId,
                                                                    @RequestParam int threshold) {
        // Stub: returns the threshold it was handed without storing it. OFFICER_AVAILABILITY owns the
        // real value and is written through Team Management.
        return wrapResponse(placeholderDeo(userId).maxThreshold(threshold).build());
    }

    @PutMapping("/deo/{userId}/status")
    public ResponseEntity<ApiResponse<DeoResponse>> updateDeoStatus(@PathVariable String userId,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) Boolean onLeave) {
        // Stub: reflects the flags back without storing them, as above.
        return wrapResponse(placeholderDeo(userId)
                .isActive(active == null || active)
                .isOnLeave(onLeave != null && onLeave)
                .maxThreshold(20)
                .build());
    }

    private static DeoResponse.DeoResponseBuilder placeholderDeo(String userId) {
        return DeoResponse.builder()
                .id(1)
                .userId(userId)
                .displayName("Updated User")
                .email(userId + "@rbi.org.in")
                .isActive(true)
                .isOnLeave(false)
                .currentAssignedCount(0)
                .sortOrder(1);
    }

    @DeleteMapping("/deo/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeDeo(@PathVariable String userId) {
        return wrapResponse(null);
    }

    @PostMapping("/deo/reset-pointer")
    public ResponseEntity<ApiResponse<PointerResetResponse>> resetPointer() {
        roundRobinPointer.set(0);
        return wrapResponse(PointerResetResponse.builder()
                .message("Round-robin pointer reset")
                .pointer(0)
                .build());
    }

    @DeleteMapping("/ignore-list/{id}")
    public ResponseEntity<ApiResponse<Void>> removeIgnoreEntry(@PathVariable int id) {
        ignoreListStore.removeIf(e -> Integer.valueOf(id).equals(e.getId()));
        return wrapResponse(null);
    }

    @PutMapping("/ignore-list/{id}")
    public ResponseEntity<ApiResponse<IgnoreListEntryResponse>> updateIgnoreEntry(
            @PathVariable int id, @RequestBody Map<String, Object> request) {
        // Stub: echoes the submitted entry back and never touches the store, so an edit does not change
        // what ingestion auto-closes. Nothing calls this yet.
        return wrapResponse(IgnoreListEntryResponse.builder()
                .id(id)
                .emailPattern((String) request.getOrDefault("emailPattern", ""))
                .patternType((String) request.getOrDefault("patternType", "EXACT"))
                .reason((String) request.getOrDefault("reason", ""))
                .addedBy("admin")
                .isActive(true)
                .createdAt(LocalDateTime.now().toString())
                .build());
    }

    // ─── Helper methods ───

    private EmailDraftResponse toDraftResponse(EmailDraft draft) {
        List<EmailDraftAttachmentResponse> attachments = draftAttachmentRepository
                .findByDraftIdOrderByCreatedAtAsc(draft.getDraftId()).stream()
                .map(EmailSyndicationApiController::toAttachmentResponse)
                .collect(Collectors.toList());

        return EmailDraftResponse.builder()
                .id(draft.getId())
                .draftId(draft.getDraftId())
                .displayId("C" + String.format("%03d", draft.getId()))
                .messageId(draft.getMessageId())
                .senderEmail(draft.getSenderEmail())
                .subject(draft.getSubject())
                .body(draft.getBody())
                .complainantName(draft.getComplainantName())
                .complainantPhone(draft.getComplainantPhone())
                .complainantAddress(draft.getComplainantAddress())
                .complainantState(draft.getComplainantState())
                .complainantDistrict(draft.getComplainantDistrict())
                .complainantPincode(draft.getComplainantPincode())
                .cpgramsNumber(draft.getCpgramsNumber())
                .complaintSummary(draft.getComplaintSummary())
                .category(draft.getCategory())
                .modeOfReceipt(draft.getModeOfReceipt())
                .status(draft.getStatus())
                .assignedTo(draft.getAssignedTo())
                .parentComplaintId(draft.getParentComplaintId())
                .isDuplicate(draft.isDuplicate())
                .ocrProcessed(draft.isOcrProcessed())
                .ocrConfidence(draft.getOcrConfidence())
                .entityName(draft.getEntityName())
                .entityType(draft.getEntityType())
                .amountInvolved(draft.getAmountInvolved())
                .receivedAt(draft.getReceivedAt() != null ? draft.getReceivedAt().toString() : "")
                .createdAt(draft.getCreatedAt() != null ? draft.getCreatedAt().toString() : "")
                .processedBy(draft.getProcessedBy())
                .convertedComplaintId(draft.getConvertedComplaintId())
                .attachments(attachments)

                // DEO assessment
                .deoDecision(draft.getDeoDecision())
                .deoRemarks(draft.getDeoRemarks())
                .nonMaintainableReason(draft.getNonMaintainableReason())

                // Reviewer
                .reviewerDecision(draft.getReviewerDecision())
                .reviewerRemarks(draft.getReviewerRemarks())
                .targetOffice(draft.getTargetOffice())

                // Scheme & auto-closure
                .schemeVersion(draft.getSchemeVersion())
                .closureClause(draft.getClosureClause())
                .autoClosureResponsesJson(draft.getAutoClosureResponsesJson())
                .subJudice(draft.isSubJudice())
                .notAComplaintReason(draft.getNotAComplaintReason())
                .notAComplaintOthersReason(draft.getNotAComplaintOthersReason())
                .suggestionDepartment(draft.getSuggestionDepartment())
                .suggestionNature(draft.getSuggestionNature())

                // Language
                .detectedLanguage(draft.getDetectedLanguage())
                .languageName(draft.getLanguageName())
                .isVernacular(draft.isVernacular())
                .translationConfidence(draft.getTranslationConfidence())

                // Eligibility
                .proposedComplaintType(draft.getProposedComplaintType())
                .notComplaintReason(draft.getNotComplaintReason())
                .eligibilityQuestionsJson(draft.getEligibilityQuestionsJson())

                // Entity details
                .entityCategory(draft.getEntityCategory())
                .entityTypeDetail(draft.getEntityTypeDetail())
                .entityBsrCode(draft.getEntityBsrCode())
                .entityPincode(draft.getEntityPincode())
                .entityCountry(draft.getEntityCountry())
                .entityState(draft.getEntityState())
                .entityDistrict(draft.getEntityDistrict())
                .entityCity(draft.getEntityCity())
                .entityBranchName(draft.getEntityBranchName())
                .entityBranchCategory(draft.getEntityBranchCategory())
                .entityAddress(draft.getEntityAddress())
                .entityBranchCenterName(draft.getEntityBranchCenterName())
                .cosmosCode(draft.getCosmosCode())
                .assetSize(draft.getAssetSize())
                .isDepositTaking(draft.getIsDepositTaking())
                .isAssetAbove100Cr(draft.getIsAssetAbove100Cr())
                .isLiquidated(draft.getIsLiquidated())

                // Complainant extended
                .otherEntityName(draft.getOtherEntityName())
                .dateOfRegistrationWithRBI(draft.getDateOfRegistrationWithRBI())
                .complaintCategory(draft.getComplaintCategory())
                .complaintSubCategory1(draft.getComplaintSubCategory1())
                .complaintSubCategory2(draft.getComplaintSubCategory2())
                .dateOfFilingComplaint(draft.getDateOfFilingComplaint())
                .complaintRegDateValid(draft.getComplaintRegDateValid())
                .reminderSentByComplainant(draft.getReminderSentByComplainant())
                .disputedAmountInvolved(draft.getDisputedAmountInvolved())
                .dateOfFilingForFinancial(draft.getDateOfFilingForFinancial())
                .compensationSought(draft.getCompensationSought())
                .loanDisposalAmount(draft.getLoanDisposalAmount())
                .additionalComments(draft.getAdditionalComments())
                .crpcProposedAction(draft.getCrpcProposedAction())
                .vernacularLanguageDetail(draft.getVernacularLanguageDetail())

                // Legal & case
                .legalCaseFiled(draft.getLegalCaseFiled())
                .legalDateOfFiling(draft.getLegalDateOfFiling())
                .preEnquiryReceived(draft.getPreEnquiryReceived())

                // Flags
                .highPriorityComplaint(draft.getHighPriorityComplaint())
                .isRegardingPension(draft.getIsRegardingPension())
                .isAgainstBusinessCorrespondent(draft.getIsAgainstBusinessCorrespondent())
                .isAtmCreditDebitCard(draft.getIsAtmCreditDebitCard())
                .schemeFlag(draft.getSchemeFlag())
                .isFreeMarkedComplaint(draft.getIsFreeMarkedComplaint())

                // Linkage
                .currentComplaintNumber(draft.getCurrentComplaintNumber())
                .receivedReplyWithin30Days(draft.getReceivedReplyWithin30Days())

                // Declaration
                .declarationAccepted(draft.getDeclarationAccepted())

                .ocrExtractedFields(parseOcrFields(draft))
                .build();
    }

    private static EmailDraftAttachmentResponse toAttachmentResponse(EmailDraftAttachment a) {
        return EmailDraftAttachmentResponse.builder()
                .id("ATT-" + a.getId())
                .fileName(a.getFileName())
                .fileType(a.getFileType())
                .fileSize(a.getFileSize())
                .ocrText(a.getOcrText())
                .ocrConfidence(a.getOcrConfidence())
                .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : "")
                .uploadedBy(a.getUploadedBy())
                .build();
    }

    /** Null keeps the key off the wire, which is how the assessment form decides not to show the panel. */
    private Map<String, String> parseOcrFields(EmailDraft draft) {
        String json = draft.getOcrExtractedFieldsJson();
        if (!draft.isOcrProcessed() || json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse OCR fields JSON for draft {}", draft.getDraftId());
            return null;
        }
    }

    private boolean isCrpcOnlyInCcBcc(String to, String cc, String bcc) {
        String crpcPattern = "crpc";
        boolean inTo = to != null && to.toLowerCase().contains(crpcPattern);
        boolean inCc = cc != null && cc.toLowerCase().contains(crpcPattern);
        boolean inBcc = bcc != null && bcc.toLowerCase().contains(crpcPattern);
        return !inTo && (inCc || inBcc);
    }

    /**
     * Every success from this controller carried the message "OK" back when the envelope was assembled
     * by hand here, and the CRPC screens were built against that, so it is preserved rather than
     * replaced with something per-endpoint.
     */
    private static <T> ResponseEntity<ApiResponse<T>> wrapResponse(T data) {
        return ResponseEntity.ok(ApiResponse.success(data, "OK"));
    }

    private static EmailAutoCloseResponse autoClosed(String senderEmail, String subject, String reason) {
        return EmailAutoCloseResponse.builder()
                .status("NON_COMPLAINT")
                .reason(reason)
                .senderEmail(senderEmail)
                .subject(subject)
                .autoClosed(true)
                .closedAt(LocalDateTime.now().toString())
                .build();
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

    private Boolean parseBoolean(String value) {
        if (value == null || value.isEmpty()) return null;
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }
}
