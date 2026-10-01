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
 * Inbound email/letter intake vocabulary.
 *
 * <p>Every string the rebuilt intake pipeline shows to a citizen or an officer is returned by the
 * backend as a {@code messageKey} rather than as English prose, so each of these {@code intake.*}
 * keys must exist or the UI renders the raw key. Four groups:
 *
 * <ul>
 *   <li><b>Suppression and deduplication</b> — {@code email_suppressed_by_rule} when an inbound mail
 *       matches an active rule in the Exceptional Email Master ignore list and therefore produces no
 *       draft; {@code duplicate_delivery_ignored} when the identical message was already processed and
 *       the existing draft is returned instead; {@code duplicate_linked_to_parent} when the mail
 *       duplicates an earlier, now-closed complaint and is filed against that parent rather than
 *       opening a new draft.
 *   <li><b>OCR gating</b> — {@code vernacular_manual_entry_required} (regional-language content, so
 *       automatic extraction is deliberately skipped and the item is routed to a skilled data-entry
 *       operator) and {@code ocr_low_confidence_manual_entry} (the scan was not legible enough to
 *       pre-fill the form). {@code manual_entry_required_banner} is the on-screen banner that tells
 *       the officer why the draft arrived empty.
 *   <li><b>NFR-006 attachment limits</b> — per-file 2MB, 25MB total, at most 10 files, plus
 *       {@code attachment_rejected} when the declared type cannot be confirmed by content sniffing.
 *   <li><b>Admin and reporting screens</b> — the Exceptional Email Master CRUD screen title, the
 *       Ignored Emails Report title and its CSV export button, together with the {@code *_not_found}
 *       and invalid-decision validation messages the intake APIs emit.
 * </ul>
 *
 * <p>Seeded in all ten supported locales, following {@link AaTranslationSeeder}: English lives in
 * {@code TranslationKey.defaultValue}, the other nine become {@code Translation} rows. Insert-if-absent,
 * so re-running is a no-op — but note the corollary: correcting a string here does NOT rewrite a row
 * already committed to a database. A text correction needs a code-scoped UPDATE in both migration
 * directories (see database/V32 and database/oracle/V30, which do exactly that for the Scheme-year
 * drift).
 */
@Component
@Order(13)
public class IntakeTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "intake";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public IntakeTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // ═══ Suppression and deduplication ═══
        m.put("intake.email_suppressed_by_rule",
              "This email matched an active ignore rule, so no complaint draft was created.");
        m.put("intake.duplicate_delivery_ignored",
              "This message has already been received and processed. The existing draft is shown below.");
        m.put("intake.duplicate_linked_to_parent",
              "This is a duplicate of an earlier complaint that is now closed. The email and its "
              + "attachments have been added to that existing complaint instead of creating a new draft.");

        // ═══ OCR gating ═══
        m.put("intake.vernacular_manual_entry_required",
              "The content is in a regional language, so automatic text extraction was not attempted. "
              + "It has been routed to a skilled data-entry operator.");
        m.put("intake.ocr_low_confidence_manual_entry",
              "The scanned document could not be read reliably enough to fill the form automatically. "
              + "Please enter the details manually.");
        m.put("intake.manual_entry_required_banner",
              "Manual entry required — automatic text extraction was not used for this draft.");

        // ═══ Not-found and validation ═══
        m.put("intake.draft_not_found", "Draft not found.");
        m.put("intake.ignore_rule_not_found", "Ignore rule not found.");
        m.put("intake.suggested_related_invalid_decision",
              "The decision must be either Accepted or Dismissed.");

        // ═══ NFR-006 attachment limits ═══
        m.put("intake.attachment_too_large", "Each file must be {{size}}MB or smaller.");
        m.put("intake.attachment_total_too_large", "Attachments must total 25MB or less.");
        m.put("intake.attachment_too_many", "You may attach at most 10 files.");
        m.put("intake.attachment_rejected",
              "The file type could not be verified from its contents, so the file was rejected.");

        // ═══ Admin and reporting screens ═══
        m.put("intake.ignored_emails_report_title", "Ignored Emails Report");
        m.put("intake.ignored_emails_export_csv", "Export to CSV");
        m.put("intake.exceptional_email_master_title", "Exceptional Email Master");

        // ═══ Generic UI ═══
        m.put("intake.back_to_queue", "Back to Queue");
        m.put("intake.loading", "Loading…");
        m.put("intake.cancel", "Cancel");
        m.put("intake.edit", "Edit");
        m.put("intake.delete", "Delete");
        m.put("intake.search", "Search");
        m.put("intake.clear_filters", "Clear Filters");

        // ═══ Rule CRUD ═══
        m.put("intake.exceptional_email_master_subtitle",
              "Rules that suppress inbound email before any draft is created");
        m.put("intake.ignore_add_rule", "Add Rule");
        m.put("intake.ignore_edit_rule", "Edit Rule");
        m.put("intake.ignore_create", "Create Rule");
        m.put("intake.ignore_save_changes", "Save Changes");
        m.put("intake.ignore_no_rules", "No suppression rules defined");
        m.put("intake.ignore_activate", "Activate");
        m.put("intake.ignore_deactivate", "Deactivate");
        m.put("intake.ignore_status_active", "Active");
        m.put("intake.ignore_status_inactive", "Inactive");
        m.put("intake.ignore_active_label", "Rule is active");

        // ═══ Rule fields ═══
        m.put("intake.ignore_pattern_label", "Pattern");
        m.put("intake.ignore_pattern_placeholder", "e.g. noreply@bank.com or *@mailer.com");
        m.put("intake.ignore_pattern_hint", "Tested against the selected match field");
        m.put("intake.ignore_match_field_label", "Match Field");
        m.put("intake.ignore_match_field_hint", "Which email header the pattern is tested against");
        m.put("intake.ignore_pattern_type_label", "Pattern Type");

        // ═══ Enum labels ═══
        m.put("intake.match_field_from", "From");
        m.put("intake.match_field_to", "To");
        m.put("intake.match_field_cc", "Cc");
        m.put("intake.match_field_bcc", "Bcc");
        m.put("intake.match_field_subject", "Subject");
        m.put("intake.pattern_type_exact", "Exact");
        m.put("intake.pattern_type_domain", "Domain");
        m.put("intake.pattern_type_wildcard", "Wildcard");
        m.put("intake.pattern_type_contains", "Contains");

        // ═══ Additional criteria ═══
        m.put("intake.ignore_additional_criteria_heading", "Additional Criteria (optional)");
        m.put("intake.ignore_additional_criteria_hint",
              "When set, these must ALSO match for the email to be suppressed (AND logic)");
        m.put("intake.ignore_to_pattern_label", "To");
        m.put("intake.ignore_cc_pattern_label", "Cc");
        m.put("intake.ignore_bcc_pattern_label", "Bcc");
        m.put("intake.ignore_subject_pattern_label", "Subject");
        m.put("intake.ignore_and_logic_badge", "All must match");
        m.put("intake.ignore_reason_label", "Reason");
        m.put("intake.ignore_reason_placeholder", "Why should this be suppressed?");

        // ═══ Exception (counter-rule) ═══
        m.put("intake.ignore_exception_pattern_label", "Exception Pattern (counter-rule)");
        m.put("intake.ignore_exception_pattern_placeholder", "e.g. grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "If this matches, the rule is overridden and the draft IS created");

        // ═══ Explainer ═══
        m.put("intake.ignore_semantics_heading", "How suppression rules work");
        m.put("intake.ignore_semantics_match_field",
              "Match Field decides which header the pattern is tested against (default: From).");
        m.put("intake.ignore_semantics_and_logic",
              "To/Cc/Bcc/Subject are additional criteria — when set they must ALSO match.");
        m.put("intake.ignore_semantics_exception",
              "Exception Pattern is a counter-rule: if it matches, suppression is overridden and the "
              + "draft IS created. Use it to block a whole domain while still admitting named senders.");
        m.put("intake.ignore_semantics_inactive", "An inactive rule suppresses nothing.");

        // ═══ Table columns ═══
        m.put("intake.ignore_col_pattern", "Pattern");
        m.put("intake.ignore_col_match_field", "Match Field");
        m.put("intake.ignore_col_type", "Type");
        m.put("intake.ignore_col_additional_criteria", "Additional Criteria");
        m.put("intake.ignore_col_exception", "Exception");
        m.put("intake.ignore_col_reason", "Reason");
        m.put("intake.ignore_col_suppressed_count", "Suppressed");
        m.put("intake.ignore_col_suppressed_count_hint", "Emails this rule has already suppressed");
        m.put("intake.ignore_col_added_by", "Added By");
        m.put("intake.ignore_col_status", "Status");
        m.put("intake.ignore_col_actions", "Actions");

        // ═══ Toasts ═══
        m.put("intake.ignore_load_failed", "Failed to load suppression rules");
        m.put("intake.ignore_rule_created", "Rule created");
        m.put("intake.ignore_rule_create_failed", "Failed to create rule");
        m.put("intake.ignore_rule_updated", "Rule updated");
        m.put("intake.ignore_rule_update_failed", "Failed to update rule");
        m.put("intake.ignore_rule_deleted", "Rule deleted");
        m.put("intake.ignore_rule_delete_failed", "Failed to delete rule");
        m.put("intake.ignore_rule_activated", "Rule activated");
        m.put("intake.ignore_rule_deactivated", "Rule deactivated");

        // ═══ Report ═══
        m.put("intake.ignored_emails_report_subtitle",
              "Inbound emails suppressed by the Exceptional Email Master");
        m.put("intake.ignored_emails_total", "Total suppressed");
        m.put("intake.ignored_emails_empty", "No emails have been suppressed yet");
        m.put("intake.ignored_emails_empty_filtered", "No suppressed emails match these filters");
        m.put("intake.ignored_emails_load_failed", "Failed to load the suppression report");
        m.put("intake.ignored_emails_export_failed", "Failed to export CSV");

        // ═══ Report filters ═══
        m.put("intake.ignored_filter_sender", "Sender");
        m.put("intake.ignored_filter_sender_placeholder", "Sender email contains…");
        m.put("intake.ignored_filter_from_date", "From Date");
        m.put("intake.ignored_filter_to_date", "To Date");
        m.put("intake.ignored_filter_rule", "Matched Rule");
        m.put("intake.ignored_filter_all_rules", "All rules");

        // ═══ Report columns ═══
        m.put("intake.ignored_col_sender", "Sender");
        m.put("intake.ignored_col_subject", "Subject");
        m.put("intake.ignored_col_received_at", "Received At");
        m.put("intake.ignored_col_matched_rule", "Matched Rule");
        m.put("intake.ignored_col_matched_field", "Matched Field");
        m.put("intake.ignored_col_matched_reason", "Reason");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "यह ईमेल एक सक्रिय अनदेखी नियम से मेल खाता है, इसलिए कोई शिकायत प्रारूप नहीं बनाया गया।");
        m.put("intake.duplicate_delivery_ignored",
              "यह संदेश पहले ही प्राप्त और संसाधित किया जा चुका है। मौजूदा प्रारूप नीचे दिखाया गया है।");
        m.put("intake.duplicate_linked_to_parent",
              "यह एक पूर्व शिकायत की प्रतिलिपि है जो अब बंद हो चुकी है। नया प्रारूप बनाने के बजाय ईमेल और "
              + "उसके संलग्नक उसी मौजूदा शिकायत में जोड़ दिए गए हैं।");
        m.put("intake.vernacular_manual_entry_required",
              "सामग्री क्षेत्रीय भाषा में है, इसलिए स्वचालित पाठ निष्कर्षण जानबूझकर नहीं किया गया। इसे कुशल "
              + "डेटा-एंट्री ऑपरेटर के पास भेज दिया गया है।");
        m.put("intake.ocr_low_confidence_manual_entry",
              "स्कैन किया गया दस्तावेज़ इतनी विश्वसनीयता से नहीं पढ़ा जा सका कि फ़ॉर्म स्वतः भरा जा सके। "
              + "कृपया विवरण हाथ से दर्ज करें।");
        m.put("intake.manual_entry_required_banner",
              "हस्तचालित प्रविष्टि आवश्यक — इस प्रारूप के लिए स्वचालित पाठ निष्कर्षण का उपयोग नहीं किया गया।");
        m.put("intake.draft_not_found", "प्रारूप नहीं मिला।");
        m.put("intake.ignore_rule_not_found", "अनदेखी नियम नहीं मिला।");
        m.put("intake.suggested_related_invalid_decision",
              "निर्णय स्वीकृत या अस्वीकृत में से कोई एक होना चाहिए।");
        m.put("intake.attachment_too_large", "प्रत्येक फ़ाइल {{size}}MB या उससे छोटी होनी चाहिए।");
        m.put("intake.attachment_total_too_large", "संलग्नकों का कुल आकार 25MB या उससे कम होना चाहिए।");
        m.put("intake.attachment_too_many", "आप अधिकतम 10 फ़ाइलें संलग्न कर सकते हैं।");
        m.put("intake.attachment_rejected",
              "फ़ाइल का प्रकार उसकी सामग्री से सत्यापित नहीं हो सका, इसलिए फ़ाइल अस्वीकार कर दी गई।");
        m.put("intake.ignored_emails_report_title", "अनदेखे किए गए ईमेल की रिपोर्ट");
        m.put("intake.ignored_emails_export_csv", "CSV में निर्यात करें");
        m.put("intake.exceptional_email_master_title", "अपवाद ईमेल मास्टर");
        m.put("intake.back_to_queue", "कतार पर वापस जाएँ");
        m.put("intake.loading", "लोड हो रहा है…");
        m.put("intake.cancel", "रद्द करें");
        m.put("intake.edit", "संपादित करें");
        m.put("intake.delete", "हटाएँ");
        m.put("intake.search", "खोजें");
        m.put("intake.clear_filters", "फ़िल्टर हटाएँ");
        m.put("intake.exceptional_email_master_subtitle",
              "वे नियम जो कोई प्रारूप बनने से पहले ही आने वाले ईमेल को दबा देते हैं");
        m.put("intake.ignore_add_rule", "नियम जोड़ें");
        m.put("intake.ignore_edit_rule", "नियम संपादित करें");
        m.put("intake.ignore_create", "नियम बनाएँ");
        m.put("intake.ignore_save_changes", "परिवर्तन सहेजें");
        m.put("intake.ignore_no_rules", "कोई दमन नियम परिभाषित नहीं है");
        m.put("intake.ignore_activate", "सक्रिय करें");
        m.put("intake.ignore_deactivate", "निष्क्रिय करें");
        m.put("intake.ignore_status_active", "सक्रिय");
        m.put("intake.ignore_status_inactive", "निष्क्रिय");
        m.put("intake.ignore_active_label", "नियम सक्रिय है");
        m.put("intake.ignore_pattern_label", "पैटर्न");
        m.put("intake.ignore_pattern_placeholder", "उदाहरण: noreply@bank.com या *@mailer.com");
        m.put("intake.ignore_pattern_hint", "चयनित मिलान क्षेत्र पर परखा जाता है");
        m.put("intake.ignore_match_field_label", "मिलान क्षेत्र");
        m.put("intake.ignore_match_field_hint", "पैटर्न को ईमेल के किस शीर्षक पर परखा जाए");
        m.put("intake.ignore_pattern_type_label", "पैटर्न प्रकार");
        m.put("intake.match_field_from", "प्रेषक");
        m.put("intake.match_field_to", "प्राप्तकर्ता");
        m.put("intake.match_field_cc", "प्रतिलिपि");
        m.put("intake.match_field_bcc", "गुप्त प्रतिलिपि");
        m.put("intake.match_field_subject", "विषय");
        m.put("intake.pattern_type_exact", "यथावत");
        m.put("intake.pattern_type_domain", "डोमेन");
        m.put("intake.pattern_type_wildcard", "वाइल्डकार्ड");
        m.put("intake.pattern_type_contains", "शामिल है");
        m.put("intake.ignore_additional_criteria_heading", "अतिरिक्त मानदंड (वैकल्पिक)");
        m.put("intake.ignore_additional_criteria_hint",
              "निर्धारित होने पर ईमेल दबाने के लिए इनका भी मेल होना अनिवार्य है (AND तर्क)");
        m.put("intake.ignore_to_pattern_label", "प्राप्तकर्ता");
        m.put("intake.ignore_cc_pattern_label", "प्रतिलिपि");
        m.put("intake.ignore_bcc_pattern_label", "गुप्त प्रतिलिपि");
        m.put("intake.ignore_subject_pattern_label", "विषय");
        m.put("intake.ignore_and_logic_badge", "सभी का मेल आवश्यक");
        m.put("intake.ignore_reason_label", "कारण");
        m.put("intake.ignore_reason_placeholder", "इसे क्यों दबाया जाना चाहिए?");
        m.put("intake.ignore_exception_pattern_label", "अपवाद पैटर्न (प्रति-नियम)");
        m.put("intake.ignore_exception_pattern_placeholder", "उदाहरण: grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "यदि यह मेल खाता है तो नियम निरस्त हो जाता है और प्रारूप बनाया जाता है");
        m.put("intake.ignore_semantics_heading", "दमन नियम कैसे काम करते हैं");
        m.put("intake.ignore_semantics_match_field",
              "मिलान क्षेत्र तय करता है कि पैटर्न को किस शीर्षक पर परखा जाए (डिफ़ॉल्ट: प्रेषक)।");
        m.put("intake.ignore_semantics_and_logic",
              "प्राप्तकर्ता/प्रतिलिपि/गुप्त प्रतिलिपि/विषय अतिरिक्त मानदंड हैं — निर्धारित होने पर इनका भी "
              + "मेल होना चाहिए।");
        m.put("intake.ignore_semantics_exception",
              "अपवाद पैटर्न एक प्रति-नियम है: यदि यह मेल खाता है तो दमन निरस्त हो जाता है और प्रारूप बनाया "
              + "जाता है। इसका उपयोग पूरे डोमेन को रोकते हुए भी नामित प्रेषकों को अनुमति देने के लिए करें।");
        m.put("intake.ignore_semantics_inactive", "निष्क्रिय नियम कुछ भी नहीं दबाता।");
        m.put("intake.ignore_col_pattern", "पैटर्न");
        m.put("intake.ignore_col_match_field", "मिलान क्षेत्र");
        m.put("intake.ignore_col_type", "प्रकार");
        m.put("intake.ignore_col_additional_criteria", "अतिरिक्त मानदंड");
        m.put("intake.ignore_col_exception", "अपवाद");
        m.put("intake.ignore_col_reason", "कारण");
        m.put("intake.ignore_col_suppressed_count", "दबाए गए");
        m.put("intake.ignore_col_suppressed_count_hint", "इस नियम द्वारा अब तक दबाए गए ईमेल");
        m.put("intake.ignore_col_added_by", "जोड़ने वाला");
        m.put("intake.ignore_col_status", "स्थिति");
        m.put("intake.ignore_col_actions", "कार्रवाइयाँ");
        m.put("intake.ignore_load_failed", "दमन नियम लोड करने में विफल");
        m.put("intake.ignore_rule_created", "नियम बनाया गया");
        m.put("intake.ignore_rule_create_failed", "नियम बनाने में विफल");
        m.put("intake.ignore_rule_updated", "नियम अद्यतन किया गया");
        m.put("intake.ignore_rule_update_failed", "नियम अद्यतन करने में विफल");
        m.put("intake.ignore_rule_deleted", "नियम हटाया गया");
        m.put("intake.ignore_rule_delete_failed", "नियम हटाने में विफल");
        m.put("intake.ignore_rule_activated", "नियम सक्रिय किया गया");
        m.put("intake.ignore_rule_deactivated", "नियम निष्क्रिय किया गया");
        m.put("intake.ignored_emails_report_subtitle",
              "अपवाद ईमेल मास्टर द्वारा दबाए गए आने वाले ईमेल");
        m.put("intake.ignored_emails_total", "कुल दबाए गए");
        m.put("intake.ignored_emails_empty", "अभी तक कोई ईमेल नहीं दबाया गया है");
        m.put("intake.ignored_emails_empty_filtered", "इन फ़िल्टरों से मेल खाता कोई दबाया गया ईमेल नहीं");
        m.put("intake.ignored_emails_load_failed", "दमन रिपोर्ट लोड करने में विफल");
        m.put("intake.ignored_emails_export_failed", "CSV निर्यात करने में विफल");
        m.put("intake.ignored_filter_sender", "प्रेषक");
        m.put("intake.ignored_filter_sender_placeholder", "प्रेषक ईमेल में शामिल है…");
        m.put("intake.ignored_filter_from_date", "आरंभ तिथि");
        m.put("intake.ignored_filter_to_date", "अंतिम तिथि");
        m.put("intake.ignored_filter_rule", "मेल खाया नियम");
        m.put("intake.ignored_filter_all_rules", "सभी नियम");
        m.put("intake.ignored_col_sender", "प्रेषक");
        m.put("intake.ignored_col_subject", "विषय");
        m.put("intake.ignored_col_received_at", "प्राप्ति समय");
        m.put("intake.ignored_col_matched_rule", "मेल खाया नियम");
        m.put("intake.ignored_col_matched_field", "मेल खाया क्षेत्र");
        m.put("intake.ignored_col_matched_reason", "कारण");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "हा ईमेल सक्रिय दुर्लक्ष नियमाशी जुळला, त्यामुळे कोणताही तक्रार मसुदा तयार केला गेला नाही.");
        m.put("intake.duplicate_delivery_ignored",
              "हा संदेश आधीच प्राप्त होऊन त्यावर प्रक्रिया झाली आहे. सध्याचा मसुदा खाली दाखवला आहे.");
        m.put("intake.duplicate_linked_to_parent",
              "ही आधीच्या एका तक्रारीची प्रतिकृती आहे, जी आता बंद झाली आहे. नवीन मसुदा तयार करण्याऐवजी "
              + "ईमेल व त्याची जोडपत्रे त्याच तक्रारीला जोडली गेली आहेत.");
        m.put("intake.vernacular_manual_entry_required",
              "मजकूर प्रादेशिक भाषेत आहे, म्हणून स्वयंचलित मजकूर काढणी जाणीवपूर्वक केली गेली नाही. ते कुशल "
              + "डेटा-एंट्री ऑपरेटरकडे पाठवण्यात आले आहे.");
        m.put("intake.ocr_low_confidence_manual_entry",
              "स्कॅन केलेला दस्तऐवज फॉर्म स्वयंचलितपणे भरण्याइतका विश्वासार्हपणे वाचता आला नाही. कृपया तपशील "
              + "हाताने भरा.");
        m.put("intake.manual_entry_required_banner",
              "हाताने नोंद आवश्यक — या मसुद्यासाठी स्वयंचलित मजकूर काढणी वापरली गेली नाही.");
        m.put("intake.draft_not_found", "मसुदा आढळला नाही.");
        m.put("intake.ignore_rule_not_found", "दुर्लक्ष नियम आढळला नाही.");
        m.put("intake.suggested_related_invalid_decision",
              "निर्णय स्वीकारलेला किंवा फेटाळलेला यापैकी एक असणे आवश्यक आहे.");
        m.put("intake.attachment_too_large", "प्रत्येक फाइल {{size}}MB किंवा त्याहून लहान असावी.");
        m.put("intake.attachment_total_too_large", "जोडपत्रांचा एकूण आकार 25MB किंवा त्याहून कमी असावा.");
        m.put("intake.attachment_too_many", "तुम्ही जास्तीत जास्त 10 फाइल्स जोडू शकता.");
        m.put("intake.attachment_rejected",
              "फाइलचा प्रकार तिच्या आतील मजकुरावरून पडताळता आला नाही, म्हणून फाइल नाकारली गेली.");
        m.put("intake.ignored_emails_report_title", "दुर्लक्षित ईमेलचा अहवाल");
        m.put("intake.ignored_emails_export_csv", "CSV मध्ये निर्यात करा");
        m.put("intake.exceptional_email_master_title", "अपवादात्मक ईमेल मास्टर");
        m.put("intake.back_to_queue", "रांगेकडे परत जा");
        m.put("intake.loading", "लोड होत आहे…");
        m.put("intake.cancel", "रद्द करा");
        m.put("intake.edit", "संपादन करा");
        m.put("intake.delete", "काढून टाका");
        m.put("intake.search", "शोधा");
        m.put("intake.clear_filters", "गाळण्या मोकळ्या करा");
        m.put("intake.exceptional_email_master_subtitle",
              "कोणताही मसुदा तयार होण्यापूर्वीच येणारे ईमेल थांबवणारे नियम");
        m.put("intake.ignore_add_rule", "नियम जोडा");
        m.put("intake.ignore_edit_rule", "नियम संपादित करा");
        m.put("intake.ignore_create", "नियम तयार करा");
        m.put("intake.ignore_save_changes", "बदल जतन करा");
        m.put("intake.ignore_no_rules", "कोणतेही थांबवणारे नियम निश्चित केलेले नाहीत");
        m.put("intake.ignore_activate", "कार्यान्वित करा");
        m.put("intake.ignore_deactivate", "बंद करा");
        m.put("intake.ignore_status_active", "कार्यरत");
        m.put("intake.ignore_status_inactive", "बंद");
        m.put("intake.ignore_active_label", "नियम कार्यरत आहे");
        m.put("intake.ignore_pattern_label", "नमुना");
        m.put("intake.ignore_pattern_placeholder", "उदा. noreply@bank.com किंवा *@mailer.com");
        m.put("intake.ignore_pattern_hint", "निवडलेल्या जुळणी क्षेत्रावर तपासला जातो");
        m.put("intake.ignore_match_field_label", "जुळणी क्षेत्र");
        m.put("intake.ignore_match_field_hint", "नमुना ईमेलच्या कोणत्या शीर्षकावर तपासायचा");
        m.put("intake.ignore_pattern_type_label", "नमुन्याचा प्रकार");
        m.put("intake.match_field_from", "पाठवणारा");
        m.put("intake.match_field_to", "प्रति");
        m.put("intake.match_field_cc", "प्रतिलिपी");
        m.put("intake.match_field_bcc", "गुप्त प्रतिलिपी");
        m.put("intake.match_field_subject", "विषय");
        m.put("intake.pattern_type_exact", "तंतोतंत");
        m.put("intake.pattern_type_domain", "डोमेन");
        m.put("intake.pattern_type_wildcard", "वाइल्डकार्ड");
        m.put("intake.pattern_type_contains", "समाविष्ट आहे");
        m.put("intake.ignore_additional_criteria_heading", "अतिरिक्त निकष (ऐच्छिक)");
        m.put("intake.ignore_additional_criteria_hint",
              "ठरवल्यास ईमेल थांबवण्यासाठी हेदेखील जुळणे आवश्यक आहे (AND तर्क)");
        m.put("intake.ignore_to_pattern_label", "प्रति");
        m.put("intake.ignore_cc_pattern_label", "प्रतिलिपी");
        m.put("intake.ignore_bcc_pattern_label", "गुप्त प्रतिलिपी");
        m.put("intake.ignore_subject_pattern_label", "विषय");
        m.put("intake.ignore_and_logic_badge", "सर्व जुळणे आवश्यक");
        m.put("intake.ignore_reason_label", "कारण");
        m.put("intake.ignore_reason_placeholder", "हे का थांबवायचे आहे?");
        m.put("intake.ignore_exception_pattern_label", "अपवाद नमुना (प्रति-नियम)");
        m.put("intake.ignore_exception_pattern_placeholder", "उदा. grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "हे जुळल्यास नियम बाजूला ठेवला जातो आणि मसुदा तयार होतो");
        m.put("intake.ignore_semantics_heading", "थांबवणारे नियम कसे चालतात");
        m.put("intake.ignore_semantics_match_field",
              "जुळणी क्षेत्र ठरवते की नमुना कोणत्या शीर्षकावर तपासायचा (पूर्वनिर्धारित: पाठवणारा).");
        m.put("intake.ignore_semantics_and_logic",
              "प्रति/प्रतिलिपी/गुप्त प्रतिलिपी/विषय हे अतिरिक्त निकष आहेत — ठरवल्यास तेदेखील जुळणे आवश्यक आहे.");
        m.put("intake.ignore_semantics_exception",
              "अपवाद नमुना हा प्रति-नियम आहे: तो जुळल्यास थांबवणे बाजूला ठेवले जाते आणि मसुदा तयार होतो. "
              + "संपूर्ण डोमेन अडवूनही निवडक पाठवणाऱ्यांना परवानगी देण्यासाठी याचा वापर करा.");
        m.put("intake.ignore_semantics_inactive", "बंद असलेला नियम काहीही थांबवत नाही.");
        m.put("intake.ignore_col_pattern", "नमुना");
        m.put("intake.ignore_col_match_field", "जुळणी क्षेत्र");
        m.put("intake.ignore_col_type", "प्रकार");
        m.put("intake.ignore_col_additional_criteria", "अतिरिक्त निकष");
        m.put("intake.ignore_col_exception", "अपवाद");
        m.put("intake.ignore_col_reason", "कारण");
        m.put("intake.ignore_col_suppressed_count", "थांबवलेले");
        m.put("intake.ignore_col_suppressed_count_hint", "या नियमाने आतापर्यंत थांबवलेले ईमेल");
        m.put("intake.ignore_col_added_by", "जोडणारा");
        m.put("intake.ignore_col_status", "स्थिती");
        m.put("intake.ignore_col_actions", "कृती");
        m.put("intake.ignore_load_failed", "थांबवणारे नियम लोड करण्यात अपयश");
        m.put("intake.ignore_rule_created", "नियम तयार झाला");
        m.put("intake.ignore_rule_create_failed", "नियम तयार करण्यात अपयश");
        m.put("intake.ignore_rule_updated", "नियम अद्ययावत झाला");
        m.put("intake.ignore_rule_update_failed", "नियम अद्ययावत करण्यात अपयश");
        m.put("intake.ignore_rule_deleted", "नियम काढून टाकला");
        m.put("intake.ignore_rule_delete_failed", "नियम काढून टाकण्यात अपयश");
        m.put("intake.ignore_rule_activated", "नियम कार्यान्वित झाला");
        m.put("intake.ignore_rule_deactivated", "नियम बंद केला");
        m.put("intake.ignored_emails_report_subtitle",
              "अपवादात्मक ईमेल मास्टरने थांबवलेले येणारे ईमेल");
        m.put("intake.ignored_emails_total", "एकूण थांबवलेले");
        m.put("intake.ignored_emails_empty", "अद्याप कोणताही ईमेल थांबवलेला नाही");
        m.put("intake.ignored_emails_empty_filtered", "या गाळण्यांशी जुळणारा कोणताही थांबवलेला ईमेल नाही");
        m.put("intake.ignored_emails_load_failed", "थांबवण्याचा अहवाल लोड करण्यात अपयश");
        m.put("intake.ignored_emails_export_failed", "CSV निर्यात करण्यात अपयश");
        m.put("intake.ignored_filter_sender", "पाठवणारा");
        m.put("intake.ignored_filter_sender_placeholder", "पाठवणाऱ्याच्या ईमेलमध्ये आहे…");
        m.put("intake.ignored_filter_from_date", "पासूनची तारीख");
        m.put("intake.ignored_filter_to_date", "पर्यंतची तारीख");
        m.put("intake.ignored_filter_rule", "जुळलेला नियम");
        m.put("intake.ignored_filter_all_rules", "सर्व नियम");
        m.put("intake.ignored_col_sender", "पाठवणारा");
        m.put("intake.ignored_col_subject", "विषय");
        m.put("intake.ignored_col_received_at", "प्राप्त झाल्याची वेळ");
        m.put("intake.ignored_col_matched_rule", "जुळलेला नियम");
        m.put("intake.ignored_col_matched_field", "जुळलेले क्षेत्र");
        m.put("intake.ignored_col_matched_reason", "कारण");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "এই ইমেলটি একটি সক্রিয় উপেক্ষা নিয়মের সঙ্গে মিলে গেছে, তাই কোনো অভিযোগের খসড়া তৈরি করা হয়নি।");
        m.put("intake.duplicate_delivery_ignored",
              "এই বার্তাটি ইতিমধ্যেই গৃহীত ও প্রক্রিয়াকৃত হয়েছে। বিদ্যমান খসড়াটি নিচে দেখানো হয়েছে।");
        m.put("intake.duplicate_linked_to_parent",
              "এটি একটি পূর্ববর্তী অভিযোগের অনুরূপ, যা এখন নিষ্পত্তি হয়ে গেছে। নতুন খসড়া তৈরির বদলে ইমেল ও "
              + "তার সংযুক্তিগুলি সেই বিদ্যমান অভিযোগেই যুক্ত করা হয়েছে।");
        m.put("intake.vernacular_manual_entry_required",
              "বিষয়বস্তু আঞ্চলিক ভাষায় রয়েছে, তাই স্বয়ংক্রিয় লেখা নিষ্কাশন সচেতনভাবে করা হয়নি। এটি একজন "
              + "দক্ষ ডেটা-এন্ট্রি অপারেটরের কাছে পাঠানো হয়েছে।");
        m.put("intake.ocr_low_confidence_manual_entry",
              "স্ক্যান করা নথিটি ফর্ম স্বয়ংক্রিয়ভাবে পূরণ করার মতো নির্ভরযোগ্যভাবে পড়া যায়নি। অনুগ্রহ করে "
              + "বিবরণ হাতে লিখুন।");
        m.put("intake.manual_entry_required_banner",
              "হাতে তথ্য প্রবেশ প্রয়োজন — এই খসড়ার জন্য স্বয়ংক্রিয় লেখা নিষ্কাশন ব্যবহার করা হয়নি।");
        m.put("intake.draft_not_found", "খসড়া পাওয়া যায়নি।");
        m.put("intake.ignore_rule_not_found", "উপেক্ষা নিয়ম পাওয়া যায়নি।");
        m.put("intake.suggested_related_invalid_decision",
              "সিদ্ধান্ত অবশ্যই গৃহীত অথবা খারিজ হতে হবে।");
        m.put("intake.attachment_too_large", "প্রতিটি ফাইল {{size}} এমবি বা তার কম হতে হবে।");
        m.put("intake.attachment_total_too_large", "সংযুক্তিগুলির মোট আকার ২৫ এমবি বা তার কম হতে হবে।");
        m.put("intake.attachment_too_many", "আপনি সর্বাধিক ১০টি ফাইল সংযুক্ত করতে পারেন।");
        m.put("intake.attachment_rejected",
              "ফাইলের ধরন তার অন্তর্বস্তু থেকে যাচাই করা যায়নি, তাই ফাইলটি বাতিল করা হয়েছে।");
        m.put("intake.ignored_emails_report_title", "উপেক্ষিত ইমেলের প্রতিবেদন");
        m.put("intake.ignored_emails_export_csv", "সিএসভি-তে রপ্তানি করুন");
        m.put("intake.exceptional_email_master_title", "ব্যতিক্রমী ইমেল মাস্টার");
        m.put("intake.back_to_queue", "সারিতে ফিরে যান");
        m.put("intake.loading", "লোড হচ্ছে…");
        m.put("intake.cancel", "বাতিল করুন");
        m.put("intake.edit", "সম্পাদনা করুন");
        m.put("intake.delete", "মুছে ফেলুন");
        m.put("intake.search", "অনুসন্ধান করুন");
        m.put("intake.clear_filters", "ছাঁকনি মুছে ফেলুন");
        m.put("intake.exceptional_email_master_subtitle",
              "যে নিয়মগুলি কোনো খসড়া তৈরির আগেই আগত ইমেল দমন করে");
        m.put("intake.ignore_add_rule", "নিয়ম যোগ করুন");
        m.put("intake.ignore_edit_rule", "নিয়ম সম্পাদনা করুন");
        m.put("intake.ignore_create", "নিয়ম তৈরি করুন");
        m.put("intake.ignore_save_changes", "পরিবর্তন সংরক্ষণ করুন");
        m.put("intake.ignore_no_rules", "কোনো দমন নিয়ম নির্ধারিত নেই");
        m.put("intake.ignore_activate", "সক্রিয় করুন");
        m.put("intake.ignore_deactivate", "নিষ্ক্রিয় করুন");
        m.put("intake.ignore_status_active", "সক্রিয়");
        m.put("intake.ignore_status_inactive", "নিষ্ক্রিয়");
        m.put("intake.ignore_active_label", "নিয়মটি সক্রিয় রয়েছে");
        m.put("intake.ignore_pattern_label", "নকশা");
        m.put("intake.ignore_pattern_placeholder", "যেমন noreply@bank.com অথবা *@mailer.com");
        m.put("intake.ignore_pattern_hint", "নির্বাচিত মিল ক্ষেত্রের বিপরীতে পরীক্ষা করা হয়");
        m.put("intake.ignore_match_field_label", "মিল ক্ষেত্র");
        m.put("intake.ignore_match_field_hint", "নকশাটি ইমেলের কোন শিরোনামে পরীক্ষা করা হবে");
        m.put("intake.ignore_pattern_type_label", "নকশার ধরন");
        m.put("intake.match_field_from", "প্রেরক");
        m.put("intake.match_field_to", "প্রাপক");
        m.put("intake.match_field_cc", "অনুলিপি");
        m.put("intake.match_field_bcc", "গোপন অনুলিপি");
        m.put("intake.match_field_subject", "বিষয়");
        m.put("intake.pattern_type_exact", "হুবহু");
        m.put("intake.pattern_type_domain", "ডোমেইন");
        m.put("intake.pattern_type_wildcard", "ওয়াইল্ডকার্ড");
        m.put("intake.pattern_type_contains", "রয়েছে");
        m.put("intake.ignore_additional_criteria_heading", "অতিরিক্ত মানদণ্ড (ঐচ্ছিক)");
        m.put("intake.ignore_additional_criteria_hint",
              "নির্ধারিত থাকলে ইমেল দমনের জন্য এগুলিরও মিল থাকতে হবে (AND যুক্তি)");
        m.put("intake.ignore_to_pattern_label", "প্রাপক");
        m.put("intake.ignore_cc_pattern_label", "অনুলিপি");
        m.put("intake.ignore_bcc_pattern_label", "গোপন অনুলিপি");
        m.put("intake.ignore_subject_pattern_label", "বিষয়");
        m.put("intake.ignore_and_logic_badge", "সবগুলিরই মিল থাকতে হবে");
        m.put("intake.ignore_reason_label", "কারণ");
        m.put("intake.ignore_reason_placeholder", "এটি কেন দমন করা উচিত?");
        m.put("intake.ignore_exception_pattern_label", "ব্যতিক্রম নকশা (পাল্টা-নিয়ম)");
        m.put("intake.ignore_exception_pattern_placeholder", "যেমন grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "এটির মিল হলে নিয়মটি রদ হয়ে যায় এবং খসড়া তৈরি হয়");
        m.put("intake.ignore_semantics_heading", "দমন নিয়ম কীভাবে কাজ করে");
        m.put("intake.ignore_semantics_match_field",
              "মিল ক্ষেত্র ঠিক করে নকশাটি কোন শিরোনামে পরীক্ষা করা হবে (স্বাভাবিক: প্রেরক)।");
        m.put("intake.ignore_semantics_and_logic",
              "প্রাপক/অনুলিপি/গোপন অনুলিপি/বিষয় অতিরিক্ত মানদণ্ড — নির্ধারিত থাকলে এগুলিরও মিল থাকতে হবে।");
        m.put("intake.ignore_semantics_exception",
              "ব্যতিক্রম নকশা একটি পাল্টা-নিয়ম: এটির মিল হলে দমন রদ হয়ে যায় এবং খসড়া তৈরি হয়। গোটা "
              + "ডোমেইন আটকেও নির্দিষ্ট প্রেরকদের অনুমতি দিতে এটি ব্যবহার করুন।");
        m.put("intake.ignore_semantics_inactive", "নিষ্ক্রিয় নিয়ম কিছুই দমন করে না।");
        m.put("intake.ignore_col_pattern", "নকশা");
        m.put("intake.ignore_col_match_field", "মিল ক্ষেত্র");
        m.put("intake.ignore_col_type", "ধরন");
        m.put("intake.ignore_col_additional_criteria", "অতিরিক্ত মানদণ্ড");
        m.put("intake.ignore_col_exception", "ব্যতিক্রম");
        m.put("intake.ignore_col_reason", "কারণ");
        m.put("intake.ignore_col_suppressed_count", "দমিত");
        m.put("intake.ignore_col_suppressed_count_hint", "এই নিয়মে ইতিমধ্যে দমন করা ইমেল");
        m.put("intake.ignore_col_added_by", "যোগ করেছেন");
        m.put("intake.ignore_col_status", "অবস্থা");
        m.put("intake.ignore_col_actions", "কার্যক্রম");
        m.put("intake.ignore_load_failed", "দমন নিয়ম লোড করা যায়নি");
        m.put("intake.ignore_rule_created", "নিয়ম তৈরি হয়েছে");
        m.put("intake.ignore_rule_create_failed", "নিয়ম তৈরি করা যায়নি");
        m.put("intake.ignore_rule_updated", "নিয়ম হালনাগাদ হয়েছে");
        m.put("intake.ignore_rule_update_failed", "নিয়ম হালনাগাদ করা যায়নি");
        m.put("intake.ignore_rule_deleted", "নিয়ম মুছে ফেলা হয়েছে");
        m.put("intake.ignore_rule_delete_failed", "নিয়ম মুছে ফেলা যায়নি");
        m.put("intake.ignore_rule_activated", "নিয়ম সক্রিয় করা হয়েছে");
        m.put("intake.ignore_rule_deactivated", "নিয়ম নিষ্ক্রিয় করা হয়েছে");
        m.put("intake.ignored_emails_report_subtitle",
              "ব্যতিক্রমী ইমেল মাস্টার দ্বারা দমন করা আগত ইমেল");
        m.put("intake.ignored_emails_total", "মোট দমিত");
        m.put("intake.ignored_emails_empty", "এখনও কোনো ইমেল দমন করা হয়নি");
        m.put("intake.ignored_emails_empty_filtered", "এই ছাঁকনির সঙ্গে মেলে এমন কোনো দমিত ইমেল নেই");
        m.put("intake.ignored_emails_load_failed", "দমন প্রতিবেদন লোড করা যায়নি");
        m.put("intake.ignored_emails_export_failed", "সিএসভি রপ্তানি করা যায়নি");
        m.put("intake.ignored_filter_sender", "প্রেরক");
        m.put("intake.ignored_filter_sender_placeholder", "প্রেরকের ইমেলে রয়েছে…");
        m.put("intake.ignored_filter_from_date", "শুরুর তারিখ");
        m.put("intake.ignored_filter_to_date", "শেষের তারিখ");
        m.put("intake.ignored_filter_rule", "মিলে যাওয়া নিয়ম");
        m.put("intake.ignored_filter_all_rules", "সব নিয়ম");
        m.put("intake.ignored_col_sender", "প্রেরক");
        m.put("intake.ignored_col_subject", "বিষয়");
        m.put("intake.ignored_col_received_at", "প্রাপ্তির সময়");
        m.put("intake.ignored_col_matched_rule", "মিলে যাওয়া নিয়ম");
        m.put("intake.ignored_col_matched_field", "মিলে যাওয়া ক্ষেত্র");
        m.put("intake.ignored_col_matched_reason", "কারণ");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "ఈ ఇమెయిల్ ఒక క్రియాశీల విస్మరణ నియమానికి సరిపోలింది, కాబట్టి ఎటువంటి ఫిర్యాదు ముసాయిదా సృష్టించబడలేదు.");
        m.put("intake.duplicate_delivery_ignored",
              "ఈ సందేశం ఇప్పటికే స్వీకరించి ప్రాసెస్ చేయబడింది. ఇప్పటికే ఉన్న ముసాయిదా క్రింద చూపబడింది.");
        m.put("intake.duplicate_linked_to_parent",
              "ఇది ఇప్పుడు ముగిసిన ఒక పూర్వ ఫిర్యాదుకు నకలు. కొత్త ముసాయిదా సృష్టించే బదులు ఇమెయిల్ మరియు "
              + "దాని అనుబంధాలు ఆ ఫిర్యాదుకే జోడించబడ్డాయి.");
        m.put("intake.vernacular_manual_entry_required",
              "విషయం ప్రాంతీయ భాషలో ఉంది, కాబట్టి స్వయంచాలక పాఠ్య సేకరణ ఉద్దేశపూర్వకంగా ప్రయత్నించలేదు. "
              + "దీనిని నైపుణ్యం కలిగిన డేటా-ఎంట్రీ ఆపరేటర్‌కు పంపారు.");
        m.put("intake.ocr_low_confidence_manual_entry",
              "ఫారమ్‌ను స్వయంచాలకంగా నింపేంత విశ్వసనీయంగా స్కాన్ చేసిన పత్రాన్ని చదవలేకపోయాము. దయచేసి "
              + "వివరాలను చేతితో నమోదు చేయండి.");
        m.put("intake.manual_entry_required_banner",
              "చేతితో నమోదు అవసరం — ఈ ముసాయిదా కోసం స్వయంచాలక పాఠ్య సేకరణ ఉపయోగించలేదు.");
        m.put("intake.draft_not_found", "ముసాయిదా కనుగొనబడలేదు.");
        m.put("intake.ignore_rule_not_found", "విస్మరణ నియమం కనుగొనబడలేదు.");
        m.put("intake.suggested_related_invalid_decision",
              "నిర్ణయం ఆమోదించబడింది లేదా తిరస్కరించబడింది అనే వాటిలో ఒకటిగా ఉండాలి.");
        m.put("intake.attachment_too_large", "ప్రతి ఫైల్ {{size}}MB లేదా అంతకంటే తక్కువ ఉండాలి.");
        m.put("intake.attachment_total_too_large", "అనుబంధాల మొత్తం పరిమాణం 25MB లేదా అంతకంటే తక్కువ ఉండాలి.");
        m.put("intake.attachment_too_many", "మీరు గరిష్ఠంగా 10 ఫైళ్లను జోడించవచ్చు.");
        m.put("intake.attachment_rejected",
              "ఫైల్ రకాన్ని దాని కంటెంట్ నుండి ధృవీకరించలేకపోయాము, కాబట్టి ఫైల్ తిరస్కరించబడింది.");
        m.put("intake.ignored_emails_report_title", "విస్మరించిన ఇమెయిల్‌ల నివేదిక");
        m.put("intake.ignored_emails_export_csv", "CSVకి ఎగుమతి చేయండి");
        m.put("intake.exceptional_email_master_title", "మినహాయింపు ఇమెయిల్ మాస్టర్");
        m.put("intake.back_to_queue", "వరుసకు తిరిగి వెళ్లండి");
        m.put("intake.loading", "లోడ్ అవుతోంది…");
        m.put("intake.cancel", "రద్దు చేయండి");
        m.put("intake.edit", "సవరించండి");
        m.put("intake.delete", "తొలగించండి");
        m.put("intake.search", "వెతకండి");
        m.put("intake.clear_filters", "వడపోతలను తొలగించండి");
        m.put("intake.exceptional_email_master_subtitle",
              "ఏ ముసాయిదా సృష్టించబడకముందే వచ్చే ఇమెయిల్‌ను అణచివేసే నియమాలు");
        m.put("intake.ignore_add_rule", "నియమాన్ని జోడించండి");
        m.put("intake.ignore_edit_rule", "నియమాన్ని సవరించండి");
        m.put("intake.ignore_create", "నియమాన్ని సృష్టించండి");
        m.put("intake.ignore_save_changes", "మార్పులను భద్రపరచండి");
        m.put("intake.ignore_no_rules", "ఎటువంటి అణచివేత నియమాలు నిర్దేశించబడలేదు");
        m.put("intake.ignore_activate", "క్రియాశీలం చేయండి");
        m.put("intake.ignore_deactivate", "నిష్క్రియం చేయండి");
        m.put("intake.ignore_status_active", "క్రియాశీలం");
        m.put("intake.ignore_status_inactive", "నిష్క్రియం");
        m.put("intake.ignore_active_label", "నియమం క్రియాశీలంగా ఉంది");
        m.put("intake.ignore_pattern_label", "నమూనా");
        m.put("intake.ignore_pattern_placeholder", "ఉదా. noreply@bank.com లేదా *@mailer.com");
        m.put("intake.ignore_pattern_hint", "ఎంచుకున్న సరిపోలిక క్షేత్రంపై పరీక్షించబడుతుంది");
        m.put("intake.ignore_match_field_label", "సరిపోలిక క్షేత్రం");
        m.put("intake.ignore_match_field_hint", "నమూనాను ఇమెయిల్‌లోని ఏ శీర్షికపై పరీక్షించాలి");
        m.put("intake.ignore_pattern_type_label", "నమూనా రకం");
        m.put("intake.match_field_from", "పంపినవారు");
        m.put("intake.match_field_to", "స్వీకర్త");
        m.put("intake.match_field_cc", "ప్రతి");
        m.put("intake.match_field_bcc", "గోప్య ప్రతి");
        m.put("intake.match_field_subject", "విషయం");
        m.put("intake.pattern_type_exact", "సరిగ్గా");
        m.put("intake.pattern_type_domain", "డొమైన్");
        m.put("intake.pattern_type_wildcard", "వైల్డ్‌కార్డ్");
        m.put("intake.pattern_type_contains", "కలిగి ఉంది");
        m.put("intake.ignore_additional_criteria_heading", "అదనపు ప్రమాణాలు (ఐచ్ఛికం)");
        m.put("intake.ignore_additional_criteria_hint",
              "నిర్దేశించినప్పుడు ఇమెయిల్ అణచివేయబడాలంటే ఇవి కూడా సరిపోలాలి (AND తర్కం)");
        m.put("intake.ignore_to_pattern_label", "స్వీకర్త");
        m.put("intake.ignore_cc_pattern_label", "ప్రతి");
        m.put("intake.ignore_bcc_pattern_label", "గోప్య ప్రతి");
        m.put("intake.ignore_subject_pattern_label", "విషయం");
        m.put("intake.ignore_and_logic_badge", "అన్నీ సరిపోలాలి");
        m.put("intake.ignore_reason_label", "కారణం");
        m.put("intake.ignore_reason_placeholder", "దీన్ని ఎందుకు అణచివేయాలి?");
        m.put("intake.ignore_exception_pattern_label", "మినహాయింపు నమూనా (ప్రతి-నియమం)");
        m.put("intake.ignore_exception_pattern_placeholder", "ఉదా. grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "ఇది సరిపోలితే నియమం రద్దవుతుంది మరియు ముసాయిదా సృష్టించబడుతుంది");
        m.put("intake.ignore_semantics_heading", "అణచివేత నియమాలు ఎలా పనిచేస్తాయి");
        m.put("intake.ignore_semantics_match_field",
              "నమూనాను ఏ శీర్షికపై పరీక్షించాలో సరిపోలిక క్షేత్రం నిర్ణయిస్తుంది (అప్రమేయం: పంపినవారు).");
        m.put("intake.ignore_semantics_and_logic",
              "స్వీకర్త/ప్రతి/గోప్య ప్రతి/విషయం అదనపు ప్రమాణాలు — నిర్దేశించినప్పుడు అవి కూడా సరిపోలాలి.");
        m.put("intake.ignore_semantics_exception",
              "మినహాయింపు నమూనా ఒక ప్రతి-నియమం: అది సరిపోలితే అణచివేత రద్దవుతుంది మరియు ముసాయిదా "
              + "సృష్టించబడుతుంది. మొత్తం డొమైన్‌ను నిరోధించినా పేరు పెట్టిన పంపినవారిని అనుమతించడానికి "
              + "దీన్ని ఉపయోగించండి.");
        m.put("intake.ignore_semantics_inactive", "నిష్క్రియ నియమం ఏమీ అణచివేయదు.");
        m.put("intake.ignore_col_pattern", "నమూనా");
        m.put("intake.ignore_col_match_field", "సరిపోలిక క్షేత్రం");
        m.put("intake.ignore_col_type", "రకం");
        m.put("intake.ignore_col_additional_criteria", "అదనపు ప్రమాణాలు");
        m.put("intake.ignore_col_exception", "మినహాయింపు");
        m.put("intake.ignore_col_reason", "కారణం");
        m.put("intake.ignore_col_suppressed_count", "అణచివేయబడినవి");
        m.put("intake.ignore_col_suppressed_count_hint", "ఈ నియమం ఇప్పటికే అణచివేసిన ఇమెయిల్‌లు");
        m.put("intake.ignore_col_added_by", "జోడించినవారు");
        m.put("intake.ignore_col_status", "స్థితి");
        m.put("intake.ignore_col_actions", "చర్యలు");
        m.put("intake.ignore_load_failed", "అణచివేత నియమాలను లోడ్ చేయడం విఫలమైంది");
        m.put("intake.ignore_rule_created", "నియమం సృష్టించబడింది");
        m.put("intake.ignore_rule_create_failed", "నియమాన్ని సృష్టించడం విఫలమైంది");
        m.put("intake.ignore_rule_updated", "నియమం నవీకరించబడింది");
        m.put("intake.ignore_rule_update_failed", "నియమాన్ని నవీకరించడం విఫలమైంది");
        m.put("intake.ignore_rule_deleted", "నియమం తొలగించబడింది");
        m.put("intake.ignore_rule_delete_failed", "నియమాన్ని తొలగించడం విఫలమైంది");
        m.put("intake.ignore_rule_activated", "నియమం క్రియాశీలం చేయబడింది");
        m.put("intake.ignore_rule_deactivated", "నియమం నిష్క్రియం చేయబడింది");
        m.put("intake.ignored_emails_report_subtitle",
              "మినహాయింపు ఇమెయిల్ మాస్టర్ ద్వారా అణచివేయబడిన వచ్చే ఇమెయిల్‌లు");
        m.put("intake.ignored_emails_total", "మొత్తం అణచివేయబడినవి");
        m.put("intake.ignored_emails_empty", "ఇంకా ఏ ఇమెయిల్ అణచివేయబడలేదు");
        m.put("intake.ignored_emails_empty_filtered",
              "ఈ వడపోతలకు సరిపోలే అణచివేయబడిన ఇమెయిల్‌లు లేవు");
        m.put("intake.ignored_emails_load_failed", "అణచివేత నివేదికను లోడ్ చేయడం విఫలమైంది");
        m.put("intake.ignored_emails_export_failed", "CSV ఎగుమతి చేయడం విఫలమైంది");
        m.put("intake.ignored_filter_sender", "పంపినవారు");
        m.put("intake.ignored_filter_sender_placeholder", "పంపినవారి ఇమెయిల్‌లో ఉంది…");
        m.put("intake.ignored_filter_from_date", "ప్రారంభ తేదీ");
        m.put("intake.ignored_filter_to_date", "ముగింపు తేదీ");
        m.put("intake.ignored_filter_rule", "సరిపోలిన నియమం");
        m.put("intake.ignored_filter_all_rules", "అన్ని నియమాలు");
        m.put("intake.ignored_col_sender", "పంపినవారు");
        m.put("intake.ignored_col_subject", "విషయం");
        m.put("intake.ignored_col_received_at", "అందిన సమయం");
        m.put("intake.ignored_col_matched_rule", "సరిపోలిన నియమం");
        m.put("intake.ignored_col_matched_field", "సరిపోలిన క్షేత్రం");
        m.put("intake.ignored_col_matched_reason", "కారణం");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "இந்த மின்னஞ்சல் செயலில் உள்ள புறக்கணிப்பு விதியுடன் பொருந்தியது, எனவே எந்த முறையீட்டு வரைவும் "
              + "உருவாக்கப்படவில்லை.");
        m.put("intake.duplicate_delivery_ignored",
              "இந்தச் செய்தி ஏற்கனவே பெறப்பட்டு செயலாக்கப்பட்டுவிட்டது. ஏற்கனவே உள்ள வரைவு கீழே காட்டப்பட்டுள்ளது.");
        m.put("intake.duplicate_linked_to_parent",
              "இது இப்போது முடிக்கப்பட்ட ஒரு முந்தைய முறையீட்டின் நகல். புதிய வரைவை உருவாக்குவதற்குப் பதிலாக "
              + "மின்னஞ்சலும் அதன் இணைப்புகளும் அந்த முறையீட்டுடன் சேர்க்கப்பட்டுள்ளன.");
        m.put("intake.vernacular_manual_entry_required",
              "உள்ளடக்கம் ஒரு பிராந்திய மொழியில் உள்ளது, எனவே தானியங்கி உரை பிரித்தெடுத்தல் வேண்டுமென்றே "
              + "முயற்சிக்கப்படவில்லை. இது திறமையான தரவு-உள்ளீட்டு ஆபரேட்டருக்கு அனுப்பப்பட்டுள்ளது.");
        m.put("intake.ocr_low_confidence_manual_entry",
              "படிவத்தைத் தானாக நிரப்பும் அளவுக்கு ஸ்கேன் செய்யப்பட்ட ஆவணத்தை நம்பகமாகப் படிக்க முடியவில்லை. "
              + "விவரங்களைக் கைமுறையாக உள்ளிடவும்.");
        m.put("intake.manual_entry_required_banner",
              "கைமுறை உள்ளீடு தேவை — இந்த வரைவுக்குத் தானியங்கி உரை பிரித்தெடுத்தல் பயன்படுத்தப்படவில்லை.");
        m.put("intake.draft_not_found", "வரைவு கண்டறியப்படவில்லை.");
        m.put("intake.ignore_rule_not_found", "புறக்கணிப்பு விதி கண்டறியப்படவில்லை.");
        m.put("intake.suggested_related_invalid_decision",
              "முடிவு ஏற்கப்பட்டது அல்லது நிராகரிக்கப்பட்டது என்பதில் ஒன்றாக இருக்க வேண்டும்.");
        m.put("intake.attachment_too_large", "ஒவ்வொரு கோப்பும் {{size}}MB அல்லது அதற்குக் குறைவாக இருக்க வேண்டும்.");
        m.put("intake.attachment_total_too_large",
              "இணைப்புகளின் மொத்த அளவு 25MB அல்லது அதற்குக் குறைவாக இருக்க வேண்டும்.");
        m.put("intake.attachment_too_many", "நீங்கள் அதிகபட்சம் 10 கோப்புகளை இணைக்கலாம்.");
        m.put("intake.attachment_rejected",
              "கோப்பின் வகையை அதன் உள்ளடக்கத்திலிருந்து சரிபார்க்க முடியவில்லை, எனவே கோப்பு நிராகரிக்கப்பட்டது.");
        m.put("intake.ignored_emails_report_title", "புறக்கணிக்கப்பட்ட மின்னஞ்சல்கள் அறிக்கை");
        m.put("intake.ignored_emails_export_csv", "CSV ஆக ஏற்றுமதி செய்");
        m.put("intake.exceptional_email_master_title", "விதிவிலக்கு மின்னஞ்சல் முதன்மைப் பட்டியல்");
        m.put("intake.back_to_queue", "வரிசைக்குத் திரும்பு");
        m.put("intake.loading", "ஏற்றப்படுகிறது…");
        m.put("intake.cancel", "ரத்து செய்");
        m.put("intake.edit", "திருத்து");
        m.put("intake.delete", "நீக்கு");
        m.put("intake.search", "தேடு");
        m.put("intake.clear_filters", "வடிகட்டிகளை அழி");
        m.put("intake.exceptional_email_master_subtitle",
              "எந்த வரைவும் உருவாக்கப்படும் முன்பே வரும் மின்னஞ்சலை அடக்கும் விதிகள்");
        m.put("intake.ignore_add_rule", "விதியைச் சேர்");
        m.put("intake.ignore_edit_rule", "விதியைத் திருத்து");
        m.put("intake.ignore_create", "விதியை உருவாக்கு");
        m.put("intake.ignore_save_changes", "மாற்றங்களைச் சேமி");
        m.put("intake.ignore_no_rules", "எந்த அடக்கு விதியும் வரையறுக்கப்படவில்லை");
        m.put("intake.ignore_activate", "செயல்படுத்து");
        m.put("intake.ignore_deactivate", "செயலிழக்கச் செய்");
        m.put("intake.ignore_status_active", "செயலில்");
        m.put("intake.ignore_status_inactive", "செயலில் இல்லை");
        m.put("intake.ignore_active_label", "விதி செயலில் உள்ளது");
        m.put("intake.ignore_pattern_label", "வடிவம்");
        m.put("intake.ignore_pattern_placeholder", "எ.கா. noreply@bank.com அல்லது *@mailer.com");
        m.put("intake.ignore_pattern_hint", "தேர்ந்தெடுக்கப்பட்ட பொருத்தப் புலத்தில் சோதிக்கப்படுகிறது");
        m.put("intake.ignore_match_field_label", "பொருத்தப் புலம்");
        m.put("intake.ignore_match_field_hint", "வடிவம் மின்னஞ்சலின் எந்தத் தலைப்பில் சோதிக்கப்படும்");
        m.put("intake.ignore_pattern_type_label", "வடிவ வகை");
        m.put("intake.match_field_from", "அனுப்புநர்");
        m.put("intake.match_field_to", "பெறுநர்");
        m.put("intake.match_field_cc", "நகல்");
        m.put("intake.match_field_bcc", "மறை நகல்");
        m.put("intake.match_field_subject", "பொருள்");
        m.put("intake.pattern_type_exact", "சரியான");
        m.put("intake.pattern_type_domain", "களம்");
        m.put("intake.pattern_type_wildcard", "வைல்டுகார்டு");
        m.put("intake.pattern_type_contains", "உள்ளடக்கியது");
        m.put("intake.ignore_additional_criteria_heading", "கூடுதல் அளவுகோல்கள் (விருப்பத்திற்குரியது)");
        m.put("intake.ignore_additional_criteria_hint",
              "அமைக்கப்பட்டால், மின்னஞ்சல் அடக்கப்படுவதற்கு இவையும் பொருந்த வேண்டும் (AND தருக்கம்)");
        m.put("intake.ignore_to_pattern_label", "பெறுநர்");
        m.put("intake.ignore_cc_pattern_label", "நகல்");
        m.put("intake.ignore_bcc_pattern_label", "மறை நகல்");
        m.put("intake.ignore_subject_pattern_label", "பொருள்");
        m.put("intake.ignore_and_logic_badge", "அனைத்தும் பொருந்த வேண்டும்");
        m.put("intake.ignore_reason_label", "காரணம்");
        m.put("intake.ignore_reason_placeholder", "இதை ஏன் அடக்க வேண்டும்?");
        m.put("intake.ignore_exception_pattern_label", "விதிவிலக்கு வடிவம் (எதிர்-விதி)");
        m.put("intake.ignore_exception_pattern_placeholder", "எ.கா. grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "இது பொருந்தினால் விதி மீறப்பட்டு வரைவு உருவாக்கப்படும்");
        m.put("intake.ignore_semantics_heading", "அடக்கு விதிகள் எவ்வாறு செயல்படுகின்றன");
        m.put("intake.ignore_semantics_match_field",
              "வடிவம் எந்தத் தலைப்பில் சோதிக்கப்படும் என்பதைப் பொருத்தப் புலம் தீர்மானிக்கிறது "
              + "(இயல்புநிலை: அனுப்புநர்).");
        m.put("intake.ignore_semantics_and_logic",
              "பெறுநர்/நகல்/மறை நகல்/பொருள் ஆகியவை கூடுதல் அளவுகோல்கள் — அமைக்கப்பட்டால் அவையும் "
              + "பொருந்த வேண்டும்.");
        m.put("intake.ignore_semantics_exception",
              "விதிவிலக்கு வடிவம் ஒரு எதிர்-விதி: அது பொருந்தினால் அடக்குதல் மீறப்பட்டு வரைவு "
              + "உருவாக்கப்படும். ஒரு முழு களத்தையும் தடுத்து, பெயரிடப்பட்ட அனுப்புநர்களை மட்டும் "
              + "அனுமதிக்க இதைப் பயன்படுத்துங்கள்.");
        m.put("intake.ignore_semantics_inactive", "செயலில் இல்லாத விதி எதையும் அடக்குவதில்லை.");
        m.put("intake.ignore_col_pattern", "வடிவம்");
        m.put("intake.ignore_col_match_field", "பொருத்தப் புலம்");
        m.put("intake.ignore_col_type", "வகை");
        m.put("intake.ignore_col_additional_criteria", "கூடுதல் அளவுகோல்கள்");
        m.put("intake.ignore_col_exception", "விதிவிலக்கு");
        m.put("intake.ignore_col_reason", "காரணம்");
        m.put("intake.ignore_col_suppressed_count", "அடக்கப்பட்டவை");
        m.put("intake.ignore_col_suppressed_count_hint",
              "இந்த விதி ஏற்கனவே அடக்கிய மின்னஞ்சல்கள்");
        m.put("intake.ignore_col_added_by", "சேர்த்தவர்");
        m.put("intake.ignore_col_status", "நிலை");
        m.put("intake.ignore_col_actions", "செயல்கள்");
        m.put("intake.ignore_load_failed", "அடக்கு விதிகளை ஏற்ற முடியவில்லை");
        m.put("intake.ignore_rule_created", "விதி உருவாக்கப்பட்டது");
        m.put("intake.ignore_rule_create_failed", "விதியை உருவாக்க முடியவில்லை");
        m.put("intake.ignore_rule_updated", "விதி புதுப்பிக்கப்பட்டது");
        m.put("intake.ignore_rule_update_failed", "விதியைப் புதுப்பிக்க முடியவில்லை");
        m.put("intake.ignore_rule_deleted", "விதி நீக்கப்பட்டது");
        m.put("intake.ignore_rule_delete_failed", "விதியை நீக்க முடியவில்லை");
        m.put("intake.ignore_rule_activated", "விதி செயல்படுத்தப்பட்டது");
        m.put("intake.ignore_rule_deactivated", "விதி செயலிழக்கச் செய்யப்பட்டது");
        m.put("intake.ignored_emails_report_subtitle",
              "விதிவிலக்கு மின்னஞ்சல் முதன்மைப் பட்டியலால் அடக்கப்பட்ட வரும் மின்னஞ்சல்கள்");
        m.put("intake.ignored_emails_total", "மொத்தம் அடக்கப்பட்டவை");
        m.put("intake.ignored_emails_empty", "இன்னும் எந்த மின்னஞ்சலும் அடக்கப்படவில்லை");
        m.put("intake.ignored_emails_empty_filtered",
              "இந்த வடிகட்டிகளுக்குப் பொருந்தும் அடக்கப்பட்ட மின்னஞ்சல்கள் இல்லை");
        m.put("intake.ignored_emails_load_failed", "அடக்குதல் அறிக்கையை ஏற்ற முடியவில்லை");
        m.put("intake.ignored_emails_export_failed", "CSV ஏற்றுமதி செய்ய முடியவில்லை");
        m.put("intake.ignored_filter_sender", "அனுப்புநர்");
        m.put("intake.ignored_filter_sender_placeholder", "அனுப்புநரின் மின்னஞ்சலில் உள்ளது…");
        m.put("intake.ignored_filter_from_date", "தொடக்க நாள்");
        m.put("intake.ignored_filter_to_date", "இறுதி நாள்");
        m.put("intake.ignored_filter_rule", "பொருந்திய விதி");
        m.put("intake.ignored_filter_all_rules", "அனைத்து விதிகள்");
        m.put("intake.ignored_col_sender", "அனுப்புநர்");
        m.put("intake.ignored_col_subject", "பொருள்");
        m.put("intake.ignored_col_received_at", "பெறப்பட்ட நேரம்");
        m.put("intake.ignored_col_matched_rule", "பொருந்திய விதி");
        m.put("intake.ignored_col_matched_field", "பொருந்திய புலம்");
        m.put("intake.ignored_col_matched_reason", "காரணம்");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "આ ઈમેલ સક્રિય અવગણના નિયમ સાથે મેળ ખાય છે, તેથી કોઈ ફરિયાદ ડ્રાફ્ટ બનાવવામાં આવ્યો નથી.");
        m.put("intake.duplicate_delivery_ignored",
              "આ સંદેશ પહેલેથી જ પ્રાપ્ત થઈને પ્રક્રિયામાં લેવાયો છે. વર્તમાન ડ્રાફ્ટ નીચે બતાવેલ છે.");
        m.put("intake.duplicate_linked_to_parent",
              "આ અગાઉની એક ફરિયાદની નકલ છે જે હવે બંધ થઈ ગઈ છે. નવો ડ્રાફ્ટ બનાવવાને બદલે ઈમેલ અને તેના "
              + "જોડાણો તે જ ફરિયાદમાં ઉમેરવામાં આવ્યાં છે.");
        m.put("intake.vernacular_manual_entry_required",
              "સામગ્રી પ્રાદેશિક ભાષામાં છે, તેથી સ્વયંસંચાલિત લખાણ નિષ્કર્ષણ જાણીજોઈને કરવામાં આવ્યું નથી. "
              + "તેને કુશળ ડેટા-એન્ટ્રી ઓપરેટરને મોકલવામાં આવ્યું છે.");
        m.put("intake.ocr_low_confidence_manual_entry",
              "સ્કેન કરેલો દસ્તાવેજ ફોર્મ સ્વયં ભરી શકાય એટલી વિશ્વસનીયતાથી વાંચી શકાયો નથી. કૃપા કરીને "
              + "વિગતો હાથે દાખલ કરો.");
        m.put("intake.manual_entry_required_banner",
              "હાથે નોંધ કરવી જરૂરી — આ ડ્રાફ્ટ માટે સ્વયંસંચાલિત લખાણ નિષ્કર્ષણ વપરાયું નથી.");
        m.put("intake.draft_not_found", "ડ્રાફ્ટ મળ્યો નથી.");
        m.put("intake.ignore_rule_not_found", "અવગણના નિયમ મળ્યો નથી.");
        m.put("intake.suggested_related_invalid_decision",
              "નિર્ણય સ્વીકૃત અથવા નકારેલ હોવો જોઈએ.");
        m.put("intake.attachment_too_large", "દરેક ફાઈલ {{size}}MB કે તેથી નાની હોવી જોઈએ.");
        m.put("intake.attachment_total_too_large", "જોડાણોનું કુલ કદ 25MB કે તેથી ઓછું હોવું જોઈએ.");
        m.put("intake.attachment_too_many", "તમે વધુમાં વધુ 10 ફાઈલો જોડી શકો છો.");
        m.put("intake.attachment_rejected",
              "ફાઈલનો પ્રકાર તેની અંદરની સામગ્રી પરથી ચકાસી શકાયો નથી, તેથી ફાઈલ નકારવામાં આવી.");
        m.put("intake.ignored_emails_report_title", "અવગણાયેલા ઈમેલનો અહેવાલ");
        m.put("intake.ignored_emails_export_csv", "CSV માં નિકાસ કરો");
        m.put("intake.exceptional_email_master_title", "અપવાદરૂપ ઈમેલ માસ્ટર");
        m.put("intake.back_to_queue", "કતારમાં પાછા જાઓ");
        m.put("intake.loading", "લોડ થઈ રહ્યું છે…");
        m.put("intake.cancel", "રદ કરો");
        m.put("intake.edit", "સંપાદિત કરો");
        m.put("intake.delete", "કાઢી નાખો");
        m.put("intake.search", "શોધો");
        m.put("intake.clear_filters", "ગાળકો સાફ કરો");
        m.put("intake.exceptional_email_master_subtitle",
              "કોઈ ડ્રાફ્ટ બને તે પહેલાં જ આવતા ઈમેલને દબાવતા નિયમો");
        m.put("intake.ignore_add_rule", "નિયમ ઉમેરો");
        m.put("intake.ignore_edit_rule", "નિયમ સંપાદિત કરો");
        m.put("intake.ignore_create", "નિયમ બનાવો");
        m.put("intake.ignore_save_changes", "ફેરફારો સાચવો");
        m.put("intake.ignore_no_rules", "કોઈ દમન નિયમ નિર્ધારિત નથી");
        m.put("intake.ignore_activate", "સક્રિય કરો");
        m.put("intake.ignore_deactivate", "નિષ્ક્રિય કરો");
        m.put("intake.ignore_status_active", "સક્રિય");
        m.put("intake.ignore_status_inactive", "નિષ્ક્રિય");
        m.put("intake.ignore_active_label", "નિયમ સક્રિય છે");
        m.put("intake.ignore_pattern_label", "ભાત");
        m.put("intake.ignore_pattern_placeholder", "ઉદા. noreply@bank.com અથવા *@mailer.com");
        m.put("intake.ignore_pattern_hint", "પસંદ કરેલા મેળ ક્ષેત્ર સામે તપાસવામાં આવે છે");
        m.put("intake.ignore_match_field_label", "મેળ ક્ષેત્ર");
        m.put("intake.ignore_match_field_hint", "ભાત ઈમેલના કયા શીર્ષક સામે તપાસવામાં આવે");
        m.put("intake.ignore_pattern_type_label", "ભાતનો પ્રકાર");
        m.put("intake.match_field_from", "મોકલનાર");
        m.put("intake.match_field_to", "મેળવનાર");
        m.put("intake.match_field_cc", "નકલ");
        m.put("intake.match_field_bcc", "ગુપ્ત નકલ");
        m.put("intake.match_field_subject", "વિષય");
        m.put("intake.pattern_type_exact", "તદ્દન સરખું");
        m.put("intake.pattern_type_domain", "ડોમેન");
        m.put("intake.pattern_type_wildcard", "વાઈલ્ડકાર્ડ");
        m.put("intake.pattern_type_contains", "સમાવે છે");
        m.put("intake.ignore_additional_criteria_heading", "વધારાના માપદંડ (વૈકલ્પિક)");
        m.put("intake.ignore_additional_criteria_hint",
              "નિર્ધારિત હોય ત્યારે ઈમેલ દબાવવા માટે આ પણ મેળ ખાવા જોઈએ (AND તર્ક)");
        m.put("intake.ignore_to_pattern_label", "મેળવનાર");
        m.put("intake.ignore_cc_pattern_label", "નકલ");
        m.put("intake.ignore_bcc_pattern_label", "ગુપ્ત નકલ");
        m.put("intake.ignore_subject_pattern_label", "વિષય");
        m.put("intake.ignore_and_logic_badge", "બધા મેળ ખાવા જોઈએ");
        m.put("intake.ignore_reason_label", "કારણ");
        m.put("intake.ignore_reason_placeholder", "આને કેમ દબાવવું જોઈએ?");
        m.put("intake.ignore_exception_pattern_label", "અપવાદ ભાત (પ્રતિ-નિયમ)");
        m.put("intake.ignore_exception_pattern_placeholder", "ઉદા. grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "જો આ મેળ ખાય તો નિયમ રદ થાય છે અને ડ્રાફ્ટ બનાવવામાં આવે છે");
        m.put("intake.ignore_semantics_heading", "દમન નિયમો કેવી રીતે કામ કરે છે");
        m.put("intake.ignore_semantics_match_field",
              "ભાત કયા શીર્ષક સામે તપાસવો તે મેળ ક્ષેત્ર નક્કી કરે છે (પૂર્વનિર્ધારિત: મોકલનાર).");
        m.put("intake.ignore_semantics_and_logic",
              "મેળવનાર/નકલ/ગુપ્ત નકલ/વિષય વધારાના માપદંડ છે — નિર્ધારિત હોય ત્યારે તે પણ મેળ ખાવા જોઈએ.");
        m.put("intake.ignore_semantics_exception",
              "અપવાદ ભાત એક પ્રતિ-નિયમ છે: જો તે મેળ ખાય તો દમન રદ થાય છે અને ડ્રાફ્ટ બને છે. આખા "
              + "ડોમેનને રોકીને પણ નામ આપેલા મોકલનારને પ્રવેશ આપવા આનો ઉપયોગ કરો.");
        m.put("intake.ignore_semantics_inactive", "નિષ્ક્રિય નિયમ કંઈ પણ દબાવતો નથી.");
        m.put("intake.ignore_col_pattern", "ભાત");
        m.put("intake.ignore_col_match_field", "મેળ ક્ષેત્ર");
        m.put("intake.ignore_col_type", "પ્રકાર");
        m.put("intake.ignore_col_additional_criteria", "વધારાના માપદંડ");
        m.put("intake.ignore_col_exception", "અપવાદ");
        m.put("intake.ignore_col_reason", "કારણ");
        m.put("intake.ignore_col_suppressed_count", "દબાવેલા");
        m.put("intake.ignore_col_suppressed_count_hint", "આ નિયમે અત્યાર સુધી દબાવેલા ઈમેલ");
        m.put("intake.ignore_col_added_by", "ઉમેરનાર");
        m.put("intake.ignore_col_status", "સ્થિતિ");
        m.put("intake.ignore_col_actions", "ક્રિયાઓ");
        m.put("intake.ignore_load_failed", "દમન નિયમો લોડ કરવામાં નિષ્ફળ");
        m.put("intake.ignore_rule_created", "નિયમ બનાવવામાં આવ્યો");
        m.put("intake.ignore_rule_create_failed", "નિયમ બનાવવામાં નિષ્ફળ");
        m.put("intake.ignore_rule_updated", "નિયમ સુધારવામાં આવ્યો");
        m.put("intake.ignore_rule_update_failed", "નિયમ સુધારવામાં નિષ્ફળ");
        m.put("intake.ignore_rule_deleted", "નિયમ કાઢી નાખવામાં આવ્યો");
        m.put("intake.ignore_rule_delete_failed", "નિયમ કાઢી નાખવામાં નિષ્ફળ");
        m.put("intake.ignore_rule_activated", "નિયમ સક્રિય કરવામાં આવ્યો");
        m.put("intake.ignore_rule_deactivated", "નિયમ નિષ્ક્રિય કરવામાં આવ્યો");
        m.put("intake.ignored_emails_report_subtitle",
              "અપવાદરૂપ ઈમેલ માસ્ટર દ્વારા દબાવવામાં આવેલા આવતા ઈમેલ");
        m.put("intake.ignored_emails_total", "કુલ દબાવેલા");
        m.put("intake.ignored_emails_empty", "હજુ સુધી કોઈ ઈમેલ દબાવવામાં આવ્યો નથી");
        m.put("intake.ignored_emails_empty_filtered", "આ ગાળકો સાથે મેળ ખાતો કોઈ દબાવેલો ઈમેલ નથી");
        m.put("intake.ignored_emails_load_failed", "દમન અહેવાલ લોડ કરવામાં નિષ્ફળ");
        m.put("intake.ignored_emails_export_failed", "CSV નિકાસ કરવામાં નિષ્ફળ");
        m.put("intake.ignored_filter_sender", "મોકલનાર");
        m.put("intake.ignored_filter_sender_placeholder", "મોકલનારના ઈમેલમાં સમાયેલું છે…");
        m.put("intake.ignored_filter_from_date", "શરૂ તારીખ");
        m.put("intake.ignored_filter_to_date", "અંતિમ તારીખ");
        m.put("intake.ignored_filter_rule", "મેળ ખાધેલો નિયમ");
        m.put("intake.ignored_filter_all_rules", "બધા નિયમો");
        m.put("intake.ignored_col_sender", "મોકલનાર");
        m.put("intake.ignored_col_subject", "વિષય");
        m.put("intake.ignored_col_received_at", "મળ્યાનો સમય");
        m.put("intake.ignored_col_matched_rule", "મેળ ખાધેલો નિયમ");
        m.put("intake.ignored_col_matched_field", "મેળ ખાધેલું ક્ષેત્ર");
        m.put("intake.ignored_col_matched_reason", "કારણ");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "یہ ای میل ایک فعال نظر انداز قاعدے سے مطابقت رکھتی ہے، اس لیے کوئی شکایتی مسودہ نہیں بنایا گیا۔");
        m.put("intake.duplicate_delivery_ignored",
              "یہ پیغام پہلے ہی موصول اور کارروائی میں لایا جا چکا ہے۔ موجودہ مسودہ نیچے دکھایا گیا ہے۔");
        m.put("intake.duplicate_linked_to_parent",
              "یہ ایک سابقہ شکایت کی نقل ہے جو اب بند ہو چکی ہے۔ نیا مسودہ بنانے کے بجائے ای میل اور اس کے "
              + "منسلکات اسی موجودہ شکایت میں شامل کر دیے گئے ہیں۔");
        m.put("intake.vernacular_manual_entry_required",
              "مواد ایک علاقائی زبان میں ہے، اس لیے خودکار متن کشید کرنے کی دانستہ کوشش نہیں کی گئی۔ اسے "
              + "ایک ماہر ڈیٹا اینٹری آپریٹر کو بھیج دیا گیا ہے۔");
        m.put("intake.ocr_low_confidence_manual_entry",
              "اسکین کردہ دستاویز اتنے بھروسے سے نہیں پڑھی جا سکی کہ فارم خود بخود بھرا جا سکے۔ براہ کرم "
              + "تفصیلات ہاتھ سے درج کریں۔");
        m.put("intake.manual_entry_required_banner",
              "ہاتھ سے اندراج درکار — اس مسودے کے لیے خودکار متن کشید کرنا استعمال نہیں کیا گیا۔");
        m.put("intake.draft_not_found", "مسودہ نہیں ملا۔");
        m.put("intake.ignore_rule_not_found", "نظر انداز قاعدہ نہیں ملا۔");
        m.put("intake.suggested_related_invalid_decision",
              "فیصلہ منظور شدہ یا مسترد شدہ ہونا چاہیے۔");
        m.put("intake.attachment_too_large", "ہر فائل {{size}} ایم بی یا اس سے کم ہونی چاہیے۔");
        m.put("intake.attachment_total_too_large", "منسلکات کا کل حجم ۲۵ ایم بی یا اس سے کم ہونا چاہیے۔");
        m.put("intake.attachment_too_many", "آپ زیادہ سے زیادہ ۱۰ فائلیں منسلک کر سکتے ہیں۔");
        m.put("intake.attachment_rejected",
              "فائل کی قسم اس کے مندرجات سے تصدیق نہیں ہو سکی، اس لیے فائل مسترد کر دی گئی۔");
        m.put("intake.ignored_emails_report_title", "نظر انداز کی گئی ای میلوں کی رپورٹ");
        m.put("intake.ignored_emails_export_csv", "سی ایس وی میں برآمد کریں");
        m.put("intake.exceptional_email_master_title", "استثنائی ای میل ماسٹر");
        m.put("intake.back_to_queue", "قطار پر واپس جائیں");
        m.put("intake.loading", "لوڈ ہو رہا ہے…");
        m.put("intake.cancel", "منسوخ کریں");
        m.put("intake.edit", "ترمیم کریں");
        m.put("intake.delete", "حذف کریں");
        m.put("intake.search", "تلاش کریں");
        m.put("intake.clear_filters", "فلٹر ہٹا دیں");
        m.put("intake.exceptional_email_master_subtitle",
              "وہ قواعد جو کوئی مسودہ بننے سے پہلے ہی آنے والی ای میل کو دبا دیتے ہیں");
        m.put("intake.ignore_add_rule", "قاعدہ شامل کریں");
        m.put("intake.ignore_edit_rule", "قاعدے میں ترمیم کریں");
        m.put("intake.ignore_create", "قاعدہ بنائیں");
        m.put("intake.ignore_save_changes", "تبدیلیاں محفوظ کریں");
        m.put("intake.ignore_no_rules", "کوئی دباؤ قاعدہ متعین نہیں ہے");
        m.put("intake.ignore_activate", "فعال کریں");
        m.put("intake.ignore_deactivate", "غیر فعال کریں");
        m.put("intake.ignore_status_active", "فعال");
        m.put("intake.ignore_status_inactive", "غیر فعال");
        m.put("intake.ignore_active_label", "قاعدہ فعال ہے");
        m.put("intake.ignore_pattern_label", "نمونہ");
        m.put("intake.ignore_pattern_placeholder", "مثلاً noreply@bank.com یا *@mailer.com");
        m.put("intake.ignore_pattern_hint", "منتخب مماثلت خانے کے مقابل جانچا جاتا ہے");
        m.put("intake.ignore_match_field_label", "مماثلت خانہ");
        m.put("intake.ignore_match_field_hint", "نمونہ ای میل کے کس سرنامے پر جانچا جائے");
        m.put("intake.ignore_pattern_type_label", "نمونے کی قسم");
        m.put("intake.match_field_from", "بھیجنے والا");
        m.put("intake.match_field_to", "وصول کنندہ");
        m.put("intake.match_field_cc", "نقل");
        m.put("intake.match_field_bcc", "پوشیدہ نقل");
        m.put("intake.match_field_subject", "موضوع");
        m.put("intake.pattern_type_exact", "بعینہٖ");
        m.put("intake.pattern_type_domain", "ڈومین");
        m.put("intake.pattern_type_wildcard", "وائلڈ کارڈ");
        m.put("intake.pattern_type_contains", "شامل ہے");
        m.put("intake.ignore_additional_criteria_heading", "اضافی معیار (اختیاری)");
        m.put("intake.ignore_additional_criteria_hint",
              "متعین ہونے پر ای میل دبانے کے لیے ان کی بھی مماثلت لازمی ہے (AND منطق)");
        m.put("intake.ignore_to_pattern_label", "وصول کنندہ");
        m.put("intake.ignore_cc_pattern_label", "نقل");
        m.put("intake.ignore_bcc_pattern_label", "پوشیدہ نقل");
        m.put("intake.ignore_subject_pattern_label", "موضوع");
        m.put("intake.ignore_and_logic_badge", "سب کی مماثلت لازمی");
        m.put("intake.ignore_reason_label", "وجہ");
        m.put("intake.ignore_reason_placeholder", "اسے کیوں دبایا جانا چاہیے؟");
        m.put("intake.ignore_exception_pattern_label", "استثنائی نمونہ (جوابی قاعدہ)");
        m.put("intake.ignore_exception_pattern_placeholder", "مثلاً grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "اگر یہ مطابقت رکھے تو قاعدہ منسوخ ہو جاتا ہے اور مسودہ بنایا جاتا ہے");
        m.put("intake.ignore_semantics_heading", "دباؤ قواعد کیسے کام کرتے ہیں");
        m.put("intake.ignore_semantics_match_field",
              "مماثلت خانہ طے کرتا ہے کہ نمونہ کس سرنامے پر جانچا جائے (طے شدہ: بھیجنے والا)۔");
        m.put("intake.ignore_semantics_and_logic",
              "وصول کنندہ/نقل/پوشیدہ نقل/موضوع اضافی معیار ہیں — متعین ہونے پر ان کی بھی مماثلت لازمی ہے۔");
        m.put("intake.ignore_semantics_exception",
              "استثنائی نمونہ ایک جوابی قاعدہ ہے: اگر یہ مطابقت رکھے تو دباؤ منسوخ ہو جاتا ہے اور مسودہ "
              + "بن جاتا ہے۔ پورے ڈومین کو روکنے کے باوجود مخصوص بھیجنے والوں کو اجازت دینے کے لیے اسے "
              + "استعمال کریں۔");
        m.put("intake.ignore_semantics_inactive", "غیر فعال قاعدہ کچھ بھی نہیں دباتا۔");
        m.put("intake.ignore_col_pattern", "نمونہ");
        m.put("intake.ignore_col_match_field", "مماثلت خانہ");
        m.put("intake.ignore_col_type", "قسم");
        m.put("intake.ignore_col_additional_criteria", "اضافی معیار");
        m.put("intake.ignore_col_exception", "استثنا");
        m.put("intake.ignore_col_reason", "وجہ");
        m.put("intake.ignore_col_suppressed_count", "دبائی گئیں");
        m.put("intake.ignore_col_suppressed_count_hint", "اس قاعدے نے اب تک جو ای میلیں دبائی ہیں");
        m.put("intake.ignore_col_added_by", "شامل کرنے والا");
        m.put("intake.ignore_col_status", "حالت");
        m.put("intake.ignore_col_actions", "اقدامات");
        m.put("intake.ignore_load_failed", "دباؤ قواعد لوڈ کرنے میں ناکامی");
        m.put("intake.ignore_rule_created", "قاعدہ بنا دیا گیا");
        m.put("intake.ignore_rule_create_failed", "قاعدہ بنانے میں ناکامی");
        m.put("intake.ignore_rule_updated", "قاعدہ تجدید کر دیا گیا");
        m.put("intake.ignore_rule_update_failed", "قاعدہ تجدید کرنے میں ناکامی");
        m.put("intake.ignore_rule_deleted", "قاعدہ حذف کر دیا گیا");
        m.put("intake.ignore_rule_delete_failed", "قاعدہ حذف کرنے میں ناکامی");
        m.put("intake.ignore_rule_activated", "قاعدہ فعال کر دیا گیا");
        m.put("intake.ignore_rule_deactivated", "قاعدہ غیر فعال کر دیا گیا");
        m.put("intake.ignored_emails_report_subtitle",
              "استثنائی ای میل ماسٹر کے ذریعے دبائی گئی آنے والی ای میلیں");
        m.put("intake.ignored_emails_total", "کل دبائی گئیں");
        m.put("intake.ignored_emails_empty", "ابھی تک کوئی ای میل نہیں دبائی گئی");
        m.put("intake.ignored_emails_empty_filtered",
              "ان فلٹروں سے مطابقت رکھنے والی کوئی دبائی گئی ای میل نہیں");
        m.put("intake.ignored_emails_load_failed", "دباؤ رپورٹ لوڈ کرنے میں ناکامی");
        m.put("intake.ignored_emails_export_failed", "سی ایس وی برآمد کرنے میں ناکامی");
        m.put("intake.ignored_filter_sender", "بھیجنے والا");
        m.put("intake.ignored_filter_sender_placeholder", "بھیجنے والے کی ای میل میں شامل ہے…");
        m.put("intake.ignored_filter_from_date", "آغاز کی تاریخ");
        m.put("intake.ignored_filter_to_date", "اختتام کی تاریخ");
        m.put("intake.ignored_filter_rule", "مطابقت رکھنے والا قاعدہ");
        m.put("intake.ignored_filter_all_rules", "تمام قواعد");
        m.put("intake.ignored_col_sender", "بھیجنے والا");
        m.put("intake.ignored_col_subject", "موضوع");
        m.put("intake.ignored_col_received_at", "موصولی کا وقت");
        m.put("intake.ignored_col_matched_rule", "مطابقت رکھنے والا قاعدہ");
        m.put("intake.ignored_col_matched_field", "مطابقت رکھنے والا خانہ");
        m.put("intake.ignored_col_matched_reason", "وجہ");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "ಈ ಇಮೇಲ್ ಸಕ್ರಿಯ ನಿರ್ಲಕ್ಷ್ಯ ನಿಯಮಕ್ಕೆ ಹೊಂದಿಕೆಯಾಗಿದೆ, ಆದ್ದರಿಂದ ಯಾವುದೇ ದೂರಿನ ಕರಡು ರಚಿಸಲಾಗಿಲ್ಲ.");
        m.put("intake.duplicate_delivery_ignored",
              "ಈ ಸಂದೇಶವನ್ನು ಈಗಾಗಲೇ ಸ್ವೀಕರಿಸಿ ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲಾಗಿದೆ. ಅಸ್ತಿತ್ವದಲ್ಲಿರುವ ಕರಡನ್ನು ಕೆಳಗೆ ತೋರಿಸಲಾಗಿದೆ.");
        m.put("intake.duplicate_linked_to_parent",
              "ಇದು ಈಗ ಮುಕ್ತಾಯಗೊಂಡಿರುವ ಹಿಂದಿನ ದೂರಿನ ನಕಲು. ಹೊಸ ಕರಡು ರಚಿಸುವ ಬದಲು ಇಮೇಲ್ ಮತ್ತು ಅದರ "
              + "ಲಗತ್ತುಗಳನ್ನು ಅದೇ ದೂರಿಗೆ ಸೇರಿಸಲಾಗಿದೆ.");
        m.put("intake.vernacular_manual_entry_required",
              "ವಿಷಯವು ಪ್ರಾದೇಶಿಕ ಭಾಷೆಯಲ್ಲಿದೆ, ಆದ್ದರಿಂದ ಸ್ವಯಂಚಾಲಿತ ಪಠ್ಯ ಹೊರತೆಗೆಯುವಿಕೆಯನ್ನು ಉದ್ದೇಶಪೂರ್ವಕವಾಗಿ "
              + "ಪ್ರಯತ್ನಿಸಲಾಗಿಲ್ಲ. ಇದನ್ನು ನುರಿತ ದತ್ತಾಂಶ-ನಮೂದು ನಿರ್ವಾಹಕರಿಗೆ ಕಳುಹಿಸಲಾಗಿದೆ.");
        m.put("intake.ocr_low_confidence_manual_entry",
              "ನಮೂನೆಯನ್ನು ಸ್ವಯಂಚಾಲಿತವಾಗಿ ತುಂಬುವಷ್ಟು ವಿಶ್ವಾಸಾರ್ಹವಾಗಿ ಸ್ಕ್ಯಾನ್ ಮಾಡಿದ ದಾಖಲೆಯನ್ನು ಓದಲಾಗಿಲ್ಲ. "
              + "ದಯವಿಟ್ಟು ವಿವರಗಳನ್ನು ಕೈಯಾರೆ ನಮೂದಿಸಿ.");
        m.put("intake.manual_entry_required_banner",
              "ಕೈಯಾರೆ ನಮೂದು ಅಗತ್ಯ — ಈ ಕರಡಿಗೆ ಸ್ವಯಂಚಾಲಿತ ಪಠ್ಯ ಹೊರತೆಗೆಯುವಿಕೆಯನ್ನು ಬಳಸಲಾಗಿಲ್ಲ.");
        m.put("intake.draft_not_found", "ಕರಡು ಕಂಡುಬಂದಿಲ್ಲ.");
        m.put("intake.ignore_rule_not_found", "ನಿರ್ಲಕ್ಷ್ಯ ನಿಯಮ ಕಂಡುಬಂದಿಲ್ಲ.");
        m.put("intake.suggested_related_invalid_decision",
              "ನಿರ್ಧಾರವು ಸ್ವೀಕರಿಸಲಾಗಿದೆ ಅಥವಾ ತಿರಸ್ಕರಿಸಲಾಗಿದೆ ಎಂಬುದರಲ್ಲಿ ಒಂದಾಗಿರಬೇಕು.");
        m.put("intake.attachment_too_large", "ಪ್ರತಿ ಕಡತ {{size}}MB ಅಥವಾ ಅದಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು.");
        m.put("intake.attachment_total_too_large",
              "ಲಗತ್ತುಗಳ ಒಟ್ಟು ಗಾತ್ರ 25MB ಅಥವಾ ಅದಕ್ಕಿಂತ ಕಡಿಮೆ ಇರಬೇಕು.");
        m.put("intake.attachment_too_many", "ನೀವು ಗರಿಷ್ಠ 10 ಕಡತಗಳನ್ನು ಲಗತ್ತಿಸಬಹುದು.");
        m.put("intake.attachment_rejected",
              "ಕಡತದ ಪ್ರಕಾರವನ್ನು ಅದರ ಒಳವಿಷಯದಿಂದ ಪರಿಶೀಲಿಸಲಾಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಕಡತವನ್ನು ತಿರಸ್ಕರಿಸಲಾಗಿದೆ.");
        m.put("intake.ignored_emails_report_title", "ನಿರ್ಲಕ್ಷಿಸಿದ ಇಮೇಲ್‌ಗಳ ವರದಿ");
        m.put("intake.ignored_emails_export_csv", "CSV ಗೆ ರಫ್ತು ಮಾಡಿ");
        m.put("intake.exceptional_email_master_title", "ಅಪವಾದ ಇಮೇಲ್ ಮಾಸ್ಟರ್");
        m.put("intake.back_to_queue", "ಸರತಿಗೆ ಹಿಂತಿರುಗಿ");
        m.put("intake.loading", "ಲೋಡ್ ಆಗುತ್ತಿದೆ…");
        m.put("intake.cancel", "ರದ್ದುಗೊಳಿಸಿ");
        m.put("intake.edit", "ಸಂಪಾದಿಸಿ");
        m.put("intake.delete", "ಅಳಿಸಿ");
        m.put("intake.search", "ಹುಡುಕಿ");
        m.put("intake.clear_filters", "ಶೋಧಕಗಳನ್ನು ತೆರವುಗೊಳಿಸಿ");
        m.put("intake.exceptional_email_master_subtitle",
              "ಯಾವುದೇ ಕರಡು ರಚನೆಯಾಗುವ ಮೊದಲೇ ಒಳಬರುವ ಇಮೇಲ್‌ಅನ್ನು ತಡೆಯುವ ನಿಯಮಗಳು");
        m.put("intake.ignore_add_rule", "ನಿಯಮ ಸೇರಿಸಿ");
        m.put("intake.ignore_edit_rule", "ನಿಯಮ ಸಂಪಾದಿಸಿ");
        m.put("intake.ignore_create", "ನಿಯಮ ರಚಿಸಿ");
        m.put("intake.ignore_save_changes", "ಬದಲಾವಣೆಗಳನ್ನು ಉಳಿಸಿ");
        m.put("intake.ignore_no_rules", "ಯಾವುದೇ ತಡೆ ನಿಯಮಗಳನ್ನು ನಿಗದಿಪಡಿಸಿಲ್ಲ");
        m.put("intake.ignore_activate", "ಸಕ್ರಿಯಗೊಳಿಸಿ");
        m.put("intake.ignore_deactivate", "ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಿ");
        m.put("intake.ignore_status_active", "ಸಕ್ರಿಯ");
        m.put("intake.ignore_status_inactive", "ನಿಷ್ಕ್ರಿಯ");
        m.put("intake.ignore_active_label", "ನಿಯಮ ಸಕ್ರಿಯವಾಗಿದೆ");
        m.put("intake.ignore_pattern_label", "ಮಾದರಿ");
        m.put("intake.ignore_pattern_placeholder", "ಉದಾ. noreply@bank.com ಅಥವಾ *@mailer.com");
        m.put("intake.ignore_pattern_hint", "ಆಯ್ದ ಹೊಂದಿಕೆ ಕ್ಷೇತ್ರದ ವಿರುದ್ಧ ಪರೀಕ್ಷಿಸಲಾಗುತ್ತದೆ");
        m.put("intake.ignore_match_field_label", "ಹೊಂದಿಕೆ ಕ್ಷೇತ್ರ");
        m.put("intake.ignore_match_field_hint", "ಮಾದರಿಯನ್ನು ಇಮೇಲ್‌ನ ಯಾವ ಶೀರ್ಷಿಕೆಯಲ್ಲಿ ಪರೀಕ್ಷಿಸಬೇಕು");
        m.put("intake.ignore_pattern_type_label", "ಮಾದರಿಯ ಪ್ರಕಾರ");
        m.put("intake.match_field_from", "ಕಳುಹಿಸಿದವರು");
        m.put("intake.match_field_to", "ಸ್ವೀಕರಿಸುವವರು");
        m.put("intake.match_field_cc", "ಪ್ರತಿ");
        m.put("intake.match_field_bcc", "ಗುಪ್ತ ಪ್ರತಿ");
        m.put("intake.match_field_subject", "ವಿಷಯ");
        m.put("intake.pattern_type_exact", "ನಿಖರ");
        m.put("intake.pattern_type_domain", "ಡೊಮೇನ್");
        m.put("intake.pattern_type_wildcard", "ವೈಲ್ಡ್‌ಕಾರ್ಡ್");
        m.put("intake.pattern_type_contains", "ಒಳಗೊಂಡಿದೆ");
        m.put("intake.ignore_additional_criteria_heading", "ಹೆಚ್ಚುವರಿ ಮಾನದಂಡಗಳು (ಐಚ್ಛಿಕ)");
        m.put("intake.ignore_additional_criteria_hint",
              "ನಿಗದಿಪಡಿಸಿದಾಗ ಇಮೇಲ್ ತಡೆಯಲು ಇವು ಸಹ ಹೊಂದಿಕೆಯಾಗಬೇಕು (AND ತರ್ಕ)");
        m.put("intake.ignore_to_pattern_label", "ಸ್ವೀಕರಿಸುವವರು");
        m.put("intake.ignore_cc_pattern_label", "ಪ್ರತಿ");
        m.put("intake.ignore_bcc_pattern_label", "ಗುಪ್ತ ಪ್ರತಿ");
        m.put("intake.ignore_subject_pattern_label", "ವಿಷಯ");
        m.put("intake.ignore_and_logic_badge", "ಎಲ್ಲವೂ ಹೊಂದಿಕೆಯಾಗಬೇಕು");
        m.put("intake.ignore_reason_label", "ಕಾರಣ");
        m.put("intake.ignore_reason_placeholder", "ಇದನ್ನು ಏಕೆ ತಡೆಯಬೇಕು?");
        m.put("intake.ignore_exception_pattern_label", "ಅಪವಾದ ಮಾದರಿ (ಪ್ರತಿ-ನಿಯಮ)");
        m.put("intake.ignore_exception_pattern_placeholder", "ಉದಾ. grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "ಇದು ಹೊಂದಿಕೆಯಾದರೆ ನಿಯಮ ರದ್ದಾಗುತ್ತದೆ ಮತ್ತು ಕರಡು ರಚನೆಯಾಗುತ್ತದೆ");
        m.put("intake.ignore_semantics_heading", "ತಡೆ ನಿಯಮಗಳು ಹೇಗೆ ಕೆಲಸ ಮಾಡುತ್ತವೆ");
        m.put("intake.ignore_semantics_match_field",
              "ಮಾದರಿಯನ್ನು ಯಾವ ಶೀರ್ಷಿಕೆಯಲ್ಲಿ ಪರೀಕ್ಷಿಸಬೇಕೆಂದು ಹೊಂದಿಕೆ ಕ್ಷೇತ್ರ ನಿರ್ಧರಿಸುತ್ತದೆ "
              + "(ಪೂರ್ವನಿಯೋಜಿತ: ಕಳುಹಿಸಿದವರು).");
        m.put("intake.ignore_semantics_and_logic",
              "ಸ್ವೀಕರಿಸುವವರು/ಪ್ರತಿ/ಗುಪ್ತ ಪ್ರತಿ/ವಿಷಯ ಹೆಚ್ಚುವರಿ ಮಾನದಂಡಗಳು — ನಿಗದಿಪಡಿಸಿದಾಗ ಅವು ಸಹ "
              + "ಹೊಂದಿಕೆಯಾಗಬೇಕು.");
        m.put("intake.ignore_semantics_exception",
              "ಅಪವಾದ ಮಾದರಿ ಒಂದು ಪ್ರತಿ-ನಿಯಮ: ಅದು ಹೊಂದಿಕೆಯಾದರೆ ತಡೆ ರದ್ದಾಗುತ್ತದೆ ಮತ್ತು ಕರಡು ರಚನೆಯಾಗುತ್ತದೆ. "
              + "ಇಡೀ ಡೊಮೇನ್‌ಅನ್ನು ತಡೆದರೂ ನಿರ್ದಿಷ್ಟ ಕಳುಹಿಸುವವರಿಗೆ ಅನುಮತಿ ನೀಡಲು ಇದನ್ನು ಬಳಸಿ.");
        m.put("intake.ignore_semantics_inactive", "ನಿಷ್ಕ್ರಿಯ ನಿಯಮ ಏನನ್ನೂ ತಡೆಯುವುದಿಲ್ಲ.");
        m.put("intake.ignore_col_pattern", "ಮಾದರಿ");
        m.put("intake.ignore_col_match_field", "ಹೊಂದಿಕೆ ಕ್ಷೇತ್ರ");
        m.put("intake.ignore_col_type", "ಪ್ರಕಾರ");
        m.put("intake.ignore_col_additional_criteria", "ಹೆಚ್ಚುವರಿ ಮಾನದಂಡಗಳು");
        m.put("intake.ignore_col_exception", "ಅಪವಾದ");
        m.put("intake.ignore_col_reason", "ಕಾರಣ");
        m.put("intake.ignore_col_suppressed_count", "ತಡೆಯಲಾದವು");
        m.put("intake.ignore_col_suppressed_count_hint", "ಈ ನಿಯಮ ಈಗಾಗಲೇ ತಡೆದಿರುವ ಇಮೇಲ್‌ಗಳು");
        m.put("intake.ignore_col_added_by", "ಸೇರಿಸಿದವರು");
        m.put("intake.ignore_col_status", "ಸ್ಥಿತಿ");
        m.put("intake.ignore_col_actions", "ಕ್ರಿಯೆಗಳು");
        m.put("intake.ignore_load_failed", "ತಡೆ ನಿಯಮಗಳನ್ನು ಲೋಡ್ ಮಾಡಲು ವಿಫಲವಾಯಿತು");
        m.put("intake.ignore_rule_created", "ನಿಯಮ ರಚಿಸಲಾಗಿದೆ");
        m.put("intake.ignore_rule_create_failed", "ನಿಯಮ ರಚಿಸಲು ವಿಫಲವಾಯಿತು");
        m.put("intake.ignore_rule_updated", "ನಿಯಮ ನವೀಕರಿಸಲಾಗಿದೆ");
        m.put("intake.ignore_rule_update_failed", "ನಿಯಮ ನವೀಕರಿಸಲು ವಿಫಲವಾಯಿತು");
        m.put("intake.ignore_rule_deleted", "ನಿಯಮ ಅಳಿಸಲಾಗಿದೆ");
        m.put("intake.ignore_rule_delete_failed", "ನಿಯಮ ಅಳಿಸಲು ವಿಫಲವಾಯಿತು");
        m.put("intake.ignore_rule_activated", "ನಿಯಮ ಸಕ್ರಿಯಗೊಳಿಸಲಾಗಿದೆ");
        m.put("intake.ignore_rule_deactivated", "ನಿಯಮ ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಲಾಗಿದೆ");
        m.put("intake.ignored_emails_report_subtitle",
              "ಅಪವಾದ ಇಮೇಲ್ ಮಾಸ್ಟರ್ ತಡೆದಿರುವ ಒಳಬರುವ ಇಮೇಲ್‌ಗಳು");
        m.put("intake.ignored_emails_total", "ಒಟ್ಟು ತಡೆಯಲಾದವು");
        m.put("intake.ignored_emails_empty", "ಇನ್ನೂ ಯಾವುದೇ ಇಮೇಲ್ ತಡೆಯಲಾಗಿಲ್ಲ");
        m.put("intake.ignored_emails_empty_filtered",
              "ಈ ಶೋಧಕಗಳಿಗೆ ಹೊಂದಿಕೆಯಾಗುವ ತಡೆಯಲಾದ ಇಮೇಲ್‌ಗಳಿಲ್ಲ");
        m.put("intake.ignored_emails_load_failed", "ತಡೆ ವರದಿಯನ್ನು ಲೋಡ್ ಮಾಡಲು ವಿಫಲವಾಯಿತು");
        m.put("intake.ignored_emails_export_failed", "CSV ರಫ್ತು ಮಾಡಲು ವಿಫಲವಾಯಿತು");
        m.put("intake.ignored_filter_sender", "ಕಳುಹಿಸಿದವರು");
        m.put("intake.ignored_filter_sender_placeholder", "ಕಳುಹಿಸಿದವರ ಇಮೇಲ್‌ನಲ್ಲಿ ಇದೆ…");
        m.put("intake.ignored_filter_from_date", "ಆರಂಭ ದಿನಾಂಕ");
        m.put("intake.ignored_filter_to_date", "ಅಂತಿಮ ದಿನಾಂಕ");
        m.put("intake.ignored_filter_rule", "ಹೊಂದಿಕೆಯಾದ ನಿಯಮ");
        m.put("intake.ignored_filter_all_rules", "ಎಲ್ಲಾ ನಿಯಮಗಳು");
        m.put("intake.ignored_col_sender", "ಕಳುಹಿಸಿದವರು");
        m.put("intake.ignored_col_subject", "ವಿಷಯ");
        m.put("intake.ignored_col_received_at", "ಬಂದ ಸಮಯ");
        m.put("intake.ignored_col_matched_rule", "ಹೊಂದಿಕೆಯಾದ ನಿಯಮ");
        m.put("intake.ignored_col_matched_field", "ಹೊಂದಿಕೆಯಾದ ಕ್ಷೇತ್ರ");
        m.put("intake.ignored_col_matched_reason", "ಕಾರಣ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("intake.email_suppressed_by_rule",
              "ഈ ഇമെയിൽ സജീവമായ ഒരു അവഗണന നിയമവുമായി പൊരുത്തപ്പെട്ടു, അതിനാൽ പരാതിയുടെ കരട് സൃഷ്ടിച്ചിട്ടില്ല.");
        m.put("intake.duplicate_delivery_ignored",
              "ഈ സന്ദേശം നേരത്തെ തന്നെ ലഭിച്ച് പ്രോസസ് ചെയ്തിട്ടുണ്ട്. നിലവിലുള്ള കരട് താഴെ കാണിച്ചിരിക്കുന്നു.");
        m.put("intake.duplicate_linked_to_parent",
              "ഇത് ഇപ്പോൾ അവസാനിപ്പിച്ച ഒരു മുൻ പരാതിയുടെ തനിപ്പകർപ്പാണ്. പുതിയ കരട് സൃഷ്ടിക്കുന്നതിനു പകരം "
              + "ഇമെയിലും അതിന്റെ അനുബന്ധങ്ങളും ആ പരാതിയോട് ചേർത്തിട്ടുണ്ട്.");
        m.put("intake.vernacular_manual_entry_required",
              "ഉള്ളടക്കം ഒരു പ്രാദേശിക ഭാഷയിലാണ്, അതിനാൽ സ്വയമേവയുള്ള വാചകം വേർതിരിച്ചെടുക്കൽ മനഃപൂർവം "
              + "ശ്രമിച്ചിട്ടില്ല. ഇത് വിദഗ്ധനായ ഡാറ്റാ എൻട്രി ഓപ്പറേറ്റർക്ക് കൈമാറിയിട്ടുണ്ട്.");
        m.put("intake.ocr_low_confidence_manual_entry",
              "ഫോം സ്വയമേവ പൂരിപ്പിക്കാൻ കഴിയുന്നത്ര വിശ്വസനീയമായി സ്കാൻ ചെയ്ത രേഖ വായിക്കാൻ കഴിഞ്ഞില്ല. "
              + "വിവരങ്ങൾ സ്വമേധയാ നൽകുക.");
        m.put("intake.manual_entry_required_banner",
              "സ്വമേധയാ വിവരം നൽകേണ്ടതുണ്ട് — ഈ കരടിനായി സ്വയമേവയുള്ള വാചകം വേർതിരിച്ചെടുക്കൽ ഉപയോഗിച്ചിട്ടില്ല.");
        m.put("intake.draft_not_found", "കരട് കണ്ടെത്തിയില്ല.");
        m.put("intake.ignore_rule_not_found", "അവഗണന നിയമം കണ്ടെത്തിയില്ല.");
        m.put("intake.suggested_related_invalid_decision",
              "തീരുമാനം സ്വീകരിച്ചു അല്ലെങ്കിൽ തള്ളി എന്നതിൽ ഒന്നായിരിക്കണം.");
        m.put("intake.attachment_too_large", "ഓരോ ഫയലും {{size}}MB അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.");
        m.put("intake.attachment_total_too_large",
              "അനുബന്ധങ്ങളുടെ ആകെ വലുപ്പം 25MB അല്ലെങ്കിൽ അതിൽ കുറവായിരിക്കണം.");
        m.put("intake.attachment_too_many", "നിങ്ങൾക്ക് പരമാവധി 10 ഫയലുകൾ ചേർക്കാം.");
        m.put("intake.attachment_rejected",
              "ഫയലിന്റെ തരം അതിന്റെ ഉള്ളടക്കത്തിൽ നിന്ന് പരിശോധിക്കാൻ കഴിഞ്ഞില്ല, അതിനാൽ ഫയൽ നിരസിച്ചു.");
        m.put("intake.ignored_emails_report_title", "അവഗണിച്ച ഇമെയിലുകളുടെ റിപ്പോർട്ട്");
        m.put("intake.ignored_emails_export_csv", "CSV ആയി എക്സ്പോർട്ട് ചെയ്യുക");
        m.put("intake.exceptional_email_master_title", "അപവാദ ഇമെയിൽ മാസ്റ്റർ");
        m.put("intake.back_to_queue", "ക്യൂവിലേക്ക് മടങ്ങുക");
        m.put("intake.loading", "ലോഡ് ചെയ്യുന്നു…");
        m.put("intake.cancel", "റദ്ദാക്കുക");
        m.put("intake.edit", "തിരുത്തുക");
        m.put("intake.delete", "നീക്കം ചെയ്യുക");
        m.put("intake.search", "തിരയുക");
        m.put("intake.clear_filters", "അരിപ്പകൾ മായ്ക്കുക");
        m.put("intake.exceptional_email_master_subtitle",
              "ഒരു കരട് സൃഷ്ടിക്കുന്നതിനു മുൻപേ വരുന്ന ഇമെയിൽ തടയുന്ന നിയമങ്ങൾ");
        m.put("intake.ignore_add_rule", "നിയമം ചേർക്കുക");
        m.put("intake.ignore_edit_rule", "നിയമം തിരുത്തുക");
        m.put("intake.ignore_create", "നിയമം സൃഷ്ടിക്കുക");
        m.put("intake.ignore_save_changes", "മാറ്റങ്ങൾ സൂക്ഷിക്കുക");
        m.put("intake.ignore_no_rules", "തടയൽ നിയമങ്ങൾ ഒന്നും നിർണയിച്ചിട്ടില്ല");
        m.put("intake.ignore_activate", "സജീവമാക്കുക");
        m.put("intake.ignore_deactivate", "നിഷ്ക്രിയമാക്കുക");
        m.put("intake.ignore_status_active", "സജീവം");
        m.put("intake.ignore_status_inactive", "നിഷ്ക്രിയം");
        m.put("intake.ignore_active_label", "നിയമം സജീവമാണ്");
        m.put("intake.ignore_pattern_label", "മാതൃക");
        m.put("intake.ignore_pattern_placeholder", "ഉദാ. noreply@bank.com അല്ലെങ്കിൽ *@mailer.com");
        m.put("intake.ignore_pattern_hint", "തിരഞ്ഞെടുത്ത പൊരുത്ത ഫീൽഡിൽ പരിശോധിക്കപ്പെടുന്നു");
        m.put("intake.ignore_match_field_label", "പൊരുത്ത ഫീൽഡ്");
        m.put("intake.ignore_match_field_hint", "മാതൃക ഇമെയിലിന്റെ ഏതു തലക്കെട്ടിൽ പരിശോധിക്കണം");
        m.put("intake.ignore_pattern_type_label", "മാതൃകയുടെ തരം");
        m.put("intake.match_field_from", "അയച്ചയാൾ");
        m.put("intake.match_field_to", "സ്വീകർത്താവ്");
        m.put("intake.match_field_cc", "പകർപ്പ്");
        m.put("intake.match_field_bcc", "രഹസ്യ പകർപ്പ്");
        m.put("intake.match_field_subject", "വിഷയം");
        m.put("intake.pattern_type_exact", "കൃത്യം");
        m.put("intake.pattern_type_domain", "ഡൊമെയ്ൻ");
        m.put("intake.pattern_type_wildcard", "വൈൽഡ്കാർഡ്");
        m.put("intake.pattern_type_contains", "ഉൾക്കൊള്ളുന്നു");
        m.put("intake.ignore_additional_criteria_heading", "അധിക മാനദണ്ഡങ്ങൾ (ഐച്ഛികം)");
        m.put("intake.ignore_additional_criteria_hint",
              "നിർണയിച്ചാൽ ഇമെയിൽ തടയാൻ ഇവയും പൊരുത്തപ്പെടണം (AND യുക്തി)");
        m.put("intake.ignore_to_pattern_label", "സ്വീകർത്താവ്");
        m.put("intake.ignore_cc_pattern_label", "പകർപ്പ്");
        m.put("intake.ignore_bcc_pattern_label", "രഹസ്യ പകർപ്പ്");
        m.put("intake.ignore_subject_pattern_label", "വിഷയം");
        m.put("intake.ignore_and_logic_badge", "എല്ലാം പൊരുത്തപ്പെടണം");
        m.put("intake.ignore_reason_label", "കാരണം");
        m.put("intake.ignore_reason_placeholder", "ഇത് എന്തുകൊണ്ട് തടയണം?");
        m.put("intake.ignore_exception_pattern_label", "അപവാദ മാതൃക (എതിർ-നിയമം)");
        m.put("intake.ignore_exception_pattern_placeholder", "ഉദാ. grievance@bank.com");
        m.put("intake.ignore_exception_pattern_hint",
              "ഇത് പൊരുത്തപ്പെട്ടാൽ നിയമം റദ്ദാകുകയും കരട് സൃഷ്ടിക്കപ്പെടുകയും ചെയ്യും");
        m.put("intake.ignore_semantics_heading", "തടയൽ നിയമങ്ങൾ എങ്ങനെ പ്രവർത്തിക്കുന്നു");
        m.put("intake.ignore_semantics_match_field",
              "മാതൃക ഏതു തലക്കെട്ടിൽ പരിശോധിക്കണമെന്ന് പൊരുത്ത ഫീൽഡ് തീരുമാനിക്കുന്നു "
              + "(സ്വതേ: അയച്ചയാൾ).");
        m.put("intake.ignore_semantics_and_logic",
              "സ്വീകർത്താവ്/പകർപ്പ്/രഹസ്യ പകർപ്പ്/വിഷയം അധിക മാനദണ്ഡങ്ങളാണ് — നിർണയിച്ചാൽ അവയും "
              + "പൊരുത്തപ്പെടണം.");
        m.put("intake.ignore_semantics_exception",
              "അപവാദ മാതൃക ഒരു എതിർ-നിയമമാണ്: അത് പൊരുത്തപ്പെട്ടാൽ തടയൽ റദ്ദാകുകയും കരട് "
              + "സൃഷ്ടിക്കപ്പെടുകയും ചെയ്യും. ഒരു ഡൊമെയ്ൻ പൂർണമായി തടയുമ്പോഴും നിശ്ചിത അയക്കുന്നവരെ "
              + "അനുവദിക്കാൻ ഇത് ഉപയോഗിക്കുക.");
        m.put("intake.ignore_semantics_inactive", "നിഷ്ക്രിയ നിയമം ഒന്നും തടയുന്നില്ല.");
        m.put("intake.ignore_col_pattern", "മാതൃക");
        m.put("intake.ignore_col_match_field", "പൊരുത്ത ഫീൽഡ്");
        m.put("intake.ignore_col_type", "തരം");
        m.put("intake.ignore_col_additional_criteria", "അധിക മാനദണ്ഡങ്ങൾ");
        m.put("intake.ignore_col_exception", "അപവാദം");
        m.put("intake.ignore_col_reason", "കാരണം");
        m.put("intake.ignore_col_suppressed_count", "തടഞ്ഞവ");
        m.put("intake.ignore_col_suppressed_count_hint", "ഈ നിയമം ഇതുവരെ തടഞ്ഞ ഇമെയിലുകൾ");
        m.put("intake.ignore_col_added_by", "ചേർത്തയാൾ");
        m.put("intake.ignore_col_status", "സ്ഥിതി");
        m.put("intake.ignore_col_actions", "നടപടികൾ");
        m.put("intake.ignore_load_failed", "തടയൽ നിയമങ്ങൾ ലോഡ് ചെയ്യാൻ കഴിഞ്ഞില്ല");
        m.put("intake.ignore_rule_created", "നിയമം സൃഷ്ടിച്ചു");
        m.put("intake.ignore_rule_create_failed", "നിയമം സൃഷ്ടിക്കാൻ കഴിഞ്ഞില്ല");
        m.put("intake.ignore_rule_updated", "നിയമം പുതുക്കി");
        m.put("intake.ignore_rule_update_failed", "നിയമം പുതുക്കാൻ കഴിഞ്ഞില്ല");
        m.put("intake.ignore_rule_deleted", "നിയമം നീക്കം ചെയ്തു");
        m.put("intake.ignore_rule_delete_failed", "നിയമം നീക്കം ചെയ്യാൻ കഴിഞ്ഞില്ല");
        m.put("intake.ignore_rule_activated", "നിയമം സജീവമാക്കി");
        m.put("intake.ignore_rule_deactivated", "നിയമം നിഷ്ക്രിയമാക്കി");
        m.put("intake.ignored_emails_report_subtitle",
              "അപവാദ ഇമെയിൽ മാസ്റ്റർ തടഞ്ഞ വരവ് ഇമെയിലുകൾ");
        m.put("intake.ignored_emails_total", "ആകെ തടഞ്ഞവ");
        m.put("intake.ignored_emails_empty", "ഇതുവരെ ഒരു ഇമെയിലും തടഞ്ഞിട്ടില്ല");
        m.put("intake.ignored_emails_empty_filtered",
              "ഈ അരിപ്പകളോട് പൊരുത്തപ്പെടുന്ന തടഞ്ഞ ഇമെയിലുകൾ ഇല്ല");
        m.put("intake.ignored_emails_load_failed", "തടയൽ റിപ്പോർട്ട് ലോഡ് ചെയ്യാൻ കഴിഞ്ഞില്ല");
        m.put("intake.ignored_emails_export_failed", "CSV എക്സ്പോർട്ട് ചെയ്യാൻ കഴിഞ്ഞില്ല");
        m.put("intake.ignored_filter_sender", "അയച്ചയാൾ");
        m.put("intake.ignored_filter_sender_placeholder", "അയച്ചയാളുടെ ഇമെയിലിൽ ഉണ്ട്…");
        m.put("intake.ignored_filter_from_date", "തുടക്ക തീയതി");
        m.put("intake.ignored_filter_to_date", "അവസാന തീയതി");
        m.put("intake.ignored_filter_rule", "പൊരുത്തപ്പെട്ട നിയമം");
        m.put("intake.ignored_filter_all_rules", "എല്ലാ നിയമങ്ങളും");
        m.put("intake.ignored_col_sender", "അയച്ചയാൾ");
        m.put("intake.ignored_col_subject", "വിഷയം");
        m.put("intake.ignored_col_received_at", "ലഭിച്ച സമയം");
        m.put("intake.ignored_col_matched_rule", "പൊരുത്തപ്പെട്ട നിയമം");
        m.put("intake.ignored_col_matched_field", "പൊരുത്തപ്പെട്ട ഫീൽഡ്");
        m.put("intake.ignored_col_matched_reason", "കാരണം");
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
