-- V81: NODAL_OFFICER_RECORDS — processing office column + one-record-per-complaint key (UST569/UST773)
-- MySQL version
--
-- Context: NODAL_OFFICER_RECORDS had no creating code path at all. The only writer was the reassignment
-- executor, which updates an existing row, so the table stayed permanently empty — and the 15/20-day
-- staleness escalations in NotificationScheduledTasks therefore matched zero rows every night and logged
-- success. UST569 adds auto-creation on complaint registration, which makes two things necessary here.
--
-- 1. PROCESSING_OFFICE (nullable). UST773 resolves the NO/PNO from (regulated entity + processing
--    Ombudsman office), so the record has to remember which office it was resolved for. Nullable because
--    two complaint-creation paths have no office in scope, and a record with an unknown office is still
--    strictly better than no record; also because this runs against a shared dev database on
--    ddl-auto=update, where adding a NOT NULL column would fail outright if any row already existed.
--
-- 2. UK_NO_COMPLAINT on COMPLAINT_NUMBER. There is no DB-level guard today, so two concurrent
--    registrations of the same complaint (a retried POST, a redelivered message) would both observe
--    "no record" and both insert. Each duplicate then carries its own independent staleness clock, so the
--    Regulated Entity gets chased twice for one complaint and an officer sees two contradictory contact
--    sets. The key is safe to add now specifically because the table is empty — this is the only moment
--    it can be added without a data-cleanup step, which is why it is done here rather than deferred.
--    NodalOfficerRecordService.ensureRecordExists treats the resulting
--    DataIntegrityViolationException as success: the row it wanted now exists.

DROP PROCEDURE IF EXISTS add_nodal_officer_record_office;

DELIMITER //
CREATE PROCEDURE add_nodal_officer_record_office()
proc_body: BEGIN
    DECLARE tbl_missing INT;
    DECLARE col_missing INT;
    DECLARE dup_count INT;
    DECLARE idx_missing INT;

    SET tbl_missing := (SELECT COUNT(*) = 0 FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'NODAL_OFFICER_RECORDS');
    IF tbl_missing THEN
        -- Hibernate creates this table from the entity on first boot; nothing to alter yet.
        LEAVE proc_body;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'NODAL_OFFICER_RECORDS'
                          AND COLUMN_NAME = 'processing_office');
    IF col_missing THEN
        ALTER TABLE NODAL_OFFICER_RECORDS ADD COLUMN processing_office VARCHAR(100) NULL;
    END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'NODAL_OFFICER_RECORDS'
                          AND INDEX_NAME = 'idx_no_processing_office');
    IF idx_missing THEN
        CREATE INDEX idx_no_processing_office ON NODAL_OFFICER_RECORDS(processing_office);
    END IF;

    -- The unique key is only added when the data can actually satisfy it. On a clean database that is
    -- always true (zero rows). If an environment somehow already holds duplicates, adding the key would
    -- abort the whole migration, so it is skipped with a row left in sight of the operator instead.
    SET dup_count := (SELECT COUNT(*) FROM (
                          SELECT complaint_number FROM NODAL_OFFICER_RECORDS
                           GROUP BY complaint_number HAVING COUNT(*) > 1) d);

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'NODAL_OFFICER_RECORDS'
                          AND INDEX_NAME = 'uk_no_complaint');

    IF idx_missing AND dup_count = 0 THEN
        ALTER TABLE NODAL_OFFICER_RECORDS
            ADD CONSTRAINT uk_no_complaint UNIQUE (complaint_number);
    END IF;
END proc_body //
DELIMITER ;

CALL add_nodal_officer_record_office();
DROP PROCEDURE add_nodal_officer_record_office;
