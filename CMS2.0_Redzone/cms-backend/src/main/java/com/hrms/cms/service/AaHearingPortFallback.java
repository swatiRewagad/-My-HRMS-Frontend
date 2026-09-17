package com.hrms.cms.service;

import com.hrms.cms.dto.AaHearingRecord;
import com.hrms.cms.entity.Appeal;
import com.hrms.cms.repository.AppealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interim {@link AaHearingPort}, active only until S3C registers a real one.
 *
 * Preserves exactly the behaviour that exists today — the sitting is written to
 * Appeal.hearingDate/hearingVenue — so nothing regresses while S3C builds real persistence.
 *
 * It therefore also preserves today's LIMITATION, and says so rather than hiding it: there is no
 * history, so {@link #history} returns at most the single current sitting and a reschedule overwrites
 * its predecessor. That is precisely the audit gap S3C exists to close; this class must not be mistaken
 * for a solution to it.
 */
@Configuration
@Slf4j
class AaHearingPortFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(AaHearingPort.class)
    AaHearingPort aaHearingPortFallback(AppealRepository appealRepository) {
        return new AaHearingPortFallback(appealRepository);
    }
}

@RequiredArgsConstructor
@Slf4j
class AaHearingPortFallback implements AaHearingPort {

    private final AppealRepository appealRepository;

    @Override
    @Transactional
    public AaHearingRecord schedule(String appealNumber, LocalDateTime when, String venue, String mode,
                                    List<String> partiesToNotify) {
        return write(appealNumber, when, venue, mode, "SCHEDULED", null, null, partiesToNotify);
    }

    @Override
    @Transactional
    public AaHearingRecord reschedule(String appealNumber, LocalDateTime when, String venue, String mode,
                                      String reason, List<String> partiesToNotify) {
        // The previous sitting is lost here. Recorded as a warning so the gap is visible in the log
        // rather than silent, until S3C appends instead of overwriting.
        log.warn("AA hearing for {} rescheduled without history — the previous sitting is overwritten "
                + "until S3C's hearing persistence lands", appealNumber);
        return write(appealNumber, when, venue, mode, "RESCHEDULED", null, reason, partiesToNotify);
    }

    @Override
    @Transactional
    public AaHearingRecord recordOutcome(String appealNumber, String outcome, String remarks) {
        Appeal appeal = require(appealNumber);
        return AaHearingRecord.builder()
                .appealNumber(appealNumber)
                .eventType("COMPLETED")
                .scheduledFor(appeal.getHearingDate())
                .venue(appeal.getHearingVenue())
                .outcome(outcome)
                .remarks(remarks)
                .performedAt(LocalDateTime.now())
                .build();
    }

    /** At most one record: whatever is currently on the appeal. Never null. */
    @Override
    @Transactional(readOnly = true)
    public List<AaHearingRecord> history(String appealNumber) {
        AaHearingRecord current = current(appealNumber);
        return current == null ? List.of() : List.of(current);
    }

    @Override
    @Transactional(readOnly = true)
    public AaHearingRecord current(String appealNumber) {
        Appeal appeal = require(appealNumber);
        if (appeal.getHearingDate() == null) {
            return null;
        }
        return AaHearingRecord.builder()
                .appealNumber(appealNumber)
                .eventType("SCHEDULED")
                .scheduledFor(appeal.getHearingDate())
                .venue(appeal.getHearingVenue())
                .build();
    }

    private AaHearingRecord write(String appealNumber, LocalDateTime when, String venue, String mode,
                                  String eventType, String outcome, String remarks,
                                  List<String> partiesToNotify) {
        Appeal appeal = require(appealNumber);
        appeal.setHearingDate(when);
        if (venue != null && !venue.isBlank()) {
            appeal.setHearingVenue(venue);
        }
        appealRepository.save(appeal);

        return AaHearingRecord.builder()
                .appealNumber(appealNumber)
                .eventType(eventType)
                .scheduledFor(when)
                .venue(venue)
                .mode(mode)
                .outcome(outcome)
                .remarks(remarks)
                .partiesNotified(partiesToNotify == null ? List.of() : partiesToNotify)
                .performedAt(LocalDateTime.now())
                .build();
    }

    private Appeal require(String appealNumber) {
        return appealRepository.findByAppealNumber(appealNumber)
                .orElseThrow(() -> new IllegalArgumentException("aa.workflow.error_appeal_not_found"));
    }
}
