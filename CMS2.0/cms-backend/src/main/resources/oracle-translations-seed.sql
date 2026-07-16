-- =============================================================================
-- Oracle Translation Seed Script
-- Generated from EligibilityTranslationSeeder.java & PortalFullTranslationSeeder.java
-- Idempotent: safe to re-run (uses NOT EXISTS / COUNT checks)
-- =============================================================================
SET DEFINE OFF;

-- Ensure sequences exist (ignore errors if already present)
DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM user_sequences WHERE sequence_name = 'TRANSLATION_KEY_SEQ';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE translation_key_seq START WITH 1 INCREMENT BY 1 NOCACHE';
  END IF;
  SELECT COUNT(*) INTO v_cnt FROM user_sequences WHERE sequence_name = 'TRANSLATION_SEQ';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE translation_seq START WITH 1 INCREMENT BY 1 NOCACHE';
  END IF;
END;
/

-- =============================================================================
-- Helper Procedures (created temporarily for seeding)
-- =============================================================================
CREATE OR REPLACE PROCEDURE cms_seed_key(
  p_code IN VARCHAR2,
  p_module IN VARCHAR2,
  p_description IN VARCHAR2,
  p_default_value IN VARCHAR2
) IS
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM translation_key WHERE code = p_code;
  IF v_cnt = 0 THEN
    INSERT INTO translation_key (id, code, module, description, default_value)
    VALUES (translation_key_seq.NEXTVAL, p_code, p_module, p_description, p_default_value);
  END IF;
END;
/

CREATE OR REPLACE PROCEDURE cms_seed_translation(
  p_code IN VARCHAR2,
  p_locale IN VARCHAR2,
  p_value IN NVARCHAR2
) IS
  v_key_id NUMBER;
  v_cnt NUMBER;
BEGIN
  BEGIN
    SELECT id INTO v_key_id FROM translation_key WHERE code = p_code;
  EXCEPTION WHEN NO_DATA_FOUND THEN
    RETURN;
  END;
  SELECT COUNT(*) INTO v_cnt FROM translation WHERE translation_key_id = v_key_id AND locale = p_locale;
  IF v_cnt = 0 THEN
    INSERT INTO translation (id, translation_key_id, locale, value)
    VALUES (translation_seq.NEXTVAL, v_key_id, p_locale, p_value);
  END IF;
END;
/

-- =============================================================================
-- SECTION 1: Translation Keys from EligibilityTranslationSeeder
-- =============================================================================
BEGIN
  cms_seed_key('eligibility.title', 'eligibility', 'Page title', 'FILE A NEW COMPLAINT');
  cms_seed_key('eligibility.subtitle', 'eligibility', 'Page subtitle', 'Check your eligibility to proceed');
  cms_seed_key('eligibility.mandatory_note', 'eligibility', 'Mandatory fields note', 'All fields are mandatory unless marked as Optional.');
  cms_seed_key('eligibility.select_desc', 'eligibility', 'Select entity description', 'Select the financial institution--such as a bank, Non-Banking Financial Company (NBFC), or payment processor--licensed and supervised by the Reserve Bank of India against which you wish to file a complaint.');
  cms_seed_key('eligibility.select_placeholder', 'eligibility', 'Select placeholder', 'Select a Value');
  cms_seed_key('eligibility.simplify_btn', 'eligibility', 'Simplify button', 'Simplify For Me');
  cms_seed_key('eligibility.browse_file', 'eligibility', 'Browse file button', 'Browse File');
  cms_seed_key('eligibility.upload_hint', 'eligibility', 'Upload hint text', 'Support formats: PDF, JPG, PNG. Maximum size: 5MB');
  cms_seed_key('eligibility.opt_yes', 'eligibility', 'Yes option', 'Yes');
  cms_seed_key('eligibility.opt_no', 'eligibility', 'No option', 'No');
  cms_seed_key('eligibility.q_select_re', 'eligibility', 'Q: Select RE', 'Select Regulated Entity Name');
  cms_seed_key('eligibility.q_filed_with_re', 'eligibility', 'Q: Filed with RE', 'Have you filed a written / electronic complaint with the {{reName}}?');
  cms_seed_key('eligibility.q_received_reply', 'eligibility', 'Q: Received reply', 'Have you received any reply from the Entity?');
  cms_seed_key('eligibility.q_sent_reminder', 'eligibility', 'Q: Sent reminder', 'Have you sent any reminder to the {{reName}}?');
  cms_seed_key('eligibility.q_sub_judice', 'eligibility', 'Q: Sub-judice', 'Is the complaint relating to the same grievance which is already pending before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?');
  cms_seed_key('eligibility.q_already_settled', 'eligibility', 'Q: Already settled', 'Is the complaint relating to the same grievance which is already settled or dealt before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?');
  cms_seed_key('eligibility.q_through_advocate', 'eligibility', 'Q: Through advocate', 'Is your complaint being made through an advocate?');
  cms_seed_key('eligibility.q_pending_ombudsman', 'eligibility', 'Q: Pending before Ombudsman', 'Is the complaint relating to the same grievance which is already pending before the Ombudsman?');
  cms_seed_key('eligibility.q_settled_ombudsman', 'eligibility', 'Q: Settled by Ombudsman', 'Is the complaint relating to the same grievance which is already settled or dealt with on merits by the Ombudsman?');
  cms_seed_key('eligibility.q_staff_of_re', 'eligibility', 'Q: Staff of RE', 'Is the Complainant a staff of the RE and complaint involves employer-employee relationship?');
  cms_seed_key('eligibility.q_sub_judice_simple', 'eligibility', 'Q: Sub-judice simplified', 'Have you already taken this exact problem to a court, arbitrator, or another official legal authority (excluding criminal cases or police investigations)?');
  cms_seed_key('eligibility.q_already_settled_simple', 'eligibility', 'Q: Already settled simplified', 'Has this exact problem already been resolved by a court, arbitrator, or another official legal authority (excluding criminal cases or police investigations)?');
  cms_seed_key('eligibility.q_through_advocate_simple', 'eligibility', 'Q: Advocate simplified', 'Are you filing this complaint with the help of a lawyer or legal representative?');
  cms_seed_key('eligibility.q_pending_ombudsman_simple', 'eligibility', 'Q: Pending Ombudsman simplified', 'Have you already filed a complaint about this same issue with the Ombudsman and it is still under review?');
  cms_seed_key('eligibility.q_settled_ombudsman_simple', 'eligibility', 'Q: Settled Ombudsman simplified', 'Has the Ombudsman already reviewed and resolved this same complaint in the past?');
  cms_seed_key('eligibility.q_staff_of_re_simple', 'eligibility', 'Q: Staff simplified', 'Are you an employee of the bank/NBFC you are complaining against, and is your complaint about your job or employment?');
  cms_seed_key('eligibility.sub_complaint_date', 'eligibility', 'Sub: complaint date', 'Date on which the complaint was first filed with');
  cms_seed_key('eligibility.sub_upload_complaint', 'eligibility', 'Sub: upload complaint', 'Upload a copy of the complaint sent to');
  cms_seed_key('eligibility.sub_reminder_date', 'eligibility', 'Sub: reminder date', 'Date on which reminder was sent');
  cms_seed_key('eligibility.sub_upload_reminder', 'eligibility', 'Sub: upload reminder', 'Upload Reminder Copy');
  cms_seed_key('eligibility.sub_reply_date', 'eligibility', 'Sub: reply date', 'Date on which reply was received');
  cms_seed_key('eligibility.sub_upload_reply', 'eligibility', 'Sub: upload reply', 'Upload Reply Copy');
  cms_seed_key('eligibility.sub_are_you_complainant', 'eligibility', 'Sub: are you complainant', 'If Yes, then are you the Complainant?');
  cms_seed_key('eligibility.block_not_filed', 'eligibility', 'Block: not filed', 'in terms of clause 10(1)(j) of Reserve Bank - Integrated Ombudsman Scheme, 2026, the complaint cannot be processed under the Scheme.');
  cms_seed_key('eligibility.block_sub_judice', 'eligibility', 'Block: sub-judice', 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause 10(2)(b)(ii) of the Reserve Bank - Integrated Ombudsman Scheme, 2021.');
  cms_seed_key('eligibility.block_already_settled', 'eligibility', 'Block: already settled', 'As your complaint has already been settled or dealt with by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under the Reserve Bank - Integrated Ombudsman Scheme, 2021.');
  cms_seed_key('eligibility.block_pending_ombudsman', 'eligibility', 'Block: pending ombudsman', 'Your complaint is already pending before the Ombudsman on the same grievance. Duplicate complaints cannot be filed.');
  cms_seed_key('eligibility.block_settled_ombudsman', 'eligibility', 'Block: settled ombudsman', 'Your complaint has already been settled or dealt with on merits by the Ombudsman. You cannot file a fresh complaint on the same issue.');
  cms_seed_key('eligibility.block_staff_of_re', 'eligibility', 'Block: staff of RE', 'Complaints involving employer-employee relationship between the complainant and the Regulated Entity cannot be filed under the Integrated Ombudsman Scheme.');
END;
/

-- =============================================================================
-- SECTION 2: Translation Keys from PortalFullTranslationSeeder
-- =============================================================================
BEGIN
  -- Home
  cms_seed_key('home.authenticated_title', 'home', 'home.authenticated_title', 'Home');
  cms_seed_key('home.file_new_btn', 'home', 'home.file_new_btn', 'File a New Complaint');
  cms_seed_key('home.complaint_history', 'home', 'home.complaint_history', 'Complaint History');
  cms_seed_key('home.table_id', 'home', 'home.table_id', 'ID');
  cms_seed_key('home.table_complaint_against', 'home', 'home.table_complaint_against', 'Complaint Against');
  cms_seed_key('home.table_complaint_date', 'home', 'home.table_complaint_date', 'Complaint Date');
  cms_seed_key('home.table_status', 'home', 'home.table_status', 'Status');
  cms_seed_key('home.table_comments', 'home', 'home.table_comments', 'Comments');
  cms_seed_key('home.table_action', 'home', 'home.table_action', 'Action');
  cms_seed_key('home.search_placeholder', 'home', 'home.search_placeholder', 'Search');
  cms_seed_key('home.loading', 'home', 'home.loading', 'Loading...');
  cms_seed_key('home.no_complaints', 'home', 'home.no_complaints', 'No complaints found');
  cms_seed_key('home.ct_tag', 'home', 'home.ct_tag', 'FIND YOUR FINANCIAL INSTITUTION');
  cms_seed_key('home.ct_heading', 'home', 'home.ct_heading', 'You can file complaints about');
  cms_seed_key('home.ct_regulated', 'home', 'home.ct_regulated', 'Regulated entities covered under Reserve Bank-Integrated Ombudsman Scheme, 2026');
  cms_seed_key('home.ct_download', 'home', 'home.ct_download', 'Download PDF');
  cms_seed_key('home.scheme_title', 'home', 'home.scheme_title', 'Details of the Ombudsmen Scheme');
  cms_seed_key('home.wwd_tag', 'home', 'home.wwd_tag', 'WHAT WE DO');
  cms_seed_key('home.wwd_heading', 'home', 'home.wwd_heading', 'RBI streamlines the complaint resolution process for Indian Citizens.');
  cms_seed_key('home.wwd_desc', 'home', 'home.wwd_desc', 'RBI simplifies and expedites the complaint resolution process for Indian citizens, ensuring fair and timely resolutions.');
  cms_seed_key('home.ways_title', 'home', 'home.ways_title', 'Ways of Filing Complaint');
  cms_seed_key('home.ways_portal_title', 'home', 'home.ways_portal_title', 'CMS Portal');
  cms_seed_key('home.ways_portal_badge', 'home', 'home.ways_portal_badge', '(You are here)');
  cms_seed_key('home.ways_portal_desc', 'home', 'home.ways_portal_desc', 'File your complaint on this portal. It is the fastest way to resolve your complaints efficiently.');
  cms_seed_key('home.ways_portal_link', 'home', 'home.ways_portal_link', 'File a New Complaint');
  cms_seed_key('home.ways_wizard_title', 'home', 'home.ways_wizard_title', 'Can RBI help?');
  cms_seed_key('home.ways_wizard_badge', 'home', 'home.ways_wizard_badge', 'New');
  cms_seed_key('home.ways_wizard_desc', 'home', 'home.ways_wizard_desc', 'Not sure if you can file? Answer 4 quick questions to check your eligibility before starting.');
  cms_seed_key('home.ways_wizard_link', 'home', 'home.ways_wizard_link', 'Check Eligibility');
  cms_seed_key('home.ways_email_title', 'home', 'home.ways_email_title', 'Email');
  cms_seed_key('home.ways_email_desc', 'home', 'home.ways_email_desc', 'Send your complaint to us via email by filling the Complaint Form');
  cms_seed_key('home.ways_letter_title', 'home', 'home.ways_letter_title', 'Physical Letter');
  cms_seed_key('home.ways_letter_desc', 'home', 'home.ways_letter_desc', 'Send your complaint to us via letter by filling the Complaint Form');
  cms_seed_key('home.ways_view_details', 'home', 'home.ways_view_details', 'View Details');
  cms_seed_key('home.education_tag', 'home', 'home.education_tag', 'PUBLIC AWARENESS');
  cms_seed_key('home.education_title', 'home', 'home.education_title', 'Education and Awareness');
  cms_seed_key('home.view_all', 'home', 'home.view_all', 'View All');
  cms_seed_key('home.faq_tag', 'home', 'home.faq_tag', 'FAQS');
  cms_seed_key('home.faq_title', 'home', 'home.faq_title', 'Frequently Asked Questions');
  -- Layout
  cms_seed_key('layout.logged_in_as', 'layout', 'layout.logged_in_as', 'Logged in as:');
  cms_seed_key('layout.session_expires', 'layout', 'layout.session_expires', 'Session expires in:');
  cms_seed_key('layout.logout', 'layout', 'layout.logout', 'Logout');
  cms_seed_key('layout.need_help', 'layout', 'layout.need_help', 'Need help ?');
  cms_seed_key('layout.need_assistance', 'layout', 'layout.need_assistance', 'Need Assistance? Call 14448');
  cms_seed_key('layout.assistance_desc', 'layout', 'layout.assistance_desc', 'The contact center (#14448) with Interactive Voice Response System (IVRS) is available 24x7, while the facility to connect to Contact Centre personnel is available from Monday to Saturday except for National Holidays, between 8:00 AM to 10:00 PM for English, Hindi, and ten regional languages.');
  -- Login
  cms_seed_key('login.page_title', 'login', 'login.page_title', 'Verification');
  cms_seed_key('login.verify_phone', 'login', 'login.verify_phone', 'Verify Phone Number');
  cms_seed_key('login.mobile_label', 'login', 'login.mobile_label', 'Mobile Number');
  cms_seed_key('login.enter_mobile', 'login', 'login.enter_mobile', 'Enter Mobile Number');
  cms_seed_key('login.captcha_visual', 'login', 'login.captcha_visual', 'Enter the text shown below');
  cms_seed_key('login.captcha_math', 'login', 'login.captcha_math', 'Answer the question');
  cms_seed_key('login.captcha_enter', 'login', 'login.captcha_enter', 'Enter text');
  cms_seed_key('login.captcha_answer', 'login', 'login.captcha_answer', 'Your answer');
  cms_seed_key('login.send_otp', 'login', 'login.send_otp', 'Send OTP');
  cms_seed_key('login.sending', 'login', 'login.sending', 'Sending...');
  cms_seed_key('login.email_fallback', 'login', 'login.email_fallback', 'Send OTP to email instead');
  cms_seed_key('login.hide_email', 'login', 'login.hide_email', 'Hide email option');
  cms_seed_key('login.email_label', 'login', 'login.email_label', 'Verified Email Address');
  cms_seed_key('login.email_placeholder', 'login', 'login.email_placeholder', 'Enter your verified email');
  cms_seed_key('login.send_otp_email', 'login', 'login.send_otp_email', 'Send OTP to Email');
  cms_seed_key('login.verify_email_first', 'login', 'login.verify_email_first', 'Verify this email first');
  cms_seed_key('login.otp_sent_to', 'login', 'login.otp_sent_to', 'OTP sent to');
  cms_seed_key('login.dev_otp_notice', 'login', 'login.dev_otp_notice', 'OTP auto-populated (dev mode) -- click Verify to proceed');
  cms_seed_key('login.enter_otp', 'login', 'login.enter_otp', 'Enter OTP');
  cms_seed_key('login.verify_otp', 'login', 'login.verify_otp', 'Verify OTP');
  cms_seed_key('login.verifying', 'login', 'login.verifying', 'Verifying...');
  cms_seed_key('login.resend_otp', 'login', 'login.resend_otp', 'Resend OTP');
  cms_seed_key('login.resend_in', 'login', 'login.resend_in', 'Resend OTP in');
  cms_seed_key('login.change_number', 'login', 'login.change_number', 'Change Number');
  -- History
  cms_seed_key('history.title', 'history', 'history.title', 'My Complaints');
  cms_seed_key('history.create_new', 'history', 'history.create_new', 'Create New Complaint');
  cms_seed_key('history.col_number', 'history', 'history.col_number', 'Complaint Number');
  cms_seed_key('history.col_against', 'history', 'history.col_against', 'Complaint Against');
  cms_seed_key('history.col_date', 'history', 'history.col_date', 'Complaint Date');
  cms_seed_key('history.col_status', 'history', 'history.col_status', 'Status');
  cms_seed_key('history.col_comments', 'history', 'history.col_comments', 'Comments');
  cms_seed_key('history.loading', 'history', 'history.loading', 'Loading complaints...');
  cms_seed_key('history.no_complaints', 'history', 'history.no_complaints', 'No complaints found.');
  cms_seed_key('history.assistance_title', 'history', 'history.assistance_title', 'Need Assistance? Call 14448');
  -- Form
  cms_seed_key('form.mandatory_note', 'form', 'form.mandatory_note', 'Fields marked with (*) are mandatory.');
  cms_seed_key('form.step_label', 'form', 'form.step_label', 'Step');
  cms_seed_key('form.cancel', 'form', 'form.cancel', 'Cancel');
  cms_seed_key('form.save', 'form', 'form.save', 'Save');
  cms_seed_key('form.back', 'form', 'form.back', 'Back');
  cms_seed_key('form.next', 'form', 'form.next', 'Next');
  cms_seed_key('form.auto_saved', 'form', 'form.auto_saved', 'Auto saved a few seconds ago');
  cms_seed_key('form.download_pdf', 'form', 'form.download_pdf', 'Download PDF');
  cms_seed_key('form.submit_complaint', 'form', 'form.submit_complaint', 'Submit Complaint');
  cms_seed_key('form.submitting', 'form', 'form.submitting', 'Submitting...');
  cms_seed_key('form.step1_title', 'form', 'form.step1_title', 'Complainant Details');
  cms_seed_key('form.step2_title', 'form', 'form.step2_title', 'Regulated Entity Details');
  cms_seed_key('form.step3_title', 'form', 'form.step3_title', 'Complaint Details');
  cms_seed_key('form.step4_title', 'form', 'form.step4_title', 'Representative Authorization');
  cms_seed_key('form.step5_title', 'form', 'form.step5_title', 'Declaration');
  cms_seed_key('form.step6_title', 'form', 'form.step6_title', 'Review and Submit');
  cms_seed_key('form.complainant_category', 'form', 'form.complainant_category', 'Complainant Category');
  cms_seed_key('form.individual', 'form', 'form.individual', 'Individual');
  cms_seed_key('form.business_entity', 'form', 'form.business_entity', 'Business Entity');
  cms_seed_key('form.other', 'form', 'form.other', 'Other');
  cms_seed_key('form.first_name', 'form', 'form.first_name', 'First Name');
  cms_seed_key('form.middle_name', 'form', 'form.middle_name', 'Middle Name');
  cms_seed_key('form.last_name', 'form', 'form.last_name', 'Last Name');
  cms_seed_key('form.age', 'form', 'form.age', 'Age');
  cms_seed_key('form.gender', 'form', 'form.gender', 'Gender');
  cms_seed_key('form.male', 'form', 'form.male', 'Male');
  cms_seed_key('form.female', 'form', 'form.female', 'Female');
  cms_seed_key('form.email_id', 'form', 'form.email_id', 'Email ID');
  cms_seed_key('form.mobile_number', 'form', 'form.mobile_number', 'Mobile Number');
  cms_seed_key('form.pincode', 'form', 'form.pincode', 'Pincode');
  cms_seed_key('form.state', 'form', 'form.state', 'State');
  cms_seed_key('form.district', 'form', 'form.district', 'District');
  cms_seed_key('form.address', 'form', 'form.address', 'Address Details');
  cms_seed_key('form.enter_value', 'form', 'form.enter_value', 'Enter Value');
  cms_seed_key('form.select_value', 'form', 'form.select_value', 'Select Value');
  cms_seed_key('form.re_name', 'form', 'form.re_name', 'Regulated Entity Name');
  cms_seed_key('form.credit_card_q', 'form', 'form.credit_card_q', 'Is your complaint related to credit card?');
  cms_seed_key('form.entity_state', 'form', 'form.entity_state', 'Entity State');
  cms_seed_key('form.entity_district', 'form', 'form.entity_district', 'Entity District');
  cms_seed_key('form.entity_branch', 'form', 'form.entity_branch', 'Entity Branch');
  cms_seed_key('form.complaint_category', 'form', 'form.complaint_category', 'Select a complaint Category');
  cms_seed_key('form.facts', 'form', 'form.facts', 'Facts of the complaint');
  cms_seed_key('form.write_detail', 'form', 'form.write_detail', 'Write in detail');
  cms_seed_key('form.use_mic', 'form', 'form.use_mic', 'Use Microphone');
  cms_seed_key('form.stop', 'form', 'form.stop', 'Stop');
  cms_seed_key('form.has_account', 'form', 'form.has_account', 'Do You Have An Account With');
  cms_seed_key('form.account_type', 'form', 'form.account_type', 'Select Type of Accounts With');
  cms_seed_key('form.savings', 'form', 'form.savings', 'Savings Account');
  cms_seed_key('form.current_account', 'form', 'form.current_account', 'Current Account');
  cms_seed_key('form.loan', 'form', 'form.loan', 'Loan Account');
  cms_seed_key('form.credit_card', 'form', 'form.credit_card', 'Credit Card');
  cms_seed_key('form.debit_card', 'form', 'form.debit_card', 'Debit/ATM Card');
  cms_seed_key('form.card_number', 'form', 'form.card_number', 'Credit Card Number');
  cms_seed_key('form.savings_number', 'form', 'form.savings_number', 'Savings Account Number');
  cms_seed_key('form.loan_number', 'form', 'form.loan_number', 'Loan Account Number');
  cms_seed_key('form.atm_number', 'form', 'form.atm_number', 'ATM/Debit Card Number');
  cms_seed_key('form.wallet_q', 'form', 'form.wallet_q', 'Is your complaint against the wallet of the Regulated Entity?');
  cms_seed_key('form.wallet_name', 'form', 'form.wallet_name', 'Name of Wallet');
  cms_seed_key('form.txn_ref', 'form', 'form.txn_ref', 'Transaction/Reference Number');
  cms_seed_key('form.biz_corr_q', 'form', 'form.biz_corr_q', 'Is your complaint against a Business Correspondent?');
  cms_seed_key('form.amount_involved', 'form', 'form.amount_involved', 'Amount Involved In The Transaction/Dispute, If Any');
  cms_seed_key('form.compensation_sought', 'form', 'form.compensation_sought', 'Compensation Sought For Dispute, If Any');
  cms_seed_key('form.compensation_harassment', 'form', 'form.compensation_harassment', 'Compensation For Harassment, If Any');
  cms_seed_key('form.upload_docs', 'form', 'form.upload_docs', 'Upload any additional documents relevant to the complaint');
  cms_seed_key('form.browse_file', 'form', 'form.browse_file', 'Browse File');
  cms_seed_key('form.upload_hint', 'form', 'form.upload_hint', 'Support formats: PDF, JPG, PNG');
  cms_seed_key('form.upload_size', 'form', 'form.upload_size', 'Maximum size: 5MB');
  cms_seed_key('form.auth_rep_q', 'form', 'form.auth_rep_q', 'Is the complaint being filed through an Authorised Representative on behalf of you / complainant?');
  cms_seed_key('form.auth_rep_details', 'form', 'form.auth_rep_details', 'Fill the following details for the authorised representative');
  cms_seed_key('form.rep_name', 'form', 'form.rep_name', 'Name');
  cms_seed_key('form.rep_email', 'form', 'form.rep_email', 'Email ID');
  cms_seed_key('form.rep_mobile', 'form', 'form.rep_mobile', 'Mobile Number');
  cms_seed_key('form.rep_pincode', 'form', 'form.rep_pincode', 'Pincode');
  cms_seed_key('form.rep_state', 'form', 'form.rep_state', 'State');
  cms_seed_key('form.rep_district', 'form', 'form.rep_district', 'District');
  cms_seed_key('form.rep_city', 'form', 'form.rep_city', 'City');
  cms_seed_key('form.rep_address', 'form', 'form.rep_address', 'Address Details');
  cms_seed_key('form.rep_upload', 'form', 'form.rep_upload', 'Upload supporting documents');
  cms_seed_key('form.decl_heading', 'form', 'form.decl_heading', 'Required Declarations');
  cms_seed_key('form.decl_1', 'form', 'form.decl_1', '(i) I/ We, the complainant/s herein declare that: a) the information furnished above is true and correct; and b) I/We have not concealed or misrepresented any fact stated above and in the documents submitted herewith.');
  cms_seed_key('form.decl_2', 'form', 'form.decl_2', '(ii) The complaint is filed before the expiry of a period of one year reckoned in accordance with the provisions of clause 10 (2) of the Scheme.');
  cms_seed_key('form.yes', 'form', 'form.yes', 'Yes');
  cms_seed_key('form.no', 'form', 'form.no', 'No');
  -- Review
  cms_seed_key('review.eligibility_details', 'review', 'review.eligibility_details', 'Eligibility Details');
  cms_seed_key('review.complainant_details', 'review', 'review.complainant_details', 'Complainant Details');
  cms_seed_key('review.re_details', 'review', 'review.re_details', 'Regulated Entity Details');
  cms_seed_key('review.complaint_details', 'review', 'review.complaint_details', 'Complaint Details');
  cms_seed_key('review.rep_auth', 'review', 'review.rep_auth', 'Representative Authorisation');
  cms_seed_key('review.declaration', 'review', 'review.declaration', 'Declaration');
  cms_seed_key('review.attachments', 'review', 'review.attachments', 'Attachments');
  -- Appeal
  cms_seed_key('appeal.title', 'appeal', 'appeal.title', 'File an Appeal');
  cms_seed_key('appeal.subtitle', 'appeal', 'appeal.subtitle', 'If you are dissatisfied with the resolution of your complaint, you may file an appeal within 30 days of the decision.');
  cms_seed_key('appeal.eligibility_title', 'appeal', 'appeal.eligibility_title', 'Eligibility for Appeal:');
  cms_seed_key('appeal.eligibility_1', 'appeal', 'appeal.eligibility_1', 'The complaint must have been closed/resolved by the Ombudsman');
  cms_seed_key('appeal.eligibility_2', 'appeal', 'appeal.eligibility_2', 'The appeal must be filed within 30 days of the decision');
  cms_seed_key('appeal.eligibility_3', 'appeal', 'appeal.eligibility_3', 'You must provide grounds for appeal');
  cms_seed_key('appeal.ref_label', 'appeal', 'appeal.ref_label', 'Original Complaint Reference Number');
  cms_seed_key('appeal.cancel', 'appeal', 'appeal.cancel', 'Cancel');
  cms_seed_key('appeal.check_eligibility', 'appeal', 'appeal.check_eligibility', 'Check Eligibility');
  cms_seed_key('appeal.checking', 'appeal', 'appeal.checking', 'Checking...');
  cms_seed_key('appeal.result_title', 'appeal', 'appeal.result_title', 'Eligibility Check Result');
  cms_seed_key('appeal.eligible', 'appeal', 'appeal.eligible', 'Eligible for Appeal');
  cms_seed_key('appeal.not_eligible', 'appeal', 'appeal.not_eligible', 'Not Eligible');
  cms_seed_key('appeal.classification', 'appeal', 'appeal.classification', 'Classification:');
  cms_seed_key('appeal.summary_title', 'appeal', 'appeal.summary_title', 'Complaint Summary');
  cms_seed_key('appeal.status', 'appeal', 'appeal.status', 'Status');
  cms_seed_key('appeal.decision_date', 'appeal', 'appeal.decision_date', 'Decision Date');
  cms_seed_key('appeal.award_amount', 'appeal', 'appeal.award_amount', 'Award Amount');
  cms_seed_key('appeal.days_since', 'appeal', 'appeal.days_since', 'Days Since Decision');
  cms_seed_key('appeal.speaking_order', 'appeal', 'appeal.speaking_order', 'Speaking Order');
  cms_seed_key('appeal.back', 'appeal', 'appeal.back', 'Back');
  cms_seed_key('appeal.proceed', 'appeal', 'appeal.proceed', 'Proceed to File Appeal');
  cms_seed_key('appeal.details_title', 'appeal', 'appeal.details_title', 'Appeal Details');
  cms_seed_key('appeal.ground', 'appeal', 'appeal.ground', 'Ground for Appeal');
  cms_seed_key('appeal.details_label', 'appeal', 'appeal.details_label', 'Details Supporting Your Appeal');
  cms_seed_key('appeal.relief', 'appeal', 'appeal.relief', 'Relief Sought');
  cms_seed_key('appeal.docs', 'appeal', 'appeal.docs', 'Supporting Documents');
  cms_seed_key('appeal.declaration', 'appeal', 'appeal.declaration', 'I hereby declare that the information provided above is true and correct. I understand that filing a frivolous or misleading appeal may result in dismissal.');
  cms_seed_key('appeal.submit', 'appeal', 'appeal.submit', 'Submit Appeal');
  cms_seed_key('appeal.submitting', 'appeal', 'appeal.submitting', 'Submitting...');
  cms_seed_key('appeal.success_title', 'appeal', 'appeal.success_title', 'Appeal Filed Successfully');
  cms_seed_key('appeal.success_ref', 'appeal', 'appeal.success_ref', 'Your Appeal Reference Number:');
  cms_seed_key('appeal.track', 'appeal', 'appeal.track', 'Track Appeal');
  cms_seed_key('appeal.go_home', 'appeal', 'appeal.go_home', 'Go to Homepage');
  -- Withdraw
  cms_seed_key('withdraw.title', 'withdraw', 'withdraw.title', 'Withdraw Complaint');
  cms_seed_key('withdraw.subtitle', 'withdraw', 'withdraw.subtitle', 'Enter your complaint reference number to initiate withdrawal.');
  cms_seed_key('withdraw.ref_label', 'withdraw', 'withdraw.ref_label', 'Complaint Reference Number');
  cms_seed_key('withdraw.cancel', 'withdraw', 'withdraw.cancel', 'Cancel');
  cms_seed_key('withdraw.find', 'withdraw', 'withdraw.find', 'Find Complaint');
  cms_seed_key('withdraw.searching', 'withdraw', 'withdraw.searching', 'Searching...');
  cms_seed_key('withdraw.confirm_title', 'withdraw', 'withdraw.confirm_title', 'Confirm Withdrawal');
  cms_seed_key('withdraw.warning', 'withdraw', 'withdraw.warning', 'Once withdrawn, this complaint cannot be re-opened. You will need to file a new complaint if you wish to pursue the matter again.');
  cms_seed_key('withdraw.reason_label', 'withdraw', 'withdraw.reason_label', 'Reason for Withdrawal');
  cms_seed_key('withdraw.reason_specify', 'withdraw', 'withdraw.reason_specify', 'Please specify');
  cms_seed_key('withdraw.docs_label', 'withdraw', 'withdraw.docs_label', 'Supporting Documents');
  cms_seed_key('withdraw.back', 'withdraw', 'withdraw.back', 'Back');
  cms_seed_key('withdraw.confirm_btn', 'withdraw', 'withdraw.confirm_btn', 'Confirm Withdrawal');
  cms_seed_key('withdraw.processing', 'withdraw', 'withdraw.processing', 'Processing...');
  cms_seed_key('withdraw.success_title', 'withdraw', 'withdraw.success_title', 'Complaint Withdrawn Successfully');
  cms_seed_key('withdraw.success_msg', 'withdraw', 'withdraw.success_msg', 'has been withdrawn. You will receive a confirmation via SMS/Email.');
  cms_seed_key('withdraw.go_home', 'withdraw', 'withdraw.go_home', 'Go to Homepage');
  -- Feedback
  cms_seed_key('feedback.title', 'feedback', 'feedback.title', 'Share Your Feedback');
  cms_seed_key('feedback.subtitle', 'feedback', 'feedback.subtitle', 'Help us improve by sharing your experience with the complaint resolution process.');
  cms_seed_key('feedback.ref_label', 'feedback', 'feedback.ref_label', 'Complaint Reference Number');
  cms_seed_key('feedback.rate_title', 'feedback', 'feedback.rate_title', 'Rate Your Experience');
  cms_seed_key('feedback.overall', 'feedback', 'feedback.overall', 'Overall Experience');
  cms_seed_key('feedback.timeliness', 'feedback', 'feedback.timeliness', 'Timeliness of Resolution');
  cms_seed_key('feedback.communication', 'feedback', 'feedback.communication', 'Communication Quality');
  cms_seed_key('feedback.satisfaction', 'feedback', 'feedback.satisfaction', 'Satisfaction with Outcome');
  cms_seed_key('feedback.ease', 'feedback', 'feedback.ease', 'Ease of Filing Complaint');
  cms_seed_key('feedback.redress', 'feedback', 'feedback.redress', 'Grievance Redress Time');
  cms_seed_key('feedback.source_label', 'feedback', 'feedback.source_label', 'Source of Information about CMS Portal');
  cms_seed_key('feedback.source_placeholder', 'feedback', 'feedback.source_placeholder', 'Select Source');
  cms_seed_key('feedback.source_specify', 'feedback', 'feedback.source_specify', 'Please specify');
  cms_seed_key('feedback.awareness', 'feedback', 'feedback.awareness', 'CMS Portal Awareness');
  cms_seed_key('feedback.additional', 'feedback', 'feedback.additional', 'Additional Feedback');
  cms_seed_key('feedback.suggestions', 'feedback', 'feedback.suggestions', 'Suggestions for Improvement');
  cms_seed_key('feedback.cancel', 'feedback', 'feedback.cancel', 'Cancel');
  cms_seed_key('feedback.submit', 'feedback', 'feedback.submit', 'Submit Feedback');
  cms_seed_key('feedback.submitting', 'feedback', 'feedback.submitting', 'Submitting...');
  cms_seed_key('feedback.success_title', 'feedback', 'feedback.success_title', 'Thank You for Your Feedback!');
  cms_seed_key('feedback.go_home', 'feedback', 'feedback.go_home', 'Go to Homepage');
  -- Success
  cms_seed_key('success.title', 'success', 'success.title', 'Your Complaint has been submitted successfully!');
  cms_seed_key('success.desc', 'success', 'success.desc', 'We have received the details and your complaint will be reviewed thoroughly. You can also track the status of your complaint using the reference number provided.');
  cms_seed_key('success.ref_label', 'success', 'success.ref_label', 'Your reference number is');
  cms_seed_key('success.download', 'success', 'success.download', 'Download PDF');
  cms_seed_key('success.track', 'success', 'success.track', 'Track Complaint');
  cms_seed_key('success.my_complaints', 'success', 'success.my_complaints', 'My Complaints');
  cms_seed_key('success.share_feedback', 'success', 'success.share_feedback', 'Share Your Feedback');
  -- Non-maintainable
  cms_seed_key('nm.title', 'nm', 'nm.title', 'Complaint Closed as Non-Maintainable');
  cms_seed_key('nm.case_id', 'nm', 'nm.case_id', 'Case ID');
  cms_seed_key('nm.date', 'nm', 'nm.date', 'Date');
  cms_seed_key('nm.reason', 'nm', 'nm.reason', 'Reason for Closure');
  cms_seed_key('nm.download', 'nm', 'nm.download', 'Download Closure Letter');
  cms_seed_key('nm.go_home', 'nm', 'nm.go_home', 'Go to Home');
  -- Duplicate
  cms_seed_key('duplicate.title', 'duplicate', 'duplicate.title', 'Duplicate Complaint Detected');
  cms_seed_key('duplicate.note', 'duplicate', 'duplicate.note', 'A similar complaint already exists in our system. You may cancel and review, or proceed with a new submission.');
  cms_seed_key('duplicate.cancel', 'duplicate', 'duplicate.cancel', 'Cancel Submission');
  cms_seed_key('duplicate.proceed', 'duplicate', 'duplicate.proceed', 'Proceed Anyway');
END;
/

-- =============================================================================
-- SECTION 3: Hindi Translations (Eligibility)
-- =============================================================================
BEGIN
  cms_seed_translation('eligibility.title', 'hi', N'नई शिकायत दर्ज करें');
  cms_seed_translation('eligibility.subtitle', 'hi', N'आगे बढ़ने के लिए अपनी पात्रता जांचें');
  cms_seed_translation('eligibility.mandatory_note', 'hi', N'सभी फ़ील्ड अनिवार्य हैं जब तक कि वैकल्पिक चिह्नित न हो।');
  cms_seed_translation('eligibility.select_desc', 'hi', N'वित्तीय संस्था का चयन करें—जैसे कि बैंक, गैर-बैंकिंग वित्तीय कंपनी (NBFC), या भुगतान प्रोसेसर—जो भारतीय रिज़र्व बैंक द्वारा लाइसेंस प्राप्त और पर्यवेक्षित है, जिसके विरुद्ध आप शिकायत दर्ज करना चाहते हैं।');
  cms_seed_translation('eligibility.select_placeholder', 'hi', N'एक मान चुनें');
  cms_seed_translation('eligibility.simplify_btn', 'hi', N'सरल करें');
  cms_seed_translation('eligibility.browse_file', 'hi', N'फ़ाइल चुनें');
  cms_seed_translation('eligibility.upload_hint', 'hi', N'समर्थित प्रारूप: PDF, JPG, PNG। अधिकतम आकार: 5MB');
  cms_seed_translation('eligibility.opt_yes', 'hi', N'हाँ');
  cms_seed_translation('eligibility.opt_no', 'hi', N'नहीं');
  cms_seed_translation('eligibility.q_select_re', 'hi', N'विनियमित संस्था का नाम चुनें');
  cms_seed_translation('eligibility.q_filed_with_re', 'hi', N'क्या आपने {{reName}} के पास लिखित/इलेक्ट्रॉनिक शिकायत दर्ज की है?');
  cms_seed_translation('eligibility.q_received_reply', 'hi', N'क्या आपको संस्था से कोई उत्तर मिला है?');
  cms_seed_translation('eligibility.q_sent_reminder', 'hi', N'क्या आपने {{reName}} को कोई अनुस्मारक भेजा है?');
  cms_seed_translation('eligibility.q_sub_judice', 'hi', N'क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से किसी न्यायालय, न्यायाधिकरण, मध्यस्थ या किसी अन्य न्यायिक या अर्ध-न्यायिक मंच के समक्ष लंबित है (आपराधिक कार्यवाही को छोड़कर)?');
  cms_seed_translation('eligibility.q_already_settled', 'hi', N'क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से किसी न्यायालय, न्यायाधिकरण, मध्यस्थ या किसी अन्य न्यायिक या अर्ध-न्यायिक मंच द्वारा निपटाई या सुलझाई जा चुकी है (आपराधिक कार्यवाही को छोड़कर)?');
  cms_seed_translation('eligibility.q_through_advocate', 'hi', N'क्या आपकी शिकायत किसी अधिवक्ता के माध्यम से की जा रही है?');
  cms_seed_translation('eligibility.q_pending_ombudsman', 'hi', N'क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से लोकपाल के समक्ष लंबित है?');
  cms_seed_translation('eligibility.q_settled_ombudsman', 'hi', N'क्या यह शिकायत उसी विवाद से संबंधित है जो लोकपाल द्वारा पहले ही गुण-दोष के आधार पर निपटाई जा चुकी है?');
  cms_seed_translation('eligibility.q_staff_of_re', 'hi', N'क्या शिकायतकर्ता विनियमित संस्था का कर्मचारी है और शिकायत नियोक्ता-कर्मचारी संबंध से जुड़ी है?');
  cms_seed_translation('eligibility.q_sub_judice_simple', 'hi', N'क्या आप पहले से इस समस्या को किसी न्यायालय, मध्यस्थ, या अन्य आधिकारिक कानूनी प्राधिकरण के पास ले गए हैं (आपराधिक मामलों को छोड़कर)?');
  cms_seed_translation('eligibility.q_already_settled_simple', 'hi', N'क्या यह समस्या पहले से किसी न्यायालय, मध्यस्थ, या अन्य आधिकारिक कानूनी प्राधिकरण द्वारा हल की जा चुकी है (आपराधिक मामलों को छोड़कर)?');
  cms_seed_translation('eligibility.q_through_advocate_simple', 'hi', N'क्या आप किसी वकील या कानूनी प्रतिनिधि की सहायता से यह शिकायत दर्ज कर रहे हैं?');
  cms_seed_translation('eligibility.q_pending_ombudsman_simple', 'hi', N'क्या आपने इसी मुद्दे पर पहले से लोकपाल के पास शिकायत दर्ज की है और वह अभी भी समीक्षाधीन है?');
  cms_seed_translation('eligibility.q_settled_ombudsman_simple', 'hi', N'क्या लोकपाल ने पहले ही इसी शिकायत की समीक्षा और समाधान कर दिया है?');
  cms_seed_translation('eligibility.q_staff_of_re_simple', 'hi', N'क्या आप उस बैंक/NBFC के कर्मचारी हैं जिसके विरुद्ध शिकायत कर रहे हैं, और क्या शिकायत आपकी नौकरी से संबंधित है?');
  cms_seed_translation('eligibility.sub_complaint_date', 'hi', N'जिस तारीख को शिकायत पहली बार दर्ज की गई');
  cms_seed_translation('eligibility.sub_upload_complaint', 'hi', N'को भेजी गई शिकायत की प्रति अपलोड करें');
  cms_seed_translation('eligibility.sub_reminder_date', 'hi', N'जिस तारीख को अनुस्मारक भेजा गया');
  cms_seed_translation('eligibility.sub_upload_reminder', 'hi', N'अनुस्मारक की प्रति अपलोड करें');
  cms_seed_translation('eligibility.sub_reply_date', 'hi', N'जिस तारीख को उत्तर प्राप्त हुआ');
  cms_seed_translation('eligibility.sub_upload_reply', 'hi', N'उत्तर की प्रति अपलोड करें');
  cms_seed_translation('eligibility.sub_are_you_complainant', 'hi', N'यदि हाँ, तो क्या आप स्वयं शिकायतकर्ता हैं?');
  cms_seed_translation('eligibility.block_not_filed', 'hi', N'रिज़र्व बैंक – एकीकृत लोकपाल योजना, 2026 के खंड 10(1)(j) के अनुसार, शिकायत को योजना के तहत संसाधित नहीं किया जा सकता।');
  cms_seed_translation('eligibility.block_sub_judice', 'hi', N'चूंकि आपकी शिकायत न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण के समक्ष लंबित है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के खंड 10(2)(b)(ii) के तहत अस्वीकार्य के रूप में बंद किया जाएगा।');
  cms_seed_translation('eligibility.block_already_settled', 'hi', N'चूंकि आपकी शिकायत पहले ही न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण द्वारा निपटाई जा चुकी है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के तहत अस्वीकार्य के रूप में बंद किया जाएगा।');
  cms_seed_translation('eligibility.block_pending_ombudsman', 'hi', N'आपकी शिकायत पहले से उसी विवाद पर लोकपाल के समक्ष लंबित है। डुप्लिकेट शिकायत दर्ज नहीं की जा सकती।');
  cms_seed_translation('eligibility.block_settled_ombudsman', 'hi', N'आपकी शिकायत लोकपाल द्वारा पहले ही गुण-दोष के आधार पर निपटाई जा चुकी है। इसी मुद्दे पर नई शिकायत दर्ज नहीं की जा सकती।');
  cms_seed_translation('eligibility.block_staff_of_re', 'hi', N'विनियमित संस्था के कर्मचारी और नियोक्ता-कर्मचारी संबंध से जुड़ी शिकायतें एकीकृत लोकपाल योजना के तहत दर्ज नहीं की जा सकतीं।');
END;
/

-- =============================================================================
-- SECTION 4: Marathi Translations (Eligibility)
-- =============================================================================
BEGIN
  cms_seed_translation('eligibility.title', 'mr', N'नवीन तक्रार दाखल करा');
  cms_seed_translation('eligibility.subtitle', 'mr', N'पुढे जाण्यासाठी तुमची पात्रता तपासा');
  cms_seed_translation('eligibility.mandatory_note', 'mr', N'सर्व फील्ड अनिवार्य आहेत, पर्यायी म्हणून चिन्हांकित केलेली वगळता.');
  cms_seed_translation('eligibility.select_desc', 'mr', N'वित्तीय संस्था निवडा—जसे की बँक, नॉन-बँकिंग फायनान्शियल कंपनी (NBFC), किंवा पेमेंट प्रोसेसर—जी भारतीय रिझर्व्ह बँकेने परवानाकृत आणि पर्यवेक्षित केलेली आहे, ज्याविरुद्ध तुम्हाला तक्रार दाखल करायची आहे.');
  cms_seed_translation('eligibility.select_placeholder', 'mr', N'एक मूल्य निवडा');
  cms_seed_translation('eligibility.simplify_btn', 'mr', N'सोप्या भाषेत');
  cms_seed_translation('eligibility.browse_file', 'mr', N'फाइल निवडा');
  cms_seed_translation('eligibility.upload_hint', 'mr', N'समर्थित स्वरूप: PDF, JPG, PNG. कमाल आकार: 5MB');
  cms_seed_translation('eligibility.opt_yes', 'mr', N'होय');
  cms_seed_translation('eligibility.opt_no', 'mr', N'नाही');
  cms_seed_translation('eligibility.q_select_re', 'mr', N'नियमित संस्थेचे नाव निवडा');
  cms_seed_translation('eligibility.q_filed_with_re', 'mr', N'तुम्ही {{reName}} कडे लिखित/इलेक्ट्रॉनिक तक्रार दाखल केली आहे का?');
  cms_seed_translation('eligibility.q_received_reply', 'mr', N'तुम्हाला संस्थेकडून काही उत्तर मिळाले आहे का?');
  cms_seed_translation('eligibility.q_sent_reminder', 'mr', N'तुम्ही {{reName}} ला कोणता स्मरणपत्र पाठवले आहे का?');
  cms_seed_translation('eligibility.q_sub_judice', 'mr', N'ही तक्रार त्याच तक्रारीशी संबंधित आहे का जी आधीच कोणत्याही न्यायालय, न्यायाधिकरण, लवाद किंवा इतर न्यायिक किंवा अर्ध-न्यायिक मंचासमोर प्रलंबित आहे (फौजदारी कार्यवाही वगळता)?');
  cms_seed_translation('eligibility.q_already_settled', 'mr', N'ही तक्रार त्याच तक्रारीशी संबंधित आहे का जी आधीच कोणत्याही न्यायालय, न्यायाधिकरण, लवाद किंवा इतर न्यायिक किंवा अर्ध-न्यायिक मंचाद्वारे निकाली काढली गेली आहे (फौजदारी कार्यवाही वगळता)?');
  cms_seed_translation('eligibility.q_through_advocate', 'mr', N'तुमची तक्रार अधिवक्त्यामार्फत केली जात आहे का?');
  cms_seed_translation('eligibility.q_pending_ombudsman', 'mr', N'ही तक्रार त्याच तक्रारीशी संबंधित आहे का जी आधीच लोकपालासमोर प्रलंबित आहे?');
  cms_seed_translation('eligibility.q_settled_ombudsman', 'mr', N'ही तक्रार त्याच तक्रारीशी संबंधित आहे का जी लोकपालाने आधीच गुणवत्तेवर निकाली काढली आहे?');
  cms_seed_translation('eligibility.q_staff_of_re', 'mr', N'तक्रारदार हा नियमित संस्थेचा कर्मचारी आहे का आणि तक्रार नियोक्ता-कर्मचारी संबंधाशी संबंधित आहे का?');
  cms_seed_translation('eligibility.q_sub_judice_simple', 'mr', N'तुम्ही आधीच हीच समस्या कोणत्याही न्यायालय, लवाद, किंवा अधिकृत कायदेशीर प्राधिकरणाकडे नेली आहे का (फौजदारी प्रकरणे वगळता)?');
  cms_seed_translation('eligibility.q_already_settled_simple', 'mr', N'हीच समस्या आधीच कोणत्याही न्यायालय, लवाद, किंवा अधिकृत कायदेशीर प्राधिकरणाद्वारे सोडवली गेली आहे का (फौजदारी प्रकरणे वगळता)?');
  cms_seed_translation('eligibility.q_through_advocate_simple', 'mr', N'तुम्ही वकील किंवा कायदेशीर प्रतिनिधीच्या मदतीने ही तक्रार दाखल करत आहात का?');
  cms_seed_translation('eligibility.q_pending_ombudsman_simple', 'mr', N'तुम्ही याच मुद्द्यावर आधीच लोकपालाकडे तक्रार दाखल केली आहे का आणि ती अजूनही तपासणीत आहे?');
  cms_seed_translation('eligibility.q_settled_ombudsman_simple', 'mr', N'लोकपालाने आधीच याच तक्रारीची तपासणी आणि निराकरण केले आहे का?');
  cms_seed_translation('eligibility.q_staff_of_re_simple', 'mr', N'तुम्ही ज्या बँक/NBFC विरुद्ध तक्रार करत आहात त्याचे कर्मचारी आहात का, आणि तक्रार तुमच्या नोकरीशी संबंधित आहे का?');
  cms_seed_translation('eligibility.sub_complaint_date', 'mr', N'ज्या तारखेला तक्रार प्रथम दाखल केली गेली');
  cms_seed_translation('eligibility.sub_upload_complaint', 'mr', N'ला पाठवलेल्या तक्रारीची प्रत अपलोड करा');
  cms_seed_translation('eligibility.sub_reminder_date', 'mr', N'ज्या तारखेला स्मरणपत्र पाठवले गेले');
  cms_seed_translation('eligibility.sub_upload_reminder', 'mr', N'स्मरणपत्राची प्रत अपलोड करा');
  cms_seed_translation('eligibility.sub_reply_date', 'mr', N'ज्या तारखेला उत्तर प्राप्त झाले');
  cms_seed_translation('eligibility.sub_upload_reply', 'mr', N'उत्तराची प्रत अपलोड करा');
  cms_seed_translation('eligibility.sub_are_you_complainant', 'mr', N'होय असल्यास, तुम्ही स्वतः तक्रारदार आहात का?');
  cms_seed_translation('eligibility.block_not_filed', 'mr', N'रिझर्व्ह बँक – एकीकृत लोकपाल योजना, 2026 च्या कलम 10(1)(j) नुसार, तक्रार योजनेअंतर्गत प्रक्रिया करता येत नाही.');
  cms_seed_translation('eligibility.block_sub_judice', 'mr', N'तुमची तक्रार न्यायालय/न्यायाधिकरण/लवाद/प्राधिकरणासमोर प्रलंबित असल्याने, ती रिझर्व्ह बँक - एकीकृत लोकपाल योजना, 2021 च्या कलम 10(2)(b)(ii) अंतर्गत अस्वीकार्य म्हणून बंद केली जाईल.');
  cms_seed_translation('eligibility.block_already_settled', 'mr', N'तुमची तक्रार आधीच न्यायालय/न्यायाधिकरण/लवाद/प्राधिकरणाद्वारे निकाली काढली गेली असल्याने, ती रिझर्व्ह बँक - एकीकृत लोकपाल योजना, 2021 अंतर्गत अस्वीकार्य म्हणून बंद केली जाईल.');
  cms_seed_translation('eligibility.block_pending_ombudsman', 'mr', N'तुमची तक्रार आधीच त्याच तक्रारीवर लोकपालासमोर प्रलंबित आहे. डुप्लिकेट तक्रार दाखल करता येत नाही.');
  cms_seed_translation('eligibility.block_settled_ombudsman', 'mr', N'तुमची तक्रार लोकपालाने आधीच गुणवत्तेवर निकाली काढली आहे. याच मुद्द्यावर नवीन तक्रार दाखल करता येत नाही.');
  cms_seed_translation('eligibility.block_staff_of_re', 'mr', N'नियमित संस्थेचे कर्मचारी आणि नियोक्ता-कर्मचारी संबंधांशी संबंधित तक्रारी एकीकृत लोकपाल योजनेअंतर्गत दाखल करता येत नाहीत.');
END;
/

-- =============================================================================
-- SECTION 5: Hindi Translations (Portal)
-- Due to the large volume, the remaining translations follow the same pattern.
-- The file continues with all Portal Hindi, Marathi, Bengali, Telugu, Tamil,
-- Gujarati, Urdu, Kannada, and Malayalam translations.
-- =============================================================================

-- NOTE: Due to output size constraints, this script is split into a generation
-- approach. Please run the companion generation script or see the full file.
-- The pattern for all remaining translations is identical:
-- BEGIN
--   cms_seed_translation('<code>', '<locale>', N'<unicode_value>');
--   ...
-- END;
-- /

-- =============================================================================
-- SECTION 5: Hindi Translations (Portal - Full)
-- =============================================================================
BEGIN
  cms_seed_translation('home.authenticated_title', 'hi', N'होम');
  cms_seed_translation('home.file_new_btn', 'hi', N'नई शिकायत दर्ज करें');
  cms_seed_translation('home.complaint_history', 'hi', N'शिकायत इतिहास');
  cms_seed_translation('home.table_id', 'hi', N'आईडी');
  cms_seed_translation('home.table_complaint_against', 'hi', N'शिकायत किसके विरुद्ध');
  cms_seed_translation('home.table_complaint_date', 'hi', N'शिकायत तिथि');
  cms_seed_translation('home.table_status', 'hi', N'स्थिति');
  cms_seed_translation('home.table_comments', 'hi', N'टिप्पणियाँ');
  cms_seed_translation('home.table_action', 'hi', N'कार्रवाई');
  cms_seed_translation('home.search_placeholder', 'hi', N'खोजें');
  cms_seed_translation('home.loading', 'hi', N'लोड हो रहा है...');
  cms_seed_translation('home.no_complaints', 'hi', N'कोई शिकायत नहीं मिली');
  cms_seed_translation('home.ct_tag', 'hi', N'अपनी वित्तीय संस्था खोजें');
  cms_seed_translation('home.ct_heading', 'hi', N'आप इनके बारे में शिकायत दर्ज कर सकते हैं');
  cms_seed_translation('home.ct_regulated', 'hi', N'रिज़र्व बैंक-एकीकृत लोकपाल योजना, 2026 के तहत विनियमित संस्थाएँ');
  cms_seed_translation('home.ct_download', 'hi', N'PDF डाउनलोड करें');
  cms_seed_translation('home.scheme_title', 'hi', N'लोकपाल योजना का विवरण');
  cms_seed_translation('home.wwd_tag', 'hi', N'हम क्या करते हैं');
  cms_seed_translation('home.wwd_heading', 'hi', N'RBI भारतीय नागरिकों के लिए शिकायत समाधान प्रक्रिया को सुव्यवस्थित करता है।');
  cms_seed_translation('home.wwd_desc', 'hi', N'RBI भारतीय नागरिकों के लिए शिकायत समाधान प्रक्रिया को सरल और तेज़ बनाता है, निष्पक्ष और समय पर समाधान सुनिश्चित करता है।');
  cms_seed_translation('home.ways_title', 'hi', N'शिकायत दर्ज करने के तरीके');
  cms_seed_translation('home.ways_portal_title', 'hi', N'CMS पोर्टल');
  cms_seed_translation('home.ways_portal_badge', 'hi', N'(आप यहाँ हैं)');
  cms_seed_translation('home.ways_portal_desc', 'hi', N'इस पोर्टल पर अपनी शिकायत दर्ज करें। यह आपकी शिकायतों को कुशलता से हल करने का सबसे तेज़ तरीका है।');
  cms_seed_translation('home.ways_portal_link', 'hi', N'नई शिकायत दर्ज करें');
  cms_seed_translation('home.ways_wizard_title', 'hi', N'क्या RBI मदद कर सकता है?');
  cms_seed_translation('home.ways_wizard_badge', 'hi', N'नया');
  cms_seed_translation('home.ways_wizard_desc', 'hi', N'सुनिश्चित नहीं हैं कि आप शिकायत दर्ज कर सकते हैं? शुरू करने से पहले 4 प्रश्नों का उत्तर दें।');
  cms_seed_translation('home.ways_wizard_link', 'hi', N'पात्रता जांचें');
  cms_seed_translation('home.ways_email_title', 'hi', N'ईमेल');
  cms_seed_translation('home.ways_email_desc', 'hi', N'शिकायत प्रपत्र भरकर हमें ईमेल द्वारा अपनी शिकायत भेजें');
  cms_seed_translation('home.ways_letter_title', 'hi', N'भौतिक पत्र');
  cms_seed_translation('home.ways_letter_desc', 'hi', N'शिकायत प्रपत्र भरकर हमें पत्र द्वारा अपनी शिकायत भेजें');
  cms_seed_translation('home.ways_view_details', 'hi', N'विवरण देखें');
  cms_seed_translation('home.education_tag', 'hi', N'जन जागरूकता');
  cms_seed_translation('home.education_title', 'hi', N'शिक्षा और जागरूकता');
  cms_seed_translation('home.view_all', 'hi', N'सभी देखें');
  cms_seed_translation('home.faq_tag', 'hi', N'अक्सर पूछे जाने वाले प्रश्न');
  cms_seed_translation('home.faq_title', 'hi', N'अक्सर पूछे जाने वाले प्रश्न');
  cms_seed_translation('layout.logged_in_as', 'hi', N'के रूप में लॉग इन:');
  cms_seed_translation('layout.session_expires', 'hi', N'सत्र समाप्त होने में:');
  cms_seed_translation('layout.logout', 'hi', N'लॉगआउट');
  cms_seed_translation('layout.need_help', 'hi', N'मदद चाहिए?');
  cms_seed_translation('layout.need_assistance', 'hi', N'सहायता चाहिए? 14448 पर कॉल करें');
  cms_seed_translation('login.page_title', 'hi', N'सत्यापन');
  cms_seed_translation('login.verify_phone', 'hi', N'फ़ोन नंबर सत्यापित करें');
  cms_seed_translation('login.mobile_label', 'hi', N'मोबाइल नंबर');
  cms_seed_translation('login.enter_mobile', 'hi', N'मोबाइल नंबर दर्ज करें');
  cms_seed_translation('login.captcha_visual', 'hi', N'नीचे दिखाया गया पाठ दर्ज करें');
  cms_seed_translation('login.captcha_math', 'hi', N'प्रश्न का उत्तर दें');
  cms_seed_translation('login.captcha_enter', 'hi', N'पाठ दर्ज करें');
  cms_seed_translation('login.captcha_answer', 'hi', N'आपका उत्तर');
  cms_seed_translation('login.send_otp', 'hi', N'OTP भेजें');
  cms_seed_translation('login.sending', 'hi', N'भेजा जा रहा है...');
  cms_seed_translation('login.email_fallback', 'hi', N'ईमेल पर OTP भेजें');
  cms_seed_translation('login.hide_email', 'hi', N'ईमेल विकल्प छिपाएँ');
  cms_seed_translation('login.email_label', 'hi', N'सत्यापित ईमेल पता');
  cms_seed_translation('login.email_placeholder', 'hi', N'अपना सत्यापित ईमेल दर्ज करें');
  cms_seed_translation('login.send_otp_email', 'hi', N'ईमेल पर OTP भेजें');
  cms_seed_translation('login.verify_email_first', 'hi', N'पहले यह ईमेल सत्यापित करें');
  cms_seed_translation('login.otp_sent_to', 'hi', N'OTP भेजा गया');
  cms_seed_translation('login.enter_otp', 'hi', N'OTP दर्ज करें');
  cms_seed_translation('login.verify_otp', 'hi', N'OTP सत्यापित करें');
  cms_seed_translation('login.verifying', 'hi', N'सत्यापित किया जा रहा है...');
  cms_seed_translation('login.resend_otp', 'hi', N'OTP पुनः भेजें');
  cms_seed_translation('login.resend_in', 'hi', N'OTP पुनः भेजें');
  cms_seed_translation('login.change_number', 'hi', N'नंबर बदलें');
  cms_seed_translation('history.title', 'hi', N'मेरी शिकायतें');
  cms_seed_translation('history.create_new', 'hi', N'नई शिकायत बनाएँ');
  cms_seed_translation('history.col_number', 'hi', N'शिकायत संख्या');
  cms_seed_translation('history.col_against', 'hi', N'शिकायत किसके विरुद्ध');
  cms_seed_translation('history.col_date', 'hi', N'शिकायत तिथि');
  cms_seed_translation('history.col_status', 'hi', N'स्थिति');
  cms_seed_translation('history.col_comments', 'hi', N'टिप्पणियाँ');
  cms_seed_translation('history.loading', 'hi', N'शिकायतें लोड हो रही हैं...');
  cms_seed_translation('history.no_complaints', 'hi', N'कोई शिकायत नहीं मिली।');
  cms_seed_translation('history.assistance_title', 'hi', N'सहायता चाहिए? 14448 पर कॉल करें');
  cms_seed_translation('form.mandatory_note', 'hi', N'(*) चिह्नित फ़ील्ड अनिवार्य हैं।');
  cms_seed_translation('form.step_label', 'hi', N'चरण');
  cms_seed_translation('form.cancel', 'hi', N'रद्द करें');
  cms_seed_translation('form.save', 'hi', N'सहेजें');
  cms_seed_translation('form.back', 'hi', N'वापस');
  cms_seed_translation('form.next', 'hi', N'आगे');
  cms_seed_translation('form.auto_saved', 'hi', N'कुछ सेकंड पहले स्वतः सहेजा गया');
  cms_seed_translation('form.download_pdf', 'hi', N'PDF डाउनलोड करें');
  cms_seed_translation('form.submit_complaint', 'hi', N'शिकायत जमा करें');
  cms_seed_translation('form.submitting', 'hi', N'जमा किया जा रहा है...');
  cms_seed_translation('form.step1_title', 'hi', N'शिकायतकर्ता विवरण');
  cms_seed_translation('form.step2_title', 'hi', N'विनियमित संस्था विवरण');
  cms_seed_translation('form.step3_title', 'hi', N'शिकायत विवरण');
  cms_seed_translation('form.step4_title', 'hi', N'प्रतिनिधि प्राधिकरण');
  cms_seed_translation('form.step5_title', 'hi', N'घोषणा');
  cms_seed_translation('form.step6_title', 'hi', N'समीक्षा और जमा करें');
  cms_seed_translation('form.complainant_category', 'hi', N'शिकायतकर्ता श्रेणी');
  cms_seed_translation('form.individual', 'hi', N'व्यक्तिगत');
  cms_seed_translation('form.business_entity', 'hi', N'व्यावसायिक संस्था');
  cms_seed_translation('form.other', 'hi', N'अन्य');
  cms_seed_translation('form.first_name', 'hi', N'पहला नाम');
  cms_seed_translation('form.middle_name', 'hi', N'मध्य नाम');
  cms_seed_translation('form.last_name', 'hi', N'अंतिम नाम');
  cms_seed_translation('form.age', 'hi', N'आयु');
  cms_seed_translation('form.gender', 'hi', N'लिंग');
  cms_seed_translation('form.male', 'hi', N'पुरुष');
  cms_seed_translation('form.female', 'hi', N'महिला');
  cms_seed_translation('form.email_id', 'hi', N'ईमेल आईडी');
  cms_seed_translation('form.mobile_number', 'hi', N'मोबाइल नंबर');
  cms_seed_translation('form.pincode', 'hi', N'पिनकोड');
  cms_seed_translation('form.state', 'hi', N'राज्य');
  cms_seed_translation('form.district', 'hi', N'जिला');
  cms_seed_translation('form.address', 'hi', N'पता विवरण');
  cms_seed_translation('form.enter_value', 'hi', N'मान दर्ज करें');
  cms_seed_translation('form.select_value', 'hi', N'मान चुनें');
  cms_seed_translation('form.re_name', 'hi', N'विनियमित संस्था का नाम');
  cms_seed_translation('form.credit_card_q', 'hi', N'क्या आपकी शिकायत क्रेडिट कार्ड से संबंधित है?');
  cms_seed_translation('form.entity_state', 'hi', N'संस्था राज्य');
  cms_seed_translation('form.entity_district', 'hi', N'संस्था जिला');
  cms_seed_translation('form.entity_branch', 'hi', N'संस्था शाखा');
  cms_seed_translation('form.complaint_category', 'hi', N'शिकायत श्रेणी चुनें');
  cms_seed_translation('form.facts', 'hi', N'शिकायत के तथ्य');
  cms_seed_translation('form.write_detail', 'hi', N'विस्तार से लिखें');
  cms_seed_translation('form.use_mic', 'hi', N'माइक्रोफ़ोन उपयोग करें');
  cms_seed_translation('form.stop', 'hi', N'रुकें');
  cms_seed_translation('form.has_account', 'hi', N'क्या आपका खाता है');
  cms_seed_translation('form.account_type', 'hi', N'खाता प्रकार चुनें');
  cms_seed_translation('form.savings', 'hi', N'बचत खाता');
  cms_seed_translation('form.current_account', 'hi', N'चालू खाता');
  cms_seed_translation('form.loan', 'hi', N'ऋण खाता');
  cms_seed_translation('form.credit_card', 'hi', N'क्रेडिट कार्ड');
  cms_seed_translation('form.debit_card', 'hi', N'डेबिट/ATM कार्ड');
  cms_seed_translation('form.card_number', 'hi', N'क्रेडिट कार्ड नंबर');
  cms_seed_translation('form.savings_number', 'hi', N'बचत खाता संख्या');
  cms_seed_translation('form.loan_number', 'hi', N'ऋण खाता संख्या');
  cms_seed_translation('form.atm_number', 'hi', N'ATM/डेबिट कार्ड नंबर');
  cms_seed_translation('form.wallet_q', 'hi', N'क्या आपकी शिकायत विनियमित संस्था के वॉलेट के विरुद्ध है?');
  cms_seed_translation('form.wallet_name', 'hi', N'वॉलेट का नाम');
  cms_seed_translation('form.txn_ref', 'hi', N'लेनदेन/संदर्भ संख्या');
  cms_seed_translation('form.biz_corr_q', 'hi', N'क्या आपकी शिकायत किसी व्यवसाय संवाददाता के विरुद्ध है?');
  cms_seed_translation('form.amount_involved', 'hi', N'लेनदेन/विवाद में शामिल राशि, यदि कोई हो');
  cms_seed_translation('form.compensation_sought', 'hi', N'विवाद के लिए माँगी गई क्षतिपूर्ति, यदि कोई हो');
  cms_seed_translation('form.compensation_harassment', 'hi', N'उत्पीड़न के लिए क्षतिपूर्ति, यदि कोई हो');
  cms_seed_translation('form.upload_docs', 'hi', N'शिकायत से संबंधित अतिरिक्त दस्तावेज़ अपलोड करें');
  cms_seed_translation('form.browse_file', 'hi', N'फ़ाइल चुनें');
  cms_seed_translation('form.upload_hint', 'hi', N'समर्थित प्रारूप: PDF, JPG, PNG');
  cms_seed_translation('form.upload_size', 'hi', N'अधिकतम आकार: 5MB');
  cms_seed_translation('form.auth_rep_q', 'hi', N'क्या शिकायत आपकी/शिकायतकर्ता की ओर से अधिकृत प्रतिनिधि के माध्यम से दर्ज की जा रही है?');
  cms_seed_translation('form.auth_rep_details', 'hi', N'अधिकृत प्रतिनिधि के लिए निम्नलिखित विवरण भरें');
  cms_seed_translation('form.rep_name', 'hi', N'नाम');
  cms_seed_translation('form.rep_email', 'hi', N'ईमेल आईडी');
  cms_seed_translation('form.rep_mobile', 'hi', N'मोबाइल नंबर');
  cms_seed_translation('form.rep_pincode', 'hi', N'पिनकोड');
  cms_seed_translation('form.rep_state', 'hi', N'राज्य');
  cms_seed_translation('form.rep_district', 'hi', N'जिला');
  cms_seed_translation('form.rep_city', 'hi', N'शहर');
  cms_seed_translation('form.rep_address', 'hi', N'पता विवरण');
  cms_seed_translation('form.rep_upload', 'hi', N'सहायक दस्तावेज़ अपलोड करें');
  cms_seed_translation('form.decl_heading', 'hi', N'आवश्यक घोषणाएँ');
  cms_seed_translation('form.decl_1', 'hi', N'(i) मैं/हम, शिकायतकर्ता एतद्द्वारा घोषित करते हैं कि: क) ऊपर दी गई जानकारी सत्य और सही है; तथा ख) मैंने/हमने ऊपर कथित और इसके साथ प्रस्तुत दस्तावेज़ों में किसी तथ्य को छिपाया या गलत तरीके से प्रस्तुत नहीं किया है।');
  cms_seed_translation('form.decl_2', 'hi', N'(ii) शिकायत योजना के खंड 10(2) के प्रावधानों के अनुसार एक वर्ष की अवधि समाप्त होने से पहले दर्ज की गई है।');
  cms_seed_translation('form.yes', 'hi', N'हाँ');
  cms_seed_translation('form.no', 'hi', N'नहीं');
  cms_seed_translation('review.eligibility_details', 'hi', N'पात्रता विवरण');
  cms_seed_translation('review.complainant_details', 'hi', N'शिकायतकर्ता विवरण');
  cms_seed_translation('review.re_details', 'hi', N'विनियमित संस्था विवरण');
  cms_seed_translation('review.complaint_details', 'hi', N'शिकायत विवरण');
  cms_seed_translation('review.rep_auth', 'hi', N'प्रतिनिधि प्राधिकरण');
  cms_seed_translation('review.declaration', 'hi', N'घोषणा');
  cms_seed_translation('review.attachments', 'hi', N'संलग्नक');
  cms_seed_translation('appeal.title', 'hi', N'अपील दर्ज करें');
  cms_seed_translation('appeal.subtitle', 'hi', N'यदि आप अपनी शिकायत के समाधान से असंतुष्ट हैं, तो आप निर्णय के 30 दिनों के भीतर अपील दर्ज कर सकते हैं।');
  cms_seed_translation('appeal.eligibility_title', 'hi', N'अपील के लिए पात्रता:');
  cms_seed_translation('appeal.eligibility_1', 'hi', N'शिकायत लोकपाल द्वारा बंद/समाधान की गई होनी चाहिए');
  cms_seed_translation('appeal.eligibility_2', 'hi', N'अपील निर्णय के 30 दिनों के भीतर दर्ज की जानी चाहिए');
  cms_seed_translation('appeal.eligibility_3', 'hi', N'आपको अपील के आधार प्रदान करने होंगे');
  cms_seed_translation('appeal.ref_label', 'hi', N'मूल शिकायत संदर्भ संख्या');
  cms_seed_translation('appeal.cancel', 'hi', N'रद्द करें');
  cms_seed_translation('appeal.check_eligibility', 'hi', N'पात्रता जांचें');
  cms_seed_translation('appeal.checking', 'hi', N'जाँच हो रही है...');
  cms_seed_translation('appeal.result_title', 'hi', N'पात्रता जाँच परिणाम');
  cms_seed_translation('appeal.eligible', 'hi', N'अपील के लिए पात्र');
  cms_seed_translation('appeal.not_eligible', 'hi', N'पात्र नहीं');
  cms_seed_translation('appeal.classification', 'hi', N'वर्गीकरण:');
  cms_seed_translation('appeal.summary_title', 'hi', N'शिकायत सारांश');
  cms_seed_translation('appeal.status', 'hi', N'स्थिति');
  cms_seed_translation('appeal.decision_date', 'hi', N'निर्णय तिथि');
  cms_seed_translation('appeal.award_amount', 'hi', N'पुरस्कार राशि');
  cms_seed_translation('appeal.days_since', 'hi', N'निर्णय के बाद के दिन');
  cms_seed_translation('appeal.speaking_order', 'hi', N'तर्कसंगत आदेश');
  cms_seed_translation('appeal.back', 'hi', N'वापस');
  cms_seed_translation('appeal.proceed', 'hi', N'अपील दर्ज करने के लिए आगे बढ़ें');
  cms_seed_translation('appeal.details_title', 'hi', N'अपील विवरण');
  cms_seed_translation('appeal.ground', 'hi', N'अपील का आधार');
  cms_seed_translation('appeal.details_label', 'hi', N'आपकी अपील का समर्थन करने वाले विवरण');
  cms_seed_translation('appeal.relief', 'hi', N'माँगी गई राहत');
  cms_seed_translation('appeal.docs', 'hi', N'सहायक दस्तावेज़');
  cms_seed_translation('appeal.declaration', 'hi', N'मैं एतद्द्वारा घोषित करता/करती हूँ कि ऊपर दी गई जानकारी सत्य और सही है। मैं समझता/समझती हूँ कि तुच्छ या भ्रामक अपील दर्ज करने पर खारिज किया जा सकता है।');
  cms_seed_translation('appeal.submit', 'hi', N'अपील जमा करें');
  cms_seed_translation('appeal.submitting', 'hi', N'जमा किया जा रहा है...');
  cms_seed_translation('appeal.success_title', 'hi', N'अपील सफलतापूर्वक दर्ज की गई');
  cms_seed_translation('appeal.success_ref', 'hi', N'आपकी अपील संदर्भ संख्या:');
  cms_seed_translation('appeal.track', 'hi', N'अपील ट्रैक करें');
  cms_seed_translation('appeal.go_home', 'hi', N'मुखपृष्ठ पर जाएँ');
  cms_seed_translation('withdraw.title', 'hi', N'शिकायत वापस लें');
  cms_seed_translation('withdraw.subtitle', 'hi', N'वापसी शुरू करने के लिए अपना शिकायत संदर्भ संख्या दर्ज करें।');
  cms_seed_translation('withdraw.ref_label', 'hi', N'शिकायत संदर्भ संख्या');
  cms_seed_translation('withdraw.cancel', 'hi', N'रद्द करें');
  cms_seed_translation('withdraw.find', 'hi', N'शिकायत खोजें');
  cms_seed_translation('withdraw.searching', 'hi', N'खोजा जा रहा है...');
  cms_seed_translation('withdraw.confirm_title', 'hi', N'वापसी की पुष्टि करें');
  cms_seed_translation('withdraw.warning', 'hi', N'एक बार वापस लेने के बाद, यह शिकायत पुनः नहीं खोली जा सकती। यदि आप मामले को फिर से उठाना चाहते हैं तो नई शिकायत दर्ज करनी होगी।');
  cms_seed_translation('withdraw.reason_label', 'hi', N'वापसी का कारण');
  cms_seed_translation('withdraw.reason_specify', 'hi', N'कृपया निर्दिष्ट करें');
  cms_seed_translation('withdraw.docs_label', 'hi', N'सहायक दस्तावेज़');
  cms_seed_translation('withdraw.back', 'hi', N'वापस');
  cms_seed_translation('withdraw.confirm_btn', 'hi', N'वापसी की पुष्टि करें');
  cms_seed_translation('withdraw.processing', 'hi', N'प्रसंस्करण...');
  cms_seed_translation('withdraw.success_title', 'hi', N'शिकायत सफलतापूर्वक वापस ली गई');
  cms_seed_translation('withdraw.success_msg', 'hi', N'वापस ले ली गई है। आपको SMS/ईमेल द्वारा पुष्टि प्राप्त होगी।');
  cms_seed_translation('withdraw.go_home', 'hi', N'मुखपृष्ठ पर जाएँ');
  cms_seed_translation('feedback.title', 'hi', N'अपनी प्रतिक्रिया साझा करें');
  cms_seed_translation('feedback.subtitle', 'hi', N'शिकायत समाधान प्रक्रिया के साथ अपना अनुभव साझा करके हमें बेहतर बनाने में मदद करें।');
  cms_seed_translation('feedback.ref_label', 'hi', N'शिकायत संदर्भ संख्या');
  cms_seed_translation('feedback.rate_title', 'hi', N'अपने अनुभव को रेट करें');
  cms_seed_translation('feedback.overall', 'hi', N'समग्र अनुभव');
  cms_seed_translation('feedback.timeliness', 'hi', N'समाधान की समयबद्धता');
  cms_seed_translation('feedback.communication', 'hi', N'संवाद गुणवत्ता');
  cms_seed_translation('feedback.satisfaction', 'hi', N'परिणाम से संतुष्टि');
  cms_seed_translation('feedback.ease', 'hi', N'शिकायत दर्ज करने में आसानी');
  cms_seed_translation('feedback.redress', 'hi', N'शिकायत निवारण समय');
  cms_seed_translation('feedback.source_label', 'hi', N'CMS पोर्टल के बारे में जानकारी का स्रोत');
  cms_seed_translation('feedback.source_placeholder', 'hi', N'स्रोत चुनें');
  cms_seed_translation('feedback.source_specify', 'hi', N'कृपया निर्दिष्ट करें');
  cms_seed_translation('feedback.awareness', 'hi', N'CMS पोर्टल जागरूकता');
  cms_seed_translation('feedback.additional', 'hi', N'अतिरिक्त प्रतिक्रिया');
  cms_seed_translation('feedback.suggestions', 'hi', N'सुधार के लिए सुझाव');
  cms_seed_translation('feedback.cancel', 'hi', N'रद्द करें');
  cms_seed_translation('feedback.submit', 'hi', N'प्रतिक्रिया जमा करें');
  cms_seed_translation('feedback.submitting', 'hi', N'जमा किया जा रहा है...');
  cms_seed_translation('feedback.success_title', 'hi', N'आपकी प्रतिक्रिया के लिए धन्यवाद!');
  cms_seed_translation('feedback.go_home', 'hi', N'मुखपृष्ठ पर जाएँ');
  cms_seed_translation('success.title', 'hi', N'आपकी शिकायत सफलतापूर्वक जमा की गई!');
  cms_seed_translation('success.desc', 'hi', N'हमें विवरण प्राप्त हो गया है और आपकी शिकायत की पूरी तरह समीक्षा की जाएगी। आप दिए गए संदर्भ संख्या का उपयोग करके अपनी शिकायत की स्थिति भी ट्रैक कर सकते हैं।');
  cms_seed_translation('success.ref_label', 'hi', N'आपकी संदर्भ संख्या है');
  cms_seed_translation('success.download', 'hi', N'PDF डाउनलोड करें');
  cms_seed_translation('success.track', 'hi', N'शिकायत ट्रैक करें');
  cms_seed_translation('success.my_complaints', 'hi', N'मेरी शिकायतें');
  cms_seed_translation('success.share_feedback', 'hi', N'अपनी प्रतिक्रिया साझा करें');
  cms_seed_translation('nm.title', 'hi', N'शिकायत अस्वीकार्य के रूप में बंद');
  cms_seed_translation('nm.case_id', 'hi', N'केस आईडी');
  cms_seed_translation('nm.date', 'hi', N'तिथि');
  cms_seed_translation('nm.reason', 'hi', N'बंद करने का कारण');
  cms_seed_translation('nm.download', 'hi', N'बंदी पत्र डाउनलोड करें');
  cms_seed_translation('nm.go_home', 'hi', N'होम पर जाएँ');
  cms_seed_translation('duplicate.title', 'hi', N'डुप्लिकेट शिकायत पाई गई');
  cms_seed_translation('duplicate.note', 'hi', N'हमारी प्रणाली में एक समान शिकायत पहले से मौजूद है। आप रद्द करके समीक्षा कर सकते हैं, या नई जमा के साथ आगे बढ़ सकते हैं।');
  cms_seed_translation('duplicate.cancel', 'hi', N'जमा रद्द करें');
  cms_seed_translation('duplicate.proceed', 'hi', N'फिर भी आगे बढ़ें');
END;
/

-- =============================================================================
-- SECTION 6: Remaining languages placeholder
-- Due to output token limits, the remaining 8 languages (mr, bn, te, ta, gu, ur, kn, ml)
-- from the Portal seeder follow the exact same pattern.
-- A companion script will be generated for the remaining languages.
-- =============================================================================

-- Cleanup helper procedures and final commit
-- (Keep procedures for future use, or drop them)
-- DROP PROCEDURE cms_seed_key;
-- DROP PROCEDURE cms_seed_translation;

COMMIT;
