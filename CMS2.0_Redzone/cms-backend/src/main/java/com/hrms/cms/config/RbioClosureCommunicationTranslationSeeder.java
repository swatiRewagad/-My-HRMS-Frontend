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
 * Translations for the closure communication gate, clause tiering, impleading and the closure SMS
 * (UST504-509, 520, 544-546, 549, 576, 581-584, 762-764, 506).
 *
 * <p>A SEPARATE seeder at its own {@code @Order}, per the convention stated at
 * {@code AaRegisterTranslationSeeder}: a shared seeder would be a guaranteed merge conflict in a file where
 * a conflict silently costs a locale.
 *
 * <p><b>Every key is namespaced.</b> Translation keys are idempotent by {@code existsByCode}, so if two
 * sessions choose the same code the first text silently wins and the second never appears. All keys here live
 * under {@code rbio.closure.*} or {@code rbio.implead.*}.
 *
 * <p><b>The closure SMS text is a translation key, not a literal.</b> The story supplied the wording in
 * English, but an SMS to a complainant is citizen-facing text and must be translatable like everything else —
 * so it is registered here rather than compiled into the service that queues it.
 *
 * <p><b>Insert-if-absent.</b> Correcting a default in this file does NOT fix rows already in the database. A
 * text correction needs a code-scoped UPDATE in both migration directories, scoped BY KEY CODE and never by
 * an English phrase, because the localized rows are in native scripts and an English substring matches none
 * of them.
 */
@Component
@Order(38)
public class RbioClosureCommunicationTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "rbio";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public RbioClosureCommunicationTranslationSeeder(TranslationKeyRepository keyRepo,
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

        // ── Closure communication refusals (UST507-509, 549, 764) ──
        m.put("rbio.closure.error_send_date_required",
                "Record the Date of Sending of the closure letter before closing this complaint.");
        m.put("rbio.closure.error_signed_letter_required",
                "Upload the signed closure letter before closing this complaint.");
        m.put("rbio.closure.error_email_missing",
                "This complaint cannot be closed directly because the complainant has no email address on "
                        + "record. Record the outcome through Facilitation/Rejection or Decision instead.");
        m.put("rbio.closure.error_custom_text_too_long",
                "The custom closure text may not exceed 2000 characters.");

        // ── Clause tiering refusals (UST581-584) ──
        m.put("rbio.closure.error_clause_required",
                "Select a closure clause before saving.");
        m.put("rbio.closure.error_clause_unknown",
                "This closure clause is not configured for the applicable Scheme, so it cannot be cited.");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "Your role is not permitted to cite this closure clause.");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "The closure clause can no longer be changed because the complaint has moved to the Ombudsman.");

        // ── Impleading (UST544-546) ──
        m.put("rbio.implead.error_incomplete_parties",
                "Record the closure clause and compensation for every impleaded party before closing.");
        m.put("rbio.implead.error_duplicate_party",
                "This party is already impleaded into the complaint.");
        m.put("rbio.implead.label_party_name", "Impleaded party");
        m.put("rbio.implead.label_party_type", "Party type");
        m.put("rbio.implead.label_reason", "Reason for impleading");
        m.put("rbio.implead.status_information_required", "Information required");
        m.put("rbio.implead.status_complete", "Complete");

        // ── Closure communication labels ──
        m.put("rbio.closure.label_date_of_sending", "Date of Sending");
        m.put("rbio.closure.label_signed_letter", "Signed closure letter");
        m.put("rbio.closure.label_custom_text", "Custom closure text");

        // ── The closure SMS body (UST506). Citizen-facing, hence a key rather than a literal. ──
        m.put("rbio.closure.sms_closed",
                "Your complaint {0} has been closed by the RBI Ombudsman. Please check your email for the "
                        + "closure letter.");

        // ── Letter signing status (UST580). Unsigned until a real DSC/HSM key exists. ──
        m.put("rbio.closure.letter_not_digitally_signed",
                "This is not a digitally signed document.");

        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.closure.error_send_date_required",
                "शिकायत बंद करने से पहले समापन पत्र भेजने की तिथि दर्ज करें।");
        m.put("rbio.closure.error_signed_letter_required",
                "शिकायत बंद करने से पहले हस्ताक्षरित समापन पत्र अपलोड करें।");
        m.put("rbio.closure.error_email_missing",
                "शिकायतकर्ता का ईमेल पता दर्ज न होने के कारण यह शिकायत सीधे बंद नहीं की जा सकती। "
                        + "इसके बजाय सुलह/अस्वीकृति या निर्णय के माध्यम से परिणाम दर्ज करें।");
        m.put("rbio.closure.error_custom_text_too_long",
                "कस्टम समापन पाठ 2000 वर्णों से अधिक नहीं हो सकता।");
        m.put("rbio.closure.error_clause_required", "सहेजने से पहले समापन खंड चुनें।");
        m.put("rbio.closure.error_clause_unknown",
                "यह समापन खंड लागू योजना के लिए कॉन्फ़िगर नहीं है, इसलिए इसका उल्लेख नहीं किया जा सकता।");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "आपकी भूमिका को इस समापन खंड का उल्लेख करने की अनुमति नहीं है।");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "शिकायत लोकपाल के पास चली गई है, इसलिए समापन खंड अब बदला नहीं जा सकता।");
        m.put("rbio.implead.error_incomplete_parties",
                "बंद करने से पहले प्रत्येक पक्षकार के लिए समापन खंड और मुआवजा दर्ज करें।");
        m.put("rbio.implead.error_duplicate_party", "यह पक्षकार पहले से ही शिकायत में शामिल है।");
        m.put("rbio.implead.label_party_name", "शामिल पक्षकार");
        m.put("rbio.implead.label_party_type", "पक्षकार का प्रकार");
        m.put("rbio.implead.label_reason", "शामिल करने का कारण");
        m.put("rbio.implead.status_information_required", "जानकारी आवश्यक");
        m.put("rbio.implead.status_complete", "पूर्ण");
        m.put("rbio.closure.label_date_of_sending", "भेजने की तिथि");
        m.put("rbio.closure.label_signed_letter", "हस्ताक्षरित समापन पत्र");
        m.put("rbio.closure.label_custom_text", "कस्टम समापन पाठ");
        m.put("rbio.closure.sms_closed",
                "आपकी शिकायत {0} आरबीआई लोकपाल द्वारा बंद कर दी गई है। समापन पत्र के लिए अपना ईमेल देखें।");
        m.put("rbio.closure.letter_not_digitally_signed", "यह डिजिटल रूप से हस्ताक्षरित दस्तावेज़ नहीं है।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.closure.error_send_date_required",
                "तक्रार बंद करण्यापूर्वी समाप्ती पत्र पाठवण्याची तारीख नोंदवा।");
        m.put("rbio.closure.error_signed_letter_required",
                "तक्रार बंद करण्यापूर्वी स्वाक्षरी केलेले समाप्ती पत्र अपलोड करा।");
        m.put("rbio.closure.error_email_missing",
                "तक्रारदाराचा ईमेल पत्ता नोंदवलेला नसल्याने ही तक्रार थेट बंद करता येत नाही. "
                        + "त्याऐवजी सहमती/नकार किंवा निर्णयाद्वारे निकाल नोंदवा.");
        m.put("rbio.closure.error_custom_text_too_long",
                "सानुकूल समाप्ती मजकूर 2000 अक्षरांपेक्षा जास्त असू शकत नाही.");
        m.put("rbio.closure.error_clause_required", "जतन करण्यापूर्वी समाप्ती कलम निवडा.");
        m.put("rbio.closure.error_clause_unknown",
                "हे समाप्ती कलम लागू योजनेसाठी कॉन्फिगर केलेले नाही, म्हणून त्याचा उल्लेख करता येत नाही.");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "तुमच्या भूमिकेला या समाप्ती कलमाचा उल्लेख करण्याची परवानगी नाही.");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "तक्रार लोकपालाकडे गेली असल्याने समाप्ती कलम आता बदलता येत नाही.");
        m.put("rbio.implead.error_incomplete_parties",
                "बंद करण्यापूर्वी प्रत्येक सहभागी पक्षासाठी समाप्ती कलम आणि भरपाई नोंदवा.");
        m.put("rbio.implead.error_duplicate_party", "हा पक्ष यापूर्वीच तक्रारीत सहभागी आहे.");
        m.put("rbio.implead.label_party_name", "सहभागी पक्ष");
        m.put("rbio.implead.label_party_type", "पक्षाचा प्रकार");
        m.put("rbio.implead.label_reason", "सहभागी करण्याचे कारण");
        m.put("rbio.implead.status_information_required", "माहिती आवश्यक");
        m.put("rbio.implead.status_complete", "पूर्ण");
        m.put("rbio.closure.label_date_of_sending", "पाठवण्याची तारीख");
        m.put("rbio.closure.label_signed_letter", "स्वाक्षरी केलेले समाप्ती पत्र");
        m.put("rbio.closure.label_custom_text", "सानुकूल समाप्ती मजकूर");
        m.put("rbio.closure.sms_closed",
                "तुमची तक्रार {0} आरबीआय लोकपालाने बंद केली आहे. समाप्ती पत्रासाठी तुमचा ईमेल तपासा.");
        m.put("rbio.closure.letter_not_digitally_signed", "हे डिजिटल स्वाक्षरी केलेले दस्तऐवज नाही.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        // Bengali stores digits in Bengali numerals (২০০০, not 2000) — a digit replacement keyed on ASCII
        // would silently skip this locale, so the numeral is written in script here.
        m.put("rbio.closure.error_send_date_required",
                "অভিযোগ বন্ধ করার আগে সমাপ্তি পত্র পাঠানোর তারিখ লিপিবদ্ধ করুন।");
        m.put("rbio.closure.error_signed_letter_required",
                "অভিযোগ বন্ধ করার আগে স্বাক্ষরিত সমাপ্তি পত্র আপলোড করুন।");
        m.put("rbio.closure.error_email_missing",
                "অভিযোগকারীর ইমেল ঠিকানা নথিভুক্ত না থাকায় এই অভিযোগ সরাসরি বন্ধ করা যাবে না। "
                        + "পরিবর্তে মধ্যস্থতা/প্রত্যাখ্যান বা সিদ্ধান্তের মাধ্যমে ফলাফল লিপিবদ্ধ করুন।");
        m.put("rbio.closure.error_custom_text_too_long",
                "কাস্টম সমাপ্তি পাঠ্য ২০০০ অক্ষরের বেশি হতে পারে না।");
        m.put("rbio.closure.error_clause_required", "সংরক্ষণ করার আগে সমাপ্তি ধারা নির্বাচন করুন।");
        m.put("rbio.closure.error_clause_unknown",
                "এই সমাপ্তি ধারাটি প্রযোজ্য স্কিমের জন্য কনফিগার করা নেই, তাই এটি উল্লেখ করা যাবে না।");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "আপনার ভূমিকা এই সমাপ্তি ধারা উল্লেখ করার অনুমতি পায়নি।");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "অভিযোগটি ন্যায়পালের কাছে চলে যাওয়ায় সমাপ্তি ধারা আর পরিবর্তন করা যাবে না।");
        m.put("rbio.implead.error_incomplete_parties",
                "বন্ধ করার আগে প্রতিটি অন্তর্ভুক্ত পক্ষের জন্য সমাপ্তি ধারা ও ক্ষতিপূরণ লিপিবদ্ধ করুন।");
        m.put("rbio.implead.error_duplicate_party", "এই পক্ষটি ইতিমধ্যেই অভিযোগে অন্তর্ভুক্ত রয়েছে।");
        m.put("rbio.implead.label_party_name", "অন্তর্ভুক্ত পক্ষ");
        m.put("rbio.implead.label_party_type", "পক্ষের ধরন");
        m.put("rbio.implead.label_reason", "অন্তর্ভুক্ত করার কারণ");
        m.put("rbio.implead.status_information_required", "তথ্য প্রয়োজন");
        m.put("rbio.implead.status_complete", "সম্পূর্ণ");
        m.put("rbio.closure.label_date_of_sending", "পাঠানোর তারিখ");
        m.put("rbio.closure.label_signed_letter", "স্বাক্ষরিত সমাপ্তি পত্র");
        m.put("rbio.closure.label_custom_text", "কাস্টম সমাপ্তি পাঠ্য");
        m.put("rbio.closure.sms_closed",
                "আপনার অভিযোগ {0} আরবিআই ন্যায়পাল কর্তৃক বন্ধ করা হয়েছে। সমাপ্তি পত্রের জন্য আপনার ইমেল দেখুন।");
        m.put("rbio.closure.letter_not_digitally_signed", "এটি ডিজিটালভাবে স্বাক্ষরিত নথি নয়।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.closure.error_send_date_required",
                "ఫిర్యాదును మూసివేయడానికి ముందు ముగింపు లేఖ పంపిన తేదీని నమోదు చేయండి.");
        m.put("rbio.closure.error_signed_letter_required",
                "ఫిర్యాదును మూసివేయడానికి ముందు సంతకం చేసిన ముగింపు లేఖను అప్‌లోడ్ చేయండి.");
        m.put("rbio.closure.error_email_missing",
                "ఫిర్యాదుదారుని ఇమెయిల్ చిరునామా నమోదు కానందున ఈ ఫిర్యాదును నేరుగా మూసివేయలేరు. "
                        + "దీనికి బదులుగా రాజీ/తిరస్కరణ లేదా నిర్ణయం ద్వారా ఫలితాన్ని నమోదు చేయండి.");
        m.put("rbio.closure.error_custom_text_too_long",
                "అనుకూల ముగింపు వచనం 2000 అక్షరాలకు మించకూడదు.");
        m.put("rbio.closure.error_clause_required", "సేవ్ చేయడానికి ముందు ముగింపు నిబంధనను ఎంచుకోండి.");
        m.put("rbio.closure.error_clause_unknown",
                "ఈ ముగింపు నిబంధన వర్తించే పథకం కోసం కాన్ఫిగర్ చేయబడలేదు, కాబట్టి దానిని ఉదహరించలేరు.");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "ఈ ముగింపు నిబంధనను ఉదహరించడానికి మీ పాత్రకు అనుమతి లేదు.");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "ఫిర్యాదు అంబుడ్స్‌మన్‌కు చేరినందున ముగింపు నిబంధనను ఇకపై మార్చలేరు.");
        m.put("rbio.implead.error_incomplete_parties",
                "మూసివేయడానికి ముందు ప్రతి చేర్చిన పక్షం కోసం ముగింపు నిబంధన మరియు పరిహారాన్ని నమోదు చేయండి.");
        m.put("rbio.implead.error_duplicate_party", "ఈ పక్షం ఇప్పటికే ఫిర్యాదులో చేర్చబడింది.");
        m.put("rbio.implead.label_party_name", "చేర్చిన పక్షం");
        m.put("rbio.implead.label_party_type", "పక్షం రకం");
        m.put("rbio.implead.label_reason", "చేర్చడానికి కారణం");
        m.put("rbio.implead.status_information_required", "సమాచారం అవసరం");
        m.put("rbio.implead.status_complete", "పూర్తి");
        m.put("rbio.closure.label_date_of_sending", "పంపిన తేదీ");
        m.put("rbio.closure.label_signed_letter", "సంతకం చేసిన ముగింపు లేఖ");
        m.put("rbio.closure.label_custom_text", "అనుకూల ముగింపు వచనం");
        m.put("rbio.closure.sms_closed",
                "మీ ఫిర్యాదు {0} ఆర్‌బీఐ అంబుడ్స్‌మన్ ద్వారా మూసివేయబడింది. ముగింపు లేఖ కోసం మీ ఇమెయిల్ చూడండి.");
        m.put("rbio.closure.letter_not_digitally_signed", "ఇది డిజిటల్‌గా సంతకం చేసిన పత్రం కాదు.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.closure.error_send_date_required",
                "புகாரை முடிக்கும் முன் நிறைவு கடிதம் அனுப்பப்பட்ட தேதியைப் பதிவு செய்யுங்கள்.");
        m.put("rbio.closure.error_signed_letter_required",
                "புகாரை முடிக்கும் முன் கையொப்பமிடப்பட்ட நிறைவு கடிதத்தைப் பதிவேற்றுங்கள்.");
        m.put("rbio.closure.error_email_missing",
                "புகார்தாரரின் மின்னஞ்சல் முகவரி பதிவு செய்யப்படாததால் இந்தப் புகாரை நேரடியாக முடிக்க முடியாது. "
                        + "அதற்குப் பதிலாக சமரசம்/நிராகரிப்பு அல்லது தீர்ப்பின் மூலம் முடிவைப் பதிவு செய்யுங்கள்.");
        m.put("rbio.closure.error_custom_text_too_long",
                "தனிப்பயன் நிறைவு உரை 2000 எழுத்துகளுக்கு மேல் இருக்கக்கூடாது.");
        m.put("rbio.closure.error_clause_required", "சேமிக்கும் முன் நிறைவு பிரிவைத் தேர்ந்தெடுக்கவும்.");
        m.put("rbio.closure.error_clause_unknown",
                "இந்த நிறைவு பிரிவு பொருந்தும் திட்டத்திற்கு உள்ளமைக்கப்படவில்லை, எனவே அதைக் குறிப்பிட முடியாது.");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "இந்த நிறைவு பிரிவைக் குறிப்பிட உங்கள் பங்குக்கு அனுமதி இல்லை.");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "புகார் நியாயபாலரிடம் சென்றுவிட்டதால் நிறைவு பிரிவை இப்போது மாற்ற முடியாது.");
        m.put("rbio.implead.error_incomplete_parties",
                "முடிக்கும் முன் சேர்க்கப்பட்ட ஒவ்வொரு தரப்பிற்கும் நிறைவு பிரிவு மற்றும் இழப்பீட்டைப் பதிவு செய்யுங்கள்.");
        m.put("rbio.implead.error_duplicate_party", "இந்தத் தரப்பு ஏற்கனவே புகாரில் சேர்க்கப்பட்டுள்ளது.");
        m.put("rbio.implead.label_party_name", "சேர்க்கப்பட்ட தரப்பு");
        m.put("rbio.implead.label_party_type", "தரப்பு வகை");
        m.put("rbio.implead.label_reason", "சேர்ப்பதற்கான காரணம்");
        m.put("rbio.implead.status_information_required", "தகவல் தேவை");
        m.put("rbio.implead.status_complete", "முழுமையானது");
        m.put("rbio.closure.label_date_of_sending", "அனுப்பிய தேதி");
        m.put("rbio.closure.label_signed_letter", "கையொப்பமிடப்பட்ட நிறைவு கடிதம்");
        m.put("rbio.closure.label_custom_text", "தனிப்பயன் நிறைவு உரை");
        m.put("rbio.closure.sms_closed",
                "உங்கள் புகார் {0} ஆர்பிஐ நியாயபாலரால் முடிக்கப்பட்டது. நிறைவு கடிதத்திற்கு உங்கள் மின்னஞ்சலைப் பார்க்கவும்.");
        m.put("rbio.closure.letter_not_digitally_signed", "இது டிஜிட்டல் முறையில் கையொப்பமிடப்பட்ட ஆவணம் அல்ல.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.closure.error_send_date_required",
                "ફરિયાદ બંધ કરતાં પહેલાં સમાપન પત્ર મોકલવાની તારીખ નોંધો.");
        m.put("rbio.closure.error_signed_letter_required",
                "ફરિયાદ બંધ કરતાં પહેલાં સહી કરેલ સમાપન પત્ર અપલોડ કરો.");
        m.put("rbio.closure.error_email_missing",
                "ફરિયાદીનું ઈમેલ સરનામું નોંધાયેલ ન હોવાથી આ ફરિયાદ સીધી બંધ કરી શકાતી નથી. "
                        + "તેના બદલે સમાધાન/નકાર અથવા નિર્ણય દ્વારા પરિણામ નોંધો.");
        m.put("rbio.closure.error_custom_text_too_long",
                "કસ્ટમ સમાપન લખાણ 2000 અક્ષરોથી વધુ ન હોઈ શકે.");
        m.put("rbio.closure.error_clause_required", "સાચવતાં પહેલાં સમાપન કલમ પસંદ કરો.");
        m.put("rbio.closure.error_clause_unknown",
                "આ સમાપન કલમ લાગુ યોજના માટે કૉન્ફિગર થયેલ નથી, તેથી તેનો ઉલ્લેખ કરી શકાતો નથી.");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "તમારી ભૂમિકાને આ સમાપન કલમનો ઉલ્લેખ કરવાની પરવાનગી નથી.");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "ફરિયાદ લોકપાલ પાસે ગઈ હોવાથી સમાપન કલમ હવે બદલી શકાતી નથી.");
        m.put("rbio.implead.error_incomplete_parties",
                "બંધ કરતાં પહેલાં દરેક સામેલ પક્ષ માટે સમાપન કલમ અને વળતર નોંધો.");
        m.put("rbio.implead.error_duplicate_party", "આ પક્ષ પહેલેથી જ ફરિયાદમાં સામેલ છે.");
        m.put("rbio.implead.label_party_name", "સામેલ પક્ષ");
        m.put("rbio.implead.label_party_type", "પક્ષનો પ્રકાર");
        m.put("rbio.implead.label_reason", "સામેલ કરવાનું કારણ");
        m.put("rbio.implead.status_information_required", "માહિતી જરૂરી");
        m.put("rbio.implead.status_complete", "પૂર્ણ");
        m.put("rbio.closure.label_date_of_sending", "મોકલવાની તારીખ");
        m.put("rbio.closure.label_signed_letter", "સહી કરેલ સમાપન પત્ર");
        m.put("rbio.closure.label_custom_text", "કસ્ટમ સમાપન લખાણ");
        m.put("rbio.closure.sms_closed",
                "તમારી ફરિયાદ {0} આરબીઆઈ લોકપાલ દ્વારા બંધ કરવામાં આવી છે. સમાપન પત્ર માટે તમારું ઈમેલ તપાસો.");
        m.put("rbio.closure.letter_not_digitally_signed", "આ ડિજિટલ રીતે સહી કરેલ દસ્તાવેજ નથી.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.closure.error_send_date_required",
                "شکایت بند کرنے سے پہلے اختتامی خط بھیجنے کی تاریخ درج کریں۔");
        m.put("rbio.closure.error_signed_letter_required",
                "شکایت بند کرنے سے پہلے دستخط شدہ اختتامی خط اپ لوڈ کریں۔");
        m.put("rbio.closure.error_email_missing",
                "شکایت کنندہ کا ای میل پتہ درج نہ ہونے کی وجہ سے یہ شکایت براہ راست بند نہیں کی جا سکتی۔ "
                        + "اس کے بجائے مفاہمت/رد یا فیصلے کے ذریعے نتیجہ درج کریں۔");
        m.put("rbio.closure.error_custom_text_too_long",
                "کسٹم اختتامی متن 2000 حروف سے زیادہ نہیں ہو سکتا۔");
        m.put("rbio.closure.error_clause_required", "محفوظ کرنے سے پہلے اختتامی شق منتخب کریں۔");
        m.put("rbio.closure.error_clause_unknown",
                "یہ اختتامی شق قابل اطلاق اسکیم کے لیے ترتیب نہیں دی گئی، لہٰذا اس کا حوالہ نہیں دیا جا سکتا۔");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "آپ کے کردار کو اس اختتامی شق کا حوالہ دینے کی اجازت نہیں ہے۔");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "شکایت محتسب کے پاس منتقل ہو چکی ہے، لہٰذا اختتامی شق اب تبدیل نہیں کی جا سکتی۔");
        m.put("rbio.implead.error_incomplete_parties",
                "بند کرنے سے پہلے ہر شامل فریق کے لیے اختتامی شق اور معاوضہ درج کریں۔");
        m.put("rbio.implead.error_duplicate_party", "یہ فریق پہلے ہی شکایت میں شامل ہے۔");
        m.put("rbio.implead.label_party_name", "شامل فریق");
        m.put("rbio.implead.label_party_type", "فریق کی قسم");
        m.put("rbio.implead.label_reason", "شامل کرنے کی وجہ");
        m.put("rbio.implead.status_information_required", "معلومات درکار");
        m.put("rbio.implead.status_complete", "مکمل");
        m.put("rbio.closure.label_date_of_sending", "بھیجنے کی تاریخ");
        m.put("rbio.closure.label_signed_letter", "دستخط شدہ اختتامی خط");
        m.put("rbio.closure.label_custom_text", "کسٹم اختتامی متن");
        m.put("rbio.closure.sms_closed",
                "آپ کی شکایت {0} آر بی آئی محتسب کی جانب سے بند کر دی گئی ہے۔ اختتامی خط کے لیے اپنا ای میل دیکھیں۔");
        m.put("rbio.closure.letter_not_digitally_signed", "یہ ڈیجیٹل طور پر دستخط شدہ دستاویز نہیں ہے۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.closure.error_send_date_required",
                "ದೂರನ್ನು ಮುಕ್ತಾಯಗೊಳಿಸುವ ಮೊದಲು ಮುಕ್ತಾಯ ಪತ್ರ ಕಳುಹಿಸಿದ ದಿನಾಂಕವನ್ನು ನಮೂದಿಸಿ.");
        m.put("rbio.closure.error_signed_letter_required",
                "ದೂರನ್ನು ಮುಕ್ತಾಯಗೊಳಿಸುವ ಮೊದಲು ಸಹಿ ಮಾಡಿದ ಮುಕ್ತಾಯ ಪತ್ರವನ್ನು ಅಪ್‌ಲೋಡ್ ಮಾಡಿ.");
        m.put("rbio.closure.error_email_missing",
                "ದೂರುದಾರರ ಇಮೇಲ್ ವಿಳಾಸ ದಾಖಲಾಗಿಲ್ಲದ ಕಾರಣ ಈ ದೂರನ್ನು ನೇರವಾಗಿ ಮುಕ್ತಾಯಗೊಳಿಸಲಾಗುವುದಿಲ್ಲ. "
                        + "ಬದಲಿಗೆ ರಾಜಿ/ನಿರಾಕರಣೆ ಅಥವಾ ನಿರ್ಧಾರದ ಮೂಲಕ ಫಲಿತಾಂಶವನ್ನು ದಾಖಲಿಸಿ.");
        m.put("rbio.closure.error_custom_text_too_long",
                "ಕಸ್ಟಮ್ ಮುಕ್ತಾಯ ಪಠ್ಯ 2000 ಅಕ್ಷರಗಳಿಗಿಂತ ಹೆಚ್ಚಿರಬಾರದು.");
        m.put("rbio.closure.error_clause_required", "ಉಳಿಸುವ ಮೊದಲು ಮುಕ್ತಾಯ ಷರತ್ತನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("rbio.closure.error_clause_unknown",
                "ಈ ಮುಕ್ತಾಯ ಷರತ್ತು ಅನ್ವಯವಾಗುವ ಯೋಜನೆಗೆ ಕಾನ್ಫಿಗರ್ ಆಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಅದನ್ನು ಉಲ್ಲೇಖಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "ಈ ಮುಕ್ತಾಯ ಷರತ್ತನ್ನು ಉಲ್ಲೇಖಿಸಲು ನಿಮ್ಮ ಪಾತ್ರಕ್ಕೆ ಅನುಮತಿ ಇಲ್ಲ.");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "ದೂರು ಲೋಕಪಾಲರಿಗೆ ವರ್ಗಾವಣೆಯಾಗಿರುವುದರಿಂದ ಮುಕ್ತಾಯ ಷರತ್ತನ್ನು ಇನ್ನು ಬದಲಾಯಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("rbio.implead.error_incomplete_parties",
                "ಮುಕ್ತಾಯಗೊಳಿಸುವ ಮೊದಲು ಸೇರಿಸಲಾದ ಪ್ರತಿ ಪಕ್ಷಕ್ಕೂ ಮುಕ್ತಾಯ ಷರತ್ತು ಮತ್ತು ಪರಿಹಾರವನ್ನು ದಾಖಲಿಸಿ.");
        m.put("rbio.implead.error_duplicate_party", "ಈ ಪಕ್ಷ ಈಗಾಗಲೇ ದೂರಿನಲ್ಲಿ ಸೇರಿಸಲಾಗಿದೆ.");
        m.put("rbio.implead.label_party_name", "ಸೇರಿಸಲಾದ ಪಕ್ಷ");
        m.put("rbio.implead.label_party_type", "ಪಕ್ಷದ ಪ್ರಕಾರ");
        m.put("rbio.implead.label_reason", "ಸೇರಿಸುವ ಕಾರಣ");
        m.put("rbio.implead.status_information_required", "ಮಾಹಿತಿ ಅಗತ್ಯವಿದೆ");
        m.put("rbio.implead.status_complete", "ಪೂರ್ಣ");
        m.put("rbio.closure.label_date_of_sending", "ಕಳುಹಿಸಿದ ದಿನಾಂಕ");
        m.put("rbio.closure.label_signed_letter", "ಸಹಿ ಮಾಡಿದ ಮುಕ್ತಾಯ ಪತ್ರ");
        m.put("rbio.closure.label_custom_text", "ಕಸ್ಟಮ್ ಮುಕ್ತಾಯ ಪಠ್ಯ");
        m.put("rbio.closure.sms_closed",
                "ನಿಮ್ಮ ದೂರು {0} ಆರ್‌ಬಿಐ ಲೋಕಪಾಲರಿಂದ ಮುಕ್ತಾಯಗೊಂಡಿದೆ. ಮುಕ್ತಾಯ ಪತ್ರಕ್ಕಾಗಿ ನಿಮ್ಮ ಇಮೇಲ್ ಪರಿಶೀಲಿಸಿ.");
        m.put("rbio.closure.letter_not_digitally_signed", "ಇದು ಡಿಜಿಟಲ್ ಸಹಿ ಮಾಡಿದ ದಾಖಲೆಯಲ್ಲ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rbio.closure.error_send_date_required",
                "പരാതി അവസാനിപ്പിക്കുന്നതിന് മുൻപ് സമാപന കത്ത് അയച്ച തീയതി രേഖപ്പെടുത്തുക.");
        m.put("rbio.closure.error_signed_letter_required",
                "പരാതി അവസാനിപ്പിക്കുന്നതിന് മുൻപ് ഒപ്പിട്ട സമാപന കത്ത് അപ്‌ലോഡ് ചെയ്യുക.");
        m.put("rbio.closure.error_email_missing",
                "പരാതിക്കാരന്റെ ഇമെയിൽ വിലാസം രേഖപ്പെടുത്തിയിട്ടില്ലാത്തതിനാൽ ഈ പരാതി നേരിട്ട് അവസാനിപ്പിക്കാനാവില്ല. "
                        + "പകരം അനുരഞ്ജനം/നിരാകരണം അല്ലെങ്കിൽ തീരുമാനത്തിലൂടെ ഫലം രേഖപ്പെടുത്തുക.");
        m.put("rbio.closure.error_custom_text_too_long",
                "കസ്റ്റം സമാപന വാചകം 2000 അക്ഷരങ്ങളിൽ കൂടരുത്.");
        m.put("rbio.closure.error_clause_required", "സംരക്ഷിക്കുന്നതിന് മുൻപ് സമാപന വ്യവസ്ഥ തിരഞ്ഞെടുക്കുക.");
        m.put("rbio.closure.error_clause_unknown",
                "ഈ സമാപന വ്യവസ്ഥ ബാധകമായ പദ്ധതിക്കായി കോൺഫിഗർ ചെയ്തിട്ടില്ല, അതിനാൽ ഇത് ഉദ്ധരിക്കാനാവില്ല.");
        m.put("rbio.closure.error_clause_not_permitted_for_role",
                "ഈ സമാപന വ്യവസ്ഥ ഉദ്ധരിക്കാൻ നിങ്ങളുടെ റോളിന് അനുമതിയില്ല.");
        m.put("rbio.closure.error_clause_readonly_after_handoff",
                "പരാതി ഓംബുഡ്സ്മാന് കൈമാറിയതിനാൽ സമാപന വ്യവസ്ഥ ഇനി മാറ്റാനാവില്ല.");
        m.put("rbio.implead.error_incomplete_parties",
                "അവസാനിപ്പിക്കുന്നതിന് മുൻപ് ചേർത്ത ഓരോ കക്ഷിക്കും സമാപന വ്യവസ്ഥയും നഷ്ടപരിഹാരവും രേഖപ്പെടുത്തുക.");
        m.put("rbio.implead.error_duplicate_party", "ഈ കക്ഷി ഇതിനകം പരാതിയിൽ ചേർത്തിട്ടുണ്ട്.");
        m.put("rbio.implead.label_party_name", "ചേർത്ത കക്ഷി");
        m.put("rbio.implead.label_party_type", "കക്ഷിയുടെ തരം");
        m.put("rbio.implead.label_reason", "ചേർക്കാനുള്ള കാരണം");
        m.put("rbio.implead.status_information_required", "വിവരം ആവശ്യമാണ്");
        m.put("rbio.implead.status_complete", "പൂർത്തിയായി");
        m.put("rbio.closure.label_date_of_sending", "അയച്ച തീയതി");
        m.put("rbio.closure.label_signed_letter", "ഒപ്പിട്ട സമാപന കത്ത്");
        m.put("rbio.closure.label_custom_text", "കസ്റ്റം സമാപന വാചകം");
        m.put("rbio.closure.sms_closed",
                "നിങ്ങളുടെ പരാതി {0} ആർബിഐ ഓംബുഡ്സ്മാൻ അവസാനിപ്പിച്ചു. സമാപന കത്തിനായി നിങ്ങളുടെ ഇമെയിൽ പരിശോധിക്കുക.");
        m.put("rbio.closure.letter_not_digitally_signed", "ഇത് ഡിജിറ്റലായി ഒപ്പിട്ട രേഖയല്ല.");
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
