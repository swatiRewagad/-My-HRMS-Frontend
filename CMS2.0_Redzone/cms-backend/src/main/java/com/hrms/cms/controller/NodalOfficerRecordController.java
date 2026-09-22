package com.hrms.cms.controller;

import com.hrms.cms.dto.CreateNodalOfficerRecordRequest;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.NodalOfficerRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UST572-575: Dealing Officer screens for Nodal Officer records.
 */
@RestController
@RequestMapping("/api/nodal-officer-records")
@RequiredArgsConstructor
public class NodalOfficerRecordController {

    private final NodalOfficerRecordService recordService;
    private final NodalOfficerRecordRepository recordRepository;

    /**
     * UST572-575. {@code @Valid} gives the form per-field messages; the service repeats the mandatory
     * checks so they hold regardless of how the service is reached.
     */
    @PostMapping
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> addRecord(
            @Valid @RequestBody CreateNodalOfficerRecordRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        NodalOfficerRecord saved = recordService.addRecord(request, userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("complaintNumber", saved.getComplaintNumber());
        data.put("entityName", saved.getEntityName());
        data.put("status", saved.getStatus());
        data.put("assignedTo", saved.getAssignedTo());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Nodal Officer record added",
                "data", data));
    }

    @GetMapping("/by-complaint/{complaintNumber}")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> byComplaint(@PathVariable String complaintNumber) {
        List<NodalOfficerRecord> records = recordRepository.findByComplaintNumber(complaintNumber);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", records));
    }
}
