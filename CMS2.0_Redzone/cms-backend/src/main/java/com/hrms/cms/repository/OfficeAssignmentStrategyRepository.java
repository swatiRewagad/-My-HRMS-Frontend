package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeAssignmentStrategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfficeAssignmentStrategyRepository extends JpaRepository<OfficeAssignmentStrategy, Long> {

    /**
     * The one strategy row for an office.
     *
     * <p>Returns {@code Optional} rather than a list because the unique key on {@code officeId} makes more
     * than one impossible. An absent row is normal and means "never configured", which the resolver
     * treats as ROUND_ROBIN — the office still routes rather than refusing work.
     */
    Optional<OfficeAssignmentStrategy> findByOfficeId(String officeId);

    List<OfficeAssignmentStrategy> findAllByOrderByOfficeIdAsc();
}
