package com.hrms.cms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * Refuses to start if the OTP is exposed in API responses outside local development.
 *
 * <p>WHAT IT GUARDS. {@code cms.auth.otp.dev-auto-populate} makes
 * {@code POST /api/v1/citizen/auth/send-otp} return the generated OTP in its own response body (and the
 * same for the email variant). Anyone able to reach that endpoint can then authenticate as any mobile
 * number, so wherever this is on there is effectively no citizen authentication at all.
 *
 * <p>WHY A STARTUP ASSERTION AND NOT A COMMENT. The setting was already documented as dev-only and
 * overridden to false in the {@code prod} profile. It was still live in every deployed environment,
 * because the OpenShift ConfigMap set {@code SPRING_PROFILES_ACTIVE=openshift} — a profile that did not
 * exist — and Spring does not warn about an unknown profile. The whole override quietly did nothing.
 * Any control that depends on a particular profile being active can fail the same way; this one depends
 * only on the flag's effective value, so it holds however the configuration is assembled.
 *
 * <p>FAIL CLOSED, LOUDLY. A misconfigured deployment should not start. Downgrading this to a warning
 * would reproduce the original defect exactly: an authentication bypass that nobody notices because
 * nothing stops. The remedy is always to set the flag false, never to relax this check.
 *
 * <p>{@code dev-local} is the sole exemption: the Playwright suite reads {@code devOtp} to obtain a
 * citizen session without an SMS gateway. That profile also uses a local database and relaxed security,
 * so it is already unsuitable for anything but a developer machine.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpExposureGuard {

    /** The only profile permitted to expose the OTP. */
    static final String DEV_LOCAL = "dev-local";

    private final AuthSecurityProperties authProps;
    private final Environment environment;

    @PostConstruct
    void verifyOtpIsNotExposed() {
        boolean exposed = authProps.getOtp().isDevAutoPopulate();
        List<String> active = Arrays.asList(environment.getActiveProfiles());

        if (!exposed) {
            log.info("OTP exposure guard: dev-auto-populate is false. Active profiles: {}",
                    active.isEmpty() ? "[default]" : active);
            return;
        }

        if (active.contains(DEV_LOCAL)) {
            log.warn("OTP exposure guard: dev-auto-populate is TRUE under the {} profile. "
                    + "send-otp will return the OTP in its response body. This is permitted ONLY for "
                    + "local development and E2E testing.", DEV_LOCAL);
            return;
        }

        throw new IllegalStateException(String.format(
                "REFUSING TO START: cms.auth.otp.dev-auto-populate is true but the active profile is %s, "
                        + "not %s. This returns the OTP in the send-otp response body, letting anyone "
                        + "authenticate as any mobile number. Set OTP_DEV_AUTO_POPULATE=false (or remove "
                        + "the override) before deploying. Do not relax this check.",
                active.isEmpty() ? "[default]" : active, DEV_LOCAL));
    }
}
