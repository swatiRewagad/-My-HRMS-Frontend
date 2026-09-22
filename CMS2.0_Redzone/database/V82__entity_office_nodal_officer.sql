-- V82: ENTITY_OFFICE_NODAL_OFFICER — (regulated entity + Ombudsman office) → NO/PNO (UST773)
-- MySQL version
--
-- ═══ CONTRACT FOR THE ADMIN CRUD SCREEN ═══
-- This table is owned jointly: the resolver (NodalOfficerResolver) reads it, and the Ombudsman admin
-- maintenance screen owns create/update/deactivate of whole rows. Anything building against it must hold
-- to the following, because the resolver's correctness depends on all four points.
--
--   1. LOOKUP KEY IS ENTITY_NAME_NORMALIZED, NOT ENTITY_NAME.
--      COMPLAINTS.ENTITY_CODE stores entity *names* ("State Bank of India"), not short codes, and
--      REGULATED_ENTITIES has no code column. ENTITY_NAME_NORMALIZED must always be written as
--      RegulatedEntity.normalize(ENTITY_NAME) — uppercase, punctuation stripped, whitespace collapsed.
--      The resolver matches it with EQUALITY ONLY, never LIKE. This is the whole safety property: a fuzzy
--      match here would hand one bank's Nodal Officer contacts to a complaint filed against a different
--      bank, which is a cross-entity data leak, not a cosmetic bug. If the screen writes ENTITY_NAME
--      without recomputing ENTITY_NAME_NORMALIZED, the row becomes unreachable and every complaint for
--      that entity silently falls through to the Ombudsman Admin.
--
--   2. PROCESSING_OFFICE IS NULLABLE AND NULL IS MEANINGFUL.
--      NULL means "this contact set serves every office for this entity" — the national-desk case, so a
--      small entity needs one row rather than twenty-two. A non-NULL value must be the office name in
--      OFFICE_CODE_MASTER.OFFICE_NAME form ("Mumbai I", "New Delhi I"), which is what
--      ComplaintNumberGeneratorService.resolveOfficeName returns and what the workflow layer passes as
--      targetOffice. Office CODES ('013') will not match and must not be stored here.
--
--   3. RESOLUTION ORDER IS (entity, office) → (entity, NULL) → REGULATED_ENTITIES → PNO → Ombudsman Admin.
--      A row whose NODAL_OFFICER_NAME is blank does NOT stop the ladder; the resolver treats it as absent
--      and keeps going. So blanking a name is not a way to block resolution — deactivate the row instead.
--
--   4. DEACTIVATE, DO NOT DELETE. ACTIVE = 0 is excluded from resolution. NODAL_OFFICER_RECORDS rows
--      already created keep the contacts they copied, so deactivating here changes future resolutions
--      only; it does not retro-edit complaints already in flight.
--
-- Seeded empty on purpose. Inventing NO/PNO contacts for real banks would put fabricated names and email
-- addresses in front of officers who would then write to them; the rows must come from the admin screen.

CREATE TABLE IF NOT EXISTS ENTITY_OFFICE_NODAL_OFFICER (
    ID                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    ENTITY_NAME               VARCHAR(300) NOT NULL,
    ENTITY_NAME_NORMALIZED    VARCHAR(300) NOT NULL,
    PROCESSING_OFFICE         VARCHAR(100) NULL,
    NODAL_OFFICER_NAME        VARCHAR(200) NULL,
    NODAL_OFFICER_DESIGNATION VARCHAR(100) NULL,
    NODAL_OFFICER_EMAIL       VARCHAR(200) NULL,
    NODAL_OFFICER_PHONE       VARCHAR(20)  NULL,
    PNO_NAME                  VARCHAR(200) NULL,
    PNO_EMAIL                 VARCHAR(200) NULL,
    PNO_PHONE                 VARCHAR(20)  NULL,
    ACTIVE                    TINYINT(1)   NOT NULL DEFAULT 1,
    CREATED_BY                VARCHAR(100) NULL,
    CREATED_AT                DATETIME     NULL,
    UPDATED_AT                DATETIME     NULL
);

DROP PROCEDURE IF EXISTS add_entity_office_no_indexes;

DELIMITER //
CREATE PROCEDURE add_entity_office_no_indexes()
BEGIN
    DECLARE idx_missing INT;

    -- One contact set per (entity, office). Without it, two racing admin edits leave two rows and the
    -- resolver's findFirst picks whichever the optimiser returns — so which officer receives the
    -- complaint would depend on row order, and would change between runs.
    --
    -- MySQL treats NULLs as distinct in a unique index, so this does NOT constrain the entity-wide
    -- (PROCESSING_OFFICE IS NULL) rows to one per entity. That is a known, accepted limit: the resolver
    -- uses findFirst for the NULL-office lookup so a duplicate national row degrades to "an arbitrary one
    -- of two identical desks" rather than an error. Enforcing it would need a generated sentinel column,
    -- which is not worth adding a column that only exists to satisfy an index.
    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ENTITY_OFFICE_NODAL_OFFICER'
                          AND INDEX_NAME = 'uk_eono_entity_office');
    IF idx_missing THEN
        ALTER TABLE ENTITY_OFFICE_NODAL_OFFICER
            ADD CONSTRAINT uk_eono_entity_office UNIQUE (ENTITY_NAME_NORMALIZED, PROCESSING_OFFICE);
    END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ENTITY_OFFICE_NODAL_OFFICER'
                          AND INDEX_NAME = 'idx_eono_entity');
    IF idx_missing THEN
        CREATE INDEX idx_eono_entity ON ENTITY_OFFICE_NODAL_OFFICER(ENTITY_NAME_NORMALIZED);
    END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ENTITY_OFFICE_NODAL_OFFICER'
                          AND INDEX_NAME = 'idx_eono_office');
    IF idx_missing THEN
        CREATE INDEX idx_eono_office ON ENTITY_OFFICE_NODAL_OFFICER(PROCESSING_OFFICE);
    END IF;
END //
DELIMITER ;

CALL add_entity_office_no_indexes();
DROP PROCEDURE add_entity_office_no_indexes;
