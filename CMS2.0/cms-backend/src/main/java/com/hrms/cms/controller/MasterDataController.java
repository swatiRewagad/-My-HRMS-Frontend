package com.hrms.cms.controller;

import com.hrms.cms.entity.CategoryMaster;
import com.hrms.cms.entity.DepartmentRoutingMaster;
import com.hrms.cms.repository.CategoryMasterRepository;
import com.hrms.cms.repository.DepartmentRoutingMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/masters")
@RequiredArgsConstructor
public class MasterDataController {

    private final CategoryMasterRepository categoryRepo;
    private final DepartmentRoutingMasterRepository routingRepo;

    // ─── Category Master ───
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryMaster>> getCategories(
            @RequestParam(required = false) String schemeVersion,
            @RequestParam(required = false) String entityType) {
        if (schemeVersion != null) {
            return ResponseEntity.ok(categoryRepo.findBySchemeVersionAndActiveTrueOrderBySortOrderAsc(schemeVersion));
        }
        if (entityType != null) {
            return ResponseEntity.ok(categoryRepo.findByEntityTypeAndActiveTrueOrderBySortOrderAsc(entityType));
        }
        return ResponseEntity.ok(categoryRepo.findByActiveTrueOrderBySortOrderAsc());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<CategoryMaster> createCategory(@RequestBody CategoryMaster category) {
        category.setActive(true);
        return ResponseEntity.ok(categoryRepo.save(category));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<CategoryMaster> updateCategory(@PathVariable Long id, @RequestBody CategoryMaster category) {
        CategoryMaster existing = categoryRepo.findById(id).orElseThrow();
        existing.setCategoryName(category.getCategoryName());
        existing.setSubCategory(category.getSubCategory());
        existing.setSchemeVersion(category.getSchemeVersion());
        existing.setEntityType(category.getEntityType());
        existing.setSortOrder(category.getSortOrder());
        existing.setActive(category.isActive());
        return ResponseEntity.ok(categoryRepo.save(existing));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        CategoryMaster existing = categoryRepo.findById(id).orElseThrow();
        existing.setActive(false);
        categoryRepo.save(existing);
        return ResponseEntity.noContent().build();
    }

    // ─── Department Routing Master ───
    @GetMapping("/department-routing")
    public ResponseEntity<List<DepartmentRoutingMaster>> getRoutingRules(
            @RequestParam(required = false) String department) {
        if (department != null) {
            return ResponseEntity.ok(routingRepo.findByDepartmentAndActiveTrueOrderByEntityNameAsc(department));
        }
        return ResponseEntity.ok(routingRepo.findByActiveTrueOrderByEntityNameAsc());
    }

    @PostMapping("/department-routing")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<DepartmentRoutingMaster> createRoutingRule(@RequestBody DepartmentRoutingMaster rule) {
        rule.setActive(true);
        return ResponseEntity.ok(routingRepo.save(rule));
    }

    @PutMapping("/department-routing/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<DepartmentRoutingMaster> updateRoutingRule(@PathVariable Long id, @RequestBody DepartmentRoutingMaster rule) {
        DepartmentRoutingMaster existing = routingRepo.findById(id).orElseThrow();
        existing.setEntityName(rule.getEntityName());
        existing.setDepartment(rule.getDepartment());
        existing.setTargetOffice(rule.getTargetOffice());
        existing.setRegistrationStatus(rule.getRegistrationStatus());
        existing.setActive(rule.isActive());
        return ResponseEntity.ok(routingRepo.save(existing));
    }

    @DeleteMapping("/department-routing/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<Void> deleteRoutingRule(@PathVariable Long id) {
        DepartmentRoutingMaster existing = routingRepo.findById(id).orElseThrow();
        existing.setActive(false);
        routingRepo.save(existing);
        return ResponseEntity.noContent().build();
    }

    // ─── Cancelled RE Auto-Flag lookup ───
    @GetMapping("/department-routing/check-cancelled/{entityName}")
    public ResponseEntity<Map<String, Object>> checkCancelledEntity(@PathVariable String entityName) {
        var entry = routingRepo.findByEntityNameIgnoreCaseAndActiveTrue(entityName);
        if (entry.isPresent() && "CANCELLED".equals(entry.get().getRegistrationStatus())) {
            return ResponseEntity.ok(Map.of("cancelled", true, "entity", entry.get().getEntityName(),
                    "message", "This entity's registration has been cancelled."));
        }
        return ResponseEntity.ok(Map.of("cancelled", false));
    }
}
