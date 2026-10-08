package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * The bytes of one {@link ComplaintAttachment}, held in the database rather than on a filesystem.
 *
 * <p><b>Why a second table instead of a FILE_DATA column on ComplaintAttachment.</b> A {@code @Lob} on
 * that entity would be read on every read of it: {@code @Basic(fetch = LAZY)} needs Hibernate's bytecode
 * enhancer, which this build does not run, so the annotation is silently ignored. Opening the attachments
 * sidebar lists names and sizes for a complaint — with the blob on the same row that list would pull up to
 * the full 25MB per-complaint budget of document bytes out of the database to render filenames.
 *
 * <p>Rows are written only for attachments stored after this change. Attachments taken in earlier still
 * live on disk under {@code ComplaintAttachment.storagePath} and have no row here, which is why every read
 * path falls back to the file when this lookup misses.
 */
@Entity
@Table(name = "COMPLAINT_ATTACHMENT_DATA")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintAttachmentData {

    /** Shares the attachment's own id — one row of bytes per attachment, no surrogate key. */
    @Id
    @Column(name = "ATTACHMENT_ID")
    private Long attachmentId;

    /**
     * Explicit {@code LONGBLOB}: without a {@code columnDefinition}, Hibernate's MySQL dialect maps a
     * {@code @Lob byte[]} to plain {@code BLOB}, which tops out at 64KB — smaller than a single attachment
     * ({@link com.hrms.cms.config.FileStorageConfig#maxFileSize} allows up to 5MB), so any real document
     * failed on insert with a truncation error.
     */
    @Lob
    @Column(name = "FILE_DATA", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] fileData;
}
