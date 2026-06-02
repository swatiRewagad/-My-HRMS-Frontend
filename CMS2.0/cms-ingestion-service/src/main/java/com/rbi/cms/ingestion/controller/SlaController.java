package com.rbi.cms.ingestion.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.ingestion.entity.ComplaintMaster;
import com.rbi.cms.ingestion.repository.ComplaintMasterRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/sla")
@RequiredArgsConstructor
@Tag(name = "SLA Monitoring", description = "SLA breach detection and escalation")
public class SlaController {

    private final ComplaintMasterRepository repository;

    @GetMapping("/breached")
    @Operation(summary = "Get SLA breached complaints")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBreached() {
        List<ComplaintStatus> excludedStatuses = List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);
        List<ComplaintMaster> breached = repository.findSlaBreached(Instant.now(), excludedStatuses);

        List<Map<String, Object>> result = breached.stream()
                .map(c -> Map.<String, Object>of(
                        "complaintId", c.getComplaintId(),
                        "status", c.getStatus().name(),
                        "assignedTeam", c.getAssignedTeam() != null ? c.getAssignedTeam() : "UNASSIGNED",
                        "slaDueDate", c.getSlaDueDate().toString(),
                        "overdueDays", Duration.between(c.getSlaDueDate(), Instant.now()).toDays()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/check")
    @Operation(summary = "Run SLA check and auto-escalate breached complaints")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> runSlaCheck() {
        List<ComplaintStatus> excludedStatuses = List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);
        List<ComplaintMaster> breached = repository.findSlaBreached(Instant.now(), excludedStatuses);

        int escalated = 0;
        for (ComplaintMaster complaint : breached) {
            if (complaint.getStatus() != ComplaintStatus.ESCALATED) {
                complaint.setStatus(ComplaintStatus.ESCALATED);
                repository.save(complaint);
                escalated++;
                log.warn("[SLA] Auto-escalating complaint {} (overdue {} days)",
                        complaint.getComplaintId(),
                        Duration.between(complaint.getSlaDueDate(), Instant.now()).toDays());
            }
        }

        Map<String, Integer> result = Map.of(
                "totalBreached", breached.size(),
                "newlyEscalated", escalated
        );

        return ResponseEntity.ok(ApiResponse.success(result, "SLA check completed"));
    }
}
