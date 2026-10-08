-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V110 — CEPC_CONCILIATION_MEETINGS: other-entities fields
--
-- WHY THIS EXISTS
--
--   The Conciliation tab replaces its free-text "Conciliation Remarks" field with a structured
--   Yes/No — "Want to add other entities" — followed by up to six entity picks (Entity Name 1..6).
--   WANT_OTHER_ENTITIES carries the answer; OTHER_ENTITY_IDS/OTHER_ENTITY_NAMES carry the picks,
--   comma-separated and positionally paired, the same denormalisation COMPLAINTS uses for
--   ENTITY_NAME alongside its regulated-entity id — the ids are authoritative, the names are the
--   display label. A fixed cap of six does not warrant six pairs of columns or a child table.
--
--   CEPC_CONCILIATION_MEETINGS was created by explicit DDL (V103), not by Hibernate ddl-auto, so
--   unlike CEPC_COMPLAINT_ASSESSMENT its name's case is not in question — plain ADD COLUMN suffices.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE CEPC_CONCILIATION_MEETINGS
    ADD COLUMN IF NOT EXISTS WANT_OTHER_ENTITIES BIT(1)        NULL;

ALTER TABLE CEPC_CONCILIATION_MEETINGS
    ADD COLUMN IF NOT EXISTS OTHER_ENTITY_IDS    VARCHAR(200)  NULL;

ALTER TABLE CEPC_CONCILIATION_MEETINGS
    ADD COLUMN IF NOT EXISTS OTHER_ENTITY_NAMES  VARCHAR(1000) NULL;
