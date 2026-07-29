package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CATEGORY_MASTER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CategoryMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String categoryName;

    @Column(length = 200)
    private String subCategory;

    @Column(length = 20)
    private String schemeVersion; // RBIOS_2021, RBIOS_2026, BOTH

    @Column(length = 20)
    private String entityType; // RBIO, CEPC, ALL

    private boolean active;

    private int sortOrder;

    @Column(length = 100)
    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
