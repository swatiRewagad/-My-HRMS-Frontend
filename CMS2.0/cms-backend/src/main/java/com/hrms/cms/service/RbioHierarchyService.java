package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.enums.DepartmentConstants;
import com.rbi.cms.common.enums.RoleConstants;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Enforces the RBIO escalation ladder RBIO_DO -> RBIO_REVIEWER -> RBIO_DEPUTY_OMBUDSMAN ->
 * RBIO_OMBUDSMAN. A complaint may only move one rung up at a time, and only the officer currently
 * holding it may move it. RBIO_ADMIN is exempt from the ladder so it can cover for absent officers
 * and escalate past a rung.
 */
@Service
public class RbioHierarchyService {

    private static final List<String> LADDER = List.of(
            RoleConstants.RBIO_DO,
            RoleConstants.RBIO_REVIEWER,
            RoleConstants.RBIO_DEPUTY_OMBUDSMAN,
            RoleConstants.RBIO_OMBUDSMAN);

    // The forward targets and the Angular screens' own role names ('DO', 'REVIEWER', ...) are the same
    // vocabulary, unprefixed, while Keycloak and COMPLAINTS.ASSIGNED_ROLE use the RBIO_ prefix.
    private static final Map<String, String> RUNG_BY_ALIAS = Map.of(
            "DO", RoleConstants.RBIO_DO,
            "DEALING_OFFICER", RoleConstants.RBIO_DO,
            "REVIEWER", RoleConstants.RBIO_REVIEWER,
            "DEPUTY_OMBUDSMAN", RoleConstants.RBIO_DEPUTY_OMBUDSMAN,
            "OMBUDSMAN", RoleConstants.RBIO_OMBUDSMAN);

    private static final Map<String, String> ROLE_STATUSES = Map.of(
            RoleConstants.RBIO_DO, ComplaintStatus.SENT_BACK_TO_DO.name(),
            RoleConstants.RBIO_REVIEWER, ComplaintStatus.SENT_TO_REVIEWER.name(),
            RoleConstants.RBIO_DEPUTY_OMBUDSMAN, ComplaintStatus.SENT_TO_DEPUTY_OMBUDSMAN.name(),
            RoleConstants.RBIO_OMBUDSMAN, ComplaintStatus.SENT_TO_OMBUDSMAN.name());

    /** A rejected transition, carrying the HTTP status the controller should answer with. */
    public record Denial(HttpStatus status, String message) {}

    /** The RBIO role a forward target hands the complaint to, or null if the target leaves the ladder. */
    public String roleForTarget(String target) {
        return RUNG_BY_ALIAS.get(bareName(target));
    }

    public String statusForRole(String role) {
        return ROLE_STATUSES.get(normalizeRole(role));
    }

    /** Canonicalises a role to its {@code RBIO_*} spelling; unknown roles are returned uppercased. */
    public String normalizeRole(String role) {
        String value = bareName(role);
        if (value == null) {
            return null;
        }
        return RUNG_BY_ALIAS.getOrDefault(value, value);
    }

    private String bareName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return value.startsWith("ROLE_") ? value.substring("ROLE_".length()) : value;
    }

    public boolean isRbio(Complaint complaint) {
        return DepartmentConstants.DEPT_RBIO.equals(
                DepartmentConstants.canonicalize(complaint.getDepartment()));
    }

    /**
     * Whether the caller may change the complaint rather than only read it. Editing is the holder's
     * privilege: the officer the complaint is assigned to edits, everyone else views. RBIO_ADMIN edits
     * regardless, so it can act for an absent officer. An unassigned complaint locks nobody out.
     */
    public boolean canEdit(String assignedOfficer, String caller, Collection<String> callerRoles) {
        if (isRbioAdmin(callerRoles)) {
            return true;
        }
        if (assignedOfficer == null || assignedOfficer.isBlank()) {
            return true;
        }
        return caller != null && assignedOfficer.trim().equalsIgnoreCase(caller.trim());
    }

    public boolean isRbioAdmin(Collection<String> callerRoles) {
        return callerRoles != null && callerRoles.stream()
                .map(this::normalizeRole)
                .anyMatch(RoleConstants.RBIO_ADMIN::equals);
    }

    /**
     * Validates a forward along the ladder. Returns empty when the move is allowed, or when the
     * complaint is not an RBIO complaint — CEPC and CRPC reuse target keywords such as
     * {@code REVIEWER} for their own roles, so their transitions are none of this service's business.
     */
    public Optional<Denial> validateForward(Complaint complaint, String target, String callerRole,
                                            String performedBy, String newStatus) {
        if (!isRbio(complaint)) {
            return Optional.empty();
        }

        if (newStatus != null && newStatus.equalsIgnoreCase(complaint.getStatus())) {
            return deny(HttpStatus.CONFLICT, "Complaint " + complaint.getComplaintNumber()
                    + " is already at status " + complaint.getStatus() + " — nothing to forward.");
        }

        // Closures and forwards out of RBIO (other office, other regulator) are not ladder moves.
        String targetRole = roleForTarget(target);
        if (targetRole == null) {
            return Optional.empty();
        }

        String actingRole = normalizeRole(callerRole);
        if (actingRole == null) {
            actingRole = normalizeRole(complaint.getAssignedRole());
        }

        // RBIO_ADMIN covers absent officers and escalates past a rung, so it skips both checks below.
        if (RoleConstants.RBIO_ADMIN.equals(actingRole)) {
            return Optional.empty();
        }

        Optional<Denial> holderDenial = validateHolder(complaint, performedBy);
        if (holderDenial.isPresent()) {
            return holderDenial;
        }

        // A role outside the ladder (e.g. an office HEAD) has its own flows; only rungs are ordered.
        int from = LADDER.indexOf(actingRole);
        if (from < 0) {
            return Optional.empty();
        }

        // Downward moves are send-backs and may skip rungs; upward moves must climb exactly one.
        int to = LADDER.indexOf(targetRole);
        if (to == from) {
            return deny(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot forward complaint "
                    + complaint.getComplaintNumber() + " to its own role " + targetRole + ".");
        }
        if (to > from + 1) {
            return deny(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot skip the RBIO hierarchy: " + actingRole
                    + " must forward to " + LADDER.get(from + 1) + " before " + targetRole
                    + ". Escalating past a rung requires RBIO_ADMIN.");
        }

        return Optional.empty();
    }

    /**
     * Validates an RBIO_ADMIN reassignment: the new role must be the current one (absence cover) or
     * a higher one (escalation), never lower, and a same-role move must actually change the person.
     */
    public Optional<Denial> validateAdminReassign(Complaint complaint, String targetRole, String targetOfficer) {
        if (!isRbio(complaint)) {
            return deny(HttpStatus.UNPROCESSABLE_ENTITY, "Complaint " + complaint.getComplaintNumber()
                    + " does not belong to RBIO; it cannot be reassigned within the RBIO hierarchy.");
        }
        if (targetOfficer == null || targetOfficer.isBlank()) {
            return deny(HttpStatus.BAD_REQUEST, "assignedTo is required to reassign a complaint.");
        }

        int to = LADDER.indexOf(normalizeRole(targetRole));
        if (to < 0) {
            return deny(HttpStatus.UNPROCESSABLE_ENTITY, targetRole
                    + " is not an RBIO hierarchy role. Allowed: " + String.join(" -> ", LADDER));
        }

        int from = LADDER.indexOf(normalizeRole(complaint.getAssignedRole()));
        if (from >= 0 && to < from) {
            return deny(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot reassign down from "
                    + LADDER.get(from) + " to " + LADDER.get(to) + ".");
        }
        if (to == from && targetOfficer.equalsIgnoreCase(complaint.getAssignedOfficer())) {
            return deny(HttpStatus.CONFLICT, "Complaint " + complaint.getComplaintNumber()
                    + " is already assigned to " + targetOfficer + " as " + targetRole + ".");
        }
        return Optional.empty();
    }

    /**
     * TODO: this trusts the caller-supplied {@code performedBy} because cms-backend does not validate
     * JWTs (SecurityConfig permits all requests and RbioRoleGuardAspect decodes tokens without
     * verifying signatures), so it is spoofable. Replace with the authenticated principal once the
     * backend becomes an OAuth2 resource server.
     */
    private Optional<Denial> validateHolder(Complaint complaint, String performedBy) {
        String holder = complaint.getAssignedOfficer();
        if (canEdit(holder, performedBy, List.of())) {
            return Optional.empty();
        }
        return deny(HttpStatus.FORBIDDEN, "Complaint " + complaint.getComplaintNumber()
                + " is held by " + holder + "; only that officer or RBIO_ADMIN may forward it.");
    }

    private Optional<Denial> deny(HttpStatus status, String message) {
        return Optional.of(new Denial(status, message));
    }
}
