package com.hrms.cms.controller;

import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.service.EmailSimulationService;
import com.hrms.cms.service.OcrExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    private static final List<Map<String, Object>> deoPool = List.of(
            Map.of("userId", "deo_001", "displayName", "Amit Verma", "maxThreshold", 20),
            Map.of("userId", "deo_002", "displayName", "Sneha Patil", "maxThreshold", 15),
            Map.of("userId", "deo_003", "displayName", "Ramesh Iyer", "maxThreshold", 20)
    );
    private int roundRobinPointer = 0;

    private String assignToNextDeo() {
        String assignee = (String) deoPool.get(roundRobinPointer % deoPool.size()).get("displayName");
        roundRobinPointer = (roundRobinPointer + 1) % deoPool.size();
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
        List<Map<String, Object>> attachments = new ArrayList<>();
        String complainantName = extractName(senderEmail);
        String complainantPhone = "";
        String category = "General";
        String complaintSummary = subject;

        if (attachment != null && !attachment.isEmpty()) {
            String contentType = attachment.getContentType();
            Set<String> allowedTypes = Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff");

            Map<String, Object> attachmentInfo = new LinkedHashMap<>();
            attachmentInfo.put("id", 1);
            attachmentInfo.put("fileName", attachment.getOriginalFilename());
            attachmentInfo.put("fileType", contentType);
            attachmentInfo.put("fileSize", attachment.getSize());
            attachmentInfo.put("ocrText", "");
            attachmentInfo.put("ocrConfidence", 0);
            attachmentInfo.put("createdAt", LocalDateTime.now().toString());

            if (contentType != null && allowedTypes.contains(contentType)) {
                try {
                    byte[] fileBytes = attachment.getBytes();
                    ocrExtracted = ocrService.extractFromImage(fileBytes, contentType);

                    if (!ocrExtracted.isEmpty()) {
                        ocrProcessed = true;
                        ocrConfidence = 85;
                        attachmentInfo.put("ocrText", ocrExtracted.toString());
                        attachmentInfo.put("ocrConfidence", ocrConfidence);

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

            attachments.add(attachmentInfo);
        }

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", 1);
        draft.put("draftId", threadId);
        draft.put("messageId", messageId == null || messageId.isEmpty() ? UUID.randomUUID().toString() : messageId);
        draft.put("senderEmail", senderEmail);
        draft.put("subject", subject);
        draft.put("body", body);
        draft.put("complainantName", complainantName);
        draft.put("complainantPhone", complainantPhone);
        draft.put("cpgramsNumber", ocrExtracted.getOrDefault("cpgramsNumber", ""));
        draft.put("complaintSummary", complaintSummary);
        draft.put("category", category);
        draft.put("modeOfReceipt", "EMAIL");
        draft.put("status", "ASSIGNED");
        draft.put("assignedTo", assignedTo);
        draft.put("parentComplaintId", complaintNumber);
        draft.put("isDuplicate", false);
        draft.put("ocrProcessed", ocrProcessed);
        draft.put("ocrConfidence", ocrConfidence);
        draft.put("receivedAt", LocalDateTime.now().toString());
        draft.put("createdAt", LocalDateTime.now().toString());
        draft.put("processedBy", "");
        draft.put("convertedComplaintId", "");
        draft.put("attachments", attachments);
        draft.put("suggestedRelated", List.of());

        // Include OCR extracted fields so frontend can auto-fill
        if (!ocrExtracted.isEmpty()) {
            draft.put("ocrExtractedFields", ocrExtracted);
        }

        return wrapResponse(draft);
    }

    @GetMapping("/queue")
    public Map<String, Object> getQueue(@RequestParam(required = false) String status) {
        List<Map<String, Object>> threads = emailService.getAllThreads();
        List<Map<String, Object>> drafts = threads.stream().map(t -> {
            Map<String, Object> draft = new LinkedHashMap<>();
            int index = threads.indexOf(t);
            draft.put("id", index + 1);
            draft.put("draftId", t.get("threadId"));
            draft.put("messageId", UUID.randomUUID().toString());
            draft.put("senderEmail", t.get("fromEmail"));
            draft.put("subject", t.get("subject"));
            draft.put("body", "");
            draft.put("complainantName", extractName((String) t.get("fromEmail")));
            draft.put("complainantPhone", "");
            draft.put("cpgramsNumber", "");
            draft.put("complaintSummary", t.get("subject"));
            draft.put("category", "General");
            draft.put("modeOfReceipt", "EMAIL");
            String threadStatus = (String) t.getOrDefault("status", "ASSIGNED");
            draft.put("status", "COMPLETED".equals(threadStatus) ? "CONVERTED" : "ASSIGNED");
            String assignee = (String) deoPool.get(index % deoPool.size()).get("displayName");
            draft.put("assignedTo", assignee);
            draft.put("parentComplaintId", t.get("complaintNumber"));
            draft.put("isDuplicate", false);
            draft.put("ocrProcessed", false);
            draft.put("ocrConfidence", 0);
            draft.put("receivedAt", t.get("sentAt") != null ? t.get("sentAt").toString() : LocalDateTime.now().toString());
            draft.put("createdAt", t.get("sentAt") != null ? t.get("sentAt").toString() : LocalDateTime.now().toString());
            draft.put("processedBy", "");
            draft.put("convertedComplaintId", "");
            draft.put("attachments", List.of());
            draft.put("suggestedRelated", List.of());
            return draft;
        }).collect(Collectors.toList());

        if (status != null && !status.isEmpty()) {
            drafts = drafts.stream()
                    .filter(d -> status.equalsIgnoreCase((String) d.get("status")))
                    .collect(Collectors.toList());
        }

        return wrapResponse(drafts);
    }

    @GetMapping("/drafts/{draftId}")
    public Map<String, Object> getDraft(@PathVariable String draftId) {
        Map<String, Object> thread = emailService.getThread(draftId);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", 1);
        draft.put("draftId", draftId);
        draft.put("messageId", UUID.randomUUID().toString());
        draft.put("senderEmail", thread.get("fromEmail"));
        draft.put("subject", thread.get("subject"));
        draft.put("body", "");
        draft.put("complainantName", extractName((String) thread.get("fromEmail")));
        draft.put("complainantPhone", "");
        draft.put("cpgramsNumber", "");
        draft.put("complaintSummary", thread.get("subject"));
        draft.put("category", "General");
        draft.put("modeOfReceipt", "EMAIL");
        String threadStatus = (String) thread.getOrDefault("status", "ASSIGNED");
        draft.put("status", "COMPLETED".equals(threadStatus) ? "CONVERTED" : "ASSIGNED");
        draft.put("assignedTo", assignToNextDeo());
        draft.put("parentComplaintId", thread.get("complaintNumber"));
        draft.put("isDuplicate", false);
        draft.put("ocrProcessed", false);
        draft.put("ocrConfidence", 0);
        draft.put("receivedAt", LocalDateTime.now().toString());
        draft.put("createdAt", LocalDateTime.now().toString());
        draft.put("processedBy", "");
        draft.put("convertedComplaintId", "");
        draft.put("attachments", List.of());
        draft.put("suggestedRelated", List.of());

        return wrapResponse(draft);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> emailStats = emailService.getStats();
        int total = (int) emailStats.getOrDefault("totalThreads", 0);
        long completed = (long) emailStats.getOrDefault("completed", 0L);
        long awaiting = (long) emailStats.getOrDefault("awaitingForm", 0L);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalDrafts", total);
        stats.put("pendingCount", 0);
        stats.put("assignedCount", awaiting);
        stats.put("inProgressCount", 0);
        stats.put("convertedCount", completed);
        stats.put("duplicateCount", 0);
        stats.put("ignoredCount", 0);
        stats.put("activeDeoCount", deoPool.size());

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
        List<Map<String, Object>> deos = new ArrayList<>();
        deos.add(Map.of(
                "id", 1, "userId", "deo_001", "displayName", "Amit Verma",
                "email", "amit.verma@rbi.org.in", "isActive", true, "isOnLeave", false,
                "maxThreshold", 20, "currentAssignedCount", 5, "sortOrder", 1
        ));
        deos.add(Map.of(
                "id", 2, "userId", "deo_002", "displayName", "Sneha Patil",
                "email", "sneha.patil@rbi.org.in", "isActive", true, "isOnLeave", false,
                "maxThreshold", 15, "currentAssignedCount", 8, "sortOrder", 2
        ));
        deos.add(Map.of(
                "id", 3, "userId", "deo_003", "displayName", "Ramesh Iyer",
                "email", "ramesh.iyer@rbi.org.in", "isActive", true, "isOnLeave", true,
                "maxThreshold", 20, "currentAssignedCount", 0, "sortOrder", 3
        ));
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
        return wrapResponse(null);
    }

    @PutMapping("/drafts/{draftId}")
    public Map<String, Object> updateDraft(@PathVariable String draftId, @RequestBody Map<String, Object> request) {
        Map<String, Object> thread = emailService.getThread(draftId);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", 1);
        draft.put("draftId", draftId);
        draft.put("messageId", UUID.randomUUID().toString());
        draft.put("senderEmail", thread.get("fromEmail"));
        draft.put("subject", request.getOrDefault("subject", thread.get("subject")));
        draft.put("body", request.getOrDefault("body", ""));
        draft.put("complainantName", request.getOrDefault("complainantName", ""));
        draft.put("complainantPhone", request.getOrDefault("complainantPhone", ""));
        draft.put("cpgramsNumber", request.getOrDefault("cpgramsNumber", ""));
        draft.put("complaintSummary", request.getOrDefault("complaintSummary", ""));
        draft.put("category", request.getOrDefault("category", "General"));
        draft.put("modeOfReceipt", "EMAIL");
        draft.put("status", "IN_PROGRESS");
        draft.put("assignedTo", "");
        draft.put("parentComplaintId", thread.get("complaintNumber"));
        draft.put("isDuplicate", false);
        draft.put("ocrProcessed", false);
        draft.put("ocrConfidence", 0);
        draft.put("receivedAt", LocalDateTime.now().toString());
        draft.put("createdAt", LocalDateTime.now().toString());
        draft.put("processedBy", "");
        draft.put("convertedComplaintId", "");
        draft.put("attachments", List.of());
        draft.put("suggestedRelated", List.of());
        return wrapResponse(draft);
    }

    @PostMapping("/drafts/{draftId}/convert")
    public Map<String, Object> convertDraft(@PathVariable String draftId) {
        Map<String, Object> thread = emailService.getThread(draftId);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", 1);
        draft.put("draftId", draftId);
        draft.put("messageId", UUID.randomUUID().toString());
        draft.put("senderEmail", thread.get("fromEmail"));
        draft.put("subject", thread.get("subject"));
        draft.put("body", "");
        draft.put("complainantName", extractName((String) thread.get("fromEmail")));
        draft.put("complainantPhone", "");
        draft.put("cpgramsNumber", "");
        draft.put("complaintSummary", thread.get("subject"));
        draft.put("category", "General");
        draft.put("modeOfReceipt", "EMAIL");
        draft.put("status", "CONVERTED");
        draft.put("assignedTo", "");
        draft.put("parentComplaintId", thread.get("complaintNumber"));
        draft.put("isDuplicate", false);
        draft.put("ocrProcessed", false);
        draft.put("ocrConfidence", 0);
        draft.put("receivedAt", LocalDateTime.now().toString());
        draft.put("createdAt", LocalDateTime.now().toString());
        draft.put("processedBy", "System");
        draft.put("convertedComplaintId", thread.get("complaintNumber"));
        draft.put("attachments", List.of());
        draft.put("suggestedRelated", List.of());
        return wrapResponse(draft);
    }

    @PostMapping("/drafts/{draftId}/reassign")
    public Map<String, Object> reassignDraft(@PathVariable String draftId, @RequestParam String targetDeoId) {
        return wrapResponse(null);
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
}
