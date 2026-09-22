package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeAssignmentMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfficeAssignmentMappingRepository extends JpaRepository<OfficeAssignmentMapping, Long> {

    /**
     * The active mapping for one subject at one office.
     *
     * <p>{@code active} is part of the predicate rather than filtered afterwards so a deactivated mapping
     * behaves exactly like a missing one: the lookup falls through to the configured fallback instead of
     * routing to an officer an admin has deliberately taken out of the rota.
     */
    Optional<OfficeAssignmentMapping> findByOfficeIdAndMappingTypeAndSubjectKeyAndActiveTrue(
            String officeId, String mappingType, String subjectKey);

    List<OfficeAssignmentMapping> findByOfficeIdAndMappingTypeOrderBySubjectKeyAsc(
            String officeId, String mappingType);

    List<OfficeAssignmentMapping> findByOfficeIdOrderByMappingTypeAscSubjectKeyAsc(String officeId);

    /** Used to report how many mappings an office has before its strategy is switched away from them. */
    long countByOfficeIdAndMappingTypeAndActiveTrue(String officeId, String mappingType);
}
