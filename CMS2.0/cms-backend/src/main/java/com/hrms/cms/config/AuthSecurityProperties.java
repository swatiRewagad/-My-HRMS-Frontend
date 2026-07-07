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

    @Getter @Setter
    public static class Otp {
        private int length = 6;
        private int expiryMinutes = 5;
        private int maxResendPerHour = 5;
        private int maxVerifyAttemptsPerOtp = 3;
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
}
