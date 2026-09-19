package com.rbi.cms.storage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "STORAGE_BLOBS", indexes = {
        @Index(name = "idx_storage_blob_object", columnList = "OBJECT_ID", unique = true),
        @Index(name = "idx_storage_blob_bucket", columnList = "BUCKET")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageBlob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optional multi-tenancy discriminator — not populated by the current single-tenant deployment. */
    @Column(name = "TENANT_ID", length = 64)
    private String tenantId;

    @Column(name = "OBJECT_ID", nullable = false, unique = true, length = 200)
    private String objectId;

    @Column(name = "BUCKET", length = 200)
    private String bucket;

    @Column(name = "FILE_NAME", nullable = false, length = 500)
    private String fileName;

    @Column(name = "CONTENT_TYPE", length = 150)
    private String contentType;

    @Column(name = "SIZE_BYTES", nullable = false)
    private Long sizeBytes;

    @Column(name = "CHECKSUM", length = 64)
    private String checksum;

    @Lob
    @Column(name = "STORAGE_DATA", nullable = false)
    private byte[] storageData;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
