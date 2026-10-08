-- ============================================================
-- V106 — UST87 AC5: tag a complaint filed despite a duplicate warning
--
-- FR-G-020/FR-G-021 let the citizen proceed after being told their complaint looks like a duplicate.
-- The user story requires the new complaint then be "tagged as a duplicate complaint to the previously
-- identified complaint" — but there was nowhere to record that, so the link was lost and the officer
-- saw two unrelated complaints about one grievance.
--
-- Nullable because the overwhelming majority of complaints raise no duplicate match at all; a value
-- here is the exception and means "the citizen was warned about this complaint number and continued".
-- Deliberately NOT a foreign key to COMPLAINTS(COMPLAINT_NUMBER): the referenced complaint can be
-- purged under retention while this one is still live, and losing the older record must not block
-- inserting or deleting the newer one.
-- ============================================================

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = 'DUPLICATE_OF_COMPLAINT_NUMBER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD DUPLICATE_OF_COMPLAINT_NUMBER VARCHAR2(50) NULL';
    END IF;
END;
/

-- Indexed so "show me everything duplicating complaint X" is a lookup rather than a full scan.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES
     WHERE INDEX_NAME = 'IDX_COMPLAINT_DUPLICATE_OF';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_COMPLAINT_DUPLICATE_OF ON COMPLAINTS(DUPLICATE_OF_COMPLAINT_NUMBER)';
    END IF;
END;
/
