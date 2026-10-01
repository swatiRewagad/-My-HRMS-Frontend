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
 * Appellate Authority classification and role vocabulary.
 *
 * <p>Two groups of keys, both already referenced by the AA UI:
 *
 * <ul>
 *   <li>{@code classification.*} — resolved by the shared status badge, which the AA dashboard and
 *       appeal detail render with {@code keyPrefix="classification"}. The badge lowercases the status
 *       value, so an {@code APPEAL} classification looks up {@code classification.appeal}. Without
 *       these rows the badge falls back to the raw enum, i.e. a citizen-visible "REPRESENTATION".
 *   <li>{@code aa.role_*} and the override labels — {@code AaDashboardComponent.roleLabels} maps each
 *       AA role to a translation key rather than an English literal, so the keys must exist or the
 *       role chip shows the key itself.
 * </ul>
 *
 * <p>Seeded in all ten supported locales, following {@link ReassignmentTranslationSeeder}: English
 * lives in {@code TranslationKey.defaultValue}, the other nine become {@code Translation} rows.
 * Insert-if-absent, so re-running is a no-op — but note the corollary: correcting a string here does
 * NOT rewrite a row already committed to a database. A text correction needs a code-scoped UPDATE in
 * both migration directories (see database/V32 and database/oracle/V30, which do exactly that for
 * the Scheme-year drift).
 */
@Component
@Order(11)
public class AaTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "aa";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public AaTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // ═══ Classification badge (status-badge keyPrefix="classification") ═══
        m.put("classification.appeal", "Appeal");
        m.put("classification.representation", "Representation");
        m.put("classification.unknown", "Unknown");

        // ═══ Manual classification override ═══
        m.put("aa.overridden", "Overridden");
        m.put("aa.classification_overridden", "Classification was manually overridden");

        // ═══ AA role names (AaDashboardComponent.roleLabels) ═══
        m.put("aa.role_do", "Dealing Officer");
        m.put("aa.role_reviewer", "Reviewer");
        m.put("aa.role_secretariat", "Secretariat");
        m.put("aa.role_admin", "AA Administrator");

        // ═══ Fail-closed appeal classification ═══
        // Returned as messageKey with the HTTP 503 body when a complaint's closure clause is missing
        // from CLOSURE_CLAUSE_MASTER, so Appeal-vs-Representation cannot be decided. Guessing could
        // wrongly deny statutory recourse under the Scheme, so the request fails closed and retries.
        m.put("appeal.error_clause_not_configured",
              "This complaint's closure clause is not yet configured, so we cannot determine whether "
              + "it can be appealed. Our team has been notified. Please try again shortly.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "अपील");
        m.put("classification.representation", "अभ्यावेदन");
        m.put("classification.unknown", "अज्ञात");
        m.put("aa.overridden", "अधिभावी");
        m.put("aa.classification_overridden", "वर्गीकरण को मैन्युअल रूप से बदला गया था");
        m.put("aa.role_do", "कार्यकारी अधिकारी");
        m.put("aa.role_reviewer", "समीक्षक");
        m.put("aa.role_secretariat", "सचिवालय");
        m.put("aa.role_admin", "अपीलीय प्राधिकारी प्रशासक");
        m.put("appeal.error_clause_not_configured",
              "इस शिकायत का समापन खंड अभी कॉन्फ़िगर नहीं किया गया है, इसलिए हम यह निर्धारित नहीं कर सकते कि "
              + "इसके विरुद्ध अपील की जा सकती है या नहीं। हमारी टीम को सूचित कर दिया गया है। कृपया कुछ ही समय में पुनः प्रयास करें।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "अपील");
        m.put("classification.representation", "निवेदन");
        m.put("classification.unknown", "अज्ञात");
        m.put("aa.overridden", "अधिक्रमित");
        m.put("aa.classification_overridden", "वर्गीकरण मॅन्युअली बदलले गेले होते");
        m.put("aa.role_do", "कार्यवाहक अधिकारी");
        m.put("aa.role_reviewer", "पुनरावलोकनकर्ता");
        m.put("aa.role_secretariat", "सचिवालय");
        m.put("aa.role_admin", "अपिलीय प्राधिकरण प्रशासक");
        m.put("appeal.error_clause_not_configured",
              "या तक्रारीचे समाप्ती कलम अद्याप कॉन्फिगर केलेले नाही, त्यामुळे यावर अपील करता येईल का हे आम्ही "
              + "निश्चित करू शकत नाही. आमच्या पथकाला कळविण्यात आले आहे. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "আপিল");
        m.put("classification.representation", "আবেদন");
        m.put("classification.unknown", "অজানা");
        m.put("aa.overridden", "অগ্রাহ্য করা হয়েছে");
        m.put("aa.classification_overridden", "শ্রেণিবিন্যাস হাতে পরিবর্তন করা হয়েছিল");
        m.put("aa.role_do", "কার্যনির্বাহী আধিকারিক");
        m.put("aa.role_reviewer", "পর্যালোচক");
        m.put("aa.role_secretariat", "সচিবালয়");
        m.put("aa.role_admin", "আপিল কর্তৃপক্ষ প্রশাসক");
        m.put("appeal.error_clause_not_configured",
              "এই অভিযোগের নিষ্পত্তির ধারাটি এখনও কনফিগার করা হয়নি, তাই এর বিরুদ্ধে আপিল করা যাবে কিনা তা "
              + "আমরা নির্ধারণ করতে পারছি না। আমাদের দলকে জানানো হয়েছে। অনুগ্রহ করে কিছুক্ষণ পরে আবার চেষ্টা করুন।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "అప్పీలు");
        m.put("classification.representation", "వినతి");
        m.put("classification.unknown", "తెలియదు");
        m.put("aa.overridden", "అధిగమించబడింది");
        m.put("aa.classification_overridden", "వర్గీకరణ మాన్యువల్‌గా మార్చబడింది");
        m.put("aa.role_do", "నిర్వహణ అధికారి");
        m.put("aa.role_reviewer", "సమీక్షకుడు");
        m.put("aa.role_secretariat", "సచివాలయం");
        m.put("aa.role_admin", "అప్పీలు అధికార నిర్వాహకుడు");
        m.put("appeal.error_clause_not_configured",
              "ఈ ఫిర్యాదు ముగింపు నిబంధన ఇంకా కాన్ఫిగర్ చేయబడలేదు, కాబట్టి దీనిపై అప్పీలు చేయవచ్చా లేదా అని "
              + "మేము నిర్ణయించలేము. మా బృందానికి తెలియజేయబడింది. దయచేసి కొద్దిసేపటి తర్వాత మళ్లీ ప్రయత్నించండి.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "மேல்முறையீடு");
        m.put("classification.representation", "மனு");
        m.put("classification.unknown", "தெரியவில்லை");
        m.put("aa.overridden", "மேலெழுதப்பட்டது");
        m.put("aa.classification_overridden", "வகைப்பாடு கைமுறையாக மாற்றப்பட்டது");
        m.put("aa.role_do", "நடவடிக்கை அதிகாரி");
        m.put("aa.role_reviewer", "மறுஆய்வாளர்");
        m.put("aa.role_secretariat", "செயலகம்");
        m.put("aa.role_admin", "மேல்முறையீட்டு ஆணையர் நிர்வாகி");
        m.put("appeal.error_clause_not_configured",
              "இந்த முறையீட்டின் முடிவுரை விதி இன்னும் அமைக்கப்படவில்லை, எனவே இதற்கு மேல்முறையீடு செய்ய "
              + "முடியுமா என்பதை நாங்கள் தீர்மானிக்க முடியவில்லை. எங்கள் குழுவிற்குத் தெரிவிக்கப்பட்டுள்ளது. "
              + "சிறிது நேரம் கழித்து மீண்டும் முயற்சிக்கவும்.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "અપીલ");
        m.put("classification.representation", "રજૂઆત");
        m.put("classification.unknown", "અજ્ઞાત");
        m.put("aa.overridden", "અધિક્રમિત");
        m.put("aa.classification_overridden", "વર્ગીકરણ મેન્યુઅલી બદલવામાં આવ્યું હતું");
        m.put("aa.role_do", "કાર્યવાહક અધિકારી");
        m.put("aa.role_reviewer", "સમીક્ષક");
        m.put("aa.role_secretariat", "સચિવાલય");
        m.put("aa.role_admin", "અપીલ સત્તાધિકારી પ્રશાસક");
        m.put("appeal.error_clause_not_configured",
              "આ ફરિયાદનું સમાપન કલમ હજુ કૉન્ફિગર કરવામાં આવ્યું નથી, તેથી તેની સામે અપીલ કરી શકાય કે નહીં તે "
              + "અમે નક્કી કરી શકતા નથી. અમારી ટીમને જાણ કરવામાં આવી છે. કૃપા કરીને થોડા સમય પછી ફરી પ્રયાસ કરો.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "اپیل");
        m.put("classification.representation", "درخواست");
        m.put("classification.unknown", "نامعلوم");
        m.put("aa.overridden", "منسوخ شدہ");
        m.put("aa.classification_overridden", "درجہ بندی کو دستی طور پر تبدیل کیا گیا تھا");
        m.put("aa.role_do", "کارروائی افسر");
        m.put("aa.role_reviewer", "جائزہ کار");
        m.put("aa.role_secretariat", "سیکرٹریٹ");
        m.put("aa.role_admin", "اپیلٹ اتھارٹی منتظم");
        m.put("appeal.error_clause_not_configured",
              "اس شکایت کی اختتامی شرط ابھی ترتیب نہیں دی گئی ہے، اس لیے ہم یہ طے نہیں کر سکتے کہ اس پر "
              + "اپیل کی جا سکتی ہے یا نہیں۔ ہماری ٹیم کو مطلع کر دیا گیا ہے۔ براہ کرم کچھ دیر بعد دوبارہ کوشش کریں۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "ಮೇಲ್ಮನವಿ");
        m.put("classification.representation", "ಮನವಿ");
        m.put("classification.unknown", "ಅಜ್ಞಾತ");
        m.put("aa.overridden", "ಅತಿಕ್ರಮಿಸಲಾಗಿದೆ");
        m.put("aa.classification_overridden", "ವರ್ಗೀಕರಣವನ್ನು ಕೈಯಾರೆ ಬದಲಾಯಿಸಲಾಗಿದೆ");
        m.put("aa.role_do", "ಕಾರ್ಯನಿರ್ವಹಣಾ ಅಧಿಕಾರಿ");
        m.put("aa.role_reviewer", "ಪರಿಶೀಲಕ");
        m.put("aa.role_secretariat", "ಸಚಿವಾಲಯ");
        m.put("aa.role_admin", "ಮೇಲ್ಮನವಿ ಪ್ರಾಧಿಕಾರ ನಿರ್ವಾಹಕ");
        m.put("appeal.error_clause_not_configured",
              "ಈ ದೂರಿನ ಮುಕ್ತಾಯ ಷರತ್ತನ್ನು ಇನ್ನೂ ಕಾನ್ಫಿಗರ್ ಮಾಡಲಾಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಇದರ ವಿರುದ್ಧ ಮೇಲ್ಮನವಿ "
              + "ಸಲ್ಲಿಸಬಹುದೇ ಎಂಬುದನ್ನು ನಾವು ನಿರ್ಧರಿಸಲಾಗುತ್ತಿಲ್ಲ. ನಮ್ಮ ತಂಡಕ್ಕೆ ತಿಳಿಸಲಾಗಿದೆ. ದಯವಿಟ್ಟು ಸ್ವಲ್ಪ ಸಮಯದ ನಂತರ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("classification.appeal", "അപ്പീൽ");
        m.put("classification.representation", "നിവേദനം");
        m.put("classification.unknown", "അജ്ഞാതം");
        m.put("aa.overridden", "അതിലംഘിച്ചു");
        m.put("aa.classification_overridden", "വർഗ്ഗീകരണം സ്വമേധയാ മാറ്റിയിരുന്നു");
        m.put("aa.role_do", "നടപടി ഓഫീസർ");
        m.put("aa.role_reviewer", "പുനഃപരിശോധകൻ");
        m.put("aa.role_secretariat", "സെക്രട്ടേറിയറ്റ്");
        m.put("aa.role_admin", "അപ്പീൽ അധികാരി അഡ്മിനിസ്ട്രേറ്റർ");
        m.put("appeal.error_clause_not_configured",
              "ഈ പരാതിയുടെ അവസാനിപ്പിക്കൽ വ്യവസ്ഥ ഇതുവരെ കോൺഫിഗർ ചെയ്തിട്ടില്ല, അതിനാൽ ഇതിനെതിരെ അപ്പീൽ "
              + "നൽകാനാകുമോ എന്ന് ഞങ്ങൾക്ക് നിർണ്ണയിക്കാൻ കഴിയുന്നില്ല. ഞങ്ങളുടെ ടീമിനെ അറിയിച്ചിട്ടുണ്ട്. "
              + "കുറച്ച് സമയത്തിന് ശേഷം വീണ്ടും ശ്രമിക്കുക.");
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
