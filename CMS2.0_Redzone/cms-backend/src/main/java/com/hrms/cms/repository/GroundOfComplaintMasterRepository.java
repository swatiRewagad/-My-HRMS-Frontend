package com.hrms.cms.repository;

import com.hrms.cms.entity.GroundOfComplaintMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GroundOfComplaintMasterRepository extends JpaRepository<GroundOfComplaintMaster, Long> {

    Optional<GroundOfComplaintMaster> findBySchemeVersionAndGroundCode(String schemeVersion, String groundCode);

    /**
     * Grounds in force for a scheme on a given date, for the search dropdown.
     *
     * Date-bounded like the clause master so a Scheme amendment retires a ground without deleting it:
     * complaints already filed under a retired ground must remain findable.
     */
    @Query("""
           SELECT g FROM GroundOfComplaintMaster g
           WHERE g.schemeVersion = :schemeVersion
             AND g.active = true
             AND (g.effectiveFrom IS NULL OR g.effectiveFrom <= :onDate)
             AND (g.effectiveTo IS NULL OR g.effectiveTo >= :onDate)
           ORDER BY g.sortOrder ASC, g.label ASC
           """)
    List<GroundOfComplaintMaster> findInForce(@Param("schemeVersion") String schemeVersion,
                                             @Param("onDate") LocalDate onDate);

    List<GroundOfComplaintMaster> findByActiveTrueOrderBySortOrderAsc();
}
