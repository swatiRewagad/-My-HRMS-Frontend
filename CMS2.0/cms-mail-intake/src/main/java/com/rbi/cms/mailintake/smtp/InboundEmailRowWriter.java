package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailEvent;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.repository.InboundEmailEventRepository;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from {@link InboundEmailIngestService} deliberately: the insert attempt needs its own
 * fresh transaction (REQUIRES_NEW) so that when it fails on the content_sha256 unique constraint
 * (two connections racing to deliver the same bytes), only this small transaction rolls back —
 * not whatever transactional context the caller is in. Calling a REQUIRES_NEW method via
 * self-invocation in the same class silently skips the proxy and doesn't get a new transaction at
 * all, which is exactly why this is a separate bean rather than a private method.
 */
@Component
@RequiredArgsConstructor
class InboundEmailRowWriter {

    private final InboundEmailRepository emailRepository;
    private final InboundEmailEventRepository eventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    InboundEmail insertReceived(String contentSha256, String envelopeFrom, String envelopeTo,
                                 String remoteIp, String rawStoreUri, long rawSizeBytes) {
        InboundEmail email = InboundEmail.builder()
                .contentSha256(contentSha256)
                .envelopeFrom(envelopeFrom)
                .envelopeTo(envelopeTo)
                .remoteIp(remoteIp)
                .rawStoreUri(rawStoreUri)
                .rawSizeBytes(rawSizeBytes)
                .status(InboundEmailStatus.RECEIVED)
                .build();
        email = emailRepository.save(email);

        eventRepository.save(InboundEmailEvent.builder()
                .emailId(email.getId())
                .fromStatus(null)
                .toStatus(InboundEmailStatus.RECEIVED)
                .actor("system:smtp-listener")
                .detail("Accepted " + rawSizeBytes + " bytes from " + remoteIp)
                .build());

        return email;
    }
}
