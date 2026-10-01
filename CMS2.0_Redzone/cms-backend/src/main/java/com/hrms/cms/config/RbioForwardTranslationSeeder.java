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
 * Translations for S5's transfers and forwarding (UST556-568, 632-634, 759-761, 765-766, 770-772).
 *
 * <p>A SEPARATE seeder at its own {@code @Order}, per the convention stated at
 * {@code AaRegisterTranslationSeeder}. Every key is namespaced {@code rbio.forward.*}: keys are idempotent by
 * {@code existsByCode}, so a code chosen by two sessions keeps the first text and the second never appears.
 *
 * <p><b>Insert-if-absent.</b> Correcting a default here does NOT fix rows already in the database — that needs
 * a code-scoped UPDATE in both migration directories, scoped BY KEY CODE and never by an English phrase,
 * because the localized rows are in native scripts and an English substring matches none of them.
 *
 * <p>The error keys mirror the messages the server returns, so one refusal reads the same whether it came from
 * {@code InterOfficeTransferService}, {@code ForwardTargetService} or the transition table's backstop.
 */
@Component
@Order(44)
public class RbioForwardTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "rbio";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public RbioForwardTranslationSeeder(TranslationKeyRepository keyRepo,
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
        m.put("rbio.forward.title", "Forward Complaint");
        m.put("rbio.forward.sent_to_other_office", "Sent to Other Office");
        m.put("rbio.forward.transfer_office", "Transfer Office");
        m.put("rbio.forward.target_office", "Target Office");
        m.put("rbio.forward.reason", "Reason for Transfer");
        m.put("rbio.forward.language", "Language");
        m.put("rbio.forward.comments", "Comments");
        m.put("rbio.forward.department", "RBI Department");
        m.put("rbio.forward.regulatory_body", "Regulatory Body");
        m.put("rbio.forward.email_verified", "Email verified");
        m.put("rbio.forward.email_unverified", "Email not verified");
        m.put("rbio.forward.action.save_and_proceed", "Save and Proceed");
        m.put("rbio.forward.action.approve", "Approve Transfer");
        m.put("rbio.forward.action.reject", "Reject Transfer");
        m.put("rbio.forward.status.pending", "Awaiting CRPC Head approval");
        m.put("rbio.forward.status.approved", "Transfer approved");
        m.put("rbio.forward.status.rejected", "Transfer rejected");
        m.put("rbio.forward.history.layout_converted", "Complaint layout converted");
        m.put("rbio.forward.history.old_layout", "Previous layout");
        m.put("rbio.forward.history.new_layout", "New layout");
        m.put("rbio.forward.history.approved_by", "Approved by");
        m.put("rbio.forward.saved.requested",
                "Transfer requested. The complaint is awaiting CRPC Head approval.");
        m.put("rbio.forward.saved.approved", "Transfer approved and assigned at the destination office.");
        m.put("rbio.forward.saved.rejected", "Transfer rejected and returned to the previous owner.");
        m.put("rbio.forward.awareness_email_queued",
                "An awareness email will be sent to the complainant.");
        m.put("rbio.forward.error.office_required", "A destination office is required.");
        m.put("rbio.forward.error.office_unknown", "That office is not in the office master.");
        m.put("rbio.forward.error.reason_required", "A reason for transfer is required.");
        m.put("rbio.forward.error.department_required", "A target department is required.");
        m.put("rbio.forward.error.department_unknown",
                "That department is not in the RBI department master.");
        m.put("rbio.forward.error.body_required", "A regulatory body is required.");
        m.put("rbio.forward.error.body_unknown", "That body is not in the regulatory body master.");
        m.put("rbio.forward.error.body_email_unverified",
                "That body cannot receive a referral until its contact email has been verified.");
        m.put("rbio.forward.error.destination_at_capacity",
                "The destination office is at its configured capacity. Redirect the transfer or have an "
                        + "administrator permit over-capacity transfers.");
        m.put("rbio.forward.error.rejection_comment_required",
                "A comment is required to reject a transfer.");
        m.put("rbio.forward.error.select_officer",
                "The previous officer is unavailable. Select an officer before saving.");
        m.put("rbio.forward.error.unavailable", "The transfer could not be requested. Please retry.");
        m.put("rbio.forward.error.no_destinations",
                "No destination offices are configured. Please retry once they are available.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "शिकायत अग्रेषित करें");
        m.put("rbio.forward.sent_to_other_office", "अन्य कार्यालय को भेजा गया");
        m.put("rbio.forward.transfer_office", "स्थानांतरण कार्यालय");
        m.put("rbio.forward.target_office", "लक्ष्य कार्यालय");
        m.put("rbio.forward.reason", "स्थानांतरण का कारण");
        m.put("rbio.forward.language", "भाषा");
        m.put("rbio.forward.comments", "टिप्पणियाँ");
        m.put("rbio.forward.department", "आरबीआई विभाग");
        m.put("rbio.forward.regulatory_body", "नियामक संस्था");
        m.put("rbio.forward.email_verified", "ईमेल सत्यापित");
        m.put("rbio.forward.email_unverified", "ईमेल सत्यापित नहीं");
        m.put("rbio.forward.action.save_and_proceed", "सहेजें और आगे बढ़ें");
        m.put("rbio.forward.action.approve", "स्थानांतरण स्वीकृत करें");
        m.put("rbio.forward.action.reject", "स्थानांतरण अस्वीकार करें");
        m.put("rbio.forward.status.pending", "सीआरपीसी प्रमुख की स्वीकृति प्रतीक्षित");
        m.put("rbio.forward.status.approved", "स्थानांतरण स्वीकृत");
        m.put("rbio.forward.status.rejected", "स्थानांतरण अस्वीकृत");
        m.put("rbio.forward.history.layout_converted", "शिकायत का स्वरूप परिवर्तित");
        m.put("rbio.forward.history.old_layout", "पूर्व स्वरूप");
        m.put("rbio.forward.history.new_layout", "नया स्वरूप");
        m.put("rbio.forward.history.approved_by", "स्वीकृतकर्ता");
        m.put("rbio.forward.saved.requested",
                "स्थानांतरण का अनुरोध किया गया। शिकायत सीआरपीसी प्रमुख की स्वीकृति की प्रतीक्षा में है।");
        m.put("rbio.forward.saved.approved", "स्थानांतरण स्वीकृत और लक्ष्य कार्यालय में सौंपा गया।");
        m.put("rbio.forward.saved.rejected", "स्थानांतरण अस्वीकृत और पूर्व स्वामी को लौटाया गया।");
        m.put("rbio.forward.awareness_email_queued", "शिकायतकर्ता को सूचना ईमेल भेजा जाएगा।");
        m.put("rbio.forward.error.office_required", "लक्ष्य कार्यालय आवश्यक है।");
        m.put("rbio.forward.error.office_unknown", "वह कार्यालय कार्यालय मास्टर में नहीं है।");
        m.put("rbio.forward.error.reason_required", "स्थानांतरण का कारण आवश्यक है।");
        m.put("rbio.forward.error.department_required", "लक्ष्य विभाग आवश्यक है।");
        m.put("rbio.forward.error.department_unknown", "वह विभाग आरबीआई विभाग मास्टर में नहीं है।");
        m.put("rbio.forward.error.body_required", "नियामक संस्था आवश्यक है।");
        m.put("rbio.forward.error.body_unknown", "वह संस्था नियामक संस्था मास्टर में नहीं है।");
        m.put("rbio.forward.error.body_email_unverified",
                "उस संस्था का संपर्क ईमेल सत्यापित होने तक संदर्भ नहीं भेजा जा सकता।");
        m.put("rbio.forward.error.destination_at_capacity",
                "लक्ष्य कार्यालय अपनी निर्धारित क्षमता पर है। स्थानांतरण पुनर्निर्देशित करें या "
                        + "प्रशासक से अनुमति लें।");
        m.put("rbio.forward.error.rejection_comment_required",
                "स्थानांतरण अस्वीकार करने के लिए टिप्पणी आवश्यक है।");
        m.put("rbio.forward.error.select_officer",
                "पूर्व अधिकारी अनुपलब्ध है। सहेजने से पहले अधिकारी चुनें।");
        m.put("rbio.forward.error.unavailable",
                "स्थानांतरण का अनुरोध नहीं किया जा सका। कृपया पुनः प्रयास करें।");
        m.put("rbio.forward.error.no_destinations",
                "कोई लक्ष्य कार्यालय कॉन्फ़िगर नहीं है। उपलब्ध होने पर पुनः प्रयास करें।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "तक्रार अग्रेषित करा");
        m.put("rbio.forward.sent_to_other_office", "अन्य कार्यालयाकडे पाठवले");
        m.put("rbio.forward.transfer_office", "हस्तांतरण कार्यालय");
        m.put("rbio.forward.target_office", "लक्ष्य कार्यालय");
        m.put("rbio.forward.reason", "हस्तांतरणाचे कारण");
        m.put("rbio.forward.language", "भाषा");
        m.put("rbio.forward.comments", "टिप्पण्या");
        m.put("rbio.forward.department", "आरबीआय विभाग");
        m.put("rbio.forward.regulatory_body", "नियामक संस्था");
        m.put("rbio.forward.email_verified", "ईमेल सत्यापित");
        m.put("rbio.forward.email_unverified", "ईमेल सत्यापित नाही");
        m.put("rbio.forward.action.save_and_proceed", "जतन करा आणि पुढे जा");
        m.put("rbio.forward.action.approve", "हस्तांतरण मंजूर करा");
        m.put("rbio.forward.action.reject", "हस्तांतरण नाकारा");
        m.put("rbio.forward.status.pending", "सीआरपीसी प्रमुखांच्या मंजुरीच्या प्रतीक्षेत");
        m.put("rbio.forward.status.approved", "हस्तांतरण मंजूर");
        m.put("rbio.forward.status.rejected", "हस्तांतरण नाकारले");
        m.put("rbio.forward.history.layout_converted", "तक्रारीचे स्वरूप बदलले");
        m.put("rbio.forward.history.old_layout", "पूर्वीचे स्वरूप");
        m.put("rbio.forward.history.new_layout", "नवीन स्वरूप");
        m.put("rbio.forward.history.approved_by", "मंजूर करणारे");
        m.put("rbio.forward.saved.requested",
                "हस्तांतरणाची विनंती केली. तक्रार सीआरपीसी प्रमुखांच्या मंजुरीच्या प्रतीक्षेत आहे.");
        m.put("rbio.forward.saved.approved", "हस्तांतरण मंजूर आणि लक्ष्य कार्यालयात नेमले.");
        m.put("rbio.forward.saved.rejected", "हस्तांतरण नाकारले आणि पूर्वीच्या मालकाकडे परत केले.");
        m.put("rbio.forward.awareness_email_queued", "तक्रारदारास माहिती ईमेल पाठवला जाईल.");
        m.put("rbio.forward.error.office_required", "लक्ष्य कार्यालय आवश्यक आहे.");
        m.put("rbio.forward.error.office_unknown", "ते कार्यालय कार्यालय मास्टरमध्ये नाही.");
        m.put("rbio.forward.error.reason_required", "हस्तांतरणाचे कारण आवश्यक आहे.");
        m.put("rbio.forward.error.department_required", "लक्ष्य विभाग आवश्यक आहे.");
        m.put("rbio.forward.error.department_unknown", "तो विभाग आरबीआय विभाग मास्टरमध्ये नाही.");
        m.put("rbio.forward.error.body_required", "नियामक संस्था आवश्यक आहे.");
        m.put("rbio.forward.error.body_unknown", "ती संस्था नियामक संस्था मास्टरमध्ये नाही.");
        m.put("rbio.forward.error.body_email_unverified",
                "त्या संस्थेचा संपर्क ईमेल सत्यापित होईपर्यंत संदर्भ पाठवता येणार नाही.");
        m.put("rbio.forward.error.destination_at_capacity",
                "लक्ष्य कार्यालय त्याच्या निर्धारित क्षमतेवर आहे. हस्तांतरण पुनर्निर्देशित करा किंवा "
                        + "प्रशासकाची परवानगी घ्या.");
        m.put("rbio.forward.error.rejection_comment_required",
                "हस्तांतरण नाकारण्यासाठी टिप्पणी आवश्यक आहे.");
        m.put("rbio.forward.error.select_officer",
                "पूर्वीचा अधिकारी उपलब्ध नाही. जतन करण्यापूर्वी अधिकारी निवडा.");
        m.put("rbio.forward.error.unavailable",
                "हस्तांतरणाची विनंती करता आली नाही. कृपया पुन्हा प्रयत्न करा.");
        m.put("rbio.forward.error.no_destinations",
                "कोणतेही लक्ष्य कार्यालय संरचित नाही. उपलब्ध झाल्यावर पुन्हा प्रयत्न करा.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "অভিযোগ অগ্রেষণ করুন");
        m.put("rbio.forward.sent_to_other_office", "অন্য কার্যালয়ে পাঠানো হয়েছে");
        m.put("rbio.forward.transfer_office", "স্থানান্তর কার্যালয়");
        m.put("rbio.forward.target_office", "লক্ষ্য কার্যালয়");
        m.put("rbio.forward.reason", "স্থানান্তরের কারণ");
        m.put("rbio.forward.language", "ভাষা");
        m.put("rbio.forward.comments", "মন্তব্য");
        m.put("rbio.forward.department", "আরবিআই বিভাগ");
        m.put("rbio.forward.regulatory_body", "নিয়ন্ত্রক সংস্থা");
        m.put("rbio.forward.email_verified", "ইমেল যাচাইকৃত");
        m.put("rbio.forward.email_unverified", "ইমেল যাচাই করা হয়নি");
        m.put("rbio.forward.action.save_and_proceed", "সংরক্ষণ করে এগিয়ে যান");
        m.put("rbio.forward.action.approve", "স্থানান্তর অনুমোদন করুন");
        m.put("rbio.forward.action.reject", "স্থানান্তর প্রত্যাখ্যান করুন");
        m.put("rbio.forward.status.pending", "সিআরপিসি প্রধানের অনুমোদনের অপেক্ষায়");
        m.put("rbio.forward.status.approved", "স্থানান্তর অনুমোদিত");
        m.put("rbio.forward.status.rejected", "স্থানান্তর প্রত্যাখ্যাত");
        m.put("rbio.forward.history.layout_converted", "অভিযোগের বিন্যাস পরিবর্তিত");
        m.put("rbio.forward.history.old_layout", "পূর্বের বিন্যাস");
        m.put("rbio.forward.history.new_layout", "নতুন বিন্যাস");
        m.put("rbio.forward.history.approved_by", "অনুমোদনকারী");
        m.put("rbio.forward.saved.requested",
                "স্থানান্তরের অনুরোধ করা হয়েছে। অভিযোগ সিআরপিসি প্রধানের অনুমোদনের অপেক্ষায়।");
        m.put("rbio.forward.saved.approved", "স্থানান্তর অনুমোদিত এবং লক্ষ্য কার্যালয়ে নিয়োজিত।");
        m.put("rbio.forward.saved.rejected", "স্থানান্তর প্রত্যাখ্যাত এবং পূর্ববর্তী মালিকের কাছে ফেরত।");
        m.put("rbio.forward.awareness_email_queued", "অভিযোগকারীকে একটি অবহিতকরণ ইমেল পাঠানো হবে।");
        m.put("rbio.forward.error.office_required", "লক্ষ্য কার্যালয় আবশ্যক।");
        m.put("rbio.forward.error.office_unknown", "সেই কার্যালয় কার্যালয় মাস্টারে নেই।");
        m.put("rbio.forward.error.reason_required", "স্থানান্তরের কারণ আবশ্যক।");
        m.put("rbio.forward.error.department_required", "লক্ষ্য বিভাগ আবশ্যক।");
        m.put("rbio.forward.error.department_unknown", "সেই বিভাগ আরবিআই বিভাগ মাস্টারে নেই।");
        m.put("rbio.forward.error.body_required", "নিয়ন্ত্রক সংস্থা আবশ্যক।");
        m.put("rbio.forward.error.body_unknown", "সেই সংস্থা নিয়ন্ত্রক সংস্থা মাস্টারে নেই।");
        m.put("rbio.forward.error.body_email_unverified",
                "সেই সংস্থার যোগাযোগ ইমেল যাচাই না হওয়া পর্যন্ত রেফারেল পাঠানো যাবে না।");
        m.put("rbio.forward.error.destination_at_capacity",
                "লক্ষ্য কার্যালয় তার নির্ধারিত ক্ষমতায় পৌঁছেছে। স্থানান্তর পুনর্নির্দেশ করুন বা "
                        + "প্রশাসকের অনুমতি নিন।");
        m.put("rbio.forward.error.rejection_comment_required",
                "স্থানান্তর প্রত্যাখ্যান করতে মন্তব্য আবশ্যক।");
        m.put("rbio.forward.error.select_officer",
                "পূর্ববর্তী কর্মকর্তা উপলব্ধ নন। সংরক্ষণের আগে একজন কর্মকর্তা নির্বাচন করুন।");
        m.put("rbio.forward.error.unavailable",
                "স্থানান্তরের অনুরোধ করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।");
        m.put("rbio.forward.error.no_destinations",
                "কোনো লক্ষ্য কার্যালয় কনফিগার করা নেই। উপলব্ধ হলে পুনরায় চেষ্টা করুন।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "ఫిర్యాదును పంపండి");
        m.put("rbio.forward.sent_to_other_office", "వేరే కార్యాలయానికి పంపబడింది");
        m.put("rbio.forward.transfer_office", "బదిలీ కార్యాలయం");
        m.put("rbio.forward.target_office", "లక్ష్య కార్యాలయం");
        m.put("rbio.forward.reason", "బదిలీ కారణం");
        m.put("rbio.forward.language", "భాష");
        m.put("rbio.forward.comments", "వ్యాఖ్యలు");
        m.put("rbio.forward.department", "ఆర్‌బీఐ విభాగం");
        m.put("rbio.forward.regulatory_body", "నియంత్రణ సంస్థ");
        m.put("rbio.forward.email_verified", "ఇమెయిల్ ధృవీకరించబడింది");
        m.put("rbio.forward.email_unverified", "ఇమెయిల్ ధృవీకరించబడలేదు");
        m.put("rbio.forward.action.save_and_proceed", "సేవ్ చేసి కొనసాగించండి");
        m.put("rbio.forward.action.approve", "బదిలీని ఆమోదించండి");
        m.put("rbio.forward.action.reject", "బదిలీని తిరస్కరించండి");
        m.put("rbio.forward.status.pending", "సీఆర్‌పీసీ ప్రధాన ఆమోదం కోసం వేచి ఉంది");
        m.put("rbio.forward.status.approved", "బదిలీ ఆమోదించబడింది");
        m.put("rbio.forward.status.rejected", "బదిలీ తిరస్కరించబడింది");
        m.put("rbio.forward.history.layout_converted", "ఫిర్యాదు నమూనా మార్చబడింది");
        m.put("rbio.forward.history.old_layout", "మునుపటి నమూనా");
        m.put("rbio.forward.history.new_layout", "కొత్త నమూనా");
        m.put("rbio.forward.history.approved_by", "ఆమోదించినవారు");
        m.put("rbio.forward.saved.requested",
                "బదిలీ అభ్యర్థించబడింది. ఫిర్యాదు సీఆర్‌పీసీ ప్రధాన ఆమోదం కోసం వేచి ఉంది.");
        m.put("rbio.forward.saved.approved", "బదిలీ ఆమోదించబడి లక్ష్య కార్యాలయంలో కేటాయించబడింది.");
        m.put("rbio.forward.saved.rejected", "బదిలీ తిరస్కరించబడి మునుపటి యజమానికి తిరిగి ఇవ్వబడింది.");
        m.put("rbio.forward.awareness_email_queued", "ఫిర్యాదుదారుకు సమాచార ఇమెయిల్ పంపబడుతుంది.");
        m.put("rbio.forward.error.office_required", "లక్ష్య కార్యాలయం అవసరం.");
        m.put("rbio.forward.error.office_unknown", "ఆ కార్యాలయం కార్యాలయ మాస్టర్‌లో లేదు.");
        m.put("rbio.forward.error.reason_required", "బదిలీ కారణం అవసరం.");
        m.put("rbio.forward.error.department_required", "లక్ష్య విభాగం అవసరం.");
        m.put("rbio.forward.error.department_unknown", "ఆ విభాగం ఆర్‌బీఐ విభాగ మాస్టర్‌లో లేదు.");
        m.put("rbio.forward.error.body_required", "నియంత్రణ సంస్థ అవసరం.");
        m.put("rbio.forward.error.body_unknown", "ఆ సంస్థ నియంత్రణ సంస్థ మాస్టర్‌లో లేదు.");
        m.put("rbio.forward.error.body_email_unverified",
                "ఆ సంస్థ సంప్రదింపు ఇమెయిల్ ధృవీకరించబడే వరకు సిఫారసు పంపలేరు.");
        m.put("rbio.forward.error.destination_at_capacity",
                "లక్ష్య కార్యాలయం దాని నిర్ణీత సామర్థ్యానికి చేరింది. బదిలీని మళ్లించండి లేదా "
                        + "నిర్వాహకుని అనుమతి పొందండి.");
        m.put("rbio.forward.error.rejection_comment_required",
                "బదిలీని తిరస్కరించడానికి వ్యాఖ్య అవసరం.");
        m.put("rbio.forward.error.select_officer",
                "మునుపటి అధికారి అందుబాటులో లేరు. సేవ్ చేయడానికి ముందు అధికారిని ఎంచుకోండి.");
        m.put("rbio.forward.error.unavailable",
                "బదిలీ అభ్యర్థించలేకపోయాము. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("rbio.forward.error.no_destinations",
                "ఏ లక్ష్య కార్యాలయం కాన్ఫిగర్ చేయబడలేదు. అందుబాటులో వచ్చినప్పుడు మళ్లీ ప్రయత్నించండి.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "புகாரை அனுப்பு");
        m.put("rbio.forward.sent_to_other_office", "வேறு அலுவலகத்திற்கு அனுப்பப்பட்டது");
        m.put("rbio.forward.transfer_office", "இடமாற்ற அலுவலகம்");
        m.put("rbio.forward.target_office", "இலக்கு அலுவலகம்");
        m.put("rbio.forward.reason", "இடமாற்றத்திற்கான காரணம்");
        m.put("rbio.forward.language", "மொழி");
        m.put("rbio.forward.comments", "கருத்துகள்");
        m.put("rbio.forward.department", "ஆர்பிஐ துறை");
        m.put("rbio.forward.regulatory_body", "ஒழுங்குமுறை அமைப்பு");
        m.put("rbio.forward.email_verified", "மின்னஞ்சல் சரிபார்க்கப்பட்டது");
        m.put("rbio.forward.email_unverified", "மின்னஞ்சல் சரிபார்க்கப்படவில்லை");
        m.put("rbio.forward.action.save_and_proceed", "சேமித்து தொடரவும்");
        m.put("rbio.forward.action.approve", "இடமாற்றத்தை அனுமதி");
        m.put("rbio.forward.action.reject", "இடமாற்றத்தை நிராகரி");
        m.put("rbio.forward.status.pending", "சிஆர்பிசி தலைவரின் ஒப்புதலுக்காக காத்திருக்கிறது");
        m.put("rbio.forward.status.approved", "இடமாற்றம் அனுமதிக்கப்பட்டது");
        m.put("rbio.forward.status.rejected", "இடமாற்றம் நிராகரிக்கப்பட்டது");
        m.put("rbio.forward.history.layout_converted", "புகாரின் வடிவம் மாற்றப்பட்டது");
        m.put("rbio.forward.history.old_layout", "முந்தைய வடிவம்");
        m.put("rbio.forward.history.new_layout", "புதிய வடிவம்");
        m.put("rbio.forward.history.approved_by", "அனுமதித்தவர்");
        m.put("rbio.forward.saved.requested",
                "இடமாற்றம் கோரப்பட்டது. புகார் சிஆர்பிசி தலைவரின் ஒப்புதலுக்காக காத்திருக்கிறது.");
        m.put("rbio.forward.saved.approved", "இடமாற்றம் அனுமதிக்கப்பட்டு இலக்கு அலுவலகத்தில் ஒப்படைக்கப்பட்டது.");
        m.put("rbio.forward.saved.rejected", "இடமாற்றம் நிராகரிக்கப்பட்டு முந்தைய உரிமையாளருக்குத் திரும்பியது.");
        m.put("rbio.forward.awareness_email_queued", "புகார்தாரருக்கு அறிவிப்பு மின்னஞ்சல் அனுப்பப்படும்.");
        m.put("rbio.forward.error.office_required", "இலக்கு அலுவலகம் தேவை.");
        m.put("rbio.forward.error.office_unknown", "அந்த அலுவலகம் அலுவலக முதன்மைப் பட்டியலில் இல்லை.");
        m.put("rbio.forward.error.reason_required", "இடமாற்றத்திற்கான காரணம் தேவை.");
        m.put("rbio.forward.error.department_required", "இலக்குத் துறை தேவை.");
        m.put("rbio.forward.error.department_unknown", "அந்தத் துறை ஆர்பிஐ துறை முதன்மைப் பட்டியலில் இல்லை.");
        m.put("rbio.forward.error.body_required", "ஒழுங்குமுறை அமைப்பு தேவை.");
        m.put("rbio.forward.error.body_unknown", "அந்த அமைப்பு ஒழுங்குமுறை அமைப்பு பட்டியலில் இல்லை.");
        m.put("rbio.forward.error.body_email_unverified",
                "அந்த அமைப்பின் தொடர்பு மின்னஞ்சல் சரிபார்க்கப்படும் வரை பரிந்துரை அனுப்ப முடியாது.");
        m.put("rbio.forward.error.destination_at_capacity",
                "இலக்கு அலுவலகம் அதன் நிர்ணயிக்கப்பட்ட கொள்ளளவை எட்டியுள்ளது. இடமாற்றத்தை மாற்றி "
                        + "அனுப்பவும் அல்லது நிர்வாகியின் அனுமதியைப் பெறவும்.");
        m.put("rbio.forward.error.rejection_comment_required",
                "இடமாற்றத்தை நிராகரிக்க கருத்து தேவை.");
        m.put("rbio.forward.error.select_officer",
                "முந்தைய அதிகாரி கிடைக்கவில்லை. சேமிப்பதற்கு முன் ஒரு அதிகாரியைத் தேர்ந்தெடுக்கவும்.");
        m.put("rbio.forward.error.unavailable", "இடமாற்றம் கோர முடியவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("rbio.forward.error.no_destinations",
                "எந்த இலக்கு அலுவலகமும் அமைக்கப்படவில்லை. கிடைக்கும்போது மீண்டும் முயற்சிக்கவும்.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "ફરિયાદ અગ્રેષિત કરો");
        m.put("rbio.forward.sent_to_other_office", "અન્ય કાર્યાલયને મોકલ્યું");
        m.put("rbio.forward.transfer_office", "સ્થાનાંતરણ કાર્યાલય");
        m.put("rbio.forward.target_office", "લક્ષ્ય કાર્યાલય");
        m.put("rbio.forward.reason", "સ્થાનાંતરણનું કારણ");
        m.put("rbio.forward.language", "ભાષા");
        m.put("rbio.forward.comments", "ટિપ્પણીઓ");
        m.put("rbio.forward.department", "આરબીઆઈ વિભાગ");
        m.put("rbio.forward.regulatory_body", "નિયામક સંસ્થા");
        m.put("rbio.forward.email_verified", "ઈમેલ સત્યાપિત");
        m.put("rbio.forward.email_unverified", "ઈમેલ સત્યાપિત નથી");
        m.put("rbio.forward.action.save_and_proceed", "સાચવો અને આગળ વધો");
        m.put("rbio.forward.action.approve", "સ્થાનાંતરણ મંજૂર કરો");
        m.put("rbio.forward.action.reject", "સ્થાનાંતરણ નકારો");
        m.put("rbio.forward.status.pending", "સીઆરપીસી વડાની મંજૂરીની પ્રતીક્ષામાં");
        m.put("rbio.forward.status.approved", "સ્થાનાંતરણ મંજૂર");
        m.put("rbio.forward.status.rejected", "સ્થાનાંતરણ નકારાયું");
        m.put("rbio.forward.history.layout_converted", "ફરિયાદનું સ્વરૂપ બદલાયું");
        m.put("rbio.forward.history.old_layout", "પહેલાંનું સ્વરૂપ");
        m.put("rbio.forward.history.new_layout", "નવું સ્વરૂપ");
        m.put("rbio.forward.history.approved_by", "મંજૂર કરનાર");
        m.put("rbio.forward.saved.requested",
                "સ્થાનાંતરણની વિનંતી કરી. ફરિયાદ સીઆરપીસી વડાની મંજૂરીની પ્રતીક્ષામાં છે.");
        m.put("rbio.forward.saved.approved", "સ્થાનાંતરણ મંજૂર અને લક્ષ્ય કાર્યાલયમાં સોંપાયું.");
        m.put("rbio.forward.saved.rejected", "સ્થાનાંતરણ નકારાયું અને પહેલાંના માલિકને પરત કર્યું.");
        m.put("rbio.forward.awareness_email_queued", "ફરિયાદીને જાણકારી ઈમેલ મોકલવામાં આવશે.");
        m.put("rbio.forward.error.office_required", "લક્ષ્ય કાર્યાલય આવશ્યક છે.");
        m.put("rbio.forward.error.office_unknown", "તે કાર્યાલય કાર્યાલય માસ્ટરમાં નથી.");
        m.put("rbio.forward.error.reason_required", "સ્થાનાંતરણનું કારણ આવશ્યક છે.");
        m.put("rbio.forward.error.department_required", "લક્ષ્ય વિભાગ આવશ્યક છે.");
        m.put("rbio.forward.error.department_unknown", "તે વિભાગ આરબીઆઈ વિભાગ માસ્ટરમાં નથી.");
        m.put("rbio.forward.error.body_required", "નિયામક સંસ્થા આવશ્યક છે.");
        m.put("rbio.forward.error.body_unknown", "તે સંસ્થા નિયામક સંસ્થા માસ્ટરમાં નથી.");
        m.put("rbio.forward.error.body_email_unverified",
                "તે સંસ્થાનો સંપર્ક ઈમેલ સત્યાપિત થાય ત્યાં સુધી સંદર્ભ મોકલી શકાતો નથી.");
        m.put("rbio.forward.error.destination_at_capacity",
                "લક્ષ્ય કાર્યાલય તેની નિર્ધારિત ક્ષમતા પર છે. સ્થાનાંતરણ પુનર્નિર્દેશિત કરો અથવા "
                        + "પ્રશાસકની પરવાનગી લો.");
        m.put("rbio.forward.error.rejection_comment_required",
                "સ્થાનાંતરણ નકારવા માટે ટિપ્પણી આવશ્યક છે.");
        m.put("rbio.forward.error.select_officer",
                "પહેલાંના અધિકારી ઉપલબ્ધ નથી. સાચવતાં પહેલાં અધિકારી પસંદ કરો.");
        m.put("rbio.forward.error.unavailable",
                "સ્થાનાંતરણની વિનંતી કરી શકાઈ નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("rbio.forward.error.no_destinations",
                "કોઈ લક્ષ્ય કાર્યાલય રચાયેલ નથી. ઉપલબ્ધ થાય ત્યારે ફરી પ્રયાસ કરો.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "شکایت آگے بھیجیں");
        m.put("rbio.forward.sent_to_other_office", "دوسرے دفتر کو بھیجا گیا");
        m.put("rbio.forward.transfer_office", "منتقلی دفتر");
        m.put("rbio.forward.target_office", "ہدف دفتر");
        m.put("rbio.forward.reason", "منتقلی کی وجہ");
        m.put("rbio.forward.language", "زبان");
        m.put("rbio.forward.comments", "تبصرے");
        m.put("rbio.forward.department", "آر بی آئی شعبہ");
        m.put("rbio.forward.regulatory_body", "ریگولیٹری ادارہ");
        m.put("rbio.forward.email_verified", "ای میل تصدیق شدہ");
        m.put("rbio.forward.email_unverified", "ای میل تصدیق شدہ نہیں");
        m.put("rbio.forward.action.save_and_proceed", "محفوظ کریں اور آگے بڑھیں");
        m.put("rbio.forward.action.approve", "منتقلی منظور کریں");
        m.put("rbio.forward.action.reject", "منتقلی مسترد کریں");
        m.put("rbio.forward.status.pending", "سی آر پی سی سربراہ کی منظوری کا انتظار");
        m.put("rbio.forward.status.approved", "منتقلی منظور");
        m.put("rbio.forward.status.rejected", "منتقلی مسترد");
        m.put("rbio.forward.history.layout_converted", "شکایت کا خاکہ تبدیل ہوا");
        m.put("rbio.forward.history.old_layout", "سابقہ خاکہ");
        m.put("rbio.forward.history.new_layout", "نیا خاکہ");
        m.put("rbio.forward.history.approved_by", "منظور کنندہ");
        m.put("rbio.forward.saved.requested",
                "منتقلی کی درخواست کی گئی۔ شکایت سی آر پی سی سربراہ کی منظوری کے انتظار میں ہے۔");
        m.put("rbio.forward.saved.approved", "منتقلی منظور اور ہدف دفتر میں تفویض کر دی گئی۔");
        m.put("rbio.forward.saved.rejected", "منتقلی مسترد اور سابقہ مالک کو واپس کر دی گئی۔");
        m.put("rbio.forward.awareness_email_queued", "شکایت کنندہ کو اطلاعی ای میل بھیجی جائے گی۔");
        m.put("rbio.forward.error.office_required", "ہدف دفتر ضروری ہے۔");
        m.put("rbio.forward.error.office_unknown", "وہ دفتر دفتر ماسٹر میں نہیں ہے۔");
        m.put("rbio.forward.error.reason_required", "منتقلی کی وجہ ضروری ہے۔");
        m.put("rbio.forward.error.department_required", "ہدف شعبہ ضروری ہے۔");
        m.put("rbio.forward.error.department_unknown", "وہ شعبہ آر بی آئی شعبہ ماسٹر میں نہیں ہے۔");
        m.put("rbio.forward.error.body_required", "ریگولیٹری ادارہ ضروری ہے۔");
        m.put("rbio.forward.error.body_unknown", "وہ ادارہ ریگولیٹری ادارہ ماسٹر میں نہیں ہے۔");
        m.put("rbio.forward.error.body_email_unverified",
                "اس ادارے کا رابطہ ای میل تصدیق ہونے تک حوالہ نہیں بھیجا جا سکتا۔");
        m.put("rbio.forward.error.destination_at_capacity",
                "ہدف دفتر اپنی مقررہ گنجائش پر ہے۔ منتقلی کا رخ تبدیل کریں یا منتظم سے اجازت لیں۔");
        m.put("rbio.forward.error.rejection_comment_required",
                "منتقلی مسترد کرنے کے لیے تبصرہ ضروری ہے۔");
        m.put("rbio.forward.error.select_officer",
                "سابقہ افسر دستیاب نہیں۔ محفوظ کرنے سے پہلے افسر منتخب کریں۔");
        m.put("rbio.forward.error.unavailable",
                "منتقلی کی درخواست نہیں کی جا سکی۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("rbio.forward.error.no_destinations",
                "کوئی ہدف دفتر مرتب نہیں ہے۔ دستیاب ہونے پر دوبارہ کوشش کریں۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "ದೂರನ್ನು ರವಾನಿಸಿ");
        m.put("rbio.forward.sent_to_other_office", "ಬೇರೆ ಕಚೇರಿಗೆ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("rbio.forward.transfer_office", "ವರ್ಗಾವಣೆ ಕಚೇರಿ");
        m.put("rbio.forward.target_office", "ಗುರಿ ಕಚೇರಿ");
        m.put("rbio.forward.reason", "ವರ್ಗಾವಣೆಯ ಕಾರಣ");
        m.put("rbio.forward.language", "ಭಾಷೆ");
        m.put("rbio.forward.comments", "ಟಿಪ್ಪಣಿಗಳು");
        m.put("rbio.forward.department", "ಆರ್‌ಬಿಐ ಇಲಾಖೆ");
        m.put("rbio.forward.regulatory_body", "ನಿಯಂತ್ರಣ ಸಂಸ್ಥೆ");
        m.put("rbio.forward.email_verified", "ಇಮೇಲ್ ಪರಿಶೀಲಿಸಲಾಗಿದೆ");
        m.put("rbio.forward.email_unverified", "ಇಮೇಲ್ ಪರಿಶೀಲಿಸಲಾಗಿಲ್ಲ");
        m.put("rbio.forward.action.save_and_proceed", "ಉಳಿಸಿ ಮುಂದುವರಿಯಿರಿ");
        m.put("rbio.forward.action.approve", "ವರ್ಗಾವಣೆ ಅನುಮೋದಿಸಿ");
        m.put("rbio.forward.action.reject", "ವರ್ಗಾವಣೆ ತಿರಸ್ಕರಿಸಿ");
        m.put("rbio.forward.status.pending", "ಸಿಆರ್‌ಪಿಸಿ ಮುಖ್ಯಸ್ಥರ ಅನುಮೋದನೆಗಾಗಿ ಕಾಯುತ್ತಿದೆ");
        m.put("rbio.forward.status.approved", "ವರ್ಗಾವಣೆ ಅನುಮೋದಿತ");
        m.put("rbio.forward.status.rejected", "ವರ್ಗಾವಣೆ ತಿರಸ್ಕೃತ");
        m.put("rbio.forward.history.layout_converted", "ದೂರಿನ ಸ್ವರೂಪ ಬದಲಾಗಿದೆ");
        m.put("rbio.forward.history.old_layout", "ಹಿಂದಿನ ಸ್ವರೂಪ");
        m.put("rbio.forward.history.new_layout", "ಹೊಸ ಸ್ವರೂಪ");
        m.put("rbio.forward.history.approved_by", "ಅನುಮೋದಿಸಿದವರು");
        m.put("rbio.forward.saved.requested",
                "ವರ್ಗಾವಣೆ ಕೋರಲಾಗಿದೆ. ದೂರು ಸಿಆರ್‌ಪಿಸಿ ಮುಖ್ಯಸ್ಥರ ಅನುಮೋದನೆಗಾಗಿ ಕಾಯುತ್ತಿದೆ.");
        m.put("rbio.forward.saved.approved", "ವರ್ಗಾವಣೆ ಅನುಮೋದಿಸಿ ಗುರಿ ಕಚೇರಿಯಲ್ಲಿ ನಿಯೋಜಿಸಲಾಗಿದೆ.");
        m.put("rbio.forward.saved.rejected", "ವರ್ಗಾವಣೆ ತಿರಸ್ಕರಿಸಿ ಹಿಂದಿನ ಮಾಲೀಕರಿಗೆ ಹಿಂತಿರುಗಿಸಲಾಗಿದೆ.");
        m.put("rbio.forward.awareness_email_queued", "ದೂರುದಾರರಿಗೆ ಮಾಹಿತಿ ಇಮೇಲ್ ಕಳುಹಿಸಲಾಗುವುದು.");
        m.put("rbio.forward.error.office_required", "ಗುರಿ ಕಚೇರಿ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.forward.error.office_unknown", "ಆ ಕಚೇರಿ ಕಚೇರಿ ಮಾಸ್ಟರ್‌ನಲ್ಲಿ ಇಲ್ಲ.");
        m.put("rbio.forward.error.reason_required", "ವರ್ಗಾವಣೆಯ ಕಾರಣ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.forward.error.department_required", "ಗುರಿ ಇಲಾಖೆ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.forward.error.department_unknown", "ಆ ಇಲಾಖೆ ಆರ್‌ಬಿಐ ಇಲಾಖೆ ಮಾಸ್ಟರ್‌ನಲ್ಲಿ ಇಲ್ಲ.");
        m.put("rbio.forward.error.body_required", "ನಿಯಂತ್ರಣ ಸಂಸ್ಥೆ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.forward.error.body_unknown", "ಆ ಸಂಸ್ಥೆ ನಿಯಂತ್ರಣ ಸಂಸ್ಥೆ ಮಾಸ್ಟರ್‌ನಲ್ಲಿ ಇಲ್ಲ.");
        m.put("rbio.forward.error.body_email_unverified",
                "ಆ ಸಂಸ್ಥೆಯ ಸಂಪರ್ಕ ಇಮೇಲ್ ಪರಿಶೀಲನೆಯಾಗುವವರೆಗೆ ಶಿಫಾರಸು ಕಳುಹಿಸಲಾಗದು.");
        m.put("rbio.forward.error.destination_at_capacity",
                "ಗುರಿ ಕಚೇರಿ ತನ್ನ ನಿಗದಿತ ಸಾಮರ್ಥ್ಯದಲ್ಲಿದೆ. ವರ್ಗಾವಣೆಯನ್ನು ಮರುನಿರ್ದೇಶಿಸಿ ಅಥವಾ "
                        + "ನಿರ್ವಾಹಕರ ಅನುಮತಿ ಪಡೆಯಿರಿ.");
        m.put("rbio.forward.error.rejection_comment_required",
                "ವರ್ಗಾವಣೆ ತಿರಸ್ಕರಿಸಲು ಟಿಪ್ಪಣಿ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.forward.error.select_officer",
                "ಹಿಂದಿನ ಅಧಿಕಾರಿ ಲಭ್ಯವಿಲ್ಲ. ಉಳಿಸುವ ಮೊದಲು ಅಧಿಕಾರಿಯನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("rbio.forward.error.unavailable",
                "ವರ್ಗಾವಣೆ ಕೋರಲಾಗಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("rbio.forward.error.no_destinations",
                "ಯಾವುದೇ ಗುರಿ ಕಚೇರಿ ಸಂರಚಿಸಲಾಗಿಲ್ಲ. ಲಭ್ಯವಾದಾಗ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.forward.title", "പരാതി കൈമാറുക");
        m.put("rbio.forward.sent_to_other_office", "മറ്റൊരു ഓഫീസിലേക്ക് അയച്ചു");
        m.put("rbio.forward.transfer_office", "കൈമാറ്റ ഓഫീസ്");
        m.put("rbio.forward.target_office", "ലക്ഷ്യ ഓഫീസ്");
        m.put("rbio.forward.reason", "കൈമാറ്റത്തിന്റെ കാരണം");
        m.put("rbio.forward.language", "ഭാഷ");
        m.put("rbio.forward.comments", "അഭിപ്രായങ്ങൾ");
        m.put("rbio.forward.department", "ആർബിഐ വിഭാഗം");
        m.put("rbio.forward.regulatory_body", "നിയന്ത്രണ സ്ഥാപനം");
        m.put("rbio.forward.email_verified", "ഇമെയിൽ പരിശോധിച്ചു");
        m.put("rbio.forward.email_unverified", "ഇമെയിൽ പരിശോധിച്ചിട്ടില്ല");
        m.put("rbio.forward.action.save_and_proceed", "സംരക്ഷിച്ച് തുടരുക");
        m.put("rbio.forward.action.approve", "കൈമാറ്റം അനുവദിക്കുക");
        m.put("rbio.forward.action.reject", "കൈമാറ്റം നിരസിക്കുക");
        m.put("rbio.forward.status.pending", "സിആർപിസി മേധാവിയുടെ അനുമതിക്കായി കാത്തിരിക്കുന്നു");
        m.put("rbio.forward.status.approved", "കൈമാറ്റം അനുവദിച്ചു");
        m.put("rbio.forward.status.rejected", "കൈമാറ്റം നിരസിച്ചു");
        m.put("rbio.forward.history.layout_converted", "പരാതിയുടെ രൂപം മാറ്റി");
        m.put("rbio.forward.history.old_layout", "മുൻ രൂപം");
        m.put("rbio.forward.history.new_layout", "പുതിയ രൂപം");
        m.put("rbio.forward.history.approved_by", "അനുവദിച്ചത്");
        m.put("rbio.forward.saved.requested",
                "കൈമാറ്റം അഭ്യർഥിച്ചു. പരാതി സിആർപിസി മേധാവിയുടെ അനുമതിക്കായി കാത്തിരിക്കുന്നു.");
        m.put("rbio.forward.saved.approved", "കൈമാറ്റം അനുവദിച്ച് ലക്ഷ്യ ഓഫീസിൽ നിയോഗിച്ചു.");
        m.put("rbio.forward.saved.rejected", "കൈമാറ്റം നിരസിച്ച് മുൻ ഉടമയ്ക്ക് തിരികെ നൽകി.");
        m.put("rbio.forward.awareness_email_queued", "പരാതിക്കാരന് അറിയിപ്പ് ഇമെയിൽ അയയ്ക്കും.");
        m.put("rbio.forward.error.office_required", "ലക്ഷ്യ ഓഫീസ് ആവശ്യമാണ്.");
        m.put("rbio.forward.error.office_unknown", "ആ ഓഫീസ് ഓഫീസ് മാസ്റ്ററിൽ ഇല്ല.");
        m.put("rbio.forward.error.reason_required", "കൈമാറ്റത്തിന്റെ കാരണം ആവശ്യമാണ്.");
        m.put("rbio.forward.error.department_required", "ലക്ഷ്യ വിഭാഗം ആവശ്യമാണ്.");
        m.put("rbio.forward.error.department_unknown", "ആ വിഭാഗം ആർബിഐ വിഭാഗ മാസ്റ്ററിൽ ഇല്ല.");
        m.put("rbio.forward.error.body_required", "നിയന്ത്രണ സ്ഥാപനം ആവശ്യമാണ്.");
        m.put("rbio.forward.error.body_unknown", "ആ സ്ഥാപനം നിയന്ത്രണ സ്ഥാപന മാസ്റ്ററിൽ ഇല്ല.");
        m.put("rbio.forward.error.body_email_unverified",
                "ആ സ്ഥാപനത്തിന്റെ ബന്ധപ്പെടാനുള്ള ഇമെയിൽ പരിശോധിക്കുന്നതുവരെ റഫറൽ അയയ്ക്കാനാവില്ല.");
        m.put("rbio.forward.error.destination_at_capacity",
                "ലക്ഷ്യ ഓഫീസ് അതിന്റെ നിർണ്ണയിച്ച ശേഷിയിലാണ്. കൈമാറ്റം തിരിച്ചുവിടുക അല്ലെങ്കിൽ "
                        + "അഡ്മിനിസ്ട്രേറ്ററുടെ അനുമതി നേടുക.");
        m.put("rbio.forward.error.rejection_comment_required",
                "കൈമാറ്റം നിരസിക്കാൻ അഭിപ്രായം ആവശ്യമാണ്.");
        m.put("rbio.forward.error.select_officer",
                "മുൻ ഉദ്യോഗസ്ഥൻ ലഭ്യമല്ല. സംരക്ഷിക്കുന്നതിന് മുൻപ് ഒരു ഉദ്യോഗസ്ഥനെ തിരഞ്ഞെടുക്കുക.");
        m.put("rbio.forward.error.unavailable", "കൈമാറ്റം അഭ്യർഥിക്കാനായില്ല. വീണ്ടും ശ്രമിക്കുക.");
        m.put("rbio.forward.error.no_destinations",
                "ലക്ഷ്യ ഓഫീസുകൾ ക്രമീകരിച്ചിട്ടില്ല. ലഭ്യമാകുമ്പോൾ വീണ്ടും ശ്രമിക്കുക.");
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
