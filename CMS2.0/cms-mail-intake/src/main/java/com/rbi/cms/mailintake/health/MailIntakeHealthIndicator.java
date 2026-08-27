package com.rbi.cms.mailintake.health;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import com.rbi.cms.mailintake.smtp.SmtpServer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Three checks, any one of which takes the whole indicator DOWN: the SMTP listener is actually
 * bound (a crashed/never-started Netty bootstrap doesn't otherwise show up as unhealthy — the
 * servlet container is happy regardless), the raw-message store directory is writable (durability
 * depends on it — see RawMessageStore), and the RECEIVED+due-FAILED backlog is under
 * cms.mail.intake.health.queue-depth-warning-threshold (a growing queue almost always means the
 * parser worker pool is stuck, not that traffic merely spiked).
 */
@Component
@RequiredArgsConstructor
public class MailIntakeHealthIndicator implements HealthIndicator {

    private final SmtpServer smtpServer;
    private final InboundEmailRepository emailRepository;
    private final MailIntakeProperties properties;

    @Override
    public Health health() {
        boolean listenerBound = isListenerBound();
        boolean storeWritable = isStoreWritable();
        long queueDepth = emailRepository.countByStatus(InboundEmailStatus.RECEIVED)
                + emailRepository.countByStatusAndNextAttemptAtBefore(InboundEmailStatus.FAILED, Instant.now());
        int threshold = properties.getHealth().getQueueDepthWarningThreshold();
        boolean queueOk = queueDepth <= threshold;

        Health.Builder builder = (listenerBound && storeWritable && queueOk) ? Health.up() : Health.down();
        return builder
                .withDetail("listenerBound", listenerBound)
                .withDetail("storeWritable", storeWritable)
                .withDetail("queueDepth", queueDepth)
                .withDetail("queueDepthWarningThreshold", threshold)
                .build();
    }

    private boolean isListenerBound() {
        try {
            return smtpServer.isRunning() && smtpServer.getBoundPort() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isStoreWritable() {
        try {
            Path baseDir = Paths.get(properties.getStorage().getRawMessageBasePath());
            Files.createDirectories(baseDir);
            Path marker = baseDir.resolve(".health-check");
            Files.writeString(marker, Long.toString(System.currentTimeMillis()));
            Files.delete(marker);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
