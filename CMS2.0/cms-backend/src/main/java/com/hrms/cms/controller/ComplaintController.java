package com.hrms.cms.controller;

import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.dto.UpdateComplaintRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

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
    public Complaint file(@RequestBody FileComplaintRequest request) {
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
}
