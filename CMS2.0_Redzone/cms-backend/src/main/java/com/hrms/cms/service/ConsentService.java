package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.CitizenConsent;
import com.hrms.cms.repository.CitizenConsentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    public static final String PURPOSE_COMPLAINT_REGISTRATION = "COMPLAINT_REGISTRATION";

    /** Key of the notice shown on the login screen; the same text is snapshotted onto the record. */
    public static final String NOTICE_KEY = "consent.dpdp_notice";

    private static final int NOTICE_MAX_LENGTH = 2000;

    private final CitizenConsentRepository consentRepository;
    private final TranslationService translationService;
    private final AuthSecurityProperties authProps;

    /**
     * UST5: records the consent the citizen gave, snapshotting the notice actually served for their
     * locale. Returns the persisted record so callers can log or echo the version.
     */
    @Transactional
    public CitizenConsent recordConsent(String mobileNumber, String locale, String clientIp, String userAgent) {
        String resolvedLocale = locale == null || locale.isBlank() ? "en" : locale;
        String notice = translationService.getTranslationsForLocale(resolvedLocale)
                .getOrDefault(NOTICE_KEY, NOTICE_KEY);

        CitizenConsent consent = CitizenConsent.builder()
                .mobileNumber(mobileNumber)
                .purpose(PURPOSE_COMPLAINT_REGISTRATION)
                .consentVersion(authProps.getConsent().getVersion())
                .locale(resolvedLocale)
                .noticeText(truncate(notice))
                .clientIp(clientIp)
                .userAgent(truncate(userAgent, 500))
                .build();

        consentRepository.save(consent);
        log.info("DPDP consent {} recorded for mobile ****{}",
                consent.getConsentVersion(), mobileNumber.substring(mobileNumber.length() - 4));
        return consent;
    }

    /** True when the mobile has a consent record for the version currently in force. */
    @Transactional(readOnly = true)
    public boolean hasCurrentConsent(String mobileNumber) {
        return consentRepository
                .findTopByMobileNumberAndPurposeOrderByGrantedAtDesc(mobileNumber, PURPOSE_COMPLAINT_REGISTRATION)
                .map(c -> authProps.getConsent().getVersion().equals(c.getConsentVersion()))
                .orElse(false);
    }

    private String truncate(String value) {
        return truncate(value, NOTICE_MAX_LENGTH);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
