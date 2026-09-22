package com.hrms.cms.service.report;

import com.hrms.cms.entity.ReportAccessRole;
import com.hrms.cms.entity.RbioStaffProfile;
import com.hrms.cms.repository.ReportAccessRoleRepository;
import com.hrms.cms.repository.RbioStaffProfileRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides what the calling user may see in a report, from the SSO token — never from a header.
 *
 * <h2>The defect this replaces</h2>
 * {@code ReportBuilderController} bound the caller's identity from {@code X-User-Role} (default
 * {@code "SENIOR"}) and {@code X-User-Department} (default {@code ""}), and
 * {@code QueryCompiler.buildAuthScope} granted unrestricted access for a blank department OR the role
 * {@code SENIOR}. Both defaults were the most privileged value available, so a request with no
 * headers at all read every complaint in the database. Commit 41d7670 eliminated this
 * header-precedence pattern from four role-guard aspects; this controller was missed because it used
 * Spring {@code @RequestHeader} binding instead of a resolver, making it the seventh instance.
 *
 * <h2>Why it delegates identity rather than decoding the token again</h2>
 * {@link RequestIdentityResolver} already implements the JWT-first, header-only-under-dev-local
 * ordering, with the load-bearing invariant that a token yielding any role short-circuits so a header
 * can never override or augment it. There are already nine copies of that decode logic in this
 * codebase; a tenth would be another place for the ordering to be got wrong. This class adds only
 * what is specific to reports: the view/export capability and the department scope.
 *
 * <h2>Why territory comes from RBIO_STAFF_PROFILE and not from the request</h2>
 * The old {@code X-User-Department} header let the caller nominate their own scope, which is not a
 * control. The office posting is read from the staff profile keyed on the resolved user id — the same
 * table that owns office postings for assignment routing — so a caller cannot widen their own
 * territory. A staff member with no profile row gets no unrestricted fallback; see
 * {@link #resolveDepartmentScope}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAccessService {

    /**
     * Roles that see every department. These are the supervisory/administrative roles the access
     * table marks as unrestricted; membership is decided by the role name carried in the token, and
     * the list is intentionally short. It is NOT the old {@code "SENIOR"} string, which was never a
     * real Keycloak role — it was only ever reachable as a header default, which is what made the
     * previous check trivially bypassable.
     */
    private static final Set<String> UNRESTRICTED_ROLES = Set.of(
            "ADMIN", "RBIO_ADMIN", "CEPD_ADMIN", "AA_ADMIN", "AA_SECRETARIAT",
            "RBIO_OMBUDSMAN", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_SECRETARY");

    private final ReportAccessRoleRepository accessRoleRepository;
    private final RbioStaffProfileRepository staffProfileRepository;
    private final RequestIdentityResolver identityResolver;

    /**
     * Resolves the scope for the current request, for the given report type.
     *
     * @param reportType {@code RBIO | CEPC | CRPC | AA}, or null to mean "any report"
     * @return never null; {@link ReportScope#DENIED} when the caller cannot be identified or holds no
     *         configured report access
     */
    public ReportScope resolveScope(String reportType) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            // No request bound. This happens on the scheduler thread, which must not silently obtain
            // an unrestricted scope — that was how scheduled reports came to email unscoped output.
            log.debug("No bound request; report scope denied");
            return ReportScope.DENIED;
        }

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null || identity.getUserId() == null) {
            log.warn("Report access denied: the request carried no resolvable identity");
            return ReportScope.DENIED;
        }

        Set<String> roles = identity.getRoles() == null ? Set.of() : identity.getRoles();
        if (roles.isEmpty()) {
            log.warn("Report access denied for '{}': the token granted no roles", identity.getUserId());
            return ReportScope.DENIED;
        }

        boolean canView = false;
        boolean canExport = false;

        List<ReportAccessRole> configured = accessRoleRepository.findByRoleNameIn(new ArrayList<>(roles));
        for (ReportAccessRole row : configured) {
            boolean typeMatches = ReportAccessRole.TYPE_ALL.equalsIgnoreCase(row.getReportType())
                    || reportType == null
                    || reportType.equalsIgnoreCase(row.getReportType());
            if (!typeMatches) {
                continue;
            }
            // Capabilities UNION across the caller's roles: holding any role that permits export is
            // enough. A role that forbids export does not veto one that allows it, because the roles
            // are additive grants rather than a precedence ladder — that is how Keycloak realm roles
            // behave everywhere else in this application, and disagreeing here would be surprising.
            canView |= row.isViewAllowed();
            canExport |= row.isExportAllowed();
        }

        if (!canView) {
            // An empty or non-matching access table is a REFUSAL, not a free pass. The Angular screen
            // used to read an empty list as "everyone may export" because its computed fell through
            // to `return true`; the server must not repeat that reading.
            log.warn("Report access denied for '{}' (roles {}): no REPORT_ACCESS_ROLE row grants view",
                    identity.getUserId(), roles);
            return ReportScope.DENIED;
        }

        String departmentScope = resolveDepartmentScope(identity, roles);

        return new ReportScope(identity.getUserId(), departmentScope, true, canExport, roles);
    }

    /**
     * The department this caller is confined to. Null means unrestricted.
     *
     * <p>A caller holding an unrestricted role sees everything. Everyone else is confined to the
     * department on their staff profile. A staff member with no profile row, or a profile with no
     * office posting, is confined to {@link ReportScope#NO_DEPARTMENT} — a sentinel that matches no
     * complaint. That is deliberately inconvenient rather than permissive: the alternative (treating
     * "no posting on file" as "sees everything") is the precise shape of the bug being fixed, where a
     * blank department meant unrestricted.
     */
    private String resolveDepartmentScope(RequestIdentity identity, Set<String> roles) {
        if (roles.stream().anyMatch(UNRESTRICTED_ROLES::contains)) {
            return null;
        }

        String department = staffProfileRepository.findByUserId(identity.getUserId())
                .map(RbioStaffProfile::getOfficeCode)
                .filter(code -> code != null && !code.isBlank())
                .orElse(null);

        if (department == null) {
            log.warn("Report scope for '{}' matches nothing: no office posting on the staff profile",
                    identity.getUserId());
            return ReportScope.NO_DEPARTMENT;
        }
        return department;
    }

    /** True when the caller may export output for this report type. */
    public boolean canExport(String reportType) {
        return resolveScope(reportType).canExport();
    }

    /**
     * Resolves a scope for a named user with NO bound HTTP request — the scheduled-report path.
     *
     * <p>Needed because a {@code @Scheduled} thread has no token to read, and the previous code solved
     * that by passing the unrestricted sentinel: every scheduled report ran across all departments and
     * emailed the output off-site. That made scheduling a standing bulk-PII export which bypassed the
     * scoping the interactive endpoint applied.
     *
     * <p>Roles come from {@code RBIO_STAFF_PROFILE.PRIMARY_ROLE} rather than from a token. That is a
     * weaker source — a user's Keycloak roles may have changed since the profile row was written — so it
     * is used ONLY here, and it fails closed: a user with no profile row, no primary role, or no granting
     * access row gets {@link ReportScope#DENIED} and the scheduled report is skipped. Skipping a
     * scheduled email is a visible, recoverable inconvenience; sending one built from an over-wide scope
     * is not recoverable at all, because the mail has left the building.
     */
    public ReportScope resolveScopeForOwner(String username, String reportType) {
        if (username == null || username.isBlank()) {
            return ReportScope.DENIED;
        }

        RbioStaffProfile profile = staffProfileRepository.findByUserId(username).orElse(null);
        if (profile == null || profile.getPrimaryRole() == null || profile.getPrimaryRole().isBlank()) {
            log.warn("Scheduled report scope denied for '{}': no staff profile or no primary role",
                    username);
            return ReportScope.DENIED;
        }

        Set<String> roles = Set.of(profile.getPrimaryRole().trim().toUpperCase());

        boolean canView = false;
        boolean canExport = false;
        for (ReportAccessRole row : accessRoleRepository.findByRoleNameIn(new ArrayList<>(roles))) {
            boolean typeMatches = ReportAccessRole.TYPE_ALL.equalsIgnoreCase(row.getReportType())
                    || reportType == null
                    || reportType.equalsIgnoreCase(row.getReportType());
            if (typeMatches) {
                canView |= row.isViewAllowed();
                canExport |= row.isExportAllowed();
            }
        }

        if (!canView) {
            log.warn("Scheduled report scope denied for '{}' (role {}): no granting access row",
                    username, roles);
            return ReportScope.DENIED;
        }

        String departmentScope = roles.stream().anyMatch(UNRESTRICTED_ROLES::contains)
                ? null
                : (profile.getOfficeCode() == null || profile.getOfficeCode().isBlank()
                        ? ReportScope.NO_DEPARTMENT
                        : profile.getOfficeCode());

        return new ReportScope(username, departmentScope, true, canExport, roles);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
