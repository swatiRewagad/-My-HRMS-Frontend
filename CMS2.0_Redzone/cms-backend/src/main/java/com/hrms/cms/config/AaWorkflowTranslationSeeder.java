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
 * Appellate Authority appeal-workflow state machine vocabulary: transition guard failures, the SLA
 * band shown against an appeal, and the lifecycle events recorded on (and notified from) the appeal
 * timeline.
 *
 * <p>Three groups of keys:
 *
 * <ul>
 *   <li>{@code aa.workflow.error_*} — guard rejections raised when a requested action is not legal
 *       for the appeal's current state, or when the actor's reviewer tier / target role / remand
 *       parent cannot be resolved.
 *   <li>{@code aa.sla.*} — the four SLA bands rendered against an appeal in queues and detail views,
 *       including the "no deadline applies at this stage" band.
 *   <li>{@code aa.event.*} — the appeal lifecycle events. Several of these are surfaced verbatim to
 *       the CITIZEN who filed the appeal, so the register is deliberately formal.
 * </ul>
 *
 * <p>Seeded in all ten supported locales, following {@link AaAssignmentTranslationSeeder}: English
 * lives in {@code TranslationKey.defaultValue}, the other nine become {@code Translation} rows.
 * Insert-if-absent, so re-running is a no-op — but note the corollary: correcting a string here does
 * NOT rewrite a row already committed to a database. A text correction needs a code-scoped UPDATE in
 * both migration directories.
 *
 * <p>Note on "Tier 2": the English strings carry the numeral, but every vernacular rendering spells
 * the ordinal out ("द्वितीय स्तर", "இரண்டாம் நிலை", …) so that no locale has to choose between
 * Latin and native digits.
 */
@Component
@Order(16)
public class AaWorkflowTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "aa-workflow";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public AaWorkflowTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // ═══ Transition guard failures ═══
        m.put("aa.workflow.error_illegal_transition",
              "This action is not allowed at the appeal's current stage. Refresh to see the available actions.");
        m.put("aa.workflow.error_appeal_not_found", "Appeal not found");
        m.put("aa.workflow.error_invalid_target_role", "Select a valid Appellate Authority role");
        m.put("aa.workflow.error_reopen_reason_required",
              "A reason is required to reopen a disposed appeal");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "Your reviewer tier could not be determined. Contact the administrator.");
        m.put("aa.workflow.error_already_tier2", "A Tier 2 reviewer cannot escalate further");
        m.put("aa.workflow.error_no_tier2_reviewer", "No Tier 2 reviewer is currently available");
        m.put("aa.workflow.error_remand_parent_missing",
              "The original complaint could not be found, so this appeal cannot be remanded");

        // ═══ SLA bands ═══
        m.put("aa.sla.on_track", "On track");
        m.put("aa.sla.at_risk", "Due soon");
        m.put("aa.sla.breached", "Overdue");
        m.put("aa.sla.not_tracked", "No deadline at this stage");

        // ═══ Appeal lifecycle events ═══
        m.put("aa.event.filed", "Appeal filed");
        m.put("aa.event.accepted", "Appeal accepted for review");
        m.put("aa.event.rejected", "Appeal rejected");
        m.put("aa.event.assigned_to_reviewer", "Appeal assigned to a reviewer");
        m.put("aa.event.hearing_scheduled", "Hearing scheduled");
        m.put("aa.event.escalated_to_tier2", "Appeal escalated to a Tier 2 reviewer");
        m.put("aa.event.order_passed", "Order issued on your appeal");
        m.put("aa.event.remanded", "Appeal remanded to the Ombudsman");
        m.put("aa.event.dismissed", "Appeal dismissed");
        m.put("aa.event.reopened", "Appeal reopened");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "यह कार्रवाई अपील की वर्तमान अवस्था में अनुमत नहीं है। उपलब्ध कार्रवाइयाँ देखने के लिए पृष्ठ ताज़ा करें।");
        m.put("aa.workflow.error_appeal_not_found", "अपील नहीं मिली");
        m.put("aa.workflow.error_invalid_target_role", "कोई वैध अपीलीय प्राधिकारी भूमिका चुनें");
        m.put("aa.workflow.error_reopen_reason_required",
              "निपटाई गई अपील को पुनः खोलने के लिए कारण देना अनिवार्य है");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "आपका समीक्षक स्तर निर्धारित नहीं हो सका। प्रशासक से संपर्क करें।");
        m.put("aa.workflow.error_already_tier2", "द्वितीय स्तर का समीक्षक इससे आगे उत्प्रेषण नहीं कर सकता");
        m.put("aa.workflow.error_no_tier2_reviewer", "इस समय द्वितीय स्तर का कोई समीक्षक उपलब्ध नहीं है");
        m.put("aa.workflow.error_remand_parent_missing",
              "मूल शिकायत नहीं मिल सकी, अतः यह अपील प्रतिप्रेषित नहीं की जा सकती");
        m.put("aa.sla.on_track", "समय पर");
        m.put("aa.sla.at_risk", "शीघ्र देय");
        m.put("aa.sla.breached", "अतिदेय");
        m.put("aa.sla.not_tracked", "इस चरण पर कोई समय-सीमा नहीं");
        m.put("aa.event.filed", "अपील दाखिल की गई");
        m.put("aa.event.accepted", "अपील समीक्षा हेतु स्वीकार की गई");
        m.put("aa.event.rejected", "अपील अस्वीकृत की गई");
        m.put("aa.event.assigned_to_reviewer", "अपील समीक्षक को सौंपी गई");
        m.put("aa.event.hearing_scheduled", "सुनवाई निर्धारित की गई");
        m.put("aa.event.escalated_to_tier2", "अपील द्वितीय स्तर के समीक्षक को उत्प्रेषित की गई");
        m.put("aa.event.order_passed", "आपकी अपील पर आदेश जारी किया गया");
        m.put("aa.event.remanded", "अपील लोकपाल को प्रतिप्रेषित की गई");
        m.put("aa.event.dismissed", "अपील खारिज की गई");
        m.put("aa.event.reopened", "अपील पुनः खोली गई");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "अपिलाच्या सध्याच्या टप्प्यावर ही कार्यवाही अनुमत नाही. उपलब्ध कार्यवाही पाहण्यासाठी पृष्ठ ताजे करा.");
        m.put("aa.workflow.error_appeal_not_found", "अपील आढळली नाही");
        m.put("aa.workflow.error_invalid_target_role", "वैध अपिलीय प्राधिकरण भूमिका निवडा");
        m.put("aa.workflow.error_reopen_reason_required",
              "निकाली काढलेली अपील पुन्हा उघडण्यासाठी कारण देणे आवश्यक आहे");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "आपला समीक्षक स्तर निश्चित करता आला नाही. प्रशासकाशी संपर्क साधा.");
        m.put("aa.workflow.error_already_tier2",
              "द्वितीय स्तरावरील समीक्षक यापुढे वरिष्ठ स्तरावर पाठवू शकत नाही");
        m.put("aa.workflow.error_no_tier2_reviewer", "सध्या द्वितीय स्तरावरील कोणताही समीक्षक उपलब्ध नाही");
        m.put("aa.workflow.error_remand_parent_missing",
              "मूळ तक्रार सापडली नाही, त्यामुळे ही अपील पुनर्विचारार्थ परत पाठवता येत नाही");
        m.put("aa.sla.on_track", "वेळेत");
        m.put("aa.sla.at_risk", "लवकरच देय");
        m.put("aa.sla.breached", "मुदत उलटली");
        m.put("aa.sla.not_tracked", "या टप्प्यावर कोणतीही मुदत नाही");
        m.put("aa.event.filed", "अपील दाखल केली");
        m.put("aa.event.accepted", "अपील समीक्षेसाठी स्वीकारली");
        m.put("aa.event.rejected", "अपील नाकारली");
        m.put("aa.event.assigned_to_reviewer", "अपील समीक्षकाकडे सोपविली");
        m.put("aa.event.hearing_scheduled", "सुनावणी निश्चित केली");
        m.put("aa.event.escalated_to_tier2", "अपील द्वितीय स्तरावरील समीक्षकाकडे पाठविली");
        m.put("aa.event.order_passed", "आपल्या अपिलावर आदेश जारी करण्यात आला");
        m.put("aa.event.remanded", "अपील लोकपालांकडे पुनर्विचारार्थ परत पाठविली");
        m.put("aa.event.dismissed", "अपील फेटाळली");
        m.put("aa.event.reopened", "अपील पुन्हा उघडली");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "আপিলের বর্তমান পর্যায়ে এই কাজটি অনুমোদিত নয়। উপলব্ধ কাজগুলি দেখতে পৃষ্ঠাটি নতুন করে নিন।");
        m.put("aa.workflow.error_appeal_not_found", "আপিল পাওয়া যায়নি");
        m.put("aa.workflow.error_invalid_target_role", "একটি বৈধ আপিল কর্তৃপক্ষের ভূমিকা নির্বাচন করুন");
        m.put("aa.workflow.error_reopen_reason_required",
              "নিষ্পত্তি হওয়া আপিল পুনরায় খুলতে কারণ উল্লেখ করা আবশ্যক");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "আপনার পর্যালোচক স্তর নির্ণয় করা যায়নি। প্রশাসকের সঙ্গে যোগাযোগ করুন।");
        m.put("aa.workflow.error_already_tier2",
              "দ্বিতীয় স্তরের পর্যালোচক আর ঊর্ধ্বতন স্তরে পাঠাতে পারেন না");
        m.put("aa.workflow.error_no_tier2_reviewer", "বর্তমানে দ্বিতীয় স্তরের কোনো পর্যালোচক উপলব্ধ নেই");
        m.put("aa.workflow.error_remand_parent_missing",
              "মূল অভিযোগটি পাওয়া যায়নি, তাই এই আপিল পুনর্বিবেচনার জন্য ফেরত পাঠানো যাবে না");
        m.put("aa.sla.on_track", "সময়ের মধ্যে");
        m.put("aa.sla.at_risk", "শীঘ্রই সময়সীমা");
        m.put("aa.sla.breached", "সময়সীমা উত্তীর্ণ");
        m.put("aa.sla.not_tracked", "এই পর্যায়ে কোনো সময়সীমা নেই");
        m.put("aa.event.filed", "আপিল দাখিল করা হয়েছে");
        m.put("aa.event.accepted", "আপিল পর্যালোচনার জন্য গৃহীত হয়েছে");
        m.put("aa.event.rejected", "আপিল প্রত্যাখ্যাত হয়েছে");
        m.put("aa.event.assigned_to_reviewer", "আপিল একজন পর্যালোচকের কাছে বরাদ্দ করা হয়েছে");
        m.put("aa.event.hearing_scheduled", "শুনানি নির্ধারিত হয়েছে");
        m.put("aa.event.escalated_to_tier2", "আপিল দ্বিতীয় স্তরের পর্যালোচকের কাছে পাঠানো হয়েছে");
        m.put("aa.event.order_passed", "আপনার আপিলের উপর আদেশ জারি করা হয়েছে");
        m.put("aa.event.remanded", "আপিল পুনর্বিবেচনার জন্য লোকপালের কাছে ফেরত পাঠানো হয়েছে");
        m.put("aa.event.dismissed", "আপিল খারিজ করা হয়েছে");
        m.put("aa.event.reopened", "আপিল পুনরায় খোলা হয়েছে");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "అప్పీలు ప్రస్తుత దశలో ఈ చర్యకు అనుమతి లేదు. అందుబాటులో ఉన్న చర్యలను చూడటానికి పేజీని రిఫ్రెష్ చేయండి.");
        m.put("aa.workflow.error_appeal_not_found", "అప్పీలు కనుగొనబడలేదు");
        m.put("aa.workflow.error_invalid_target_role", "చెల్లుబాటు అయ్యే అప్పీలు అధికార పాత్రను ఎంచుకోండి");
        m.put("aa.workflow.error_reopen_reason_required",
              "పరిష్కరించబడిన అప్పీలును తిరిగి తెరవడానికి కారణం తెలపడం తప్పనిసరి");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "మీ సమీక్షకుని స్థాయిని నిర్ణయించడం సాధ్యపడలేదు. నిర్వాహకుని సంప్రదించండి.");
        m.put("aa.workflow.error_already_tier2",
              "రెండవ స్థాయి సమీక్షకుడు దీనికి మించి ఉన్నత స్థాయికి పంపలేరు");
        m.put("aa.workflow.error_no_tier2_reviewer", "ప్రస్తుతం రెండవ స్థాయి సమీక్షకుడు ఎవరూ అందుబాటులో లేరు");
        m.put("aa.workflow.error_remand_parent_missing",
              "అసలు ఫిర్యాదు కనుగొనబడలేదు, కాబట్టి ఈ అప్పీలును పునఃపరిశీలనకు వెనక్కి పంపడం సాధ్యం కాదు");
        m.put("aa.sla.on_track", "సమయానుసారం");
        m.put("aa.sla.at_risk", "త్వరలో గడువు");
        m.put("aa.sla.breached", "గడువు దాటింది");
        m.put("aa.sla.not_tracked", "ఈ దశలో ఎటువంటి గడువు లేదు");
        m.put("aa.event.filed", "అప్పీలు దాఖలు చేయబడింది");
        m.put("aa.event.accepted", "అప్పీలు సమీక్ష కోసం స్వీకరించబడింది");
        m.put("aa.event.rejected", "అప్పీలు తిరస్కరించబడింది");
        m.put("aa.event.assigned_to_reviewer", "అప్పీలు ఒక సమీక్షకునికి కేటాయించబడింది");
        m.put("aa.event.hearing_scheduled", "విచారణ నిర్ణయించబడింది");
        m.put("aa.event.escalated_to_tier2", "అప్పీలు రెండవ స్థాయి సమీక్షకునికి పంపబడింది");
        m.put("aa.event.order_passed", "మీ అప్పీలుపై ఉత్తర్వు జారీ చేయబడింది");
        m.put("aa.event.remanded", "అప్పీలు పునఃపరిశీలన కోసం అంబుడ్స్‌మన్‌కు తిరిగి పంపబడింది");
        m.put("aa.event.dismissed", "అప్పీలు కొట్టివేయబడింది");
        m.put("aa.event.reopened", "అప్పీలు తిరిగి తెరవబడింది");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "மேல்முறையீட்டின் தற்போதைய நிலையில் இந்த நடவடிக்கை அனுமதிக்கப்படவில்லை. கிடைக்கும் நடவடிக்கைகளைக் காண பக்கத்தைப் புதுப்பியுங்கள்.");
        m.put("aa.workflow.error_appeal_not_found", "மேல்முறையீடு காணப்படவில்லை");
        m.put("aa.workflow.error_invalid_target_role",
              "செல்லுபடியாகும் மேல்முறையீட்டு ஆணைய பணிப் பங்கைத் தேர்ந்தெடுக்கவும்");
        m.put("aa.workflow.error_reopen_reason_required",
              "தீர்வு பெற்ற மேல்முறையீட்டை மீண்டும் திறக்க காரணம் அளிப்பது கட்டாயம்");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "உங்கள் பரிசீலனை நிலையை உறுதிப்படுத்த முடியவில்லை. நிர்வாகியைத் தொடர்பு கொள்ளுங்கள்.");
        m.put("aa.workflow.error_already_tier2",
              "இரண்டாம் நிலைப் பரிசீலனை அதிகாரி இதற்கு மேல் மேல்நிலைக்கு அனுப்ப முடியாது");
        m.put("aa.workflow.error_no_tier2_reviewer",
              "தற்போது இரண்டாம் நிலைப் பரிசீலனை அதிகாரி எவரும் கிடைக்கவில்லை");
        m.put("aa.workflow.error_remand_parent_missing",
              "மூல முறையீடு காணப்படவில்லை, எனவே இந்த மேல்முறையீட்டை மறுபரிசீலனைக்குத் திரும்ப அனுப்ப முடியாது");
        m.put("aa.sla.on_track", "காலக்கெடுவுக்குள்");
        m.put("aa.sla.at_risk", "விரைவில் காலக்கெடு");
        m.put("aa.sla.breached", "காலக்கெடு கடந்தது");
        m.put("aa.sla.not_tracked", "இந்த நிலையில் காலக்கெடு இல்லை");
        m.put("aa.event.filed", "மேல்முறையீடு தாக்கல் செய்யப்பட்டது");
        m.put("aa.event.accepted", "மேல்முறையீடு பரிசீலனைக்கு ஏற்கப்பட்டது");
        m.put("aa.event.rejected", "மேல்முறையீடு நிராகரிக்கப்பட்டது");
        m.put("aa.event.assigned_to_reviewer",
              "மேல்முறையீடு ஒரு பரிசீலனை அதிகாரிக்கு ஒப்படைக்கப்பட்டது");
        m.put("aa.event.hearing_scheduled", "விசாரணை நாள் நிர்ணயிக்கப்பட்டது");
        m.put("aa.event.escalated_to_tier2",
              "மேல்முறையீடு இரண்டாம் நிலைப் பரிசீலனை அதிகாரிக்கு அனுப்பப்பட்டது");
        m.put("aa.event.order_passed", "உங்கள் மேல்முறையீட்டின் மீது ஆணை பிறப்பிக்கப்பட்டது");
        m.put("aa.event.remanded",
              "மேல்முறையீடு மறுபரிசீலனைக்காக குறைதீர்ப்பாளருக்குத் திரும்ப அனுப்பப்பட்டது");
        m.put("aa.event.dismissed", "மேல்முறையீடு தள்ளுபடி செய்யப்பட்டது");
        m.put("aa.event.reopened", "மேல்முறையீடு மீண்டும் திறக்கப்பட்டது");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "અપીલના વર્તમાન તબક્કે આ કાર્યવાહી અનુમત નથી. ઉપલબ્ધ કાર્યવાહી જોવા માટે પૃષ્ઠ તાજું કરો.");
        m.put("aa.workflow.error_appeal_not_found", "અપીલ મળી નથી");
        m.put("aa.workflow.error_invalid_target_role", "માન્ય અપીલ સત્તાધિકારી ભૂમિકા પસંદ કરો");
        m.put("aa.workflow.error_reopen_reason_required",
              "નિકાલ થયેલી અપીલ ફરી ખોલવા માટે કારણ આપવું આવશ્યક છે");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "તમારો સમીક્ષક સ્તર નિર્ધારિત કરી શકાયો નથી. પ્રશાસકનો સંપર્ક કરો.");
        m.put("aa.workflow.error_already_tier2",
              "દ્વિતીય સ્તરના સમીક્ષક આથી આગળ ઉચ્ચ સ્તરે મોકલી શકતા નથી");
        m.put("aa.workflow.error_no_tier2_reviewer", "હાલમાં દ્વિતીય સ્તરના કોઈ સમીક્ષક ઉપલબ્ધ નથી");
        m.put("aa.workflow.error_remand_parent_missing",
              "મૂળ ફરિયાદ મળી શકી નથી, તેથી આ અપીલ પુનર્વિચાર માટે પરત મોકલી શકાતી નથી");
        m.put("aa.sla.on_track", "સમયસર");
        m.put("aa.sla.at_risk", "ટૂંક સમયમાં નિયત");
        m.put("aa.sla.breached", "સમયમર્યાદા વીતી ગઈ");
        m.put("aa.sla.not_tracked", "આ તબક્કે કોઈ સમયમર્યાદા નથી");
        m.put("aa.event.filed", "અપીલ દાખલ કરવામાં આવી");
        m.put("aa.event.accepted", "અપીલ સમીક્ષા માટે સ્વીકારવામાં આવી");
        m.put("aa.event.rejected", "અપીલ નામંજૂર કરવામાં આવી");
        m.put("aa.event.assigned_to_reviewer", "અપીલ સમીક્ષકને સોંપવામાં આવી");
        m.put("aa.event.hearing_scheduled", "સુનાવણી નિર્ધારિત કરવામાં આવી");
        m.put("aa.event.escalated_to_tier2", "અપીલ દ્વિતીય સ્તરના સમીક્ષકને મોકલવામાં આવી");
        m.put("aa.event.order_passed", "તમારી અપીલ પર આદેશ જારી કરવામાં આવ્યો");
        m.put("aa.event.remanded", "અપીલ પુનર્વિચાર માટે લોકપાલને પરત મોકલવામાં આવી");
        m.put("aa.event.dismissed", "અપીલ ફગાવી દેવામાં આવી");
        m.put("aa.event.reopened", "અપીલ ફરી ખોલવામાં આવી");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "اپیل کے موجودہ مرحلے پر یہ کارروائی جائز نہیں ہے۔ دستیاب کارروائیاں دیکھنے کے لیے صفحہ تازہ کریں۔");
        m.put("aa.workflow.error_appeal_not_found", "اپیل نہیں ملی");
        m.put("aa.workflow.error_invalid_target_role", "ایک درست اپیلٹ اتھارٹی کردار منتخب کریں");
        m.put("aa.workflow.error_reopen_reason_required",
              "نمٹائی گئی اپیل دوبارہ کھولنے کے لیے وجہ بتانا لازمی ہے");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "آپ کے جائزہ کار درجے کا تعین نہیں کیا جا سکا۔ منتظم سے رابطہ کریں۔");
        m.put("aa.workflow.error_already_tier2",
              "دوسرے درجے کا جائزہ کار اس سے آگے بالا سطح پر نہیں بھیج سکتا");
        m.put("aa.workflow.error_no_tier2_reviewer", "اس وقت دوسرے درجے کا کوئی جائزہ کار دستیاب نہیں ہے");
        m.put("aa.workflow.error_remand_parent_missing",
              "اصل شکایت نہیں مل سکی، چنانچہ یہ اپیل دوبارہ غور کے لیے واپس نہیں بھیجی جا سکتی");
        m.put("aa.sla.on_track", "وقت پر");
        m.put("aa.sla.at_risk", "جلد واجب");
        m.put("aa.sla.breached", "میعاد گزر چکی");
        m.put("aa.sla.not_tracked", "اس مرحلے پر کوئی میعاد مقرر نہیں");
        m.put("aa.event.filed", "اپیل دائر کی گئی");
        m.put("aa.event.accepted", "اپیل جائزے کے لیے منظور کی گئی");
        m.put("aa.event.rejected", "اپیل مسترد کر دی گئی");
        m.put("aa.event.assigned_to_reviewer", "اپیل ایک جائزہ کار کے سپرد کی گئی");
        m.put("aa.event.hearing_scheduled", "سماعت مقرر کی گئی");
        m.put("aa.event.escalated_to_tier2", "اپیل دوسرے درجے کے جائزہ کار کو بھیجی گئی");
        m.put("aa.event.order_passed", "آپ کی اپیل پر حکم جاری کیا گیا");
        m.put("aa.event.remanded", "اپیل دوبارہ غور کے لیے محتسب کو واپس بھیجی گئی");
        m.put("aa.event.dismissed", "اپیل خارج کر دی گئی");
        m.put("aa.event.reopened", "اپیل دوبارہ کھولی گئی");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "ಮೇಲ್ಮನವಿಯ ಪ್ರಸ್ತುತ ಹಂತದಲ್ಲಿ ಈ ಕ್ರಮಕ್ಕೆ ಅನುಮತಿ ಇಲ್ಲ. ಲಭ್ಯವಿರುವ ಕ್ರಮಗಳನ್ನು ನೋಡಲು ಪುಟವನ್ನು ಹೊಸದಾಗಿ ಲೋಡ್ ಮಾಡಿ.");
        m.put("aa.workflow.error_appeal_not_found", "ಮೇಲ್ಮನವಿ ಕಂಡುಬಂದಿಲ್ಲ");
        m.put("aa.workflow.error_invalid_target_role", "ಮಾನ್ಯವಾದ ಮೇಲ್ಮನವಿ ಪ್ರಾಧಿಕಾರ ಪಾತ್ರವನ್ನು ಆಯ್ಕೆ ಮಾಡಿ");
        m.put("aa.workflow.error_reopen_reason_required",
              "ವಿಲೇವಾರಿಯಾದ ಮೇಲ್ಮನವಿಯನ್ನು ಮತ್ತೆ ತೆರೆಯಲು ಕಾರಣ ನೀಡುವುದು ಕಡ್ಡಾಯ");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "ನಿಮ್ಮ ಪರಿಶೀಲಕ ಹಂತವನ್ನು ನಿರ್ಧರಿಸಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ. ನಿರ್ವಾಹಕರನ್ನು ಸಂಪರ್ಕಿಸಿ.");
        m.put("aa.workflow.error_already_tier2",
              "ಎರಡನೇ ಹಂತದ ಪರಿಶೀಲಕರು ಇದಕ್ಕಿಂತ ಮೇಲಿನ ಹಂತಕ್ಕೆ ಕಳುಹಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ");
        m.put("aa.workflow.error_no_tier2_reviewer", "ಸದ್ಯಕ್ಕೆ ಎರಡನೇ ಹಂತದ ಯಾವುದೇ ಪರಿಶೀಲಕರು ಲಭ್ಯವಿಲ್ಲ");
        m.put("aa.workflow.error_remand_parent_missing",
              "ಮೂಲ ದೂರು ಕಂಡುಬರಲಿಲ್ಲ, ಆದ್ದರಿಂದ ಈ ಮೇಲ್ಮನವಿಯನ್ನು ಮರುಪರಿಶೀಲನೆಗಾಗಿ ಹಿಂತಿರುಗಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ");
        m.put("aa.sla.on_track", "ಸಮಯಕ್ಕೆ ಸರಿಯಾಗಿ");
        m.put("aa.sla.at_risk", "ಶೀಘ್ರದಲ್ಲೇ ಗಡುವು");
        m.put("aa.sla.breached", "ಗಡುವು ಮೀರಿದೆ");
        m.put("aa.sla.not_tracked", "ಈ ಹಂತದಲ್ಲಿ ಯಾವುದೇ ಗಡುವು ಇಲ್ಲ");
        m.put("aa.event.filed", "ಮೇಲ್ಮನವಿ ಸಲ್ಲಿಸಲಾಗಿದೆ");
        m.put("aa.event.accepted", "ಮೇಲ್ಮನವಿಯನ್ನು ಪರಿಶೀಲನೆಗಾಗಿ ಸ್ವೀಕರಿಸಲಾಗಿದೆ");
        m.put("aa.event.rejected", "ಮೇಲ್ಮನವಿಯನ್ನು ತಿರಸ್ಕರಿಸಲಾಗಿದೆ");
        m.put("aa.event.assigned_to_reviewer", "ಮೇಲ್ಮನವಿಯನ್ನು ಒಬ್ಬ ಪರಿಶೀಲಕರಿಗೆ ವಹಿಸಲಾಗಿದೆ");
        m.put("aa.event.hearing_scheduled", "ವಿಚಾರಣೆ ನಿಗದಿಯಾಗಿದೆ");
        m.put("aa.event.escalated_to_tier2", "ಮೇಲ್ಮನವಿಯನ್ನು ಎರಡನೇ ಹಂತದ ಪರಿಶೀಲಕರಿಗೆ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("aa.event.order_passed", "ನಿಮ್ಮ ಮೇಲ್ಮನವಿಯ ಮೇಲೆ ಆದೇಶ ಹೊರಡಿಸಲಾಗಿದೆ");
        m.put("aa.event.remanded", "ಮೇಲ್ಮನವಿಯನ್ನು ಮರುಪರಿಶೀಲನೆಗಾಗಿ ಒಂಬುಡ್ಸ್‌ಮನ್‌ಗೆ ಹಿಂತಿರುಗಿಸಲಾಗಿದೆ");
        m.put("aa.event.dismissed", "ಮೇಲ್ಮನವಿಯನ್ನು ವಜಾ ಮಾಡಲಾಗಿದೆ");
        m.put("aa.event.reopened", "ಮೇಲ್ಮನವಿಯನ್ನು ಮತ್ತೆ ತೆರೆಯಲಾಗಿದೆ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.workflow.error_illegal_transition",
              "അപ്പീലിന്റെ നിലവിലുള്ള ഘട്ടത്തിൽ ഈ നടപടി അനുവദനീയമല്ല. ലഭ്യമായ നടപടികൾ കാണാൻ പേജ് പുതുക്കുക.");
        m.put("aa.workflow.error_appeal_not_found", "അപ്പീൽ കണ്ടെത്തിയില്ല");
        m.put("aa.workflow.error_invalid_target_role", "സാധുവായ ഒരു അപ്പീൽ അധികാരി ചുമതല തിരഞ്ഞെടുക്കുക");
        m.put("aa.workflow.error_reopen_reason_required",
              "തീർപ്പാക്കിയ അപ്പീൽ വീണ്ടും തുറക്കുന്നതിന് കാരണം നൽകേണ്ടത് നിർബന്ധമാണ്");
        m.put("aa.workflow.error_reviewer_tier_unknown",
              "നിങ്ങളുടെ പരിശോധകതലം നിർണയിക്കാൻ കഴിഞ്ഞില്ല. അഡ്മിനിസ്ട്രേറ്ററെ ബന്ധപ്പെടുക.");
        m.put("aa.workflow.error_already_tier2",
              "രണ്ടാം തലത്തിലുള്ള പരിശോധകന് ഇതിനു മുകളിലേക്ക് കൈമാറാൻ കഴിയില്ല");
        m.put("aa.workflow.error_no_tier2_reviewer", "നിലവിൽ രണ്ടാം തലത്തിലുള്ള പരിശോധകർ ആരും ലഭ്യമല്ല");
        m.put("aa.workflow.error_remand_parent_missing",
              "യഥാർത്ഥ പരാതി കണ്ടെത്താനായില്ല, അതിനാൽ ഈ അപ്പീൽ പുനഃപരിശോധനയ്ക്കായി തിരിച്ചയക്കാൻ കഴിയില്ല");
        m.put("aa.sla.on_track", "സമയബന്ധിതം");
        m.put("aa.sla.at_risk", "ഉടൻ അവസാന തീയതി");
        m.put("aa.sla.breached", "സമയപരിധി കഴിഞ്ഞു");
        m.put("aa.sla.not_tracked", "ഈ ഘട്ടത്തിൽ സമയപരിധി ഇല്ല");
        m.put("aa.event.filed", "അപ്പീൽ സമർപ്പിച്ചു");
        m.put("aa.event.accepted", "അപ്പീൽ പരിശോധനയ്ക്കായി സ്വീകരിച്ചു");
        m.put("aa.event.rejected", "അപ്പീൽ നിരസിച്ചു");
        m.put("aa.event.assigned_to_reviewer", "അപ്പീൽ ഒരു പരിശോധകനു കൈമാറി");
        m.put("aa.event.hearing_scheduled", "വാദം കേൾക്കൽ നിശ്ചയിച്ചു");
        m.put("aa.event.escalated_to_tier2", "അപ്പീൽ രണ്ടാം തലത്തിലുള്ള പരിശോധകനു കൈമാറി");
        m.put("aa.event.order_passed", "നിങ്ങളുടെ അപ്പീലിൽ ഉത്തരവ് പുറപ്പെടുവിച്ചു");
        m.put("aa.event.remanded", "അപ്പീൽ പുനഃപരിശോധനയ്ക്കായി ഓംബുഡ്സ്മാനു തിരിച്ചയച്ചു");
        m.put("aa.event.dismissed", "അപ്പീൽ തള്ളി");
        m.put("aa.event.reopened", "അപ്പീൽ വീണ്ടും തുറന്നു");
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
