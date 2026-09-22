-- V31: appeal.error_clause_not_configured in all ten locales
-- Oracle version. MySQL twin: database/V33__aa_clause_not_configured_translation.sql
--
-- WHY THIS KEY EXISTS
--
--   Appeal-vs-Representation is decided from the complaint's closure clause. When that clause is
--   absent from CLOSURE_CLAUSE_MASTER the classifier cannot decide, and it now FAILS CLOSED rather
--   than guessing: guessing "Representation" would wrongly deny a citizen statutory recourse under
--   the Scheme. The endpoint answers HTTP 503 with a retryable body carrying
--
--       "messageKey": "appeal.error_clause_not_configured"
--
--   The portal renders that key so the complainant reads the message in their own language instead
--   of an English literal. Without the rows below the portal shows the raw key text — or, because
--   TranslationService falls back to TRANSLATION_KEYS.DEFAULT_VALUE, English to every locale. Either
--   way a non-English speaker is told nothing useful at the exact moment their appeal is blocked.
--
-- MODULE = 'aa'
--
--   Deliberate, despite the 'appeal.' code prefix. AaTranslationSeeder hardcodes MODULE = "aa" for
--   every key it owns, so a dev database seeded by the CommandLineRunner gets module 'aa'. Using
--   'appeal' here would make the two paths disagree, and the per-module endpoint
--   /api/v1/i18n/translations/{locale}/{module} would then serve the key under a different module
--   depending on how the database was populated.
--
-- Mirrored by cms-backend/.../config/AaTranslationSeeder.java (@Order(11)).
--
-- Re-running is safe: the shape guards read USER_TABLES / USER_TAB_COLUMNS / USER_INDEXES, and both
-- the key and the localized values are existence-checked before INSERT. The N'' literals require an
-- NLS/AL32UTF8-capable client (SQL*Plus: SET NLS_LANG accordingly), otherwise the native scripts
-- land as '?'.

SET DEFINE OFF;
WHENEVER SQLERROR CONTINUE;

-- ═══════════════════════════════════════════════════════════════════════════
-- Key + localized values (10 locales)
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_keys_tab NUMBER;
    v_defval   NUMBER;
    v_tr_tab   NUMBER;

    -- Insert-if-absent key. Mirrors ak() in V30; this schema revision uses IDENTITY columns.
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
    -- Guard: only run where the modern TRANSLATION_KEYS(CODE, DEFAULT_VALUE) shape exists. The
    -- oldest schema revision used KEY_CODE/CONTEXT and has no DEFAULT_VALUE column; referencing
    -- DEFAULT_VALUE there would fail the whole script.
    SELECT COUNT(*) INTO v_keys_tab FROM USER_TABLES WHERE TABLE_NAME = 'TRANSLATION_KEYS';
    SELECT COUNT(*) INTO v_defval   FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'TRANSLATION_KEYS' AND COLUMN_NAME = 'DEFAULT_VALUE';

    IF v_keys_tab = 0 OR v_defval = 0 THEN
        DBMS_OUTPUT.PUT_LINE('V31: TRANSLATION_KEYS(DEFAULT_VALUE) absent, skipping seed.');
        RETURN;
    END IF;

    ak('appeal.error_clause_not_configured',
       'HTTP 503 messageKey: closure clause missing from CLOSURE_CLAUSE_MASTER, appealability undecidable',
       'This complaint''s closure clause is not yet configured, so we cannot determine whether it can be appealed. Our team has been notified. Please try again shortly.');

    -- Localized rows need TRANSLATIONS(TRANSLATION_KEY_ID); guard the same way.
    SELECT COUNT(*) INTO v_tr_tab FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'TRANSLATIONS' AND COLUMN_NAME = 'TRANSLATION_KEY_ID';

    IF v_tr_tab = 0 THEN
        DBMS_OUTPUT.PUT_LINE('V31: TRANSLATIONS.TRANSLATION_KEY_ID absent, key seeded without locales.');
        COMMIT;
        RETURN;
    END IF;

    at_('appeal.error_clause_not_configured', 'hi',
        N'इस शिकायत का समापन खंड अभी कॉन्फ़िगर नहीं किया गया है, इसलिए हम यह निर्धारित नहीं कर सकते कि इसके विरुद्ध अपील की जा सकती है या नहीं। हमारी टीम को सूचित कर दिया गया है। कृपया कुछ ही समय में पुनः प्रयास करें।');

    at_('appeal.error_clause_not_configured', 'mr',
        N'या तक्रारीचे समाप्ती कलम अद्याप कॉन्फिगर केलेले नाही, त्यामुळे यावर अपील करता येईल का हे आम्ही निश्चित करू शकत नाही. आमच्या पथकाला कळविण्यात आले आहे. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.');

    at_('appeal.error_clause_not_configured', 'bn',
        N'এই অভিযোগের নিষ্পত্তির ধারাটি এখনও কনফিগার করা হয়নি, তাই এর বিরুদ্ধে আপিল করা যাবে কিনা তা আমরা নির্ধারণ করতে পারছি না। আমাদের দলকে জানানো হয়েছে। অনুগ্রহ করে কিছুক্ষণ পরে আবার চেষ্টা করুন।');

    at_('appeal.error_clause_not_configured', 'te',
        N'ఈ ఫిర్యాదు ముగింపు నిబంధన ఇంకా కాన్ఫిగర్ చేయబడలేదు, కాబట్టి దీనిపై అప్పీలు చేయవచ్చా లేదా అని మేము నిర్ణయించలేము. మా బృందానికి తెలియజేయబడింది. దయచేసి కొద్దిసేపటి తర్వాత మళ్లీ ప్రయత్నించండి.');

    at_('appeal.error_clause_not_configured', 'ta',
        N'இந்த முறையீட்டின் முடிவுரை விதி இன்னும் அமைக்கப்படவில்லை, எனவே இதற்கு மேல்முறையீடு செய்ய முடியுமா என்பதை நாங்கள் தீர்மானிக்க முடியவில்லை. எங்கள் குழுவிற்குத் தெரிவிக்கப்பட்டுள்ளது. சிறிது நேரம் கழித்து மீண்டும் முயற்சிக்கவும்.');

    at_('appeal.error_clause_not_configured', 'gu',
        N'આ ફરિયાદનું સમાપન કલમ હજુ કૉન્ફિગર કરવામાં આવ્યું નથી, તેથી તેની સામે અપીલ કરી શકાય કે નહીં તે અમે નક્કી કરી શકતા નથી. અમારી ટીમને જાણ કરવામાં આવી છે. કૃપા કરીને થોડા સમય પછી ફરી પ્રયાસ કરો.');

    at_('appeal.error_clause_not_configured', 'ur',
        N'اس شکایت کی اختتامی شرط ابھی ترتیب نہیں دی گئی ہے، اس لیے ہم یہ طے نہیں کر سکتے کہ اس پر اپیل کی جا سکتی ہے یا نہیں۔ ہماری ٹیم کو مطلع کر دیا گیا ہے۔ براہ کرم کچھ دیر بعد دوبارہ کوشش کریں۔');

    at_('appeal.error_clause_not_configured', 'kn',
        N'ಈ ದೂರಿನ ಮುಕ್ತಾಯ ಷರತ್ತನ್ನು ಇನ್ನೂ ಕಾನ್ಫಿಗರ್ ಮಾಡಲಾಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಇದರ ವಿರುದ್ಧ ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಬಹುದೇ ಎಂಬುದನ್ನು ನಾವು ನಿರ್ಧರಿಸಲಾಗುತ್ತಿಲ್ಲ. ನಮ್ಮ ತಂಡಕ್ಕೆ ತಿಳಿಸಲಾಗಿದೆ. ದಯವಿಟ್ಟು ಸ್ವಲ್ಪ ಸಮಯದ ನಂತರ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.');

    at_('appeal.error_clause_not_configured', 'ml',
        N'ഈ പരാതിയുടെ അവസാനിപ്പിക്കൽ വ്യവസ്ഥ ഇതുവരെ കോൺഫിഗർ ചെയ്തിട്ടില്ല, അതിനാൽ ഇതിനെതിരെ അപ്പീൽ നൽകാനാകുമോ എന്ന് ഞങ്ങൾക്ക് നിർണ്ണയിക്കാൻ കഴിയുന്നില്ല. ഞങ്ങളുടെ ടീമിനെ അറിയിച്ചിട്ടുണ്ട്. കുറച്ച് സമയത്തിന് ശേഷം വീണ്ടും ശ്രമിക്കുക.');

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
