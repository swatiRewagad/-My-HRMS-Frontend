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
 * Chrome for the notification bell: its heading, its empty state, the relative timestamps and the
 * per-type badge.
 *
 * <p>Every one of these strings was a hardcoded English literal in
 * {@code notification-bell.component.ts}, which made the bell the one piece of shell chrome that
 * stayed English in all ten locales. The {@code notifications.time.*} entries take a {@code {count}}
 * placeholder, interpolated by {@code TranslationService.translate(key, params)} on the client.
 *
 * <p>{@code notifications.type.*} is keyed on the lowercased notification type. The list mirrors the
 * {@code NotificationType} union in {@code notification.service.ts} plus the types producers actually
 * emit that were missing from it ({@code NEW_ASSIGNMENT}, {@code ESCALATION},
 * {@code CONFIGURATION_ALERT}, {@code QUERY}, {@code TRANSFER_REQUEST}). The component falls back to
 * the humanised enum for anything unseeded, so a new type is degraded rather than broken.
 *
 * <p>A separate seeder from the AA and intake ones on purpose: several sessions are adding keys
 * concurrently and a shared file is a guaranteed merge conflict. Insert-if-absent, like its
 * siblings — correcting a default here does NOT rewrite a row already in the database; that needs a
 * code-scoped UPDATE in both migration directories.
 */
@Component
@Order(15)
public class NotificationBellTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "notifications";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public NotificationBellTranslationSeeder(TranslationKeyRepository keyRepo,
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

        // ═══ Bell chrome ═══
        m.put("notifications.title", "Notifications");
        m.put("notifications.mark_all_read", "Mark all read");
        m.put("notifications.empty", "No notifications");
        m.put("notifications.live_updates_unavailable",
              "Live updates are unavailable. Reopen this list to check for new items.");

        // ═══ Relative timestamps ═══
        m.put("notifications.time.just_now", "Just now");
        m.put("notifications.time.minutes_ago", "{{count}}m ago");
        m.put("notifications.time.hours_ago", "{{count}}h ago");
        m.put("notifications.time.days_ago", "{{count}}d ago");

        // ═══ Type badges ═══
        m.put("notifications.type.assignment", "Assignment");
        m.put("notifications.type.new_assignment", "New assignment");
        m.put("notifications.type.reassignment", "Reassignment");
        m.put("notifications.type.transfer_in", "Transferred in");
        m.put("notifications.type.transfer_pending", "Transfer pending");
        m.put("notifications.type.transfer_request", "Transfer request");
        m.put("notifications.type.pending_3day", "Pending 3 days");
        m.put("notifications.type.pending_5day", "Pending 5 days");
        m.put("notifications.type.duplicate_detected", "Possible duplicate");
        m.put("notifications.type.sent_back", "Sent back");
        m.put("notifications.type.bulk_close", "Bulk closure");
        m.put("notifications.type.on_leave_pending", "Officer on leave");
        m.put("notifications.type.no_record_assigned", "No record assigned");
        m.put("notifications.type.no_reassigned_to_rbi", "Returned to RBI");
        m.put("notifications.type.no_status_stale", "Status not updated");
        m.put("notifications.type.re_response", "Entity response");
        m.put("notifications.type.re_update", "Entity update");
        m.put("notifications.type.complaint_closed", "Complaint closed");
        m.put("notifications.type.meeting_scheduled", "Meeting scheduled");
        m.put("notifications.type.award_passed", "Award passed");
        m.put("notifications.type.decision", "Decision");
        m.put("notifications.type.advisory_complied", "Advisory complied");
        m.put("notifications.type.document_uploaded", "Document uploaded");
        m.put("notifications.type.upload_link_sent", "Upload link sent");
        m.put("notifications.type.crpc_toll_free_reminder", "Toll-free reminder");
        m.put("notifications.type.ria_legal_update", "Legal update");
        m.put("notifications.type.escalation", "Escalation");
        m.put("notifications.type.configuration_alert", "Configuration alert");
        m.put("notifications.type.query", "Query");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "सूचनाएँ");
        m.put("notifications.mark_all_read", "सभी को पढ़ा हुआ चिह्नित करें");
        m.put("notifications.empty", "कोई सूचना नहीं");
        m.put("notifications.live_updates_unavailable",
              "तत्काल अद्यतन उपलब्ध नहीं हैं। नई सूचनाएँ देखने के लिए इस सूची को फिर से खोलें।");
        m.put("notifications.time.just_now", "अभी");
        m.put("notifications.time.minutes_ago", "{{count}} मिनट पूर्व");
        m.put("notifications.time.hours_ago", "{{count}} घंटे पूर्व");
        m.put("notifications.time.days_ago", "{{count}} दिन पूर्व");
        m.put("notifications.type.assignment", "आवंटन");
        m.put("notifications.type.new_assignment", "नया आवंटन");
        m.put("notifications.type.reassignment", "पुनरावंटन");
        m.put("notifications.type.transfer_in", "स्थानांतरित प्राप्त");
        m.put("notifications.type.transfer_pending", "स्थानांतरण लंबित");
        m.put("notifications.type.transfer_request", "स्थानांतरण अनुरोध");
        m.put("notifications.type.pending_3day", "3 दिन से लंबित");
        m.put("notifications.type.pending_5day", "5 दिन से लंबित");
        m.put("notifications.type.duplicate_detected", "संभावित पुनरावृत्ति");
        m.put("notifications.type.sent_back", "वापस भेजा गया");
        m.put("notifications.type.bulk_close", "सामूहिक समापन");
        m.put("notifications.type.on_leave_pending", "अधिकारी अवकाश पर");
        m.put("notifications.type.no_record_assigned", "कोई अभिलेख आवंटित नहीं");
        m.put("notifications.type.no_reassigned_to_rbi", "भारतीय रिज़र्व बैंक को वापस");
        m.put("notifications.type.no_status_stale", "स्थिति अद्यतन नहीं");
        m.put("notifications.type.re_response", "संस्था का उत्तर");
        m.put("notifications.type.re_update", "संस्था का अद्यतन");
        m.put("notifications.type.complaint_closed", "शिकायत बंद");
        m.put("notifications.type.meeting_scheduled", "बैठक निर्धारित");
        m.put("notifications.type.award_passed", "अधिनिर्णय पारित");
        m.put("notifications.type.decision", "निर्णय");
        m.put("notifications.type.advisory_complied", "परामर्श का अनुपालन");
        m.put("notifications.type.document_uploaded", "दस्तावेज़ अपलोड");
        m.put("notifications.type.upload_link_sent", "अपलोड लिंक भेजा गया");
        m.put("notifications.type.crpc_toll_free_reminder", "टोल-फ़्री अनुस्मारक");
        m.put("notifications.type.ria_legal_update", "विधिक अद्यतन");
        m.put("notifications.type.escalation", "उत्प्रेषण");
        m.put("notifications.type.configuration_alert", "संरचना चेतावनी");
        m.put("notifications.type.query", "प्रश्न");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "सूचना");
        m.put("notifications.mark_all_read", "सर्व वाचल्या म्हणून चिन्हांकित करा");
        m.put("notifications.empty", "कोणतीही सूचना नाही");
        m.put("notifications.live_updates_unavailable",
              "थेट अद्ययावत माहिती उपलब्ध नाही. नवीन सूचना पाहण्यासाठी ही यादी पुन्हा उघडा.");
        m.put("notifications.time.just_now", "आत्ताच");
        m.put("notifications.time.minutes_ago", "{{count}} मिनिटांपूर्वी");
        m.put("notifications.time.hours_ago", "{{count}} तासांपूर्वी");
        m.put("notifications.time.days_ago", "{{count}} दिवसांपूर्वी");
        m.put("notifications.type.assignment", "वाटप");
        m.put("notifications.type.new_assignment", "नवीन वाटप");
        m.put("notifications.type.reassignment", "पुनर्वाटप");
        m.put("notifications.type.transfer_in", "हस्तांतरणाने प्राप्त");
        m.put("notifications.type.transfer_pending", "हस्तांतरण प्रलंबित");
        m.put("notifications.type.transfer_request", "हस्तांतरण विनंती");
        m.put("notifications.type.pending_3day", "3 दिवसांपासून प्रलंबित");
        m.put("notifications.type.pending_5day", "5 दिवसांपासून प्रलंबित");
        m.put("notifications.type.duplicate_detected", "संभाव्य दुहेरी नोंद");
        m.put("notifications.type.sent_back", "परत पाठविले");
        m.put("notifications.type.bulk_close", "सामूहिक निर्गती");
        m.put("notifications.type.on_leave_pending", "अधिकारी रजेवर");
        m.put("notifications.type.no_record_assigned", "कोणतीही नोंद वाटप केलेली नाही");
        m.put("notifications.type.no_reassigned_to_rbi", "भारतीय रिझर्व्ह बँकेकडे परत");
        m.put("notifications.type.no_status_stale", "स्थिती अद्ययावत नाही");
        m.put("notifications.type.re_response", "संस्थेचे उत्तर");
        m.put("notifications.type.re_update", "संस्थेकडून अद्ययावत माहिती");
        m.put("notifications.type.complaint_closed", "तक्रार बंद");
        m.put("notifications.type.meeting_scheduled", "बैठक निश्चित");
        m.put("notifications.type.award_passed", "निवाडा पारित");
        m.put("notifications.type.decision", "निर्णय");
        m.put("notifications.type.advisory_complied", "सल्ल्याचे पालन");
        m.put("notifications.type.document_uploaded", "दस्तऐवज अपलोड");
        m.put("notifications.type.upload_link_sent", "अपलोड दुवा पाठविला");
        m.put("notifications.type.crpc_toll_free_reminder", "टोल-फ्री स्मरणपत्र");
        m.put("notifications.type.ria_legal_update", "कायदेशीर अद्ययावत माहिती");
        m.put("notifications.type.escalation", "वरिष्ठ स्तरावर पाठवणे");
        m.put("notifications.type.configuration_alert", "संरचना इशारा");
        m.put("notifications.type.query", "विचारणा");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "বিজ্ঞপ্তি");
        m.put("notifications.mark_all_read", "সব পড়া হিসেবে চিহ্নিত করুন");
        m.put("notifications.empty", "কোনো বিজ্ঞপ্তি নেই");
        m.put("notifications.live_updates_unavailable",
              "সরাসরি হালনাগাদ পাওয়া যাচ্ছে না। নতুন বিষয় দেখতে এই তালিকাটি আবার খুলুন।");
        m.put("notifications.time.just_now", "এই মুহূর্তে");
        m.put("notifications.time.minutes_ago", "{{count}} মিনিট আগে");
        m.put("notifications.time.hours_ago", "{{count}} ঘণ্টা আগে");
        m.put("notifications.time.days_ago", "{{count}} দিন আগে");
        m.put("notifications.type.assignment", "বরাদ্দ");
        m.put("notifications.type.new_assignment", "নতুন বরাদ্দ");
        m.put("notifications.type.reassignment", "পুনর্বরাদ্দ");
        m.put("notifications.type.transfer_in", "স্থানান্তরিত হয়ে এসেছে");
        m.put("notifications.type.transfer_pending", "স্থানান্তর অপেক্ষমাণ");
        m.put("notifications.type.transfer_request", "স্থানান্তরের আবেদন");
        m.put("notifications.type.pending_3day", "৩ দিন ধরে অপেক্ষমাণ");
        m.put("notifications.type.pending_5day", "৫ দিন ধরে অপেক্ষমাণ");
        m.put("notifications.type.duplicate_detected", "সম্ভাব্য নকল");
        m.put("notifications.type.sent_back", "ফেরত পাঠানো হয়েছে");
        m.put("notifications.type.bulk_close", "সমষ্টিগত নিষ্পত্তি");
        m.put("notifications.type.on_leave_pending", "আধিকারিক ছুটিতে");
        m.put("notifications.type.no_record_assigned", "কোনো নথি বরাদ্দ করা হয়নি");
        m.put("notifications.type.no_reassigned_to_rbi", "ভারতীয় রিজার্ভ ব্যাঙ্কে ফেরত");
        m.put("notifications.type.no_status_stale", "অবস্থা হালনাগাদ হয়নি");
        m.put("notifications.type.re_response", "প্রতিষ্ঠানের জবাব");
        m.put("notifications.type.re_update", "প্রতিষ্ঠানের হালনাগাদ");
        m.put("notifications.type.complaint_closed", "অভিযোগ নিষ্পত্তি হয়েছে");
        m.put("notifications.type.meeting_scheduled", "বৈঠক নির্ধারিত");
        m.put("notifications.type.award_passed", "রোয়েদাদ প্রদান করা হয়েছে");
        m.put("notifications.type.decision", "সিদ্ধান্ত");
        m.put("notifications.type.advisory_complied", "পরামর্শ পালিত হয়েছে");
        m.put("notifications.type.document_uploaded", "নথি আপলোড হয়েছে");
        m.put("notifications.type.upload_link_sent", "আপলোডের লিঙ্ক পাঠানো হয়েছে");
        m.put("notifications.type.crpc_toll_free_reminder", "টোল-ফ্রি স্মারক");
        m.put("notifications.type.ria_legal_update", "আইনি হালনাগাদ");
        m.put("notifications.type.escalation", "ঊর্ধ্বতন স্তরে প্রেরণ");
        m.put("notifications.type.configuration_alert", "বিন্যাস সতর্কতা");
        m.put("notifications.type.query", "জিজ্ঞাসা");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "సూచనలు");
        m.put("notifications.mark_all_read", "అన్నీ చదివినట్లు గుర్తించండి");
        m.put("notifications.empty", "సూచనలు ఏవీ లేవు");
        m.put("notifications.live_updates_unavailable",
              "తక్షణ నవీకరణలు అందుబాటులో లేవు. కొత్త అంశాల కోసం ఈ జాబితాను తిరిగి తెరవండి.");
        m.put("notifications.time.just_now", "ఇప్పుడే");
        m.put("notifications.time.minutes_ago", "{{count}} నిమిషాల క్రితం");
        m.put("notifications.time.hours_ago", "{{count}} గంటల క్రితం");
        m.put("notifications.time.days_ago", "{{count}} రోజుల క్రితం");
        m.put("notifications.type.assignment", "కేటాయింపు");
        m.put("notifications.type.new_assignment", "కొత్త కేటాయింపు");
        m.put("notifications.type.reassignment", "పునఃకేటాయింపు");
        m.put("notifications.type.transfer_in", "బదిలీ ద్వారా వచ్చింది");
        m.put("notifications.type.transfer_pending", "బదిలీ పెండింగ్‌లో");
        m.put("notifications.type.transfer_request", "బదిలీ అభ్యర్థన");
        m.put("notifications.type.pending_3day", "3 రోజులుగా పెండింగ్‌లో");
        m.put("notifications.type.pending_5day", "5 రోజులుగా పెండింగ్‌లో");
        m.put("notifications.type.duplicate_detected", "నకిలీ కావచ్చు");
        m.put("notifications.type.sent_back", "వెనక్కి పంపబడింది");
        m.put("notifications.type.bulk_close", "సమూహ ముగింపు");
        m.put("notifications.type.on_leave_pending", "అధికారి సెలవులో");
        m.put("notifications.type.no_record_assigned", "ఏ రికార్డు కేటాయించబడలేదు");
        m.put("notifications.type.no_reassigned_to_rbi", "భారతీయ రిజర్వ్ బ్యాంకుకు తిరిగి పంపబడింది");
        m.put("notifications.type.no_status_stale", "స్థితి నవీకరించబడలేదు");
        m.put("notifications.type.re_response", "సంస్థ సమాధానం");
        m.put("notifications.type.re_update", "సంస్థ నవీకరణ");
        m.put("notifications.type.complaint_closed", "ఫిర్యాదు ముగించబడింది");
        m.put("notifications.type.meeting_scheduled", "సమావేశం నిర్ణయించబడింది");
        m.put("notifications.type.award_passed", "తీర్పు వెలువడింది");
        m.put("notifications.type.decision", "నిర్ణయం");
        m.put("notifications.type.advisory_complied", "సలహా పాలించబడింది");
        m.put("notifications.type.document_uploaded", "పత్రం అప్‌లోడ్ చేయబడింది");
        m.put("notifications.type.upload_link_sent", "అప్‌లోడ్ లింక్ పంపబడింది");
        m.put("notifications.type.crpc_toll_free_reminder", "టోల్-ఫ్రీ గుర్తుచేయింపు");
        m.put("notifications.type.ria_legal_update", "న్యాయపరమైన నవీకరణ");
        m.put("notifications.type.escalation", "ఉన్నత స్థాయికి పంపడం");
        m.put("notifications.type.configuration_alert", "ఆకృతీకరణ హెచ్చరిక");
        m.put("notifications.type.query", "ప్రశ్న");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "அறிவிப்புகள்");
        m.put("notifications.mark_all_read", "அனைத்தையும் படித்ததாகக் குறிக்கவும்");
        m.put("notifications.empty", "அறிவிப்புகள் இல்லை");
        m.put("notifications.live_updates_unavailable",
              "நேரடி புதுப்பிப்புகள் கிடைக்கவில்லை. புதிய அறிவிப்புகளைப் பார்க்க இப்பட்டியலை மீண்டும் திறக்கவும்.");
        m.put("notifications.time.just_now", "இப்போதே");
        m.put("notifications.time.minutes_ago", "{{count}} நிமிடங்களுக்கு முன்");
        m.put("notifications.time.hours_ago", "{{count}} மணி நேரத்திற்கு முன்");
        m.put("notifications.time.days_ago", "{{count}} நாட்களுக்கு முன்");
        m.put("notifications.type.assignment", "ஒப்படைப்பு");
        m.put("notifications.type.new_assignment", "புதிய ஒப்படைப்பு");
        m.put("notifications.type.reassignment", "மறுஒப்படைப்பு");
        m.put("notifications.type.transfer_in", "மாற்றப்பட்டு வந்தது");
        m.put("notifications.type.transfer_pending", "மாற்றம் நிலுவையில்");
        m.put("notifications.type.transfer_request", "மாற்றக் கோரிக்கை");
        m.put("notifications.type.pending_3day", "3 நாட்களாக நிலுவையில்");
        m.put("notifications.type.pending_5day", "5 நாட்களாக நிலுவையில்");
        m.put("notifications.type.duplicate_detected", "நகல் இருக்கக்கூடும்");
        m.put("notifications.type.sent_back", "திரும்ப அனுப்பப்பட்டது");
        m.put("notifications.type.bulk_close", "மொத்த முடிவுறுத்தல்");
        m.put("notifications.type.on_leave_pending", "அதிகாரி விடுப்பில்");
        m.put("notifications.type.no_record_assigned", "எந்தப் பதிவும் ஒப்படைக்கப்படவில்லை");
        m.put("notifications.type.no_reassigned_to_rbi", "இந்திய ரிசர்வ் வங்கிக்குத் திரும்ப அனுப்பப்பட்டது");
        m.put("notifications.type.no_status_stale", "நிலை புதுப்பிக்கப்படவில்லை");
        m.put("notifications.type.re_response", "நிறுவனத்தின் பதில்");
        m.put("notifications.type.re_update", "நிறுவனத்தின் புதுப்பிப்பு");
        m.put("notifications.type.complaint_closed", "முறையீடு முடிக்கப்பட்டது");
        m.put("notifications.type.meeting_scheduled", "கூட்டம் நிர்ணயிக்கப்பட்டது");
        m.put("notifications.type.award_passed", "தீர்ப்பு வழங்கப்பட்டது");
        m.put("notifications.type.decision", "முடிவு");
        m.put("notifications.type.advisory_complied", "அறிவுரை பின்பற்றப்பட்டது");
        m.put("notifications.type.document_uploaded", "ஆவணம் பதிவேற்றப்பட்டது");
        m.put("notifications.type.upload_link_sent", "பதிவேற்ற இணைப்பு அனுப்பப்பட்டது");
        m.put("notifications.type.crpc_toll_free_reminder", "கட்டணமில்லா அழைப்பு நினைவூட்டல்");
        m.put("notifications.type.ria_legal_update", "சட்டப் புதுப்பிப்பு");
        m.put("notifications.type.escalation", "மேல்நிலைப் பரிசீலனை");
        m.put("notifications.type.configuration_alert", "அமைவு எச்சரிக்கை");
        m.put("notifications.type.query", "வினவல்");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "સૂચનાઓ");
        m.put("notifications.mark_all_read", "બધી વાંચેલી તરીકે ચિહ્નિત કરો");
        m.put("notifications.empty", "કોઈ સૂચના નથી");
        m.put("notifications.live_updates_unavailable",
              "તાત્કાલિક અદ્યતન માહિતી ઉપલબ્ધ નથી. નવી બાબતો જોવા માટે આ સૂચિ ફરી ખોલો.");
        m.put("notifications.time.just_now", "હમણાં જ");
        m.put("notifications.time.minutes_ago", "{{count}} મિનિટ પહેલાં");
        m.put("notifications.time.hours_ago", "{{count}} કલાક પહેલાં");
        m.put("notifications.time.days_ago", "{{count}} દિવસ પહેલાં");
        m.put("notifications.type.assignment", "સોંપણી");
        m.put("notifications.type.new_assignment", "નવી સોંપણી");
        m.put("notifications.type.reassignment", "પુનઃસોંપણી");
        m.put("notifications.type.transfer_in", "તબદીલ થઈને આવ્યું");
        m.put("notifications.type.transfer_pending", "તબદીલી બાકી");
        m.put("notifications.type.transfer_request", "તબદીલી વિનંતી");
        m.put("notifications.type.pending_3day", "3 દિવસથી બાકી");
        m.put("notifications.type.pending_5day", "5 દિવસથી બાકી");
        m.put("notifications.type.duplicate_detected", "સંભવિત નકલ");
        m.put("notifications.type.sent_back", "પાછું મોકલ્યું");
        m.put("notifications.type.bulk_close", "સામૂહિક નિકાલ");
        m.put("notifications.type.on_leave_pending", "અધિકારી રજા પર");
        m.put("notifications.type.no_record_assigned", "કોઈ નોંધ સોંપાઈ નથી");
        m.put("notifications.type.no_reassigned_to_rbi", "ભારતીય રિઝર્વ બેંકને પરત");
        m.put("notifications.type.no_status_stale", "સ્થિતિ અદ્યતન નથી");
        m.put("notifications.type.re_response", "સંસ્થાનો જવાબ");
        m.put("notifications.type.re_update", "સંસ્થા તરફથી અદ્યતન માહિતી");
        m.put("notifications.type.complaint_closed", "ફરિયાદ બંધ");
        m.put("notifications.type.meeting_scheduled", "બેઠક નિર્ધારિત");
        m.put("notifications.type.award_passed", "ન્યાયનિર્ણય પસાર");
        m.put("notifications.type.decision", "નિર્ણય");
        m.put("notifications.type.advisory_complied", "સલાહનું પાલન");
        m.put("notifications.type.document_uploaded", "દસ્તાવેજ અપલોડ થયો");
        m.put("notifications.type.upload_link_sent", "અપલોડ લિંક મોકલી");
        m.put("notifications.type.crpc_toll_free_reminder", "ટોલ-ફ્રી સ્મૃતિપત્ર");
        m.put("notifications.type.ria_legal_update", "કાયદાકીય અદ્યતન માહિતી");
        m.put("notifications.type.escalation", "ઉચ્ચ સ્તરે મોકલવું");
        m.put("notifications.type.configuration_alert", "રચના ચેતવણી");
        m.put("notifications.type.query", "પૃચ્છા");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "اطلاعات");
        m.put("notifications.mark_all_read", "سب کو پڑھا ہوا نشان زد کریں");
        m.put("notifications.empty", "کوئی اطلاع نہیں");
        m.put("notifications.live_updates_unavailable",
              "فوری تازہ کاری دستیاب نہیں ہے۔ نئی اطلاعات دیکھنے کے لیے یہ فہرست دوبارہ کھولیں۔");
        m.put("notifications.time.just_now", "ابھی");
        m.put("notifications.time.minutes_ago", "{{count}} منٹ پہلے");
        m.put("notifications.time.hours_ago", "{{count}} گھنٹے پہلے");
        m.put("notifications.time.days_ago", "{{count}} دن پہلے");
        m.put("notifications.type.assignment", "سپردگی");
        m.put("notifications.type.new_assignment", "نئی سپردگی");
        m.put("notifications.type.reassignment", "دوبارہ سپردگی");
        m.put("notifications.type.transfer_in", "منتقل ہو کر آیا");
        m.put("notifications.type.transfer_pending", "منتقلی زیرِ التوا");
        m.put("notifications.type.transfer_request", "منتقلی کی درخواست");
        m.put("notifications.type.pending_3day", "3 دن سے زیرِ التوا");
        m.put("notifications.type.pending_5day", "5 دن سے زیرِ التوا");
        m.put("notifications.type.duplicate_detected", "ممکنہ نقل");
        m.put("notifications.type.sent_back", "واپس بھیج دیا گیا");
        m.put("notifications.type.bulk_close", "اجتماعی اختتام");
        m.put("notifications.type.on_leave_pending", "افسر رخصت پر");
        m.put("notifications.type.no_record_assigned", "کوئی ریکارڈ سپرد نہیں کیا گیا");
        m.put("notifications.type.no_reassigned_to_rbi", "ریزرو بینک آف انڈیا کو واپس");
        m.put("notifications.type.no_status_stale", "حالت تازہ نہیں کی گئی");
        m.put("notifications.type.re_response", "ادارے کا جواب");
        m.put("notifications.type.re_update", "ادارے کی تازہ کاری");
        m.put("notifications.type.complaint_closed", "شکایت بند");
        m.put("notifications.type.meeting_scheduled", "اجلاس مقرر");
        m.put("notifications.type.award_passed", "ایوارڈ جاری");
        m.put("notifications.type.decision", "فیصلہ");
        m.put("notifications.type.advisory_complied", "ہدایت پر عمل");
        m.put("notifications.type.document_uploaded", "دستاویز اپ لوڈ ہو گئی");
        m.put("notifications.type.upload_link_sent", "اپ لوڈ لنک بھیج دیا گیا");
        m.put("notifications.type.crpc_toll_free_reminder", "ٹول فری یاد دہانی");
        m.put("notifications.type.ria_legal_update", "قانونی تازہ کاری");
        m.put("notifications.type.escalation", "بالا سطح پر ارسال");
        m.put("notifications.type.configuration_alert", "ترتیبات کی تنبیہ");
        m.put("notifications.type.query", "استفسار");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "ಅಧಿಸೂಚನೆಗಳು");
        m.put("notifications.mark_all_read", "ಎಲ್ಲವನ್ನೂ ಓದಿದಂತೆ ಗುರುತಿಸಿ");
        m.put("notifications.empty", "ಯಾವುದೇ ಅಧಿಸೂಚನೆಗಳಿಲ್ಲ");
        m.put("notifications.live_updates_unavailable",
              "ತತ್‌ಕ್ಷಣದ ನವೀಕರಣಗಳು ಲಭ್ಯವಿಲ್ಲ. ಹೊಸ ಅಂಶಗಳನ್ನು ನೋಡಲು ಈ ಪಟ್ಟಿಯನ್ನು ಮತ್ತೆ ತೆರೆಯಿರಿ.");
        m.put("notifications.time.just_now", "ಈಗಷ್ಟೇ");
        m.put("notifications.time.minutes_ago", "{{count}} ನಿಮಿಷಗಳ ಹಿಂದೆ");
        m.put("notifications.time.hours_ago", "{{count}} ಗಂಟೆಗಳ ಹಿಂದೆ");
        m.put("notifications.time.days_ago", "{{count}} ದಿನಗಳ ಹಿಂದೆ");
        m.put("notifications.type.assignment", "ವಹಿಸುವಿಕೆ");
        m.put("notifications.type.new_assignment", "ಹೊಸ ವಹಿಸುವಿಕೆ");
        m.put("notifications.type.reassignment", "ಮರುವಹಿಸುವಿಕೆ");
        m.put("notifications.type.transfer_in", "ವರ್ಗಾವಣೆಯಾಗಿ ಬಂದಿದೆ");
        m.put("notifications.type.transfer_pending", "ವರ್ಗಾವಣೆ ಬಾಕಿ");
        m.put("notifications.type.transfer_request", "ವರ್ಗಾವಣೆ ಕೋರಿಕೆ");
        m.put("notifications.type.pending_3day", "3 ದಿನಗಳಿಂದ ಬಾಕಿ");
        m.put("notifications.type.pending_5day", "5 ದಿನಗಳಿಂದ ಬಾಕಿ");
        m.put("notifications.type.duplicate_detected", "ಸಂಭಾವ್ಯ ನಕಲು");
        m.put("notifications.type.sent_back", "ಹಿಂದಿರುಗಿಸಲಾಗಿದೆ");
        m.put("notifications.type.bulk_close", "ಸಮೂಹ ಇತ್ಯರ್ಥ");
        m.put("notifications.type.on_leave_pending", "ಅಧಿಕಾರಿ ರಜೆಯಲ್ಲಿ");
        m.put("notifications.type.no_record_assigned", "ಯಾವುದೇ ದಾಖಲೆ ವಹಿಸಲಾಗಿಲ್ಲ");
        m.put("notifications.type.no_reassigned_to_rbi", "ಭಾರತೀಯ ರಿಸರ್ವ್ ಬ್ಯಾಂಕಿಗೆ ಹಿಂದಿರುಗಿಸಲಾಗಿದೆ");
        m.put("notifications.type.no_status_stale", "ಸ್ಥಿತಿ ನವೀಕರಿಸಿಲ್ಲ");
        m.put("notifications.type.re_response", "ಸಂಸ್ಥೆಯ ಪ್ರತಿಕ್ರಿಯೆ");
        m.put("notifications.type.re_update", "ಸಂಸ್ಥೆಯ ನವೀಕರಣ");
        m.put("notifications.type.complaint_closed", "ದೂರು ಮುಕ್ತಾಯ");
        m.put("notifications.type.meeting_scheduled", "ಸಭೆ ನಿಗದಿ");
        m.put("notifications.type.award_passed", "ತೀರ್ಪು ಪ್ರಕಟ");
        m.put("notifications.type.decision", "ನಿರ್ಧಾರ");
        m.put("notifications.type.advisory_complied", "ಸಲಹೆ ಪಾಲನೆ");
        m.put("notifications.type.document_uploaded", "ದಾಖಲೆ ಅಪ್‌ಲೋಡ್ ಆಗಿದೆ");
        m.put("notifications.type.upload_link_sent", "ಅಪ್‌ಲೋಡ್ ಕೊಂಡಿ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("notifications.type.crpc_toll_free_reminder", "ಶುಲ್ಕರಹಿತ ಕರೆ ಜ್ಞಾಪನೆ");
        m.put("notifications.type.ria_legal_update", "ಕಾನೂನು ನವೀಕರಣ");
        m.put("notifications.type.escalation", "ಮೇಲಿನ ಹಂತಕ್ಕೆ ಸಲ್ಲಿಕೆ");
        m.put("notifications.type.configuration_alert", "ಸಂರಚನೆ ಎಚ್ಚರಿಕೆ");
        m.put("notifications.type.query", "ಪ್ರಶ್ನೆ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("notifications.title", "അറിയിപ്പുകൾ");
        m.put("notifications.mark_all_read", "എല്ലാം വായിച്ചതായി അടയാളപ്പെടുത്തുക");
        m.put("notifications.empty", "അറിയിപ്പുകൾ ഇല്ല");
        m.put("notifications.live_updates_unavailable",
              "തത്സമയ പുതുക്കലുകൾ ലഭ്യമല്ല. പുതിയ അറിയിപ്പുകൾ കാണാൻ ഈ പട്ടിക വീണ്ടും തുറക്കുക.");
        m.put("notifications.time.just_now", "ഇപ്പോൾ തന്നെ");
        m.put("notifications.time.minutes_ago", "{{count}} മിനിറ്റ് മുൻപ്");
        m.put("notifications.time.hours_ago", "{{count}} മണിക്കൂർ മുൻപ്");
        m.put("notifications.time.days_ago", "{{count}} ദിവസം മുൻപ്");
        m.put("notifications.type.assignment", "ചുമതല");
        m.put("notifications.type.new_assignment", "പുതിയ ചുമതല");
        m.put("notifications.type.reassignment", "പുനർചുമതല");
        m.put("notifications.type.transfer_in", "കൈമാറി ലഭിച്ചു");
        m.put("notifications.type.transfer_pending", "കൈമാറ്റം തീർപ്പാകാത്തത്");
        m.put("notifications.type.transfer_request", "കൈമാറ്റ അഭ്യർത്ഥന");
        m.put("notifications.type.pending_3day", "3 ദിവസമായി തീർപ്പാകാത്തത്");
        m.put("notifications.type.pending_5day", "5 ദിവസമായി തീർപ്പാകാത്തത്");
        m.put("notifications.type.duplicate_detected", "ഇരട്ടിപ്പ് സാധ്യത");
        m.put("notifications.type.sent_back", "തിരികെ അയച്ചു");
        m.put("notifications.type.bulk_close", "കൂട്ടത്തോടെ തീർപ്പാക്കൽ");
        m.put("notifications.type.on_leave_pending", "ഓഫീസർ അവധിയിൽ");
        m.put("notifications.type.no_record_assigned", "ഒരു രേഖയും നൽകിയിട്ടില്ല");
        m.put("notifications.type.no_reassigned_to_rbi", "റിസർവ് ബാങ്കിന് തിരികെ അയച്ചു");
        m.put("notifications.type.no_status_stale", "നില പുതുക്കിയിട്ടില്ല");
        m.put("notifications.type.re_response", "സ്ഥാപനത്തിന്റെ മറുപടി");
        m.put("notifications.type.re_update", "സ്ഥാപനത്തിന്റെ പുതുക്കൽ");
        m.put("notifications.type.complaint_closed", "പരാതി തീർപ്പാക്കി");
        m.put("notifications.type.meeting_scheduled", "യോഗം നിശ്ചയിച്ചു");
        m.put("notifications.type.award_passed", "വിധി പ്രഖ്യാപിച്ചു");
        m.put("notifications.type.decision", "തീരുമാനം");
        m.put("notifications.type.advisory_complied", "ഉപദേശം പാലിച്ചു");
        m.put("notifications.type.document_uploaded", "രേഖ അപ്‌ലോഡ് ചെയ്തു");
        m.put("notifications.type.upload_link_sent", "അപ്‌ലോഡ് ലിങ്ക് അയച്ചു");
        m.put("notifications.type.crpc_toll_free_reminder", "ടോൾ-ഫ്രീ ഓർമ്മപ്പെടുത്തൽ");
        m.put("notifications.type.ria_legal_update", "നിയമപരമായ പുതുക്കൽ");
        m.put("notifications.type.escalation", "ഉയർന്ന തലത്തിലേക്ക് കൈമാറൽ");
        m.put("notifications.type.configuration_alert", "ക്രമീകരണ മുന്നറിയിപ്പ്");
        m.put("notifications.type.query", "ചോദ്യം");
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
