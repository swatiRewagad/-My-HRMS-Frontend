package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "FILE_SHARES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "FILE_ID", nullable = false)
    private Long fileId;

    @Column(name = "SHARED_BY", nullable = false)
    private Long sharedBy;

    @Column(name = "SHARED_WITH")
    private Long sharedWith;

    @Column(nullable = false, length = 20)
    private String shareType;

    @Column(unique = true, length = 100)
    private String shareToken;

    @Column(length = 20)
    private String permission;

    private LocalDateTime expiresAt;
    private LocalDateTime sharedAt;

    @PrePersist
    protected void onCreate() { this.sharedAt = LocalDateTime.now(); }
}
