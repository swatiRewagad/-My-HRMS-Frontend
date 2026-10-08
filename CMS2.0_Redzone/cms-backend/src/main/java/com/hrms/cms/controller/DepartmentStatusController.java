package com.hrms.cms.controller;

import com.hrms.cms.entity.CepcDashboardFilter;
import com.hrms.cms.entity.RoleStatusMapping;
import com.hrms.cms.repository.CepcDashboardFilterRepository;
import com.hrms.cms.repository.RoleStatusMappingRepository;
import com.hrms.cms.security.CepcIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The status-filter vocabulary a role may choose from, per department.
 *
 * <p>Backs the CEPC dashboard's status dropdown ({@code loadStatusCodes()}). Reads {@code ROLE_STATUS_MAPPING}
 * for the role-to-status matrix and {@code CEPC_DASHBOARD_FILTER} for each code's label.
 *
 * <p>Returns {@code {code, label}} pairs rather than bare strings because the two differ: {@code code} is the
 * normalised value posted back to the search endpoint ({@code SENT_BACK_TO_CEPC_DO}), {@code label} is what
 * the user reads ("Sent Back to CEPC Dealing Official"). Deriving one from the other in the frontend would put
 * the wording of every dropdown entry in a string-manipulation function.
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Slf4j
public class DepartmentStatusController {

    private final RoleStatusMappingRepository mappingRepo;
    private final CepcDashboardFilterRepository filterRepo;
    private final CepcIdentityResolver identity;

    /**
     * @param code the department code; only {@code CEPC} has a filter vocabulary in this table
     * @param role the role whose list is wanted — honoured only if the caller actually holds it
     */
    @GetMapping("/{code}/role-status")
    public ResponseEntity<Map<String, Object>> roleStatus(@PathVariable String code,
                                                          @RequestParam(required = false) String role) {
        if (!"CEPC".equalsIgnoreCase(code == null ? "" : code.trim())) {
            // Named rather than guessed at: RBIO has its own filter endpoint over its own master table, and
            // answering for it here would hand back CEPC codes under an RBIO department code.
            log.debug("role-status requested for department '{}', which has no filter vocabulary here", code);
            return ResponseEntity.ok(body(false, "departments.role_status.error.unsupported_department", List.of()));
        }

        String effectiveRole = effectiveRole(role);
        if (effectiveRole == null) {
            // An unroled caller gets nothing rather than a default list. resolveCepcRole() returns null
            // precisely so this case is distinguishable, and offering CEPC_DO's dropdown to someone who holds
            // no CEPC role would advertise queues they cannot act on.
            log.debug("role-status requested by a caller holding no CEPC role");
            return ResponseEntity.ok(body(true, "OK", List.of()));
        }

        Map<String, String> labels = filterRepo
                .findByDimensionAndIsActiveOrderByDisplayOrderAsc(CepcDashboardFilter.DIMENSION_STATUS, "Y")
                .stream()
                .collect(Collectors.toMap(CepcDashboardFilter::getFilterCode,
                        CepcDashboardFilter::getLabelEn, (a, b) -> a));

        List<Map<String, String>> entries = new ArrayList<>();
        for (RoleStatusMapping m : mappingRepo.findByRoleNameOrderBySequenceAsc(effectiveRole)) {
            String label = labels.get(m.getStatusCode());
            if (label == null) {
                // Withheld on purpose. CepcComplaintSearchService resolves the selected code against
                // CEPC_DASHBOARD_FILTER and fails closed when it finds nothing, so offering this entry would
                // give the user a dropdown option that silently returns an empty grid every time.
                log.warn("Status '{}' is mapped to role {} but has no active CEPC_DASHBOARD_FILTER row — "
                        + "withholding it from the dropdown", m.getStatusCode(), effectiveRole);
                continue;
            }
            entries.add(Map.of("code", m.getStatusCode(), "label", label,
                    "labelKey", labelKey(m.getStatusCode())));
        }

        return ResponseEntity.ok(body(true, "OK", entries));
    }

    /**
     * The role whose list to serve, or null when the caller holds no CEPC role.
     *
     * <p>The requested role is used only when the caller holds it. Otherwise their own resolved CEPC role is
     * used — a caller cannot type {@code role=CEPC_CLOSING_AUTHORITY} into the query string to be offered
     * closure-queue filters they have no business selecting.
     */
    private String effectiveRole(String requested) {
        String own = identity.resolveCepcRole();
        if (requested == null || requested.isBlank()) {
            return own;
        }
        String wanted = requested.trim().toUpperCase(Locale.ROOT);
        return identity.resolveRoles().contains(wanted) ? wanted : own;
    }

    /**
     * The {@code TRANSLATION_KEYS.CODE} holding this dropdown entry's wording.
     *
     * <p>Keyed on the filter code rather than on {@code LABEL_EN} so the vocabulary is stable: the labels are
     * sentence-cased prose ("Sent Back to CEPC Dealing Official") that an edit would silently re-point.
     * {@code CEPC_DASHBOARD_FILTER} is seeded from a fixed set of constants, so the key set is closed.
     */
    private static String labelKey(String filterCode) {
        return "ui.filter." + filterCode.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> body(boolean success, String message, List<?> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", success);
        out.put("message", message);
        out.put("data", data);
        return out;
    }
}
