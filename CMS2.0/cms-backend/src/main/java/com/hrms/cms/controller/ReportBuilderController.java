package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.ReportDefinition;
import com.hrms.cms.entity.ReportSchedule;
import com.hrms.cms.repository.ReportDefinitionRepository;
import com.hrms.cms.repository.ReportScheduleRepository;
import com.hrms.cms.service.report.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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

    @GetMapping("/semantic-model")
    public ResponseEntity<Map<String, Object>> getSemanticModel() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("subjects", semanticModel.getSubjects());
        model.put("filters", semanticModel.getFilters());
        model.put("groupBys", semanticModel.getGroupBys());
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

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Role", defaultValue = "SENIOR") String userRole,
            @RequestHeader(value = "X-User-Department", defaultValue = "") String userDepartment) {

        ReportQuery query = parseQuery(request);
        List<Map<String, Object>> results = queryCompiler.execute(query, userRole, userDepartment);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sentence", query.getSentence());
        response.put("resultCount", results.size());
        response.put("maxRows", 5000);
        response.put("bounded", true);
        response.put("readOnly", true);
        response.put("results", results);
        return ResponseEntity.ok(response);
    }

    private static final int MAX_WIDGETS_PER_USER = 3;

    @PostMapping("/save-widget")
    public ResponseEntity<ReportDefinition> saveWidget(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Username", defaultValue = "system") String username) {

        List<ReportDefinition> existing = reportDefRepo.findByOwnerUsernameAndDashboardWidgetTrueOrderByDisplayOrderAsc(username);
        if (existing.size() >= MAX_WIDGETS_PER_USER) {
            throw new IllegalArgumentException("Maximum " + MAX_WIDGETS_PER_USER + " widgets allowed per user. Remove an existing widget first.");
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
    public ResponseEntity<ReportSchedule> schedule(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-User-Username", defaultValue = "system") String username,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email) {

        Long reportDefId = Long.valueOf(request.get("reportDefinitionId").toString());
        String frequency = (String) request.getOrDefault("frequency", "DAILY");
        String slot = (String) request.getOrDefault("deliverySlot", "23:00");

        Set<String> validSlots = Set.of("22:00", "23:00", "00:00", "01:00", "02:00");
        if (!validSlots.contains(slot)) {
            throw new IllegalArgumentException("Delivery slot must be off-hours: " + validSlots);
        }

        ReportSchedule schedule = ReportSchedule.builder()
                .reportDefinitionId(reportDefId)
                .ownerUsername(username)
                .recipientEmail(email)
                .frequency(frequency)
                .deliverySlot(slot)
                .build();

        ReportSchedule saved = scheduleRepo.save(schedule);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/my-widgets")
    public ResponseEntity<List<ReportDefinition>> getMyWidgets(
            @RequestHeader(value = "X-User-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(
                reportDefRepo.findByOwnerUsernameAndDashboardWidgetTrueOrderByDisplayOrderAsc(username));
    }

    @DeleteMapping("/widget/{id}")
    public ResponseEntity<Void> deleteWidget(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Username", defaultValue = "system") String username) {
        reportDefRepo.findById(id).ifPresent(def -> {
            if (def.getOwnerUsername().equals(username)) {
                reportDefRepo.delete(def);
            }
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my-schedules")
    public ResponseEntity<List<ReportSchedule>> getMySchedules(
            @RequestHeader(value = "X-User-Username", defaultValue = "system") String username) {
        return ResponseEntity.ok(scheduleRepo.findByOwnerUsername(username));
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
                            .field((String) map.get("field"))
                            .operator((String) map.get("operator"))
                            .value((String) map.get("value"))
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

    private String serializeQuery(Object query) {
        try {
            return objectMapper.writeValueAsString(query);
        } catch (Exception e) {
            return "{}";
        }
    }
}
