package com.hrms.cms.controller;

import com.hrms.cms.service.ForwardTargetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The forwarding-destination masters: RBIO offices, CEPC offices, RBI departments and external regulators
 * (UST556, 563, 761, 766, 534, 527-528).
 *
 * <p><b>Every one of these was previously either missing or hardcoded.</b>
 *
 * <ul>
 *   <li>{@code regulatory-bodies} was called by {@code rbio-workflow.service.ts} at a path no controller
 *       implements — and at the wrong prefix ({@code /master-data} rather than {@code /masters}). The
 *       {@code catchError(() => of([]))} turned the 404 into a permanently empty dropdown while the screen
 *       claimed "Only bodies from the validated master list can be selected".</li>
 *   <li>The RBI department list existed only as a hardcoded nine-element array in a CEPC component,
 *       including a literal 'Other'.</li>
 *   <li>The CEPC office list had no data source at all: {@code officeType} was 'BO' on every row.</li>
 * </ul>
 *
 * <p>Mounted as a SEPARATE controller from {@code MasterDataController}: that class is a chokepoint shared by
 * several sessions, and these are new masters rather than changes to its two.
 *
 * <p><b>Both paths are served.</b> {@code /api/v1/masters/**} is the correct prefix, and
 * {@code /api/v1/master-data/**} is registered as an alias because the existing frontend already builds that
 * URL. Serving the alias is what makes the live screen work without a coordinated frontend release; the
 * frontend is corrected to the canonical path in the same change, so the alias is for callers that have not
 * shipped yet rather than a permanent second contract.
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/masters", "/api/v1/master-data"})
@RequiredArgsConstructor
public class ForwardTargetController {

    private final ForwardTargetService forwardTargetService;

    /**
     * The regulatory bodies a complaint may be referred to (UST766).
     *
     * <p>Returns only bodies with a VERIFIED contact email while verification is required, so the dropdown
     * cannot offer a destination the server will then refuse. An empty list means no body has been verified —
     * the caller must show that and refuse the forward, NOT fall back to a compiled-in list.
     */
    @GetMapping({"/regulatory-bodies", "/regulators"})
    public ResponseEntity<Map<String, Object>> regulatoryBodies(
            @RequestParam(defaultValue = "false") boolean includeUnverified,
            @RequestParam(required = false) String q) {
        List<Map<String, Object>> bodies = filterByQuery(includeUnverified
                ? forwardTargetService.allRegulatoryBodies()
                : forwardTargetService.regulatoryBodies(), q);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", bodies.size());
        // Named explicitly so a caller can tell "no bodies are configured" from "none are verified yet" —
        // two different operational problems with the same empty dropdown.
        meta.put("includeUnverified", includeUnverified);
        return buildResponse(true, "Regulatory bodies retrieved", meta, bodies);
    }

    /** The RBI departments a complaint may be forwarded to (UST761, 534, 527-528). */
    @GetMapping("/rbi-departments")
    public ResponseEntity<Map<String, Object>> rbiDepartments(
            @RequestParam(required = false) String q) {
        List<Map<String, Object>> departments = filterByQuery(forwardTargetService.departments(), q);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", departments.size());
        return buildResponse(true, "RBI departments retrieved", meta, departments);
    }

    /**
     * Transfer destination offices, filtered by layout.
     *
     * @param layout RBIO (default) or CEPC. UST563 requires the CEPC list when Transfer Office = CEPC.
     */
    @GetMapping("/transfer-offices")
    public ResponseEntity<Map<String, Object>> transferOffices(
            @RequestParam(defaultValue = "RBIO") String layout) {
        boolean cepc = ForwardTargetService.LAYOUT_CEPC.equalsIgnoreCase(layout);
        List<Map<String, Object>> offices = cepc
                ? forwardTargetService.cepcOffices()
                : forwardTargetService.rbioOffices();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("layout", cepc ? ForwardTargetService.LAYOUT_CEPC : ForwardTargetService.LAYOUT_RBIO);
        meta.put("count", offices.size());
        return buildResponse(true, "Transfer offices retrieved", meta, offices);
    }

    /**
     * Narrows a destination list by the type-ahead's {@code q}, on code and name.
     *
     * <p>The masters hold tens of rows, so this filters the list the service already built rather than
     * pushing a LIKE into the repository. A blank or absent {@code q} means "everything" — the dropdown asks
     * for the full list before the officer has typed anything, and treating that as a search for the empty
     * string would return nothing and look like an unconfigured master.
     */
    private static List<Map<String, Object>> filterByQuery(List<Map<String, Object>> rows, String q) {
        if (q == null || q.isBlank()) {
            return rows;
        }
        String term = q.trim().toLowerCase();
        return rows.stream()
                .filter(row -> matches(row.get("code"), term) || matches(row.get("name"), term))
                .toList();
    }

    private static boolean matches(Object value, String term) {
        return value != null && String.valueOf(value).toLowerCase().contains(term);
    }

    private static Map<String, Object> envelope(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    private static ResponseEntity<Map<String, Object>> buildResponse(
            boolean success, String message, Map<String, Object> meta, Object listData) {
        Map<String, Object> response = envelope(success, message, listData);
        response.put("meta", meta);
        return ResponseEntity.ok(response);
    }
}
