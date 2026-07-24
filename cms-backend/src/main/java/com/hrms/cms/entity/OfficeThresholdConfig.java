package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "OFFICE_THRESHOLD_CONFIG")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficeThresholdConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String officeId; // RBIO-MUM, RBIO-DEL, CEPC, etc.

    @Column(nullable = false, length = 200)
    private String officeName;

    @Column(nullable = false, length = 20)
    private String department; // RBIO, CEPC, CEPD

    private int maxThreshold;

    private int currentCount;

    private int overflowSequenceOrder;

    @Column(length = 50)
    private String overflowTargetOffice;

    private boolean active;

    @Column(length = 100)
    private String updatedBy;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
