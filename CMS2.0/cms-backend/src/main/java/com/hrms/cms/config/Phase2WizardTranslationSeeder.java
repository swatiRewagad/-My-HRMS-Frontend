package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.LinkedHashMap;

@Component
@Order(4)
public class Phase2WizardTranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public Phase2WizardTranslationSeeder(TranslationKeyRepository keyRepo, TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // ── Eligibility Wizard (Portal 2) ──
        seedIfAbsent("wizard.title", "wizard", "Wizard page title", "Can RBI help you?");
        seedIfAbsent("wizard.subtitle", "wizard", "Wizard page subtitle", "Answer a few quick questions to check if you can file a complaint with the RBI Ombudsman");
        seedIfAbsent("wizard.step", "wizard", "Step label", "Step");
        seedIfAbsent("wizard.select_placeholder", "wizard", "Select dropdown placeholder", "Select an option...");
        seedIfAbsent("wizard.check_eligibility", "wizard", "Check eligibility button", "Check Eligibility");
        seedIfAbsent("wizard.checking", "wizard", "Checking status", "Checking your eligibility...");
        seedIfAbsent("wizard.proceed_to_file", "wizard", "Proceed to file button", "Proceed to File Complaint");
        seedIfAbsent("wizard.start_over", "wizard", "Start over button", "Start Over");
        seedIfAbsent("wizard.go_home", "wizard", "Go home link", "Return to Home");
        seedIfAbsent("wizard.soft_block_notice", "wizard", "Soft block notice", "This is advisory only — you can still proceed to file a complaint if you believe your case deserves consideration.");

        // Wizard questions
        seedIfAbsent("wizard.q1_entity_type", "wizard", "Question 1", "What type of financial institution is your complaint against?");
        seedIfAbsent("wizard.q1_hint", "wizard", "Question 1 hint", "Select the type of RBI-regulated entity you have a grievance against.");
        seedIfAbsent("wizard.q2_complained_to_re", "wizard", "Question 2", "Have you already complained to this institution?");
        seedIfAbsent("wizard.q2_hint", "wizard", "Question 2 hint", "RBI requires that you first approach the institution directly before filing with the Ombudsman.");
        seedIfAbsent("wizard.q3_complaint_date", "wizard", "Question 3", "When did you complain to the institution?");
        seedIfAbsent("wizard.q3_hint", "wizard", "Question 3 hint", "Enter the date you first filed your written/electronic complaint with the bank or NBFC.");
        seedIfAbsent("wizard.q4_re_response", "wizard", "Question 4", "How did the institution respond?");
        seedIfAbsent("wizard.q4_hint", "wizard", "Question 4 hint", "Select the option that best describes the response you received from the institution.");

        // Question options
        seedIfAbsent("wizard.opt_bank", "wizard", "Q1 option: Bank", "Commercial Bank");
        seedIfAbsent("wizard.opt_nbfc", "wizard", "Q1 option: NBFC", "Non-Banking Financial Company (NBFC)");
        seedIfAbsent("wizard.opt_psp", "wizard", "Q1 option: PSP", "Payment System Participant (UPI/Wallet)");
        seedIfAbsent("wizard.opt_cic", "wizard", "Q1 option: CIC", "Credit Information Company");
        seedIfAbsent("wizard.opt_unknown", "wizard", "Q1 option: Unknown", "I'm not sure");
        seedIfAbsent("wizard.opt_yes_complained", "wizard", "Q2 option: Yes", "Yes, I have complained");
        seedIfAbsent("wizard.opt_no_not_yet", "wizard", "Q2 option: No", "No, not yet");
        seedIfAbsent("wizard.opt_no_reply", "wizard", "Q4 option: No reply", "No reply received");
        seedIfAbsent("wizard.opt_dissatisfied", "wizard", "Q4 option: Dissatisfied", "Replied but I am not satisfied");
        seedIfAbsent("wizard.opt_resolved", "wizard", "Q4 option: Resolved", "Replied and issue is resolved");

        // Timeline
        seedIfAbsent("wizard.timeline_title", "wizard", "Timeline section title", "YOUR FILING TIMELINE");
        seedIfAbsent("wizard.timeline_step1", "wizard", "Timeline step 1", "Complained to Institution");
        seedIfAbsent("wizard.timeline_step1_desc", "wizard", "Timeline step 1 desc", "Filed written complaint with the Regulated Entity");
        seedIfAbsent("wizard.timeline_step2", "wizard", "Timeline step 2", "Wait Period (30 days)");
        seedIfAbsent("wizard.timeline_step2_desc", "wizard", "Timeline step 2 desc", "Allow the institution 30 days to respond");
        seedIfAbsent("wizard.timeline_step3", "wizard", "Timeline step 3", "File with RBI Ombudsman");
        seedIfAbsent("wizard.timeline_step3_desc", "wizard", "Timeline step 3 desc", "Eligible to file within 1 year of the complaint date");
        seedIfAbsent("wizard.days_remaining", "wizard", "Days remaining label", "days remaining");
        seedIfAbsent("wizard.compensation_title", "wizard", "Compensation title", "Potential Compensation");

        // ── OTP / Login ──
        seedIfAbsent("login.title", "login", "Login page title", "Verification");
        seedIfAbsent("login.verify_phone", "login", "Verify phone heading", "Verify Phone Number");
        seedIfAbsent("login.mobile_label", "login", "Mobile label", "Mobile Number");
        seedIfAbsent("login.send_otp", "login", "Send OTP button", "Send OTP");
        seedIfAbsent("login.sending", "login", "Sending status", "Sending...");
        seedIfAbsent("login.enter_otp", "login", "Enter OTP label", "Enter OTP");
        seedIfAbsent("login.verify_otp", "login", "Verify OTP button", "Verify OTP");
        seedIfAbsent("login.verifying", "login", "Verifying status", "Verifying...");
        seedIfAbsent("login.resend_otp", "login", "Resend OTP", "Resend OTP");
        seedIfAbsent("login.change_number", "login", "Change number", "Change Number");
        seedIfAbsent("login.otp_sent_to", "login", "OTP sent message", "OTP sent to");
        seedIfAbsent("login.email_fallback", "login", "Email fallback link", "Send OTP to email instead");
        seedIfAbsent("login.dev_otp_notice", "login", "Dev OTP auto-populated notice", "OTP auto-populated (dev mode)");

        // ── Portal navigation ──
        seedIfAbsent("portal.portal1_title", "portal", "Portal 1 title", "Public Portal 1");
        seedIfAbsent("portal.portal1_desc", "portal", "Portal 1 description", "Standard complaint filing — eligibility check, multi-step form, track, withdraw, appeal");
        seedIfAbsent("portal.portal2_title", "portal", "Portal 2 title", "Public Portal 2");
        seedIfAbsent("portal.portal2_desc", "portal", "Portal 2 description", "\"Can RBI help?\" — guided eligibility wizard with timeline visualization before filing");

        seedHindiTranslations();
        seedMarathiTranslations();
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
        hi.put("wizard.title", "क्या RBI आपकी मदद कर सकता है?");
        hi.put("wizard.subtitle", "RBI लोकपाल के पास शिकायत दर्ज करने की पात्रता जांचने के लिए कुछ प्रश्नों का उत्तर दें");
        hi.put("wizard.step", "चरण");
        hi.put("wizard.select_placeholder", "एक विकल्प चुनें...");
        hi.put("wizard.check_eligibility", "पात्रता जांचें");
        hi.put("wizard.checking", "आपकी पात्रता जांची जा रही है...");
        hi.put("wizard.proceed_to_file", "शिकायत दर्ज करें");
        hi.put("wizard.start_over", "फिर से शुरू करें");
        hi.put("wizard.go_home", "होम पर लौटें");
        hi.put("wizard.soft_block_notice", "यह केवल सलाहकारी है — यदि आपको लगता है कि आपका मामला विचारणीय है तो आप शिकायत दर्ज कर सकते हैं।");

        hi.put("wizard.q1_entity_type", "आपकी शिकायत किस प्रकार की वित्तीय संस्था के विरुद्ध है?");
        hi.put("wizard.q1_hint", "उस RBI-विनियमित संस्था का प्रकार चुनें जिसके विरुद्ध आपकी शिकायत है।");
        hi.put("wizard.q2_complained_to_re", "क्या आपने पहले से इस संस्था में शिकायत की है?");
        hi.put("wizard.q2_hint", "RBI की आवश्यकता है कि आप पहले संस्था से सीधे संपर्क करें।");
        hi.put("wizard.q3_complaint_date", "आपने संस्था में कब शिकायत की?");
        hi.put("wizard.q3_hint", "वह तारीख दर्ज करें जब आपने बैंक या NBFC में लिखित/इलेक्ट्रॉनिक शिकायत दर्ज की।");
        hi.put("wizard.q4_re_response", "संस्था ने कैसा उत्तर दिया?");
        hi.put("wizard.q4_hint", "वह विकल्प चुनें जो संस्था से प्राप्त उत्तर का सबसे अच्छा वर्णन करता है।");

        hi.put("wizard.opt_bank", "वाणिज्यिक बैंक");
        hi.put("wizard.opt_nbfc", "गैर-बैंकिंग वित्तीय कंपनी (NBFC)");
        hi.put("wizard.opt_psp", "भुगतान प्रणाली प्रतिभागी (UPI/वॉलेट)");
        hi.put("wizard.opt_cic", "क्रेडिट सूचना कंपनी");
        hi.put("wizard.opt_unknown", "मुझे पता नहीं");
        hi.put("wizard.opt_yes_complained", "हाँ, मैंने शिकायत की है");
        hi.put("wizard.opt_no_not_yet", "नहीं, अभी नहीं");
        hi.put("wizard.opt_no_reply", "कोई उत्तर नहीं मिला");
        hi.put("wizard.opt_dissatisfied", "उत्तर मिला लेकिन संतुष्ट नहीं हूँ");
        hi.put("wizard.opt_resolved", "उत्तर मिला और समस्या हल हो गई");

        hi.put("wizard.timeline_title", "आपकी शिकायत दर्ज करने की समय-सीमा");
        hi.put("wizard.timeline_step1", "संस्था में शिकायत की");
        hi.put("wizard.timeline_step1_desc", "विनियमित संस्था में लिखित शिकायत दर्ज की");
        hi.put("wizard.timeline_step2", "प्रतीक्षा अवधि (30 दिन)");
        hi.put("wizard.timeline_step2_desc", "संस्था को उत्तर देने के लिए 30 दिन दें");
        hi.put("wizard.timeline_step3", "RBI लोकपाल में दर्ज करें");
        hi.put("wizard.timeline_step3_desc", "शिकायत तिथि से 1 वर्ष के भीतर दर्ज करने के पात्र");
        hi.put("wizard.days_remaining", "दिन शेष");
        hi.put("wizard.compensation_title", "संभावित मुआवज़ा");

        hi.put("login.title", "सत्यापन");
        hi.put("login.verify_phone", "फ़ोन नंबर सत्यापित करें");
        hi.put("login.mobile_label", "मोबाइल नंबर");
        hi.put("login.send_otp", "OTP भेजें");
        hi.put("login.sending", "भेजा जा रहा है...");
        hi.put("login.enter_otp", "OTP दर्ज करें");
        hi.put("login.verify_otp", "OTP सत्यापित करें");
        hi.put("login.verifying", "सत्यापित किया जा रहा है...");
        hi.put("login.resend_otp", "OTP पुनः भेजें");
        hi.put("login.change_number", "नंबर बदलें");
        hi.put("login.otp_sent_to", "OTP भेजा गया");
        hi.put("login.email_fallback", "ईमेल पर OTP भेजें");

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
        mr.put("wizard.title", "RBI तुमची मदत करू शकते का?");
        mr.put("wizard.subtitle", "RBI लोकपालकडे तक्रार दाखल करण्याची पात्रता तपासण्यासाठी काही प्रश्नांची उत्तरे द्या");
        mr.put("wizard.step", "टप्पा");
        mr.put("wizard.select_placeholder", "एक पर्याय निवडा...");
        mr.put("wizard.check_eligibility", "पात्रता तपासा");
        mr.put("wizard.checking", "तुमची पात्रता तपासली जात आहे...");
        mr.put("wizard.proceed_to_file", "तक्रार दाखल करा");
        mr.put("wizard.start_over", "पुन्हा सुरू करा");
        mr.put("wizard.go_home", "मुख्यपृष्ठावर परत जा");

        mr.put("wizard.q1_entity_type", "तुमची तक्रार कोणत्या प्रकारच्या वित्तीय संस्थेविरुद्ध आहे?");
        mr.put("wizard.q1_hint", "तुमची तक्रार असलेल्या RBI-नियंत्रित संस्थेचा प्रकार निवडा.");
        mr.put("wizard.q2_complained_to_re", "तुम्ही आधीच या संस्थेकडे तक्रार केली आहे का?");
        mr.put("wizard.q2_hint", "RBI ची आवश्यकता आहे की तुम्ही प्रथम संस्थेशी थेट संपर्क साधा.");
        mr.put("wizard.q3_complaint_date", "तुम्ही संस्थेकडे कधी तक्रार केली?");
        mr.put("wizard.q3_hint", "बँक किंवा NBFC कडे लिखित/इलेक्ट्रॉनिक तक्रार दाखल केल्याची तारीख प्रविष्ट करा.");
        mr.put("wizard.q4_re_response", "संस्थेने कसा प्रतिसाद दिला?");
        mr.put("wizard.q4_hint", "संस्थेकडून मिळालेल्या प्रतिसादाचे सर्वोत्तम वर्णन करणारा पर्याय निवडा.");

        mr.put("wizard.opt_bank", "व्यापारी बँक");
        mr.put("wizard.opt_nbfc", "बिगर-बँकिंग वित्तीय कंपनी (NBFC)");
        mr.put("wizard.opt_psp", "पेमेंट सिस्टम सहभागी (UPI/वॉलेट)");
        mr.put("wizard.opt_cic", "क्रेडिट माहिती कंपनी");
        mr.put("wizard.opt_unknown", "मला माहित नाही");
        mr.put("wizard.opt_yes_complained", "होय, मी तक्रार केली आहे");
        mr.put("wizard.opt_no_not_yet", "नाही, अजून नाही");
        mr.put("wizard.opt_no_reply", "कोणताही प्रतिसाद मिळाला नाही");
        mr.put("wizard.opt_dissatisfied", "प्रतिसाद मिळाला पण समाधानी नाही");
        mr.put("wizard.opt_resolved", "प्रतिसाद मिळाला आणि समस्या सोडवली");

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
}
