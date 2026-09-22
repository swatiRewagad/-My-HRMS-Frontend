package com.hrms.cms.controller;

import com.hrms.cms.entity.ComplaintFeedback;
import com.hrms.cms.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    // ═══════════════════════════════════════════════════════════════════
    // POST /api/v1/feedback — Submit feedback (public, citizen-facing)
    // ═══════════════════════════════════════════════════════════════════

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitFeedback(@RequestBody Map<String, Object> request) {
        try {
            ComplaintFeedback saved = feedbackService.submitFeedback(request);
            Map<String, Object> data = buildFeedbackMap(saved);
            return ResponseEntity.status(HttpStatus.CREATED).body(buildResponse(true, "Feedback submitted successfully.", data));
        } catch (IllegalStateException e) {
            // 409 — duplicate feedback
            return ResponseEntity.status(HttpStatus.CONFLICT).body(buildResponse(false, e.getMessage(), null));
        } catch (IllegalArgumentException e) {
            // 400 — validation error
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildResponse(false, e.getMessage(), null));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /api/v1/feedback/{complaintNumber} — Get feedback for a complaint (staff)
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getFeedbackForComplaint(@PathVariable String complaintNumber) {
        Optional<ComplaintFeedback> opt = feedbackService.getFeedbackForComplaint(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildResponse(false, "No feedback found for complaint: " + complaintNumber, null));
        }
        return ResponseEntity.ok(buildResponse(true, "Feedback retrieved.", buildFeedbackMap(opt.get())));
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /api/v1/feedback/office/{officeCode} — List feedback for an office (staff)
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/office/{officeCode}")
    public ResponseEntity<Map<String, Object>> getFeedbackByOffice(@PathVariable String officeCode) {
        List<ComplaintFeedback> feedbackList = feedbackService.getFeedbackByOffice(officeCode);
        List<Map<String, Object>> data = feedbackList.stream().map(this::buildFeedbackMap).toList();
        return ResponseEntity.ok(buildResponse(true, "Feedback list retrieved.", data));
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /api/v1/feedback/all — All feedback (CEPD admin only)
    // ═══════════════════════════════════════════════════════════════════

    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllFeedback() {
        List<ComplaintFeedback> feedbackList = feedbackService.getAllFeedback();
        List<Map<String, Object>> data = feedbackList.stream().map(this::buildFeedbackMap).toList();
        return ResponseEntity.ok(buildResponse(true, "All feedback retrieved.", data));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════════════

    private Map<String, Object> buildFeedbackMap(ComplaintFeedback fb) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", fb.getId());
        map.put("complaintNumber", fb.getComplaintNumber());
        map.put("overallRating", fb.getOverallRating());
        map.put("easeOfFiling", fb.getEaseOfFiling());
        map.put("timelinessRating", fb.getTimelinessRating());
        map.put("communicationRating", fb.getCommunicationRating());
        map.put("satisfactionRating", fb.getSatisfactionRating());
        map.put("grievanceRedressTime", fb.getGrievanceRedressTime());
        map.put("sourceOfInformation", fb.getSourceOfInformation());
        map.put("sourceOtherText", fb.getSourceOtherText());
        map.put("cmsPortalAwareness", fb.getCmsPortalAwareness());
        map.put("feedbackText", fb.getFeedbackText());
        map.put("suggestions", fb.getSuggestions());
        map.put("complainantPhone", fb.getComplainantPhone());
        map.put("officeCode", fb.getOfficeCode());
        map.put("submittedAt", fb.getSubmittedAt() != null ? fb.getSubmittedAt().toString() : null);
        return map;
    }

    private Map<String, Object> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}
