package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.domain.entity.AsgnAttribute;
import com.rbi.cms.assignment.domain.entity.AsgnAttributeValue;
import com.rbi.cms.assignment.persistence.repository.AttributeRepository;
import com.rbi.cms.assignment.persistence.repository.AttributeValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assignment/attributes")
@RequiredArgsConstructor
public class AttributeController {

    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<List<AsgnAttribute>> getAttributes() {
        String tenant = tenantContext.getCurrentTenant();
        List<AsgnAttribute> attrs = attributeRepository.findByTenantIdAndActiveOrderByDisplayOrder(tenant, true);
        return ResponseEntity.ok(attrs);
    }

    @GetMapping("/{code}/values")
    public ResponseEntity<List<AsgnAttributeValue>> getAttributeValues(
            @PathVariable String code,
            @RequestParam(required = false) String q) {
        String tenant = tenantContext.getCurrentTenant();
        List<AsgnAttributeValue> values;
        if (q != null && !q.isBlank()) {
            values = attributeValueRepository.findByTenantIdAndAttributeCodeAndValueLabelContainingIgnoreCaseAndActive(
                    tenant, code, q, true);
        } else {
            values = attributeValueRepository.findByTenantIdAndAttributeCodeAndActiveOrderBySortOrder(
                    tenant, code, true);
        }
        return ResponseEntity.ok(values);
    }
}
