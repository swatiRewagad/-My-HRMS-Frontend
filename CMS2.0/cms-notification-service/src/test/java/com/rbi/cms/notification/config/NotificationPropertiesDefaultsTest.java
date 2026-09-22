package com.rbi.cms.notification.config;

import com.rbi.cms.common.enums.NotificationChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The regression test for "a deployment nobody configured must not mail real complainants".
 *
 * <p>Asserts the defaults on the object itself, not as bound from yml, because that is the guarantee that
 * matters: any environment, any profile, any missing config file still yields SIMULATE. Sending used to be
 * suppressed by a {@code dev-local}-only bean, and neither documented startup path activates that profile.</p>
 */
class NotificationPropertiesDefaultsTest {

    @Test
    void shouldSimulateUntilExplicitlyToldOtherwise() {
        NotificationProperties properties = new NotificationProperties();

        assertThat(properties.getMode()).isEqualTo(DispatchMode.SIMULATE);
        assertThat(properties.isEmailEnabled()).isFalse();
        assertThat(properties.isSmsEnabled()).isFalse();
        assertThat(properties.dispatchesFor(NotificationChannel.EMAIL)).isFalse();
        assertThat(properties.dispatchesFor(NotificationChannel.SMS)).isFalse();
    }

    @Test
    void shouldRequireBothTheModeAndTheChannelFlag() {
        NotificationProperties properties = new NotificationProperties();

        properties.setMode(DispatchMode.SEND);
        assertThat(properties.dispatchesFor(NotificationChannel.EMAIL))
                .as("SEND alone must not be enough - the channel has to be enabled too")
                .isFalse();

        properties.setEmailEnabled(true);
        assertThat(properties.dispatchesFor(NotificationChannel.EMAIL)).isTrue();
        assertThat(properties.dispatchesFor(NotificationChannel.SMS))
                .as("enabling email must not enable SMS")
                .isFalse();
    }

    @Test
    void shouldNotDispatchWhenOnlyTheChannelFlagIsSet() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEmailEnabled(true);

        assertThat(properties.getMode()).isEqualTo(DispatchMode.SIMULATE);
        assertThat(properties.dispatchesFor(NotificationChannel.EMAIL)).isFalse();
    }

    @Test
    void shouldCarrySmtpTimeoutsSoAHungServerCannotStallAConsumer() {
        NotificationProperties.Email email = new NotificationProperties().getEmail();

        assertThat(email.getConnectTimeoutMs()).isPositive();
        assertThat(email.getReadTimeoutMs()).isPositive();
        assertThat(email.getWriteTimeoutMs()).isPositive();
        assertThat(email.getDefaultFrom()).isNotBlank();
    }
}
