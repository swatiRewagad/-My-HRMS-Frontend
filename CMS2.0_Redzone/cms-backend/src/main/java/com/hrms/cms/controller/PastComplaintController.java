package com.hrms.cms.controller;

import com.hrms.cms.service.PastComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/past-complaints")
@RequiredArgsConstructor
public class PastComplaintController {

    private final PastComplaintService pastComplaintService;

    @GetMapping("/by-complainant")
    public ResponseEntity<Map<String, Object>> getByComplainant(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String excludeId) {

        List<Map<String, Object>> results = pastComplaintService.findPastComplaints(email, phone, excludeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", results);
        response.put("count", results.size());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * The complainant's prior complaints, resolved from the complaint being worked.
     *
     * <p>The by-complainant form above requires the caller to KNOW the complainant's email, and the
     * complaint-detail response masks it, so a staff screen cannot use that form. This one takes the
     * complaint number the screen is already showing and does the identity lookup server-side, which
     * keeps the real address out of the browser.
     */
    @GetMapping("/for-complaint/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getForComplaint(@PathVariable String complaintNumber) {
        List<Map<String, Object>> results =
                pastComplaintService.findPastComplaintsForComplaint(complaintNumber);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", results);
        response.put("count", results.size());
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/detail/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getComplaintDetail(@PathVariable String complaintNumber) {
        Map<String, Object> detail = pastComplaintService.getComplaintDetail(complaintNumber);

        if (detail == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", detail);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/similar")
    public ResponseEntity<Map<String, Object>> findSimilar(@RequestBody Map<String, String> request) {
        String subject = request.getOrDefault("subject", "");
        String description = request.getOrDefault("description", "");
        String category = request.getOrDefault("category", "");
        String excludeId = request.getOrDefault("excludeId", "");

        List<Map<String, Object>> results = pastComplaintService.findSimilarCases(subject, description, category, excludeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", results);
        response.put("count", results.size());
        response.put("matchMethod", (results.isEmpty()) ? "none" : "groq-ai");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
