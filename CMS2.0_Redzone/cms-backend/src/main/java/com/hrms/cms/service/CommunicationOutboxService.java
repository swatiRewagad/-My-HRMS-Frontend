package com.hrms.cms.service;

import com.hrms.cms.entity.CommunicationOutbox;
import com.hrms.cms.repository.CommunicationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Queues outbound communications and, separately, drains the queue.
 *
 * <p>QUEUEING JOINS THE CALLER'S TRANSACTION. If a closure rolls back, the letter it would have sent must
 * roll back with it — a citizen must never receive a closure letter for a closure that did not happen.
 * This is why {@link #queue} is not {@code @Async} and does no dispatch: the decision to communicate is
 * part of the workflow's atomic unit, the delivery is not.
 *
 * <p>DRAINING COMMITS PER ROW, in its own transaction ({@code REQUIRES_NEW}). One unreachable recipient
 * must not roll back the twenty messages already dispatched in the same sweep. A failed row records the
 * error and its attempt count and stays unsent, so it is retried and remains visible rather than being
 * silently dropped.
 *
 * <p>WHAT "SENT" MEANS HERE. {@code sent = true} records that {@link OutboundMessagePort} accepted the
 * message. The only adapter today is {@code LoggingOutboundMessageAdapter}, which delivers nothing and
 * says so loudly in the log. Until a real gateway adapter is supplied, a drained queue is proof that the
 * system decided correctly and addressed the right recipient — not that anyone received anything.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunicationOutboxService {

    /** Bounded so a large backlog cannot hold a transaction or the scheduler thread open indefinitely. */
    private static final int DRAIN_BATCH_SIZE = 50;

    private final CommunicationOutboxRepository outboxRepository;
    private final OutboundMessagePort outboundMessagePort;

    /**
     * Queues one communication. Participates in the caller's transaction by design — see the class note.
     *
     * @return the persisted row, so a caller can assert what it queued
     */
    @Transactional
    public CommunicationOutbox queue(CommunicationOutbox communication) {
        if (communication.getRecipient() == null || communication.getRecipient().isBlank()) {
            // Refused rather than stored: a row with no recipient can never be dispatched, so accepting
            // it would create a permanently failing queue entry that looks like a pending obligation.
            throw new IllegalArgumentException(
                    "A communication cannot be queued without a recipient (type="
                            + communication.getCommunicationType() + ")");
        }
        if (communication.getChannel() == null || communication.getChannel().isBlank()) {
            throw new IllegalArgumentException("A communication cannot be queued without a channel");
        }

        communication.setSent(false);
        communication.setSentAt(null);
        CommunicationOutbox saved = outboxRepository.save(communication);
        log.info("Queued {} over {} for ref={} (outboxId={})", saved.getCommunicationType(),
                saved.getChannel(), saved.getRelatedReference(), saved.getId());
        return saved;
    }

    /** Whether this exact communication is already queued or sent, so a retried action stays idempotent. */
    @Transactional(readOnly = true)
    public boolean alreadyQueued(String relatedReference, String communicationType, String channel) {
        return outboxRepository.existsByRelatedReferenceAndCommunicationTypeAndChannel(
                relatedReference, communicationType, channel);
    }

    @Transactional(readOnly = true)
    public List<CommunicationOutbox> findForReference(String relatedReference) {
        return outboxRepository.findByRelatedReferenceOrderByCreatedAtDesc(relatedReference);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return outboxRepository.countBySentFalse();
    }

    /**
     * Dispatches queued communications, oldest first.
     *
     * @return the number of rows successfully dispatched in this sweep
     */
    public int drain() {
        List<CommunicationOutbox> pending =
                outboxRepository.findBySentFalseOrderByCreatedAtAsc(PageRequest.of(0, DRAIN_BATCH_SIZE));
        int dispatched = 0;
        for (CommunicationOutbox row : pending) {
            if (dispatchOne(row.getId())) {
                dispatched++;
            }
        }
        if (!pending.isEmpty()) {
            log.info("Communication outbox sweep: {} of {} dispatched", dispatched, pending.size());
        }
        return dispatched;
    }

    /**
     * Dispatches a single row in its own transaction, so one failure cannot roll back the rest of the
     * sweep. Called through the repository by id rather than taking the entity, so the row is re-read
     * inside the new transaction and a concurrently-dispatched row is not sent twice.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean dispatchOne(Long id) {
        CommunicationOutbox row = outboxRepository.findById(id).orElse(null);
        if (row == null || row.isSent()) {
            return false;
        }

        row.setAttemptCount(row.getAttemptCount() + 1);
        try {
            String content = CommunicationOutbox.CHANNEL_SMS.equalsIgnoreCase(row.getChannel())
                    ? row.getSmsText()
                    : row.getBody();
            outboundMessagePort.send(row.getChannel(), row.getRecipient(), row.getSubject(),
                    content, row.getRelatedReference());
            row.setSent(true);
            row.setSentAt(LocalDateTime.now());
            row.setLastError(null);
            outboxRepository.save(row);
            return true;
        } catch (RuntimeException e) {
            // Left unsent on purpose: the obligation survives the failure and is retried next sweep.
            row.setLastError(truncate(e.getMessage()));
            outboxRepository.save(row);
            log.warn("Communication outbox dispatch failed for id={} ref={} attempt={}: {}",
                    row.getId(), row.getRelatedReference(), row.getAttemptCount(), e.getMessage());
            return false;
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
