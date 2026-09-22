package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appellate Authority hearing scheduling, order issuance and the notices raised by both.
 *
 * <p>Four groups of keys:
 *
 * <ul>
 *   <li>{@code aa.notify.*} — messages addressed to the citizen/appellant about their own appeal.
 *   <li>{@code aa.notify.officer.*} — bell notifications raised to registrars, bench officers and
 *       the appellate authority.
 *   <li>{@code aa.hearing.*} / {@code aa.order.*} success and failure strings returned by the
 *       hearing and order APIs.
 *   <li>Table and label vocabulary rendered by the hearing-history and notice-log components.
 * </ul>
 *
 * <p>Wording is deliberately NEUTRAL and FACTUAL. No statutory or Scheme text, no clause numbers
 * and no prescribed notice formulation is reproduced here — the authoritative wording of a legal
 * notice has not been supplied, so these strings only state what happened. Anything intended to
 * carry legal effect must be reviewed and replaced before go-live.
 *
 * <p>Seeded in all ten supported locales, following {@link AaAssignmentTranslationSeeder}: English
 * lives in {@code TranslationKey.defaultValue}, the other nine become {@code Translation} rows.
 * Insert-if-absent, so re-running is a no-op — but note the corollary: correcting a string here
 * does NOT rewrite a row already committed to a database. A text correction needs a code-scoped
 * UPDATE in both migration directories.
 */
@Component
@Order(18)
@RequiredArgsConstructor
@Slf4j
public class AaHearingOrderTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "aa-hearing-order";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

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
        log.info("AA hearing/order vocabulary seeded: {} keys in module {}", english().size(), MODULE);
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        // ═══ Citizen / appellant notices ═══
        m.put("aa.notify.appeal_accepted", "Your appeal {appealNumber} has been accepted for consideration.");
        m.put("aa.notify.appeal_rejected", "Your appeal {appealNumber} has not been accepted for consideration.");
        m.put("aa.notify.hearing_scheduled",
              "A hearing on your appeal {appealNumber} has been scheduled for {hearingDate} at {hearingVenue}.");
        m.put("aa.notify.hearing_rescheduled",
              "The hearing on your appeal {appealNumber} has been moved to {hearingDate} at {hearingVenue}.");
        m.put("aa.notify.hearing_adjourned",
              "The hearing on your appeal {appealNumber} has been adjourned. A new date will be informed to you.");
        m.put("aa.notify.order_passed", "An order was issued on your appeal {appealNumber} on {orderDate}.");
        m.put("aa.notify.order_corrected", "A correction was made to the order on your appeal {appealNumber}.");
        m.put("aa.notify.appeal_dismissed", "Your appeal {appealNumber} has been closed without any relief.");
        m.put("aa.notify.appeal_remanded", "Your appeal {appealNumber} has been sent back for fresh consideration.");
        m.put("aa.notify.hearing_reminder",
              "Reminder: the hearing on your appeal {appealNumber} is on {hearingDate} at {hearingVenue}.");

        // ═══ Officer / staff bell notifications ═══
        m.put("aa.notify.officer.appeal_accepted", "Appeal {appealNumber} was accepted for consideration");
        m.put("aa.notify.officer.appeal_rejected", "Appeal {appealNumber} was not accepted for consideration");
        m.put("aa.notify.officer.assigned_to_bench", "Appeal {appealNumber} was assigned to a bench");
        m.put("aa.notify.officer.hearing_scheduled",
              "A hearing was scheduled for appeal {appealNumber} on {hearingDate}");
        m.put("aa.notify.officer.hearing_rescheduled",
              "The hearing for appeal {appealNumber} was moved to {hearingDate}");
        m.put("aa.notify.officer.hearing_adjourned", "The hearing for appeal {appealNumber} was adjourned");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "The hearing outcome for appeal {appealNumber} was recorded as {outcome}");
        m.put("aa.notify.officer.order_passed", "An order was issued on appeal {appealNumber} on {orderDate}");
        m.put("aa.notify.officer.order_corrected", "The order on appeal {appealNumber} was corrected");
        m.put("aa.notify.officer.appeal_dismissed", "Appeal {appealNumber} was closed without any relief");
        m.put("aa.notify.officer.appeal_remanded", "Appeal {appealNumber} was sent back for fresh consideration");
        m.put("aa.notify.officer.forwarded_to_authority",
              "Appeal {appealNumber} was forwarded to the appellate authority");
        m.put("aa.notify.officer.sent_back_registrar", "Appeal {appealNumber} was sent back to the registrar");
        m.put("aa.notify.officer.reassigned", "Appeal {appealNumber} was reassigned to you");
        m.put("aa.notify.officer.escalated_to_tier2", "Appeal {appealNumber} was escalated to the second tier");
        m.put("aa.notify.officer.hearing_reminder",
              "Reminder: a hearing for appeal {appealNumber} is on {hearingDate}");
        m.put("aa.notify.officer.sla_reminder", "Appeal {appealNumber} is approaching its due date");

        // ═══ Hearing and order API responses ═══
        m.put("aa.hearing.scheduled_notices_recorded", "Hearing scheduled. Notices recorded for dispatch.");
        m.put("aa.hearing.rescheduled_notices_recorded", "Hearing rescheduled. Notices recorded for dispatch.");
        m.put("aa.hearing.adjourned", "Hearing adjourned");
        m.put("aa.hearing.outcome_recorded", "Hearing outcome recorded");
        m.put("aa.hearing.error_officer_double_booked",
              "The selected officer already has a hearing at this time");
        m.put("aa.hearing.error_invalid_request", "The hearing details are incomplete or invalid");
        m.put("aa.hearing.error_invalid_state",
              "This appeal is not in a state where this hearing action is allowed");
        m.put("aa.hearing.error_no_scheduled_hearing", "There is no scheduled hearing for this appeal");
        m.put("aa.order.issued", "Order issued");
        m.put("aa.order.corrected", "Order correction recorded");
        m.put("aa.order.error_already_issued", "An order has already been issued on this appeal");
        m.put("aa.order.error_ed_approval_required",
              "Approval from the Executive Director is required before this order can be issued");
        m.put("aa.order.error_invalid_request", "The order details are incomplete or invalid");
        // Both refusals are ENGLISH-ONLY on purpose. They state a legal ground for refusing to issue an
        // order, so their nine translations need the same sign-off as the clause labels; until then the
        // TranslationService falls back to the English default, which is correct rather than a raw key.
        m.put("aa.order.error_award_cap_exceeded",
              "The award exceeds the maximum compensation permitted by the Scheme, so this order "
                      + "cannot be issued");
        m.put("aa.order.error_sub_judice",
              "This appeal is recorded as having a related court trial. State the ground on which the "
                      + "Appellate Authority proceeds, or do not issue an order while the matter is "
                      + "sub-judice");
        m.put("aa.order.error_no_order_to_correct", "There is no issued order on this appeal to correct");

        // ═══ Hearing history, notice log and order labels ═══
        m.put("aa.hearing.history_title", "Hearing History");
        m.put("aa.hearing.event_scheduled", "Scheduled");
        m.put("aa.hearing.event_rescheduled", "Rescheduled");
        m.put("aa.hearing.event_adjourned", "Adjourned");
        m.put("aa.hearing.event_completed", "Completed");
        m.put("aa.hearing.event_cancelled", "Cancelled");
        m.put("aa.hearing.col_date", "Date");
        m.put("aa.hearing.col_venue", "Venue");
        m.put("aa.hearing.col_mode", "Mode");
        m.put("aa.hearing.col_outcome", "Outcome");
        m.put("aa.hearing.col_reason", "Reason");
        m.put("aa.hearing.mode_in_person", "In Person");
        m.put("aa.hearing.mode_video", "Video Conference");
        m.put("aa.hearing.mode_hybrid", "Hybrid");
        m.put("aa.notice.status_pending", "Pending");
        m.put("aa.notice.status_sent", "Sent");
        m.put("aa.notice.status_failed", "Failed");
        m.put("aa.notice.status_cancelled", "Cancelled");
        m.put("aa.notice.channel_email", "Email");
        m.put("aa.notice.channel_sms", "SMS");
        m.put("aa.notice.channel_none", "No channel");
        m.put("aa.notice.recipient_appellant", "Appellant");
        m.put("aa.notice.recipient_respondent", "Respondent");
        m.put("aa.notice.recipient_ombudsman", "Ombudsman");
        m.put("aa.notice.queued_not_sent", "Notice recorded — awaiting dispatch");
        m.put("aa.order.revision_label", "Revision");
        m.put("aa.order.correction_reason", "Correction Reason");
        m.put("aa.order.issuing_authority", "Issuing Authority");
        m.put("aa.order.clause_relied_on", "Clause relied on: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "A downloadable PDF of the order is not available in this release");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "आपकी अपील {appealNumber} विचार हेतु स्वीकार कर ली गई है।");
        m.put("aa.notify.appeal_rejected", "आपकी अपील {appealNumber} विचार हेतु स्वीकार नहीं की गई है।");
        m.put("aa.notify.hearing_scheduled",
              "आपकी अपील {appealNumber} पर सुनवाई {hearingDate} को {hearingVenue} में निर्धारित की गई है।");
        m.put("aa.notify.hearing_rescheduled",
              "आपकी अपील {appealNumber} पर सुनवाई {hearingDate} को {hearingVenue} में स्थानांतरित कर दी गई है।");
        m.put("aa.notify.hearing_adjourned",
              "आपकी अपील {appealNumber} पर सुनवाई स्थगित कर दी गई है। नई तिथि की सूचना आपको दी जाएगी।");
        m.put("aa.notify.order_passed", "आपकी अपील {appealNumber} पर {orderDate} को आदेश जारी किया गया।");
        m.put("aa.notify.order_corrected", "आपकी अपील {appealNumber} के आदेश में एक सुधार किया गया।");
        m.put("aa.notify.appeal_dismissed", "आपकी अपील {appealNumber} किसी राहत के बिना बंद कर दी गई है।");
        m.put("aa.notify.appeal_remanded", "आपकी अपील {appealNumber} पुनर्विचार हेतु वापस भेज दी गई है।");
        m.put("aa.notify.hearing_reminder",
              "स्मरण: आपकी अपील {appealNumber} पर सुनवाई {hearingDate} को {hearingVenue} में है।");
        m.put("aa.notify.officer.appeal_accepted", "अपील {appealNumber} विचार हेतु स्वीकार की गई");
        m.put("aa.notify.officer.appeal_rejected", "अपील {appealNumber} विचार हेतु स्वीकार नहीं की गई");
        m.put("aa.notify.officer.assigned_to_bench", "अपील {appealNumber} एक पीठ को सौंपी गई");
        m.put("aa.notify.officer.hearing_scheduled",
              "अपील {appealNumber} के लिए {hearingDate} को सुनवाई निर्धारित की गई");
        m.put("aa.notify.officer.hearing_rescheduled",
              "अपील {appealNumber} की सुनवाई {hearingDate} को स्थानांतरित की गई");
        m.put("aa.notify.officer.hearing_adjourned", "अपील {appealNumber} की सुनवाई स्थगित कर दी गई");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "अपील {appealNumber} की सुनवाई का परिणाम {outcome} के रूप में दर्ज किया गया");
        m.put("aa.notify.officer.order_passed", "अपील {appealNumber} पर {orderDate} को आदेश जारी किया गया");
        m.put("aa.notify.officer.order_corrected", "अपील {appealNumber} के आदेश में सुधार किया गया");
        m.put("aa.notify.officer.appeal_dismissed", "अपील {appealNumber} किसी राहत के बिना बंद कर दी गई");
        m.put("aa.notify.officer.appeal_remanded", "अपील {appealNumber} पुनर्विचार हेतु वापस भेजी गई");
        m.put("aa.notify.officer.forwarded_to_authority", "अपील {appealNumber} अपीलीय प्राधिकारी को अग्रेषित की गई");
        m.put("aa.notify.officer.sent_back_registrar", "अपील {appealNumber} रजिस्ट्रार को वापस भेजी गई");
        m.put("aa.notify.officer.reassigned", "अपील {appealNumber} आपको पुनः सौंपी गई");
        m.put("aa.notify.officer.escalated_to_tier2", "अपील {appealNumber} द्वितीय स्तर पर भेजी गई");
        m.put("aa.notify.officer.hearing_reminder",
              "स्मरण: अपील {appealNumber} के लिए सुनवाई {hearingDate} को है");
        m.put("aa.notify.officer.sla_reminder", "अपील {appealNumber} की नियत तिथि निकट आ रही है");
        m.put("aa.hearing.scheduled_notices_recorded", "सुनवाई निर्धारित की गई। सूचनाएँ प्रेषण हेतु दर्ज कर ली गई हैं।");
        m.put("aa.hearing.rescheduled_notices_recorded", "सुनवाई पुनर्निर्धारित की गई। सूचनाएँ प्रेषण हेतु दर्ज कर ली गई हैं।");
        m.put("aa.hearing.adjourned", "सुनवाई स्थगित की गई");
        m.put("aa.hearing.outcome_recorded", "सुनवाई का परिणाम दर्ज किया गया");
        m.put("aa.hearing.error_officer_double_booked",
              "चयनित अधिकारी की इस समय पहले से ही एक सुनवाई निर्धारित है");
        m.put("aa.hearing.error_invalid_request", "सुनवाई का विवरण अपूर्ण अथवा अमान्य है");
        m.put("aa.hearing.error_invalid_state",
              "यह अपील ऐसी स्थिति में नहीं है जिसमें सुनवाई की यह कार्रवाई अनुमत हो");
        m.put("aa.hearing.error_no_scheduled_hearing", "इस अपील के लिए कोई सुनवाई निर्धारित नहीं है");
        m.put("aa.order.issued", "आदेश जारी किया गया");
        m.put("aa.order.corrected", "आदेश का सुधार दर्ज किया गया");
        m.put("aa.order.error_already_issued", "इस अपील पर पहले ही आदेश जारी किया जा चुका है");
        m.put("aa.order.error_ed_approval_required",
              "यह आदेश जारी करने से पूर्व कार्यपालक निदेशक का अनुमोदन आवश्यक है");
        m.put("aa.order.error_invalid_request", "आदेश का विवरण अपूर्ण अथवा अमान्य है");
        m.put("aa.order.error_no_order_to_correct", "इस अपील पर सुधार के लिए कोई जारी आदेश नहीं है");
        m.put("aa.hearing.history_title", "सुनवाई का विवरण");
        m.put("aa.hearing.event_scheduled", "निर्धारित");
        m.put("aa.hearing.event_rescheduled", "पुनर्निर्धारित");
        m.put("aa.hearing.event_adjourned", "स्थगित");
        m.put("aa.hearing.event_completed", "पूर्ण");
        m.put("aa.hearing.event_cancelled", "रद्द");
        m.put("aa.hearing.col_date", "तिथि");
        m.put("aa.hearing.col_venue", "स्थान");
        m.put("aa.hearing.col_mode", "माध्यम");
        m.put("aa.hearing.col_outcome", "परिणाम");
        m.put("aa.hearing.col_reason", "कारण");
        m.put("aa.hearing.mode_in_person", "प्रत्यक्ष उपस्थिति");
        m.put("aa.hearing.mode_video", "वीडियो द्वारा");
        m.put("aa.hearing.mode_hybrid", "मिश्रित");
        m.put("aa.notice.status_pending", "लंबित");
        m.put("aa.notice.status_sent", "भेजी गई");
        m.put("aa.notice.status_failed", "विफल");
        m.put("aa.notice.status_cancelled", "रद्द");
        m.put("aa.notice.channel_email", "ई-मेल");
        m.put("aa.notice.channel_sms", "एसएमएस");
        m.put("aa.notice.channel_none", "कोई माध्यम नहीं");
        m.put("aa.notice.recipient_appellant", "अपीलकर्ता");
        m.put("aa.notice.recipient_respondent", "प्रत्यर्थी");
        m.put("aa.notice.recipient_ombudsman", "लोकपाल");
        m.put("aa.notice.queued_not_sent", "सूचना दर्ज — प्रेषण की प्रतीक्षा में");
        m.put("aa.order.revision_label", "संशोधन");
        m.put("aa.order.correction_reason", "सुधार का कारण");
        m.put("aa.order.issuing_authority", "जारीकर्ता प्राधिकारी");
        m.put("aa.order.clause_relied_on", "आधारित खंड: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "इस संस्करण में आदेश की डाउनलोड योग्य पीडीएफ उपलब्ध नहीं है");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "आपली अपील {appealNumber} विचारार्थ स्वीकारण्यात आली आहे.");
        m.put("aa.notify.appeal_rejected", "आपली अपील {appealNumber} विचारार्थ स्वीकारण्यात आलेली नाही.");
        m.put("aa.notify.hearing_scheduled",
              "आपल्या अपील {appealNumber} वरील सुनावणी {hearingDate} रोजी {hearingVenue} येथे निश्चित करण्यात आली आहे.");
        m.put("aa.notify.hearing_rescheduled",
              "आपल्या अपील {appealNumber} वरील सुनावणी {hearingDate} रोजी {hearingVenue} येथे हलविण्यात आली आहे.");
        m.put("aa.notify.hearing_adjourned",
              "आपल्या अपील {appealNumber} वरील सुनावणी तहकूब करण्यात आली आहे. नवीन तारीख आपल्याला कळविण्यात येईल.");
        m.put("aa.notify.order_passed", "आपल्या अपील {appealNumber} वर {orderDate} रोजी आदेश जारी करण्यात आला.");
        m.put("aa.notify.order_corrected", "आपल्या अपील {appealNumber} वरील आदेशात एक दुरुस्ती करण्यात आली.");
        m.put("aa.notify.appeal_dismissed", "आपली अपील {appealNumber} कोणत्याही दिलाशाशिवाय बंद करण्यात आली आहे.");
        m.put("aa.notify.appeal_remanded", "आपली अपील {appealNumber} फेरविचारासाठी परत पाठविण्यात आली आहे.");
        m.put("aa.notify.hearing_reminder",
              "स्मरण: आपल्या अपील {appealNumber} वरील सुनावणी {hearingDate} रोजी {hearingVenue} येथे आहे.");
        m.put("aa.notify.officer.appeal_accepted", "अपील {appealNumber} विचारार्थ स्वीकारण्यात आली");
        m.put("aa.notify.officer.appeal_rejected", "अपील {appealNumber} विचारार्थ स्वीकारण्यात आलेली नाही");
        m.put("aa.notify.officer.assigned_to_bench", "अपील {appealNumber} एका पीठाकडे सोपविण्यात आली");
        m.put("aa.notify.officer.hearing_scheduled",
              "अपील {appealNumber} साठी {hearingDate} रोजी सुनावणी निश्चित करण्यात आली");
        m.put("aa.notify.officer.hearing_rescheduled",
              "अपील {appealNumber} ची सुनावणी {hearingDate} रोजी हलविण्यात आली");
        m.put("aa.notify.officer.hearing_adjourned", "अपील {appealNumber} ची सुनावणी तहकूब करण्यात आली");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "अपील {appealNumber} च्या सुनावणीचा निष्कर्ष {outcome} असा नोंदविण्यात आला");
        m.put("aa.notify.officer.order_passed", "अपील {appealNumber} वर {orderDate} रोजी आदेश जारी करण्यात आला");
        m.put("aa.notify.officer.order_corrected", "अपील {appealNumber} वरील आदेशात दुरुस्ती करण्यात आली");
        m.put("aa.notify.officer.appeal_dismissed", "अपील {appealNumber} कोणत्याही दिलाशाशिवाय बंद करण्यात आली");
        m.put("aa.notify.officer.appeal_remanded", "अपील {appealNumber} फेरविचारासाठी परत पाठविण्यात आली");
        m.put("aa.notify.officer.forwarded_to_authority", "अपील {appealNumber} अपिलीय प्राधिकरणाकडे पाठविण्यात आली");
        m.put("aa.notify.officer.sent_back_registrar", "अपील {appealNumber} निबंधकाकडे परत पाठविण्यात आली");
        m.put("aa.notify.officer.reassigned", "अपील {appealNumber} आपल्याकडे पुन्हा सोपविण्यात आली");
        m.put("aa.notify.officer.escalated_to_tier2", "अपील {appealNumber} दुसऱ्या स्तरावर पाठविण्यात आली");
        m.put("aa.notify.officer.hearing_reminder",
              "स्मरण: अपील {appealNumber} साठी सुनावणी {hearingDate} रोजी आहे");
        m.put("aa.notify.officer.sla_reminder", "अपील {appealNumber} ची नियत मुदत जवळ येत आहे");
        m.put("aa.hearing.scheduled_notices_recorded", "सुनावणी निश्चित केली. सूचना पाठविण्यासाठी नोंदविण्यात आल्या आहेत.");
        m.put("aa.hearing.rescheduled_notices_recorded", "सुनावणी पुन्हा निश्चित केली. सूचना पाठविण्यासाठी नोंदविण्यात आल्या आहेत.");
        m.put("aa.hearing.adjourned", "सुनावणी तहकूब केली");
        m.put("aa.hearing.outcome_recorded", "सुनावणीचा निष्कर्ष नोंदविला");
        m.put("aa.hearing.error_officer_double_booked",
              "निवडलेल्या अधिकाऱ्याची या वेळी आधीच एक सुनावणी निश्चित आहे");
        m.put("aa.hearing.error_invalid_request", "सुनावणीचा तपशील अपूर्ण किंवा अवैध आहे");
        m.put("aa.hearing.error_invalid_state",
              "ही अपील अशा स्थितीत नाही ज्यात सुनावणीची ही कार्यवाही अनुमत असेल");
        m.put("aa.hearing.error_no_scheduled_hearing", "या अपिलासाठी कोणतीही सुनावणी निश्चित केलेली नाही");
        m.put("aa.order.issued", "आदेश जारी केला");
        m.put("aa.order.corrected", "आदेशातील दुरुस्ती नोंदविली");
        m.put("aa.order.error_already_issued", "या अपिलावर आधीच आदेश जारी करण्यात आला आहे");
        m.put("aa.order.error_ed_approval_required",
              "हा आदेश जारी करण्यापूर्वी कार्यकारी संचालकांची मंजुरी आवश्यक आहे");
        m.put("aa.order.error_invalid_request", "आदेशाचा तपशील अपूर्ण किंवा अवैध आहे");
        m.put("aa.order.error_no_order_to_correct", "या अपिलावर दुरुस्तीसाठी कोणताही जारी केलेला आदेश नाही");
        m.put("aa.hearing.history_title", "सुनावणीचा तपशील");
        m.put("aa.hearing.event_scheduled", "निश्चित");
        m.put("aa.hearing.event_rescheduled", "पुन्हा निश्चित");
        m.put("aa.hearing.event_adjourned", "तहकूब");
        m.put("aa.hearing.event_completed", "पूर्ण");
        m.put("aa.hearing.event_cancelled", "रद्द");
        m.put("aa.hearing.col_date", "तारीख");
        m.put("aa.hearing.col_venue", "ठिकाण");
        m.put("aa.hearing.col_mode", "पद्धत");
        m.put("aa.hearing.col_outcome", "निष्कर्ष");
        m.put("aa.hearing.col_reason", "कारण");
        m.put("aa.hearing.mode_in_person", "प्रत्यक्ष उपस्थिती");
        m.put("aa.hearing.mode_video", "व्हिडिओद्वारे");
        m.put("aa.hearing.mode_hybrid", "संमिश्र");
        m.put("aa.notice.status_pending", "प्रलंबित");
        m.put("aa.notice.status_sent", "पाठविली");
        m.put("aa.notice.status_failed", "अयशस्वी");
        m.put("aa.notice.status_cancelled", "रद्द");
        m.put("aa.notice.channel_email", "ई-मेल");
        m.put("aa.notice.channel_sms", "एसएमएस");
        m.put("aa.notice.channel_none", "कोणतेही माध्यम नाही");
        m.put("aa.notice.recipient_appellant", "अपीलकर्ता");
        m.put("aa.notice.recipient_respondent", "प्रतिवादी");
        m.put("aa.notice.recipient_ombudsman", "लोकपाल");
        m.put("aa.notice.queued_not_sent", "सूचना नोंदविली — पाठविण्याच्या प्रतीक्षेत");
        m.put("aa.order.revision_label", "सुधारित आवृत्ती");
        m.put("aa.order.correction_reason", "दुरुस्तीचे कारण");
        m.put("aa.order.issuing_authority", "आदेश जारी करणारे प्राधिकरण");
        m.put("aa.order.clause_relied_on", "आधार घेतलेले कलम: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "या आवृत्तीत आदेशाची डाउनलोड करण्यायोग्य पीडीएफ उपलब्ध नाही");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "আপনার আপিল {appealNumber} বিবেচনার জন্য গ্রহণ করা হয়েছে।");
        m.put("aa.notify.appeal_rejected", "আপনার আপিল {appealNumber} বিবেচনার জন্য গ্রহণ করা হয়নি।");
        m.put("aa.notify.hearing_scheduled",
              "আপনার আপিল {appealNumber} সংক্রান্ত শুনানি {hearingDate} তারিখে {hearingVenue}-তে নির্ধারিত হয়েছে।");
        m.put("aa.notify.hearing_rescheduled",
              "আপনার আপিল {appealNumber} সংক্রান্ত শুনানি {hearingDate} তারিখে {hearingVenue}-তে সরানো হয়েছে।");
        m.put("aa.notify.hearing_adjourned",
              "আপনার আপিল {appealNumber} সংক্রান্ত শুনানি মুলতুবি রাখা হয়েছে। নতুন তারিখ আপনাকে জানানো হবে।");
        m.put("aa.notify.order_passed", "আপনার আপিল {appealNumber} সংক্রান্ত আদেশ {orderDate} তারিখে জারি করা হয়েছে।");
        m.put("aa.notify.order_corrected", "আপনার আপিল {appealNumber} সংক্রান্ত আদেশে একটি সংশোধন করা হয়েছে।");
        m.put("aa.notify.appeal_dismissed", "আপনার আপিল {appealNumber} কোনো প্রতিকার ছাড়াই বন্ধ করা হয়েছে।");
        m.put("aa.notify.appeal_remanded", "আপনার আপিল {appealNumber} নতুন করে বিবেচনার জন্য ফেরত পাঠানো হয়েছে।");
        m.put("aa.notify.hearing_reminder",
              "স্মারক: আপনার আপিল {appealNumber} সংক্রান্ত শুনানি {hearingDate} তারিখে {hearingVenue}-তে রয়েছে।");
        m.put("aa.notify.officer.appeal_accepted", "আপিল {appealNumber} বিবেচনার জন্য গ্রহণ করা হয়েছে");
        m.put("aa.notify.officer.appeal_rejected", "আপিল {appealNumber} বিবেচনার জন্য গ্রহণ করা হয়নি");
        m.put("aa.notify.officer.assigned_to_bench", "আপিল {appealNumber} একটি বেঞ্চের কাছে বরাদ্দ করা হয়েছে");
        m.put("aa.notify.officer.hearing_scheduled",
              "আপিল {appealNumber}-এর জন্য {hearingDate} তারিখে শুনানি নির্ধারিত হয়েছে");
        m.put("aa.notify.officer.hearing_rescheduled",
              "আপিল {appealNumber}-এর শুনানি {hearingDate} তারিখে সরানো হয়েছে");
        m.put("aa.notify.officer.hearing_adjourned", "আপিল {appealNumber}-এর শুনানি মুলতুবি রাখা হয়েছে");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "আপিল {appealNumber}-এর শুনানির ফলাফল {outcome} হিসেবে নথিবদ্ধ হয়েছে");
        m.put("aa.notify.officer.order_passed", "আপিল {appealNumber} সংক্রান্ত আদেশ {orderDate} তারিখে জারি হয়েছে");
        m.put("aa.notify.officer.order_corrected", "আপিল {appealNumber} সংক্রান্ত আদেশ সংশোধন করা হয়েছে");
        m.put("aa.notify.officer.appeal_dismissed", "আপিল {appealNumber} কোনো প্রতিকার ছাড়াই বন্ধ করা হয়েছে");
        m.put("aa.notify.officer.appeal_remanded", "আপিল {appealNumber} নতুন করে বিবেচনার জন্য ফেরত পাঠানো হয়েছে");
        m.put("aa.notify.officer.forwarded_to_authority", "আপিল {appealNumber} আপিল কর্তৃপক্ষের কাছে পাঠানো হয়েছে");
        m.put("aa.notify.officer.sent_back_registrar", "আপিল {appealNumber} রেজিস্ট্রারের কাছে ফেরত পাঠানো হয়েছে");
        m.put("aa.notify.officer.reassigned", "আপিল {appealNumber} পুনরায় আপনাকে বরাদ্দ করা হয়েছে");
        m.put("aa.notify.officer.escalated_to_tier2", "আপিল {appealNumber} দ্বিতীয় স্তরে পাঠানো হয়েছে");
        m.put("aa.notify.officer.hearing_reminder",
              "স্মারক: আপিল {appealNumber}-এর শুনানি {hearingDate} তারিখে রয়েছে");
        m.put("aa.notify.officer.sla_reminder", "আপিল {appealNumber}-এর নির্ধারিত তারিখ ঘনিয়ে আসছে");
        m.put("aa.hearing.scheduled_notices_recorded", "শুনানি নির্ধারিত হয়েছে। নোটিশগুলি পাঠানোর জন্য নথিবদ্ধ হয়েছে।");
        m.put("aa.hearing.rescheduled_notices_recorded", "শুনানি পুনরায় নির্ধারিত হয়েছে। নোটিশগুলি পাঠানোর জন্য নথিবদ্ধ হয়েছে।");
        m.put("aa.hearing.adjourned", "শুনানি মুলতুবি রাখা হয়েছে");
        m.put("aa.hearing.outcome_recorded", "শুনানির ফলাফল নথিবদ্ধ হয়েছে");
        m.put("aa.hearing.error_officer_double_booked",
              "নির্বাচিত আধিকারিকের এই সময়ে ইতিমধ্যেই একটি শুনানি নির্ধারিত রয়েছে");
        m.put("aa.hearing.error_invalid_request", "শুনানির বিবরণ অসম্পূর্ণ অথবা অবৈধ");
        m.put("aa.hearing.error_invalid_state",
              "এই আপিলটি এমন অবস্থায় নেই যেখানে শুনানির এই পদক্ষেপ অনুমোদিত");
        m.put("aa.hearing.error_no_scheduled_hearing", "এই আপিলের জন্য কোনো শুনানি নির্ধারিত নেই");
        m.put("aa.order.issued", "আদেশ জারি করা হয়েছে");
        m.put("aa.order.corrected", "আদেশের সংশোধন নথিবদ্ধ হয়েছে");
        m.put("aa.order.error_already_issued", "এই আপিলে ইতিমধ্যেই একটি আদেশ জারি করা হয়েছে");
        m.put("aa.order.error_ed_approval_required",
              "এই আদেশ জারি করার আগে কার্যনির্বাহী পরিচালকের অনুমোদন আবশ্যক");
        m.put("aa.order.error_invalid_request", "আদেশের বিবরণ অসম্পূর্ণ অথবা অবৈধ");
        m.put("aa.order.error_no_order_to_correct", "এই আপিলে সংশোধনের জন্য কোনো জারি করা আদেশ নেই");
        m.put("aa.hearing.history_title", "শুনানির নথি");
        m.put("aa.hearing.event_scheduled", "নির্ধারিত");
        m.put("aa.hearing.event_rescheduled", "পুনরায় নির্ধারিত");
        m.put("aa.hearing.event_adjourned", "মুলতুবি");
        m.put("aa.hearing.event_completed", "সম্পন্ন");
        m.put("aa.hearing.event_cancelled", "বাতিল");
        m.put("aa.hearing.col_date", "তারিখ");
        m.put("aa.hearing.col_venue", "স্থান");
        m.put("aa.hearing.col_mode", "পদ্ধতি");
        m.put("aa.hearing.col_outcome", "ফলাফল");
        m.put("aa.hearing.col_reason", "কারণ");
        m.put("aa.hearing.mode_in_person", "সশরীরে উপস্থিতি");
        m.put("aa.hearing.mode_video", "ভিডিও মাধ্যমে");
        m.put("aa.hearing.mode_hybrid", "মিশ্র");
        m.put("aa.notice.status_pending", "অপেক্ষমাণ");
        m.put("aa.notice.status_sent", "পাঠানো হয়েছে");
        m.put("aa.notice.status_failed", "ব্যর্থ");
        m.put("aa.notice.status_cancelled", "বাতিল");
        m.put("aa.notice.channel_email", "ই-মেল");
        m.put("aa.notice.channel_sms", "এসএমএস");
        m.put("aa.notice.channel_none", "কোনো মাধ্যম নেই");
        m.put("aa.notice.recipient_appellant", "আপিলকারী");
        m.put("aa.notice.recipient_respondent", "প্রতিবাদী");
        m.put("aa.notice.recipient_ombudsman", "ন্যায়পাল");
        m.put("aa.notice.queued_not_sent", "নোটিশ নথিবদ্ধ — পাঠানোর অপেক্ষায়");
        m.put("aa.order.revision_label", "সংশোধিত সংস্করণ");
        m.put("aa.order.correction_reason", "সংশোধনের কারণ");
        m.put("aa.order.issuing_authority", "আদেশ জারিকারী কর্তৃপক্ষ");
        m.put("aa.order.clause_relied_on", "নির্ভর করা ধারা: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "এই সংস্করণে আদেশের ডাউনলোডযোগ্য পিডিএফ উপলব্ধ নয়");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "మీ అప్పీలు {appealNumber} పరిశీలన కోసం స్వీకరించబడింది.");
        m.put("aa.notify.appeal_rejected", "మీ అప్పీలు {appealNumber} పరిశీలన కోసం స్వీకరించబడలేదు.");
        m.put("aa.notify.hearing_scheduled",
              "మీ అప్పీలు {appealNumber} పై విచారణ {hearingDate} నాడు {hearingVenue} వద్ద నిర్ణయించబడింది.");
        m.put("aa.notify.hearing_rescheduled",
              "మీ అప్పీలు {appealNumber} పై విచారణ {hearingDate} నాడు {hearingVenue} వద్దకు మార్చబడింది.");
        m.put("aa.notify.hearing_adjourned",
              "మీ అప్పీలు {appealNumber} పై విచారణ వాయిదా వేయబడింది. కొత్త తేదీ మీకు తెలియజేయబడుతుంది.");
        m.put("aa.notify.order_passed", "మీ అప్పీలు {appealNumber} పై {orderDate} నాడు ఉత్తర్వు జారీ చేయబడింది.");
        m.put("aa.notify.order_corrected", "మీ అప్పీలు {appealNumber} పై ఉత్తర్వులో ఒక సవరణ చేయబడింది.");
        m.put("aa.notify.appeal_dismissed", "మీ అప్పీలు {appealNumber} ఎటువంటి ఉపశమనం లేకుండా ముగించబడింది.");
        m.put("aa.notify.appeal_remanded", "మీ అప్పీలు {appealNumber} తిరిగి పరిశీలన కోసం వెనక్కి పంపబడింది.");
        m.put("aa.notify.hearing_reminder",
              "గుర్తుంపు: మీ అప్పీలు {appealNumber} పై విచారణ {hearingDate} నాడు {hearingVenue} వద్ద ఉంది.");
        m.put("aa.notify.officer.appeal_accepted", "అప్పీలు {appealNumber} పరిశీలన కోసం స్వీకరించబడింది");
        m.put("aa.notify.officer.appeal_rejected", "అప్పీలు {appealNumber} పరిశీలన కోసం స్వీకరించబడలేదు");
        m.put("aa.notify.officer.assigned_to_bench", "అప్పీలు {appealNumber} ఒక బెంచ్‌కు కేటాయించబడింది");
        m.put("aa.notify.officer.hearing_scheduled",
              "అప్పీలు {appealNumber} కోసం {hearingDate} నాడు విచారణ నిర్ణయించబడింది");
        m.put("aa.notify.officer.hearing_rescheduled",
              "అప్పీలు {appealNumber} విచారణ {hearingDate} నాటికి మార్చబడింది");
        m.put("aa.notify.officer.hearing_adjourned", "అప్పీలు {appealNumber} విచారణ వాయిదా వేయబడింది");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "అప్పీలు {appealNumber} విచారణ ఫలితం {outcome} గా నమోదు చేయబడింది");
        m.put("aa.notify.officer.order_passed", "అప్పీలు {appealNumber} పై {orderDate} నాడు ఉత్తర్వు జారీ చేయబడింది");
        m.put("aa.notify.officer.order_corrected", "అప్పీలు {appealNumber} పై ఉత్తర్వు సవరించబడింది");
        m.put("aa.notify.officer.appeal_dismissed", "అప్పీలు {appealNumber} ఎటువంటి ఉపశమనం లేకుండా ముగించబడింది");
        m.put("aa.notify.officer.appeal_remanded", "అప్పీలు {appealNumber} తిరిగి పరిశీలన కోసం వెనక్కి పంపబడింది");
        m.put("aa.notify.officer.forwarded_to_authority", "అప్పీలు {appealNumber} అప్పీలు అధికారికి పంపబడింది");
        m.put("aa.notify.officer.sent_back_registrar", "అప్పీలు {appealNumber} రిజిస్ట్రార్‌కు వెనక్కి పంపబడింది");
        m.put("aa.notify.officer.reassigned", "అప్పీలు {appealNumber} మీకు తిరిగి కేటాయించబడింది");
        m.put("aa.notify.officer.escalated_to_tier2", "అప్పీలు {appealNumber} రెండవ స్థాయికి పంపబడింది");
        m.put("aa.notify.officer.hearing_reminder",
              "గుర్తుంపు: అప్పీలు {appealNumber} కోసం విచారణ {hearingDate} నాడు ఉంది");
        m.put("aa.notify.officer.sla_reminder", "అప్పీలు {appealNumber} గడువు తేదీ దగ్గర పడుతోంది");
        m.put("aa.hearing.scheduled_notices_recorded", "విచారణ నిర్ణయించబడింది. నోటీసులు పంపడానికి నమోదు చేయబడ్డాయి.");
        m.put("aa.hearing.rescheduled_notices_recorded", "విచారణ తిరిగి నిర్ణయించబడింది. నోటీసులు పంపడానికి నమోదు చేయబడ్డాయి.");
        m.put("aa.hearing.adjourned", "విచారణ వాయిదా వేయబడింది");
        m.put("aa.hearing.outcome_recorded", "విచారణ ఫలితం నమోదు చేయబడింది");
        m.put("aa.hearing.error_officer_double_booked",
              "ఎంచుకున్న అధికారికి ఈ సమయంలో ఇప్పటికే ఒక విచారణ నిర్ణయించబడి ఉంది");
        m.put("aa.hearing.error_invalid_request", "విచారణ వివరాలు అసంపూర్ణంగా లేదా చెల్లనివిగా ఉన్నాయి");
        m.put("aa.hearing.error_invalid_state",
              "ఈ అప్పీలు ఈ విచారణ చర్యకు అనుమతించే స్థితిలో లేదు");
        m.put("aa.hearing.error_no_scheduled_hearing", "ఈ అప్పీలు కోసం ఏ విచారణ నిర్ణయించబడలేదు");
        m.put("aa.order.issued", "ఉత్తర్వు జారీ చేయబడింది");
        m.put("aa.order.corrected", "ఉత్తర్వు సవరణ నమోదు చేయబడింది");
        m.put("aa.order.error_already_issued", "ఈ అప్పీలుపై ఇప్పటికే ఒక ఉత్తర్వు జారీ చేయబడింది");
        m.put("aa.order.error_ed_approval_required",
              "ఈ ఉత్తర్వు జారీ చేయడానికి ముందు కార్యనిర్వాహక సంచాలకుని ఆమోదం అవసరం");
        m.put("aa.order.error_invalid_request", "ఉత్తర్వు వివరాలు అసంపూర్ణంగా లేదా చెల్లనివిగా ఉన్నాయి");
        m.put("aa.order.error_no_order_to_correct", "ఈ అప్పీలుపై సవరించడానికి జారీ చేసిన ఉత్తర్వు ఏదీ లేదు");
        m.put("aa.hearing.history_title", "విచారణ చరిత్ర");
        m.put("aa.hearing.event_scheduled", "నిర్ణయించబడింది");
        m.put("aa.hearing.event_rescheduled", "తిరిగి నిర్ణయించబడింది");
        m.put("aa.hearing.event_adjourned", "వాయిదా వేయబడింది");
        m.put("aa.hearing.event_completed", "పూర్తయింది");
        m.put("aa.hearing.event_cancelled", "రద్దు చేయబడింది");
        m.put("aa.hearing.col_date", "తేదీ");
        m.put("aa.hearing.col_venue", "స్థలం");
        m.put("aa.hearing.col_mode", "విధానం");
        m.put("aa.hearing.col_outcome", "ఫలితం");
        m.put("aa.hearing.col_reason", "కారణం");
        m.put("aa.hearing.mode_in_person", "ప్రత్యక్ష హాజరు");
        m.put("aa.hearing.mode_video", "వీడియో ద్వారా");
        m.put("aa.hearing.mode_hybrid", "మిశ్రమ");
        m.put("aa.notice.status_pending", "పెండింగ్‌లో");
        m.put("aa.notice.status_sent", "పంపబడింది");
        m.put("aa.notice.status_failed", "విఫలమైంది");
        m.put("aa.notice.status_cancelled", "రద్దు చేయబడింది");
        m.put("aa.notice.channel_email", "ఈ-మెయిల్");
        m.put("aa.notice.channel_sms", "ఎస్ఎమ్ఎస్");
        m.put("aa.notice.channel_none", "ఏ మార్గం లేదు");
        m.put("aa.notice.recipient_appellant", "అప్పీలుదారు");
        m.put("aa.notice.recipient_respondent", "ప్రతివాది");
        m.put("aa.notice.recipient_ombudsman", "అంబుడ్స్‌మన్");
        m.put("aa.notice.queued_not_sent", "నోటీసు నమోదు చేయబడింది — పంపడానికి వేచి ఉంది");
        m.put("aa.order.revision_label", "సవరణ");
        m.put("aa.order.correction_reason", "సవరణకు కారణం");
        m.put("aa.order.issuing_authority", "ఉత్తర్వు జారీ చేసిన అధికారం");
        m.put("aa.order.clause_relied_on", "ఆధారపడిన నిబంధన: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "ఈ విడుదలలో ఉత్తర్వు డౌన్‌లోడ్ చేయగల పీడీఎఫ్ అందుబాటులో లేదు");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "உங்கள் மேல்முறையீடு {appealNumber} பரிசீலனைக்கு ஏற்கப்பட்டுள்ளது.");
        m.put("aa.notify.appeal_rejected", "உங்கள் மேல்முறையீடு {appealNumber} பரிசீலனைக்கு ஏற்கப்படவில்லை.");
        m.put("aa.notify.hearing_scheduled",
              "உங்கள் மேல்முறையீடு {appealNumber} மீதான விசாரணை {hearingDate} அன்று {hearingVenue} இல் நிர்ணயிக்கப்பட்டுள்ளது.");
        m.put("aa.notify.hearing_rescheduled",
              "உங்கள் மேல்முறையீடு {appealNumber} மீதான விசாரணை {hearingDate} அன்று {hearingVenue} இற்கு மாற்றப்பட்டுள்ளது.");
        m.put("aa.notify.hearing_adjourned",
              "உங்கள் மேல்முறையீடு {appealNumber} மீதான விசாரணை ஒத்திவைக்கப்பட்டுள்ளது. புதிய தேதி உங்களுக்குத் தெரிவிக்கப்படும்.");
        m.put("aa.notify.order_passed", "உங்கள் மேல்முறையீடு {appealNumber} மீது {orderDate} அன்று ஆணை பிறப்பிக்கப்பட்டது.");
        m.put("aa.notify.order_corrected", "உங்கள் மேல்முறையீடு {appealNumber} மீதான ஆணையில் ஒரு திருத்தம் செய்யப்பட்டது.");
        m.put("aa.notify.appeal_dismissed", "உங்கள் மேல்முறையீடு {appealNumber} எந்த நிவாரணமும் இன்றி முடிக்கப்பட்டுள்ளது.");
        m.put("aa.notify.appeal_remanded", "உங்கள் மேல்முறையீடு {appealNumber} புதிதாகப் பரிசீலிக்க திரும்ப அனுப்பப்பட்டுள்ளது.");
        m.put("aa.notify.hearing_reminder",
              "நினைவூட்டல்: உங்கள் மேல்முறையீடு {appealNumber} மீதான விசாரணை {hearingDate} அன்று {hearingVenue} இல் உள்ளது.");
        m.put("aa.notify.officer.appeal_accepted", "மேல்முறையீடு {appealNumber} பரிசீலனைக்கு ஏற்கப்பட்டது");
        m.put("aa.notify.officer.appeal_rejected", "மேல்முறையீடு {appealNumber} பரிசீலனைக்கு ஏற்கப்படவில்லை");
        m.put("aa.notify.officer.assigned_to_bench", "மேல்முறையீடு {appealNumber} ஒரு அமர்வுக்கு ஒப்படைக்கப்பட்டது");
        m.put("aa.notify.officer.hearing_scheduled",
              "மேல்முறையீடு {appealNumber} க்கு {hearingDate} அன்று விசாரணை நிர்ணயிக்கப்பட்டது");
        m.put("aa.notify.officer.hearing_rescheduled",
              "மேல்முறையீடு {appealNumber} இன் விசாரணை {hearingDate} க்கு மாற்றப்பட்டது");
        m.put("aa.notify.officer.hearing_adjourned", "மேல்முறையீடு {appealNumber} இன் விசாரணை ஒத்திவைக்கப்பட்டது");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "மேல்முறையீடு {appealNumber} இன் விசாரணை முடிவு {outcome} எனப் பதிவு செய்யப்பட்டது");
        m.put("aa.notify.officer.order_passed", "மேல்முறையீடு {appealNumber} மீது {orderDate} அன்று ஆணை பிறப்பிக்கப்பட்டது");
        m.put("aa.notify.officer.order_corrected", "மேல்முறையீடு {appealNumber} மீதான ஆணை திருத்தப்பட்டது");
        m.put("aa.notify.officer.appeal_dismissed", "மேல்முறையீடு {appealNumber} எந்த நிவாரணமும் இன்றி முடிக்கப்பட்டது");
        m.put("aa.notify.officer.appeal_remanded", "மேல்முறையீடு {appealNumber} புதிதாகப் பரிசீலிக்க திரும்ப அனுப்பப்பட்டது");
        m.put("aa.notify.officer.forwarded_to_authority", "மேல்முறையீடு {appealNumber} மேல்முறையீட்டு ஆணையத்திற்கு அனுப்பப்பட்டது");
        m.put("aa.notify.officer.sent_back_registrar", "மேல்முறையீடு {appealNumber} பதிவாளருக்குத் திரும்ப அனுப்பப்பட்டது");
        m.put("aa.notify.officer.reassigned", "மேல்முறையீடு {appealNumber} உங்களுக்கு மீண்டும் ஒப்படைக்கப்பட்டது");
        m.put("aa.notify.officer.escalated_to_tier2", "மேல்முறையீடு {appealNumber} இரண்டாம் நிலைக்கு அனுப்பப்பட்டது");
        m.put("aa.notify.officer.hearing_reminder",
              "நினைவூட்டல்: மேல்முறையீடு {appealNumber} க்கான விசாரணை {hearingDate} அன்று உள்ளது");
        m.put("aa.notify.officer.sla_reminder", "மேல்முறையீடு {appealNumber} இன் கால எல்லை நெருங்கி வருகிறது");
        m.put("aa.hearing.scheduled_notices_recorded", "விசாரணை நிர்ணயிக்கப்பட்டது. அறிவிப்புகள் அனுப்புவதற்காகப் பதிவு செய்யப்பட்டன.");
        m.put("aa.hearing.rescheduled_notices_recorded", "விசாரணை மீள்நிர்ணயிக்கப்பட்டது. அறிவிப்புகள் அனுப்புவதற்காகப் பதிவு செய்யப்பட்டன.");
        m.put("aa.hearing.adjourned", "விசாரணை ஒத்திவைக்கப்பட்டது");
        m.put("aa.hearing.outcome_recorded", "விசாரணை முடிவு பதிவு செய்யப்பட்டது");
        m.put("aa.hearing.error_officer_double_booked",
              "தேர்ந்தெடுக்கப்பட்ட அதிகாரிக்கு இந்த நேரத்தில் ஏற்கெனவே ஒரு விசாரணை நிர்ணயிக்கப்பட்டுள்ளது");
        m.put("aa.hearing.error_invalid_request", "விசாரணை விவரங்கள் முழுமையற்றவை அல்லது செல்லாதவை");
        m.put("aa.hearing.error_invalid_state",
              "இந்த மேல்முறையீடு இந்த விசாரணை நடவடிக்கை அனுமதிக்கப்படும் நிலையில் இல்லை");
        m.put("aa.hearing.error_no_scheduled_hearing", "இந்த மேல்முறையீட்டுக்கு எந்த விசாரணையும் நிர்ணயிக்கப்படவில்லை");
        m.put("aa.order.issued", "ஆணை பிறப்பிக்கப்பட்டது");
        m.put("aa.order.corrected", "ஆணை திருத்தம் பதிவு செய்யப்பட்டது");
        m.put("aa.order.error_already_issued", "இந்த மேல்முறையீட்டின் மீது ஏற்கெனவே ஒரு ஆணை பிறப்பிக்கப்பட்டுவிட்டது");
        m.put("aa.order.error_ed_approval_required",
              "இந்த ஆணையைப் பிறப்பிப்பதற்கு முன் செயல் இயக்குநரின் ஒப்புதல் தேவை");
        m.put("aa.order.error_invalid_request", "ஆணை விவரங்கள் முழுமையற்றவை அல்லது செல்லாதவை");
        m.put("aa.order.error_no_order_to_correct", "இந்த மேல்முறையீட்டின் மீது திருத்துவதற்கு பிறப்பிக்கப்பட்ட ஆணை இல்லை");
        m.put("aa.hearing.history_title", "விசாரணை வரலாறு");
        m.put("aa.hearing.event_scheduled", "நிர்ணயிக்கப்பட்டது");
        m.put("aa.hearing.event_rescheduled", "மீள்நிர்ணயிக்கப்பட்டது");
        m.put("aa.hearing.event_adjourned", "ஒத்திவைக்கப்பட்டது");
        m.put("aa.hearing.event_completed", "நிறைவடைந்தது");
        m.put("aa.hearing.event_cancelled", "ரத்து செய்யப்பட்டது");
        m.put("aa.hearing.col_date", "தேதி");
        m.put("aa.hearing.col_venue", "இடம்");
        m.put("aa.hearing.col_mode", "முறை");
        m.put("aa.hearing.col_outcome", "முடிவு");
        m.put("aa.hearing.col_reason", "காரணம்");
        m.put("aa.hearing.mode_in_person", "நேரில் ஆஜராதல்");
        m.put("aa.hearing.mode_video", "காணொலி வழியாக");
        m.put("aa.hearing.mode_hybrid", "கலப்பு");
        m.put("aa.notice.status_pending", "நிலுவையில்");
        m.put("aa.notice.status_sent", "அனுப்பப்பட்டது");
        m.put("aa.notice.status_failed", "தோல்வியடைந்தது");
        m.put("aa.notice.status_cancelled", "ரத்து செய்யப்பட்டது");
        m.put("aa.notice.channel_email", "மின்னஞ்சல்");
        m.put("aa.notice.channel_sms", "குறுஞ்செய்தி");
        m.put("aa.notice.channel_none", "வழி எதுவும் இல்லை");
        m.put("aa.notice.recipient_appellant", "மேல்முறையீட்டாளர்");
        m.put("aa.notice.recipient_respondent", "எதிர்மனுதாரர்");
        m.put("aa.notice.recipient_ombudsman", "நியாயவாதி");
        m.put("aa.notice.queued_not_sent", "அறிவிப்பு பதிவு செய்யப்பட்டது — அனுப்புவதற்குக் காத்திருக்கிறது");
        m.put("aa.order.revision_label", "திருத்தப்பட்ட பதிப்பு");
        m.put("aa.order.correction_reason", "திருத்தத்திற்கான காரணம்");
        m.put("aa.order.issuing_authority", "ஆணை பிறப்பித்த அதிகாரம்");
        m.put("aa.order.clause_relied_on", "சார்ந்த பிரிவு: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "இந்த வெளியீட்டில் ஆணையின் பதிவிறக்கக்கூடிய பீடிஎஃப் கிடைக்கவில்லை");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "તમારી અપીલ {appealNumber} વિચારણા માટે સ્વીકારવામાં આવી છે.");
        m.put("aa.notify.appeal_rejected", "તમારી અપીલ {appealNumber} વિચારણા માટે સ્વીકારવામાં આવી નથી.");
        m.put("aa.notify.hearing_scheduled",
              "તમારી અપીલ {appealNumber} પરની સુનાવણી {hearingDate} ના રોજ {hearingVenue} ખાતે નિર્ધારિત કરવામાં આવી છે.");
        m.put("aa.notify.hearing_rescheduled",
              "તમારી અપીલ {appealNumber} પરની સુનાવણી {hearingDate} ના રોજ {hearingVenue} ખાતે ખસેડવામાં આવી છે.");
        m.put("aa.notify.hearing_adjourned",
              "તમારી અપીલ {appealNumber} પરની સુનાવણી મુલતવી રાખવામાં આવી છે. નવી તારીખ તમને જણાવવામાં આવશે.");
        m.put("aa.notify.order_passed", "તમારી અપીલ {appealNumber} પર {orderDate} ના રોજ આદેશ જારી કરવામાં આવ્યો.");
        m.put("aa.notify.order_corrected", "તમારી અપીલ {appealNumber} પરના આદેશમાં એક સુધારો કરવામાં આવ્યો.");
        m.put("aa.notify.appeal_dismissed", "તમારી અપીલ {appealNumber} કોઈ રાહત વિના બંધ કરવામાં આવી છે.");
        m.put("aa.notify.appeal_remanded", "તમારી અપીલ {appealNumber} નવેસરથી વિચારણા માટે પરત મોકલવામાં આવી છે.");
        m.put("aa.notify.hearing_reminder",
              "સ્મરણ: તમારી અપીલ {appealNumber} પરની સુનાવણી {hearingDate} ના રોજ {hearingVenue} ખાતે છે.");
        m.put("aa.notify.officer.appeal_accepted", "અપીલ {appealNumber} વિચારણા માટે સ્વીકારવામાં આવી");
        m.put("aa.notify.officer.appeal_rejected", "અપીલ {appealNumber} વિચારણા માટે સ્વીકારવામાં આવી નથી");
        m.put("aa.notify.officer.assigned_to_bench", "અપીલ {appealNumber} એક બેન્ચને સોંપવામાં આવી");
        m.put("aa.notify.officer.hearing_scheduled",
              "અપીલ {appealNumber} માટે {hearingDate} ના રોજ સુનાવણી નિર્ધારિત કરવામાં આવી");
        m.put("aa.notify.officer.hearing_rescheduled",
              "અપીલ {appealNumber} ની સુનાવણી {hearingDate} ના રોજ ખસેડવામાં આવી");
        m.put("aa.notify.officer.hearing_adjourned", "અપીલ {appealNumber} ની સુનાવણી મુલતવી રાખવામાં આવી");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "અપીલ {appealNumber} ની સુનાવણીનું પરિણામ {outcome} તરીકે નોંધવામાં આવ્યું");
        m.put("aa.notify.officer.order_passed", "અપીલ {appealNumber} પર {orderDate} ના રોજ આદેશ જારી કરવામાં આવ્યો");
        m.put("aa.notify.officer.order_corrected", "અપીલ {appealNumber} પરના આદેશમાં સુધારો કરવામાં આવ્યો");
        m.put("aa.notify.officer.appeal_dismissed", "અપીલ {appealNumber} કોઈ રાહત વિના બંધ કરવામાં આવી");
        m.put("aa.notify.officer.appeal_remanded", "અપીલ {appealNumber} નવેસરથી વિચારણા માટે પરત મોકલવામાં આવી");
        m.put("aa.notify.officer.forwarded_to_authority", "અપીલ {appealNumber} અપીલ સત્તાધિકારીને મોકલવામાં આવી");
        m.put("aa.notify.officer.sent_back_registrar", "અપીલ {appealNumber} રજિસ્ટ્રારને પરત મોકલવામાં આવી");
        m.put("aa.notify.officer.reassigned", "અપીલ {appealNumber} તમને ફરીથી સોંપવામાં આવી");
        m.put("aa.notify.officer.escalated_to_tier2", "અપીલ {appealNumber} બીજા સ્તરે મોકલવામાં આવી");
        m.put("aa.notify.officer.hearing_reminder",
              "સ્મરણ: અપીલ {appealNumber} માટે સુનાવણી {hearingDate} ના રોજ છે");
        m.put("aa.notify.officer.sla_reminder", "અપીલ {appealNumber} ની નિયત તારીખ નજીક આવી રહી છે");
        m.put("aa.hearing.scheduled_notices_recorded", "સુનાવણી નિર્ધારિત કરવામાં આવી. નોટિસો મોકલવા માટે નોંધવામાં આવી છે.");
        m.put("aa.hearing.rescheduled_notices_recorded", "સુનાવણી ફરીથી નિર્ધારિત કરવામાં આવી. નોટિસો મોકલવા માટે નોંધવામાં આવી છે.");
        m.put("aa.hearing.adjourned", "સુનાવણી મુલતવી રાખવામાં આવી");
        m.put("aa.hearing.outcome_recorded", "સુનાવણીનું પરિણામ નોંધવામાં આવ્યું");
        m.put("aa.hearing.error_officer_double_booked",
              "પસંદ કરેલા અધિકારીની આ સમયે પહેલેથી જ એક સુનાવણી નિર્ધારિત છે");
        m.put("aa.hearing.error_invalid_request", "સુનાવણીની વિગતો અપૂર્ણ અથવા અમાન્ય છે");
        m.put("aa.hearing.error_invalid_state",
              "આ અપીલ એવી સ્થિતિમાં નથી જેમાં સુનાવણીની આ કાર્યવાહી માન્ય હોય");
        m.put("aa.hearing.error_no_scheduled_hearing", "આ અપીલ માટે કોઈ સુનાવણી નિર્ધારિત નથી");
        m.put("aa.order.issued", "આદેશ જારી કરવામાં આવ્યો");
        m.put("aa.order.corrected", "આદેશનો સુધારો નોંધવામાં આવ્યો");
        m.put("aa.order.error_already_issued", "આ અપીલ પર પહેલેથી જ આદેશ જારી કરવામાં આવ્યો છે");
        m.put("aa.order.error_ed_approval_required",
              "આ આદેશ જારી કરવા પહેલાં કાર્યકારી નિયામકની મંજૂરી આવશ્યક છે");
        m.put("aa.order.error_invalid_request", "આદેશની વિગતો અપૂર્ણ અથવા અમાન્ય છે");
        m.put("aa.order.error_no_order_to_correct", "આ અપીલ પર સુધારા માટે કોઈ જારી કરેલો આદેશ નથી");
        m.put("aa.hearing.history_title", "સુનાવણીનો ઇતિહાસ");
        m.put("aa.hearing.event_scheduled", "નિર્ધારિત");
        m.put("aa.hearing.event_rescheduled", "ફરીથી નિર્ધારિત");
        m.put("aa.hearing.event_adjourned", "મુલતવી");
        m.put("aa.hearing.event_completed", "પૂર્ણ");
        m.put("aa.hearing.event_cancelled", "રદ");
        m.put("aa.hearing.col_date", "તારીખ");
        m.put("aa.hearing.col_venue", "સ્થળ");
        m.put("aa.hearing.col_mode", "પદ્ધતિ");
        m.put("aa.hearing.col_outcome", "પરિણામ");
        m.put("aa.hearing.col_reason", "કારણ");
        m.put("aa.hearing.mode_in_person", "પ્રત્યક્ષ હાજરી");
        m.put("aa.hearing.mode_video", "વીડિયો દ્વારા");
        m.put("aa.hearing.mode_hybrid", "મિશ્ર");
        m.put("aa.notice.status_pending", "બાકી");
        m.put("aa.notice.status_sent", "મોકલવામાં આવી");
        m.put("aa.notice.status_failed", "નિષ્ફળ");
        m.put("aa.notice.status_cancelled", "રદ");
        m.put("aa.notice.channel_email", "ઈ-મેલ");
        m.put("aa.notice.channel_sms", "એસએમએસ");
        m.put("aa.notice.channel_none", "કોઈ માધ્યમ નથી");
        m.put("aa.notice.recipient_appellant", "અપીલકર્તા");
        m.put("aa.notice.recipient_respondent", "પ્રતિવાદી");
        m.put("aa.notice.recipient_ombudsman", "લોકપાલ");
        m.put("aa.notice.queued_not_sent", "નોટિસ નોંધવામાં આવી — મોકલવાની પ્રતીક્ષામાં");
        m.put("aa.order.revision_label", "સંશોધન");
        m.put("aa.order.correction_reason", "સુધારાનું કારણ");
        m.put("aa.order.issuing_authority", "આદેશ જારી કરનાર સત્તાધિકારી");
        m.put("aa.order.clause_relied_on", "આધાર લીધેલ કલમ: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "આ આવૃત્તિમાં આદેશની ડાઉનલોડ કરી શકાય એવી પીડીએફ ઉપલબ્ધ નથી");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "آپ کی اپیل {appealNumber} غور کے لیے قبول کر لی گئی ہے۔");
        m.put("aa.notify.appeal_rejected", "آپ کی اپیل {appealNumber} غور کے لیے قبول نہیں کی گئی ہے۔");
        m.put("aa.notify.hearing_scheduled",
              "آپ کی اپیل {appealNumber} پر سماعت {hearingDate} کو {hearingVenue} میں مقرر کی گئی ہے۔");
        m.put("aa.notify.hearing_rescheduled",
              "آپ کی اپیل {appealNumber} پر سماعت {hearingDate} کو {hearingVenue} میں منتقل کر دی گئی ہے۔");
        m.put("aa.notify.hearing_adjourned",
              "آپ کی اپیل {appealNumber} پر سماعت ملتوی کر دی گئی ہے۔ نئی تاریخ آپ کو بتائی جائے گی۔");
        m.put("aa.notify.order_passed", "آپ کی اپیل {appealNumber} پر {orderDate} کو حکم جاری کیا گیا۔");
        m.put("aa.notify.order_corrected", "آپ کی اپیل {appealNumber} پر جاری حکم میں ایک تصحیح کی گئی۔");
        m.put("aa.notify.appeal_dismissed", "آپ کی اپیل {appealNumber} کسی ریلیف کے بغیر بند کر دی گئی ہے۔");
        m.put("aa.notify.appeal_remanded", "آپ کی اپیل {appealNumber} نئے سرے سے غور کے لیے واپس بھیج دی گئی ہے۔");
        m.put("aa.notify.hearing_reminder",
              "یاد دہانی: آپ کی اپیل {appealNumber} پر سماعت {hearingDate} کو {hearingVenue} میں ہے۔");
        m.put("aa.notify.officer.appeal_accepted", "اپیل {appealNumber} غور کے لیے قبول کی گئی");
        m.put("aa.notify.officer.appeal_rejected", "اپیل {appealNumber} غور کے لیے قبول نہیں کی گئی");
        m.put("aa.notify.officer.assigned_to_bench", "اپیل {appealNumber} ایک بینچ کے سپرد کی گئی");
        m.put("aa.notify.officer.hearing_scheduled",
              "اپیل {appealNumber} کے لیے {hearingDate} کو سماعت مقرر کی گئی");
        m.put("aa.notify.officer.hearing_rescheduled",
              "اپیل {appealNumber} کی سماعت {hearingDate} کو منتقل کی گئی");
        m.put("aa.notify.officer.hearing_adjourned", "اپیل {appealNumber} کی سماعت ملتوی کر دی گئی");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "اپیل {appealNumber} کی سماعت کا نتیجہ {outcome} کے طور پر درج کیا گیا");
        m.put("aa.notify.officer.order_passed", "اپیل {appealNumber} پر {orderDate} کو حکم جاری کیا گیا");
        m.put("aa.notify.officer.order_corrected", "اپیل {appealNumber} پر جاری حکم میں تصحیح کی گئی");
        m.put("aa.notify.officer.appeal_dismissed", "اپیل {appealNumber} کسی ریلیف کے بغیر بند کر دی گئی");
        m.put("aa.notify.officer.appeal_remanded", "اپیل {appealNumber} نئے سرے سے غور کے لیے واپس بھیجی گئی");
        m.put("aa.notify.officer.forwarded_to_authority", "اپیل {appealNumber} اپیلٹ اتھارٹی کو ارسال کی گئی");
        m.put("aa.notify.officer.sent_back_registrar", "اپیل {appealNumber} رجسٹرار کو واپس بھیجی گئی");
        m.put("aa.notify.officer.reassigned", "اپیل {appealNumber} دوبارہ آپ کے سپرد کی گئی");
        m.put("aa.notify.officer.escalated_to_tier2", "اپیل {appealNumber} دوسرے درجے پر بھیجی گئی");
        m.put("aa.notify.officer.hearing_reminder",
              "یاد دہانی: اپیل {appealNumber} کے لیے سماعت {hearingDate} کو ہے");
        m.put("aa.notify.officer.sla_reminder", "اپیل {appealNumber} کی مقررہ تاریخ قریب آ رہی ہے");
        m.put("aa.hearing.scheduled_notices_recorded", "سماعت مقرر کر دی گئی۔ نوٹس بھیجنے کے لیے درج کر لیے گئے ہیں۔");
        m.put("aa.hearing.rescheduled_notices_recorded", "سماعت دوبارہ مقرر کر دی گئی۔ نوٹس بھیجنے کے لیے درج کر لیے گئے ہیں۔");
        m.put("aa.hearing.adjourned", "سماعت ملتوی کر دی گئی");
        m.put("aa.hearing.outcome_recorded", "سماعت کا نتیجہ درج کر لیا گیا");
        m.put("aa.hearing.error_officer_double_booked",
              "منتخب افسر کی اس وقت پہلے ہی ایک سماعت مقرر ہے");
        m.put("aa.hearing.error_invalid_request", "سماعت کی تفصیلات نامکمل یا غیر درست ہیں");
        m.put("aa.hearing.error_invalid_state",
              "یہ اپیل ایسی حالت میں نہیں ہے جس میں سماعت کی یہ کارروائی جائز ہو");
        m.put("aa.hearing.error_no_scheduled_hearing", "اس اپیل کے لیے کوئی سماعت مقرر نہیں ہے");
        m.put("aa.order.issued", "حکم جاری کر دیا گیا");
        m.put("aa.order.corrected", "حکم کی تصحیح درج کر لی گئی");
        m.put("aa.order.error_already_issued", "اس اپیل پر پہلے ہی ایک حکم جاری کیا جا چکا ہے");
        m.put("aa.order.error_ed_approval_required",
              "یہ حکم جاری کرنے سے پہلے ایگزیکٹو ڈائریکٹر کی منظوری لازمی ہے");
        m.put("aa.order.error_invalid_request", "حکم کی تفصیلات نامکمل یا غیر درست ہیں");
        m.put("aa.order.error_no_order_to_correct", "اس اپیل پر تصحیح کے لیے کوئی جاری کردہ حکم موجود نہیں");
        m.put("aa.hearing.history_title", "سماعت کی تفصیل");
        m.put("aa.hearing.event_scheduled", "مقرر");
        m.put("aa.hearing.event_rescheduled", "دوبارہ مقرر");
        m.put("aa.hearing.event_adjourned", "ملتوی");
        m.put("aa.hearing.event_completed", "مکمل");
        m.put("aa.hearing.event_cancelled", "منسوخ");
        m.put("aa.hearing.col_date", "تاریخ");
        m.put("aa.hearing.col_venue", "مقام");
        m.put("aa.hearing.col_mode", "طریقہ");
        m.put("aa.hearing.col_outcome", "نتیجہ");
        m.put("aa.hearing.col_reason", "وجہ");
        m.put("aa.hearing.mode_in_person", "بذاتِ خود حاضری");
        m.put("aa.hearing.mode_video", "ویڈیو کے ذریعے");
        m.put("aa.hearing.mode_hybrid", "مخلوط");
        m.put("aa.notice.status_pending", "زیرِ التوا");
        m.put("aa.notice.status_sent", "بھیجا گیا");
        m.put("aa.notice.status_failed", "ناکام");
        m.put("aa.notice.status_cancelled", "منسوخ");
        m.put("aa.notice.channel_email", "ای-میل");
        m.put("aa.notice.channel_sms", "ایس ایم ایس");
        m.put("aa.notice.channel_none", "کوئی ذریعہ نہیں");
        m.put("aa.notice.recipient_appellant", "اپیل کنندہ");
        m.put("aa.notice.recipient_respondent", "جواب دہندہ");
        m.put("aa.notice.recipient_ombudsman", "محتسب");
        m.put("aa.notice.queued_not_sent", "نوٹس درج کر لیا گیا — بھیجنے کا منتظر");
        m.put("aa.order.revision_label", "ترمیم");
        m.put("aa.order.correction_reason", "تصحیح کی وجہ");
        m.put("aa.order.issuing_authority", "حکم جاری کرنے والا ادارہ");
        m.put("aa.order.clause_relied_on", "بنیاد بننے والی شرط: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "اس اجرا میں حکم کی ڈاؤن لوڈ کے قابل پی ڈی ایف دستیاب نہیں ہے");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಪರಿಗಣನೆಗೆ ಸ್ವೀಕರಿಸಲಾಗಿದೆ.");
        m.put("aa.notify.appeal_rejected", "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಪರಿಗಣನೆಗೆ ಸ್ವೀಕರಿಸಲಾಗಿಲ್ಲ.");
        m.put("aa.notify.hearing_scheduled",
              "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲಿನ ವಿಚಾರಣೆಯನ್ನು {hearingDate} ರಂದು {hearingVenue} ನಲ್ಲಿ ನಿಗದಿಪಡಿಸಲಾಗಿದೆ.");
        m.put("aa.notify.hearing_rescheduled",
              "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲಿನ ವಿಚಾರಣೆಯನ್ನು {hearingDate} ರಂದು {hearingVenue} ಗೆ ಬದಲಾಯಿಸಲಾಗಿದೆ.");
        m.put("aa.notify.hearing_adjourned",
              "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲಿನ ವಿಚಾರಣೆಯನ್ನು ಮುಂದೂಡಲಾಗಿದೆ. ಹೊಸ ದಿನಾಂಕವನ್ನು ನಿಮಗೆ ತಿಳಿಸಲಾಗುವುದು.");
        m.put("aa.notify.order_passed", "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲೆ {orderDate} ರಂದು ಆದೇಶ ಹೊರಡಿಸಲಾಯಿತು.");
        m.put("aa.notify.order_corrected", "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲಿನ ಆದೇಶದಲ್ಲಿ ಒಂದು ತಿದ್ದುಪಡಿ ಮಾಡಲಾಯಿತು.");
        m.put("aa.notify.appeal_dismissed", "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಯಾವುದೇ ಪರಿಹಾರವಿಲ್ಲದೆ ಮುಕ್ತಾಯಗೊಳಿಸಲಾಗಿದೆ.");
        m.put("aa.notify.appeal_remanded", "ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಹೊಸದಾಗಿ ಪರಿಗಣಿಸಲು ಹಿಂತಿರುಗಿಸಲಾಗಿದೆ.");
        m.put("aa.notify.hearing_reminder",
              "ನೆನಪೋಲೆ: ನಿಮ್ಮ ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲಿನ ವಿಚಾರಣೆ {hearingDate} ರಂದು {hearingVenue} ನಲ್ಲಿ ಇದೆ.");
        m.put("aa.notify.officer.appeal_accepted", "ಮೇಲ್ಮನವಿ {appealNumber} ಪರಿಗಣನೆಗೆ ಸ್ವೀಕರಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.appeal_rejected", "ಮೇಲ್ಮನವಿ {appealNumber} ಪರಿಗಣನೆಗೆ ಸ್ವೀಕರಿಸಲಾಗಿಲ್ಲ");
        m.put("aa.notify.officer.assigned_to_bench", "ಮೇಲ್ಮನವಿ {appealNumber} ಒಂದು ಪೀಠಕ್ಕೆ ವಹಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.hearing_scheduled",
              "ಮೇಲ್ಮನವಿ {appealNumber} ಗಾಗಿ {hearingDate} ರಂದು ವಿಚಾರಣೆ ನಿಗದಿಪಡಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.hearing_rescheduled",
              "ಮೇಲ್ಮನವಿ {appealNumber} ವಿಚಾರಣೆಯನ್ನು {hearingDate} ಗೆ ಬದಲಾಯಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.hearing_adjourned", "ಮೇಲ್ಮನವಿ {appealNumber} ವಿಚಾರಣೆಯನ್ನು ಮುಂದೂಡಲಾಯಿತು");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "ಮೇಲ್ಮನವಿ {appealNumber} ವಿಚಾರಣೆಯ ಫಲಿತಾಂಶವನ್ನು {outcome} ಎಂದು ದಾಖಲಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.order_passed", "ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲೆ {orderDate} ರಂದು ಆದೇಶ ಹೊರಡಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.order_corrected", "ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲಿನ ಆದೇಶವನ್ನು ತಿದ್ದುಪಡಿ ಮಾಡಲಾಯಿತು");
        m.put("aa.notify.officer.appeal_dismissed", "ಮೇಲ್ಮನವಿ {appealNumber} ಯಾವುದೇ ಪರಿಹಾರವಿಲ್ಲದೆ ಮುಕ್ತಾಯಗೊಳಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.appeal_remanded", "ಮೇಲ್ಮನವಿ {appealNumber} ಹೊಸದಾಗಿ ಪರಿಗಣಿಸಲು ಹಿಂತಿರುಗಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.forwarded_to_authority", "ಮೇಲ್ಮನವಿ {appealNumber} ಮೇಲ್ಮನವಿ ಪ್ರಾಧಿಕಾರಕ್ಕೆ ರವಾನಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.sent_back_registrar", "ಮೇಲ್ಮನವಿ {appealNumber} ರಿಜಿಸ್ಟ್ರಾರ್‌ಗೆ ಹಿಂತಿರುಗಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.reassigned", "ಮೇಲ್ಮನವಿ {appealNumber} ನಿಮಗೆ ಮರುವಹಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.escalated_to_tier2", "ಮೇಲ್ಮನವಿ {appealNumber} ಎರಡನೇ ಹಂತಕ್ಕೆ ಕಳುಹಿಸಲಾಯಿತು");
        m.put("aa.notify.officer.hearing_reminder",
              "ನೆನಪೋಲೆ: ಮೇಲ್ಮನವಿ {appealNumber} ಗಾಗಿ ವಿಚಾರಣೆ {hearingDate} ರಂದು ಇದೆ");
        m.put("aa.notify.officer.sla_reminder", "ಮೇಲ್ಮನವಿ {appealNumber} ನಿಗದಿತ ದಿನಾಂಕ ಸಮೀಪಿಸುತ್ತಿದೆ");
        m.put("aa.hearing.scheduled_notices_recorded", "ವಿಚಾರಣೆ ನಿಗದಿಪಡಿಸಲಾಗಿದೆ. ಸೂಚನೆಗಳನ್ನು ಕಳುಹಿಸಲು ದಾಖಲಿಸಲಾಗಿದೆ.");
        m.put("aa.hearing.rescheduled_notices_recorded", "ವಿಚಾರಣೆ ಮರುನಿಗದಿಪಡಿಸಲಾಗಿದೆ. ಸೂಚನೆಗಳನ್ನು ಕಳುಹಿಸಲು ದಾಖಲಿಸಲಾಗಿದೆ.");
        m.put("aa.hearing.adjourned", "ವಿಚಾರಣೆ ಮುಂದೂಡಲಾಗಿದೆ");
        m.put("aa.hearing.outcome_recorded", "ವಿಚಾರಣೆಯ ಫಲಿತಾಂಶ ದಾಖಲಿಸಲಾಗಿದೆ");
        m.put("aa.hearing.error_officer_double_booked",
              "ಆಯ್ಕೆ ಮಾಡಿದ ಅಧಿಕಾರಿಗೆ ಈ ಸಮಯದಲ್ಲಿ ಈಗಾಗಲೇ ಒಂದು ವಿಚಾರಣೆ ನಿಗದಿಯಾಗಿದೆ");
        m.put("aa.hearing.error_invalid_request", "ವಿಚಾರಣೆಯ ವಿವರಗಳು ಅಪೂರ್ಣ ಅಥವಾ ಅಮಾನ್ಯವಾಗಿವೆ");
        m.put("aa.hearing.error_invalid_state",
              "ಈ ಮೇಲ್ಮನವಿ ಈ ವಿಚಾರಣೆ ಕ್ರಮಕ್ಕೆ ಅನುಮತಿಸುವ ಸ್ಥಿತಿಯಲ್ಲಿ ಇಲ್ಲ");
        m.put("aa.hearing.error_no_scheduled_hearing", "ಈ ಮೇಲ್ಮನವಿಗಾಗಿ ಯಾವುದೇ ವಿಚಾರಣೆ ನಿಗದಿಯಾಗಿಲ್ಲ");
        m.put("aa.order.issued", "ಆದೇಶ ಹೊರಡಿಸಲಾಗಿದೆ");
        m.put("aa.order.corrected", "ಆದೇಶದ ತಿದ್ದುಪಡಿ ದಾಖಲಿಸಲಾಗಿದೆ");
        m.put("aa.order.error_already_issued", "ಈ ಮೇಲ್ಮನವಿಯ ಮೇಲೆ ಈಗಾಗಲೇ ಒಂದು ಆದೇಶ ಹೊರಡಿಸಲಾಗಿದೆ");
        m.put("aa.order.error_ed_approval_required",
              "ಈ ಆದೇಶ ಹೊರಡಿಸುವ ಮೊದಲು ಕಾರ್ಯನಿರ್ವಾಹಕ ನಿರ್ದೇಶಕರ ಅನುಮೋದನೆ ಅಗತ್ಯ");
        m.put("aa.order.error_invalid_request", "ಆದೇಶದ ವಿವರಗಳು ಅಪೂರ್ಣ ಅಥವಾ ಅಮಾನ್ಯವಾಗಿವೆ");
        m.put("aa.order.error_no_order_to_correct", "ಈ ಮೇಲ್ಮನವಿಯ ಮೇಲೆ ತಿದ್ದುಪಡಿ ಮಾಡಲು ಹೊರಡಿಸಿದ ಆದೇಶ ಇಲ್ಲ");
        m.put("aa.hearing.history_title", "ವಿಚಾರಣೆಯ ಇತಿಹಾಸ");
        m.put("aa.hearing.event_scheduled", "ನಿಗದಿಯಾಗಿದೆ");
        m.put("aa.hearing.event_rescheduled", "ಮರುನಿಗದಿಯಾಗಿದೆ");
        m.put("aa.hearing.event_adjourned", "ಮುಂದೂಡಲಾಗಿದೆ");
        m.put("aa.hearing.event_completed", "ಪೂರ್ಣಗೊಂಡಿದೆ");
        m.put("aa.hearing.event_cancelled", "ರದ್ದಾಗಿದೆ");
        m.put("aa.hearing.col_date", "ದಿನಾಂಕ");
        m.put("aa.hearing.col_venue", "ಸ್ಥಳ");
        m.put("aa.hearing.col_mode", "ವಿಧಾನ");
        m.put("aa.hearing.col_outcome", "ಫಲಿತಾಂಶ");
        m.put("aa.hearing.col_reason", "ಕಾರಣ");
        m.put("aa.hearing.mode_in_person", "ಪ್ರತ್ಯಕ್ಷ ಹಾಜರಾತಿ");
        m.put("aa.hearing.mode_video", "ವಿಡಿಯೋ ಮೂಲಕ");
        m.put("aa.hearing.mode_hybrid", "ಮಿಶ್ರ");
        m.put("aa.notice.status_pending", "ಬಾಕಿ");
        m.put("aa.notice.status_sent", "ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("aa.notice.status_failed", "ವಿಫಲವಾಗಿದೆ");
        m.put("aa.notice.status_cancelled", "ರದ್ದಾಗಿದೆ");
        m.put("aa.notice.channel_email", "ಇ-ಮೇಲ್");
        m.put("aa.notice.channel_sms", "ಎಸ್ಎಂಎಸ್");
        m.put("aa.notice.channel_none", "ಯಾವುದೇ ಮಾರ್ಗವಿಲ್ಲ");
        m.put("aa.notice.recipient_appellant", "ಮೇಲ್ಮನವಿದಾರ");
        m.put("aa.notice.recipient_respondent", "ಪ್ರತಿವಾದಿ");
        m.put("aa.notice.recipient_ombudsman", "ಒಂಬುಡ್ಸ್‌ಮನ್");
        m.put("aa.notice.queued_not_sent", "ಸೂಚನೆ ದಾಖಲಿಸಲಾಗಿದೆ — ಕಳುಹಿಸಲು ಬಾಕಿ");
        m.put("aa.order.revision_label", "ಪರಿಷ್ಕರಣೆ");
        m.put("aa.order.correction_reason", "ತಿದ್ದುಪಡಿಯ ಕಾರಣ");
        m.put("aa.order.issuing_authority", "ಆದೇಶ ಹೊರಡಿಸಿದ ಪ್ರಾಧಿಕಾರ");
        m.put("aa.order.clause_relied_on", "ಆಧರಿಸಿದ ಷರತ್ತು: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "ಈ ಬಿಡುಗಡೆಯಲ್ಲಿ ಆದೇಶದ ಡೌನ್‌ಲೋಡ್ ಮಾಡಬಹುದಾದ ಪಿಡಿಎಫ್ ಲಭ್ಯವಿಲ್ಲ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aa.notify.appeal_accepted", "നിങ്ങളുടെ അപ്പീൽ {appealNumber} പരിഗണനയ്ക്കായി സ്വീകരിച്ചിട്ടുണ്ട്.");
        m.put("aa.notify.appeal_rejected", "നിങ്ങളുടെ അപ്പീൽ {appealNumber} പരിഗണനയ്ക്കായി സ്വീകരിച്ചിട്ടില്ല.");
        m.put("aa.notify.hearing_scheduled",
              "നിങ്ങളുടെ അപ്പീൽ {appealNumber} സംബന്ധിച്ച വാദം കേൾക്കൽ {hearingDate}-ന് {hearingVenue}-ൽ നിശ്ചയിച്ചിട്ടുണ്ട്.");
        m.put("aa.notify.hearing_rescheduled",
              "നിങ്ങളുടെ അപ്പീൽ {appealNumber} സംബന്ധിച്ച വാദം കേൾക്കൽ {hearingDate}-ന് {hearingVenue}-ലേക്ക് മാറ്റിയിട്ടുണ്ട്.");
        m.put("aa.notify.hearing_adjourned",
              "നിങ്ങളുടെ അപ്പീൽ {appealNumber} സംബന്ധിച്ച വാദം കേൾക്കൽ മാറ്റിവച്ചിട്ടുണ്ട്. പുതിയ തീയതി നിങ്ങളെ അറിയിക്കും.");
        m.put("aa.notify.order_passed", "നിങ്ങളുടെ അപ്പീൽ {appealNumber}-ൽ {orderDate}-ന് ഉത്തരവ് പുറപ്പെടുവിച്ചു.");
        m.put("aa.notify.order_corrected", "നിങ്ങളുടെ അപ്പീൽ {appealNumber}-ലെ ഉത്തരവിൽ ഒരു തിരുത്തൽ വരുത്തി.");
        m.put("aa.notify.appeal_dismissed", "നിങ്ങളുടെ അപ്പീൽ {appealNumber} യാതൊരു ആശ്വാസവുമില്ലാതെ അവസാനിപ്പിച്ചിട്ടുണ്ട്.");
        m.put("aa.notify.appeal_remanded", "നിങ്ങളുടെ അപ്പീൽ {appealNumber} പുതുതായി പരിഗണിക്കാൻ തിരികെ അയച്ചിട്ടുണ്ട്.");
        m.put("aa.notify.hearing_reminder",
              "ഓർമ്മപ്പെടുത്തൽ: നിങ്ങളുടെ അപ്പീൽ {appealNumber} സംബന്ധിച്ച വാദം കേൾക്കൽ {hearingDate}-ന് {hearingVenue}-ൽ ആണ്.");
        m.put("aa.notify.officer.appeal_accepted", "അപ്പീൽ {appealNumber} പരിഗണനയ്ക്കായി സ്വീകരിച്ചു");
        m.put("aa.notify.officer.appeal_rejected", "അപ്പീൽ {appealNumber} പരിഗണനയ്ക്കായി സ്വീകരിച്ചിട്ടില്ല");
        m.put("aa.notify.officer.assigned_to_bench", "അപ്പീൽ {appealNumber} ഒരു ബെഞ്ചിന് നൽകി");
        m.put("aa.notify.officer.hearing_scheduled",
              "അപ്പീൽ {appealNumber}-നായി {hearingDate}-ന് വാദം കേൾക്കൽ നിശ്ചയിച്ചു");
        m.put("aa.notify.officer.hearing_rescheduled",
              "അപ്പീൽ {appealNumber}-ന്റെ വാദം കേൾക്കൽ {hearingDate}-ലേക്ക് മാറ്റി");
        m.put("aa.notify.officer.hearing_adjourned", "അപ്പീൽ {appealNumber}-ന്റെ വാദം കേൾക്കൽ മാറ്റിവച്ചു");
        m.put("aa.notify.officer.hearing_outcome_recorded",
              "അപ്പീൽ {appealNumber}-ന്റെ വാദം കേൾക്കലിന്റെ ഫലം {outcome} എന്ന് രേഖപ്പെടുത്തി");
        m.put("aa.notify.officer.order_passed", "അപ്പീൽ {appealNumber}-ൽ {orderDate}-ന് ഉത്തരവ് പുറപ്പെടുവിച്ചു");
        m.put("aa.notify.officer.order_corrected", "അപ്പീൽ {appealNumber}-ലെ ഉത്തരവ് തിരുത്തി");
        m.put("aa.notify.officer.appeal_dismissed", "അപ്പീൽ {appealNumber} യാതൊരു ആശ്വാസവുമില്ലാതെ അവസാനിപ്പിച്ചു");
        m.put("aa.notify.officer.appeal_remanded", "അപ്പീൽ {appealNumber} പുതുതായി പരിഗണിക്കാൻ തിരികെ അയച്ചു");
        m.put("aa.notify.officer.forwarded_to_authority", "അപ്പീൽ {appealNumber} അപ്പീൽ അധികാരിക്ക് കൈമാറി");
        m.put("aa.notify.officer.sent_back_registrar", "അപ്പീൽ {appealNumber} രജിസ്ട്രാർക്ക് തിരികെ അയച്ചു");
        m.put("aa.notify.officer.reassigned", "അപ്പീൽ {appealNumber} നിങ്ങൾക്ക് വീണ്ടും നൽകി");
        m.put("aa.notify.officer.escalated_to_tier2", "അപ്പീൽ {appealNumber} രണ്ടാം തലത്തിലേക്ക് അയച്ചു");
        m.put("aa.notify.officer.hearing_reminder",
              "ഓർമ്മപ്പെടുത്തൽ: അപ്പീൽ {appealNumber}-നായുള്ള വാദം കേൾക്കൽ {hearingDate}-ന് ആണ്");
        m.put("aa.notify.officer.sla_reminder", "അപ്പീൽ {appealNumber}-ന്റെ നിശ്ചിത തീയതി അടുക്കുന്നു");
        m.put("aa.hearing.scheduled_notices_recorded", "വാദം കേൾക്കൽ നിശ്ചയിച്ചു. അറിയിപ്പുകൾ അയയ്ക്കാനായി രേഖപ്പെടുത്തി.");
        m.put("aa.hearing.rescheduled_notices_recorded", "വാദം കേൾക്കൽ വീണ്ടും നിശ്ചയിച്ചു. അറിയിപ്പുകൾ അയയ്ക്കാനായി രേഖപ്പെടുത്തി.");
        m.put("aa.hearing.adjourned", "വാദം കേൾക്കൽ മാറ്റിവച്ചു");
        m.put("aa.hearing.outcome_recorded", "വാദം കേൾക്കലിന്റെ ഫലം രേഖപ്പെടുത്തി");
        m.put("aa.hearing.error_officer_double_booked",
              "തിരഞ്ഞെടുത്ത ഓഫീസർക്ക് ഈ സമയത്ത് ഇതിനകം ഒരു വാദം കേൾക്കൽ നിശ്ചയിച്ചിട്ടുണ്ട്");
        m.put("aa.hearing.error_invalid_request", "വാദം കേൾക്കലിന്റെ വിവരങ്ങൾ അപൂർണ്ണമോ അസാധുവോ ആണ്");
        m.put("aa.hearing.error_invalid_state",
              "ഈ അപ്പീൽ ഈ വാദം കേൾക്കൽ നടപടി അനുവദിക്കുന്ന അവസ്ഥയിൽ അല്ല");
        m.put("aa.hearing.error_no_scheduled_hearing", "ഈ അപ്പീലിനായി വാദം കേൾക്കൽ ഒന്നും നിശ്ചയിച്ചിട്ടില്ല");
        m.put("aa.order.issued", "ഉത്തരവ് പുറപ്പെടുവിച്ചു");
        m.put("aa.order.corrected", "ഉത്തരവിന്റെ തിരുത്തൽ രേഖപ്പെടുത്തി");
        m.put("aa.order.error_already_issued", "ഈ അപ്പീലിൽ ഇതിനകം ഒരു ഉത്തരവ് പുറപ്പെടുവിച്ചിട്ടുണ്ട്");
        m.put("aa.order.error_ed_approval_required",
              "ഈ ഉത്തരവ് പുറപ്പെടുവിക്കുന്നതിന് മുമ്പ് എക്സിക്യൂട്ടീവ് ഡയറക്ടറുടെ അനുമതി ആവശ്യമാണ്");
        m.put("aa.order.error_invalid_request", "ഉത്തരവിന്റെ വിവരങ്ങൾ അപൂർണ്ണമോ അസാധുവോ ആണ്");
        m.put("aa.order.error_no_order_to_correct", "ഈ അപ്പീലിൽ തിരുത്താൻ പുറപ്പെടുവിച്ച ഉത്തരവ് ഇല്ല");
        m.put("aa.hearing.history_title", "വാദം കേൾക്കൽ ചരിത്രം");
        m.put("aa.hearing.event_scheduled", "നിശ്ചയിച്ചു");
        m.put("aa.hearing.event_rescheduled", "വീണ്ടും നിശ്ചയിച്ചു");
        m.put("aa.hearing.event_adjourned", "മാറ്റിവച്ചു");
        m.put("aa.hearing.event_completed", "പൂർത്തിയായി");
        m.put("aa.hearing.event_cancelled", "റദ്ദാക്കി");
        m.put("aa.hearing.col_date", "തീയതി");
        m.put("aa.hearing.col_venue", "സ്ഥലം");
        m.put("aa.hearing.col_mode", "രീതി");
        m.put("aa.hearing.col_outcome", "ഫലം");
        m.put("aa.hearing.col_reason", "കാരണം");
        m.put("aa.hearing.mode_in_person", "നേരിട്ടുള്ള ഹാജർ");
        m.put("aa.hearing.mode_video", "വീഡിയോ വഴി");
        m.put("aa.hearing.mode_hybrid", "സമ്മിശ്രം");
        m.put("aa.notice.status_pending", "തീർപ്പാകാത്ത");
        m.put("aa.notice.status_sent", "അയച്ചു");
        m.put("aa.notice.status_failed", "പരാജയപ്പെട്ടു");
        m.put("aa.notice.status_cancelled", "റദ്ദാക്കി");
        m.put("aa.notice.channel_email", "ഇ-മെയിൽ");
        m.put("aa.notice.channel_sms", "എസ്എംഎസ്");
        m.put("aa.notice.channel_none", "മാർഗ്ഗം ഒന്നുമില്ല");
        m.put("aa.notice.recipient_appellant", "അപ്പീൽ നൽകിയ ആൾ");
        m.put("aa.notice.recipient_respondent", "എതിർകക്ഷി");
        m.put("aa.notice.recipient_ombudsman", "ഓംബുഡ്സ്മാൻ");
        m.put("aa.notice.queued_not_sent", "അറിയിപ്പ് രേഖപ്പെടുത്തി — അയയ്ക്കാൻ കാത്തിരിക്കുന്നു");
        m.put("aa.order.revision_label", "പരിഷ്കരണം");
        m.put("aa.order.correction_reason", "തിരുത്തലിന്റെ കാരണം");
        m.put("aa.order.issuing_authority", "ഉത്തരവ് പുറപ്പെടുവിച്ച അധികാരി");
        m.put("aa.order.clause_relied_on", "ആശ്രയിച്ച വ്യവസ്ഥ: {clauseCode}");
        m.put("aa.order.no_pdf_phase1", "ഈ പുറത്തിറക്കലിൽ ഉത്തരവിന്റെ ഡൗൺലോഡ് ചെയ്യാവുന്ന പിഡിഎഫ് ലഭ്യമല്ല");
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
