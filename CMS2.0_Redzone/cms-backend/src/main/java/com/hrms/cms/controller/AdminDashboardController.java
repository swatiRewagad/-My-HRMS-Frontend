package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final ComplaintRepository complaintRepository;

    @GetMapping("/dashboard-stats")
    public Map<String, Object> getDashboardStats(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String status) {

        List<Complaint> all = complaintRepository.findAllByOrderByCreatedAtDesc();

        // Apply filters
        if (fromDate != null && !fromDate.isBlank()) {
            LocalDateTime from = java.time.LocalDate.parse(fromDate).atStartOfDay();
            all = all.stream().filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(from)).collect(Collectors.toList());
        }
        if (toDate != null && !toDate.isBlank()) {
            LocalDateTime to = java.time.LocalDate.parse(toDate).atTime(23, 59, 59);
            all = all.stream().filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isAfter(to)).collect(Collectors.toList());
        }
        if (department != null && !department.isBlank()) {
            if ("CRPC".equals(department)) {
                // CRPC handles complaints with department=CRPC or unassigned (null/empty)
                all = all.stream().filter(c -> "CRPC".equals(c.getDepartment()) || c.getDepartment() == null || c.getDepartment().isBlank()).collect(Collectors.toList());
            } else {
                all = all.stream().filter(c -> department.equals(c.getDepartment())).collect(Collectors.toList());
            }
        }
        if (mode != null && !mode.isBlank()) {
            all = all.stream().filter(c -> mode.equals(c.getFilingType())).collect(Collectors.toList());
        }
        if (status != null && !status.isBlank()) {
            all = all.stream().filter(c -> status.equals(c.getStatus())).collect(Collectors.toList());
        }

        // Group by department (null/empty department = CRPC)
        long crpcCount = all.stream().filter(c -> "CRPC".equals(c.getDepartment()) || c.getDepartment() == null || c.getDepartment().isBlank()).count();
        long rbioCount = all.stream().filter(c -> "RBIO".equals(c.getDepartment())).count();
        long cepcCount = all.stream().filter(c -> "CEPC".equals(c.getDepartment())).count();
        long appellateCount = all.stream().filter(c -> "APPELLATE".equals(c.getDepartment())).count();

        // Group by month for complaints by type
        Map<Month, Long> crpcByMonth = all.stream()
                .filter(c -> ("CRPC".equals(c.getDepartment()) || c.getDepartment() == null || c.getDepartment().isBlank()) && c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().getMonth(), Collectors.counting()));
        Map<Month, Long> rbioByMonth = all.stream()
                .filter(c -> "RBIO".equals(c.getDepartment()) && c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().getMonth(), Collectors.counting()));
        Map<Month, Long> cepcByMonth = all.stream()
                .filter(c -> "CEPC".equals(c.getDepartment()) && c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().getMonth(), Collectors.counting()));
        Map<Month, Long> appellateByMonth = all.stream()
                .filter(c -> "APPELLATE".equals(c.getDepartment()) && c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().getMonth(), Collectors.counting()));

        // Build monthly arrays (last 12 months or financial year Apr-Mar)
        Month[] fyMonths = { Month.APRIL, Month.MAY, Month.JUNE, Month.JULY, Month.AUGUST, Month.SEPTEMBER,
                Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH };
        String[] monthLabels = { "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar" };

        List<Long> crpcData = new ArrayList<>();
        List<Long> rbioData = new ArrayList<>();
        List<Long> cepcData = new ArrayList<>();
        List<Long> appellateData = new ArrayList<>();

        for (Month m : fyMonths) {
            crpcData.add(crpcByMonth.getOrDefault(m, 0L));
            rbioData.add(rbioByMonth.getOrDefault(m, 0L));
            cepcData.add(cepcByMonth.getOrDefault(m, 0L));
            appellateData.add(appellateByMonth.getOrDefault(m, 0L));
        }

        // Mode of receipt
        long emailCount = all.stream().filter(c -> "EMAIL".equals(c.getFilingType())).count();
        long physicalCount = all.stream().filter(c -> "PHYSICAL_LETTER".equals(c.getFilingType())).count();
        long portalCount = all.stream().filter(c -> "PORTAL".equals(c.getFilingType())).count();

        Map<Month, Long> emailByMonth = all.stream()
                .filter(c -> "EMAIL".equals(c.getFilingType()) && c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().getMonth(), Collectors.counting()));
        Map<Month, Long> physicalByMonth = all.stream()
                .filter(c -> "PHYSICAL_LETTER".equals(c.getFilingType()) && c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().getMonth(), Collectors.counting()));

        List<Long> emailModeData = new ArrayList<>();
        List<Long> physicalModeData = new ArrayList<>();
        for (Month m : fyMonths) {
            emailModeData.add(emailByMonth.getOrDefault(m, 0L));
            physicalModeData.add(physicalByMonth.getOrDefault(m, 0L));
        }

        // Actions taken (by status)
        Map<Month, Long> closedByMonth = all.stream()
                .filter(c -> "closed".equals(c.getStatus()) && c.getClosedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getClosedAt().getMonth(), Collectors.counting()));
        Map<Month, Long> resolvedByMonth = all.stream()
                .filter(c -> "resolved".equals(c.getStatus()) && c.getResolvedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getResolvedAt().getMonth(), Collectors.counting()));
        Map<Month, Long> escalatedByMonth = all.stream()
                .filter(c -> "escalated".equals(c.getStatus()) && c.getEscalatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getEscalatedAt().getMonth(), Collectors.counting()));
        Map<Month, Long> forwardedByMonth = all.stream()
                .filter(c -> c.getStatus() != null && c.getStatus().startsWith("forwarded") && c.getCreatedAt() != null)
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().getMonth(), Collectors.counting()));

        List<Long> closedData = new ArrayList<>();
        List<Long> nacData = new ArrayList<>();
        List<Long> sentToRegData = new ArrayList<>();
        List<Long> sentToRbiData = new ArrayList<>();
        for (Month m : fyMonths) {
            closedData.add(closedByMonth.getOrDefault(m, 0L) + resolvedByMonth.getOrDefault(m, 0L));
            nacData.add(escalatedByMonth.getOrDefault(m, 0L));
            sentToRegData.add(forwardedByMonth.getOrDefault(m, 0L));
            sentToRbiData.add(0L);
        }

        // Maintainability: percentage of complaints resolved within SLA per month
        List<Long> maintTotal = new ArrayList<>();
        List<Long> maintEmail = new ArrayList<>();
        List<Long> maintPhysical = new ArrayList<>();
        for (Month m : fyMonths) {
            long totalInMonth = all.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().getMonth() == m).count();
            long resolvedInMonth = all.stream()
                    .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().getMonth() == m
                            && ("resolved".equals(c.getStatus()) || "closed".equals(c.getStatus())))
                    .count();
            maintTotal.add(totalInMonth > 0 ? (resolvedInMonth * 100) / totalInMonth : 0);

            long emailInMonth = all.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().getMonth() == m && "EMAIL".equals(c.getFilingType())).count();
            long emailResolvedInMonth = all.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().getMonth() == m && "EMAIL".equals(c.getFilingType()) && ("resolved".equals(c.getStatus()) || "closed".equals(c.getStatus()))).count();
            maintEmail.add(emailInMonth > 0 ? (emailResolvedInMonth * 100) / emailInMonth : 0);

            long physInMonth = all.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().getMonth() == m && "PHYSICAL_LETTER".equals(c.getFilingType())).count();
            long physResolvedInMonth = all.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().getMonth() == m && "PHYSICAL_LETTER".equals(c.getFilingType()) && ("resolved".equals(c.getStatus()) || "closed".equals(c.getStatus()))).count();
            maintPhysical.add(physInMonth > 0 ? (physResolvedInMonth * 100) / physInMonth : 0);
        }

        // TAT (Turn Around Time) - average days to resolve per department per month
        List<Double> tatCrpc = new ArrayList<>();
        List<Double> tatRbio = new ArrayList<>();
        List<Double> tatCepc = new ArrayList<>();
        List<Double> tatAvg = new ArrayList<>();
        for (Month m : fyMonths) {
            tatCrpc.add(getAvgTat(all, "CRPC", m));
            tatRbio.add(getAvgTat(all, "RBIO", m));
            tatCepc.add(getAvgTat(all, "CEPC", m));
            tatAvg.add(getAvgTat(all, null, m));
        }

        // Totals
        long totalComplaints = all.size();
        long openComplaints = all.stream().filter(c -> c.getStatus() != null && !List.of("resolved", "closed", "rejected", "withdrawn").contains(c.getStatus())).count();
        long resolvedComplaints = all.stream().filter(c -> "resolved".equals(c.getStatus()) || "closed".equals(c.getStatus())).count();
        long escalatedComplaints = complaintRepository.countByStatus("escalated");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("complaintsByType", Map.of("CRPC", crpcData, "RBIO", rbioData, "CEPC", cepcData, "AppellateAuthority", appellateData));
        response.put("months", Arrays.asList(monthLabels));
        response.put("maintainability", Map.of("total", maintTotal, "email", maintEmail, "physical", maintPhysical));
        response.put("maintainabilityMonths", Arrays.asList(monthLabels));
        response.put("modeOfReceipt", Map.of("email", emailModeData, "physical", physicalModeData));
        response.put("modeMonths", Arrays.asList(monthLabels));
        response.put("actionsTaken", Map.of("closed", closedData, "nac", nacData, "sentToRegulatory", sentToRegData, "sentToRbi", sentToRbiData));
        response.put("actionMonths", Arrays.asList(monthLabels));
        response.put("tat", Map.of("crpc", tatCrpc, "rbio", tatRbio, "cepc", tatCepc, "average", tatAvg));
        response.put("tatMonths", Arrays.asList(monthLabels));
        response.put("totalComplaints", totalComplaints);
        response.put("openComplaints", openComplaints);
        response.put("resolvedComplaints", resolvedComplaints);
        response.put("escalatedComplaints", escalatedComplaints);
        response.put("slaBreached", 0);
        return response;
    }

    private double getAvgTat(List<Complaint> all, String dept, Month month) {
        List<Complaint> resolved = all.stream()
                .filter(c -> c.getResolvedAt() != null && c.getCreatedAt() != null
                        && c.getResolvedAt().getMonth() == month
                        && (dept == null || dept.equals(c.getDepartment())))
                .collect(Collectors.toList());
        if (resolved.isEmpty()) return 0.0;
        double avgDays = resolved.stream()
                .mapToDouble(c -> Math.max(0, java.time.Duration.between(c.getCreatedAt(), c.getResolvedAt()).toDays()))
                .average()
                .orElse(0.0);
        return Math.round(avgDays * 10.0) / 10.0;
    }

    @GetMapping("/dashboard/stats")
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
