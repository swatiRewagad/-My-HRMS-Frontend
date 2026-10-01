-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V53 — SYSTEM_CONFIG tunables for AA hearing scheduling, reminders and order issue
--
-- Session S3C. Oracle counterpart of MySQL V55. Re-runnable: the local seed_config procedure inserts
-- only when the key is absent, so re-running never overwrites a value an operator has since tuned.
--
-- SYSTEM_CONFIG is the store because there is no PREFERENCE_MASTER table in this schema; it is the
-- established typed, cached, auditable config store.
--
-- Two defaults are deliberately OFF, and that is a design decision rather than an oversight:
--   reminder_enabled = false     — a @SpringBootTest that boots the scheduler must not be able to
--                                 fire real notices at real citizens.
--   require_ed_approval = false  — gating order issue on ED approval is NET-NEW behaviour, not a bug
--                                 fix. It changes who can issue an order, so it needs an explicit
--                                 operator decision, not a silent default.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DECLARE
    PROCEDURE seed_config(p_key VARCHAR2, p_value VARCHAR2, p_desc VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_exists = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_value, p_desc, 'V53', SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    seed_config('cms.aa.hearing.conflict_check_enabled', 'true',
        'Server-side check that refuses to list an officer who is already occupied in the slot. Off = double-booking allowed.');
    seed_config('cms.aa.hearing.slot_minutes', '60',
        'Minutes a listing occupies the presiding officer''s calendar; the width of the window the conflict check compares against.');
    seed_config('cms.aa.hearing.min_notice_days', '7',
        'ADVISORY ONLY: days between listing and hearing below which the UI warns. It does NOT refuse the listing. NOT a figure from the RBIOS Scheme - a working default pending legal sign-off.');
    seed_config('cms.aa.hearing.reminder_enabled', 'false',
        'Master switch for the pre-hearing reminder sweep. Defaults FALSE so a @SpringBootTest cannot fire real reminders.');
    seed_config('cms.aa.hearing.reminder_lead_hours', '24',
        'Hours before the hearing at which the reminder obligation is recorded, when the sweep is enabled.');
    seed_config('cms.aa.order.require_ed_approval', 'false',
        'Whether issuing an order requires recorded ED approval. Defaults FALSE: gating orders on ED approval is net-new behaviour, not a bug fix, so it needs an explicit operator decision.');
    seed_config('cms.aa.order.scheme_version', 'RBIOS_2021',
        'Scheme edition stamped on issued orders and used to resolve valid clause codes from CLOSURE_CLAUSE_MASTER.');
    COMMIT;
END;
/
