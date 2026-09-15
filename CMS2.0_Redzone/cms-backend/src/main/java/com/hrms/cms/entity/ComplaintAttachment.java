package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_ATTACHMENTS", indexes = {
    @Index(name = "idx_attachment_complaint", columnList = "COMPLAINT_ID")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_ID", nullable = false)
    private Long complaintId;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(nullable = false, length = 500)
    private String originalName;

    @Column(length = 100)
    private String contentType;

    private Long fileSize;

    @Column(length = 1000)
    private String storagePath;

    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
