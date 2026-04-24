package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CHUNK_UPLOADS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChunkUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String uploadId;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(length = 100)
    private String contentType;

    private Long totalSize;
    private Integer totalChunks;
    private Integer chunksReceived;

    @Column(name = "FOLDER_ID", nullable = false)
    private Long folderId;

    @Column(name = "UPLOADED_BY", nullable = false)
    private Long uploadedBy;

    @Column(length = 20)
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = "uploading";
        if (this.chunksReceived == null) this.chunksReceived = 0;
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
