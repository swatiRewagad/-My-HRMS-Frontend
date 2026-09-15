-- V13: eligibility question scope narrowing + advocate sub-question master row
-- Oracle version (mirrors database/V14__eligibility_entity_scope_and_advocate_subquestion.sql;
-- the two directories' V-numbers are not in sync)
--
-- EligibilityQuestionMasterSeeder is insert-if-absent on (SCHEME_VERSION, QUESTION_KEY), so changing
-- its defaults does NOT update rows already present. Both changes below therefore need a migration.
--
-- Part 1 (Decision D): four Ombudsman/forum questions were scoped NON_CEPC, which showed them for
-- every non-CEPC department — including any third or unknown department value. They are RBIO
-- questions, so narrow them to RBIO.
--
-- Part 2: BRD row 15 ("If Yes, then are you the Complainant?") had no master row at all. It was
-- hardcoded in the Angular template with no translation key, so it rendered in English in all ten
-- locales and its block message was hardcoded English prose.

-- ═══ Part 1: narrow NON_CEPC → RBIO ═══
-- Guarded on the current value so a deliberate operator re-scope is not clobbered, and so re-running
-- is a no-op.
UPDATE ELIGIBILITY_QUESTION_MASTER
   SET APPLICABLE_ENTITY_TYPE = 'RBIO',
       UPDATED_AT = SYSTIMESTAMP
 WHERE SCHEME_VERSION = 'RBIOS_2021'
   AND APPLICABLE_ENTITY_TYPE = 'NON_CEPC'
   AND QUESTION_KEY IN ('isSubJudice', 'alreadySettled', 'pendingBeforeOmbudsman', 'settledByOmbudsman');

-- ═══ Part 2: seed the advocate follow-up as a master row ═══
-- QUESTION_NUMBER continues the seeder's sequence (14 rows including this one).
-- CLAUSE_REFERENCE is deliberately left NULL: the Scheme clause barring an advocate-filed complaint
-- where the filer is not the complainant has NOT been verified. An unverified citation on a
-- citizen-facing closure denies statutory recourse, so it must be supplied by the business owner
-- rather than guessed.
INSERT INTO ELIGIBILITY_QUESTION_MASTER
    (SCHEME_VERSION, APPLICABLE_ENTITY_TYPE, QUESTION_NUMBER, QUESTION_KEY, QUESTION_TYPE,
     QUESTION_TEXT, TRANSLATION_KEY, BLOCK_ON, BLOCK_MESSAGE, BLOCK_MESSAGE_KEY, CLAUSE_REFERENCE,
     NON_MAINTAINABLE, INLINE_SUB_QUESTION, ACTIVE, CREATED_AT, UPDATED_AT)
SELECT 'RBIOS_2021', 'RBIO', 14, 'isComplainantSelf', 'radio',
       TO_CLOB('If Yes, then are you the Complainant?'),
       'eligibility.sub_are_you_complainant',
       'no',
       TO_CLOB('As per the Integrated Ombudsman Scheme, a complaint filed through an advocate must be filed by the complainant themselves. Since you are not the complainant, this complaint cannot be processed.'),
       'eligibility.block_advocate_not_complainant',
       NULL,
       1, 1, 1, SYSTIMESTAMP, SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM ELIGIBILITY_QUESTION_MASTER e
                    WHERE e.SCHEME_VERSION = 'RBIOS_2021'
                      AND e.QUESTION_KEY = 'isComplainantSelf');

-- ═══ Part 3: translation key for the previously hardcoded block message ═══
-- eligibility.sub_are_you_complainant already exists in all ten locales (it was seeded for the
-- review-summary label), so only the new block message key is added here. Values for the nine Indian
-- locales are seeded by EligibilityTranslationSeeder; this migration only guarantees the key exists
-- so environments that never restart the seeder still resolve it.
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
SELECT 'eligibility.block_advocate_not_complainant', 'eligibility',
       'Block: advocate filing for a non-complainant',
       TO_CLOB('As per the Integrated Ombudsman Scheme, a complaint filed through an advocate must be filed by the complainant themselves. Since you are not the complainant, this complaint cannot be processed.')
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                    WHERE k.CODE = 'eligibility.block_advocate_not_complainant');

COMMIT;
/
