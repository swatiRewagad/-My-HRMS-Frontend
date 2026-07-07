package com.hrms.cms.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/rules")
public class RulesApiController {

    private final List<Map<String, Object>> rules = new ArrayList<>();
    private final List<Map<String, Object>> categories = List.of(
            Map.of("id", 1, "code", "ASSIGNMENT", "name", "Assignment Rules", "description", "Rules for routing complaints to departments/officers"),
            Map.of("id", 2, "code", "ESCALATION", "name", "Escalation Rules", "description", "Rules for auto-escalating complaints based on SLA breach"),
            Map.of("id", 3, "code", "PRIORITY", "name", "Priority Rules", "description", "Rules for determining complaint priority (High/Medium/Low)"),
            Map.of("id", 4, "code", "CATEGORIZATION", "name", "Categorization Rules", "description", "Rules for auto-categorizing complaints based on keywords"),
            Map.of("id", 5, "code", "SLA", "name", "SLA Rules", "description", "Rules for computing SLA deadlines per category and priority"),
            Map.of("id", 6, "code", "NOTIFICATION", "name", "Notification Rules", "description", "Rules for triggering notifications and alerts"),
            Map.of("id", 7, "code", "MAINTAINABILITY", "name", "Maintainability Rules", "description", "Rules for RB-IOS 2026 objective maintainability (Q13/Q16/Q17 grounds)"),
            Map.of("id", 8, "code", "COMPENSATION", "name", "Compensation Rules", "description", "Rules for computing compensation bands (RB-IOS Q22/Q23 caps)")
    );

    public RulesApiController() {
        seedRules();
    }

    private void seedRules() {
        rules.add(createRule(1, "ASGN-001", "Route ATM complaints to RBIO", "ASSIGNMENT",
                "rule \"Route ATM to RBIO\"\n  when\n    $c : Complaint(categoryCode == \"ATM_DEBIT\")\n  then\n    $c.setDepartment(\"RBIO\");\n    $c.setAssignedRole(\"RBIO_OFFICER\");\nend",
                10, "ACTIVE", "admin"));
        rules.add(createRule(2, "ASGN-002", "Route UPI complaints to CEPC", "ASSIGNMENT",
                "rule \"Route UPI to CEPC\"\n  when\n    $c : Complaint(categoryCode == \"UPI_MOBILE\")\n  then\n    $c.setDepartment(\"CEPC\");\n    $c.setAssignedRole(\"CEPC_OFFICER\");\nend",
                10, "ACTIVE", "admin"));
        rules.add(createRule(3, "ESC-001", "Escalate if no response in 7 days", "ESCALATION",
                "rule \"Escalate after 7 days\"\n  when\n    $c : Complaint(status == \"in_progress\", daysSinceLastUpdate > 7)\n  then\n    $c.escalate();\n    $c.setPriority(\"high\");\nend",
                20, "ACTIVE", "admin"));
        rules.add(createRule(4, "ESC-002", "Escalate high-value complaints immediately", "ESCALATION",
                "rule \"High value escalation\"\n  when\n    $c : Complaint(amount > 500000, status == \"pending\")\n  then\n    $c.setPriority(\"high\");\n    $c.setAssignedRole(\"SUPERVISOR\");\nend",
                25, "ACTIVE", "admin"));
        rules.add(createRule(5, "PRI-001", "Senior citizen complaints are high priority", "PRIORITY",
                "rule \"Senior citizen priority\"\n  when\n    $c : Complaint(complainantAge > 60)\n  then\n    $c.setPriority(\"high\");\nend",
                15, "ACTIVE", "admin"));
        rules.add(createRule(6, "PRI-002", "Fraud complaints are critical priority", "PRIORITY",
                "rule \"Fraud critical\"\n  when\n    $c : Complaint(categoryCode == \"FRAUD\")\n  then\n    $c.setPriority(\"critical\");\n    $c.setEscalationRequired(true);\nend",
                30, "DRAFT", "admin"));
        rules.add(createRule(7, "CAT-001", "Auto-categorize ATM keywords", "CATEGORIZATION",
                "rule \"Categorize ATM\"\n  when\n    $c : Complaint(description matches \".*ATM.*cash.*not.*dispensed.*\")\n  then\n    $c.setCategoryCode(\"ATM_DEBIT\");\nend",
                5, "ACTIVE", "admin"));
        rules.add(createRule(8, "SLA-001", "ATM complaints - 7 day SLA", "SLA",
                "rule \"ATM SLA\"\n  when\n    $c : Complaint(categoryCode == \"ATM_DEBIT\")\n  then\n    $c.setSlaDeadlineDays(7);\nend",
                10, "ACTIVE", "admin"));
        rules.add(createRule(9, "NOT-001", "SMS on complaint registration", "NOTIFICATION",
                "rule \"Registration SMS\"\n  when\n    $c : Complaint(status == \"pending\", isNew == true)\n  then\n    notify(\"SMS\", $c.getComplainantPhone(), \"Your complaint \" + $c.getComplaintNumber() + \" has been registered.\");\nend",
                5, "INACTIVE", "admin"));
        // Maintainability Rules (RB-IOS 2026)
        rules.add(createRule(10, "MRE-001", "Entity coverage check (Q13)", "MAINTAINABILITY",
                "rule \"Entity coverage check - Q13\"\n  salience 100\n  when\n    $c : Complaint(entityCode != null)\n    $e : EntityRegistry(code == $c.entityCode, coveredUnderScheme == false)\n  then\n    $c.addMreGround(\"ENTITY_NOT_COVERED\", \"FAIL\", \"Q13\", \"Entity not covered under RB-IOS 2026\");\n    $c.setObjectivelyNonMaintainable(true);\nend",
                100, "ACTIVE", "admin"));
        rules.add(createRule(11, "MRE-002", "Prior RE complaint requirement (Q16)", "MAINTAINABILITY",
                "rule \"Prior RE complaint check - Q16\"\n  salience 95\n  when\n    $c : Complaint(priorReComplaint == false || priorReComplaint == null)\n  then\n    $c.addMreGround(\"NO_PRIOR_RE_COMPLAINT\", \"FAIL\", \"Q16\", \"Not first approached RE\");\n    $c.setObjectivelyNonMaintainable(true);\nend",
                95, "ACTIVE", "admin"));
        rules.add(createRule(12, "MRE-003", "RE window not elapsed (Q17 - 30 days)", "MAINTAINABILITY",
                "rule \"RE window not elapsed - Q17\"\n  salience 90\n  when\n    $c : Complaint(priorReComplaint == true, reComplaintDate != null, reRepliedAndDissatisfied == false, daysSinceReComplaint < applicableWindowDays)\n  then\n    $c.addMreGround(\"FILED_BEFORE_WINDOW\", \"FAIL\", \"Q17\", \"Filed before \" + $c.getApplicableWindowDays() + \"-day window elapsed\");\n    $c.setObjectivelyNonMaintainable(true);\nend",
                90, "ACTIVE", "admin"));
        rules.add(createRule(13, "MRE-004", "Filing deadline exceeded (365 days)", "MAINTAINABILITY",
                "rule \"Filing deadline exceeded - Q16\"\n  salience 85\n  when\n    $c : Complaint(priorReComplaint == true, daysSinceWindowExpiry > 365)\n  then\n    $c.addMreGround(\"FILED_BEYOND_DEADLINE\", \"FAIL\", \"Q16/Q17\", \"Filed beyond 365-day deadline\");\n    $c.setObjectivelyNonMaintainable(true);\nend",
                85, "ACTIVE", "admin"));
        rules.add(createRule(14, "MRE-005", "Limitation Act period exceeded (3 years)", "MAINTAINABILITY",
                "rule \"Limitation period exceeded - Q16\"\n  salience 80\n  when\n    $c : Complaint(priorReComplaint == true, yearsSinceReComplaint > 3)\n  then\n    $c.addMreGround(\"RE_COMPLAINT_BEYOND_LIMITATION\", \"FAIL\", \"Q16\", \"RE complaint beyond 3-year Limitation Act period\");\n    $c.setObjectivelyNonMaintainable(true);\nend",
                80, "ACTIVE", "admin"));
        rules.add(createRule(15, "MRE-006", "Duplicate grievance check (Q16)", "MAINTAINABILITY",
                "rule \"Duplicate grievance check - Q16\"\n  salience 75\n  when\n    $c : Complaint(sameGrievancePendingOrDecided == true)\n  then\n    $c.addMreGround(\"SAME_GRIEVANCE_PENDING\", \"FAIL\", \"Q16\", \"Same grievance already pending or decided\");\n    $c.setObjectivelyNonMaintainable(true);\nend",
                75, "ACTIVE", "admin"));
        // Compensation Rules (RB-IOS Q22/Q23)
        rules.add(createRule(16, "CMP-001", "Consequential loss cap Rs 30L (Q22)", "COMPENSATION",
                "rule \"Consequential loss cap\"\n  salience 10\n  when\n    $c : Complaint(maintainabilityDetermination == \"MAINTAINABLE\")\n    $award : AwardCalculation(consequentialLoss > 3000000)\n  then\n    $award.setConsequentialLoss(3000000);\n    $award.addNote(\"Capped at Rs 30,00,000 per RB-IOS Q22\");\nend",
                10, "ACTIVE", "admin"));
        rules.add(createRule(17, "CMP-002", "Time/harassment cap Rs 3L (Q23)", "COMPENSATION",
                "rule \"Time and harassment cap\"\n  salience 10\n  when\n    $c : Complaint(maintainabilityDetermination == \"MAINTAINABLE\")\n    $award : AwardCalculation(timeHarassmentAmount > 300000)\n  then\n    $award.setTimeHarassmentAmount(300000);\n    $award.addNote(\"Capped at Rs 3,00,000 per RB-IOS Q23\");\nend",
                10, "ACTIVE", "admin"));
    }

    private Map<String, Object> createRule(int id, String code, String name, String categoryCode,
                                            String drl, int salience, String status, String createdBy) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", id);
        rule.put("ruleCode", code);
        rule.put("ruleName", name);
        rule.put("categoryCode", categoryCode);
        rule.put("categoryName", categories.stream()
                .filter(c -> c.get("code").equals(categoryCode))
                .map(c -> (String) c.get("name"))
                .findFirst().orElse(categoryCode));
        rule.put("drlContent", drl);
        rule.put("salience", salience);
        rule.put("version", 1);
        rule.put("status", status);
        rule.put("effectiveFrom", LocalDateTime.now().minusDays(30).toString());
        rule.put("effectiveTo", null);
        rule.put("createdBy", createdBy);
        rule.put("createdAt", LocalDateTime.now().minusDays(30).toString());
        rule.put("updatedBy", null);
        rule.put("updatedAt", null);
        rule.put("approvedBy", "ACTIVE".equals(status) ? "supervisor" : null);
        rule.put("approvedAt", "ACTIVE".equals(status) ? LocalDateTime.now().minusDays(29).toString() : null);
        return rule;
    }

    @GetMapping("/categories")
    public Map<String, Object> getCategories() {
        return wrapResponse(categories);
    }

    @GetMapping
    public Map<String, Object> getRules(@RequestParam(required = false) String category,
                                         @RequestParam(required = false) String status) {
        List<Map<String, Object>> filtered = rules.stream()
                .filter(r -> category == null || category.isEmpty() || r.get("categoryCode").equals(category))
                .filter(r -> status == null || status.isEmpty() || r.get("status").equals(status))
                .toList();
        return wrapResponse(filtered);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getRule(@PathVariable int id) {
        return rules.stream()
                .filter(r -> (int) r.get("id") == id)
                .findFirst()
                .map(this::wrapResponse)
                .orElse(wrapError("Rule not found"));
    }

    @PostMapping
    public Map<String, Object> createRule(@RequestBody Map<String, Object> request,
                                           @RequestHeader(value = "X-User", defaultValue = "admin") String user) {
        int newId = rules.size() + 1;
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", newId);
        rule.put("ruleCode", request.getOrDefault("ruleCode", "RULE-" + newId));
        rule.put("ruleName", request.getOrDefault("ruleName", ""));
        rule.put("categoryCode", request.getOrDefault("categoryCode", ""));
        rule.put("categoryName", categories.stream()
                .filter(c -> c.get("code").equals(request.getOrDefault("categoryCode", "")))
                .map(c -> (String) c.get("name"))
                .findFirst().orElse(""));
        rule.put("drlContent", request.getOrDefault("drlContent", ""));
        rule.put("salience", request.getOrDefault("salience", 10));
        rule.put("version", 1);
        rule.put("status", "DRAFT");
        rule.put("effectiveFrom", request.get("effectiveFrom"));
        rule.put("effectiveTo", request.get("effectiveTo"));
        rule.put("createdBy", user);
        rule.put("createdAt", LocalDateTime.now().toString());
        rule.put("updatedBy", null);
        rule.put("updatedAt", null);
        rule.put("approvedBy", null);
        rule.put("approvedAt", null);
        rules.add(rule);
        return wrapResponse(rule);
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateRule(@PathVariable int id, @RequestBody Map<String, Object> request,
                                           @RequestHeader(value = "X-User", defaultValue = "admin") String user) {
        Optional<Map<String, Object>> ruleOpt = rules.stream().filter(r -> (int) r.get("id") == id).findFirst();
        if (ruleOpt.isEmpty()) return wrapError("Rule not found");

        Map<String, Object> rule = ruleOpt.get();
        if (request.containsKey("ruleName")) rule.put("ruleName", request.get("ruleName"));
        if (request.containsKey("categoryCode")) {
            rule.put("categoryCode", request.get("categoryCode"));
            rule.put("categoryName", categories.stream()
                    .filter(c -> c.get("code").equals(request.get("categoryCode")))
                    .map(c -> (String) c.get("name"))
                    .findFirst().orElse(""));
        }
        if (request.containsKey("drlContent")) rule.put("drlContent", request.get("drlContent"));
        if (request.containsKey("salience")) rule.put("salience", request.get("salience"));
        if (request.containsKey("effectiveFrom")) rule.put("effectiveFrom", request.get("effectiveFrom"));
        if (request.containsKey("effectiveTo")) rule.put("effectiveTo", request.get("effectiveTo"));
        rule.put("updatedBy", user);
        rule.put("updatedAt", LocalDateTime.now().toString());
        rule.put("version", (int) rule.get("version") + 1);
        rule.put("status", "DRAFT");
        return wrapResponse(rule);
    }

    @PostMapping("/{id}/activate")
    public Map<String, Object> activateRule(@PathVariable int id,
                                             @RequestHeader(value = "X-User", defaultValue = "supervisor") String approver) {
        Optional<Map<String, Object>> ruleOpt = rules.stream().filter(r -> (int) r.get("id") == id).findFirst();
        if (ruleOpt.isEmpty()) return wrapError("Rule not found");

        Map<String, Object> rule = ruleOpt.get();
        if (approver.equals(rule.get("createdBy"))) {
            return wrapError("Maker-Checker violation: approver must differ from creator");
        }
        rule.put("status", "ACTIVE");
        rule.put("approvedBy", approver);
        rule.put("approvedAt", LocalDateTime.now().toString());
        return wrapResponse(rule);
    }

    @PostMapping("/{id}/deactivate")
    public Map<String, Object> deactivateRule(@PathVariable int id,
                                               @RequestHeader(value = "X-User", defaultValue = "admin") String user,
                                               @RequestParam(required = false) String reason) {
        Optional<Map<String, Object>> ruleOpt = rules.stream().filter(r -> (int) r.get("id") == id).findFirst();
        if (ruleOpt.isEmpty()) return wrapError("Rule not found");

        Map<String, Object> rule = ruleOpt.get();
        rule.put("status", "INACTIVE");
        rule.put("updatedBy", user);
        rule.put("updatedAt", LocalDateTime.now().toString());
        return wrapResponse(rule);
    }

    @PostMapping("/{id}/rollback")
    public Map<String, Object> rollbackRule(@PathVariable int id,
                                             @RequestHeader(value = "X-User", defaultValue = "admin") String user) {
        Optional<Map<String, Object>> ruleOpt = rules.stream().filter(r -> (int) r.get("id") == id).findFirst();
        if (ruleOpt.isEmpty()) return wrapError("Rule not found");

        Map<String, Object> rule = ruleOpt.get();
        rule.put("status", "DRAFT");
        rule.put("version", Math.max(1, (int) rule.get("version") - 1));
        rule.put("updatedBy", user);
        rule.put("updatedAt", LocalDateTime.now().toString());
        rule.put("approvedBy", null);
        rule.put("approvedAt", null);
        return wrapResponse(rule);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> archiveRule(@PathVariable int id,
                                            @RequestHeader(value = "X-User", defaultValue = "admin") String user) {
        Optional<Map<String, Object>> ruleOpt = rules.stream().filter(r -> (int) r.get("id") == id).findFirst();
        if (ruleOpt.isEmpty()) return wrapError("Rule not found");

        Map<String, Object> rule = ruleOpt.get();
        rule.put("status", "ARCHIVED");
        rule.put("updatedBy", user);
        rule.put("updatedAt", LocalDateTime.now().toString());
        return wrapResponse(null);
    }

    @GetMapping("/{id}/history")
    public Map<String, Object> getRuleHistory(@PathVariable int id) {
        List<Map<String, Object>> history = List.of(
                Map.of("id", 1, "version", 1, "drlContent", "Initial version", "changeReason", "Created",
                        "changedBy", "admin", "changedAt", LocalDateTime.now().minusDays(30).toString(), "action", "CREATED"),
                Map.of("id", 2, "version", 1, "drlContent", "Activated", "changeReason", "Approved by supervisor",
                        "changedBy", "supervisor", "changedAt", LocalDateTime.now().minusDays(29).toString(), "action", "ACTIVATED")
        );
        return wrapResponse(history);
    }

    @PostMapping("/validate")
    public Map<String, Object> validateDrl(@RequestBody Map<String, Object> request) {
        String drl = (String) request.getOrDefault("drlContent", "");
        boolean valid = drl.contains("rule") && drl.contains("when") && drl.contains("then") && drl.contains("end");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("errors", valid ? List.of() : List.of("DRL must contain rule/when/then/end blocks"));
        result.put("warnings", List.of());
        return wrapResponse(result);
    }

    @PostMapping("/test")
    public Map<String, Object> testRules(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", true);
        result.put("rulesFireCount", 2);
        result.put("outputFacts", Map.of("department", "RBIO", "priority", "high", "slaDeadlineDays", 7));
        result.put("rulesFired", List.of("Route ATM to RBIO", "ATM SLA"));
        result.put("error", null);
        return wrapResponse(result);
    }

    @PostMapping("/deploy")
    public Map<String, Object> deployRules(@RequestHeader(value = "X-User", defaultValue = "admin") String user) {
        long activeCount = rules.stream().filter(r -> "ACTIVE".equals(r.get("status"))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deploymentId", "DEP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        result.put("rulesDeployed", activeCount);
        result.put("status", "SUCCESS");
        result.put("deployedAt", LocalDateTime.now().toString());
        result.put("error", null);
        return wrapResponse(result);
    }

    private Map<String, Object> wrapResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        return response;
    }

    private Map<String, Object> wrapError(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        return response;
    }
}
