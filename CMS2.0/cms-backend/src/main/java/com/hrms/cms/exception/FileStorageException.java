package com.hrms.cms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Attachment failure carrying the HTTP status and a stable machine-readable code, so callers can
 * distinguish a rejected file type from an oversized file or a missing attachment.
 */
@Getter
public class FileStorageException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public FileStorageException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static FileStorageException unsupportedType(String allowedTypes) {
        return new FileStorageException(
                "File type not allowed. Allowed types: " + allowedTypes,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
    }

    public static FileStorageException fileTooLarge(long maxFileSize) {
        return new FileStorageException(
                "File exceeds maximum size of " + (maxFileSize / 1048576) + "MB",
                HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
    }

    public static FileStorageException attachmentLimitReached(int maxFiles) {
        return new FileStorageException(
                "Max files per complaint reached (" + maxFiles + ")",
                HttpStatus.CONFLICT, "ATTACHMENT_LIMIT_REACHED");
    }

    public static FileStorageException attachmentNotFound(Long attachmentId) {
        return new FileStorageException(
                "Attachment not found: " + attachmentId,
                HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND");
    }

    public static FileStorageException storageUnavailable(String detail) {
        return new FileStorageException(
                "Storage service request failed: " + detail,
                HttpStatus.BAD_GATEWAY, "STORAGE_SERVICE_UNAVAILABLE");
    }
}
