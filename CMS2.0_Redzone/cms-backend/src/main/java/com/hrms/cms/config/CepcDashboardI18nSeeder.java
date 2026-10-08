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
 * The CEPC dashboard's own vocabulary: status chips, the status dropdown, the tab strip, the KPI tiles, the
 * filter drawer and the grid column headers the shared {@code ui.col.*} set does not cover.
 *
 * <p>Only {@code en} and {@code hi} are seeded. The header's switcher
 * ({@code LanguageSelectComponent}) offers those two alone, so the other nine locales are unreachable from
 * this screen; {@code TranslationService.getTranslationsForLocale} fills any absent locale from each key's
 * {@code defaultValue}, so they read English rather than breaking.
 *
 * <p>A NEW seeder at {@code @Order(72)} rather than an edit to {@code CepcDashboardTranslationSeeder} (70) or
 * {@code AssistanceRailTranslationSeeder} (71), per the convention here: a shared file is a guaranteed
 * conflict where a conflict silently costs a locale. 60-71 were verified taken before choosing 72.
 *
 * <p>Seeding is insert-if-absent by key code, so a duplicate code anywhere would silently keep the FIRST
 * text. {@code ui.status.*}, {@code ui.filter.*} and {@code ui.cepc.*} were each verified unused.
 *
 * <p><b>The two key sets that are a contract, not a naming choice.</b> {@code ui.status.*} codes are produced
 * at runtime by {@code CepcStatus.labelKey()} from the stored status, and {@code ui.filter.*} by
 * {@code DepartmentStatusController.labelKey()} from the {@code CEPC_DASHBOARD_FILTER} code. A key missing
 * here therefore does not fall back to English — it renders the raw {@code ui.status.foo} token on screen.
 * Both sources are closed sets of Java constants; adding a status or a filter code means adding its key here.
 */
@Component
@Order(72)
public class CepcDashboardI18nSeeder implements CommandLineRunner {

    private static final String MODULE = "cepc";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public CepcDashboardI18nSeeder(TranslationKeyRepository keyRepo,
                                   TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        english().forEach(this::seed);
        seedLocale("hi", hindi());
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();

        // ═══ Column headers the shared ui.col.* set does not carry ═══
        m.put("ui.col.assigned_to", "Assigned To");
        m.put("ui.col.contact_person", "Contact Person");

        // ═══ Columns where CEPC's English differs from the shared ui.col.* wording ═══
        // These six exist ONLY to keep the English column headers byte-identical to what CEPC has always
        // shown. The shared keys say "Complainant", "Entity", "Category", "Creation Date", "Last Updated"
        // and "SLA (hrs)"; pointing CEPC at those renamed six live headers in English, which is a visible
        // regression for a change whose whole purpose is the Hindi column. Do not merge these back into
        // ui.col.* — the shared keys are also read by the RBIO, RE and CRPC grids, so editing their English
        // to match CEPC would just move the same regression onto those screens.
        m.put("ui.cepc.col.complainant_name", "Complainant Name");
        m.put("ui.cepc.col.entity_name", "Entity Name");
        m.put("ui.cepc.col.complaint_category", "Complaint Category");
        m.put("ui.cepc.col.created_date", "Created Date");
        m.put("ui.cepc.col.updated_date", "Updated Date");
        m.put("ui.cepc.col.sla_breach_in", "SLA Breach In");

        // ═══ Status chips — keys mirror CepcStatus.LABELS, including the legacy spellings ═══
        // The legacy tokens share their canonical twin's wording because CepcStatus.LABELS gives them the
        // same English; a row stored under either spelling must read identically.
        m.put("ui.status.new_complaint", "New Complaint");
        m.put("ui.status.information_required", "Information Required");
        m.put("ui.status.meeting_scheduled", "Meeting Scheduled");
        m.put("ui.status.advisory_complied", "Advisory Complied");
        m.put("ui.status.sent_to_reviewer", "Sent To Reviewer");
        m.put("ui.status.sent_to_incharge", "Sent To Incharge");
        m.put("ui.status.sent_to_closing_authority", "Sent To Closing Authority");
        m.put("ui.status.sent_back_to_do", "Sent Back To DO");
        m.put("ui.status.sent_back_to_reviewer", "Sent Back To Reviewer");
        m.put("ui.status.sent_back_to_incharge", "Sent Back To Incharge");
        m.put("ui.status.sent_to_rbi", "Sent To RBI");
        m.put("ui.status.complaint_reopen", "Complaint Re Open");
        m.put("ui.status.complaint_rejected", "Complaint Rejected");
        m.put("ui.status.rejected", "Complaint Rejected");
        m.put("ui.status.pending_office_head_approval", "Pending Office Head Approval");
        m.put("ui.status.complaint_settled", "Complaint Settled");
        m.put("ui.status.marked_for_closure", "Marked For Closure");
        // MARK_FOR_CLOSURE has no CepcStatus.LABELS entry, so label() title-cases it to "Mark For Closure".
        // Seeded with that exact wording rather than "Marked For Closure": the two are different statuses and
        // silently merging them here would change what the English chip has always read.
        m.put("ui.status.mark_for_closure", "Mark For Closure");
        m.put("ui.status.sent_to_other_departments", "Sent To Other Departments");
        m.put("ui.status.forwarded_to_contact", "Sent To Other Departments");
        m.put("ui.status.sent_to_other_regulated_bodies", "Sent To Other Regulated Bodies");
        m.put("ui.status.forwarded_external", "Sent To Other Regulated Bodies");
        m.put("ui.status.sent_to_other_office", "Sent To Other Office");
        m.put("ui.status.sent_to_other", "Sent To Other Office");
        m.put("ui.status.complaint_closed", "Complaint Closed");
        m.put("ui.status.complaint_withdrawn", "Complaint Withdrawn");
        m.put("ui.status.forwarded", "Forwarded");
        m.put("ui.status.resolved", "Resolved");
        m.put("ui.status.draft", "Draft");

        // ═══ Status dropdown — keys mirror CEPC_DASHBOARD_FILTER codes, wording mirrors LABEL_EN ═══
        m.put("ui.filter.all_complaints", "All Complaints");
        m.put("ui.filter.complaint_assigned_to_me", "Complaint Assigned To Me");
        m.put("ui.filter.new_complaint", "New Complaint");
        m.put("ui.filter.draft_complaints", "Draft Complaints");
        m.put("ui.filter.meeting_scheduled", "Meeting Scheduled");
        m.put("ui.filter.sent_to_cepc_dealing_official", "Sent to CEPC Dealing Official");
        m.put("ui.filter.sent_to_cepc_reviewer", "Sent to CEPC Reviewer");
        m.put("ui.filter.sent_to_incharge", "Sent to CEPC In-charge");
        m.put("ui.filter.sent_to_closing_authority", "Sent to Closing Authority");
        m.put("ui.filter.sent_back_to_cepc_do", "Sent Back to CEPC Dealing Official");
        m.put("ui.filter.sent_back_to_cepc_reviewer", "Sent Back to CEPC Reviewer");
        m.put("ui.filter.sent_back_to_incharge", "Sent Back to CEPC In-charge");
        m.put("ui.filter.sent_to_other_rbi_department", "Sent to Other RBI Department");
        m.put("ui.filter.sent_to_other_regulated_bodies", "Sent to Other Regulated Bodies");
        m.put("ui.filter.sent_to_other_office", "Sent to Other Office");
        m.put("ui.filter.reopened_complaints", "Reopened Complaints");
        m.put("ui.filter.mark_for_closure", "Mark for Closure");
        m.put("ui.filter.closed_complaints", "Closed Complaints");

        // ═══ Grid chrome ═══
        m.put("ui.cepc.grid.unread_only", "Unread");
        m.put("ui.cepc.grid.without_attachments", "Without Attachments");
        m.put("ui.cepc.grid.fixed_column", "Fixed column");
        m.put("ui.cepc.grid.search_placeholder", "Search...");
        m.put("ui.cepc.grid.empty", "No complaints records found matching current criteria.");
        // Single braces, not {{...}}: PrimeNG's currentPageReportTemplate substitutes these itself, and
        // TranslationService.translate() only rewrites {{double}} placeholders, so these pass through intact.
        m.put("ui.cepc.grid.page_report", "Showing {first} to {last} of {totalRecords} entries");

        // ═══ Column picker ═══
        m.put("ui.cepc.picker.title", "Manage Columns");
        m.put("ui.cepc.picker.subtitle", "Choose which columns to display in \"{{table}}\" table");
        m.put("ui.cepc.picker.find", "Find a column...");
        m.put("ui.cepc.picker.clear_search", "Clear search");
        m.put("ui.cepc.picker.selected", "selected");
        m.put("ui.cepc.picker.select_all", "Select All");
        m.put("ui.cepc.picker.deselect", "Deselect");
        m.put("ui.cepc.picker.drag_hint", "Drag to reorder");
        m.put("ui.cepc.picker.on", "ON");
        m.put("ui.cepc.picker.no_match", "No columns match \"{{query}}\"");
        m.put("ui.cepc.picker.reset", "Reset to Default");
        m.put("ui.cepc.picker.reset_tooltip", "Restore all columns to default order and visibility");
        m.put("ui.cepc.picker.cancel", "Cancel");
        m.put("ui.cepc.picker.apply", "Apply");

        // ═══ Tab strip ═══
        m.put("ui.cepc.tab.all", "All");
        m.put("ui.cepc.tab.draft", "Draft");
        m.put("ui.cepc.tab.meeting_scheduled", "Meeting Scheduled");
        m.put("ui.cepc.tab.sent_back_to_me", "Sent Back to Me");
        m.put("ui.cepc.tab.sent_to_re", "Sent to RE");
        m.put("ui.cepc.tab.contact_person", "Contact Person");

        // ═══ KPI tiles ═══
        m.put("ui.cepc.kpi.total_pending", "Total Pending Complaints");
        m.put("ui.cepc.kpi.pending_with_me", "Pending with Me");
        m.put("ui.cepc.kpi.pending_with_re", "Pending with RE");
        m.put("ui.cepc.kpi.meeting_scheduled", "Meeting Scheduled");
        m.put("ui.cepc.kpi.pending_at_meeting_scheduled", "Pending at Meeting Scheduled");
        m.put("ui.cepc.kpi.sla_tracking", "SLA Analysis Tracking");
        m.put("ui.cepc.kpi.sla_breached", "SLA Breached");
        m.put("ui.cepc.kpi.sla_0_15", "0-15 Days");
        m.put("ui.cepc.kpi.sla_16_30", "16-30 Days");

        // ═══ Dashboard toolbar ═══
        m.put("ui.cepc.dash.title", "{{dept}} Complaints");
        m.put("ui.cepc.dash.advanced_search", "Advanced Search");
        m.put("ui.cepc.dash.clear_search", "Clear Search");
        m.put("ui.cepc.dash.filter", "Filter");
        m.put("ui.cepc.dash.filter_count", "Filter ({{count}})");
        m.put("ui.cepc.dash.select_status", "Select a status");
        m.put("ui.cepc.dash.draft_complaint", "Draft Complaint");
        m.put("ui.cepc.dash.clear_all", "Clear All");

        // ═══ Filter drawer ═══
        m.put("ui.cepc.filter.title", "Dashboard Filters");
        m.put("ui.cepc.filter.cat.states", "State");
        m.put("ui.cepc.filter.cat.districts", "District");
        m.put("ui.cepc.filter.cat.years", "Year");
        m.put("ui.cepc.filter.cat.quarters", "Quarter");
        m.put("ui.cepc.filter.cat.meeting_types", "Meeting");
        m.put("ui.cepc.filter.cat.document_types", "Document");
        m.put("ui.cepc.filter.quarter_1", "Quarter 1 (Apr - Jun)");
        m.put("ui.cepc.filter.quarter_2", "Quarter 2 (Jul - Sep)");
        m.put("ui.cepc.filter.quarter_3", "Quarter 3 (Oct - Dec)");
        m.put("ui.cepc.filter.quarter_4", "Quarter 4 (Jan - Mar)");
        m.put("ui.cepc.filter.loading_districts", "Loading districts...");
        m.put("ui.cepc.filter.select_state_first", "Please select a State first to view districts.");
        m.put("ui.cepc.filter.no_options", "No options available");
        m.put("ui.cepc.filter.select_all", "Select All");
        m.put("ui.cepc.filter.deselect_all", "Deselect All");
        m.put("ui.cepc.filter.clear_all", "Clear All");
        m.put("ui.cepc.filter.apply", "Apply Filters");

        // ═══ Sidebar and app header ═══
        m.put("ui.cepc.nav.complaints", "Complaints");
        m.put("ui.cepc.nav.reports", "Reports");
        m.put("ui.cepc.nav.expand", "Expand");
        m.put("ui.cepc.nav.collapse", "Collapse");
        m.put("ui.cepc.header.toggle_theme", "Toggle Theme");
        m.put("ui.cepc.header.logout", "Logout");

        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("ui.col.assigned_to", "सौंपा गया");
        m.put("ui.col.contact_person", "संपर्क व्यक्ति");

        m.put("ui.cepc.col.complainant_name", "शिकायतकर्ता का नाम");
        m.put("ui.cepc.col.entity_name", "संस्था का नाम");
        m.put("ui.cepc.col.complaint_category", "शिकायत श्रेणी");
        m.put("ui.cepc.col.created_date", "निर्माण तिथि");
        m.put("ui.cepc.col.updated_date", "अद्यतन तिथि");
        m.put("ui.cepc.col.sla_breach_in", "एसएलए भंग होने में");

        m.put("ui.status.new_complaint", "नई शिकायत");
        m.put("ui.status.information_required", "जानकारी आवश्यक");
        m.put("ui.status.meeting_scheduled", "बैठक निर्धारित");
        m.put("ui.status.advisory_complied", "सलाह का अनुपालन");
        m.put("ui.status.sent_to_reviewer", "समीक्षक को भेजा गया");
        m.put("ui.status.sent_to_incharge", "प्रभारी को भेजा गया");
        m.put("ui.status.sent_to_closing_authority", "समापन प्राधिकारी को भेजा गया");
        m.put("ui.status.sent_back_to_do", "कार्यकारी अधिकारी को वापस भेजा गया");
        m.put("ui.status.sent_back_to_reviewer", "समीक्षक को वापस भेजा गया");
        m.put("ui.status.sent_back_to_incharge", "प्रभारी को वापस भेजा गया");
        m.put("ui.status.sent_to_rbi", "भारतीय रिज़र्व बैंक को भेजा गया");
        m.put("ui.status.complaint_reopen", "शिकायत पुनः खोली गई");
        m.put("ui.status.complaint_rejected", "शिकायत अस्वीकृत");
        m.put("ui.status.rejected", "शिकायत अस्वीकृत");
        m.put("ui.status.pending_office_head_approval", "कार्यालय प्रमुख की स्वीकृति लंबित");
        m.put("ui.status.complaint_settled", "शिकायत निपटाई गई");
        m.put("ui.status.marked_for_closure", "समापन हेतु चिह्नित");
        m.put("ui.status.mark_for_closure", "समापन हेतु चिह्नित करें");
        m.put("ui.status.sent_to_other_departments", "अन्य विभागों को भेजा गया");
        m.put("ui.status.forwarded_to_contact", "अन्य विभागों को भेजा गया");
        m.put("ui.status.sent_to_other_regulated_bodies", "अन्य विनियमित संस्थाओं को भेजा गया");
        m.put("ui.status.forwarded_external", "अन्य विनियमित संस्थाओं को भेजा गया");
        m.put("ui.status.sent_to_other_office", "अन्य कार्यालय को भेजा गया");
        m.put("ui.status.sent_to_other", "अन्य कार्यालय को भेजा गया");
        m.put("ui.status.complaint_closed", "शिकायत बंद");
        m.put("ui.status.complaint_withdrawn", "शिकायत वापस ली गई");
        m.put("ui.status.forwarded", "अग्रेषित");
        m.put("ui.status.resolved", "समाधान हुआ");
        m.put("ui.status.draft", "प्रारूप");

        m.put("ui.filter.all_complaints", "सभी शिकायतें");
        m.put("ui.filter.complaint_assigned_to_me", "मुझे सौंपी गई शिकायतें");
        m.put("ui.filter.new_complaint", "नई शिकायत");
        m.put("ui.filter.draft_complaints", "प्रारूप शिकायतें");
        m.put("ui.filter.meeting_scheduled", "बैठक निर्धारित");
        m.put("ui.filter.sent_to_cepc_dealing_official", "सीईपीसी कार्यकारी अधिकारी को भेजी गई");
        m.put("ui.filter.sent_to_cepc_reviewer", "सीईपीसी समीक्षक को भेजी गई");
        m.put("ui.filter.sent_to_incharge", "सीईपीसी प्रभारी को भेजी गई");
        m.put("ui.filter.sent_to_closing_authority", "समापन प्राधिकारी को भेजी गई");
        m.put("ui.filter.sent_back_to_cepc_do", "सीईपीसी कार्यकारी अधिकारी को वापस भेजी गई");
        m.put("ui.filter.sent_back_to_cepc_reviewer", "सीईपीसी समीक्षक को वापस भेजी गई");
        m.put("ui.filter.sent_back_to_incharge", "सीईपीसी प्रभारी को वापस भेजी गई");
        m.put("ui.filter.sent_to_other_rbi_department", "अन्य आरबीआई विभाग को भेजी गई");
        m.put("ui.filter.sent_to_other_regulated_bodies", "अन्य विनियमित संस्थाओं को भेजी गई");
        m.put("ui.filter.sent_to_other_office", "अन्य कार्यालय को भेजी गई");
        m.put("ui.filter.reopened_complaints", "पुनः खोली गई शिकायतें");
        m.put("ui.filter.mark_for_closure", "समापन हेतु चिह्नित");
        m.put("ui.filter.closed_complaints", "बंद शिकायतें");

        m.put("ui.cepc.grid.unread_only", "केवल अपठित");
        m.put("ui.cepc.grid.without_attachments", "बिना अनुलग्नक");
        m.put("ui.cepc.grid.fixed_column", "स्थिर स्तंभ");
        m.put("ui.cepc.grid.search_placeholder", "खोजें...");
        m.put("ui.cepc.grid.empty", "वर्तमान मानदंडों से मेल खाता कोई शिकायत रिकॉर्ड नहीं मिला।");
        m.put("ui.cepc.grid.page_report", "{totalRecords} प्रविष्टियों में से {first} से {last} तक दिखा रहे हैं");

        m.put("ui.cepc.picker.title", "स्तंभ प्रबंधित करें");
        m.put("ui.cepc.picker.subtitle", "\"{{table}}\" तालिका में दिखाने के लिए स्तंभ चुनें");
        m.put("ui.cepc.picker.find", "स्तंभ खोजें...");
        m.put("ui.cepc.picker.clear_search", "खोज साफ़ करें");
        m.put("ui.cepc.picker.selected", "चयनित");
        m.put("ui.cepc.picker.select_all", "सभी चुनें");
        m.put("ui.cepc.picker.deselect", "चयन हटाएँ");
        m.put("ui.cepc.picker.drag_hint", "क्रम बदलने के लिए खींचें");
        m.put("ui.cepc.picker.on", "चालू");
        m.put("ui.cepc.picker.no_match", "\"{{query}}\" से मेल खाता कोई स्तंभ नहीं");
        m.put("ui.cepc.picker.reset", "डिफ़ॉल्ट पर पुनः सेट करें");
        m.put("ui.cepc.picker.reset_tooltip", "सभी स्तंभों को डिफ़ॉल्ट क्रम और दृश्यता पर पुनर्स्थापित करें");
        m.put("ui.cepc.picker.cancel", "रद्द करें");
        m.put("ui.cepc.picker.apply", "लागू करें");

        m.put("ui.cepc.tab.all", "सभी");
        m.put("ui.cepc.tab.draft", "प्रारूप");
        m.put("ui.cepc.tab.meeting_scheduled", "बैठक निर्धारित");
        m.put("ui.cepc.tab.sent_back_to_me", "मुझे वापस भेजी गई");
        m.put("ui.cepc.tab.sent_to_re", "विनियमित संस्था को भेजी गई");
        m.put("ui.cepc.tab.contact_person", "संपर्क व्यक्ति");

        m.put("ui.cepc.kpi.total_pending", "कुल लंबित शिकायतें");
        m.put("ui.cepc.kpi.pending_with_me", "मेरे पास लंबित");
        m.put("ui.cepc.kpi.pending_with_re", "विनियमित संस्था के पास लंबित");
        m.put("ui.cepc.kpi.meeting_scheduled", "बैठक निर्धारित");
        m.put("ui.cepc.kpi.pending_at_meeting_scheduled", "बैठक निर्धारित पर लंबित");
        m.put("ui.cepc.kpi.sla_tracking", "एसएलए विश्लेषण ट्रैकिंग");
        m.put("ui.cepc.kpi.sla_breached", "एसएलए उल्लंघन");
        m.put("ui.cepc.kpi.sla_0_15", "0-15 दिन");
        m.put("ui.cepc.kpi.sla_16_30", "16-30 दिन");

        m.put("ui.cepc.dash.title", "{{dept}} शिकायतें");
        m.put("ui.cepc.dash.advanced_search", "उन्नत खोज");
        m.put("ui.cepc.dash.clear_search", "खोज साफ़ करें");
        m.put("ui.cepc.dash.filter", "छानें");
        m.put("ui.cepc.dash.filter_count", "छानें ({{count}})");
        m.put("ui.cepc.dash.select_status", "स्थिति चुनें");
        m.put("ui.cepc.dash.draft_complaint", "प्रारूप शिकायत");
        m.put("ui.cepc.dash.clear_all", "सभी साफ़ करें");

        m.put("ui.cepc.filter.title", "डैशबोर्ड फ़िल्टर");
        m.put("ui.cepc.filter.cat.states", "राज्य");
        m.put("ui.cepc.filter.cat.districts", "जिला");
        m.put("ui.cepc.filter.cat.years", "वर्ष");
        m.put("ui.cepc.filter.cat.quarters", "तिमाही");
        m.put("ui.cepc.filter.cat.meeting_types", "बैठक");
        m.put("ui.cepc.filter.cat.document_types", "दस्तावेज़");
        m.put("ui.cepc.filter.quarter_1", "तिमाही 1 (अप्रैल - जून)");
        m.put("ui.cepc.filter.quarter_2", "तिमाही 2 (जुलाई - सितंबर)");
        m.put("ui.cepc.filter.quarter_3", "तिमाही 3 (अक्टूबर - दिसंबर)");
        m.put("ui.cepc.filter.quarter_4", "तिमाही 4 (जनवरी - मार्च)");
        m.put("ui.cepc.filter.loading_districts", "जिले लोड हो रहे हैं...");
        m.put("ui.cepc.filter.select_state_first", "जिले देखने के लिए पहले राज्य चुनें।");
        m.put("ui.cepc.filter.no_options", "कोई विकल्प उपलब्ध नहीं");
        m.put("ui.cepc.filter.select_all", "सभी चुनें");
        m.put("ui.cepc.filter.deselect_all", "सभी का चयन हटाएँ");
        m.put("ui.cepc.filter.clear_all", "सभी साफ़ करें");
        m.put("ui.cepc.filter.apply", "फ़िल्टर लागू करें");

        m.put("ui.cepc.nav.complaints", "शिकायतें");
        m.put("ui.cepc.nav.reports", "रिपोर्ट");
        m.put("ui.cepc.nav.expand", "विस्तृत करें");
        m.put("ui.cepc.nav.collapse", "संकुचित करें");
        m.put("ui.cepc.header.toggle_theme", "थीम बदलें");
        m.put("ui.cepc.header.logout", "लॉग आउट");

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
