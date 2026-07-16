-- ============================================================
-- Oracle SQL Seed Script: Eligibility Translations (All Locales)
-- Generated: 2026-07-16
-- Module: eligibility
-- Locales: en, hi, mr, bn, te, ta, gu, ur, kn, ml
-- ============================================================

-- Step 1: Insert Translation Keys (English defaults)
-- Uses MERGE to avoid duplicates (idempotent)

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.title' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.title', 'eligibility', 'Page title', 'FILE A NEW COMPLAINT');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.subtitle' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.subtitle', 'eligibility', 'Page subtitle', 'Check your eligibility to proceed');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.mandatory_note' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.mandatory_note', 'eligibility', 'Mandatory fields note', 'All fields are mandatory unless marked as Optional.');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.select_desc' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.select_desc', 'eligibility', 'Select entity description', 'Select the financial institution--such as a bank, Non-Banking Financial Company (NBFC), or payment processor--licensed and supervised by the Reserve Bank of India against which you wish to file a complaint.');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.select_placeholder' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.select_placeholder', 'eligibility', 'Select placeholder', 'Select a Value');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.simplify_btn' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.simplify_btn', 'eligibility', 'Simplify button', 'Simplify For Me');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.browse_file' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.browse_file', 'eligibility', 'Browse file button', 'Browse File');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.upload_hint' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.upload_hint', 'eligibility', 'Upload hint text', 'Support formats: PDF, JPG, PNG. Maximum size: 5MB');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.opt_yes' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.opt_yes', 'eligibility', 'Yes option', 'Yes');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.opt_no' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.opt_no', 'eligibility', 'No option', 'No');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_select_re' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_select_re', 'eligibility', 'Q: Select RE', 'Select Regulated Entity Name');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_filed_with_re' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_filed_with_re', 'eligibility', 'Q: Filed with RE', 'Have you filed a written / electronic complaint with the {{reName}}?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_received_reply' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_received_reply', 'eligibility', 'Q: Received reply', 'Have you received any reply from the Entity?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_sent_reminder' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_sent_reminder', 'eligibility', 'Q: Sent reminder', 'Have you sent any reminder to the {{reName}}?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_sub_judice' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_sub_judice', 'eligibility', 'Q: Sub-judice', 'Is the complaint relating to the same grievance which is already pending before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_already_settled' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_already_settled', 'eligibility', 'Q: Already settled', 'Is the complaint relating to the same grievance which is already settled or dealt before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_through_advocate' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_through_advocate', 'eligibility', 'Q: Through advocate', 'Is your complaint being made through an advocate?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_pending_ombudsman' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_pending_ombudsman', 'eligibility', 'Q: Pending before Ombudsman', 'Is the complaint relating to the same grievance which is already pending before the Ombudsman?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_settled_ombudsman' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_settled_ombudsman', 'eligibility', 'Q: Settled by Ombudsman', 'Is the complaint relating to the same grievance which is already settled or dealt with on merits by the Ombudsman?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_staff_of_re' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_staff_of_re', 'eligibility', 'Q: Staff of RE', 'Is the Complainant a staff of the RE and complaint involves employer-employee relationship?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_sub_judice_simple' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_sub_judice_simple', 'eligibility', 'Q: Sub-judice simplified', 'Have you already taken this exact problem to a court, arbitrator, or another official legal authority (excluding criminal cases or police investigations)?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_already_settled_simple' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_already_settled_simple', 'eligibility', 'Q: Already settled simplified', 'Has this exact problem already been resolved by a court, arbitrator, or another official legal authority (excluding criminal cases or police investigations)?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_through_advocate_simple' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_through_advocate_simple', 'eligibility', 'Q: Advocate simplified', 'Are you filing this complaint with the help of a lawyer or legal representative?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_pending_ombudsman_simple' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_pending_ombudsman_simple', 'eligibility', 'Q: Pending Ombudsman simplified', 'Have you already filed a complaint about this same issue with the Ombudsman and it is still under review?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_settled_ombudsman_simple' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_settled_ombudsman_simple', 'eligibility', 'Q: Settled Ombudsman simplified', 'Has the Ombudsman already reviewed and resolved this same complaint in the past?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.q_staff_of_re_simple' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.q_staff_of_re_simple', 'eligibility', 'Q: Staff simplified', 'Are you an employee of the bank/NBFC you are complaining against, and is your complaint about your job or employment?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.sub_complaint_date' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.sub_complaint_date', 'eligibility', 'Sub: complaint date', 'Date on which the complaint was first filed with');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.sub_upload_complaint' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.sub_upload_complaint', 'eligibility', 'Sub: upload complaint', 'Upload a copy of the complaint sent to');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.sub_reminder_date' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.sub_reminder_date', 'eligibility', 'Sub: reminder date', 'Date on which reminder was sent');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.sub_upload_reminder' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.sub_upload_reminder', 'eligibility', 'Sub: upload reminder', 'Upload Reminder Copy');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.sub_reply_date' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.sub_reply_date', 'eligibility', 'Sub: reply date', 'Date on which reply was received');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.sub_upload_reply' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.sub_upload_reply', 'eligibility', 'Sub: upload reply', 'Upload Reply Copy');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.sub_are_you_complainant' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.sub_are_you_complainant', 'eligibility', 'Sub: are you complainant', 'If Yes, then are you the Complainant?');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.block_not_filed' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.block_not_filed', 'eligibility', 'Block: not filed', 'in terms of clause 10(1)(j) of Reserve Bank - Integrated Ombudsman Scheme, 2026, the complaint cannot be processed under the Scheme.');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.block_sub_judice' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.block_sub_judice', 'eligibility', 'Block: sub-judice', 'As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause 10(2)(b)(ii) of the Reserve Bank - Integrated Ombudsman Scheme, 2021.');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.block_already_settled' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.block_already_settled', 'eligibility', 'Block: already settled', 'As your complaint has already been settled or dealt with by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under the Reserve Bank - Integrated Ombudsman Scheme, 2021.');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.block_pending_ombudsman' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.block_pending_ombudsman', 'eligibility', 'Block: pending ombudsman', 'Your complaint is already pending before the Ombudsman on the same grievance. Duplicate complaints cannot be filed.');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.block_settled_ombudsman' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.block_settled_ombudsman', 'eligibility', 'Block: settled ombudsman', 'Your complaint has already been settled or dealt with on merits by the Ombudsman. You cannot file a fresh complaint on the same issue.');

MERGE INTO TRANSLATION_KEY tk
USING (SELECT 'eligibility.block_staff_of_re' AS code FROM DUAL) src ON (tk.CODE = src.code)
WHEN NOT MATCHED THEN INSERT (CODE, MODULE, DESCRIPTION, DEFAULT_VALUE)
VALUES ('eligibility.block_staff_of_re', 'eligibility', 'Block: staff of RE', 'Complaints involving employer-employee relationship between the complainant and the Regulated Entity cannot be filed under the Integrated Ombudsman Scheme.');

-- ============================================================
-- Step 2: Insert Translations for each locale
-- Uses MERGE with subquery to find TRANSLATION_KEY.ID by CODE
-- ============================================================

-- ===================== HINDI (hi) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'नई शिकायत दर्ज करें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'आगे बढ़ने के लिए अपनी पात्रता जांचें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.mandatory_note') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'सभी फ़ील्ड अनिवार्य हैं जब तक कि वैकल्पिक चिह्नित न हो।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.select_desc') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'वित्तीय संस्था का चयन करें—जैसे कि बैंक, गैर-बैंकिंग वित्तीय कंपनी (NBFC), या भुगतान प्रोसेसर—जो भारतीय रिज़र्व बैंक द्वारा लाइसेंस प्राप्त और पर्यवेक्षित है, जिसके विरुद्ध आप शिकायत दर्ज करना चाहते हैं।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.select_placeholder') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'एक मान चुनें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.simplify_btn') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'सरल करें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.browse_file') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'फ़ाइल चुनें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.upload_hint') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'समर्थित प्रारूप: PDF, JPG, PNG। अधिकतम आकार: 5MB');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'हाँ');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'नहीं');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_select_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'विनियमित संस्था का नाम चुनें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_filed_with_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या आपने {{reName}} के पास लिखित/इलेक्ट्रॉनिक शिकायत दर्ज की है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_received_reply') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या आपको संस्था से कोई उत्तर मिला है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_sent_reminder') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या आपने {{reName}} को कोई अनुस्मारक भेजा है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_sub_judice') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से किसी न्यायालय, न्यायाधिकरण, मध्यस्थ या किसी अन्य न्यायिक या अर्ध-न्यायिक मंच के समक्ष लंबित है (आपराधिक कार्यवाही को छोड़कर)?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_already_settled') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से किसी न्यायालय, न्यायाधिकरण, मध्यस्थ या किसी अन्य न्यायिक या अर्ध-न्यायिक मंच द्वारा निपटाई या सुलझाई जा चुकी है (आपराधिक कार्यवाही को छोड़कर)?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_through_advocate') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या आपकी शिकायत किसी अधिवक्ता के माध्यम से की जा रही है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_pending_ombudsman') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से लोकपाल के समक्ष लंबित है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_settled_ombudsman') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या यह शिकायत उसी विवाद से संबंधित है जो लोकपाल द्वारा पहले ही गुण-दोष के आधार पर निपटाई जा चुकी है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_staff_of_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या शिकायतकर्ता विनियमित संस्था का कर्मचारी है और शिकायत नियोक्ता-कर्मचारी संबंध से जुड़ी है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_sub_judice_simple') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या आप पहले से इस समस्या को किसी न्यायालय, मध्यस्थ, या अन्य आधिकारिक कानूनी प्राधिकरण के पास ले गए हैं (आपराधिक मामलों को छोड़कर)?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_already_settled_simple') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या यह समस्या पहले से किसी न्यायालय, मध्यस्थ, या अन्य आधिकारिक कानूनी प्राधिकरण द्वारा हल की जा चुकी है (आपराधिक मामलों को छोड़कर)?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_through_advocate_simple') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या आप किसी वकील या कानूनी प्रतिनिधि की सहायता से यह शिकायत दर्ज कर रहे हैं?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_pending_ombudsman_simple') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या आपने इसी मुद्दे पर पहले से लोकपाल के पास शिकायत दर्ज की है और वह अभी भी समीक्षाधीन है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_settled_ombudsman_simple') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या लोकपाल ने पहले ही इसी शिकायत की समीक्षा और समाधान कर दिया है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_staff_of_re_simple') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'क्या आप उस बैंक/NBFC के कर्मचारी हैं जिसके विरुद्ध शिकायत कर रहे हैं, और क्या शिकायत आपकी नौकरी से संबंधित है?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.sub_complaint_date') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'जिस तारीख को शिकायत पहली बार दर्ज की गई');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.sub_upload_complaint') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'को भेजी गई शिकायत की प्रति अपलोड करें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.sub_reminder_date') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'जिस तारीख को अनुस्मारक भेजा गया');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.sub_upload_reminder') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'अनुस्मारक की प्रति अपलोड करें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.sub_reply_date') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'जिस तारीख को उत्तर प्राप्त हुआ');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.sub_upload_reply') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'उत्तर की प्रति अपलोड करें');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.sub_are_you_complainant') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'यदि हाँ, तो क्या आप स्वयं शिकायतकर्ता हैं?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'रिज़र्व बैंक – एकीकृत लोकपाल योजना, 2026 के खंड 10(1)(j) के अनुसार, शिकायत को योजना के तहत संसाधित नहीं किया जा सकता।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_sub_judice') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'चूंकि आपकी शिकायत न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण के समक्ष लंबित है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के खंड 10(2)(b)(ii) के तहत अस्वीकार्य के रूप में बंद किया जाएगा।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_already_settled') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'चूंकि आपकी शिकायत पहले ही न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण द्वारा निपटाई जा चुकी है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के तहत अस्वीकार्य के रूप में बंद किया जाएगा।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_pending_ombudsman') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'आपकी शिकायत पहले से उसी विवाद पर लोकपाल के समक्ष लंबित है। डुप्लिकेट शिकायत दर्ज नहीं की जा सकती।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_settled_ombudsman') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'आपकी शिकायत लोकपाल द्वारा पहले ही गुण-दोष के आधार पर निपटाई जा चुकी है। इसी मुद्दे पर नई शिकायत दर्ज नहीं की जा सकती।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'hi' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_staff_of_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'विनियमित संस्था के कर्मचारी और नियोक्ता-कर्मचारी संबंध से जुड़ी शिकायतें एकीकृत लोकपाल योजना के तहत दर्ज नहीं की जा सकतीं।');

-- ===================== MARATHI (mr) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'नवीन तक्रार दाखल करा');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'पुढे जाण्यासाठी तुमची पात्रता तपासा');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.mandatory_note') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'सर्व फील्ड अनिवार्य आहेत, पर्यायी म्हणून चिन्हांकित केलेली वगळता.');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.select_placeholder') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'एक मूल्य निवडा');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.simplify_btn') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'सोप्या भाषेत');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.browse_file') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'फाइल निवडा');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.upload_hint') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'समर्थित स्वरूप: PDF, JPG, PNG. कमाल आकार: 5MB');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'होय');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'नाही');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_select_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'नियमित संस्थेचे नाव निवडा');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_filed_with_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'तुम्ही {{reName}} कडे लिखित/इलेक्ट्रॉनिक तक्रार दाखल केली आहे का?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_received_reply') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'तुम्हाला संस्थेकडून काही उत्तर मिळाले आहे का?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_sent_reminder') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'तुम्ही {{reName}} ला कोणता स्मरणपत्र पाठवले आहे का?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'रिझर्व्ह बँक – एकीकृत लोकपाल योजना, 2026 च्या कलम 10(1)(j) नुसार, तक्रार योजनेअंतर्गत प्रक्रिया करता येत नाही.');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_pending_ombudsman') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'तुमची तक्रार आधीच त्याच तक्रारीवर लोकपालासमोर प्रलंबित आहे. डुप्लिकेट तक्रार दाखल करता येत नाही.');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_settled_ombudsman') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'तुमची तक्रार लोकपालाने आधीच गुणवत्तेवर निकाली काढली आहे. याच मुद्द्यावर नवीन तक्रार दाखल करता येत नाही.');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'mr' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_staff_of_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'नियमित संस्थेचे कर्मचारी आणि नियोक्ता-कर्मचारी संबंधांशी संबंधित तक्रारी एकीकृत लोकपाल योजनेअंतर्गत दाखल करता येत नाहीत.');

-- ===================== BENGALI (bn) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'নতুন অভিযোগ দায়ের করুন');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'এগিয়ে যেতে আপনার যোগ্যতা পরীক্ষা করুন');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.mandatory_note') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ঐচ্ছিক চিহ্নিত না হলে সকল ক্ষেত্র বাধ্যতামূলক।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.select_placeholder') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'একটি মান নির্বাচন করুন');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.simplify_btn') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'সহজ করুন');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.browse_file') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ফাইল নির্বাচন করুন');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'হ্যাঁ');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'না');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_select_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'নিয়ন্ত্রিত সংস্থার নাম নির্বাচন করুন');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.q_filed_with_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'আপনি কি {{reName}}-এ লিখিত/ইলেকট্রনিক অভিযোগ দায়ের করেছেন?');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'রিজার্ভ ব্যাংক – সমন্বিত ওম্বডসম্যান স্কিম, ২০২৬-এর ধারা ১০(১)(জে) অনুসারে, এই অভিযোগ স্কিমের অধীনে প্রক্রিয়া করা যাবে না।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_pending_ombudsman') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'আপনার অভিযোগ ইতিমধ্যে একই বিষয়ে ওম্বডসম্যানের কাছে বিচারাধীন। ডুপ্লিকেট অভিযোগ দায়ের করা যাবে না।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_settled_ombudsman') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'আপনার অভিযোগ ইতিমধ্যে ওম্বডসম্যান দ্বারা গুণবিচারে নিষ্পত্তি হয়েছে। একই বিষয়ে নতুন অভিযোগ দায়ের করা যাবে না।');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'bn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_staff_of_re') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'নিয়ন্ত্রিত সংস্থার কর্মচারী এবং নিয়োগকর্তা-কর্মচারী সম্পর্কের অভিযোগ সমন্বিত ওম্বডসম্যান স্কিমের অধীনে দায়ের করা যাবে না।');

-- ===================== TELUGU (te) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'te' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'కొత్త ఫిర్యాదు దాఖలు చేయండి');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'te' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ముందుకు సాగడానికి మీ అర్హతను తనిఖీ చేయండి');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'te' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'అవును');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'te' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'కాదు');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'te' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'రిజర్వ్ బ్యాంక్ – సమగ్ర ఓంబుడ్స్‌మన్ పథకం, 2026 క్లాజ్ 10(1)(j) ప్రకారం, ఫిర్యాదును పథకం కింద ప్రాసెస్ చేయడం సాధ్యం కాదు.');

-- ===================== TAMIL (ta) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ta' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'புதிய புகார் பதிவு செய்யுங்கள்');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ta' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'தொடர உங்கள் தகுதியை சரிபார்க்கவும்');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ta' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ஆம்');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ta' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'இல்லை');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ta' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ரிசர்வ் வங்கி – ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டம், 2026 பிரிவு 10(1)(j) படி, புகார் திட்டத்தின் கீழ் செயல்படுத்த முடியாது.');

-- ===================== GUJARATI (gu) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'gu' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'નવી ફરિયાદ દાખલ કરો');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'gu' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'આગળ વધવા માટે તમારી પાત્રતા તપાસો');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'gu' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'હા');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'gu' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ના');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'gu' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'રિઝર્વ બેંક – સંકલિત ઓમ્બડ્સમેન યોજના, 2026 ની કલમ 10(1)(j) મુજબ, ફરિયાદ યોજના હેઠળ પ્રક્રિયા કરી શકાતી નથી.');

-- ===================== URDU (ur) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ur' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'نئی شکایت درج کریں');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ur' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'آگے بڑھنے کے لیے اپنی اہلیت جانچیں');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ur' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ہاں');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ur' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'نہیں');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ur' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ریزرو بینک – مربوط اومبڈزمین اسکیم، 2026 کی شق 10(1)(j) کے مطابق، شکایت اسکیم کے تحت عملدرآمد نہیں ہو سکتی۔');

-- ===================== KANNADA (kn) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'kn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ಹೊಸ ದೂರು ದಾಖಲಿಸಿ');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'kn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ಮುಂದುವರಿಯಲು ನಿಮ್ಮ ಅರ್ಹತೆಯನ್ನು ಪರಿಶೀಲಿಸಿ');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'kn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ಹೌದು');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'kn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ಇಲ್ಲ');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'kn' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್ – ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆ, 2026 ಕಲಂ 10(1)(j) ಪ್ರಕಾರ, ದೂರನ್ನು ಯೋಜನೆಯಡಿ ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ.');

-- ===================== MALAYALAM (ml) =====================

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ml' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.title') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'പുതിയ പരാതി ഫയൽ ചെയ്യുക');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ml' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.subtitle') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'മുന്നോട്ട് പോകാൻ നിങ്ങളുടെ യോഗ്യത പരിശോധിക്കുക');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ml' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_yes') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'അതെ');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ml' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.opt_no') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'ഇല്ല');

MERGE INTO TRANSLATION t
USING (SELECT tk.ID AS tk_id, 'ml' AS locale FROM TRANSLATION_KEY tk WHERE tk.CODE = 'eligibility.block_not_filed') src
ON (t.TRANSLATION_KEY_ID = src.tk_id AND t.LOCALE = src.locale)
WHEN NOT MATCHED THEN INSERT (TRANSLATION_KEY_ID, LOCALE, VALUE) VALUES (src.tk_id, src.locale, N'റിസർവ് ബാങ്ക് – സംയോജിത ഓംബുഡ്സ്മാൻ സ്കീം, 2026 ക്ലോസ് 10(1)(j) പ്രകാരം, പരാതി സ്കീമിന് കീഴിൽ പ്രോസസ്സ് ചെയ്യാൻ കഴിയില്ല.');

COMMIT;
-- ============================================================
-- END OF SCRIPT
-- Note: This script contains representative entries for each locale.
-- The full seeder (EligibilityTranslationSeeder.java) handles all keys.
-- Run on Oracle with AL32UTF8 character set for proper Unicode support.
-- ============================================================
