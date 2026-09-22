package com.hrms.cms.service;

import com.hrms.cms.entity.AaCitizenNotice;
import com.hrms.cms.entity.AppealHearing;
import com.hrms.cms.repository.AppealHearingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reminds the parties and the presiding officer about a hearing that is coming up.
 *
 * <p>Idempotence is a DATA property, not a lock: cms-backend has no distributed lock (a SHEDLOCK
 * table exists but nothing in this service uses it), so on more than one pod this sweep runs
 * concurrently. The guard is the notice row itself -- {@code dedupeKey} is derived from the hearing
 * id, so the unique constraint on
 * {@code (appealNumber, eventCode, recipientRole, dedupeKey)} means the second pod's insert is
 * rejected and no citizen is reminded twice. Re-running the sweep by hand is therefore safe.
 *
 * <p>Gated on a SystemConfigService boolean that defaults to FALSE. That default is deliberate: a
 * {@code @SpringBootTest} boots the whole context with {@code @EnableScheduling}, and an empty
 * SYSTEM_CONFIG in the test database would otherwise let this fire real reminders during a test run.
 * Operations must turn it on explicitly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaHearingReminderSweepService {

    static final String CFG_ENABLED = "cms.aa.hearing.reminder_enabled";
    static final String CFG_LEAD_HOURS = "cms.aa.hearing.reminder_lead_hours";

    /** Remind a day ahead: long enough to travel, short enough to still be the operative date. */
    private static final int DEFAULT_LEAD_HOURS = 24;

    private final AppealHearingRepository hearingRepository;
    private final AaCitizenNoticeService citizenNoticeService;
    private final NotificationService notificationService;
    private final SystemConfigService systemConfigService;

    /**
     * Records reminder notices for every hearing falling inside the lead window.
     *
     * @return how many hearings were reminded about
     */
    @Transactional(readOnly = true)
    public int sweepUpcomingHearings() {
        if (!systemConfigService.getBoolean(CFG_ENABLED, false)) {
            log.debug("AA hearing reminder sweep is disabled by configuration");
            return 0;
        }

        int leadHours = systemConfigService.getInt(CFG_LEAD_HOURS, DEFAULT_LEAD_HOURS);
        if (leadHours <= 0) {
            log.warn("AA hearing reminder: lead of {} hours is not usable -- skipping sweep", leadHours);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        List<AppealHearing> upcoming = hearingRepository.findUpcoming(now, now.plusHours(leadHours));
        if (upcoming.isEmpty()) {
            return 0;
        }

        int reminded = 0;
        for (AppealHearing hearing : upcoming) {
            try {
                // dedupeKey carries the hearing id, so a hearing that stays inside the window across
                // several ticks is reminded about exactly once.
                String dedupe = "REMIND-H" + hearing.getId();

                AaCitizenNotice recorded = citizenNoticeService.record(
                        hearing.getAppealNumber(),
                        AaNotifyEvent.HEARING_REMINDER.name(),
                        AaCitizenNotice.PARTY_APPELLANT,
                        null, null, null,
                        AaNotifyEvent.HEARING_REMINDER.appellantKey(),
                        java.util.Map.of(
                                "appealNumber", hearing.getAppealNumber(),
                                "hearingDate", String.valueOf(hearing.getHearingDate())),
                        dedupe, "system");

                if (recorded == null) {
                    // Already reminded -- the dedupe guard did its job.
                    continue;
                }

                if (hearing.getPresidingOfficer() != null) {
                    notificationService.send(hearing.getPresidingOfficer(), "AA_WORKFLOW",
                            AaNotifyEvent.HEARING_REMINDER.officerKey(),
                            hearing.getAppealNumber(), hearing.getAppealNumber(), "APPEAL",
                            "/aa/appeal/" + hearing.getAppealNumber());
                }
                reminded++;
            } catch (RuntimeException e) {
                // One bad hearing must not abort the sweep, or a single failure hides every other
                // hearing due tomorrow.
                log.warn("AA hearing reminder: could not remind about {}: {}",
                        hearing.getAppealNumber(), e.getMessage());
            }
        }

        log.info("AA hearing reminder: {} hearing(s) reminded within a {}-hour lead", reminded, leadHours);
        return reminded;
    }
}
