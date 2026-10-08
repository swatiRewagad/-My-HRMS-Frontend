package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ACCOUNT_TYPE_MASTER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountTypeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "\"value\"", nullable = false, length = 50, unique = true)
    private String value;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
