-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V47 — AA reassignment requests (story 11)
--
-- Session S2C. Re-runnable.
--
-- A separate table from REASSIGNMENT_REQUESTS rather than a widening of it. That table requires
-- NODAL_OFFICER_RECORD_ID and ENTITY_CODE, both NOT NULL and both meaningless for an appeal, and the RE
-- flows (UST838-845) depend on its current shape. The status vocabulary is kept identical so the two
-- read alike.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s2c47_add_index;
DELIMITER //
CREATE PROCEDURE s2c47_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CREATE TABLE IF NOT EXISTS AA_REASSIGNMENT_REQUEST (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    appeal_number       VARCHAR(50)  NOT NULL,
    role_group          VARCHAR(100),
    from_user_id        VARCHAR(200) NOT NULL,
    -- Null means "let the engine choose", which is the common case: an officer asking to be relieved
    -- usually has no view on who takes over, and naming a colleague would route around the pool rules.
    to_user_id          VARCHAR(200),
    -- Immutable in the entity mapping: an audit trail that can be edited afterwards is not one.
    reason              VARCHAR(1000) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    requested_by        VARCHAR(200) NOT NULL,
    requested_by_role   VARCHAR(50),
    requested_at        DATETIME(6)  NOT NULL,
    decided_by          VARCHAR(200),
    decided_at          DATETIME(6),
    decision_comment    VARCHAR(1000),
    -- Where it actually went, which can differ from to_user_id when the engine chose.
    resolved_to_user_id VARCHAR(200),
    auto_approved       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s2c47_add_index('AA_REASSIGNMENT_REQUEST', 'idx_aarr_status', 'status');
CALL s2c47_add_index('AA_REASSIGNMENT_REQUEST', 'idx_aarr_appeal', 'appeal_number');
CALL s2c47_add_index('AA_REASSIGNMENT_REQUEST', 'idx_aarr_requested_by', 'requested_by');

DROP PROCEDURE IF EXISTS s2c47_add_index;
