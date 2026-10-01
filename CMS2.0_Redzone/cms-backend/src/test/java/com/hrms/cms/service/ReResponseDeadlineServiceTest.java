package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the RE response deadline and the server-side overdue rule (UST780, UST637).
 *
 * <p>The behaviour being guarded is that a deadline actually gets WRITTEN. Before this, the field had one
 * writer on an extension path nobody used, so it was NULL everywhere and the sweep meant to chase an
 * unresponsive entity filtered every complaint out. A test that only checked "isOverdue returns true for a
 * past date" would have passed against that broken system.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReResponseDeadlineServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private NodalOfficerRecordRepository nodalOfficerRecordRepository;
    @Mock private BusinessHoursService businessHoursService;
    @Mock private SystemConfigService systemConfigService;

    @InjectMocks private ReResponseDeadlineService service;

    private Complaint complaint(String status, LocalDate deadline) {
        Complaint c = new Complaint();
        c.setComplaintNumber("N202627013000001");
        c.setStatus(status);
        c.setReResponseDeadline(deadline);
        return c;
    }

    private void everyDayIsWorking() {
        when(businessHoursService.isBusinessDay(any(LocalDate.class))).thenReturn(true);
    }

    private void noNodalRecord() {
        when(nodalOfficerRecordRepository.findFirstByComplaintNumber(anyString()))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("Setting the deadline (UST780)")
    class Setting {

        @Test
        void writesTheOfficersChosenDateToTheComplaint() {
            everyDayIsWorking();
            noNodalRecord();
            Complaint c = complaint("in_progress", null);
            LocalDate chosen = LocalDate.now().plusDays(10);

            var result = service.setDeadline(c, chosen, "13(1) Notice");

            assertThat(result.accepted()).isTrue();
            assertThat(c.getReResponseDeadline()).isEqualTo(chosen);
            verify(complaintRepository).save(c);
        }

        @Test
        void alsoWritesToTheNodalOfficerRecord() {
            // UST780 requires the deadline on BOTH. The NO record is what the staleness escalations read, so
            // a deadline only on the complaint is invisible to the job meant to chase the entity.
            everyDayIsWorking();
            NodalOfficerRecord record = new NodalOfficerRecord();
            when(nodalOfficerRecordRepository.findFirstByComplaintNumber(anyString()))
                    .thenReturn(Optional.of(record));
            LocalDate chosen = LocalDate.now().plusDays(7);

            service.setDeadline(complaint("in_progress", null), chosen, "13(1) Notice");

            assertThat(record.getReResponseDeadline()).isEqualTo(chosen);
            // Which communication set it, so a reviewer can tell a notice from an information request.
            assertThat(record.getDeadlineCommunication()).isEqualTo("13(1) Notice");
            verify(nodalOfficerRecordRepository).save(record);
        }

        @Test
        void usesTheConfiguredWindowWhenNoDateIsChosen() {
            everyDayIsWorking();
            noNodalRecord();
            when(systemConfigService.getInt(eq(ReResponseDeadlineService.CFG_RESPONSE_DAYS), anyInt()))
                    .thenReturn(15);
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            LocalDate computed = LocalDate.now().plusDays(15);
            when(businessHoursService.calculateDueDate(any(), eq(135)))
                    .thenReturn(computed.atStartOfDay());

            var result = service.setDeadline(complaint("in_progress", null), null, "Information Required");

            assertThat(result.accepted()).isTrue();
            assertThat(result.deadline()).isEqualTo(computed);
            // The configured key is read rather than a literal. It was seeded with 15 and read by NOTHING,
            // while ReResponsivenessService hardcodes 30 — those two disagreed silently.
            verify(systemConfigService).getInt(eq(ReResponseDeadlineService.CFG_RESPONSE_DAYS), anyInt());
        }
    }

    @Nested
    @DisplayName("Refusing an unusable date")
    class Refusing {

        @Test
        void refusesAChosenDateInThePast() {
            everyDayIsWorking();
            Complaint c = complaint("in_progress", null);

            var result = service.setDeadline(c, LocalDate.now().minusDays(1), "13(1) Notice");

            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("in the past");
            // Nothing is written on a refusal, so a mistyped year cannot leave a half-set deadline.
            assertThat(c.getReResponseDeadline()).isNull();
            verify(complaintRepository, never()).save(any());
        }

        @Test
        void refusesAChosenDateOnANonWorkingDay() {
            // A deadline on a Sunday or a gazetted holiday gives the entity less time than the Scheme
            // intends. Refused rather than silently moved: an officer who picked the wrong day should be
            // told, not have a different date substituted behind their back.
            when(businessHoursService.isBusinessDay(any(LocalDate.class))).thenReturn(false);

            var result = service.setDeadline(complaint("in_progress", null),
                    LocalDate.now().plusDays(5), "13(1) Notice");

            assertThat(result.accepted()).isFalse();
            assertThat(result.reason()).contains("weekend or a gazetted holiday");
        }

        @Test
        void correctsForwardWhenTheCONFIGUREDWindowLandsOnAHoliday() {
            // A computed default is corrected, not refused: the officer supplied nothing wrong, and the
            // fault would be in configuration they cannot see. Refusing would block the notice entirely.
            noNodalRecord();
            when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(15);
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            LocalDate holiday = LocalDate.now().plusDays(15);
            when(businessHoursService.calculateDueDate(any(), anyInt())).thenReturn(holiday.atStartOfDay());
            // Only the computed day is a holiday; every other day is workable, so the correction moves by
            // exactly one. Expressed as an Answer because a later broad stub would otherwise override a
            // narrower one and the holiday would appear workable.
            when(businessHoursService.isBusinessDay(any(LocalDate.class)))
                    .thenAnswer(inv -> !holiday.equals(inv.getArgument(0)));

            var result = service.setDeadline(complaint("in_progress", null), null, "13(1) Notice");

            assertThat(result.accepted()).isTrue();
            assertThat(result.deadline()).isEqualTo(holiday.plusDays(1));
        }
    }

    @Nested
    @DisplayName("Overdue is decided on the server (UST637)")
    class Overdue {

        @Test
        void aPastDeadlineWithNoResponseIsOverdue() {
            assertThat(service.isOverdue(complaint("in_progress", LocalDate.now().minusDays(3)))).isTrue();
        }

        @Test
        void aFutureDeadlineIsNotOverdue() {
            assertThat(service.isOverdue(complaint("in_progress", LocalDate.now().plusDays(3)))).isFalse();
        }

        @Test
        void todayIsNotYetOverdue() {
            // The entity has until the end of the day it was given. Treating the deadline date itself as
            // overdue would quietly shorten every window by a day.
            assertThat(service.isOverdue(complaint("in_progress", LocalDate.now()))).isFalse();
        }

        @Test
        void aComplaintWithNoDeadlineIsNeverOverdue() {
            assertThat(service.isOverdue(complaint("in_progress", null))).isFalse();
        }

        @Test
        void anAnsweredComplaintStopsBeingOverdue() {
            // This is what makes the UI highlight clear ITSELF once the entity responds (UST637), rather
            // than needing a separate un-highlight step somebody could forget.
            assertThat(service.isOverdue(complaint("re_responded", LocalDate.now().minusDays(5)))).isFalse();
        }

        @Test
        void aResolvedOrClosedComplaintStopsBeingOverdue() {
            assertThat(service.isOverdue(complaint("resolved", LocalDate.now().minusDays(5)))).isFalse();
            assertThat(service.isOverdue(complaint("closed", LocalDate.now().minusDays(5)))).isFalse();
        }

        @Test
        void reportsHowManyDaysLateTheEntityIs() {
            // Exposed so the UI need not subtract dates itself — the same reason the flag is server-side.
            assertThat(service.daysOverdue(complaint("in_progress", LocalDate.now().minusDays(4)))).isEqualTo(4);
            assertThat(service.daysOverdue(complaint("in_progress", LocalDate.now().plusDays(4)))).isZero();
        }

        @Test
        void aNullComplaintIsNotOverdue() {
            assertThat(service.isOverdue(null)).isFalse();
        }
    }
}
