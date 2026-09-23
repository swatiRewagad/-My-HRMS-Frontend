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
 * Column headers and controls for the shared task grid.
 *
 * <p>This application is task-based: every role meets a grid of tasks and clicks through to the
 * complaint. Consolidating eighteen hand-rolled tables onto one component means the column headers
 * become shared vocabulary, so they are seeded once here rather than hardcoded per module — which is
 * how CRPC and CEPC ended up with zero localisation in the first place.
 *
 * <p>A NEW seeder at {@code @Order(62)} rather than an edit to UiShellTranslationSeeder (60) or
 * MasterDataFallbackTranslationSeeder (61), per the convention here: concurrent sessions add keys, and
 * a shared file is a guaranteed conflict where a conflict silently costs a locale. 60 and 61 were
 * verified taken before choosing 62.
 *
 * <p>Seeding is insert-if-absent by key code, so a duplicate code anywhere would silently keep the
 * FIRST text. The {@code ui.grid.*} namespace was verified unused before this was written.
 *
 * <p>Punjabi is included: {@code pa} is now a member of SupportedLocale, so a locale omitted here
 * would fall back to English and look like a translation gap rather than an oversight.
 */
@Component
@Order(62)
public class TaskGridTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "ui";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public TaskGridTranslationSeeder(TranslationKeyRepository keyRepo,
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
        seedLocale("pa", punjabi());
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        // ═══ Grid chrome ═══
        m.put("ui.grid.tasks", "Tasks");
        m.put("ui.grid.complaints", "Complaints");
        m.put("ui.grid.appeals", "Appeals");
        m.put("ui.grid.select_all", "Select all rows on this page");
        m.put("ui.grid.select_row", "Select this row");

        // ═══ Shared column headers ═══
        // Deliberately generic: the same complaint field must read the same way in every module's grid.
        m.put("ui.col.complaint_number", "Complaint Number");
        m.put("ui.col.complaint_id", "Complaint Id");
        m.put("ui.col.appeal_number", "Appeal Number");
        m.put("ui.col.subject", "Subject");
        m.put("ui.col.complainant_name", "Complainant");
        m.put("ui.col.entity_name", "Entity");
        m.put("ui.col.category", "Category");
        m.put("ui.col.status", "Status");
        m.put("ui.col.priority", "Priority");
        m.put("ui.col.ageing", "Pending");
        m.put("ui.col.sla_remaining", "SLA (hrs)");
        m.put("ui.col.created_at", "Creation Date");
        m.put("ui.col.updated_at", "Last Updated");
        m.put("ui.col.closed_at", "Closed On");
        m.put("ui.col.assigned_officer", "Assigned Officer");
        m.put("ui.col.assigned_role", "Assigned Role");
        m.put("ui.col.office", "Office");
        m.put("ui.col.mode_of_receipt", "Mode");
        m.put("ui.col.from_email", "From");
        m.put("ui.col.state", "State");
        m.put("ui.col.district", "District");
        m.put("ui.col.deadline", "SLA Due");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "कार्य");
        m.put("ui.grid.complaints", "शिकायतें");
        m.put("ui.grid.appeals", "अपील");
        m.put("ui.grid.select_all", "इस पृष्ठ की सभी पंक्तियाँ चुनें");
        m.put("ui.grid.select_row", "यह पंक्ति चुनें");
        m.put("ui.col.complaint_number", "शिकायत संख्या");
        m.put("ui.col.complaint_id", "शिकायत आईडी");
        m.put("ui.col.appeal_number", "अपील संख्या");
        m.put("ui.col.subject", "विषय");
        m.put("ui.col.complainant_name", "शिकायतकर्ता का नाम");
        m.put("ui.col.entity_name", "संस्था का नाम");
        m.put("ui.col.category", "श्रेणी");
        m.put("ui.col.status", "स्थिति");
        m.put("ui.col.priority", "प्राथमिकता");
        m.put("ui.col.ageing", "लंबित");
        m.put("ui.col.sla_remaining", "एसएलए (घंटे)");
        m.put("ui.col.created_at", "निर्माण तिथि");
        m.put("ui.col.updated_at", "अंतिम अद्यतन");
        m.put("ui.col.closed_at", "बंद होने की तिथि");
        m.put("ui.col.assigned_officer", "नियुक्त अधिकारी");
        m.put("ui.col.assigned_role", "नियुक्त भूमिका");
        m.put("ui.col.office", "कार्यालय");
        m.put("ui.col.mode_of_receipt", "प्राप्ति का माध्यम");
        m.put("ui.col.from_email", "प्रेषक");
        m.put("ui.col.state", "राज्य");
        m.put("ui.col.district", "जिला");
        m.put("ui.col.deadline", "समय-सीमा");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "कार्ये");
        m.put("ui.grid.complaints", "तक्रारी");
        m.put("ui.grid.appeals", "अपील");
        m.put("ui.grid.select_all", "या पृष्ठावरील सर्व ओळी निवडा");
        m.put("ui.grid.select_row", "ही ओळ निवडा");
        m.put("ui.col.complaint_number", "तक्रार क्रमांक");
        m.put("ui.col.complaint_id", "तक्रार आयडी");
        m.put("ui.col.appeal_number", "अपील क्रमांक");
        m.put("ui.col.subject", "विषय");
        m.put("ui.col.complainant_name", "तक्रारदाराचे नाव");
        m.put("ui.col.entity_name", "संस्थेचे नाव");
        m.put("ui.col.category", "श्रेणी");
        m.put("ui.col.status", "स्थिती");
        m.put("ui.col.priority", "प्राधान्य");
        m.put("ui.col.ageing", "प्रलंबित");
        m.put("ui.col.sla_remaining", "एसएलए (तास)");
        m.put("ui.col.created_at", "निर्मिती दिनांक");
        m.put("ui.col.updated_at", "शेवटचे अद्यतन");
        m.put("ui.col.closed_at", "बंद केल्याचा दिनांक");
        m.put("ui.col.assigned_officer", "नियुक्त अधिकारी");
        m.put("ui.col.assigned_role", "नियुक्त भूमिका");
        m.put("ui.col.office", "कार्यालय");
        m.put("ui.col.mode_of_receipt", "प्राप्तीचे माध्यम");
        m.put("ui.col.from_email", "प्रेषक");
        m.put("ui.col.state", "राज्य");
        m.put("ui.col.district", "जिल्हा");
        m.put("ui.col.deadline", "मुदत");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "কার্যসমূহ");
        m.put("ui.grid.complaints", "অভিযোগসমূহ");
        m.put("ui.grid.appeals", "আপিল");
        m.put("ui.grid.select_all", "এই পৃষ্ঠার সব সারি নির্বাচন করুন");
        m.put("ui.grid.select_row", "এই সারি নির্বাচন করুন");
        m.put("ui.col.complaint_number", "অভিযোগ নম্বর");
        m.put("ui.col.complaint_id", "অভিযোগ আইডি");
        m.put("ui.col.appeal_number", "আপিল নম্বর");
        m.put("ui.col.subject", "বিষয়");
        m.put("ui.col.complainant_name", "অভিযোগকারীর নাম");
        m.put("ui.col.entity_name", "সংস্থার নাম");
        m.put("ui.col.category", "শ্রেণি");
        m.put("ui.col.status", "অবস্থা");
        m.put("ui.col.priority", "অগ্রাধিকার");
        m.put("ui.col.ageing", "মুলতুবি");
        m.put("ui.col.sla_remaining", "এসএলএ (ঘণ্টা)");
        m.put("ui.col.created_at", "তৈরির তারিখ");
        m.put("ui.col.updated_at", "সর্বশেষ হালনাগাদ");
        m.put("ui.col.closed_at", "বন্ধের তারিখ");
        m.put("ui.col.assigned_officer", "নিযুক্ত আধিকারিক");
        m.put("ui.col.assigned_role", "নিযুক্ত ভূমিকা");
        m.put("ui.col.office", "কার্যালয়");
        m.put("ui.col.mode_of_receipt", "প্রাপ্তির মাধ্যম");
        m.put("ui.col.from_email", "প্রেরক");
        m.put("ui.col.state", "রাজ্য");
        m.put("ui.col.district", "জেলা");
        m.put("ui.col.deadline", "সময়সীমা");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "పనులు");
        m.put("ui.grid.complaints", "ఫిర్యాదులు");
        m.put("ui.grid.appeals", "అప్పీళ్లు");
        m.put("ui.grid.select_all", "ఈ పేజీలోని అన్ని వరుసలను ఎంచుకోండి");
        m.put("ui.grid.select_row", "ఈ వరుసను ఎంచుకోండి");
        m.put("ui.col.complaint_number", "ఫిర్యాదు సంఖ్య");
        m.put("ui.col.complaint_id", "ఫిర్యాదు ఐడీ");
        m.put("ui.col.appeal_number", "అప్పీలు సంఖ్య");
        m.put("ui.col.subject", "విషయం");
        m.put("ui.col.complainant_name", "ఫిర్యాదుదారు పేరు");
        m.put("ui.col.entity_name", "సంస్థ పేరు");
        m.put("ui.col.category", "వర్గం");
        m.put("ui.col.status", "స్థితి");
        m.put("ui.col.priority", "ప్రాధాన్యత");
        m.put("ui.col.ageing", "పెండింగ్");
        m.put("ui.col.sla_remaining", "ఎస్ఎల్ఏ (గంటలు)");
        m.put("ui.col.created_at", "సృష్టి తేదీ");
        m.put("ui.col.updated_at", "చివరి నవీకరణ");
        m.put("ui.col.closed_at", "ముగిసిన తేదీ");
        m.put("ui.col.assigned_officer", "కేటాయించిన అధికారి");
        m.put("ui.col.assigned_role", "కేటాయించిన పాత్ర");
        m.put("ui.col.office", "కార్యాలయం");
        m.put("ui.col.mode_of_receipt", "స్వీకరణ విధానం");
        m.put("ui.col.from_email", "పంపినవారు");
        m.put("ui.col.state", "రాష్ట్రం");
        m.put("ui.col.district", "జిల్లా");
        m.put("ui.col.deadline", "గడువు");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "பணிகள்");
        m.put("ui.grid.complaints", "புகார்கள்");
        m.put("ui.grid.appeals", "மேல்முறையீடுகள்");
        m.put("ui.grid.select_all", "இந்தப் பக்கத்தில் உள்ள அனைத்து வரிசைகளையும் தேர்ந்தெடு");
        m.put("ui.grid.select_row", "இந்த வரிசையைத் தேர்ந்தெடு");
        m.put("ui.col.complaint_number", "புகார் எண்");
        m.put("ui.col.complaint_id", "புகார் ஐடி");
        m.put("ui.col.appeal_number", "மேல்முறையீட்டு எண்");
        m.put("ui.col.subject", "பொருள்");
        m.put("ui.col.complainant_name", "புகார்தாரர் பெயர்");
        m.put("ui.col.entity_name", "நிறுவனத்தின் பெயர்");
        m.put("ui.col.category", "வகை");
        m.put("ui.col.status", "நிலை");
        m.put("ui.col.priority", "முன்னுரிமை");
        m.put("ui.col.ageing", "நிலுவையில்");
        m.put("ui.col.sla_remaining", "எஸ்எல்ஏ (மணி)");
        m.put("ui.col.created_at", "உருவாக்கிய தேதி");
        m.put("ui.col.updated_at", "கடைசி புதுப்பிப்பு");
        m.put("ui.col.closed_at", "முடித்த தேதி");
        m.put("ui.col.assigned_officer", "நியமிக்கப்பட்ட அதிகாரி");
        m.put("ui.col.assigned_role", "நியமிக்கப்பட்ட பங்கு");
        m.put("ui.col.office", "அலுவலகம்");
        m.put("ui.col.mode_of_receipt", "பெறும் முறை");
        m.put("ui.col.from_email", "அனுப்புநர்");
        m.put("ui.col.state", "மாநிலம்");
        m.put("ui.col.district", "மாவட்டம்");
        m.put("ui.col.deadline", "காலக்கெடு");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "કાર્યો");
        m.put("ui.grid.complaints", "ફરિયાદો");
        m.put("ui.grid.appeals", "અપીલો");
        m.put("ui.grid.select_all", "આ પૃષ્ઠની બધી પંક્તિઓ પસંદ કરો");
        m.put("ui.grid.select_row", "આ પંક્તિ પસંદ કરો");
        m.put("ui.col.complaint_number", "ફરિયાદ નંબર");
        m.put("ui.col.complaint_id", "ફરિયાદ આઈડી");
        m.put("ui.col.appeal_number", "અપીલ નંબર");
        m.put("ui.col.subject", "વિષય");
        m.put("ui.col.complainant_name", "ફરિયાદીનું નામ");
        m.put("ui.col.entity_name", "સંસ્થાનું નામ");
        m.put("ui.col.category", "શ્રેણી");
        m.put("ui.col.status", "સ્થિતિ");
        m.put("ui.col.priority", "પ્રાથમિકતા");
        m.put("ui.col.ageing", "બાકી");
        m.put("ui.col.sla_remaining", "એસએલએ (કલાક)");
        m.put("ui.col.created_at", "બનાવ્યાની તારીખ");
        m.put("ui.col.updated_at", "છેલ્લું અપડેટ");
        m.put("ui.col.closed_at", "બંધ થયાની તારીખ");
        m.put("ui.col.assigned_officer", "નિયુક્ત અધિકારી");
        m.put("ui.col.assigned_role", "નિયુક્ત ભૂમિકા");
        m.put("ui.col.office", "કાર્યાલય");
        m.put("ui.col.mode_of_receipt", "પ્રાપ્તિનું માધ્યમ");
        m.put("ui.col.from_email", "પ્રેષક");
        m.put("ui.col.state", "રાજ્ય");
        m.put("ui.col.district", "જિલ્લો");
        m.put("ui.col.deadline", "સમયમર્યાદા");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "کام");
        m.put("ui.grid.complaints", "شکایات");
        m.put("ui.grid.appeals", "اپیلیں");
        m.put("ui.grid.select_all", "اس صفحے کی تمام قطاریں منتخب کریں");
        m.put("ui.grid.select_row", "یہ قطار منتخب کریں");
        m.put("ui.col.complaint_number", "شکایت نمبر");
        m.put("ui.col.complaint_id", "شکایت آئی ڈی");
        m.put("ui.col.appeal_number", "اپیل نمبر");
        m.put("ui.col.subject", "موضوع");
        m.put("ui.col.complainant_name", "شکایت کنندہ کا نام");
        m.put("ui.col.entity_name", "ادارے کا نام");
        m.put("ui.col.category", "قسم");
        m.put("ui.col.status", "حالت");
        m.put("ui.col.priority", "ترجیح");
        m.put("ui.col.ageing", "زیر التواء");
        m.put("ui.col.sla_remaining", "ایس ایل اے (گھنٹے)");
        m.put("ui.col.created_at", "تاریخ اجراء");
        m.put("ui.col.updated_at", "آخری تازہ کاری");
        m.put("ui.col.closed_at", "بندش کی تاریخ");
        m.put("ui.col.assigned_officer", "مقرر کردہ افسر");
        m.put("ui.col.assigned_role", "مقرر کردہ کردار");
        m.put("ui.col.office", "دفتر");
        m.put("ui.col.mode_of_receipt", "وصولی کا ذریعہ");
        m.put("ui.col.from_email", "بھیجنے والا");
        m.put("ui.col.state", "ریاست");
        m.put("ui.col.district", "ضلع");
        m.put("ui.col.deadline", "آخری تاریخ");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "ಕಾರ್ಯಗಳು");
        m.put("ui.grid.complaints", "ದೂರುಗಳು");
        m.put("ui.grid.appeals", "ಮೇಲ್ಮನವಿಗಳು");
        m.put("ui.grid.select_all", "ಈ ಪುಟದ ಎಲ್ಲಾ ಸಾಲುಗಳನ್ನು ಆಯ್ಕೆಮಾಡಿ");
        m.put("ui.grid.select_row", "ಈ ಸಾಲನ್ನು ಆಯ್ಕೆಮಾಡಿ");
        m.put("ui.col.complaint_number", "ದೂರು ಸಂಖ್ಯೆ");
        m.put("ui.col.complaint_id", "ದೂರು ಐಡಿ");
        m.put("ui.col.appeal_number", "ಮೇಲ್ಮನವಿ ಸಂಖ್ಯೆ");
        m.put("ui.col.subject", "ವಿಷಯ");
        m.put("ui.col.complainant_name", "ದೂರುದಾರರ ಹೆಸರು");
        m.put("ui.col.entity_name", "ಸಂಸ್ಥೆಯ ಹೆಸರು");
        m.put("ui.col.category", "ವರ್ಗ");
        m.put("ui.col.status", "ಸ್ಥಿತಿ");
        m.put("ui.col.priority", "ಆದ್ಯತೆ");
        m.put("ui.col.ageing", "ಬಾಕಿ");
        m.put("ui.col.sla_remaining", "ಎಸ್ಎಲ್ಎ (ಗಂಟೆ)");
        m.put("ui.col.created_at", "ಸೃಷ್ಟಿ ದಿನಾಂಕ");
        m.put("ui.col.updated_at", "ಕೊನೆಯ ನವೀಕರಣ");
        m.put("ui.col.closed_at", "ಮುಚ್ಚಿದ ದಿನಾಂಕ");
        m.put("ui.col.assigned_officer", "ನಿಯೋಜಿತ ಅಧಿಕಾರಿ");
        m.put("ui.col.assigned_role", "ನಿಯೋಜಿತ ಪಾತ್ರ");
        m.put("ui.col.office", "ಕಚೇರಿ");
        m.put("ui.col.mode_of_receipt", "ಸ್ವೀಕೃತಿ ವಿಧಾನ");
        m.put("ui.col.from_email", "ಕಳುಹಿಸಿದವರು");
        m.put("ui.col.state", "ರಾಜ್ಯ");
        m.put("ui.col.district", "ಜಿಲ್ಲೆ");
        m.put("ui.col.deadline", "ಗಡುವು");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "ജോലികൾ");
        m.put("ui.grid.complaints", "പരാതികൾ");
        m.put("ui.grid.appeals", "അപ്പീലുകൾ");
        m.put("ui.grid.select_all", "ഈ പേജിലെ എല്ലാ വരികളും തിരഞ്ഞെടുക്കുക");
        m.put("ui.grid.select_row", "ഈ വരി തിരഞ്ഞെടുക്കുക");
        m.put("ui.col.complaint_number", "പരാതി നമ്പർ");
        m.put("ui.col.complaint_id", "പരാതി ഐഡി");
        m.put("ui.col.appeal_number", "അപ്പീൽ നമ്പർ");
        m.put("ui.col.subject", "വിഷയം");
        m.put("ui.col.complainant_name", "പരാതിക്കാരന്റെ പേര്");
        m.put("ui.col.entity_name", "സ്ഥാപനത്തിന്റെ പേര്");
        m.put("ui.col.category", "വിഭാഗം");
        m.put("ui.col.status", "നില");
        m.put("ui.col.priority", "മുൻഗണന");
        m.put("ui.col.ageing", "തീർപ്പാകാത്തത്");
        m.put("ui.col.sla_remaining", "എസ്എൽഎ (മണിക്കൂർ)");
        m.put("ui.col.created_at", "സൃഷ്ടിച്ച തീയതി");
        m.put("ui.col.updated_at", "അവസാന പുതുക്കൽ");
        m.put("ui.col.closed_at", "അടച്ച തീയതി");
        m.put("ui.col.assigned_officer", "നിയോഗിച്ച ഉദ്യോഗസ്ഥൻ");
        m.put("ui.col.assigned_role", "നിയോഗിച്ച റോൾ");
        m.put("ui.col.office", "ഓഫീസ്");
        m.put("ui.col.mode_of_receipt", "സ്വീകരണ രീതി");
        m.put("ui.col.from_email", "അയച്ചത്");
        m.put("ui.col.state", "സംസ്ഥാനം");
        m.put("ui.col.district", "ജില്ല");
        m.put("ui.col.deadline", "അവസാന തീയതി");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ui.grid.tasks", "ਕਾਰਜ");
        m.put("ui.grid.complaints", "ਸ਼ਿਕਾਇਤਾਂ");
        m.put("ui.grid.appeals", "ਅਪੀਲਾਂ");
        m.put("ui.grid.select_all", "ਇਸ ਪੰਨੇ ਦੀਆਂ ਸਾਰੀਆਂ ਕਤਾਰਾਂ ਚੁਣੋ");
        m.put("ui.grid.select_row", "ਇਹ ਕਤਾਰ ਚੁਣੋ");
        m.put("ui.col.complaint_number", "ਸ਼ਿਕਾਇਤ ਨੰਬਰ");
        m.put("ui.col.complaint_id", "ਸ਼ਿਕਾਇਤ ਆਈਡੀ");
        m.put("ui.col.appeal_number", "ਅਪੀਲ ਨੰਬਰ");
        m.put("ui.col.subject", "ਵਿਸ਼ਾ");
        m.put("ui.col.complainant_name", "ਸ਼ਿਕਾਇਤਕਰਤਾ ਦਾ ਨਾਮ");
        m.put("ui.col.entity_name", "ਸੰਸਥਾ ਦਾ ਨਾਮ");
        m.put("ui.col.category", "ਸ਼੍ਰੇਣੀ");
        m.put("ui.col.status", "ਸਥਿਤੀ");
        m.put("ui.col.priority", "ਤਰਜੀਹ");
        m.put("ui.col.ageing", "ਬਕਾਇਆ");
        m.put("ui.col.sla_remaining", "ਐਸਐਲਏ (ਘੰਟੇ)");
        m.put("ui.col.created_at", "ਬਣਾਉਣ ਦੀ ਤਾਰੀਖ");
        m.put("ui.col.updated_at", "ਆਖਰੀ ਅੱਪਡੇਟ");
        m.put("ui.col.closed_at", "ਬੰਦ ਹੋਣ ਦੀ ਤਾਰੀਖ");
        m.put("ui.col.assigned_officer", "ਨਿਯੁਕਤ ਅਧਿਕਾਰੀ");
        m.put("ui.col.assigned_role", "ਨਿਯੁਕਤ ਭੂਮਿਕਾ");
        m.put("ui.col.office", "ਦਫ਼ਤਰ");
        m.put("ui.col.mode_of_receipt", "ਪ੍ਰਾਪਤੀ ਦਾ ਢੰਗ");
        m.put("ui.col.from_email", "ਭੇਜਣ ਵਾਲਾ");
        m.put("ui.col.state", "ਰਾਜ");
        m.put("ui.col.district", "ਜ਼ਿਲ੍ਹਾ");
        m.put("ui.col.deadline", "ਅੰਤਿਮ ਤਾਰੀਖ");
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
