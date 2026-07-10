package com.hrms.cms.controller;

import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.triage.IntakeTriageService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintApiV1Controller {

    private final ComplaintService complaintService;
    private final BankRepository bankRepository;
    private final IntakeTriageService triageService;
    private final Validator validator;

    @PostMapping
    public ResponseEntity<Map<String, Object>> registerComplaint(@RequestBody Map<String, Object> request) {
        FileComplaintRequest req = new FileComplaintRequest();
        req.setComplainantName((String) request.getOrDefault("complainantName", ""));
        req.setComplainantEmail((String) request.getOrDefault("complainantEmail", ""));
        req.setComplainantPhone((String) request.getOrDefault("complainantPhone", ""));
        req.setSubject((String) request.getOrDefault("subject", ""));
        req.setDescription((String) request.getOrDefault("description", ""));
        req.setPriority((String) request.getOrDefault("priority", "medium"));
        req.setFilingType((String) request.getOrDefault("filingType", "ONLINE"));

        if (request.get("priorReComplaint") != null) {
            req.setPriorReComplaint(Boolean.valueOf(request.get("priorReComplaint").toString()));
        }
        if (request.get("reComplaintDate") != null) {
            req.setReComplaintDate(java.time.LocalDate.parse(request.get("reComplaintDate").toString()));
        }
        if (request.get("reComplaintReference") != null) {
            req.setReComplaintReference(request.get("reComplaintReference").toString());
        }
        if (request.get("reRepliedAndDissatisfied") != null) {
            req.setReRepliedAndDissatisfied(Boolean.valueOf(request.get("reRepliedAndDissatisfied").toString()));
        }

        Set<ConstraintViolation<FileComplaintRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Validation failed: " + errors));
        }

        Complaint c = complaintService.fileComplaint(req);

        try {
            triageService.triageOnRegistration(c);
        } catch (Exception e) {
            // Triage failure must not block complaint registration
        }

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("complaintId", c.getComplaintNumber());
        ack.put("status", "REGISTERED");
        ack.put("registeredAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : LocalDateTime.now().toString());
        ack.put("slaDueDate", c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : LocalDateTime.now().plusDays(30).toString());
        ack.put("acknowledgementMessage", "Your complaint has been registered successfully. Use the reference number to track status.");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Complaint registered successfully");
        response.put("data", ack);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getComplaintsByPhone(@RequestParam String phone) {
        List<Complaint> complaints = complaintService.getByComplainantPhone(phone);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        List<Map<String, Object>> items = complaints.stream().map(c -> {
            String bankName = "";
            if (c.getBankId() != null) {
                bankName = bankRepository.findById(c.getBankId())
                        .map(b -> b.getName()).orElse("");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("complaintId", c.getComplaintNumber());
            item.put("entityName", bankName.isEmpty() ? c.getSubject() : bankName);
            item.put("complaintDate", c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "");
            item.put("status", c.getStatus() != null ? c.getStatus().toUpperCase() : "PENDING");
            item.put("comments", c.getDescription() != null ? c.getDescription().substring(0, Math.min(c.getDescription().length(), 50)) : "");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Complaints retrieved");
        response.put("data", items);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getComplaintDetail(@PathVariable String complaintNumber) {
        Complaint c;
        try {
            c = complaintService.getByComplaintNumber(complaintNumber);
        } catch (RuntimeException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Complaint not found");
            error.put("data", null);
            error.put("correlationId", UUID.randomUUID().toString());
            error.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        List<ComplaintTimeline> timeline = complaintService.getTimeline(c.getId());

        String bankName = "";
        if (c.getBankId() != null) {
            bankName = bankRepository.findById(c.getBankId())
                    .map(b -> b.getName())
                    .orElse("");
        }

        String registeredAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : "";
        String slaDueDate = c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : "";

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", c.getId());
        detail.put("complaintId", c.getComplaintNumber());
        detail.put("complaintNumber", c.getComplaintNumber());
        detail.put("category", "General");
        detail.put("priority", c.getPriority() != null ? c.getPriority().toUpperCase() : "MEDIUM");
        detail.put("status", c.getStatus() != null ? c.getStatus().toUpperCase() : "NEW");
        detail.put("subject", c.getSubject());
        detail.put("description", c.getDescription());
        detail.put("complainantName", c.getComplainantName());
        detail.put("complainantEmail", c.getComplainantEmail());
        detail.put("complainantPhone", c.getComplainantPhone());
        detail.put("entityName", bankName);
        detail.put("entityType", "BANK");
        detail.put("amountInvolved", 0);
        detail.put("transactionDate", c.getBankComplaintDate() != null ? c.getBankComplaintDate().toString() : null);
        detail.put("assignedTeam", c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "Unassigned");
        detail.put("assignedTo", c.getAssignedOfficer());
        detail.put("registeredAt", registeredAt);
        detail.put("createdAt", registeredAt);
        detail.put("slaDueDate", slaDueDate);
        detail.put("resolutionSummary", null);
        detail.put("resolvedAt", c.getResolvedAt() != null ? c.getResolvedAt().toString() : null);
        detail.put("timeline", timeline.stream().map(t -> {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("fromStatus", t.getFromStatus());
            tm.put("toStatus", t.getToStatus());
            tm.put("action", t.getAction());
            tm.put("timestamp", t.getPerformedAt() != null ? t.getPerformedAt().toString() : "");
            tm.put("remarks", t.getRemarks());
            return tm;
        }).collect(Collectors.toList()));
        detail.put("communications", List.of());
        detail.put("documents", List.of());
        detail.put("triageSignal", c.getTriageSignal());
        detail.put("triageFlags", c.getTriageFlags());
        detail.put("eligibilityTimeline", c.getEligibilityTimeline());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", detail);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentComplaints(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(limit, 50);
        List<Complaint> complaints = complaintService.getAllComplaintsPaged(PageRequest.of(0, limit)).getContent();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");

        List<Map<String, Object>> items = complaints.stream().map(c -> {
            String bankName = "";
            if (c.getBankId() != null) {
                bankName = bankRepository.findById(c.getBankId())
                        .map(b -> b.getName()).orElse("");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("complaintNumber", c.getComplaintNumber());
            item.put("subject", c.getSubject());
            item.put("entityName", bankName);
            item.put("complainantName", c.getComplainantName());
            item.put("status", c.getStatus());
            item.put("date", c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Recent complaints retrieved");
        response.put("data", items);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }
}
