-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V106 — CEPC_CONTACT_PERSONS: who at the entity this office has actually been dealing with
--
-- WHY A TABLE OF ITS OWN
--
--   The Contact Entity tab needs to add and edit contact persons, several per complaint. The obvious
--   home was NODAL_OFFICER_RECORDS with a row-type flag, and that does not work:
--
--     * uk_no_complaint (added in V81) is UNIQUE on COMPLAINT_NUMBER — one row per complaint — and
--       RECORD_NUMBER is UNIQUE and derived as 'NOR-' || the complaint number. A second row per
--       complaint is therefore rejected twice over, and the table is no longer empty, so neither key
--       can be dropped without consequence.
--     * Eight queries read that table with no notion of a row that is not a nodal record: RBIO's
--       by-complaint endpoint, the dashboard's NO/PNO columns, the auto-create existence check (which
--       would find a contact row, conclude a record exists and never create the real one), the RE
--       reassignment workload counts, and the staleness sweep that chases entities. Contact rows would
--       surface in all of them unless every predicate were amended.
--
--   The two are also different in kind. A nodal officer is whoever the entity has DESIGNATED, taken
--   from master data and snapshotted onto the record when the complaint is forwarded. A contact person
--   is whoever the dealing officer is in touch with, typed in by hand. Hence: no unique key on
--   COMPLAINT_NUMBER, and no defaulting from the entity master.
--
-- NO FOREIGN KEY TO COMPLAINTS
--
--   Scoped by COMPLAINT_NUMBER, matching how NODAL_OFFICER_RECORDS scopes itself, so the two resolve
--   side by side from the one complaint number the tab already holds. The service rejects a number that
--   is not a known complaint; a database-level FK would additionally dictate a delete rule for a table
--   whose rows nothing deletes.
--
-- WHY THE GUARD READS information_schema WITH UPPER(TABLE_NAME), AND WHY THE CREATE IS LOWERCASE
--
--   Unchanged from V103/V104, whose headers set out the argument in full: Hibernate folds @Table names
--   to lowercase, and on MySQL 8 information_schema.TABLES.TABLE_NAME is binary-collated, so a bare
--   comparison against an uppercase literal matches nothing. This is a genuinely new table, so the
--   CREATE below actually runs — and with lower_case_table_names=0 (the Linux default, and the dev
--   container's setting) an uppercase CREATE would leave ddl-auto=update to build a lowercase twin
--   beside it and strand this one empty.
--
--   Do not copy this form into database/oracle/, which needs the bare comparison for the opposite
--   folding reason.
--
-- Re-runnable. Procedure prefix cepc_v106_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v106_create_table;
DELIMITER //
CREATE PROCEDURE cepc_v106_create_table()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'CEPC_CONTACT_PERSONS') THEN
        CREATE TABLE cepc_contact_persons (
            ID               BIGINT       NOT NULL AUTO_INCREMENT,
            -- Not unique: several contacts per complaint is the point of the table.
            COMPLAINT_NUMBER VARCHAR(50)  NOT NULL,
            NAME             VARCHAR(200) NOT NULL,
            DESIGNATION      VARCHAR(100) NULL,
            -- Nullable individually, but the service refuses a contact with neither an email nor a
            -- phone: a contact nobody can reach is the same defect as no contact at all. Not expressible
            -- as a column constraint without a CHECK that MySQL 5.7 would have ignored silently.
            EMAIL            VARCHAR(200) NULL,
            PHONE            VARCHAR(20)  NULL,
            REMARKS          VARCHAR(500) NULL,
            -- On the row rather than left to the audit trail: the tab shows "added by" so a reviewer can
            -- tell a dealing officer's own contact from one carried over, and reading that from an audit
            -- query per row would be a join the list does not otherwise need.
            CREATED_BY       VARCHAR(200) NULL,
            LAST_MODIFIED_BY VARCHAR(200) NULL,
            CREATED_AT       DATETIME(6)  NULL,
            LAST_MODIFIED_AT DATETIME(6)  NULL,
            PRIMARY KEY (ID),
            -- The only read this table serves is "the contacts for one complaint, oldest first".
            KEY IDX_CEPC_CP_COMPLAINT (COMPLAINT_NUMBER)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
END //
DELIMITER ;

CALL cepc_v106_create_table();
DROP PROCEDURE IF EXISTS cepc_v106_create_table;
