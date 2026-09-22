-- V32: Scheme-year correction + AA classification/role translation keys
-- MySQL version. Oracle twin: database/oracle/V30__aa_scheme_year_and_classification_translations.sql
--
-- PART A — corrective UPDATEs for a legal-citation defect.
--
--   Several citizen-facing seeded rows cited the "Reserve Bank - Integrated Ombudsman Scheme, 2026".
--   The Scheme in force is the 2021 edition; cms.eligibility.scheme-name in application.yml is
--   authoritative and reads "Reserve Bank - Integrated Ombudsman Scheme, 2021". Serving a wrong
--   Scheme year to a complainant in a statutory citation is a correctness defect, not cosmetic.
--
--   The seeders (EligibilityTranslationSeeder, PortalFullTranslationSeeder, and the oracle-*.sql
--   scripts) are all INSERT-IF-ABSENT. Fixing the seeder text therefore does NOTHING to a database
--   that already holds the bad row — hence these UPDATEs.
--
--   Two traps this migration deliberately avoids:
--
--   (a) SCOPED BY KEY CODE, never by an English substring. The offending rows exist in ten locales,
--       stored in native scripts. A predicate like `value LIKE '%Scheme, 2026%'` matches only the
--       English row, so nine locales would silently keep the wrong year while the migration reported
--       success. Every statement below joins TRANSLATION_KEYS and filters on `code IN (...)`.
--
--   (b) BENGALI DIGITS. The Bengali rows render the year in Bengali numerals — ২০২৬, not 2026. An
--       ASCII REPLACE('2026','2021') skips them entirely. The Bengali numeral form is handled by its
--       own REPLACE, applied unconditionally alongside the ASCII one (REPLACE is a no-op when the
--       needle is absent, so a single pass fixes every script).
--
--   Only the YEAR changes. Clause numbers are untouched: '10(1)(j)' stays '10(1)(j)', and the
--   Bengali ১০(১)(জে) is likewise left alone.
--
-- PART B — AA classification + role vocabulary in all ten locales.
--
--   classification.* is read by the shared status badge, which the AA dashboard and appeal detail
--   render with keyPrefix="classification"; the badge lowercases the status value, so APPEAL resolves
--   classification.appeal. aa.role_* is read by AaDashboardComponent.roleLabels. Absent these rows
--   the UI shows raw enums / raw key names to the user.
--
--   Mirrored by cms-backend/.../config/AaTranslationSeeder.java (@Order(11)) for dev databases that
--   are populated by the CommandLineRunner seeders rather than by these scripts.
--
-- Re-running is safe. MySQL 8.4 has no ADD COLUMN / CREATE INDEX IF NOT EXISTS, so structural
-- changes (none are needed here, but the guard idiom is retained for the index check) go through
-- information_schema; the UPDATEs are idempotent because 2021 -> 2021 is a no-op; and every seed is
-- INSERT ... WHERE NOT EXISTS.

-- ═══════════════════════════════════════════════════════════════════════════
-- PART A. Scheme-year correction — key-scoped, script-aware
-- ═══════════════════════════════════════════════════════════════════════════

-- A1. English defaults on the key rows.
UPDATE TRANSLATION_KEYS
SET default_value = REPLACE(default_value, 'Scheme, 2026', 'Scheme, 2021')
WHERE code IN ('eligibility.block_not_filed', 'home.ct_regulated')
  AND default_value LIKE '%Scheme, 2026%';

-- A2. Localized rows. Scoped by key code so all ten locales are reached, and both the ASCII and the
--     Bengali numeral forms of the year are rewritten in the same pass.
UPDATE TRANSLATIONS t
JOIN TRANSLATION_KEYS tk ON tk.id = t.translation_key_id
SET t.value = REPLACE(REPLACE(t.value, '2026', '2021'), '২০২৬', '২০২১'),
    t.updated_at = NOW()
WHERE tk.code IN ('eligibility.block_not_filed', 'home.ct_regulated')
  AND (t.value LIKE '%2026%' OR t.value LIKE '%২০২৬%');

-- A3. Safety net for any other translation row that picked up the wrong Scheme year from an earlier
--     seed run. Still not an English-substring match: the ASCII/Bengali year predicate is the only
--     thing that is script-specific, and both variants are listed. Restricted to the modules that
--     carry Scheme citations so an unrelated row containing a literal 2026 (a date, a target year)
--     is not rewritten.
UPDATE TRANSLATIONS t
JOIN TRANSLATION_KEYS tk ON tk.id = t.translation_key_id
SET t.value = REPLACE(REPLACE(t.value, '2026', '2021'), '২০২৬', '২০২১'),
    t.updated_at = NOW()
WHERE tk.module IN ('eligibility', 'home')
  AND (t.value LIKE '%2026%' OR t.value LIKE '%২০২৬%');

UPDATE TRANSLATION_KEYS
SET default_value = REPLACE(default_value, '2026', '2021')
WHERE module IN ('eligibility', 'home')
  AND default_value LIKE '%Ombudsman Scheme, 2026%';

-- ═══════════════════════════════════════════════════════════════════════════
-- PART B. AA classification + role translation keys (10 locales)
-- ═══════════════════════════════════════════════════════════════════════════

-- B1. Keys, with the English text as default_value.
INSERT INTO TRANSLATION_KEYS (code, module, description, default_value, created_at, updated_at)
SELECT src.c, src.m, src.d, src.v, NOW(), NOW() FROM (
    SELECT 'classification.appeal'          AS c, 'aa' AS m, 'AA classification: appeal'                  AS d, 'Appeal'                                    AS v UNION ALL
    SELECT 'classification.representation',       'aa',       'AA classification: representation',              'Representation'                            UNION ALL
    SELECT 'classification.unknown',              'aa',       'AA classification: unknown/unset',               'Unknown'                                   UNION ALL
    SELECT 'aa.overridden',                       'aa',       'Short badge: classification manually changed',   'Overridden'                                UNION ALL
    SELECT 'aa.classification_overridden',        'aa',       'Tooltip: classification manually changed',        'Classification was manually overridden'   UNION ALL
    SELECT 'aa.role_do',                          'aa',       'AA role: dealing officer',                        'Dealing Officer'                          UNION ALL
    SELECT 'aa.role_reviewer',                    'aa',       'AA role: reviewer',                               'Reviewer'                                 UNION ALL
    SELECT 'aa.role_secretariat',                 'aa',       'AA role: secretariat',                            'Secretariat'                              UNION ALL
    SELECT 'aa.role_admin',                       'aa',       'AA role: administrator',                          'AA Administrator'
) src
WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS tk WHERE tk.code = src.c);

-- B2. Localized values for the nine non-English locales.
INSERT INTO TRANSLATIONS (translation_key_id, locale, value, updated_at)
SELECT tk.id, src.locale, src.value, NOW()
FROM (
    -- hi
    SELECT 'classification.appeal'         AS c, 'hi' AS locale, 'अपील'                                        AS value UNION ALL
    SELECT 'classification.representation',      'hi',           'अभ्यावेदन'                                          UNION ALL
    SELECT 'classification.unknown',             'hi',           'अज्ञात'                                             UNION ALL
    SELECT 'aa.overridden',                      'hi',           'अधिभावी'                                            UNION ALL
    SELECT 'aa.classification_overridden',       'hi',           'वर्गीकरण को मैन्युअल रूप से बदला गया था'                        UNION ALL
    SELECT 'aa.role_do',                         'hi',           'कार्यकारी अधिकारी'                                     UNION ALL
    SELECT 'aa.role_reviewer',                   'hi',           'समीक्षक'                                            UNION ALL
    SELECT 'aa.role_secretariat',                'hi',           'सचिवालय'                                            UNION ALL
    SELECT 'aa.role_admin',                      'hi',           'अपीलीय प्राधिकारी प्रशासक'                                UNION ALL
    -- mr
    SELECT 'classification.appeal',              'mr',           'अपील'                                              UNION ALL
    SELECT 'classification.representation',      'mr',           'निवेदन'                                             UNION ALL
    SELECT 'classification.unknown',             'mr',           'अज्ञात'                                             UNION ALL
    SELECT 'aa.overridden',                      'mr',           'अधिक्रमित'                                           UNION ALL
    SELECT 'aa.classification_overridden',       'mr',           'वर्गीकरण मॅन्युअली बदलले गेले होते'                            UNION ALL
    SELECT 'aa.role_do',                         'mr',           'कार्यवाहक अधिकारी'                                     UNION ALL
    SELECT 'aa.role_reviewer',                   'mr',           'पुनरावलोकनकर्ता'                                       UNION ALL
    SELECT 'aa.role_secretariat',                'mr',           'सचिवालय'                                            UNION ALL
    SELECT 'aa.role_admin',                      'mr',           'अपिलीय प्राधिकरण प्रशासक'                                UNION ALL
    -- bn
    SELECT 'classification.appeal',              'bn',           'আপিল'                                              UNION ALL
    SELECT 'classification.representation',      'bn',           'আবেদন'                                             UNION ALL
    SELECT 'classification.unknown',             'bn',           'অজানা'                                             UNION ALL
    SELECT 'aa.overridden',                      'bn',           'অগ্রাহ্য করা হয়েছে'                                     UNION ALL
    SELECT 'aa.classification_overridden',       'bn',           'শ্রেণিবিন্যাস হাতে পরিবর্তন করা হয়েছিল'                       UNION ALL
    SELECT 'aa.role_do',                         'bn',           'কার্যনির্বাহী আধিকারিক'                                   UNION ALL
    SELECT 'aa.role_reviewer',                   'bn',           'পর্যালোচক'                                           UNION ALL
    SELECT 'aa.role_secretariat',                'bn',           'সচিবালয়'                                            UNION ALL
    SELECT 'aa.role_admin',                      'bn',           'আপিল কর্তৃপক্ষ প্রশাসক'                                  UNION ALL
    -- te
    SELECT 'classification.appeal',              'te',           'అప్పీలు'                                            UNION ALL
    SELECT 'classification.representation',      'te',           'వినతి'                                              UNION ALL
    SELECT 'classification.unknown',             'te',           'తెలియదు'                                            UNION ALL
    SELECT 'aa.overridden',                      'te',           'అధిగమించబడింది'                                       UNION ALL
    SELECT 'aa.classification_overridden',       'te',           'వర్గీకరణ మాన్యువల్‌గా మార్చబడింది'                            UNION ALL
    SELECT 'aa.role_do',                         'te',           'నిర్వహణ అధికారి'                                       UNION ALL
    SELECT 'aa.role_reviewer',                   'te',           'సమీక్షకుడు'                                          UNION ALL
    SELECT 'aa.role_secretariat',                'te',           'సచివాలయం'                                           UNION ALL
    SELECT 'aa.role_admin',                      'te',           'అప్పీలు అధికార నిర్వాహకుడు'                              UNION ALL
    -- ta
    SELECT 'classification.appeal',              'ta',           'மேல்முறையீடு'                                        UNION ALL
    SELECT 'classification.representation',      'ta',           'மனு'                                               UNION ALL
    SELECT 'classification.unknown',             'ta',           'தெரியவில்லை'                                         UNION ALL
    SELECT 'aa.overridden',                      'ta',           'மேலெழுதப்பட்டது'                                      UNION ALL
    SELECT 'aa.classification_overridden',       'ta',           'வகைப்பாடு கைமுறையாக மாற்றப்பட்டது'                        UNION ALL
    SELECT 'aa.role_do',                         'ta',           'நடவடிக்கை அதிகாரி'                                    UNION ALL
    SELECT 'aa.role_reviewer',                   'ta',           'மறுஆய்வாளர்'                                         UNION ALL
    SELECT 'aa.role_secretariat',                'ta',           'செயலகம்'                                            UNION ALL
    SELECT 'aa.role_admin',                      'ta',           'மேல்முறையீட்டு ஆணையர் நிர்வாகி'                          UNION ALL
    -- gu
    SELECT 'classification.appeal',              'gu',           'અપીલ'                                              UNION ALL
    SELECT 'classification.representation',      'gu',           'રજૂઆત'                                             UNION ALL
    SELECT 'classification.unknown',             'gu',           'અજ્ઞાત'                                             UNION ALL
    SELECT 'aa.overridden',                      'gu',           'અધિક્રમિત'                                           UNION ALL
    SELECT 'aa.classification_overridden',       'gu',           'વર્ગીકરણ મેન્યુઅલી બદલવામાં આવ્યું હતું'                       UNION ALL
    SELECT 'aa.role_do',                         'gu',           'કાર્યવાહક અધિકારી'                                     UNION ALL
    SELECT 'aa.role_reviewer',                   'gu',           'સમીક્ષક'                                            UNION ALL
    SELECT 'aa.role_secretariat',                'gu',           'સચિવાલય'                                            UNION ALL
    SELECT 'aa.role_admin',                      'gu',           'અપીલ સત્તાધિકારી પ્રશાસક'                                UNION ALL
    -- ur
    SELECT 'classification.appeal',              'ur',           'اپیل'                                              UNION ALL
    SELECT 'classification.representation',      'ur',           'درخواست'                                            UNION ALL
    SELECT 'classification.unknown',             'ur',           'نامعلوم'                                            UNION ALL
    SELECT 'aa.overridden',                      'ur',           'منسوخ شدہ'                                          UNION ALL
    SELECT 'aa.classification_overridden',       'ur',           'درجہ بندی کو دستی طور پر تبدیل کیا گیا تھا'               UNION ALL
    SELECT 'aa.role_do',                         'ur',           'کارروائی افسر'                                       UNION ALL
    SELECT 'aa.role_reviewer',                   'ur',           'جائزہ کار'                                          UNION ALL
    SELECT 'aa.role_secretariat',                'ur',           'سیکرٹریٹ'                                           UNION ALL
    SELECT 'aa.role_admin',                      'ur',           'اپیلٹ اتھارٹی منتظم'                                  UNION ALL
    -- kn
    SELECT 'classification.appeal',              'kn',           'ಮೇಲ್ಮನವಿ'                                           UNION ALL
    SELECT 'classification.representation',      'kn',           'ಮನವಿ'                                              UNION ALL
    SELECT 'classification.unknown',             'kn',           'ಅಜ್ಞಾತ'                                             UNION ALL
    SELECT 'aa.overridden',                      'kn',           'ಅತಿಕ್ರಮಿಸಲಾಗಿದೆ'                                       UNION ALL
    SELECT 'aa.classification_overridden',       'kn',           'ವರ್ಗೀಕರಣವನ್ನು ಕೈಯಾರೆ ಬದಲಾಯಿಸಲಾಗಿದೆ'                     UNION ALL
    SELECT 'aa.role_do',                         'kn',           'ಕಾರ್ಯನಿರ್ವಹಣಾ ಅಧಿಕಾರಿ'                                 UNION ALL
    SELECT 'aa.role_reviewer',                   'kn',           'ಪರಿಶೀಲಕ'                                            UNION ALL
    SELECT 'aa.role_secretariat',                'kn',           'ಸಚಿವಾಲಯ'                                            UNION ALL
    SELECT 'aa.role_admin',                      'kn',           'ಮೇಲ್ಮನವಿ ಪ್ರಾಧಿಕಾರ ನಿರ್ವಾಹಕ'                             UNION ALL
    -- ml
    SELECT 'classification.appeal',              'ml',           'അപ്പീൽ'                                            UNION ALL
    SELECT 'classification.representation',      'ml',           'നിവേദനം'                                            UNION ALL
    SELECT 'classification.unknown',             'ml',           'അജ്ഞാതം'                                            UNION ALL
    SELECT 'aa.overridden',                      'ml',           'അതിലംഘിച്ചു'                                          UNION ALL
    SELECT 'aa.classification_overridden',       'ml',           'വർഗ്ഗീകരണം സ്വമേധയാ മാറ്റിയിരുന്നു'                        UNION ALL
    SELECT 'aa.role_do',                         'ml',           'നടപടി ഓഫീസർ'                                        UNION ALL
    SELECT 'aa.role_reviewer',                   'ml',           'പുനഃപരിശോധകൻ'                                       UNION ALL
    SELECT 'aa.role_secretariat',                'ml',           'സെക്രട്ടേറിയറ്റ്'                                        UNION ALL
    SELECT 'aa.role_admin',                      'ml',           'അപ്പീൽ അധികാരി അഡ്മിനിസ്ട്രേറ്റർ'
) src
JOIN TRANSLATION_KEYS tk ON tk.code = src.c
WHERE NOT EXISTS (
    SELECT 1 FROM TRANSLATIONS t
    WHERE t.translation_key_id = tk.id AND t.locale = src.locale
);

-- B3. Index guard, information_schema style (MySQL 8.4 has no CREATE INDEX IF NOT EXISTS).
--     idx_tkey_module already exists in every environment seen; the guard makes this migration
--     safe on a database created before that index was added.
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'TRANSLATION_KEYS'
               AND INDEX_NAME = 'idx_tkey_module');
SET @sql := IF(@idx = 0,
               'CREATE INDEX idx_tkey_module ON TRANSLATION_KEYS(module)',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
