package com.rbi.cms.workflow.config;

import com.rbi.cms.workflow.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@Profile("!dev-local")
@EnableScheduling
@RequiredArgsConstructor
public class ProcessedEventCleanupScheduler {

    private final ProcessedEventRepository processedEventRepository;

    @Value("${cms.kafka.idempotency.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${cms.kafka.idempotency.cleanup-cron:0 0 2 * * ?}")
    public void cleanupOldEvents() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = processedEventRepository.deleteEventsOlderThan(cutoff);
        log.info("[CLEANUP] Removed {} processed events older than {} days", deleted, retentionDays);
    }
}
