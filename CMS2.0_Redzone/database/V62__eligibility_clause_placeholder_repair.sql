-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- V62 — Restore the {{clause}} placeholder in the eligibility "not filed" block message.
--
-- THE DEFECT. `eligibility.block_not_filed` is citizen-facing prose explaining why a complaint cannot
-- be processed. It is supposed to carry a `{{clause}}` placeholder that the server interpolates from
-- CLOSURE_CLAUSE_MASTER, so the clause a complainant is told about is the clause actually applied.
-- Instead the stored rows have clause 10(1)(j) BAKED INTO THE PROSE in all ten locales. A hardcoded
-- clause number in a legal notice is exactly the citizen-facing correctness failure this project
-- treats as fail-closed: if the applicable clause ever differs from 10(1)(j), every one of these
-- messages silently misstates the law to the complainant.
--
-- WHY A MIGRATION AND NOT A SEEDER EDIT. EligibilityTranslationSeeder ALREADY has the correct
-- placeholder text for English and all nine other locales — it is `seedIfAbsent`, so it never rewrote
-- rows that were inserted by an earlier, baked version of itself. Correcting a seeder default does not
-- fix rows already in the database. The strings below are copied verbatim from that seeder, so the two
-- cannot disagree after this runs.
--
-- SCOPED BY KEY CODE, NEVER BY ENGLISH TEXT. The localised rows are in Devanagari, Bengali, Telugu,
-- Tamil, Gujarati, Urdu, Kannada and Malayalam script; an English substring match would find none of
-- them. Note also that the BENGALI row writes the YEAR IN BENGALI NUMERALS (২০২১, not 2021), so a
-- repair keyed on the ASCII digits "2021" would silently skip Bengali.
--
-- Re-running is safe: each UPDATE is idempotent and additionally guarded on the placeholder being
-- absent, so a row an operator has already corrected by hand is left alone.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

-- English lives in TRANSLATION_KEYS.DEFAULT_VALUE, not in TRANSLATIONS (only 138 explicit `en` rows
-- exist for ~1876 keys), so the English repair is an update to the key's default.
UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'in terms of clause {{clause}} of Reserve Bank – Integrated Ombudsman Scheme, 2021, the complaint cannot be processed under the Scheme.',
       UPDATED_AT = NOW()
 WHERE CODE = 'eligibility.block_not_filed'
   AND DEFAULT_VALUE NOT LIKE '%{{clause}}%';

-- Any explicit `en` row, if one exists, must agree with the default.
UPDATE TRANSLATIONS t
  JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'in terms of clause {{clause}} of Reserve Bank – Integrated Ombudsman Scheme, 2021, the complaint cannot be processed under the Scheme.',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'en'
   AND t.VALUE NOT LIKE '%{{clause}}%';

-- ── The nine localised rows, verbatim from EligibilityTranslationSeeder ─────────────────────────
UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'रिज़र्व बैंक – एकीकृत लोकपाल योजना, 2021 के खंड {{clause}} के अनुसार, शिकायत को योजना के तहत संसाधित नहीं किया जा सकता।',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'hi' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'रिझर्व्ह बँक – एकीकृत लोकपाल योजना, 2021 च्या कलम {{clause}} नुसार, तक्रार योजनेअंतर्गत प्रक्रिया करता येत नाही.',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'mr' AND t.VALUE NOT LIKE '%{{clause}}%';

-- Bengali: the year is ২০২১ in Bengali numerals, which is why this cannot be repaired by matching '2021'.
UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'রিজার্ভ ব্যাংক – সমন্বিত ওম্বডসম্যান স্কিম, ২০২১-এর ধারা {{clause}} অনুসারে, এই অভিযোগ স্কিমের অধীনে প্রক্রিয়া করা যাবে না।',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'bn' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'రిజర్వ్ బ్యాంక్ – సమగ్ర ఓంబుడ్స్‌మన్ పథకం, 2021 క్లాజ్ {{clause}} ప్రకారం, ఫిర్యాదును పథకం కింద ప్రాసెస్ చేయడం సాధ్యం కాదు.',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'te' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ரிசர்வ் வங்கி – ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டம், 2021 பிரிவு {{clause}} படி, புகார் திட்டத்தின் கீழ் செயல்படுத்த முடியாது.',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'ta' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'રિઝર્વ બેંક – સંકલિત ઓમ્બડ્સમેન યોજના, 2021 ની કલમ {{clause}} મુજબ, ફરિયાદ યોજના હેઠળ પ્રક્રિયા કરી શકાતી નથી.',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'gu' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ریزرو بینک – مربوط اومبڈزمین اسکیم، 2021 کی شق {{clause}} کے مطابق، شکایت اسکیم کے تحت عملدرآمد نہیں ہو سکتی۔',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'ur' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್ – ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆ, 2021 ಕಲಂ {{clause}} ಪ್ರಕಾರ, ದೂರನ್ನು ಯೋಜನೆಯಡಿ ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ.',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'kn' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'റിസർവ് ബാങ്ക് – സംയോജിത ഓംബുഡ്സ്മാൻ സ്കീം, 2021 ക്ലോസ് {{clause}} പ്രകാരം, പരാതി സ്കീമിന് കീഴിൽ പ്രോസസ്സ് ചെയ്യാൻ കഴിയില്ല.',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_not_filed' AND t.LOCALE = 'ml' AND t.VALUE NOT LIKE '%{{clause}}%';

-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- PART 2 (same defect, second key): eligibility.block_sub_judice bakes clause 10(2)(b)(ii).
--
-- Found by e2e/public/eligibility-clause-interpolation.spec.ts, which checks BOTH block_not_filed and
-- block_sub_judice. The seeder already holds '{{clause}}' for English, hi, mr and bn; only these four
-- stored values drifted. The other six locales never cite a clause in this message, so they are
-- correctly left alone rather than having a citation added to them.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause {{clause}} of the Reserve Bank - Integrated Ombudsman Scheme, 2021.',
       UPDATED_AT = NOW()
 WHERE CODE = 'eligibility.block_sub_judice'
   AND DEFAULT_VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'चूंकि आपकी शिकायत न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण के समक्ष लंबित है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के खंड {{clause}} के तहत अस्वीकार्य के रूप में बंद किया जाएगा।',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_sub_judice' AND t.LOCALE = 'hi' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'तुमची तक्रार न्यायालय/न्यायाधिकरण/लवाद/प्राधिकरणासमोर प्रलंबित असल्याने, ती रिझर्व्ह बँक - एकीकृत लोकपाल योजना, 2021 च्या कलम {{clause}} अंतर्गत अस्वीकार्य म्हणून बंद केली जाईल.',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_sub_judice' AND t.LOCALE = 'mr' AND t.VALUE NOT LIKE '%{{clause}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'আপনার অভিযোগ আদালত/ট্রাইব্যুনাল/সালিশ/কর্তৃপক্ষের কাছে বিচারাধীন থাকায়, এটি রিজার্ভ ব্যাংক - সমন্বিত ওম্বডসম্যান স্কিম, ২০২১-এর ধারা {{clause}} অনুসারে অগ্রহণযোগ্য হিসেবে বন্ধ করা হবে।',
       t.UPDATED_AT = NOW()
 WHERE k.CODE = 'eligibility.block_sub_judice' AND t.LOCALE = 'bn' AND t.VALUE NOT LIKE '%{{clause}}%';

-- ─────────────────────────────────────────────────────────────────────────────────────────────────
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
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

UPDATE ELIGIBILITY_QUESTION_MASTER
   SET BLOCK_MESSAGE = 'in terms of clause {{clause}} of Reserve Bank – Integrated Ombudsman Scheme, 2021, the complaint cannot be processed under the Scheme.'
 WHERE QUESTION_KEY = 'filedWithRE'
   AND BLOCK_MESSAGE LIKE '%10(1)(j)%';

UPDATE ELIGIBILITY_QUESTION_MASTER
   SET BLOCK_MESSAGE = 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause {{clause}} of the Reserve Bank - Integrated Ombudsman Scheme, 2021.'
 WHERE QUESTION_KEY = 'isSubJudice'
   AND BLOCK_MESSAGE LIKE '%10(2)(b)(ii)%';
