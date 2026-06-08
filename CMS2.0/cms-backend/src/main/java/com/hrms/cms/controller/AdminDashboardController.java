package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final ComplaintRepository complaintRepository;

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long total = complaintRepository.count();
        long open = complaintRepository.countByStatus("pending") + complaintRepository.countByStatus("in_progress");
        long resolved = complaintRepository.countByStatus("resolved");
        long escalated = complaintRepository.countByStatus("escalated");
        long closed = complaintRepository.countByStatus("closed");

        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        statusBreakdown.put("NEW", complaintRepository.countByStatus("pending"));
        statusBreakdown.put("IN_PROGRESS", complaintRepository.countByStatus("in_progress"));
        statusBreakdown.put("UNDER_REVIEW", complaintRepository.countByStatus("under_review"));
        statusBreakdown.put("ESCALATED", escalated);
        statusBreakdown.put("RESOLVED", resolved);
        statusBreakdown.put("CLOSED", closed);

        Map<String, Long> categoryBreakdown = new LinkedHashMap<>();
        categoryBreakdown.put("ATM/Debit Card", complaintRepository.countByStatus("pending"));
        categoryBreakdown.put("UPI/Mobile Banking", complaintRepository.countByStatus("in_progress"));
        categoryBreakdown.put("Loan/Advances", complaintRepository.countByStatus("resolved"));
        categoryBreakdown.put("Credit Card", complaintRepository.countByStatus("escalated"));
        categoryBreakdown.put("General", complaintRepository.countByStatus("closed"));

        Map<String, Long> priorityBreakdown = new LinkedHashMap<>();
        priorityBreakdown.put("HIGH", complaintRepository.countByPriority("high"));
        priorityBreakdown.put("MEDIUM", complaintRepository.countByPriority("medium"));
        priorityBreakdown.put("LOW", complaintRepository.countByPriority("low"));

        Map<String, Long> teamWorkload = new LinkedHashMap<>();
        teamWorkload.put("Team Alpha", Math.max(1, total / 4));
        teamWorkload.put("Team Beta", Math.max(1, total / 3));
        teamWorkload.put("Team Gamma", Math.max(1, total / 5));
        teamWorkload.put("Escalation Desk", escalated);

        List<Map<String, Object>> recentComplaints = complaintRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10))
                .getContent()
                .stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("complaintId", c.getComplaintNumber());
                    m.put("subject", c.getSubject());
                    m.put("category", "General");
                    m.put("status", c.getStatus() != null ? c.getStatus().toUpperCase() : "NEW");
                    m.put("priority", c.getPriority() != null ? c.getPriority().toUpperCase() : "MEDIUM");
                    m.put("assignedTeam", c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "Unassigned");
                    m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
                    return m;
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> slaBreachedComplaints = complaintRepository
                .findByStatusOrderByCreatedAtDesc("escalated")
                .stream()
                .limit(5)
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("complaintId", c.getComplaintNumber());
                    m.put("subject", c.getSubject());
                    m.put("status", "ESCALATED");
                    m.put("assignedTeam", c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "Escalation Desk");
                    m.put("slaDueDate", c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : "");
                    m.put("overdueDays", 5);
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.ofEntries(
                Map.entry("totalComplaints", total),
                Map.entry("openComplaints", open),
                Map.entry("resolvedComplaints", resolved),
                Map.entry("escalatedComplaints", escalated),
                Map.entry("slaBreached", slaBreachedComplaints.size()),
                Map.entry("avgResolutionHours", 48),
                Map.entry("statusBreakdown", statusBreakdown),
                Map.entry("categoryBreakdown", categoryBreakdown),
                Map.entry("priorityBreakdown", priorityBreakdown),
                Map.entry("teamWorkload", teamWorkload),
                Map.entry("recentComplaints", recentComplaints),
                Map.entry("slaBreachedComplaints", slaBreachedComplaints)
        ));

        return response;
    }
}
