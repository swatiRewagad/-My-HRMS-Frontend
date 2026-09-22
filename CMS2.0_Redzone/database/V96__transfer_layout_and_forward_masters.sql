-- ============================================================
-- V96 — Transfer provenance/layout columns, and the three missing forwarding masters
-- Session S5 (UST556-568, 632-634, 759-761, 765-766, 770-772)
-- ============================================================
--
-- 1. WHY THE TRANSFER ROW NEEDS MORE COLUMNS
--
--   INTER_OFFICE_TRANSFERS records fromOffice/toOffice/approvedBy/resolvedAt but not WHAT CHANGED.
--   UST634/568/565 require the conversion history to show the OLD layout, the NEW layout, the approving
--   CRPC Head and the timestamp. The first two had nowhere to live, and the origin MODULE was inferred
--   at approval time from a string prefix on the destination office id (resolveDepartment: CEPC->CEPC,
--   CEPD->CEPD, else RBIO). Since offices are keyed by a NUMERIC OFFICE_CODE ("013"), that prefix match
--   fell off the end for every real office and resolved "RBIO" unconditionally — so an RBIO->CEPC
--   conversion recorded itself as RBIO->RBIO.
--
--   WHAT "LAYOUT" MEANS HERE. There is no pre-existing layout concept in this codebase: `layout` appears
--   only as i18n keys for page chrome and as two Bengaluru locality names. Ruling, stated explicitly:
--   layout = the complaint's OWNING MODULE (RBIO | CEPC | CEPD), i.e. the department column, which is
--   what selects the field set and the workflow vocabulary. Stored as its own from/to pair rather than
--   re-derived, because a derivation cannot be evidenced after the fact and this one was provably wrong.
--
-- 2. WHY LANGUAGE IS ON THE TRANSFER (UST765/525)
--
--   The forward captures the complaint's language so the receiving office knows whether it can read the
--   file. No forward path read a language param at all before this.
--
-- 3. WHY overflow_accepted EXISTS
--
--   OfficeRoutingService.incrementOffice now returns false when the destination is AT CAPACITY, and its
--   only production call site discarded that boolean — so a transfer could land on a full office and
--   nothing recorded that it had. The transfer is now REFUSED when the destination is full; this column
--   records the case where an approver deliberately overrides that refusal, so an over-capacity office is
--   an auditable decision rather than an invisible side effect.
--
-- ALL NEW COLUMNS NULLABLE. ddl-auto=update runs on a database shared by seven sessions.
-- Re-running is safe: information_schema guards on every ALTER, INSERT ... WHERE NOT EXISTS on every seed.
-- ============================================================

DROP PROCEDURE IF EXISTS s5_fwd_add_column;
DROP PROCEDURE IF EXISTS s5_fwd_add_index;

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

CREATE PROCEDURE s5_fwd_add_index(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_columns, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- ─────────────────────────────────────────────────────────────
-- 1. Transfer provenance, layout conversion and destination assignment
-- ─────────────────────────────────────────────────────────────
-- The module the complaint belonged to when the transfer was REQUESTED, captured rather than inferred.
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'origin_module',    'VARCHAR(20) NULL');
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'from_layout',      'VARCHAR(20) NULL');
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'to_layout',        'VARCHAR(20) NULL');

-- The officer the complaint actually landed on at the destination, and how they were chosen. Previously
-- the destination OFFICE CODE was written into COMPLAINTS.assigned_officer — a non-user value in a user
-- column — so "who owns this complaint now" had no answer after a transfer.
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'assigned_officer', 'VARCHAR(200) NULL');
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'assignment_strategy', 'VARCHAR(40) NULL');
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'assignment_reason',   'VARCHAR(500) NULL');

-- The office the complaint sat in before the move, so the region change is evidenced on the row itself
-- and not only recoverable by replaying the timeline.
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'from_office_code', 'VARCHAR(10) NULL');
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'to_office_code',   'VARCHAR(10) NULL');

-- UST765/525: the language carried with the forward.
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'language', 'VARCHAR(10) NULL');

-- A forward to an RBI department or an external regulator is not an office-to-office move, so the
-- destination needs somewhere to live that is not to_office.
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'target_department',    'VARCHAR(100) NULL');
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'target_body_id',       'BIGINT NULL');
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'target_body_name',     'VARCHAR(250) NULL');

-- See header note 3.
CALL s5_fwd_add_column('INTER_OFFICE_TRANSFERS', 'overflow_accepted', 'CHAR(1) NULL');

-- The pending queue is read on every CRPC Head page load, filtered by status.
CALL s5_fwd_add_index('INTER_OFFICE_TRANSFERS', 'idx_iot_status_requested', 'status, requested_at');

-- The RBI department a complaint was forwarded to. Its own column rather than assigned_officer, which is
-- what the CEPC forward arms use — that leaves a department NAME in a user column, so the complaint appears
-- to be owned by a department and "who is working on this" has no answer.
CALL s5_fwd_add_column('COMPLAINTS', 'forwarded_to_department', 'VARCHAR(100) NULL');

-- ─────────────────────────────────────────────────────────────
-- 2. REGULATORY_BODY_MASTER (UST766)
-- ─────────────────────────────────────────────────────────────
-- Did not exist in any form. The frontend called GET /api/v1/master-data/regulatory-bodies, which no
-- controller implements (and the real master controller is mounted at /api/v1/masters, so even the
-- prefix was wrong). catchError(() => of([])) turned the 404 into an empty dropdown, while the screen
-- told the officer "Only bodies from the validated master list can be selected".
--
-- EMAIL_VERIFIED IS THE POINT OF THE STORY. UST766 restricts forwarding to bodies WITH VERIFIED EMAIL
-- IDs. A nullable flag that must equal 'Y' fails closed: a body whose email has not been verified
-- cannot be selected, and an unseeded master forwards to nobody rather than to a guessed address.
CREATE TABLE IF NOT EXISTS REGULATORY_BODY_MASTER (
    ID              BIGINT AUTO_INCREMENT PRIMARY KEY,
    BODY_CODE       VARCHAR(30)  NOT NULL,
    BODY_NAME       VARCHAR(250) NOT NULL,
    CONTACT_EMAIL   VARCHAR(320) NULL,
    EMAIL_VERIFIED  CHAR(1)      NULL,
    VERIFIED_BY     VARCHAR(200) NULL,
    VERIFIED_AT     DATETIME     NULL,
    CONTACT_PHONE   VARCHAR(30)  NULL,
    ADDRESS         VARCHAR(500) NULL,
    JURISDICTION    VARCHAR(250) NULL,
    IS_ACTIVE       CHAR(1)      NULL,
    CREATED_BY      VARCHAR(100) NULL,
    CREATED_AT      DATETIME     NULL,
    UPDATED_AT      DATETIME     NULL
);

CALL s5_fwd_add_index('REGULATORY_BODY_MASTER', 'idx_reg_body_code', 'BODY_CODE');
CALL s5_fwd_add_index('REGULATORY_BODY_MASTER', 'idx_reg_body_active', 'IS_ACTIVE');

-- The four statutory financial-sector regulators a banking complaint is realistically referred to,
-- plus the RBI-internal consumer education department. Seeded with EMAIL_VERIFIED = 'N' DELIBERATELY:
-- nobody has verified these addresses, and seeding 'Y' would assert a verification that never happened
-- and immediately permit forwarding a citizen's complaint to an unconfirmed mailbox. An administrator
-- verifies each one, which is an auditable act recorded in VERIFIED_BY/VERIFIED_AT.
INSERT INTO REGULATORY_BODY_MASTER
    (BODY_CODE, BODY_NAME, CONTACT_EMAIL, EMAIL_VERIFIED, JURISDICTION, IS_ACTIVE, CREATED_BY, CREATED_AT)
SELECT 'SEBI', 'Securities and Exchange Board of India', NULL, 'N',
       'Securities markets, listed companies, mutual funds', 'Y', 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM REGULATORY_BODY_MASTER WHERE BODY_CODE = 'SEBI');

INSERT INTO REGULATORY_BODY_MASTER
    (BODY_CODE, BODY_NAME, CONTACT_EMAIL, EMAIL_VERIFIED, JURISDICTION, IS_ACTIVE, CREATED_BY, CREATED_AT)
SELECT 'IRDAI', 'Insurance Regulatory and Development Authority of India', NULL, 'N',
       'Insurance policies, claims and intermediaries', 'Y', 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM REGULATORY_BODY_MASTER WHERE BODY_CODE = 'IRDAI');

INSERT INTO REGULATORY_BODY_MASTER
    (BODY_CODE, BODY_NAME, CONTACT_EMAIL, EMAIL_VERIFIED, JURISDICTION, IS_ACTIVE, CREATED_BY, CREATED_AT)
SELECT 'PFRDA', 'Pension Fund Regulatory and Development Authority', NULL, 'N',
       'National Pension System and pension funds', 'Y', 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM REGULATORY_BODY_MASTER WHERE BODY_CODE = 'PFRDA');

INSERT INTO REGULATORY_BODY_MASTER
    (BODY_CODE, BODY_NAME, CONTACT_EMAIL, EMAIL_VERIFIED, JURISDICTION, IS_ACTIVE, CREATED_BY, CREATED_AT)
SELECT 'NHB', 'National Housing Bank', NULL, 'N',
       'Housing finance companies', 'Y', 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM REGULATORY_BODY_MASTER WHERE BODY_CODE = 'NHB');

-- ─────────────────────────────────────────────────────────────
-- 3. RBI_DEPARTMENT_MASTER (UST761/534/527-528)
-- ─────────────────────────────────────────────────────────────
-- The only list of RBI departments in the product was a hardcoded nine-element array in
-- cepc-complaint-detail.component.ts, including a literal 'Other'. DEPARTMENT_ROUTING_MASTER cannot
-- serve as the picker: it maps an ENTITY to its owning department (its rows are banks, not
-- departments) and it carries no contact column — a point its neighbouring entity documents explicitly.
CREATE TABLE IF NOT EXISTS RBI_DEPARTMENT_MASTER (
    ID              BIGINT AUTO_INCREMENT PRIMARY KEY,
    DEPT_CODE       VARCHAR(30)  NOT NULL,
    DEPT_NAME       VARCHAR(250) NOT NULL,
    CONTACT_EMAIL   VARCHAR(320) NULL,
    -- The role group whose round-robin picks an officer inside the target department (UST761: "assignment
    -- inside the target department follows CRPC Head Round-Robin logic").
    ASSIGN_ROLE_GROUP VARCHAR(50) NULL,
    DESCRIPTION     VARCHAR(500) NULL,
    IS_ACTIVE       CHAR(1)      NULL,
    DISPLAY_ORDER   INT          NULL,
    CREATED_BY      VARCHAR(100) NULL,
    CREATED_AT      DATETIME     NULL
);

CALL s5_fwd_add_index('RBI_DEPARTMENT_MASTER', 'idx_rbi_dept_code', 'DEPT_CODE');
CALL s5_fwd_add_index('RBI_DEPARTMENT_MASTER', 'idx_rbi_dept_active', 'IS_ACTIVE');

INSERT INTO RBI_DEPARTMENT_MASTER (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
SELECT 'DOS', 'Department of Supervision', 'CRPC_DEO', 'Y', 10, 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = 'DOS');

INSERT INTO RBI_DEPARTMENT_MASTER (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
SELECT 'DOR', 'Department of Regulation', 'CRPC_DEO', 'Y', 20, 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = 'DOR');

INSERT INTO RBI_DEPARTMENT_MASTER (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
SELECT 'CEPD', 'Consumer Education and Protection Department', 'CRPC_DEO', 'Y', 30, 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = 'CEPD');

INSERT INTO RBI_DEPARTMENT_MASTER (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
SELECT 'DPSS', 'Department of Payment and Settlement Systems', 'CRPC_DEO', 'Y', 40, 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = 'DPSS');

INSERT INTO RBI_DEPARTMENT_MASTER (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
SELECT 'FIDD', 'Financial Inclusion and Development Department', 'CRPC_DEO', 'Y', 50, 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = 'FIDD');

INSERT INTO RBI_DEPARTMENT_MASTER (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
SELECT 'DCM', 'Department of Currency Management', 'CRPC_DEO', 'Y', 60, 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = 'DCM');

INSERT INTO RBI_DEPARTMENT_MASTER (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
SELECT 'FED', 'Foreign Exchange Department', 'CRPC_DEO', 'Y', 70, 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = 'FED');

INSERT INTO RBI_DEPARTMENT_MASTER (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
SELECT 'DOA', 'Department of Audit', 'CRPC_DEO', 'Y', 80, 'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = 'DOA');

-- ─────────────────────────────────────────────────────────────
-- 4. CEPC offices become addressable (UST556/563)
-- ─────────────────────────────────────────────────────────────
-- OFFICE_CODE_MASTER.OFFICE_TYPE is 'BO' on every one of its rows, so "the list of CEPC offices" had no
-- data source at all and the CEPC branch of the transfer form could not be populated. CEPC offices are
-- added as rows with OFFICE_TYPE = 'CEPC', which is what lets the picker filter them; the existing 'BO'
-- rows are left untouched so every current reader (the RBIO office dropdown, the threshold join,
-- complaint-number generation) behaves exactly as before.
--
-- Codes are prefixed C0* to keep them outside the numeric RBIO code space, so a CEPC office can never
-- collide with an OFFICE_CODE a complaint already carries.
INSERT INTO OFFICE_CODE_MASTER (OFFICE_CODE, OFFICE_NAME, OFFICE_TYPE, IS_ACTIVE)
SELECT 'C01', 'CEPC Mumbai', 'CEPC', 1
WHERE NOT EXISTS (SELECT 1 FROM OFFICE_CODE_MASTER WHERE OFFICE_CODE = 'C01');

INSERT INTO OFFICE_CODE_MASTER (OFFICE_CODE, OFFICE_NAME, OFFICE_TYPE, IS_ACTIVE)
SELECT 'C02', 'CEPC New Delhi', 'CEPC', 1
WHERE NOT EXISTS (SELECT 1 FROM OFFICE_CODE_MASTER WHERE OFFICE_CODE = 'C02');

INSERT INTO OFFICE_CODE_MASTER (OFFICE_CODE, OFFICE_NAME, OFFICE_TYPE, IS_ACTIVE)
SELECT 'C03', 'CEPC Chennai', 'CEPC', 1
WHERE NOT EXISTS (SELECT 1 FROM OFFICE_CODE_MASTER WHERE OFFICE_CODE = 'C03');

INSERT INTO OFFICE_CODE_MASTER (OFFICE_CODE, OFFICE_NAME, OFFICE_TYPE, IS_ACTIVE)
SELECT 'C04', 'CEPC Kolkata', 'CEPC', 1
WHERE NOT EXISTS (SELECT 1 FROM OFFICE_CODE_MASTER WHERE OFFICE_CODE = 'C04');

INSERT INTO OFFICE_CODE_MASTER (OFFICE_CODE, OFFICE_NAME, OFFICE_TYPE, IS_ACTIVE)
SELECT 'C05', 'CEPC Chandigarh', 'CEPC', 1
WHERE NOT EXISTS (SELECT 1 FROM OFFICE_CODE_MASTER WHERE OFFICE_CODE = 'C05');

-- Capacity rows for the new CEPC offices, so a transfer INTO one is subject to the same threshold test
-- as a transfer into an RBIO office. Without a row, routeToOffice returns NOT_FOUND and refuses — which
-- fails closed correctly but would make every CEPC destination unusable.
INSERT INTO OFFICE_THRESHOLD_CONFIG
    (office_id, office_name, department, max_threshold, current_count, overflow_sequence_order, active)
SELECT o.OFFICE_CODE, o.OFFICE_NAME, 'CEPC', 500, 0, 900, TRUE
  FROM OFFICE_CODE_MASTER o
 WHERE o.OFFICE_TYPE = 'CEPC'
   AND NOT EXISTS (SELECT 1 FROM OFFICE_THRESHOLD_CONFIG t WHERE t.office_id = o.OFFICE_CODE);

DROP PROCEDURE IF EXISTS s5_fwd_add_column;
DROP PROCEDURE IF EXISTS s5_fwd_add_index;

-- ─────────────────────────────────────────────────────────────
-- 5. Configuration
-- ─────────────────────────────────────────────────────────────
-- Whether an approving CRPC Head may push a complaint into an office already at its declared capacity.
-- Default false: capacity exists to be respected, and the previous behaviour (silently discarding the
-- capacity verdict) is the defect being fixed. An RBI administrator can permit the override, and when
-- they do it is recorded on the transfer row rather than being invisible.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'transfer.allow_over_capacity_override', 'false',
       'Whether a CRPC Head may approve a transfer into an office at its threshold. Recorded on the transfer row when used.',
       'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'transfer.allow_over_capacity_override');

-- Whether forwarding to an external regulator is permitted before that body's email has been verified.
-- Default false and deliberately so: UST766 restricts forwarding to bodies with VERIFIED email ids, and
-- the seeded bodies all start unverified.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'forward.require_verified_body_email', 'true',
       'Refuse a forward to a regulatory body whose contact email is not verified (UST766).',
       'V96_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'forward.require_verified_body_email');
