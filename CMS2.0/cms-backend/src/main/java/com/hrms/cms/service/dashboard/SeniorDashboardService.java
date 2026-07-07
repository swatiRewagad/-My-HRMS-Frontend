package com.hrms.cms.service.dashboard;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.service.TatCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeniorDashboardService {

    private final ComplaintRepository complaintRepo;
    private final ComplaintCategoryRepository categoryRepo;
    private final TatCalculationService tatService;

    private Map<Long, String> categoryNameCache = null;

    private String getCategoryName(Long categoryId) {
        if (categoryId == null) return "Uncategorized";
        if (categoryNameCache == null) {
            categoryNameCache = new HashMap<>();
            try {
                categoryRepo.findAll().forEach(c -> categoryNameCache.put(c.getId(), c.getName()));
            } catch (Exception e) {
                log.debug("Could not load category names: {}", e.getMessage());
            }
        }
        return categoryNameCache.getOrDefault(categoryId, "Category-" + categoryId);
    }

    @Cacheable(value = "analytics-summary", key = "'senior-pipeline'")
    public Map<String, Object> getPipelineSummary() {
        List<Complaint> all = complaintRepo.findAll();

        long total = all.size();
        long pending = all.stream().filter(c -> "pending".equals(c.getStatus())).count();
        long inProgress = all.stream().filter(c -> "in_progress".equals(c.getStatus())).count();
        long resolved = all.stream().filter(c -> "resolved".equals(c.getStatus())).count();
        long escalated = all.stream().filter(c -> "escalated".equals(c.getStatus())).count();
        long closed = all.stream().filter(c -> "closed".equals(c.getStatus())).count();

        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("total", total);
        pipeline.put("pending", pending);
        pipeline.put("inProgress", inProgress);
        pipeline.put("resolved", resolved);
        pipeline.put("escalated", escalated);
        pipeline.put("closed", closed);
        pipeline.put("activeBacklog", pending + inProgress + escalated);
        pipeline.put("resolutionRate", total > 0 ? Math.round((double)(resolved + closed) / total * 100) : 0);
        pipeline.put("computedAt", LocalDateTime.now().toString());
        return pipeline;
    }

    @Cacheable(value = "analytics-summary", key = "'senior-tat'")
    public Map<String, Object> getTatAnalytics() {
        List<Complaint> active = complaintRepo.findAll().stream()
                .filter(c -> c.getCreatedAt() != null)
                .filter(c -> !"closed".equals(c.getStatus()) && !"withdrawn".equals(c.getStatus()))
                .toList();

        long breached = 0;
        long atRisk = 0;
        long onTrack = 0;
        long totalElapsedDays = 0;
        int count = 0;

        for (Complaint c : active) {
            TatCalculationService.TatResult tat = tatService.calculateTat(c.getCreatedAt(), c.getResolvedAt());
            if (tat.isBreached()) breached++;
            else if (tat.getPercentUsed() >= 80) atRisk++;
            else onTrack++;
            totalElapsedDays += tat.getBusinessDaysElapsed();
            count++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalActive", active.size());
        result.put("breached", breached);
        result.put("atRisk", atRisk);
        result.put("onTrack", onTrack);
        result.put("avgElapsedDays", count > 0 ? Math.round((double) totalElapsedDays / count * 10.0) / 10.0 : 0);
        result.put("breachRate", active.size() > 0 ? Math.round((double) breached / active.size() * 100) : 0);
        result.put("computedAt", LocalDateTime.now().toString());
        return result;
    }

    @Cacheable(value = "analytics-summary", key = "'senior-bottlenecks'")
    public Map<String, Object> getBottlenecks() {
        List<Complaint> all = complaintRepo.findAll();

        Map<String, Long> byDepartment = all.stream()
                .filter(c -> c.getDepartment() != null)
                .filter(c -> "pending".equals(c.getStatus()) || "in_progress".equals(c.getStatus()))
                .collect(Collectors.groupingBy(Complaint::getDepartment, Collectors.counting()));

        Map<String, Long> byCategory = all.stream()
                .filter(c -> c.getCategoryId() != null)
                .filter(c -> "pending".equals(c.getStatus()) || "in_progress".equals(c.getStatus()))
                .collect(Collectors.groupingBy(c -> getCategoryName(c.getCategoryId()), Collectors.counting()));

        Map<String, Long> byPriority = all.stream()
                .filter(c -> c.getPriority() != null)
                .collect(Collectors.groupingBy(Complaint::getPriority, Collectors.counting()));

        Map<String, Long> unassigned = all.stream()
                .filter(c -> c.getAssignedOfficer() == null || c.getAssignedOfficer().isBlank())
                .filter(c -> "pending".equals(c.getStatus()))
                .filter(c -> c.getDepartment() != null)
                .collect(Collectors.groupingBy(Complaint::getDepartment, Collectors.counting()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("backlogByDepartment", byDepartment);
        result.put("backlogByCategory", sortedTop(byCategory, 10));
        result.put("volumeByPriority", byPriority);
        result.put("unassignedByDepartment", unassigned);
        result.put("computedAt", LocalDateTime.now().toString());
        return result;
    }

    @Cacheable(value = "analytics-summary", key = "'senior-trend'")
    public List<Map<String, Object>> getWeeklyTrend() {
        List<Complaint> all = complaintRepo.findAll();
        LocalDateTime now = LocalDateTime.now();

        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int w = 11; w >= 0; w--) {
            LocalDateTime weekStart = now.minusWeeks(w + 1);
            LocalDateTime weekEnd = now.minusWeeks(w);

            long filed = all.stream()
                    .filter(c -> c.getCreatedAt() != null)
                    .filter(c -> !c.getCreatedAt().isBefore(weekStart) && c.getCreatedAt().isBefore(weekEnd))
                    .count();

            long resolved = all.stream()
                    .filter(c -> c.getResolvedAt() != null)
                    .filter(c -> !c.getResolvedAt().isBefore(weekStart) && c.getResolvedAt().isBefore(weekEnd))
                    .count();

            Map<String, Object> week = new LinkedHashMap<>();
            week.put("weekLabel", "W-" + w);
            week.put("filed", filed);
            week.put("resolved", resolved);
            week.put("net", filed - resolved);
            weeks.add(week);
        }
        return weeks;
    }

    @Cacheable(value = "analytics-summary", key = "'senior-entity-performance'")
    public Map<String, Object> getEntityPerformance() {
        List<Complaint> all = complaintRepo.findAll();

        Map<String, Long> volumeByEntity = all.stream()
                .filter(c -> c.getEntityCode() != null)
                .collect(Collectors.groupingBy(Complaint::getEntityCode, Collectors.counting()));

        Map<String, Long> breachByEntity = all.stream()
                .filter(c -> c.getEntityCode() != null && c.getCreatedAt() != null)
                .filter(c -> !"closed".equals(c.getStatus()) && !"withdrawn".equals(c.getStatus()))
                .filter(c -> tatService.calculateTat(c.getCreatedAt(), c.getResolvedAt()).isBreached())
                .collect(Collectors.groupingBy(Complaint::getEntityCode, Collectors.counting()));

        Map<String, Long> resolvedByEntity = all.stream()
                .filter(c -> c.getEntityCode() != null)
                .filter(c -> "resolved".equals(c.getStatus()) || "closed".equals(c.getStatus()))
                .collect(Collectors.groupingBy(Complaint::getEntityCode, Collectors.counting()));

        Map<String, Map<String, Long>> statusByDept = all.stream()
                .filter(c -> c.getDepartment() != null && c.getStatus() != null)
                .collect(Collectors.groupingBy(Complaint::getDepartment,
                        Collectors.groupingBy(Complaint::getStatus, Collectors.counting())));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("volumeByEntity", sortedTop(volumeByEntity, 10));
        result.put("breachByEntity", sortedTop(breachByEntity, 10));
        result.put("resolvedByEntity", sortedTop(resolvedByEntity, 10));
        result.put("statusByDepartment", statusByDept);
        return result;
    }

    private Map<String, Long> sortedTop(Map<String, Long> map, int limit) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }
}
