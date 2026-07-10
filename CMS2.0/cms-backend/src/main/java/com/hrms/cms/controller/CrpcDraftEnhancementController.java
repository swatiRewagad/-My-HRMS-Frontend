package com.hrms.cms.controller;

import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.repository.EmailDraftRepository;
import com.hrms.cms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crpc/drafts")
@RequiredArgsConstructor
public class CrpcDraftEnhancementController {

    private final EmailDraftRepository draftRepository;
    private final NotificationService notificationService;

    @PostMapping("/{draftId}/vernacular-override")
    @PreAuthorize("hasAnyRole('REVIEWER', 'CRPC_REVIEWER', 'CRPC_HEAD')")
    public ResponseEntity<EmailDraft> overrideVernacularFlag(
            @PathVariable String draftId,
            @RequestBody Map<String, Object> body) {
        EmailDraft draft = draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        Boolean isVernacular = (Boolean) body.get("isVernacular");
        String detectedLanguage = (String) body.get("detectedLanguage");

        if (isVernacular != null) draft.setVernacular(isVernacular);
        if (detectedLanguage != null) draft.setDetectedLanguage(detectedLanguage);

        return ResponseEntity.ok(draftRepository.save(draft));
    }

    @PostMapping("/{draftId}/cpgrams-number")
    @PreAuthorize("hasAnyRole('DEO', 'CRPC_DEO', 'REVIEWER', 'CRPC_REVIEWER')")
    public ResponseEntity<EmailDraft> setCpgramsNumber(
            @PathVariable String draftId,
            @RequestBody Map<String, String> body) {
        EmailDraft draft = draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        String cpgramsNumber = body.get("cpgramsNumber");
        if (cpgramsNumber != null && !cpgramsNumber.isBlank()) {
            draft.setCpgramsNumber(cpgramsNumber);
        }
        return ResponseEntity.ok(draftRepository.save(draft));
    }

    @GetMapping("/vernacular")
    @PreAuthorize("hasAnyRole('REVIEWER', 'CRPC_REVIEWER', 'CRPC_HEAD')")
    public ResponseEntity<List<EmailDraft>> getVernacularDrafts() {
        return ResponseEntity.ok(draftRepository.findByIsVernacularTrueOrderByCreatedAtDesc());
    }

    @PostMapping("/{draftId}/mark-sent-back-highlight")
    @PreAuthorize("hasAnyRole('REVIEWER', 'CRPC_REVIEWER')")
    public ResponseEntity<EmailDraft> markSentBackHighlight(
            @PathVariable String draftId,
            @RequestParam String colour,
            @AuthenticationPrincipal Jwt jwt) {
        EmailDraft draft = draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        // Store highlight color in OcrExtractedFieldsJson as a workaround field
        // In production, add a dedicated column
        String existingJson = draft.getOcrExtractedFieldsJson();
        if (existingJson == null) existingJson = "{}";
        if (!existingJson.contains("\"sentBackHighlight\"")) {
            existingJson = existingJson.substring(0, existingJson.length() - 1)
                    + (existingJson.length() > 2 ? "," : "")
                    + "\"sentBackHighlight\":\"" + colour + "\"}";
            draft.setOcrExtractedFieldsJson(existingJson);
        }
        return ResponseEntity.ok(draftRepository.save(draft));
    }

    @PostMapping("/{draftId}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailDraft> adminReopen(
            @PathVariable String draftId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        EmailDraft draft = draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));

        draft.setStatus("ASSIGNED");
        draft.setDeoDecision(null);
        draft.setReviewerDecision(null);
        draft.setReviewerRemarks(null);
        draft = draftRepository.save(draft);

        if (draft.getAssignedTo() != null) {
            notificationService.send(
                    draft.getAssignedTo(),
                    "ASSIGNMENT",
                    "Draft reopened by Admin",
                    "Draft " + draftId + " has been reopened. Reason: " + body.getOrDefault("reason", "Admin action"),
                    draftId,
                    "DRAFT",
                    "/crpc/draft/" + draftId
            );
        }
        return ResponseEntity.ok(draft);
    }
}
