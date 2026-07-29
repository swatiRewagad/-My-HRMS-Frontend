package com.hrms.cms.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class TatCalculationService {

    private final BusinessHoursService businessHoursService;

    private static final int DEFAULT_SLA_HOURS = 240; // 30 business days * 8 hours

    public TatCalculationService(BusinessHoursService businessHoursService) {
        this.businessHoursService = businessHoursService;
    }

    public TatResult calculateTat(LocalDateTime filedAt, LocalDateTime resolvedAt, int slaBusinessHours) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveEnd = resolvedAt != null ? resolvedAt : now;

        long elapsedBusinessHours = businessHoursService.calculateElapsedBusinessHours(filedAt, effectiveEnd);
        LocalDateTime dueDate = businessHoursService.calculateDueDate(filedAt, slaBusinessHours);
        long remainingHours = Math.max(0, slaBusinessHours - elapsedBusinessHours);

        boolean breached = elapsedBusinessHours > slaBusinessHours;
        double percentUsed = slaBusinessHours > 0 ? (double) elapsedBusinessHours / slaBusinessHours * 100 : 0;

        int businessDaysElapsed = (int) (elapsedBusinessHours / businessHoursService.getBusinessHoursPerDay());
        int businessDaysRemaining = (int) (remainingHours / businessHoursService.getBusinessHoursPerDay());

        TatResult result = new TatResult();
        result.setFiledAt(filedAt);
        result.setDueDate(dueDate);
        result.setResolvedAt(resolvedAt);
        result.setSlaBusinessHours(slaBusinessHours);
        result.setElapsedBusinessHours(elapsedBusinessHours);
        result.setRemainingBusinessHours(remainingHours);
        result.setBusinessDaysElapsed(businessDaysElapsed);
        result.setBusinessDaysRemaining(businessDaysRemaining);
        result.setPercentUsed(Math.min(percentUsed, 100));
        result.setBreached(breached);
        result.setStatus(determineStatus(percentUsed, breached, resolvedAt != null));

        return result;
    }

    public TatResult calculateTat(LocalDateTime filedAt, LocalDateTime resolvedAt) {
        return calculateTat(filedAt, resolvedAt, DEFAULT_SLA_HOURS);
    }

    private String determineStatus(double percentUsed, boolean breached, boolean resolved) {
        if (resolved && !breached) return "RESOLVED_WITHIN_SLA";
        if (resolved && breached) return "RESOLVED_BREACHED";
        if (breached) return "BREACHED";
        if (percentUsed >= 80) return "AT_RISK";
        if (percentUsed >= 50) return "IN_PROGRESS";
        return "ON_TRACK";
    }

    public static class TatResult {
        private LocalDateTime filedAt;
        private LocalDateTime dueDate;
        private LocalDateTime resolvedAt;
        private int slaBusinessHours;
        private long elapsedBusinessHours;
        private long remainingBusinessHours;
        private int businessDaysElapsed;
        private int businessDaysRemaining;
        private double percentUsed;
        private boolean breached;
        private String status;

        public Map<String, Object> toMap() {
            return Map.ofEntries(
                Map.entry("filedAt", filedAt != null ? filedAt.toString() : ""),
                Map.entry("dueDate", dueDate != null ? dueDate.toString() : ""),
                Map.entry("resolvedAt", resolvedAt != null ? resolvedAt.toString() : ""),
                Map.entry("slaBusinessHours", slaBusinessHours),
                Map.entry("elapsedBusinessHours", elapsedBusinessHours),
                Map.entry("remainingBusinessHours", remainingBusinessHours),
                Map.entry("businessDaysElapsed", businessDaysElapsed),
                Map.entry("businessDaysRemaining", businessDaysRemaining),
                Map.entry("percentUsed", Math.round(percentUsed * 10.0) / 10.0),
                Map.entry("breached", breached),
                Map.entry("status", status)
            );
        }

        public LocalDateTime getFiledAt() { return filedAt; }
        public void setFiledAt(LocalDateTime filedAt) { this.filedAt = filedAt; }
        public LocalDateTime getDueDate() { return dueDate; }
        public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
        public LocalDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
        public int getSlaBusinessHours() { return slaBusinessHours; }
        public void setSlaBusinessHours(int slaBusinessHours) { this.slaBusinessHours = slaBusinessHours; }
        public long getElapsedBusinessHours() { return elapsedBusinessHours; }
        public void setElapsedBusinessHours(long elapsedBusinessHours) { this.elapsedBusinessHours = elapsedBusinessHours; }
        public long getRemainingBusinessHours() { return remainingBusinessHours; }
        public void setRemainingBusinessHours(long remainingBusinessHours) { this.remainingBusinessHours = remainingBusinessHours; }
        public int getBusinessDaysElapsed() { return businessDaysElapsed; }
        public void setBusinessDaysElapsed(int businessDaysElapsed) { this.businessDaysElapsed = businessDaysElapsed; }
        public int getBusinessDaysRemaining() { return businessDaysRemaining; }
        public void setBusinessDaysRemaining(int businessDaysRemaining) { this.businessDaysRemaining = businessDaysRemaining; }
        public double getPercentUsed() { return percentUsed; }
        public void setPercentUsed(double percentUsed) { this.percentUsed = percentUsed; }
        public boolean isBreached() { return breached; }
        public void setBreached(boolean breached) { this.breached = breached; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
