-- V8: Phase 1 UST changes — withdrawal fields, appeal delay, feedback, FAQ, system config
-- MySQL version

-- ═══ COMPLAINTS: withdrawal fields ═══
ALTER TABLE COMPLAINTS ADD COLUMN withdrawal_reason VARCHAR(500) NULL;
ALTER TABLE COMPLAINTS ADD COLUMN withdrawal_date DATETIME NULL;
ALTER TABLE COMPLAINTS ADD COLUMN withdrawn_by VARCHAR(200) NULL;

-- ═══ APPEALS: reason for delay (31-60 day window) ═══
ALTER TABLE APPEALS ADD COLUMN reason_for_delay VARCHAR(500) NULL;

-- ═══ COMPLAINT_FEEDBACK ═══
CREATE TABLE IF NOT EXISTS COMPLAINT_FEEDBACK (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_number VARCHAR(50) NOT NULL,
    overall_rating INT NOT NULL,
    ease_of_filing INT NOT NULL,
    timeliness_rating INT NULL,
    communication_rating INT NULL,
    satisfaction_rating INT NULL,
    grievance_redress_time INT NOT NULL,
    source_of_information VARCHAR(100) NOT NULL,
    source_other_text VARCHAR(500) NULL,
    cms_portal_awareness VARCHAR(50) NOT NULL,
    feedback_text VARCHAR(500) NULL,
    suggestions VARCHAR(500) NULL,
    complainant_phone VARCHAR(20) NULL,
    office_code VARCHAR(20) NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feedback_complaint UNIQUE (complaint_number)
);

CREATE INDEX idx_feedback_complaint ON COMPLAINT_FEEDBACK (complaint_number);
CREATE INDEX idx_feedback_phone ON COMPLAINT_FEEDBACK (complainant_phone);
CREATE INDEX idx_feedback_office ON COMPLAINT_FEEDBACK (office_code);

-- ═══ FAQ ═══
CREATE TABLE IF NOT EXISTS FAQ (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_key VARCHAR(200) NOT NULL,
    answer_key VARCHAR(200) NOT NULL,
    category VARCHAR(100) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

CREATE INDEX idx_faq_category ON FAQ (category);
CREATE INDEX idx_faq_sort ON FAQ (sort_order);

-- Seed initial FAQ entries (keys reference TRANSLATIONS table)
INSERT INTO FAQ (question_key, answer_key, category, sort_order, is_active, created_at, updated_at) VALUES
('faq.q1.question', 'faq.q1.answer', 'GENERAL', 1, TRUE, NOW(), NOW()),
('faq.q2.question', 'faq.q2.answer', 'GENERAL', 2, TRUE, NOW(), NOW()),
('faq.q3.question', 'faq.q3.answer', 'GENERAL', 3, TRUE, NOW(), NOW()),
('faq.q4.question', 'faq.q4.answer', 'FILING', 4, TRUE, NOW(), NOW()),
('faq.q5.question', 'faq.q5.answer', 'FILING', 5, TRUE, NOW(), NOW()),
('faq.q6.question', 'faq.q6.answer', 'TRACKING', 6, TRUE, NOW(), NOW()),
('faq.q7.question', 'faq.q7.answer', 'TRACKING', 7, TRUE, NOW(), NOW()),
('faq.q8.question', 'faq.q8.answer', 'APPEAL', 8, TRUE, NOW(), NOW()),
('faq.q9.question', 'faq.q9.answer', 'APPEAL', 9, TRUE, NOW(), NOW()),
('faq.q10.question', 'faq.q10.answer', 'WITHDRAWAL', 10, TRUE, NOW(), NOW());

-- ═══ SYSTEM_CONFIG ═══
CREATE TABLE IF NOT EXISTS SYSTEM_CONFIG (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    config_value VARCHAR(500) NOT NULL,
    description VARCHAR(500) NULL,
    updated_by VARCHAR(200) NULL,
    updated_at DATETIME NULL,
    CONSTRAINT uk_config_key UNIQUE (config_key)
);

-- Seed default timeline configuration
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_at) VALUES
('timeline.complaint.filing_window_days', '365', 'Maximum days after incident to file a complaint', NOW()),
('timeline.appeal.filing_window_days', '30', 'Days after closure to file an appeal without delay reason', NOW()),
('timeline.appeal.extended_window_days', '60', 'Days after closure to file an appeal with delay reason', NOW()),
('timeline.re.response_deadline_days', '15', 'Days for RE to respond to a forwarded complaint', NOW()),
('timeline.sla.default_resolution_days', '30', 'Default SLA for complaint resolution', NOW());

-- ═══ CONFIG_AUDIT_LOG ═══
CREATE TABLE IF NOT EXISTS CONFIG_AUDIT_LOG (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    old_value VARCHAR(500) NULL,
    new_value VARCHAR(500) NULL,
    changed_by VARCHAR(200) NOT NULL,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_config_audit_key ON CONFIG_AUDIT_LOG (config_key);
CREATE INDEX idx_config_audit_date ON CONFIG_AUDIT_LOG (changed_at);
