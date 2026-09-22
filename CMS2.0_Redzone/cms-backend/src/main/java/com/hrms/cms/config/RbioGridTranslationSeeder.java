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
 * Vocabulary for the RBIO home grid, its colour bands and its advance search (UST426-442, UST754).
 *
 * <p>Three groups of keys:
 * <ul>
 *   <li>{@code rbio.grid.*} — column headers, paging, empty states and load errors;
 *   <li>{@code rbio.band.*} — the six UST436 row colour bands;
 *   <li>{@code rbio.search.*} — the advance-search field labels, hints and result messages.
 * </ul>
 *
 * <p>Every key is namespaced {@code rbio.grid.* / rbio.band.* / rbio.search.*}. Translation keys are
 * idempotent by {@code existsByCode}, so if another concurrent session picked the same code its text
 * would silently win and these would never appear — namespacing is what prevents that.
 *
 * <p>Seeded in all ten locales: English on {@code TranslationKey.defaultValue}, the other nine as
 * {@code Translation} rows. Insert-if-absent, so re-running is a no-op — with the corollary that
 * correcting a string here does NOT rewrite a row already committed to a database. A text correction
 * needs a code-scoped UPDATE in both migration directories.
 *
 * <p>NOTE on the status labels themselves: {@code rbio.status.*} is NOT seeded here.
 * {@code RbioStatusMasterSeeder} owns those codes via {@code RBIO_STATUS_MASTER.translation_key}, and a
 * second writer for the same keys is how two sources of truth diverge.
 */
@Component
@Order(34)
public class RbioGridTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "rbio-grid";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public RbioGridTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // ── Column headers (UST754) ──
        m.put("rbio.grid.col_complaint_id", "Complaint Id");
        m.put("rbio.grid.col_complaint_number", "Complaint Number");
        m.put("rbio.grid.col_from", "From");
        m.put("rbio.grid.col_sla_breach_in", "SLA Breach In");
        m.put("rbio.grid.col_mode", "Mode");
        m.put("rbio.grid.col_complainant_name", "Complainant Name");
        m.put("rbio.grid.col_status", "Status");
        m.put("rbio.grid.col_entity_name", "Entity Name");
        m.put("rbio.grid.col_category", "Complaint Category");
        m.put("rbio.grid.col_creation_date", "Creation Date");
        m.put("rbio.grid.col_subject", "Subject");
        m.put("rbio.grid.col_priority", "Priority");
        m.put("rbio.grid.col_assigned_to", "Assigned To");

        // ── Filters and paging ──
        m.put("rbio.grid.filter_status", "Filter by status");
        m.put("rbio.grid.filter_queue", "Filter by queue");
        m.put("rbio.grid.all_statuses", "All");
        m.put("rbio.grid.queue_assigned_to_me", "Complaints Assigned To Me");
        m.put("rbio.grid.queue_all", "All Complaints");
        m.put("rbio.grid.refresh", "Refresh");
        m.put("rbio.grid.search", "Search");
        m.put("rbio.grid.search_column", "Search Column Name");
        m.put("rbio.grid.showing", "Showing");
        m.put("rbio.grid.of", "of");
        m.put("rbio.grid.shown_after_refine", "shown after filters");
        m.put("rbio.grid.retry", "Retry");

        // ── Empty and error states ──
        m.put("rbio.grid.no_complaints", "No complaints in this queue.");
        m.put("rbio.grid.no_rows_after_refine", "No complaints on this page match your filters.");
        m.put("rbio.grid.error_load_failed",
                "Could not load complaints. Please retry; if this persists, contact support.");
        m.put("rbio.grid.error_forbidden", "You do not have permission to view RBIO complaints.");
        m.put("rbio.grid.error_filters_unavailable",
                "Status filters could not be loaded, so filtering by status is unavailable. "
                        + "Your complaints are still listed.");

        // ── Statistics cards. Named as PAGE counts where that is what they are. ──
        m.put("rbio.stats.total_pending", "Total Pending Complaints");
        m.put("rbio.stats.pending_with_me_page", "Pending with Me (this page)");
        m.put("rbio.stats.pending_contact_page", "Pending with Contact Person (this page)");
        m.put("rbio.stats.pending_meeting_page", "Meeting Scheduled (this page)");
        m.put("rbio.stats.sla_breach_page", "SLA Breach (this page)");
        m.put("rbio.stats.re_overdue", "RE Overdue");

        // ── Colour bands (UST436) ──
        m.put("rbio.band.legend", "Colour key:");
        m.put("rbio.band.clear", "Clear colours");
        m.put("rbio.band.white", "New (CRPC/Portal)");
        m.put("rbio.band.red", "Sent to RE");
        m.put("rbio.band.green", "Response from RE");
        m.put("rbio.band.yellow", "Sent back to Dealing Official");
        m.put("rbio.band.pink", "Withdrawn");
        m.put("rbio.band.blue", "CRPC delayed");

        // ── Advance search (UST439-442) ──
        m.put("rbio.search.complaint_number", "Complaint Number");
        m.put("rbio.search.complaint_id", "Complaint Id");
        m.put("rbio.search.status_code", "Status Code");
        m.put("rbio.search.complainant_name", "Complainant Name");
        m.put("rbio.search.complainant_mobile", "Complainant Mobile");
        m.put("rbio.search.complainant_email", "Complainant Email");
        m.put("rbio.search.from_email", "From Email ID");
        m.put("rbio.search.mode_of_receipt", "Mode of Receipt");
        m.put("rbio.search.entity_name", "Entity Name");
        m.put("rbio.search.category", "Complaint Main Category");
        m.put("rbio.search.reported_from", "Reported On (from)");
        m.put("rbio.search.reported_to", "Reported On (to)");
        m.put("rbio.search.subject", "Subject");
        m.put("rbio.search.select_value", "Select a Value");
        m.put("rbio.search.hint_partial", "Partial match");
        m.put("rbio.search.hint_exact", "Exact match required");
        m.put("rbio.search.search", "Search");
        m.put("rbio.search.cancel", "Cancel");
        m.put("rbio.search.clear", "Clear");
        m.put("rbio.search.adjust", "Adjust search");
        m.put("rbio.search.criteria_used", "Your search");
        m.put("rbio.search.no_results", "No complaints match your search criteria.");
        m.put("rbio.search.error_term_too_short",
                "Please enter at least two characters for a partial-match field.");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "Searching by Nodal Officer is not available yet.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "शिकायत आईडी");
        m.put("rbio.grid.col_complaint_number", "शिकायत संख्या");
        m.put("rbio.grid.col_from", "प्रेषक");
        m.put("rbio.grid.col_sla_breach_in", "एसएलए उल्लंघन में");
        m.put("rbio.grid.col_mode", "माध्यम");
        m.put("rbio.grid.col_complainant_name", "शिकायतकर्ता का नाम");
        m.put("rbio.grid.col_status", "स्थिति");
        m.put("rbio.grid.col_entity_name", "संस्था का नाम");
        m.put("rbio.grid.col_category", "शिकायत श्रेणी");
        m.put("rbio.grid.col_creation_date", "निर्माण तिथि");
        m.put("rbio.grid.col_subject", "विषय");
        m.put("rbio.grid.col_priority", "प्राथमिकता");
        m.put("rbio.grid.col_assigned_to", "को सौंपा गया");
        m.put("rbio.grid.filter_status", "स्थिति के अनुसार छानें");
        m.put("rbio.grid.filter_queue", "कतार के अनुसार छानें");
        m.put("rbio.grid.all_statuses", "सभी");
        m.put("rbio.grid.queue_assigned_to_me", "मुझे सौंपी गई शिकायतें");
        m.put("rbio.grid.queue_all", "सभी शिकायतें");
        m.put("rbio.grid.refresh", "ताज़ा करें");
        m.put("rbio.grid.search", "खोजें");
        m.put("rbio.grid.search_column", "स्तंभ का नाम खोजें");
        m.put("rbio.grid.showing", "दिखा रहे हैं");
        m.put("rbio.grid.of", "में से");
        m.put("rbio.grid.shown_after_refine", "छानने के बाद दिखाए गए");
        m.put("rbio.grid.retry", "पुनः प्रयास करें");
        m.put("rbio.grid.no_complaints", "इस कतार में कोई शिकायत नहीं है।");
        m.put("rbio.grid.no_rows_after_refine", "इस पृष्ठ पर कोई शिकायत आपके छानने से मेल नहीं खाती।");
        m.put("rbio.grid.error_load_failed",
                "शिकायतें लोड नहीं हो सकीं। कृपया पुनः प्रयास करें; यदि यह बना रहे तो सहायता से संपर्क करें।");
        m.put("rbio.grid.error_forbidden", "आपको आरबीआईओ शिकायतें देखने की अनुमति नहीं है।");
        m.put("rbio.grid.error_filters_unavailable",
                "स्थिति फ़िल्टर लोड नहीं हो सके, इसलिए स्थिति के अनुसार छानना उपलब्ध नहीं है। "
                        + "आपकी शिकायतें अभी भी सूचीबद्ध हैं।");
        m.put("rbio.stats.total_pending", "कुल लंबित शिकायतें");
        m.put("rbio.stats.pending_with_me_page", "मेरे पास लंबित (इस पृष्ठ पर)");
        m.put("rbio.stats.pending_contact_page", "संपर्क व्यक्ति के पास लंबित (इस पृष्ठ पर)");
        m.put("rbio.stats.pending_meeting_page", "बैठक निर्धारित (इस पृष्ठ पर)");
        m.put("rbio.stats.sla_breach_page", "एसएलए उल्लंघन (इस पृष्ठ पर)");
        m.put("rbio.stats.re_overdue", "विनियमित संस्था विलंबित");
        m.put("rbio.band.legend", "रंग संकेत:");
        m.put("rbio.band.clear", "रंग हटाएँ");
        m.put("rbio.band.white", "नई (सीआरपीसी/पोर्टल)");
        m.put("rbio.band.red", "विनियमित संस्था को भेजी गई");
        m.put("rbio.band.green", "विनियमित संस्था से उत्तर");
        m.put("rbio.band.yellow", "कार्यकारी अधिकारी को वापस भेजी गई");
        m.put("rbio.band.pink", "वापस ली गई");
        m.put("rbio.band.blue", "सीआरपीसी विलंबित");
        m.put("rbio.search.complaint_number", "शिकायत संख्या");
        m.put("rbio.search.complaint_id", "शिकायत आईडी");
        m.put("rbio.search.status_code", "स्थिति कोड");
        m.put("rbio.search.complainant_name", "शिकायतकर्ता का नाम");
        m.put("rbio.search.complainant_mobile", "शिकायतकर्ता का मोबाइल");
        m.put("rbio.search.complainant_email", "शिकायतकर्ता का ईमेल");
        m.put("rbio.search.from_email", "प्रेषक ईमेल आईडी");
        m.put("rbio.search.mode_of_receipt", "प्राप्ति का माध्यम");
        m.put("rbio.search.entity_name", "संस्था का नाम");
        m.put("rbio.search.category", "शिकायत मुख्य श्रेणी");
        m.put("rbio.search.reported_from", "रिपोर्ट की तिथि (से)");
        m.put("rbio.search.reported_to", "रिपोर्ट की तिथि (तक)");
        m.put("rbio.search.subject", "विषय");
        m.put("rbio.search.select_value", "एक मान चुनें");
        m.put("rbio.search.hint_partial", "आंशिक मिलान");
        m.put("rbio.search.hint_exact", "पूर्ण मिलान आवश्यक");
        m.put("rbio.search.search", "खोजें");
        m.put("rbio.search.cancel", "रद्द करें");
        m.put("rbio.search.clear", "साफ़ करें");
        m.put("rbio.search.adjust", "खोज बदलें");
        m.put("rbio.search.criteria_used", "आपकी खोज");
        m.put("rbio.search.no_results", "आपके खोज मानदंड से कोई शिकायत मेल नहीं खाती।");
        m.put("rbio.search.error_term_too_short",
                "आंशिक मिलान वाले क्षेत्र के लिए कम से कम दो अक्षर दर्ज करें।");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "नोडल अधिकारी द्वारा खोज अभी उपलब्ध नहीं है।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "तक्रार आयडी");
        m.put("rbio.grid.col_complaint_number", "तक्रार क्रमांक");
        m.put("rbio.grid.col_from", "प्रेषक");
        m.put("rbio.grid.col_sla_breach_in", "एसएलए उल्लंघनात");
        m.put("rbio.grid.col_mode", "माध्यम");
        m.put("rbio.grid.col_complainant_name", "तक्रारदाराचे नाव");
        m.put("rbio.grid.col_status", "स्थिती");
        m.put("rbio.grid.col_entity_name", "संस्थेचे नाव");
        m.put("rbio.grid.col_category", "तक्रार श्रेणी");
        m.put("rbio.grid.col_creation_date", "निर्मिती दिनांक");
        m.put("rbio.grid.col_subject", "विषय");
        m.put("rbio.grid.col_priority", "प्राधान्य");
        m.put("rbio.grid.col_assigned_to", "यांना नेमले");
        m.put("rbio.grid.filter_status", "स्थितीनुसार गाळणी");
        m.put("rbio.grid.filter_queue", "रांगेनुसार गाळणी");
        m.put("rbio.grid.all_statuses", "सर्व");
        m.put("rbio.grid.queue_assigned_to_me", "मला नेमलेल्या तक्रारी");
        m.put("rbio.grid.queue_all", "सर्व तक्रारी");
        m.put("rbio.grid.refresh", "ताजे करा");
        m.put("rbio.grid.search", "शोधा");
        m.put("rbio.grid.search_column", "स्तंभाचे नाव शोधा");
        m.put("rbio.grid.showing", "दाखवत आहे");
        m.put("rbio.grid.of", "पैकी");
        m.put("rbio.grid.shown_after_refine", "गाळणीनंतर दाखवले");
        m.put("rbio.grid.retry", "पुन्हा प्रयत्न करा");
        m.put("rbio.grid.no_complaints", "या रांगेत कोणतीही तक्रार नाही.");
        m.put("rbio.grid.no_rows_after_refine", "या पानावरील कोणतीही तक्रार तुमच्या गाळणीशी जुळत नाही.");
        m.put("rbio.grid.error_load_failed",
                "तक्रारी लोड होऊ शकल्या नाहीत. कृपया पुन्हा प्रयत्न करा; असे राहिल्यास मदतीशी संपर्क करा.");
        m.put("rbio.grid.error_forbidden", "तुम्हाला आरबीआयओ तक्रारी पाहण्याची परवानगी नाही.");
        m.put("rbio.grid.error_filters_unavailable",
                "स्थिती गाळण्या लोड होऊ शकल्या नाहीत, म्हणून स्थितीनुसार गाळणी उपलब्ध नाही. "
                        + "तुमच्या तक्रारी अद्याप सूचीबद्ध आहेत.");
        m.put("rbio.stats.total_pending", "एकूण प्रलंबित तक्रारी");
        m.put("rbio.stats.pending_with_me_page", "माझ्याकडे प्रलंबित (या पानावर)");
        m.put("rbio.stats.pending_contact_page", "संपर्क व्यक्तीकडे प्रलंबित (या पानावर)");
        m.put("rbio.stats.pending_meeting_page", "बैठक नियोजित (या पानावर)");
        m.put("rbio.stats.sla_breach_page", "एसएलए उल्लंघन (या पानावर)");
        m.put("rbio.stats.re_overdue", "नियंत्रित संस्था विलंबित");
        m.put("rbio.band.legend", "रंग सूची:");
        m.put("rbio.band.clear", "रंग काढा");
        m.put("rbio.band.white", "नवीन (सीआरपीसी/पोर्टल)");
        m.put("rbio.band.red", "नियंत्रित संस्थेकडे पाठवले");
        m.put("rbio.band.green", "नियंत्रित संस्थेकडून उत्तर");
        m.put("rbio.band.yellow", "कार्यकारी अधिकाऱ्याकडे परत");
        m.put("rbio.band.pink", "मागे घेतले");
        m.put("rbio.band.blue", "सीआरपीसी विलंबित");
        m.put("rbio.search.complaint_number", "तक्रार क्रमांक");
        m.put("rbio.search.complaint_id", "तक्रार आयडी");
        m.put("rbio.search.status_code", "स्थिती कोड");
        m.put("rbio.search.complainant_name", "तक्रारदाराचे नाव");
        m.put("rbio.search.complainant_mobile", "तक्रारदाराचा मोबाइल");
        m.put("rbio.search.complainant_email", "तक्रारदाराचा ईमेल");
        m.put("rbio.search.from_email", "प्रेषक ईमेल आयडी");
        m.put("rbio.search.mode_of_receipt", "प्राप्तीचे माध्यम");
        m.put("rbio.search.entity_name", "संस्थेचे नाव");
        m.put("rbio.search.category", "तक्रार मुख्य श्रेणी");
        m.put("rbio.search.reported_from", "नोंद दिनांक (पासून)");
        m.put("rbio.search.reported_to", "नोंद दिनांक (पर्यंत)");
        m.put("rbio.search.subject", "विषय");
        m.put("rbio.search.select_value", "मूल्य निवडा");
        m.put("rbio.search.hint_partial", "अंशतः जुळणी");
        m.put("rbio.search.hint_exact", "अचूक जुळणी आवश्यक");
        m.put("rbio.search.search", "शोधा");
        m.put("rbio.search.cancel", "रद्द करा");
        m.put("rbio.search.clear", "साफ करा");
        m.put("rbio.search.adjust", "शोध बदला");
        m.put("rbio.search.criteria_used", "तुमचा शोध");
        m.put("rbio.search.no_results", "तुमच्या शोध निकषांशी कोणतीही तक्रार जुळत नाही.");
        m.put("rbio.search.error_term_too_short",
                "अंशतः जुळणीच्या क्षेत्रासाठी किमान दोन अक्षरे प्रविष्ट करा.");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "नोडल अधिकाऱ्याद्वारे शोध अद्याप उपलब्ध नाही.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "অভিযোগ আইডি");
        m.put("rbio.grid.col_complaint_number", "অভিযোগ নম্বর");
        m.put("rbio.grid.col_from", "প্রেরক");
        m.put("rbio.grid.col_sla_breach_in", "এসএলএ লঙ্ঘনে");
        m.put("rbio.grid.col_mode", "মাধ্যম");
        m.put("rbio.grid.col_complainant_name", "অভিযোগকারীর নাম");
        m.put("rbio.grid.col_status", "অবস্থা");
        m.put("rbio.grid.col_entity_name", "প্রতিষ্ঠানের নাম");
        m.put("rbio.grid.col_category", "অভিযোগের শ্রেণি");
        m.put("rbio.grid.col_creation_date", "তৈরির তারিখ");
        m.put("rbio.grid.col_subject", "বিষয়");
        m.put("rbio.grid.col_priority", "প্রাধান্য");
        m.put("rbio.grid.col_assigned_to", "নিযুক্ত");
        m.put("rbio.grid.filter_status", "অবস্থা অনুসারে ছাঁকুন");
        m.put("rbio.grid.filter_queue", "সারি অনুসারে ছাঁকুন");
        m.put("rbio.grid.all_statuses", "সব");
        m.put("rbio.grid.queue_assigned_to_me", "আমাকে নিযুক্ত অভিযোগ");
        m.put("rbio.grid.queue_all", "সব অভিযোগ");
        m.put("rbio.grid.refresh", "রিফ্রেশ করুন");
        m.put("rbio.grid.search", "অনুসন্ধান");
        m.put("rbio.grid.search_column", "কলামের নাম অনুসন্ধান");
        m.put("rbio.grid.showing", "দেখানো হচ্ছে");
        m.put("rbio.grid.of", "এর মধ্যে");
        m.put("rbio.grid.shown_after_refine", "ছাঁকার পরে দেখানো");
        m.put("rbio.grid.retry", "পুনরায় চেষ্টা করুন");
        m.put("rbio.grid.no_complaints", "এই সারিতে কোনো অভিযোগ নেই।");
        m.put("rbio.grid.no_rows_after_refine", "এই পৃষ্ঠার কোনো অভিযোগ আপনার ছাঁকনির সাথে মেলে না।");
        m.put("rbio.grid.error_load_failed",
                "অভিযোগ লোড করা যায়নি। আবার চেষ্টা করুন; সমস্যা থাকলে সহায়তার সাথে যোগাযোগ করুন।");
        m.put("rbio.grid.error_forbidden", "আরবিআইও অভিযোগ দেখার অনুমতি আপনার নেই।");
        m.put("rbio.grid.error_filters_unavailable",
                "অবস্থা ছাঁকনি লোড করা যায়নি, তাই অবস্থা অনুসারে ছাঁকা অনুপলব্ধ। "
                        + "আপনার অভিযোগ এখনও তালিকাভুক্ত।");
        m.put("rbio.stats.total_pending", "মোট মুলতুবি অভিযোগ");
        m.put("rbio.stats.pending_with_me_page", "আমার কাছে মুলতুবি (এই পৃষ্ঠায়)");
        m.put("rbio.stats.pending_contact_page", "যোগাযোগ ব্যক্তির কাছে মুলতুবি (এই পৃষ্ঠায়)");
        m.put("rbio.stats.pending_meeting_page", "সভা নির্ধারিত (এই পৃষ্ঠায়)");
        m.put("rbio.stats.sla_breach_page", "এসএলএ লঙ্ঘন (এই পৃষ্ঠায়)");
        m.put("rbio.stats.re_overdue", "নিয়ন্ত্রিত সংস্থা বিলম্বিত");
        m.put("rbio.band.legend", "রঙের সূচক:");
        m.put("rbio.band.clear", "রঙ মুছুন");
        m.put("rbio.band.white", "নতুন (সিআরপিসি/পোর্টাল)");
        m.put("rbio.band.red", "নিয়ন্ত্রিত সংস্থায় পাঠানো");
        m.put("rbio.band.green", "নিয়ন্ত্রিত সংস্থা থেকে উত্তর");
        m.put("rbio.band.yellow", "কার্য অধিকারীর কাছে ফেরত");
        m.put("rbio.band.pink", "প্রত্যাহৃত");
        m.put("rbio.band.blue", "সিআরপিসি বিলম্বিত");
        m.put("rbio.search.complaint_number", "অভিযোগ নম্বর");
        m.put("rbio.search.complaint_id", "অভিযোগ আইডি");
        m.put("rbio.search.status_code", "অবস্থা কোড");
        m.put("rbio.search.complainant_name", "অভিযোগকারীর নাম");
        m.put("rbio.search.complainant_mobile", "অভিযোগকারীর মোবাইল");
        m.put("rbio.search.complainant_email", "অভিযোগকারীর ইমেল");
        m.put("rbio.search.from_email", "প্রেরক ইমেল আইডি");
        m.put("rbio.search.mode_of_receipt", "প্রাপ্তির মাধ্যম");
        m.put("rbio.search.entity_name", "প্রতিষ্ঠানের নাম");
        m.put("rbio.search.category", "অভিযোগের প্রধান শ্রেণি");
        m.put("rbio.search.reported_from", "রিপোর্টের তারিখ (থেকে)");
        m.put("rbio.search.reported_to", "রিপোর্টের তারিখ (পর্যন্ত)");
        m.put("rbio.search.subject", "বিষয়");
        m.put("rbio.search.select_value", "একটি মান নির্বাচন করুন");
        m.put("rbio.search.hint_partial", "আংশিক মিল");
        m.put("rbio.search.hint_exact", "সম্পূর্ণ মিল প্রয়োজন");
        m.put("rbio.search.search", "অনুসন্ধান");
        m.put("rbio.search.cancel", "বাতিল");
        m.put("rbio.search.clear", "মুছুন");
        m.put("rbio.search.adjust", "অনুসন্ধান পরিবর্তন");
        m.put("rbio.search.criteria_used", "আপনার অনুসন্ধান");
        m.put("rbio.search.no_results", "আপনার অনুসন্ধানের শর্তের সাথে কোনো অভিযোগ মেলে না।");
        m.put("rbio.search.error_term_too_short",
                "আংশিক মিলের ক্ষেত্রে অন্তত দুটি অক্ষর লিখুন।");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "নোডাল অফিসার দিয়ে অনুসন্ধান এখনও অনুপলব্ধ।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "ఫిర్యాదు ఐడీ");
        m.put("rbio.grid.col_complaint_number", "ఫిర్యాదు సంఖ్య");
        m.put("rbio.grid.col_from", "పంపినవారు");
        m.put("rbio.grid.col_sla_breach_in", "ఎస్ఎల్ఏ ఉల్లంఘనలో");
        m.put("rbio.grid.col_mode", "విధానం");
        m.put("rbio.grid.col_complainant_name", "ఫిర్యాదిదారు పేరు");
        m.put("rbio.grid.col_status", "స్థితి");
        m.put("rbio.grid.col_entity_name", "సంస్థ పేరు");
        m.put("rbio.grid.col_category", "ఫిర్యాదు వర్గం");
        m.put("rbio.grid.col_creation_date", "సృష్టి తేదీ");
        m.put("rbio.grid.col_subject", "విషయం");
        m.put("rbio.grid.col_priority", "ప్రాధాన్యత");
        m.put("rbio.grid.col_assigned_to", "కేటాయించినది");
        m.put("rbio.grid.filter_status", "స్థితి ప్రకారం వడపోత");
        m.put("rbio.grid.filter_queue", "వరుస ప్రకారం వడపోత");
        m.put("rbio.grid.all_statuses", "అన్నీ");
        m.put("rbio.grid.queue_assigned_to_me", "నాకు కేటాయించిన ఫిర్యాదులు");
        m.put("rbio.grid.queue_all", "అన్ని ఫిర్యాదులు");
        m.put("rbio.grid.refresh", "రిఫ్రెష్");
        m.put("rbio.grid.search", "వెతకండి");
        m.put("rbio.grid.search_column", "కాలమ్ పేరు వెతకండి");
        m.put("rbio.grid.showing", "చూపుతోంది");
        m.put("rbio.grid.of", "లో");
        m.put("rbio.grid.shown_after_refine", "వడపోత తర్వాత చూపినవి");
        m.put("rbio.grid.retry", "మళ్ళీ ప్రయత్నించండి");
        m.put("rbio.grid.no_complaints", "ఈ వరుసలో ఫిర్యాదులు లేవు.");
        m.put("rbio.grid.no_rows_after_refine", "ఈ పేజీలో ఏ ఫిర్యాదు మీ వడపోతకు సరిపోలేదు.");
        m.put("rbio.grid.error_load_failed",
                "ఫిర్యాదులు లోడ్ చేయడం సాధ్యపడలేదు. మళ్ళీ ప్రయత్నించండి; కొనసాగితే సహాయాన్ని సంప్రదించండి.");
        m.put("rbio.grid.error_forbidden", "ఆర్‌బీఐఓ ఫిర్యాదులను చూసే అనుమతి మీకు లేదు.");
        m.put("rbio.grid.error_filters_unavailable",
                "స్థితి వడపోతలు లోడ్ కాలేదు, కాబట్టి స్థితి ప్రకారం వడపోత అందుబాటులో లేదు. "
                        + "మీ ఫిర్యాదులు ఇంకా జాబితా చేయబడ్డాయి.");
        m.put("rbio.stats.total_pending", "మొత్తం పెండింగ్ ఫిర్యాదులు");
        m.put("rbio.stats.pending_with_me_page", "నా వద్ద పెండింగ్ (ఈ పేజీలో)");
        m.put("rbio.stats.pending_contact_page", "సంప్రదింపు వ్యక్తి వద్ద పెండింగ్ (ఈ పేజీలో)");
        m.put("rbio.stats.pending_meeting_page", "సమావేశం నిర్ణయించబడింది (ఈ పేజీలో)");
        m.put("rbio.stats.sla_breach_page", "ఎస్ఎల్ఏ ఉల్లంఘన (ఈ పేజీలో)");
        m.put("rbio.stats.re_overdue", "నియంత్రిత సంస్థ ఆలస్యం");
        m.put("rbio.band.legend", "రంగు సూచిక:");
        m.put("rbio.band.clear", "రంగులు తొలగించండి");
        m.put("rbio.band.white", "కొత్తది (సీఆర్‌పీసీ/పోర్టల్)");
        m.put("rbio.band.red", "నియంత్రిత సంస్థకు పంపబడింది");
        m.put("rbio.band.green", "నియంత్రిత సంస్థ నుండి సమాధానం");
        m.put("rbio.band.yellow", "కార్య అధికారికి తిరిగి పంపబడింది");
        m.put("rbio.band.pink", "ఉపసంహరించబడింది");
        m.put("rbio.band.blue", "సీఆర్‌పీసీ ఆలస్యం");
        m.put("rbio.search.complaint_number", "ఫిర్యాదు సంఖ్య");
        m.put("rbio.search.complaint_id", "ఫిర్యాదు ఐడీ");
        m.put("rbio.search.status_code", "స్థితి కోడ్");
        m.put("rbio.search.complainant_name", "ఫిర్యాదిదారు పేరు");
        m.put("rbio.search.complainant_mobile", "ఫిర్యాదిదారు మొబైల్");
        m.put("rbio.search.complainant_email", "ఫిర్యాదిదారు ఇమెయిల్");
        m.put("rbio.search.from_email", "పంపినవారి ఇమెయిల్ ఐడీ");
        m.put("rbio.search.mode_of_receipt", "స్వీకరణ విధానం");
        m.put("rbio.search.entity_name", "సంస్థ పేరు");
        m.put("rbio.search.category", "ఫిర్యాదు ప్రధాన వర్గం");
        m.put("rbio.search.reported_from", "నివేదించిన తేదీ (నుండి)");
        m.put("rbio.search.reported_to", "నివేదించిన తేదీ (వరకు)");
        m.put("rbio.search.subject", "విషయం");
        m.put("rbio.search.select_value", "ఒక విలువను ఎంచుకోండి");
        m.put("rbio.search.hint_partial", "పాక్షిక సరిపోలిక");
        m.put("rbio.search.hint_exact", "ఖచ్చితమైన సరిపోలిక అవసరం");
        m.put("rbio.search.search", "వెతకండి");
        m.put("rbio.search.cancel", "రద్దు");
        m.put("rbio.search.clear", "క్లియర్");
        m.put("rbio.search.adjust", "శోధనను మార్చండి");
        m.put("rbio.search.criteria_used", "మీ శోధన");
        m.put("rbio.search.no_results", "మీ శోధన ప్రమాణాలకు ఏ ఫిర్యాదు సరిపోలేదు.");
        m.put("rbio.search.error_term_too_short",
                "పాక్షిక సరిపోలిక క్షేత్రానికి కనీసం రెండు అక్షరాలు నమోదు చేయండి.");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "నోడల్ అధికారి ద్వారా శోధన ఇంకా అందుబాటులో లేదు.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "புகார் அடையாளம்");
        m.put("rbio.grid.col_complaint_number", "புகார் எண்");
        m.put("rbio.grid.col_from", "அனுப்புநர்");
        m.put("rbio.grid.col_sla_breach_in", "எஸ்எல்ஏ மீறலில்");
        m.put("rbio.grid.col_mode", "முறை");
        m.put("rbio.grid.col_complainant_name", "புகார்தாரர் பெயர்");
        m.put("rbio.grid.col_status", "நிலை");
        m.put("rbio.grid.col_entity_name", "நிறுவனப் பெயர்");
        m.put("rbio.grid.col_category", "புகார் வகை");
        m.put("rbio.grid.col_creation_date", "உருவாக்கிய தேதி");
        m.put("rbio.grid.col_subject", "பொருள்");
        m.put("rbio.grid.col_priority", "முன்னுரிமை");
        m.put("rbio.grid.col_assigned_to", "ஒப்படைக்கப்பட்டது");
        m.put("rbio.grid.filter_status", "நிலை வாரியாக வடிகட்டு");
        m.put("rbio.grid.filter_queue", "வரிசை வாரியாக வடிகட்டு");
        m.put("rbio.grid.all_statuses", "அனைத்தும்");
        m.put("rbio.grid.queue_assigned_to_me", "எனக்கு ஒப்படைக்கப்பட்ட புகார்கள்");
        m.put("rbio.grid.queue_all", "அனைத்து புகார்கள்");
        m.put("rbio.grid.refresh", "புதுப்பி");
        m.put("rbio.grid.search", "தேடு");
        m.put("rbio.grid.search_column", "நிரல் பெயரைத் தேடு");
        m.put("rbio.grid.showing", "காட்டுகிறது");
        m.put("rbio.grid.of", "இல்");
        m.put("rbio.grid.shown_after_refine", "வடிகட்டிய பின் காட்டப்பட்டது");
        m.put("rbio.grid.retry", "மீண்டும் முயல்");
        m.put("rbio.grid.no_complaints", "இந்த வரிசையில் புகார்கள் இல்லை.");
        m.put("rbio.grid.no_rows_after_refine", "இந்தப் பட்டியலில் எந்தப் புகாரும் உங்கள் வடிகட்டலுக்குப் பொருந்தவில்லை.");
        m.put("rbio.grid.error_load_failed",
                "புகார்களை ஏற்ற முடியவில்லை. மீண்டும் முயலுங்கள்; தொடர்ந்தால் உதவியைத் தொடர்பு கொள்ளுங்கள்.");
        m.put("rbio.grid.error_forbidden", "ஆர்பிஐஓ புகார்களைப் பார்க்க உங்களுக்கு அனுமதி இல்லை.");
        m.put("rbio.grid.error_filters_unavailable",
                "நிலை வடிகட்டிகள் ஏற்றப்படவில்லை, எனவே நிலை வாரியாக வடிகட்டல் கிடைக்கவில்லை. "
                        + "உங்கள் புகார்கள் இன்னும் பட்டியலிடப்பட்டுள்ளன.");
        m.put("rbio.stats.total_pending", "மொத்த நிலுவை புகார்கள்");
        m.put("rbio.stats.pending_with_me_page", "என்னிடம் நிலுவை (இந்தப் பட்டியலில்)");
        m.put("rbio.stats.pending_contact_page", "தொடர்பு நபரிடம் நிலுவை (இந்தப் பட்டியலில்)");
        m.put("rbio.stats.pending_meeting_page", "கூட்டம் திட்டமிடப்பட்டது (இந்தப் பட்டியலில்)");
        m.put("rbio.stats.sla_breach_page", "எஸ்எல்ஏ மீறல் (இந்தப் பட்டியலில்)");
        m.put("rbio.stats.re_overdue", "ஒழுங்குமுறை நிறுவனம் தாமதம்");
        m.put("rbio.band.legend", "வண்ண விளக்கம்:");
        m.put("rbio.band.clear", "வண்ணங்களை அழி");
        m.put("rbio.band.white", "புதியது (சிஆர்பிசி/போர்ட்டல்)");
        m.put("rbio.band.red", "ஒழுங்குமுறை நிறுவனத்திற்கு அனுப்பப்பட்டது");
        m.put("rbio.band.green", "ஒழுங்குமுறை நிறுவனத்திடமிருந்து பதில்");
        m.put("rbio.band.yellow", "செயல் அதிகாரிக்குத் திரும்ப அனுப்பப்பட்டது");
        m.put("rbio.band.pink", "திரும்பப் பெறப்பட்டது");
        m.put("rbio.band.blue", "சிஆர்பிசி தாமதம்");
        m.put("rbio.search.complaint_number", "புகார் எண்");
        m.put("rbio.search.complaint_id", "புகார் அடையாளம்");
        m.put("rbio.search.status_code", "நிலைக் குறியீடு");
        m.put("rbio.search.complainant_name", "புகார்தாரர் பெயர்");
        m.put("rbio.search.complainant_mobile", "புகார்தாரர் கைபேசி");
        m.put("rbio.search.complainant_email", "புகார்தாரர் மின்னஞ்சல்");
        m.put("rbio.search.from_email", "அனுப்புநர் மின்னஞ்சல் அடையாளம்");
        m.put("rbio.search.mode_of_receipt", "பெறும் முறை");
        m.put("rbio.search.entity_name", "நிறுவனப் பெயர்");
        m.put("rbio.search.category", "புகார் முதன்மை வகை");
        m.put("rbio.search.reported_from", "அறிவிக்கப்பட்ட தேதி (முதல்)");
        m.put("rbio.search.reported_to", "அறிவிக்கப்பட்ட தேதி (வரை)");
        m.put("rbio.search.subject", "பொருள்");
        m.put("rbio.search.select_value", "ஒரு மதிப்பைத் தேர்ந்தெடு");
        m.put("rbio.search.hint_partial", "பகுதி பொருத்தம்");
        m.put("rbio.search.hint_exact", "சரியான பொருத்தம் தேவை");
        m.put("rbio.search.search", "தேடு");
        m.put("rbio.search.cancel", "ரத்து");
        m.put("rbio.search.clear", "அழி");
        m.put("rbio.search.adjust", "தேடலை மாற்று");
        m.put("rbio.search.criteria_used", "உங்கள் தேடல்");
        m.put("rbio.search.no_results", "உங்கள் தேடல் அளவுகோல்களுக்கு எந்தப் புகாரும் பொருந்தவில்லை.");
        m.put("rbio.search.error_term_too_short",
                "பகுதி பொருத்தப் புலத்திற்கு குறைந்தது இரண்டு எழுத்துகளை உள்ளிடுங்கள்.");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "நோடல் அதிகாரி வாரியாகத் தேடல் இன்னும் கிடைக்கவில்லை.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "ફરિયાદ આઈડી");
        m.put("rbio.grid.col_complaint_number", "ફરિયાદ નંબર");
        m.put("rbio.grid.col_from", "પ્રેષક");
        m.put("rbio.grid.col_sla_breach_in", "એસએલએ ઉલ્લંઘનમાં");
        m.put("rbio.grid.col_mode", "માધ્યમ");
        m.put("rbio.grid.col_complainant_name", "ફરિયાદીનું નામ");
        m.put("rbio.grid.col_status", "સ્થિતિ");
        m.put("rbio.grid.col_entity_name", "સંસ્થાનું નામ");
        m.put("rbio.grid.col_category", "ફરિયાદ શ્રેણી");
        m.put("rbio.grid.col_creation_date", "બનાવ્યાની તારીખ");
        m.put("rbio.grid.col_subject", "વિષય");
        m.put("rbio.grid.col_priority", "પ્રાથમિકતા");
        m.put("rbio.grid.col_assigned_to", "સોંપાયેલ");
        m.put("rbio.grid.filter_status", "સ્થિતિ પ્રમાણે ગાળો");
        m.put("rbio.grid.filter_queue", "કતાર પ્રમાણે ગાળો");
        m.put("rbio.grid.all_statuses", "બધા");
        m.put("rbio.grid.queue_assigned_to_me", "મને સોંપાયેલી ફરિયાદો");
        m.put("rbio.grid.queue_all", "બધી ફરિયાદો");
        m.put("rbio.grid.refresh", "તાજું કરો");
        m.put("rbio.grid.search", "શોધો");
        m.put("rbio.grid.search_column", "કૉલમનું નામ શોધો");
        m.put("rbio.grid.showing", "બતાવે છે");
        m.put("rbio.grid.of", "માંથી");
        m.put("rbio.grid.shown_after_refine", "ગાળ્યા પછી બતાવેલ");
        m.put("rbio.grid.retry", "ફરી પ્રયાસ કરો");
        m.put("rbio.grid.no_complaints", "આ કતારમાં કોઈ ફરિયાદ નથી.");
        m.put("rbio.grid.no_rows_after_refine", "આ પાના પરની કોઈ ફરિયાદ તમારા ગાળણ સાથે મેળ ખાતી નથી.");
        m.put("rbio.grid.error_load_failed",
                "ફરિયાદો લોડ થઈ શકી નથી. ફરી પ્રયાસ કરો; ચાલુ રહે તો સહાયનો સંપર્ક કરો.");
        m.put("rbio.grid.error_forbidden", "તમને આરબીઆઈઓ ફરિયાદો જોવાની પરવાનગી નથી.");
        m.put("rbio.grid.error_filters_unavailable",
                "સ્થિતિ ગાળણો લોડ થઈ શક્યા નથી, તેથી સ્થિતિ પ્રમાણે ગાળણ ઉપલબ્ધ નથી. "
                        + "તમારી ફરિયાદો હજુ સૂચિબદ્ધ છે.");
        m.put("rbio.stats.total_pending", "કુલ બાકી ફરિયાદો");
        m.put("rbio.stats.pending_with_me_page", "મારી પાસે બાકી (આ પાના પર)");
        m.put("rbio.stats.pending_contact_page", "સંપર્ક વ્યક્તિ પાસે બાકી (આ પાના પર)");
        m.put("rbio.stats.pending_meeting_page", "બેઠક નિર્ધારિત (આ પાના પર)");
        m.put("rbio.stats.sla_breach_page", "એસએલએ ઉલ્લંઘન (આ પાના પર)");
        m.put("rbio.stats.re_overdue", "નિયંત્રિત સંસ્થા વિલંબિત");
        m.put("rbio.band.legend", "રંગ સૂચિ:");
        m.put("rbio.band.clear", "રંગો દૂર કરો");
        m.put("rbio.band.white", "નવી (સીઆરપીસી/પોર્ટલ)");
        m.put("rbio.band.red", "નિયંત્રિત સંસ્થાને મોકલેલી");
        m.put("rbio.band.green", "નિયંત્રિત સંસ્થા તરફથી જવાબ");
        m.put("rbio.band.yellow", "કાર્ય અધિકારીને પરત મોકલેલી");
        m.put("rbio.band.pink", "પાછી ખેંચેલી");
        m.put("rbio.band.blue", "સીઆરપીસી વિલંબિત");
        m.put("rbio.search.complaint_number", "ફરિયાદ નંબર");
        m.put("rbio.search.complaint_id", "ફરિયાદ આઈડી");
        m.put("rbio.search.status_code", "સ્થિતિ કોડ");
        m.put("rbio.search.complainant_name", "ફરિયાદીનું નામ");
        m.put("rbio.search.complainant_mobile", "ફરિયાદીનો મોબાઇલ");
        m.put("rbio.search.complainant_email", "ફરિયાદીનો ઈમેલ");
        m.put("rbio.search.from_email", "પ્રેષક ઈમેલ આઈડી");
        m.put("rbio.search.mode_of_receipt", "પ્રાપ્તિનું માધ્યમ");
        m.put("rbio.search.entity_name", "સંસ્થાનું નામ");
        m.put("rbio.search.category", "ફરિયાદ મુખ્ય શ્રેણી");
        m.put("rbio.search.reported_from", "નોંધ તારીખ (થી)");
        m.put("rbio.search.reported_to", "નોંધ તારીખ (સુધી)");
        m.put("rbio.search.subject", "વિષય");
        m.put("rbio.search.select_value", "એક મૂલ્ય પસંદ કરો");
        m.put("rbio.search.hint_partial", "આંશિક મેળ");
        m.put("rbio.search.hint_exact", "ચોક્કસ મેળ જરૂરી");
        m.put("rbio.search.search", "શોધો");
        m.put("rbio.search.cancel", "રદ કરો");
        m.put("rbio.search.clear", "સાફ કરો");
        m.put("rbio.search.adjust", "શોધ બદલો");
        m.put("rbio.search.criteria_used", "તમારી શોધ");
        m.put("rbio.search.no_results", "તમારા શોધ માપદંડ સાથે કોઈ ફરિયાદ મેળ ખાતી નથી.");
        m.put("rbio.search.error_term_too_short",
                "આંશિક મેળના ક્ષેત્ર માટે ઓછામાં ઓછા બે અક્ષર દાખલ કરો.");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "નોડલ અધિકારી દ્વારા શોધ હજુ ઉપલબ્ધ નથી.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "شکایت شناخت");
        m.put("rbio.grid.col_complaint_number", "شکایت نمبر");
        m.put("rbio.grid.col_from", "بھیجنے والا");
        m.put("rbio.grid.col_sla_breach_in", "ایس ایل اے خلاف ورزی میں");
        m.put("rbio.grid.col_mode", "طریقہ");
        m.put("rbio.grid.col_complainant_name", "شکایت کنندہ کا نام");
        m.put("rbio.grid.col_status", "حالت");
        m.put("rbio.grid.col_entity_name", "ادارے کا نام");
        m.put("rbio.grid.col_category", "شکایت کی قسم");
        m.put("rbio.grid.col_creation_date", "تخلیق کی تاریخ");
        m.put("rbio.grid.col_subject", "موضوع");
        m.put("rbio.grid.col_priority", "ترجیح");
        m.put("rbio.grid.col_assigned_to", "سونپا گیا");
        m.put("rbio.grid.filter_status", "حالت کے مطابق چھانٹیں");
        m.put("rbio.grid.filter_queue", "قطار کے مطابق چھانٹیں");
        m.put("rbio.grid.all_statuses", "تمام");
        m.put("rbio.grid.queue_assigned_to_me", "مجھے سونپی گئی شکایات");
        m.put("rbio.grid.queue_all", "تمام شکایات");
        m.put("rbio.grid.refresh", "تازہ کریں");
        m.put("rbio.grid.search", "تلاش");
        m.put("rbio.grid.search_column", "کالم کا نام تلاش کریں");
        m.put("rbio.grid.showing", "دکھا رہا ہے");
        m.put("rbio.grid.of", "میں سے");
        m.put("rbio.grid.shown_after_refine", "چھانٹنے کے بعد دکھایا گیا");
        m.put("rbio.grid.retry", "دوبارہ کوشش کریں");
        m.put("rbio.grid.no_complaints", "اس قطار میں کوئی شکایت نہیں ہے۔");
        m.put("rbio.grid.no_rows_after_refine", "اس صفحے کی کوئی شکایت آپ کی چھانٹی سے میل نہیں کھاتی۔");
        m.put("rbio.grid.error_load_failed",
                "شکایات لوڈ نہیں ہو سکیں۔ دوبارہ کوشش کریں؛ مسئلہ رہے تو معاونت سے رابطہ کریں۔");
        m.put("rbio.grid.error_forbidden", "آپ کو آر بی آئی او شکایات دیکھنے کی اجازت نہیں ہے۔");
        m.put("rbio.grid.error_filters_unavailable",
                "حالت کی چھانٹیاں لوڈ نہیں ہو سکیں، اس لیے حالت کے مطابق چھانٹنا دستیاب نہیں۔ "
                        + "آپ کی شکایات اب بھی فہرست میں ہیں۔");
        m.put("rbio.stats.total_pending", "کل زیر التواء شکایات");
        m.put("rbio.stats.pending_with_me_page", "میرے پاس زیر التواء (اس صفحے پر)");
        m.put("rbio.stats.pending_contact_page", "رابطہ فرد کے پاس زیر التواء (اس صفحے پر)");
        m.put("rbio.stats.pending_meeting_page", "اجلاس مقرر (اس صفحے پر)");
        m.put("rbio.stats.sla_breach_page", "ایس ایل اے خلاف ورزی (اس صفحے پر)");
        m.put("rbio.stats.re_overdue", "ضابطہ کار ادارہ تاخیر");
        m.put("rbio.band.legend", "رنگ کی کلید:");
        m.put("rbio.band.clear", "رنگ صاف کریں");
        m.put("rbio.band.white", "نئی (سی آر پی سی/پورٹل)");
        m.put("rbio.band.red", "ضابطہ کار ادارے کو بھیجی گئی");
        m.put("rbio.band.green", "ضابطہ کار ادارے سے جواب");
        m.put("rbio.band.yellow", "کارروائی افسر کو واپس بھیجی گئی");
        m.put("rbio.band.pink", "واپس لی گئی");
        m.put("rbio.band.blue", "سی آر پی سی تاخیر");
        m.put("rbio.search.complaint_number", "شکایت نمبر");
        m.put("rbio.search.complaint_id", "شکایت شناخت");
        m.put("rbio.search.status_code", "حالت کوڈ");
        m.put("rbio.search.complainant_name", "شکایت کنندہ کا نام");
        m.put("rbio.search.complainant_mobile", "شکایت کنندہ کا موبائل");
        m.put("rbio.search.complainant_email", "شکایت کنندہ کا ای میل");
        m.put("rbio.search.from_email", "بھیجنے والے کا ای میل");
        m.put("rbio.search.mode_of_receipt", "وصولی کا طریقہ");
        m.put("rbio.search.entity_name", "ادارے کا نام");
        m.put("rbio.search.category", "شکایت کی بنیادی قسم");
        m.put("rbio.search.reported_from", "اطلاع کی تاریخ (سے)");
        m.put("rbio.search.reported_to", "اطلاع کی تاریخ (تک)");
        m.put("rbio.search.subject", "موضوع");
        m.put("rbio.search.select_value", "ایک قیمت منتخب کریں");
        m.put("rbio.search.hint_partial", "جزوی مطابقت");
        m.put("rbio.search.hint_exact", "مکمل مطابقت ضروری");
        m.put("rbio.search.search", "تلاش");
        m.put("rbio.search.cancel", "منسوخ");
        m.put("rbio.search.clear", "صاف کریں");
        m.put("rbio.search.adjust", "تلاش تبدیل کریں");
        m.put("rbio.search.criteria_used", "آپ کی تلاش");
        m.put("rbio.search.no_results", "آپ کے تلاش کے معیار سے کوئی شکایت میل نہیں کھاتی۔");
        m.put("rbio.search.error_term_too_short",
                "جزوی مطابقت والے خانے کے لیے کم از کم دو حروف درج کریں۔");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "نوڈل افسر کے ذریعے تلاش ابھی دستیاب نہیں۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "ದೂರು ಐಡಿ");
        m.put("rbio.grid.col_complaint_number", "ದೂರು ಸಂಖ್ಯೆ");
        m.put("rbio.grid.col_from", "ಕಳುಹಿಸಿದವರು");
        m.put("rbio.grid.col_sla_breach_in", "ಎಸ್‌ಎಲ್‌ಎ ಉಲ್ಲಂಘನೆಯಲ್ಲಿ");
        m.put("rbio.grid.col_mode", "ವಿಧಾನ");
        m.put("rbio.grid.col_complainant_name", "ದೂರುದಾರರ ಹೆಸರು");
        m.put("rbio.grid.col_status", "ಸ್ಥಿತಿ");
        m.put("rbio.grid.col_entity_name", "ಸಂಸ್ಥೆಯ ಹೆಸರು");
        m.put("rbio.grid.col_category", "ದೂರಿನ ವರ್ಗ");
        m.put("rbio.grid.col_creation_date", "ಸೃಷ್ಟಿ ದಿನಾಂಕ");
        m.put("rbio.grid.col_subject", "ವಿಷಯ");
        m.put("rbio.grid.col_priority", "ಆದ್ಯತೆ");
        m.put("rbio.grid.col_assigned_to", "ನಿಯೋಜಿಸಲಾಗಿದೆ");
        m.put("rbio.grid.filter_status", "ಸ್ಥಿತಿಯ ಪ್ರಕಾರ ಶೋಧಿಸಿ");
        m.put("rbio.grid.filter_queue", "ಸರದಿಯ ಪ್ರಕಾರ ಶೋಧಿಸಿ");
        m.put("rbio.grid.all_statuses", "ಎಲ್ಲಾ");
        m.put("rbio.grid.queue_assigned_to_me", "ನನಗೆ ನಿಯೋಜಿಸಿದ ದೂರುಗಳು");
        m.put("rbio.grid.queue_all", "ಎಲ್ಲಾ ದೂರುಗಳು");
        m.put("rbio.grid.refresh", "ರಿಫ್ರೆಶ್");
        m.put("rbio.grid.search", "ಹುಡುಕಿ");
        m.put("rbio.grid.search_column", "ಕಾಲಮ್ ಹೆಸರು ಹುಡುಕಿ");
        m.put("rbio.grid.showing", "ತೋರಿಸುತ್ತಿದೆ");
        m.put("rbio.grid.of", "ರಲ್ಲಿ");
        m.put("rbio.grid.shown_after_refine", "ಶೋಧಿಸಿದ ನಂತರ ತೋರಿಸಿದವು");
        m.put("rbio.grid.retry", "ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ");
        m.put("rbio.grid.no_complaints", "ಈ ಸರದಿಯಲ್ಲಿ ದೂರುಗಳಿಲ್ಲ.");
        m.put("rbio.grid.no_rows_after_refine", "ಈ ಪುಟದ ಯಾವುದೇ ದೂರು ನಿಮ್ಮ ಶೋಧನೆಗೆ ಹೊಂದಿಕೆಯಾಗಿಲ್ಲ.");
        m.put("rbio.grid.error_load_failed",
                "ದೂರುಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಿಲ್ಲ. ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ; ಮುಂದುವರಿದರೆ ಸಹಾಯವನ್ನು ಸಂಪರ್ಕಿಸಿ.");
        m.put("rbio.grid.error_forbidden", "ಆರ್‌ಬಿಐಒ ದೂರುಗಳನ್ನು ನೋಡಲು ನಿಮಗೆ ಅನುಮತಿ ಇಲ್ಲ.");
        m.put("rbio.grid.error_filters_unavailable",
                "ಸ್ಥಿತಿ ಶೋಧಕಗಳು ಲೋಡ್ ಆಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಸ್ಥಿತಿಯ ಪ್ರಕಾರ ಶೋಧನೆ ಲಭ್ಯವಿಲ್ಲ. "
                        + "ನಿಮ್ಮ ದೂರುಗಳು ಇನ್ನೂ ಪಟ್ಟಿಯಲ್ಲಿವೆ.");
        m.put("rbio.stats.total_pending", "ಒಟ್ಟು ಬಾಕಿ ದೂರುಗಳು");
        m.put("rbio.stats.pending_with_me_page", "ನನ್ನ ಬಳಿ ಬಾಕಿ (ಈ ಪುಟದಲ್ಲಿ)");
        m.put("rbio.stats.pending_contact_page", "ಸಂಪರ್ಕ ವ್ಯಕ್ತಿಯ ಬಳಿ ಬಾಕಿ (ಈ ಪುಟದಲ್ಲಿ)");
        m.put("rbio.stats.pending_meeting_page", "ಸಭೆ ನಿಗದಿ (ಈ ಪುಟದಲ್ಲಿ)");
        m.put("rbio.stats.sla_breach_page", "ಎಸ್‌ಎಲ್‌ಎ ಉಲ್ಲಂಘನೆ (ಈ ಪುಟದಲ್ಲಿ)");
        m.put("rbio.stats.re_overdue", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆ ವಿಳಂಬ");
        m.put("rbio.band.legend", "ಬಣ್ಣ ಸೂಚಿ:");
        m.put("rbio.band.clear", "ಬಣ್ಣಗಳನ್ನು ತೆಗೆದುಹಾಕಿ");
        m.put("rbio.band.white", "ಹೊಸದು (ಸಿಆರ್‌ಪಿಸಿ/ಪೋರ್ಟಲ್)");
        m.put("rbio.band.red", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಗೆ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("rbio.band.green", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆಯಿಂದ ಪ್ರತಿಕ್ರಿಯೆ");
        m.put("rbio.band.yellow", "ಕಾರ್ಯ ಅಧಿಕಾರಿಗೆ ಹಿಂತಿರುಗಿಸಲಾಗಿದೆ");
        m.put("rbio.band.pink", "ಹಿಂಪಡೆಯಲಾಗಿದೆ");
        m.put("rbio.band.blue", "ಸಿಆರ್‌ಪಿಸಿ ವಿಳಂಬ");
        m.put("rbio.search.complaint_number", "ದೂರು ಸಂಖ್ಯೆ");
        m.put("rbio.search.complaint_id", "ದೂರು ಐಡಿ");
        m.put("rbio.search.status_code", "ಸ್ಥಿತಿ ಕೋಡ್");
        m.put("rbio.search.complainant_name", "ದೂರುದಾರರ ಹೆಸರು");
        m.put("rbio.search.complainant_mobile", "ದೂರುದಾರರ ಮೊಬೈಲ್");
        m.put("rbio.search.complainant_email", "ದೂರುದಾರರ ಇಮೇಲ್");
        m.put("rbio.search.from_email", "ಕಳುಹಿಸಿದವರ ಇಮೇಲ್ ಐಡಿ");
        m.put("rbio.search.mode_of_receipt", "ಸ್ವೀಕೃತಿ ವಿಧಾನ");
        m.put("rbio.search.entity_name", "ಸಂಸ್ಥೆಯ ಹೆಸರು");
        m.put("rbio.search.category", "ದೂರಿನ ಮುಖ್ಯ ವರ್ಗ");
        m.put("rbio.search.reported_from", "ವರದಿ ದಿನಾಂಕ (ಇಂದ)");
        m.put("rbio.search.reported_to", "ವರದಿ ದಿನಾಂಕ (ವರೆಗೆ)");
        m.put("rbio.search.subject", "ವಿಷಯ");
        m.put("rbio.search.select_value", "ಒಂದು ಮೌಲ್ಯವನ್ನು ಆಯ್ಕೆಮಾಡಿ");
        m.put("rbio.search.hint_partial", "ಭಾಗಶಃ ಹೊಂದಿಕೆ");
        m.put("rbio.search.hint_exact", "ನಿಖರ ಹೊಂದಿಕೆ ಅಗತ್ಯ");
        m.put("rbio.search.search", "ಹುಡುಕಿ");
        m.put("rbio.search.cancel", "ರದ್ದು");
        m.put("rbio.search.clear", "ಅಳಿಸಿ");
        m.put("rbio.search.adjust", "ಹುಡುಕಾಟ ಬದಲಿಸಿ");
        m.put("rbio.search.criteria_used", "ನಿಮ್ಮ ಹುಡುಕಾಟ");
        m.put("rbio.search.no_results", "ನಿಮ್ಮ ಹುಡುಕಾಟ ಮಾನದಂಡಕ್ಕೆ ಯಾವುದೇ ದೂರು ಹೊಂದಿಕೆಯಾಗಿಲ್ಲ.");
        m.put("rbio.search.error_term_too_short",
                "ಭಾಗಶಃ ಹೊಂದಿಕೆಯ ಕ್ಷೇತ್ರಕ್ಕೆ ಕನಿಷ್ಠ ಎರಡು ಅಕ್ಷರಗಳನ್ನು ನಮೂದಿಸಿ.");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "ನೋಡಲ್ ಅಧಿಕಾರಿಯ ಮೂಲಕ ಹುಡುಕಾಟ ಇನ್ನೂ ಲಭ್ಯವಿಲ್ಲ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.grid.col_complaint_id", "പരാതി ഐഡി");
        m.put("rbio.grid.col_complaint_number", "പരാതി നമ്പർ");
        m.put("rbio.grid.col_from", "അയച്ചത്");
        m.put("rbio.grid.col_sla_breach_in", "എസ്എൽഎ ലംഘനത്തിൽ");
        m.put("rbio.grid.col_mode", "രീതി");
        m.put("rbio.grid.col_complainant_name", "പരാതിക്കാരന്റെ പേര്");
        m.put("rbio.grid.col_status", "നില");
        m.put("rbio.grid.col_entity_name", "സ്ഥാപനത്തിന്റെ പേര്");
        m.put("rbio.grid.col_category", "പരാതി വിഭാഗം");
        m.put("rbio.grid.col_creation_date", "സൃഷ്ടിച്ച തീയതി");
        m.put("rbio.grid.col_subject", "വിഷയം");
        m.put("rbio.grid.col_priority", "മുൻഗണന");
        m.put("rbio.grid.col_assigned_to", "നിയോഗിച്ചത്");
        m.put("rbio.grid.filter_status", "നില അനുസരിച്ച് അരിക്കുക");
        m.put("rbio.grid.filter_queue", "ക്യൂ അനുസരിച്ച് അരിക്കുക");
        m.put("rbio.grid.all_statuses", "എല്ലാം");
        m.put("rbio.grid.queue_assigned_to_me", "എനിക്ക് നിയോഗിച്ച പരാതികൾ");
        m.put("rbio.grid.queue_all", "എല്ലാ പരാതികളും");
        m.put("rbio.grid.refresh", "പുതുക്കുക");
        m.put("rbio.grid.search", "തിരയുക");
        m.put("rbio.grid.search_column", "കോളം പേര് തിരയുക");
        m.put("rbio.grid.showing", "കാണിക്കുന്നു");
        m.put("rbio.grid.of", "ഇൽ");
        m.put("rbio.grid.shown_after_refine", "അരിച്ചതിന് ശേഷം കാണിച്ചത്");
        m.put("rbio.grid.retry", "വീണ്ടും ശ്രമിക്കുക");
        m.put("rbio.grid.no_complaints", "ഈ ക്യൂവിൽ പരാതികളില്ല.");
        m.put("rbio.grid.no_rows_after_refine", "ഈ പേജിലെ ഒരു പരാതിയും നിങ്ങളുടെ അരിപ്പയോട് യോജിക്കുന്നില്ല.");
        m.put("rbio.grid.error_load_failed",
                "പരാതികൾ ലോഡ് ചെയ്യാനായില്ല. വീണ്ടും ശ്രമിക്കുക; തുടരുന്നെങ്കിൽ സഹായവുമായി ബന്ധപ്പെടുക.");
        m.put("rbio.grid.error_forbidden", "ആർബിഐഒ പരാതികൾ കാണാൻ നിങ്ങൾക്ക് അനുമതിയില്ല.");
        m.put("rbio.grid.error_filters_unavailable",
                "നില അരിപ്പകൾ ലോഡ് ചെയ്യാനായില്ല, അതിനാൽ നില അനുസരിച്ചുള്ള അരിക്കൽ ലഭ്യമല്ല. "
                        + "നിങ്ങളുടെ പരാതികൾ ഇപ്പോഴും പട്ടികയിലുണ്ട്.");
        m.put("rbio.stats.total_pending", "മൊത്തം തീർപ്പാകാത്ത പരാതികൾ");
        m.put("rbio.stats.pending_with_me_page", "എന്റെ പക്കൽ തീർപ്പാകാത്തത് (ഈ പേജിൽ)");
        m.put("rbio.stats.pending_contact_page", "ബന്ധപ്പെടേണ്ട വ്യക്തിയുടെ പക്കൽ (ഈ പേജിൽ)");
        m.put("rbio.stats.pending_meeting_page", "യോഗം നിശ്ചയിച്ചു (ഈ പേജിൽ)");
        m.put("rbio.stats.sla_breach_page", "എസ്എൽഎ ലംഘനം (ഈ പേജിൽ)");
        m.put("rbio.stats.re_overdue", "നിയന്ത്രിത സ്ഥാപനം വൈകി");
        m.put("rbio.band.legend", "വർണ്ണ സൂചിക:");
        m.put("rbio.band.clear", "വർണ്ണങ്ങൾ മായ്ക്കുക");
        m.put("rbio.band.white", "പുതിയത് (സിആർപിസി/പോർട്ടൽ)");
        m.put("rbio.band.red", "നിയന്ത്രിത സ്ഥാപനത്തിന് അയച്ചു");
        m.put("rbio.band.green", "നിയന്ത്രിത സ്ഥാപനത്തിൽ നിന്ന് മറുപടി");
        m.put("rbio.band.yellow", "നടപടി ഓഫീസർക്ക് തിരികെ അയച്ചു");
        m.put("rbio.band.pink", "പിൻവലിച്ചു");
        m.put("rbio.band.blue", "സിആർപിസി വൈകി");
        m.put("rbio.search.complaint_number", "പരാതി നമ്പർ");
        m.put("rbio.search.complaint_id", "പരാതി ഐഡി");
        m.put("rbio.search.status_code", "നില കോഡ്");
        m.put("rbio.search.complainant_name", "പരാതിക്കാരന്റെ പേര്");
        m.put("rbio.search.complainant_mobile", "പരാതിക്കാരന്റെ മൊബൈൽ");
        m.put("rbio.search.complainant_email", "പരാതിക്കാരന്റെ ഇമെയിൽ");
        m.put("rbio.search.from_email", "അയച്ചവരുടെ ഇമെയിൽ ഐഡി");
        m.put("rbio.search.mode_of_receipt", "ലഭിച്ച രീതി");
        m.put("rbio.search.entity_name", "സ്ഥാപനത്തിന്റെ പേര്");
        m.put("rbio.search.category", "പരാതിയുടെ പ്രധാന വിഭാഗം");
        m.put("rbio.search.reported_from", "റിപ്പോർട്ട് തീയതി (മുതൽ)");
        m.put("rbio.search.reported_to", "റിപ്പോർട്ട് തീയതി (വരെ)");
        m.put("rbio.search.subject", "വിഷയം");
        m.put("rbio.search.select_value", "ഒരു മൂല്യം തിരഞ്ഞെടുക്കുക");
        m.put("rbio.search.hint_partial", "ഭാഗിക പൊരുത്തം");
        m.put("rbio.search.hint_exact", "കൃത്യമായ പൊരുത്തം ആവശ്യം");
        m.put("rbio.search.search", "തിരയുക");
        m.put("rbio.search.cancel", "റദ്ദാക്കുക");
        m.put("rbio.search.clear", "മായ്ക്കുക");
        m.put("rbio.search.adjust", "തിരയൽ മാറ്റുക");
        m.put("rbio.search.criteria_used", "നിങ്ങളുടെ തിരയൽ");
        m.put("rbio.search.no_results", "നിങ്ങളുടെ തിരയൽ മാനദണ്ഡവുമായി ഒരു പരാതിയും യോജിക്കുന്നില്ല.");
        m.put("rbio.search.error_term_too_short",
                "ഭാഗിക പൊരുത്ത ഫീൽഡിന് കുറഞ്ഞത് രണ്ട് അക്ഷരങ്ങൾ നൽകുക.");
        m.put("rbio.search.error_nodal_officer_unsupported",
                "നോഡൽ ഓഫീസർ വഴിയുള്ള തിരയൽ ഇനിയും ലഭ്യമല്ല.");
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
