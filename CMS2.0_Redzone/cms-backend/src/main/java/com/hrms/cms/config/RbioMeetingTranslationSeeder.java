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
 * Translations for S5's conciliation meetings (UST496-503, 643-651).
 *
 * <p>A SEPARATE seeder at its own {@code @Order}, per the convention stated at
 * {@code AaRegisterTranslationSeeder}: a shared seeder would be a guaranteed merge conflict in a file where a
 * conflict silently costs a locale.
 *
 * <p><b>Every key is namespaced {@code rbio.meeting.*}.</b> Keys are idempotent by {@code existsByCode}, so if
 * two sessions choose the same code the first text silently wins and the second never appears.
 *
 * <p><b>Insert-if-absent.</b> Correcting a default here does NOT fix rows already in the database — a text
 * correction needs a code-scoped UPDATE in both migration directories, scoped BY KEY CODE and never by an
 * English phrase, because the localized rows are in native scripts and an English substring matches none.
 *
 * <p>The error keys mirror the messages {@code RbioMeetingService} returns, so the same refusal is
 * translatable whether it came from the service's own validation or from the transition table's backstop.
 */
@Component
@Order(43)
public class RbioMeetingTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "rbio";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public RbioMeetingTranslationSeeder(TranslationKeyRepository keyRepo,
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

        // ── Screen and field labels ──
        m.put("rbio.meeting.title", "Conciliation Meeting");
        m.put("rbio.meeting.status", "Meeting Status");
        m.put("rbio.meeting.status.scheduled", "Scheduled");
        m.put("rbio.meeting.status.rescheduled", "Rescheduled");
        m.put("rbio.meeting.status.completed", "Complete");
        m.put("rbio.meeting.date", "Meeting Date");
        m.put("rbio.meeting.time", "Meeting Time");
        m.put("rbio.meeting.venue", "Venue");
        m.put("rbio.meeting.mode", "Mode");
        m.put("rbio.meeting.participants", "Participants");
        m.put("rbio.meeting.participants.entity", "Entity");
        m.put("rbio.meeting.participants.complainant", "Complainant");
        m.put("rbio.meeting.participants.both", "Both");
        m.put("rbio.meeting.reschedule_reason", "Reason for Rescheduling");
        m.put("rbio.meeting.entity_accepted", "Entity Acceptance");
        m.put("rbio.meeting.entity_accepted.yes", "Yes");
        m.put("rbio.meeting.entity_accepted.no", "No");
        m.put("rbio.meeting.minutes", "Minutes of Meeting");
        m.put("rbio.meeting.history", "Meeting History");
        m.put("rbio.meeting.history.superseded", "Superseded");
        m.put("rbio.meeting.history.current", "Current meeting");
        m.put("rbio.meeting.no_meeting", "No meeting has been scheduled for this complaint.");

        // ── Actions ──
        m.put("rbio.meeting.action.schedule", "Schedule Meeting");
        m.put("rbio.meeting.action.reschedule", "Reschedule Meeting");
        m.put("rbio.meeting.action.complete", "Record Minutes");
        m.put("rbio.meeting.action.download_mom", "Download MOM Letter");
        m.put("rbio.meeting.action.upload_signed", "Upload Signed MOM");
        m.put("rbio.meeting.action.add_participant", "Add Participant");

        // ── Participants ──
        m.put("rbio.meeting.participant.confirmed", "Attendance confirmed");
        m.put("rbio.meeting.participant.invited", "Invited");
        m.put("rbio.meeting.participant.cap_reached",
                "A complaint may have at most six additional entity participants.");

        // ── Success ──
        m.put("rbio.meeting.saved.scheduled", "Meeting scheduled.");
        m.put("rbio.meeting.saved.rescheduled",
                "Meeting rescheduled. The previous meeting details are retained in the history.");
        m.put("rbio.meeting.saved.completed", "Minutes of meeting recorded.");
        m.put("rbio.meeting.saved.uploaded", "Signed minutes uploaded.");

        // ── Refusals. These mirror RbioMeetingService's messages exactly. ──
        m.put("rbio.meeting.error.date_required", "A meeting date is required.");
        m.put("rbio.meeting.error.date_invalid", "The meeting date must be a valid date.");
        m.put("rbio.meeting.error.time_required", "A meeting time is required.");
        m.put("rbio.meeting.error.time_invalid", "The meeting time must be a valid time (HH:mm).");
        m.put("rbio.meeting.error.participants_required",
                "Select the participants: Entity, Complainant or Both.");
        m.put("rbio.meeting.error.participants_invalid",
                "Participants must be Entity, Complainant or Both.");
        m.put("rbio.meeting.error.reason_required", "A reason is required to reschedule a meeting.");
        m.put("rbio.meeting.error.reason_too_long", "The reason for rescheduling is too long.");
        m.put("rbio.meeting.error.acceptance_required",
                "Record whether the entity accepted the settlement discussed.");
        m.put("rbio.meeting.error.acceptance_invalid", "Entity acceptance must be Yes or No.");
        m.put("rbio.meeting.error.minutes_required", "The minutes of the meeting are required.");
        m.put("rbio.meeting.error.minutes_too_long", "The minutes of the meeting are too long.");
        m.put("rbio.meeting.error.status_excluded",
                "A meeting cannot be scheduled for a complaint at this stage.");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "Meeting eligibility could not be checked. Please retry.");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "There is no scheduled meeting on this complaint to reschedule.");
        m.put("rbio.meeting.error.nothing_to_complete",
                "There is no scheduled meeting on this complaint to record minutes against.");
        m.put("rbio.meeting.error.no_completed_meeting",
                "No completed meeting exists, so there are no minutes to issue.");
        m.put("rbio.meeting.error.template_missing",
                "The Minutes of Meeting template is not configured. Please retry.");
        m.put("rbio.meeting.error.unavailable",
                "The meeting could not be recorded. Please retry.");
        m.put("rbio.meeting.error.participant_name_required", "A participant name is required.");
        m.put("rbio.meeting.error.upload_failed",
                "The signed minutes could not be uploaded. Please retry.");

        // Outcome-form labels. Owned HERE rather than borrowed from rbio.ladder.* / rbio.legal.*: those keys
        // were checked against the live TRANSLATION_KEYS table and do not exist, so interpolating them would
        // render the raw key to staff — the exact defect already found on the RBIO detail tab strip.
        m.put("rbio.meeting.outcome.title", "Record Conciliation Outcome");
        m.put("rbio.meeting.outcome.settled", "Settled");
        m.put("rbio.meeting.outcome.failed", "Failed");
        m.put("rbio.meeting.outcome.not_started", "Not started");
        m.put("rbio.meeting.outcome.in_progress", "In progress");
        m.put("rbio.meeting.outcome.compensation_type", "Compensation Type");
        m.put("rbio.meeting.outcome.compensation_amount", "Compensation Amount (INR)");
        m.put("rbio.meeting.outcome.summary", "Settlement Summary");
        m.put("rbio.meeting.outcome.failure_reason", "Reason for Failure");
        m.put("rbio.meeting.action.cancel", "Cancel");
        m.put("rbio.meeting.action.save", "Save and Proceed");
        m.put("rbio.meeting.participant.email", "Email");
        m.put("rbio.meeting.participant.none", "No participants recorded yet.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "सुलह बैठक");
        m.put("rbio.meeting.status", "बैठक की स्थिति");
        m.put("rbio.meeting.status.scheduled", "निर्धारित");
        m.put("rbio.meeting.status.rescheduled", "पुनर्निर्धारित");
        m.put("rbio.meeting.status.completed", "पूर्ण");
        m.put("rbio.meeting.date", "बैठक की तिथि");
        m.put("rbio.meeting.time", "बैठक का समय");
        m.put("rbio.meeting.venue", "स्थान");
        m.put("rbio.meeting.mode", "माध्यम");
        m.put("rbio.meeting.participants", "प्रतिभागी");
        m.put("rbio.meeting.participants.entity", "संस्था");
        m.put("rbio.meeting.participants.complainant", "शिकायतकर्ता");
        m.put("rbio.meeting.participants.both", "दोनों");
        m.put("rbio.meeting.reschedule_reason", "पुनर्निर्धारण का कारण");
        m.put("rbio.meeting.entity_accepted", "संस्था की स्वीकृति");
        m.put("rbio.meeting.entity_accepted.yes", "हाँ");
        m.put("rbio.meeting.entity_accepted.no", "नहीं");
        m.put("rbio.meeting.minutes", "बैठक का कार्यवृत्त");
        m.put("rbio.meeting.history", "बैठक इतिहास");
        m.put("rbio.meeting.history.superseded", "अधिक्रमित");
        m.put("rbio.meeting.history.current", "वर्तमान बैठक");
        m.put("rbio.meeting.no_meeting", "इस शिकायत के लिए कोई बैठक निर्धारित नहीं की गई है।");
        m.put("rbio.meeting.action.schedule", "बैठक निर्धारित करें");
        m.put("rbio.meeting.action.reschedule", "बैठक पुनर्निर्धारित करें");
        m.put("rbio.meeting.action.complete", "कार्यवृत्त दर्ज करें");
        m.put("rbio.meeting.action.download_mom", "कार्यवृत्त पत्र डाउनलोड करें");
        m.put("rbio.meeting.action.upload_signed", "हस्ताक्षरित कार्यवृत्त अपलोड करें");
        m.put("rbio.meeting.action.add_participant", "प्रतिभागी जोड़ें");
        m.put("rbio.meeting.participant.confirmed", "उपस्थिति की पुष्टि");
        m.put("rbio.meeting.participant.invited", "आमंत्रित");
        m.put("rbio.meeting.participant.cap_reached",
                "एक शिकायत में अधिकतम छह अतिरिक्त संस्था प्रतिभागी हो सकते हैं।");
        m.put("rbio.meeting.saved.scheduled", "बैठक निर्धारित की गई।");
        m.put("rbio.meeting.saved.rescheduled",
                "बैठक पुनर्निर्धारित की गई। पिछली बैठक का विवरण इतिहास में सुरक्षित है।");
        m.put("rbio.meeting.saved.completed", "बैठक का कार्यवृत्त दर्ज किया गया।");
        m.put("rbio.meeting.saved.uploaded", "हस्ताक्षरित कार्यवृत्त अपलोड किया गया।");
        m.put("rbio.meeting.error.date_required", "बैठक की तिथि आवश्यक है।");
        m.put("rbio.meeting.error.date_invalid", "बैठक की तिथि वैध होनी चाहिए।");
        m.put("rbio.meeting.error.time_required", "बैठक का समय आवश्यक है।");
        m.put("rbio.meeting.error.time_invalid", "बैठक का समय वैध होना चाहिए (HH:mm)।");
        m.put("rbio.meeting.error.participants_required",
                "प्रतिभागी चुनें: संस्था, शिकायतकर्ता या दोनों।");
        m.put("rbio.meeting.error.participants_invalid",
                "प्रतिभागी संस्था, शिकायतकर्ता या दोनों होने चाहिए।");
        m.put("rbio.meeting.error.reason_required", "बैठक पुनर्निर्धारित करने के लिए कारण आवश्यक है।");
        m.put("rbio.meeting.error.reason_too_long", "पुनर्निर्धारण का कारण बहुत लंबा है।");
        m.put("rbio.meeting.error.acceptance_required",
                "दर्ज करें कि संस्था ने चर्चा किए गए समाधान को स्वीकार किया या नहीं।");
        m.put("rbio.meeting.error.acceptance_invalid", "संस्था की स्वीकृति हाँ या नहीं होनी चाहिए।");
        m.put("rbio.meeting.error.minutes_required", "बैठक का कार्यवृत्त आवश्यक है।");
        m.put("rbio.meeting.error.minutes_too_long", "बैठक का कार्यवृत्त बहुत लंबा है।");
        m.put("rbio.meeting.error.status_excluded",
                "इस चरण पर शिकायत के लिए बैठक निर्धारित नहीं की जा सकती।");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "बैठक की पात्रता जाँची नहीं जा सकी। कृपया पुनः प्रयास करें।");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "इस शिकायत पर पुनर्निर्धारित करने के लिए कोई निर्धारित बैठक नहीं है।");
        m.put("rbio.meeting.error.nothing_to_complete",
                "इस शिकायत पर कार्यवृत्त दर्ज करने के लिए कोई निर्धारित बैठक नहीं है।");
        m.put("rbio.meeting.error.no_completed_meeting",
                "कोई पूर्ण बैठक नहीं है, इसलिए जारी करने के लिए कोई कार्यवृत्त नहीं है।");
        m.put("rbio.meeting.error.template_missing",
                "कार्यवृत्त पत्र का प्रारूप कॉन्फ़िगर नहीं है। कृपया पुनः प्रयास करें।");
        m.put("rbio.meeting.error.unavailable", "बैठक दर्ज नहीं की जा सकी। कृपया पुनः प्रयास करें।");
        m.put("rbio.meeting.error.participant_name_required", "प्रतिभागी का नाम आवश्यक है।");
        m.put("rbio.meeting.error.upload_failed",
                "हस्ताक्षरित कार्यवृत्त अपलोड नहीं किया जा सका। कृपया पुनः प्रयास करें।");
        m.put("rbio.meeting.outcome.title", "सुलह परिणाम दर्ज करें");
        m.put("rbio.meeting.outcome.settled", "निपटाया गया");
        m.put("rbio.meeting.outcome.failed", "विफल");
        m.put("rbio.meeting.outcome.not_started", "प्रारंभ नहीं");
        m.put("rbio.meeting.outcome.in_progress", "प्रगति पर");
        m.put("rbio.meeting.outcome.compensation_type", "मुआवजे का प्रकार");
        m.put("rbio.meeting.outcome.compensation_amount", "मुआवजा राशि (रुपये)");
        m.put("rbio.meeting.outcome.summary", "समाधान सारांश");
        m.put("rbio.meeting.outcome.failure_reason", "विफलता का कारण");
        m.put("rbio.meeting.action.cancel", "रद्द करें");
        m.put("rbio.meeting.action.save", "सहेजें और आगे बढ़ें");
        m.put("rbio.meeting.participant.email", "ईमेल");
        m.put("rbio.meeting.participant.none", "अभी कोई प्रतिभागी दर्ज नहीं।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "सलोखा बैठक");
        m.put("rbio.meeting.status", "बैठकीची स्थिती");
        m.put("rbio.meeting.status.scheduled", "नियोजित");
        m.put("rbio.meeting.status.rescheduled", "पुनर्नियोजित");
        m.put("rbio.meeting.status.completed", "पूर्ण");
        m.put("rbio.meeting.date", "बैठकीची तारीख");
        m.put("rbio.meeting.time", "बैठकीची वेळ");
        m.put("rbio.meeting.venue", "ठिकाण");
        m.put("rbio.meeting.mode", "माध्यम");
        m.put("rbio.meeting.participants", "सहभागी");
        m.put("rbio.meeting.participants.entity", "संस्था");
        m.put("rbio.meeting.participants.complainant", "तक्रारदार");
        m.put("rbio.meeting.participants.both", "दोन्ही");
        m.put("rbio.meeting.reschedule_reason", "पुनर्नियोजनाचे कारण");
        m.put("rbio.meeting.entity_accepted", "संस्थेची स्वीकृती");
        m.put("rbio.meeting.entity_accepted.yes", "होय");
        m.put("rbio.meeting.entity_accepted.no", "नाही");
        m.put("rbio.meeting.minutes", "बैठकीचा कार्यवृत्तांत");
        m.put("rbio.meeting.history", "बैठक इतिहास");
        m.put("rbio.meeting.history.superseded", "अधिक्रमित");
        m.put("rbio.meeting.history.current", "चालू बैठक");
        m.put("rbio.meeting.no_meeting", "या तक्रारीसाठी कोणतीही बैठक नियोजित केलेली नाही.");
        m.put("rbio.meeting.action.schedule", "बैठक नियोजित करा");
        m.put("rbio.meeting.action.reschedule", "बैठक पुनर्नियोजित करा");
        m.put("rbio.meeting.action.complete", "कार्यवृत्तांत नोंदवा");
        m.put("rbio.meeting.action.download_mom", "कार्यवृत्तांत पत्र डाउनलोड करा");
        m.put("rbio.meeting.action.upload_signed", "स्वाक्षरीत कार्यवृत्तांत अपलोड करा");
        m.put("rbio.meeting.action.add_participant", "सहभागी जोडा");
        m.put("rbio.meeting.participant.confirmed", "उपस्थिती निश्चित");
        m.put("rbio.meeting.participant.invited", "आमंत्रित");
        m.put("rbio.meeting.participant.cap_reached",
                "एका तक्रारीत जास्तीत जास्त सहा अतिरिक्त संस्था सहभागी असू शकतात.");
        m.put("rbio.meeting.saved.scheduled", "बैठक नियोजित केली.");
        m.put("rbio.meeting.saved.rescheduled",
                "बैठक पुनर्नियोजित केली. मागील बैठकीचा तपशील इतिहासात जतन आहे.");
        m.put("rbio.meeting.saved.completed", "बैठकीचा कार्यवृत्तांत नोंदवला.");
        m.put("rbio.meeting.saved.uploaded", "स्वाक्षरीत कार्यवृत्तांत अपलोड केला.");
        m.put("rbio.meeting.error.date_required", "बैठकीची तारीख आवश्यक आहे.");
        m.put("rbio.meeting.error.date_invalid", "बैठकीची तारीख वैध असावी.");
        m.put("rbio.meeting.error.time_required", "बैठकीची वेळ आवश्यक आहे.");
        m.put("rbio.meeting.error.time_invalid", "बैठकीची वेळ वैध असावी (HH:mm).");
        m.put("rbio.meeting.error.participants_required",
                "सहभागी निवडा: संस्था, तक्रारदार किंवा दोन्ही.");
        m.put("rbio.meeting.error.participants_invalid",
                "सहभागी संस्था, तक्रारदार किंवा दोन्ही असावे.");
        m.put("rbio.meeting.error.reason_required", "बैठक पुनर्नियोजित करण्यासाठी कारण आवश्यक आहे.");
        m.put("rbio.meeting.error.reason_too_long", "पुनर्नियोजनाचे कारण खूप लांब आहे.");
        m.put("rbio.meeting.error.acceptance_required",
                "संस्थेने चर्चा केलेला तोडगा स्वीकारला की नाही ते नोंदवा.");
        m.put("rbio.meeting.error.acceptance_invalid", "संस्थेची स्वीकृती होय किंवा नाही असावी.");
        m.put("rbio.meeting.error.minutes_required", "बैठकीचा कार्यवृत्तांत आवश्यक आहे.");
        m.put("rbio.meeting.error.minutes_too_long", "बैठकीचा कार्यवृत्तांत खूप लांब आहे.");
        m.put("rbio.meeting.error.status_excluded",
                "या टप्प्यावर तक्रारीसाठी बैठक नियोजित करता येत नाही.");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "बैठकीची पात्रता तपासता आली नाही. कृपया पुन्हा प्रयत्न करा.");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "या तक्रारीवर पुनर्नियोजित करण्यासाठी कोणतीही नियोजित बैठक नाही.");
        m.put("rbio.meeting.error.nothing_to_complete",
                "या तक्रारीवर कार्यवृत्तांत नोंदवण्यासाठी कोणतीही नियोजित बैठक नाही.");
        m.put("rbio.meeting.error.no_completed_meeting",
                "कोणतीही पूर्ण बैठक नाही, म्हणून जारी करण्यासाठी कार्यवृत्तांत नाही.");
        m.put("rbio.meeting.error.template_missing",
                "कार्यवृत्तांत पत्राचा नमुना संरचित नाही. कृपया पुन्हा प्रयत्न करा.");
        m.put("rbio.meeting.error.unavailable", "बैठक नोंदवता आली नाही. कृपया पुन्हा प्रयत्न करा.");
        m.put("rbio.meeting.error.participant_name_required", "सहभागीचे नाव आवश्यक आहे.");
        m.put("rbio.meeting.error.upload_failed",
                "स्वाक्षरीत कार्यवृत्तांत अपलोड करता आला नाही. कृपया पुन्हा प्रयत्न करा.");
        m.put("rbio.meeting.outcome.title", "सलोखा निकाल नोंदवा");
        m.put("rbio.meeting.outcome.settled", "निपटवले");
        m.put("rbio.meeting.outcome.failed", "अयशस्वी");
        m.put("rbio.meeting.outcome.not_started", "सुरू नाही");
        m.put("rbio.meeting.outcome.in_progress", "प्रगतीपथावर");
        m.put("rbio.meeting.outcome.compensation_type", "भरपाईचा प्रकार");
        m.put("rbio.meeting.outcome.compensation_amount", "भरपाई रक्कम (रुपये)");
        m.put("rbio.meeting.outcome.summary", "तोडगा सारांश");
        m.put("rbio.meeting.outcome.failure_reason", "अपयशाचे कारण");
        m.put("rbio.meeting.action.cancel", "रद्द करा");
        m.put("rbio.meeting.action.save", "जतन करा आणि पुढे जा");
        m.put("rbio.meeting.participant.email", "ईमेल");
        m.put("rbio.meeting.participant.none", "अद्याप कोणतेही सहभागी नोंदवलेले नाहीत.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "মধ্যস্থতা বৈঠক");
        m.put("rbio.meeting.status", "বৈঠকের অবস্থা");
        m.put("rbio.meeting.status.scheduled", "নির্ধারিত");
        m.put("rbio.meeting.status.rescheduled", "পুনর্নির্ধারিত");
        m.put("rbio.meeting.status.completed", "সম্পন্ন");
        m.put("rbio.meeting.date", "বৈঠকের তারিখ");
        m.put("rbio.meeting.time", "বৈঠকের সময়");
        m.put("rbio.meeting.venue", "স্থান");
        m.put("rbio.meeting.mode", "মাধ্যম");
        m.put("rbio.meeting.participants", "অংশগ্রহণকারী");
        m.put("rbio.meeting.participants.entity", "সংস্থা");
        m.put("rbio.meeting.participants.complainant", "অভিযোগকারী");
        m.put("rbio.meeting.participants.both", "উভয়");
        m.put("rbio.meeting.reschedule_reason", "পুনর্নির্ধারণের কারণ");
        m.put("rbio.meeting.entity_accepted", "সংস্থার সম্মতি");
        m.put("rbio.meeting.entity_accepted.yes", "হ্যাঁ");
        m.put("rbio.meeting.entity_accepted.no", "না");
        m.put("rbio.meeting.minutes", "বৈঠকের কার্যবিবরণী");
        m.put("rbio.meeting.history", "বৈঠকের ইতিহাস");
        m.put("rbio.meeting.history.superseded", "প্রতিস্থাপিত");
        m.put("rbio.meeting.history.current", "বর্তমান বৈঠক");
        m.put("rbio.meeting.no_meeting", "এই অভিযোগের জন্য কোনো বৈঠক নির্ধারিত হয়নি।");
        m.put("rbio.meeting.action.schedule", "বৈঠক নির্ধারণ করুন");
        m.put("rbio.meeting.action.reschedule", "বৈঠক পুনর্নির্ধারণ করুন");
        m.put("rbio.meeting.action.complete", "কার্যবিবরণী নথিভুক্ত করুন");
        m.put("rbio.meeting.action.download_mom", "কার্যবিবরণী পত্র ডাউনলোড করুন");
        m.put("rbio.meeting.action.upload_signed", "স্বাক্ষরিত কার্যবিবরণী আপলোড করুন");
        m.put("rbio.meeting.action.add_participant", "অংশগ্রহণকারী যোগ করুন");
        m.put("rbio.meeting.participant.confirmed", "উপস্থিতি নিশ্চিত");
        m.put("rbio.meeting.participant.invited", "আমন্ত্রিত");
        m.put("rbio.meeting.participant.cap_reached",
                "একটি অভিযোগে সর্বাধিক ছয়টি অতিরিক্ত সংস্থা অংশগ্রহণকারী থাকতে পারে।");
        m.put("rbio.meeting.saved.scheduled", "বৈঠক নির্ধারিত হয়েছে।");
        m.put("rbio.meeting.saved.rescheduled",
                "বৈঠক পুনর্নির্ধারিত হয়েছে। পূর্ববর্তী বৈঠকের বিবরণ ইতিহাসে সংরক্ষিত আছে।");
        m.put("rbio.meeting.saved.completed", "বৈঠকের কার্যবিবরণী নথিভুক্ত হয়েছে।");
        m.put("rbio.meeting.saved.uploaded", "স্বাক্ষরিত কার্যবিবরণী আপলোড হয়েছে।");
        m.put("rbio.meeting.error.date_required", "বৈঠকের তারিখ আবশ্যক।");
        m.put("rbio.meeting.error.date_invalid", "বৈঠকের তারিখ বৈধ হতে হবে।");
        m.put("rbio.meeting.error.time_required", "বৈঠকের সময় আবশ্যক।");
        m.put("rbio.meeting.error.time_invalid", "বৈঠকের সময় বৈধ হতে হবে (HH:mm)।");
        m.put("rbio.meeting.error.participants_required",
                "অংশগ্রহণকারী নির্বাচন করুন: সংস্থা, অভিযোগকারী বা উভয়।");
        m.put("rbio.meeting.error.participants_invalid",
                "অংশগ্রহণকারী সংস্থা, অভিযোগকারী বা উভয় হতে হবে।");
        m.put("rbio.meeting.error.reason_required", "বৈঠক পুনর্নির্ধারণের জন্য কারণ আবশ্যক।");
        m.put("rbio.meeting.error.reason_too_long", "পুনর্নির্ধারণের কারণ অত্যন্ত দীর্ঘ।");
        m.put("rbio.meeting.error.acceptance_required",
                "সংস্থা আলোচিত নিষ্পত্তি গ্রহণ করেছে কিনা নথিভুক্ত করুন।");
        m.put("rbio.meeting.error.acceptance_invalid", "সংস্থার সম্মতি হ্যাঁ বা না হতে হবে।");
        m.put("rbio.meeting.error.minutes_required", "বৈঠকের কার্যবিবরণী আবশ্যক।");
        m.put("rbio.meeting.error.minutes_too_long", "বৈঠকের কার্যবিবরণী অত্যন্ত দীর্ঘ।");
        m.put("rbio.meeting.error.status_excluded",
                "এই পর্যায়ে অভিযোগের জন্য বৈঠক নির্ধারণ করা যাবে না।");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "বৈঠকের যোগ্যতা যাচাই করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "এই অভিযোগে পুনর্নির্ধারণের জন্য কোনো নির্ধারিত বৈঠক নেই।");
        m.put("rbio.meeting.error.nothing_to_complete",
                "এই অভিযোগে কার্যবিবরণী নথিভুক্ত করার জন্য কোনো নির্ধারিত বৈঠক নেই।");
        m.put("rbio.meeting.error.no_completed_meeting",
                "কোনো সম্পন্ন বৈঠক নেই, তাই জারি করার জন্য কোনো কার্যবিবরণী নেই।");
        m.put("rbio.meeting.error.template_missing",
                "কার্যবিবরণী পত্রের নমুনা কনফিগার করা নেই। অনুগ্রহ করে পুনরায় চেষ্টা করুন।");
        m.put("rbio.meeting.error.unavailable",
                "বৈঠক নথিভুক্ত করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।");
        m.put("rbio.meeting.error.participant_name_required", "অংশগ্রহণকারীর নাম আবশ্যক।");
        m.put("rbio.meeting.error.upload_failed",
                "স্বাক্ষরিত কার্যবিবরণী আপলোড করা যায়নি। অনুগ্রহ করে পুনরায় চেষ্টা করুন।");
        m.put("rbio.meeting.outcome.title", "মধ্যস্থতার ফলাফল নথিভুক্ত করুন");
        m.put("rbio.meeting.outcome.settled", "নিষ্পত্তি হয়েছে");
        m.put("rbio.meeting.outcome.failed", "ব্যর্থ");
        m.put("rbio.meeting.outcome.not_started", "শুরু হয়নি");
        m.put("rbio.meeting.outcome.in_progress", "চলমান");
        m.put("rbio.meeting.outcome.compensation_type", "ক্ষতিপূরণের ধরন");
        m.put("rbio.meeting.outcome.compensation_amount", "ক্ষতিপূরণের পরিমাণ (টাকা)");
        m.put("rbio.meeting.outcome.summary", "নিষ্পত্তির সারসংক্ষেপ");
        m.put("rbio.meeting.outcome.failure_reason", "ব্যর্থতার কারণ");
        m.put("rbio.meeting.action.cancel", "বাতিল করুন");
        m.put("rbio.meeting.action.save", "সংরক্ষণ করে এগিয়ে যান");
        m.put("rbio.meeting.participant.email", "ইমেল");
        m.put("rbio.meeting.participant.none", "এখনও কোনো অংশগ্রহণকারী নথিভুক্ত হয়নি।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "సయోధ్య సమావేశం");
        m.put("rbio.meeting.status", "సమావేశ స్థితి");
        m.put("rbio.meeting.status.scheduled", "నిర్ణయించబడింది");
        m.put("rbio.meeting.status.rescheduled", "పునర్నిర్ణయించబడింది");
        m.put("rbio.meeting.status.completed", "పూర్తయింది");
        m.put("rbio.meeting.date", "సమావేశ తేదీ");
        m.put("rbio.meeting.time", "సమావేశ సమయం");
        m.put("rbio.meeting.venue", "ప్రదేశం");
        m.put("rbio.meeting.mode", "విధానం");
        m.put("rbio.meeting.participants", "పాల్గొనేవారు");
        m.put("rbio.meeting.participants.entity", "సంస్థ");
        m.put("rbio.meeting.participants.complainant", "ఫిర్యాదుదారు");
        m.put("rbio.meeting.participants.both", "ఇద్దరూ");
        m.put("rbio.meeting.reschedule_reason", "పునర్నిర్ణయ కారణం");
        m.put("rbio.meeting.entity_accepted", "సంస్థ ఆమోదం");
        m.put("rbio.meeting.entity_accepted.yes", "అవును");
        m.put("rbio.meeting.entity_accepted.no", "కాదు");
        m.put("rbio.meeting.minutes", "సమావేశ నివేదిక");
        m.put("rbio.meeting.history", "సమావేశ చరిత్ర");
        m.put("rbio.meeting.history.superseded", "భర్తీ చేయబడింది");
        m.put("rbio.meeting.history.current", "ప్రస్తుత సమావేశం");
        m.put("rbio.meeting.no_meeting", "ఈ ఫిర్యాదు కోసం ఏ సమావేశం నిర్ణయించబడలేదు.");
        m.put("rbio.meeting.action.schedule", "సమావేశం నిర్ణయించండి");
        m.put("rbio.meeting.action.reschedule", "సమావేశం పునర్నిర్ణయించండి");
        m.put("rbio.meeting.action.complete", "నివేదిక నమోదు చేయండి");
        m.put("rbio.meeting.action.download_mom", "నివేదిక పత్రం డౌన్‌లోడ్ చేయండి");
        m.put("rbio.meeting.action.upload_signed", "సంతకం చేసిన నివేదిక అప్‌లోడ్ చేయండి");
        m.put("rbio.meeting.action.add_participant", "పాల్గొనేవారిని జోడించండి");
        m.put("rbio.meeting.participant.confirmed", "హాజరు నిర్ధారించబడింది");
        m.put("rbio.meeting.participant.invited", "ఆహ్వానించబడింది");
        m.put("rbio.meeting.participant.cap_reached",
                "ఒక ఫిర్యాదులో గరిష్ఠంగా ఆరు అదనపు సంస్థ పాల్గొనేవారు ఉండవచ్చు.");
        m.put("rbio.meeting.saved.scheduled", "సమావేశం నిర్ణయించబడింది.");
        m.put("rbio.meeting.saved.rescheduled",
                "సమావేశం పునర్నిర్ణయించబడింది. మునుపటి సమావేశ వివరాలు చరిత్రలో భద్రంగా ఉన్నాయి.");
        m.put("rbio.meeting.saved.completed", "సమావేశ నివేదిక నమోదు చేయబడింది.");
        m.put("rbio.meeting.saved.uploaded", "సంతకం చేసిన నివేదిక అప్‌లోడ్ చేయబడింది.");
        m.put("rbio.meeting.error.date_required", "సమావేశ తేదీ అవసరం.");
        m.put("rbio.meeting.error.date_invalid", "సమావేశ తేదీ చెల్లుబాటు కావాలి.");
        m.put("rbio.meeting.error.time_required", "సమావేశ సమయం అవసరం.");
        m.put("rbio.meeting.error.time_invalid", "సమావేశ సమయం చెల్లుబాటు కావాలి (HH:mm).");
        m.put("rbio.meeting.error.participants_required",
                "పాల్గొనేవారిని ఎంచుకోండి: సంస్థ, ఫిర్యాదుదారు లేదా ఇద్దరూ.");
        m.put("rbio.meeting.error.participants_invalid",
                "పాల్గొనేవారు సంస్థ, ఫిర్యాదుదారు లేదా ఇద్దరూ కావాలి.");
        m.put("rbio.meeting.error.reason_required", "సమావేశం పునర్నిర్ణయించడానికి కారణం అవసరం.");
        m.put("rbio.meeting.error.reason_too_long", "పునర్నిర్ణయ కారణం చాలా పొడవుగా ఉంది.");
        m.put("rbio.meeting.error.acceptance_required",
                "చర్చించిన పరిష్కారాన్ని సంస్థ ఆమోదించిందో లేదో నమోదు చేయండి.");
        m.put("rbio.meeting.error.acceptance_invalid", "సంస్థ ఆమోదం అవును లేదా కాదు కావాలి.");
        m.put("rbio.meeting.error.minutes_required", "సమావేశ నివేదిక అవసరం.");
        m.put("rbio.meeting.error.minutes_too_long", "సమావేశ నివేదిక చాలా పొడవుగా ఉంది.");
        m.put("rbio.meeting.error.status_excluded",
                "ఈ దశలో ఫిర్యాదు కోసం సమావేశం నిర్ణయించలేరు.");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "సమావేశ అర్హతను తనిఖీ చేయలేకపోయాము. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "ఈ ఫిర్యాదుపై పునర్నిర్ణయించడానికి నిర్ణయించిన సమావేశం లేదు.");
        m.put("rbio.meeting.error.nothing_to_complete",
                "ఈ ఫిర్యాదుపై నివేదిక నమోదు చేయడానికి నిర్ణయించిన సమావేశం లేదు.");
        m.put("rbio.meeting.error.no_completed_meeting",
                "పూర్తయిన సమావేశం లేదు, కాబట్టి జారీ చేయడానికి నివేదిక లేదు.");
        m.put("rbio.meeting.error.template_missing",
                "నివేదిక పత్రం మూస కాన్ఫిగర్ చేయబడలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("rbio.meeting.error.unavailable",
                "సమావేశం నమోదు చేయలేకపోయాము. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("rbio.meeting.error.participant_name_required", "పాల్గొనేవారి పేరు అవసరం.");
        m.put("rbio.meeting.error.upload_failed",
                "సంతకం చేసిన నివేదిక అప్‌లోడ్ చేయలేకపోయాము. దయచేసి మళ్లీ ప్రయత్నించండి.");
        m.put("rbio.meeting.outcome.title", "సయోధ్య ఫలితం నమోదు చేయండి");
        m.put("rbio.meeting.outcome.settled", "పరిష్కరించబడింది");
        m.put("rbio.meeting.outcome.failed", "విఫలమైంది");
        m.put("rbio.meeting.outcome.not_started", "ప్రారంభం కాలేదు");
        m.put("rbio.meeting.outcome.in_progress", "పురోగతిలో");
        m.put("rbio.meeting.outcome.compensation_type", "పరిహార రకం");
        m.put("rbio.meeting.outcome.compensation_amount", "పరిహార మొత్తం (రూ.)");
        m.put("rbio.meeting.outcome.summary", "పరిష్కార సారాంశం");
        m.put("rbio.meeting.outcome.failure_reason", "వైఫల్య కారణం");
        m.put("rbio.meeting.action.cancel", "రద్దు చేయండి");
        m.put("rbio.meeting.action.save", "సేవ్ చేసి కొనసాగించండి");
        m.put("rbio.meeting.participant.email", "ఇమెయిల్");
        m.put("rbio.meeting.participant.none", "ఇంకా పాల్గొనేవారు నమోదు కాలేదు.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "சமரச கூட்டம்");
        m.put("rbio.meeting.status", "கூட்ட நிலை");
        m.put("rbio.meeting.status.scheduled", "திட்டமிடப்பட்டது");
        m.put("rbio.meeting.status.rescheduled", "மறுதிட்டமிடப்பட்டது");
        m.put("rbio.meeting.status.completed", "நிறைவு");
        m.put("rbio.meeting.date", "கூட்ட தேதி");
        m.put("rbio.meeting.time", "கூட்ட நேரம்");
        m.put("rbio.meeting.venue", "இடம்");
        m.put("rbio.meeting.mode", "முறை");
        m.put("rbio.meeting.participants", "பங்கேற்பாளர்கள்");
        m.put("rbio.meeting.participants.entity", "நிறுவனம்");
        m.put("rbio.meeting.participants.complainant", "புகார்தாரர்");
        m.put("rbio.meeting.participants.both", "இருவரும்");
        m.put("rbio.meeting.reschedule_reason", "மறுதிட்டமிடலுக்கான காரணம்");
        m.put("rbio.meeting.entity_accepted", "நிறுவனத்தின் ஏற்பு");
        m.put("rbio.meeting.entity_accepted.yes", "ஆம்");
        m.put("rbio.meeting.entity_accepted.no", "இல்லை");
        m.put("rbio.meeting.minutes", "கூட்ட நடவடிக்கை குறிப்பு");
        m.put("rbio.meeting.history", "கூட்ட வரலாறு");
        m.put("rbio.meeting.history.superseded", "மாற்றப்பட்டது");
        m.put("rbio.meeting.history.current", "தற்போதைய கூட்டம்");
        m.put("rbio.meeting.no_meeting", "இந்தப் புகாருக்கு எந்தக் கூட்டமும் திட்டமிடப்படவில்லை.");
        m.put("rbio.meeting.action.schedule", "கூட்டத்தை திட்டமிடு");
        m.put("rbio.meeting.action.reschedule", "கூட்டத்தை மறுதிட்டமிடு");
        m.put("rbio.meeting.action.complete", "நடவடிக்கை குறிப்பை பதிவு செய்");
        m.put("rbio.meeting.action.download_mom", "நடவடிக்கை குறிப்பு கடிதத்தைப் பதிவிறக்கு");
        m.put("rbio.meeting.action.upload_signed", "கையொப்பமிட்ட குறிப்பைப் பதிவேற்று");
        m.put("rbio.meeting.action.add_participant", "பங்கேற்பாளரைச் சேர்");
        m.put("rbio.meeting.participant.confirmed", "வருகை உறுதி");
        m.put("rbio.meeting.participant.invited", "அழைக்கப்பட்டது");
        m.put("rbio.meeting.participant.cap_reached",
                "ஒரு புகாரில் அதிகபட்சம் ஆறு கூடுதல் நிறுவன பங்கேற்பாளர்கள் இருக்கலாம்.");
        m.put("rbio.meeting.saved.scheduled", "கூட்டம் திட்டமிடப்பட்டது.");
        m.put("rbio.meeting.saved.rescheduled",
                "கூட்டம் மறுதிட்டமிடப்பட்டது. முந்தைய கூட்ட விவரங்கள் வரலாற்றில் பாதுகாக்கப்பட்டுள்ளன.");
        m.put("rbio.meeting.saved.completed", "கூட்ட நடவடிக்கை குறிப்பு பதிவு செய்யப்பட்டது.");
        m.put("rbio.meeting.saved.uploaded", "கையொப்பமிட்ட குறிப்பு பதிவேற்றப்பட்டது.");
        m.put("rbio.meeting.error.date_required", "கூட்ட தேதி தேவை.");
        m.put("rbio.meeting.error.date_invalid", "கூட்ட தேதி செல்லுபடியாக இருக்க வேண்டும்.");
        m.put("rbio.meeting.error.time_required", "கூட்ட நேரம் தேவை.");
        m.put("rbio.meeting.error.time_invalid", "கூட்ட நேரம் செல்லுபடியாக இருக்க வேண்டும் (HH:mm).");
        m.put("rbio.meeting.error.participants_required",
                "பங்கேற்பாளர்களைத் தேர்ந்தெடுக்கவும்: நிறுவனம், புகார்தாரர் அல்லது இருவரும்.");
        m.put("rbio.meeting.error.participants_invalid",
                "பங்கேற்பாளர்கள் நிறுவனம், புகார்தாரர் அல்லது இருவரும் ஆக இருக்க வேண்டும்.");
        m.put("rbio.meeting.error.reason_required", "கூட்டத்தை மறுதிட்டமிட காரணம் தேவை.");
        m.put("rbio.meeting.error.reason_too_long", "மறுதிட்டமிடலுக்கான காரணம் மிக நீளமானது.");
        m.put("rbio.meeting.error.acceptance_required",
                "விவாதிக்கப்பட்ட தீர்வை நிறுவனம் ஏற்றுக்கொண்டதா என்பதைப் பதிவு செய்யவும்.");
        m.put("rbio.meeting.error.acceptance_invalid",
                "நிறுவனத்தின் ஏற்பு ஆம் அல்லது இல்லை ஆக இருக்க வேண்டும்.");
        m.put("rbio.meeting.error.minutes_required", "கூட்ட நடவடிக்கை குறிப்பு தேவை.");
        m.put("rbio.meeting.error.minutes_too_long", "கூட்ட நடவடிக்கை குறிப்பு மிக நீளமானது.");
        m.put("rbio.meeting.error.status_excluded",
                "இந்த நிலையில் புகாருக்கு கூட்டம் திட்டமிட முடியாது.");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "கூட்டத் தகுதியைச் சரிபார்க்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "இந்தப் புகாரில் மறுதிட்டமிட திட்டமிடப்பட்ட கூட்டம் இல்லை.");
        m.put("rbio.meeting.error.nothing_to_complete",
                "இந்தப் புகாரில் குறிப்பைப் பதிவு செய்ய திட்டமிடப்பட்ட கூட்டம் இல்லை.");
        m.put("rbio.meeting.error.no_completed_meeting",
                "நிறைவடைந்த கூட்டம் இல்லை, எனவே வெளியிட குறிப்பு இல்லை.");
        m.put("rbio.meeting.error.template_missing",
                "நடவடிக்கை குறிப்பு கடித வடிவம் அமைக்கப்படவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("rbio.meeting.error.unavailable", "கூட்டத்தைப் பதிவு செய்ய முடியவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("rbio.meeting.error.participant_name_required", "பங்கேற்பாளர் பெயர் தேவை.");
        m.put("rbio.meeting.error.upload_failed",
                "கையொப்பமிட்ட குறிப்பைப் பதிவேற்ற முடியவில்லை. மீண்டும் முயற்சிக்கவும்.");
        m.put("rbio.meeting.outcome.title", "சமரச முடிவைப் பதிவு செய்");
        m.put("rbio.meeting.outcome.settled", "தீர்க்கப்பட்டது");
        m.put("rbio.meeting.outcome.failed", "தோல்வி");
        m.put("rbio.meeting.outcome.not_started", "தொடங்கவில்லை");
        m.put("rbio.meeting.outcome.in_progress", "நடைபெறுகிறது");
        m.put("rbio.meeting.outcome.compensation_type", "இழப்பீட்டு வகை");
        m.put("rbio.meeting.outcome.compensation_amount", "இழப்பீட்டுத் தொகை (ரூ.)");
        m.put("rbio.meeting.outcome.summary", "தீர்வு சுருக்கம்");
        m.put("rbio.meeting.outcome.failure_reason", "தோல்விக்கான காரணம்");
        m.put("rbio.meeting.action.cancel", "ரத்து செய்");
        m.put("rbio.meeting.action.save", "சேமித்து தொடரவும்");
        m.put("rbio.meeting.participant.email", "மின்னஞ்சல்");
        m.put("rbio.meeting.participant.none", "இன்னும் பங்கேற்பாளர்கள் பதிவு செய்யப்படவில்லை.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "સમાધાન બેઠક");
        m.put("rbio.meeting.status", "બેઠકની સ્થિતિ");
        m.put("rbio.meeting.status.scheduled", "નિર્ધારિત");
        m.put("rbio.meeting.status.rescheduled", "પુનર્નિર્ધારિત");
        m.put("rbio.meeting.status.completed", "પૂર્ણ");
        m.put("rbio.meeting.date", "બેઠકની તારીખ");
        m.put("rbio.meeting.time", "બેઠકનો સમય");
        m.put("rbio.meeting.venue", "સ્થળ");
        m.put("rbio.meeting.mode", "માધ્યમ");
        m.put("rbio.meeting.participants", "સહભાગીઓ");
        m.put("rbio.meeting.participants.entity", "સંસ્થા");
        m.put("rbio.meeting.participants.complainant", "ફરિયાદી");
        m.put("rbio.meeting.participants.both", "બંને");
        m.put("rbio.meeting.reschedule_reason", "પુનર્નિર્ધારણનું કારણ");
        m.put("rbio.meeting.entity_accepted", "સંસ્થાની સ્વીકૃતિ");
        m.put("rbio.meeting.entity_accepted.yes", "હા");
        m.put("rbio.meeting.entity_accepted.no", "ના");
        m.put("rbio.meeting.minutes", "બેઠકની કાર્યનોંધ");
        m.put("rbio.meeting.history", "બેઠક ઇતિહાસ");
        m.put("rbio.meeting.history.superseded", "બદલાયેલ");
        m.put("rbio.meeting.history.current", "વર્તમાન બેઠક");
        m.put("rbio.meeting.no_meeting", "આ ફરિયાદ માટે કોઈ બેઠક નિર્ધારિત કરવામાં આવી નથી.");
        m.put("rbio.meeting.action.schedule", "બેઠક નિર્ધારિત કરો");
        m.put("rbio.meeting.action.reschedule", "બેઠક પુનર્નિર્ધારિત કરો");
        m.put("rbio.meeting.action.complete", "કાર્યનોંધ નોંધો");
        m.put("rbio.meeting.action.download_mom", "કાર્યનોંધ પત્ર ડાઉનલોડ કરો");
        m.put("rbio.meeting.action.upload_signed", "સહી કરેલ કાર્યનોંધ અપલોડ કરો");
        m.put("rbio.meeting.action.add_participant", "સહભાગી ઉમેરો");
        m.put("rbio.meeting.participant.confirmed", "હાજરી પુષ્ટિ");
        m.put("rbio.meeting.participant.invited", "આમંત્રિત");
        m.put("rbio.meeting.participant.cap_reached",
                "એક ફરિયાદમાં વધુમાં વધુ છ વધારાના સંસ્થા સહભાગીઓ હોઈ શકે છે.");
        m.put("rbio.meeting.saved.scheduled", "બેઠક નિર્ધારિત કરવામાં આવી.");
        m.put("rbio.meeting.saved.rescheduled",
                "બેઠક પુનર્નિર્ધારિત કરવામાં આવી. પહેલાંની બેઠકની વિગતો ઇતિહાસમાં સુરક્ષિત છે.");
        m.put("rbio.meeting.saved.completed", "બેઠકની કાર્યનોંધ નોંધવામાં આવી.");
        m.put("rbio.meeting.saved.uploaded", "સહી કરેલ કાર્યનોંધ અપલોડ કરવામાં આવી.");
        m.put("rbio.meeting.error.date_required", "બેઠકની તારીખ આવશ્યક છે.");
        m.put("rbio.meeting.error.date_invalid", "બેઠકની તારીખ માન્ય હોવી જોઈએ.");
        m.put("rbio.meeting.error.time_required", "બેઠકનો સમય આવશ્યક છે.");
        m.put("rbio.meeting.error.time_invalid", "બેઠકનો સમય માન્ય હોવો જોઈએ (HH:mm).");
        m.put("rbio.meeting.error.participants_required",
                "સહભાગીઓ પસંદ કરો: સંસ્થા, ફરિયાદી અથવા બંને.");
        m.put("rbio.meeting.error.participants_invalid",
                "સહભાગીઓ સંસ્થા, ફરિયાદી અથવા બંને હોવા જોઈએ.");
        m.put("rbio.meeting.error.reason_required", "બેઠક પુનર્નિર્ધારિત કરવા માટે કારણ આવશ્યક છે.");
        m.put("rbio.meeting.error.reason_too_long", "પુનર્નિર્ધારણનું કારણ ઘણું લાંબું છે.");
        m.put("rbio.meeting.error.acceptance_required",
                "સંસ્થાએ ચર્ચિત સમાધાન સ્વીકાર્યું કે નહીં તે નોંધો.");
        m.put("rbio.meeting.error.acceptance_invalid", "સંસ્થાની સ્વીકૃતિ હા અથવા ના હોવી જોઈએ.");
        m.put("rbio.meeting.error.minutes_required", "બેઠકની કાર્યનોંધ આવશ્યક છે.");
        m.put("rbio.meeting.error.minutes_too_long", "બેઠકની કાર્યનોંધ ઘણી લાંબી છે.");
        m.put("rbio.meeting.error.status_excluded",
                "આ તબક્કે ફરિયાદ માટે બેઠક નિર્ધારિત કરી શકાતી નથી.");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "બેઠકની પાત્રતા તપાસી શકાઈ નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "આ ફરિયાદ પર પુનર્નિર્ધારિત કરવા માટે કોઈ નિર્ધારિત બેઠક નથી.");
        m.put("rbio.meeting.error.nothing_to_complete",
                "આ ફરિયાદ પર કાર્યનોંધ નોંધવા માટે કોઈ નિર્ધારિત બેઠક નથી.");
        m.put("rbio.meeting.error.no_completed_meeting",
                "કોઈ પૂર્ણ બેઠક નથી, તેથી જારી કરવા માટે કોઈ કાર્યનોંધ નથી.");
        m.put("rbio.meeting.error.template_missing",
                "કાર્યનોંધ પત્રનો નમૂનો રચાયેલ નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("rbio.meeting.error.unavailable", "બેઠક નોંધી શકાઈ નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("rbio.meeting.error.participant_name_required", "સહભાગીનું નામ આવશ્યક છે.");
        m.put("rbio.meeting.error.upload_failed",
                "સહી કરેલ કાર્યનોંધ અપલોડ કરી શકાઈ નથી. કૃપા કરીને ફરી પ્રયાસ કરો.");
        m.put("rbio.meeting.outcome.title", "સમાધાન પરિણામ નોંધો");
        m.put("rbio.meeting.outcome.settled", "સમાધાન થયું");
        m.put("rbio.meeting.outcome.failed", "નિષ્ફળ");
        m.put("rbio.meeting.outcome.not_started", "શરૂ થયું નથી");
        m.put("rbio.meeting.outcome.in_progress", "પ્રગતિમાં");
        m.put("rbio.meeting.outcome.compensation_type", "વળતરનો પ્રકાર");
        m.put("rbio.meeting.outcome.compensation_amount", "વળતર રકમ (રૂ.)");
        m.put("rbio.meeting.outcome.summary", "સમાધાન સારાંશ");
        m.put("rbio.meeting.outcome.failure_reason", "નિષ્ફળતાનું કારણ");
        m.put("rbio.meeting.action.cancel", "રદ કરો");
        m.put("rbio.meeting.action.save", "સાચવો અને આગળ વધો");
        m.put("rbio.meeting.participant.email", "ઈમેલ");
        m.put("rbio.meeting.participant.none", "હજુ કોઈ સહભાગી નોંધાયા નથી.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "مفاہمتی اجلاس");
        m.put("rbio.meeting.status", "اجلاس کی حالت");
        m.put("rbio.meeting.status.scheduled", "طے شدہ");
        m.put("rbio.meeting.status.rescheduled", "دوبارہ طے شدہ");
        m.put("rbio.meeting.status.completed", "مکمل");
        m.put("rbio.meeting.date", "اجلاس کی تاریخ");
        m.put("rbio.meeting.time", "اجلاس کا وقت");
        m.put("rbio.meeting.venue", "مقام");
        m.put("rbio.meeting.mode", "طریقہ");
        m.put("rbio.meeting.participants", "شرکاء");
        m.put("rbio.meeting.participants.entity", "ادارہ");
        m.put("rbio.meeting.participants.complainant", "شکایت کنندہ");
        m.put("rbio.meeting.participants.both", "دونوں");
        m.put("rbio.meeting.reschedule_reason", "دوبارہ طے کرنے کی وجہ");
        m.put("rbio.meeting.entity_accepted", "ادارے کی منظوری");
        m.put("rbio.meeting.entity_accepted.yes", "ہاں");
        m.put("rbio.meeting.entity_accepted.no", "نہیں");
        m.put("rbio.meeting.minutes", "اجلاس کی کارروائی");
        m.put("rbio.meeting.history", "اجلاس کی تاریخ");
        m.put("rbio.meeting.history.superseded", "تبدیل شدہ");
        m.put("rbio.meeting.history.current", "موجودہ اجلاس");
        m.put("rbio.meeting.no_meeting", "اس شکایت کے لیے کوئی اجلاس طے نہیں کیا گیا۔");
        m.put("rbio.meeting.action.schedule", "اجلاس طے کریں");
        m.put("rbio.meeting.action.reschedule", "اجلاس دوبارہ طے کریں");
        m.put("rbio.meeting.action.complete", "کارروائی درج کریں");
        m.put("rbio.meeting.action.download_mom", "کارروائی کا خط ڈاؤن لوڈ کریں");
        m.put("rbio.meeting.action.upload_signed", "دستخط شدہ کارروائی اپ لوڈ کریں");
        m.put("rbio.meeting.action.add_participant", "شریک شامل کریں");
        m.put("rbio.meeting.participant.confirmed", "حاضری کی تصدیق");
        m.put("rbio.meeting.participant.invited", "مدعو");
        m.put("rbio.meeting.participant.cap_reached",
                "ایک شکایت میں زیادہ سے زیادہ چھ اضافی ادارہ شرکاء ہو سکتے ہیں۔");
        m.put("rbio.meeting.saved.scheduled", "اجلاس طے کر دیا گیا۔");
        m.put("rbio.meeting.saved.rescheduled",
                "اجلاس دوبارہ طے کر دیا گیا۔ پچھلے اجلاس کی تفصیلات تاریخ میں محفوظ ہیں۔");
        m.put("rbio.meeting.saved.completed", "اجلاس کی کارروائی درج کر دی گئی۔");
        m.put("rbio.meeting.saved.uploaded", "دستخط شدہ کارروائی اپ لوڈ کر دی گئی۔");
        m.put("rbio.meeting.error.date_required", "اجلاس کی تاریخ ضروری ہے۔");
        m.put("rbio.meeting.error.date_invalid", "اجلاس کی تاریخ درست ہونی چاہیے۔");
        m.put("rbio.meeting.error.time_required", "اجلاس کا وقت ضروری ہے۔");
        m.put("rbio.meeting.error.time_invalid", "اجلاس کا وقت درست ہونا چاہیے (HH:mm)۔");
        m.put("rbio.meeting.error.participants_required",
                "شرکاء منتخب کریں: ادارہ، شکایت کنندہ یا دونوں۔");
        m.put("rbio.meeting.error.participants_invalid",
                "شرکاء ادارہ، شکایت کنندہ یا دونوں ہونے چاہییں۔");
        m.put("rbio.meeting.error.reason_required", "اجلاس دوبارہ طے کرنے کے لیے وجہ ضروری ہے۔");
        m.put("rbio.meeting.error.reason_too_long", "دوبارہ طے کرنے کی وجہ بہت طویل ہے۔");
        m.put("rbio.meeting.error.acceptance_required",
                "درج کریں کہ ادارے نے زیر بحث تصفیہ قبول کیا یا نہیں۔");
        m.put("rbio.meeting.error.acceptance_invalid", "ادارے کی منظوری ہاں یا نہیں ہونی چاہیے۔");
        m.put("rbio.meeting.error.minutes_required", "اجلاس کی کارروائی ضروری ہے۔");
        m.put("rbio.meeting.error.minutes_too_long", "اجلاس کی کارروائی بہت طویل ہے۔");
        m.put("rbio.meeting.error.status_excluded",
                "اس مرحلے پر شکایت کے لیے اجلاس طے نہیں کیا جا سکتا۔");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "اجلاس کی اہلیت جانچی نہیں جا سکی۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "اس شکایت پر دوبارہ طے کرنے کے لیے کوئی طے شدہ اجلاس نہیں ہے۔");
        m.put("rbio.meeting.error.nothing_to_complete",
                "اس شکایت پر کارروائی درج کرنے کے لیے کوئی طے شدہ اجلاس نہیں ہے۔");
        m.put("rbio.meeting.error.no_completed_meeting",
                "کوئی مکمل اجلاس نہیں ہے، لہٰذا جاری کرنے کے لیے کوئی کارروائی نہیں ہے۔");
        m.put("rbio.meeting.error.template_missing",
                "کارروائی کے خط کا نمونہ مرتب نہیں ہے۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("rbio.meeting.error.unavailable", "اجلاس درج نہیں کیا جا سکا۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("rbio.meeting.error.participant_name_required", "شریک کا نام ضروری ہے۔");
        m.put("rbio.meeting.error.upload_failed",
                "دستخط شدہ کارروائی اپ لوڈ نہیں کی جا سکی۔ براہ کرم دوبارہ کوشش کریں۔");
        m.put("rbio.meeting.outcome.title", "مفاہمتی نتیجہ درج کریں");
        m.put("rbio.meeting.outcome.settled", "تصفیہ ہو گیا");
        m.put("rbio.meeting.outcome.failed", "ناکام");
        m.put("rbio.meeting.outcome.not_started", "شروع نہیں ہوا");
        m.put("rbio.meeting.outcome.in_progress", "جاری");
        m.put("rbio.meeting.outcome.compensation_type", "معاوضے کی قسم");
        m.put("rbio.meeting.outcome.compensation_amount", "معاوضے کی رقم (روپے)");
        m.put("rbio.meeting.outcome.summary", "تصفیے کا خلاصہ");
        m.put("rbio.meeting.outcome.failure_reason", "ناکامی کی وجہ");
        m.put("rbio.meeting.action.cancel", "منسوخ کریں");
        m.put("rbio.meeting.action.save", "محفوظ کریں اور آگے بڑھیں");
        m.put("rbio.meeting.participant.email", "ای میل");
        m.put("rbio.meeting.participant.none", "ابھی کوئی شریک درج نہیں۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "ಸಂಧಾನ ಸಭೆ");
        m.put("rbio.meeting.status", "ಸಭೆಯ ಸ್ಥಿತಿ");
        m.put("rbio.meeting.status.scheduled", "ನಿಗದಿಯಾಗಿದೆ");
        m.put("rbio.meeting.status.rescheduled", "ಮರುನಿಗದಿಯಾಗಿದೆ");
        m.put("rbio.meeting.status.completed", "ಪೂರ್ಣ");
        m.put("rbio.meeting.date", "ಸಭೆಯ ದಿನಾಂಕ");
        m.put("rbio.meeting.time", "ಸಭೆಯ ಸಮಯ");
        m.put("rbio.meeting.venue", "ಸ್ಥಳ");
        m.put("rbio.meeting.mode", "ವಿಧಾನ");
        m.put("rbio.meeting.participants", "ಭಾಗವಹಿಸುವವರು");
        m.put("rbio.meeting.participants.entity", "ಸಂಸ್ಥೆ");
        m.put("rbio.meeting.participants.complainant", "ದೂರುದಾರ");
        m.put("rbio.meeting.participants.both", "ಇಬ್ಬರೂ");
        m.put("rbio.meeting.reschedule_reason", "ಮರುನಿಗದಿಯ ಕಾರಣ");
        m.put("rbio.meeting.entity_accepted", "ಸಂಸ್ಥೆಯ ಒಪ್ಪಿಗೆ");
        m.put("rbio.meeting.entity_accepted.yes", "ಹೌದು");
        m.put("rbio.meeting.entity_accepted.no", "ಇಲ್ಲ");
        m.put("rbio.meeting.minutes", "ಸಭೆಯ ನಡವಳಿ");
        m.put("rbio.meeting.history", "ಸಭೆಯ ಇತಿಹಾಸ");
        m.put("rbio.meeting.history.superseded", "ಬದಲಾಯಿಸಲಾಗಿದೆ");
        m.put("rbio.meeting.history.current", "ಪ್ರಸ್ತುತ ಸಭೆ");
        m.put("rbio.meeting.no_meeting", "ಈ ದೂರಿಗೆ ಯಾವುದೇ ಸಭೆ ನಿಗದಿಯಾಗಿಲ್ಲ.");
        m.put("rbio.meeting.action.schedule", "ಸಭೆ ನಿಗದಿಪಡಿಸಿ");
        m.put("rbio.meeting.action.reschedule", "ಸಭೆ ಮರುನಿಗದಿಪಡಿಸಿ");
        m.put("rbio.meeting.action.complete", "ನಡವಳಿ ದಾಖಲಿಸಿ");
        m.put("rbio.meeting.action.download_mom", "ನಡವಳಿ ಪತ್ರ ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("rbio.meeting.action.upload_signed", "ಸಹಿ ಮಾಡಿದ ನಡವಳಿ ಅಪ್‌ಲೋಡ್ ಮಾಡಿ");
        m.put("rbio.meeting.action.add_participant", "ಭಾಗವಹಿಸುವವರನ್ನು ಸೇರಿಸಿ");
        m.put("rbio.meeting.participant.confirmed", "ಹಾಜರಿ ದೃಢಪಟ್ಟಿದೆ");
        m.put("rbio.meeting.participant.invited", "ಆಹ್ವಾನಿತ");
        m.put("rbio.meeting.participant.cap_reached",
                "ಒಂದು ದೂರಿನಲ್ಲಿ ಗರಿಷ್ಠ ಆರು ಹೆಚ್ಚುವರಿ ಸಂಸ್ಥೆ ಭಾಗವಹಿಸುವವರು ಇರಬಹುದು.");
        m.put("rbio.meeting.saved.scheduled", "ಸಭೆ ನಿಗದಿಯಾಗಿದೆ.");
        m.put("rbio.meeting.saved.rescheduled",
                "ಸಭೆ ಮರುನಿಗದಿಯಾಗಿದೆ. ಹಿಂದಿನ ಸಭೆಯ ವಿವರಗಳು ಇತಿಹಾಸದಲ್ಲಿ ಉಳಿದಿವೆ.");
        m.put("rbio.meeting.saved.completed", "ಸಭೆಯ ನಡವಳಿ ದಾಖಲಾಗಿದೆ.");
        m.put("rbio.meeting.saved.uploaded", "ಸಹಿ ಮಾಡಿದ ನಡವಳಿ ಅಪ್‌ಲೋಡ್ ಆಗಿದೆ.");
        m.put("rbio.meeting.error.date_required", "ಸಭೆಯ ದಿನಾಂಕ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.meeting.error.date_invalid", "ಸಭೆಯ ದಿನಾಂಕ ಮಾನ್ಯವಾಗಿರಬೇಕು.");
        m.put("rbio.meeting.error.time_required", "ಸಭೆಯ ಸಮಯ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.meeting.error.time_invalid", "ಸಭೆಯ ಸಮಯ ಮಾನ್ಯವಾಗಿರಬೇಕು (HH:mm).");
        m.put("rbio.meeting.error.participants_required",
                "ಭಾಗವಹಿಸುವವರನ್ನು ಆಯ್ಕೆಮಾಡಿ: ಸಂಸ್ಥೆ, ದೂರುದಾರ ಅಥವಾ ಇಬ್ಬರೂ.");
        m.put("rbio.meeting.error.participants_invalid",
                "ಭಾಗವಹಿಸುವವರು ಸಂಸ್ಥೆ, ದೂರುದಾರ ಅಥವಾ ಇಬ್ಬರೂ ಆಗಿರಬೇಕು.");
        m.put("rbio.meeting.error.reason_required", "ಸಭೆ ಮರುನಿಗದಿಪಡಿಸಲು ಕಾರಣ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.meeting.error.reason_too_long", "ಮರುನಿಗದಿಯ ಕಾರಣ ತುಂಬಾ ದೀರ್ಘವಾಗಿದೆ.");
        m.put("rbio.meeting.error.acceptance_required",
                "ಚರ್ಚಿಸಿದ ಪರಿಹಾರವನ್ನು ಸಂಸ್ಥೆ ಒಪ್ಪಿಕೊಂಡಿದೆಯೇ ಎಂದು ದಾಖಲಿಸಿ.");
        m.put("rbio.meeting.error.acceptance_invalid", "ಸಂಸ್ಥೆಯ ಒಪ್ಪಿಗೆ ಹೌದು ಅಥವಾ ಇಲ್ಲ ಆಗಿರಬೇಕು.");
        m.put("rbio.meeting.error.minutes_required", "ಸಭೆಯ ನಡವಳಿ ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.meeting.error.minutes_too_long", "ಸಭೆಯ ನಡವಳಿ ತುಂಬಾ ದೀರ್ಘವಾಗಿದೆ.");
        m.put("rbio.meeting.error.status_excluded",
                "ಈ ಹಂತದಲ್ಲಿ ದೂರಿಗೆ ಸಭೆ ನಿಗದಿಪಡಿಸಲು ಸಾಧ್ಯವಿಲ್ಲ.");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "ಸಭೆಯ ಅರ್ಹತೆಯನ್ನು ಪರಿಶೀಲಿಸಲಾಗಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "ಈ ದೂರಿನಲ್ಲಿ ಮರುನಿಗದಿಪಡಿಸಲು ಯಾವುದೇ ನಿಗದಿತ ಸಭೆ ಇಲ್ಲ.");
        m.put("rbio.meeting.error.nothing_to_complete",
                "ಈ ದೂರಿನಲ್ಲಿ ನಡವಳಿ ದಾಖಲಿಸಲು ಯಾವುದೇ ನಿಗದಿತ ಸಭೆ ಇಲ್ಲ.");
        m.put("rbio.meeting.error.no_completed_meeting",
                "ಪೂರ್ಣಗೊಂಡ ಸಭೆ ಇಲ್ಲ, ಆದ್ದರಿಂದ ಹೊರಡಿಸಲು ನಡವಳಿ ಇಲ್ಲ.");
        m.put("rbio.meeting.error.template_missing",
                "ನಡವಳಿ ಪತ್ರದ ಮಾದರಿ ಸಂರಚಿಸಲಾಗಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("rbio.meeting.error.unavailable", "ಸಭೆ ದಾಖಲಿಸಲಾಗಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("rbio.meeting.error.participant_name_required", "ಭಾಗವಹಿಸುವವರ ಹೆಸರು ಅಗತ್ಯವಿದೆ.");
        m.put("rbio.meeting.error.upload_failed",
                "ಸಹಿ ಮಾಡಿದ ನಡವಳಿ ಅಪ್‌ಲೋಡ್ ಆಗಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.");
        m.put("rbio.meeting.outcome.title", "ಸಂಧಾನ ಫಲಿತಾಂಶ ದಾಖಲಿಸಿ");
        m.put("rbio.meeting.outcome.settled", "ಪರಿಹಾರವಾಗಿದೆ");
        m.put("rbio.meeting.outcome.failed", "ವಿಫಲ");
        m.put("rbio.meeting.outcome.not_started", "ಪ್ರಾರಂಭವಾಗಿಲ್ಲ");
        m.put("rbio.meeting.outcome.in_progress", "ಪ್ರಗತಿಯಲ್ಲಿ");
        m.put("rbio.meeting.outcome.compensation_type", "ಪರಿಹಾರದ ಪ್ರಕಾರ");
        m.put("rbio.meeting.outcome.compensation_amount", "ಪರಿಹಾರ ಮೊತ್ತ (ರೂ.)");
        m.put("rbio.meeting.outcome.summary", "ಪರಿಹಾರ ಸಾರಾಂಶ");
        m.put("rbio.meeting.outcome.failure_reason", "ವಿಫಲತೆಯ ಕಾರಣ");
        m.put("rbio.meeting.action.cancel", "ರದ್ದುಗೊಳಿಸಿ");
        m.put("rbio.meeting.action.save", "ಉಳಿಸಿ ಮುಂದುವರಿಯಿರಿ");
        m.put("rbio.meeting.participant.email", "ಇಮೇಲ್");
        m.put("rbio.meeting.participant.none", "ಇನ್ನೂ ಯಾವುದೇ ಭಾಗವಹಿಸುವವರು ದಾಖಲಾಗಿಲ್ಲ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.meeting.title", "അനുരഞ്ജന യോഗം");
        m.put("rbio.meeting.status", "യോഗത്തിന്റെ നില");
        m.put("rbio.meeting.status.scheduled", "നിശ്ചയിച്ചു");
        m.put("rbio.meeting.status.rescheduled", "പുനർനിശ്ചയിച്ചു");
        m.put("rbio.meeting.status.completed", "പൂർത്തിയായി");
        m.put("rbio.meeting.date", "യോഗ തീയതി");
        m.put("rbio.meeting.time", "യോഗ സമയം");
        m.put("rbio.meeting.venue", "സ്ഥലം");
        m.put("rbio.meeting.mode", "രീതി");
        m.put("rbio.meeting.participants", "പങ്കാളികൾ");
        m.put("rbio.meeting.participants.entity", "സ്ഥാപനം");
        m.put("rbio.meeting.participants.complainant", "പരാതിക്കാരൻ");
        m.put("rbio.meeting.participants.both", "രണ്ടും");
        m.put("rbio.meeting.reschedule_reason", "പുനർനിശ്ചയത്തിനുള്ള കാരണം");
        m.put("rbio.meeting.entity_accepted", "സ്ഥാപനത്തിന്റെ സമ്മതം");
        m.put("rbio.meeting.entity_accepted.yes", "അതെ");
        m.put("rbio.meeting.entity_accepted.no", "അല്ല");
        m.put("rbio.meeting.minutes", "യോഗ നടപടിക്കുറിപ്പ്");
        m.put("rbio.meeting.history", "യോഗ ചരിത്രം");
        m.put("rbio.meeting.history.superseded", "മാറ്റിസ്ഥാപിച്ചു");
        m.put("rbio.meeting.history.current", "നിലവിലുള്ള യോഗം");
        m.put("rbio.meeting.no_meeting", "ഈ പരാതിക്കായി ഒരു യോഗവും നിശ്ചയിച്ചിട്ടില്ല.");
        m.put("rbio.meeting.action.schedule", "യോഗം നിശ്ചയിക്കുക");
        m.put("rbio.meeting.action.reschedule", "യോഗം പുനർനിശ്ചയിക്കുക");
        m.put("rbio.meeting.action.complete", "നടപടിക്കുറിപ്പ് രേഖപ്പെടുത്തുക");
        m.put("rbio.meeting.action.download_mom", "നടപടിക്കുറിപ്പ് കത്ത് ഡൗൺലോഡ് ചെയ്യുക");
        m.put("rbio.meeting.action.upload_signed", "ഒപ്പിട്ട നടപടിക്കുറിപ്പ് അപ്‌ലോഡ് ചെയ്യുക");
        m.put("rbio.meeting.action.add_participant", "പങ്കാളിയെ ചേർക്കുക");
        m.put("rbio.meeting.participant.confirmed", "ഹാജർ സ്ഥിരീകരിച്ചു");
        m.put("rbio.meeting.participant.invited", "ക്ഷണിച്ചു");
        m.put("rbio.meeting.participant.cap_reached",
                "ഒരു പരാതിയിൽ പരമാവധി ആറ് അധിക സ്ഥാപന പങ്കാളികൾ ഉണ്ടാകാം.");
        m.put("rbio.meeting.saved.scheduled", "യോഗം നിശ്ചയിച്ചു.");
        m.put("rbio.meeting.saved.rescheduled",
                "യോഗം പുനർനിശ്ചയിച്ചു. മുൻ യോഗത്തിന്റെ വിവരങ്ങൾ ചരിത്രത്തിൽ സൂക്ഷിച്ചിട്ടുണ്ട്.");
        m.put("rbio.meeting.saved.completed", "യോഗ നടപടിക്കുറിപ്പ് രേഖപ്പെടുത്തി.");
        m.put("rbio.meeting.saved.uploaded", "ഒപ്പിട്ട നടപടിക്കുറിപ്പ് അപ്‌ലോഡ് ചെയ്തു.");
        m.put("rbio.meeting.error.date_required", "യോഗ തീയതി ആവശ്യമാണ്.");
        m.put("rbio.meeting.error.date_invalid", "യോഗ തീയതി സാധുവായിരിക്കണം.");
        m.put("rbio.meeting.error.time_required", "യോഗ സമയം ആവശ്യമാണ്.");
        m.put("rbio.meeting.error.time_invalid", "യോഗ സമയം സാധുവായിരിക്കണം (HH:mm).");
        m.put("rbio.meeting.error.participants_required",
                "പങ്കാളികളെ തിരഞ്ഞെടുക്കുക: സ്ഥാപനം, പരാതിക്കാരൻ അല്ലെങ്കിൽ രണ്ടും.");
        m.put("rbio.meeting.error.participants_invalid",
                "പങ്കാളികൾ സ്ഥാപനം, പരാതിക്കാരൻ അല്ലെങ്കിൽ രണ്ടും ആയിരിക്കണം.");
        m.put("rbio.meeting.error.reason_required", "യോഗം പുനർനിശ്ചയിക്കാൻ കാരണം ആവശ്യമാണ്.");
        m.put("rbio.meeting.error.reason_too_long", "പുനർനിശ്ചയത്തിനുള്ള കാരണം വളരെ നീണ്ടതാണ്.");
        m.put("rbio.meeting.error.acceptance_required",
                "ചർച്ച ചെയ്ത പരിഹാരം സ്ഥാപനം അംഗീകരിച്ചോ എന്ന് രേഖപ്പെടുത്തുക.");
        m.put("rbio.meeting.error.acceptance_invalid",
                "സ്ഥാപനത്തിന്റെ സമ്മതം അതെ അല്ലെങ്കിൽ അല്ല ആയിരിക്കണം.");
        m.put("rbio.meeting.error.minutes_required", "യോഗ നടപടിക്കുറിപ്പ് ആവശ്യമാണ്.");
        m.put("rbio.meeting.error.minutes_too_long", "യോഗ നടപടിക്കുറിപ്പ് വളരെ നീണ്ടതാണ്.");
        m.put("rbio.meeting.error.status_excluded",
                "ഈ ഘട്ടത്തിൽ പരാതിക്കായി യോഗം നിശ്ചയിക്കാൻ കഴിയില്ല.");
        m.put("rbio.meeting.error.status_rules_unavailable",
                "യോഗ യോഗ്യത പരിശോധിക്കാൻ കഴിഞ്ഞില്ല. വീണ്ടും ശ്രമിക്കുക.");
        m.put("rbio.meeting.error.nothing_to_reschedule",
                "ഈ പരാതിയിൽ പുനർനിശ്ചയിക്കാൻ നിശ്ചയിച്ച യോഗമില്ല.");
        m.put("rbio.meeting.error.nothing_to_complete",
                "ഈ പരാതിയിൽ നടപടിക്കുറിപ്പ് രേഖപ്പെടുത്താൻ നിശ്ചയിച്ച യോഗമില്ല.");
        m.put("rbio.meeting.error.no_completed_meeting",
                "പൂർത്തിയായ യോഗമില്ല, അതിനാൽ പുറപ്പെടുവിക്കാൻ നടപടിക്കുറിപ്പില്ല.");
        m.put("rbio.meeting.error.template_missing",
                "നടപടിക്കുറിപ്പ് കത്തിന്റെ മാതൃക ക്രമീകരിച്ചിട്ടില്ല. വീണ്ടും ശ്രമിക്കുക.");
        m.put("rbio.meeting.error.unavailable", "യോഗം രേഖപ്പെടുത്താൻ കഴിഞ്ഞില്ല. വീണ്ടും ശ്രമിക്കുക.");
        m.put("rbio.meeting.error.participant_name_required", "പങ്കാളിയുടെ പേര് ആവശ്യമാണ്.");
        m.put("rbio.meeting.error.upload_failed",
                "ഒപ്പിട്ട നടപടിക്കുറിപ്പ് അപ്‌ലോഡ് ചെയ്യാൻ കഴിഞ്ഞില്ല. വീണ്ടും ശ്രമിക്കുക.");
        m.put("rbio.meeting.outcome.title", "അനുരഞ്ജന ഫലം രേഖപ്പെടുത്തുക");
        m.put("rbio.meeting.outcome.settled", "പരിഹരിച്ചു");
        m.put("rbio.meeting.outcome.failed", "പരാജയം");
        m.put("rbio.meeting.outcome.not_started", "ആരംഭിച്ചിട്ടില്ല");
        m.put("rbio.meeting.outcome.in_progress", "നടക്കുന്നു");
        m.put("rbio.meeting.outcome.compensation_type", "നഷ്ടപരിഹാര തരം");
        m.put("rbio.meeting.outcome.compensation_amount", "നഷ്ടപരിഹാര തുക (രൂ.)");
        m.put("rbio.meeting.outcome.summary", "പരിഹാര സംഗ്രഹം");
        m.put("rbio.meeting.outcome.failure_reason", "പരാജയ കാരണം");
        m.put("rbio.meeting.action.cancel", "റദ്ദാക്കുക");
        m.put("rbio.meeting.action.save", "സംരക്ഷിച്ച് തുടരുക");
        m.put("rbio.meeting.participant.email", "ഇമെയിൽ");
        m.put("rbio.meeting.participant.none", "ഇതുവരെ പങ്കാളികളെ രേഖപ്പെടുത്തിയിട്ടില്ല.");
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
