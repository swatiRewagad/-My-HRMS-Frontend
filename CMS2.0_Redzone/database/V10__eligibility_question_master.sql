-- V10: ELIGIBILITY_QUESTION_MASTER (UST-public / D14)
-- MySQL version
--
-- Context: the citizen filing wizard's maintainability questions, their block rules and their
-- Scheme clause references were hardcoded in the Angular component
-- (file-complaint.component.ts eligibilityQuestions[]). A Scheme amendment therefore needed a
-- frontend release, and the hardcoded text had already drifted — it cited
-- "Integrated Ombudsman Scheme, 2026" for clauses that belong to the 2021 Scheme.
--
-- Rows are seeded by EligibilityQuestionMasterSeeder (insert-if-absent on
-- schemeVersion + questionKey) so operator edits made through the master survive a restart.
-- Served to the portal by GET /api/v1/eligibility/questions.
--
-- Mirrors the AUTO_CLOSURE_QUESTIONS conventions (schemeVersion + entityType + questionNumber).

CREATE TABLE IF NOT EXISTS ELIGIBILITY_QUESTION_MASTER (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scheme_version VARCHAR(20) NOT NULL,
    applicable_entity_type VARCHAR(20) NOT NULL,
    question_number INT NOT NULL,
    question_key VARCHAR(60) NOT NULL,
    question_type VARCHAR(20) NOT NULL,
    question_text TEXT NOT NULL,
    translation_key VARCHAR(120) NULL,
    block_on VARCHAR(20) NULL,
    block_message TEXT NULL,
    block_message_key VARCHAR(120) NULL,
    clause_reference VARCHAR(100) NULL,
    non_maintainable BIT(1) NOT NULL DEFAULT b'0',
    simplified_text TEXT NULL,
    simplified_text_key VARCHAR(120) NULL,
    inline_sub_question BIT(1) NOT NULL DEFAULT b'0',
    active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

-- MySQL 8.4 has no CREATE INDEX IF NOT EXISTS, so guard on information_schema.
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ELIGIBILITY_QUESTION_MASTER'
               AND INDEX_NAME = 'idx_eqm_scheme');
SET @sql := IF(@idx = 0,
               'CREATE INDEX idx_eqm_scheme ON ELIGIBILITY_QUESTION_MASTER(scheme_version)',
               'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ELIGIBILITY_QUESTION_MASTER'
               AND INDEX_NAME = 'idx_eqm_entity_type');
SET @sql := IF(@idx = 0,
               'CREATE INDEX idx_eqm_entity_type ON ELIGIBILITY_QUESTION_MASTER(applicable_entity_type)',
               'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ELIGIBILITY_QUESTION_MASTER'
               AND INDEX_NAME = 'idx_eqm_key');
SET @sql := IF(@idx = 0,
               'CREATE INDEX idx_eqm_key ON ELIGIBILITY_QUESTION_MASTER(question_key)',
               'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ═══ Correct the Scheme year in already-seeded translations ═══
-- The seeders are insert-if-absent, so environments seeded before this migration still hold
-- "Integrated Ombudsman Scheme, 2026" for clauses that belong to the 2021 Scheme. Citizens are
-- shown this text verbatim when a complaint is closed as non-maintainable, so it must be corrected
-- in place rather than only in the seeder defaults.
--
-- Scoped by key code, not by the English phrase: the localized rows are in native scripts, so no
-- English substring matches them. Bengali also stores the year in Bengali numerals (২০২৬), which
-- an ASCII '2026' replacement would silently skip.
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.id = t.translation_key_id
   SET t.`value` = REPLACE(REPLACE(t.`value`, '2026', '2021'), '২০২৬', '২০২১')
 WHERE k.code IN ('eligibility.block_not_filed', 'eligibility.passed_message', 'home.ct_regulated');

UPDATE TRANSLATION_KEYS
   SET default_value = REPLACE(REPLACE(default_value, '2026', '2021'), '২০২৬', '২০২১')
 WHERE code IN ('eligibility.block_not_filed', 'eligibility.passed_message', 'home.ct_regulated');
