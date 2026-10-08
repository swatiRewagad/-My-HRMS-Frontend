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
 * The CEPC complaint detail view's vocabulary — every static label on the screen, in English and Hindi.
 *
 * <p>Seeded so {@code GET /api/v1/i18n/translations/{locale}} is self-sufficient: the English a reader sees
 * comes from the same table as the Hindi, in every environment, rather than from whatever literals happened
 * to be compiled into that build of the frontend. The seeder is insert-if-absent and unconditional, so a
 * fresh database and a long-lived one converge on the same vocabulary at the next startup.
 *
 * <p><b>Whole sentences, not fragments.</b> The template builds several messages by concatenating pieces
 * around an interpolated value — {@code "Are you sure you want to" + action + "the request ?"}. Those are
 * seeded here as ONE key with a {@code {{...}}} placeholder, because Hindi puts the verb last: translating
 * the fragments separately and re-concatenating them in the template produces word salad. The wiring step
 * has to replace the concatenation with the single key, not translate each piece in place.
 *
 * <p>A NEW seeder at {@code @Order(73)} rather than an edit to {@link CepcDashboardI18nSeeder} (72), per the
 * convention here: a shared file is a guaranteed conflict where a conflict silently costs a locale. 60-72
 * were verified taken before choosing 73.
 *
 * @see CepcDashboardI18nSeeder the dashboard's half of the same vocabulary
 */
@Component
@Order(73)
public class CepcDetailViewI18nSeeder implements CommandLineRunner {

    private static final String MODULE = "cepc";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public CepcDetailViewI18nSeeder(TranslationKeyRepository keyRepo,
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

        // ═══ Section and panel headings ═══
        m.put("ui.cepc.dv.section.basic_details", "Basic Details");
        m.put("ui.cepc.dv.section.basic_identification", "Basic Identification");
        m.put("ui.cepc.dv.section.key_information", "Key Information");
        m.put("ui.cepc.dv.section.complainant_details", "Complainant Details");
        m.put("ui.cepc.dv.section.entity_details", "Entity Details");
        m.put("ui.cepc.dv.section.complaint_details", "Complaint Details");
        m.put("ui.cepc.dv.section.complaint_classification", "Complaint Classification");
        m.put("ui.cepc.dv.section.complaint_linkage", "Complaint Linkage");
        m.put("ui.cepc.dv.section.reminder_financial", "Reminder & Financial Details");
        m.put("ui.cepc.dv.section.flags_indicators", "Flags & Indicators");
        m.put("ui.cepc.dv.section.legal_case_details", "Legal & Case Details");
        m.put("ui.cepc.dv.section.document_details", "Document Details");
        m.put("ui.cepc.dv.section.declaration", "Declaration");
        m.put("ui.cepc.dv.section.assignment", "Assignment");
        m.put("ui.cepc.dv.section.summary", "Summary");
        m.put("ui.cepc.dv.section.settings", "Settings");
        m.put("ui.cepc.dv.section.confirmation", "Confirmation");

        // ═══ Tabs and rail ═══
        m.put("ui.cepc.dv.tab.complaint", "Complaint");
        m.put("ui.cepc.dv.tab.eligibility", "Eligibility");
        m.put("ui.cepc.dv.tab.assessment", "Assessment");
        m.put("ui.cepc.dv.tab.conciliation", "Conciliation");
        m.put("ui.cepc.dv.tab.final_decision", "Final Decision");
        m.put("ui.cepc.dv.tab.contact_entity", "Contact Entity");
        m.put("ui.cepc.dv.tab.legal_case", "Legal Case");
        m.put("ui.cepc.dv.tab.attachments", "Attachments");
        m.put("ui.cepc.dv.tab.history", "History");
        m.put("ui.cepc.dv.tab.past_complaints", "Past Complaints");
        m.put("ui.cepc.dv.tab.comments", "Comments");
        m.put("ui.cepc.dv.tab.all_comments", "All Comments");
        m.put("ui.cepc.dv.tab.email_communication", "Email Communication");
        m.put("ui.cepc.dv.tab.open_activities", "Open Activities");
        m.put("ui.cepc.dv.tab.closed_activities", "Closed Activities");

        // ═══ Field labels — identification ═══
        m.put("ui.cepc.dv.field.complaint_id", "Complaint ID");
        m.put("ui.cepc.dv.field.complaint_number", "Complaint Number");
        m.put("ui.cepc.dv.field.current_complaint_number", "Current Complaint Number");
        m.put("ui.cepc.dv.field.record_number", "Record Number");
        m.put("ui.cepc.dv.field.cpgram_number", "CPGRAM Number");
        m.put("ui.cepc.dv.field.module_name", "Module Name");
        m.put("ui.cepc.dv.field.status_code", "Status Code");
        m.put("ui.cepc.dv.field.complaint_status_on_portal", "Complaint Status on Portal");
        m.put("ui.cepc.dv.field.mode_of_receipt", "Mode of Receipt");
        m.put("ui.cepc.dv.field.receipt_date", "Receipt Date");
        m.put("ui.cepc.dv.field.date_of_filing", "Date of Filing the Complaint");
        m.put("ui.cepc.dv.field.date_of_receipt", "Date of Receipt");
        m.put("ui.cepc.dv.field.bank_complaint_receipt_date", "Bank Complaint Receipt Date");
        m.put("ui.cepc.dv.field.complaint_reopened_date", "Complaint Reopened Date");
        m.put("ui.cepc.dv.field.registration_date_valid", "Complaint Registration Date Valid");
        m.put("ui.cepc.dv.field.date_of_registration_rbi", "Date of Registration with RBI");
        m.put("ui.cepc.dv.field.created_by", "Created By");
        m.put("ui.cepc.dv.field.sla_breach_in", "SLA Breach In");
        m.put("ui.cepc.dv.field.status", "Status");
        m.put("ui.cepc.dv.field.assigned_to", "Assigned To");

        // ═══ Field labels — complainant ═══
        m.put("ui.cepc.dv.field.complainant_name", "Complainant Name");
        m.put("ui.cepc.dv.field.name", "Name");
        m.put("ui.cepc.dv.field.email", "Email");
        m.put("ui.cepc.dv.field.email_id", "Email ID");
        m.put("ui.cepc.dv.field.mobile", "Mobile");
        m.put("ui.cepc.dv.field.address", "Address");
        m.put("ui.cepc.dv.field.city", "City");
        m.put("ui.cepc.dv.field.district", "District");
        m.put("ui.cepc.dv.field.state", "State");
        m.put("ui.cepc.dv.field.country", "Country");
        m.put("ui.cepc.dv.field.pincode", "Pincode");
        m.put("ui.cepc.dv.field.designation", "Designation");

        // ═══ Field labels — entity ═══
        m.put("ui.cepc.dv.field.entity_name", "Entity Name");
        m.put("ui.cepc.dv.field.entity_type", "Entity Type");
        m.put("ui.cepc.dv.field.entity_category", "Entity Category");
        m.put("ui.cepc.dv.field.entity_address", "Entity Address");
        m.put("ui.cepc.dv.field.entity_branch_name", "Entity Branch Name");
        m.put("ui.cepc.dv.field.entity_branch_category", "Entity Branch Category");
        m.put("ui.cepc.dv.field.bank_name", "Bank Name");
        m.put("ui.cepc.dv.field.bank_category", "Bank Category");
        m.put("ui.cepc.dv.field.bank_branch_name", "Bank Branch Name");
        m.put("ui.cepc.dv.field.bank_branch_category", "Bank Branch Category");
        m.put("ui.cepc.dv.field.other_entity_name", "Other Entity Name");
        m.put("ui.cepc.dv.field.bsr_code", "BSR Code");
        m.put("ui.cepc.dv.field.cosmos_code", "COSMOS Code");
        m.put("ui.cepc.dv.field.asset_size_crores", "Asset Size in Crores");
        m.put("ui.cepc.dv.field.rbo_cgpc_old", "RBO CGPC (Old)");

        // ═══ Field labels — classification ═══
        m.put("ui.cepc.dv.field.complaint_category", "Complaint Category");
        m.put("ui.cepc.dv.field.sub_category", "Sub-category");
        m.put("ui.cepc.dv.field.sub_category_2", "Sub-category 2");
        m.put("ui.cepc.dv.field.complaint_sub_category_1", "Complaint Sub-Category 1");
        m.put("ui.cepc.dv.field.complaint_sub_category_2", "Complaint Sub-Category 2");
        m.put("ui.cepc.dv.field.proposed_complaint_type", "Proposed Complaint Type");
        m.put("ui.cepc.dv.field.subject", "Subject");
        m.put("ui.cepc.dv.field.gist_of_case", "Gist of the Case");
        m.put("ui.cepc.dv.field.gist_of_case_regional", "Gist of the Case in regional language");
        m.put("ui.cepc.dv.field.vernacular_language", "Vernacular Language");

        // ═══ Field labels — financial and reminders ═══
        m.put("ui.cepc.dv.field.dispute_amount", "Dispute Amount");
        m.put("ui.cepc.dv.field.dispute_amount_involved", "Dispute Amount Involved");
        m.put("ui.cepc.dv.field.disputed_amount_involved", "Disputed Amount Involved");
        m.put("ui.cepc.dv.field.compensation_loss", "Compensation (Loss)");
        m.put("ui.cepc.dv.field.compensation_mental_harassment", "Compensation (Mental Harassment)");
        m.put("ui.cepc.dv.field.compensation_due_to_loss", "Compensation Due to Loss");
        m.put("ui.cepc.dv.field.compensation_due_to_mental_harassment", "Compensation Due to Mental Harassment");
        m.put("ui.cepc.dv.field.compensation_sought",
                "Compensation Sought for Expenses Incurred, Harassment and Mental Anguish (if any)");
        m.put("ui.cepc.dv.field.reminder_sent_by_complainant", "Whether any Reminder was sent by the Complainant");
        m.put("ui.cepc.dv.field.reply_within_30_days",
                "Received any Reply within 30 Days (or within time stipulated by RBI / NPCI / Card Network)");

        // ═══ Field labels — flags ═══
        m.put("ui.cepc.dv.field.high_priority_complaint", "High Priority Complaint");
        m.put("ui.cepc.dv.field.systemic_issue", "Systemic Issue?");
        m.put("ui.cepc.dv.field.grounds_flag", "Grounds Flag");
        m.put("ui.cepc.dv.field.scheme_flag", "Scheme Flag");
        m.put("ui.cepc.dv.field.against_business_correspondent", "Is Complaint Against a Business Correspondent");
        m.put("ui.cepc.dv.field.is_cpgram", "Is Complaint CPGRAM");
        m.put("ui.cepc.dv.field.free_marked_complaint", "Is Complaint Pertaining to a Free Marked Complaint");
        m.put("ui.cepc.dv.field.regarding_pension", "Is Complaint Regarding Pension");
        m.put("ui.cepc.dv.field.legal_case_filed", "Whether Legal Case is Filed");

        // ═══ Field labels — workflow, award and advisory ═══
        m.put("ui.cepc.dv.field.crpc_proposed_action", "CRPC Proposed Action");
        m.put("ui.cepc.dv.field.crpc_proposed_clause", "CRPC Proposed Clause");
        m.put("ui.cepc.dv.field.expected_status_change", "Expected Status Change");
        m.put("ui.cepc.dv.field.sub_action", "Sub-Action");
        m.put("ui.cepc.dv.field.select_sub_action", "Select Sub-Action");
        m.put("ui.cepc.dv.field.action", "Action");
        m.put("ui.cepc.dv.field.advisory_compliance_date", "Advisory Compliance Date");
        m.put("ui.cepc.dv.field.date_advisory_complied", "Date by Which Advisory to be Complied");
        m.put("ui.cepc.dv.field.date_nodal_officer_comply", "Date by Which Nodal Officer Should Comply");
        m.put("ui.cepc.dv.field.date_award_accepted", "Date of Acceptance of Award by the Complainant");
        m.put("ui.cepc.dv.field.date_award_implemented", "Date of Implementation of Award by the Bank");
        m.put("ui.cepc.dv.field.speaking_order_contents", "Contents of the Speaking Order");
        m.put("ui.cepc.dv.field.reason_for_closure", "Reason for Closure");
        m.put("ui.cepc.dv.field.reason_for_reopen", "Reason for Reopen");
        m.put("ui.cepc.dv.field.reason_remarks", "Reason / Remarks");
        m.put("ui.cepc.dv.field.remarks", "Remarks");
        m.put("ui.cepc.dv.field.additional_comments", "Additional Comments");
        m.put("ui.cepc.dv.field.additional_information", "Additional Information");
        m.put("ui.cepc.dv.field.comment", "Comment");
        m.put("ui.cepc.dv.field.comment_to_no", "Comment to NO");
        m.put("ui.cepc.dv.field.comment_to_pno", "Comment to PNO");
        m.put("ui.cepc.dv.field.comments_from_other_officers", "Comments From Other Officers");

        // ═══ Field labels — offices and officers ═══
        m.put("ui.cepc.dv.field.designated_office", "Designated Office");
        m.put("ui.cepc.dv.field.processing_office", "Processing Office");
        m.put("ui.cepc.dv.field.dealing_official", "Dealing Official");
        m.put("ui.cepc.dv.field.cepc_dealing_officer", "CEPC Dealing Officer");
        m.put("ui.cepc.dv.field.cepc_dealing_officer_name", "CEPC Dealing Officer Name");
        m.put("ui.cepc.dv.field.crpc_full", "Centralised Receipt and Processing Centre(CRPC)");
        m.put("ui.cepc.dv.field.transfer_from_office", "Transfer From Office");
        m.put("ui.cepc.dv.field.transfer_target_office", "Transfer Target Office");
        m.put("ui.cepc.dv.field.transfer_request_summary", "Transfer Request Summary");
        m.put("ui.cepc.dv.field.reassign_territory", "Reassign Territory");

        // ═══ Nodal and contact person ═══
        m.put("ui.cepc.dv.field.no_name", "NO Name");
        m.put("ui.cepc.dv.field.no_email", "NO Email");
        m.put("ui.cepc.dv.field.no_mobile", "NO Mobile");
        m.put("ui.cepc.dv.field.no_record_number", "NO Record Number");
        m.put("ui.cepc.dv.field.pno_name", "PNO Name");
        m.put("ui.cepc.dv.field.pno_email", "PNO Email");
        m.put("ui.cepc.dv.field.pno_mobile", "PNO Mobile");
        m.put("ui.cepc.dv.field.contact_persons", "Contact Persons");
        m.put("ui.cepc.dv.field.how_contact_recorded", "How this contact came to be recorded");
        m.put("ui.cepc.dv.contact.blurb",
                "People at the regulated entity this office is dealing with on this complaint.");

        // ═══ Email composer ═══
        m.put("ui.cepc.dv.email.to", "To");
        m.put("ui.cepc.dv.email.from", "From");
        m.put("ui.cepc.dv.email.cc", "CC");
        m.put("ui.cepc.dv.email.bcc", "BCC");
        m.put("ui.cepc.dv.email.add_cc", "Add CC recipients");
        m.put("ui.cepc.dv.email.add_bcc", "Add BCC recipients");
        m.put("ui.cepc.dv.email.create_new", "Create New Email");
        m.put("ui.cepc.dv.email.drafts", "Draft Emails");
        m.put("ui.cepc.dv.email.edit_draft", "Edit Draft");
        m.put("ui.cepc.dv.email.save_draft", "Save Draft");
        m.put("ui.cepc.dv.email.complaint_copy", "Complaint Copy");
        m.put("ui.cepc.dv.email.select_activity",
                "Select an activity to view email or click \"Create New Email\"");
        m.put("ui.cepc.dv.email.send_failed", "This email could not be sent.");

        // ═══ Buttons and actions ═══
        m.put("ui.cepc.dv.action.save", "Save");
        m.put("ui.cepc.dv.action.update", "Update");
        m.put("ui.cepc.dv.action.cancel", "Cancel");
        m.put("ui.cepc.dv.action.close", "Close");
        m.put("ui.cepc.dv.action.edit", "Edit");
        m.put("ui.cepc.dv.action.remove", "Remove");
        m.put("ui.cepc.dv.action.search", "Search");
        m.put("ui.cepc.dv.action.select", "Select");
        m.put("ui.cepc.dv.action.select_a_value", "Select a Value");
        m.put("ui.cepc.dv.action.download", "Download");
        m.put("ui.cepc.dv.action.preview", "Preview");
        m.put("ui.cepc.dv.action.preview_confirm", "Preview and Confirm");
        m.put("ui.cepc.dv.action.post", "Post");
        m.put("ui.cepc.dv.action.reply", "Reply");
        m.put("ui.cepc.dv.action.retry", "Retry");
        m.put("ui.cepc.dv.action.refresh", "Refresh");
        m.put("ui.cepc.dv.action.bookmark", "Bookmark");
        m.put("ui.cepc.dv.action.full_screen", "Full Screen");
        m.put("ui.cepc.dv.action.read_more", "...read more");
        m.put("ui.cepc.dv.action.show_less", "show less");
        m.put("ui.cepc.dv.action.back_to_home", "Back to Home");
        m.put("ui.cepc.dv.action.back_to_complaint", "Back to complaint");
        m.put("ui.cepc.dv.action.forward", "Forward");
        m.put("ui.cepc.dv.action.refer_back", "Refer Back");
        m.put("ui.cepc.dv.action.accept", "Accept");
        m.put("ui.cepc.dv.action.partial_accept", "Partial Accept");
        m.put("ui.cepc.dv.action.reject", "Reject");
        m.put("ui.cepc.dv.action.approve", "Approve");
        m.put("ui.cepc.dv.action.settle", "Settle");
        m.put("ui.cepc.dv.action.withdraw", "Withdraw");
        m.put("ui.cepc.dv.action.send_for_approval", "Send for Approval");
        m.put("ui.cepc.dv.action.mark_for_closure", "Mark for Closure");
        m.put("ui.cepc.dv.action.close_complaint", "Close Complaint");
        m.put("ui.cepc.dv.action.reopen_complaint", "Reopen Complaint");
        m.put("ui.cepc.dv.action.mark_all_eligible", "Mark all as eligible");
        m.put("ui.cepc.dv.action.add_contact_person", "Add Contact Person");
        m.put("ui.cepc.dv.action.add_another_contact_person", "Add Another Contact Person");
        m.put("ui.cepc.dv.action.edit_contact_person", "Edit this contact person");

        // ═══ Placeholders ═══
        m.put("ui.cepc.dv.ph.enter_text", "Enter text here");
        m.put("ui.cepc.dv.ph.enter_amount", "Enter Amount");
        m.put("ui.cepc.dv.ph.enter_subject", "Enter subject");
        m.put("ui.cepc.dv.ph.enter_reason", "Enter reason");
        m.put("ui.cepc.dv.ph.enter_email", "Enter email");
        m.put("ui.cepc.dv.ph.enter_your_email", "Enter your email here");
        m.put("ui.cepc.dv.ph.enter_recipient_email", "Enter recipient email");
        m.put("ui.cepc.dv.ph.enter_complaint_number", "Enter complaint number");
        m.put("ui.cepc.dv.ph.enter_other_entity_name", "Enter other entity name");
        m.put("ui.cepc.dv.ph.enter_grounds_flag", "Enter grounds flag");
        m.put("ui.cepc.dv.ph.enter_scheme_flag", "Enter scheme flag");
        m.put("ui.cepc.dv.ph.enter_rbo_cgpc", "Enter RBO CGPC");
        m.put("ui.cepc.dv.ph.post_your_comment", "Post your comment here");
        m.put("ui.cepc.dv.ph.contact_person_name", "Contact person's name");
        m.put("ui.cepc.dv.ph.mobile_10_digit", "10-digit mobile number");
        m.put("ui.cepc.dv.ph.eg_branch_manager", "e.g. Branch Manager");
        m.put("ui.cepc.dv.ph.eg_loan", "e.g. Loan");
        m.put("ui.cepc.dv.ph.select_an_office", "Select an office");
        m.put("ui.cepc.dv.ph.select_language", "Select language");
        m.put("ui.cepc.dv.ph.search_deo", "Search DEO by name, email or office");

        // ═══ Empty and loading states ═══
        m.put("ui.cepc.dv.empty.no_comments", "No comments yet.");
        m.put("ui.cepc.dv.empty.no_history", "No history available.");
        m.put("ui.cepc.dv.empty.no_attachments", "No attachments available.");
        m.put("ui.cepc.dv.empty.no_attachments_for_complaint", "No attachments available for this complaint.");
        m.put("ui.cepc.dv.empty.no_document_details", "No document details available.");
        m.put("ui.cepc.dv.empty.no_document_uploaded", "No document uploaded");
        m.put("ui.cepc.dv.empty.no_nodal_records", "No Nodal Officer records found");
        m.put("ui.cepc.dv.empty.no_contact_person",
                "No contact person recorded yet. Add one from the Contact Entity tab.");
        m.put("ui.cepc.dv.empty.no_entity_match", "No entity matches that name");
        m.put("ui.cepc.dv.empty.no_deo_match", "No DEO matches that search.");
        m.put("ui.cepc.dv.empty.no_additional_settings", "No additional settings available.");
        m.put("ui.cepc.dv.loading.contact_persons", "Loading contact persons…");
        m.put("ui.cepc.dv.loading.conversation", "Loading conversation…");
        m.put("ui.cepc.dv.loading.nodal_records", "Loading Nodal Officer records…");
        m.put("ui.cepc.dv.loading.roster", "Loading roster...");

        // ═══ Whole-sentence messages ═══
        // Assembled in the template today from fragments around an interpolated value. Seeded as one
        // sentence each because Hindi is verb-final: "Are you sure you want to" + verb + "the request ?"
        // cannot be reassembled in Hindi word order from its English pieces.
        m.put("ui.cepc.dv.msg.confirm_action", "Are you sure you want to {{action}} the request?");
        m.put("ui.cepc.dv.msg.confirm_mark_for_closure",
                "Are you sure you want to mark the complaint for closure?");
        m.put("ui.cepc.dv.msg.confirm_reopen", "Are you sure you want to reopen the complaint?");
        m.put("ui.cepc.dv.msg.closed_successfully", "Complaint {{number}} has been closed successfully.");
        m.put("ui.cepc.dv.msg.routed_to", "Complaint {{number}} has been routed to {{target}}.");
        m.put("ui.cepc.dv.msg.transferred_to", "Complaint {{number}} has been transferred to {{target}}.");
        m.put("ui.cepc.dv.msg.already_sent_for_approval",
                "Complaint {{number}} has already been sent for approval and is in {{status}} mode.");
        m.put("ui.cepc.dv.msg.pending_crpc_head", "{{status}}, pending CRPC Head approval.");
        m.put("ui.cepc.dv.msg.conciliation_other_officer",
                "This complaint is assigned to another officer, so its conciliation cannot be edited here.");
        m.put("ui.cepc.dv.msg.contact_needs_name_and_one",
                "Enter a name and at least one of email or mobile.");
        m.put("ui.cepc.dv.msg.why_reopen", "Why is this complaint being reopened?");
        m.put("ui.cepc.dv.msg.change_territory", "Do you want to change the Territory?");
        m.put("ui.cepc.dv.msg.declaration",
                "All data/information entered by me in the system is correct as per my understanding");

        // ═══ Status and state words used as display values on this screen ═══
        m.put("ui.cepc.dv.state.new_complaint", "New Complaint");
        m.put("ui.cepc.dv.state.draft", "Draft");
        m.put("ui.cepc.dv.state.duplicate", "Duplicate");
        m.put("ui.cepc.dv.state.information_required", "Information Required");
        m.put("ui.cepc.dv.state.sent_back", "Sent Back");
        m.put("ui.cepc.dv.state.sent_for_approval", "Sent for Approval");
        m.put("ui.cepc.dv.state.assessment_sent_for_approval", "Assessment Sent for Approval");
        m.put("ui.cepc.dv.state.sent_to_dealing_officer", "Sent to Dealing Officer");
        m.put("ui.cepc.dv.state.sent_to_other_office", "Sent to Other Office");
        m.put("ui.cepc.dv.state.complaint_closed", "Complaint Closed");
        m.put("ui.cepc.dv.state.complaint_forwarded", "Complaint Forwarded");
        m.put("ui.cepc.dv.state.complaint_sent_back", "Complaint Sent Back");
        m.put("ui.cepc.dv.state.complaint_reopened", "Complaint Reopened");
        m.put("ui.cepc.dv.state.complaint_already_processed", "Complaint Already Processed");
        m.put("ui.cepc.dv.state.advisory_issued", "Advisory Issued");
        m.put("ui.cepc.dv.state.pre_enquiry_received", "Pre Enquiry Received");
        m.put("ui.cepc.dv.state.notice_13_1", "13-1 Notice");
        m.put("ui.cepc.dv.state.notice_13_1_issued", "13:1 Notice Issued");
        m.put("ui.cepc.dv.state.maintainable", "Maintainable");
        m.put("ui.cepc.dv.state.partially_maintainable", "Partially Maintainable");
        m.put("ui.cepc.dv.state.non_maintainable", "Non-Maintainable");
        m.put("ui.cepc.dv.state.not_a_complaint", "Not a Complaint");
        m.put("ui.cepc.dv.state.view_only", "View Only");
        m.put("ui.cepc.dv.state.inactive", "Inactive");
        m.put("ui.cepc.dv.state.on_leave", "On leave");
        m.put("ui.cepc.dv.state.at_threshold", "At threshold");
        m.put("ui.cepc.dv.state.atm_complaint", "ATM Complaint");

        // ═══ Short generic values ═══
        m.put("ui.cepc.dv.value.yes", "Yes");
        m.put("ui.cepc.dv.value.no", "No");
        m.put("ui.cepc.dv.value.optional", "(Optional)");
        m.put("ui.cepc.dv.value.not_applicable", "Not Applicable");
        m.put("ui.cepc.dv.value.automatic", "Automatic");
        m.put("ui.cepc.dv.value.manual", "Manual");
        m.put("ui.cepc.dv.value.atm_card", "ATM / Credit / Debit Card");
        m.put("ui.cepc.dv.value.loan_account", "Loan / Disposal Account");

        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("ui.cepc.dv.section.basic_details", "मूल विवरण");
        m.put("ui.cepc.dv.section.basic_identification", "मूल पहचान");
        m.put("ui.cepc.dv.section.key_information", "मुख्य जानकारी");
        m.put("ui.cepc.dv.section.complainant_details", "शिकायतकर्ता का विवरण");
        m.put("ui.cepc.dv.section.entity_details", "संस्था का विवरण");
        m.put("ui.cepc.dv.section.complaint_details", "शिकायत का विवरण");
        m.put("ui.cepc.dv.section.complaint_classification", "शिकायत वर्गीकरण");
        m.put("ui.cepc.dv.section.complaint_linkage", "शिकायत संबद्धता");
        m.put("ui.cepc.dv.section.reminder_financial", "अनुस्मारक एवं वित्तीय विवरण");
        m.put("ui.cepc.dv.section.flags_indicators", "ध्वज एवं संकेतक");
        m.put("ui.cepc.dv.section.legal_case_details", "विधिक एवं मामले का विवरण");
        m.put("ui.cepc.dv.section.document_details", "दस्तावेज़ विवरण");
        m.put("ui.cepc.dv.section.declaration", "घोषणा");
        m.put("ui.cepc.dv.section.assignment", "आवंटन");
        m.put("ui.cepc.dv.section.summary", "सारांश");
        m.put("ui.cepc.dv.section.settings", "सेटिंग्स");
        m.put("ui.cepc.dv.section.confirmation", "पुष्टि");

        m.put("ui.cepc.dv.tab.complaint", "शिकायत");
        m.put("ui.cepc.dv.tab.eligibility", "पात्रता");
        m.put("ui.cepc.dv.tab.assessment", "मूल्यांकन");
        m.put("ui.cepc.dv.tab.conciliation", "सुलह");
        m.put("ui.cepc.dv.tab.final_decision", "अंतिम निर्णय");
        m.put("ui.cepc.dv.tab.contact_entity", "संपर्क संस्था");
        m.put("ui.cepc.dv.tab.legal_case", "विधिक मामला");
        m.put("ui.cepc.dv.tab.attachments", "अनुलग्नक");
        m.put("ui.cepc.dv.tab.history", "इतिहास");
        m.put("ui.cepc.dv.tab.past_complaints", "पूर्व शिकायतें");
        m.put("ui.cepc.dv.tab.comments", "टिप्पणियाँ");
        m.put("ui.cepc.dv.tab.all_comments", "सभी टिप्पणियाँ");
        m.put("ui.cepc.dv.tab.email_communication", "ईमेल संचार");
        m.put("ui.cepc.dv.tab.open_activities", "खुली गतिविधियाँ");
        m.put("ui.cepc.dv.tab.closed_activities", "बंद गतिविधियाँ");

        m.put("ui.cepc.dv.field.complaint_id", "शिकायत आईडी");
        m.put("ui.cepc.dv.field.complaint_number", "शिकायत संख्या");
        m.put("ui.cepc.dv.field.current_complaint_number", "वर्तमान शिकायत संख्या");
        m.put("ui.cepc.dv.field.record_number", "रिकॉर्ड संख्या");
        m.put("ui.cepc.dv.field.cpgram_number", "सीपीग्राम संख्या");
        m.put("ui.cepc.dv.field.module_name", "मॉड्यूल का नाम");
        m.put("ui.cepc.dv.field.status_code", "स्थिति कोड");
        m.put("ui.cepc.dv.field.complaint_status_on_portal", "पोर्टल पर शिकायत की स्थिति");
        m.put("ui.cepc.dv.field.mode_of_receipt", "प्राप्ति का माध्यम");
        m.put("ui.cepc.dv.field.receipt_date", "प्राप्ति तिथि");
        m.put("ui.cepc.dv.field.date_of_filing", "शिकायत दर्ज करने की तिथि");
        m.put("ui.cepc.dv.field.date_of_receipt", "प्राप्ति की तिथि");
        m.put("ui.cepc.dv.field.bank_complaint_receipt_date", "बैंक शिकायत प्राप्ति तिथि");
        m.put("ui.cepc.dv.field.complaint_reopened_date", "शिकायत पुनः खोलने की तिथि");
        m.put("ui.cepc.dv.field.registration_date_valid", "शिकायत पंजीकरण तिथि वैध");
        m.put("ui.cepc.dv.field.date_of_registration_rbi", "भारतीय रिज़र्व बैंक में पंजीकरण की तिथि");
        m.put("ui.cepc.dv.field.created_by", "निर्माता");
        m.put("ui.cepc.dv.field.sla_breach_in", "एसएलए भंग होने में");
        m.put("ui.cepc.dv.field.status", "स्थिति");
        m.put("ui.cepc.dv.field.assigned_to", "सौंपा गया");

        m.put("ui.cepc.dv.field.complainant_name", "शिकायतकर्ता का नाम");
        m.put("ui.cepc.dv.field.name", "नाम");
        m.put("ui.cepc.dv.field.email", "ईमेल");
        m.put("ui.cepc.dv.field.email_id", "ईमेल आईडी");
        m.put("ui.cepc.dv.field.mobile", "मोबाइल");
        m.put("ui.cepc.dv.field.address", "पता");
        m.put("ui.cepc.dv.field.city", "शहर");
        m.put("ui.cepc.dv.field.district", "जिला");
        m.put("ui.cepc.dv.field.state", "राज्य");
        m.put("ui.cepc.dv.field.country", "देश");
        m.put("ui.cepc.dv.field.pincode", "पिन कोड");
        m.put("ui.cepc.dv.field.designation", "पदनाम");

        m.put("ui.cepc.dv.field.entity_name", "संस्था का नाम");
        m.put("ui.cepc.dv.field.entity_type", "संस्था का प्रकार");
        m.put("ui.cepc.dv.field.entity_category", "संस्था की श्रेणी");
        m.put("ui.cepc.dv.field.entity_address", "संस्था का पता");
        m.put("ui.cepc.dv.field.entity_branch_name", "संस्था की शाखा का नाम");
        m.put("ui.cepc.dv.field.entity_branch_category", "संस्था की शाखा की श्रेणी");
        m.put("ui.cepc.dv.field.bank_name", "बैंक का नाम");
        m.put("ui.cepc.dv.field.bank_category", "बैंक की श्रेणी");
        m.put("ui.cepc.dv.field.bank_branch_name", "बैंक शाखा का नाम");
        m.put("ui.cepc.dv.field.bank_branch_category", "बैंक शाखा की श्रेणी");
        m.put("ui.cepc.dv.field.other_entity_name", "अन्य संस्था का नाम");
        m.put("ui.cepc.dv.field.bsr_code", "बीएसआर कोड");
        m.put("ui.cepc.dv.field.cosmos_code", "कॉसमॉस कोड");
        m.put("ui.cepc.dv.field.asset_size_crores", "परिसंपत्ति आकार (करोड़ में)");
        m.put("ui.cepc.dv.field.rbo_cgpc_old", "आरबीओ सीजीपीसी (पुराना)");

        m.put("ui.cepc.dv.field.complaint_category", "शिकायत श्रेणी");
        m.put("ui.cepc.dv.field.sub_category", "उप-श्रेणी");
        m.put("ui.cepc.dv.field.sub_category_2", "उप-श्रेणी 2");
        m.put("ui.cepc.dv.field.complaint_sub_category_1", "शिकायत उप-श्रेणी 1");
        m.put("ui.cepc.dv.field.complaint_sub_category_2", "शिकायत उप-श्रेणी 2");
        m.put("ui.cepc.dv.field.proposed_complaint_type", "प्रस्तावित शिकायत प्रकार");
        m.put("ui.cepc.dv.field.subject", "विषय");
        m.put("ui.cepc.dv.field.gist_of_case", "मामले का सारांश");
        m.put("ui.cepc.dv.field.gist_of_case_regional", "क्षेत्रीय भाषा में मामले का सारांश");
        m.put("ui.cepc.dv.field.vernacular_language", "क्षेत्रीय भाषा");

        m.put("ui.cepc.dv.field.dispute_amount", "विवादित राशि");
        m.put("ui.cepc.dv.field.dispute_amount_involved", "संबंधित विवादित राशि");
        m.put("ui.cepc.dv.field.disputed_amount_involved", "संबंधित विवादित राशि");
        m.put("ui.cepc.dv.field.compensation_loss", "प्रतिकर (हानि)");
        m.put("ui.cepc.dv.field.compensation_mental_harassment", "प्रतिकर (मानसिक उत्पीड़न)");
        m.put("ui.cepc.dv.field.compensation_due_to_loss", "हानि के कारण प्रतिकर");
        m.put("ui.cepc.dv.field.compensation_due_to_mental_harassment", "मानसिक उत्पीड़न के कारण प्रतिकर");
        m.put("ui.cepc.dv.field.compensation_sought",
                "हुए व्यय, उत्पीड़न और मानसिक पीड़ा के लिए मांगा गया प्रतिकर (यदि कोई हो)");
        m.put("ui.cepc.dv.field.reminder_sent_by_complainant", "क्या शिकायतकर्ता द्वारा कोई अनुस्मारक भेजा गया");
        m.put("ui.cepc.dv.field.reply_within_30_days",
                "क्या 30 दिनों के भीतर (या आरबीआई / एनपीसीआई / कार्ड नेटवर्क द्वारा निर्धारित समय के भीतर) कोई उत्तर प्राप्त हुआ");

        m.put("ui.cepc.dv.field.high_priority_complaint", "उच्च प्राथमिकता वाली शिकायत");
        m.put("ui.cepc.dv.field.systemic_issue", "प्रणालीगत मुद्दा?");
        m.put("ui.cepc.dv.field.grounds_flag", "आधार ध्वज");
        m.put("ui.cepc.dv.field.scheme_flag", "योजना ध्वज");
        m.put("ui.cepc.dv.field.against_business_correspondent", "क्या शिकायत व्यवसाय प्रतिनिधि के विरुद्ध है");
        m.put("ui.cepc.dv.field.is_cpgram", "क्या शिकायत सीपीग्राम है");
        m.put("ui.cepc.dv.field.free_marked_complaint", "क्या शिकायत फ्री मार्क्ड शिकायत से संबंधित है");
        m.put("ui.cepc.dv.field.regarding_pension", "क्या शिकायत पेंशन से संबंधित है");
        m.put("ui.cepc.dv.field.legal_case_filed", "क्या विधिक मामला दायर किया गया है");

        m.put("ui.cepc.dv.field.crpc_proposed_action", "सीआरपीसी प्रस्तावित कार्रवाई");
        m.put("ui.cepc.dv.field.crpc_proposed_clause", "सीआरपीसी प्रस्तावित खंड");
        m.put("ui.cepc.dv.field.expected_status_change", "अपेक्षित स्थिति परिवर्तन");
        m.put("ui.cepc.dv.field.sub_action", "उप-कार्रवाई");
        m.put("ui.cepc.dv.field.select_sub_action", "उप-कार्रवाई चुनें");
        m.put("ui.cepc.dv.field.action", "कार्रवाई");
        m.put("ui.cepc.dv.field.advisory_compliance_date", "सलाह अनुपालन तिथि");
        m.put("ui.cepc.dv.field.date_advisory_complied", "जिस तिथि तक सलाह का अनुपालन किया जाना है");
        m.put("ui.cepc.dv.field.date_nodal_officer_comply", "जिस तिथि तक नोडल अधिकारी को अनुपालन करना है");
        m.put("ui.cepc.dv.field.date_award_accepted", "शिकायतकर्ता द्वारा अधिनिर्णय स्वीकार करने की तिथि");
        m.put("ui.cepc.dv.field.date_award_implemented", "बैंक द्वारा अधिनिर्णय लागू करने की तिथि");
        m.put("ui.cepc.dv.field.speaking_order_contents", "सकारण आदेश की विषय-वस्तु");
        m.put("ui.cepc.dv.field.reason_for_closure", "समापन का कारण");
        m.put("ui.cepc.dv.field.reason_for_reopen", "पुनः खोलने का कारण");
        m.put("ui.cepc.dv.field.reason_remarks", "कारण / टिप्पणी");
        m.put("ui.cepc.dv.field.remarks", "टिप्पणी");
        m.put("ui.cepc.dv.field.additional_comments", "अतिरिक्त टिप्पणियाँ");
        m.put("ui.cepc.dv.field.additional_information", "अतिरिक्त जानकारी");
        m.put("ui.cepc.dv.field.comment", "टिप्पणी");
        m.put("ui.cepc.dv.field.comment_to_no", "नोडल अधिकारी को टिप्पणी");
        m.put("ui.cepc.dv.field.comment_to_pno", "प्रधान नोडल अधिकारी को टिप्पणी");
        m.put("ui.cepc.dv.field.comments_from_other_officers", "अन्य अधिकारियों की टिप्पणियाँ");

        m.put("ui.cepc.dv.field.designated_office", "नामित कार्यालय");
        m.put("ui.cepc.dv.field.processing_office", "प्रसंस्करण कार्यालय");
        m.put("ui.cepc.dv.field.dealing_official", "कार्यकारी अधिकारी");
        m.put("ui.cepc.dv.field.cepc_dealing_officer", "सीईपीसी कार्यकारी अधिकारी");
        m.put("ui.cepc.dv.field.cepc_dealing_officer_name", "सीईपीसी कार्यकारी अधिकारी का नाम");
        m.put("ui.cepc.dv.field.crpc_full", "केंद्रीकृत प्राप्ति एवं प्रसंस्करण केंद्र (सीआरपीसी)");
        m.put("ui.cepc.dv.field.transfer_from_office", "स्थानांतरण करने वाला कार्यालय");
        m.put("ui.cepc.dv.field.transfer_target_office", "लक्षित कार्यालय");
        m.put("ui.cepc.dv.field.transfer_request_summary", "स्थानांतरण अनुरोध सारांश");
        m.put("ui.cepc.dv.field.reassign_territory", "क्षेत्र पुनः आवंटित करें");

        m.put("ui.cepc.dv.field.no_name", "नोडल अधिकारी का नाम");
        m.put("ui.cepc.dv.field.no_email", "नोडल अधिकारी का ईमेल");
        m.put("ui.cepc.dv.field.no_mobile", "नोडल अधिकारी का मोबाइल");
        m.put("ui.cepc.dv.field.no_record_number", "नोडल अधिकारी रिकॉर्ड संख्या");
        m.put("ui.cepc.dv.field.pno_name", "प्रधान नोडल अधिकारी का नाम");
        m.put("ui.cepc.dv.field.pno_email", "प्रधान नोडल अधिकारी का ईमेल");
        m.put("ui.cepc.dv.field.pno_mobile", "प्रधान नोडल अधिकारी का मोबाइल");
        m.put("ui.cepc.dv.field.contact_persons", "संपर्क व्यक्ति");
        m.put("ui.cepc.dv.field.how_contact_recorded", "यह संपर्क किस प्रकार दर्ज हुआ");
        m.put("ui.cepc.dv.contact.blurb",
                "विनियमित संस्था के वे व्यक्ति जिनसे यह कार्यालय इस शिकायत पर व्यवहार कर रहा है।");

        m.put("ui.cepc.dv.email.to", "प्रति");
        m.put("ui.cepc.dv.email.from", "प्रेषक");
        m.put("ui.cepc.dv.email.cc", "प्रतिलिपि");
        m.put("ui.cepc.dv.email.bcc", "गुप्त प्रतिलिपि");
        m.put("ui.cepc.dv.email.add_cc", "प्रतिलिपि प्राप्तकर्ता जोड़ें");
        m.put("ui.cepc.dv.email.add_bcc", "गुप्त प्रतिलिपि प्राप्तकर्ता जोड़ें");
        m.put("ui.cepc.dv.email.create_new", "नया ईमेल बनाएँ");
        m.put("ui.cepc.dv.email.drafts", "प्रारूप ईमेल");
        m.put("ui.cepc.dv.email.edit_draft", "प्रारूप संपादित करें");
        m.put("ui.cepc.dv.email.save_draft", "प्रारूप सहेजें");
        m.put("ui.cepc.dv.email.complaint_copy", "शिकायत की प्रतिलिपि");
        m.put("ui.cepc.dv.email.select_activity",
                "ईमेल देखने के लिए कोई गतिविधि चुनें या \"नया ईमेल बनाएँ\" पर क्लिक करें");
        m.put("ui.cepc.dv.email.send_failed", "यह ईमेल भेजा नहीं जा सका।");

        m.put("ui.cepc.dv.action.save", "सहेजें");
        m.put("ui.cepc.dv.action.update", "अद्यतन करें");
        m.put("ui.cepc.dv.action.cancel", "रद्द करें");
        m.put("ui.cepc.dv.action.close", "बंद करें");
        m.put("ui.cepc.dv.action.edit", "संपादित करें");
        m.put("ui.cepc.dv.action.remove", "हटाएँ");
        m.put("ui.cepc.dv.action.search", "खोजें");
        m.put("ui.cepc.dv.action.select", "चुनें");
        m.put("ui.cepc.dv.action.select_a_value", "कोई मान चुनें");
        m.put("ui.cepc.dv.action.download", "डाउनलोड करें");
        m.put("ui.cepc.dv.action.preview", "पूर्वावलोकन");
        m.put("ui.cepc.dv.action.preview_confirm", "पूर्वावलोकन करें और पुष्टि करें");
        m.put("ui.cepc.dv.action.post", "भेजें");
        m.put("ui.cepc.dv.action.reply", "उत्तर दें");
        m.put("ui.cepc.dv.action.retry", "पुनः प्रयास करें");
        m.put("ui.cepc.dv.action.refresh", "ताज़ा करें");
        m.put("ui.cepc.dv.action.bookmark", "बुकमार्क");
        m.put("ui.cepc.dv.action.full_screen", "पूर्ण स्क्रीन");
        m.put("ui.cepc.dv.action.read_more", "...और पढ़ें");
        m.put("ui.cepc.dv.action.show_less", "कम दिखाएँ");
        m.put("ui.cepc.dv.action.back_to_home", "होम पर वापस");
        m.put("ui.cepc.dv.action.back_to_complaint", "शिकायत पर वापस");
        m.put("ui.cepc.dv.action.forward", "अग्रेषित करें");
        m.put("ui.cepc.dv.action.refer_back", "वापस भेजें");
        m.put("ui.cepc.dv.action.accept", "स्वीकार करें");
        m.put("ui.cepc.dv.action.partial_accept", "आंशिक रूप से स्वीकार करें");
        m.put("ui.cepc.dv.action.reject", "अस्वीकार करें");
        m.put("ui.cepc.dv.action.approve", "अनुमोदित करें");
        m.put("ui.cepc.dv.action.settle", "निपटाएँ");
        m.put("ui.cepc.dv.action.withdraw", "वापस लें");
        m.put("ui.cepc.dv.action.send_for_approval", "अनुमोदन हेतु भेजें");
        m.put("ui.cepc.dv.action.mark_for_closure", "समापन हेतु चिह्नित करें");
        m.put("ui.cepc.dv.action.close_complaint", "शिकायत बंद करें");
        m.put("ui.cepc.dv.action.reopen_complaint", "शिकायत पुनः खोलें");
        m.put("ui.cepc.dv.action.mark_all_eligible", "सभी को पात्र चिह्नित करें");
        m.put("ui.cepc.dv.action.add_contact_person", "संपर्क व्यक्ति जोड़ें");
        m.put("ui.cepc.dv.action.add_another_contact_person", "अन्य संपर्क व्यक्ति जोड़ें");
        m.put("ui.cepc.dv.action.edit_contact_person", "इस संपर्क व्यक्ति को संपादित करें");

        m.put("ui.cepc.dv.ph.enter_text", "यहाँ पाठ दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_amount", "राशि दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_subject", "विषय दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_reason", "कारण दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_email", "ईमेल दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_your_email", "अपना ईमेल यहाँ दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_recipient_email", "प्राप्तकर्ता का ईमेल दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_complaint_number", "शिकायत संख्या दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_other_entity_name", "अन्य संस्था का नाम दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_grounds_flag", "आधार ध्वज दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_scheme_flag", "योजना ध्वज दर्ज करें");
        m.put("ui.cepc.dv.ph.enter_rbo_cgpc", "आरबीओ सीजीपीसी दर्ज करें");
        m.put("ui.cepc.dv.ph.post_your_comment", "अपनी टिप्पणी यहाँ लिखें");
        m.put("ui.cepc.dv.ph.contact_person_name", "संपर्क व्यक्ति का नाम");
        m.put("ui.cepc.dv.ph.mobile_10_digit", "10 अंकों का मोबाइल नंबर");
        m.put("ui.cepc.dv.ph.eg_branch_manager", "उदा. शाखा प्रबंधक");
        m.put("ui.cepc.dv.ph.eg_loan", "उदा. ऋण");
        m.put("ui.cepc.dv.ph.select_an_office", "कोई कार्यालय चुनें");
        m.put("ui.cepc.dv.ph.select_language", "भाषा चुनें");
        m.put("ui.cepc.dv.ph.search_deo", "नाम, ईमेल या कार्यालय से कार्यकारी अधिकारी खोजें");

        m.put("ui.cepc.dv.empty.no_comments", "अभी कोई टिप्पणी नहीं।");
        m.put("ui.cepc.dv.empty.no_history", "कोई इतिहास उपलब्ध नहीं।");
        m.put("ui.cepc.dv.empty.no_attachments", "कोई अनुलग्नक उपलब्ध नहीं।");
        m.put("ui.cepc.dv.empty.no_attachments_for_complaint", "इस शिकायत के लिए कोई अनुलग्नक उपलब्ध नहीं।");
        m.put("ui.cepc.dv.empty.no_document_details", "कोई दस्तावेज़ विवरण उपलब्ध नहीं।");
        m.put("ui.cepc.dv.empty.no_document_uploaded", "कोई दस्तावेज़ अपलोड नहीं किया गया");
        m.put("ui.cepc.dv.empty.no_nodal_records", "कोई नोडल अधिकारी रिकॉर्ड नहीं मिला");
        m.put("ui.cepc.dv.empty.no_contact_person",
                "अभी कोई संपर्क व्यक्ति दर्ज नहीं। संपर्क संस्था टैब से जोड़ें।");
        m.put("ui.cepc.dv.empty.no_entity_match", "उस नाम से कोई संस्था मेल नहीं खाती");
        m.put("ui.cepc.dv.empty.no_deo_match", "उस खोज से कोई कार्यकारी अधिकारी मेल नहीं खाता।");
        m.put("ui.cepc.dv.empty.no_additional_settings", "कोई अतिरिक्त सेटिंग उपलब्ध नहीं।");
        m.put("ui.cepc.dv.loading.contact_persons", "संपर्क व्यक्ति लोड हो रहे हैं…");
        m.put("ui.cepc.dv.loading.conversation", "वार्तालाप लोड हो रहा है…");
        m.put("ui.cepc.dv.loading.nodal_records", "नोडल अधिकारी रिकॉर्ड लोड हो रहे हैं…");
        m.put("ui.cepc.dv.loading.roster", "नामावली लोड हो रही है...");

        m.put("ui.cepc.dv.msg.confirm_action", "क्या आप वाकई अनुरोध को {{action}} चाहते हैं?");
        m.put("ui.cepc.dv.msg.confirm_mark_for_closure", "क्या आप वाकई शिकायत को समापन हेतु चिह्नित करना चाहते हैं?");
        m.put("ui.cepc.dv.msg.confirm_reopen", "क्या आप वाकई शिकायत को पुनः खोलना चाहते हैं?");
        m.put("ui.cepc.dv.msg.closed_successfully", "शिकायत {{number}} सफलतापूर्वक बंद कर दी गई है।");
        m.put("ui.cepc.dv.msg.routed_to", "शिकायत {{number}} को {{target}} के पास भेज दिया गया है।");
        m.put("ui.cepc.dv.msg.transferred_to", "शिकायत {{number}} को {{target}} में स्थानांतरित कर दिया गया है।");
        m.put("ui.cepc.dv.msg.already_sent_for_approval",
                "शिकायत {{number}} पहले ही अनुमोदन हेतु भेजी जा चुकी है और {{status}} स्थिति में है।");
        m.put("ui.cepc.dv.msg.pending_crpc_head", "{{status}}, सीआरपीसी प्रमुख का अनुमोदन लंबित।");
        m.put("ui.cepc.dv.msg.conciliation_other_officer",
                "यह शिकायत किसी अन्य अधिकारी को सौंपी गई है, इसलिए इसकी सुलह यहाँ संपादित नहीं की जा सकती।");
        m.put("ui.cepc.dv.msg.contact_needs_name_and_one",
                "नाम तथा ईमेल या मोबाइल में से कम से कम एक दर्ज करें।");
        m.put("ui.cepc.dv.msg.why_reopen", "यह शिकायत पुनः क्यों खोली जा रही है?");
        m.put("ui.cepc.dv.msg.change_territory", "क्या आप क्षेत्र बदलना चाहते हैं?");
        m.put("ui.cepc.dv.msg.declaration",
                "मेरे द्वारा प्रणाली में दर्ज सभी आँकड़े/जानकारी मेरी समझ के अनुसार सही हैं");

        m.put("ui.cepc.dv.state.new_complaint", "नई शिकायत");
        m.put("ui.cepc.dv.state.draft", "प्रारूप");
        m.put("ui.cepc.dv.state.duplicate", "अनुलिपि");
        m.put("ui.cepc.dv.state.information_required", "जानकारी आवश्यक");
        m.put("ui.cepc.dv.state.sent_back", "वापस भेजा गया");
        m.put("ui.cepc.dv.state.sent_for_approval", "अनुमोदन हेतु भेजा गया");
        m.put("ui.cepc.dv.state.assessment_sent_for_approval", "मूल्यांकन अनुमोदन हेतु भेजा गया");
        m.put("ui.cepc.dv.state.sent_to_dealing_officer", "कार्यकारी अधिकारी को भेजा गया");
        m.put("ui.cepc.dv.state.sent_to_other_office", "अन्य कार्यालय को भेजा गया");
        m.put("ui.cepc.dv.state.complaint_closed", "शिकायत बंद");
        m.put("ui.cepc.dv.state.complaint_forwarded", "शिकायत अग्रेषित");
        m.put("ui.cepc.dv.state.complaint_sent_back", "शिकायत वापस भेजी गई");
        m.put("ui.cepc.dv.state.complaint_reopened", "शिकायत पुनः खोली गई");
        m.put("ui.cepc.dv.state.complaint_already_processed", "शिकायत पहले ही प्रसंस्कृत");
        m.put("ui.cepc.dv.state.advisory_issued", "सलाह जारी");
        m.put("ui.cepc.dv.state.pre_enquiry_received", "पूर्व जाँच प्राप्त");
        m.put("ui.cepc.dv.state.notice_13_1", "13-1 नोटिस");
        m.put("ui.cepc.dv.state.notice_13_1_issued", "13:1 नोटिस जारी");
        m.put("ui.cepc.dv.state.maintainable", "विचारणीय");
        m.put("ui.cepc.dv.state.partially_maintainable", "आंशिक रूप से विचारणीय");
        m.put("ui.cepc.dv.state.non_maintainable", "अविचारणीय");
        m.put("ui.cepc.dv.state.not_a_complaint", "शिकायत नहीं");
        m.put("ui.cepc.dv.state.view_only", "केवल देखने योग्य");
        m.put("ui.cepc.dv.state.inactive", "निष्क्रिय");
        m.put("ui.cepc.dv.state.on_leave", "अवकाश पर");
        m.put("ui.cepc.dv.state.at_threshold", "सीमा पर");
        m.put("ui.cepc.dv.state.atm_complaint", "एटीएम शिकायत");

        m.put("ui.cepc.dv.value.yes", "हाँ");
        m.put("ui.cepc.dv.value.no", "नहीं");
        m.put("ui.cepc.dv.value.optional", "(वैकल्पिक)");
        m.put("ui.cepc.dv.value.not_applicable", "लागू नहीं");
        m.put("ui.cepc.dv.value.automatic", "स्वचालित");
        m.put("ui.cepc.dv.value.manual", "मैनुअल");
        m.put("ui.cepc.dv.value.atm_card", "एटीएम / क्रेडिट / डेबिट कार्ड");
        m.put("ui.cepc.dv.value.loan_account", "ऋण / निपटान खाता");

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
