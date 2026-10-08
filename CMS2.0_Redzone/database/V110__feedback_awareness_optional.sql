-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V110 — UST109 Q6: "did the CMS portal increase awareness" is OPTIONAL
--
-- WHY THIS EXISTS
--
--   UST109 lists the awareness question as optional, but COMPLAINT_FEEDBACK.cms_portal_awareness was
--   created NOT NULL (V8). Service-level validation also demanded it, so the contradiction was hidden:
--   the API rejected the submission before it could reach the column. With that validation removed to
--   match the user story, a questionnaire that legitimately skips Q6 reached the insert and failed on
--   the constraint instead — so the column has to be relaxed for the field to actually be optional.
--
--   The three rating questions and the source-of-information question remain mandatory and keep their
--   NOT NULL constraints; only Q6 changes.
--
-- Oracle counterpart is V107. The two directories' V-numbers are NOT in sync.
-- Re-runnable: the MODIFY is guarded on information_schema. Procedure prefix v110_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS v110_relax_awareness;

DELIMITER //
CREATE PROCEDURE v110_relax_awareness()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINT_FEEDBACK'
           AND COLUMN_NAME = 'cms_portal_awareness' AND IS_NULLABLE = 'NO') > 0 THEN
        ALTER TABLE COMPLAINT_FEEDBACK MODIFY COLUMN cms_portal_awareness VARCHAR(50) NULL;
    END IF;
END //
DELIMITER ;

CALL v110_relax_awareness();
DROP PROCEDURE IF EXISTS v110_relax_awareness;
