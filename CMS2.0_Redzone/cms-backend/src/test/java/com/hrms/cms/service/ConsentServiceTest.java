package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.CitizenConsent;
import com.hrms.cms.repository.CitizenConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ConsentServiceTest {

    private static final String HINDI_NOTICE =
            "मैं आरबीआई द्वारा मेरी शिकायत के पंजीकरण और समाधान के लिए मेरे व्यक्तिगत डेटा के उपयोग की सहमति देता/देती हूँ।";
    private static final String ENGLISH_NOTICE =
            "I consent to RBI using my personal data for registering and resolving my complaint in line with "
            + "applicable laws and the Digital Personal Data Protection Act, 2023.";

    @Mock
    private CitizenConsentRepository consentRepository;

    @Mock
    private TranslationService translationService;

    @Mock
    private AuthSecurityProperties authProps;

    @InjectMocks
    private ConsentService consentService;

    private AuthSecurityProperties.Consent consentProps;

    @BeforeEach
    void setup() {
        consentProps = new AuthSecurityProperties.Consent();
        consentProps.setVersion("DPDP_2023_V1");
        when(authProps.getConsent()).thenReturn(consentProps);

        when(translationService.getTranslationsForLocale("en"))
                .thenReturn(Map.of(ConsentService.NOTICE_KEY, ENGLISH_NOTICE));
        when(translationService.getTranslationsForLocale("hi"))
                .thenReturn(Map.of(ConsentService.NOTICE_KEY, HINDI_NOTICE));
    }

    private CitizenConsent captureSaved() {
        ArgumentCaptor<CitizenConsent> captor = ArgumentCaptor.forClass(CitizenConsent.class);
        verify(consentRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("recordConsent")
    class RecordConsent {

        @Test
        @DisplayName("snapshots the notice actually served for the citizen's locale")
        void snapshotsLocalisedNotice() {
            consentService.recordConsent("9876543210", "hi", "10.1.2.3", "Mozilla/5.0");

            CitizenConsent saved = captureSaved();
            assertThat(saved.getNoticeText()).isEqualTo(HINDI_NOTICE);
            assertThat(saved.getLocale()).isEqualTo("hi");
        }

        @Test
        @DisplayName("stamps the consent version currently in force")
        void stampsCurrentVersion() {
            consentService.recordConsent("9876543210", "en", null, null);
            assertThat(captureSaved().getConsentVersion()).isEqualTo("DPDP_2023_V1");
        }

        @Test
        @DisplayName("records the complaint-registration purpose and the citizen's mobile")
        void recordsPurposeAndMobile() {
            consentService.recordConsent("9876543210", "en", "10.1.2.3", "UA");

            CitizenConsent saved = captureSaved();
            assertThat(saved.getPurpose()).isEqualTo(ConsentService.PURPOSE_COMPLAINT_REGISTRATION);
            assertThat(saved.getMobileNumber()).isEqualTo("9876543210");
            assertThat(saved.getClientIp()).isEqualTo("10.1.2.3");
            assertThat(saved.getUserAgent()).isEqualTo("UA");
        }

        @Test
        @DisplayName("falls back to English when no locale is supplied")
        void defaultsToEnglish() {
            consentService.recordConsent("9876543210", null, null, null);
            assertThat(captureSaved().getLocale()).isEqualTo("en");

            reset(consentRepository);
            consentService.recordConsent("9876543210", "   ", null, null);
            assertThat(captureSaved().getLocale()).isEqualTo("en");
        }

        @Test
        @DisplayName("stores the key itself when the locale has no translation, so the gap is visible")
        void storesKeyWhenTranslationMissing() {
            when(translationService.getTranslationsForLocale("ta")).thenReturn(Map.of());

            consentService.recordConsent("9876543210", "ta", null, null);
            assertThat(captureSaved().getNoticeText()).isEqualTo(ConsentService.NOTICE_KEY);
        }

        @Test
        @DisplayName("truncates an over-long notice to the NOTICE_TEXT column width")
        void truncatesOverlongNotice() {
            when(translationService.getTranslationsForLocale("en"))
                    .thenReturn(Map.of(ConsentService.NOTICE_KEY, "x".repeat(2500)));

            consentService.recordConsent("9876543210", "en", null, null);
            assertThat(captureSaved().getNoticeText()).hasSize(2000);
        }

        @Test
        @DisplayName("truncates an over-long user agent to the USER_AGENT column width")
        void truncatesOverlongUserAgent() {
            consentService.recordConsent("9876543210", "en", null, "u".repeat(900));
            assertThat(captureSaved().getUserAgent()).hasSize(500);
        }
    }

    @Nested
    @DisplayName("hasCurrentConsent")
    class HasCurrentConsent {

        private CitizenConsent consentWithVersion(String version) {
            return CitizenConsent.builder()
                    .mobileNumber("9876543210")
                    .purpose(ConsentService.PURPOSE_COMPLAINT_REGISTRATION)
                    .consentVersion(version)
                    .locale("en")
                    .noticeText(ENGLISH_NOTICE)
                    .grantedAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("true when the latest consent matches the version in force")
        void trueForCurrentVersion() {
            when(consentRepository.findTopByMobileNumberAndPurposeOrderByGrantedAtDesc(
                    "9876543210", ConsentService.PURPOSE_COMPLAINT_REGISTRATION))
                    .thenReturn(Optional.of(consentWithVersion("DPDP_2023_V1")));

            assertThat(consentService.hasCurrentConsent("9876543210")).isTrue();
        }

        @Test
        @DisplayName("false when the notice has been revised since consent was given")
        void falseAfterVersionBump() {
            when(consentRepository.findTopByMobileNumberAndPurposeOrderByGrantedAtDesc(
                    anyString(), anyString()))
                    .thenReturn(Optional.of(consentWithVersion("DPDP_2023_V1")));
            consentProps.setVersion("DPDP_2023_V2");

            assertThat(consentService.hasCurrentConsent("9876543210")).isFalse();
        }

        @Test
        @DisplayName("false when the citizen has never consented")
        void falseWhenNoRecord() {
            when(consentRepository.findTopByMobileNumberAndPurposeOrderByGrantedAtDesc(
                    anyString(), anyString()))
                    .thenReturn(Optional.empty());

            assertThat(consentService.hasCurrentConsent("9876543210")).isFalse();
        }
    }
}
