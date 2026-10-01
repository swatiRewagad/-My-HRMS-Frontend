package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Citizen-facing labels for the ten grievance categories in COMPLAINT_CATEGORIES.
 *
 * <p>The category list is the FIRST substantive choice a complainant makes, and it was English-only in
 * an eleven-locale product: the table has a {@code name} column and nothing else, so a Tamil or
 * Gurmukhi speaker filing a complaint chose from English strings. These keys localise the label while
 * {@code name} — the value actually submitted, stored and matched by routing — stays English.
 *
 * <p>The nine non-English translations are NOT new prose. They are the same strings already seeded for
 * {@code aa.ground.*}, which names these identical ten categories as the grounds of an appeal. Reusing
 * them rather than retranslating keeps one vocabulary: a category must not be worded one way when a
 * citizen files and another way when they appeal against the outcome.
 *
 * <p>Punjabi is seeded HERE for the first time. {@code aa.ground.*} covers nine locales and predates
 * {@code pa} being served at all, so it has no {@code pa} row; without these the newest locale would
 * fall back to English on the category list specifically.
 *
 * <p>Insert-if-absent, so re-running is a no-op — with the usual corollary: correcting a string here
 * does NOT rewrite a row already committed to a database. A text correction needs a code-scoped UPDATE
 * in both migration directories.
 */
@Component
@Order(63)
@RequiredArgsConstructor
@Slf4j
public class ComplaintCategoryTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "complaint-category";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    @Override
    @Transactional
    public void run(String... args) {
        english().forEach(this::seed);
        seedLocale("hi", hindi());
        seedLocale("bn", bengali());
        seedLocale("mr", marathi());
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
        m.put("category.atm_debit_card", "ATM / Debit Card");
        m.put("category.credit_card", "Credit Card");
        m.put("category.internet_banking", "Internet Banking");
        m.put("category.mobile_banking_upi", "Mobile Banking / UPI");
        m.put("category.loan_advances", "Loan / Advances");
        m.put("category.deposit_accounts", "Deposit Accounts");
        m.put("category.pension", "Pension");
        m.put("category.remittance_transfer", "Remittance / Transfer");
        m.put("category.insurance", "Insurance");
        m.put("category.others", "Others");
        return m;
    }

    private Map<String, String> hindi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "एटीएम / डेबिट कार्ड");
        m.put("category.credit_card", "क्रेडिट कार्ड");
        m.put("category.internet_banking", "इंटरनेट बैंकिंग");
        m.put("category.mobile_banking_upi", "मोबाइल बैंकिंग / यूपीआई");
        m.put("category.loan_advances", "ऋण / अग्रिम");
        m.put("category.deposit_accounts", "जमा खाते");
        m.put("category.pension", "पेंशन");
        m.put("category.remittance_transfer", "प्रेषण / अंतरण");
        m.put("category.insurance", "बीमा");
        m.put("category.others", "अन्य");
        return m;
    }

    private Map<String, String> bengali() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "এটিএম / ডেবিট কার্ড");
        m.put("category.credit_card", "ক্রেডিট কার্ড");
        m.put("category.internet_banking", "ইন্টারনেট ব্যাঙ্কিং");
        m.put("category.mobile_banking_upi", "মোবাইল ব্যাঙ্কিং / ইউপিআই");
        m.put("category.loan_advances", "ঋণ / অগ্রিম");
        m.put("category.deposit_accounts", "আমানত হিসাব");
        m.put("category.pension", "পেনশন");
        m.put("category.remittance_transfer", "প্রেরণ / স্থানান্তর");
        m.put("category.insurance", "বিমা");
        m.put("category.others", "অন্যান্য");
        return m;
    }

    private Map<String, String> marathi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "एटीएम / डेबिट कार्ड");
        m.put("category.credit_card", "क्रेडिट कार्ड");
        m.put("category.internet_banking", "इंटरनेट बँकिंग");
        m.put("category.mobile_banking_upi", "मोबाइल बँकिंग / यूपीआय");
        m.put("category.loan_advances", "कर्ज / अग्रिम");
        m.put("category.deposit_accounts", "ठेव खाती");
        m.put("category.pension", "निवृत्तिवेतन");
        m.put("category.remittance_transfer", "पैसे पाठवणे / हस्तांतरण");
        m.put("category.insurance", "विमा");
        m.put("category.others", "इतर");
        return m;
    }

    private Map<String, String> telugu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "ఏటీఎం / డెబిట్ కార్డ్");
        m.put("category.credit_card", "క్రెడిట్ కార్డ్");
        m.put("category.internet_banking", "ఇంటర్నెట్ బ్యాంకింగ్");
        m.put("category.mobile_banking_upi", "మొబైల్ బ్యాంకింగ్ / యూపీఐ");
        m.put("category.loan_advances", "రుణం / అడ్వాన్సులు");
        m.put("category.deposit_accounts", "డిపాజిట్ ఖాతాలు");
        m.put("category.pension", "పెన్షన్");
        m.put("category.remittance_transfer", "చెల్లింపు / బదిలీ");
        m.put("category.insurance", "బీమా");
        m.put("category.others", "ఇతరాలు");
        return m;
    }

    private Map<String, String> tamil() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "ஏடிஎம் / டெபிட் அட்டை");
        m.put("category.credit_card", "கடன் அட்டை");
        m.put("category.internet_banking", "இணைய வங்கி சேவை");
        m.put("category.mobile_banking_upi", "கைபேசி வங்கி சேவை / யூபிஐ");
        m.put("category.loan_advances", "கடன் / முன்பணம்");
        m.put("category.deposit_accounts", "வைப்பு கணக்குகள்");
        m.put("category.pension", "ஓய்வூதியம்");
        m.put("category.remittance_transfer", "பணப் பரிமாற்றம்");
        m.put("category.insurance", "காப்பீடு");
        m.put("category.others", "மற்றவை");
        return m;
    }

    private Map<String, String> gujarati() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "એટીએમ / ડેબિટ કાર્ડ");
        m.put("category.credit_card", "ક્રેડિટ કાર્ડ");
        m.put("category.internet_banking", "ઇન્ટરનેટ બેન્કિંગ");
        m.put("category.mobile_banking_upi", "મોબાઇલ બેન્કિંગ / યુપીઆઈ");
        m.put("category.loan_advances", "લોન / એડવાન્સ");
        m.put("category.deposit_accounts", "ડિપોઝિટ ખાતાં");
        m.put("category.pension", "પેન્શન");
        m.put("category.remittance_transfer", "રકમ મોકલવી / તબદીલી");
        m.put("category.insurance", "વીમો");
        m.put("category.others", "અન્ય");
        return m;
    }

    private Map<String, String> urdu() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "اے ٹی ایم / ڈیبٹ کارڈ");
        m.put("category.credit_card", "کریڈٹ کارڈ");
        m.put("category.internet_banking", "انٹرنیٹ بینکنگ");
        m.put("category.mobile_banking_upi", "موبائل بینکنگ / یو پی آئی");
        m.put("category.loan_advances", "قرض / پیشگی رقم");
        m.put("category.deposit_accounts", "ڈپازٹ اکاؤنٹس");
        m.put("category.pension", "پنشن");
        m.put("category.remittance_transfer", "رقم کی منتقلی");
        m.put("category.insurance", "بیمہ");
        m.put("category.others", "دیگر");
        return m;
    }

    private Map<String, String> kannada() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "ಎಟಿಎಂ / ಡೆಬಿಟ್ ಕಾರ್ಡ್");
        m.put("category.credit_card", "ಕ್ರೆಡಿಟ್ ಕಾರ್ಡ್");
        m.put("category.internet_banking", "ಇಂಟರ್ನೆಟ್ ಬ್ಯಾಂಕಿಂಗ್");
        m.put("category.mobile_banking_upi", "ಮೊಬೈಲ್ ಬ್ಯಾಂಕಿಂಗ್ / ಯುಪಿಐ");
        m.put("category.loan_advances", "ಸಾಲ / ಮುಂಗಡ");
        m.put("category.deposit_accounts", "ಠೇವಣಿ ಖಾತೆಗಳು");
        m.put("category.pension", "ಪಿಂಚಣಿ");
        m.put("category.remittance_transfer", "ಹಣ ರವಾನೆ / ವರ್ಗಾವಣೆ");
        m.put("category.insurance", "ವಿಮೆ");
        m.put("category.others", "ಇತರೆ");
        return m;
    }

    private Map<String, String> malayalam() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "എടിഎം / ഡെബിറ്റ് കാർഡ്");
        m.put("category.credit_card", "ക്രെഡിറ്റ് കാർഡ്");
        m.put("category.internet_banking", "ഇന്റർനെറ്റ് ബാങ്കിംഗ്");
        m.put("category.mobile_banking_upi", "മൊബൈൽ ബാങ്കിംഗ് / യുപിഐ");
        m.put("category.loan_advances", "വായ്പ / അഡ്വാൻസ്");
        m.put("category.deposit_accounts", "നിക്ഷേപ അക്കൗണ്ടുകൾ");
        m.put("category.pension", "പെൻഷൻ");
        m.put("category.remittance_transfer", "പണമടയ്ക്കൽ / കൈമാറ്റം");
        m.put("category.insurance", "ഇൻഷുറൻസ്");
        m.put("category.others", "മറ്റുള്ളവ");
        return m;
    }

    private Map<String, String> punjabi() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("category.atm_debit_card", "ਏਟੀਐਮ / ਡੈਬਿਟ ਕਾਰਡ");
        m.put("category.credit_card", "ਕ੍ਰੈਡਿਟ ਕਾਰਡ");
        m.put("category.internet_banking", "ਇੰਟਰਨੈੱਟ ਬੈਂਕਿੰਗ");
        m.put("category.mobile_banking_upi", "ਮੋਬਾਈਲ ਬੈਂਕਿੰਗ / ਯੂਪੀਆਈ");
        m.put("category.loan_advances", "ਕਰਜ਼ਾ / ਪੇਸ਼ਗੀ");
        m.put("category.deposit_accounts", "ਜਮ੍ਹਾਂ ਖਾਤੇ");
        m.put("category.pension", "ਪੈਨਸ਼ਨ");
        m.put("category.remittance_transfer", "ਰਕਮ ਭੇਜਣਾ / ਤਬਾਦਲਾ");
        m.put("category.insurance", "ਬੀਮਾ");
        m.put("category.others", "ਹੋਰ");
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
