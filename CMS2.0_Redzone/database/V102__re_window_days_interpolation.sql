-- V102: make cms.mre.re-window-days the single source of truth for the RE window shown to a citizen
-- MySQL version
--
-- UST11: eligibility.block_less_than_30_days baked the literal "30" into the prose in all ten locales
-- (Bengali in Bengali numerals, ৩০). The wizard enforces cms.mre.re-window-days, which it reads from
-- GET /api/v1/eligibility/questions — so raising that property changed the rule the product ENFORCED
-- while the citizen was still told to wait "30 days". Exactly the defect the clause citations had
-- before V15, and the same fix: the prose carries a {{days}} placeholder the portal interpolates from
-- the served reWindowDays.
--
-- The seeders are insert-if-absent, so rows already present keep the baked digit. This rewrites them.
-- Scoped BY KEY CODE, not by an English phrase: the localized rows are in native scripts.
--
-- Re-running is safe: each REPLACE is a no-op once the literal is gone.

-- The same defect is in the standalone eligibility wizard's filing timeline
-- (wizard.timeline_step2 "Wait Period (30 days)" / _desc "Allow the institution 30 days to respond"),
-- which is the same window quoted in a different surface. Both are corrected together.

-- ═══ Latin-digit forms (en, hi, mr, te, ta, gu, ur, kn, ml) ═══
-- Bounded to these keys, so the bare literal "30" cannot reach any other prose.
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.id = t.translation_key_id
   SET t.`value` = REPLACE(t.`value`, '30', '{{days}}')
 WHERE k.code IN ('eligibility.block_less_than_30_days',
                  'wizard.timeline_step2', 'wizard.timeline_step2_desc');

-- ═══ Bengali numerals: ৩০ is 30 ═══
UPDATE TRANSLATIONS t
   JOIN TRANSLATION_KEYS k ON k.id = t.translation_key_id
   SET t.`value` = REPLACE(t.`value`, '৩০', '{{days}}')
 WHERE k.code IN ('eligibility.block_less_than_30_days',
                  'wizard.timeline_step2', 'wizard.timeline_step2_desc')
   AND t.locale = 'bn';

UPDATE TRANSLATION_KEYS
   SET default_value = REPLACE(default_value, '30', '{{days}}')
 WHERE code IN ('eligibility.block_less_than_30_days',
                'wizard.timeline_step2', 'wizard.timeline_step2_desc');

-- ═══ UST11 scenario 3: the date the window opens ═══
-- A citizen told to wait must be told until WHEN. Added as its own key rather than spliced into the
-- block prose, so it did not require re-translating that message in ten locales.
--
-- English only, deliberately. The nine Indian-language values must be authored by the translation
-- team; seeding a machine guess into a citizen-facing legal notice is worse than the documented
-- English fallback the portal already applies for a missing key.
INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'eligibility.re_window_opens_on', 'eligibility', 'Notice: date the RE window opens',
       'You may file your complaint with the RBI Ombudsman on or after {{date}}.'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE code = 'eligibility.re_window_opens_on');

INSERT INTO TRANSLATIONS (translation_key_id, locale, `value`)
SELECT k.id, 'en', 'You may file your complaint with the RBI Ombudsman on or after {{date}}.'
  FROM TRANSLATION_KEYS k
 WHERE k.code = 'eligibility.re_window_opens_on'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.translation_key_id = k.id AND t.locale = 'en');

-- ═══ Fail-closed notice for the standalone wizard ═══
-- /wizard-check failures used to fall back to a verdict computed in the browser against a 30-day
-- window compiled into the bundle, so the citizen was handed an eligibility determination — including
-- "you are eligible" — that no server ever issued. The wizard now says the check is unavailable.
-- English only, for the same reason as above.
INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'wizard.check_unavailable', 'wizard', 'Eligibility check unavailable',
       'The eligibility check could not be completed because the service is unavailable. Please retry. This check is advisory only — you may still proceed to file your complaint.'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS WHERE code = 'wizard.check_unavailable');

INSERT INTO TRANSLATIONS (translation_key_id, locale, `value`)
SELECT k.id, 'en', 'The eligibility check could not be completed because the service is unavailable. Please retry. This check is advisory only — you may still proceed to file your complaint.'
  FROM TRANSLATION_KEYS k
 WHERE k.code = 'wizard.check_unavailable'
   AND NOT EXISTS (SELECT 1 FROM TRANSLATIONS t
                    WHERE t.translation_key_id = k.id AND t.locale = 'en');
