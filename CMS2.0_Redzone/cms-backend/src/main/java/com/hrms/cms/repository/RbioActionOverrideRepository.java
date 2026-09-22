package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioActionOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RbioActionOverrideRepository extends JpaRepository<RbioActionOverride, Long> {

    /** Newest first, matching how the History tab renders. */
    List<RbioActionOverride> findByComplaintNumberOrderByOverriddenAtDesc(String complaintNumber);

    List<RbioActionOverride> findByComplaintNumberAndFieldNameOrderByOverriddenAtDesc(
            String complaintNumber, String fieldName);
}
