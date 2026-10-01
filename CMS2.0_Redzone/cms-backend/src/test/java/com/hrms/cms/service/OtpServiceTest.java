package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.OtpAttempt;
import com.hrms.cms.repository.OtpAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class OtpServiceTest {

    @Mock
    private OtpAttemptRepository otpAttemptRepository;

    @Mock
    private AuthSecurityProperties authProps;

    /**
     * The three OPTIONAL collaborators OtpService takes by field (@Autowired(required=false)) rather than
     * through the Lombok constructor. @InjectMocks populates them by field injection.
     */
    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private TranslationService translationService;

    @Mock
    private OutboundMessagePort outboundMessagePort;

    @InjectMocks
    private OtpService otpService;

    private AuthSecurityProperties.Otp otpProps;
    private AuthSecurityProperties.RateLimit rateLimitProps;

    @BeforeEach
    void setup() {
        otpProps = new AuthSecurityProperties.Otp();
        otpProps.setLength(6);
        otpProps.setExpiryMinutes(5);
        otpProps.setMaxVerifyAttemptsPerOtp(3);
        otpProps.setResendCooldownSeconds(120);

        rateLimitProps = new AuthSecurityProperties.RateLimit();
        rateLimitProps.setOtpRequestsPerMobilePerHour(5);

        when(authProps.getOtp()).thenReturn(otpProps);
        when(authProps.getRateLimit()).thenReturn(rateLimitProps);

        // @InjectMocks used the Lombok constructor and does NOT then inject fields, so the three optional
        // collaborators would stay null and every test below would quietly exercise the fallback path
        // instead of the one it names. They are bound explicitly.
        otpService.setSystemConfigService(systemConfigService);
        otpService.setTranslationService(translationService);
        otpService.setOutboundMessagePort(outboundMessagePort);

        // Faithful default: SystemConfigService.getInt(key, default) returns the default when no
        // SYSTEM_CONFIG row exists, which is the state of every environment that has not overridden the
        // window. Without this a bare mock would answer 0 and resendCooldownSeconds() would accept it
        // (0 is a legitimate value — it disables the gap), silently turning off a control the property
        // configures. Individual tests override with the value they are actually exercising.
        when(systemConfigService.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
    }

    @Nested
    @DisplayName("generateOtp")
    class GenerateOtp {

        @Test
        @DisplayName("should generate OTP of configured length and save to DB")
        void shouldGenerateAndSave() {
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            String otp = otpService.generateOtp("9876543210", "session-1", "SMS", null);

            assertThat(otp).hasSize(6);
            assertThat(otp).matches("\\d{6}");

            ArgumentCaptor<OtpAttempt> captor = ArgumentCaptor.forClass(OtpAttempt.class);
            verify(otpAttemptRepository).save(captor.capture());

            OtpAttempt saved = captor.getValue();
            assertThat(saved.getMobileNumber()).isEqualTo("9876543210");
            assertThat(saved.getChannel()).isEqualTo("SMS");
            assertThat(saved.isUsed()).isFalse();
            assertThat(saved.getAttemptCount()).isZero();
        }

        @Test
        @DisplayName("should store OTP as hash, not plaintext")
        void shouldHashOtp() {
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            String otp = otpService.generateOtp("9876543210", "session-1", "SMS", null);

            ArgumentCaptor<OtpAttempt> captor = ArgumentCaptor.forClass(OtpAttempt.class);
            verify(otpAttemptRepository).save(captor.capture());

            assertThat(captor.getValue().getOtpHash()).isNotEqualTo(otp);
            assertThat(captor.getValue().getOtpHash()).hasSize(64);
        }

        @Test
        @DisplayName("UST8: should invalidate any earlier live OTP for the same mobile")
        void shouldSupersedeEarlierOtps() {
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            otpService.generateOtp("9876543210", "session-2", "SMS", null);

            verify(otpAttemptRepository).invalidateActiveOtps(eq("9876543210"), any());
        }
    }

    @Nested
    @DisplayName("resendCooldownRemaining (UST8)")
    class ResendCooldown {

        @Test
        @DisplayName("should allow the first ever request")
        void shouldAllowFirstRequest() {
            when(otpAttemptRepository.findTopByMobileNumberOrderByCreatedAtDesc("9876543210"))
                    .thenReturn(Optional.empty());

            assertThat(otpService.resendCooldownRemaining("9876543210")).isZero();
        }

        @Test
        @DisplayName("should report the remaining wait when an OTP was just issued")
        void shouldReportRemainingWait() {
            OtpAttempt recent = OtpAttempt.builder().mobileNumber("9876543210").build();
            recent.setCreatedAt(LocalDateTime.now().minusSeconds(30));

            when(otpAttemptRepository.findTopByMobileNumberOrderByCreatedAtDesc("9876543210"))
                    .thenReturn(Optional.of(recent));

            assertThat(otpService.resendCooldownRemaining("9876543210")).isBetween(85, 90);
        }

        @Test
        @DisplayName("should allow once the cooldown has elapsed")
        void shouldAllowAfterCooldown() {
            OtpAttempt old = OtpAttempt.builder().mobileNumber("9876543210").build();
            old.setCreatedAt(LocalDateTime.now().minusSeconds(121));

            when(otpAttemptRepository.findTopByMobileNumberOrderByCreatedAtDesc("9876543210"))
                    .thenReturn(Optional.of(old));

            assertThat(otpService.resendCooldownRemaining("9876543210")).isZero();
        }

        @Test
        @DisplayName("should be disabled when configured to zero, as in dev-local")
        void shouldBeDisabledWhenZero() {
            otpProps.setResendCooldownSeconds(0);
            OtpAttempt recent = OtpAttempt.builder().mobileNumber("9876543210").build();
            recent.setCreatedAt(LocalDateTime.now());

            when(otpAttemptRepository.findTopByMobileNumberOrderByCreatedAtDesc("9876543210"))
                    .thenReturn(Optional.of(recent));

            assertThat(otpService.resendCooldownRemaining("9876543210")).isZero();
        }
    }

    /**
     * The SMS the citizen is supposed to receive.
     *
     * <p>WHY THIS IS A UNIT TEST AND NOT AN E2E ONE. QA states the body verbatim
     * ("Your OTP for Mobile Number authentication on RBI CMS is 211067. This is valid only for 5
     * minutes"), but the only transport is {@code LoggingOutboundMessageAdapter}, which deliberately
     * delivers nothing and — by PII policy — logs the body's LENGTH, not its text. So there is no
     * observable artefact for a browser test to read, and the composed text can only be asserted here.
     * {@link OtpService#renderOtpMessage} is package-private for exactly this reason.
     *
     * <p>{@code translationService} and {@code outboundMessagePort} are optional @Autowired FIELDS on the
     * service, so Mockito's @InjectMocks populates them by field injection once they are declared as
     * @Mock above; the tests in the other nested classes leave them null and take the fallback paths.
     */
    @Nested
    @DisplayName("OTP message composition and dispatch")
    class OtpMessage {

        private static final String TEMPLATE =
                "Your OTP for Mobile Number authentication on RBI CMS is {{otp}}. "
                        + "This is valid only for {{minutes}} minutes";

        @Test
        @DisplayName("renders the exact SMS body with the OTP and the ENFORCED validity interpolated")
        void shouldRenderExactBody() {
            when(translationService.getTranslationsForLocale("en"))
                    .thenReturn(java.util.Map.of(OtpService.SMS_BODY_KEY, TEMPLATE));

            String body = otpService.renderOtpMessage("211067", 5, "en");

            assertThat(body).isEqualTo(
                    "Your OTP for Mobile Number authentication on RBI CMS is 211067. "
                            + "This is valid only for 5 minutes");
        }

        @Test
        @DisplayName("quotes the validity the server enforces, not a literal 5")
        void shouldQuoteConfiguredValidity() {
            when(translationService.getTranslationsForLocale("en"))
                    .thenReturn(java.util.Map.of(OtpService.SMS_BODY_KEY, TEMPLATE));

            // dev-local runs at 10 minutes, not 5. The message must follow the configuration.
            assertThat(otpService.renderOtpMessage("211067", 10, "en"))
                    .contains("valid only for 10 minutes")
                    .doesNotContain("5 minutes");
        }

        @Test
        @DisplayName("refuses to produce a body that would leak a {{placeholder}} to the citizen")
        void shouldRefuseUnresolvedPlaceholder() {
            when(translationService.getTranslationsForLocale("hi"))
                    .thenReturn(java.util.Map.of(OtpService.SMS_BODY_KEY,
                            "आपका OTP {{otp}} है, {{validity}} मिनट के लिए वैध"));

            assertThat(otpService.renderOtpMessage("211067", 5, "hi")).isNull();
        }

        @Test
        @DisplayName("falls back to English when no locale is supplied")
        void shouldDefaultToEnglish() {
            when(translationService.getTranslationsForLocale("en"))
                    .thenReturn(java.util.Map.of(OtpService.SMS_BODY_KEY, TEMPLATE));

            assertThat(otpService.renderOtpMessage("211067", 5, null)).contains("211067");
            verify(translationService).getTranslationsForLocale("en");
        }

        @Test
        @DisplayName("addresses the composed SMS to the citizen's mobile on generation")
        void shouldDispatchToMobile() {
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(translationService.getTranslationsForLocale("en"))
                    .thenReturn(java.util.Map.of(OtpService.SMS_BODY_KEY, TEMPLATE));

            String otp = otpService.generateOtp("9876543210", "session-3", "SMS", null, "en");

            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(outboundMessagePort).send(eq("SMS"), eq("9876543210"), isNull(),
                    bodyCaptor.capture(), isNull());
            assertThat(bodyCaptor.getValue())
                    .isEqualTo("Your OTP for Mobile Number authentication on RBI CMS is " + otp
                            + ". This is valid only for 5 minutes");
        }

        @Test
        @DisplayName("a dispatch failure does NOT deny the citizen an OTP that was in fact issued")
        void shouldNotFailRequestOnDispatchFailure() {
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(translationService.getTranslationsForLocale("en"))
                    .thenReturn(java.util.Map.of(OtpService.SMS_BODY_KEY, TEMPLATE));
            doThrow(new OutboundMessagePort.OutboundDispatchException("gateway down"))
                    .when(outboundMessagePort).send(any(), any(), any(), any(), any());

            assertThat(otpService.generateOtp("9876543210", "session-4", "SMS", null, "en"))
                    .matches("\\d{6}");
        }

        @Test
        @DisplayName("dispatches nothing at all rather than a half-rendered message")
        void shouldDispatchNothingWhenTranslationMissing() {
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(translationService.getTranslationsForLocale("en")).thenReturn(java.util.Map.of());

            otpService.generateOtp("9876543210", "session-5", "SMS", null, "en");

            verify(outboundMessagePort, never()).send(any(), any(), any(), any(), any());
        }
    }

    /**
     * The two windows the citizen is TOLD about must be the two the server ENFORCES, and both are
     * retunable without a redeploy — so both are read from SYSTEM_CONFIG with the property as default.
     */
    @Nested
    @DisplayName("configurable windows")
    class ConfigurableWindows {

        @Test
        @DisplayName("a SYSTEM_CONFIG override beats the application.yml property")
        void configOverridesProperty() {
            when(systemConfigService.getInt("cms.auth.otp.expiry_minutes", 5)).thenReturn(3);
            when(systemConfigService.getInt("cms.auth.otp.resend_cooldown_seconds", 120)).thenReturn(90);

            assertThat(otpService.expiryMinutes()).isEqualTo(3);
            assertThat(otpService.resendCooldownSeconds()).isEqualTo(90);
        }

        @Test
        @DisplayName("a nonsensical override is ignored in favour of the property")
        void rejectsNonPositiveExpiry() {
            when(systemConfigService.getInt("cms.auth.otp.expiry_minutes", 5)).thenReturn(0);
            when(systemConfigService.getInt("cms.auth.otp.resend_cooldown_seconds", 120)).thenReturn(-1);

            assertThat(otpService.expiryMinutes()).isEqualTo(5);
            assertThat(otpService.resendCooldownSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("the row EXPIRES_AT is set from the same accessor the SMS quotes")
        void expiryRowFollowsConfig() {
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(systemConfigService.getInt("cms.auth.otp.expiry_minutes", 5)).thenReturn(3);

            otpService.generateOtp("9876543210", "session-6", "SMS", null);

            ArgumentCaptor<OtpAttempt> captor = ArgumentCaptor.forClass(OtpAttempt.class);
            verify(otpAttemptRepository).save(captor.capture());
            assertThat(captor.getValue().getExpiresAt())
                    .isAfter(LocalDateTime.now().plusMinutes(2))
                    .isBefore(LocalDateTime.now().plusMinutes(4));
        }
    }

    @Nested
    @DisplayName("verifyOtp")
    class VerifyOtp {

        @Test
        @DisplayName("should return SUCCESS for correct OTP")
        void shouldSucceedForCorrectOtp() throws Exception {
            String otp = "123456";
            String hash = hashSha256(otp);

            OtpAttempt attempt = OtpAttempt.builder()
                    .mobileNumber("9876543210")
                    .otpHash(hash)
                    .used(false)
                    .attemptCount(0)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            when(otpAttemptRepository
                    .findTopByMobileNumberAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                            eq("9876543210"), any()))
                    .thenReturn(Optional.of(attempt));
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = otpService.verifyOtp("9876543210", "123456");

            assertThat(result).isEqualTo(OtpService.OtpVerificationResult.SUCCESS);
            assertThat(attempt.isUsed()).isTrue();
            assertThat(attempt.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("should return INVALID for incorrect OTP")
        void shouldFailForIncorrectOtp() throws Exception {
            String hash = hashSha256("123456");

            OtpAttempt attempt = OtpAttempt.builder()
                    .mobileNumber("9876543210")
                    .otpHash(hash)
                    .used(false)
                    .attemptCount(0)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            when(otpAttemptRepository
                    .findTopByMobileNumberAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                            eq("9876543210"), any()))
                    .thenReturn(Optional.of(attempt));
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = otpService.verifyOtp("9876543210", "999999");

            assertThat(result).isEqualTo(OtpService.OtpVerificationResult.INVALID);
            assertThat(attempt.getAttemptCount()).isEqualTo(1);
            assertThat(attempt.isUsed()).isFalse();
        }

        @Test
        @DisplayName("should return MAX_ATTEMPTS_EXCEEDED after 3 failed tries")
        void shouldExceedMaxAttempts() throws Exception {
            String hash = hashSha256("123456");

            OtpAttempt attempt = OtpAttempt.builder()
                    .mobileNumber("9876543210")
                    .otpHash(hash)
                    .used(false)
                    .attemptCount(3)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            when(otpAttemptRepository
                    .findTopByMobileNumberAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                            eq("9876543210"), any()))
                    .thenReturn(Optional.of(attempt));
            when(otpAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = otpService.verifyOtp("9876543210", "999999");

            assertThat(result).isEqualTo(OtpService.OtpVerificationResult.MAX_ATTEMPTS_EXCEEDED);
            assertThat(attempt.isUsed()).isTrue();
        }

        @Test
        @DisplayName("should return EXPIRED_OR_NOT_FOUND when no active OTP")
        void shouldReturnExpired() {
            when(otpAttemptRepository
                    .findTopByMobileNumberAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                            eq("9876543210"), any()))
                    .thenReturn(Optional.empty());

            var result = otpService.verifyOtp("9876543210", "123456");

            assertThat(result).isEqualTo(OtpService.OtpVerificationResult.EXPIRED_OR_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("rateLimiting")
    class RateLimiting {

        @Test
        @DisplayName("should detect rate limit when max OTP requests exceeded")
        void shouldDetectRateLimit() {
            when(otpAttemptRepository.countRecentByMobile(eq("9876543210"), any()))
                    .thenReturn(5L);

            assertThat(otpService.isRateLimitedByMobile("9876543210")).isTrue();
        }

        @Test
        @DisplayName("should not rate limit when under threshold")
        void shouldNotRateLimit() {
            when(otpAttemptRepository.countRecentByMobile(eq("9876543210"), any()))
                    .thenReturn(2L);

            assertThat(otpService.isRateLimitedByMobile("9876543210")).isFalse();
        }
    }

    private String hashSha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes());
        return HexFormat.of().formatHex(hash);
    }
}
