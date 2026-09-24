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
 * The page title for the AA appeals dashboard, missing when AA was moved onto the shared staff shell.
 *
 * A NEW seeder rather than an edit to {@code UiShellTranslationSeeder}, per the convention here:
 * several sessions add keys concurrently and a shared file is a guaranteed conflict in a place where
 * a conflict silently costs a locale.
 *
 * Why a {@code ui.page.*} key and not a reuse of {@code ui.nav.aa_appeals}: the two already differ
 * by design across every other module. The sidebar needs a short label that fits a collapsed rail
 * ("Appeals"), the content heading names the screen in full ("Appeals Dashboard") -- exactly as
 * ui.nav.cepc_dashboard / ui.page.cepc_dashboard do. AA was the only shell-mounted module with no
 * page-title key, so its heading would have fallen back to rendering the raw key.
 */
@Component
@Order(66)
public class AaPageTitleTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "ui";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public AaPageTitleTranslationSeeder(TranslationKeyRepository keyRepo,
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
        m.put("ui.page.aa_appeals", "Appeals Dashboard");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "अपील डैशबोर्ड");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "अपील डॅशबोर्ड");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "আপিল ড্যাশবোর্ড");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "అప్పీళ్ల డాష్‌బోర్డ్");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "மேல்முறையீட்டு டாஷ்போர்டு");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "અપીલ ડેશબોર્ડ");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "اپیل ڈیش بورڈ");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "ಮೇಲ್ಮನವಿ ಡ್ಯಾಶ್‌ಬೋರ್ಡ್");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "അപ്പീൽ ഡാഷ്‌ബോർഡ്");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.page.aa_appeals", "ਅਪੀਲ ਡੈਸ਼ਬੋਰਡ");
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
