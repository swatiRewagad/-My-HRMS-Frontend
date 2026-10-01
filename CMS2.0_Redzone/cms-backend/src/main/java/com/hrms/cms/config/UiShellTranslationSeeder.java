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
 * Translations for the shared staff shell, the unified navigation and the common UI vocabulary
 * introduced by the UI homogenisation batch.
 *
 * A NEW seeder rather than an edit to an existing one, per the convention here: several sessions add
 * keys concurrently and a shared file is a guaranteed conflict in a place where a conflict silently
 * costs a locale.
 *
 * The {@code ui.*} prefix is deliberate and was verified unused before this was written. Seeding is
 * insert-if-absent by key code, so a duplicate code anywhere would silently keep the FIRST text and
 * this file's value would never appear.
 *
 * Why this seeder exists at all: CRPC and CEPC had ZERO uses of the translate pipe -- every label was
 * hardcoded English -- while AA had 344. Staff-facing chrome could therefore never be localised, and
 * the header's language button was decorative. These keys are what let the shared shell render in all
 * ten locales.
 *
 * Scope limit worth stating: these are interface labels only. No key here names a clause, a statutory
 * ground or a compensation limit. Translating a legal label is a legal statement about what the law
 * means, so those remain English-only pending sign-off.
 */
@Component
@Order(60)
public class UiShellTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "ui";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public UiShellTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // ═══ Shell chrome ═══
        m.put("ui.shell.rbi_hindi", "भारतीय रिज़र्व बैंक");
        m.put("ui.shell.rbi_english", "RESERVE BANK OF INDIA");
        m.put("ui.shell.cms_short", "CMS");
        m.put("ui.shell.cms_full", "Complaint Management System");
        m.put("ui.shell.logout", "Sign out");
        m.put("ui.shell.collapse", "Collapse");
        m.put("ui.shell.primary_navigation", "Primary navigation");
        m.put("ui.shell.dismiss_notification", "Dismiss notification");
        m.put("ui.shell.change_language", "Change language");

        // ═══ Unified navigation ═══
        m.put("ui.nav.crpc_complaints", "CRPC Complaints");
        m.put("ui.nav.cepc_dashboard", "CEPC Complaints");
        m.put("ui.nav.rbio_workbench", "RBIO Workbench");
        m.put("ui.nav.aa_appeals", "Appeals");
        m.put("ui.nav.re_portal", "Regulated Entity");
        m.put("ui.nav.reports", "Reports");

        // ═══ Role labels shown under the user's name ═══
        m.put("ui.role.crpc_deo", "CRPC Data Entry Operator");
        m.put("ui.role.crpc_reviewer", "CRPC Reviewer");
        m.put("ui.role.crpc_incharge", "CRPC In-Charge");
        m.put("ui.role.cepc_officer", "CEPC Officer");
        m.put("ui.role.rbio_officer", "RBIO Officer");
        m.put("ui.role.staff", "Staff");

        // ═══ Page titles ═══
        m.put("ui.page.crpc_complaints", "CRPC Complaints");
        m.put("ui.page.cepc_dashboard", "CEPC Complaint Dashboard");
        m.put("ui.page.rbio_home", "RBIO Complaint Workbench");
        m.put("ui.page.re_dashboard", "Complaints Awaiting Response");

        // ═══ Common table / grid vocabulary ═══
        m.put("ui.common.search", "Search");
        m.put("ui.common.reset", "Reset");
        m.put("ui.common.filters", "Filters");
        m.put("ui.common.advanced_search", "Advanced Search");
        m.put("ui.common.clear_filters", "Clear Filters");
        m.put("ui.common.refresh", "Refresh");
        m.put("ui.common.export", "Export");
        m.put("ui.common.columns", "Columns");
        m.put("ui.common.save", "Save");
        m.put("ui.common.cancel", "Cancel");
        m.put("ui.common.close", "Close");
        m.put("ui.common.submit", "Submit");
        m.put("ui.common.confirm", "Confirm");
        m.put("ui.common.back", "Back");
        m.put("ui.common.next", "Next");
        m.put("ui.common.previous", "Previous");
        m.put("ui.common.retry", "Retry");
        m.put("ui.common.view", "View");
        m.put("ui.common.actions", "Actions");
        m.put("ui.common.loading", "Loading…");
        m.put("ui.common.no_records", "No records to display.");
        m.put("ui.common.load_failed", "The data could not be loaded. Please try again.");
        m.put("ui.common.page_of", "Page {{current}} of {{total}}");
        m.put("ui.common.rows_per_page", "Rows per page");
        m.put("ui.common.showing_count", "Showing {{shown}} of {{total}} entries");
        m.put("ui.common.all", "All");
        m.put("ui.common.yes", "Yes");
        m.put("ui.common.no", "No");
        m.put("ui.common.required_field", "This field is required.");

        // ═══ CEPC queue and actions ═══
        // CEPC had ZERO uses of the translate pipe, so these labels were hardcoded English in the
        // template. The keys live under the cepc.* namespace rather than ui.*, matching where the
        // module's own vocabulary belongs.
        m.put("crpc.status.assessment_complete", "Assessment Complete");
        m.put("crpc.queue.assigned_to_me", "Complaints Assigned To Me");
        m.put("common.skip_to_content", "Skip to main content");
        m.put("cepc.queue.assigned_to_me", "Assigned To Me");
        m.put("cepc.queue.all_complaints", "All Complaints");
        m.put("cepc.action.create_complaint", "Create Complaint");

        // ═══ Notifications ═══
        m.put("ui.toast.saved", "Your changes have been saved.");
        m.put("ui.toast.save_failed", "The changes could not be saved. Please try again.");
        m.put("ui.toast.action_failed", "That action could not be completed. Please try again.");
        m.put("ui.toast.officers_synced", "{{count}} new officer(s) synced from the directory.");
        m.put("ui.toast.officers_already_synced", "All directory users are already in the pool.");
        m.put("ui.toast.officer_sync_failed", "Users could not be fetched from the directory.");

        // ═══ Attachment limits (13.1) ═══
        // Text carries no digits of its own; the limit is interpolated from configuration, so the
        // hint can never again contradict what the server actually enforces.
        m.put("ui.upload.max_file_size", "Maximum {{size}} MB per file.");
        m.put("ui.upload.max_total_size", "Maximum {{size}} MB in total per complaint.");
        m.put("ui.upload.error_file_too_large", "Each file must be {{size}} MB or smaller.");
        m.put("ui.upload.error_total_too_large", "All attachments together must be {{size}} MB or smaller.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "भारतीय रिज़र्व बैंक");
        m.put("ui.shell.cms_full", "शिकायत प्रबंधन प्रणाली");
        m.put("ui.shell.logout", "साइन आउट");
        m.put("ui.shell.collapse", "संक्षिप्त करें");
        m.put("ui.shell.primary_navigation", "मुख्य नेविगेशन");
        m.put("ui.shell.dismiss_notification", "सूचना हटाएँ");
        m.put("ui.shell.change_language", "भाषा बदलें");
        m.put("ui.nav.crpc_complaints", "सीआरपीसी शिकायतें");
        m.put("ui.nav.cepc_dashboard", "सीईपीसी शिकायतें");
        m.put("ui.nav.rbio_workbench", "आरबीआईओ कार्यक्षेत्र");
        m.put("ui.nav.aa_appeals", "अपील");
        m.put("ui.nav.re_portal", "विनियमित संस्था");
        m.put("ui.nav.reports", "रिपोर्ट");
        m.put("ui.role.crpc_deo", "सीआरपीसी डेटा एंट्री ऑपरेटर");
        m.put("ui.role.crpc_reviewer", "सीआरपीसी समीक्षक");
        m.put("ui.role.crpc_incharge", "सीआरपीसी प्रभारी");
        m.put("ui.role.cepc_officer", "सीईपीसी अधिकारी");
        m.put("ui.role.rbio_officer", "आरबीआईओ अधिकारी");
        m.put("ui.role.staff", "कर्मचारी");
        m.put("ui.page.crpc_complaints", "सीआरपीसी शिकायतें");
        m.put("ui.page.cepc_dashboard", "सीईपीसी शिकायत डैशबोर्ड");
        m.put("ui.page.rbio_home", "आरबीआईओ शिकायत कार्यक्षेत्र");
        m.put("ui.page.re_dashboard", "उत्तर की प्रतीक्षा में शिकायतें");
        m.put("ui.common.search", "खोजें");
        m.put("ui.common.reset", "रीसेट करें");
        m.put("ui.common.filters", "फ़िल्टर");
        m.put("ui.common.advanced_search", "उन्नत खोज");
        m.put("ui.common.clear_filters", "फ़िल्टर हटाएँ");
        m.put("ui.common.refresh", "ताज़ा करें");
        m.put("ui.common.export", "निर्यात");
        m.put("ui.common.columns", "स्तंभ");
        m.put("ui.common.save", "सहेजें");
        m.put("ui.common.cancel", "रद्द करें");
        m.put("ui.common.close", "बंद करें");
        m.put("ui.common.submit", "प्रस्तुत करें");
        m.put("ui.common.confirm", "पुष्टि करें");
        m.put("ui.common.back", "पीछे");
        m.put("ui.common.next", "आगे");
        m.put("ui.common.previous", "पिछला");
        m.put("ui.common.retry", "पुनः प्रयास करें");
        m.put("ui.common.view", "देखें");
        m.put("ui.common.actions", "कार्य");
        m.put("ui.common.loading", "लोड हो रहा है…");
        m.put("ui.common.no_records", "प्रदर्शित करने के लिए कोई रिकॉर्ड नहीं है।");
        m.put("ui.common.load_failed", "डेटा लोड नहीं हो सका। कृपया पुनः प्रयास करें।");
        m.put("ui.common.page_of", "पृष्ठ {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "प्रति पृष्ठ पंक्तियाँ");
        m.put("ui.common.showing_count", "{{total}} में से {{shown}} दिखाए जा रहे हैं");
        m.put("ui.common.all", "सभी");
        m.put("ui.common.yes", "हाँ");
        m.put("ui.common.no", "नहीं");
        m.put("ui.common.required_field", "यह फ़ील्ड आवश्यक है।");
        m.put("crpc.status.assessment_complete", "मूल्यांकन पूर्ण");
        m.put("crpc.queue.assigned_to_me", "मुझे सौंपी गई शिकायतें");
        m.put("common.skip_to_content", "मुख्य सामग्री पर जाएँ");
        m.put("cepc.queue.assigned_to_me", "मुझे सौंपी गई");
        m.put("cepc.queue.all_complaints", "सभी शिकायतें");
        m.put("cepc.action.create_complaint", "शिकायत बनाएँ");
        m.put("ui.toast.saved", "आपके परिवर्तन सहेज लिए गए हैं।");
        m.put("ui.toast.save_failed", "परिवर्तन सहेजे नहीं जा सके। कृपया पुनः प्रयास करें।");
        m.put("ui.toast.action_failed", "यह कार्य पूरा नहीं हो सका। कृपया पुनः प्रयास करें।");
        m.put("ui.toast.officers_synced", "निर्देशिका से {{count}} नए अधिकारी समन्वयित किए गए।");
        m.put("ui.toast.officers_already_synced", "निर्देशिका के सभी उपयोगकर्ता पहले से पूल में हैं।");
        m.put("ui.toast.officer_sync_failed", "निर्देशिका से उपयोगकर्ता प्राप्त नहीं किए जा सके।");
        m.put("ui.upload.max_file_size", "प्रति फ़ाइल अधिकतम {{size}} एमबी।");
        m.put("ui.upload.max_total_size", "प्रति शिकायत कुल अधिकतम {{size}} एमबी।");
        m.put("ui.upload.error_file_too_large", "प्रत्येक फ़ाइल {{size}} एमबी या उससे छोटी होनी चाहिए।");
        m.put("ui.upload.error_total_too_large", "सभी अनुलग्नक मिलाकर {{size}} एमबी या उससे कम होने चाहिए।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "भारतीय रिझर्व्ह बँक");
        m.put("ui.shell.cms_full", "तक्रार व्यवस्थापन प्रणाली");
        m.put("ui.shell.logout", "साइन आउट");
        m.put("ui.shell.collapse", "लहान करा");
        m.put("ui.shell.primary_navigation", "मुख्य नेव्हिगेशन");
        m.put("ui.shell.dismiss_notification", "सूचना काढा");
        m.put("ui.shell.change_language", "भाषा बदला");
        m.put("ui.nav.crpc_complaints", "सीआरपीसी तक्रारी");
        m.put("ui.nav.cepc_dashboard", "सीईपीसी तक्रारी");
        m.put("ui.nav.rbio_workbench", "आरबीआयओ कार्यक्षेत्र");
        m.put("ui.nav.aa_appeals", "अपील");
        m.put("ui.nav.re_portal", "नियंत्रित संस्था");
        m.put("ui.nav.reports", "अहवाल");
        m.put("ui.role.crpc_deo", "सीआरपीसी डेटा एंट्री ऑपरेटर");
        m.put("ui.role.crpc_reviewer", "सीआरपीसी समीक्षक");
        m.put("ui.role.crpc_incharge", "सीआरपीसी प्रभारी");
        m.put("ui.role.cepc_officer", "सीईपीसी अधिकारी");
        m.put("ui.role.rbio_officer", "आरबीआयओ अधिकारी");
        m.put("ui.role.staff", "कर्मचारी");
        m.put("ui.page.crpc_complaints", "सीआरपीसी तक्रारी");
        m.put("ui.page.cepc_dashboard", "सीईपीसी तक्रार डॅशबोर्ड");
        m.put("ui.page.rbio_home", "आरबीआयओ तक्रार कार्यक्षेत्र");
        m.put("ui.page.re_dashboard", "उत्तराच्या प्रतीक्षेत असलेल्या तक्रारी");
        m.put("ui.common.search", "शोधा");
        m.put("ui.common.reset", "रीसेट करा");
        m.put("ui.common.filters", "फिल्टर");
        m.put("ui.common.advanced_search", "प्रगत शोध");
        m.put("ui.common.clear_filters", "फिल्टर काढा");
        m.put("ui.common.refresh", "रिफ्रेश करा");
        m.put("ui.common.export", "निर्यात");
        m.put("ui.common.columns", "स्तंभ");
        m.put("ui.common.save", "जतन करा");
        m.put("ui.common.cancel", "रद्द करा");
        m.put("ui.common.close", "बंद करा");
        m.put("ui.common.submit", "सादर करा");
        m.put("ui.common.confirm", "निश्चित करा");
        m.put("ui.common.back", "मागे");
        m.put("ui.common.next", "पुढे");
        m.put("ui.common.previous", "मागील");
        m.put("ui.common.retry", "पुन्हा प्रयत्न करा");
        m.put("ui.common.view", "पहा");
        m.put("ui.common.actions", "क्रिया");
        m.put("ui.common.loading", "लोड होत आहे…");
        m.put("ui.common.no_records", "दर्शविण्यासाठी कोणतीही नोंद नाही.");
        m.put("ui.common.load_failed", "डेटा लोड होऊ शकला नाही. कृपया पुन्हा प्रयत्न करा.");
        m.put("ui.common.page_of", "पृष्ठ {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "प्रति पृष्ठ ओळी");
        m.put("ui.common.showing_count", "{{total}} पैकी {{shown}} दर्शवित आहे");
        m.put("ui.common.all", "सर्व");
        m.put("ui.common.yes", "होय");
        m.put("ui.common.no", "नाही");
        m.put("ui.common.required_field", "हे फील्ड आवश्यक आहे.");
        m.put("crpc.status.assessment_complete", "मूल्यांकन पूर्ण");
        m.put("crpc.queue.assigned_to_me", "मला नेमून दिलेल्या तक्रारी");
        m.put("common.skip_to_content", "मुख्य मजकुरावर जा");
        m.put("cepc.queue.assigned_to_me", "मला नेमून दिलेल्या");
        m.put("cepc.queue.all_complaints", "सर्व तक्रारी");
        m.put("cepc.action.create_complaint", "तक्रार तयार करा");
        m.put("ui.toast.saved", "तुमचे बदल जतन केले आहेत.");
        m.put("ui.toast.save_failed", "बदल जतन होऊ शकले नाहीत. कृपया पुन्हा प्रयत्न करा.");
        m.put("ui.toast.action_failed", "ही क्रिया पूर्ण होऊ शकली नाही. कृपया पुन्हा प्रयत्न करा.");
        m.put("ui.toast.officers_synced", "निर्देशिकेतून {{count}} नवीन अधिकारी समक्रमित केले.");
        m.put("ui.toast.officers_already_synced", "निर्देशिकेतील सर्व वापरकर्ते आधीच पूलमध्ये आहेत.");
        m.put("ui.toast.officer_sync_failed", "निर्देशिकेतून वापरकर्ते मिळू शकले नाहीत.");
        m.put("ui.upload.max_file_size", "प्रति फाइल कमाल {{size}} एमबी.");
        m.put("ui.upload.max_total_size", "प्रति तक्रार एकूण कमाल {{size}} एमबी.");
        m.put("ui.upload.error_file_too_large", "प्रत्येक फाइल {{size}} एमबी किंवा त्याहून लहान असावी.");
        m.put("ui.upload.error_total_too_large", "सर्व संलग्नके मिळून {{size}} एमबी किंवा कमी असावीत.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "ভারতীয় রিজার্ভ ব্যাঙ্ক");
        m.put("ui.shell.cms_full", "অভিযোগ ব্যবস্থাপনা ব্যবস্থা");
        m.put("ui.shell.logout", "সাইন আউট");
        m.put("ui.shell.collapse", "সংকুচিত করুন");
        m.put("ui.shell.primary_navigation", "প্রধান নেভিগেশন");
        m.put("ui.shell.dismiss_notification", "বিজ্ঞপ্তি সরান");
        m.put("ui.shell.change_language", "ভাষা পরিবর্তন করুন");
        m.put("ui.nav.crpc_complaints", "সিআরপিসি অভিযোগ");
        m.put("ui.nav.cepc_dashboard", "সিইপিসি অভিযোগ");
        m.put("ui.nav.rbio_workbench", "আরবিআইও কর্মক্ষেত্র");
        m.put("ui.nav.aa_appeals", "আপিল");
        m.put("ui.nav.re_portal", "নিয়ন্ত্রিত সংস্থা");
        m.put("ui.nav.reports", "প্রতিবেদন");
        m.put("ui.role.crpc_deo", "সিআরপিসি ডেটা এন্ট্রি অপারেটর");
        m.put("ui.role.crpc_reviewer", "সিআরপিসি পর্যালোচক");
        m.put("ui.role.crpc_incharge", "সিআরপিসি ইনচার্জ");
        m.put("ui.role.cepc_officer", "সিইপিসি আধিকারিক");
        m.put("ui.role.rbio_officer", "আরবিআইও আধিকারিক");
        m.put("ui.role.staff", "কর্মী");
        m.put("ui.page.crpc_complaints", "সিআরপিসি অভিযোগ");
        m.put("ui.page.cepc_dashboard", "সিইপিসি অভিযোগ ড্যাশবোর্ড");
        m.put("ui.page.rbio_home", "আরবিআইও অভিযোগ কর্মক্ষেত্র");
        m.put("ui.page.re_dashboard", "উত্তরের অপেক্ষায় থাকা অভিযোগ");
        m.put("ui.common.search", "অনুসন্ধান");
        m.put("ui.common.reset", "পুনঃসেট");
        m.put("ui.common.filters", "ফিল্টার");
        m.put("ui.common.advanced_search", "উন্নত অনুসন্ধান");
        m.put("ui.common.clear_filters", "ফিল্টার সরান");
        m.put("ui.common.refresh", "রিফ্রেশ");
        m.put("ui.common.export", "রপ্তানি");
        m.put("ui.common.columns", "কলাম");
        m.put("ui.common.save", "সংরক্ষণ");
        m.put("ui.common.cancel", "বাতিল");
        m.put("ui.common.close", "বন্ধ করুন");
        m.put("ui.common.submit", "জমা দিন");
        m.put("ui.common.confirm", "নিশ্চিত করুন");
        m.put("ui.common.back", "পিছনে");
        m.put("ui.common.next", "পরবর্তী");
        m.put("ui.common.previous", "পূর্ববর্তী");
        m.put("ui.common.retry", "পুনরায় চেষ্টা করুন");
        m.put("ui.common.view", "দেখুন");
        m.put("ui.common.actions", "কার্যক্রম");
        m.put("ui.common.loading", "লোড হচ্ছে…");
        m.put("ui.common.no_records", "প্রদর্শনের জন্য কোনও রেকর্ড নেই।");
        m.put("ui.common.load_failed", "ডেটা লোড করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।");
        m.put("ui.common.page_of", "পৃষ্ঠা {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "প্রতি পৃষ্ঠায় সারি");
        m.put("ui.common.showing_count", "{{total}}-এর মধ্যে {{shown}} দেখানো হচ্ছে");
        m.put("ui.common.all", "সমস্ত");
        m.put("ui.common.yes", "হ্যাঁ");
        m.put("ui.common.no", "না");
        m.put("ui.common.required_field", "এই ক্ষেত্রটি আবশ্যক।");
        m.put("crpc.status.assessment_complete", "মূল্যায়ন সম্পূর্ণ");
        m.put("crpc.queue.assigned_to_me", "আমাকে বরাদ্দ করা অভিযোগ");
        m.put("common.skip_to_content", "মূল বিষয়বস্তুতে যান");
        m.put("cepc.queue.assigned_to_me", "আমাকে বরাদ্দ করা");
        m.put("cepc.queue.all_complaints", "সমস্ত অভিযোগ");
        m.put("cepc.action.create_complaint", "অভিযোগ তৈরি করুন");
        m.put("ui.toast.saved", "আপনার পরিবর্তনগুলি সংরক্ষিত হয়েছে।");
        m.put("ui.toast.save_failed", "পরিবর্তনগুলি সংরক্ষণ করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।");
        m.put("ui.toast.action_failed", "এই কাজটি সম্পন্ন করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।");
        m.put("ui.toast.officers_synced", "নির্দেশিকা থেকে {{count}} জন নতুন আধিকারিক সমন্বিত হয়েছেন।");
        m.put("ui.toast.officers_already_synced", "নির্দেশিকার সমস্ত ব্যবহারকারী ইতিমধ্যেই পুলে রয়েছেন।");
        m.put("ui.toast.officer_sync_failed", "নির্দেশিকা থেকে ব্যবহারকারী আনা যায়নি।");
        m.put("ui.upload.max_file_size", "প্রতি ফাইলে সর্বাধিক {{size}} এমবি।");
        m.put("ui.upload.max_total_size", "প্রতি অভিযোগে সর্বমোট সর্বাধিক {{size}} এমবি।");
        m.put("ui.upload.error_file_too_large", "প্রতিটি ফাইল {{size}} এমবি বা তার ছোট হতে হবে।");
        m.put("ui.upload.error_total_too_large", "সমস্ত সংযুক্তি একসঙ্গে {{size}} এমবি বা কম হতে হবে।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "భారతీయ రిజర్వ్ బ్యాంక్");
        m.put("ui.shell.cms_full", "ఫిర్యాదు నిర్వహణ వ్యవస్థ");
        m.put("ui.shell.logout", "సైన్ అవుట్");
        m.put("ui.shell.collapse", "కుదించు");
        m.put("ui.shell.primary_navigation", "ప్రధాన నావిగేషన్");
        m.put("ui.shell.dismiss_notification", "నోటిఫికేషన్ తొలగించు");
        m.put("ui.shell.change_language", "భాష మార్చు");
        m.put("ui.nav.crpc_complaints", "సీఆర్‌పీసీ ఫిర్యాదులు");
        m.put("ui.nav.cepc_dashboard", "సీఈపీసీ ఫిర్యాదులు");
        m.put("ui.nav.rbio_workbench", "ఆర్‌బీఐఓ కార్యక్షేత్రం");
        m.put("ui.nav.aa_appeals", "అప్పీళ్లు");
        m.put("ui.nav.re_portal", "నియంత్రిత సంస్థ");
        m.put("ui.nav.reports", "నివేదికలు");
        m.put("ui.role.crpc_deo", "సీఆర్‌పీసీ డేటా ఎంట్రీ ఆపరేటర్");
        m.put("ui.role.crpc_reviewer", "సీఆర్‌పీసీ సమీక్షకుడు");
        m.put("ui.role.crpc_incharge", "సీఆర్‌పీసీ ఇన్‌చార్జ్");
        m.put("ui.role.cepc_officer", "సీఈపీసీ అధికారి");
        m.put("ui.role.rbio_officer", "ఆర్‌బీఐఓ అధికారి");
        m.put("ui.role.staff", "సిబ్బంది");
        m.put("ui.page.crpc_complaints", "సీఆర్‌పీసీ ఫిర్యాదులు");
        m.put("ui.page.cepc_dashboard", "సీఈపీసీ ఫిర్యాదు డాష్‌బోర్డ్");
        m.put("ui.page.rbio_home", "ఆర్‌బీఐఓ ఫిర్యాదు కార్యక్షేత్రం");
        m.put("ui.page.re_dashboard", "సమాధానం కోసం ఎదురుచూస్తున్న ఫిర్యాదులు");
        m.put("ui.common.search", "వెతకండి");
        m.put("ui.common.reset", "రీసెట్");
        m.put("ui.common.filters", "ఫిల్టర్లు");
        m.put("ui.common.advanced_search", "అధునాతన శోధన");
        m.put("ui.common.clear_filters", "ఫిల్టర్లు తొలగించు");
        m.put("ui.common.refresh", "రిఫ్రెష్");
        m.put("ui.common.export", "ఎగుమతి");
        m.put("ui.common.columns", "నిలువు వరుసలు");
        m.put("ui.common.save", "సేవ్ చేయండి");
        m.put("ui.common.cancel", "రద్దు చేయండి");
        m.put("ui.common.close", "మూసివేయండి");
        m.put("ui.common.submit", "సమర్పించండి");
        m.put("ui.common.confirm", "నిర్ధారించండి");
        m.put("ui.common.back", "వెనుక");
        m.put("ui.common.next", "తదుపరి");
        m.put("ui.common.previous", "మునుపటి");
        m.put("ui.common.retry", "మళ్లీ ప్రయత్నించండి");
        m.put("ui.common.view", "చూడండి");
        m.put("ui.common.actions", "చర్యలు");
        m.put("ui.common.loading", "లోడ్ అవుతోంది…");
        m.put("ui.common.no_records", "ప్రదర్శించడానికి రికార్డులు లేవు.");
        m.put("ui.common.load_failed", "డేటా లోడ్ కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("ui.common.page_of", "పేజీ {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "పేజీకి వరుసలు");
        m.put("ui.common.showing_count", "{{total}}లో {{shown}} చూపిస్తోంది");
        m.put("ui.common.all", "అన్నీ");
        m.put("ui.common.yes", "అవును");
        m.put("ui.common.no", "కాదు");
        m.put("ui.common.required_field", "ఈ ఫీల్డ్ అవసరం.");
        m.put("crpc.status.assessment_complete", "మదింపు పూర్తయింది");
        m.put("crpc.queue.assigned_to_me", "నాకు కేటాయించిన ఫిర్యాదులు");
        m.put("common.skip_to_content", "ప్రధాన కంటెంట్‌కు వెళ్లండి");
        m.put("cepc.queue.assigned_to_me", "నాకు కేటాయించినవి");
        m.put("cepc.queue.all_complaints", "అన్ని ఫిర్యాదులు");
        m.put("cepc.action.create_complaint", "ఫిర్యాదు సృష్టించండి");
        m.put("ui.toast.saved", "మీ మార్పులు సేవ్ చేయబడ్డాయి.");
        m.put("ui.toast.save_failed", "మార్పులు సేవ్ కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("ui.toast.action_failed", "ఆ చర్య పూర్తి కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("ui.toast.officers_synced", "డైరెక్టరీ నుండి {{count}} కొత్త అధికారులు సమకాలీకరించబడ్డారు.");
        m.put("ui.toast.officers_already_synced", "డైరెక్టరీలోని వినియోగదారులందరూ ఇప్పటికే పూల్‌లో ఉన్నారు.");
        m.put("ui.toast.officer_sync_failed", "డైరెక్టరీ నుండి వినియోగదారులను పొందలేకపోయాము.");
        m.put("ui.upload.max_file_size", "ప్రతి ఫైల్‌కు గరిష్ఠంగా {{size}} ఎంబీ.");
        m.put("ui.upload.max_total_size", "ప్రతి ఫిర్యాదుకు మొత్తం గరిష్ఠంగా {{size}} ఎంబీ.");
        m.put("ui.upload.error_file_too_large", "ప్రతి ఫైల్ {{size}} ఎంబీ లేదా అంతకంటే చిన్నదిగా ఉండాలి.");
        m.put("ui.upload.error_total_too_large", "అన్ని అనుబంధాలు కలిపి {{size}} ఎంబీ లేదా తక్కువగా ఉండాలి.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "இந்திய ரிசர்வ் வங்கி");
        m.put("ui.shell.cms_full", "புகார் மேலாண்மை அமைப்பு");
        m.put("ui.shell.logout", "வெளியேறு");
        m.put("ui.shell.collapse", "சுருக்கு");
        m.put("ui.shell.primary_navigation", "முதன்மை வழிசெலுத்தல்");
        m.put("ui.shell.dismiss_notification", "அறிவிப்பை நீக்கு");
        m.put("ui.shell.change_language", "மொழியை மாற்று");
        m.put("ui.nav.crpc_complaints", "சிஆர்பிசி புகார்கள்");
        m.put("ui.nav.cepc_dashboard", "சிஇபிசி புகார்கள்");
        m.put("ui.nav.rbio_workbench", "ஆர்பிஐஓ பணியிடம்");
        m.put("ui.nav.aa_appeals", "மேல்முறையீடுகள்");
        m.put("ui.nav.re_portal", "ஒழுங்குபடுத்தப்பட்ட நிறுவனம்");
        m.put("ui.nav.reports", "அறிக்கைகள்");
        m.put("ui.role.crpc_deo", "சிஆர்பிசி தரவு உள்ளீட்டு இயக்குபவர்");
        m.put("ui.role.crpc_reviewer", "சிஆர்பிசி மதிப்பாய்வாளர்");
        m.put("ui.role.crpc_incharge", "சிஆர்பிசி பொறுப்பாளர்");
        m.put("ui.role.cepc_officer", "சிஇபிசி அதிகாரி");
        m.put("ui.role.rbio_officer", "ஆர்பிஐஓ அதிகாரி");
        m.put("ui.role.staff", "பணியாளர்");
        m.put("ui.page.crpc_complaints", "சிஆர்பிசி புகார்கள்");
        m.put("ui.page.cepc_dashboard", "சிஇபிசி புகார் டாஷ்போர்டு");
        m.put("ui.page.rbio_home", "ஆர்பிஐஓ புகார் பணியிடம்");
        m.put("ui.page.re_dashboard", "பதிலுக்கு காத்திருக்கும் புகார்கள்");
        m.put("ui.common.search", "தேடு");
        m.put("ui.common.reset", "மீட்டமை");
        m.put("ui.common.filters", "வடிகட்டிகள்");
        m.put("ui.common.advanced_search", "மேம்பட்ட தேடல்");
        m.put("ui.common.clear_filters", "வடிகட்டிகளை அழி");
        m.put("ui.common.refresh", "புதுப்பி");
        m.put("ui.common.export", "ஏற்றுமதி");
        m.put("ui.common.columns", "நிரல்கள்");
        m.put("ui.common.save", "சேமி");
        m.put("ui.common.cancel", "ரத்து செய்");
        m.put("ui.common.close", "மூடு");
        m.put("ui.common.submit", "சமர்ப்பி");
        m.put("ui.common.confirm", "உறுதிப்படுத்து");
        m.put("ui.common.back", "பின்");
        m.put("ui.common.next", "அடுத்து");
        m.put("ui.common.previous", "முந்தையது");
        m.put("ui.common.retry", "மீண்டும் முயற்சி செய்");
        m.put("ui.common.view", "பார்");
        m.put("ui.common.actions", "செயல்கள்");
        m.put("ui.common.loading", "ஏற்றப்படுகிறது…");
        m.put("ui.common.no_records", "காட்ட எந்தப் பதிவும் இல்லை.");
        m.put("ui.common.load_failed", "தரவை ஏற்ற முடியவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("ui.common.page_of", "பக்கம் {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "ஒரு பக்கத்திற்கு வரிசைகள்");
        m.put("ui.common.showing_count", "{{total}}-இல் {{shown}} காட்டப்படுகிறது");
        m.put("ui.common.all", "அனைத்தும்");
        m.put("ui.common.yes", "ஆம்");
        m.put("ui.common.no", "இல்லை");
        m.put("ui.common.required_field", "இந்தப் புலம் அவசியம்.");
        m.put("crpc.status.assessment_complete", "மதிப்பீடு முடிந்தது");
        m.put("crpc.queue.assigned_to_me", "எனக்கு ஒதுக்கப்பட்ட புகார்கள்");
        m.put("common.skip_to_content", "முதன்மை உள்ளடக்கத்திற்குச் செல்");
        m.put("cepc.queue.assigned_to_me", "எனக்கு ஒதுக்கப்பட்டவை");
        m.put("cepc.queue.all_complaints", "அனைத்து புகார்கள்");
        m.put("cepc.action.create_complaint", "புகாரை உருவாக்கு");
        m.put("ui.toast.saved", "உங்கள் மாற்றங்கள் சேமிக்கப்பட்டன.");
        m.put("ui.toast.save_failed", "மாற்றங்களைச் சேமிக்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("ui.toast.action_failed", "அந்தச் செயலை நிறைவு செய்ய முடியவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("ui.toast.officers_synced", "அடைவிலிருந்து {{count}} புதிய அதிகாரிகள் ஒத்திசைக்கப்பட்டனர்.");
        m.put("ui.toast.officers_already_synced", "அடைவின் அனைத்துப் பயனர்களும் ஏற்கெனவே குழுவில் உள்ளனர்.");
        m.put("ui.toast.officer_sync_failed", "அடைவிலிருந்து பயனர்களைப் பெற முடியவில்லை.");
        m.put("ui.upload.max_file_size", "ஒரு கோப்பிற்கு அதிகபட்சம் {{size}} எம்பி.");
        m.put("ui.upload.max_total_size", "ஒரு புகாருக்கு மொத்தம் அதிகபட்சம் {{size}} எம்பி.");
        m.put("ui.upload.error_file_too_large", "ஒவ்வொரு கோப்பும் {{size}} எம்பி அல்லது சிறியதாக இருக்க வேண்டும்.");
        m.put("ui.upload.error_total_too_large", "அனைத்து இணைப்புகளும் சேர்ந்து {{size}} எம்பி அல்லது குறைவாக இருக்க வேண்டும்.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "ભારતીય રિઝર્વ બેંક");
        m.put("ui.shell.cms_full", "ફરિયાદ વ્યવસ્થાપન પ્રણાલી");
        m.put("ui.shell.logout", "સાઇન આઉટ");
        m.put("ui.shell.collapse", "સંકોચો");
        m.put("ui.shell.primary_navigation", "મુખ્ય નેવિગેશન");
        m.put("ui.shell.dismiss_notification", "સૂચના દૂર કરો");
        m.put("ui.shell.change_language", "ભાષા બદલો");
        m.put("ui.nav.crpc_complaints", "સીઆરપીસી ફરિયાદો");
        m.put("ui.nav.cepc_dashboard", "સીઈપીસી ફરિયાદો");
        m.put("ui.nav.rbio_workbench", "આરબીઆઈઓ કાર્યક્ષેત્ર");
        m.put("ui.nav.aa_appeals", "અપીલો");
        m.put("ui.nav.re_portal", "નિયંત્રિત સંસ્થા");
        m.put("ui.nav.reports", "અહેવાલો");
        m.put("ui.role.crpc_deo", "સીઆરપીસી ડેટા એન્ટ્રી ઓપરેટર");
        m.put("ui.role.crpc_reviewer", "સીઆરપીસી સમીક્ષક");
        m.put("ui.role.crpc_incharge", "સીઆરપીસી ઇન્ચાર્જ");
        m.put("ui.role.cepc_officer", "સીઈપીસી અધિકારી");
        m.put("ui.role.rbio_officer", "આરબીઆઈઓ અધિકારી");
        m.put("ui.role.staff", "કર્મચારી");
        m.put("ui.page.crpc_complaints", "સીઆરપીસી ફરિયાદો");
        m.put("ui.page.cepc_dashboard", "સીઈપીસી ફરિયાદ ડેશબોર્ડ");
        m.put("ui.page.rbio_home", "આરબીઆઈઓ ફરિયાદ કાર્યક્ષેત્ર");
        m.put("ui.page.re_dashboard", "જવાબની પ્રતીક્ષામાં ફરિયાદો");
        m.put("ui.common.search", "શોધો");
        m.put("ui.common.reset", "રીસેટ કરો");
        m.put("ui.common.filters", "ફિલ્ટર");
        m.put("ui.common.advanced_search", "અદ્યતન શોધ");
        m.put("ui.common.clear_filters", "ફિલ્ટર સાફ કરો");
        m.put("ui.common.refresh", "તાજું કરો");
        m.put("ui.common.export", "નિકાસ");
        m.put("ui.common.columns", "કૉલમ");
        m.put("ui.common.save", "સાચવો");
        m.put("ui.common.cancel", "રદ કરો");
        m.put("ui.common.close", "બંધ કરો");
        m.put("ui.common.submit", "સબમિટ કરો");
        m.put("ui.common.confirm", "પુષ્ટિ કરો");
        m.put("ui.common.back", "પાછળ");
        m.put("ui.common.next", "આગળ");
        m.put("ui.common.previous", "પહેલાનું");
        m.put("ui.common.retry", "ફરી પ્રયાસ કરો");
        m.put("ui.common.view", "જુઓ");
        m.put("ui.common.actions", "ક્રિયાઓ");
        m.put("ui.common.loading", "લોડ થઈ રહ્યું છે…");
        m.put("ui.common.no_records", "પ્રદર્શિત કરવા માટે કોઈ રેકોર્ડ નથી.");
        m.put("ui.common.load_failed", "ડેટા લોડ થઈ શક્યો નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("ui.common.page_of", "પૃષ્ઠ {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "પ્રતિ પૃષ્ઠ પંક્તિઓ");
        m.put("ui.common.showing_count", "{{total}} માંથી {{shown}} દર્શાવે છે");
        m.put("ui.common.all", "બધા");
        m.put("ui.common.yes", "હા");
        m.put("ui.common.no", "ના");
        m.put("ui.common.required_field", "આ ફીલ્ડ આવશ્યક છે.");
        m.put("crpc.status.assessment_complete", "મૂલ્યાંકન પૂર્ણ");
        m.put("crpc.queue.assigned_to_me", "મને સોંપાયેલ ફરિયાદો");
        m.put("common.skip_to_content", "મુખ્ય સામગ્રી પર જાઓ");
        m.put("cepc.queue.assigned_to_me", "મને સોંપાયેલ");
        m.put("cepc.queue.all_complaints", "બધી ફરિયાદો");
        m.put("cepc.action.create_complaint", "ફરિયાદ બનાવો");
        m.put("ui.toast.saved", "તમારા ફેરફારો સાચવવામાં આવ્યા છે.");
        m.put("ui.toast.save_failed", "ફેરફારો સાચવી શકાયા નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("ui.toast.action_failed", "તે ક્રિયા પૂર્ણ થઈ શકી નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("ui.toast.officers_synced", "નિર્દેશિકામાંથી {{count}} નવા અધિકારીઓ સમન્વયિત થયા.");
        m.put("ui.toast.officers_already_synced", "નિર્દેશિકાના તમામ વપરાશકર્તાઓ પહેલેથી પૂલમાં છે.");
        m.put("ui.toast.officer_sync_failed", "નિર્દેશિકામાંથી વપરાશકર્તાઓ મેળવી શકાયા નથી.");
        m.put("ui.upload.max_file_size", "પ્રતિ ફાઇલ મહત્તમ {{size}} એમબી.");
        m.put("ui.upload.max_total_size", "પ્રતિ ફરિયાદ કુલ મહત્તમ {{size}} એમબી.");
        m.put("ui.upload.error_file_too_large", "પ્રત્યેક ફાઇલ {{size}} એમબી અથવા તેથી નાની હોવી જોઈએ.");
        m.put("ui.upload.error_total_too_large", "તમામ જોડાણો મળીને {{size}} એમબી અથવા ઓછા હોવા જોઈએ.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "ریزرو بینک آف انڈیا");
        m.put("ui.shell.cms_full", "شکایات کے انتظام کا نظام");
        m.put("ui.shell.logout", "سائن آؤٹ");
        m.put("ui.shell.collapse", "سمیٹیں");
        m.put("ui.shell.primary_navigation", "بنیادی نیویگیشن");
        m.put("ui.shell.dismiss_notification", "اطلاع ہٹائیں");
        m.put("ui.shell.change_language", "زبان تبدیل کریں");
        m.put("ui.nav.crpc_complaints", "سی آر پی سی شکایات");
        m.put("ui.nav.cepc_dashboard", "سی ای پی سی شکایات");
        m.put("ui.nav.rbio_workbench", "آر بی آئی او ورک بینچ");
        m.put("ui.nav.aa_appeals", "اپیلیں");
        m.put("ui.nav.re_portal", "ریگولیٹڈ ادارہ");
        m.put("ui.nav.reports", "رپورٹس");
        m.put("ui.role.crpc_deo", "سی آر پی سی ڈیٹا انٹری آپریٹر");
        m.put("ui.role.crpc_reviewer", "سی آر پی سی جائزہ کار");
        m.put("ui.role.crpc_incharge", "سی آر پی سی انچارج");
        m.put("ui.role.cepc_officer", "سی ای پی سی افسر");
        m.put("ui.role.rbio_officer", "آر بی آئی او افسر");
        m.put("ui.role.staff", "عملہ");
        m.put("ui.page.crpc_complaints", "سی آر پی سی شکایات");
        m.put("ui.page.cepc_dashboard", "سی ای پی سی شکایات ڈیش بورڈ");
        m.put("ui.page.rbio_home", "آر بی آئی او شکایات ورک بینچ");
        m.put("ui.page.re_dashboard", "جواب کی منتظر شکایات");
        m.put("ui.common.search", "تلاش کریں");
        m.put("ui.common.reset", "دوبارہ ترتیب دیں");
        m.put("ui.common.filters", "فلٹرز");
        m.put("ui.common.advanced_search", "جدید تلاش");
        m.put("ui.common.clear_filters", "فلٹرز صاف کریں");
        m.put("ui.common.refresh", "تازہ کریں");
        m.put("ui.common.export", "برآمد");
        m.put("ui.common.columns", "کالم");
        m.put("ui.common.save", "محفوظ کریں");
        m.put("ui.common.cancel", "منسوخ کریں");
        m.put("ui.common.close", "بند کریں");
        m.put("ui.common.submit", "جمع کرائیں");
        m.put("ui.common.confirm", "تصدیق کریں");
        m.put("ui.common.back", "واپس");
        m.put("ui.common.next", "اگلا");
        m.put("ui.common.previous", "پچھلا");
        m.put("ui.common.retry", "دوبارہ کوشش کریں");
        m.put("ui.common.view", "دیکھیں");
        m.put("ui.common.actions", "اعمال");
        m.put("ui.common.loading", "لوڈ ہو رہا ہے…");
        m.put("ui.common.no_records", "دکھانے کے لیے کوئی ریکارڈ نہیں۔");
        m.put("ui.common.load_failed", "ڈیٹا لوڈ نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("ui.common.page_of", "صفحہ {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "فی صفحہ قطاریں");
        m.put("ui.common.showing_count", "{{total}} میں سے {{shown}} دکھائے جا رہے ہیں");
        m.put("ui.common.all", "تمام");
        m.put("ui.common.yes", "ہاں");
        m.put("ui.common.no", "نہیں");
        m.put("ui.common.required_field", "یہ خانہ لازمی ہے۔");
        m.put("crpc.status.assessment_complete", "تشخیص مکمل");
        m.put("crpc.queue.assigned_to_me", "مجھے تفویض کردہ شکایات");
        m.put("common.skip_to_content", "مرکزی مواد پر جائیں");
        m.put("cepc.queue.assigned_to_me", "مجھے تفویض کردہ");
        m.put("cepc.queue.all_complaints", "تمام شکایات");
        m.put("cepc.action.create_complaint", "شکایت بنائیں");
        m.put("ui.toast.saved", "آپ کی تبدیلیاں محفوظ کر لی گئی ہیں۔");
        m.put("ui.toast.save_failed", "تبدیلیاں محفوظ نہیں ہو سکیں۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("ui.toast.action_failed", "یہ عمل مکمل نہیں ہو سکا۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("ui.toast.officers_synced", "ڈائریکٹری سے {{count}} نئے افسران ہم وقت ہوئے۔");
        m.put("ui.toast.officers_already_synced", "ڈائریکٹری کے تمام صارفین پہلے ہی پول میں ہیں۔");
        m.put("ui.toast.officer_sync_failed", "ڈائریکٹری سے صارفین حاصل نہیں کیے جا سکے۔");
        m.put("ui.upload.max_file_size", "فی فائل زیادہ سے زیادہ {{size}} ایم بی۔");
        m.put("ui.upload.max_total_size", "فی شکایت کل زیادہ سے زیادہ {{size}} ایم بی۔");
        m.put("ui.upload.error_file_too_large", "ہر فائل {{size}} ایم بی یا اس سے چھوٹی ہونی چاہیے۔");
        m.put("ui.upload.error_total_too_large", "تمام منسلکات مل کر {{size}} ایم بی یا کم ہونے چاہییں۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "ಭಾರತೀಯ ರಿಸರ್ವ್ ಬ್ಯಾಂಕ್");
        m.put("ui.shell.cms_full", "ದೂರು ನಿರ್ವಹಣಾ ವ್ಯವಸ್ಥೆ");
        m.put("ui.shell.logout", "ಸೈನ್ ಔಟ್");
        m.put("ui.shell.collapse", "ಸಂಕುಚಿಸು");
        m.put("ui.shell.primary_navigation", "ಮುಖ್ಯ ನ್ಯಾವಿಗೇಷನ್");
        m.put("ui.shell.dismiss_notification", "ಅಧಿಸೂಚನೆ ತೆಗೆದುಹಾಕಿ");
        m.put("ui.shell.change_language", "ಭಾಷೆ ಬದಲಿಸಿ");
        m.put("ui.nav.crpc_complaints", "ಸಿಆರ್‌ಪಿಸಿ ದೂರುಗಳು");
        m.put("ui.nav.cepc_dashboard", "ಸಿಇಪಿಸಿ ದೂರುಗಳು");
        m.put("ui.nav.rbio_workbench", "ಆರ್‌ಬಿಐಒ ಕಾರ್ಯಕ್ಷೇತ್ರ");
        m.put("ui.nav.aa_appeals", "ಮೇಲ್ಮನವಿಗಳು");
        m.put("ui.nav.re_portal", "ನಿಯಂತ್ರಿತ ಸಂಸ್ಥೆ");
        m.put("ui.nav.reports", "ವರದಿಗಳು");
        m.put("ui.role.crpc_deo", "ಸಿಆರ್‌ಪಿಸಿ ಡೇಟಾ ಎಂಟ್ರಿ ಆಪರೇಟರ್");
        m.put("ui.role.crpc_reviewer", "ಸಿಆರ್‌ಪಿಸಿ ಪರಿಶೀಲಕ");
        m.put("ui.role.crpc_incharge", "ಸಿಆರ್‌ಪಿಸಿ ಉಸ್ತುವಾರಿ");
        m.put("ui.role.cepc_officer", "ಸಿಇಪಿಸಿ ಅಧಿಕಾರಿ");
        m.put("ui.role.rbio_officer", "ಆರ್‌ಬಿಐಒ ಅಧಿಕಾರಿ");
        m.put("ui.role.staff", "ಸಿಬ್ಬಂದಿ");
        m.put("ui.page.crpc_complaints", "ಸಿಆರ್‌ಪಿಸಿ ದೂರುಗಳು");
        m.put("ui.page.cepc_dashboard", "ಸಿಇಪಿಸಿ ದೂರು ಡ್ಯಾಶ್‌ಬೋರ್ಡ್");
        m.put("ui.page.rbio_home", "ಆರ್‌ಬಿಐಒ ದೂರು ಕಾರ್ಯಕ್ಷೇತ್ರ");
        m.put("ui.page.re_dashboard", "ಪ್ರತಿಕ್ರಿಯೆಗೆ ಕಾಯುತ್ತಿರುವ ದೂರುಗಳು");
        m.put("ui.common.search", "ಹುಡುಕಿ");
        m.put("ui.common.reset", "ಮರುಹೊಂದಿಸಿ");
        m.put("ui.common.filters", "ಫಿಲ್ಟರ್‌ಗಳು");
        m.put("ui.common.advanced_search", "ಸುಧಾರಿತ ಹುಡುಕಾಟ");
        m.put("ui.common.clear_filters", "ಫಿಲ್ಟರ್‌ಗಳನ್ನು ತೆರವುಗೊಳಿಸಿ");
        m.put("ui.common.refresh", "ರಿಫ್ರೆಶ್");
        m.put("ui.common.export", "ರಫ್ತು");
        m.put("ui.common.columns", "ಕಾಲಮ್‌ಗಳು");
        m.put("ui.common.save", "ಉಳಿಸಿ");
        m.put("ui.common.cancel", "ರದ್ದುಮಾಡಿ");
        m.put("ui.common.close", "ಮುಚ್ಚಿ");
        m.put("ui.common.submit", "ಸಲ್ಲಿಸಿ");
        m.put("ui.common.confirm", "ದೃಢೀಕರಿಸಿ");
        m.put("ui.common.back", "ಹಿಂದೆ");
        m.put("ui.common.next", "ಮುಂದೆ");
        m.put("ui.common.previous", "ಹಿಂದಿನದು");
        m.put("ui.common.retry", "ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ");
        m.put("ui.common.view", "ವೀಕ್ಷಿಸಿ");
        m.put("ui.common.actions", "ಕ್ರಮಗಳು");
        m.put("ui.common.loading", "ಲೋಡ್ ಆಗುತ್ತಿದೆ…");
        m.put("ui.common.no_records", "ಪ್ರದರ್ಶಿಸಲು ಯಾವುದೇ ದಾಖಲೆಗಳಿಲ್ಲ.");
        m.put("ui.common.load_failed", "ಡೇಟಾ ಲೋಡ್ ಆಗಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("ui.common.page_of", "ಪುಟ {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "ಪ್ರತಿ ಪುಟಕ್ಕೆ ಸಾಲುಗಳು");
        m.put("ui.common.showing_count", "{{total}} ರಲ್ಲಿ {{shown}} ತೋರಿಸಲಾಗಿದೆ");
        m.put("ui.common.all", "ಎಲ್ಲಾ");
        m.put("ui.common.yes", "ಹೌದು");
        m.put("ui.common.no", "ಇಲ್ಲ");
        m.put("ui.common.required_field", "ಈ ಕ್ಷೇತ್ರ ಅಗತ್ಯವಿದೆ.");
        m.put("crpc.status.assessment_complete", "ಮೌಲ್ಯಮಾಪನ ಪೂರ್ಣ");
        m.put("crpc.queue.assigned_to_me", "ನನಗೆ ನಿಯೋಜಿಸಿದ ದೂರುಗಳು");
        m.put("common.skip_to_content", "ಮುಖ್ಯ ವಿಷಯಕ್ಕೆ ಹೋಗಿ");
        m.put("cepc.queue.assigned_to_me", "ನನಗೆ ನಿಯೋಜಿಸಲಾಗಿದೆ");
        m.put("cepc.queue.all_complaints", "ಎಲ್ಲಾ ದೂರುಗಳು");
        m.put("cepc.action.create_complaint", "ದೂರು ರಚಿಸಿ");
        m.put("ui.toast.saved", "ನಿಮ್ಮ ಬದಲಾವಣೆಗಳನ್ನು ಉಳಿಸಲಾಗಿದೆ.");
        m.put("ui.toast.save_failed", "ಬದಲಾವಣೆಗಳನ್ನು ಉಳಿಸಲಾಗಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("ui.toast.action_failed", "ಆ ಕ್ರಮ ಪೂರ್ಣಗೊಳ್ಳಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("ui.toast.officers_synced", "ಡೈರೆಕ್ಟರಿಯಿಂದ {{count}} ಹೊಸ ಅಧಿಕಾರಿಗಳನ್ನು ಸಿಂಕ್ ಮಾಡಲಾಗಿದೆ.");
        m.put("ui.toast.officers_already_synced", "ಡೈರೆಕ್ಟರಿಯ ಎಲ್ಲಾ ಬಳಕೆದಾರರು ಈಗಾಗಲೇ ಪೂಲ್‌ನಲ್ಲಿದ್ದಾರೆ.");
        m.put("ui.toast.officer_sync_failed", "ಡೈರೆಕ್ಟರಿಯಿಂದ ಬಳಕೆದಾರರನ್ನು ಪಡೆಯಲಾಗಲಿಲ್ಲ.");
        m.put("ui.upload.max_file_size", "ಪ್ರತಿ ಫೈಲ್‌ಗೆ ಗರಿಷ್ಠ {{size}} ಎಂಬಿ.");
        m.put("ui.upload.max_total_size", "ಪ್ರತಿ ದೂರಿಗೆ ಒಟ್ಟು ಗರಿಷ್ಠ {{size}} ಎಂಬಿ.");
        m.put("ui.upload.error_file_too_large", "ಪ್ರತಿ ಫೈಲ್ {{size}} ಎಂಬಿ ಅಥವಾ ಚಿಕ್ಕದಾಗಿರಬೇಕು.");
        m.put("ui.upload.error_total_too_large", "ಎಲ್ಲಾ ಲಗತ್ತುಗಳು ಒಟ್ಟಾಗಿ {{size}} ಎಂಬಿ ಅಥವಾ ಕಡಿಮೆ ಇರಬೇಕು.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "റിസർവ് ബാങ്ക് ഓഫ് ഇന്ത്യ");
        m.put("ui.shell.cms_full", "പരാതി പരിപാലന സംവിധാനം");
        m.put("ui.shell.logout", "സൈൻ ഔട്ട്");
        m.put("ui.shell.collapse", "ചുരുക്കുക");
        m.put("ui.shell.primary_navigation", "പ്രാഥമിക നാവിഗേഷൻ");
        m.put("ui.shell.dismiss_notification", "അറിയിപ്പ് നീക്കുക");
        m.put("ui.shell.change_language", "ഭാഷ മാറ്റുക");
        m.put("ui.nav.crpc_complaints", "സിആർപിസി പരാതികൾ");
        m.put("ui.nav.cepc_dashboard", "സിഇപിസി പരാതികൾ");
        m.put("ui.nav.rbio_workbench", "ആർബിഐഒ പണിയിടം");
        m.put("ui.nav.aa_appeals", "അപ്പീലുകൾ");
        m.put("ui.nav.re_portal", "നിയന്ത്രിത സ്ഥാപനം");
        m.put("ui.nav.reports", "റിപ്പോർട്ടുകൾ");
        m.put("ui.role.crpc_deo", "സിആർപിസി ഡാറ്റാ എൻട്രി ഓപ്പറേറ്റർ");
        m.put("ui.role.crpc_reviewer", "സിആർപിസി പുനരവലോകകൻ");
        m.put("ui.role.crpc_incharge", "സിആർപിസി ചുമതലക്കാരൻ");
        m.put("ui.role.cepc_officer", "സിഇപിസി ഉദ്യോഗസ്ഥൻ");
        m.put("ui.role.rbio_officer", "ആർബിഐഒ ഉദ്യോഗസ്ഥൻ");
        m.put("ui.role.staff", "ജീവനക്കാരൻ");
        m.put("ui.page.crpc_complaints", "സിആർപിസി പരാതികൾ");
        m.put("ui.page.cepc_dashboard", "സിഇപിസി പരാതി ഡാഷ്‌ബോർഡ്");
        m.put("ui.page.rbio_home", "ആർബിഐഒ പരാതി പണിയിടം");
        m.put("ui.page.re_dashboard", "മറുപടിക്കായി കാത്തിരിക്കുന്ന പരാതികൾ");
        m.put("ui.common.search", "തിരയുക");
        m.put("ui.common.reset", "പുനഃക്രമീകരിക്കുക");
        m.put("ui.common.filters", "ഫിൽട്ടറുകൾ");
        m.put("ui.common.advanced_search", "വിപുലമായ തിരയൽ");
        m.put("ui.common.clear_filters", "ഫിൽട്ടറുകൾ മായ്ക്കുക");
        m.put("ui.common.refresh", "പുതുക്കുക");
        m.put("ui.common.export", "കയറ്റുമതി");
        m.put("ui.common.columns", "കോളങ്ങൾ");
        m.put("ui.common.save", "സംരക്ഷിക്കുക");
        m.put("ui.common.cancel", "റദ്ദാക്കുക");
        m.put("ui.common.close", "അടയ്ക്കുക");
        m.put("ui.common.submit", "സമർപ്പിക്കുക");
        m.put("ui.common.confirm", "സ്ഥിരീകരിക്കുക");
        m.put("ui.common.back", "പിന്നോട്ട്");
        m.put("ui.common.next", "അടുത്തത്");
        m.put("ui.common.previous", "മുൻപത്തേത്");
        m.put("ui.common.retry", "വീണ്ടും ശ്രമിക്കുക");
        m.put("ui.common.view", "കാണുക");
        m.put("ui.common.actions", "പ്രവർത്തനങ്ങൾ");
        m.put("ui.common.loading", "ലോഡ് ചെയ്യുന്നു…");
        m.put("ui.common.no_records", "പ്രദർശിപ്പിക്കാൻ രേഖകളില്ല.");
        m.put("ui.common.load_failed", "ഡാറ്റ ലോഡ് ചെയ്യാനായില്ല. ദയവായി വീണ്ടും ശ്രമിക്കുക.");
        m.put("ui.common.page_of", "പേജ് {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "ഓരോ പേജിലും വരികൾ");
        m.put("ui.common.showing_count", "{{total}} ൽ {{shown}} കാണിക്കുന്നു");
        m.put("ui.common.all", "എല്ലാം");
        m.put("ui.common.yes", "അതെ");
        m.put("ui.common.no", "അല്ല");
        m.put("ui.common.required_field", "ഈ ഫീൽഡ് നിർബന്ധമാണ്.");
        m.put("crpc.status.assessment_complete", "വിലയിരുത്തൽ പൂർത്തിയായി");
        m.put("crpc.queue.assigned_to_me", "എനിക്ക് നിയോഗിച്ച പരാതികൾ");
        m.put("common.skip_to_content", "പ്രധാന ഉള്ളടക്കത്തിലേക്ക് പോകുക");
        m.put("cepc.queue.assigned_to_me", "എനിക്ക് നിയോഗിച്ചത്");
        m.put("cepc.queue.all_complaints", "എല്ലാ പരാതികളും");
        m.put("cepc.action.create_complaint", "പരാതി സൃഷ്ടിക്കുക");
        m.put("ui.toast.saved", "നിങ്ങളുടെ മാറ്റങ്ങൾ സംരക്ഷിച്ചു.");
        m.put("ui.toast.save_failed", "മാറ്റങ്ങൾ സംരക്ഷിക്കാനായില്ല. ദയവായി വീണ്ടും ശ്രമിക്കുക.");
        m.put("ui.toast.action_failed", "ആ പ്രവർത്തനം പൂർത്തിയാക്കാനായില്ല. ദയവായി വീണ്ടും ശ്രമിക്കുക.");
        m.put("ui.toast.officers_synced", "ഡയറക്ടറിയിൽ നിന്ന് {{count}} പുതിയ ഉദ്യോഗസ്ഥരെ സമന്വയിപ്പിച്ചു.");
        m.put("ui.toast.officers_already_synced", "ഡയറക്ടറിയിലെ എല്ലാ ഉപയോക്താക്കളും ഇതിനകം പൂളിലുണ്ട്.");
        m.put("ui.toast.officer_sync_failed", "ഡയറക്ടറിയിൽ നിന്ന് ഉപയോക്താക്കളെ ലഭ്യമാക്കാനായില്ല.");
        m.put("ui.upload.max_file_size", "ഓരോ ഫയലിനും പരമാവധി {{size}} എംബി.");
        m.put("ui.upload.max_total_size", "ഓരോ പരാതിക്കും ആകെ പരമാവധി {{size}} എംബി.");
        m.put("ui.upload.error_file_too_large", "ഓരോ ഫയലും {{size}} എംബി അല്ലെങ്കിൽ ചെറുതായിരിക്കണം.");
        m.put("ui.upload.error_total_too_large", "എല്ലാ അറ്റാച്ചുമെന്റുകളും ചേർന്ന് {{size}} എംബി അല്ലെങ്കിൽ കുറവായിരിക്കണം.");
        return m;
    }

    /**
     * Punjabi is seeded here with real Gurmukhi. Worth stating why: every one of the 1806 pre-existing
     * keys carries Punjabi text byte-identical to English, i.e. the locale exists but is entirely
     * untranslated. These keys are the first that genuinely differ, so a test asserting "pa != en"
     * passes for this key set and still fails for the legacy ones -- which is the correct signal.
     */
    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.shell.rbi_english", "ਭਾਰਤੀ ਰਿਜ਼ਰਵ ਬੈਂਕ");
        m.put("ui.shell.cms_full", "ਸ਼ਿਕਾਇਤ ਪ੍ਰਬੰਧਨ ਪ੍ਰਣਾਲੀ");
        m.put("ui.shell.logout", "ਸਾਈਨ ਆਊਟ");
        m.put("ui.shell.collapse", "ਸੰਕੁਚਿਤ ਕਰੋ");
        m.put("ui.shell.primary_navigation", "ਮੁੱਖ ਨੈਵੀਗੇਸ਼ਨ");
        m.put("ui.shell.dismiss_notification", "ਸੂਚਨਾ ਹਟਾਓ");
        m.put("ui.shell.change_language", "ਭਾਸ਼ਾ ਬਦਲੋ");
        m.put("ui.nav.crpc_complaints", "ਸੀਆਰਪੀਸੀ ਸ਼ਿਕਾਇਤਾਂ");
        m.put("ui.nav.cepc_dashboard", "ਸੀਈਪੀਸੀ ਸ਼ਿਕਾਇਤਾਂ");
        m.put("ui.nav.rbio_workbench", "ਆਰਬੀਆਈਓ ਕਾਰਜ-ਖੇਤਰ");
        m.put("ui.nav.aa_appeals", "ਅਪੀਲਾਂ");
        m.put("ui.nav.re_portal", "ਨਿਯੰਤ੍ਰਿਤ ਸੰਸਥਾ");
        m.put("ui.nav.reports", "ਰਿਪੋਰਟਾਂ");
        m.put("ui.role.crpc_deo", "ਸੀਆਰਪੀਸੀ ਡਾਟਾ ਐਂਟਰੀ ਆਪਰੇਟਰ");
        m.put("ui.role.crpc_reviewer", "ਸੀਆਰਪੀਸੀ ਸਮੀਖਿਅਕ");
        m.put("ui.role.crpc_incharge", "ਸੀਆਰਪੀਸੀ ਇੰਚਾਰਜ");
        m.put("ui.role.cepc_officer", "ਸੀਈਪੀਸੀ ਅਧਿਕਾਰੀ");
        m.put("ui.role.rbio_officer", "ਆਰਬੀਆਈਓ ਅਧਿਕਾਰੀ");
        m.put("ui.role.staff", "ਸਟਾਫ");
        m.put("ui.page.crpc_complaints", "ਸੀਆਰਪੀਸੀ ਸ਼ਿਕਾਇਤਾਂ");
        m.put("ui.page.cepc_dashboard", "ਸੀਈਪੀਸੀ ਸ਼ਿਕਾਇਤ ਡੈਸ਼ਬੋਰਡ");
        m.put("ui.page.rbio_home", "ਆਰਬੀਆਈਓ ਸ਼ਿਕਾਇਤ ਕਾਰਜ-ਖੇਤਰ");
        m.put("ui.page.re_dashboard", "ਜਵਾਬ ਦੀ ਉਡੀਕ ਵਿੱਚ ਸ਼ਿਕਾਇਤਾਂ");
        m.put("ui.common.search", "ਖੋਜੋ");
        m.put("ui.common.reset", "ਮੁੜ ਸੈੱਟ ਕਰੋ");
        m.put("ui.common.filters", "ਫਿਲਟਰ");
        m.put("ui.common.advanced_search", "ਉੱਨਤ ਖੋਜ");
        m.put("ui.common.clear_filters", "ਫਿਲਟਰ ਸਾਫ਼ ਕਰੋ");
        m.put("ui.common.refresh", "ਤਾਜ਼ਾ ਕਰੋ");
        m.put("ui.common.export", "ਨਿਰਯਾਤ");
        m.put("ui.common.columns", "ਕਾਲਮ");
        m.put("ui.common.save", "ਸੰਭਾਲੋ");
        m.put("ui.common.cancel", "ਰੱਦ ਕਰੋ");
        m.put("ui.common.close", "ਬੰਦ ਕਰੋ");
        m.put("ui.common.submit", "ਜਮ੍ਹਾਂ ਕਰੋ");
        m.put("ui.common.confirm", "ਪੁਸ਼ਟੀ ਕਰੋ");
        m.put("ui.common.back", "ਪਿੱਛੇ");
        m.put("ui.common.next", "ਅੱਗੇ");
        m.put("ui.common.previous", "ਪਿਛਲਾ");
        m.put("ui.common.retry", "ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ");
        m.put("ui.common.view", "ਵੇਖੋ");
        m.put("ui.common.actions", "ਕਾਰਵਾਈਆਂ");
        m.put("ui.common.loading", "ਲੋਡ ਹੋ ਰਿਹਾ ਹੈ…");
        m.put("ui.common.no_records", "ਦਿਖਾਉਣ ਲਈ ਕੋਈ ਰਿਕਾਰਡ ਨਹੀਂ ਹੈ।");
        m.put("ui.common.load_failed", "ਡਾਟਾ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ। ਕਿਰਪਾ ਕਰਕੇ ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ।");
        m.put("ui.common.page_of", "ਪੰਨਾ {{current}} / {{total}}");
        m.put("ui.common.rows_per_page", "ਪ੍ਰਤੀ ਪੰਨਾ ਕਤਾਰਾਂ");
        m.put("ui.common.showing_count", "{{total}} ਵਿੱਚੋਂ {{shown}} ਦਿਖਾਏ ਜਾ ਰਹੇ ਹਨ");
        m.put("ui.common.all", "ਸਾਰੇ");
        m.put("ui.common.yes", "ਹਾਂ");
        m.put("ui.common.no", "ਨਹੀਂ");
        m.put("ui.common.required_field", "ਇਹ ਖੇਤਰ ਲਾਜ਼ਮੀ ਹੈ।");
        m.put("crpc.status.assessment_complete", "ਮੁਲਾਂਕਣ ਪੂਰਾ");
        m.put("crpc.queue.assigned_to_me", "ਮੈਨੂੰ ਸੌਂਪੀਆਂ ਗਈਆਂ ਸ਼ਿਕਾਇਤਾਂ");
        m.put("common.skip_to_content", "ਮੁੱਖ ਸਮੱਗਰੀ ਤੇ ਜਾਓ");
        m.put("cepc.queue.assigned_to_me", "ਮੈਨੂੰ ਸੌਂਪੀਆਂ ਗਈਆਂ");
        m.put("cepc.queue.all_complaints", "ਸਾਰੀਆਂ ਸ਼ਿਕਾਇਤਾਂ");
        m.put("cepc.action.create_complaint", "ਸ਼ਿਕਾਇਤ ਬਣਾਓ");
        m.put("ui.toast.saved", "ਤੁਹਾਡੀਆਂ ਤਬਦੀਲੀਆਂ ਸੰਭਾਲ ਲਈਆਂ ਗਈਆਂ ਹਨ।");
        m.put("ui.toast.save_failed", "ਤਬਦੀਲੀਆਂ ਸੰਭਾਲੀਆਂ ਨਹੀਂ ਜਾ ਸਕੀਆਂ। ਕਿਰਪਾ ਕਰਕੇ ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ।");
        m.put("ui.toast.action_failed", "ਉਹ ਕਾਰਵਾਈ ਪੂਰੀ ਨਹੀਂ ਹੋ ਸਕੀ। ਕਿਰਪਾ ਕਰਕੇ ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ।");
        m.put("ui.toast.officers_synced", "ਡਾਇਰੈਕਟਰੀ ਤੋਂ {{count}} ਨਵੇਂ ਅਧਿਕਾਰੀ ਸਮਕਾਲੀ ਕੀਤੇ ਗਏ।");
        m.put("ui.toast.officers_already_synced", "ਡਾਇਰੈਕਟਰੀ ਦੇ ਸਾਰੇ ਵਰਤੋਂਕਾਰ ਪਹਿਲਾਂ ਹੀ ਪੂਲ ਵਿੱਚ ਹਨ।");
        m.put("ui.toast.officer_sync_failed", "ਡਾਇਰੈਕਟਰੀ ਤੋਂ ਵਰਤੋਂਕਾਰ ਪ੍ਰਾਪਤ ਨਹੀਂ ਕੀਤੇ ਜਾ ਸਕੇ।");
        m.put("ui.upload.max_file_size", "ਪ੍ਰਤੀ ਫਾਈਲ ਵੱਧ ਤੋਂ ਵੱਧ {{size}} ਐਮਬੀ।");
        m.put("ui.upload.max_total_size", "ਪ੍ਰਤੀ ਸ਼ਿਕਾਇਤ ਕੁੱਲ ਵੱਧ ਤੋਂ ਵੱਧ {{size}} ਐਮਬੀ।");
        m.put("ui.upload.error_file_too_large", "ਹਰੇਕ ਫਾਈਲ {{size}} ਐਮਬੀ ਜਾਂ ਛੋਟੀ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ।");
        m.put("ui.upload.error_total_too_large", "ਸਾਰੇ ਨੱਥੀ ਮਿਲਾ ਕੇ {{size}} ਐਮਬੀ ਜਾਂ ਘੱਟ ਹੋਣੇ ਚਾਹੀਦੇ ਹਨ।");
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
