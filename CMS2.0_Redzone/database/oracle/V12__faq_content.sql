-- V12: FAQ content for the public portal (UST118)
-- Oracle version — parity with database/V13__faq_content.sql
--
-- Two jobs:
--
-- 1. Retire the placeholder rows seeded by V7 (Oracle). Those pointed at translation keys
--    ('faq.q1.question' ... 'faq.q12.answer') that were never created in TRANSLATION_KEYS, so on a
--    database where V7 ran the portal rendered the raw key text to citizens. They are deactivated
--    rather than deleted, because an operator may have already attached real content to those ids.
--
-- 2. Retire any FAQ row whose QUESTION_KEY has no TRANSLATION_KEYS entry. This is deliberately
--    broader than the V7 key list: the FAQ table stores keys, not text, so an unresolvable key is
--    always a citizen-visible defect regardless of how the row got there.
--
-- The FAQ rows and their en/hi/mr translations themselves are seeded by FaqSeeder (@Order(8)),
-- which is insert-if-absent on QUESTION_KEY. That seeder is not profile-gated, so it runs in every
-- environment and this migration only has to handle the cleanup that a seeder cannot do.
--
-- IS_ACTIVE is NUMBER(1) in the Oracle DDL (V7), not a BOOLEAN as in MySQL.

UPDATE FAQ
   SET IS_ACTIVE = 0, UPDATED_AT = SYSTIMESTAMP
 WHERE REGEXP_LIKE(QUESTION_KEY, '^faq\.q[0-9]+\.(question|answer)$');

UPDATE FAQ f
   SET f.IS_ACTIVE = 0, f.UPDATED_AT = SYSTIMESTAMP
 WHERE f.IS_ACTIVE = 1
   AND NOT EXISTS (SELECT 1 FROM TRANSLATION_KEYS k WHERE k.CODE = f.QUESTION_KEY);

-- FaqSeeder writes lowercase category codes ('filing', 'eligibility', 'tracking', 'privacy') which
-- the FAQ page resolves to labels via faq.cat_<category>. V7 wrote uppercase codes with no such
-- label key; align any survivors so the filter buttons stay readable.
UPDATE FAQ SET CATEGORY = LOWER(CATEGORY) WHERE CATEGORY <> LOWER(CATEGORY);

COMMIT;
