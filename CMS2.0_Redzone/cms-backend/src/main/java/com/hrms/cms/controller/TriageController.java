package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.mre.MreVerdict;
import com.hrms.cms.service.triage.CompensationPrecedentService;
import com.hrms.cms.service.triage.IntakeTriageService;
import com.hrms.cms.service.triage.ReResponsivenessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class TriageController {

    private final IntakeTriageService triageService;
    private final CompensationPrecedentService compensationService;
    private final ReResponsivenessService reRadarService;
    private final ComplaintService complaintService;

    @GetMapping("/complaint/{id}")
    public ResponseEntity<Map<String, Object>> getTriageForComplaint(@PathVariable Long id) {
        Complaint complaint = complaintService.getComplaint(id);
        MreVerdict verdict = triageService.evaluateWithoutPersisting(complaint);

        CompensationPrecedentService.CompensationBand band = compensationService.getCompensationBand(complaint);

        return ResponseEntity.ok(Map.of(
                "complaintNumber", complaint.getComplaintNumber(),
                "triageSignal", verdict.getTriageSignal(),
                "verdict", verdict,
                "compensationBand", band
        ));
    }

    @PostMapping("/re-track/{complaintId}")
    public ResponseEntity<ReResponseTracker> trackReForwarding(
            @PathVariable Long complaintId,
            @RequestParam Long regulatedEntityId) {
        Complaint complaint = complaintService.getComplaint(complaintId);
        ReResponseTracker tracker = reRadarService.trackForwarding(complaint, regulatedEntityId);
        return ResponseEntity.ok(tracker);
    }

    @PostMapping("/re-response/{complaintId}")
    public ResponseEntity<Void> recordReResponse(@PathVariable Long complaintId) {
        reRadarService.recordResponse(complaintId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/re-radar/{regulatedEntityId}")
    public ResponseEntity<ReResponsivenessService.ReRadarSummary> getReRadar(@PathVariable Long regulatedEntityId) {
        return ResponseEntity.ok(reRadarService.getRadarForEntity(regulatedEntityId));
    }

    @GetMapping("/re-breaches")
    public ResponseEntity<List<ReResponseTracker>> getBreachedCases() {
        return ResponseEntity.ok(reRadarService.getBreachedCases());
    }
}
