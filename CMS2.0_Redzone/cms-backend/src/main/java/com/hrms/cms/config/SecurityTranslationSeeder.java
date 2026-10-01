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
 * Translation keys and per-locale values for the security console, the PII reveal control and the
 * safe-deactivation dialog (UST873, UST875, UST877, UST887, UST890).
 *
 * Insert-if-absent, like the other seeders: correcting a default here does NOT rewrite a row already
 * in the database. A later text correction needs a scoped UPDATE in both migration directories,
 * keyed by code rather than by an English phrase.
 *
 * All ten supported locales are seeded. These strings gate a citizen's personal data and an
 * officer's access, so a user working in Tamil or Urdu must not be shown an English-only warning
 * about what an action will record.
 */
@Component
@Order(9)
public class SecurityTranslationSeeder implements CommandLineRunner {

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public SecurityTranslationSeeder(TranslationKeyRepository keyRepo,
                                     TranslationRepository translationRepo) {
        this.keyRepo = keyRepo;
        this.translationRepo = translationRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedKeys();
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

    /** English lives on the key's default value, which is the fallback when a locale row is absent. */
    private void seedKeys() {
        seed("security.reveal_button", "security", "Reveal details");
        seed("security.reveal_reason_label", "security", "Reason for viewing");
        seed("security.reveal_logged_notice", "security", "This action is recorded against your name.");
        seed("security.reveal_submit", "security", "Reveal and record");
        seed("security.masked_hint", "security", "Hidden to protect personal data");
        seed("security.reveal_not_permitted", "security",
             "You are not permitted to reveal complainant details.");
        seed("security.reveal_reason_required", "security",
             "Please give a reason of at least 10 characters.");
        seed("security.alerts_title", "security", "Security alerts");
        seed("security.alerts_empty", "security", "No security alerts");
        seed("security.acknowledge", "security", "Acknowledge");
        seed("security.acknowledged", "security", "Acknowledged");
        seed("security.severity", "security", "Severity");
        seed("security.subject", "security", "User or IP");
        seed("security.alert_type", "security", "Alert");
        seed("security.event_count", "security", "Events");
        seed("security.raised_at", "security", "Raised");
        seed("security.status_open", "security", "Open");
        seed("security.credentials_revoked", "security",
             "Your access has been revoked. Contact your administrator.");

        seed("team.deactivate_title", "team", "Deactivate team member");
        seed("team.deactivate_open_records", "team",
             "This officer still holds open complaints. Choose who they transfer to.");
        seed("team.reassign_to_label", "team", "Reassign open complaints to");
        seed("team.revoke_access_label", "team", "Also revoke sign-in access immediately");
        seed("team.deactivate_confirm", "team", "Deactivate");
        seed("team.deactivate_cancel", "team", "Cancel");
        seed("team.deactivate_success", "team", "Team member deactivated");
        seed("team.deactivate_failed", "team", "The team member could not be deactivated.");
        seed("team.reassign_required", "team", "Choose an officer to receive the open complaints.");
        seed("team.open_records_count", "team", "Open complaints");
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "विवरण दिखाएँ");
        m.put("security.reveal_reason_label", "देखने का कारण");
        m.put("security.reveal_logged_notice", "यह कार्रवाई आपके नाम के साथ दर्ज की जाती है।");
        m.put("security.reveal_submit", "दिखाएँ और दर्ज करें");
        m.put("security.masked_hint", "व्यक्तिगत डेटा की सुरक्षा के लिए छिपाया गया");
        m.put("security.reveal_not_permitted", "आपको शिकायतकर्ता का विवरण देखने की अनुमति नहीं है।");
        m.put("security.reveal_reason_required", "कृपया कम से कम 10 अक्षरों का कारण दें।");
        m.put("security.alerts_title", "सुरक्षा अलर्ट");
        m.put("security.alerts_empty", "कोई सुरक्षा अलर्ट नहीं");
        m.put("security.acknowledge", "स्वीकार करें");
        m.put("security.acknowledged", "स्वीकृत");
        m.put("security.severity", "गंभीरता");
        m.put("security.subject", "उपयोगकर्ता या आईपी");
        m.put("security.alert_type", "अलर्ट");
        m.put("security.event_count", "घटनाएँ");
        m.put("security.raised_at", "उठाया गया");
        m.put("security.status_open", "खुला");
        m.put("security.credentials_revoked", "आपकी पहुँच रद्द कर दी गई है। अपने प्रशासक से संपर्क करें।");
        m.put("team.deactivate_title", "टीम सदस्य को निष्क्रिय करें");
        m.put("team.deactivate_open_records",
              "इस अधिकारी के पास अभी भी खुली शिकायतें हैं। चुनें कि वे किसे स्थानांतरित की जाएँ।");
        m.put("team.reassign_to_label", "खुली शिकायतें इन्हें सौंपें");
        m.put("team.revoke_access_label", "साइन-इन पहुँच तुरंत रद्द करें");
        m.put("team.deactivate_confirm", "निष्क्रिय करें");
        m.put("team.deactivate_cancel", "रद्द करें");
        m.put("team.deactivate_success", "टीम सदस्य निष्क्रिय कर दिया गया");
        m.put("team.deactivate_failed", "टीम सदस्य को निष्क्रिय नहीं किया जा सका।");
        m.put("team.reassign_required", "खुली शिकायतें प्राप्त करने के लिए एक अधिकारी चुनें।");
        m.put("team.open_records_count", "खुली शिकायतें");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "तपशील दाखवा");
        m.put("security.reveal_reason_label", "पाहण्याचे कारण");
        m.put("security.reveal_logged_notice", "ही कृती तुमच्या नावासह नोंदवली जाते.");
        m.put("security.reveal_submit", "दाखवा आणि नोंदवा");
        m.put("security.masked_hint", "वैयक्तिक माहितीच्या संरक्षणासाठी लपवले");
        m.put("security.reveal_not_permitted", "तुम्हाला तक्रारदाराचे तपशील पाहण्याची परवानगी नाही.");
        m.put("security.reveal_reason_required", "कृपया किमान 10 अक्षरांचे कारण द्या.");
        m.put("security.alerts_title", "सुरक्षा सूचना");
        m.put("security.alerts_empty", "कोणतीही सुरक्षा सूचना नाही");
        m.put("security.acknowledge", "स्वीकारा");
        m.put("security.acknowledged", "स्वीकारले");
        m.put("security.severity", "तीव्रता");
        m.put("security.subject", "वापरकर्ता किंवा आयपी");
        m.put("security.alert_type", "सूचना");
        m.put("security.event_count", "घटना");
        m.put("security.raised_at", "उपस्थित केले");
        m.put("security.status_open", "खुले");
        m.put("security.credentials_revoked", "तुमचा प्रवेश रद्द केला आहे. प्रशासकाशी संपर्क साधा.");
        m.put("team.deactivate_title", "कार्यसंघ सदस्य निष्क्रिय करा");
        m.put("team.deactivate_open_records",
              "या अधिकाऱ्याकडे अद्याप खुल्या तक्रारी आहेत. त्या कोणाकडे हस्तांतरित करायच्या ते निवडा.");
        m.put("team.reassign_to_label", "खुल्या तक्रारी यांना सोपवा");
        m.put("team.revoke_access_label", "साइन-इन प्रवेश ताबडतोब रद्द करा");
        m.put("team.deactivate_confirm", "निष्क्रिय करा");
        m.put("team.deactivate_cancel", "रद्द करा");
        m.put("team.deactivate_success", "कार्यसंघ सदस्य निष्क्रिय केला");
        m.put("team.deactivate_failed", "कार्यसंघ सदस्य निष्क्रिय करता आला नाही.");
        m.put("team.reassign_required", "खुल्या तक्रारी घेण्यासाठी एक अधिकारी निवडा.");
        m.put("team.open_records_count", "खुल्या तक्रारी");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "বিবরণ দেখান");
        m.put("security.reveal_reason_label", "দেখার কারণ");
        m.put("security.reveal_logged_notice", "এই কাজটি আপনার নামে নথিভুক্ত করা হয়।");
        m.put("security.reveal_submit", "দেখান ও নথিভুক্ত করুন");
        m.put("security.masked_hint", "ব্যক্তিগত তথ্য সুরক্ষার জন্য গোপন করা হয়েছে");
        m.put("security.reveal_not_permitted", "আপনার অভিযোগকারীর বিবরণ দেখার অনুমতি নেই।");
        m.put("security.reveal_reason_required", "অনুগ্রহ করে কমপক্ষে ১০ অক্ষরের একটি কারণ দিন।");
        m.put("security.alerts_title", "সুরক্ষা সতর্কতা");
        m.put("security.alerts_empty", "কোনো সুরক্ষা সতর্কতা নেই");
        m.put("security.acknowledge", "স্বীকার করুন");
        m.put("security.acknowledged", "স্বীকৃত");
        m.put("security.severity", "তীব্রতা");
        m.put("security.subject", "ব্যবহারকারী বা আইপি");
        m.put("security.alert_type", "সতর্কতা");
        m.put("security.event_count", "ঘটনা");
        m.put("security.raised_at", "উত্থাপিত");
        m.put("security.status_open", "খোলা");
        m.put("security.credentials_revoked", "আপনার প্রবেশাধিকার বাতিল করা হয়েছে। প্রশাসকের সঙ্গে যোগাযোগ করুন।");
        m.put("team.deactivate_title", "দলের সদস্যকে নিষ্ক্রিয় করুন");
        m.put("team.deactivate_open_records",
              "এই আধিকারিকের কাছে এখনও খোলা অভিযোগ রয়েছে। সেগুলি কাকে হস্তান্তর করা হবে তা বাছুন।");
        m.put("team.reassign_to_label", "খোলা অভিযোগ এঁকে দিন");
        m.put("team.revoke_access_label", "সাইন-ইন প্রবেশাধিকার এখনই বাতিল করুন");
        m.put("team.deactivate_confirm", "নিষ্ক্রিয় করুন");
        m.put("team.deactivate_cancel", "বাতিল করুন");
        m.put("team.deactivate_success", "দলের সদস্য নিষ্ক্রিয় করা হয়েছে");
        m.put("team.deactivate_failed", "দলের সদস্যকে নিষ্ক্রিয় করা যায়নি।");
        m.put("team.reassign_required", "খোলা অভিযোগ গ্রহণ করার জন্য একজন আধিকারিক বাছুন।");
        m.put("team.open_records_count", "খোলা অভিযোগ");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "వివరాలు చూపించు");
        m.put("security.reveal_reason_label", "చూడటానికి కారణం");
        m.put("security.reveal_logged_notice", "ఈ చర్య మీ పేరుతో నమోదు చేయబడుతుంది.");
        m.put("security.reveal_submit", "చూపించి నమోదు చేయి");
        m.put("security.masked_hint", "వ్యక్తిగత సమాచార రక్షణ కోసం దాచబడింది");
        m.put("security.reveal_not_permitted", "ఫిర్యాదుదారు వివరాలను చూసే అనుమతి మీకు లేదు.");
        m.put("security.reveal_reason_required", "దయచేసి కనీసం 10 అక్షరాల కారణం ఇవ్వండి.");
        m.put("security.alerts_title", "భద్రతా హెచ్చరికలు");
        m.put("security.alerts_empty", "భద్రతా హెచ్చరికలు లేవు");
        m.put("security.acknowledge", "అంగీకరించు");
        m.put("security.acknowledged", "అంగీకరించబడింది");
        m.put("security.severity", "తీవ్రత");
        m.put("security.subject", "వినియోగదారు లేదా ఐపీ");
        m.put("security.alert_type", "హెచ్చరిక");
        m.put("security.event_count", "సంఘటనలు");
        m.put("security.raised_at", "లేవనెత్తినది");
        m.put("security.status_open", "తెరిచి ఉంది");
        m.put("security.credentials_revoked", "మీ ప్రవేశం రద్దు చేయబడింది. మీ నిర్వాహకుడిని సంప్రదించండి.");
        m.put("team.deactivate_title", "జట్టు సభ్యుడిని నిష్క్రియం చేయి");
        m.put("team.deactivate_open_records",
              "ఈ అధికారి వద్ద ఇంకా తెరిచిన ఫిర్యాదులు ఉన్నాయి. వాటిని ఎవరికి బదిలీ చేయాలో ఎంచుకోండి.");
        m.put("team.reassign_to_label", "తెరిచిన ఫిర్యాదులను వీరికి అప్పగించు");
        m.put("team.revoke_access_label", "సైన్-ఇన్ ప్రవేశాన్ని వెంటనే రద్దు చేయి");
        m.put("team.deactivate_confirm", "నిష్క్రియం చేయి");
        m.put("team.deactivate_cancel", "రద్దు చేయి");
        m.put("team.deactivate_success", "జట్టు సభ్యుడు నిష్క్రియం చేయబడ్డాడు");
        m.put("team.deactivate_failed", "జట్టు సభ్యుడిని నిష్క్రియం చేయలేకపోయాము.");
        m.put("team.reassign_required", "తెరిచిన ఫిర్యాదులను స్వీకరించడానికి ఒక అధికారిని ఎంచుకోండి.");
        m.put("team.open_records_count", "తెరిచిన ఫిర్యాదులు");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "விவரங்களைக் காட்டு");
        m.put("security.reveal_reason_label", "பார்ப்பதற்கான காரணம்");
        m.put("security.reveal_logged_notice", "இந்தச் செயல் உங்கள் பெயரில் பதிவு செய்யப்படுகிறது.");
        m.put("security.reveal_submit", "காட்டி பதிவு செய்");
        m.put("security.masked_hint", "தனிநபர் தகவலைப் பாதுகாக்க மறைக்கப்பட்டுள்ளது");
        m.put("security.reveal_not_permitted", "புகார்தாரர் விவரங்களைப் பார்க்க உங்களுக்கு அனுமதி இல்லை.");
        m.put("security.reveal_reason_required", "குறைந்தது 10 எழுத்துகள் கொண்ட காரணத்தை அளிக்கவும்.");
        m.put("security.alerts_title", "பாதுகாப்பு எச்சரிக்கைகள்");
        m.put("security.alerts_empty", "பாதுகாப்பு எச்சரிக்கைகள் இல்லை");
        m.put("security.acknowledge", "ஏற்றுக்கொள்");
        m.put("security.acknowledged", "ஏற்கப்பட்டது");
        m.put("security.severity", "தீவிரம்");
        m.put("security.subject", "பயனர் அல்லது ஐபி");
        m.put("security.alert_type", "எச்சரிக்கை");
        m.put("security.event_count", "நிகழ்வுகள்");
        m.put("security.raised_at", "எழுப்பப்பட்டது");
        m.put("security.status_open", "திறந்துள்ளது");
        m.put("security.credentials_revoked", "உங்கள் அணுகல் ரத்து செய்யப்பட்டது. நிர்வாகியைத் தொடர்பு கொள்ளுங்கள்.");
        m.put("team.deactivate_title", "குழு உறுப்பினரை செயலிழக்கச் செய்");
        m.put("team.deactivate_open_records",
              "இந்த அதிகாரியிடம் இன்னும் திறந்த புகார்கள் உள்ளன. அவற்றை யாருக்கு மாற்ற வேண்டும் என்பதைத் தேர்வுசெய்யுங்கள்.");
        m.put("team.reassign_to_label", "திறந்த புகார்களை இவருக்கு ஒப்படை");
        m.put("team.revoke_access_label", "உள்நுழைவு அணுகலை உடனே ரத்துசெய்");
        m.put("team.deactivate_confirm", "செயலிழக்கச் செய்");
        m.put("team.deactivate_cancel", "ரத்துசெய்");
        m.put("team.deactivate_success", "குழு உறுப்பினர் செயலிழக்கச் செய்யப்பட்டார்");
        m.put("team.deactivate_failed", "குழு உறுப்பினரை செயலிழக்கச் செய்ய முடியவில்லை.");
        m.put("team.reassign_required", "திறந்த புகார்களைப் பெற ஒரு அதிகாரியைத் தேர்வுசெய்யுங்கள்.");
        m.put("team.open_records_count", "திறந்த புகார்கள்");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "વિગતો બતાવો");
        m.put("security.reveal_reason_label", "જોવાનું કારણ");
        m.put("security.reveal_logged_notice", "આ ક્રિયા તમારા નામે નોંધવામાં આવે છે.");
        m.put("security.reveal_submit", "બતાવો અને નોંધો");
        m.put("security.masked_hint", "વ્યક્તિગત માહિતીની સુરક્ષા માટે છુપાવેલ");
        m.put("security.reveal_not_permitted", "તમને ફરિયાદીની વિગતો જોવાની પરવાનગી નથી.");
        m.put("security.reveal_reason_required", "કૃપા કરીને ઓછામાં ઓછા 10 અક્ષરોનું કારણ આપો.");
        m.put("security.alerts_title", "સુરક્ષા ચેતવણીઓ");
        m.put("security.alerts_empty", "કોઈ સુરક્ષા ચેતવણી નથી");
        m.put("security.acknowledge", "સ્વીકારો");
        m.put("security.acknowledged", "સ્વીકૃત");
        m.put("security.severity", "ગંભીરતા");
        m.put("security.subject", "વપરાશકર્તા અથવા આઈપી");
        m.put("security.alert_type", "ચેતવણી");
        m.put("security.event_count", "ઘટનાઓ");
        m.put("security.raised_at", "ઉઠાવેલ");
        m.put("security.status_open", "ખુલ્લું");
        m.put("security.credentials_revoked", "તમારો પ્રવેશ રદ કરવામાં આવ્યો છે. તમારા વ્યવસ્થાપકનો સંપર્ક કરો.");
        m.put("team.deactivate_title", "ટીમ સભ્યને નિષ્ક્રિય કરો");
        m.put("team.deactivate_open_records",
              "આ અધિકારી પાસે હજુ ખુલ્લી ફરિયાદો છે. તે કોને સોંપવી તે પસંદ કરો.");
        m.put("team.reassign_to_label", "ખુલ્લી ફરિયાદો આમને સોંપો");
        m.put("team.revoke_access_label", "સાઇન-ઇન પ્રવેશ તરત જ રદ કરો");
        m.put("team.deactivate_confirm", "નિષ્ક્રિય કરો");
        m.put("team.deactivate_cancel", "રદ કરો");
        m.put("team.deactivate_success", "ટીમ સભ્ય નિષ્ક્રિય કરાયો");
        m.put("team.deactivate_failed", "ટીમ સભ્યને નિષ્ક્રિય કરી શકાયો નહીં.");
        m.put("team.reassign_required", "ખુલ્લી ફરિયાદો મેળવવા માટે એક અધિકારી પસંદ કરો.");
        m.put("team.open_records_count", "ખુલ્લી ફરિયાદો");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "تفصیلات دکھائیں");
        m.put("security.reveal_reason_label", "دیکھنے کی وجہ");
        m.put("security.reveal_logged_notice", "یہ کارروائی آپ کے نام سے درج کی جاتی ہے۔");
        m.put("security.reveal_submit", "دکھائیں اور درج کریں");
        m.put("security.masked_hint", "ذاتی معلومات کے تحفظ کے لیے چھپایا گیا");
        m.put("security.reveal_not_permitted", "آپ کو شکایت کنندہ کی تفصیلات دیکھنے کی اجازت نہیں ہے۔");
        m.put("security.reveal_reason_required", "براہ کرم کم از کم 10 حروف کی وجہ بتائیں۔");
        m.put("security.alerts_title", "سیکیورٹی انتباہات");
        m.put("security.alerts_empty", "کوئی سیکیورٹی انتباہ نہیں");
        m.put("security.acknowledge", "تسلیم کریں");
        m.put("security.acknowledged", "تسلیم شدہ");
        m.put("security.severity", "شدت");
        m.put("security.subject", "صارف یا آئی پی");
        m.put("security.alert_type", "انتباہ");
        m.put("security.event_count", "واقعات");
        m.put("security.raised_at", "اٹھایا گیا");
        m.put("security.status_open", "کھلا");
        m.put("security.credentials_revoked", "آپ کی رسائی منسوخ کر دی گئی ہے۔ اپنے منتظم سے رابطہ کریں۔");
        m.put("team.deactivate_title", "ٹیم رکن کو غیر فعال کریں");
        m.put("team.deactivate_open_records",
              "اس افسر کے پاس اب بھی کھلی شکایات ہیں۔ منتخب کریں کہ وہ کس کو منتقل کی جائیں۔");
        m.put("team.reassign_to_label", "کھلی شکایات اِن کے سپرد کریں");
        m.put("team.revoke_access_label", "سائن اِن رسائی فوراً منسوخ کریں");
        m.put("team.deactivate_confirm", "غیر فعال کریں");
        m.put("team.deactivate_cancel", "منسوخ کریں");
        m.put("team.deactivate_success", "ٹیم رکن غیر فعال کر دیا گیا");
        m.put("team.deactivate_failed", "ٹیم رکن کو غیر فعال نہیں کیا جا سکا۔");
        m.put("team.reassign_required", "کھلی شکایات وصول کرنے کے لیے ایک افسر منتخب کریں۔");
        m.put("team.open_records_count", "کھلی شکایات");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "ವಿವರಗಳನ್ನು ತೋರಿಸು");
        m.put("security.reveal_reason_label", "ನೋಡುವ ಕಾರಣ");
        m.put("security.reveal_logged_notice", "ಈ ಕ್ರಮವನ್ನು ನಿಮ್ಮ ಹೆಸರಿನಲ್ಲಿ ದಾಖಲಿಸಲಾಗುತ್ತದೆ.");
        m.put("security.reveal_submit", "ತೋರಿಸಿ ದಾಖಲಿಸು");
        m.put("security.masked_hint", "ವೈಯಕ್ತಿಕ ಮಾಹಿತಿ ರಕ್ಷಣೆಗಾಗಿ ಮರೆಮಾಡಲಾಗಿದೆ");
        m.put("security.reveal_not_permitted", "ದೂರುದಾರರ ವಿವರಗಳನ್ನು ನೋಡಲು ನಿಮಗೆ ಅನುಮತಿ ಇಲ್ಲ.");
        m.put("security.reveal_reason_required", "ದಯವಿಟ್ಟು ಕನಿಷ್ಠ 10 ಅಕ್ಷರಗಳ ಕಾರಣ ನೀಡಿ.");
        m.put("security.alerts_title", "ಸುರಕ್ಷತಾ ಎಚ್ಚರಿಕೆಗಳು");
        m.put("security.alerts_empty", "ಯಾವುದೇ ಸುರಕ್ಷತಾ ಎಚ್ಚರಿಕೆ ಇಲ್ಲ");
        m.put("security.acknowledge", "ಒಪ್ಪಿಕೊಳ್ಳಿ");
        m.put("security.acknowledged", "ಒಪ್ಪಿಕೊಳ್ಳಲಾಗಿದೆ");
        m.put("security.severity", "ತೀವ್ರತೆ");
        m.put("security.subject", "ಬಳಕೆದಾರ ಅಥವಾ ಐಪಿ");
        m.put("security.alert_type", "ಎಚ್ಚರಿಕೆ");
        m.put("security.event_count", "ಘಟನೆಗಳು");
        m.put("security.raised_at", "ಎತ್ತಲಾಗಿದೆ");
        m.put("security.status_open", "ತೆರೆದಿದೆ");
        m.put("security.credentials_revoked", "ನಿಮ್ಮ ಪ್ರವೇಶವನ್ನು ರದ್ದುಗೊಳಿಸಲಾಗಿದೆ. ನಿರ್ವಾಹಕರನ್ನು ಸಂಪರ್ಕಿಸಿ.");
        m.put("team.deactivate_title", "ತಂಡದ ಸದಸ್ಯರನ್ನು ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಿ");
        m.put("team.deactivate_open_records",
              "ಈ ಅಧಿಕಾರಿಯ ಬಳಿ ಇನ್ನೂ ತೆರೆದ ದೂರುಗಳಿವೆ. ಅವುಗಳನ್ನು ಯಾರಿಗೆ ವರ್ಗಾಯಿಸಬೇಕು ಎಂದು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("team.reassign_to_label", "ತೆರೆದ ದೂರುಗಳನ್ನು ಇವರಿಗೆ ವಹಿಸಿ");
        m.put("team.revoke_access_label", "ಸೈನ್-ಇನ್ ಪ್ರವೇಶವನ್ನು ತಕ್ಷಣ ರದ್ದುಗೊಳಿಸಿ");
        m.put("team.deactivate_confirm", "ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಿ");
        m.put("team.deactivate_cancel", "ರದ್ದುಮಾಡಿ");
        m.put("team.deactivate_success", "ತಂಡದ ಸದಸ್ಯ ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಲಾಗಿದೆ");
        m.put("team.deactivate_failed", "ತಂಡದ ಸದಸ್ಯರನ್ನು ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಲಾಗಲಿಲ್ಲ.");
        m.put("team.reassign_required", "ತೆರೆದ ದೂರುಗಳನ್ನು ಸ್ವೀಕರಿಸಲು ಒಬ್ಬ ಅಧಿಕಾರಿಯನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("team.open_records_count", "ತೆರೆದ ದೂರುಗಳು");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("security.reveal_button", "വിവരങ്ങൾ കാണിക്കുക");
        m.put("security.reveal_reason_label", "കാണാനുള്ള കാരണം");
        m.put("security.reveal_logged_notice", "ഈ നടപടി നിങ്ങളുടെ പേരിൽ രേഖപ്പെടുത്തുന്നു.");
        m.put("security.reveal_submit", "കാണിച്ച് രേഖപ്പെടുത്തുക");
        m.put("security.masked_hint", "വ്യക്തിഗത വിവരങ്ങൾ സംരക്ഷിക്കാൻ മറച്ചിരിക്കുന്നു");
        m.put("security.reveal_not_permitted", "പരാതിക്കാരന്റെ വിവരങ്ങൾ കാണാൻ നിങ്ങൾക്ക് അനുമതിയില്ല.");
        m.put("security.reveal_reason_required", "കുറഞ്ഞത് 10 അക്ഷരങ്ങളുള്ള ഒരു കാരണം നൽകുക.");
        m.put("security.alerts_title", "സുരക്ഷാ മുന്നറിയിപ്പുകൾ");
        m.put("security.alerts_empty", "സുരക്ഷാ മുന്നറിയിപ്പുകൾ ഇല്ല");
        m.put("security.acknowledge", "അംഗീകരിക്കുക");
        m.put("security.acknowledged", "അംഗീകരിച്ചു");
        m.put("security.severity", "തീവ്രത");
        m.put("security.subject", "ഉപയോക്താവ് അല്ലെങ്കിൽ ഐപി");
        m.put("security.alert_type", "മുന്നറിയിപ്പ്");
        m.put("security.event_count", "സംഭവങ്ങൾ");
        m.put("security.raised_at", "ഉന്നയിച്ചത്");
        m.put("security.status_open", "തുറന്നിരിക്കുന്നു");
        m.put("security.credentials_revoked", "നിങ്ങളുടെ പ്രവേശനം റദ്ദാക്കി. അഡ്മിനിസ്ട്രേറ്ററെ ബന്ധപ്പെടുക.");
        m.put("team.deactivate_title", "ടീം അംഗത്തെ നിർജ്ജീവമാക്കുക");
        m.put("team.deactivate_open_records",
              "ഈ ഓഫീസറുടെ പക്കൽ ഇനിയും തുറന്ന പരാതികളുണ്ട്. അവ ആർക്ക് കൈമാറണമെന്ന് തിരഞ്ഞെടുക്കുക.");
        m.put("team.reassign_to_label", "തുറന്ന പരാതികൾ ഇവർക്ക് നൽകുക");
        m.put("team.revoke_access_label", "സൈൻ-ഇൻ പ്രവേശനം ഉടൻ റദ്ദാക്കുക");
        m.put("team.deactivate_confirm", "നിർജ്ജീവമാക്കുക");
        m.put("team.deactivate_cancel", "റദ്ദാക്കുക");
        m.put("team.deactivate_success", "ടീം അംഗത്തെ നിർജ്ജീവമാക്കി");
        m.put("team.deactivate_failed", "ടീം അംഗത്തെ നിർജ്ജീവമാക്കാൻ കഴിഞ്ഞില്ല.");
        m.put("team.reassign_required", "തുറന്ന പരാതികൾ സ്വീകരിക്കാൻ ഒരു ഓഫീസറെ തിരഞ്ഞെടുക്കുക.");
        m.put("team.open_records_count", "തുറന്ന പരാതികൾ");
        return m;
    }

    private void seed(String code, String module, String defaultValue) {
        if (keyRepo.existsByCode(code)) {
            return;
        }
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(module);
        key.setDefaultValue(defaultValue);
        keyRepo.save(key);
    }

    private void seedLocale(String locale, Map<String, String> translations) {
        for (Map.Entry<String, String> entry : translations.entrySet()) {
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
