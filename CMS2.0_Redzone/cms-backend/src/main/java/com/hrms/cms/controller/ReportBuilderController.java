package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.ReportDefinition;
import com.hrms.cms.entity.ReportSchedule;
import com.hrms.cms.repository.ReportDefinitionRepository;
import com.hrms.cms.repository.ReportScheduleRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.report.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

/**
 * The report builder API (UST613-623, UST669-672, UST777).
 *
 * <h2>Identity is taken from the SSO token, never from a header</h2>
 * Every endpoint here used to read the caller from {@code @RequestHeader} parameters —
 * {@code X-User-Role} defaulting to {@code "SENIOR"}, {@code X-User-Department} to {@code ""}, and
 * {@code X-User-Username} to {@code "system"}. Those defaults were the most privileged values the code
 * could express, and {@code QueryCompiler.buildAuthScope} granted unrestricted access for either the
 * blank department or the {@code SENIOR} role. A request carrying no headers at all therefore read every
 * complaint in the database, and widget ownership was decided by a string the caller chose. Commit
 * 41d7670 eliminated this header-precedence defect from four role-guard aspects; this controller was the
 * seventh and final instance in cms-backend, missed because Spring header binding does not look like a
 * resolver.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportBuilderController {

    private final SemanticModelRegistry semanticModel;
    private final QueryCompiler queryCompiler;
    private final ReportDefinitionRepository reportDefRepo;
    private final ReportScheduleRepository scheduleRepo;
    private final ObjectMapper objectMapper;
    private final ReportAccessService reportAccessService;
    private final ReportAccessRoleAdminService accessRoleAdminService;
    private final ReportDrillDownService drillDownService;
    private final ReportFilterCompiler filterCompiler;
    private final ReportColumnRegistry columnRegistry;
    private final RequestIdentityResolver identityResolver;

    private static final int MAX_WIDGETS_PER_USER = 3;

    /** Kept in step with {@code QueryCompiler.MAX_ROWS}, which is the cap actually applied. */
    private static final int MAX_ROWS = 5000;

    @GetMapping("/semantic-model")
    public ResponseEntity<Map<String, Object>> getSemanticModel() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("subjects", semanticModel.getSubjects());
        model.put("filters", semanticModel.getFilters());
        model.put("groupBys", semanticModel.getGroupBys());
        // Published so the client's operator catalogue and range bounds come from the server that
        // ENFORCES them, rather than living in TypeScript constants that can drift out of step. The
        // client previously invented its own six-operator list with no server counterpart at all.
        model.put("operators", ReportFilterOperator.names());
        model.put("maxDateRangeDays", filterCompiler.maxDateRangeDays());
        model.put("minDateRangeDays", filterCompiler.minDateRangeDays());
        model.put("maxInValues", filterCompiler.maxInValues());
        model.put("columns", columnRegistry.defaultColumns().stream().map(c -> {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("columnKey", c.getColumnKey());
            col.put("fieldName", c.getFieldName());
            col.put("valueType", c.getValueType());
            return col;
        }).toList());
        return ResponseEntity.ok(model);
    }

    @PostMapping("/compile")
    public ResponseEntity<Map<String, Object>> compile(@RequestBody Map<String, Object> request) {
        ReportQuery query = parseQuery(request);
        queryCompiler.validate(query);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("sentence", query.getSentence());
        response.put("query", request);
        return ResponseEntity.ok(response);
    }

    /**
     * Runs a report within the caller's own scope.
     *
     * <p>See the class comment for what the removed header parameters were doing. The scope now comes
     * from {@link ReportAccessService}, which fails closed.
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@RequestBody Map<String, Object> request) {

        ReportQuery query = parseQuery(request);
        ReportScope scope = reportAccessService.resolveScope(reportTypeOf(request));

        List<String> notices = new ArrayList<>();
        List<Map<String, Object>> results = queryCompiler.execute(query, scope, notices);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sentence", query.getSentence());
        response.put("resultCount", results.size());
        response.put("maxRows", MAX_ROWS);
        response.put("bounded", true);
        response.put("readOnly", true);
        // Surfaced so a range the server auto-capped is not mistaken for a complete answer (UST617).
        response.put("notices", notices);
        response.put("canExport", scope.canExport());
        response.put("results", results);
        return ResponseEntity.ok(response);
    }

    /**
     * The export surface, gated server-side (UST669/670).
     *
     * <p>Export was previously refused only in the browser, by hiding a button for two hardcoded role
     * names. Anyone who could POST to {@code /execute} received the same rows and could save them, so the
     * restriction was decorative. The decision is now here and consults the configurable access list.
     */
    @PostMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@RequestBody Map<String, Object> request) {
        ReportQuery query = parseQuery(request);
        ReportScope scope = reportAccessService.resolveScope(reportTypeOf(request));

        if (!scope.canExport()) {
            throw new ReportAccessDeniedException("Your role may view reports but not export them.");
        }

        List<String> notices = new ArrayList<>();
        List<Map<String, Object>> results = queryCompiler.execute(query, scope, notices);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sentence", query.getSentence());
        response.put("resultCount", results.size());
        response.put("notices", notices);
        response.put("results", results);
        return ResponseEntity.ok(response);
    }

    // ── Access list (UST670) ─────────────────────────────────────────────────────────────────────
    //
    // Narrowed to administrators in SecurityConfig. Without that these would sit under the broad
    // /api/v1/reports/** STAFF_ROLES grant, letting any of ~22 staff roles rewrite the very table that
    // governs report access — including granting themselves export.

    @GetMapping("/access-roles")
    public ResponseEntity<List<Map<String, Object>>> listAccessRoles() {
        return ResponseEntity.ok(accessRoleAdminService.list());
    }

    @PostMapping("/access-roles")
    public ResponseEntity<List<Map<String, Object>>> saveAccessRoles(
            @RequestBody List<Map<String, Object>> rows) {
        return ResponseEntity.ok(accessRoleAdminService.replaceAll(rows, resolveActor()));
    }

    @DeleteMapping("/access-roles/{id}")
    public ResponseEntity<Void> deleteAccessRole(@PathVariable Long id) {
        boolean removed = accessRoleAdminService.delete(id, resolveActor());
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ── Drill-down (UST620-623) ──────────────────────────────────────────────────────────────────

    /**
     * The nodal-officer records behind one complaint's NO-record count.
     *
     * <p>Scoped independently inside {@link ReportDrillDownService}. This endpoint is reachable directly
     * with any complaint number, so the fact that some earlier report request was authorised says nothing
     * about this one (UST622).
     */
    @GetMapping("/drill-down/no-records")
    public ResponseEntity<List<Map<String, Object>>> noRecordDrillDown(
            @RequestParam String complaintId) {
        return ResponseEntity.ok(drillDownService.noRecordsFor(complaintId));
    }

    @PostMapping("/save-widget")
    public ResponseEntity<ReportDefinition> saveWidget(@RequestBody Map<String, Object> request) {
        String username = resolveActor();

        List<ReportDefinition> existing =
                reportDefRepo.findByOwnerUsernameAndDashboardWidgetTrueOrderByDisplayOrderAsc(username);
        if (existing.size() >= MAX_WIDGETS_PER_USER) {
            throw new IllegalArgumentException("Maximum " + MAX_WIDGETS_PER_USER
                    + " widgets allowed per user. Remove an existing widget first.");
        }

        String sentence = (String) request.getOrDefault("sentence", "");
        String chartType = (String) request.getOrDefault("chartType", "TABLE");
        String title = (String) request.getOrDefault("title", sentence);

        ReportDefinition def = ReportDefinition.builder()
                .ownerUsername(username)
                .sentence(sentence)
                .queryDefinition(serializeQuery(request.get("query")))
                .chartType(chartType)
                .title(title)
                .dashboardWidget(true)
                .displayOrder(0)
                .build();

        ReportDefinition saved = reportDefRepo.save(def);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/schedule")
    public ResponseEntity<ReportSchedule> schedule(@RequestBody Map<String, Object> request) {
        String username = resolveActor();
        // Taken from the body rather than an X-User-Email header. A scheduled report emails its output
        // off-site, so the address must be attributable to the authenticated requester's own submission.
        String email = (String) request.getOrDefault("recipientEmail", "");

        Long reportDefId = Long.valueOf(request.get("reportDefinitionId").toString());
        String frequency = (String) request.getOrDefault("frequency", "DAILY");
        String slot = (String) request.getOrDefault("deliverySlot", "23:00");

        Set<String> validSlots = Set.of("22:00", "23:00", "00:00", "01:00", "02:00");
        if (!validSlots.contains(slot)) {
            throw new IllegalArgumentException("Delivery slot must be off-hours: " + validSlots);
        }

        // A schedule may only be created against the requester's OWN report definition. Without this a
        // caller could schedule somebody else's saved report to their own address, and because the
        // scheduler runs the query server-side, that is an exfiltration path for rows the requester
        // could not otherwise see.
        ReportDefinition def = reportDefRepo.findById(reportDefId)
                .filter(d -> username.equals(d.getOwnerUsername()))
                .orElseThrow(() -> new ReportAccessDeniedException(
                        "You can only schedule a report definition you own."));

        ReportSchedule schedule = ReportSchedule.builder()
                .reportDefinitionId(def.getId())
                .ownerUsername(username)
                .recipientEmail(email)
                .frequency(frequency)
                .deliverySlot(slot)
                .build();

        ReportSchedule saved = scheduleRepo.save(schedule);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/my-widgets")
    public ResponseEntity<List<ReportDefinition>> getMyWidgets() {
        return ResponseEntity.ok(reportDefRepo
                .findByOwnerUsernameAndDashboardWidgetTrueOrderByDisplayOrderAsc(resolveActor()));
    }

    /**
     * Removes one of the caller's own widgets.
     *
     * <p>Ownership was compared against {@code X-User-Username}, so naming another user in that header
     * deleted THEIR widgets — and a non-match returned 200 with no action, so the caller could not tell
     * the difference between success and a silent no-op. Identity now comes from the token, and a foreign
     * or missing widget is a 404.
     */
    @DeleteMapping("/widget/{id}")
    public ResponseEntity<Void> deleteWidget(@PathVariable Long id) {
        String username = resolveActor();
        return reportDefRepo.findById(id)
                .filter(def -> username.equals(def.getOwnerUsername()))
                .map(def -> {
                    reportDefRepo.delete(def);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/my-schedules")
    public ResponseEntity<List<ReportSchedule>> getMySchedules() {
        return ResponseEntity.ok(scheduleRepo.findByOwnerUsername(resolveActor()));
    }

    /**
     * The caller, from the SSO token.
     *
     * <p>Replaces {@code @RequestHeader("X-User-Username") defaultValue = "system"}. That default
     * attributed an unidentified caller to a plausible-looking account name, and since widget ownership
     * was compared against it, anyone could read or delete another user's widgets by naming them. Fails
     * closed: no resolvable identity is a refusal, never a fallback name.
     */
    private String resolveActor() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        RequestIdentity identity =
                attrs == null ? null : identityResolver.resolve(attrs.getRequest());
        if (identity == null || identity.getUserId() == null) {
            throw new ReportAccessDeniedException(
                    "Your identity could not be established from the request, so the action was refused.");
        }
        return identity.getUserId();
    }

    /** Optional hint letting the access list vary by report type; absent means "any report". */
    private String reportTypeOf(Map<String, Object> request) {
        Object raw = request.get("reportType");
        return raw == null || raw.toString().isBlank() ? null : raw.toString().trim().toUpperCase();
    }

    private ReportQuery parseQuery(Map<String, Object> request) {
        String subjectId = (String) request.get("subjectId");
        String groupByField = (String) request.get("groupByField");
        String sentence = (String) request.getOrDefault("sentence", "");

        List<ReportQuery.QueryFilter> filters = new ArrayList<>();
        Object filtersRaw = request.get("filters");
        if (filtersRaw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    filters.add(ReportQuery.QueryFilter.builder()
                            .field(asText(map.get("field")))
                            .operator(asText(map.get("operator")))
                            .value(asText(map.get("value")))
                            .build());
                }
            }
        }

        return ReportQuery.builder()
                .subjectId(subjectId)
                .filters(filters)
                .groupByField(groupByField)
                .sentence(sentence)
                .build();
    }

    /**
     * Coerces a filter member to text.
     *
     * <p>These were unchecked {@code (String)} casts, so a JSON number, array or object in any of the
     * three positions threw {@code ClassCastException} and surfaced as a 500 rather than a 400 naming the
     * bad field. A number in a filter value is entirely reasonable client behaviour.
     */
    private String asText(Object value) {
        return value == null ? null : value.toString();
    }

    private String serializeQuery(Object query) {
        try {
            return objectMapper.writeValueAsString(query);
        } catch (Exception e) {
            return "{}";
        }
    }
}
