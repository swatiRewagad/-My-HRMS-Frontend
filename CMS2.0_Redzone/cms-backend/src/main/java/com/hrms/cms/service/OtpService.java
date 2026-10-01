package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.OtpAttempt;
import com.hrms.cms.repository.OtpAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    /**
     * SYSTEM_CONFIG keys for the two OTP windows a citizen is told about.
     *
     * <p>WHY BOTH ARE READABLE FROM SYSTEM_CONFIG AND NOT ONLY FROM application.yml. The citizen-facing
     * wording quotes these figures ("valid only for 5 minutes", "can only be regenerated after 2
     * minutes"), and the SMS and the refusal message must agree with what the server actually enforces.
     * A property alone needs a redeploy to retune, so the enforced rule and the promised rule drift —
     * the same defect class as the RE window and the upload limits. The property remains the DEFAULT; a
     * SYSTEM_CONFIG row overrides it, and both the enforcement and the prose read this one accessor.
     */
    static final String CFG_EXPIRY_MINUTES = "cms.auth.otp.expiry_minutes";
    static final String CFG_RESEND_COOLDOWN_SECONDS = "cms.auth.otp.resend_cooldown_seconds";

    /** Key of the SMS a citizen receives. Registered as a translation key so it localises. */
    public static final String SMS_BODY_KEY = "login.otp_sms_body";

    private final OtpAttemptRepository otpAttemptRepository;
    private final AuthSecurityProperties authProps;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Optional collaborators, injected by field rather than constructor.
     *
     * <p>Deliberate, and the reason is not style: {@code OtpServiceTest} builds this service with
     * {@code @InjectMocks} over the Lombok constructor, so adding required constructor parameters would
     * silently pass null for anything the test does not mock and every existing OTP unit test would NPE
     * on a path it is not testing. Optional injection with null guards is the pattern already used for
     * cross-cutting collaborators in this codebase (see {@code RbioWorkflowService}), and it keeps the
     * fallback behaviour explicit: no config service means the configured property wins, no translation
     * service or transport means no SMS is composed — never a half-rendered message.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SystemConfigService systemConfigService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TranslationService translationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OutboundMessagePort outboundMessagePort;

    /**
     * Package-private setters for the three optional collaborators.
     *
     * <p>Not a convenience: Mockito's {@code @InjectMocks} picks the BIGGEST constructor it can satisfy
     * and, having used the Lombok constructor, does not then also inject fields — so a mock declared for
     * an {@code @Autowired(required=false)} field silently stays null and the test exercises the fallback
     * path instead of the one it names. Explicit setters make the wiring visible in the test rather than
     * dependent on Mockito's injection order. Package-private, so nothing outside this package can
     * rebind a collaborator at runtime.
     */
    void setSystemConfigService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    void setTranslationService(TranslationService translationService) {
        this.translationService = translationService;
    }

    void setOutboundMessagePort(OutboundMessagePort outboundMessagePort) {
        this.outboundMessagePort = outboundMessagePort;
    }

    /** Minutes an OTP stays valid — the figure the SMS quotes and {@link #generateOtp} enforces. */
    public int expiryMinutes() {
        int property = authProps.getOtp().getExpiryMinutes();
        if (systemConfigService == null) return property;
        int configured = systemConfigService.getInt(CFG_EXPIRY_MINUTES, property);
        return configured > 0 ? configured : property;
    }

    /** Seconds that must pass between OTP requests for one mobile. 0 disables the gap (dev-local). */
    public int resendCooldownSeconds() {
        int property = authProps.getOtp().getResendCooldownSeconds();
        if (systemConfigService == null) return property;
        int configured = systemConfigService.getInt(CFG_RESEND_COOLDOWN_SECONDS, property);
        return configured >= 0 ? configured : property;
    }

    /**
     * UST8: seconds the caller must still wait before another OTP may be requested, or 0 when a request
     * is allowed now. The browser timer alone is no control — a scripted client simply skips it.
     */
    public int resendCooldownRemaining(String mobileNumber) {
        int cooldown = resendCooldownSeconds();
        if (cooldown <= 0) return 0;
        return otpAttemptRepository.findTopByMobileNumberOrderByCreatedAtDesc(mobileNumber)
                .map(last -> {
                    long elapsed = Duration.between(last.getCreatedAt(), LocalDateTime.now()).toSeconds();
                    return elapsed >= cooldown ? 0 : (int) (cooldown - elapsed);
                })
                .orElse(0);
    }

    /**
     * Whether this mobile has an OTP request recent enough for a RESEND to be a continuation of it.
     *
     * <p>The resend endpoint takes no CAPTCHA — a citizen who has already solved one and is waiting for a
     * code should not have to solve another to be sent it again. This is what keeps that safe: a resend is
     * only accepted while an earlier, CAPTCHA-gated request for the same mobile is still in flight. Once
     * the window has passed, the citizen starts again from the mobile + CAPTCHA screen.
     *
     * <p>The window is expiry + cooldown rather than expiry alone, so a code that has just lapsed can
     * still be re-sent from the OTP screen the citizen is looking at.
     */
    public boolean hasRecentOtpRequest(String mobileNumber) {
        LocalDateTime since = LocalDateTime.now()
                .minusMinutes(expiryMinutes())
                .minusSeconds(Math.max(resendCooldownSeconds(), 0));
        return otpAttemptRepository.findTopByMobileNumberOrderByCreatedAtDesc(mobileNumber)
                .map(last -> last.getCreatedAt() != null && last.getCreatedAt().isAfter(since))
                .orElse(false);
    }

    @Transactional
    public String generateOtp(String mobileNumber, String sessionId, String channel, String email) {
        return generateOtp(mobileNumber, sessionId, channel, email, null);
    }

    /**
     * Issues an OTP and dispatches it over {@code channel}.
     *
     * @param locale locale the SMS should be rendered in; null falls back to English
     */
    @Transactional
    public String generateOtp(String mobileNumber, String sessionId, String channel, String email,
                              String locale) {
        // UST8: without this an earlier OTP stays valid alongside the new one, so a regenerated code
        // does not actually retire the code that was already sent.
        otpAttemptRepository.invalidateActiveOtps(mobileNumber, LocalDateTime.now());

        String otp = authProps.getOtp().isDevAutoPopulate() ? "123456" : generateSecureOtp();
        String otpHash = hashValue(otp);

        int validityMinutes = expiryMinutes();
        OtpAttempt attempt = OtpAttempt.builder()
                .mobileNumber(mobileNumber)
                .otpHash(otpHash)
                .channel(channel)
                .email(email)
                .sessionId(sessionId)
                .used(false)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(validityMinutes))
                .build();

        otpAttemptRepository.save(attempt);
        log.info("OTP generated for mobile: ****{} via {}", mobileNumber.substring(mobileNumber.length() - 4), channel);

        dispatchOtp(mobileNumber, otp, validityMinutes, channel, email, locale);
        return otp;
    }

    /**
     * Renders the OTP message and hands it to the outbound transport.
     *
     * <p>WHY THIS EXISTS. {@code CitizenAuthController} previously carried
     * {@code // TODO: Integrate with actual SMS gateway} and a {@code log.info} — the OTP was generated,
     * stored and returned to the caller, and NOTHING was ever addressed to the citizen's handset. In
     * dev-local the code came back in the response body so the flow appeared to work end to end; with the
     * flag off, as in every other environment, a citizen could never receive a code at all.
     *
     * <p>The body is composed from the {@link #SMS_BODY_KEY} translation with the OTP and the validity
     * interpolated, so the figure quoted to the citizen is the one {@link #expiryMinutes} enforces rather
     * than a literal baked into a string.
     *
     * <p>Dispatch failure does NOT fail the request. The row is already committed and the OTP is valid, so
     * throwing here would deny a citizen a code that the system has in fact issued; the failure is logged
     * and, on a real gateway, the citizen resends. The transport today is
     * {@code LoggingOutboundMessageAdapter}, which delivers nothing and says so — so a passing test proves
     * the message was composed and addressed, not that it arrived.
     */
    private void dispatchOtp(String mobileNumber, String otp, int validityMinutes, String channel,
                             String email, String locale) {
        if (outboundMessagePort == null) {
            return;
        }
        String body = renderOtpMessage(otp, validityMinutes, locale);
        if (body == null) {
            // No translation available: a half-rendered message containing a raw {{placeholder}} is worse
            // than none, so nothing is dispatched and the gap is logged rather than shown to a citizen.
            log.error("OTP message not dispatched: translation {} is unavailable", SMS_BODY_KEY);
            return;
        }

        boolean viaEmail = "EMAIL".equalsIgnoreCase(channel);
        String recipient = viaEmail ? email : mobileNumber;
        if (recipient == null || recipient.isBlank()) {
            log.error("OTP message not dispatched: no recipient for channel {}", channel);
            return;
        }

        try {
            outboundMessagePort.send(viaEmail ? "EMAIL" : "SMS", recipient,
                    viaEmail ? "Your RBI CMS one-time password" : null, body, null);
        } catch (RuntimeException e) {
            log.error("OTP dispatch over {} failed for ****{}: {}", channel,
                    mobileNumber.substring(mobileNumber.length() - 4), e.getMessage());
        }
    }

    /**
     * Renders the OTP message body, or null when the text cannot be produced without leaking a
     * placeholder.
     *
     * <p>Both parameters are always supplied — {@code TranslationService.translate} in the portal takes
     * params optionally, and a {@code {{placeholder}}} key rendered without them prints the literal to the
     * citizen. That has already happened once in this product, so the check is explicit: if any
     * placeholder survives interpolation the message is refused rather than sent.
     */
    String renderOtpMessage(String otp, int validityMinutes, String locale) {
        if (translationService == null) {
            return null;
        }
        String resolvedLocale = (locale == null || locale.isBlank()) ? "en" : locale;
        // The map itself is null-checked: an OTP must not be denied because the translation lookup came
        // back empty. Every failure to produce a template ends the same way — no message, logged loudly.
        Map<String, String> bundle = translationService.getTranslationsForLocale(resolvedLocale);
        String template = bundle == null ? null : bundle.get(SMS_BODY_KEY);
        if (template == null || template.isBlank() || SMS_BODY_KEY.equals(template)) {
            return null;
        }

        String rendered = template
                .replace("{{otp}}", otp)
                .replace("{{minutes}}", String.valueOf(validityMinutes));
        if (rendered.contains("{{")) {
            log.error("OTP message template {} has an unresolved placeholder for locale {}: {}",
                    SMS_BODY_KEY, resolvedLocale, rendered);
            return null;
        }
        return rendered;
    }

    @Transactional
    public OtpVerificationResult verifyOtp(String mobileNumber, String otpInput) {
        Optional<OtpAttempt> activeOtp = otpAttemptRepository
                .findTopByMobileNumberAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        mobileNumber, LocalDateTime.now());

        if (activeOtp.isEmpty()) {
            return OtpVerificationResult.EXPIRED_OR_NOT_FOUND;
        }

        OtpAttempt attempt = activeOtp.get();

        if (attempt.getAttemptCount() >= authProps.getOtp().getMaxVerifyAttemptsPerOtp()) {
            attempt.setUsed(true);
            otpAttemptRepository.save(attempt);
            return OtpVerificationResult.MAX_ATTEMPTS_EXCEEDED;
        }

        attempt.setAttemptCount(attempt.getAttemptCount() + 1);

        String inputHash = hashValue(otpInput);
        if (constantTimeEquals(inputHash, attempt.getOtpHash())) {
            attempt.setUsed(true);
            attempt.setUsedAt(LocalDateTime.now());
            otpAttemptRepository.save(attempt);
            return OtpVerificationResult.SUCCESS;
        }

        otpAttemptRepository.save(attempt);
        return OtpVerificationResult.INVALID;
    }

    /**
     * Issues an OTP scoped to a session AND a channel, so two codes can be live at once (UST599).
     *
     * <p>Differs from {@link #generateOtp} in exactly one respect: invalidation is scoped to
     * {@code sessionId + channel} instead of to the mobile number. {@code generateOtp} retires every
     * live code for a mobile, which makes a dual-channel OTP impossible — the second code issued would
     * silently kill the first, so the user could never satisfy both inputs.
     *
     * <p>A separate method rather than a flag on the existing one: the citizen-login flow depends on
     * mobile-wide invalidation (UST8, a re-sent code must retire the previous one), and that behaviour
     * must not become conditional on a parameter a caller might get wrong.
     *
     * @param sessionId the upload-link token — unique per link and unused by any other query
     * @param channel   SMS or EMAIL; part of the lookup key, not merely metadata as elsewhere
     */
    @Transactional
    public String generateSessionOtp(String mobileNumber, String sessionId, String channel, String email) {
        otpAttemptRepository.invalidateActiveOtpsForSessionChannel(sessionId, channel, LocalDateTime.now());

        String otp = authProps.getOtp().isDevAutoPopulate() ? "123456" : generateSecureOtp();

        otpAttemptRepository.save(OtpAttempt.builder()
                .mobileNumber(mobileNumber)
                .otpHash(hashValue(otp))
                .channel(channel)
                .email(email)
                .sessionId(sessionId)
                .used(false)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes()))
                .build());

        log.info("Session OTP generated for session {} via {}", sessionId, channel);
        return otp;
    }

    /**
     * Verifies one channel's OTP for a session (UST599).
     *
     * <p>Attempt counting, expiry and max-attempt lockout behave exactly as in {@link #verifyOtp} —
     * deliberately identical, so the two channels cannot be brute-forced under a weaker rule than the
     * login flow. Each channel carries its own attempt counter because they are separate rows.
     */
    @Transactional
    public OtpVerificationResult verifySessionOtp(String sessionId, String channel, String otpInput) {
        Optional<OtpAttempt> activeOtp = otpAttemptRepository
                .findTopBySessionIdAndChannelAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        sessionId, channel, LocalDateTime.now());

        if (activeOtp.isEmpty()) {
            return OtpVerificationResult.EXPIRED_OR_NOT_FOUND;
        }

        OtpAttempt attempt = activeOtp.get();

        if (attempt.getAttemptCount() >= authProps.getOtp().getMaxVerifyAttemptsPerOtp()) {
            attempt.setUsed(true);
            otpAttemptRepository.save(attempt);
            return OtpVerificationResult.MAX_ATTEMPTS_EXCEEDED;
        }

        attempt.setAttemptCount(attempt.getAttemptCount() + 1);

        if (constantTimeEquals(hashValue(otpInput), attempt.getOtpHash())) {
            attempt.setUsed(true);
            attempt.setUsedAt(LocalDateTime.now());
            otpAttemptRepository.save(attempt);
            return OtpVerificationResult.SUCCESS;
        }

        otpAttemptRepository.save(attempt);
        return OtpVerificationResult.INVALID;
    }

    public boolean isRateLimitedByMobile(String mobileNumber) {
        long recentCount = otpAttemptRepository.countRecentByMobile(
                mobileNumber, LocalDateTime.now().minusHours(1));
        return recentCount >= authProps.getRateLimit().getOtpRequestsPerMobilePerHour();
    }

    private String generateSecureOtp() {
        int length = authProps.getOtp().getLength();
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * Compares two hex hashes WITHOUT leaking how much of the value matched.
     *
     * <p>String.equals returns as soon as two characters differ, so the time it takes reveals the length
     * of the shared prefix. Against an OTP that is a usable oracle: an attacker who can measure the
     * response can recover the correct hash one character at a time instead of guessing 10^6 codes.
     * MessageDigest.isEqual always inspects every byte.
     *
     * <p>Null-safe because a missing stored hash must fail closed rather than throw.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String hashValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public enum OtpVerificationResult {
        SUCCESS,
        INVALID,
        EXPIRED_OR_NOT_FOUND,
        MAX_ATTEMPTS_EXCEEDED
    }
}
