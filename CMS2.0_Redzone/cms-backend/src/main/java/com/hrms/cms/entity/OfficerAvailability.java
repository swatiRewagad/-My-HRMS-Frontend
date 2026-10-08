package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "OFFICER_AVAILABILITY")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficerAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String userId;

    @Column(length = 50)
    private String role;

    @Column(length = 20)
    private String officeCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = true;
}
