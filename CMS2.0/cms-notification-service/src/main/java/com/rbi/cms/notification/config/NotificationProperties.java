package com.rbi.cms.notification.config;

import com.rbi.cms.common.enums.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Everything about how this service dispatches, under {@code cms.notification}.
 *
 * <p>The defaults here are the safe ones, and they are set in the field initialisers rather than only in
 * yml on purpose. Real sending previously depended on the {@code dev-local} profile being active to
 * suppress it, but neither documented startup path activates that profile — the services guide starts this
 * module with a bare {@code mvn spring-boot:run}, and compose sets {@code SPRING_PROFILES_ACTIVE: docker},
 * for which no {@code application-docker.yml} exists in any module. The result was a service that would
 * attempt live SMTP whenever nobody had thought about it.</p>
 *
 * <p>So sending now takes two deliberate opt-ins — {@link #mode} of {@code SEND} <em>and</em> the channel's
 * own enable flag — and no profile can turn it on. An unconfigured deployment simulates.</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "cms.notification")
public class NotificationProperties {

    /** Safe by default: simulate unless someone has explicitly asked for real delivery. */
    @NotNull
    private DispatchMode mode = DispatchMode.SIMULATE;

    private boolean emailEnabled = false;

    /** Reserved for the SMS channel, which has no gateway implementation yet. */
    private boolean smsEnabled = false;

    private final Email email = new Email();

    /** True only when this channel should actually be dispatched rather than simulated. */
    public boolean dispatchesFor(NotificationChannel channel) {
        if (mode != DispatchMode.SEND) {
            return false;
        }
        return switch (channel) {
            case EMAIL -> emailEnabled;
            case SMS -> smsEnabled;
        };
    }

    @Getter
    @Setter
    public static class Email {

        /** Used only when the stored row has no from address of its own. */
        private String defaultFrom = "noreply@cms.rbi.org.in";

        /**
         * SMTP timeouts, in milliseconds. There were none before, so an unresponsive mail server held a
         * consumer thread until Kafka's max.poll.interval.ms evicted the whole consumer from the group.
         */
        @Positive
        private int connectTimeoutMs = 5000;

        @Positive
        private int readTimeoutMs = 10000;

        @Positive
        private int writeTimeoutMs = 10000;
    }
}
