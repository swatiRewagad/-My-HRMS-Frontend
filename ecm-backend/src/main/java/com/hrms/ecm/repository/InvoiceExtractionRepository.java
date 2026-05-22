package com.hrms.ecm.repository;

import com.hrms.ecm.entity.InvoiceExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InvoiceExtractionRepository extends JpaRepository<InvoiceExtraction, Long> {
    Optional<InvoiceExtraction> findByFileId(Long fileId);
}
