-- ============================================================
-- V60 — Restore the {{clause}} placeholder in eligibility.block_not_filed
-- Oracle counterpart of MySQL V62. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- The rationale is recorded in full in the MySQL counterpart (database/V62). In brief:
-- `eligibility.block_not_filed` is citizen-facing prose telling a complainant which Scheme clause bars
-- their complaint. The stored rows had clause 10(1)(j) BAKED INTO THE PROSE in all ten locales instead
-- of carrying the {{clause}} placeholder the server interpolates from CLOSURE_CLAUSE_MASTER, so the
-- message would misstate the law the moment a different clause applied.
--
-- EligibilityTranslationSeeder already holds the correct placeholder text but is seedIfAbsent, so it
-- never rewrote the rows an earlier baked version of itself had inserted. These strings are copied
-- verbatim from that seeder.
--
-- SCOPED BY KEY CODE, never by English text: the rows are in eight non-Latin scripts. The BENGALI row
-- also writes the year in Bengali numerals, so a repair keyed on ASCII '2021' would skip it.
--
-- Re-running is safe: every UPDATE is additionally guarded on the placeholder being absent, so a row
-- already corrected by hand is left untouched.
-- ============================================================

-- English is carried by TRANSLATION_KEYS.DEFAULT_VALUE, not by a TRANSLATIONS row.
UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'in terms of clause {{clause}} of Reserve Bank – Integrated Ombudsman Scheme, 2021, the complaint cannot be processed under the Scheme.',
       UPDATED_AT = SYSTIMESTAMP
 WHERE CODE = 'eligibility.block_not_filed'
   AND DEFAULT_VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t
   SET t.VALUE = 'in terms of clause {{clause}} of Reserve Bank – Integrated Ombudsman Scheme, 2021, the complaint cannot be processed under the Scheme.',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'en'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'रिज़र्व बैंक – एकीकृत लोकपाल योजना, 2021 के खंड {{clause}} के अनुसार, शिकायत को योजना के तहत संसाधित नहीं किया जा सकता।',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'hi'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'रिझर्व्ह बँक – एकीकृत लोकपाल योजना, 2021 च्या कलम {{clause}} नुसार, तक्रार योजनेअंतर्गत प्रक्रिया करता येत नाही.',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'mr'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'রিজার্ভ ব্যাংক – সমন্বিত ওম্বডসম্যান স্কিম, ২০২১-এর ধারা {{clause}} অনুসারে, এই অভিযোগ স্কিমের অধীনে প্রক্রিয়া করা যাবে না।',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'bn'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'రిజర్వ్ బ్యాంక్ – సమగ్ర ఓంబుడ్స్‌మన్ పథకం, 2021 క్లాజ్ {{clause}} ప్రకారం, ఫిర్యాదును పథకం కింద ప్రాసెస్ చేయడం సాధ్యం కాదు.',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'te'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ரிசர்வ் வங்கி – ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டம், 2021 பிரிவு {{clause}} படி, புகார் திட்டத்தின் கீழ் செயல்படுத்த முடியாது.',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'ta'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'રિઝર્વ બેંક – સંકલિત ઓમ્બડ્સમેન યોજના, 2021 ની કલમ {{clause}} મુજબ, ફરિયાદ યોજના હેઠળ પ્રક્રિયા કરી શકાતી નથી.',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'gu'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ریزرو بینک – مربوط اومبڈزمین اسکیم، 2021 کی شق {{clause}} کے مطابق، شکایت اسکیم کے تحت عملدرآمد نہیں ہو سکتی۔',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'ur'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್ – ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆ, 2021 ಕಲಂ {{clause}} ಪ್ರಕಾರ, ದೂರನ್ನು ಯೋಜನೆಯಡಿ ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ.',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'kn'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'റിസർവ് ബാങ്ക് – സംയോജിത ഓംബുഡ്സ്മാൻ സ്കീം, 2021 ക്ലോസ് {{clause}} പ്രകാരം, പരാതി സ്കീമിന് കീഴിൽ പ്രോസസ്സ് ചെയ്യാൻ കഴിയില്ല.',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'ml'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_not_filed');

COMMIT;

-- ============================================================
-- PART 2: eligibility.block_sub_judice baked clause 10(2)(b)(ii) in en/hi/mr/bn.
-- Only these four drifted; the other six locales do not cite a clause in this message and are left
-- untouched rather than having a citation invented for them.
-- ============================================================

UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause {{clause}} of the Reserve Bank - Integrated Ombudsman Scheme, 2021.',
       UPDATED_AT = SYSTIMESTAMP
 WHERE CODE = 'eligibility.block_sub_judice'
   AND DEFAULT_VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t
   SET t.VALUE = 'चूंकि आपकी शिकायत न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण के समक्ष लंबित है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के खंड {{clause}} के तहत अस्वीकार्य के रूप में बंद किया जाएगा।',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'hi'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_sub_judice');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'तुमची तक्रार न्यायालय/न्यायाधिकरण/लवाद/प्राधिकरणासमोर प्रलंबित असल्याने, ती रिझर्व्ह बँक - एकीकृत लोकपाल योजना, 2021 च्या कलम {{clause}} अंतर्गत अस्वीकार्य म्हणून बंद केली जाईल.',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'mr'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_sub_judice');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'আপনার অভিযোগ আদালত/ট্রাইব্যুনাল/সালিশ/কর্তৃপক্ষের কাছে বিচারাধীন থাকায়, এটি রিজার্ভ ব্যাংক - সমন্বিত ওম্বডসম্যান স্কিম, ২০২১-এর ধারা {{clause}} অনুসারে অগ্রহণযোগ্য হিসেবে বন্ধ করা হবে।',
       t.UPDATED_AT = SYSTIMESTAMP
 WHERE t.LOCALE = 'bn'
   AND t.VALUE NOT LIKE '%{{clause}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'eligibility.block_sub_judice');

COMMIT;

-- =================================================================================================
-- PART 3: ELIGIBILITY_QUESTION_MASTER.BLOCK_MESSAGE bakes the clause too.
--
-- The master row carries BOTH a CLAUSE_REFERENCE column and the prose the citizen is shown. Two rows
-- had the clause number written into the prose as well, so the citation was stored twice and the copy
-- in the prose could not track the column. `filedWithRE` baked 10(1)(j) and `isSubJudice` baked
-- 10(2)(b)(ii); both now carry the {{clause}} placeholder, which the component interpolates from
-- CLAUSE_REFERENCE with a GLOBAL regex.
--
-- The other four clause-bearing rows (alreadySettled, staffOfRE, previouslyFiledWithCEPC,
-- employerRelationship) deliberately do NOT cite a clause in their prose at all. A citation is not
-- added to them here: which clause each cites is a legal question, and inventing one in a migration is
-- exactly what the fail-closed rule forbids. They keep their CLAUSE_REFERENCE column for the workflow.
-- =================================================================================================

UPDATE ELIGIBILITY_QUESTION_MASTER
   SET BLOCK_MESSAGE = 'in terms of clause {{clause}} of Reserve Bank – Integrated Ombudsman Scheme, 2021, the complaint cannot be processed under the Scheme.'
 WHERE QUESTION_KEY = 'filedWithRE'
   AND BLOCK_MESSAGE LIKE '%10(1)(j)%';

UPDATE ELIGIBILITY_QUESTION_MASTER
   SET BLOCK_MESSAGE = 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause {{clause}} of the Reserve Bank - Integrated Ombudsman Scheme, 2021.'
 WHERE QUESTION_KEY = 'isSubJudice'
   AND BLOCK_MESSAGE LIKE '%10(2)(b)(ii)%';

COMMIT;
