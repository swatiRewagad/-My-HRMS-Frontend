package com.hrms.cms.controller;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.service.CaptchaService;
import com.hrms.cms.service.CaptchaService.CaptchaChallenge;
import com.hrms.cms.service.CooloffService;
import com.hrms.cms.service.CooloffService.CooloffStatus;
import com.hrms.cms.service.CitizenSessionService;
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
    private final AuthSecurityProperties authProps;

    private static final String FINGERPRINT_COOKIE = "cms_fp";

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

        if (mobile == null || !mobile.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_MOBILE",
                    "message", "Enter a valid 10-digit Indian mobile number starting with 6-9."));
        }

        if (!captchaService.verifyCaptcha(captchaToken, captchaAnswer)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_CAPTCHA",
                    "message", "Invalid CAPTCHA. Please try again."));
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

        if (otpService.isRateLimitedByMobile(mobile)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RATE_LIMITED",
                    "message", "OTP request limit reached. Try again later."));
        }

        String sessionId = UUID.randomUUID().toString();
        String otp = otpService.generateOtp(mobile, sessionId, "SMS", null);

        // TODO: Integrate with actual SMS gateway (environment.integrations.smsGateway)
        log.info("OTP for {}: {} (would be sent via SMS in production)", mobile, otp);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "OTP sent to your mobile number.",
                "sessionId", sessionId,
                "expiresInSeconds", authProps.getOtp().getExpiryMinutes() * 60));
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

        if (!captchaService.verifyCaptcha(captchaToken, captchaAnswer)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_CAPTCHA",
                    "message", "Invalid CAPTCHA. Please try again."));
        }

        if (!sessionService.isEmailVerifiedForMobile(mobile, email)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "EMAIL_NOT_VERIFIED",
                    "message", "This email has not been verified for this mobile number. Please verify your email first."));
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

        if (otpService.isRateLimitedByMobile(mobile)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "RATE_LIMITED",
                    "message", "OTP request limit reached. Try again later."));
        }

        String sessionId = UUID.randomUUID().toString();
        String otp = otpService.generateOtp(mobile, sessionId, "EMAIL", email);

        // TODO: Integrate with actual SMTP service (environment.integrations.smtp)
        log.info("Email OTP for {} to {}: {} (would be sent via email in production)", mobile, email, otp);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "OTP sent to your verified email address.",
                "sessionId", sessionId,
                "expiresInSeconds", authProps.getOtp().getExpiryMinutes() * 60));
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
                        "message", "OTP has expired. Please request a new one."));
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

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token != null) {
            sessionService.invalidateSession(token);
        }
        return ResponseEntity.ok(Map.of("success", true));
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
