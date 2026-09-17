-- V37: AA parent-search and Register-milestone translations (session S2A)
-- MySQL version. Oracle twin: database/oracle/V35__aa_register_translations.sql
--
-- WHY THIS FILE EXISTS: AaRegisterTranslationSeeder (@Order(12)) only runs on a boot against a database
-- that does not yet hold these keys. Oracle/SIT is provisioned by migration, not by a Spring boot run,
-- so without this file the whole AA search and Register UI would render English keys in all ten
-- locales there. Generated from the seeder so the two cannot drift.
--
-- Every statement is INSERT-IF-ABSENT and scoped BY KEY CODE. Never match on an English phrase: the
-- localized rows are in native scripts, so an English substring matches none of them.
--
-- Bengali numerals are intentional in the upload-limit messages (২ MB, ২৫ MB, ১০ files). An ASCII digit
-- replacement would silently skip them.

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.heading', 'aa', 'Search Parent Complaint', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.heading');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.complaint_number', 'aa', 'Complaint Number', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.complaint_number');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.appellant_name', 'aa', 'Appellant Name', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.appellant_name');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.appellant_mobile', 'aa', 'Mobile Number', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.appellant_mobile');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.appellant_email', 'aa', 'Email Address', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.appellant_email');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.rbio_office', 'aa', 'RBIO Office', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.rbio_office');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.closure_clause', 'aa', 'Closure Clause', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.closure_clause');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.category', 'aa', 'Category', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.category');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.ground_of_complaint', 'aa', 'Ground of Complaint', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.ground_of_complaint');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.button_search', 'aa', 'Search', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.button_search');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.button_reset', 'aa', 'Reset', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.button_reset');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.no_results', 'aa', 'No complaints match your search.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.no_results');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.error_no_filter', 'aa', 'Enter at least one search criterion.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.error_no_filter');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.error_term_too_short', 'aa', 'Enter at least two characters to search by name.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.error_term_too_short');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.error_failed', 'aa', 'The search could not be completed. Please try again.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.error_failed');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.loading', 'aa', 'Searching…', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.loading');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.results_count', 'aa', '{{count}} complaint(s) found', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.results_count');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.col_status', 'aa', 'Status', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.col_status');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.col_office', 'aa', 'Office', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.col_office');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.col_closed_on', 'aa', 'Closed On', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.col_closed_on');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.select_option_all', 'aa', 'All', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.select_option_all');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.search.no_office_recorded', 'aa', 'No office recorded', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.search.no_office_recorded');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.heading', 'aa', 'Register Appeal / Representation', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.heading');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.milestone_heading', 'aa', 'Registration Details', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.milestone_heading');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appeal_filed_by', 'aa', 'Appeal Filed By', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appeal_filed_by');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.source_of_appeal', 'aa', 'Source of Appeal', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.source_of_appeal');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.mode_of_receipt', 'aa', 'Mode of Receipt', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.mode_of_receipt');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appeal_ground', 'aa', 'Ground of Appeal', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appeal_ground');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.relief_sought', 'aa', 'Relief Sought', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.relief_sought');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.complainant_heading', 'aa', 'Complainant Details', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.complainant_heading');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.entity_heading', 'aa', 'Regulated Entity Details', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.entity_heading');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.is_complainant_advocate', 'aa', 'Is the complainant an advocate?', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.is_complainant_advocate');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.has_related_court_trial', 'aa', 'Are there any related court trials?', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.has_related_court_trial');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.prompt_log_legal_case', 'aa', 'Please log this case in the Legal Cases module.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.prompt_log_legal_case');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.ed_approval_given', 'aa', 'ED / equal-rank approval obtained?', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.ed_approval_given');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.ed_approval_date', 'aa', 'Date of Approval', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.ed_approval_date');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.ed_approval_comments', 'aa', 'Approval Comments', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.ed_approval_comments');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.ed_approval_document', 'aa', 'Approval Document', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.ed_approval_document');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.button_save_proceed', 'aa', 'Save & Proceed', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.button_save_proceed');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.create_appeal', 'aa', 'Create Appeal', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.create_appeal');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.create_representation', 'aa', 'Create Representation', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.create_representation');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.success', 'aa', 'The appeal has been registered.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.success');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.error_mandatory_incomplete', 'aa', 'Please complete all mandatory fields before proceeding.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.error_mandatory_incomplete');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.error_parent_not_appealable', 'aa', 'Only a closed or reopened complaint can be appealed.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.error_parent_not_appealable');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.error_not_permitted', 'aa', 'You are not permitted to register an appeal against this complaint.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.error_not_permitted');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.unresolved_no_source', 'aa', 'Not available from the complaint record — please enter this value.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.unresolved_no_source');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.unresolved_not_in_master', 'aa', 'Not found in the master data — please enter this value.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.unresolved_not_in_master');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.field_required', 'aa', 'This field is required.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.field_required');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.yes', 'aa', 'Yes', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.yes');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.no', 'aa', 'No', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.no');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.atm_debit_card', 'aa', 'ATM / Debit Card', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.atm_debit_card');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.credit_card', 'aa', 'Credit Card', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.credit_card');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.internet_banking', 'aa', 'Internet Banking', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.internet_banking');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.mobile_banking_upi', 'aa', 'Mobile Banking / UPI', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.mobile_banking_upi');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.loan_advances', 'aa', 'Loan / Advances', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.loan_advances');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.deposit_accounts', 'aa', 'Deposit Accounts', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.deposit_accounts');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.pension', 'aa', 'Pension', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.pension');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.remittance_transfer', 'aa', 'Remittance / Transfer', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.remittance_transfer');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.insurance', 'aa', 'Insurance', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.insurance');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.ground.others', 'aa', 'Others', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.ground.others');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.upload.error_file_too_large', 'aa', 'Each file must be 2 MB or smaller.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.upload.error_file_too_large');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.upload.error_total_too_large', 'aa', 'All attachments together must be 25 MB or smaller.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.upload.error_total_too_large');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.upload.error_too_many_files', 'aa', 'You may attach at most 10 files.', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.upload.error_too_many_files');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.button_retry', 'aa', 'Retry', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.button_retry');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appellant_city', 'aa', 'City', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appellant_city');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appellant_country', 'aa', 'Country', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appellant_country');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appellant_pincode', 'aa', 'Pincode', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appellant_pincode');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appellant_district', 'aa', 'District', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appellant_district');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appellant_state', 'aa', 'State', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appellant_state');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appellant_address1', 'aa', 'Address Line 1', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appellant_address1');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.appellant_address2', 'aa', 'Address Line 2', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.appellant_address2');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.entity_name', 'aa', 'Entity Name', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.entity_name');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.entity_region', 'aa', 'Entity Region', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.entity_region');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.entity_category', 'aa', 'Entity Category', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.entity_category');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.entity_branch', 'aa', 'Branch', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.entity_branch');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.bsr_ifsc_code', 'aa', 'BSR / IFSC Code', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.bsr_ifsc_code');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.account_number', 'aa', 'Account Number', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.account_number');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.card_number', 'aa', 'Card Number', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.card_number');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.nodal_officer_name', 'aa', 'Nodal Officer', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.nodal_officer_name');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.reason_for_delay', 'aa', 'Reason for Delay', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.reason_for_delay');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.declarations_heading', 'aa', 'Declarations', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.declarations_heading');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.parent_complaint', 'aa', 'Parent Complaint', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.parent_complaint');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.closure_clause', 'aa', 'Closure Clause', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.closure_clause');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.filed_by_complainant', 'aa', 'Complainant', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.filed_by_complainant');

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.register.filed_by_entity', 'aa', 'Regulated Entity', NOW(6), NOW(6) FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.register.filed_by_entity');


-- ── hindi (hi) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'मूल शिकायत खोजें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'शिकायत संख्या', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अपीलकर्ता का नाम', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'मोबाइल नंबर', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'ईमेल पता', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'आरबीआईओ कार्यालय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'समापन खंड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'श्रेणी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'शिकायत का आधार', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'खोजें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'रीसेट करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'आपकी खोज से कोई शिकायत मेल नहीं खाती।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'कम से कम एक खोज मानदंड दर्ज करें।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'नाम से खोजने के लिए कम से कम दो अक्षर दर्ज करें।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'खोज पूरी नहीं हो सकी। कृपया पुनः प्रयास करें।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'खोज रहे हैं…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', '{{count}} शिकायतें मिलीं', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'स्थिति', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'कार्यालय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'बंद होने की तिथि', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'सभी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'कोई कार्यालय दर्ज नहीं', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अपील / अभ्यावेदन पंजीकृत करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'पंजीकरण विवरण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अपील दायर करने वाला', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अपील का स्रोत', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'प्राप्ति का माध्यम', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अपील का आधार', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'वांछित राहत', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'शिकायतकर्ता का विवरण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'विनियमित संस्था का विवरण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'क्या शिकायतकर्ता अधिवक्ता है?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'क्या कोई संबंधित न्यायालय मुकदमा है?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'कृपया इस मामले को कानूनी मामले मॉड्यूल में दर्ज करें।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'ईडी / समकक्ष रैंक की स्वीकृति प्राप्त हुई?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'स्वीकृति की तिथि', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'स्वीकृति टिप्पणियाँ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'स्वीकृति दस्तावेज़', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'सहेजें और आगे बढ़ें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अपील बनाएँ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अभ्यावेदन बनाएँ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अपील पंजीकृत कर दी गई है।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'आगे बढ़ने से पहले सभी अनिवार्य फ़ील्ड भरें।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'केवल बंद या पुनः खोली गई शिकायत पर ही अपील की जा सकती है।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'आपको इस शिकायत के विरुद्ध अपील पंजीकृत करने की अनुमति नहीं है।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'शिकायत रिकॉर्ड से उपलब्ध नहीं — कृपया यह मान दर्ज करें।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'मास्टर डेटा में नहीं मिला — कृपया यह मान दर्ज करें।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'यह फ़ील्ड आवश्यक है।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'हाँ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'नहीं', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'एटीएम / डेबिट कार्ड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'क्रेडिट कार्ड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'इंटरनेट बैंकिंग', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'मोबाइल बैंकिंग / यूपीआई', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'ऋण / अग्रिम', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'जमा खाते', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'पेंशन', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'प्रेषण / अंतरण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'बीमा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'अन्य', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'प्रत्येक फ़ाइल 2 एमबी या उससे कम होनी चाहिए।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'सभी अनुलग्नक मिलाकर 25 एमबी या उससे कम होने चाहिए।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'आप अधिकतम 10 फ़ाइलें संलग्न कर सकते हैं।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'पुनः प्रयास करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'शहर', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'देश', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'पिन कोड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'जिला', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'राज्य', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'पता पंक्ति 1', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'पता पंक्ति 2', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'संस्था का नाम', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'संस्था क्षेत्र', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'संस्था श्रेणी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'शाखा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'बीएसआर / आईएफएससी कोड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'खाता संख्या', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'कार्ड संख्या', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'नोडल अधिकारी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'विलंब का कारण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'घोषणाएँ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'मूल शिकायत', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'समापन खंड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'शिकायतकर्ता', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'hi', 'विनियमित संस्था', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');


-- ── marathi (mr) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मूळ तक्रार शोधा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'तक्रार क्रमांक', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'अपीलकर्त्याचे नाव', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मोबाइल क्रमांक', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'ईमेल पत्ता', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'आरबीआयओ कार्यालय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'समाप्ती कलम', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'श्रेणी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'तक्रारीचा आधार', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'शोधा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'पुन्हा सेट करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'तुमच्या शोधाशी कोणतीही तक्रार जुळत नाही.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'कमीत कमी एक शोध निकष प्रविष्ट करा.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'नावाने शोधण्यासाठी कमीत कमी दोन अक्षरे प्रविष्ट करा.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'शोध पूर्ण होऊ शकला नाही. कृपया पुन्हा प्रयत्न करा.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'शोधत आहे…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', '{{count}} तक्रारी आढळल्या', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'स्थिती', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'कार्यालय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'बंद केल्याची तारीख', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'सर्व', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'कोणतेही कार्यालय नोंदवलेले नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'अपील / निवेदन नोंदवा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'नोंदणी तपशील', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'अपील दाखल करणारा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'अपिलाचा स्रोत', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'प्राप्तीचा मार्ग', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'अपिलाचा आधार', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मागितलेली सुटका', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'तक्रारदाराचे तपशील', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'नियंत्रित संस्थेचे तपशील', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'तक्रारदार वकील आहे का?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'संबंधित न्यायालयीन खटले आहेत का?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'कृपया हे प्रकरण कायदेशीर प्रकरण मॉड्यूलमध्ये नोंदवा.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'ईडी / समान दर्जाची मान्यता मिळाली?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मान्यतेची तारीख', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मान्यता टिप्पण्या', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मान्यता दस्तऐवज', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'जतन करा आणि पुढे जा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'अपील तयार करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'निवेदन तयार करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'अपील नोंदवले गेले आहे.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'पुढे जाण्यापूर्वी सर्व अनिवार्य फील्ड पूर्ण करा.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'केवळ बंद केलेल्या किंवा पुन्हा उघडलेल्या तक्रारीवर अपील करता येते.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'या तक्रारीविरुद्ध अपील नोंदवण्याची तुम्हाला परवानगी नाही.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'तक्रार नोंदीतून उपलब्ध नाही — कृपया हे मूल्य प्रविष्ट करा.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मास्टर डेटामध्ये आढळले नाही — कृपया हे मूल्य प्रविष्ट करा.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'हे फील्ड आवश्यक आहे.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'होय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'एटीएम / डेबिट कार्ड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'क्रेडिट कार्ड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'इंटरनेट बँकिंग', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मोबाइल बँकिंग / यूपीआय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'कर्ज / अग्रिम', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'ठेव खाती', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'निवृत्तिवेतन', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'पैसे पाठवणे / हस्तांतरण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'विमा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'इतर', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'प्रत्येक फाइल 2 एमबी किंवा त्यापेक्षा कमी असावी.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'सर्व संलग्नके मिळून 25 एमबी किंवा त्यापेक्षा कमी असावीत.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'तुम्ही जास्तीत जास्त 10 फाइल्स जोडू शकता.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'पुन्हा प्रयत्न करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'शहर', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'देश', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'पिन कोड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'जिल्हा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'राज्य', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'पत्ता ओळ 1', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'पत्ता ओळ 2', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'संस्थेचे नाव', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'संस्था प्रदेश', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'संस्था श्रेणी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'शाखा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'बीएसआर / आयएफएससी कोड', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'खाते क्रमांक', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'कार्ड क्रमांक', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'नोडल अधिकारी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'विलंबाचे कारण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'घोषणा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'मूळ तक्रार', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'समाप्ती कलम', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'तक्रारदार', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'mr', 'नियंत्रित संस्था', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');


-- ── bengali (bn) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'মূল অভিযোগ খুঁজুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অভিযোগ নম্বর', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপিলকারীর নাম', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'মোবাইল নম্বর', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'ইমেল ঠিকানা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আরবিআইও কার্যালয়', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'নিষ্পত্তি ধারা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'শ্রেণি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অভিযোগের ভিত্তি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'খুঁজুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'পুনঃনির্ধারণ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপনার অনুসন্ধানের সঙ্গে কোনো অভিযোগ মেলেনি।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অন্তত একটি অনুসন্ধান শর্ত লিখুন।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'নাম দিয়ে খুঁজতে অন্তত দুটি অক্ষর লিখুন।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অনুসন্ধান সম্পূর্ণ করা যায়নি। অনুগ্রহ করে আবার চেষ্টা করুন।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'খোঁজা হচ্ছে…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', '{{count}}টি অভিযোগ পাওয়া গেছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অবস্থা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'কার্যালয়', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'নিষ্পত্তির তারিখ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'সব', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'কোনো কার্যালয় নথিভুক্ত নেই', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপিল / প্রতিবেদন নিবন্ধন করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'নিবন্ধনের বিবরণ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপিল দাখিলকারী', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপিলের উৎস', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'প্রাপ্তির মাধ্যম', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপিলের ভিত্তি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'প্রার্থিত প্রতিকার', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অভিযোগকারীর বিবরণ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'নিয়ন্ত্রিত সংস্থার বিবরণ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অভিযোগকারী কি একজন আইনজীবী?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'সম্পর্কিত কোনো আদালতের বিচার আছে কি?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অনুগ্রহ করে এই মামলাটি আইনি মামলা মডিউলে নথিভুক্ত করুন।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'ইডি / সমপদস্থের অনুমোদন পাওয়া গেছে?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অনুমোদনের তারিখ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অনুমোদনের মন্তব্য', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অনুমোদনের নথি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'সংরক্ষণ করে এগিয়ে যান', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপিল তৈরি করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'প্রতিবেদন তৈরি করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপিলটি নিবন্ধিত হয়েছে।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'এগিয়ে যাওয়ার আগে সব আবশ্যক ক্ষেত্র পূরণ করুন।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'কেবল নিষ্পত্তি হওয়া বা পুনরায় খোলা অভিযোগের বিরুদ্ধেই আপিল করা যায়।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'এই অভিযোগের বিরুদ্ধে আপিল নিবন্ধন করার অনুমতি আপনার নেই।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অভিযোগের নথি থেকে পাওয়া যায়নি — অনুগ্রহ করে এই মানটি লিখুন।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'মাস্টার ডেটায় পাওয়া যায়নি — অনুগ্রহ করে এই মানটি লিখুন।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'এই ক্ষেত্রটি আবশ্যক।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'হ্যাঁ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'না', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'এটিএম / ডেবিট কার্ড', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'ক্রেডিট কার্ড', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'ইন্টারনেট ব্যাঙ্কিং', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'মোবাইল ব্যাঙ্কিং / ইউপিআই', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'ঋণ / অগ্রিম', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আমানত হিসাব', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'পেনশন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'প্রেরণ / স্থানান্তর', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'বিমা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অন্যান্য', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'প্রতিটি ফাইল ২ এমবি বা তার কম হতে হবে।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'সব সংযুক্তি একসঙ্গে ২৫ এমবি বা তার কম হতে হবে।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আপনি সর্বাধিক ১০টি ফাইল সংযুক্ত করতে পারেন।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'আবার চেষ্টা করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'শহর', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'দেশ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'পিন কোড', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'জেলা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'রাজ্য', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'ঠিকানা লাইন ১', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'ঠিকানা লাইন ২', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'সংস্থার নাম', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'সংস্থার অঞ্চল', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'সংস্থার শ্রেণি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'শাখা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'বিএসআর / আইএফএসসি কোড', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'হিসাব নম্বর', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'কার্ড নম্বর', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'নোডাল অফিসার', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'বিলম্বের কারণ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'ঘোষণা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'মূল অভিযোগ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'নিষ্পত্তি ধারা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'অভিযোগকারী', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'bn', 'নিয়ন্ত্রিত সংস্থা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');


-- ── telugu (te) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'మూల ఫిర్యాదును వెతకండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఫిర్యాదు సంఖ్య', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అప్పీలుదారు పేరు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'మొబైల్ నంబర్', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఇమెయిల్ చిరునామా', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఆర్‌బీఐఓ కార్యాలయం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ముగింపు నిబంధన', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'వర్గం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఫిర్యాదు ఆధారం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'వెతకండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'రీసెట్ చేయండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'మీ శోధనకు ఏ ఫిర్యాదు సరిపోలలేదు.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'కనీసం ఒక శోధన ప్రమాణాన్ని నమోదు చేయండి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'పేరుతో వెతకడానికి కనీసం రెండు అక్షరాలు నమోదు చేయండి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'శోధన పూర్తి కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'వెతుకుతోంది…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', '{{count}} ఫిర్యాదులు కనుగొనబడ్డాయి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'స్థితి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'కార్యాలయం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ముగించిన తేదీ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అన్నీ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'కార్యాలయం నమోదు కాలేదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అప్పీలు / వినతిని నమోదు చేయండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'నమోదు వివరాలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అప్పీలు దాఖలు చేసినవారు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అప్పీలు మూలం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'స్వీకరణ విధానం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అప్పీలు ఆధారం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'కోరిన ఉపశమనం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఫిర్యాదుదారు వివరాలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'నియంత్రిత సంస్థ వివరాలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఫిర్యాదుదారు న్యాయవాదియా?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'సంబంధిత న్యాయస్థాన విచారణలు ఉన్నాయా?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'దయచేసి ఈ కేసును న్యాయ కేసుల మాడ్యూల్‌లో నమోదు చేయండి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఈడీ / సమాన స్థాయి ఆమోదం పొందారా?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఆమోదం తేదీ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఆమోద వ్యాఖ్యలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఆమోద పత్రం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'భద్రపరచి కొనసాగండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అప్పీలును సృష్టించండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'వినతిని సృష్టించండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అప్పీలు నమోదు చేయబడింది.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'కొనసాగే ముందు అన్ని తప్పనిసరి ఫీల్డ్‌లను పూర్తి చేయండి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ముగించిన లేదా తిరిగి తెరిచిన ఫిర్యాదుపై మాత్రమే అప్పీలు చేయవచ్చు.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఈ ఫిర్యాదుపై అప్పీలు నమోదు చేసే అనుమతి మీకు లేదు.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఫిర్యాదు రికార్డు నుండి అందుబాటులో లేదు — దయచేసి ఈ విలువను నమోదు చేయండి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'మాస్టర్ డేటాలో కనుగొనబడలేదు — దయచేసి ఈ విలువను నమోదు చేయండి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఈ ఫీల్డ్ అవసరం.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అవును', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'కాదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఏటీఎం / డెబిట్ కార్డ్', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'క్రెడిట్ కార్డ్', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఇంటర్నెట్ బ్యాంకింగ్', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'మొబైల్ బ్యాంకింగ్ / యూపీఐ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'రుణం / అడ్వాన్సులు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'డిపాజిట్ ఖాతాలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'పెన్షన్', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'చెల్లింపు / బదిలీ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'బీమా', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఇతరాలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ప్రతి ఫైల్ 2 ఎంబీ లోపు ఉండాలి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'అన్ని జోడింపులు కలిపి 25 ఎంబీ లోపు ఉండాలి.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'మీరు గరిష్ఠంగా 10 ఫైల్‌లను జోడించగలరు.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'మళ్లీ ప్రయత్నించండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'నగరం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'దేశం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'పిన్ కోడ్', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'జిల్లా', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'రాష్ట్రం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'చిరునామా పంక్తి 1', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'చిరునామా పంక్తి 2', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'సంస్థ పేరు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'సంస్థ ప్రాంతం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'సంస్థ వర్గం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'శాఖ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'బీఎస్ఆర్ / ఐఎఫ్ఎస్‌సీ కోడ్', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఖాతా సంఖ్య', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'కార్డ్ సంఖ్య', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'నోడల్ అధికారి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఆలస్యానికి కారణం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ప్రకటనలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'మూల ఫిర్యాదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ముగింపు నిబంధన', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'ఫిర్యాదుదారు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'te', 'నియంత్రిత సంస్థ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');


-- ── tamil (ta) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மூல புகாரைத் தேடுங்கள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'புகார் எண்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீட்டாளர் பெயர்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'கைபேசி எண்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மின்னஞ்சல் முகவரி', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஆர்பிஐஓ அலுவலகம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'முடிவுறுத்தல் பிரிவு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'வகை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'புகாரின் அடிப்படை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'தேடு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மீட்டமை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'உங்கள் தேடலுக்கு எந்த புகாரும் பொருந்தவில்லை.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'குறைந்தது ஒரு தேடல் நிபந்தனையை உள்ளிடுங்கள்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'பெயரால் தேட குறைந்தது இரண்டு எழுத்துகளை உள்ளிடுங்கள்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'தேடலை நிறைவு செய்ய முடியவில்லை. மீண்டும் முயற்சிக்கவும்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'தேடுகிறது…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', '{{count}} புகார்கள் கிடைத்தன', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'நிலை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'அலுவலகம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'முடிக்கப்பட்ட தேதி', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'அனைத்தும்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'அலுவலகம் பதிவு செய்யப்படவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீடு / விண்ணப்பத்தைப் பதிவு செய்யுங்கள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'பதிவு விவரங்கள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீடு தாக்கல் செய்தவர்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீட்டின் ஆதாரம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'பெறப்பட்ட முறை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீட்டின் அடிப்படை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'கோரப்பட்ட நிவாரணம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'புகார்தாரர் விவரங்கள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஒழுங்குமுறை நிறுவன விவரங்கள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'புகார்தாரர் வழக்கறிஞரா?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'தொடர்புடைய நீதிமன்ற வழக்குகள் உள்ளதா?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'இந்த வழக்கை சட்ட வழக்குகள் தொகுதியில் பதிவு செய்யுங்கள்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஈடி / சமநிலை அதிகாரியின் ஒப்புதல் பெறப்பட்டதா?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஒப்புதல் தேதி', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஒப்புதல் கருத்துகள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஒப்புதல் ஆவணம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'சேமித்து தொடரவும்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீட்டை உருவாக்கு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'விண்ணப்பத்தை உருவாக்கு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீடு பதிவு செய்யப்பட்டது.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'தொடர்வதற்கு முன் அனைத்து கட்டாய புலங்களையும் நிரப்பவும்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'முடிக்கப்பட்ட அல்லது மீண்டும் திறக்கப்பட்ட புகார் மீது மட்டுமே மேல்முறையீடு செய்ய முடியும்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'இந்த புகாருக்கு எதிராக மேல்முறையீட்டைப் பதிவு செய்ய உங்களுக்கு அனுமதி இல்லை.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'புகார் பதிவேட்டில் கிடைக்கவில்லை — இந்த மதிப்பை உள்ளிடுங்கள்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'முதன்மைத் தரவில் காணப்படவில்லை — இந்த மதிப்பை உள்ளிடுங்கள்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'இந்தப் புலம் தேவை.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஆம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'இல்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஏடிஎம் / டெபிட் அட்டை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'கடன் அட்டை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'இணைய வங்கி சேவை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'கைபேசி வங்கி சேவை / யூபிஐ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'கடன் / முன்பணம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'வைப்பு கணக்குகள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஓய்வூதியம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'பணப் பரிமாற்றம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'காப்பீடு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மற்றவை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஒவ்வொரு கோப்பும் 2 எம்பி அல்லது குறைவாக இருக்க வேண்டும்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'அனைத்து இணைப்புகளும் சேர்ந்து 25 எம்பி அல்லது குறைவாக இருக்க வேண்டும்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'நீங்கள் அதிகபட்சம் 10 கோப்புகளை இணைக்கலாம்.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மீண்டும் முயற்சி', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'நகரம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'நாடு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'அஞ்சல் குறியீடு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மாவட்டம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மாநிலம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'முகவரி வரி 1', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'முகவரி வரி 2', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'நிறுவனத்தின் பெயர்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'நிறுவன பிராந்தியம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'நிறுவன வகை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'கிளை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'பிஎஸ்ஆர் / ஐஎஃப்எஸ்சி குறியீடு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'கணக்கு எண்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'அட்டை எண்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'நோடல் அதிகாரி', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'தாமதத்திற்கான காரணம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'அறிவிப்புகள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'மூல புகார்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'முடிவுறுத்தல் பிரிவு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'புகார்தாரர்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ta', 'ஒழுங்குமுறை நிறுவனம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');


-- ── gujarati (gu) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'મૂળ ફરિયાદ શોધો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ફરિયાદ નંબર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'અપીલકર્તાનું નામ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'મોબાઇલ નંબર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ઇમેલ સરનામું', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'આરબીઆઈઓ કાર્યાલય', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સમાપન કલમ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'શ્રેણી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ફરિયાદનો આધાર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'શોધો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'રીસેટ કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'તમારી શોધ સાથે કોઈ ફરિયાદ મેળ ખાતી નથી.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ઓછામાં ઓછું એક શોધ માપદંડ દાખલ કરો.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'નામથી શોધવા માટે ઓછામાં ઓછા બે અક્ષરો દાખલ કરો.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'શોધ પૂર્ણ થઈ શકી નથી. કૃપા કરીને ફરી પ્રયાસ કરો.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'શોધી રહ્યા છીએ…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', '{{count}} ફરિયાદો મળી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સ્થિતિ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'કાર્યાલય', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'બંધ કરવાની તારીખ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'બધા', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'કોઈ કાર્યાલય નોંધાયેલ નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'અપીલ / રજૂઆત નોંધો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'નોંધણી વિગતો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'અપીલ દાખલ કરનાર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'અપીલનો સ્રોત', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'પ્રાપ્તિની રીત', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'અપીલનો આધાર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'માંગેલી રાહત', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ફરિયાદીની વિગતો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'નિયંત્રિત સંસ્થાની વિગતો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'શું ફરિયાદી વકીલ છે?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'શું કોઈ સંબંધિત કોર્ટ કેસ છે?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'કૃપા કરીને આ કેસ કાનૂની કેસ મોડ્યુલમાં નોંધો.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ઈડી / સમકક્ષ કક્ષાની મંજૂરી મળી?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'મંજૂરીની તારીખ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'મંજૂરી ટિપ્પણીઓ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'મંજૂરી દસ્તાવેજ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સાચવો અને આગળ વધો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'અપીલ બનાવો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'રજૂઆત બનાવો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'અપીલ નોંધાઈ ગઈ છે.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'આગળ વધતા પહેલાં તમામ ફરજિયાત ક્ષેત્રો પૂર્ણ કરો.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ફક્ત બંધ કરેલી અથવા ફરી ખોલેલી ફરિયાદ પર જ અપીલ કરી શકાય છે.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'આ ફરિયાદ વિરુદ્ધ અપીલ નોંધવાની તમને પરવાનગી નથી.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ફરિયાદ રેકોર્ડમાંથી ઉપલબ્ધ નથી — કૃપા કરીને આ મૂલ્ય દાખલ કરો.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'માસ્ટર ડેટામાં મળ્યું નથી — કૃપા કરીને આ મૂલ્ય દાખલ કરો.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'આ ક્ષેત્ર આવશ્યક છે.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'હા', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ના', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'એટીએમ / ડેબિટ કાર્ડ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ક્રેડિટ કાર્ડ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ઇન્ટરનેટ બેન્કિંગ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'મોબાઇલ બેન્કિંગ / યુપીઆઈ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'લોન / એડવાન્સ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ડિપોઝિટ ખાતાં', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'પેન્શન', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'રકમ મોકલવી / તબદીલી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'વીમો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'અન્ય', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'દરેક ફાઇલ 2 એમબી અથવા તેથી નાની હોવી જોઈએ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'તમામ જોડાણો મળીને 25 એમબી અથવા તેથી નાનાં હોવાં જોઈએ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'તમે વધુમાં વધુ 10 ફાઇલો જોડી શકો છો.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ફરી પ્રયાસ કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'શહેર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'દેશ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'પિન કોડ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'જિલ્લો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'રાજ્ય', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સરનામું લાઇન 1', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સરનામું લાઇન 2', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સંસ્થાનું નામ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સંસ્થા પ્રદેશ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સંસ્થા શ્રેણી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'શાખા', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'બીએસઆર / આઈએફએસસી કોડ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ખાતા નંબર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'કાર્ડ નંબર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'નોડલ અધિકારી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'વિલંબનું કારણ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ઘોષણાઓ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'મૂળ ફરિયાદ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'સમાપન કલમ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'ફરિયાદી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'gu', 'નિયંત્રિત સંસ્થા', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');


-- ── urdu (ur) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'بنیادی شکایت تلاش کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'شکایت نمبر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اپیل کنندہ کا نام', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'موبائل نمبر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ای میل پتہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'آر بی آئی او دفتر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اختتامی شق', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'قسم', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'شکایت کی بنیاد', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'تلاش کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'دوبارہ ترتیب دیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'آپ کی تلاش سے کوئی شکایت مطابقت نہیں رکھتی۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'کم از کم ایک تلاش کا معیار درج کریں۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'نام سے تلاش کرنے کے لیے کم از کم دو حروف درج کریں۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'تلاش مکمل نہیں ہو سکی۔ براہ کرم دوبارہ کوشش کریں۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'تلاش جاری ہے…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', '{{count}} شکایات ملیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'حالت', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'دفتر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'بند ہونے کی تاریخ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'تمام', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'کوئی دفتر درج نہیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اپیل / نمائندگی درج کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اندراج کی تفصیلات', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اپیل دائر کرنے والا', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اپیل کا ذریعہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'وصولی کا طریقہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اپیل کی بنیاد', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'مطلوبہ ریلیف', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'شکایت کنندہ کی تفصیلات', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ریگولیٹڈ ادارے کی تفصیلات', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'کیا شکایت کنندہ وکیل ہے؟', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'کیا کوئی متعلقہ عدالتی مقدمہ ہے؟', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'براہ کرم اس مقدمے کو قانونی مقدمات ماڈیول میں درج کریں۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ای ڈی / ہم پلہ افسر کی منظوری حاصل ہوئی؟', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'منظوری کی تاریخ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'منظوری کے تبصرے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'منظوری کی دستاویز', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'محفوظ کریں اور آگے بڑھیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اپیل بنائیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'نمائندگی بنائیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اپیل درج کر دی گئی ہے۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'آگے بڑھنے سے پہلے تمام لازمی خانے مکمل کریں۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'صرف بند شدہ یا دوبارہ کھولی گئی شکایت پر اپیل کی جا سکتی ہے۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'آپ کو اس شکایت کے خلاف اپیل درج کرنے کی اجازت نہیں ہے۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'شکایت کے ریکارڈ سے دستیاب نہیں — براہ کرم یہ قیمت درج کریں۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ماسٹر ڈیٹا میں نہیں ملا — براہ کرم یہ قیمت درج کریں۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'یہ خانہ لازمی ہے۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ہاں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'نہیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اے ٹی ایم / ڈیبٹ کارڈ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'کریڈٹ کارڈ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'انٹرنیٹ بینکنگ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'موبائل بینکنگ / یو پی آئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'قرض / پیشگی رقم', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ڈپازٹ اکاؤنٹس', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'پنشن', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'رقم کی منتقلی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'بیمہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'دیگر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ہر فائل 2 ایم بی یا اس سے کم ہونی چاہیے۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'تمام منسلکات مل کر 25 ایم بی یا اس سے کم ہونے چاہیے۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'آپ زیادہ سے زیادہ 10 فائلیں منسلک کر سکتے ہیں۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'دوبارہ کوشش کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'شہر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ملک', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'پن کوڈ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ضلع', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ریاست', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'پتہ سطر 1', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'پتہ سطر 2', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ادارے کا نام', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ادارے کا علاقہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ادارے کی قسم', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'شاخ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'بی ایس آر / آئی ایف ایس سی کوڈ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اکاؤنٹ نمبر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'کارڈ نمبر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'نوڈل افسر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'تاخیر کی وجہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اعلانات', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'بنیادی شکایت', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'اختتامی شق', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'شکایت کنندہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ur', 'ریگولیٹڈ ادارہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');


-- ── kannada (kn) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೂಲ ದೂರನ್ನು ಹುಡುಕಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ದೂರು ಸಂಖ್ಯೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿದಾರರ ಹೆಸರು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೊಬೈಲ್ ಸಂಖ್ಯೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಇಮೇಲ್ ವಿಳಾಸ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಆರ್‌ಬಿಐಒ ಕಚೇರಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮುಕ್ತಾಯ ಷರತ್ತು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ವರ್ಗ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ದೂರಿನ ಆಧಾರ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಹುಡುಕಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮರುಹೊಂದಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ನಿಮ್ಮ ಹುಡುಕಾಟಕ್ಕೆ ಯಾವುದೇ ದೂರು ಹೊಂದಿಕೆಯಾಗಿಲ್ಲ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಕನಿಷ್ಠ ಒಂದು ಹುಡುಕಾಟ ಮಾನದಂಡವನ್ನು ನಮೂದಿಸಿ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಹೆಸರಿನಿಂದ ಹುಡುಕಲು ಕನಿಷ್ಠ ಎರಡು ಅಕ್ಷರಗಳನ್ನು ನಮೂದಿಸಿ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಹುಡುಕಾಟ ಪೂರ್ಣಗೊಳ್ಳಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಹುಡುಕುತ್ತಿದೆ…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', '{{count}} ದೂರುಗಳು ಕಂಡುಬಂದಿವೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಸ್ಥಿತಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಕಚೇರಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮುಚ್ಚಿದ ದಿನಾಂಕ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಎಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಯಾವುದೇ ಕಚೇರಿ ದಾಖಲಾಗಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿ / ಪ್ರಾತಿನಿಧ್ಯವನ್ನು ನೋಂದಾಯಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ನೋಂದಣಿ ವಿವರಗಳು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಿದವರು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿಯ ಮೂಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಸ್ವೀಕೃತಿ ವಿಧಾನ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿಯ ಆಧಾರ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಕೋರಿದ ಪರಿಹಾರ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ದೂರುದಾರರ ವಿವರಗಳು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ವಿವರಗಳು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ದೂರುದಾರರು ವಕೀಲರೇ?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಸಂಬಂಧಿತ ನ್ಯಾಯಾಲಯ ವಿಚಾರಣೆಗಳಿವೆಯೇ?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ದಯವಿಟ್ಟು ಈ ಪ್ರಕರಣವನ್ನು ಕಾನೂನು ಪ್ರಕರಣಗಳ ಮಾಡ್ಯೂಲ್‌ನಲ್ಲಿ ದಾಖಲಿಸಿ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಇಡಿ / ಸಮಾನ ಶ್ರೇಣಿಯ ಅನುಮೋದನೆ ಪಡೆಯಲಾಗಿದೆಯೇ?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಅನುಮೋದನೆಯ ದಿನಾಂಕ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಅನುಮೋದನೆ ಟಿಪ್ಪಣಿಗಳು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಅನುಮೋದನೆ ದಾಖಲೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಉಳಿಸಿ ಮತ್ತು ಮುಂದುವರಿಯಿರಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿ ರಚಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಪ್ರಾತಿನಿಧ್ಯ ರಚಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿ ನೋಂದಾಯಿಸಲಾಗಿದೆ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮುಂದುವರಿಯುವ ಮೊದಲು ಎಲ್ಲ ಕಡ್ಡಾಯ ಕ್ಷೇತ್ರಗಳನ್ನು ಪೂರ್ಣಗೊಳಿಸಿ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮುಚ್ಚಿದ ಅಥವಾ ಮರುಪ್ರಾರಂಭಿಸಿದ ದೂರಿನ ಮೇಲೆ ಮಾತ್ರ ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಬಹುದು.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಈ ದೂರಿನ ವಿರುದ್ಧ ಮೇಲ್ಮನವಿ ನೋಂದಾಯಿಸಲು ನಿಮಗೆ ಅನುಮತಿ ಇಲ್ಲ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ದೂರಿನ ದಾಖಲೆಯಿಂದ ಲಭ್ಯವಿಲ್ಲ — ದಯವಿಟ್ಟು ಈ ಮೌಲ್ಯವನ್ನು ನಮೂದಿಸಿ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮಾಸ್ಟರ್ ಡೇಟಾದಲ್ಲಿ ಕಂಡುಬಂದಿಲ್ಲ — ದಯವಿಟ್ಟು ಈ ಮೌಲ್ಯವನ್ನು ನಮೂದಿಸಿ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಈ ಕ್ಷೇತ್ರ ಅಗತ್ಯವಿದೆ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಹೌದು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಇಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಎಟಿಎಂ / ಡೆಬಿಟ್ ಕಾರ್ಡ್', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಕ್ರೆಡಿಟ್ ಕಾರ್ಡ್', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಇಂಟರ್ನೆಟ್ ಬ್ಯಾಂಕಿಂಗ್', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೊಬೈಲ್ ಬ್ಯಾಂಕಿಂಗ್ / ಯುಪಿಐ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಸಾಲ / ಮುಂಗಡ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಠೇವಣಿ ಖಾತೆಗಳು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಪಿಂಚಣಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಹಣ ರವಾನೆ / ವರ್ಗಾವಣೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ವಿಮೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಇತರೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಪ್ರತಿ ಕಡತವು 2 ಎಂಬಿ ಅಥವಾ ಕಡಿಮೆ ಇರಬೇಕು.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಎಲ್ಲ ಲಗತ್ತುಗಳು ಒಟ್ಟಾಗಿ 25 ಎಂಬಿ ಅಥವಾ ಕಡಿಮೆ ಇರಬೇಕು.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ನೀವು ಗರಿಷ್ಠ 10 ಕಡತಗಳನ್ನು ಲಗತ್ತಿಸಬಹುದು.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ನಗರ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ದೇಶ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಪಿನ್ ಕೋಡ್', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಜಿಲ್ಲೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ರಾಜ್ಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ವಿಳಾಸ ಸಾಲು 1', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ವಿಳಾಸ ಸಾಲು 2', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಸಂಸ್ಥೆಯ ಹೆಸರು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಸಂಸ್ಥೆ ಪ್ರದೇಶ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಸಂಸ್ಥೆ ವರ್ಗ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಶಾಖೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಬಿಎಸ್ಆರ್ / ಐಎಫ್ಎಸ್‌ಸಿ ಕೋಡ್', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಖಾತೆ ಸಂಖ್ಯೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಕಾರ್ಡ್ ಸಂಖ್ಯೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ನೋಡಲ್ ಅಧಿಕಾರಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ವಿಳಂಬದ ಕಾರಣ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಘೋಷಣೆಗಳು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮೂಲ ದೂರು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ಮುಕ್ತಾಯ ಷರತ್ತು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ದೂರುದಾರ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'kn', 'ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');


-- ── malayalam (ml) ──

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'മൂല പരാതി തിരയുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പരാതി നമ്പർ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.complaint_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അപ്പീൽ നൽകിയവരുടെ പേര്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'മൊബൈൽ നമ്പർ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_mobile'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഇമെയിൽ വിലാസം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.appellant_email'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ആർബിഐഒ ഓഫീസ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.rbio_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അവസാനിപ്പിക്കൽ വ്യവസ്ഥ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'വിഭാഗം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പരാതിയുടെ അടിസ്ഥാനം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.ground_of_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'തിരയുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_search'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പുനഃക്രമീകരിക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.button_reset'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നിങ്ങളുടെ തിരയലിനോട് ഒരു പരാതിയും പൊരുത്തപ്പെടുന്നില്ല.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_results'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'കുറഞ്ഞത് ഒരു തിരയൽ മാനദണ്ഡം നൽകുക.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_no_filter'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പേര് ഉപയോഗിച്ച് തിരയാൻ കുറഞ്ഞത് രണ്ട് അക്ഷരങ്ങൾ നൽകുക.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_term_too_short'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'തിരയൽ പൂർത്തിയാക്കാനായില്ല. വീണ്ടും ശ്രമിക്കുക.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.error_failed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'തിരയുന്നു…', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.loading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', '{{count}} പരാതികൾ കണ്ടെത്തി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.results_count'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നില', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_status'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഓഫീസ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_office'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അവസാനിപ്പിച്ച തീയതി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.col_closed_on'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'എല്ലാം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.select_option_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഓഫീസ് രേഖപ്പെടുത്തിയിട്ടില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.search.no_office_recorded'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അപ്പീൽ / നിവേദനം രജിസ്റ്റർ ചെയ്യുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'രജിസ്ട്രേഷൻ വിശദാംശങ്ങൾ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.milestone_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അപ്പീൽ സമർപ്പിച്ചത്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_filed_by'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അപ്പീലിന്റെ ഉറവിടം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.source_of_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'സ്വീകരിച്ച രീതി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.mode_of_receipt'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അപ്പീലിന്റെ അടിസ്ഥാനം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appeal_ground'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ആവശ്യപ്പെട്ട ആശ്വാസം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.relief_sought'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പരാതിക്കാരന്റെ വിശദാംശങ്ങൾ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.complainant_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നിയന്ത്രിത സ്ഥാപനത്തിന്റെ വിശദാംശങ്ങൾ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പരാതിക്കാരൻ ഒരു അഭിഭാഷകനാണോ?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.is_complainant_advocate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ബന്ധപ്പെട്ട കോടതി വിചാരണകൾ ഉണ്ടോ?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.has_related_court_trial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഈ കേസ് നിയമ കേസുകളുടെ മോഡ്യൂളിൽ രേഖപ്പെടുത്തുക.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.prompt_log_legal_case'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഇഡി / തുല്യ പദവിയുടെ അനുമതി ലഭിച്ചോ?', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_given'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അനുമതിയുടെ തീയതി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_date'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അനുമതി അഭിപ്രായങ്ങൾ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_comments'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അനുമതി രേഖ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.ed_approval_document'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'സംരക്ഷിച്ച് തുടരുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_save_proceed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അപ്പീൽ സൃഷ്ടിക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_appeal'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നിവേദനം സൃഷ്ടിക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.create_representation'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അപ്പീൽ രജിസ്റ്റർ ചെയ്തു.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.success'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'തുടരുന്നതിന് മുമ്പ് എല്ലാ നിർബന്ധിത ഫീൽഡുകളും പൂർത്തിയാക്കുക.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_mandatory_incomplete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അവസാനിപ്പിച്ചതോ വീണ്ടും തുറന്നതോ ആയ പരാതിയിൽ മാത്രമേ അപ്പീൽ നൽകാനാകും.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_parent_not_appealable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഈ പരാതിക്കെതിരെ അപ്പീൽ രജിസ്റ്റർ ചെയ്യാൻ നിങ്ങൾക്ക് അനുമതിയില്ല.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.error_not_permitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പരാതി രേഖയിൽ നിന്ന് ലഭ്യമല്ല — ഈ മൂല്യം നൽകുക.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_no_source'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'മാസ്റ്റർ ഡാറ്റയിൽ കണ്ടെത്തിയില്ല — ഈ മൂല്യം നൽകുക.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.unresolved_not_in_master'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഈ ഫീൽഡ് ആവശ്യമാണ്.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.field_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അതെ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.yes'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അല്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.no'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'എടിഎം / ഡെബിറ്റ് കാർഡ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.atm_debit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ക്രെഡിറ്റ് കാർഡ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.credit_card'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഇന്റർനെറ്റ് ബാങ്കിംഗ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.internet_banking'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'മൊബൈൽ ബാങ്കിംഗ് / യുപിഐ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.mobile_banking_upi'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'വായ്പ / അഡ്വാൻസ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.loan_advances'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നിക്ഷേപ അക്കൗണ്ടുകൾ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.deposit_accounts'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പെൻഷൻ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.pension'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പണമടയ്ക്കൽ / കൈമാറ്റം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.remittance_transfer'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഇൻഷുറൻസ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.insurance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'മറ്റുള്ളവ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.ground.others'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ഓരോ ഫയലും 2 എംബി അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_file_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'എല്ലാ അറ്റാച്ച്‌മെന്റുകളും ചേർന്ന് 25 എംബി അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_total_too_large'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നിങ്ങൾക്ക് പരമാവധി 10 ഫയലുകൾ അറ്റാച്ച് ചെയ്യാം.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.upload.error_too_many_files'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'വീണ്ടും ശ്രമിക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.button_retry'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നഗരം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_city'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'രാജ്യം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_country'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പിൻ കോഡ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_pincode'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ജില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_district'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'സംസ്ഥാനം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_state'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'വിലാസ വരി 1', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address1'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'വിലാസ വരി 2', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.appellant_address2'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'സ്ഥാപനത്തിന്റെ പേര്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'സ്ഥാപന മേഖല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_region'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'സ്ഥാപന വിഭാഗം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_category'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ശാഖ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.entity_branch'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'ബിഎസ്ആർ / ഐഎഫ്എസ്‌സി കോഡ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.bsr_ifsc_code'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അക്കൗണ്ട് നമ്പർ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.account_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'കാർഡ് നമ്പർ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.card_number'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നോഡൽ ഓഫീസർ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.nodal_officer_name'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'കാലതാമസത്തിന്റെ കാരണം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.reason_for_delay'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പ്രഖ്യാപനങ്ങൾ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.declarations_heading'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'മൂല പരാതി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.parent_complaint'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'അവസാനിപ്പിക്കൽ വ്യവസ്ഥ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.closure_clause'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'പരാതിക്കാരൻ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_complainant'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

INSERT INTO translations (translation_key_id, locale, value, updated_at)
SELECT k.id, 'ml', 'നിയന്ത്രിത സ്ഥാപനം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.register.filed_by_entity'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
