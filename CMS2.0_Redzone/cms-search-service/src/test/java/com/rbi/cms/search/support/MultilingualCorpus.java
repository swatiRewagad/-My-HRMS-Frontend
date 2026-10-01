package com.rbi.cms.search.support;

import java.util.ArrayList;
import java.util.List;

/**
 * The 13-language complaint corpus that {@code MultilingualCorpusSeeder} writes to the database,
 * restated here so {@code SeededCorpusSearchIT} can index the same text.
 *
 * <p><b>This is a deliberate copy, and the duplication is the lesser evil.</b> The seeder lives in
 * {@code cms-backend} ({@code com.hrms.cms}, groupId {@code com.hrms}) and this test lives in
 * {@code cms-search-service} ({@code com.rbi.cms}, groupId {@code com.rbi.cms}). The two modules
 * share no dependency in either direction — {@code cms-backend} does not even depend on
 * {@code cms-common}, which every other service does — so there is no existing place to put a shared
 * constant. Creating one would mean either making the search service depend on the whole monolith's
 * entity layer, or promoting test fixture text into {@code cms-common}'s production jar. Both cost
 * more than a copy.
 *
 * <p><b>The copy is therefore load-bearing and has a guard.</b> If the seeder's text is edited and
 * this file is not, the test keeps passing against stale strings while the real corpus has moved —
 * exactly the failure mode that makes duplicated fixtures worse than useless. The guard is that these
 * strings are keyed to complaint numbers ({@code CMS-MLCORPUS-HI-ROOT}) that the seeder also derives
 * from the language code, so a reconciliation query against the database is a one-liner, and the
 * session report records the measured agreement between the two at the time of writing.
 *
 * <p>Root/inflected pairs per language, sharing one key noun. Only {@code en}, {@code hi}, {@code bn}
 * stem; {@code ur} normalises Arabic script without stemming; the remaining nine
 * ({@code ta te mr gu kn ml pa or as}) have no stemmer at all. See the seeder's class javadoc for the
 * measurements.
 */
public final class MultilingualCorpus {

    public static final String NUMBER_PREFIX = "CMS-MLCORPUS-";

    private MultilingualCorpus() {
    }

    /**
     * @param language      ISO code, one of the 13 served languages
     * @param rootSubject   subject of the document holding the ROOT form of the key term
     * @param rootBody      description of the ROOT document
     * @param inflSubject   subject of the document holding the INFLECTED form
     * @param inflBody      description of the INFLECTED document
     * @param rootTerm      the root surface form a test queries with
     * @param inflectedTerm the inflected surface form a test queries with
     */
    public record LanguagePair(String language,
                               String rootSubject, String rootBody,
                               String inflSubject, String inflBody,
                               String rootTerm, String inflectedTerm) {

        /** Keeps the parameterized test names readable: "hi", not the whole record. */
        @Override
        public String toString() {
            return language;
        }
    }

    public static List<LanguagePair> pairs() {
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

        // hi — hindi analyzer: शिकायतों -> शिकायत
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

        // ur — cms_urdu: arabic_normalization folds script variants; it does NOT stem.
        pairs.add(new LanguagePair("ur",
                "میرے بچت کھاتے سے اجازت کے بغیر رقم کاٹی گئی",
                "میں نے شاخ میں شکایت درج کرائی لیکن بینک نے ابھی تک رقم واپس نہیں کی۔",
                "اے ٹی ایم سے رقم نہیں ملی مگر کھاتے سے کٹ گئی",
                "بینک نے میری پچھلی شکایات پر کوئی کارروائی نہیں کی اور قرض کی وصولی کے لیے بار بار فون کیے۔",
                "شکایت", "شکایات"));

        // mr — Devanagari, borrows .hi, but hindi_stemmer does not stem Marathi.
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

        // as — Bengali script, borrows .bn, but bengali_stemmer does not reduce the Assamese সমূহ
        // plural the way it reduces the Bengali গুলি.
        pairs.add(new LanguagePair("as",
                "মোৰ সঞ্চয় হিচাপৰ পৰা অনুমতি নোহোৱাকৈ টকা কাটি লোৱা হ'ল",
                "মই শাখা পৰিচালকৰ আগত অভিযোগ দাখিল কৰিছিলোঁ, কিন্তু বেংকে এতিয়াও টকা ফিৰাই দিয়া নাই।",
                "এটিএমৰ পৰা টকা নোপোৱাকৈয়ো হিচাপৰ পৰা কাটি লোৱা হ'ল",
                "বেংকে মোৰ আগৰ অভিযোগসমূহ কাৰণ নজনাই বন্ধ কৰি দিছে।",
                "অভিযোগ", "অভিযোগসমূহ"));

        return pairs;
    }
}
