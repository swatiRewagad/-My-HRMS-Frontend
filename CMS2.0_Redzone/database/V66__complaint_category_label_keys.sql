-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- V66 — Give COMPLAINT_CATEGORIES a LABEL_KEY so the category list can be localised.
--
-- WHY. COMPLAINT_CATEGORIES is the authoritative grievance-category master (ten active rows, seeded
-- 2026-06-26) and it has a NAME column and nothing else. NAME is the value submitted on a complaint,
-- stored, and matched by routing, so it cannot itself be translated without changing what is recorded.
-- The result was that the first substantive choice a complainant makes was English-only in an
-- eleven-locale product. LABEL_KEY localises what is DISPLAYED and leaves NAME alone.
--
-- The keys are seeded by ComplaintCategoryTranslationSeeder in all eleven locales. Their nine
-- non-English strings are the ones already used by aa.ground.*, which names these same ten categories
-- as the grounds of an appeal — one vocabulary, so a category is not worded one way when a citizen
-- files and another way when they appeal. Punjabi is added there for the first time; aa.ground.*
-- predates pa being served and has no pa row.
--
-- NULL IS A VALID VALUE. A category an operator adds later has no key, and the API falls back to NAME,
-- so it renders in English rather than as a raw key. This is deliberately not NOT NULL.
--
-- Re-runnable: the column add is guarded by an information_schema check (MySQL cannot do ADD COLUMN IF
-- NOT EXISTS), and each UPDATE is scoped BY NAME and only fills a LABEL_KEY that is still NULL, so a
-- value corrected by hand survives a replay.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'complaint_categories'
       AND COLUMN_NAME = 'LABEL_KEY'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE complaint_categories ADD COLUMN LABEL_KEY VARCHAR(100) NULL',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Scoped on the English NAME, which is the stable business key here: these ten rows are referenced by
-- name from the complaint payload, so the name is exactly what cannot drift.
UPDATE complaint_categories SET LABEL_KEY = 'category.atm_debit_card'      WHERE name = 'ATM / Debit Card'      AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.credit_card'         WHERE name = 'Credit Card'           AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.internet_banking'    WHERE name = 'Internet Banking'      AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.mobile_banking_upi'  WHERE name = 'Mobile Banking / UPI'  AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.loan_advances'       WHERE name = 'Loan / Advances'       AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.deposit_accounts'    WHERE name = 'Deposit Accounts'      AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.pension'             WHERE name = 'Pension'               AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.remittance_transfer' WHERE name = 'Remittance / Transfer' AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.insurance'           WHERE name = 'Insurance'             AND LABEL_KEY IS NULL;
UPDATE complaint_categories SET LABEL_KEY = 'category.others'              WHERE name = 'Others'                AND LABEL_KEY IS NULL;
