package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "BANK_BRANCHES", indexes = {
    @Index(name = "idx_bank_branches_bank_pincode", columnList = "bankId, pincode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bankId;

    @Column(nullable = false, length = 50)
    private String bankCode;

    @Column(nullable = false, length = 20, unique = true)
    private String ifsc;

    @Column(nullable = false, length = 200)
    private String branchName;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String state;

    @Column(nullable = false, length = 6)
    private String pincode;
}
