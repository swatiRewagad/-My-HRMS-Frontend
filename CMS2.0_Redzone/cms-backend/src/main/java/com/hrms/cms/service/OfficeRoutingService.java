package com.hrms.cms.service;

import com.hrms.cms.entity.OfficeThresholdConfig;
import com.hrms.cms.repository.OfficeThresholdConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeRoutingService {

    private final OfficeThresholdConfigRepository thresholdRepo;

    @Transactional
    public Map<String, Object> routeToOffice(String targetOfficeId, boolean isVernacularOverride) {
        OfficeThresholdConfig target = thresholdRepo.findByOfficeId(targetOfficeId).orElse(null);
        if (target == null) {
            return Map.of("officeId", targetOfficeId, "status", "NOT_FOUND");
        }

        if (isVernacularOverride) {
            target.setCurrentCount(target.getCurrentCount() + 1);
            thresholdRepo.save(target);
            return Map.of("officeId", targetOfficeId, "status", "ASSIGNED_VERNACULAR_OVERRIDE",
                    "currentCount", target.getCurrentCount());
        }

        if (target.getCurrentCount() < target.getMaxThreshold()) {
            target.setCurrentCount(target.getCurrentCount() + 1);
            thresholdRepo.save(target);
            return Map.of("officeId", targetOfficeId, "status", "ASSIGNED",
                    "currentCount", target.getCurrentCount());
        }

        // Threshold met — find overflow target
        List<OfficeThresholdConfig> allOffices = thresholdRepo
                .findByDepartmentAndActiveTrueOrderByOverflowSequenceOrderAsc(target.getDepartment());

        for (OfficeThresholdConfig overflow : allOffices) {
            if (overflow.getOfficeId().equals(targetOfficeId)) continue;
            if (overflow.getCurrentCount() < overflow.getMaxThreshold()) {
                overflow.setCurrentCount(overflow.getCurrentCount() + 1);
                thresholdRepo.save(overflow);
                log.info("Threshold met for {}. Overflow to {}", targetOfficeId, overflow.getOfficeId());
                return Map.of("officeId", overflow.getOfficeId(), "status", "OVERFLOW_ASSIGNED",
                        "reason", "Primary office " + targetOfficeId + " at capacity",
                        "currentCount", overflow.getCurrentCount());
            }
        }

        // All offices at threshold — reset all counters
        resetAllCounters(target.getDepartment());
        target = thresholdRepo.findByOfficeId(targetOfficeId).orElse(target);
        target.setCurrentCount(1);
        thresholdRepo.save(target);
        log.info("All {} offices at threshold — reset counters. Assigned to {}", target.getDepartment(), targetOfficeId);
        return Map.of("officeId", targetOfficeId, "status", "ASSIGNED_AFTER_RESET",
                "currentCount", 1);
    }

    @Transactional
    public void decrementOffice(String officeId) {
        OfficeThresholdConfig config = thresholdRepo.findByOfficeId(officeId).orElse(null);
        if (config != null && config.getCurrentCount() > 0) {
            config.setCurrentCount(config.getCurrentCount() - 1);
            thresholdRepo.save(config);
        }
    }

    @Transactional
    public void incrementOffice(String officeId) {
        OfficeThresholdConfig config = thresholdRepo.findByOfficeId(officeId).orElse(null);
        if (config != null) {
            config.setCurrentCount(config.getCurrentCount() + 1);
            thresholdRepo.save(config);
        }
    }

    @Transactional
    public void resetAllCounters(String department) {
        List<OfficeThresholdConfig> offices = thresholdRepo
                .findByDepartmentAndActiveTrueOrderByOverflowSequenceOrderAsc(department);
        for (OfficeThresholdConfig o : offices) {
            o.setCurrentCount(0);
            thresholdRepo.save(o);
        }
        log.info("Reset all counters for department: {}", department);
    }

    @Transactional
    public void updateThreshold(String officeId, int newThreshold, String updatedBy) {
        OfficeThresholdConfig config = thresholdRepo.findByOfficeId(officeId)
                .orElseThrow(() -> new IllegalArgumentException("Office not found: " + officeId));
        config.setMaxThreshold(newThreshold);
        config.setUpdatedBy(updatedBy);
        thresholdRepo.save(config);
    }

    public List<OfficeThresholdConfig> getAllOfficeConfigs() {
        return thresholdRepo.findByActiveTrueOrderByOverflowSequenceOrderAsc();
    }
}
