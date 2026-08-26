package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.InterOfficeTransfer;
import com.hrms.cms.entity.OfficeThresholdConfig;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.service.InterOfficeTransferService;
import com.hrms.cms.service.OfficeRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/crpc/head")
@PreAuthorize("hasAnyRole('CRPC_HEAD', 'ADMIN')")
@RequiredArgsConstructor
public class CrpcHeadController {

    private final InterOfficeTransferService transferService;
    private final OfficeRoutingService officeRoutingService;
    private final ComplaintRepository complaintRepository;

    @GetMapping("/transfers/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingTransfers() {
        List<Map<String, Object>> enriched = transferService.getPendingTransfers().stream()
                .map(this::enrichTransfer)
                .collect(Collectors.toList());
        return ResponseEntity.ok(enriched);
    }

    // Powers the main dashboard grid — includes resolved (approved/rejected) transfers too,
    // so a complaint doesn't just vanish from the list once it's been acted on.
    @GetMapping("/transfers/all")
    public ResponseEntity<List<Map<String, Object>>> getAllTransfers() {
        List<Map<String, Object>> enriched = transferService.getAllTransfers().stream()
                .map(this::enrichTransfer)
                .collect(Collectors.toList());
        return ResponseEntity.ok(enriched);
    }

    private Map<String, Object> enrichTransfer(InterOfficeTransfer t) {
        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(t.getComplaintNumber());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        // complaintId doubles as the transfer's own row id (used for the approve/reject URL);
        // complaintNumber is the human-readable complaint number shown in the UI.
        m.put("complaintId", String.valueOf(t.getId()));
        m.put("complaintNumber", t.getComplaintNumber());
        m.put("from", t.getRequestedBy());
        m.put("pending", t.getRequestedAt() != null ? Duration.between(t.getRequestedAt(), LocalDateTime.now()).toDays() : 0);
        m.put("fromOffice", t.getFromOffice());
        m.put("targetOffice", t.getToOffice());
        m.put("status", t.getStatus());
        m.put("creationDate", t.getRequestedAt() != null ? t.getRequestedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        m.put("language", "English");
        // The reason the requester gave when forwarding — not the complaint's own subject.
        m.put("comment", t.getReason());

        Complaint c = complaintOpt.orElse(null);
        // The complaint's own current workflow status (e.g. "assigned" once with a DO),
        // separate from the transfer's own PENDING/APPROVED/REJECTED resolution status.
        m.put("complaintStatus", c != null ? c.getStatus() : "");
        m.put("assignedOfficer", c != null ? c.getAssignedOfficer() : "");
        m.put("entityName", c != null ? c.getEntityName() : "");
        m.put("proposedCategory", c != null && c.getCategoryName() != null ? c.getCategoryName() : "General");
        m.put("territory", c != null && c.getComplainantState() != null ? c.getComplainantState() : "");
        m.put("subject", c != null ? c.getSubject() : t.getReason());
        m.put("complainantName", c != null ? c.getComplainantName() : "");
        m.put("complainantEmail", c != null ? c.getComplainantEmail() : "");
        m.put("complainantPhone", c != null ? c.getComplainantPhone() : "");
        m.put("description", c != null ? c.getDescription() : t.getReason());
        m.put("timeline", List.of());
        return m;
    }

    @GetMapping("/transfers/pending/count")
    public ResponseEntity<Map<String, Object>> getPendingCount() {
        return ResponseEntity.ok(Map.of("count", transferService.getPendingCount()));
    }

    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String overrideToOffice) {
        Map<String, String> b = body != null ? body : Map.of();
        String approvedBy = b.getOrDefault("approvedBy", "");
        String override = overrideToOffice != null ? overrideToOffice : b.get("overrideToOffice");
        InterOfficeTransfer t = transferService.approveTransfer(id, approvedBy, override);
        return ResponseEntity.ok(withAssignedOfficer(t));
    }

    @PostMapping("/transfers/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String comment) {
        Map<String, String> b = body != null ? body : Map.of();
        String rejectedBy = b.getOrDefault("approvedBy", "");
        String rejectionComment = comment != null ? comment : b.get("rejectionComment");
        if (rejectionComment == null || rejectionComment.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        InterOfficeTransfer t = transferService.rejectTransfer(id, rejectedBy, rejectionComment);
        return ResponseEntity.ok(withAssignedOfficer(t));
    }

    private Map<String, Object> withAssignedOfficer(InterOfficeTransfer t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("complaintNumber", t.getComplaintNumber());
        m.put("status", t.getStatus());
        m.put("toOffice", t.getToOffice());
        m.put("rejectionComment", t.getRejectionComment());
        complaintRepository.findByComplaintNumber(t.getComplaintNumber())
                .ifPresent(c -> m.put("assignedOfficer", c.getAssignedOfficer()));
        return m;
    }

    @PostMapping("/transfers/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InterOfficeTransfer> requestTransfer(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(transferService.requestTransfer(
                request.get("complaintNumber"),
                request.get("fromOffice"),
                request.get("toOffice"),
                request.get("transferType"),
                request.get("reason"),
                request.get("requestedBy")
        ));
    }

    @GetMapping("/transfers/history/{complaintNumber}")
    public ResponseEntity<List<InterOfficeTransfer>> getTransferHistory(@PathVariable String complaintNumber) {
        return ResponseEntity.ok(transferService.getTransferHistory(complaintNumber));
    }

    // Office threshold management
    @GetMapping("/office-thresholds")
    public ResponseEntity<List<OfficeThresholdConfig>> getOfficeThresholds() {
        return ResponseEntity.ok(officeRoutingService.getAllOfficeConfigs());
    }

    @PutMapping("/office-thresholds/{officeId}")
    public ResponseEntity<Map<String, Object>> updateThreshold(
            @PathVariable String officeId,
            @RequestParam int threshold,
            @RequestParam(required = false) String updatedBy) {
        officeRoutingService.updateThreshold(officeId, threshold, updatedBy != null ? updatedBy : "unknown");
        return ResponseEntity.ok(Map.of("status", "updated", "officeId", officeId, "newThreshold", threshold));
    }

    @PostMapping("/office-thresholds/reset")
    public ResponseEntity<Map<String, Object>> resetCounters(@RequestParam(defaultValue = "RBIO") String department) {
        officeRoutingService.resetAllCounters(department);
        return ResponseEntity.ok(Map.of("status", "reset", "department", department));
    }

    // Bulk reassignment
    @PostMapping("/bulk-reassign")
    public ResponseEntity<Map<String, Object>> bulkReassign(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> complaintIds = (List<String>) request.get("complaintIds");
        String targetUser = (String) request.get("targetUser");
        int count = complaintIds != null ? complaintIds.size() : 0;
        return ResponseEntity.ok(Map.of("status", "reassigned", "count", count, "targetUser", targetUser));
    }

    // Reopen closed complaint
    @PostMapping("/reopen/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> reopenComplaint(
            @PathVariable String complaintNumber,
            @RequestParam String reason,
            @RequestParam(required = false) String reopenedBy) {
        return ResponseEntity.ok(Map.of("status", "reopened", "complaintNumber", complaintNumber, "by", reopenedBy != null ? reopenedBy : "unknown"));
    }
}
