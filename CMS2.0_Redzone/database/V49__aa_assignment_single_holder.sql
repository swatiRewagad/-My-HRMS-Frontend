-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V49 — enforce "one live holder per appeal" in the database (session S2C)
--
-- The engine serialises assignment with a row lock on the per-ROLE-GROUP rotation pointer, which makes
-- the threshold safe. It does NOT make the holder invariant safe: two concurrent assignments of the
-- SAME appeal into DIFFERENT role groups (AA_DO and AA_REVIEWER, a real transition in this workflow)
-- lock different pointer rows, both observe no current holder, and both insert. The result is two live
-- holders and two officers permanently charged for one record.
--
-- Application-level checking cannot close that: the two transactions never contend on a shared row.
-- So the invariant is expressed as a constraint.
--
-- Technique: a generated column that equals APPEAL_NUMBER while the row is held and is NULL once it is
-- released, with a UNIQUE index over it. MySQL treats NULLs as distinct in a unique index, so any number
-- of released rows coexist while at most one unreleased row per appeal is permitted. This is the
-- portable equivalent of a filtered/partial index.
--
-- Re-runnable.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s2c49_add_column;
DELIMITER //
CREATE PROCEDURE s2c49_add_column(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def VARCHAR(500))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_col, ' ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS s2c49_add_unique;
DELIMITER //
CREATE PROCEDURE s2c49_add_unique(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE UNIQUE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ── Clean up any pre-existing duplicates before adding the constraint ─────────────────────────────
-- Without this the index creation fails on a database that already contains a double-holder row. The
-- newest placement is kept as the live one, because it reflects the most recent decision; older
-- unreleased rows are marked released rather than deleted, so the placement history survives.
UPDATE AA_ASSIGNMENT_RECORD r
  JOIN (
        SELECT appeal_number, MAX(id) AS keep_id
          FROM AA_ASSIGNMENT_RECORD
         WHERE released_at IS NULL
         GROUP BY appeal_number
        HAVING COUNT(*) > 1
       ) dup
    ON dup.appeal_number = r.appeal_number
   SET r.released_at = NOW(6)
 WHERE r.released_at IS NULL
   AND r.id <> dup.keep_id;

CALL s2c49_add_column('AA_ASSIGNMENT_RECORD', 'active_appeal_number',
    "VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN appeal_number ELSE NULL END) STORED");

CALL s2c49_add_unique('AA_ASSIGNMENT_RECORD', 'uk_aaar_single_holder', 'active_appeal_number');

DROP PROCEDURE IF EXISTS s2c49_add_column;
DROP PROCEDURE IF EXISTS s2c49_add_unique;
