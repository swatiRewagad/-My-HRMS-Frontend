package com.hrms.cms.controller;

import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintComment;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintCommentRepository;
import com.hrms.cms.repository.SimulatedEmailRepository;
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
    private final ComplaintCategoryRepository categoryRepository;
    private final SimulatedEmailRepository simulatedEmailRepository;
    private final ComplaintCommentRepository complaintCommentRepository;
    private final IntakeTriageService triageService;
    private final Validator validator;
    private final com.hrms.cms.service.ComplaintRoutingService complaintRoutingService;
    private final com.hrms.cms.service.KeycloakUserService keycloakUserService;
    private final com.hrms.cms.repository.OfficerAvailabilityRepository officerAvailabilityRepository;
    private final com.hrms.cms.repository.OfficeCodeMasterRepository officeCodeMasterRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> registerComplaint(@RequestBody Map<String, Object> request) {
        FileComplaintRequest req = new FileComplaintRequest();
        req.setComplainantName((String) request.getOrDefault("complainantName", ""));
        req.setComplainantEmail((String) request.getOrDefault("complainantEmail", ""));
        req.setComplainantPhone((String) request.getOrDefault("complainantPhone", ""));
        req.setComplainantAddress((String) request.get("complainantAddress"));
        req.setComplainantState((String) request.get("complainantState"));
        req.setComplainantDistrict((String) request.get("complainantDistrict"));
        req.setSubject((String) request.getOrDefault("subject", ""));
        req.setDescription((String) request.getOrDefault("description", ""));
        req.setPriority((String) request.getOrDefault("priority", "medium"));
        req.setFilingType((String) request.getOrDefault("filingType", "ONLINE"));

        if (request.get("regulatedEntityId") != null) {
            req.setRegulatedEntityId(Long.valueOf(request.get("regulatedEntityId").toString()));
        }
        if (request.get("entityName") != null) {
            req.setEntityName(request.get("entityName").toString());
        }
        if (request.get("entityType") != null) {
            req.setEntityType(request.get("entityType").toString());
        }
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

        // Resolve category name to ID
        if (request.get("category") != null) {
            String categoryName = request.get("category").toString();
            req.setCategoryName(categoryName);
            categoryRepository.findFirstByNameIgnoreCase(categoryName)
                    .ifPresent(cat -> req.setCategoryId(cat.getId()));
        }
        if (req.getCategoryId() == null && request.get("categoryId") != null) {
            req.setCategoryId(Long.valueOf(request.get("categoryId").toString()));
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

        String bankName = c.getEntityName() != null ? c.getEntityName() : "";
        if (bankName.isEmpty() && c.getBankId() != null) {
            bankName = bankRepository.findById(c.getBankId())
                    .map(b -> b.getName())
                    .orElse("");
        }

        String categoryName = c.getCategoryName() != null ? c.getCategoryName() : "General";
        if ("General".equals(categoryName) && c.getCategoryId() != null) {
            categoryName = categoryRepository.findById(c.getCategoryId())
                    .map(cat -> cat.getName()).orElse("General");
        }

        String registeredAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : "";
        String slaDueDate = c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : "";

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", c.getId());
        detail.put("complaintId", c.getComplaintNumber());
        detail.put("complaintNumber", c.getComplaintNumber());
        detail.put("category", categoryName);
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
        detail.put("closureClause", c.getClosureClause());
        detail.put("proposedAction", c.getProposedAction());
        detail.put("proposedClause", c.getProposedClause());
        detail.put("forwardedOfficeCode", c.getForwardedOfficeCode());
        detail.put("forwardedOfficeName", c.getForwardedOfficeCode() != null
                ? officeCodeMasterRepository.findByOfficeCodeAndIsActiveTrue(c.getForwardedOfficeCode())
                        .map(o -> o.getOfficeName()).orElse(c.getForwardedOfficeCode())
                : null);
        detail.put("preForwardOfficer", c.getPreForwardOfficer());
        detail.put("preForwardRole", c.getPreForwardRole());
        detail.put("closureClauseDescription", c.getClosureClauseDescription());
        detail.put("complaintStatusOnPortal", c.getComplaintStatusOnPortal());
        detail.put("speakingOrderGenerated", c.getSpeakingOrderGenerated());
        detail.put("gistOfCase", c.getGistOfCase());
        detail.put("gistOfCaseRegional", c.getGistOfCaseRegional());

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

    @GetMapping("/{complaintNumber}/emails")
    public ResponseEntity<Map<String, Object>> getComplaintEmails(@PathVariable String complaintNumber) {
        List<SimulatedEmail> emails = simulatedEmailRepository.findByComplaintNumberOrderBySentAtDesc(complaintNumber);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        List<Map<String, Object>> items = emails.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("subject", e.getSubject());
            item.put("from", e.getFromEmail());
            item.put("to", e.getToEmail());
            item.put("body", e.getBody());
            item.put("date", e.getSentAt() != null ? e.getSentAt().format(fmt) : "");
            item.put("status", e.getStatus());
            item.put("direction", e.getDirection());
            item.put("attachments", e.getAttachmentUrl() != null ?
                List.of(Map.of("name", e.getAttachmentUrl(), "size", "")) : List.of());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", items);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{complaintNumber}/emails")
    public ResponseEntity<Map<String, Object>> sendComplaintEmail(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {

        SimulatedEmail email = SimulatedEmail.builder()
                .messageId("MSG-" + UUID.randomUUID().toString().substring(0, 8))
                .threadId("THR-" + complaintNumber)
                .fromEmail(request.getOrDefault("from", "cmssupportngp@rbi.org.in"))
                .toEmail(request.getOrDefault("to", ""))
                .subject(request.getOrDefault("subject", ""))
                .body(request.getOrDefault("body", ""))
                .direction("OUTBOUND")
                .status(request.getOrDefault("status", "SENT"))
                .complaintNumber(complaintNumber)
                .build();

        simulatedEmailRepository.save(email);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Email " + email.getStatus().toLowerCase());
        response.put("data", Map.of("id", email.getId(), "messageId", email.getMessageId()));
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{complaintNumber}/comments")
    public ResponseEntity<Map<String, Object>> getComments(@PathVariable String complaintNumber) {
        List<ComplaintComment> comments = complaintCommentRepository.findByComplaintNumberOrderByCreatedAtDesc(complaintNumber);

        List<Map<String, Object>> items = comments.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("author", c.getAuthor());
            item.put("initials", c.getInitials());
            item.put("text", c.getText());
            item.put("role", c.getRole());
            item.put("color", c.getColor());
            item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", items);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{complaintNumber}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {

        ComplaintComment comment = ComplaintComment.builder()
                .complaintNumber(complaintNumber)
                .author(request.getOrDefault("author", "Unknown"))
                .initials(request.getOrDefault("initials", ""))
                .text(request.getOrDefault("text", ""))
                .role(request.getOrDefault("role", ""))
                .color(request.getOrDefault("color", null))
                .build();

        complaintCommentRepository.save(comment);

        Map<String, Object> commentData = new LinkedHashMap<>();
        commentData.put("id", comment.getId());
        commentData.put("author", comment.getAuthor());
        commentData.put("initials", comment.getInitials());
        commentData.put("text", comment.getText());
        commentData.put("role", comment.getRole());
        commentData.put("color", comment.getColor());
        commentData.put("createdAt", comment.getCreatedAt().toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Comment added");
        response.put("data", commentData);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/nodal-records/{recordNumber}/comments")
    public ResponseEntity<Map<String, Object>> getNodalRecordComments(@PathVariable String recordNumber) {
        List<ComplaintComment> comments = complaintCommentRepository.findByNoRecordNumberOrderByCreatedAtDesc(recordNumber);

        List<Map<String, Object>> items = comments.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("author", c.getAuthor());
            item.put("initials", c.getInitials());
            item.put("text", c.getText());
            item.put("target", c.getTarget());
            item.put("color", c.getColor());
            item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", items);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/nodal-records/{recordNumber}/comments")
    public ResponseEntity<Map<String, Object>> addNodalRecordComment(
            @PathVariable String recordNumber,
            @RequestBody Map<String, String> request) {

        String target = request.getOrDefault("target", "NO");
        ComplaintComment comment = ComplaintComment.builder()
                .noRecordNumber(recordNumber)
                .complaintNumber(request.getOrDefault("complaintNumber", ""))
                .author(request.getOrDefault("author", "Unknown"))
                .initials(request.getOrDefault("initials", ""))
                .text(request.getOrDefault("text", ""))
                .target(target)
                .color(request.getOrDefault("color", null))
                .build();

        complaintCommentRepository.save(comment);

        Map<String, Object> commentData = new LinkedHashMap<>();
        commentData.put("id", comment.getId());
        commentData.put("author", comment.getAuthor());
        commentData.put("initials", comment.getInitials());
        commentData.put("text", comment.getText());
        commentData.put("target", comment.getTarget());
        commentData.put("color", comment.getColor());
        commentData.put("createdAt", comment.getCreatedAt().toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Comment added");
        response.put("data", commentData);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{complaintNumber}/send-for-approval")
    public ResponseEntity<Map<String, Object>> sendForApproval(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {

        String target = (String) request.getOrDefault("target", "");
        String assignedTo = (String) request.getOrDefault("assignedTo", "");
        String assignedToName = (String) request.getOrDefault("assignedToName", "");
        String assignmentMode = (String) request.getOrDefault("assignmentMode", "MANUAL");
        String performedBy = (String) request.getOrDefault("performedBy", assignedTo);
        String proposedAction = (String) request.get("proposedAction");
        String proposedClause = (String) request.get("proposedClause");

        Complaint complaint;
        try {
            complaint = complaintService.getByComplaintNumber(complaintNumber);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Complaint not found: " + complaintNumber));
        }

        String oldStatus = complaint.getStatus();
        String newStatus;
        boolean isOfficeForward = "OTHER_OFFICE".equals(target);
        boolean closesImmediately = "OTHER_REGULATORY_BODIES".equals(target) || "OTHER_RBI_DEPARTMENT".equals(target);
        switch (target) {
            case "REVIEWER": newStatus = "SENT_TO_REVIEWER"; break;
            case "DEPUTY_OMBUDSMAN": newStatus = "SENT_TO_DEPUTY_OMBUDSMAN"; break;
            case "OMBUDSMAN": newStatus = "SENT_TO_OMBUDSMAN"; break;
            case "DEALING_OFFICER": newStatus = "SENT_TO_DO"; break;
            case "CLOSE": newStatus = "CLOSED"; break;
            case "OTHER_OFFICE": newStatus = "PENDING_OFFICE_HEAD_APPROVAL"; break;
            default:
                newStatus = closesImmediately ? "CLOSED" : "SENT_TO_" + target;
                break;
        }

        complaint.setStatus(newStatus);
        if ("CLOSED".equals(newStatus)) {
            complaint.setResolvedAt(LocalDateTime.now());
            if (closesImmediately) {
                complaint.setClosureClauseDescription(
                        "Forwarded to " + assignedToName + " — complaint closed on this end."
                                + (request.get("remarks") != null ? " " + request.get("remarks") : ""));
            }
            if (request.get("closureClause") != null)
                complaint.setClosureClause(request.get("closureClause").toString());
            if (!closesImmediately && request.get("remarks") != null)
                complaint.setClosureClauseDescription(request.get("remarks").toString());
            if (request.get("complaintStatusOnPortal") != null)
                complaint.setComplaintStatusOnPortal(request.get("complaintStatusOnPortal").toString());
            if (request.get("speakingOrderGenerated") != null)
                complaint.setSpeakingOrderGenerated(request.get("speakingOrderGenerated").toString());
            if (request.get("gistOfCase") != null)
                complaint.setGistOfCase(request.get("gistOfCase").toString());
            if (request.get("gistOfCaseRegional") != null)
                complaint.setGistOfCaseRegional(request.get("gistOfCaseRegional").toString());
        }

        if (isOfficeForward) {
            String officeCode = (String) request.get("officeCode");
            if (officeCode == null || officeCode.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "officeCode is required when forwarding to Other Office"));
            }
            String headOfficer = complaintRoutingService.assignOfficerByRole("CRPC_HEAD");
            String performedByRole = (String) request.get("performedByRole");
            complaint.setForwardedOfficeCode(officeCode);
            complaint.setPreForwardOfficer(performedBy);
            complaint.setPreForwardRole(performedByRole != null && !performedByRole.isBlank() ? performedByRole : complaint.getAssignedRole());
            complaint.setAssignedRole("CRPC_HEAD");
            complaint.setAssignedOfficer(headOfficer);
        } else {
            complaint.setAssignedOfficer(assignedTo);
        }
        if (proposedAction != null && !proposedAction.isBlank()) {
            complaint.setProposedAction(proposedAction);
        }
        if (proposedClause != null && !proposedClause.isBlank()) {
            complaint.setProposedClause(proposedClause);
        }
        complaintService.updateComplaintDirectly(complaint);

        String action = "CLOSED".equals(newStatus) ? "CLOSED" : "FORWARDED";
        String remarks;
        if (closesImmediately) {
            remarks = "Forwarded to " + assignedToName + " (" + target + ") — complaint closed.";
        } else if (isOfficeForward) {
            remarks = "Forwarded to office " + request.get("officeCode") + " — pending CRPC Head approval ("
                    + complaint.getAssignedOfficer() + ")";
        } else if ("CLOSED".equals(newStatus)) {
            remarks = "Complaint closed. " + (request.get("remarks") != null ? request.get("remarks").toString() : "");
        } else {
            remarks = "Forwarded to " + assignedToName + " (" + target + ") via " + assignmentMode;
        }
        complaintService.addTimeline(complaint.getId(), action, performedBy, remarks, oldStatus, newStatus);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", complaintNumber);
        data.put("status", newStatus);
        data.put("assignedTo", assignedTo);
        data.put("assignedToName", assignedToName);
        data.put("target", target);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Complaint forwarded to " + assignedToName);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{complaintNumber}/office-head-decision")
    public ResponseEntity<Map<String, Object>> officeHeadDecision(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {

        String decision = (String) request.getOrDefault("decision", "");
        String comment = (String) request.get("comment");
        String performedBy = (String) request.getOrDefault("performedBy", "");

        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "decision must be APPROVE or REJECT"));
        }
        if ("REJECT".equals(decision) && (comment == null || comment.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "A rejection comment is mandatory"));
        }

        Complaint complaint;
        try {
            complaint = complaintService.getByComplaintNumber(complaintNumber);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Complaint not found: " + complaintNumber));
        }

        String oldStatus = complaint.getStatus();
        String newStatus;
        String remarks;

        if ("APPROVE".equals(decision)) {
            String overrideOfficeCode = (String) request.get("overrideOfficeCode");
            String officeCode = (overrideOfficeCode != null && !overrideOfficeCode.isBlank())
                    ? overrideOfficeCode : complaint.getForwardedOfficeCode();
            if (overrideOfficeCode != null && !overrideOfficeCode.isBlank()) {
                complaint.setForwardedOfficeCode(overrideOfficeCode);
            }
            String role = "CEPC".equals(complaint.getDepartment()) ? "CEPC_OFFICER" : "RBIO_OFFICER";
            String assignedOfficer = assignOfficerByRoleAndOffice(role, officeCode);
            newStatus = "assigned";
            complaint.setAssignedRole(role);
            complaint.setAssignedOfficer(assignedOfficer);
            remarks = "Approved by CRPC Head, assigned to " + assignedOfficer + " at office " + officeCode
                    + (comment != null && !comment.isBlank() ? " — " + comment : "");
        } else {
            newStatus = "SENT_BACK";
            String role = "CEPC".equals(complaint.getDepartment()) ? "CEPC_OFFICER" : "RBIO_OFFICER";
            complaint.setAssignedRole(role);
            complaint.setAssignedOfficer(complaint.getPreForwardOfficer());
            remarks = "Rejected by CRPC Head, returned to " + complaint.getPreForwardOfficer() + " — " + comment;
        }

        complaint.setStatus(newStatus);
        complaintService.updateComplaintDirectly(complaint);
        complaintService.addTimeline(complaint.getId(), "OFFICE_HEAD_" + decision, performedBy, remarks, oldStatus, newStatus);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", complaintNumber);
        data.put("status", newStatus);
        data.put("assignedOfficer", complaint.getAssignedOfficer());
        data.put("decision", decision);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", remarks);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * Round-robin assignment scoped to a specific office. Falls back to the unscoped
     * role-wide round robin if no officer's OfficerAvailability record matches the office.
     */
    private String assignOfficerByRoleAndOffice(String role, String officeCode) {
        List<Map<String, Object>> officers = keycloakUserService.getUsersByRole(role);
        List<Map<String, Object>> officeMatched = new ArrayList<>();
        for (Map<String, Object> officer : officers) {
            String userId = (String) officer.get("userId");
            officerAvailabilityRepository.findByUserIdAndRole(userId, role).ifPresent(oa -> {
                if (officeCode != null && officeCode.equals(oa.getOfficeCode())) {
                    officeMatched.add(officer);
                }
            });
        }
        if (officeMatched.isEmpty()) {
            return complaintRoutingService.assignOfficerByRole(role);
        }
        String picked = complaintRoutingService.assignOfficerByRole(role);
        boolean pickedInOffice = officeMatched.stream().anyMatch(o -> picked.equals(o.get("userId")));
        return pickedInOffice ? picked : (String) officeMatched.get(0).get("userId");
    }
}
