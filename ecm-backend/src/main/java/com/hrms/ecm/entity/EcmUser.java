package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ECM_USERS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EcmUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(length = 200)
    private String email;

    @Column(length = 50)
    private String role;

    @Column(length = 10)
    private String initials;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}
