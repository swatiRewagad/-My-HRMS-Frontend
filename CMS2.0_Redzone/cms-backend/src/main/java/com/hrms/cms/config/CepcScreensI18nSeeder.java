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
 * The remaining CEPC screens' vocabulary: create/draft complaint, the compact complaint detail, advanced
 * search, the SLA dashboard, conciliation, legal case, the right-side panels and the forward workflow.
 *
 * <p>Completes the set begun by {@link CepcDashboardI18nSeeder} and {@link CepcDetailViewI18nSeeder}, so
 * {@code GET /api/v1/i18n/translations/{locale}} alone is enough to render any CEPC screen in either
 * language, in any environment.
 *
 * <p><b>Deliberately NOT seeded</b>, because a key would make these worse rather than better:
 * <ul>
 *   <li><b>RBI branding</b> — the bilingual lockup ("भारतीय रिज़र्व बैंक / Reserve Bank of India",
 *       "RESERVE BANK OF INDIA", "Complaint Management System") is a fixed mark that shows both scripts at
 *       once by design, so switching one half to Hindi would break it.</li>
 *   <li><b>Acronyms</b> — CMS, RB, CEPC, RBIO, CPGRAMS read the same in both languages.</li>
 *   <li><b>Date-format masks</b> — {@code dd-mm-yyyy} is a pattern, not prose.</li>
 *   <li><b>Unbuilt-feature stubs</b> — "assessment stub" and friends are placeholders that will be deleted,
 *       so translating them would be churn.</li>
 *   <li><b>Labels fused to an HTML entity</b> — "&amp;#128197; Schedule Hearing" carries its icon inside the
 *       text node. Keying it would put the entity in the translation value; these need the icon lifted into
 *       its own element first, which is a template change rather than a translation.</li>
 *   <li><b>Sentence fragments</b> — "Are you sure you want to", "the request ?", "has been routed to",
 *       "— meeting scheduled for" and the rest are concatenated around interpolated values. They are listed
 *       as whole sentences under {@code ui.cepc.msg.*} here; the template has to be restructured to use one
 *       key, because Hindi is verb-final and cannot be reassembled from English pieces.</li>
 * </ul>
 *
 * <p>A NEW seeder at {@code @Order(74)}; 60-73 were verified taken.
 */
@Component
@Order(74)
public class CepcScreensI18nSeeder implements CommandLineRunner {

    private static final String MODULE = "cepc";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public CepcScreensI18nSeeder(TranslationKeyRepository keyRepo,
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

        // ═══ Create / draft complaint ═══
        m.put("ui.cepc.cc.add_attachment", "Add Attachment");
        m.put("ui.cepc.cc.add_no_record", "Add NO Record");
        m.put("ui.cepc.cc.nodal_officer_record", "Nodal Officer Record");
        m.put("ui.cepc.cc.assigned_to_dealing_officer", "Assigned to Dealing Officer");
        m.put("ui.cepc.cc.assigned_user_on_leave", "Assigned user is on Leave");
        m.put("ui.cepc.cc.award_pass", "Award Pass");
        m.put("ui.cepc.cc.branch_category", "Branch Category");
        m.put("ui.cepc.cc.branch_center_name", "Branch Center Name");
        m.put("ui.cepc.cc.branch_name", "Branch Name");
        m.put("ui.cepc.cc.browse", "Browse");
        m.put("ui.cepc.cc.choose_file", "Choose file");
        m.put("ui.cepc.cc.closure_clause", "Closure Clause");
        m.put("ui.cepc.cc.closure_clause_description", "Closure Clause Description");
        m.put("ui.cepc.cc.complaint_created", "Complaint Created");
        m.put("ui.cepc.cc.complaint_owner", "Complaint Owner");
        m.put("ui.cepc.cc.complaint_view", "Complaint view");
        m.put("ui.cepc.cc.created_on", "Created On");
        m.put("ui.cepc.cc.dashboard", "Dashboard");
        m.put("ui.cepc.cc.back_to_dashboard", "Back to Dashboard");
        m.put("ui.cepc.cc.due_date", "Due Date");
        m.put("ui.cepc.cc.file", "File");
        m.put("ui.cepc.cc.history_timeline", "History / Timeline");
        m.put("ui.cepc.cc.incharge_decision", "Incharge Decision");
        m.put("ui.cepc.cc.incharge_decision_comments", "Incharge Decision Comments");
        m.put("ui.cepc.cc.more_options", "More options");
        m.put("ui.cepc.cc.name_of_crpc_deo", "Name of CRPC DEO");
        m.put("ui.cepc.cc.name_of_office", "Name of Office");
        m.put("ui.cepc.cc.not_assigned", "Not Assigned");
        m.put("ui.cepc.cc.not_generated", "Not Generated");
        m.put("ui.cepc.cc.open", "Open");
        m.put("ui.cepc.cc.no_drafts", "No drafts");
        m.put("ui.cepc.cc.no_open_activities", "No open activities");
        m.put("ui.cepc.cc.proposed_action", "Proposed Action");
        m.put("ui.cepc.cc.proposed_clause", "Proposed Clause");
        m.put("ui.cepc.cc.query", "Query");
        m.put("ui.cepc.cc.reason_not_a_complaint", "Reason for not a complaint");
        m.put("ui.cepc.cc.select_action", "Select Action");
        m.put("ui.cepc.cc.select_template", "Select Template");
        m.put("ui.cepc.cc.send", "Send");
        m.put("ui.cepc.cc.send_back", "Send Back");
        m.put("ui.cepc.cc.send_email", "Send Email");
        m.put("ui.cepc.cc.send_to_re", "Send to RE");
        m.put("ui.cepc.cc.speaking_order_generated", "Speaking order generated?");
        m.put("ui.cepc.cc.suggestion", "Suggestion");
        m.put("ui.cepc.cc.create", "Create");
        m.put("ui.cepc.cc.confirm", "Confirm");
        m.put("ui.cepc.cc.skip", "Skip");
        m.put("ui.cepc.cc.phone", "Phone");
        m.put("ui.cepc.cc.email_address", "Email address");
        m.put("ui.cepc.cc.full_name", "Full name");
        m.put("ui.cepc.cc.full_address", "Full address");
        m.put("ui.cepc.cc.load_failed_title", "Couldn't load this complaint");
        m.put("ui.cepc.cc.load_failed_body",
                "The complaint could not be fetched from the server. Please check the link and try again.");

        // Intake channels and document handling
        m.put("ui.cepc.cc.portal", "Portal");
        m.put("ui.cepc.cc.cpgrams", "CPGRAMS");
        m.put("ui.cepc.cc.physical_letter", "Physical Letter");
        m.put("ui.cepc.cc.physical_letter_receipt_date", "Physical Letter Receipt Date");
        m.put("ui.cepc.cc.scan_upload_physical_letter", "Scan and Upload Physical Letter Copy");
        m.put("ui.cepc.cc.reference_complaint_copy", "Reference Complaint Copy");
        m.put("ui.cepc.cc.remove_file", "Remove File");
        m.put("ui.cepc.cc.drag_and_drop_or", "Drag and Drop or");
        m.put("ui.cepc.cc.drag_and_drop_file_here_or", "Drag and Drop file here or");
        m.put("ui.cepc.cc.max_size_all_files_2mb", "Maximum Size of All Files: 2MB");
        m.put("ui.cepc.cc.max_size_5mb", "Maximum size: 5MB");
        m.put("ui.cepc.cc.formats_pdf_doc_jpg_png", "Support formats: PDF, DOC, JPG, PNG");
        m.put("ui.cepc.cc.formats_pdf_word_jpeg_png", "Support formats: PDF, WORD, JPEG, PNG");
        m.put("ui.cepc.cc.extraction_complete", "Extraction complete");
        m.put("ui.cepc.cc.extraction_disclaimer",
                "Content extraction may not be 100% accurate. Please verify all information.");

        // Create-complaint placeholders
        m.put("ui.cepc.cc.ph.enter_value", "Enter a value");
        m.put("ui.cepc.cc.ph.mobile_10_digit", "10-digit mobile");
        m.put("ui.cepc.cc.ph.pincode_6_digit", "6-digit pincode");
        m.put("ui.cepc.cc.ph.enter_pincode", "Enter pincode");
        m.put("ui.cepc.cc.ph.enter_bsr_code", "Enter BSR code");
        m.put("ui.cepc.cc.ph.enter_branch_center_name", "Enter branch center name");
        m.put("ui.cepc.cc.ph.enter_country_name", "Enter Country name");
        m.put("ui.cepc.cc.ph.enter_entity_address", "Enter entity address");
        m.put("ui.cepc.cc.ph.enter_cpgram_number", "Enter CPGRAM number");
        m.put("ui.cepc.cc.ph.select_city_name", "Select city name");
        m.put("ui.cepc.cc.ph.select_district_name", "Select district name");
        m.put("ui.cepc.cc.ph.select_state_name", "Select state name");
        m.put("ui.cepc.cc.ph.select_district", "Select District");
        m.put("ui.cepc.cc.ph.select_state", "Select State");
        m.put("ui.cepc.cc.ph.select_entity_branch_category", "Select entity branch category");
        m.put("ui.cepc.cc.ph.select_entity_branch_name", "Select entity branch name");
        m.put("ui.cepc.cc.ph.loading_offices", "Loading offices…");

        // ═══ Compact complaint detail screen ═══
        m.put("ui.cepc.cd.title", "CEPC Complaint Examination");
        m.put("ui.cepc.cd.complaint_information", "Complaint Information");
        m.put("ui.cepc.cd.available_actions", "Available Actions");
        m.put("ui.cepc.cd.assign_to", "Assign To");
        m.put("ui.cepc.cd.assigned", "Assigned");
        m.put("ui.cepc.cd.assigned_officer", "Assigned Officer");
        m.put("ui.cepc.cd.amount_involved", "Amount Involved");
        m.put("ui.cepc.cd.category", "Category");
        m.put("ui.cepc.cd.department", "Department");
        m.put("ui.cepc.cd.description", "Description");
        m.put("ui.cepc.cd.filing_type", "Filing Type");
        m.put("ui.cepc.cd.priority", "Priority");
        m.put("ui.cepc.cd.transaction_date", "Transaction Date");
        m.put("ui.cepc.cd.target_department", "Target Department");
        m.put("ui.cepc.cd.select_department", "Select Department");
        m.put("ui.cepc.cd.edit_complaint", "Edit Complaint");
        m.put("ui.cepc.cd.upload_in_progress", "Uploading...");
        m.put("ui.cepc.cd.no_documents_uploaded", "No documents uploaded yet.");
        m.put("ui.cepc.cd.not_found", "Complaint not found.");
        m.put("ui.cepc.cd.loading", "Loading complaint details...");
        m.put("ui.cepc.cd.select_an_action", "Select an action to perform on this complaint.");
        m.put("ui.cepc.cd.closed_no_actions", "This complaint has been closed. No further actions available.");
        m.put("ui.cepc.cd.why_changed", "Why is this record being changed?");

        // Priority and progress words shown on the compact detail screen
        m.put("ui.cepc.cd.priority_critical", "Critical");
        m.put("ui.cepc.cd.priority_high", "High");
        m.put("ui.cepc.cd.priority_medium", "Medium");
        m.put("ui.cepc.cd.priority_low", "Low");
        m.put("ui.cepc.cd.state_pending", "Pending");
        m.put("ui.cepc.cd.state_in_progress", "In Progress");
        m.put("ui.cepc.cd.state_under_review", "Under Review");
        m.put("ui.cepc.cd.state_escalated", "Escalated");

        // ═══ Advanced search ═══
        m.put("ui.cepc.as.clear_filters", "Clear Filters");
        m.put("ui.cepc.as.complaint_id", "Complaint Id");
        m.put("ui.cepc.as.complainant_email", "Complainant Email");
        m.put("ui.cepc.as.complainant_mobile", "Complainant Mobile Number");
        m.put("ui.cepc.as.complaint_main_category", "Complaint Main Category");
        m.put("ui.cepc.as.from_email_id", "From Email ID");
        m.put("ui.cepc.as.no_contact_person", "NO/ Contact Person");
        m.put("ui.cepc.as.reported_on", "Reported On");
        m.put("ui.cepc.as.select_a_bank", "Select a Bank");

        // ═══ SLA dashboard ═══
        m.put("ui.cepc.sla.title", "CEPC - SLA Compliance Dashboard");
        m.put("ui.cepc.sla.subtitle", "Service Level Agreement Monitoring");
        m.put("ui.cepc.sla.total_active", "Total Active");
        m.put("ui.cepc.sla.breached", "Breached");
        m.put("ui.cepc.sla.breached_caps", "BREACHED");
        m.put("ui.cepc.sla.at_risk", "At Risk");
        m.put("ui.cepc.sla.on_track", "On Track");
        m.put("ui.cepc.sla.all_clear", "All Clear");
        m.put("ui.cepc.sla.attention_required", "Attention Required");
        m.put("ui.cepc.sla.overall_compliance", "Overall Compliance");
        m.put("ui.cepc.sla.compliance", "SLA Compliance");
        m.put("ui.cepc.sla.status", "SLA Status");
        m.put("ui.cepc.sla.complaint_no", "Complaint No.");
        m.put("ui.cepc.sla.officer", "Officer");
        m.put("ui.cepc.sla.days", "Days");
        m.put("ui.cepc.sla.view", "View");
        m.put("ui.cepc.sla.loading", "Loading SLA data...");
        m.put("ui.cepc.sla.empty", "No breached or at-risk complaints.");

        // ═══ Conciliation ═══
        m.put("ui.cepc.conc.proceedings", "Conciliation Proceedings");
        m.put("ui.cepc.conc.schedule_hearing", "Schedule Conciliation Hearing");
        m.put("ui.cepc.conc.record_outcome", "Record Conciliation Outcome");
        m.put("ui.cepc.conc.parties_involved", "Parties Involved");
        m.put("ui.cepc.conc.venue", "Venue");
        m.put("ui.cepc.conc.notes", "Notes");
        m.put("ui.cepc.conc.date_required", "Date *");
        m.put("ui.cepc.conc.time_required", "Time *");
        m.put("ui.cepc.conc.outcome_required", "Outcome *");
        m.put("ui.cepc.conc.summary_notes_required", "Summary Notes *");
        m.put("ui.cepc.conc.amount_in_inr", "Amount in INR");
        m.put("ui.cepc.conc.compensation_amount", "Compensation Amount (if applicable)");
        m.put("ui.cepc.conc.settled", "Settled");
        m.put("ui.cepc.conc.failed", "Failed");
        m.put("ui.cepc.conc.status_prefix", "Status:");
        m.put("ui.cepc.conc.uploading_minutes", "Uploading minutes...");
        m.put("ui.cepc.conc.minutes_uploaded", "Minutes uploaded successfully.");
        m.put("ui.cepc.conc.ph.venue", "Meeting venue or video link");
        m.put("ui.cepc.conc.ph.outcome_summary", "Conciliation outcome summary...");
        m.put("ui.cepc.conc.ph.additional_instructions", "Additional instructions...");

        // Conciliation workflow panel
        m.put("ui.cepc.conc.meeting_status", "Meeting Status");
        m.put("ui.cepc.conc.meeting_comments", "Meeting Comments");
        m.put("ui.cepc.conc.meeting_cancelled", "Meeting Cancelled");
        m.put("ui.cepc.conc.meeting_rescheduled", "Meeting Rescheduled");
        m.put("ui.cepc.conc.meeting_marked_completed", "Meeting Marked Completed");
        m.put("ui.cepc.conc.accepted_by_complainant", "Accepted by Complainant");
        m.put("ui.cepc.conc.accepted_by_entity", "Accepted by Regulated Entity");
        m.put("ui.cepc.conc.via_video_conference", "Conducted through Video Conference");
        m.put("ui.cepc.conc.want_other_entities", "Want to add other entities");
        m.put("ui.cepc.conc.what_was_discussed", "What was discussed and agreed in the meeting");
        m.put("ui.cepc.conc.details_saved", "Conciliation details saved.");
        m.put("ui.cepc.conc.loading_details", "Loading conciliation details…");
        m.put("ui.cepc.conc.new_meeting_warning",
                "Saving this will start a new meeting. The current meeting is kept, unchanged, in the history below.");

        // ═══ Legal case ═══
        m.put("ui.cepc.lc.cases", "Legal Cases");
        m.put("ui.cepc.lc.update_details", "Update Details");
        m.put("ui.cepc.lc.update_details_title", "Legal Case — Update Details");
        m.put("ui.cepc.lc.case_no_year", "Case No./WP No & Year");
        m.put("ui.cepc.lc.court_name", "Name of the Court");
        m.put("ui.cepc.lc.advocate_name", "Name of the Advocate");
        m.put("ui.cepc.lc.assistant_legal_advisor", "Assistant Legal Advisor");
        m.put("ui.cepc.lc.parties_of_case", "Parties of Case");
        m.put("ui.cepc.lc.region_of_legal_team", "Region of Legal Team Involved");
        m.put("ui.cepc.lc.rbi_first_respondent", "Whether RBI/OBO is the 1st Respondent");
        m.put("ui.cepc.lc.appearance_required", "Appearance required in the Hearing");
        m.put("ui.cepc.lc.next_hearing_date", "Date of next hearing");
        m.put("ui.cepc.lc.action_taken_so_far", "Action taken so far");
        m.put("ui.cepc.lc.action_to_be_taken", "Action to be taken");
        m.put("ui.cepc.lc.brief_particulars",
                "Brief particular/Subject matter of the case and nature of relief claimed");
        m.put("ui.cepc.lc.monetary_claim_details",
                "Details of Monetary claim against RBI and the provisions made thereof");
        m.put("ui.cepc.lc.remarks_present_status", "Remarks/Present status of the Case");
        m.put("ui.cepc.lc.loading", "Loading...");

        // ═══ Right-side panels ═══
        m.put("ui.cepc.panel.attach_new_document", "Attach New Document");
        m.put("ui.cepc.panel.download_all", "Download All");
        m.put("ui.cepc.panel.no_attachments", "No attachments");
        m.put("ui.cepc.panel.view", "View");
        m.put("ui.cepc.panel.no_comments", "No comments");
        m.put("ui.cepc.panel.modified_by", "Modified By");
        m.put("ui.cepc.panel.modified_on", "Modified On");
        m.put("ui.cepc.panel.loading_history", "Loading history...");
        m.put("ui.cepc.panel.no_history_for_complaint", "No history available for this complaint.");
        m.put("ui.cepc.panel.audit_trail", "Audit Trail");
        m.put("ui.cepc.panel.loading_timeline", "Loading timeline...");
        m.put("ui.cepc.panel.audit_trail_failed", "Could not load the audit trail.");
        m.put("ui.cepc.panel.no_past_complaints", "No past complaints found");
        m.put("ui.cepc.panel.no_documents_attached", "No documents attached");
        m.put("ui.cepc.panel.documents", "Documents");
        m.put("ui.cepc.panel.reference", "Reference");
        m.put("ui.cepc.panel.complaint_id_prefix", "Complaint Id:");
        m.put("ui.cepc.panel.entity_prefix", "Entity:");
        m.put("ui.cepc.panel.modified_date_prefix", "Modified Date:");
        m.put("ui.cepc.panel.processing_office_prefix", "Processing Office:");

        // ═══ Forward / transfer workflow ═══
        m.put("ui.cepc.fw.forward_to", "Forward To");
        m.put("ui.cepc.fw.transfer_office", "Transfer Office");
        m.put("ui.cepc.fw.other_office", "Other Office");
        m.put("ui.cepc.fw.other_rbi_department", "Other RBI Department");
        m.put("ui.cepc.fw.other_regulatory_bodies", "Other Regulatory Bodies");
        m.put("ui.cepc.fw.name_of_department", "Name of Department");
        m.put("ui.cepc.fw.name_of_regulator", "Name of Regulator");
        m.put("ui.cepc.fw.email_of_department", "Email id of the Department");
        m.put("ui.cepc.fw.email_of_regulator", "Email id of the Regulator");
        m.put("ui.cepc.fw.reason_for_transfer", "Reason for Transfer");
        m.put("ui.cepc.fw.sent_from_office_comments", "Sent from Office Comments");
        m.put("ui.cepc.fw.ph.enter_transfer_reason", "Enter the reason for this transfer");
        m.put("ui.cepc.fw.ph.enter_sending_office_comments", "Enter comments from the sending office");
        m.put("ui.cepc.fw.no_department_match", "No department matches that name");
        m.put("ui.cepc.fw.no_regulator_match", "No regulator matches that name");

        // ═══ Maintainability wording variants ═══
        // Two more spellings of the same idea already on screen, including a typo. Seeded verbatim rather
        // than normalised: the rule on this screen is that the English must not change.
        m.put("ui.cepc.value.non_maintainable_spaced", "Non Maintainable");
        m.put("ui.cepc.value.non_maintanable_typo", "Non-Maintanable");

        // ═══ Whole-sentence messages for the fragment sites ═══
        // Not wired by the codemod — the templates concatenate these around interpolated values and must be
        // restructured by hand to use one key each.
        m.put("ui.cepc.msg.confirm_close", "Are you sure you want to close the complaint?");
        m.put("ui.cepc.msg.confirm_forward", "Are you sure you want to forward the complaint?");
        m.put("ui.cepc.msg.confirm_send_to_crpc_deo",
                "Are you sure you want to send the request to the CRPC DEO?");
        m.put("ui.cepc.msg.confirm_add_and_send",
                "Do you want to add and send {{name}} to the Dealing Officer?");
        m.put("ui.cepc.msg.meeting_scheduled_for", "Meeting scheduled for {{date}} at {{time}}.");
        m.put("ui.cepc.msg.meeting_rescheduled_for", "Meeting rescheduled for {{date}} at {{time}}.");
        m.put("ui.cepc.msg.meeting_cancelled", "Meeting cancelled.");
        m.put("ui.cepc.msg.meeting_marked_completed", "Meeting marked completed.");

        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("ui.cepc.cc.add_attachment", "अनुलग्नक जोड़ें");
        m.put("ui.cepc.cc.add_no_record", "नोडल अधिकारी रिकॉर्ड जोड़ें");
        m.put("ui.cepc.cc.nodal_officer_record", "नोडल अधिकारी रिकॉर्ड");
        m.put("ui.cepc.cc.assigned_to_dealing_officer", "कार्यकारी अधिकारी को सौंपा गया");
        m.put("ui.cepc.cc.assigned_user_on_leave", "नियुक्त उपयोगकर्ता अवकाश पर है");
        m.put("ui.cepc.cc.award_pass", "अधिनिर्णय पारित");
        m.put("ui.cepc.cc.branch_category", "शाखा श्रेणी");
        m.put("ui.cepc.cc.branch_center_name", "शाखा केंद्र का नाम");
        m.put("ui.cepc.cc.branch_name", "शाखा का नाम");
        m.put("ui.cepc.cc.browse", "ब्राउज़ करें");
        m.put("ui.cepc.cc.choose_file", "फ़ाइल चुनें");
        m.put("ui.cepc.cc.closure_clause", "समापन खंड");
        m.put("ui.cepc.cc.closure_clause_description", "समापन खंड का विवरण");
        m.put("ui.cepc.cc.complaint_created", "शिकायत बनाई गई");
        m.put("ui.cepc.cc.complaint_owner", "शिकायत स्वामी");
        m.put("ui.cepc.cc.complaint_view", "शिकायत दृश्य");
        m.put("ui.cepc.cc.created_on", "निर्माण तिथि");
        m.put("ui.cepc.cc.dashboard", "डैशबोर्ड");
        m.put("ui.cepc.cc.back_to_dashboard", "डैशबोर्ड पर वापस");
        m.put("ui.cepc.cc.due_date", "नियत तिथि");
        m.put("ui.cepc.cc.file", "फ़ाइल");
        m.put("ui.cepc.cc.history_timeline", "इतिहास / समयरेखा");
        m.put("ui.cepc.cc.incharge_decision", "प्रभारी का निर्णय");
        m.put("ui.cepc.cc.incharge_decision_comments", "प्रभारी के निर्णय की टिप्पणियाँ");
        m.put("ui.cepc.cc.more_options", "अधिक विकल्प");
        m.put("ui.cepc.cc.name_of_crpc_deo", "सीआरपीसी कार्यकारी अधिकारी का नाम");
        m.put("ui.cepc.cc.name_of_office", "कार्यालय का नाम");
        m.put("ui.cepc.cc.not_assigned", "आवंटित नहीं");
        m.put("ui.cepc.cc.not_generated", "सृजित नहीं");
        m.put("ui.cepc.cc.open", "खुला");
        m.put("ui.cepc.cc.no_drafts", "कोई प्रारूप नहीं");
        m.put("ui.cepc.cc.no_open_activities", "कोई खुली गतिविधि नहीं");
        m.put("ui.cepc.cc.proposed_action", "प्रस्तावित कार्रवाई");
        m.put("ui.cepc.cc.proposed_clause", "प्रस्तावित खंड");
        m.put("ui.cepc.cc.query", "पूछताछ");
        m.put("ui.cepc.cc.reason_not_a_complaint", "शिकायत न होने का कारण");
        m.put("ui.cepc.cc.select_action", "कार्रवाई चुनें");
        m.put("ui.cepc.cc.select_template", "टेम्पलेट चुनें");
        m.put("ui.cepc.cc.send", "भेजें");
        m.put("ui.cepc.cc.send_back", "वापस भेजें");
        m.put("ui.cepc.cc.send_email", "ईमेल भेजें");
        m.put("ui.cepc.cc.send_to_re", "विनियमित संस्था को भेजें");
        m.put("ui.cepc.cc.speaking_order_generated", "सकारण आदेश सृजित हुआ?");
        m.put("ui.cepc.cc.suggestion", "सुझाव");
        m.put("ui.cepc.cc.create", "बनाएँ");
        m.put("ui.cepc.cc.confirm", "पुष्टि करें");
        m.put("ui.cepc.cc.skip", "छोड़ें");
        m.put("ui.cepc.cc.phone", "दूरभाष");
        m.put("ui.cepc.cc.email_address", "ईमेल पता");
        m.put("ui.cepc.cc.full_name", "पूरा नाम");
        m.put("ui.cepc.cc.full_address", "पूरा पता");
        m.put("ui.cepc.cc.load_failed_title", "यह शिकायत लोड नहीं हो सकी");
        m.put("ui.cepc.cc.load_failed_body",
                "सर्वर से शिकायत प्राप्त नहीं की जा सकी। कृपया लिंक जाँचें और पुनः प्रयास करें।");

        m.put("ui.cepc.cc.portal", "पोर्टल");
        m.put("ui.cepc.cc.cpgrams", "सीपीग्राम्स");
        m.put("ui.cepc.cc.physical_letter", "भौतिक पत्र");
        m.put("ui.cepc.cc.physical_letter_receipt_date", "भौतिक पत्र प्राप्ति तिथि");
        m.put("ui.cepc.cc.scan_upload_physical_letter", "भौतिक पत्र की प्रतिलिपि स्कैन कर अपलोड करें");
        m.put("ui.cepc.cc.reference_complaint_copy", "संदर्भ शिकायत प्रतिलिपि");
        m.put("ui.cepc.cc.remove_file", "फ़ाइल हटाएँ");
        m.put("ui.cepc.cc.drag_and_drop_or", "खींचें और छोड़ें या");
        m.put("ui.cepc.cc.drag_and_drop_file_here_or", "फ़ाइल यहाँ खींचें और छोड़ें या");
        m.put("ui.cepc.cc.max_size_all_files_2mb", "सभी फ़ाइलों का अधिकतम आकार: 2 एमबी");
        m.put("ui.cepc.cc.max_size_5mb", "अधिकतम आकार: 5 एमबी");
        m.put("ui.cepc.cc.formats_pdf_doc_jpg_png", "समर्थित प्रारूप: PDF, DOC, JPG, PNG");
        m.put("ui.cepc.cc.formats_pdf_word_jpeg_png", "समर्थित प्रारूप: PDF, WORD, JPEG, PNG");
        m.put("ui.cepc.cc.extraction_complete", "निष्कर्षण पूर्ण");
        m.put("ui.cepc.cc.extraction_disclaimer",
                "विषय-वस्तु का निष्कर्षण 100% सटीक नहीं हो सकता। कृपया सभी जानकारी सत्यापित करें।");

        m.put("ui.cepc.cc.ph.enter_value", "कोई मान दर्ज करें");
        m.put("ui.cepc.cc.ph.mobile_10_digit", "10 अंकों का मोबाइल");
        m.put("ui.cepc.cc.ph.pincode_6_digit", "6 अंकों का पिन कोड");
        m.put("ui.cepc.cc.ph.enter_pincode", "पिन कोड दर्ज करें");
        m.put("ui.cepc.cc.ph.enter_bsr_code", "बीएसआर कोड दर्ज करें");
        m.put("ui.cepc.cc.ph.enter_branch_center_name", "शाखा केंद्र का नाम दर्ज करें");
        m.put("ui.cepc.cc.ph.enter_country_name", "देश का नाम दर्ज करें");
        m.put("ui.cepc.cc.ph.enter_entity_address", "संस्था का पता दर्ज करें");
        m.put("ui.cepc.cc.ph.enter_cpgram_number", "सीपीग्राम संख्या दर्ज करें");
        m.put("ui.cepc.cc.ph.select_city_name", "शहर का नाम चुनें");
        m.put("ui.cepc.cc.ph.select_district_name", "जिले का नाम चुनें");
        m.put("ui.cepc.cc.ph.select_state_name", "राज्य का नाम चुनें");
        m.put("ui.cepc.cc.ph.select_district", "जिला चुनें");
        m.put("ui.cepc.cc.ph.select_state", "राज्य चुनें");
        m.put("ui.cepc.cc.ph.select_entity_branch_category", "संस्था की शाखा श्रेणी चुनें");
        m.put("ui.cepc.cc.ph.select_entity_branch_name", "संस्था की शाखा का नाम चुनें");
        m.put("ui.cepc.cc.ph.loading_offices", "कार्यालय लोड हो रहे हैं…");

        m.put("ui.cepc.cd.title", "सीईपीसी शिकायत परीक्षण");
        m.put("ui.cepc.cd.complaint_information", "शिकायत की जानकारी");
        m.put("ui.cepc.cd.available_actions", "उपलब्ध कार्रवाइयाँ");
        m.put("ui.cepc.cd.assign_to", "किसे सौंपें");
        m.put("ui.cepc.cd.assigned", "सौंपा गया");
        m.put("ui.cepc.cd.assigned_officer", "नियुक्त अधिकारी");
        m.put("ui.cepc.cd.amount_involved", "संबंधित राशि");
        m.put("ui.cepc.cd.category", "श्रेणी");
        m.put("ui.cepc.cd.department", "विभाग");
        m.put("ui.cepc.cd.description", "विवरण");
        m.put("ui.cepc.cd.filing_type", "दाखिल करने का प्रकार");
        m.put("ui.cepc.cd.priority", "प्राथमिकता");
        m.put("ui.cepc.cd.transaction_date", "लेनदेन तिथि");
        m.put("ui.cepc.cd.target_department", "लक्षित विभाग");
        m.put("ui.cepc.cd.select_department", "विभाग चुनें");
        m.put("ui.cepc.cd.edit_complaint", "शिकायत संपादित करें");
        m.put("ui.cepc.cd.upload_in_progress", "अपलोड हो रहा है...");
        m.put("ui.cepc.cd.no_documents_uploaded", "अभी कोई दस्तावेज़ अपलोड नहीं किया गया।");
        m.put("ui.cepc.cd.not_found", "शिकायत नहीं मिली।");
        m.put("ui.cepc.cd.loading", "शिकायत का विवरण लोड हो रहा है...");
        m.put("ui.cepc.cd.select_an_action", "इस शिकायत पर करने योग्य कार्रवाई चुनें।");
        m.put("ui.cepc.cd.closed_no_actions", "यह शिकायत बंद कर दी गई है। आगे कोई कार्रवाई उपलब्ध नहीं।");
        m.put("ui.cepc.cd.why_changed", "यह रिकॉर्ड क्यों बदला जा रहा है?");

        m.put("ui.cepc.cd.priority_critical", "अत्यावश्यक");
        m.put("ui.cepc.cd.priority_high", "उच्च");
        m.put("ui.cepc.cd.priority_medium", "मध्यम");
        m.put("ui.cepc.cd.priority_low", "निम्न");
        m.put("ui.cepc.cd.state_pending", "लंबित");
        m.put("ui.cepc.cd.state_in_progress", "प्रगति पर");
        m.put("ui.cepc.cd.state_under_review", "समीक्षाधीन");
        m.put("ui.cepc.cd.state_escalated", "उच्चस्तर पर भेजा गया");

        m.put("ui.cepc.as.clear_filters", "फ़िल्टर साफ़ करें");
        m.put("ui.cepc.as.complaint_id", "शिकायत आईडी");
        m.put("ui.cepc.as.complainant_email", "शिकायतकर्ता का ईमेल");
        m.put("ui.cepc.as.complainant_mobile", "शिकायतकर्ता का मोबाइल नंबर");
        m.put("ui.cepc.as.complaint_main_category", "शिकायत की मुख्य श्रेणी");
        m.put("ui.cepc.as.from_email_id", "प्रेषक ईमेल आईडी");
        m.put("ui.cepc.as.no_contact_person", "नोडल अधिकारी / संपर्क व्यक्ति");
        m.put("ui.cepc.as.reported_on", "रिपोर्ट की तिथि");
        m.put("ui.cepc.as.select_a_bank", "बैंक चुनें");

        m.put("ui.cepc.sla.title", "सीईपीसी - एसएलए अनुपालन डैशबोर्ड");
        m.put("ui.cepc.sla.subtitle", "सेवा स्तर करार निगरानी");
        m.put("ui.cepc.sla.total_active", "कुल सक्रिय");
        m.put("ui.cepc.sla.breached", "भंग");
        m.put("ui.cepc.sla.breached_caps", "भंग");
        m.put("ui.cepc.sla.at_risk", "जोखिम में");
        m.put("ui.cepc.sla.on_track", "सही दिशा में");
        m.put("ui.cepc.sla.all_clear", "सब ठीक");
        m.put("ui.cepc.sla.attention_required", "ध्यान आवश्यक");
        m.put("ui.cepc.sla.overall_compliance", "समग्र अनुपालन");
        m.put("ui.cepc.sla.compliance", "एसएलए अनुपालन");
        m.put("ui.cepc.sla.status", "एसएलए स्थिति");
        m.put("ui.cepc.sla.complaint_no", "शिकायत सं.");
        m.put("ui.cepc.sla.officer", "अधिकारी");
        m.put("ui.cepc.sla.days", "दिन");
        m.put("ui.cepc.sla.view", "देखें");
        m.put("ui.cepc.sla.loading", "एसएलए डेटा लोड हो रहा है...");
        m.put("ui.cepc.sla.empty", "कोई भंग या जोखिम वाली शिकायत नहीं।");

        m.put("ui.cepc.conc.proceedings", "सुलह कार्यवाही");
        m.put("ui.cepc.conc.schedule_hearing", "सुलह सुनवाई निर्धारित करें");
        m.put("ui.cepc.conc.record_outcome", "सुलह का परिणाम दर्ज करें");
        m.put("ui.cepc.conc.parties_involved", "संबंधित पक्ष");
        m.put("ui.cepc.conc.venue", "स्थान");
        m.put("ui.cepc.conc.notes", "टिप्पणियाँ");
        m.put("ui.cepc.conc.date_required", "तिथि *");
        m.put("ui.cepc.conc.time_required", "समय *");
        m.put("ui.cepc.conc.outcome_required", "परिणाम *");
        m.put("ui.cepc.conc.summary_notes_required", "सारांश टिप्पणियाँ *");
        m.put("ui.cepc.conc.amount_in_inr", "राशि (रुपये में)");
        m.put("ui.cepc.conc.compensation_amount", "प्रतिकर राशि (यदि लागू हो)");
        m.put("ui.cepc.conc.settled", "निपटाया गया");
        m.put("ui.cepc.conc.failed", "असफल");
        m.put("ui.cepc.conc.status_prefix", "स्थिति:");
        m.put("ui.cepc.conc.uploading_minutes", "कार्यवृत्त अपलोड हो रहा है...");
        m.put("ui.cepc.conc.minutes_uploaded", "कार्यवृत्त सफलतापूर्वक अपलोड हुआ।");
        m.put("ui.cepc.conc.ph.venue", "बैठक स्थल या वीडियो लिंक");
        m.put("ui.cepc.conc.ph.outcome_summary", "सुलह परिणाम का सारांश...");
        m.put("ui.cepc.conc.ph.additional_instructions", "अतिरिक्त निर्देश...");

        m.put("ui.cepc.conc.meeting_status", "बैठक की स्थिति");
        m.put("ui.cepc.conc.meeting_comments", "बैठक की टिप्पणियाँ");
        m.put("ui.cepc.conc.meeting_cancelled", "बैठक रद्द");
        m.put("ui.cepc.conc.meeting_rescheduled", "बैठक पुनर्निर्धारित");
        m.put("ui.cepc.conc.meeting_marked_completed", "बैठक पूर्ण चिह्नित");
        m.put("ui.cepc.conc.accepted_by_complainant", "शिकायतकर्ता द्वारा स्वीकृत");
        m.put("ui.cepc.conc.accepted_by_entity", "विनियमित संस्था द्वारा स्वीकृत");
        m.put("ui.cepc.conc.via_video_conference", "वीडियो कॉन्फ्रेंस के माध्यम से संचालित");
        m.put("ui.cepc.conc.want_other_entities", "अन्य संस्थाएँ जोड़ना चाहते हैं");
        m.put("ui.cepc.conc.what_was_discussed", "बैठक में क्या चर्चा हुई और क्या सहमति बनी");
        m.put("ui.cepc.conc.details_saved", "सुलह का विवरण सहेजा गया।");
        m.put("ui.cepc.conc.loading_details", "सुलह का विवरण लोड हो रहा है…");
        m.put("ui.cepc.conc.new_meeting_warning",
                "इसे सहेजने पर एक नई बैठक आरंभ होगी। वर्तमान बैठक नीचे इतिहास में अपरिवर्तित रहेगी।");

        m.put("ui.cepc.lc.cases", "विधिक मामले");
        m.put("ui.cepc.lc.update_details", "विवरण अद्यतन करें");
        m.put("ui.cepc.lc.update_details_title", "विधिक मामला — विवरण अद्यतन करें");
        m.put("ui.cepc.lc.case_no_year", "मामला सं./डब्ल्यूपी सं. एवं वर्ष");
        m.put("ui.cepc.lc.court_name", "न्यायालय का नाम");
        m.put("ui.cepc.lc.advocate_name", "अधिवक्ता का नाम");
        m.put("ui.cepc.lc.assistant_legal_advisor", "सहायक विधि सलाहकार");
        m.put("ui.cepc.lc.parties_of_case", "मामले के पक्षकार");
        m.put("ui.cepc.lc.region_of_legal_team", "संबंधित विधि दल का क्षेत्र");
        m.put("ui.cepc.lc.rbi_first_respondent", "क्या आरबीआई/ओबीओ प्रथम प्रतिवादी है");
        m.put("ui.cepc.lc.appearance_required", "सुनवाई में उपस्थिति आवश्यक");
        m.put("ui.cepc.lc.next_hearing_date", "अगली सुनवाई की तिथि");
        m.put("ui.cepc.lc.action_taken_so_far", "अब तक की गई कार्रवाई");
        m.put("ui.cepc.lc.action_to_be_taken", "की जाने वाली कार्रवाई");
        m.put("ui.cepc.lc.brief_particulars",
                "मामले का संक्षिप्त विवरण/विषय-वस्तु तथा मांगी गई राहत का स्वरूप");
        m.put("ui.cepc.lc.monetary_claim_details",
                "आरबीआई के विरुद्ध मौद्रिक दावे का विवरण तथा उसके लिए किए गए प्रावधान");
        m.put("ui.cepc.lc.remarks_present_status", "टिप्पणी/मामले की वर्तमान स्थिति");
        m.put("ui.cepc.lc.loading", "लोड हो रहा है...");

        m.put("ui.cepc.panel.attach_new_document", "नया दस्तावेज़ संलग्न करें");
        m.put("ui.cepc.panel.download_all", "सभी डाउनलोड करें");
        m.put("ui.cepc.panel.no_attachments", "कोई अनुलग्नक नहीं");
        m.put("ui.cepc.panel.view", "देखें");
        m.put("ui.cepc.panel.no_comments", "कोई टिप्पणी नहीं");
        m.put("ui.cepc.panel.modified_by", "संशोधनकर्ता");
        m.put("ui.cepc.panel.modified_on", "संशोधन तिथि");
        m.put("ui.cepc.panel.loading_history", "इतिहास लोड हो रहा है...");
        m.put("ui.cepc.panel.no_history_for_complaint", "इस शिकायत के लिए कोई इतिहास उपलब्ध नहीं।");
        m.put("ui.cepc.panel.audit_trail", "अंकेक्षण अभिलेख");
        m.put("ui.cepc.panel.loading_timeline", "समयरेखा लोड हो रही है...");
        m.put("ui.cepc.panel.audit_trail_failed", "अंकेक्षण अभिलेख लोड नहीं हो सका।");
        m.put("ui.cepc.panel.no_past_complaints", "कोई पूर्व शिकायत नहीं मिली");
        m.put("ui.cepc.panel.no_documents_attached", "कोई दस्तावेज़ संलग्न नहीं");
        m.put("ui.cepc.panel.documents", "दस्तावेज़");
        m.put("ui.cepc.panel.reference", "संदर्भ");
        m.put("ui.cepc.panel.complaint_id_prefix", "शिकायत आईडी:");
        m.put("ui.cepc.panel.entity_prefix", "संस्था:");
        m.put("ui.cepc.panel.modified_date_prefix", "संशोधन तिथि:");
        m.put("ui.cepc.panel.processing_office_prefix", "प्रसंस्करण कार्यालय:");

        m.put("ui.cepc.fw.forward_to", "किसे अग्रेषित करें");
        m.put("ui.cepc.fw.transfer_office", "स्थानांतरण कार्यालय");
        m.put("ui.cepc.fw.other_office", "अन्य कार्यालय");
        m.put("ui.cepc.fw.other_rbi_department", "अन्य आरबीआई विभाग");
        m.put("ui.cepc.fw.other_regulatory_bodies", "अन्य विनियामक संस्थाएँ");
        m.put("ui.cepc.fw.name_of_department", "विभाग का नाम");
        m.put("ui.cepc.fw.name_of_regulator", "विनियामक का नाम");
        m.put("ui.cepc.fw.email_of_department", "विभाग का ईमेल आईडी");
        m.put("ui.cepc.fw.email_of_regulator", "विनियामक का ईमेल आईडी");
        m.put("ui.cepc.fw.reason_for_transfer", "स्थानांतरण का कारण");
        m.put("ui.cepc.fw.sent_from_office_comments", "भेजने वाले कार्यालय की टिप्पणियाँ");
        m.put("ui.cepc.fw.ph.enter_transfer_reason", "इस स्थानांतरण का कारण दर्ज करें");
        m.put("ui.cepc.fw.ph.enter_sending_office_comments", "भेजने वाले कार्यालय की टिप्पणियाँ दर्ज करें");
        m.put("ui.cepc.fw.no_department_match", "उस नाम से कोई विभाग मेल नहीं खाता");
        m.put("ui.cepc.fw.no_regulator_match", "उस नाम से कोई विनियामक मेल नहीं खाता");

        m.put("ui.cepc.value.non_maintainable_spaced", "अविचारणीय");
        m.put("ui.cepc.value.non_maintanable_typo", "अविचारणीय");

        m.put("ui.cepc.msg.confirm_close", "क्या आप वाकई शिकायत बंद करना चाहते हैं?");
        m.put("ui.cepc.msg.confirm_forward", "क्या आप वाकई शिकायत अग्रेषित करना चाहते हैं?");
        m.put("ui.cepc.msg.confirm_send_to_crpc_deo",
                "क्या आप वाकई अनुरोध सीआरपीसी कार्यकारी अधिकारी को भेजना चाहते हैं?");
        m.put("ui.cepc.msg.confirm_add_and_send",
                "क्या आप {{name}} को जोड़कर कार्यकारी अधिकारी को भेजना चाहते हैं?");
        m.put("ui.cepc.msg.meeting_scheduled_for", "बैठक {{date}} को {{time}} बजे निर्धारित।");
        m.put("ui.cepc.msg.meeting_rescheduled_for", "बैठक {{date}} को {{time}} बजे पुनर्निर्धारित।");
        m.put("ui.cepc.msg.meeting_cancelled", "बैठक रद्द कर दी गई।");
        m.put("ui.cepc.msg.meeting_marked_completed", "बैठक पूर्ण चिह्नित की गई।");

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
