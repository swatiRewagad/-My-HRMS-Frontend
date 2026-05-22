package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CMS_USERS", indexes = {
    @Index(name = "idx_user_phone", columnList = "phone", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CmsUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(length = 10)
    private String pincode;

    @Column(length = 50)
    private String state;

    @Column(length = 100)
    private String district;

    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastLoginAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
