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
 * Translation keys for the RE Activity Status ladder (UST846–UST852), seeded for all ten supported
 * locales.
 *
 * The badge has to render identically on the RE and the RBI side (UST847), which means neither side
 * can hold its own hardcoded English label — both resolve the same key. The RE and RBIO portals were
 * entirely hardcoded English before this, so these are the first keys those screens consume.
 *
 * Insert-if-absent, like the other seeders: correcting a default here does NOT rewrite a row already
 * in the database. Later text corrections need a scoped UPDATE in both migration directories, keyed
 * by code rather than by an English phrase.
 */
@Component
@Order(7)
public class ReActivityTranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public ReActivityTranslationSeeder(TranslationKeyRepository keyRepo, TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedKeys();
        seedLocale("hi", hindi());
        seedLocale("bn", bengali());
        seedLocale("mr", marathi());
        seedLocale("te", telugu());
        seedLocale("ta", tamil());
        seedLocale("gu", gujarati());
        seedLocale("ur", urdu());
        seedLocale("kn", kannada());
        seedLocale("ml", malayalam());
    }

    private void seedKeys() {
        // ═══ The seven ladder levels ═══
        seed("re.activity.not_opened", "Not Opened");
        seed("re.activity.opened", "Opened");
        seed("re.activity.under_review", "Under Review");
        seed("re.activity.response_being_prepared", "Response Being Prepared");
        seed("re.activity.documents_uploaded", "Documents Uploaded");
        seed("re.activity.response_submitted", "Response Submitted");
        seed("re.activity.overdue", "Overdue");

        // ═══ Staff-side panel (UST852) ═══
        seed("re.activity.section_title", "Entity Activity");
        seed("re.activity.last_updated", "Last updated");
        seed("re.activity.never_updated", "No activity recorded yet");
        seed("re.activity.read_only_notice",
             "This status is derived automatically from the entity's actions and cannot be edited.");
        seed("re.activity.history_title", "Activity history");
        seed("re.activity.no_history", "No activity has been recorded for this record yet.");
        seed("re.activity.source_automatic", "System");
        seed("re.activity.source_manual", "User");
        seed("re.activity.nudge_sent", "Follow-up reminder sent");
        seed("re.activity.stuck_for", "Stalled for {days} day(s)");

        // ═══ RE-side (UST846) ═══
        seed("re.activity.draft_saved", "Draft saved");
        seed("re.activity.draft_save_button", "Save Draft");
        seed("re.activity.manual_set_forbidden",
             "RE Activity Status is derived from your actions and cannot be set directly.");
        seed("re.activity.progress_visible_notice",
             "Your progress on this record is visible to the RBI team that assigned it.");

        // ═══ Notifications (UST849, UST850) ═══
        seed("notification.re_activity.overdue", "RE has not responded within the deadline");
        seed("notification.re_activity.nudge", "RE progress has stalled");

        // ═══ Maker-checker configuration (UST851) ═══
        seed("re.activity.config.title", "Nudge Thresholds");
        seed("re.activity.config.threshold_label", "Days before nudging");
        seed("re.activity.config.request_button", "Request Change");
        seed("re.activity.config.approve_button", "Approve");
        seed("re.activity.config.reject_button", "Reject");
        seed("re.activity.config.reason_label", "Reason for the change");
        seed("re.activity.config.pending_approval", "Awaiting approval by another administrator");
        seed("re.activity.config.self_approval_blocked",
             "You raised this change, so it must be approved by a different administrator.");
        seed("re.activity.config.stale_request",
             "This value changed since the request was raised. Please raise a fresh request.");
        seed("re.activity.config.applies_to_future_only",
             "Changing a threshold affects future status changes only. Records already in a status keep the threshold that applied when they entered it.");
        seed("re.activity.config.reason_required_on_reject",
             "A reason is required when rejecting a configuration change.");
    }

    private void seed(String code, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule("re-activity");
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

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "नहीं खोला गया");
        m.put("re.activity.opened", "खोला गया");
        m.put("re.activity.under_review", "समीक्षाधीन");
        m.put("re.activity.response_being_prepared", "उत्तर तैयार किया जा रहा है");
        m.put("re.activity.documents_uploaded", "दस्तावेज़ अपलोड किए गए");
        m.put("re.activity.response_submitted", "उत्तर प्रस्तुत किया गया");
        m.put("re.activity.overdue", "अतिदेय");
        m.put("re.activity.section_title", "संस्था गतिविधि");
        m.put("re.activity.last_updated", "अंतिम अद्यतन");
        m.put("re.activity.never_updated", "अभी तक कोई गतिविधि दर्ज नहीं");
        m.put("re.activity.read_only_notice",
              "यह स्थिति संस्था की कार्रवाइयों से स्वतः निर्धारित होती है और इसे संपादित नहीं किया जा सकता।");
        m.put("re.activity.history_title", "गतिविधि इतिहास");
        m.put("re.activity.no_history", "इस रिकॉर्ड के लिए अभी तक कोई गतिविधि दर्ज नहीं की गई है।");
        m.put("re.activity.source_automatic", "सिस्टम");
        m.put("re.activity.source_manual", "उपयोगकर्ता");
        m.put("re.activity.nudge_sent", "अनुवर्ती अनुस्मारक भेजा गया");
        m.put("re.activity.stuck_for", "{days} दिन से रुका हुआ");
        m.put("re.activity.draft_saved", "प्रारूप सहेजा गया");
        m.put("re.activity.draft_save_button", "प्रारूप सहेजें");
        m.put("re.activity.manual_set_forbidden",
              "आरई गतिविधि स्थिति आपकी कार्रवाइयों से निर्धारित होती है और इसे सीधे निर्धारित नहीं किया जा सकता।");
        m.put("re.activity.progress_visible_notice",
              "इस रिकॉर्ड पर आपकी प्रगति उस आरबीआई टीम को दिखाई देती है जिसने इसे सौंपा है।");
        m.put("notification.re_activity.overdue", "आरई ने समय-सीमा में उत्तर नहीं दिया");
        m.put("notification.re_activity.nudge", "आरई की प्रगति रुक गई है");
        m.put("re.activity.config.title", "अनुस्मारक सीमाएँ");
        m.put("re.activity.config.threshold_label", "अनुस्मारक से पहले दिन");
        m.put("re.activity.config.request_button", "परिवर्तन का अनुरोध करें");
        m.put("re.activity.config.approve_button", "स्वीकृत करें");
        m.put("re.activity.config.reject_button", "अस्वीकार करें");
        m.put("re.activity.config.reason_label", "परिवर्तन का कारण");
        m.put("re.activity.config.pending_approval", "अन्य प्रशासक की स्वीकृति प्रतीक्षित");
        m.put("re.activity.config.self_approval_blocked",
              "आपने यह परिवर्तन प्रस्तावित किया है, इसलिए इसे किसी अन्य प्रशासक द्वारा स्वीकृत किया जाना चाहिए।");
        m.put("re.activity.config.stale_request",
              "अनुरोध उठाए जाने के बाद यह मान बदल गया है। कृपया नया अनुरोध करें।");
        m.put("re.activity.config.applies_to_future_only",
              "सीमा बदलने से केवल भविष्य के स्थिति परिवर्तन प्रभावित होते हैं। जो रिकॉर्ड पहले से किसी स्थिति में हैं, उन पर प्रवेश के समय लागू सीमा ही रहेगी।");
        m.put("re.activity.config.reason_required_on_reject",
              "कॉन्फ़िगरेशन परिवर्तन अस्वीकार करते समय कारण आवश्यक है।");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "খোলা হয়নি");
        m.put("re.activity.opened", "খোলা হয়েছে");
        m.put("re.activity.under_review", "পর্যালোচনাধীন");
        m.put("re.activity.response_being_prepared", "উত্তর প্রস্তুত করা হচ্ছে");
        m.put("re.activity.documents_uploaded", "নথি আপলোড করা হয়েছে");
        m.put("re.activity.response_submitted", "উত্তর জমা দেওয়া হয়েছে");
        m.put("re.activity.overdue", "সময়সীমা উত্তীর্ণ");
        m.put("re.activity.section_title", "সংস্থার কার্যকলাপ");
        m.put("re.activity.last_updated", "সর্বশেষ হালনাগাদ");
        m.put("re.activity.never_updated", "এখনও কোনো কার্যকলাপ নথিভুক্ত হয়নি");
        m.put("re.activity.read_only_notice",
              "এই অবস্থা সংস্থার কার্যক্রম থেকে স্বয়ংক্রিয়ভাবে নির্ধারিত হয় এবং সম্পাদনা করা যায় না।");
        m.put("re.activity.history_title", "কার্যকলাপের ইতিহাস");
        m.put("re.activity.no_history", "এই নথির জন্য এখনও কোনো কার্যকলাপ নথিভুক্ত হয়নি।");
        m.put("re.activity.source_automatic", "সিস্টেম");
        m.put("re.activity.source_manual", "ব্যবহারকারী");
        m.put("re.activity.nudge_sent", "অনুসরণমূলক অনুস্মারক পাঠানো হয়েছে");
        m.put("re.activity.stuck_for", "{days} দিন ধরে থমকে আছে");
        m.put("re.activity.draft_saved", "খসড়া সংরক্ষিত হয়েছে");
        m.put("re.activity.draft_save_button", "খসড়া সংরক্ষণ করুন");
        m.put("re.activity.manual_set_forbidden",
              "আরই কার্যকলাপ অবস্থা আপনার কার্যক্রম থেকে নির্ধারিত হয় এবং সরাসরি নির্ধারণ করা যায় না।");
        m.put("re.activity.progress_visible_notice",
              "এই নথিতে আপনার অগ্রগতি যে আরবিআই দল এটি বরাদ্দ করেছে তারা দেখতে পাবে।");
        m.put("notification.re_activity.overdue", "আরই নির্ধারিত সময়ে উত্তর দেয়নি");
        m.put("notification.re_activity.nudge", "আরই-এর অগ্রগতি থমকে গেছে");
        m.put("re.activity.config.title", "অনুস্মারক সীমা");
        m.put("re.activity.config.threshold_label", "অনুস্মারকের আগে দিন");
        m.put("re.activity.config.request_button", "পরিবর্তনের অনুরোধ করুন");
        m.put("re.activity.config.approve_button", "অনুমোদন করুন");
        m.put("re.activity.config.reject_button", "প্রত্যাখ্যান করুন");
        m.put("re.activity.config.reason_label", "পরিবর্তনের কারণ");
        m.put("re.activity.config.pending_approval", "অন্য প্রশাসকের অনুমোদনের অপেক্ষায়");
        m.put("re.activity.config.self_approval_blocked",
              "আপনি এই পরিবর্তনের অনুরোধ করেছেন, তাই অন্য একজন প্রশাসককে এটি অনুমোদন করতে হবে।");
        m.put("re.activity.config.stale_request",
              "অনুরোধ করার পর এই মান পরিবর্তিত হয়েছে। অনুগ্রহ করে নতুন অনুরোধ করুন।");
        m.put("re.activity.config.applies_to_future_only",
              "সীমা পরিবর্তন কেবল ভবিষ্যতের অবস্থা পরিবর্তনে প্রযোজ্য। যে নথিগুলি ইতিমধ্যে কোনো অবস্থায় রয়েছে সেগুলি প্রবেশের সময়ের সীমা ধরে রাখে।");
        m.put("re.activity.config.reason_required_on_reject",
              "কনফিগারেশন পরিবর্তন প্রত্যাখ্যান করার সময় একটি কারণ প্রয়োজন।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "उघडलेले नाही");
        m.put("re.activity.opened", "उघडले");
        m.put("re.activity.under_review", "पुनरावलोकनाधीन");
        m.put("re.activity.response_being_prepared", "उत्तर तयार केले जात आहे");
        m.put("re.activity.documents_uploaded", "कागदपत्रे अपलोड केली");
        m.put("re.activity.response_submitted", "उत्तर सादर केले");
        m.put("re.activity.overdue", "मुदत उलटली");
        m.put("re.activity.section_title", "संस्थेची कार्यवाही");
        m.put("re.activity.last_updated", "शेवटचे अद्यतन");
        m.put("re.activity.never_updated", "अद्याप कोणतीही कार्यवाही नोंदवली नाही");
        m.put("re.activity.read_only_notice",
              "ही स्थिती संस्थेच्या कृतींवरून स्वयंचलितपणे ठरवली जाते आणि ती संपादित करता येत नाही.");
        m.put("re.activity.history_title", "कार्यवाहीचा इतिहास");
        m.put("re.activity.no_history", "या नोंदीसाठी अद्याप कोणतीही कार्यवाही नोंदवली गेली नाही.");
        m.put("re.activity.source_automatic", "प्रणाली");
        m.put("re.activity.source_manual", "वापरकर्ता");
        m.put("re.activity.nudge_sent", "पाठपुरावा स्मरणपत्र पाठवले");
        m.put("re.activity.stuck_for", "{days} दिवसांपासून थांबले");
        m.put("re.activity.draft_saved", "मसुदा जतन केला");
        m.put("re.activity.draft_save_button", "मसुदा जतन करा");
        m.put("re.activity.manual_set_forbidden",
              "आरई कार्यवाही स्थिती तुमच्या कृतींवरून ठरते आणि ती थेट निश्चित करता येत नाही.");
        m.put("re.activity.progress_visible_notice",
              "या नोंदीवरील तुमची प्रगती ती नेमून दिलेल्या आरबीआय संघाला दिसते.");
        m.put("notification.re_activity.overdue", "आरईने मुदतीत उत्तर दिले नाही");
        m.put("notification.re_activity.nudge", "आरईची प्रगती थांबली आहे");
        m.put("re.activity.config.title", "स्मरणपत्र मर्यादा");
        m.put("re.activity.config.threshold_label", "स्मरणपत्रापूर्वीचे दिवस");
        m.put("re.activity.config.request_button", "बदलाची विनंती करा");
        m.put("re.activity.config.approve_button", "मंजूर करा");
        m.put("re.activity.config.reject_button", "नाकारा");
        m.put("re.activity.config.reason_label", "बदलाचे कारण");
        m.put("re.activity.config.pending_approval", "अन्य प्रशासकाच्या मंजुरीच्या प्रतीक्षेत");
        m.put("re.activity.config.self_approval_blocked",
              "तुम्ही हा बदल सुचवला आहे, म्हणून तो दुसऱ्या प्रशासकाने मंजूर करावा लागेल.");
        m.put("re.activity.config.stale_request",
              "विनंती केल्यानंतर हे मूल्य बदलले आहे. कृपया नवीन विनंती करा.");
        m.put("re.activity.config.applies_to_future_only",
              "मर्यादा बदलल्याने केवळ भविष्यातील स्थिती बदल प्रभावित होतात. आधीच एखाद्या स्थितीत असलेल्या नोंदी प्रवेशाच्या वेळची मर्यादा कायम ठेवतात.");
        m.put("re.activity.config.reason_required_on_reject",
              "कॉन्फिगरेशन बदल नाकारताना कारण आवश्यक आहे.");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "తెరవలేదు");
        m.put("re.activity.opened", "తెరిచారు");
        m.put("re.activity.under_review", "పరిశీలనలో");
        m.put("re.activity.response_being_prepared", "సమాధానం సిద్ధం చేస్తున్నారు");
        m.put("re.activity.documents_uploaded", "పత్రాలు అప్‌లోడ్ చేశారు");
        m.put("re.activity.response_submitted", "సమాధానం సమర్పించారు");
        m.put("re.activity.overdue", "గడువు ముగిసింది");
        m.put("re.activity.section_title", "సంస్థ కార్యకలాపం");
        m.put("re.activity.last_updated", "చివరిగా నవీకరించినది");
        m.put("re.activity.never_updated", "ఇంకా ఎటువంటి కార్యకలాపం నమోదు కాలేదు");
        m.put("re.activity.read_only_notice",
              "ఈ స్థితి సంస్థ చర్యల ఆధారంగా స్వయంచాలకంగా నిర్ణయించబడుతుంది, దీన్ని సవరించలేరు.");
        m.put("re.activity.history_title", "కార్యకలాప చరిత్ర");
        m.put("re.activity.no_history", "ఈ రికార్డు కోసం ఇంకా ఎటువంటి కార్యకలాపం నమోదు కాలేదు.");
        m.put("re.activity.source_automatic", "వ్యవస్థ");
        m.put("re.activity.source_manual", "వినియోగదారు");
        m.put("re.activity.nudge_sent", "అనుసరణ గుర్తుంపు పంపబడింది");
        m.put("re.activity.stuck_for", "{days} రోజుల నుండి నిలిచిపోయింది");
        m.put("re.activity.draft_saved", "ముసాయిదా సేవ్ చేయబడింది");
        m.put("re.activity.draft_save_button", "ముసాయిదా సేవ్ చేయండి");
        m.put("re.activity.manual_set_forbidden",
              "ఆర్‌ఇ కార్యకలాప స్థితి మీ చర్యల ఆధారంగా నిర్ణయించబడుతుంది, నేరుగా సెట్ చేయలేరు.");
        m.put("re.activity.progress_visible_notice",
              "ఈ రికార్డుపై మీ పురోగతి దీన్ని కేటాయించిన ఆర్‌బీఐ బృందానికి కనిపిస్తుంది.");
        m.put("notification.re_activity.overdue", "ఆర్‌ఇ గడువులోపు సమాధానం ఇవ్వలేదు");
        m.put("notification.re_activity.nudge", "ఆర్‌ఇ పురోగతి నిలిచిపోయింది");
        m.put("re.activity.config.title", "గుర్తుంపు పరిమితులు");
        m.put("re.activity.config.threshold_label", "గుర్తుంపుకు ముందు రోజులు");
        m.put("re.activity.config.request_button", "మార్పును అభ్యర్థించండి");
        m.put("re.activity.config.approve_button", "ఆమోదించండి");
        m.put("re.activity.config.reject_button", "తిరస్కరించండి");
        m.put("re.activity.config.reason_label", "మార్పుకు కారణం");
        m.put("re.activity.config.pending_approval", "మరో నిర్వాహకుని ఆమోదం కోసం వేచి ఉంది");
        m.put("re.activity.config.self_approval_blocked",
              "ఈ మార్పును మీరు ప్రతిపాదించారు, కాబట్టి దీన్ని మరో నిర్వాహకుడు ఆమోదించాలి.");
        m.put("re.activity.config.stale_request",
              "అభ్యర్థన చేసిన తర్వాత ఈ విలువ మారింది. కొత్త అభ్యర్థన చేయండి.");
        m.put("re.activity.config.applies_to_future_only",
              "పరిమితి మార్పు భవిష్యత్ స్థితి మార్పులకు మాత్రమే వర్తిస్తుంది. ఇప్పటికే ఒక స్థితిలో ఉన్న రికార్డులు ప్రవేశ సమయంలోని పరిమితిని కొనసాగిస్తాయి.");
        m.put("re.activity.config.reason_required_on_reject",
              "కాన్ఫిగరేషన్ మార్పును తిరస్కరించేటప్పుడు కారణం అవసరం.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "திறக்கப்படவில்லை");
        m.put("re.activity.opened", "திறக்கப்பட்டது");
        m.put("re.activity.under_review", "மறுஆய்வில்");
        m.put("re.activity.response_being_prepared", "பதில் தயாரிக்கப்படுகிறது");
        m.put("re.activity.documents_uploaded", "ஆவணங்கள் பதிவேற்றப்பட்டன");
        m.put("re.activity.response_submitted", "பதில் சமர்ப்பிக்கப்பட்டது");
        m.put("re.activity.overdue", "காலம் கடந்தது");
        m.put("re.activity.section_title", "நிறுவன செயல்பாடு");
        m.put("re.activity.last_updated", "கடைசியாக புதுப்பிக்கப்பட்டது");
        m.put("re.activity.never_updated", "இன்னும் எந்த செயல்பாடும் பதிவாகவில்லை");
        m.put("re.activity.read_only_notice",
              "இந்த நிலை நிறுவனத்தின் செயல்களில் இருந்து தானாகவே தீர்மானிக்கப்படுகிறது, திருத்த முடியாது.");
        m.put("re.activity.history_title", "செயல்பாட்டு வரலாறு");
        m.put("re.activity.no_history", "இந்தப் பதிவுக்கு இன்னும் எந்த செயல்பாடும் பதிவாகவில்லை.");
        m.put("re.activity.source_automatic", "அமைப்பு");
        m.put("re.activity.source_manual", "பயனர்");
        m.put("re.activity.nudge_sent", "தொடர் நினைவூட்டல் அனுப்பப்பட்டது");
        m.put("re.activity.stuck_for", "{days} நாட்களாக நிறுத்தப்பட்டுள்ளது");
        m.put("re.activity.draft_saved", "வரைவு சேமிக்கப்பட்டது");
        m.put("re.activity.draft_save_button", "வரைவைச் சேமி");
        m.put("re.activity.manual_set_forbidden",
              "ஆர்.இ. செயல்பாட்டு நிலை உங்கள் செயல்களில் இருந்து தீர்மானிக்கப்படுகிறது, நேரடியாக அமைக்க முடியாது.");
        m.put("re.activity.progress_visible_notice",
              "இந்தப் பதிவில் உங்கள் முன்னேற்றம் அதை ஒப்படைத்த ஆர்.பி.ஐ. குழுவுக்குத் தெரியும்.");
        m.put("notification.re_activity.overdue", "ஆர்.இ. கால அவகாசத்தில் பதிலளிக்கவில்லை");
        m.put("notification.re_activity.nudge", "ஆர்.இ. முன்னேற்றம் நின்றுவிட்டது");
        m.put("re.activity.config.title", "நினைவூட்டல் வரம்புகள்");
        m.put("re.activity.config.threshold_label", "நினைவூட்டலுக்கு முன் நாட்கள்");
        m.put("re.activity.config.request_button", "மாற்றத்தைக் கோரு");
        m.put("re.activity.config.approve_button", "ஒப்புதல்");
        m.put("re.activity.config.reject_button", "நிராகரி");
        m.put("re.activity.config.reason_label", "மாற்றத்திற்கான காரணம்");
        m.put("re.activity.config.pending_approval", "மற்றொரு நிர்வாகியின் ஒப்புதலுக்குக் காத்திருக்கிறது");
        m.put("re.activity.config.self_approval_blocked",
              "இந்த மாற்றத்தை நீங்கள் கோரியுள்ளீர்கள், எனவே வேறொரு நிர்வாகி இதை ஒப்புக வேண்டும்.");
        m.put("re.activity.config.stale_request",
              "கோரிக்கை வைக்கப்பட்ட பிறகு இந்த மதிப்பு மாறியுள்ளது. புதிய கோரிக்கையை வைக்கவும்.");
        m.put("re.activity.config.applies_to_future_only",
              "வரம்பை மாற்றுவது எதிர்கால நிலை மாற்றங்களுக்கு மட்டுமே பொருந்தும். ஏற்கனவே ஒரு நிலையில் உள்ள பதிவுகள் நுழைந்த நேரத்தின் வரம்பையே தக்கவைக்கும்.");
        m.put("re.activity.config.reason_required_on_reject",
              "கட்டமைப்பு மாற்றத்தை நிராகரிக்கும்போது காரணம் தேவை.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "ખોલ્યું નથી");
        m.put("re.activity.opened", "ખોલ્યું");
        m.put("re.activity.under_review", "સમીક્ષા હેઠળ");
        m.put("re.activity.response_being_prepared", "જવાબ તૈયાર થઈ રહ્યો છે");
        m.put("re.activity.documents_uploaded", "દસ્તાવેજો અપલોડ કર્યા");
        m.put("re.activity.response_submitted", "જવાબ સબમિટ કર્યો");
        m.put("re.activity.overdue", "સમયમર્યાદા વીતી");
        m.put("re.activity.section_title", "સંસ્થા પ્રવૃત્તિ");
        m.put("re.activity.last_updated", "છેલ્લે અપડેટ કર્યું");
        m.put("re.activity.never_updated", "હજુ કોઈ પ્રવૃત્તિ નોંધાઈ નથી");
        m.put("re.activity.read_only_notice",
              "આ સ્થિતિ સંસ્થાની ક્રિયાઓ પરથી સ્વયંસંચાલિત રીતે નક્કી થાય છે અને તેને સંપાદિત કરી શકાતી નથી.");
        m.put("re.activity.history_title", "પ્રવૃત્તિ ઇતિહાસ");
        m.put("re.activity.no_history", "આ રેકોર્ડ માટે હજુ કોઈ પ્રવૃત્તિ નોંધાઈ નથી.");
        m.put("re.activity.source_automatic", "સિસ્ટમ");
        m.put("re.activity.source_manual", "વપરાશકર્તા");
        m.put("re.activity.nudge_sent", "અનુસરણ સ્મરણપત્ર મોકલ્યું");
        m.put("re.activity.stuck_for", "{days} દિવસથી અટકેલું");
        m.put("re.activity.draft_saved", "ડ્રાફ્ટ સાચવ્યો");
        m.put("re.activity.draft_save_button", "ડ્રાફ્ટ સાચવો");
        m.put("re.activity.manual_set_forbidden",
              "આરઈ પ્રવૃત્તિ સ્થિતિ તમારી ક્રિયાઓ પરથી નક્કી થાય છે અને સીધી સેટ કરી શકાતી નથી.");
        m.put("re.activity.progress_visible_notice",
              "આ રેકોર્ડ પર તમારી પ્રગતિ તે સોંપનાર આરબીઆઈ ટીમને દેખાય છે.");
        m.put("notification.re_activity.overdue", "આરઈએ સમયમર્યાદામાં જવાબ આપ્યો નથી");
        m.put("notification.re_activity.nudge", "આરઈની પ્રગતિ અટકી ગઈ છે");
        m.put("re.activity.config.title", "સ્મરણપત્ર મર્યાદાઓ");
        m.put("re.activity.config.threshold_label", "સ્મરણપત્ર પહેલાંના દિવસો");
        m.put("re.activity.config.request_button", "ફેરફારની વિનંતી કરો");
        m.put("re.activity.config.approve_button", "મંજૂર કરો");
        m.put("re.activity.config.reject_button", "નકારો");
        m.put("re.activity.config.reason_label", "ફેરફારનું કારણ");
        m.put("re.activity.config.pending_approval", "અન્ય પ્રશાસકની મંજૂરીની પ્રતીક્ષામાં");
        m.put("re.activity.config.self_approval_blocked",
              "તમે આ ફેરફારની વિનંતી કરી છે, તેથી તેને અન્ય પ્રશાસકે મંજૂર કરવો પડશે.");
        m.put("re.activity.config.stale_request",
              "વિનંતી કર્યા પછી આ મૂલ્ય બદલાયું છે. કૃપા કરીને નવી વિનંતી કરો.");
        m.put("re.activity.config.applies_to_future_only",
              "મર્યાદા બદલવાથી ફક્ત ભવિષ્યના સ્થિતિ ફેરફારો પ્રભાવિત થાય છે. જે રેકોર્ડ પહેલેથી કોઈ સ્થિતિમાં છે તે પ્રવેશ સમયની મર્યાદા જાળવી રાખે છે.");
        m.put("re.activity.config.reason_required_on_reject",
              "કન્ફિગરેશન ફેરફાર નકારતી વખતે કારણ આવશ્યક છે.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "نہیں کھولا گیا");
        m.put("re.activity.opened", "کھولا گیا");
        m.put("re.activity.under_review", "زیرِ جائزہ");
        m.put("re.activity.response_being_prepared", "جواب تیار کیا جا رہا ہے");
        m.put("re.activity.documents_uploaded", "دستاویزات اپ لوڈ کی گئیں");
        m.put("re.activity.response_submitted", "جواب جمع کرا دیا گیا");
        m.put("re.activity.overdue", "مقررہ مدت گزر گئی");
        m.put("re.activity.section_title", "ادارے کی سرگرمی");
        m.put("re.activity.last_updated", "آخری تازہ کاری");
        m.put("re.activity.never_updated", "ابھی تک کوئی سرگرمی درج نہیں");
        m.put("re.activity.read_only_notice",
              "یہ حالت ادارے کے اقدامات سے خود بخود متعین ہوتی ہے اور اسے تبدیل نہیں کیا جا سکتا۔");
        m.put("re.activity.history_title", "سرگرمی کی تاریخ");
        m.put("re.activity.no_history", "اس ریکارڈ کے لیے ابھی تک کوئی سرگرمی درج نہیں کی گئی۔");
        m.put("re.activity.source_automatic", "نظام");
        m.put("re.activity.source_manual", "صارف");
        m.put("re.activity.nudge_sent", "یاد دہانی بھیج دی گئی");
        m.put("re.activity.stuck_for", "{days} دن سے رکا ہوا");
        m.put("re.activity.draft_saved", "مسودہ محفوظ ہو گیا");
        m.put("re.activity.draft_save_button", "مسودہ محفوظ کریں");
        m.put("re.activity.manual_set_forbidden",
              "آر ای سرگرمی کی حالت آپ کے اقدامات سے متعین ہوتی ہے اور اسے براہِ راست مقرر نہیں کیا جا سکتا۔");
        m.put("re.activity.progress_visible_notice",
              "اس ریکارڈ پر آپ کی پیش رفت اُس آر بی آئی ٹیم کو نظر آتی ہے جس نے یہ سونپا ہے۔");
        m.put("notification.re_activity.overdue", "آر ای نے مقررہ مدت میں جواب نہیں دیا");
        m.put("notification.re_activity.nudge", "آر ای کی پیش رفت رک گئی ہے");
        m.put("re.activity.config.title", "یاد دہانی کی حدیں");
        m.put("re.activity.config.threshold_label", "یاد دہانی سے پہلے دن");
        m.put("re.activity.config.request_button", "تبدیلی کی درخواست دیں");
        m.put("re.activity.config.approve_button", "منظور کریں");
        m.put("re.activity.config.reject_button", "مسترد کریں");
        m.put("re.activity.config.reason_label", "تبدیلی کی وجہ");
        m.put("re.activity.config.pending_approval", "دوسرے منتظم کی منظوری کا انتظار");
        m.put("re.activity.config.self_approval_blocked",
              "آپ نے یہ تبدیلی تجویز کی ہے، لہٰذا اسے کسی دوسرے منتظم کو منظور کرنا ہوگا۔");
        m.put("re.activity.config.stale_request",
              "درخواست کے بعد یہ قدر تبدیل ہو گئی ہے۔ براہِ کرم نئی درخواست دیں۔");
        m.put("re.activity.config.applies_to_future_only",
              "حد تبدیل کرنے سے صرف آئندہ حالت کی تبدیلیاں متاثر ہوتی ہیں۔ جو ریکارڈ پہلے ہی کسی حالت میں ہیں وہ داخلے کے وقت کی حد برقرار رکھتے ہیں۔");
        m.put("re.activity.config.reason_required_on_reject",
              "کنفیگریشن تبدیلی مسترد کرتے وقت وجہ درکار ہے۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "ತೆರೆಯಲಾಗಿಲ್ಲ");
        m.put("re.activity.opened", "ತೆರೆಯಲಾಗಿದೆ");
        m.put("re.activity.under_review", "ಪರಿಶೀಲನೆಯಲ್ಲಿ");
        m.put("re.activity.response_being_prepared", "ಪ್ರತಿಕ್ರಿಯೆ ಸಿದ್ಧಪಡಿಸಲಾಗುತ್ತಿದೆ");
        m.put("re.activity.documents_uploaded", "ದಾಖಲೆಗಳನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಲಾಗಿದೆ");
        m.put("re.activity.response_submitted", "ಪ್ರತಿಕ್ರಿಯೆ ಸಲ್ಲಿಸಲಾಗಿದೆ");
        m.put("re.activity.overdue", "ಗಡುವು ಮೀರಿದೆ");
        m.put("re.activity.section_title", "ಸಂಸ್ಥೆಯ ಚಟುವಟಿಕೆ");
        m.put("re.activity.last_updated", "ಕೊನೆಯ ನವೀಕರಣ");
        m.put("re.activity.never_updated", "ಇನ್ನೂ ಯಾವುದೇ ಚಟುವಟಿಕೆ ದಾಖಲಾಗಿಲ್ಲ");
        m.put("re.activity.read_only_notice",
              "ಈ ಸ್ಥಿತಿಯನ್ನು ಸಂಸ್ಥೆಯ ಕ್ರಮಗಳಿಂದ ಸ್ವಯಂಚಾಲಿತವಾಗಿ ನಿರ್ಧರಿಸಲಾಗುತ್ತದೆ ಮತ್ತು ಸಂಪಾದಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("re.activity.history_title", "ಚಟುವಟಿಕೆ ಇತಿಹಾಸ");
        m.put("re.activity.no_history", "ಈ ದಾಖಲೆಗೆ ಇನ್ನೂ ಯಾವುದೇ ಚಟುವಟಿಕೆ ದಾಖಲಾಗಿಲ್ಲ.");
        m.put("re.activity.source_automatic", "ವ್ಯವಸ್ಥೆ");
        m.put("re.activity.source_manual", "ಬಳಕೆದಾರ");
        m.put("re.activity.nudge_sent", "ಅನುಸರಣಾ ಜ್ಞಾಪನೆ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("re.activity.stuck_for", "{days} ದಿನಗಳಿಂದ ಸ್ತಬ್ಧವಾಗಿದೆ");
        m.put("re.activity.draft_saved", "ಕರಡು ಉಳಿಸಲಾಗಿದೆ");
        m.put("re.activity.draft_save_button", "ಕರಡು ಉಳಿಸಿ");
        m.put("re.activity.manual_set_forbidden",
              "ಆರ್‌ಇ ಚಟುವಟಿಕೆ ಸ್ಥಿತಿಯನ್ನು ನಿಮ್ಮ ಕ್ರಮಗಳಿಂದ ನಿರ್ಧರಿಸಲಾಗುತ್ತದೆ, ನೇರವಾಗಿ ಹೊಂದಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("re.activity.progress_visible_notice",
              "ಈ ದಾಖಲೆಯ ಮೇಲಿನ ನಿಮ್ಮ ಪ್ರಗತಿಯು ಅದನ್ನು ನಿಯೋಜಿಸಿದ ಆರ್‌ಬಿಐ ತಂಡಕ್ಕೆ ಗೋಚರಿಸುತ್ತದೆ.");
        m.put("notification.re_activity.overdue", "ಆರ್‌ಇ ಗಡುವಿನೊಳಗೆ ಪ್ರತಿಕ್ರಿಯಿಸಿಲ್ಲ");
        m.put("notification.re_activity.nudge", "ಆರ್‌ಇ ಪ್ರಗತಿ ಸ್ತಬ್ಧವಾಗಿದೆ");
        m.put("re.activity.config.title", "ಜ್ಞಾಪನೆ ಮಿತಿಗಳು");
        m.put("re.activity.config.threshold_label", "ಜ್ಞಾಪನೆಗೂ ಮುನ್ನ ದಿನಗಳು");
        m.put("re.activity.config.request_button", "ಬದಲಾವಣೆಗೆ ವಿನಂತಿಸಿ");
        m.put("re.activity.config.approve_button", "ಅನುಮೋದಿಸಿ");
        m.put("re.activity.config.reject_button", "ತಿರಸ್ಕರಿಸಿ");
        m.put("re.activity.config.reason_label", "ಬದಲಾವಣೆಯ ಕಾರಣ");
        m.put("re.activity.config.pending_approval", "ಇನ್ನೊಬ್ಬ ನಿರ್ವಾಹಕರ ಅನುಮೋದನೆಗೆ ಬಾಕಿ");
        m.put("re.activity.config.self_approval_blocked",
              "ನೀವು ಈ ಬದಲಾವಣೆಯನ್ನು ವಿನಂತಿಸಿದ್ದೀರಿ, ಆದ್ದರಿಂದ ಬೇರೊಬ್ಬ ನಿರ್ವಾಹಕರು ಇದನ್ನು ಅನುಮೋದಿಸಬೇಕು.");
        m.put("re.activity.config.stale_request",
              "ವಿನಂತಿ ಸಲ್ಲಿಸಿದ ನಂತರ ಈ ಮೌಲ್ಯ ಬದಲಾಗಿದೆ. ದಯವಿಟ್ಟು ಹೊಸ ವಿನಂತಿ ಸಲ್ಲಿಸಿ.");
        m.put("re.activity.config.applies_to_future_only",
              "ಮಿತಿ ಬದಲಾವಣೆ ಭವಿಷ್ಯದ ಸ್ಥಿತಿ ಬದಲಾವಣೆಗಳಿಗೆ ಮಾತ್ರ ಅನ್ವಯಿಸುತ್ತದೆ. ಈಗಾಗಲೇ ಒಂದು ಸ್ಥಿತಿಯಲ್ಲಿರುವ ದಾಖಲೆಗಳು ಪ್ರವೇಶದ ಸಮಯದ ಮಿತಿಯನ್ನೇ ಉಳಿಸಿಕೊಳ್ಳುತ್ತವೆ.");
        m.put("re.activity.config.reason_required_on_reject",
              "ಕಾನ್ಫಿಗರೇಶನ್ ಬದಲಾವಣೆಯನ್ನು ತಿರಸ್ಕರಿಸುವಾಗ ಕಾರಣ ಅಗತ್ಯ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.activity.not_opened", "തുറന്നിട്ടില്ല");
        m.put("re.activity.opened", "തുറന്നു");
        m.put("re.activity.under_review", "പരിശോധനയിൽ");
        m.put("re.activity.response_being_prepared", "മറുപടി തയ്യാറാക്കുന്നു");
        m.put("re.activity.documents_uploaded", "രേഖകൾ അപ്‌ലോഡ് ചെയ്തു");
        m.put("re.activity.response_submitted", "മറുപടി സമർപ്പിച്ചു");
        m.put("re.activity.overdue", "സമയപരിധി കഴിഞ്ഞു");
        m.put("re.activity.section_title", "സ്ഥാപന പ്രവർത്തനം");
        m.put("re.activity.last_updated", "അവസാനം പുതുക്കിയത്");
        m.put("re.activity.never_updated", "ഇതുവരെ പ്രവർത്തനം രേഖപ്പെടുത്തിയിട്ടില്ല");
        m.put("re.activity.read_only_notice",
              "ഈ നില സ്ഥാപനത്തിന്റെ പ്രവർത്തനങ്ങളിൽ നിന്ന് സ്വയമേവ നിർണ്ണയിക്കപ്പെടുന്നു, തിരുത്താൻ കഴിയില്ല.");
        m.put("re.activity.history_title", "പ്രവർത്തന ചരിത്രം");
        m.put("re.activity.no_history", "ഈ രേഖയ്ക്ക് ഇതുവരെ പ്രവർത്തനം രേഖപ്പെടുത്തിയിട്ടില്ല.");
        m.put("re.activity.source_automatic", "സിസ്റ്റം");
        m.put("re.activity.source_manual", "ഉപയോക്താവ്");
        m.put("re.activity.nudge_sent", "തുടർ ഓർമ്മപ്പെടുത്തൽ അയച്ചു");
        m.put("re.activity.stuck_for", "{days} ദിവസമായി നിശ്ചലം");
        m.put("re.activity.draft_saved", "കരട് സൂക്ഷിച്ചു");
        m.put("re.activity.draft_save_button", "കരട് സൂക്ഷിക്കുക");
        m.put("re.activity.manual_set_forbidden",
              "ആർഇ പ്രവർത്തന നില നിങ്ങളുടെ പ്രവർത്തനങ്ങളിൽ നിന്ന് നിർണ്ണയിക്കപ്പെടുന്നു, നേരിട്ട് സെറ്റ് ചെയ്യാൻ കഴിയില്ല.");
        m.put("re.activity.progress_visible_notice",
              "ഈ രേഖയിലെ നിങ്ങളുടെ പുരോഗതി അത് നിയോഗിച്ച ആർബിഐ ടീമിന് ദൃശ്യമാണ്.");
        m.put("notification.re_activity.overdue", "ആർഇ സമയപരിധിക്കുള്ളിൽ മറുപടി നൽകിയില്ല");
        m.put("notification.re_activity.nudge", "ആർഇയുടെ പുരോഗതി നിശ്ചലമായി");
        m.put("re.activity.config.title", "ഓർമ്മപ്പെടുത്തൽ പരിധികൾ");
        m.put("re.activity.config.threshold_label", "ഓർമ്മപ്പെടുത്തലിന് മുൻപുള്ള ദിവസങ്ങൾ");
        m.put("re.activity.config.request_button", "മാറ്റം അഭ്യർത്ഥിക്കുക");
        m.put("re.activity.config.approve_button", "അനുവദിക്കുക");
        m.put("re.activity.config.reject_button", "നിരസിക്കുക");
        m.put("re.activity.config.reason_label", "മാറ്റത്തിന്റെ കാരണം");
        m.put("re.activity.config.pending_approval", "മറ്റൊരു അഡ്മിനിസ്ട്രേറ്ററുടെ അനുമതിക്കായി കാത്തിരിക്കുന്നു");
        m.put("re.activity.config.self_approval_blocked",
              "നിങ്ങളാണ് ഈ മാറ്റം അഭ്യർത്ഥിച്ചത്, അതിനാൽ മറ്റൊരു അഡ്മിനിസ്ട്രേറ്റർ ഇത് അനുവദിക്കണം.");
        m.put("re.activity.config.stale_request",
              "അഭ്യർത്ഥന നൽകിയ ശേഷം ഈ മൂല്യം മാറി. പുതിയ അഭ്യർത്ഥന നൽകുക.");
        m.put("re.activity.config.applies_to_future_only",
              "പരിധി മാറ്റുന്നത് ഭാവിയിലെ നില മാറ്റങ്ങളെ മാത്രം ബാധിക്കും. ഇതിനകം ഒരു നിലയിലുള്ള രേഖകൾ പ്രവേശിച്ച സമയത്തെ പരിധി നിലനിർത്തും.");
        m.put("re.activity.config.reason_required_on_reject",
              "കോൺഫിഗറേഷൻ മാറ്റം നിരസിക്കുമ്പോൾ ഒരു കാരണം ആവശ്യമാണ്.");
        return m;
    }
}
