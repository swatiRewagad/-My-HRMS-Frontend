package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.copilot.MaintainabilityCopilotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final MaintainabilityCopilotService copilotService;
    private final ComplaintService complaintService;

    @GetMapping("/maintainability/{complaintId}")
    public ResponseEntity<MaintainabilityCopilotService.CopilotResponse> getMaintainabilitySuggestion(
            @PathVariable String complaintId) {
        Long id;
        try {
            id = Long.parseLong(complaintId);
        } catch (NumberFormatException e) {
            Complaint c = complaintService.getByComplaintNumber(complaintId);
            id = c.getId();
        }
        MaintainabilityCopilotService.CopilotResponse response = copilotService.generateSuggestion(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/maintainability/{complaintId}/decision")
    public ResponseEntity<Map<String, Object>> recordDecision(
            @PathVariable Long complaintId,
            @RequestBody Map<String, String> request) {
        String determination = request.get("determination");
        String officer = request.get("officer");
        String rationale = request.get("rationale");

        Complaint complaint = complaintService.getComplaint(complaintId);
        complaint.setMaintainabilityDetermination(determination);
        complaint.setMaintainabilityDeterminedBy(officer);
        complaint.setMaintainabilityDeterminedAt(LocalDateTime.now());

        complaintService.updateMaintainability(complaint);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("complaintId", complaintId);
        response.put("determination", determination);
        response.put("determinedBy", officer);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }
}
