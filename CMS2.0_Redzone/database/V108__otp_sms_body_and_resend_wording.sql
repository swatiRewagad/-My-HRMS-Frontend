-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────
-- V108 — the OTP SMS a citizen actually receives.  MySQL version.
-- (Oracle mirror: database/oracle/V105__otp_sms_body_and_resend_wording.sql — the two directories'
--  V-numbers have never been in sync.)
--
-- V104..V107 were taken by parallel sessions, hence 108.
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
-- in the database for that to work: Phase2WizardTranslationSeeder seeds it, but seedIfAbsent() returns
-- early when the CODE already exists, and more to the point a seeder only runs on a restart — an
-- already-seeded cms_db (which has all 34 other login.* keys and none of this one) never gets it. Hence
-- this migration.
--
-- ── WHY THE VALIDITY IS A PLACEHOLDER AND NOT "5" ─────────────────────────────────────────────────────
-- The number of minutes quoted here is CONFIGURABLE: cms.auth.otp.expiry-minutes in application.yml,
-- overridable at runtime by a SYSTEM_CONFIG row (cms.auth.otp.expiry_minutes), and dev-local already
-- runs it at 10, not 5. Baking "5 minutes" into the prose would let the ENFORCED window be retuned while
-- the citizen was still promised five — the exact defect V102 fixed for the RE window, V103 for the
-- upload limits and V107 for the filing window. OtpService.renderOtpMessage() interpolates {{minutes}}
-- from OtpService.expiryMinutes(), the same accessor generateOtp() uses to set EXPIRES_AT, so the figure
-- the citizen is told and the figure the server enforces cannot diverge.
--
-- ── WHY A PLACEHOLDER IS SAFE HERE, WHEN V106 SAID IT WOULD NOT BE ────────────────────────────────────
-- TranslationService.translate(key, params?) takes params OPTIONALLY, so a key rendered through the bare
-- `| translate` pipe leaks raw {{braces}} to the citizen. This key is never touched by the pipe: its only
-- consumer is OtpService.renderOtpMessage(), which supplies BOTH parameters and then refuses to dispatch
-- at all if any "{{" survives interpolation — an undelivered SMS with a logged error beats a delivered
-- one reading "valid only for {{minutes}} minutes".
--
-- ── ENGLISH ONLY, DELIBERATELY ────────────────────────────────────────────────────────────────────────
-- Ten other locales are served (bn gu hi kn ml mr pa ta te ur) and this key gets a row in NONE of them.
-- That is a decision, not an omission: because renderOtpMessage() refuses to send a template whose
-- placeholders did not resolve, a machine-guessed translation that mangles {{otp}} or {{minutes}} would
-- stop the SMS going out entirely for that locale. TranslationService.getTranslationsForLocale() falls
-- back to TRANSLATION_KEYS.DEFAULT_VALUE via putIfAbsent for any locale with no row, so every locale
-- resolves to the English text below and every citizen gets a code. The nine Indian-language renderings
-- are for the translation team, and until they land the fallback is documented rather than invented.
--
-- ── RE-RUNNABLE ───────────────────────────────────────────────────────────────────────────────────────
-- Both statements are guarded on absence (key CODE, then (key, locale)), so a second run matches nothing
-- and any wording an operator has since revised is left alone.
-- ────────────────────────────────────────────────────────────────────────────────────────────────────────────

-- ── the key, with the English text as its DEFAULT_VALUE (the cross-locale fallback) ──
INSERT INTO TRANSLATION_KEYS (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE, CREATED_AT, UPDATED_AT)
SELECT 'login.otp_sms_body',
       'login',
       'OTP SMS body sent to the citizen',
       'Your OTP for Mobile Number authentication on RBI CMS is {{otp}}. This is valid only for {{minutes}} minutes',
       NOW(), NOW()
  FROM (SELECT 1) d
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE CODE = 'login.otp_sms_body');

-- ── en ──
INSERT INTO TRANSLATIONS (TRANSLATION_KEY_ID, LOCALE, VALUE, UPDATED_AT)
SELECT k.ID, 'en',
       'Your OTP for Mobile Number authentication on RBI CMS is {{otp}}. This is valid only for {{minutes}} minutes',
       NOW()
  FROM TRANSLATION_KEYS k
 WHERE k.CODE = 'login.otp_sms_body'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.TRANSLATION_KEY_ID = k.ID AND t.LOCALE = 'en');
