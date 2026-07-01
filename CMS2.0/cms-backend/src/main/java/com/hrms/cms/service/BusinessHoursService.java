package com.hrms.cms.service;

import com.hrms.cms.repository.HolidayRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessHoursService {

    private final HolidayRepository holidayRepository;

    @Value("${cms.tat.business-hour-start:9}")
    private int businessHourStart;

    @Value("${cms.tat.business-hour-end:18}")
    private int businessHourEnd;

    @Value("${cms.tat.timezone:Asia/Kolkata}")
    private String timezone;

    public BusinessHoursService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !isHoliday(date);
    }

    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsByHolidayDate(date);
    }

    public boolean isWithinBusinessHours(LocalDateTime dateTime) {
        ZonedDateTime zoned = dateTime.atZone(ZoneId.of(timezone));
        if (!isBusinessDay(zoned.toLocalDate())) return false;
        int hour = zoned.getHour();
        return hour >= businessHourStart && hour < businessHourEnd;
    }

    public LocalDateTime calculateDueDate(LocalDateTime startTime, int businessHours) {
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime current = startTime.atZone(zone);
        int remainingHours = businessHours;

        while (remainingHours > 0) {
            current = advanceToNextBusinessStart(current);

            int hoursLeftToday = businessHourEnd - current.getHour();
            if (hoursLeftToday <= 0) {
                current = current.plusDays(1).withHour(businessHourStart).withMinute(0).withSecond(0);
                continue;
            }

            int hoursToConsume = Math.min(remainingHours, hoursLeftToday);
            current = current.plusHours(hoursToConsume);
            remainingHours -= hoursToConsume;
        }

        return current.toLocalDateTime();
    }

    public long calculateElapsedBusinessHours(LocalDateTime start, LocalDateTime end) {
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime current = start.atZone(zone);
        ZonedDateTime endZoned = end.atZone(zone);
        long totalHours = 0;

        while (current.isBefore(endZoned)) {
            if (!isBusinessDay(current.toLocalDate())) {
                current = current.plusDays(1).withHour(businessHourStart).withMinute(0).withSecond(0);
                continue;
            }

            int startHour = Math.max(current.getHour(), businessHourStart);
            int endHour = businessHourEnd;

            if (current.toLocalDate().equals(endZoned.toLocalDate())) {
                endHour = Math.min(endZoned.getHour(), businessHourEnd);
            }

            if (startHour < endHour) {
                totalHours += (endHour - startHour);
            }

            current = current.plusDays(1).withHour(businessHourStart).withMinute(0).withSecond(0);
        }

        return totalHours;
    }

    public int getBusinessHoursPerDay() {
        return businessHourEnd - businessHourStart;
    }

    @Cacheable(value = "holidays", key = "#year")
    public Set<LocalDate> getHolidaysForYear(int year) {
        return holidayRepository.findByYear(year).stream()
            .map(h -> h.getHolidayDate())
            .collect(Collectors.toSet());
    }

    private ZonedDateTime advanceToNextBusinessStart(ZonedDateTime current) {
        if (current.getHour() >= businessHourEnd) {
            current = current.plusDays(1).withHour(businessHourStart).withMinute(0).withSecond(0);
        } else if (current.getHour() < businessHourStart) {
            current = current.withHour(businessHourStart).withMinute(0).withSecond(0);
        }

        while (!isBusinessDay(current.toLocalDate())) {
            current = current.plusDays(1).withHour(businessHourStart).withMinute(0).withSecond(0);
        }

        return current;
    }
}
