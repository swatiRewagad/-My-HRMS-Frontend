package com.hrms.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * The no-op adapter that stands in until a real gateway is supplied.
 *
 * <p>Records the dispatch decision and returns successfully. It does NOT deliver anything, and it is
 * deliberately loud about that in the log so a green test run is never mistaken for working email or
 * SMS.
 *
 * <p>Selected when {@code cms.notification.dispatch.mode} is {@code noop}, which is also the default
 * when the property is absent — so tests and any environment that has not opted in stay silent. Set
 * the property to {@code kafka} to activate {@link KafkaOutboundMessageAdapter} instead.
 *
 * <p>PII: the recipient is logged because an operator diagnosing "the complainant says they were never
 * told" needs to know who the system addressed. The BODY is not logged — a complaint body contains
 * the complainant's account details and grievance narrative, and notification volume means it would
 * end up in aggregated log storage. Only its length is recorded, which is enough to tell an empty
 * template render from a populated one.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "cms.notification.dispatch.mode", havingValue = "noop",
        matchIfMissing = true)
public class LoggingOutboundMessageAdapter implements OutboundMessagePort {

    @Override
    public Outcome send(String channel, String recipient, String subject, String body,
                        String relatedReference, String dispatchRef) {
        if (recipient == null || recipient.isBlank()) {
            throw new OutboundDispatchException("No recipient supplied for " + channel + " dispatch");
        }

        log.info("NO GATEWAY CONFIGURED — {} not delivered. recipient={}, ref={}, subject='{}', bodyLength={}",
                channel, recipient, relatedReference, subject, body == null ? 0 : body.length());

        // SENT, not QUEUED: nothing else will ever settle this attempt, and leaving it PENDING would
        // make every local run accumulate rows that look like a stuck queue.
        return Outcome.SENT;
    }
}
