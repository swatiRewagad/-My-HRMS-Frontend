package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "DEPARTMENT_ROUTING_MASTER", indexes = {
    @Index(name = "idx_drm_entity", columnList = "entityName")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DepartmentRoutingMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String entityName;

    @Column(nullable = false, length = 50)
    private String department; // RBIO, CEPC, CEPD

    @Column(length = 100)
    private String targetOffice;

    @Column(length = 30)
    private String registrationStatus; // ACTIVE, CANCELLED, SURRENDERED

    private boolean active;

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
