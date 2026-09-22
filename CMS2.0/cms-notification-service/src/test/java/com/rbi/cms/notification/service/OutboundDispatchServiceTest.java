package com.rbi.cms.notification.service;

import com.rbi.cms.common.enums.DeliveryStatus;
import com.rbi.cms.common.enums.NotificationChannel;
import com.rbi.cms.common.event.NotificationDispatchEvent;
import com.rbi.cms.notification.channel.DispatchChannelHandler;
import com.rbi.cms.notification.config.DispatchMode;
import com.rbi.cms.notification.config.NotificationProperties;
import com.rbi.cms.notification.repository.OutboundEmailRepository;
import com.rbi.cms.notification.repository.OutboundEmailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboundDispatchServiceTest {

    private static final long EMAIL_ID = 77L;

    private OutboundEmailRepository repository;
    private NotificationProperties properties;
    private RecordingHandler handler;
    private OutboundDispatchService service;

    /** Captures whether dispatch was attempted, and can be told to fail. */
    private static class RecordingHandler implements DispatchChannelHandler {
        private boolean dispatched;
        private Exception failWith;

        @Override
        public NotificationChannel channel() {
            return NotificationChannel.EMAIL;
        }

        @Override
        public void dispatch(OutboundEmailRow row) throws Exception {
            dispatched = true;
            if (failWith != null) {
                throw failWith;
            }
        }
    }

    @BeforeEach
    void setUp() {
        repository = mock(OutboundEmailRepository.class);
        properties = new NotificationProperties();
        handler = new RecordingHandler();
        service = new OutboundDispatchService(repository, properties, List.of(handler));

        when(repository.find(EMAIL_ID)).thenReturn(Optional.of(pendingRow()));
        when(repository.markSent(anyLong(), any())).thenReturn(1);
        when(repository.markFailed(anyLong(), anyString(), any())).thenReturn(1);
    }

    private static OutboundEmailRow pendingRow() {
        return OutboundEmailRow.builder()
                .id(EMAIL_ID)
                .messageId("msg-77")
                .fromEmail("officer@rbi.org.in")
                .toEmail("nodal@bank.example.com")
                .subject("Comments called for")
                .body("<p>Please respond.</p>")
                .complaintNumber("CMS-20260101-ABC123")
                .deliveryStatus(DeliveryStatus.PENDING.name())
                .build();
    }

    private static NotificationDispatchEvent event() {
        return NotificationDispatchEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .channel(NotificationChannel.EMAIL)
                .recordId(EMAIL_ID)
                .complaintNumber("CMS-20260101-ABC123")
                .occurredAt(Instant.now())
                .build();
    }

    private void enableRealSending() {
        properties.setMode(DispatchMode.SEND);
        properties.setEmailEnabled(true);
    }

    @Test
    void shouldMarkSentWithoutSendingAnythingInSimulateMode() throws Exception {
        service.dispatch(event());

        assertThat(handler.dispatched)
                .as("SIMULATE must not reach the channel handler at all")
                .isFalse();
        verify(repository).markSent(eqId(), any());
    }

    @Test
    void shouldSendAndMarkSentWhenEnabled() throws Exception {
        enableRealSending();

        service.dispatch(event());

        assertThat(handler.dispatched).isTrue();
        verify(repository).markSent(eqId(), any());
    }

    /**
     * The contract that makes Kafka's retry work. Recording FAILED here would take the row out of PENDING,
     * so the first retry's conditional UPDATE would match nothing and three attempts would collapse into one.
     */
    @Test
    void shouldLetADispatchFailurePropagateWithoutRecordingIt() {
        enableRealSending();
        handler.failWith = new IllegalStateException("SMTP refused the connection");

        assertThatThrownBy(() -> service.dispatch(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP refused");

        verify(repository, never()).markFailed(anyLong(), anyString(), any());
        verify(repository, never()).markSent(anyLong(), any());
    }

    @Test
    void shouldDoNothingWhenTheRowIsAlreadyResolved() throws Exception {
        when(repository.find(EMAIL_ID)).thenReturn(Optional.of(OutboundEmailRow.builder()
                .id(EMAIL_ID)
                .toEmail("nodal@bank.example.com")
                .deliveryStatus(DeliveryStatus.SENT.name())
                .build()));
        enableRealSending();

        service.dispatch(event());

        assertThat(handler.dispatched)
                .as("a redelivered event must not send the mail a second time")
                .isFalse();
        verify(repository, never()).markSent(anyLong(), any());
    }

    @Test
    void shouldDoNothingWhenTheRowHasBeenDeleted() throws Exception {
        when(repository.find(EMAIL_ID)).thenReturn(Optional.empty());
        enableRealSending();

        service.dispatch(event());

        assertThat(handler.dispatched).isFalse();
        verify(repository, never()).markSent(anyLong(), any());
    }

    @Test
    void shouldRejectAChannelWithNoHandlerAsNonRetryable() {
        NotificationDispatchEvent smsEvent = NotificationDispatchEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .channel(NotificationChannel.SMS)
                .recordId(EMAIL_ID)
                .build();

        // IllegalArgumentException is registered as non-retryable, so this goes straight to the DLQ.
        assertThatThrownBy(() -> service.dispatch(smsEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SMS");
    }

    @Test
    void shouldRejectAnEventMissingItsRecordId() {
        NotificationDispatchEvent incomplete = NotificationDispatchEvent.builder()
                .channel(NotificationChannel.EMAIL)
                .build();

        assertThatThrownBy(() -> service.dispatch(incomplete))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRecordFailureWhenAskedTo() {
        service.recordFailure(EMAIL_ID, "SMTP refused the connection");

        verify(repository).markFailed(eqId(), anyString(), any());
    }

    private static long eqId() {
        return org.mockito.ArgumentMatchers.eq(EMAIL_ID);
    }
}
