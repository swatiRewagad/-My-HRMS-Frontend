package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.ComplaintDraft;
import com.hrms.cms.repository.ComplaintDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/complaints/drafts")
@RequiredArgsConstructor
public class ComplaintDraftController {

    private final ComplaintDraftRepository draftRepository;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveDraft(@RequestBody Map<String, Object> request) {
        String phone = (String) request.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Phone is required"));
        }

        String entityName = (String) request.getOrDefault("entityName", "");
        Integer currentStep = request.get("currentStep") != null ? ((Number) request.get("currentStep")).intValue() : 1;
        String phase = (String) request.getOrDefault("phase", "eligibility");

        String formDataJson = serializeToJson(request.get("formData"));
        String eligibilityJson = serializeToJson(request.get("eligibilityAnswers"));

        // Upsert: one draft per phone number
        ComplaintDraft draft = draftRepository.findByPhoneOrderByUpdatedAtDesc(phone)
                .stream().findFirst().orElse(null);

        if (draft == null) {
            draft = new ComplaintDraft();
            draft.setDraftId("DRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            draft.setPhone(phone);
        }

        draft.setEntityName(entityName);
        draft.setFormDataJson(formDataJson);
        draft.setEligibilityAnswersJson(eligibilityJson);
        draft.setCurrentStep(currentStep);
        draft.setPhase(phase);

        draftRepository.save(draft);

        Map<String, Object> data = Map.of("draftId", draft.getDraftId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true, "message", "Draft saved", "data", data,
                "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDrafts(@RequestParam String phone) {
        List<ComplaintDraft> drafts = draftRepository.findByPhoneOrderByUpdatedAtDesc(phone);

        List<Map<String, Object>> items = drafts.stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("draftId", d.getDraftId());
            item.put("phone", d.getPhone());
            item.put("entityName", d.getEntityName());
            item.put("currentStep", d.getCurrentStep());
            item.put("phase", d.getPhase());
            item.put("updatedAt", d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : "");
            item.put("formData", deserializeJson(d.getFormDataJson()));
            item.put("eligibilityAnswers", deserializeJson(d.getEligibilityAnswersJson()));
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true, "message", "Drafts retrieved", "data", items,
                "timestamp", LocalDateTime.now().toString()));
    }

    @GetMapping("/{draftId}")
    public ResponseEntity<Map<String, Object>> getDraft(@PathVariable String draftId) {
        Optional<ComplaintDraft> opt = draftRepository.findByDraftId(draftId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false, "message", "Draft not found", "data", Collections.emptyMap()));
        }

        ComplaintDraft d = opt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("draftId", d.getDraftId());
        data.put("phone", d.getPhone());
        data.put("entityName", d.getEntityName());
        data.put("currentStep", d.getCurrentStep());
        data.put("phase", d.getPhase());
        data.put("updatedAt", d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : "");
        data.put("formData", deserializeJson(d.getFormDataJson()));
        data.put("eligibilityAnswers", deserializeJson(d.getEligibilityAnswersJson()));

        return ResponseEntity.ok(Map.of(
                "success", true, "message", "OK", "data", data,
                "timestamp", LocalDateTime.now().toString()));
    }

    @DeleteMapping("/{draftId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteDraft(@PathVariable String draftId) {
        draftRepository.deleteByDraftId(draftId);
        return ResponseEntity.ok(Map.of(
                "success", true, "message", "Draft deleted",
                "timestamp", LocalDateTime.now().toString()));
    }

    private String serializeToJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
