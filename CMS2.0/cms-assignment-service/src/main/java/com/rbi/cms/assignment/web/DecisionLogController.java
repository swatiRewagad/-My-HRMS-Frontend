package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.domain.entity.AsgnDecisionLog;
import com.rbi.cms.assignment.persistence.repository.DecisionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assignment/decision-logs")
@RequiredArgsConstructor
public class DecisionLogController {

    private final DecisionLogRepository decisionLogRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<List<AsgnDecisionLog>> search(
            @RequestParam(required = false) String decisionPoint,
            @RequestParam(required = false) String caseRef,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) Boolean dryRun,
            @RequestParam(defaultValue = "50") int limit) {

        PageRequest page = PageRequest.of(0, Math.min(limit, 200));
        List<AsgnDecisionLog> results;

        if (caseRef != null && !caseRef.isBlank()) {
            results = decisionLogRepository.findByCaseRefOrderByCreatedAtDesc(caseRef);
        } else if (decisionPoint != null && !decisionPoint.isBlank()) {
            results = decisionLogRepository.findByDecisionPointOrderByCreatedAtDesc(decisionPoint, page);
        } else if (ruleCode != null && !ruleCode.isBlank()) {
            results = decisionLogRepository.findByMatchedRuleCodeOrderByCreatedAtDesc(ruleCode, page);
        } else {
            results = decisionLogRepository.findRecentByTenant(tenantContext.getCurrentTenant(), page);
        }

        if (dryRun != null && dryRun) {
            results = results.stream().filter(AsgnDecisionLog::isDryRun).toList();
        }

        return ResponseEntity.ok(results);
    }
}
