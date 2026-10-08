-- ═══════════════════════════════════════════════════════════════════════════
-- V108 — COMPLAINTS gains display columns and a CEPC office scope
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Seven columns, in two groups.
--
-- Display copies (ENTITY_NAME, CATEGORY_NAME, ASSIGNED_OFFICER_NAME): the row already
-- carries ENTITY_CODE, CATEGORY_ID and ASSIGNED_OFFICER, but those are codes and ids.
-- The dashboard grid sorts and column-filters on the name the officer reads, and doing
-- that through a join to each master makes every listing query fan out over three more
-- tables. The copies are written at intake and on reassignment.
--
-- REGIONAL_OFFICE is the CEPC jurisdiction scope, applied on every listing request
-- alongside DEPARTMENT. It is deliberately NOT RBIO_OFFICE_CODE: that column is the RBIO
-- intake office parsed out of the complaint number and is NULL on every CEPC complaint,
-- so it cannot carry CEPC scoping.
--
-- IS_READ and HAS_ATTACHMENT are NOT NULL with DEFAULT 0 so the ALTER succeeds against
-- existing rows without a separate backfill. IS_READ is "has anyone opened this", which
-- is NOT the dashboard's unread filter — that one is per user and stays answered by
-- COMPLAINT_READ_STATE, because one boolean on a shared row cannot be per user.
--
-- Table name is lowercase and unquoted on purpose. MySQL deployments of this schema run
-- with lower_case_table_names=0, where the Hibernate-created table is `complaints` and
-- an uppercase COMPLAINTS in DDL resolves to a different, non-existent table.

ALTER TABLE complaints
    ADD COLUMN entity_name VARCHAR(300) NULL COMMENT
        'Regulated entity name, copied from the entity master at intake so the grid can sort on it without a join.';

ALTER TABLE complaints
    ADD COLUMN category_name VARCHAR(200) NULL COMMENT
        'Complaint category name, copied from COMPLAINT_CATEGORY. CATEGORY_ID remains the authoritative link.';

ALTER TABLE complaints
    ADD COLUMN assigned_officer_name VARCHAR(250) NULL COMMENT
        'Display name of the officer in ASSIGNED_OFFICER. Rewritten whenever the assignment changes.';

ALTER TABLE complaints
    ADD COLUMN regional_office VARCHAR(100) NULL COMMENT
        'Office whose jurisdiction the complaint sits in. Mandatory scope on CEPC listing requests together with DEPARTMENT. NULL on rows predating this column.';

ALTER TABLE complaints
    ADD COLUMN is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT
        'Whether the complaint has been opened at all. Not the per-user unread filter, which is COMPLAINT_READ_STATE.';

ALTER TABLE complaints
    ADD COLUMN has_attachment TINYINT(1) NOT NULL DEFAULT 0 COMMENT
        'Whether any COMPLAINT_ATTACHMENT row exists. Denormalised so the grid can filter on it without an EXISTS subquery.';

ALTER TABLE complaints
    ADD COLUMN created_by VARCHAR(200) NULL COMMENT
        'Who filed the complaint. CREATED_AT already existed; the actor did not.';

-- REGIONAL_OFFICE is ANDed into every CEPC listing query next to DEPARTMENT, so the two
-- are indexed together rather than separately.
CREATE INDEX idx_complaints_dept_office ON complaints (department, regional_office);
