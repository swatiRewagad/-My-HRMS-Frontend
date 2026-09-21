package com.hrms.cms.controller;

import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.dto.UpdateComplaintRequest;
import com.hrms.cms.dto.complaint.RbioComplaintSummaryResponse;
import com.hrms.cms.dto.complaint.RbioConciliationResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.event.ComplaintEventPublisher;
import com.hrms.cms.security.CallerIdentity;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.RbioComplaintSummaryService;
import com.hrms.cms.service.RbioConciliationService;
import com.hrms.cms.service.RbioHierarchyService;
import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.common.enums.RoleConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final RbioComplaintSummaryService rbioComplaintSummaryService;
    private final RbioConciliationService rbioConciliationService;
    private final RbioHierarchyService rbioHierarchyService;
    private final ComplaintEventPublisher complaintEventPublisher;
    private final CallerIdentity callerIdentity;

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
    @RbioRoleGuard(roles = {RoleConstants.RBIO_DO, RoleConstants.RBIO_REVIEWER,
            RoleConstants.RBIO_DEPUTY_OMBUDSMAN, RoleConstants.RBIO_OMBUDSMAN, RoleConstants.RBIO_ADMIN,
            "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "CRPC_HEAD"})
    public ResponseEntity<ApiResponse<RbioComplaintSummaryResponse>> getRbioSummary(@PathVariable Long id) {
        try {
            RbioComplaintSummaryResponse summary = rbioComplaintSummaryService.getSummary(id);
            // Lets the screen render read-only rather than let the officer fill a form the PUT will reject.
            summary.setCanEdit(rbioHierarchyService.canEdit(
                    summary.getAssignedOfficer(), callerIdentity.username(), callerIdentity.roles()));
            // Opening the complaint is what marks it read, for this caller only. Announced only once the
            // receipt was actually written, and after markRead's own transaction has committed, so the
            // index never runs ahead of the row.
            String reader = callerIdentity.username();
            rbioComplaintSummaryService.markRead(id, reader)
                    .ifPresent(complaintNumber -> complaintEventPublisher.publishComplaintRead(complaintNumber, reader));
            return ResponseEntity.ok(ApiResponse.success(summary, "OK"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Accepts the same nested shape {@link #getRbioSummary} returns, so the officer form can
     * round-trip one object. An omitted key is left untouched; a key present with a null value is
     * cleared.
     */
    @PutMapping("/rbio/{id}/summary")
    @RbioRoleGuard(roles = {RoleConstants.RBIO_DO, RoleConstants.RBIO_REVIEWER,
            RoleConstants.RBIO_DEPUTY_OMBUDSMAN, RoleConstants.RBIO_OMBUDSMAN, RoleConstants.RBIO_ADMIN,
            "RBIO_OFFICER", "RBIO_SUPERVISOR"})
    public ResponseEntity<ApiResponse<RbioComplaintSummaryResponse>> updateRbioSummary(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        try {
            String actor = Objects.requireNonNullElse(callerIdentity.username(), userId);
            RbioComplaintSummaryResponse updated = rbioComplaintSummaryService.updateSummary(
                    id, payload, actor, callerIdentity.roles());
            return ResponseEntity.ok(ApiResponse.success(updated, "Summary updated"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error(e.getReason()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Complaint not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Conciliation tab: the live meeting plus the reschedule trail behind it.
     */
    @GetMapping("/rbio/{id}/conciliation")
    @RbioRoleGuard(roles = {RoleConstants.RBIO_DO, RoleConstants.RBIO_REVIEWER,
            RoleConstants.RBIO_DEPUTY_OMBUDSMAN, RoleConstants.RBIO_OMBUDSMAN, RoleConstants.RBIO_ADMIN,
            "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "CRPC_HEAD"})
    public ResponseEntity<ApiResponse<RbioConciliationResponse>> getRbioConciliation(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success(rbioConciliationService.getConciliation(id), "OK"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Accepts the flat shape {@code data.current} of {@link #getRbioConciliation} returns. Updates the
     * live meeting in place; a {@code meetingStatus} of RESCHEDULED, or a live meeting that has already
     * been completed or cancelled, opens a new meeting instead so the previous one is preserved.
     */
    @PutMapping("/rbio/{id}/conciliation")
    @RbioRoleGuard(roles = {RoleConstants.RBIO_DO, RoleConstants.RBIO_ADMIN})
    public ResponseEntity<ApiResponse<RbioConciliationResponse>> updateRbioConciliation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        try {
            String actor = Objects.requireNonNullElse(callerIdentity.username(), userId);
            RbioConciliationResponse updated = rbioConciliationService.saveMeeting(
                    id, payload, actor, callerIdentity.roles());
            return ResponseEntity.ok(ApiResponse.success(updated, "Conciliation updated"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.error(e.getReason()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Complaint not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        }
    }
}
