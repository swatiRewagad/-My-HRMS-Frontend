package com.hrms.cms.service.report;

import com.hrms.cms.entity.ReportAccessRole;
import com.hrms.cms.repository.ReportAccessRoleRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maintains the report access list for the Super-Admin screen (UST670).
 *
 * <h2>The client contract is the specification here</h2>
 * The Angular screen has existed for some time against endpoints that did not, so its expectations are
 * the only stable spec available. Three of them shape this class:
 * <ul>
 *   <li>POST receives the ENTIRE list, not one row. It is replace-all semantics.</li>
 *   <li>A new row arrives with {@code id: 0}; an existing row carries its real id.</li>
 *   <li>The response is the full persisted list, because the component does {@code set(roles)} on the
 *       reply rather than re-fetching.</li>
 * </ul>
 *
 * <h2>Why replace-all is honoured rather than corrected to a cleaner REST shape</h2>
 * Changing it would need a coordinated frontend release, and the screen is already written. The risk
 * replace-all carries — that a stale client silently deletes rows another admin added — is mitigated by
 * {@link #replaceAll} rejecting an empty list outright: the one irreversible mistake (wiping the whole
 * access list and thereby locking everyone out of reports) is the one it refuses to perform implicitly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAccessRoleAdminService {

    /**
     * Report types the access list may name. Free text here would let a typo create a row that silently
     * never matches — a grant an admin believes they made and did not.
     */
    private static final Set<String> VALID_REPORT_TYPES =
            Set.of("ALL", "RBIO", "CEPC", "CRPC", "AA");

    /**
     * Roles that may READ or WRITE the access list itself.
     *
     * <p>This is enforced in the service, not only by the {@code SecurityConfig} request matcher, and that
     * duplication is deliberate. Under the {@code dev-local} profile {@code DevLocalSecurityConfig} is
     * {@code anyRequest().permitAll()}, so the HTTP matcher does not run at all — a control that exists
     * only in the enforcing profile is absent from the profile developers and E2E suites actually use, and
     * would be reported as working by a test that never exercised it. Found exactly that way.
     *
     * <p>The table decides who may VIEW REPORTS; who may edit the table itself is a narrower, structural
     * question, so it stays in code rather than being self-referentially configurable — otherwise an admin
     * could remove their own ability to fix a mistake.
     */
    private static final Set<String> ACCESS_LIST_ADMIN_ROLES = Set.of("ADMIN", "RBIO_ADMIN");

    private final ReportAccessRoleRepository repository;
    private final RequestIdentityResolver identityResolver;

    /**
     * Refuses a caller who does not hold an administrative role.
     *
     * <p>Fails closed: no resolvable identity is a refusal, never a default.
     */
    private void requireAccessListAdmin() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        RequestIdentity identity =
                attrs == null ? null : identityResolver.resolve(attrs.getRequest());

        Set<String> roles = identity == null || identity.getRoles() == null
                ? Set.of() : identity.getRoles();

        if (roles.stream().noneMatch(ACCESS_LIST_ADMIN_ROLES::contains)) {
            log.warn("Report access-list change refused for '{}' (roles {})",
                    identity == null ? "<unidentified>" : identity.getUserId(), roles);
            throw new ReportAccessDeniedException(
                    "Only an administrator may view or change the report access list.");
        }
    }

    public List<Map<String, Object>> list() {
        requireAccessListAdmin();
        return repository.findAllByOrderByReportTypeAscRoleNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Applies the submitted list, inserting rows with no id and updating the rest.
     *
     * @throws IllegalArgumentException when the payload is empty or names an unknown report type
     */
    @Transactional
    public List<Map<String, Object>> replaceAll(List<Map<String, Object>> submitted, String actor) {
        requireAccessListAdmin();

        if (submitted == null || submitted.isEmpty()) {
            // Refused rather than obeyed. An empty list would deny every role report access at once,
            // and because the screen sends the whole list on every change, a client working from a
            // stale empty state could do it by accident. Deleting the last row must be deliberate,
            // which DELETE /access-roles/{id} makes it.
            throw new IllegalArgumentException(
                    "The access list cannot be emptied by a bulk save, because that would remove report "
                            + "access from every role. Remove rows individually instead.");
        }

        List<ReportAccessRole> saved = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Map<String, Object> row : submitted) {
            String reportType = asString(row.get("reportType"), "ALL").toUpperCase();
            String roleName = asString(row.get("roleName"), "").trim().toUpperCase();
            boolean canExport = asBoolean(row.get("canExport"));
            // The client form has no view checkbox — every row it creates is a viewer, and export is the
            // only distinction it offers. Defaulting canView to true keeps that contract; the column
            // exists so a future "listed but suspended" state does not need a migration.
            boolean canView = row.containsKey("canView") ? asBoolean(row.get("canView")) : true;

            if (roleName.isEmpty()) {
                throw new IllegalArgumentException("An access list entry has no role name.");
            }
            if (!VALID_REPORT_TYPES.contains(reportType)) {
                throw new IllegalArgumentException(
                        "Unknown report type '" + reportType + "'. Valid values: " + VALID_REPORT_TYPES);
            }

            String key = reportType + "|" + roleName;
            if (!seen.add(key)) {
                // The unique constraint would catch this, but as a 500 from a constraint violation
                // rather than a message naming the duplicate.
                throw new IllegalArgumentException(
                        "The access list names " + roleName + " for " + reportType + " twice.");
            }

            ReportAccessRole entity = repository
                    .findByReportTypeAndRoleName(reportType, roleName)
                    .orElseGet(() -> ReportAccessRole.builder()
                            .reportType(reportType)
                            .roleName(roleName)
                            .createdBy(actor)
                            .build());

            entity.setViewAllowed(canView);
            entity.setExportAllowed(canExport);
            entity.setUpdatedBy(actor);

            saved.add(repository.save(entity));
        }

        log.info("Report access list updated by '{}': {} row(s)", actor, saved.size());
        return list();
    }

    @Transactional
    public boolean delete(Long id, String actor) {
        requireAccessListAdmin();

        return repository.findById(id).map(row -> {
            repository.delete(row);
            log.info("Report access row {} ({} / {}) removed by '{}'",
                    id, row.getReportType(), row.getRoleName(), actor);
            return true;
        }).orElse(false);
    }

    /**
     * Shapes a row the way the existing client interface declares it: {@code id, reportType, roleName,
     * canExport} as a real JSON boolean. {@code canView} is additive and ignored by the current client.
     */
    private Map<String, Object> toDto(ReportAccessRole row) {
        Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("id", row.getId());
        dto.put("reportType", row.getReportType());
        dto.put("roleName", row.getRoleName());
        dto.put("canView", row.isViewAllowed());
        dto.put("canExport", row.isExportAllowed());
        return dto;
    }

    private String asString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    /** Accepts a real boolean or its string form, since form serialisation varies. */
    private boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
