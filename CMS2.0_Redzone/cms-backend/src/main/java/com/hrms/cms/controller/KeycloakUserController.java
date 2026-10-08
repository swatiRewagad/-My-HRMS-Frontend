package com.hrms.cms.controller;

import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/keycloak")
@RequiredArgsConstructor
public class KeycloakUserController {

    /** The workload ceiling assumed for an officer with no pool row of their own. */
    private static final int DEFAULT_MAX_WORKLOAD = 20;

    private final KeycloakUserService keycloakUserService;
    private final OfficeCodeMasterRepository officeCodeMasterRepository;
    private final AaOfficerPoolRepository officerPoolRepository;
    private final ComplaintRoutingService complaintRoutingService;

    @GetMapping("/users/deos")
    public Map<String, Object> getDeos() {
        List<Map<String, Object>> deos = keycloakUserService.getDeos();
        List<Map<String, Object>> enriched = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> deo : deos) {
            Map<String, Object> enrichedDeo = new LinkedHashMap<>(deo);
            enrichedDeo.put("isActive", Boolean.TRUE.equals(deo.get("enabled")));
            enrichedDeo.put("isOnLeave", false);
            enrichedDeo.put("maxThreshold", 20);
            enrichedDeo.put("currentAssignedCount", 0);
            enrichedDeo.put("sortOrder", sortOrder++);
            enriched.add(enrichedDeo);
        }
        return wrapResponse(enriched);
    }

    @GetMapping("/users/reviewers")
    public Map<String, Object> getReviewers() {
        List<Map<String, Object>> reviewers = keycloakUserService.getReviewers();
        List<Map<String, Object>> enriched = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> reviewer : reviewers) {
            Map<String, Object> enrichedReviewer = new LinkedHashMap<>(reviewer);
            enrichedReviewer.put("isActive", Boolean.TRUE.equals(reviewer.get("enabled")));
            enrichedReviewer.put("isOnLeave", false);
            enrichedReviewer.put("maxLoad", 25);
            enrichedReviewer.put("currentLoad", 0);
            enrichedReviewer.put("region", "");
            enrichedReviewer.put("sortOrder", sortOrder++);
            enriched.add(enrichedReviewer);
        }
        return wrapResponse(enriched);
    }

    @GetMapping("/users/all")
    public Map<String, Object> getAllCrpcUsers() {
        return wrapResponse(keycloakUserService.getAllCrpcUsers());
    }

    @GetMapping("/users/by-role")
    public List<Map<String, Object>> getUsersByRole(@RequestParam String role) {
        return keycloakUserService.getUsersByRole(role);
    }

    /**
     * The offices the forward and transfer pickers offer.
     *
     * <p>Inactive offices are excluded rather than returned with a flag: an officer who can see a
     * decommissioned office in the list will eventually pick one, and the complaint then sits in a queue
     * nobody reads.
     */
    @GetMapping("/offices")
    public Map<String, Object> getOffices() {
        List<Map<String, Object>> offices = officeCodeMasterRepository.findByIsActiveTrueOrderByOfficeNameAsc()
                .stream().map(KeycloakUserController::officeView).toList();
        return wrapResponse(offices);
    }

    /**
     * Who holds a role, and whether they can currently take work.
     *
     * <p>Membership comes from Keycloak, but the availability facts — office, leave, threshold — come from
     * {@code wf_officer_pool}, which is the same table {@code DurableRoundRobinAssigner} treats as its
     * exclusion list. Reading one and assigning from the other would let this screen show an officer as
     * available whom the assigner will never pick.
     *
     * <p><b>The two sources are unioned, not intersected.</b> Keycloak is not reachable in every
     * environment and {@code getUsersByRole} answers with an empty list when it cannot be asked, which
     * would empty the assignment dialogs entirely. A pool row is itself evidence the officer holds the
     * role, so it is enough on its own.
     */
    @GetMapping("/users/availability")
    public Map<String, Object> getAvailability(@RequestParam String role) {
        Map<String, Map<String, Object>> byUser = new LinkedHashMap<>();

        for (Map<String, Object> user : keycloakUserService.getUsersByRole(role)) {
            String userId = str(user.get("userId"));
            if (userId.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(user);
            row.put("userId", userId);
            row.put("username", userId);
            applyDefaults(row, role);
            byUser.put(userId, row);
        }

        for (AaOfficerPool pool : officerPoolRepository.findByRoleGroupOrderByUserIdAsc(role)) {
            Map<String, Object> row = byUser.computeIfAbsent(pool.getUserId(), id -> {
                Map<String, Object> fresh = new LinkedHashMap<>();
                fresh.put("userId", id);
                fresh.put("username", id);
                fresh.put("email", "");
                applyDefaults(fresh, role);
                return fresh;
            });
            applyPool(row, pool);
        }

        return wrapResponse(new ArrayList<>(byUser.values()));
    }

    /**
     * The officer automatic assignment would hand this complaint to.
     *
     * <p>Delegates to {@link ComplaintRoutingService#assignOfficerByRole}, so the name previewed here is
     * the one the transition will actually pick — a preview computed independently would disagree with the
     * assignment the officer then confirms.
     *
     * <p>{@code office} narrows the candidates, but only when it leaves any: an office with nobody in the
     * pool would otherwise preview as blank and the officer would read that as "no one is available"
     * rather than "this office has no roster yet".
     */
    @GetMapping("/users/next-assignee")
    public Map<String, Object> getNextAssignee(@RequestParam String role,
                                              @RequestParam(required = false) String office) {
        List<AaOfficerPool> pool = officerPoolRepository.findEligible(role);
        if (office != null && !office.isBlank()) {
            List<AaOfficerPool> inOffice = pool.stream()
                    .filter(p -> office.equalsIgnoreCase(p.getRegionalOffice()))
                    .toList();
            if (!inOffice.isEmpty()) {
                pool = inOffice;
            }
        }

        final String roundRobinPick = complaintRoutingService.assignOfficerByRole(role);
        String assigned = roundRobinPick;
        String method = "ROUND_ROBIN";
        if (assigned == null || pool.stream().noneMatch(p -> p.getUserId().equals(roundRobinPick))) {
            // Either nothing was assignable, or the round-robin pointer landed outside the office filter.
            // Falling back to the first ordered candidate keeps the dialog usable; the label tells the
            // officer the suggestion is not the round-robin turn so they can override it knowingly.
            assigned = pool.isEmpty() ? null : pool.get(0).getUserId();
            method = "POOL_ORDER";
        }
        if (assigned == null) {
            return wrapFailure("No officer is currently available for role " + role);
        }

        String userId = assigned;
        AaOfficerPool row = pool.stream().filter(p -> p.getUserId().equals(userId)).findFirst().orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("username", userId);
        data.put("displayName", row != null && row.getDisplayName() != null && !row.getDisplayName().isBlank()
                ? row.getDisplayName() : userId);
        data.put("officeCode", row != null ? nullToEmpty(row.getRegionalOffice()) : "");
        data.put("assignmentMethod", method);
        data.put("totalPoolSize", pool.size());
        return wrapResponse(data);
    }

    private static Map<String, Object> officeView(OfficeCodeMaster office) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", office.getId());
        item.put("officeCode", office.getOfficeCode());
        item.put("officeName", office.getOfficeName());
        item.put("officeType", office.getOfficeType());
        return item;
    }

    /** Availability facts for an officer with no pool row: present, unencumbered, no office of record. */
    private static void applyDefaults(Map<String, Object> row, String role) {
        row.put("role", role);
        row.put("isActive", true);
        row.put("isOnLeave", false);
        row.put("leaveReason", "");
        row.put("officeCode", "");
        row.put("currentLoad", 0);
        row.put("currentWorkload", 0);
        row.put("maxThreshold", DEFAULT_MAX_WORKLOAD);
        row.put("maxWorkload", DEFAULT_MAX_WORKLOAD);
        row.put("available", true);
    }

    private static void applyPool(Map<String, Object> row, AaOfficerPool pool) {
        if (pool.getDisplayName() != null && !pool.getDisplayName().isBlank()) {
            row.putIfAbsent("displayName", pool.getDisplayName());
        }
        boolean active = pool.isActive();
        boolean onLeave = pool.isOnLeave();
        int threshold = pool.getMaxWorkload() != null ? pool.getMaxWorkload() : DEFAULT_MAX_WORKLOAD;
        int load = pool.getCurrentWorkload() != null ? pool.getCurrentWorkload() : 0;

        row.put("isActive", active);
        row.put("isOnLeave", onLeave);
        row.put("officeCode", nullToEmpty(pool.getRegionalOffice()));
        row.put("currentLoad", load);
        row.put("currentWorkload", load);
        row.put("maxThreshold", threshold);
        row.put("maxWorkload", threshold);
        // Mirrors findEligible's predicate, so a row shown as available is one the assigner would pick.
        row.put("available", active && !onLeave);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> wrapFailure(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    private Map<String, Object> wrapResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}
