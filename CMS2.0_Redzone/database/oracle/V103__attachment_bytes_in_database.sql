-- ============================================================
-- V103 — COMPLAINT_ATTACHMENT_DATA: attachment bytes held in the database
--
-- Oracle counterpart of MySQL V105. The two directories' V-numbers are NOT in sync.
--
-- WHY THIS EXISTS
--
--   Attachment bytes were written to a folder under cms.attachments.root-path and only a path was kept in
--   COMPLAINT_ATTACHMENTS. The documents therefore lived outside every guarantee the database gives: nothing
--   in a database backup, restore or replica contained them, and a row could — and in this deployment did —
--   outlive the file it pointed at, which the bundle endpoint reports as a missing attachment. Bytes now go
--   here instead.
--
-- WHY A SECOND TABLE AND NOT A FILE_DATA COLUMN ON COMPLAINT_ATTACHMENTS
--
--   A LOB on that table would be read whenever a row of it is read. @Basic(fetch = LAZY) does not help: lazy
--   basic fetching needs Hibernate's bytecode enhancer, which this build does not run, so the annotation is
--   ignored without complaint. The attachments sidebar lists names, types and sizes for a complaint — with
--   the bytes on the same row, rendering that list would pull up to the whole 25MB per-complaint budget out
--   of the database to display filenames.
--
-- EXISTING ATTACHMENTS ARE NOT MIGRATED
--
--   Their bytes are on a filesystem this script cannot read. Rows keep their STORAGE_PATH and no row is
--   created here for them; FileStorageService falls back to that path whenever this table has no row for an
--   attachment, so old and new attachments both download. Only rows created after this change have
--   STORAGE_PATH NULL, and that NULL is what says "the bytes are in the database".
--
-- ORACLE-SPECIFIC NOTES
--
--   BLOB, which is Oracle's only unbounded binary type — there is no size ladder to choose from as there is
--   on MySQL, so the per-file cap (5MB, from FileStorageConfig/SYSTEM_CONFIG) is enforced solely by the
--   application. Stored out of line by default, which is what we want: a SELECT that does not name the
--   column does not read the bytes.
--
--   Guarded on USER_TABLES / USER_TAB_COLUMNS so a re-run is safe. Those guards compare TABLE_NAME against
--   bare uppercase literals, which is correct HERE and must not be "fixed" to match MySQL V105's UPPER()
--   form — see V101's header for the full argument. Same code shape, opposite folding direction.
-- ============================================================

DECLARE
    PROCEDURE add_table(p_table VARCHAR2, p_sql VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = p_table;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_sql;
        END IF;
    END;
BEGIN
    -- ATTACHMENT_ID shares COMPLAINT_ATTACHMENTS.ID — one row of bytes per attachment, no surrogate key.
    --
    -- No FK to COMPLAINT_ATTACHMENTS: the application deletes the bytes and the row in one transaction
    -- (FileStorageService.deleteAttachment), and COMPLAINT_ATTACHMENTS itself carries no FK to COMPLAINTS,
    -- so one here would be the only FK in this area and would fail on any database where an earlier orphan
    -- already exists.
    add_table('COMPLAINT_ATTACHMENT_DATA', '
        CREATE TABLE COMPLAINT_ATTACHMENT_DATA (
            ATTACHMENT_ID NUMBER(19) NOT NULL,
            FILE_DATA     BLOB       NOT NULL,
            CONSTRAINT PK_COMPLAINT_ATTACHMENT_DATA PRIMARY KEY (ATTACHMENT_ID)
        )');
END;
/

-- ============================================================
-- COMPLAINT_ATTACHMENTS.STORAGE_PATH must be nullable
--
--   Rows created from now on have no file, so they have no path. The column is left in place rather than
--   dropped: it is the only thing that locates the documents attached before this change.
--
--   MODIFY ... NULL raises ORA-01451 if the column is already nullable, so the guard checks NULLABLE first
--   rather than relying on the statement being idempotent.
-- ============================================================

DECLARE
    v_nullable VARCHAR2(1);
BEGIN
    SELECT NULLABLE INTO v_nullable FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINT_ATTACHMENTS' AND COLUMN_NAME = 'STORAGE_PATH';
    IF v_nullable = 'N' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINT_ATTACHMENTS MODIFY (STORAGE_PATH NULL)';
    END IF;
EXCEPTION
    -- The table or column is absent: this database has not reached the release that created it, and
    -- ddl-auto/an earlier script will create it already nullable.
    WHEN NO_DATA_FOUND THEN NULL;
END;
/
