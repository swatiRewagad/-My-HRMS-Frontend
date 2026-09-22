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
 * Complaint/appeal status labels for the shared status badge (UST847).
 *
 * These keys replace per-component {@code getStatusLabel} maps that each portal kept privately. The
 * English defaults below are lifted verbatim from those maps so the migration does not silently
 * reword the UI — CEPC in particular used domain wording ("Under Examination" for in_progress,
 * "Forwarded to Dept" for forwarded) that a generic humanised fallback would have thrown away.
 *
 * <p>Where two portals disagreed on the same status the CEPC/general wording wins, because the
 * badge now renders identically on the RE and RBI sides by design and cannot carry two labels for
 * one value. The one case that matters: RE's dashboard said "Pending Response" where CEPC said
 * "Pending"; "Pending Response" is kept, since it is the more informative of the two and is still
 * accurate on the RBI side.
 *
 * Keyed {@code status.<lowercased status value>} to match what StatusBadgeComponent derives when no
 * explicit labelKey is supplied.
 */
@Component
@Order(8)
public class StatusVocabularyTranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public StatusVocabularyTranslationSeeder(TranslationKeyRepository keyRepo,
                                            TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        english().forEach(this::seed);
        seedLocale("hi", hindi());
        seedLocale("bn", bengali());
        seedLocale("mr", marathi());
        seedLocale("te", telugu());
        seedLocale("ta", tamil());
        seedLocale("gu", gujarati());
        seedLocale("ur", urdu());
        seedLocale("kn", kannada());
        seedLocale("ml", malayalam());
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        // ═══ Complaint workflow (CEPC/RBIO wording preserved) ═══
        m.put("status.new", "New Complaint");
        m.put("status.pending", "Pending Response");
        m.put("status.draft", "Draft");
        m.put("status.assigned", "Assigned");
        m.put("status.in_progress", "Under Examination");
        m.put("status.under_review", "Under Review");
        m.put("status.reviewer_review", "Reviewer Review");
        m.put("status.incharge_review", "In Charge Review");
        m.put("status.awaiting_closure", "Awaiting Closure");
        m.put("status.escalated", "Escalated");
        m.put("status.sent_back", "Sent Back");
        m.put("status.returned", "Returned");
        m.put("status.info_requested", "Info Requested");
        m.put("status.information_required", "Information Required");
        m.put("status.forwarded", "Forwarded to Dept");
        m.put("status.forwarded_external", "Forwarded Externally");
        m.put("status.forwarded_to_contact", "With Contact Person");
        m.put("status.re_responded", "Entity Responded");
        m.put("status.responded", "Responded");
        m.put("status.breached", "Breached");
        m.put("status.extension_requested", "Extension Requested");
        m.put("status.clarification_requested", "Clarification Requested");
        m.put("status.conciliation", "Conciliation");
        m.put("status.conciliated", "Conciliated");
        m.put("status.adjudication", "Adjudication");
        m.put("status.adjudicated", "Adjudicated");
        m.put("status.advisory_issued", "Advisory Issued");
        m.put("status.resolved", "Resolved");
        m.put("status.approved", "Approved");
        m.put("status.rejected", "Rejected");
        m.put("status.closed", "Closed");
        m.put("status.withdrawn", "Withdrawn");
        m.put("status.non_maintainable", "Non Maintainable");
        // ═══ Appeal workflow (AA wording preserved) ═══
        m.put("status.filed", "Filed");
        m.put("status.accepted", "Accepted");
        m.put("status.hearing_scheduled", "Hearing Scheduled");
        m.put("status.hearing_completed", "Hearing Completed");
        m.put("status.order_reserved", "Order Reserved");
        m.put("status.order_passed", "Order Passed");
        m.put("status.remanded", "Remanded");
        m.put("status.dismissed", "Dismissed");
        m.put("status.documents_requested", "Documents Requested");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "नई शिकायत");
        m.put("status.pending", "उत्तर प्रतीक्षित");
        m.put("status.draft", "प्रारूप");
        m.put("status.assigned", "सौंपा गया");
        m.put("status.in_progress", "जाँच में");
        m.put("status.under_review", "समीक्षाधीन");
        m.put("status.reviewer_review", "समीक्षक समीक्षा");
        m.put("status.incharge_review", "प्रभारी समीक्षा");
        m.put("status.awaiting_closure", "समापन प्रतीक्षित");
        m.put("status.escalated", "उच्चस्तर पर भेजा गया");
        m.put("status.sent_back", "वापस भेजा गया");
        m.put("status.returned", "लौटाया गया");
        m.put("status.info_requested", "जानकारी मांगी गई");
        m.put("status.information_required", "जानकारी आवश्यक");
        m.put("status.forwarded", "विभाग को अग्रेषित");
        m.put("status.forwarded_external", "बाहर अग्रेषित");
        m.put("status.forwarded_to_contact", "संपर्क व्यक्ति के पास");
        m.put("status.re_responded", "संस्था ने उत्तर दिया");
        m.put("status.responded", "उत्तर दिया गया");
        m.put("status.breached", "उल्लंघन");
        m.put("status.extension_requested", "अवधि विस्तार अनुरोध");
        m.put("status.clarification_requested", "स्पष्टीकरण मांगा गया");
        m.put("status.conciliation", "सुलह");
        m.put("status.conciliated", "सुलह हुई");
        m.put("status.adjudication", "अधिनिर्णय");
        m.put("status.adjudicated", "अधिनिर्णीत");
        m.put("status.advisory_issued", "परामर्श जारी");
        m.put("status.resolved", "समाधान हुआ");
        m.put("status.approved", "स्वीकृत");
        m.put("status.rejected", "अस्वीकृत");
        m.put("status.closed", "बंद");
        m.put("status.withdrawn", "वापस लिया गया");
        m.put("status.non_maintainable", "विचारणीय नहीं");
        m.put("status.filed", "दायर");
        m.put("status.accepted", "स्वीकार");
        m.put("status.hearing_scheduled", "सुनवाई निर्धारित");
        m.put("status.hearing_completed", "सुनवाई पूर्ण");
        m.put("status.order_reserved", "आदेश सुरक्षित");
        m.put("status.order_passed", "आदेश पारित");
        m.put("status.remanded", "प्रतिप्रेषित");
        m.put("status.dismissed", "खारिज");
        m.put("status.documents_requested", "दस्तावेज़ मांगे गए");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "নতুন অভিযোগ");
        m.put("status.pending", "উত্তরের অপেক্ষায়");
        m.put("status.draft", "খসড়া");
        m.put("status.assigned", "নিয়োগ করা হয়েছে");
        m.put("status.in_progress", "পরীক্ষাধীন");
        m.put("status.under_review", "পর্যালোচনাধীন");
        m.put("status.reviewer_review", "পর্যালোচক পর্যালোচনা");
        m.put("status.incharge_review", "ইনচার্জ পর্যালোচনা");
        m.put("status.awaiting_closure", "নিষ্পত্তির অপেক্ষায়");
        m.put("status.escalated", "ঊর্ধ্বতনে প্রেরিত");
        m.put("status.sent_back", "ফেরত পাঠানো হয়েছে");
        m.put("status.returned", "ফেরত এসেছে");
        m.put("status.info_requested", "তথ্য চাওয়া হয়েছে");
        m.put("status.information_required", "তথ্য প্রয়োজন");
        m.put("status.forwarded", "বিভাগে অগ্রেরিত");
        m.put("status.forwarded_external", "বাইরে অগ্রেরিত");
        m.put("status.forwarded_to_contact", "যোগাযোগ ব্যক্তির কাছে");
        m.put("status.re_responded", "সংস্থা উত্তর দিয়েছে");
        m.put("status.responded", "উত্তর দেওয়া হয়েছে");
        m.put("status.breached", "লঙ্ঘিত");
        m.put("status.extension_requested", "সময় বৃদ্ধির অনুরোধ");
        m.put("status.clarification_requested", "ব্যাখ্যা চাওয়া হয়েছে");
        m.put("status.conciliation", "মধ্যস্থতা");
        m.put("status.conciliated", "মধ্যস্থতা সম্পন্ন");
        m.put("status.adjudication", "বিচারনিষ্পত্তি");
        m.put("status.adjudicated", "বিচারনিষ্পত্তি হয়েছে");
        m.put("status.advisory_issued", "পরামর্শ জারি");
        m.put("status.resolved", "নিষ্পত্তি হয়েছে");
        m.put("status.approved", "অনুমোদিত");
        m.put("status.rejected", "প্রত্যাখ্যাত");
        m.put("status.closed", "বন্ধ");
        m.put("status.withdrawn", "প্রত্যাহৃত");
        m.put("status.non_maintainable", "গ্রহণযোগ্য নয়");
        m.put("status.filed", "দাখিল");
        m.put("status.accepted", "গৃহীত");
        m.put("status.hearing_scheduled", "শুনানি নির্ধারিত");
        m.put("status.hearing_completed", "শুনানি সম্পন্ন");
        m.put("status.order_reserved", "আদেশ সংরক্ষিত");
        m.put("status.order_passed", "আদেশ প্রদত্ত");
        m.put("status.remanded", "পুনঃপ্রেরিত");
        m.put("status.dismissed", "খারিজ");
        m.put("status.documents_requested", "নথি চাওয়া হয়েছে");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "नवीन तक्रार");
        m.put("status.pending", "उत्तराच्या प्रतीक्षेत");
        m.put("status.draft", "मसुदा");
        m.put("status.assigned", "नेमून दिले");
        m.put("status.in_progress", "तपासणीत");
        m.put("status.under_review", "पुनरावलोकनाधीन");
        m.put("status.reviewer_review", "समीक्षक पुनरावलोकन");
        m.put("status.incharge_review", "प्रभारी पुनरावलोकन");
        m.put("status.awaiting_closure", "निकालाच्या प्रतीक्षेत");
        m.put("status.escalated", "वरिष्ठांकडे पाठवले");
        m.put("status.sent_back", "परत पाठवले");
        m.put("status.returned", "परत आले");
        m.put("status.info_requested", "माहिती मागवली");
        m.put("status.information_required", "माहिती आवश्यक");
        m.put("status.forwarded", "विभागाकडे पाठवले");
        m.put("status.forwarded_external", "बाहेर पाठवले");
        m.put("status.forwarded_to_contact", "संपर्क व्यक्तीकडे");
        m.put("status.re_responded", "संस्थेने उत्तर दिले");
        m.put("status.responded", "उत्तर दिले");
        m.put("status.breached", "उल्लंघन");
        m.put("status.extension_requested", "मुदतवाढ विनंती");
        m.put("status.clarification_requested", "स्पष्टीकरण मागवले");
        m.put("status.conciliation", "समेट");
        m.put("status.conciliated", "समेट झाला");
        m.put("status.adjudication", "न्यायनिर्णय");
        m.put("status.adjudicated", "न्यायनिर्णय झाला");
        m.put("status.advisory_issued", "सल्ला जारी");
        m.put("status.resolved", "निराकरण झाले");
        m.put("status.approved", "मंजूर");
        m.put("status.rejected", "नाकारले");
        m.put("status.closed", "बंद");
        m.put("status.withdrawn", "मागे घेतले");
        m.put("status.non_maintainable", "विचारार्ह नाही");
        m.put("status.filed", "दाखल");
        m.put("status.accepted", "स्वीकारले");
        m.put("status.hearing_scheduled", "सुनावणी निश्चित");
        m.put("status.hearing_completed", "सुनावणी पूर्ण");
        m.put("status.order_reserved", "आदेश राखीव");
        m.put("status.order_passed", "आदेश दिला");
        m.put("status.remanded", "पुन्हा पाठवले");
        m.put("status.dismissed", "फेटाळले");
        m.put("status.documents_requested", "कागदपत्रे मागवली");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "కొత్త ఫిర్యాదు");
        m.put("status.pending", "సమాధానం పెండింగ్");
        m.put("status.draft", "ముసాయిదా");
        m.put("status.assigned", "కేటాయించారు");
        m.put("status.in_progress", "పరిశీలనలో");
        m.put("status.under_review", "సమీక్షలో");
        m.put("status.reviewer_review", "సమీక్షకుని సమీక్ష");
        m.put("status.incharge_review", "ఇన్‌చార్జి సమీక్ష");
        m.put("status.awaiting_closure", "ముగింపు కోసం వేచి ఉంది");
        m.put("status.escalated", "పైస్థాయికి పంపారు");
        m.put("status.sent_back", "వెనక్కి పంపారు");
        m.put("status.returned", "తిరిగి వచ్చింది");
        m.put("status.info_requested", "సమాచారం అడిగారు");
        m.put("status.information_required", "సమాచారం అవసరం");
        m.put("status.forwarded", "శాఖకు పంపారు");
        m.put("status.forwarded_external", "బయటకు పంపారు");
        m.put("status.forwarded_to_contact", "సంప్రదింపు వ్యక్తి వద్ద");
        m.put("status.re_responded", "సంస్థ సమాధానం ఇచ్చింది");
        m.put("status.responded", "సమాధానం ఇచ్చారు");
        m.put("status.breached", "ఉల్లంఘన");
        m.put("status.extension_requested", "గడువు పొడిగింపు అభ్యర్థన");
        m.put("status.clarification_requested", "వివరణ అడిగారు");
        m.put("status.conciliation", "రాజీ");
        m.put("status.conciliated", "రాజీ కుదిరింది");
        m.put("status.adjudication", "తీర్పు");
        m.put("status.adjudicated", "తీర్పు ఇచ్చారు");
        m.put("status.advisory_issued", "సలహా జారీ");
        m.put("status.resolved", "పరిష్కరించారు");
        m.put("status.approved", "ఆమోదించారు");
        m.put("status.rejected", "తిరస్కరించారు");
        m.put("status.closed", "మూసివేశారు");
        m.put("status.withdrawn", "ఉపసంహరించారు");
        m.put("status.non_maintainable", "విచారణార్హం కాదు");
        m.put("status.filed", "దాఖలు");
        m.put("status.accepted", "అంగీకరించారు");
        m.put("status.hearing_scheduled", "విచారణ నిర్ణయించారు");
        m.put("status.hearing_completed", "విచారణ పూర్తి");
        m.put("status.order_reserved", "ఉత్తర్వు రిజర్వ్");
        m.put("status.order_passed", "ఉత్తర్వు జారీ");
        m.put("status.remanded", "తిరిగి పంపారు");
        m.put("status.dismissed", "కొట్టివేశారు");
        m.put("status.documents_requested", "పత్రాలు అడిగారు");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "புதிய புகார்");
        m.put("status.pending", "பதிலுக்குக் காத்திருக்கிறது");
        m.put("status.draft", "வரைவு");
        m.put("status.assigned", "ஒப்படைக்கப்பட்டது");
        m.put("status.in_progress", "பரிசீலனையில்");
        m.put("status.under_review", "மறுஆய்வில்");
        m.put("status.reviewer_review", "மறுஆய்வாளர் ஆய்வு");
        m.put("status.incharge_review", "பொறுப்பாளர் ஆய்வு");
        m.put("status.awaiting_closure", "முடிவுக்குக் காத்திருக்கிறது");
        m.put("status.escalated", "மேல்நிலைக்கு அனுப்பப்பட்டது");
        m.put("status.sent_back", "திரும்ப அனுப்பப்பட்டது");
        m.put("status.returned", "திரும்பியது");
        m.put("status.info_requested", "தகவல் கோரப்பட்டது");
        m.put("status.information_required", "தகவல் தேவை");
        m.put("status.forwarded", "துறைக்கு அனுப்பப்பட்டது");
        m.put("status.forwarded_external", "வெளியே அனுப்பப்பட்டது");
        m.put("status.forwarded_to_contact", "தொடர்பு நபரிடம்");
        m.put("status.re_responded", "நிறுவனம் பதிலளித்தது");
        m.put("status.responded", "பதிலளிக்கப்பட்டது");
        m.put("status.breached", "மீறல்");
        m.put("status.extension_requested", "கால நீட்டிப்புக் கோரிக்கை");
        m.put("status.clarification_requested", "விளக்கம் கோரப்பட்டது");
        m.put("status.conciliation", "சமரசம்");
        m.put("status.conciliated", "சமரசம் ஆனது");
        m.put("status.adjudication", "தீர்ப்பு");
        m.put("status.adjudicated", "தீர்ப்பளிக்கப்பட்டது");
        m.put("status.advisory_issued", "ஆலோசனை வழங்கப்பட்டது");
        m.put("status.resolved", "தீர்க்கப்பட்டது");
        m.put("status.approved", "அனுமதிக்கப்பட்டது");
        m.put("status.rejected", "நிராகரிக்கப்பட்டது");
        m.put("status.closed", "மூடப்பட்டது");
        m.put("status.withdrawn", "திரும்பப் பெறப்பட்டது");
        m.put("status.non_maintainable", "விசாரணைக்கு உரியதல்ல");
        m.put("status.filed", "தாக்கல் செய்யப்பட்டது");
        m.put("status.accepted", "ஏற்கப்பட்டது");
        m.put("status.hearing_scheduled", "விசாரணை நிர்ணயிக்கப்பட்டது");
        m.put("status.hearing_completed", "விசாரணை நிறைவு");
        m.put("status.order_reserved", "உத்தரவு ஒதுக்கப்பட்டது");
        m.put("status.order_passed", "உத்தரவு பிறப்பிக்கப்பட்டது");
        m.put("status.remanded", "மீண்டும் அனுப்பப்பட்டது");
        m.put("status.dismissed", "தள்ளுபடி");
        m.put("status.documents_requested", "ஆவணங்கள் கோரப்பட்டன");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "નવી ફરિયાદ");
        m.put("status.pending", "જવાબની પ્રતીક્ષામાં");
        m.put("status.draft", "ડ્રાફ્ટ");
        m.put("status.assigned", "સોંપ્યું");
        m.put("status.in_progress", "તપાસ હેઠળ");
        m.put("status.under_review", "સમીક્ષા હેઠળ");
        m.put("status.reviewer_review", "સમીક્ષક સમીક્ષા");
        m.put("status.incharge_review", "પ્રભારી સમીક્ષા");
        m.put("status.awaiting_closure", "સમાપનની પ્રતીક્ષામાં");
        m.put("status.escalated", "ઉચ્ચ સ્તરે મોકલ્યું");
        m.put("status.sent_back", "પરત મોકલ્યું");
        m.put("status.returned", "પરત આવ્યું");
        m.put("status.info_requested", "માહિતી માંગી");
        m.put("status.information_required", "માહિતી આવશ્યક");
        m.put("status.forwarded", "વિભાગને મોકલ્યું");
        m.put("status.forwarded_external", "બહાર મોકલ્યું");
        m.put("status.forwarded_to_contact", "સંપર્ક વ્યક્તિ પાસે");
        m.put("status.re_responded", "સંસ્થાએ જવાબ આપ્યો");
        m.put("status.responded", "જવાબ આપ્યો");
        m.put("status.breached", "ઉલ્લંઘન");
        m.put("status.extension_requested", "સમય વધારાની વિનંતી");
        m.put("status.clarification_requested", "સ્પષ્ટતા માંગી");
        m.put("status.conciliation", "સમાધાન");
        m.put("status.conciliated", "સમાધાન થયું");
        m.put("status.adjudication", "ન્યાયનિર્ણય");
        m.put("status.adjudicated", "ન્યાયનિર્ણય થયો");
        m.put("status.advisory_issued", "સલાહ જારી");
        m.put("status.resolved", "નિરાકરણ થયું");
        m.put("status.approved", "મંજૂર");
        m.put("status.rejected", "નકારેલ");
        m.put("status.closed", "બંધ");
        m.put("status.withdrawn", "પાછું ખેંચ્યું");
        m.put("status.non_maintainable", "વિચારણાપાત્ર નથી");
        m.put("status.filed", "દાખલ");
        m.put("status.accepted", "સ્વીકૃત");
        m.put("status.hearing_scheduled", "સુનાવણી નિર્ધારિત");
        m.put("status.hearing_completed", "સુનાવણી પૂર્ણ");
        m.put("status.order_reserved", "આદેશ અનામત");
        m.put("status.order_passed", "આદેશ પસાર");
        m.put("status.remanded", "પુનઃ મોકલ્યું");
        m.put("status.dismissed", "કાઢી નાખ્યું");
        m.put("status.documents_requested", "દસ્તાવેજો માંગ્યા");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "نئی شکایت");
        m.put("status.pending", "جواب کے منتظر");
        m.put("status.draft", "مسودہ");
        m.put("status.assigned", "سونپا گیا");
        m.put("status.in_progress", "زیرِ تفتیش");
        m.put("status.under_review", "زیرِ جائزہ");
        m.put("status.reviewer_review", "جائزہ کار کا جائزہ");
        m.put("status.incharge_review", "انچارج کا جائزہ");
        m.put("status.awaiting_closure", "اختتام کے منتظر");
        m.put("status.escalated", "بالا سطح پر بھیجا گیا");
        m.put("status.sent_back", "واپس بھیجا گیا");
        m.put("status.returned", "واپس آیا");
        m.put("status.info_requested", "معلومات طلب کی گئیں");
        m.put("status.information_required", "معلومات درکار");
        m.put("status.forwarded", "شعبے کو ارسال");
        m.put("status.forwarded_external", "باہر ارسال");
        m.put("status.forwarded_to_contact", "رابطہ کار کے پاس");
        m.put("status.re_responded", "ادارے نے جواب دیا");
        m.put("status.responded", "جواب دیا گیا");
        m.put("status.breached", "خلاف ورزی");
        m.put("status.extension_requested", "مہلت میں توسیع کی درخواست");
        m.put("status.clarification_requested", "وضاحت طلب کی گئی");
        m.put("status.conciliation", "مصالحت");
        m.put("status.conciliated", "مصالحت ہو گئی");
        m.put("status.adjudication", "فیصلہ سازی");
        m.put("status.adjudicated", "فیصلہ ہو گیا");
        m.put("status.advisory_issued", "ہدایت جاری");
        m.put("status.resolved", "حل ہو گیا");
        m.put("status.approved", "منظور");
        m.put("status.rejected", "مسترد");
        m.put("status.closed", "بند");
        m.put("status.withdrawn", "واپس لیا گیا");
        m.put("status.non_maintainable", "قابلِ سماعت نہیں");
        m.put("status.filed", "دائر");
        m.put("status.accepted", "قبول");
        m.put("status.hearing_scheduled", "سماعت مقرر");
        m.put("status.hearing_completed", "سماعت مکمل");
        m.put("status.order_reserved", "حکم محفوظ");
        m.put("status.order_passed", "حکم جاری");
        m.put("status.remanded", "دوبارہ بھیجا گیا");
        m.put("status.dismissed", "خارج");
        m.put("status.documents_requested", "دستاویزات طلب کی گئیں");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "ಹೊಸ ದೂರು");
        m.put("status.pending", "ಪ್ರತಿಕ್ರಿಯೆ ಬಾಕಿ");
        m.put("status.draft", "ಕರಡು");
        m.put("status.assigned", "ನಿಯೋಜಿಸಲಾಗಿದೆ");
        m.put("status.in_progress", "ಪರಿಶೀಲನೆಯಲ್ಲಿ");
        m.put("status.under_review", "ಸಮೀಕ್ಷೆಯಲ್ಲಿ");
        m.put("status.reviewer_review", "ಸಮೀಕ್ಷಕರ ಪರಿಶೀಲನೆ");
        m.put("status.incharge_review", "ಪ್ರಭಾರಿ ಪರಿಶೀಲನೆ");
        m.put("status.awaiting_closure", "ಮುಕ್ತಾಯಕ್ಕೆ ಕಾಯುತ್ತಿದೆ");
        m.put("status.escalated", "ಮೇಲ್ಮಟ್ಟಕ್ಕೆ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("status.sent_back", "ಹಿಂತಿರುಗಿಸಲಾಗಿದೆ");
        m.put("status.returned", "ಹಿಂತಿರುಗಿದೆ");
        m.put("status.info_requested", "ಮಾಹಿತಿ ಕೋರಲಾಗಿದೆ");
        m.put("status.information_required", "ಮಾಹಿತಿ ಅಗತ್ಯ");
        m.put("status.forwarded", "ಇಲಾಖೆಗೆ ರವಾನಿಸಲಾಗಿದೆ");
        m.put("status.forwarded_external", "ಹೊರಗೆ ರವಾನಿಸಲಾಗಿದೆ");
        m.put("status.forwarded_to_contact", "ಸಂಪರ್ಕ ವ್ಯಕ್ತಿಯ ಬಳಿ");
        m.put("status.re_responded", "ಸಂಸ್ಥೆ ಪ್ರತಿಕ್ರಿಯಿಸಿದೆ");
        m.put("status.responded", "ಪ್ರತಿಕ್ರಿಯಿಸಲಾಗಿದೆ");
        m.put("status.breached", "ಉಲ್ಲಂಘನೆ");
        m.put("status.extension_requested", "ಗಡುವು ವಿಸ್ತರಣೆ ಕೋರಿಕೆ");
        m.put("status.clarification_requested", "ಸ್ಪಷ್ಟೀಕರಣ ಕೋರಲಾಗಿದೆ");
        m.put("status.conciliation", "ಸಂಧಾನ");
        m.put("status.conciliated", "ಸಂಧಾನವಾಗಿದೆ");
        m.put("status.adjudication", "ತೀರ್ಪು");
        m.put("status.adjudicated", "ತೀರ್ಪು ನೀಡಲಾಗಿದೆ");
        m.put("status.advisory_issued", "ಸಲಹೆ ಜಾರಿ");
        m.put("status.resolved", "ಪರಿಹರಿಸಲಾಗಿದೆ");
        m.put("status.approved", "ಅನುಮೋದಿಸಲಾಗಿದೆ");
        m.put("status.rejected", "ತಿರಸ್ಕರಿಸಲಾಗಿದೆ");
        m.put("status.closed", "ಮುಚ್ಚಲಾಗಿದೆ");
        m.put("status.withdrawn", "ಹಿಂಪಡೆಯಲಾಗಿದೆ");
        m.put("status.non_maintainable", "ವಿಚಾರಣೆಗೆ ಅರ್ಹವಲ್ಲ");
        m.put("status.filed", "ಸಲ್ಲಿಸಲಾಗಿದೆ");
        m.put("status.accepted", "ಸ್ವೀಕರಿಸಲಾಗಿದೆ");
        m.put("status.hearing_scheduled", "ವಿಚಾರಣೆ ನಿಗದಿ");
        m.put("status.hearing_completed", "ವಿಚಾರಣೆ ಪೂರ್ಣ");
        m.put("status.order_reserved", "ಆದೇಶ ಕಾಯ್ದಿರಿಸಲಾಗಿದೆ");
        m.put("status.order_passed", "ಆದೇಶ ಹೊರಡಿಸಲಾಗಿದೆ");
        m.put("status.remanded", "ಮರಳಿ ಕಳುಹಿಸಲಾಗಿದೆ");
        m.put("status.dismissed", "ವಜಾಗೊಳಿಸಲಾಗಿದೆ");
        m.put("status.documents_requested", "ದಾಖಲೆಗಳನ್ನು ಕೋರಲಾಗಿದೆ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status.new", "പുതിയ പരാതി");
        m.put("status.pending", "മറുപടി കാത്തിരിക്കുന്നു");
        m.put("status.draft", "കരട്");
        m.put("status.assigned", "നിയോഗിച്ചു");
        m.put("status.in_progress", "പരിശോധനയിൽ");
        m.put("status.under_review", "സമീക്ഷയിൽ");
        m.put("status.reviewer_review", "സമീക്ഷകന്റെ പരിശോധന");
        m.put("status.incharge_review", "ചുമതലക്കാരന്റെ പരിശോധന");
        m.put("status.awaiting_closure", "അവസാനിപ്പിക്കാൻ കാത്തിരിക്കുന്നു");
        m.put("status.escalated", "ഉയർന്ന തലത്തിലേക്ക് അയച്ചു");
        m.put("status.sent_back", "തിരികെ അയച്ചു");
        m.put("status.returned", "തിരികെ വന്നു");
        m.put("status.info_requested", "വിവരം ആവശ്യപ്പെട്ടു");
        m.put("status.information_required", "വിവരം ആവശ്യമാണ്");
        m.put("status.forwarded", "വിഭാഗത്തിലേക്ക് കൈമാറി");
        m.put("status.forwarded_external", "പുറത്തേക്ക് കൈമാറി");
        m.put("status.forwarded_to_contact", "ബന്ധപ്പെടേണ്ട വ്യക്തിയുടെ പക്കൽ");
        m.put("status.re_responded", "സ്ഥാപനം മറുപടി നൽകി");
        m.put("status.responded", "മറുപടി നൽകി");
        m.put("status.breached", "ലംഘനം");
        m.put("status.extension_requested", "സമയപരിധി നീട്ടാൻ അഭ്യർത്ഥന");
        m.put("status.clarification_requested", "വിശദീകരണം ആവശ്യപ്പെട്ടു");
        m.put("status.conciliation", "അനുരഞ്ജനം");
        m.put("status.conciliated", "അനുരഞ്ജനം നടന്നു");
        m.put("status.adjudication", "വിധിനിർണ്ണയം");
        m.put("status.adjudicated", "വിധി പ്രഖ്യാപിച്ചു");
        m.put("status.advisory_issued", "നിർദ്ദേശം നൽകി");
        m.put("status.resolved", "പരിഹരിച്ചു");
        m.put("status.approved", "അനുവദിച്ചു");
        m.put("status.rejected", "നിരസിച്ചു");
        m.put("status.closed", "അടച്ചു");
        m.put("status.withdrawn", "പിൻവലിച്ചു");
        m.put("status.non_maintainable", "പരിഗണനാർഹമല്ല");
        m.put("status.filed", "സമർപ്പിച്ചു");
        m.put("status.accepted", "സ്വീകരിച്ചു");
        m.put("status.hearing_scheduled", "വാദം നിശ്ചയിച്ചു");
        m.put("status.hearing_completed", "വാദം പൂർത്തിയായി");
        m.put("status.order_reserved", "ഉത്തരവ് മാറ്റിവച്ചു");
        m.put("status.order_passed", "ഉത്തരവ് പുറപ്പെടുവിച്ചു");
        m.put("status.remanded", "തിരികെ അയച്ചു");
        m.put("status.dismissed", "തള്ളി");
        m.put("status.documents_requested", "രേഖകൾ ആവശ്യപ്പെട്ടു");
        return m;
    }

    private void seed(String code, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule("status");
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
