-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V49 — AA workflow state machine: stage SLA config (session S3A). Oracle counterpart of MySQL V51.
--
-- Re-runnable via USER_TABLES / USER_INDEXES guards.
--
-- Deliberately NARROWER than the MySQL file, and both omissions are correct rather than oversights:
--   * OUTBOX_EVENT already exists here (database/V1__initial_schema.sql is Oracle syntax and creates
--     it, along with OUTBOX_EVENT_SEQ). The MySQL side needed it created because that file was never
--     translated. Creating it again here would be wrong.
--   * HOLIDAYS is already seeded here with 36 real RBI/national rows
--     (database/oracle/V2__seed_data.sql). The MySQL seed exists only for dev parity against an empty
--     table; this remains the authoritative calendar.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- SYSTEM_CONFIG — per-stage SLA durations, in WORKING days
--
-- Appeals had no in-flight deadline: TIMELINE.APPEAL.FILING_WINDOW_DAYS and EXTENDED_WINDOW_DAYS are
-- consulted only when deciding whether an appeal may be FILED. CEPC and RBIO hold their stage durations
-- in Java constants, so a timeline change needs a redeploy; these live in SYSTEM_CONFIG so an operator
-- can adjust them without one.
--
-- IMPORTANT: these are OPERATIONAL tracking targets, NOT statutory limits. No RBIOS text prescribing
-- per-stage AA timelines was available, so the values are conservative placeholders and must be
-- confirmed before go-live. Presenting an internal estimate to a citizen as a legal entitlement would be
-- worse than showing no deadline at all.
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    PROCEDURE seed_config(p_key VARCHAR2, p_value VARCHAR2, p_desc VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_exists = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_value, p_desc, 'V49', SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    seed_config('cms.aa.sla.filed.days', '7',
        'Working days an appeal may sit awaiting AA_DO acceptance. OPERATIONAL target, not statutory.');
    seed_config('cms.aa.sla.under_review.days', '30',
        'Working days allowed for review once accepted. OPERATIONAL target, not statutory.');
    seed_config('cms.aa.sla.hearing_scheduled.days', '30',
        'Working days from hearing scheduling to disposal. OPERATIONAL target, not statutory.');
    COMMIT;
END;
/
