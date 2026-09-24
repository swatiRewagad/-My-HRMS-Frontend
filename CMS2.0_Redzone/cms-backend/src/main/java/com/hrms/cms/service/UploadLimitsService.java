package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The single source of truth for attachment size limits (13.1).
 *
 * <h2>Why this exists</h2>
 * The ruling is that CONFIGURATION is authoritative and the number itself is expected to move; it
 * currently stands at 2 MB per file and 25 MB per complaint. What matters is that ONE place decides.
 * The limit used to be compiled into several places that did not agree — FileStorageConfig,
 * application.yml, IntakeAttachmentValidator's own {@code @Value}, and {@code maxFileSizeMB} in all
 * three Angular environment files — while the citizen-facing hint seeded in ten locales quoted a
 * different figure again. The UI therefore contradicted enforcement in both directions, and changing
 * the limit meant editing several files and cutting a release.
 *
 * <p>Reading from {@code SYSTEM_CONFIG} makes the limit changeable at runtime by an administrator, and
 * lets the browser ask the server what the limit is instead of shipping its own copy. The constants
 * below are the fallback for a database that has no row yet, and must equal the yml defaults.
 *
 * <p>Every enforcement path must come through here. {@code FileUploadValidator} and
 * {@code FileStorageService} originally read FileStorageConfig instead, so {@code /api/files/upload}
 * — the path every citizen attachment takes — silently ignored the configured limit.
 *
 * <p>Spring's {@code max-file-size: 50MB} servlet cap is deliberately left alone: it is the outer
 * bound that stops a malicious multipart body from being buffered at all, not the product rule. It
 * must stay ABOVE this limit, or a rejection would surface as a container error rather than a
 * translated message.
 */
@Service
@RequiredArgsConstructor
public class UploadLimitsService {

    public static final String KEY_MAX_FILE_BYTES = "cms.attachments.max_file_size_bytes";
    public static final String KEY_MAX_TOTAL_BYTES = "cms.attachments.max_total_size_bytes";
    public static final String KEY_MAX_FILE_COUNT = "cms.attachments.max_file_count";

    private static final long DEFAULT_MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final long DEFAULT_MAX_TOTAL_BYTES = 25L * 1024 * 1024;
    private static final int DEFAULT_MAX_FILE_COUNT = 10;

    private final SystemConfigService systemConfig;

    public long maxFileSizeBytes() {
        return systemConfig.getLong(KEY_MAX_FILE_BYTES, DEFAULT_MAX_FILE_BYTES);
    }

    public long maxTotalSizeBytes() {
        return systemConfig.getLong(KEY_MAX_TOTAL_BYTES, DEFAULT_MAX_TOTAL_BYTES);
    }

    public int maxFileCount() {
        return systemConfig.getInt(KEY_MAX_FILE_COUNT, DEFAULT_MAX_FILE_COUNT);
    }

    /**
     * Whole megabytes, for display and for the {{size}} placeholder in the upload hints.
     *
     * <p>Rounds DOWN deliberately. Telling a citizen "5 MB" when 5.4 MB would be accepted is harmless;
     * telling them "6 MB" when the server rejects at 5.4 MB is the contradiction this class removes.
     */
    public long maxFileSizeMb() {
        return maxFileSizeBytes() / (1024 * 1024);
    }

    public long maxTotalSizeMb() {
        return maxTotalSizeBytes() / (1024 * 1024);
    }
}
