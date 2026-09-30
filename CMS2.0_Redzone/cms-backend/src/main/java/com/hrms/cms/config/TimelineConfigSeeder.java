package com.hrms.cms.config;

import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.SystemConfigRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SYSTEM_CONFIG rows for the {@code timeline.*} windows.
 *
 * <p>WHY THIS EXISTS: every reader of these keys resolves them with a hardcoded fallback, so an
 * absent row is invisible — the window still works, at the compiled-in default. But
 * {@link com.hrms.cms.service.TimelineConfigService#getTimelineConfigs()} lists rows, and
 * {@code updateConfig} throws "Config key not found" for a key that has none. The effect was that
 * {@code timeline.appeal.filing_window_days} governed whether a citizen may still file an appeal
 * while being absent from the admin screen and impossible to change without a redeploy. A window
 * with a statutory meaning that no administrator can reach is a configuration gap, not a default.
 *
 * <p>The seeded VALUES are each reader's own fallback constant, so seeding changes no behaviour —
 * it only makes the existing behaviour visible and editable. Insert-if-absent, so re-running is a
 * no-op and an operator's later edit is never overwritten on restart.
 *
 * <p>Keys are those production code actually reads (grep {@code "timeline.}). Note the two spellings
 * that coexist: {@code timeline.appeal_window_days} is read by the older CEPC path while
 * {@code timeline.appeal.filing_window_days} is read by AppealEligibilityService. Both are seeded
 * because both are live; unifying them is a data migration, not a seeder's business.
 */
@Component
@Order(20)
public class TimelineConfigSeeder implements CommandLineRunner {

    private final SystemConfigRepository repo;

    public TimelineConfigSeeder(SystemConfigRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        defaults().forEach((key, spec) -> seed(key, spec[0], spec[1]));
    }

    private Map<String, String[]> defaults() {
        Map<String, String[]> m = new LinkedHashMap<>();
        // AppealEligibilityService.DEFAULT_FILING_WINDOW_DAYS / DEFAULT_EXTENDED_WINDOW_DAYS
        m.put("timeline.appeal.filing_window_days",
                new String[]{"30", "Days from the decision within which an appeal may be filed"});
        m.put("timeline.appeal.extended_window_days",
                new String[]{"60", "Outer limit including condonation; beyond this an appeal is refused"});
        // ReResponseDeadlineService.DEFAULT_RESPONSE_DAYS
        m.put("timeline.re.response_deadline_days",
                new String[]{"15", "Days a Regulated Entity has to file its written response"});
        // TimelineConfigService.getDefaultValue
        m.put("timeline.re_response_days",
                new String[]{"30", "Legacy RE response window read by the CEPC path"});
        m.put("timeline.filing_deadline_days",
                new String[]{"365", "Days from cause of action within which a complaint is maintainable"});
        m.put("timeline.crpc_processing_days",
                new String[]{"30", "Days CRPC has to process a complaint before escalation"});
        m.put("timeline.rbio_resolution_days",
                new String[]{"30", "Days an RBIO office has to resolve a complaint"});
        m.put("timeline.appeal_window_days",
                new String[]{"30", "Legacy appeal window read by the CEPC path"});
        return m;
    }

    private void seed(String key, String value, String description) {
        if (repo.findByConfigKey(key).isPresent()) {
            return;
        }
        repo.save(SystemConfig.builder()
                .configKey(key)
                .configValue(value)
                .description(description)
                .updatedBy("system-seed")
                .build());
    }
}
