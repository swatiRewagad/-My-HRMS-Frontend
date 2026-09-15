-- V14: make ELIGIBILITY_QUESTION_MASTER.CLAUSE_REFERENCE the single source of truth for clause citations
-- Oracle version (mirrors database/V15__clause_interpolation.sql; the two directories' V-numbers are
-- not in sync)
--
-- Clause numbers were baked into the translated block-message prose in all ten locales, so correcting a
-- citation meant ten edits across six scripts — and Bengali stores the digits in Bengali numerals, so an
-- ASCII find/replace silently skipped it. The prose now carries a {{clause}} placeholder that the portal
-- interpolates from CLAUSE_REFERENCE.
--
-- The seeders are insert-if-absent, so rows already present keep the baked digits. Scoped BY KEY CODE,
-- not by an English phrase: the localized rows are in native scripts, so no English substring matches.
-- UNISTR is used for the Bengali literals so they survive any client charset.
-- VALUE / DEFAULT_VALUE are CLOB, so DBMS_LOB.SUBSTR hands a VARCHAR2 to REPLACE.
--
-- Re-running is safe: each REPLACE is a no-op once the literal is gone.

-- ═══ Translated block messages: Latin-digit forms (en, hi, mr, te, ta, gu, ur, kn, ml) ═══
UPDATE TRANSLATIONS t
   SET t.VALUE = TO_CLOB(REPLACE(REPLACE(DBMS_LOB.SUBSTR(t.VALUE, 32767, 1),
                                         '10(1)(j)', '{{clause}}'),
                                 '10(2)(b)(ii)', '{{clause}}'))
 WHERE t.VALUE IS NOT NULL
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID
                  AND k.CODE IN ('eligibility.block_not_filed', 'eligibility.block_sub_judice'));

-- ═══ Bengali numerals ═══
-- \09E7\09E6 = ১০ ; \0995\09C7 = কে-form "জে" is \099C\09C7 ; \0996 = খ
-- ১০(১)(জে)       -> UNISTR('\09E7\09E6(\09E7)(\099C\09C7)')
-- ১০(২)(খ)(ii)    -> UNISTR('\09E7\09E6(\09E8)(\0996)') || '(ii)'  -- trailing "ii" is Latin as stored
UPDATE TRANSLATIONS t
   SET t.VALUE = TO_CLOB(REPLACE(REPLACE(DBMS_LOB.SUBSTR(t.VALUE, 32767, 1),
                                         UNISTR('\09E7\09E6(\09E7)(\099C\09C7)'), '{{clause}}'),
                                 UNISTR('\09E7\09E6(\09E8)(\0996)') || '(ii)', '{{clause}}'))
 WHERE t.LOCALE = 'bn'
   AND t.VALUE IS NOT NULL
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID
                  AND k.CODE IN ('eligibility.block_not_filed', 'eligibility.block_sub_judice'));

UPDATE TRANSLATION_KEYS k
   SET k.DEFAULT_VALUE = TO_CLOB(REPLACE(REPLACE(DBMS_LOB.SUBSTR(k.DEFAULT_VALUE, 32767, 1),
                                                 '10(1)(j)', '{{clause}}'),
                                         '10(2)(b)(ii)', '{{clause}}'))
 WHERE k.CODE IN ('eligibility.block_not_filed', 'eligibility.block_sub_judice')
   AND k.DEFAULT_VALUE IS NOT NULL;

-- ═══ Master BLOCK_MESSAGE columns ═══
-- The master carries an English fallback used when a translation key is missing, so it needs the same
-- placeholder or the fallback would still show a stale citation.
UPDATE ELIGIBILITY_QUESTION_MASTER
   SET BLOCK_MESSAGE = TO_CLOB(REPLACE(REPLACE(DBMS_LOB.SUBSTR(BLOCK_MESSAGE, 32767, 1),
                                               '10(1)(j)', '{{clause}}'),
                                       '10(2)(b)(ii)', '{{clause}}')),
       UPDATED_AT = SYSTIMESTAMP
 WHERE SCHEME_VERSION = 'RBIOS_2021'
   AND BLOCK_MESSAGE IS NOT NULL
   AND (DBMS_LOB.INSTR(BLOCK_MESSAGE, '10(1)(j)') > 0
     OR DBMS_LOB.INSTR(BLOCK_MESSAGE, '10(2)(b)(ii)') > 0);

COMMIT;
/
