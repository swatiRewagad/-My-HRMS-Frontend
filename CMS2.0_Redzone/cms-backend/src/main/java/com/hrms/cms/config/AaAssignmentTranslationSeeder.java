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
 * Appellate Authority assignment, officer-pool administration, reassignment approval and
 * unclaimed-draft escalation vocabulary.
 *
 * <p>Five groups of keys, all referenced by the AA assignment feature:
 *
 * <ul>
 *   <li>{@code aa.assignment.*} — outcome and failure reasons returned by the auto-assignment
 *       engine (round-robin, grace-band overflow, vernacular skill match, administrator override).
 *   <li>{@code aa.pool.*} — officer-pool administration: workload threshold edits, skill edits,
 *       activation toggles (single and bulk), queue rebalance and their validation errors.
 *   <li>{@code aa.reassign.*} — reassignment request lifecycle plus the notifications raised to
 *       the requesting officer and the approver.
 *   <li>{@code aa.escalation.*} — unclaimed-draft sweep and claim acknowledgement.
 *   <li>{@code aa.admin.*} — labels for the AA assignment administration console.
 * </ul>
 *
 * <p>Seeded in all ten supported locales, following {@link AaTranslationSeeder}: English lives in
 * {@code TranslationKey.defaultValue}, the other nine become {@code Translation} rows.
 * Insert-if-absent, so re-running is a no-op — but note the corollary: correcting a string here
 * does NOT rewrite a row already committed to a database. A text correction needs a code-scoped
 * UPDATE in both migration directories.
 */
@Component
@Order(14)
public class AaAssignmentTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "aa-assignment";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public AaAssignmentTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // ═══ Auto-assignment outcomes ═══
        m.put("aa.assignment.assigned", "Appeal assigned to you");
        m.put("aa.assignment.assigned_under_grace", "Appeal assigned above the normal workload limit");
        m.put("aa.assignment.vernacular_override", "Appeal assigned to you for language handling");
        m.put("aa.assignment.manual_override", "Appeal assigned to you by the administrator");
        m.put("aa.assignment.pool_empty", "No officer is currently available for assignment");
        m.put("aa.assignment.pool_exhausted", "All officers have reached their workload limit");
        m.put("aa.assignment.error_reason_required", "A reason is required for a manual assignment");
        m.put("aa.assignment.error_target_not_in_pool", "The selected officer is not in the assignment pool");
        m.put("aa.assignment.error_target_unavailable", "The selected officer is inactive or on leave");

        // ═══ Officer pool administration ═══
        m.put("aa.pool.threshold_updated", "Workload limit updated");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "Workload limit updated. Rebalancing is pending administrator action.");
        m.put("aa.pool.skills_updated", "Language skills updated");
        m.put("aa.pool.activation_updated", "Officer status updated");
        m.put("aa.pool.activation_unchanged", "Officer status was already set to this value");
        m.put("aa.pool.bulk_activation_complete", "All selected officers were updated");
        m.put("aa.pool.bulk_activation_partial", "Some officers could not be updated");
        m.put("aa.pool.rebalance_complete", "Queue rebalanced");
        m.put("aa.pool.deactivate_warning_pending_work", "This officer still has pending complaints assigned");
        m.put("aa.pool.deactivate_no_pending_work", "This officer has no pending complaints");
        m.put("aa.pool.notify_deactivated_with_pending",
              "An officer was deactivated while still holding pending complaints");
        m.put("aa.pool.error_officer_not_found", "Officer not found in the assignment pool");
        m.put("aa.pool.error_threshold_negative", "The workload limit cannot be negative");
        m.put("aa.pool.error_threshold_required", "A workload limit value is required");
        m.put("aa.pool.error_no_officers_selected", "Select at least one officer");
        m.put("aa.pool.error_confirmation_required",
              "Confirmation is required because this officer has pending complaints");
        m.put("aa.pool.error_role_group_required", "A role group is required");
        m.put("aa.pool.error_unknown", "The action could not be completed");

        // ═══ Reassignment request lifecycle ═══
        m.put("aa.reassign.submitted", "Reassignment request submitted for approval");
        m.put("aa.reassign.auto_approved", "Reassignment request approved automatically");
        m.put("aa.reassign.approved", "Reassignment request approved");
        m.put("aa.reassign.rejected", "Reassignment request rejected");
        m.put("aa.reassign.withdrawn", "Reassignment request withdrawn");
        m.put("aa.reassign.notify_approved", "Your reassignment request was approved");
        m.put("aa.reassign.notify_rejected", "Your reassignment request was rejected");
        m.put("aa.reassign.notify_pending_approval", "A reassignment request is awaiting your approval");
        m.put("aa.reassign.error_appeal_required", "An appeal number is required");
        m.put("aa.reassign.error_reason_required", "A reason is required for a reassignment request");
        m.put("aa.reassign.error_identity_unresolved", "Your identity could not be established");
        m.put("aa.reassign.error_not_assigned", "This appeal is not currently assigned to anyone");
        m.put("aa.reassign.error_not_holder",
              "You can only request reassignment of an appeal assigned to you");
        m.put("aa.reassign.error_already_pending",
              "A reassignment request is already pending for this appeal");
        m.put("aa.reassign.error_request_not_found", "Reassignment request not found");
        m.put("aa.reassign.error_not_pending", "This request has already been decided");
        m.put("aa.reassign.error_not_requester", "Only the officer who raised a request may withdraw it");

        // ═══ Unclaimed-draft escalation ═══
        m.put("aa.escalation.unclaimed_draft", "A draft has remained unclaimed beyond the allowed time");
        m.put("aa.escalation.claimed", "Draft marked as picked up");
        m.put("aa.escalation.claim_not_holder", "This draft is not assigned to you");
        m.put("aa.escalation.sweep_complete", "Escalation check complete");

        // ═══ Administration console labels ═══
        m.put("aa.admin.console_title", "AA Assignment Administration");
        m.put("aa.admin.officer_pool", "Officer Pool");
        m.put("aa.admin.threshold", "Workload Limit");
        m.put("aa.admin.current_workload", "Current Workload");
        m.put("aa.admin.status_active", "Active");
        m.put("aa.admin.status_inactive", "Inactive");
        m.put("aa.admin.status_on_leave", "On Leave");
        m.put("aa.admin.at_threshold", "At Limit");
        m.put("aa.admin.eligible", "Eligible");
        m.put("aa.admin.bulk_activate", "Activate Selected");
        m.put("aa.admin.bulk_deactivate", "Deactivate Selected");
        m.put("aa.admin.edit_threshold", "Edit Limit");
        m.put("aa.admin.skill_languages", "Language Skills");
        m.put("aa.admin.reason", "Reason");
        m.put("aa.admin.confirm", "Confirm");
        m.put("aa.admin.cancel", "Cancel");
        m.put("aa.admin.select_all", "Select All");
        m.put("aa.admin.pending_approvals", "Pending Reassignment Approvals");
        m.put("aa.admin.approve", "Approve");
        m.put("aa.admin.reject", "Reject");
        m.put("aa.admin.rebalance", "Rebalance Queue");
        m.put("aa.admin.audit_trail", "Audit Trail");
        m.put("aa.admin.no_officers", "No officers in this pool");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "Setting the limit to 0 means unlimited. Tick 'unlimited' to confirm, or enter a number above 0.");
        m.put("aa.assignment.error_concurrently_assigned",
              "This appeal was assigned by another user at the same time. Please refresh and try again.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "अपील आपको सौंपी गई है");
        m.put("aa.assignment.assigned_under_grace", "सामान्य कार्यभार सीमा से अधिक अपील सौंपी गई है");
        m.put("aa.assignment.vernacular_override", "भाषा संबंधी कार्यवाही के लिए अपील आपको सौंपी गई है");
        m.put("aa.assignment.manual_override", "प्रशासक द्वारा अपील आपको सौंपी गई है");
        m.put("aa.assignment.pool_empty", "इस समय सौंपने के लिए कोई अधिकारी उपलब्ध नहीं है");
        m.put("aa.assignment.pool_exhausted", "सभी अधिकारी अपनी कार्यभार सीमा तक पहुँच चुके हैं");
        m.put("aa.assignment.error_reason_required", "मैन्युअल आवंटन के लिए कारण देना अनिवार्य है");
        m.put("aa.assignment.error_target_not_in_pool", "चयनित अधिकारी आवंटन समूह में नहीं है");
        m.put("aa.assignment.error_target_unavailable", "चयनित अधिकारी निष्क्रिय है अथवा अवकाश पर है");
        m.put("aa.pool.threshold_updated", "कार्यभार सीमा अद्यतन कर दी गई");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "कार्यभार सीमा अद्यतन कर दी गई। पुनर्संतुलन प्रशासक की कार्रवाई हेतु लंबित है।");
        m.put("aa.pool.skills_updated", "भाषा कौशल अद्यतन कर दिए गए");
        m.put("aa.pool.activation_updated", "अधिकारी की स्थिति अद्यतन कर दी गई");
        m.put("aa.pool.activation_unchanged", "अधिकारी की स्थिति पहले से ही यही निर्धारित थी");
        m.put("aa.pool.bulk_activation_complete", "चयनित सभी अधिकारी अद्यतन कर दिए गए");
        m.put("aa.pool.bulk_activation_partial", "कुछ अधिकारियों को अद्यतन नहीं किया जा सका");
        m.put("aa.pool.rebalance_complete", "कतार का पुनर्संतुलन कर दिया गया");
        m.put("aa.pool.deactivate_warning_pending_work", "इस अधिकारी के पास अभी भी लंबित शिकायतें सौंपी हुई हैं");
        m.put("aa.pool.deactivate_no_pending_work", "इस अधिकारी के पास कोई लंबित शिकायत नहीं है");
        m.put("aa.pool.notify_deactivated_with_pending",
              "एक अधिकारी को लंबित शिकायतें रहते हुए ही निष्क्रिय कर दिया गया");
        m.put("aa.pool.error_officer_not_found", "आवंटन समूह में अधिकारी नहीं मिला");
        m.put("aa.pool.error_threshold_negative", "कार्यभार सीमा ऋणात्मक नहीं हो सकती");
        m.put("aa.pool.error_threshold_required", "कार्यभार सीमा का मान देना अनिवार्य है");
        m.put("aa.pool.error_no_officers_selected", "कम से कम एक अधिकारी का चयन करें");
        m.put("aa.pool.error_confirmation_required",
              "इस अधिकारी के पास लंबित शिकायतें होने के कारण पुष्टि आवश्यक है");
        m.put("aa.pool.error_role_group_required", "भूमिका समूह देना अनिवार्य है");
        m.put("aa.pool.error_unknown", "यह कार्रवाई पूरी नहीं की जा सकी");
        m.put("aa.reassign.submitted", "पुनरावंटन अनुरोध अनुमोदन हेतु प्रस्तुत किया गया");
        m.put("aa.reassign.auto_approved", "पुनरावंटन अनुरोध स्वतः अनुमोदित कर दिया गया");
        m.put("aa.reassign.approved", "पुनरावंटन अनुरोध अनुमोदित कर दिया गया");
        m.put("aa.reassign.rejected", "पुनरावंटन अनुरोध अस्वीकृत कर दिया गया");
        m.put("aa.reassign.withdrawn", "पुनरावंटन अनुरोध वापस ले लिया गया");
        m.put("aa.reassign.notify_approved", "आपका पुनरावंटन अनुरोध अनुमोदित कर दिया गया");
        m.put("aa.reassign.notify_rejected", "आपका पुनरावंटन अनुरोध अस्वीकृत कर दिया गया");
        m.put("aa.reassign.notify_pending_approval", "एक पुनरावंटन अनुरोध आपके अनुमोदन की प्रतीक्षा में है");
        m.put("aa.reassign.error_appeal_required", "अपील संख्या देना अनिवार्य है");
        m.put("aa.reassign.error_reason_required", "पुनरावंटन अनुरोध के लिए कारण देना अनिवार्य है");
        m.put("aa.reassign.error_identity_unresolved", "आपकी पहचान स्थापित नहीं हो सकी");
        m.put("aa.reassign.error_not_assigned", "यह अपील इस समय किसी को सौंपी नहीं गई है");
        m.put("aa.reassign.error_not_holder",
              "आप केवल अपने को सौंपी गई अपील के पुनरावंटन का अनुरोध कर सकते हैं");
        m.put("aa.reassign.error_already_pending",
              "इस अपील के लिए एक पुनरावंटन अनुरोध पहले से ही लंबित है");
        m.put("aa.reassign.error_request_not_found", "पुनरावंटन अनुरोध नहीं मिला");
        m.put("aa.reassign.error_not_pending", "इस अनुरोध पर पहले ही निर्णय हो चुका है");
        m.put("aa.reassign.error_not_requester", "अनुरोध केवल वही अधिकारी वापस ले सकता है जिसने उसे प्रस्तुत किया था");
        m.put("aa.escalation.unclaimed_draft", "एक प्रारूप निर्धारित समय से अधिक समय तक अस्वीकृत पड़ा रहा");
        m.put("aa.escalation.claimed", "प्रारूप ग्रहण किया गया के रूप में चिह्नित");
        m.put("aa.escalation.claim_not_holder", "यह प्रारूप आपको सौंपा नहीं गया है");
        m.put("aa.escalation.sweep_complete", "उत्प्रेषण जाँच पूर्ण हुई");
        m.put("aa.admin.console_title", "अपीलीय प्राधिकारी आवंटन प्रशासन");
        m.put("aa.admin.officer_pool", "अधिकारी समूह");
        m.put("aa.admin.threshold", "कार्यभार सीमा");
        m.put("aa.admin.current_workload", "वर्तमान कार्यभार");
        m.put("aa.admin.status_active", "सक्रिय");
        m.put("aa.admin.status_inactive", "निष्क्रिय");
        m.put("aa.admin.status_on_leave", "अवकाश पर");
        m.put("aa.admin.at_threshold", "सीमा पर");
        m.put("aa.admin.eligible", "पात्र");
        m.put("aa.admin.bulk_activate", "चयनित को सक्रिय करें");
        m.put("aa.admin.bulk_deactivate", "चयनित को निष्क्रिय करें");
        m.put("aa.admin.edit_threshold", "सीमा संपादित करें");
        m.put("aa.admin.skill_languages", "भाषा कौशल");
        m.put("aa.admin.reason", "कारण");
        m.put("aa.admin.confirm", "पुष्टि करें");
        m.put("aa.admin.cancel", "रद्द करें");
        m.put("aa.admin.select_all", "सभी चुनें");
        m.put("aa.admin.pending_approvals", "लंबित पुनरावंटन अनुमोदन");
        m.put("aa.admin.approve", "अनुमोदित करें");
        m.put("aa.admin.reject", "अस्वीकार करें");
        m.put("aa.admin.rebalance", "कतार का पुनर्संतुलन करें");
        m.put("aa.admin.audit_trail", "अंकेक्षण अभिलेख");
        m.put("aa.admin.no_officers", "इस समूह में कोई अधिकारी नहीं है");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "सीमा 0 निर्धारित करने का अर्थ असीमित है। पुष्टि के लिए 'असीमित' पर निशान लगाएँ, अथवा 0 से अधिक कोई संख्या दर्ज करें।");
        m.put("aa.assignment.error_concurrently_assigned",
              "यह अपील उसी समय किसी अन्य उपयोगकर्ता द्वारा सौंप दी गई थी। कृपया पृष्ठ ताज़ा करके पुनः प्रयास करें।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "अपील आपल्याकडे सोपविण्यात आली आहे");
        m.put("aa.assignment.assigned_under_grace", "नेहमीच्या कार्यभार मर्यादेपेक्षा अधिक अपील सोपविण्यात आली आहे");
        m.put("aa.assignment.vernacular_override", "भाषाविषयक कामकाजासाठी अपील आपल्याकडे सोपविण्यात आली आहे");
        m.put("aa.assignment.manual_override", "प्रशासकाने अपील आपल्याकडे सोपविली आहे");
        m.put("aa.assignment.pool_empty", "सध्या वाटपासाठी कोणताही अधिकारी उपलब्ध नाही");
        m.put("aa.assignment.pool_exhausted", "सर्व अधिकारी आपल्या कार्यभार मर्यादेपर्यंत पोहोचले आहेत");
        m.put("aa.assignment.error_reason_required", "मॅन्युअल वाटपासाठी कारण देणे आवश्यक आहे");
        m.put("aa.assignment.error_target_not_in_pool", "निवडलेला अधिकारी वाटप गटात नाही");
        m.put("aa.assignment.error_target_unavailable", "निवडलेला अधिकारी निष्क्रिय आहे किंवा रजेवर आहे");
        m.put("aa.pool.threshold_updated", "कार्यभार मर्यादा अद्ययावत केली");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "कार्यभार मर्यादा अद्ययावत केली. पुनर्संतुलन प्रशासकाच्या कार्यवाहीसाठी प्रलंबित आहे.");
        m.put("aa.pool.skills_updated", "भाषा कौशल्ये अद्ययावत केली");
        m.put("aa.pool.activation_updated", "अधिकाऱ्याची स्थिती अद्ययावत केली");
        m.put("aa.pool.activation_unchanged", "अधिकाऱ्याची स्थिती आधीच याच मूल्यावर निश्चित होती");
        m.put("aa.pool.bulk_activation_complete", "निवडलेले सर्व अधिकारी अद्ययावत केले");
        m.put("aa.pool.bulk_activation_partial", "काही अधिकाऱ्यांना अद्ययावत करता आले नाही");
        m.put("aa.pool.rebalance_complete", "रांगेचे पुनर्संतुलन केले");
        m.put("aa.pool.deactivate_warning_pending_work", "या अधिकाऱ्याकडे अद्याप प्रलंबित तक्रारी सोपविलेल्या आहेत");
        m.put("aa.pool.deactivate_no_pending_work", "या अधिकाऱ्याकडे कोणतीही प्रलंबित तक्रार नाही");
        m.put("aa.pool.notify_deactivated_with_pending",
              "प्रलंबित तक्रारी असतानाही एका अधिकाऱ्याला निष्क्रिय करण्यात आले");
        m.put("aa.pool.error_officer_not_found", "वाटप गटात अधिकारी आढळला नाही");
        m.put("aa.pool.error_threshold_negative", "कार्यभार मर्यादा ऋण असू शकत नाही");
        m.put("aa.pool.error_threshold_required", "कार्यभार मर्यादेचे मूल्य देणे आवश्यक आहे");
        m.put("aa.pool.error_no_officers_selected", "किमान एका अधिकाऱ्याची निवड करा");
        m.put("aa.pool.error_confirmation_required",
              "या अधिकाऱ्याकडे प्रलंबित तक्रारी असल्यामुळे निश्चिती आवश्यक आहे");
        m.put("aa.pool.error_role_group_required", "भूमिका गट देणे आवश्यक आहे");
        m.put("aa.pool.error_unknown", "ही कार्यवाही पूर्ण करता आली नाही");
        m.put("aa.reassign.submitted", "पुनर्वाटप विनंती मंजुरीसाठी सादर केली");
        m.put("aa.reassign.auto_approved", "पुनर्वाटप विनंती स्वयंचलितपणे मंजूर केली");
        m.put("aa.reassign.approved", "पुनर्वाटप विनंती मंजूर केली");
        m.put("aa.reassign.rejected", "पुनर्वाटप विनंती नाकारली");
        m.put("aa.reassign.withdrawn", "पुनर्वाटप विनंती मागे घेतली");
        m.put("aa.reassign.notify_approved", "आपली पुनर्वाटप विनंती मंजूर करण्यात आली");
        m.put("aa.reassign.notify_rejected", "आपली पुनर्वाटप विनंती नाकारण्यात आली");
        m.put("aa.reassign.notify_pending_approval", "एक पुनर्वाटप विनंती आपल्या मंजुरीच्या प्रतीक्षेत आहे");
        m.put("aa.reassign.error_appeal_required", "अपील क्रमांक देणे आवश्यक आहे");
        m.put("aa.reassign.error_reason_required", "पुनर्वाटप विनंतीसाठी कारण देणे आवश्यक आहे");
        m.put("aa.reassign.error_identity_unresolved", "आपली ओळख निश्चित करता आली नाही");
        m.put("aa.reassign.error_not_assigned", "ही अपील सध्या कोणालाही सोपविलेली नाही");
        m.put("aa.reassign.error_not_holder",
              "आपण केवळ आपल्याकडे सोपविलेल्या अपिलाच्या पुनर्वाटपाची विनंती करू शकता");
        m.put("aa.reassign.error_already_pending",
              "या अपिलासाठी एक पुनर्वाटप विनंती आधीच प्रलंबित आहे");
        m.put("aa.reassign.error_request_not_found", "पुनर्वाटप विनंती आढळली नाही");
        m.put("aa.reassign.error_not_pending", "या विनंतीवर आधीच निर्णय घेण्यात आला आहे");
        m.put("aa.reassign.error_not_requester", "विनंती केवळ ती सादर करणारा अधिकारीच मागे घेऊ शकतो");
        m.put("aa.escalation.unclaimed_draft", "एक मसुदा अनुमत कालावधीपेक्षा अधिक काळ अस्वीकृत राहिला");
        m.put("aa.escalation.claimed", "मसुदा स्वीकारला असे चिन्हांकित केले");
        m.put("aa.escalation.claim_not_holder", "हा मसुदा आपल्याकडे सोपविलेला नाही");
        m.put("aa.escalation.sweep_complete", "वाढीव स्तर तपासणी पूर्ण झाली");
        m.put("aa.admin.console_title", "अपिलीय प्राधिकरण वाटप प्रशासन");
        m.put("aa.admin.officer_pool", "अधिकारी गट");
        m.put("aa.admin.threshold", "कार्यभार मर्यादा");
        m.put("aa.admin.current_workload", "सध्याचा कार्यभार");
        m.put("aa.admin.status_active", "सक्रिय");
        m.put("aa.admin.status_inactive", "निष्क्रिय");
        m.put("aa.admin.status_on_leave", "रजेवर");
        m.put("aa.admin.at_threshold", "मर्यादेवर");
        m.put("aa.admin.eligible", "पात्र");
        m.put("aa.admin.bulk_activate", "निवडलेले सक्रिय करा");
        m.put("aa.admin.bulk_deactivate", "निवडलेले निष्क्रिय करा");
        m.put("aa.admin.edit_threshold", "मर्यादा संपादित करा");
        m.put("aa.admin.skill_languages", "भाषा कौशल्ये");
        m.put("aa.admin.reason", "कारण");
        m.put("aa.admin.confirm", "निश्चित करा");
        m.put("aa.admin.cancel", "रद्द करा");
        m.put("aa.admin.select_all", "सर्व निवडा");
        m.put("aa.admin.pending_approvals", "प्रलंबित पुनर्वाटप मंजुऱ्या");
        m.put("aa.admin.approve", "मंजूर करा");
        m.put("aa.admin.reject", "नाकारा");
        m.put("aa.admin.rebalance", "रांगेचे पुनर्संतुलन करा");
        m.put("aa.admin.audit_trail", "लेखापरीक्षण नोंद");
        m.put("aa.admin.no_officers", "या गटात कोणताही अधिकारी नाही");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "मर्यादा 0 ठेवणे म्हणजे अमर्याद. निश्चितीसाठी 'अमर्याद' वर खूण करा, किंवा 0 पेक्षा मोठा आकडा नमूद करा.");
        m.put("aa.assignment.error_concurrently_assigned",
              "ही अपील त्याच वेळी दुसऱ्या वापरकर्त्याने सोपविली होती. कृपया पृष्ठ पुन्हा उघडून पुन्हा प्रयत्न करा.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "আপিলটি আপনাকে বরাদ্দ করা হয়েছে");
        m.put("aa.assignment.assigned_under_grace", "স্বাভাবিক কর্মভার সীমার অতিরিক্ত আপিল বরাদ্দ করা হয়েছে");
        m.put("aa.assignment.vernacular_override", "ভাষা সংক্রান্ত কাজের জন্য আপিলটি আপনাকে বরাদ্দ করা হয়েছে");
        m.put("aa.assignment.manual_override", "প্রশাসক আপিলটি আপনাকে বরাদ্দ করেছেন");
        m.put("aa.assignment.pool_empty", "বরাদ্দের জন্য বর্তমানে কোনো আধিকারিক উপলব্ধ নেই");
        m.put("aa.assignment.pool_exhausted", "সকল আধিকারিক তাঁদের কর্মভার সীমায় পৌঁছে গিয়েছেন");
        m.put("aa.assignment.error_reason_required", "হাতে করা বরাদ্দের জন্য কারণ উল্লেখ করা আবশ্যক");
        m.put("aa.assignment.error_target_not_in_pool", "নির্বাচিত আধিকারিক বরাদ্দ গোষ্ঠীতে নেই");
        m.put("aa.assignment.error_target_unavailable", "নির্বাচিত আধিকারিক নিষ্ক্রিয় অথবা ছুটিতে আছেন");
        m.put("aa.pool.threshold_updated", "কর্মভার সীমা হালনাগাদ করা হয়েছে");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "কর্মভার সীমা হালনাগাদ করা হয়েছে। পুনঃসমতা প্রশাসকের পদক্ষেপের অপেক্ষায় রয়েছে।");
        m.put("aa.pool.skills_updated", "ভাষা দক্ষতা হালনাগাদ করা হয়েছে");
        m.put("aa.pool.activation_updated", "আধিকারিকের অবস্থা হালনাগাদ করা হয়েছে");
        m.put("aa.pool.activation_unchanged", "আধিকারিকের অবস্থা আগে থেকেই এই মানে নির্ধারিত ছিল");
        m.put("aa.pool.bulk_activation_complete", "নির্বাচিত সকল আধিকারিক হালনাগাদ করা হয়েছে");
        m.put("aa.pool.bulk_activation_partial", "কিছু আধিকারিককে হালনাগাদ করা যায়নি");
        m.put("aa.pool.rebalance_complete", "সারি পুনঃসমতাযুক্ত করা হয়েছে");
        m.put("aa.pool.deactivate_warning_pending_work", "এই আধিকারিকের কাছে এখনও নিষ্পত্তিহীন অভিযোগ বরাদ্দ রয়েছে");
        m.put("aa.pool.deactivate_no_pending_work", "এই আধিকারিকের কোনো নিষ্পত্তিহীন অভিযোগ নেই");
        m.put("aa.pool.notify_deactivated_with_pending",
              "নিষ্পত্তিহীন অভিযোগ থাকা সত্ত্বেও একজন আধিকারিককে নিষ্ক্রিয় করা হয়েছে");
        m.put("aa.pool.error_officer_not_found", "বরাদ্দ গোষ্ঠীতে আধিকারিককে পাওয়া যায়নি");
        m.put("aa.pool.error_threshold_negative", "কর্মভার সীমা ঋণাত্মক হতে পারে না");
        m.put("aa.pool.error_threshold_required", "কর্মভার সীমার মান উল্লেখ করা আবশ্যক");
        m.put("aa.pool.error_no_officers_selected", "অন্তত একজন আধিকারিক নির্বাচন করুন");
        m.put("aa.pool.error_confirmation_required",
              "এই আধিকারিকের নিষ্পত্তিহীন অভিযোগ থাকায় নিশ্চিতকরণ আবশ্যক");
        m.put("aa.pool.error_role_group_required", "একটি ভূমিকা গোষ্ঠী উল্লেখ করা আবশ্যক");
        m.put("aa.pool.error_unknown", "এই কাজটি সম্পন্ন করা যায়নি");
        m.put("aa.reassign.submitted", "পুনর্বরাদ্দের আবেদন অনুমোদনের জন্য দাখিল করা হয়েছে");
        m.put("aa.reassign.auto_approved", "পুনর্বরাদ্দের আবেদন স্বয়ংক্রিয়ভাবে অনুমোদিত হয়েছে");
        m.put("aa.reassign.approved", "পুনর্বরাদ্দের আবেদন অনুমোদিত হয়েছে");
        m.put("aa.reassign.rejected", "পুনর্বরাদ্দের আবেদন প্রত্যাখ্যাত হয়েছে");
        m.put("aa.reassign.withdrawn", "পুনর্বরাদ্দের আবেদন প্রত্যাহার করা হয়েছে");
        m.put("aa.reassign.notify_approved", "আপনার পুনর্বরাদ্দের আবেদন অনুমোদিত হয়েছে");
        m.put("aa.reassign.notify_rejected", "আপনার পুনর্বরাদ্দের আবেদন প্রত্যাখ্যাত হয়েছে");
        m.put("aa.reassign.notify_pending_approval", "একটি পুনর্বরাদ্দের আবেদন আপনার অনুমোদনের অপেক্ষায় রয়েছে");
        m.put("aa.reassign.error_appeal_required", "আপিল নম্বর উল্লেখ করা আবশ্যক");
        m.put("aa.reassign.error_reason_required", "পুনর্বরাদ্দের আবেদনের জন্য কারণ উল্লেখ করা আবশ্যক");
        m.put("aa.reassign.error_identity_unresolved", "আপনার পরিচয় নিশ্চিত করা যায়নি");
        m.put("aa.reassign.error_not_assigned", "এই আপিলটি বর্তমানে কারও কাছে বরাদ্দ নেই");
        m.put("aa.reassign.error_not_holder",
              "আপনি কেবল আপনাকে বরাদ্দ করা আপিলের পুনর্বরাদ্দের আবেদন করতে পারেন");
        m.put("aa.reassign.error_already_pending",
              "এই আপিলের জন্য একটি পুনর্বরাদ্দের আবেদন ইতিমধ্যেই বিচারাধীন রয়েছে");
        m.put("aa.reassign.error_request_not_found", "পুনর্বরাদ্দের আবেদন পাওয়া যায়নি");
        m.put("aa.reassign.error_not_pending", "এই আবেদনটি সম্পর্কে ইতিমধ্যেই সিদ্ধান্ত নেওয়া হয়েছে");
        m.put("aa.reassign.error_not_requester", "যে আধিকারিক আবেদনটি করেছেন কেবল তিনিই তা প্রত্যাহার করতে পারেন");
        m.put("aa.escalation.unclaimed_draft", "একটি খসড়া অনুমোদিত সময়সীমার পরেও অগৃহীত রয়ে গিয়েছে");
        m.put("aa.escalation.claimed", "খসড়াটি গৃহীত হিসেবে চিহ্নিত করা হয়েছে");
        m.put("aa.escalation.claim_not_holder", "এই খসড়াটি আপনাকে বরাদ্দ করা হয়নি");
        m.put("aa.escalation.sweep_complete", "ঊর্ধ্বতন স্তরে প্রেরণের পরীক্ষা সম্পন্ন হয়েছে");
        m.put("aa.admin.console_title", "আপিল কর্তৃপক্ষ বরাদ্দ প্রশাসন");
        m.put("aa.admin.officer_pool", "আধিকারিক গোষ্ঠী");
        m.put("aa.admin.threshold", "কর্মভার সীমা");
        m.put("aa.admin.current_workload", "বর্তমান কর্মভার");
        m.put("aa.admin.status_active", "সক্রিয়");
        m.put("aa.admin.status_inactive", "নিষ্ক্রিয়");
        m.put("aa.admin.status_on_leave", "ছুটিতে");
        m.put("aa.admin.at_threshold", "সীমায় পৌঁছেছে");
        m.put("aa.admin.eligible", "যোগ্য");
        m.put("aa.admin.bulk_activate", "নির্বাচিতদের সক্রিয় করুন");
        m.put("aa.admin.bulk_deactivate", "নির্বাচিতদের নিষ্ক্রিয় করুন");
        m.put("aa.admin.edit_threshold", "সীমা সম্পাদনা করুন");
        m.put("aa.admin.skill_languages", "ভাষা দক্ষতা");
        m.put("aa.admin.reason", "কারণ");
        m.put("aa.admin.confirm", "নিশ্চিত করুন");
        m.put("aa.admin.cancel", "বাতিল করুন");
        m.put("aa.admin.select_all", "সব নির্বাচন করুন");
        m.put("aa.admin.pending_approvals", "অপেক্ষমাণ পুনর্বরাদ্দ অনুমোদন");
        m.put("aa.admin.approve", "অনুমোদন করুন");
        m.put("aa.admin.reject", "প্রত্যাখ্যান করুন");
        m.put("aa.admin.rebalance", "সারি পুনঃসমতাযুক্ত করুন");
        m.put("aa.admin.audit_trail", "নিরীক্ষা নথি");
        m.put("aa.admin.no_officers", "এই গোষ্ঠীতে কোনো আধিকারিক নেই");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "সীমা ০ রাখার অর্থ অসীম। নিশ্চিত করতে 'অসীম' চিহ্নিত করুন, অথবা ০-এর বেশি কোনো সংখ্যা লিখুন।");
        m.put("aa.assignment.error_concurrently_assigned",
              "এই আপিলটি একই সময়ে অন্য একজন ব্যবহারকারী বরাদ্দ করেছেন। অনুগ্রহ করে পৃষ্ঠাটি নতুন করে নিয়ে আবার চেষ্টা করুন।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "అప్పీలు మీకు కేటాయించబడింది");
        m.put("aa.assignment.assigned_under_grace", "సాధారణ పని భారం పరిమితికి మించి అప్పీలు కేటాయించబడింది");
        m.put("aa.assignment.vernacular_override", "భాషా సంబంధిత నిర్వహణ కోసం అప్పీలు మీకు కేటాయించబడింది");
        m.put("aa.assignment.manual_override", "నిర్వాహకుడు అప్పీలును మీకు కేటాయించారు");
        m.put("aa.assignment.pool_empty", "కేటాయింపు కోసం ప్రస్తుతం ఏ అధికారి అందుబాటులో లేరు");
        m.put("aa.assignment.pool_exhausted", "అధికారులందరూ తమ పని భారం పరిమితిని చేరుకున్నారు");
        m.put("aa.assignment.error_reason_required", "మాన్యువల్ కేటాయింపుకు కారణం తెలపడం తప్పనిసరి");
        m.put("aa.assignment.error_target_not_in_pool", "ఎంచుకున్న అధికారి కేటాయింపు బృందంలో లేరు");
        m.put("aa.assignment.error_target_unavailable", "ఎంచుకున్న అధికారి క్రియారహితంగా ఉన్నారు లేదా సెలవులో ఉన్నారు");
        m.put("aa.pool.threshold_updated", "పని భారం పరిమితి నవీకరించబడింది");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "పని భారం పరిమితి నవీకరించబడింది. పునఃసమతుల్యం నిర్వాహకుని చర్య కోసం పెండింగ్‌లో ఉంది.");
        m.put("aa.pool.skills_updated", "భాషా నైపుణ్యాలు నవీకరించబడ్డాయి");
        m.put("aa.pool.activation_updated", "అధికారి స్థితి నవీకరించబడింది");
        m.put("aa.pool.activation_unchanged", "అధికారి స్థితి ఇప్పటికే ఈ విలువకు నిర్ణయించబడి ఉంది");
        m.put("aa.pool.bulk_activation_complete", "ఎంచుకున్న అధికారులందరూ నవీకరించబడ్డారు");
        m.put("aa.pool.bulk_activation_partial", "కొందరు అధికారులను నవీకరించడం సాధ్యపడలేదు");
        m.put("aa.pool.rebalance_complete", "వరుస పునఃసమతుల్యం చేయబడింది");
        m.put("aa.pool.deactivate_warning_pending_work", "ఈ అధికారికి ఇంకా పరిష్కారం కాని ఫిర్యాదులు కేటాయించబడి ఉన్నాయి");
        m.put("aa.pool.deactivate_no_pending_work", "ఈ అధికారికి పరిష్కారం కాని ఫిర్యాదులు ఏవీ లేవు");
        m.put("aa.pool.notify_deactivated_with_pending",
              "పరిష్కారం కాని ఫిర్యాదులు ఉన్నప్పటికీ ఒక అధికారిని క్రియారహితం చేశారు");
        m.put("aa.pool.error_officer_not_found", "కేటాయింపు బృందంలో అధికారి కనుగొనబడలేదు");
        m.put("aa.pool.error_threshold_negative", "పని భారం పరిమితి ఋణాత్మకంగా ఉండకూడదు");
        m.put("aa.pool.error_threshold_required", "పని భారం పరిమితి విలువ తెలపడం తప్పనిసరి");
        m.put("aa.pool.error_no_officers_selected", "కనీసం ఒక అధికారిని ఎంచుకోండి");
        m.put("aa.pool.error_confirmation_required",
              "ఈ అధికారికి పరిష్కారం కాని ఫిర్యాదులు ఉన్నందున నిర్ధారణ అవసరం");
        m.put("aa.pool.error_role_group_required", "పాత్ర సమూహం తెలపడం తప్పనిసరి");
        m.put("aa.pool.error_unknown", "ఈ చర్యను పూర్తి చేయడం సాధ్యపడలేదు");
        m.put("aa.reassign.submitted", "పునఃకేటాయింపు అభ్యర్థన ఆమోదం కోసం సమర్పించబడింది");
        m.put("aa.reassign.auto_approved", "పునఃకేటాయింపు అభ్యర్థన స్వయంచాలకంగా ఆమోదించబడింది");
        m.put("aa.reassign.approved", "పునఃకేటాయింపు అభ్యర్థన ఆమోదించబడింది");
        m.put("aa.reassign.rejected", "పునఃకేటాయింపు అభ్యర్థన తిరస్కరించబడింది");
        m.put("aa.reassign.withdrawn", "పునఃకేటాయింపు అభ్యర్థన ఉపసంహరించబడింది");
        m.put("aa.reassign.notify_approved", "మీ పునఃకేటాయింపు అభ్యర్థన ఆమోదించబడింది");
        m.put("aa.reassign.notify_rejected", "మీ పునఃకేటాయింపు అభ్యర్థన తిరస్కరించబడింది");
        m.put("aa.reassign.notify_pending_approval", "ఒక పునఃకేటాయింపు అభ్యర్థన మీ ఆమోదం కోసం వేచి ఉంది");
        m.put("aa.reassign.error_appeal_required", "అప్పీలు సంఖ్య తెలపడం తప్పనిసరి");
        m.put("aa.reassign.error_reason_required", "పునఃకేటాయింపు అభ్యర్థనకు కారణం తెలపడం తప్పనిసరి");
        m.put("aa.reassign.error_identity_unresolved", "మీ గుర్తింపును నిర్ధారించడం సాధ్యపడలేదు");
        m.put("aa.reassign.error_not_assigned", "ఈ అప్పీలు ప్రస్తుతం ఎవరికీ కేటాయించబడలేదు");
        m.put("aa.reassign.error_not_holder",
              "మీకు కేటాయించబడిన అప్పీలు పునఃకేటాయింపును మాత్రమే మీరు అభ్యర్థించగలరు");
        m.put("aa.reassign.error_already_pending",
              "ఈ అప్పీలు కోసం ఒక పునఃకేటాయింపు అభ్యర్థన ఇప్పటికే పెండింగ్‌లో ఉంది");
        m.put("aa.reassign.error_request_not_found", "పునఃకేటాయింపు అభ్యర్థన కనుగొనబడలేదు");
        m.put("aa.reassign.error_not_pending", "ఈ అభ్యర్థనపై ఇప్పటికే నిర్ణయం తీసుకోబడింది");
        m.put("aa.reassign.error_not_requester", "అభ్యర్థనను లేవనెత్తిన అధికారి మాత్రమే దానిని ఉపసంహరించగలరు");
        m.put("aa.escalation.unclaimed_draft", "ఒక ముసాయిదా అనుమతించిన కాలవ్యవధి దాటినా స్వీకరించబడకుండా ఉండిపోయింది");
        m.put("aa.escalation.claimed", "ముసాయిదా స్వీకరించబడినదిగా గుర్తించబడింది");
        m.put("aa.escalation.claim_not_holder", "ఈ ముసాయిదా మీకు కేటాయించబడలేదు");
        m.put("aa.escalation.sweep_complete", "ఉన్నత స్థాయికి పంపే తనిఖీ పూర్తయింది");
        m.put("aa.admin.console_title", "అప్పీలు అధికార కేటాయింపు నిర్వహణ");
        m.put("aa.admin.officer_pool", "అధికారుల బృందం");
        m.put("aa.admin.threshold", "పని భారం పరిమితి");
        m.put("aa.admin.current_workload", "ప్రస్తుత పని భారం");
        m.put("aa.admin.status_active", "క్రియాశీలం");
        m.put("aa.admin.status_inactive", "క్రియారహితం");
        m.put("aa.admin.status_on_leave", "సెలవులో");
        m.put("aa.admin.at_threshold", "పరిమితి వద్ద");
        m.put("aa.admin.eligible", "అర్హులు");
        m.put("aa.admin.bulk_activate", "ఎంచుకున్నవారిని క్రియాశీలం చేయండి");
        m.put("aa.admin.bulk_deactivate", "ఎంచుకున్నవారిని క్రియారహితం చేయండి");
        m.put("aa.admin.edit_threshold", "పరిమితిని సవరించండి");
        m.put("aa.admin.skill_languages", "భాషా నైపుణ్యాలు");
        m.put("aa.admin.reason", "కారణం");
        m.put("aa.admin.confirm", "నిర్ధారించండి");
        m.put("aa.admin.cancel", "రద్దు చేయండి");
        m.put("aa.admin.select_all", "అన్నీ ఎంచుకోండి");
        m.put("aa.admin.pending_approvals", "పెండింగ్‌లో ఉన్న పునఃకేటాయింపు ఆమోదాలు");
        m.put("aa.admin.approve", "ఆమోదించండి");
        m.put("aa.admin.reject", "తిరస్కరించండి");
        m.put("aa.admin.rebalance", "వరుసను పునఃసమతుల్యం చేయండి");
        m.put("aa.admin.audit_trail", "తనిఖీ నమోదు");
        m.put("aa.admin.no_officers", "ఈ బృందంలో అధికారులు ఎవరూ లేరు");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "పరిమితిని 0 గా ఉంచడం అంటే అపరిమితం. నిర్ధారించడానికి 'అపరిమితం' పై గుర్తు పెట్టండి, లేదా 0 కంటే ఎక్కువ సంఖ్యను నమోదు చేయండి.");
        m.put("aa.assignment.error_concurrently_assigned",
              "ఈ అప్పీలును అదే సమయంలో మరొక వినియోగదారు కేటాయించారు. దయచేసి పేజీని రిఫ్రెష్ చేసి తిరిగి ప్రయత్నించండి.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "மேல்முறையீடு உங்களுக்கு ஒப்படைக்கப்பட்டுள்ளது");
        m.put("aa.assignment.assigned_under_grace", "வழக்கமான பணிச்சுமை வரம்பைக் கடந்து மேல்முறையீடு ஒப்படைக்கப்பட்டுள்ளது");
        m.put("aa.assignment.vernacular_override", "மொழி சார்ந்த கையாளுதலுக்காக மேல்முறையீடு உங்களுக்கு ஒப்படைக்கப்பட்டுள்ளது");
        m.put("aa.assignment.manual_override", "நிர்வாகி மேல்முறையீட்டை உங்களுக்கு ஒப்படைத்துள்ளார்");
        m.put("aa.assignment.pool_empty", "ஒப்படைப்புக்கு தற்போது எந்த அதிகாரியும் கிடைக்கவில்லை");
        m.put("aa.assignment.pool_exhausted", "அனைத்து அதிகாரிகளும் தமது பணிச்சுமை வரம்பை எட்டிவிட்டனர்");
        m.put("aa.assignment.error_reason_required", "கைமுறை ஒப்படைப்புக்கு காரணம் அளிப்பது கட்டாயம்");
        m.put("aa.assignment.error_target_not_in_pool", "தேர்ந்தெடுக்கப்பட்ட அதிகாரி ஒப்படைப்புக் குழுவில் இல்லை");
        m.put("aa.assignment.error_target_unavailable", "தேர்ந்தெடுக்கப்பட்ட அதிகாரி செயலில் இல்லை அல்லது விடுப்பில் உள்ளார்");
        m.put("aa.pool.threshold_updated", "பணிச்சுமை வரம்பு புதுப்பிக்கப்பட்டது");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "பணிச்சுமை வரம்பு புதுப்பிக்கப்பட்டது. மறுசமநிலைப்படுத்தல் நிர்வாகியின் நடவடிக்கைக்காக நிலுவையில் உள்ளது.");
        m.put("aa.pool.skills_updated", "மொழித் திறன்கள் புதுப்பிக்கப்பட்டன");
        m.put("aa.pool.activation_updated", "அதிகாரியின் நிலை புதுப்பிக்கப்பட்டது");
        m.put("aa.pool.activation_unchanged", "அதிகாரியின் நிலை ஏற்கெனவே இந்த மதிப்பிலேயே அமைக்கப்பட்டிருந்தது");
        m.put("aa.pool.bulk_activation_complete", "தேர்ந்தெடுக்கப்பட்ட அனைத்து அதிகாரிகளும் புதுப்பிக்கப்பட்டனர்");
        m.put("aa.pool.bulk_activation_partial", "சில அதிகாரிகளைப் புதுப்பிக்க முடியவில்லை");
        m.put("aa.pool.rebalance_complete", "வரிசை மறுசமநிலைப்படுத்தப்பட்டது");
        m.put("aa.pool.deactivate_warning_pending_work", "இந்த அதிகாரியிடம் இன்னும் நிலுவையில் உள்ள முறையீடுகள் ஒப்படைக்கப்பட்டுள்ளன");
        m.put("aa.pool.deactivate_no_pending_work", "இந்த அதிகாரியிடம் நிலுவையில் உள்ள முறையீடுகள் இல்லை");
        m.put("aa.pool.notify_deactivated_with_pending",
              "நிலுவையில் உள்ள முறையீடுகள் இருந்தபோதும் ஒரு அதிகாரி செயலிழக்கப்பட்டார்");
        m.put("aa.pool.error_officer_not_found", "ஒப்படைப்புக் குழுவில் அதிகாரி காணப்படவில்லை");
        m.put("aa.pool.error_threshold_negative", "பணிச்சுமை வரம்பு எதிர்மறையாக இருக்க முடியாது");
        m.put("aa.pool.error_threshold_required", "பணிச்சுமை வரம்பு மதிப்பு அளிப்பது கட்டாயம்");
        m.put("aa.pool.error_no_officers_selected", "குறைந்தது ஒரு அதிகாரியைத் தேர்ந்தெடுக்கவும்");
        m.put("aa.pool.error_confirmation_required",
              "இந்த அதிகாரியிடம் நிலுவையில் உள்ள முறையீடுகள் இருப்பதால் உறுதிப்படுத்தல் தேவை");
        m.put("aa.pool.error_role_group_required", "பணிப் பங்கு குழு அளிப்பது கட்டாயம்");
        m.put("aa.pool.error_unknown", "இந்த நடவடிக்கையை நிறைவு செய்ய முடியவில்லை");
        m.put("aa.reassign.submitted", "மறுஒப்படைப்புக் கோரிக்கை ஒப்புதலுக்காகச் சமர்ப்பிக்கப்பட்டது");
        m.put("aa.reassign.auto_approved", "மறுஒப்படைப்புக் கோரிக்கை தானாகவே ஒப்புதல் பெற்றது");
        m.put("aa.reassign.approved", "மறுஒப்படைப்புக் கோரிக்கை ஒப்புதல் பெற்றது");
        m.put("aa.reassign.rejected", "மறுஒப்படைப்புக் கோரிக்கை நிராகரிக்கப்பட்டது");
        m.put("aa.reassign.withdrawn", "மறுஒப்படைப்புக் கோரிக்கை திரும்பப் பெறப்பட்டது");
        m.put("aa.reassign.notify_approved", "உங்கள் மறுஒப்படைப்புக் கோரிக்கை ஒப்புதல் பெற்றது");
        m.put("aa.reassign.notify_rejected", "உங்கள் மறுஒப்படைப்புக் கோரிக்கை நிராகரிக்கப்பட்டது");
        m.put("aa.reassign.notify_pending_approval", "ஒரு மறுஒப்படைப்புக் கோரிக்கை உங்கள் ஒப்புதலுக்குக் காத்திருக்கிறது");
        m.put("aa.reassign.error_appeal_required", "மேல்முறையீட்டு எண் அளிப்பது கட்டாயம்");
        m.put("aa.reassign.error_reason_required", "மறுஒப்படைப்புக் கோரிக்கைக்குக் காரணம் அளிப்பது கட்டாயம்");
        m.put("aa.reassign.error_identity_unresolved", "உங்கள் அடையாளத்தை உறுதிப்படுத்த முடியவில்லை");
        m.put("aa.reassign.error_not_assigned", "இந்த மேல்முறையீடு தற்போது யாருக்கும் ஒப்படைக்கப்படவில்லை");
        m.put("aa.reassign.error_not_holder",
              "உங்களுக்கு ஒப்படைக்கப்பட்ட மேல்முறையீட்டின் மறுஒப்படைப்பை மட்டுமே நீங்கள் கோர முடியும்");
        m.put("aa.reassign.error_already_pending",
              "இந்த மேல்முறையீட்டுக்கு ஒரு மறுஒப்படைப்புக் கோரிக்கை ஏற்கெனவே நிலுவையில் உள்ளது");
        m.put("aa.reassign.error_request_not_found", "மறுஒப்படைப்புக் கோரிக்கை காணப்படவில்லை");
        m.put("aa.reassign.error_not_pending", "இந்தக் கோரிக்கையின் மீது ஏற்கெனவே முடிவு எடுக்கப்பட்டுவிட்டது");
        m.put("aa.reassign.error_not_requester", "கோரிக்கையை எழுப்பிய அதிகாரி மட்டுமே அதைத் திரும்பப் பெற முடியும்");
        m.put("aa.escalation.unclaimed_draft", "ஒரு வரைவு அனுமதிக்கப்பட்ட காலத்திற்கு மேலும் ஏற்கப்படாமல் இருந்துவிட்டது");
        m.put("aa.escalation.claimed", "வரைவு ஏற்றுக்கொள்ளப்பட்டதாகக் குறிக்கப்பட்டது");
        m.put("aa.escalation.claim_not_holder", "இந்த வரைவு உங்களுக்கு ஒப்படைக்கப்படவில்லை");
        m.put("aa.escalation.sweep_complete", "மேல்நிலைப் பரிசீலனைச் சோதனை நிறைவடைந்தது");
        m.put("aa.admin.console_title", "மேல்முறையீட்டு ஆணைய ஒப்படைப்பு நிர்வாகம்");
        m.put("aa.admin.officer_pool", "அதிகாரிகள் குழு");
        m.put("aa.admin.threshold", "பணிச்சுமை வரம்பு");
        m.put("aa.admin.current_workload", "தற்போதைய பணிச்சுமை");
        m.put("aa.admin.status_active", "செயலில்");
        m.put("aa.admin.status_inactive", "செயலில் இல்லை");
        m.put("aa.admin.status_on_leave", "விடுப்பில்");
        m.put("aa.admin.at_threshold", "வரம்பில்");
        m.put("aa.admin.eligible", "தகுதியுள்ளவர்");
        m.put("aa.admin.bulk_activate", "தேர்ந்தெடுத்தவற்றைச் செயல்படுத்து");
        m.put("aa.admin.bulk_deactivate", "தேர்ந்தெடுத்தவற்றைச் செயலிழக்கச் செய்");
        m.put("aa.admin.edit_threshold", "வரம்பைத் திருத்து");
        m.put("aa.admin.skill_languages", "மொழித் திறன்கள்");
        m.put("aa.admin.reason", "காரணம்");
        m.put("aa.admin.confirm", "உறுதிப்படுத்து");
        m.put("aa.admin.cancel", "ரத்து செய்");
        m.put("aa.admin.select_all", "அனைத்தையும் தேர்ந்தெடு");
        m.put("aa.admin.pending_approvals", "நிலுவையில் உள்ள மறுஒப்படைப்பு ஒப்புதல்கள்");
        m.put("aa.admin.approve", "ஒப்புதல் அளி");
        m.put("aa.admin.reject", "நிராகரி");
        m.put("aa.admin.rebalance", "வரிசையை மறுசமநிலைப்படுத்து");
        m.put("aa.admin.audit_trail", "தணிக்கைப் பதிவு");
        m.put("aa.admin.no_officers", "இந்தக் குழுவில் அதிகாரிகள் இல்லை");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "வரம்பை 0 எனக் குறிப்பிடுவது வரம்பற்றது என்பதைக் குறிக்கும். உறுதிப்படுத்த 'வரம்பற்றது' எனக் குறியிடுங்கள், அல்லது 0-க்கு மேற்பட்ட எண்ணை உள்ளிடுங்கள்.");
        m.put("aa.assignment.error_concurrently_assigned",
              "இந்த மேல்முறையீட்டை அதே நேரத்தில் மற்றொரு பயனர் ஒப்படைத்துவிட்டார். பக்கத்தைப் புதுப்பித்து மீண்டும் முயலுங்கள்.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "અપીલ તમને સોંપવામાં આવી છે");
        m.put("aa.assignment.assigned_under_grace", "સામાન્ય કાર્યભાર મર્યાદાથી વધુ અપીલ સોંપવામાં આવી છે");
        m.put("aa.assignment.vernacular_override", "ભાષા સંબંધિત કામગીરી માટે અપીલ તમને સોંપવામાં આવી છે");
        m.put("aa.assignment.manual_override", "પ્રશાસકે અપીલ તમને સોંપી છે");
        m.put("aa.assignment.pool_empty", "હાલમાં સોંપણી માટે કોઈ અધિકારી ઉપલબ્ધ નથી");
        m.put("aa.assignment.pool_exhausted", "બધા અધિકારીઓ તેમની કાર્યભાર મર્યાદા સુધી પહોંચી ગયા છે");
        m.put("aa.assignment.error_reason_required", "મેન્યુઅલ સોંપણી માટે કારણ આપવું આવશ્યક છે");
        m.put("aa.assignment.error_target_not_in_pool", "પસંદ કરેલા અધિકારી સોંપણી જૂથમાં નથી");
        m.put("aa.assignment.error_target_unavailable", "પસંદ કરેલા અધિકારી નિષ્ક્રિય છે અથવા રજા પર છે");
        m.put("aa.pool.threshold_updated", "કાર્યભાર મર્યાદા અદ્યતન કરવામાં આવી");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "કાર્યભાર મર્યાદા અદ્યતન કરવામાં આવી. પુનઃસંતુલન પ્રશાસકની કાર્યવાહી માટે બાકી છે.");
        m.put("aa.pool.skills_updated", "ભાષા કૌશલ્ય અદ્યતન કરવામાં આવ્યાં");
        m.put("aa.pool.activation_updated", "અધિકારીની સ્થિતિ અદ્યતન કરવામાં આવી");
        m.put("aa.pool.activation_unchanged", "અધિકારીની સ્થિતિ પહેલેથી જ આ મૂલ્ય પર નિર્ધારિત હતી");
        m.put("aa.pool.bulk_activation_complete", "પસંદ કરેલા બધા અધિકારીઓ અદ્યતન કરવામાં આવ્યા");
        m.put("aa.pool.bulk_activation_partial", "કેટલાક અધિકારીઓને અદ્યતન કરી શકાયા નથી");
        m.put("aa.pool.rebalance_complete", "કતારનું પુનઃસંતુલન કરવામાં આવ્યું");
        m.put("aa.pool.deactivate_warning_pending_work", "આ અધિકારી પાસે હજુ પણ બાકી ફરિયાદો સોંપાયેલી છે");
        m.put("aa.pool.deactivate_no_pending_work", "આ અધિકારી પાસે કોઈ બાકી ફરિયાદ નથી");
        m.put("aa.pool.notify_deactivated_with_pending",
              "બાકી ફરિયાદો હોવા છતાં એક અધિકારીને નિષ્ક્રિય કરવામાં આવ્યા");
        m.put("aa.pool.error_officer_not_found", "સોંપણી જૂથમાં અધિકારી મળ્યા નથી");
        m.put("aa.pool.error_threshold_negative", "કાર્યભાર મર્યાદા ઋણ હોઈ શકતી નથી");
        m.put("aa.pool.error_threshold_required", "કાર્યભાર મર્યાદાનું મૂલ્ય આપવું આવશ્યક છે");
        m.put("aa.pool.error_no_officers_selected", "ઓછામાં ઓછા એક અધિકારીની પસંદગી કરો");
        m.put("aa.pool.error_confirmation_required",
              "આ અધિકારી પાસે બાકી ફરિયાદો હોવાથી પુષ્ટિ આવશ્યક છે");
        m.put("aa.pool.error_role_group_required", "ભૂમિકા જૂથ આપવું આવશ્યક છે");
        m.put("aa.pool.error_unknown", "આ કાર્યવાહી પૂર્ણ કરી શકાઈ નથી");
        m.put("aa.reassign.submitted", "પુનઃસોંપણી વિનંતી મંજૂરી માટે રજૂ કરવામાં આવી");
        m.put("aa.reassign.auto_approved", "પુનઃસોંપણી વિનંતી સ્વયંસંચાલિત રીતે મંજૂર કરવામાં આવી");
        m.put("aa.reassign.approved", "પુનઃસોંપણી વિનંતી મંજૂર કરવામાં આવી");
        m.put("aa.reassign.rejected", "પુનઃસોંપણી વિનંતી નામંજૂર કરવામાં આવી");
        m.put("aa.reassign.withdrawn", "પુનઃસોંપણી વિનંતી પાછી ખેંચવામાં આવી");
        m.put("aa.reassign.notify_approved", "તમારી પુનઃસોંપણી વિનંતી મંજૂર કરવામાં આવી");
        m.put("aa.reassign.notify_rejected", "તમારી પુનઃસોંપણી વિનંતી નામંજૂર કરવામાં આવી");
        m.put("aa.reassign.notify_pending_approval", "એક પુનઃસોંપણી વિનંતી તમારી મંજૂરીની પ્રતીક્ષામાં છે");
        m.put("aa.reassign.error_appeal_required", "અપીલ ક્રમાંક આપવો આવશ્યક છે");
        m.put("aa.reassign.error_reason_required", "પુનઃસોંપણી વિનંતી માટે કારણ આપવું આવશ્યક છે");
        m.put("aa.reassign.error_identity_unresolved", "તમારી ઓળખ સ્થાપિત કરી શકાઈ નથી");
        m.put("aa.reassign.error_not_assigned", "આ અપીલ હાલમાં કોઈને સોંપવામાં આવી નથી");
        m.put("aa.reassign.error_not_holder",
              "તમે ફક્ત તમને સોંપાયેલી અપીલની પુનઃસોંપણીની વિનંતી કરી શકો છો");
        m.put("aa.reassign.error_already_pending",
              "આ અપીલ માટે એક પુનઃસોંપણી વિનંતી પહેલેથી જ બાકી છે");
        m.put("aa.reassign.error_request_not_found", "પુનઃસોંપણી વિનંતી મળી નથી");
        m.put("aa.reassign.error_not_pending", "આ વિનંતી પર પહેલેથી જ નિર્ણય લેવાઈ ગયો છે");
        m.put("aa.reassign.error_not_requester", "વિનંતી ફક્ત તે અધિકારી પાછી ખેંચી શકે જેમણે તે રજૂ કરી હતી");
        m.put("aa.escalation.unclaimed_draft", "એક મુસદ્દો અનુમત સમય કરતાં વધુ સમય સુધી અસ્વીકૃત રહ્યો");
        m.put("aa.escalation.claimed", "મુસદ્દો સ્વીકારાયેલો તરીકે ચિહ્નિત કરવામાં આવ્યો");
        m.put("aa.escalation.claim_not_holder", "આ મુસદ્દો તમને સોંપવામાં આવ્યો નથી");
        m.put("aa.escalation.sweep_complete", "ઉચ્ચ સ્તરે મોકલવાની તપાસ પૂર્ણ થઈ");
        m.put("aa.admin.console_title", "અપીલ સત્તાધિકારી સોંપણી પ્રશાસન");
        m.put("aa.admin.officer_pool", "અધિકારી જૂથ");
        m.put("aa.admin.threshold", "કાર્યભાર મર્યાદા");
        m.put("aa.admin.current_workload", "વર્તમાન કાર્યભાર");
        m.put("aa.admin.status_active", "સક્રિય");
        m.put("aa.admin.status_inactive", "નિષ્ક્રિય");
        m.put("aa.admin.status_on_leave", "રજા પર");
        m.put("aa.admin.at_threshold", "મર્યાદા પર");
        m.put("aa.admin.eligible", "પાત્ર");
        m.put("aa.admin.bulk_activate", "પસંદ કરેલાને સક્રિય કરો");
        m.put("aa.admin.bulk_deactivate", "પસંદ કરેલાને નિષ્ક્રિય કરો");
        m.put("aa.admin.edit_threshold", "મર્યાદા સંપાદિત કરો");
        m.put("aa.admin.skill_languages", "ભાષા કૌશલ્ય");
        m.put("aa.admin.reason", "કારણ");
        m.put("aa.admin.confirm", "પુષ્ટિ કરો");
        m.put("aa.admin.cancel", "રદ કરો");
        m.put("aa.admin.select_all", "બધા પસંદ કરો");
        m.put("aa.admin.pending_approvals", "બાકી પુનઃસોંપણી મંજૂરીઓ");
        m.put("aa.admin.approve", "મંજૂર કરો");
        m.put("aa.admin.reject", "નામંજૂર કરો");
        m.put("aa.admin.rebalance", "કતારનું પુનઃસંતુલન કરો");
        m.put("aa.admin.audit_trail", "ઑડિટ નોંધ");
        m.put("aa.admin.no_officers", "આ જૂથમાં કોઈ અધિકારી નથી");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "મર્યાદા 0 રાખવાનો અર્થ અમર્યાદિત થાય છે. પુષ્ટિ માટે 'અમર્યાદિત' પર નિશાન કરો, અથવા 0 થી વધુ કોઈ સંખ્યા દાખલ કરો.");
        m.put("aa.assignment.error_concurrently_assigned",
              "આ અપીલ તે જ સમયે અન્ય વપરાશકર્તાએ સોંપી દીધી હતી. કૃપા કરીને પૃષ્ઠ તાજું કરીને ફરી પ્રયાસ કરો.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "اپیل آپ کے سپرد کی گئی ہے");
        m.put("aa.assignment.assigned_under_grace", "معمول کی کام کی حد سے زیادہ اپیل سپرد کی گئی ہے");
        m.put("aa.assignment.vernacular_override", "زبان سے متعلق کارروائی کے لیے اپیل آپ کے سپرد کی گئی ہے");
        m.put("aa.assignment.manual_override", "منتظم نے اپیل آپ کے سپرد کی ہے");
        m.put("aa.assignment.pool_empty", "اس وقت سپردگی کے لیے کوئی افسر دستیاب نہیں ہے");
        m.put("aa.assignment.pool_exhausted", "تمام افسران اپنی کام کی حد تک پہنچ چکے ہیں");
        m.put("aa.assignment.error_reason_required", "دستی سپردگی کے لیے وجہ بتانا لازمی ہے");
        m.put("aa.assignment.error_target_not_in_pool", "منتخب افسر سپردگی گروپ میں شامل نہیں ہے");
        m.put("aa.assignment.error_target_unavailable", "منتخب افسر غیر فعال ہے یا رخصت پر ہے");
        m.put("aa.pool.threshold_updated", "کام کی حد تازہ کر دی گئی");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "کام کی حد تازہ کر دی گئی۔ توازن کی بحالی منتظم کی کارروائی کے لیے زیرِ التوا ہے۔");
        m.put("aa.pool.skills_updated", "زبان کی مہارتیں تازہ کر دی گئیں");
        m.put("aa.pool.activation_updated", "افسر کی حالت تازہ کر دی گئی");
        m.put("aa.pool.activation_unchanged", "افسر کی حالت پہلے ہی اسی قیمت پر مقرر تھی");
        m.put("aa.pool.bulk_activation_complete", "منتخب کردہ تمام افسران تازہ کر دیے گئے");
        m.put("aa.pool.bulk_activation_partial", "بعض افسران کو تازہ نہیں کیا جا سکا");
        m.put("aa.pool.rebalance_complete", "قطار کا توازن بحال کر دیا گیا");
        m.put("aa.pool.deactivate_warning_pending_work", "اس افسر کے پاس اب بھی زیرِ التوا شکایات سپرد ہیں");
        m.put("aa.pool.deactivate_no_pending_work", "اس افسر کے پاس کوئی زیرِ التوا شکایت نہیں ہے");
        m.put("aa.pool.notify_deactivated_with_pending",
              "زیرِ التوا شکایات موجود ہونے کے باوجود ایک افسر کو غیر فعال کر دیا گیا");
        m.put("aa.pool.error_officer_not_found", "سپردگی گروپ میں افسر نہیں ملا");
        m.put("aa.pool.error_threshold_negative", "کام کی حد منفی نہیں ہو سکتی");
        m.put("aa.pool.error_threshold_required", "کام کی حد کی قیمت دینا لازمی ہے");
        m.put("aa.pool.error_no_officers_selected", "کم از کم ایک افسر منتخب کریں");
        m.put("aa.pool.error_confirmation_required",
              "اس افسر کے پاس زیرِ التوا شکایات ہونے کی وجہ سے تصدیق لازمی ہے");
        m.put("aa.pool.error_role_group_required", "کردار گروپ دینا لازمی ہے");
        m.put("aa.pool.error_unknown", "یہ کارروائی مکمل نہیں کی جا سکی");
        m.put("aa.reassign.submitted", "دوبارہ سپردگی کی درخواست منظوری کے لیے پیش کر دی گئی");
        m.put("aa.reassign.auto_approved", "دوبارہ سپردگی کی درخواست خود بخود منظور ہو گئی");
        m.put("aa.reassign.approved", "دوبارہ سپردگی کی درخواست منظور کر لی گئی");
        m.put("aa.reassign.rejected", "دوبارہ سپردگی کی درخواست مسترد کر دی گئی");
        m.put("aa.reassign.withdrawn", "دوبارہ سپردگی کی درخواست واپس لے لی گئی");
        m.put("aa.reassign.notify_approved", "آپ کی دوبارہ سپردگی کی درخواست منظور کر لی گئی");
        m.put("aa.reassign.notify_rejected", "آپ کی دوبارہ سپردگی کی درخواست مسترد کر دی گئی");
        m.put("aa.reassign.notify_pending_approval", "ایک دوبارہ سپردگی کی درخواست آپ کی منظوری کی منتظر ہے");
        m.put("aa.reassign.error_appeal_required", "اپیل نمبر دینا لازمی ہے");
        m.put("aa.reassign.error_reason_required", "دوبارہ سپردگی کی درخواست کے لیے وجہ بتانا لازمی ہے");
        m.put("aa.reassign.error_identity_unresolved", "آپ کی شناخت ثابت نہیں کی جا سکی");
        m.put("aa.reassign.error_not_assigned", "یہ اپیل اس وقت کسی کے سپرد نہیں ہے");
        m.put("aa.reassign.error_not_holder",
              "آپ صرف اپنے سپرد کی گئی اپیل کی دوبارہ سپردگی کی درخواست کر سکتے ہیں");
        m.put("aa.reassign.error_already_pending",
              "اس اپیل کے لیے ایک دوبارہ سپردگی کی درخواست پہلے ہی زیرِ التوا ہے");
        m.put("aa.reassign.error_request_not_found", "دوبارہ سپردگی کی درخواست نہیں ملی");
        m.put("aa.reassign.error_not_pending", "اس درخواست پر پہلے ہی فیصلہ ہو چکا ہے");
        m.put("aa.reassign.error_not_requester", "درخواست صرف وہی افسر واپس لے سکتا ہے جس نے اسے پیش کیا تھا");
        m.put("aa.escalation.unclaimed_draft", "ایک مسودہ مقررہ مدت سے زیادہ عرصے تک غیر منظور شدہ پڑا رہا");
        m.put("aa.escalation.claimed", "مسودہ وصول شدہ کے طور پر نشان زد کیا گیا");
        m.put("aa.escalation.claim_not_holder", "یہ مسودہ آپ کے سپرد نہیں ہے");
        m.put("aa.escalation.sweep_complete", "بالا سطح پر ارسال کی جانچ مکمل ہوئی");
        m.put("aa.admin.console_title", "اپیلٹ اتھارٹی سپردگی انتظامیہ");
        m.put("aa.admin.officer_pool", "افسران کا گروپ");
        m.put("aa.admin.threshold", "کام کی حد");
        m.put("aa.admin.current_workload", "موجودہ کام کا بوجھ");
        m.put("aa.admin.status_active", "فعال");
        m.put("aa.admin.status_inactive", "غیر فعال");
        m.put("aa.admin.status_on_leave", "رخصت پر");
        m.put("aa.admin.at_threshold", "حد پر");
        m.put("aa.admin.eligible", "اہل");
        m.put("aa.admin.bulk_activate", "منتخب کردہ کو فعال کریں");
        m.put("aa.admin.bulk_deactivate", "منتخب کردہ کو غیر فعال کریں");
        m.put("aa.admin.edit_threshold", "حد میں ترمیم کریں");
        m.put("aa.admin.skill_languages", "زبان کی مہارتیں");
        m.put("aa.admin.reason", "وجہ");
        m.put("aa.admin.confirm", "تصدیق کریں");
        m.put("aa.admin.cancel", "منسوخ کریں");
        m.put("aa.admin.select_all", "سب منتخب کریں");
        m.put("aa.admin.pending_approvals", "زیرِ التوا دوبارہ سپردگی کی منظوریاں");
        m.put("aa.admin.approve", "منظور کریں");
        m.put("aa.admin.reject", "مسترد کریں");
        m.put("aa.admin.rebalance", "قطار کا توازن بحال کریں");
        m.put("aa.admin.audit_trail", "آڈٹ ریکارڈ");
        m.put("aa.admin.no_officers", "اس گروپ میں کوئی افسر نہیں ہے");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "حد کو 0 مقرر کرنے کا مطلب لامحدود ہے۔ تصدیق کے لیے 'لامحدود' پر نشان لگائیں، یا 0 سے زیادہ کوئی عدد درج کریں۔");
        m.put("aa.assignment.error_concurrently_assigned",
              "یہ اپیل اسی وقت کسی دوسرے صارف نے سپرد کر دی تھی۔ براہِ کرم صفحہ تازہ کر کے دوبارہ کوشش کریں۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "ಮೇಲ್ಮನವಿಯನ್ನು ನಿಮಗೆ ವಹಿಸಲಾಗಿದೆ");
        m.put("aa.assignment.assigned_under_grace", "ಸಾಮಾನ್ಯ ಕೆಲಸದ ಹೊರೆ ಮಿತಿಗಿಂತ ಹೆಚ್ಚಾಗಿ ಮೇಲ್ಮನವಿ ವಹಿಸಲಾಗಿದೆ");
        m.put("aa.assignment.vernacular_override", "ಭಾಷಾ ಸಂಬಂಧಿತ ನಿರ್ವಹಣೆಗಾಗಿ ಮೇಲ್ಮನವಿಯನ್ನು ನಿಮಗೆ ವಹಿಸಲಾಗಿದೆ");
        m.put("aa.assignment.manual_override", "ನಿರ್ವಾಹಕರು ಮೇಲ್ಮನವಿಯನ್ನು ನಿಮಗೆ ವಹಿಸಿದ್ದಾರೆ");
        m.put("aa.assignment.pool_empty", "ಸದ್ಯಕ್ಕೆ ವಹಿಸಲು ಯಾವುದೇ ಅಧಿಕಾರಿ ಲಭ್ಯವಿಲ್ಲ");
        m.put("aa.assignment.pool_exhausted", "ಎಲ್ಲಾ ಅಧಿಕಾರಿಗಳು ತಮ್ಮ ಕೆಲಸದ ಹೊರೆ ಮಿತಿಯನ್ನು ತಲುಪಿದ್ದಾರೆ");
        m.put("aa.assignment.error_reason_required", "ಕೈಯಾರೆ ವಹಿಸುವಿಕೆಗೆ ಕಾರಣ ನೀಡುವುದು ಕಡ್ಡಾಯ");
        m.put("aa.assignment.error_target_not_in_pool", "ಆಯ್ಕೆ ಮಾಡಿದ ಅಧಿಕಾರಿ ವಹಿಸುವಿಕೆ ಗುಂಪಿನಲ್ಲಿ ಇಲ್ಲ");
        m.put("aa.assignment.error_target_unavailable", "ಆಯ್ಕೆ ಮಾಡಿದ ಅಧಿಕಾರಿ ನಿಷ್ಕ್ರಿಯರಾಗಿದ್ದಾರೆ ಅಥವಾ ರಜೆಯಲ್ಲಿದ್ದಾರೆ");
        m.put("aa.pool.threshold_updated", "ಕೆಲಸದ ಹೊರೆ ಮಿತಿಯನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "ಕೆಲಸದ ಹೊರೆ ಮಿತಿಯನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ. ಮರುಸಮತೋಲನ ನಿರ್ವಾಹಕರ ಕ್ರಮಕ್ಕಾಗಿ ಬಾಕಿ ಉಳಿದಿದೆ.");
        m.put("aa.pool.skills_updated", "ಭಾಷಾ ಕೌಶಲ್ಯಗಳನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ");
        m.put("aa.pool.activation_updated", "ಅಧಿಕಾರಿಯ ಸ್ಥಿತಿಯನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ");
        m.put("aa.pool.activation_unchanged", "ಅಧಿಕಾರಿಯ ಸ್ಥಿತಿ ಈಗಾಗಲೇ ಇದೇ ಮೌಲ್ಯಕ್ಕೆ ನಿಗದಿಯಾಗಿತ್ತು");
        m.put("aa.pool.bulk_activation_complete", "ಆಯ್ಕೆ ಮಾಡಿದ ಎಲ್ಲಾ ಅಧಿಕಾರಿಗಳನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ");
        m.put("aa.pool.bulk_activation_partial", "ಕೆಲವು ಅಧಿಕಾರಿಗಳನ್ನು ನವೀಕರಿಸಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ");
        m.put("aa.pool.rebalance_complete", "ಸರತಿಯನ್ನು ಮರುಸಮತೋಲನಗೊಳಿಸಲಾಗಿದೆ");
        m.put("aa.pool.deactivate_warning_pending_work", "ಈ ಅಧಿಕಾರಿಗೆ ಇನ್ನೂ ಬಾಕಿ ಇರುವ ದೂರುಗಳನ್ನು ವಹಿಸಲಾಗಿದೆ");
        m.put("aa.pool.deactivate_no_pending_work", "ಈ ಅಧಿಕಾರಿಗೆ ಯಾವುದೇ ಬಾಕಿ ದೂರುಗಳಿಲ್ಲ");
        m.put("aa.pool.notify_deactivated_with_pending",
              "ಬಾಕಿ ದೂರುಗಳು ಇದ್ದರೂ ಒಬ್ಬ ಅಧಿಕಾರಿಯನ್ನು ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಲಾಗಿದೆ");
        m.put("aa.pool.error_officer_not_found", "ವಹಿಸುವಿಕೆ ಗುಂಪಿನಲ್ಲಿ ಅಧಿಕಾರಿ ಕಂಡುಬಂದಿಲ್ಲ");
        m.put("aa.pool.error_threshold_negative", "ಕೆಲಸದ ಹೊರೆ ಮಿತಿ ಋಣಾತ್ಮಕವಾಗಿರಲು ಸಾಧ್ಯವಿಲ್ಲ");
        m.put("aa.pool.error_threshold_required", "ಕೆಲಸದ ಹೊರೆ ಮಿತಿಯ ಮೌಲ್ಯ ನೀಡುವುದು ಕಡ್ಡಾಯ");
        m.put("aa.pool.error_no_officers_selected", "ಕನಿಷ್ಠ ಒಬ್ಬ ಅಧಿಕಾರಿಯನ್ನು ಆಯ್ಕೆ ಮಾಡಿ");
        m.put("aa.pool.error_confirmation_required",
              "ಈ ಅಧಿಕಾರಿಗೆ ಬಾಕಿ ದೂರುಗಳು ಇರುವುದರಿಂದ ದೃಢೀಕರಣ ಅಗತ್ಯ");
        m.put("aa.pool.error_role_group_required", "ಪಾತ್ರ ಗುಂಪು ನೀಡುವುದು ಕಡ್ಡಾಯ");
        m.put("aa.pool.error_unknown", "ಈ ಕ್ರಮವನ್ನು ಪೂರ್ಣಗೊಳಿಸಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ");
        m.put("aa.reassign.submitted", "ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ಅನುಮೋದನೆಗಾಗಿ ಸಲ್ಲಿಸಲಾಗಿದೆ");
        m.put("aa.reassign.auto_approved", "ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆ ಸ್ವಯಂಚಾಲಿತವಾಗಿ ಅನುಮೋದನೆಯಾಗಿದೆ");
        m.put("aa.reassign.approved", "ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ಅನುಮೋದಿಸಲಾಗಿದೆ");
        m.put("aa.reassign.rejected", "ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ತಿರಸ್ಕರಿಸಲಾಗಿದೆ");
        m.put("aa.reassign.withdrawn", "ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ಹಿಂಪಡೆಯಲಾಗಿದೆ");
        m.put("aa.reassign.notify_approved", "ನಿಮ್ಮ ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ಅನುಮೋದಿಸಲಾಗಿದೆ");
        m.put("aa.reassign.notify_rejected", "ನಿಮ್ಮ ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ತಿರಸ್ಕರಿಸಲಾಗಿದೆ");
        m.put("aa.reassign.notify_pending_approval", "ಒಂದು ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆ ನಿಮ್ಮ ಅನುಮೋದನೆಗಾಗಿ ಕಾಯುತ್ತಿದೆ");
        m.put("aa.reassign.error_appeal_required", "ಮೇಲ್ಮನವಿ ಸಂಖ್ಯೆ ನೀಡುವುದು ಕಡ್ಡಾಯ");
        m.put("aa.reassign.error_reason_required", "ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಗೆ ಕಾರಣ ನೀಡುವುದು ಕಡ್ಡಾಯ");
        m.put("aa.reassign.error_identity_unresolved", "ನಿಮ್ಮ ಗುರುತನ್ನು ಸ್ಥಾಪಿಸಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ");
        m.put("aa.reassign.error_not_assigned", "ಈ ಮೇಲ್ಮನವಿಯನ್ನು ಸದ್ಯಕ್ಕೆ ಯಾರಿಗೂ ವಹಿಸಲಾಗಿಲ್ಲ");
        m.put("aa.reassign.error_not_holder",
              "ನಿಮಗೆ ವಹಿಸಲಾದ ಮೇಲ್ಮನವಿಯ ಮರುವಹಿಸುವಿಕೆಯನ್ನು ಮಾತ್ರ ನೀವು ಕೋರಬಹುದು");
        m.put("aa.reassign.error_already_pending",
              "ಈ ಮೇಲ್ಮನವಿಗಾಗಿ ಒಂದು ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆ ಈಗಾಗಲೇ ಬಾಕಿ ಇದೆ");
        m.put("aa.reassign.error_request_not_found", "ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆ ಕಂಡುಬಂದಿಲ್ಲ");
        m.put("aa.reassign.error_not_pending", "ಈ ಕೋರಿಕೆಯ ಮೇಲೆ ಈಗಾಗಲೇ ನಿರ್ಧಾರ ತೆಗೆದುಕೊಳ್ಳಲಾಗಿದೆ");
        m.put("aa.reassign.error_not_requester", "ಕೋರಿಕೆಯನ್ನು ಸಲ್ಲಿಸಿದ ಅಧಿಕಾರಿ ಮಾತ್ರ ಅದನ್ನು ಹಿಂಪಡೆಯಬಹುದು");
        m.put("aa.escalation.unclaimed_draft", "ಒಂದು ಕರಡು ಅನುಮತಿಸಿದ ಅವಧಿಯನ್ನು ಮೀರಿಯೂ ಸ್ವೀಕರಿಸದೆ ಉಳಿದಿದೆ");
        m.put("aa.escalation.claimed", "ಕರಡನ್ನು ಸ್ವೀಕರಿಸಲಾಗಿದೆ ಎಂದು ಗುರುತಿಸಲಾಗಿದೆ");
        m.put("aa.escalation.claim_not_holder", "ಈ ಕರಡನ್ನು ನಿಮಗೆ ವಹಿಸಲಾಗಿಲ್ಲ");
        m.put("aa.escalation.sweep_complete", "ಮೇಲಿನ ಹಂತಕ್ಕೆ ಕಳುಹಿಸುವ ಪರಿಶೀಲನೆ ಪೂರ್ಣಗೊಂಡಿದೆ");
        m.put("aa.admin.console_title", "ಮೇಲ್ಮನವಿ ಪ್ರಾಧಿಕಾರ ವಹಿಸುವಿಕೆ ನಿರ್ವಹಣೆ");
        m.put("aa.admin.officer_pool", "ಅಧಿಕಾರಿಗಳ ಗುಂಪು");
        m.put("aa.admin.threshold", "ಕೆಲಸದ ಹೊರೆ ಮಿತಿ");
        m.put("aa.admin.current_workload", "ಪ್ರಸ್ತುತ ಕೆಲಸದ ಹೊರೆ");
        m.put("aa.admin.status_active", "ಸಕ್ರಿಯ");
        m.put("aa.admin.status_inactive", "ನಿಷ್ಕ್ರಿಯ");
        m.put("aa.admin.status_on_leave", "ರಜೆಯಲ್ಲಿ");
        m.put("aa.admin.at_threshold", "ಮಿತಿಯಲ್ಲಿ");
        m.put("aa.admin.eligible", "ಅರ್ಹ");
        m.put("aa.admin.bulk_activate", "ಆಯ್ಕೆ ಮಾಡಿದವರನ್ನು ಸಕ್ರಿಯಗೊಳಿಸಿ");
        m.put("aa.admin.bulk_deactivate", "ಆಯ್ಕೆ ಮಾಡಿದವರನ್ನು ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಿ");
        m.put("aa.admin.edit_threshold", "ಮಿತಿಯನ್ನು ಸಂಪಾದಿಸಿ");
        m.put("aa.admin.skill_languages", "ಭಾಷಾ ಕೌಶಲ್ಯಗಳು");
        m.put("aa.admin.reason", "ಕಾರಣ");
        m.put("aa.admin.confirm", "ದೃಢೀಕರಿಸಿ");
        m.put("aa.admin.cancel", "ರದ್ದುಗೊಳಿಸಿ");
        m.put("aa.admin.select_all", "ಎಲ್ಲವನ್ನೂ ಆಯ್ಕೆ ಮಾಡಿ");
        m.put("aa.admin.pending_approvals", "ಬಾಕಿ ಇರುವ ಮರುವಹಿಸುವಿಕೆ ಅನುಮೋದನೆಗಳು");
        m.put("aa.admin.approve", "ಅನುಮೋದಿಸಿ");
        m.put("aa.admin.reject", "ತಿರಸ್ಕರಿಸಿ");
        m.put("aa.admin.rebalance", "ಸರತಿಯನ್ನು ಮರುಸಮತೋಲನಗೊಳಿಸಿ");
        m.put("aa.admin.audit_trail", "ಲೆಕ್ಕಪರಿಶೋಧನಾ ದಾಖಲೆ");
        m.put("aa.admin.no_officers", "ಈ ಗುಂಪಿನಲ್ಲಿ ಯಾವುದೇ ಅಧಿಕಾರಿಗಳಿಲ್ಲ");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "ಮಿತಿಯನ್ನು 0 ಎಂದು ನಿಗದಿಪಡಿಸಿದರೆ ಅದರ ಅರ್ಥ ಅಪರಿಮಿತ. ದೃಢೀಕರಿಸಲು 'ಅಪರಿಮಿತ' ಎಂಬುದನ್ನು ಗುರುತಿಸಿ, ಅಥವಾ 0 ಕ್ಕಿಂತ ಹೆಚ್ಚಿನ ಸಂಖ್ಯೆಯನ್ನು ನಮೂದಿಸಿ.");
        m.put("aa.assignment.error_concurrently_assigned",
              "ಈ ಮೇಲ್ಮನವಿಯನ್ನು ಅದೇ ಸಮಯದಲ್ಲಿ ಬೇರೊಬ್ಬ ಬಳಕೆದಾರರು ವಹಿಸಿದ್ದಾರೆ. ದಯವಿಟ್ಟು ಪುಟವನ್ನು ಹೊಸದಾಗಿ ಲೋಡ್ ಮಾಡಿ ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.assignment.assigned", "അപ്പീൽ നിങ്ങൾക്ക് നൽകിയിട്ടുണ്ട്");
        m.put("aa.assignment.assigned_under_grace", "സാധാരണ ജോലിഭാര പരിധിക്ക് മുകളിൽ അപ്പീൽ നൽകിയിട്ടുണ്ട്");
        m.put("aa.assignment.vernacular_override", "ഭാഷാ സംബന്ധമായ കൈകാര്യത്തിനായി അപ്പീൽ നിങ്ങൾക്ക് നൽകിയിട്ടുണ്ട്");
        m.put("aa.assignment.manual_override", "അഡ്മിനിസ്ട്രേറ്റർ അപ്പീൽ നിങ്ങൾക്ക് നൽകിയിട്ടുണ്ട്");
        m.put("aa.assignment.pool_empty", "നിലവിൽ ചുമതല നൽകാൻ ഒരു ഓഫീസറും ലഭ്യമല്ല");
        m.put("aa.assignment.pool_exhausted", "എല്ലാ ഓഫീസർമാരും അവരുടെ ജോലിഭാര പരിധിയിൽ എത്തിയിട്ടുണ്ട്");
        m.put("aa.assignment.error_reason_required", "സ്വമേധയാ ചുമതല നൽകുന്നതിന് കാരണം നൽകേണ്ടത് നിർബന്ധമാണ്");
        m.put("aa.assignment.error_target_not_in_pool", "തിരഞ്ഞെടുത്ത ഓഫീസർ ചുമതലാ സംഘത്തിൽ ഇല്ല");
        m.put("aa.assignment.error_target_unavailable", "തിരഞ്ഞെടുത്ത ഓഫീസർ നിഷ്ക്രിയമാണ് അല്ലെങ്കിൽ അവധിയിലാണ്");
        m.put("aa.pool.threshold_updated", "ജോലിഭാര പരിധി പുതുക്കി");
        m.put("aa.pool.threshold_updated_rebalance_pending",
              "ജോലിഭാര പരിധി പുതുക്കി. പുനഃസന്തുലനം അഡ്മിനിസ്ട്രേറ്ററുടെ നടപടിക്കായി കാത്തിരിക്കുന്നു.");
        m.put("aa.pool.skills_updated", "ഭാഷാ വൈദഗ്ധ്യം പുതുക്കി");
        m.put("aa.pool.activation_updated", "ഓഫീസറുടെ നില പുതുക്കി");
        m.put("aa.pool.activation_unchanged", "ഓഫീസറുടെ നില ഇതിനകം ഈ മൂല്യത്തിൽ തന്നെ നിശ്ചയിച്ചിരുന്നു");
        m.put("aa.pool.bulk_activation_complete", "തിരഞ്ഞെടുത്ത എല്ലാ ഓഫീസർമാരും പുതുക്കി");
        m.put("aa.pool.bulk_activation_partial", "ചില ഓഫീസർമാരെ പുതുക്കാൻ കഴിഞ്ഞില്ല");
        m.put("aa.pool.rebalance_complete", "ക്യൂ പുനഃസന്തുലിതമാക്കി");
        m.put("aa.pool.deactivate_warning_pending_work", "ഈ ഓഫീസർക്ക് ഇപ്പോഴും തീർപ്പാകാത്ത പരാതികൾ നൽകിയിട്ടുണ്ട്");
        m.put("aa.pool.deactivate_no_pending_work", "ഈ ഓഫീസർക്ക് തീർപ്പാകാത്ത പരാതികൾ ഇല്ല");
        m.put("aa.pool.notify_deactivated_with_pending",
              "തീർപ്പാകാത്ത പരാതികൾ ഉണ്ടായിരുന്നിട്ടും ഒരു ഓഫീസറെ നിഷ്ക്രിയമാക്കി");
        m.put("aa.pool.error_officer_not_found", "ചുമതലാ സംഘത്തിൽ ഓഫീസറെ കണ്ടെത്തിയില്ല");
        m.put("aa.pool.error_threshold_negative", "ജോലിഭാര പരിധി ന്യൂനമായിരിക്കാൻ കഴിയില്ല");
        m.put("aa.pool.error_threshold_required", "ജോലിഭാര പരിധിയുടെ മൂല്യം നൽകേണ്ടത് നിർബന്ധമാണ്");
        m.put("aa.pool.error_no_officers_selected", "ചുരുങ്ങിയത് ഒരു ഓഫീസറെ തിരഞ്ഞെടുക്കുക");
        m.put("aa.pool.error_confirmation_required",
              "ഈ ഓഫീസർക്ക് തീർപ്പാകാത്ത പരാതികൾ ഉള്ളതിനാൽ സ്ഥിരീകരണം ആവശ്യമാണ്");
        m.put("aa.pool.error_role_group_required", "ചുമതലാ വിഭാഗം നൽകേണ്ടത് നിർബന്ധമാണ്");
        m.put("aa.pool.error_unknown", "ഈ നടപടി പൂർത്തിയാക്കാൻ കഴിഞ്ഞില്ല");
        m.put("aa.reassign.submitted", "പുനർചുമതലാ അഭ്യർത്ഥന അനുമതിക്കായി സമർപ്പിച്ചു");
        m.put("aa.reassign.auto_approved", "പുനർചുമതലാ അഭ്യർത്ഥന സ്വയമേവ അനുവദിച്ചു");
        m.put("aa.reassign.approved", "പുനർചുമതലാ അഭ്യർത്ഥന അനുവദിച്ചു");
        m.put("aa.reassign.rejected", "പുനർചുമതലാ അഭ്യർത്ഥന നിരസിച്ചു");
        m.put("aa.reassign.withdrawn", "പുനർചുമതലാ അഭ്യർത്ഥന പിൻവലിച്ചു");
        m.put("aa.reassign.notify_approved", "നിങ്ങളുടെ പുനർചുമതലാ അഭ്യർത്ഥന അനുവദിച്ചു");
        m.put("aa.reassign.notify_rejected", "നിങ്ങളുടെ പുനർചുമതലാ അഭ്യർത്ഥന നിരസിച്ചു");
        m.put("aa.reassign.notify_pending_approval", "ഒരു പുനർചുമതലാ അഭ്യർത്ഥന നിങ്ങളുടെ അനുമതിക്കായി കാത്തിരിക്കുന്നു");
        m.put("aa.reassign.error_appeal_required", "അപ്പീൽ നമ്പർ നൽകേണ്ടത് നിർബന്ധമാണ്");
        m.put("aa.reassign.error_reason_required", "പുനർചുമതലാ അഭ്യർത്ഥനയ്ക്ക് കാരണം നൽകേണ്ടത് നിർബന്ധമാണ്");
        m.put("aa.reassign.error_identity_unresolved", "നിങ്ങളുടെ വ്യക്തിത്വം സ്ഥാപിക്കാൻ കഴിഞ്ഞില്ല");
        m.put("aa.reassign.error_not_assigned", "ഈ അപ്പീൽ നിലവിൽ ആർക്കും നൽകിയിട്ടില്ല");
        m.put("aa.reassign.error_not_holder",
              "നിങ്ങൾക്ക് നൽകിയ അപ്പീലിന്റെ പുനർചുമതല മാത്രമേ നിങ്ങൾ അഭ്യർത്ഥിക്കാൻ കഴിയൂ");
        m.put("aa.reassign.error_already_pending",
              "ഈ അപ്പീലിനായി ഒരു പുനർചുമതലാ അഭ്യർത്ഥന ഇതിനകം തീർപ്പാകാതെ ഉണ്ട്");
        m.put("aa.reassign.error_request_not_found", "പുനർചുമതലാ അഭ്യർത്ഥന കണ്ടെത്തിയില്ല");
        m.put("aa.reassign.error_not_pending", "ഈ അഭ്യർത്ഥനയിൽ ഇതിനകം തീരുമാനമെടുത്തിട്ടുണ്ട്");
        m.put("aa.reassign.error_not_requester", "അഭ്യർത്ഥന ഉന്നയിച്ച ഓഫീസർക്ക് മാത്രമേ അത് പിൻവലിക്കാൻ കഴിയൂ");
        m.put("aa.escalation.unclaimed_draft", "ഒരു കരട് അനുവദിച്ച സമയത്തിനു ശേഷവും സ്വീകരിക്കപ്പെടാതെ കിടന്നു");
        m.put("aa.escalation.claimed", "കരട് സ്വീകരിച്ചതായി അടയാളപ്പെടുത്തി");
        m.put("aa.escalation.claim_not_holder", "ഈ കരട് നിങ്ങൾക്ക് നൽകിയിട്ടില്ല");
        m.put("aa.escalation.sweep_complete", "ഉയർന്ന തലത്തിലേക്ക് കൈമാറ്റ പരിശോധന പൂർത്തിയായി");
        m.put("aa.admin.console_title", "അപ്പീൽ അധികാരി ചുമതലാ ഭരണനിർവഹണം");
        m.put("aa.admin.officer_pool", "ഓഫീസർ സംഘം");
        m.put("aa.admin.threshold", "ജോലിഭാര പരിധി");
        m.put("aa.admin.current_workload", "നിലവിലുള്ള ജോലിഭാരം");
        m.put("aa.admin.status_active", "സജീവം");
        m.put("aa.admin.status_inactive", "നിഷ്ക്രിയം");
        m.put("aa.admin.status_on_leave", "അവധിയിൽ");
        m.put("aa.admin.at_threshold", "പരിധിയിൽ");
        m.put("aa.admin.eligible", "യോഗ്യൻ");
        m.put("aa.admin.bulk_activate", "തിരഞ്ഞെടുത്തവ സജീവമാക്കുക");
        m.put("aa.admin.bulk_deactivate", "തിരഞ്ഞെടുത്തവ നിഷ്ക്രിയമാക്കുക");
        m.put("aa.admin.edit_threshold", "പരിധി തിരുത്തുക");
        m.put("aa.admin.skill_languages", "ഭാഷാ വൈദഗ്ധ്യം");
        m.put("aa.admin.reason", "കാരണം");
        m.put("aa.admin.confirm", "സ്ഥിരീകരിക്കുക");
        m.put("aa.admin.cancel", "റദ്ദാക്കുക");
        m.put("aa.admin.select_all", "എല്ലാം തിരഞ്ഞെടുക്കുക");
        m.put("aa.admin.pending_approvals", "തീർപ്പാകാത്ത പുനർചുമതലാ അനുമതികൾ");
        m.put("aa.admin.approve", "അനുവദിക്കുക");
        m.put("aa.admin.reject", "നിരസിക്കുക");
        m.put("aa.admin.rebalance", "ക്യൂ പുനഃസന്തുലിതമാക്കുക");
        m.put("aa.admin.audit_trail", "ഓഡിറ്റ് രേഖ");
        m.put("aa.admin.no_officers", "ഈ സംഘത്തിൽ ഓഫീസർമാരില്ല");
        m.put("aa.pool.error_threshold_zero_ambiguous",
              "പരിധി 0 ആയി നിശ്ചയിക്കുന്നത് പരിധിയില്ലാത്തത് എന്നാണ് അർത്ഥം. സ്ഥിരീകരിക്കാൻ 'പരിധിയില്ലാത്തത്' എന്നതിൽ അടയാളമിടുക, അല്ലെങ്കിൽ 0-ൽ കൂടുതലുള്ള ഒരു സംഖ്യ നൽകുക.");
        m.put("aa.assignment.error_concurrently_assigned",
              "ഈ അപ്പീൽ അതേ സമയത്ത് മറ്റൊരു ഉപയോക്താവ് നൽകിയിരുന്നു. ദയവായി പേജ് പുതുക്കി വീണ്ടും ശ്രമിക്കുക.");
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
