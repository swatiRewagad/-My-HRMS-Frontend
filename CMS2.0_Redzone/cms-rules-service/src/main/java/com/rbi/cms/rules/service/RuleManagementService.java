package com.rbi.cms.rules.service;

import com.rbi.cms.common.exception.ResourceNotFoundException;
import com.rbi.cms.rules.dto.*;
import com.rbi.cms.rules.engine.DynamicRuleEngine;
import com.rbi.cms.rules.entity.*;
import com.rbi.cms.rules.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleManagementService {

    private final RuleDefinitionRepository ruleRepository;
    private final RuleCategoryRepository categoryRepository;
    private final RuleVersionHistoryRepository versionHistoryRepository;
    private final RuleDeploymentLogRepository deploymentLogRepository;
    private final DynamicRuleEngine ruleEngine;

    @Transactional
    public RuleResponse createRule(RuleRequest request, String createdBy) {
        log.info("Creating rule: {} by: {}", request.getRuleCode(), createdBy);

        RuleCategory category = categoryRepository.findByCode(request.getCategoryCode())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryCode()));

        RuleDefinition rule = RuleDefinition.builder()
                .ruleCode(request.getRuleCode())
                .ruleName(request.getRuleName())
                .category(category)
                .drlContent(request.getDrlContent())
                .salience(request.getSalience() != null ? request.getSalience() : 100)
                .version(1)
                .status(RuleStatus.DRAFT)
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .createdBy(createdBy)
                .build();

        rule = ruleRepository.save(rule);

        recordHistory(rule, RuleAction.CREATED, createdBy, "Initial creation");

        return toResponse(rule);
    }

    @Transactional
    public RuleResponse updateRule(Long id, RuleRequest request, String updatedBy) {
        log.info("Updating rule ID: {} by: {}", id, updatedBy);

        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", String.valueOf(id)));

        if (rule.getStatus() == RuleStatus.ACTIVE) {
            rule.setStatus(RuleStatus.DRAFT);
        }

        rule.setRuleName(request.getRuleName());
        rule.setDrlContent(request.getDrlContent());
        rule.setSalience(request.getSalience() != null ? request.getSalience() : rule.getSalience());
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        rule.setVersion(rule.getVersion() + 1);
        rule.setUpdatedBy(updatedBy);
        rule.setApprovedBy(null);
        rule.setApprovedAt(null);

        rule = ruleRepository.save(rule);

        recordHistory(rule, RuleAction.MODIFIED, updatedBy, request.getChangeReason());

        return toResponse(rule);
    }

    @Transactional
    public RuleResponse activateRule(Long id, String approvedBy) {
        log.info("Activating rule ID: {} by: {}", id, approvedBy);

        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", String.valueOf(id)));

        if (rule.getCreatedBy().equals(approvedBy)) {
            throw new IllegalStateException("Maker-Checker violation: Creator cannot approve their own rule");
        }

        rule.setStatus(RuleStatus.ACTIVE);
        rule.setApprovedBy(approvedBy);
        rule.setApprovedAt(Instant.now());

        rule = ruleRepository.save(rule);

        recordHistory(rule, RuleAction.ACTIVATED, approvedBy, "Approved and activated");

        return toResponse(rule);
    }

    @Transactional
    public RuleResponse deactivateRule(Long id, String deactivatedBy, String reason) {
        log.info("Deactivating rule ID: {} by: {}", id, deactivatedBy);

        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", String.valueOf(id)));

        rule.setStatus(RuleStatus.INACTIVE);
        rule.setUpdatedBy(deactivatedBy);

        rule = ruleRepository.save(rule);

        recordHistory(rule, RuleAction.DEACTIVATED, deactivatedBy, reason);

        return toResponse(rule);
    }

    @Transactional
    public RuleResponse rollbackRule(Long id, String rolledBackBy) {
        log.info("Rolling back rule ID: {} by: {}", id, rolledBackBy);

        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", String.valueOf(id)));

        List<RuleVersionHistory> history = versionHistoryRepository
                .findByRuleIdOrderByVersionDesc(id);

        if (history.size() < 2) {
            throw new IllegalStateException("No previous version to rollback to");
        }

        RuleVersionHistory previousVersion = history.get(1);

        rule.setDrlContent(previousVersion.getDrlContent());
        rule.setVersion(rule.getVersion() + 1);
        rule.setStatus(RuleStatus.ACTIVE);
        rule.setUpdatedBy(rolledBackBy);

        rule = ruleRepository.save(rule);

        recordHistory(rule, RuleAction.ROLLED_BACK, rolledBackBy,
                "Rolled back to version " + previousVersion.getVersion());

        return toResponse(rule);
    }

    public RuleResponse getRule(Long id) {
        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", String.valueOf(id)));
        return toResponse(rule);
    }

    public RuleResponse getRuleByCode(String ruleCode) {
        RuleDefinition rule = ruleRepository.findByRuleCode(ruleCode)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleCode));
        return toResponse(rule);
    }

    public List<RuleResponse> getAllRules() {
        return ruleRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<RuleResponse> getRulesByCategory(String categoryCode) {
        return ruleRepository.findByCategoryCode(categoryCode).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<RuleResponse> getRulesByStatus(RuleStatus status) {
        return ruleRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<RuleHistoryResponse> getRuleHistory(Long ruleId) {
        return versionHistoryRepository.findByRuleIdOrderByVersionDesc(ruleId).stream()
                .map(h -> RuleHistoryResponse.builder()
                        .id(h.getId())
                        .version(h.getVersion())
                        .drlContent(h.getDrlContent())
                        .changeReason(h.getChangeReason())
                        .changedBy(h.getChangedBy())
                        .changedAt(h.getChangedAt())
                        .action(h.getAction())
                        .build())
                .collect(Collectors.toList());
    }

    public RuleValidationResponse validateDrl(String drlContent) {
        DynamicRuleEngine.RuleValidationResult result = ruleEngine.validateDrl(drlContent);
        return RuleValidationResponse.builder()
                .valid(result.valid())
                .errors(result.errors())
                .warnings(result.warnings())
                .build();
    }

    @Transactional
    public DeploymentResponse deployRules(String deployedBy) {
        log.info("Deploying all active rules by: {}", deployedBy);

        String deploymentId = UUID.randomUUID().toString();
        List<RuleDefinition> activeRules = ruleRepository.findActiveEffectiveRules(Instant.now());

        try {
            ruleEngine.reloadRules(activeRules);

            RuleDeploymentLog deployLog = RuleDeploymentLog.builder()
                    .deploymentId(deploymentId)
                    .rulesCount(activeRules.size())
                    .deployedBy(deployedBy)
                    .status("SUCCESS")
                    .build();
            deploymentLogRepository.save(deployLog);

            log.info("Deployment {} successful: {} rules deployed", deploymentId, activeRules.size());

            return DeploymentResponse.builder()
                    .deploymentId(deploymentId)
                    .rulesDeployed(activeRules.size())
                    .status("SUCCESS")
                    .deployedAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Deployment {} failed: {}", deploymentId, e.getMessage());

            RuleDeploymentLog deployLog = RuleDeploymentLog.builder()
                    .deploymentId(deploymentId)
                    .rulesCount(activeRules.size())
                    .deployedBy(deployedBy)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .build();
            deploymentLogRepository.save(deployLog);

            return DeploymentResponse.builder()
                    .deploymentId(deploymentId)
                    .rulesDeployed(0)
                    .status("FAILED")
                    .deployedAt(Instant.now())
                    .error(e.getMessage())
                    .build();
        }
    }

    public RuleTestResponse testRule(RuleTestRequest request) {
        log.info("Testing rules for category: {}", request.getCategoryCode());

        if (!ruleEngine.isInitialized()) {
            return RuleTestResponse.builder()
                    .executed(false)
                    .error("Rule engine not initialized. Deploy rules first.")
                    .build();
        }

        KieSession session = ruleEngine.newKieSession();
        List<String> rulesFired = new ArrayList<>();

        try {
            session.addEventListener(new org.kie.api.event.rule.DefaultRuleRuntimeEventListener() {});
            session.addEventListener(new org.kie.api.event.rule.DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(org.kie.api.event.rule.AfterMatchFiredEvent event) {
                    rulesFired.add(event.getMatch().getRule().getName());
                }
            });

            Object fact = buildFact(request.getCategoryCode(), request.getInputFacts());
            session.insert(fact);
            int count = session.fireAllRules();

            Map<String, Object> output = extractFactValues(fact);

            return RuleTestResponse.builder()
                    .executed(true)
                    .rulesFireCount(count)
                    .outputFacts(output)
                    .rulesFired(rulesFired)
                    .build();

        } catch (Exception e) {
            log.error("Rule test failed: {}", e.getMessage(), e);
            return RuleTestResponse.builder()
                    .executed(false)
                    .error(e.getMessage())
                    .build();
        } finally {
            session.dispose();
        }
    }

    public List<RuleDeploymentLog> getDeploymentHistory() {
        return deploymentLogRepository.findAllByOrderByDeployedAtDesc();
    }

    @Transactional
    public void archiveRule(Long id, String archivedBy) {
        RuleDefinition rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", String.valueOf(id)));
        rule.setStatus(RuleStatus.ARCHIVED);
        rule.setUpdatedBy(archivedBy);
        ruleRepository.save(rule);
    }

    private Object buildFact(String categoryCode, Map<String, Object> input) {
        return switch (categoryCode) {
            case "ASSIGNMENT" -> com.rbi.cms.common.dto.AssignmentFact.builder()
                    .complaintId((String) input.getOrDefault("complaintId", "TEST-001"))
                    .category((String) input.getOrDefault("category", "GENERAL"))
                    .priority((String) input.getOrDefault("priority", "MEDIUM"))
                    .jurisdictionCode((String) input.getOrDefault("jurisdictionCode", ""))
                    .amountInvolved(input.get("amountInvolved") != null ?
                            ((Number) input.get("amountInvolved")).doubleValue() : null)
                    .build();
            case "ESCALATION" -> com.rbi.cms.common.dto.EscalationFact.builder()
                    .complaintId((String) input.getOrDefault("complaintId", "TEST-001"))
                    .category((String) input.getOrDefault("category", "GENERAL"))
                    .priority((String) input.getOrDefault("priority", "MEDIUM"))
                    .slaPercentElapsed(input.get("slaPercentElapsed") != null ?
                            ((Number) input.get("slaPercentElapsed")).doubleValue() : 0)
                    .amountInvolved(input.get("amountInvolved") != null ?
                            ((Number) input.get("amountInvolved")).doubleValue() : 0)
                    .currentDaysOpen(input.get("currentDaysOpen") != null ?
                            ((Number) input.get("currentDaysOpen")).intValue() : 0)
                    .build();
            default -> throw new IllegalArgumentException("Unsupported category for testing: " + categoryCode);
        };
    }

    private Map<String, Object> extractFactValues(Object fact) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (fact instanceof com.rbi.cms.common.dto.AssignmentFact af) {
            output.put("assignedTeam", af.getAssignedTeam());
            output.put("assignedOfficer", af.getAssignedOfficer());
            output.put("escalated", af.isEscalated());
            output.put("escalationLevel", af.getEscalationLevel());
        } else if (fact instanceof com.rbi.cms.common.dto.EscalationFact ef) {
            output.put("escalationLevel", ef.getEscalationLevel());
            output.put("escalationAction", ef.getEscalationAction());
            output.put("escalationMessage", ef.getEscalationMessage());
        }
        return output;
    }

    private void recordHistory(RuleDefinition rule, RuleAction action, String changedBy, String reason) {
        RuleVersionHistory history = RuleVersionHistory.builder()
                .rule(rule)
                .version(rule.getVersion())
                .drlContent(rule.getDrlContent())
                .changeReason(reason)
                .changedBy(changedBy)
                .action(action)
                .build();
        versionHistoryRepository.save(history);
    }

    private RuleResponse toResponse(RuleDefinition rule) {
        return RuleResponse.builder()
                .id(rule.getId())
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getRuleName())
                .categoryCode(rule.getCategory().getCode())
                .categoryName(rule.getCategory().getName())
                .drlContent(rule.getDrlContent())
                .salience(rule.getSalience())
                .version(rule.getVersion())
                .status(rule.getStatus())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .createdBy(rule.getCreatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedBy(rule.getUpdatedBy())
                .updatedAt(rule.getUpdatedAt())
                .approvedBy(rule.getApprovedBy())
                .approvedAt(rule.getApprovedAt())
                .build();
    }
}
