package com.rbi.cms.notification.channel;

import com.rbi.cms.common.enums.NotificationChannel;
import com.rbi.cms.notification.repository.OutboundEmailRow;

/**
 * Sends one notification over one channel.
 *
 * <p>The seam for the deferred SMS channel: a handler is discovered by the channel it declares, so adding
 * SMS means adding one {@code @Component} — the event, the topic, the listener and the Kafka config all
 * stay as they are.</p>
 *
 * <p>Implementations throw on failure rather than reporting it. Swallowing the exception here would defeat
 * the container's retry, because the row would leave PENDING and every subsequent attempt would find
 * nothing to do.</p>
 */
public interface DispatchChannelHandler {

    NotificationChannel channel();

    /**
     * @throws Exception if dispatch failed; the caller lets this propagate so the Kafka error handler can
     *                   retry and, once attempts are exhausted, record the failure exactly once.
     */
    void dispatch(OutboundEmailRow row) throws Exception;
}
