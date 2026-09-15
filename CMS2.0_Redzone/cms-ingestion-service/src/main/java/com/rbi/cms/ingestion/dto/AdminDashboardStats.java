package com.rbi.cms.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardStats {

    private long totalComplaints;
    private long openComplaints;
    private long resolvedComplaints;
    private long escalatedComplaints;
    private long slaBreached;
    private double avgResolutionHours;

    private Map<String, Long> statusBreakdown;
    private Map<String, Long> categoryBreakdown;
    private Map<String, Long> priorityBreakdown;
    private Map<String, Long> teamWorkload;

    private List<RecentComplaint> recentComplaints;
    private List<SlaBreachedComplaint> slaBreachedComplaints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentComplaint {
        private String complaintId;
        private String subject;
        private String category;
        private String status;
        private String priority;
        private String assignedTeam;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlaBreachedComplaint {
        private String complaintId;
        private String subject;
        private String status;
        private String assignedTeam;
        private String slaDueDate;
        private long overdueDays;
    }
}
