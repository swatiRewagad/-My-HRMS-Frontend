package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final ComplaintRepository complaintRepository;
    private final ComplaintService complaintService;
    private final BankRepository bankRepository;

    private static final List<String> CLOSED_STATUSES = List.of("resolved", "closed", "rejected", "withdrawn", "adjudicated", "conciliated");

    @GetMapping("/rbio/tasks")
    public ResponseEntity<Map<String, Object>> getRbioTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {
        return getTasksByDepartment("RBIO", role, officer);
    }

    @GetMapping("/cepc/tasks")
    public ResponseEntity<Map<String, Object>> getCepcTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {
        return getTasksByDepartment("CEPC", role, officer);
    }

    @PostMapping("/rbio/assign/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> assignToRbio(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return assignComplaint(complaintNumber, "RBIO", request);
    }

    @PostMapping("/cepc/assign/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> assignToCepc(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return assignComplaint(complaintNumber, "CEPC", request);
    }

    @PostMapping("/rbio/action/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> rbioAction(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return performAction(complaintNumber, "RBIO", request);
    }

    @PostMapping("/cepc/action/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> cepcAction(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return performAction(complaintNumber, "CEPC", request);
    }

    @GetMapping("/rbio/completed")
    public ResponseEntity<Map<String, Object>> getRbioCompleted(
            @RequestParam(required = false) String officer) {
        return getCompletedByDepartment("RBIO", officer);
    }

    @GetMapping("/cepc/completed")
    public ResponseEntity<Map<String, Object>> getCepcCompleted(
            @RequestParam(required = false) String officer) {
        return getCompletedByDepartment("CEPC", officer);
    }

    @GetMapping("/unassigned")
    public ResponseEntity<Map<String, Object>> getUnassigned() {
        List<Complaint> unassigned = complaintRepository.findByStatusAndDepartmentIsNullOrderByCreatedAtDesc("pending");
        return buildResponse(true, "Unassigned complaints", buildTaskList(unassigned));
    }

    @PostMapping("/route/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> routeComplaint(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();
        String department = request.getOrDefault("department", "RBIO");
        String role = request.getOrDefault("role", department + "_OFFICER");
        String officer = request.getOrDefault("officer", "");

        c.setDepartment(department);
        c.setAssignedRole(role);
        c.setAssignedOfficer(officer);
        c.setStatus("assigned");
        complaintRepository.save(c);

        complaintService.addTimeline(c.getId(), "ROUTED", "system",
                "Routed to " + department + " - " + role, "pending", "assigned");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("department", department);
        data.put("assignedRole", role);
        data.put("assignedOfficer", officer);
        data.put("status", "assigned");

        return buildResponse(true, "Complaint routed successfully", data);
    }

    private ResponseEntity<Map<String, Object>> getCompletedByDepartment(String dept, String officer) {
        List<Complaint> completed = new java.util.ArrayList<>();
        for (String status : CLOSED_STATUSES) {
            completed.addAll(complaintRepository.findByDepartmentAndStatusOrderByCreatedAtDesc(dept, status));
        }
        return buildResponse(true, "Completed tasks", buildTaskList(completed));
    }

    private ResponseEntity<Map<String, Object>> getTasksByDepartment(String dept, String role, String officer) {
        List<Complaint> tasks;

        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    dept, officer, CLOSED_STATUSES);
        } else if (role != null && !role.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    dept, role, CLOSED_STATUSES);
        } else {
            tasks = complaintRepository.findByDepartmentAndStatusOrderByCreatedAtDesc(dept, "assigned");
        }

        return buildResponse(true, "Tasks retrieved", buildTaskList(tasks));
    }

    private ResponseEntity<Map<String, Object>> assignComplaint(String complaintNumber, String dept, Map<String, String> request) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();
        String role = request.getOrDefault("role", dept + "_OFFICER");
        String officer = request.getOrDefault("officer", "");

        String prevStatus = c.getStatus();
        c.setDepartment(dept);
        c.setAssignedRole(role);
        c.setAssignedOfficer(officer);
        c.setStatus("assigned");
        complaintRepository.save(c);

        complaintService.addTimeline(c.getId(), "ASSIGNED", request.getOrDefault("actor", "system"),
                "Assigned to " + officer + " (" + role + ")", prevStatus, "assigned");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("assignedTo", officer);
        data.put("role", role);

        return buildResponse(true, "Complaint assigned", data);
    }

    private ResponseEntity<Map<String, Object>> performAction(String complaintNumber, String dept, Map<String, String> request) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();
        String action = request.getOrDefault("action", "").toUpperCase();
        String remarks = request.getOrDefault("remarks", "");
        String actor = request.getOrDefault("actor", "");
        String prevStatus = c.getStatus();

        switch (action) {
            case "APPROVE":
                c.setStatus("approved");
                c.setAssignedRole(getNextRole(dept, c.getAssignedRole()));
                break;
            case "REJECT":
                c.setStatus("rejected");
                c.setResolvedAt(LocalDateTime.now());
                break;
            case "ESCALATE":
                c.setStatus("escalated");
                c.setEscalatedAt(LocalDateTime.now());
                c.setAssignedRole(getNextRole(dept, c.getAssignedRole()));
                break;
            case "RETURN_TO_OFFICER":
                c.setStatus("returned");
                c.setAssignedRole(dept + "_OFFICER");
                break;
            case "RESOLVE":
                c.setStatus("resolved");
                c.setResolvedAt(LocalDateTime.now());
                break;
            case "CONCILIATION_SUCCESS":
                c.setStatus("conciliated");
                c.setResolvedAt(LocalDateTime.now());
                break;
            case "CONCILIATION_FAILED":
                c.setStatus("escalated");
                c.setAssignedRole(dept + "_ADJUDICATOR");
                break;
            case "ADJUDICATION_AWARD":
                c.setStatus("adjudicated");
                c.setResolvedAt(LocalDateTime.now());
                break;
            default:
                return buildResponse(false, "Unknown action: " + action, null);
        }

        complaintRepository.save(c);
        complaintService.addTimeline(c.getId(), action, actor, remarks, prevStatus, c.getStatus());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("action", action);
        data.put("newStatus", c.getStatus());
        data.put("assignedRole", c.getAssignedRole());

        return buildResponse(true, "Action performed: " + action, data);
    }

    private String getNextRole(String dept, String currentRole) {
        if (currentRole == null) return dept + "_OFFICER";
        Map<String, String> escalation = Map.of(
                dept + "_OFFICER", dept + "_SUPERVISOR",
                dept + "_SUPERVISOR", dept + "_CONCILIATOR",
                dept + "_CONCILIATOR", dept + "_ADJUDICATOR"
        );
        return escalation.getOrDefault(currentRole, currentRole);
    }

    private List<Map<String, Object>> buildTaskList(List<Complaint> complaints) {
        return complaints.stream().map(c -> {
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("complaintId", c.getId());
            task.put("complaintNumber", c.getComplaintNumber());
            task.put("subject", c.getSubject());
            task.put("complainantName", c.getComplainantName());
            task.put("priority", c.getPriority() != null ? c.getPriority().toUpperCase() : "MEDIUM");
            task.put("status", c.getStatus() != null ? c.getStatus().toUpperCase() : "PENDING");
            task.put("assignedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : "");
            task.put("slaDueDate", c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : "");
            task.put("entityName", c.getBankId() != null ?
                    bankRepository.findById(c.getBankId()).map(b -> b.getName()).orElse("") : "");
            task.put("department", c.getDepartment());
            task.put("assignedRole", c.getAssignedRole());
            task.put("assignedOfficer", c.getAssignedOfficer());
            return task;
        }).collect(Collectors.toList());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
