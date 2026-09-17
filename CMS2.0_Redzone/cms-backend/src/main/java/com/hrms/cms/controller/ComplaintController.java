package com.hrms.cms.controller;

import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.dto.UpdateComplaintRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.PiiMaskingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Legacy complaint API.
 *
 * These endpoints serialise the {@link Complaint} entity directly, which used to include the
 * complainant's name, phone, email, address, account number and the authorised-representative
 * block — and {@code /track/{complaintNumber}} is reachable by an unauthenticated citizen. Masking
 * only the newer /api/v1 controller would have left this as a trivial bypass, so PII is masked here
 * too (UST875). Responses are masked maps rather than the entity so the persistence context cannot
 * flush masked values back to the database.
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final PiiMaskingService piiMaskingService;
    private final RequestIdentityResolver requestIdentityResolver;

    @GetMapping
    public List<Map<String, Object>> getAll(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String search,
                                            HttpServletRequest request) {
        List<Complaint> complaints;
        if (search != null && !search.isBlank()) {
            complaints = complaintService.searchComplaints(search);
        } else if (status != null) {
            complaints = complaintService.getByStatus(status);
        } else {
            complaints = complaintService.getAllComplaints();
        }
        return piiMaskingService.maskComplaints(complaints, false);
    }

    @GetMapping("/paged")
    public Page<Map<String, Object>> getAllPaged(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);
        Page<Complaint> complaints;
        if (search != null && !search.isBlank()) {
            complaints = complaintService.searchComplaintsPaged(search, pageable);
        } else if (status != null) {
            complaints = complaintService.getByStatusPaged(status, pageable);
        } else {
            complaints = complaintService.getAllComplaintsPaged(pageable);
        }
        return complaints.map(c -> piiMaskingService.maskComplaint(c, false));
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id, HttpServletRequest request) {
        return piiMaskingService.maskComplaint(complaintService.getComplaint(id), false);
    }

    /**
     * Public tracker. Always masked: the caller proved knowledge of a reference number, which is not
     * proof of being the complainant, and reference numbers are shared over email and phone.
     */
    @GetMapping("/track/{complaintNumber}")
    public Map<String, Object> track(@PathVariable String complaintNumber) {
        return piiMaskingService.maskComplaint(
                complaintService.getByComplaintNumber(complaintNumber), false);
    }

    @PostMapping
    public Map<String, Object> file(@Valid @RequestBody FileComplaintRequest request) {
        // The filer supplied this PII, so echoing it back reveals nothing they do not already know.
        return piiMaskingService.maskComplaint(complaintService.fileComplaint(request), true);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id,
                                      @RequestBody UpdateComplaintRequest request) {
        return piiMaskingService.maskComplaint(complaintService.updateComplaint(id, request), false);
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
}
