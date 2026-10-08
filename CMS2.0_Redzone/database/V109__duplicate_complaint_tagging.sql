-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V109 — UST87 AC5: tag a complaint filed despite a duplicate warning
--
-- WHY THIS EXISTS
--
--   FR-G-020/FR-G-021 let the citizen continue after being told their complaint looks like a duplicate
--   of an earlier one. The user story then requires the new complaint be "tagged as a duplicate
--   complaint to the previously identified complaint" — but no column held that link, so once the
--   citizen clicked "Proceed Anyway" the relationship was discarded. The officer then received two
--   apparently unrelated complaints about a single grievance, which is the outcome FR-G-020 exists to
--   prevent.
--
--   Nullable because almost every complaint raises no duplicate match; a value here is the exception
--   and means "the citizen was warned about this complaint number and chose to file anyway".
--
--   Deliberately NOT a foreign key to COMPLAINTS(complaint_number): the referenced complaint may be
--   purged under the retention policy while this one is still live, and losing the older record must
--   not block inserting or deleting the newer one.
--
-- Oracle counterpart is V106. The two directories' V-numbers are NOT in sync.
-- Re-runnable: the ALTER is guarded on information_schema. Procedure prefix v109_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS v109_add_column;
DROP PROCEDURE IF EXISTS v109_add_index;

DELIMITER //
CREATE PROCEDURE v109_add_column(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_type VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_col, ' ', p_type);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //

-- Non-unique, unlike V108's helper: many complaints may duplicate the same earlier one.
CREATE PROCEDURE v109_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL v109_add_column('COMPLAINTS', 'duplicate_of_complaint_number', 'VARCHAR(50) NULL');

-- Indexed so "show me everything duplicating complaint X" is a lookup rather than a full scan.
CALL v109_add_index('COMPLAINTS', 'idx_complaint_duplicate_of', 'duplicate_of_complaint_number');

DROP PROCEDURE IF EXISTS v109_add_column;
DROP PROCEDURE IF EXISTS v109_add_index;
