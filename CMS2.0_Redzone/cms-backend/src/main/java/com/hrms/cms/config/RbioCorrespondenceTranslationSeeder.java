package com.hrms.cms.config;

import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translations for the RBIO correspondence surfaces: the Email Communication tab, the Attachments tab,
 * the History tab, and the secure upload link (UST585-604, UST656-657, UST776).
 *
 * <p>A SEPARATE seeder, per the convention stated at {@code AaRegisterTranslationSeeder}: seven sessions
 * are working this repository concurrently, and a shared seeder is a guaranteed conflict in a file where
 * a conflict silently costs a locale.
 *
 * <p>Every key is namespaced under {@code email.*}, {@code attachment.*}, {@code complaint.history.*} or
 * {@code upload_link.*}. Namespacing matters more than usual here: translation keys are idempotent by
 * {@code existsByCode}, so if two sessions choose the same code the first text silently wins and the
 * second never appears.
 *
 * <p>Insert-if-absent, like every seeder here — so correcting a default in this file does NOT fix rows
 * already in the database. Any later text correction needs a corrective UPDATE in both migrations,
 * scoped BY KEY CODE rather than by an English substring, because localized rows are in native scripts.
 */
@Component
@Order(32)
public class RbioCorrespondenceTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "rbio";

    private final TranslationKeyRepository keyRepo;

    public RbioCorrespondenceTranslationSeeder(TranslationKeyRepository keyRepo) {
        this.keyRepo = keyRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        english().forEach(this::seed);
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();

        // ── Email Communication tab (UST590-593) ──
        m.put("email.section_title", "Email Communication");
        m.put("email.compose", "Compose Email");
        m.put("email.loading", "Loading correspondence...");
        m.put("email.empty", "No emails have been exchanged on this complaint.");
        m.put("email.retry", "Retry");
        m.put("email.error_load_failed", "The email correspondence could not be loaded. Please retry.");
        m.put("email.sent", "Sent");
        m.put("email.received", "Received");
        m.put("email.from", "From");
        m.put("email.to", "To");
        m.put("email.cc", "Cc");
        m.put("email.subject", "Subject");
        m.put("email.body", "Message");
        m.put("email.template", "Template");
        m.put("email.no_template", "No template");
        m.put("email.template_used", "Template used");
        m.put("email.template_search_placeholder", "Search templates");
        m.put("email.to_placeholder", "Comma-separated RBI email addresses");
        m.put("email.reply", "Reply");
        m.put("email.reply_all", "Reply All");
        m.put("email.forward", "Forward");
        m.put("email.send", "Send");
        m.put("email.sending", "Sending...");
        m.put("email.cancel", "Cancel");
        m.put("email.thread_preserved", "The original message will be quoted and the thread preserved.");
        m.put("email.error_no_recipients", "At least one recipient is required.");
        m.put("email.error_send_failed", "The email could not be sent. Please retry.");
        m.put("email.error_malformed_recipient",
                "One or more recipient addresses are not valid email addresses.");
        // UST656: the refusal an officer sees when a recipient is outside the permitted domains.
        m.put("email.error_recipient_domain_not_allowed",
                "Email may only be sent to addresses on the permitted RBI domains.");

        // ── Attachments tab (UST585-589) ──
        m.put("attachment.section_title", "Attachments");
        m.put("attachment.attach_new", "Attach New Document");
        m.put("attachment.uploading", "Uploading...");
        m.put("attachment.download", "Download");
        m.put("attachment.download_all", "Download All");
        m.put("attachment.loading", "Loading attachments...");
        m.put("attachment.empty", "No documents have been attached to this complaint.");
        m.put("attachment.retry", "Retry");
        m.put("attachment.filter_all", "All sources");
        m.put("attachment.col_name", "Document");
        m.put("attachment.col_source", "Source");
        m.put("attachment.col_uploaded_by", "Uploaded by");
        m.put("attachment.col_size", "Size");
        m.put("attachment.col_uploaded_at", "Uploaded at");
        m.put("attachment.limit_note",
                "Up to {{maxFile}} MB per file and {{maxTotal}} MB in total.");
        m.put("attachment.error_load_failed", "The attachments could not be loaded. Please retry.");
        m.put("attachment.error_upload_failed", "The document could not be uploaded. Please retry.");
        // UST589: provenance labels.
        m.put("attachment.source.officer", "RBI Officer");
        m.put("attachment.source.complainant", "Complainant");
        m.put("attachment.source.regulated_entity", "Regulated Entity");
        m.put("attachment.source.system", "System");
        m.put("attachment.source.unknown", "Not recorded");

        // ── History tab (UST594-597) ──
        m.put("complaint.history.section_title", "Complaint History");
        m.put("complaint.history.loading", "Loading history...");
        m.put("complaint.history.empty", "No actions have been recorded on this complaint yet.");
        m.put("complaint.history.retry", "Retry");
        m.put("complaint.history.error_load_failed",
                "The complaint history could not be loaded. Please retry.");
        m.put("complaint.history.show_automatic", "Include system events");
        m.put("complaint.history.automatic", "System");
        m.put("complaint.history.by", "by");
        m.put("complaint.history.none", "None");
        m.put("complaint.history.closure_clause", "Closure clause");
        m.put("complaint.history.destination_office", "Destination office");
        m.put("complaint.history.action_unknown", "Action");
        // UST597: shown when a modification attempt is refused.
        m.put("complaint.history.error_immutable",
                "Complaint history is an immutable audit record and cannot be modified or deleted.");
        // Action labels. Keyed off the action code, so a new table-driven action needs no code change.
        m.put("complaint.history.action.filed", "Complaint filed");
        m.put("complaint.history.action.status_change", "Status changed");
        m.put("complaint.history.action.update", "Complaint updated");
        m.put("complaint.history.action.maintainability_decision", "Maintainability decided");
        m.put("complaint.history.action.reassign", "Reassigned");
        m.put("complaint.history.action.escalate", "Escalated");
        m.put("complaint.history.action.approve", "Approved");
        m.put("complaint.history.action.close_complaint", "Complaint closed");
        m.put("complaint.history.action.reject", "Complaint rejected");
        m.put("complaint.history.action.resolve", "Complaint resolved");
        m.put("complaint.history.action.reopen", "Complaint reopened");
        m.put("complaint.history.action.forward_to_conciliation", "Forwarded to conciliation");
        m.put("complaint.history.action.forward_to_adjudication", "Forwarded to adjudication");
        m.put("complaint.history.action.schedule_meeting", "Meeting scheduled");
        m.put("complaint.history.action.re_responded", "Regulated entity responded");

        // ── Secure upload link (UST598-604, UST776) ──
        m.put("upload_link.otp_sent", "Verification codes have been sent to your email and mobile.");
        m.put("upload_link.otp_verified", "Verified. You may now upload documents.");
        m.put("upload_link.upload_success", "Your documents have been uploaded.");
        m.put("upload_link.status_none", "No active upload link for this complaint.");
        m.put("upload_link.error_missing_token", "An upload link token is required.");
        m.put("upload_link.error_invalid_token", "This upload link is not valid.");
        m.put("upload_link.error_expired", "This upload link has expired.");
        m.put("upload_link.error_both_otps_required",
                "Both the email and the mobile verification codes are required.");
        m.put("upload_link.error_invalid_otp", "The verification code is incorrect.");
        m.put("upload_link.error_otp_expired",
                "The verification code has expired. Please request new codes.");
        m.put("upload_link.error_max_attempts",
                "Too many incorrect attempts. Please request new codes.");
        m.put("upload_link.error_otp_not_verified",
                "Verify the email and mobile codes before uploading documents.");
        m.put("upload_link.error_no_files", "At least one file must be provided.");
        m.put("upload_link.error_all_rejected", "None of the files could be accepted.");
        m.put("upload_link.error_storage_failed",
                "The documents could not be saved. Please try again.");
        m.put("upload_link.error_no_contact",
                "This complaint has no email address or mobile number, so a secure upload link "
                        + "cannot be delivered.");
        m.put("upload_link.error_complaint_missing", "The complaint for this link no longer exists.");

        // ── UST603-604: server-side refusals while a link is live ──
        m.put("workflow.error_upload_link_active_closure",
                "This complaint cannot be closed while a secure document-upload link is active.");
        m.put("workflow.error_upload_link_active_forward",
                "This complaint cannot be forwarded to a deciding authority while a secure "
                        + "document-upload link is active.");

        m.put("complaint.error_not_found", "Complaint not found.");
        return m;
    }

    private void seed(String code, String englishValue) {
        if (keyRepo.existsByCode(code)) {
            return;
        }
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(MODULE);
        key.setDefaultValue(englishValue);
        key.setDescription("RBIO correspondence, attachments, history and upload-link text (S6)");
        keyRepo.save(key);
    }
}
