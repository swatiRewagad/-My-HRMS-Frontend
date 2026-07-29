package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ComplaintCategoryRepository extends JpaRepository<ComplaintCategory, Long> {
    List<ComplaintCategory> findByParentIdIsNullOrderBySortOrder();
    List<ComplaintCategory> findByParentIdOrderBySortOrder(Long parentId);
    List<ComplaintCategory> findByStatus(String status);
    Optional<ComplaintCategory> findFirstByNameIgnoreCase(String name);
}
