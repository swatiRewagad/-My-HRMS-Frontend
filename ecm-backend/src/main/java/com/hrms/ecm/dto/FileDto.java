package com.hrms.ecm.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileDto {
    private Long id;
    private String name;
    private String originalName;
    private String contentType;
    private long size;
    private String sizeFormatted;
    private String folderName;
    private String folderVisibility;
    private String uploadedByName;
    private String status;
    private LocalDateTime uploadedAt;
    private List<ShareDto> shares;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ShareDto {
        private Long id;
        private String shareType;
        private String sharedWithName;
        private String permission;
        private String shareToken;
        private LocalDateTime expiresAt;
        private LocalDateTime sharedAt;
    }
}
