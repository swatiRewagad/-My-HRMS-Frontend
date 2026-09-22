-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V55 — SYSTEM_CONFIG tunables for AA hearing scheduling, reminders and order issue
--
-- Session S3C. Re-runnable: insert-if-absent via SELECT ... WHERE NOT EXISTS, the same idiom V46
-- uses. Re-running never overwrites a value an operator has since tuned in the Config screen.
--
-- SYSTEM_CONFIG is the store because there is no PREFERENCE_MASTER table in this schema; it is the
-- established typed, cached, auditable config store and its edits already flow to CONFIG_AUDIT_LOG.
--
-- Two defaults are deliberately OFF, and that is a design decision rather than an oversight:
--   reminder_enabled = false     — a @SpringBootTest that boots the scheduler must not be able to
--                                 fire real notices at real citizens.
--   require_ed_approval = false  — gating order issue on ED approval is NET-NEW behaviour, not a bug
--                                 fix. Turning it on changes who can issue an order, so it needs an
--                                 explicit operator decision, not a silent default.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT * FROM (
    SELECT 'cms.aa.hearing.conflict_check_enabled' AS k, 'true' AS v,
           'Server-side check that refuses to list an officer who is already occupied in the slot. Off = double-booking allowed.' AS d,
           'V55' AS u, NOW() AS t
    UNION ALL SELECT 'cms.aa.hearing.slot_minutes', '60',
           'Minutes a listing occupies the presiding officer''s calendar; the width of the window the conflict check compares against.',
           'V55', NOW()
    UNION ALL SELECT 'cms.aa.hearing.min_notice_days', '7',
           'ADVISORY ONLY: days between listing and hearing below which the UI warns. It does NOT refuse the listing. NOT a figure from the RBIOS Scheme - a working default pending legal sign-off.',
           'V55', NOW()
    UNION ALL SELECT 'cms.aa.hearing.reminder_enabled', 'false',
           'Master switch for the pre-hearing reminder sweep. Defaults FALSE so a @SpringBootTest cannot fire real reminders.',
           'V55', NOW()
    UNION ALL SELECT 'cms.aa.hearing.reminder_lead_hours', '24',
           'Hours before the hearing at which the reminder obligation is recorded, when the sweep is enabled.',
           'V55', NOW()
    UNION ALL SELECT 'cms.aa.order.require_ed_approval', 'false',
           'Whether issuing an order requires recorded ED approval. Defaults FALSE: gating orders on ED approval is net-new behaviour, not a bug fix, so it needs an explicit operator decision.',
           'V55', NOW()
    UNION ALL SELECT 'cms.aa.order.scheme_version', 'RBIOS_2021',
           'Scheme edition stamped on issued orders and used to resolve valid clause codes from CLOSURE_CLAUSE_MASTER.',
           'V55', NOW()
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM SYSTEM_CONFIG sc WHERE sc.config_key = seed.k
);
