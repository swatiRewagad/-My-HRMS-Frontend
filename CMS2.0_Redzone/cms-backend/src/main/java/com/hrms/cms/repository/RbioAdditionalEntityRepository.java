package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioAdditionalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RbioAdditionalEntityRepository extends JpaRepository<RbioAdditionalEntity, Long> {

    List<RbioAdditionalEntity> findByComplaintNumberOrderByCreatedAtAsc(String complaintNumber);

    /** The count the six-cap is enforced against. */
    long countByComplaintNumber(String complaintNumber);

    boolean existsByComplaintNumberAndEntityNameIgnoreCase(String complaintNumber, String entityName);
}
