-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V40 — Inbound email/letter intake vocabulary, all ten locales
-- Oracle version. MySQL twin: database/V42__intake_translations.sql
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
--         in a migration — the pattern established by oracle-V30/V32 for the Scheme-year defect and
--         reused in Part C below.
--
--   So this migration makes the intake vocabulary available from the schema alone, independent of any
--   application boot. It does not fight the seeder: both paths are insert-if-absent, whichever runs
--   first wins, and the second is a no-op. Text is kept byte-identical to IntakeTranslationSeeder and
--   to the MySQL twin so the three paths cannot disagree.
--
-- WHAT IS SEEDED — 16 keys x (1 English default + 9 localized rows), MODULE = 'intake':
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
-- FOUR ORACLE-SPECIFIC TRAPS THIS MIGRATION DELIBERATELY AVOIDS
--
--   1. SCOPED BY KEY CODE, NEVER BY AN ENGLISH PHRASE. Localized rows live in native scripts. A
--      predicate such as `VALUE LIKE '%Draft not found%'` reaches the English row and silently misses
--      all nine other locales, while still reporting success. Every statement below correlates on
--      TRANSLATION_KEYS.CODE.
--
--   2. NO HARDCODED IDs. TRANSLATION_KEYS.ID is an IDENTITY column and differs per environment. Each
--      locale row resolves its parent by SELECTing the ID for its CODE.
--
--   3. TWO SCHEMA SHAPES. The V1 revision of this schema declared TRANSLATIONS(KEY_ID, LOCALE_ID)
--      with a SUPPORTED_LOCALES foreign key and no UPDATED_AT; the V4 revision (which the JPA entity
--      matches) declares TRANSLATIONS(TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT). Referencing
--      TRANSLATION_KEY_ID on a V1-shaped database fails the whole script, so Part B is gated on a
--      USER_TAB_COLUMNS probe exactly as oracle/V30 gates its Part A.
--
--   4. NATIVE-SCRIPT DIGITS. Three strings carry numbers (2MB, 25MB, 10 files). Bengali renders them
--      in Bengali numerals — ২ / ২৫ / ১০, not 2 / 25 / 10 — and Urdu in Eastern Arabic numerals
--      — ۲ / ۲۵ / ۱۰. Writing ASCII digits into those two locales would be a visible defect to a
--      reader of that script, so the numerals are written per script and MB is spelled out
--      ("এমবি", "ایم بی") because the Latin unit does not sit correctly in either run of text.
--
-- IDEMPOTENCY
--
--   No MERGE and no ON CONFLICT. Every insert goes through a local procedure that COUNTs first, so a
--   second run inserts zero rows; the Part C UPDATEs only match rows that are still wrong. VALUE and
--   DEFAULT_VALUE are CLOB, so emptiness is tested with DBMS_LOB.GETLENGTH rather than = ''. Nothing
--   is deleted or truncated.
--
--   VALUE is a reserved word and is double-quoted as "VALUE" throughout, matching V46. The column was
--   created unquoted, so the stored identifier is uppercase and "VALUE" resolves to it correctly.
--
--   The N'' literals require an NLS/AL32UTF8-capable client (SQL*Plus: SET NLS_LANG accordingly),
--   otherwise the Indic and Arabic scripts arrive as replacement characters.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

SET DEFINE OFF;
WHENEVER SQLERROR CONTINUE;

-- ═══════════════════════════════════════════════════════════════════════════
-- PART A + PART B. Keys with English defaults, then the nine localized locales
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_shape NUMBER;

    -- Insert-if-absent key. Mirrors add_key() in V46.
    PROCEDURE ik(p_code VARCHAR2, p_desc VARCHAR2, p_val VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM TRANSLATION_KEYS WHERE CODE = p_code;
        IF v_cnt = 0 THEN
            INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT, UPDATED_AT)
            VALUES (p_code, 'intake', p_desc, p_val, SYSTIMESTAMP, SYSTIMESTAMP);
        END IF;
    END;

    -- Insert-if-absent localized value. Resolves the parent id from the CODE; returns quietly if the
    -- key is missing so a partial database cannot raise NO_DATA_FOUND out of the block.
    PROCEDURE it(p_code VARCHAR2, p_locale VARCHAR2, p_val VARCHAR2) IS
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
            INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, "VALUE", UPDATED_AT)
            VALUES (v_key_id, p_locale, p_val, SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    -- ── PART A. Keys + English defaults ────────────────────────────────────
    ik('intake.email_suppressed_by_rule',
       'Intake: inbound mail matched an active ignore rule, no draft created',
       'This email matched an active ignore rule, so no complaint draft was created.');
    ik('intake.duplicate_delivery_ignored',
       'Intake: identical message already received, existing draft returned',
       'This message has already been received and processed. The existing draft is shown below.');
    ik('intake.duplicate_linked_to_parent',
       'Intake: duplicate of a closed complaint, mail attached to that parent',
       'This is a duplicate of an earlier complaint that is now closed. The email and its attachments have been added to that existing complaint instead of creating a new draft.');
    ik('intake.vernacular_manual_entry_required',
       'Intake: regional-language content, OCR deliberately skipped, routed to data-entry operator',
       'The content is in a regional language, so automatic text extraction was not attempted. It has been routed to a skilled data-entry operator.');
    ik('intake.ocr_low_confidence_manual_entry',
       'Intake: scan not legible enough to pre-fill the form, manual entry needed',
       'The scanned document could not be read reliably enough to fill the form automatically. Please enter the details manually.');
    ik('intake.manual_entry_required_banner',
       'Intake: on-screen banner telling the officer this draft needs manual entry',
       'Manual entry required — automatic text extraction was not used for this draft.');
    ik('intake.draft_not_found',
       'Intake: draft not found',
       'Draft not found.');
    ik('intake.ignore_rule_not_found',
       'Intake: suppression rule not found',
       'Ignore rule not found.');
    ik('intake.suggested_related_invalid_decision',
       'Intake: suggested-related decision must be Accepted or Dismissed',
       'The decision must be either Accepted or Dismissed.');
    ik('intake.attachment_too_large',
       'Intake NFR-006: per-file size limit 2MB',
       'Each file must be 2MB or smaller.');
    ik('intake.attachment_total_too_large',
       'Intake NFR-006: aggregate attachment size limit 25MB',
       'Attachments must total 25MB or less.');
    ik('intake.attachment_too_many',
       'Intake NFR-006: at most 10 attachments',
       'You may attach at most 10 files.');
    ik('intake.attachment_rejected',
       'Intake NFR-006: declared type not confirmed by content sniffing',
       'The file type could not be verified from its contents, so the file was rejected.');
    ik('intake.ignored_emails_report_title',
       'Intake: Ignored Emails Report screen title',
       'Ignored Emails Report');
    ik('intake.ignored_emails_export_csv',
       'Intake: Ignored Emails Report CSV export button',
       'Export to CSV');
    ik('intake.exceptional_email_master_title',
       'Intake: Exceptional Email Master CRUD screen title',
       'Exceptional Email Master');
    COMMIT;

    -- ── PART B. Localized values ───────────────────────────────────────────
    -- Shape guard: the V1 revision has TRANSLATIONS(KEY_ID, LOCALE_ID) and no TRANSLATION_KEY_ID
    -- column at all. Skip rather than fail the script on such a database.
    SELECT COUNT(*) INTO v_shape FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'TRANSLATIONS' AND COLUMN_NAME = 'TRANSLATION_KEY_ID';
    IF v_shape = 0 THEN
        DBMS_OUTPUT.PUT_LINE('V40: TRANSLATIONS.TRANSLATION_KEY_ID absent (V1-shaped schema), '
                             || 'skipping localized rows. Keys and English defaults were still seeded.');
        RETURN;
    END IF;

    -- ── hi (Hindi, Devanagari) ─────────────────────────────────────────────
    it('intake.email_suppressed_by_rule',           'hi', N'यह ईमेल एक सक्रिय अनदेखी नियम से मेल खाता है, इसलिए कोई शिकायत प्रारूप नहीं बनाया गया।');
    it('intake.duplicate_delivery_ignored',         'hi', N'यह संदेश पहले ही प्राप्त और संसाधित किया जा चुका है। मौजूदा प्रारूप नीचे दिखाया गया है।');
    it('intake.duplicate_linked_to_parent',         'hi', N'यह एक पूर्व शिकायत की प्रतिलिपि है जो अब बंद हो चुकी है। नया प्रारूप बनाने के बजाय ईमेल और उसके संलग्नक उसी मौजूदा शिकायत में जोड़ दिए गए हैं।');
    it('intake.vernacular_manual_entry_required',   'hi', N'सामग्री क्षेत्रीय भाषा में है, इसलिए स्वचालित पाठ निष्कर्षण जानबूझकर नहीं किया गया। इसे कुशल डेटा-एंट्री ऑपरेटर के पास भेज दिया गया है।');
    it('intake.ocr_low_confidence_manual_entry',    'hi', N'स्कैन किया गया दस्तावेज़ इतनी विश्वसनीयता से नहीं पढ़ा जा सका कि फ़ॉर्म स्वतः भरा जा सके। कृपया विवरण हाथ से दर्ज करें।');
    it('intake.manual_entry_required_banner',       'hi', N'हस्तचालित प्रविष्टि आवश्यक — इस प्रारूप के लिए स्वचालित पाठ निष्कर्षण का उपयोग नहीं किया गया।');
    it('intake.draft_not_found',                    'hi', N'प्रारूप नहीं मिला।');
    it('intake.ignore_rule_not_found',              'hi', N'अनदेखी नियम नहीं मिला।');
    it('intake.suggested_related_invalid_decision', 'hi', N'निर्णय स्वीकृत या अस्वीकृत में से कोई एक होना चाहिए।');
    it('intake.attachment_too_large',               'hi', N'प्रत्येक फ़ाइल 2MB या उससे छोटी होनी चाहिए।');
    it('intake.attachment_total_too_large',         'hi', N'संलग्नकों का कुल आकार 25MB या उससे कम होना चाहिए।');
    it('intake.attachment_too_many',                'hi', N'आप अधिकतम 10 फ़ाइलें संलग्न कर सकते हैं।');
    it('intake.attachment_rejected',                'hi', N'फ़ाइल का प्रकार उसकी सामग्री से सत्यापित नहीं हो सका, इसलिए फ़ाइल अस्वीकार कर दी गई।');
    it('intake.ignored_emails_report_title',        'hi', N'अनदेखे किए गए ईमेल की रिपोर्ट');
    it('intake.ignored_emails_export_csv',          'hi', N'CSV में निर्यात करें');
    it('intake.exceptional_email_master_title',     'hi', N'अपवाद ईमेल मास्टर');

    -- ── mr (Marathi, Devanagari — a different language from Hindi, not a copy)
    it('intake.email_suppressed_by_rule',           'mr', N'हा ईमेल सक्रिय दुर्लक्ष नियमाशी जुळला, त्यामुळे कोणताही तक्रार मसुदा तयार केला गेला नाही.');
    it('intake.duplicate_delivery_ignored',         'mr', N'हा संदेश आधीच प्राप्त होऊन त्यावर प्रक्रिया झाली आहे. सध्याचा मसुदा खाली दाखवला आहे.');
    it('intake.duplicate_linked_to_parent',         'mr', N'ही आधीच्या एका तक्रारीची प्रतिकृती आहे, जी आता बंद झाली आहे. नवीन मसुदा तयार करण्याऐवजी ईमेल व त्याची जोडपत्रे त्याच तक्रारीला जोडली गेली आहेत.');
    it('intake.vernacular_manual_entry_required',   'mr', N'मजकूर प्रादेशिक भाषेत आहे, म्हणून स्वयंचलित मजकूर काढणी जाणीवपूर्वक केली गेली नाही. ते कुशल डेटा-एंट्री ऑपरेटरकडे पाठवण्यात आले आहे.');
    it('intake.ocr_low_confidence_manual_entry',    'mr', N'स्कॅन केलेला दस्तऐवज फॉर्म स्वयंचलितपणे भरण्याइतका विश्वासार्हपणे वाचता आला नाही. कृपया तपशील हाताने भरा.');
    it('intake.manual_entry_required_banner',       'mr', N'हाताने नोंद आवश्यक — या मसुद्यासाठी स्वयंचलित मजकूर काढणी वापरली गेली नाही.');
    it('intake.draft_not_found',                    'mr', N'मसुदा आढळला नाही.');
    it('intake.ignore_rule_not_found',              'mr', N'दुर्लक्ष नियम आढळला नाही.');
    it('intake.suggested_related_invalid_decision', 'mr', N'निर्णय स्वीकारलेला किंवा फेटाळलेला यापैकी एक असणे आवश्यक आहे.');
    it('intake.attachment_too_large',               'mr', N'प्रत्येक फाइल 2MB किंवा त्याहून लहान असावी.');
    it('intake.attachment_total_too_large',         'mr', N'जोडपत्रांचा एकूण आकार 25MB किंवा त्याहून कमी असावा.');
    it('intake.attachment_too_many',                'mr', N'तुम्ही जास्तीत जास्त 10 फाइल्स जोडू शकता.');
    it('intake.attachment_rejected',                'mr', N'फाइलचा प्रकार तिच्या आतील मजकुरावरून पडताळता आला नाही, म्हणून फाइल नाकारली गेली.');
    it('intake.ignored_emails_report_title',        'mr', N'दुर्लक्षित ईमेलचा अहवाल');
    it('intake.ignored_emails_export_csv',          'mr', N'CSV मध्ये निर्यात करा');
    it('intake.exceptional_email_master_title',     'mr', N'अपवादात्मक ईमेल मास्टर');

    -- ── bn (Bengali) — digits in Bengali numerals: ২ / ২৫ / ১০ ─────────────
    it('intake.email_suppressed_by_rule',           'bn', N'এই ইমেলটি একটি সক্রিয় উপেক্ষা নিয়মের সঙ্গে মিলে গেছে, তাই কোনো অভিযোগের খসড়া তৈরি করা হয়নি।');
    it('intake.duplicate_delivery_ignored',         'bn', N'এই বার্তাটি ইতিমধ্যেই গৃহীত ও প্রক্রিয়াকৃত হয়েছে। বিদ্যমান খসড়াটি নিচে দেখানো হয়েছে।');
    it('intake.duplicate_linked_to_parent',         'bn', N'এটি একটি পূর্ববর্তী অভিযোগের অনুরূপ, যা এখন নিষ্পত্তি হয়ে গেছে। নতুন খসড়া তৈরির বদলে ইমেল ও তার সংযুক্তিগুলি সেই বিদ্যমান অভিযোগেই যুক্ত করা হয়েছে।');
    it('intake.vernacular_manual_entry_required',   'bn', N'বিষয়বস্তু আঞ্চলিক ভাষায় রয়েছে, তাই স্বয়ংক্রিয় লেখা নিষ্কাশন সচেতনভাবে করা হয়নি। এটি একজন দক্ষ ডেটা-এন্ট্রি অপারেটরের কাছে পাঠানো হয়েছে।');
    it('intake.ocr_low_confidence_manual_entry',    'bn', N'স্ক্যান করা নথিটি ফর্ম স্বয়ংক্রিয়ভাবে পূরণ করার মতো নির্ভরযোগ্যভাবে পড়া যায়নি। অনুগ্রহ করে বিবরণ হাতে লিখুন।');
    it('intake.manual_entry_required_banner',       'bn', N'হাতে তথ্য প্রবেশ প্রয়োজন — এই খসড়ার জন্য স্বয়ংক্রিয় লেখা নিষ্কাশন ব্যবহার করা হয়নি।');
    it('intake.draft_not_found',                    'bn', N'খসড়া পাওয়া যায়নি।');
    it('intake.ignore_rule_not_found',              'bn', N'উপেক্ষা নিয়ম পাওয়া যায়নি।');
    it('intake.suggested_related_invalid_decision', 'bn', N'সিদ্ধান্ত অবশ্যই গৃহীত অথবা খারিজ হতে হবে।');
    it('intake.attachment_too_large',               'bn', N'প্রতিটি ফাইল ২ এমবি বা তার কম হতে হবে।');
    it('intake.attachment_total_too_large',         'bn', N'সংযুক্তিগুলির মোট আকার ২৫ এমবি বা তার কম হতে হবে।');
    it('intake.attachment_too_many',                'bn', N'আপনি সর্বাধিক ১০টি ফাইল সংযুক্ত করতে পারেন।');
    it('intake.attachment_rejected',                'bn', N'ফাইলের ধরন তার অন্তর্বস্তু থেকে যাচাই করা যায়নি, তাই ফাইলটি বাতিল করা হয়েছে।');
    it('intake.ignored_emails_report_title',        'bn', N'উপেক্ষিত ইমেলের প্রতিবেদন');
    it('intake.ignored_emails_export_csv',          'bn', N'সিএসভি-তে রপ্তানি করুন');
    it('intake.exceptional_email_master_title',     'bn', N'ব্যতিক্রমী ইমেল মাস্টার');

    -- ── te (Telugu) ────────────────────────────────────────────────────────
    it('intake.email_suppressed_by_rule',           'te', N'ఈ ఇమెయిల్ ఒక క్రియాశీల విస్మరణ నియమానికి సరిపోలింది, కాబట్టి ఎటువంటి ఫిర్యాదు ముసాయిదా సృష్టించబడలేదు.');
    it('intake.duplicate_delivery_ignored',         'te', N'ఈ సందేశం ఇప్పటికే స్వీకరించి ప్రాసెస్ చేయబడింది. ఇప్పటికే ఉన్న ముసాయిదా క్రింద చూపబడింది.');
    it('intake.duplicate_linked_to_parent',         'te', N'ఇది ఇప్పుడు ముగిసిన ఒక పూర్వ ఫిర్యాదుకు నకలు. కొత్త ముసాయిదా సృష్టించే బదులు ఇమెయిల్ మరియు దాని అనుబంధాలు ఆ ఫిర్యాదుకే జోడించబడ్డాయి.');
    it('intake.vernacular_manual_entry_required',   'te', N'విషయం ప్రాంతీయ భాషలో ఉంది, కాబట్టి స్వయంచాలక పాఠ్య సేకరణ ఉద్దేశపూర్వకంగా ప్రయత్నించలేదు. దీనిని నైపుణ్యం కలిగిన డేటా-ఎంట్రీ ఆపరేటర్‌కు పంపారు.');
    it('intake.ocr_low_confidence_manual_entry',    'te', N'ఫారమ్‌ను స్వయంచాలకంగా నింపేంత విశ్వసనీయంగా స్కాన్ చేసిన పత్రాన్ని చదవలేకపోయాము. దయచేసి వివరాలను చేతితో నమోదు చేయండి.');
    it('intake.manual_entry_required_banner',       'te', N'చేతితో నమోదు అవసరం — ఈ ముసాయిదా కోసం స్వయంచాలక పాఠ్య సేకరణ ఉపయోగించలేదు.');
    it('intake.draft_not_found',                    'te', N'ముసాయిదా కనుగొనబడలేదు.');
    it('intake.ignore_rule_not_found',              'te', N'విస్మరణ నియమం కనుగొనబడలేదు.');
    it('intake.suggested_related_invalid_decision', 'te', N'నిర్ణయం ఆమోదించబడింది లేదా తిరస్కరించబడింది అనే వాటిలో ఒకటిగా ఉండాలి.');
    it('intake.attachment_too_large',               'te', N'ప్రతి ఫైల్ 2MB లేదా అంతకంటే తక్కువ ఉండాలి.');
    it('intake.attachment_total_too_large',         'te', N'అనుబంధాల మొత్తం పరిమాణం 25MB లేదా అంతకంటే తక్కువ ఉండాలి.');
    it('intake.attachment_too_many',                'te', N'మీరు గరిష్ఠంగా 10 ఫైళ్లను జోడించవచ్చు.');
    it('intake.attachment_rejected',                'te', N'ఫైల్ రకాన్ని దాని కంటెంట్ నుండి ధృవీకరించలేకపోయాము, కాబట్టి ఫైల్ తిరస్కరించబడింది.');
    it('intake.ignored_emails_report_title',        'te', N'విస్మరించిన ఇమెయిల్‌ల నివేదిక');
    it('intake.ignored_emails_export_csv',          'te', N'CSVకి ఎగుమతి చేయండి');
    it('intake.exceptional_email_master_title',     'te', N'మినహాయింపు ఇమెయిల్ మాస్టర్');

    -- ── ta (Tamil) ─────────────────────────────────────────────────────────
    it('intake.email_suppressed_by_rule',           'ta', N'இந்த மின்னஞ்சல் செயலில் உள்ள புறக்கணிப்பு விதியுடன் பொருந்தியது, எனவே எந்த முறையீட்டு வரைவும் உருவாக்கப்படவில்லை.');
    it('intake.duplicate_delivery_ignored',         'ta', N'இந்தச் செய்தி ஏற்கனவே பெறப்பட்டு செயலாக்கப்பட்டுவிட்டது. ஏற்கனவே உள்ள வரைவு கீழே காட்டப்பட்டுள்ளது.');
    it('intake.duplicate_linked_to_parent',         'ta', N'இது இப்போது முடிக்கப்பட்ட ஒரு முந்தைய முறையீட்டின் நகல். புதிய வரைவை உருவாக்குவதற்குப் பதிலாக மின்னஞ்சலும் அதன் இணைப்புகளும் அந்த முறையீட்டுடன் சேர்க்கப்பட்டுள்ளன.');
    it('intake.vernacular_manual_entry_required',   'ta', N'உள்ளடக்கம் ஒரு பிராந்திய மொழியில் உள்ளது, எனவே தானியங்கி உரை பிரித்தெடுத்தல் வேண்டுமென்றே முயற்சிக்கப்படவில்லை. இது திறமையான தரவு-உள்ளீட்டு ஆபரேட்டருக்கு அனுப்பப்பட்டுள்ளது.');
    it('intake.ocr_low_confidence_manual_entry',    'ta', N'படிவத்தைத் தானாக நிரப்பும் அளவுக்கு ஸ்கேன் செய்யப்பட்ட ஆவணத்தை நம்பகமாகப் படிக்க முடியவில்லை. விவரங்களைக் கைமுறையாக உள்ளிடவும்.');
    it('intake.manual_entry_required_banner',       'ta', N'கைமுறை உள்ளீடு தேவை — இந்த வரைவுக்குத் தானியங்கி உரை பிரித்தெடுத்தல் பயன்படுத்தப்படவில்லை.');
    it('intake.draft_not_found',                    'ta', N'வரைவு கண்டறியப்படவில்லை.');
    it('intake.ignore_rule_not_found',              'ta', N'புறக்கணிப்பு விதி கண்டறியப்படவில்லை.');
    it('intake.suggested_related_invalid_decision', 'ta', N'முடிவு ஏற்கப்பட்டது அல்லது நிராகரிக்கப்பட்டது என்பதில் ஒன்றாக இருக்க வேண்டும்.');
    it('intake.attachment_too_large',               'ta', N'ஒவ்வொரு கோப்பும் 2MB அல்லது அதற்குக் குறைவாக இருக்க வேண்டும்.');
    it('intake.attachment_total_too_large',         'ta', N'இணைப்புகளின் மொத்த அளவு 25MB அல்லது அதற்குக் குறைவாக இருக்க வேண்டும்.');
    it('intake.attachment_too_many',                'ta', N'நீங்கள் அதிகபட்சம் 10 கோப்புகளை இணைக்கலாம்.');
    it('intake.attachment_rejected',                'ta', N'கோப்பின் வகையை அதன் உள்ளடக்கத்திலிருந்து சரிபார்க்க முடியவில்லை, எனவே கோப்பு நிராகரிக்கப்பட்டது.');
    it('intake.ignored_emails_report_title',        'ta', N'புறக்கணிக்கப்பட்ட மின்னஞ்சல்கள் அறிக்கை');
    it('intake.ignored_emails_export_csv',          'ta', N'CSV ஆக ஏற்றுமதி செய்');
    it('intake.exceptional_email_master_title',     'ta', N'விதிவிலக்கு மின்னஞ்சல் முதன்மைப் பட்டியல்');

    -- ── gu (Gujarati) ──────────────────────────────────────────────────────
    it('intake.email_suppressed_by_rule',           'gu', N'આ ઈમેલ સક્રિય અવગણના નિયમ સાથે મેળ ખાય છે, તેથી કોઈ ફરિયાદ ડ્રાફ્ટ બનાવવામાં આવ્યો નથી.');
    it('intake.duplicate_delivery_ignored',         'gu', N'આ સંદેશ પહેલેથી જ પ્રાપ્ત થઈને પ્રક્રિયામાં લેવાયો છે. વર્તમાન ડ્રાફ્ટ નીચે બતાવેલ છે.');
    it('intake.duplicate_linked_to_parent',         'gu', N'આ અગાઉની એક ફરિયાદની નકલ છે જે હવે બંધ થઈ ગઈ છે. નવો ડ્રાફ્ટ બનાવવાને બદલે ઈમેલ અને તેના જોડાણો તે જ ફરિયાદમાં ઉમેરવામાં આવ્યાં છે.');
    it('intake.vernacular_manual_entry_required',   'gu', N'સામગ્રી પ્રાદેશિક ભાષામાં છે, તેથી સ્વયંસંચાલિત લખાણ નિષ્કર્ષણ જાણીજોઈને કરવામાં આવ્યું નથી. તેને કુશળ ડેટા-એન્ટ્રી ઓપરેટરને મોકલવામાં આવ્યું છે.');
    it('intake.ocr_low_confidence_manual_entry',    'gu', N'સ્કેન કરેલો દસ્તાવેજ ફોર્મ સ્વયં ભરી શકાય એટલી વિશ્વસનીયતાથી વાંચી શકાયો નથી. કૃપા કરીને વિગતો હાથે દાખલ કરો.');
    it('intake.manual_entry_required_banner',       'gu', N'હાથે નોંધ કરવી જરૂરી — આ ડ્રાફ્ટ માટે સ્વયંસંચાલિત લખાણ નિષ્કર્ષણ વપરાયું નથી.');
    it('intake.draft_not_found',                    'gu', N'ડ્રાફ્ટ મળ્યો નથી.');
    it('intake.ignore_rule_not_found',              'gu', N'અવગણના નિયમ મળ્યો નથી.');
    it('intake.suggested_related_invalid_decision', 'gu', N'નિર્ણય સ્વીકૃત અથવા નકારેલ હોવો જોઈએ.');
    it('intake.attachment_too_large',               'gu', N'દરેક ફાઈલ 2MB કે તેથી નાની હોવી જોઈએ.');
    it('intake.attachment_total_too_large',         'gu', N'જોડાણોનું કુલ કદ 25MB કે તેથી ઓછું હોવું જોઈએ.');
    it('intake.attachment_too_many',                'gu', N'તમે વધુમાં વધુ 10 ફાઈલો જોડી શકો છો.');
    it('intake.attachment_rejected',                'gu', N'ફાઈલનો પ્રકાર તેની અંદરની સામગ્રી પરથી ચકાસી શકાયો નથી, તેથી ફાઈલ નકારવામાં આવી.');
    it('intake.ignored_emails_report_title',        'gu', N'અવગણાયેલા ઈમેલનો અહેવાલ');
    it('intake.ignored_emails_export_csv',          'gu', N'CSV માં નિકાસ કરો');
    it('intake.exceptional_email_master_title',     'gu', N'અપવાદરૂપ ઈમેલ માસ્ટર');

    -- ── ur (Urdu, Arabic script) — digits in Eastern Arabic numerals: ۲ / ۲۵ / ۱۰
    it('intake.email_suppressed_by_rule',           'ur', N'یہ ای میل ایک فعال نظر انداز قاعدے سے مطابقت رکھتی ہے، اس لیے کوئی شکایتی مسودہ نہیں بنایا گیا۔');
    it('intake.duplicate_delivery_ignored',         'ur', N'یہ پیغام پہلے ہی موصول اور کارروائی میں لایا جا چکا ہے۔ موجودہ مسودہ نیچے دکھایا گیا ہے۔');
    it('intake.duplicate_linked_to_parent',         'ur', N'یہ ایک سابقہ شکایت کی نقل ہے جو اب بند ہو چکی ہے۔ نیا مسودہ بنانے کے بجائے ای میل اور اس کے منسلکات اسی موجودہ شکایت میں شامل کر دیے گئے ہیں۔');
    it('intake.vernacular_manual_entry_required',   'ur', N'مواد ایک علاقائی زبان میں ہے، اس لیے خودکار متن کشید کرنے کی دانستہ کوشش نہیں کی گئی۔ اسے ایک ماہر ڈیٹا اینٹری آپریٹر کو بھیج دیا گیا ہے۔');
    it('intake.ocr_low_confidence_manual_entry',    'ur', N'اسکین کردہ دستاویز اتنے بھروسے سے نہیں پڑھی جا سکی کہ فارم خود بخود بھرا جا سکے۔ براہ کرم تفصیلات ہاتھ سے درج کریں۔');
    it('intake.manual_entry_required_banner',       'ur', N'ہاتھ سے اندراج درکار — اس مسودے کے لیے خودکار متن کشید کرنا استعمال نہیں کیا گیا۔');
    it('intake.draft_not_found',                    'ur', N'مسودہ نہیں ملا۔');
    it('intake.ignore_rule_not_found',              'ur', N'نظر انداز قاعدہ نہیں ملا۔');
    it('intake.suggested_related_invalid_decision', 'ur', N'فیصلہ منظور شدہ یا مسترد شدہ ہونا چاہیے۔');
    it('intake.attachment_too_large',               'ur', N'ہر فائل ۲ ایم بی یا اس سے کم ہونی چاہیے۔');
    it('intake.attachment_total_too_large',         'ur', N'منسلکات کا کل حجم ۲۵ ایم بی یا اس سے کم ہونا چاہیے۔');
    it('intake.attachment_too_many',                'ur', N'آپ زیادہ سے زیادہ ۱۰ فائلیں منسلک کر سکتے ہیں۔');
    it('intake.attachment_rejected',                'ur', N'فائل کی قسم اس کے مندرجات سے تصدیق نہیں ہو سکی، اس لیے فائل مسترد کر دی گئی۔');
    it('intake.ignored_emails_report_title',        'ur', N'نظر انداز کی گئی ای میلوں کی رپورٹ');
    it('intake.ignored_emails_export_csv',          'ur', N'سی ایس وی میں برآمد کریں');
    it('intake.exceptional_email_master_title',     'ur', N'استثنائی ای میل ماسٹر');

    -- ── kn (Kannada) ───────────────────────────────────────────────────────
    it('intake.email_suppressed_by_rule',           'kn', N'ಈ ಇಮೇಲ್ ಸಕ್ರಿಯ ನಿರ್ಲಕ್ಷ್ಯ ನಿಯಮಕ್ಕೆ ಹೊಂದಿಕೆಯಾಗಿದೆ, ಆದ್ದರಿಂದ ಯಾವುದೇ ದೂರಿನ ಕರಡು ರಚಿಸಲಾಗಿಲ್ಲ.');
    it('intake.duplicate_delivery_ignored',         'kn', N'ಈ ಸಂದೇಶವನ್ನು ಈಗಾಗಲೇ ಸ್ವೀಕರಿಸಿ ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲಾಗಿದೆ. ಅಸ್ತಿತ್ವದಲ್ಲಿರುವ ಕರಡನ್ನು ಕೆಳಗೆ ತೋರಿಸಲಾಗಿದೆ.');
    it('intake.duplicate_linked_to_parent',         'kn', N'ಇದು ಈಗ ಮುಕ್ತಾಯಗೊಂಡಿರುವ ಹಿಂದಿನ ದೂರಿನ ನಕಲು. ಹೊಸ ಕರಡು ರಚಿಸುವ ಬದಲು ಇಮೇಲ್ ಮತ್ತು ಅದರ ಲಗತ್ತುಗಳನ್ನು ಅದೇ ದೂರಿಗೆ ಸೇರಿಸಲಾಗಿದೆ.');
    it('intake.vernacular_manual_entry_required',   'kn', N'ವಿಷಯವು ಪ್ರಾದೇಶಿಕ ಭಾಷೆಯಲ್ಲಿದೆ, ಆದ್ದರಿಂದ ಸ್ವಯಂಚಾಲಿತ ಪಠ್ಯ ಹೊರತೆಗೆಯುವಿಕೆಯನ್ನು ಉದ್ದೇಶಪೂರ್ವಕವಾಗಿ ಪ್ರಯತ್ನಿಸಲಾಗಿಲ್ಲ. ಇದನ್ನು ನುರಿತ ದತ್ತಾಂಶ-ನಮೂದು ನಿರ್ವಾಹಕರಿಗೆ ಕಳುಹಿಸಲಾಗಿದೆ.');
    it('intake.ocr_low_confidence_manual_entry',    'kn', N'ನಮೂನೆಯನ್ನು ಸ್ವಯಂಚಾಲಿತವಾಗಿ ತುಂಬುವಷ್ಟು ವಿಶ್ವಾಸಾರ್ಹವಾಗಿ ಸ್ಕ್ಯಾನ್ ಮಾಡಿದ ದಾಖಲೆಯನ್ನು ಓದಲಾಗಿಲ್ಲ. ದಯವಿಟ್ಟು ವಿವರಗಳನ್ನು ಕೈಯಾರೆ ನಮೂದಿಸಿ.');
    it('intake.manual_entry_required_banner',       'kn', N'ಕೈಯಾರೆ ನಮೂದು ಅಗತ್ಯ — ಈ ಕರಡಿಗೆ ಸ್ವಯಂಚಾಲಿತ ಪಠ್ಯ ಹೊರತೆಗೆಯುವಿಕೆಯನ್ನು ಬಳಸಲಾಗಿಲ್ಲ.');
    it('intake.draft_not_found',                    'kn', N'ಕರಡು ಕಂಡುಬಂದಿಲ್ಲ.');
    it('intake.ignore_rule_not_found',              'kn', N'ನಿರ್ಲಕ್ಷ್ಯ ನಿಯಮ ಕಂಡುಬಂದಿಲ್ಲ.');
    it('intake.suggested_related_invalid_decision', 'kn', N'ನಿರ್ಧಾರವು ಸ್ವೀಕರಿಸಲಾಗಿದೆ ಅಥವಾ ತಿರಸ್ಕರಿಸಲಾಗಿದೆ ಎಂಬುದರಲ್ಲಿ ಒಂದಾಗಿರಬೇಕು.');
    it('intake.attachment_too_large',               'kn', N'ಪ್ರತಿ ಕಡತ 2MB ಅಥವಾ ಅದಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು.');
    it('intake.attachment_total_too_large',         'kn', N'ಲಗತ್ತುಗಳ ಒಟ್ಟು ಗಾತ್ರ 25MB ಅಥವಾ ಅದಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು.');
    it('intake.attachment_too_many',                'kn', N'ನೀವು ಗರಿಷ್ಠ 10 ಕಡತಗಳನ್ನು ಲಗತ್ತಿಸಬಹುದು.');
    it('intake.attachment_rejected',                'kn', N'ಕಡತದ ಪ್ರಕಾರವನ್ನು ಅದರ ಒಳವಿಷಯದಿಂದ ಪರಿಶೀಲಿಸಲಾಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಕಡತವನ್ನು ತಿರಸ್ಕರಿಸಲಾಗಿದೆ.');
    it('intake.ignored_emails_report_title',        'kn', N'ನಿರ್ಲಕ್ಷಿಸಿದ ಇಮೇಲ್‌ಗಳ ವರದಿ');
    it('intake.ignored_emails_export_csv',          'kn', N'CSV ಗೆ ರಫ್ತು ಮಾಡಿ');
    it('intake.exceptional_email_master_title',     'kn', N'ಅಪವಾದ ಇಮೇಲ್ ಮಾಸ್ಟರ್');

    -- ── ml (Malayalam) ─────────────────────────────────────────────────────
    it('intake.email_suppressed_by_rule',           'ml', N'ഈ ഇമെയിൽ സജീവമായ ഒരു അവഗണന നിയമവുമായി പൊരുത്തപ്പെട്ടു, അതിനാൽ പരാതിയുടെ കരട് സൃഷ്ടിച്ചിട്ടില്ല.');
    it('intake.duplicate_delivery_ignored',         'ml', N'ഈ സന്ദേശം നേരത്തെ തന്നെ ലഭിച്ച് പ്രോസസ് ചെയ്തിട്ടുണ്ട്. നിലവിലുള്ള കരട് താഴെ കാണിച്ചിരിക്കുന്നു.');
    it('intake.duplicate_linked_to_parent',         'ml', N'ഇത് ഇപ്പോൾ അവസാനിപ്പിച്ച ഒരു മുൻ പരാതിയുടെ തനിപ്പകർപ്പാണ്. പുതിയ കരട് സൃഷ്ടിക്കുന്നതിനു പകരം ഇമെയിലും അതിന്റെ അനുബന്ധങ്ങളും ആ പരാതിയോട് ചേർത്തിട്ടുണ്ട്.');
    it('intake.vernacular_manual_entry_required',   'ml', N'ഉള്ളടക്കം ഒരു പ്രാദേശിക ഭാഷയിലാണ്, അതിനാൽ സ്വയമേവയുള്ള വാചകം വേർതിരിച്ചെടുക്കൽ മനഃപൂർവം ശ്രമിച്ചിട്ടില്ല. ഇത് വിദഗ്ധനായ ഡാറ്റാ എൻട്രി ഓപ്പറേറ്റർക്ക് കൈമാറിയിട്ടുണ്ട്.');
    it('intake.ocr_low_confidence_manual_entry',    'ml', N'ഫോം സ്വയമേവ പൂരിപ്പിക്കാൻ കഴിയുന്നത്ര വിശ്വസനീയമായി സ്കാൻ ചെയ്ത രേഖ വായിക്കാൻ കഴിഞ്ഞില്ല. വിവരങ്ങൾ സ്വമേധയാ നൽകുക.');
    it('intake.manual_entry_required_banner',       'ml', N'സ്വമേധയാ വിവരം നൽകേണ്ടതുണ്ട് — ഈ കരടിനായി സ്വയമേവയുള്ള വാചകം വേർതിരിച്ചെടുക്കൽ ഉപയോഗിച്ചിട്ടില്ല.');
    it('intake.draft_not_found',                    'ml', N'കരട് കണ്ടെത്തിയില്ല.');
    it('intake.ignore_rule_not_found',              'ml', N'അവഗണന നിയമം കണ്ടെത്തിയില്ല.');
    it('intake.suggested_related_invalid_decision', 'ml', N'തീരുമാനം സ്വീകരിച്ചു അല്ലെങ്കിൽ തള്ളി എന്നതിൽ ഒന്നായിരിക്കണം.');
    it('intake.attachment_too_large',               'ml', N'ഓരോ ഫയലും 2MB അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.');
    it('intake.attachment_total_too_large',         'ml', N'അനുബന്ധങ്ങളുടെ ആകെ വലുപ്പം 25MB അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.');
    it('intake.attachment_too_many',                'ml', N'നിങ്ങൾക്ക് പരമാവധി 10 ഫയലുകൾ ചേർക്കാം.');
    it('intake.attachment_rejected',                'ml', N'ഫയലിന്റെ തരം അതിന്റെ ഉള്ളടക്കത്തിൽ നിന്ന് പരിശോധിക്കാൻ കഴിഞ്ഞില്ല, അതിനാൽ ഫയൽ നിരസിച്ചു.');
    it('intake.ignored_emails_report_title',        'ml', N'അവഗണിച്ച ഇമെയിലുകളുടെ റിപ്പോർട്ട്');
    it('intake.ignored_emails_export_csv',          'ml', N'CSV ആയി എക്സ്പോർട്ട് ചെയ്യുക');
    it('intake.exceptional_email_master_title',     'ml', N'അപവാദ ഇമെയിൽ മാസ്റ്റർ');

    COMMIT;
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- PART C. Corrective UPDATEs — key-scoped, never English-text-scoped
--
--   Same reasoning as oracle/V30 Part A. Both the seeder and Part A above are insert-if-absent, so a
--   key row already committed with a NULL/blank MODULE or a NULL DEFAULT_VALUE keeps those values
--   forever; nothing in either seed path repairs it. These UPDATEs do, and they are idempotent because
--   they only match rows that are still wrong.
--
--   C1 matters operationally: the intake admin screens list keys by module and the Ignored Emails
--   Report groups its labels the same way, so a key stranded outside MODULE='intake' is invisible to
--   both even though the row exists.
--
--   DEFAULT_VALUE is a CLOB, so emptiness is tested with DBMS_LOB.GETLENGTH — `= ''` is NULL-equivalent
--   in Oracle and would match nothing.
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_col NUMBER;

    PROCEDURE fix_default(p_code VARCHAR2, p_val VARCHAR2) IS
    BEGIN
        UPDATE TRANSLATION_KEYS
           SET DEFAULT_VALUE = TO_CLOB(p_val), UPDATED_AT = SYSTIMESTAMP
         WHERE CODE = p_code
           AND (DEFAULT_VALUE IS NULL OR DBMS_LOB.GETLENGTH(DEFAULT_VALUE) = 0);
    END;
BEGIN
    -- C1. Module tagging. Scoped by CODE, so all 16 keys are reached regardless of what text they hold.
    UPDATE TRANSLATION_KEYS
       SET MODULE = 'intake', UPDATED_AT = SYSTIMESTAMP
     WHERE CODE IN ('intake.email_suppressed_by_rule',
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
       AND (MODULE IS NULL OR MODULE <> 'intake');

    -- C2. Blank English fallback. A key row with a NULL/empty DEFAULT_VALUE degrades to the raw key for
    --     any locale that has no row of its own, which is exactly the failure this migration exists to
    --     prevent. Only empty rows are touched, so a deliberate wording change is not clobbered.
    SELECT COUNT(*) INTO v_col FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'TRANSLATION_KEYS' AND COLUMN_NAME = 'DEFAULT_VALUE';
    IF v_col > 0 THEN
        fix_default('intake.draft_not_found',                'Draft not found.');
        fix_default('intake.ignore_rule_not_found',          'Ignore rule not found.');
        fix_default('intake.ignored_emails_report_title',    'Ignored Emails Report');
        fix_default('intake.ignored_emails_export_csv',      'Export to CSV');
        fix_default('intake.exceptional_email_master_title', 'Exceptional Email Master');
    ELSE
        DBMS_OUTPUT.PUT_LINE('V40: TRANSLATION_KEYS.DEFAULT_VALUE absent, skipping Part C2.');
    END IF;

    COMMIT;
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- Index guards (Oracle has no CREATE INDEX IF NOT EXISTS)
--
--   Both indexes exist in every environment seen; the guards make this file safe on a schema created
--   before they were added, and re-running is a no-op either way. IDX_TRANS_KEY_LOCALE is the unique
--   index that makes the existence checks above a hard constraint rather than a convention, so it is
--   only created when the V4-shaped columns are actually present.
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_tab NUMBER;
    v_idx NUMBER;
    v_col NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_tab FROM USER_TABLES WHERE TABLE_NAME = 'TRANSLATION_KEYS';
    SELECT COUNT(*) INTO v_idx FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_TKEY_MODULE';
    IF v_tab = 1 AND v_idx = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_TKEY_MODULE ON TRANSLATION_KEYS(MODULE)';
    END IF;

    SELECT COUNT(*) INTO v_col FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'TRANSLATIONS' AND COLUMN_NAME = 'TRANSLATION_KEY_ID';
    SELECT COUNT(*) INTO v_idx FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_TRANS_KEY_LOCALE';
    IF v_col = 1 AND v_idx = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX IDX_TRANS_KEY_LOCALE ON TRANSLATIONS(TRANSLATION_KEY_ID, LOCALE)';
    END IF;
END;
/
