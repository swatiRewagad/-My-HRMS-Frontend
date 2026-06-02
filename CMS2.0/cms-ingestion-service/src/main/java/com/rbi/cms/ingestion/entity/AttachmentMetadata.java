package com.rbi.cms.ingestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ATTACHMENT_METADATA")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "attachment_seq")
    @SequenceGenerator(name = "attachment_seq", sequenceName = "ATTACHMENT_METADATA_SEQ", allocationSize = 1)
    @Column(name = "ATTACHMENT_ID")
    private Long attachmentId;

    @Column(name = "COMPLAINT_ID", nullable = false, length = 30)
    private String complaintId;

    @Column(name = "FILE_NAME", nullable = false, length = 255)
    private String fileName;

    @Column(name = "CONTENT_TYPE", nullable = false, length = 100)
    private String contentType;

    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    @Column(name = "STORAGE_PATH", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "CHECKSUM", length = 64)
    private String checksum;

    @Column(name = "UPLOADED_BY", length = 100)
    private String uploadedBy;

    @Column(name = "UPLOADED_AT", nullable = false)
    private Instant uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = Instant.now();
    }
}
