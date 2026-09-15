package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintNumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplaintNumberSequenceRepository extends JpaRepository<ComplaintNumberSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ComplaintNumberSequence s WHERE s.officeCode = :officeCode AND s.financialYear = :financialYear")
    Optional<ComplaintNumberSequence> findByOfficeCodeAndFinancialYearForUpdate(
            @Param("officeCode") String officeCode,
            @Param("financialYear") String financialYear);

    Optional<ComplaintNumberSequence> findByOfficeCodeAndFinancialYear(String officeCode, String financialYear);
}
