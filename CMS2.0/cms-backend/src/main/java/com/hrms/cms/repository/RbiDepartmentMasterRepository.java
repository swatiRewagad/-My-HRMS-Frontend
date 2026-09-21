package com.hrms.cms.repository;

import com.hrms.cms.entity.RbiDepartmentMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RbiDepartmentMasterRepository extends JpaRepository<RbiDepartmentMaster, Long> {

    List<RbiDepartmentMaster> findByActiveTrueOrderBySortOrderAscNameAsc();

    Optional<RbiDepartmentMaster> findByCode(String code);

    /** Matches on code as well as name so typing "DoS" finds it without knowing the full title. */
    @Query("SELECT d FROM RbiDepartmentMaster d WHERE d.active = true AND ("
            + "LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%')) OR "
            + "LOWER(d.code) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "ORDER BY d.sortOrder ASC, d.name ASC")
    List<RbiDepartmentMaster> search(@Param("q") String query);
}
