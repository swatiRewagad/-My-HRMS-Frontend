package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "OFFICE_CODE_MASTER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficeCodeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 10)
    private String officeType;

    @Column(nullable = false, length = 100)
    private String officeName;

    @Column(nullable = false, unique = true, length = 10)
    private String officeCode;

    @Column(nullable = false)
    @Builder.Default
    private Integer counter = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer threshold = 2;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
