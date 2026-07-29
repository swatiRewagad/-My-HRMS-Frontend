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
        seedIfAbsent("eligibility.block_not_filed", "eligibility", "Block: not filed", "in terms of clause 10(1)(j) of Reserve Bank – Integrated Ombudsman Scheme, 2026, the complaint cannot be processed under the Scheme.");
        seedIfAbsent("eligibility.block_sub_judice", "eligibility", "Block: sub-judice", "As your complaint is sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause 10(2)(b)(ii) of the Reserve Bank - Integrated Ombudsman Scheme, 2021.");
        seedIfAbsent("eligibility.block_already_settled", "eligibility", "Block: already settled", "As your complaint has already been settled or dealt with by a Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under the Reserve Bank - Integrated Ombudsman Scheme, 2021.");
        seedIfAbsent("eligibility.block_pending_ombudsman", "eligibility", "Block: pending ombudsman", "Your complaint is already pending before the Ombudsman on the same grievance. Duplicate complaints cannot be filed.");
        seedIfAbsent("eligibility.block_settled_ombudsman", "eligibility", "Block: settled ombudsman", "Your complaint has already been settled or dealt with on merits by the Ombudsman. You cannot file a fresh complaint on the same issue.");
        seedIfAbsent("eligibility.block_staff_of_re", "eligibility", "Block: staff of RE", "Complaints involving employer-employee relationship between the complainant and the Regulated Entity cannot be filed under the Integrated Ombudsman Scheme.");

        // Block-note surrounding text
        seedIfAbsent("eligibility.block_indicated", "eligibility", "Block: as you have indicated", "As you have indicated");
        seedIfAbsent("eligibility.block_in_response", "eligibility", "Block: in response to query", "in response to this query,");
        seedIfAbsent("eligibility.block_written_required", "eligibility", "Block: written complaint required", "A written/electronic complaint is required to be filed with the Regulated Entity first.");
        seedIfAbsent("eligibility.block_regret", "eligibility", "Block: regret message", "Accordingly, we regret to inform you that your present grievance against");
        seedIfAbsent("eligibility.block_cannot_register", "eligibility", "Block: cannot register", "cannot be registered under the Scheme. In case the response was furnished erroneously, you may change the response.");
        seedIfAbsent("eligibility.block_regards", "eligibility", "Block: regards", "Regards, RBI CMS Team.");
        seedIfAbsent("eligibility.show_closure_letter", "eligibility", "Show closure letter button", "Show Closure Letter");
        seedIfAbsent("eligibility.passed_title", "eligibility", "Eligibility passed title", "Eligibility Passed");
        seedIfAbsent("eligibility.passed_message", "eligibility", "Eligibility passed message", "You are eligible to file a complaint under RBI Integrated Ombudsman Scheme, 2026.");

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

        hi.put("eligibility.block_not_filed", "रिज़र्व बैंक – एकीकृत लोकपाल योजना, 2026 के खंड 10(1)(j) के अनुसार, शिकायत को योजना के तहत संसाधित नहीं किया जा सकता।");
        hi.put("eligibility.block_sub_judice", "चूंकि आपकी शिकायत न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण के समक्ष लंबित है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के खंड 10(2)(b)(ii) के तहत अस्वीकार्य के रूप में बंद किया जाएगा।");
        hi.put("eligibility.block_already_settled", "चूंकि आपकी शिकायत पहले ही न्यायालय/न्यायाधिकरण/मध्यस्थ/प्राधिकरण द्वारा निपटाई जा चुकी है, इसे रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2021 के तहत अस्वीकार्य के रूप में बंद किया जाएगा।");
        hi.put("eligibility.block_pending_ombudsman", "आपकी शिकायत पहले से उसी विवाद पर लोकपाल के समक्ष लंबित है। डुप्लिकेट शिकायत दर्ज नहीं की जा सकती।");
        hi.put("eligibility.block_settled_ombudsman", "आपकी शिकायत लोकपाल द्वारा पहले ही गुण-दोष के आधार पर निपटाई जा चुकी है। इसी मुद्दे पर नई शिकायत दर्ज नहीं की जा सकती।");
        hi.put("eligibility.block_staff_of_re", "विनियमित संस्था के कर्मचारी और नियोक्ता-कर्मचारी संबंध से जुड़ी शिकायतें एकीकृत लोकपाल योजना के तहत दर्ज नहीं की जा सकतीं।");

        hi.put("eligibility.block_indicated", "जैसा कि आपने");
        hi.put("eligibility.block_in_response", "इस प्रश्न के उत्तर में बताया है,");
        hi.put("eligibility.block_written_required", "विनियमित संस्था के पास पहले लिखित/इलेक्ट्रॉनिक शिकायत दर्ज करना आवश्यक है।");
        hi.put("eligibility.block_regret", "तदनुसार, हम आपको सूचित करते हैं कि आपकी वर्तमान शिकायत");
        hi.put("eligibility.block_cannot_register", "के विरुद्ध योजना के तहत पंजीकृत नहीं की जा सकती। यदि उत्तर गलती से दिया गया था, तो आप उत्तर बदल सकते हैं।");
        hi.put("eligibility.block_regards", "सादर, RBI CMS टीम।");
        hi.put("eligibility.show_closure_letter", "बंद करने का पत्र दिखाएं");
        hi.put("eligibility.passed_title", "पात्रता पारित");
        hi.put("eligibility.passed_message", "आप RBI एकीकृत लोकपाल योजना, 2026 के तहत शिकायत दर्ज करने के पात्र हैं।");
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

        mr.put("eligibility.block_not_filed", "रिझर्व्ह बँक – एकीकृत लोकपाल योजना, 2026 च्या कलम 10(1)(j) नुसार, तक्रार योजनेअंतर्गत प्रक्रिया करता येत नाही.");
        mr.put("eligibility.block_sub_judice", "तुमची तक्रार न्यायालय/न्यायाधिकरण/लवाद/प्राधिकरणासमोर प्रलंबित असल्याने, ती रिझर्व्ह बँक - एकीकृत लोकपाल योजना, 2021 च्या कलम 10(2)(b)(ii) अंतर्गत अस्वीकार्य म्हणून बंद केली जाईल.");
        mr.put("eligibility.block_already_settled", "तुमची तक्रार आधीच न्यायालय/न्यायाधिकरण/लवाद/प्राधिकरणाद्वारे निकाली काढली गेली असल्याने, ती रिझर्व्ह बँक - एकीकृत लोकपाल योजना, 2021 अंतर्गत अस्वीकार्य म्हणून बंद केली जाईल.");
        mr.put("eligibility.block_pending_ombudsman", "तुमची तक्रार आधीच त्याच तक्रारीवर लोकपालासमोर प्रलंबित आहे. डुप्लिकेट तक्रार दाखल करता येत नाही.");
        mr.put("eligibility.block_settled_ombudsman", "तुमची तक्रार लोकपालाने आधीच गुणवत्तेवर निकाली काढली आहे. याच मुद्द्यावर नवीन तक्रार दाखल करता येत नाही.");
        mr.put("eligibility.block_staff_of_re", "नियमित संस्थेचे कर्मचारी आणि नियोक्ता-कर्मचारी संबंधांशी संबंधित तक्रारी एकीकृत लोकपाल योजनेअंतर्गत दाखल करता येत नाहीत.");

        mr.put("eligibility.block_indicated", "तुम्ही");
        mr.put("eligibility.block_in_response", "या प्रश्नाच्या उत्तरात सूचित केल्याप्रमाणे,");
        mr.put("eligibility.block_written_required", "नियमित संस्थेकडे प्रथम लिखित/इलेक्ट्रॉनिक तक्रार दाखल करणे आवश्यक आहे.");
        mr.put("eligibility.block_regret", "त्यानुसार, आम्ही तुम्हाला कळवतो की तुमची सध्याची तक्रार");
        mr.put("eligibility.block_cannot_register", "विरुद्ध योजनेअंतर्गत नोंदणी करता येत नाही. उत्तर चुकून दिले असल्यास, तुम्ही उत्तर बदलू शकता.");
        mr.put("eligibility.block_regards", "सादर, RBI CMS टीम.");
        mr.put("eligibility.show_closure_letter", "बंद करण्याचे पत्र दाखवा");
        mr.put("eligibility.passed_title", "पात्रता उत्तीर्ण");
        mr.put("eligibility.passed_message", "तुम्ही RBI एकीकृत लोकपाल योजना, 2026 अंतर्गत तक्रार दाखल करण्यास पात्र आहात.");
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
        m.put("eligibility.q_select_re", "নিয়ন্ত্রিত সংস্থার নাম নির্বাচন করুন");
        m.put("eligibility.q_filed_with_re", "আপনি কি {{reName}}-এ লিখিত/ইলেকট্রনিক অভিযোগ দায়ের করেছেন?");
        m.put("eligibility.q_received_reply", "আপনি কি সংস্থা থেকে কোনো উত্তর পেয়েছেন?");
        m.put("eligibility.q_sent_reminder", "আপনি কি {{reName}}-কে কোনো স্মারকপত্র পাঠিয়েছেন?");
        m.put("eligibility.q_sub_judice", "এই অভিযোগটি কি ইতিমধ্যে কোনো আদালত, ট্রাইব্যুনাল, সালিশ বা অন্য কোনো বিচারিক বা আধা-বিচারিক ফোরামে বিচারাধীন (ফৌজদারি কার্যক্রম ব্যতীত)?");
        m.put("eligibility.q_through_advocate", "আপনার অভিযোগ কি কোনো আইনজীবীর মাধ্যমে করা হচ্ছে?");
        m.put("eligibility.q_pending_ombudsman", "এই অভিযোগটি কি ইতিমধ্যে একই বিষয়ে ওম্বডসম্যানের কাছে বিচারাধীন?");
        m.put("eligibility.q_settled_ombudsman", "এই অভিযোগটি কি ইতিমধ্যে ওম্বডসম্যান দ্বারা গুণবিচারে নিষ্পত্তি হয়েছে?");
        m.put("eligibility.q_staff_of_re", "অভিযোগকারী কি নিয়ন্ত্রিত সংস্থার কর্মচারী এবং অভিযোগটি নিয়োগকর্তা-কর্মচারী সম্পর্কের সাথে সম্পর্কিত?");
        m.put("eligibility.sub_complaint_date", "যে তারিখে প্রথম অভিযোগ দায়ের করা হয়েছিল");
        m.put("eligibility.sub_upload_complaint", "-কে পাঠানো অভিযোগের অনুলিপি আপলোড করুন");
        m.put("eligibility.sub_reminder_date", "যে তারিখে স্মারকপত্র পাঠানো হয়েছিল");
        m.put("eligibility.sub_upload_reminder", "স্মারকপত্রের অনুলিপি আপলোড করুন");
        m.put("eligibility.sub_reply_date", "যে তারিখে উত্তর পাওয়া গেছে");
        m.put("eligibility.sub_upload_reply", "উত্তরের অনুলিপি আপলোড করুন");
        m.put("eligibility.sub_are_you_complainant", "হ্যাঁ হলে, আপনি কি নিজে অভিযোগকারী?");
        m.put("eligibility.block_not_filed", "রিজার্ভ ব্যাংক – সমন্বিত ওম্বডসম্যান স্কিম, ২০২৬-এর ধারা ১০(১)(জে) অনুসারে, এই অভিযোগ স্কিমের অধীনে প্রক্রিয়া করা যাবে না।");
        m.put("eligibility.block_sub_judice", "আপনার অভিযোগ আদালত/ট্রাইব্যুনাল/সালিশ/কর্তৃপক্ষের কাছে বিচারাধীন থাকায়, এটি রিজার্ভ ব্যাংক - সমন্বিত ওম্বডসম্যান স্কিম, ২০২১-এর ধারা ১০(২)(খ)(ii) অনুসারে অগ্রহণযোগ্য হিসেবে বন্ধ করা হবে।");
        m.put("eligibility.block_pending_ombudsman", "আপনার অভিযোগ ইতিমধ্যে একই বিষয়ে ওম্বডসম্যানের কাছে বিচারাধীন। ডুপ্লিকেট অভিযোগ দায়ের করা যাবে না।");
        m.put("eligibility.block_settled_ombudsman", "আপনার অভিযোগ ইতিমধ্যে ওম্বডসম্যান দ্বারা গুণবিচারে নিষ্পত্তি হয়েছে। একই বিষয়ে নতুন অভিযোগ দায়ের করা যাবে না।");
        m.put("eligibility.block_staff_of_re", "নিয়ন্ত্রিত সংস্থার কর্মচারী এবং নিয়োগকর্তা-কর্মচারী সম্পর্কের অভিযোগ সমন্বিত ওম্বডসম্যান স্কিমের অধীনে দায়ের করা যাবে না।");
        m.put("eligibility.block_indicated", "আপনি যেমন");
        m.put("eligibility.block_in_response", "এই প্রশ্নের উত্তরে জানিয়েছেন,");
        m.put("eligibility.block_written_required", "নিয়ন্ত্রিত সংস্থার কাছে প্রথমে লিখিত/ইলেকট্রনিক অভিযোগ দায়ের করা আবশ্যক।");
        m.put("eligibility.block_regret", "তদনুসারে, আমরা আপনাকে জানাচ্ছি যে আপনার বর্তমান অভিযোগ");
        m.put("eligibility.block_cannot_register", "এর বিরুদ্ধে স্কিমের অধীনে নিবন্ধিত করা যাবে না। যদি উত্তরটি ভুলবশত দেওয়া হয়ে থাকে, আপনি উত্তর পরিবর্তন করতে পারেন।");
        m.put("eligibility.block_regards", "শুভেচ্ছান্তে, RBI CMS টিম।");
        m.put("eligibility.show_closure_letter", "বন্ধের পত্র দেখুন");
        m.put("eligibility.passed_title", "যোগ্যতা উত্তীর্ণ");
        m.put("eligibility.passed_message", "আপনি RBI সমন্বিত ওম্বডসম্যান স্কিম, ২০২৬-এর অধীনে অভিযোগ দায়ের করতে যোগ্য।");
        m.put("layout.assistance_desc", "যোগাযোগ কেন্দ্র (#14448) ইন্টারেক্টিভ ভয়েস রেসপন্স সিস্টেম (IVRS) সহ 24x7 উপলব্ধ, যখন যোগাযোগ কেন্দ্রের কর্মীদের সাথে সংযোগের সুবিধা সোমবার থেকে শনিবার (জাতীয় ছুটি ব্যতীত) সকাল 8:00 থেকে রাত 10:00 পর্যন্ত ইংরেজি, হিন্দি এবং দশটি আঞ্চলিক ভাষায় উপলব্ধ।");
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
        m.put("eligibility.q_select_re", "నియంత్రిత సంస్థ పేరు ఎంచుకోండి");
        m.put("eligibility.q_filed_with_re", "మీరు {{reName}} వద్ద వ్రాతపూర్వక/ఎలక్ట్రానిక్ ఫిర్యాదు దాఖలు చేశారా?");
        m.put("eligibility.q_received_reply", "మీకు సంస్థ నుండి ఏదైనా సమాధానం వచ్చిందా?");
        m.put("eligibility.q_sent_reminder", "మీరు {{reName}}కు ఏదైనా రిమైండర్ పంపారా?");
        m.put("eligibility.q_sub_judice", "ఈ ఫిర్యాదు ఇప్పటికే ఏదైనా న్యాయస్థానం, ట్రిబ్యునల్, మధ్యవర్తి లేదా ఇతర న్యాయ లేదా అర్ధ-న్యాయ వేదిక ముందు పెండింగ్‌లో ఉందా (నేర విచారణలు మినహా)?");
        m.put("eligibility.q_through_advocate", "మీ ఫిర్యాదు న్యాయవాది ద్వారా చేయబడుతోందా?");
        m.put("eligibility.q_pending_ombudsman", "ఈ ఫిర్యాదు ఇప్పటికే అదే విషయంపై ఓంబుడ్స్‌మన్ ముందు పెండింగ్‌లో ఉందా?");
        m.put("eligibility.q_settled_ombudsman", "ఈ ఫిర్యాదు ఇప్పటికే ఓంబుడ్స్‌మన్ చేత గుణదోషాల ఆధారంగా పరిష్కరించబడిందా?");
        m.put("eligibility.q_staff_of_re", "ఫిర్యాదుదారు నియంత్రిత సంస్థ ఉద్యోగి మరియు ఫిర్యాదు యజమాని-ఉద్యోగి సంబంధానికి సంబంధించినదా?");
        m.put("eligibility.sub_complaint_date", "ఫిర్యాదు మొదట దాఖలు చేసిన తేదీ");
        m.put("eligibility.sub_upload_complaint", "కు పంపిన ఫిర్యాదు కాపీ అప్‌లోడ్ చేయండి");
        m.put("eligibility.sub_reminder_date", "రిమైండర్ పంపిన తేదీ");
        m.put("eligibility.sub_upload_reminder", "రిమైండర్ కాపీ అప్‌లోడ్ చేయండి");
        m.put("eligibility.sub_reply_date", "సమాధానం అందిన తేదీ");
        m.put("eligibility.sub_upload_reply", "సమాధానం కాపీ అప్‌లోడ్ చేయండి");
        m.put("eligibility.sub_are_you_complainant", "అవును అయితే, మీరే ఫిర్యాదుదారులా?");
        m.put("eligibility.block_not_filed", "రిజర్వ్ బ్యాంక్ – సమగ్ర ఓంబుడ్స్‌మన్ పథకం, 2026 క్లాజ్ 10(1)(j) ప్రకారం, ఫిర్యాదును పథకం కింద ప్రాసెస్ చేయడం సాధ్యం కాదు.");
        m.put("eligibility.block_sub_judice", "మీ ఫిర్యాదు న్యాయస్థానం/ట్రిబ్యునల్/మధ్యవర్తి/అధికారం ముందు పెండింగ్‌లో ఉన్నందున, ఇది అనర్హంగా మూసివేయబడుతుంది.");
        m.put("eligibility.block_pending_ombudsman", "మీ ఫిర్యాదు ఇప్పటికే అదే విషయంపై ఓంబుడ్స్‌మన్ ముందు పెండింగ్‌లో ఉంది. డూప్లికేట్ ఫిర్యాదు దాఖలు చేయలేరు.");
        m.put("eligibility.block_settled_ombudsman", "మీ ఫిర్యాదు ఇప్పటికే ఓంబుడ్స్‌మన్ చేత పరిష్కరించబడింది. అదే విషయంపై కొత్త ఫిర్యాదు దాఖలు చేయలేరు.");
        m.put("eligibility.block_staff_of_re", "నియంత్రిత సంస్థ ఉద్యోగి మరియు యజమాని-ఉద్యోగి సంబంధ ఫిర్యాదులు సమగ్ర ఓంబుడ్స్‌మన్ పథకం కింద దాఖలు చేయలేరు.");
        m.put("eligibility.block_indicated", "మీరు");
        m.put("eligibility.block_in_response", "ఈ ప్రశ్నకు సమాధానంగా తెలిపినట్లు,");
        m.put("eligibility.block_written_required", "నియంత్రిత సంస్థ వద్ద ముందుగా వ్రాతపూర్వక/ఎలక్ట్రానిక్ ఫిర్యాదు దాఖలు చేయడం అవసరం.");
        m.put("eligibility.block_regret", "తదనుగుణంగా, మీ ప్రస్తుత ఫిర్యాదు");
        m.put("eligibility.block_cannot_register", "పై పథకం కింద నమోదు చేయడం సాధ్యం కాదని మేము తెలియజేస్తున్నాము. సమాధానం పొరపాటున ఇచ్చినట్లయితే, మీరు సమాధానాన్ని మార్చవచ్చు.");
        m.put("eligibility.block_regards", "వందనాలు, RBI CMS బృందం.");
        m.put("eligibility.show_closure_letter", "మూసివేత పత్రం చూపించు");
        m.put("eligibility.passed_title", "అర్హత ఉత్తీర్ణం");
        m.put("eligibility.passed_message", "మీరు RBI సమగ్ర ఓంబుడ్స్‌మన్ పథకం, 2026 కింద ఫిర్యాదు దాఖలు చేయడానికి అర్హులు.");
        m.put("layout.assistance_desc", "సంప్రదింపు కేంద్రం (#14448) ఇంటరాక్టివ్ వాయిస్ రెస్పాన్స్ సిస్టమ్ (IVRS)తో 24x7 అందుబాటులో ఉంటుంది, అదే సమయంలో సంప్రదింపు కేంద్ర సిబ్బందితో అనుసంధానం చేసే సౌకర్యం సోమవారం నుండి శనివారం (జాతీయ సెలవులు మినహా) ఉదయం 8:00 నుండి రాత్రి 10:00 వరకు ఆంగ్లం, హిందీ మరియు పది ప్రాంతీయ భాషల్లో అందుబాటులో ఉంటుంది.");
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
        m.put("eligibility.q_select_re", "ஒழுங்குமுறை நிறுவனத்தின் பெயரைத் தேர்ந்தெடுக்கவும்");
        m.put("eligibility.q_filed_with_re", "நீங்கள் {{reName}} இல் எழுத்துப்பூர்வ/மின்னணு புகார் அளித்துள்ளீர்களா?");
        m.put("eligibility.q_received_reply", "நிறுவனத்திடம் இருந்து ஏதேனும் பதில் கிடைத்ததா?");
        m.put("eligibility.q_sent_reminder", "நீங்கள் {{reName}}க்கு ஏதேனும் நினைவூட்டல் அனுப்பினீர்களா?");
        m.put("eligibility.q_sub_judice", "இந்தப் புகார் ஏற்கனவே ஏதேனும் நீதிமன்றம், தீர்ப்பாயம், நடுவர் அல்லது பிற நீதி அல்லது அரை-நீதி மன்றத்தில் நிலுவையில் உள்ளதா (குற்றவியல் நடவடிக்கைகள் தவிர)?");
        m.put("eligibility.q_through_advocate", "உங்கள் புகார் வழக்கறிஞர் மூலம் தாக்கல் செய்யப்படுகிறதா?");
        m.put("eligibility.q_pending_ombudsman", "இந்தப் புகார் ஏற்கனவே அதே விஷயத்தில் குறைதீர்ப்பாளரிடம் நிலுவையில் உள்ளதா?");
        m.put("eligibility.q_settled_ombudsman", "இந்தப் புகார் ஏற்கனவே குறைதீர்ப்பாளரால் தீர்வு செய்யப்பட்டுள்ளதா?");
        m.put("eligibility.q_staff_of_re", "புகாரளிப்பவர் ஒழுங்குமுறை நிறுவனத்தின் ஊழியரா மற்றும் புகார் முதலாளி-ஊழியர் உறவு தொடர்பானதா?");
        m.put("eligibility.sub_complaint_date", "புகார் முதலில் பதிவு செய்த தேதி");
        m.put("eligibility.sub_upload_complaint", "க்கு அனுப்பிய புகாரின் நகலை பதிவேற்றவும்");
        m.put("eligibility.sub_reminder_date", "நினைவூட்டல் அனுப்பிய தேதி");
        m.put("eligibility.sub_upload_reminder", "நினைவூட்டல் நகலை பதிவேற்றவும்");
        m.put("eligibility.sub_reply_date", "பதில் பெற்ற தேதி");
        m.put("eligibility.sub_upload_reply", "பதில் நகலை பதிவேற்றவும்");
        m.put("eligibility.sub_are_you_complainant", "ஆம் என்றால், நீங்களே புகாரளிப்பவரா?");
        m.put("eligibility.block_not_filed", "ரிசர்வ் வங்கி – ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டம், 2026 பிரிவு 10(1)(j) படி, புகார் திட்டத்தின் கீழ் செயல்படுத்த முடியாது.");
        m.put("eligibility.block_sub_judice", "உங்கள் புகார் நீதிமன்றம்/தீர்ப்பாயம்/நடுவர்/அதிகாரத்தின் முன் நிலுவையில் உள்ளதால், இது தகுதியற்றதாக மூடப்படும்.");
        m.put("eligibility.block_pending_ombudsman", "உங்கள் புகார் ஏற்கனவே அதே விஷயத்தில் குறைதீர்ப்பாளரிடம் நிலுவையில் உள்ளது. நகல் புகார் தாக்கல் செய்ய முடியாது.");
        m.put("eligibility.block_settled_ombudsman", "உங்கள் புகார் ஏற்கனவே குறைதீர்ப்பாளரால் தீர்வு செய்யப்பட்டுள்ளது. அதே விஷயத்தில் புதிய புகார் தாக்கல் செய்ய முடியாது.");
        m.put("eligibility.block_staff_of_re", "ஒழுங்குமுறை நிறுவன ஊழியர் மற்றும் முதலாளி-ஊழியர் உறவு தொடர்பான புகார்கள் ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டத்தின் கீழ் தாக்கல் செய்ய முடியாது.");
        m.put("eligibility.block_indicated", "நீங்கள்");
        m.put("eligibility.block_in_response", "இந்த கேள்விக்கான பதிலாக தெரிவித்தபடி,");
        m.put("eligibility.block_written_required", "ஒழுங்குமுறை நிறுவனத்திடம் முதலில் எழுத்து/மின்னணு புகார் அளிக்க வேண்டும்.");
        m.put("eligibility.block_regret", "அதன்படி, உங்கள் தற்போதைய புகார்");
        m.put("eligibility.block_cannot_register", "மீது திட்டத்தின் கீழ் பதிவு செய்ய இயலாது என்பதை தெரிவிக்கிறோம். பதில் தவறுதலாக அளிக்கப்பட்டிருந்தால், நீங்கள் பதிலை மாற்றலாம்.");
        m.put("eligibility.block_regards", "வணக்கம், RBI CMS குழு.");
        m.put("eligibility.show_closure_letter", "முடிவுக் கடிதத்தைக் காட்டு");
        m.put("eligibility.passed_title", "தகுதி தேர்ச்சி");
        m.put("eligibility.passed_message", "நீங்கள் RBI ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டம், 2026 இன் கீழ் புகார் அளிக்க தகுதியானவர்.");
        m.put("layout.assistance_desc", "தொடர்பு மையம் (#14448) ஊடாடும் குரல் பதில் அமைப்பு (IVRS) மூலம் 24x7 கிடைக்கிறது, அதே நேரத்தில் தொடர்பு மைய பணியாளர்களுடன் இணைக்கும் வசதி திங்கள் முதல் சனி வரை (தேசிய விடுமுறைகள் தவிர) காலை 8:00 முதல் இரவு 10:00 வரை ஆங்கிலம், இந்தி மற்றும் பத்து பிராந்திய மொழிகளில் கிடைக்கிறது.");
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
        m.put("eligibility.q_select_re", "નિયંત્રિત સંસ્થાનું નામ પસંદ કરો");
        m.put("eligibility.q_filed_with_re", "શું તમે {{reName}} પાસે લેખિત/ઈલેક્ટ્રોનિક ફરિયાદ દાખલ કરી છે?");
        m.put("eligibility.q_received_reply", "શું તમને સંસ્થા તરફથી કોઈ જવાબ મળ્યો છે?");
        m.put("eligibility.q_sent_reminder", "શું તમે {{reName}}ને કોઈ રીમાઇન્ડર મોકલ્યું છે?");
        m.put("eligibility.q_sub_judice", "શું આ ફરિયાદ પહેલેથી કોઈ અદાલત, ટ્રિબ્યુનલ, આર્બિટ્રેટર અથવા અન્ય ન્યાયિક ફોરમ સમક્ષ પેન્ડિંગ છે (ફોજદારી કાર્યવાહી સિવાય)?");
        m.put("eligibility.q_through_advocate", "શું તમારી ફરિયાદ વકીલ મારફતે કરવામાં આવી રહી છે?");
        m.put("eligibility.q_pending_ombudsman", "શું આ ફરિયાદ પહેલેથી એ જ વિષય પર ઓમ્બડ્સમેન સમક્ષ પેન્ડિંગ છે?");
        m.put("eligibility.q_settled_ombudsman", "શું આ ફરિયાદ પહેલેથી ઓમ્બડ્સમેન દ્વારા ગુણદોષ પર નિકાલ કરવામાં આવી છે?");
        m.put("eligibility.q_staff_of_re", "શું ફરિયાદી નિયંત્રિત સંસ્થાનો કર્મચારી છે અને ફરિયાદ નોકરીદાતા-કર્મચારી સંબંધ સાથે સંબંધિત છે?");
        m.put("eligibility.sub_complaint_date", "જે તારીખે ફરિયાદ પ્રથમ દાખલ કરવામાં આવી");
        m.put("eligibility.sub_upload_complaint", "ને મોકલેલી ફરિયાદની નકલ અપલોડ કરો");
        m.put("eligibility.sub_reminder_date", "જે તારીખે રીમાઇન્ડર મોકલવામાં આવ્યું");
        m.put("eligibility.sub_upload_reminder", "રીમાઇન્ડરની નકલ અપલોડ કરો");
        m.put("eligibility.sub_reply_date", "જે તારીખે જવાબ મળ્યો");
        m.put("eligibility.sub_upload_reply", "જવાબની નકલ અપલોડ કરો");
        m.put("eligibility.sub_are_you_complainant", "હા તો, શું તમે પોતે ફરિયાદી છો?");
        m.put("eligibility.block_not_filed", "રિઝર્વ બેંક – સંકલિત ઓમ્બડ્સમેન યોજના, 2026 ની કલમ 10(1)(j) મુજબ, ફરિયાદ યોજના હેઠળ પ્રક્રિયા કરી શકાતી નથી.");
        m.put("eligibility.block_sub_judice", "તમારી ફરિયાદ અદાલત/ટ્રિબ્યુનલ/આર્બિટ્રેટર/સત્તાધિકારી સમક્ષ પેન્ડિંગ હોવાથી, તેને અયોગ્ય તરીકે બંધ કરવામાં આવશે.");
        m.put("eligibility.block_pending_ombudsman", "તમારી ફરિયાદ પહેલેથી એ જ વિષય પર ઓમ્બડ્સમેન સમક્ષ પેન્ડિંગ છે. ડુપ્લિકેટ ફરિયાદ દાખલ કરી શકાતી નથી.");
        m.put("eligibility.block_settled_ombudsman", "તમારી ફરિયાદ પહેલેથી ઓમ્બડ્સમેન દ્વારા નિકાલ કરવામાં આવી છે. એ જ વિષય પર નવી ફરિયાદ દાખલ કરી શકાતી નથી.");
        m.put("eligibility.block_staff_of_re", "નિયંત્રિત સંસ્થાના કર્મચારી અને નોકરીદાતા-કર્મચારી સંબંધની ફરિયાદો સંકલિત ઓમ્બડ્સમેન યોજના હેઠળ દાખલ કરી શકાતી નથી.");
        m.put("eligibility.block_indicated", "તમે");
        m.put("eligibility.block_in_response", "આ પ્રશ્નના જવાબમાં જણાવ્યા મુજબ,");
        m.put("eligibility.block_written_required", "નિયમિત સંસ્થા પાસે પ્રથમ લેખિત/ઇલેક્ટ્રોનિક ફરિયાદ દાખલ કરવી જરૂરી છે.");
        m.put("eligibility.block_regret", "તદનુસાર, અમે તમને જાણ કરીએ છીએ કે તમારી વર્તમાન ફરિયાદ");
        m.put("eligibility.block_cannot_register", "સામે યોજના હેઠળ નોંધણી કરી શકાતી નથી. જો જવાબ ભૂલથી આપવામાં આવ્યો હોય, તો તમે જવાબ બદલી શકો છો.");
        m.put("eligibility.block_regards", "સાદર, RBI CMS ટીમ.");
        m.put("eligibility.show_closure_letter", "બંધ કરવાનો પત્ર બતાવો");
        m.put("eligibility.passed_title", "પાત્રતા પાસ");
        m.put("eligibility.passed_message", "તમે RBI સંકલિત લોકપાલ યોજના, 2026 હેઠળ ફરિયાદ દાખલ કરવા માટે પાત્ર છો.");
        m.put("layout.assistance_desc", "સંપર્ક કેન્દ્ર (#14448) ઇન્ટરેક્ટિવ વોઇસ રિસ્પોન્સ સિસ્ટમ (IVRS) સાથે 24x7 ઉપલબ્ધ છે, જ્યારે સંપર્ક કેન્દ્ર કર્મચારીઓ સાથે જોડાવાની સુવિધા સોમવારથી શનિવાર (રાષ્ટ્રીય રજાઓ સિવાય) સવારે 8:00 થી રાત્રે 10:00 સુધી અંગ્રેજી, હિન્દી અને દસ પ્રાદેશિક ભાષાઓમાં ઉપલબ્ધ છે.");
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
        m.put("eligibility.q_select_re", "ریگولیٹڈ ادارے کا نام منتخب کریں");
        m.put("eligibility.q_filed_with_re", "کیا آپ نے {{reName}} میں تحریری/الیکٹرانک شکایت درج کی ہے؟");
        m.put("eligibility.q_received_reply", "کیا آپ کو ادارے سے کوئی جواب ملا ہے؟");
        m.put("eligibility.q_sent_reminder", "کیا آپ نے {{reName}} کو کوئی یاد دہانی بھیجی ہے؟");
        m.put("eligibility.q_sub_judice", "کیا یہ شکایت پہلے سے کسی عدالت، ٹریبونل، ثالث یا دیگر عدالتی فورم میں زیر سماعت ہے (فوجداری کارروائی کے سوا)؟");
        m.put("eligibility.q_through_advocate", "کیا آپ کی شکایت وکیل کے ذریعے دائر کی جا رہی ہے؟");
        m.put("eligibility.q_pending_ombudsman", "کیا یہ شکایت پہلے سے اسی معاملے پر اومبڈزمین کے سامنے زیر التوا ہے؟");
        m.put("eligibility.q_settled_ombudsman", "کیا یہ شکایت پہلے ہی اومبڈزمین نے میرٹ پر طے کر دی ہے؟");
        m.put("eligibility.q_staff_of_re", "کیا شکایت کنندہ ریگولیٹڈ ادارے کا ملازم ہے اور شکایت آجر-ملازم تعلق سے متعلق ہے؟");
        m.put("eligibility.sub_complaint_date", "جس تاریخ کو شکایت پہلی بار درج کی گئی");
        m.put("eligibility.sub_upload_complaint", "کو بھیجی گئی شکایت کی کاپی اپلوڈ کریں");
        m.put("eligibility.sub_reminder_date", "جس تاریخ کو یاد دہانی بھیجی گئی");
        m.put("eligibility.sub_upload_reminder", "یاد دہانی کی کاپی اپلوڈ کریں");
        m.put("eligibility.sub_reply_date", "جس تاریخ کو جواب موصول ہوا");
        m.put("eligibility.sub_upload_reply", "جواب کی کاپی اپلوڈ کریں");
        m.put("eligibility.sub_are_you_complainant", "اگر ہاں، تو کیا آپ خود شکایت کنندہ ہیں؟");
        m.put("eligibility.block_not_filed", "ریزرو بینک – مربوط اومبڈزمین اسکیم، 2026 کی شق 10(1)(j) کے مطابق، شکایت اسکیم کے تحت عملدرآمد نہیں ہو سکتی۔");
        m.put("eligibility.block_sub_judice", "آپ کی شکایت عدالت/ٹریبونل/ثالث/اتھارٹی کے سامنے زیر التوا ہونے کی وجہ سے، اسے ناقابل قبول کے طور پر بند کیا جائے گا۔");
        m.put("eligibility.block_pending_ombudsman", "آپ کی شکایت پہلے سے اسی معاملے پر اومبڈزمین کے سامنے زیر التوا ہے۔ ڈپلیکیٹ شکایت درج نہیں کی جا سکتی۔");
        m.put("eligibility.block_settled_ombudsman", "آپ کی شکایت پہلے ہی اومبڈزمین نے طے کر دی ہے۔ اسی معاملے پر نئی شکایت درج نہیں کی جا سکتی۔");
        m.put("eligibility.block_staff_of_re", "ریگولیٹڈ ادارے کے ملازم اور آجر-ملازم تعلق کی شکایات مربوط اومبڈزمین اسکیم کے تحت درج نہیں کی جا سکتیں۔");
        m.put("eligibility.block_indicated", "جیسا کہ آپ نے");
        m.put("eligibility.block_in_response", "اس سوال کے جواب میں بتایا ہے،");
        m.put("eligibility.block_written_required", "ریگولیٹڈ ادارے میں پہلے تحریری/الیکٹرانک شکایت درج کرانا ضروری ہے۔");
        m.put("eligibility.block_regret", "اس کے مطابق، ہم آپ کو مطلع کرتے ہیں کہ آپ کی موجودہ شکایت");
        m.put("eligibility.block_cannot_register", "کے خلاف اسکیم کے تحت رجسٹر نہیں کی جا سکتی۔ اگر جواب غلطی سے دیا گیا ہو تو آپ جواب تبدیل کر سکتے ہیں۔");
        m.put("eligibility.block_regards", "نیک خواہشات، RBI CMS ٹیم۔");
        m.put("eligibility.show_closure_letter", "بندش خط دکھائیں");
        m.put("eligibility.passed_title", "اہلیت پاس");
        m.put("eligibility.passed_message", "آپ RBI مربوط محتسب اسکیم، 2026 کے تحت شکایت درج کرانے کے اہل ہیں۔");
        m.put("layout.assistance_desc", "رابطہ مرکز (#14448) انٹرایکٹو وائس رسپانس سسٹم (IVRS) کے ساتھ 24x7 دستیاب ہے، جبکہ رابطہ مرکز کے عملے سے رابطے کی سہولت سوموار سے ہفتہ (قومی تعطیلات کے علاوہ) صبح 8:00 سے رات 10:00 تک انگریزی، ہندی اور دس علاقائی زبانوں میں دستیاب ہے۔");
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
        m.put("eligibility.q_select_re", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಹೆಸರು ಆಯ್ಕೆಮಾಡಿ");
        m.put("eligibility.q_filed_with_re", "ನೀವು {{reName}} ಬಳಿ ಲಿಖಿತ/ಎಲೆಕ್ಟ್ರಾನಿಕ್ ದೂರು ಸಲ್ಲಿಸಿದ್ದೀರಾ?");
        m.put("eligibility.q_received_reply", "ನಿಮಗೆ ಸಂಸ್ಥೆಯಿಂದ ಯಾವುದೇ ಉತ್ತರ ಸಿಕ್ಕಿದೆಯೇ?");
        m.put("eligibility.q_sent_reminder", "ನೀವು {{reName}}ಗೆ ಯಾವುದೇ ಜ್ಞಾಪನೆ ಕಳುಹಿಸಿದ್ದೀರಾ?");
        m.put("eligibility.q_sub_judice", "ಈ ದೂರು ಈಗಾಗಲೇ ಯಾವುದೇ ನ್ಯಾಯಾಲಯ, ನ್ಯಾಯಾಧಿಕರಣ, ಮಧ್ಯಸ್ಥಿಕೆ ಅಥವಾ ಇತರ ನ್ಯಾಯಿಕ ವೇದಿಕೆಯ ಮುಂದೆ ಬಾಕಿ ಇದೆಯೇ (ಕ್ರಿಮಿನಲ್ ಪ್ರಕರಣಗಳನ್ನು ಹೊರತುಪಡಿಸಿ)?");
        m.put("eligibility.q_through_advocate", "ನಿಮ್ಮ ದೂರು ವಕೀಲರ ಮೂಲಕ ಸಲ್ಲಿಸಲಾಗುತ್ತಿದೆಯೇ?");
        m.put("eligibility.q_pending_ombudsman", "ಈ ದೂರು ಈಗಾಗಲೇ ಅದೇ ವಿಷಯದ ಮೇಲೆ ಲೋಕಪಾಲರ ಮುಂದೆ ಬಾಕಿ ಇದೆಯೇ?");
        m.put("eligibility.q_settled_ombudsman", "ಈ ದೂರು ಈಗಾಗಲೇ ಲೋಕಪಾಲರಿಂದ ಗುಣಾವಗುಣಗಳ ಆಧಾರದ ಮೇಲೆ ಇತ್ಯರ್ಥಗೊಂಡಿದೆಯೇ?");
        m.put("eligibility.q_staff_of_re", "ದೂರುದಾರರು ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಸಿಬ್ಬಂದಿಯೇ ಮತ್ತು ದೂರು ನಿಯೋಜಕ-ಉದ್ಯೋಗಿ ಸಂಬಂಧಕ್ಕೆ ಸಂಬಂಧಿಸಿದೆಯೇ?");
        m.put("eligibility.sub_complaint_date", "ದೂರು ಮೊದಲ ಬಾರಿಗೆ ಸಲ್ಲಿಸಿದ ದಿನಾಂಕ");
        m.put("eligibility.sub_upload_complaint", "ಗೆ ಕಳುಹಿಸಿದ ದೂರಿನ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("eligibility.sub_reminder_date", "ಜ್ಞಾಪನೆ ಕಳುಹಿಸಿದ ದಿನಾಂಕ");
        m.put("eligibility.sub_upload_reminder", "ಜ್ಞಾಪನೆಯ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("eligibility.sub_reply_date", "ಉತ್ತರ ಸಿಕ್ಕ ದಿನಾಂಕ");
        m.put("eligibility.sub_upload_reply", "ಉತ್ತರದ ಪ್ರತಿಯನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("eligibility.sub_are_you_complainant", "ಹೌದು ಎಂದಾದರೆ, ನೀವೇ ದೂರುದಾರರೇ?");
        m.put("eligibility.block_not_filed", "ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್ – ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆ, 2026 ಕಲಂ 10(1)(j) ಪ್ರಕಾರ, ದೂರನ್ನು ಯೋಜನೆಯಡಿ ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ.");
        m.put("eligibility.block_sub_judice", "ನಿಮ್ಮ ದೂರು ನ್ಯಾಯಾಲಯ/ನ್ಯಾಯಾಧಿಕರಣ/ಮಧ್ಯಸ್ಥಿಕೆ/ಅಧಿಕಾರದ ಮುಂದೆ ಬಾಕಿ ಇರುವುದರಿಂದ, ಇದನ್ನು ಅನರ್ಹವೆಂದು ಮುಚ್ಚಲಾಗುವುದು.");
        m.put("eligibility.block_pending_ombudsman", "ನಿಮ್ಮ ದೂರು ಈಗಾಗಲೇ ಅದೇ ವಿಷಯದ ಮೇಲೆ ಲೋಕಪಾಲರ ಮುಂದೆ ಬಾಕಿ ಇದೆ. ನಕಲಿ ದೂರು ಸಲ್ಲಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("eligibility.block_settled_ombudsman", "ನಿಮ್ಮ ದೂರು ಈಗಾಗಲೇ ಲೋಕಪಾಲರಿಂದ ಇತ್ಯರ್ಥಗೊಂಡಿದೆ. ಅದೇ ವಿಷಯದ ಮೇಲೆ ಹೊಸ ದೂರು ಸಲ್ಲಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("eligibility.block_staff_of_re", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯ ಸಿಬ್ಬಂದಿ ಮತ್ತು ನಿಯೋಜಕ-ಉದ್ಯೋಗಿ ಸಂಬಂಧದ ದೂರುಗಳನ್ನು ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆಯಡಿ ಸಲ್ಲಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("eligibility.block_indicated", "ನೀವು");
        m.put("eligibility.block_in_response", "ಈ ಪ್ರಶ್ನೆಗೆ ಉತ್ತರವಾಗಿ ತಿಳಿಸಿದಂತೆ,");
        m.put("eligibility.block_written_required", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಲ್ಲಿ ಮೊದಲು ಲಿಖಿತ/ಎಲೆಕ್ಟ್ರಾನಿಕ್ ದೂರು ದಾಖಲಿಸುವುದು ಅಗತ್ಯ.");
        m.put("eligibility.block_regret", "ಅದರಂತೆ, ನಿಮ್ಮ ಪ್ರಸ್ತುತ ದೂರು");
        m.put("eligibility.block_cannot_register", "ವಿರುದ್ಧ ಯೋಜನೆಯಡಿ ನೋಂದಾಯಿಸಲಾಗುವುದಿಲ್ಲ ಎಂದು ನಾವು ತಿಳಿಸುತ್ತೇವೆ. ಉತ್ತರವನ್ನು ತಪ್ಪಾಗಿ ನೀಡಿದ್ದರೆ, ನೀವು ಉತ್ತರವನ್ನು ಬದಲಾಯಿಸಬಹುದು.");
        m.put("eligibility.block_regards", "ಶುಭಾಶಯಗಳೊಂದಿಗೆ, RBI CMS ತಂಡ.");
        m.put("eligibility.show_closure_letter", "ಮುಕ್ತಾಯ ಪತ್ರ ತೋರಿಸಿ");
        m.put("eligibility.passed_title", "ಅರ್ಹತೆ ಉತ್ತೀರ್ಣ");
        m.put("eligibility.passed_message", "ನೀವು RBI ಸಮಗ್ರ ಓಂಬುಡ್ಸ್‌ಮನ್ ಯೋಜನೆ, 2026 ಅಡಿಯಲ್ಲಿ ದೂರು ದಾಖಲಿಸಲು ಅರ್ಹರು.");
        m.put("layout.assistance_desc", "ಸಂಪರ್ಕ ಕೇಂದ್ರ (#14448) ಇಂಟರಾಕ್ಟಿವ್ ವಾಯ್ಸ್ ರೆಸ್ಪಾನ್ಸ್ ಸಿಸ್ಟಮ್ (IVRS) ಜೊತೆ 24x7 ಲಭ್ಯವಿದೆ, ಆದರೆ ಸಂಪರ್ಕ ಕೇಂದ್ರ ಸಿಬ್ಬಂದಿಯೊಂದಿಗೆ ಸಂಪರ್ಕಿಸುವ ಸೌಲಭ್ಯ ಸೋಮವಾರದಿಂದ ಶನಿವಾರ (ರಾಷ್ಟ್ರೀಯ ರಜಾದಿನಗಳನ್ನು ಹೊರತುಪಡಿಸಿ) ಬೆಳಿಗ್ಗೆ 8:00 ರಿಂದ ರಾತ್ರಿ 10:00 ರವರೆಗೆ ಆಂಗ್ಲ, ಹಿಂದಿ ಮತ್ತು ಹತ್ತು ಪ್ರಾದೇಶಿಕ ಭಾಷೆಗಳಲ್ಲಿ ಲಭ್ಯವಿದೆ.");
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
        m.put("eligibility.q_select_re", "നിയന്ത്രിത സ്ഥാപനത്തിന്റെ പേര് തിരഞ്ഞെടുക്കുക");
        m.put("eligibility.q_filed_with_re", "നിങ്ങൾ {{reName}}-ൽ രേഖാമൂലം/ഇലക്ട്രോണിക് പരാതി സമർപ്പിച്ചിട്ടുണ്ടോ?");
        m.put("eligibility.q_received_reply", "സ്ഥാപനത്തിൽ നിന്ന് എന്തെങ്കിലും മറുപടി ലഭിച്ചോ?");
        m.put("eligibility.q_sent_reminder", "നിങ്ങൾ {{reName}}-ന് എന്തെങ്കിലും ഓർമ്മപ്പെടുത്തൽ അയച്ചോ?");
        m.put("eligibility.q_sub_judice", "ഈ പരാതി ഇതിനകം ഏതെങ്കിലും കോടതി, ട്രൈബ്യൂണൽ, ആർബിട്രേറ്റർ അല്ലെങ്കിൽ മറ്റ് ജുഡീഷ്യൽ ഫോറത്തിന് മുമ്പാകെ തീർപ്പുകൽപ്പിക്കാതെ നിലനിൽക്കുന്നുണ്ടോ (ക്രിമിനൽ നടപടികൾ ഒഴികെ)?");
        m.put("eligibility.q_through_advocate", "നിങ്ങളുടെ പരാതി അഭിഭാഷകൻ മുഖേന സമർപ്പിക്കുന്നതാണോ?");
        m.put("eligibility.q_pending_ombudsman", "ഈ പരാതി ഇതിനകം അതേ വിഷയത്തിൽ ഓംബുഡ്സ്മാന് മുമ്പാകെ തീർപ്പുകൽപ്പിക്കാതെ നിലനിൽക്കുന്നുണ്ടോ?");
        m.put("eligibility.q_settled_ombudsman", "ഈ പരാതി ഇതിനകം ഓംബുഡ്സ്മാൻ ഗുണദോഷവിചാരത്തിൽ പരിഹരിച്ചിട്ടുണ്ടോ?");
        m.put("eligibility.q_staff_of_re", "പരാതിക്കാരൻ നിയന്ത്രിത സ്ഥാപനത്തിന്റെ ജീവനക്കാരനാണോ, പരാതി തൊഴിലുടമ-ജീവനക്കാരൻ ബന്ധവുമായി ബന്ധപ്പെട്ടതാണോ?");
        m.put("eligibility.sub_complaint_date", "പരാതി ആദ്യം സമർപ്പിച്ച തീയതി");
        m.put("eligibility.sub_upload_complaint", "ന് അയച്ച പരാതിയുടെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("eligibility.sub_reminder_date", "ഓർമ്മപ്പെടുത്തൽ അയച്ച തീയതി");
        m.put("eligibility.sub_upload_reminder", "ഓർമ്മപ്പെടുത്തലിന്റെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("eligibility.sub_reply_date", "മറുപടി ലഭിച്ച തീയതി");
        m.put("eligibility.sub_upload_reply", "മറുപടിയുടെ പകർപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("eligibility.sub_are_you_complainant", "അതെ എങ്കിൽ, നിങ്ങൾ തന്നെ പരാതിക്കാരനാണോ?");
        m.put("eligibility.block_not_filed", "റിസർവ് ബാങ്ക് – സംയോജിത ഓംബുഡ്സ്മാൻ സ്കീം, 2026 ക്ലോസ് 10(1)(j) പ്രകാരം, പരാതി സ്കീമിന് കീഴിൽ പ്രോസസ്സ് ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_sub_judice", "നിങ്ങളുടെ പരാതി കോടതി/ട്രൈബ്യൂണൽ/ആർബിട്രേറ്റർ/അതോറിറ്റിക്ക് മുമ്പാകെ നിലനിൽക്കുന്നതിനാൽ, ഇത് അയോഗ്യമെന്ന് അടച്ചുപൂട്ടും.");
        m.put("eligibility.block_pending_ombudsman", "നിങ്ങളുടെ പരാതി ഇതിനകം അതേ വിഷയത്തിൽ ഓംബുഡ്സ്മാന് മുമ്പാകെ നിലനിൽക്കുന്നു. ഡ്യൂപ്ലിക്കേറ്റ് പരാതി ഫയൽ ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_settled_ombudsman", "നിങ്ങളുടെ പരാതി ഇതിനകം ഓംബുഡ്സ്മാൻ പരിഹരിച്ചിട്ടുണ്ട്. അതേ വിഷയത്തിൽ പുതിയ പരാതി ഫയൽ ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_staff_of_re", "നിയന്ത്രിത സ്ഥാപന ജീവനക്കാരനും തൊഴിലുടമ-ജീവനക്കാരൻ ബന്ധ പരാതികളും സംയോജിത ഓംബുഡ്സ്മാൻ സ്കീമിന് കീഴിൽ ഫയൽ ചെയ്യാൻ കഴിയില്ല.");
        m.put("eligibility.block_indicated", "നിങ്ങൾ");
        m.put("eligibility.block_in_response", "ഈ ചോദ്യത്തിന് ഉത്തരമായി സൂചിപ്പിച്ചതുപോലെ,");
        m.put("eligibility.block_written_required", "നിയന്ത്രിത സ്ഥാപനത്തിൽ ആദ്യം രേഖാമൂലം/ഇലക്ട്രോണിക് പരാതി നൽകേണ്ടതുണ്ട്.");
        m.put("eligibility.block_regret", "അതനുസരിച്ച്, നിങ്ങളുടെ നിലവിലെ പരാതി");
        m.put("eligibility.block_cannot_register", "ക്കെതിരെ പദ്ധതിയുടെ കീഴിൽ രജിസ്റ്റർ ചെയ്യാൻ കഴിയില്ലെന്ന് ഞങ്ങൾ അറിയിക്കുന്നു. ഉത്തരം തെറ്റായി നൽകിയതാണെങ്കിൽ, നിങ്ങൾക്ക് ഉത്തരം മാറ്റാവുന്നതാണ്.");
        m.put("eligibility.block_regards", "ആശംസകൾ, RBI CMS ടീം.");
        m.put("eligibility.show_closure_letter", "ക്ലോഷർ കത്ത് കാണിക്കുക");
        m.put("eligibility.passed_title", "യോഗ്യത വിജയം");
        m.put("eligibility.passed_message", "RBI സമഗ്ര ഓംബുഡ്സ്മാൻ പദ്ധതി, 2026 പ്രകാരം പരാതി നൽകാൻ നിങ്ങൾ യോഗ്യനാണ്.");
        m.put("layout.assistance_desc", "കോൺടാക്ട് സെന്റർ (#14448) ഇന്ററാക്ടീവ് വോയ്‌സ് റെസ്‌പോൺസ് സിസ്റ്റം (IVRS) ഉപയോഗിച്ച് 24x7 ലഭ്യമാണ്, അതേസമയം കോൺടാക്ട് സെന്റർ ജീവനക്കാരുമായി ബന്ധപ്പെടാനുള്ള സൗകര്യം ദേശീയ അവധി ദിവസങ്ങൾ ഒഴികെ തിങ്കൾ മുതൽ ശനി വരെ രാവിലെ 8:00 മുതൽ രാത്രി 10:00 വരെ ഇംഗ്ലീഷ്, ഹിന്ദി, പത്ത് പ്രാദേശിക ഭാഷകളിൽ ലഭ്യമാണ്.");
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
