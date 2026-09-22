package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.repository.ClosureClauseMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which closure clauses a role may use, and enforces it (UST581-584, 769, 774).
 *
 * <p>WHY THIS REPLACES THE OLD ENDPOINT. {@code WorkflowController.getClosureClauses} hand-built a
 * {@code List<Map>} and never read {@code CLOSURE_CLAUSE_MASTER}, even though that table already models
 * exactly this: {@code restricted_to_roles}, {@code appealable_by_complainant},
 * {@code appealable_by_entity}, {@code scheme_version} and {@code effective_from}/{@code effective_to}. The
 * Deputy branch was an empty block and the Reviewer branch was two comments, so every role except Ombudsman
 * received the identical list — UST582/583 were unimplemented and UST584 (appealable clauses withheld from
 * anyone but the Ombudsman) was actively violated. Worse, the {@code role} came in as an untrusted query
 * parameter with no guard, and the Angular caller mapped roles client-side with
 * {@code role.includes('OMBUDSMAN')} tested before {@code includes('DEPUTY')} — so a Deputy Ombudsman
 * matched the Ombudsman branch and was handed the award clauses.
 *
 * <p>FILTERING THE PICKER IS NOT THE CONTROL. {@link #assertRoleMayUseClause} is, and the closure path calls
 * it on the way in. A filtered list is a courtesy to the officer; the refusal is what makes the tier real.
 *
 * <p>THE 2026 CLAUSES 16(5) AND 16(6) HAVE BEEN REMOVED. They were invented — present in no migration and no
 * seeder — flagged {@code newIn2026}, and served unconditionally to every role with no date gate, while
 * {@code ClosureClauseMasterSeeder} explicitly refuses to invent 2026 codes pending RBI notification and
 * {@code V10__eligibility_question_master.sql} actively rewrites '2026' back to '2021'. A complaint closed
 * under a clause absent from the master becomes permanently unappealable, because
 * {@code AppealClassificationService} correctly fails closed. The date-gated, scheme-versioned MECHANISM is
 * built here and will serve a 2026 set the moment one is seeded; the CONTENT must come from the gazette.
 * No legal text is authored in this codebase.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClosureClauseAccessService {

    private final ClosureClauseMasterRepository clauseRepository;

    @Value("${cms.eligibility.scheme-version:RBIOS_2021}")
    private String defaultSchemeVersion;

    /**
     * The clause set a role may cite for a complaint.
     *
     * <p>The scheme version comes from the COMPLAINT where it has one, so a reprocessed complaint is offered
     * the clauses it was originally closed under rather than today's set (UST769). This mirrors
     * {@code AppealClassificationService.applicableSchemeVersion}, which is the only other place that already
     * gets this right.
     */
    @Transactional(readOnly = true)
    public List<ClosureClauseMaster> clausesFor(Complaint complaint, String role) {
        String scheme = applicableSchemeVersion(complaint);
        List<ClosureClauseMaster> inForce = clauseRepository.findAllInForce(scheme, LocalDate.now());
        return inForce.stream().filter(clause -> roleMayUse(clause, role)).toList();
    }

    /** The clause set for a scheme version, with no complaint in scope. */
    @Transactional(readOnly = true)
    public List<ClosureClauseMaster> clausesForScheme(String schemeVersion, String role) {
        String scheme = (schemeVersion == null || schemeVersion.isBlank()) ? defaultSchemeVersion : schemeVersion;
        return clauseRepository.findAllInForce(scheme, LocalDate.now()).stream()
                .filter(clause -> roleMayUse(clause, role))
                .toList();
    }

    /**
     * Refuses a closure citing a clause the role may not use, or one absent from the master (UST584).
     *
     * <p>Fails closed on an unknown clause. Accepting it would persist a clause that
     * {@code AppealClassificationService} cannot classify, which denies the citizen an appeal — strictly worse
     * than refusing the closure and asking the officer to pick a configured clause.
     */
    @Transactional(readOnly = true)
    public void assertRoleMayUseClause(Complaint complaint, String clauseCode, String role) {
        if (clauseCode == null || clauseCode.isBlank()) {
            return;
        }
        String scheme = applicableSchemeVersion(complaint);
        ClosureClauseMaster clause = clauseRepository
                .findInForce(scheme, clauseCode.trim(), LocalDate.now())
                .orElseThrow(() -> new ClauseNotPermittedException(
                        "rbio.closure.error_clause_unknown",
                        "Closure clause '" + clauseCode + "' is not configured for scheme " + scheme
                                + ", so it cannot be cited. A complaint closed under an unconfigured clause "
                                + "could not be appealed.",
                        clauseCode, role));

        if (!roleMayUse(clause, role)) {
            throw new ClauseNotPermittedException(
                    "rbio.closure.error_clause_not_permitted_for_role",
                    "Role '" + role + "' is not permitted to cite closure clause '" + clauseCode + "'.",
                    clauseCode, role);
        }
    }

    /**
     * Whether a role may use a clause.
     *
     * <p>A null or blank {@code restrictedToRoles} means unrestricted, which is how the seeded data expresses
     * "available to all". A restricted clause with a BLANK role is refused rather than allowed: an
     * unidentified caller must not receive the appealable set, which is precisely the hole that made the role
     * query-parameter exploitable.
     */
    private boolean roleMayUse(ClosureClauseMaster clause, String role) {
        String restricted = clause.getRestrictedToRoles();
        if (restricted == null || restricted.isBlank()) {
            return true;
        }
        if (role == null || role.isBlank()) {
            return false;
        }
        return permittedRoles(restricted).contains(normaliseRole(role));
    }

    /**
     * Splits the comma-separated column into whole tokens.
     *
     * <p>Whole-token comparison, not {@code contains}: a substring test would match {@code ADMIN} inside
     * {@code RBIO_ADMIN} and quietly widen every clause restricted to administrators.
     */
    private Set<String> permittedRoles(String restrictedToRoles) {
        return new LinkedHashSet<>(Arrays.stream(restrictedToRoles.split(","))
                .map(this::normaliseRole)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /**
     * Normalises a role to the vocabulary the master table uses.
     *
     * <p>The seeded {@code OMBUDSMAN_ONLY} value is {@code "OMBUDSMAN,RBIO_ADMIN,ADMIN"}, while the roles
     * arriving from Keycloak are prefixed — {@code RBIO_OMBUDSMAN}, {@code ROLE_RBIO_OMBUDSMAN}. Stripping the
     * prefixes here is what makes an {@code RBIO_OMBUDSMAN} match {@code OMBUDSMAN} without also making
     * {@code RBIO_DEPUTY_OMBUDSMAN} match it, because the comparison is on the whole remaining token.
     */
    private String normaliseRole(String role) {
        String normalised = role.trim().toUpperCase();
        if (normalised.startsWith("ROLE_")) {
            normalised = normalised.substring("ROLE_".length());
        }
        if (normalised.equals("RBIO_OMBUDSMAN")) {
            return "OMBUDSMAN";
        }
        return normalised;
    }

    /**
     * The complaint's own scheme version, falling back to the configured default.
     *
     * <p>Per-complaint rather than global, so reprocessing an older complaint offers the clause set it was
     * closed under. This is the gate UST769 needs; it does not depend on a 2026 clause list existing.
     */
    private String applicableSchemeVersion(Complaint complaint) {
        if (complaint != null && complaint.getSchemeVersion() != null
                && !complaint.getSchemeVersion().isBlank()) {
            return complaint.getSchemeVersion();
        }
        return defaultSchemeVersion;
    }

    /** Refuses a clause a role may not cite, or one absent from the master. Maps to 409 CONFLICT. */
    @lombok.Getter
    public static class ClauseNotPermittedException extends RuntimeException {
        private final String messageKey;
        private final String clauseCode;
        private final String role;

        public ClauseNotPermittedException(String messageKey, String message, String clauseCode, String role) {
            super(message);
            this.messageKey = messageKey;
            this.clauseCode = clauseCode;
            this.role = role;
        }
    }
}
