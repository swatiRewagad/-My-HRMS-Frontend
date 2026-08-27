package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP and total concurrent-connection caps (brief: "Per-IP connection rate limit, max
 * concurrent connections"). Deliberately not a full leaky-bucket rate limiter — a single
 * relay/DC mail gateway sending a burst of redirected mail is expected, legitimate traffic; the
 * caps exist to bound worst-case resource use, not to throttle normal operation.
 */
@Component
@RequiredArgsConstructor
public class ConnectionTracker {

    private final MailIntakeProperties properties;
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> perIpConnections = new ConcurrentHashMap<>();

    /** Returns true and reserves a slot if under both caps; false (no slot reserved) otherwise. */
    public boolean tryAcquire(String remoteIp) {
        MailIntakeProperties.Listener cfg = properties.getListener();

        if (totalConnections.get() >= cfg.getMaxConcurrentConnections()) {
            return false;
        }
        AtomicInteger perIp = perIpConnections.computeIfAbsent(remoteIp, k -> new AtomicInteger(0));
        int current = perIp.incrementAndGet();
        if (current > cfg.getMaxConnectionsPerIp()) {
            perIp.decrementAndGet();
            return false;
        }
        totalConnections.incrementAndGet();
        return true;
    }

    public void release(String remoteIp) {
        totalConnections.decrementAndGet();
        perIpConnections.computeIfPresent(remoteIp, (k, v) -> {
            int remaining = v.decrementAndGet();
            return remaining <= 0 ? null : v;
        });
    }

    public int currentTotal() {
        return totalConnections.get();
    }
}
