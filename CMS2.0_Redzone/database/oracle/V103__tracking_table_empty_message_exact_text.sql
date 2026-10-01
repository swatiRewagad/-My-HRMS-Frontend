-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────
-- V103 (Oracle mirror of MySQL V106) — UST100 (FR-G-031) S5: the citizen tracking table's empty message.
--
-- The numbering differs from the MySQL side because the two directories were renumbered independently by
-- parallel sessions; this is the mirror of database/V106__tracking_table_empty_message_exact_text.sql and
-- the full rationale lives there. In short:
--
--   UST100 S5 states the expected result verbatim as "No complaints found." — with a full stop. Eight of
--   the ten existing locales already terminate the sentence; `en` and `hi` do not, because the older csv
--   seed (data/translations.csv) inserted them first and both seeders are insert-if-absent, so the newer
--   PortalFullTranslationSeeder wording ('No complaints found.') never reached an already-seeded database.
--   A seeder edit alone therefore cannot fix an existing environment — hence this migration — and the csv
--   is corrected in the same change so a never-seeded database does not reintroduce the divergence.
--
--   DEFAULT_VALUE is corrected too, because TranslationService falls back to it (putIfAbsent) for any
--   locale with no row of its own.
--
--   `pa` is served by /api/v1/i18n/locales but has no row for this key, so one is inserted.
--
-- RE-RUNNABLE: each UPDATE is guarded on the value still being the exact string this migration knows
-- about, and the INSERT on the (key, locale) row being absent.
-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────

-- ── en: add the full stop UST100 S5 asks for ──
UPDATE TRANSLATIONS
   SET VALUE = 'No complaints found.', UPDATED_AT = SYSTIMESTAMP
 WHERE LOCALE = 'en'
   AND VALUE = 'No complaints found'
   AND TRANSLATION_KEY_ID IN (SELECT ID FROM TRANSLATION_KEYS WHERE CODE = 'history.no_complaints');

-- ── the key's own fallback, served to any locale with no row ──
UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'No complaints found.'
 WHERE CODE = 'history.no_complaints'
   AND DEFAULT_VALUE = 'No complaints found';

-- ── hi: add the danda, so Hindi matches the other eight locales and the seeder ──
UPDATE TRANSLATIONS
   SET VALUE = 'कोई शिकायत नहीं मिली।', UPDATED_AT = SYSTIMESTAMP
 WHERE LOCALE = 'hi'
   AND VALUE = 'कोई शिकायत नहीं मिली'
   AND TRANSLATION_KEY_ID IN (SELECT ID FROM TRANSLATION_KEYS WHERE CODE = 'history.no_complaints');

-- ── pa: the locale is served but has no row for this key ──
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'pa', 'ਕੋਈ ਸ਼ਿਕਾਇਤ ਨਹੀਂ ਮਿਲੀ।', SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'history.no_complaints'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'pa');

COMMIT;
