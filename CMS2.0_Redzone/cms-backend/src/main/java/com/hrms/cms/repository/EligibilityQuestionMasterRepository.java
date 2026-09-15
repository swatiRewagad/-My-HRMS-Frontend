package com.hrms.cms.repository;

import com.hrms.cms.entity.EligibilityQuestionMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EligibilityQuestionMasterRepository extends JpaRepository<EligibilityQuestionMaster, Long> {

    List<EligibilityQuestionMaster> findBySchemeVersionAndActiveTrueOrderByQuestionNumberAsc(String schemeVersion);

    List<EligibilityQuestionMaster> findByActiveTrueOrderByQuestionNumberAsc();

    Optional<EligibilityQuestionMaster> findBySchemeVersionAndQuestionKey(String schemeVersion, String questionKey);
}
