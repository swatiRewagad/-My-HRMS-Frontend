-- ============================================================
-- V64 — Give COMPLAINT_CATEGORIES a LABEL_KEY so the category list can be localised.
-- Oracle counterpart of MySQL V66. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- The rationale is recorded in full in the MySQL counterpart (database/V66). In brief: NAME is the
-- value submitted on a complaint, stored and matched by routing, so it cannot be translated without
-- changing what is recorded. LABEL_KEY localises what is DISPLAYED. The keys are seeded in all eleven
-- locales by ComplaintCategoryTranslationSeeder, reusing the aa.ground.* strings so a category is not
-- worded one way when a citizen files and another way when they appeal.
--
-- NULL is a valid value: a category added later has no key and the API falls back to NAME, rendering
-- English rather than a raw key. Deliberately not NOT NULL.
--
-- Re-runnable: the column add is guarded by a USER_TAB_COLS count, and each UPDATE is scoped BY NAME
-- and only fills a LABEL_KEY that is still NULL, so a value corrected by hand survives a replay.
-- ============================================================

DECLARE
    v_count NUMBER;

    PROCEDURE set_label_key(p_name VARCHAR2, p_key VARCHAR2) IS
    BEGIN
        UPDATE COMPLAINT_CATEGORIES
           SET LABEL_KEY = p_key
         WHERE NAME = p_name
           AND LABEL_KEY IS NULL;
    END;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLS
     WHERE TABLE_NAME = 'COMPLAINT_CATEGORIES' AND COLUMN_NAME = 'LABEL_KEY';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINT_CATEGORIES ADD (LABEL_KEY VARCHAR2(100))';
    END IF;

    set_label_key('ATM / Debit Card',      'category.atm_debit_card');
    set_label_key('Credit Card',           'category.credit_card');
    set_label_key('Internet Banking',      'category.internet_banking');
    set_label_key('Mobile Banking / UPI',  'category.mobile_banking_upi');
    set_label_key('Loan / Advances',       'category.loan_advances');
    set_label_key('Deposit Accounts',      'category.deposit_accounts');
    set_label_key('Pension',               'category.pension');
    set_label_key('Remittance / Transfer', 'category.remittance_transfer');
    set_label_key('Insurance',             'category.insurance');
    set_label_key('Others',                'category.others');

    COMMIT;
END;
/
