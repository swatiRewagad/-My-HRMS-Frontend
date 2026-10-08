package com.hrms.cms.repository;

import com.hrms.cms.entity.CepcLegalCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** One legal-case dossier per complaint — see {@link CepcLegalCase}. */
@Repository
public interface CepcLegalCaseRepository extends JpaRepository<CepcLegalCase, Long> {

    Optional<CepcLegalCase> findByComplaintNumber(String complaintNumber);
}
