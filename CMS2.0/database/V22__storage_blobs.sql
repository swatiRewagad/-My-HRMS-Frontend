-- V22: STORAGE_BLOBS (MySQL)
--
-- Attachment payloads used to live on the app server's filesystem, with
-- COMPLAINT_ATTACHMENTS.storage_path holding a relative path like "CMS-001/<uuid>_name.pdf". That
-- makes the upload directory a piece of unreplicated state: a second cms-backend instance cannot
-- serve a file the first one received, and nothing backs it up with the database.
--
-- STORAGE_BLOBS moves the bytes into the schema, owned by cms-storage-service. storage_path now
-- holds the OBJECT_ID (a UUID) instead of a path — same column, same VARCHAR type, so no
-- COMPLAINT_ATTACHMENTS change is needed.
--
-- TENANT_ID is deliberately nullable: the current deployment is single-tenant and populates nothing
-- here. It exists so a future multi-tenant split has a discriminator to filter on without a
-- migration on a table full of BLOBs.
--
-- Oracle equivalent: database/oracle/V22__storage_blobs.sql
-- LONGBLOB (not BLOB) because MySQL's BLOB caps at 64 KB, far below the 50 MB upload limit.

CREATE TABLE IF NOT EXISTS STORAGE_BLOBS (
    ID            BIGINT        NOT NULL AUTO_INCREMENT,
    TENANT_ID     VARCHAR(64)   NULL,
    OBJECT_ID     VARCHAR(200)  NOT NULL,
    BUCKET        VARCHAR(200)  NULL,
    FILE_NAME     VARCHAR(500)  NOT NULL,
    CONTENT_TYPE  VARCHAR(150)  NULL,
    SIZE_BYTES    BIGINT        NOT NULL,
    CHECKSUM      VARCHAR(64)   NULL,
    STORAGE_DATA  LONGBLOB      NOT NULL,
    CREATED_AT    DATETIME(6)   NOT NULL,
    PRIMARY KEY (ID),
    -- Every read path (fetch, metadata, delete) looks the row up by OBJECT_ID, and the handle in
    -- COMPLAINT_ATTACHMENTS.storage_path must resolve to exactly one blob.
    UNIQUE KEY idx_storage_blob_object (OBJECT_ID),
    -- Supports listing a complaint's blobs from the storage service directly, bypassing cms-backend.
    KEY idx_storage_blob_bucket (BUCKET)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- No backfill of existing filesystem attachments. Rows whose storage_path still holds a
-- "CMS-001/<uuid>_name.pdf" path have no matching blob and will fail to download until the files
-- are re-uploaded or migrated by a separate one-off job.
