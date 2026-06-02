package com.rbi.cms.ingestion.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.ingestion.dto.AdminDashboardStats;
import com.rbi.cms.ingestion.entity.ComplaintMaster;
import com.rbi.cms.ingestion.repository.ComplaintMasterRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "Administrative statistics and metrics")
public class AdminDashboardController {

    private final ComplaintMasterRepository repository;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics", description = "Returns overall complaint stats, breakdowns, and SLA metrics")
    public ResponseEntity<ApiResponse<AdminDashboardStats>> getStats() {

        long total = repository.count();

        List<ComplaintStatus> closedStatuses = List.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);

        long resolved = repository.countByStatus(ComplaintStatus.RESOLVED) + repository.countByStatus(ComplaintStatus.CLOSED);
        long escalated = repository.countByStatus(ComplaintStatus.ESCALATED);
        long open = total - resolved;

        long slaBreached = repository.countBySlaDueDateBeforeAndStatusNotIn(Instant.now(), closedStatuses);

        Map<String, Long> statusBreakdown = repository.countByStatusGrouped().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Long> categoryBreakdown = repository.countByCategoryGrouped().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Long> priorityBreakdown = repository.countByPriorityGrouped().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Long> teamWorkload = repository.countByTeamGrouped().stream()
                .collect(Collectors.toMap(
                        row -> row[0] != null ? row[0].toString() : "UNASSIGNED",
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<AdminDashboardStats.RecentComplaint> recent = repository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(c -> AdminDashboardStats.RecentComplaint.builder()
                        .complaintId(c.getComplaintId())
                        .subject(c.getSubject())
                        .category(c.getCategory() != null ? c.getCategory().name() : "")
                        .status(c.getStatus().name())
                        .priority(c.getPriority() != null ? c.getPriority().name() : "")
                        .assignedTeam(c.getAssignedTeam())
                        .createdAt(c.getCreatedAt().toString())
                        .build())
                .toList();

        List<AdminDashboardStats.SlaBreachedComplaint> breachedList = repository
                .findSlaBreached(Instant.now(), closedStatuses).stream()
                .limit(10)
                .map(c -> AdminDashboardStats.SlaBreachedComplaint.builder()
                        .complaintId(c.getComplaintId())
                        .subject(c.getSubject())
                        .status(c.getStatus().name())
                        .assignedTeam(c.getAssignedTeam())
                        .slaDueDate(c.getSlaDueDate().toString())
                        .overdueDays(Duration.between(c.getSlaDueDate(), Instant.now()).toDays())
                        .build())
                .toList();

        double avgResolutionHours = computeAvgResolutionHours();

        AdminDashboardStats stats = AdminDashboardStats.builder()
                .totalComplaints(total)
                .openComplaints(open)
                .resolvedComplaints(resolved)
                .escalatedComplaints(escalated)
                .slaBreached(slaBreached)
                .avgResolutionHours(avgResolutionHours)
                .statusBreakdown(statusBreakdown)
                .categoryBreakdown(categoryBreakdown)
                .priorityBreakdown(priorityBreakdown)
                .teamWorkload(teamWorkload)
                .recentComplaints(recent)
                .slaBreachedComplaints(breachedList)
                .build();

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private double computeAvgResolutionHours() {
        List<ComplaintMaster> all = repository.findAll();
        List<ComplaintMaster> resolved = all.stream()
                .filter(c -> c.getResolvedAt() != null && c.getCreatedAt() != null)
                .toList();

        if (resolved.isEmpty()) return 0.0;

        double totalHours = resolved.stream()
                .mapToDouble(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toHours())
                .sum();

        return Math.round((totalHours / resolved.size()) * 10.0) / 10.0;
    }
}
