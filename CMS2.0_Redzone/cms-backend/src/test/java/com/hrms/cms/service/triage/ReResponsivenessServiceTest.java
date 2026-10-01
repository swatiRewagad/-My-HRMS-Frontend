package com.hrms.cms.service.triage;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.service.BusinessHoursService;
import com.hrms.cms.service.ReResponseDeadlineService;
import com.hrms.cms.service.SystemConfigService;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Guards the RE responsiveness window against the two defects this class shipped with.
 *
 * <h2>Why these tests are shaped this way</h2>
 * The window was hardcoded 30 days and multiplied by a literal 8 business hours. A test that asserted
 * "a tracker is saved with a window" would have passed against both defects, and so would a test that
 * mocked the window calculation and asserted the mocked value came back. So these tests assert on the
 * NUMBERS that were wrong:
 *
 * <ul>
 *   <li>the window days must come from SYSTEM_CONFIG {@code timeline.re.response_deadline_days} — asserted
 *       by configuring a value that is neither 30 nor the 15 fallback, so neither the old literal nor a
 *       coincidentally-equal default can satisfy it;</li>
 *   <li>the business-hours multiplier must be {@link BusinessHoursService#getBusinessHoursPerDay()} —
 *       asserted by capturing the exact hour count handed to {@code calculateDueDate}, because that is the
 *       only place the literal 8 was observable.</li>
 * </ul>
 *
 * <p>{@link ReResponseDeadlineService} is deliberately REAL here, not mocked. Mocking it would let the
 * tracker keep its own private copy of the window and still pass — the whole point of the fix is that there
 * is one definition, and only a real collaborator proves the delegation happens.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReResponsivenessServiceTest {

    private static final int BUSINESS_HOURS_PER_DAY = 9;

    @Mock private ReResponseTrackerRepository trackerRepo;
    @Mock private BusinessHoursService businessHoursService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private NodalOfficerRecordRepository nodalOfficerRecordRepository;

    private ReResponsivenessService service;

    private void withConfiguredWindow(int days) {
        ReResponseDeadlineService deadlineService = new ReResponseDeadlineService(
                complaintRepository, nodalOfficerRecordRepository, businessHoursService, systemConfigService);
        service = new ReResponsivenessService(trackerRepo, deadlineService);

        when(systemConfigService.getInt(eq(ReResponseDeadlineService.CFG_RESPONSE_DAYS), anyInt()))
                .thenReturn(days);
        when(businessHoursService.getBusinessHoursPerDay()).thenReturn(BUSINESS_HOURS_PER_DAY);
        when(businessHoursService.isBusinessDay(any(LocalDate.class))).thenReturn(true);
        when(businessHoursService.calculateDueDate(any(), anyInt()))
                .thenAnswer(inv -> LocalDateTime.now().plusDays(7));
        when(trackerRepo.save(any(ReResponseTracker.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Complaint complaint() {
        Complaint c = new Complaint();
        c.setId(4242L);
        c.setComplaintNumber("N202627013000001");
        return c;
    }

    private int capturedBusinessHours() {
        ArgumentCaptor<Integer> hours = ArgumentCaptor.forClass(Integer.class);
        verify(businessHoursService).calculateDueDate(any(), hours.capture());
        return hours.getValue();
    }

    @Nested
    @DisplayName("The response window comes from configuration, not a literal")
    class WindowDays {

        @Test
        void readsTheConfiguredWindowRatherThanTheOldHardcoded30() {
            // 12 is chosen so it matches NEITHER the removed literal (30) NOR the service fallback (15).
            // A test using 15 would still pass if the config read were deleted, and one using 30 would pass
            // against the original defect.
            withConfiguredWindow(12);

            ReResponseTracker tracker = service.trackForwarding(complaint(), 77L);

            assertThat(tracker.getWindowDays()).isEqualTo(12);
            verify(systemConfigService).getInt(eq(ReResponseDeadlineService.CFG_RESPONSE_DAYS), anyInt());
        }

        @Test
        void usesTheSameWindowTheDeadlineServiceShowsTheOfficer() {
            // This is the defect that mattered most: the date the entity was given and the date it was
            // judged against were computed by two services that disagreed. 15 is the seeded value.
            withConfiguredWindow(15);

            ReResponseTracker tracker = service.trackForwarding(complaint(), 77L);

            assertThat(tracker.getWindowDays()).isEqualTo(15);
            // 30 was the old hardcoded value. Named explicitly so a regression reads unambiguously.
            assertThat(tracker.getWindowDays()).isNotEqualTo(30);
        }

        @Test
        void fallsBackTo15WhenTheConfigRowIsMissing() {
            // Fallback must be the statutory 15, not the old 30. A missing row must not double the window.
            ReResponseDeadlineService deadlineService = new ReResponseDeadlineService(
                    complaintRepository, nodalOfficerRecordRepository, businessHoursService, systemConfigService);
            service = new ReResponsivenessService(trackerRepo, deadlineService);
            when(systemConfigService.getInt(anyString(), anyInt()))
                    .thenAnswer(inv -> inv.getArgument(1)); // absent row: caller's default wins
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(BUSINESS_HOURS_PER_DAY);
            when(businessHoursService.calculateDueDate(any(), anyInt()))
                    .thenReturn(LocalDateTime.now().plusDays(15));
            when(trackerRepo.save(any(ReResponseTracker.class))).thenAnswer(inv -> inv.getArgument(0));

            ReResponseTracker tracker = service.trackForwarding(complaint(), 77L);

            assertThat(tracker.getWindowDays()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Business hours per day comes from BusinessHoursService, not a literal 8")
    class BusinessHoursMultiplier {

        @Test
        void multipliesTheWindowByTheConfiguredBusinessHoursPerDay() {
            // The literal 8 was only ever observable in the hour count passed to calculateDueDate, so that
            // is what is captured. 15 x 9 = 135; the defect produced 15 x 8 = 120.
            withConfiguredWindow(15);

            service.trackForwarding(complaint(), 77L);

            assertThat(capturedBusinessHours()).isEqualTo(15 * BUSINESS_HOURS_PER_DAY);
            assertThat(capturedBusinessHours()).isNotEqualTo(15 * 8);
            verify(businessHoursService, atLeastOnce()).getBusinessHoursPerDay();
        }

        @Test
        void trackstheWindowInFullDaysNotTheShortenedEightHourDay() {
            // A second working-day length proves the multiplier is genuinely read rather than a 9 that
            // happens to match. With a 10-hour day, 20 days must be 200 business hours.
            withConfiguredWindow(20);
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(10);

            service.trackForwarding(complaint(), 77L);

            assertThat(capturedBusinessHours()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("What the tracker records")
    class Recording {

        @Test
        void storesTheExpiryTheWindowActuallyComputed() {
            // The stored expiry must be the computed one. Storing forwardedAt + windowDays directly would
            // ignore holidays and produce a different date from the one the officer was shown.
            withConfiguredWindow(15);
            LocalDateTime computed = LocalDateTime.now().plusDays(21);
            when(businessHoursService.calculateDueDate(any(), anyInt())).thenReturn(computed);

            ReResponseTracker tracker = service.trackForwarding(complaint(), 77L);

            assertThat(tracker.getWindowExpiresAt()).isEqualTo(computed);
            assertThat(tracker.isBreached()).isFalse();
            assertThat(tracker.isExParteEligible()).isFalse();
        }

        @Test
        void aResponseAfterTheWindowIsRecordedAsBreached() {
            withConfiguredWindow(15);
            ReResponseTracker existing = ReResponseTracker.builder()
                    .complaintId(4242L)
                    .forwardedAt(LocalDateTime.now().minusDays(40))
                    .windowExpiresAt(LocalDateTime.now().minusDays(1))
                    .breached(false)
                    .build();
            when(trackerRepo.findByComplaintId(4242L)).thenReturn(java.util.Optional.of(existing));

            service.recordResponse(4242L);

            assertThat(existing.isBreached()).isTrue();
        }

        @Test
        void aResponseInsideTheWindowIsNotBreached() {
            withConfiguredWindow(15);
            ReResponseTracker existing = ReResponseTracker.builder()
                    .complaintId(4242L)
                    .forwardedAt(LocalDateTime.now().minusDays(2))
                    .windowExpiresAt(LocalDateTime.now().plusDays(10))
                    .breached(false)
                    .build();
            when(trackerRepo.findByComplaintId(4242L)).thenReturn(java.util.Optional.of(existing));

            service.recordResponse(4242L);

            assertThat(existing.isBreached()).isFalse();
        }
    }
}
