package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeOverflowMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OfficeOverflowMappingRepository extends JpaRepository<OfficeOverflowMapping, Integer> {

    Optional<OfficeOverflowMapping> findByOfficeCodeAndIsActiveTrue(String officeCode);

    Optional<OfficeOverflowMapping> findByOfficeNameAndIsActiveTrue(String officeName);
}
