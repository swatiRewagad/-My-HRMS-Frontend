package com.hrms.cms.config;

import com.hrms.cms.entity.AcknowledgementLetterTemplate;
import com.hrms.cms.repository.AcknowledgementLetterTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the RBIO and CEPC acknowledgement letter bodies (English + Hindi) transcribed from the
 * approved letter templates.
 *
 * <p>Insert-if-absent, so correcting wording here does NOT touch rows already in the database —
 * edit the stored row directly (via {@code /api/v1/acknowledgement-templates}) or bump
 * {@code version} on a replacement row instead. That is the entire point of storing this in the
 * database rather than in code: a wording fix is a data change, not a deploy.
 *
 * <p>Markup inside {@code bodyTemplate}: a line starting {@code ">> "} is centered+bold (the
 * letterhead title), a line starting {@code "## "} is bold in place (the subject line), a blank
 * line is a paragraph gap. See {@code ComplaintLetterPdfService.parseLines}.
 *
 * <p>Hindi wording was transcribed from the source letter images rather than typed by a Hindi
 * speaker — proofread it against the original before relying on it for a real citizen mailing.
 */
@Component
@Order(9)
public class AcknowledgementLetterTemplateSeeder implements CommandLineRunner {

    private final AcknowledgementLetterTemplateRepository repo;

    public AcknowledgementLetterTemplateSeeder(AcknowledgementLetterTemplateRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seed("RBIO", "EN", RBIO_EN);
        seed("RBIO", "HI", RBIO_HI);
        seed("CEPC", "EN", CEPC_EN);
        seed("CEPC", "HI", CEPC_HI);
    }

    private void seed(String department, String language, String body) {
        if (repo.existsByDepartmentAndLanguage(department, language)) return;
        repo.save(AcknowledgementLetterTemplate.builder()
                .department(department)
                .language(language)
                .bodyTemplate(body)
                .version(1)
                .active(true)
                .build());
    }

    private static final String RBIO_EN = """
            >> The Office of RBI Ombudsman
            >> Reserve Bank of India

            RBI/CMS/{{complaintRefNo}}/{{currentFY}}                                   Date: {{currentDate}}

            {{complainantName}}
            {{addressLine1}}
            {{stateDistrict}}
            {{pincode}}
            {{mobileNumber}}
            {{emailId}}

            Madam/Dear Sir(s),

            ## Acknowledgement: Registration of Complaint - {{complaintRefNo}} against {{entityName}} regarding {{complaintCategory}}

            Please refer to your complaint against {{entityName}} filed through the CMS {{modeOfReceipt}} with the Ombudsman. We acknowledge the receipt of your complaint which has been registered with us with a complaint no {{complaintRefNo}}.

            2. The maintainability or otherwise of your complaint under the Reserve Bank - Integrated Ombudsman Scheme, 2026 will be examined and the action taken would be advised to you in due course.

            3. Please do not write to crpc@rbi.org.in for tracking the status of your complaint. However, you may track the status of your complaint on https://cms.rbi.org.in by quoting your complaint number {{complaintRefNo}} and registered mobile number.

            4. Should you require any further information or clarification, please feel free to contact our staff on Toll-Free Number: 14448 (Monday to Saturday except National Holidays, between 8:00 AM - 10:00 PM).

            5. Please do not reply to this email. Replies to this email cannot be responded to by us.

            Regards
            The Office of RBI Ombudsman
            Reserve Bank of India
            """;

    private static final String RBIO_HI = """
            >> आरबीआई लोकपाल कार्यालय
            >> भारतीय रिज़र्व बैंक

            आरबीआई/सीएमएस/{{complaintRefNo}}/{{currentFY}}                              दिनांक: {{currentDate}}

            {{complainantName}}
            {{addressLine1}}
            {{stateDistrict}}
            {{pincode}}
            {{mobileNumber}}
            {{emailId}}

            महोदय/महोदया,

            ## अभिस्वीकृति: शिकायत का पंजीकरण - {{complaintRefNo}} {{entityName}} के विरुद्ध {{complaintCategory}} के संबंध में

            कृपया {{entityName}} के विरुद्ध सीएमएस {{modeOfReceipt}} के माध्यम से लोकपाल के पास दायर अपनी शिकायत का संदर्भ लें। हम आपकी शिकायत प्राप्ति की सूचना देते हैं जो हमारे पास शिकायत सं. {{complaintRefNo}} के साथ पंजीकृत की गई है।

            2. रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2026 के अंतर्गत आपकी शिकायत की पोषणीयता की जांच की जाएगी और की गई कार्रवाई के विषय में आपको समय पर सूचित किया जाएगा।

            3. कृपया अपनी शिकायत की स्थिति को ट्रैक करने के लिए crpc@rbi.org.in पर न लिखें। तथापि, आप अपनी शिकायत संख्या {{complaintRefNo}} और पंजीकृत मोबाइल नंबर के माध्यम से https://cms.rbi.org.in पर अपनी शिकायत की स्थिति को ट्रैक कर सकते हैं।

            4. यदि आपको किसी अतिरिक्त जानकारी या स्पष्टीकरण की आवश्यकता हो तो कृपया हमारे स्टाफ से टोल-फ्री नंबर: 14448 (सोमवार से शनिवार, राष्ट्रीय अवकाशों को छोड़कर, प्रातः 8:00 बजे से रात्रि 10:00 बजे तक) पर संपर्क करें।

            5. कृपया इस ईमेल का जवाब न दें। इस ईमेल के जवाबों का हमारे द्वारा जवाब नहीं दिया जा सकता।

            सादर
            आरबीआई लोकपाल कार्यालय
            भारतीय रिज़र्व बैंक
            """;

    private static final String CEPC_EN = """
            >> Consumer Education and Protection Cell, {{cepcName}}
            >> Reserve Bank of India

            RBI/CMS/{{complaintRefNo}}/{{currentFY}}                                   Date: {{currentDate}}

            {{complainantName}}
            {{addressLine1}}
            {{stateDistrict}}
            {{pincode}}
            {{mobileNumber}}
            {{emailId}}

            Madam/Dear Sir(s),

            ## Acknowledgement: Registration of Complaint - {{complaintRefNo}} against {{entityName}} regarding {{complaintCategory}}

            Please refer to your complaint against {{entityName}} filed through the CMS {{modeOfReceipt}} with the Reserve Bank of India. We acknowledge the receipt of your complaint which has been registered with us with a complaint no {{complaintRefNo}}.

            2. As {{entityName}} is currently not covered under the Reserve Bank - Integrated Ombudsman Scheme, 2026, your complaint against this entity will be examined and the action taken thereon would be advised to you in due course.

            3. Please do not write to crpc@rbi.org.in for tracking the status of your complaint. However, you may track the status of your complaint on https://cms.rbi.org.in by quoting your complaint number {{complaintRefNo}} and registered mobile number.

            4. Should you require any further information or clarification, please feel free to contact our staff on Toll-Free Number: 14448 (Monday to Saturday except National Holidays, between 8:00 AM - 10:00 PM).

            Please do not reply to this email. Replies to this email cannot be responded to by us.

            Regards
            Consumer Education and Protection Cell
            Reserve Bank of India
            """;

    private static final String CEPC_HI = """
            >> उपभोक्ता शिक्षा और संरक्षण कक्ष, {{cepcName}}
            >> भारतीय रिज़र्व बैंक

            आरबीआई/सीएमएस/{{complaintRefNo}}/{{currentFY}}                              दिनांक: {{currentDate}}

            {{complainantName}}
            {{addressLine1}}
            {{stateDistrict}}
            {{pincode}}
            {{mobileNumber}}
            {{emailId}}

            महोदय/महोदया,

            ## अभिस्वीकृति: शिकायत का पंजीकरण - {{complaintRefNo}} {{entityName}} के विरुद्ध {{complaintCategory}} के संबंध में

            कृपया {{entityName}} के विरुद्ध सीएमएस {{modeOfReceipt}} के माध्यम से भारतीय रिज़र्व बैंक के पास दायर अपनी शिकायत का संदर्भ लें। हम आपकी शिकायत प्राप्ति की सूचना देते हैं जो हमारे पास शिकायत सं. {{complaintRefNo}} के साथ पंजीकृत की गई है।

            2. चूंकि {{entityName}} वर्तमान में रिज़र्व बैंक - एकीकृत लोकपाल योजना, 2026 के अंतर्गत कवर नहीं है, इस संस्था के विरुद्ध आपकी शिकायत की जांच की जाएगी और की गई कार्रवाई के विषय में आपको समय पर सूचित किया जाएगा।

            3. कृपया अपनी शिकायत की स्थिति को ट्रैक करने के लिए crpc@rbi.org.in पर न लिखें। तथापि, आप अपनी शिकायत संख्या {{complaintRefNo}} और पंजीकृत मोबाइल नंबर के माध्यम से https://cms.rbi.org.in पर अपनी शिकायत की स्थिति को ट्रैक कर सकते हैं।

            4. यदि आपको किसी अतिरिक्त जानकारी या स्पष्टीकरण की आवश्यकता हो तो कृपया हमारे स्टाफ से टोल-फ्री नंबर: 14448 (सोमवार से शनिवार, राष्ट्रीय अवकाशों को छोड़कर, प्रातः 8:00 बजे से रात्रि 10:00 बजे तक) पर संपर्क करें।

            कृपया इस ईमेल का जवाब न दें। इस ईमेल के जवाबों का हमारे द्वारा जवाब नहीं दिया जा सकता।

            सादर
            उपभोक्ता शिक्षा और संरक्षण कक्ष
            भारतीय रिज़र्व बैंक
            """;
}
