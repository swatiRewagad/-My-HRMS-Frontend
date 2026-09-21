-- V25: Denormalised officer columns on COMPLAINTS and WF_OFFICER_POOL (MySQL)
--
-- These columns are already mapped by the entities and already written by the application, but no
-- migration ever added them: they only exist in dev-local, where ddl-auto=update created them
-- silently. Prod runs ddl-auto=validate, so without this script the service fails to start.
-- database/seed_rbio_full_lifecycle.sql already inserts into all four COMPLAINTS columns, which is
-- how the gap went unnoticed.
--
-- COMPLAINTS.ASSIGNED_OFFICER_NAME duplicates a name that WF_OFFICER_POOL.DISPLAY_NAME also holds,
-- deliberately. The dashboard grid is served from OpenSearch, not from a join: the indexer copies the
-- complaint row as-is, so a name that is not on the row cannot be displayed or sorted on at all.
--
-- COMPLAINTS.REGIONAL_OFFICE is VARCHAR(100) to match WF_OFFICER_POOL.REGIONAL_OFFICE, which is where
-- the value is copied from. It is also the field OfficerScopePolicy filters every officer's search on,
-- so a complaint with a NULL here is invisible to scoped searches.
--
-- HAS_ATTACHMENT is NOT NULL with a default rather than nullable: the search service reads it as a
-- primitive boolean filter ("Without Attachments"), and a NULL would silently drop the row from both
-- sides of that filter. Existing rows become FALSE, which is wrong for any complaint that does have
-- an attachment — a corrective UPDATE from COMPLAINT_ATTACHMENTS is left to the data team, since only
-- they know which attachment rows are live.
--
-- WF_OFFICER_POOL gains only DEPARTMENT. REGIONAL_OFFICE already exists and must not be added again.
-- VARCHAR(255) matches the entity, which declares no length and so takes Hibernate's default — unlike
-- its VARCHAR(100) namesake on COMPLAINTS.
--
-- Complaint.readBy is intentionally absent from this script. It is a @Transient field populated only
-- on the reindex stream from COMPLAINT_READ_RECEIPTS (V24); it is not a column and must not become one.
--
-- Oracle equivalent: database/oracle/V25__denormalised_officer_columns.sql
-- MySQL has no ADD COLUMN IF NOT EXISTS, so re-running fails with error 1060 (duplicate column name).

ALTER TABLE COMPLAINTS
    ADD COLUMN ASSIGNED_OFFICER_NAME  VARCHAR(250)  NULL,
    ADD COLUMN REGIONAL_OFFICE        VARCHAR(100)  NULL,
    ADD COLUMN IS_READ                TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN HAS_ATTACHMENT         TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN CREATED_BY             VARCHAR(200)  NULL;

-- Scoped searches filter on REGIONAL_OFFICE for every officer on every page, so it is never a scan.
CREATE INDEX IDX_COMPLAINTS_REGIONAL_OFFICE ON COMPLAINTS (REGIONAL_OFFICE);

ALTER TABLE WF_OFFICER_POOL
    ADD COLUMN DEPARTMENT  VARCHAR(255)  NULL;

-- USER_ID is the lookup key for resolving a username to a name or an office on every complaint
-- assignment and every timeline write; the existing index covers only (ROLE_GROUP, IS_ACTIVE).
CREATE INDEX IDX_WF_POOL_USER_ID ON WF_OFFICER_POOL (USER_ID);
