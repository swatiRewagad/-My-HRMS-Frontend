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
 * The CEPC dashboard's KPI cards, status buckets and grid toggles.
 *
 * <h2>Why these keys did not already exist</h2>
 * The CEPC dashboard rendered its five stat-card labels and six status tabs as HARDCODED ENGLISH
 * literals in the template, in an application that serves eleven locales. The RBIO dashboard — the
 * reference this one is being brought onto — reads the same cards from {@code rbio.stats.*}. Those keys
 * are reused rather than duplicated under a {@code cepc.} prefix wherever the card means the same
 * thing, because a card labelled "Pending with Me" in two modules must not be able to drift into two
 * different wordings. What is seeded here is only what CEPC needs and RBIO does not have.
 *
 * <h2>@Order(70)</h2>
 * A new seeder rather than an edit to TaskGridTranslationSeeder (62), per the convention here:
 * concurrent sessions add keys and a shared file is a guaranteed conflict, where a conflict silently
 * costs a locale. 63-69 were verified taken; 70 was free.
 *
 * <p>Seeding is insert-if-absent by key code, so a duplicate code anywhere would silently keep the
 * FIRST text. The {@code cepc.stats.*}, {@code cepc.bucket.*} and {@code ui.grid.filter_*} namespaces
 * were verified unused before this was written.
 */
@Component
@Order(70)
public class CepcDashboardTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "cepc";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public CepcDashboardTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // ═══ Buckets the CEPC status vocabulary needs and status.* does not carry ═══
        // MEETING_SCHEDULED is a workflow STAGE, not a status, so there is no status.* key for it.
        m.put("cepc.bucket.meeting_scheduled", "Meeting Scheduled");
        m.put("cepc.bucket.reopened", "Reopened");
        // ═══ Grid toggles ═══
        m.put("ui.grid.filter_unread", "Unread");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "बैठक निर्धारित");
        m.put("cepc.bucket.reopened", "पुनः खोला गया");
        m.put("ui.grid.filter_unread", "अपठित");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "बैठक निश्चित");
        m.put("cepc.bucket.reopened", "पुन्हा उघडले");
        m.put("ui.grid.filter_unread", "न वाचलेले");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "বৈঠক নির্ধারিত");
        m.put("cepc.bucket.reopened", "পুনরায় খোলা হয়েছে");
        m.put("ui.grid.filter_unread", "অপঠিত");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "సమావేశం నిర్ణయించబడింది");
        m.put("cepc.bucket.reopened", "తిరిగి తెరవబడింది");
        m.put("ui.grid.filter_unread", "చదవనివి");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "கூட்டம் நிர்ணயிக்கப்பட்டது");
        m.put("cepc.bucket.reopened", "மீண்டும் திறக்கப்பட்டது");
        m.put("ui.grid.filter_unread", "படிக்கப்படாதது");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "બેઠક નિર્ધારિત");
        m.put("cepc.bucket.reopened", "ફરીથી ખોલેલું");
        m.put("ui.grid.filter_unread", "વાંચ્યા વગરનું");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "اجلاس مقرر");
        m.put("cepc.bucket.reopened", "دوبارہ کھولا گیا");
        m.put("ui.grid.filter_unread", "غیر پڑھا");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "ಸಭೆ ನಿಗದಿಯಾಗಿದೆ");
        m.put("cepc.bucket.reopened", "ಮರು ತೆರೆಯಲಾಗಿದೆ");
        m.put("ui.grid.filter_unread", "ಓದದಿರುವುದು");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "യോഗം നിശ്ചയിച്ചു");
        m.put("cepc.bucket.reopened", "വീണ്ടും തുറന്നു");
        m.put("ui.grid.filter_unread", "വായിക്കാത്തത്");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cepc.bucket.meeting_scheduled", "ਮੀਟਿੰਗ ਨਿਰਧਾਰਤ");
        m.put("cepc.bucket.reopened", "ਦੁਬਾਰਾ ਖੋਲ੍ਹਿਆ ਗਿਆ");
        m.put("ui.grid.filter_unread", "ਅਣਪੜ੍ਹਿਆ");
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
