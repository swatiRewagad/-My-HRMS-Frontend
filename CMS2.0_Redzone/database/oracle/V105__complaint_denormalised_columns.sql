-- ============================================================
-- V105 — COMPLAINTS gains display columns and a CEPC office scope
--
--   Oracle counterpart of MySQL V108. Seven columns, in two groups.
--
--   Display copies (ENTITY_NAME, CATEGORY_NAME, ASSIGNED_OFFICER_NAME): the row already
--   carries ENTITY_CODE, CATEGORY_ID and ASSIGNED_OFFICER, but those are codes and ids.
--   The dashboard grid sorts and column-filters on the name the officer reads, and serving
--   that through a join to each master makes every listing query fan out over three more
--   tables.
--
--   REGIONAL_OFFICE is the CEPC jurisdiction scope, applied on every listing request
--   alongside DEPARTMENT. Deliberately not RBIO_OFFICE_CODE, which is the RBIO intake
--   office parsed from the complaint number and is NULL on every CEPC complaint.
--
--   IS_READ and HAS_ATTACHMENT are NUMBER(1) NOT NULL DEFAULT 0, so the ADD succeeds
--   against existing rows with no backfill. IS_READ is "has anyone opened this" and is NOT
--   the dashboard's unread filter: that is per user and stays answered by
--   COMPLAINT_READ_STATE, which one boolean on a shared row cannot express.
--
--   Guarded per column so a re-run is a no-op, matching V104.
-- ============================================================

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = 'ENTITY_NAME';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD (ENTITY_NAME VARCHAR2(300))';
        EXECUTE IMMEDIATE
            'COMMENT ON COLUMN COMPLAINTS.ENTITY_NAME IS ''Regulated entity name, copied from the entity master at intake so the grid can sort on it without a join.''';
    END IF;

    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = 'CATEGORY_NAME';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD (CATEGORY_NAME VARCHAR2(200))';
        EXECUTE IMMEDIATE
            'COMMENT ON COLUMN COMPLAINTS.CATEGORY_NAME IS ''Complaint category name, copied from COMPLAINT_CATEGORY. CATEGORY_ID remains the authoritative link.''';
    END IF;

    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = 'ASSIGNED_OFFICER_NAME';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD (ASSIGNED_OFFICER_NAME VARCHAR2(250))';
        EXECUTE IMMEDIATE
            'COMMENT ON COLUMN COMPLAINTS.ASSIGNED_OFFICER_NAME IS ''Display name of the officer in ASSIGNED_OFFICER. Rewritten whenever the assignment changes.''';
    END IF;

    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = 'REGIONAL_OFFICE';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD (REGIONAL_OFFICE VARCHAR2(100))';
        EXECUTE IMMEDIATE
            'COMMENT ON COLUMN COMPLAINTS.REGIONAL_OFFICE IS ''Office whose jurisdiction the complaint sits in. Mandatory scope on CEPC listing requests together with DEPARTMENT. NULL on rows predating this column.''';
    END IF;

    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = 'IS_READ';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD (IS_READ NUMBER(1) DEFAULT 0 NOT NULL)';
        EXECUTE IMMEDIATE
            'COMMENT ON COLUMN COMPLAINTS.IS_READ IS ''Whether the complaint has been opened at all. Not the per-user unread filter, which is COMPLAINT_READ_STATE.''';
    END IF;

    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = 'HAS_ATTACHMENT';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD (HAS_ATTACHMENT NUMBER(1) DEFAULT 0 NOT NULL)';
        EXECUTE IMMEDIATE
            'COMMENT ON COLUMN COMPLAINTS.HAS_ATTACHMENT IS ''Whether any COMPLAINT_ATTACHMENT row exists. Denormalised so the grid can filter on it without an EXISTS subquery.''';
    END IF;

    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = 'CREATED_BY';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD (CREATED_BY VARCHAR2(200))';
        EXECUTE IMMEDIATE
            'COMMENT ON COLUMN COMPLAINTS.CREATED_BY IS ''Who filed the complaint. CREATED_AT already existed; the actor did not.''';
    END IF;

    -- REGIONAL_OFFICE is ANDed into every CEPC listing query next to DEPARTMENT, so the
    -- two are indexed together rather than separately.
    SELECT COUNT(*) INTO v_exists FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_COMPLAINTS_DEPT_OFFICE';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_COMPLAINTS_DEPT_OFFICE ON COMPLAINTS (DEPARTMENT, REGIONAL_OFFICE)';
    END IF;
END;
/
