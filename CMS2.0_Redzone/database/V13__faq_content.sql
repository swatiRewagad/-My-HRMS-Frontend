-- V13: FAQ content for the public portal (UST118)
-- MySQL version
--
-- Two jobs:
--
-- 1. Retire the placeholder rows seeded by V8. Those pointed at translation keys
--    ('faq.q1.question' ... 'faq.q12.answer') that were never created in TRANSLATION_KEYS, so on a
--    database where V8 ran the portal rendered the raw key text to citizens. They are deactivated
--    rather than deleted, because an operator may have already attached real content to those ids.
--
-- 2. Retire any FAQ row whose question_key has no TRANSLATION_KEYS entry. This is deliberately
--    broader than the V8 key list: the FAQ table stores keys, not text, so an unresolvable key is
--    always a citizen-visible defect regardless of how the row got there.
--
-- The FAQ rows and their en/hi/mr translations themselves are seeded by FaqSeeder (@Order(8)),
-- which is insert-if-absent on question_key. That seeder is not profile-gated, so it runs in every
-- environment and this migration only has to handle the cleanup that a seeder cannot do.

UPDATE FAQ
   SET is_active = FALSE, updated_at = NOW()
 WHERE question_key REGEXP '^faq\\.q[0-9]+\\.(question|answer)$';

UPDATE FAQ f
   SET f.is_active = FALSE, f.updated_at = NOW()
 WHERE f.is_active = TRUE
   AND NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS k WHERE k.code = f.question_key);

-- FaqSeeder writes lowercase category codes ('filing', 'eligibility', 'tracking', 'privacy') which
-- the FAQ page resolves to labels via faq.cat_<category>. V8 wrote uppercase codes with no such
-- label key; align any survivors so the filter buttons stay readable.
UPDATE FAQ SET category = LOWER(category) WHERE category <> LOWER(category);
