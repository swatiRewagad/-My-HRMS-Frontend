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
 * Translation keys for RE nodal-officer reassignment (UST838–UST845).
 *
 * <p>Insert-if-absent in all ten supported locales, following
 * {@link StatusVocabularyTranslationSeeder} rather than {@link QueryThreadTranslationSeeder} —
 * the latter seeds English only, which would leave nine locales silently falling back to English
 * text while a locale-coverage test still passed on key presence alone.
 *
 * <p>English comes from {@code TranslationKey.defaultValue}; the other nine become {@code Translation}
 * rows. Correcting a default here does NOT rewrite a row already in the database; a later text
 * correction needs a scoped UPDATE in both migration directories, keyed by code.
 */
@Component
@Order(10)
public class ReassignmentTranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public ReassignmentTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // ═══ Reassignment popup (UST841) ═══
        m.put("re.reassign.title", "Reassign Record");
        m.put("re.reassign.candidate_label", "Reassign to");
        m.put("re.reassign.search_placeholder", "Search by name, designation or email");
        m.put("re.reassign.workload_label", "Current workload");
        m.put("re.reassign.workload_column", "Open records");
        m.put("re.reassign.no_candidates", "No other officers in your entity are available to receive this record.");
        m.put("re.reassign.reason_label", "Reason for reassignment");
        m.put("re.reassign.submit_button", "Submit Request");
        m.put("re.reassign.cancel_button", "Cancel");
        m.put("re.reassign.role_filter_label", "Role");
        m.put("re.reassign.role.nodal_officer", "Nodal Officer");
        m.put("re.reassign.role.contact_person", "Contact Person");
        m.put("re.reassign.role.pno", "Principal Nodal Officer");
        m.put("re.reassign.territory_label", "Territory");

        // ═══ Immutable reason + clarifications (UST840) ═══
        m.put("re.reassign.reason_immutable_notice",
              "The reason cannot be edited once submitted. To add context, post a clarification.");
        m.put("re.reassign.clarification_title", "Clarifications");
        m.put("re.reassign.clarification_add_placeholder", "Add a clarification…");
        m.put("re.reassign.clarification_add_button", "Add Clarification");
        m.put("re.reassign.clarification_none", "No clarifications have been added.");
        m.put("re.reassign.original_reason_label", "Original reason");

        // ═══ My Requests (UST842) ═══
        m.put("re.reassign.my_requests_title", "My Reassignment Requests");
        m.put("re.reassign.my_requests_none", "You have not raised any reassignment requests.");
        m.put("re.reassign.requested_at", "Requested");
        m.put("re.reassign.decided_at", "Decided");
        m.put("re.reassign.withdraw_button", "Withdraw");
        m.put("re.reassign.from_officer", "From");
        m.put("re.reassign.to_officer", "To");

        // ═══ PNO approvals (UST843) ═══
        m.put("re.reassign.approvals_title", "Reassignment Approvals");
        m.put("re.reassign.approvals_none", "There are no reassignment requests awaiting your approval.");
        m.put("re.reassign.approve_button", "Approve");
        m.put("re.reassign.reject_button", "Reject");
        m.put("re.reassign.bulk_approve_button", "Approve Selected");
        m.put("re.reassign.bulk_reject_button", "Reject Selected");
        m.put("re.reassign.decision_comment_label", "Comment");
        m.put("re.reassign.select_all", "Select all");
        m.put("re.reassign.selected_count", "selected");
        m.put("re.reassign.bulk_partial_success",
              "Some records could not be updated. The remaining records were updated successfully.");
        m.put("re.reassign.bulk_all_succeeded", "All selected records were updated.");

        // ═══ PNO dashboard (UST838) ═══
        m.put("re.pno.dashboard_title", "Team Workload");
        m.put("re.pno.officer_column", "Officer");
        m.put("re.pno.workload_column", "Open records");
        m.put("re.pno.total_active_records", "Total open records");
        m.put("re.pno.pending_approvals", "Requests awaiting approval");
        m.put("re.pno.no_officers", "No officers are registered for your entity.");
        m.put("re.pno.workload_definition_notice",
              "Open records exclude drafts and closed work.");

        // ═══ History report (UST844) ═══
        m.put("re.reassign.history_title", "Reassignment History");
        m.put("re.reassign.history_none", "No reassignments have been recorded.");
        m.put("re.reassign.history_from_filter", "Reassigned from");
        m.put("re.reassign.history_to_filter", "Reassigned to");
        m.put("re.reassign.history_date_from", "From date");
        m.put("re.reassign.history_date_to", "To date");
        m.put("re.reassign.history_trigger", "Type");
        m.put("re.reassign.trigger.approved_request", "Approved request");
        m.put("re.reassign.trigger.direct", "Direct reassignment");
        m.put("re.reassign.history_performed_by", "Actioned by");
        m.put("re.reassign.summary_outbound", "Records reassigned away");
        m.put("re.reassign.summary_inbound", "Records received");

        // ═══ Notifications (UST845) ═══
        m.put("notification.reassign.out", "A record has been reassigned away from you");
        m.put("notification.reassign.out.body",
              "A complaint record you were handling has been reassigned to another officer.");
        m.put("notification.reassign.in", "A record has been reassigned to you");
        m.put("notification.reassign.in.body",
              "A complaint record has been reassigned to you and now needs your attention.");

        // ═══ Status vocabulary for reassignment requests ═══
        m.put("re.reassign.status.pending", "Awaiting Approval");
        m.put("re.reassign.status.approved", "Approved");
        m.put("re.reassign.status.rejected", "Rejected");
        m.put("re.reassign.status.withdrawn", "Withdrawn");

        // ═══ Errors — every key the API can return ═══
        m.put("re.reassign.error.unauthenticated", "You must sign in to perform this action.");
        m.put("re.reassign.error.not_permitted", "You do not have permission to perform this action.");
        m.put("re.reassign.error.pno_only", "Only a Principal Nodal Officer can approve reassignments.");
        m.put("re.reassign.error.cross_entity", "You can only act on records belonging to your own entity.");
        m.put("re.reassign.error.entity_unresolved", "Your entity could not be established.");
        m.put("re.reassign.error.entity_required", "An entity must be specified.");
        m.put("re.reassign.error.target_required", "Select the officer who should receive the record.");
        m.put("re.reassign.error.target_not_in_entity", "That officer does not belong to your entity.");
        m.put("re.reassign.error.target_inactive", "That officer is no longer active and cannot receive records.");
        m.put("re.reassign.error.already_assigned", "The record is already assigned to that officer.");
        m.put("re.reassign.error.already_pending", "A reassignment request is already pending for this record.");
        m.put("re.reassign.error.already_decided", "This request has already been decided.");
        m.put("re.reassign.error.reason_too_short", "Please give a fuller reason for the reassignment.");
        m.put("re.reassign.error.clarification_required", "A clarification cannot be empty.");
        m.put("re.reassign.error.rejection_comment_required", "A comment is required when rejecting a request.");
        m.put("re.reassign.error.nothing_selected", "Select at least one record.");
        m.put("re.reassign.error.bulk_limit_exceeded", "Too many records were selected at once.");
        m.put("re.reassign.error.not_your_request", "You can only withdraw your own request.");
        m.put("re.reassign.error.conflict",
              "This record was changed by someone else. Reload and try again.");
        m.put("re.reassign.error.not_found", "The record or request could not be found.");
        m.put("re.reassign.error.invalid", "The request could not be processed.");
        m.put("re.reassign.error.invalid_date", "The date provided is not valid.");
        m.put("re.reassign.error.unexpected", "Something went wrong. Please try again.");
        m.put("re.reassign.error.load_failed", "The reassignment details could not be loaded.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "रिकॉर्ड पुनः सौंपें");
        m.put("re.reassign.candidate_label", "किसे सौंपें");
        m.put("re.reassign.search_placeholder", "नाम, पदनाम या ईमेल से खोजें");
        m.put("re.reassign.workload_label", "वर्तमान कार्यभार");
        m.put("re.reassign.workload_column", "खुले रिकॉर्ड");
        m.put("re.reassign.no_candidates", "आपकी संस्था में कोई अन्य अधिकारी यह रिकॉर्ड लेने के लिए उपलब्ध नहीं है।");
        m.put("re.reassign.reason_label", "पुनः सौंपने का कारण");
        m.put("re.reassign.submit_button", "अनुरोध भेजें");
        m.put("re.reassign.cancel_button", "रद्द करें");
        m.put("re.reassign.role_filter_label", "भूमिका");
        m.put("re.reassign.role.nodal_officer", "नोडल अधिकारी");
        m.put("re.reassign.role.contact_person", "संपर्क व्यक्ति");
        m.put("re.reassign.role.pno", "प्रधान नोडल अधिकारी");
        m.put("re.reassign.territory_label", "क्षेत्र");
        m.put("re.reassign.reason_immutable_notice",
              "प्रस्तुत करने के बाद कारण संपादित नहीं किया जा सकता। संदर्भ जोड़ने के लिए स्पष्टीकरण दर्ज करें।");
        m.put("re.reassign.clarification_title", "स्पष्टीकरण");
        m.put("re.reassign.clarification_add_placeholder", "स्पष्टीकरण जोड़ें…");
        m.put("re.reassign.clarification_add_button", "स्पष्टीकरण जोड़ें");
        m.put("re.reassign.clarification_none", "कोई स्पष्टीकरण नहीं जोड़ा गया है।");
        m.put("re.reassign.original_reason_label", "मूल कारण");
        m.put("re.reassign.my_requests_title", "मेरे पुनः सौंपने के अनुरोध");
        m.put("re.reassign.my_requests_none", "आपने कोई पुनः सौंपने का अनुरोध नहीं किया है।");
        m.put("re.reassign.requested_at", "अनुरोध किया");
        m.put("re.reassign.decided_at", "निर्णय हुआ");
        m.put("re.reassign.withdraw_button", "वापस लें");
        m.put("re.reassign.from_officer", "से");
        m.put("re.reassign.to_officer", "को");
        m.put("re.reassign.approvals_title", "पुनः सौंपने की स्वीकृतियाँ");
        m.put("re.reassign.approvals_none", "आपकी स्वीकृति की प्रतीक्षा में कोई अनुरोध नहीं है।");
        m.put("re.reassign.approve_button", "स्वीकृत करें");
        m.put("re.reassign.reject_button", "अस्वीकार करें");
        m.put("re.reassign.bulk_approve_button", "चयनित स्वीकृत करें");
        m.put("re.reassign.bulk_reject_button", "चयनित अस्वीकार करें");
        m.put("re.reassign.decision_comment_label", "टिप्पणी");
        m.put("re.reassign.select_all", "सभी चुनें");
        m.put("re.reassign.selected_count", "चयनित");
        m.put("re.reassign.bulk_partial_success",
              "कुछ रिकॉर्ड अद्यतन नहीं हो सके। शेष रिकॉर्ड सफलतापूर्वक अद्यतन कर दिए गए।");
        m.put("re.reassign.bulk_all_succeeded", "सभी चयनित रिकॉर्ड अद्यतन कर दिए गए।");
        m.put("re.pno.dashboard_title", "टीम कार्यभार");
        m.put("re.pno.officer_column", "अधिकारी");
        m.put("re.pno.workload_column", "खुले रिकॉर्ड");
        m.put("re.pno.total_active_records", "कुल खुले रिकॉर्ड");
        m.put("re.pno.pending_approvals", "स्वीकृति की प्रतीक्षा में अनुरोध");
        m.put("re.pno.no_officers", "आपकी संस्था के लिए कोई अधिकारी पंजीकृत नहीं है।");
        m.put("re.pno.workload_definition_notice", "खुले रिकॉर्ड में प्रारूप और बंद कार्य शामिल नहीं हैं।");
        m.put("re.reassign.history_title", "पुनः सौंपने का इतिहास");
        m.put("re.reassign.history_none", "कोई पुनः सौंपना दर्ज नहीं है।");
        m.put("re.reassign.history_from_filter", "किससे सौंपा गया");
        m.put("re.reassign.history_to_filter", "किसे सौंपा गया");
        m.put("re.reassign.history_date_from", "आरंभ तिथि");
        m.put("re.reassign.history_date_to", "अंतिम तिथि");
        m.put("re.reassign.history_trigger", "प्रकार");
        m.put("re.reassign.trigger.approved_request", "स्वीकृत अनुरोध");
        m.put("re.reassign.trigger.direct", "सीधा पुनः सौंपना");
        m.put("re.reassign.history_performed_by", "कार्रवाई करने वाला");
        m.put("re.reassign.summary_outbound", "अन्य को सौंपे गए रिकॉर्ड");
        m.put("re.reassign.summary_inbound", "प्राप्त रिकॉर्ड");
        m.put("notification.reassign.out", "एक रिकॉर्ड आपसे हटाकर किसी और को सौंप दिया गया है");
        m.put("notification.reassign.out.body",
              "आपके पास जो शिकायत रिकॉर्ड था, वह किसी अन्य अधिकारी को सौंप दिया गया है।");
        m.put("notification.reassign.in", "एक रिकॉर्ड आपको सौंपा गया है");
        m.put("notification.reassign.in.body",
              "एक शिकायत रिकॉर्ड आपको सौंपा गया है और अब उस पर आपका ध्यान आवश्यक है।");
        m.put("re.reassign.status.pending", "स्वीकृति प्रतीक्षित");
        m.put("re.reassign.status.approved", "स्वीकृत");
        m.put("re.reassign.status.rejected", "अस्वीकृत");
        m.put("re.reassign.status.withdrawn", "वापस लिया गया");
        m.put("re.reassign.error.unauthenticated", "यह कार्य करने के लिए आपको साइन इन करना होगा।");
        m.put("re.reassign.error.not_permitted", "आपको यह कार्य करने की अनुमति नहीं है।");
        m.put("re.reassign.error.pno_only", "केवल प्रधान नोडल अधिकारी पुनः सौंपना स्वीकृत कर सकते हैं।");
        m.put("re.reassign.error.cross_entity", "आप केवल अपनी संस्था के रिकॉर्ड पर कार्य कर सकते हैं।");
        m.put("re.reassign.error.entity_unresolved", "आपकी संस्था निर्धारित नहीं हो सकी।");
        m.put("re.reassign.error.entity_required", "संस्था निर्दिष्ट करना आवश्यक है।");
        m.put("re.reassign.error.target_required", "उस अधिकारी को चुनें जिसे रिकॉर्ड सौंपा जाना है।");
        m.put("re.reassign.error.target_not_in_entity", "वह अधिकारी आपकी संस्था का नहीं है।");
        m.put("re.reassign.error.target_inactive", "वह अधिकारी अब सक्रिय नहीं है और रिकॉर्ड नहीं ले सकता।");
        m.put("re.reassign.error.already_assigned", "रिकॉर्ड पहले से ही उस अधिकारी को सौंपा गया है।");
        m.put("re.reassign.error.already_pending", "इस रिकॉर्ड के लिए एक अनुरोध पहले से लंबित है।");
        m.put("re.reassign.error.already_decided", "इस अनुरोध पर पहले ही निर्णय हो चुका है।");
        m.put("re.reassign.error.reason_too_short", "कृपया पुनः सौंपने का अधिक विस्तृत कारण दें।");
        m.put("re.reassign.error.clarification_required", "स्पष्टीकरण रिक्त नहीं हो सकता।");
        m.put("re.reassign.error.rejection_comment_required", "अनुरोध अस्वीकार करते समय टिप्पणी आवश्यक है।");
        m.put("re.reassign.error.nothing_selected", "कम से कम एक रिकॉर्ड चुनें।");
        m.put("re.reassign.error.bulk_limit_exceeded", "एक बार में बहुत अधिक रिकॉर्ड चुने गए।");
        m.put("re.reassign.error.not_your_request", "आप केवल अपना अनुरोध वापस ले सकते हैं।");
        m.put("re.reassign.error.conflict", "यह रिकॉर्ड किसी और ने बदल दिया है। पुनः लोड करके फिर प्रयास करें।");
        m.put("re.reassign.error.not_found", "रिकॉर्ड या अनुरोध नहीं मिला।");
        m.put("re.reassign.error.invalid", "अनुरोध संसाधित नहीं हो सका।");
        m.put("re.reassign.error.invalid_date", "दी गई तिथि मान्य नहीं है।");
        m.put("re.reassign.error.unexpected", "कुछ गलत हो गया। कृपया पुनः प्रयास करें।");
        m.put("re.reassign.error.load_failed", "पुनः सौंपने का विवरण लोड नहीं हो सका।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "नोंद पुन्हा सुपूर्द करा");
        m.put("re.reassign.candidate_label", "कोणाकडे सुपूर्द करावी");
        m.put("re.reassign.search_placeholder", "नाव, पदनाम किंवा ईमेलद्वारे शोधा");
        m.put("re.reassign.workload_label", "सध्याचा कामाचा भार");
        m.put("re.reassign.workload_column", "प्रलंबित नोंदी");
        m.put("re.reassign.no_candidates", "ही नोंद स्वीकारण्यासाठी आपल्या संस्थेत दुसरा कोणताही अधिकारी उपलब्ध नाही.");
        m.put("re.reassign.reason_label", "पुन्हा सुपूर्द करण्याचे कारण");
        m.put("re.reassign.submit_button", "विनंती सादर करा");
        m.put("re.reassign.cancel_button", "रद्द करा");
        m.put("re.reassign.role_filter_label", "भूमिका");
        m.put("re.reassign.role.nodal_officer", "नोडल अधिकारी");
        m.put("re.reassign.role.contact_person", "संपर्क व्यक्ती");
        m.put("re.reassign.role.pno", "मुख्य नोडल अधिकारी");
        m.put("re.reassign.territory_label", "कार्यक्षेत्र");
        m.put("re.reassign.reason_immutable_notice",
              "सादर केल्यानंतर कारणात बदल करता येत नाही. अधिक माहिती देण्यासाठी स्पष्टीकरण नोंदवा.");
        m.put("re.reassign.clarification_title", "स्पष्टीकरणे");
        m.put("re.reassign.clarification_add_placeholder", "स्पष्टीकरण नोंदवा…");
        m.put("re.reassign.clarification_add_button", "स्पष्टीकरण नोंदवा");
        m.put("re.reassign.clarification_none", "कोणतेही स्पष्टीकरण नोंदवलेले नाही.");
        m.put("re.reassign.original_reason_label", "मूळ कारण");
        m.put("re.reassign.my_requests_title", "माझ्या पुनर्सुपूर्दगी विनंत्या");
        m.put("re.reassign.my_requests_none", "आपण कोणतीही पुनर्सुपूर्दगी विनंती केलेली नाही.");
        m.put("re.reassign.requested_at", "विनंती केली");
        m.put("re.reassign.decided_at", "निर्णय घेतला");
        m.put("re.reassign.withdraw_button", "मागे घ्या");
        m.put("re.reassign.from_officer", "कडून");
        m.put("re.reassign.to_officer", "कडे");
        m.put("re.reassign.approvals_title", "पुनर्सुपूर्दगी मंजुऱ्या");
        m.put("re.reassign.approvals_none", "आपल्या मंजुरीच्या प्रतीक्षेत कोणतीही विनंती नाही.");
        m.put("re.reassign.approve_button", "मंजूर करा");
        m.put("re.reassign.reject_button", "नाकारा");
        m.put("re.reassign.bulk_approve_button", "निवडलेल्या मंजूर करा");
        m.put("re.reassign.bulk_reject_button", "निवडलेल्या नाकारा");
        m.put("re.reassign.decision_comment_label", "शेरा");
        m.put("re.reassign.select_all", "सर्व निवडा");
        m.put("re.reassign.selected_count", "निवडलेल्या");
        m.put("re.reassign.bulk_partial_success",
              "काही नोंदी अद्ययावत करता आल्या नाहीत. उर्वरित नोंदी यशस्वीरित्या अद्ययावत झाल्या.");
        m.put("re.reassign.bulk_all_succeeded", "निवडलेल्या सर्व नोंदी अद्ययावत झाल्या.");
        m.put("re.pno.dashboard_title", "पथकाचा कामाचा भार");
        m.put("re.pno.officer_column", "अधिकारी");
        m.put("re.pno.workload_column", "प्रलंबित नोंदी");
        m.put("re.pno.total_active_records", "एकूण प्रलंबित नोंदी");
        m.put("re.pno.pending_approvals", "मंजुरीच्या प्रतीक्षेत विनंत्या");
        m.put("re.pno.no_officers", "आपल्या संस्थेसाठी कोणताही अधिकारी नोंदणीकृत नाही.");
        m.put("re.pno.workload_definition_notice", "प्रलंबित नोंदींमध्ये मसुदे व निकाली कामाचा समावेश नाही.");
        m.put("re.reassign.history_title", "पुनर्सुपूर्दगीचा इतिहास");
        m.put("re.reassign.history_none", "कोणतीही पुनर्सुपूर्दगी नोंदवलेली नाही.");
        m.put("re.reassign.history_from_filter", "यांच्याकडून सुपूर्द");
        m.put("re.reassign.history_to_filter", "यांच्याकडे सुपूर्द");
        m.put("re.reassign.history_date_from", "पासूनची तारीख");
        m.put("re.reassign.history_date_to", "पर्यंतची तारीख");
        m.put("re.reassign.history_trigger", "प्रकार");
        m.put("re.reassign.trigger.approved_request", "मंजूर विनंती");
        m.put("re.reassign.trigger.direct", "थेट पुनर्सुपूर्दगी");
        m.put("re.reassign.history_performed_by", "कार्यवाही करणारे");
        m.put("re.reassign.summary_outbound", "इतरांकडे सुपूर्द केलेल्या नोंदी");
        m.put("re.reassign.summary_inbound", "प्राप्त झालेल्या नोंदी");
        m.put("notification.reassign.out", "एक नोंद आपल्याकडून दुसऱ्याकडे सुपूर्द करण्यात आली आहे");
        m.put("notification.reassign.out.body",
              "आपण हाताळत असलेली तक्रार नोंद दुसऱ्या अधिकाऱ्याकडे सुपूर्द करण्यात आली आहे.");
        m.put("notification.reassign.in", "एक नोंद आपल्याकडे सुपूर्द करण्यात आली आहे");
        m.put("notification.reassign.in.body",
              "एक तक्रार नोंद आपल्याकडे सुपूर्द करण्यात आली आहे व आता तिकडे आपले लक्ष आवश्यक आहे.");
        m.put("re.reassign.status.pending", "मंजुरी प्रलंबित");
        m.put("re.reassign.status.approved", "मंजूर");
        m.put("re.reassign.status.rejected", "नाकारलेली");
        m.put("re.reassign.status.withdrawn", "मागे घेतलेली");
        m.put("re.reassign.error.unauthenticated", "ही कृती करण्यासाठी आपल्याला साइन इन करणे आवश्यक आहे.");
        m.put("re.reassign.error.not_permitted", "ही कृती करण्याची आपल्याला परवानगी नाही.");
        m.put("re.reassign.error.pno_only", "केवळ मुख्य नोडल अधिकारीच पुनर्सुपूर्दगी मंजूर करू शकतात.");
        m.put("re.reassign.error.cross_entity", "आपण केवळ आपल्या स्वतःच्या संस्थेच्या नोंदींवर कार्यवाही करू शकता.");
        m.put("re.reassign.error.entity_unresolved", "आपली संस्था निश्चित करता आली नाही.");
        m.put("re.reassign.error.entity_required", "संस्था नमूद करणे आवश्यक आहे.");
        m.put("re.reassign.error.target_required", "नोंद कोणत्या अधिकाऱ्याकडे जावी ते निवडा.");
        m.put("re.reassign.error.target_not_in_entity", "तो अधिकारी आपल्या संस्थेतील नाही.");
        m.put("re.reassign.error.target_inactive", "तो अधिकारी आता कार्यरत नाही व नोंदी स्वीकारू शकत नाही.");
        m.put("re.reassign.error.already_assigned", "ही नोंद त्या अधिकाऱ्याकडे यापूर्वीच सुपूर्द केलेली आहे.");
        m.put("re.reassign.error.already_pending", "या नोंदीसाठी एक पुनर्सुपूर्दगी विनंती यापूर्वीच प्रलंबित आहे.");
        m.put("re.reassign.error.already_decided", "या विनंतीवर यापूर्वीच निर्णय झाला आहे.");
        m.put("re.reassign.error.reason_too_short", "कृपया पुनर्सुपूर्दगीचे अधिक सविस्तर कारण द्या.");
        m.put("re.reassign.error.clarification_required", "स्पष्टीकरण रिकामे असू शकत नाही.");
        m.put("re.reassign.error.rejection_comment_required", "विनंती नाकारताना शेरा देणे आवश्यक आहे.");
        m.put("re.reassign.error.nothing_selected", "कमीत कमी एक नोंद निवडा.");
        m.put("re.reassign.error.bulk_limit_exceeded", "एकाच वेळी फार जास्त नोंदी निवडल्या गेल्या.");
        m.put("re.reassign.error.not_your_request", "आपण केवळ आपली स्वतःची विनंती मागे घेऊ शकता.");
        m.put("re.reassign.error.conflict", "ही नोंद अन्य कोणी बदलली आहे. पुन्हा लोड करून प्रयत्न करा.");
        m.put("re.reassign.error.not_found", "नोंद किंवा विनंती सापडली नाही.");
        m.put("re.reassign.error.invalid", "विनंतीवर प्रक्रिया करता आली नाही.");
        m.put("re.reassign.error.invalid_date", "दिलेली तारीख वैध नाही.");
        m.put("re.reassign.error.unexpected", "काहीतरी चूक झाली. कृपया पुन्हा प्रयत्न करा.");
        m.put("re.reassign.error.load_failed", "पुनर्सुपूर्दगीचा तपशील लोड करता आला नाही.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "নথি পুনরায় অর্পণ করুন");
        m.put("re.reassign.candidate_label", "কাকে অর্পণ করবেন");
        m.put("re.reassign.search_placeholder", "নাম, পদবি বা ইমেল দিয়ে খুঁজুন");
        m.put("re.reassign.workload_label", "বর্তমান কাজের চাপ");
        m.put("re.reassign.workload_column", "চলমান নথি");
        m.put("re.reassign.no_candidates", "এই নথি গ্রহণ করার জন্য আপনার সংস্থায় অন্য কোনো আধিকারিক উপলব্ধ নেই।");
        m.put("re.reassign.reason_label", "পুনরায় অর্পণের কারণ");
        m.put("re.reassign.submit_button", "অনুরোধ জমা দিন");
        m.put("re.reassign.cancel_button", "বাতিল করুন");
        m.put("re.reassign.role_filter_label", "ভূমিকা");
        m.put("re.reassign.role.nodal_officer", "নোডাল আধিকারিক");
        m.put("re.reassign.role.contact_person", "যোগাযোগকারী ব্যক্তি");
        m.put("re.reassign.role.pno", "প্রধান নোডাল আধিকারিক");
        m.put("re.reassign.territory_label", "এলাকা");
        m.put("re.reassign.reason_immutable_notice",
              "জমা দেওয়ার পর কারণ সম্পাদনা করা যাবে না। বিস্তারিত জানাতে একটি ব্যাখ্যা যোগ করুন।");
        m.put("re.reassign.clarification_title", "ব্যাখ্যা");
        m.put("re.reassign.clarification_add_placeholder", "একটি ব্যাখ্যা যোগ করুন…");
        m.put("re.reassign.clarification_add_button", "ব্যাখ্যা যোগ করুন");
        m.put("re.reassign.clarification_none", "কোনো ব্যাখ্যা যোগ করা হয়নি।");
        m.put("re.reassign.original_reason_label", "মূল কারণ");
        m.put("re.reassign.my_requests_title", "আমার পুনরায় অর্পণের অনুরোধ");
        m.put("re.reassign.my_requests_none", "আপনি কোনো পুনরায় অর্পণের অনুরোধ করেননি।");
        m.put("re.reassign.requested_at", "অনুরোধ করা হয়েছে");
        m.put("re.reassign.decided_at", "সিদ্ধান্ত হয়েছে");
        m.put("re.reassign.withdraw_button", "প্রত্যাহার করুন");
        m.put("re.reassign.from_officer", "থেকে");
        m.put("re.reassign.to_officer", "প্রতি");
        m.put("re.reassign.approvals_title", "পুনরায় অর্পণের অনুমোদন");
        m.put("re.reassign.approvals_none", "আপনার অনুমোদনের অপেক্ষায় কোনো অনুরোধ নেই।");
        m.put("re.reassign.approve_button", "অনুমোদন করুন");
        m.put("re.reassign.reject_button", "প্রত্যাখ্যান করুন");
        m.put("re.reassign.bulk_approve_button", "নির্বাচিতগুলি অনুমোদন করুন");
        m.put("re.reassign.bulk_reject_button", "নির্বাচিতগুলি প্রত্যাখ্যান করুন");
        m.put("re.reassign.decision_comment_label", "মন্তব্য");
        m.put("re.reassign.select_all", "সব নির্বাচন করুন");
        m.put("re.reassign.selected_count", "নির্বাচিত");
        m.put("re.reassign.bulk_partial_success",
              "কিছু নথি হালনাগাদ করা যায়নি। বাকি নথিগুলি সফলভাবে হালনাগাদ হয়েছে।");
        m.put("re.reassign.bulk_all_succeeded", "নির্বাচিত সব নথি হালনাগাদ করা হয়েছে।");
        m.put("re.pno.dashboard_title", "দলের কাজের চাপ");
        m.put("re.pno.officer_column", "আধিকারিক");
        m.put("re.pno.workload_column", "চলমান নথি");
        m.put("re.pno.total_active_records", "মোট চলমান নথি");
        m.put("re.pno.pending_approvals", "অনুমোদনের অপেক্ষায় অনুরোধ");
        m.put("re.pno.no_officers", "আপনার সংস্থার জন্য কোনো আধিকারিক নিবন্ধিত নেই।");
        m.put("re.pno.workload_definition_notice", "চলমান নথিতে খসড়া ও নিষ্পন্ন কাজ অন্তর্ভুক্ত নয়।");
        m.put("re.reassign.history_title", "পুনরায় অর্পণের ইতিহাস");
        m.put("re.reassign.history_none", "কোনো পুনরায় অর্পণ নথিভুক্ত হয়নি।");
        m.put("re.reassign.history_from_filter", "যাঁর কাছ থেকে অর্পিত");
        m.put("re.reassign.history_to_filter", "যাঁকে অর্পিত");
        m.put("re.reassign.history_date_from", "শুরুর তারিখ");
        m.put("re.reassign.history_date_to", "শেষ তারিখ");
        m.put("re.reassign.history_trigger", "ধরন");
        m.put("re.reassign.trigger.approved_request", "অনুমোদিত অনুরোধ");
        m.put("re.reassign.trigger.direct", "সরাসরি পুনরায় অর্পণ");
        m.put("re.reassign.history_performed_by", "পদক্ষেপ গ্রহণকারী");
        m.put("re.reassign.summary_outbound", "অন্যকে অর্পিত নথি");
        m.put("re.reassign.summary_inbound", "প্রাপ্ত নথি");
        m.put("notification.reassign.out", "একটি নথি আপনার কাছ থেকে অন্যকে অর্পণ করা হয়েছে");
        m.put("notification.reassign.out.body",
              "আপনি যে অভিযোগ নথিটি পরিচালনা করছিলেন, তা অন্য একজন আধিকারিককে অর্পণ করা হয়েছে।");
        m.put("notification.reassign.in", "একটি নথি আপনাকে অর্পণ করা হয়েছে");
        m.put("notification.reassign.in.body",
              "একটি অভিযোগ নথি আপনাকে অর্পণ করা হয়েছে এবং এখন এতে আপনার মনোযোগ প্রয়োজন।");
        m.put("re.reassign.status.pending", "অনুমোদনের অপেক্ষায়");
        m.put("re.reassign.status.approved", "অনুমোদিত");
        m.put("re.reassign.status.rejected", "প্রত্যাখ্যাত");
        m.put("re.reassign.status.withdrawn", "প্রত্যাহৃত");
        m.put("re.reassign.error.unauthenticated", "এই কাজটি করতে আপনাকে সাইন ইন করতে হবে।");
        m.put("re.reassign.error.not_permitted", "এই কাজটি করার অনুমতি আপনার নেই।");
        m.put("re.reassign.error.pno_only", "কেবল প্রধান নোডাল আধিকারিকই পুনরায় অর্পণ অনুমোদন করতে পারেন।");
        m.put("re.reassign.error.cross_entity", "আপনি কেবল নিজের সংস্থার নথির উপর পদক্ষেপ নিতে পারেন।");
        m.put("re.reassign.error.entity_unresolved", "আপনার সংস্থা নির্ধারণ করা যায়নি।");
        m.put("re.reassign.error.entity_required", "একটি সংস্থা উল্লেখ করা আবশ্যক।");
        m.put("re.reassign.error.target_required", "নথিটি যে আধিকারিকের কাছে যাবে তাঁকে নির্বাচন করুন।");
        m.put("re.reassign.error.target_not_in_entity", "সেই আধিকারিক আপনার সংস্থার নন।");
        m.put("re.reassign.error.target_inactive", "সেই আধিকারিক আর সক্রিয় নন এবং নথি গ্রহণ করতে পারবেন না।");
        m.put("re.reassign.error.already_assigned", "নথিটি ইতিমধ্যেই সেই আধিকারিককে অর্পণ করা হয়েছে।");
        m.put("re.reassign.error.already_pending", "এই নথির জন্য একটি পুনরায় অর্পণের অনুরোধ ইতিমধ্যেই বিচারাধীন।");
        m.put("re.reassign.error.already_decided", "এই অনুরোধের বিষয়ে ইতিমধ্যেই সিদ্ধান্ত নেওয়া হয়েছে।");
        m.put("re.reassign.error.reason_too_short", "অনুগ্রহ করে পুনরায় অর্পণের আরও বিশদ কারণ দিন।");
        m.put("re.reassign.error.clarification_required", "ব্যাখ্যা খালি রাখা যাবে না।");
        m.put("re.reassign.error.rejection_comment_required", "অনুরোধ প্রত্যাখ্যান করার সময় মন্তব্য আবশ্যক।");
        m.put("re.reassign.error.nothing_selected", "অন্তত একটি নথি নির্বাচন করুন।");
        m.put("re.reassign.error.bulk_limit_exceeded", "একবারে অনেক বেশি নথি নির্বাচন করা হয়েছে।");
        m.put("re.reassign.error.not_your_request", "আপনি কেবল নিজের অনুরোধ প্রত্যাহার করতে পারেন।");
        m.put("re.reassign.error.conflict", "এই নথিটি অন্য কেউ পরিবর্তন করেছেন। পুনরায় লোড করে আবার চেষ্টা করুন।");
        m.put("re.reassign.error.not_found", "নথি বা অনুরোধটি খুঁজে পাওয়া যায়নি।");
        m.put("re.reassign.error.invalid", "অনুরোধটি প্রক্রিয়া করা যায়নি।");
        m.put("re.reassign.error.invalid_date", "প্রদত্ত তারিখটি বৈধ নয়।");
        m.put("re.reassign.error.unexpected", "কিছু ভুল হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।");
        m.put("re.reassign.error.load_failed", "পুনরায় অর্পণের বিবরণ লোড করা যায়নি।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "రికార్డును తిరిగి కేటాయించండి");
        m.put("re.reassign.candidate_label", "ఎవరికి కేటాయించాలి");
        m.put("re.reassign.search_placeholder", "పేరు, హోదా లేదా ఇమెయిల్ ద్వారా వెతకండి");
        m.put("re.reassign.workload_label", "ప్రస్తుత పని భారం");
        m.put("re.reassign.workload_column", "పెండింగ్ రికార్డులు");
        m.put("re.reassign.no_candidates", "ఈ రికార్డును స్వీకరించడానికి మీ సంస్థలో వేరే అధికారి ఎవరూ అందుబాటులో లేరు.");
        m.put("re.reassign.reason_label", "తిరిగి కేటాయించడానికి కారణం");
        m.put("re.reassign.submit_button", "అభ్యర్థనను సమర్పించండి");
        m.put("re.reassign.cancel_button", "రద్దు చేయండి");
        m.put("re.reassign.role_filter_label", "పాత్ర");
        m.put("re.reassign.role.nodal_officer", "నోడల్ అధికారి");
        m.put("re.reassign.role.contact_person", "సంప్రదింపు వ్యక్తి");
        m.put("re.reassign.role.pno", "ప్రధాన నోడల్ అధికారి");
        m.put("re.reassign.territory_label", "ప్రాంతం");
        m.put("re.reassign.reason_immutable_notice",
              "సమర్పించిన తర్వాత కారణాన్ని సవరించలేరు. అదనపు సమాచారం ఇవ్వడానికి వివరణను నమోదు చేయండి.");
        m.put("re.reassign.clarification_title", "వివరణలు");
        m.put("re.reassign.clarification_add_placeholder", "ఒక వివరణను జోడించండి…");
        m.put("re.reassign.clarification_add_button", "వివరణను జోడించండి");
        m.put("re.reassign.clarification_none", "ఎటువంటి వివరణలు జోడించబడలేదు.");
        m.put("re.reassign.original_reason_label", "అసలు కారణం");
        m.put("re.reassign.my_requests_title", "నా తిరిగి కేటాయింపు అభ్యర్థనలు");
        m.put("re.reassign.my_requests_none", "మీరు ఎటువంటి తిరిగి కేటాయింపు అభ్యర్థనలను చేయలేదు.");
        m.put("re.reassign.requested_at", "అభ్యర్థించిన తేదీ");
        m.put("re.reassign.decided_at", "నిర్ణయించిన తేదీ");
        m.put("re.reassign.withdraw_button", "ఉపసంహరించండి");
        m.put("re.reassign.from_officer", "నుండి");
        m.put("re.reassign.to_officer", "కు");
        m.put("re.reassign.approvals_title", "తిరిగి కేటాయింపు ఆమోదాలు");
        m.put("re.reassign.approvals_none", "మీ ఆమోదం కోసం వేచి ఉన్న అభ్యర్థనలు ఏవీ లేవు.");
        m.put("re.reassign.approve_button", "ఆమోదించండి");
        m.put("re.reassign.reject_button", "తిరస్కరించండి");
        m.put("re.reassign.bulk_approve_button", "ఎంచుకున్నవి ఆమోదించండి");
        m.put("re.reassign.bulk_reject_button", "ఎంచుకున్నవి తిరస్కరించండి");
        m.put("re.reassign.decision_comment_label", "వ్యాఖ్య");
        m.put("re.reassign.select_all", "అన్నీ ఎంచుకోండి");
        m.put("re.reassign.selected_count", "ఎంచుకున్నవి");
        m.put("re.reassign.bulk_partial_success",
              "కొన్ని రికార్డులను నవీకరించలేకపోయాము. మిగిలిన రికార్డులు విజయవంతంగా నవీకరించబడ్డాయి.");
        m.put("re.reassign.bulk_all_succeeded", "ఎంచుకున్న అన్ని రికార్డులు నవీకరించబడ్డాయి.");
        m.put("re.pno.dashboard_title", "బృందం పని భారం");
        m.put("re.pno.officer_column", "అధికారి");
        m.put("re.pno.workload_column", "పెండింగ్ రికార్డులు");
        m.put("re.pno.total_active_records", "మొత్తం పెండింగ్ రికార్డులు");
        m.put("re.pno.pending_approvals", "ఆమోదం కోసం వేచి ఉన్న అభ్యర్థనలు");
        m.put("re.pno.no_officers", "మీ సంస్థ కోసం ఎటువంటి అధికారులు నమోదు కాలేదు.");
        m.put("re.pno.workload_definition_notice", "పెండింగ్ రికార్డులలో ముసాయిదాలు మరియు ముగిసిన పని ఉండవు.");
        m.put("re.reassign.history_title", "తిరిగి కేటాయింపు చరిత్ర");
        m.put("re.reassign.history_none", "ఎటువంటి తిరిగి కేటాయింపులు నమోదు కాలేదు.");
        m.put("re.reassign.history_from_filter", "ఎవరి నుండి కేటాయించారు");
        m.put("re.reassign.history_to_filter", "ఎవరికి కేటాయించారు");
        m.put("re.reassign.history_date_from", "ప్రారంభ తేదీ");
        m.put("re.reassign.history_date_to", "ముగింపు తేదీ");
        m.put("re.reassign.history_trigger", "రకం");
        m.put("re.reassign.trigger.approved_request", "ఆమోదించిన అభ్యర్థన");
        m.put("re.reassign.trigger.direct", "ప్రత్యక్ష తిరిగి కేటాయింపు");
        m.put("re.reassign.history_performed_by", "చర్య తీసుకున్నవారు");
        m.put("re.reassign.summary_outbound", "ఇతరులకు కేటాయించిన రికార్డులు");
        m.put("re.reassign.summary_inbound", "అందుకున్న రికార్డులు");
        m.put("notification.reassign.out", "ఒక రికార్డు మీ నుండి మరొకరికి కేటాయించబడింది");
        m.put("notification.reassign.out.body",
              "మీరు నిర్వహిస్తున్న ఫిర్యాదు రికార్డు మరొక అధికారికి కేటాయించబడింది.");
        m.put("notification.reassign.in", "ఒక రికార్డు మీకు కేటాయించబడింది");
        m.put("notification.reassign.in.body",
              "ఒక ఫిర్యాదు రికార్డు మీకు కేటాయించబడింది మరియు ఇప్పుడు దానికి మీ శ్రద్ధ అవసరం.");
        m.put("re.reassign.status.pending", "ఆమోదం పెండింగ్‌లో");
        m.put("re.reassign.status.approved", "ఆమోదించబడింది");
        m.put("re.reassign.status.rejected", "తిరస్కరించబడింది");
        m.put("re.reassign.status.withdrawn", "ఉపసంహరించబడింది");
        m.put("re.reassign.error.unauthenticated", "ఈ చర్యను చేయడానికి మీరు సైన్ ఇన్ చేయాలి.");
        m.put("re.reassign.error.not_permitted", "ఈ చర్యను చేయడానికి మీకు అనుమతి లేదు.");
        m.put("re.reassign.error.pno_only", "ప్రధాన నోడల్ అధికారి మాత్రమే తిరిగి కేటాయింపులను ఆమోదించగలరు.");
        m.put("re.reassign.error.cross_entity", "మీరు మీ స్వంత సంస్థకు చెందిన రికార్డులపై మాత్రమే చర్య తీసుకోగలరు.");
        m.put("re.reassign.error.entity_unresolved", "మీ సంస్థను నిర్ధారించలేకపోయాము.");
        m.put("re.reassign.error.entity_required", "ఒక సంస్థను పేర్కొనడం తప్పనిసరి.");
        m.put("re.reassign.error.target_required", "రికార్డును స్వీకరించాల్సిన అధికారిని ఎంచుకోండి.");
        m.put("re.reassign.error.target_not_in_entity", "ఆ అధికారి మీ సంస్థకు చెందినవారు కాదు.");
        m.put("re.reassign.error.target_inactive", "ఆ అధికారి ఇకపై చురుకుగా లేరు మరియు రికార్డులను స్వీకరించలేరు.");
        m.put("re.reassign.error.already_assigned", "రికార్డు ఇప్పటికే ఆ అధికారికి కేటాయించబడింది.");
        m.put("re.reassign.error.already_pending", "ఈ రికార్డు కోసం ఒక తిరిగి కేటాయింపు అభ్యర్థన ఇప్పటికే పెండింగ్‌లో ఉంది.");
        m.put("re.reassign.error.already_decided", "ఈ అభ్యర్థనపై ఇప్పటికే నిర్ణయం తీసుకోబడింది.");
        m.put("re.reassign.error.reason_too_short", "దయచేసి తిరిగి కేటాయింపుకు మరింత వివరమైన కారణాన్ని ఇవ్వండి.");
        m.put("re.reassign.error.clarification_required", "వివరణ ఖాళీగా ఉండకూడదు.");
        m.put("re.reassign.error.rejection_comment_required", "అభ్యర్థనను తిరస్కరించేటప్పుడు వ్యాఖ్య తప్పనిసరి.");
        m.put("re.reassign.error.nothing_selected", "కనీసం ఒక రికార్డును ఎంచుకోండి.");
        m.put("re.reassign.error.bulk_limit_exceeded", "ఒకేసారి చాలా ఎక్కువ రికార్డులు ఎంచుకోబడ్డాయి.");
        m.put("re.reassign.error.not_your_request", "మీరు మీ స్వంత అభ్యర్థనను మాత్రమే ఉపసంహరించగలరు.");
        m.put("re.reassign.error.conflict", "ఈ రికార్డును వేరొకరు మార్చారు. మళ్లీ లోడ్ చేసి ప్రయత్నించండి.");
        m.put("re.reassign.error.not_found", "రికార్డు లేదా అభ్యర్థన కనుగొనబడలేదు.");
        m.put("re.reassign.error.invalid", "అభ్యర్థనను ప్రాసెస్ చేయలేకపోయాము.");
        m.put("re.reassign.error.invalid_date", "ఇచ్చిన తేదీ చెల్లదు.");
        m.put("re.reassign.error.unexpected", "ఏదో తప్పు జరిగింది. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("re.reassign.error.load_failed", "తిరిగి కేటాయింపు వివరాలను లోడ్ చేయలేకపోయాము.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "பதிவேட்டை மீண்டும் ஒப்படைக்கவும்");
        m.put("re.reassign.candidate_label", "யாருக்கு ஒப்படைக்க வேண்டும்");
        m.put("re.reassign.search_placeholder", "பெயர், பதவி அல்லது மின்னஞ்சல் மூலம் தேடுங்கள்");
        m.put("re.reassign.workload_label", "தற்போதைய பணிச்சுமை");
        m.put("re.reassign.workload_column", "நிலுவையிலுள்ள பதிவேடுகள்");
        m.put("re.reassign.no_candidates", "இந்தப் பதிவேட்டைப் பெற உங்கள் நிறுவனத்தில் வேறு எந்த அதிகாரியும் இல்லை.");
        m.put("re.reassign.reason_label", "மீண்டும் ஒப்படைப்பதற்கான காரணம்");
        m.put("re.reassign.submit_button", "கோரிக்கையைச் சமர்ப்பிக்கவும்");
        m.put("re.reassign.cancel_button", "ரத்து செய்");
        m.put("re.reassign.role_filter_label", "பொறுப்பு");
        m.put("re.reassign.role.nodal_officer", "நோடல் அதிகாரி");
        m.put("re.reassign.role.contact_person", "தொடர்பு நபர்");
        m.put("re.reassign.role.pno", "தலைமை நோடல் அதிகாரி");
        m.put("re.reassign.territory_label", "பகுதி");
        m.put("re.reassign.reason_immutable_notice",
              "சமர்ப்பித்த பிறகு காரணத்தைத் திருத்த முடியாது. மேலும் விவரம் சேர்க்க ஒரு விளக்கத்தைப் பதிவு செய்யுங்கள்.");
        m.put("re.reassign.clarification_title", "விளக்கங்கள்");
        m.put("re.reassign.clarification_add_placeholder", "ஒரு விளக்கத்தைச் சேர்க்கவும்…");
        m.put("re.reassign.clarification_add_button", "விளக்கத்தைச் சேர்");
        m.put("re.reassign.clarification_none", "எந்த விளக்கமும் சேர்க்கப்படவில்லை.");
        m.put("re.reassign.original_reason_label", "அசல் காரணம்");
        m.put("re.reassign.my_requests_title", "எனது மீள்ஒப்படைப்புக் கோரிக்கைகள்");
        m.put("re.reassign.my_requests_none", "நீங்கள் எந்த மீள்ஒப்படைப்புக் கோரிக்கையையும் முன்வைக்கவில்லை.");
        m.put("re.reassign.requested_at", "கோரப்பட்டது");
        m.put("re.reassign.decided_at", "முடிவு எடுக்கப்பட்டது");
        m.put("re.reassign.withdraw_button", "திரும்பப் பெறு");
        m.put("re.reassign.from_officer", "இடமிருந்து");
        m.put("re.reassign.to_officer", "இடம்");
        m.put("re.reassign.approvals_title", "மீள்ஒப்படைப்பு ஒப்புதல்கள்");
        m.put("re.reassign.approvals_none", "உங்கள் ஒப்புதலுக்குக் காத்திருக்கும் கோரிக்கைகள் இல்லை.");
        m.put("re.reassign.approve_button", "ஒப்புதல் அளி");
        m.put("re.reassign.reject_button", "நிராகரி");
        m.put("re.reassign.bulk_approve_button", "தேர்ந்தெடுத்தவற்றை ஒப்புதல் அளி");
        m.put("re.reassign.bulk_reject_button", "தேர்ந்தெடுத்தவற்றை நிராகரி");
        m.put("re.reassign.decision_comment_label", "கருத்து");
        m.put("re.reassign.select_all", "அனைத்தையும் தேர்ந்தெடு");
        m.put("re.reassign.selected_count", "தேர்ந்தெடுக்கப்பட்டவை");
        m.put("re.reassign.bulk_partial_success",
              "சில பதிவேடுகளைப் புதுப்பிக்க முடியவில்லை. மீதமுள்ள பதிவேடுகள் வெற்றிகரமாகப் புதுப்பிக்கப்பட்டன.");
        m.put("re.reassign.bulk_all_succeeded", "தேர்ந்தெடுக்கப்பட்ட அனைத்துப் பதிவேடுகளும் புதுப்பிக்கப்பட்டன.");
        m.put("re.pno.dashboard_title", "குழுவின் பணிச்சுமை");
        m.put("re.pno.officer_column", "அதிகாரி");
        m.put("re.pno.workload_column", "நிலுவையிலுள்ள பதிவேடுகள்");
        m.put("re.pno.total_active_records", "மொத்த நிலுவைப் பதிவேடுகள்");
        m.put("re.pno.pending_approvals", "ஒப்புதலுக்குக் காத்திருக்கும் கோரிக்கைகள்");
        m.put("re.pno.no_officers", "உங்கள் நிறுவனத்திற்கு எந்த அதிகாரியும் பதிவு செய்யப்படவில்லை.");
        m.put("re.pno.workload_definition_notice", "நிலுவைப் பதிவேடுகளில் வரைவுகளும் முடிக்கப்பட்ட பணிகளும் அடங்கவில்லை.");
        m.put("re.reassign.history_title", "மீள்ஒப்படைப்பு வரலாறு");
        m.put("re.reassign.history_none", "எந்த மீள்ஒப்படைப்பும் பதிவு செய்யப்படவில்லை.");
        m.put("re.reassign.history_from_filter", "யாரிடமிருந்து ஒப்படைக்கப்பட்டது");
        m.put("re.reassign.history_to_filter", "யாருக்கு ஒப்படைக்கப்பட்டது");
        m.put("re.reassign.history_date_from", "தொடக்க தேதி");
        m.put("re.reassign.history_date_to", "இறுதி தேதி");
        m.put("re.reassign.history_trigger", "வகை");
        m.put("re.reassign.trigger.approved_request", "ஒப்புதல் பெற்ற கோரிக்கை");
        m.put("re.reassign.trigger.direct", "நேரடி மீள்ஒப்படைப்பு");
        m.put("re.reassign.history_performed_by", "நடவடிக்கை எடுத்தவர்");
        m.put("re.reassign.summary_outbound", "பிறருக்கு ஒப்படைக்கப்பட்ட பதிவேடுகள்");
        m.put("re.reassign.summary_inbound", "பெறப்பட்ட பதிவேடுகள்");
        m.put("notification.reassign.out", "ஒரு பதிவேடு உங்களிடமிருந்து வேறு ஒருவருக்கு ஒப்படைக்கப்பட்டுள்ளது");
        m.put("notification.reassign.out.body",
              "நீங்கள் கையாண்டு வந்த புகார் பதிவேடு மற்றொரு அதிகாரிக்கு ஒப்படைக்கப்பட்டுள்ளது.");
        m.put("notification.reassign.in", "ஒரு பதிவேடு உங்களுக்கு ஒப்படைக்கப்பட்டுள்ளது");
        m.put("notification.reassign.in.body",
              "ஒரு புகார் பதிவேடு உங்களுக்கு ஒப்படைக்கப்பட்டுள்ளது, இப்போது அதற்கு உங்கள் கவனம் தேவை.");
        m.put("re.reassign.status.pending", "ஒப்புதல் நிலுவையில்");
        m.put("re.reassign.status.approved", "ஒப்புதல் அளிக்கப்பட்டது");
        m.put("re.reassign.status.rejected", "நிராகரிக்கப்பட்டது");
        m.put("re.reassign.status.withdrawn", "திரும்பப் பெறப்பட்டது");
        m.put("re.reassign.error.unauthenticated", "இந்தச் செயலைச் செய்ய நீங்கள் உள்நுழைய வேண்டும்.");
        m.put("re.reassign.error.not_permitted", "இந்தச் செயலைச் செய்ய உங்களுக்கு அனுமதி இல்லை.");
        m.put("re.reassign.error.pno_only", "தலைமை நோடல் அதிகாரி மட்டுமே மீள்ஒப்படைப்புகளுக்கு ஒப்புதல் அளிக்க முடியும்.");
        m.put("re.reassign.error.cross_entity", "உங்கள் சொந்த நிறுவனத்தைச் சேர்ந்த பதிவேடுகளில் மட்டுமே நீங்கள் நடவடிக்கை எடுக்க முடியும்.");
        m.put("re.reassign.error.entity_unresolved", "உங்கள் நிறுவனத்தை உறுதிப்படுத்த முடியவில்லை.");
        m.put("re.reassign.error.entity_required", "ஒரு நிறுவனத்தைக் குறிப்பிட வேண்டும்.");
        m.put("re.reassign.error.target_required", "பதிவேட்டைப் பெற வேண்டிய அதிகாரியைத் தேர்ந்தெடுக்கவும்.");
        m.put("re.reassign.error.target_not_in_entity", "அந்த அதிகாரி உங்கள் நிறுவனத்தைச் சேர்ந்தவர் அல்ல.");
        m.put("re.reassign.error.target_inactive", "அந்த அதிகாரி இப்போது பணியில் இல்லை, பதிவேடுகளைப் பெற முடியாது.");
        m.put("re.reassign.error.already_assigned", "பதிவேடு ஏற்கெனவே அந்த அதிகாரிக்கு ஒப்படைக்கப்பட்டுள்ளது.");
        m.put("re.reassign.error.already_pending", "இந்தப் பதிவேட்டுக்கு ஒரு மீள்ஒப்படைப்புக் கோரிக்கை ஏற்கெனவே நிலுவையில் உள்ளது.");
        m.put("re.reassign.error.already_decided", "இந்தக் கோரிக்கையில் ஏற்கெனவே முடிவு எடுக்கப்பட்டுவிட்டது.");
        m.put("re.reassign.error.reason_too_short", "மீள்ஒப்படைப்புக்கு இன்னும் விரிவான காரணத்தைத் தெரிவிக்கவும்.");
        m.put("re.reassign.error.clarification_required", "விளக்கம் காலியாக இருக்கக் கூடாது.");
        m.put("re.reassign.error.rejection_comment_required", "கோரிக்கையை நிராகரிக்கும்போது கருத்து அவசியம்.");
        m.put("re.reassign.error.nothing_selected", "குறைந்தது ஒரு பதிவேட்டையாவது தேர்ந்தெடுக்கவும்.");
        m.put("re.reassign.error.bulk_limit_exceeded", "ஒரே நேரத்தில் மிக அதிகமான பதிவேடுகள் தேர்ந்தெடுக்கப்பட்டன.");
        m.put("re.reassign.error.not_your_request", "உங்கள் சொந்தக் கோரிக்கையை மட்டுமே நீங்கள் திரும்பப் பெற முடியும்.");
        m.put("re.reassign.error.conflict", "இந்தப் பதிவேட்டை வேறு ஒருவர் மாற்றியுள்ளார். மீண்டும் ஏற்றி முயற்சிக்கவும்.");
        m.put("re.reassign.error.not_found", "பதிவேடு அல்லது கோரிக்கையைக் கண்டுபிடிக்க முடியவில்லை.");
        m.put("re.reassign.error.invalid", "கோரிக்கையைச் செயலாக்க முடியவில்லை.");
        m.put("re.reassign.error.invalid_date", "அளிக்கப்பட்ட தேதி செல்லாது.");
        m.put("re.reassign.error.unexpected", "ஏதோ தவறு நடந்தது. மீண்டும் முயற்சிக்கவும்.");
        m.put("re.reassign.error.load_failed", "மீள்ஒப்படைப்பு விவரங்களை ஏற்ற முடியவில்லை.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "રેકોર્ડ ફરીથી સોંપો");
        m.put("re.reassign.candidate_label", "કોને સોંપવો");
        m.put("re.reassign.search_placeholder", "નામ, હોદ્દો કે ઈમેલ દ્વારા શોધો");
        m.put("re.reassign.workload_label", "હાલનું કામનું ભારણ");
        m.put("re.reassign.workload_column", "બાકી રેકોર્ડ");
        m.put("re.reassign.no_candidates", "આ રેકોર્ડ સ્વીકારવા માટે તમારી સંસ્થામાં અન્ય કોઈ અધિકારી ઉપલબ્ધ નથી.");
        m.put("re.reassign.reason_label", "ફરીથી સોંપવાનું કારણ");
        m.put("re.reassign.submit_button", "વિનંતી રજૂ કરો");
        m.put("re.reassign.cancel_button", "રદ કરો");
        m.put("re.reassign.role_filter_label", "ભૂમિકા");
        m.put("re.reassign.role.nodal_officer", "નોડલ અધિકારી");
        m.put("re.reassign.role.contact_person", "સંપર્ક વ્યક્તિ");
        m.put("re.reassign.role.pno", "મુખ્ય નોડલ અધિકારી");
        m.put("re.reassign.territory_label", "વિસ્તાર");
        m.put("re.reassign.reason_immutable_notice",
              "રજૂ કર્યા પછી કારણમાં ફેરફાર થઈ શકતો નથી. વધુ માહિતી આપવા માટે સ્પષ્ટતા નોંધો.");
        m.put("re.reassign.clarification_title", "સ્પષ્ટતાઓ");
        m.put("re.reassign.clarification_add_placeholder", "એક સ્પષ્ટતા ઉમેરો…");
        m.put("re.reassign.clarification_add_button", "સ્પષ્ટતા ઉમેરો");
        m.put("re.reassign.clarification_none", "કોઈ સ્પષ્ટતા ઉમેરવામાં આવી નથી.");
        m.put("re.reassign.original_reason_label", "મૂળ કારણ");
        m.put("re.reassign.my_requests_title", "મારી પુનઃસોંપણી વિનંતીઓ");
        m.put("re.reassign.my_requests_none", "તમે કોઈ પુનઃસોંપણી વિનંતી કરી નથી.");
        m.put("re.reassign.requested_at", "વિનંતી કરી");
        m.put("re.reassign.decided_at", "નિર્ણય લેવાયો");
        m.put("re.reassign.withdraw_button", "પાછી ખેંચો");
        m.put("re.reassign.from_officer", "તરફથી");
        m.put("re.reassign.to_officer", "તરફ");
        m.put("re.reassign.approvals_title", "પુનઃસોંપણી મંજૂરીઓ");
        m.put("re.reassign.approvals_none", "તમારી મંજૂરીની પ્રતીક્ષામાં કોઈ વિનંતી નથી.");
        m.put("re.reassign.approve_button", "મંજૂર કરો");
        m.put("re.reassign.reject_button", "નકારો");
        m.put("re.reassign.bulk_approve_button", "પસંદ કરેલી મંજૂર કરો");
        m.put("re.reassign.bulk_reject_button", "પસંદ કરેલી નકારો");
        m.put("re.reassign.decision_comment_label", "ટિપ્પણી");
        m.put("re.reassign.select_all", "બધું પસંદ કરો");
        m.put("re.reassign.selected_count", "પસંદ કરેલા");
        m.put("re.reassign.bulk_partial_success",
              "કેટલાક રેકોર્ડ સુધારી શકાયા નથી. બાકીના રેકોર્ડ સફળતાપૂર્વક સુધારવામાં આવ્યા.");
        m.put("re.reassign.bulk_all_succeeded", "પસંદ કરેલા બધા રેકોર્ડ સુધારવામાં આવ્યા.");
        m.put("re.pno.dashboard_title", "ટીમનું કામનું ભારણ");
        m.put("re.pno.officer_column", "અધિકારી");
        m.put("re.pno.workload_column", "બાકી રેકોર્ડ");
        m.put("re.pno.total_active_records", "કુલ બાકી રેકોર્ડ");
        m.put("re.pno.pending_approvals", "મંજૂરીની પ્રતીક્ષામાં વિનંતીઓ");
        m.put("re.pno.no_officers", "તમારી સંસ્થા માટે કોઈ અધિકારી નોંધાયેલ નથી.");
        m.put("re.pno.workload_definition_notice", "બાકી રેકોર્ડમાં ડ્રાફ્ટ અને પૂર્ણ થયેલું કામ સામેલ નથી.");
        m.put("re.reassign.history_title", "પુનઃસોંપણીનો ઇતિહાસ");
        m.put("re.reassign.history_none", "કોઈ પુનઃસોંપણી નોંધાયેલ નથી.");
        m.put("re.reassign.history_from_filter", "કોના તરફથી સોંપાયો");
        m.put("re.reassign.history_to_filter", "કોને સોંપાયો");
        m.put("re.reassign.history_date_from", "શરૂ તારીખ");
        m.put("re.reassign.history_date_to", "અંતિમ તારીખ");
        m.put("re.reassign.history_trigger", "પ્રકાર");
        m.put("re.reassign.trigger.approved_request", "મંજૂર થયેલી વિનંતી");
        m.put("re.reassign.trigger.direct", "સીધી પુનઃસોંપણી");
        m.put("re.reassign.history_performed_by", "કાર્યવાહી કરનાર");
        m.put("re.reassign.summary_outbound", "અન્યને સોંપાયેલા રેકોર્ડ");
        m.put("re.reassign.summary_inbound", "પ્રાપ્ત થયેલા રેકોર્ડ");
        m.put("notification.reassign.out", "એક રેકોર્ડ તમારી પાસેથી બીજા કોઈને સોંપવામાં આવ્યો છે");
        m.put("notification.reassign.out.body",
              "તમે સંભાળી રહ્યા હતા તે ફરિયાદ રેકોર્ડ અન્ય અધિકારીને સોંપવામાં આવ્યો છે.");
        m.put("notification.reassign.in", "એક રેકોર્ડ તમને સોંપવામાં આવ્યો છે");
        m.put("notification.reassign.in.body",
              "એક ફરિયાદ રેકોર્ડ તમને સોંપવામાં આવ્યો છે અને હવે તેના પર તમારું ધ્યાન જરૂરી છે.");
        m.put("re.reassign.status.pending", "મંજૂરી બાકી");
        m.put("re.reassign.status.approved", "મંજૂર");
        m.put("re.reassign.status.rejected", "નકારેલી");
        m.put("re.reassign.status.withdrawn", "પાછી ખેંચેલી");
        m.put("re.reassign.error.unauthenticated", "આ કાર્ય કરવા માટે તમારે સાઇન ઇન કરવું પડશે.");
        m.put("re.reassign.error.not_permitted", "તમને આ કાર્ય કરવાની પરવાનગી નથી.");
        m.put("re.reassign.error.pno_only", "ફક્ત મુખ્ય નોડલ અધિકારી જ પુનઃસોંપણી મંજૂર કરી શકે છે.");
        m.put("re.reassign.error.cross_entity", "તમે ફક્ત તમારી પોતાની સંસ્થાના રેકોર્ડ પર કાર્યવાહી કરી શકો છો.");
        m.put("re.reassign.error.entity_unresolved", "તમારી સંસ્થા નક્કી થઈ શકી નથી.");
        m.put("re.reassign.error.entity_required", "સંસ્થા દર્શાવવી જરૂરી છે.");
        m.put("re.reassign.error.target_required", "રેકોર્ડ કોને મળવો જોઈએ તે અધિકારી પસંદ કરો.");
        m.put("re.reassign.error.target_not_in_entity", "તે અધિકારી તમારી સંસ્થાના નથી.");
        m.put("re.reassign.error.target_inactive", "તે અધિકારી હવે સક્રિય નથી અને રેકોર્ડ સ્વીકારી શકતા નથી.");
        m.put("re.reassign.error.already_assigned", "રેકોર્ડ પહેલેથી જ તે અધિકારીને સોંપાયેલ છે.");
        m.put("re.reassign.error.already_pending", "આ રેકોર્ડ માટે એક પુનઃસોંપણી વિનંતી પહેલેથી જ બાકી છે.");
        m.put("re.reassign.error.already_decided", "આ વિનંતી પર પહેલેથી જ નિર્ણય લેવાઈ ગયો છે.");
        m.put("re.reassign.error.reason_too_short", "કૃપા કરીને પુનઃસોંપણી માટે વધુ વિગતવાર કારણ આપો.");
        m.put("re.reassign.error.clarification_required", "સ્પષ્ટતા ખાલી હોઈ શકતી નથી.");
        m.put("re.reassign.error.rejection_comment_required", "વિનંતી નકારતી વખતે ટિપ્પણી જરૂરી છે.");
        m.put("re.reassign.error.nothing_selected", "ઓછામાં ઓછો એક રેકોર્ડ પસંદ કરો.");
        m.put("re.reassign.error.bulk_limit_exceeded", "એક જ સમયે ઘણા વધુ રેકોર્ડ પસંદ કરાયા.");
        m.put("re.reassign.error.not_your_request", "તમે ફક્ત તમારી પોતાની વિનંતી પાછી ખેંચી શકો છો.");
        m.put("re.reassign.error.conflict", "આ રેકોર્ડ બીજા કોઈએ બદલ્યો છે. ફરીથી લોડ કરીને પ્રયાસ કરો.");
        m.put("re.reassign.error.not_found", "રેકોર્ડ કે વિનંતી મળી નથી.");
        m.put("re.reassign.error.invalid", "વિનંતી પર પ્રક્રિયા થઈ શકી નથી.");
        m.put("re.reassign.error.invalid_date", "આપેલી તારીખ માન્ય નથી.");
        m.put("re.reassign.error.unexpected", "કંઈક ખોટું થયું. કૃપા કરીને ફરીથી પ્રયાસ કરો.");
        m.put("re.reassign.error.load_failed", "પુનઃસોંપણીની વિગતો લોડ થઈ શકી નથી.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "ریکارڈ دوبارہ سونپیں");
        m.put("re.reassign.candidate_label", "کس کو سونپنا ہے");
        m.put("re.reassign.search_placeholder", "نام، عہدے یا ای میل سے تلاش کریں");
        m.put("re.reassign.workload_label", "موجودہ کام کا بوجھ");
        m.put("re.reassign.workload_column", "زیرِ التوا ریکارڈ");
        m.put("re.reassign.no_candidates", "آپ کے ادارے میں کوئی دوسرا افسر یہ ریکارڈ لینے کے لیے دستیاب نہیں ہے۔");
        m.put("re.reassign.reason_label", "دوبارہ سونپنے کی وجہ");
        m.put("re.reassign.submit_button", "درخواست جمع کرائیں");
        m.put("re.reassign.cancel_button", "منسوخ کریں");
        m.put("re.reassign.role_filter_label", "کردار");
        m.put("re.reassign.role.nodal_officer", "نوڈل افسر");
        m.put("re.reassign.role.contact_person", "رابطہ کار");
        m.put("re.reassign.role.pno", "پرنسپل نوڈل افسر");
        m.put("re.reassign.territory_label", "علاقہ");
        m.put("re.reassign.reason_immutable_notice",
              "جمع کرانے کے بعد وجہ میں ترمیم نہیں ہو سکتی۔ مزید تفصیل کے لیے وضاحت درج کریں۔");
        m.put("re.reassign.clarification_title", "وضاحتیں");
        m.put("re.reassign.clarification_add_placeholder", "ایک وضاحت شامل کریں…");
        m.put("re.reassign.clarification_add_button", "وضاحت شامل کریں");
        m.put("re.reassign.clarification_none", "کوئی وضاحت شامل نہیں کی گئی۔");
        m.put("re.reassign.original_reason_label", "اصل وجہ");
        m.put("re.reassign.my_requests_title", "میری دوبارہ سونپنے کی درخواستیں");
        m.put("re.reassign.my_requests_none", "آپ نے دوبارہ سونپنے کی کوئی درخواست نہیں دی۔");
        m.put("re.reassign.requested_at", "درخواست کی گئی");
        m.put("re.reassign.decided_at", "فیصلہ ہوا");
        m.put("re.reassign.withdraw_button", "واپس لیں");
        m.put("re.reassign.from_officer", "سے");
        m.put("re.reassign.to_officer", "کو");
        m.put("re.reassign.approvals_title", "دوبارہ سونپنے کی منظوریاں");
        m.put("re.reassign.approvals_none", "آپ کی منظوری کے منتظر کوئی درخواست نہیں ہے۔");
        m.put("re.reassign.approve_button", "منظور کریں");
        m.put("re.reassign.reject_button", "مسترد کریں");
        m.put("re.reassign.bulk_approve_button", "منتخب شدہ منظور کریں");
        m.put("re.reassign.bulk_reject_button", "منتخب شدہ مسترد کریں");
        m.put("re.reassign.decision_comment_label", "تبصرہ");
        m.put("re.reassign.select_all", "سب منتخب کریں");
        m.put("re.reassign.selected_count", "منتخب شدہ");
        m.put("re.reassign.bulk_partial_success",
              "کچھ ریکارڈ اپ ڈیٹ نہیں ہو سکے۔ باقی ریکارڈ کامیابی سے اپ ڈیٹ کر دیے گئے۔");
        m.put("re.reassign.bulk_all_succeeded", "تمام منتخب شدہ ریکارڈ اپ ڈیٹ کر دیے گئے۔");
        m.put("re.pno.dashboard_title", "ٹیم کا کام کا بوجھ");
        m.put("re.pno.officer_column", "افسر");
        m.put("re.pno.workload_column", "زیرِ التوا ریکارڈ");
        m.put("re.pno.total_active_records", "کل زیرِ التوا ریکارڈ");
        m.put("re.pno.pending_approvals", "منظوری کے منتظر درخواستیں");
        m.put("re.pno.no_officers", "آپ کے ادارے کے لیے کوئی افسر رجسٹرڈ نہیں ہے۔");
        m.put("re.pno.workload_definition_notice", "زیرِ التوا ریکارڈ میں مسودے اور مکمل شدہ کام شامل نہیں ہیں۔");
        m.put("re.reassign.history_title", "دوبارہ سونپنے کی تاریخ");
        m.put("re.reassign.history_none", "دوبارہ سونپنے کا کوئی اندراج نہیں ہے۔");
        m.put("re.reassign.history_from_filter", "جس سے سونپا گیا");
        m.put("re.reassign.history_to_filter", "جسے سونپا گیا");
        m.put("re.reassign.history_date_from", "ابتدائی تاریخ");
        m.put("re.reassign.history_date_to", "آخری تاریخ");
        m.put("re.reassign.history_trigger", "قسم");
        m.put("re.reassign.trigger.approved_request", "منظور شدہ درخواست");
        m.put("re.reassign.trigger.direct", "براہِ راست دوبارہ سونپنا");
        m.put("re.reassign.history_performed_by", "کارروائی کرنے والا");
        m.put("re.reassign.summary_outbound", "دوسروں کو سونپے گئے ریکارڈ");
        m.put("re.reassign.summary_inbound", "موصول شدہ ریکارڈ");
        m.put("notification.reassign.out", "ایک ریکارڈ آپ سے لے کر کسی اور کو سونپ دیا گیا ہے");
        m.put("notification.reassign.out.body",
              "جو شکایت ریکارڈ آپ سنبھال رہے تھے، وہ کسی دوسرے افسر کو سونپ دیا گیا ہے۔");
        m.put("notification.reassign.in", "ایک ریکارڈ آپ کو سونپا گیا ہے");
        m.put("notification.reassign.in.body",
              "ایک شکایت ریکارڈ آپ کو سونپا گیا ہے اور اب اس پر آپ کی توجہ درکار ہے۔");
        m.put("re.reassign.status.pending", "منظوری زیرِ التوا");
        m.put("re.reassign.status.approved", "منظور شدہ");
        m.put("re.reassign.status.rejected", "مسترد شدہ");
        m.put("re.reassign.status.withdrawn", "واپس لی گئی");
        m.put("re.reassign.error.unauthenticated", "یہ کام کرنے کے لیے آپ کو سائن اِن کرنا ہوگا۔");
        m.put("re.reassign.error.not_permitted", "آپ کو یہ کام کرنے کی اجازت نہیں ہے۔");
        m.put("re.reassign.error.pno_only", "صرف پرنسپل نوڈل افسر دوبارہ سونپنے کی منظوری دے سکتے ہیں۔");
        m.put("re.reassign.error.cross_entity", "آپ صرف اپنے ہی ادارے کے ریکارڈ پر کارروائی کر سکتے ہیں۔");
        m.put("re.reassign.error.entity_unresolved", "آپ کا ادارہ متعین نہیں ہو سکا۔");
        m.put("re.reassign.error.entity_required", "ادارہ بتانا لازمی ہے۔");
        m.put("re.reassign.error.target_required", "وہ افسر منتخب کریں جسے ریکارڈ ملنا چاہیے۔");
        m.put("re.reassign.error.target_not_in_entity", "وہ افسر آپ کے ادارے سے تعلق نہیں رکھتا۔");
        m.put("re.reassign.error.target_inactive", "وہ افسر اب فعال نہیں ہے اور ریکارڈ نہیں لے سکتا۔");
        m.put("re.reassign.error.already_assigned", "ریکارڈ پہلے ہی اُس افسر کو سونپا جا چکا ہے۔");
        m.put("re.reassign.error.already_pending", "اس ریکارڈ کے لیے دوبارہ سونپنے کی ایک درخواست پہلے ہی زیرِ التوا ہے۔");
        m.put("re.reassign.error.already_decided", "اس درخواست پر پہلے ہی فیصلہ ہو چکا ہے۔");
        m.put("re.reassign.error.reason_too_short", "براہِ کرم دوبارہ سونپنے کی زیادہ تفصیلی وجہ بتائیں۔");
        m.put("re.reassign.error.clarification_required", "وضاحت خالی نہیں ہو سکتی۔");
        m.put("re.reassign.error.rejection_comment_required", "درخواست مسترد کرتے وقت تبصرہ لازمی ہے۔");
        m.put("re.reassign.error.nothing_selected", "کم از کم ایک ریکارڈ منتخب کریں۔");
        m.put("re.reassign.error.bulk_limit_exceeded", "ایک ساتھ بہت زیادہ ریکارڈ منتخب کر لیے گئے۔");
        m.put("re.reassign.error.not_your_request", "آپ صرف اپنی درخواست واپس لے سکتے ہیں۔");
        m.put("re.reassign.error.conflict", "یہ ریکارڈ کسی اور نے تبدیل کر دیا ہے۔ دوبارہ لوڈ کر کے کوشش کریں۔");
        m.put("re.reassign.error.not_found", "ریکارڈ یا درخواست نہیں مل سکی۔");
        m.put("re.reassign.error.invalid", "درخواست پر کارروائی نہیں ہو سکی۔");
        m.put("re.reassign.error.invalid_date", "دی گئی تاریخ درست نہیں ہے۔");
        m.put("re.reassign.error.unexpected", "کچھ غلط ہو گیا۔ براہِ کرم دوبارہ کوشش کریں۔");
        m.put("re.reassign.error.load_failed", "دوبارہ سونپنے کی تفصیلات لوڈ نہیں ہو سکیں۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "ದಾಖಲೆಯನ್ನು ಮರುಹಂಚಿಕೆ ಮಾಡಿ");
        m.put("re.reassign.candidate_label", "ಯಾರಿಗೆ ಹಂಚಬೇಕು");
        m.put("re.reassign.search_placeholder", "ಹೆಸರು, ಹುದ್ದೆ ಅಥವಾ ಇಮೇಲ್ ಮೂಲಕ ಹುಡುಕಿ");
        m.put("re.reassign.workload_label", "ಪ್ರಸ್ತುತ ಕೆಲಸದ ಹೊರೆ");
        m.put("re.reassign.workload_column", "ಬಾಕಿ ಇರುವ ದಾಖಲೆಗಳು");
        m.put("re.reassign.no_candidates", "ಈ ದಾಖಲೆಯನ್ನು ಸ್ವೀಕರಿಸಲು ನಿಮ್ಮ ಸಂಸ್ಥೆಯಲ್ಲಿ ಬೇರೆ ಯಾವುದೇ ಅಧಿಕಾರಿ ಲಭ್ಯವಿಲ್ಲ.");
        m.put("re.reassign.reason_label", "ಮರುಹಂಚಿಕೆಗೆ ಕಾರಣ");
        m.put("re.reassign.submit_button", "ಮನವಿಯನ್ನು ಸಲ್ಲಿಸಿ");
        m.put("re.reassign.cancel_button", "ರದ್ದುಗೊಳಿಸಿ");
        m.put("re.reassign.role_filter_label", "ಪಾತ್ರ");
        m.put("re.reassign.role.nodal_officer", "ನೋಡಲ್ ಅಧಿಕಾರಿ");
        m.put("re.reassign.role.contact_person", "ಸಂಪರ್ಕ ವ್ಯಕ್ತಿ");
        m.put("re.reassign.role.pno", "ಪ್ರಧಾನ ನೋಡಲ್ ಅಧಿಕಾರಿ");
        m.put("re.reassign.territory_label", "ವ್ಯಾಪ್ತಿ ಪ್ರದೇಶ");
        m.put("re.reassign.reason_immutable_notice",
              "ಸಲ್ಲಿಸಿದ ನಂತರ ಕಾರಣವನ್ನು ತಿದ್ದಲಾಗುವುದಿಲ್ಲ. ಹೆಚ್ಚಿನ ವಿವರ ನೀಡಲು ಸ್ಪಷ್ಟೀಕರಣವನ್ನು ದಾಖಲಿಸಿ.");
        m.put("re.reassign.clarification_title", "ಸ್ಪಷ್ಟೀಕರಣಗಳು");
        m.put("re.reassign.clarification_add_placeholder", "ಒಂದು ಸ್ಪಷ್ಟೀಕರಣ ಸೇರಿಸಿ…");
        m.put("re.reassign.clarification_add_button", "ಸ್ಪಷ್ಟೀಕರಣ ಸೇರಿಸಿ");
        m.put("re.reassign.clarification_none", "ಯಾವುದೇ ಸ್ಪಷ್ಟೀಕರಣ ಸೇರಿಸಲಾಗಿಲ್ಲ.");
        m.put("re.reassign.original_reason_label", "ಮೂಲ ಕಾರಣ");
        m.put("re.reassign.my_requests_title", "ನನ್ನ ಮರುಹಂಚಿಕೆ ಮನವಿಗಳು");
        m.put("re.reassign.my_requests_none", "ನೀವು ಯಾವುದೇ ಮರುಹಂಚಿಕೆ ಮನವಿಯನ್ನು ಸಲ್ಲಿಸಿಲ್ಲ.");
        m.put("re.reassign.requested_at", "ಮನವಿ ಸಲ್ಲಿಸಿದ ದಿನಾಂಕ");
        m.put("re.reassign.decided_at", "ನಿರ್ಧಾರವಾದ ದಿನಾಂಕ");
        m.put("re.reassign.withdraw_button", "ಹಿಂಪಡೆಯಿರಿ");
        m.put("re.reassign.from_officer", "ಇಂದ");
        m.put("re.reassign.to_officer", "ಗೆ");
        m.put("re.reassign.approvals_title", "ಮರುಹಂಚಿಕೆ ಅನುಮೋದನೆಗಳು");
        m.put("re.reassign.approvals_none", "ನಿಮ್ಮ ಅನುಮೋದನೆಗೆ ಕಾಯುತ್ತಿರುವ ಯಾವುದೇ ಮನವಿಗಳಿಲ್ಲ.");
        m.put("re.reassign.approve_button", "ಅನುಮೋದಿಸಿ");
        m.put("re.reassign.reject_button", "ತಿರಸ್ಕರಿಸಿ");
        m.put("re.reassign.bulk_approve_button", "ಆಯ್ದವುಗಳನ್ನು ಅನುಮೋದಿಸಿ");
        m.put("re.reassign.bulk_reject_button", "ಆಯ್ದವುಗಳನ್ನು ತಿರಸ್ಕರಿಸಿ");
        m.put("re.reassign.decision_comment_label", "ಟಿಪ್ಪಣಿ");
        m.put("re.reassign.select_all", "ಎಲ್ಲವನ್ನೂ ಆಯ್ಕೆ ಮಾಡಿ");
        m.put("re.reassign.selected_count", "ಆಯ್ಕೆಯಾದವು");
        m.put("re.reassign.bulk_partial_success",
              "ಕೆಲವು ದಾಖಲೆಗಳನ್ನು ನವೀಕರಿಸಲಾಗಿಲ್ಲ. ಉಳಿದ ದಾಖಲೆಗಳನ್ನು ಯಶಸ್ವಿಯಾಗಿ ನವೀಕರಿಸಲಾಗಿದೆ.");
        m.put("re.reassign.bulk_all_succeeded", "ಆಯ್ಕೆಯಾದ ಎಲ್ಲಾ ದಾಖಲೆಗಳನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ.");
        m.put("re.pno.dashboard_title", "ತಂಡದ ಕೆಲಸದ ಹೊರೆ");
        m.put("re.pno.officer_column", "ಅಧಿಕಾರಿ");
        m.put("re.pno.workload_column", "ಬಾಕಿ ಇರುವ ದಾಖಲೆಗಳು");
        m.put("re.pno.total_active_records", "ಒಟ್ಟು ಬಾಕಿ ದಾಖಲೆಗಳು");
        m.put("re.pno.pending_approvals", "ಅನುಮೋದನೆಗೆ ಕಾಯುತ್ತಿರುವ ಮನವಿಗಳು");
        m.put("re.pno.no_officers", "ನಿಮ್ಮ ಸಂಸ್ಥೆಗೆ ಯಾವುದೇ ಅಧಿಕಾರಿ ನೋಂದಾಯಿಸಲಾಗಿಲ್ಲ.");
        m.put("re.pno.workload_definition_notice", "ಬಾಕಿ ದಾಖಲೆಗಳಲ್ಲಿ ಕರಡುಗಳು ಮತ್ತು ಮುಗಿದ ಕೆಲಸ ಸೇರಿಲ್ಲ.");
        m.put("re.reassign.history_title", "ಮರುಹಂಚಿಕೆಯ ಇತಿಹಾಸ");
        m.put("re.reassign.history_none", "ಯಾವುದೇ ಮರುಹಂಚಿಕೆ ದಾಖಲಾಗಿಲ್ಲ.");
        m.put("re.reassign.history_from_filter", "ಯಾರಿಂದ ಹಂಚಲಾಯಿತು");
        m.put("re.reassign.history_to_filter", "ಯಾರಿಗೆ ಹಂಚಲಾಯಿತು");
        m.put("re.reassign.history_date_from", "ಆರಂಭ ದಿನಾಂಕ");
        m.put("re.reassign.history_date_to", "ಅಂತಿಮ ದಿನಾಂಕ");
        m.put("re.reassign.history_trigger", "ಪ್ರಕಾರ");
        m.put("re.reassign.trigger.approved_request", "ಅನುಮೋದಿತ ಮನವಿ");
        m.put("re.reassign.trigger.direct", "ನೇರ ಮರುಹಂಚಿಕೆ");
        m.put("re.reassign.history_performed_by", "ಕ್ರಮ ಕೈಗೊಂಡವರು");
        m.put("re.reassign.summary_outbound", "ಇತರರಿಗೆ ಹಂಚಿದ ದಾಖಲೆಗಳು");
        m.put("re.reassign.summary_inbound", "ಸ್ವೀಕರಿಸಿದ ದಾಖಲೆಗಳು");
        m.put("notification.reassign.out", "ಒಂದು ದಾಖಲೆಯನ್ನು ನಿಮ್ಮಿಂದ ಬೇರೊಬ್ಬರಿಗೆ ಹಂಚಲಾಗಿದೆ");
        m.put("notification.reassign.out.body",
              "ನೀವು ನಿರ್ವಹಿಸುತ್ತಿದ್ದ ದೂರು ದಾಖಲೆಯನ್ನು ಬೇರೊಬ್ಬ ಅಧಿಕಾರಿಗೆ ಹಂಚಲಾಗಿದೆ.");
        m.put("notification.reassign.in", "ಒಂದು ದಾಖಲೆಯನ್ನು ನಿಮಗೆ ಹಂಚಲಾಗಿದೆ");
        m.put("notification.reassign.in.body",
              "ಒಂದು ದೂರು ದಾಖಲೆಯನ್ನು ನಿಮಗೆ ಹಂಚಲಾಗಿದೆ ಮತ್ತು ಈಗ ಅದಕ್ಕೆ ನಿಮ್ಮ ಗಮನ ಬೇಕಾಗಿದೆ.");
        m.put("re.reassign.status.pending", "ಅನುಮೋದನೆ ಬಾಕಿ");
        m.put("re.reassign.status.approved", "ಅನುಮೋದಿತ");
        m.put("re.reassign.status.rejected", "ತಿರಸ್ಕೃತ");
        m.put("re.reassign.status.withdrawn", "ಹಿಂಪಡೆಯಲಾಗಿದೆ");
        m.put("re.reassign.error.unauthenticated", "ಈ ಕ್ರಿಯೆಯನ್ನು ಮಾಡಲು ನೀವು ಸೈನ್ ಇನ್ ಮಾಡಬೇಕು.");
        m.put("re.reassign.error.not_permitted", "ಈ ಕ್ರಿಯೆಯನ್ನು ಮಾಡಲು ನಿಮಗೆ ಅನುಮತಿ ಇಲ್ಲ.");
        m.put("re.reassign.error.pno_only", "ಪ್ರಧಾನ ನೋಡಲ್ ಅಧಿಕಾರಿ ಮಾತ್ರ ಮರುಹಂಚಿಕೆಯನ್ನು ಅನುಮೋದಿಸಬಹುದು.");
        m.put("re.reassign.error.cross_entity", "ನೀವು ನಿಮ್ಮ ಸ್ವಂತ ಸಂಸ್ಥೆಗೆ ಸೇರಿದ ದಾಖಲೆಗಳ ಮೇಲೆ ಮಾತ್ರ ಕ್ರಮ ಕೈಗೊಳ್ಳಬಹುದು.");
        m.put("re.reassign.error.entity_unresolved", "ನಿಮ್ಮ ಸಂಸ್ಥೆಯನ್ನು ನಿರ್ಧರಿಸಲಾಗಿಲ್ಲ.");
        m.put("re.reassign.error.entity_required", "ಸಂಸ್ಥೆಯನ್ನು ಸೂಚಿಸುವುದು ಕಡ್ಡಾಯ.");
        m.put("re.reassign.error.target_required", "ದಾಖಲೆಯನ್ನು ಪಡೆಯಬೇಕಾದ ಅಧಿಕಾರಿಯನ್ನು ಆಯ್ಕೆ ಮಾಡಿ.");
        m.put("re.reassign.error.target_not_in_entity", "ಆ ಅಧಿಕಾರಿ ನಿಮ್ಮ ಸಂಸ್ಥೆಗೆ ಸೇರಿದವರಲ್ಲ.");
        m.put("re.reassign.error.target_inactive", "ಆ ಅಧಿಕಾರಿ ಇನ್ನು ಸಕ್ರಿಯರಾಗಿಲ್ಲ ಮತ್ತು ದಾಖಲೆಗಳನ್ನು ಸ್ವೀಕರಿಸಲಾರರು.");
        m.put("re.reassign.error.already_assigned", "ದಾಖಲೆಯನ್ನು ಈಗಾಗಲೇ ಆ ಅಧಿಕಾರಿಗೆ ಹಂಚಲಾಗಿದೆ.");
        m.put("re.reassign.error.already_pending", "ಈ ದಾಖಲೆಗೆ ಒಂದು ಮರುಹಂಚಿಕೆ ಮನವಿ ಈಗಾಗಲೇ ಬಾಕಿ ಇದೆ.");
        m.put("re.reassign.error.already_decided", "ಈ ಮನವಿಯ ಬಗ್ಗೆ ಈಗಾಗಲೇ ನಿರ್ಧಾರವಾಗಿದೆ.");
        m.put("re.reassign.error.reason_too_short", "ದಯವಿಟ್ಟು ಮರುಹಂಚಿಕೆಗೆ ಹೆಚ್ಚು ವಿವರವಾದ ಕಾರಣ ನೀಡಿ.");
        m.put("re.reassign.error.clarification_required", "ಸ್ಪಷ್ಟೀಕರಣ ಖಾಲಿಯಾಗಿರಬಾರದು.");
        m.put("re.reassign.error.rejection_comment_required", "ಮನವಿಯನ್ನು ತಿರಸ್ಕರಿಸುವಾಗ ಟಿಪ್ಪಣಿ ಕಡ್ಡಾಯ.");
        m.put("re.reassign.error.nothing_selected", "ಕನಿಷ್ಠ ಒಂದು ದಾಖಲೆಯನ್ನು ಆಯ್ಕೆ ಮಾಡಿ.");
        m.put("re.reassign.error.bulk_limit_exceeded", "ಒಂದೇ ಬಾರಿಗೆ ಬಹಳ ಹೆಚ್ಚು ದಾಖಲೆಗಳನ್ನು ಆಯ್ಕೆ ಮಾಡಲಾಗಿದೆ.");
        m.put("re.reassign.error.not_your_request", "ನೀವು ನಿಮ್ಮ ಸ್ವಂತ ಮನವಿಯನ್ನು ಮಾತ್ರ ಹಿಂಪಡೆಯಬಹುದು.");
        m.put("re.reassign.error.conflict", "ಈ ದಾಖಲೆಯನ್ನು ಬೇರೊಬ್ಬರು ಬದಲಾಯಿಸಿದ್ದಾರೆ. ಮತ್ತೆ ಲೋಡ್ ಮಾಡಿ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("re.reassign.error.not_found", "ದಾಖಲೆ ಅಥವಾ ಮನವಿ ಸಿಗಲಿಲ್ಲ.");
        m.put("re.reassign.error.invalid", "ಮನವಿಯನ್ನು ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲಾಗಿಲ್ಲ.");
        m.put("re.reassign.error.invalid_date", "ನೀಡಿದ ದಿನಾಂಕ ಮಾನ್ಯವಲ್ಲ.");
        m.put("re.reassign.error.unexpected", "ಏನೋ ತಪ್ಪಾಗಿದೆ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("re.reassign.error.load_failed", "ಮರುಹಂಚಿಕೆಯ ವಿವರಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಿಲ್ಲ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("re.reassign.title", "രേഖ പുനർനിയോഗിക്കുക");
        m.put("re.reassign.candidate_label", "ആർക്ക് കൈമാറണം");
        m.put("re.reassign.search_placeholder", "പേര്, തസ്തിക അല്ലെങ്കിൽ ഇമെയിൽ ഉപയോഗിച്ച് തിരയുക");
        m.put("re.reassign.workload_label", "നിലവിലുള്ള ജോലിഭാരം");
        m.put("re.reassign.workload_column", "തീർപ്പാകാത്ത രേഖകൾ");
        m.put("re.reassign.no_candidates", "ഈ രേഖ സ്വീകരിക്കാൻ നിങ്ങളുടെ സ്ഥാപനത്തിൽ മറ്റൊരു ഉദ്യോഗസ്ഥനും ലഭ്യമല്ല.");
        m.put("re.reassign.reason_label", "പുനർനിയോഗത്തിനുള്ള കാരണം");
        m.put("re.reassign.submit_button", "അഭ്യർത്ഥന സമർപ്പിക്കുക");
        m.put("re.reassign.cancel_button", "റദ്ദാക്കുക");
        m.put("re.reassign.role_filter_label", "ചുമതല");
        m.put("re.reassign.role.nodal_officer", "നോഡൽ ഓഫീസർ");
        m.put("re.reassign.role.contact_person", "ബന്ധപ്പെടേണ്ട വ്യക്തി");
        m.put("re.reassign.role.pno", "പ്രിൻസിപ്പൽ നോഡൽ ഓഫീസർ");
        m.put("re.reassign.territory_label", "പ്രദേശം");
        m.put("re.reassign.reason_immutable_notice",
              "സമർപ്പിച്ചശേഷം കാരണം തിരുത്താൻ കഴിയില്ല. കൂടുതൽ വിവരം നൽകാൻ ഒരു വിശദീകരണം രേഖപ്പെടുത്തുക.");
        m.put("re.reassign.clarification_title", "വിശദീകരണങ്ങൾ");
        m.put("re.reassign.clarification_add_placeholder", "ഒരു വിശദീകരണം ചേർക്കുക…");
        m.put("re.reassign.clarification_add_button", "വിശദീകരണം ചേർക്കുക");
        m.put("re.reassign.clarification_none", "വിശദീകരണങ്ങളൊന്നും ചേർത്തിട്ടില്ല.");
        m.put("re.reassign.original_reason_label", "യഥാർത്ഥ കാരണം");
        m.put("re.reassign.my_requests_title", "എന്റെ പുനർനിയോഗ അഭ്യർത്ഥനകൾ");
        m.put("re.reassign.my_requests_none", "നിങ്ങൾ പുനർനിയോഗ അഭ്യർത്ഥനകളൊന്നും സമർപ്പിച്ചിട്ടില്ല.");
        m.put("re.reassign.requested_at", "അഭ്യർത്ഥിച്ചത്");
        m.put("re.reassign.decided_at", "തീരുമാനിച്ചത്");
        m.put("re.reassign.withdraw_button", "പിൻവലിക്കുക");
        m.put("re.reassign.from_officer", "നിന്ന്");
        m.put("re.reassign.to_officer", "ലേക്ക്");
        m.put("re.reassign.approvals_title", "പുനർനിയോഗ അനുമതികൾ");
        m.put("re.reassign.approvals_none", "നിങ്ങളുടെ അനുമതിക്കായി കാത്തിരിക്കുന്ന അഭ്യർത്ഥനകളില്ല.");
        m.put("re.reassign.approve_button", "അനുമതി നൽകുക");
        m.put("re.reassign.reject_button", "നിരസിക്കുക");
        m.put("re.reassign.bulk_approve_button", "തിരഞ്ഞെടുത്തവയ്ക്ക് അനുമതി നൽകുക");
        m.put("re.reassign.bulk_reject_button", "തിരഞ്ഞെടുത്തവ നിരസിക്കുക");
        m.put("re.reassign.decision_comment_label", "അഭിപ്രായം");
        m.put("re.reassign.select_all", "എല്ലാം തിരഞ്ഞെടുക്കുക");
        m.put("re.reassign.selected_count", "തിരഞ്ഞെടുത്തവ");
        m.put("re.reassign.bulk_partial_success",
              "ചില രേഖകൾ പുതുക്കാൻ കഴിഞ്ഞില്ല. ബാക്കി രേഖകൾ വിജയകരമായി പുതുക്കി.");
        m.put("re.reassign.bulk_all_succeeded", "തിരഞ്ഞെടുത്ത എല്ലാ രേഖകളും പുതുക്കി.");
        m.put("re.pno.dashboard_title", "സംഘത്തിന്റെ ജോലിഭാരം");
        m.put("re.pno.officer_column", "ഉദ്യോഗസ്ഥൻ");
        m.put("re.pno.workload_column", "തീർപ്പാകാത്ത രേഖകൾ");
        m.put("re.pno.total_active_records", "ആകെ തീർപ്പാകാത്ത രേഖകൾ");
        m.put("re.pno.pending_approvals", "അനുമതിക്കായി കാത്തിരിക്കുന്ന അഭ്യർത്ഥനകൾ");
        m.put("re.pno.no_officers", "നിങ്ങളുടെ സ്ഥാപനത്തിനായി ഉദ്യോഗസ്ഥരെ ആരെയും രജിസ്റ്റർ ചെയ്തിട്ടില്ല.");
        m.put("re.pno.workload_definition_notice", "തീർപ്പാകാത്ത രേഖകളിൽ കരടുകളും പൂർത്തിയായ ജോലിയും ഉൾപ്പെടുന്നില്ല.");
        m.put("re.reassign.history_title", "പുനർനിയോഗ ചരിത്രം");
        m.put("re.reassign.history_none", "പുനർനിയോഗങ്ങളൊന്നും രേഖപ്പെടുത്തിയിട്ടില്ല.");
        m.put("re.reassign.history_from_filter", "ആരിൽ നിന്ന് കൈമാറി");
        m.put("re.reassign.history_to_filter", "ആർക്ക് കൈമാറി");
        m.put("re.reassign.history_date_from", "ആരംഭ തീയതി");
        m.put("re.reassign.history_date_to", "അവസാന തീയതി");
        m.put("re.reassign.history_trigger", "തരം");
        m.put("re.reassign.trigger.approved_request", "അനുമതി ലഭിച്ച അഭ്യർത്ഥന");
        m.put("re.reassign.trigger.direct", "നേരിട്ടുള്ള പുനർനിയോഗം");
        m.put("re.reassign.history_performed_by", "നടപടി സ്വീകരിച്ചത്");
        m.put("re.reassign.summary_outbound", "മറ്റുള്ളവർക്ക് കൈമാറിയ രേഖകൾ");
        m.put("re.reassign.summary_inbound", "ലഭിച്ച രേഖകൾ");
        m.put("notification.reassign.out", "ഒരു രേഖ നിങ്ങളിൽ നിന്ന് മറ്റൊരാൾക്ക് കൈമാറിയിരിക്കുന്നു");
        m.put("notification.reassign.out.body",
              "നിങ്ങൾ കൈകാര്യം ചെയ്തിരുന്ന പരാതി രേഖ മറ്റൊരു ഉദ്യോഗസ്ഥന് കൈമാറിയിരിക്കുന്നു.");
        m.put("notification.reassign.in", "ഒരു രേഖ നിങ്ങൾക്ക് കൈമാറിയിരിക്കുന്നു");
        m.put("notification.reassign.in.body",
              "ഒരു പരാതി രേഖ നിങ്ങൾക്ക് കൈമാറിയിരിക്കുന്നു, ഇപ്പോൾ അതിന് നിങ്ങളുടെ ശ്രദ്ധ ആവശ്യമാണ്.");
        m.put("re.reassign.status.pending", "അനുമതി കാത്തിരിക്കുന്നു");
        m.put("re.reassign.status.approved", "അനുമതി ലഭിച്ചു");
        m.put("re.reassign.status.rejected", "നിരസിച്ചു");
        m.put("re.reassign.status.withdrawn", "പിൻവലിച്ചു");
        m.put("re.reassign.error.unauthenticated", "ഈ നടപടി ചെയ്യാൻ നിങ്ങൾ സൈൻ ഇൻ ചെയ്യണം.");
        m.put("re.reassign.error.not_permitted", "ഈ നടപടി ചെയ്യാൻ നിങ്ങൾക്ക് അനുമതിയില്ല.");
        m.put("re.reassign.error.pno_only", "പ്രിൻസിപ്പൽ നോഡൽ ഓഫീസർക്ക് മാത്രമേ പുനർനിയോഗങ്ങൾക്ക് അനുമതി നൽകാൻ കഴിയൂ.");
        m.put("re.reassign.error.cross_entity", "നിങ്ങളുടെ സ്വന്തം സ്ഥാപനത്തിന്റെ രേഖകളിൽ മാത്രമേ നിങ്ങൾക്ക് നടപടി എടുക്കാൻ കഴിയൂ.");
        m.put("re.reassign.error.entity_unresolved", "നിങ്ങളുടെ സ്ഥാപനം നിർണ്ണയിക്കാൻ കഴിഞ്ഞില്ല.");
        m.put("re.reassign.error.entity_required", "ഒരു സ്ഥാപനം വ്യക്തമാക്കേണ്ടതുണ്ട്.");
        m.put("re.reassign.error.target_required", "രേഖ ലഭിക്കേണ്ട ഉദ്യോഗസ്ഥനെ തിരഞ്ഞെടുക്കുക.");
        m.put("re.reassign.error.target_not_in_entity", "ആ ഉദ്യോഗസ്ഥൻ നിങ്ങളുടെ സ്ഥാപനത്തിൽ ഉള്ളവനല്ല.");
        m.put("re.reassign.error.target_inactive", "ആ ഉദ്യോഗസ്ഥൻ ഇപ്പോൾ സജീവമല്ല, രേഖകൾ സ്വീകരിക്കാൻ കഴിയില്ല.");
        m.put("re.reassign.error.already_assigned", "രേഖ നേരത്തെ തന്നെ ആ ഉദ്യോഗസ്ഥന് കൈമാറിയിട്ടുണ്ട്.");
        m.put("re.reassign.error.already_pending", "ഈ രേഖയ്ക്കായി ഒരു പുനർനിയോഗ അഭ്യർത്ഥന നേരത്തെ തന്നെ തീർപ്പാകാതെ കിടക്കുന്നു.");
        m.put("re.reassign.error.already_decided", "ഈ അഭ്യർത്ഥനയിൽ നേരത്തെ തന്നെ തീരുമാനമെടുത്തിട്ടുണ്ട്.");
        m.put("re.reassign.error.reason_too_short", "പുനർനിയോഗത്തിന് കൂടുതൽ വിശദമായ കാരണം നൽകുക.");
        m.put("re.reassign.error.clarification_required", "വിശദീകരണം ശൂന്യമായിരിക്കരുത്.");
        m.put("re.reassign.error.rejection_comment_required", "അഭ്യർത്ഥന നിരസിക്കുമ്പോൾ അഭിപ്രായം നിർബന്ധമാണ്.");
        m.put("re.reassign.error.nothing_selected", "കുറഞ്ഞത് ഒരു രേഖയെങ്കിലും തിരഞ്ഞെടുക്കുക.");
        m.put("re.reassign.error.bulk_limit_exceeded", "ഒരേസമയം വളരെയധികം രേഖകൾ തിരഞ്ഞെടുത്തു.");
        m.put("re.reassign.error.not_your_request", "നിങ്ങളുടെ സ്വന്തം അഭ്യർത്ഥന മാത്രമേ പിൻവലിക്കാൻ കഴിയൂ.");
        m.put("re.reassign.error.conflict", "ഈ രേഖ മറ്റൊരാൾ മാറ്റിയിട്ടുണ്ട്. വീണ്ടും ലോഡ് ചെയ്ത് ശ്രമിക്കുക.");
        m.put("re.reassign.error.not_found", "രേഖയോ അഭ്യർത്ഥനയോ കണ്ടെത്താനായില്ല.");
        m.put("re.reassign.error.invalid", "അഭ്യർത്ഥന പ്രോസസ് ചെയ്യാനായില്ല.");
        m.put("re.reassign.error.invalid_date", "നൽകിയ തീയതി സാധുവല്ല.");
        m.put("re.reassign.error.unexpected", "എന്തോ പിഴവ് സംഭവിച്ചു. വീണ്ടും ശ്രമിക്കുക.");
        m.put("re.reassign.error.load_failed", "പുനർനിയോഗ വിവരങ്ങൾ ലോഡ് ചെയ്യാനായില്ല.");
        return m;
    }

    private void seed(String code, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule("reassignment");
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
