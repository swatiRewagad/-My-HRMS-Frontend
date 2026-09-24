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

/**
 * Translations for the AA parent-complaint search and appeal Register milestone (session S2A).
 *
 * A SEPARATE seeder from AaTranslationSeeder (@Order(11)) deliberately: three sessions are building the
 * AA module concurrently, and a shared seeder would be a guaranteed merge conflict in a file where a
 * conflict silently costs a locale.
 *
 * Insert-if-absent, like every other seeder here. That has a consequence worth stating: correcting a
 * default in this file does NOT fix rows already in the database, so any later text correction needs a
 * corrective UPDATE in both migrations, scoped BY KEY CODE — localized rows are in native scripts, so
 * an English substring matches none of them.
 *
 * The unresolved_* keys explain a deliberately EMPTY input on the register form. Several fields the
 * stories call "auto-filled" have no source in the schema (see AaAppealAutofillService), and an officer
 * facing a blank box needs to know it is blank because the data does not exist, not because the page
 * failed to load.
 */
@Component
@Order(12)
public class AaRegisterTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "aa";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public AaRegisterTranslationSeeder(TranslationKeyRepository keyRepo,
                                       TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        english().forEach(this::seed);
        seedLocale("hi", hindi());
        seedLocale("mr", marathi());
        seedLocale("bn", bengali());
        seedLocale("te", telugu());
        seedLocale("ta", tamil());
        seedLocale("gu", gujarati());
        seedLocale("ur", urdu());
        seedLocale("kn", kannada());
        seedLocale("ml", malayalam());
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        // ═══ Parent-complaint search ═══
        m.put("aa.search.heading", "Search Parent Complaint");
        m.put("aa.search.complaint_number", "Complaint Number");
        m.put("aa.search.appellant_name", "Appellant Name");
        m.put("aa.search.appellant_mobile", "Mobile Number");
        m.put("aa.search.appellant_email", "Email Address");
        m.put("aa.search.rbio_office", "RBIO Office");
        m.put("aa.search.closure_clause", "Closure Clause");
        m.put("aa.search.category", "Category");
        m.put("aa.search.ground_of_complaint", "Ground of Complaint");
        m.put("aa.search.button_search", "Search");
        m.put("aa.search.button_reset", "Reset");
        m.put("aa.search.no_results", "No complaints match your search.");
        m.put("aa.search.error_no_filter", "Enter at least one search criterion.");
        m.put("aa.search.error_term_too_short", "Enter at least two characters to search by name.");
        m.put("aa.search.error_failed", "The search could not be completed. Please try again.");
        m.put("aa.search.loading", "Searching…");
        m.put("aa.search.results_count", "{{count}} complaint(s) found");
        m.put("aa.search.col_status", "Status");
        m.put("aa.search.col_office", "Office");
        m.put("aa.search.col_closed_on", "Closed On");
        m.put("aa.search.select_option_all", "All");
        m.put("aa.search.no_office_recorded", "No office recorded");

        // ═══ Register milestone ═══
        m.put("aa.register.heading", "Register Appeal / Representation");
        m.put("aa.register.milestone_heading", "Registration Details");
        m.put("aa.register.appeal_filed_by", "Appeal Filed By");
        m.put("aa.register.source_of_appeal", "Source of Appeal");
        m.put("aa.register.mode_of_receipt", "Mode of Receipt");
        m.put("aa.register.appeal_ground", "Ground of Appeal");
        m.put("aa.register.relief_sought", "Relief Sought");
        m.put("aa.register.complainant_heading", "Complainant Details");
        m.put("aa.register.entity_heading", "Regulated Entity Details");
        m.put("aa.register.is_complainant_advocate", "Is the complainant an advocate?");
        m.put("aa.register.has_related_court_trial", "Are there any related court trials?");
        m.put("aa.register.prompt_log_legal_case",
              "Please log this case in the Legal Cases module.");
        m.put("aa.register.ed_approval_given", "ED / equal-rank approval obtained?");
        m.put("aa.register.ed_approval_date", "Date of Approval");
        m.put("aa.register.ed_approval_comments", "Approval Comments");
        m.put("aa.register.ed_approval_document", "Approval Document");
        m.put("aa.register.button_save_proceed", "Save & Proceed");
        m.put("aa.register.create_appeal", "Create Appeal");
        m.put("aa.register.create_representation", "Create Representation");
        m.put("aa.register.success", "The appeal has been registered.");
        m.put("aa.register.error_mandatory_incomplete",
              "Please complete all mandatory fields before proceeding.");
        m.put("aa.register.error_parent_not_appealable",
              "Only a closed or reopened complaint can be appealed.");
        m.put("aa.register.error_not_permitted",
              "You are not permitted to register an appeal against this complaint.");
        m.put("aa.register.unresolved_no_source",
              "Not available from the complaint record — please enter this value.");
        m.put("aa.register.unresolved_not_in_master",
              "Not found in the master data — please enter this value.");
        m.put("aa.register.field_required", "This field is required.");
        m.put("aa.register.yes", "Yes");
        m.put("aa.register.no", "No");

        // ═══ Ground of complaint master labels ═══
        m.put("aa.ground.atm_debit_card", "ATM / Debit Card");
        m.put("aa.ground.credit_card", "Credit Card");
        m.put("aa.ground.internet_banking", "Internet Banking");
        m.put("aa.ground.mobile_banking_upi", "Mobile Banking / UPI");
        m.put("aa.ground.loan_advances", "Loan / Advances");
        m.put("aa.ground.deposit_accounts", "Deposit Accounts");
        m.put("aa.ground.pension", "Pension");
        m.put("aa.ground.remittance_transfer", "Remittance / Transfer");
        m.put("aa.ground.insurance", "Insurance");
        m.put("aa.ground.others", "Others");

        // ═══ Attachment limits (NFR-006) ═══
        m.put("aa.upload.error_file_too_large", "Each file must be {{size}} MB or smaller.");
        m.put("aa.upload.error_total_too_large", "All attachments together must be {{total}} MB or smaller.");
        m.put("aa.upload.error_too_many_files", "You may attach at most {{count}} files.");
        m.put("aa.register.button_retry", "Retry");

        // ═══ Field labels that had no key of their own ═══
        // These were initially borrowed from unrelated modules ('Country' labelled with the RE
        // territory key, 'Entity Region' with an office key, 'BSR/IFSC' with a transaction-reference
        // key). A borrowed key renders a WRONG label in all ten locales, which on a legal intake form
        // means the officer types the wrong value into the wrong field.
        m.put("aa.register.appellant_city", "City");
        m.put("aa.register.appellant_country", "Country");
        m.put("aa.register.appellant_pincode", "Pincode");
        m.put("aa.register.appellant_district", "District");
        m.put("aa.register.appellant_state", "State");
        m.put("aa.register.appellant_address1", "Address Line 1");
        m.put("aa.register.appellant_address2", "Address Line 2");
        m.put("aa.register.entity_name", "Entity Name");
        m.put("aa.register.entity_region", "Entity Region");
        m.put("aa.register.entity_category", "Entity Category");
        m.put("aa.register.entity_branch", "Branch");
        m.put("aa.register.bsr_ifsc_code", "BSR / IFSC Code");
        m.put("aa.register.account_number", "Account Number");
        m.put("aa.register.card_number", "Card Number");
        m.put("aa.register.nodal_officer_name", "Nodal Officer");
        m.put("aa.register.reason_for_delay", "Reason for Delay");
        m.put("aa.register.declarations_heading", "Declarations");
        m.put("aa.register.parent_complaint", "Parent Complaint");
        m.put("aa.register.closure_clause", "Closure Clause");
        m.put("aa.register.filed_by_complainant", "Complainant");
        m.put("aa.register.filed_by_entity", "Regulated Entity");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "मूल शिकायत खोजें");
        m.put("aa.search.complaint_number", "शिकायत संख्या");
        m.put("aa.search.appellant_name", "अपीलकर्ता का नाम");
        m.put("aa.search.appellant_mobile", "मोबाइल नंबर");
        m.put("aa.search.appellant_email", "ईमेल पता");
        m.put("aa.search.rbio_office", "आरबीआईओ कार्यालय");
        m.put("aa.search.closure_clause", "समापन खंड");
        m.put("aa.search.category", "श्रेणी");
        m.put("aa.search.ground_of_complaint", "शिकायत का आधार");
        m.put("aa.search.button_search", "खोजें");
        m.put("aa.search.button_reset", "रीसेट करें");
        m.put("aa.search.no_results", "आपकी खोज से कोई शिकायत मेल नहीं खाती।");
        m.put("aa.search.error_no_filter", "कम से कम एक खोज मानदंड दर्ज करें।");
        m.put("aa.search.error_term_too_short", "नाम से खोजने के लिए कम से कम दो अक्षर दर्ज करें।");
        m.put("aa.search.error_failed", "खोज पूरी नहीं हो सकी। कृपया पुनः प्रयास करें।");
        m.put("aa.search.loading", "खोज रहे हैं…");
        m.put("aa.search.results_count", "{{count}} शिकायतें मिलीं");
        m.put("aa.search.col_status", "स्थिति");
        m.put("aa.search.col_office", "कार्यालय");
        m.put("aa.search.col_closed_on", "बंद होने की तिथि");
        m.put("aa.search.select_option_all", "सभी");
        m.put("aa.search.no_office_recorded", "कोई कार्यालय दर्ज नहीं");
        m.put("aa.register.heading", "अपील / अभ्यावेदन पंजीकृत करें");
        m.put("aa.register.milestone_heading", "पंजीकरण विवरण");
        m.put("aa.register.appeal_filed_by", "अपील दायर करने वाला");
        m.put("aa.register.source_of_appeal", "अपील का स्रोत");
        m.put("aa.register.mode_of_receipt", "प्राप्ति का माध्यम");
        m.put("aa.register.appeal_ground", "अपील का आधार");
        m.put("aa.register.relief_sought", "वांछित राहत");
        m.put("aa.register.complainant_heading", "शिकायतकर्ता का विवरण");
        m.put("aa.register.entity_heading", "विनियमित संस्था का विवरण");
        m.put("aa.register.is_complainant_advocate", "क्या शिकायतकर्ता अधिवक्ता है?");
        m.put("aa.register.has_related_court_trial", "क्या कोई संबंधित न्यायालय मुकदमा है?");
        m.put("aa.register.prompt_log_legal_case", "कृपया इस मामले को कानूनी मामले मॉड्यूल में दर्ज करें।");
        m.put("aa.register.ed_approval_given", "ईडी / समकक्ष रैंक की स्वीकृति प्राप्त हुई?");
        m.put("aa.register.ed_approval_date", "स्वीकृति की तिथि");
        m.put("aa.register.ed_approval_comments", "स्वीकृति टिप्पणियाँ");
        m.put("aa.register.ed_approval_document", "स्वीकृति दस्तावेज़");
        m.put("aa.register.button_save_proceed", "सहेजें और आगे बढ़ें");
        m.put("aa.register.create_appeal", "अपील बनाएँ");
        m.put("aa.register.create_representation", "अभ्यावेदन बनाएँ");
        m.put("aa.register.success", "अपील पंजीकृत कर दी गई है।");
        m.put("aa.register.error_mandatory_incomplete", "आगे बढ़ने से पहले सभी अनिवार्य फ़ील्ड भरें।");
        m.put("aa.register.error_parent_not_appealable",
              "केवल बंद या पुनः खोली गई शिकायत पर ही अपील की जा सकती है।");
        m.put("aa.register.error_not_permitted",
              "आपको इस शिकायत के विरुद्ध अपील पंजीकृत करने की अनुमति नहीं है।");
        m.put("aa.register.unresolved_no_source",
              "शिकायत रिकॉर्ड से उपलब्ध नहीं — कृपया यह मान दर्ज करें।");
        m.put("aa.register.unresolved_not_in_master",
              "मास्टर डेटा में नहीं मिला — कृपया यह मान दर्ज करें।");
        m.put("aa.register.field_required", "यह फ़ील्ड आवश्यक है।");
        m.put("aa.register.yes", "हाँ");
        m.put("aa.register.no", "नहीं");
        m.put("aa.ground.atm_debit_card", "एटीएम / डेबिट कार्ड");
        m.put("aa.ground.credit_card", "क्रेडिट कार्ड");
        m.put("aa.ground.internet_banking", "इंटरनेट बैंकिंग");
        m.put("aa.ground.mobile_banking_upi", "मोबाइल बैंकिंग / यूपीआई");
        m.put("aa.ground.loan_advances", "ऋण / अग्रिम");
        m.put("aa.ground.deposit_accounts", "जमा खाते");
        m.put("aa.ground.pension", "पेंशन");
        m.put("aa.ground.remittance_transfer", "प्रेषण / अंतरण");
        m.put("aa.ground.insurance", "बीमा");
        m.put("aa.ground.others", "अन्य");
        m.put("aa.upload.error_file_too_large", "प्रत्येक फ़ाइल {{size}} एमबी या उससे कम होनी चाहिए।");
        m.put("aa.upload.error_total_too_large", "सभी अनुलग्नक मिलाकर {{total}} एमबी या उससे कम होने चाहिए।");
        m.put("aa.upload.error_too_many_files", "आप अधिकतम {{count}} फ़ाइलें संलग्न कर सकते हैं।");
        m.put("aa.register.button_retry", "पुनः प्रयास करें");
        m.put("aa.register.appellant_city", "शहर");
        m.put("aa.register.appellant_country", "देश");
        m.put("aa.register.appellant_pincode", "पिन कोड");
        m.put("aa.register.appellant_district", "जिला");
        m.put("aa.register.appellant_state", "राज्य");
        m.put("aa.register.appellant_address1", "पता पंक्ति 1");
        m.put("aa.register.appellant_address2", "पता पंक्ति 2");
        m.put("aa.register.entity_name", "संस्था का नाम");
        m.put("aa.register.entity_region", "संस्था क्षेत्र");
        m.put("aa.register.entity_category", "संस्था श्रेणी");
        m.put("aa.register.entity_branch", "शाखा");
        m.put("aa.register.bsr_ifsc_code", "बीएसआर / आईएफएससी कोड");
        m.put("aa.register.account_number", "खाता संख्या");
        m.put("aa.register.card_number", "कार्ड संख्या");
        m.put("aa.register.nodal_officer_name", "नोडल अधिकारी");
        m.put("aa.register.reason_for_delay", "विलंब का कारण");
        m.put("aa.register.declarations_heading", "घोषणाएँ");
        m.put("aa.register.parent_complaint", "मूल शिकायत");
        m.put("aa.register.closure_clause", "समापन खंड");
        m.put("aa.register.filed_by_complainant", "शिकायतकर्ता");
        m.put("aa.register.filed_by_entity", "विनियमित संस्था");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "मूळ तक्रार शोधा");
        m.put("aa.search.complaint_number", "तक्रार क्रमांक");
        m.put("aa.search.appellant_name", "अपीलकर्त्याचे नाव");
        m.put("aa.search.appellant_mobile", "मोबाइल क्रमांक");
        m.put("aa.search.appellant_email", "ईमेल पत्ता");
        m.put("aa.search.rbio_office", "आरबीआयओ कार्यालय");
        m.put("aa.search.closure_clause", "समाप्ती कलम");
        m.put("aa.search.category", "श्रेणी");
        m.put("aa.search.ground_of_complaint", "तक्रारीचा आधार");
        m.put("aa.search.button_search", "शोधा");
        m.put("aa.search.button_reset", "पुन्हा सेट करा");
        m.put("aa.search.no_results", "तुमच्या शोधाशी कोणतीही तक्रार जुळत नाही.");
        m.put("aa.search.error_no_filter", "कमीत कमी एक शोध निकष प्रविष्ट करा.");
        m.put("aa.search.error_term_too_short", "नावाने शोधण्यासाठी कमीत कमी दोन अक्षरे प्रविष्ट करा.");
        m.put("aa.search.error_failed", "शोध पूर्ण होऊ शकला नाही. कृपया पुन्हा प्रयत्न करा.");
        m.put("aa.search.loading", "शोधत आहे…");
        m.put("aa.search.results_count", "{{count}} तक्रारी आढळल्या");
        m.put("aa.search.col_status", "स्थिती");
        m.put("aa.search.col_office", "कार्यालय");
        m.put("aa.search.col_closed_on", "बंद केल्याची तारीख");
        m.put("aa.search.select_option_all", "सर्व");
        m.put("aa.search.no_office_recorded", "कोणतेही कार्यालय नोंदवलेले नाही");
        m.put("aa.register.heading", "अपील / निवेदन नोंदवा");
        m.put("aa.register.milestone_heading", "नोंदणी तपशील");
        m.put("aa.register.appeal_filed_by", "अपील दाखल करणारा");
        m.put("aa.register.source_of_appeal", "अपिलाचा स्रोत");
        m.put("aa.register.mode_of_receipt", "प्राप्तीचा मार्ग");
        m.put("aa.register.appeal_ground", "अपिलाचा आधार");
        m.put("aa.register.relief_sought", "मागितलेली सुटका");
        m.put("aa.register.complainant_heading", "तक्रारदाराचे तपशील");
        m.put("aa.register.entity_heading", "नियंत्रित संस्थेचे तपशील");
        m.put("aa.register.is_complainant_advocate", "तक्रारदार वकील आहे का?");
        m.put("aa.register.has_related_court_trial", "संबंधित न्यायालयीन खटले आहेत का?");
        m.put("aa.register.prompt_log_legal_case", "कृपया हे प्रकरण कायदेशीर प्रकरण मॉड्यूलमध्ये नोंदवा.");
        m.put("aa.register.ed_approval_given", "ईडी / समान दर्जाची मान्यता मिळाली?");
        m.put("aa.register.ed_approval_date", "मान्यतेची तारीख");
        m.put("aa.register.ed_approval_comments", "मान्यता टिप्पण्या");
        m.put("aa.register.ed_approval_document", "मान्यता दस्तऐवज");
        m.put("aa.register.button_save_proceed", "जतन करा आणि पुढे जा");
        m.put("aa.register.create_appeal", "अपील तयार करा");
        m.put("aa.register.create_representation", "निवेदन तयार करा");
        m.put("aa.register.success", "अपील नोंदवले गेले आहे.");
        m.put("aa.register.error_mandatory_incomplete", "पुढे जाण्यापूर्वी सर्व अनिवार्य फील्ड पूर्ण करा.");
        m.put("aa.register.error_parent_not_appealable",
              "केवळ बंद केलेल्या किंवा पुन्हा उघडलेल्या तक्रारीवर अपील करता येते.");
        m.put("aa.register.error_not_permitted",
              "या तक्रारीविरुद्ध अपील नोंदवण्याची तुम्हाला परवानगी नाही.");
        m.put("aa.register.unresolved_no_source",
              "तक्रार नोंदीतून उपलब्ध नाही — कृपया हे मूल्य प्रविष्ट करा.");
        m.put("aa.register.unresolved_not_in_master",
              "मास्टर डेटामध्ये आढळले नाही — कृपया हे मूल्य प्रविष्ट करा.");
        m.put("aa.register.field_required", "हे फील्ड आवश्यक आहे.");
        m.put("aa.register.yes", "होय");
        m.put("aa.register.no", "नाही");
        m.put("aa.ground.atm_debit_card", "एटीएम / डेबिट कार्ड");
        m.put("aa.ground.credit_card", "क्रेडिट कार्ड");
        m.put("aa.ground.internet_banking", "इंटरनेट बँकिंग");
        m.put("aa.ground.mobile_banking_upi", "मोबाइल बँकिंग / यूपीआय");
        m.put("aa.ground.loan_advances", "कर्ज / अग्रिम");
        m.put("aa.ground.deposit_accounts", "ठेव खाती");
        m.put("aa.ground.pension", "निवृत्तिवेतन");
        m.put("aa.ground.remittance_transfer", "पैसे पाठवणे / हस्तांतरण");
        m.put("aa.ground.insurance", "विमा");
        m.put("aa.ground.others", "इतर");
        m.put("aa.upload.error_file_too_large", "प्रत्येक फाइल {{size}} एमबी किंवा त्यापेक्षा कमी असावी.");
        m.put("aa.upload.error_total_too_large", "सर्व संलग्नके मिळून {{total}} एमबी किंवा त्यापेक्षा कमी असावीत.");
        m.put("aa.upload.error_too_many_files", "तुम्ही जास्तीत जास्त {{count}} फाइल्स जोडू शकता.");
        m.put("aa.register.button_retry", "पुन्हा प्रयत्न करा");
        m.put("aa.register.appellant_city", "शहर");
        m.put("aa.register.appellant_country", "देश");
        m.put("aa.register.appellant_pincode", "पिन कोड");
        m.put("aa.register.appellant_district", "जिल्हा");
        m.put("aa.register.appellant_state", "राज्य");
        m.put("aa.register.appellant_address1", "पत्ता ओळ 1");
        m.put("aa.register.appellant_address2", "पत्ता ओळ 2");
        m.put("aa.register.entity_name", "संस्थेचे नाव");
        m.put("aa.register.entity_region", "संस्था प्रदेश");
        m.put("aa.register.entity_category", "संस्था श्रेणी");
        m.put("aa.register.entity_branch", "शाखा");
        m.put("aa.register.bsr_ifsc_code", "बीएसआर / आयएफएससी कोड");
        m.put("aa.register.account_number", "खाते क्रमांक");
        m.put("aa.register.card_number", "कार्ड क्रमांक");
        m.put("aa.register.nodal_officer_name", "नोडल अधिकारी");
        m.put("aa.register.reason_for_delay", "विलंबाचे कारण");
        m.put("aa.register.declarations_heading", "घोषणा");
        m.put("aa.register.parent_complaint", "मूळ तक्रार");
        m.put("aa.register.closure_clause", "समाप्ती कलम");
        m.put("aa.register.filed_by_complainant", "तक्रारदार");
        m.put("aa.register.filed_by_entity", "नियंत्रित संस्था");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "মূল অভিযোগ খুঁজুন");
        m.put("aa.search.complaint_number", "অভিযোগ নম্বর");
        m.put("aa.search.appellant_name", "আপিলকারীর নাম");
        m.put("aa.search.appellant_mobile", "মোবাইল নম্বর");
        m.put("aa.search.appellant_email", "ইমেল ঠিকানা");
        m.put("aa.search.rbio_office", "আরবিআইও কার্যালয়");
        m.put("aa.search.closure_clause", "নিষ্পত্তি ধারা");
        m.put("aa.search.category", "শ্রেণি");
        m.put("aa.search.ground_of_complaint", "অভিযোগের ভিত্তি");
        m.put("aa.search.button_search", "খুঁজুন");
        m.put("aa.search.button_reset", "পুনঃনির্ধারণ");
        m.put("aa.search.no_results", "আপনার অনুসন্ধানের সঙ্গে কোনো অভিযোগ মেলেনি।");
        m.put("aa.search.error_no_filter", "অন্তত একটি অনুসন্ধান শর্ত লিখুন।");
        m.put("aa.search.error_term_too_short", "নাম দিয়ে খুঁজতে অন্তত দুটি অক্ষর লিখুন।");
        m.put("aa.search.error_failed", "অনুসন্ধান সম্পূর্ণ করা যায়নি। অনুগ্রহ করে আবার চেষ্টা করুন।");
        m.put("aa.search.loading", "খোঁজা হচ্ছে…");
        m.put("aa.search.results_count", "{{count}}টি অভিযোগ পাওয়া গেছে");
        m.put("aa.search.col_status", "অবস্থা");
        m.put("aa.search.col_office", "কার্যালয়");
        m.put("aa.search.col_closed_on", "নিষ্পত্তির তারিখ");
        m.put("aa.search.select_option_all", "সব");
        m.put("aa.search.no_office_recorded", "কোনো কার্যালয় নথিভুক্ত নেই");
        m.put("aa.register.heading", "আপিল / প্রতিবেদন নিবন্ধন করুন");
        m.put("aa.register.milestone_heading", "নিবন্ধনের বিবরণ");
        m.put("aa.register.appeal_filed_by", "আপিল দাখিলকারী");
        m.put("aa.register.source_of_appeal", "আপিলের উৎস");
        m.put("aa.register.mode_of_receipt", "প্রাপ্তির মাধ্যম");
        m.put("aa.register.appeal_ground", "আপিলের ভিত্তি");
        m.put("aa.register.relief_sought", "প্রার্থিত প্রতিকার");
        m.put("aa.register.complainant_heading", "অভিযোগকারীর বিবরণ");
        m.put("aa.register.entity_heading", "নিয়ন্ত্রিত সংস্থার বিবরণ");
        m.put("aa.register.is_complainant_advocate", "অভিযোগকারী কি একজন আইনজীবী?");
        m.put("aa.register.has_related_court_trial", "সম্পর্কিত কোনো আদালতের বিচার আছে কি?");
        m.put("aa.register.prompt_log_legal_case", "অনুগ্রহ করে এই মামলাটি আইনি মামলা মডিউলে নথিভুক্ত করুন।");
        m.put("aa.register.ed_approval_given", "ইডি / সমপদস্থের অনুমোদন পাওয়া গেছে?");
        m.put("aa.register.ed_approval_date", "অনুমোদনের তারিখ");
        m.put("aa.register.ed_approval_comments", "অনুমোদনের মন্তব্য");
        m.put("aa.register.ed_approval_document", "অনুমোদনের নথি");
        m.put("aa.register.button_save_proceed", "সংরক্ষণ করে এগিয়ে যান");
        m.put("aa.register.create_appeal", "আপিল তৈরি করুন");
        m.put("aa.register.create_representation", "প্রতিবেদন তৈরি করুন");
        m.put("aa.register.success", "আপিলটি নিবন্ধিত হয়েছে।");
        m.put("aa.register.error_mandatory_incomplete", "এগিয়ে যাওয়ার আগে সব আবশ্যক ক্ষেত্র পূরণ করুন।");
        m.put("aa.register.error_parent_not_appealable",
              "কেবল নিষ্পত্তি হওয়া বা পুনরায় খোলা অভিযোগের বিরুদ্ধেই আপিল করা যায়।");
        m.put("aa.register.error_not_permitted",
              "এই অভিযোগের বিরুদ্ধে আপিল নিবন্ধন করার অনুমতি আপনার নেই।");
        m.put("aa.register.unresolved_no_source",
              "অভিযোগের নথি থেকে পাওয়া যায়নি — অনুগ্রহ করে এই মানটি লিখুন।");
        m.put("aa.register.unresolved_not_in_master",
              "মাস্টার ডেটায় পাওয়া যায়নি — অনুগ্রহ করে এই মানটি লিখুন।");
        m.put("aa.register.field_required", "এই ক্ষেত্রটি আবশ্যক।");
        m.put("aa.register.yes", "হ্যাঁ");
        m.put("aa.register.no", "না");
        m.put("aa.ground.atm_debit_card", "এটিএম / ডেবিট কার্ড");
        m.put("aa.ground.credit_card", "ক্রেডিট কার্ড");
        m.put("aa.ground.internet_banking", "ইন্টারনেট ব্যাঙ্কিং");
        m.put("aa.ground.mobile_banking_upi", "মোবাইল ব্যাঙ্কিং / ইউপিআই");
        m.put("aa.ground.loan_advances", "ঋণ / অগ্রিম");
        m.put("aa.ground.deposit_accounts", "আমানত হিসাব");
        m.put("aa.ground.pension", "পেনশন");
        m.put("aa.ground.remittance_transfer", "প্রেরণ / স্থানান্তর");
        m.put("aa.ground.insurance", "বিমা");
        m.put("aa.ground.others", "অন্যান্য");
        // Bengali stores digits in Bengali numerals: ২ = 2, ২৫ = 25, ১০ = 10.
        m.put("aa.upload.error_file_too_large", "প্রতিটি ফাইল {{size}} এমবি বা তার কম হতে হবে।");
        m.put("aa.upload.error_total_too_large", "সব সংযুক্তি একসঙ্গে {{total}} এমবি বা তার কম হতে হবে।");
        m.put("aa.upload.error_too_many_files", "আপনি সর্বাধিক {{count}}টি ফাইল সংযুক্ত করতে পারেন।");
        m.put("aa.register.button_retry", "আবার চেষ্টা করুন");
        m.put("aa.register.appellant_city", "শহর");
        m.put("aa.register.appellant_country", "দেশ");
        m.put("aa.register.appellant_pincode", "পিন কোড");
        m.put("aa.register.appellant_district", "জেলা");
        m.put("aa.register.appellant_state", "রাজ্য");
        m.put("aa.register.appellant_address1", "ঠিকানা লাইন ১");
        m.put("aa.register.appellant_address2", "ঠিকানা লাইন ২");
        m.put("aa.register.entity_name", "সংস্থার নাম");
        m.put("aa.register.entity_region", "সংস্থার অঞ্চল");
        m.put("aa.register.entity_category", "সংস্থার শ্রেণি");
        m.put("aa.register.entity_branch", "শাখা");
        m.put("aa.register.bsr_ifsc_code", "বিএসআর / আইএফএসসি কোড");
        m.put("aa.register.account_number", "হিসাব নম্বর");
        m.put("aa.register.card_number", "কার্ড নম্বর");
        m.put("aa.register.nodal_officer_name", "নোডাল অফিসার");
        m.put("aa.register.reason_for_delay", "বিলম্বের কারণ");
        m.put("aa.register.declarations_heading", "ঘোষণা");
        m.put("aa.register.parent_complaint", "মূল অভিযোগ");
        m.put("aa.register.closure_clause", "নিষ্পত্তি ধারা");
        m.put("aa.register.filed_by_complainant", "অভিযোগকারী");
        m.put("aa.register.filed_by_entity", "নিয়ন্ত্রিত সংস্থা");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "మూల ఫిర్యాదును వెతకండి");
        m.put("aa.search.complaint_number", "ఫిర్యాదు సంఖ్య");
        m.put("aa.search.appellant_name", "అప్పీలుదారు పేరు");
        m.put("aa.search.appellant_mobile", "మొబైల్ నంబర్");
        m.put("aa.search.appellant_email", "ఇమెయిల్ చిరునామా");
        m.put("aa.search.rbio_office", "ఆర్‌బీఐఓ కార్యాలయం");
        m.put("aa.search.closure_clause", "ముగింపు నిబంధన");
        m.put("aa.search.category", "వర్గం");
        m.put("aa.search.ground_of_complaint", "ఫిర్యాదు ఆధారం");
        m.put("aa.search.button_search", "వెతకండి");
        m.put("aa.search.button_reset", "రీసెట్ చేయండి");
        m.put("aa.search.no_results", "మీ శోధనకు ఏ ఫిర్యాదు సరిపోలలేదు.");
        m.put("aa.search.error_no_filter", "కనీసం ఒక శోధన ప్రమాణాన్ని నమోదు చేయండి.");
        m.put("aa.search.error_term_too_short", "పేరుతో వెతకడానికి కనీసం రెండు అక్షరాలు నమోదు చేయండి.");
        m.put("aa.search.error_failed", "శోధన పూర్తి కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("aa.search.loading", "వెతుకుతోంది…");
        m.put("aa.search.results_count", "{{count}} ఫిర్యాదులు కనుగొనబడ్డాయి");
        m.put("aa.search.col_status", "స్థితి");
        m.put("aa.search.col_office", "కార్యాలయం");
        m.put("aa.search.col_closed_on", "ముగించిన తేదీ");
        m.put("aa.search.select_option_all", "అన్నీ");
        m.put("aa.search.no_office_recorded", "కార్యాలయం నమోదు కాలేదు");
        m.put("aa.register.heading", "అప్పీలు / వినతిని నమోదు చేయండి");
        m.put("aa.register.milestone_heading", "నమోదు వివరాలు");
        m.put("aa.register.appeal_filed_by", "అప్పీలు దాఖలు చేసినవారు");
        m.put("aa.register.source_of_appeal", "అప్పీలు మూలం");
        m.put("aa.register.mode_of_receipt", "స్వీకరణ విధానం");
        m.put("aa.register.appeal_ground", "అప్పీలు ఆధారం");
        m.put("aa.register.relief_sought", "కోరిన ఉపశమనం");
        m.put("aa.register.complainant_heading", "ఫిర్యాదుదారు వివరాలు");
        m.put("aa.register.entity_heading", "నియంత్రిత సంస్థ వివరాలు");
        m.put("aa.register.is_complainant_advocate", "ఫిర్యాదుదారు న్యాయవాదియా?");
        m.put("aa.register.has_related_court_trial", "సంబంధిత న్యాయస్థాన విచారణలు ఉన్నాయా?");
        m.put("aa.register.prompt_log_legal_case", "దయచేసి ఈ కేసును న్యాయ కేసుల మాడ్యూల్‌లో నమోదు చేయండి.");
        m.put("aa.register.ed_approval_given", "ఈడీ / సమాన స్థాయి ఆమోదం పొందారా?");
        m.put("aa.register.ed_approval_date", "ఆమోదం తేదీ");
        m.put("aa.register.ed_approval_comments", "ఆమోద వ్యాఖ్యలు");
        m.put("aa.register.ed_approval_document", "ఆమోద పత్రం");
        m.put("aa.register.button_save_proceed", "భద్రపరచి కొనసాగండి");
        m.put("aa.register.create_appeal", "అప్పీలును సృష్టించండి");
        m.put("aa.register.create_representation", "వినతిని సృష్టించండి");
        m.put("aa.register.success", "అప్పీలు నమోదు చేయబడింది.");
        m.put("aa.register.error_mandatory_incomplete", "కొనసాగే ముందు అన్ని తప్పనిసరి ఫీల్డ్‌లను పూర్తి చేయండి.");
        m.put("aa.register.error_parent_not_appealable",
              "ముగించిన లేదా తిరిగి తెరిచిన ఫిర్యాదుపై మాత్రమే అప్పీలు చేయవచ్చు.");
        m.put("aa.register.error_not_permitted",
              "ఈ ఫిర్యాదుపై అప్పీలు నమోదు చేసే అనుమతి మీకు లేదు.");
        m.put("aa.register.unresolved_no_source",
              "ఫిర్యాదు రికార్డు నుండి అందుబాటులో లేదు — దయచేసి ఈ విలువను నమోదు చేయండి.");
        m.put("aa.register.unresolved_not_in_master",
              "మాస్టర్ డేటాలో కనుగొనబడలేదు — దయచేసి ఈ విలువను నమోదు చేయండి.");
        m.put("aa.register.field_required", "ఈ ఫీల్డ్ అవసరం.");
        m.put("aa.register.yes", "అవును");
        m.put("aa.register.no", "కాదు");
        m.put("aa.ground.atm_debit_card", "ఏటీఎం / డెబిట్ కార్డ్");
        m.put("aa.ground.credit_card", "క్రెడిట్ కార్డ్");
        m.put("aa.ground.internet_banking", "ఇంటర్నెట్ బ్యాంకింగ్");
        m.put("aa.ground.mobile_banking_upi", "మొబైల్ బ్యాంకింగ్ / యూపీఐ");
        m.put("aa.ground.loan_advances", "రుణం / అడ్వాన్సులు");
        m.put("aa.ground.deposit_accounts", "డిపాజిట్ ఖాతాలు");
        m.put("aa.ground.pension", "పెన్షన్");
        m.put("aa.ground.remittance_transfer", "చెల్లింపు / బదిలీ");
        m.put("aa.ground.insurance", "బీమా");
        m.put("aa.ground.others", "ఇతరాలు");
        m.put("aa.upload.error_file_too_large", "ప్రతి ఫైల్ {{size}} ఎంబీ లోపు ఉండాలి.");
        m.put("aa.upload.error_total_too_large", "అన్ని జోడింపులు కలిపి {{total}} ఎంబీ లోపు ఉండాలి.");
        m.put("aa.upload.error_too_many_files", "మీరు గరిష్ఠంగా {{count}} ఫైల్‌లను జోడించగలరు.");
        m.put("aa.register.button_retry", "మళ్లీ ప్రయత్నించండి");
        m.put("aa.register.appellant_city", "నగరం");
        m.put("aa.register.appellant_country", "దేశం");
        m.put("aa.register.appellant_pincode", "పిన్ కోడ్");
        m.put("aa.register.appellant_district", "జిల్లా");
        m.put("aa.register.appellant_state", "రాష్ట్రం");
        m.put("aa.register.appellant_address1", "చిరునామా పంక్తి 1");
        m.put("aa.register.appellant_address2", "చిరునామా పంక్తి 2");
        m.put("aa.register.entity_name", "సంస్థ పేరు");
        m.put("aa.register.entity_region", "సంస్థ ప్రాంతం");
        m.put("aa.register.entity_category", "సంస్థ వర్గం");
        m.put("aa.register.entity_branch", "శాఖ");
        m.put("aa.register.bsr_ifsc_code", "బీఎస్ఆర్ / ఐఎఫ్ఎస్‌సీ కోడ్");
        m.put("aa.register.account_number", "ఖాతా సంఖ్య");
        m.put("aa.register.card_number", "కార్డ్ సంఖ్య");
        m.put("aa.register.nodal_officer_name", "నోడల్ అధికారి");
        m.put("aa.register.reason_for_delay", "ఆలస్యానికి కారణం");
        m.put("aa.register.declarations_heading", "ప్రకటనలు");
        m.put("aa.register.parent_complaint", "మూల ఫిర్యాదు");
        m.put("aa.register.closure_clause", "ముగింపు నిబంధన");
        m.put("aa.register.filed_by_complainant", "ఫిర్యాదుదారు");
        m.put("aa.register.filed_by_entity", "నియంత్రిత సంస్థ");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "மூல புகாரைத் தேடுங்கள்");
        m.put("aa.search.complaint_number", "புகார் எண்");
        m.put("aa.search.appellant_name", "மேல்முறையீட்டாளர் பெயர்");
        m.put("aa.search.appellant_mobile", "கைபேசி எண்");
        m.put("aa.search.appellant_email", "மின்னஞ்சல் முகவரி");
        m.put("aa.search.rbio_office", "ஆர்பிஐஓ அலுவலகம்");
        m.put("aa.search.closure_clause", "முடிவுறுத்தல் பிரிவு");
        m.put("aa.search.category", "வகை");
        m.put("aa.search.ground_of_complaint", "புகாரின் அடிப்படை");
        m.put("aa.search.button_search", "தேடு");
        m.put("aa.search.button_reset", "மீட்டமை");
        m.put("aa.search.no_results", "உங்கள் தேடலுக்கு எந்த புகாரும் பொருந்தவில்லை.");
        m.put("aa.search.error_no_filter", "குறைந்தது ஒரு தேடல் நிபந்தனையை உள்ளிடுங்கள்.");
        m.put("aa.search.error_term_too_short", "பெயரால் தேட குறைந்தது இரண்டு எழுத்துகளை உள்ளிடுங்கள்.");
        m.put("aa.search.error_failed", "தேடலை நிறைவு செய்ய முடியவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("aa.search.loading", "தேடுகிறது…");
        m.put("aa.search.results_count", "{{count}} புகார்கள் கிடைத்தன");
        m.put("aa.search.col_status", "நிலை");
        m.put("aa.search.col_office", "அலுவலகம்");
        m.put("aa.search.col_closed_on", "முடிக்கப்பட்ட தேதி");
        m.put("aa.search.select_option_all", "அனைத்தும்");
        m.put("aa.search.no_office_recorded", "அலுவலகம் பதிவு செய்யப்படவில்லை");
        m.put("aa.register.heading", "மேல்முறையீடு / விண்ணப்பத்தைப் பதிவு செய்யுங்கள்");
        m.put("aa.register.milestone_heading", "பதிவு விவரங்கள்");
        m.put("aa.register.appeal_filed_by", "மேல்முறையீடு தாக்கல் செய்தவர்");
        m.put("aa.register.source_of_appeal", "மேல்முறையீட்டின் ஆதாரம்");
        m.put("aa.register.mode_of_receipt", "பெறப்பட்ட முறை");
        m.put("aa.register.appeal_ground", "மேல்முறையீட்டின் அடிப்படை");
        m.put("aa.register.relief_sought", "கோரப்பட்ட நிவாரணம்");
        m.put("aa.register.complainant_heading", "புகார்தாரர் விவரங்கள்");
        m.put("aa.register.entity_heading", "ஒழுங்குமுறை நிறுவன விவரங்கள்");
        m.put("aa.register.is_complainant_advocate", "புகார்தாரர் வழக்கறிஞரா?");
        m.put("aa.register.has_related_court_trial", "தொடர்புடைய நீதிமன்ற வழக்குகள் உள்ளதா?");
        m.put("aa.register.prompt_log_legal_case", "இந்த வழக்கை சட்ட வழக்குகள் தொகுதியில் பதிவு செய்யுங்கள்.");
        m.put("aa.register.ed_approval_given", "ஈடி / சமநிலை அதிகாரியின் ஒப்புதல் பெறப்பட்டதா?");
        m.put("aa.register.ed_approval_date", "ஒப்புதல் தேதி");
        m.put("aa.register.ed_approval_comments", "ஒப்புதல் கருத்துகள்");
        m.put("aa.register.ed_approval_document", "ஒப்புதல் ஆவணம்");
        m.put("aa.register.button_save_proceed", "சேமித்து தொடரவும்");
        m.put("aa.register.create_appeal", "மேல்முறையீட்டை உருவாக்கு");
        m.put("aa.register.create_representation", "விண்ணப்பத்தை உருவாக்கு");
        m.put("aa.register.success", "மேல்முறையீடு பதிவு செய்யப்பட்டது.");
        m.put("aa.register.error_mandatory_incomplete", "தொடர்வதற்கு முன் அனைத்து கட்டாய புலங்களையும் நிரப்பவும்.");
        m.put("aa.register.error_parent_not_appealable",
              "முடிக்கப்பட்ட அல்லது மீண்டும் திறக்கப்பட்ட புகார் மீது மட்டுமே மேல்முறையீடு செய்ய முடியும்.");
        m.put("aa.register.error_not_permitted",
              "இந்த புகாருக்கு எதிராக மேல்முறையீட்டைப் பதிவு செய்ய உங்களுக்கு அனுமதி இல்லை.");
        m.put("aa.register.unresolved_no_source",
              "புகார் பதிவேட்டில் கிடைக்கவில்லை — இந்த மதிப்பை உள்ளிடுங்கள்.");
        m.put("aa.register.unresolved_not_in_master",
              "முதன்மைத் தரவில் காணப்படவில்லை — இந்த மதிப்பை உள்ளிடுங்கள்.");
        m.put("aa.register.field_required", "இந்தப் புலம் தேவை.");
        m.put("aa.register.yes", "ஆம்");
        m.put("aa.register.no", "இல்லை");
        m.put("aa.ground.atm_debit_card", "ஏடிஎம் / டெபிட் அட்டை");
        m.put("aa.ground.credit_card", "கடன் அட்டை");
        m.put("aa.ground.internet_banking", "இணைய வங்கி சேவை");
        m.put("aa.ground.mobile_banking_upi", "கைபேசி வங்கி சேவை / யூபிஐ");
        m.put("aa.ground.loan_advances", "கடன் / முன்பணம்");
        m.put("aa.ground.deposit_accounts", "வைப்பு கணக்குகள்");
        m.put("aa.ground.pension", "ஓய்வூதியம்");
        m.put("aa.ground.remittance_transfer", "பணப் பரிமாற்றம்");
        m.put("aa.ground.insurance", "காப்பீடு");
        m.put("aa.ground.others", "மற்றவை");
        m.put("aa.upload.error_file_too_large", "ஒவ்வொரு கோப்பும் {{size}} எம்பி அல்லது குறைவாக இருக்க வேண்டும்.");
        m.put("aa.upload.error_total_too_large", "அனைத்து இணைப்புகளும் சேர்ந்து {{total}} எம்பி அல்லது குறைவாக இருக்க வேண்டும்.");
        m.put("aa.upload.error_too_many_files", "நீங்கள் அதிகபட்சம் {{count}} கோப்புகளை இணைக்கலாம்.");
        m.put("aa.register.button_retry", "மீண்டும் முயற்சி");
        m.put("aa.register.appellant_city", "நகரம்");
        m.put("aa.register.appellant_country", "நாடு");
        m.put("aa.register.appellant_pincode", "அஞ்சல் குறியீடு");
        m.put("aa.register.appellant_district", "மாவட்டம்");
        m.put("aa.register.appellant_state", "மாநிலம்");
        m.put("aa.register.appellant_address1", "முகவரி வரி 1");
        m.put("aa.register.appellant_address2", "முகவரி வரி 2");
        m.put("aa.register.entity_name", "நிறுவனத்தின் பெயர்");
        m.put("aa.register.entity_region", "நிறுவன பிராந்தியம்");
        m.put("aa.register.entity_category", "நிறுவன வகை");
        m.put("aa.register.entity_branch", "கிளை");
        m.put("aa.register.bsr_ifsc_code", "பிஎஸ்ஆர் / ஐஎஃப்எஸ்சி குறியீடு");
        m.put("aa.register.account_number", "கணக்கு எண்");
        m.put("aa.register.card_number", "அட்டை எண்");
        m.put("aa.register.nodal_officer_name", "நோடல் அதிகாரி");
        m.put("aa.register.reason_for_delay", "தாமதத்திற்கான காரணம்");
        m.put("aa.register.declarations_heading", "அறிவிப்புகள்");
        m.put("aa.register.parent_complaint", "மூல புகார்");
        m.put("aa.register.closure_clause", "முடிவுறுத்தல் பிரிவு");
        m.put("aa.register.filed_by_complainant", "புகார்தாரர்");
        m.put("aa.register.filed_by_entity", "ஒழுங்குமுறை நிறுவனம்");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "મૂળ ફરિયાદ શોધો");
        m.put("aa.search.complaint_number", "ફરિયાદ નંબર");
        m.put("aa.search.appellant_name", "અપીલકર્તાનું નામ");
        m.put("aa.search.appellant_mobile", "મોબાઇલ નંબર");
        m.put("aa.search.appellant_email", "ઇમેલ સરનામું");
        m.put("aa.search.rbio_office", "આરબીઆઈઓ કાર્યાલય");
        m.put("aa.search.closure_clause", "સમાપન કલમ");
        m.put("aa.search.category", "શ્રેણી");
        m.put("aa.search.ground_of_complaint", "ફરિયાદનો આધાર");
        m.put("aa.search.button_search", "શોધો");
        m.put("aa.search.button_reset", "રીસેટ કરો");
        m.put("aa.search.no_results", "તમારી શોધ સાથે કોઈ ફરિયાદ મેળ ખાતી નથી.");
        m.put("aa.search.error_no_filter", "ઓછામાં ઓછું એક શોધ માપદંડ દાખલ કરો.");
        m.put("aa.search.error_term_too_short", "નામથી શોધવા માટે ઓછામાં ઓછા બે અક્ષરો દાખલ કરો.");
        m.put("aa.search.error_failed", "શોધ પૂર્ણ થઈ શકી નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("aa.search.loading", "શોધી રહ્યા છીએ…");
        m.put("aa.search.results_count", "{{count}} ફરિયાદો મળી");
        m.put("aa.search.col_status", "સ્થિતિ");
        m.put("aa.search.col_office", "કાર્યાલય");
        m.put("aa.search.col_closed_on", "બંધ કરવાની તારીખ");
        m.put("aa.search.select_option_all", "બધા");
        m.put("aa.search.no_office_recorded", "કોઈ કાર્યાલય નોંધાયેલ નથી");
        m.put("aa.register.heading", "અપીલ / રજૂઆત નોંધો");
        m.put("aa.register.milestone_heading", "નોંધણી વિગતો");
        m.put("aa.register.appeal_filed_by", "અપીલ દાખલ કરનાર");
        m.put("aa.register.source_of_appeal", "અપીલનો સ્રોત");
        m.put("aa.register.mode_of_receipt", "પ્રાપ્તિની રીત");
        m.put("aa.register.appeal_ground", "અપીલનો આધાર");
        m.put("aa.register.relief_sought", "માંગેલી રાહત");
        m.put("aa.register.complainant_heading", "ફરિયાદીની વિગતો");
        m.put("aa.register.entity_heading", "નિયંત્રિત સંસ્થાની વિગતો");
        m.put("aa.register.is_complainant_advocate", "શું ફરિયાદી વકીલ છે?");
        m.put("aa.register.has_related_court_trial", "શું કોઈ સંબંધિત કોર્ટ કેસ છે?");
        m.put("aa.register.prompt_log_legal_case", "કૃપા કરીને આ કેસ કાનૂની કેસ મોડ્યુલમાં નોંધો.");
        m.put("aa.register.ed_approval_given", "ઈડી / સમકક્ષ કક્ષાની મંજૂરી મળી?");
        m.put("aa.register.ed_approval_date", "મંજૂરીની તારીખ");
        m.put("aa.register.ed_approval_comments", "મંજૂરી ટિપ્પણીઓ");
        m.put("aa.register.ed_approval_document", "મંજૂરી દસ્તાવેજ");
        m.put("aa.register.button_save_proceed", "સાચવો અને આગળ વધો");
        m.put("aa.register.create_appeal", "અપીલ બનાવો");
        m.put("aa.register.create_representation", "રજૂઆત બનાવો");
        m.put("aa.register.success", "અપીલ નોંધાઈ ગઈ છે.");
        m.put("aa.register.error_mandatory_incomplete", "આગળ વધતા પહેલાં તમામ ફરજિયાત ક્ષેત્રો પૂર્ણ કરો.");
        m.put("aa.register.error_parent_not_appealable",
              "ફક્ત બંધ કરેલી અથવા ફરી ખોલેલી ફરિયાદ પર જ અપીલ કરી શકાય છે.");
        m.put("aa.register.error_not_permitted",
              "આ ફરિયાદ વિરુદ્ધ અપીલ નોંધવાની તમને પરવાનગી નથી.");
        m.put("aa.register.unresolved_no_source",
              "ફરિયાદ રેકોર્ડમાંથી ઉપલબ્ધ નથી — કૃપા કરીને આ મૂલ્ય દાખલ કરો.");
        m.put("aa.register.unresolved_not_in_master",
              "માસ્ટર ડેટામાં મળ્યું નથી — કૃપા કરીને આ મૂલ્ય દાખલ કરો.");
        m.put("aa.register.field_required", "આ ક્ષેત્ર આવશ્યક છે.");
        m.put("aa.register.yes", "હા");
        m.put("aa.register.no", "ના");
        m.put("aa.ground.atm_debit_card", "એટીએમ / ડેબિટ કાર્ડ");
        m.put("aa.ground.credit_card", "ક્રેડિટ કાર્ડ");
        m.put("aa.ground.internet_banking", "ઇન્ટરનેટ બેન્કિંગ");
        m.put("aa.ground.mobile_banking_upi", "મોબાઇલ બેન્કિંગ / યુપીઆઈ");
        m.put("aa.ground.loan_advances", "લોન / એડવાન્સ");
        m.put("aa.ground.deposit_accounts", "ડિપોઝિટ ખાતાં");
        m.put("aa.ground.pension", "પેન્શન");
        m.put("aa.ground.remittance_transfer", "રકમ મોકલવી / તબદીલી");
        m.put("aa.ground.insurance", "વીમો");
        m.put("aa.ground.others", "અન્ય");
        m.put("aa.upload.error_file_too_large", "દરેક ફાઇલ {{size}} એમબી અથવા તેથી નાની હોવી જોઈએ.");
        m.put("aa.upload.error_total_too_large", "તમામ જોડાણો મળીને {{total}} એમબી અથવા તેથી નાનાં હોવાં જોઈએ.");
        m.put("aa.upload.error_too_many_files", "તમે વધુમાં વધુ {{count}} ફાઇલો જોડી શકો છો.");
        m.put("aa.register.button_retry", "ફરી પ્રયાસ કરો");
        m.put("aa.register.appellant_city", "શહેર");
        m.put("aa.register.appellant_country", "દેશ");
        m.put("aa.register.appellant_pincode", "પિન કોડ");
        m.put("aa.register.appellant_district", "જિલ્લો");
        m.put("aa.register.appellant_state", "રાજ્ય");
        m.put("aa.register.appellant_address1", "સરનામું લાઇન 1");
        m.put("aa.register.appellant_address2", "સરનામું લાઇન 2");
        m.put("aa.register.entity_name", "સંસ્થાનું નામ");
        m.put("aa.register.entity_region", "સંસ્થા પ્રદેશ");
        m.put("aa.register.entity_category", "સંસ્થા શ્રેણી");
        m.put("aa.register.entity_branch", "શાખા");
        m.put("aa.register.bsr_ifsc_code", "બીએસઆર / આઈએફએસસી કોડ");
        m.put("aa.register.account_number", "ખાતા નંબર");
        m.put("aa.register.card_number", "કાર્ડ નંબર");
        m.put("aa.register.nodal_officer_name", "નોડલ અધિકારી");
        m.put("aa.register.reason_for_delay", "વિલંબનું કારણ");
        m.put("aa.register.declarations_heading", "ઘોષણાઓ");
        m.put("aa.register.parent_complaint", "મૂળ ફરિયાદ");
        m.put("aa.register.closure_clause", "સમાપન કલમ");
        m.put("aa.register.filed_by_complainant", "ફરિયાદી");
        m.put("aa.register.filed_by_entity", "નિયંત્રિત સંસ્થા");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "بنیادی شکایت تلاش کریں");
        m.put("aa.search.complaint_number", "شکایت نمبر");
        m.put("aa.search.appellant_name", "اپیل کنندہ کا نام");
        m.put("aa.search.appellant_mobile", "موبائل نمبر");
        m.put("aa.search.appellant_email", "ای میل پتہ");
        m.put("aa.search.rbio_office", "آر بی آئی او دفتر");
        m.put("aa.search.closure_clause", "اختتامی شق");
        m.put("aa.search.category", "قسم");
        m.put("aa.search.ground_of_complaint", "شکایت کی بنیاد");
        m.put("aa.search.button_search", "تلاش کریں");
        m.put("aa.search.button_reset", "دوبارہ ترتیب دیں");
        m.put("aa.search.no_results", "آپ کی تلاش سے کوئی شکایت مطابقت نہیں رکھتی۔");
        m.put("aa.search.error_no_filter", "کم از کم ایک تلاش کا معیار درج کریں۔");
        m.put("aa.search.error_term_too_short", "نام سے تلاش کرنے کے لیے کم از کم دو حروف درج کریں۔");
        m.put("aa.search.error_failed", "تلاش مکمل نہیں ہو سکی۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("aa.search.loading", "تلاش جاری ہے…");
        m.put("aa.search.results_count", "{{count}} شکایات ملیں");
        m.put("aa.search.col_status", "حالت");
        m.put("aa.search.col_office", "دفتر");
        m.put("aa.search.col_closed_on", "بند ہونے کی تاریخ");
        m.put("aa.search.select_option_all", "تمام");
        m.put("aa.search.no_office_recorded", "کوئی دفتر درج نہیں");
        m.put("aa.register.heading", "اپیل / نمائندگی درج کریں");
        m.put("aa.register.milestone_heading", "اندراج کی تفصیلات");
        m.put("aa.register.appeal_filed_by", "اپیل دائر کرنے والا");
        m.put("aa.register.source_of_appeal", "اپیل کا ذریعہ");
        m.put("aa.register.mode_of_receipt", "وصولی کا طریقہ");
        m.put("aa.register.appeal_ground", "اپیل کی بنیاد");
        m.put("aa.register.relief_sought", "مطلوبہ ریلیف");
        m.put("aa.register.complainant_heading", "شکایت کنندہ کی تفصیلات");
        m.put("aa.register.entity_heading", "ریگولیٹڈ ادارے کی تفصیلات");
        m.put("aa.register.is_complainant_advocate", "کیا شکایت کنندہ وکیل ہے؟");
        m.put("aa.register.has_related_court_trial", "کیا کوئی متعلقہ عدالتی مقدمہ ہے؟");
        m.put("aa.register.prompt_log_legal_case", "براہ کرم اس مقدمے کو قانونی مقدمات ماڈیول میں درج کریں۔");
        m.put("aa.register.ed_approval_given", "ای ڈی / ہم پلہ افسر کی منظوری حاصل ہوئی؟");
        m.put("aa.register.ed_approval_date", "منظوری کی تاریخ");
        m.put("aa.register.ed_approval_comments", "منظوری کے تبصرے");
        m.put("aa.register.ed_approval_document", "منظوری کی دستاویز");
        m.put("aa.register.button_save_proceed", "محفوظ کریں اور آگے بڑھیں");
        m.put("aa.register.create_appeal", "اپیل بنائیں");
        m.put("aa.register.create_representation", "نمائندگی بنائیں");
        m.put("aa.register.success", "اپیل درج کر دی گئی ہے۔");
        m.put("aa.register.error_mandatory_incomplete", "آگے بڑھنے سے پہلے تمام لازمی خانے مکمل کریں۔");
        m.put("aa.register.error_parent_not_appealable",
              "صرف بند شدہ یا دوبارہ کھولی گئی شکایت پر اپیل کی جا سکتی ہے۔");
        m.put("aa.register.error_not_permitted",
              "آپ کو اس شکایت کے خلاف اپیل درج کرنے کی اجازت نہیں ہے۔");
        m.put("aa.register.unresolved_no_source",
              "شکایت کے ریکارڈ سے دستیاب نہیں — براہ کرم یہ قیمت درج کریں۔");
        m.put("aa.register.unresolved_not_in_master",
              "ماسٹر ڈیٹا میں نہیں ملا — براہ کرم یہ قیمت درج کریں۔");
        m.put("aa.register.field_required", "یہ خانہ لازمی ہے۔");
        m.put("aa.register.yes", "ہاں");
        m.put("aa.register.no", "نہیں");
        m.put("aa.ground.atm_debit_card", "اے ٹی ایم / ڈیبٹ کارڈ");
        m.put("aa.ground.credit_card", "کریڈٹ کارڈ");
        m.put("aa.ground.internet_banking", "انٹرنیٹ بینکنگ");
        m.put("aa.ground.mobile_banking_upi", "موبائل بینکنگ / یو پی آئی");
        m.put("aa.ground.loan_advances", "قرض / پیشگی رقم");
        m.put("aa.ground.deposit_accounts", "ڈپازٹ اکاؤنٹس");
        m.put("aa.ground.pension", "پنشن");
        m.put("aa.ground.remittance_transfer", "رقم کی منتقلی");
        m.put("aa.ground.insurance", "بیمہ");
        m.put("aa.ground.others", "دیگر");
        m.put("aa.upload.error_file_too_large", "ہر فائل {{size}} ایم بی یا اس سے کم ہونی چاہیے۔");
        m.put("aa.upload.error_total_too_large", "تمام منسلکات مل کر {{total}} ایم بی یا اس سے کم ہونے چاہیے۔");
        m.put("aa.upload.error_too_many_files", "آپ زیادہ سے زیادہ {{count}} فائلیں منسلک کر سکتے ہیں۔");
        m.put("aa.register.button_retry", "دوبارہ کوشش کریں");
        m.put("aa.register.appellant_city", "شہر");
        m.put("aa.register.appellant_country", "ملک");
        m.put("aa.register.appellant_pincode", "پن کوڈ");
        m.put("aa.register.appellant_district", "ضلع");
        m.put("aa.register.appellant_state", "ریاست");
        m.put("aa.register.appellant_address1", "پتہ سطر 1");
        m.put("aa.register.appellant_address2", "پتہ سطر 2");
        m.put("aa.register.entity_name", "ادارے کا نام");
        m.put("aa.register.entity_region", "ادارے کا علاقہ");
        m.put("aa.register.entity_category", "ادارے کی قسم");
        m.put("aa.register.entity_branch", "شاخ");
        m.put("aa.register.bsr_ifsc_code", "بی ایس آر / آئی ایف ایس سی کوڈ");
        m.put("aa.register.account_number", "اکاؤنٹ نمبر");
        m.put("aa.register.card_number", "کارڈ نمبر");
        m.put("aa.register.nodal_officer_name", "نوڈل افسر");
        m.put("aa.register.reason_for_delay", "تاخیر کی وجہ");
        m.put("aa.register.declarations_heading", "اعلانات");
        m.put("aa.register.parent_complaint", "بنیادی شکایت");
        m.put("aa.register.closure_clause", "اختتامی شق");
        m.put("aa.register.filed_by_complainant", "شکایت کنندہ");
        m.put("aa.register.filed_by_entity", "ریگولیٹڈ ادارہ");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "ಮೂಲ ದೂರನ್ನು ಹುಡುಕಿ");
        m.put("aa.search.complaint_number", "ದೂರು ಸಂಖ್ಯೆ");
        m.put("aa.search.appellant_name", "ಮೇಲ್ಮನವಿದಾರರ ಹೆಸರು");
        m.put("aa.search.appellant_mobile", "ಮೊಬೈಲ್ ಸಂಖ್ಯೆ");
        m.put("aa.search.appellant_email", "ಇಮೇಲ್ ವಿಳಾಸ");
        m.put("aa.search.rbio_office", "ಆರ್‌ಬಿಐಒ ಕಚೇರಿ");
        m.put("aa.search.closure_clause", "ಮುಕ್ತಾಯ ಷರತ್ತು");
        m.put("aa.search.category", "ವರ್ಗ");
        m.put("aa.search.ground_of_complaint", "ದೂರಿನ ಆಧಾರ");
        m.put("aa.search.button_search", "ಹುಡುಕಿ");
        m.put("aa.search.button_reset", "ಮರುಹೊಂದಿಸಿ");
        m.put("aa.search.no_results", "ನಿಮ್ಮ ಹುಡುಕಾಟಕ್ಕೆ ಯಾವುದೇ ದೂರು ಹೊಂದಿಕೆಯಾಗಿಲ್ಲ.");
        m.put("aa.search.error_no_filter", "ಕನಿಷ್ಠ ಒಂದು ಹುಡುಕಾಟ ಮಾನದಂಡವನ್ನು ನಮೂದಿಸಿ.");
        m.put("aa.search.error_term_too_short", "ಹೆಸರಿನಿಂದ ಹುಡುಕಲು ಕನಿಷ್ಠ ಎರಡು ಅಕ್ಷರಗಳನ್ನು ನಮೂದಿಸಿ.");
        m.put("aa.search.error_failed", "ಹುಡುಕಾಟ ಪೂರ್ಣಗೊಳ್ಳಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("aa.search.loading", "ಹುಡುಕುತ್ತಿದೆ…");
        m.put("aa.search.results_count", "{{count}} ದೂರುಗಳು ಕಂಡುಬಂದಿವೆ");
        m.put("aa.search.col_status", "ಸ್ಥಿತಿ");
        m.put("aa.search.col_office", "ಕಚೇರಿ");
        m.put("aa.search.col_closed_on", "ಮುಚ್ಚಿದ ದಿನಾಂಕ");
        m.put("aa.search.select_option_all", "ಎಲ್ಲ");
        m.put("aa.search.no_office_recorded", "ಯಾವುದೇ ಕಚೇರಿ ದಾಖಲಾಗಿಲ್ಲ");
        m.put("aa.register.heading", "ಮೇಲ್ಮನವಿ / ಪ್ರಾತಿನಿಧ್ಯವನ್ನು ನೋಂದಾಯಿಸಿ");
        m.put("aa.register.milestone_heading", "ನೋಂದಣಿ ವಿವರಗಳು");
        m.put("aa.register.appeal_filed_by", "ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಿದವರು");
        m.put("aa.register.source_of_appeal", "ಮೇಲ್ಮನವಿಯ ಮೂಲ");
        m.put("aa.register.mode_of_receipt", "ಸ್ವೀಕೃತಿ ವಿಧಾನ");
        m.put("aa.register.appeal_ground", "ಮೇಲ್ಮನವಿಯ ಆಧಾರ");
        m.put("aa.register.relief_sought", "ಕೋರಿದ ಪರಿಹಾರ");
        m.put("aa.register.complainant_heading", "ದೂರುದಾರರ ವಿವರಗಳು");
        m.put("aa.register.entity_heading", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ವಿವರಗಳು");
        m.put("aa.register.is_complainant_advocate", "ದೂರುದಾರರು ವಕೀಲರೇ?");
        m.put("aa.register.has_related_court_trial", "ಸಂಬಂಧಿತ ನ್ಯಾಯಾಲಯ ವಿಚಾರಣೆಗಳಿವೆಯೇ?");
        m.put("aa.register.prompt_log_legal_case", "ದಯವಿಟ್ಟು ಈ ಪ್ರಕರಣವನ್ನು ಕಾನೂನು ಪ್ರಕರಣಗಳ ಮಾಡ್ಯೂಲ್‌ನಲ್ಲಿ ದಾಖಲಿಸಿ.");
        m.put("aa.register.ed_approval_given", "ಇಡಿ / ಸಮಾನ ಶ್ರೇಣಿಯ ಅನುಮೋದನೆ ಪಡೆಯಲಾಗಿದೆಯೇ?");
        m.put("aa.register.ed_approval_date", "ಅನುಮೋದನೆಯ ದಿನಾಂಕ");
        m.put("aa.register.ed_approval_comments", "ಅನುಮೋದನೆ ಟಿಪ್ಪಣಿಗಳು");
        m.put("aa.register.ed_approval_document", "ಅನುಮೋದನೆ ದಾಖಲೆ");
        m.put("aa.register.button_save_proceed", "ಉಳಿಸಿ ಮತ್ತು ಮುಂದುವರಿಯಿರಿ");
        m.put("aa.register.create_appeal", "ಮೇಲ್ಮನವಿ ರಚಿಸಿ");
        m.put("aa.register.create_representation", "ಪ್ರಾತಿನಿಧ್ಯ ರಚಿಸಿ");
        m.put("aa.register.success", "ಮೇಲ್ಮನವಿ ನೋಂದಾಯಿಸಲಾಗಿದೆ.");
        m.put("aa.register.error_mandatory_incomplete", "ಮುಂದುವರಿಯುವ ಮೊದಲು ಎಲ್ಲ ಕಡ್ಡಾಯ ಕ್ಷೇತ್ರಗಳನ್ನು ಪೂರ್ಣಗೊಳಿಸಿ.");
        m.put("aa.register.error_parent_not_appealable",
              "ಮುಚ್ಚಿದ ಅಥವಾ ಮರುಪ್ರಾರಂಭಿಸಿದ ದೂರಿನ ಮೇಲೆ ಮಾತ್ರ ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಬಹುದು.");
        m.put("aa.register.error_not_permitted",
              "ಈ ದೂರಿನ ವಿರುದ್ಧ ಮೇಲ್ಮನವಿ ನೋಂದಾಯಿಸಲು ನಿಮಗೆ ಅನುಮತಿ ಇಲ್ಲ.");
        m.put("aa.register.unresolved_no_source",
              "ದೂರಿನ ದಾಖಲೆಯಿಂದ ಲಭ್ಯವಿಲ್ಲ — ದಯವಿಟ್ಟು ಈ ಮೌಲ್ಯವನ್ನು ನಮೂದಿಸಿ.");
        m.put("aa.register.unresolved_not_in_master",
              "ಮಾಸ್ಟರ್ ಡೇಟಾದಲ್ಲಿ ಕಂಡುಬಂದಿಲ್ಲ — ದಯವಿಟ್ಟು ಈ ಮೌಲ್ಯವನ್ನು ನಮೂದಿಸಿ.");
        m.put("aa.register.field_required", "ಈ ಕ್ಷೇತ್ರ ಅಗತ್ಯವಿದೆ.");
        m.put("aa.register.yes", "ಹೌದು");
        m.put("aa.register.no", "ಇಲ್ಲ");
        m.put("aa.ground.atm_debit_card", "ಎಟಿಎಂ / ಡೆಬಿಟ್ ಕಾರ್ಡ್");
        m.put("aa.ground.credit_card", "ಕ್ರೆಡಿಟ್ ಕಾರ್ಡ್");
        m.put("aa.ground.internet_banking", "ಇಂಟರ್ನೆಟ್ ಬ್ಯಾಂಕಿಂಗ್");
        m.put("aa.ground.mobile_banking_upi", "ಮೊಬೈಲ್ ಬ್ಯಾಂಕಿಂಗ್ / ಯುಪಿಐ");
        m.put("aa.ground.loan_advances", "ಸಾಲ / ಮುಂಗಡ");
        m.put("aa.ground.deposit_accounts", "ಠೇವಣಿ ಖಾತೆಗಳು");
        m.put("aa.ground.pension", "ಪಿಂಚಣಿ");
        m.put("aa.ground.remittance_transfer", "ಹಣ ರವಾನೆ / ವರ್ಗಾವಣೆ");
        m.put("aa.ground.insurance", "ವಿಮೆ");
        m.put("aa.ground.others", "ಇತರೆ");
        m.put("aa.upload.error_file_too_large", "ಪ್ರತಿ ಕಡತವು {{size}} ಎಂಬಿ ಅಥವಾ ಕಡಿಮೆ ಇರಬೇಕು.");
        m.put("aa.upload.error_total_too_large", "ಎಲ್ಲ ಲಗತ್ತುಗಳು ಒಟ್ಟಾಗಿ {{total}} ಎಂಬಿ ಅಥವಾ ಕಡಿಮೆ ಇರಬೇಕು.");
        m.put("aa.upload.error_too_many_files", "ನೀವು ಗರಿಷ್ಠ {{count}} ಕಡತಗಳನ್ನು ಲಗತ್ತಿಸಬಹುದು.");
        m.put("aa.register.button_retry", "ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ");
        m.put("aa.register.appellant_city", "ನಗರ");
        m.put("aa.register.appellant_country", "ದೇಶ");
        m.put("aa.register.appellant_pincode", "ಪಿನ್ ಕೋಡ್");
        m.put("aa.register.appellant_district", "ಜಿಲ್ಲೆ");
        m.put("aa.register.appellant_state", "ರಾಜ್ಯ");
        m.put("aa.register.appellant_address1", "ವಿಳಾಸ ಸಾಲು 1");
        m.put("aa.register.appellant_address2", "ವಿಳಾಸ ಸಾಲು 2");
        m.put("aa.register.entity_name", "ಸಂಸ್ಥೆಯ ಹೆಸರು");
        m.put("aa.register.entity_region", "ಸಂಸ್ಥೆ ಪ್ರದೇಶ");
        m.put("aa.register.entity_category", "ಸಂಸ್ಥೆ ವರ್ಗ");
        m.put("aa.register.entity_branch", "ಶಾಖೆ");
        m.put("aa.register.bsr_ifsc_code", "ಬಿಎಸ್ಆರ್ / ಐಎಫ್ಎಸ್‌ಸಿ ಕೋಡ್");
        m.put("aa.register.account_number", "ಖಾತೆ ಸಂಖ್ಯೆ");
        m.put("aa.register.card_number", "ಕಾರ್ಡ್ ಸಂಖ್ಯೆ");
        m.put("aa.register.nodal_officer_name", "ನೋಡಲ್ ಅಧಿಕಾರಿ");
        m.put("aa.register.reason_for_delay", "ವಿಳಂಬದ ಕಾರಣ");
        m.put("aa.register.declarations_heading", "ಘೋಷಣೆಗಳು");
        m.put("aa.register.parent_complaint", "ಮೂಲ ದೂರು");
        m.put("aa.register.closure_clause", "ಮುಕ್ತಾಯ ಷರತ್ತು");
        m.put("aa.register.filed_by_complainant", "ದೂರುದಾರ");
        m.put("aa.register.filed_by_entity", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.search.heading", "മൂല പരാതി തിരയുക");
        m.put("aa.search.complaint_number", "പരാതി നമ്പർ");
        m.put("aa.search.appellant_name", "അപ്പീൽ നൽകിയവരുടെ പേര്");
        m.put("aa.search.appellant_mobile", "മൊബൈൽ നമ്പർ");
        m.put("aa.search.appellant_email", "ഇമെയിൽ വിലാസം");
        m.put("aa.search.rbio_office", "ആർബിഐഒ ഓഫീസ്");
        m.put("aa.search.closure_clause", "അവസാനിപ്പിക്കൽ വ്യവസ്ഥ");
        m.put("aa.search.category", "വിഭാഗം");
        m.put("aa.search.ground_of_complaint", "പരാതിയുടെ അടിസ്ഥാനം");
        m.put("aa.search.button_search", "തിരയുക");
        m.put("aa.search.button_reset", "പുനഃക്രമീകരിക്കുക");
        m.put("aa.search.no_results", "നിങ്ങളുടെ തിരയലിനോട് ഒരു പരാതിയും പൊരുത്തപ്പെടുന്നില്ല.");
        m.put("aa.search.error_no_filter", "കുറഞ്ഞത് ഒരു തിരയൽ മാനദണ്ഡം നൽകുക.");
        m.put("aa.search.error_term_too_short", "പേര് ഉപയോഗിച്ച് തിരയാൻ കുറഞ്ഞത് രണ്ട് അക്ഷരങ്ങൾ നൽകുക.");
        m.put("aa.search.error_failed", "തിരയൽ പൂർത്തിയാക്കാനായില്ല. വീണ്ടും ശ്രമിക്കുക.");
        m.put("aa.search.loading", "തിരയുന്നു…");
        m.put("aa.search.results_count", "{{count}} പരാതികൾ കണ്ടെത്തി");
        m.put("aa.search.col_status", "നില");
        m.put("aa.search.col_office", "ഓഫീസ്");
        m.put("aa.search.col_closed_on", "അവസാനിപ്പിച്ച തീയതി");
        m.put("aa.search.select_option_all", "എല്ലാം");
        m.put("aa.search.no_office_recorded", "ഓഫീസ് രേഖപ്പെടുത്തിയിട്ടില്ല");
        m.put("aa.register.heading", "അപ്പീൽ / നിവേദനം രജിസ്റ്റർ ചെയ്യുക");
        m.put("aa.register.milestone_heading", "രജിസ്ട്രേഷൻ വിശദാംശങ്ങൾ");
        m.put("aa.register.appeal_filed_by", "അപ്പീൽ സമർപ്പിച്ചത്");
        m.put("aa.register.source_of_appeal", "അപ്പീലിന്റെ ഉറവിടം");
        m.put("aa.register.mode_of_receipt", "സ്വീകരിച്ച രീതി");
        m.put("aa.register.appeal_ground", "അപ്പീലിന്റെ അടിസ്ഥാനം");
        m.put("aa.register.relief_sought", "ആവശ്യപ്പെട്ട ആശ്വാസം");
        m.put("aa.register.complainant_heading", "പരാതിക്കാരന്റെ വിശദാംശങ്ങൾ");
        m.put("aa.register.entity_heading", "നിയന്ത്രിത സ്ഥാപനത്തിന്റെ വിശദാംശങ്ങൾ");
        m.put("aa.register.is_complainant_advocate", "പരാതിക്കാരൻ ഒരു അഭിഭാഷകനാണോ?");
        m.put("aa.register.has_related_court_trial", "ബന്ധപ്പെട്ട കോടതി വിചാരണകൾ ഉണ്ടോ?");
        m.put("aa.register.prompt_log_legal_case", "ഈ കേസ് നിയമ കേസുകളുടെ മോഡ്യൂളിൽ രേഖപ്പെടുത്തുക.");
        m.put("aa.register.ed_approval_given", "ഇഡി / തുല്യ പദവിയുടെ അനുമതി ലഭിച്ചോ?");
        m.put("aa.register.ed_approval_date", "അനുമതിയുടെ തീയതി");
        m.put("aa.register.ed_approval_comments", "അനുമതി അഭിപ്രായങ്ങൾ");
        m.put("aa.register.ed_approval_document", "അനുമതി രേഖ");
        m.put("aa.register.button_save_proceed", "സംരക്ഷിച്ച് തുടരുക");
        m.put("aa.register.create_appeal", "അപ്പീൽ സൃഷ്ടിക്കുക");
        m.put("aa.register.create_representation", "നിവേദനം സൃഷ്ടിക്കുക");
        m.put("aa.register.success", "അപ്പീൽ രജിസ്റ്റർ ചെയ്തു.");
        m.put("aa.register.error_mandatory_incomplete", "തുടരുന്നതിന് മുമ്പ് എല്ലാ നിർബന്ധിത ഫീൽഡുകളും പൂർത്തിയാക്കുക.");
        m.put("aa.register.error_parent_not_appealable",
              "അവസാനിപ്പിച്ചതോ വീണ്ടും തുറന്നതോ ആയ പരാതിയിൽ മാത്രമേ അപ്പീൽ നൽകാനാകും.");
        m.put("aa.register.error_not_permitted",
              "ഈ പരാതിക്കെതിരെ അപ്പീൽ രജിസ്റ്റർ ചെയ്യാൻ നിങ്ങൾക്ക് അനുമതിയില്ല.");
        m.put("aa.register.unresolved_no_source",
              "പരാതി രേഖയിൽ നിന്ന് ലഭ്യമല്ല — ഈ മൂല്യം നൽകുക.");
        m.put("aa.register.unresolved_not_in_master",
              "മാസ്റ്റർ ഡാറ്റയിൽ കണ്ടെത്തിയില്ല — ഈ മൂല്യം നൽകുക.");
        m.put("aa.register.field_required", "ഈ ഫീൽഡ് ആവശ്യമാണ്.");
        m.put("aa.register.yes", "അതെ");
        m.put("aa.register.no", "അല്ല");
        m.put("aa.ground.atm_debit_card", "എടിഎം / ഡെബിറ്റ് കാർഡ്");
        m.put("aa.ground.credit_card", "ക്രെഡിറ്റ് കാർഡ്");
        m.put("aa.ground.internet_banking", "ഇന്റർനെറ്റ് ബാങ്കിംഗ്");
        m.put("aa.ground.mobile_banking_upi", "മൊബൈൽ ബാങ്കിംഗ് / യുപിഐ");
        m.put("aa.ground.loan_advances", "വായ്പ / അഡ്വാൻസ്");
        m.put("aa.ground.deposit_accounts", "നിക്ഷേപ അക്കൗണ്ടുകൾ");
        m.put("aa.ground.pension", "പെൻഷൻ");
        m.put("aa.ground.remittance_transfer", "പണമടയ്ക്കൽ / കൈമാറ്റം");
        m.put("aa.ground.insurance", "ഇൻഷുറൻസ്");
        m.put("aa.ground.others", "മറ്റുള്ളവ");
        m.put("aa.upload.error_file_too_large", "ഓരോ ഫയലും {{size}} എംബി അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.");
        m.put("aa.upload.error_total_too_large", "എല്ലാ അറ്റാച്ച്‌മെന്റുകളും ചേർന്ന് {{total}} എംബി അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.");
        m.put("aa.upload.error_too_many_files", "നിങ്ങൾക്ക് പരമാവധി {{count}} ഫയലുകൾ അറ്റാച്ച് ചെയ്യാം.");
        m.put("aa.register.button_retry", "വീണ്ടും ശ്രമിക്കുക");
        m.put("aa.register.appellant_city", "നഗരം");
        m.put("aa.register.appellant_country", "രാജ്യം");
        m.put("aa.register.appellant_pincode", "പിൻ കോഡ്");
        m.put("aa.register.appellant_district", "ജില്ല");
        m.put("aa.register.appellant_state", "സംസ്ഥാനം");
        m.put("aa.register.appellant_address1", "വിലാസ വരി 1");
        m.put("aa.register.appellant_address2", "വിലാസ വരി 2");
        m.put("aa.register.entity_name", "സ്ഥാപനത്തിന്റെ പേര്");
        m.put("aa.register.entity_region", "സ്ഥാപന മേഖല");
        m.put("aa.register.entity_category", "സ്ഥാപന വിഭാഗം");
        m.put("aa.register.entity_branch", "ശാഖ");
        m.put("aa.register.bsr_ifsc_code", "ബിഎസ്ആർ / ഐഎഫ്എസ്‌സി കോഡ്");
        m.put("aa.register.account_number", "അക്കൗണ്ട് നമ്പർ");
        m.put("aa.register.card_number", "കാർഡ് നമ്പർ");
        m.put("aa.register.nodal_officer_name", "നോഡൽ ഓഫീസർ");
        m.put("aa.register.reason_for_delay", "കാലതാമസത്തിന്റെ കാരണം");
        m.put("aa.register.declarations_heading", "പ്രഖ്യാപനങ്ങൾ");
        m.put("aa.register.parent_complaint", "മൂല പരാതി");
        m.put("aa.register.closure_clause", "അവസാനിപ്പിക്കൽ വ്യവസ്ഥ");
        m.put("aa.register.filed_by_complainant", "പരാതിക്കാരൻ");
        m.put("aa.register.filed_by_entity", "നിയന്ത്രിത സ്ഥാപനം");
        return m;
    }

    private void seed(String code, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(MODULE);
        key.setDefaultValue(defaultValue);
        keyRepo.save(key);
    }

    private void seedLocale(String locale, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
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
