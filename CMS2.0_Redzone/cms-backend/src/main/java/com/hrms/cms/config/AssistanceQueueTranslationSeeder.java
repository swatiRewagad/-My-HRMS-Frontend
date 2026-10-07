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
 * Vocabulary for the queue-scoped assistance signals (Brief 21 §5.3 item 4, deadline triage).
 *
 * <h2>A NEW seeder at {@code @Order(73)} rather than an edit to {@code AssistanceRailTranslationSeeder}</h2>
 * The convention in this codebase is one seeder per feature with its own order, and the rail's seeder is
 * a 630-line chokepoint. Keeping these keys separate also keeps a real distinction visible: the
 * {@code assistance.signal.*} namespace describes ONE complaint, and {@code assistance.queue.*}
 * describes the officer's whole QUEUE. A reader who finds a queue key under the rail's namespace would
 * reasonably assume it was per-complaint.
 *
 * <p>{@code 73} was verified free — the taken orders in this package are 1–22, 32–34, 38, 42–45 and
 * 60–71. It runs AFTER the rail's {@code 71} for no functional reason (the two namespaces do not
 * overlap and {@code seed} is insert-if-absent either way), but ordering it later keeps the reading
 * order of the feature's seeders the same as the order the features were built in.
 *
 * <h2>Every failure mode in this file is SILENT</h2>
 * This is the single most important thing to know before changing a key or a placeholder here, and it
 * is why the coverage test exists. {@code TranslationService.translate} returns the KEY ITSELF when a
 * lookup misses, and substitutes only the {@code {{name}}} placeholders it is handed, leaving the rest
 * in place. The client then discards any resolved string that still carries {@code {{...}}} — or that
 * echoes the key back — and prints the server's resolved ENGLISH instead. So:
 *
 * <ul>
 *   <li>a MISSPELLED key code reverts that locale to English</li>
 *   <li>a MISSPELLED placeholder reverts that locale to English</li>
 *   <li>a MISSING locale reverts that locale to English</li>
 * </ul>
 *
 * None of those produce an error, an empty row, or a log line. All three are indistinguishable from
 * "this locale has not been translated yet", which is exactly what a reviewer would conclude. The
 * compiler cannot help: no Java code reads these strings.
 *
 * <h2>The key keeps the HYPHENATED kind</h2>
 * {@code assistance.queue.signal.queue-deadline-triage} spells the kind exactly as
 * {@code AssistanceQueueService.KIND_DEADLINE_TRIAGE} spells it, hyphens and all. Normalising to
 * underscores here would miss the client's lookup, silently, per above. The client holds its own
 * hardcoded key map — hardcoded so a second server-side kind cannot mint a plausible-looking key nobody
 * has translated — and that map, not this comment, is the authority on spelling. Adding a kind means
 * editing the map and this seeder together.
 *
 * <h2>The placeholders are a wire contract with {@code AssistanceQueueResponse.Signal.params}</h2>
 * Four names, all four emitted together by {@code AssistanceQueueService} in one {@code LinkedHashMap},
 * so there is no call path that supplies one without the others and none can be left unsubstituted:
 *
 * <ul>
 *   <li>{@code count} — the BREACHING count, the "3". Also the {@code count} FIELD on the signal. The
 *       client folds the field into {@code params['count']} only when params lacks the key, so a kind
 *       whose field meant something other than its placeholder would render a false sentence in all 13
 *       locales while the English title stayed correct. The service sends both, identical.</li>
 *   <li>{@code total} — the queue size, the "14". NOT decoration: Brief 21's position is that a bare
 *       figure with no denominator will be distrusted, correctly. "3 cases breach" invites the question
 *       "out of how many?", and an officer who cannot answer it cannot triage. Every locale below
 *       carries both numbers.</li>
 *   <li>{@code hours} — the configured window ({@code cms.assistance.deadline-window-hours}, default
 *       48). Interpolated rather than baked into the sentence because the window is operator-set, and a
 *       locale reading "48h" under a 72h configuration would be a lie in twelve languages.</li>
 *   <li>{@code overdue} — how many of the breaching cases are ALREADY past due. Used by the separate
 *       {@code .overdue_detail} key, not the main sentence.</li>
 * </ul>
 *
 * <h2>Why {@code .overdue_detail} is its own key and may not be reached</h2>
 * The server puts the overdue clause in the signal's {@code detail} field, and the rail's existing
 * component has NO detail-key map — one key per kind is all it looks up. The key is seeded so that
 * wiring a detail line is a template change rather than a template change plus twelve translations, and
 * because the number is in {@code params} regardless. VERIFY against the client's key map before
 * relying on it: a key nobody asks for cannot be assumed to be reaching anyone.
 *
 * <h2>Pluralisation is deliberately avoided, not forgotten</h2>
 * Every sentence below is phrased to read correctly for any count, because this stack has no ICU plural
 * support — {@code translate} does placeholder substitution and nothing else. English says "cases"
 * rather than "case(s)" and accepts that "1 of your 14 cases" is slightly stiff, which is a better
 * trade than a parenthesis in thirteen languages. The floors in
 * {@code AssistanceQueueService} mean {@code total} is never below 3, so the denominator is always
 * plural in fact.
 *
 * <h2>Assamese is not a copy of Bengali</h2>
 * Same rule as the rail's seeder. Assamese uses {@code ৰ} for {@code র}, takes {@code ৰ} for the
 * genitive, and uses the {@code -ক} polite imperative. Copying {@code bengali()} under the {@code as}
 * key would ship a locale that reads as foreign to an Assamese officer while passing any coverage test
 * that only counted keys — so the coverage test also asserts the two maps are not identical.
 *
 * <h2>Every locale is phrased as a REPORT, never an instruction</h2>
 * "N of your M cases breach within Xh", not "prioritise these N". §5.1 forbids the rail from reordering
 * or deciding anything, and a sentence that reads as a directive in one locale quietly converts an
 * ambient count into an order for the officers who read that language.
 */
@Component
@Order(73)
public class AssistanceQueueTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "assistance";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    public AssistanceQueueTranslationSeeder(TranslationKeyRepository keyRepo,
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
        m.put("assistance.queue.group", "Across your cases");
        m.put("assistance.queue.dismiss", "Dismiss this queue alert");
        m.put("assistance.queue.bulb_label", "Deadline alert — {{count}} of {{total}} cases");
        m.put("assistance.queue.announce",
            "{{count}} of your {{total}} cases breach within {{hours}} hours.");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "{{count}} of your {{total}} cases breach within {{hours}}h");
        m.put("assistance.queue.overdue_detail", "{{overdue}} of these are already overdue");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "आपके सभी मामलों में");
        m.put("assistance.queue.dismiss", "यह समय-सीमा सूचना हटाएं");
        m.put("assistance.queue.bulb_label", "समय-सीमा सूचना — {{total}} में से {{count}} मामले");
        m.put("assistance.queue.announce",
            "आपके {{total}} मामलों में से {{count}} की समय-सीमा {{hours}} घंटों में समाप्त हो रही है।");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "आपके {{total}} मामलों में से {{count}} की समय-सीमा {{hours}} घंटों में समाप्त हो रही है");
        m.put("assistance.queue.overdue_detail", "इनमें से {{overdue}} की समय-सीमा पहले ही बीत चुकी है");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "तुमच्या सर्व प्रकरणांमध्ये");
        m.put("assistance.queue.dismiss", "ही मुदत सूचना काढून टाका");
        m.put("assistance.queue.bulb_label", "मुदत सूचना — {{total}} पैकी {{count}} प्रकरणे");
        m.put("assistance.queue.announce",
            "तुमच्या {{total}} प्रकरणांपैकी {{count}} प्रकरणांची मुदत {{hours}} तासांत संपत आहे.");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "तुमच्या {{total}} प्रकरणांपैकी {{count}} प्रकरणांची मुदत {{hours}} तासांत संपत आहे");
        m.put("assistance.queue.overdue_detail", "यांपैकी {{overdue}} प्रकरणांची मुदत आधीच उलटली आहे");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "আপনার সব মামলা জুড়ে");
        m.put("assistance.queue.dismiss", "এই সময়সীমার সতর্কতা সরান");
        m.put("assistance.queue.bulb_label", "সময়সীমার সতর্কতা — {{total}}টির মধ্যে {{count}}টি মামলা");
        m.put("assistance.queue.announce",
            "আপনার {{total}}টি মামলার মধ্যে {{count}}টির সময়সীমা {{hours}} ঘণ্টার মধ্যে শেষ হচ্ছে।");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "আপনার {{total}}টি মামলার মধ্যে {{count}}টির সময়সীমা {{hours}} ঘণ্টার মধ্যে শেষ হচ্ছে");
        m.put("assistance.queue.overdue_detail", "এর মধ্যে {{overdue}}টির সময়সীমা ইতিমধ্যেই পার হয়েছে");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "మీ అన్ని కేసులలో");
        m.put("assistance.queue.dismiss", "ఈ గడువు హెచ్చరికను తొలగించండి");
        m.put("assistance.queue.bulb_label", "గడువు హెచ్చరిక — {{total}}లో {{count}} కేసులు");
        m.put("assistance.queue.announce",
            "మీ {{total}} కేసులలో {{count}} కేసుల గడువు {{hours}} గంటలలో ముగుస్తుంది.");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "మీ {{total}} కేసులలో {{count}} కేసుల గడువు {{hours}} గంటలలో ముగుస్తుంది");
        m.put("assistance.queue.overdue_detail", "వీటిలో {{overdue}} కేసుల గడువు ఇప్పటికే ముగిసింది");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "உங்கள் அனைத்து வழக்குகளிலும்");
        m.put("assistance.queue.dismiss", "இந்த காலக்கெடு அறிவிப்பை நீக்கு");
        m.put("assistance.queue.bulb_label", "காலக்கெடு அறிவிப்பு — {{total}}ல் {{count}} வழக்குகள்");
        m.put("assistance.queue.announce",
            "உங்கள் {{total}} வழக்குகளில் {{count}} வழக்குகளின் காலக்கெடு {{hours}} மணி நேரத்தில் முடிகிறது.");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "உங்கள் {{total}} வழக்குகளில் {{count}} வழக்குகளின் காலக்கெடு {{hours}} மணி நேரத்தில் முடிகிறது");
        m.put("assistance.queue.overdue_detail", "இவற்றில் {{overdue}} வழக்குகளின் காலக்கெடு ஏற்கனவே கடந்துவிட்டது");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "તમારા બધા કેસોમાં");
        m.put("assistance.queue.dismiss", "આ સમયમર્યાદા સૂચના દૂર કરો");
        m.put("assistance.queue.bulb_label", "સમયમર્યાદા સૂચના — {{total}} માંથી {{count}} કેસ");
        m.put("assistance.queue.announce",
            "તમારા {{total}} કેસોમાંથી {{count}} કેસની સમયમર્યાદા {{hours}} કલાકમાં પૂરી થાય છે.");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "તમારા {{total}} કેસોમાંથી {{count}} કેસની સમયમર્યાદા {{hours}} કલાકમાં પૂરી થાય છે");
        m.put("assistance.queue.overdue_detail", "આમાંથી {{overdue}} કેસની સમયમર્યાદા પહેલેથી જ વીતી ગઈ છે");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "آپ کے تمام مقدمات میں");
        m.put("assistance.queue.dismiss", "یہ ڈیڈ لائن اطلاع ہٹائیں");
        m.put("assistance.queue.bulb_label", "ڈیڈ لائن اطلاع — {{total}} میں سے {{count}} مقدمات");
        m.put("assistance.queue.announce",
            "آپ کے {{total}} مقدمات میں سے {{count}} کی مدت {{hours}} گھنٹوں میں ختم ہو رہی ہے۔");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "آپ کے {{total}} مقدمات میں سے {{count}} کی مدت {{hours}} گھنٹوں میں ختم ہو رہی ہے");
        m.put("assistance.queue.overdue_detail", "ان میں سے {{overdue}} کی مدت پہلے ہی گزر چکی ہے");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "ನಿಮ್ಮ ಎಲ್ಲಾ ಪ್ರಕರಣಗಳಲ್ಲಿ");
        m.put("assistance.queue.dismiss", "ಈ ಗಡುವಿನ ಸೂಚನೆಯನ್ನು ತೆಗೆದುಹಾಕಿ");
        m.put("assistance.queue.bulb_label", "ಗಡುವಿನ ಸೂಚನೆ — {{total}} ರಲ್ಲಿ {{count}} ಪ್ರಕರಣಗಳು");
        m.put("assistance.queue.announce",
            "ನಿಮ್ಮ {{total}} ಪ್ರಕರಣಗಳಲ್ಲಿ {{count}} ಪ್ರಕರಣಗಳ ಗಡುವು {{hours}} ಗಂಟೆಗಳಲ್ಲಿ ಮುಗಿಯುತ್ತದೆ.");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "ನಿಮ್ಮ {{total}} ಪ್ರಕರಣಗಳಲ್ಲಿ {{count}} ಪ್ರಕರಣಗಳ ಗಡುವು {{hours}} ಗಂಟೆಗಳಲ್ಲಿ ಮುಗಿಯುತ್ತದೆ");
        m.put("assistance.queue.overdue_detail", "ಇವುಗಳಲ್ಲಿ {{overdue}} ಪ್ರಕರಣಗಳ ಗಡುವು ಈಗಾಗಲೇ ಮುಗಿದಿದೆ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "നിങ്ങളുടെ എല്ലാ കേസുകളിലും");
        m.put("assistance.queue.dismiss", "ഈ സമയപരിധി അറിയിപ്പ് നീക്കം ചെയ്യുക");
        m.put("assistance.queue.bulb_label", "സമയപരിധി അറിയിപ്പ് — {{total}} ൽ {{count}} കേസുകൾ");
        m.put("assistance.queue.announce",
            "നിങ്ങളുടെ {{total}} കേസുകളിൽ {{count}} കേസുകളുടെ സമയപരിധി {{hours}} മണിക്കൂറിനുള്ളിൽ അവസാനിക്കുന്നു.");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "നിങ്ങളുടെ {{total}} കേസുകളിൽ {{count}} കേസുകളുടെ സമയപരിധി {{hours}} മണിക്കൂറിനുള്ളിൽ അവസാനിക്കുന്നു");
        m.put("assistance.queue.overdue_detail", "ഇവയിൽ {{overdue}} കേസുകളുടെ സമയപരിധി ഇതിനകം കഴിഞ്ഞു");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "ਤੁਹਾਡੇ ਸਾਰੇ ਮਾਮਲਿਆਂ ਵਿੱਚ");
        m.put("assistance.queue.dismiss", "ਇਹ ਸਮਾਂ-ਸੀਮਾ ਸੂਚਨਾ ਹਟਾਓ");
        m.put("assistance.queue.bulb_label", "ਸਮਾਂ-ਸੀਮਾ ਸੂਚਨਾ — {{total}} ਵਿੱਚੋਂ {{count}} ਮਾਮਲੇ");
        m.put("assistance.queue.announce",
            "ਤੁਹਾਡੇ {{total}} ਮਾਮਲਿਆਂ ਵਿੱਚੋਂ {{count}} ਦੀ ਸਮਾਂ-ਸੀਮਾ {{hours}} ਘੰਟਿਆਂ ਵਿੱਚ ਖਤਮ ਹੋ ਰਹੀ ਹੈ।");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "ਤੁਹਾਡੇ {{total}} ਮਾਮਲਿਆਂ ਵਿੱਚੋਂ {{count}} ਦੀ ਸਮਾਂ-ਸੀਮਾ {{hours}} ਘੰਟਿਆਂ ਵਿੱਚ ਖਤਮ ਹੋ ਰਹੀ ਹੈ");
        m.put("assistance.queue.overdue_detail", "ਇਹਨਾਂ ਵਿੱਚੋਂ {{overdue}} ਦੀ ਸਮਾਂ-ਸੀਮਾ ਪਹਿਲਾਂ ਹੀ ਬੀਤ ਚੁੱਕੀ ਹੈ");
        return m;
    }

    private Map<String, String> odia() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "ଆପଣଙ୍କ ସମସ୍ତ ମାମଲାରେ");
        m.put("assistance.queue.dismiss", "ଏହି ସମୟସୀମା ସୂଚନା ହଟାନ୍ତୁ");
        m.put("assistance.queue.bulb_label", "ସମୟସୀମା ସୂଚନା — {{total}} ମଧ୍ୟରୁ {{count}} ମାମଲା");
        m.put("assistance.queue.announce",
            "ଆପଣଙ୍କ {{total}} ମାମଲା ମଧ୍ୟରୁ {{count}}ଟିର ସମୟସୀମା {{hours}} ଘଣ୍ଟା ମଧ୍ୟରେ ଶେଷ ହେଉଛି।");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "ଆପଣଙ୍କ {{total}} ମାମଲା ମଧ୍ୟରୁ {{count}}ଟିର ସମୟସୀମା {{hours}} ଘଣ୍ଟା ମଧ୍ୟରେ ଶେଷ ହେଉଛି");
        m.put("assistance.queue.overdue_detail", "ଏହା ମଧ୍ୟରୁ {{overdue}}ଟିର ସମୟସୀମା ପୂର୍ବରୁ ଶେଷ ହୋଇଯାଇଛି");
        return m;
    }

    private Map<String, String> assamese() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("assistance.queue.group", "আপোনাৰ সকলো গোচৰত");
        m.put("assistance.queue.dismiss", "এই সময়সীমাৰ সূচনা আঁতৰাওক");
        m.put("assistance.queue.bulb_label", "সময়সীমাৰ সূচনা — {{total}}ৰ ভিতৰত {{count}} গোচৰ");
        m.put("assistance.queue.announce",
            "আপোনাৰ {{total}} গোচৰৰ ভিতৰত {{count}}টাৰ সময়সীমা {{hours}} ঘণ্টাৰ ভিতৰত শেষ হৈছে।");
        m.put("assistance.queue.signal.queue-deadline-triage",
            "আপোনাৰ {{total}} গোচৰৰ ভিতৰত {{count}}টাৰ সময়সীমা {{hours}} ঘণ্টাৰ ভিতৰত শেষ হৈছে");
        m.put("assistance.queue.overdue_detail", "ইয়াৰ ভিতৰত {{overdue}}টাৰ সময়সীমা ইতিমধ্যে পাৰ হৈ গৈছে");
        return m;
    }

    /**
     * Insert-if-absent by key code, exactly as the rail's seeder does.
     *
     * <p>Idempotent on the KEY, which means an operator who has corrected a value in the database keeps
     * their correction across restarts rather than having it overwritten every boot.
     */
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
