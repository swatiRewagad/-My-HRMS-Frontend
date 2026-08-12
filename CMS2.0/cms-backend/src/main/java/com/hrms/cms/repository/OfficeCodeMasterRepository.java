package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeCodeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OfficeCodeMasterRepository extends JpaRepository<OfficeCodeMaster, Integer> {

    Optional<OfficeCodeMaster> findByOfficeNameAndIsActiveTrue(String officeName);

    Optional<OfficeCodeMaster> findByOfficeCodeAndIsActiveTrue(String officeCode);
}
