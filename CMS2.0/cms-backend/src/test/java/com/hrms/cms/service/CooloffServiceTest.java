package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.LoginCooloff;
import com.hrms.cms.repository.LoginCooloffRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class CooloffServiceTest {

    @Mock
    private LoginCooloffRepository cooloffRepository;

    @Mock
    private AuthSecurityProperties authProps;

    @InjectMocks
    private CooloffService cooloffService;

    @BeforeEach
    void setup() {
        AuthSecurityProperties.Cooloff cooloffProps = new AuthSecurityProperties.Cooloff();
        cooloffProps.setProgressionSeconds(List.of(30, 60, 120, 300, 600));
        cooloffProps.setMaxCeilingSeconds(600);
        cooloffProps.setResetAfterMinutes(60);
        when(authProps.getCooloff()).thenReturn(cooloffProps);
    }

    @Nested
    @DisplayName("checkCooloff")
    class CheckCooloff {

        @Test
        @DisplayName("should return inactive when no cooloff exists")
        void shouldReturnInactiveWhenNone() {
            when(cooloffRepository.findByFingerprintHashAndClientIpAndCooloffUntilAfter(
                    any(), any(), any())).thenReturn(Optional.empty());
            when(cooloffRepository.findByMobileNumberAndCooloffUntilAfter(
                    any(), any())).thenReturn(Optional.empty());

            var status = cooloffService.checkCooloff("fp-hash", "1.2.3.4", "9876543210");

            assertThat(status.active()).isFalse();
            assertThat(status.remainingSeconds()).isZero();
        }

        @Test
        @DisplayName("should return active when IP/fingerprint cooloff exists")
        void shouldDetectIpCooloff() {
            LoginCooloff cooloff = LoginCooloff.builder()
                    .cooloffUntil(LocalDateTime.now().plusSeconds(45))
                    .build();

            when(cooloffRepository.findByFingerprintHashAndClientIpAndCooloffUntilAfter(
                    eq("fp-hash"), eq("1.2.3.4"), any())).thenReturn(Optional.of(cooloff));

            var status = cooloffService.checkCooloff("fp-hash", "1.2.3.4", "9876543210");

            assertThat(status.active()).isTrue();
            assertThat(status.remainingSeconds()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should return active when mobile cooloff exists")
        void shouldDetectMobileCooloff() {
            when(cooloffRepository.findByFingerprintHashAndClientIpAndCooloffUntilAfter(
                    any(), any(), any())).thenReturn(Optional.empty());

            LoginCooloff cooloff = LoginCooloff.builder()
                    .cooloffUntil(LocalDateTime.now().plusSeconds(120))
                    .build();
            when(cooloffRepository.findByMobileNumberAndCooloffUntilAfter(
                    eq("9876543210"), any())).thenReturn(Optional.of(cooloff));

            var status = cooloffService.checkCooloff("fp-hash", "1.2.3.4", "9876543210");

            assertThat(status.active()).isTrue();
        }
    }

    @Nested
    @DisplayName("recordFailedAttempt")
    class RecordFailed {

        @Test
        @DisplayName("should create new cooloff on first failure with 30s duration")
        void shouldCreateNewCooloff() {
            when(cooloffRepository.findByFingerprintHashAndClientIp("fp-hash", "1.2.3.4"))
                    .thenReturn(Optional.empty());
            when(cooloffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            cooloffService.recordFailedAttempt("fp-hash", "1.2.3.4", "9876543210");

            ArgumentCaptor<LoginCooloff> captor = ArgumentCaptor.forClass(LoginCooloff.class);
            verify(cooloffRepository).save(captor.capture());

            LoginCooloff saved = captor.getValue();
            assertThat(saved.getFailedAttempts()).isEqualTo(1);
            assertThat(saved.getCooloffSeconds()).isEqualTo(30);
        }

        @Test
        @DisplayName("should escalate cooloff duration on subsequent failures")
        void shouldEscalateCooloff() {
            LoginCooloff existing = LoginCooloff.builder()
                    .fingerprintHash("fp-hash")
                    .clientIp("1.2.3.4")
                    .mobileNumber("9876543210")
                    .failedAttempts(2)
                    .cooloffSeconds(60)
                    .cooloffUntil(LocalDateTime.now().minusSeconds(10))
                    .lastAttemptAt(LocalDateTime.now().minusMinutes(1))
                    .build();

            when(cooloffRepository.findByFingerprintHashAndClientIp("fp-hash", "1.2.3.4"))
                    .thenReturn(Optional.of(existing));
            when(cooloffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            cooloffService.recordFailedAttempt("fp-hash", "1.2.3.4", "9876543210");

            assertThat(existing.getFailedAttempts()).isEqualTo(3);
            assertThat(existing.getCooloffSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should cap at maximum ceiling (600s)")
        void shouldCapAtMaximum() {
            LoginCooloff existing = LoginCooloff.builder()
                    .fingerprintHash("fp-hash")
                    .clientIp("1.2.3.4")
                    .mobileNumber("9876543210")
                    .failedAttempts(10)
                    .cooloffSeconds(600)
                    .cooloffUntil(LocalDateTime.now().minusSeconds(10))
                    .lastAttemptAt(LocalDateTime.now().minusMinutes(1))
                    .build();

            when(cooloffRepository.findByFingerprintHashAndClientIp("fp-hash", "1.2.3.4"))
                    .thenReturn(Optional.of(existing));
            when(cooloffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            cooloffService.recordFailedAttempt("fp-hash", "1.2.3.4", "9876543210");

            assertThat(existing.getCooloffSeconds()).isEqualTo(600);
        }

        @Test
        @DisplayName("should reset counter after 60 minutes of inactivity")
        void shouldResetAfterTimeout() {
            LoginCooloff existing = LoginCooloff.builder()
                    .fingerprintHash("fp-hash")
                    .clientIp("1.2.3.4")
                    .mobileNumber("9876543210")
                    .failedAttempts(5)
                    .cooloffSeconds(600)
                    .cooloffUntil(LocalDateTime.now().minusHours(2))
                    .lastAttemptAt(LocalDateTime.now().minusHours(2))
                    .build();

            when(cooloffRepository.findByFingerprintHashAndClientIp("fp-hash", "1.2.3.4"))
                    .thenReturn(Optional.of(existing));
            when(cooloffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            cooloffService.recordFailedAttempt("fp-hash", "1.2.3.4", "9876543210");

            assertThat(existing.getFailedAttempts()).isEqualTo(1);
            assertThat(existing.getCooloffSeconds()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("clearCooloff")
    class ClearCooloff {

        @Test
        @DisplayName("should reset failed attempts and cooloff on successful login")
        void shouldClearOnSuccess() {
            LoginCooloff existing = LoginCooloff.builder()
                    .fingerprintHash("fp-hash")
                    .clientIp("1.2.3.4")
                    .failedAttempts(3)
                    .cooloffUntil(LocalDateTime.now().plusMinutes(5))
                    .build();

            when(cooloffRepository.findByFingerprintHashAndClientIp("fp-hash", "1.2.3.4"))
                    .thenReturn(Optional.of(existing));
            when(cooloffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            cooloffService.clearCooloff("fp-hash", "1.2.3.4");

            assertThat(existing.getFailedAttempts()).isZero();
        }
    }
}
