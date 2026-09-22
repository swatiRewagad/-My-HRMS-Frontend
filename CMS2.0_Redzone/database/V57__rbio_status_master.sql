-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V57 — RBIO_STATUS_MASTER + per-role visibility (Wave 0 foundation)
--
-- Re-runnable. MySQL 8.4 has no IF NOT EXISTS for ADD COLUMN or CREATE INDEX, so DDL is guarded
-- through information_schema. Procedure prefix is w0_ — distinct from aa_ (V31), s2c_ (V46) and
-- s3a_ (V51), each of which drops its own procedures at end of file.
--
-- WHY THIS TABLE EXISTS
-- RBIO writes ~13 lowercase status literals directly from Java switch arms. The ComplaintStatus enum
-- in cms-common (11 UPPERCASE values) is bypassed entirely, and no STATUS_MASTER exists. The
-- consequence is that every screen that needs "the list of statuses a Reviewer may filter by" has to
-- hardcode an array, and five such screens hardcode five different arrays. This table is the single
-- vocabulary; UST426-433 filter lists are driven from RBIO_STATUS_ROLE_VISIBILITY, never from a
-- literal in a component.
--
-- THE CRITICAL MODELLING POINT: the ~25 "statuses" in the backlog are NOT all statuses. They are
-- three different kinds of thing, and treating them uniformly is why the existing screens disagree:
--   STATUS — a value COMPLAINTS.status actually takes  ("Complaint Closed", "Award Passed")
--   QUEUE  — a routing position, i.e. status + assigned_role together ("Sent to Reviewer")
--   SCOPE  — not a status at all, but a predicate on the CALLER ("Complaint Assigned to Me")
-- FILTER_KIND records which, so the list endpoint knows whether a filter value constrains `status`,
-- constrains status+role, or constrains the caller. A SCOPE row has no legacy_value and never appears
-- in COMPLAINTS.status; asking the DB for complaints WHERE status='ALL' would return nothing, which is
-- exactly the class of silent-empty-grid bug this column prevents.
--
-- BACKWARD COMPATIBILITY: legacy_value holds the lowercase string the code writes TODAY. It is the
-- join key from live data to this vocabulary. Nothing in this migration rewrites COMPLAINTS.status —
-- a data migration of a legally-significant column is not something to bundle into a foundation
-- change, and the mapping here makes it unnecessary.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS w0_add_index;
DELIMITER //
CREATE PROCEDURE w0_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. RBIO_STATUS_MASTER — the status vocabulary
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS RBIO_STATUS_MASTER (
    STATUS_CODE     VARCHAR(50)  NOT NULL,

    -- The lowercase string written to COMPLAINTS.status today. NULL for QUEUE/SCOPE rows and for
    -- statuses the backlog asks for but no code yet writes. NOT unique: several codes deliberately
    -- share a legacy value because the legacy vocabulary is coarser than the backlog's.
    LEGACY_VALUE    VARCHAR(30),

    -- STATUS | QUEUE | SCOPE — see the header. Determines how the list endpoint applies the filter.
    FILTER_KIND     VARCHAR(10)  NOT NULL DEFAULT 'STATUS',

    LABEL_EN        VARCHAR(150) NOT NULL,
    -- i18n key. Seeded by a Java seeder, not here, so the 9 locales stay in one place.
    TRANSLATION_KEY VARCHAR(150),

    MILESTONE_CODE  VARCHAR(30),

    -- For QUEUE rows: the role whose inbox this queue IS. Lets "Sent to Reviewer" resolve to
    -- (status, assigned_role) without a second lookup table.
    QUEUE_ROLE      VARCHAR(50),

    -- IS_CLOSED is the authoritative answer to "does this status mean the file is shut?", replacing
    -- the hardcoded CLOSED_STATUSES lists. IS_TERMINAL is narrower: closed AND not reopenable.
    IS_CLOSED       CHAR(1)      NOT NULL DEFAULT 'N',
    IS_TERMINAL     CHAR(1)      NOT NULL DEFAULT 'N',
    -- Whether a citizen may see this status verbatim. Internal routing states ("Sent Back to DO")
    -- disclose RBI's internal disagreement about a case and must not surface on the tracker.
    IS_CITIZEN_VISIBLE CHAR(1)   NOT NULL DEFAULT 'N',

    DISPLAY_ORDER   INT          NOT NULL DEFAULT 999,
    IS_ACTIVE       CHAR(1)      NOT NULL DEFAULT 'Y',
    SCHEME_VERSION  VARCHAR(20)  NOT NULL DEFAULT 'RBIOS_2021',
    CREATED_AT      DATETIME(6),
    PRIMARY KEY (STATUS_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL w0_add_index('RBIO_STATUS_MASTER', 'IDX_RBIO_STATUS_LEGACY', 'LEGACY_VALUE');
CALL w0_add_index('RBIO_STATUS_MASTER', 'IDX_RBIO_STATUS_KIND', 'FILTER_KIND, DISPLAY_ORDER');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. RBIO_STATUS_ROLE_VISIBILITY — which filters each role sees (UST426-433)
--
-- A child table rather than a column-per-role. Nine roles would mean nine boolean columns and a
-- schema change every time a rank is added; more importantly DISPLAY_ORDER is per-role — a Dealing
-- Official and an Ombudsman want the same statuses in a different order — which a single shared
-- column on the master cannot express.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS RBIO_STATUS_ROLE_VISIBILITY (
    ID            BIGINT       NOT NULL AUTO_INCREMENT,
    STATUS_CODE   VARCHAR(50)  NOT NULL,
    ROLE_NAME     VARCHAR(50)  NOT NULL,
    DISPLAY_ORDER INT          NOT NULL DEFAULT 999,
    -- The tab this role lands on when it opens the list with no filter chosen. At most one per role;
    -- not enforced by a constraint because "at most one" is not expressible as a unique key, so the
    -- seeder asserts it and the service falls back to lowest DISPLAY_ORDER.
    IS_DEFAULT    CHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_RBIO_STATUS_ROLE (STATUS_CODE, ROLE_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL w0_add_index('RBIO_STATUS_ROLE_VISIBILITY', 'IDX_RBIO_STATUS_VIS_ROLE', 'ROLE_NAME, DISPLAY_ORDER');

DROP PROCEDURE IF EXISTS w0_add_index;
