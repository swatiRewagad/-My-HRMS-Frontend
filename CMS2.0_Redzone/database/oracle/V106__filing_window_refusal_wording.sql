-- V106 - filing-window refusal wording, ten locales.  Oracle version.
-- (MySQL mirror: database/V109__filing_window_refusal_wording.sql)
--
-- The filing window now REFUSES a late complaint instead of warning about it (business ruling), and
-- WHICH window applies depends on whether the Regulated Entity replied:
--
--   RE never replied -> the window runs from the RE complaint date, cms.eligibility.grievance-filing-window-days
--   RE did reply     -> the window runs from the REPLY date, cms.mre.filing-deadline-days
--
-- Two different anchor dates and two different lengths cannot be expressed as one sentence, so
-- eligibility.time_barred_warning is replaced by two keys rather than reworded. The day count stays a
-- {{days}} placeholder for the same reason V107 made it one: both figures are configurable, and the
-- number the citizen is shown is interpolated from the number the server enforces.
--
-- EligibilityTranslationSeeder seeds these, but seedIfAbsent() only helps a database that has not been
-- seeded yet -- an existing cms_db never gains a newly added key. Hence this migration.
--
-- Re-runnable: every statement is guarded on absence of the key CODE, then of the (key, locale) pair, so a
-- second run matches nothing and any wording an operator has since revised is left untouched.

-- ══ eligibility.block_filing_window ══
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.block_filing_window', 'eligibility', 'Block: filing window elapsed (RE never replied)', TO_CLOB('Complaint filing period has expired. A complaint must be filed within {{days}} days of your complaint to the Regulated Entity.'), SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.block_filing_window');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'en', TO_CLOB('Complaint filing period has expired. A complaint must be filed within {{days}} days of your complaint to the Regulated Entity.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'en');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', TO_CLOB('शिकायत दाखिल करने की अवधि समाप्त हो गई है। शिकायत विनियमित संस्था के पास आपकी शिकायत के {{days}} दिनों के भीतर दाखिल की जानी चाहिए।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', TO_CLOB('तक्रार दाखल करण्याची मुदत संपली आहे. तक्रार नियमित संस्थेकडे तुमच्या तक्रारीच्या {{days}} दिवसांच्या आत दाखल केली जाणे आवश्यक आहे.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', TO_CLOB('অভিযোগ দায়ের করার সময়সীমা শেষ হয়ে গেছে। নিয়ন্ত্রিত সংস্থার কাছে আপনার অভিযোগের {{days}} দিনের মধ্যে অভিযোগ দায়ের করতে হবে।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', TO_CLOB('ఫిర్యాదు దాఖలు చేసే గడువు ముగిసింది. నియంత్రిత సంస్థ వద్ద మీ ఫిర్యాదు చేసిన {{days}} రోజులలోపు ఫిర్యాదు దాఖలు చేయాలి.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', TO_CLOB('புகார் அளிக்கும் காலம் முடிந்துவிட்டது. ஒழுங்குமுறை நிறுவனத்தில் உங்கள் புகார் அளித்த {{days}} நாட்களுக்குள் புகார் அளிக்கப்பட வேண்டும்.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', TO_CLOB('ફરિયાદ દાખલ કરવાની મુદત પૂરી થઈ ગઈ છે. નિયંત્રિત સંસ્થા પાસે તમારી ફરિયાદના {{days}} દિવસમાં ફરિયાદ દાખલ કરવી આવશ્યક છે.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', TO_CLOB('شکایت درج کرانے کی مدت گزر چکی ہے۔ شکایت ریگولیٹڈ ادارے میں آپ کی شکایت کے {{days}} دن کے اندر درج کرانی ضروری ہے۔'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', TO_CLOB('ದೂರು ಸಲ್ಲಿಸುವ ಅವಧಿ ಮುಗಿದಿದೆ. ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಲ್ಲಿ ನಿಮ್ಮ ದೂರಿನ {{days}} ದಿನಗಳೊಳಗೆ ದೂರು ಸಲ್ಲಿಸಬೇಕು.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', TO_CLOB('പരാതി സമർപ്പിക്കാനുള്ള കാലാവധി കഴിഞ്ഞു. നിയന്ത്രിത സ്ഥാപനത്തിൽ നിങ്ങളുടെ പരാതി നൽകി {{days}} ദിവസത്തിനുള്ളിൽ പരാതി സമർപ്പിക്കണം.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_filing_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

-- ══ eligibility.block_post_reply_window ══
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'eligibility.block_post_reply_window', 'eligibility', 'Block: filing window elapsed (after RE reply)', TO_CLOB('Complaint filing period has expired. A complaint must be filed within {{days}} days of the Regulated Entity''s reply.'), SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'eligibility.block_post_reply_window');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'en', TO_CLOB('Complaint filing period has expired. A complaint must be filed within {{days}} days of the Regulated Entity''s reply.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'en');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'hi', TO_CLOB('शिकायत दाखिल करने की अवधि समाप्त हो गई है। शिकायत विनियमित संस्था के उत्तर के {{days}} दिनों के भीतर दाखिल की जानी चाहिए।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'hi');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'mr', TO_CLOB('तक्रार दाखल करण्याची मुदत संपली आहे. तक्रार नियमित संस्थेच्या उत्तराच्या {{days}} दिवसांच्या आत दाखल केली जाणे आवश्यक आहे.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'mr');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'bn', TO_CLOB('অভিযোগ দায়ের করার সময়সীমা শেষ হয়ে গেছে। নিয়ন্ত্রিত সংস্থার উত্তরের {{days}} দিনের মধ্যে অভিযোগ দায়ের করতে হবে।'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'bn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'te', TO_CLOB('ఫిర్యాదు దాఖలు చేసే గడువు ముగిసింది. నియంత్రిత సంస్థ సమాధానం ఇచ్చిన {{days}} రోజులలోపు ఫిర్యాదు దాఖలు చేయాలి.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'te');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ta', TO_CLOB('புகார் அளிக்கும் காலம் முடிந்துவிட்டது. ஒழுங்குமுறை நிறுவனத்தின் பதிலுக்குப் பிறகு {{days}} நாட்களுக்குள் புகார் அளிக்கப்பட வேண்டும்.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ta');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'gu', TO_CLOB('ફરિયાદ દાખલ કરવાની મુદત પૂરી થઈ ગઈ છે. નિયંત્રિત સંસ્થાના જવાબના {{days}} દિવસમાં ફરિયાદ દાખલ કરવી આવશ્યક છે.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'gu');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ur', TO_CLOB('شکایت درج کرانے کی مدت گزر چکی ہے۔ شکایت ریگولیٹڈ ادارے کے جواب کے {{days}} دن کے اندر درج کرانی ضروری ہے۔'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ur');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'kn', TO_CLOB('ದೂರು ಸಲ್ಲಿಸುವ ಅವಧಿ ಮುಗಿದಿದೆ. ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಉತ್ತರದ {{days}} ದಿನಗಳೊಳಗೆ ದೂರು ಸಲ್ಲಿಸಬೇಕು.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'kn');
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'ml', TO_CLOB('പരാതി സമർപ്പിക്കാനുള്ള കാലാവധി കഴിഞ്ഞു. നിയന്ത്രിത സ്ഥാപനത്തിന്റെ മറുപടിക്ക് ശേഷം {{days}} ദിവസത്തിനുള്ളിൽ പരാതി സമർപ്പിക്കണം.'), SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'eligibility.block_post_reply_window'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'ml');

COMMIT;
/
