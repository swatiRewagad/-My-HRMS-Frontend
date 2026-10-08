-- ============================================================
-- V107 — UST109 Q6: "did the CMS portal increase awareness" is OPTIONAL
--
-- Oracle counterpart of MySQL V110. The two directories' V-numbers are NOT in sync.
--
-- See MySQL V110's header for the full rationale: COMPLAINT_FEEDBACK.CMS_PORTAL_AWARENESS was created
-- NOT NULL (Oracle V7) while UST109 lists the question as optional. Service-level validation hid the
-- contradiction by rejecting the submission first; with that removed, a questionnaire that skips Q6
-- failed on the column constraint instead.
--
-- The mandatory questions (three ratings, source of information) keep their NOT NULL constraints.
-- ============================================================

DECLARE
    v_nullable VARCHAR2(1);
BEGIN
    SELECT NULLABLE INTO v_nullable FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINT_FEEDBACK' AND COLUMN_NAME = 'CMS_PORTAL_AWARENESS';
    IF v_nullable = 'N' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINT_FEEDBACK MODIFY (CMS_PORTAL_AWARENESS NULL)';
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN NULL;
END;
/
