package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.security.RbioIdentityResolver;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.RbioComplaintListService;
import com.hrms.cms.service.SystemConfigService;
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

    /** UST436: the CRPC-delay band threshold, tunable without a release. */
    static final String CFG_CRPC_DELAY_DAYS = "rbio.grid.crpc_delay_days";
    static final String CFG_MAX_PAGE_SIZE = "rbio.grid.max_page_size";
    static final String CFG_BAND_PREFIX = "rbio.grid.band.colour.";

    private final RbioComplaintListService listService;
    private final RbioIdentityResolver identityResolver;
    private final SystemConfigService systemConfigService;
    private final com.hrms.cms.security.CmsPrincipalResolver principalResolver;

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
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            // ── UST439 advance search, matched server-side ──
            @RequestParam(required = false) String complaintNumber,
            @RequestParam(required = false) String complainantName,
            @RequestParam(required = false) String complainantMobile,
            @RequestParam(required = false) String complainantEmail,
            @RequestParam(required = false) String statusCode,
            @RequestParam(required = false) String complaintId,
            @RequestParam(required = false) String fromEmailId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String modeOfReceipt,
            @RequestParam(required = false) String nodalOfficerName,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String reportedFrom,
            @RequestParam(required = false) String reportedTo) {

        String callerRole = identityResolver.resolveRbioRole();
        String callerUserId = identityResolver.resolveActor();

        var advanced = new RbioComplaintListService.AdvancedSearch(
                complaintNumber, complainantName, complainantMobile, complainantEmail,
                statusCode, complaintId, fromEmailId, subject, modeOfReceipt,
                entityName, nodalOfficerName, categoryId, reportedFrom, reportedTo);

        // UST441: a one-character substring term scans the table and returns most of it. Refused with a
        // named reason rather than run, and rather than silently widened to an exact match.
        List<String> tooShort = advanced.tooShortTerms();
        if (!tooShort.isEmpty()) {
            return buildError("rbio.search.error_term_too_short",
                    "Search term is too short for: " + String.join(", ", tooShort));
        }

        // Nodal Officer / Contact Person is in the UST439 field list but COMPLAINTS has no nodal-officer
        // column — the name lives on ENTITY_OFFICE_NODAL_OFFICER, reachable only through the dirty
        // ENTITY_CODE. Refusing is deliberate: accepting the parameter and ignoring it would return a
        // result set that looks like an answer while the criterion was silently discarded, which is the
        // param-name-mismatch failure this module has already been bitten by.
        if (nodalOfficerName != null && !nodalOfficerName.isBlank()) {
            return buildError("rbio.search.error_nodal_officer_unsupported",
                    "Searching by Nodal Officer is not available: the complaint record does not store "
                            + "the nodal officer, and matching it through the entity code would silently "
                            + "miss complaints whose entity is not mapped.");
        }

        RbioComplaintListService.ListQuery query = new RbioComplaintListService.ListQuery(
                status, search, entityName, priority, milestone,
                assignedTo, assignedRole, officeCode,
                callerUserId, callerRole,
                page, size, sortBy, sortDir, advanced);

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
        // UST631: the UI uses this to hide the Final Decision closure options. It is a CONVENIENCE — the
        // control is @RequiresAuthority on the close action itself, so direct URL access is refused whatever
        // the UI chose to render. Sending it here saves the grid a second round trip per row.
        data.put("canCloseFinal",
                principalResolver.resolve().has(com.hrms.cms.security.CmsAuthority.RBIO_COMPLAINT_CLOSE_FINAL));
        // UST442: lets the UI distinguish "your search matched nothing" from "you have no work", so it can
        // echo the criteria back instead of showing a bare empty grid.
        data.put("searchApplied", advanced.any());
        data.put("emptyMessageKey", items.isEmpty()
                ? (advanced.any() ? "rbio.search.no_results" : "rbio.grid.no_complaints")
                : null);

        return buildResponse(true, "RBIO complaints retrieved", data);
    }

    /**
     * Grid presentation configuration: the UST436 colour bands and the CRPC-delay threshold.
     *
     * <p>Served from SYSTEM_CONFIG so the palette and the threshold can change without a release. The
     * bands are DERIVED per row at render time from the complaint's current state — there is no band
     * column, because a stored band would contradict the status beside it the moment a complaint moved.
     */
    @GetMapping("/grid-config")
    @RbioRoleGuard(roles = {
            "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
    public ResponseEntity<Map<String, Object>> gridConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("crpcDelayDays", systemConfigService.getInt(CFG_CRPC_DELAY_DAYS, 3));
        data.put("maxPageSize", systemConfigService.getInt(CFG_MAX_PAGE_SIZE, 100));
        data.put("bands", List.of(
                band("WHITE", CFG_BAND_PREFIX + "white", "#ffffff", "rbio.band.white"),
                band("RED", CFG_BAND_PREFIX + "red", "#fee2e2", "rbio.band.red"),
                band("GREEN", CFG_BAND_PREFIX + "green", "#dcfce7", "rbio.band.green"),
                band("YELLOW", CFG_BAND_PREFIX + "yellow", "#fef9c3", "rbio.band.yellow"),
                band("PINK", CFG_BAND_PREFIX + "pink", "#fce7f3", "rbio.band.pink"),
                band("BLUE", CFG_BAND_PREFIX + "blue", "#dbeafe", "rbio.band.blue")));
        return buildResponse(true, "RBIO grid configuration", data);
    }

    private Map<String, Object> band(String code, String configKey, String fallback, String labelKey) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("code", code);
        b.put("colour", systemConfigService.getString(configKey, fallback));
        b.put("labelKey", labelKey);
        return b;
    }

    private ResponseEntity<Map<String, Object>> buildError(String messageKey, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("messageKey", messageKey);
        response.put("data", null);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.badRequest().body(response);
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
