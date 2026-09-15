package com.hrms.cms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "cms.auth")
@Getter @Setter
public class AuthSecurityProperties {

    private Otp otp = new Otp();
    private Captcha captcha = new Captcha();
    private Cooloff cooloff = new Cooloff();
    private RateLimit rateLimit = new RateLimit();
    private Consent consent = new Consent();

    @Getter @Setter
    public static class Otp {
        private int length = 6;
        private int expiryMinutes = 5;
        private int maxResendPerHour = 5;
        private int maxVerifyAttemptsPerOtp = 3;
        /** UST8: minimum gap between OTP requests for the same mobile. */
        private int resendCooldownSeconds = 120;
        private boolean devAutoPopulate = false;
    }

    @Getter @Setter
    public static class Captcha {
        private int length = 6;
        private int expiryMinutes = 5;
        private int imageWidth = 200;
        private int imageHeight = 60;
    }

    @Getter @Setter
    public static class Cooloff {
        private List<Integer> progressionSeconds = List.of(30, 60, 120, 300, 600);
        private int maxCeilingSeconds = 600;
        private int resetAfterMinutes = 60;
    }

    @Getter @Setter
    public static class RateLimit {
        private int otpRequestsPerMobilePerHour = 5;
        private int otpRequestsPerIpPerHour = 20;
        private int loginAttemptsPerIpPerMinute = 10;
    }

    @Getter @Setter
    public static class Consent {
        /**
         * UST5: bump this whenever the DPDP notice wording changes. Existing consents then stop
         * counting as current, so citizens are re-asked instead of being bound by a notice they
         * never saw.
         */
        private String version = "DPDP_2023_V1";
    }
}
