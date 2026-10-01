-- V113: the CEPC dashboard's "create complaint in the CRPC layout" action label
-- MySQL version
--
-- The CEPC dashboard offers TWO creation paths and they are not interchangeable:
--   • cepc.action.create_complaint  → the dashboard's own eight-field dialog (complainant, entity,
--     subject, description, priority, filing type). Already translated in all 11 locales.
--   • cepc.action.create_complaint_crpc → /crpc/physical-letter, the CRPC intake LAYOUT, which
--     additionally collects Mode of Receipt, Category, Complaint/Suggestion Type, Module Name, State
--     and District. A DO taking a complaint off a physical letter needs that form, not the dialog.
--
-- Only the second key was missing, so the button rendered the raw key `cepc.action.create_complaint_crpc`
-- as its own label. This adds the key and all eleven locales. Nothing else in the i18n tables is touched.
--
-- ═══ WHY THIS IS A MIGRATION AND NOT A CODE CONSTANT ═══
-- Every label in this application is a TRANSLATION_KEYS row resolved at runtime through
-- GET /api/v1/i18n/translations/{lang}; a literal in the template renders identically in all eleven
-- locales and is the defect that left CRPC and CEPC untranslated. There is no frontend string table to
-- add this to.
--
-- ═══ RE-RUNNABLE ═══
-- Every statement is guarded by NOT EXISTS on the natural key (CODE for the key, (KEY_ID, LOCALE) for
-- each translation), so applying this file twice inserts nothing the second time. That matters because
-- this repository has NO FLYWAY — database/*.sql is applied by hand, so a file must tolerate being run
-- again by someone who cannot tell whether it already was.
--
-- ═══ NOTE FOR WHOEVER APPLIES THIS ═══
-- The i18n response is served from a Spring cache. Translations are re-read per request, but if the
-- new label does not appear, restart the backend JVM — `@Cacheable` master data in this service has no
-- matching @CacheEvict and /actuator/caches is not exposed.

INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT, UPDATED_AT)
SELECT 'cepc.action.create_complaint_crpc', 'cepc',
       'CEPC dashboard action: create a complaint using the CRPC physical-letter intake layout',
       'Create in CRPC Layout', NOW(), NOW()
  FROM (SELECT 1) d
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'cepc.action.create_complaint_crpc');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'en', 'Create in CRPC Layout', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'en');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', 'CRPC लेआउट में शिकायत बनाएं', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', 'CRPC लेआउटमध्ये तक्रार तयार करा', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', 'CRPC লেআউটে অভিযোগ তৈরি করুন', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', 'CRPC లేఅవుట్‌లో ఫిర్యాదు సృష్టించండి', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', 'CRPC தளவமைப்பில் புகாரை உருவாக்கவும்', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', 'CRPC લેઆઉટમાં ફરિયાદ બનાવો', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', 'CRPC ವಿನ್ಯಾಸದಲ್ಲಿ ದೂರನ್ನು ರಚಿಸಿ', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', 'CRPC ലേഔട്ടിൽ പരാതി സൃഷ്ടിക്കുക', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'pa', 'CRPC ਲੇਆਉਟ ਵਿੱਚ ਸ਼ਿਕਾਇਤ ਬਣਾਓ', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'pa');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', 'CRPC لے آؤٹ میں شکایت بنائیں', NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'cepc.action.create_complaint_crpc'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');

-- ═══════════════════════════════════════════════════════════════════════════════════════════════════
-- The five CEPC dashboard KPI card labels.
--
-- ═══ WHY NOT SIMPLY REUSE RBIO's rbio.stats.* KEYS ═══
-- CEPC's cards count the SAME five things RBIO's do, and reuse was the first instinct — but four of
-- RBIO's five labels end in "(this page)":
--     rbio.stats.pending_with_me_page   = 'Pending with Me (this page)'
--     rbio.stats.pending_contact_page   = 'Pending with Contact Person (this page)'
--     rbio.stats.pending_meeting_page   = 'Meeting Scheduled (this page)'
--     rbio.stats.sla_breach_page        = 'SLA Breach (this page)'
-- That qualifier is TRUE for RBIO and FALSE for CEPC. RBIO's grid is server-paged and its endpoint
-- returns no per-status totals, so RBIO can only honestly count the page in front of the officer.
-- CEPC's loadComplaints() takes no page parameter — it receives the whole queue and the grid slices it
-- client-side — so CEPC's counters are over the ENTIRE queue. Borrowing RBIO's strings would have put
-- "(this page)" on a number that is not a page count, understating a 759-row queue's SLA breaches to an
-- officer deciding what to work on next. Honest labels win over key reuse here.
--
-- The KEYS are CEPC's own; the five FACTS counted, their order, the icons and the click-through
-- behaviour are all copied from RBIO, so the screens still read as the same screen.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT, UPDATED_AT)
SELECT 'cepc.stats.total_pending', 'cepc', 'CEPC KPI: all non-terminal complaints in the queue', 'Total Pending Complaints', NOW(), NOW()
  FROM (SELECT 1) d WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'cepc.stats.total_pending');
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT, UPDATED_AT)
SELECT 'cepc.stats.pending_with_me', 'cepc', 'CEPC KPI: new/pending/assigned/in-progress with this officer', 'Pending with Me', NOW(), NOW()
  FROM (SELECT 1) d WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'cepc.stats.pending_with_me');
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT, UPDATED_AT)
SELECT 'cepc.stats.pending_contact_person', 'cepc', 'CEPC KPI: awaiting the contact person or requested information', 'Pending with Contact Person', NOW(), NOW()
  FROM (SELECT 1) d WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'cepc.stats.pending_contact_person');
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT, UPDATED_AT)
SELECT 'cepc.stats.pending_meeting', 'cepc', 'CEPC KPI: workflow stage MEETING_SCHEDULED', 'Meeting Scheduled', NOW(), NOW()
  FROM (SELECT 1) d WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'cepc.stats.pending_meeting');
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT, UPDATED_AT)
SELECT 'cepc.stats.sla_breach', 'cepc', 'CEPC KPI: deadline in the past on a still-open complaint', 'SLA Breach', NOW(), NOW()
  FROM (SELECT 1) d WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'cepc.stats.sla_breach');

INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, v.LOCALE, v.VALUE, NOW() FROM TRANSLATION_KEYS k JOIN (
  SELECT 'cepc.stats.total_pending' AS CODE, 'en' AS LOCALE, 'Total Pending Complaints' AS VALUE
  UNION ALL SELECT 'cepc.stats.total_pending', 'hi', 'कुल लंबित शिकायतें'
  UNION ALL SELECT 'cepc.stats.total_pending', 'mr', 'एकूण प्रलंबित तक्रारी'
  UNION ALL SELECT 'cepc.stats.total_pending', 'bn', 'মোট মুলতুবি অভিযোগ'
  UNION ALL SELECT 'cepc.stats.total_pending', 'te', 'మొత్తం పెండింగ్ ఫిర్యాదులు'
  UNION ALL SELECT 'cepc.stats.total_pending', 'ta', 'மொத்த நிலுவை புகார்கள்'
  UNION ALL SELECT 'cepc.stats.total_pending', 'gu', 'કુલ બાકી ફરિયાદો'
  UNION ALL SELECT 'cepc.stats.total_pending', 'kn', 'ಒಟ್ಟು ಬಾಕಿ ದೂರುಗಳು'
  UNION ALL SELECT 'cepc.stats.total_pending', 'ml', 'ആകെ തീർപ്പാകാത്ത പരാതികൾ'
  UNION ALL SELECT 'cepc.stats.total_pending', 'pa', 'ਕੁੱਲ ਬਕਾਇਆ ਸ਼ਿਕਾਇਤਾਂ'
  UNION ALL SELECT 'cepc.stats.total_pending', 'ur', 'کل زیر التواء شکایات'

  UNION ALL SELECT 'cepc.stats.pending_with_me', 'en', 'Pending with Me'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'hi', 'मेरे पास लंबित'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'mr', 'माझ्याकडे प्रलंबित'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'bn', 'আমার কাছে মুলতুবি'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'te', 'నా వద్ద పెండింగ్'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'ta', 'என்னிடம் நிலுவையில்'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'gu', 'મારી પાસે બાકી'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'kn', 'ನನ್ನ ಬಳಿ ಬಾಕಿ'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'ml', 'എന്റെ പക്കൽ തീർപ്പാകാത്തവ'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'pa', 'ਮੇਰੇ ਕੋਲ ਬਕਾਇਆ'
  UNION ALL SELECT 'cepc.stats.pending_with_me', 'ur', 'میرے پاس زیر التواء'

  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'en', 'Pending with Contact Person'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'hi', 'संपर्क व्यक्ति के पास लंबित'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'mr', 'संपर्क व्यक्तीकडे प्रलंबित'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'bn', 'যোগাযোগ ব্যক্তির কাছে মুলতুবি'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'te', 'సంప్రదింపు వ్యక్తి వద్ద పెండింగ్'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'ta', 'தொடர்பு நபரிடம் நிலுவையில்'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'gu', 'સંપર્ક વ્યક્તિ પાસે બાકી'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'kn', 'ಸಂಪರ್ಕ ವ್ಯಕ್ತಿಯ ಬಳಿ ಬಾಕಿ'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'ml', 'ബന്ധപ്പെടേണ്ട വ്യക്തിയുടെ പക്കൽ'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'pa', 'ਸੰਪਰਕ ਵਿਅਕਤੀ ਕੋਲ ਬਕਾਇਆ'
  UNION ALL SELECT 'cepc.stats.pending_contact_person', 'ur', 'رابطہ کار کے پاس زیر التواء'

  UNION ALL SELECT 'cepc.stats.pending_meeting', 'en', 'Meeting Scheduled'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'hi', 'बैठक निर्धारित'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'mr', 'बैठक नियोजित'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'bn', 'সভা নির্ধারিত'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'te', 'సమావేశం షెడ్యూల్ చేయబడింది'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'ta', 'கூட்டம் திட்டமிடப்பட்டது'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'gu', 'બેઠક નિર્ધારિત'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'kn', 'ಸಭೆ ನಿಗದಿಯಾಗಿದೆ'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'ml', 'യോഗം നിശ്ചയിച്ചു'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'pa', 'ਮੀਟਿੰਗ ਨਿਰਧਾਰਤ'
  UNION ALL SELECT 'cepc.stats.pending_meeting', 'ur', 'میٹنگ طے شدہ'

  UNION ALL SELECT 'cepc.stats.sla_breach', 'en', 'SLA Breach'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'hi', 'एसएलए उल्लंघन'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'mr', 'एसएलए उल्लंघन'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'bn', 'এসএলএ লঙ্ঘন'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'te', 'ఎస్ఎల్ఏ ఉల్లంఘన'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'ta', 'எஸ்எல்ஏ மீறல்'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'gu', 'એસએલએ ઉલ્લંઘન'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'kn', 'ಎಸ್‌ಎಲ್‌ಎ ಉಲ್ಲಂಘನೆ'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'ml', 'എസ്എൽഎ ലംഘനം'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'pa', 'ਐਸਐਲਏ ਉਲੰਘਣਾ'
  UNION ALL SELECT 'cepc.stats.sla_breach', 'ur', 'ایس ایل اے کی خلاف ورزی'
) v ON v.CODE = k.CODE
 WHERE NOT EXISTS (
   SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = v.LOCALE
 );
