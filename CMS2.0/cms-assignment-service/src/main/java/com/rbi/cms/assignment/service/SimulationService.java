package com.rbi.cms.assignment.service;

import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.domain.entity.AsgnRuleSet;
import com.rbi.cms.assignment.domain.entity.AsgnRuleSetVersion;
import com.rbi.cms.assignment.engine.compiler.CompiledRuleSet;
import com.rbi.cms.assignment.engine.compiler.RuleSetCompiler;
import com.rbi.cms.assignment.engine.evaluator.EvaluationResult;
import com.rbi.cms.assignment.engine.evaluator.RuleEvaluator;
import com.rbi.cms.assignment.persistence.repository.RuleSetRepository;
import com.rbi.cms.assignment.persistence.repository.RuleSetVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleSetVersionRepository versionRepository;
    private final RuleSetCompiler compiler;
    private final RuleEvaluator evaluator;
    private final TenantContext tenantContext;

    public SimulationResponse simulate(Long ruleSetId, Long versionId, List<Map<String, Object>> testCases) {
        AsgnRuleSet ruleSet = ruleSetRepository.findById(ruleSetId)
                .orElseThrow(() -> new NoSuchElementException("RuleSet not found: " + ruleSetId));
        AsgnRuleSetVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found: " + versionId));

        CompiledRuleSet compiled = compiler.compile(ruleSet, version);
        if (compiled == null) {
            return new SimulationResponse(List.of(), new SimulationSummary(0, 0, 0, 0));
        }

        List<SimulationResultRow> results = new ArrayList<>();
        int matchCount = 0;
        int fallbackCount = 0;
        int errorCount = 0;

        for (int i = 0; i < testCases.size(); i++) {
            Map<String, Object> context = testCases.get(i);
            try {
                EvaluationResult eval = evaluator.evaluate(compiled, context, false);
                String matchedCode = eval.matchedRule() != null ? eval.matchedRule().ruleCode() : null;
                String outcomeType = eval.outcome() != null ? eval.outcome().outcomeType().name() : null;
                String outcomeTarget = eval.outcome() != null ? eval.outcome().targetId() : null;

                if (eval.matchedRule() != null) matchCount++;
                if (eval.fallbackApplied()) fallbackCount++;

                results.add(new SimulationResultRow(i, context, matchedCode, outcomeType,
                        outcomeTarget, eval.fallbackApplied(), null));
            } catch (Exception e) {
                errorCount++;
                results.add(new SimulationResultRow(i, context, null, null, null, false, e.getMessage()));
            }
        }

        SimulationSummary summary = new SimulationSummary(testCases.size(), matchCount, fallbackCount, errorCount);
        return new SimulationResponse(results, summary);
    }

    public record SimulationResponse(List<SimulationResultRow> results, SimulationSummary summary) {}

    public record SimulationResultRow(
            int index,
            Map<String, Object> context,
            String matchedRuleCode,
            String outcomeType,
            String outcomeTarget,
            boolean fallbackApplied,
            String error
    ) {}

    public record SimulationSummary(int totalCases, int matched, int fallback, int errors) {}
}
