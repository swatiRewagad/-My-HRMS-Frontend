package com.hrms.cms.repository;

import com.hrms.cms.entity.CaseIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CaseIdSequenceRepository extends JpaRepository<CaseIdSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CaseIdSequence s WHERE s.officeCode = :officeCode AND s.financialYear = :financialYear")
    Optional<CaseIdSequence> findByOfficeCodeAndFinancialYearForUpdate(
            @Param("officeCode") String officeCode,
            @Param("financialYear") String financialYear);

    Optional<CaseIdSequence> findByOfficeCodeAndFinancialYear(String officeCode, String financialYear);
}
