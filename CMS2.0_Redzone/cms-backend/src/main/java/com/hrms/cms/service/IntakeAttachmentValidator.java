package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * NFR-006 enforcement for intake endpoints (inbound email and scanned physical letters).
 *
 * Delegates per-file checks (magic bytes, name safety) to FileUploadValidator, which is owned by
 * another workstream, and adds the aggregate limits it does not cover. Wrapping rather than editing
 * that class keeps this session's changes out of a file being modified concurrently, and gives the
 * intake path one place to enforce the limits even if the delegate's API shifts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntakeAttachmentValidator {

    private final FileUploadValidator fileUploadValidator;

    @Value("${cms.attachments.intake.max-file-size-bytes:2097152}")
    private long maxFileSizeBytes;

    @Value("${cms.attachments.intake.max-total-size-bytes:26214400}")
    private long maxTotalSizeBytes;

    @Value("${cms.attachments.intake.max-file-count:10}")
    private int maxFileCount;

    /** Carries a translation key so the rejection can be shown in the citizen's locale. */
    public static class IntakeUploadRejected extends RuntimeException {
        private final String messageKey;

        public IntakeUploadRejected(String messageKey, String message) {
            super(message);
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    public void validateSingle(MultipartFile file) {
        validateBatch(List.of(file));
    }

    /**
     * Enforces count, per-file size and total size, then the delegate's content checks.
     * Order matters: cheap aggregate rejections happen before any byte is read.
     */
    public void validateBatch(List<MultipartFile> files) {
        List<MultipartFile> present = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (present.isEmpty()) {
            return;
        }

        if (present.size() > maxFileCount) {
            throw new IntakeUploadRejected("intake.attachment_too_many",
                    "At most " + maxFileCount + " files may be attached");
        }

        long total = 0;
        for (MultipartFile file : present) {
            if (file.getSize() > maxFileSizeBytes) {
                throw new IntakeUploadRejected("intake.attachment_too_large",
                        "Each file must be " + (maxFileSizeBytes / (1024 * 1024)) + "MB or smaller");
            }
            total += file.getSize();
        }

        if (total > maxTotalSizeBytes) {
            throw new IntakeUploadRejected("intake.attachment_total_too_large",
                    "Attachments must total " + (maxTotalSizeBytes / (1024 * 1024)) + "MB or less");
        }

        for (MultipartFile file : present) {
            try {
                fileUploadValidator.validate(file);
            } catch (FileUploadValidator.InvalidUploadException e) {
                // Client-declared content type is never trusted; this is the magic-byte verdict.
                throw new IntakeUploadRejected("intake.attachment_rejected", e.getMessage());
            }
        }
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public long getMaxTotalSizeBytes() {
        return maxTotalSizeBytes;
    }

    public int getMaxFileCount() {
        return maxFileCount;
    }
}
