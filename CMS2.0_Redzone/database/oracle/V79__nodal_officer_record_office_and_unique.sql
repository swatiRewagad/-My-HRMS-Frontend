-- V79: NODAL_OFFICER_RECORDS — processing office column + one-record-per-complaint key (UST569/UST773)
-- Oracle version (mirrors database/V81__nodal_officer_record_office_and_unique.sql;
-- the two directories' V-numbers are not in sync)
--
-- Context: NODAL_OFFICER_RECORDS had no creating code path at all. The only writer was the reassignment
-- executor, which updates an existing row, so the table stayed permanently empty — and the 15/20-day
-- staleness escalations in NotificationScheduledTasks therefore matched zero rows every night and logged
-- success. UST569 adds auto-creation on complaint registration, which makes two things necessary here.
--
-- 1. PROCESSING_OFFICE (nullable). UST773 resolves the NO/PNO from (regulated entity + processing
--    Ombudsman office), so the record has to remember which office it was resolved for. Nullable because
--    two complaint-creation paths have no office in scope, and a record with an unknown office is still
--    strictly better than no record; also because this runs on ddl-auto=update against a shared database,
--    where adding a NOT NULL column would fail outright if any row already existed.
--
-- 2. UK_NO_COMPLAINT on COMPLAINT_NUMBER. There is no DB-level guard today, so two concurrent
--    registrations of the same complaint (a retried POST, a redelivered message) would both observe
--    "no record" and both insert. Each duplicate then carries its own independent staleness clock, so the
--    Regulated Entity gets chased twice for one complaint and an officer sees two contradictory contact
--    sets. The key is safe to add now specifically because the table is empty — this is the only moment
--    it can be added without a data-cleanup step. NodalOfficerRecordService.ensureRecordExists treats the
--    resulting DataIntegrityViolationException as success: the row it wanted now exists.

DECLARE
    v_count NUMBER;
    v_dups  NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'NODAL_OFFICER_RECORDS';
    IF v_count = 0 THEN
        -- Hibernate creates this table from the entity on first boot; nothing to alter yet.
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'NODAL_OFFICER_RECORDS' AND COLUMN_NAME = 'PROCESSING_OFFICE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE NODAL_OFFICER_RECORDS ADD PROCESSING_OFFICE VARCHAR2(100)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_NO_PROCESSING_OFFICE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_NO_PROCESSING_OFFICE ON NODAL_OFFICER_RECORDS(PROCESSING_OFFICE)';
    END IF;

    -- Only added when the data can satisfy it. On a clean database that is always true (zero rows). If an
    -- environment already holds duplicates, adding the key would abort the migration, so it is skipped and
    -- left visible to the operator instead.
    SELECT COUNT(*) INTO v_dups FROM (
        SELECT COMPLAINT_NUMBER FROM NODAL_OFFICER_RECORDS
         GROUP BY COMPLAINT_NUMBER HAVING COUNT(*) > 1);

    SELECT COUNT(*) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UK_NO_COMPLAINT';
    IF v_count = 0 AND v_dups = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE NODAL_OFFICER_RECORDS '
                       || 'ADD CONSTRAINT UK_NO_COMPLAINT UNIQUE (COMPLAINT_NUMBER)';
    END IF;
END;
/
