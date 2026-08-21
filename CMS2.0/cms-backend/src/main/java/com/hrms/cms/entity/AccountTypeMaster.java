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

    @Column(nullable = false, length = 50, unique = true)
    private String value;

    private boolean active;

    private int sortOrder;
}
