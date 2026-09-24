-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- V67 — Replace the BAKED file-size digit in the two upload error messages with a {{size}} placeholder.
--
-- WHY A MIGRATION IS REQUIRED AND A SEEDER EDIT IS NOT ENOUGH. Both seeders are insert-if-absent BY KEY
-- CODE, so once a key exists no later run rewrites its text. Correcting the Java default therefore
-- fixes only a database that has never been seeded; every existing environment keeps the old string.
--
-- WHAT WAS WRONG. The product rule is 5 MB, configurable via cms.upload.max_file_size and served by
-- GET /api/v1/config/upload-limits. These two messages instead hardcoded a digit:
--   * intake.attachment_too_large     — "2MB" in English and nine translations. Actively WRONG: it
--                                       stated a limit less than half the one the server enforces.
--   * aa.upload.error_file_too_large  — English had already been corrected to "5 MB", but all nine
--                                       translations still said 2. So the message a citizen saw
--                                       depended on their language, which is worse than being uniformly
--                                       wrong: it cannot be spotted by testing in English.
--
-- A baked digit cannot track configuration. Retuning the limit would silently desynchronise every
-- locale again, which is exactly how the 2-vs-5 split arose. {{size}} is interpolated at render time
-- from the configured value, following ui.upload.error_file_too_large which already did this correctly.
--
-- Re-runnable: each UPDATE is scoped BY KEY CODE and requires the value to still lack the placeholder,
-- so a second run matches nothing and a string an operator has since reworded is left alone.
--
-- NOTE ON NUMERALS: Bengali and Urdu spelled the digit in their own scripts (২, ۲) rather than ASCII,
-- so a WHERE clause looking for '2' would have missed them. Scoping on the ABSENCE of {{size}} catches
-- every variant regardless of how the numeral was written.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

-- ── English defaults, held on the key itself ────────────────────────────────────────────────────
UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'Each file must be {{size}}MB or smaller.'
 WHERE CODE = 'intake.attachment_too_large'
   AND DEFAULT_VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'Each file must be {{size}} MB or smaller.'
 WHERE CODE = 'aa.upload.error_file_too_large'
   AND DEFAULT_VALUE NOT LIKE '%{{size}}%';

-- ── intake.attachment_too_large, nine locales ───────────────────────────────────────────────────
UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'प्रत्येक फ़ाइल {{size}}MB या उससे छोटी होनी चाहिए।'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'hi' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'प्रत्येक फाइल {{size}}MB किंवा त्याहून लहान असावी.'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'mr' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'প্রতিটি ফাইল {{size}} এমবি বা তার কম হতে হবে।'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'bn' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ప్రతి ఫైల్ {{size}}MB లేదా అంతకంటే తక్కువ ఉండాలి.'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'te' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ஒவ்வொரு கோப்பும் {{size}}MB அல்லது அதற்குக் குறைவாக இருக்க வேண்டும்.'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'ta' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'દરેક ફાઈલ {{size}}MB કે તેથી નાની હોવી જોઈએ.'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'gu' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ہر فائل {{size}} ایم بی یا اس سے کم ہونی چاہیے۔'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'ur' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ಪ್ರತಿ ಕಡತ {{size}}MB ಅಥವಾ ಅದಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು.'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'kn' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ഓരോ ഫയലും {{size}}MB അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.'
 WHERE k.CODE = 'intake.attachment_too_large' AND t.LOCALE = 'ml' AND t.VALUE NOT LIKE '%{{size}}%';

-- ── aa.upload.error_file_too_large, nine locales ────────────────────────────────────────────────
UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'प्रत्येक फ़ाइल {{size}} एमबी या उससे कम होनी चाहिए।'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'hi' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'प्रत्येक फाइल {{size}} एमबी किंवा त्यापेक्षा कमी असावी.'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'mr' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'প্রতিটি ফাইল {{size}} এমবি বা তার কম হতে হবে।'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'bn' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ప్రతి ఫైల్ {{size}} ఎంబీ లోపు ఉండాలి.'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'te' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ஒவ்வொரு கோப்பும் {{size}} எம்பி அல்லது குறைவாக இருக்க வேண்டும்.'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'ta' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'દરેક ફાઇલ {{size}} એમબી અથવા તેથી નાની હોવી જોઈએ.'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'gu' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ہر فائل {{size}} ایم بی یا اس سے کم ہونی چاہیے۔'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'ur' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ಪ್ರತಿ ಕಡತವು {{size}} ಎಂಬಿ ಅಥವಾ ಕಡಿಮೆ ಇರಬೇಕು.'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'kn' AND t.VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t JOIN TRANSLATION_KEYS k ON k.ID = t.TRANSLATION_KEY_ID
   SET t.VALUE = 'ഓരോ ഫയലും {{size}} എംബി അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.'
 WHERE k.CODE = 'aa.upload.error_file_too_large' AND t.LOCALE = 'ml' AND t.VALUE NOT LIKE '%{{size}}%';
