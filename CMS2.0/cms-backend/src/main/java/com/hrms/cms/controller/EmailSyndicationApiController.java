package com.hrms.cms.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.entity.EmailDraftAttachment;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.EmailDraftAttachmentRepository;
import com.hrms.cms.repository.EmailDraftRepository;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.EmailSimulationService;
import com.hrms.cms.service.KeycloakUserService;
import com.hrms.cms.service.LanguageTranslationService;
import com.hrms.cms.service.OcrExtractionService;
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
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/email-syndication")
@RequiredArgsConstructor
public class EmailSyndicationApiController {

    private final EmailSimulationService emailService;
    private final OcrExtractionService ocrService;
    private final LanguageTranslationService translationService;
    private final KeycloakUserService keycloakUserService;
    private final EmailDraftRepository draftRepository;
    private final EmailDraftAttachmentRepository draftAttachmentRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintRoutingService routingService;
    private final ComplaintService complaintService;
    private final ObjectMapper objectMapper;

    @Value("${cms.attachments.root-path:C:/cms-attachments}")
    private String attachmentsRootPath;

    private int roundRobinPointer = 0;

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
        String assignee = (String) pool.get(roundRobinPointer % pool.size()).get("userId");
        roundRobinPointer = (roundRobinPointer + 1) % pool.size();
        return assignee;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingestEmail(@RequestBody Map<String, Object> request) {
        String senderEmail = (String) request.getOrDefault("senderEmail", "");
        String subject = (String) request.getOrDefault("subject", "");
        String body = (String) request.getOrDefault("body", "");
        String messageId = (String) request.getOrDefault("messageId", UUID.randomUUID().toString());

        return processIngest(senderEmail, subject, body, messageId, null);
    }

    @PostMapping(value = "/ingest-with-attachment", consumes = "multipart/form-data")
    public Map<String, Object> ingestEmailWithAttachment(
            @RequestParam("senderEmail") String senderEmail,
            @RequestParam("subject") String subject,
            @RequestParam(value = "body", required = false, defaultValue = "") String body,
            @RequestParam(value = "messageId", required = false, defaultValue = "") String messageId,
            @RequestParam("attachment") MultipartFile attachment) {

        return processIngest(senderEmail, subject, body, messageId, attachment);
    }

    private Map<String, Object> processIngest(String senderEmail, String subject, String body,
                                               String messageId, MultipartFile attachment) {
        IncomingEmailRequest emailReq = new IncomingEmailRequest();
        emailReq.setFromEmail(senderEmail);
        emailReq.setFromName(extractName(senderEmail));
        emailReq.setSubject(subject);
        emailReq.setBody(body);

        Map<String, Object> result = emailService.receiveEmail(emailReq);

        String complaintNumber = (String) result.get("complaintNumber");
        String threadId = (String) result.get("threadId");
        String assignedTo = assignToNextDeo();

        // OCR processing if attachment is present
        boolean ocrProcessed = false;
        int ocrConfidence = 0;
        Map<String, String> ocrExtracted = Collections.emptyMap();
        String complainantName = extractName(senderEmail);
        String complainantPhone = "";
        String category = "General";
        String complaintSummary = subject;

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

        // Persist draft to database
        String ocrJson = "";
        if (!ocrExtracted.isEmpty()) {
            try {
                ocrJson = objectMapper.writeValueAsString(ocrExtracted);
            } catch (Exception e) {
                ocrJson = "";
            }
        }

        long nextSeq = draftRepository.count() + 1;
        String generatedDraftId = "DRF-" + String.format("%06d", nextSeq);

        EmailDraft draft = EmailDraft.builder()
                .draftId(generatedDraftId)
                .threadId(threadId)
                .messageId(messageId == null || messageId.isEmpty() ? UUID.randomUUID().toString() : messageId)
                .senderEmail(senderEmail)
                .subject(subject)
                .body(isVernacular ? translatedBody : body)
                .complainantName(complainantName)
                .complainantPhone(complainantPhone)
                .complainantAddress(ocrExtracted.getOrDefault("complainantAddress", ""))
                .complainantState(ocrExtracted.getOrDefault("complainantState", ""))
                .complainantDistrict(ocrExtracted.getOrDefault("complainantDistrict", ""))
                .complainantPincode(ocrExtracted.getOrDefault("complainantPincode", ""))
                .cpgramsNumber(ocrExtracted.getOrDefault("cpgramsNumber", ""))
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
                .entityName(ocrExtracted.getOrDefault("entityName", ""))
                .entityType(ocrExtracted.getOrDefault("entityType", ""))
                .amountInvolved(parseAmount(ocrExtracted.getOrDefault("amountInvolved", "")))
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

        EmailDraft saved = draftRepository.save(draft);
        log.info("Draft saved to DB: id={}, draftId={}, assignedTo={}", saved.getId(), saved.getDraftId(), saved.getAssignedTo());

        // Save attachment to database and disk (after draft so we use the correct draftId)
        if (attachment != null && !attachment.isEmpty()) {
            String storagePath = "";
            try {
                Path draftDir = Paths.get(attachmentsRootPath, "email-drafts", saved.getDraftId());
                Files.createDirectories(draftDir);
                Path targetFile = draftDir.resolve(attachment.getOriginalFilename());
                attachment.transferTo(targetFile.toFile());
                storagePath = targetFile.toString();
                log.info("Attachment stored at: {}", storagePath);
            } catch (IOException e) {
                log.error("Failed to store attachment file to disk: {}", e.getMessage());
            }

            EmailDraftAttachment att = EmailDraftAttachment.builder()
                    .draftId(saved.getDraftId())
                    .fileName(attachment.getOriginalFilename())
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
            long nextSeq = draftRepository.count() + 1;
            String draftId = "DRF-" + String.format("%06d", nextSeq);

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
                    .receivedAt(java.time.LocalDateTime.now())
                    .build();

            EmailDraft saved = draftRepository.save(draft);
            log.info("Physical letter draft saved: draftId={}", saved.getDraftId());

            // Store attachment if present
            if (attachment != null && !attachment.isEmpty()) {
                String storagePath = "";
                try {
                    Path draftDir = Paths.get(attachmentsRootPath, "email-drafts", saved.getDraftId());
                    Files.createDirectories(draftDir);
                    Path targetFile = draftDir.resolve(attachment.getOriginalFilename());
                    attachment.transferTo(targetFile.toFile());
                    storagePath = targetFile.toString();
                    log.info("Physical letter attachment stored at: {}", storagePath);
                } catch (IOException e) {
                    log.error("Failed to store physical letter attachment: {}", e.getMessage());
                }

                EmailDraftAttachment att = EmailDraftAttachment.builder()
                        .draftId(saved.getDraftId())
                        .fileName(attachment.getOriginalFilename())
                        .fileType(attachment.getContentType())
                        .fileSize(attachment.getSize())
                        .storagePath(storagePath)
                        .ocrText("")
                        .ocrConfidence(0)
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
        stats.put("pendingCount", 0);
        stats.put("assignedCount", assigned);
        stats.put("inProgressCount", inProgress);
        stats.put("convertedCount", converted);
        stats.put("duplicateCount", 0);
        stats.put("ignoredCount", 0);
        stats.put("activeDeoCount", getDeoPool().size());

        return wrapResponse(stats);
    }

    @GetMapping("/ignore-list")
    public Map<String, Object> getIgnoreList() {
        return wrapResponse(List.of());
    }

    @PostMapping("/ignore-list")
    public Map<String, Object> addToIgnoreList(@RequestBody Map<String, Object> request) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", 1);
        entry.put("emailPattern", request.getOrDefault("emailPattern", ""));
        entry.put("patternType", request.getOrDefault("patternType", "EXACT"));
        entry.put("reason", request.getOrDefault("reason", ""));
        entry.put("addedBy", "admin");
        entry.put("isActive", true);
        entry.put("createdAt", LocalDateTime.now().toString());
        return wrapResponse(entry);
    }

    @PostMapping("/ignore-list/bulk")
    public Map<String, Object> bulkAddIgnoreList(@RequestBody List<Map<String, Object>> requests) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", i + 1);
            entry.put("emailPattern", requests.get(i).getOrDefault("emailPattern", ""));
            entry.put("patternType", requests.get(i).getOrDefault("patternType", "EXACT"));
            entry.put("reason", requests.get(i).getOrDefault("reason", ""));
            entry.put("addedBy", "admin");
            entry.put("isActive", true);
            entry.put("createdAt", LocalDateTime.now().toString());
            entries.add(entry);
        }
        return wrapResponse(entries);
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
        roundRobinPointer = 0;
        return wrapResponse(Map.of("message", "Round-robin pointer reset", "pointer", 0));
    }

    @DeleteMapping("/ignore-list/{id}")
    public Map<String, Object> removeIgnoreEntry(@PathVariable int id) {
        return wrapResponse(null);
    }

    @PutMapping("/ignore-list/{id}")
    public Map<String, Object> updateIgnoreEntry(@PathVariable int id, @RequestBody Map<String, Object> request) {
        Map<String, Object> entry = new LinkedHashMap<>(request);
        entry.put("id", id);
        entry.put("addedBy", "admin");
        entry.put("isActive", true);
        entry.put("createdAt", LocalDateTime.now().toString());
        return wrapResponse(entry);
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
        response.put("suggestedRelated", List.of());

        // DEO assessment
        response.put("deoDecision", draft.getDeoDecision());
        response.put("deoRemarks", draft.getDeoRemarks());
        response.put("nonMaintainableReason", draft.getNonMaintainableReason());

        // Reviewer
        response.put("reviewerDecision", draft.getReviewerDecision());
        response.put("reviewerRemarks", draft.getReviewerRemarks());
        response.put("targetOffice", draft.getTargetOffice());

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

    private Map<String, Object> wrapResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    private String extractName(String email) {
        if (email == null || email.isEmpty()) return "Unknown";
        String local = email.split("@")[0];
        String[] parts = local.split("[._]");
        return Arrays.stream(parts)
                .map(p -> p.substring(0, 1).toUpperCase() + p.substring(1))
                .collect(Collectors.joining(" "));
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
