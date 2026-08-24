package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.domain.entity.AsgnExclusionRule;
import com.rbi.cms.assignment.persistence.repository.ExclusionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/assignment/exclusions")
@RequiredArgsConstructor
public class ExclusionController {

    private final ExclusionRuleRepository exclusionRuleRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<List<AsgnExclusionRule>> list() {
        String tenant = tenantContext.getCurrentTenant();
        return ResponseEntity.ok(exclusionRuleRepository.findByTenantIdOrderByCreatedAtDesc(tenant));
    }

    @PostMapping
    public ResponseEntity<AsgnExclusionRule> create(@RequestBody Map<String, Object> body) {
        String tenant = tenantContext.getCurrentTenant();
        AsgnExclusionRule rule = AsgnExclusionRule.builder()
                .tenantId(tenant)
                .exclusionType((String) body.get("exclusionType"))
                .description((String) body.get("description"))
                .conditionJson(body.get("conditionJson") != null ? body.get("conditionJson").toString() : null)
                .active(true)
                .build();
        return ResponseEntity.ok(exclusionRuleRepository.save(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AsgnExclusionRule> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AsgnExclusionRule rule = exclusionRuleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Exclusion rule not found: " + id));
        if (body.containsKey("description")) rule.setDescription((String) body.get("description"));
        if (body.containsKey("conditionJson")) rule.setConditionJson(body.get("conditionJson").toString());
        if (body.containsKey("active")) rule.setActive((Boolean) body.get("active"));
        return ResponseEntity.ok(exclusionRuleRepository.save(rule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        exclusionRuleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
