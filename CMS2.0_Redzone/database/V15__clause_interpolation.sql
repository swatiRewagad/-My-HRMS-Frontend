-- V15: make ELIGIBILITY_QUESTION_MASTER.clause_reference the single source of truth for clause citations
-- MySQL version
--
-- Clause numbers were baked into the translated block-message prose in all ten locales, so correcting a
-- citation meant ten edits across six scripts — and Bengali stores the digits in Bengali numerals
-- (১০(১)(জে)), so an ASCII find/replace silently skipped it. The prose now carries a {{clause}}
-- placeholder that the portal interpolates from clause_reference.
--
-- The seeders are insert-if-absent, so rows already present keep the baked digits. This migration
-- rewrites them in place. It is scoped BY KEY CODE, not by an English phrase: the localized rows are in
-- native scripts, so no English substring matches them.
--
-- Re-running is safe: each REPLACE is a no-op once the literal is gone.

-- ═══ Translated block messages: swap the baked digits for the placeholder ═══
-- Latin-digit forms (en, hi, mr, te, ta, gu, ur, kn, ml).
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.id = t.translation_key_id
   SET t.`value` = REPLACE(REPLACE(t.`value`, '10(1)(j)', '{{clause}}'), '10(2)(b)(ii)', '{{clause}}')
 WHERE k.code IN ('eligibility.block_not_filed', 'eligibility.block_sub_judice');

-- Bengali numerals. ১০(১)(জে) is 10(1)(j); ১০(২)(খ)(ii) is 10(2)(b)(ii) and is mixed-script — the
-- trailing "ii" is Latin even in the Bengali row, so it must be matched exactly as stored.
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.id = t.translation_key_id
   SET t.`value` = REPLACE(REPLACE(t.`value`, '১০(১)(জে)', '{{clause}}'), '১০(২)(খ)(ii)', '{{clause}}')
 WHERE k.code IN ('eligibility.block_not_filed', 'eligibility.block_sub_judice')
   AND t.locale = 'bn';

UPDATE TRANSLATION_KEYS
   SET default_value = REPLACE(REPLACE(default_value, '10(1)(j)', '{{clause}}'),
                               '10(2)(b)(ii)', '{{clause}}')
 WHERE code IN ('eligibility.block_not_filed', 'eligibility.block_sub_judice');

-- ═══ Master block_message columns ═══
-- The master carries an English fallback used when a translation key is missing, so it needs the same
-- placeholder or the fallback would still show a stale citation.
UPDATE ELIGIBILITY_QUESTION_MASTER
   SET block_message = REPLACE(REPLACE(block_message, '10(1)(j)', '{{clause}}'),
                               '10(2)(b)(ii)', '{{clause}}'),
       updated_at = NOW()
 WHERE scheme_version = 'RBIOS_2021'
   AND block_message IS NOT NULL
   AND (block_message LIKE '%10(1)(j)%' OR block_message LIKE '%10(2)(b)(ii)%');
