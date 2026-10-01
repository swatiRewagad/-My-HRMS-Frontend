-- V14: eligibility question scope narrowing + advocate sub-question master row
-- MySQL version
--
-- EligibilityQuestionMasterSeeder is insert-if-absent on (scheme_version, question_key), so changing
-- its defaults does NOT update rows already present. Both changes below therefore need a migration.
--
-- Part 1 (Decision D): four Ombudsman/forum questions were scoped NON_CEPC, which showed them for
-- every non-CEPC department — including any third or unknown department value. They are RBIO
-- questions, so narrow them to RBIO.
--
-- Part 2: BRD row 15 ("If Yes, then are you the Complainant?") had no master row at all. It was
-- hardcoded in the Angular template with no translation key, so it rendered in English in all ten
-- locales and its block message was hardcoded English prose. Seeding it as a master row moves the
-- text and the block rule into operator-maintained data.

-- ═══ Part 1: narrow NON_CEPC → RBIO ═══
-- Guarded on the current value so a deliberate operator re-scope is not clobbered, and so re-running
-- is a no-op.
UPDATE ELIGIBILITY_QUESTION_MASTER
   SET applicable_entity_type = 'RBIO',
       updated_at = NOW()
 WHERE scheme_version = 'RBIOS_2021'
   AND applicable_entity_type = 'NON_CEPC'
   AND question_key IN ('isSubJudice', 'alreadySettled', 'pendingBeforeOmbudsman', 'settledByOmbudsman');

-- ═══ Part 2: seed the advocate follow-up as a master row ═══
-- question_number continues the seeder's sequence (14 rows including this one).
-- clause_reference is deliberately left NULL: the Scheme clause barring an advocate-filed complaint
-- where the filer is not the complainant has NOT been verified. An unverified citation on a
-- citizen-facing closure denies statutory recourse, so it must be supplied by the business owner
-- rather than guessed.
INSERT INTO ELIGIBILITY_QUESTION_MASTER
    (scheme_version, applicable_entity_type, question_number, question_key, question_type,
     question_text, translation_key, block_on, block_message, block_message_key, clause_reference,
     non_maintainable, inline_sub_question, active, created_at, updated_at)
SELECT 'RBIOS_2021', 'RBIO', 14, 'isComplainantSelf', 'radio',
       'If Yes, then are you the Complainant?',
       'eligibility.sub_are_you_complainant',
       'no',
       'As per the Integrated Ombudsman Scheme, a complaint filed through an advocate must be filed by the complainant themselves. Since you are not the complainant, this complaint cannot be processed.',
       'eligibility.block_advocate_not_complainant',
       NULL,
       b'1', b'1', b'1', NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (
       SELECT 1 FROM (SELECT scheme_version, question_key FROM ELIGIBILITY_QUESTION_MASTER) x
        WHERE x.scheme_version = 'RBIOS_2021' AND x.question_key = 'isComplainantSelf');

-- ═══ Part 3: translation keys for the previously hardcoded text ═══
-- eligibility.sub_are_you_complainant already exists in all ten locales (it was seeded for the
-- review-summary label), so only the new block message key is added here. Values for the nine Indian
-- locales are seeded by EligibilityTranslationSeeder; this migration only guarantees the key exists
-- so environments that never restart the seeder still resolve it.
INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'eligibility.block_advocate_not_complainant', 'eligibility',
       'Block: advocate filing for a non-complainant',
       'As per the Integrated Ombudsman Scheme, a complaint filed through an advocate must be filed by the complainant themselves. Since you are not the complainant, this complaint cannot be processed.'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                    WHERE k.code = 'eligibility.block_advocate_not_complainant');
