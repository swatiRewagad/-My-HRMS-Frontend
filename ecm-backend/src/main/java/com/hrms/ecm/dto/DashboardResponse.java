package com.hrms.ecm.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardResponse {

    private StatsDto stats;
    private StorageDto storage;
    private List<FileTypeCountDto> fileTypeBreakdown;
    private List<RecentFileDto> recentUploads;
    private List<ActivityDto> recentActivity;
    private FolderSummaryDto folderSummary;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatsDto {
        private long totalFiles;
        private long totalFolders;
        private long publicFolders;
        private long privateFolders;
        private long totalShares;
        private long totalUsers;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StorageDto {
        private long usedBytes;
        private long capacityBytes;
        private String usedFormatted;
        private String capacityFormatted;
        private double usedPercent;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FileTypeCountDto {
        private String type;
        private long count;
        private long sizeBytes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecentFileDto {
        private Long id;
        private String name;
        private String contentType;
        private long size;
        private String folderName;
        private String folderVisibility;
        private String uploadedByName;
        private LocalDateTime uploadedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ActivityDto {
        private String action;
        private String entityType;
        private String entityName;
        private String userName;
        private LocalDateTime performedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class FolderSummaryDto {
        private long publicCount;
        private long privateCount;
        private long publicFiles;
        private long privateFiles;
    }
}
