-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- V103 — Finish what V67 started: remove the remaining BAKED file-size digits from upload prose.
--
-- RULING BEING IMPLEMENTED. Configuration is authoritative and the figure is expected to move; it
-- currently stands at 2 MB per file / 25 MB total / 10 files, held in SYSTEM_CONFIG
-- (cms.attachments.max_file_size_bytes, max_total_size_bytes, max_file_count) and served by
-- GET /api/v1/config/upload-limits. A digit written into prose cannot track that.
--
-- WHY A MIGRATION AND NOT JUST A SEEDER EDIT. Every translation seeder is insert-if-absent BY KEY CODE,
-- so once a key exists no later run rewrites its text. Correcting the Java defaults (done in the same
-- change) fixes only a database that has never been seeded; every existing environment keeps the old
-- string. Both halves are required.
--
-- WHAT V67 LEFT BEHIND. V67 converted intake.attachment_too_large and aa.upload.error_file_too_large
-- and stopped there, so these still stated a literal:
--   * aa.upload.error_total_too_large   — "25 MB" in en + 9 locales
--   * aa.upload.error_too_many_files    — "10 files" in en + 9 locales (Bengali/Urdu in native digits)
-- Both are rendered beside aa.upload.error_file_too_large in the AA register screen, which V67 DID
-- convert — so one of the three messages tracked configuration and the other two did not, on the same
-- control. That is the worst arrangement: it looks correct in whichever case you happen to test.
--
-- ── SCOPE DELIBERATELY EXCLUDES FOUR OTHER BAKED KEYS ────────────────────────────────────────────
-- complaint.attachment_hint, eligibility.upload_hint, form.upload_size and
-- intake.attachment_total_too_large also bake a digit, and are NOT touched here.
--
-- A PLACEHOLDER IS ONLY AN IMPROVEMENT IF SOMETHING INTERPOLATES IT. None of those four has a caller
-- that supplies params:
--   * complaint.attachment_hint, eligibility.upload_hint, form.upload_size — no code references them
--     at all; they are seeded and unread. Converting them would trade a stale number for a literal
--     "{{size}}" the moment anyone did start rendering them.
--   * intake.attachment_total_too_large — raised by IntakeAttachmentValidator via IntakeUploadRejected,
--     which carries a messageKey and a message but NO parameter map, so the placeholder could not be
--     filled server-side either.
-- Converting prose whose consumer cannot interpolate is how a raw "{{size}}" reaches a user, which is a
-- worse defect than a stale digit: it is visibly broken rather than quietly wrong. Those four need their
-- consumers built first, and are recorded as outstanding rather than silently half-fixed here.
--
-- RE-RUNNABLE. Every statement is scoped BY KEY CODE and requires the placeholder to still be ABSENT, so
-- a second run matches nothing and a string an operator has since reworded is left alone.
--
-- NOTE ON NUMERALS. Bengali and Urdu spell digits in their own scripts (২৫, ۲۵, ১০, ۱۰), so a WHERE
-- clause looking for '25' or '10' would miss them. Scoping on the ABSENCE of the placeholder catches
-- every variant regardless of how the numeral was written — the same lesson V67 recorded.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

-- ── English defaults, held on the key itself ────────────────────────────────────────────────────
UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'All attachments together must be {{total}} MB or smaller.'
 WHERE CODE = 'aa.upload.error_total_too_large'
   AND DEFAULT_VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'You may attach at most {{count}} files.'
 WHERE CODE = 'aa.upload.error_too_many_files'
   AND DEFAULT_VALUE NOT LIKE '%{{count}}%';

-- ── aa.upload.error_total_too_large, nine locales ───────────────────────────────────────────────
UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'सभी अनुलग्नक मिलाकर {{total}} एमबी या उससे कम होने चाहिए।'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'hi' AND t.VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'सर्व संलग्नके मिळून {{total}} एमबी किंवा त्यापेक्षा कमी असावीत.'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'mr' AND t.VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'সব সংযুক্তি একসঙ্গে {{total}} এমবি বা তার কম হতে হবে।'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'bn' AND t.VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'అన్ని జోడింపులు కలిపి {{total}} ఎంబీ లోపు ఉండాలి.'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'te' AND t.VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'அனைத்து இணைப்புகளும் சேர்ந்து {{total}} எம்பி அல்லது குறைவாக இருக்க வேண்டும்.'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'ta' AND t.VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'તમામ જોડાણો મળીને {{total}} એમબી અથવા તેથી નાનાં હોવાં જોઈએ.'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'gu' AND t.VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'تمام منسلکات مل کر {{total}} ایم بی یا اس سے کم ہونے چاہیے۔'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'ur' AND t.VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ಎಲ್ಲ ಲಗತ್ತುಗಳು ಒಟ್ಟಾಗಿ {{total}} ಎಂಬಿ ಅಥವಾ ಕಡಿಮೆ ಇರಬೇಕು.'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'kn' AND t.VALUE NOT LIKE '%{{total}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'എല്ലാ അറ്റാച്ച്‌മെന്റുകളും ചേർന്ന് {{total}} എംബി അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.'
 WHERE k.CODE = 'aa.upload.error_total_too_large' AND t.LOCALE = 'ml' AND t.VALUE NOT LIKE '%{{total}}%';

-- ── aa.upload.error_too_many_files, nine locales ────────────────────────────────────────────────
UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'आप अधिकतम {{count}} फ़ाइलें संलग्न कर सकते हैं।'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'hi' AND t.VALUE NOT LIKE '%{{count}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'तुम्ही जास्तीत जास्त {{count}} फाइल्स जोडू शकता.'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'mr' AND t.VALUE NOT LIKE '%{{count}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'আপনি সর্বাধিক {{count}}টি ফাইল সংযুক্ত করতে পারেন।'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'bn' AND t.VALUE NOT LIKE '%{{count}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'మీరు గరిష్ఠంగా {{count}} ఫైల్‌లను జోడించగలరు.'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'te' AND t.VALUE NOT LIKE '%{{count}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'நீங்கள் அதிகபட்சம் {{count}} கோப்புகளை இணைக்கலாம்.'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'ta' AND t.VALUE NOT LIKE '%{{count}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'તમે વધુમાં વધુ {{count}} ફાઇલો જોડી શકો છો.'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'gu' AND t.VALUE NOT LIKE '%{{count}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'آپ زیادہ سے زیادہ {{count}} فائلیں منسلک کر سکتے ہیں۔'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'ur' AND t.VALUE NOT LIKE '%{{count}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ನೀವು ಗರಿಷ್ಠ {{count}} ಕಡತಗಳನ್ನು ಲಗತ್ತಿಸಬಹುದು.'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'kn' AND t.VALUE NOT LIKE '%{{count}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'നിങ്ങൾക്ക് പരമാവധി {{count}} ഫയലുകൾ അറ്റാച്ച് ചെയ്യാം.'
 WHERE k.CODE = 'aa.upload.error_too_many_files' AND t.LOCALE = 'ml' AND t.VALUE NOT LIKE '%{{count}}%';
