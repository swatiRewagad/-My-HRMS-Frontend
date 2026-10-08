-- ============================================================
-- V111 — "Sent from office comments" on the inter-office transfer row
-- ============================================================
--
-- The Forward milestone's Other Office option requires two distinct free-text values before
-- Save and Proceed: Reason for Transfer (already stored in INTER_OFFICE_TRANSFERS.reason) and Sent
-- from Office Comments, which had no column anywhere — InterOfficeTransferService.requestTransfer
-- only ever accepted the single `reason` string. Kept as its own column rather than folded into
-- `reason` so the two remain separately queryable/reportable.
--
-- Nullable (shared ddl-auto database). Re-running is safe: guarded on information_schema.
-- ============================================================

DROP PROCEDURE IF EXISTS s5_fwd_add_column;

DELIMITER $$

CREATE PROCEDURE s5_fwd_add_column(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'office_comments', 'TEXT NULL');
