package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Page titles and meta descriptions for the public portal, in all eleven locales.
 *
 * <p>WHY THESE ARE TRANSLATION KEYS AND NOT STRINGS IN THE ANGULAR CODE. The portal had no per-route
 * titles at all — every one of 84 routes shared one static English title from {@code index.html}. Fixing
 * that by hardcoding English titles in a service would rebuild precisely the defect the i18n work
 * removed, and a title tag is the single most visible string a search engine shows a citizen. A Tamil
 * speaker searching in Tamil should find a Tamil result.
 *
 * <p>WORDING IS DESCRIPTIVE, NOT PROMOTIONAL. These are the words a citizen actually searches for
 * ("complaint against bank", "banking ombudsman") in plain language. No statutory text and no clause
 * numbers are reproduced here: nothing in a meta description carries legal effect, and inventing
 * quasi-legal phrasing for SEO would be worse than a lower ranking.
 *
 * <p>{@code @Order(65)} — a NEW seeder rather than an edit to another session's file, per the convention
 * here. 60-64 were verified taken before choosing 65.
 *
 * <p>Insert-if-absent by key code, so re-running is a no-op — with the corollary that correcting any of
 * this text later needs a code-scoped UPDATE migration in both dialects, not just an edit here.
 */
@Component
@Order(65)
@RequiredArgsConstructor
@Slf4j
public class SeoTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "seo";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    @Override
    @Transactional
    public void run(String... args) {
        english().forEach(this::seed);
        seedLocale("hi", hindi());
        seedLocale("bn", bengali());
        seedLocale("mr", marathi());
        seedLocale("te", telugu());
        seedLocale("ta", tamil());
        seedLocale("gu", gujarati());
        seedLocale("ur", urdu());
        seedLocale("kn", kannada());
        seedLocale("ml", malayalam());
        seedLocale("pa", punjabi());
        log.info("SEO titles and descriptions seeded: {} keys in module {}", english().size(), MODULE);
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "RBI Complaint Management System");

        m.put("seo.home_title", "File a Complaint Against Your Bank or NBFC");
        m.put("seo.home_description",
              "File and track a complaint against a bank, NBFC or payment system operator with the "
                      + "Reserve Bank of India under the Integrated Ombudsman Scheme. Free of cost.");

        m.put("seo.faq_title", "Frequently Asked Questions");
        m.put("seo.faq_description",
              "Answers to common questions about filing a banking complaint with the RBI Ombudsman: "
                      + "who can complain, what it costs, how long it takes and what happens next.");

        m.put("seo.eligibility_title", "Check If You Can File a Complaint");
        m.put("seo.eligibility_description",
              "Answer a few questions to find out whether your grievance against a bank or NBFC can be "
                      + "taken up by the RBI Ombudsman, before you file.");

        m.put("seo.track_title", "Track Your Complaint");
        m.put("seo.track_description",
              "Check the current status of a complaint you have filed with the RBI Ombudsman using your "
                      + "complaint reference number.");

        m.put("seo.login_title", "Sign In to File or Track a Complaint");
        m.put("seo.login_description",
              "Sign in with your mobile number to file a new complaint, track an existing one, or appeal "
                      + "a decision of the RBI Ombudsman.");

        // Non-indexable pages still need titles: a browser tab, a bookmark and a screen reader all use
        // them even when a crawler is told to stay away.
        m.put("seo.file_complaint_title", "File a New Complaint");
        m.put("seo.file_complaint_description", "Submit a new grievance against a regulated entity.");
        m.put("seo.history_title", "My Complaints");
        m.put("seo.history_description", "Complaints you have filed and their current status.");
        m.put("seo.appeal_title", "File an Appeal");
        m.put("seo.appeal_description", "Appeal a decision of the RBI Ombudsman.");
        m.put("seo.feedback_title", "Submit Feedback");
        m.put("seo.feedback_description", "Tell us about your experience with the complaint process.");
        m.put("seo.withdraw_title", "Withdraw a Complaint");
        m.put("seo.withdraw_description", "Withdraw a complaint you have already filed.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "आरबीआई शिकायत प्रबंध प्रणाली");
        m.put("seo.home_title", "अपने बैंक या एनबीएफसी के विरुद्ध शिकायत दर्ज करें");
        m.put("seo.home_description", "एकीकृत लोकपाल योजना के अंतर्गत भारतीय रिज़र्व बैंक में बैंक, एनबीएफसी या भुगतान प्रणाली संचालक के विरुद्ध नि:शुल्क शिकायत दर्ज करें और उसकी स्थिति देखें।");
        m.put("seo.faq_title", "अक्सर पूछे जाने वाले प्रश्न");
        m.put("seo.faq_description", "आरबीआई लोकपाल के पास बैंकिंग शिकायत दर्ज करने से जुड़े सामान्य प्रश्नों के उत्तर: कौन शिकायत कर सकता है, शुल्क कितना है, कितना समय लगता है और आगे क्या होता है।");
        m.put("seo.eligibility_title", "जाँचें कि आप शिकायत दर्ज कर सकते हैं या नहीं");
        m.put("seo.eligibility_description", "शिकायत दर्ज करने से पहले कुछ प्रश्नों के उत्तर देकर जानें कि आपकी शिकायत आरबीआई लोकपाल द्वारा ली जा सकती है या नहीं।");
        m.put("seo.track_title", "अपनी शिकायत की स्थिति देखें");
        m.put("seo.track_description", "अपने शिकायत संदर्भ क्रमांक से आरबीआई लोकपाल में दर्ज शिकायत की वर्तमान स्थिति देखें।");
        m.put("seo.login_title", "शिकायत दर्ज करने या देखने के लिए साइन इन करें");
        m.put("seo.login_description", "नई शिकायत दर्ज करने, पुरानी शिकायत देखने या लोकपाल के निर्णय के विरुद्ध अपील करने के लिए अपने मोबाइल नंबर से साइन इन करें।");
        m.put("seo.file_complaint_title", "नई शिकायत दर्ज करें");
        m.put("seo.file_complaint_description", "विनियमित संस्था के विरुद्ध नई शिकायत प्रस्तुत करें।");
        m.put("seo.history_title", "मेरी शिकायतें");
        m.put("seo.history_description", "आपके द्वारा दर्ज शिकायतें और उनकी वर्तमान स्थिति।");
        m.put("seo.appeal_title", "अपील दर्ज करें");
        m.put("seo.appeal_description", "आरबीआई लोकपाल के निर्णय के विरुद्ध अपील करें।");
        m.put("seo.feedback_title", "प्रतिक्रिया दें");
        m.put("seo.feedback_description", "शिकायत प्रक्रिया के अपने अनुभव के बारे में बताएँ।");
        m.put("seo.withdraw_title", "शिकायत वापस लें");
        m.put("seo.withdraw_description", "पहले दर्ज की गई शिकायत वापस लें।");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "আরবিআই অভিযোগ ব্যবস্থাপনা প্রণালী");
        m.put("seo.home_title", "আপনার ব্যাঙ্ক বা এনবিএফসি-র বিরুদ্ধে অভিযোগ দাখিল করুন");
        m.put("seo.home_description", "সমন্বিত ন্যায়পাল প্রকল্পের অধীনে ভারতীয় রিজার্ভ ব্যাঙ্কে ব্যাঙ্ক, এনবিএফসি বা পেমেন্ট সিস্টেম অপারেটরের বিরুদ্ধে বিনামূল্যে অভিযোগ দাখিল করুন ও তার অবস্থা দেখুন।");
        m.put("seo.faq_title", "সাধারণ জিজ্ঞাসা");
        m.put("seo.faq_description", "আরবিআই ন্যায়পালের কাছে ব্যাঙ্কিং অভিযোগ দাখিল সম্পর্কে সাধারণ প্রশ্নের উত্তর: কে অভিযোগ করতে পারেন, খরচ কত, কত সময় লাগে এবং এরপর কী হয়।");
        m.put("seo.eligibility_title", "আপনি অভিযোগ দাখিল করতে পারেন কিনা দেখুন");
        m.put("seo.eligibility_description", "অভিযোগ দাখিলের আগে কয়েকটি প্রশ্নের উত্তর দিয়ে জানুন আপনার অভিযোগ আরবিআই ন্যায়পাল গ্রহণ করতে পারেন কিনা।");
        m.put("seo.track_title", "আপনার অভিযোগের অবস্থা দেখুন");
        m.put("seo.track_description", "আপনার অভিযোগ রেফারেন্স নম্বর দিয়ে আরবিআই ন্যায়পালে দাখিল করা অভিযোগের বর্তমান অবস্থা দেখুন।");
        m.put("seo.login_title", "অভিযোগ দাখিল বা দেখতে সাইন ইন করুন");
        m.put("seo.login_description", "নতুন অভিযোগ দাখিল, পুরনো অভিযোগ দেখা বা ন্যায়পালের সিদ্ধান্তের বিরুদ্ধে আপিল করতে আপনার মোবাইল নম্বর দিয়ে সাইন ইন করুন।");
        m.put("seo.file_complaint_title", "নতুন অভিযোগ দাখিল করুন");
        m.put("seo.file_complaint_description", "নিয়ন্ত্রিত সংস্থার বিরুদ্ধে নতুন অভিযোগ জমা দিন।");
        m.put("seo.history_title", "আমার অভিযোগ");
        m.put("seo.history_description", "আপনার দাখিল করা অভিযোগ ও তার বর্তমান অবস্থা।");
        m.put("seo.appeal_title", "আপিল দাখিল করুন");
        m.put("seo.appeal_description", "আরবিআই ন্যায়পালের সিদ্ধান্তের বিরুদ্ধে আপিল করুন।");
        m.put("seo.feedback_title", "মতামত দিন");
        m.put("seo.feedback_description", "অভিযোগ প্রক্রিয়া নিয়ে আপনার অভিজ্ঞতা জানান।");
        m.put("seo.withdraw_title", "অভিযোগ প্রত্যাহার করুন");
        m.put("seo.withdraw_description", "আগে দাখিল করা অভিযোগ প্রত্যাহার করুন।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "आरबीआय तक्रार व्यवस्थापन प्रणाली");
        m.put("seo.home_title", "तुमच्या बँक किंवा एनबीएफसीविरुद्ध तक्रार नोंदवा");
        m.put("seo.home_description", "एकीकृत लोकपाल योजनेअंतर्गत भारतीय रिझर्व्ह बँकेकडे बँक, एनबीएफसी किंवा पेमेंट सिस्टम ऑपरेटरविरुद्ध विनामूल्य तक्रार नोंदवा आणि तिची स्थिती पहा.");
        m.put("seo.faq_title", "वारंवार विचारले जाणारे प्रश्न");
        m.put("seo.faq_description", "आरबीआय लोकपालकडे बँकिंग तक्रार नोंदवण्याबाबत सामान्य प्रश्नांची उत्तरे: कोण तक्रार करू शकतो, खर्च किती, किती वेळ लागतो आणि पुढे काय होते.");
        m.put("seo.eligibility_title", "तुम्ही तक्रार नोंदवू शकता का ते तपासा");
        m.put("seo.eligibility_description", "तक्रार नोंदवण्यापूर्वी काही प्रश्नांची उत्तरे देऊन तुमची तक्रार आरबीआय लोकपाल घेऊ शकतात का ते जाणून घ्या.");
        m.put("seo.track_title", "तुमच्या तक्रारीची स्थिती पहा");
        m.put("seo.track_description", "तुमच्या तक्रार संदर्भ क्रमांकाने आरबीआय लोकपालकडे नोंदवलेल्या तक्रारीची सध्याची स्थिती पहा.");
        m.put("seo.login_title", "तक्रार नोंदवण्यासाठी किंवा पाहण्यासाठी साइन इन करा");
        m.put("seo.login_description", "नवीन तक्रार नोंदवण्यासाठी, जुनी तक्रार पाहण्यासाठी किंवा लोकपालाच्या निर्णयाविरुद्ध अपील करण्यासाठी तुमच्या मोबाइल क्रमांकाने साइन इन करा.");
        m.put("seo.file_complaint_title", "नवीन तक्रार नोंदवा");
        m.put("seo.file_complaint_description", "नियंत्रित संस्थेविरुद्ध नवीन तक्रार सादर करा.");
        m.put("seo.history_title", "माझ्या तक्रारी");
        m.put("seo.history_description", "तुम्ही नोंदवलेल्या तक्रारी आणि त्यांची सध्याची स्थिती.");
        m.put("seo.appeal_title", "अपील नोंदवा");
        m.put("seo.appeal_description", "आरबीआय लोकपालाच्या निर्णयाविरुद्ध अपील करा.");
        m.put("seo.feedback_title", "अभिप्राय द्या");
        m.put("seo.feedback_description", "तक्रार प्रक्रियेबाबतचा तुमचा अनुभव सांगा.");
        m.put("seo.withdraw_title", "तक्रार मागे घ्या");
        m.put("seo.withdraw_description", "आधी नोंदवलेली तक्रार मागे घ्या.");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "ఆర్‌బీఐ ఫిర్యాదు నిర్వహణ వ్యవస్థ");
        m.put("seo.home_title", "మీ బ్యాంక్ లేదా ఎన్‌బీఎఫ్‌సీపై ఫిర్యాదు చేయండి");
        m.put("seo.home_description", "సమగ్ర అంబుడ్స్‌మన్ పథకం కింద భారతీయ రిజర్వ్ బ్యాంక్‌కు బ్యాంక్, ఎన్‌బీఎఫ్‌సీ లేదా చెల్లింపు వ్యవస్థ నిర్వాహకుడిపై ఉచితంగా ఫిర్యాదు చేయండి, స్థితిని చూడండి.");
        m.put("seo.faq_title", "తరచుగా అడిగే ప్రశ్నలు");
        m.put("seo.faq_description", "ఆర్‌బీఐ అంబుడ్స్‌మన్‌కు బ్యాంకింగ్ ఫిర్యాదు చేయడంపై సాధారణ ప్రశ్నలకు సమాధానాలు: ఎవరు ఫిర్యాదు చేయగలరు, ఖర్చు ఎంత, ఎంత సమయం పడుతుంది, తరువాత ఏమి జరుగుతుంది.");
        m.put("seo.eligibility_title", "మీరు ఫిర్యాదు చేయగలరో లేదో తెలుసుకోండి");
        m.put("seo.eligibility_description", "ఫిర్యాదు చేసే ముందు కొన్ని ప్రశ్నలకు సమాధానమిచ్చి మీ ఫిర్యాదును ఆర్‌బీఐ అంబుడ్స్‌మన్ స్వీకరించగలరో లేదో తెలుసుకోండి.");
        m.put("seo.track_title", "మీ ఫిర్యాదు స్థితిని చూడండి");
        m.put("seo.track_description", "మీ ఫిర్యాదు రిఫరెన్స్ నంబర్‌తో ఆర్‌బీఐ అంబుడ్స్‌మన్‌కు చేసిన ఫిర్యాదు ప్రస్తుత స్థితిని చూడండి.");
        m.put("seo.login_title", "ఫిర్యాదు చేయడానికి లేదా చూడడానికి సైన్ ఇన్ చేయండి");
        m.put("seo.login_description", "కొత్త ఫిర్యాదు చేయడానికి, పాత ఫిర్యాదు చూడడానికి లేదా అంబుడ్స్‌మన్ నిర్ణయంపై అప్పీలు చేయడానికి మీ మొబైల్ నంబర్‌తో సైన్ ఇన్ చేయండి.");
        m.put("seo.file_complaint_title", "కొత్త ఫిర్యాదు చేయండి");
        m.put("seo.file_complaint_description", "నియంత్రిత సంస్థపై కొత్త ఫిర్యాదు సమర్పించండి.");
        m.put("seo.history_title", "నా ఫిర్యాదులు");
        m.put("seo.history_description", "మీరు చేసిన ఫిర్యాదులు మరియు వాటి ప్రస్తుత స్థితి.");
        m.put("seo.appeal_title", "అప్పీలు చేయండి");
        m.put("seo.appeal_description", "ఆర్‌బీఐ అంబుడ్స్‌మన్ నిర్ణయంపై అప్పీలు చేయండి.");
        m.put("seo.feedback_title", "అభిప్రాయం తెలపండి");
        m.put("seo.feedback_description", "ఫిర్యాదు ప్రక్రియపై మీ అనుభవాన్ని తెలపండి.");
        m.put("seo.withdraw_title", "ఫిర్యాదు ఉపసంహరించండి");
        m.put("seo.withdraw_description", "ఇదివరకు చేసిన ఫిర్యాదును ఉపసంహరించండి.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "ஆர்பிஐ புகார் மேலாண்மை அமைப்பு");
        m.put("seo.home_title", "உங்கள் வங்கி அல்லது என்பிஎஃப்சி மீது புகார் அளிக்கவும்");
        m.put("seo.home_description", "ஒருங்கிணைந்த நியாயவாதி திட்டத்தின் கீழ் இந்திய ரிசர்வ் வங்கியில் வங்கி, என்பிஎஃப்சி அல்லது பணப் பரிமாற்ற நிறுவனம் மீது இலவசமாக புகார் அளித்து நிலையைப் பாருங்கள்.");
        m.put("seo.faq_title", "அடிக்கடி கேட்கப்படும் கேள்விகள்");
        m.put("seo.faq_description", "ஆர்பிஐ நியாயவாதியிடம் வங்கிப் புகார் அளிப்பது பற்றிய பொதுவான கேள்விகளுக்கு பதில்கள்: யார் புகார் அளிக்கலாம், கட்டணம் எவ்வளவு, எவ்வளவு காலம் ஆகும், அதன் பிறகு என்ன நடக்கும்.");
        m.put("seo.eligibility_title", "நீங்கள் புகார் அளிக்க முடியுமா என்று சரிபார்க்கவும்");
        m.put("seo.eligibility_description", "புகார் அளிப்பதற்கு முன் சில கேள்விகளுக்கு பதிலளித்து உங்கள் புகாரை ஆர்பிஐ நியாயவாதி ஏற்க முடியுமா என்று அறிந்து கொள்ளுங்கள்.");
        m.put("seo.track_title", "உங்கள் புகாரின் நிலையைப் பாருங்கள்");
        m.put("seo.track_description", "உங்கள் புகார் குறிப்பு எண்ணைப் பயன்படுத்தி ஆர்பிஐ நியாயவாதியிடம் அளித்த புகாரின் தற்போதைய நிலையைப் பாருங்கள்.");
        m.put("seo.login_title", "புகார் அளிக்க அல்லது பார்க்க உள்நுழையவும்");
        m.put("seo.login_description", "புதிய புகார் அளிக்க, பழைய புகாரைப் பார்க்க அல்லது நியாயவாதியின் முடிவுக்கு எதிராக மேல்முறையீடு செய்ய உங்கள் கைபேசி எண்ணுடன் உள்நுழையவும்.");
        m.put("seo.file_complaint_title", "புதிய புகார் அளிக்கவும்");
        m.put("seo.file_complaint_description", "ஒழுங்குபடுத்தப்பட்ட நிறுவனம் மீது புதிய புகாரைச் சமர்ப்பிக்கவும்.");
        m.put("seo.history_title", "எனது புகார்கள்");
        m.put("seo.history_description", "நீங்கள் அளித்த புகார்கள் மற்றும் அவற்றின் தற்போதைய நிலை.");
        m.put("seo.appeal_title", "மேல்முறையீடு செய்யவும்");
        m.put("seo.appeal_description", "ஆர்பிஐ நியாயவாதியின் முடிவுக்கு எதிராக மேல்முறையீடு செய்யவும்.");
        m.put("seo.feedback_title", "கருத்து தெரிவிக்கவும்");
        m.put("seo.feedback_description", "புகார் நடைமுறை பற்றிய உங்கள் அனுபவத்தைத் தெரிவிக்கவும்.");
        m.put("seo.withdraw_title", "புகாரை திரும்பப் பெறவும்");
        m.put("seo.withdraw_description", "ஏற்கனவே அளித்த புகாரைத் திரும்பப் பெறவும்.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "આરબીઆઈ ફરિયાદ વ્યવસ્થાપન પ્રણાલી");
        m.put("seo.home_title", "તમારી બેંક અથવા એનબીએફસી વિરુદ્ધ ફરિયાદ નોંધાવો");
        m.put("seo.home_description", "એકીકૃત લોકપાલ યોજના હેઠળ ભારતીય રિઝર્વ બેંકમાં બેંક, એનબીએફસી અથવા પેમેન્ટ સિસ્ટમ સંચાલક વિરુદ્ધ વિનામૂલ્યે ફરિયાદ નોંધાવો અને સ્થિતિ જુઓ.");
        m.put("seo.faq_title", "વારંવાર પૂછાતા પ્રશ્નો");
        m.put("seo.faq_description", "આરબીઆઈ લોકપાલ પાસે બેંકિંગ ફરિયાદ નોંધાવવા વિશે સામાન્ય પ્રશ્નોના જવાબ: કોણ ફરિયાદ કરી શકે, ખર્ચ કેટલો, કેટલો સમય લાગે અને પછી શું થાય.");
        m.put("seo.eligibility_title", "તપાસો કે તમે ફરિયાદ નોંધાવી શકો છો કે નહીં");
        m.put("seo.eligibility_description", "ફરિયાદ નોંધાવતા પહેલાં કેટલાક પ્રશ્નોના જવાબ આપી જાણો કે તમારી ફરિયાદ આરબીઆઈ લોકપાલ સ્વીકારી શકે છે કે નહીં.");
        m.put("seo.track_title", "તમારી ફરિયાદની સ્થિતિ જુઓ");
        m.put("seo.track_description", "તમારા ફરિયાદ સંદર્ભ ક્રમાંક દ્વારા આરબીઆઈ લોકપાલમાં નોંધાયેલી ફરિયાદની વર્તમાન સ્થિતિ જુઓ.");
        m.put("seo.login_title", "ફરિયાદ નોંધાવવા અથવા જોવા સાઇન ઇન કરો");
        m.put("seo.login_description", "નવી ફરિયાદ નોંધાવવા, જૂની ફરિયાદ જોવા અથવા લોકપાલના નિર્ણય વિરુદ્ધ અપીલ કરવા તમારા મોબાઇલ નંબરથી સાઇન ઇન કરો.");
        m.put("seo.file_complaint_title", "નવી ફરિયાદ નોંધાવો");
        m.put("seo.file_complaint_description", "નિયંત્રિત સંસ્થા વિરુદ્ધ નવી ફરિયાદ સબમિટ કરો.");
        m.put("seo.history_title", "મારી ફરિયાદો");
        m.put("seo.history_description", "તમે નોંધાવેલી ફરિયાદો અને તેમની વર્તમાન સ્થિતિ.");
        m.put("seo.appeal_title", "અપીલ નોંધાવો");
        m.put("seo.appeal_description", "આરબીઆઈ લોકપાલના નિર્ણય વિરુદ્ધ અપીલ કરો.");
        m.put("seo.feedback_title", "પ્રતિસાદ આપો");
        m.put("seo.feedback_description", "ફરિયાદ પ્રક્રિયા વિશે તમારો અનુભવ જણાવો.");
        m.put("seo.withdraw_title", "ફરિયાદ પાછી ખેંચો");
        m.put("seo.withdraw_description", "પહેલાં નોંધાવેલી ફરિયાદ પાછી ખેંચો.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "آر بی آئی شکایت انتظامی نظام");
        m.put("seo.home_title", "اپنے بینک یا این بی ایف سی کے خلاف شکایت درج کریں");
        m.put("seo.home_description", "مربوط محتسب اسکیم کے تحت ریزرو بینک آف انڈیا میں بینک، این بی ایف سی یا ادائیگی نظام آپریٹر کے خلاف مفت شکایت درج کریں اور اس کی حالت دیکھیں۔");
        m.put("seo.faq_title", "عام پوچھے جانے والے سوالات");
        m.put("seo.faq_description", "آر بی آئی محتسب کے پاس بینکنگ شکایت درج کرنے سے متعلق عام سوالات کے جوابات: کون شکایت کر سکتا ہے، خرچ کتنا ہے، کتنا وقت لگتا ہے اور اس کے بعد کیا ہوتا ہے۔");
        m.put("seo.eligibility_title", "جانچیں کہ آپ شکایت درج کر سکتے ہیں یا نہیں");
        m.put("seo.eligibility_description", "شکایت درج کرنے سے پہلے چند سوالات کے جواب دے کر جانیں کہ آپ کی شکایت آر بی آئی محتسب لے سکتے ہیں یا نہیں۔");
        m.put("seo.track_title", "اپنی شکایت کی حالت دیکھیں");
        m.put("seo.track_description", "اپنے شکایت حوالہ نمبر سے آر بی آئی محتسب میں درج شکایت کی موجودہ حالت دیکھیں۔");
        m.put("seo.login_title", "شکایت درج کرنے یا دیکھنے کے لیے سائن ان کریں");
        m.put("seo.login_description", "نئی شکایت درج کرنے، پرانی شکایت دیکھنے یا محتسب کے فیصلے کے خلاف اپیل کرنے کے لیے اپنے موبائل نمبر سے سائن ان کریں۔");
        m.put("seo.file_complaint_title", "نئی شکایت درج کریں");
        m.put("seo.file_complaint_description", "ریگولیٹڈ ادارے کے خلاف نئی شکایت جمع کریں۔");
        m.put("seo.history_title", "میری شکایات");
        m.put("seo.history_description", "آپ کی درج کردہ شکایات اور ان کی موجودہ حالت۔");
        m.put("seo.appeal_title", "اپیل درج کریں");
        m.put("seo.appeal_description", "آر بی آئی محتسب کے فیصلے کے خلاف اپیل کریں۔");
        m.put("seo.feedback_title", "رائے دیں");
        m.put("seo.feedback_description", "شکایت کے عمل کے بارے میں اپنا تجربہ بتائیں۔");
        m.put("seo.withdraw_title", "شکایت واپس لیں");
        m.put("seo.withdraw_description", "پہلے درج کی گئی شکایت واپس لیں۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "ಆರ್‌ಬಿಐ ದೂರು ನಿರ್ವಹಣಾ ವ್ಯವಸ್ಥೆ");
        m.put("seo.home_title", "ನಿಮ್ಮ ಬ್ಯಾಂಕ್ ಅಥವಾ ಎನ್‌ಬಿಎಫ್‌ಸಿ ವಿರುದ್ಧ ದೂರು ದಾಖಲಿಸಿ");
        m.put("seo.home_description", "ಸಮಗ್ರ ಒಂಬುಡ್ಸ್‌ಮನ್ ಯೋಜನೆಯಡಿ ಭಾರತೀಯ ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್‌ಗೆ ಬ್ಯಾಂಕ್, ಎನ್‌ಬಿಎಫ್‌ಸಿ ಅಥವಾ ಪಾವತಿ ವ್ಯವಸ್ಥೆ ನಿರ್ವಾಹಕರ ವಿರುದ್ಧ ಉಚಿತವಾಗಿ ದೂರು ದಾಖಲಿಸಿ ಮತ್ತು ಸ್ಥಿತಿ ನೋಡಿ.");
        m.put("seo.faq_title", "ಪದೇ ಪದೇ ಕೇಳಲಾಗುವ ಪ್ರಶ್ನೆಗಳು");
        m.put("seo.faq_description", "ಆರ್‌ಬಿಐ ಒಂಬುಡ್ಸ್‌ಮನ್‌ಗೆ ಬ್ಯಾಂಕಿಂಗ್ ದೂರು ದಾಖಲಿಸುವ ಬಗ್ಗೆ ಸಾಮಾನ್ಯ ಪ್ರಶ್ನೆಗಳಿಗೆ ಉತ್ತರಗಳು: ಯಾರು ದೂರು ನೀಡಬಹುದು, ವೆಚ್ಚ ಎಷ್ಟು, ಎಷ್ಟು ಸಮಯ ಬೇಕು ಮತ್ತು ಮುಂದೆ ಏನಾಗುತ್ತದೆ.");
        m.put("seo.eligibility_title", "ನೀವು ದೂರು ದಾಖಲಿಸಬಹುದೇ ಎಂದು ಪರಿಶೀಲಿಸಿ");
        m.put("seo.eligibility_description", "ದೂರು ದಾಖಲಿಸುವ ಮೊದಲು ಕೆಲವು ಪ್ರಶ್ನೆಗಳಿಗೆ ಉತ್ತರಿಸಿ ನಿಮ್ಮ ದೂರನ್ನು ಆರ್‌ಬಿಐ ಒಂಬುಡ್ಸ್‌ಮನ್ ಸ್ವೀಕರಿಸಬಹುದೇ ಎಂದು ತಿಳಿಯಿರಿ.");
        m.put("seo.track_title", "ನಿಮ್ಮ ದೂರಿನ ಸ್ಥಿತಿ ನೋಡಿ");
        m.put("seo.track_description", "ನಿಮ್ಮ ದೂರು ಉಲ್ಲೇಖ ಸಂಖ್ಯೆಯಿಂದ ಆರ್‌ಬಿಐ ಒಂಬುಡ್ಸ್‌ಮನ್‌ಗೆ ದಾಖಲಿಸಿದ ದೂರಿನ ಪ್ರಸ್ತುತ ಸ್ಥಿತಿ ನೋಡಿ.");
        m.put("seo.login_title", "ದೂರು ದಾಖಲಿಸಲು ಅಥವಾ ನೋಡಲು ಸೈನ್ ಇನ್ ಮಾಡಿ");
        m.put("seo.login_description", "ಹೊಸ ದೂರು ದಾಖಲಿಸಲು, ಹಳೆಯ ದೂರು ನೋಡಲು ಅಥವಾ ಒಂಬುಡ್ಸ್‌ಮನ್ ನಿರ್ಧಾರದ ವಿರುದ್ಧ ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಲು ನಿಮ್ಮ ಮೊಬೈಲ್ ಸಂಖ್ಯೆಯಿಂದ ಸೈನ್ ಇನ್ ಮಾಡಿ.");
        m.put("seo.file_complaint_title", "ಹೊಸ ದೂರು ದಾಖಲಿಸಿ");
        m.put("seo.file_complaint_description", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ವಿರುದ್ಧ ಹೊಸ ದೂರು ಸಲ್ಲಿಸಿ.");
        m.put("seo.history_title", "ನನ್ನ ದೂರುಗಳು");
        m.put("seo.history_description", "ನೀವು ದಾಖಲಿಸಿದ ದೂರುಗಳು ಮತ್ತು ಅವುಗಳ ಪ್ರಸ್ತುತ ಸ್ಥಿತಿ.");
        m.put("seo.appeal_title", "ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಿ");
        m.put("seo.appeal_description", "ಆರ್‌ಬಿಐ ಒಂಬುಡ್ಸ್‌ಮನ್ ನಿರ್ಧಾರದ ವಿರುದ್ಧ ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಿ.");
        m.put("seo.feedback_title", "ಪ್ರತಿಕ್ರಿಯೆ ನೀಡಿ");
        m.put("seo.feedback_description", "ದೂರು ಪ್ರಕ್ರಿಯೆಯ ಬಗ್ಗೆ ನಿಮ್ಮ ಅನುಭವ ತಿಳಿಸಿ.");
        m.put("seo.withdraw_title", "ದೂರು ಹಿಂಪಡೆಯಿರಿ");
        m.put("seo.withdraw_description", "ಈಗಾಗಲೇ ದಾಖಲಿಸಿದ ದೂರನ್ನು ಹಿಂಪಡೆಯಿರಿ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "ആർബിഐ പരാതി പരിപാലന സംവിധാനം");
        m.put("seo.home_title", "നിങ്ങളുടെ ബാങ്കിനോ എൻബിഎഫ്‌സിക്കോ എതിരെ പരാതി നൽകുക");
        m.put("seo.home_description", "സംയോജിത ഓംബുഡ്സ്മാൻ പദ്ധതിക്ക് കീഴിൽ റിസർവ് ബാങ്ക് ഓഫ് ഇന്ത്യയിൽ ബാങ്ക്, എൻബിഎഫ്‌സി അല്ലെങ്കിൽ പേയ്‌മെന്റ് സിസ്റ്റം ഓപ്പറേറ്റർക്കെതിരെ സൗജന്യമായി പരാതി നൽകുകയും നില അറിയുകയും ചെയ്യുക.");
        m.put("seo.faq_title", "പതിവ് ചോദ്യങ്ങൾ");
        m.put("seo.faq_description", "ആർബിഐ ഓംബുഡ്സ്മാന് ബാങ്കിംഗ് പരാതി നൽകുന്നതിനെക്കുറിച്ചുള്ള സാധാരണ ചോദ്യങ്ങൾക്കുള്ള ഉത്തരങ്ങൾ: ആർക്ക് പരാതി നൽകാം, ചെലവ് എത്ര, എത്ര സമയം എടുക്കും, പിന്നീട് എന്ത് സംഭവിക്കും.");
        m.put("seo.eligibility_title", "നിങ്ങൾക്ക് പരാതി നൽകാമോ എന്ന് പരിശോധിക്കുക");
        m.put("seo.eligibility_description", "പരാതി നൽകുന്നതിന് മുമ്പ് ചില ചോദ്യങ്ങൾക്ക് ഉത്തരം നൽകി നിങ്ങളുടെ പരാതി ആർബിഐ ഓംബുഡ്സ്മാന് സ്വീകരിക്കാനാകുമോ എന്ന് അറിയുക.");
        m.put("seo.track_title", "നിങ്ങളുടെ പരാതിയുടെ നില അറിയുക");
        m.put("seo.track_description", "നിങ്ങളുടെ പരാതി റഫറൻസ് നമ്പർ ഉപയോഗിച്ച് ആർബിഐ ഓംബുഡ്സ്മാന് നൽകിയ പരാതിയുടെ നിലവിലെ സ്ഥിതി അറിയുക.");
        m.put("seo.login_title", "പരാതി നൽകാനോ കാണാനോ സൈൻ ഇൻ ചെയ്യുക");
        m.put("seo.login_description", "പുതിയ പരാതി നൽകാനോ പഴയ പരാതി കാണാനോ ഓംബുഡ്സ്മാന്റെ തീരുമാനത്തിനെതിരെ അപ്പീൽ നൽകാനോ നിങ്ങളുടെ മൊബൈൽ നമ്പർ ഉപയോഗിച്ച് സൈൻ ഇൻ ചെയ്യുക.");
        m.put("seo.file_complaint_title", "പുതിയ പരാതി നൽകുക");
        m.put("seo.file_complaint_description", "നിയന്ത്രിത സ്ഥാപനത്തിനെതിരെ പുതിയ പരാതി സമർപ്പിക്കുക.");
        m.put("seo.history_title", "എന്റെ പരാതികൾ");
        m.put("seo.history_description", "നിങ്ങൾ നൽകിയ പരാതികളും അവയുടെ നിലവിലെ സ്ഥിതിയും.");
        m.put("seo.appeal_title", "അപ്പീൽ നൽകുക");
        m.put("seo.appeal_description", "ആർബിഐ ഓംബുഡ്സ്മാന്റെ തീരുമാനത്തിനെതിരെ അപ്പീൽ നൽകുക.");
        m.put("seo.feedback_title", "അഭിപ്രായം അറിയിക്കുക");
        m.put("seo.feedback_description", "പരാതി നടപടിക്രമത്തെക്കുറിച്ചുള്ള നിങ്ങളുടെ അനുഭവം അറിയിക്കുക.");
        m.put("seo.withdraw_title", "പരാതി പിൻവലിക്കുക");
        m.put("seo.withdraw_description", "നേരത്തെ നൽകിയ പരാതി പിൻവലിക്കുക.");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("seo.site_name", "ਆਰਬੀਆਈ ਸ਼ਿਕਾਇਤ ਪ੍ਰਬੰਧਨ ਪ੍ਰਣਾਲੀ");
        m.put("seo.home_title", "ਆਪਣੇ ਬੈਂਕ ਜਾਂ ਐਨਬੀਐਫਸੀ ਵਿਰੁੱਧ ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰੋ");
        m.put("seo.home_description", "ਏਕੀਕ੍ਰਿਤ ਲੋਕਪਾਲ ਯੋਜਨਾ ਅਧੀਨ ਭਾਰਤੀ ਰਿਜ਼ਰਵ ਬੈਂਕ ਵਿੱਚ ਬੈਂਕ, ਐਨਬੀਐਫਸੀ ਜਾਂ ਭੁਗਤਾਨ ਪ੍ਰਣਾਲੀ ਸੰਚਾਲਕ ਵਿਰੁੱਧ ਮੁਫ਼ਤ ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰੋ ਅਤੇ ਸਥਿਤੀ ਵੇਖੋ।");
        m.put("seo.faq_title", "ਅਕਸਰ ਪੁੱਛੇ ਜਾਂਦੇ ਸਵਾਲ");
        m.put("seo.faq_description", "ਆਰਬੀਆਈ ਲੋਕਪਾਲ ਕੋਲ ਬੈਂਕਿੰਗ ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰਨ ਬਾਰੇ ਆਮ ਸਵਾਲਾਂ ਦੇ ਜਵਾਬ: ਕੌਣ ਸ਼ਿਕਾਇਤ ਕਰ ਸਕਦਾ ਹੈ, ਖਰਚਾ ਕਿੰਨਾ ਹੈ, ਕਿੰਨਾ ਸਮਾਂ ਲੱਗਦਾ ਹੈ ਅਤੇ ਅੱਗੇ ਕੀ ਹੁੰਦਾ ਹੈ।");
        m.put("seo.eligibility_title", "ਜਾਂਚੋ ਕਿ ਤੁਸੀਂ ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰ ਸਕਦੇ ਹੋ ਜਾਂ ਨਹੀਂ");
        m.put("seo.eligibility_description", "ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰਨ ਤੋਂ ਪਹਿਲਾਂ ਕੁਝ ਸਵਾਲਾਂ ਦੇ ਜਵਾਬ ਦੇ ਕੇ ਜਾਣੋ ਕਿ ਤੁਹਾਡੀ ਸ਼ਿਕਾਇਤ ਆਰਬੀਆਈ ਲੋਕਪਾਲ ਲੈ ਸਕਦੇ ਹਨ ਜਾਂ ਨਹੀਂ।");
        m.put("seo.track_title", "ਆਪਣੀ ਸ਼ਿਕਾਇਤ ਦੀ ਸਥਿਤੀ ਵੇਖੋ");
        m.put("seo.track_description", "ਆਪਣੇ ਸ਼ਿਕਾਇਤ ਹਵਾਲਾ ਨੰਬਰ ਨਾਲ ਆਰਬੀਆਈ ਲੋਕਪਾਲ ਕੋਲ ਦਰਜ ਸ਼ਿਕਾਇਤ ਦੀ ਮੌਜੂਦਾ ਸਥਿਤੀ ਵੇਖੋ।");
        m.put("seo.login_title", "ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰਨ ਜਾਂ ਵੇਖਣ ਲਈ ਸਾਈਨ ਇਨ ਕਰੋ");
        m.put("seo.login_description", "ਨਵੀਂ ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰਨ, ਪੁਰਾਣੀ ਸ਼ਿਕਾਇਤ ਵੇਖਣ ਜਾਂ ਲੋਕਪਾਲ ਦੇ ਫ਼ੈਸਲੇ ਵਿਰੁੱਧ ਅਪੀਲ ਕਰਨ ਲਈ ਆਪਣੇ ਮੋਬਾਈਲ ਨੰਬਰ ਨਾਲ ਸਾਈਨ ਇਨ ਕਰੋ।");
        m.put("seo.file_complaint_title", "ਨਵੀਂ ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰੋ");
        m.put("seo.file_complaint_description", "ਨਿਯੰਤ੍ਰਿਤ ਸੰਸਥਾ ਵਿਰੁੱਧ ਨਵੀਂ ਸ਼ਿਕਾਇਤ ਜਮ੍ਹਾਂ ਕਰੋ।");
        m.put("seo.history_title", "ਮੇਰੀਆਂ ਸ਼ਿਕਾਇਤਾਂ");
        m.put("seo.history_description", "ਤੁਹਾਡੇ ਵੱਲੋਂ ਦਰਜ ਸ਼ਿਕਾਇਤਾਂ ਅਤੇ ਉਨ੍ਹਾਂ ਦੀ ਮੌਜੂਦਾ ਸਥਿਤੀ।");
        m.put("seo.appeal_title", "ਅਪੀਲ ਦਰਜ ਕਰੋ");
        m.put("seo.appeal_description", "ਆਰਬੀਆਈ ਲੋਕਪਾਲ ਦੇ ਫ਼ੈਸਲੇ ਵਿਰੁੱਧ ਅਪੀਲ ਕਰੋ।");
        m.put("seo.feedback_title", "ਫੀਡਬੈਕ ਦਿਓ");
        m.put("seo.feedback_description", "ਸ਼ਿਕਾਇਤ ਪ੍ਰਕਿਰਿਆ ਬਾਰੇ ਆਪਣਾ ਅਨੁਭਵ ਦੱਸੋ।");
        m.put("seo.withdraw_title", "ਸ਼ਿਕਾਇਤ ਵਾਪਸ ਲਓ");
        m.put("seo.withdraw_description", "ਪਹਿਲਾਂ ਦਰਜ ਕੀਤੀ ਸ਼ਿਕਾਇਤ ਵਾਪਸ ਲਓ।");
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
