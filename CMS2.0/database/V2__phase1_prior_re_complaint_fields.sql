-- ============================================================
-- RBI CMS Phase 1 - Prior RE Complaint Fields
-- Version: 2.0.0
-- Description: Add fields for RB-IOS 2026 eligibility spine
-- Oracle-portable DDL (also works with MySQL via minor syntax)
-- ============================================================

-- ============================================================
-- ALTER TABLE: COMPLAINTS
-- Add prior-RE-complaint fields required by RB-IOS Q16/Q17/Q18
-- ============================================================

-- Whether complainant already complained to the Regulated Entity
ALTER TABLE COMPLAINTS ADD COLUMN prior_re_complaint TINYINT(1) DEFAULT NULL;

-- Date of the prior complaint to the RE
ALTER TABLE COMPLAINTS ADD COLUMN re_complaint_date DATE DEFAULT NULL;

-- RE complaint reference / acknowledgement number
ALTER TABLE COMPLAINTS ADD COLUMN re_complaint_reference VARCHAR(200) DEFAULT NULL;

-- Whether RE has replied and complainant is dissatisfied (overrides 30-day window)
ALTER TABLE COMPLAINTS ADD COLUMN re_replied_and_dissatisfied TINYINT(1) DEFAULT NULL;

-- Maintainability determination fields (Phase 2/5)
ALTER TABLE COMPLAINTS ADD COLUMN triage_signal VARCHAR(10) DEFAULT NULL;
ALTER TABLE COMPLAINTS ADD COLUMN triage_flags TEXT DEFAULT NULL;
ALTER TABLE COMPLAINTS ADD COLUMN eligibility_timeline TEXT DEFAULT NULL;
ALTER TABLE COMPLAINTS ADD COLUMN maintainability_determination VARCHAR(30) DEFAULT NULL;
ALTER TABLE COMPLAINTS ADD COLUMN maintainability_determined_by VARCHAR(200) DEFAULT NULL;
ALTER TABLE COMPLAINTS ADD COLUMN maintainability_determined_at DATETIME DEFAULT NULL;
ALTER TABLE COMPLAINTS ADD COLUMN award_amount DECIMAL(15,2) DEFAULT NULL;

-- Performance indexes for MRE and analytics queries
CREATE INDEX idx_complaint_re_date ON COMPLAINTS(re_complaint_date);
CREATE INDEX idx_complaint_triage ON COMPLAINTS(triage_signal);
CREATE INDEX idx_complaint_maintainability ON COMPLAINTS(maintainability_determination);
CREATE INDEX idx_complaint_award ON COMPLAINTS(award_amount);

-- ============================================================
-- TABLE: RE_RESPONSE_TRACKER (Phase 5 - RE Responsiveness Radar)
-- ============================================================

CREATE TABLE RE_RESPONSE_TRACKER (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_id BIGINT NOT NULL,
    regulated_entity_id BIGINT NOT NULL,
    forwarded_at DATETIME NOT NULL,
    responded_at DATETIME DEFAULT NULL,
    window_days INT NOT NULL DEFAULT 30,
    window_expires_at DATETIME DEFAULT NULL,
    breached TINYINT(1) NOT NULL DEFAULT 0,
    ex_parte_eligible TINYINT(1) NOT NULL DEFAULT 0,
    notes VARCHAR(500) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_re_tracker_complaint (complaint_id),
    INDEX idx_re_tracker_re (regulated_entity_id),
    INDEX idx_re_tracker_breached (breached),
    INDEX idx_re_tracker_forwarded (forwarded_at)
);

-- ============================================================
-- Oracle-equivalent DDL (for production Oracle DB porting)
-- ============================================================
-- ALTER TABLE COMPLAINTS ADD prior_re_complaint NUMBER(1) DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD re_complaint_date DATE DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD re_complaint_reference VARCHAR2(200) DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD re_replied_and_dissatisfied NUMBER(1) DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD triage_signal VARCHAR2(10) DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD triage_flags CLOB DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD eligibility_timeline CLOB DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD maintainability_determination VARCHAR2(30) DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD maintainability_determined_by VARCHAR2(200) DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD maintainability_determined_at TIMESTAMP DEFAULT NULL;
-- ALTER TABLE COMPLAINTS ADD award_amount NUMBER(15,2) DEFAULT NULL;
-- CREATE INDEX idx_complaint_re_date ON COMPLAINTS(re_complaint_date);
-- CREATE INDEX idx_complaint_triage ON COMPLAINTS(triage_signal);
-- CREATE INDEX idx_complaint_maintainability ON COMPLAINTS(maintainability_determination);
-- CREATE INDEX idx_complaint_award ON COMPLAINTS(award_amount);
