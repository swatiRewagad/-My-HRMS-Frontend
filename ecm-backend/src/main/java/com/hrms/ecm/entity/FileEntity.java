package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "FILES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false, length = 500)
    private String originalName;

    @Column(length = 100)
    private String contentType;

    private Long size;

    @Column(length = 1000)
    private String storagePath;

    @Column(name = "FOLDER_ID", nullable = false)
    private Long folderId;

    @Column(name = "UPLOADED_BY", nullable = false)
    private Long uploadedBy;

    @Column(length = 50)
    private String status;

    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "active";
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
