package com.hrms.ecm.repository;

import com.hrms.ecm.entity.DocumentTypeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentTypeConfigRepository extends JpaRepository<DocumentTypeConfig, Long> {
    List<DocumentTypeConfig> findByProjectIdOrderByTypeNameAsc(Long projectId);
    List<DocumentTypeConfig> findByProjectIdAndStatusOrderByTypeNameAsc(Long projectId, String status);
}
