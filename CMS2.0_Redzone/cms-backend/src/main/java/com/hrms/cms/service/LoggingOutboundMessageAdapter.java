package com.hrms.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The no-op adapter that stands in until a real gateway is supplied.
 *
 * <p>Records the dispatch decision and returns successfully. It does NOT deliver anything, and it is
 * deliberately loud about that in the log so a green test run is never mistaken for working email or
 * SMS.
 *
 * <p>A real gateway adapter should be annotated {@code @Primary} so it wins injection without this
 * class being deleted — keeping the no-op available makes it possible to run local and CI environments
 * with dispatch deliberately disabled.
 *
 * <p>PII: the recipient is logged because an operator diagnosing "the complainant says they were never
 * told" needs to know who the system addressed. The BODY is not logged — a complaint body contains
 * the complainant's account details and grievance narrative, and notification volume means it would
 * end up in aggregated log storage. Only its length is recorded, which is enough to tell an empty
 * template render from a populated one.
 */
@Service
@Slf4j
public class LoggingOutboundMessageAdapter implements OutboundMessagePort {

    @Override
    public void send(String channel, String recipient, String subject, String body,
                     String relatedReference) {
        if (recipient == null || recipient.isBlank()) {
            throw new OutboundDispatchException("No recipient supplied for " + channel + " dispatch");
        }

        log.info("NO GATEWAY CONFIGURED — {} not delivered. recipient={}, ref={}, subject='{}', bodyLength={}",
                channel, recipient, relatedReference, subject, body == null ? 0 : body.length());
    }
}
