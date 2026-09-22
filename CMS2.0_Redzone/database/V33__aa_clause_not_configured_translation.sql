-- V33: appeal.error_clause_not_configured in all ten locales
-- MySQL version. Oracle twin: database/oracle/V31__aa_clause_not_configured_translation.sql
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
--   TranslationService falls back to TRANSLATION_KEYS.default_value, English to every locale. Either
--   way a non-English speaker is told nothing useful at the exact moment their appeal is blocked.
--
-- MODULE = 'aa'
--
--   Deliberate, despite the 'appeal.' code prefix. AaTranslationSeeder hardcodes MODULE = "aa" for
--   every key it owns, so a dev database seeded by the CommandLineRunner gets module 'aa'. Using
--   'appeal' here would make the two paths disagree, and the per-module endpoint
--   /api/v1/i18n/translations/{locale}/{module} would then serve the key under a different module
--   depending on how the database was populated. The full-locale endpoint is unaffected either way.
--
-- Mirrored by cms-backend/.../config/AaTranslationSeeder.java (@Order(11)) for dev databases that
-- are populated by the CommandLineRunner seeders rather than by these scripts.
--
-- Re-running is safe. The key insert is INSERT ... WHERE NOT EXISTS on code; the value inserts are
-- INSERT ... WHERE NOT EXISTS on (translation_key_id, locale) and resolve the id by joining
-- TRANSLATION_KEYS on code, exactly as V32 does. Note the corollary of insert-if-absent: correcting
-- the wording later does NOT rewrite a row already committed — that needs a key-scoped UPDATE, the
-- way V32 Part A had to fix the Scheme-year drift.

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. Key, with the English text as default_value.
-- ═══════════════════════════════════════════════════════════════════════════
INSERT INTO TRANSLATION_KEYS (code, module, description, default_value, created_at, updated_at)
SELECT src.c, src.m, src.d, src.v, NOW(), NOW() FROM (
    SELECT 'appeal.error_clause_not_configured' AS c,
           'aa'                                 AS m,
           'HTTP 503 messageKey: closure clause missing from CLOSURE_CLAUSE_MASTER, appealability undecidable' AS d,
           'This complaint''s closure clause is not yet configured, so we cannot determine whether it can be appealed. Our team has been notified. Please try again shortly.' AS v
) src
WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS tk WHERE tk.code = src.c);

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. Localized values for the nine non-English locales.
-- ═══════════════════════════════════════════════════════════════════════════
INSERT INTO TRANSLATIONS (translation_key_id, locale, value, updated_at)
SELECT tk.id, src.locale, src.value, NOW()
FROM (
    SELECT 'appeal.error_clause_not_configured' AS c, 'hi' AS locale,
           'इस शिकायत का समापन खंड अभी कॉन्फ़िगर नहीं किया गया है, इसलिए हम यह निर्धारित नहीं कर सकते कि इसके विरुद्ध अपील की जा सकती है या नहीं। हमारी टीम को सूचित कर दिया गया है। कृपया कुछ ही समय में पुनः प्रयास करें।' AS value UNION ALL
    SELECT 'appeal.error_clause_not_configured', 'mr',
           'या तक्रारीचे समाप्ती कलम अद्याप कॉन्फिगर केलेले नाही, त्यामुळे यावर अपील करता येईल का हे आम्ही निश्चित करू शकत नाही. आमच्या पथकाला कळविण्यात आले आहे. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.' UNION ALL
    SELECT 'appeal.error_clause_not_configured', 'bn',
           'এই অভিযোগের নিষ্পত্তির ধারাটি এখনও কনফিগার করা হয়নি, তাই এর বিরুদ্ধে আপিল করা যাবে কিনা তা আমরা নির্ধারণ করতে পারছি না। আমাদের দলকে জানানো হয়েছে। অনুগ্রহ করে কিছুক্ষণ পরে আবার চেষ্টা করুন।' UNION ALL
    SELECT 'appeal.error_clause_not_configured', 'te',
           'ఈ ఫిర్యాదు ముగింపు నిబంధన ఇంకా కాన్ఫిగర్ చేయబడలేదు, కాబట్టి దీనిపై అప్పీలు చేయవచ్చా లేదా అని మేము నిర్ణయించలేము. మా బృందానికి తెలియజేయబడింది. దయచేసి కొద్దిసేపటి తర్వాత మళ్లీ ప్రయత్నించండి.' UNION ALL
    SELECT 'appeal.error_clause_not_configured', 'ta',
           'இந்த முறையீட்டின் முடிவுரை விதி இன்னும் அமைக்கப்படவில்லை, எனவே இதற்கு மேல்முறையீடு செய்ய முடியுமா என்பதை நாங்கள் தீர்மானிக்க முடியவில்லை. எங்கள் குழுவிற்குத் தெரிவிக்கப்பட்டுள்ளது. சிறிது நேரம் கழித்து மீண்டும் முயற்சிக்கவும்.' UNION ALL
    SELECT 'appeal.error_clause_not_configured', 'gu',
           'આ ફરિયાદનું સમાપન કલમ હજુ કૉન્ફિગર કરવામાં આવ્યું નથી, તેથી તેની સામે અપીલ કરી શકાય કે નહીં તે અમે નક્કી કરી શકતા નથી. અમારી ટીમને જાણ કરવામાં આવી છે. કૃપા કરીને થોડા સમય પછી ફરી પ્રયાસ કરો.' UNION ALL
    SELECT 'appeal.error_clause_not_configured', 'ur',
           'اس شکایت کی اختتامی شرط ابھی ترتیب نہیں دی گئی ہے، اس لیے ہم یہ طے نہیں کر سکتے کہ اس پر اپیل کی جا سکتی ہے یا نہیں۔ ہماری ٹیم کو مطلع کر دیا گیا ہے۔ براہ کرم کچھ دیر بعد دوبارہ کوشش کریں۔' UNION ALL
    SELECT 'appeal.error_clause_not_configured', 'kn',
           'ಈ ದೂರಿನ ಮುಕ್ತಾಯ ಷರತ್ತನ್ನು ಇನ್ನೂ ಕಾನ್ಫಿಗರ್ ಮಾಡಲಾಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಇದರ ವಿರುದ್ಧ ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಬಹುದೇ ಎಂಬುದನ್ನು ನಾವು ನಿರ್ಧರಿಸಲಾಗುತ್ತಿಲ್ಲ. ನಮ್ಮ ತಂಡಕ್ಕೆ ತಿಳಿಸಲಾಗಿದೆ. ದಯವಿಟ್ಟು ಸ್ವಲ್ಪ ಸಮಯದ ನಂತರ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.' UNION ALL
    SELECT 'appeal.error_clause_not_configured', 'ml',
           'ഈ പരാതിയുടെ അവസാനിപ്പിക്കൽ വ്യവസ്ഥ ഇതുവരെ കോൺഫിഗർ ചെയ്തിട്ടില്ല, അതിനാൽ ഇതിനെതിരെ അപ്പീൽ നൽകാനാകുമോ എന്ന് ഞങ്ങൾക്ക് നിർണ്ണയിക്കാൻ കഴിയുന്നില്ല. ഞങ്ങളുടെ ടീമിനെ അറിയിച്ചിട്ടുണ്ട്. കുറച്ച് സമയത്തിന് ശേഷം വീണ്ടും ശ്രമിക്കുക.'
) src
JOIN TRANSLATION_KEYS tk ON tk.code = src.c
WHERE NOT EXISTS (
    SELECT 1 FROM TRANSLATIONS t
    WHERE t.translation_key_id = tk.id AND t.locale = src.locale
);

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. Index guard, information_schema style (MySQL 8.4 has no CREATE INDEX IF NOT EXISTS).
--    Retained from V32: idx_tkey_module exists in every environment seen, but a database created
--    before that index was added must not fail this migration.
-- ═══════════════════════════════════════════════════════════════════════════
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
