package com.hrms.cms.repository;

import com.hrms.cms.entity.CategoryMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryMasterRepository extends JpaRepository<CategoryMaster, Long> {
    List<CategoryMaster> findByActiveTrueOrderBySortOrderAsc();
    List<CategoryMaster> findBySchemeVersionAndActiveTrueOrderBySortOrderAsc(String schemeVersion);
    List<CategoryMaster> findByEntityTypeAndActiveTrueOrderBySortOrderAsc(String entityType);
}
