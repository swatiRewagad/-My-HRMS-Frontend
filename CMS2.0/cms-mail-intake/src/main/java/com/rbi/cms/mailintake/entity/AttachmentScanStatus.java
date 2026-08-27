package com.rbi.cms.mailintake.entity;

public enum AttachmentScanStatus {
    PENDING,
    CLEAN,
    INFECTED,
    SCAN_FAILED,
    /** The no-op default AttachmentScanner ran and skipped real scanning. */
    SKIPPED
}
