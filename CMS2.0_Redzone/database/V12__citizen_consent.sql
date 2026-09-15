-- V12: DPDP Act, 2023 consent record for citizen complaint filing (UST5)
-- MySQL version
--
-- Maps com.hrms.cms.entity.CitizenConsent. NOTICE_TEXT holds a snapshot of the notice actually
-- served to the citizen in their locale, not a foreign key to TRANSLATIONS: the DPDP Act requires
-- the consent manager to demonstrate, per data principal, exactly what notice was accepted. A live
-- lookup would silently re-write history the moment the wording is revised.
--
-- CONSENT_VERSION carries the notice edition in force at capture time (cms.auth.consent.version).
-- Bumping that property makes existing rows non-current, so citizens are re-asked.

CREATE TABLE IF NOT EXISTS CITIZEN_CONSENTS (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile_number VARCHAR(15) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    consent_version VARCHAR(20) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    notice_text VARCHAR(2000) NOT NULL,
    granted_at DATETIME NOT NULL,
    client_ip VARCHAR(50) NULL,
    user_agent VARCHAR(500) NULL
);

-- MySQL has no CREATE INDEX IF NOT EXISTS, and hibernate ddl-auto may already have created the
-- table together with its indexes. Each index is therefore guarded via information_schema.
-- Plain statements only (no DELIMITER / stored procedure) so any migration runner can execute this.
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'CITIZEN_CONSENTS'
                AND INDEX_NAME = 'idx_consent_mobile');
SET @sql := IF(@idx = 0, 'CREATE INDEX idx_consent_mobile ON CITIZEN_CONSENTS (mobile_number)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'CITIZEN_CONSENTS'
                AND INDEX_NAME = 'idx_consent_granted_at');
SET @sql := IF(@idx = 0, 'CREATE INDEX idx_consent_granted_at ON CITIZEN_CONSENTS (granted_at)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Supports the "does this mobile hold a current consent for this purpose" lookup
-- (CitizenConsentRepository.findTopByMobileNumberAndPurposeOrderByGrantedAtDesc).
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'CITIZEN_CONSENTS'
                AND INDEX_NAME = 'idx_consent_mobile_purpose_ts');
SET @sql := IF(@idx = 0,
               'CREATE INDEX idx_consent_mobile_purpose_ts ON CITIZEN_CONSENTS (mobile_number, purpose, granted_at)',
               'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ═══ Complaint-level declaration flag (UST5 enforcement point) ═══
-- FileComplaintRequest.declarationAccepted is asserted for ONLINE filings only; email / physical /
-- walk-in intake never saw a checkbox and must not be rejected. Persisted so a filed complaint
-- carries its own evidence of the step-5 declaration rather than relying on the consent row alone.
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                AND COLUMN_NAME = 'DECLARATION_ACCEPTED');
SET @sql := IF(@col = 0,
               'ALTER TABLE COMPLAINTS ADD COLUMN declaration_accepted TINYINT(1) NULL',
               'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
