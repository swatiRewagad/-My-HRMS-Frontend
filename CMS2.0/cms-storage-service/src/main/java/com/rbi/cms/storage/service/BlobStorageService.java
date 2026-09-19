package com.rbi.cms.storage.service;

import com.rbi.cms.common.exception.CmsException;
import com.rbi.cms.storage.config.BlobStorageProperties;
import com.rbi.cms.storage.dto.StorageBlobMetadata;
import com.rbi.cms.storage.dto.StorageBlobResponse;
import com.rbi.cms.storage.entity.StorageBlob;
import com.rbi.cms.storage.repository.StorageBlobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlobStorageService {

    private final StorageBlobRepository repository;
    private final BlobStorageProperties properties;

    @Transactional
    public StorageBlobResponse store(String bucket, String tenantId, MultipartFile file) {
        String fileName = file.getOriginalFilename();

        if (!properties.isAllowedType(fileName)) {
            throw new CmsException(
                    "File type not allowed. Allowed types: " + properties.getAllowedTypes(),
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new CmsException(
                    "File exceeds maximum allowed size of " + (properties.getMaxFileSize() / 1048576) + "MB",
                    HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        if (file.isEmpty()) {
            throw new CmsException("Uploaded file is empty", HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new CmsException("Unable to read uploaded file", HttpStatus.BAD_REQUEST, "UNREADABLE_UPLOAD");
        }

        StorageBlob blob = repository.save(StorageBlob.builder()
                .objectId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .bucket(bucket)
                .fileName(fileName)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .sizeBytes((long) data.length)
                .checksum(checksum(data))
                .storageData(data)
                .build());

        log.info("Stored blob objectId={} bucket={} name={} bytes={}",
                blob.getObjectId(), bucket, fileName, data.length);

        return toResponse(blob);
    }

    @Transactional(readOnly = true)
    public StorageBlob fetch(String objectId) {
        return repository.findByObjectId(objectId)
                .orElseThrow(() -> notFound(objectId));
    }

    @Transactional(readOnly = true)
    public StorageBlobMetadata metadata(String objectId) {
        return repository.findMetadataByObjectId(objectId)
                .orElseThrow(() -> notFound(objectId));
    }

    @Transactional
    public void delete(String objectId) {
        if (repository.deleteByObjectId(objectId) == 0) {
            throw notFound(objectId);
        }
        log.info("Deleted blob objectId={}", objectId);
    }

    private static CmsException notFound(String objectId) {
        return new CmsException("No stored object found for id " + objectId,
                HttpStatus.NOT_FOUND, "OBJECT_NOT_FOUND");
    }

    private static StorageBlobResponse toResponse(StorageBlob blob) {
        return new StorageBlobResponse(
                blob.getObjectId(), blob.getTenantId(), blob.getBucket(), blob.getFileName(),
                blob.getContentType(), blob.getSizeBytes(), blob.getChecksum(), blob.getCreatedAt());
    }

    private static String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
