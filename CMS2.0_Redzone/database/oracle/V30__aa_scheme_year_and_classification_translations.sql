-- V30: Scheme-year correction + AA classification/role translation keys
-- Oracle version. MySQL twin: database/V32__aa_scheme_year_and_classification_translations.sql
--
-- PART A — corrective UPDATEs for a legal-citation defect.
--
--   Several citizen-facing seeded rows cited the "Reserve Bank - Integrated Ombudsman Scheme, 2026".
--   The Scheme in force is the 2021 edition; cms.eligibility.scheme-name in application.yml is
--   authoritative and reads "Reserve Bank - Integrated Ombudsman Scheme, 2021". Serving a wrong
--   Scheme year to a complainant in a statutory citation is a correctness defect, not cosmetic.
--
--   The seed scripts (oracle-translations-seed.sql, db/oracle_translation_seed.sql,
--   db/oracle_eligibility_translations_all_locales.sql) are all INSERT-IF-ABSENT / MERGE ... WHEN NOT
--   MATCHED. Correcting their text does NOTHING to a database that already holds the bad row — hence
--   these UPDATEs.
--
--   Two traps this migration deliberately avoids:
--
--   (a) SCOPED BY KEY CODE, never by an English substring. The offending rows exist in ten locales,
--       stored in native scripts. A predicate like `VALUE LIKE '%Scheme, 2026%'` matches only the
--       English row, so nine locales would silently keep the wrong year while the migration reported
--       success. Every statement below correlates on TRANSLATION_KEYS.CODE.
--
--   (b) BENGALI DIGITS. The Bengali rows render the year in Bengali numerals — N'২০২৬', not 2026. An
--       ASCII REPLACE('2026','2021') skips them entirely. The Bengali numeral form gets its own
--       REPLACE, applied in the same expression (REPLACE is a no-op when the needle is absent, so one
--       pass fixes every script).
--
--   Only the YEAR changes. Clause numbers are untouched: '10(1)(j)' stays '10(1)(j)', and the
--   Bengali N'১০(১)(জে)' is likewise left alone.
--
--   VALUE / DEFAULT_VALUE are CLOB here, so the predicates use DBMS_LOB.INSTR rather than LIKE.
--
-- PART B — AA classification + role vocabulary in all ten locales.
--
--   classification.* is read by the shared status badge (keyPrefix="classification"; the badge
--   lowercases the status value, so APPEAL resolves classification.appeal). aa.role_* is read by
--   AaDashboardComponent.roleLabels. Absent these rows the UI shows raw enums / raw key names.
--
--   Mirrored by cms-backend/.../config/AaTranslationSeeder.java (@Order(11)).
--
-- Re-running is safe: guards use USER_TAB_COLUMNS / USER_TABLES / USER_INDEXES, the UPDATEs are
-- no-ops once the year is 2021, and every seed is existence-checked before INSERT.
-- The N'' literals require the client to be NLS/AL32UTF8-capable (SQL*Plus: SET NLS_LANG accordingly).

SET DEFINE OFF;
WHENEVER SQLERROR CONTINUE;

-- ═══════════════════════════════════════════════════════════════════════════
-- PART A. Scheme-year correction — key-scoped, script-aware
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_tab NUMBER;
BEGIN
    -- Guard: only run where the modern TRANSLATION_KEYS(CODE, DEFAULT_VALUE) shape exists. The
    -- oldest schema revision used KEY_CODE/CONTEXT and has no DEFAULT_VALUE column, and blindly
    -- referencing DEFAULT_VALUE there fails the whole script.
    SELECT COUNT(*) INTO v_tab FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'TRANSLATION_KEYS' AND COLUMN_NAME = 'DEFAULT_VALUE';
    IF v_tab = 0 THEN
        DBMS_OUTPUT.PUT_LINE('V30: TRANSLATION_KEYS.DEFAULT_VALUE absent, skipping Part A key fix.');
    ELSE
        -- A1. English defaults on the key rows.
        UPDATE TRANSLATION_KEYS
           SET DEFAULT_VALUE = TO_CLOB(REPLACE(DBMS_LOB.SUBSTR(DEFAULT_VALUE, 4000, 1),
                                               'Scheme, 2026', 'Scheme, 2021'))
         WHERE CODE IN ('eligibility.block_not_filed', 'home.ct_regulated')
           AND DBMS_LOB.INSTR(DEFAULT_VALUE, 'Scheme, 2026') > 0;

        -- A1b. Safety net across the Scheme-citing modules, still not an English-text scope: the
        -- module restriction stops an unrelated literal 2026 (a date, a target year) being rewritten.
        UPDATE TRANSLATION_KEYS
           SET DEFAULT_VALUE = TO_CLOB(REPLACE(DBMS_LOB.SUBSTR(DEFAULT_VALUE, 4000, 1),
                                               '2026', '2021'))
         WHERE MODULE IN ('eligibility', 'home')
           AND DBMS_LOB.INSTR(DEFAULT_VALUE, 'Ombudsman Scheme, 2026') > 0;
    END IF;

    -- A2. Localized rows, scoped by key CODE so all ten locales are reached. Both the ASCII and the
    -- Bengali numeral forms of the year are rewritten in the same expression.
    SELECT COUNT(*) INTO v_tab FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'TRANSLATIONS' AND COLUMN_NAME = 'TRANSLATION_KEY_ID';
    IF v_tab = 0 THEN
        DBMS_OUTPUT.PUT_LINE('V30: TRANSLATIONS.TRANSLATION_KEY_ID absent, skipping Part A value fix.');
    ELSE
        UPDATE TRANSLATIONS t
           SET t.VALUE = TO_CLOB(REPLACE(REPLACE(DBMS_LOB.SUBSTR(t.VALUE, 4000, 1),
                                                 '2026', '2021'),
                                         UNISTR('\09E8\09E6\09E8\09EC'),
                                         UNISTR('\09E8\09E6\09E8\09E7')))
         WHERE EXISTS (SELECT 1 FROM TRANSLATION_KEYS tk
                        WHERE tk.ID = t.TRANSLATION_KEY_ID
                          AND (tk.CODE IN ('eligibility.block_not_filed', 'home.ct_regulated')
                               OR tk.MODULE IN ('eligibility', 'home')))
           AND (DBMS_LOB.INSTR(t.VALUE, '2026') > 0
                OR DBMS_LOB.INSTR(t.VALUE, UNISTR('\09E8\09E6\09E8\09EC')) > 0);
    END IF;
    COMMIT;
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- PART B. AA classification + role translation keys (10 locales)
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    -- Insert-if-absent key. Mirrors the ek() helper in db/oracle_translation_seed.sql, except that
    -- this schema revision uses IDENTITY columns; the sequence fallback covers V1-shaped databases.
    PROCEDURE ak(p_code VARCHAR2, p_desc VARCHAR2, p_val VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM TRANSLATION_KEYS WHERE CODE = p_code;
        IF v_cnt = 0 THEN
            INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
            VALUES (p_code, 'aa', p_desc, p_val);
        END IF;
    END;

    -- Insert-if-absent localized value.
    PROCEDURE at_(p_code VARCHAR2, p_locale VARCHAR2, p_val VARCHAR2) IS
        v_key_id NUMBER;
        v_cnt    NUMBER;
    BEGIN
        BEGIN
            SELECT ID INTO v_key_id FROM TRANSLATION_KEYS WHERE CODE = p_code;
        EXCEPTION WHEN NO_DATA_FOUND THEN RETURN;
        END;
        SELECT COUNT(*) INTO v_cnt FROM TRANSLATIONS
         WHERE TRANSLATION_KEY_ID = v_key_id AND LOCALE = p_locale;
        IF v_cnt = 0 THEN
            INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE)
            VALUES (v_key_id, p_locale, p_val);
        END IF;
    END;
BEGIN
    -- ── Keys + English defaults ────────────────────────────────────────────
    ak('classification.appeal',         'AA classification: appeal',                      'Appeal');
    ak('classification.representation', 'AA classification: representation',              'Representation');
    ak('classification.unknown',        'AA classification: unknown/unset',               'Unknown');
    ak('aa.overridden',                 'Short badge: classification manually changed',   'Overridden');
    ak('aa.classification_overridden',  'Tooltip: classification manually changed',       'Classification was manually overridden');
    ak('aa.role_do',                    'AA role: dealing officer',                       'Dealing Officer');
    ak('aa.role_reviewer',              'AA role: reviewer',                              'Reviewer');
    ak('aa.role_secretariat',           'AA role: secretariat',                           'Secretariat');
    ak('aa.role_admin',                 'AA role: administrator',                         'AA Administrator');

    -- ── hi ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'hi', N'अपील');
    at_('classification.representation', 'hi', N'अभ्यावेदन');
    at_('classification.unknown',        'hi', N'अज्ञात');
    at_('aa.overridden',                 'hi', N'अधिभावी');
    at_('aa.classification_overridden',  'hi', N'वर्गीकरण को मैन्युअल रूप से बदला गया था');
    at_('aa.role_do',                    'hi', N'कार्यकारी अधिकारी');
    at_('aa.role_reviewer',              'hi', N'समीक्षक');
    at_('aa.role_secretariat',           'hi', N'सचिवालय');
    at_('aa.role_admin',                 'hi', N'अपीलीय प्राधिकारी प्रशासक');

    -- ── mr ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'mr', N'अपील');
    at_('classification.representation', 'mr', N'निवेदन');
    at_('classification.unknown',        'mr', N'अज्ञात');
    at_('aa.overridden',                 'mr', N'अधिक्रमित');
    at_('aa.classification_overridden',  'mr', N'वर्गीकरण मॅन्युअली बदलले गेले होते');
    at_('aa.role_do',                    'mr', N'कार्यवाहक अधिकारी');
    at_('aa.role_reviewer',              'mr', N'पुनरावलोकनकर्ता');
    at_('aa.role_secretariat',           'mr', N'सचिवालय');
    at_('aa.role_admin',                 'mr', N'अपिलीय प्राधिकरण प्रशासक');

    -- ── bn ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'bn', N'আপিল');
    at_('classification.representation', 'bn', N'আবেদন');
    at_('classification.unknown',        'bn', N'অজানা');
    at_('aa.overridden',                 'bn', N'অগ্রাহ্য করা হয়েছে');
    at_('aa.classification_overridden',  'bn', N'শ্রেণিবিন্যাস হাতে পরিবর্তন করা হয়েছিল');
    at_('aa.role_do',                    'bn', N'কার্যনির্বাহী আধিকারিক');
    at_('aa.role_reviewer',              'bn', N'পর্যালোচক');
    at_('aa.role_secretariat',           'bn', N'সচিবালয়');
    at_('aa.role_admin',                 'bn', N'আপিল কর্তৃপক্ষ প্রশাসক');

    -- ── te ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'te', N'అప్పీలు');
    at_('classification.representation', 'te', N'వినతి');
    at_('classification.unknown',        'te', N'తెలియదు');
    at_('aa.overridden',                 'te', N'అధిగమించబడింది');
    at_('aa.classification_overridden',  'te', N'వర్గీకరణ మాన్యువల్‌గా మార్చబడింది');
    at_('aa.role_do',                    'te', N'నిర్వహణ అధికారి');
    at_('aa.role_reviewer',              'te', N'సమీక్షకుడు');
    at_('aa.role_secretariat',           'te', N'సచివాలయం');
    at_('aa.role_admin',                 'te', N'అప్పీలు అధికార నిర్వాహకుడు');

    -- ── ta ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'ta', N'மேல்முறையீடு');
    at_('classification.representation', 'ta', N'மனு');
    at_('classification.unknown',        'ta', N'தெரியவில்லை');
    at_('aa.overridden',                 'ta', N'மேலெழுதப்பட்டது');
    at_('aa.classification_overridden',  'ta', N'வகைப்பாடு கைமுறையாக மாற்றப்பட்டது');
    at_('aa.role_do',                    'ta', N'நடவடிக்கை அதிகாரி');
    at_('aa.role_reviewer',              'ta', N'மறுஆய்வாளர்');
    at_('aa.role_secretariat',           'ta', N'செயலகம்');
    at_('aa.role_admin',                 'ta', N'மேல்முறையீட்டு ஆணையர் நிர்வாகி');

    -- ── gu ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'gu', N'અપીલ');
    at_('classification.representation', 'gu', N'રજૂઆત');
    at_('classification.unknown',        'gu', N'અજ્ઞાત');
    at_('aa.overridden',                 'gu', N'અધિક્રમિત');
    at_('aa.classification_overridden',  'gu', N'વર્ગીકરણ મેન્યુઅલી બદલવામાં આવ્યું હતું');
    at_('aa.role_do',                    'gu', N'કાર્યવાહક અધિકારી');
    at_('aa.role_reviewer',              'gu', N'સમીક્ષક');
    at_('aa.role_secretariat',           'gu', N'સચિવાલય');
    at_('aa.role_admin',                 'gu', N'અપીલ સત્તાધિકારી પ્રશાસક');

    -- ── ur ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'ur', N'اپیل');
    at_('classification.representation', 'ur', N'درخواست');
    at_('classification.unknown',        'ur', N'نامعلوم');
    at_('aa.overridden',                 'ur', N'منسوخ شدہ');
    at_('aa.classification_overridden',  'ur', N'درجہ بندی کو دستی طور پر تبدیل کیا گیا تھا');
    at_('aa.role_do',                    'ur', N'کارروائی افسر');
    at_('aa.role_reviewer',              'ur', N'جائزہ کار');
    at_('aa.role_secretariat',           'ur', N'سیکرٹریٹ');
    at_('aa.role_admin',                 'ur', N'اپیلٹ اتھارٹی منتظم');

    -- ── kn ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'kn', N'ಮೇಲ್ಮನವಿ');
    at_('classification.representation', 'kn', N'ಮನವಿ');
    at_('classification.unknown',        'kn', N'ಅಜ್ಞಾತ');
    at_('aa.overridden',                 'kn', N'ಅತಿಕ್ರಮಿಸಲಾಗಿದೆ');
    at_('aa.classification_overridden',  'kn', N'ವರ್ಗೀಕರಣವನ್ನು ಕೈಯಾರೆ ಬದಲಾಯಿಸಲಾಗಿದೆ');
    at_('aa.role_do',                    'kn', N'ಕಾರ್ಯನಿರ್ವಹಣಾ ಅಧಿಕಾರಿ');
    at_('aa.role_reviewer',              'kn', N'ಪರಿಶೀಲಕ');
    at_('aa.role_secretariat',           'kn', N'ಸಚಿವಾಲಯ');
    at_('aa.role_admin',                 'kn', N'ಮೇಲ್ಮನವಿ ಪ್ರಾಧಿಕಾರ ನಿರ್ವಾಹಕ');

    -- ── ml ─────────────────────────────────────────────────────────────────
    at_('classification.appeal',         'ml', N'അപ്പീൽ');
    at_('classification.representation', 'ml', N'നിവേദനം');
    at_('classification.unknown',        'ml', N'അജ്ഞാതം');
    at_('aa.overridden',                 'ml', N'അതിലംഘിച്ചു');
    at_('aa.classification_overridden',  'ml', N'വർഗ്ഗീകരണം സ്വമേധയാ മാറ്റിയിരുന്നു');
    at_('aa.role_do',                    'ml', N'നടപടി ഓഫീസർ');
    at_('aa.role_reviewer',              'ml', N'പുനഃപരിശോധകൻ');
    at_('aa.role_secretariat',           'ml', N'സെക്രട്ടേറിയറ്റ്');
    at_('aa.role_admin',                 'ml', N'അപ്പീൽ അധികാരി അഡ്മിനിസ്ട്രേറ്റർ');

    COMMIT;
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- Index guard (Oracle has no CREATE INDEX IF NOT EXISTS)
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_tab NUMBER;
    v_idx NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_tab FROM USER_TABLES WHERE TABLE_NAME = 'TRANSLATION_KEYS';
    SELECT COUNT(*) INTO v_idx FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_TKEY_MODULE';
    IF v_tab = 1 AND v_idx = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_TKEY_MODULE ON TRANSLATION_KEYS(MODULE)';
    END IF;
END;
/
