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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class OtpServiceTest {

    @Mock
    private OtpAttemptRepository otpAttemptRepository;

    @Mock
    private AuthSecurityProperties authProps;

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

        rateLimitProps = new AuthSecurityProperties.RateLimit();
        rateLimitProps.setOtpRequestsPerMobilePerHour(5);

        when(authProps.getOtp()).thenReturn(otpProps);
        when(authProps.getRateLimit()).thenReturn(rateLimitProps);
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
