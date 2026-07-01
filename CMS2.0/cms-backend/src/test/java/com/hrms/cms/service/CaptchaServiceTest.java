package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.CaptchaSession;
import com.hrms.cms.repository.CaptchaSessionRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class CaptchaServiceTest {

    @Mock
    private CaptchaSessionRepository captchaSessionRepository;

    @Mock
    private AuthSecurityProperties authProps;

    @InjectMocks
    private CaptchaService captchaService;

    @BeforeEach
    void setup() {
        AuthSecurityProperties.Captcha captchaProps = new AuthSecurityProperties.Captcha();
        captchaProps.setLength(6);
        captchaProps.setExpiryMinutes(5);
        captchaProps.setImageWidth(200);
        captchaProps.setImageHeight(60);
        when(authProps.getCaptcha()).thenReturn(captchaProps);
    }

    @Nested
    @DisplayName("generateVisualCaptcha")
    class GenerateVisual {

        @Test
        @DisplayName("should produce a base64 PNG image and a token")
        void shouldGenerateImageAndToken() {
            when(captchaSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var challenge = captchaService.generateVisualCaptcha();

            assertThat(challenge.token()).isNotBlank();
            assertThat(challenge.imageData()).startsWith("data:image/png;base64,");
            assertThat(challenge.type()).isEqualTo("VISUAL");

            verify(captchaSessionRepository).save(any(CaptchaSession.class));
        }

        @Test
        @DisplayName("should store answer hash, not plaintext")
        void shouldStoreHash() {
            when(captchaSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            captchaService.generateVisualCaptcha();

            ArgumentCaptor<CaptchaSession> captor = ArgumentCaptor.forClass(CaptchaSession.class);
            verify(captchaSessionRepository).save(captor.capture());

            CaptchaSession saved = captor.getValue();
            assertThat(saved.getAnswerHash()).hasSize(64);
            assertThat(saved.getCaptchaType()).isEqualTo("VISUAL");
            assertThat(saved.isUsed()).isFalse();
        }
    }

    @Nested
    @DisplayName("generateMathCaptcha")
    class GenerateMath {

        @Test
        @DisplayName("should produce an arithmetic question")
        void shouldGenerateMathQuestion() {
            when(captchaSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var challenge = captchaService.generateMathCaptcha();

            assertThat(challenge.audioQuestion()).matches("What is \\d+ (plus|minus) \\d+\\?");
            assertThat(challenge.type()).isEqualTo("MATH");
            assertThat(challenge.token()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("verifyCaptcha")
    class Verify {

        @Test
        @DisplayName("should return true for correct answer")
        void shouldVerifyCorrectAnswer() {
            String token = "test-token";
            String answer = "abc123";
            String hash = sha256(answer.toLowerCase());

            CaptchaSession session = CaptchaSession.builder()
                    .captchaToken(token)
                    .answerHash(hash)
                    .used(false)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            when(captchaSessionRepository.findByCaptchaTokenAndUsedFalseAndExpiresAtAfter(
                    eq(token), any())).thenReturn(Optional.of(session));
            when(captchaSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            boolean result = captchaService.verifyCaptcha(token, "abc123");

            assertThat(result).isTrue();
            assertThat(session.isUsed()).isTrue();
        }

        @Test
        @DisplayName("should return false for incorrect answer")
        void shouldRejectIncorrectAnswer() {
            String token = "test-token";
            String hash = sha256("abc123");

            CaptchaSession session = CaptchaSession.builder()
                    .captchaToken(token)
                    .answerHash(hash)
                    .used(false)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            when(captchaSessionRepository.findByCaptchaTokenAndUsedFalseAndExpiresAtAfter(
                    eq(token), any())).thenReturn(Optional.of(session));
            when(captchaSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            boolean result = captchaService.verifyCaptcha(token, "wrong");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for expired/used token")
        void shouldRejectExpiredToken() {
            when(captchaSessionRepository.findByCaptchaTokenAndUsedFalseAndExpiresAtAfter(
                    eq("expired"), any())).thenReturn(Optional.empty());

            boolean result = captchaService.verifyCaptcha("expired", "anything");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should reject null inputs")
        void shouldRejectNullInputs() {
            assertThat(captchaService.verifyCaptcha(null, "answer")).isFalse();
            assertThat(captchaService.verifyCaptcha("token", null)).isFalse();
            assertThat(captchaService.verifyCaptcha("token", "  ")).isFalse();
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            String token = "test-token";
            String hash = sha256("abc123");

            CaptchaSession session = CaptchaSession.builder()
                    .captchaToken(token)
                    .answerHash(hash)
                    .used(false)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            when(captchaSessionRepository.findByCaptchaTokenAndUsedFalseAndExpiresAtAfter(
                    eq(token), any())).thenReturn(Optional.of(session));
            when(captchaSessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            boolean result = captchaService.verifyCaptcha(token, "ABC123");

            assertThat(result).isTrue();
        }
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
