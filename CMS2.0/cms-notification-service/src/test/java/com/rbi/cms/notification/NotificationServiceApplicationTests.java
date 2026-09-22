package com.rbi.cms.notification;

import com.rbi.cms.notification.config.DispatchMode;
import com.rbi.cms.notification.config.NotificationProperties;
import com.rbi.cms.notification.repository.OutboundEmailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application context.
 *
 * <p>Modest-looking but load-bearing: this change gave the service a datasource for the first time, and the
 * likeliest way to break it is a wiring or autoconfiguration failure at startup rather than a logic error.
 * It also exercises {@code DispatchStartupCheck}, which runs as an {@code ApplicationRunner} and will fail
 * the context if the delivery columns are missing.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.sql.init.schema-locations=classpath:schema-simulated-emails.sql",
        "spring.datasource.url=jdbc:h2:mem:notification-smoke;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration",
        // No broker in this test; the listener containers retry in the background without failing startup.
        "spring.kafka.bootstrap-servers=localhost:59092"
})
class NotificationServiceApplicationTests {

    @Autowired private NotificationProperties properties;
    @Autowired private OutboundEmailRepository emailRepository;

    @Test
    void shouldStartWithADatasourceAndNoMailSender() {
        assertThat(emailRepository).isNotNull();
        // The startup check already probed these columns; calling it again proves the wiring, not the schema.
        emailRepository.assertSchema();
    }

    @Test
    void shouldStartInSimulateModeWithoutAnyOverrides() {
        assertThat(properties.getMode()).isEqualTo(DispatchMode.SIMULATE);
        assertThat(properties.isEmailEnabled()).isFalse();
    }
}
