package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.security.RbioIdentityResolver;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.RbioComplaintListService;
import com.hrms.cms.service.RbioRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The RBIO complaint list (S1 builds its grid on this).
 *
 * <p>{@code GET /api/v1/rbio/complaints} did not exist. {@code rbio-home.component.ts} called it, took
 * the 404 into its error branch, and rendered {@code generateSampleData()} — ten fabricated complaints
 * with realistic names and banks. The screen demoed perfectly and showed nothing real.
 *
 * <p>Deliberately a NEW controller rather than more methods on {@code WorkflowController}, which is
 * already ~935 lines and holds every RBIO endpoint. Sessions S1-S7 need to add list behaviour without
 * six of them editing one file.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rbio")
@RequiredArgsConstructor
public class RbioComplaintListController {

    private final RbioComplaintListService listService;
    private final RbioIdentityResolver identityResolver;

    /**
     * A page of RBIO complaints, scoped to the caller's role.
     *
     * <p>{@code assignedTo} is accepted because the existing frontend already sends it, but it is a
     * FILTER, not the scope: the authoritative caller identity comes from the token. A caller cannot
     * widen their view by sending someone else's id, and cannot narrow the department scope at all.
     */
    @GetMapping("/complaints")
    @RbioRoleGuard(roles = {
            "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
    public ResponseEntity<Map<String, Object>> listComplaints(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String milestone,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String assignedRole,
            @RequestParam(required = false) String officeCode,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {

        String callerRole = identityResolver.resolveRbioRole();
        String callerUserId = identityResolver.resolveActor();

        RbioComplaintListService.ListQuery query = new RbioComplaintListService.ListQuery(
                status, search, entityName, priority, milestone,
                assignedTo, assignedRole, officeCode,
                callerUserId, callerRole,
                page, size, sortBy, sortDir);

        Page<Complaint> result = listService.search(query);

        List<Map<String, Object>> items = result.getContent().stream()
                .map(listService::toListItem)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", items);
        data.put("page", result.getNumber());
        data.put("size", result.getSize());
        data.put("totalElements", result.getTotalElements());
        data.put("totalPages", result.getTotalPages());
        data.put("first", result.isFirst());
        data.put("last", result.isLast());
        data.put("sortBy", sortBy);
        data.put("sortDir", sortDir);
        data.put("appliedStatus", status);
        data.put("callerRole", callerRole);

        return buildResponse(true, "RBIO complaints retrieved", data);
    }

    /**
     * The status filter tabs this role may see (UST426-433), from RBIO_STATUS_MASTER.
     *
     * <p>Exists so S1 renders five different per-role filter lists from DATA rather than a hardcoded
     * array per role — five hardcoded arrays being how the current screens came to disagree.
     */
    @GetMapping("/status-filters")
    @RbioRoleGuard(roles = {
            "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
    public ResponseEntity<Map<String, Object>> statusFilters(
            @RequestParam(required = false) String role) {

        // An explicitly requested role is honoured for admin tooling, but the caller's own resolved role
        // is the default — so the common case cannot be steered by a query parameter.
        String resolved = identityResolver.resolveRbioRole();
        String effective = (role != null && !role.isBlank()) ? role.trim() : resolved;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", effective);
        data.put("filters", listService.filtersFor(effective));
        data.put("sortableFields", RbioComplaintListService.sortableFields());
        return buildResponse(true, "RBIO status filters", data);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
}
