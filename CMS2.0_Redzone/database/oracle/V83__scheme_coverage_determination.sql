-- ============================================================
-- V83 — Record the Scheme-coverage determination on the complaint
-- Session S3 (UST473). Oracle counterpart of MySQL V85.
-- ============================================================
--
-- See database/V85__scheme_coverage_determination.sql for the full rationale. In brief:
--
--   * MreEntityCoverageService answered a substring existence test over REGULATED_ENTITIES that ignored
--     the DEPARTMENT column entirely, so a CEPC entity — outside the Ombudsman Scheme — answered
--     "covered", and the verdict was cached.
--   * ComplaintRoutingService.resolveDepartment took the FIRST row of an unordered partial match. On the
--     live data 'HDFC' matches HDFC Credila (CEPC) before two HDFC Bank rows (RBIO), so a complaint
--     against a Scheme-covered bank was routed to CEPC by row order. Unknown and blank entities returned
--     'RBIO', i.e. default-ALLOW on a maintainability determination.
--
--   Coverage is now decided in one place, refuses to guess across departments, and treats unknown as not
--   covered. AMBIGUOUS and UNKNOWN route to CEPC as the fail-closed direction.
--
--   Two new columns rather than reusing MAINTAINABILITY_DETERMINATION, which is a two-value human
--   decision read by Drools cap rules as `== "MAINTAINABLE"` — a third value would fall out of those
--   guards silently — and which carries a DETERMINED_BY naming a person.
--
--   Both NULLABLE, because ddl-auto=update runs against a shared database and NOT NULL would be permanent
--   for every other session. Not backfilled: inventing a determination for a historical complaint would
--   put a legal conclusion on a record that never had one.
--
-- Re-running is safe: guarded on USER_TAB_COLUMNS.
-- ============================================================

DECLARE
    PROCEDURE add_column(p_table VARCHAR2, p_column VARCHAR2, p_definition VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_column || ' ' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column('COMPLAINTS', 'SCHEME_COVERAGE_STATUS', 'VARCHAR2(20)');
    add_column('COMPLAINTS', 'SCHEME_COVERAGE_REASON', 'VARCHAR2(500)');
    COMMIT;
END;
/
