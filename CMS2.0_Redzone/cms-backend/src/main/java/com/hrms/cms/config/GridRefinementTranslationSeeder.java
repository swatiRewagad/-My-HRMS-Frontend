package com.hrms.cms.config;

import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Footer wording for a SERVER-PAGED task grid whose column filters narrow only the current page.
 *
 * <p>WHY A SEPARATE KEY FROM {@code ui.common.showing_count}. When the server pages, the grid holds one
 * page and its column filters refine just that page. Reporting "Showing 1 to 10 of 4231" while a filter
 * is active would state a range of a total the filter never touched — a confidently wrong number of
 * exactly the kind this screen was full of. This key says plainly that the count is a filter of the
 * loaded page, so the user is not misled about how much of the queue they are looking at.
 *
 * <p>A separate seeder at {@code @Order(64)} rather than an edit to TaskGridTranslationSeeder (62),
 * per the convention here: concurrent sessions add keys and editing a shared file is a guaranteed
 * conflict, where a conflict silently costs a locale. 60-63 were verified taken before choosing 64.
 *
 * <p>Insert-if-absent by key code, so re-running is a no-op — with the corollary that correcting this
 * text later needs a code-scoped UPDATE migration in both dialects, not an edit here.
 */
@Component
@Order(64)
@RequiredArgsConstructor
public class GridRefinementTranslationSeeder implements CommandLineRunner {

    private static final String MODULE = "ui";
    private static final String KEY = "ui.grid.refined_count";

    private final TranslationKeyRepository keyRepo;
    private final TranslationRepository translationRepo;

    @Override
    @Transactional
    public void run(String... args) {
        seed(KEY, "Showing {{shown}} filtered from this page of {{total}} total");

        Map<String, String> byLocale = new LinkedHashMap<>();
        byLocale.put("hi", "इस पृष्ठ से {{shown}} दिखाए जा रहे हैं, कुल {{total}} में से");
        byLocale.put("mr", "या पृष्ठावरून {{shown}} दर्शविले जात आहेत, एकूण {{total}} पैकी");
        byLocale.put("bn", "এই পৃষ্ঠা থেকে {{shown}} দেখানো হচ্ছে, মোট {{total}} এর মধ্যে");
        byLocale.put("te", "ఈ పేజీ నుండి {{shown}} చూపబడుతున్నాయి, మొత్తం {{total}} లో");
        byLocale.put("ta", "இந்தப் பக்கத்திலிருந்து {{shown}} காட்டப்படுகிறது, மொத்தம் {{total}} இல்");
        byLocale.put("gu", "આ પૃષ્ઠમાંથી {{shown}} બતાવવામાં આવી રહ્યા છે, કુલ {{total}} માંથી");
        byLocale.put("ur", "اس صفحے سے {{shown}} دکھائے جا رہے ہیں، کل {{total}} میں سے");
        byLocale.put("kn", "ಈ ಪುಟದಿಂದ {{shown}} ತೋರಿಸಲಾಗುತ್ತಿದೆ, ಒಟ್ಟು {{total}} ರಲ್ಲಿ");
        byLocale.put("ml", "ഈ പേജിൽ നിന്ന് {{shown}} കാണിക്കുന്നു, ആകെ {{total}} ൽ");
        byLocale.put("pa", "ਇਸ ਪੰਨੇ ਤੋਂ {{shown}} ਦਿਖਾਏ ਜਾ ਰਹੇ ਹਨ, ਕੁੱਲ {{total}} ਵਿੱਚੋਂ");

        byLocale.forEach(this::seedLocale);
    }

    private void seed(String code, String defaultValue) {
        if (keyRepo.existsByCode(code)) return;
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(MODULE);
        key.setDefaultValue(defaultValue);
        keyRepo.save(key);
    }

    private void seedLocale(String locale, String value) {
        keyRepo.findByCode(KEY).ifPresent(key -> {
            if (!translationRepo.existsByTranslationKeyAndLocale(key, locale)) {
                Translation t = new Translation();
                t.setTranslationKey(key);
                t.setLocale(locale);
                t.setValue(value);
                translationRepo.save(t);
            }
        });
    }
}
