package com.hrms.cms.service;

import com.hrms.cms.repository.ComplaintRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardService {

    private final ComplaintRepository complaintRepository;
    private final TatCalculationService tatService;

    public DashboardService(ComplaintRepository complaintRepository, TatCalculationService tatService) {
        this.complaintRepository = complaintRepository;
        this.tatService = tatService;
    }

    @Cacheable(value = "dashboard", key = "'officer-summary'")
    public Map<String, Object> getOfficerSummary() {
        long total = complaintRepository.count();
        long pending = complaintRepository.countByStatus("pending");
        long inProgress = complaintRepository.countByStatus("in_progress");
        long resolved = complaintRepository.countByStatus("resolved");
        long escalated = complaintRepository.countByStatus("escalated");
        long closed = complaintRepository.countByStatus("closed");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalComplaints", total);
        summary.put("pending", pending);
        summary.put("inProgress", inProgress);
        summary.put("resolved", resolved);
        summary.put("escalated", escalated);
        summary.put("closed", closed);
        summary.put("resolutionRate", total > 0 ? Math.round((double)(resolved + closed) / total * 100) : 0);
        summary.put("cachedAt", LocalDateTime.now().toString());

        return summary;
    }

    @Cacheable(value = "dashboard", key = "'department-' + #department")
    public Map<String, Object> getDepartmentSummary(String department) {
        long total = complaintRepository.countByDepartment(department);
        long pending = complaintRepository.countByDepartmentAndStatus(department, "pending");
        long inProgress = complaintRepository.countByDepartmentAndStatus(department, "in_progress");
        long resolved = complaintRepository.countByDepartmentAndStatus(department, "resolved");
        long escalated = complaintRepository.countByDepartmentAndStatus(department, "escalated");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("department", department);
        summary.put("total", total);
        summary.put("pending", pending);
        summary.put("inProgress", inProgress);
        summary.put("resolved", resolved);
        summary.put("escalated", escalated);
        summary.put("cachedAt", LocalDateTime.now().toString());

        return summary;
    }

    @CacheEvict(value = "dashboard", allEntries = true)
    public void refreshCache() {
        // Cache eviction triggered
    }
}
