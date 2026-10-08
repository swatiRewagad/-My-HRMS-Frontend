package com.hrms.cms.config;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A fixed, synthetic complaint corpus in all 13 served languages, so that language-aware search can
 * be demonstrated and regression-tested against real Indic text.
 *
 * <p><b>Why this exists.</b> The complaint index carries a {@code cms_indic} base analyzer plus
 * {@code en}/{@code hi}/{@code bn}/{@code ur} analyzed subfields, and the mapping was proven correct
 * against a live node — but there was no data in any language other than English. Nobody could
 * demonstrate that per-language retrieval works, and nothing would fail if a future mapping change
 * silently broke it. Twenty-six documents fix both.
 *
 * <p><b>The corpus is a matched ROOT/INFL pair per language, and that shape is the whole point.</b>
 * Only four languages have a real analyzer. Measured on a live Elasticsearch 8.15.3 node against the
 * shipped mapping:
 *
 * <ul>
 *   <li>{@code en}, {@code hi}, {@code bn} stem. Querying the Hindi root शिकायत on the base
 *       {@code description} field reaches only the ROOT document; on {@code description.hi} it reaches
 *       BOTH, because {@code hindi_stemmer} reduces शिकायतों to शिकायत.
 *   <li>{@code ur} normalises Arabic script but does not stem — the Arabic-form spelling شكايت reaches
 *       the Urdu-form document شکایت, while the plural شکایات does not reach the singular.
 *   <li>The other <b>nine</b> ({@code ta te mr gu kn ml pa or as}) have NO stemmer. They use
 *       {@code cms_indic} (icu_tokenizer + indic_normalization + decimal_digit + icu_folding), which
 *       tokenises, normalises and folds but never stems, so only surface-form matching works.
 * </ul>
 *
 * <p>Two of those nine are worth naming because the code looks like it helps them and does not.
 * {@code LanguageDetector.analyzedSubfield} maps {@code mr -> hi} and {@code as -> bn}, since Marathi
 * is indistinguishable from Hindi by script and Assamese from Bengali. But borrowing the subfield does
 * not buy stemming for the borrowing language: measured, {@code description.hi:"तक्रार"} does not reach
 * the Marathi तक्रारींवर document, and {@code description.bn:"অভিযোগ"} does not reach the Assamese
 * অভিযোগসমূহ document even though it does reach the Bengali অভিযোগগুলি one. Marathi and Assamese are
 * therefore in the no-stemmer group in practice. {@code SeededCorpusSearchIT} asserts exactly this,
 * including the misses, so the limitation is recorded rather than discovered later in production.
 *
 * <p><b>{@code @Profile("dev-local")}, not a boolean flag.</b> This is invented complaint data about
 * invented citizens. A flag can be switched on in the wrong ConfigMap; the profile cannot be present
 * in {@code prod} or {@code openshift} without someone changing the deployment's active profile, which
 * is a visible act. Synthetic rows in a statutory complaint register are not a recoverable mistake.
 *
 * <p><b>Idempotent.</b> Skipped entirely when the corpus count already matches, and each row is
 * additionally guarded by {@code findByComplaintNumber} so a partially-seeded database converges rather
 * than duplicating or failing on the unique index. Re-running on a seeded database issues one COUNT and
 * stops.
 *
 * <p><b>Deterministic, including the timestamps — and that needed a native UPDATE.</b> Tests assert
 * exact hit counts and date-sorted ordering, so every field has to be fixed text at a fixed instant.
 * {@link Complaint#onCreate()} sets {@code createdAt}/{@code filedAt} to {@code LocalDateTime.now()}
 * unconditionally, overwriting whatever the caller set. That is not a hypothetical: the 60
 * {@code CMS-DEMO-*} rows from {@link DemoDataSeeder} were written with a deliberate 90-day spread and
 * all 60 landed within 130 milliseconds of each other in the live database, which quietly destroys any
 * report that groups them by date. Rather than change a lifecycle callback every other session depends
 * on, this seeder pins its own rows with a native UPDATE after the flush — the same escape hatch
 * {@link ComplaintVersionBackfill} uses for a problem JPA cannot express.
 *
 * <p><b>Reaching Elasticsearch.</b> This seeder writes only to MySQL, the system of record, because
 * indexing belongs to cms-search-service and duplicating it here would mean two writers with two
 * mappings. The rows arrive in the {@code cms-complaints} alias by the existing path:
 * {@code ReindexJob} reads the {@code COMPLAINTS} table directly and bulk-indexes it
 * ({@code POST /cms-search/api/v1/search/reindex}). The Kafka route via
 * {@code ComplaintIndexingListener} does not apply — these rows are inserted, not filed through the
 * intake API, so no {@code complaint.ingested} event is ever produced for them.
 *
 * <p><b>Namespaced loudly, because this database is shared.</b> Every row carries the
 * {@code CMS-MLCORPUS-} complaint-number prefix, {@code SYNTHETIC CORPUS} as the complainant name, and
 * {@code MLCORPUS} as the entity code, so no other session can mistake one for a real complaint and no
 * number can collide with the {@code CMP-*}, {@code CMS-DEMO-*} or {@code CEPC/*} series already
 * present.
 */
@Slf4j
@Component
@Order(45)
@Profile("dev-local")
@RequiredArgsConstructor
public class MultilingualCorpusSeeder implements CommandLineRunner {

    /**
     * Prefix for every seeded complaint number. Checked for collisions against the live database
     * before this was chosen: the existing series are {@code CMP-2026*}, {@code CMS-2026*},
     * {@code CMS-DEMO-*}, {@code CEPC/2026/*} and one {@code N2026*}, none of which can produce this.
     */
    static final String NUMBER_PREFIX = "CMS-MLCORPUS-";

    /** Says what the row is in the field a human reads first. */
    private static final String SYNTHETIC_NAME = "SYNTHETIC CORPUS - multilingual search fixture";

    private static final String SYNTHETIC_ENTITY = "MLCORPUS";

    /**
     * Fixed instant for the whole corpus, one day apart per language so date sorting is stable and
     * reproducible. Deliberately in the past and deliberately not {@code now()}.
     */
    private static final LocalDateTime CORPUS_EPOCH = LocalDateTime.of(2026, 1, 5, 9, 0, 0);

    private final ComplaintRepository complaintRepo;
    private final EntityManager entityManager;

    /**
     * One language's pair of complaints.
     *
     * @param language     ISO code, one of the 13 served languages
     * @param rootSubject  subject of the document holding the ROOT form of the key term
     * @param rootBody     description of the ROOT document
     * @param inflSubject  subject of the document holding the INFLECTED form
     * @param inflBody     description of the INFLECTED document
     * @param rootTerm     the root surface form a test queries with
     * @param inflectedTerm the inflected surface form a test queries with
     */
    record LanguagePair(String language,
                        String rootSubject, String rootBody,
                        String inflSubject, String inflBody,
                        String rootTerm, String inflectedTerm) {
    }

    /**
     * The corpus.
     *
     * <p>Genuine short banking-complaint prose in each script — ATM non-dispense, unauthorised debit,
     * failed UPI transfer, recovery-agent harassment — not transliterated English and not filler. Each
     * pair shares one key noun ("complaint" / "grievance") in root form in the first document and in an
     * inflected form in the second, which is what makes the stemming asymmetry assertable.
     *
     * <p>Every root/inflected term here was run through {@code _analyze} on a live node against this
     * mapping before being committed, so the test's expectations are measurements rather than
     * assumptions about what Lucene does to Indic text.
     */
    static List<LanguagePair> corpus() {
        List<LanguagePair> pairs = new ArrayList<>();

        // en — english analyzer: complaints -> complaint
        pairs.add(new LanguagePair("en",
                "Unauthorised debit from my savings account",
                "A complaint was filed with the bank branch about one unauthorised debit, and the "
                        + "branch manager refused to register it.",
                "Repeated ATM disputes remain unresolved",
                "Two complaints about failed ATM withdrawals were escalated, and the bank closed both "
                        + "without refunding the disputed amounts.",
                "complaint", "complaints"));

        // hi — hindi analyzer: शिकायतों -> शिकायत. The pair the brief's measurement is built on.
        pairs.add(new LanguagePair("hi",
                "एटीएम से पैसे नहीं निकले पर खाते से कट गए",
                "मैंने शाखा प्रबंधक के पास शिकायत दर्ज कराई, लेकिन राशि आज तक वापस नहीं हुई।",
                "यूपीआई से भेजा पैसा लाभार्थी को नहीं मिला",
                "बैंक ने मेरी पिछली शिकायतों पर कोई कार्रवाई नहीं की और खाते से दो बार राशि काट ली गई।",
                "शिकायत", "शिकायतों"));

        // bn — bengali analyzer: অভিযোগগুলি -> অভিযোগ
        pairs.add(new LanguagePair("bn",
                "অনুমতি ছাড়াই আমার সঞ্চয়ী হিসাব থেকে টাকা কাটা হয়েছে",
                "আমি শাখায় একটি অভিযোগ জমা দিয়েছি, কিন্তু ব্যাঙ্ক এখনও টাকা ফেরত দেয়নি।",
                "এটিএম থেকে টাকা না পেলেও হিসাব থেকে কাটা হয়েছে",
                "আমার আগের অভিযোগগুলি ব্যাঙ্ক কোনো কারণ না জানিয়েই বন্ধ করে দিয়েছে।",
                "অভিযোগ", "অভিযোগগুলি"));

        // ur — cms_urdu: arabic_normalization folds kaf/yeh variants; it does NOT stem.
        pairs.add(new LanguagePair("ur",
                "میرے بچت کھاتے سے اجازت کے بغیر رقم کاٹی گئی",
                "میں نے شاخ میں شکایت درج کرائی لیکن بینک نے ابھی تک رقم واپس نہیں کی۔",
                "اے ٹی ایم سے رقم نہیں ملی مگر کھاتے سے کٹ گئی",
                "بینک نے میری پچھلی شکایات پر کوئی کارروائی نہیں کی اور قرض کی وصولی کے لیے بار بار فون کیے۔",
                "شکایت", "شکایات"));

        // mr — Devanagari, so it borrows the .hi subfield, but hindi_stemmer does not stem Marathi.
        pairs.add(new LanguagePair("mr",
                "एटीएममधून रक्कम मिळाली नाही पण खात्यातून वजा झाली",
                "मी शाखा व्यवस्थापकाकडे तक्रार नोंदवली, तरीही रक्कम परत मिळालेली नाही.",
                "कर्ज वसुलीसाठी बँकेच्या प्रतिनिधींनी धमकी दिली",
                "माझ्या आधीच्या तक्रारींवर बँकेने कोणतीही कार्यवाही केली नाही.",
                "तक्रार", "तक्रारींवर"));

        // te — no analyzer, cms_indic only
        pairs.add(new LanguagePair("te",
                "నా పొదుపు ఖాతా నుండి అనుమతి లేకుండా డబ్బు కట్ అయింది",
                "నేను శాఖ మేనేజర్‌కు ఫిర్యాదు ఇచ్చాను, అయినా బ్యాంకు డబ్బు తిరిగి ఇవ్వలేదు.",
                "యూపీఐ చెల్లింపు విఫలమైనా ఖాతా నుండి డబ్బు తీసేశారు",
                "నా పాత ఫిర్యాదులు బ్యాంకు కారణం చెప్పకుండానే మూసివేసింది.",
                "ఫిర్యాదు", "ఫిర్యాదులు"));

        // ta — no analyzer, cms_indic only
        pairs.add(new LanguagePair("ta",
                "எனது சேமிப்புக் கணக்கிலிருந்து அனுமதியின்றி பணம் எடுக்கப்பட்டது",
                "நான் கிளை மேலாளரிடம் புகார் அளித்தேன், ஆனால் வங்கி இன்னும் பணத்தைத் திரும்ப அளிக்கவில்லை.",
                "ஏடிஎம்மில் பணம் வராமலே கணக்கிலிருந்து கழிக்கப்பட்டது",
                "எனது முந்தைய புகார்கள் காரணம் சொல்லாமல் வங்கியால் முடிக்கப்பட்டன.",
                "புகார்", "புகார்கள்"));

        // ml — no analyzer, cms_indic only
        pairs.add(new LanguagePair("ml",
                "എന്റെ സേവിംഗ്സ് അക്കൗണ്ടിൽ നിന്ന് അനുമതിയില്ലാതെ പണം പിൻവലിച്ചു",
                "ഞാൻ ശാഖാ മാനേജർക്ക് പരാതി നൽകി, എന്നാൽ ബാങ്ക് ഇതുവരെ പണം തിരികെ നൽകിയിട്ടില്ല.",
                "എടിഎമ്മിൽ പണം ലഭിച്ചില്ലെങ്കിലും അക്കൗണ്ടിൽ നിന്ന് കുറച്ചു",
                "എന്റെ മുൻ പരാതികൾ കാരണം അറിയിക്കാതെ ബാങ്ക് അവസാനിപ്പിച്ചു.",
                "പരാതി", "പരാതികൾ"));

        // kn — no analyzer, cms_indic only
        pairs.add(new LanguagePair("kn",
                "ನನ್ನ ಉಳಿತಾಯ ಖಾತೆಯಿಂದ ಅನುಮತಿ ಇಲ್ಲದೆ ಹಣ ಕಡಿತವಾಗಿದೆ",
                "ನಾನು ಶಾಖಾ ವ್ಯವಸ್ಥಾಪಕರಿಗೆ ದೂರು ನೀಡಿದೆ, ಆದರೆ ಬ್ಯಾಂಕ್ ಇನ್ನೂ ಹಣ ಹಿಂತಿರುಗಿಸಿಲ್ಲ.",
                "ಯುಪಿಐ ವರ್ಗಾವಣೆ ವಿಫಲವಾದರೂ ಖಾತೆಯಿಂದ ಹಣ ಕಡಿತವಾಯಿತು",
                "ನನ್ನ ಹಿಂದಿನ ದೂರುಗಳನ್ನು ಬ್ಯಾಂಕ್ ಕಾರಣ ತಿಳಿಸದೆ ಮುಚ್ಚಿದೆ.",
                "ದೂರು", "ದೂರುಗಳನ್ನು"));

        // gu — no analyzer, cms_indic only
        pairs.add(new LanguagePair("gu",
                "મારા બચત ખાતામાંથી મંજૂરી વિના રકમ કપાઈ ગઈ",
                "મેં શાખા મેનેજરને ફરિયાદ કરી, પરંતુ બેંકે આજ સુધી રકમ પરત કરી નથી.",
                "એટીએમમાંથી રોકડ મળી નહીં પણ ખાતામાંથી કપાઈ",
                "બેંકે મારી જૂની ફરિયાદોનું કોઈ કારણ આપ્યા વિના નિકાલ કરી દીધો.",
                "ફરિયાદ", "ફરિયાદોનું"));

        // pa — Gurmukhi, no analyzer, cms_indic only
        pairs.add(new LanguagePair("pa",
                "ਮੇਰੇ ਬਚਤ ਖਾਤੇ ਵਿੱਚੋਂ ਮਨਜ਼ੂਰੀ ਤੋਂ ਬਿਨਾਂ ਰਕਮ ਕੱਟੀ ਗਈ",
                "ਮੈਂ ਸ਼ਾਖਾ ਮੈਨੇਜਰ ਕੋਲ ਸ਼ਿਕਾਇਤ ਦਰਜ ਕਰਵਾਈ, ਪਰ ਬੈਂਕ ਨੇ ਹੁਣ ਤੱਕ ਰਕਮ ਵਾਪਸ ਨਹੀਂ ਕੀਤੀ।",
                "ਕਰਜ਼ੇ ਦੀ ਵਸੂਲੀ ਲਈ ਏਜੰਟਾਂ ਨੇ ਧਮਕੀਆਂ ਦਿੱਤੀਆਂ",
                "ਬੈਂਕ ਨੇ ਮੇਰੀਆਂ ਪਿਛਲੀਆਂ ਸ਼ਿਕਾਇਤਾਂ ਉੱਤੇ ਕੋਈ ਕਾਰਵਾਈ ਨਹੀਂ ਕੀਤੀ।",
                "ਸ਼ਿਕਾਇਤ", "ਸ਼ਿਕਾਇਤਾਂ"));

        // or — no analyzer, cms_indic only
        pairs.add(new LanguagePair("or",
                "ମୋର ସଞ୍ଚୟ ଖାତାରୁ ଅନୁମତି ବିନା ଟଙ୍କା କଟିଯାଇଛି",
                "ମୁଁ ଶାଖା ପରିଚାଳକ ନିକଟରେ ଅଭିଯୋଗ ଦାଖଲ କରିଥିଲି, କିନ୍ତୁ ବ୍ୟାଙ୍କ ଏପର୍ଯ୍ୟନ୍ତ ଟଙ୍କା ଫେରସ୍ତ କରିନାହିଁ।",
                "ଏଟିଏମରୁ ଟଙ୍କା ମିଳିନାହିଁ ତଥାପି ଖାତାରୁ କଟିଛି",
                "ବ୍ୟାଙ୍କ ମୋର ପୂର୍ବ ଅଭିଯୋଗଗୁଡ଼ିକ କାରଣ ନ ଜଣାଇ ବନ୍ଦ କରିଦେଇଛି।",
                "ଅଭିଯୋଗ", "ଅଭିଯୋଗଗୁଡ଼ିକ"));

        // as — Bengali script, so it borrows the .bn subfield, but bengali_stemmer does not reduce
        // the Assamese plural সমূহ the way it reduces the Bengali গুলি.
        pairs.add(new LanguagePair("as",
                "মোৰ সঞ্চয় হিচাপৰ পৰা অনুমতি নোহোৱাকৈ টকা কাটি লোৱা হ'ল",
                "মই শাখা পৰিচালকৰ আগত অভিযোগ দাখিল কৰিছিলোঁ, কিন্তু বেংকে এতিয়াও টকা ফিৰাই দিয়া নাই।",
                "এটিএমৰ পৰা টকা নোপোৱাকৈয়ো হিচাপৰ পৰা কাটি লোৱা হ'ল",
                "বেংকে মোৰ আগৰ অভিযোগসমূহ কাৰণ নজনাই বন্ধ কৰি দিছে।",
                "অভিযোগ", "অভিযোগসমূহ"));

        return pairs;
    }

    /** {@code CMS-MLCORPUS-HI-ROOT} and friends: the stable id a test can hardcode. */
    static String complaintNumber(String language, boolean root) {
        return NUMBER_PREFIX + language.toUpperCase() + (root ? "-ROOT" : "-INFL");
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<LanguagePair> pairs = corpus();
        int expected = pairs.size() * 2;

        long existing = countCorpusRows();
        if (existing >= expected) {
            log.info("Multilingual search corpus already present ({} rows under '{}'), skipping",
                    existing, NUMBER_PREFIX);
            return;
        }

        log.info("Seeding multilingual search corpus: {} languages x 2 documents (found {} of {})",
                pairs.size(), existing, expected);

        int inserted = 0;
        for (int i = 0; i < pairs.size(); i++) {
            LanguagePair pair = pairs.get(i);
            // One day apart per language, root before inflected, so createdAt ordering is total and
            // reproducible across machines.
            LocalDateTime rootAt = CORPUS_EPOCH.plusDays(i);
            LocalDateTime inflAt = rootAt.plusHours(1);

            inserted += insertIfAbsent(complaintNumber(pair.language(), true),
                    pair.rootSubject(), pair.rootBody(), rootAt);
            inserted += insertIfAbsent(complaintNumber(pair.language(), false),
                    pair.inflSubject(), pair.inflBody(), inflAt);
        }

        if (inserted == 0) {
            return;
        }

        // Must happen after the inserts are flushed, because the UPDATE is native SQL and would
        // otherwise run against rows the persistence context has not written yet.
        entityManager.flush();
        int pinned = pinTimestamps(pairs);

        log.info("Multilingual search corpus seeded: {} row(s) inserted, {} timestamp(s) pinned. "
                        + "Run POST /cms-search/api/v1/search/reindex to make them searchable.",
                inserted, pinned);
    }

    private long countCorpusRows() {
        return ((Number) entityManager
                // Lowercase and unquoted, matching ComplaintVersionBackfill: Linux MySQL defaults to
                // lower_case_table_names=0, so the uppercase literal matches no table there even though
                // @Table(name = "COMPLAINTS") does, via Boot's CamelCaseToUnderscoresNamingStrategy.
                .createNativeQuery("SELECT COUNT(*) FROM complaints WHERE complaint_number LIKE :prefix")
                .setParameter("prefix", NUMBER_PREFIX + "%")
                .getSingleResult()).longValue();
    }

    /**
     * @return 1 if a row was written, 0 if one already existed
     */
    private int insertIfAbsent(String number, String subject, String description, LocalDateTime at) {
        // Per-row guard as well as the bulk count: a run interrupted halfway leaves some of the 26
        // present, and the unique index on complaint_number would make a blind re-insert throw rather
        // than converge.
        if (complaintRepo.findByComplaintNumber(number).isPresent()) {
            return 0;
        }

        Complaint c = new Complaint();
        c.setComplaintNumber(number);
        c.setComplainantName(SYNTHETIC_NAME);
        c.setSubject(subject);
        c.setDescription(description);
        c.setStatus("closed");
        c.setPriority("medium");
        c.setDepartment("RBIO");
        c.setEntityCode(SYNTHETIC_ENTITY);
        c.setCategoryId(1L);
        // Set here too, so a reader of the entity in the same transaction sees the intended value even
        // though onCreate() is about to overwrite the persisted column.
        c.setCreatedAt(at);
        c.setFiledAt(at);

        complaintRepo.save(c);
        return 1;
    }

    /**
     * Forces the fixed instants past {@link Complaint#onCreate()}.
     *
     * <p>Native SQL because the problem is the JPA lifecycle itself: any entity write re-triggers
     * {@code @PrePersist}/{@code @PreUpdate} and loses the value again.
     *
     * @return number of rows whose timestamps were corrected
     */
    private int pinTimestamps(List<LanguagePair> pairs) {
        int pinned = 0;
        for (int i = 0; i < pairs.size(); i++) {
            LanguagePair pair = pairs.get(i);
            LocalDateTime rootAt = CORPUS_EPOCH.plusDays(i);
            pinned += pinOne(complaintNumber(pair.language(), true), rootAt);
            pinned += pinOne(complaintNumber(pair.language(), false), rootAt.plusHours(1));
        }
        return pinned;
    }

    private int pinOne(String number, LocalDateTime at) {
        return entityManager.createNativeQuery(
                        "UPDATE complaints SET created_at = :at, filed_at = :at, updated_at = :at "
                                + "WHERE complaint_number = :number")
                .setParameter("at", at)
                .setParameter("number", number)
                .executeUpdate();
    }
}
