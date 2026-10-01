package com.hrms.cms.config;

import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(3)
public class Phase1TranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;

    public Phase1TranslationSeeder(TranslationKeyRepository keyRepo) {
        this.keyRepo = keyRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedIfAbsent("complaint.prior_re_complaint", "complaint", "Prior RE complaint label", "Have you already complained to the Regulated Entity?");
        seedIfAbsent("complaint.prior_re_complaint_hint", "complaint", "Prior RE complaint hint", "Select Yes if you have already filed a complaint with the bank/NBFC/PPI about this issue.");
        seedIfAbsent("complaint.re_complaint_date", "complaint", "RE complaint date label", "Date of complaint to Regulated Entity");
        seedIfAbsent("complaint.re_complaint_date_hint", "complaint", "RE complaint date hint", "Enter the date when you first complained to the Regulated Entity.");
        seedIfAbsent("complaint.re_complaint_reference", "complaint", "RE complaint reference label", "RE complaint reference / acknowledgement number");
        seedIfAbsent("complaint.re_complaint_reference_hint", "complaint", "RE reference hint", "Enter the reference or acknowledgement number received from the Regulated Entity.");
        seedIfAbsent("complaint.re_replied_and_dissatisfied", "complaint", "RE replied and dissatisfied label", "Has the Regulated Entity replied and are you dissatisfied with the response?");
        seedIfAbsent("complaint.re_replied_hint", "complaint", "RE replied hint", "Select Yes if the RE has responded but you are not satisfied with their resolution.");
        seedIfAbsent("complaint.validation.re_date_required", "complaint", "RE date required validation", "Date of complaint to RE is required when prior complaint is indicated.");
        seedIfAbsent("complaint.validation.re_reference_required", "complaint", "RE reference required validation", "RE complaint reference is required when prior complaint is indicated.");
        seedIfAbsent("complaint.validation.re_date_future", "complaint", "RE date future validation", "Date of complaint to RE cannot be in the future.");
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
}
