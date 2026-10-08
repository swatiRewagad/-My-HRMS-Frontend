package com.hrms.cms.config;

import com.hrms.cms.entity.RoleStatusMapping;
import com.hrms.cms.repository.RoleStatusMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_ALL_COMPLAINTS;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_CLOSED_COMPLAINTS;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_COMPLAINT_ASSIGNED_TO_ME;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_DRAFT_COMPLAINTS;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_MARK_FOR_CLOSURE;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_MEETING_SCHEDULED;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_NEW_COMPLAINT;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_REOPENED_COMPLAINTS;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_BACK_TO_CEPC_DO;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_BACK_TO_CEPC_IN_CHARGE;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_BACK_TO_CEPC_REVIEWER;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_CEPC_IN_CHARGE;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_CEPC_REVIEWER;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_CLOSING_AUTHORITY;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_OTHER_OFFICE;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_OTHER_RBI_DEPARTMENT;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_OTHER_REGULATED_BODIES;

/**
 * Seeds {@code ROLE_STATUS_MAPPING}: which status-dropdown entries each CEPC role is offered, in order.
 *
 * <p><b>Reconciling, not insert-if-absent:</b> each role's list below is the whole vocabulary that role is
 * offered, so a boot inserts what is missing, re-sequences what moved and deletes codes the list no longer
 * names. Insert-if-absent could not express a removal at all — dropping an entry here left it in every
 * already-seeded database, still pickable — and left survivors holding sequence numbers the new entries had
 * been given, shuffling the dropdown. Only roles named in {@link #run} are touched.
 *
 * <p>Ordered after {@code CepcDashboardFilterSeeder} so that the predicate rows every code here depends on
 * are in place first. Not a hard requirement — the dependency is resolved per request, not at boot — but a
 * dropdown offered before its predicates exist would return empty grids for as long as the gap lasted.
 *
 * <p><b>Role spellings come from {@code CepcIdentityResolver.CEPC_ROLES}.</b> That is what
 * {@code resolveCepcRole()} returns and the lookup is an equality match, so {@code CEPC_INCHARGE} is correct
 * and {@code CEPC_IN_CHARGE} would be a row no caller ever sees.
 *
 * <h2>Codes deliberately absent</h2>
 *
 * <p>{@code SENT_TO_RBI} was specified for {@code CEPC_ADMIN} and is not seeded: nothing in the CEPC workflow
 * records a complaint as sent to RBI, so it has no predicate to resolve to and would be a dropdown entry that
 * always returns nothing. It belongs here once the workflow writes a status or stage for it.
 *
 * <h2>Codes folded together</h2>
 *
 * <p>The specification named several concepts twice under different spellings. Each pair is one code here,
 * because two codes for one predicate means the dropdown offers the same rows under two names:
 * {@code RE_OPENED_COMPLAINTS} into {@code REOPENED_COMPLAINTS}; {@code COMPLAINT_CLOSED} into
 * {@code CLOSED_COMPLAINTS}; {@code MEETING_SCHEDULE} into {@code MEETING_SCHEDULED};
 * {@code SENT_BACK_TO_CEPC_DEALING_OFFICIAL} into {@code SENT_BACK_TO_CEPC_DO};
 * {@code ASSIGN_TO_OTHER_DEPARTMENT} into {@code SENT_TO_OTHER_RBI_DEPARTMENT}; and
 * {@code ASSIGNED_TO_OTHER_REGULATORY} into {@code SENT_TO_OTHER_REGULATED_BODIES}.
 */
@Component
@Order(31)
@RequiredArgsConstructor
@Slf4j
public class RoleStatusMappingSeeder implements CommandLineRunner {

    private final RoleStatusMappingRepository mappingRepo;

    @Override
    @Transactional
    public void run(String... args) {
        List<RoleStatusMapping> rows = new ArrayList<>();
        rows.addAll(dealingOfficial());
        rows.addAll(reviewer());
        rows.addAll(inCharge());
        rows.addAll(closingAuthority());
        rows.addAll(admin());

        Map<String, List<RoleStatusMapping>> byRole = rows.stream().collect(
                Collectors.groupingBy(RoleStatusMapping::getRoleName, LinkedHashMap::new, Collectors.toList()));

        int inserted = 0;
        int resequenced = 0;
        int removed = 0;
        for (Map.Entry<String, List<RoleStatusMapping>> role : byRole.entrySet()) {
            Map<String, RoleStatusMapping> unclaimed = mappingRepo
                    .findByRoleNameOrderBySequenceAsc(role.getKey()).stream()
                    .collect(Collectors.toMap(RoleStatusMapping::getStatusCode, m -> m, (a, b) -> a,
                            LinkedHashMap::new));

            for (RoleStatusMapping want : role.getValue()) {
                RoleStatusMapping have = unclaimed.remove(want.getStatusCode());
                if (have == null) {
                    mappingRepo.save(want);
                    inserted++;
                } else if (!Objects.equals(have.getSequence(), want.getSequence())) {
                    // Sequence comes from position in the list above, so dropping or reordering an entry
                    // shifts every entry after it. Insert-if-absent alone left the survivors on their old
                    // positions and gave the new rows the positions those survivors still held, which reads
                    // as an arbitrarily shuffled dropdown rather than the order written here.
                    have.setSequence(want.getSequence());
                    have.setDescription(want.getDescription());
                    mappingRepo.save(have);
                    resequenced++;
                }
            }

            // Codes no longer in this role's list. Deleted rather than left behind: the list here is the
            // whole vocabulary a role is offered, so a stale row is an entry the operator cannot see is
            // obsolete and the officer can still pick. Scoped to the roles seeded above, so a role this
            // seeder does not own is untouched.
            for (RoleStatusMapping stale : unclaimed.values()) {
                mappingRepo.delete(stale);
                removed++;
            }
        }
        if (inserted > 0 || resequenced > 0 || removed > 0) {
            log.info("CEPC role-status mapping reconciled: {} of {} rows inserted, {} resequenced, {} removed",
                    inserted, rows.size(), resequenced, removed);
        }
    }

    /** "Sent Back to me" is SENT_BACK_TO_CEPC_DO here, as in {@link #reviewer()} and {@link #inCharge()}. */
    private List<RoleStatusMapping> dealingOfficial() {
        Builder b = new Builder("CEPC_DO");
        b.add(STATUS_ALL_COMPLAINTS, "View all complaints");
        b.add(STATUS_DRAFT_COMPLAINTS, "View draft complaints");
        b.add(STATUS_MEETING_SCHEDULED, "View meeting scheduled complaints");
        b.add(STATUS_SENT_TO_CEPC_IN_CHARGE, "View complaints sent to CEPC in-charge");
        b.add(STATUS_SENT_TO_CEPC_REVIEWER, "View complaints sent to CEPC reviewer");
        b.add(STATUS_SENT_BACK_TO_CEPC_DO, "View complaints sent back to me");
        b.add(STATUS_CLOSED_COMPLAINTS, "View closed complaints");
        return b.rows();
    }

    /** "Sent Back to me" is SENT_BACK_TO_CEPC_REVIEWER here, the same way it is the in-charge's own code in
     * {@link #inCharge()}. The label stays role-neutral because other roles are offered the code too. */
    private List<RoleStatusMapping> reviewer() {
        Builder b = new Builder("CEPC_REVIEWER");
        b.add(STATUS_ALL_COMPLAINTS, "View all complaints");
        b.add(STATUS_SENT_TO_CEPC_IN_CHARGE, "View complaints sent to CEPC in-charge");
        b.add(STATUS_SENT_TO_CLOSING_AUTHORITY, "View complaints sent to closing authority");
        b.add(STATUS_SENT_BACK_TO_CEPC_REVIEWER, "View complaints sent back to me");
        b.add(STATUS_SENT_BACK_TO_CEPC_DO, "View complaints sent back to CEPC DO");
        b.add(STATUS_SENT_TO_OTHER_RBI_DEPARTMENT, "View complaints sent to other RBI department");
        return b.rows();
    }

    /**
     * SENT_TO_INCHARGE and COMPLAINT_ASSIGNED_TO_ME are deliberately absent: both describe the
     * in-charge's own inbox, which is what this role sees by default, so offering them as filters is a
     * dropdown entry that narrows nothing. "Sent Back to me" is SENT_BACK_TO_INCHARGE — the label
     * stays role-neutral because CEPC_DO and CEPC_CLOSING_AUTHORITY are offered the same code.
     */
    private List<RoleStatusMapping> inCharge() {
        Builder b = new Builder("CEPC_INCHARGE");
        b.add(STATUS_ALL_COMPLAINTS, "View all complaints");
        b.add(STATUS_SENT_TO_CLOSING_AUTHORITY, "View complaints sent to closing authority");
        b.add(STATUS_SENT_BACK_TO_CEPC_IN_CHARGE, "View complaints sent back to me");
        b.add(STATUS_SENT_BACK_TO_CEPC_DO, "View complaints sent back to CEPC DO");
        b.add(STATUS_SENT_BACK_TO_CEPC_REVIEWER, "View complaints sent back to CEPC reviewer");
        b.add(STATUS_CLOSED_COMPLAINTS, "View closed complaints");
        b.add(STATUS_MARK_FOR_CLOSURE, "View complaints marked for closure");
        b.add(STATUS_SENT_TO_OTHER_REGULATED_BODIES, "View complaints sent to other regulated bodies");
        b.add(STATUS_SENT_TO_OTHER_RBI_DEPARTMENT, "View complaints sent to other RBI department");
        b.add(STATUS_SENT_TO_OTHER_OFFICE, "View complaints sent to other office");
        return b.rows();
    }

    /**
     * "Sent to me" is SENT_TO_CLOSING_AUTHORITY — the forward hop into this desk, where the other roles
     * read their own inbox off a send-back code.
     */
    private List<RoleStatusMapping> closingAuthority() {
        Builder b = new Builder("CEPC_CLOSING_AUTHORITY");
        b.add(STATUS_ALL_COMPLAINTS, "View all complaints");
        b.add(STATUS_SENT_BACK_TO_CEPC_IN_CHARGE, "View complaints sent back to CEPC in-charge");
        b.add(STATUS_SENT_BACK_TO_CEPC_DO, "View complaints sent back to CEPC DO");
        b.add(STATUS_SENT_BACK_TO_CEPC_REVIEWER, "View complaints sent back to CEPC reviewer");
        b.add(STATUS_SENT_TO_CLOSING_AUTHORITY, "View complaints sent to me");
        b.add(STATUS_CLOSED_COMPLAINTS, "View closed complaints");
        b.add(STATUS_MARK_FOR_CLOSURE, "View complaints marked for closure");
        b.add(STATUS_SENT_TO_OTHER_REGULATED_BODIES, "View complaints sent to other regulated bodies");
        b.add(STATUS_SENT_TO_OTHER_RBI_DEPARTMENT, "View complaints sent to other RBI department");
        b.add(STATUS_SENT_TO_OTHER_OFFICE, "View complaints sent to other office");
        b.add(STATUS_REOPENED_COMPLAINTS, "View reopened complaints");
        return b.rows();
    }

    private List<RoleStatusMapping> admin() {
        Builder b = new Builder("CEPC_ADMIN");
        b.add(STATUS_ALL_COMPLAINTS, "View all complaints");
        b.add(STATUS_COMPLAINT_ASSIGNED_TO_ME, "View complaints assigned to me");
        b.add(STATUS_NEW_COMPLAINT, "View new complaints");
        b.add(STATUS_MEETING_SCHEDULED, "View meeting scheduled complaints");
        b.add(STATUS_SENT_TO_OTHER_RBI_DEPARTMENT, "View complaints assigned to other department");
        b.add(STATUS_SENT_TO_OTHER_REGULATED_BODIES, "View complaints assigned to other regulatory");
        b.add(STATUS_SENT_TO_CEPC_REVIEWER, "View complaints sent to CEPC reviewer");
        b.add(STATUS_SENT_TO_CEPC_IN_CHARGE, "View complaints sent to CEPC in-charge");
        b.add(STATUS_SENT_TO_CLOSING_AUTHORITY, "View complaints sent to closing authority");
        b.add(STATUS_SENT_BACK_TO_CEPC_DO, "View complaints sent back to CEPC DO");
        b.add(STATUS_SENT_BACK_TO_CEPC_IN_CHARGE, "View complaints sent back to CEPC in-charge");
        b.add(STATUS_SENT_BACK_TO_CEPC_REVIEWER, "View complaints sent back to CEPC reviewer");
        b.add(STATUS_MARK_FOR_CLOSURE, "View complaints marked for closure");
        b.add(STATUS_REOPENED_COMPLAINTS, "View re-opened complaints");
        b.add(STATUS_CLOSED_COMPLAINTS, "View closed complaints");
        return b.rows();
    }

    /** Assigns each role's sequence from call order, so reordering a list reorders that role's dropdown. */
    private static final class Builder {

        private final String roleName;
        private final List<RoleStatusMapping> rows = new ArrayList<>();

        private Builder(String roleName) {
            this.roleName = roleName;
        }

        private void add(String statusCode, String description) {
            rows.add(RoleStatusMapping.builder()
                    .roleName(roleName)
                    .statusCode(statusCode)
                    .description(description)
                    .sequence(rows.size() + 1)
                    .build());
        }

        private List<RoleStatusMapping> rows() {
            return rows;
        }
    }
}
