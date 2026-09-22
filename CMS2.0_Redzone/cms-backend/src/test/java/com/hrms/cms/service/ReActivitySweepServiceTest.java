package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the overdue flip (UST849) and the stuck-record nudge (UST850).
 *
 * The nudge behaviour cannot be proven through the API without waiting real days, and the snapshot
 * rule — that a threshold change must NOT retroactively re-judge records already sitting in a status
 * — is only observable by controlling the stored snapshot directly. Hence unit level.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReActivitySweepServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ReResponseTrackerRepository trackerRepository;
    @Mock private ReActivityStatusService activityStatusService;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private ReActivitySweepService sweepService;

    private Complaint complaint(Long id, ReActivityStatus status) {
        Complaint c = new Complaint();
        c.setId(id);
        c.setComplaintNumber("CMP-TEST-" + id);
        c.setAssignedOfficer("officer1");
        c.setReActivityStatus(status);
        return c;
    }

    private ReResponseTracker tracker(Long complaintId) {
        return ReResponseTracker.builder()
                .id(complaintId)
                .complaintId(complaintId)
                .regulatedEntityId(1L)
                .forwardedAt(LocalDateTime.now().minusDays(20))
                .windowDays(15)
                .windowExpiresAt(LocalDateTime.now().minusDays(5))
                .breached(false)
                .build();
    }

    private void enableAll() {
        when(systemConfigRepository.findByConfigKey(anyString())).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("Overdue sweep (UST849)")
    class OverdueSweep {

        @Test
        @DisplayName("flips an expired record to OVERDUE and escalates from the same trigger")
        void flipsAndEscalatesTogether() {
            enableAll();
            Complaint c = complaint(1L, ReActivityStatus.OPENED);
            when(trackerRepository.findPendingBreaches(any())).thenReturn(List.of(tracker(1L)));
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(c));
            when(activityStatusService.currentStatusOf(c)).thenReturn(ReActivityStatus.OPENED);
            when(activityStatusService.recordActivity(eq(c), eq(ReActivityStatus.OVERDUE), anyString(),
                    eq(TimelineEventSource.AUTOMATIC), anyString())).thenReturn(true);

            int flipped = sweepService.sweepOverdue();

            assertThat(flipped).isEqualTo(1);
            // The escalation is the whole point of "one trigger": a record cannot be marked overdue
            // without the owning officer being told.
            verify(notificationService).send(eq("officer1"), eq("RE_ACTIVITY_OVERDUE"),
                    anyString(), anyString(), eq("CMP-TEST-1"), eq("COMPLAINT"), anyString());
        }

        @Test
        @DisplayName("does not escalate a record that already answered")
        void skipsRespondedRecords() {
            enableAll();
            Complaint c = complaint(2L, ReActivityStatus.RESPONSE_SUBMITTED);
            when(trackerRepository.findPendingBreaches(any())).thenReturn(List.of(tracker(2L)));
            when(complaintRepository.findById(2L)).thenReturn(Optional.of(c));
            when(activityStatusService.currentStatusOf(c)).thenReturn(ReActivityStatus.RESPONSE_SUBMITTED);

            int flipped = sweepService.sweepOverdue();

            assertThat(flipped).isZero();
            verify(notificationService, never()).send(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("does not re-notify when the record is already OVERDUE")
        void idempotentAcrossRuns() {
            enableAll();
            Complaint c = complaint(3L, ReActivityStatus.OVERDUE);
            when(trackerRepository.findPendingBreaches(any())).thenReturn(List.of(tracker(3L)));
            when(complaintRepository.findById(3L)).thenReturn(Optional.of(c));
            when(activityStatusService.currentStatusOf(c)).thenReturn(ReActivityStatus.OVERDUE);
            // The forward-only rule refuses OVERDUE → OVERDUE, so no change is reported.
            when(activityStatusService.recordActivity(eq(c), eq(ReActivityStatus.OVERDUE), anyString(),
                    any(), anyString())).thenReturn(false);

            assertThat(sweepService.sweepOverdue()).isZero();
            verify(notificationService, never()).send(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("is disabled by its SYSTEM_CONFIG switch")
        void respectsKillSwitch() {
            when(systemConfigRepository.findByConfigKey(
                    ReActivitySweepService.CONFIG_ESCALATION_ENABLED))
                    .thenReturn(Optional.of(SystemConfig.builder()
                            .configKey(ReActivitySweepService.CONFIG_ESCALATION_ENABLED)
                            .configValue("false").build()));

            assertThat(sweepService.sweepOverdue()).isZero();
            verify(trackerRepository, never()).findPendingBreaches(any());
        }
    }

    @Nested
    @DisplayName("Nudge sweep (UST850)")
    class NudgeSweep {

        private Complaint stuck(Long id, ReActivityStatus status, int snapshotDays, int daysAgo) {
            Complaint c = complaint(id, status);
            c.setReActivityNudgeDays(snapshotDays);
            c.setReActivityChangedAt(LocalDateTime.now().minusDays(daysAgo));
            return c;
        }

        @Test
        @DisplayName("nudges once the snapshotted threshold has elapsed")
        void nudgesPastThreshold() {
            enableAll();
            Complaint c = stuck(10L, ReActivityStatus.OPENED, 3, 5);
            when(complaintRepository.findNudgeCandidates(anyList(), any(Pageable.class)))
                    .thenReturn(List.of(c));
            when(activityStatusService.currentStatusOf(c)).thenReturn(ReActivityStatus.OPENED);

            assertThat(sweepService.sweepNudges()).isEqualTo(1);
            verify(notificationService).send(eq("officer1"), eq("RE_ACTIVITY_NUDGE"),
                    anyString(), anyString(), eq("CMP-TEST-10"), eq("COMPLAINT"), anyString());

            // Stamped so the next run does not notify again for the same status.
            ArgumentCaptor<Complaint> saved = ArgumentCaptor.forClass(Complaint.class);
            verify(complaintRepository).save(saved.capture());
            assertThat(saved.getValue().getReActivityNudgedAt()).isNotNull();
        }

        @Test
        @DisplayName("stays silent while the record is inside its threshold")
        void silentInsideThreshold() {
            enableAll();
            Complaint c = stuck(11L, ReActivityStatus.OPENED, 5, 2);
            when(complaintRepository.findNudgeCandidates(anyList(), any(Pageable.class)))
                    .thenReturn(List.of(c));
            when(activityStatusService.currentStatusOf(c)).thenReturn(ReActivityStatus.OPENED);

            assertThat(sweepService.sweepNudges()).isZero();
            verify(notificationService, never()).send(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("uses the record's SNAPSHOT, not the current config value")
        void snapshotWinsOverLiveConfig() {
            // Live config says nudge after 1 day. The record entered its status when the threshold was
            // 30 days and is only 5 days old. Reading live config here would nudge it — which is
            // exactly the retroactive behaviour UST850 forbids.
            when(systemConfigRepository.findByConfigKey(ReActivitySweepService.CONFIG_NUDGE_ENABLED))
                    .thenReturn(Optional.empty());
            when(systemConfigRepository.findByConfigKey(ReActivitySweepService.CONFIG_SWEEP_BATCH_SIZE))
                    .thenReturn(Optional.empty());
            when(systemConfigRepository.findByConfigKey("cms.re.activity.nudge_days.opened"))
                    .thenReturn(Optional.of(SystemConfig.builder()
                            .configKey("cms.re.activity.nudge_days.opened")
                            .configValue("1").build()));

            Complaint c = stuck(12L, ReActivityStatus.OPENED, 30, 5);
            when(complaintRepository.findNudgeCandidates(anyList(), any(Pageable.class)))
                    .thenReturn(List.of(c));
            when(activityStatusService.currentStatusOf(c)).thenReturn(ReActivityStatus.OPENED);

            assertThat(sweepService.sweepNudges()).isZero();
            verify(notificationService, never()).send(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("skips a record with no snapshot rather than guessing a threshold")
        void skipsUnsnapshottedRecords() {
            enableAll();
            Complaint c = complaint(13L, ReActivityStatus.OPENED);
            c.setReActivityChangedAt(LocalDateTime.now().minusDays(100));
            c.setReActivityNudgeDays(null);
            when(complaintRepository.findNudgeCandidates(anyList(), any(Pageable.class)))
                    .thenReturn(List.of(c));
            when(activityStatusService.currentStatusOf(c)).thenReturn(ReActivityStatus.OPENED);

            assertThat(sweepService.sweepNudges()).isZero();
        }

        @Test
        @DisplayName("does not nudge a record that has reached a non-nudgeable level")
        void skipsNonNudgeableStatuses() {
            enableAll();
            Complaint c = stuck(14L, ReActivityStatus.RESPONSE_SUBMITTED, 3, 90);
            when(complaintRepository.findNudgeCandidates(anyList(), any(Pageable.class)))
                    .thenReturn(List.of(c));
            when(activityStatusService.currentStatusOf(c)).thenReturn(ReActivityStatus.RESPONSE_SUBMITTED);

            assertThat(sweepService.sweepNudges()).isZero();
        }

        @Test
        @DisplayName("is disabled by its SYSTEM_CONFIG switch")
        void respectsKillSwitch() {
            when(systemConfigRepository.findByConfigKey(ReActivitySweepService.CONFIG_NUDGE_ENABLED))
                    .thenReturn(Optional.of(SystemConfig.builder()
                            .configKey(ReActivitySweepService.CONFIG_NUDGE_ENABLED)
                            .configValue("false").build()));

            assertThat(sweepService.sweepNudges()).isZero();
            verify(complaintRepository, never()).findNudgeCandidates(anyList(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("Ladder ordering rules")
    class LadderRules {

        @Test
        @DisplayName("only advances forward, except that OVERDUE can interrupt")
        void forwardOnly() {
            assertThat(ReActivityStatus.OPENED.canAdvanceTo(ReActivityStatus.UNDER_REVIEW)).isTrue();
            assertThat(ReActivityStatus.UNDER_REVIEW.canAdvanceTo(ReActivityStatus.OPENED)).isFalse();
            assertThat(ReActivityStatus.OPENED.canAdvanceTo(ReActivityStatus.OPENED)).isFalse();
            assertThat(ReActivityStatus.UNDER_REVIEW.canAdvanceTo(ReActivityStatus.OVERDUE)).isTrue();
        }

        @Test
        @DisplayName("a submitted response can never be flipped to OVERDUE")
        void submittedIsNotOverduable() {
            assertThat(ReActivityStatus.RESPONSE_SUBMITTED.canAdvanceTo(ReActivityStatus.OVERDUE))
                    .isFalse();
        }

        @Test
        @DisplayName("a late response can climb out of OVERDUE")
        void lateResponseRecovers() {
            assertThat(ReActivityStatus.OVERDUE.canAdvanceTo(ReActivityStatus.RESPONSE_SUBMITTED))
                    .isTrue();
        }

        @Test
        @DisplayName("only the early levels are nudgeable")
        void nudgeableSet() {
            assertThat(ReActivityStatus.nudgeableStatuses())
                    .containsExactly(ReActivityStatus.NOT_OPENED, ReActivityStatus.OPENED,
                            ReActivityStatus.UNDER_REVIEW, ReActivityStatus.RESPONSE_BEING_PREPARED);
            // Nudging an already-OVERDUE record would duplicate the SLA escalation the story says
            // nudges must not replace.
            assertThat(ReActivityStatus.OVERDUE.isNudgeable()).isFalse();
        }

        @Test
        @DisplayName("an unknown status code is rejected rather than silently defaulted")
        void rejectsUnknownCode() {
            assertThat(ReActivityStatus.fromCode(null)).isEqualTo(ReActivityStatus.NOT_OPENED);
            assertThat(ReActivityStatus.fromCode("opened")).isEqualTo(ReActivityStatus.OPENED);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> ReActivityStatus.fromCode("NOT_A_STATUS"));
        }
    }
}
