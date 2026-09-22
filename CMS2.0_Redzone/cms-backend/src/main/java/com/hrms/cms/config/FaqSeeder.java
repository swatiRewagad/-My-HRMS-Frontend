package com.hrms.cms.config;

import com.hrms.cms.entity.Faq;
import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.FaqRepository;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seeds the FAQ table and its question/answer translation keys.
 *
 * Answers deliberately avoid citing clause numbers and the cms.mre.* day counts other than the
 * 30-day RE response window: those values are configurable per environment, so quoting them in
 * static prose would let the FAQ drift out of step with the rules actually being enforced.
 *
 * Only en/hi/mr are seeded here. getTranslationsForLocale falls back to the English defaultValue
 * for any locale with no row, so the remaining seven locales render readable English rather than a
 * raw key until professionally translated copy is available.
 */
@Component
@Order(8)
public class FaqSeeder implements CommandLineRunner {

    private static final String CAT_FILING = "filing";
    private static final String CAT_ELIGIBILITY = "eligibility";
    private static final String CAT_TRACKING = "tracking";
    private static final String CAT_PRIVACY = "privacy";

    private final FaqRepository faqRepo;
    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public FaqSeeder(FaqRepository faqRepo, TranslationKeyRepository keyRepo,
                     TranslationRepository translationRepo) {
        this.faqRepo = faqRepo;
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedCategoryLabels();
        seedEnglish();
        seedHindi();
        seedMarathi();
    }

    private void seedCategoryLabels() {
        seedKey("faq.cat_filing", "faq", "FAQ category: filing", "Filing a Complaint");
        seedKey("faq.cat_eligibility", "faq", "FAQ category: eligibility", "Eligibility");
        seedKey("faq.cat_tracking", "faq", "FAQ category: tracking", "Tracking & Updates");
        seedKey("faq.cat_privacy", "faq", "FAQ category: privacy", "Privacy & Security");
    }

    private void seedEnglish() {
        int n = 0;

        faq(++n, CAT_FILING, "faq.q_what_is_scheme", "faq.a_what_is_scheme",
                "What is the Reserve Bank – Integrated Ombudsman Scheme?",
                "It is a grievance redress mechanism set up by the Reserve Bank of India for customers "
                        + "of RBI-regulated entities such as banks, NBFCs, payment system participants and "
                        + "credit information companies. If your grievance is not resolved by the entity "
                        + "itself, you can escalate it to the RBI Ombudsman through this portal.");

        faq(++n, CAT_FILING, "faq.q_fee", "faq.a_fee",
                "Is there any fee for filing a complaint?",
                "No. Filing a complaint with the RBI Ombudsman is free of cost. You will never be asked "
                        + "to pay a fee, and no one from the Reserve Bank will ask you for money, card "
                        + "details, PINs or OTPs to process your complaint.");

        faq(++n, CAT_FILING, "faq.q_complain_to_re_first", "faq.a_complain_to_re_first",
                "Do I have to complain to my bank or finance company first?",
                "Yes. You must first raise a written or electronic complaint with the regulated entity "
                        + "concerned. You may approach the Ombudsman if the entity rejects your complaint, "
                        + "if you are not satisfied with the reply, or if you receive no reply within 30 "
                        + "days of complaining to them.");

        faq(++n, CAT_FILING, "faq.q_documents", "faq.a_documents",
                "What details and documents should I keep ready?",
                "Keep a copy of the complaint you sent to the regulated entity and the date you sent it, "
                        + "any reply or reminder correspondence, and the entity's acknowledgement reference "
                        + "if one was issued. Supporting documents such as statements or receipts help the "
                        + "Ombudsman assess your case faster.");

        faq(++n, CAT_FILING, "faq.q_languages", "faq.a_languages",
                "Can I use the portal in my own language?",
                "Yes. Use the language selector at the top of the page to switch between English, Hindi "
                        + "and other supported Indian languages. You can change the language at any point "
                        + "without losing what you have already entered.");

        faq(++n, CAT_FILING, "faq.q_accessibility", "faq.a_accessibility",
                "I find the text hard to read. Can I change it?",
                "Yes. The toolbar at the top of every page lets you increase or decrease the text size, "
                        + "switch to a high-contrast view, and open an accessibility panel. Where a question "
                        + "is worded in legal language, a simplified version is also available.");

        faq(++n, CAT_ELIGIBILITY, "faq.q_no_reply", "faq.a_no_reply",
                "What if the entity never replies to my complaint?",
                "The regulated entity is allowed 30 days to respond. If that period has passed with no "
                        + "reply, you may proceed to file with the Ombudsman. If you file before the 30 days "
                        + "have elapsed, the portal will tell you the date from which you can file.");

        faq(++n, CAT_ELIGIBILITY, "faq.q_not_taken_up", "faq.a_not_taken_up",
                "Which complaints cannot be taken up by the Ombudsman?",
                "Broadly, a complaint cannot be taken up if the same grievance is already pending before, "
                        + "or has already been decided by, the Ombudsman or a court, tribunal or arbitrator; "
                        + "if it concerns an employer-employee relationship with the regulated entity; or if "
                        + "you have not first complained to the entity. The eligibility questions on this "
                        + "portal will tell you before you complete the form, and will explain the reason.");

        faq(++n, CAT_ELIGIBILITY, "faq.q_advocate", "faq.a_advocate",
                "Can someone else file the complaint for me?",
                "The complaint must be filed by the complainant. If you indicate that the complaint is "
                        + "being made through an advocate, you will be asked to confirm that you are the "
                        + "complainant; if you are not, the complaint cannot be processed.");

        faq(++n, CAT_ELIGIBILITY, "faq.q_not_eligible", "faq.a_not_eligible",
                "The portal says my complaint is not maintainable. What now?",
                "You will be shown the specific reason and a reference number for that decision. Being "
                        + "non-maintainable under this Scheme does not mean your grievance is invalid — you "
                        + "may still pursue it with the regulated entity or through any other remedy "
                        + "available to you.");

        faq(++n, CAT_TRACKING, "faq.q_track", "faq.a_track",
                "How do I check the status of my complaint?",
                "Open the Track page. You can search using your complaint reference number, or verify "
                        + "your registered mobile number with an OTP to see all complaints filed from that "
                        + "number.");

        faq(++n, CAT_TRACKING, "faq.q_track_anonymous", "faq.a_track_anonymous",
                "Can I check status without logging in?",
                "Yes. Entering a valid complaint reference number shows the current status without "
                        + "logging in. For your protection, personal details such as the complainant's name "
                        + "and mobile number are masked unless you verify your mobile number with an OTP.");

        faq(++n, CAT_TRACKING, "faq.q_withdraw", "faq.a_withdraw",
                "Can I withdraw a complaint I have already filed?",
                "Yes. Verify your mobile number with an OTP, open the complaint from your complaint "
                        + "history, and choose to withdraw it. Only the complainant who filed a complaint "
                        + "can withdraw it.");

        faq(++n, CAT_TRACKING, "faq.q_contact", "faq.a_contact",
                "Whom do I contact if I need help using this portal?",
                "Call the toll-free helpline 14448 shown at the top of every page. Please keep your "
                        + "complaint reference number handy so that your query can be answered quickly.");

        faq(++n, CAT_PRIVACY, "faq.q_data_use", "faq.a_data_use",
                "How is my personal information used?",
                "Your details are collected only to register and process your complaint and are shared "
                        + "with the regulated entity concerned for that purpose. Before you submit, you are "
                        + "shown a consent notice explaining what is collected and why, and your consent is "
                        + "recorded.");

        faq(++n, CAT_PRIVACY, "faq.q_withdraw_consent", "faq.a_withdraw_consent",
                "Can I withdraw the consent I gave?",
                "Yes. You may withdraw your consent, but doing so may prevent your complaint from being "
                        + "processed further, because the details you provided are needed to examine the "
                        + "grievance with the regulated entity.");

        faq(++n, CAT_PRIVACY, "faq.q_session", "faq.a_session",
                "Why was I signed out while filling the form?",
                "For your security, a session ends after a period of inactivity and you are warned "
                        + "shortly before that happens. Interacting with the page keeps the session active. "
                        + "If you are signed out, verify your mobile number again to continue.");

        faq(++n, CAT_PRIVACY, "faq.q_phishing", "faq.a_phishing",
                "How do I know a message about my complaint is genuine?",
                "Communication about your complaint will reference your complaint number and will never "
                        + "ask for your card number, PIN, password, full account number or an OTP. Do not "
                        + "share an OTP with anyone, including callers claiming to be from the Reserve Bank.");
    }

    private void seedHindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("faq.cat_filing", "शिकायत दर्ज करना");
        m.put("faq.cat_eligibility", "पात्रता");
        m.put("faq.cat_tracking", "स्थिति और अद्यतन");
        m.put("faq.cat_privacy", "गोपनीयता और सुरक्षा");

        m.put("faq.q_what_is_scheme", "रिज़र्व बैंक – एकीकृत लोकपाल योजना क्या है?");
        m.put("faq.a_what_is_scheme", "यह भारतीय रिज़र्व बैंक द्वारा स्थापित एक शिकायत निवारण व्यवस्था है, "
                + "जो बैंक, एनबीएफसी, भुगतान प्रणाली प्रतिभागी और क्रेडिट सूचना कंपनियों जैसी आरबीआई-विनियमित "
                + "संस्थाओं के ग्राहकों के लिए है। यदि संस्था स्वयं आपकी शिकायत का समाधान नहीं करती, तो आप इस "
                + "पोर्टल के माध्यम से आरबीआई लोकपाल के पास जा सकते हैं।");

        m.put("faq.q_fee", "शिकायत दर्ज करने के लिए कोई शुल्क है?");
        m.put("faq.a_fee", "नहीं। आरबीआई लोकपाल के पास शिकायत दर्ज करना निःशुल्क है। आपसे कभी शुल्क नहीं मांगा "
                + "जाएगा, और रिज़र्व बैंक का कोई भी व्यक्ति शिकायत के लिए पैसे, कार्ड विवरण, पिन या ओटीपी "
                + "नहीं मांगेगा।");

        m.put("faq.q_complain_to_re_first", "क्या मुझे पहले अपने बैंक या वित्त कंपनी में शिकायत करनी होगी?");
        m.put("faq.a_complain_to_re_first", "हाँ। आपको पहले संबंधित विनियमित संस्था में लिखित या इलेक्ट्रॉनिक "
                + "शिकायत दर्ज करनी होगी। यदि संस्था आपकी शिकायत अस्वीकार कर देती है, आप उत्तर से संतुष्ट नहीं "
                + "हैं, या शिकायत के 30 दिनों के भीतर कोई उत्तर नहीं मिलता, तो आप लोकपाल के पास जा सकते हैं।");

        m.put("faq.q_documents", "मुझे कौन-से विवरण और दस्तावेज़ तैयार रखने चाहिए?");
        m.put("faq.a_documents", "विनियमित संस्था को भेजी गई शिकायत की प्रति और उसकी तारीख, कोई भी उत्तर या "
                + "अनुस्मारक पत्राचार, और संस्था द्वारा जारी पावती संदर्भ तैयार रखें। विवरण जैसे खाता विवरण या "
                + "रसीदें लोकपाल को आपके मामले का शीघ्र मूल्यांकन करने में सहायता करती हैं।");

        m.put("faq.q_languages", "क्या मैं पोर्टल को अपनी भाषा में उपयोग कर सकता हूँ?");
        m.put("faq.a_languages", "हाँ। पृष्ठ के शीर्ष पर भाषा चयनकर्ता से आप अंग्रेज़ी, हिंदी और अन्य समर्थित "
                + "भारतीय भाषाओं के बीच बदल सकते हैं। आप किसी भी समय भाषा बदल सकते हैं और आपकी दर्ज की गई "
                + "जानकारी सुरक्षित रहती है।");

        m.put("faq.q_accessibility", "मुझे पाठ पढ़ने में कठिनाई होती है। क्या मैं इसे बदल सकता हूँ?");
        m.put("faq.a_accessibility", "हाँ। प्रत्येक पृष्ठ के शीर्ष पर उपलब्ध टूलबार से आप पाठ का आकार बढ़ा या "
                + "घटा सकते हैं, उच्च-कंट्रास्ट दृश्य पर जा सकते हैं, और सुगम्यता पैनल खोल सकते हैं। जहाँ प्रश्न "
                + "कानूनी भाषा में है, वहाँ सरल संस्करण भी उपलब्ध है।");

        m.put("faq.q_no_reply", "यदि संस्था मेरी शिकायत का उत्तर कभी न दे तो क्या होगा?");
        m.put("faq.a_no_reply", "विनियमित संस्था को उत्तर देने के लिए 30 दिन का समय है। यदि यह अवधि बिना उत्तर "
                + "बीत गई है, तो आप लोकपाल के पास शिकायत दर्ज कर सकते हैं। यदि आप 30 दिन पूरे होने से पहले "
                + "शिकायत दर्ज करते हैं, तो पोर्टल आपको वह तारीख बताएगा जिससे आप दर्ज कर सकते हैं।");

        m.put("faq.q_not_taken_up", "कौन-सी शिकायतें लोकपाल द्वारा नहीं ली जा सकतीं?");
        m.put("faq.a_not_taken_up", "सामान्यतः यदि वही शिकायत लोकपाल, न्यायालय, अधिकरण या मध्यस्थ के समक्ष "
                + "पहले से लंबित है या निर्णीत हो चुकी है; यदि वह विनियमित संस्था के साथ नियोक्ता-कर्मचारी संबंध "
                + "से जुड़ी है; या यदि आपने पहले संस्था में शिकायत नहीं की है, तो शिकायत नहीं ली जा सकती। पोर्टल "
                + "के पात्रता प्रश्न फ़ॉर्म पूरा करने से पहले ही आपको कारण सहित बता देंगे।");

        m.put("faq.q_advocate", "क्या कोई अन्य व्यक्ति मेरी ओर से शिकायत दर्ज कर सकता है?");
        m.put("faq.a_advocate", "शिकायत शिकायतकर्ता द्वारा ही दर्ज की जानी चाहिए। यदि आप बताते हैं कि शिकायत "
                + "अधिवक्ता के माध्यम से की जा रही है, तो आपसे पुष्टि करने को कहा जाएगा कि आप शिकायतकर्ता हैं; "
                + "यदि आप नहीं हैं, तो शिकायत पर कार्यवाही नहीं हो सकती।");

        m.put("faq.q_not_eligible", "पोर्टल कहता है कि मेरी शिकायत विचारणीय नहीं है। अब क्या करें?");
        m.put("faq.a_not_eligible", "आपको विशिष्ट कारण और उस निर्णय का संदर्भ क्रमांक दिखाया जाएगा। इस योजना के "
                + "अंतर्गत विचारणीय न होने का अर्थ यह नहीं है कि आपकी शिकायत अमान्य है — आप इसे विनियमित संस्था "
                + "के साथ या अपने लिए उपलब्ध किसी अन्य उपाय के माध्यम से आगे बढ़ा सकते हैं।");

        m.put("faq.q_track", "मैं अपनी शिकायत की स्थिति कैसे देखूँ?");
        m.put("faq.a_track", "ट्रैक पृष्ठ खोलें। आप अपने शिकायत संदर्भ क्रमांक से खोज सकते हैं, या ओटीपी से अपना "
                + "पंजीकृत मोबाइल नंबर सत्यापित करके उस नंबर से दर्ज सभी शिकायतें देख सकते हैं।");

        m.put("faq.q_track_anonymous", "क्या मैं लॉग इन किए बिना स्थिति देख सकता हूँ?");
        m.put("faq.a_track_anonymous", "हाँ। वैध शिकायत संदर्भ क्रमांक दर्ज करने पर वर्तमान स्थिति बिना लॉग इन "
                + "दिखती है। आपकी सुरक्षा के लिए शिकायतकर्ता का नाम और मोबाइल नंबर जैसे व्यक्तिगत विवरण छिपे "
                + "रहते हैं, जब तक आप ओटीपी से अपना मोबाइल नंबर सत्यापित नहीं करते।");

        m.put("faq.q_withdraw", "क्या मैं पहले दर्ज की गई शिकायत वापस ले सकता हूँ?");
        m.put("faq.a_withdraw", "हाँ। ओटीपी से अपना मोबाइल नंबर सत्यापित करें, अपनी शिकायत सूची से शिकायत खोलें "
                + "और वापस लेने का विकल्प चुनें। शिकायत केवल उसी शिकायतकर्ता द्वारा वापस ली जा सकती है जिसने "
                + "उसे दर्ज किया था।");

        m.put("faq.q_contact", "पोर्टल के उपयोग में सहायता के लिए किससे संपर्क करूँ?");
        m.put("faq.a_contact", "प्रत्येक पृष्ठ के शीर्ष पर दिए टोल-फ़्री हेल्पलाइन 14448 पर कॉल करें। कृपया अपना "
                + "शिकायत संदर्भ क्रमांक तैयार रखें जिससे आपके प्रश्न का शीघ्र उत्तर दिया जा सके।");

        m.put("faq.q_data_use", "मेरी व्यक्तिगत जानकारी का उपयोग कैसे होता है?");
        m.put("faq.a_data_use", "आपके विवरण केवल आपकी शिकायत दर्ज करने और उस पर कार्यवाही करने के लिए एकत्र किए "
                + "जाते हैं और इसी उद्देश्य से संबंधित विनियमित संस्था के साथ साझा किए जाते हैं। जमा करने से पहले "
                + "आपको एक सहमति सूचना दिखाई जाती है जिसमें बताया जाता है कि क्या एकत्र किया जाता है और क्यों, "
                + "और आपकी सहमति दर्ज की जाती है।");

        m.put("faq.q_withdraw_consent", "क्या मैं दी गई सहमति वापस ले सकता हूँ?");
        m.put("faq.a_withdraw_consent", "हाँ। आप अपनी सहमति वापस ले सकते हैं, परंतु ऐसा करने से आपकी शिकायत पर "
                + "आगे कार्यवाही नहीं हो सकेगी, क्योंकि विनियमित संस्था के साथ शिकायत की जाँच के लिए आपके दिए "
                + "विवरण आवश्यक हैं।");

        m.put("faq.q_session", "फ़ॉर्म भरते समय मुझे साइन आउट क्यों कर दिया गया?");
        m.put("faq.a_session", "आपकी सुरक्षा के लिए निष्क्रियता की अवधि के बाद सत्र समाप्त हो जाता है और इससे "
                + "कुछ समय पहले आपको चेतावनी दी जाती है। पृष्ठ पर कार्य करते रहने से सत्र सक्रिय रहता है। साइन "
                + "आउट होने पर जारी रखने के लिए अपना मोबाइल नंबर पुनः सत्यापित करें।");

        m.put("faq.q_phishing", "मुझे कैसे पता चलेगा कि शिकायत से संबंधित संदेश वास्तविक है?");
        m.put("faq.a_phishing", "आपकी शिकायत से संबंधित संदेश में आपका शिकायत क्रमांक होगा और उसमें कभी आपका "
                + "कार्ड नंबर, पिन, पासवर्ड, पूरा खाता क्रमांक या ओटीपी नहीं मांगा जाएगा। ओटीपी किसी के साथ "
                + "साझा न करें, चाहे कॉल करने वाला स्वयं को रिज़र्व बैंक का बताए।");

        applyLocale(m, "hi");
    }

    private void seedMarathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("faq.cat_filing", "तक्रार दाखल करणे");
        m.put("faq.cat_eligibility", "पात्रता");
        m.put("faq.cat_tracking", "स्थिती आणि अद्यतने");
        m.put("faq.cat_privacy", "गोपनीयता आणि सुरक्षा");

        m.put("faq.q_what_is_scheme", "रिझर्व्ह बँक – एकीकृत लोकपाल योजना काय आहे?");
        m.put("faq.a_what_is_scheme", "ही भारतीय रिझर्व्ह बँकेने स्थापन केलेली तक्रार निवारण यंत्रणा आहे, जी "
                + "बँका, एनबीएफसी, देयक प्रणाली सहभागी आणि क्रेडिट माहिती कंपन्यांसारख्या आरबीआय-नियमित "
                + "संस्थांच्या ग्राहकांसाठी आहे. संस्थेने स्वतः तुमची तक्रार सोडवली नाही, तर तुम्ही या "
                + "पोर्टलद्वारे आरबीआय लोकपालाकडे जाऊ शकता.");

        m.put("faq.q_fee", "तक्रार दाखल करण्यासाठी काही शुल्क आहे का?");
        m.put("faq.a_fee", "नाही. आरबीआय लोकपालाकडे तक्रार दाखल करणे विनामूल्य आहे. तुमच्याकडून कधीही शुल्क "
                + "मागितले जाणार नाही, आणि रिझर्व्ह बँकेची कोणतीही व्यक्ती तक्रारीसाठी पैसे, कार्ड तपशील, पिन "
                + "किंवा ओटीपी मागणार नाही.");

        m.put("faq.q_complain_to_re_first", "मला प्रथम माझ्या बँकेकडे किंवा वित्त कंपनीकडे तक्रार करावी लागेल का?");
        m.put("faq.a_complain_to_re_first", "होय. तुम्हाला प्रथम संबंधित नियमित संस्थेकडे लिखित किंवा "
                + "इलेक्ट्रॉनिक तक्रार दाखल करावी लागेल. संस्थेने तुमची तक्रार नाकारल्यास, उत्तराने तुमचे समाधान "
                + "न झाल्यास, किंवा तक्रारीच्या 30 दिवसांत उत्तर न मिळाल्यास तुम्ही लोकपालाकडे जाऊ शकता.");

        m.put("faq.q_documents", "मी कोणते तपशील आणि कागदपत्रे तयार ठेवावीत?");
        m.put("faq.a_documents", "नियमित संस्थेला पाठवलेल्या तक्रारीची प्रत आणि तारीख, कोणतेही उत्तर किंवा "
                + "स्मरणपत्र पत्रव्यवहार, आणि संस्थेने दिलेला पोचपावती संदर्भ तयार ठेवा. खाते विवरण किंवा पावत्या "
                + "यांसारखी कागदपत्रे लोकपालाला तुमच्या प्रकरणाचे लवकर मूल्यांकन करण्यास मदत करतात.");

        m.put("faq.q_languages", "मी पोर्टल माझ्या भाषेत वापरू शकतो का?");
        m.put("faq.a_languages", "होय. पानाच्या वरील भाषा निवडकाद्वारे तुम्ही इंग्रजी, हिंदी आणि इतर समर्थित "
                + "भारतीय भाषांमध्ये बदल करू शकता. तुम्ही कधीही भाषा बदलू शकता आणि भरलेली माहिती सुरक्षित राहते.");

        m.put("faq.q_accessibility", "मला मजकूर वाचण्यास अडचण येते. मी तो बदलू शकतो का?");
        m.put("faq.a_accessibility", "होय. प्रत्येक पानाच्या वरील साधनपट्टीद्वारे तुम्ही मजकुराचा आकार वाढवू "
                + "किंवा कमी करू शकता, उच्च-कॉन्ट्रास्ट दृश्यावर जाऊ शकता, आणि सुलभता पॅनेल उघडू शकता. जिथे प्रश्न "
                + "कायदेशीर भाषेत आहे, तिथे सोपी आवृत्तीही उपलब्ध आहे.");

        m.put("faq.q_no_reply", "संस्थेने माझ्या तक्रारीला कधीच उत्तर न दिल्यास काय?");
        m.put("faq.a_no_reply", "नियमित संस्थेला उत्तर देण्यासाठी 30 दिवसांची मुदत आहे. ही मुदत उत्तराशिवाय संपली "
                + "असेल, तर तुम्ही लोकपालाकडे तक्रार दाखल करू शकता. 30 दिवस पूर्ण होण्यापूर्वी तक्रार दाखल "
                + "केल्यास, पोर्टल तुम्हाला कोणत्या तारखेपासून दाखल करता येईल ते सांगेल.");

        m.put("faq.q_not_taken_up", "कोणत्या तक्रारी लोकपाल घेऊ शकत नाही?");
        m.put("faq.a_not_taken_up", "सामान्यतः तीच तक्रार लोकपाल, न्यायालय, न्यायाधिकरण किंवा मध्यस्थासमोर आधीच "
                + "प्रलंबित असल्यास किंवा निकाली निघाली असल्यास; ती नियमित संस्थेशी नियोक्ता-कर्मचारी संबंधाशी "
                + "संबंधित असल्यास; किंवा तुम्ही प्रथम संस्थेकडे तक्रार केली नसल्यास, तक्रार घेतली जाऊ शकत नाही. "
                + "पोर्टलवरील पात्रता प्रश्न फॉर्म पूर्ण करण्यापूर्वीच कारणासह तुम्हाला सांगतील.");

        m.put("faq.q_advocate", "माझ्यावतीने दुसरी व्यक्ती तक्रार दाखल करू शकते का?");
        m.put("faq.a_advocate", "तक्रार तक्रारदाराने स्वतः दाखल करावी. तक्रार वकिलामार्फत केली जात असल्याचे "
                + "तुम्ही सांगितल्यास, तुम्ही तक्रारदार आहात याची पुष्टी करण्यास सांगितले जाईल; तुम्ही नसल्यास "
                + "तक्रारीवर कार्यवाही होऊ शकत नाही.");

        m.put("faq.q_not_eligible", "पोर्टल म्हणते की माझी तक्रार विचारार्ह नाही. आता काय?");
        m.put("faq.a_not_eligible", "तुम्हाला विशिष्ट कारण आणि त्या निर्णयाचा संदर्भ क्रमांक दाखवला जाईल. या "
                + "योजनेअंतर्गत विचारार्ह नसणे याचा अर्थ तुमची तक्रार अवैध आहे असा नाही — तुम्ही ती नियमित "
                + "संस्थेकडे किंवा उपलब्ध अन्य उपायाद्वारे पुढे नेऊ शकता.");

        m.put("faq.q_track", "मी माझ्या तक्रारीची स्थिती कशी पाहू?");
        m.put("faq.a_track", "ट्रॅक पान उघडा. तुम्ही तुमच्या तक्रार संदर्भ क्रमांकाने शोधू शकता, किंवा ओटीपीद्वारे "
                + "नोंदणीकृत मोबाइल क्रमांक पडताळून त्या क्रमांकावरून दाखल केलेल्या सर्व तक्रारी पाहू शकता.");

        m.put("faq.q_track_anonymous", "मी लॉग इन न करता स्थिती पाहू शकतो का?");
        m.put("faq.a_track_anonymous", "होय. वैध तक्रार संदर्भ क्रमांक टाकल्यास सध्याची स्थिती लॉग इन न करता "
                + "दिसते. तुमच्या सुरक्षेसाठी तक्रारदाराचे नाव आणि मोबाइल क्रमांक यांसारखे वैयक्तिक तपशील "
                + "लपवलेले असतात, जोपर्यंत तुम्ही ओटीपीद्वारे मोबाइल क्रमांक पडताळत नाही.");

        m.put("faq.q_withdraw", "मी आधीच दाखल केलेली तक्रार मागे घेऊ शकतो का?");
        m.put("faq.a_withdraw", "होय. ओटीपीद्वारे मोबाइल क्रमांक पडताळा, तुमच्या तक्रार इतिहासातून तक्रार उघडा "
                + "आणि मागे घेण्याचा पर्याय निवडा. तक्रार केवळ ती दाखल केलेल्या तक्रारदारालाच मागे घेता येते.");

        m.put("faq.q_contact", "पोर्टल वापरण्यात मदतीसाठी कोणाशी संपर्क साधावा?");
        m.put("faq.a_contact", "प्रत्येक पानाच्या वरील टोल-फ्री हेल्पलाइन 14448 वर कॉल करा. कृपया तुमचा तक्रार "
                + "संदर्भ क्रमांक तयार ठेवा जेणेकरून तुमच्या प्रश्नाचे लवकर उत्तर देता येईल.");

        m.put("faq.q_data_use", "माझ्या वैयक्तिक माहितीचा वापर कसा होतो?");
        m.put("faq.a_data_use", "तुमचे तपशील केवळ तुमची तक्रार नोंदवण्यासाठी आणि त्यावर कार्यवाही करण्यासाठी "
                + "गोळा केले जातात आणि त्याच उद्देशाने संबंधित नियमित संस्थेसोबत सामायिक केले जातात. सादर "
                + "करण्यापूर्वी तुम्हाला संमती सूचना दाखवली जाते ज्यात काय गोळा केले जाते आणि का हे स्पष्ट केले "
                + "जाते, आणि तुमची संमती नोंदवली जाते.");

        m.put("faq.q_withdraw_consent", "मी दिलेली संमती मागे घेऊ शकतो का?");
        m.put("faq.a_withdraw_consent", "होय. तुम्ही संमती मागे घेऊ शकता, परंतु त्यामुळे तुमच्या तक्रारीवर पुढील "
                + "कार्यवाही होऊ शकणार नाही, कारण नियमित संस्थेसोबत तक्रारीची तपासणी करण्यासाठी तुम्ही दिलेले "
                + "तपशील आवश्यक आहेत.");

        m.put("faq.q_session", "फॉर्म भरत असताना मला साइन आउट का केले गेले?");
        m.put("faq.a_session", "तुमच्या सुरक्षेसाठी निष्क्रियतेच्या कालावधीनंतर सत्र संपते आणि त्यापूर्वी थोड्या "
                + "वेळ आधी तुम्हाला सूचना दिली जाते. पानावर काम करत राहिल्याने सत्र सक्रिय राहते. साइन आउट "
                + "झाल्यास पुढे जाण्यासाठी तुमचा मोबाइल क्रमांक पुन्हा पडताळा.");

        m.put("faq.q_phishing", "तक्रारीबद्दलचा संदेश खरा आहे हे मला कसे कळेल?");
        m.put("faq.a_phishing", "तुमच्या तक्रारीबद्दलच्या संदेशात तुमचा तक्रार क्रमांक असेल आणि त्यात कधीही "
                + "तुमचा कार्ड क्रमांक, पिन, पासवर्ड, पूर्ण खाते क्रमांक किंवा ओटीपी मागितला जाणार नाही. ओटीपी "
                + "कोणाशीही सामायिक करू नका, कॉल करणारा स्वतःला रिझर्व्ह बँकेचा म्हणत असला तरीही.");

        applyLocale(m, "mr");
    }

    /** Insert-if-absent on questionKey so operator edits and sort-order changes survive restarts. */
    private void faq(int sortOrder, String category, String questionKey, String answerKey,
                     String question, String answer) {
        seedKey(questionKey, "faq", "FAQ question", question);
        seedKey(answerKey, "faq", "FAQ answer", answer);

        boolean exists = faqRepo.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .anyMatch(f -> questionKey.equals(f.getQuestionKey()));
        if (exists) return;

        faqRepo.save(Faq.builder()
                .questionKey(questionKey)
                .answerKey(answerKey)
                .category(category)
                .sortOrder(sortOrder)
                .isActive(true)
                .build());
    }

    private void seedKey(String code, String module, String description, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(module);
        key.setDescription(description);
        key.setDefaultValue(defaultValue);
        keyRepo.save(key);
    }

    private void applyLocale(Map<String, String> values, String locale) {
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
