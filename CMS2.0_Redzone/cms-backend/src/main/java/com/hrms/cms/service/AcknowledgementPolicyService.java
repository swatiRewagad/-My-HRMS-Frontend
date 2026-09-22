package com.hrms.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Decides whether an automatic acknowledgement may be sent to an inbound sender.
 *
 * Internal RBI senders are suppressed: acknowledging an internally-forwarded mail sends a
 * citizen-facing receipt to a colleague, and two RBI mailboxes acknowledging each other loop.
 * The domain list is configuration, not a literal, so it can be corrected without a rebuild.
 */
@Slf4j
@Service
public class AcknowledgementPolicyService {

    private final List<String> suppressedDomains;

    public AcknowledgementPolicyService(
            @Value("${cms.email.ack.suppressed-domains:rbi.org.in}") String suppressedDomainsCsv) {
        this.suppressedDomains = Arrays.stream(suppressedDomainsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    public boolean shouldAcknowledge(String senderEmail) {
        return !isSuppressedSender(senderEmail);
    }

    public boolean isSuppressedSender(String senderEmail) {
        if (senderEmail == null || senderEmail.isBlank()) {
            // No address to reply to; suppress rather than attempt a send.
            return true;
        }
        String email = senderEmail.toLowerCase().trim();
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return true;
        }
        String domain = email.substring(at + 1);
        return suppressedDomains.stream()
                .anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }

    public List<String> getSuppressedDomains() {
        return suppressedDomains;
    }
}
