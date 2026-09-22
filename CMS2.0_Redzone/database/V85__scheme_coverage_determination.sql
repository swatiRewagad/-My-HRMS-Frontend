-- ============================================================
-- V85 — Record the Scheme-coverage determination on the complaint
-- Session S3 (UST473)
-- ============================================================
--
-- WHY
--
--   UST473 requires complaints against entities NOT covered by the Scheme to be handled by CEPC rather
--   than RBIO, and requires the CHECK RESULT to be recorded on the complaint. Neither happened:
--
--     * MreEntityCoverageService.isEntityCovered answered a substring existence test over
--       REGULATED_ENTITIES and ignored the `department` column entirely — and ignored its own
--       entityType argument. So a CEPC-listed entity, which is outside the Ombudsman Scheme, answered
--       "covered", and the result was @Cacheable so the wrong answer was then served from cache.
--
--     * ComplaintRoutingService.resolveDepartment took matches.get(0) from an UNORDERED partial match.
--       Against the live data 'HDFC' matches HDFC Credila Financial Services Limited (CEPC) plus two
--       HDFC Bank rows (RBIO), and the CEPC row comes back FIRST — so a complaint against a
--       Scheme-covered bank was routed to CEPC by row order. It also returned 'RBIO' for a blank name
--       and for an unknown entity, which is default-ALLOW on a maintainability determination: an entity
--       nobody regulates was admitted to the Ombudsman Scheme.
--
--   Coverage is now resolved in one place, refuses to guess when a name matches entities in different
--   departments, and treats unknown as not covered. Ambiguous and unknown route to CEPC, which is the
--   fail-closed direction: CEPC can escalate a complaint in, whereas RBIO issuing a determination over
--   an entity it has no jurisdiction for cannot be undone from the citizen's side.
--
-- WHY TWO NEW COLUMNS RATHER THAN REUSING maintainability_determination
--
--   That column is a two-value HUMAN decision (MAINTAINABLE / NON_MAINTAINABLE) read by Drools
--   compensation-cap rules as `maintainabilityDetermination == "MAINTAINABLE"`. A third value would fall
--   out of every such guard silently. It also carries a determined_by naming a person, while this is a
--   server determination made before any officer sees the file. The codebase already has this precedent:
--   deputy_decision was kept separate from maintainability_determination for the same reason.
--
-- ALSO: scheme_version finally gets a writer. It existed as a column that every reader fell back off,
-- so a complaint could not say which Scheme's rules had been applied to it.
--
-- Both columns are NULLABLE. ddl-auto=update runs against a shared database, so a NOT NULL column here
-- would be permanent for every other session and would break their inserts. NULL means the complaint
-- predates this check; it is NOT backfilled, because inventing a determination for a historical
-- complaint would put a legal conclusion on a record that never had one.
--
-- Re-running is safe: MySQL 8.4 has no ADD COLUMN IF NOT EXISTS, so it is guarded on information_schema.
-- ============================================================

DROP PROCEDURE IF EXISTS s3_cov_add_column;

DELIMITER $$
CREATE PROCEDURE s3_cov_add_column(
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

CALL s3_cov_add_column('COMPLAINTS', 'scheme_coverage_status', 'VARCHAR(20) NULL');
CALL s3_cov_add_column('COMPLAINTS', 'scheme_coverage_reason', 'VARCHAR(500) NULL');

DROP PROCEDURE IF EXISTS s3_cov_add_column;
