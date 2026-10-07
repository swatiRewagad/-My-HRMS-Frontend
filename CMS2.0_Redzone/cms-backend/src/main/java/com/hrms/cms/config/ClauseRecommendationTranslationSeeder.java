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
 * Vocabulary for the closure-clause recommendation (Brief 21 §5.3.2).
 *
 * <h2>Why these strings must be translated at all</h2>
 * The recommendation annotates a {@code <select>} that an officer uses to CLOSE a complaint. The
 * annotation is the only thing standing between "this option moved to the top" and "this option moved
 * to the top for a reason I can weigh" — Brief 21's position is that "a bare recommendation with no
 * denominator will be distrusted, correctly". An untranslated annotation is a bare recommendation for
 * every officer who does not read English, which is worse than no annotation at all: the ORDER still
 * changes, so they are influenced without being told why.
 *
 * <h2>Both numbers in every locale, never the percentage alone</h2>
 * {@code clause-recommendation.used_in} carries {@code count} AND {@code total} in all thirteen
 * locales. A locale phrased as "usually cited" or "most common" would be the exact failure the brief
 * names — it reads as authority while hiding how much evidence is behind it, and an officer has no way
 * to distinguish 777-of-815 from 3-of-5. No locale here states a share without its denominator.
 *
 * <h2>Every locale READS AS A REPORT, never as an instruction</h2>
 * §5.1 requires the system to suggest and never auto-apply. That is enforced structurally on the wire
 * ({@code ClauseRecommendationResponse} has no field a client could read as a selection) but it can be
 * undone by phrasing: a locale rendered as "cite 15(1)(a)" converts a historical count into a
 * directive, and the officers who read that locale would be receiving a different product. So every
 * string below is in the past tense about OTHER closures — "was cited in N of M comparable closures" —
 * and none uses an imperative.
 *
 * <h2>Clause codes are NEVER translated</h2>
 * {@code 15(1)(a)} is a statutory citation and must read identically in every locale; it is also the
 * string the officer matches against the {@code <option>} values the picker renders, so a localised
 * form would name an option that does not exist. The {@code clause} placeholder is interpolated
 * verbatim everywhere, exactly as {@code AssistanceRailTranslationSeeder} does for the same reason.
 *
 * <p>The clause LABEL is not seeded here either, and must not be: it comes from
 * {@code CLOSURE_CLAUSE_MASTER.label_key}, which the response carries through as {@code labelKey}. No
 * legal text is authored in this codebase — {@code ClosureClauseAccessService}'s header records that
 * two invented 2026 clauses had to be removed for exactly this reason.
 *
 * <h2>Placeholder names are a wire contract</h2>
 * {@code count} and {@code total} are the two numbers {@code ClauseRecommendationResponse.Recommendation}
 * carries as {@code occurrences} and {@code cohortTotal}. The trap is documented at length on
 * {@code AssistanceRailTranslationSeeder} and applies identically here: the client discards a resolved
 * string that still contains a {@code {{...}}} and falls back to English, so a placeholder TYPO does not
 * render visibly broken text — it silently reverts that one locale to English, which is
 * indistinguishable from the locale never having been translated. No error, no empty row, nothing in a
 * log. {@code ClauseRecommendationTranslationCoverageTest} asserts the placeholder sets match across
 * all thirteen locales for that reason.
 *
 * <h2>Hyphenated key segment, kept verbatim</h2>
 * {@code clause-recommendation}, with the hyphen, matching the {@code cms.assistance.clause-recommendation.enabled}
 * property and the {@code /clause-recommendation} route. Normalising it to an underscore here would miss
 * every lookup the client makes, and the miss is the silent English reversion described above.
 *
 * <h2>Count-neutral phrasing, on purpose</h2>
 * One i18n value per key cannot branch on singular/plural, so the counted strings carry the plural
 * form. Same decision as the rail seeder, for the same reason: the plural is the agreed English
 * contract and the string the client's fallback is compared against.
 *
 * <h2>A NEW seeder at {@code @Order(72)}</h2>
 * Rather than an edit to {@code AssistanceRailTranslationSeeder} (71), per the convention here:
 * concurrent sessions add keys, a shared file is a guaranteed conflict, and a conflict in a seeder
 * silently costs a locale. 1-22, 32-34, 38, 42-45, 60-71 and 73 were verified taken before choosing 72.
 * The {@code clause-recommendation.*} namespace was verified unused before this was written.
 *
 * <p>Seeding is insert-if-absent by key code, so re-running is safe.
 *
 * <h2>All THIRTEEN product locales</h2>
 * {@code en, hi, mr, bn, ur, te, ta, ml, kn, gu, pa, or, as} — the set
 * {@code LanguageTranslationService} enumerates. Odia and Assamese are included because omitting them
 * is invisible: those officers would be served English with no missing-key error anywhere. Assamese is
 * NOT Bengali relabelled — it uses {@code ৰ} for {@code র}, takes {@code ৰ} for the genitive, and uses
 * the {@code -ক} polite imperative — so the coverage test asserts the two maps are not identical, which
 * is the only way a copy-paste gets caught.
 */
@Component
@Order(72)
public class ClauseRecommendationTranslationSeeder implements CommandLineRunner {

    /**
     * {@code assistance}, shared with the rail's seeder rather than a new module name.
     *
     * <p>The module column groups keys for the translation-admin screens, and these keys belong to the
     * same feature family an operator would look under. The key CODES are namespaced
     * ({@code clause-recommendation.*}) so nothing collides; it is only the grouping that is shared.
     */
    private static final String MODULE = "assistance";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public ClauseRecommendationTranslationSeeder(TranslationKeyRepository keyRepo,
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
        // The caption above the picker when at least one option carries evidence. States the
        // denominator ONCE, so each annotated option does not have to repeat it.
        m.put("clause-recommendation.heading",
            "Ordered by what {{total}} comparable closures cited");
        // The per-option annotation. BOTH numbers, never the percentage alone.
        m.put("clause-recommendation.used_in",
            "cited in {{count}} of {{total}} comparable closures");
        // Screen-reader text for the same annotation, which must be a complete sentence because it is
        // read out of the visual context that makes the fragment above make sense.
        m.put("clause-recommendation.used_in_aria",
            "This clause was cited in {{count}} of {{total}} comparable closures.");
        // Shown beside the top-ranked option only. Deliberately NOT "recommended": the register
        // reporting what it did is a different claim from the system endorsing it.
        m.put("clause-recommendation.most_cited", "Most cited here");
        // The honest caveat, shown with the heading. This is the sentence that keeps a count from being
        // read as a rule, and it is why no locale below uses an imperative.
        m.put("clause-recommendation.advisory",
            "This is what past closures did, not a recommendation. Choose the clause that fits this complaint.");
        // Shown when the switch is on but the rollup has nothing for this complaint — which is the
        // MEASURED common case, not an edge: an RBIO officer gets this on every RBIO complaint because
        // the only clause those cohorts contain is one their role may not cite.
        m.put("clause-recommendation.no_history",
            "No comparable closures to order these by.");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} समान निपटानों में उद्धृत खंडों के क्रम में");
        m.put("clause-recommendation.used_in",
            "{{total}} समान निपटानों में से {{count}} में उद्धृत");
        m.put("clause-recommendation.used_in_aria",
            "यह खंड {{total}} समान निपटानों में से {{count}} में उद्धृत किया गया था।");
        m.put("clause-recommendation.most_cited", "यहाँ सर्वाधिक उद्धृत");
        m.put("clause-recommendation.advisory",
            "यह पिछले निपटानों में हुआ था, कोई सिफ़ारिश नहीं है। इस शिकायत के अनुरूप खंड चुनें।");
        m.put("clause-recommendation.no_history",
            "इन्हें क्रम देने के लिए कोई समान निपटान उपलब्ध नहीं है।");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} समान निपटारांमध्ये उद्धृत केलेल्या कलमांच्या क्रमाने");
        m.put("clause-recommendation.used_in",
            "{{total}} समान निपटारांपैकी {{count}} मध्ये उद्धृत");
        m.put("clause-recommendation.used_in_aria",
            "हे कलम {{total}} समान निपटारांपैकी {{count}} मध्ये उद्धृत केले गेले होते.");
        m.put("clause-recommendation.most_cited", "येथे सर्वाधिक उद्धृत");
        m.put("clause-recommendation.advisory",
            "हे पूर्वीच्या निपटारांमध्ये घडले होते, ही शिफारस नाही. या तक्रारीला साजेसे कलम निवडा.");
        m.put("clause-recommendation.no_history",
            "यांना क्रम देण्यासाठी कोणतेही समान निपटारे उपलब्ध नाहीत.");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}}টি তুলনীয় নিষ্পত্তিতে উল্লিখিত ধারার ক্রমে");
        m.put("clause-recommendation.used_in",
            "{{total}}টি তুলনীয় নিষ্পত্তির মধ্যে {{count}}টিতে উল্লিখিত");
        m.put("clause-recommendation.used_in_aria",
            "এই ধারাটি {{total}}টি তুলনীয় নিষ্পত্তির মধ্যে {{count}}টিতে উল্লেখ করা হয়েছিল।");
        m.put("clause-recommendation.most_cited", "এখানে সর্বাধিক উল্লিখিত");
        m.put("clause-recommendation.advisory",
            "এটি পূর্ববর্তী নিষ্পত্তিতে যা হয়েছিল, কোনো সুপারিশ নয়। এই অভিযোগের উপযুক্ত ধারা বেছে নিন।");
        m.put("clause-recommendation.no_history",
            "এগুলি ক্রমানুসারে সাজানোর জন্য কোনো তুলনীয় নিষ্পত্তি নেই।");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} పోల్చదగిన ముగింపులలో ఉదహరించిన నిబంధనల క్రమంలో");
        m.put("clause-recommendation.used_in",
            "{{total}} పోల్చదగిన ముగింపులలో {{count}}లో ఉదహరించబడింది");
        m.put("clause-recommendation.used_in_aria",
            "ఈ నిబంధన {{total}} పోల్చదగిన ముగింపులలో {{count}}లో ఉదహరించబడింది.");
        m.put("clause-recommendation.most_cited", "ఇక్కడ అత్యధికంగా ఉదహరించబడినది");
        m.put("clause-recommendation.advisory",
            "ఇది గత ముగింపులలో జరిగినది, సిఫార్సు కాదు. ఈ ఫిర్యాదుకు సరిపోయే నిబంధనను ఎంచుకోండి.");
        m.put("clause-recommendation.no_history",
            "వీటిని క్రమబద్ధీకరించడానికి పోల్చదగిన ముగింపులు లేవు.");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} ஒப்பிடத்தக்க முடிவுகளில் குறிப்பிடப்பட்ட பிரிவுகளின் வரிசையில்");
        m.put("clause-recommendation.used_in",
            "{{total}} ஒப்பிடத்தக்க முடிவுகளில் {{count}}-இல் குறிப்பிடப்பட்டது");
        m.put("clause-recommendation.used_in_aria",
            "இந்தப் பிரிவு {{total}} ஒப்பிடத்தக்க முடிவுகளில் {{count}}-இல் குறிப்பிடப்பட்டது.");
        m.put("clause-recommendation.most_cited", "இங்கு அதிகம் குறிப்பிடப்பட்டது");
        m.put("clause-recommendation.advisory",
            "இது முந்தைய முடிவுகளில் நடந்தது, இது ஒரு பரிந்துரை அல்ல. இந்த புகாருக்குப் பொருத்தமான பிரிவைத் தேர்ந்தெடுக்கவும்.");
        m.put("clause-recommendation.no_history",
            "இவற்றை வரிசைப்படுத்த ஒப்பிடத்தக்க முடிவுகள் இல்லை.");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} સમાન નિકાલમાં ઉલ્લેખિત કલમોના ક્રમમાં");
        m.put("clause-recommendation.used_in",
            "{{total}} સમાન નિકાલમાંથી {{count}}માં ઉલ્લેખિત");
        m.put("clause-recommendation.used_in_aria",
            "આ કલમ {{total}} સમાન નિકાલમાંથી {{count}}માં ઉલ્લેખવામાં આવી હતી.");
        m.put("clause-recommendation.most_cited", "અહીં સૌથી વધુ ઉલ્લેખિત");
        m.put("clause-recommendation.advisory",
            "આ ભૂતકાળના નિકાલમાં થયું હતું, કોઈ ભલામણ નથી. આ ફરિયાદને અનુરૂપ કલમ પસંદ કરો.");
        m.put("clause-recommendation.no_history",
            "આને ક્રમ આપવા માટે કોઈ સમાન નિકાલ ઉપલબ્ધ નથી.");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} مشابہ تصفیوں میں حوالہ دی گئی شرائط کی ترتیب میں");
        m.put("clause-recommendation.used_in",
            "{{total}} مشابہ تصفیوں میں سے {{count}} میں حوالہ دیا گیا");
        m.put("clause-recommendation.used_in_aria",
            "اس شرط کا {{total}} مشابہ تصفیوں میں سے {{count}} میں حوالہ دیا گیا تھا۔");
        m.put("clause-recommendation.most_cited", "یہاں سب سے زیادہ حوالہ دی گئی");
        m.put("clause-recommendation.advisory",
            "یہ گزشتہ تصفیوں میں ہوا تھا، کوئی سفارش نہیں ہے۔ اس شکایت کے مطابق شرط منتخب کریں۔");
        m.put("clause-recommendation.no_history",
            "ان کو ترتیب دینے کے لیے کوئی مشابہ تصفیہ موجود نہیں ہے۔");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} ಹೋಲಿಸಬಹುದಾದ ಮುಕ್ತಾಯಗಳಲ್ಲಿ ಉಲ್ಲೇಖಿಸಿದ ಷರತ್ತುಗಳ ಕ್ರಮದಲ್ಲಿ");
        m.put("clause-recommendation.used_in",
            "{{total}} ಹೋಲಿಸಬಹುದಾದ ಮುಕ್ತಾಯಗಳಲ್ಲಿ {{count}}ರಲ್ಲಿ ಉಲ್ಲೇಖಿಸಲಾಗಿದೆ");
        m.put("clause-recommendation.used_in_aria",
            "ಈ ಷರತ್ತನ್ನು {{total}} ಹೋಲಿಸಬಹುದಾದ ಮುಕ್ತಾಯಗಳಲ್ಲಿ {{count}}ರಲ್ಲಿ ಉಲ್ಲೇಖಿಸಲಾಗಿತ್ತು.");
        m.put("clause-recommendation.most_cited", "ಇಲ್ಲಿ ಹೆಚ್ಚು ಉಲ್ಲೇಖಿಸಲಾದದ್ದು");
        m.put("clause-recommendation.advisory",
            "ಇದು ಹಿಂದಿನ ಮುಕ್ತಾಯಗಳಲ್ಲಿ ನಡೆದದ್ದು, ಶಿಫಾರಸು ಅಲ್ಲ. ಈ ದೂರಿಗೆ ಸರಿಹೊಂದುವ ಷರತ್ತನ್ನು ಆಯ್ಕೆಮಾಡಿ.");
        m.put("clause-recommendation.no_history",
            "ಇವುಗಳನ್ನು ಕ್ರಮಬದ್ಧಗೊಳಿಸಲು ಹೋಲಿಸಬಹುದಾದ ಮುಕ್ತಾಯಗಳಿಲ್ಲ.");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} സമാന തീർപ്പുകളിൽ ഉദ്ധരിച്ച വ്യവസ്ഥകളുടെ ക്രമത്തിൽ");
        m.put("clause-recommendation.used_in",
            "{{total}} സമാന തീർപ്പുകളിൽ {{count}} എണ്ണത്തിൽ ഉദ്ധരിച്ചു");
        m.put("clause-recommendation.used_in_aria",
            "ഈ വ്യവസ്ഥ {{total}} സമാന തീർപ്പുകളിൽ {{count}} എണ്ണത്തിൽ ഉദ്ധരിച്ചിരുന്നു.");
        m.put("clause-recommendation.most_cited", "ഇവിടെ ഏറ്റവും കൂടുതൽ ഉദ്ധരിച്ചത്");
        m.put("clause-recommendation.advisory",
            "ഇത് മുൻ തീർപ്പുകളിൽ സംഭവിച്ചതാണ്, ഒരു ശുപാർശയല്ല. ഈ പരാതിക്ക് അനുയോജ്യമായ വ്യവസ്ഥ തിരഞ്ഞെടുക്കുക.");
        m.put("clause-recommendation.no_history",
            "ഇവ ക്രമീകരിക്കാൻ സമാന തീർപ്പുകളില്ല.");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}} ਸਮਾਨ ਨਿਪਟਾਰਿਆਂ ਵਿੱਚ ਹਵਾਲਾ ਦਿੱਤੀਆਂ ਧਾਰਾਵਾਂ ਦੇ ਕ੍ਰਮ ਵਿੱਚ");
        m.put("clause-recommendation.used_in",
            "{{total}} ਸਮਾਨ ਨਿਪਟਾਰਿਆਂ ਵਿੱਚੋਂ {{count}} ਵਿੱਚ ਹਵਾਲਾ ਦਿੱਤਾ ਗਿਆ");
        m.put("clause-recommendation.used_in_aria",
            "ਇਸ ਧਾਰਾ ਦਾ {{total}} ਸਮਾਨ ਨਿਪਟਾਰਿਆਂ ਵਿੱਚੋਂ {{count}} ਵਿੱਚ ਹਵਾਲਾ ਦਿੱਤਾ ਗਿਆ ਸੀ।");
        m.put("clause-recommendation.most_cited", "ਇੱਥੇ ਸਭ ਤੋਂ ਵੱਧ ਹਵਾਲਾ ਦਿੱਤੀ ਗਈ");
        m.put("clause-recommendation.advisory",
            "ਇਹ ਪਿਛਲੇ ਨਿਪਟਾਰਿਆਂ ਵਿੱਚ ਹੋਇਆ ਸੀ, ਕੋਈ ਸਿਫ਼ਾਰਸ਼ ਨਹੀਂ ਹੈ। ਇਸ ਸ਼ਿਕਾਇਤ ਦੇ ਅਨੁਕੂਲ ਧਾਰਾ ਚੁਣੋ।");
        m.put("clause-recommendation.no_history",
            "ਇਹਨਾਂ ਨੂੰ ਕ੍ਰਮ ਦੇਣ ਲਈ ਕੋਈ ਸਮਾਨ ਨਿਪਟਾਰਾ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।");
        return m;
    }

    private Map<String, String> odia() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}}ଟି ତୁଳନୀୟ ନିଷ୍ପତ୍ତିରେ ଉଦ୍ଧୃତ ଧାରାର କ୍ରମରେ");
        m.put("clause-recommendation.used_in",
            "{{total}}ଟି ତୁଳନୀୟ ନିଷ୍ପତ୍ତି ମଧ୍ୟରୁ {{count}}ଟିରେ ଉଦ୍ଧୃତ");
        m.put("clause-recommendation.used_in_aria",
            "ଏହି ଧାରା {{total}}ଟି ତୁଳନୀୟ ନିଷ୍ପତ୍ତି ମଧ୍ୟରୁ {{count}}ଟିରେ ଉଦ୍ଧୃତ ହୋଇଥିଲା।");
        m.put("clause-recommendation.most_cited", "ଏଠାରେ ସର୍ବାଧିକ ଉଦ୍ଧୃତ");
        m.put("clause-recommendation.advisory",
            "ଏହା ପୂର୍ବ ନିଷ୍ପତ୍ତିରେ ଘଟିଥିଲା, କୌଣସି ସୁପାରିଶ ନୁହେଁ। ଏହି ଅଭିଯୋଗ ପାଇଁ ଉପଯୁକ୍ତ ଧାରା ବାଛନ୍ତୁ।");
        m.put("clause-recommendation.no_history",
            "ଏଗୁଡ଼ିକୁ କ୍ରମରେ ସଜାଇବା ପାଇଁ କୌଣସି ତୁଳନୀୟ ନିଷ୍ପତ୍ତି ନାହିଁ।");
        return m;
    }

    /**
     * Assamese, written rather than copied from {@link #bengali()}.
     *
     * <p>Same script, different language: {@code ৰ} for Bengali {@code র}, the {@code ৰ} genitive, and
     * the {@code -ক} polite imperative ({@code কৰক}, not {@code করুন}). A copy of the Bengali map would
     * pass any coverage test that merely counted keys while reading as foreign to an Assamese officer,
     * which is why the coverage test asserts the two maps differ.
     */
    private Map<String, String> assamese() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clause-recommendation.heading",
            "{{total}}টা তুলনীয় নিষ্পত্তিত উল্লেখ কৰা ধাৰাৰ ক্ৰমত");
        m.put("clause-recommendation.used_in",
            "{{total}}টা তুলনীয় নিষ্পত্তিৰ ভিতৰত {{count}}টাত উল্লেখ কৰা হৈছে");
        m.put("clause-recommendation.used_in_aria",
            "এই ধাৰাটো {{total}}টা তুলনীয় নিষ্পত্তিৰ ভিতৰত {{count}}টাত উল্লেখ কৰা হৈছিল।");
        m.put("clause-recommendation.most_cited", "ইয়াত সৰ্বাধিক উল্লেখ কৰা");
        m.put("clause-recommendation.advisory",
            "এইটো পূৰ্বৰ নিষ্পত্তিত হৈছিল, কোনো পৰামৰ্শ নহয়। এই অভিযোগৰ উপযুক্ত ধাৰা বাছনি কৰক।");
        m.put("clause-recommendation.no_history",
            "এইবোৰ ক্ৰমত সজাবৰ বাবে কোনো তুলনীয় নিষ্পত্তি নাই।");
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
