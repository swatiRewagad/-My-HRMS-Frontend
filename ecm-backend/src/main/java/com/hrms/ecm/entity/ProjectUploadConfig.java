package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "PROJECT_UPLOAD_CONFIGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectUploadConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PROJECT_ID", nullable = false, unique = true)
    private Long projectId;

    @Column(nullable = false)
    private Long maxFileSizeBytes;

    @Column(nullable = false)
    private Long totalAllocatedStorageBytes;

    @Column(nullable = false, length = 500)
    private String allowedContentTypes;

    @Column(nullable = false, length = 1000)
    private String uploadBasePath;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
