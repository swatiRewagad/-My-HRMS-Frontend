package com.hrms.cms.controller;

import com.hrms.cms.entity.CategoryMaster;
import com.hrms.cms.entity.DepartmentRoutingMaster;
import com.hrms.cms.repository.CategoryMasterRepository;
import com.hrms.cms.repository.DepartmentRoutingMasterRepository;
import com.hrms.cms.security.CmsAuthority;
import com.hrms.cms.security.RequiresAuthority;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Master data: anonymous READ, authority-guarded WRITE (UST456-459).
 *
 * <h2>Why the annotations changed</h2>
 * Every write here carried {@code @PreAuthorize("hasAnyRole('ADMIN','CRPC_ADMIN')")} and NONE of them ran:
 * {@code @EnableMethodSecurity} is absent from this application, so all six were inert. Combined with
 * {@code /api/v1/masters/**} being {@code permitAll} in the filter chain, an anonymous caller could rewrite
 * CATEGORY_MASTER and DEPARTMENT_ROUTING_MASTER — the tables that decide which office a complaint reaches
 * and whether it is maintainable.
 *
 * <p>Two controls now apply, deliberately. The filter chain restricts the write VERBS to the master-admin
 * roles, and {@link RequiresAuthority} requires the {@code MASTER_DATA_WRITE} authority granted by the SSO.
 * The chain is coarse and role-based; the authority is the control that survives a role being renamed or
 * split in the SSO without a change here, since user administration is delegated to the SSO.
 *
 * <p>Reads stay anonymous: the citizen filing wizard needs the category list before anyone signs in.
 */
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
    @RequiresAuthority(CmsAuthority.MASTER_DATA_WRITE)
    public ResponseEntity<CategoryMaster> createCategory(@RequestBody CategoryMaster category) {
        category.setActive(true);
        return ResponseEntity.ok(categoryRepo.save(category));
    }

    @PutMapping("/categories/{id}")
    @RequiresAuthority(CmsAuthority.MASTER_DATA_WRITE)
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
    @RequiresAuthority(CmsAuthority.MASTER_DATA_WRITE)
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
    @RequiresAuthority(CmsAuthority.MASTER_DATA_WRITE)
    public ResponseEntity<DepartmentRoutingMaster> createRoutingRule(@RequestBody DepartmentRoutingMaster rule) {
        rule.setActive(true);
        return ResponseEntity.ok(routingRepo.save(rule));
    }

    @PutMapping("/department-routing/{id}")
    @RequiresAuthority(CmsAuthority.MASTER_DATA_WRITE)
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
    @RequiresAuthority(CmsAuthority.MASTER_DATA_WRITE)
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
