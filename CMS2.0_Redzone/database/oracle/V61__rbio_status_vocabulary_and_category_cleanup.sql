-- ============================================================
-- V61 — Complete the RBIO status vocabulary, and de-pollute CATEGORY_MASTER
-- Oracle counterpart of MySQL V63. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- Rationale in full in the MySQL counterpart (database/V63). In brief:
--
-- PART 1. Live complaints carry status strings that resolve to no RBIO_STATUS_MASTER row, so the grid
--   cannot label them, the filter cannot offer them and IS_CLOSED is undefined for them. Measured
--   2026-09-22: 13 values match no STATUS_CODE (395 complaints), but 5 of those ALREADY resolve via the
--   LEGACY_VALUE column, which is the key the readers actually join on. Seeding those 5 would duplicate
--   existing states and create LEGACY_VALUE collisions, so only the 8 genuinely orphaned values (290
--   complaints) are seeded.
--
--   IS_CLOSED semantics: FORWARDED is 'Y' — the complaint has left RBIO so the file is shut here,
--   matching SENT_TO_OTHER_DEPT / SENT_TO_OTHER_RE — while IS_TERMINAL stays 'N' because a forwarded
--   complaint can still be reopened. Everything else is work in flight and is 'N'. AWAITING_CLOSURE is
--   'N' deliberately: awaiting closure is not closed, and 'Y' would drop complaints out of the
--   open-work queue before anyone closed them.
--   These flags are VARCHAR2(1) 'Y'/'N' in this table, not NUMBER(1).
--
--   Role visibility lives in a SEPARATE table: a status is invisible in every role filter until
--   RBIO_STATUS_ROLE_VISIBILITY carries it.
--
-- PART 2. CATEGORY_MASTER is 100% test pollution: 14 rows, one distinct name, zero active. The purge is
--   scoped to the probe NAME so a real category added later is never deleted. The pollution REGENERATES
--   (the e2e DELETE is a soft delete server-side), so this is a cleanup, not a permanent fix.
--   Dropping the table is a schema change and is proposed in the report, not executed here.
--
-- Everything is guarded on an existence check so a re-run is safe.
-- ============================================================

DECLARE
    PROCEDURE seed_status(p_code VARCHAR2, p_legacy VARCHAR2, p_label VARCHAR2,
                          p_milestone VARCHAR2, p_closed VARCHAR2, p_order NUMBER) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM RBIO_STATUS_MASTER
         WHERE STATUS_CODE = p_code OR LEGACY_VALUE = p_legacy;
        IF v_count = 0 THEN
            INSERT INTO RBIO_STATUS_MASTER
                (STATUS_CODE, LEGACY_VALUE, LABEL_EN, TRANSLATION_KEY, FILTER_KIND, MILESTONE_CODE,
                 IS_CLOSED, IS_TERMINAL, IS_ACTIVE, IS_CITIZEN_VISIBLE, DISPLAY_ORDER, SCHEME_VERSION,
                 QUEUE_ROLE, BLOCKS_MEETING, CREATED_AT)
            VALUES (p_code, p_legacy, p_label, 'rbio.status.' || LOWER(p_code), 'STATUS', p_milestone,
                    p_closed, 'N', 'Y', 'Y', p_order, 'RBIOS_2021', NULL, NULL, SYSTIMESTAMP);
        END IF;
    END;

    PROCEDURE seed_visibility(p_role VARCHAR2, p_code VARCHAR2, p_order NUMBER) IS
        v_master NUMBER;
        v_count  NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_master FROM RBIO_STATUS_MASTER WHERE STATUS_CODE = p_code;
        IF v_master = 0 THEN RETURN; END IF;
        SELECT COUNT(*) INTO v_count FROM RBIO_STATUS_ROLE_VISIBILITY
         WHERE ROLE_NAME = p_role AND STATUS_CODE = p_code;
        IF v_count = 0 THEN
            INSERT INTO RBIO_STATUS_ROLE_VISIBILITY (ROLE_NAME, STATUS_CODE, IS_DEFAULT, DISPLAY_ORDER)
            VALUES (p_role, p_code, 'N', p_order);
        END IF;
    END;

    -- English default only. Unlike clause labels these are ordinary UI vocabulary and MAY be translated
    -- later; nothing is machine-translated here.
    PROCEDURE seed_key(p_code VARCHAR2, p_default VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM TRANSLATION_KEYS WHERE CODE = p_code;
        IF v_count = 0 THEN
            INSERT INTO TRANSLATION_KEYS (CODE, DEFAULT_VALUE, DESCRIPTION, MODULE, CREATED_AT, UPDATED_AT)
            VALUES (p_code, p_default, 'RBIO status label (RBIO_STATUS_MASTER.translation_key)',
                    'rbio-status', SYSTIMESTAMP, SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    seed_status('FORWARDED', 'forwarded', 'Forwarded', 'FORWARD', 'Y', 35);
    seed_status('AWAITING_DETAILS', 'awaiting_details', 'Awaiting Details', 'ASSESSMENT', 'N', 36);
    seed_status('REVIEWER_REVIEW', 'reviewer_review', 'Under Reviewer Review', 'ASSESSMENT', 'N', 37);
    seed_status('RE_RESPONDED', 're_responded', 'Regulated Entity Responded', 'ASSESSMENT', 'N', 38);
    seed_status('RETURNED', 'returned', 'Returned', 'ASSESSMENT', 'N', 39);
    seed_status('AWAITING_CLOSURE', 'awaiting_closure', 'Awaiting Closure', 'FINAL_DECISION', 'N', 40);
    seed_status('INCHARGE_REVIEW', 'incharge_review', 'Under In-charge Review', 'ASSESSMENT', 'N', 41);
    seed_status('UNDER_REVIEW', 'under_review', 'Under Review', 'ASSESSMENT', 'N', 42);

    seed_visibility('RBIO_ADMIN', 'FORWARDED', 35);
    seed_visibility('RBIO_ADMIN', 'AWAITING_DETAILS', 36);
    seed_visibility('RBIO_ADMIN', 'REVIEWER_REVIEW', 37);
    seed_visibility('RBIO_ADMIN', 'RE_RESPONDED', 38);
    seed_visibility('RBIO_ADMIN', 'RETURNED', 39);
    seed_visibility('RBIO_ADMIN', 'AWAITING_CLOSURE', 40);
    seed_visibility('RBIO_ADMIN', 'INCHARGE_REVIEW', 41);
    seed_visibility('RBIO_ADMIN', 'UNDER_REVIEW', 42);
    seed_visibility('RBIO_DEALING_OFFICIAL', 'AWAITING_DETAILS', 36);
    seed_visibility('RBIO_DEALING_OFFICIAL', 'RE_RESPONDED', 38);
    seed_visibility('RBIO_DEALING_OFFICIAL', 'RETURNED', 39);
    seed_visibility('RBIO_DEALING_OFFICIAL', 'FORWARDED', 35);
    seed_visibility('RBIO_REVIEWER', 'REVIEWER_REVIEW', 37);
    seed_visibility('RBIO_REVIEWER', 'AWAITING_DETAILS', 36);
    seed_visibility('RBIO_REVIEWER', 'RE_RESPONDED', 38);
    seed_visibility('RBIO_REVIEWER', 'RETURNED', 39);
    seed_visibility('RBIO_SUPERVISOR', 'FORWARDED', 35);
    seed_visibility('RBIO_SUPERVISOR', 'AWAITING_CLOSURE', 40);
    seed_visibility('RBIO_SUPERVISOR', 'INCHARGE_REVIEW', 41);
    seed_visibility('RBIO_SUPERVISOR', 'UNDER_REVIEW', 42);
    seed_visibility('RBIO_OFFICER', 'AWAITING_DETAILS', 36);
    seed_visibility('RBIO_OFFICER', 'RE_RESPONDED', 38);
    seed_visibility('RBIO_OFFICER', 'RETURNED', 39);

    seed_key('rbio.status.forwarded', 'Forwarded');
    seed_key('rbio.status.awaiting_details', 'Awaiting Details');
    seed_key('rbio.status.reviewer_review', 'Under Reviewer Review');
    seed_key('rbio.status.re_responded', 'Regulated Entity Responded');
    seed_key('rbio.status.returned', 'Returned');
    seed_key('rbio.status.awaiting_closure', 'Awaiting Closure');
    seed_key('rbio.status.incharge_review', 'Under In-charge Review');
    seed_key('rbio.status.under_review', 'Under Review');

    -- PART 2: purge the CATEGORY_MASTER test residue.
    DELETE FROM CATEGORY_MASTER
     WHERE CATEGORY_NAME = 'S1 authority e2e probe'
       AND ACTIVE = 0;
END;
/

COMMIT;
