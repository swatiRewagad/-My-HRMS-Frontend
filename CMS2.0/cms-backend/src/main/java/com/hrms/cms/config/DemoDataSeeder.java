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

    private final ComplaintRepository complaintRepo;

    @Override
    public void run(String... args) {
        if (complaintRepo.count() >= 80) {
            log.info("Demo data already seeded ({} complaints exist), skipping.", complaintRepo.count());
            return;
        }

        log.info("Seeding 60 demo complaints for report builder testing...");
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
            c.setComplaintNumber(String.format("CMS-DEMO-%04d", 1000 + i));
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

        complaintRepo.saveAll(batch);
        log.info("Seeded {} demo complaints successfully.", batch.size());
    }
}
