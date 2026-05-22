package com.hrms.ecm.repository;

import com.hrms.ecm.entity.InvoiceFieldConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvoiceFieldConfigRepository extends JpaRepository<InvoiceFieldConfig, Long> {
    List<InvoiceFieldConfig> findByDocTypeConfigIdOrderByDisplayOrderAsc(Long docTypeConfigId);
    void deleteByDocTypeConfigId(Long docTypeConfigId);
}
