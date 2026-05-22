package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "DOCUMENT_TYPE_CONFIGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentTypeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PROJECT_ID", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String typeName;

    @Column(nullable = false, length = 50)
    private String typeCode;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean extractionEnabled = false;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "active";

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
