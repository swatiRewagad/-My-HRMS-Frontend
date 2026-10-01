package com.hrms.cms.config;

import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Translation keys for the RE/RBI query thread UI (UST853–UST857, UST860).
 *
 * Insert-if-absent, like the other seeders: correcting a default here does NOT rewrite a row that
 * is already in the database. Any later text correction needs a scoped UPDATE in both migration
 * directories, keyed by code rather than by an English phrase.
 */
@Component
@Order(6)
public class QueryThreadTranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;

    public QueryThreadTranslationSeeder(TranslationKeyRepository keyRepo) {
        this.keyRepo = keyRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // ═══ Thread shell ═══
        seed("query.section_title", "Queries & Correspondence");
        seed("query.none_yet", "No queries have been raised on this complaint yet.");
        seed("query.raise_button", "Raise a Query");
        seed("query.subject_label", "Subject");
        seed("query.message_label", "Message");
        seed("query.type_label", "Query type");
        seed("query.send_button", "Send");
        seed("query.reply_placeholder", "Write a reply…");
        seed("query.reply_button", "Post Reply");
        seed("query.cancel_button", "Cancel");
        seed("query.posted_by", "Posted by");
        seed("query.thread_resolved", "This thread is resolved and no longer accepts replies.");
        seed("query.awaiting_my_response", "Query awaiting my response");
        seed("query.unread_badge", "Unread");
        seed("query.status_open", "Open");
        seed("query.status_resolved", "Resolved");
        seed("query.pending_with_re", "Awaiting entity response");
        seed("query.pending_with_rbi", "Awaiting RBI response");

        // ═══ Immutability notice (UST857) ═══
        seed("query.immutable_notice",
             "Messages cannot be edited or deleted. To correct something, post a follow-up message.");

        // ═══ Query types ═══
        seed("query.type.clarification", "Clarification");
        seed("query.type.extension_request", "Deadline Extension Request");
        seed("query.type.document_request", "Document Request");
        seed("query.type.meeting_request", "Meeting Request");

        // ═══ Extension requests (UST853) ═══
        seed("query.extension.proposed_deadline_label", "Proposed new deadline");
        seed("query.extension.reason_label", "Reason for the extension");
        seed("query.extension.clock_running_notice",
             "The response deadline continues to run while this request is pending.");
        seed("query.extension.decision_pending", "Awaiting decision");
        seed("query.extension.decision_approved", "Extension approved");
        seed("query.extension.decision_rejected", "Extension rejected");
        seed("query.extension.granted_deadline_label", "Revised deadline");
        seed("query.extension.approve_button", "Approve Extension");
        seed("query.extension.reject_button", "Reject Extension");
        seed("query.extension.decision_reason_label", "Reason for the decision");
        seed("query.extension.reason_required_on_reject",
             "A reason is required when rejecting an extension request.");
        seed("query.extension.deadline_must_be_future", "The proposed new deadline must be in the future.");
        seed("query.extension.exceeds_maximum",
             "The proposed deadline exceeds the maximum extension permitted.");
        seed("query.extension.clock_paused_notice",
             "The response deadline is paused and has been revised.");

        // ═══ Document requests (UST854) ═══
        seed("query.documents.checklist_title", "Documents requested");
        seed("query.documents.add_item", "Add a document to the list");
        seed("query.documents.item_name_label", "Document name");
        seed("query.documents.item_description_label", "Notes (optional)");
        seed("query.documents.mark_provided", "Mark as provided");
        seed("query.documents.provided", "Provided");
        seed("query.documents.outstanding", "Outstanding");
        seed("query.documents.attach_first",
             "Attach the document before marking this item as provided.");
        seed("query.documents.attach_in_thread", "Attach a document to this thread");
        seed("query.documents.at_least_one_required",
             "A document request must name at least one document.");
        seed("query.documents.all_provided", "All requested documents have been provided.");

        // ═══ Meeting requests (UST855) ═══
        seed("query.meeting.purpose_label", "Purpose of the meeting");
        seed("query.meeting.proposed_times_title", "Proposed times");
        seed("query.meeting.add_slot", "Add another time");
        seed("query.meeting.slot_start_label", "From");
        seed("query.meeting.slot_end_label", "To");
        seed("query.meeting.accept_button", "Accept this time");
        seed("query.meeting.counter_button", "Propose a different time");
        seed("query.meeting.decline_button", "Decline");
        seed("query.meeting.decline_reason_label", "Reason for declining");
        seed("query.meeting.outcome_accepted", "Meeting accepted");
        seed("query.meeting.outcome_declined", "Meeting declined");
        seed("query.meeting.outcome_pending", "Awaiting response");
        seed("query.meeting.slot_superseded", "Superseded");
        seed("query.meeting.no_calendar_notice",
             "This records the agreed time only — no calendar invitation is sent.");
        seed("query.meeting.at_least_one_slot_required",
             "At least one proposed date and time is required.");
        seed("query.meeting.decline_reason_required",
             "A reason is required when declining a meeting request.");

        // ═══ Internal notes (UST860) ═══
        seed("query.notes.section_title", "Internal Notes");
        seed("query.notes.entity_only_notice",
             "Internal notes are visible only to your organisation. They are never shared with RBI or the complainant.");
        seed("query.notes.add_placeholder", "Add an internal note…");
        seed("query.notes.add_button", "Add Note");
        seed("query.notes.edit_button", "Edit");
        seed("query.notes.save_button", "Save");
        seed("query.notes.edit_window_notice", "You can edit your own note for a short time after posting.");
        seed("query.notes.locked", "This note is locked and can no longer be edited.");
        seed("query.notes.edited_marker", "edited");
        seed("query.notes.none_yet", "No internal notes have been added yet.");
        seed("query.notes.export_blocked",
             "Internal notes are entity-only and cannot be included in an export shared with RBI.");

        // ═══ Notifications ═══
        seed("notification.query.raised", "A new query has been raised");
        seed("notification.query.replied", "A query has a new reply");
        seed("notification.query.extension_approved", "Your extension request was approved");
        seed("notification.query.extension_rejected", "Your extension request was rejected");
        seed("notification.query.meeting_updated", "A meeting request was updated");

        // ═══ Errors ═══
        seed("query.error.subject_required", "A subject is required.");
        seed("query.error.body_required", "A message is required.");
        seed("query.error.load_failed", "The queries could not be loaded. Please try again.");
        seed("query.error.send_failed", "The query could not be sent. Please try again.");
        seed("query.error.not_permitted", "You do not have permission to perform this action.");
    }

    private void seed(String code, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule("query");
        key.setDefaultValue(defaultValue);
        keyRepo.save(key);
    }
}
