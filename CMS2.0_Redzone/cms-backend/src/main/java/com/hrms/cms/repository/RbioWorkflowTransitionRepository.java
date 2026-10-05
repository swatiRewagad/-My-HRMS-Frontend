package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioWorkflowTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RbioWorkflowTransitionRepository extends JpaRepository<RbioWorkflowTransition, Long> {

    List<RbioWorkflowTransition> findByIsActive(String isActive);

    List<RbioWorkflowTransition> findByActionCodeAndRoleNameAndIsActive(
            String actionCode, String roleName, String isActive);

    /**
     * Candidate rows for an action+role, most specific first: a row naming the exact from-status
     * outranks a wildcard row. Both are legitimate — REASSIGN applies from any status, while ACCEPT
     * applies only from {@code assigned} or {@code returned} — so the resolver must prefer the
     * specific one rather than whichever the database happened to return first.
     */
    @Query("""
           SELECT t FROM RbioWorkflowTransition t
           WHERE t.isActive = 'Y'
             AND UPPER(t.actionCode) = UPPER(:action)
             AND UPPER(t.roleName) = UPPER(:role)
             AND (t.fromStatus IS NULL OR LOWER(t.fromStatus) = LOWER(:status))
           ORDER BY CASE WHEN t.fromStatus IS NULL THEN 1 ELSE 0 END, t.displayOrder
           """)
    List<RbioWorkflowTransition> resolve(@Param("action") String action,
                                         @Param("role") String role,
                                         @Param("status") String status);

    /** Whether the role is permitted this action at all, regardless of the complaint's status. */
    @Query("""
           SELECT COUNT(t) > 0 FROM RbioWorkflowTransition t
           WHERE t.isActive = 'Y'
             AND UPPER(t.actionCode) = UPPER(:action)
             AND UPPER(t.roleName) = UPPER(:role)
           """)
    boolean existsForActionAndRole(@Param("action") String action, @Param("role") String role);

    /**
     * Whether the table has ANY active row for this role.
     *
     * <p>This is what makes the table authoritative rather than merely additive. If the role is present
     * at all, the table's answer is final — so a session that REVOKES an action by deactivating its rows
     * is honoured, instead of being silently overridden by the compiled-in registry default. If the role
     * is absent entirely (unseeded database, or a role nobody has configured), the registry answers.
     */
    @Query("""
           SELECT COUNT(t) > 0 FROM RbioWorkflowTransition t
           WHERE t.isActive = 'Y' AND UPPER(t.roleName) = UPPER(:role)
           """)
    boolean knowsRole(@Param("role") String role);

    Optional<RbioWorkflowTransition> findByActionCodeAndRoleNameAndFromStatus(
            String actionCode, String roleName, String fromStatus);

    /**
     * The action codes that land a complaint in a given milestone.
     *
     * <p>DISTINCT because the table holds one row per action PER ROLE, so an action open to six roles
     * appears six times; the caller wants the set of codes, not the cross-product.
     *
     * <p>Used to answer "has a final decision been taken" without compiling the list of
     * decision-taking actions into Java — see {@code RbioFinalDecisionService}.
     */
    @Query("""
           SELECT DISTINCT UPPER(t.actionCode) FROM RbioWorkflowTransition t
           WHERE t.isActive = 'Y' AND UPPER(t.toMilestone) = UPPER(:milestone)
           """)
    List<String> findActionCodesForMilestone(@Param("milestone") String milestone);
}
