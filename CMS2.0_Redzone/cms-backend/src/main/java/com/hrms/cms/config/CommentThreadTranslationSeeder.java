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
import java.util.List;
import java.util.Map;

/**
 * Vocabulary for the shared staff comment thread.
 *
 * <p>THE WORDING OF THE THREE TIERS IS THE LOAD-BEARING PART. "Public" is a trap: an officer reading it
 * will reasonably assume the complainant can see the comment, and will then either self-censor a
 * legitimate remark or — far worse — write one believing it is a reply to the citizen. Every locale
 * below therefore renders PUBLIC as "all RBI staff", never as "public", and the section carries a
 * standing notice that a complainant never sees any of it. Translators must preserve that distinction;
 * a literal rendering of "public" in any locale reintroduces the hazard.
 *
 * <p>A NEW seeder at {@code @Order(68)} rather than an edit to an existing one, per the convention here:
 * concurrent sessions add keys and a shared file is a guaranteed conflict, where a conflict silently
 * costs a locale. 1-22, 32-34, 38, 42-44 and 60-67 were verified taken before choosing 68.
 *
 * <p>Seeding is insert-if-absent by key code. The {@code comments.*} namespace was verified unused
 * before this was written — the pre-existing comment-ish features live under {@code query.*}.
 */
@Component
@Order(68)
public class CommentThreadTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "comments";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public CommentThreadTranslationSeeder(TranslationKeyRepository keyRepo,
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
        m.put("comments.section_title", "Comments");
        m.put("comments.staff_only_notice",
            "Comments are internal to RBI. The complainant never sees them, whatever their visibility.");
        m.put("comments.add_label", "Add a comment");
        m.put("comments.add_placeholder", "Enter your comment");
        m.put("comments.post_button", "Post Comment");
        m.put("comments.reply_button", "Reply");
        m.put("comments.post_reply_button", "Post Reply");
        m.put("comments.reply_placeholder", "Write a reply");
        m.put("comments.reply_inherits_visibility", "This reply will be visible to:");
        m.put("comments.edit_button", "Edit");
        m.put("comments.save_button", "Save");
        m.put("comments.cancel_button", "Cancel");
        m.put("comments.edited_marker", "edited");
        m.put("comments.none_yet", "No comments yet.");
        m.put("comments.restricted_to", "Visible to:");
        m.put("comments.audience_label", "Who can see this");
        m.put("comments.visibility.legend", "Comment visibility");
        m.put("comments.visibility.public", "All RBI staff");
        m.put("comments.visibility.public.hint", "Every RBI user working on this complaint can read this.");
        m.put("comments.visibility.restricted", "Selected roles");
        m.put("comments.visibility.restricted.hint", "Only you and the roles you select can read this.");
        m.put("comments.visibility.private", "Only me");
        m.put("comments.visibility.private.hint", "Only you can read this. Not even your supervisor.");
        m.put("comments.error.load_failed", "Comments could not be loaded.");
        m.put("comments.error.post_failed", "The comment could not be posted. Nothing has been saved.");
        m.put("comments.error.body_required", "Enter a comment before posting.");
        m.put("comments.error.audience_required", "Select at least one role, or change the visibility.");
        m.put("comments.error.edit_refused", "This comment could not be edited. Only its author may change it.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "टिप्पणियाँ");
        m.put("comments.staff_only_notice",
            "टिप्पणियाँ भारतीय रिज़र्व बैंक के आंतरिक उपयोग हेतु हैं। दृश्यता चाहे जो हो, शिकायतकर्ता इन्हें कभी नहीं देखता।");
        m.put("comments.add_label", "टिप्पणी जोड़ें");
        m.put("comments.add_placeholder", "अपनी टिप्पणी दर्ज करें");
        m.put("comments.post_button", "टिप्पणी प्रकाशित करें");
        m.put("comments.reply_button", "उत्तर दें");
        m.put("comments.post_reply_button", "उत्तर प्रकाशित करें");
        m.put("comments.reply_placeholder", "उत्तर लिखें");
        m.put("comments.reply_inherits_visibility", "यह उत्तर इन्हें दिखाई देगा:");
        m.put("comments.edit_button", "संपादित करें");
        m.put("comments.save_button", "सहेजें");
        m.put("comments.cancel_button", "रद्द करें");
        m.put("comments.edited_marker", "संपादित");
        m.put("comments.none_yet", "अभी कोई टिप्पणी नहीं।");
        m.put("comments.restricted_to", "इन्हें दिखाई देगा:");
        m.put("comments.audience_label", "इसे कौन देख सकता है");
        m.put("comments.visibility.legend", "टिप्पणी की दृश्यता");
        m.put("comments.visibility.public", "सभी आरबीआई कर्मचारी");
        m.put("comments.visibility.public.hint", "इस शिकायत पर कार्यरत प्रत्येक आरबीआई उपयोगकर्ता इसे पढ़ सकता है।");
        m.put("comments.visibility.restricted", "चयनित भूमिकाएँ");
        m.put("comments.visibility.restricted.hint", "केवल आप और आपके द्वारा चयनित भूमिकाएँ इसे पढ़ सकती हैं।");
        m.put("comments.visibility.private", "केवल मैं");
        m.put("comments.visibility.private.hint", "केवल आप इसे पढ़ सकते हैं। आपके पर्यवेक्षक भी नहीं।");
        m.put("comments.error.load_failed", "टिप्पणियाँ लोड नहीं हो सकीं।");
        m.put("comments.error.post_failed", "टिप्पणी प्रकाशित नहीं हो सकी। कुछ भी सहेजा नहीं गया है।");
        m.put("comments.error.body_required", "प्रकाशित करने से पहले टिप्पणी दर्ज करें।");
        m.put("comments.error.audience_required", "कम से कम एक भूमिका चुनें, या दृश्यता बदलें।");
        m.put("comments.error.edit_refused", "यह टिप्पणी संपादित नहीं की जा सकी। केवल इसका लेखक इसे बदल सकता है।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "टिप्पण्या");
        m.put("comments.staff_only_notice",
            "टिप्पण्या भारतीय रिझर्व्ह बँकेच्या अंतर्गत वापरासाठी आहेत. दृश्यता कोणतीही असली तरी तक्रारदार त्या कधीही पाहत नाही.");
        m.put("comments.add_label", "टिप्पणी जोडा");
        m.put("comments.add_placeholder", "आपली टिप्पणी नोंदवा");
        m.put("comments.post_button", "टिप्पणी प्रकाशित करा");
        m.put("comments.reply_button", "उत्तर द्या");
        m.put("comments.post_reply_button", "उत्तर प्रकाशित करा");
        m.put("comments.reply_placeholder", "उत्तर लिहा");
        m.put("comments.reply_inherits_visibility", "हे उत्तर यांना दिसेल:");
        m.put("comments.edit_button", "संपादित करा");
        m.put("comments.save_button", "जतन करा");
        m.put("comments.cancel_button", "रद्द करा");
        m.put("comments.edited_marker", "संपादित");
        m.put("comments.none_yet", "अद्याप कोणतीही टिप्पणी नाही.");
        m.put("comments.restricted_to", "यांना दिसेल:");
        m.put("comments.audience_label", "हे कोण पाहू शकते");
        m.put("comments.visibility.legend", "टिप्पणीची दृश्यता");
        m.put("comments.visibility.public", "सर्व आरबीआय कर्मचारी");
        m.put("comments.visibility.public.hint", "या तक्रारीवर काम करणारा प्रत्येक आरबीआय वापरकर्ता हे वाचू शकतो.");
        m.put("comments.visibility.restricted", "निवडलेल्या भूमिका");
        m.put("comments.visibility.restricted.hint", "केवळ आपण आणि आपण निवडलेल्या भूमिका हे वाचू शकतात.");
        m.put("comments.visibility.private", "केवळ मी");
        m.put("comments.visibility.private.hint", "केवळ आपण हे वाचू शकता. आपले पर्यवेक्षकही नाही.");
        m.put("comments.error.load_failed", "टिप्पण्या लोड होऊ शकल्या नाहीत.");
        m.put("comments.error.post_failed", "टिप्पणी प्रकाशित होऊ शकली नाही. काहीही जतन झाले नाही.");
        m.put("comments.error.body_required", "प्रकाशित करण्यापूर्वी टिप्पणी नोंदवा.");
        m.put("comments.error.audience_required", "कमीत कमी एक भूमिका निवडा, किंवा दृश्यता बदला.");
        m.put("comments.error.edit_refused", "ही टिप्पणी संपादित करता आली नाही. केवळ तिचा लेखकच ती बदलू शकतो.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "মন্তব্য");
        m.put("comments.staff_only_notice",
            "মন্তব্যগুলি ভারতীয় রিজার্ভ ব্যাঙ্কের অভ্যন্তরীণ। দৃশ্যমানতা যা-ই হোক, অভিযোগকারী কখনও এগুলি দেখতে পান না।");
        m.put("comments.add_label", "মন্তব্য যোগ করুন");
        m.put("comments.add_placeholder", "আপনার মন্তব্য লিখুন");
        m.put("comments.post_button", "মন্তব্য প্রকাশ করুন");
        m.put("comments.reply_button", "উত্তর দিন");
        m.put("comments.post_reply_button", "উত্তর প্রকাশ করুন");
        m.put("comments.reply_placeholder", "উত্তর লিখুন");
        m.put("comments.reply_inherits_visibility", "এই উত্তরটি দেখতে পাবেন:");
        m.put("comments.edit_button", "সম্পাদনা");
        m.put("comments.save_button", "সংরক্ষণ");
        m.put("comments.cancel_button", "বাতিল");
        m.put("comments.edited_marker", "সম্পাদিত");
        m.put("comments.none_yet", "এখনও কোনও মন্তব্য নেই।");
        m.put("comments.restricted_to", "দেখতে পাবেন:");
        m.put("comments.audience_label", "কারা এটি দেখতে পাবেন");
        m.put("comments.visibility.legend", "মন্তব্যের দৃশ্যমানতা");
        m.put("comments.visibility.public", "সমস্ত আরবিআই কর্মী");
        m.put("comments.visibility.public.hint", "এই অভিযোগে কর্মরত প্রত্যেক আরবিআই ব্যবহারকারী এটি পড়তে পারেন।");
        m.put("comments.visibility.restricted", "নির্বাচিত ভূমিকা");
        m.put("comments.visibility.restricted.hint", "শুধুমাত্র আপনি এবং আপনার নির্বাচিত ভূমিকাগুলি এটি পড়তে পারে।");
        m.put("comments.visibility.private", "শুধু আমি");
        m.put("comments.visibility.private.hint", "শুধুমাত্র আপনি এটি পড়তে পারেন। আপনার পর্যবেক্ষকও নন।");
        m.put("comments.error.load_failed", "মন্তব্য লোড করা যায়নি।");
        m.put("comments.error.post_failed", "মন্তব্যটি প্রকাশ করা যায়নি। কিছুই সংরক্ষিত হয়নি।");
        m.put("comments.error.body_required", "প্রকাশ করার আগে একটি মন্তব্য লিখুন।");
        m.put("comments.error.audience_required", "অন্তত একটি ভূমিকা নির্বাচন করুন, বা দৃশ্যমানতা পরিবর্তন করুন।");
        m.put("comments.error.edit_refused", "এই মন্তব্যটি সম্পাদনা করা যায়নি। কেবল এর লেখকই এটি পরিবর্তন করতে পারেন।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "వ్యాఖ్యలు");
        m.put("comments.staff_only_notice",
            "వ్యాఖ్యలు ఆర్‌బీఐ అంతర్గత వినియోగం కోసం. దృశ్యమానత ఏదైనా, ఫిర్యాదుదారు వీటిని ఎప్పుడూ చూడరు.");
        m.put("comments.add_label", "వ్యాఖ్య జోడించండి");
        m.put("comments.add_placeholder", "మీ వ్యాఖ్యను నమోదు చేయండి");
        m.put("comments.post_button", "వ్యాఖ్యను ప్రచురించండి");
        m.put("comments.reply_button", "ప్రత్యుత్తరం");
        m.put("comments.post_reply_button", "ప్రత్యుత్తరం ప్రచురించండి");
        m.put("comments.reply_placeholder", "ప్రత్యుత్తరం వ్రాయండి");
        m.put("comments.reply_inherits_visibility", "ఈ ప్రత్యుత్తరం వీరికి కనిపిస్తుంది:");
        m.put("comments.edit_button", "సవరించు");
        m.put("comments.save_button", "భద్రపరచు");
        m.put("comments.cancel_button", "రద్దు");
        m.put("comments.edited_marker", "సవరించబడింది");
        m.put("comments.none_yet", "ఇంకా వ్యాఖ్యలు లేవు.");
        m.put("comments.restricted_to", "వీరికి కనిపిస్తుంది:");
        m.put("comments.audience_label", "దీన్ని ఎవరు చూడగలరు");
        m.put("comments.visibility.legend", "వ్యాఖ్య దృశ్యమానత");
        m.put("comments.visibility.public", "అందరు ఆర్‌బీఐ సిబ్బంది");
        m.put("comments.visibility.public.hint", "ఈ ఫిర్యాదుపై పనిచేసే ప్రతి ఆర్‌బీఐ వినియోగదారు దీన్ని చదవగలరు.");
        m.put("comments.visibility.restricted", "ఎంచుకున్న పాత్రలు");
        m.put("comments.visibility.restricted.hint", "మీరు మరియు మీరు ఎంచుకున్న పాత్రలు మాత్రమే దీన్ని చదవగలరు.");
        m.put("comments.visibility.private", "నేను మాత్రమే");
        m.put("comments.visibility.private.hint", "మీరు మాత్రమే దీన్ని చదవగలరు. మీ పర్యవేక్షకుడు కూడా కాదు.");
        m.put("comments.error.load_failed", "వ్యాఖ్యలను లోడ్ చేయడం సాధ్యం కాలేదు.");
        m.put("comments.error.post_failed", "వ్యాఖ్యను ప్రచురించడం సాధ్యం కాలేదు. ఏదీ భద్రపరచబడలేదు.");
        m.put("comments.error.body_required", "ప్రచురించే ముందు వ్యాఖ్యను నమోదు చేయండి.");
        m.put("comments.error.audience_required", "కనీసం ఒక పాత్రను ఎంచుకోండి, లేదా దృశ్యమానతను మార్చండి.");
        m.put("comments.error.edit_refused", "ఈ వ్యాఖ్యను సవరించలేకపోయాము. దీని రచయిత మాత్రమే మార్చగలరు.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "கருத்துகள்");
        m.put("comments.staff_only_notice",
            "கருத்துகள் ஆர்பிஐ-யின் உள்ளார்ந்த பயன்பாட்டுக்கானவை. தெரிவுநிலை எதுவாக இருந்தாலும், புகார்தாரர் இவற்றைப் பார்ப்பதில்லை.");
        m.put("comments.add_label", "கருத்து சேர்க்கவும்");
        m.put("comments.add_placeholder", "உங்கள் கருத்தை உள்ளிடவும்");
        m.put("comments.post_button", "கருத்தை வெளியிடு");
        m.put("comments.reply_button", "பதிலளி");
        m.put("comments.post_reply_button", "பதிலை வெளியிடு");
        m.put("comments.reply_placeholder", "பதில் எழுதுங்கள்");
        m.put("comments.reply_inherits_visibility", "இந்தப் பதில் இவர்களுக்குத் தெரியும்:");
        m.put("comments.edit_button", "திருத்து");
        m.put("comments.save_button", "சேமி");
        m.put("comments.cancel_button", "ரத்து");
        m.put("comments.edited_marker", "திருத்தப்பட்டது");
        m.put("comments.none_yet", "இன்னும் கருத்துகள் இல்லை.");
        m.put("comments.restricted_to", "இவர்களுக்குத் தெரியும்:");
        m.put("comments.audience_label", "இதை யார் பார்க்க முடியும்");
        m.put("comments.visibility.legend", "கருத்தின் தெரிவுநிலை");
        m.put("comments.visibility.public", "அனைத்து ஆர்பிஐ பணியாளர்கள்");
        m.put("comments.visibility.public.hint", "இந்தப் புகாரில் பணியாற்றும் ஒவ்வொரு ஆர்பிஐ பயனரும் இதைப் படிக்கலாம்.");
        m.put("comments.visibility.restricted", "தேர்ந்தெடுக்கப்பட்ட பங்குகள்");
        m.put("comments.visibility.restricted.hint", "நீங்களும் நீங்கள் தேர்ந்தெடுத்த பங்குகளும் மட்டுமே இதைப் படிக்க முடியும்.");
        m.put("comments.visibility.private", "நான் மட்டும்");
        m.put("comments.visibility.private.hint", "நீங்கள் மட்டுமே இதைப் படிக்க முடியும். உங்கள் மேற்பார்வையாளரும் அல்ல.");
        m.put("comments.error.load_failed", "கருத்துகளை ஏற்ற முடியவில்லை.");
        m.put("comments.error.post_failed", "கருத்தை வெளியிட முடியவில்லை. எதுவும் சேமிக்கப்படவில்லை.");
        m.put("comments.error.body_required", "வெளியிடுவதற்கு முன் ஒரு கருத்தை உள்ளிடவும்.");
        m.put("comments.error.audience_required", "குறைந்தது ஒரு பங்கைத் தேர்ந்தெடுக்கவும், அல்லது தெரிவுநிலையை மாற்றவும்.");
        m.put("comments.error.edit_refused", "இந்தக் கருத்தைத் திருத்த முடியவில்லை. அதை எழுதியவர் மட்டுமே மாற்ற முடியும்.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "ટિપ્પણીઓ");
        m.put("comments.staff_only_notice",
            "ટિપ્પણીઓ આરબીઆઈના આંતરિક ઉપયોગ માટે છે. દૃશ્યતા ભલે કોઈ પણ હોય, ફરિયાદકર્તા તેને કદી જોતા નથી.");
        m.put("comments.add_label", "ટિપ્પણી ઉમેરો");
        m.put("comments.add_placeholder", "તમારી ટિપ્પણી દાખલ કરો");
        m.put("comments.post_button", "ટિપ્પણી પ્રકાશિત કરો");
        m.put("comments.reply_button", "જવાબ આપો");
        m.put("comments.post_reply_button", "જવાબ પ્રકાશિત કરો");
        m.put("comments.reply_placeholder", "જવાબ લખો");
        m.put("comments.reply_inherits_visibility", "આ જવાબ આમને દેખાશે:");
        m.put("comments.edit_button", "સંપાદિત કરો");
        m.put("comments.save_button", "સાચવો");
        m.put("comments.cancel_button", "રદ કરો");
        m.put("comments.edited_marker", "સંપાદિત");
        m.put("comments.none_yet", "હજી કોઈ ટિપ્પણી નથી.");
        m.put("comments.restricted_to", "આમને દેખાશે:");
        m.put("comments.audience_label", "આ કોણ જોઈ શકે છે");
        m.put("comments.visibility.legend", "ટિપ્પણીની દૃશ્યતા");
        m.put("comments.visibility.public", "બધા આરબીઆઈ કર્મચારીઓ");
        m.put("comments.visibility.public.hint", "આ ફરિયાદ પર કામ કરતા દરેક આરબીઆઈ વપરાશકર્તા આ વાંચી શકે છે.");
        m.put("comments.visibility.restricted", "પસંદ કરેલી ભૂમિકાઓ");
        m.put("comments.visibility.restricted.hint", "ફક્ત તમે અને તમે પસંદ કરેલી ભૂમિકાઓ આ વાંચી શકે છે.");
        m.put("comments.visibility.private", "ફક્ત હું");
        m.put("comments.visibility.private.hint", "ફક્ત તમે આ વાંચી શકો છો. તમારા નિરીક્ષક પણ નહીં.");
        m.put("comments.error.load_failed", "ટિપ્પણીઓ લોડ થઈ શકી નથી.");
        m.put("comments.error.post_failed", "ટિપ્પણી પ્રકાશિત થઈ શકી નથી. કંઈ સાચવાયું નથી.");
        m.put("comments.error.body_required", "પ્રકાશિત કરતાં પહેલાં ટિપ્પણી દાખલ કરો.");
        m.put("comments.error.audience_required", "ઓછામાં ઓછી એક ભૂમિકા પસંદ કરો, અથવા દૃશ્યતા બદલો.");
        m.put("comments.error.edit_refused", "આ ટિપ્પણી સંપાદિત કરી શકાઈ નથી. ફક્ત તેના લેખક જ તેને બદલી શકે છે.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "تبصرے");
        m.put("comments.staff_only_notice",
            "تبصرے آر بی آئی کے داخلی استعمال کے لیے ہیں۔ دکھائی دینے کی سطح کچھ بھی ہو، شکایت کنندہ انہیں کبھی نہیں دیکھتا۔");
        m.put("comments.add_label", "تبصرہ شامل کریں");
        m.put("comments.add_placeholder", "اپنا تبصرہ درج کریں");
        m.put("comments.post_button", "تبصرہ شائع کریں");
        m.put("comments.reply_button", "جواب دیں");
        m.put("comments.post_reply_button", "جواب شائع کریں");
        m.put("comments.reply_placeholder", "جواب لکھیں");
        m.put("comments.reply_inherits_visibility", "یہ جواب انہیں دکھائی دے گا:");
        m.put("comments.edit_button", "ترمیم");
        m.put("comments.save_button", "محفوظ کریں");
        m.put("comments.cancel_button", "منسوخ");
        m.put("comments.edited_marker", "ترمیم شدہ");
        m.put("comments.none_yet", "ابھی کوئی تبصرہ نہیں۔");
        m.put("comments.restricted_to", "انہیں دکھائی دے گا:");
        m.put("comments.audience_label", "اسے کون دیکھ سکتا ہے");
        m.put("comments.visibility.legend", "تبصرے کی دکھائی دینے کی سطح");
        m.put("comments.visibility.public", "تمام آر بی آئی عملہ");
        m.put("comments.visibility.public.hint", "اس شکایت پر کام کرنے والا ہر آر بی آئی صارف اسے پڑھ سکتا ہے۔");
        m.put("comments.visibility.restricted", "منتخب کردہ کردار");
        m.put("comments.visibility.restricted.hint", "صرف آپ اور آپ کے منتخب کردہ کردار اسے پڑھ سکتے ہیں۔");
        m.put("comments.visibility.private", "صرف میں");
        m.put("comments.visibility.private.hint", "صرف آپ اسے پڑھ سکتے ہیں۔ آپ کے نگران بھی نہیں۔");
        m.put("comments.error.load_failed", "تبصرے لوڈ نہیں ہو سکے۔");
        m.put("comments.error.post_failed", "تبصرہ شائع نہیں ہو سکا۔ کچھ بھی محفوظ نہیں ہوا۔");
        m.put("comments.error.body_required", "شائع کرنے سے پہلے تبصرہ درج کریں۔");
        m.put("comments.error.audience_required", "کم از کم ایک کردار منتخب کریں، یا دکھائی دینے کی سطح تبدیل کریں۔");
        m.put("comments.error.edit_refused", "اس تبصرے میں ترمیم نہیں ہو سکی۔ صرف اس کا لکھنے والا اسے بدل سکتا ہے۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "ಕಾಮೆಂಟ್‌ಗಳು");
        m.put("comments.staff_only_notice",
            "ಕಾಮೆಂಟ್‌ಗಳು ಆರ್‌ಬಿಐನ ಆಂತರಿಕ ಬಳಕೆಗಾಗಿ. ಗೋಚರತೆ ಏನೇ ಇರಲಿ, ದೂರುದಾರರು ಇವುಗಳನ್ನು ಎಂದಿಗೂ ನೋಡುವುದಿಲ್ಲ.");
        m.put("comments.add_label", "ಕಾಮೆಂಟ್ ಸೇರಿಸಿ");
        m.put("comments.add_placeholder", "ನಿಮ್ಮ ಕಾಮೆಂಟ್ ನಮೂದಿಸಿ");
        m.put("comments.post_button", "ಕಾಮೆಂಟ್ ಪ್ರಕಟಿಸಿ");
        m.put("comments.reply_button", "ಪ್ರತ್ಯುತ್ತರ");
        m.put("comments.post_reply_button", "ಪ್ರತ್ಯುತ್ತರ ಪ್ರಕಟಿಸಿ");
        m.put("comments.reply_placeholder", "ಪ್ರತ್ಯುತ್ತರ ಬರೆಯಿರಿ");
        m.put("comments.reply_inherits_visibility", "ಈ ಪ್ರತ್ಯುತ್ತರ ಇವರಿಗೆ ಗೋಚರಿಸುತ್ತದೆ:");
        m.put("comments.edit_button", "ಸಂಪಾದಿಸಿ");
        m.put("comments.save_button", "ಉಳಿಸಿ");
        m.put("comments.cancel_button", "ರದ್ದುಮಾಡಿ");
        m.put("comments.edited_marker", "ಸಂಪಾದಿಸಲಾಗಿದೆ");
        m.put("comments.none_yet", "ಇನ್ನೂ ಯಾವುದೇ ಕಾಮೆಂಟ್ ಇಲ್ಲ.");
        m.put("comments.restricted_to", "ಇವರಿಗೆ ಗೋಚರಿಸುತ್ತದೆ:");
        m.put("comments.audience_label", "ಇದನ್ನು ಯಾರು ನೋಡಬಹುದು");
        m.put("comments.visibility.legend", "ಕಾಮೆಂಟ್ ಗೋಚರತೆ");
        m.put("comments.visibility.public", "ಎಲ್ಲಾ ಆರ್‌ಬಿಐ ಸಿಬ್ಬಂದಿ");
        m.put("comments.visibility.public.hint", "ಈ ದೂರಿನ ಮೇಲೆ ಕೆಲಸ ಮಾಡುವ ಪ್ರತಿಯೊಬ್ಬ ಆರ್‌ಬಿಐ ಬಳಕೆದಾರರು ಇದನ್ನು ಓದಬಹುದು.");
        m.put("comments.visibility.restricted", "ಆಯ್ದ ಪಾತ್ರಗಳು");
        m.put("comments.visibility.restricted.hint", "ನೀವು ಮತ್ತು ನೀವು ಆಯ್ಕೆ ಮಾಡಿದ ಪಾತ್ರಗಳು ಮಾತ್ರ ಇದನ್ನು ಓದಬಹುದು.");
        m.put("comments.visibility.private", "ನಾನು ಮಾತ್ರ");
        m.put("comments.visibility.private.hint", "ನೀವು ಮಾತ್ರ ಇದನ್ನು ಓದಬಹುದು. ನಿಮ್ಮ ಮೇಲ್ವಿಚಾರಕರೂ ಅಲ್ಲ.");
        m.put("comments.error.load_failed", "ಕಾಮೆಂಟ್‌ಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ.");
        m.put("comments.error.post_failed", "ಕಾಮೆಂಟ್ ಪ್ರಕಟಿಸಲಾಗಲಿಲ್ಲ. ಏನನ್ನೂ ಉಳಿಸಲಾಗಿಲ್ಲ.");
        m.put("comments.error.body_required", "ಪ್ರಕಟಿಸುವ ಮೊದಲು ಕಾಮೆಂಟ್ ನಮೂದಿಸಿ.");
        m.put("comments.error.audience_required", "ಕನಿಷ್ಠ ಒಂದು ಪಾತ್ರವನ್ನು ಆಯ್ಕೆಮಾಡಿ, ಅಥವಾ ಗೋಚರತೆಯನ್ನು ಬದಲಾಯಿಸಿ.");
        m.put("comments.error.edit_refused", "ಈ ಕಾಮೆಂಟ್ ಸಂಪಾದಿಸಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ. ಅದನ್ನು ಬರೆದವರು ಮಾತ್ರ ಬದಲಾಯಿಸಬಹುದು.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "അഭിപ്രായങ്ങൾ");
        m.put("comments.staff_only_notice",
            "അഭിപ്രായങ്ങൾ ആർബിഐയുടെ ആന്തരിക ഉപയോഗത്തിനാണ്. ദൃശ്യപരത എന്തായാലും, പരാതിക്കാരൻ ഇവ ഒരിക്കലും കാണുന്നില്ല.");
        m.put("comments.add_label", "അഭിപ്രായം ചേർക്കുക");
        m.put("comments.add_placeholder", "നിങ്ങളുടെ അഭിപ്രായം നൽകുക");
        m.put("comments.post_button", "അഭിപ്രായം പ്രസിദ്ധീകരിക്കുക");
        m.put("comments.reply_button", "മറുപടി");
        m.put("comments.post_reply_button", "മറുപടി പ്രസിദ്ധീകരിക്കുക");
        m.put("comments.reply_placeholder", "മറുപടി എഴുതുക");
        m.put("comments.reply_inherits_visibility", "ഈ മറുപടി ഇവർക്ക് കാണാനാകും:");
        m.put("comments.edit_button", "തിരുത്തുക");
        m.put("comments.save_button", "സംരക്ഷിക്കുക");
        m.put("comments.cancel_button", "റദ്ദാക്കുക");
        m.put("comments.edited_marker", "തിരുത്തി");
        m.put("comments.none_yet", "ഇതുവരെ അഭിപ്രായങ്ങളില്ല.");
        m.put("comments.restricted_to", "ഇവർക്ക് കാണാനാകും:");
        m.put("comments.audience_label", "ഇത് ആർക്ക് കാണാനാകും");
        m.put("comments.visibility.legend", "അഭിപ്രായത്തിന്റെ ദൃശ്യപരത");
        m.put("comments.visibility.public", "എല്ലാ ആർബിഐ ഉദ്യോഗസ്ഥരും");
        m.put("comments.visibility.public.hint", "ഈ പരാതിയിൽ പ്രവർത്തിക്കുന്ന എല്ലാ ആർബിഐ ഉപയോക്താക്കൾക്കും ഇത് വായിക്കാം.");
        m.put("comments.visibility.restricted", "തിരഞ്ഞെടുത്ത റോളുകൾ");
        m.put("comments.visibility.restricted.hint", "നിങ്ങൾക്കും നിങ്ങൾ തിരഞ്ഞെടുത്ത റോളുകൾക്കും മാത്രം ഇത് വായിക്കാം.");
        m.put("comments.visibility.private", "ഞാൻ മാത്രം");
        m.put("comments.visibility.private.hint", "നിങ്ങൾക്ക് മാത്രം ഇത് വായിക്കാം. നിങ്ങളുടെ മേലുദ്യോഗസ്ഥനും കഴിയില്ല.");
        m.put("comments.error.load_failed", "അഭിപ്രായങ്ങൾ ലോഡ് ചെയ്യാനായില്ല.");
        m.put("comments.error.post_failed", "അഭിപ്രായം പ്രസിദ്ധീകരിക്കാനായില്ല. ഒന്നും സംരക്ഷിച്ചിട്ടില്ല.");
        m.put("comments.error.body_required", "പ്രസിദ്ധീകരിക്കുന്നതിന് മുൻപ് ഒരു അഭിപ്രായം നൽകുക.");
        m.put("comments.error.audience_required", "കുറഞ്ഞത് ഒരു റോൾ തിരഞ്ഞെടുക്കുക, അല്ലെങ്കിൽ ദൃശ്യപരത മാറ്റുക.");
        m.put("comments.error.edit_refused", "ഈ അഭിപ്രായം തിരുത്താൻ കഴിഞ്ഞില്ല. അത് എഴുതിയ ആൾക്ക് മാത്രമേ മാറ്റാനാകൂ.");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("comments.section_title", "ਟਿੱਪਣੀਆਂ");
        m.put("comments.staff_only_notice",
            "ਟਿੱਪਣੀਆਂ ਆਰਬੀਆਈ ਦੀ ਅੰਦਰੂਨੀ ਵਰਤੋਂ ਲਈ ਹਨ। ਦਿੱਖ ਭਾਵੇਂ ਕੋਈ ਵੀ ਹੋਵੇ, ਸ਼ਿਕਾਇਤਕਰਤਾ ਇਹਨਾਂ ਨੂੰ ਕਦੇ ਨਹੀਂ ਦੇਖਦਾ।");
        m.put("comments.add_label", "ਟਿੱਪਣੀ ਜੋੜੋ");
        m.put("comments.add_placeholder", "ਆਪਣੀ ਟਿੱਪਣੀ ਦਰਜ ਕਰੋ");
        m.put("comments.post_button", "ਟਿੱਪਣੀ ਪ੍ਰਕਾਸ਼ਿਤ ਕਰੋ");
        m.put("comments.reply_button", "ਜਵਾਬ ਦਿਓ");
        m.put("comments.post_reply_button", "ਜਵਾਬ ਪ੍ਰਕਾਸ਼ਿਤ ਕਰੋ");
        m.put("comments.reply_placeholder", "ਜਵਾਬ ਲਿਖੋ");
        m.put("comments.reply_inherits_visibility", "ਇਹ ਜਵਾਬ ਇਹਨਾਂ ਨੂੰ ਦਿਖਾਈ ਦੇਵੇਗਾ:");
        m.put("comments.edit_button", "ਸੋਧੋ");
        m.put("comments.save_button", "ਸੰਭਾਲੋ");
        m.put("comments.cancel_button", "ਰੱਦ ਕਰੋ");
        m.put("comments.edited_marker", "ਸੋਧਿਆ");
        m.put("comments.none_yet", "ਹਾਲੇ ਕੋਈ ਟਿੱਪਣੀ ਨਹੀਂ।");
        m.put("comments.restricted_to", "ਇਹਨਾਂ ਨੂੰ ਦਿਖਾਈ ਦੇਵੇਗਾ:");
        m.put("comments.audience_label", "ਇਹ ਕੌਣ ਦੇਖ ਸਕਦਾ ਹੈ");
        m.put("comments.visibility.legend", "ਟਿੱਪਣੀ ਦੀ ਦਿੱਖ");
        m.put("comments.visibility.public", "ਸਾਰੇ ਆਰਬੀਆਈ ਕਰਮਚਾਰੀ");
        m.put("comments.visibility.public.hint", "ਇਸ ਸ਼ਿਕਾਇਤ 'ਤੇ ਕੰਮ ਕਰਨ ਵਾਲਾ ਹਰ ਆਰਬੀਆਈ ਵਰਤੋਂਕਾਰ ਇਸਨੂੰ ਪੜ੍ਹ ਸਕਦਾ ਹੈ।");
        m.put("comments.visibility.restricted", "ਚੁਣੀਆਂ ਭੂਮਿਕਾਵਾਂ");
        m.put("comments.visibility.restricted.hint", "ਸਿਰਫ਼ ਤੁਸੀਂ ਅਤੇ ਤੁਹਾਡੀਆਂ ਚੁਣੀਆਂ ਭੂਮਿਕਾਵਾਂ ਇਸਨੂੰ ਪੜ੍ਹ ਸਕਦੀਆਂ ਹਨ।");
        m.put("comments.visibility.private", "ਸਿਰਫ਼ ਮੈਂ");
        m.put("comments.visibility.private.hint", "ਸਿਰਫ਼ ਤੁਸੀਂ ਇਸਨੂੰ ਪੜ੍ਹ ਸਕਦੇ ਹੋ। ਤੁਹਾਡਾ ਨਿਗਰਾਨ ਵੀ ਨਹੀਂ।");
        m.put("comments.error.load_failed", "ਟਿੱਪਣੀਆਂ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕੀਆਂ।");
        m.put("comments.error.post_failed", "ਟਿੱਪਣੀ ਪ੍ਰਕਾਸ਼ਿਤ ਨਹੀਂ ਹੋ ਸਕੀ। ਕੁਝ ਵੀ ਸੰਭਾਲਿਆ ਨਹੀਂ ਗਿਆ।");
        m.put("comments.error.body_required", "ਪ੍ਰਕਾਸ਼ਿਤ ਕਰਨ ਤੋਂ ਪਹਿਲਾਂ ਟਿੱਪਣੀ ਦਰਜ ਕਰੋ।");
        m.put("comments.error.audience_required", "ਘੱਟੋ-ਘੱਟ ਇੱਕ ਭੂਮਿਕਾ ਚੁਣੋ, ਜਾਂ ਦਿੱਖ ਬਦਲੋ।");
        m.put("comments.error.edit_refused", "ਇਹ ਟਿੱਪਣੀ ਸੋਧੀ ਨਹੀਂ ਜਾ ਸਕੀ। ਸਿਰਫ਼ ਇਸ ਦਾ ਲੇਖਕ ਹੀ ਇਸ ਨੂੰ ਬਦਲ ਸਕਦਾ ਹੈ।");
        return m;
    }

    private void seed(String code, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(MODULE);
        key.setDefaultValue(defaultValue);
        keyRepo.save(key);
        putTranslation(key, "en", defaultValue);
    }

    private void seedLocale(String locale, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            keyRepo.findByCode(entry.getKey())
                .ifPresent(key -> putTranslation(key, locale, entry.getValue()));
        }
    }

    private void putTranslation(TranslationKey key, String locale, String value) {
        List<Translation> existing = translationRepo.findByKeyIdAndLocale(key.getId(), locale);
        if (existing.isEmpty()) {
            Translation t = new Translation();
            t.setTranslationKey(key);
            t.setLocale(locale);
            t.setValue(value);
            translationRepo.save(t);
        }
    }
}
