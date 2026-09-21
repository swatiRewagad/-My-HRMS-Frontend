package com.hrms.cms.repository;

import com.hrms.cms.entity.RegulatorMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegulatorMasterRepository extends JpaRepository<RegulatorMaster, Long> {

    List<RegulatorMaster> findByActiveTrueOrderBySortOrderAscNameAsc();

    Optional<RegulatorMaster> findByCode(String code);

    /** Matches on code as well as name so typing "SEBI" finds it without knowing the full title. */
    @Query("SELECT r FROM RegulatorMaster r WHERE r.active = true AND ("
            + "LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "LOWER(r.code) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "ORDER BY r.sortOrder ASC, r.name ASC")
    List<RegulatorMaster> search(@Param("q") String query);
}
