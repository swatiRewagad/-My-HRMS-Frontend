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
 * Translations for the FAIL-CLOSED master-data states on citizen-facing forms.
 *
 * <p>WHY THESE KEYS EXIST. The complaint wizard used to read its grievance categories from
 * CATEGORY_MASTER — a table holding only inactive E2E probes — and, on getting an empty list, silently
 * substituted a COMPILED-IN array of categories. A complainant was therefore choosing from hardcoded
 * constants that no master table governed, and the category drives routing and maintainability. The
 * wizard now reads the authoritative COMPLAINT_CATEGORIES and, if it cannot, blocks the step and offers
 * a retry instead of guessing. These are the strings for that blocked state.
 *
 * <p>A NEW seeder at {@code @Order(61)} rather than an edit to UiShellTranslationSeeder (60), per the
 * convention here: several sessions add keys concurrently and a shared file is a guaranteed conflict in
 * a place where a conflict silently costs a locale. 61 was verified unclaimed — 1-22, 32-34, 38, 42-44
 * and 60 are in use.
 *
 * <p>Seeding is insert-if-absent by key code, so correcting a value here does NOT update a row already
 * in the database; that needs a code-scoped UPDATE in both migration directories.
 *
 * <p>Scope limit, deliberately: no key here names a clause, a statutory ground or a compensation limit.
 * Those stay English-only pending legal sign-off, because translating a legal label asserts what the law
 * means.
 */
@Component
@Order(61)
public class MasterDataFallbackTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "form";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public MasterDataFallbackTranslationSeeder(TranslationKeyRepository keyRepo,
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
        seedLocale("pa", punjabi());
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "The complaint categories could not be loaded. Please retry — your complaint cannot be "
                        + "categorised until they are available.");
        m.put("ui.common.select_value", "Select Value");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "शिकायत श्रेणियाँ लोड नहीं हो सकीं। कृपया पुनः प्रयास करें — जब तक वे उपलब्ध नहीं होतीं, "
                        + "आपकी शिकायत का वर्गीकरण नहीं किया जा सकता।");
        m.put("ui.common.select_value", "मान चुनें");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "तक्रार श्रेणी लोड होऊ शकल्या नाहीत. कृपया पुन्हा प्रयत्न करा — त्या उपलब्ध होईपर्यंत "
                        + "तुमच्या तक्रारीचे वर्गीकरण करता येणार नाही.");
        m.put("ui.common.select_value", "मूल्य निवडा");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "অভিযোগের বিভাগগুলি লোড করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন — সেগুলি উপলব্ধ না "
                        + "হওয়া পর্যন্ত আপনার অভিযোগ শ্রেণীবদ্ধ করা যাবে না।");
        m.put("ui.common.select_value", "মান নির্বাচন করুন");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "ఫిర్యాదు వర్గాలను లోడ్ చేయడం సాధ్యం కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి — అవి అందుబాటులోకి "
                        + "వచ్చే వరకు మీ ఫిర్యాదును వర్గీకరించడం సాధ్యం కాదు.");
        m.put("ui.common.select_value", "విలువను ఎంచుకోండి");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "புகார் வகைகளை ஏற்ற முடியவில்லை. மீண்டும் முயற்சிக்கவும் — அவை கிடைக்கும் வரை உங்கள் "
                        + "புகாரை வகைப்படுத்த முடியாது.");
        m.put("ui.common.select_value", "மதிப்பைத் தேர்ந்தெடுக்கவும்");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "ફરિયાદ શ્રેણીઓ લોડ થઈ શકી નથી. કૃપા કરીને ફરી પ્રયાસ કરો — તે ઉપલબ્ધ થાય ત્યાં સુધી તમારી "
                        + "ફરિયાદનું વર્ગીકરણ કરી શકાતું નથી.");
        m.put("ui.common.select_value", "મૂલ્ય પસંદ કરો");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "شکایت کی اقسام لوڈ نہیں ہو سکیں۔ براہ کرم دوبارہ کوشش کریں — جب تک وہ دستیاب نہ ہوں، "
                        + "آپ کی شکایت کی درجہ بندی نہیں کی جا سکتی۔");
        m.put("ui.common.select_value", "قیمت منتخب کریں");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "ದೂರಿನ ವರ್ಗಗಳನ್ನು ಲೋಡ್ ಮಾಡಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ — ಅವು ಲಭ್ಯವಾಗುವವರೆಗೆ "
                        + "ನಿಮ್ಮ ದೂರನ್ನು ವರ್ಗೀಕರಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ.");
        m.put("ui.common.select_value", "ಮೌಲ್ಯವನ್ನು ಆಯ್ಕೆಮಾಡಿ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "പരാതി വിഭാഗങ്ങൾ ലോഡ് ചെയ്യാൻ കഴിഞ്ഞില്ല. വീണ്ടും ശ്രമിക്കുക — അവ ലഭ്യമാകുന്നതുവരെ നിങ്ങളുടെ "
                        + "പരാതി വർഗ്ഗീകരിക്കാൻ കഴിയില്ല.");
        m.put("ui.common.select_value", "മൂല്യം തിരഞ്ഞെടുക്കുക");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("form.categories_unavailable",
                "ਸ਼ਿਕਾਇਤ ਸ਼੍ਰੇਣੀਆਂ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕੀਆਂ। ਕਿਰਪਾ ਕਰਕੇ ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ — ਜਦੋਂ ਤੱਕ ਉਹ ਉਪਲਬਧ ਨਹੀਂ "
                        + "ਹੁੰਦੀਆਂ, ਤੁਹਾਡੀ ਸ਼ਿਕਾਇਤ ਦਾ ਵਰਗੀਕਰਨ ਨਹੀਂ ਕੀਤਾ ਜਾ ਸਕਦਾ।");
        m.put("ui.common.select_value", "ਮੁੱਲ ਚੁਣੋ");
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
