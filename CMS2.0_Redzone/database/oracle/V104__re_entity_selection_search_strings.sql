-- V104 - UST13 Regulated Entity selection: the field-specific mandatory error, the search-box strings
-- and the entity-master-unavailable notice, in en + 9 locales.  ORACLE version.
--
-- Mirrors database/V105__re_entity_selection_search_strings.sql. The two directories' V-numbers are not
-- in sync (the MySQL side was already at V104 when this was written), which is the existing convention
-- here - see oracle/V101 mirroring V104.
--
-- Oracle is the production database, so the MySQL migration alone would have left every deployed
-- environment without these six strings; the portal would then fall back to its in-component English
-- and the nine Indian locales would silently serve English on this screen.
--
-- WHY A MIGRATION AND NOT JUST A SEEDER EDIT. EligibilityTranslationSeeder is insert-if-absent BY KEY
-- CODE, and each locale block is insert-if-absent by (KEY, LOCALE). Adding the keys to the Java (done
-- in the same change) therefore populates only a database that has NEVER been seeded; every existing
-- environment already holds the eligibility keys, and a seeder edit alone would never give it these
-- six rows.
--
-- WHAT EACH KEY IS FOR.
--   eligibility.entity_mandatory            UST13 S2. The entity select is a NAMED field, so its
--                                           mandatory error names the field. The wizard previously
--                                           emitted eligibility.response_mandatory ("Response is
--                                           mandatory.") here, which asks the citizen to answer a
--                                           "response" on a screen with no question to respond to.
--                                           response_mandatory is deliberately NOT touched: it stays
--                                           correct for the Yes/No maintainability questions
--                                           (UST14/UST18 S3).
--   eligibility.entity_search_label         Accessible name for the search box.
--   eligibility.entity_search_placeholder   Its placeholder - which is what tells a citizen they may
--                                           search by entity TYPE as well as by name (UST13 S3).
--   eligibility.entity_search_clear         Accessible name for the clear-search button (QA10).
--   eligibility.entity_search_no_results    UST13 QA8. Carries {{term}}.
--   eligibility.entities_unavailable        The entity master could not be fetched. Previously that
--                                           failure was swallowed into an empty option list, which is
--                                           indistinguishable from "RBI regulates nothing".
--
-- {{term}} AND ITS CONSUMER. TranslationService.translate(key, params?) takes params OPTIONALLY, so
-- `{{ key | translate }}` on this key would render a literal "{{term}}" to the citizen. The consumer is
-- therefore a component getter (entitySearchNoResultsText) which calls translate() WITH the params map.
-- The term is bound as text, so a term such as `<img src=x onerror=alert(1)>` is escaped and shown.
--
-- ORACLE SPECIFICS. DEFAULT_VALUE and TRANSLATIONS.VALUE are CLOB (V4__complete_oracle_ddl.sql), so the
-- literals are wrapped in TO_CLOB rather than relying on implicit VARCHAR2 conversion - several of the
-- Devanagari/Tamil/Malayalam values exceed the 4000-byte literal limit once encoded as UTF-8.
-- Timestamps are SYSTIMESTAMP, not NOW().
--
-- RE-RUNNABLE. Every insert is guarded on the row being ABSENT (NOT EXISTS on CODE for the key, and on
-- (TRANSLATION_KEY_ID, LOCALE) for each translation), so a second run matches nothing and any wording
-- an operator has since revised is left alone.

-- == eligibility.entity_mandatory ==
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_mandatory', 'eligibility', 'Error when no Regulated Entity is selected',
       TO_CLOB('Regulated Entity Name is mandatory.'), SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_mandatory');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', TO_CLOB('विनियमित संस्था का नाम अनिवार्य है।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', TO_CLOB('नियमित संस्थेचे नाव अनिवार्य आहे.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', TO_CLOB('নিয়ন্ত্রিত সংস্থার নাম বাধ্যতামূলক।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', TO_CLOB('నియంత్రిత సంస్థ పేరు తప్పనిసరి.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', TO_CLOB('ஒழுங்குமுறை நிறுவனத்தின் பெயர் கட்டாயமாகும்.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', TO_CLOB('નિયમન કરાયેલ સંસ્થાનું નામ ફરજિયાત છે.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', TO_CLOB('ریگولیٹڈ ادارے کا نام لازمی ہے۔'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', TO_CLOB('ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಹೆಸರು ಕಡ್ಡಾಯವಾಗಿದೆ.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', TO_CLOB('നിയന്ത്രിത സ്ഥാപനത്തിന്റെ പേര് നിർബന്ധമാണ്.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_mandatory'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- == eligibility.entity_search_label ==
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_search_label', 'eligibility', 'Accessible label for the RE search box',
       TO_CLOB('Search Regulated Entity by name or type'), SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_search_label');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', TO_CLOB('नाम या प्रकार से विनियमित संस्था खोजें'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', TO_CLOB('नाव किंवा प्रकारानुसार नियमित संस्था शोधा'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', TO_CLOB('নাম বা ধরন অনুসারে নিয়ন্ত্রিত সংস্থা খুঁজুন'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', TO_CLOB('పేరు లేదా రకం ఆధారంగా నియంత్రిత సంస్థను వెతకండి'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', TO_CLOB('பெயர் அல்லது வகை மூலம் ஒழுங்குமுறை நிறுவனத்தைத் தேடுங்கள்'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', TO_CLOB('નામ અથવા પ્રકાર દ્વારા નિયમન કરાયેલ સંસ્થા શોધો'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', TO_CLOB('نام یا قسم کے ذریعے ریگولیٹڈ ادارہ تلاش کریں'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', TO_CLOB('ಹೆಸರು ಅಥವಾ ಪ್ರಕಾರದ ಮೂಲಕ ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯನ್ನು ಹುಡುಕಿ'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', TO_CLOB('പേര് അല്ലെങ്കിൽ തരം അനുസരിച്ച് നിയന്ത്രിത സ്ഥാപനം തിരയുക'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_label'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- == eligibility.entity_search_placeholder ==
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_search_placeholder', 'eligibility', 'Placeholder for the RE search box',
       TO_CLOB('Search by entity name or entity type'), SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_search_placeholder');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', TO_CLOB('संस्था का नाम या संस्था का प्रकार खोजें'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', TO_CLOB('संस्थेचे नाव किंवा संस्थेचा प्रकार शोधा'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', TO_CLOB('সংস্থার নাম বা সংস্থার ধরন খুঁজুন'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', TO_CLOB('సంస్థ పేరు లేదా సంస్థ రకాన్ని వెతకండి'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', TO_CLOB('நிறுவனப் பெயர் அல்லது நிறுவன வகையைத் தேடுங்கள்'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', TO_CLOB('સંસ્થાનું નામ અથવા સંસ્થાનો પ્રકાર શોધો'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', TO_CLOB('ادارے کا نام یا ادارے کی قسم تلاش کریں'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', TO_CLOB('ಸಂಸ್ಥೆಯ ಹೆಸರು ಅಥವಾ ಸಂಸ್ಥೆಯ ಪ್ರಕಾರವನ್ನು ಹುಡುಕಿ'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', TO_CLOB('സ്ഥാപനത്തിന്റെ പേരോ സ്ഥാപനത്തിന്റെ തരമോ തിരയുക'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_placeholder'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- == eligibility.entity_search_clear ==
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_search_clear', 'eligibility', 'Accessible label for the clear-search button',
       TO_CLOB('Clear search'), SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_search_clear');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', TO_CLOB('खोज साफ़ करें'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', TO_CLOB('शोध साफ करा'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', TO_CLOB('অনুসন্ধান মুছুন'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', TO_CLOB('వెతుకులాటను తొలగించండి'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', TO_CLOB('தேடலை அழிக்கவும்'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', TO_CLOB('શોધ સાફ કરો'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', TO_CLOB('تلاش صاف کریں'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', TO_CLOB('ಹುಡುಕಾಟವನ್ನು ಅಳಿಸಿ'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', TO_CLOB('തിരയൽ മായ്ക്കുക'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_clear'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- == eligibility.entity_search_no_results ==
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entity_search_no_results', 'eligibility', 'Shown when the RE search matches nothing',
       TO_CLOB('No results found for "{{term}}".'), SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entity_search_no_results');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', TO_CLOB('"{{term}}" के लिए कोई परिणाम नहीं मिला।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', TO_CLOB('"{{term}}" साठी कोणतेही परिणाम आढळले नाहीत.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', TO_CLOB('"{{term}}"-এর জন্য কোনো ফলাফল পাওয়া যায়নি।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', TO_CLOB('"{{term}}" కోసం ఫలితాలు కనుగొనబడలేదు.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', TO_CLOB('"{{term}}" க்கான முடிவுகள் எதுவும் இல்லை.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', TO_CLOB('"{{term}}" માટે કોઈ પરિણામ મળ્યું નથી.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', TO_CLOB('"{{term}}" کے لیے کوئی نتیجہ نہیں ملا۔'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', TO_CLOB('"{{term}}" ಗಾಗಿ ಯಾವುದೇ ಫಲಿತಾಂಶ ಸಿಗಲಿಲ್ಲ.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', TO_CLOB('"{{term}}" എന്നതിന് ഫലങ്ങൾ ഒന്നും കണ്ടെത്തിയില്ല.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entity_search_no_results'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- == eligibility.entities_unavailable ==
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.entities_unavailable', 'eligibility', 'Shown when the Regulated Entity master cannot be loaded',
       TO_CLOB('The list of Regulated Entities could not be loaded. Please retry — a complaint cannot be filed without naming an entity.'), SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.entities_unavailable');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', TO_CLOB('विनियमित संस्थाओं की सूची लोड नहीं हो सकी। कृपया पुनः प्रयास करें — संस्था का नाम बताए बिना शिकायत दर्ज नहीं की जा सकती।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', TO_CLOB('नियमित संस्थांची यादी लोड होऊ शकली नाही. कृपया पुन्हा प्रयत्न करा — संस्थेचे नाव न देता तक्रार दाखल करता येत नाही.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', TO_CLOB('নিয়ন্ত্রিত সংস্থার তালিকা লোড করা যায়নি। আবার চেষ্টা করুন — সংস্থার নাম না দিয়ে অভিযোগ দায়ের করা যায় না।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', TO_CLOB('నియంత్రిత సంస్థల జాబితా లోడ్ కాలేదు. మళ్లీ ప్రయత్నించండి — సంస్థ పేరు చెప్పకుండా ఫిర్యాదు దాఖలు చేయలేరు.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', TO_CLOB('ஒழுங்குமுறை நிறுவனங்களின் பட்டியலை ஏற்ற முடியவில்லை. மீண்டும் முயலுங்கள் — நிறுவனத்தைக் குறிப்பிடாமல் புகார் அளிக்க முடியாது.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', TO_CLOB('નિયમન કરાયેલ સંસ્થાઓની સૂચિ લોડ થઈ શકી નથી. કૃપા કરીને ફરી પ્રયાસ કરો — સંસ્થાનું નામ આપ્યા વિના ફરિયાદ દાખલ કરી શકાતી નથી.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', TO_CLOB('ریگولیٹڈ اداروں کی فہرست لوڈ نہیں ہو سکی۔ براہِ کرم دوبارہ کوشش کریں — ادارے کا نام بتائے بغیر شکایت درج نہیں کی جا سکتی۔'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', TO_CLOB('ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಗಳ ಪಟ್ಟಿಯನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ — ಸಂಸ್ಥೆಯ ಹೆಸರು ಇಲ್ಲದೆ ದೂರು ದಾಖಲಿಸಲಾಗದು.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', TO_CLOB('നിയന്ത്രിത സ്ഥാപനങ്ങളുടെ പട്ടിക ലോഡ് ചെയ്യാനായില്ല. വീണ്ടും ശ്രമിക്കുക — സ്ഥാപനത്തിന്റെ പേര് നൽകാതെ പരാതി നൽകാനാവില്ല.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.entities_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

COMMIT;
