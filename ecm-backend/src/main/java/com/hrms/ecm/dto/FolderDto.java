package com.hrms.ecm.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FolderDto {
    private Long id;
    private String name;
    private String visibility;
    private String description;
    private Long parentId;
    private String path;
    private String ownerName;
    private long fileCount;
    private List<FolderDto> children;
    private List<AccessEntryDto> accessList;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AccessEntryDto {
        private Long id;
        private Long userId;
        private String userName;
        private String userEmail;
        private String permission;
        private LocalDateTime grantedAt;
    }
}
