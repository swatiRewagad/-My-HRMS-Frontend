package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.LoginCooloff;
import com.hrms.cms.repository.LoginCooloffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CooloffService {

    private final LoginCooloffRepository cooloffRepository;
    private final AuthSecurityProperties authProps;

    public CooloffStatus checkCooloff(String fingerprintHash, String clientIp, String mobileNumber) {
        Optional<LoginCooloff> byFingerprint = cooloffRepository
                .findByFingerprintHashAndClientIpAndCooloffUntilAfter(fingerprintHash, clientIp, LocalDateTime.now());

        if (byFingerprint.isPresent()) {
            LoginCooloff co = byFingerprint.get();
            long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), co.getCooloffUntil()).getSeconds();
            return new CooloffStatus(true, (int) remainingSeconds, "IP/browser cooloff active");
        }

        Optional<LoginCooloff> byMobile = cooloffRepository
                .findByMobileNumberAndCooloffUntilAfter(mobileNumber, LocalDateTime.now());

        if (byMobile.isPresent()) {
            LoginCooloff co = byMobile.get();
            long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), co.getCooloffUntil()).getSeconds();
            return new CooloffStatus(true, (int) remainingSeconds, "Mobile number cooloff active");
        }

        return new CooloffStatus(false, 0, null);
    }

    @Transactional
    public void recordFailedAttempt(String fingerprintHash, String clientIp, String mobileNumber) {
        Optional<LoginCooloff> existing = cooloffRepository
                .findByFingerprintHashAndClientIp(fingerprintHash, clientIp);

        LoginCooloff cooloff;
        if (existing.isPresent()) {
            cooloff = existing.get();

            if (cooloff.getLastAttemptAt().isBefore(
                    LocalDateTime.now().minusMinutes(authProps.getCooloff().getResetAfterMinutes()))) {
                cooloff.setFailedAttempts(0);
            }

            cooloff.setFailedAttempts(cooloff.getFailedAttempts() + 1);
            cooloff.setMobileNumber(mobileNumber);
        } else {
            cooloff = LoginCooloff.builder()
                    .fingerprintHash(fingerprintHash)
                    .clientIp(clientIp)
                    .mobileNumber(mobileNumber)
                    .failedAttempts(1)
                    .cooloffSeconds(0)
                    .cooloffUntil(LocalDateTime.now())
                    .build();
        }

        int nextCooloff = calculateCooloffSeconds(cooloff.getFailedAttempts());
        cooloff.setCooloffSeconds(nextCooloff);
        cooloff.setCooloffUntil(LocalDateTime.now().plusSeconds(nextCooloff));

        cooloffRepository.save(cooloff);
        log.info("Cooloff applied: {}s for IP {} mobile {}****",
                nextCooloff, clientIp, mobileNumber.substring(0, 4));
    }

    @Transactional
    public void clearCooloff(String fingerprintHash, String clientIp) {
        Optional<LoginCooloff> existing = cooloffRepository
                .findByFingerprintHashAndClientIp(fingerprintHash, clientIp);
        existing.ifPresent(co -> {
            co.setFailedAttempts(0);
            co.setCooloffUntil(LocalDateTime.now());
            cooloffRepository.save(co);
        });
    }

    private int calculateCooloffSeconds(int failedAttempts) {
        List<Integer> progression = authProps.getCooloff().getProgressionSeconds();
        int index = Math.min(failedAttempts - 1, progression.size() - 1);
        if (index < 0) return 0;
        return Math.min(progression.get(index), authProps.getCooloff().getMaxCeilingSeconds());
    }

    public record CooloffStatus(boolean active, int remainingSeconds, String reason) {}
}
