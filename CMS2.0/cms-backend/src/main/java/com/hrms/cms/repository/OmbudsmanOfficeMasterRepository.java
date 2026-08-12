package com.hrms.cms.repository;

import com.hrms.cms.entity.OmbudsmanOfficeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OmbudsmanOfficeMasterRepository extends JpaRepository<OmbudsmanOfficeMaster, Integer> {

    @Query("SELECT o FROM OmbudsmanOfficeMaster o WHERE o.isActive = true AND LOWER(o.jurisdiction) LIKE LOWER(CONCAT('%', :state, '%'))")
    List<OmbudsmanOfficeMaster> findByJurisdictionContainingState(@Param("state") String state);

    Optional<OmbudsmanOfficeMaster> findByOfficeNameAndIsActiveTrue(String officeName);
}
