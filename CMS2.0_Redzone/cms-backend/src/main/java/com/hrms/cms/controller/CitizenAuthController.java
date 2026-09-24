package com.hrms.cms.controller;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.service.CaptchaService;
import com.hrms.cms.service.CaptchaService.CaptchaChallenge;
import com.hrms.cms.service.ConsentService;
import com.hrms.cms.service.CooloffService;
import com.hrms.cms.service.CooloffService.CooloffStatus;
import com.hrms.cms.service.CitizenSessionService;
import com.hrms.cms.service.EncryptionKeyService;
import com.hrms.cms.service.OtpService;
import com.hrms.cms.service.OtpService.OtpVerificationResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/citizen/auth")
@RequiredArgsConstructor
public class CitizenAuthController {

    private final OtpService otpService;
    private final CaptchaService captchaService;
    private final CooloffService cooloffService;
    private final CitizenSessionService sessionService;
    private final EncryptionKeyService encryptionKeyService;
    private final ConsentService consentService;
    private final AuthSecurityProperties authProps;

    private static final String FINGERPRINT_COOKIE = "cms_fp";

    /**
     * Citizen-facing refusal wording, stated once so every path that refuses for the same reason says the
     * same thing.
     *
     * <p>WHY THE RESEND MESSAGE CARRIES NO FIGURE. It used to read "Please wait before requesting another
     * OTP" with the seconds only in a sibling field, so the citizen was never actually told how long — and
     * the portal composed its own sentence from {@code retryAfterSeconds}, giving two different wordings
     * for one rule. The gap itself is configurable ({@code cms.auth.otp.resend_cooldown_seconds}), so the
     * minutes are interpolated from what the server enforces rather than written into the literal.
     * {@code retryAfterSeconds} is still returned, because a countdown needs seconds, not minutes.
     */
    static final String MSG_MOBILE_REQUIRED = "Mobile number is required to request OTP.";

    /**
     * The expiry refusal. QA quotes it WITHOUT the closing full stop
     * ("OTP has expired. Please request a new one") while quoting the resend refusal WITH one. The
     * product's sentence is kept terminated — an unterminated sentence next to a terminated one reads as
     * a typo, and QA's own quote is satisfied as a prefix either way. The punctuation itself is raised as
     * an open question rather than guessed at; it is the same class as the title-case-vs-seeded-lowercase
     * question already open with the product owner.
     */
    static final String MSG_OTP_EXPIRED = "OTP has expired. Please request a new one.";

    /** Refusal for a resend inside the cooldown. {0} is the configured gap in whole minutes. */
    static final String MSG_RESEND_TOO_SOON_TEMPLATE = "OTP can only be regenerated after %s minutes.";

    /**
     * Renders the cooldown refusal from the CONFIGURED gap.
     *
     * <p>Seconds are rounded UP to whole minutes: a 90-second gap must not be described as "after 1
     * minute", because a citizen who waits exactly that long is still refused.
     */
    static String resendTooSoonMessage(int cooldownSeconds) {
        int minutes = Math.max(1, (int) Math.ceil(cooldownSeconds / 60.0));
        return String.format(MSG_RESEND_TOO_SOON_TEMPLATE, minutes);
    }

    @GetMapping("/captcha")
    public ResponseEntity<?> getCaptcha(@RequestParam(defaultValue = "VISUAL") String type) {
        CaptchaChallenge challenge;
        if ("MATH".equalsIgnoreCase(type)) {
            challenge = captchaService.generateMathCaptcha();
        } else {
            challenge = captchaService.generateVisualCaptcha();
        }
        return ResponseEntity.ok(Map.of(
                "token", challenge.token(),
                "imageData", challenge.imageData() != null ? challenge.imageData() : "",
                "audioQuestion", challenge.audioQuestion() != null ? challenge.audioQuestion() : "",
                "type", challenge.type()
        ));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        String mobile = body.get("mobile");
        String captchaToken = body.get("captchaToken");
        String captchaAnswer = body.get("captchaAnswer");

        // An ABSENT mobile and a MALFORMED one are different problems and must not share a message.
        // "Enter a valid 10-digit Indian mobile number starting with 6-9" tells a citizen who typed
        // nothing that what they typed was wrong, which is both untrue and unactionable.
        if (mobile == null || mobile.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MOBILE_REQUIRED",
                    "message", MSG_MOBILE_REQUIRED));
        }

        if (!mobile.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_MOBILE",
                    "message", "Enter a valid 10-digit Indian mobile number starting with 6-9."));
        }

        String fingerprint = resolveFingerprint(request, response);
        String clientIp = getClientIp(request);

        // UST4/UST6: cool-off is checked before the CAPTCHA so a locked-out client cannot keep burning
        // through challenges, and a wrong CAPTCHA now counts as a failed attempt — otherwise the
        // CAPTCHA could be brute-forced indefinitely without ever tripping the lockout.
        CooloffStatus cooloff = cooloffService.checkCooloff(fingerprint, clientIp, mobile);
        if (cooloff.active()) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "COOLOFF_ACTIVE",
                    "message", "Too many attempts. Please wait.",
                    "retryAfterSeconds", cooloff.remainingSeconds()));
        }

        if (!captchaService.verifyCaptcha(captchaToken, captchaAnswer)) {
            cooloffService.recordFailedAttempt(fingerprint, clientIp, mobile);
            CooloffStatus afterFailure = cooloffService.checkCooloff(fingerprint, clientIp, mobile);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_CAPTCHA",
                    "message", "Invalid CAPTCHA. Please try again.",
                    "cooloffActive", afterFailure.active(),
                    "retryAfterSeconds", afterFailure.remainingSeconds()));
        }

        if (otpService.isRateLimitedByMobile(mobile)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RATE_LIMITED",
                    "message", "OTP request limit reached. Try again later."));
        }

        int resendWait = otpService.resendCooldownRemaining(mobile);
        if (resendWait > 0) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RESEND_COOLDOWN",
                    "message", resendTooSoonMessage(otpService.resendCooldownSeconds()),
                    "retryAfterSeconds", resendWait));
        }

        // UST5: this endpoint also serves the complaint tracker, where no consent is collected because
        // no new personal data is processed. Consent is therefore recorded when offered, and *enforced*
        // at complaint submission — the point at which processing actually begins.
        if (consentGiven(body)) {
            consentService.recordConsent(mobile, body.get("locale"), clientIp, request.getHeader("User-Agent"));
        }

        String sessionId = UUID.randomUUID().toString();
        // Generation now DISPATCHES the code too (OtpService.dispatchOtp). The "TODO: integrate with an
        // actual SMS gateway" that stood here meant nothing was ever addressed to the handset.
        String otp = otpService.generateOtp(mobile, sessionId, "SMS", null, body.get("locale"));

        Map<String, Object> responseBody = new HashMap<>(Map.of(
                "success", true,
                "message", "OTP sent to your mobile number.",
                "sessionId", sessionId,
                "expiresInSeconds", otpService.expiryMinutes() * 60,
                // The portal shows the resend countdown, and it must be the server's gap rather than a
                // number the client picked: the client used to default to 120s regardless of configuration,
                // so under dev-local (cooldown 0) it disabled its own Resend button for two minutes for a
                // rule the server was not applying.
                "resendAfterSeconds", otpService.resendCooldownSeconds()));

        if (authProps.getOtp().isDevAutoPopulate()) {
            responseBody.put("devOtp", otp);
        }

        return ResponseEntity.ok(responseBody);
    }

    /**
     * Re-sends an OTP to a mobile that already has a request in flight (QA cases 8-12).
     *
     * <p>WHY A SEPARATE ENDPOINT. The portal's "Resend OTP" button called nothing: it reset the component
     * to step 1 and reloaded the CAPTCHA, so a citizen who had not received a code was sent back to
     * re-solve a CAPTCHA and re-enter their number, and no new code was ever issued. The QA case requires
     * the click to deliver a NEW OTP to the number already entered.
     *
     * <p>NO CAPTCHA, AND WHY THAT IS SAFE. A citizen who already solved one and is waiting for the code
     * should not have to solve another. The endpoint is therefore constrained in three ways instead: it
     * only accepts a mobile with a recent CAPTCHA-gated request ({@code hasRecentOtpRequest}), the
     * server-side resend cooldown applies exactly as on {@code send-otp}, and the per-mobile hourly rate
     * limit applies too. Rapid clicking therefore hits the cooldown, which is QA case 10 — the restriction
     * and the 2-minute message are the SAME control, not a separate attempt counter.
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        String mobile = body.get("mobile");

        if (mobile == null || mobile.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MOBILE_REQUIRED",
                    "message", MSG_MOBILE_REQUIRED));
        }
        if (!mobile.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_MOBILE",
                    "message", "Enter a valid 10-digit Indian mobile number starting with 6-9."));
        }

        String fingerprint = resolveFingerprint(request, response);
        String clientIp = getClientIp(request);

        CooloffStatus cooloff = cooloffService.checkCooloff(fingerprint, clientIp, mobile);
        if (cooloff.active()) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "COOLOFF_ACTIVE",
                    "message", "Too many attempts. Please wait.",
                    "retryAfterSeconds", cooloff.remainingSeconds()));
        }

        // Without this a CAPTCHA-free endpoint would issue OTPs to arbitrary numbers on demand. A resend is
        // only ever a continuation of a request that DID pass the CAPTCHA.
        if (!otpService.hasRecentOtpRequest(mobile)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "NO_ACTIVE_REQUEST",
                    "message", "Please request an OTP first."));
        }

        // Checked BEFORE the cooldown: an exhausted hourly allowance is a different refusal from "too
        // soon", and reporting the 2-minute wait to someone who has used up the hour would be a lie.
        if (otpService.isRateLimitedByMobile(mobile)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RATE_LIMITED",
                    "message", "OTP request limit reached. Try again later."));
        }

        int resendWait = otpService.resendCooldownRemaining(mobile);
        if (resendWait > 0) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RESEND_COOLDOWN",
                    "message", resendTooSoonMessage(otpService.resendCooldownSeconds()),
                    "retryAfterSeconds", resendWait));
        }

        String sessionId = UUID.randomUUID().toString();
        String otp = otpService.generateOtp(mobile, sessionId, "SMS", null, body.get("locale"));

        Map<String, Object> responseBody = new HashMap<>(Map.of(
                "success", true,
                "message", "A new OTP has been sent to your mobile number.",
                "sessionId", sessionId,
                "expiresInSeconds", otpService.expiryMinutes() * 60,
                "resendAfterSeconds", otpService.resendCooldownSeconds()));

        if (authProps.getOtp().isDevAutoPopulate()) {
            responseBody.put("devOtp", otp);
        }

        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/send-otp-email")
    public ResponseEntity<?> sendOtpViaEmail(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        String mobile = body.get("mobile");
        String email = body.get("email");
        String captchaToken = body.get("captchaToken");
        String captchaAnswer = body.get("captchaAnswer");

        if (mobile == null || !mobile.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_MOBILE",
                    "message", "Enter a valid 10-digit Indian mobile number."));
        }

        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_EMAIL",
                    "message", "Enter a valid email address."));
        }

        String fingerprint = resolveFingerprint(request, response);
        String clientIp = getClientIp(request);

        CooloffStatus cooloff = cooloffService.checkCooloff(fingerprint, clientIp, mobile);
        if (cooloff.active()) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "COOLOFF_ACTIVE",
                    "message", "Too many attempts. Please wait.",
                    "retryAfterSeconds", cooloff.remainingSeconds()));
        }

        if (!captchaService.verifyCaptcha(captchaToken, captchaAnswer)) {
            cooloffService.recordFailedAttempt(fingerprint, clientIp, mobile);
            CooloffStatus afterFailure = cooloffService.checkCooloff(fingerprint, clientIp, mobile);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_CAPTCHA",
                    "message", "Invalid CAPTCHA. Please try again.",
                    "cooloffActive", afterFailure.active(),
                    "retryAfterSeconds", afterFailure.remainingSeconds()));
        }

        if (!sessionService.isEmailVerifiedForMobile(mobile, email)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "EMAIL_NOT_VERIFIED",
                    "message", "This email has not been verified for this mobile number. Please verify your email first."));
        }

        if (otpService.isRateLimitedByMobile(mobile)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RATE_LIMITED",
                    "message", "OTP request limit reached. Try again later."));
        }

        int resendWait = otpService.resendCooldownRemaining(mobile);
        if (resendWait > 0) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RESEND_COOLDOWN",
                    "message", resendTooSoonMessage(otpService.resendCooldownSeconds()),
                    "retryAfterSeconds", resendWait));
        }

        if (consentGiven(body)) {
            consentService.recordConsent(mobile, body.get("locale"), clientIp, request.getHeader("User-Agent"));
        }

        String sessionId = UUID.randomUUID().toString();
        String otp = otpService.generateOtp(mobile, sessionId, "EMAIL", email, body.get("locale"));

        Map<String, Object> emailBody = new HashMap<>(Map.of(
                "success", true,
                "message", "OTP sent to your verified email address.",
                "sessionId", sessionId,
                "expiresInSeconds", otpService.expiryMinutes() * 60,
                "resendAfterSeconds", otpService.resendCooldownSeconds()));

        if (authProps.getOtp().isDevAutoPopulate()) {
            emailBody.put("devOtp", otp);
        }

        return ResponseEntity.ok(emailBody);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        String mobile = body.get("mobile");
        String otp = body.get("otp");
        String sessionId = body.get("sessionId");

        if (mobile == null || otp == null || otp.length() != authProps.getOtp().getLength()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_INPUT",
                    "message", "Invalid mobile or OTP format."));
        }

        String fingerprint = resolveFingerprint(request, response);
        String clientIp = getClientIp(request);

        OtpVerificationResult result = otpService.verifyOtp(mobile, otp);

        switch (result) {
            case SUCCESS -> {
                cooloffService.clearCooloff(fingerprint, clientIp);
                String token = sessionService.createSession(mobile);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "token", token,
                        "expiresInMinutes", 15));
            }
            case INVALID -> {
                cooloffService.recordFailedAttempt(fingerprint, clientIp, mobile);
                CooloffStatus status = cooloffService.checkCooloff(fingerprint, clientIp, mobile);
                return ResponseEntity.status(401).body(Map.of(
                        "error", "INVALID_OTP",
                        "message", "Incorrect OTP. Please try again.",
                        "cooloffActive", status.active(),
                        "retryAfterSeconds", status.remainingSeconds()));
            }
            case EXPIRED_OR_NOT_FOUND -> {
                return ResponseEntity.status(410).body(Map.of(
                        "error", "OTP_EXPIRED",
                        "message", MSG_OTP_EXPIRED));
            }
            case MAX_ATTEMPTS_EXCEEDED -> {
                cooloffService.recordFailedAttempt(fingerprint, clientIp, mobile);
                return ResponseEntity.status(429).body(Map.of(
                        "error", "MAX_ATTEMPTS",
                        "message", "Too many incorrect attempts. Please request a new OTP."));
            }
            default -> {
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "UNKNOWN",
                        "message", "An unexpected error occurred."));
            }
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> initiateEmailVerification(@RequestBody Map<String, String> body) {
        String mobile = body.get("mobile");
        String email = body.get("email");

        if (mobile == null || !mobile.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_MOBILE"));
        }
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_EMAIL"));
        }

        String result = sessionService.initiateEmailVerification(mobile, email);

        // TODO: Send verification link via SMTP
        log.info("Email verification initiated for {} -> {} token: {}", mobile, email, result);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Verification link sent to your email. Please check your inbox."));
    }

    @GetMapping("/verify-email/confirm")
    public ResponseEntity<?> confirmEmailVerification(@RequestParam String token) {
        boolean verified = sessionService.confirmEmailVerification(token);
        if (verified) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Email verified successfully. You can now use it for OTP delivery."));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_TOKEN",
                "message", "Verification link is invalid or expired."));
    }

    @PostMapping("/validate-session")
    public ResponseEntity<?> validateSession(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || !sessionService.isSessionValid(token)) {
            return ResponseEntity.status(401).body(Map.of("valid", false));
        }
        return ResponseEntity.ok(Map.of("valid", true));
    }

    @PostMapping("/encryption-key")
    public ResponseEntity<?> getEncryptionKey(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String sessionId = body.get("sessionId");

        if (token == null || !sessionService.isSessionValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED"));
        }

        String key = encryptionKeyService.deriveSessionKey(sessionId != null ? sessionId : token);
        return ResponseEntity.ok(Map.of("key", key));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token != null) {
            sessionService.invalidateSession(token);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    private boolean consentGiven(Map<String, String> body) {
        return "true".equalsIgnoreCase(body.get("consentGiven"));
    }

    private String resolveFingerprint(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (FINGERPRINT_COOKIE.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        String fp = hashValue(UUID.randomUUID().toString());
        Cookie cookie = new Cookie(FINGERPRINT_COOKIE, fp);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(86400 * 30);
        response.addCookie(cookie);
        return fp;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
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
}
