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
import java.util.List;
import java.util.Map;

/**
 * The six {@link com.hrms.cms.entity.DraftStatus} values that the complaint status vocabulary does not
 * cover.
 *
 * <p>StatusVocabularyTranslationSeeder (@Order(8)) is the authority for {@code status.*}, but it was
 * written from the complaint/appeal lifecycle and so carries no key for an intake draft's own states.
 * Four DraftStatus values happen to collide with complaint statuses and are already served
 * (assigned, in_progress, rejected, draft); these six are not:
 * SENT_TO_REVIEWER, APPROVED_ROUTED, CONVERTED, DUPLICATE, IGNORED, PENDING_MANUAL_ENTRY.
 *
 * <p>WHY THIS MATTERS NOW. The CRPC assessment screens are moving onto the shared status badge, which
 * derives {@code status.<lowercased value>} and falls back to a humanised form of the raw enum when the
 * key is absent. Without these rows a reviewer would read "Sent To Reviewer" and "Approved Routed" —
 * mechanical de-underscoring of an internal enum, which is exactly the wording the badge exists to stop
 * leaking. The English values below are the labels those screens printed as literals before the
 * migration, so the text on screen does not change; only its source does.
 *
 * <p>A SEPARATE seeder at its own {@code @Order(67)} rather than an edit to the @Order(8) file, per the
 * convention here: concurrent sessions add keys and editing a shared file is a guaranteed conflict,
 * where a conflict silently costs a locale. 1-22 and 32-66 were verified taken before choosing 67.
 *
 * <p>Insert-if-absent, unlike the @Order(8) seeder's upsert. That seeder overwrites because earlier
 * seeders write the same codes with generic wording; no one else writes these six, so there is nothing
 * to correct and the ordinary convention applies.
 */
@Component
@Order(67)
public class DraftStatusTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "status";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public DraftStatusTranslationSeeder(TranslationKeyRepository keyRepo, TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

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
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "Sent to Reviewer");
        m.put("status.approved_routed", "Approved & Routed");
        m.put("status.converted", "Converted to Complaint");
        m.put("status.duplicate", "Duplicate");
        m.put("status.ignored", "Ignored");
        m.put("status.pending_manual_entry", "Pending Manual Entry");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "समीक्षक को भेजा गया");
        m.put("status.approved_routed", "अनुमोदित एवं अग्रेषित");
        m.put("status.converted", "शिकायत में परिवर्तित");
        m.put("status.duplicate", "प्रतिरूप");
        m.put("status.ignored", "उपेक्षित");
        m.put("status.pending_manual_entry", "मैनुअल प्रविष्टि लंबित");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "পর্যালোচকের কাছে পাঠানো হয়েছে");
        m.put("status.approved_routed", "অনুমোদিত ও প্রেরিত");
        m.put("status.converted", "অভিযোগে রূপান্তরিত");
        m.put("status.duplicate", "প্রতিলিপি");
        m.put("status.ignored", "উপেক্ষিত");
        m.put("status.pending_manual_entry", "ম্যানুয়াল এন্ট্রি মুলতুবি");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "समीक्षकाकडे पाठवले");
        m.put("status.approved_routed", "मंजूर व पाठवले");
        m.put("status.converted", "तक्रारीत रूपांतरित");
        m.put("status.duplicate", "प्रतिरूप");
        m.put("status.ignored", "दुर्लक्षित");
        m.put("status.pending_manual_entry", "मॅन्युअल नोंद प्रलंबित");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "సమీక్షకుడికి పంపబడింది");
        m.put("status.approved_routed", "ఆమోదించి పంపబడింది");
        m.put("status.converted", "ఫిర్యాదుగా మార్చబడింది");
        m.put("status.duplicate", "నకిలీ");
        m.put("status.ignored", "విస్మరించబడింది");
        m.put("status.pending_manual_entry", "మాన్యువల్ నమోదు పెండింగ్");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "மதிப்பாய்வாளருக்கு அனுப்பப்பட்டது");
        m.put("status.approved_routed", "அனுமதிக்கப்பட்டு அனுப்பப்பட்டது");
        m.put("status.converted", "புகாராக மாற்றப்பட்டது");
        m.put("status.duplicate", "நகல்");
        m.put("status.ignored", "புறக்கணிக்கப்பட்டது");
        m.put("status.pending_manual_entry", "கைமுறை பதிவு நிலுவையில்");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "સમીક્ષકને મોકલ્યું");
        m.put("status.approved_routed", "મંજૂર અને મોકલ્યું");
        m.put("status.converted", "ફરિયાદમાં રૂપાંતરિત");
        m.put("status.duplicate", "નકલ");
        m.put("status.ignored", "અવગણેલું");
        m.put("status.pending_manual_entry", "મેન્યુઅલ એન્ટ્રી બાકી");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "جائزہ کار کو بھیجا گیا");
        m.put("status.approved_routed", "منظور شدہ اور بھیجا گیا");
        m.put("status.converted", "شکایت میں تبدیل");
        m.put("status.duplicate", "نقل");
        m.put("status.ignored", "نظر انداز");
        m.put("status.pending_manual_entry", "دستی اندراج زیر التواء");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "ಪರಿಶೀಲಕರಿಗೆ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("status.approved_routed", "ಅನುಮೋದಿಸಿ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("status.converted", "ದೂರಿಗೆ ಪರಿವರ್ತಿಸಲಾಗಿದೆ");
        m.put("status.duplicate", "ನಕಲು");
        m.put("status.ignored", "ನಿರ್ಲಕ್ಷಿಸಲಾಗಿದೆ");
        m.put("status.pending_manual_entry", "ಹಸ್ತಚಾಲಿತ ನಮೂದು ಬಾಕಿ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "പുനരവലോകകന് അയച്ചു");
        m.put("status.approved_routed", "അനുമതി നൽകി അയച്ചു");
        m.put("status.converted", "പരാതിയായി മാറ്റി");
        m.put("status.duplicate", "തനിപ്പകർപ്പ്");
        m.put("status.ignored", "അവഗണിച്ചു");
        m.put("status.pending_manual_entry", "നേരിട്ടുള്ള രേഖപ്പെടുത്തൽ ശേഷിക്കുന്നു");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.sent_to_reviewer", "ਸਮੀਖਿਅਕ ਨੂੰ ਭੇਜਿਆ");
        m.put("status.approved_routed", "ਮਨਜ਼ੂਰ ਅਤੇ ਭੇਜਿਆ");
        m.put("status.converted", "ਸ਼ਿਕਾਇਤ ਵਿੱਚ ਬਦਲਿਆ");
        m.put("status.duplicate", "ਨਕਲ");
        m.put("status.ignored", "ਅਣਗੌਲਿਆ");
        m.put("status.pending_manual_entry", "ਹੱਥੀਂ ਦਰਜ ਕਰਨਾ ਬਾਕੀ");
        return m;
    }

    private void seed(String code, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(MODULE);
        key.setDefaultValue(defaultValue);
        keyRepo.save(key);
        putTranslation(key, "en", defaultValue);
    }

    private void seedLocale(String locale, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            keyRepo.findByCode(entry.getKey())
                .ifPresent(key -> putTranslation(key, locale, entry.getValue()));
        }
    }

    private void putTranslation(TranslationKey key, String locale, String value) {
        List<Translation> existing = translationRepo.findByKeyIdAndLocale(key.getId(), locale);
        if (existing.isEmpty()) {
            Translation t = new Translation();
            t.setTranslationKey(key);
            t.setLocale(locale);
            t.setValue(value);
            translationRepo.save(t);
        }
    }
}
