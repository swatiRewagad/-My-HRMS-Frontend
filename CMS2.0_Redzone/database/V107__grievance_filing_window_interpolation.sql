-- V107: make cms.eligibility.grievance-filing-window-days the single source of truth for the filing
-- window quoted to a citizen.
-- MySQL version (Oracle mirror: database/oracle/V102__grievance_filing_window_interpolation.sql)
--
-- UST12: eligibility.time_barred_warning baked the literal "310" into the prose in all ten locales
-- (Bengali in Bengali numerals, ৩১০). The wizard ENFORCES the window it reads from
-- GET /api/v1/eligibility/questions as `grievanceFilingWindowDays` — so raising
-- cms.eligibility.grievance-filing-window-days changed the rule the product applied while the citizen
-- was still told the window was 310 days. This is the SAME defect class V102 fixed for the RE window
-- (eligibility.block_less_than_30_days) and V103 fixed for upload limits, and it has the same fix: the
-- prose carries a {{days}} placeholder the portal interpolates from the served value.
--
-- EligibilityTranslationSeeder is insert-if-absent BY KEY, so correcting the seeder alone never
-- rewrites a row that already exists in a live database. This migration rewrites them.
-- Scoped BY KEY CODE, not by an English phrase: the localized rows are in native scripts.
--
-- Re-running is safe: each REPLACE is a no-op once the literal is gone.

-- ═══ Latin-digit forms (en, hi, mr, te, ta, gu, ur, kn, ml) ═══
-- Bounded to this one key, so the bare literal "310" cannot be disturbed in any other prose.
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.id = t.translation_key_id
   SET t.`value` = REPLACE(t.`value`, '310', '{{days}}')
 WHERE k.code = 'eligibility.time_barred_warning';

-- ═══ Bengali numerals: ৩১০ is 310 ═══
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.id = t.translation_key_id
   SET t.`value` = REPLACE(t.`value`, '৩১০', '{{days}}')
 WHERE k.code = 'eligibility.time_barred_warning'
   AND t.locale = 'bn';

UPDATE TRANSLATION_KEYS
   SET default_value = REPLACE(default_value, '310', '{{days}}')
 WHERE code = 'eligibility.time_barred_warning';
