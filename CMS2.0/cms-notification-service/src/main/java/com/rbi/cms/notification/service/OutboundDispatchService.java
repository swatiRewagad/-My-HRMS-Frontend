package com.rbi.cms.notification.service;

import com.rbi.cms.common.enums.DeliveryStatus;
import com.rbi.cms.common.enums.NotificationChannel;
import com.rbi.cms.common.event.NotificationDispatchEvent;
import com.rbi.cms.notification.channel.DispatchChannelHandler;
import com.rbi.cms.notification.config.NotificationProperties;
import com.rbi.cms.notification.repository.OutboundEmailRepository;
import com.rbi.cms.notification.repository.OutboundEmailRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Resolves one dispatch request: find the row, decide whether to really send, and record the outcome.
 *
 * <p>Failure is signalled by throwing, never by marking the row FAILED here. Recording the failure at this
 * point would move the row out of PENDING, so the container's first retry would find nothing to do and the
 * three configured attempts would collapse into one. FAILED is written once, by the recoverer, after
 * retries are exhausted.</p>
 */
@Slf4j
@Service
public class OutboundDispatchService {

    private final OutboundEmailRepository emailRepository;
    private final NotificationProperties properties;
    private final List<DispatchChannelHandler> handlers;

    public OutboundDispatchService(OutboundEmailRepository emailRepository,
                                   NotificationProperties properties,
                                   List<DispatchChannelHandler> handlers) {
        this.emailRepository = emailRepository;
        this.properties = properties;
        this.handlers = List.copyOf(handlers);
    }

    /**
     * Resolved per request rather than indexed once at construction, so the lookup never depends on what a
     * handler reported at the moment this bean happened to be built.
     */
    private Optional<DispatchChannelHandler> handlerFor(NotificationChannel channel) {
        return handlers.stream()
                .filter(handler -> channel == handler.channel())
                .findFirst();
    }

    /**
     * @throws IllegalArgumentException for anything a retry could never fix — the error handler treats it
     *                                 as non-retryable and routes the message straight to the DLQ.
     * @throws Exception               if dispatch failed in a way worth retrying.
     */
    public void dispatch(NotificationDispatchEvent event) throws Exception {
        if (event == null || event.getRecordId() == null || event.getChannel() == null) {
            throw new IllegalArgumentException("Dispatch request is missing its channel or record id");
        }

        DispatchChannelHandler handler = handlerFor(event.getChannel())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No handler is registered for channel " + event.getChannel()));

        Optional<OutboundEmailRow> found = emailRepository.find(event.getRecordId());
        if (found.isEmpty()) {
            // Retrying cannot conjure a deleted row.
            log.warn("Dispatch request {} refers to email {}, which no longer exists",
                    event.getEventId(), event.getRecordId());
            return;
        }

        OutboundEmailRow row = found.get();
        if (!DeliveryStatus.PENDING.name().equalsIgnoreCase(row.getDeliveryStatus())) {
            // The redelivery branch: a duplicate event, or a row somebody else already resolved.
            log.info("Email {} is already {}, so dispatch request {} needs no action",
                    row.getId(), row.getDeliveryStatus(), event.getEventId());
            return;
        }

        if (!properties.dispatchesFor(event.getChannel())) {
            log.info("Simulating {} dispatch for email {} (mode={}, so nothing is actually sent)",
                    event.getChannel(), row.getId(), properties.getMode());
            recordSent(row.getId());
            return;
        }

        handler.dispatch(row);
        recordSent(row.getId());
    }

    /**
     * Records a failed dispatch. Called by the recoverer once retries are exhausted, so it runs at most once
     * per request.
     */
    public void recordFailure(long recordId, String error) {
        int updated = emailRepository.markFailed(recordId, error, LocalDateTime.now());
        if (updated == 0) {
            log.warn("Email {} was no longer pending, so the failure was not recorded against it", recordId);
        } else {
            log.warn("Email {} failed to dispatch: {}", recordId, error);
        }
    }

    private void recordSent(long recordId) {
        int updated = emailRepository.markSent(recordId, LocalDateTime.now());
        if (updated == 0) {
            // Someone resolved it between the read and the write; the send already happened, so this is
            // information rather than a problem.
            log.info("Email {} was resolved concurrently; leaving its recorded outcome alone", recordId);
        }
    }
}
