package com.rbi.cms.mailintake.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "INBOUND_EMAIL_ATTACHMENT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundEmailAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMAIL_ID", nullable = false)
    private Long emailId;

    /** Sanitised for display only — never used to build a filesystem path. The blob itself is
     *  stored by generated UUID via storeUri. */
    @Column(name = "FILENAME", length = 500)
    private String filename;

    @Column(name = "DECLARED_CONTENT_TYPE", length = 200)
    private String declaredContentType;

    /** Tika's actual sniffed type — deliberately kept separate from declared_content_type so a
     *  mismatch (e.g. .pdf that's actually an .exe) is visible, not silently trusted. */
    @Column(name = "DETECTED_CONTENT_TYPE", length = 200)
    private String detectedContentType;

    @Column(name = "SIZE_BYTES", nullable = false)
    private Long sizeBytes;

    @Column(name = "CONTENT_SHA256", length = 64)
    private String contentSha256;

    @Column(name = "STORE_URI", nullable = false, length = 500)
    private String storeUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "SCAN_STATUS", nullable = false, length = 20)
    @Builder.Default
    private AttachmentScanStatus scanStatus = AttachmentScanStatus.PENDING;

    @Column(name = "EXTRACTED_TEXT_URI", length = 500)
    private String extractedTextUri;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
