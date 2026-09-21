package com.hrms.cms.config;

import com.hrms.cms.entity.RbiDepartmentMaster;
import com.hrms.cms.entity.RegulatorMaster;
import com.hrms.cms.repository.RbiDepartmentMasterRepository;
import com.hrms.cms.repository.RegulatorMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds REGULATOR_MASTER and RBI_DEPARTMENT_MASTER so the RBIO Forward tab's two lookups have
 * something to search on the dev-local profile. Without a row in each, both dropdowns come up empty
 * and the tab looks broken rather than unseeded.
 *
 * <p><b>The email addresses here are deliberately fake.</b> The organisation names are public fact,
 * but their real grievance mailboxes are not in this repository, and a plausible-looking wrong
 * address is worse than an obviously fake one — it would be indistinguishable from reference data and
 * could be mailed in earnest. Every address is on {@code example.com}, matching the convention
 * {@link RbioDevDataLoader} already uses for its dev complainant. Production must populate these
 * through the admin CRUD on {@code /api/v1/masters/regulators} and
 * {@code /api/v1/masters/rbi-departments}; the prod profile runs {@code ddl-auto=validate} and this
 * loader does not activate there.
 *
 * <p>Upserts on {@code code} rather than skipping wholesale when the table is non-empty, so adding an
 * entry below and restarting picks it up instead of silently doing nothing. Existing rows are left
 * alone, so a locally edited address survives a restart.
 */
@Slf4j
@Component
@Profile("dev-local")
@Order(15)
@RequiredArgsConstructor
public class ForwardTargetDevDataLoader implements CommandLineRunner {

    private final RegulatorMasterRepository regulatorRepository;
    private final RbiDepartmentMasterRepository rbiDepartmentRepository;

    private record Target(String code, String name, String email) { }

    private static final List<Target> REGULATORS = List.of(
            new Target("SEBI", "Securities and Exchange Board of India", "sebi.grievance@example.com"),
            new Target("IRDAI", "Insurance Regulatory and Development Authority of India", "irdai.grievance@example.com"),
            new Target("PFRDA", "Pension Fund Regulatory and Development Authority", "pfrda.grievance@example.com"),
            new Target("IBBI", "Insolvency and Bankruptcy Board of India", "ibbi.grievance@example.com"),
            new Target("NHB", "National Housing Bank", "nhb.grievance@example.com"),
            new Target("NABARD", "National Bank for Agriculture and Rural Development", "nabard.grievance@example.com"));

    private static final List<Target> RBI_DEPARTMENTS = List.of(
            new Target("CEPD", "Consumer Education and Protection Department", "cepd@example.com"),
            new Target("DoR", "Department of Regulation", "dor@example.com"),
            new Target("DoS", "Department of Supervision", "dos@example.com"),
            new Target("DPSS", "Department of Payment and Settlement Systems", "dpss@example.com"),
            new Target("FED", "Foreign Exchange Department", "fed@example.com"),
            new Target("DCM", "Department of Currency Management", "dcm@example.com"),
            new Target("FIDD", "Financial Inclusion and Development Department", "fidd@example.com"));

    @Override
    public void run(String... args) {
        int regulators = seedRegulators();
        int departments = seedDepartments();

        if (regulators == 0 && departments == 0) {
            log.info("Forward-target dev data already present — nothing to add");
            return;
        }
        log.info("Seeded {} regulator(s) and {} RBI department(s) for the Forward tab. "
                + "Email addresses are example.com placeholders, not real mailboxes.",
                regulators, departments);
    }

    private int seedRegulators() {
        int added = 0;
        for (int i = 0; i < REGULATORS.size(); i++) {
            Target t = REGULATORS.get(i);
            if (regulatorRepository.findByCode(t.code()).isPresent()) continue;
            regulatorRepository.save(RegulatorMaster.builder()
                    .code(t.code()).name(t.name()).email(t.email())
                    .active(true).sortOrder(i + 1)
                    .build());
            added++;
        }
        return added;
    }

    private int seedDepartments() {
        int added = 0;
        for (int i = 0; i < RBI_DEPARTMENTS.size(); i++) {
            Target t = RBI_DEPARTMENTS.get(i);
            if (rbiDepartmentRepository.findByCode(t.code()).isPresent()) continue;
            rbiDepartmentRepository.save(RbiDepartmentMaster.builder()
                    .code(t.code()).name(t.name()).email(t.email())
                    .active(true).sortOrder(i + 1)
                    .build());
            added++;
        }
        return added;
    }
}
