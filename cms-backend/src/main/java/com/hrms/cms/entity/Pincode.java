package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "PINCODES", indexes = {
    @Index(name = "idx_pincode_code", columnList = "pincode"),
    @Index(name = "idx_pincode_state", columnList = "state"),
    @Index(name = "idx_pincode_district", columnList = "district")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pincode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(nullable = false, length = 100)
    private String officeName;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(length = 50)
    private String region;

    @Column(length = 50)
    private String division;

    @Column(length = 20)
    private String officeType;
}
