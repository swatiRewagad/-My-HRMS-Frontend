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
 * Translations for the S3 ladder, send-backs, additional entities, legal case and reopen.
 *
 * <p>A SEPARATE seeder at its own {@code @Order}, per the convention stated at
 * {@code AaRegisterTranslationSeeder}: a shared seeder would be a guaranteed merge conflict in a file
 * where a conflict silently costs a locale.
 *
 * <p><b>Every key is namespaced.</b> Translation keys are idempotent by {@code existsByCode}, so if two
 * sessions choose the same code the first text silently wins and the second never appears. All keys here
 * live under {@code rbio.ladder.*}, {@code rbio.entity.*}, {@code rbio.legal.*} or {@code rbio.reopen.*}.
 *
 * <p><b>Insert-if-absent.</b> Correcting a default in this file does NOT fix rows already in the
 * database — a text correction needs a code-scoped UPDATE in both migration directories, scoped BY KEY
 * CODE and never by an English phrase, because the localized rows are in native scripts and an English
 * substring matches none of them.
 */
@Component
@Order(33)
public class RbioLadderTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "rbio";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public RbioLadderTranslationSeeder(TranslationKeyRepository keyRepo,
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

        // ── Ladder actions ──
        m.put("rbio.ladder.action.submit_for_review", "Send to Reviewer");
        m.put("rbio.ladder.action.forward_deputy", "Send to Deputy Ombudsman");
        m.put("rbio.ladder.action.forward_ombudsman", "Send to Ombudsman");
        m.put("rbio.ladder.action.send_back_do", "Send Back to Dealing Official");
        m.put("rbio.ladder.action.send_back_reviewer", "Send Back to Reviewer");
        m.put("rbio.ladder.action.send_back_deputy", "Send Back to Deputy Ombudsman");

        // ── Assignment mode (UST477, 481, 514, 515, 485) ──
        m.put("rbio.ladder.assignment.mode", "Assignment");
        m.put("rbio.ladder.assignment.automatic", "Automatic (Round Robin)");
        m.put("rbio.ladder.assignment.manual", "Manual");
        m.put("rbio.ladder.assignment.select_officer", "Select an officer");
        m.put("rbio.ladder.assignment.auto_ombudsman", "The office Ombudsman is assigned automatically");

        // ── Send-back outcomes ──
        m.put("rbio.ladder.sendback.auto_previous", "Returned to the previous officer");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "The previous officer is no longer active. Select an officer to send this complaint back to.");
        m.put("rbio.ladder.error.no_previous_holder",
                "No previous officer held this complaint at that level. Select an officer to send it back to.");
        m.put("rbio.ladder.error.comment_required", "A comment is required before saving.");
        m.put("rbio.ladder.error.mandatory_fields", "Complete all mandatory fields before submitting.");
        m.put("rbio.ladder.error.field_required", "This field is required.");

        // ── Decisions ──
        m.put("rbio.ladder.decision.maintainable", "Maintainable");
        m.put("rbio.ladder.decision.non_maintainable", "Non-Maintainable");
        m.put("rbio.ladder.decision.facilitation", "Facilitation");
        m.put("rbio.ladder.decision.rejection", "Rejection");
        m.put("rbio.ladder.decision.settled", "Settled");
        m.put("rbio.ladder.decision.withdrawn", "Withdrawn");
        m.put("rbio.ladder.decision.not_a_complaint", "Not a Complaint");
        m.put("rbio.ladder.decision.advisory_complied", "Advisory Complied");
        m.put("rbio.ladder.error.decision_required", "Select a decision before saving.");
        m.put("rbio.ladder.error.maintainability_required", "Record a maintainability determination before saving.");
        m.put("rbio.ladder.error.clause_required", "Select the clause under which this complaint is closed.");

        // ── Override history ──
        m.put("rbio.ladder.override.heading", "Action & Clause Override History");
        m.put("rbio.ladder.override.field", "Field");
        m.put("rbio.ladder.override.previous_value", "Previous Value");
        m.put("rbio.ladder.override.new_value", "New Value");
        m.put("rbio.ladder.override.changed_by", "Changed By");
        m.put("rbio.ladder.override.changed_at", "Changed On");
        m.put("rbio.ladder.override.none", "No overrides have been recorded for this complaint.");

        // ── Additional entities (UST487-495) ──
        m.put("rbio.entity.heading", "Additional Entities");
        m.put("rbio.entity.add", "Add Entity");
        m.put("rbio.entity.name", "Entity Name");
        m.put("rbio.entity.branch", "Branch");
        m.put("rbio.entity.type", "Entity Type");
        m.put("rbio.entity.category", "Entity Category");
        m.put("rbio.entity.none", "No additional entities have been added.");
        m.put("rbio.entity.cap_reached",
                "The maximum of six additional entities has been reached for this complaint.");
        m.put("rbio.entity.error.duplicate", "This entity is already recorded on this complaint.");
        m.put("rbio.entity.remaining", "You may add {{count}} more.");

        // ── Legal case (UST553) ──
        m.put("rbio.legal.heading", "Legal Case");
        m.put("rbio.legal.case_number", "Case Number");
        m.put("rbio.legal.court_name", "Court Name");
        m.put("rbio.legal.case_status", "Case Status");
        m.put("rbio.legal.filing_date", "Filing Date");
        m.put("rbio.legal.next_hearing", "Next Hearing Date");
        m.put("rbio.legal.none", "No legal case has been recorded for this complaint.");
        m.put("rbio.legal.error.hearing_before_filing",
                "The next hearing date cannot fall before the filing date.");

        // ── Reopen (UST550-554) ──
        m.put("rbio.reopen.heading", "Reopen Complaint");
        m.put("rbio.reopen.reason", "Reason for Reopening");
        m.put("rbio.reopen.reason.appellate_authority", "Appellate Authority");
        m.put("rbio.reopen.reason.court_order", "Court Order");
        m.put("rbio.reopen.reason.correction_required", "Correction Required");
        m.put("rbio.reopen.justification", "Justification");
        m.put("rbio.reopen.error.reason_required", "Select a reason for reopening.");
        m.put("rbio.reopen.error.justification_required", "A justification is required to reopen this complaint.");
        m.put("rbio.reopen.error.not_permitted", "Only the Ombudsman may reopen a closed complaint.");
        m.put("rbio.reopen.assigned_original", "Reopened and returned to the original dealing official.");

        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "समीक्षक को भेजें");
        m.put("rbio.ladder.action.forward_deputy", "उप लोकपाल को भेजें");
        m.put("rbio.ladder.action.forward_ombudsman", "लोकपाल को भेजें");
        m.put("rbio.ladder.action.send_back_do", "कार्यकारी अधिकारी को वापस भेजें");
        m.put("rbio.ladder.action.send_back_reviewer", "समीक्षक को वापस भेजें");
        m.put("rbio.ladder.action.send_back_deputy", "उप लोकपाल को वापस भेजें");
        m.put("rbio.ladder.assignment.mode", "आवंटन");
        m.put("rbio.ladder.assignment.automatic", "स्वचालित (राउंड रॉबिन)");
        m.put("rbio.ladder.assignment.manual", "मैनुअल");
        m.put("rbio.ladder.assignment.select_officer", "अधिकारी चुनें");
        m.put("rbio.ladder.assignment.auto_ombudsman", "कार्यालय के लोकपाल को स्वतः आवंटित किया जाता है");
        m.put("rbio.ladder.sendback.auto_previous", "पिछले अधिकारी को वापस भेजा गया");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "पिछले अधिकारी अब सक्रिय नहीं हैं। इस शिकायत को वापस भेजने के लिए अधिकारी चुनें।");
        m.put("rbio.ladder.error.no_previous_holder",
                "उस स्तर पर किसी पिछले अधिकारी ने यह शिकायत नहीं संभाली। वापस भेजने के लिए अधिकारी चुनें।");
        m.put("rbio.ladder.error.comment_required", "सहेजने से पहले टिप्पणी आवश्यक है।");
        m.put("rbio.ladder.error.mandatory_fields", "जमा करने से पहले सभी अनिवार्य फ़ील्ड भरें।");
        m.put("rbio.ladder.error.field_required", "यह फ़ील्ड आवश्यक है।");
        m.put("rbio.ladder.decision.maintainable", "पोषणीय");
        m.put("rbio.ladder.decision.non_maintainable", "अपोषणीय");
        m.put("rbio.ladder.decision.facilitation", "सुविधा प्रदान");
        m.put("rbio.ladder.decision.rejection", "अस्वीकृति");
        m.put("rbio.ladder.decision.settled", "निपटाया गया");
        m.put("rbio.ladder.decision.withdrawn", "वापस लिया गया");
        m.put("rbio.ladder.decision.not_a_complaint", "शिकायत नहीं है");
        m.put("rbio.ladder.decision.advisory_complied", "सलाह का अनुपालन");
        m.put("rbio.ladder.error.decision_required", "सहेजने से पहले निर्णय चुनें।");
        m.put("rbio.ladder.error.maintainability_required", "सहेजने से पहले पोषणीयता निर्धारण दर्ज करें।");
        m.put("rbio.ladder.error.clause_required", "वह खंड चुनें जिसके अंतर्गत यह शिकायत बंद की गई है।");
        m.put("rbio.ladder.override.heading", "कार्रवाई और खंड अधिभावी इतिहास");
        m.put("rbio.ladder.override.field", "फ़ील्ड");
        m.put("rbio.ladder.override.previous_value", "पिछला मान");
        m.put("rbio.ladder.override.new_value", "नया मान");
        m.put("rbio.ladder.override.changed_by", "परिवर्तनकर्ता");
        m.put("rbio.ladder.override.changed_at", "परिवर्तन तिथि");
        m.put("rbio.ladder.override.none", "इस शिकायत के लिए कोई अधिभावी दर्ज नहीं है।");
        m.put("rbio.entity.heading", "अतिरिक्त संस्थाएँ");
        m.put("rbio.entity.add", "संस्था जोड़ें");
        m.put("rbio.entity.name", "संस्था का नाम");
        m.put("rbio.entity.branch", "शाखा");
        m.put("rbio.entity.type", "संस्था का प्रकार");
        m.put("rbio.entity.category", "संस्था की श्रेणी");
        m.put("rbio.entity.none", "कोई अतिरिक्त संस्था नहीं जोड़ी गई है।");
        m.put("rbio.entity.cap_reached", "इस शिकायत के लिए छह अतिरिक्त संस्थाओं की अधिकतम सीमा पूरी हो गई है।");
        m.put("rbio.entity.error.duplicate", "यह संस्था इस शिकायत में पहले से दर्ज है।");
        m.put("rbio.entity.remaining", "आप {{count}} और जोड़ सकते हैं।");
        m.put("rbio.legal.heading", "कानूनी मामला");
        m.put("rbio.legal.case_number", "मामला संख्या");
        m.put("rbio.legal.court_name", "न्यायालय का नाम");
        m.put("rbio.legal.case_status", "मामले की स्थिति");
        m.put("rbio.legal.filing_date", "दाखिल करने की तिथि");
        m.put("rbio.legal.next_hearing", "अगली सुनवाई की तिथि");
        m.put("rbio.legal.none", "इस शिकायत के लिए कोई कानूनी मामला दर्ज नहीं है।");
        m.put("rbio.legal.error.hearing_before_filing", "अगली सुनवाई की तिथि दाखिल करने की तिथि से पहले नहीं हो सकती।");
        m.put("rbio.reopen.heading", "शिकायत पुनः खोलें");
        m.put("rbio.reopen.reason", "पुनः खोलने का कारण");
        m.put("rbio.reopen.reason.appellate_authority", "अपीलीय प्राधिकरण");
        m.put("rbio.reopen.reason.court_order", "न्यायालय का आदेश");
        m.put("rbio.reopen.reason.correction_required", "सुधार आवश्यक");
        m.put("rbio.reopen.justification", "औचित्य");
        m.put("rbio.reopen.error.reason_required", "पुनः खोलने का कारण चुनें।");
        m.put("rbio.reopen.error.justification_required", "इस शिकायत को पुनः खोलने के लिए औचित्य आवश्यक है।");
        m.put("rbio.reopen.error.not_permitted", "केवल लोकपाल ही बंद शिकायत को पुनः खोल सकते हैं।");
        m.put("rbio.reopen.assigned_original", "पुनः खोलकर मूल कार्यकारी अधिकारी को लौटाया गया।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "समीक्षकाकडे पाठवा");
        m.put("rbio.ladder.action.forward_deputy", "उप लोकपालाकडे पाठवा");
        m.put("rbio.ladder.action.forward_ombudsman", "लोकपालाकडे पाठवा");
        m.put("rbio.ladder.action.send_back_do", "कार्यकारी अधिकाऱ्याकडे परत पाठवा");
        m.put("rbio.ladder.action.send_back_reviewer", "समीक्षकाकडे परत पाठवा");
        m.put("rbio.ladder.action.send_back_deputy", "उप लोकपालाकडे परत पाठवा");
        m.put("rbio.ladder.assignment.mode", "नियुक्ती");
        m.put("rbio.ladder.assignment.automatic", "स्वयंचलित (राउंड रॉबिन)");
        m.put("rbio.ladder.assignment.manual", "मॅन्युअल");
        m.put("rbio.ladder.assignment.select_officer", "अधिकारी निवडा");
        m.put("rbio.ladder.assignment.auto_ombudsman", "कार्यालयाच्या लोकपालांना स्वयंचलितपणे नियुक्त केले जाते");
        m.put("rbio.ladder.sendback.auto_previous", "मागील अधिकाऱ्याकडे परत पाठवले");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "मागील अधिकारी आता सक्रिय नाहीत. ही तक्रार परत पाठवण्यासाठी अधिकारी निवडा.");
        m.put("rbio.ladder.error.no_previous_holder",
                "त्या स्तरावर कोणत्याही मागील अधिकाऱ्याने ही तक्रार हाताळली नाही. परत पाठवण्यासाठी अधिकारी निवडा.");
        m.put("rbio.ladder.error.comment_required", "जतन करण्यापूर्वी टिप्पणी आवश्यक आहे.");
        m.put("rbio.ladder.error.mandatory_fields", "सबमिट करण्यापूर्वी सर्व अनिवार्य फील्ड भरा.");
        m.put("rbio.ladder.error.field_required", "हे फील्ड आवश्यक आहे.");
        m.put("rbio.ladder.decision.maintainable", "पोषणीय");
        m.put("rbio.ladder.decision.non_maintainable", "अपोषणीय");
        m.put("rbio.ladder.decision.facilitation", "सुविधा");
        m.put("rbio.ladder.decision.rejection", "नाकारले");
        m.put("rbio.ladder.decision.settled", "निकाली काढले");
        m.put("rbio.ladder.decision.withdrawn", "मागे घेतले");
        m.put("rbio.ladder.decision.not_a_complaint", "तक्रार नाही");
        m.put("rbio.ladder.decision.advisory_complied", "सल्ल्याचे पालन");
        m.put("rbio.ladder.error.decision_required", "जतन करण्यापूर्वी निर्णय निवडा.");
        m.put("rbio.ladder.error.maintainability_required", "जतन करण्यापूर्वी पोषणीयता निर्धारण नोंदवा.");
        m.put("rbio.ladder.error.clause_required", "ही तक्रार कोणत्या कलमाखाली बंद केली आहे ते निवडा.");
        m.put("rbio.ladder.override.heading", "कृती आणि कलम अधिक्रमण इतिहास");
        m.put("rbio.ladder.override.field", "फील्ड");
        m.put("rbio.ladder.override.previous_value", "मागील मूल्य");
        m.put("rbio.ladder.override.new_value", "नवीन मूल्य");
        m.put("rbio.ladder.override.changed_by", "बदल करणारे");
        m.put("rbio.ladder.override.changed_at", "बदलाची तारीख");
        m.put("rbio.ladder.override.none", "या तक्रारीसाठी कोणतेही अधिक्रमण नोंदवलेले नाही.");
        m.put("rbio.entity.heading", "अतिरिक्त संस्था");
        m.put("rbio.entity.add", "संस्था जोडा");
        m.put("rbio.entity.name", "संस्थेचे नाव");
        m.put("rbio.entity.branch", "शाखा");
        m.put("rbio.entity.type", "संस्थेचा प्रकार");
        m.put("rbio.entity.category", "संस्थेची श्रेणी");
        m.put("rbio.entity.none", "कोणतीही अतिरिक्त संस्था जोडलेली नाही.");
        m.put("rbio.entity.cap_reached", "या तक्रारीसाठी सहा अतिरिक्त संस्थांची कमाल मर्यादा गाठली आहे.");
        m.put("rbio.entity.error.duplicate", "ही संस्था या तक्रारीत आधीच नोंदवली आहे.");
        m.put("rbio.entity.remaining", "तुम्ही अजून {{count}} जोडू शकता.");
        m.put("rbio.legal.heading", "कायदेशीर प्रकरण");
        m.put("rbio.legal.case_number", "प्रकरण क्रमांक");
        m.put("rbio.legal.court_name", "न्यायालयाचे नाव");
        m.put("rbio.legal.case_status", "प्रकरणाची स्थिती");
        m.put("rbio.legal.filing_date", "दाखल तारीख");
        m.put("rbio.legal.next_hearing", "पुढील सुनावणीची तारीख");
        m.put("rbio.legal.none", "या तक्रारीसाठी कोणतेही कायदेशीर प्रकरण नोंदवलेले नाही.");
        m.put("rbio.legal.error.hearing_before_filing", "पुढील सुनावणीची तारीख दाखल तारखेपूर्वी असू शकत नाही.");
        m.put("rbio.reopen.heading", "तक्रार पुन्हा उघडा");
        m.put("rbio.reopen.reason", "पुन्हा उघडण्याचे कारण");
        m.put("rbio.reopen.reason.appellate_authority", "अपील प्राधिकरण");
        m.put("rbio.reopen.reason.court_order", "न्यायालयाचा आदेश");
        m.put("rbio.reopen.reason.correction_required", "दुरुस्ती आवश्यक");
        m.put("rbio.reopen.justification", "समर्थन");
        m.put("rbio.reopen.error.reason_required", "पुन्हा उघडण्याचे कारण निवडा.");
        m.put("rbio.reopen.error.justification_required", "ही तक्रार पुन्हा उघडण्यासाठी समर्थन आवश्यक आहे.");
        m.put("rbio.reopen.error.not_permitted", "केवळ लोकपालच बंद तक्रार पुन्हा उघडू शकतात.");
        m.put("rbio.reopen.assigned_original", "पुन्हा उघडून मूळ कार्यकारी अधिकाऱ्याकडे परत पाठवले.");
        return m;
    }

    /**
     * Bengali. NOTE: Bengali stores digits in Bengali numerals (৬, not 6), so the entity-cap message uses
     * ৬ — a digit replacement keyed on ASCII would silently skip this locale.
     */
    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "পর্যালোচকের কাছে পাঠান");
        m.put("rbio.ladder.action.forward_deputy", "উপ ন্যায়পালের কাছে পাঠান");
        m.put("rbio.ladder.action.forward_ombudsman", "ন্যায়পালের কাছে পাঠান");
        m.put("rbio.ladder.action.send_back_do", "কার্যনির্বাহী আধিকারিকের কাছে ফেরত পাঠান");
        m.put("rbio.ladder.action.send_back_reviewer", "পর্যালোচকের কাছে ফেরত পাঠান");
        m.put("rbio.ladder.action.send_back_deputy", "উপ ন্যায়পালের কাছে ফেরত পাঠান");
        m.put("rbio.ladder.assignment.mode", "নিয়োগ");
        m.put("rbio.ladder.assignment.automatic", "স্বয়ংক্রিয় (রাউন্ড রবিন)");
        m.put("rbio.ladder.assignment.manual", "ম্যানুয়াল");
        m.put("rbio.ladder.assignment.select_officer", "আধিকারিক নির্বাচন করুন");
        m.put("rbio.ladder.assignment.auto_ombudsman", "কার্যালয়ের ন্যায়পালকে স্বয়ংক্রিয়ভাবে নিয়োগ করা হয়");
        m.put("rbio.ladder.sendback.auto_previous", "পূর্ববর্তী আধিকারিকের কাছে ফেরত পাঠানো হয়েছে");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "পূর্ববর্তী আধিকারিক আর সক্রিয় নন। এই অভিযোগ ফেরত পাঠাতে একজন আধিকারিক নির্বাচন করুন।");
        m.put("rbio.ladder.error.no_previous_holder",
                "সেই স্তরে কোনও পূর্ববর্তী আধিকারিক এই অভিযোগ পরিচালনা করেননি। ফেরত পাঠাতে একজন আধিকারিক নির্বাচন করুন।");
        m.put("rbio.ladder.error.comment_required", "সংরক্ষণের আগে একটি মন্তব্য আবশ্যক।");
        m.put("rbio.ladder.error.mandatory_fields", "জমা দেওয়ার আগে সমস্ত আবশ্যক ক্ষেত্র পূরণ করুন।");
        m.put("rbio.ladder.error.field_required", "এই ক্ষেত্রটি আবশ্যক।");
        m.put("rbio.ladder.decision.maintainable", "বিচারযোগ্য");
        m.put("rbio.ladder.decision.non_maintainable", "অবিচারযোগ্য");
        m.put("rbio.ladder.decision.facilitation", "সহায়তা");
        m.put("rbio.ladder.decision.rejection", "প্রত্যাখ্যান");
        m.put("rbio.ladder.decision.settled", "নিষ্পত্তি হয়েছে");
        m.put("rbio.ladder.decision.withdrawn", "প্রত্যাহার করা হয়েছে");
        m.put("rbio.ladder.decision.not_a_complaint", "অভিযোগ নয়");
        m.put("rbio.ladder.decision.advisory_complied", "পরামর্শ পালিত");
        m.put("rbio.ladder.error.decision_required", "সংরক্ষণের আগে একটি সিদ্ধান্ত নির্বাচন করুন।");
        m.put("rbio.ladder.error.maintainability_required", "সংরক্ষণের আগে বিচারযোগ্যতার নির্ধারণ নথিভুক্ত করুন।");
        m.put("rbio.ladder.error.clause_required", "এই অভিযোগ কোন ধারার অধীনে বন্ধ করা হয়েছে তা নির্বাচন করুন।");
        m.put("rbio.ladder.override.heading", "পদক্ষেপ ও ধারা অধিক্রমণ ইতিহাস");
        m.put("rbio.ladder.override.field", "ক্ষেত্র");
        m.put("rbio.ladder.override.previous_value", "পূর্ববর্তী মান");
        m.put("rbio.ladder.override.new_value", "নতুন মান");
        m.put("rbio.ladder.override.changed_by", "পরিবর্তনকারী");
        m.put("rbio.ladder.override.changed_at", "পরিবর্তনের তারিখ");
        m.put("rbio.ladder.override.none", "এই অভিযোগের জন্য কোনও অধিক্রমণ নথিভুক্ত নেই।");
        m.put("rbio.entity.heading", "অতিরিক্ত সংস্থা");
        m.put("rbio.entity.add", "সংস্থা যোগ করুন");
        m.put("rbio.entity.name", "সংস্থার নাম");
        m.put("rbio.entity.branch", "শাখা");
        m.put("rbio.entity.type", "সংস্থার প্রকার");
        m.put("rbio.entity.category", "সংস্থার শ্রেণি");
        m.put("rbio.entity.none", "কোনও অতিরিক্ত সংস্থা যোগ করা হয়নি।");
        m.put("rbio.entity.cap_reached", "এই অভিযোগের জন্য ৬টি অতিরিক্ত সংস্থার সর্বোচ্চ সীমা পূর্ণ হয়েছে।");
        m.put("rbio.entity.error.duplicate", "এই সংস্থা ইতিমধ্যেই এই অভিযোগে নথিভুক্ত।");
        m.put("rbio.entity.remaining", "আপনি আরও {{count}}টি যোগ করতে পারেন।");
        m.put("rbio.legal.heading", "আইনি মামলা");
        m.put("rbio.legal.case_number", "মামলা নম্বর");
        m.put("rbio.legal.court_name", "আদালতের নাম");
        m.put("rbio.legal.case_status", "মামলার অবস্থা");
        m.put("rbio.legal.filing_date", "দাখিলের তারিখ");
        m.put("rbio.legal.next_hearing", "পরবর্তী শুনানির তারিখ");
        m.put("rbio.legal.none", "এই অভিযোগের জন্য কোনও আইনি মামলা নথিভুক্ত নেই।");
        m.put("rbio.legal.error.hearing_before_filing", "পরবর্তী শুনানির তারিখ দাখিলের তারিখের আগে হতে পারে না।");
        m.put("rbio.reopen.heading", "অভিযোগ পুনরায় খুলুন");
        m.put("rbio.reopen.reason", "পুনরায় খোলার কারণ");
        m.put("rbio.reopen.reason.appellate_authority", "আপিল কর্তৃপক্ষ");
        m.put("rbio.reopen.reason.court_order", "আদালতের আদেশ");
        m.put("rbio.reopen.reason.correction_required", "সংশোধন প্রয়োজন");
        m.put("rbio.reopen.justification", "যৌক্তিকতা");
        m.put("rbio.reopen.error.reason_required", "পুনরায় খোলার কারণ নির্বাচন করুন।");
        m.put("rbio.reopen.error.justification_required", "এই অভিযোগ পুনরায় খুলতে যৌক্তিকতা আবশ্যক।");
        m.put("rbio.reopen.error.not_permitted", "কেবল ন্যায়পাল বন্ধ অভিযোগ পুনরায় খুলতে পারেন।");
        m.put("rbio.reopen.assigned_original", "পুনরায় খুলে মূল কার্যনির্বাহী আধিকারিকের কাছে ফেরত পাঠানো হয়েছে।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "సమీక్షకుడికి పంపండి");
        m.put("rbio.ladder.action.forward_deputy", "ఉప అంబుడ్స్‌మన్‌కు పంపండి");
        m.put("rbio.ladder.action.forward_ombudsman", "అంబుడ్స్‌మన్‌కు పంపండి");
        m.put("rbio.ladder.action.send_back_do", "కార్యనిర్వాహక అధికారికి తిరిగి పంపండి");
        m.put("rbio.ladder.action.send_back_reviewer", "సమీక్షకుడికి తిరిగి పంపండి");
        m.put("rbio.ladder.action.send_back_deputy", "ఉప అంబుడ్స్‌మన్‌కు తిరిగి పంపండి");
        m.put("rbio.ladder.assignment.mode", "కేటాయింపు");
        m.put("rbio.ladder.assignment.automatic", "స్వయంచాలక (రౌండ్ రాబిన్)");
        m.put("rbio.ladder.assignment.manual", "మాన్యువల్");
        m.put("rbio.ladder.assignment.select_officer", "అధికారిని ఎంచుకోండి");
        m.put("rbio.ladder.assignment.auto_ombudsman", "కార్యాలయ అంబుడ్స్‌మన్‌కు స్వయంచాలకంగా కేటాయించబడుతుంది");
        m.put("rbio.ladder.sendback.auto_previous", "మునుపటి అధికారికి తిరిగి పంపబడింది");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "మునుపటి అధికారి ఇకపై చురుకుగా లేరు. ఈ ఫిర్యాదును తిరిగి పంపడానికి ఒక అధికారిని ఎంచుకోండి.");
        m.put("rbio.ladder.error.no_previous_holder",
                "ఆ స్థాయిలో ఏ మునుపటి అధికారి ఈ ఫిర్యాదును నిర్వహించలేదు. తిరిగి పంపడానికి ఒక అధికారిని ఎంచుకోండి.");
        m.put("rbio.ladder.error.comment_required", "సేవ్ చేయడానికి ముందు వ్యాఖ్య అవసరం.");
        m.put("rbio.ladder.error.mandatory_fields", "సమర్పించడానికి ముందు అన్ని తప్పనిసరి ఫీల్డ్‌లను పూరించండి.");
        m.put("rbio.ladder.error.field_required", "ఈ ఫీల్డ్ అవసరం.");
        m.put("rbio.ladder.decision.maintainable", "విచారణ యోగ్యం");
        m.put("rbio.ladder.decision.non_maintainable", "విచారణ అయోగ్యం");
        m.put("rbio.ladder.decision.facilitation", "సులభతరం");
        m.put("rbio.ladder.decision.rejection", "తిరస్కరణ");
        m.put("rbio.ladder.decision.settled", "పరిష్కరించబడింది");
        m.put("rbio.ladder.decision.withdrawn", "ఉపసంహరించబడింది");
        m.put("rbio.ladder.decision.not_a_complaint", "ఫిర్యాదు కాదు");
        m.put("rbio.ladder.decision.advisory_complied", "సలహా పాలించబడింది");
        m.put("rbio.ladder.error.decision_required", "సేవ్ చేయడానికి ముందు ఒక నిర్ణయాన్ని ఎంచుకోండి.");
        m.put("rbio.ladder.error.maintainability_required", "సేవ్ చేయడానికి ముందు విచారణ యోగ్యత నిర్ధారణను నమోదు చేయండి.");
        m.put("rbio.ladder.error.clause_required", "ఈ ఫిర్యాదు ఏ నిబంధన కింద మూసివేయబడిందో ఎంచుకోండి.");
        m.put("rbio.ladder.override.heading", "చర్య మరియు నిబంధన అధిక్రమణ చరిత్ర");
        m.put("rbio.ladder.override.field", "ఫీల్డ్");
        m.put("rbio.ladder.override.previous_value", "మునుపటి విలువ");
        m.put("rbio.ladder.override.new_value", "కొత్త విలువ");
        m.put("rbio.ladder.override.changed_by", "మార్చినవారు");
        m.put("rbio.ladder.override.changed_at", "మార్చిన తేదీ");
        m.put("rbio.ladder.override.none", "ఈ ఫిర్యాదు కోసం ఏ అధిక్రమణ నమోదు కాలేదు.");
        m.put("rbio.entity.heading", "అదనపు సంస్థలు");
        m.put("rbio.entity.add", "సంస్థను జోడించండి");
        m.put("rbio.entity.name", "సంస్థ పేరు");
        m.put("rbio.entity.branch", "శాఖ");
        m.put("rbio.entity.type", "సంస్థ రకం");
        m.put("rbio.entity.category", "సంస్థ వర్గం");
        m.put("rbio.entity.none", "ఏ అదనపు సంస్థ జోడించబడలేదు.");
        m.put("rbio.entity.cap_reached", "ఈ ఫిర్యాదు కోసం ఆరు అదనపు సంస్థల గరిష్ఠ పరిమితి చేరుకుంది.");
        m.put("rbio.entity.error.duplicate", "ఈ సంస్థ ఇప్పటికే ఈ ఫిర్యాదులో నమోదు చేయబడింది.");
        m.put("rbio.entity.remaining", "మీరు మరో {{count}} జోడించవచ్చు.");
        m.put("rbio.legal.heading", "న్యాయ కేసు");
        m.put("rbio.legal.case_number", "కేసు సంఖ్య");
        m.put("rbio.legal.court_name", "న్యాయస్థానం పేరు");
        m.put("rbio.legal.case_status", "కేసు స్థితి");
        m.put("rbio.legal.filing_date", "దాఖలు తేదీ");
        m.put("rbio.legal.next_hearing", "తదుపరి విచారణ తేదీ");
        m.put("rbio.legal.none", "ఈ ఫిర్యాదు కోసం ఏ న్యాయ కేసు నమోదు కాలేదు.");
        m.put("rbio.legal.error.hearing_before_filing", "తదుపరి విచారణ తేదీ దాఖలు తేదీకి ముందు ఉండకూడదు.");
        m.put("rbio.reopen.heading", "ఫిర్యాదును తిరిగి తెరవండి");
        m.put("rbio.reopen.reason", "తిరిగి తెరవడానికి కారణం");
        m.put("rbio.reopen.reason.appellate_authority", "అప్పీలేట్ అధికారం");
        m.put("rbio.reopen.reason.court_order", "న్యాయస్థానం ఉత్తర్వు");
        m.put("rbio.reopen.reason.correction_required", "సవరణ అవసరం");
        m.put("rbio.reopen.justification", "సమర్థన");
        m.put("rbio.reopen.error.reason_required", "తిరిగి తెరవడానికి కారణాన్ని ఎంచుకోండి.");
        m.put("rbio.reopen.error.justification_required", "ఈ ఫిర్యాదును తిరిగి తెరవడానికి సమర్థన అవసరం.");
        m.put("rbio.reopen.error.not_permitted", "మూసివేసిన ఫిర్యాదును అంబుడ్స్‌మన్ మాత్రమే తిరిగి తెరవగలరు.");
        m.put("rbio.reopen.assigned_original", "తిరిగి తెరిచి అసలు కార్యనిర్వాహక అధికారికి పంపబడింది.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "மறுஆய்வாளருக்கு அனுப்பவும்");
        m.put("rbio.ladder.action.forward_deputy", "துணை நியாயதுரந்தரருக்கு அனுப்பவும்");
        m.put("rbio.ladder.action.forward_ombudsman", "நியாயதுரந்தரருக்கு அனுப்பவும்");
        m.put("rbio.ladder.action.send_back_do", "செயல் அதிகாரிக்குத் திரும்ப அனுப்பவும்");
        m.put("rbio.ladder.action.send_back_reviewer", "மறுஆய்வாளருக்குத் திரும்ப அனுப்பவும்");
        m.put("rbio.ladder.action.send_back_deputy", "துணை நியாயதுரந்தரருக்குத் திரும்ப அனுப்பவும்");
        m.put("rbio.ladder.assignment.mode", "ஒப்படைப்பு");
        m.put("rbio.ladder.assignment.automatic", "தானியங்கி (ரவுண்ட் ராபின்)");
        m.put("rbio.ladder.assignment.manual", "கைமுறை");
        m.put("rbio.ladder.assignment.select_officer", "அதிகாரியைத் தேர்ந்தெடுக்கவும்");
        m.put("rbio.ladder.assignment.auto_ombudsman", "அலுவலக நியாயதுரந்தரர் தானாகவே ஒப்படைக்கப்படுகிறார்");
        m.put("rbio.ladder.sendback.auto_previous", "முந்தைய அதிகாரிக்குத் திரும்ப அனுப்பப்பட்டது");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "முந்தைய அதிகாரி இப்போது செயலில் இல்லை. இந்த முறையீட்டைத் திரும்ப அனுப்ப ஒரு அதிகாரியைத் தேர்ந்தெடுக்கவும்.");
        m.put("rbio.ladder.error.no_previous_holder",
                "அந்த நிலையில் எந்த முந்தைய அதிகாரியும் இந்த முறையீட்டைக் கையாளவில்லை. திரும்ப அனுப்ப ஒரு அதிகாரியைத் தேர்ந்தெடுக்கவும்.");
        m.put("rbio.ladder.error.comment_required", "சேமிப்பதற்கு முன் ஒரு கருத்து அவசியம்.");
        m.put("rbio.ladder.error.mandatory_fields", "சமர்ப்பிக்கும் முன் அனைத்து கட்டாயப் புலங்களையும் நிரப்பவும்.");
        m.put("rbio.ladder.error.field_required", "இந்தப் புலம் அவசியம்.");
        m.put("rbio.ladder.decision.maintainable", "விசாரணைக்குத் தகுதியானது");
        m.put("rbio.ladder.decision.non_maintainable", "விசாரணைக்குத் தகுதியற்றது");
        m.put("rbio.ladder.decision.facilitation", "எளிதாக்கல்");
        m.put("rbio.ladder.decision.rejection", "நிராகரிப்பு");
        m.put("rbio.ladder.decision.settled", "தீர்க்கப்பட்டது");
        m.put("rbio.ladder.decision.withdrawn", "திரும்பப் பெறப்பட்டது");
        m.put("rbio.ladder.decision.not_a_complaint", "முறையீடு அல்ல");
        m.put("rbio.ladder.decision.advisory_complied", "ஆலோசனை பின்பற்றப்பட்டது");
        m.put("rbio.ladder.error.decision_required", "சேமிப்பதற்கு முன் ஒரு முடிவைத் தேர்ந்தெடுக்கவும்.");
        m.put("rbio.ladder.error.maintainability_required", "சேமிப்பதற்கு முன் தகுதி நிர்ணயத்தைப் பதிவு செய்யவும்.");
        m.put("rbio.ladder.error.clause_required", "இந்த முறையீடு எந்தப் பிரிவின் கீழ் மூடப்பட்டது எனத் தேர்ந்தெடுக்கவும்.");
        m.put("rbio.ladder.override.heading", "நடவடிக்கை மற்றும் பிரிவு மேலெழுதல் வரலாறு");
        m.put("rbio.ladder.override.field", "புலம்");
        m.put("rbio.ladder.override.previous_value", "முந்தைய மதிப்பு");
        m.put("rbio.ladder.override.new_value", "புதிய மதிப்பு");
        m.put("rbio.ladder.override.changed_by", "மாற்றியவர்");
        m.put("rbio.ladder.override.changed_at", "மாற்றிய தேதி");
        m.put("rbio.ladder.override.none", "இந்த முறையீட்டுக்கு எந்த மேலெழுதலும் பதிவு செய்யப்படவில்லை.");
        m.put("rbio.entity.heading", "கூடுதல் நிறுவனங்கள்");
        m.put("rbio.entity.add", "நிறுவனத்தைச் சேர்க்கவும்");
        m.put("rbio.entity.name", "நிறுவனத்தின் பெயர்");
        m.put("rbio.entity.branch", "கிளை");
        m.put("rbio.entity.type", "நிறுவன வகை");
        m.put("rbio.entity.category", "நிறுவனப் பிரிவு");
        m.put("rbio.entity.none", "கூடுதல் நிறுவனங்கள் எதுவும் சேர்க்கப்படவில்லை.");
        m.put("rbio.entity.cap_reached", "இந்த முறையீட்டுக்கு ஆறு கூடுதல் நிறுவனங்கள் என்ற அதிகபட்ச வரம்பு எட்டப்பட்டது.");
        m.put("rbio.entity.error.duplicate", "இந்த நிறுவனம் ஏற்கனவே இந்த முறையீட்டில் பதிவு செய்யப்பட்டுள்ளது.");
        m.put("rbio.entity.remaining", "நீங்கள் மேலும் {{count}} சேர்க்கலாம்.");
        m.put("rbio.legal.heading", "சட்ட வழக்கு");
        m.put("rbio.legal.case_number", "வழக்கு எண்");
        m.put("rbio.legal.court_name", "நீதிமன்றத்தின் பெயர்");
        m.put("rbio.legal.case_status", "வழக்கின் நிலை");
        m.put("rbio.legal.filing_date", "தாக்கல் தேதி");
        m.put("rbio.legal.next_hearing", "அடுத்த விசாரணை தேதி");
        m.put("rbio.legal.none", "இந்த முறையீட்டுக்கு எந்தச் சட்ட வழக்கும் பதிவு செய்யப்படவில்லை.");
        m.put("rbio.legal.error.hearing_before_filing", "அடுத்த விசாரணை தேதி தாக்கல் தேதிக்கு முன் இருக்க முடியாது.");
        m.put("rbio.reopen.heading", "முறையீட்டை மீண்டும் திறக்கவும்");
        m.put("rbio.reopen.reason", "மீண்டும் திறப்பதற்கான காரணம்");
        m.put("rbio.reopen.reason.appellate_authority", "மேல்முறையீட்டு அதிகாரம்");
        m.put("rbio.reopen.reason.court_order", "நீதிமன்ற உத்தரவு");
        m.put("rbio.reopen.reason.correction_required", "திருத்தம் தேவை");
        m.put("rbio.reopen.justification", "நியாயப்படுத்தல்");
        m.put("rbio.reopen.error.reason_required", "மீண்டும் திறப்பதற்கான காரணத்தைத் தேர்ந்தெடுக்கவும்.");
        m.put("rbio.reopen.error.justification_required", "இந்த முறையீட்டை மீண்டும் திறக்க நியாயப்படுத்தல் அவசியம்.");
        m.put("rbio.reopen.error.not_permitted", "மூடப்பட்ட முறையீட்டை நியாயதுரந்தரர் மட்டுமே மீண்டும் திறக்க முடியும்.");
        m.put("rbio.reopen.assigned_original", "மீண்டும் திறக்கப்பட்டு அசல் செயல் அதிகாரிக்குத் திருப்பி அனுப்பப்பட்டது.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "સમીક્ષકને મોકલો");
        m.put("rbio.ladder.action.forward_deputy", "ઉપ લોકપાલને મોકલો");
        m.put("rbio.ladder.action.forward_ombudsman", "લોકપાલને મોકલો");
        m.put("rbio.ladder.action.send_back_do", "કાર્યકારી અધિકારીને પરત મોકલો");
        m.put("rbio.ladder.action.send_back_reviewer", "સમીક્ષકને પરત મોકલો");
        m.put("rbio.ladder.action.send_back_deputy", "ઉપ લોકપાલને પરત મોકલો");
        m.put("rbio.ladder.assignment.mode", "સોંપણી");
        m.put("rbio.ladder.assignment.automatic", "સ્વચાલિત (રાઉન્ડ રોબિન)");
        m.put("rbio.ladder.assignment.manual", "મેન્યુઅલ");
        m.put("rbio.ladder.assignment.select_officer", "અધિકારી પસંદ કરો");
        m.put("rbio.ladder.assignment.auto_ombudsman", "કાર્યાલયના લોકપાલને સ્વચાલિત રીતે સોંપવામાં આવે છે");
        m.put("rbio.ladder.sendback.auto_previous", "અગાઉના અધિકારીને પરત મોકલ્યું");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "અગાઉના અધિકારી હવે સક્રિય નથી. આ ફરિયાદ પરત મોકલવા માટે અધિકારી પસંદ કરો.");
        m.put("rbio.ladder.error.no_previous_holder",
                "તે સ્તરે કોઈ અગાઉના અધિકારીએ આ ફરિયાદ સંભાળી નથી. પરત મોકલવા માટે અધિકારી પસંદ કરો.");
        m.put("rbio.ladder.error.comment_required", "સાચવતા પહેલાં ટિપ્પણી આવશ્યક છે.");
        m.put("rbio.ladder.error.mandatory_fields", "સબમિટ કરતા પહેલાં તમામ ફરજિયાત ક્ષેત્રો ભરો.");
        m.put("rbio.ladder.error.field_required", "આ ક્ષેત્ર આવશ્યક છે.");
        m.put("rbio.ladder.decision.maintainable", "પોષણીય");
        m.put("rbio.ladder.decision.non_maintainable", "અપોષણીય");
        m.put("rbio.ladder.decision.facilitation", "સુવિધા");
        m.put("rbio.ladder.decision.rejection", "નામંજૂર");
        m.put("rbio.ladder.decision.settled", "સમાધાન થયું");
        m.put("rbio.ladder.decision.withdrawn", "પાછું ખેંચ્યું");
        m.put("rbio.ladder.decision.not_a_complaint", "ફરિયાદ નથી");
        m.put("rbio.ladder.decision.advisory_complied", "સલાહનું પાલન");
        m.put("rbio.ladder.error.decision_required", "સાચવતા પહેલાં નિર્ણય પસંદ કરો.");
        m.put("rbio.ladder.error.maintainability_required", "સાચવતા પહેલાં પોષણીયતા નિર્ધારણ નોંધો.");
        m.put("rbio.ladder.error.clause_required", "આ ફરિયાદ કઈ કલમ હેઠળ બંધ કરવામાં આવી છે તે પસંદ કરો.");
        m.put("rbio.ladder.override.heading", "કાર્યવાહી અને કલમ ઓવરરાઇડ ઇતિહાસ");
        m.put("rbio.ladder.override.field", "ક્ષેત્ર");
        m.put("rbio.ladder.override.previous_value", "અગાઉનું મૂલ્ય");
        m.put("rbio.ladder.override.new_value", "નવું મૂલ્ય");
        m.put("rbio.ladder.override.changed_by", "બદલનાર");
        m.put("rbio.ladder.override.changed_at", "બદલવાની તારીખ");
        m.put("rbio.ladder.override.none", "આ ફરિયાદ માટે કોઈ ઓવરરાઇડ નોંધાયેલ નથી.");
        m.put("rbio.entity.heading", "વધારાની સંસ્થાઓ");
        m.put("rbio.entity.add", "સંસ્થા ઉમેરો");
        m.put("rbio.entity.name", "સંસ્થાનું નામ");
        m.put("rbio.entity.branch", "શાખા");
        m.put("rbio.entity.type", "સંસ્થાનો પ્રકાર");
        m.put("rbio.entity.category", "સંસ્થાની શ્રેણી");
        m.put("rbio.entity.none", "કોઈ વધારાની સંસ્થા ઉમેરવામાં આવી નથી.");
        m.put("rbio.entity.cap_reached", "આ ફરિયાદ માટે છ વધારાની સંસ્થાઓની મહત્તમ મર્યાદા પહોંચી ગઈ છે.");
        m.put("rbio.entity.error.duplicate", "આ સંસ્થા આ ફરિયાદમાં પહેલેથી નોંધાયેલ છે.");
        m.put("rbio.entity.remaining", "તમે વધુ {{count}} ઉમેરી શકો છો.");
        m.put("rbio.legal.heading", "કાનૂની કેસ");
        m.put("rbio.legal.case_number", "કેસ નંબર");
        m.put("rbio.legal.court_name", "અદાલતનું નામ");
        m.put("rbio.legal.case_status", "કેસની સ્થિતિ");
        m.put("rbio.legal.filing_date", "દાખલ તારીખ");
        m.put("rbio.legal.next_hearing", "આગામી સુનાવણીની તારીખ");
        m.put("rbio.legal.none", "આ ફરિયાદ માટે કોઈ કાનૂની કેસ નોંધાયેલ નથી.");
        m.put("rbio.legal.error.hearing_before_filing", "આગામી સુનાવણીની તારીખ દાખલ તારીખ પહેલાં હોઈ શકતી નથી.");
        m.put("rbio.reopen.heading", "ફરિયાદ ફરીથી ખોલો");
        m.put("rbio.reopen.reason", "ફરીથી ખોલવાનું કારણ");
        m.put("rbio.reopen.reason.appellate_authority", "અપીલ સત્તાધિકારી");
        m.put("rbio.reopen.reason.court_order", "અદાલતનો આદેશ");
        m.put("rbio.reopen.reason.correction_required", "સુધારો આવશ્યક");
        m.put("rbio.reopen.justification", "વાજબીપણું");
        m.put("rbio.reopen.error.reason_required", "ફરીથી ખોલવાનું કારણ પસંદ કરો.");
        m.put("rbio.reopen.error.justification_required", "આ ફરિયાદ ફરીથી ખોલવા માટે વાજબીપણું આવશ્યક છે.");
        m.put("rbio.reopen.error.not_permitted", "બંધ ફરિયાદ ફક્ત લોકપાલ જ ફરીથી ખોલી શકે છે.");
        m.put("rbio.reopen.assigned_original", "ફરીથી ખોલીને મૂળ કાર્યકારી અધિકારીને પરત મોકલ્યું.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "جائزہ کار کو بھیجیں");
        m.put("rbio.ladder.action.forward_deputy", "نائب محتسب کو بھیجیں");
        m.put("rbio.ladder.action.forward_ombudsman", "محتسب کو بھیجیں");
        m.put("rbio.ladder.action.send_back_do", "کارگزار افسر کو واپس بھیجیں");
        m.put("rbio.ladder.action.send_back_reviewer", "جائزہ کار کو واپس بھیجیں");
        m.put("rbio.ladder.action.send_back_deputy", "نائب محتسب کو واپس بھیجیں");
        m.put("rbio.ladder.assignment.mode", "تعین");
        m.put("rbio.ladder.assignment.automatic", "خودکار (راؤنڈ رابن)");
        m.put("rbio.ladder.assignment.manual", "دستی");
        m.put("rbio.ladder.assignment.select_officer", "افسر منتخب کریں");
        m.put("rbio.ladder.assignment.auto_ombudsman", "دفتر کے محتسب کو خودکار طور پر تعین کیا جاتا ہے");
        m.put("rbio.ladder.sendback.auto_previous", "سابقہ افسر کو واپس بھیج دیا گیا");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "سابقہ افسر اب فعال نہیں ہیں۔ اس شکایت کو واپس بھیجنے کے لیے ایک افسر منتخب کریں۔");
        m.put("rbio.ladder.error.no_previous_holder",
                "اس سطح پر کسی سابقہ افسر نے یہ شکایت نہیں سنبھالی۔ واپس بھیجنے کے لیے ایک افسر منتخب کریں۔");
        m.put("rbio.ladder.error.comment_required", "محفوظ کرنے سے پہلے تبصرہ لازمی ہے۔");
        m.put("rbio.ladder.error.mandatory_fields", "جمع کرنے سے پہلے تمام لازمی خانے مکمل کریں۔");
        m.put("rbio.ladder.error.field_required", "یہ خانہ لازمی ہے۔");
        m.put("rbio.ladder.decision.maintainable", "قابل سماعت");
        m.put("rbio.ladder.decision.non_maintainable", "ناقابل سماعت");
        m.put("rbio.ladder.decision.facilitation", "سہولت کاری");
        m.put("rbio.ladder.decision.rejection", "مسترد");
        m.put("rbio.ladder.decision.settled", "طے شدہ");
        m.put("rbio.ladder.decision.withdrawn", "واپس لے لیا گیا");
        m.put("rbio.ladder.decision.not_a_complaint", "شکایت نہیں ہے");
        m.put("rbio.ladder.decision.advisory_complied", "ہدایت پر عمل");
        m.put("rbio.ladder.error.decision_required", "محفوظ کرنے سے پہلے فیصلہ منتخب کریں۔");
        m.put("rbio.ladder.error.maintainability_required", "محفوظ کرنے سے پہلے قابل سماعت ہونے کا تعین درج کریں۔");
        m.put("rbio.ladder.error.clause_required", "منتخب کریں کہ یہ شکایت کس شق کے تحت بند کی گئی ہے۔");
        m.put("rbio.ladder.override.heading", "کارروائی اور شق کی تبدیلی کی تاریخ");
        m.put("rbio.ladder.override.field", "خانہ");
        m.put("rbio.ladder.override.previous_value", "سابقہ قدر");
        m.put("rbio.ladder.override.new_value", "نئی قدر");
        m.put("rbio.ladder.override.changed_by", "تبدیل کرنے والا");
        m.put("rbio.ladder.override.changed_at", "تبدیلی کی تاریخ");
        m.put("rbio.ladder.override.none", "اس شکایت کے لیے کوئی تبدیلی درج نہیں ہے۔");
        m.put("rbio.entity.heading", "اضافی ادارے");
        m.put("rbio.entity.add", "ادارہ شامل کریں");
        m.put("rbio.entity.name", "ادارے کا نام");
        m.put("rbio.entity.branch", "شاخ");
        m.put("rbio.entity.type", "ادارے کی قسم");
        m.put("rbio.entity.category", "ادارے کی ذیل");
        m.put("rbio.entity.none", "کوئی اضافی ادارہ شامل نہیں کیا گیا۔");
        m.put("rbio.entity.cap_reached", "اس شکایت کے لیے چھ اضافی اداروں کی زیادہ سے زیادہ حد پوری ہو گئی ہے۔");
        m.put("rbio.entity.error.duplicate", "یہ ادارہ اس شکایت میں پہلے سے درج ہے۔");
        m.put("rbio.entity.remaining", "آپ مزید {{count}} شامل کر سکتے ہیں۔");
        m.put("rbio.legal.heading", "قانونی مقدمہ");
        m.put("rbio.legal.case_number", "مقدمہ نمبر");
        m.put("rbio.legal.court_name", "عدالت کا نام");
        m.put("rbio.legal.case_status", "مقدمے کی حالت");
        m.put("rbio.legal.filing_date", "دائر کرنے کی تاریخ");
        m.put("rbio.legal.next_hearing", "اگلی سماعت کی تاریخ");
        m.put("rbio.legal.none", "اس شکایت کے لیے کوئی قانونی مقدمہ درج نہیں ہے۔");
        m.put("rbio.legal.error.hearing_before_filing", "اگلی سماعت کی تاریخ دائر کرنے کی تاریخ سے پہلے نہیں ہو سکتی۔");
        m.put("rbio.reopen.heading", "شکایت دوبارہ کھولیں");
        m.put("rbio.reopen.reason", "دوبارہ کھولنے کی وجہ");
        m.put("rbio.reopen.reason.appellate_authority", "اپیلٹ اتھارٹی");
        m.put("rbio.reopen.reason.court_order", "عدالتی حکم");
        m.put("rbio.reopen.reason.correction_required", "درستی درکار");
        m.put("rbio.reopen.justification", "جواز");
        m.put("rbio.reopen.error.reason_required", "دوبارہ کھولنے کی وجہ منتخب کریں۔");
        m.put("rbio.reopen.error.justification_required", "اس شکایت کو دوبارہ کھولنے کے لیے جواز لازمی ہے۔");
        m.put("rbio.reopen.error.not_permitted", "بند شکایت کو صرف محتسب دوبارہ کھول سکتے ہیں۔");
        m.put("rbio.reopen.assigned_original", "دوبارہ کھول کر اصل کارگزار افسر کو واپس بھیج دیا گیا۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "ಪರಿಶೀಲಕರಿಗೆ ಕಳುಹಿಸಿ");
        m.put("rbio.ladder.action.forward_deputy", "ಉಪ ಒಂಬುಡ್ಸ್‌ಮನ್‌ಗೆ ಕಳುಹಿಸಿ");
        m.put("rbio.ladder.action.forward_ombudsman", "ಒಂಬುಡ್ಸ್‌ಮನ್‌ಗೆ ಕಳುಹಿಸಿ");
        m.put("rbio.ladder.action.send_back_do", "ಕಾರ್ಯನಿರ್ವಾಹಕ ಅಧಿಕಾರಿಗೆ ಹಿಂತಿರುಗಿಸಿ");
        m.put("rbio.ladder.action.send_back_reviewer", "ಪರಿಶೀಲಕರಿಗೆ ಹಿಂತಿರುಗಿಸಿ");
        m.put("rbio.ladder.action.send_back_deputy", "ಉಪ ಒಂಬುಡ್ಸ್‌ಮನ್‌ಗೆ ಹಿಂತಿರುಗಿಸಿ");
        m.put("rbio.ladder.assignment.mode", "ನಿಯೋಜನೆ");
        m.put("rbio.ladder.assignment.automatic", "ಸ್ವಯಂಚಾಲಿತ (ರೌಂಡ್ ರಾಬಿನ್)");
        m.put("rbio.ladder.assignment.manual", "ಕೈಯಿಂದ");
        m.put("rbio.ladder.assignment.select_officer", "ಅಧಿಕಾರಿಯನ್ನು ಆಯ್ಕೆಮಾಡಿ");
        m.put("rbio.ladder.assignment.auto_ombudsman", "ಕಚೇರಿಯ ಒಂಬುಡ್ಸ್‌ಮನ್‌ಗೆ ಸ್ವಯಂಚಾಲಿತವಾಗಿ ನಿಯೋಜಿಸಲಾಗುತ್ತದೆ");
        m.put("rbio.ladder.sendback.auto_previous", "ಹಿಂದಿನ ಅಧಿಕಾರಿಗೆ ಹಿಂತಿರುಗಿಸಲಾಗಿದೆ");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "ಹಿಂದಿನ ಅಧಿಕಾರಿ ಈಗ ಸಕ್ರಿಯವಾಗಿಲ್ಲ. ಈ ದೂರನ್ನು ಹಿಂತಿರುಗಿಸಲು ಅಧಿಕಾರಿಯನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("rbio.ladder.error.no_previous_holder",
                "ಆ ಹಂತದಲ್ಲಿ ಯಾವುದೇ ಹಿಂದಿನ ಅಧಿಕಾರಿ ಈ ದೂರನ್ನು ನಿರ್ವಹಿಸಿಲ್ಲ. ಹಿಂತಿರುಗಿಸಲು ಅಧಿಕಾರಿಯನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("rbio.ladder.error.comment_required", "ಉಳಿಸುವ ಮೊದಲು ಟಿಪ್ಪಣಿ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.ladder.error.mandatory_fields", "ಸಲ್ಲಿಸುವ ಮೊದಲು ಎಲ್ಲಾ ಕಡ್ಡಾಯ ಕ್ಷೇತ್ರಗಳನ್ನು ಭರ್ತಿ ಮಾಡಿ.");
        m.put("rbio.ladder.error.field_required", "ಈ ಕ್ಷೇತ್ರ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.ladder.decision.maintainable", "ವಿಚಾರಣೆಗೆ ಅರ್ಹ");
        m.put("rbio.ladder.decision.non_maintainable", "ವಿಚಾರಣೆಗೆ ಅನರ್ಹ");
        m.put("rbio.ladder.decision.facilitation", "ಸೌಲಭ್ಯ");
        m.put("rbio.ladder.decision.rejection", "ತಿರಸ್ಕಾರ");
        m.put("rbio.ladder.decision.settled", "ಪರಿಹರಿಸಲಾಗಿದೆ");
        m.put("rbio.ladder.decision.withdrawn", "ಹಿಂಪಡೆಯಲಾಗಿದೆ");
        m.put("rbio.ladder.decision.not_a_complaint", "ದೂರು ಅಲ್ಲ");
        m.put("rbio.ladder.decision.advisory_complied", "ಸಲಹೆ ಪಾಲಿಸಲಾಗಿದೆ");
        m.put("rbio.ladder.error.decision_required", "ಉಳಿಸುವ ಮೊದಲು ನಿರ್ಧಾರವನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("rbio.ladder.error.maintainability_required", "ಉಳಿಸುವ ಮೊದಲು ಅರ್ಹತೆಯ ನಿರ್ಧಾರವನ್ನು ದಾಖಲಿಸಿ.");
        m.put("rbio.ladder.error.clause_required", "ಈ ದೂರನ್ನು ಯಾವ ಷರತ್ತಿನಡಿ ಮುಚ್ಚಲಾಗಿದೆ ಎಂಬುದನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("rbio.ladder.override.heading", "ಕ್ರಮ ಮತ್ತು ಷರತ್ತು ಅತಿಕ್ರಮಣ ಇತಿಹಾಸ");
        m.put("rbio.ladder.override.field", "ಕ್ಷೇತ್ರ");
        m.put("rbio.ladder.override.previous_value", "ಹಿಂದಿನ ಮೌಲ್ಯ");
        m.put("rbio.ladder.override.new_value", "ಹೊಸ ಮೌಲ್ಯ");
        m.put("rbio.ladder.override.changed_by", "ಬದಲಾಯಿಸಿದವರು");
        m.put("rbio.ladder.override.changed_at", "ಬದಲಾವಣೆಯ ದಿನಾಂಕ");
        m.put("rbio.ladder.override.none", "ಈ ದೂರಿಗೆ ಯಾವುದೇ ಅತಿಕ್ರಮಣ ದಾಖಲಾಗಿಲ್ಲ.");
        m.put("rbio.entity.heading", "ಹೆಚ್ಚುವರಿ ಸಂಸ್ಥೆಗಳು");
        m.put("rbio.entity.add", "ಸಂಸ್ಥೆಯನ್ನು ಸೇರಿಸಿ");
        m.put("rbio.entity.name", "ಸಂಸ್ಥೆಯ ಹೆಸರು");
        m.put("rbio.entity.branch", "ಶಾಖೆ");
        m.put("rbio.entity.type", "ಸಂಸ್ಥೆಯ ಪ್ರಕಾರ");
        m.put("rbio.entity.category", "ಸಂಸ್ಥೆಯ ವರ್ಗ");
        m.put("rbio.entity.none", "ಯಾವುದೇ ಹೆಚ್ಚುವರಿ ಸಂಸ್ಥೆ ಸೇರಿಸಲಾಗಿಲ್ಲ.");
        m.put("rbio.entity.cap_reached", "ಈ ದೂರಿಗೆ ಆರು ಹೆಚ್ಚುವರಿ ಸಂಸ್ಥೆಗಳ ಗರಿಷ್ಠ ಮಿತಿ ತಲುಪಿದೆ.");
        m.put("rbio.entity.error.duplicate", "ಈ ಸಂಸ್ಥೆ ಈಗಾಗಲೇ ಈ ದೂರಿನಲ್ಲಿ ದಾಖಲಾಗಿದೆ.");
        m.put("rbio.entity.remaining", "ನೀವು ಇನ್ನೂ {{count}} ಸೇರಿಸಬಹುದು.");
        m.put("rbio.legal.heading", "ಕಾನೂನು ಪ್ರಕರಣ");
        m.put("rbio.legal.case_number", "ಪ್ರಕರಣ ಸಂಖ್ಯೆ");
        m.put("rbio.legal.court_name", "ನ್ಯಾಯಾಲಯದ ಹೆಸರು");
        m.put("rbio.legal.case_status", "ಪ್ರಕರಣದ ಸ್ಥಿತಿ");
        m.put("rbio.legal.filing_date", "ದಾಖಲಿಸಿದ ದಿನಾಂಕ");
        m.put("rbio.legal.next_hearing", "ಮುಂದಿನ ವಿಚಾರಣೆಯ ದಿನಾಂಕ");
        m.put("rbio.legal.none", "ಈ ದೂರಿಗೆ ಯಾವುದೇ ಕಾನೂನು ಪ್ರಕರಣ ದಾಖಲಾಗಿಲ್ಲ.");
        m.put("rbio.legal.error.hearing_before_filing", "ಮುಂದಿನ ವಿಚಾರಣೆಯ ದಿನಾಂಕ ದಾಖಲಿಸಿದ ದಿನಾಂಕಕ್ಕಿಂತ ಮೊದಲು ಇರಬಾರದು.");
        m.put("rbio.reopen.heading", "ದೂರನ್ನು ಪುನಃ ತೆರೆಯಿರಿ");
        m.put("rbio.reopen.reason", "ಪುನಃ ತೆರೆಯುವ ಕಾರಣ");
        m.put("rbio.reopen.reason.appellate_authority", "ಮೇಲ್ಮನವಿ ಪ್ರಾಧಿಕಾರ");
        m.put("rbio.reopen.reason.court_order", "ನ್ಯಾಯಾಲಯದ ಆದೇಶ");
        m.put("rbio.reopen.reason.correction_required", "ತಿದ್ದುಪಡಿ ಅಗತ್ಯ");
        m.put("rbio.reopen.justification", "ಸಮರ್ಥನೆ");
        m.put("rbio.reopen.error.reason_required", "ಪುನಃ ತೆರೆಯುವ ಕಾರಣವನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("rbio.reopen.error.justification_required", "ಈ ದೂರನ್ನು ಪುನಃ ತೆರೆಯಲು ಸಮರ್ಥನೆ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.reopen.error.not_permitted", "ಮುಚ್ಚಿದ ದೂರನ್ನು ಒಂಬುಡ್ಸ್‌ಮನ್ ಮಾತ್ರ ಪುನಃ ತೆರೆಯಬಹುದು.");
        m.put("rbio.reopen.assigned_original", "ಪುನಃ ತೆರೆದು ಮೂಲ ಕಾರ್ಯನಿರ್ವಾಹಕ ಅಧಿಕಾರಿಗೆ ಹಿಂತಿರುಗಿಸಲಾಗಿದೆ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.ladder.action.submit_for_review", "പുനഃപരിശോധകന് അയയ്ക്കുക");
        m.put("rbio.ladder.action.forward_deputy", "ഉപ ഓംബുഡ്സ്മാന് അയയ്ക്കുക");
        m.put("rbio.ladder.action.forward_ombudsman", "ഓംബുഡ്സ്മാന് അയയ്ക്കുക");
        m.put("rbio.ladder.action.send_back_do", "നിർവഹണ ഉദ്യോഗസ്ഥന് തിരികെ അയയ്ക്കുക");
        m.put("rbio.ladder.action.send_back_reviewer", "പുനഃപരിശോധകന് തിരികെ അയയ്ക്കുക");
        m.put("rbio.ladder.action.send_back_deputy", "ഉപ ഓംബുഡ്സ്മാന് തിരികെ അയയ്ക്കുക");
        m.put("rbio.ladder.assignment.mode", "നിയോഗം");
        m.put("rbio.ladder.assignment.automatic", "സ്വയമേവ (റൗണ്ട് റോബിൻ)");
        m.put("rbio.ladder.assignment.manual", "മാനുവൽ");
        m.put("rbio.ladder.assignment.select_officer", "ഉദ്യോഗസ്ഥനെ തിരഞ്ഞെടുക്കുക");
        m.put("rbio.ladder.assignment.auto_ombudsman", "ഓഫീസിലെ ഓംബുഡ്സ്മാനെ സ്വയമേവ നിയോഗിക്കുന്നു");
        m.put("rbio.ladder.sendback.auto_previous", "മുൻ ഉദ്യോഗസ്ഥന് തിരികെ അയച്ചു");
        m.put("rbio.ladder.error.previous_holder_inactive",
                "മുൻ ഉദ്യോഗസ്ഥൻ ഇപ്പോൾ സജീവമല്ല. ഈ പരാതി തിരികെ അയയ്ക്കാൻ ഒരു ഉദ്യോഗസ്ഥനെ തിരഞ്ഞെടുക്കുക.");
        m.put("rbio.ladder.error.no_previous_holder",
                "ആ തലത്തിൽ ഒരു മുൻ ഉദ്യോഗസ്ഥനും ഈ പരാതി കൈകാര്യം ചെയ്തിട്ടില്ല. തിരികെ അയയ്ക്കാൻ ഒരു ഉദ്യോഗസ്ഥനെ തിരഞ്ഞെടുക്കുക.");
        m.put("rbio.ladder.error.comment_required", "സംരക്ഷിക്കുന്നതിന് മുമ്പ് ഒരു അഭിപ്രായം ആവശ്യമാണ്.");
        m.put("rbio.ladder.error.mandatory_fields", "സമർപ്പിക്കുന്നതിന് മുമ്പ് എല്ലാ നിർബന്ധിത ഫീൽഡുകളും പൂരിപ്പിക്കുക.");
        m.put("rbio.ladder.error.field_required", "ഈ ഫീൽഡ് ആവശ്യമാണ്.");
        m.put("rbio.ladder.decision.maintainable", "വിചാരണയോഗ്യം");
        m.put("rbio.ladder.decision.non_maintainable", "വിചാരണയോഗ്യമല്ല");
        m.put("rbio.ladder.decision.facilitation", "സഹായം");
        m.put("rbio.ladder.decision.rejection", "നിരാകരണം");
        m.put("rbio.ladder.decision.settled", "പരിഹരിച്ചു");
        m.put("rbio.ladder.decision.withdrawn", "പിൻവലിച്ചു");
        m.put("rbio.ladder.decision.not_a_complaint", "പരാതിയല്ല");
        m.put("rbio.ladder.decision.advisory_complied", "ഉപദേശം പാലിച്ചു");
        m.put("rbio.ladder.error.decision_required", "സംരക്ഷിക്കുന്നതിന് മുമ്പ് ഒരു തീരുമാനം തിരഞ്ഞെടുക്കുക.");
        m.put("rbio.ladder.error.maintainability_required", "സംരക്ഷിക്കുന്നതിന് മുമ്പ് വിചാരണയോഗ്യതാ നിർണയം രേഖപ്പെടുത്തുക.");
        m.put("rbio.ladder.error.clause_required", "ഈ പരാതി ഏത് വ്യവസ്ഥയുടെ കീഴിൽ അടച്ചു എന്ന് തിരഞ്ഞെടുക്കുക.");
        m.put("rbio.ladder.override.heading", "നടപടിയും വ്യവസ്ഥയും അതിലംഘന ചരിത്രം");
        m.put("rbio.ladder.override.field", "ഫീൽഡ്");
        m.put("rbio.ladder.override.previous_value", "മുൻ മൂല്യം");
        m.put("rbio.ladder.override.new_value", "പുതിയ മൂല്യം");
        m.put("rbio.ladder.override.changed_by", "മാറ്റിയത്");
        m.put("rbio.ladder.override.changed_at", "മാറ്റിയ തീയതി");
        m.put("rbio.ladder.override.none", "ഈ പരാതിക്ക് അതിലംഘനങ്ങൾ രേഖപ്പെടുത്തിയിട്ടില്ല.");
        m.put("rbio.entity.heading", "അധിക സ്ഥാപനങ്ങൾ");
        m.put("rbio.entity.add", "സ്ഥാപനം ചേർക്കുക");
        m.put("rbio.entity.name", "സ്ഥാപനത്തിന്റെ പേര്");
        m.put("rbio.entity.branch", "ശാഖ");
        m.put("rbio.entity.type", "സ്ഥാപനത്തിന്റെ തരം");
        m.put("rbio.entity.category", "സ്ഥാപനത്തിന്റെ വർഗം");
        m.put("rbio.entity.none", "അധിക സ്ഥാപനങ്ങൾ ചേർത്തിട്ടില്ല.");
        m.put("rbio.entity.cap_reached", "ഈ പരാതിക്ക് ആറ് അധിക സ്ഥാപനങ്ങളുടെ പരമാവധി പരിധി എത്തി.");
        m.put("rbio.entity.error.duplicate", "ഈ സ്ഥാപനം ഇതിനകം ഈ പരാതിയിൽ രേഖപ്പെടുത്തിയിട്ടുണ്ട്.");
        m.put("rbio.entity.remaining", "നിങ്ങൾക്ക് ഇനിയും {{count}} ചേർക്കാം.");
        m.put("rbio.legal.heading", "നിയമ കേസ്");
        m.put("rbio.legal.case_number", "കേസ് നമ്പർ");
        m.put("rbio.legal.court_name", "കോടതിയുടെ പേര്");
        m.put("rbio.legal.case_status", "കേസിന്റെ സ്ഥിതി");
        m.put("rbio.legal.filing_date", "ഫയൽ ചെയ്ത തീയതി");
        m.put("rbio.legal.next_hearing", "അടുത്ത വാദം കേൾക്കുന്ന തീയതി");
        m.put("rbio.legal.none", "ഈ പരാതിക്ക് നിയമ കേസ് രേഖപ്പെടുത്തിയിട്ടില്ല.");
        m.put("rbio.legal.error.hearing_before_filing", "അടുത്ത വാദം കേൾക്കുന്ന തീയതി ഫയൽ ചെയ്ത തീയതിക്ക് മുമ്പാകരുത്.");
        m.put("rbio.reopen.heading", "പരാതി വീണ്ടും തുറക്കുക");
        m.put("rbio.reopen.reason", "വീണ്ടും തുറക്കാനുള്ള കാരണം");
        m.put("rbio.reopen.reason.appellate_authority", "അപ്പീൽ അധികാരി");
        m.put("rbio.reopen.reason.court_order", "കോടതി ഉത്തരവ്");
        m.put("rbio.reopen.reason.correction_required", "തിരുത്തൽ ആവശ്യമാണ്");
        m.put("rbio.reopen.justification", "ന്യായീകരണം");
        m.put("rbio.reopen.error.reason_required", "വീണ്ടും തുറക്കാനുള്ള കാരണം തിരഞ്ഞെടുക്കുക.");
        m.put("rbio.reopen.error.justification_required", "ഈ പരാതി വീണ്ടും തുറക്കാൻ ന്യായീകരണം ആവശ്യമാണ്.");
        m.put("rbio.reopen.error.not_permitted", "അടച്ച പരാതി ഓംബുഡ്സ്മാന് മാത്രമേ വീണ്ടും തുറക്കാനാകും.");
        m.put("rbio.reopen.assigned_original", "വീണ്ടും തുറന്ന് യഥാർത്ഥ നിർവഹണ ഉദ്യോഗസ്ഥന് തിരികെ നൽകി.");
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
