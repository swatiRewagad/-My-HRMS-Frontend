package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "UPLOAD_LINKS", indexes = {
    @Index(name = "idx_upload_link_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_upload_link_token", columnList = "token", unique = true),
    @Index(name = "idx_upload_link_active", columnList = "active"),
    @Index(name = "idx_upload_link_expires", columnList = "expiresAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UploadLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(length = 200)
    private String complainantEmail;

    @Column(length = 20)
    private String complainantMobile;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean documentsSubmitted = false;

    private LocalDateTime documentsSubmittedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.sentAt == null) this.sentAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
