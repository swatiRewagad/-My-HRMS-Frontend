package com.hrms.cms.config;

import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(4)
public class Phase2WizardTranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;

    public Phase2WizardTranslationSeeder(TranslationKeyRepository keyRepo) {
        this.keyRepo = keyRepo;
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
