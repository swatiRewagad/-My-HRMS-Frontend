package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.UploadLink;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.UploadLinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The S6 notification-scheduler behaviours: configurable intervals, and the evidence-erasure fix on
 * upload-link expiry (UST601, UST611-612, UST662, UST776).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationScheduledTasksS6Test {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private NotificationService notificationService;
    @Mock private NodalOfficerRecordRepository nodalOfficerRecordRepository;
    @Mock private UploadLinkRepository uploadLinkRepository;
    @Mock private RbioStatusVocabulary rbioStatusVocabulary;
    @Mock private SystemConfigService systemConfigService;

    private NotificationScheduledTasks tasks;
    private NotificationConfigService config;

    private void build() {
        config = new NotificationConfigService(systemConfigService);
        tasks = new NotificationScheduledTasks(complaintRepository, notificationService,
                nodalOfficerRecordRepository, uploadLinkRepository, rbioStatusVocabulary, config);
        when(rbioStatusVocabulary.closedStatuses()).thenReturn(List.of("closed", "resolved"));
    }

    @Nested
    @DisplayName("Upload-link expiry (UST776)")
    class UploadLinkExpiry {

        /**
         * THE EVIDENCE-ERASURE BUG. The job used to call {@code setDocumentsSubmitted(false)} on every
         * lapsing link unconditionally, so a complainant who uploaded on day 3 had the record of their
         * submission wiped on day 7 — leaving a row with a submission TIMESTAMP but a false submission
         * FLAG, and a UST776 display value of "No" for someone who demonstrably did submit.
         */
        @Test
        @DisplayName("preserves documentsSubmitted when the complainant actually submitted")
        void preservesSubmissionFlagOnLapse() {
            build();
            UploadLink submitted = UploadLink.builder()
                    .complaintNumber("CMP-1").token("t1")
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .active(true)
                    .documentsSubmitted(true)
                    .documentsSubmittedAt(LocalDateTime.now().minusDays(4))
                    .build();
            when(uploadLinkRepository.findByActiveTrueAndExpiresAtBefore(any()))
                    .thenReturn(List.of(submitted));

            tasks.checkUploadLinkExpiry();

            assertThat(submitted.isActive()).as("a lapsed link is deactivated").isFalse();
            assertThat(submitted.isDocumentsSubmitted())
                    .as("the submission record must survive the link lapsing")
                    .isTrue();
            // No notification: there is nothing to chase, the documents arrived.
            verify(notificationService, never()).raiseEvent(any(), eq("UPLOAD_LINK_EXPIRED"),
                    any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("notifies only when a link lapsed with no submission")
        void notifiesOnLapseWithoutSubmission() {
            build();
            UploadLink unsubmitted = UploadLink.builder()
                    .complaintNumber("CMP-2").token("t2")
                    .expiresAt(LocalDateTime.now().minusDays(1))
                    .active(true)
                    .documentsSubmitted(false)
                    .build();
            when(uploadLinkRepository.findByActiveTrueAndExpiresAtBefore(any()))
                    .thenReturn(List.of(unsubmitted));
            when(complaintRepository.findByComplaintNumber("CMP-2")).thenReturn(Optional.empty());

            tasks.checkUploadLinkExpiry();

            assertThat(unsubmitted.isActive()).isFalse();
            verify(notificationService).raiseEvent(any(), eq("UPLOAD_LINK_EXPIRED"),
                    any(), any(), eq("CMP-2"), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Configurable intervals (UST611-612, UST662)")
    class ConfigurableIntervals {

        /**
         * Proves the threshold is READ FROM CONFIG rather than hardcoded: with the nudge set to 30 days,
         * the query cutoff must be ~30 days ago, not the old literal 5.
         */
        @Test
        @DisplayName("pending nudge honours notification.pending.nudge_days")
        void pendingNudgeUsesConfiguredInterval() {
            build();
            when(systemConfigService.getInt(
                    eq(NotificationConfigService.KEY_PENDING_NUDGE_DAYS), anyInt())).thenReturn(30);
            when(systemConfigService.getSet(any(), any())).thenReturn(Set.of("COMPLAINT_OWNER"));
            when(complaintRepository.findByStatusAndLastStatusChangeDateBefore(any(), any()))
                    .thenReturn(List.of());

            tasks.checkPendingFiveDays();

            ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(complaintRepository)
                    .findByStatusAndLastStatusChangeDateBefore(eq("pending"), cutoff.capture());

            long daysAgo = java.time.Duration.between(cutoff.getValue(), LocalDateTime.now()).toDays();
            assertThat(daysAgo)
                    .as("cutoff must reflect the configured 30 days, not the former hardcoded 5")
                    .isBetween(29L, 31L);
        }

        /**
         * A zero or negative interval would make {@code minusDays} select the FUTURE, so every complaint
         * ever filed would match and every officer would be nudged about all of them. The config reader
         * must refuse the value rather than honour it.
         */
        @Test
        @DisplayName("a non-positive configured interval falls back instead of selecting the future")
        void nonPositiveIntervalFallsBack() {
            build();
            when(systemConfigService.getInt(
                    eq(NotificationConfigService.KEY_PENDING_NUDGE_DAYS), anyInt())).thenReturn(0);

            assertThat(config.pendingNudgeDays())
                    .as("0 days must fall back to the safe default")
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("RE-response alert delay of 0 is honoured, since 'alert on lapse' is valid")
        void zeroGraceIsValidForReResponse() {
            build();
            when(systemConfigService.getInt(
                    eq(NotificationConfigService.KEY_RE_RESPONSE_ALERT_DELAY_DAYS), anyInt()))
                    .thenReturn(0);

            assertThat(config.reResponseAlertDelayDays()).isZero();
        }
    }

    @Nested
    @DisplayName("Stale no-record scan (UST611)")
    class StaleNoRecordScan {

        /**
         * The scan must exclude records whose PARENT COMPLAINT is closed. Previously it filtered only on
         * the NO record's own status, so a complaint closed while its record still read
         * INFORMATION_REQUIRED escalated to an administrator every day forever — the record never changes
         * again, so it only gets staler.
         */
        @Test
        @DisplayName("queries only records whose parent complaint is still open")
        void excludesClosedParents() {
            build();
            when(systemConfigService.getInt(any(), anyInt())).thenReturn(15);
            when(systemConfigService.getSet(any(), any())).thenReturn(Set.of("RBIO_ADMIN"));
            when(nodalOfficerRecordRepository.findStaleWithOpenComplaint(any(), any(), any()))
                    .thenReturn(List.of());

            tasks.checkNoRecordStale();

            // The closed-status-aware query is the one used; the old status-only finder must not be.
            verify(nodalOfficerRecordRepository, atLeastOnce())
                    .findStaleWithOpenComplaint(eq("INFORMATION_REQUIRED"), any(),
                            eq(List.of("closed", "resolved")));
            verify(nodalOfficerRecordRepository, never())
                    .findByStatusAndLastModifiedAtBefore(any(), any());
        }
    }
}
