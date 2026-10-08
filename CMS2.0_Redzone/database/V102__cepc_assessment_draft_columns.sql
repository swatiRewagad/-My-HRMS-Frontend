-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V102 — CEPC_COMPLAINT_ASSESSMENT: draft columns for the Final Decision and Forward tabs
--
-- WHY THIS EXISTS
--
--   The meta row's Save icon used to appear only on the Summary tab. Extending it to the Conciliation,
--   Forward and Final Decision tabs needs somewhere to put a decision that is still being drafted —
--   until now those two tabs' fields were persisted ONLY by their terminal action (send-for-approval,
--   which closes the complaint), so a half-finished decision could not be saved at all.
--
--   Conciliation needed nothing: CEPC_CONCILIATION_MEETING already stores all eight of its fields.
--
--   Six of the Final Decision fields already have columns here (GIST_OF_CASE, GIST_OF_CASE_REGIONAL,
--   SPEAKING_ORDER_GENERATED, COMPLAINT_STATUS_ON_PORTAL, ADVISORY_COMPLIANCE_DATE,
--   AWARD_ACCEPTANCE_DATE) plus the three money columns; this adds only the six that did not.
--
-- WHY FORWARD IS ONE JSON COLUMN AND FINAL DECISION IS SCALARS
--
--   Under utf8mb4 MySQL charges a varchar against the 65535-byte row limit at 4 bytes per character, and
--   this table has already hit that ceiling once (see the entity's javadoc — three 4000-char narrative
--   columns took 48KB and the table stopped accepting new columns). The Forward draft is six fields of
--   which four are 200-char names and emails: ~3.2KB of budget for a pre-dispatch scratchpad nothing
--   reports on. As TEXT it costs a pointer. The Final Decision figures feed closure MIS and must stay
--   queryable, so those stay scalar — and being short, they are nearly free.
--
--   The TEXT columns below are TEXT and not @Lob for the reason V101 records: a bare `@Lob String`
--   resolves to TINYTEXT (255 bytes) under this dialect and ddl-auto=update will silently narrow the
--   column, truncating officer narrative. The entity pins columnDefinition = "TEXT" to match this file.
--
-- WHY THE TABLE NAME IS RESOLVED AT RUNTIME
--
--   This table has no CREATE TABLE anywhere in database/. It exists only because the dev and Oracle-dev
--   profiles run ddl-auto=update (only prod runs `validate`), so HIBERNATE names it — and Boot's
--   CamelCaseToUnderscoresNamingStrategy folds the entity's @Table(name = "CEPC_COMPLAINT_ASSESSMENT")
--   to lowercase. On a server with lower_case_table_names=0 (the Linux default, and the case on the dev
--   MySQL container) identifiers are case-sensitive, so a hardcoded `ALTER TABLE
--   CEPC_COMPLAINT_ASSESSMENT` would fail with "table doesn't exist" against the very table it can see in
--   information_schema — whose TABLE_NAME comparison, by contrast, IS case-insensitive under the default
--   collation. So the guard can match either spelling but the DDL cannot: the real name is read out of
--   information_schema and interpolated. That also makes this script correct against a MySQL deployment
--   whose table was created in either case.
--
--   The script no-ops cleanly when the table is absent, so it can be replayed against a database where
--   Hibernate has not yet created it.
--
-- Re-runnable. Procedure prefix cepc_fd_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_fd_add_draft_columns;
DELIMITER //
CREATE PROCEDURE cepc_fd_add_draft_columns()
BEGIN
    DECLARE v_table VARCHAR(64) DEFAULT NULL;

    -- The table as the server actually spells it, or NULL when it does not exist yet.
    SELECT TABLE_NAME INTO v_table FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'CEPC_COMPLAINT_ASSESSMENT'
     LIMIT 1;

    IF v_table IS NULL THEN
        SELECT 'CEPC_COMPLAINT_ASSESSMENT absent - skipping' AS note;
    ELSE
        SET @t = v_table;

        -- FINAL_DECISION_ACTION — which of CLOSE / ADVISORY / AWARD / REJECT_WITHDRAW_SETTLE the officer
        -- is working towards.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'FINAL_DECISION_ACTION') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN FINAL_DECISION_ACTION VARCHAR(40) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'REJECT_WITHDRAW_SETTLE_SUB_ACTION') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t,
                              '` ADD COLUMN REJECT_WITHDRAW_SETTLE_SUB_ACTION VARCHAR(40) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'REJECT_WITHDRAW_SETTLE_REASON') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t,
                              '` ADD COLUMN REJECT_WITHDRAW_SETTLE_REASON TEXT NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'CLOSURE_CLAUSE_DESCRIPTION') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t,
                              '` ADD COLUMN CLOSURE_CLAUSE_DESCRIPTION TEXT NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Deliberately not COMPLAINTS.CLOSURE_CLAUSE: that column is written by the terminal workflow arm
        -- and a draft save must not overwrite a clause the closure already committed. The read side falls
        -- through to the complaint's value when it is set, so a committed clause still wins.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'CLOSURE_CLAUSE_DRAFT') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN CLOSURE_CLAUSE_DRAFT VARCHAR(60) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Distinct from COMPLAINTS.AWARD_IMPLEMENTED_DATE, which records when the RE actually complied.
        -- This is the date the award sets for them to do it by.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'AWARD_IMPLEMENTATION_DATE') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN AWARD_IMPLEMENTATION_DATE DATE NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'FORWARD_DRAFT_JSON') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN FORWARD_DRAFT_JSON TEXT NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Idempotent repair for the TINYTEXT hazard V101 documents: if ddl-auto reached these columns
        -- before this script did and narrowed them, widen them back. A column already TEXT is unaffected.
        SET @sql = CONCAT('ALTER TABLE `', @t,
                          '` MODIFY COLUMN REJECT_WITHDRAW_SETTLE_REASON TEXT NULL');
        PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

        SET @sql = CONCAT('ALTER TABLE `', @t, '` MODIFY COLUMN CLOSURE_CLAUSE_DESCRIPTION TEXT NULL');
        PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

        SET @sql = CONCAT('ALTER TABLE `', @t, '` MODIFY COLUMN FORWARD_DRAFT_JSON TEXT NULL');
        PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    END IF;
END //
DELIMITER ;

CALL cepc_fd_add_draft_columns();
DROP PROCEDURE IF EXISTS cepc_fd_add_draft_columns;
