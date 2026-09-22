-- V31: AA foundation — Closure Clause Master, Appeal schema expansion, REOPENED support
-- MySQL version. Oracle twin: database/oracle/V29__aa_foundation_clause_master_and_appeal_schema.sql
--
-- Context: the Appellate Authority module could not implement its own user stories.
--
--   1. CLOSURE_CLAUSE_MASTER is new. The closure-clause vocabulary was a hardcoded List<Map> inside
--      WorkflowController.getClosureClauses, so a Scheme amendment required a code release — and the
--      appeal path never read it anyway. Classification (APPEAL vs REPRESENTATION) was taken from a
--      request parameter defaulting to "APPEAL", which meant the browser decided a citizen's
--      statutory recourse.
--
--      Appealability is PARTY-DEPENDENT, hence two boolean columns rather than one. Per the RBI
--      ruling: a complainant may appeal a closure under 15(1)(a) or 15(1)(b); a regulated entity may
--      appeal only under 15(1)(b). Everything else is a representation. Note this SUPERSEDES the old
--      hardcoded list, which also marked 16(2)(c)-(f) "appellable" — a flag no code ever read.
--
--      Only RBIOS_2021 is seeded. RBIOS 2026 clause numbers are deliberately NOT invented: they are
--      pending RBI confirmation, and a guessed clause number in a citizen-facing closure letter is a
--      legal defect. The table is scheme-versioned and date-bounded so 2026 arrives as data, and so a
--      complaint is always judged under the Scheme in force when it was created.
--
--   2. APPEALS gains the Register-milestone intake fields, provenance (createdBy, routedBy, putUpTo)
--      and entity linkage. Without createdBy the "Created By Me" view is impossible; without
--      routedByUserId every "send back to the ORIGINAL officer" story is unimplementable, because
--      send-back currently round-robins to a random holder of the role.
--
--   3. APPEAL_TIMELINE gains fieldName/oldValue/newValue so a classification override records what
--      actually changed. The timeline could only say "status went X to Y", and AuditLog is no better:
--      its previousState/newState are length-50 and status-shaped with no field name.
--
--   4. APPEAL_ATTACHMENTS is new. Appeal documents were written as COMPLAINT_ATTACHMENTS rows with
--      the appeal number in the complaint-number column, mixing an appeal's evidence into its parent
--      complaint's attachment set.
--
--   5. COMPLAINTS.reopened_at + an index on workflow_stage. "Closed or reopened" could not be
--      expressed as a status filter: reopen is recorded only as workflow_stage='REOPENED' and that
--      column was unindexed. Existing CEPC/RBIO reopen behaviour is untouched.
--
-- Re-running is safe: every ALTER is guarded on information_schema and every seed is
-- INSERT ... WHERE NOT EXISTS (MySQL 8.4 has no ADD COLUMN / CREATE INDEX IF NOT EXISTS).

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. CLOSURE_CLAUSE_MASTER
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS CLOSURE_CLAUSE_MASTER (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    scheme_version              VARCHAR(20)  NOT NULL,
    clause_code                 VARCHAR(40)  NOT NULL,
    label                       VARCHAR(300) NOT NULL,
    label_key                   VARCHAR(160) NULL,
    category                    VARCHAR(40)  NOT NULL,
    appealable_by_complainant   TINYINT(1)   NOT NULL DEFAULT 0,
    appealable_by_entity        TINYINT(1)   NOT NULL DEFAULT 0,
    restricted_to_roles         VARCHAR(300) NULL,
    effective_from              DATE         NULL,
    effective_to                DATE         NULL,
    active                      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at                  DATETIME(6)  NULL,
    updated_at                  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- One row per clause per scheme version: a duplicate would make classification ambiguous, which
    -- for a legal determination must be impossible rather than merely unlikely.
    UNIQUE KEY uk_ccm_scheme_clause (scheme_version, clause_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS aa_add_index;
DELIMITER //
CREATE PROCEDURE aa_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL aa_add_index('CLOSURE_CLAUSE_MASTER', 'idx_ccm_scheme', 'scheme_version');
CALL aa_add_index('CLOSURE_CLAUSE_MASTER', 'idx_ccm_active', 'active');

-- RBIOS 2021 clauses. Seeded here as well as in ClosureClauseMasterSeeder so a DB restored without
-- an application boot still classifies correctly.
INSERT INTO CLOSURE_CLAUSE_MASTER
    (scheme_version, clause_code, label, label_key, category,
     appealable_by_complainant, appealable_by_entity, restricted_to_roles, active, created_at, updated_at)
SELECT * FROM (
    SELECT 'RBIOS_2021' AS scheme_version, '15(1)(a)' AS clause_code,
           'Award - full relief granted' AS label, 'clause.15_1_a' AS label_key, 'AWARD' AS category,
           1 AS appealable_by_complainant, 0 AS appealable_by_entity,
           'OMBUDSMAN,RBIO_ADMIN,ADMIN' AS restricted_to_roles, 1 AS active,
           NOW(6) AS created_at, NOW(6) AS updated_at
    UNION ALL SELECT 'RBIOS_2021','15(1)(b)','Award - partial relief with compensation','clause.15_1_b','AWARD',
           1,1,'OMBUDSMAN,RBIO_ADMIN,ADMIN',1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(1)','Resolved to the satisfaction of the complainant','clause.16_1','RESOLUTION',
           0,0,NULL,1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(2)(a)','Not maintainable - time barred','clause.16_2_a','NON_MAINTAINABLE',
           0,0,NULL,1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(2)(b)','Not maintainable - frivolous or vexatious','clause.16_2_b','NON_MAINTAINABLE',
           0,0,NULL,1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(2)(c)','Not maintainable - sub-judice','clause.16_2_c','NON_MAINTAINABLE',
           0,0,'OMBUDSMAN,RBIO_ADMIN,ADMIN',1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(2)(d)','Not maintainable - outside jurisdiction','clause.16_2_d','NON_MAINTAINABLE',
           0,0,'OMBUDSMAN,RBIO_ADMIN,ADMIN',1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(2)(e)','Not maintainable - already settled by RBI','clause.16_2_e','NON_MAINTAINABLE',
           0,0,'OMBUDSMAN,RBIO_ADMIN,ADMIN',1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(2)(f)','Not maintainable - covered by another dispute mechanism','clause.16_2_f','NON_MAINTAINABLE',
           0,0,'OMBUDSMAN,RBIO_ADMIN,ADMIN',1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(2)(g)','Not maintainable - anonymous complaint','clause.16_2_g','NON_MAINTAINABLE',
           0,0,NULL,1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(2)(h)','Not maintainable - insufficient information','clause.16_2_h','NON_MAINTAINABLE',
           0,0,NULL,1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(3)','Closed - complainant not responding','clause.16_3','CLOSURE',
           0,0,NULL,1,NOW(6),NOW(6)
    UNION ALL SELECT 'RBIOS_2021','16(4)','Closed - matter settled between the parties','clause.16_4','CLOSURE',
           0,0,NULL,1,NOW(6),NOW(6)
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM CLOSURE_CLAUSE_MASTER existing
     WHERE existing.scheme_version = seed.scheme_version
       AND existing.clause_code    = seed.clause_code
);

-- Corrective UPDATE, scoped BY CLAUSE CODE. The seeder is insert-if-absent, so a database seeded
-- before the party-dependent ruling would still carry a single-flag interpretation. Only the award
-- clauses are appealable, and only 15(1)(b) is appealable by an entity.
UPDATE CLOSURE_CLAUSE_MASTER
   SET appealable_by_complainant = 1, appealable_by_entity = 0, updated_at = NOW(6)
 WHERE scheme_version = 'RBIOS_2021' AND clause_code = '15(1)(a)'
   AND (appealable_by_complainant <> 1 OR appealable_by_entity <> 0);

UPDATE CLOSURE_CLAUSE_MASTER
   SET appealable_by_complainant = 1, appealable_by_entity = 1, updated_at = NOW(6)
 WHERE scheme_version = 'RBIOS_2021' AND clause_code = '15(1)(b)'
   AND (appealable_by_complainant <> 1 OR appealable_by_entity <> 1);

UPDATE CLOSURE_CLAUSE_MASTER
   SET appealable_by_complainant = 0, appealable_by_entity = 0, updated_at = NOW(6)
 WHERE scheme_version = 'RBIOS_2021'
   AND clause_code NOT IN ('15(1)(a)', '15(1)(b)')
   AND (appealable_by_complainant <> 0 OR appealable_by_entity <> 0);

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. APPEALS — intake, provenance, entity linkage, override audit
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS aa_add_column;
DELIMITER //
CREATE PROCEDURE aa_add_column(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_type VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_col, ' ', p_type);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- Classification override audit
CALL aa_add_column('APPEALS', 'classification_overridden',        'TINYINT(1) NOT NULL DEFAULT 0');
CALL aa_add_column('APPEALS', 'classification_override_reason',   'VARCHAR(1000) NULL');
CALL aa_add_column('APPEALS', 'classification_overridden_by',     'VARCHAR(200) NULL');
CALL aa_add_column('APPEALS', 'classification_overridden_at',     'DATETIME(6) NULL');

-- Closure clause, resolved from CLOSURE_CLAUSE_MASTER
CALL aa_add_column('APPEALS', 'closure_clause',                   'VARCHAR(40) NULL');

-- Provenance. NULL on historical rows: they genuinely have no recorded creator, and inventing one
-- would be worse than an honest blank in the "Created By Me" view.
CALL aa_add_column('APPEALS', 'created_by',                       'VARCHAR(200) NULL');
CALL aa_add_column('APPEALS', 'created_by_role',                  'VARCHAR(50) NULL');
CALL aa_add_column('APPEALS', 'routed_by_user_id',                'VARCHAR(200) NULL');
CALL aa_add_column('APPEALS', 'routed_by_role',                   'VARCHAR(50) NULL');
CALL aa_add_column('APPEALS', 'put_up_to_user_id',                'VARCHAR(200) NULL');

-- Entity linkage
CALL aa_add_column('APPEALS', 'entity_code',                      'VARCHAR(100) NULL');
CALL aa_add_column('APPEALS', 'resolved_pno_user_id',             'VARCHAR(200) NULL');

-- Register-milestone intake
CALL aa_add_column('APPEALS', 'appeal_filed_by',                  'VARCHAR(30) NULL');
CALL aa_add_column('APPEALS', 'source_of_appeal',                 'VARCHAR(60) NULL');
CALL aa_add_column('APPEALS', 'mode_of_receipt',                  'VARCHAR(30) NULL');
CALL aa_add_column('APPEALS', 'appellant_address1',               'VARCHAR(300) NULL');
CALL aa_add_column('APPEALS', 'appellant_address2',               'VARCHAR(300) NULL');
CALL aa_add_column('APPEALS', 'appellant_city',                   'VARCHAR(100) NULL');
CALL aa_add_column('APPEALS', 'appellant_district',               'VARCHAR(100) NULL');
CALL aa_add_column('APPEALS', 'appellant_state',                  'VARCHAR(100) NULL');
CALL aa_add_column('APPEALS', 'appellant_country',                'VARCHAR(100) NULL');
CALL aa_add_column('APPEALS', 'appellant_pincode',                'VARCHAR(10) NULL');
CALL aa_add_column('APPEALS', 'category_id',                      'BIGINT NULL');
CALL aa_add_column('APPEALS', 'entity_name',                      'VARCHAR(300) NULL');
CALL aa_add_column('APPEALS', 'entity_region',                    'VARCHAR(100) NULL');
CALL aa_add_column('APPEALS', 'entity_category',                  'VARCHAR(100) NULL');
CALL aa_add_column('APPEALS', 'entity_branch',                    'VARCHAR(200) NULL');
CALL aa_add_column('APPEALS', 'bsr_ifsc_code',                    'VARCHAR(40) NULL');
CALL aa_add_column('APPEALS', 'account_number',                   'VARCHAR(60) NULL');
CALL aa_add_column('APPEALS', 'card_number',                      'VARCHAR(60) NULL');
CALL aa_add_column('APPEALS', 'nodal_officer_name',               'VARCHAR(200) NULL');
-- Nullable Boolean, not NOT NULL DEFAULT 0: "not yet answered" and "answered No" are different, and
-- the Register milestone must be able to block on an unanswered mandatory checkbox.
CALL aa_add_column('APPEALS', 'is_complainant_advocate',          'TINYINT(1) NULL');
CALL aa_add_column('APPEALS', 'has_related_court_trial',          'TINYINT(1) NULL');

-- Regulated-entity sign-off for PNO-created appeals
CALL aa_add_column('APPEALS', 'ed_approval_given',                'TINYINT(1) NULL');
CALL aa_add_column('APPEALS', 'ed_approval_date',                 'DATETIME(6) NULL');
CALL aa_add_column('APPEALS', 'ed_approval_comments',             'VARCHAR(2000) NULL');

CALL aa_add_index('APPEALS', 'idx_appeal_classification', 'classification_type');
CALL aa_add_index('APPEALS', 'idx_appeal_created_by',     'created_by');
CALL aa_add_index('APPEALS', 'idx_appeal_entity_code',    'entity_code');

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. APPEAL_TIMELINE — field-level audit
-- ═══════════════════════════════════════════════════════════════════════════
CALL aa_add_column('APPEAL_TIMELINE', 'field_name', 'VARCHAR(60) NULL');
CALL aa_add_column('APPEAL_TIMELINE', 'old_value',  'TEXT NULL');
CALL aa_add_column('APPEAL_TIMELINE', 'new_value',  'TEXT NULL');

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. APPEAL_ATTACHMENTS
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS APPEAL_ATTACHMENTS (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    appeal_number   VARCHAR(50)   NOT NULL,
    file_name       VARCHAR(400)  NOT NULL,
    content_type    VARCHAR(100)  NULL,
    file_size       BIGINT        NULL,
    storage_path    VARCHAR(1000) NOT NULL,
    document_type   VARCHAR(40)   NULL,
    source_draft_id VARCHAR(60)   NULL,
    uploaded_by     VARCHAR(200)  NULL,
    uploaded_at     DATETIME(6)   NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL aa_add_index('APPEAL_ATTACHMENTS', 'idx_appeal_att_number', 'appeal_number');
CALL aa_add_index('APPEAL_ATTACHMENTS', 'idx_appeal_att_draft',  'source_draft_id');

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. COMPLAINTS — make "closed or reopened" answerable
-- ═══════════════════════════════════════════════════════════════════════════
-- Reopen is recorded as workflow_stage='REOPENED' (CepcWorkflowService), not as a status, so the AA
-- parent-complaint search must filter on it. The column was unindexed. reopened_at is added as an
-- explicit timestamp: last_reopened_at exists but is only set by the CEPC path.
CALL aa_add_column('COMPLAINTS', 'reopened_at', 'DATETIME(6) NULL');
CALL aa_add_index('COMPLAINTS', 'idx_complaint_workflow_stage', 'workflow_stage');
CALL aa_add_index('COMPLAINTS', 'idx_complaint_closure_clause', 'closure_clause');

-- Backfill from the CEPC/RBIO reopen path so existing reopened complaints are findable.
UPDATE COMPLAINTS
   SET reopened_at = last_reopened_at
 WHERE reopened_at IS NULL
   AND last_reopened_at IS NOT NULL;

DROP PROCEDURE IF EXISTS aa_add_column;
DROP PROCEDURE IF EXISTS aa_add_index;
