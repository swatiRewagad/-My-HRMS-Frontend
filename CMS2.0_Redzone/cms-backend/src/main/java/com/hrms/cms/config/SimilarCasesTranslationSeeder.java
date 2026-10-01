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
 * Vocabulary for the shared similar-cases panel and the saved-remark template picker.
 *
 * <p>THE THREE FAILURE MESSAGES ARE THE LOAD-BEARING PART. The two screens this replaced ended their
 * error handlers with an empty list, so "No similar cases found" rendered for a 404, a 500, a timeout
 * and a genuine no-match alike — and one of the two endpoints did not exist in any controller, which
 * nobody noticed for months because the screen said the same calm sentence either way. The panel now
 * has three distinct states and this seeder carries three distinct messages:
 *
 * <ul>
 *   <li>{@code similar.none_found} — the search ran and the index matched nothing</li>
 *   <li>{@code similar.unavailable} — the server reported {@code provider:"none"}: no search backend</li>
 *   <li>{@code similar.error_failed} — the request did not complete; we know nothing about the index</li>
 * </ul>
 *
 * <p>Translators must keep those three apart. Rendering any two of them with the same wording in any
 * locale reintroduces precisely the defect this panel was built to remove, for the officers who read
 * that locale.
 *
 * <p>{@code similar.identifiers_only_note} is also not decoration: the API narrows Elasticsearch
 * {@code _source} to identifiers so one complaint's free text cannot surface on another's screen, and
 * the note tells the officer that the absence of a body is deliberate rather than a load failure.
 *
 * <p>A NEW seeder at {@code @Order(69)} rather than an edit to an existing one, per the convention
 * here: concurrent sessions add keys and a shared file is a guaranteed conflict, where a conflict
 * silently costs a locale. 1-22, 32-34, 38, 42-45 and 60-68 were verified taken before choosing 69.
 *
 * <p>Seeding is insert-if-absent by key code. Both the {@code similar.*} and {@code templates.*}
 * namespaces were verified unused before this was written.
 */
@Component
@Order(69)
public class SimilarCasesTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "similar";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public SimilarCasesTranslationSeeder(TranslationKeyRepository keyRepo,
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
        m.put("similar.panel_title", "Similar Cases");
        m.put("similar.close", "Close the similar cases panel");
        m.put("similar.identifiers_only_note",
            "Only complaint numbers, subjects and dates are shown. The text of another complaint is never disclosed here.");
        m.put("similar.searching", "Searching...");
        m.put("similar.none_found", "No similar cases found.");
        m.put("similar.none_found_hint", "The search ran and nothing matched this complaint.");
        m.put("similar.unavailable", "Similar-case search is unavailable.");
        m.put("similar.unavailable_hint", "No search service is configured, so no comparison could be made.");
        m.put("similar.error_failed", "The search did not complete.");
        m.put("similar.error_failed_hint", "This is not a result. Similar cases may well exist.");
        m.put("similar.retry", "Search again");

        m.put("templates.error_load_failed", "Saved remark templates could not be loaded.");
        m.put("templates.retry", "Try again");
        m.put("templates.loading", "Loading templates...");
        m.put("templates.none_available", "No saved remark template is available for this action.");
        m.put("templates.pick_label", "Use a saved remark template");
        m.put("templates.pick_placeholder", "Select a template");
        m.put("templates.insert_button", "Insert");
        m.put("templates.append_note", "Inserting adds the template below whatever you have already written.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "समान मामले");
        m.put("similar.close", "समान मामले पैनल बंद करें");
        m.put("similar.identifiers_only_note",
            "केवल शिकायत संख्या, विषय और तिथियाँ दिखाई जाती हैं। किसी अन्य शिकायत का पाठ यहाँ कभी प्रकट नहीं किया जाता।");
        m.put("similar.searching", "खोज जारी है...");
        m.put("similar.none_found", "कोई समान मामला नहीं मिला।");
        m.put("similar.none_found_hint", "खोज चली और इस शिकायत से कुछ भी मेल नहीं खाया।");
        m.put("similar.unavailable", "समान मामलों की खोज उपलब्ध नहीं है।");
        m.put("similar.unavailable_hint", "कोई खोज सेवा कॉन्फ़िगर नहीं है, इसलिए कोई तुलना नहीं हो सकी।");
        m.put("similar.error_failed", "खोज पूरी नहीं हो सकी।");
        m.put("similar.error_failed_hint", "यह कोई परिणाम नहीं है। समान मामले हो भी सकते हैं।");
        m.put("similar.retry", "फिर से खोजें");

        m.put("templates.error_load_failed", "सहेजे गए टिप्पणी प्रारूप लोड नहीं हो सके।");
        m.put("templates.retry", "फिर कोशिश करें");
        m.put("templates.loading", "प्रारूप लोड हो रहे हैं...");
        m.put("templates.none_available", "इस कार्रवाई के लिए कोई सहेजा गया टिप्पणी प्रारूप उपलब्ध नहीं है।");
        m.put("templates.pick_label", "सहेजा गया टिप्पणी प्रारूप उपयोग करें");
        m.put("templates.pick_placeholder", "एक प्रारूप चुनें");
        m.put("templates.insert_button", "सम्मिलित करें");
        m.put("templates.append_note", "सम्मिलित करने पर प्रारूप आपके पहले लिखे पाठ के नीचे जुड़ जाता है।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "समान प्रकरणे");
        m.put("similar.close", "समान प्रकरणे पटल बंद करा");
        m.put("similar.identifiers_only_note",
            "केवळ तक्रार क्रमांक, विषय आणि दिनांक दाखवले जातात. दुसऱ्या तक्रारीचा मजकूर येथे कधीही उघड केला जात नाही.");
        m.put("similar.searching", "शोध सुरू आहे...");
        m.put("similar.none_found", "समान प्रकरण आढळले नाही.");
        m.put("similar.none_found_hint", "शोध चालला आणि या तक्रारीशी काहीही जुळले नाही.");
        m.put("similar.unavailable", "समान प्रकरणांचा शोध उपलब्ध नाही.");
        m.put("similar.unavailable_hint", "कोणतीही शोध सेवा संरचित नाही, त्यामुळे तुलना होऊ शकली नाही.");
        m.put("similar.error_failed", "शोध पूर्ण होऊ शकला नाही.");
        m.put("similar.error_failed_hint", "हा निकाल नाही. समान प्रकरणे असू शकतात.");
        m.put("similar.retry", "पुन्हा शोधा");

        m.put("templates.error_load_failed", "जतन केलेले टिप्पणी नमुने लोड होऊ शकले नाहीत.");
        m.put("templates.retry", "पुन्हा प्रयत्न करा");
        m.put("templates.loading", "नमुने लोड होत आहेत...");
        m.put("templates.none_available", "या कृतीसाठी जतन केलेला टिप्पणी नमुना उपलब्ध नाही.");
        m.put("templates.pick_label", "जतन केलेला टिप्पणी नमुना वापरा");
        m.put("templates.pick_placeholder", "एक नमुना निवडा");
        m.put("templates.insert_button", "समाविष्ट करा");
        m.put("templates.append_note", "समाविष्ट केल्यावर नमुना आपण आधी लिहिलेल्या मजकुराखाली जोडला जातो.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "অনুরূপ মামলা");
        m.put("similar.close", "অনুরূপ মামলার প্যানেল বন্ধ করুন");
        m.put("similar.identifiers_only_note",
            "কেবল অভিযোগ নম্বর, বিষয় ও তারিখ দেখানো হয়। অন্য কোনও অভিযোগের লেখা এখানে কখনও প্রকাশ করা হয় না।");
        m.put("similar.searching", "অনুসন্ধান চলছে...");
        m.put("similar.none_found", "কোনও অনুরূপ মামলা পাওয়া যায়নি।");
        m.put("similar.none_found_hint", "অনুসন্ধান চলেছে এবং এই অভিযোগের সঙ্গে কিছুই মেলেনি।");
        m.put("similar.unavailable", "অনুরূপ মামলার অনুসন্ধান পাওয়া যাচ্ছে না।");
        m.put("similar.unavailable_hint", "কোনও অনুসন্ধান পরিষেবা কনফিগার করা নেই, তাই কোনও তুলনা করা যায়নি।");
        m.put("similar.error_failed", "অনুসন্ধান সম্পূর্ণ হয়নি।");
        m.put("similar.error_failed_hint", "এটি কোনও ফলাফল নয়। অনুরূপ মামলা থাকতেও পারে।");
        m.put("similar.retry", "আবার অনুসন্ধান করুন");

        m.put("templates.error_load_failed", "সংরক্ষিত মন্তব্যের নমুনা লোড করা যায়নি।");
        m.put("templates.retry", "আবার চেষ্টা করুন");
        m.put("templates.loading", "নমুনা লোড হচ্ছে...");
        m.put("templates.none_available", "এই কাজের জন্য কোনও সংরক্ষিত মন্তব্যের নমুনা নেই।");
        m.put("templates.pick_label", "সংরক্ষিত মন্তব্যের নমুনা ব্যবহার করুন");
        m.put("templates.pick_placeholder", "একটি নমুনা নির্বাচন করুন");
        m.put("templates.insert_button", "সন্নিবেশ করুন");
        m.put("templates.append_note", "সন্নিবেশ করলে নমুনাটি আপনার আগে লেখা অংশের নিচে যোগ হয়।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "సారూప్య కేసులు");
        m.put("similar.close", "సారూప్య కేసుల ప్యానెల్‌ను మూసివేయండి");
        m.put("similar.identifiers_only_note",
            "ఫిర్యాదు సంఖ్యలు, అంశాలు మరియు తేదీలు మాత్రమే చూపబడతాయి. మరొక ఫిర్యాదు పాఠ్యం ఇక్కడ ఎప్పుడూ బహిర్గతం కాదు.");
        m.put("similar.searching", "శోధన జరుగుతోంది...");
        m.put("similar.none_found", "సారూప్య కేసులు కనుగొనబడలేదు.");
        m.put("similar.none_found_hint", "శోధన జరిగింది మరియు ఈ ఫిర్యాదుతో ఏదీ సరిపోలలేదు.");
        m.put("similar.unavailable", "సారూప్య కేసుల శోధన అందుబాటులో లేదు.");
        m.put("similar.unavailable_hint", "శోధన సేవ కాన్ఫిగర్ చేయబడలేదు, కాబట్టి పోలిక చేయడం సాధ్యం కాలేదు.");
        m.put("similar.error_failed", "శోధన పూర్తి కాలేదు.");
        m.put("similar.error_failed_hint", "ఇది ఫలితం కాదు. సారూప్య కేసులు ఉండవచ్చు.");
        m.put("similar.retry", "మళ్లీ శోధించండి");

        m.put("templates.error_load_failed", "భద్రపరచిన వ్యాఖ్య మూసలను లోడ్ చేయడం సాధ్యం కాలేదు.");
        m.put("templates.retry", "మళ్లీ ప్రయత్నించండి");
        m.put("templates.loading", "మూసలు లోడ్ అవుతున్నాయి...");
        m.put("templates.none_available", "ఈ చర్యకు భద్రపరచిన వ్యాఖ్య మూస అందుబాటులో లేదు.");
        m.put("templates.pick_label", "భద్రపరచిన వ్యాఖ్య మూసను ఉపయోగించండి");
        m.put("templates.pick_placeholder", "ఒక మూసను ఎంచుకోండి");
        m.put("templates.insert_button", "చేర్చండి");
        m.put("templates.append_note", "చేర్చినప్పుడు మూస మీరు ఇప్పటికే వ్రాసిన వాటి క్రింద జోడించబడుతుంది.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "ஒத்த வழக்குகள்");
        m.put("similar.close", "ஒத்த வழக்குகள் பலகத்தை மூடு");
        m.put("similar.identifiers_only_note",
            "புகார் எண்கள், தலைப்புகள் மற்றும் தேதிகள் மட்டுமே காட்டப்படுகின்றன. வேறு ஒரு புகாரின் உரை இங்கு ஒருபோதும் வெளிப்படுத்தப்படுவதில்லை.");
        m.put("similar.searching", "தேடப்படுகிறது...");
        m.put("similar.none_found", "ஒத்த வழக்குகள் எதுவும் கிடைக்கவில்லை.");
        m.put("similar.none_found_hint", "தேடல் நடந்தது, இந்தப் புகாருடன் எதுவும் பொருந்தவில்லை.");
        m.put("similar.unavailable", "ஒத்த வழக்குகளைத் தேடும் வசதி கிடைக்கவில்லை.");
        m.put("similar.unavailable_hint", "தேடல் சேவை எதுவும் அமைக்கப்படவில்லை, எனவே ஒப்பிடல் நடைபெறவில்லை.");
        m.put("similar.error_failed", "தேடல் நிறைவடையவில்லை.");
        m.put("similar.error_failed_hint", "இது ஒரு முடிவு அல்ல. ஒத்த வழக்குகள் இருக்கவும் கூடும்.");
        m.put("similar.retry", "மீண்டும் தேடு");

        m.put("templates.error_load_failed", "சேமிக்கப்பட்ட கருத்து வடிவங்களை ஏற்ற முடியவில்லை.");
        m.put("templates.retry", "மீண்டும் முயலுங்கள்");
        m.put("templates.loading", "வடிவங்கள் ஏற்றப்படுகின்றன...");
        m.put("templates.none_available", "இந்தச் செயலுக்கு சேமிக்கப்பட்ட கருத்து வடிவம் எதுவும் இல்லை.");
        m.put("templates.pick_label", "சேமிக்கப்பட்ட கருத்து வடிவத்தைப் பயன்படுத்து");
        m.put("templates.pick_placeholder", "ஒரு வடிவத்தைத் தேர்ந்தெடுக்கவும்");
        m.put("templates.insert_button", "செருகு");
        m.put("templates.append_note", "செருகும்போது வடிவம் நீங்கள் ஏற்கெனவே எழுதியதற்குக் கீழே சேர்க்கப்படும்.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "સમાન કેસો");
        m.put("similar.close", "સમાન કેસોનું પેનલ બંધ કરો");
        m.put("similar.identifiers_only_note",
            "ફક્ત ફરિયાદ ક્રમાંક, વિષય અને તારીખો દર્શાવાય છે. અન્ય ફરિયાદનો લખાણ અહીં કદી જાહેર કરાતો નથી.");
        m.put("similar.searching", "શોધ ચાલુ છે...");
        m.put("similar.none_found", "કોઈ સમાન કેસ મળ્યો નથી.");
        m.put("similar.none_found_hint", "શોધ ચાલી અને આ ફરિયાદ સાથે કંઈ મેળ ખાધું નથી.");
        m.put("similar.unavailable", "સમાન કેસોની શોધ ઉપલબ્ધ નથી.");
        m.put("similar.unavailable_hint", "કોઈ શોધ સેવા ગોઠવાયેલ નથી, તેથી કોઈ સરખામણી થઈ શકી નથી.");
        m.put("similar.error_failed", "શોધ પૂર્ણ થઈ શકી નથી.");
        m.put("similar.error_failed_hint", "આ પરિણામ નથી. સમાન કેસો હોઈ પણ શકે.");
        m.put("similar.retry", "ફરીથી શોધો");

        m.put("templates.error_load_failed", "સાચવેલા ટિપ્પણી નમૂના લોડ થઈ શક્યા નથી.");
        m.put("templates.retry", "ફરી પ્રયાસ કરો");
        m.put("templates.loading", "નમૂના લોડ થઈ રહ્યા છે...");
        m.put("templates.none_available", "આ કાર્યવાહી માટે કોઈ સાચવેલો ટિપ્પણી નમૂનો ઉપલબ્ધ નથી.");
        m.put("templates.pick_label", "સાચવેલો ટિપ્પણી નમૂનો વાપરો");
        m.put("templates.pick_placeholder", "એક નમૂનો પસંદ કરો");
        m.put("templates.insert_button", "દાખલ કરો");
        m.put("templates.append_note", "દાખલ કરવાથી નમૂનો તમે પહેલાં લખેલા લખાણની નીચે ઉમેરાય છે.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "مماثل مقدمات");
        m.put("similar.close", "مماثل مقدمات کا پینل بند کریں");
        m.put("similar.identifiers_only_note",
            "صرف شکایت نمبر، موضوع اور تاریخیں دکھائی جاتی ہیں۔ کسی دوسری شکایت کا متن یہاں کبھی ظاہر نہیں کیا جاتا۔");
        m.put("similar.searching", "تلاش جاری ہے...");
        m.put("similar.none_found", "کوئی مماثل مقدمہ نہیں ملا۔");
        m.put("similar.none_found_hint", "تلاش چلی اور اس شکایت سے کچھ بھی میل نہیں کھایا۔");
        m.put("similar.unavailable", "مماثل مقدمات کی تلاش دستیاب نہیں ہے۔");
        m.put("similar.unavailable_hint", "کوئی تلاش سروس ترتیب نہیں دی گئی، اس لیے کوئی موازنہ نہیں ہو سکا۔");
        m.put("similar.error_failed", "تلاش مکمل نہیں ہو سکی۔");
        m.put("similar.error_failed_hint", "یہ کوئی نتیجہ نہیں ہے۔ مماثل مقدمات موجود بھی ہو سکتے ہیں۔");
        m.put("similar.retry", "دوبارہ تلاش کریں");

        m.put("templates.error_load_failed", "محفوظ شدہ تبصرہ سانچے لوڈ نہیں ہو سکے۔");
        m.put("templates.retry", "دوبارہ کوشش کریں");
        m.put("templates.loading", "سانچے لوڈ ہو رہے ہیں...");
        m.put("templates.none_available", "اس کارروائی کے لیے کوئی محفوظ شدہ تبصرہ سانچہ دستیاب نہیں۔");
        m.put("templates.pick_label", "محفوظ شدہ تبصرہ سانچہ استعمال کریں");
        m.put("templates.pick_placeholder", "ایک سانچہ منتخب کریں");
        m.put("templates.insert_button", "شامل کریں");
        m.put("templates.append_note", "شامل کرنے پر سانچہ آپ کے پہلے لکھے ہوئے متن کے نیچے جڑ جاتا ہے۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "ಹೋಲುವ ಪ್ರಕರಣಗಳು");
        m.put("similar.close", "ಹೋಲುವ ಪ್ರಕರಣಗಳ ಫಲಕವನ್ನು ಮುಚ್ಚಿ");
        m.put("similar.identifiers_only_note",
            "ದೂರು ಸಂಖ್ಯೆ, ವಿಷಯ ಮತ್ತು ದಿನಾಂಕಗಳನ್ನು ಮಾತ್ರ ತೋರಿಸಲಾಗುತ್ತದೆ. ಮತ್ತೊಂದು ದೂರಿನ ಪಠ್ಯವನ್ನು ಇಲ್ಲಿ ಎಂದಿಗೂ ಬಹಿರಂಗಪಡಿಸುವುದಿಲ್ಲ.");
        m.put("similar.searching", "ಹುಡುಕಲಾಗುತ್ತಿದೆ...");
        m.put("similar.none_found", "ಹೋಲುವ ಪ್ರಕರಣಗಳು ಕಂಡುಬಂದಿಲ್ಲ.");
        m.put("similar.none_found_hint", "ಹುಡುಕಾಟ ನಡೆಯಿತು ಮತ್ತು ಈ ದೂರಿಗೆ ಯಾವುದೂ ಹೊಂದಿಕೆಯಾಗಲಿಲ್ಲ.");
        m.put("similar.unavailable", "ಹೋಲುವ ಪ್ರಕರಣಗಳ ಹುಡುಕಾಟ ಲಭ್ಯವಿಲ್ಲ.");
        m.put("similar.unavailable_hint", "ಯಾವುದೇ ಹುಡುಕಾಟ ಸೇವೆ ಸಂರಚಿಸಲಾಗಿಲ್ಲ, ಆದ್ದರಿಂದ ಹೋಲಿಕೆ ಮಾಡಲಾಗಿಲ್ಲ.");
        m.put("similar.error_failed", "ಹುಡುಕಾಟ ಪೂರ್ಣಗೊಂಡಿಲ್ಲ.");
        m.put("similar.error_failed_hint", "ಇದು ಫಲಿತಾಂಶವಲ್ಲ. ಹೋಲುವ ಪ್ರಕರಣಗಳು ಇರಲೂಬಹುದು.");
        m.put("similar.retry", "ಮತ್ತೆ ಹುಡುಕಿ");

        m.put("templates.error_load_failed", "ಉಳಿಸಿದ ಕಾಮೆಂಟ್ ಟೆಂಪ್ಲೇಟ್‌ಗಳನ್ನು ಲೋಡ್ ಮಾಡಲಾಗಲಿಲ್ಲ.");
        m.put("templates.retry", "ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ");
        m.put("templates.loading", "ಟೆಂಪ್ಲೇಟ್‌ಗಳು ಲೋಡ್ ಆಗುತ್ತಿವೆ...");
        m.put("templates.none_available", "ಈ ಕ್ರಮಕ್ಕೆ ಉಳಿಸಿದ ಕಾಮೆಂಟ್ ಟೆಂಪ್ಲೇಟ್ ಲಭ್ಯವಿಲ್ಲ.");
        m.put("templates.pick_label", "ಉಳಿಸಿದ ಕಾಮೆಂಟ್ ಟೆಂಪ್ಲೇಟ್ ಬಳಸಿ");
        m.put("templates.pick_placeholder", "ಒಂದು ಟೆಂಪ್ಲೇಟ್ ಆಯ್ಕೆಮಾಡಿ");
        m.put("templates.insert_button", "ಸೇರಿಸಿ");
        m.put("templates.append_note", "ಸೇರಿಸಿದಾಗ ಟೆಂಪ್ಲೇಟ್ ನೀವು ಈಗಾಗಲೇ ಬರೆದಿರುವುದರ ಕೆಳಗೆ ಸೇರುತ್ತದೆ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "സമാന കേസുകൾ");
        m.put("similar.close", "സമാന കേസുകളുടെ പാനൽ അടയ്ക്കുക");
        m.put("similar.identifiers_only_note",
            "പരാതി നമ്പറുകൾ, വിഷയം, തീയതികൾ മാത്രമാണ് കാണിക്കുന്നത്. മറ്റൊരു പരാതിയുടെ വാചകം ഇവിടെ ഒരിക്കലും വെളിപ്പെടുത്തുന്നില്ല.");
        m.put("similar.searching", "തിരയുന്നു...");
        m.put("similar.none_found", "സമാന കേസുകൾ കണ്ടെത്തിയില്ല.");
        m.put("similar.none_found_hint", "തിരച്ചിൽ നടന്നു, ഈ പരാതിയുമായി ഒന്നും പൊരുത്തപ്പെട്ടില്ല.");
        m.put("similar.unavailable", "സമാന കേസുകളുടെ തിരച്ചിൽ ലഭ്യമല്ല.");
        m.put("similar.unavailable_hint", "തിരച്ചിൽ സേവനം ക്രമീകരിച്ചിട്ടില്ല, അതിനാൽ താരതമ്യം നടന്നില്ല.");
        m.put("similar.error_failed", "തിരച്ചിൽ പൂർത്തിയായില്ല.");
        m.put("similar.error_failed_hint", "ഇതൊരു ഫലമല്ല. സമാന കേസുകൾ ഉണ്ടാകാനും സാധ്യതയുണ്ട്.");
        m.put("similar.retry", "വീണ്ടും തിരയുക");

        m.put("templates.error_load_failed", "സംരക്ഷിച്ച അഭിപ്രായ മാതൃകകൾ ലോഡ് ചെയ്യാനായില്ല.");
        m.put("templates.retry", "വീണ്ടും ശ്രമിക്കുക");
        m.put("templates.loading", "മാതൃകകൾ ലോഡ് ചെയ്യുന്നു...");
        m.put("templates.none_available", "ഈ നടപടിക്ക് സംരക്ഷിച്ച അഭിപ്രായ മാതൃക ലഭ്യമല്ല.");
        m.put("templates.pick_label", "സംരക്ഷിച്ച അഭിപ്രായ മാതൃക ഉപയോഗിക്കുക");
        m.put("templates.pick_placeholder", "ഒരു മാതൃക തിരഞ്ഞെടുക്കുക");
        m.put("templates.insert_button", "ചേർക്കുക");
        m.put("templates.append_note", "ചേർക്കുമ്പോൾ മാതൃക നിങ്ങൾ ഇതിനകം എഴുതിയതിന് താഴെ കൂട്ടിച്ചേർക്കുന്നു.");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("similar.panel_title", "ਸਮਾਨ ਕੇਸ");
        m.put("similar.close", "ਸਮਾਨ ਕੇਸਾਂ ਦਾ ਪੈਨਲ ਬੰਦ ਕਰੋ");
        m.put("similar.identifiers_only_note",
            "ਸਿਰਫ਼ ਸ਼ਿਕਾਇਤ ਨੰਬਰ, ਵਿਸ਼ਾ ਅਤੇ ਤਾਰੀਖਾਂ ਦਿਖਾਈਆਂ ਜਾਂਦੀਆਂ ਹਨ। ਕਿਸੇ ਹੋਰ ਸ਼ਿਕਾਇਤ ਦਾ ਪਾਠ ਇੱਥੇ ਕਦੇ ਜ਼ਾਹਰ ਨਹੀਂ ਕੀਤਾ ਜਾਂਦਾ।");
        m.put("similar.searching", "ਖੋਜ ਜਾਰੀ ਹੈ...");
        m.put("similar.none_found", "ਕੋਈ ਸਮਾਨ ਕੇਸ ਨਹੀਂ ਮਿਲਿਆ।");
        m.put("similar.none_found_hint", "ਖੋਜ ਚੱਲੀ ਅਤੇ ਇਸ ਸ਼ਿਕਾਇਤ ਨਾਲ ਕੁਝ ਵੀ ਮੇਲ ਨਹੀਂ ਖਾਧਾ।");
        m.put("similar.unavailable", "ਸਮਾਨ ਕੇਸਾਂ ਦੀ ਖੋਜ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।");
        m.put("similar.unavailable_hint", "ਕੋਈ ਖੋਜ ਸੇਵਾ ਸੰਰਚਿਤ ਨਹੀਂ ਹੈ, ਇਸ ਲਈ ਕੋਈ ਤੁਲਨਾ ਨਹੀਂ ਹੋ ਸਕੀ।");
        m.put("similar.error_failed", "ਖੋਜ ਪੂਰੀ ਨਹੀਂ ਹੋ ਸਕੀ।");
        m.put("similar.error_failed_hint", "ਇਹ ਕੋਈ ਨਤੀਜਾ ਨਹੀਂ ਹੈ। ਸਮਾਨ ਕੇਸ ਹੋ ਵੀ ਸਕਦੇ ਹਨ।");
        m.put("similar.retry", "ਦੁਬਾਰਾ ਖੋਜੋ");

        m.put("templates.error_load_failed", "ਸੰਭਾਲੇ ਟਿੱਪਣੀ ਨਮੂਨੇ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕੇ।");
        m.put("templates.retry", "ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ");
        m.put("templates.loading", "ਨਮੂਨੇ ਲੋਡ ਹੋ ਰਹੇ ਹਨ...");
        m.put("templates.none_available", "ਇਸ ਕਾਰਵਾਈ ਲਈ ਕੋਈ ਸੰਭਾਲਿਆ ਟਿੱਪਣੀ ਨਮੂਨਾ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।");
        m.put("templates.pick_label", "ਸੰਭਾਲਿਆ ਟਿੱਪਣੀ ਨਮੂਨਾ ਵਰਤੋ");
        m.put("templates.pick_placeholder", "ਇੱਕ ਨਮੂਨਾ ਚੁਣੋ");
        m.put("templates.insert_button", "ਸ਼ਾਮਲ ਕਰੋ");
        m.put("templates.append_note", "ਸ਼ਾਮਲ ਕਰਨ 'ਤੇ ਨਮੂਨਾ ਤੁਹਾਡੇ ਪਹਿਲਾਂ ਲਿਖੇ ਪਾਠ ਦੇ ਹੇਠਾਂ ਜੁੜ ਜਾਂਦਾ ਹੈ।");
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
