-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────
-- V106 — UST100 (FR-G-031) S5: the citizen tracking table's empty message.  MySQL version.
--
-- (V104 and V105 were taken by parallel sessions, hence 106.)
--
-- ── WHAT THIS FIXES ───────────────────────────────────────────────────────────────────────────────────
-- UST100 S5 states the expected result verbatim: "No complaints found."  — with a full stop.
--
-- `history.no_complaints` already exists and already carries nine non-English translations, EIGHT of
-- which end in their locale's sentence terminator (Devanagari danda, or a full stop). Two rows disagree:
--
--     en   'No complaints found'      -- no full stop
--     hi   'कोई शिकायत नहीं मिली'      -- no danda
--
-- PortalFullTranslationSeeder (line 128 / 409) has ALWAYS held the terminated forms
-- ('No complaints found.' / 'कोई शिकायत नहीं मिली।'). The un-terminated rows in cms_db came from the
-- OLDER csv seed (src/main/resources/data/translations.csv:367-368), which ran first and won: both
-- seeders are insert-if-absent, so whichever inserts the row fixes its wording forever. That is exactly
-- why a seeder edit cannot fix this and a migration must — and why the csv is corrected in the same
-- change, so a NEVER-seeded database does not reintroduce the divergence.
--
-- The default_value on TRANSLATION_KEYS is corrected too. TranslationService.getTranslationsForLocale
-- falls back to it via putIfAbsent for any locale with no row of its own, so a locale that is not
-- covered (pa) would otherwise still serve the un-terminated English.
--
-- ── WHY THE EXACT TEXT MATTERS AT ALL ─────────────────────────────────────────────────────────────────
-- This string is not decorative: it is the ONLY thing distinguishing "you have filed nothing" from a
-- broken screen, and the same screen change that made it reachable (complaint-tracker.component.html —
-- the table used to be hidden whenever the list was empty, so the message was dead markup) also made it
-- worth getting right. The failure case is now a SEPARATE key (ui.common.load_failed), so a 500 can
-- never be reported to a citizen as "No complaints found."
--
-- ── NO PLACEHOLDER ────────────────────────────────────────────────────────────────────────────────────
-- Deliberately none. TranslationService.translate(key, params?) takes params OPTIONALLY, so a key holding
-- a {{placeholder}} rendered through the bare `| translate` pipe leaks the literal braces to the citizen.
-- This key is consumed by the pipe with no params, so it must contain no placeholder, and does not.
--
-- ── RE-RUNNABLE ───────────────────────────────────────────────────────────────────────────────────────
-- Each UPDATE is guarded on the value still being the exact un-terminated string this migration knows
-- about, so a second run matches nothing and any wording an operator has since revised is left alone.
-- The pa INSERT is guarded on the (key, locale) row being absent.
-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────

-- ── en: add the full stop UST100 S5 asks for ──
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
    SET t.VALUE = 'No complaints found.', t.UPDATED_AT = NOW()
  WHERE k.CODE = 'history.no_complaints'
    AND t.LOCALE = 'en'
    AND t.VALUE = 'No complaints found';

-- ── the key's own fallback, served to any locale with no row ──
UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'No complaints found.'
 WHERE CODE = 'history.no_complaints'
   AND DEFAULT_VALUE = 'No complaints found';

-- ── hi: add the danda, so Hindi matches the other eight locales and the seeder ──
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
    SET t.VALUE = 'कोई शिकायत नहीं मिली।', t.UPDATED_AT = NOW()
  WHERE k.CODE = 'history.no_complaints'
    AND t.LOCALE = 'hi'
    AND t.VALUE = 'कोई शिकायत नहीं मिली';

-- ── pa: the locale is served by /api/v1/i18n/locales but has no row for this key ──
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'pa', 'ਕੋਈ ਸ਼ਿਕਾਇਤ ਨਹੀਂ ਮਿਲੀ।', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'history.no_complaints'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'pa');
