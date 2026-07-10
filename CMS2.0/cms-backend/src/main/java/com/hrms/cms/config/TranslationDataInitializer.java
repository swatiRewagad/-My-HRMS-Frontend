package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
@Order(2)
@Slf4j
public class TranslationDataInitializer implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    @Override
    @Transactional
    public void run(String... args) {
        if (keyRepo.count() > 0) {
            log.info("Translation data already loaded ({} keys)", keyRepo.count());
            return;
        }

        log.info("Loading translation data from CSV...");
        try {
            ClassPathResource resource = new ClassPathResource("data/translations.csv");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

            String header = reader.readLine();
            Map<String, TranslationKey> keyCache = new LinkedHashMap<>();
            List<Translation> translations = new ArrayList<>(500);

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 6) continue;

                String code = parts[0].trim();
                String module = parts[1].trim();
                String description = parts[2].trim();
                String defaultValue = parts[3].trim();
                String locale = parts[4].trim();
                String value = parts[5].trim();

                TranslationKey key = keyCache.computeIfAbsent(code, k -> {
                    TranslationKey tk = new TranslationKey();
                    tk.setCode(k);
                    tk.setModule(module);
                    tk.setDescription(description);
                    tk.setDefaultValue(defaultValue);
                    return keyRepo.save(tk);
                });

                Translation t = new Translation();
                t.setTranslationKey(key);
                t.setLocale(locale);
                t.setValue(value);
                translations.add(t);

                if (translations.size() >= 200) {
                    translationRepo.saveAll(translations);
                    translations.clear();
                }
            }

            if (!translations.isEmpty()) {
                translationRepo.saveAll(translations);
            }

            reader.close();
            log.info("Loaded {} translation keys with translations from CSV", keyCache.size());

        } catch (Exception e) {
            log.error("Failed to load translations from CSV: {}", e.getMessage());
            log.info("Falling back to inline translation seed data...");
            seedInline();
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private void seedInline() {
        List<String[]> keys = new ArrayList<>();
        keys.add(new String[]{"common.app_title", "common", "Application title", "Complaint Management System"});
        keys.add(new String[]{"common.home", "common", "Home navigation", "Home"});
        keys.add(new String[]{"common.back", "common", "Back button", "Back"});
        keys.add(new String[]{"common.next", "common", "Next button", "Next"});
        keys.add(new String[]{"common.submit", "common", "Submit button", "Submit"});
        keys.add(new String[]{"common.cancel", "common", "Cancel button", "Cancel"});
        keys.add(new String[]{"common.save", "common", "Save button", "Save"});
        keys.add(new String[]{"common.close", "common", "Close button", "Close"});
        keys.add(new String[]{"common.loading", "common", "Loading indicator", "Loading..."});
        keys.add(new String[]{"common.error", "common", "Generic error", "An error occurred"});
        keys.add(new String[]{"common.success", "common", "Generic success", "Success"});
        keys.add(new String[]{"common.required_field", "common", "Required field message", "This field is required"});
        keys.add(new String[]{"common.yes", "common", "Yes option", "Yes"});
        keys.add(new String[]{"common.no", "common", "No option", "No"});
        keys.add(new String[]{"common.select_option", "common", "Select placeholder", "Select an option"});
        keys.add(new String[]{"common.search", "common", "Search placeholder", "Search"});
        keys.add(new String[]{"common.logout", "common", "Logout", "Logout"});
        keys.add(new String[]{"common.language", "common", "Language selector label", "Language"});
        keys.add(new String[]{"nav.about_us", "nav", "About us nav link", "About us"});
        keys.add(new String[]{"nav.public_awareness", "nav", "Public awareness nav link", "Public Awareness"});
        keys.add(new String[]{"nav.my_complaints", "nav", "My complaints nav link", "My Complaints"});
        keys.add(new String[]{"nav.login", "nav", "Login nav link", "Login"});
        keys.add(new String[]{"home.hero_title", "home", "Hero title", "Making complaint resolution"});
        keys.add(new String[]{"home.hero_highlight", "home", "Hero highlight word", "Easier"});
        keys.add(new String[]{"home.file_complaint", "home", "File complaint button", "File a Complaint"});
        keys.add(new String[]{"login.title", "login", "Login page title", "Verification"});
        keys.add(new String[]{"login.verify_phone", "login", "Verify phone heading", "Verify Phone Number"});
        keys.add(new String[]{"login.mobile_label", "login", "Mobile field label", "Mobile Number"});
        keys.add(new String[]{"login.send_otp", "login", "Send OTP button", "Send OTP"});
        keys.add(new String[]{"login.verify_otp", "login", "Verify OTP button", "Verify OTP"});
        keys.add(new String[]{"complaint.title", "complaint", "Complaint form title", "Raise a Complaint"});
        keys.add(new String[]{"complaint.name", "complaint", "Name field", "Name"});
        keys.add(new String[]{"complaint.email", "complaint", "Email field", "Email (Optional)"});
        keys.add(new String[]{"complaint.pincode", "complaint", "Pincode field", "Pincode"});
        keys.add(new String[]{"complaint.district", "complaint", "District field", "District"});
        keys.add(new String[]{"complaint.state", "complaint", "State field", "State"});
        keys.add(new String[]{"status.pending", "status", "Pending status", "Pending"});
        keys.add(new String[]{"status.in_progress", "status", "In progress status", "In Progress"});
        keys.add(new String[]{"status.resolved", "status", "Resolved status", "Resolved"});
        keys.add(new String[]{"status.closed", "status", "Closed status", "Closed"});

        for (String[] k : keys) {
            TranslationKey key = new TranslationKey();
            key.setCode(k[0]);
            key.setModule(k[1]);
            key.setDescription(k[2]);
            key.setDefaultValue(k[3]);
            key = keyRepo.save(key);

            Translation enTranslation = new Translation();
            enTranslation.setTranslationKey(key);
            enTranslation.setLocale("en");
            enTranslation.setValue(k[3]);
            translationRepo.save(enTranslation);
        }

        seedHindiFallback();
        log.info("Seeded {} inline translation keys as fallback", keys.size());
    }

    private void seedHindiFallback() {
        Map<String, String> hi = new LinkedHashMap<>();
        hi.put("common.app_title", "शिकायत प्रबंधन प्रणाली");
        hi.put("common.home", "होम");
        hi.put("common.back", "वापस");
        hi.put("common.next", "अगला");
        hi.put("common.submit", "जमा करें");
        hi.put("common.cancel", "रद्द करें");
        hi.put("common.yes", "हाँ");
        hi.put("common.no", "नहीं");
        hi.put("common.language", "भाषा");
        hi.put("login.title", "सत्यापन");
        hi.put("login.verify_phone", "फ़ोन नंबर सत्यापित करें");
        hi.put("login.mobile_label", "मोबाइल नंबर");
        hi.put("login.send_otp", "OTP भेजें");
        hi.put("complaint.title", "शिकायत दर्ज करें");
        hi.put("complaint.name", "नाम");
        hi.put("complaint.email", "ईमेल (वैकल्पिक)");
        hi.put("complaint.pincode", "पिनकोड");
        hi.put("complaint.district", "जिला");
        hi.put("complaint.state", "राज्य");
        hi.put("status.pending", "लंबित");
        hi.put("status.in_progress", "प्रगति में");
        hi.put("status.resolved", "हल किया गया");
        hi.put("status.closed", "बंद");

        for (Map.Entry<String, String> entry : hi.entrySet()) {
            keyRepo.findByCode(entry.getKey()).ifPresent(key -> {
                Translation t = new Translation();
                t.setTranslationKey(key);
                t.setLocale("hi");
                t.setValue(entry.getValue());
                translationRepo.save(t);
            });
        }
    }
}
