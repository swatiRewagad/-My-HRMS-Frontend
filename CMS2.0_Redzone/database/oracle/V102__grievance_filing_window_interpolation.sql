-- V102: make cms.eligibility.grievance-filing-window-days the single source of truth for the filing
-- window quoted to a citizen.
-- Oracle version (mirrors database/V107__grievance_filing_window_interpolation.sql; the two
-- directories' V-numbers are not in sync)
--
-- UST12: eligibility.time_barred_warning baked the literal "310" into the prose in all ten locales
-- (Bengali in Bengali numerals, ৩১০). The wizard ENFORCES the window it reads from
-- GET /api/v1/eligibility/questions as `grievanceFilingWindowDays` — so raising
-- cms.eligibility.grievance-filing-window-days changed the rule the product applied while the citizen
-- was still told the window was 310 days. This is the SAME defect class V100 fixed for the RE window,
-- and it has the same fix: the prose carries a {{days}} placeholder the portal interpolates.
--
-- EligibilityTranslationSeeder is insert-if-absent BY KEY, so correcting the seeder alone never
-- rewrites a row that already exists in a live database. This migration rewrites them.
-- Scoped BY KEY CODE, not by an English phrase: the localized rows are in native scripts.
-- UNISTR is used for the Bengali literal so it survives any client charset.
-- VALUE / DEFAULT_VALUE are CLOB, so DBMS_LOB.SUBSTR hands a VARCHAR2 to REPLACE.
--
-- Re-running is safe: each REPLACE is a no-op once the literal is gone.

-- ═══ Latin-digit forms (en, hi, mr, te, ta, gu, ur, kn, ml) ═══
UPDATE TRANSLATIONS t
   SET t.VALUE = TO_CLOB(REPLACE(DBMS_LOB.SUBSTR(t.VALUE, 32767, 1), '310', '{{days}}'))
 WHERE t.VALUE IS NOT NULL
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID
                  AND k.CODE = 'eligibility.time_barred_warning');

-- ═══ Bengali numerals: \09E9\09E7\09E6 is ৩১০ = 310 ═══
UPDATE TRANSLATIONS t
   SET t.VALUE = TO_CLOB(REPLACE(DBMS_LOB.SUBSTR(t.VALUE, 32767, 1),
                                 UNISTR('\09E9\09E7\09E6'), '{{days}}'))
 WHERE t.LOCALE = 'bn'
   AND t.VALUE IS NOT NULL
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID
                  AND k.CODE = 'eligibility.time_barred_warning');

UPDATE TRANSLATION_KEYS k
   SET k.DEFAULT_VALUE = TO_CLOB(REPLACE(DBMS_LOB.SUBSTR(k.DEFAULT_VALUE, 32767, 1),
                                         '310', '{{days}}'))
 WHERE k.CODE = 'eligibility.time_barred_warning'
   AND k.DEFAULT_VALUE IS NOT NULL;

COMMIT;
/
