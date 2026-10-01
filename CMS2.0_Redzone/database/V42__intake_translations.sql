-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V42 — Inbound email/letter intake vocabulary, all ten locales
-- MySQL 8.4 version. Oracle twin: database/oracle/V40__intake_translations.sql
--
-- WHY THIS FILE EXISTS
--
--   Every string the rebuilt intake pipeline shows a citizen or an officer travels from the backend
--   as a `messageKey` (intake.*), never as English prose. The rows behind those keys are normally
--   created by cms-backend/.../config/IntakeTranslationSeeder.java, a CommandLineRunner (@Order(13))
--   that runs on application boot and is INSERT-IF-ABSENT.
--
--   That has two consequences the schema must cover on its own:
--
--     (a) A database restored from a dump, or a deployment where the seeder has not yet run (or was
--         disabled, or crashed on an earlier @Order bean), holds no intake.* rows at all. The UI then
--         renders the raw key — a complainant sees the literal text
--         "intake.duplicate_linked_to_parent" instead of an explanation. Reports and the Exceptional
--         Email Master screen show raw key names as their titles.
--
--     (b) Because the seeder is insert-if-absent, correcting the Java text does NOTHING to a database
--         that already committed the old row. Text drift has to be repaired by a code-scoped UPDATE
--         in a migration — the pattern established by V32/oracle-V30 for the Scheme-year defect and
--         reused in Part D below.
--
--   So this migration makes the intake vocabulary available from the schema alone, independent of any
--   application boot. It does not fight the seeder: both paths are insert-if-absent, whichever runs
--   first wins, and the second is a no-op. Text is kept byte-identical to IntakeTranslationSeeder so
--   the two paths cannot disagree.
--
-- WHAT IS SEEDED — 16 keys x (1 English default + 9 localized rows), module = 'intake':
--
--   Suppression / deduplication  email_suppressed_by_rule, duplicate_delivery_ignored,
--                                duplicate_linked_to_parent
--   OCR gating                   vernacular_manual_entry_required, ocr_low_confidence_manual_entry,
--                                manual_entry_required_banner
--   Not-found / validation       draft_not_found, ignore_rule_not_found,
--                                suggested_related_invalid_decision
--   NFR-006 attachment limits    attachment_too_large (2MB/file), attachment_total_too_large (25MB),
--                                attachment_too_many (10 files), attachment_rejected
--   Admin / reporting screens    ignored_emails_report_title, ignored_emails_export_csv,
--                                exceptional_email_master_title
--
-- THREE TRAPS THIS MIGRATION DELIBERATELY AVOIDS
--
--   1. SCOPED BY KEY CODE, NEVER BY AN ENGLISH PHRASE. Localized rows live in native scripts. A
--      predicate such as `value LIKE '%Draft not found%'` reaches the English row and silently misses
--      all nine other locales, while still reporting success. Every statement below correlates on
--      translation_keys.code.
--
--   2. NO HARDCODED IDs. translation_keys.id is AUTO_INCREMENT and differs per environment (this
--      database already holds the rows from the seeder, at ids assigned by boot order). Each locale
--      row resolves its parent through a join on `code`.
--
--   3. NATIVE-SCRIPT DIGITS. Three strings carry numbers (2MB, 25MB, 10 files). Bengali renders them
--      in Bengali numerals — ২ / ২৫ / ১০, not 2 / 25 / 10 — and Urdu in Eastern Arabic numerals
--      — ۲ / ۲۵ / ۱۰. Writing ASCII digits into those two locales would be a visible defect to a
--      reader of that script, so the numerals are written per script and MB is spelled out
--      ("এমবি", "ایم بی") because the Latin unit does not sit correctly in either run of text.
--
-- IDEMPOTENCY
--
--   MySQL 8.4 has no INSERT ... IF NOT EXISTS for this shape, and ON DUPLICATE KEY is deliberately
--   NOT used: it would need the (translation_key_id, locale) unique index to exist, and on the key
--   table it would bump AUTO_INCREMENT on every re-run. Instead every insert is
--   INSERT ... SELECT ... WHERE NOT EXISTS, so a second run inserts zero rows. The Part D UPDATEs are
--   self-limiting (they only touch rows that are still wrong). Nothing is deleted or truncated.
--
--   utf8mb4-safe: run with --default-character-set=utf8mb4. The CAST(... AS CHAR(1000)) on the first
--   branch of each UNION ALL fixes the derived column wide enough for the longest string in the block,
--   so no branch can be truncated by the union type resolution.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- PART A. The 16 keys, with the English text as default_value
-- ═══════════════════════════════════════════════════════════════════════════
INSERT INTO translation_keys (code, module, description, default_value, created_at, updated_at)
SELECT src.c, 'intake', src.d, src.v, NOW(6), NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule'           AS c,
           'Intake: inbound mail matched an active ignore rule, no draft created' AS d,
           CAST('This email matched an active ignore rule, so no complaint draft was created.' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'Intake: identical message already received, existing draft returned',
           'This message has already been received and processed. The existing draft is shown below.'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'Intake: duplicate of a closed complaint, mail attached to that parent',
           'This is a duplicate of an earlier complaint that is now closed. The email and its attachments have been added to that existing complaint instead of creating a new draft.'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'Intake: regional-language content, OCR deliberately skipped, routed to data-entry operator',
           'The content is in a regional language, so automatic text extraction was not attempted. It has been routed to a skilled data-entry operator.'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'Intake: scan not legible enough to pre-fill the form, manual entry needed',
           'The scanned document could not be read reliably enough to fill the form automatically. Please enter the details manually.'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'Intake: on-screen banner telling the officer this draft needs manual entry',
           'Manual entry required — automatic text extraction was not used for this draft.'
    UNION ALL SELECT 'intake.draft_not_found',
           'Intake: draft not found',
           'Draft not found.'
    UNION ALL SELECT 'intake.ignore_rule_not_found',
           'Intake: suppression rule not found',
           'Ignore rule not found.'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision',
           'Intake: suggested-related decision must be Accepted or Dismissed',
           'The decision must be either Accepted or Dismissed.'
    UNION ALL SELECT 'intake.attachment_too_large',
           'Intake NFR-006: per-file size limit 2MB',
           'Each file must be 2MB or smaller.'
    UNION ALL SELECT 'intake.attachment_total_too_large',
           'Intake NFR-006: aggregate attachment size limit 25MB',
           'Attachments must total 25MB or less.'
    UNION ALL SELECT 'intake.attachment_too_many',
           'Intake NFR-006: at most 10 attachments',
           'You may attach at most 10 files.'
    UNION ALL SELECT 'intake.attachment_rejected',
           'Intake NFR-006: declared type not confirmed by content sniffing',
           'The file type could not be verified from its contents, so the file was rejected.'
    UNION ALL SELECT 'intake.ignored_emails_report_title',
           'Intake: Ignored Emails Report screen title',
           'Ignored Emails Report'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',
           'Intake: Ignored Emails Report CSV export button',
           'Export to CSV'
    UNION ALL SELECT 'intake.exceptional_email_master_title',
           'Intake: Exceptional Email Master CRUD screen title',
           'Exceptional Email Master'
) src
WHERE NOT EXISTS (SELECT 1 FROM translation_keys tk WHERE tk.code = src.c);

-- ═══════════════════════════════════════════════════════════════════════════
-- PART B. Localized values, one guarded INSERT per locale
--
--   `value` is quoted in the entity mapping (@Column(name = "\"value\"")) and is awkward in both
--   dialects, so it is backtick-quoted here throughout. The parent id comes from the join on `code`,
--   never from a literal.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── hi (Hindi, Devanagari) ─────────────────────────────────────────────────
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'hi', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('यह ईमेल एक सक्रिय अनदेखी नियम से मेल खाता है, इसलिए कोई शिकायत प्रारूप नहीं बनाया गया।' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'यह संदेश पहले ही प्राप्त और संसाधित किया जा चुका है। मौजूदा प्रारूप नीचे दिखाया गया है।'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'यह एक पूर्व शिकायत की प्रतिलिपि है जो अब बंद हो चुकी है। नया प्रारूप बनाने के बजाय ईमेल और उसके संलग्नक उसी मौजूदा शिकायत में जोड़ दिए गए हैं।'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'सामग्री क्षेत्रीय भाषा में है, इसलिए स्वचालित पाठ निष्कर्षण जानबूझकर नहीं किया गया। इसे कुशल डेटा-एंट्री ऑपरेटर के पास भेज दिया गया है।'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'स्कैन किया गया दस्तावेज़ इतनी विश्वसनीयता से नहीं पढ़ा जा सका कि फ़ॉर्म स्वतः भरा जा सके। कृपया विवरण हाथ से दर्ज करें।'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'हस्तचालित प्रविष्टि आवश्यक — इस प्रारूप के लिए स्वचालित पाठ निष्कर्षण का उपयोग नहीं किया गया।'
    UNION ALL SELECT 'intake.draft_not_found',                   'प्रारूप नहीं मिला।'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'अनदेखी नियम नहीं मिला।'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','निर्णय स्वीकृत या अस्वीकृत में से कोई एक होना चाहिए।'
    UNION ALL SELECT 'intake.attachment_too_large',              'प्रत्येक फ़ाइल 2MB या उससे छोटी होनी चाहिए।'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'संलग्नकों का कुल आकार 25MB या उससे कम होना चाहिए।'
    UNION ALL SELECT 'intake.attachment_too_many',               'आप अधिकतम 10 फ़ाइलें संलग्न कर सकते हैं।'
    UNION ALL SELECT 'intake.attachment_rejected',
           'फ़ाइल का प्रकार उसकी सामग्री से सत्यापित नहीं हो सका, इसलिए फ़ाइल अस्वीकार कर दी गई।'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'अनदेखे किए गए ईमेल की रिपोर्ट'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'CSV में निर्यात करें'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'अपवाद ईमेल मास्टर'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'hi');

-- ── mr (Marathi, Devanagari — a different language from Hindi, not a copy) ─
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'mr', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('हा ईमेल सक्रिय दुर्लक्ष नियमाशी जुळला, त्यामुळे कोणताही तक्रार मसुदा तयार केला गेला नाही.' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'हा संदेश आधीच प्राप्त होऊन त्यावर प्रक्रिया झाली आहे. सध्याचा मसुदा खाली दाखवला आहे.'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'ही आधीच्या एका तक्रारीची प्रतिकृती आहे, जी आता बंद झाली आहे. नवीन मसुदा तयार करण्याऐवजी ईमेल व त्याची जोडपत्रे त्याच तक्रारीला जोडली गेली आहेत.'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'मजकूर प्रादेशिक भाषेत आहे, म्हणून स्वयंचलित मजकूर काढणी जाणीवपूर्वक केली गेली नाही. ते कुशल डेटा-एंट्री ऑपरेटरकडे पाठवण्यात आले आहे.'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'स्कॅन केलेला दस्तऐवज फॉर्म स्वयंचलितपणे भरण्याइतका विश्वासार्हपणे वाचता आला नाही. कृपया तपशील हाताने भरा.'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'हाताने नोंद आवश्यक — या मसुद्यासाठी स्वयंचलित मजकूर काढणी वापरली गेली नाही.'
    UNION ALL SELECT 'intake.draft_not_found',                   'मसुदा आढळला नाही.'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'दुर्लक्ष नियम आढळला नाही.'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','निर्णय स्वीकारलेला किंवा फेटाळलेला यापैकी एक असणे आवश्यक आहे.'
    UNION ALL SELECT 'intake.attachment_too_large',              'प्रत्येक फाइल 2MB किंवा त्याहून लहान असावी.'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'जोडपत्रांचा एकूण आकार 25MB किंवा त्याहून कमी असावा.'
    UNION ALL SELECT 'intake.attachment_too_many',               'तुम्ही जास्तीत जास्त 10 फाइल्स जोडू शकता.'
    UNION ALL SELECT 'intake.attachment_rejected',
           'फाइलचा प्रकार तिच्या आतील मजकुरावरून पडताळता आला नाही, म्हणून फाइल नाकारली गेली.'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'दुर्लक्षित ईमेलचा अहवाल'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'CSV मध्ये निर्यात करा'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'अपवादात्मक ईमेल मास्टर'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'mr');

-- ── bn (Bengali) — digits in Bengali numerals: ২ / ২৫ / ১০ ─────────────────
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'bn', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('এই ইমেলটি একটি সক্রিয় উপেক্ষা নিয়মের সঙ্গে মিলে গেছে, তাই কোনো অভিযোগের খসড়া তৈরি করা হয়নি।' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'এই বার্তাটি ইতিমধ্যেই গৃহীত ও প্রক্রিয়াকৃত হয়েছে। বিদ্যমান খসড়াটি নিচে দেখানো হয়েছে।'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'এটি একটি পূর্ববর্তী অভিযোগের অনুরূপ, যা এখন নিষ্পত্তি হয়ে গেছে। নতুন খসড়া তৈরির বদলে ইমেল ও তার সংযুক্তিগুলি সেই বিদ্যমান অভিযোগেই যুক্ত করা হয়েছে।'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'বিষয়বস্তু আঞ্চলিক ভাষায় রয়েছে, তাই স্বয়ংক্রিয় লেখা নিষ্কাশন সচেতনভাবে করা হয়নি। এটি একজন দক্ষ ডেটা-এন্ট্রি অপারেটরের কাছে পাঠানো হয়েছে।'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'স্ক্যান করা নথিটি ফর্ম স্বয়ংক্রিয়ভাবে পূরণ করার মতো নির্ভরযোগ্যভাবে পড়া যায়নি। অনুগ্রহ করে বিবরণ হাতে লিখুন।'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'হাতে তথ্য প্রবেশ প্রয়োজন — এই খসড়ার জন্য স্বয়ংক্রিয় লেখা নিষ্কাশন ব্যবহার করা হয়নি।'
    UNION ALL SELECT 'intake.draft_not_found',                   'খসড়া পাওয়া যায়নি।'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'উপেক্ষা নিয়ম পাওয়া যায়নি।'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','সিদ্ধান্ত অবশ্যই গৃহীত অথবা খারিজ হতে হবে।'
    UNION ALL SELECT 'intake.attachment_too_large',              'প্রতিটি ফাইল ২ এমবি বা তার কম হতে হবে।'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'সংযুক্তিগুলির মোট আকার ২৫ এমবি বা তার কম হতে হবে।'
    UNION ALL SELECT 'intake.attachment_too_many',               'আপনি সর্বাধিক ১০টি ফাইল সংযুক্ত করতে পারেন।'
    UNION ALL SELECT 'intake.attachment_rejected',
           'ফাইলের ধরন তার অন্তর্বস্তু থেকে যাচাই করা যায়নি, তাই ফাইলটি বাতিল করা হয়েছে।'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'উপেক্ষিত ইমেলের প্রতিবেদন'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'সিএসভি-তে রপ্তানি করুন'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'ব্যতিক্রমী ইমেল মাস্টার'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'bn');

-- ── te (Telugu) ────────────────────────────────────────────────────────────
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'te', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('ఈ ఇమెయిల్ ఒక క్రియాశీల విస్మరణ నియమానికి సరిపోలింది, కాబట్టి ఎటువంటి ఫిర్యాదు ముసాయిదా సృష్టించబడలేదు.' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'ఈ సందేశం ఇప్పటికే స్వీకరించి ప్రాసెస్ చేయబడింది. ఇప్పటికే ఉన్న ముసాయిదా క్రింద చూపబడింది.'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'ఇది ఇప్పుడు ముగిసిన ఒక పూర్వ ఫిర్యాదుకు నకలు. కొత్త ముసాయిదా సృష్టించే బదులు ఇమెయిల్ మరియు దాని అనుబంధాలు ఆ ఫిర్యాదుకే జోడించబడ్డాయి.'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'విషయం ప్రాంతీయ భాషలో ఉంది, కాబట్టి స్వయంచాలక పాఠ్య సేకరణ ఉద్దేశపూర్వకంగా ప్రయత్నించలేదు. దీనిని నైపుణ్యం కలిగిన డేటా-ఎంట్రీ ఆపరేటర్‌కు పంపారు.'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'ఫారమ్‌ను స్వయంచాలకంగా నింపేంత విశ్వసనీయంగా స్కాన్ చేసిన పత్రాన్ని చదవలేకపోయాము. దయచేసి వివరాలను చేతితో నమోదు చేయండి.'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'చేతితో నమోదు అవసరం — ఈ ముసాయిదా కోసం స్వయంచాలక పాఠ్య సేకరణ ఉపయోగించలేదు.'
    UNION ALL SELECT 'intake.draft_not_found',                   'ముసాయిదా కనుగొనబడలేదు.'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'విస్మరణ నియమం కనుగొనబడలేదు.'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','నిర్ణయం ఆమోదించబడింది లేదా తిరస్కరించబడింది అనే వాటిలో ఒకటిగా ఉండాలి.'
    UNION ALL SELECT 'intake.attachment_too_large',              'ప్రతి ఫైల్ 2MB లేదా అంతకంటే తక్కువ ఉండాలి.'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'అనుబంధాల మొత్తం పరిమాణం 25MB లేదా అంతకంటే తక్కువ ఉండాలి.'
    UNION ALL SELECT 'intake.attachment_too_many',               'మీరు గరిష్ఠంగా 10 ఫైళ్లను జోడించవచ్చు.'
    UNION ALL SELECT 'intake.attachment_rejected',
           'ఫైల్ రకాన్ని దాని కంటెంట్ నుండి ధృవీకరించలేకపోయాము, కాబట్టి ఫైల్ తిరస్కరించబడింది.'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'విస్మరించిన ఇమెయిల్‌ల నివేదిక'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'CSVకి ఎగుమతి చేయండి'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'మినహాయింపు ఇమెయిల్ మాస్టర్'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'te');

-- ── ta (Tamil) ─────────────────────────────────────────────────────────────
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'ta', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('இந்த மின்னஞ்சல் செயலில் உள்ள புறக்கணிப்பு விதியுடன் பொருந்தியது, எனவே எந்த முறையீட்டு வரைவும் உருவாக்கப்படவில்லை.' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'இந்தச் செய்தி ஏற்கனவே பெறப்பட்டு செயலாக்கப்பட்டுவிட்டது. ஏற்கனவே உள்ள வரைவு கீழே காட்டப்பட்டுள்ளது.'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'இது இப்போது முடிக்கப்பட்ட ஒரு முந்தைய முறையீட்டின் நகல். புதிய வரைவை உருவாக்குவதற்குப் பதிலாக மின்னஞ்சலும் அதன் இணைப்புகளும் அந்த முறையீட்டுடன் சேர்க்கப்பட்டுள்ளன.'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'உள்ளடக்கம் ஒரு பிராந்திய மொழியில் உள்ளது, எனவே தானியங்கி உரை பிரித்தெடுத்தல் வேண்டுமென்றே முயற்சிக்கப்படவில்லை. இது திறமையான தரவு-உள்ளீட்டு ஆபரேட்டருக்கு அனுப்பப்பட்டுள்ளது.'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'படிவத்தைத் தானாக நிரப்பும் அளவுக்கு ஸ்கேன் செய்யப்பட்ட ஆவணத்தை நம்பகமாகப் படிக்க முடியவில்லை. விவரங்களைக் கைமுறையாக உள்ளிடவும்.'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'கைமுறை உள்ளீடு தேவை — இந்த வரைவுக்குத் தானியங்கி உரை பிரித்தெடுத்தல் பயன்படுத்தப்படவில்லை.'
    UNION ALL SELECT 'intake.draft_not_found',                   'வரைவு கண்டறியப்படவில்லை.'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'புறக்கணிப்பு விதி கண்டறியப்படவில்லை.'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','முடிவு ஏற்கப்பட்டது அல்லது நிராகரிக்கப்பட்டது என்பதில் ஒன்றாக இருக்க வேண்டும்.'
    UNION ALL SELECT 'intake.attachment_too_large',              'ஒவ்வொரு கோப்பும் 2MB அல்லது அதற்குக் குறைவாக இருக்க வேண்டும்.'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'இணைப்புகளின் மொத்த அளவு 25MB அல்லது அதற்குக் குறைவாக இருக்க வேண்டும்.'
    UNION ALL SELECT 'intake.attachment_too_many',               'நீங்கள் அதிகபட்சம் 10 கோப்புகளை இணைக்கலாம்.'
    UNION ALL SELECT 'intake.attachment_rejected',
           'கோப்பின் வகையை அதன் உள்ளடக்கத்திலிருந்து சரிபார்க்க முடியவில்லை, எனவே கோப்பு நிராகரிக்கப்பட்டது.'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'புறக்கணிக்கப்பட்ட மின்னஞ்சல்கள் அறிக்கை'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'CSV ஆக ஏற்றுமதி செய்'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'விதிவிலக்கு மின்னஞ்சல் முதன்மைப் பட்டியல்'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'ta');

-- ── gu (Gujarati) ──────────────────────────────────────────────────────────
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'gu', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('આ ઈમેલ સક્રિય અવગણના નિયમ સાથે મેળ ખાય છે, તેથી કોઈ ફરિયાદ ડ્રાફ્ટ બનાવવામાં આવ્યો નથી.' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'આ સંદેશ પહેલેથી જ પ્રાપ્ત થઈને પ્રક્રિયામાં લેવાયો છે. વર્તમાન ડ્રાફ્ટ નીચે બતાવેલ છે.'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'આ અગાઉની એક ફરિયાદની નકલ છે જે હવે બંધ થઈ ગઈ છે. નવો ડ્રાફ્ટ બનાવવાને બદલે ઈમેલ અને તેના જોડાણો તે જ ફરિયાદમાં ઉમેરવામાં આવ્યાં છે.'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'સામગ્રી પ્રાદેશિક ભાષામાં છે, તેથી સ્વયંસંચાલિત લખાણ નિષ્કર્ષણ જાણીજોઈને કરવામાં આવ્યું નથી. તેને કુશળ ડેટા-એન્ટ્રી ઓપરેટરને મોકલવામાં આવ્યું છે.'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'સ્કેન કરેલો દસ્તાવેજ ફોર્મ સ્વયં ભરી શકાય એટલી વિશ્વસનીયતાથી વાંચી શકાયો નથી. કૃપા કરીને વિગતો હાથે દાખલ કરો.'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'હાથે નોંધ કરવી જરૂરી — આ ડ્રાફ્ટ માટે સ્વયંસંચાલિત લખાણ નિષ્કર્ષણ વપરાયું નથી.'
    UNION ALL SELECT 'intake.draft_not_found',                   'ડ્રાફ્ટ મળ્યો નથી.'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'અવગણના નિયમ મળ્યો નથી.'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','નિર્ણય સ્વીકૃત અથવા નકારેલ હોવો જોઈએ.'
    UNION ALL SELECT 'intake.attachment_too_large',              'દરેક ફાઈલ 2MB કે તેથી નાની હોવી જોઈએ.'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'જોડાણોનું કુલ કદ 25MB કે તેથી ઓછું હોવું જોઈએ.'
    UNION ALL SELECT 'intake.attachment_too_many',               'તમે વધુમાં વધુ 10 ફાઈલો જોડી શકો છો.'
    UNION ALL SELECT 'intake.attachment_rejected',
           'ફાઈલનો પ્રકાર તેની અંદરની સામગ્રી પરથી ચકાસી શકાયો નથી, તેથી ફાઈલ નકારવામાં આવી.'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'અવગણાયેલા ઈમેલનો અહેવાલ'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'CSV માં નિકાસ કરો'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'અપવાદરૂપ ઈમેલ માસ્ટર'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'gu');

-- ── ur (Urdu, Arabic script) — digits in Eastern Arabic numerals: ۲ / ۲۵ / ۱۰
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'ur', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('یہ ای میل ایک فعال نظر انداز قاعدے سے مطابقت رکھتی ہے، اس لیے کوئی شکایتی مسودہ نہیں بنایا گیا۔' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'یہ پیغام پہلے ہی موصول اور کارروائی میں لایا جا چکا ہے۔ موجودہ مسودہ نیچے دکھایا گیا ہے۔'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'یہ ایک سابقہ شکایت کی نقل ہے جو اب بند ہو چکی ہے۔ نیا مسودہ بنانے کے بجائے ای میل اور اس کے منسلکات اسی موجودہ شکایت میں شامل کر دیے گئے ہیں۔'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'مواد ایک علاقائی زبان میں ہے، اس لیے خودکار متن کشید کرنے کی دانستہ کوشش نہیں کی گئی۔ اسے ایک ماہر ڈیٹا اینٹری آپریٹر کو بھیج دیا گیا ہے۔'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'اسکین کردہ دستاویز اتنے بھروسے سے نہیں پڑھی جا سکی کہ فارم خود بخود بھرا جا سکے۔ براہ کرم تفصیلات ہاتھ سے درج کریں۔'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'ہاتھ سے اندراج درکار — اس مسودے کے لیے خودکار متن کشید کرنا استعمال نہیں کیا گیا۔'
    UNION ALL SELECT 'intake.draft_not_found',                   'مسودہ نہیں ملا۔'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'نظر انداز قاعدہ نہیں ملا۔'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','فیصلہ منظور شدہ یا مسترد شدہ ہونا چاہیے۔'
    UNION ALL SELECT 'intake.attachment_too_large',              'ہر فائل ۲ ایم بی یا اس سے کم ہونی چاہیے۔'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'منسلکات کا کل حجم ۲۵ ایم بی یا اس سے کم ہونا چاہیے۔'
    UNION ALL SELECT 'intake.attachment_too_many',               'آپ زیادہ سے زیادہ ۱۰ فائلیں منسلک کر سکتے ہیں۔'
    UNION ALL SELECT 'intake.attachment_rejected',
           'فائل کی قسم اس کے مندرجات سے تصدیق نہیں ہو سکی، اس لیے فائل مسترد کر دی گئی۔'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'نظر انداز کی گئی ای میلوں کی رپورٹ'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'سی ایس وی میں برآمد کریں'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'استثنائی ای میل ماسٹر'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'ur');

-- ── kn (Kannada) ───────────────────────────────────────────────────────────
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'kn', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('ಈ ಇಮೇಲ್ ಸಕ್ರಿಯ ನಿರ್ಲಕ್ಷ್ಯ ನಿಯಮಕ್ಕೆ ಹೊಂದಿಕೆಯಾಗಿದೆ, ಆದ್ದರಿಂದ ಯಾವುದೇ ದೂರಿನ ಕರಡು ರಚಿಸಲಾಗಿಲ್ಲ.' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'ಈ ಸಂದೇಶವನ್ನು ಈಗಾಗಲೇ ಸ್ವೀಕರಿಸಿ ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲಾಗಿದೆ. ಅಸ್ತಿತ್ವದಲ್ಲಿರುವ ಕರಡನ್ನು ಕೆಳಗೆ ತೋರಿಸಲಾಗಿದೆ.'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'ಇದು ಈಗ ಮುಕ್ತಾಯಗೊಂಡಿರುವ ಹಿಂದಿನ ದೂರಿನ ನಕಲು. ಹೊಸ ಕರಡು ರಚಿಸುವ ಬದಲು ಇಮೇಲ್ ಮತ್ತು ಅದರ ಲಗತ್ತುಗಳನ್ನು ಅದೇ ದೂರಿಗೆ ಸೇರಿಸಲಾಗಿದೆ.'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'ವಿಷಯವು ಪ್ರಾದೇಶಿಕ ಭಾಷೆಯಲ್ಲಿದೆ, ಆದ್ದರಿಂದ ಸ್ವಯಂಚಾಲಿತ ಪಠ್ಯ ಹೊರತೆಗೆಯುವಿಕೆಯನ್ನು ಉದ್ದೇಶಪೂರ್ವಕವಾಗಿ ಪ್ರಯತ್ನಿಸಲಾಗಿಲ್ಲ. ಇದನ್ನು ನುರಿತ ದತ್ತಾಂಶ-ನಮೂದು ನಿರ್ವಾಹಕರಿಗೆ ಕಳುಹಿಸಲಾಗಿದೆ.'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'ನಮೂನೆಯನ್ನು ಸ್ವಯಂಚಾಲಿತವಾಗಿ ತುಂಬುವಷ್ಟು ವಿಶ್ವಾಸಾರ್ಹವಾಗಿ ಸ್ಕ್ಯಾನ್ ಮಾಡಿದ ದಾಖಲೆಯನ್ನು ಓದಲಾಗಿಲ್ಲ. ದಯವಿಟ್ಟು ವಿವರಗಳನ್ನು ಕೈಯಾರೆ ನಮೂದಿಸಿ.'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'ಕೈಯಾರೆ ನಮೂದು ಅಗತ್ಯ — ಈ ಕರಡಿಗೆ ಸ್ವಯಂಚಾಲಿತ ಪಠ್ಯ ಹೊರತೆಗೆಯುವಿಕೆಯನ್ನು ಬಳಸಲಾಗಿಲ್ಲ.'
    UNION ALL SELECT 'intake.draft_not_found',                   'ಕರಡು ಕಂಡುಬಂದಿಲ್ಲ.'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'ನಿರ್ಲಕ್ಷ್ಯ ನಿಯಮ ಕಂಡುಬಂದಿಲ್ಲ.'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','ನಿರ್ಧಾರವು ಸ್ವೀಕರಿಸಲಾಗಿದೆ ಅಥವಾ ತಿರಸ್ಕರಿಸಲಾಗಿದೆ ಎಂಬುದರಲ್ಲಿ ಒಂದಾಗಿರಬೇಕು.'
    UNION ALL SELECT 'intake.attachment_too_large',              'ಪ್ರತಿ ಕಡತ 2MB ಅಥವಾ ಅದಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು.'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'ಲಗತ್ತುಗಳ ಒಟ್ಟು ಗಾತ್ರ 25MB ಅಥವಾ ಅದಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು.'
    UNION ALL SELECT 'intake.attachment_too_many',               'ನೀವು ಗರಿಷ್ಠ 10 ಕಡತಗಳನ್ನು ಲಗತ್ತಿಸಬಹುದು.'
    UNION ALL SELECT 'intake.attachment_rejected',
           'ಕಡತದ ಪ್ರಕಾರವನ್ನು ಅದರ ಒಳವಿಷಯದಿಂದ ಪರಿಶೀಲಿಸಲಾಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಕಡತವನ್ನು ತಿರಸ್ಕರಿಸಲಾಗಿದೆ.'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'ನಿರ್ಲಕ್ಷಿಸಿದ ಇಮೇಲ್‌ಗಳ ವರದಿ'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'CSV ಗೆ ರಫ್ತು ಮಾಡಿ'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'ಅಪವಾದ ಇಮೇಲ್ ಮಾಸ್ಟರ್'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'kn');

-- ── ml (Malayalam) ─────────────────────────────────────────────────────────
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT tk.id, 'ml', src.v, NOW(6) FROM (
    SELECT 'intake.email_suppressed_by_rule' AS c,
           CAST('ഈ ഇമെയിൽ സജീവമായ ഒരു അവഗണന നിയമവുമായി പൊരുത്തപ്പെട്ടു, അതിനാൽ പരാതിയുടെ കരട് സൃഷ്ടിച്ചിട്ടില്ല.' AS CHAR(1000)) AS v
    UNION ALL SELECT 'intake.duplicate_delivery_ignored',
           'ഈ സന്ദേശം നേരത്തെ തന്നെ ലഭിച്ച് പ്രോസസ് ചെയ്തിട്ടുണ്ട്. നിലവിലുള്ള കരട് താഴെ കാണിച്ചിരിക്കുന്നു.'
    UNION ALL SELECT 'intake.duplicate_linked_to_parent',
           'ഇത് ഇപ്പോൾ അവസാനിപ്പിച്ച ഒരു മുൻ പരാതിയുടെ തനിപ്പകർപ്പാണ്. പുതിയ കരട് സൃഷ്ടിക്കുന്നതിനു പകരം ഇമെയിലും അതിന്റെ അനുബന്ധങ്ങളും ആ പരാതിയോട് ചേർത്തിട്ടുണ്ട്.'
    UNION ALL SELECT 'intake.vernacular_manual_entry_required',
           'ഉള്ളടക്കം ഒരു പ്രാദേശിക ഭാഷയിലാണ്, അതിനാൽ സ്വയമേവയുള്ള വാചകം വേർതിരിച്ചെടുക്കൽ മനഃപൂർവം ശ്രമിച്ചിട്ടില്ല. ഇത് വിദഗ്ധനായ ഡാറ്റാ എൻട്രി ഓപ്പറേറ്റർക്ക് കൈമാറിയിട്ടുണ്ട്.'
    UNION ALL SELECT 'intake.ocr_low_confidence_manual_entry',
           'ഫോം സ്വയമേവ പൂരിപ്പിക്കാൻ കഴിയുന്നത്ര വിശ്വസനീയമായി സ്കാൻ ചെയ്ത രേഖ വായിക്കാൻ കഴിഞ്ഞില്ല. വിവരങ്ങൾ സ്വമേധയാ നൽകുക.'
    UNION ALL SELECT 'intake.manual_entry_required_banner',
           'സ്വമേധയാ വിവരം നൽകേണ്ടതുണ്ട് — ഈ കരടിനായി സ്വയമേവയുള്ള വാചകം വേർതിരിച്ചെടുക്കൽ ഉപയോഗിച്ചിട്ടില്ല.'
    UNION ALL SELECT 'intake.draft_not_found',                   'കരട് കണ്ടെത്തിയില്ല.'
    UNION ALL SELECT 'intake.ignore_rule_not_found',             'അവഗണന നിയമം കണ്ടെത്തിയില്ല.'
    UNION ALL SELECT 'intake.suggested_related_invalid_decision','തീരുമാനം സ്വീകരിച്ചു അല്ലെങ്കിൽ തള്ളി എന്നതിൽ ഒന്നായിരിക്കണം.'
    UNION ALL SELECT 'intake.attachment_too_large',              'ഓരോ ഫയലും 2MB അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.'
    UNION ALL SELECT 'intake.attachment_total_too_large',        'അനുബന്ധങ്ങളുടെ ആകെ വലുപ്പം 25MB അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.'
    UNION ALL SELECT 'intake.attachment_too_many',               'നിങ്ങൾക്ക് പരമാവധി 10 ഫയലുകൾ ചേർക്കാം.'
    UNION ALL SELECT 'intake.attachment_rejected',
           'ഫയലിന്റെ തരം അതിന്റെ ഉള്ളടക്കത്തിൽ നിന്ന് പരിശോധിക്കാൻ കഴിഞ്ഞില്ല, അതിനാൽ ഫയൽ നിരസിച്ചു.'
    UNION ALL SELECT 'intake.ignored_emails_report_title',       'അവഗണിച്ച ഇമെയിലുകളുടെ റിപ്പോർട്ട്'
    UNION ALL SELECT 'intake.ignored_emails_export_csv',         'CSV ആയി എക്സ്പോർട്ട് ചെയ്യുക'
    UNION ALL SELECT 'intake.exceptional_email_master_title',    'അപവാദ ഇമെയിൽ മാസ്റ്റർ'
) src
JOIN translation_keys tk ON tk.code = src.c
WHERE NOT EXISTS (SELECT 1 FROM translations t
                   WHERE t.translation_key_id = tk.id AND t.locale = 'ml');

-- ═══════════════════════════════════════════════════════════════════════════
-- PART C. Corrective UPDATEs — key-scoped, never English-text-scoped
--
--   Same reasoning as V32 Part A. Both the seeder and Part A above are insert-if-absent, so a key row
--   that was already committed with a NULL/blank module or a NULL default_value keeps those values
--   forever; nothing in either seed path repairs it. These UPDATEs do, and they are idempotent because
--   they only match rows that are still wrong.
--
--   C1 matters operationally: the intake admin screens list keys by module, and the Ignored Emails
--   Report groups its labels the same way, so a key stranded outside module='intake' is invisible to
--   both even though the row exists.
-- ═══════════════════════════════════════════════════════════════════════════

-- C1. Module tagging. Scoped by code, so all 16 keys are reached regardless of what text they hold.
UPDATE translation_keys
SET module = 'intake',
    updated_at = NOW(6)
WHERE code IN ('intake.email_suppressed_by_rule',
               'intake.duplicate_delivery_ignored',
               'intake.duplicate_linked_to_parent',
               'intake.vernacular_manual_entry_required',
               'intake.ocr_low_confidence_manual_entry',
               'intake.manual_entry_required_banner',
               'intake.draft_not_found',
               'intake.ignore_rule_not_found',
               'intake.suggested_related_invalid_decision',
               'intake.attachment_too_large',
               'intake.attachment_total_too_large',
               'intake.attachment_too_many',
               'intake.attachment_rejected',
               'intake.ignored_emails_report_title',
               'intake.ignored_emails_export_csv',
               'intake.exceptional_email_master_title')
  AND (module IS NULL OR module <> 'intake');

-- C2. Blank English fallback. A key row with a NULL/empty default_value degrades to the raw key for
--     any locale that has no row of its own, which is exactly the failure this migration exists to
--     prevent. Repaired from the description, which is never null for these rows. Only empty rows are
--     touched, so a deliberate wording change made elsewhere is not clobbered.
UPDATE translation_keys
SET default_value = 'Draft not found.', updated_at = NOW(6)
WHERE code = 'intake.draft_not_found'
  AND (default_value IS NULL OR default_value = '');

UPDATE translation_keys
SET default_value = 'Ignore rule not found.', updated_at = NOW(6)
WHERE code = 'intake.ignore_rule_not_found'
  AND (default_value IS NULL OR default_value = '');

UPDATE translation_keys
SET default_value = 'Ignored Emails Report', updated_at = NOW(6)
WHERE code = 'intake.ignored_emails_report_title'
  AND (default_value IS NULL OR default_value = '');

UPDATE translation_keys
SET default_value = 'Export to CSV', updated_at = NOW(6)
WHERE code = 'intake.ignored_emails_export_csv'
  AND (default_value IS NULL OR default_value = '');

UPDATE translation_keys
SET default_value = 'Exceptional Email Master', updated_at = NOW(6)
WHERE code = 'intake.exceptional_email_master_title'
  AND (default_value IS NULL OR default_value = '');

-- ═══════════════════════════════════════════════════════════════════════════
-- PART D. Index guards (MySQL 8.4 has no CREATE INDEX IF NOT EXISTS, so
--         information_schema is consulted first). Both indexes exist in every
--         environment seen; the guards make this file safe on a schema created
--         before they were added, and re-running is a no-op either way.
--
--         idx_trans_key_locale is the unique index that makes the WHERE NOT EXISTS
--         guards above a hard constraint rather than a convention.
-- ═══════════════════════════════════════════════════════════════════════════
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'translation_keys'
               AND INDEX_NAME = 'idx_tkey_module');
SET @sql := IF(@idx = 0,
               'CREATE INDEX idx_tkey_module ON translation_keys(module)',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'translations'
               AND INDEX_NAME = 'idx_trans_key_locale');
SET @sql := IF(@idx = 0,
               'CREATE UNIQUE INDEX idx_trans_key_locale ON translations(translation_key_id, locale)',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
