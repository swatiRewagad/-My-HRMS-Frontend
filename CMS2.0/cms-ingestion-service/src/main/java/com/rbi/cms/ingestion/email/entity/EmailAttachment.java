package com.rbi.cms.ingestion.email.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "EMAIL_ATTACHMENT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "DRAFT_ID", nullable = false, length = 30)
    private String draftId;

    @Column(name = "FILE_NAME", nullable = false, length = 500)
    private String fileName;

    @Column(name = "FILE_TYPE", length = 100)
    private String fileType;

    @Column(name = "FILE_SIZE")
    private Long fileSize;

    @Column(name = "STORAGE_PATH", length = 1000)
    private String storagePath;

    @Column(name = "OCR_TEXT", columnDefinition = "CLOB")
    private String ocrText;

    @Column(name = "OCR_CONFIDENCE")
    private Double ocrConfidence;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
