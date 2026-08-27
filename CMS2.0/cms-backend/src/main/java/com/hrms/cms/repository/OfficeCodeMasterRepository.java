package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeCodeMaster;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfficeCodeMasterRepository extends JpaRepository<OfficeCodeMaster, Integer> {

    Optional<OfficeCodeMaster> findByOfficeNameAndIsActiveTrue(String officeName);

    Optional<OfficeCodeMaster> findByOfficeCodeAndIsActiveTrue(String officeCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OfficeCodeMaster o WHERE o.officeCode = :code AND o.isActive = true")
    Optional<OfficeCodeMaster> findByOfficeCodeForUpdate(@Param("code") String officeCode);

    @Modifying
    @Query("UPDATE OfficeCodeMaster o SET o.counter = 0 WHERE o.officeCode IN :codes")
    void resetCounters(@Param("codes") List<String> officeCodes);
}
