package com.hrms.cms.config;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    /** Identifies this seeder's own rows, so the skip guard cannot be tripped by unrelated data. */
    private static final String NUMBER_PREFIX = "CMS-DEMO-";

    private final ComplaintRepository complaintRepo;

    /**
     * Closed complaints written per category, over and above the random 60.
     *
     * <p>Matched to {@code AssistanceRailService.MIN_CLOSURE_SAMPLE}, which refuses to report a median
     * closure time from fewer than five closed complaints. The random rows cannot clear that floor:
     * one status in six is "closed" across eight categories, which measured out at a maximum of
     * **three** closed rows in any one category — so the rail's category-closure prior was structurally
     * unable to fire on demo data no matter how long the fixture ran.
     *
     * <p>The floor is deliberately NOT lowered to fit the fixture. Five is a statistical guard on what
     * may be shown to an officer as typical; the fixture is what was inadequate.
     */
    private static final int CLOSED_PER_CATEGORY = 6;

    @Override
    public void run(String... args) {
        // Counts THIS seeder's own series, not every complaint in the database. The old guard was
        // `count() >= 80` over the whole COMPLAINTS table, so on any database that had accumulated
        // other rows — the shared dev database is at ~3000 — it short-circuited permanently and the
        // demo set could never be rewritten or extended.
        long existing = complaintRepo.countByComplaintNumberStartingWith(NUMBER_PREFIX);
        if (existing > 0) {
            log.info("Demo data already seeded ({} {}* complaints exist), skipping.",
                    existing, NUMBER_PREFIX);
            return;
        }

        log.info("Seeding demo complaints for report builder testing...");
        Random rng = new Random(42);

        String[] statuses = {"pending", "in_progress", "resolved", "closed", "escalated", "forwarded"};
        String[] departments = {"RBIO", "CEPC", "CRPC"};
        String[] priorities = {"high", "medium", "low"};
        String[] entityCodes = {"SBI", "HDFC", "ICICI", "PNB", "AXIS", "KOTAK", "BOB", "UNION", "CANARA", "INDIAN"};
        String[] subjects = {
            "Failed ATM withdrawal at branch",
            "Wrong charges debited from savings account",
            "UPI transaction failed but amount deducted",
            "Loan EMI overcharged for two months",
            "Credit card dispute not resolved by bank",
            "NEFT transfer not credited to beneficiary",
            "Mis-selling of insurance product",
            "Deposit maturity amount not credited",
            "ATM card blocked without notice",
            "Net banking fraud - unauthorized transaction"
        };
        String[] categoryLabels = {"FAILED_TXN", "WRONG_CHARGE", "UPI", "LOAN", "CARD", "NEFT_RTGS", "MIS_SELLING", "DEPOSIT", "CARD", "FAILED_TXN"};
        Long[] categoryIds = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 5L, 1L};

        List<Complaint> batch = new ArrayList<>();

        for (int i = 0; i < 60; i++) {
            int daysAgo = rng.nextInt(90);
            LocalDateTime created = LocalDateTime.now().minusDays(daysAgo).minusHours(rng.nextInt(12));

            String status = statuses[rng.nextInt(statuses.length)];
            LocalDateTime resolved = null;
            LocalDateTime closed = null;
            if ("resolved".equals(status)) {
                resolved = created.plusDays(rng.nextInt(20) + 1);
            } else if ("closed".equals(status)) {
                resolved = created.plusDays(rng.nextInt(15) + 1);
                closed = resolved.plusDays(rng.nextInt(5) + 1);
            }

            int subjectIdx = rng.nextInt(subjects.length);

            Complaint c = new Complaint();
            c.setComplaintNumber(String.format(NUMBER_PREFIX + "%04d", 1000 + i));
            c.setSubject(subjects[subjectIdx]);
            c.setStatus(status);
            c.setPriority(priorities[rng.nextInt(priorities.length)]);
            c.setDepartment(departments[rng.nextInt(departments.length)]);
            c.setEntityCode(entityCodes[rng.nextInt(entityCodes.length)]);
            c.setCategoryId(categoryIds[subjectIdx]);
            c.setCreatedAt(created);
            c.setFiledAt(created);
            c.setResolvedAt(resolved);
            c.setClosedAt(closed);
            c.setComplainantName("Demo User " + (i + 1));
            c.setComplainantEmail("demo" + (i + 1) + "@example.com");
            c.setComplainantPhone("90000" + String.format("%05d", 10000 + i));

            if (rng.nextBoolean()) {
                c.setMaintainabilityDetermination(rng.nextBoolean() ? "MAINTAINABLE" : "NON_MAINTAINABLE");
            }
            if (rng.nextInt(3) == 0) {
                c.setAssignedOfficer("officer." + departments[rng.nextInt(departments.length)].toLowerCase() + "." + (rng.nextInt(5) + 1));
            }

            batch.add(c);
        }

        batch.addAll(closedCohort(rng, subjects, categoryIds, departments, entityCodes, batch.size()));

        complaintRepo.saveAll(batch);
        log.info("Seeded {} demo complaints successfully ({} of them a closed cohort carrying a "
                + "measurable closure window).", batch.size(), batch.size() - 60);
    }

    /**
     * Closed complaints with a real filing-to-closure window, one cohort per category.
     *
     * <p>Exists so that anything measuring how long a category takes to close has a sample to measure.
     * Every row here is {@code closed}, carries a {@code categoryId}, and has {@code closedAt} strictly
     * after {@code createdAt} — the three conditions
     * {@code ComplaintRepository.findClosureWindowsForCategory} and the rail's non-negative filter
     * between them require.
     *
     * <p>The windows are spread deterministically rather than randomly (8, 11, 14, 17, 20, 23 days for
     * the first category, shifted per category) so the median is stable across runs. A fixture whose
     * median moves on every boot makes any assertion about it flaky, and the figure is shown to an
     * officer as "this category closes in N days".
     */
    private List<Complaint> closedCohort(Random rng, String[] subjects, Long[] categoryIds,
                                         String[] departments, String[] entityCodes, int startIndex) {
        List<Complaint> cohort = new ArrayList<>();
        int n = startIndex;

        for (int subjectIdx = 0; subjectIdx < subjects.length; subjectIdx++) {
            for (int k = 0; k < CLOSED_PER_CATEGORY; k++) {
                int windowDays = 8 + (k * 3) + subjectIdx;
                // Filed well inside the 90-day span the random rows use, and closed before now, so the
                // cohort does not look like a future complaint on any dashboard.
                LocalDateTime created = LocalDateTime.now()
                        .minusDays(85L - k)
                        .minusHours(rng.nextInt(12));

                Complaint c = new Complaint();
                c.setComplaintNumber(String.format(NUMBER_PREFIX + "%04d", 1000 + n++));
                c.setSubject(subjects[subjectIdx]);
                c.setStatus("closed");
                c.setPriority("medium");
                c.setDepartment(departments[subjectIdx % departments.length]);
                c.setEntityCode(entityCodes[subjectIdx % entityCodes.length]);
                c.setCategoryId(categoryIds[subjectIdx]);
                c.setCreatedAt(created);
                c.setFiledAt(created);
                c.setResolvedAt(created.plusDays(windowDays - 1L));
                c.setClosedAt(created.plusDays(windowDays));
                c.setComplainantName("Demo Closed User " + n);
                c.setComplainantEmail("demo.closed" + n + "@example.com");
                c.setComplainantPhone("91000" + String.format("%05d", 10000 + n));

                cohort.add(c);
            }
        }
        return cohort;
    }
}
