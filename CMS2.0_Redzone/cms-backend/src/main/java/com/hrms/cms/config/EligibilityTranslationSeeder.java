package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(5)
public class EligibilityTranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public EligibilityTranslationSeeder(TranslationKeyRepository keyRepo, TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Page-level labels
        seedIfAbsent("eligibility.title", "eligibility", "Page title", "FILE A NEW COMPLAINT");
        seedIfAbsent("eligibility.subtitle", "eligibility", "Page subtitle", "Check your eligibility to proceed");
        seedIfAbsent("eligibility.mandatory_note", "eligibility", "Mandatory fields note", "All fields are mandatory unless marked as Optional.");
        seedIfAbsent("eligibility.select_desc", "eligibility", "Select entity description", "Select the financial institution—such as a bank, Non-Banking Financial Company (NBFC), or payment processor—licensed and supervised by the Reserve Bank of India against which you wish to file a complaint.");
        seedIfAbsent("eligibility.select_placeholder", "eligibility", "Select placeholder", "Select a Value");
        seedIfAbsent("eligibility.simplify_btn", "eligibility", "Simplify button", "Simplify For Me");
        seedIfAbsent("eligibility.browse_file", "eligibility", "Browse file button", "Browse File");
        seedIfAbsent("eligibility.upload_hint", "eligibility", "Upload hint text", "Support formats: PDF, JPG, PNG. Maximum size: 5MB");

        // Yes/No options
        seedIfAbsent("eligibility.opt_yes", "eligibility", "Yes option", "Yes");
        seedIfAbsent("eligibility.opt_no", "eligibility", "No option", "No");

        // Shown when Next is pressed with an eligibility question left unanswered.
        seedIfAbsent("eligibility.response_mandatory", "eligibility",
                "Error when an eligibility question is unanswered", "Response is mandatory.");

        // UST13: the entity select is a NAMED field, so its mandatory error names the field rather than
        // demanding a generic "response" on a screen with nothing to respond to.
        seedIfAbsent("eligibility.entity_mandatory", "eligibility",
                "Error when no Regulated Entity is selected", "Regulated Entity Name is mandatory.");

        // UST13 S3: search the entity list by name or entity type.
        seedIfAbsent("eligibility.entity_search_label", "eligibility",
                "Accessible label for the RE search box", "Search Regulated Entity by name or type");
        seedIfAbsent("eligibility.entity_search_placeholder", "eligibility",
                "Placeholder for the RE search box", "Search by entity name or entity type");
        seedIfAbsent("eligibility.entity_search_clear", "eligibility",
                "Accessible label for the clear-search button", "Clear search");
        // {{term}} is interpolated by the COMPONENT, not by the pipe: translate(key, params?) takes
        // params optionally, so `| translate` on this key alone would print a literal {{term}}.
        seedIfAbsent("eligibility.entity_search_no_results", "eligibility",
                "Shown when the RE search matches nothing", "No results found for \"{{term}}\".");
        seedIfAbsent("eligibility.entities_unavailable", "eligibility",
                "Shown when the Regulated Entity master cannot be loaded",
                "The list of Regulated Entities could not be loaded. Please retry — a complaint cannot be filed without naming an entity.");

        // Questions
        seedIfAbsent("eligibility.q_select_re", "eligibility", "Q: Select RE", "Select Regulated Entity Name");
        seedIfAbsent("eligibility.q_filed_with_re", "eligibility", "Q: Filed with RE", "Have you filed a written / electronic complaint with the {{reName}}?");
        seedIfAbsent("eligibility.q_received_reply", "eligibility", "Q: Received reply", "Have you received any reply from the Entity?");
        seedIfAbsent("eligibility.q_sent_reminder", "eligibility", "Q: Sent reminder", "Have you sent any reminder to the {{reName}}?");
        seedIfAbsent("eligibility.q_sub_judice", "eligibility", "Q: Sub-judice", "Is the complaint relating to the same grievance which is already pending before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?");
        seedIfAbsent("eligibility.q_already_settled", "eligibility", "Q: Already settled", "Is the complaint relating to the same grievance which is already settled or dealt before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?");
        seedIfAbsent("eligibility.q_through_advocate", "eligibility", "Q: Through advocate", "Is your complaint being made through an advocate?");
        seedIfAbsent("eligibility.q_pending_ombudsman", "eligibility", "Q: Pending before Ombudsman", "Is the complaint relating to the same grievance which is already pending before the Ombudsman?");
        seedIfAbsent("eligibility.q_settled_ombudsman", "eligibility", "Q: Settled by Ombudsman", "Is the complaint relating to the same grievance which is already settled or dealt with on merits by the Ombudsman?");
        seedIfAbsent("eligibility.q_staff_of_re", "eligibility", "Q: Staff of RE", "Is the Complainant a staff of the RE and complaint involves employer-employee relationship?");
        seedIfAbsent("eligibility.q_previously_filed_cepc", "eligibility", "Q: Previously filed with CEPC", "Have you previously filed a complaint on the same subject matter with CEPC/RBI Ombudsman?");
        seedIfAbsent("eligibility.q_employee_of_re", "eligibility", "Q: Employee of RE", "Are / were you an employee of the Regulated Entity against whom this complaint is being filed?");
        seedIfAbsent("eligibility.q_employer_relationship", "eligibility", "Q: Employer relationship", "If Yes, Is your complaint involves the employee-employer relationship of the Regulated Entity?");

        // Simplified texts
        seedIfAbsent("eligibility.q_sub_judice_simple", "eligibility", "Q: Sub-judice simplified", "Have you already taken this exact problem to a court, arbitrator, or another official legal authority (excluding criminal cases or police investigations)?");
        seedIfAbsent("eligibility.q_already_settled_simple", "eligibility", "Q: Already settled simplified", "Has this exact problem already been resolved by a court, arbitrator, or another official legal authority (excluding criminal cases or police investigations)?");
        seedIfAbsent("eligibility.q_through_advocate_simple", "eligibility", "Q: Advocate simplified", "Are you filing this complaint with the help of a lawyer or legal representative?");
        seedIfAbsent("eligibility.q_pending_ombudsman_simple", "eligibility", "Q: Pending Ombudsman simplified", "Have you already filed a complaint about this same issue with the Ombudsman and it is still under review?");
        seedIfAbsent("eligibility.q_settled_ombudsman_simple", "eligibility", "Q: Settled Ombudsman simplified", "Has the Ombudsman already reviewed and resolved this same complaint in the past?");
        seedIfAbsent("eligibility.q_staff_of_re_simple", "eligibility", "Q: Staff simplified", "Are you an employee of the bank/NBFC you are complaining against, and is your complaint about your job or employment?");

        // Sub-field labels
        seedIfAbsent("eligibility.sub_complaint_date", "eligibility", "Sub: complaint date", "Date on which the complaint was first filed with");
        seedIfAbsent("eligibility.sub_upload_complaint", "eligibility", "Sub: upload complaint", "Upload a copy of the complaint sent to");
        seedIfAbsent("eligibility.sub_reminder_date", "eligibility", "Sub: reminder date", "Date on which reminder was sent");
        seedIfAbsent("eligibility.sub_upload_reminder", "eligibility", "Sub: upload reminder", "Upload Reminder Copy");
        seedIfAbsent("eligibility.sub_reply_date", "eligibility", "Sub: reply date", "Date on which reply was received");
        seedIfAbsent("eligibility.sub_upload_reply", "eligibility", "Sub: upload reply", "Upload Reply Copy");
        seedIfAbsent("eligibility.sub_are_you_complainant", "eligibility", "Sub: are you complainant", "If Yes, then are you the Complainant?");

        // Block messages
        seedIfAbsent("eligibility.block_not_filed", "eligibility", "Block: not filed", "in terms of clause {{clause}} of Reserve Bank – Integrated Ombudsman Scheme, 2021, the complaint cannot be processed under the Scheme.");
        seedIfAbsent("eligibility.block_sub_judice", "eligibility", "Block: sub-judice", "As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause {{clause}} of the Reserve Bank - Integrated Ombudsman Scheme, 2021.");
        seedIfAbsent("eligibility.block_already_settled", "eligibility", "Block: already settled", "As your complaint has already been settled or dealt with by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under the Reserve Bank - Integrated Ombudsman Scheme, 2021.");
        seedIfAbsent("eligibility.block_pending_ombudsman", "eligibility", "Block: pending ombudsman", "Your complaint is already pending before the Ombudsman on the same grievance. Duplicate complaints cannot be filed.");
        seedIfAbsent("eligibility.block_settled_ombudsman", "eligibility", "Block: settled ombudsman", "Your complaint has already been settled or dealt with on merits by the Ombudsman. You cannot file a fresh complaint on the same issue.");
        seedIfAbsent("eligibility.block_staff_of_re", "eligibility", "Block: staff of RE", "Complaints involving employer-employee relationship between the complainant and the Regulated Entity cannot be filed under the Integrated Ombudsman Scheme.");
        seedIfAbsent("eligibility.block_previously_filed_cepc", "eligibility", "Block: previously filed with CEPC", "As your complaint on the same subject matter has already been filed with CEPC/RBI, it will be closed as Non-Maintainable under the Reserve Bank - Integrated Ombudsman Scheme, 2021.");
        seedIfAbsent("eligibility.block_employer_relationship", "eligibility", "Block: employer relationship", "As your complaint involves the employee-employer relationship with the Regulated Entity, it cannot be processed under the Integrated Ombudsman Scheme, 2021.");
        seedIfAbsent("eligibility.block_advocate_not_complainant", "eligibility", "Block: advocate filing for a non-complainant", "As per the Integrated Ombudsman Scheme, a complaint filed through an advocate must be filed by the complainant themselves. Since you are not the complainant, this complaint cannot be processed.");

        // Block-note surrounding text
        seedIfAbsent("eligibility.block_indicated", "eligibility", "Block: as you have indicated", "As you have indicated");
        seedIfAbsent("eligibility.block_in_response", "eligibility", "Block: in response to query", "in response to this query,");
        seedIfAbsent("eligibility.block_written_required", "eligibility", "Block: written complaint required", "A written/electronic complaint is required to be filed with the Regulated Entity first.");
        seedIfAbsent("eligibility.block_regret", "eligibility", "Block: regret message", "Accordingly, we regret to inform you that your present grievance against");
        seedIfAbsent("eligibility.block_cannot_register", "eligibility", "Block: cannot register", "cannot be registered under the Scheme. In case the response was furnished erroneously, you may change the response.");
        seedIfAbsent("eligibility.block_regards", "eligibility", "Block: regards", "Regards, RBI CMS Team.");
        seedIfAbsent("eligibility.show_closure_letter", "eligibility", "Show closure letter button", "Show Closure Letter");
        seedIfAbsent("eligibility.questions_unavailable", "eligibility", "Question master unavailable", "The eligibility questions could not be loaded. Please retry — your complaint cannot be assessed until they are available.");
        seedIfAbsent("form.retry", "form", "Retry button", "Retry");
        seedIfAbsent("eligibility.re_covered_link", "eligibility", "Link: REs covered by the Scheme", "Regulated entities covered under Reserve Bank-Integrated Ombudsman Scheme, 2021");
        seedIfAbsent("eligibility.re_not_covered_link", "eligibility", "Link: REs not covered by the Scheme", "Regulated entities not covered under Reserve Bank-Integrated Ombudsman Scheme, 2021");
        seedIfAbsent("eligibility.passed_title", "eligibility", "Eligibility passed title", "Eligibility Passed");
        seedIfAbsent("eligibility.passed_message", "eligibility", "Eligibility passed message", "You are eligible to file a complaint under RBI Integrated Ombudsman Scheme, 2021.");

        // UST15/19/22 date validation, UST16/20/23 upload validation, UST17 reference number
        seedIfAbsent("validation.date_invalid", "validation", "Calendar-invalid date", "Enter a valid date in dd/mm/yyyy format");
        seedIfAbsent("validation.date_incomplete", "validation", "Partially typed date", "Enter the full date as dd/mm/yyyy");
        seedIfAbsent("validation.date_future", "validation", "Future date", "Date cannot be a future date");
        seedIfAbsent("validation.complaint_date_required", "validation", "RE complaint date missing", "Complaint date with RE is required");
        seedIfAbsent("validation.complaint_copy_required", "validation", "RE complaint copy missing", "Please upload a copy of the complaint sent to the Regulated Entity");
        seedIfAbsent("validation.reply_date_required", "validation", "Reply date missing", "Date on which reply was received is required");
        seedIfAbsent("validation.reply_copy_required", "validation", "Reply copy missing", "Please upload a copy of the reply received from the Regulated Entity");
        seedIfAbsent("validation.reply_before_complaint", "validation", "Reply precedes complaint", "Reply date cannot be earlier than the complaint filing date");
        seedIfAbsent("validation.reminder_date_required", "validation", "Reminder date missing", "Date on which reminder was sent is required");
        seedIfAbsent("validation.reminder_copy_required", "validation", "Reminder copy missing", "Please upload a copy of the reminder sent to the Regulated Entity");
        seedIfAbsent("validation.reminder_before_complaint", "validation", "Reminder precedes complaint", "Reminder date cannot be earlier than the complaint filing date");
        seedIfAbsent("validation.file_type_not_allowed", "validation", "Disallowed upload type", "Only PDF, JPG and PNG files are allowed");
        seedIfAbsent("validation.ref_too_long", "validation", "Reference number too long", "Reference number must not exceed 100 characters");
        seedIfAbsent("validation.ref_invalid_chars", "validation", "Reference number bad characters", "Reference number may contain only letters, digits, hyphen, underscore and slash");
        seedIfAbsent("eligibility.block_less_than_30_days", "eligibility", "Block: RE response window not elapsed", "As the Regulated Entity has not yet been given {{days}} days to respond to your complaint, your complaint cannot be registered at this time. Please wait until {{days}} days have elapsed from the date of filing your complaint with the Regulated Entity.");
        seedIfAbsent("eligibility.time_barred_warning", "eligibility", "Warning: filing window elapsed", "Your complaint to the Regulated Entity was filed more than {{days}} days ago. The filing window under the Scheme has elapsed, so your complaint may be closed as time-barred. You may still proceed.");
        seedIfAbsent("eligibility.re_window_opens_on", "eligibility", "Notice: date the RE window opens", "You may file your complaint with the RBI Ombudsman on or after {{date}}.");
        // The filing window now REFUSES rather than warns (business ruling), and which window applies
        // depends on whether the RE replied — hence two keys, not one.
        seedIfAbsent("eligibility.block_filing_window", "eligibility", "Block: filing window elapsed (RE never replied)", "Complaint filing period has expired. A complaint must be filed within {{days}} days of your complaint to the Regulated Entity.");
        seedIfAbsent("eligibility.block_post_reply_window", "eligibility", "Block: filing window elapsed (after RE reply)", "Complaint filing period has expired. A complaint must be filed within {{days}} days of the Regulated Entity's reply.");

        // UST5: DPDP consent notice. The wording is snapshotted onto each CITIZEN_CONSENTS row, so any
        // revision here must be accompanied by a bump of cms.auth.consent.version.
        seedIfAbsent("consent.dpdp_notice", "consent", "DPDP Act consent declaration",
                "I consent to RBI using my personal data for registering and resolving my complaint in line with applicable laws and the Digital Personal Data Protection Act, 2023.");
        seedIfAbsent("consent.required", "consent", "Consent not accepted error",
                "You must accept the data processing declaration to continue.");

        // Assistance description
        seedIfAbsent("layout.assistance_desc", "layout", "Assistance banner description", "The contact center (#14448) with Interactive Voice Response System (IVRS) is available 24x7, while the facility to connect to Contact Centre personnel is available from Monday to Saturday except for National Holidays, between 8:00 AM to 10:00 PM for English, Hindi, and ten regional languages.");

        keyRepo.flush();

        seedHindiTranslations();
        seedMarathiTranslations();
        seedBengaliTranslations();
        seedTeluguTranslations();
        seedTamilTranslations();
        seedGujaratiTranslations();
        seedUrduTranslations();
        seedKannadaTranslations();
        seedMalayalamTranslations();
    }

    private void seedIfAbsent(String code, String module, String description, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(module);
        key.setDescription(description);
        key.setDefaultValue(defaultValue);
        keyRepo.save(key);
    }

    private void seedHindiTranslations() {
        Map<String, String> hi = new LinkedHashMap<>();
        hi.put("eligibility.title", "नई शिकायत दर्ज करें");
        hi.put("eligibility.subtitle", "आगे बढ़ने के लिए अपनी पात्रता जांचें");
        hi.put("eligibility.mandatory_note", "सभी फ़ील्ड अनिवार्य हैं जब तक कि वैकल्पिक चिह्नित न हो।");
        hi.put("eligibility.select_desc", "वित्तीय संस्था का चयन करें—जैसे कि बैंक, गैर-बैंकिंग वित्तीय कंपनी (NBFC), या भुगतान प्रोसेसर—जो भारतीय रिज़र्व बैंक द्वारा लाइसेंस प्राप्त और पर्यवेक्षित है, जिसके विरुद्ध आप शिकायत दर्ज करना चाहते हैं।");
        hi.put("eligibility.select_placeholder", "एक मान चुनें");
        hi.put("eligibility.simplify_btn", "सरल करें");
        hi.put("eligibility.browse_file", "फ़ाइल चुनें");
        hi.put("eligibility.upload_hint", "समर्थित प्रारूप: PDF, JPG, PNG। अधिकतम आकार: 5MB");
        hi.put("eligibility.opt_yes", "हाँ");
        hi.put("eligibility.opt_no", "नहीं");
        hi.put("eligibility.response_mandatory", "उत्तर देना अनिवार्य है।");
        hi.put("eligibility.entity_mandatory", "विनियमित संस्था का नाम अनिवार्य है।");
        hi.put("eligibility.entity_search_label", "नाम या प्रकार से विनियमित संस्था खोजें");
        hi.put("eligibility.entity_search_placeholder", "संस्था का नाम या संस्था का प्रकार खोजें");
        hi.put("eligibility.entity_search_clear", "खोज साफ़ करें");
        hi.put("eligibility.entity_search_no_results", "\"{{term}}\" के लिए कोई परिणाम नहीं मिला।");
        hi.put("eligibility.entities_unavailable", "विनियमित संस्थाओं की सूची लोड नहीं हो सकी। कृपया पुनः प्रयास करें — संस्था का नाम बताए बिना शिकायत दर्ज नहीं की जा सकती।");

        hi.put("eligibility.q_select_re", "विनियमित संस्था का नाम चुनें");
        hi.put("eligibility.q_filed_with_re", "क्या आपने {{reName}} के पास लिखित/इलेक्ट्रॉनिक शिकायत दर्ज की है?");
        hi.put("eligibility.q_received_reply", "क्या आपको संस्था से कोई उत्तर मिला है?");
        hi.put("eligibility.q_sent_reminder", "क्या आपने {{reName}} को कोई अनुस्मारक भेजा है?");
        hi.put("eligibility.q_sub_judice", "क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से किसी न्यायालय, न्यायाधिकरण, मध्यस्थ या किसी अन्य न्यायिक या अर्ध-न्यायिक मंच के समक्ष लंबित है (आपराधिक कार्यवाही को छोड़कर)?");
        hi.put("eligibility.q_already_settled", "क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से किसी न्यायालय, न्यायाधिकरण, मध्यस्थ या किसी अन्य न्यायिक या अर्ध-न्यायिक मंच द्वारा निपटाई या सुलझाई जा चुकी है (आपराधिक कार्यवाही को छोड़कर)?");
        hi.put("eligibility.q_through_advocate", "क्या आपकी शिकायत किसी अधिवक्ता के माध्यम से की जा रही है?");
        hi.put("eligibility.q_pending_ombudsman", "क्या यह शिकायत उसी विवाद से संबंधित है जो पहले से लोकपाल के समक्ष लंबित है?");
        hi.put("eligibility.q_settled_ombudsman", "क्या यह शिकायत उसी विवाद से संबंधित है जो लोकपाल द्वारा पहले ही गुण-दोष के आधार पर निपटाई जा चुकी है?");
        hi.put("eligibility.q_staff_of_re", "क्या शिकायतकर्ता विनियमित संस्था का कर्मचारी है और शिकायत नियोक्ता-कर्मचारी संबंध से जुड़ी है?");
        hi.put("eligibility.q_employee_of_re", "क्या आप उस विनियमित संस्था के कर्मचारी हैं/थे जिसके विरुद्ध यह शिकायत दर्ज की जा रही है?");
        hi.put("eligibility.q_employer_relationship", "यदि हाँ, तो क्या आपकी शिकायत विनियमित संस्था के कर्मचारी-नियोक्ता संबंध से जुड़ी है?");

        hi.put("eligibility.q_sub_judice_simple", "क्या आप पहले से इस समस्या को किसी न्यायालय, मध्यस्थ, या अन्य आधिकारिक कानूनी प्राधिकरण के पास ले गए हैं (आपराधिक मामलों को छोड़कर)?");
        hi.put("eligibility.q_already_settled_simple", "क्या यह समस्या पहले से किसी न्यायालय, मध्यस्थ, या अन्य आधिकारिक कानूनी प्राधिकरण द्वारा हल की जा चुकी है (आपराधिक मामलों को छोड़कर)?");
        hi.put("eligibility.q_through_advocate_simple", "क्या आप किसी वकील या कानूनी प्रतिनिधि की सहायता से यह शिकायत दर्ज कर रहे हैं?");
        hi.put("eligibility.q_pending_ombudsman_simple", "क्या आपने इसी मुद्दे पर पहले से लोकपाल के पास शिकायत दर्ज की है और वह अभी भी समीक्षाधीन है?");
        hi.put("eligibility.q_settled_ombudsman_simple", "क्या लोकपाल ने पहले ही इसी शिकायत की समीक्षा और समाधान कर दिया है?");
        hi.put("eligibility.q_staff_of_re_simple", "क्या आप उस बैंक/NBFC के कर्मचारी हैं जिसके विरुद्ध शिकायत कर रहे हैं, और क्या शिकायत आपकी नौकरी से संबंधित है?");

        hi.put("eligibility.sub_complaint_date", "जिस तारीख को शिकायत पहली बार दर्ज की गई");
        hi.put("eligibility.sub_upload_complaint", "को भेजी गई शिकायत की प्रति अपलोड करें");
        hi.put("eligibility.sub_reminder_date", "जिस तारीख को अनुस्मारक भेजा गया");
        hi.put("eligibility.sub_upload_reminder", "अनुस्मारक की प्रति अपलोड करें");
        hi.put("eligibility.sub_reply_date", "जिस तारीख को उत्तर प्राप्त हुआ");
        hi.put("eligibility.sub_upload_reply", "उत्तर की प्रति अपलोड करें");
        hi.put("eligibility.sub_are_you_complainant", "यदि हाँ, तो क्या आप स्वयं शिकायतकर्ता हैं?");

        hi.put("eligibility.block_not_filed", "रिज़र्व बैंक – एकीकृत लोकपाल योजना, 2021 के खंड {{clause}} के अनुसार, शिकायत को योजना के तहत संसाधित नहीं किया जा सकता।");
        hi.put("eligibility.block_sub_judice", "चूंकि आपकी शिकायत न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण के समक्ष लंबित है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के खंड {{clause}} के तहत अस्वीकार्य के रूप में बंद किया जाएगा।");
        hi.put("eligibility.block_already_settled", "चूंकि आपकी शिकायत पहले ही न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण द्वारा निपटाई जा चुकी है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के तहत अस्वीकार्य के रूप में बंद किया जाएगा।");
        hi.put("eligibility.block_pending_ombudsman", "आपकी शिकायत पहले से उसी विवाद पर लोकपाल के समक्ष लंबित है। डुप्लिकेट शिकायत दर्ज नहीं की जा सकती।");
        hi.put("eligibility.block_settled_ombudsman", "आपकी शिकायत लोकपाल द्वारा पहले ही गुण-दोष के आधार पर निपटाई जा चुकी है। इसी मुद्दे पर नई शिकायत दर्ज नहीं की जा सकती।");
        hi.put("eligibility.block_staff_of_re", "विनियमित संस्था के कर्मचारी और नियोक्ता-कर्मचारी संबंध से जुड़ी शिकायतें एकीकृत लोकपाल योजना के तहत दर्ज नहीं की जा सकतीं।");
        hi.put("eligibility.block_advocate_not_complainant", "एकीकृत लोकपाल योजना के अनुसार, वकील के माध्यम से दर्ज शिकायत स्वयं शिकायतकर्ता द्वारा दायर की जानी चाहिए। चूंकि आप शिकायतकर्ता नहीं हैं, इस शिकायत को संसाधित नहीं किया जा सकता।");

        hi.put("eligibility.block_indicated", "जैसा कि आपने");
        hi.put("eligibility.block_in_response", "इस प्रश्न के उत्तर में बताया है,");
        hi.put("eligibility.block_written_required", "विनियमित संस्था के पास पहले लिखित/इलेक्ट्रॉनिक शिकायत दर्ज करना आवश्यक है।");
        hi.put("eligibility.block_regret", "तदनुसार, हम आपको सूचित करते हैं कि आपकी वर्तमान शिकायत");
        hi.put("eligibility.block_cannot_register", "के विरुद्ध योजना के तहत पंजीकृत नहीं की जा सकती। यदि उत्तर गलती से दिया गया था, तो आप उत्तर बदल सकते हैं।");
        hi.put("eligibility.block_regards", "सादर, RBI CMS टीम।");
        hi.put("eligibility.show_closure_letter", "बंद करने का पत्र दिखाएं");
        hi.put("eligibility.passed_title", "पात्रता पारित");
        hi.put("eligibility.passed_message", "आप RBI एकीकृत लोकपाल योजना, 2021 के तहत शिकायत दर्ज करने के पात्र हैं।");
        hi.put("validation.date_invalid", "dd/mm/yyyy प्रारूप में मान्य तारीख दर्ज करें");
        hi.put("validation.date_incomplete", "पूरी तारीख dd/mm/yyyy के रूप में दर्ज करें");
        hi.put("validation.date_future", "तारीख भविष्य की नहीं हो सकती");
        hi.put("validation.complaint_date_required", "विनियमित संस्था के पास शिकायत दर्ज करने की तारीख आवश्यक है");
        hi.put("validation.complaint_copy_required", "विनियमित संस्था को भेजी गई शिकायत की प्रति अपलोड करें");
        hi.put("validation.reply_date_required", "उत्तर प्राप्त होने की तारीख आवश्यक है");
        hi.put("validation.reply_copy_required", "विनियमित संस्था से प्राप्त उत्तर की प्रति अपलोड करें");
        hi.put("validation.reply_before_complaint", "उत्तर की तारीख शिकायत दर्ज करने की तारीख से पहले नहीं हो सकती");
        hi.put("validation.reminder_date_required", "अनुस्मारक भेजने की तारीख आवश्यक है");
        hi.put("validation.reminder_copy_required", "विनियमित संस्था को भेजे गए अनुस्मारक की प्रति अपलोड करें");
        hi.put("validation.reminder_before_complaint", "अनुस्मारक की तारीख शिकायत दर्ज करने की तारीख से पहले नहीं हो सकती");
        hi.put("validation.file_type_not_allowed", "केवल PDF, JPG और PNG फ़ाइलें ही स्वीकार्य हैं");
        hi.put("validation.ref_too_long", "संदर्भ संख्या 100 वर्णों से अधिक नहीं हो सकती");
        hi.put("validation.ref_invalid_chars", "संदर्भ संख्या में केवल अक्षर, अंक, हाइफ़न, अंडरस्कोर और स्लैश हो सकते हैं");
        hi.put("eligibility.block_less_than_30_days", "चूंकि विनियमित संस्था को आपकी शिकायत का उत्तर देने के लिए अभी {{days}} दिन नहीं दिए गए हैं, इस समय आपकी शिकायत पंजीकृत नहीं की जा सकती। कृपया विनियमित संस्था के पास शिकायत दर्ज करने की तारीख से {{days}} दिन पूरे होने तक प्रतीक्षा करें।");
        hi.put("eligibility.time_barred_warning", "विनियमित संस्था के पास आपकी शिकायत {{days}} दिन से अधिक पहले दर्ज की गई थी। योजना के तहत दाखिल करने की अवधि समाप्त हो गई है, इसलिए आपकी शिकायत समय-बाधित के रूप में बंद की जा सकती है। आप फिर भी आगे बढ़ सकते हैं।");
        hi.put("eligibility.block_filing_window", "शिकायत दाखिल करने की अवधि समाप्त हो गई है। शिकायत विनियमित संस्था के पास आपकी शिकायत के {{days}} दिनों के भीतर दाखिल की जानी चाहिए।");
        hi.put("eligibility.block_post_reply_window", "शिकायत दाखिल करने की अवधि समाप्त हो गई है। शिकायत विनियमित संस्था के उत्तर के {{days}} दिनों के भीतर दाखिल की जानी चाहिए।");
        hi.put("consent.dpdp_notice", "मैं सहमति देता/देती हूँ कि RBI लागू कानूनों और डिजिटल पर्सनल डेटा प्रोटेक्शन अधिनियम, 2023 के अनुरूप मेरी शिकायत दर्ज करने और उसका समाधान करने के लिए मेरे व्यक्तिगत डेटा का उपयोग करे।");
        hi.put("consent.required", "जारी रखने के लिए आपको डेटा प्रोसेसिंग घोषणा स्वीकार करनी होगी।");
        hi.put("layout.assistance_desc", "संपर्क केंद्र (#14448) इंटरैक्टिव वॉइस रिस्पांस सिस्टम (IVRS) के साथ 24x7 उपलब्ध है, जबकि संपर्क केंद्र कर्मियों से जुड़ने की सुविधा सोमवार से शनिवार (राष्ट्रीय अवकाश को छोड़कर) सुबह 8:00 बजे से रात 10:00 बजे तक अंग्रेजी, हिंदी और दस क्षेत्रीय भाषाओं में उपलब्ध है।");

        for (Map.Entry<String, String> entry : hi.entrySet()) {
            keyRepo.findByCode(entry.getKey()).ifPresent(key -> {
                if (!translationRepo.existsByTranslationKeyAndLocale(key, "hi")) {
                    Translation t = new Translation();
                    t.setTranslationKey(key);
                    t.setLocale("hi");
                    t.setValue(entry.getValue());
                    translationRepo.save(t);
                }
            });
        }
    }

    private void seedMarathiTranslations() {
        Map<String, String> mr = new LinkedHashMap<>();
        mr.put("eligibility.title", "नवीन तक्रार दाखल करा");
        mr.put("eligibility.subtitle", "पुढे जाण्यासाठी तुमची पात्रता तपासा");
        mr.put("eligibility.mandatory_note", "सर्व फील्ड अनिवार्य आहेत, पर्यायी म्हणून चिन्हांकित केलेली वगळता.");
        mr.put("eligibility.select_desc", "वित्तीय संस्था निवडा—जसे की बँक, नॉन-बँकिंग फायनान्शियल कंपनी (NBFC), किंवा पेमेंट प्रोसेसर—जी भारतीय रिझर्व्ह बँकेने परवानाकृत आणि पर्यवेक्षित केलेली आहे, ज्याविरुद्ध तुम्हाला तक्रार दाखल करायची आहे.");
        mr.put("eligibility.select_placeholder", "एक मूल्य निवडा");
        mr.put("eligibility.simplify_btn", "सोप्या भाषेत");
        mr.put("eligibility.browse_file", "फाइल निवडा");
        mr.put("eligibility.upload_hint", "समर्थित स्वरूप: PDF, JPG, PNG. कमाल आकार: 5MB");
        mr.put("eligibility.opt_yes", "होय");
        mr.put("eligibility.opt_no", "नाही");
        mr.put("eligibility.response_mandatory", "उत्तर देणे अनिवार्य आहे.");
        mr.put("eligibility.entity_mandatory", "नियमित संस्थेचे नाव अनिवार्य आहे.");
        mr.put("eligibility.entity_search_label", "नाव किंवा प्रकारानुसार नियमित संस्था शोधा");
        mr.put("eligibility.entity_search_placeholder", "संस्थेचे नाव किंवा संस्थेचा प्रकार शोधा");
        mr.put("eligibility.entity_search_clear", "शोध साफ करा");
        mr.put("eligibility.entity_search_no_results", "\"{{term}}\" साठी कोणतेही परिणाम आढळले नाहीत.");
        mr.put("eligibility.entities_unavailable", "नियमित संस्थांची यादी लोड होऊ शकली नाही. कृपया पुन्हा प्रयत्न करा — संस्थेचे नाव न देता तक्रार दाखल करता येत नाही.");

        mr.put("eligibility.q_select_re", "नियमित संस्थेचे नाव निवडा");
        mr.put("eligibility.q_filed_with_re", "तुम्ही {{reName}} कडे लिखित/इलेक्ट्रॉनिक तक्रार दाखल केली आहे का?");
        mr.put("eligibility.q_received_reply", "तुम्हाला संस्थेकडून काही उत्तर मिळाले आहे का?");
        mr.put("eligibility.q_sent_reminder", "तुम्ही {{reName}} ला कोणता स्मरणपत्र पाठवले आहे का?");
        mr.put("eligibility.q_sub_judice", "ही तक्रार त्याच तक्रारीशी संबंधित आहे का जी आधीच कोणत्याही न्यायालय, न्यायाधिकरण, लवाद किंवा इतर न्यायिक किंवा अर्ध-न्यायिक मंचासमोर प्रलंबित आहे (फौजदारी कार्यवाही वगळता)?");
        mr.put("eligibility.q_already_settled", "ही तक्रार त्याच तक्रारीशी संबंधित आहे का जी आधीच कोणत्याही न्यायालय, न्यायाधिकरण, लवाद किंवा इतर न्यायिक किंवा अर्ध-न्यायिक मंचाद्वारे निकाली काढली गेली आहे (फौजदारी कार्यवाही वगळता)?");
        mr.put("eligibility.q_through_advocate", "तुमची तक्रार अधिवक्त्यामार्फत केली जात आहे का?");
        mr.put("eligibility.q_pending_ombudsman", "ही तक्रार त्याच तक्रारीशी संबंधित आहे का जी आधीच लोकपालासमोर प्रलंबित आहे?");
        mr.put("eligibility.q_settled_ombudsman", "ही तक्रार त्याच तक्रारीशी संबंधित आहे का जी लोकपालाने आधीच गुणवत्तेवर निकाली काढली आहे?");
        mr.put("eligibility.q_staff_of_re", "तक्रारदार हा नियमित संस्थेचा कर्मचारी आहे का आणि तक्रार नियोक्ता-कर्मचारी संबंधाशी संबंधित आहे का?");
        mr.put("eligibility.q_employee_of_re", "ज्या नियमित संस्थेविरुद्ध ही तक्रार दाखल केली जात आहे, तुम्ही त्या संस्थेचे कर्मचारी आहात/होता का?");
        mr.put("eligibility.q_employer_relationship", "होय असल्यास, तुमची तक्रार नियमित संस्थेच्या कर्मचारी-नियोक्ता संबंधाशी संबंधित आहे का?");

        mr.put("eligibility.q_sub_judice_simple", "तुम्ही आधीच हीच समस्या कोणत्याही न्यायालय, लवाद, किंवा अधिकृत कायदेशीर प्राधिकरणाकडे नेली आहे का (फौजदारी प्रकरणे वगळता)?");
        mr.put("eligibility.q_already_settled_simple", "हीच समस्या आधीच कोणत्याही न्यायालय, लवाद, किंवा अधिकृत कायदेशीर प्राधिकरणाद्वारे सोडवली गेली आहे का (फौजदारी प्रकरणे वगळता)?");
        mr.put("eligibility.q_through_advocate_simple", "तुम्ही वकील किंवा कायदेशीर प्रतिनिधीच्या मदतीने ही तक्रार दाखल करत आहात का?");
        mr.put("eligibility.q_pending_ombudsman_simple", "तुम्ही याच मुद्द्यावर आधीच लोकपालाकडे तक्रार दाखल केली आहे का आणि ती अजूनही तपासणीत आहे?");
        mr.put("eligibility.q_settled_ombudsman_simple", "लोकपालाने आधीच याच तक्रारीची तपासणी आणि निराकरण केले आहे का?");
        mr.put("eligibility.q_staff_of_re_simple", "तुम्ही ज्या बँक/NBFC विरुद्ध तक्रार करत आहात त्याचे कर्मचारी आहात का, आणि तक्रार तुमच्या नोकरीशी संबंधित आहे का?");

        mr.put("eligibility.sub_complaint_date", "ज्या तारखेला तक्रार प्रथम दाखल केली गेली");
        mr.put("eligibility.sub_upload_complaint", "ला पाठवलेल्या तक्रारीची प्रत अपलोड करा");
        mr.put("eligibility.sub_reminder_date", "ज्या तारखेला स्मरणपत्र पाठवले गेले");
        mr.put("eligibility.sub_upload_reminder", "स्मरणपत्राची प्रत अपलोड करा");
        mr.put("eligibility.sub_reply_date", "ज्या तारखेला उत्तर प्राप्त झाले");
        mr.put("eligibility.sub_upload_reply", "उत्तराची प्रत अपलोड करा");
        mr.put("eligibility.sub_are_you_complainant", "होय असल्यास, तुम्ही स्वतः तक्रारदार आहात का?");

        mr.put("eligibility.block_not_filed", "रिझर्व्ह बँक – एकीकृत लोकपाल योजना, 2021 च्या कलम {{clause}} नुसार, तक्रार योजनेअंतर्गत प्रक्रिया करता येत नाही.");
        mr.put("eligibility.block_sub_judice", "तुमची तक्रार न्यायालय/न्यायाधिकरण/लवाद/प्राधिकरणासमोर प्रलंबित असल्याने, ती रिझर्व्ह बँक - एकीकृत लोकपाल योजना, 2021 च्या कलम {{clause}} अंतर्गत अस्वीकार्य म्हणून बंद केली जाईल.");
        mr.put("eligibility.block_already_settled", "तुमची तक्रार आधीच न्यायालय/न्यायाधिकरण/लवाद/प्राधिकरणाद्वारे निकाली काढली गेली असल्याने, ती रिझर्व्ह बँक - एकीकृत लोकपाल योजना, 2021 अंतर्गत अस्वीकार्य म्हणून बंद केली जाईल.");
        mr.put("eligibility.block_pending_ombudsman", "तुमची तक्रार आधीच त्याच तक्रारीवर लोकपालासमोर प्रलंबित आहे. डुप्लिकेट तक्रार दाखल करता येत नाही.");
        mr.put("eligibility.block_settled_ombudsman", "तुमची तक्रार लोकपालाने आधीच गुणवत्तेवर निकाली काढली आहे. याच मुद्द्यावर नवीन तक्रार दाखल करता येत नाही.");
        mr.put("eligibility.block_staff_of_re", "नियमित संस्थेचे कर्मचारी आणि नियोक्ता-कर्मचारी संबंधांशी संबंधित तक्रारी एकीकृत लोकपाल योजनेअंतर्गत दाखल करता येत नाहीत.");
        mr.put("eligibility.block_advocate_not_complainant", "एकीकृत लोकपाल योजनेनुसार, वकिलामार्फत दाखल केलेली तक्रार स्वतः तक्रारदाराने दाखल केली पाहिजे. तुम्ही तक्रारदार नसल्याने, ही तक्रार प्रक्रिया करता येणार नाही.");

        mr.put("eligibility.block_indicated", "तुम्ही");
        mr.put("eligibility.block_in_response", "या प्रश्नाच्या उत्तरात सूचित केल्याप्रमाणे,");
        mr.put("eligibility.block_written_required", "नियमित संस्थेकडे प्रथम लिखित/इलेक्ट्रॉनिक तक्रार दाखल करणे आवश्यक आहे.");
        mr.put("eligibility.block_regret", "त्यानुसार, आम्ही तुम्हाला कळवतो की तुमची सध्याची तक्रार");
        mr.put("eligibility.block_cannot_register", "विरुद्ध योजनेअंतर्गत नोंदणी करता येत नाही. उत्तर चुकून दिले असल्यास, तुम्ही उत्तर बदलू शकता.");
        mr.put("eligibility.block_regards", "सादर, RBI CMS टीम.");
        mr.put("eligibility.show_closure_letter", "बंद करण्याचे पत्र दाखवा");
        mr.put("eligibility.passed_title", "पात्रता उत्तीर्ण");
        mr.put("eligibility.passed_message", "तुम्ही RBI एकीकृत लोकपाल योजना, 2021 अंतर्गत तक्रार दाखल करण्यास पात्र आहात.");
        mr.put("validation.date_invalid", "dd/mm/yyyy स्वरूपात वैध तारीख प्रविष्ट करा");
        mr.put("validation.date_incomplete", "पूर्ण तारीख dd/mm/yyyy म्हणून प्रविष्ट करा");
        mr.put("validation.date_future", "तारीख भविष्यातील असू शकत नाही");
        mr.put("validation.complaint_date_required", "नियमित संस्थेकडे तक्रार दाखल केल्याची तारीख आवश्यक आहे");
        mr.put("validation.complaint_copy_required", "नियमित संस्थेला पाठवलेल्या तक्रारीची प्रत अपलोड करा");
        mr.put("validation.reply_date_required", "उत्तर प्राप्त झाल्याची तारीख आवश्यक आहे");
        mr.put("validation.reply_copy_required", "नियमित संस्थेकडून मिळालेल्या उत्तराची प्रत अपलोड करा");
        mr.put("validation.reply_before_complaint", "उत्तराची तारीख तक्रार दाखल केल्याच्या तारखेपूर्वीची असू शकत नाही");
        mr.put("validation.reminder_date_required", "स्मरणपत्र पाठवल्याची तारीख आवश्यक आहे");
        mr.put("validation.reminder_copy_required", "नियमित संस्थेला पाठवलेल्या स्मरणपत्राची प्रत अपलोड करा");
        mr.put("validation.reminder_before_complaint", "स्मरणपत्राची तारीख तक्रार दाखल केल्याच्या तारखेपूर्वीची असू शकत नाही");
        mr.put("validation.file_type_not_allowed", "केवळ PDF, JPG आणि PNG फाइल्सना परवानगी आहे");
        mr.put("validation.ref_too_long", "संदर्भ क्रमांक 100 अक्षरांपेक्षा जास्त असू शकत नाही");
        mr.put("validation.ref_invalid_chars", "संदर्भ क्रमांकात केवळ अक्षरे, अंक, हायफन, अंडरस्कोर आणि स्लॅश असू शकतात");
        mr.put("eligibility.block_less_than_30_days", "नियमित संस्थेला तुमच्या तक्रारीला उत्तर देण्यासाठी अद्याप {{days}} दिवस दिले गेले नसल्याने, यावेळी तुमची तक्रार नोंदवली जाऊ शकत नाही. नियमित संस्थेकडे तक्रार दाखल केल्याच्या तारखेपासून {{days}} दिवस पूर्ण होईपर्यंत कृपया प्रतीक्षा करा.");
        mr.put("eligibility.time_barred_warning", "नियमित संस्थेकडे तुमची तक्रार {{days}} दिवसांपेक्षा अधिक काळापूर्वी दाखल केली गेली होती. योजनेअंतर्गत दाखल करण्याची मुदत संपली आहे, म्हणून तुमची तक्रार कालबाधित म्हणून बंद केली जाऊ शकते. तुम्ही तरीही पुढे जाऊ शकता.");
        mr.put("eligibility.block_filing_window", "तक्रार दाखल करण्याची मुदत संपली आहे. तक्रार नियमित संस्थेकडे तुमच्या तक्रारीच्या {{days}} दिवसांच्या आत दाखल केली जाणे आवश्यक आहे.");
        mr.put("eligibility.block_post_reply_window", "तक्रार दाखल करण्याची मुदत संपली आहे. तक्रार नियमित संस्थेच्या उत्तराच्या {{days}} दिवसांच्या आत दाखल केली जाणे आवश्यक आहे.");
        mr.put("consent.dpdp_notice", "मी संमती देतो/देते की RBI लागू कायदे आणि डिजिटल पर्सनल डेटा प्रोटेक्शन कायदा, 2023 नुसार माझी तक्रार नोंदविण्यासाठी व सोडविण्यासाठी माझा वैयक्तिक डेटा वापरेल.");
        mr.put("consent.required", "पुढे जाण्यासाठी तुम्हाला डेटा प्रक्रिया घोषणा स्वीकारणे आवश्यक आहे.");
        mr.put("layout.assistance_desc", "संपर्क केंद्र (#14448) इंटरॅक्टिव्ह व्हॉइस रिस्पॉन्स सिस्टम (IVRS) सह 24x7 उपलब्ध आहे, तर संपर्क केंद्र कर्मचाऱ्यांशी जोडण्याची सुविधा सोमवार ते शनिवार (राष्ट्रीय सुट्ट्या वगळता) सकाळी 8:00 ते रात्री 10:00 दरम्यान इंग्रजी, हिंदी आणि दहा प्रादेशिक भाषांमध्ये उपलब्ध आहे.");

        for (Map.Entry<String, String> entry : mr.entrySet()) {
            keyRepo.findByCode(entry.getKey()).ifPresent(key -> {
                if (!translationRepo.existsByTranslationKeyAndLocale(key, "mr")) {
                    Translation t = new Translation();
                    t.setTranslationKey(key);
                    t.setLocale("mr");
                    t.setValue(entry.getValue());
                    translationRepo.save(t);
                }
            });
        }
    }

    private void seedBengaliTranslations() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eligibility.title", "নতুন অভিযোগ দায়ের করুন");
        m.put("eligibility.subtitle", "এগিয়ে যেতে আপনার যোগ্যতা পরীক্ষা করুন");
        m.put("eligibility.mandatory_note", "ঐচ্ছিক চিহ্নিত না হলে সকল ক্ষেত্র বাধ্যতামূলক।");
        m.put("eligibility.select_desc", "আর্থিক প্রতিষ্ঠান নির্বাচন করুন—যেমন ব্যাংক, নন-ব্যাংকিং আর্থিক কোম্পানি (NBFC), বা পেমেন্ট প্রসেসর—যা ভারতীয় রিজার্ভ ব্যাংক দ্বারা লাইসেন্সপ্রাপ্ত এবং তত্ত্বাবধানে রয়েছে, যার বিরুদ্ধে আপনি অভিযোগ দায়ের করতে চান।");
        m.put("eligibility.select_placeholder", "একটি মান নির্বাচন করুন");
        m.put("eligibility.simplify_btn", "সহজ করুন");
        m.put("eligibility.browse_file", "ফাইল নির্বাচন করুন");
        m.put("eligibility.upload_hint", "সমর্থিত ফরম্যাট: PDF, JPG, PNG। সর্বোচ্চ আকার: 5MB");
        m.put("eligibility.opt_yes", "হ্যাঁ");
        m.put("eligibility.opt_no", "না");
        m.put("eligibility.response_mandatory", "উত্তর দেওয়া বাধ্যতামূলক।");
        m.put("eligibility.entity_mandatory", "নিয়ন্ত্রিত সংস্থার নাম বাধ্যতামূলক।");
        m.put("eligibility.entity_search_label", "নাম বা ধরন অনুসারে নিয়ন্ত্রিত সংস্থা খুঁজুন");
        m.put("eligibility.entity_search_placeholder", "সংস্থার নাম বা সংস্থার ধরন খুঁজুন");
        m.put("eligibility.entity_search_clear", "অনুসন্ধান মুছুন");
        m.put("eligibility.entity_search_no_results", "\"{{term}}\"-এর জন্য কোনো ফলাফল পাওয়া যায়নি।");
        m.put("eligibility.entities_unavailable", "নিয়ন্ত্রিত সংস্থার তালিকা লোড করা যায়নি। আবার চেষ্টা করুন — সংস্থার নাম না দিয়ে অভিযোগ দায়ের করা যায় না।");
        m.put("eligibility.q_select_re", "নিয়ন্ত্রিত সংস্থার নাম নির্বাচন করুন");
        m.put("eligibility.q_filed_with_re", "আপনি কি {{reName}}-এ লিখিত/ইলেকট্রনিক অভিযোগ দায়ের করেছেন?");
        m.put("eligibility.q_received_reply", "আপনি কি সংস্থা থেকে কোনো উত্তর পেয়েছেন?");
        m.put("eligibility.q_sent_reminder", "আপনি কি {{reName}}-কে কোনো স্মারকপত্র পাঠিয়েছেন?");
        m.put("eligibility.q_sub_judice", "এই অভিযোগটি কি ইতিমধ্যে কোনো আদালত, ট্রাইব্যুনাল, সালিশ বা অন্য কোনো বিচারিক বা আধা-বিচারিক ফোরামে বিচারাধীন (ফৌজদারি কার্যক্রম ব্যতীত)?");
        m.put("eligibility.q_through_advocate", "আপনার অভিযোগ কি কোনো আইনজীবীর মাধ্যমে করা হচ্ছে?");
        m.put("eligibility.q_pending_ombudsman", "এই অভিযোগটি কি ইতিমধ্যে একই বিষয়ে ওম্বডসম্যানের কাছে বিচারাধীন?");
        m.put("eligibility.q_settled_ombudsman", "এই অভিযোগটি কি ইতিমধ্যে ওম্বডসম্যান দ্বারা গুণবিচারে নিষ্পত্তি হয়েছে?");
        m.put("eligibility.q_staff_of_re", "অভিযোগকারী কি নিয়ন্ত্রিত সংস্থার কর্মচারী এবং অভিযোগটি নিয়োগকর্তা-কর্মচারী সম্পর্কের সাথে সম্পর্কিত?");
        m.put("eligibility.q_employee_of_re", "যে নিয়ন্ত্রিত সংস্থার বিরুদ্ধে এই অভিযোগ দায়ের করা হচ্ছে, আপনি কি সেই সংস্থার কর্মচারী আছেন/ছিলেন?");
        m.put("eligibility.q_employer_relationship", "হ্যাঁ হলে, আপনার অভিযোগ কি নিয়ন্ত্রিত সংস্থার কর্মচারী-নিয়োগকর্তা সম্পর্কের সাথে সম্পর্কিত?");
        m.put("eligibility.sub_complaint_date", "যে তারিখে প্রথম অভিযোগ দায়ের করা হয়েছিল");
        m.put("eligibility.sub_upload_complaint", "-কে পাঠানো অভিযোগের অনুলিপি আপলোড করুন");
        m.put("eligibility.sub_reminder_date", "যে তারিখে স্মারকপত্র পাঠানো হয়েছিল");
        m.put("eligibility.sub_upload_reminder", "স্মারকপত্রের অনুলিপি আপলোড করুন");
        m.put("eligibility.sub_reply_date", "যে তারিখে উত্তর পাওয়া গেছে");
        m.put("eligibility.sub_upload_reply", "উত্তরের অনুলিপি আপলোড করুন");
        m.put("eligibility.sub_are_you_complainant", "হ্যাঁ হলে, আপনি কি নিজে অভিযোগকারী?");
        m.put("eligibility.block_not_filed", "রিজার্ভ ব্যাংক – সমন্বিত ওম্বডসম্যান স্কিম, ২০২১-এর ধারা {{clause}} অনুসারে, এই অভিযোগ স্কিমের অধীনে প্রক্রিয়া করা যাবে না।");
        m.put("eligibility.block_sub_judice", "আপনার অভিযোগ আদালত/ট্রাইব্যুনাল/সালিশ/কর্তৃপক্ষের কাছে বিচারাধীন থাকায়, এটি রিজার্ভ ব্যাংক - সমন্বিত ওম্বডসম্যান স্কিম, ২০২১-এর ধারা {{clause}} অনুসারে অগ্রহণযোগ্য হিসেবে বন্ধ করা হবে।");
        m.put("eligibility.block_pending_ombudsman", "আপনার অভিযোগ ইতিমধ্যে একই বিষয়ে ওম্বডসম্যানের কাছে বিচারাধীন। ডুপ্লিকেট অভিযোগ দায়ের করা যাবে না।");
        m.put("eligibility.block_settled_ombudsman", "আপনার অভিযোগ ইতিমধ্যে ওম্বডসম্যান দ্বারা গুণবিচারে নিষ্পত্তি হয়েছে। একই বিষয়ে নতুন অভিযোগ দায়ের করা যাবে না।");
        m.put("eligibility.block_staff_of_re", "নিয়ন্ত্রিত সংস্থার কর্মচারী এবং নিয়োগকর্তা-কর্মচারী সম্পর্কের অভিযোগ সমন্বিত ওম্বডসম্যান স্কিমের অধীনে দায়ের করা যাবে না।");
        m.put("eligibility.block_advocate_not_complainant", "সমন্বিত ওম্বডসম্যান স্কিম অনুসারে, একজন আইনজীবীর মাধ্যমে দায়ের করা অভিযোগ অভিযোগকারীকে নিজেই দায়ের করতে হবে। যেহেতু আপনি অভিযোগকারী নন, এই অভিযোগ প্রক্রিয়া করা যাবে না।");
        m.put("eligibility.block_indicated", "আপনি যেমন");
        m.put("eligibility.block_in_response", "এই প্রশ্নের উত্তরে জানিয়েছেন,");
        m.put("eligibility.block_written_required", "নিয়ন্ত্রিত সংস্থার কাছে প্রথমে লিখিত/ইলেকট্রনিক অভিযোগ দায়ের করা আবশ্যক।");
        m.put("eligibility.block_regret", "তদনুসারে, আমরা আপনাকে জানাচ্ছি যে আপনার বর্তমান অভিযোগ");
        m.put("eligibility.block_cannot_register", "এর বিরুদ্ধে স্কিমের অধীনে নিবন্ধিত করা যাবে না। যদি উত্তরটি ভুলবশত দেওয়া হয়ে থাকে, আপনি উত্তর পরিবর্তন করতে পারেন।");
        m.put("eligibility.block_regards", "শুভেচ্ছান্তে, RBI CMS টিম।");
        m.put("eligibility.show_closure_letter", "বন্ধের পত্র দেখুন");
        m.put("eligibility.passed_title", "যোগ্যতা উত্তীর্ণ");
        m.put("eligibility.passed_message", "আপনি RBI সমন্বিত ওম্বডসম্যান স্কিম, ২০২১-এর অধীনে অভিযোগ দায়ের করতে যোগ্য।");
        m.put("consent.dpdp_notice", "আমি সম্মতি দিচ্ছি যে RBI প্রযোজ্য আইন এবং ডিজিটাল পার্সোনাল ডেটা প্রোটেকশন আইন, 2023 অনুসারে আমার অভিযোগ নিবন্ধন ও নিষ্পত্তির জন্য আমার ব্যক্তিগত তথ্য ব্যবহার করবে।");
        m.put("consent.required", "চালিয়ে যেতে আপনাকে ডেটা প্রক্রিয়াকরণ ঘোষণা গ্রহণ করতে হবে।");
        m.put("layout.assistance_desc", "যোগাযোগ কেন্দ্র (#14448) ইন্টারেক্টিভ ভয়েস রেসপন্স সিস্টেম (IVRS) সহ 24x7 উপলব্ধ, যখন যোগাযোগ কেন্দ্রের কর্মীদের সাথে সংযোগের সুবিধা সোমবার থেকে শনিবার (জাতীয় ছুটি ব্যতীত) সকাল 8:00 থেকে রাত 10:00 পর্যন্ত ইংরেজি, হিন্দি এবং দশটি আঞ্চলিক ভাষায় উপলব্ধ।");
        m.put("validation.date_invalid", "dd/mm/yyyy ফর্ম্যাটে একটি বৈধ তারিখ লিখুন");
        m.put("validation.date_incomplete", "সম্পূর্ণ তারিখ dd/mm/yyyy হিসেবে লিখুন");
        m.put("validation.date_future", "তারিখ ভবিষ্যতের হতে পারে না");
        m.put("validation.complaint_date_required", "নিয়ন্ত্রিত সংস্থার কাছে অভিযোগ দায়েরের তারিখ আবশ্যক");
        m.put("validation.complaint_copy_required", "নিয়ন্ত্রিত সংস্থাকে পাঠানো অভিযোগের অনুলিপি আপলোড করুন");
        m.put("validation.reply_date_required", "উত্তর পাওয়ার তারিখ আবশ্যক");
        m.put("validation.reply_copy_required", "নিয়ন্ত্রিত সংস্থা থেকে পাওয়া উত্তরের অনুলিপি আপলোড করুন");
        m.put("validation.reply_before_complaint", "উত্তরের তারিখ অভিযোগ দায়েরের তারিখের আগে হতে পারে না");
        m.put("validation.reminder_date_required", "স্মারকপত্র পাঠানোর তারিখ আবশ্যক");
        m.put("validation.reminder_copy_required", "নিয়ন্ত্রিত সংস্থাকে পাঠানো স্মারকপত্রের অনুলিপি আপলোড করুন");
        m.put("validation.reminder_before_complaint", "স্মারকপত্রের তারিখ অভিযোগ দায়েরের তারিখের আগে হতে পারে না");
        m.put("validation.file_type_not_allowed", "শুধুমাত্র PDF, JPG এবং PNG ফাইল অনুমোদিত");
        m.put("validation.ref_too_long", "রেফারেন্স নম্বর ১০০ অক্ষরের বেশি হতে পারে না");
        m.put("validation.ref_invalid_chars", "রেফারেন্স নম্বরে শুধুমাত্র অক্ষর, সংখ্যা, হাইফেন, আন্ডারস্কোর এবং স্ল্যাশ থাকতে পারে");
        m.put("eligibility.block_less_than_30_days", "নিয়ন্ত্রিত সংস্থাকে আপনার অভিযোগের উত্তর দেওয়ার জন্য এখনও {{days}} দিন দেওয়া হয়নি, তাই এই মুহূর্তে আপনার অভিযোগ নিবন্ধিত করা যাবে না। নিয়ন্ত্রিত সংস্থার কাছে অভিযোগ দায়েরের তারিখ থেকে {{days}} দিন পূর্ণ হওয়া পর্যন্ত অপেক্ষা করুন।");
        m.put("eligibility.time_barred_warning", "নিয়ন্ত্রিত সংস্থার কাছে আপনার অভিযোগ {{days}} দিনের বেশি আগে দায়ের করা হয়েছিল। স্কিমের অধীনে দায়েরের সময়সীমা শেষ হয়ে গেছে, তাই আপনার অভিযোগ সময়-বারিত হিসেবে বন্ধ করা হতে পারে। আপনি তবুও এগিয়ে যেতে পারেন।");
        m.put("eligibility.block_filing_window", "অভিযোগ দায়ের করার সময়সীমা শেষ হয়ে গেছে। নিয়ন্ত্রিত সংস্থার কাছে আপনার অভিযোগের {{days}} দিনের মধ্যে অভিযোগ দায়ের করতে হবে।");
        m.put("eligibility.block_post_reply_window", "অভিযোগ দায়ের করার সময়সীমা শেষ হয়ে গেছে। নিয়ন্ত্রিত সংস্থার উত্তরের {{days}} দিনের মধ্যে অভিযোগ দায়ের করতে হবে।");
        saveLocaleTranslations(m, "bn");
    }

    private void seedTeluguTranslations() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eligibility.title", "కొత్త ఫిర్యాదు దాఖలు చేయండి");
        m.put("eligibility.subtitle", "ముందుకు సాగడానికి మీ అర్హతను తనిఖీ చేయండి");
        m.put("eligibility.mandatory_note", "ఐచ్ఛికం అని గుర్తించబడితే తప్ప అన్ని ఫీల్డ్‌లు తప్పనిసరి.");
        m.put("eligibility.select_desc", "ఆర్థిక సంస్థను ఎంచుకోండి—బ్యాంకు, నాన్-బ్యాంకింగ్ ఆర్థిక సంస్థ (NBFC), లేదా చెల్లింపు ప్రాసెసర్—భారతీయ రిజర్వ్ బ్యాంకు లైసెన్స్ పొందిన మరియు పర్యవేక్షించే సంస్థ, దీనిపై మీరు ఫిర్యాదు దాఖలు చేయాలనుకుంటున్నారు.");
        m.put("eligibility.select_placeholder", "ఒక విలువను ఎంచుకోండి");
        m.put("eligibility.simplify_btn", "సరళం చేయండి");
        m.put("eligibility.browse_file", "ఫైల్ ఎంచుకోండి");
        m.put("eligibility.upload_hint", "మద్దతు ఫార్మాట్లు: PDF, JPG, PNG. గరిష్ట పరిమాణం: 5MB");
        m.put("eligibility.opt_yes", "అవును");
        m.put("eligibility.opt_no", "కాదు");
        m.put("eligibility.response_mandatory", "సమాధానం ఇవ్వడం తప్పనిసరి.");
        m.put("eligibility.entity_mandatory", "నియంత్రిత సంస్థ పేరు తప్పనిసరి.");
        m.put("eligibility.entity_search_label", "పేరు లేదా రకం ఆధారంగా నియంత్రిత సంస్థను వెతకండి");
        m.put("eligibility.entity_search_placeholder", "సంస్థ పేరు లేదా సంస్థ రకాన్ని వెతకండి");
        m.put("eligibility.entity_search_clear", "వెతుకులాటను తొలగించండి");
        m.put("eligibility.entity_search_no_results", "\"{{term}}\" కోసం ఫలితాలు కనుగొనబడలేదు.");
        m.put("eligibility.entities_unavailable", "నియంత్రిత సంస్థల జాబితా లోడ్ కాలేదు. మళ్లీ ప్రయత్నించండి — సంస్థ పేరు చెప్పకుండా ఫిర్యాదు దాఖలు చేయలేరు.");
        m.put("eligibility.q_select_re", "నియంత్రిత సంస్థ పేరు ఎంచుకోండి");
        m.put("eligibility.q_filed_with_re", "మీరు {{reName}} వద్ద వ్రాతపూర్వక/ఎలక్ట్రానిక్ ఫిర్యాదు దాఖలు చేశారా?");
        m.put("eligibility.q_received_reply", "మీకు సంస్థ నుండి ఏదైనా సమాధానం వచ్చిందా?");
        m.put("eligibility.q_sent_reminder", "మీరు {{reName}}కు ఏదైనా రిమైండర్ పంపారా?");
        m.put("eligibility.q_sub_judice", "ఈ ఫిర్యాదు ఇప్పటికే ఏదైనా న్యాయస్థానం, ట్రిబ్యునల్, మధ్యవర్తి లేదా ఇతర న్యాయ లేదా అర్ధ-న్యాయ వేదిక ముందు పెండింగ్‌లో ఉందా (నేర విచారణలు మినహా)?");
        m.put("eligibility.q_through_advocate", "మీ ఫిర్యాదు న్యాయవాది ద్వారా చేయబడుతోందా?");
        m.put("eligibility.q_pending_ombudsman", "ఈ ఫిర్యాదు ఇప్పటికే అదే విషయంపై ఓంబుడ్స్‌మన్ ముందు పెండింగ్‌లో ఉందా?");
        m.put("eligibility.q_settled_ombudsman", "ఈ ఫిర్యాదు ఇప్పటికే ఓంబుడ్స్‌మన్ చేత గుణదోషాల ఆధారంగా పరిష్కరించబడిందా?");
        m.put("eligibility.q_staff_of_re", "ఫిర్యాదుదారు నియంత్రిత సంస్థ ఉద్యోగి మరియు ఫిర్యాదు యజమాని-ఉద్యోగి సంబంధానికి సంబంధించినదా?");
        m.put("eligibility.q_employee_of_re", "ఈ ఫిర్యాదు దాఖలు చేస్తున్న నియంత్రిత సంస్థలో మీరు ఉద్యోగి అయి ఉన్నారా/ఉండేవారా?");
        m.put("eligibility.q_employer_relationship", "అవును అయితే, మీ ఫిర్యాదు నియంత్రిత సంస్థ యొక్క ఉద్యోగి-యజమాని సంబంధానికి సంబంధించినదా?");
        m.put("eligibility.sub_complaint_date", "ఫిర్యాదు మొదట దాఖలు చేసిన తేదీ");
        m.put("eligibility.sub_upload_complaint", "కు పంపిన ఫిర్యాదు కాపీ అప్‌లోడ్ చేయండి");
        m.put("eligibility.sub_reminder_date", "రిమైండర్ పంపిన తేదీ");
        m.put("eligibility.sub_upload_reminder", "రిమైండర్ కాపీ అప్‌లోడ్ చేయండి");
        m.put("eligibility.sub_reply_date", "సమాధానం అందిన తేదీ");
        m.put("eligibility.sub_upload_reply", "సమాధానం కాపీ అప్‌లోడ్ చేయండి");
        m.put("eligibility.sub_are_you_complainant", "అవును అయితే, మీరే ఫిర్యాదుదారులా?");
        m.put("eligibility.block_not_filed", "రిజర్వ్ బ్యాంక్ – సమగ్ర ఓంబుడ్స్‌మన్ పథకం, 2021 క్లాజ్ {{clause}} ప్రకారం, ఫిర్యాదును పథకం కింద ప్రాసెస్ చేయడం సాధ్యం కాదు.");
        m.put("eligibility.block_sub_judice", "మీ ఫిర్యాదు న్యాయస్థానం/ట్రిబ్యునల్/మధ్యవర్తి/అధికారం ముందు పెండింగ్‌లో ఉన్నందున, ఇది అనర్హంగా మూసివేయబడుతుంది.");
        m.put("eligibility.block_pending_ombudsman", "మీ ఫిర్యాదు ఇప్పటికే అదే విషయంపై ఓంబుడ్స్‌మన్ ముందు పెండింగ్‌లో ఉంది. డూప్లికేట్ ఫిర్యాదు దాఖలు చేయలేరు.");
        m.put("eligibility.block_settled_ombudsman", "మీ ఫిర్యాదు ఇప్పటికే ఓంబుడ్స్‌మన్ చేత పరిష్కరించబడింది. అదే విషయంపై కొత్త ఫిర్యాదు దాఖలు చేయలేరు.");
        m.put("eligibility.block_staff_of_re", "నియంత్రిత సంస్థ ఉద్యోగి మరియు యజమాని-ఉద్యోగి సంబంధ ఫిర్యాదులు సమగ్ర ఓంబుడ్స్‌మన్ పథకం కింద దాఖలు చేయలేరు.");
        m.put("eligibility.block_advocate_not_complainant", "సమగ్ర ఓంబుడ్స్‌మన్ పథకం ప్రకారం, న్యాయవాది ద్వారా దాఖలు చేసిన ఫిర్యాదును ఫిర్యాదుదారు స్వయంగా దాఖలు చేయాలి. మీరు ఫిర్యాదుదారు కాదు కాబట్టి, ఈ ఫిర్యాదును ప్రాసెస్ చేయలేము.");
        m.put("eligibility.block_indicated", "మీరు");
        m.put("eligibility.block_in_response", "ఈ ప్రశ్నకు సమాధానంగా తెలిపినట్లు,");
        m.put("eligibility.block_written_required", "నియంత్రిత సంస్థ వద్ద ముందుగా వ్రాతపూర్వక/ఎలక్ట్రానిక్ ఫిర్యాదు దాఖలు చేయడం అవసరం.");
        m.put("eligibility.block_regret", "తదనుగుణంగా, మీ ప్రస్తుత ఫిర్యాదు");
        m.put("eligibility.block_cannot_register", "పై పథకం కింద నమోదు చేయడం సాధ్యం కాదని మేము తెలియజేస్తున్నాము. సమాధానం పొరపాటున ఇచ్చినట్లయితే, మీరు సమాధానాన్ని మార్చవచ్చు.");
        m.put("eligibility.block_regards", "వందనాలు, RBI CMS బృందం.");
        m.put("eligibility.show_closure_letter", "మూసివేత పత్రం చూపించు");
        m.put("eligibility.passed_title", "అర్హత ఉత్తీర్ణం");
        m.put("eligibility.passed_message", "మీరు RBI సమగ్ర ఓంబుడ్స్‌మన్ పథకం, 2021 కింద ఫిర్యాదు దాఖలు చేయడానికి అర్హులు.");
        m.put("consent.dpdp_notice", "వర్తించే చట్టాలు మరియు డిజిటల్ పర్సనల్ డేటా ప్రొటెక్షన్ చట్టం, 2023 ప్రకారం నా ఫిర్యాదును నమోదు చేసి పరిష్కరించడానికి RBI నా వ్యక్తిగత డేటాను ఉపయోగించడానికి నేను సమ్మతిస్తున్నాను.");
        m.put("consent.required", "కొనసాగించడానికి మీరు డేటా ప్రాసెసింగ్ ప్రకటనను ఆమోదించాలి.");
        m.put("layout.assistance_desc", "సంప్రదింపు కేంద్రం (#14448) ఇంటరాక్టివ్ వాయిస్ రెస్పాన్స్ సిస్టమ్ (IVRS)తో 24x7 అందుబాటులో ఉంటుంది, అదే సమయంలో సంప్రదింపు కేంద్ర సిబ్బందితో అనుసంధానం చేసే సౌకర్యం సోమవారం నుండి శనివారం (జాతీయ సెలవులు మినహా) ఉదయం 8:00 నుండి రాత్రి 10:00 వరకు ఆంగ్లం, హిందీ మరియు పది ప్రాంతీయ భాషల్లో అందుబాటులో ఉంటుంది.");
        m.put("validation.date_invalid", "dd/mm/yyyy ఫార్మాట్‌లో సరైన తేదీని నమోదు చేయండి");
        m.put("validation.date_incomplete", "పూర్తి తేదీని dd/mm/yyyy గా నమోదు చేయండి");
        m.put("validation.date_future", "తేదీ భవిష్యత్తులో ఉండకూడదు");
        m.put("validation.complaint_date_required", "నియంత్రిత సంస్థ వద్ద ఫిర్యాదు దాఖలు చేసిన తేదీ అవసరం");
        m.put("validation.complaint_copy_required", "నియంత్రిత సంస్థకు పంపిన ఫిర్యాదు కాపీని అప్‌లోడ్ చేయండి");
        m.put("validation.reply_date_required", "సమాధానం అందుకున్న తేదీ అవసరం");
        m.put("validation.reply_copy_required", "నియంత్రిత సంస్థ నుండి అందిన సమాధానం కాపీని అప్‌లోడ్ చేయండి");
        m.put("validation.reply_before_complaint", "సమాధానం తేదీ ఫిర్యాదు దాఖలు తేదీకి ముందు ఉండకూడదు");
        m.put("validation.reminder_date_required", "రిమైండర్ పంపిన తేదీ అవసరం");
        m.put("validation.reminder_copy_required", "నియంత్రిత సంస్థకు పంపిన రిమైండర్ కాపీని అప్‌లోడ్ చేయండి");
        m.put("validation.reminder_before_complaint", "రిమైండర్ తేదీ ఫిర్యాదు దాఖలు తేదీకి ముందు ఉండకూడదు");
        m.put("validation.file_type_not_allowed", "PDF, JPG మరియు PNG ఫైల్‌లు మాత్రమే అనుమతించబడతాయి");
        m.put("validation.ref_too_long", "రిఫరెన్స్ నంబర్ 100 అక్షరాలకు మించకూడదు");
        m.put("validation.ref_invalid_chars", "రిఫరెన్స్ నంబర్‌లో అక్షరాలు, అంకెలు, హైఫన్, అండర్‌స్కోర్ మరియు స్లాష్ మాత్రమే ఉండాలి");
        m.put("eligibility.block_less_than_30_days", "మీ ఫిర్యాదుకు సమాధానం ఇవ్వడానికి నియంత్రిత సంస్థకు ఇంకా {{days}} రోజులు ఇవ్వబడలేదు, కాబట్టి ఈ సమయంలో మీ ఫిర్యాదును నమోదు చేయలేము. నియంత్రిత సంస్థ వద్ద ఫిర్యాదు దాఖలు చేసిన తేదీ నుండి {{days}} రోజులు పూర్తయ్యే వరకు వేచి ఉండండి.");
        m.put("eligibility.time_barred_warning", "నియంత్రిత సంస్థ వద్ద మీ ఫిర్యాదు {{days}} రోజుల కంటే ముందు దాఖలు చేయబడింది. పథకం కింద దాఖలు చేసే గడువు ముగిసింది, కాబట్టి మీ ఫిర్యాదు కాలపరిమితి ముగిసినదిగా మూసివేయబడవచ్చు. మీరు ఇంకా కొనసాగవచ్చు.");
        m.put("eligibility.block_filing_window", "ఫిర్యాదు దాఖలు చేసే గడువు ముగిసింది. నియంత్రిత సంస్థ వద్ద మీ ఫిర్యాదు చేసిన {{days}} రోజులలోపు ఫిర్యాదు దాఖలు చేయాలి.");
        m.put("eligibility.block_post_reply_window", "ఫిర్యాదు దాఖలు చేసే గడువు ముగిసింది. నియంత్రిత సంస్థ సమాధానం ఇచ్చిన {{days}} రోజులలోపు ఫిర్యాదు దాఖలు చేయాలి.");
        saveLocaleTranslations(m, "te");
    }

    private void seedTamilTranslations() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eligibility.title", "புதிய புகார் பதிவு செய்யுங்கள்");
        m.put("eligibility.subtitle", "தொடர உங்கள் தகுதியை சரிபார்க்கவும்");
        m.put("eligibility.mandatory_note", "விருப்பமானது எனக் குறிக்கப்படாத அனைத்து புலங்களும் கட்டாயம்.");
        m.put("eligibility.select_desc", "நிதி நிறுவனத்தை தேர்ந்தெடுக்கவும்—வங்கி, வங்கி அல்லாத நிதி நிறுவனம் (NBFC), அல்லது பணம் செலுத்தும் செயலி—இந்திய ரிசர்வ் வங்கியால் உரிமம் பெற்ற மற்றும் மேற்பார்வையிடப்படும், இதன் மீது நீங்கள் புகார் பதிவு செய்ய விரும்புகிறீர்கள்.");
        m.put("eligibility.select_placeholder", "ஒரு மதிப்பைத் தேர்ந்தெடுக்கவும்");
        m.put("eligibility.simplify_btn", "எளிமையாக்கு");
        m.put("eligibility.browse_file", "கோப்பை தேர்வு செய்");
        m.put("eligibility.upload_hint", "ஆதரிக்கப்படும் வடிவங்கள்: PDF, JPG, PNG. அதிகபட்ச அளவு: 5MB");
        m.put("eligibility.opt_yes", "ஆம்");
        m.put("eligibility.opt_no", "இல்லை");
        m.put("eligibility.response_mandatory", "பதில் அளிப்பது கட்டாயமாகும்.");
        m.put("eligibility.entity_mandatory", "ஒழுங்குமுறை நிறுவனத்தின் பெயர் கட்டாயமாகும்.");
        m.put("eligibility.entity_search_label", "பெயர் அல்லது வகை மூலம் ஒழுங்குமுறை நிறுவனத்தைத் தேடுங்கள்");
        m.put("eligibility.entity_search_placeholder", "நிறுவனப் பெயர் அல்லது நிறுவன வகையைத் தேடுங்கள்");
        m.put("eligibility.entity_search_clear", "தேடலை அழிக்கவும்");
        m.put("eligibility.entity_search_no_results", "\"{{term}}\" க்கான முடிவுகள் எதுவும் இல்லை.");
        m.put("eligibility.entities_unavailable", "ஒழுங்குமுறை நிறுவனங்களின் பட்டியலை ஏற்ற முடியவில்லை. மீண்டும் முயலுங்கள் — நிறுவனத்தைக் குறிப்பிடாமல் புகார் அளிக்க முடியாது.");
        m.put("eligibility.q_select_re", "ஒழுங்குமுறை நிறுவனத்தின் பெயரைத் தேர்ந்தெடுக்கவும்");
        m.put("eligibility.q_filed_with_re", "நீங்கள் {{reName}} இல் எழுத்துப்பூர்வ/மின்னணு புகார் அளித்துள்ளீர்களா?");
        m.put("eligibility.q_received_reply", "நிறுவனத்திடம் இருந்து ஏதேனும் பதில் கிடைத்ததா?");
        m.put("eligibility.q_sent_reminder", "நீங்கள் {{reName}}க்கு ஏதேனும் நினைவூட்டல் அனுப்பினீர்களா?");
        m.put("eligibility.q_sub_judice", "இந்தப் புகார் ஏற்கனவே ஏதேனும் நீதிமன்றம், தீர்ப்பாயம், நடுவர் அல்லது பிற நீதி அல்லது அரை-நீதி மன்றத்தில் நிலுவையில் உள்ளதா (குற்றவியல் நடவடிக்கைகள் தவிர)?");
        m.put("eligibility.q_through_advocate", "உங்கள் புகார் வழக்கறிஞர் மூலம் தாக்கல் செய்யப்படுகிறதா?");
        m.put("eligibility.q_pending_ombudsman", "இந்தப் புகார் ஏற்கனவே அதே விஷயத்தில் குறைதீர்ப்பாளரிடம் நிலுவையில் உள்ளதா?");
        m.put("eligibility.q_settled_ombudsman", "இந்தப் புகார் ஏற்கனவே குறைதீர்ப்பாளரால் தீர்வு செய்யப்பட்டுள்ளதா?");
        m.put("eligibility.q_staff_of_re", "புகாரளிப்பவர் ஒழுங்குமுறை நிறுவனத்தின் ஊழியரா மற்றும் புகார் முதலாளி-ஊழியர் உறவு தொடர்பானதா?");
        m.put("eligibility.q_employee_of_re", "இந்தப் புகார் தாக்கல் செய்யப்படும் ஒழுங்குமுறை நிறுவனத்தில் நீங்கள் ஊழியராக இருக்கிறீர்களா/இருந்தீர்களா?");
        m.put("eligibility.q_employer_relationship", "ஆம் என்றால், உங்கள் புகார் ஒழுங்குமுறை நிறுவனத்தின் ஊழியர்-முதலாளி உறவு தொடர்பானதா?");
        m.put("eligibility.sub_complaint_date", "புகார் முதலில் பதிவு செய்த தேதி");
        m.put("eligibility.sub_upload_complaint", "க்கு அனுப்பிய புகாரின் நகலை பதிவேற்றவும்");
        m.put("eligibility.sub_reminder_date", "நினைவூட்டல் அனுப்பிய தேதி");
        m.put("eligibility.sub_upload_reminder", "நினைவூட்டல் நகலை பதிவேற்றவும்");
        m.put("eligibility.sub_reply_date", "பதில் பெற்ற தேதி");
        m.put("eligibility.sub_upload_reply", "பதில் நகலை பதிவேற்றவும்");
        m.put("eligibility.sub_are_you_complainant", "ஆம் என்றால், நீங்களே புகாரளிப்பவரா?");
        m.put("eligibility.block_not_filed", "ரிசர்வ் வங்கி – ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டம், 2021 பிரிவு {{clause}} படி, புகார் திட்டத்தின் கீழ் செயல்படுத்த முடியாது.");
        m.put("eligibility.block_sub_judice", "உங்கள் புகார் நீதிமன்றம்/தீர்ப்பாயம்/நடுவர்/அதிகாரத்தின் முன் நிலுவையில் உள்ளதால், இது தகுதியற்றதாக மூடப்படும்.");
        m.put("eligibility.block_pending_ombudsman", "உங்கள் புகார் ஏற்கனவே அதே விஷயத்தில் குறைதீர்ப்பாளரிடம் நிலுவையில் உள்ளது. நகல் புகார் தாக்கல் செய்ய முடியாது.");
        m.put("eligibility.block_settled_ombudsman", "உங்கள் புகார் ஏற்கனவே குறைதீர்ப்பாளரால் தீர்வு செய்யப்பட்டுள்ளது. அதே விஷயத்தில் புதிய புகார் தாக்கல் செய்ய முடியாது.");
        m.put("eligibility.block_staff_of_re", "ஒழுங்குமுறை நிறுவன ஊழியர் மற்றும் முதலாளி-ஊழியர் உறவு தொடர்பான புகார்கள் ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டத்தின் கீழ் தாக்கல் செய்ய முடியாது.");
        m.put("eligibility.block_advocate_not_complainant", "ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டத்தின்படி, வழக்கறிஞர் மூலம் தாக்கல் செய்யப்படும் புகாரை புகாரளிப்பவரே தாக்கல் செய்ய வேண்டும். நீங்கள் புகாரளிப்பவர் அல்ல என்பதால், இந்தப் புகாரைச் செயல்படுத்த முடியாது.");
        m.put("eligibility.block_indicated", "நீங்கள்");
        m.put("eligibility.block_in_response", "இந்த கேள்விக்கான பதிலாக தெரிவித்தபடி,");
        m.put("eligibility.block_written_required", "ஒழுங்குமுறை நிறுவனத்திடம் முதலில் எழுத்து/மின்னணு புகார் அளிக்க வேண்டும்.");
        m.put("eligibility.block_regret", "அதன்படி, உங்கள் தற்போதைய புகார்");
        m.put("eligibility.block_cannot_register", "மீது திட்டத்தின் கீழ் பதிவு செய்ய இயலாது என்பதை தெரிவிக்கிறோம். பதில் தவறுதலாக அளிக்கப்பட்டிருந்தால், நீங்கள் பதிலை மாற்றலாம்.");
        m.put("eligibility.block_regards", "வணக்கம், RBI CMS குழு.");
        m.put("eligibility.show_closure_letter", "முடிவுக் கடிதத்தைக் காட்டு");
        m.put("eligibility.passed_title", "தகுதி தேர்ச்சி");
        m.put("eligibility.passed_message", "நீங்கள் RBI ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டம், 2021 இன் கீழ் புகார் அளிக்க தகுதியானவர்.");
        m.put("consent.dpdp_notice", "பொருந்தும் சட்டங்கள் மற்றும் டிஜிட்டல் தனிநபர் தரவு பாதுகாப்பு சட்டம், 2023 ஆகியவற்றுக்கு இணங்க எனது புகாரைப் பதிவு செய்து தீர்க்க RBI எனது தனிநபர் தரவைப் பயன்படுத்த நான் ஒப்புதல் அளிக்கிறேன்.");
        m.put("consent.required", "தொடர்வதற்கு நீங்கள் தரவு செயலாக்க அறிவிப்பை ஏற்க வேண்டும்.");
        m.put("layout.assistance_desc", "தொடர்பு மையம் (#14448) ஊடாடும் குரல் பதில் அமைப்பு (IVRS) மூலம் 24x7 கிடைக்கிறது, அதே நேரத்தில் தொடர்பு மைய பணியாளர்களுடன் இணைக்கும் வசதி திங்கள் முதல் சனி வரை (தேசிய விடுமுறைகள் தவிர) காலை 8:00 முதல் இரவு 10:00 வரை ஆங்கிலம், இந்தி மற்றும் பத்து பிராந்திய மொழிகளில் கிடைக்கிறது.");
        m.put("validation.date_invalid", "dd/mm/yyyy வடிவத்தில் சரியான தேதியை உள்ளிடவும்");
        m.put("validation.date_incomplete", "முழு தேதியை dd/mm/yyyy ஆக உள்ளிடவும்");
        m.put("validation.date_future", "தேதி எதிர்காலமாக இருக்கக்கூடாது");
        m.put("validation.complaint_date_required", "ஒழுங்குமுறை நிறுவனத்தில் புகார் அளித்த தேதி தேவை");
        m.put("validation.complaint_copy_required", "ஒழுங்குமுறை நிறுவனத்திற்கு அனுப்பிய புகாரின் நகலைப் பதிவேற்றவும்");
        m.put("validation.reply_date_required", "பதில் பெறப்பட்ட தேதி தேவை");
        m.put("validation.reply_copy_required", "ஒழுங்குமுறை நிறுவனத்திடமிருந்து பெற்ற பதிலின் நகலைப் பதிவேற்றவும்");
        m.put("validation.reply_before_complaint", "பதில் தேதி புகார் அளித்த தேதிக்கு முன்னதாக இருக்கக்கூடாது");
        m.put("validation.reminder_date_required", "நினைவூட்டல் அனுப்பிய தேதி தேவை");
        m.put("validation.reminder_copy_required", "ஒழுங்குமுறை நிறுவனத்திற்கு அனுப்பிய நினைவூட்டலின் நகலைப் பதிவேற்றவும்");
        m.put("validation.reminder_before_complaint", "நினைவூட்டல் தேதி புகார் அளித்த தேதிக்கு முன்னதாக இருக்கக்கூடாது");
        m.put("validation.file_type_not_allowed", "PDF, JPG மற்றும் PNG கோப்புகள் மட்டுமே அனுமதிக்கப்படும்");
        m.put("validation.ref_too_long", "குறிப்பு எண் 100 எழுத்துகளுக்கு மேல் இருக்கக்கூடாது");
        m.put("validation.ref_invalid_chars", "குறிப்பு எண்ணில் எழுத்துகள், இலக்கங்கள், இணைப்புக்குறி, அடிக்கோடு மற்றும் சாய்வுக்கோடு மட்டுமே இருக்க வேண்டும்");
        m.put("eligibility.block_less_than_30_days", "உங்கள் புகாருக்கு பதிலளிக்க ஒழுங்குமுறை நிறுவனத்திற்கு இன்னும் {{days}} நாட்கள் வழங்கப்படவில்லை, எனவே இந்த நேரத்தில் உங்கள் புகாரைப் பதிவு செய்ய முடியாது. ஒழுங்குமுறை நிறுவனத்தில் புகார் அளித்த தேதியிலிருந்து {{days}} நாட்கள் நிறைவடையும் வரை காத்திருக்கவும்.");
        m.put("eligibility.time_barred_warning", "ஒழுங்குமுறை நிறுவனத்தில் உங்கள் புகார் {{days}} நாட்களுக்கு முன்பே அளிக்கப்பட்டது. திட்டத்தின் கீழ் தாக்கல் செய்யும் காலம் முடிந்துவிட்டது, எனவே உங்கள் புகார் காலவரம்பு கடந்ததாக முடிக்கப்படலாம். நீங்கள் இன்னும் தொடரலாம்.");
        m.put("eligibility.block_filing_window", "புகார் அளிக்கும் காலம் முடிந்துவிட்டது. ஒழுங்குமுறை நிறுவனத்தில் உங்கள் புகார் அளித்த {{days}} நாட்களுக்குள் புகார் அளிக்கப்பட வேண்டும்.");
        m.put("eligibility.block_post_reply_window", "புகார் அளிக்கும் காலம் முடிந்துவிட்டது. ஒழுங்குமுறை நிறுவனத்தின் பதிலுக்குப் பிறகு {{days}} நாட்களுக்குள் புகார் அளிக்கப்பட வேண்டும்.");
        saveLocaleTranslations(m, "ta");
    }

    private void seedGujaratiTranslations() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eligibility.title", "નવી ફરિયાદ દાખલ કરો");
        m.put("eligibility.subtitle", "આગળ વધવા માટે તમારી પાત્રતા તપાસો");
        m.put("eligibility.mandatory_note", "વૈકલ્પિક ચિહ્નિત ન હોય તો બધા ક્ષેત્રો ફરજિયાત છે.");
        m.put("eligibility.select_desc", "નાણાકીય સંસ્થા પસંદ કરો—જેમ કે બેંક, નોન-બેંકિંગ ફાઇનાન્શિયલ કંપની (NBFC), અથવા પેમેન્ટ પ્રોસેસર—જે ભારતીય રિઝર્વ બેંક દ્વારા લાઇસન્સ ધરાવે છે, જેની વિરુદ્ધ તમે ફરિયાદ દાખલ કરવા માંગો છો.");
        m.put("eligibility.select_placeholder", "એક મૂલ્ય પસંદ કરો");
        m.put("eligibility.simplify_btn", "સરળ કરો");
        m.put("eligibility.browse_file", "ફાઇલ પસંદ કરો");
        m.put("eligibility.upload_hint", "સમર્થિત ફોર્મેટ: PDF, JPG, PNG. મહત્તમ કદ: 5MB");
        m.put("eligibility.opt_yes", "હા");
        m.put("eligibility.opt_no", "ના");
        m.put("eligibility.response_mandatory", "જવાબ આપવો ફરજિયાત છે.");
        m.put("eligibility.entity_mandatory", "નિયમન કરાયેલ સંસ્થાનું નામ ફરજિયાત છે.");
        m.put("eligibility.entity_search_label", "નામ અથવા પ્રકાર દ્વારા નિયમન કરાયેલ સંસ્થા શોધો");
        m.put("eligibility.entity_search_placeholder", "સંસ્થાનું નામ અથવા સંસ્થાનો પ્રકાર શોધો");
        m.put("eligibility.entity_search_clear", "શોધ સાફ કરો");
        m.put("eligibility.entity_search_no_results", "\"{{term}}\" માટે કોઈ પરિણામ મળ્યું નથી.");
        m.put("eligibility.entities_unavailable", "નિયમન કરાયેલ સંસ્થાઓની સૂચિ લોડ થઈ શકી નથી. કૃપા કરીને ફરી પ્રયાસ કરો — સંસ્થાનું નામ આપ્યા વિના ફરિયાદ દાખલ કરી શકાતી નથી.");
        m.put("eligibility.q_select_re", "નિયંત્રિત સંસ્થાનું નામ પસંદ કરો");
        m.put("eligibility.q_filed_with_re", "શું તમે {{reName}} પાસે લેખિત/ઈલેક્ટ્રોનિક ફરિયાદ દાખલ કરી છે?");
        m.put("eligibility.q_received_reply", "શું તમને સંસ્થા તરફથી કોઈ જવાબ મળ્યો છે?");
        m.put("eligibility.q_sent_reminder", "શું તમે {{reName}}ને કોઈ રીમાઇન્ડર મોકલ્યું છે?");
        m.put("eligibility.q_sub_judice", "શું આ ફરિયાદ પહેલેથી કોઈ અદાલત, ટ્રિબ્યુનલ, આર્બિટ્રેટર અથવા અન્ય ન્યાયિક ફોરમ સમક્ષ પેન્ડિંગ છે (ફોજદારી કાર્યવાહી સિવાય)?");
        m.put("eligibility.q_through_advocate", "શું તમારી ફરિયાદ વકીલ મારફતે કરવામાં આવી રહી છે?");
        m.put("eligibility.q_pending_ombudsman", "શું આ ફરિયાદ પહેલેથી એ જ વિષય પર ઓમ્બડ્સમેન સમક્ષ પેન્ડિંગ છે?");
        m.put("eligibility.q_settled_ombudsman", "શું આ ફરિયાદ પહેલેથી ઓમ્બડ્સમેન દ્વારા ગુણદોષ પર નિકાલ કરવામાં આવી છે?");
        m.put("eligibility.q_staff_of_re", "શું ફરિયાદી નિયંત્રિત સંસ્થાનો કર્મચારી છે અને ફરિયાદ નોકરીદાતા-કર્મચારી સંબંધ સાથે સંબંધિત છે?");
        m.put("eligibility.q_employee_of_re", "જે નિયંત્રિત સંસ્થા વિરુદ્ધ આ ફરિયાદ દાખલ કરવામાં આવી રહી છે, તમે તેના કર્મચારી છો/હતા?");
        m.put("eligibility.q_employer_relationship", "હા તો, શું તમારી ફરિયાદ નિયંત્રિત સંસ્થાના કર્મચારી-નોકરીદાતા સંબંધ સાથે સંબંધિત છે?");
        m.put("eligibility.sub_complaint_date", "જે તારીખે ફરિયાદ પ્રથમ દાખલ કરવામાં આવી");
        m.put("eligibility.sub_upload_complaint", "ને મોકલેલી ફરિયાદની નકલ અપલોડ કરો");
        m.put("eligibility.sub_reminder_date", "જે તારીખે રીમાઇન્ડર મોકલવામાં આવ્યું");
        m.put("eligibility.sub_upload_reminder", "રીમાઇન્ડરની નકલ અપલોડ કરો");
        m.put("eligibility.sub_reply_date", "જે તારીખે જવાબ મળ્યો");
        m.put("eligibility.sub_upload_reply", "જવાબની નકલ અપલોડ કરો");
        m.put("eligibility.sub_are_you_complainant", "હા તો, શું તમે પોતે ફરિયાદી છો?");
        m.put("eligibility.block_not_filed", "રિઝર્વ બેંક – સંકલિત ઓમ્બડ્સમેન યોજના, 2021 ની કલમ {{clause}} મુજબ, ફરિયાદ યોજના હેઠળ પ્રક્રિયા કરી શકાતી નથી.");
        m.put("eligibility.block_sub_judice", "તમારી ફરિયાદ અદાલત/ટ્રિબ્યુનલ/આર્બિટ્રેટર/સત્તાધિકારી સમક્ષ પેન્ડિંગ હોવાથી, તેને અયોગ્ય તરીકે બંધ કરવામાં આવશે.");
        m.put("eligibility.block_pending_ombudsman", "તમારી ફરિયાદ પહેલેથી એ જ વિષય પર ઓમ્બડ્સમેન સમક્ષ પેન્ડિંગ છે. ડુપ્લિકેટ ફરિયાદ દાખલ કરી શકાતી નથી.");
        m.put("eligibility.block_settled_ombudsman", "તમારી ફરિયાદ પહેલેથી ઓમ્બડ્સમેન દ્વારા નિકાલ કરવામાં આવી છે. એ જ વિષય પર નવી ફરિયાદ દાખલ કરી શકાતી નથી.");
        m.put("eligibility.block_staff_of_re", "નિયંત્રિત સંસ્થાના કર્મચારી અને નોકરીદાતા-કર્મચારી સંબંધની ફરિયાદો સંકલિત ઓમ્બડ્સમેન યોજના હેઠળ દાખલ કરી શકાતી નથી.");
        m.put("eligibility.block_advocate_not_complainant", "સંકલિત ઓમ્બડ્સમેન યોજના અનુસાર, વકીલ મારફતે દાખલ કરેલી ફરિયાદ ફરિયાદીએ પોતે દાખલ કરવી જોઈએ. તમે ફરિયાદી નથી, તેથી આ ફરિયાદ પર પ્રક્રિયા કરી શકાતી નથી.");
        m.put("eligibility.block_indicated", "તમે");
        m.put("eligibility.block_in_response", "આ પ્રશ્નના જવાબમાં જણાવ્યા મુજબ,");
        m.put("eligibility.block_written_required", "નિયમિત સંસ્થા પાસે પ્રથમ લેખિત/ઇલેક્ટ્રોનિક ફરિયાદ દાખલ કરવી જરૂરી છે.");
        m.put("eligibility.block_regret", "તદનુસાર, અમે તમને જાણ કરીએ છીએ કે તમારી વર્તમાન ફરિયાદ");
        m.put("eligibility.block_cannot_register", "સામે યોજના હેઠળ નોંધણી કરી શકાતી નથી. જો જવાબ ભૂલથી આપવામાં આવ્યો હોય, તો તમે જવાબ બદલી શકો છો.");
        m.put("eligibility.block_regards", "સાદર, RBI CMS ટીમ.");
        m.put("eligibility.show_closure_letter", "બંધ કરવાનો પત્ર બતાવો");
        m.put("eligibility.passed_title", "પાત્રતા પાસ");
        m.put("eligibility.passed_message", "તમે RBI સંકલિત લોકપાલ યોજના, 2021 હેઠળ ફરિયાદ દાખલ કરવા માટે પાત્ર છો.");
        m.put("consent.dpdp_notice", "લાગુ પડતા કાયદાઓ અને ડિજિટલ પર્સનલ ડેટા પ્રોટેક્શન અધિનિયમ, 2023 અનુસાર મારી ફરિયાદ નોંધવા અને ઉકેલવા માટે RBI મારા વ્યક્તિગત ડેટાનો ઉપયોગ કરે તે માટે હું સંમતિ આપું છું.");
        m.put("consent.required", "આગળ વધવા માટે તમારે ડેટા પ્રોસેસિંગ ઘોષણા સ્વીકારવી આવશ્યક છે.");
        m.put("layout.assistance_desc", "સંપર્ક કેન્દ્ર (#14448) ઇન્ટરેક્ટિવ વોઇસ રિસ્પોન્સ સિસ્ટમ (IVRS) સાથે 24x7 ઉપલબ્ધ છે, જ્યારે સંપર્ક કેન્દ્ર કર્મચારીઓ સાથે જોડાવાની સુવિધા સોમવારથી શનિવાર (રાષ્ટ્રીય રજાઓ સિવાય) સવારે 8:00 થી રાત્રે 10:00 સુધી અંગ્રેજી, હિન્દી અને દસ પ્રાદેશિક ભાષાઓમાં ઉપલબ્ધ છે.");
        m.put("validation.date_invalid", "dd/mm/yyyy ફોર્મેટમાં માન્ય તારીખ દાખલ કરો");
        m.put("validation.date_incomplete", "સંપૂર્ણ તારીખ dd/mm/yyyy તરીકે દાખલ કરો");
        m.put("validation.date_future", "તારીખ ભવિષ્યની હોઈ શકતી નથી");
        m.put("validation.complaint_date_required", "નિયંત્રિત સંસ્થા પાસે ફરિયાદ દાખલ કરવાની તારીખ આવશ્યક છે");
        m.put("validation.complaint_copy_required", "નિયંત્રિત સંસ્થાને મોકલેલી ફરિયાદની નકલ અપલોડ કરો");
        m.put("validation.reply_date_required", "જવાબ મળ્યાની તારીખ આવશ્યક છે");
        m.put("validation.reply_copy_required", "નિયંત્રિત સંસ્થા પાસેથી મળેલા જવાબની નકલ અપલોડ કરો");
        m.put("validation.reply_before_complaint", "જવાબની તારીખ ફરિયાદ દાખલ કરવાની તારીખ પહેલાંની હોઈ શકતી નથી");
        m.put("validation.reminder_date_required", "રિમાઇન્ડર મોકલ્યાની તારીખ આવશ્યક છે");
        m.put("validation.reminder_copy_required", "નિયંત્રિત સંસ્થાને મોકલેલા રિમાઇન્ડરની નકલ અપલોડ કરો");
        m.put("validation.reminder_before_complaint", "રિમાઇન્ડરની તારીખ ફરિયાદ દાખલ કરવાની તારીખ પહેલાંની હોઈ શકતી નથી");
        m.put("validation.file_type_not_allowed", "ફક્ત PDF, JPG અને PNG ફાઇલોની પરવાનગી છે");
        m.put("validation.ref_too_long", "સંદર્ભ નંબર 100 અક્ષરોથી વધુ હોઈ શકતો નથી");
        m.put("validation.ref_invalid_chars", "સંદર્ભ નંબરમાં ફક્ત અક્ષરો, અંકો, હાઇફન, અંડરસ્કોર અને સ્લેશ હોઈ શકે છે");
        m.put("eligibility.block_less_than_30_days", "નિયંત્રિત સંસ્થાને તમારી ફરિયાદનો જવાબ આપવા માટે હજુ {{days}} દિવસ આપવામાં આવ્યા નથી, તેથી આ સમયે તમારી ફરિયાદ નોંધી શકાતી નથી. નિયંત્રિત સંસ્થા પાસે ફરિયાદ દાખલ કર્યાની તારીખથી {{days}} દિવસ પૂરા થાય ત્યાં સુધી કૃપા કરીને પ્રતીક્ષા કરો.");
        m.put("eligibility.time_barred_warning", "નિયંત્રિત સંસ્થા પાસે તમારી ફરિયાદ {{days}} દિવસ પહેલાં દાખલ કરવામાં આવી હતી. યોજના હેઠળ દાખલ કરવાની મુદત પૂરી થઈ ગઈ છે, તેથી તમારી ફરિયાદ સમય-બાધિત તરીકે બંધ થઈ શકે છે. તમે હજુ પણ આગળ વધી શકો છો.");
        m.put("eligibility.block_filing_window", "ફરિયાદ દાખલ કરવાની મુદત પૂરી થઈ ગઈ છે. નિયંત્રિત સંસ્થા પાસે તમારી ફરિયાદના {{days}} દિવસમાં ફરિયાદ દાખલ કરવી આવશ્યક છે.");
        m.put("eligibility.block_post_reply_window", "ફરિયાદ દાખલ કરવાની મુદત પૂરી થઈ ગઈ છે. નિયંત્રિત સંસ્થાના જવાબના {{days}} દિવસમાં ફરિયાદ દાખલ કરવી આવશ્યક છે.");
        saveLocaleTranslations(m, "gu");
    }

    private void seedUrduTranslations() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eligibility.title", "نئی شکایت درج کریں");
        m.put("eligibility.subtitle", "آگے بڑھنے کے لیے اپنی اہلیت جانچیں");
        m.put("eligibility.mandatory_note", "تمام فیلڈز لازمی ہیں جب تک اختیاری نشان زد نہ ہو۔");
        m.put("eligibility.select_desc", "مالیاتی ادارہ منتخب کریں—جیسے بینک، نان بینکنگ مالیاتی کمپنی (NBFC)، یا ادائیگی پروسیسر—جو ریزرو بینک آف انڈیا سے لائسنس یافتہ ہے، جس کے خلاف آپ شکایت درج کرنا چاہتے ہیں۔");
        m.put("eligibility.select_placeholder", "ایک قدر منتخب کریں");
        m.put("eligibility.simplify_btn", "آسان کریں");
        m.put("eligibility.browse_file", "فائل منتخب کریں");
        m.put("eligibility.upload_hint", "معاون فارمیٹ: PDF, JPG, PNG۔ زیادہ سے زیادہ سائز: 5MB");
        m.put("eligibility.opt_yes", "ہاں");
        m.put("eligibility.opt_no", "نہیں");
        m.put("eligibility.response_mandatory", "جواب دینا لازمی ہے۔");
        m.put("eligibility.entity_mandatory", "ریگولیٹڈ ادارے کا نام لازمی ہے۔");
        m.put("eligibility.entity_search_label", "نام یا قسم کے ذریعے ریگولیٹڈ ادارہ تلاش کریں");
        m.put("eligibility.entity_search_placeholder", "ادارے کا نام یا ادارے کی قسم تلاش کریں");
        m.put("eligibility.entity_search_clear", "تلاش صاف کریں");
        m.put("eligibility.entity_search_no_results", "\"{{term}}\" کے لیے کوئی نتیجہ نہیں ملا۔");
        m.put("eligibility.entities_unavailable", "ریگولیٹڈ اداروں کی فہرست لوڈ نہیں ہو سکی۔ براہِ کرم دوبارہ کوشش کریں — ادارے کا نام بتائے بغیر شکایت درج نہیں کی جا سکتی۔");
        m.put("eligibility.q_select_re", "ریگولیٹڈ ادارے کا نام منتخب کریں");
        m.put("eligibility.q_filed_with_re", "کیا آپ نے {{reName}} میں تحریری/الیکٹرانک شکایت درج کی ہے؟");
        m.put("eligibility.q_received_reply", "کیا آپ کو ادارے سے کوئی جواب ملا ہے؟");
        m.put("eligibility.q_sent_reminder", "کیا آپ نے {{reName}} کو کوئی یاد دہانی بھیجی ہے؟");
        m.put("eligibility.q_sub_judice", "کیا یہ شکایت پہلے سے کسی عدالت، ٹریبونل، ثالث یا دیگر عدالتی فورم میں زیر سماعت ہے (فوجداری کارروائی کے سوا)؟");
        m.put("eligibility.q_through_advocate", "کیا آپ کی شکایت وکیل کے ذریعے دائر کی جا رہی ہے؟");
        m.put("eligibility.q_pending_ombudsman", "کیا یہ شکایت پہلے سے اسی معاملے پر اومبڈزمین کے سامنے زیر التوا ہے؟");
        m.put("eligibility.q_settled_ombudsman", "کیا یہ شکایت پہلے ہی اومبڈزمین نے میرٹ پر طے کر دی ہے؟");
        m.put("eligibility.q_staff_of_re", "کیا شکایت کنندہ ریگولیٹڈ ادارے کا ملازم ہے اور شکایت آجر-ملازم تعلق سے متعلق ہے؟");
        m.put("eligibility.q_employee_of_re", "جس ریگولیٹڈ ادارے کے خلاف یہ شکایت درج کی جا رہی ہے، کیا آپ اس کے ملازم ہیں/تھے؟");
        m.put("eligibility.q_employer_relationship", "اگر ہاں، تو کیا آپ کی شکایت ریگولیٹڈ ادارے کے ملازم-آجر تعلق سے متعلق ہے؟");
        m.put("eligibility.sub_complaint_date", "جس تاریخ کو شکایت پہلی بار درج کی گئی");
        m.put("eligibility.sub_upload_complaint", "کو بھیجی گئی شکایت کی کاپی اپلوڈ کریں");
        m.put("eligibility.sub_reminder_date", "جس تاریخ کو یاد دہانی بھیجی گئی");
        m.put("eligibility.sub_upload_reminder", "یاد دہانی کی کاپی اپلوڈ کریں");
        m.put("eligibility.sub_reply_date", "جس تاریخ کو جواب موصول ہوا");
        m.put("eligibility.sub_upload_reply", "جواب کی کاپی اپلوڈ کریں");
        m.put("eligibility.sub_are_you_complainant", "اگر ہاں، تو کیا آپ خود شکایت کنندہ ہیں؟");
        m.put("eligibility.block_not_filed", "ریزرو بینک – مربوط اومبڈزمین اسکیم، 2021 کی شق {{clause}} کے مطابق، شکایت اسکیم کے تحت عملدرآمد نہیں ہو سکتی۔");
        m.put("eligibility.block_sub_judice", "آپ کی شکایت عدالت/ٹریبونل/ثالث/اتھارٹی کے سامنے زیر التوا ہونے کی وجہ سے، اسے ناقابل قبول کے طور پر بند کیا جائے گا۔");
        m.put("eligibility.block_pending_ombudsman", "آپ کی شکایت پہلے سے اسی معاملے پر اومبڈزمین کے سامنے زیر التوا ہے۔ ڈپلیکیٹ شکایت درج نہیں کی جا سکتی۔");
        m.put("eligibility.block_settled_ombudsman", "آپ کی شکایت پہلے ہی اومبڈزمین نے طے کر دی ہے۔ اسی معاملے پر نئی شکایت درج نہیں کی جا سکتی۔");
        m.put("eligibility.block_staff_of_re", "ریگولیٹڈ ادارے کے ملازم اور آجر-ملازم تعلق کی شکایات مربوط اومبڈزمین اسکیم کے تحت درج نہیں کی جا سکتیں۔");
        m.put("eligibility.block_advocate_not_complainant", "مربوط اومبڈزمین اسکیم کے مطابق، وکیل کے ذریعے درج کی گئی شکایت خود شکایت کنندہ کو درج کرنی چاہیے۔ چونکہ آپ شکایت کنندہ نہیں ہیں، اس شکایت پر کارروائی نہیں کی جا سکتی۔");
        m.put("eligibility.block_indicated", "جیسا کہ آپ نے");
        m.put("eligibility.block_in_response", "اس سوال کے جواب میں بتایا ہے،");
        m.put("eligibility.block_written_required", "ریگولیٹڈ ادارے میں پہلے تحریری/الیکٹرانک شکایت درج کرانا ضروری ہے۔");
        m.put("eligibility.block_regret", "اس کے مطابق، ہم آپ کو مطلع کرتے ہیں کہ آپ کی موجودہ شکایت");
        m.put("eligibility.block_cannot_register", "کے خلاف اسکیم کے تحت رجسٹر نہیں کی جا سکتی۔ اگر جواب غلطی سے دیا گیا ہو تو آپ جواب تبدیل کر سکتے ہیں۔");
        m.put("eligibility.block_regards", "نیک خواہشات، RBI CMS ٹیم۔");
        m.put("eligibility.show_closure_letter", "بندش خط دکھائیں");
        m.put("eligibility.passed_title", "اہلیت پاس");
        m.put("eligibility.passed_message", "آپ RBI مربوط محتسب اسکیم، 2021 کے تحت شکایت درج کرانے کے اہل ہیں۔");
        m.put("consent.dpdp_notice", "میں رضامندی دیتا/دیتی ہوں کہ RBI قابل اطلاق قوانین اور ڈیجیٹل پرسنل ڈیٹا پروٹیکشن ایکٹ، 2023 کے مطابق میری شکایت درج کرنے اور اسے حل کرنے کے لیے میرا ذاتی ڈیٹا استعمال کرے۔");
        m.put("consent.required", "جاری رکھنے کے لیے آپ کو ڈیٹا پروسیسنگ اعلامیہ قبول کرنا ہوگا۔");
        m.put("layout.assistance_desc", "رابطہ مرکز (#14448) انٹرایکٹو وائس رسپانس سسٹم (IVRS) کے ساتھ 24x7 دستیاب ہے، جبکہ رابطہ مرکز کے عملے سے رابطے کی سہولت سوموار سے ہفتہ (قومی تعطیلات کے علاوہ) صبح 8:00 سے رات 10:00 تک انگریزی، ہندی اور دس علاقائی زبانوں میں دستیاب ہے۔");
        m.put("validation.date_invalid", "dd/mm/yyyy فارمیٹ میں درست تاریخ درج کریں");
        m.put("validation.date_incomplete", "مکمل تاریخ dd/mm/yyyy کے طور پر درج کریں");
        m.put("validation.date_future", "تاریخ مستقبل کی نہیں ہو سکتی");
        m.put("validation.complaint_date_required", "ریگولیٹڈ ادارے میں شکایت درج کرنے کی تاریخ لازمی ہے");
        m.put("validation.complaint_copy_required", "ریگولیٹڈ ادارے کو بھیجی گئی شکایت کی نقل اپ لوڈ کریں");
        m.put("validation.reply_date_required", "جواب موصول ہونے کی تاریخ لازمی ہے");
        m.put("validation.reply_copy_required", "ریگولیٹڈ ادارے سے موصول جواب کی نقل اپ لوڈ کریں");
        m.put("validation.reply_before_complaint", "جواب کی تاریخ شکایت درج کرنے کی تاریخ سے پہلے نہیں ہو سکتی");
        m.put("validation.reminder_date_required", "یاد دہانی بھیجنے کی تاریخ لازمی ہے");
        m.put("validation.reminder_copy_required", "ریگولیٹڈ ادارے کو بھیجی گئی یاد دہانی کی نقل اپ لوڈ کریں");
        m.put("validation.reminder_before_complaint", "یاد دہانی کی تاریخ شکایت درج کرنے کی تاریخ سے پہلے نہیں ہو سکتی");
        m.put("validation.file_type_not_allowed", "صرف PDF، JPG اور PNG فائلوں کی اجازت ہے");
        m.put("validation.ref_too_long", "حوالہ نمبر 100 حروف سے زیادہ نہیں ہو سکتا");
        m.put("validation.ref_invalid_chars", "حوالہ نمبر میں صرف حروف، اعداد، ہائفن، انڈر سکور اور سلیش ہو سکتے ہیں");
        m.put("eligibility.block_less_than_30_days", "چونکہ ریگولیٹڈ ادارے کو آپ کی شکایت کا جواب دینے کے لیے ابھی {{days}} دن نہیں دیے گئے، اس وقت آپ کی شکایت درج نہیں کی جا سکتی۔ براہ کرم ریگولیٹڈ ادارے میں شکایت درج کرنے کی تاریخ سے {{days}} دن مکمل ہونے تک انتظار کریں۔");
        m.put("eligibility.time_barred_warning", "ریگولیٹڈ ادارے میں آپ کی شکایت {{days}} دن سے زیادہ عرصہ پہلے درج کی گئی تھی۔ اسکیم کے تحت درج کرانے کی مدت گزر چکی ہے، لہٰذا آپ کی شکایت میعاد گزر جانے کی بنیاد پر بند کی جا سکتی ہے۔ آپ پھر بھی آگے بڑھ سکتے ہیں۔");
        m.put("eligibility.block_filing_window", "شکایت درج کرانے کی مدت گزر چکی ہے۔ شکایت ریگولیٹڈ ادارے میں آپ کی شکایت کے {{days}} دن کے اندر درج کرانی ضروری ہے۔");
        m.put("eligibility.block_post_reply_window", "شکایت درج کرانے کی مدت گزر چکی ہے۔ شکایت ریگولیٹڈ ادارے کے جواب کے {{days}} دن کے اندر درج کرانی ضروری ہے۔");
        saveLocaleTranslations(m, "ur");
    }

    private void seedKannadaTranslations() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eligibility.title", "ಹೊಸ ದೂರು ದಾಖಲಿಸಿ");
        m.put("eligibility.subtitle", "ಮುಂದುವರಿಯಲು ನಿಮ್ಮ ಅರ್ಹತೆಯನ್ನು ಪರಿಶೀಲಿಸಿ");
        m.put("eligibility.mandatory_note", "ಐಚ್ಛಿಕ ಎಂದು ಗುರುತಿಸದ ಹೊರತು ಎಲ್ಲಾ ಕ್ಷೇತ್ರಗಳು ಕಡ್ಡಾಯ.");
        m.put("eligibility.select_desc", "ಹಣಕಾಸು ಸಂಸ್ಥೆಯನ್ನು ಆಯ್ಕೆಮಾಡಿ—ಬ್ಯಾಂಕ್, ನಾನ್-ಬ್ಯಾಂಕಿಂಗ್ ಹಣಕಾಸು ಕಂಪನಿ (NBFC), ಅಥವಾ ಪಾವತಿ ಪ್ರೊಸೆಸರ್—ಭಾರತೀಯ ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್‌ನಿಂದ ಪರವಾನಗಿ ಪಡೆದ, ಅದರ ವಿರುದ್ಧ ನೀವು ದೂರು ದಾಖಲಿಸಲು ಬಯಸುತ್ತೀರಿ.");
        m.put("eligibility.select_placeholder", "ಒಂದು ಮೌಲ್ಯವನ್ನು ಆಯ್ಕೆಮಾಡಿ");
        m.put("eligibility.simplify_btn", "ಸರಳಗೊಳಿಸಿ");
        m.put("eligibility.browse_file", "ಫೈಲ್ ಆಯ್ಕೆಮಾಡಿ");
        m.put("eligibility.upload_hint", "ಬೆಂಬಲಿತ ಸ್ವರೂಪಗಳು: PDF, JPG, PNG. ಗರಿಷ್ಠ ಗಾತ್ರ: 5MB");
        m.put("eligibility.opt_yes", "ಹೌದು");
        m.put("eligibility.opt_no", "ಇಲ್ಲ");
        m.put("eligibility.response_mandatory", "ಉತ್ತರ ನೀಡುವುದು ಕಡ್ಡಾಯವಾಗಿದೆ.");
        m.put("eligibility.entity_mandatory", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಹೆಸರು ಕಡ್ಡಾಯವಾಗಿದೆ.");
        m.put("eligibility.entity_search_label", "ಹೆಸರು ಅಥವಾ ಪ್ರಕಾರದ ಮೂಲಕ ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯನ್ನು ಹುಡುಕಿ");
        m.put("eligibility.entity_search_placeholder", "ಸಂಸ್ಥೆಯ ಹೆಸರು ಅಥವಾ ಸಂಸ್ಥೆಯ ಪ್ರಕಾರವನ್ನು ಹುಡುಕಿ");
        m.put("eligibility.entity_search_clear", "ಹುಡುಕಾಟವನ್ನು ಅಳಿಸಿ");
        m.put("eligibility.entity_search_no_results", "\"{{term}}\" ಗಾಗಿ ಯಾವುದೇ ಫಲಿತಾಂಶ ಸಿಗಲಿಲ್ಲ.");
        m.put("eligibility.entities_unavailable", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಗಳ ಪಟ್ಟಿಯನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ — ಸಂಸ್ಥೆಯ ಹೆಸರು ಇಲ್ಲದೆ ದೂರು ದಾಖಲಿಸಲಾಗದು.");
        m.put("eligibility.q_select_re", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಹೆಸರು ಆಯ್ಕೆಮಾಡಿ");
        m.put("eligibility.q_filed_with_re", "ನೀವು {{reName}} ಬಳಿ ಲಿಖಿತ/ಎಲೆಕ್ಟ್ರಾನಿಕ್ ದೂರು ಸಲ್ಲಿಸಿದ್ದೀರಾ?");
        m.put("eligibility.q_received_reply", "ನಿಮಗೆ ಸಂಸ್ಥೆಯಿಂದ ಯಾವುದೇ ಉತ್ತರ ಸಿಕ್ಕಿದೆಯೇ?");
        m.put("eligibility.q_sent_reminder", "ನೀವು {{reName}}ಗೆ ಯಾವುದೇ ಜ್ಞಾಪನೆ ಕಳುಹಿಸಿದ್ದೀರಾ?");
        m.put("eligibility.q_sub_judice", "ಈ ದೂರು ಈಗಾಗಲೇ ಯಾವುದೇ ನ್ಯಾಯಾಲಯ, ನ್ಯಾಯಾಧಿಕರಣ, ಮಧ್ಯಸ್ಥಿಕೆ ಅಥವಾ ಇತರ ನ್ಯಾಯಿಕ ವೇದಿಕೆಯ ಮುಂದೆ ಬಾಕಿ ಇದೆಯೇ (ಕ್ರಿಮಿನಲ್ ಪ್ರಕರಣಗಳನ್ನು ಹೊರತುಪಡಿಸಿ)?");
        m.put("eligibility.q_through_advocate", "ನಿಮ್ಮ ದೂರು ವಕೀಲರ ಮೂಲಕ ಸಲ್ಲಿಸಲಾಗುತ್ತಿದೆಯೇ?");
        m.put("eligibility.q_pending_ombudsman", "ಈ ದೂರು ಈಗಾಗಲೇ ಅದೇ ವಿಷಯದ ಮೇಲೆ ಲೋಕಪಾಲರ ಮುಂದೆ ಬಾಕಿ ಇದೆಯೇ?");
        m.put("eligibility.q_settled_ombudsman", "ಈ ದೂರು ಈಗಾಗಲೇ ಲೋಕಪಾಲರಿಂದ ಗುಣಾವಗುಣಗಳ ಆಧಾರದ ಮೇಲೆ ಇತ್ಯರ್ಥಗೊಂಡಿದೆಯೇ?");
        m.put("eligibility.q_staff_of_re", "ದೂರುದಾರರು ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಸಿಬ್ಬಂದಿಯೇ ಮತ್ತು ದೂರು ನಿಯೋಜಕ-ಉದ್ಯೋಗಿ ಸಂಬಂಧಕ್ಕೆ ಸಂಬಂಧಿಸಿದೆಯೇ?");
        m.put("eligibility.q_employee_of_re", "ಈ ದೂರನ್ನು ಸಲ್ಲಿಸಲಾಗುತ್ತಿರುವ ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಉದ್ಯೋಗಿಯಾಗಿದ್ದೀರಾ/ಆಗಿದ್ದಿರಾ?");
        m.put("eligibility.q_employer_relationship", "ಹೌದು ಎಂದಾದರೆ, ನಿಮ್ಮ ದೂರು ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಉದ್ಯೋಗಿ-ನಿಯೋಜಕ ಸಂಬಂಧಕ್ಕೆ ಸಂಬಂಧಿಸಿದೆಯೇ?");
        m.put("eligibility.sub_complaint_date", "ದೂರು ಮೊದಲ ಬಾರಿಗೆ ಸಲ್ಲಿಸಿದ ದಿನಾಂಕ");
        m.put("eligibility.sub_upload_complaint", "ಗೆ ಕಳುಹಿಸಿದ ದೂರಿನ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("eligibility.sub_reminder_date", "ಜ್ಞಾಪನೆ ಕಳುಹಿಸಿದ ದಿನಾಂಕ");
        m.put("eligibility.sub_upload_reminder", "ಜ್ಞಾಪನೆಯ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("eligibility.sub_reply_date", "ಉತ್ತರ ಸಿಕ್ಕ ದಿನಾಂಕ");
        m.put("eligibility.sub_upload_reply", "ಉತ್ತರದ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("eligibility.sub_are_you_complainant", "ಹೌದು ಎಂದಾದರೆ, ನೀವೇ ದೂರುದಾರರೇ?");
        m.put("eligibility.block_not_filed", "ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್ – ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆ, 2021 ಕಲಂ {{clause}} ಪ್ರಕಾರ, ದೂರನ್ನು ಯೋಜನೆಯಡಿ ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ.");
        m.put("eligibility.block_sub_judice", "ನಿಮ್ಮ ದೂರು ನ್ಯಾಯಾಲಯ/ನ್ಯಾಯಾಧಿಕರಣ/ಮಧ್ಯಸ್ಥಿಕೆ/ಅಧಿಕಾರದ ಮುಂದೆ ಬಾಕಿ ಇರುವುದರಿಂದ, ಇದನ್ನು ಅನರ್ಹವೆಂದು ಮುಚ್ಚಲಾಗುವುದು.");
        m.put("eligibility.block_pending_ombudsman", "ನಿಮ್ಮ ದೂರು ಈಗಾಗಲೇ ಅದೇ ವಿಷಯದ ಮೇಲೆ ಲೋಕಪಾಲರ ಮುಂದೆ ಬಾಕಿ ಇದೆ. ನಕಲಿ ದೂರು ಸಲ್ಲಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("eligibility.block_settled_ombudsman", "ನಿಮ್ಮ ದೂರು ಈಗಾಗಲೇ ಲೋಕಪಾಲರಿಂದ ಇತ್ಯರ್ಥಗೊಂಡಿದೆ. ಅದೇ ವಿಷಯದ ಮೇಲೆ ಹೊಸ ದೂರು ಸಲ್ಲಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("eligibility.block_staff_of_re", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಸಿಬ್ಬಂದಿ ಮತ್ತು ನಿಯೋಜಕ-ಉದ್ಯೋಗಿ ಸಂಬಂಧದ ದೂರುಗಳನ್ನು ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆಯಡಿ ಸಲ್ಲಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("eligibility.block_advocate_not_complainant", "ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆಯ ಪ್ರಕಾರ, ವಕೀಲರ ಮೂಲಕ ಸಲ್ಲಿಸಿದ ದೂರನ್ನು ದೂರುದಾರರೇ ಸಲ್ಲಿಸಬೇಕು. ನೀವು ದೂರುದಾರರಲ್ಲದ್ದರಿಂದ, ಈ ದೂರನ್ನು ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("eligibility.block_indicated", "ನೀವು");
        m.put("eligibility.block_in_response", "ಈ ಪ್ರಶ್ನೆಗೆ ಉತ್ತರವಾಗಿ ತಿಳಿಸಿದಂತೆ,");
        m.put("eligibility.block_written_required", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಲ್ಲಿ ಮೊದಲು ಲಿಖಿತ/ಎಲೆಕ್ಟ್ರಾನಿಕ್ ದೂರು ದಾಖಲಿಸುವುದು ಅಗತ್ಯ.");
        m.put("eligibility.block_regret", "ಅದರಂತೆ, ನಿಮ್ಮ ಪ್ರಸ್ತುತ ದೂರು");
        m.put("eligibility.block_cannot_register", "ವಿರುದ್ಧ ಯೋಜನೆಯಡಿ ನೋಂದಾಯಿಸಲಾಗುವುದಿಲ್ಲ ಎಂದು ನಾವು ತಿಳಿಸುತ್ತೇವೆ. ಉತ್ತರವನ್ನು ತಪ್ಪಾಗಿ ನೀಡಿದ್ದರೆ, ನೀವು ಉತ್ತರವನ್ನು ಬದಲಾಯಿಸಬಹುದು.");
        m.put("eligibility.block_regards", "ಶುಭಾಶಯಗಳೊಂದಿಗೆ, RBI CMS ತಂಡ.");
        m.put("eligibility.show_closure_letter", "ಮುಕ್ತಾಯ ಪತ್ರ ತೋರಿಸಿ");
        m.put("eligibility.passed_title", "ಅರ್ಹತೆ ಉತ್ತೀರ್ಣ");
        m.put("eligibility.passed_message", "ನೀವು RBI ಸಮಗ್ರ ಓಂಬುಡ್ಸ್‌ಮನ್ ಯೋಜನೆ, 2021 ಅಡಿಯಲ್ಲಿ ದೂರು ದಾಖಲಿಸಲು ಅರ್ಹರು.");
        m.put("consent.dpdp_notice", "ಅನ್ವಯವಾಗುವ ಕಾನೂನುಗಳು ಮತ್ತು ಡಿಜಿಟಲ್ ಪರ್ಸನಲ್ ಡೇಟಾ ಪ್ರೊಟೆಕ್ಷನ್ ಕಾಯ್ದೆ, 2023 ರ ಪ್ರಕಾರ ನನ್ನ ದೂರನ್ನು ನೋಂದಾಯಿಸಲು ಮತ್ತು ಪರಿಹರಿಸಲು RBI ನನ್ನ ವೈಯಕ್ತಿಕ ಡೇಟಾವನ್ನು ಬಳಸಲು ನಾನು ಸಮ್ಮತಿಸುತ್ತೇನೆ.");
        m.put("consent.required", "ಮುಂದುವರಿಯಲು ನೀವು ಡೇಟಾ ಸಂಸ್ಕರಣಾ ಘೋಷಣೆಯನ್ನು ಸ್ವೀಕರಿಸಬೇಕು.");
        m.put("layout.assistance_desc", "ಸಂಪರ್ಕ ಕೇಂದ್ರ (#14448) ಇಂಟರಾಕ್ಟಿವ್ ವಾಯ್ಸ್ ರೆಸ್ಪಾನ್ಸ್ ಸಿಸ್ಟಮ್ (IVRS) ಜೊತೆ 24x7 ಲಭ್ಯವಿದೆ, ಆದರೆ ಸಂಪರ್ಕ ಕೇಂದ್ರ ಸಿಬ್ಬಂದಿಯೊಂದಿಗೆ ಸಂಪರ್ಕಿಸುವ ಸೌಲಭ್ಯ ಸೋಮವಾರದಿಂದ ಶನಿವಾರ (ರಾಷ್ಟ್ರೀಯ ರಜಾದಿನಗಳನ್ನು ಹೊರತುಪಡಿಸಿ) ಬೆಳಿಗ್ಗೆ 8:00 ರಿಂದ ರಾತ್ರಿ 10:00 ರವರೆಗೆ ಆಂಗ್ಲ, ಹಿಂದಿ ಮತ್ತು ಹತ್ತು ಪ್ರಾದೇಶಿಕ ಭಾಷೆಗಳಲ್ಲಿ ಲಭ್ಯವಿದೆ.");
        m.put("validation.date_invalid", "dd/mm/yyyy ಸ್ವರೂಪದಲ್ಲಿ ಮಾನ್ಯ ದಿನಾಂಕವನ್ನು ನಮೂದಿಸಿ");
        m.put("validation.date_incomplete", "ಪೂರ್ಣ ದಿನಾಂಕವನ್ನು dd/mm/yyyy ಆಗಿ ನಮೂದಿಸಿ");
        m.put("validation.date_future", "ದಿನಾಂಕ ಭವಿಷ್ಯದ್ದಾಗಿರಬಾರದು");
        m.put("validation.complaint_date_required", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಲ್ಲಿ ದೂರು ಸಲ್ಲಿಸಿದ ದಿನಾಂಕ ಅಗತ್ಯವಿದೆ");
        m.put("validation.complaint_copy_required", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಗೆ ಕಳುಹಿಸಿದ ದೂರಿನ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("validation.reply_date_required", "ಉತ್ತರ ಸ್ವೀಕರಿಸಿದ ದಿನಾಂಕ ಅಗತ್ಯವಿದೆ");
        m.put("validation.reply_copy_required", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಿಂದ ಬಂದ ಉತ್ತರದ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("validation.reply_before_complaint", "ಉತ್ತರದ ದಿನಾಂಕ ದೂರು ಸಲ್ಲಿಸಿದ ದಿನಾಂಕಕ್ಕಿಂತ ಮೊದಲಿನದ್ದಾಗಿರಬಾರದು");
        m.put("validation.reminder_date_required", "ಜ್ಞಾಪನೆ ಕಳುಹಿಸಿದ ದಿನಾಂಕ ಅಗತ್ಯವಿದೆ");
        m.put("validation.reminder_copy_required", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಗೆ ಕಳುಹಿಸಿದ ಜ್ಞಾಪನೆಯ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("validation.reminder_before_complaint", "ಜ್ಞಾಪನೆಯ ದಿನಾಂಕ ದೂರು ಸಲ್ಲಿಸಿದ ದಿನಾಂಕಕ್ಕಿಂತ ಮೊದಲಿನದ್ದಾಗಿರಬಾರದು");
        m.put("validation.file_type_not_allowed", "PDF, JPG ಮತ್ತು PNG ಫೈಲ್‌ಗಳಿಗೆ ಮಾತ್ರ ಅನುಮತಿ ಇದೆ");
        m.put("validation.ref_too_long", "ಉಲ್ಲೇಖ ಸಂಖ್ಯೆ 100 ಅಕ್ಷರಗಳನ್ನು ಮೀರಬಾರದು");
        m.put("validation.ref_invalid_chars", "ಉಲ್ಲೇಖ ಸಂಖ್ಯೆಯಲ್ಲಿ ಅಕ್ಷರಗಳು, ಅಂಕಿಗಳು, ಹೈಫನ್, ಅಂಡರ್‌ಸ್ಕೋರ್ ಮತ್ತು ಸ್ಲ್ಯಾಶ್ ಮಾತ್ರ ಇರಬಹುದು");
        m.put("eligibility.block_less_than_30_days", "ನಿಮ್ಮ ದೂರಿಗೆ ಉತ್ತರಿಸಲು ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಗೆ ಇನ್ನೂ {{days}} ದಿನಗಳನ್ನು ನೀಡಲಾಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಈ ಸಮಯದಲ್ಲಿ ನಿಮ್ಮ ದೂರನ್ನು ನೋಂದಾಯಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ. ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಲ್ಲಿ ದೂರು ಸಲ್ಲಿಸಿದ ದಿನಾಂಕದಿಂದ {{days}} ದಿನಗಳು ಪೂರ್ಣಗೊಳ್ಳುವವರೆಗೆ ದಯವಿಟ್ಟು ನಿರೀಕ್ಷಿಸಿ.");
        m.put("eligibility.time_barred_warning", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಲ್ಲಿ ನಿಮ್ಮ ದೂರನ್ನು {{days}} ದಿನಗಳಿಗಿಂತ ಹಿಂದೆ ಸಲ್ಲಿಸಲಾಗಿತ್ತು. ಯೋಜನೆಯಡಿ ಸಲ್ಲಿಸುವ ಅವಧಿ ಮುಗಿದಿದೆ, ಆದ್ದರಿಂದ ನಿಮ್ಮ ದೂರನ್ನು ಕಾಲಮಿತಿ ಮುಗಿದಿದೆ ಎಂದು ಮುಚ್ಚಬಹುದು. ನೀವು ಇನ್ನೂ ಮುಂದುವರಿಯಬಹುದು.");
        m.put("eligibility.block_filing_window", "ದೂರು ಸಲ್ಲಿಸುವ ಅವಧಿ ಮುಗಿದಿದೆ. ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಲ್ಲಿ ನಿಮ್ಮ ದೂರಿನ {{days}} ದಿನಗಳೊಳಗೆ ದೂರು ಸಲ್ಲಿಸಬೇಕು.");
        m.put("eligibility.block_post_reply_window", "ದೂರು ಸಲ್ಲಿಸುವ ಅವಧಿ ಮುಗಿದಿದೆ. ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಉತ್ತರದ {{days}} ದಿನಗಳೊಳಗೆ ದೂರು ಸಲ್ಲಿಸಬೇಕು.");
        saveLocaleTranslations(m, "kn");
    }

    private void seedMalayalamTranslations() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eligibility.title", "പുതിയ പരാതി ഫയൽ ചെയ്യുക");
        m.put("eligibility.subtitle", "മുന്നോട്ട് പോകാൻ നിങ്ങളുടെ യോഗ്യത പരിശോധിക്കുക");
        m.put("eligibility.mandatory_note", "ഐച്ഛികം എന്ന് അടയാളപ്പെടുത്തിയില്ലെങ്കിൽ എല്ലാ ഫീൽഡുകളും നിർബന്ധമാണ്.");
        m.put("eligibility.select_desc", "ധനകാര്യ സ്ഥാപനം തിരഞ്ഞെടുക്കുക—ബാങ്ക്, നോൺ-ബാങ്കിംഗ് ഫിനാൻഷ്യൽ കമ്പനി (NBFC), അല്ലെങ്കിൽ പേയ്‌മെന്റ് പ്രോസസർ—ഇന്ത്യൻ റിസർവ് ബാങ്ക് ലൈസൻസ് നൽകിയതും മേൽനോട്ടം വഹിക്കുന്നതുമായ, അതിനെതിരെ നിങ്ങൾ പരാതി ഫയൽ ചെയ്യാൻ ആഗ്രഹിക്കുന്നു.");
        m.put("eligibility.select_placeholder", "ഒരു മൂല്യം തിരഞ്ഞെടുക്കുക");
        m.put("eligibility.simplify_btn", "ലളിതമാക്കുക");
        m.put("eligibility.browse_file", "ഫയൽ തിരഞ്ഞെടുക്കുക");
        m.put("eligibility.upload_hint", "പിന്തുണയ്ക്കുന്ന ഫോർമാറ്റുകൾ: PDF, JPG, PNG. പരമാവധി വലുപ്പം: 5MB");
        m.put("eligibility.opt_yes", "അതെ");
        m.put("eligibility.opt_no", "ഇല്ല");
        m.put("eligibility.response_mandatory", "ഉത്തരം നൽകേണ്ടത് നിർബന്ധമാണ്.");
        m.put("eligibility.entity_mandatory", "നിയന്ത്രിത സ്ഥാപനത്തിന്റെ പേര് നിർബന്ധമാണ്.");
        m.put("eligibility.entity_search_label", "പേര് അല്ലെങ്കിൽ തരം അനുസരിച്ച് നിയന്ത്രിത സ്ഥാപനം തിരയുക");
        m.put("eligibility.entity_search_placeholder", "സ്ഥാപനത്തിന്റെ പേരോ സ്ഥാപനത്തിന്റെ തരമോ തിരയുക");
        m.put("eligibility.entity_search_clear", "തിരയൽ മായ്ക്കുക");
        m.put("eligibility.entity_search_no_results", "\"{{term}}\" എന്നതിന് ഫലങ്ങൾ ഒന്നും കണ്ടെത്തിയില്ല.");
        m.put("eligibility.entities_unavailable", "നിയന്ത്രിത സ്ഥാപനങ്ങളുടെ പട്ടിക ലോഡ് ചെയ്യാനായില്ല. വീണ്ടും ശ്രമിക്കുക — സ്ഥാപനത്തിന്റെ പേര് നൽകാതെ പരാതി നൽകാനാവില്ല.");
        m.put("eligibility.q_select_re", "നിയന്ത്രിത സ്ഥാപനത്തിന്റെ പേര് തിരഞ്ഞെടുക്കുക");
        m.put("eligibility.q_filed_with_re", "നിങ്ങൾ {{reName}}-ൽ രേഖാമൂലം/ഇലക്ട്രോണിക് പരാതി സമർപ്പിച്ചിട്ടുണ്ടോ?");
        m.put("eligibility.q_received_reply", "സ്ഥാപനത്തിൽ നിന്ന് എന്തെങ്കിലും മറുപടി ലഭിച്ചോ?");
        m.put("eligibility.q_sent_reminder", "നിങ്ങൾ {{reName}}-ന് എന്തെങ്കിലും ഓർമ്മപ്പെടുത്തൽ അയച്ചോ?");
        m.put("eligibility.q_sub_judice", "ഈ പരാതി ഇതിനകം ഏതെങ്കിലും കോടതി, ട്രൈബ്യൂണൽ, ആർബിട്രേറ്റർ അല്ലെങ്കിൽ മറ്റ് ജുഡീഷ്യൽ ഫോറത്തിന് മുമ്പാകെ തീർപ്പുകൽപ്പിക്കാതെ നിലനിൽക്കുന്നുണ്ടോ (ക്രിമിനൽ നടപടികൾ ഒഴികെ)?");
        m.put("eligibility.q_through_advocate", "നിങ്ങളുടെ പരാതി അഭിഭാഷകൻ മുഖേന സമർപ്പിക്കുന്നതാണോ?");
        m.put("eligibility.q_pending_ombudsman", "ഈ പരാതി ഇതിനകം അതേ വിഷയത്തിൽ ഓംബുഡ്സ്മാന് മുമ്പാകെ തീർപ്പുകൽപ്പിക്കാതെ നിലനിൽക്കുന്നുണ്ടോ?");
        m.put("eligibility.q_settled_ombudsman", "ഈ പരാതി ഇതിനകം ഓംബുഡ്സ്മാൻ ഗുണദോഷവിചാരത്തിൽ പരിഹരിച്ചിട്ടുണ്ടോ?");
        m.put("eligibility.q_staff_of_re", "പരാതിക്കാരൻ നിയന്ത്രിത സ്ഥാപനത്തിന്റെ ജീവനക്കാരനാണോ, പരാതി തൊഴിലുടമ-ജീവനക്കാരൻ ബന്ധവുമായി ബന്ധപ്പെട്ടതാണോ?");
        m.put("eligibility.q_employee_of_re", "ഈ പരാതി സമർപ്പിക്കുന്ന നിയന്ത്രിത സ്ഥാപനത്തിലെ ജീവനക്കാരനാണോ/ആയിരുന്നോ നിങ്ങൾ?");
        m.put("eligibility.q_employer_relationship", "അതെ എങ്കിൽ, നിങ്ങളുടെ പരാതി നിയന്ത്രിത സ്ഥാപനത്തിന്റെ ജീവനക്കാരൻ-തൊഴിലുടമ ബന്ധവുമായി ബന്ധപ്പെട്ടതാണോ?");
        m.put("eligibility.sub_complaint_date", "പരാതി ആദ്യം സമർപ്പിച്ച തീയതി");
        m.put("eligibility.sub_upload_complaint", "ന് അയച്ച പരാതിയുടെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("eligibility.sub_reminder_date", "ഓർമ്മപ്പെടുത്തൽ അയച്ച തീയതി");
        m.put("eligibility.sub_upload_reminder", "ഓർമ്മപ്പെടുത്തലിന്റെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("eligibility.sub_reply_date", "മറുപടി ലഭിച്ച തീയതി");
        m.put("eligibility.sub_upload_reply", "മറുപടിയുടെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("eligibility.sub_are_you_complainant", "അതെ എങ്കിൽ, നിങ്ങൾ തന്നെ പരാതിക്കാരനാണോ?");
        m.put("eligibility.block_not_filed", "റിസർവ് ബാങ്ക് – സംയോജിത ഓംബുഡ്സ്മാൻ സ്കീം, 2021 ക്ലോസ് {{clause}} പ്രകാരം, പരാതി സ്കീമിന് കീഴിൽ പ്രോസസ്സ് ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_sub_judice", "നിങ്ങളുടെ പരാതി കോടതി/ട്രൈബ്യൂണൽ/ആർബിട്രേറ്റർ/അതോറിറ്റിക്ക് മുമ്പാകെ നിലനിൽക്കുന്നതിനാൽ, ഇത് അയോഗ്യമെന്ന് അടച്ചുപൂട്ടും.");
        m.put("eligibility.block_pending_ombudsman", "നിങ്ങളുടെ പരാതി ഇതിനകം അതേ വിഷയത്തിൽ ഓംബുഡ്സ്മാന് മുമ്പാകെ നിലനിൽക്കുന്നു. ഡ്യൂപ്ലിക്കേറ്റ് പരാതി ഫയൽ ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_settled_ombudsman", "നിങ്ങളുടെ പരാതി ഇതിനകം ഓംബുഡ്സ്മാൻ പരിഹരിച്ചിട്ടുണ്ട്. അതേ വിഷയത്തിൽ പുതിയ പരാതി ഫയൽ ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_staff_of_re", "നിയന്ത്രിത സ്ഥാപന ജീവനക്കാരനും തൊഴിലുടമ-ജീവനക്കാരൻ ബന്ധ പരാതികളും സംയോജിത ഓംബുഡ്സ്മാൻ സ്കീമിന് കീഴിൽ ഫയൽ ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_advocate_not_complainant", "സംയോജിത ഓംബുഡ്സ്മാൻ സ്കീം അനുസരിച്ച്, ഒരു അഭിഭാഷകൻ മുഖേന സമർപ്പിക്കുന്ന പരാതി പരാതിക്കാരൻ തന്നെ സമർപ്പിക്കണം. നിങ്ങൾ പരാതിക്കാരൻ അല്ലാത്തതിനാൽ, ഈ പരാതി പ്രോസസ് ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_indicated", "നിങ്ങൾ");
        m.put("eligibility.block_in_response", "ഈ ചോദ്യത്തിന് ഉത്തരമായി സൂചിപ്പിച്ചതുപോലെ,");
        m.put("eligibility.block_written_required", "നിയന്ത്രിത സ്ഥാപനത്തിൽ ആദ്യം രേഖാമൂലം/ഇലക്ട്രോണിക് പരാതി നൽകേണ്ടതുണ്ട്.");
        m.put("eligibility.block_regret", "അതനുസരിച്ച്, നിങ്ങളുടെ നിലവിലെ പരാതി");
        m.put("eligibility.block_cannot_register", "ക്കെതിരെ പദ്ധതിയുടെ കീഴിൽ രജിസ്റ്റർ ചെയ്യാൻ കഴിയില്ലെന്ന് ഞങ്ങൾ അറിയിക്കുന്നു. ഉത്തരം തെറ്റായി നൽകിയതാണെങ്കിൽ, നിങ്ങൾക്ക് ഉത്തരം മാറ്റാവുന്നതാണ്.");
        m.put("eligibility.block_regards", "ആശംസകൾ, RBI CMS ടീം.");
        m.put("eligibility.show_closure_letter", "ക്ലോഷർ കത്ത് കാണിക്കുക");
        m.put("eligibility.passed_title", "യോഗ്യത വിജയം");
        m.put("eligibility.passed_message", "RBI സമഗ്ര ഓംബുഡ്സ്മാൻ പദ്ധതി, 2021 പ്രകാരം പരാതി നൽകാൻ നിങ്ങൾ യോഗ്യനാണ്.");
        m.put("consent.dpdp_notice", "ബാധകമായ നിയമങ്ങൾക്കും ഡിജിറ്റൽ പേഴ്‌സണൽ ഡാറ്റ പ്രൊട്ടക്ഷൻ ആക്ട്, 2023 നും അനുസൃതമായി എന്റെ പരാതി രജിസ്റ്റർ ചെയ്യാനും പരിഹരിക്കാനും RBI എന്റെ വ്യക്തിഗത ഡാറ്റ ഉപയോഗിക്കുന്നതിന് ഞാൻ സമ്മതം നൽകുന്നു.");
        m.put("consent.required", "തുടരാൻ നിങ്ങൾ ഡാറ്റ പ്രോസസ്സിംഗ് പ്രഖ്യാപനം അംഗീകരിക്കണം.");
        m.put("layout.assistance_desc", "കോൺടാക്ട് സെന്റർ (#14448) ഇന്ററാക്ടീവ് വോയ്‌സ് റെസ്‌പോൺസ് സിസ്റ്റം (IVRS) ഉപയോഗിച്ച് 24x7 ലഭ്യമാണ്, അതേസമയം കോൺടാക്ട് സെന്റർ ജീവനക്കാരുമായി ബന്ധപ്പെടാനുള്ള സൗകര്യം ദേശീയ അവധി ദിവസങ്ങൾ ഒഴികെ തിങ്കൾ മുതൽ ശനി വരെ രാവിലെ 8:00 മുതൽ രാത്രി 10:00 വരെ ഇംഗ്ലീഷ്, ഹിന്ദി, പത്ത് പ്രാദേശിക ഭാഷകളിൽ ലഭ്യമാണ്.");
        m.put("validation.date_invalid", "dd/mm/yyyy ഫോർമാറ്റിൽ സാധുവായ തീയതി നൽകുക");
        m.put("validation.date_incomplete", "പൂർണ്ണ തീയതി dd/mm/yyyy ആയി നൽകുക");
        m.put("validation.date_future", "തീയതി ഭാവിയിലുള്ളതാകരുത്");
        m.put("validation.complaint_date_required", "നിയന്ത്രിത സ്ഥാപനത്തിൽ പരാതി സമർപ്പിച്ച തീയതി ആവശ്യമാണ്");
        m.put("validation.complaint_copy_required", "നിയന്ത്രിത സ്ഥാപനത്തിന് അയച്ച പരാതിയുടെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("validation.reply_date_required", "മറുപടി ലഭിച്ച തീയതി ആവശ്യമാണ്");
        m.put("validation.reply_copy_required", "നിയന്ത്രിത സ്ഥാപനത്തിൽ നിന്ന് ലഭിച്ച മറുപടിയുടെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("validation.reply_before_complaint", "മറുപടിയുടെ തീയതി പരാതി സമർപ്പിച്ച തീയതിക്ക് മുമ്പാകരുത്");
        m.put("validation.reminder_date_required", "ഓർമ്മപ്പെടുത്തൽ അയച്ച തീയതി ആവശ്യമാണ്");
        m.put("validation.reminder_copy_required", "നിയന്ത്രിത സ്ഥാപനത്തിന് അയച്ച ഓർമ്മപ്പെടുത്തലിന്റെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("validation.reminder_before_complaint", "ഓർമ്മപ്പെടുത്തലിന്റെ തീയതി പരാതി സമർപ്പിച്ച തീയതിക്ക് മുമ്പാകരുത്");
        m.put("validation.file_type_not_allowed", "PDF, JPG, PNG ഫയലുകൾ മാത്രമേ അനുവദനീയമാണ്");
        m.put("validation.ref_too_long", "റഫറൻസ് നമ്പർ 100 അക്ഷരങ്ങളിൽ കൂടരുത്");
        m.put("validation.ref_invalid_chars", "റഫറൻസ് നമ്പറിൽ അക്ഷരങ്ങൾ, അങ്കങ്ങൾ, ഹൈഫൻ, അണ്ടർസ്കോർ, സ്ലാഷ് എന്നിവ മാത്രമേ ഉണ്ടാകാം");
        m.put("eligibility.block_less_than_30_days", "നിങ്ങളുടെ പരാതിക്ക് മറുപടി നൽകാൻ നിയന്ത്രിത സ്ഥാപനത്തിന് ഇനിയും {{days}} ദിവസം നൽകിയിട്ടില്ല, അതിനാൽ ഈ സമയത്ത് നിങ്ങളുടെ പരാതി രജിസ്റ്റർ ചെയ്യാൻ കഴിയില്ല. നിയന്ത്രിത സ്ഥാപനത്തിൽ പരാതി സമർപ്പിച്ച തീയതിയിൽ നിന്ന് {{days}} ദിവസം പൂർത്തിയാകുന്നതുവരെ കാത്തിരിക്കുക.");
        m.put("eligibility.time_barred_warning", "നിയന്ത്രിത സ്ഥാപനത്തിൽ നിങ്ങളുടെ പരാതി {{days}} ദിവസത്തിന് മുമ്പ് സമർപ്പിച്ചതാണ്. പദ്ധതിക്ക് കീഴിലുള്ള സമർപ്പണ കാലാവധി കഴിഞ്ഞു, അതിനാൽ നിങ്ങളുടെ പരാതി കാലാവധി കഴിഞ്ഞതായി അടച്ചേക്കാം. നിങ്ങൾക്ക് ഇനിയും തുടരാം.");
        m.put("eligibility.block_filing_window", "പരാതി സമർപ്പിക്കാനുള്ള കാലാവധി കഴിഞ്ഞു. നിയന്ത്രിത സ്ഥാപനത്തിൽ നിങ്ങളുടെ പരാതി നൽകി {{days}} ദിവസത്തിനുള്ളിൽ പരാതി സമർപ്പിക്കണം.");
        m.put("eligibility.block_post_reply_window", "പരാതി സമർപ്പിക്കാനുള്ള കാലാവധി കഴിഞ്ഞു. നിയന്ത്രിത സ്ഥാപനത്തിന്റെ മറുപടിക്ക് ശേഷം {{days}} ദിവസത്തിനുള്ളിൽ പരാതി സമർപ്പിക്കണം.");
        saveLocaleTranslations(m, "ml");
    }

    private void saveLocaleTranslations(Map<String, String> translations, String locale) {
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            keyRepo.findByCode(entry.getKey()).ifPresent(key -> {
                if (!translationRepo.existsByTranslationKeyAndLocale(key, locale)) {
                    Translation t = new Translation();
                    t.setTranslationKey(key);
                    t.setLocale(locale);
                    t.setValue(entry.getValue());
                    translationRepo.save(t);
                }
            });
        }
    }
}
