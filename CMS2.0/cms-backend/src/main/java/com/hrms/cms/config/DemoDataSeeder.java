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

        seedCepcDoComplaints();
    }

    private void seedCepcDoComplaints() {
        if (complaintRepo.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                "CEPC", "CEPC_DO", List.of("closed", "resolved")).size() > 5) {
            return;
        }

        log.info("Seeding CEPC DO complaints for workflow testing...");

        String[][] cepcData = {
            {"CMP-20260603-100001", "PhonePe unauthorized debit of Rs. 8,500 - no refund after 45 days", "Sanjay Patil", "sanjay.p@email.com", "9876543210", "PhonePe", "new_complaint", "HIGH"},
            {"CMP-20260603-100002", "Credit card annual fee charged despite waiver commitment", "Meera Joshi", "meera.j@email.com", "9876543211", "HDFC Bank", "new_complaint", "MEDIUM"},
            {"CMP-20260603-100003", "URGENT: Closed Loan Account Falsely Reported as Delinquent", "Saurabh Pradhan", "saurabh.pradhan@gmail.com", "9876543212", "ASNU FINVEST", "new_complaint", "HIGH"},
            {"CMP-20260603-100004", "Fixed deposit premature closure - interest not paid correctly", "Anita Sharma", "anita.s@email.com", "9876543213", "SBI", "draft", "MEDIUM"},
            {"CMP-20260603-100005", "Unauthorized insurance policy deduction from salary account", "Rajesh Kumar", "rajesh.k@email.com", "9876543214", "ICICI Bank", "draft", "LOW"},
            {"CMP-20260603-100006", "Meeting with complainant regarding loan dispute", "Priya Verma", "priya.v@email.com", "9876543215", "PNB", "meeting_scheduled", "MEDIUM"},
            {"CMP-20260603-100007", "UPI refund pending for 60 days - Google Pay", "Vikram Singh", "vikram.s@email.com", "9876543216", "Axis Bank", "sent_to_reviewer", "HIGH"},
            {"CMP-20260603-100008", "Home loan foreclosure charges excessive", "Sunita Devi", "sunita.d@email.com", "9876543217", "Kotak Bank", "sent_to_incharge", "MEDIUM"},
            {"CMP-20260603-100009", "Bank refusing to close account - BSBD complaint", "Amit Patel", "amit.p@email.com", "9876543218", "BOB", "sent_back", "LOW"},
            {"CMP-20260603-100010", "Gold loan auction without proper notice", "Kavita Reddy", "kavita.r@email.com", "9876543219", "Canara Bank", "closed", "HIGH"},
            {"CMP-20260603-100011", "Wrong CIBIL score reported by bank", "Deepak Gupta", "deepak.g@email.com", "9876543220", "Union Bank", "marked_for_closure", "MEDIUM"},
            {"CMP-20260603-100012", "Net banking fraud - Rs 2.5 lakh lost", "Neha Agarwal", "neha.a@email.com", "9876543221", "HDFC Bank", "new_complaint", "HIGH"},
        };

        List<Complaint> cepcBatch = new ArrayList<>();
        for (String[] row : cepcData) {
            Complaint c = new Complaint();
            c.setComplaintNumber(row[0]);
            c.setSubject(row[1]);
            c.setComplainantName(row[2]);
            c.setComplainantEmail(row[3]);
            c.setComplainantPhone(row[4]);
            c.setEntityCode(row[5]);
            c.setStatus(row[6]);
            c.setPriority(row[7]);
            c.setDepartment("CEPC");
            c.setAssignedRole("CEPC_DO");
            c.setAssignedOfficer("cepc_do1");
            c.setDescription("Hold placed on my Canara Bank account due to a cyber crime investigation since 16 February. My account has been blocked, and I am unable to operate it or withdraw/credit funds.");
            c.setCreatedAt(LocalDateTime.now().minusDays(new Random().nextInt(30) + 1));
            c.setFiledAt(c.getCreatedAt());
            cepcBatch.add(c);
        }

        complaintRepo.saveAll(cepcBatch);
        log.info("Seeded {} CEPC DO complaints.", cepcBatch.size());
    }
}
