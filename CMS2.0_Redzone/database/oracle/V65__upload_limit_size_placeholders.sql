-- ============================================================
-- V65 — Replace the BAKED file-size digit in the two upload error messages with a {{size}} placeholder.
-- Oracle counterpart of MySQL V67. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- The rationale is recorded in full in the MySQL counterpart (database/V67). In brief: both seeders are
-- insert-if-absent BY KEY CODE, so correcting the Java default does NOT rewrite a row already committed
-- to a database -- a migration is the only thing that fixes an existing environment.
--
-- intake.attachment_too_large said "2MB" in English and nine translations while the server enforces 5 MB.
-- aa.upload.error_file_too_large had English corrected to "5 MB" but left all nine translations at 2, so
-- the limit a citizen was told depended on their language -- invisible to anyone testing in English.
--
-- A baked digit cannot track cms.upload.max_file_size. {{size}} is interpolated at render time.
--
-- Re-runnable: every UPDATE is scoped BY KEY CODE and requires the value to still LACK the placeholder,
-- so a second run matches nothing and a string since reworded by hand is left alone. Scoping on the
-- absence of {{size}} rather than on the digit also catches Bengali and Urdu, which spelled the numeral
-- in their own scripts and would have been missed by a search for '2'.
-- ============================================================

UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'Each file must be {{size}}MB or smaller.'
 WHERE CODE = 'intake.attachment_too_large'
   AND DEFAULT_VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATION_KEYS
   SET DEFAULT_VALUE = 'Each file must be {{size}} MB or smaller.'
 WHERE CODE = 'aa.upload.error_file_too_large'
   AND DEFAULT_VALUE NOT LIKE '%{{size}}%';

UPDATE TRANSLATIONS t
   SET t.VALUE = 'प्रत्येक फ़ाइल {{size}}MB या उससे छोटी होनी चाहिए।'
 WHERE t.LOCALE = 'hi'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'प्रत्येक फाइल {{size}}MB किंवा त्याहून लहान असावी.'
 WHERE t.LOCALE = 'mr'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'প্রতিটি ফাইল {{size}} এমবি বা তার কম হতে হবে।'
 WHERE t.LOCALE = 'bn'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ప్రతి ఫైల్ {{size}}MB లేదా అంతకంటే తక్కువ ఉండాలి.'
 WHERE t.LOCALE = 'te'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ஒவ்வொரு கோப்பும் {{size}}MB அல்லது அதற்குக் குறைவாக இருக்க வேண்டும்.'
 WHERE t.LOCALE = 'ta'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'દરેક ફાઈલ {{size}}MB કે તેથી નાની હોવી જોઈએ.'
 WHERE t.LOCALE = 'gu'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ہر فائل {{size}} ایم بی یا اس سے کم ہونی چاہیے۔'
 WHERE t.LOCALE = 'ur'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ಪ್ರತಿ ಕಡತ {{size}}MB ಅಥವಾ ಅದಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು.'
 WHERE t.LOCALE = 'kn'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ഓരോ ഫയലും {{size}}MB അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.'
 WHERE t.LOCALE = 'ml'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'intake.attachment_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'प्रत्येक फ़ाइल {{size}} एमबी या उससे कम होनी चाहिए।'
 WHERE t.LOCALE = 'hi'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'प्रत्येक फाइल {{size}} एमबी किंवा त्यापेक्षा कमी असावी.'
 WHERE t.LOCALE = 'mr'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'প্রতিটি ফাইল {{size}} এমবি বা তার কম হতে হবে।'
 WHERE t.LOCALE = 'bn'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ప్రతి ఫైల్ {{size}} ఎంబీ లోపు ఉండాలి.'
 WHERE t.LOCALE = 'te'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ஒவ்வொரு கோப்பும் {{size}} எம்பி அல்லது குறைவாக இருக்க வேண்டும்.'
 WHERE t.LOCALE = 'ta'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'દરેક ફાઇલ {{size}} એમબી અથવા તેથી નાની હોવી જોઈએ.'
 WHERE t.LOCALE = 'gu'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ہر فائل {{size}} ایم بی یا اس سے کم ہونی چاہیے۔'
 WHERE t.LOCALE = 'ur'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ಪ್ರತಿ ಕಡತವು {{size}} ಎಂಬಿ ಅಥವಾ ಕಡಿಮೆ ಇರಬೇಕು.'
 WHERE t.LOCALE = 'kn'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

UPDATE TRANSLATIONS t
   SET t.VALUE = 'ഓരോ ഫയലും {{size}} എംബി അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.'
 WHERE t.LOCALE = 'ml'
   AND t.VALUE NOT LIKE '%{{size}}%'
   AND EXISTS (SELECT 1 FROM TRANSLATION_KEYS k
                WHERE k.ID = t.TRANSLATION_KEY_ID AND k.CODE = 'aa.upload.error_file_too_large');

COMMIT;
