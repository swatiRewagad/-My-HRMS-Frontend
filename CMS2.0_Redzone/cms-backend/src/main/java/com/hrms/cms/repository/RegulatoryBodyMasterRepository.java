package com.hrms.cms.repository;

import com.hrms.cms.entity.RegulatoryBodyMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegulatoryBodyMasterRepository extends JpaRepository<RegulatoryBodyMaster, Long> {

    List<RegulatoryBodyMaster> findByIsActiveOrderByBodyNameAsc(String isActive);

    Optional<RegulatoryBodyMaster> findByBodyCodeIgnoreCase(String bodyCode);

    Optional<RegulatoryBodyMaster> findByBodyNameIgnoreCase(String bodyName);
}
