package com.rbi.cms.storage.dto;

import java.time.LocalDateTime;

/**
 * Blob attributes without the payload. Populated by a constructor-expression query so the
 * BLOB column is never read for metadata-only lookups.
 */
public record StorageBlobMetadata(
        String objectId,
        String tenantId,
        String bucket,
        String fileName,
        String contentType,
        Long sizeBytes,
        String checksum,
        LocalDateTime createdAt
) {
}
