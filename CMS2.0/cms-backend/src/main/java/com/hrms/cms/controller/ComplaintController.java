package com.hrms.cms.controller;

import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.dto.UpdateComplaintRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.RbioComplaintSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final RbioComplaintSummaryService rbioComplaintSummaryService;

    @GetMapping
    public List<Complaint> getAll(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) return complaintService.searchComplaints(search);
        if (status != null) return complaintService.getByStatus(status);
        return complaintService.getAllComplaints();
    }

    @GetMapping("/paged")
    public Page<Complaint> getAllPaged(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);
        if (search != null && !search.isBlank()) return complaintService.searchComplaintsPaged(search, pageable);
        if (status != null) return complaintService.getByStatusPaged(status, pageable);
        return complaintService.getAllComplaintsPaged(pageable);
    }

    @GetMapping("/{id}")
    public Complaint getById(@PathVariable Long id) {
        return complaintService.getComplaint(id);
    }

    @GetMapping("/track/{complaintNumber}")
    public Complaint track(@PathVariable String complaintNumber) {
        return complaintService.getByComplaintNumber(complaintNumber);
    }

    @PostMapping
    public Complaint file(@Valid @RequestBody FileComplaintRequest request) {
        return complaintService.fileComplaint(request);
    }

    @PutMapping("/{id}")
    public Complaint update(@PathVariable Long id, @RequestBody UpdateComplaintRequest request) {
        return complaintService.updateComplaint(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        complaintService.deleteComplaint(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/timeline")
    public List<ComplaintTimeline> getTimeline(@PathVariable Long id) {
        return complaintService.getTimeline(id);
    }

    @GetMapping("/stream")
    public ResponseEntity<Page<Complaint>> streamAllComplaints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Page<Complaint> complaintPage = complaintService.getStreamedComplaints(page, size);
        return ResponseEntity.ok(complaintPage);
    }

    /**
     * Consolidated RBIO officer view of one complaint, sectioned the way the officer form is laid out.
     * Guarded because it surfaces account and card numbers that the other routes on this controller
     * do not expose.
     */
    @GetMapping("/rbio/{id}/summary")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_DEPUTY_OMBUDSMAN", "CRPC_HEAD", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> getRbioSummary(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(envelope(true, "OK", rbioComplaintSummaryService.getSummary(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(envelope(false, e.getMessage(), null));
        }
    }

    /**
     * Accepts the same nested shape {@link #getRbioSummary} returns, so the officer form can
     * round-trip one object. An omitted key is left untouched; a key present with a null value is
     * cleared.
     */
    @PutMapping("/rbio/{id}/summary")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> updateRbioSummary(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        try {
            Map<String, Object> updated = rbioComplaintSummaryService.updateSummary(id, payload, userId);
            return ResponseEntity.ok(envelope(true, "Summary updated", updated));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Complaint not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(envelope(false, e.getMessage(), null));
        }
    }

    private static Map<String, Object> envelope(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}
