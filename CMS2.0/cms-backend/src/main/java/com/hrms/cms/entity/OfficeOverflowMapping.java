package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "OFFICE_OVERFLOW_MAPPING")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficeOverflowMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "OFFICE_CODE", nullable = false, unique = true, length = 10)
    private String officeCode;

    @Column(name = "OFFICE_NAME", nullable = false, length = 100)
    private String officeName;

    @Column(name = "PRIORITY1_OFFICE_NAME", nullable = false, length = 100)
    private String priority1OfficeName;

    @Column(name = "PRIORITY2_OFFICE_NAME", nullable = false, length = 100)
    private String priority2OfficeName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
