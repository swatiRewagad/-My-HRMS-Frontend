package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "OMBUDSMAN_OFFICE_MASTER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OmbudsmanOfficeMaster {

    @Id
    private Integer id;

    @Column(nullable = false, length = 100)
    private String officeName;

    @Column(nullable = false, length = 1000)
    private String jurisdiction;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
