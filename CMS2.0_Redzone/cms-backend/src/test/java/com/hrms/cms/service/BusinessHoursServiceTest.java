package com.hrms.cms.service;

import com.hrms.cms.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessHoursServiceTest {

    @Mock
    private HolidayRepository holidayRepository;

    private BusinessHoursService service;

    @BeforeEach
    void setup() {
        service = new BusinessHoursService(holidayRepository);
        ReflectionTestUtils.setField(service, "businessHourStart", 9);
        ReflectionTestUtils.setField(service, "businessHourEnd", 18);
        ReflectionTestUtils.setField(service, "timezone", "Asia/Kolkata");
        when(holidayRepository.existsByHolidayDate(any())).thenReturn(false);
    }

    @Test
    @DisplayName("weekday should be a business day")
    void weekdayShouldBeBusinessDay() {
        // 2026-07-01 is Wednesday
        assertThat(service.isBusinessDay(LocalDate.of(2026, 7, 1))).isTrue();
    }

    @Test
    @DisplayName("Saturday should not be a business day")
    void saturdayShouldNotBeBusinessDay() {
        // 2026-07-04 is Saturday
        assertThat(service.isBusinessDay(LocalDate.of(2026, 7, 4))).isFalse();
    }

    @Test
    @DisplayName("Sunday should not be a business day")
    void sundayShouldNotBeBusinessDay() {
        // 2026-07-05 is Sunday
        assertThat(service.isBusinessDay(LocalDate.of(2026, 7, 5))).isFalse();
    }

    @Test
    @DisplayName("holiday should not be a business day")
    void holidayShouldNotBeBusinessDay() {
        LocalDate republicDay = LocalDate.of(2026, 1, 26);
        when(holidayRepository.existsByHolidayDate(republicDay)).thenReturn(true);
        assertThat(service.isBusinessDay(republicDay)).isFalse();
    }

    @Test
    @DisplayName("10 AM on weekday is within business hours")
    void tenAmIsWithinBusinessHours() {
        LocalDateTime dt = LocalDateTime.of(2026, 7, 1, 10, 0);
        assertThat(service.isWithinBusinessHours(dt)).isTrue();
    }

    @Test
    @DisplayName("7 AM on weekday is NOT within business hours")
    void sevenAmIsNotWithinBusinessHours() {
        LocalDateTime dt = LocalDateTime.of(2026, 7, 1, 7, 0);
        assertThat(service.isWithinBusinessHours(dt)).isFalse();
    }

    @Test
    @DisplayName("6 PM (18:00) on weekday is NOT within business hours")
    void sixPmIsNotWithinBusinessHours() {
        LocalDateTime dt = LocalDateTime.of(2026, 7, 1, 18, 0);
        assertThat(service.isWithinBusinessHours(dt)).isFalse();
    }

    @Test
    @DisplayName("due date calculation: 9 business hours from 9 AM = next day 9 AM + 1 hour")
    void dueDateShouldSpanDays() {
        // 9 AM Wednesday + 9 hours = 9 hours total (full day = 9 hours), should be 6 PM same day
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 9, 0);
        LocalDateTime due = service.calculateDueDate(start, 9);
        assertThat(due).isEqualTo(LocalDateTime.of(2026, 7, 1, 18, 0));
    }

    @Test
    @DisplayName("due date should skip weekends")
    void dueDateShouldSkipWeekends() {
        // Friday 9 AM + 18 hours (2 business days) = Tuesday 9 AM? No — Fri 9 + 9h = Fri 18:00, then Mon 9 + 9 = Mon 18
        LocalDateTime friday9am = LocalDateTime.of(2026, 7, 3, 9, 0); // Friday
        LocalDateTime due = service.calculateDueDate(friday9am, 18);
        // 9 hours Friday + 9 hours Monday = 18 hours → Monday 6 PM
        assertThat(due).isEqualTo(LocalDateTime.of(2026, 7, 6, 18, 0));
    }

    @Test
    @DisplayName("elapsed hours calculation for same day")
    void elapsedHoursSameDay() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 1, 14, 0);
        long elapsed = service.calculateElapsedBusinessHours(start, end);
        assertThat(elapsed).isEqualTo(5);
    }

    @Test
    @DisplayName("elapsed hours should not count weekends")
    void elapsedHoursShouldSkipWeekends() {
        // Friday 9 AM to Monday 9 AM = only Friday's 9 hours
        LocalDateTime friday = LocalDateTime.of(2026, 7, 3, 9, 0);
        LocalDateTime monday = LocalDateTime.of(2026, 7, 6, 9, 0);
        long elapsed = service.calculateElapsedBusinessHours(friday, monday);
        assertThat(elapsed).isEqualTo(9);
    }

    @Test
    @DisplayName("business hours per day should be 9")
    void businessHoursPerDay() {
        assertThat(service.getBusinessHoursPerDay()).isEqualTo(9);
    }
}
