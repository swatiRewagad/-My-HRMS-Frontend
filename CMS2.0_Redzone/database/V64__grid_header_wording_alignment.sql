-- ============================================================
-- V64 — Align shared-grid header wording with what the product already displays
-- ============================================================
--
-- WHY THIS EXISTS
--
--   Consolidating eighteen hand-rolled grids onto one shared component moved the column headers from
--   hardcoded English in each template into translation keys. Three of those keys were seeded with
--   wording that differed slightly from what the product had always shown:
--
--     ui.col.complainant_name   "Complainant Name"  ->  "Complainant"
--     ui.col.entity_name        "Entity Name"       ->  "Entity"
--     ui.col.deadline           "Deadline"          ->  "SLA Due"
--     ui.common.showing_count   "Showing X of Y"    ->  "Showing X of Y entries"
--
--   That is a user-visible copy change nobody asked for, and it broke the CEPC dashboard suite, which
--   asserts the existing wording. Homogenising the IMPLEMENTATION must not silently reword the
--   INTERFACE, so the keys are corrected to match what officers already read.
--
-- WHY A MIGRATION AND NOT JUST A SEEDER EDIT
--
--   Seeders are insert-if-absent by key code (`existsByCode`). Once a key row exists, editing the Java
--   default has NO effect on a database that already ran the seeder — the old text persists forever.
--   Correcting seeded text therefore always needs an explicit UPDATE, scoped BY KEY CODE.
--
--   Only the ENGLISH default_value is touched. The ten localized rows in TRANSLATIONS are left alone:
--   they are correct renderings of the field, and an English substring match would not find them
--   anyway since they are in native scripts.
--
-- RE-RUNNABLE: plain idempotent UPDATEs guarded on the current value, so replaying is a no-op.

UPDATE TRANSLATION_KEYS
SET default_value = 'Complainant'
WHERE code = 'ui.col.complainant_name'
  AND default_value = 'Complainant Name';

UPDATE TRANSLATION_KEYS
SET default_value = 'Entity'
WHERE code = 'ui.col.entity_name'
  AND default_value = 'Entity Name';

UPDATE TRANSLATION_KEYS
SET default_value = 'SLA Due'
WHERE code = 'ui.col.deadline'
  AND default_value = 'Deadline';

-- Two suites assert the exact sentence "Showing N to M of T entries", so the trailing noun matters.
UPDATE TRANSLATION_KEYS
SET default_value = 'Showing {{shown}} of {{total}} entries'
WHERE code = 'ui.common.showing_count'
  AND default_value = 'Showing {{shown}} of {{total}}';
