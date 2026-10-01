-- ============================================================
-- V62 — Align shared-grid header wording with what the product already displays
-- Oracle counterpart of MySQL V64. The two directories' V-numbers are NOT in sync.
--
-- Full rationale is in the MySQL counterpart (database/V64). In short: consolidating eighteen grids
-- onto one shared component moved column headers into translation keys, and three were seeded with
-- wording that differed from what the product had always shown ("Complainant Name" vs "Complainant",
-- "Entity Name" vs "Entity", "Deadline" vs "SLA Due", and a range sentence missing "entries").
-- Homogenising the implementation must not silently reword the interface.
--
-- A migration rather than a seeder edit because seeders are insert-if-absent by key code: once the row
-- exists, changing the Java default has no effect on a database that already ran it.
--
-- Only the English default_value is touched; the ten localized rows in TRANSLATIONS are correct.
--
-- Re-runnable: every UPDATE is guarded on the current value, so replaying is a no-op.
-- ============================================================

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

UPDATE TRANSLATION_KEYS
SET default_value = 'Showing {{shown}} of {{total}} entries'
WHERE code = 'ui.common.showing_count'
  AND default_value = 'Showing {{shown}} of {{total}}';

COMMIT;
