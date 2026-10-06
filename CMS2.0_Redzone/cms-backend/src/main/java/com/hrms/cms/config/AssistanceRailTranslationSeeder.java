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
 * Vocabulary for the assistance rail (Brief 21).
 *
 * <h2>Why this class exists at all</h2>
 * {@code AssistanceRailResponse} and {@link com.hrms.cms.service.AssistanceRailService} both name this
 * seeder in their Javadoc, but it was never written. The rail's whole localisation design is "build the
 * line from {@code kind} plus {@code params}, never print the server's resolved English" — so with the
 * {@code assistance.*} namespace unseeded every lookup missed, and the panel fell back to server English
 * in all 13 locales while looking finished. That is the gap this closes.
 *
 * <h2>The signal keys keep the HYPHENATED kind, and that is not a style choice</h2>
 * {@code assistance-rail.component.ts} holds a hardcoded {@code KIND_LABEL_KEYS} map — deliberately
 * hardcoded, per its own comment, so that a seventh server-side kind cannot mint a plausible-looking
 * untranslated key — and every value in it is this namespace plus the kind spelled exactly as
 * {@link com.hrms.cms.service.AssistanceRailService}'s {@code KIND_*} constants spell it:
 * {@code assistance.signal.unsaved-draft}, {@code .last-section}, {@code .last-viewed},
 * {@code .complainant-history}, {@code .entity-clause-precedent}, {@code .category-closure-time}.
 *
 * <p>Normalising those hyphens to underscores here would miss every lookup the client makes, and the
 * miss is INVISIBLE: the component's {@code resolve()} treats a key echoed back by
 * {@code TranslationService.translate} as "localisation did not happen" and prints the server's English
 * instead. A whole-panel reversion to English is therefore indistinguishable from a panel that was never
 * translated — no error, no empty row, nothing in a log. The hyphens are load-bearing.
 *
 * <h2>Placeholder names are a wire contract with {@code Signal.params}</h2>
 * {@code translate} substitutes only the {@code {{name}}} placeholders it is handed and leaves the rest
 * in place, and the component then DISCARDS any resolved string still carrying a {@code {{...}}} in
 * favour of the server's English. So a placeholder typo here does not render a visible
 * {@code {{section}}} to an officer — it silently reverts that locale to English, which again looks like
 * "not translated yet". The names below are the {@code PARAM_*} constants on
 * {@link com.hrms.cms.service.AssistanceRailService}, checked against the maps the service builds:
 *
 * <ul>
 *   <li>{@code assistance.signal.unsaved-draft} — no placeholder. The preview is the officer's OWN
 *       text, rendered verbatim by the component and never translated.</li>
 *   <li>{@code assistance.signal.last-section} — {@code section}</li>
 *   <li>{@code assistance.signal.last-viewed} — {@code age}</li>
 *   <li>{@code assistance.signal.complainant-history} — {@code count}</li>
 *   <li>{@code assistance.signal.entity-clause-precedent} — {@code count}, {@code clause}</li>
 *   <li>{@code assistance.signal.category-closure-time} — {@code days} AND {@code sample}, in one
 *       sentence. The server spreads them over {@code title} ("closes in N days") and {@code detail}
 *       ("median of N closed complaints"), but the component does not: {@code KIND_LABEL_KEYS} is its
 *       only lookup table and holds exactly one key per {@code kind}, with no detail-key map of any
 *       kind. So the sample size goes in the heading or it never reaches an officer in any locale, and
 *       never is the worse answer — the sample size is what stops the median being read as more than
 *       it is, the very distinction {@code MIN_CLOSURE_SAMPLE} exists to protect. Safe to combine
 *       because the service emits both names in a single {@code Map.of}: there is no call path that
 *       supplies one without the other, so neither can be left unsubstituted.</li>
 *   <li>{@code assistance.signal.category-closure-detail} — {@code sample}. Seeded, but NOT looked up
 *       by anything today: the component has no detail-key map, which is exactly why the sample size
 *       is also folded into the heading above. Kept so that wiring a detail line later is a template
 *       change rather than a template change plus twelve translations. VERIFY against
 *       {@code KIND_LABEL_KEYS} before relying on it — a key the client never asks for cannot be
 *       assumed to be reaching anyone.</li>
 * </ul>
 *
 * <p>{@code clause} is NOT translated in any locale: {@code 15(1)(a)} is a statutory citation and must
 * read identically everywhere. {@code age} is a resolved ENGLISH phrase ("3 days ago") by the DTO's own
 * design, so {@code assistance.signal.last-viewed} localises its sentence but interpolates an English
 * fragment in every locale — a known limitation of the current server contract, not a seeding error.
 *
 * <h2>Count-neutral phrasing, on purpose</h2>
 * The service's English titles branch on singular/plural ("1 earlier complaint" vs "4 earlier
 * complaints"). One i18n value per key cannot, so the counted keys carry the plural form, which is the
 * agreed English contract and the string the client's fallback is compared against.
 *
 * <h2>Two states that must never read alike</h2>
 * Per the sibling {@link SimilarCasesTranslationSeeder}'s header: {@code assistance.nothing_to_report}
 * is the rail having RUN and having nothing to say — a normal state, and the common one, rendered with
 * {@code role=status}. {@code assistance.error_failed} is the request not completing at all, so nothing
 * is known either way, rendered with {@code role=alert}. Translators must keep them apart; wording them
 * identically in any locale reintroduces the exact defect the panel was built to remove, for the
 * officers who read that locale.
 *
 * <p>A NEW seeder at {@code @Order(71)} rather than an edit to an existing one, per the convention
 * here: concurrent sessions add keys and a shared file is a guaranteed conflict, where a conflict
 * silently costs a locale. 1-22, 32-34, 38, 42-45 and 60-70 were verified taken before choosing 71.
 *
 * <p>Seeding is insert-if-absent by key code, so re-running is safe. The {@code assistance.*} namespace
 * was verified unused before this was written — {@code layout.assistance_desc} and
 * {@code history.assistance_title} are different namespaces and are not touched.
 *
 * <h2>All THIRTEEN product locales, not eleven</h2>
 * {@code LanguageTranslationService} enumerates the locales this product serves, and {@code or} (Odia,
 * Oriya script {@code U+0B00–U+0B7F}) and {@code as} (Assamese, Bengali-Assamese script
 * {@code U+0980–U+09FF}) are two of them. This seeder originally covered only {@code en} plus ten, so
 * officers in those two locales were served the server's English — and because of the fallback
 * described above, served it SILENTLY: no missing-key error, no empty row, nothing in a log. "Eleven
 * of thirteen locales" and "thirteen of thirteen locales" are indistinguishable from the outside,
 * which is why {@code AssistanceRailTranslationCoverageTest} asserts the count against the locale list
 * rather than against a number written here.
 *
 * <p>Assamese is NOT Bengali with a different label. It is written in the same script but uses
 * {@code ৰ} for {@code র} and {@code ৱ} for the Bengali {@code ব}-wa, takes {@code ৰ} for the genitive,
 * and uses the {@code -ক} polite imperative ({@code কৰক}, not {@code করুন}). Copying {@code bengali()}
 * under the {@code as} key would therefore have shipped a locale that reads as foreign to an Assamese
 * officer while passing any coverage test that only counted keys — so the coverage test also asserts
 * the two maps are not identical.
 */
@Component
@Order(71)
public class AssistanceRailTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "assistance";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public AssistanceRailTranslationSeeder(TranslationKeyRepository keyRepo,
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
        seedLocale("or", odia());
        seedLocale("as", assamese());
    }

    private Map<String, String> english() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "Assistance");
        m.put("assistance.close", "Close the assistance panel");
        m.put("assistance.tier0_group", "Where you left off");
        m.put("assistance.tier1_group", "From similar complaints");
        m.put("assistance.nothing_to_report", "Nothing to flag on this complaint.");
        m.put("assistance.error_failed", "The assistance rail did not load.");
        m.put("assistance.error_failed_hint", "This is not a result. Signals may well exist.");
        m.put("assistance.retry", "Try again");
        m.put("assistance.dismiss", "Dismiss this kind of suggestion");
        m.put("assistance.announce_signals", "Assistance has {{count}} suggestions for this complaint.");
        m.put("assistance.all_dismissed", "You have dismissed every suggestion on this screen.");
        m.put("assistance.all_dismissed_hint", "They will not be offered again until you reload this screen.");
        m.put("assistance.bulb_label_count", "Assistance — {{count}} suggestions");

        m.put("assistance.signal.unsaved-draft", "You left unsaved text here");
        m.put("assistance.signal.last-section", "You were last in {{section}}");
        m.put("assistance.signal.last-viewed", "You last opened this {{age}}");
        m.put("assistance.signal.complainant-history",
            "This complainant has {{count}} earlier complaints");
        m.put("assistance.signal.entity-clause-precedent",
            "{{count}} earlier complaints against this entity cited {{clause}}");
        m.put("assistance.signal.category-closure-time",
            "This category closes in {{days}} days typically — median of {{sample}} closed complaints in this category");
        m.put("assistance.signal.category-closure-detail",
            "Median of {{sample}} closed complaints in this category");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "सहायता");
        m.put("assistance.close", "सहायता पैनल बंद करें");
        m.put("assistance.tier0_group", "आपने जहाँ छोड़ा था");
        m.put("assistance.tier1_group", "समान शिकायतों से");
        m.put("assistance.nothing_to_report", "इस शिकायत पर ध्यान देने योग्य कुछ नहीं है।");
        m.put("assistance.error_failed", "सहायता पैनल लोड नहीं हो सका।");
        m.put("assistance.error_failed_hint", "यह कोई परिणाम नहीं है। संकेत हो भी सकते हैं।");
        m.put("assistance.retry", "फिर कोशिश करें");
        m.put("assistance.dismiss", "इस प्रकार का सुझाव न दिखाएं");
        m.put("assistance.announce_signals", "इस शिकायत के लिए सहायता के पास {{count}} सुझाव हैं।");
        m.put("assistance.all_dismissed", "आपने इस स्क्रीन पर सभी सुझाव खारिज कर दिए हैं।");
        m.put("assistance.all_dismissed_hint", "जब तक आप यह स्क्रीन पुनः लोड नहीं करते, ये दोबारा नहीं दिखाए जाएंगे।");
        m.put("assistance.bulb_label_count", "सहायता — {{count}} सुझाव");

        m.put("assistance.signal.unsaved-draft", "आपने यहाँ बिना सहेजा पाठ छोड़ा था");
        m.put("assistance.signal.last-section", "आप अंतिम बार {{section}} में थे");
        m.put("assistance.signal.last-viewed", "आपने इसे अंतिम बार {{age}} खोला था");
        m.put("assistance.signal.complainant-history",
            "इस शिकायतकर्ता की {{count}} पहले की शिकायतें हैं");
        m.put("assistance.signal.entity-clause-precedent",
            "इस संस्था के विरुद्ध {{count}} पहले की शिकायतों में {{clause}} का हवाला दिया गया");
        m.put("assistance.signal.category-closure-time",
            "इस श्रेणी में सामान्यतः {{days}} दिनों में निपटान होता है — इस श्रेणी की {{sample}} बंद शिकायतों की माध्यिका");
        m.put("assistance.signal.category-closure-detail",
            "इस श्रेणी की {{sample}} बंद शिकायतों की माध्यिका");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "सहाय्य");
        m.put("assistance.close", "सहाय्य पटल बंद करा");
        m.put("assistance.tier0_group", "तुम्ही जेथे थांबले होते");
        m.put("assistance.tier1_group", "समान तक्रारींवरून");
        m.put("assistance.nothing_to_report", "या तक्रारीवर नोंदवण्यासारखे काही नाही.");
        m.put("assistance.error_failed", "सहाय्य पटल लोड होऊ शकले नाही.");
        m.put("assistance.error_failed_hint", "हा निकाल नाही. संकेत असूही शकतात.");
        m.put("assistance.retry", "पुन्हा प्रयत्न करा");
        m.put("assistance.dismiss", "या प्रकारची सूचना दाखवू नका");
        m.put("assistance.announce_signals", "या तक्रारीसाठी सहाय्याकडे {{count}} सूचना आहेत.");
        m.put("assistance.all_dismissed", "तुम्ही या स्क्रीनवरील सर्व सूचना नाकारल्या आहेत.");
        m.put("assistance.all_dismissed_hint", "तुम्ही ही स्क्रीन पुन्हा लोड करेपर्यंत त्या पुन्हा दाखवल्या जाणार नाहीत.");
        m.put("assistance.bulb_label_count", "सहाय्य — {{count}} सूचना");

        m.put("assistance.signal.unsaved-draft", "तुम्ही येथे जतन न केलेला मजकूर सोडला होता");
        m.put("assistance.signal.last-section", "तुम्ही शेवटी {{section}} मध्ये होते");
        m.put("assistance.signal.last-viewed", "तुम्ही हे शेवटी {{age}} उघडले होते");
        m.put("assistance.signal.complainant-history",
            "या तक्रारदाराच्या {{count}} पूर्वीच्या तक्रारी आहेत");
        m.put("assistance.signal.entity-clause-precedent",
            "या संस्थेविरुद्धच्या {{count}} पूर्वीच्या तक्रारींमध्ये {{clause}} नमूद केले होते");
        m.put("assistance.signal.category-closure-time",
            "या प्रवर्गात सामान्यतः {{days}} दिवसांत निपटारा होतो — या प्रवर्गातील {{sample}} बंद तक्रारींची मध्यिका");
        m.put("assistance.signal.category-closure-detail",
            "या प्रवर्गातील {{sample}} बंद तक्रारींची मध्यिका");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "সহায়তা");
        m.put("assistance.close", "সহায়তা প্যানেল বন্ধ করুন");
        m.put("assistance.tier0_group", "আপনি যেখানে থেমেছিলেন");
        m.put("assistance.tier1_group", "অনুরূপ অভিযোগ থেকে");
        m.put("assistance.nothing_to_report", "এই অভিযোগে উল্লেখ করার কিছু নেই।");
        m.put("assistance.error_failed", "সহায়তা প্যানেল লোড হয়নি।");
        m.put("assistance.error_failed_hint", "এটি কোনও ফলাফল নয়। সংকেত থাকতেও পারে।");
        m.put("assistance.retry", "আবার চেষ্টা করুন");
        m.put("assistance.dismiss", "এই ধরনের পরামর্শ দেখাবেন না");
        m.put("assistance.announce_signals", "এই অভিযোগের জন্য সহায়তার কাছে {{count}}টি পরামর্শ রয়েছে।");
        m.put("assistance.all_dismissed", "আপনি এই স্ক্রিনের সব পরামর্শ খারিজ করেছেন।");
        m.put("assistance.all_dismissed_hint", "আপনি এই স্ক্রিন পুনরায় লোড না করা পর্যন্ত সেগুলি আর দেখানো হবে না।");
        m.put("assistance.bulb_label_count", "সহায়তা — {{count}} পরামর্শ");

        m.put("assistance.signal.unsaved-draft", "আপনি এখানে অসংরক্ষিত লেখা রেখে গিয়েছিলেন");
        m.put("assistance.signal.last-section", "আপনি সর্বশেষ {{section}}-এ ছিলেন");
        m.put("assistance.signal.last-viewed", "আপনি এটি সর্বশেষ {{age}} খুলেছিলেন");
        m.put("assistance.signal.complainant-history",
            "এই অভিযোগকারীর {{count}}টি আগের অভিযোগ রয়েছে");
        m.put("assistance.signal.entity-clause-precedent",
            "এই সংস্থার বিরুদ্ধে {{count}}টি আগের অভিযোগে {{clause}} উল্লেখ করা হয়েছে");
        m.put("assistance.signal.category-closure-time",
            "এই শ্রেণিতে সাধারণত {{days}} দিনে নিষ্পত্তি হয় — এই শ্রেণির {{sample}}টি বন্ধ অভিযোগের মধ্যমা");
        m.put("assistance.signal.category-closure-detail",
            "এই শ্রেণির {{sample}}টি বন্ধ অভিযোগের মধ্যমা");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "సహాయం");
        m.put("assistance.close", "సహాయ ప్యానెల్‌ను మూసివేయండి");
        m.put("assistance.tier0_group", "మీరు ఆపిన చోటు");
        m.put("assistance.tier1_group", "సారూప్య ఫిర్యాదుల నుండి");
        m.put("assistance.nothing_to_report", "ఈ ఫిర్యాదుపై గుర్తించదగినది ఏదీ లేదు.");
        m.put("assistance.error_failed", "సహాయ ప్యానెల్ లోడ్ కాలేదు.");
        m.put("assistance.error_failed_hint", "ఇది ఫలితం కాదు. సంకేతాలు ఉండవచ్చు.");
        m.put("assistance.retry", "మళ్లీ ప్రయత్నించండి");
        m.put("assistance.dismiss", "ఈ రకమైన సూచనను చూపవద్దు");
        m.put("assistance.announce_signals", "ఈ ఫిర్యాదు కోసం సహాయం వద్ద {{count}} సూచనలు ఉన్నాయి.");
        m.put("assistance.all_dismissed", "ఈ స్క్రీన్‌లోని అన్ని సూచనలను మీరు తిరస్కరించారు.");
        m.put("assistance.all_dismissed_hint", "మీరు ఈ స్క్రీన్‌ను మళ్లీ లోడ్ చేసే వరకు అవి తిరిగి చూపబడవు.");
        m.put("assistance.bulb_label_count", "సహాయం — {{count}} సూచనలు");

        m.put("assistance.signal.unsaved-draft", "మీరు ఇక్కడ భద్రపరచని పాఠ్యాన్ని వదిలారు");
        m.put("assistance.signal.last-section", "మీరు చివరిగా {{section}}లో ఉన్నారు");
        m.put("assistance.signal.last-viewed", "మీరు దీన్ని చివరిగా {{age}} తెరిచారు");
        m.put("assistance.signal.complainant-history",
            "ఈ ఫిర్యాదుదారునికి {{count}} మునుపటి ఫిర్యాదులు ఉన్నాయి");
        m.put("assistance.signal.entity-clause-precedent",
            "ఈ సంస్థపై ఉన్న {{count}} మునుపటి ఫిర్యాదులలో {{clause}} ఉదహరించబడింది");
        m.put("assistance.signal.category-closure-time",
            "ఈ విభాగంలో సాధారణంగా {{days}} రోజులలో ముగుస్తుంది — ఈ విభాగంలో ముగించిన {{sample}} ఫిర్యాదుల మధ్యగతం");
        m.put("assistance.signal.category-closure-detail",
            "ఈ విభాగంలో ముగించిన {{sample}} ఫిర్యాదుల మధ్యగతం");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "உதவி");
        m.put("assistance.close", "உதவி பலகத்தை மூடு");
        m.put("assistance.tier0_group", "நீங்கள் நின்ற இடம்");
        m.put("assistance.tier1_group", "ஒத்த புகார்களிலிருந்து");
        m.put("assistance.nothing_to_report", "இந்தப் புகாரில் குறிப்பிடத்தக்கது ஒன்றும் இல்லை.");
        m.put("assistance.error_failed", "உதவி பலகம் ஏற்றப்படவில்லை.");
        m.put("assistance.error_failed_hint", "இது ஒரு முடிவு அல்ல. குறிப்புகள் இருக்கவும் கூடும்.");
        m.put("assistance.retry", "மீண்டும் முயலுங்கள்");
        m.put("assistance.dismiss", "இந்த வகையான பரிந்துரையைக் காட்ட வேண்டாம்");
        m.put("assistance.announce_signals", "இந்த புகாருக்கு உதவியில் {{count}} பரிந்துரைகள் உள்ளன.");
        m.put("assistance.all_dismissed", "இந்தத் திரையில் உள்ள அனைத்துப் பரிந்துரைகளையும் நிராகரித்துவிட்டீர்கள்.");
        m.put("assistance.all_dismissed_hint", "இந்தத் திரையை மீண்டும் ஏற்றும் வரை அவை மீண்டும் காட்டப்படாது.");
        m.put("assistance.bulb_label_count", "உதவி — {{count}} பரிந்துரைகள்");

        m.put("assistance.signal.unsaved-draft", "நீங்கள் இங்கு சேமிக்காத உரையை விட்டுச் சென்றீர்கள்");
        m.put("assistance.signal.last-section", "நீங்கள் கடைசியாக {{section}} பகுதியில் இருந்தீர்கள்");
        m.put("assistance.signal.last-viewed", "நீங்கள் இதை கடைசியாக {{age}} திறந்தீர்கள்");
        m.put("assistance.signal.complainant-history",
            "இந்தப் புகார்தாரருக்கு {{count}} முந்தைய புகார்கள் உள்ளன");
        m.put("assistance.signal.entity-clause-precedent",
            "இந்த நிறுவனத்திற்கு எதிரான {{count}} முந்தைய புகார்களில் {{clause}} குறிப்பிடப்பட்டது");
        m.put("assistance.signal.category-closure-time",
            "இந்த வகையில் பொதுவாக {{days}} நாட்களில் முடிக்கப்படுகிறது — இந்த வகையில் முடிக்கப்பட்ட {{sample}} புகார்களின் இடைநிலை");
        m.put("assistance.signal.category-closure-detail",
            "இந்த வகையில் முடிக்கப்பட்ட {{sample}} புகார்களின் இடைநிலை");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "સહાય");
        m.put("assistance.close", "સહાય પેનલ બંધ કરો");
        m.put("assistance.tier0_group", "તમે જ્યાં છોડ્યું હતું");
        m.put("assistance.tier1_group", "સમાન ફરિયાદો પરથી");
        m.put("assistance.nothing_to_report", "આ ફરિયાદ પર નોંધવા જેવું કંઈ નથી.");
        m.put("assistance.error_failed", "સહાય પેનલ લોડ થઈ શક્યું નથી.");
        m.put("assistance.error_failed_hint", "આ પરિણામ નથી. સંકેતો હોઈ પણ શકે.");
        m.put("assistance.retry", "ફરી પ્રયાસ કરો");
        m.put("assistance.dismiss", "આ પ્રકારનું સૂચન ન દર્શાવો");
        m.put("assistance.announce_signals", "આ ફરિયાદ માટે સહાય પાસે {{count}} સૂચનો છે.");
        m.put("assistance.all_dismissed", "તમે આ સ્ક્રીન પરનાં બધાં સૂચનો નકારી દીધાં છે.");
        m.put("assistance.all_dismissed_hint", "તમે આ સ્ક્રીન ફરીથી લોડ કરો ત્યાં સુધી તે ફરી દર્શાવાશે નહીં.");
        m.put("assistance.bulb_label_count", "સહાય — {{count}} સૂચનો");

        m.put("assistance.signal.unsaved-draft", "તમે અહીં સાચવ્યા વિનાનું લખાણ છોડ્યું હતું");
        m.put("assistance.signal.last-section", "તમે છેલ્લે {{section}} માં હતા");
        m.put("assistance.signal.last-viewed", "તમે આ છેલ્લે {{age}} ખોલ્યું હતું");
        m.put("assistance.signal.complainant-history",
            "આ ફરિયાદીની {{count}} અગાઉની ફરિયાદો છે");
        m.put("assistance.signal.entity-clause-precedent",
            "આ સંસ્થા વિરુદ્ધની {{count}} અગાઉની ફરિયાદોમાં {{clause}} ટાંકવામાં આવ્યું હતું");
        m.put("assistance.signal.category-closure-time",
            "આ શ્રેણીમાં સામાન્ય રીતે {{days}} દિવસમાં નિકાલ થાય છે — આ શ્રેણીની {{sample}} બંધ ફરિયાદોનો મધ્યક");
        m.put("assistance.signal.category-closure-detail",
            "આ શ્રેણીની {{sample}} બંધ ફરિયાદોનો મધ્યક");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "معاونت");
        m.put("assistance.close", "معاونت پینل بند کریں");
        m.put("assistance.tier0_group", "جہاں آپ نے چھوڑا تھا");
        m.put("assistance.tier1_group", "مماثل شکایات سے");
        m.put("assistance.nothing_to_report", "اس شکایت پر نشان زد کرنے کے لیے کچھ نہیں ہے۔");
        m.put("assistance.error_failed", "معاونت پینل لوڈ نہیں ہو سکا۔");
        m.put("assistance.error_failed_hint", "یہ کوئی نتیجہ نہیں ہے۔ اشارے موجود بھی ہو سکتے ہیں۔");
        m.put("assistance.retry", "دوبارہ کوشش کریں");
        m.put("assistance.dismiss", "اس قسم کی تجویز نہ دکھائیں");
        m.put("assistance.announce_signals", "اس شکایت کے لیے معاونت کے پاس {{count}} تجاویز ہیں۔");
        m.put("assistance.all_dismissed", "آپ نے اس اسکرین کی تمام تجاویز مسترد کر دی ہیں۔");
        m.put("assistance.all_dismissed_hint", "جب تک آپ یہ اسکرین دوبارہ لوڈ نہیں کرتے، وہ دوبارہ نہیں دکھائی جائیں گی۔");
        m.put("assistance.bulb_label_count", "مدد — {{count}} تجاویز");

        m.put("assistance.signal.unsaved-draft", "آپ نے یہاں غیر محفوظ متن چھوڑا تھا");
        m.put("assistance.signal.last-section", "آپ آخری بار {{section}} میں تھے");
        m.put("assistance.signal.last-viewed", "آپ نے اسے آخری بار {{age}} کھولا تھا");
        m.put("assistance.signal.complainant-history",
            "اس شکایت کنندہ کی {{count}} پہلی شکایات ہیں");
        m.put("assistance.signal.entity-clause-precedent",
            "اس ادارے کے خلاف {{count}} پہلی شکایات میں {{clause}} کا حوالہ دیا گیا");
        m.put("assistance.signal.category-closure-time",
            "اس زمرے میں عموماً {{days}} دنوں میں نمٹایا جاتا ہے — اس زمرے کی {{sample}} بند شکایات کا وسطانیہ");
        m.put("assistance.signal.category-closure-detail",
            "اس زمرے کی {{sample}} بند شکایات کا وسطانیہ");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "ಸಹಾಯ");
        m.put("assistance.close", "ಸಹಾಯ ಫಲಕವನ್ನು ಮುಚ್ಚಿ");
        m.put("assistance.tier0_group", "ನೀವು ನಿಲ್ಲಿಸಿದ ಸ್ಥಳ");
        m.put("assistance.tier1_group", "ಹೋಲುವ ದೂರುಗಳಿಂದ");
        m.put("assistance.nothing_to_report", "ಈ ದೂರಿನಲ್ಲಿ ಗಮನಿಸಬೇಕಾದ ಏನೂ ಇಲ್ಲ.");
        m.put("assistance.error_failed", "ಸಹಾಯ ಫಲಕ ಲೋಡ್ ಆಗಲಿಲ್ಲ.");
        m.put("assistance.error_failed_hint", "ಇದು ಫಲಿತಾಂಶವಲ್ಲ. ಸೂಚನೆಗಳು ಇರಲೂಬಹುದು.");
        m.put("assistance.retry", "ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ");
        m.put("assistance.dismiss", "ಈ ರೀತಿಯ ಸಲಹೆಯನ್ನು ತೋರಿಸಬೇಡಿ");
        m.put("assistance.announce_signals", "ಈ ದೂರಿಗಾಗಿ ಸಹಾಯದ ಬಳಿ {{count}} ಸಲಹೆಗಳಿವೆ.");
        m.put("assistance.all_dismissed", "ಈ ಪರದೆಯ ಎಲ್ಲಾ ಸಲಹೆಗಳನ್ನು ನೀವು ತಿರಸ್ಕರಿಸಿದ್ದೀರಿ.");
        m.put("assistance.all_dismissed_hint", "ನೀವು ಈ ಪರದೆಯನ್ನು ಮತ್ತೆ ಲೋಡ್ ಮಾಡುವವರೆಗೆ ಅವು ಮತ್ತೆ ತೋರಿಸಲಾಗುವುದಿಲ್ಲ.");
        m.put("assistance.bulb_label_count", "ಸಹಾಯ — {{count}} ಸಲಹೆಗಳು");

        m.put("assistance.signal.unsaved-draft", "ನೀವು ಇಲ್ಲಿ ಉಳಿಸದ ಪಠ್ಯವನ್ನು ಬಿಟ್ಟಿದ್ದೀರಿ");
        m.put("assistance.signal.last-section", "ನೀವು ಕೊನೆಯದಾಗಿ {{section}} ನಲ್ಲಿದ್ದೀರಿ");
        m.put("assistance.signal.last-viewed", "ನೀವು ಇದನ್ನು ಕೊನೆಯದಾಗಿ {{age}} ತೆರೆದಿದ್ದೀರಿ");
        m.put("assistance.signal.complainant-history",
            "ಈ ದೂರುದಾರರಿಗೆ {{count}} ಹಿಂದಿನ ದೂರುಗಳಿವೆ");
        m.put("assistance.signal.entity-clause-precedent",
            "ಈ ಸಂಸ್ಥೆಯ ವಿರುದ್ಧದ {{count}} ಹಿಂದಿನ ದೂರುಗಳಲ್ಲಿ {{clause}} ಉಲ್ಲೇಖಿಸಲಾಗಿದೆ");
        m.put("assistance.signal.category-closure-time",
            "ಈ ವರ್ಗದಲ್ಲಿ ಸಾಮಾನ್ಯವಾಗಿ {{days}} ದಿನಗಳಲ್ಲಿ ಮುಕ್ತಾಯವಾಗುತ್ತದೆ — ಈ ವರ್ಗದಲ್ಲಿ ಮುಚ್ಚಿದ {{sample}} ದೂರುಗಳ ಮಧ್ಯವರ್ತಿ ಮೌಲ್ಯ");
        m.put("assistance.signal.category-closure-detail",
            "ಈ ವರ್ಗದಲ್ಲಿ ಮುಚ್ಚಿದ {{sample}} ದೂರುಗಳ ಮಧ್ಯವರ್ತಿ ಮೌಲ್ಯ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "സഹായം");
        m.put("assistance.close", "സഹായ പാനൽ അടയ്ക്കുക");
        m.put("assistance.tier0_group", "നിങ്ങൾ നിർത്തിയ സ്ഥാനം");
        m.put("assistance.tier1_group", "സമാന പരാതികളിൽ നിന്ന്");
        m.put("assistance.nothing_to_report", "ഈ പരാതിയിൽ ശ്രദ്ധിക്കേണ്ടതൊന്നുമില്ല.");
        m.put("assistance.error_failed", "സഹായ പാനൽ ലോഡ് ചെയ്തില്ല.");
        m.put("assistance.error_failed_hint", "ഇതൊരു ഫലമല്ല. സൂചനകൾ ഉണ്ടാകാനും സാധ്യതയുണ്ട്.");
        m.put("assistance.retry", "വീണ്ടും ശ്രമിക്കുക");
        m.put("assistance.dismiss", "ഇത്തരം നിർദ്ദേശം കാണിക്കേണ്ടതില്ല");
        m.put("assistance.announce_signals", "ഈ പരാതിക്കായി സഹായത്തിന് {{count}} നിർദ്ദേശങ്ങളുണ്ട്.");
        m.put("assistance.all_dismissed", "ഈ സ്ക്രീനിലെ എല്ലാ നിർദ്ദേശങ്ങളും നിങ്ങൾ നിരസിച്ചു.");
        m.put("assistance.all_dismissed_hint", "ഈ സ്ക്രീൻ വീണ്ടും ലോഡ് ചെയ്യുന്നതുവരെ അവ വീണ്ടും കാണിക്കില്ല.");
        m.put("assistance.bulb_label_count", "സഹായം — {{count}} നിർദ്ദേശങ്ങൾ");

        m.put("assistance.signal.unsaved-draft", "നിങ്ങൾ ഇവിടെ സംരക്ഷിക്കാത്ത വാചകം വിട്ടിട്ടുണ്ട്");
        m.put("assistance.signal.last-section", "നിങ്ങൾ അവസാനമായി {{section}} വിഭാഗത്തിലായിരുന്നു");
        m.put("assistance.signal.last-viewed", "നിങ്ങൾ ഇത് അവസാനമായി {{age}} തുറന്നു");
        m.put("assistance.signal.complainant-history",
            "ഈ പരാതിക്കാരന് {{count}} മുൻ പരാതികളുണ്ട്");
        m.put("assistance.signal.entity-clause-precedent",
            "ഈ സ്ഥാപനത്തിനെതിരായ {{count}} മുൻ പരാതികളിൽ {{clause}} ഉദ്ധരിച്ചിട്ടുണ്ട്");
        m.put("assistance.signal.category-closure-time",
            "ഈ വിഭാഗത്തിൽ സാധാരണയായി {{days}} ദിവസത്തിനുള്ളിൽ തീർപ്പാകുന്നു — ഈ വിഭാഗത്തിൽ തീർപ്പാക്കിയ {{sample}} പരാതികളുടെ മധ്യമം");
        m.put("assistance.signal.category-closure-detail",
            "ഈ വിഭാഗത്തിൽ തീർപ്പാക്കിയ {{sample}} പരാതികളുടെ മധ്യമം");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "ਸਹਾਇਤਾ");
        m.put("assistance.close", "ਸਹਾਇਤਾ ਪੈਨਲ ਬੰਦ ਕਰੋ");
        m.put("assistance.tier0_group", "ਜਿੱਥੇ ਤੁਸੀਂ ਛੱਡਿਆ ਸੀ");
        m.put("assistance.tier1_group", "ਸਮਾਨ ਸ਼ਿਕਾਇਤਾਂ ਤੋਂ");
        m.put("assistance.nothing_to_report", "ਇਸ ਸ਼ਿਕਾਇਤ ਉੱਤੇ ਦਰਸਾਉਣ ਲਈ ਕੁਝ ਨਹੀਂ ਹੈ।");
        m.put("assistance.error_failed", "ਸਹਾਇਤਾ ਪੈਨਲ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ।");
        m.put("assistance.error_failed_hint", "ਇਹ ਕੋਈ ਨਤੀਜਾ ਨਹੀਂ ਹੈ। ਸੰਕੇਤ ਹੋ ਵੀ ਸਕਦੇ ਹਨ।");
        m.put("assistance.retry", "ਦੁਬਾਰਾ ਕੋਸ਼ਿਸ਼ ਕਰੋ");
        m.put("assistance.dismiss", "ਇਸ ਕਿਸਮ ਦਾ ਸੁਝਾਅ ਨਾ ਦਿਖਾਓ");
        m.put("assistance.announce_signals", "ਇਸ ਸ਼ਿਕਾਇਤ ਲਈ ਸਹਾਇਤਾ ਕੋਲ {{count}} ਸੁਝਾਅ ਹਨ।");
        m.put("assistance.all_dismissed", "ਤੁਸੀਂ ਇਸ ਸਕਰੀਨ ਦੇ ਸਾਰੇ ਸੁਝਾਅ ਰੱਦ ਕਰ ਦਿੱਤੇ ਹਨ।");
        m.put("assistance.all_dismissed_hint", "ਜਦੋਂ ਤੱਕ ਤੁਸੀਂ ਇਹ ਸਕਰੀਨ ਦੁਬਾਰਾ ਲੋਡ ਨਹੀਂ ਕਰਦੇ, ਉਹ ਦੁਬਾਰਾ ਨਹੀਂ ਦਿਖਾਏ ਜਾਣਗੇ।");
        m.put("assistance.bulb_label_count", "ਸਹਾਇਤਾ — {{count}} ਸੁਝਾਅ");

        m.put("assistance.signal.unsaved-draft", "ਤੁਸੀਂ ਇੱਥੇ ਨਾ ਸੰਭਾਲਿਆ ਪਾਠ ਛੱਡਿਆ ਸੀ");
        m.put("assistance.signal.last-section", "ਤੁਸੀਂ ਆਖਰੀ ਵਾਰ {{section}} ਵਿੱਚ ਸੀ");
        m.put("assistance.signal.last-viewed", "ਤੁਸੀਂ ਇਸਨੂੰ ਆਖਰੀ ਵਾਰ {{age}} ਖੋਲ੍ਹਿਆ ਸੀ");
        m.put("assistance.signal.complainant-history",
            "ਇਸ ਸ਼ਿਕਾਇਤਕਰਤਾ ਦੀਆਂ {{count}} ਪਹਿਲੀਆਂ ਸ਼ਿਕਾਇਤਾਂ ਹਨ");
        m.put("assistance.signal.entity-clause-precedent",
            "ਇਸ ਸੰਸਥਾ ਵਿਰੁੱਧ {{count}} ਪਹਿਲੀਆਂ ਸ਼ਿਕਾਇਤਾਂ ਵਿੱਚ {{clause}} ਦਾ ਹਵਾਲਾ ਦਿੱਤਾ ਗਿਆ");
        m.put("assistance.signal.category-closure-time",
            "ਇਸ ਸ਼੍ਰੇਣੀ ਵਿੱਚ ਆਮ ਤੌਰ 'ਤੇ {{days}} ਦਿਨਾਂ ਵਿੱਚ ਨਿਪਟਾਰਾ ਹੁੰਦਾ ਹੈ — ਇਸ ਸ਼੍ਰੇਣੀ ਦੀਆਂ {{sample}} ਬੰਦ ਸ਼ਿਕਾਇਤਾਂ ਦਾ ਮੱਧਮਾਨ");
        m.put("assistance.signal.category-closure-detail",
            "ਇਸ ਸ਼੍ਰੇਣੀ ਦੀਆਂ {{sample}} ਬੰਦ ਸ਼ਿਕਾਇਤਾਂ ਦਾ ਮੱਧਮਾਨ");
        return m;
    }

    /**
     * Odia ({@code or}) — Oriya script, {@code U+0B00–U+0B7F}.
     *
     * <p>Verb forms are the honorific/formal register throughout ({@code ଛାଡ଼ିଥିଲେ}, {@code କରନ୍ତୁ}),
     * matching the other locales here: these lines address an officer, not a customer.
     *
     * <p>{@code ମଧ୍ୟମା} is the Odia statistical term for the median, not a paraphrase of "average" —
     * the distinction matters because the whole point of {@code MIN_CLOSURE_SAMPLE} is to stop a
     * middling number being read as more than it is. Flagged in the delivery notes for a human
     * translator's confirmation, as it is the one term here a general speaker may not use daily.
     */
    private Map<String, String> odia() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "ସହାୟତା");
        m.put("assistance.close", "ସହାୟତା ପ୍ୟାନେଲ ବନ୍ଦ କରନ୍ତୁ");
        m.put("assistance.tier0_group", "ଆପଣ ଯେଉଁଠି ଛାଡ଼ିଥିଲେ");
        m.put("assistance.tier1_group", "ସମାନ ଅଭିଯୋଗରୁ");
        m.put("assistance.nothing_to_report", "ଏହି ଅଭିଯୋଗରେ ଚିହ୍ନଟ କରିବା ଯୋଗ୍ୟ କିଛି ନାହିଁ।");
        m.put("assistance.error_failed", "ସହାୟତା ପ୍ୟାନେଲ ଲୋଡ୍ ହୋଇପାରିଲା ନାହିଁ।");
        m.put("assistance.error_failed_hint", "ଏହା କୌଣସି ଫଳାଫଳ ନୁହେଁ। ସଙ୍କେତ ଥାଇପାରେ।");
        m.put("assistance.retry", "ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ");
        m.put("assistance.dismiss", "ଏହି ପ୍ରକାରର ପରାମର୍ଶ ଦେଖାନ୍ତୁ ନାହିଁ");
        m.put("assistance.announce_signals", "ଏହି ଅଭିଯୋଗ ପାଇଁ ସହାୟତା ପାଖରେ {{count}}ଟି ପରାମର୍ଶ ଅଛି।");
        m.put("assistance.all_dismissed", "ଆପଣ ଏହି ସ୍କ୍ରିନର ସମସ୍ତ ପରାମର୍ଶ ଖାରଜ କରିଛନ୍ତି।");
        m.put("assistance.all_dismissed_hint", "ଆପଣ ଏହି ସ୍କ୍ରିନ ପୁଣି ଲୋଡ୍ ନକରିବା ପର୍ଯ୍ୟନ୍ତ ସେଗୁଡ଼ିକ ପୁଣି ଦେଖାଯିବ ନାହିଁ।");
        m.put("assistance.bulb_label_count", "ସହାୟତା — {{count}} ପରାମର୍ଶ");

        m.put("assistance.signal.unsaved-draft", "ଆପଣ ଏଠାରେ ଅସଂରକ୍ଷିତ ଲେଖା ଛାଡ଼ିଥିଲେ");
        m.put("assistance.signal.last-section", "ଆପଣ ଶେଷରେ {{section}}ରେ ଥିଲେ");
        m.put("assistance.signal.last-viewed", "ଆପଣ ଏହାକୁ ଶେଷରେ {{age}} ଖୋଲିଥିଲେ");
        m.put("assistance.signal.complainant-history",
            "ଏହି ଅଭିଯୋଗକାରୀଙ୍କର {{count}}ଟି ପୂର୍ବ ଅଭିଯୋଗ ଅଛି");
        m.put("assistance.signal.entity-clause-precedent",
            "ଏହି ସଂସ୍ଥା ବିରୁଦ୍ଧରେ {{count}}ଟି ପୂର୍ବ ଅଭିଯୋଗରେ {{clause}} ଉଲ୍ଲେଖ କରାଯାଇଥିଲା");
        m.put("assistance.signal.category-closure-time",
            "ଏହି ଶ୍ରେଣୀରେ ସାଧାରଣତଃ {{days}} ଦିନରେ ନିଷ୍ପତ୍ତି ହୁଏ — ଏହି ଶ୍ରେଣୀର {{sample}}ଟି ବନ୍ଦ ଅଭିଯୋଗର ମଧ୍ୟମା");
        m.put("assistance.signal.category-closure-detail",
            "ଏହି ଶ୍ରେଣୀର {{sample}}ଟି ବନ୍ଦ ଅଭିଯୋଗର ମଧ୍ୟମା");
        return m;
    }

    /**
     * Assamese ({@code as}) — Bengali-Assamese script, {@code U+0980–U+09FF}.
     *
     * <p>Shares a script with {@link #bengali()} and is NOT a copy of it. The Assamese-specific forms
     * used here, which are what distinguish the two for a reader:
     * <ul>
     *   <li>{@code ৰ} (U+09F0) for Assamese /r/ wherever Bengali writes {@code র} (U+09B0) — so
     *       {@code অভিযোগকাৰী}, {@code কৰক}, {@code পুনৰ}, not {@code অভিযোগকারী}, {@code করুন};</li>
     *   <li>the {@code -ক} polite imperative ({@code কৰক}, {@code চেষ্টা কৰক}) rather than Bengali
     *       {@code -উন} ({@code করুন});</li>
     *   <li>{@code আপুনি} as the honorific second person, not {@code আপনি};</li>
     *   <li>the {@code ৰ} genitive and {@code -টা} classifier ({@code {{count}}টা}) in place of
     *       Bengali {@code -টি}.</li>
     * </ul>
     *
     * <p>{@code মধ্যমা} for the median carries the same caveat as the Odia map's.
     */
    private Map<String, String> assamese() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.panel_title", "সহায়তা");
        m.put("assistance.close", "সহায়তা পেনেল বন্ধ কৰক");
        m.put("assistance.tier0_group", "আপুনি য'ত এৰি গৈছিল");
        m.put("assistance.tier1_group", "সদৃশ অভিযোগৰ পৰা");
        m.put("assistance.nothing_to_report", "এই অভিযোগত ধ্যান দিবলগীয়া একো নাই।");
        m.put("assistance.error_failed", "সহায়তা পেনেল ল'ড হোৱা নাই।");
        m.put("assistance.error_failed_hint", "এইটো কোনো ফলাফল নহয়। সংকেত থাকিবও পাৰে।");
        m.put("assistance.retry", "পুনৰ চেষ্টা কৰক");
        m.put("assistance.dismiss", "এই ধৰণৰ পৰামৰ্শ নেদেখুৱাব");
        m.put("assistance.announce_signals", "এই অভিযোগৰ বাবে সহায়ৰ হাতত {{count}}টা পৰামৰ্শ আছে।");
        m.put("assistance.all_dismissed", "আপুনি এই স্ক্ৰীনৰ আটাইবোৰ পৰামৰ্শ খাৰিজ কৰিছে।");
        m.put("assistance.all_dismissed_hint", "আপুনি এই স্ক্ৰীন পুনৰ লোড নকৰালৈকে সেইবোৰ পুনৰ দেখুৱা নহ'ব।");
        m.put("assistance.bulb_label_count", "সহায় — {{count}} পৰামৰ্শ");

        m.put("assistance.signal.unsaved-draft", "আপুনি ইয়াত অসংৰক্ষিত লিখনি এৰি গৈছিল");
        m.put("assistance.signal.last-section", "আপুনি শেষবাৰ {{section}}-ত আছিল");
        m.put("assistance.signal.last-viewed", "আপুনি ইয়াক শেষবাৰ {{age}} খুলিছিল");
        m.put("assistance.signal.complainant-history",
            "এই অভিযোগকাৰীৰ {{count}}টা পূৰ্বৰ অভিযোগ আছে");
        m.put("assistance.signal.entity-clause-precedent",
            "এই প্ৰতিষ্ঠানৰ বিৰুদ্ধে {{count}}টা পূৰ্বৰ অভিযোগত {{clause}} উল্লেখ কৰা হৈছিল");
        m.put("assistance.signal.category-closure-time",
            "এই শ্ৰেণীত সাধাৰণতে {{days}} দিনত নিষ্পত্তি হয় — এই শ্ৰেণীৰ {{sample}}টা বন্ধ অভিযোগৰ মধ্যমা");
        m.put("assistance.signal.category-closure-detail",
            "এই শ্ৰেণীৰ {{sample}}টা বন্ধ অভিযোগৰ মধ্যমা");
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
