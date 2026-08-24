package com.rbi.cms.assignment.service;

import com.rbi.cms.assignment.domain.entity.AsgnDecisionLog;
import com.rbi.cms.assignment.dto.request.ResolveRequest;
import com.rbi.cms.assignment.dto.response.*;
import com.rbi.cms.assignment.engine.compiler.CompiledOutcome;
import com.rbi.cms.assignment.engine.compiler.CompiledRuleSet;
import com.rbi.cms.assignment.engine.evaluator.EvaluationResult;
import com.rbi.cms.assignment.engine.evaluator.RuleEvaluator;
import com.rbi.cms.assignment.engine.evaluator.TraceEntry;
import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.persistence.repository.DecisionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentResolveService {

    private final RuleSetCacheService cacheService;
    private final RuleEvaluator evaluator;
    private final DistributionService distributionService;
    private final GroupMemberProvider groupMemberProvider;
    private final TenantContext tenantContext;
    private final DecisionLogRepository decisionLogRepository;
    private final ObjectMapper objectMapper;

    public ResolveResponse resolve(ResolveRequest request) {
        long start = System.currentTimeMillis();

        boolean fullTrace = request.effectiveTraceLevel() == ResolveRequest.TraceLevel.FULL;
        CompiledRuleSet ruleSet = cacheService.getCompiledRuleSet(request.decisionPoint());

        Map<String, Object> context = request.context() != null ? request.context() : Map.of();
        EvaluationResult result = evaluator.evaluate(ruleSet, context, fullTrace);

        // Distribution: if outcome requires PICK_MEMBER, resolve to individual
        DistributionService.DistributionResult distResult = null;
        if (result.outcome() != null && result.outcome().assignMode() != null
                && result.outcome().assignMode() == com.rbi.cms.assignment.domain.enums.AssignMode.PICK_MEMBER
                && result.outcome().distributionStrategy() != null) {
            String groupId = result.outcome().targetId();
            List<String> candidates = groupMemberProvider.getMembers(groupId);
            distResult = distributionService.distribute(
                    groupId, result.outcome().distributionStrategy(), candidates, context);
        }

        long latencyMs = System.currentTimeMillis() - start;

        OutcomeDto outcomeDto = buildOutcomeDto(result.outcome(), distResult);
        ExplanationDto explanation = buildExplanation(result, ruleSet, latencyMs, distResult);
        List<TraceDto> trace = buildTrace(result, request.effectiveTraceLevel());

        if (!request.dryRun()) {
            persistDecisionLog(request, result, ruleSet, latencyMs, distResult);
        }

        return new ResolveResponse(outcomeDto, explanation, trace);
    }

    private OutcomeDto buildOutcomeDto(CompiledOutcome outcome, DistributionService.DistributionResult distResult) {
        if (outcome == null) {
            return new OutcomeDto("QUEUE", "SYSTEM_FALLBACK", null, null, null);
        }
        String assignedUser = distResult != null ? distResult.selectedUserId() : null;
        return new OutcomeDto(
                outcome.outcomeType().name(),
                outcome.targetId(),
                assignedUser,
                outcome.assignMode() != null ? outcome.assignMode().name() : null,
                outcome.distributionStrategy() != null ? outcome.distributionStrategy().name() : null
        );
    }

    private ExplanationDto buildExplanation(EvaluationResult result, CompiledRuleSet ruleSet,
                                               long latencyMs, DistributionService.DistributionResult distResult) {
        return new ExplanationDto(
                result.matchedRule() != null ? result.matchedRule().ruleId() : null,
                result.matchedRule() != null ? result.matchedRule().ruleCode() : null,
                result.matchedRule() != null ? result.matchedRule().name() : null,
                ruleSet != null ? ruleSet.versionNo() : 0,
                ruleSet != null ? ruleSet.hitPolicy().name() : null,
                result.outcome() != null && result.outcome().distributionStrategy() != null
                        ? result.outcome().distributionStrategy().name() : null,
                distResult != null ? distResult.candidatesConsidered() : null,
                distResult != null ? distResult.candidatesExcluded() : null,
                distResult != null && distResult.failureReason() != null ? distResult.failureReason() : null,
                result.fallbackApplied(),
                result.fallbackReason(),
                Instant.now(),
                latencyMs
        );
    }

    private List<TraceDto> buildTrace(EvaluationResult result, ResolveRequest.TraceLevel level) {
        if (level == ResolveRequest.TraceLevel.NONE || result.trace() == null) {
            return null;
        }
        List<TraceEntry> entries = result.trace();
        if (level == ResolveRequest.TraceLevel.MATCHED) {
            entries = entries.stream().filter(TraceEntry::matched).toList();
        }
        return entries.stream()
                .map(e -> new TraceDto(e.ruleId(), e.ruleCode(), e.ruleName(), e.matched(),
                        e.firstFailedAttribute(), e.failReason()))
                .toList();
    }

    private void persistDecisionLog(ResolveRequest request, EvaluationResult result,
                                     CompiledRuleSet ruleSet, long latencyMs,
                                     DistributionService.DistributionResult distResult) {
        try {
            String contextJson = objectMapper.writeValueAsString(request.context());
            AsgnDecisionLog logEntry = AsgnDecisionLog.builder()
                    .tenantId(tenantContext.getCurrentTenant())
                    .decisionPoint(request.decisionPoint())
                    .caseRef(request.caseRef())
                    .ruleSetVersionId(ruleSet != null ? ruleSet.versionId() : null)
                    .matchedRuleId(result.matchedRule() != null ? result.matchedRule().ruleId() : null)
                    .matchedRuleCode(result.matchedRule() != null ? result.matchedRule().ruleCode() : null)
                    .outcomeType(result.outcome() != null ? result.outcome().outcomeType().name() : null)
                    .outcomeTarget(result.outcome() != null ? result.outcome().targetId() : null)
                    .assignedUserId(distResult != null ? distResult.selectedUserId() : null)
                    .distributionStrategy(result.outcome() != null && result.outcome().distributionStrategy() != null
                            ? result.outcome().distributionStrategy().name() : null)
                    .candidatesConsidered(distResult != null ? distResult.candidatesConsidered() : null)
                    .candidatesExcluded(distResult != null ? distResult.candidatesExcluded() : null)
                    .fallbackApplied(result.fallbackApplied())
                    .fallbackReason(result.fallbackReason())
                    .latencyMs((int) latencyMs)
                    .contextJson(contextJson)
                    .dryRun(request.dryRun())
                    .build();
            decisionLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to persist decision log", e);
        }
    }
}
