package com.hrms.cms.controller;

import com.hrms.cms.dto.routing.CancelledEntityResponse;
import com.hrms.cms.entity.AccountTypeMaster;
import com.hrms.cms.entity.CategoryMaster;
import com.hrms.cms.entity.DepartmentRoutingMaster;
import com.hrms.cms.entity.RbiDepartmentMaster;
import com.hrms.cms.entity.RegulatorMaster;
import com.hrms.cms.repository.AccountTypeMasterRepository;
import com.hrms.cms.repository.CategoryMasterRepository;
import com.hrms.cms.repository.DepartmentRoutingMasterRepository;
import com.hrms.cms.repository.RbiDepartmentMasterRepository;
import com.hrms.cms.repository.RegulatorMasterRepository;
import com.rbi.cms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/masters")
@RequiredArgsConstructor
public class MasterDataController {

    private final CategoryMasterRepository categoryRepo;
    private final DepartmentRoutingMasterRepository routingRepo;
    private final AccountTypeMasterRepository accountTypeRepo;
    private final RegulatorMasterRepository regulatorRepo;
    private final RbiDepartmentMasterRepository rbiDepartmentRepo;

    // ─── Category Master ───
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryMaster>>> getCategories(
            @RequestParam(required = false) String schemeVersion,
            @RequestParam(required = false) String entityType) {
        if (schemeVersion != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    categoryRepo.findBySchemeVersionAndActiveTrueOrderBySortOrderAsc(schemeVersion)));
        }
        if (entityType != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    categoryRepo.findByEntityTypeAndActiveTrueOrderBySortOrderAsc(entityType)));
        }
        return ResponseEntity.ok(ApiResponse.success(categoryRepo.findByActiveTrueOrderBySortOrderAsc()));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<CategoryMaster>> createCategory(@RequestBody CategoryMaster category) {
        category.setActive(true);
        return ResponseEntity.ok(ApiResponse.success(categoryRepo.save(category), "Category created"));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<CategoryMaster>> updateCategory(@PathVariable Long id,
                                                                      @RequestBody CategoryMaster category) {
        CategoryMaster existing = categoryRepo.findById(id).orElseThrow();
        existing.setCategoryName(category.getCategoryName());
        existing.setSubCategory(category.getSubCategory());
        existing.setSchemeVersion(category.getSchemeVersion());
        existing.setEntityType(category.getEntityType());
        existing.setSortOrder(category.getSortOrder());
        existing.setActive(category.isActive());
        return ResponseEntity.ok(ApiResponse.success(categoryRepo.save(existing), "Category updated"));
    }

    /** Deactivated rather than deleted so complaints already classified keep a resolvable category. */
    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        CategoryMaster existing = categoryRepo.findById(id).orElseThrow();
        existing.setActive(false);
        categoryRepo.save(existing);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "Category deactivated"));
    }

    // ─── Department Routing Master ───
    @GetMapping("/department-routing")
    public ResponseEntity<ApiResponse<List<DepartmentRoutingMaster>>> getRoutingRules(
            @RequestParam(required = false) String department) {
        if (department != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    routingRepo.findByDepartmentAndActiveTrueOrderByEntityNameAsc(department)));
        }
        return ResponseEntity.ok(ApiResponse.success(routingRepo.findByActiveTrueOrderByEntityNameAsc()));
    }

    @PostMapping("/department-routing")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentRoutingMaster>> createRoutingRule(
            @RequestBody DepartmentRoutingMaster rule) {
        rule.setActive(true);
        return ResponseEntity.ok(ApiResponse.success(routingRepo.save(rule), "Routing rule created"));
    }

    @PutMapping("/department-routing/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentRoutingMaster>> updateRoutingRule(
            @PathVariable Long id, @RequestBody DepartmentRoutingMaster rule) {
        DepartmentRoutingMaster existing = routingRepo.findById(id).orElseThrow();
        existing.setEntityName(rule.getEntityName());
        existing.setDepartment(rule.getDepartment());
        existing.setTargetOffice(rule.getTargetOffice());
        existing.setRegistrationStatus(rule.getRegistrationStatus());
        existing.setActive(rule.isActive());
        return ResponseEntity.ok(ApiResponse.success(routingRepo.save(existing), "Routing rule updated"));
    }

    /** Deactivated rather than deleted so complaints already routed keep a resolvable rule. */
    @DeleteMapping("/department-routing/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRoutingRule(@PathVariable Long id) {
        DepartmentRoutingMaster existing = routingRepo.findById(id).orElseThrow();
        existing.setActive(false);
        routingRepo.save(existing);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "Routing rule deactivated"));
    }

    // ─── Account Types ───
    @GetMapping("/account-types")
    public ResponseEntity<ApiResponse<List<AccountTypeMaster>>> getAccountTypes() {
        return ResponseEntity.ok(ApiResponse.success(accountTypeRepo.findByActiveTrueOrderBySortOrderAsc()));
    }

    // ─── Forward targets: external regulators and internal RBI departments ───

    /** Backs the RBIO Forward tab's "Name of Regulator" lookup. {@code q} filters server-side. */
    @GetMapping("/regulators")
    public ResponseEntity<ApiResponse<List<RegulatorMaster>>> getRegulators(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(q == null || q.isBlank()
                ? regulatorRepo.findByActiveTrueOrderBySortOrderAscNameAsc()
                : regulatorRepo.search(q.trim())));
    }

    @PostMapping("/regulators")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<RegulatorMaster>> createRegulator(@RequestBody RegulatorMaster regulator) {
        regulator.setActive(true);
        return ResponseEntity.ok(ApiResponse.success(regulatorRepo.save(regulator), "Regulator created"));
    }

    @PutMapping("/regulators/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<RegulatorMaster>> updateRegulator(@PathVariable Long id,
                                                                        @RequestBody RegulatorMaster regulator) {
        return regulatorRepo.findById(id)
                .map(existing -> {
                    existing.setCode(regulator.getCode());
                    existing.setName(regulator.getName());
                    existing.setEmail(regulator.getEmail());
                    existing.setSortOrder(regulator.getSortOrder());
                    return ResponseEntity.ok(ApiResponse.success(regulatorRepo.save(existing), "Regulator updated"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Regulator not found: " + id)));
    }

    /** Deactivated rather than deleted so complaints already forwarded keep a resolvable target. */
    @DeleteMapping("/regulators/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateRegulator(@PathVariable Long id) {
        return regulatorRepo.findById(id)
                .map(existing -> {
                    existing.setActive(false);
                    regulatorRepo.save(existing);
                    return ResponseEntity.ok(ApiResponse.<Void>success(null, "Regulator deactivated"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Regulator not found: " + id)));
    }

    /** Backs the RBIO Forward tab's "Name of Department" lookup. {@code q} filters server-side. */
    @GetMapping("/rbi-departments")
    public ResponseEntity<ApiResponse<List<RbiDepartmentMaster>>> getRbiDepartments(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(q == null || q.isBlank()
                ? rbiDepartmentRepo.findByActiveTrueOrderBySortOrderAscNameAsc()
                : rbiDepartmentRepo.search(q.trim())));
    }

    @PostMapping("/rbi-departments")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<RbiDepartmentMaster>> createRbiDepartment(
            @RequestBody RbiDepartmentMaster department) {
        department.setActive(true);
        return ResponseEntity.ok(ApiResponse.success(rbiDepartmentRepo.save(department), "Department created"));
    }

    @PutMapping("/rbi-departments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<RbiDepartmentMaster>> updateRbiDepartment(
            @PathVariable Long id, @RequestBody RbiDepartmentMaster department) {
        return rbiDepartmentRepo.findById(id)
                .map(existing -> {
                    existing.setCode(department.getCode());
                    existing.setName(department.getName());
                    existing.setEmail(department.getEmail());
                    existing.setSortOrder(department.getSortOrder());
                    return ResponseEntity.ok(
                            ApiResponse.success(rbiDepartmentRepo.save(existing), "Department updated"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Department not found: " + id)));
    }

    /** Deactivated rather than deleted so complaints already forwarded keep a resolvable target. */
    @DeleteMapping("/rbi-departments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateRbiDepartment(@PathVariable Long id) {
        return rbiDepartmentRepo.findById(id)
                .map(existing -> {
                    existing.setActive(false);
                    rbiDepartmentRepo.save(existing);
                    return ResponseEntity.ok(ApiResponse.<Void>success(null, "Department deactivated"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Department not found: " + id)));
    }

    // ─── Cancelled RE Auto-Flag lookup ───
    @GetMapping("/department-routing/check-cancelled/{entityName}")
    public ResponseEntity<ApiResponse<CancelledEntityResponse>> checkCancelledEntity(
            @PathVariable String entityName) {
        var entry = routingRepo.findByEntityNameIgnoreCaseAndActiveTrue(entityName);
        if (entry.isPresent() && "CANCELLED".equals(entry.get().getRegistrationStatus())) {
            return ResponseEntity.ok(ApiResponse.success(CancelledEntityResponse.builder()
                    .cancelled(true)
                    .entity(entry.get().getEntityName())
                    .message("This entity's registration has been cancelled.")
                    .build()));
        }
        return ResponseEntity.ok(ApiResponse.success(CancelledEntityResponse.notCancelled()));
    }
}
