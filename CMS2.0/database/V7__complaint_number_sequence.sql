-- ============================================================
-- RBI CMS Phase 7 - Complaint Number Sequence Table
-- Description: Tracks sequential complaint numbers per office + financial year
-- MySQL-compatible syntax
-- ============================================================

CREATE TABLE IF NOT EXISTS COMPLAINT_NUMBER_SEQUENCE (
    ID INT AUTO_INCREMENT PRIMARY KEY,
    OFFICE_CODE VARCHAR(10) NOT NULL,
    FINANCIAL_YEAR VARCHAR(6) NOT NULL,
    LAST_SEQUENCE INT NOT NULL DEFAULT 0,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_office_fy (OFFICE_CODE, FINANCIAL_YEAR)
);
