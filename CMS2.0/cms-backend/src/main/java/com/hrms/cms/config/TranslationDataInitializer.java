package com.hrms.cms.config;

import com.hrms.cms.entity.SupportedLocale;
import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@Order(2)
public class TranslationDataInitializer implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public TranslationDataInitializer(TranslationKeyRepository keyRepo, TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (keyRepo.count() > 0) return;
        seedKeys();
    }

    private void seedKeys() {
        List<String[]> keys = new ArrayList<>();

        // Common / Navigation
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

        // Navigation
        keys.add(new String[]{"nav.about_us", "nav", "About us nav link", "About us"});
        keys.add(new String[]{"nav.public_awareness", "nav", "Public awareness nav link", "Public Awareness"});
        keys.add(new String[]{"nav.my_complaints", "nav", "My complaints nav link", "My Complaints"});
        keys.add(new String[]{"nav.login", "nav", "Login nav link", "Login"});

        // Home page
        keys.add(new String[]{"home.hero_title", "home", "Hero title", "Making complaint resolution"});
        keys.add(new String[]{"home.hero_highlight", "home", "Hero highlight word", "Easier"});
        keys.add(new String[]{"home.hero_subtitle", "home", "Hero subtitle", "Reserve Bank of India's trusted platform for complaint resolution - effortlessly file and track grievances against Regulated Entities."});
        keys.add(new String[]{"home.file_complaint", "home", "File complaint button", "File a Complaint"});
        keys.add(new String[]{"home.already_filed", "home", "Already filed text", "Already Filed A Complaint?"});
        keys.add(new String[]{"home.track_withdraw", "home", "Track/withdraw button", "Track or Withdraw Complaint"});
        keys.add(new String[]{"home.file_appeal", "home", "File appeal button", "File an Appeal"});
        keys.add(new String[]{"home.share_feedback", "home", "Share feedback button", "Share Your Feedback"});

        // Login / Verification
        keys.add(new String[]{"login.title", "login", "Login page title", "Verification"});
        keys.add(new String[]{"login.verify_phone", "login", "Verify phone heading", "Verify Phone Number"});
        keys.add(new String[]{"login.mobile_label", "login", "Mobile field label", "Mobile Number"});
        keys.add(new String[]{"login.mobile_placeholder", "login", "Mobile field placeholder", "Enter Mobile Number"});
        keys.add(new String[]{"login.send_otp", "login", "Send OTP button", "Send OTP"});
        keys.add(new String[]{"login.verify_otp", "login", "Verify OTP button", "Verify OTP"});
        keys.add(new String[]{"login.resend_otp", "login", "Resend OTP link", "Resend OTP"});
        keys.add(new String[]{"login.otp_sent", "login", "OTP sent message", "OTP sent to your mobile number"});
        keys.add(new String[]{"login.otp_label", "login", "OTP input label", "Enter OTP"});
        keys.add(new String[]{"login.otp_placeholder", "login", "OTP input placeholder", "Enter 6-digit OTP"});
        keys.add(new String[]{"login.captcha_visual", "login", "CAPTCHA visual label", "Enter the text shown below"});
        keys.add(new String[]{"login.captcha_math", "login", "CAPTCHA math label", "Answer the question"});
        keys.add(new String[]{"login.captcha_placeholder", "login", "CAPTCHA input placeholder", "Enter text"});
        keys.add(new String[]{"login.try_math", "login", "Switch to math CAPTCHA", "Try math question instead"});
        keys.add(new String[]{"login.try_visual", "login", "Switch to visual CAPTCHA", "Try visual CAPTCHA instead"});
        keys.add(new String[]{"login.refresh_captcha", "login", "Refresh CAPTCHA", "Refresh"});
        keys.add(new String[]{"login.email_fallback", "login", "Email fallback label", "Verify via Email instead"});
        keys.add(new String[]{"login.email_placeholder", "login", "Email input placeholder", "Enter registered email"});
        keys.add(new String[]{"login.cooloff_message", "login", "Cooloff message", "Too many attempts. Please wait"});
        keys.add(new String[]{"login.cooloff_remaining", "login", "Cooloff time remaining", "remaining"});

        // Complaint Form
        keys.add(new String[]{"complaint.title", "complaint", "Complaint form title", "Raise a Complaint"});
        keys.add(new String[]{"complaint.step1_title", "complaint", "Step 1 title", "Tell Us About You"});
        keys.add(new String[]{"complaint.step1_desc", "complaint", "Step 1 description", "Share some basic details about yourself to help us contact you regarding your complaint."});
        keys.add(new String[]{"complaint.step2_title", "complaint", "Step 2 title", "Entity Details"});
        keys.add(new String[]{"complaint.step2_desc", "complaint", "Step 2 description", "Tell us about the bank or financial institution you want to complain against."});
        keys.add(new String[]{"complaint.step3_title", "complaint", "Step 3 title", "Share Your Complaint"});
        keys.add(new String[]{"complaint.step3_desc", "complaint", "Step 3 description", "Describe your complaint, actions taken, responses received, and include supporting documents."});
        keys.add(new String[]{"complaint.name", "complaint", "Name field", "Name"});
        keys.add(new String[]{"complaint.email", "complaint", "Email field", "Email (Optional)"});
        keys.add(new String[]{"complaint.pincode", "complaint", "Pincode field", "Pincode"});
        keys.add(new String[]{"complaint.district", "complaint", "District field", "District"});
        keys.add(new String[]{"complaint.state", "complaint", "State field", "State"});
        keys.add(new String[]{"complaint.address", "complaint", "Address field", "Address"});
        keys.add(new String[]{"complaint.entity_type", "complaint", "Entity type field", "Entity Type"});
        keys.add(new String[]{"complaint.entity_name", "complaint", "Entity name field", "Entity Name"});
        keys.add(new String[]{"complaint.branch", "complaint", "Branch field", "Branch (if applicable)"});
        keys.add(new String[]{"complaint.account_number", "complaint", "Account number field", "Account Number (if applicable)"});
        keys.add(new String[]{"complaint.category", "complaint", "Category field", "Complaint Category"});
        keys.add(new String[]{"complaint.subcategory", "complaint", "Sub-category field", "Complaint Sub-Category"});
        keys.add(new String[]{"complaint.facts", "complaint", "Facts field", "Facts of the complaint"});
        keys.add(new String[]{"complaint.attachments", "complaint", "Attachments field", "Attachments"});
        keys.add(new String[]{"complaint.attachment_hint", "complaint", "Attachment hint", "Support formats: PDF, JPG, PNG. Maximum size: 5MB"});
        keys.add(new String[]{"complaint.complainant_category", "complaint", "Complainant category field", "Complainant Category"});
        keys.add(new String[]{"complaint.individual", "complaint", "Individual option", "Individual"});
        keys.add(new String[]{"complaint.business", "complaint", "Business option", "Business"});
        keys.add(new String[]{"complaint.other", "complaint", "Other option", "Other"});
        keys.add(new String[]{"complaint.sub_judice", "complaint", "Sub-judice question", "Is your complaint sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority?"});
        keys.add(new String[]{"complaint.through_advocate", "complaint", "Through advocate question", "Is your complaint made through an advocate (unless you are yourself an advocate)?"});
        keys.add(new String[]{"complaint.already_with_ombudsman", "complaint", "Already with ombudsman", "Has your complaint already been dealt with or is under process on the same ground with the Ombudsman?"});
        keys.add(new String[]{"complaint.regulated_entity_staff", "complaint", "Regulated entity staff question", "Is complaint from the staff of a regulated entity and involves employer employee relationship?"});
        keys.add(new String[]{"complaint.authorize_representative", "complaint", "Authorize representative", "If you want to authorize a representative to appear and make submission on your behalf before the Ombudsman, please select 'Yes' and furnish the details"});
        keys.add(new String[]{"complaint.submitted_success", "complaint", "Complaint submitted message", "Your complaint has been submitted successfully"});
        keys.add(new String[]{"complaint.complaint_number", "complaint", "Complaint number label", "Complaint Number"});

        // Pre-filing modal
        keys.add(new String[]{"prefiling.title", "complaint", "Pre-filing modal title", "Before Filing a Complaint"});
        keys.add(new String[]{"prefiling.subtitle", "complaint", "Pre-filing subtitle", "SELECT WHICHEVER IS APPLICABLE"});
        keys.add(new String[]{"prefiling.not_contacted", "complaint", "Not contacted option", "I have not contacted my bank or financial institution"});
        keys.add(new String[]{"prefiling.not_contacted_desc", "complaint", "Not contacted description", "Select this option if you have not filed a complaint with your bank or financial institution yet."});
        keys.add(new String[]{"prefiling.already_filed", "complaint", "Already filed option", "I have filed a complaint with bank or financial institution"});
        keys.add(new String[]{"prefiling.already_filed_desc", "complaint", "Already filed description", "Select this option if you are not satisfied with the reply provided by your bank or financial institute or if they have not provided a response to your complaint in 30 days."});
        keys.add(new String[]{"prefiling.complaint_date", "complaint", "Complaint date field", "When did you first file the complaint with your bank or financial institution?"});
        keys.add(new String[]{"prefiling.received_reply", "complaint", "Received reply field", "Have you received any reply from your bank or financial institution?"});

        // Complaint History / Status
        keys.add(new String[]{"history.title", "history", "History page title", "Complaint History"});
        keys.add(new String[]{"history.status", "history", "Status column", "Status"});
        keys.add(new String[]{"history.date_filed", "history", "Date filed column", "Date Filed"});
        keys.add(new String[]{"history.subject", "history", "Subject column", "Subject"});
        keys.add(new String[]{"history.no_complaints", "history", "No complaints message", "No complaints found"});
        keys.add(new String[]{"history.track_complaint", "history", "Track complaint", "Track Complaint"});
        keys.add(new String[]{"history.view_details", "history", "View details button", "View Details"});

        // Status labels
        keys.add(new String[]{"status.pending", "status", "Pending status", "Pending"});
        keys.add(new String[]{"status.in_progress", "status", "In progress status", "In Progress"});
        keys.add(new String[]{"status.resolved", "status", "Resolved status", "Resolved"});
        keys.add(new String[]{"status.closed", "status", "Closed status", "Closed"});
        keys.add(new String[]{"status.escalated", "status", "Escalated status", "Escalated"});
        keys.add(new String[]{"status.under_review", "status", "Under review status", "Under Review"});
        keys.add(new String[]{"status.assigned", "status", "Assigned status", "Assigned"});

        // Footer / Info
        keys.add(new String[]{"footer.rbi_ombudsman", "footer", "RBI Ombudsman text", "Reserve Bank of India - Integrated Ombudsman Scheme"});
        keys.add(new String[]{"footer.helpline", "footer", "Helpline text", "Toll-Free Helpline"});
        keys.add(new String[]{"footer.privacy_policy", "footer", "Privacy policy link", "Privacy Policy"});
        keys.add(new String[]{"footer.terms", "footer", "Terms link", "Terms & Conditions"});
        keys.add(new String[]{"footer.disclaimer", "footer", "Disclaimer link", "Disclaimer"});

        // Error messages
        keys.add(new String[]{"error.invalid_mobile", "error", "Invalid mobile", "Please enter a valid 10-digit mobile number"});
        keys.add(new String[]{"error.invalid_otp", "error", "Invalid OTP", "Invalid OTP. Please try again"});
        keys.add(new String[]{"error.otp_expired", "error", "OTP expired", "OTP has expired. Please request a new one"});
        keys.add(new String[]{"error.max_attempts", "error", "Max attempts reached", "Maximum attempts reached. Please try again later"});
        keys.add(new String[]{"error.server_error", "error", "Server error", "Server error. Please try again later"});
        keys.add(new String[]{"error.network_error", "error", "Network error", "Network error. Please check your connection"});
        keys.add(new String[]{"error.captcha_failed", "error", "CAPTCHA failed", "CAPTCHA verification failed. Please try again"});
        keys.add(new String[]{"error.session_expired", "error", "Session expired", "Your session has expired. Please log in again"});

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

        seedHindiTranslations();
        seedBengaliTranslations();
        seedMarathiTranslations();
        seedTeluguTranslations();
        seedTamilTranslations();
        seedGujaratiTranslations();
        seedUrduTranslations();
        seedKannadaTranslations();
        seedMalayalamTranslations();
    }

    private void seedHindiTranslations() {
        Map<String, String> hi = new LinkedHashMap<>();
        hi.put("common.app_title", "शिकायत प्रबंधन प्रणाली");
        hi.put("common.home", "होम");
        hi.put("common.back", "वापस");
        hi.put("common.next", "अगला");
        hi.put("common.submit", "जमा करें");
        hi.put("common.cancel", "रद्द करें");
        hi.put("common.save", "सहेजें");
        hi.put("common.close", "बंद करें");
        hi.put("common.loading", "लोड हो रहा है...");
        hi.put("common.error", "एक त्रुटि हुई");
        hi.put("common.success", "सफल");
        hi.put("common.required_field", "यह फ़ील्ड आवश्यक है");
        hi.put("common.yes", "हाँ");
        hi.put("common.no", "नहीं");
        hi.put("common.select_option", "एक विकल्प चुनें");
        hi.put("common.search", "खोजें");
        hi.put("common.logout", "लॉग आउट");
        hi.put("common.language", "भाषा");
        hi.put("nav.about_us", "हमारे बारे में");
        hi.put("nav.public_awareness", "जन जागरूकता");
        hi.put("nav.my_complaints", "मेरी शिकायतें");
        hi.put("nav.login", "लॉगिन");
        hi.put("home.hero_title", "शिकायत समाधान को");
        hi.put("home.hero_highlight", "आसान बनाना");
        hi.put("home.hero_subtitle", "भारतीय रिज़र्व बैंक का विश्वसनीय शिकायत समाधान मंच - विनियमित संस्थाओं के खिलाफ शिकायत दर्ज करें और ट्रैक करें।");
        hi.put("home.file_complaint", "शिकायत दर्ज करें");
        hi.put("home.already_filed", "पहले से शिकायत दर्ज है?");
        hi.put("home.track_withdraw", "शिकायत ट्रैक या वापस लें");
        hi.put("home.file_appeal", "अपील दायर करें");
        hi.put("home.share_feedback", "अपनी प्रतिक्रिया दें");
        hi.put("login.title", "सत्यापन");
        hi.put("login.verify_phone", "फ़ोन नंबर सत्यापित करें");
        hi.put("login.mobile_label", "मोबाइल नंबर");
        hi.put("login.mobile_placeholder", "मोबाइल नंबर दर्ज करें");
        hi.put("login.send_otp", "OTP भेजें");
        hi.put("login.verify_otp", "OTP सत्यापित करें");
        hi.put("login.resend_otp", "OTP पुनः भेजें");
        hi.put("login.otp_sent", "आपके मोबाइल नंबर पर OTP भेजा गया");
        hi.put("login.otp_label", "OTP दर्ज करें");
        hi.put("login.otp_placeholder", "6 अंकों का OTP दर्ज करें");
        hi.put("login.captcha_visual", "नीचे दिखाया गया पाठ दर्ज करें");
        hi.put("login.captcha_math", "प्रश्न का उत्तर दें");
        hi.put("login.cooloff_message", "बहुत अधिक प्रयास। कृपया प्रतीक्षा करें");
        hi.put("login.cooloff_remaining", "शेष");
        hi.put("complaint.title", "शिकायत दर्ज करें");
        hi.put("complaint.step1_title", "अपने बारे में बताएं");
        hi.put("complaint.step1_desc", "आपकी शिकायत के संबंध में संपर्क करने में सहायता के लिए कुछ बुनियादी विवरण साझा करें।");
        hi.put("complaint.step2_title", "संस्था विवरण");
        hi.put("complaint.step2_desc", "जिस बैंक या वित्तीय संस्था के खिलाफ शिकायत करना चाहते हैं उसके बारे में बताएं।");
        hi.put("complaint.step3_title", "अपनी शिकायत साझा करें");
        hi.put("complaint.step3_desc", "अपनी शिकायत, की गई कार्रवाई, प्राप्त उत्तर और सहायक दस्तावेज़ शामिल करें।");
        hi.put("complaint.name", "नाम");
        hi.put("complaint.email", "ईमेल (वैकल्पिक)");
        hi.put("complaint.pincode", "पिनकोड");
        hi.put("complaint.district", "जिला");
        hi.put("complaint.state", "राज्य");
        hi.put("complaint.address", "पता");
        hi.put("complaint.entity_type", "संस्था प्रकार");
        hi.put("complaint.entity_name", "संस्था का नाम");
        hi.put("complaint.category", "शिकायत श्रेणी");
        hi.put("complaint.facts", "शिकायत का विवरण");
        hi.put("complaint.attachments", "संलग्नक");
        hi.put("complaint.submitted_success", "आपकी शिकायत सफलतापूर्वक जमा हो गई है");
        hi.put("complaint.complaint_number", "शिकायत संख्या");
        hi.put("history.title", "शिकायत इतिहास");
        hi.put("history.status", "स्थिति");
        hi.put("history.date_filed", "दायर करने की तिथि");
        hi.put("history.subject", "विषय");
        hi.put("history.no_complaints", "कोई शिकायत नहीं मिली");
        hi.put("status.pending", "लंबित");
        hi.put("status.in_progress", "प्रगति में");
        hi.put("status.resolved", "हल किया गया");
        hi.put("status.closed", "बंद");
        hi.put("status.escalated", "उच्चस्तरीय");
        hi.put("footer.rbi_ombudsman", "भारतीय रिज़र्व बैंक - एकीकृत लोकपाल योजना");
        hi.put("footer.helpline", "टोल-फ्री हेल्पलाइन");
        hi.put("error.invalid_mobile", "कृपया एक वैध 10 अंकों का मोबाइल नंबर दर्ज करें");
        hi.put("error.invalid_otp", "अमान्य OTP। कृपया पुनः प्रयास करें");
        hi.put("error.otp_expired", "OTP की समय सीमा समाप्त हो गई। कृपया नया अनुरोध करें");
        hi.put("error.session_expired", "आपका सत्र समाप्त हो गया है। कृपया पुनः लॉगिन करें");
        saveTranslations(hi, "hi");
    }

    private void seedBengaliTranslations() {
        Map<String, String> bn = new LinkedHashMap<>();
        bn.put("common.app_title", "\u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u09AC\u09CD\u09AF\u09AC\u09B8\u09CD\u09A5\u09BE\u09AA\u09A8\u09BE \u09B8\u09BF\u09B8\u09CD\u099F\u09C7\u09AE");
        bn.put("common.home", "\u09B9\u09CB\u09AE");
        bn.put("common.back", "\u09AA\u09BF\u099B\u09A8\u09C7");
        bn.put("common.next", "\u09AA\u09B0\u09AC\u09B0\u09CD\u09A4\u09C0");
        bn.put("common.submit", "\u099C\u09AE\u09BE \u09A6\u09BF\u09A8");
        bn.put("common.cancel", "\u09AC\u09BE\u09A4\u09BF\u09B2");
        bn.put("common.loading", "\u09B2\u09CB\u09A1 \u09B9\u099A\u09CD\u099B\u09C7...");
        bn.put("common.yes", "\u09B9\u09CD\u09AF\u09BE\u0981");
        bn.put("common.no", "\u09A8\u09BE");
        bn.put("common.language", "\u09AD\u09BE\u09B7\u09BE");
        bn.put("nav.about_us", "\u0986\u09AE\u09BE\u09A6\u09C7\u09B0 \u09B8\u09AE\u09CD\u09AA\u09B0\u09CD\u0995\u09C7");
        bn.put("nav.public_awareness", "\u099C\u09A8\u09B8\u099A\u09C7\u09A4\u09A8\u09A4\u09BE");
        bn.put("nav.my_complaints", "\u0986\u09AE\u09BE\u09B0 \u0985\u09AD\u09BF\u09AF\u09CB\u0997");
        bn.put("nav.login", "\u09B2\u0997\u0987\u09A8");
        bn.put("home.hero_title", "\u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u09B8\u09AE\u09BE\u09A7\u09BE\u09A8");
        bn.put("home.hero_highlight", "\u09B8\u09B9\u099C \u0995\u09B0\u09BE");
        bn.put("home.hero_subtitle", "\u09AD\u09BE\u09B0\u09A4\u09C0\u09AF\u09BC \u09B0\u09BF\u099C\u09BE\u09B0\u09CD\u09AD \u09AC\u09CD\u09AF\u09BE\u0982\u0995\u09C7\u09B0 \u09AC\u09BF\u09B6\u09CD\u09AC\u09B8\u09CD\u09A4 \u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u09B8\u09AE\u09BE\u09A7\u09BE\u09A8 \u09AE\u099E\u09CD\u099A - \u09A8\u09BF\u09AF\u09BC\u09A8\u09CD\u09A4\u09CD\u09B0\u09BF\u09A4 \u09B8\u0982\u09B8\u09CD\u09A5\u09BE\u0997\u09C1\u09B2\u09BF\u09B0 \u09AC\u09BF\u09B0\u09C1\u09A6\u09CD\u09A7\u09C7 \u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u09A6\u09BE\u09AF\u09BC\u09C7\u09B0 \u098F\u09AC\u0982 \u099F\u09CD\u09B0\u09CD\u09AF\u09BE\u0995 \u0995\u09B0\u09C1\u09A8\u0964");
        bn.put("home.file_complaint", "\u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u09A6\u09BE\u09AF\u09BC\u09C7\u09B0 \u0995\u09B0\u09C1\u09A8");
        bn.put("home.already_filed", "\u0987\u09A4\u09BF\u09AE\u09A7\u09CD\u09AF\u09C7 \u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u09A6\u09BE\u09AF\u09BC\u09C7\u09B0 \u0995\u09B0\u09C7\u099B\u09C7\u09A8?");
        bn.put("home.track_withdraw", "\u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u099F\u09CD\u09B0\u09CD\u09AF\u09BE\u0995 \u09AC\u09BE \u09AA\u09CD\u09B0\u09A4\u09CD\u09AF\u09BE\u09B9\u09BE\u09B0");
        bn.put("home.file_appeal", "\u0986\u09AA\u09BF\u09B2 \u09A6\u09BE\u09AF\u09BC\u09C7\u09B0 \u0995\u09B0\u09C1\u09A8");
        bn.put("home.share_feedback", "\u0986\u09AA\u09A8\u09BE\u09B0 \u09AE\u09A4\u09BE\u09AE\u09A4 \u09A6\u09BF\u09A8");
        bn.put("login.title", "\u09AF\u09BE\u099A\u09BE\u0987\u0995\u09B0\u09A3");
        bn.put("login.verify_phone", "\u09AB\u09CB\u09A8 \u09A8\u09AE\u09CD\u09AC\u09B0 \u09AF\u09BE\u099A\u09BE\u0987 \u0995\u09B0\u09C1\u09A8");
        bn.put("login.mobile_label", "\u09AE\u09CB\u09AC\u09BE\u0987\u09B2 \u09A8\u09AE\u09CD\u09AC\u09B0");
        bn.put("login.send_otp", "OTP \u09AA\u09BE\u09A0\u09BE\u09A8");
        bn.put("login.verify_otp", "OTP \u09AF\u09BE\u099A\u09BE\u0987 \u0995\u09B0\u09C1\u09A8");
        bn.put("complaint.title", "\u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u09A6\u09BE\u09AF\u09BC\u09C7\u09B0 \u0995\u09B0\u09C1\u09A8");
        bn.put("complaint.step1_title", "\u0986\u09AA\u09A8\u09BE\u09B0 \u09B8\u09AE\u09CD\u09AA\u09B0\u09CD\u0995\u09C7 \u09AC\u09B2\u09C1\u09A8");
        bn.put("complaint.step2_title", "\u09AA\u09CD\u09B0\u09A4\u09BF\u09B7\u09CD\u09A0\u09BE\u09A8\u09C7\u09B0 \u09AC\u09BF\u09AC\u09B0\u09A3");
        bn.put("complaint.step3_title", "\u0986\u09AA\u09A8\u09BE\u09B0 \u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u099C\u09BE\u09A8\u09BE\u09A8");
        bn.put("complaint.name", "\u09A8\u09BE\u09AE");
        bn.put("complaint.email", "\u0987\u09AE\u09C7\u0987\u09B2 (\u0990\u099A\u09CD\u099B\u09BF\u0995)");
        bn.put("complaint.pincode", "\u09AA\u09BF\u09A8\u0995\u09CB\u09A1");
        bn.put("complaint.district", "\u099C\u09C7\u09B2\u09BE");
        bn.put("complaint.state", "\u09B0\u09BE\u099C\u09CD\u09AF");
        bn.put("complaint.address", "\u09A0\u09BF\u0995\u09BE\u09A8\u09BE");
        bn.put("complaint.submitted_success", "\u0986\u09AA\u09A8\u09BE\u09B0 \u0985\u09AD\u09BF\u09AF\u09CB\u0997 \u09B8\u09AB\u09B2\u09AD\u09BE\u09AC\u09C7 \u099C\u09AE\u09BE \u09B9\u09AF\u09BC\u09C7\u099B\u09C7");
        bn.put("status.pending", "\u09AE\u09C1\u09B2\u09A4\u09C1\u09AC\u09BF");
        bn.put("status.in_progress", "\u09AA\u09CD\u09B0\u0995\u09CD\u09B0\u09BF\u09AF\u09BC\u09BE\u09A7\u09C0\u09A8");
        bn.put("status.resolved", "\u09B8\u09AE\u09BE\u09A7\u09BE\u09A8 \u09B9\u09AF\u09BC\u09C7\u099B\u09C7");
        bn.put("status.closed", "\u09AC\u09A8\u09CD\u09A7");
        bn.put("footer.rbi_ombudsman", "\u09AD\u09BE\u09B0\u09A4\u09C0\u09AF\u09BC \u09B0\u09BF\u099C\u09BE\u09B0\u09CD\u09AD \u09AC\u09CD\u09AF\u09BE\u0982\u0995 - \u09B8\u09AE\u09A8\u09CD\u09AC\u09BF\u09A4 \u09B2\u09CB\u0995\u09AA\u09BE\u09B2 \u09AA\u09CD\u09B0\u0995\u09B2\u09CD\u09AA");
        saveTranslations(bn, "bn");
    }

    private void seedMarathiTranslations() {
        Map<String, String> mr = new LinkedHashMap<>();
        mr.put("common.app_title", "\u0924\u0915\u094D\u0930\u093E\u0930 \u0935\u094D\u092F\u0935\u0938\u094D\u0925\u093E\u092A\u0928\u093E \u092A\u094D\u0930\u0923\u093E\u0932\u0940");
        mr.put("common.home", "\u092E\u0941\u0916\u094D\u092F\u092A\u0943\u0937\u094D\u0920");
        mr.put("common.back", "\u092E\u093E\u0917\u0947");
        mr.put("common.next", "\u092A\u0941\u0922\u0947");
        mr.put("common.submit", "\u0938\u093E\u0926\u0930 \u0915\u0930\u093E");
        mr.put("common.cancel", "\u0930\u0926\u094D\u0926 \u0915\u0930\u093E");
        mr.put("common.loading", "\u0932\u094B\u0921 \u0939\u094B\u0924 \u0906\u0939\u0947...");
        mr.put("common.yes", "\u0939\u094B\u092F");
        mr.put("common.no", "\u0928\u093E\u0939\u0940");
        mr.put("common.language", "\u092D\u093E\u0937\u093E");
        mr.put("nav.about_us", "\u0906\u092E\u091A\u094D\u092F\u093E\u092C\u0926\u094D\u0926\u0932");
        mr.put("nav.public_awareness", "\u091C\u0928\u091C\u093E\u0917\u0943\u0924\u0940");
        mr.put("nav.my_complaints", "\u092E\u093E\u091D\u094D\u092F\u093E \u0924\u0915\u094D\u0930\u093E\u0930\u0940");
        mr.put("nav.login", "\u0932\u0949\u0917\u093F\u0928");
        mr.put("home.hero_title", "\u0924\u0915\u094D\u0930\u093E\u0930 \u0928\u093F\u0935\u093E\u0930\u0923");
        mr.put("home.hero_highlight", "\u0938\u094B\u092A\u0947 \u0915\u0930\u0923\u0947");
        mr.put("home.hero_subtitle", "\u092D\u093E\u0930\u0924\u0940\u092F \u0930\u093F\u091D\u0930\u094D\u0935\u094D\u0939 \u092C\u0901\u0915\u0947\u091A\u0947 \u0935\u093F\u0936\u094D\u0935\u093E\u0938\u093E\u0930\u094D\u0939 \u0924\u0915\u094D\u0930\u093E\u0930 \u0928\u093F\u0935\u093E\u0930\u0923 \u0935\u094D\u092F\u093E\u0938\u092A\u0940\u0920 - \u0928\u093F\u092F\u093E\u092E\u093F\u0924 \u0938\u0902\u0938\u094D\u0925\u093E\u0902\u0935\u093F\u0930\u0941\u0926\u094D\u0927 \u0924\u0915\u094D\u0930\u093E\u0930 \u0926\u093E\u0916\u0932 \u0915\u0930\u093E \u0906\u0923\u093F \u091F\u094D\u0930\u0945\u0915 \u0915\u0930\u093E.");
        mr.put("home.file_complaint", "\u0924\u0915\u094D\u0930\u093E\u0930 \u0926\u093E\u0916\u0932 \u0915\u0930\u093E");
        mr.put("home.already_filed", "\u0906\u0927\u0940\u091A \u0924\u0915\u094D\u0930\u093E\u0930 \u0926\u093E\u0916\u0932 \u0915\u0947\u0932\u0940?");
        mr.put("home.track_withdraw", "\u0924\u0915\u094D\u0930\u093E\u0930 \u091F\u094D\u0930\u0945\u0915 \u0915\u093F\u0902\u0935\u093E \u092E\u093E\u0917\u0947 \u0918\u094D\u092F\u093E");
        mr.put("home.file_appeal", "\u0905\u092A\u0940\u0932 \u0926\u093E\u0916\u0932 \u0915\u0930\u093E");
        mr.put("home.share_feedback", "\u0906\u092A\u0932\u093E \u0905\u092D\u093F\u092A\u094D\u0930\u093E\u092F \u0926\u094D\u092F\u093E");
        mr.put("login.title", "\u092A\u0921\u0924\u093E\u0933\u0923\u0940");
        mr.put("login.verify_phone", "\u092B\u094B\u0928 \u0928\u0902\u092C\u0930 \u092A\u0921\u0924\u093E\u0933\u093E");
        mr.put("login.mobile_label", "\u092E\u094B\u092C\u093E\u0907\u0932 \u0928\u0902\u092C\u0930");
        mr.put("login.send_otp", "OTP \u092A\u093E\u0920\u0935\u093E");
        mr.put("login.verify_otp", "OTP \u092A\u0921\u0924\u093E\u0933\u093E");
        mr.put("complaint.title", "\u0924\u0915\u094D\u0930\u093E\u0930 \u0928\u094B\u0902\u0926\u0935\u093E");
        mr.put("complaint.step1_title", "\u0938\u094D\u0935\u0924\u0903\u092C\u0926\u094D\u0926\u0932 \u0938\u093E\u0902\u0917\u093E");
        mr.put("complaint.step2_title", "\u0938\u0902\u0938\u094D\u0925\u0947\u091A\u0947 \u0924\u092A\u0936\u0940\u0932");
        mr.put("complaint.step3_title", "\u0924\u0941\u092E\u091A\u0940 \u0924\u0915\u094D\u0930\u093E\u0930 \u0938\u093E\u092E\u093E\u092F\u093F\u0915 \u0915\u0930\u093E");
        mr.put("complaint.name", "\u0928\u093E\u0935");
        mr.put("complaint.email", "\u0908\u092E\u0947\u0932 (\u0910\u091A\u094D\u091B\u093F\u0915)");
        mr.put("complaint.pincode", "\u092A\u093F\u0928\u0915\u094B\u0921");
        mr.put("complaint.district", "\u091C\u093F\u0932\u094D\u0939\u093E");
        mr.put("complaint.state", "\u0930\u093E\u091C\u094D\u092F");
        mr.put("complaint.address", "\u092A\u0924\u094D\u0924\u093E");
        mr.put("complaint.submitted_success", "\u0924\u0941\u092E\u091A\u0940 \u0924\u0915\u094D\u0930\u093E\u0930 \u092F\u0936\u0938\u094D\u0935\u0940\u092A\u0923\u0947 \u0938\u093E\u0926\u0930 \u091D\u093E\u0932\u0940 \u0906\u0939\u0947");
        mr.put("status.pending", "\u092A\u094D\u0930\u0932\u0902\u092C\u093F\u0924");
        mr.put("status.in_progress", "\u092A\u094D\u0930\u0917\u0924\u0940\u0924");
        mr.put("status.resolved", "\u0928\u093F\u0930\u093E\u0915\u0930\u0923 \u091D\u093E\u0932\u0947");
        mr.put("status.closed", "\u092C\u0902\u0926");
        mr.put("footer.rbi_ombudsman", "\u092D\u093E\u0930\u0924\u0940\u092F \u0930\u093F\u091D\u0930\u094D\u0935\u094D\u0939 \u092C\u0901\u0915 - \u090F\u0915\u093E\u0924\u094D\u092E\u093F\u0915 \u0932\u094B\u0915\u092A\u093E\u0932 \u092F\u094B\u091C\u0928\u093E");
        saveTranslations(mr, "mr");
    }

    private void seedTeluguTranslations() {
        Map<String, String> te = new LinkedHashMap<>();
        te.put("common.app_title", "ఫిర్యాదు నిర్వహణ వ్యవస్థ");
        te.put("common.home", "హోమ్");
        te.put("common.back", "వెనుకకు");
        te.put("common.next", "తదుపరి");
        te.put("common.submit", "సమర్పించండి");
        te.put("common.cancel", "రద్దు");
        te.put("common.loading", "లోడ్ అవుతోంది...");
        te.put("common.yes", "అవును");
        te.put("common.no", "కాదు");
        te.put("common.language", "భాష");
        te.put("login.title", "ధృవీకరణ");
        te.put("login.verify_phone", "ఫోన్ నంబర్ ధృవీకరించండి");
        te.put("login.mobile_label", "మొబైల్ నంబర్");
        te.put("login.send_otp", "OTP పంపండి");
        te.put("complaint.title", "ఫిర్యాదు దాఖలు చేయండి");
        te.put("complaint.step1_title", "మీ గురించి చెప్పండి");
        te.put("complaint.step2_title", "సంస్థ వివరాలు");
        te.put("complaint.step3_title", "మీ ఫిర్యాదు పంచుకోండి");
        te.put("complaint.name", "పేరు");
        te.put("complaint.email", "ఇమెయిల్ (ఐచ్ఛికం)");
        te.put("complaint.submitted_success", "మీ ఫిర్యాదు విజయవంతంగా సమర్పించబడింది");
        te.put("status.pending", "పెండింగ్");
        te.put("status.in_progress", "ప్రగతిలో");
        te.put("status.resolved", "పరిష్కరించబడింది");
        te.put("footer.rbi_ombudsman", "భారతీయ రిజర్వ్ బ్యాంక్ - సమగ్ర లోకపాల్ పథకం");
        saveTranslations(te, "te");
    }

    private void seedTamilTranslations() {
        Map<String, String> ta = new LinkedHashMap<>();
        ta.put("common.app_title", "புகார் மேலாண்மை அமைப்பு");
        ta.put("common.home", "முகப்பு");
        ta.put("common.back", "பின்செல்");
        ta.put("common.next", "அடுத்து");
        ta.put("common.submit", "சமர்ப்பிக்கவும்");
        ta.put("common.cancel", "ரத்து");
        ta.put("common.loading", "ஏற்றுகிறது...");
        ta.put("common.yes", "ஆம்");
        ta.put("common.no", "இல்லை");
        ta.put("common.language", "மொழி");
        ta.put("login.title", "சரிபார்ப்பு");
        ta.put("login.verify_phone", "தொலைபேசி எண்ணை சரிபார்க்கவும்");
        ta.put("login.mobile_label", "மொபைல் எண்");
        ta.put("login.send_otp", "OTP அனுப்பு");
        ta.put("complaint.title", "புகார் பதிவு செய்யவும்");
        ta.put("complaint.step1_title", "உங்களைப் பற்றி சொல்லுங்கள்");
        ta.put("complaint.step2_title", "நிறுவன விவரங்கள்");
        ta.put("complaint.step3_title", "உங்கள் புகாரை பகிரவும்");
        ta.put("complaint.name", "பெயர்");
        ta.put("complaint.email", "மின்னஞ்சல் (விருப்பமானது)");
        ta.put("complaint.submitted_success", "உங்கள் புகார் வெற்றிகரமாக சமர்ப்பிக்கப்பட்டது");
        ta.put("status.pending", "நிலுவையில்");
        ta.put("status.in_progress", "நடைபெறுகிறது");
        ta.put("status.resolved", "தீர்க்கப்பட்டது");
        ta.put("footer.rbi_ombudsman", "இந்திய ரிசர்வ் வங்கி - ஒருங்கிணைந்த குறைதீர்ப்பாளர் திட்டம்");
        saveTranslations(ta, "ta");
    }

    private void seedGujaratiTranslations() {
        Map<String, String> gu = new LinkedHashMap<>();
        gu.put("common.app_title", "ફરિયાદ વ્યવસ્થાપન સિસ્ટમ");
        gu.put("common.home", "હોમ");
        gu.put("common.back", "પાછળ");
        gu.put("common.next", "આગળ");
        gu.put("common.submit", "સબમિટ કરો");
        gu.put("common.cancel", "રદ કરો");
        gu.put("common.loading", "લોડ થઈ રહ્યું છે...");
        gu.put("common.yes", "હા");
        gu.put("common.no", "ના");
        gu.put("common.language", "ભાષા");
        gu.put("login.title", "ચકાસણી");
        gu.put("login.verify_phone", "ફોન નંબર ચકાસો");
        gu.put("login.mobile_label", "મોબાઇલ નંબર");
        gu.put("login.send_otp", "OTP મોકલો");
        gu.put("complaint.title", "ફરિયાદ નોંધાવો");
        gu.put("complaint.step1_title", "તમારા વિશે જણાવો");
        gu.put("complaint.step2_title", "સંસ્થાની વિગતો");
        gu.put("complaint.step3_title", "તમારી ફરિયાદ શેર કરો");
        gu.put("complaint.name", "નામ");
        gu.put("complaint.email", "ઇમેઇલ (વૈકલ્પિક)");
        gu.put("complaint.submitted_success", "તમારી ફરિયાદ સફળતાપૂર્વક સબમિટ થઈ ગઈ છે");
        gu.put("status.pending", "બાકી");
        gu.put("status.in_progress", "પ્રક્રિયામાં");
        gu.put("status.resolved", "ઉકેલાયેલ");
        gu.put("footer.rbi_ombudsman", "ભારતીય રિઝર્વ બેંક - સંકલિત લોકપાલ યોજના");
        saveTranslations(gu, "gu");
    }

    private void seedUrduTranslations() {
        Map<String, String> ur = new LinkedHashMap<>();
        ur.put("common.app_title", "شکایت مینجمنٹ سسٹم");
        ur.put("common.home", "ہوم");
        ur.put("common.back", "واپس");
        ur.put("common.next", "اگلا");
        ur.put("common.submit", "جمع کرائیں");
        ur.put("common.cancel", "منسوخ");
        ur.put("common.loading", "لوڈ ہو رہا ہے...");
        ur.put("common.yes", "ہاں");
        ur.put("common.no", "نہیں");
        ur.put("common.language", "زبان");
        ur.put("login.title", "تصدیق");
        ur.put("login.verify_phone", "فون نمبر کی تصدیق کریں");
        ur.put("login.mobile_label", "موبائل نمبر");
        ur.put("login.send_otp", "OTP بھیجیں");
        ur.put("complaint.title", "شکایت درج کریں");
        ur.put("complaint.step1_title", "اپنے بارے میں بتائیں");
        ur.put("complaint.step2_title", "ادارے کی تفصیلات");
        ur.put("complaint.step3_title", "اپنی شکایت شیئر کریں");
        ur.put("complaint.name", "نام");
        ur.put("complaint.email", "ای میل (اختیاری)");
        ur.put("complaint.submitted_success", "آپ کی شکایت کامیابی سے جمع ہو گئی ہے");
        ur.put("status.pending", "زیر التوا");
        ur.put("status.in_progress", "جاری");
        ur.put("status.resolved", "حل ہو گیا");
        ur.put("footer.rbi_ombudsman", "ریزرو بینک آف انڈیا - مربوط لوک پال اسکیم");
        saveTranslations(ur, "ur");
    }

    private void seedKannadaTranslations() {
        Map<String, String> kn = new LinkedHashMap<>();
        kn.put("common.app_title", "ದೂರು ನಿರ್ವಹಣಾ ವ್ಯವಸ್ಥೆ");
        kn.put("common.home", "ಮುಖಪುಟ");
        kn.put("common.back", "ಹಿಂದೆ");
        kn.put("common.next", "ಮುಂದೆ");
        kn.put("common.submit", "ಸಲ್ಲಿಸಿ");
        kn.put("common.cancel", "ರದ್ದು");
        kn.put("common.loading", "ಲೋಡ್ ಆಗುತ್ತಿದೆ...");
        kn.put("common.yes", "ಹೌದು");
        kn.put("common.no", "ಇಲ್ಲ");
        kn.put("common.language", "ಭಾಷೆ");
        kn.put("login.title", "ಪರಿಶೀಲನೆ");
        kn.put("login.verify_phone", "ಫೋನ್ ಸಂಖ್ಯೆ ಪರಿಶೀಲಿಸಿ");
        kn.put("login.mobile_label", "ಮೊಬೈಲ್ ಸಂಖ್ಯೆ");
        kn.put("login.send_otp", "OTP ಕಳುಹಿಸಿ");
        kn.put("complaint.title", "ದೂರು ದಾಖಲಿಸಿ");
        kn.put("complaint.step1_title", "ನಿಮ್ಮ ಬಗ್ಗೆ ಹೇಳಿ");
        kn.put("complaint.step2_title", "ಸಂಸ್ಥೆಯ ವಿವರಗಳು");
        kn.put("complaint.step3_title", "ನಿಮ್ಮ ದೂರು ಹಂಚಿಕೊಳ್ಳಿ");
        kn.put("complaint.name", "ಹೆಸರು");
        kn.put("complaint.email", "ಇಮೇಲ್ (ಐಚ್ಛಿಕ)");
        kn.put("complaint.submitted_success", "ನಿಮ್ಮ ದೂರು ಯಶಸ್ವಿಯಾಗಿ ಸಲ್ಲಿಸಲಾಗಿದೆ");
        kn.put("status.pending", "ಬಾಕಿ");
        kn.put("status.in_progress", "ಪ್ರಗತಿಯಲ್ಲಿ");
        kn.put("status.resolved", "ಪರಿಹರಿಸಲಾಗಿದೆ");
        kn.put("footer.rbi_ombudsman", "ಭಾರತೀಯ ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್ - ಸಮಗ್ರ ಲೋಕಪಾಲ ಯೋಜನೆ");
        saveTranslations(kn, "kn");
    }

    private void seedMalayalamTranslations() {
        Map<String, String> ml = new LinkedHashMap<>();
        ml.put("common.app_title", "പരാതി മാനേജ്‌മെന്റ് സിസ്റ്റം");
        ml.put("common.home", "ഹോം");
        ml.put("common.back", "പിന്നിലേക്ക്");
        ml.put("common.next", "അടുത്തത്");
        ml.put("common.submit", "സമർപ്പിക്കുക");
        ml.put("common.cancel", "റദ്ദാക്കുക");
        ml.put("common.loading", "ലോഡ് ചെയ്യുന്നു...");
        ml.put("common.yes", "അതെ");
        ml.put("common.no", "ഇല്ല");
        ml.put("common.language", "ഭാഷ");
        ml.put("login.title", "പരിശോധന");
        ml.put("login.verify_phone", "ഫോൺ നമ്പർ പരിശോധിക്കുക");
        ml.put("login.mobile_label", "മൊബൈൽ നമ്പർ");
        ml.put("login.send_otp", "OTP അയയ്ക്കുക");
        ml.put("complaint.title", "പരാതി സമർപ്പിക്കുക");
        ml.put("complaint.step1_title", "നിങ്ങളെക്കുറിച്ച് പറയൂ");
        ml.put("complaint.step2_title", "സ്ഥാപന വിശദാംശങ്ങൾ");
        ml.put("complaint.step3_title", "നിങ്ങളുടെ പരാതി പങ്കിടുക");
        ml.put("complaint.name", "പേര്");
        ml.put("complaint.email", "ഇമെയിൽ (ഓപ്ഷണൽ)");
        ml.put("complaint.submitted_success", "നിങ്ങളുടെ പരാതി വിജയകരമായി സമർപ്പിച്ചു");
        ml.put("status.pending", "തീർപ്പുകൽപ്പിക്കാത്ത");
        ml.put("status.in_progress", "പുരോഗതിയിൽ");
        ml.put("status.resolved", "പരിഹരിച്ചു");
        ml.put("footer.rbi_ombudsman", "റിസർവ് ബാങ്ക് ഓഫ് ഇന്ത്യ - സംയോജിത ലോക്‌പാൽ പദ്ധതി");
        saveTranslations(ml, "ml");
    }

    private void saveTranslations(Map<String, String> translations, String locale) {
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            keyRepo.findByCode(entry.getKey()).ifPresent(key -> {
                Translation t = new Translation();
                t.setTranslationKey(key);
                t.setLocale(locale);
                t.setValue(entry.getValue());
                translationRepo.save(t);
            });
        }
    }
}
