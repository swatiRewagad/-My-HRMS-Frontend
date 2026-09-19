package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ticks the communication-outbox drain, dispatching queued email and SMS.
 *
 * <p>Cron comes from a property rather than being compiled in, following {@link AaEscalationScheduler}.
 * The drain is additionally gated on a SYSTEM_CONFIG flag so an operator can stop dispatch without a
 * redeploy — and so a {@code @SpringBootTest} booting the full context does not start sending.
 *
 * <p>The gate DEFAULTS TO ENABLED, unlike the statutory guards in this codebase. Those default off because
 * enforcing an unverified legal limit is worse than not enforcing it; here the opposite holds — a queued
 * closure communication that is never drained is an obligation silently unmet. The transport is a no-op
 * adapter today, so draining is safe.
 *
 * <p>Failures are caught here so one bad tick cannot kill the schedule for every subsequent one. Per-row
 * failures are already isolated inside {@link CommunicationOutboxService#dispatchOne}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommunicationOutboxScheduler {

    static final String CFG_DISPATCH_ENABLED = "cms.communication.outbox.dispatch_enabled";

    private final CommunicationOutboxService outboxService;
    private final SystemConfigService systemConfigService;

    @Scheduled(cron = "${cms.communication.outbox.cron:0 */2 * * * *}")
    public void drainOutbox() {
        try {
            if (!systemConfigService.getBoolean(CFG_DISPATCH_ENABLED, true)) {
                return;
            }
            outboxService.drain();
        } catch (Exception e) {
            log.error("Communication outbox drain failed: {}", e.getMessage(), e);
        }
    }
}
