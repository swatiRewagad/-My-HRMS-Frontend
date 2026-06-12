package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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

    @GetMapping("/rbio/all-tasks")
    public ResponseEntity<Map<String, Object>> getRbioAllTasks(
            @RequestParam(required = false) String officer) {
        return getAllTasksByDepartment("RBIO", officer);
    }

    @GetMapping("/cepc/tasks")
    public ResponseEntity<Map<String, Object>> getCepcTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {
        return getTasksByDepartment("CEPC", role, officer);
    }

    @GetMapping("/cepc/all-tasks")
    public ResponseEntity<Map<String, Object>> getCepcAllTasks(
            @RequestParam(required = false) String officer) {
        return getAllTasksByDepartment("CEPC", officer);
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

    @GetMapping("/cepc/contact-person/tasks")
    public ResponseEntity<Map<String, Object>> getContactPersonTasks(
            @RequestParam(required = false) String officer) {
        List<Complaint> tasks;
        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    "CEPC", "CEPC_CONTACT_PERSON", officer, CLOSED_STATUSES);
        } else {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    "CEPC", "CEPC_CONTACT_PERSON", CLOSED_STATUSES);
        }
        return buildResponse(true, "Contact Person tasks", buildTaskList(tasks));
    }

    @PostMapping("/cepc/create-complaint")
    public ResponseEntity<Map<String, Object>> cepcCreateComplaint(
            @RequestBody Map<String, String> request) {
        String number = "CMP-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now())
                + "-" + (100000 + new Random().nextInt(900000));

        Complaint c = new Complaint();
        c.setComplaintNumber(number);
        c.setComplainantName(request.getOrDefault("complainantName", ""));
        c.setComplainantEmail(request.getOrDefault("complainantEmail", ""));
        c.setComplainantPhone(request.getOrDefault("complainantPhone", ""));
        c.setComplainantAddress(request.getOrDefault("complainantAddress", ""));
        c.setSubject(request.getOrDefault("subject", ""));
        c.setDescription(request.getOrDefault("description", ""));
        c.setEntityCode(request.getOrDefault("entityName", ""));
        c.setPriority(request.getOrDefault("priority", "MEDIUM"));
        c.setFilingType(request.getOrDefault("filingType", "CEPC_MANUAL"));
        c.setDepartment("CEPC");
        c.setAssignedRole("CEPC_DO");
        c.setAssignedOfficer(request.getOrDefault("createdBy", ""));
        c.setStatus("assigned");
        c.setWorkflowStage("CREATED");
        complaintRepository.save(c);

        complaintService.addTimeline(c.getId(), "CREATED", request.getOrDefault("createdBy", "system"),
                "Complaint created by CEPC Dealing Official", "new", "assigned");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", number);
        data.put("complaintId", c.getId());
        data.put("status", "assigned");
        data.put("department", "CEPC");

        return buildResponse(true, "Complaint created successfully", data);
    }

    @GetMapping("/crpc/transfers")
    public ResponseEntity<Map<String, Object>> getCrpcTransfers() {
        List<Complaint> transfers = complaintRepository.findByDepartmentAndStatusInOrderByCreatedAtDesc(
                "CRPC", List.of("sent_to_other", "pending_approval", "sent_back", "forwarded_external"));
        List<Map<String, Object>> data = transfers.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("complaintId", c.getId() != null ? c.getId().toString() : "");
            item.put("complaintNumber", c.getComplaintNumber());
            item.put("from", c.getComplainantEmail());
            item.put("pending", c.getCreatedAt() != null ?
                    Duration.between(c.getCreatedAt(), LocalDateTime.now()).toDays() : 0);
            item.put("fromOffice", c.getDepartment() != null ? c.getDepartment() : "CRPC");
            item.put("targetOffice", c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "");
            item.put("status", c.getStatus() != null ? c.getStatus() : "");
            item.put("entityName", c.getEntityCode() != null ? c.getEntityCode() : "");
            item.put("proposedCategory", c.getFilingType() != null ? c.getFilingType() : "");
            item.put("creationDate", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            item.put("language", "");
            item.put("territory", "");
            item.put("subject", c.getSubject());
            item.put("complainantName", c.getComplainantName());
            item.put("complainantEmail", c.getComplainantEmail());
            item.put("complainantPhone", c.getComplainantPhone());
            item.put("description", c.getDescription());
            item.put("timeline", List.of());
            return item;
        }).collect(Collectors.toList());
        return buildResponse(true, "Transfer complaints retrieved", data);
    }

    @PostMapping("/crpc/transfer-action/{complaintId}")
    public ResponseEntity<Map<String, Object>> crpcTransferAction(
            @PathVariable String complaintId,
            @RequestBody Map<String, String> request) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintId);
        if (opt.isEmpty()) {
            opt = complaintRepository.findById(Long.valueOf(complaintId));
        }
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();
        String action = request.getOrDefault("action", "").toUpperCase();
        String remarks = request.getOrDefault("remarks", "");
        String actor = request.getOrDefault("actor", "");
        String prevStatus = c.getStatus();

        if ("APPROVE_TRANSFER".equals(action)) {
            c.setStatus("assigned");
            c.setWorkflowStage("TRANSFER_APPROVED");
            c.setAssignedRole("CRPC_DO");
        } else if ("REJECT_TRANSFER".equals(action)) {
            c.setStatus("sent_back");
            c.setWorkflowStage("TRANSFER_REJECTED");
        } else {
            return buildResponse(false, "Unknown transfer action: " + action, null);
        }

        complaintRepository.save(c);
        complaintService.addTimeline(c.getId(), action, actor, remarks, prevStatus, c.getStatus());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("action", action);
        data.put("newStatus", c.getStatus());
        return buildResponse(true, "Transfer action performed", data);
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

    private ResponseEntity<Map<String, Object>> getAllTasksByDepartment(String dept, String officer) {
        List<Complaint> tasks;
        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedOfficerOrderByCreatedAtDesc(dept, officer);
        } else {
            tasks = complaintRepository.findByDepartmentOrderByCreatedAtDesc(dept);
        }
        return buildResponse(true, "All tasks retrieved", buildTaskList(tasks));
    }

    private ResponseEntity<Map<String, Object>> getCompletedByDepartment(String dept, String officer) {
        List<Complaint> completed = new java.util.ArrayList<>();
        if (officer != null && !officer.isBlank()) {
            for (String status : CLOSED_STATUSES) {
                completed.addAll(complaintRepository.findByDepartmentAndAssignedOfficerAndStatusOrderByCreatedAtDesc(dept, officer, status));
            }
        } else {
            for (String status : CLOSED_STATUSES) {
                completed.addAll(complaintRepository.findByDepartmentAndStatusOrderByCreatedAtDesc(dept, status));
            }
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
            tasks = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(dept, CLOSED_STATUSES);
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
            case "ACCEPT":
            case "TAKE_ACTION":
                c.setStatus("in_progress");
                c.setAssignedOfficer(actor);
                c.setWorkflowStage("EXAMINATION");
                break;
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

            // ═══ CEPC Workflow Actions ═══
            case "REQUEST_INFO":
                c.setStatus("info_requested");
                c.setWorkflowStage("AWAITING_INFO");
                break;
            case "INFO_RECEIVED":
                c.setStatus("in_progress");
                c.setWorkflowStage("EXAMINATION");
                break;
            case "FORWARD_DEPT":
                c.setStatus("forwarded");
                c.setWorkflowStage("DEPT_CONSULTATION");
                break;
            case "COMMENTS_RECEIVED":
                c.setStatus("in_progress");
                c.setWorkflowStage("EXAMINATION");
                break;
            case "SCHEDULE_MEETING":
                c.setWorkflowStage("MEETING_SCHEDULED");
                break;
            case "SUBMIT_FOR_REVIEW":
                c.setStatus("reviewer_review");
                c.setAssignedRole("CEPC_REVIEWER");
                c.setWorkflowStage("REVIEWER_REVIEW");
                break;
            case "APPROVE_REVIEW":
                c.setStatus("incharge_review");
                c.setAssignedRole("CEPC_INCHARGE");
                c.setWorkflowStage("INCHARGE_REVIEW");
                break;
            case "SEND_BACK_DO":
                c.setStatus("sent_back");
                c.setAssignedRole("CEPC_DO");
                c.setWorkflowStage("SENT_BACK_TO_DO");
                break;
            case "SEND_BACK_REVIEWER":
                c.setStatus("reviewer_review");
                c.setAssignedRole("CEPC_REVIEWER");
                c.setWorkflowStage("REVIEWER_REVIEW");
                break;
            case "SEND_BACK_INCHARGE":
                c.setStatus("incharge_review");
                c.setAssignedRole("CEPC_INCHARGE");
                c.setWorkflowStage("INCHARGE_REVIEW");
                break;
            case "APPROVE_CLOSURE":
                c.setStatus("awaiting_closure");
                c.setAssignedRole("CEPC_CLOSING_AUTHORITY");
                c.setWorkflowStage("AWAITING_CLOSURE");
                break;
            case "CLOSE_COMPLAINT":
                c.setStatus("closed");
                c.setClosedAt(LocalDateTime.now());
                c.setResolvedAt(LocalDateTime.now());
                c.setWorkflowStage("CLOSED");
                break;
            case "REASSIGN":
                String targetUser = request.getOrDefault("targetUser", "");
                if (!targetUser.isEmpty()) {
                    c.setAssignedOfficer(targetUser);
                }
                c.setStatus("assigned");
                c.setWorkflowStage("REASSIGNED");
                break;

            // ═══ Contact Person Actions ═══
            case "FORWARD_TO_CONTACT":
                String contactPerson = request.getOrDefault("targetUser", "");
                c.setStatus("forwarded_to_contact");
                c.setAssignedRole("CEPC_CONTACT_PERSON");
                if (!contactPerson.isEmpty()) {
                    c.setAssignedOfficer(contactPerson);
                }
                c.setWorkflowStage("CONTACT_PERSON_REVIEW");
                break;
            case "CONTACT_RESPONSE":
                c.setStatus("in_progress");
                c.setAssignedRole("CEPC_DO");
                c.setWorkflowStage("EXAMINATION");
                break;
            case "CONTACT_REASSIGN":
                String reassignTo = request.getOrDefault("targetUser", "");
                if (!reassignTo.isEmpty()) {
                    c.setAssignedOfficer(reassignTo);
                }
                c.setWorkflowStage("CONTACT_PERSON_REVIEW");
                break;

            // ═══ Additional CEPC Workflow Actions (per diagram) ═══
            case "FORWARD_TO_INCHARGE":
                c.setStatus("incharge_review");
                c.setAssignedRole("CEPC_INCHARGE");
                c.setWorkflowStage("INCHARGE_REVIEW");
                break;
            case "FORWARD_TO_CLOSING_AUTHORITY":
                c.setStatus("awaiting_closure");
                c.setAssignedRole("CEPC_CLOSING_AUTHORITY");
                c.setWorkflowStage("AWAITING_CLOSURE");
                break;
            case "FORWARD_TO_OTHER_OFFICE":
                c.setStatus("forwarded_external");
                c.setWorkflowStage("FORWARDED_OTHER_OFFICE");
                String otherOffice = request.getOrDefault("targetOffice", "");
                if (!otherOffice.isEmpty()) {
                    c.setAssignedOfficer(otherOffice);
                }
                break;
            case "FORWARD_TO_REGULATORY_BODY":
                c.setStatus("forwarded_external");
                c.setWorkflowStage("FORWARDED_REGULATORY_BODY");
                String regulatoryBody = request.getOrDefault("targetBody", "");
                if (!regulatoryBody.isEmpty()) {
                    c.setAssignedOfficer(regulatoryBody);
                }
                break;
            case "FORWARD_TO_OTHER_RBI_DEPT":
                c.setStatus("forwarded_external");
                c.setWorkflowStage("FORWARDED_OTHER_RBI_DEPT");
                String rbiDept = request.getOrDefault("targetDept", "");
                if (!rbiDept.isEmpty()) {
                    c.setAssignedOfficer(rbiDept);
                }
                break;
            case "REOPEN":
                c.setStatus("in_progress");
                c.setResolvedAt(null);
                c.setClosedAt(null);
                c.setWorkflowStage("REOPENED");
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
            String entityName = "";
            if (c.getEntityCode() != null && !c.getEntityCode().isBlank()) {
                entityName = c.getEntityCode();
            } else if (c.getBankId() != null) {
                entityName = bankRepository.findById(c.getBankId()).map(b -> b.getName()).orElse("");
            }
            task.put("entityName", entityName);
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
