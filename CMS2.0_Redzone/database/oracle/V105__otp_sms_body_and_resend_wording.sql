-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────
-- V105 — the OTP SMS a citizen actually receives.  Oracle version.
-- (Mirrors database/V108__otp_sms_body_and_resend_wording.sql; the two directories' V-numbers have never
--  been in sync.)
--
-- ── WHAT THIS FIXES ───────────────────────────────────────────────────────────────────────────────────
-- QA states the SMS verbatim:
--
--     "Your OTP for Mobile Number authentication on RBI CMS is 211067. This is valid only for 5 minutes"
--
-- There was NO such message anywhere in the product. CitizenAuthController carried
-- `// TODO: Integrate with actual SMS gateway` and a log line: the OTP was generated, hashed, stored and
-- returned to the caller, and nothing was ever addressed to the citizen's handset. Under dev-local the
-- code comes back in the response body (otp.dev-auto-populate), so the flow LOOKED end-to-end; with the
-- flag off — i.e. every other environment — a citizen could never receive a code at all.
--
-- OtpService now composes the body from this key and hands it to OutboundMessagePort. The key must exist
-- in the database for that to work, and a seeder only runs on a restart and only inserts when the CODE is
-- absent — an already-seeded schema never gets it. Hence this migration.
--
-- ── WHY THE VALIDITY IS A PLACEHOLDER AND NOT "5" ─────────────────────────────────────────────────────
-- The minutes quoted here are CONFIGURABLE: cms.auth.otp.expiry-minutes, overridable at runtime by a
-- SYSTEM_CONFIG row (cms.auth.otp.expiry_minutes). Baking "5 minutes" into the prose would let the
-- ENFORCED window be retuned while the citizen was still promised five — the defect V100 fixed for the RE
-- window and V102 for the filing window. OtpService.renderOtpMessage() interpolates {{minutes}} from
-- OtpService.expiryMinutes(), the same accessor that sets EXPIRES_AT, so the promised and enforced
-- figures cannot diverge.
--
-- ── WHY A PLACEHOLDER IS SAFE HERE ────────────────────────────────────────────────────────────────────
-- TranslationService.translate(key, params?) takes params OPTIONALLY, so a key rendered through the bare
-- `| translate` pipe leaks raw {{braces}}. This key is never touched by the pipe: its only consumer is
-- OtpService.renderOtpMessage(), which supplies BOTH parameters and refuses to dispatch at all if any
-- "{{" survives — an undelivered SMS with a logged error beats one reading "valid only for {{minutes}}".
--
-- ── ENGLISH ONLY, DELIBERATELY ────────────────────────────────────────────────────────────────────────
-- Ten other locales are served and this key gets a row in none of them. Because renderOtpMessage()
-- refuses a template whose placeholders did not resolve, a machine-guessed translation that mangles
-- {{otp}} or {{minutes}} would stop the SMS entirely for that locale. getTranslationsForLocale() falls
-- back to TRANSLATION_KEYS.DEFAULT_VALUE for any locale with no row, so every citizen still gets a code.
-- The Indian-language renderings are for the translation team.
--
-- ── RE-RUNNABLE ───────────────────────────────────────────────────────────────────────────────────────
-- Both statements are guarded on absence (key CODE, then (key, locale)).
-- DEFAULT_VALUE / VALUE are CLOB, hence TO_CLOB on the literals.
-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────

-- ── the key, with the English text as its DEFAULT_VALUE (the cross-locale fallback) ──
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT)
SELECT 'login.otp_sms_body', 'login', 'OTP SMS body sent to the citizen',
       TO_CLOB('Your OTP for Mobile Number authentication on RBI CMS is {{otp}}. This is valid only for {{minutes}} minutes'),
       SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'login.otp_sms_body');

-- ── en ──
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'en',
       TO_CLOB('Your OTP for Mobile Number authentication on RBI CMS is {{otp}}. This is valid only for {{minutes}} minutes'),
       SYSTIMESTAMP
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'login.otp_sms_body'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'en');

COMMIT;
/
