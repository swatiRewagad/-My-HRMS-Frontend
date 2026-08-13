package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "OFFICE_GLOBAL_THRESHOLD_CONFIG")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficeGlobalThresholdConfig {

    @Id
    private Integer id;

    @Column(nullable = false)
    private Integer thresholdValue;

    @Column(length = 200)
    private String updatedBy;

    private LocalDateTime updatedAt;
}
