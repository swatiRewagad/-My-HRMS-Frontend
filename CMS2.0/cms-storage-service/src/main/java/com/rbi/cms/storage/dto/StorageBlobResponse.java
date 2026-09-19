package com.rbi.cms.storage.dto;

import java.time.LocalDateTime;

/** Returned after a successful upload — {@code objectId} is the handle callers persist. */
public record StorageBlobResponse(
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
