-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────
-- V105 — UST13 Regulated Entity selection: the field-specific mandatory error, the search-box strings
-- and the entity-master-unavailable notice, in en + 9 locales.  MySQL version.
--
-- (V104 was taken by a parallel session between this change being written and landed, hence 105.)
--
-- WHY A MIGRATION AND NOT JUST A SEEDER EDIT. EligibilityTranslationSeeder is insert-if-absent BY KEY
-- CODE, and each locale block is insert-if-absent by (KEY, LOCALE). Adding the keys to the Java (done
-- in the same change) therefore populates only a database that has NEVER been seeded; every existing
-- environment already holds the eligibility keys, and a seeder edit alone would never give it these
-- six rows. Both halves are required, and this half is what makes the strings appear in an
-- already-seeded database.
--
-- WHAT EACH KEY IS FOR.
--   eligibility.entity_mandatory            UST13 S2. The entity select is a NAMED field, so its
--                                           mandatory error names the field. The wizard previously
--                                           emitted eligibility.response_mandatory ("Response is
--                                           mandatory.") here, which asks the citizen to answer a
--                                           "response" on a screen with no question to respond to.
--                                           response_mandatory is deliberately NOT touched: it stays
--                                           correct for the Yes/No maintainability questions
--                                           (UST14/UST18 S3), which is why a second key exists rather
--                                           than a reword of the first.
--   eligibility.entity_search_label         Accessible name for the search box.
--   eligibility.entity_search_placeholder   Its placeholder — which is what tells a citizen they may
--                                           search by entity TYPE as well as by name (UST13 S3).
--   eligibility.entity_search_clear         Accessible name for the clear-search button (QA10).
--   eligibility.entity_search_no_results    UST13 QA8. Carries {{term}}.
--   eligibility.entities_unavailable        The entity master could not be fetched. Previously that
--                                           failure was swallowed into an empty option list, which is
--                                           indistinguishable from "RBI regulates nothing" and leaves
--                                           the citizen unable to file with nothing explaining why.
--
-- {{term}} AND ITS CONSUMER. A placeholder is only an improvement if something interpolates it.
-- TranslationService.translate(key, params?) takes params OPTIONALLY, so `{{ key | translate }}` on
-- this key would render a literal "{{term}}" to the citizen — a defect this project has already had to
-- fix once. The consumer is therefore a component getter (entitySearchNoResultsText) which calls
-- translate() WITH the params map; the template renders that getter and never the pipe on this key.
-- The term is bound as text, so a term such as `<img src=x onerror=alert(1)>` is escaped and shown,
-- not executed.
--
-- RE-RUNNABLE. Every insert is guarded on the row being ABSENT (NOT EXISTS on CODE for the key, and on
-- (TRANSLATION_KEY_ID, LOCALE) for each translation), so a second run matches nothing and any wording
-- an operator has since revised is left alone.
-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────

-- ── eligibility.entity_mandatory ──
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_mandatory', 'eligibility', 'Error when no Regulated Entity is selected', 'Regulated Entity Name is mandatory.', NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_mandatory');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', 'विनियमित संस्था का नाम अनिवार्य है।', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', 'नियमित संस्थेचे नाव अनिवार्य आहे.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', 'নিয়ন্ত্রিত সংস্থার নাম বাধ্যতামূলক।', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', 'నియంత్రిత సంస్థ పేరు తప్పనిసరి.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', 'ஒழுங்குமுறை நிறுவனத்தின் பெயர் கட்டாயமாகும்.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', 'નિયમન કરાયેલ સંસ્થાનું નામ ફરજિયાત છે.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', 'ریگولیٹڈ ادارے کا نام لازمی ہے۔', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', 'ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಹೆಸರು ಕಡ್ಡಾಯವಾಗಿದೆ.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', 'നിയന്ത്രിത സ്ഥാപനത്തിന്റെ പേര് നിർബന്ധമാണ്.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- ── eligibility.entity_search_label ──
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_search_label', 'eligibility', 'Accessible label for the RE search box', 'Search Regulated Entity by name or type', NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_search_label');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', 'नाम या प्रकार से विनियमित संस्था खोजें', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', 'नाव किंवा प्रकारानुसार नियमित संस्था शोधा', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', 'নাম বা ধরন অনুসারে নিয়ন্ত্রিত সংস্থা খুঁজুন', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', 'పేరు లేదా రకం ఆధారంగా నియంత్రిత సంస్థను వెతకండి', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', 'பெயர் அல்லது வகை மூலம் ஒழுங்குமுறை நிறுவனத்தைத் தேடுங்கள்', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', 'નામ અથવા પ્રકાર દ્વારા નિયમન કરાયેલ સંસ્થા શોધો', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', 'نام یا قسم کے ذریعے ریگولیٹڈ ادارہ تلاش کریں', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', 'ಹೆಸರು ಅಥವಾ ಪ್ರಕಾರದ ಮೂಲಕ ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯನ್ನು ಹುಡುಕಿ', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', 'പേര് അല്ലെങ്കിൽ തരം അനുസരിച്ച് നിയന്ത്രിത സ്ഥാപനം തിരയുക', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- ── eligibility.entity_search_placeholder ──
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_search_placeholder', 'eligibility', 'Placeholder for the RE search box', 'Search by entity name or entity type', NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_search_placeholder');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', 'संस्था का नाम या संस्था का प्रकार खोजें', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', 'संस्थेचे नाव किंवा संस्थेचा प्रकार शोधा', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', 'সংস্থার নাম বা সংস্থার ধরন খুঁজুন', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', 'సంస్థ పేరు లేదా సంస్థ రకాన్ని వెతకండి', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', 'நிறுவனப் பெயர் அல்லது நிறுவன வகையைத் தேடுங்கள்', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', 'સંસ્થાનું નામ અથવા સંસ્થાનો પ્રકાર શોધો', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', 'ادارے کا نام یا ادارے کی قسم تلاش کریں', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', 'ಸಂಸ್ಥೆಯ ಹೆಸರು ಅಥವಾ ಸಂಸ್ಥೆಯ ಪ್ರಕಾರವನ್ನು ಹುಡುಕಿ', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', 'സ്ഥാപനത്തിന്റെ പേരോ സ്ഥാപനത്തിന്റെ തരമോ തിരയുക', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- ── eligibility.entity_search_clear ──
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_search_clear', 'eligibility', 'Accessible label for the clear-search button', 'Clear search', NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_search_clear');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', 'खोज साफ़ करें', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', 'शोध साफ करा', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', 'অনুসন্ধান মুছুন', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', 'వెతుకులాటను తొలగించండి', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', 'தேடலை அழிக்கவும்', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', 'શોધ સાફ કરો', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', 'تلاش صاف کریں', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', 'ಹುಡುಕಾಟವನ್ನು ಅಳಿಸಿ', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', 'തിരയൽ മായ്ക്കുക', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- ── eligibility.entity_search_no_results ──
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_search_no_results', 'eligibility', 'Shown when the RE search matches nothing', 'No results found for "{{term}}".', NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_search_no_results');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', '"{{term}}" के लिए कोई परिणाम नहीं मिला।', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', '"{{term}}" साठी कोणतेही परिणाम आढळले नाहीत.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', '"{{term}}"-এর জন্য কোনো ফলাফল পাওয়া যায়নি।', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', '"{{term}}" కోసం ఫలితాలు కనుగొనబడలేదు.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', '"{{term}}" க்கான முடிவுகள் எதுவும் இல்லை.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', '"{{term}}" માટે કોઈ પરિણામ મળ્યું નથી.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', '"{{term}}" کے لیے کوئی نتیجہ نہیں ملا۔', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', '"{{term}}" ಗಾಗಿ ಯಾವುದೇ ಫಲಿತಾಂಶ ಸಿಗಲಿಲ್ಲ.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', '"{{term}}" എന്നതിന് ഫലങ്ങൾ ഒന്നും കണ്ടെത്തിയില്ല.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- ── eligibility.entities_unavailable ──
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entities_unavailable', 'eligibility', 'Shown when the Regulated Entity master cannot be loaded', 'The list of Regulated Entities could not be loaded. Please retry — a complaint cannot be filed without naming an entity.', NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entities_unavailable');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', 'विनियमित संस्थाओं की सूची लोड नहीं हो सकी। कृपया पुनः प्रयास करें — संस्था का नाम बताए बिना शिकायत दर्ज नहीं की जा सकती।', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', 'नियमित संस्थांची यादी लोड होऊ शकली नाही. कृपया पुन्हा प्रयत्न करा — संस्थेचे नाव न देता तक्रार दाखल करता येत नाही.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', 'নিয়ন্ত্রিত সংস্থার তালিকা লোড করা যায়নি। আবার চেষ্টা করুন — সংস্থার নাম না দিয়ে অভিযোগ দায়ের করা যায় না।', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', 'నియంత్రిత సంస్థల జాబితా లోడ్ కాలేదు. మళ్లీ ప్రయత్నించండి — సంస్థ పేరు చెప్పకుండా ఫిర్యాదు దాఖలు చేయలేరు.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', 'ஒழுங்குமுறை நிறுவனங்களின் பட்டியலை ஏற்ற முடியவில்லை. மீண்டும் முயலுங்கள் — நிறுவனத்தைக் குறிப்பிடாமல் புகார் அளிக்க முடியாது.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', 'નિયમન કરાયેલ સંસ્થાઓની સૂચિ લોડ થઈ શકી નથી. કૃપા કરીને ફરી પ્રયાસ કરો — સંસ્થાનું નામ આપ્યા વિના ફરિયાદ દાખલ કરી શકાતી નથી.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', 'ریگولیٹڈ اداروں کی فہرست لوڈ نہیں ہو سکی۔ براہِ کرم دوبارہ کوشش کریں — ادارے کا نام بتائے بغیر شکایت درج نہیں کی جا سکتی۔', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', 'ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಗಳ ಪಟ್ಟಿಯನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ — ಸಂಸ್ಥೆಯ ಹೆಸರು ಇಲ್ಲದೆ ದೂರು ದಾಖಲಿಸಲಾಗದು.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', 'നിയന്ത്രിത സ്ഥാപനങ്ങളുടെ പട്ടിക ലോഡ് ചെയ്യാനായില്ല. വീണ്ടും ശ്രമിക്കുക — സ്ഥാപനത്തിന്റെ പേര് നൽകാതെ പരാതി നൽകാനാവില്ല.', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');
