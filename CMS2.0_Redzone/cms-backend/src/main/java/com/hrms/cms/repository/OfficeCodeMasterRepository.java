package com.hrms.cms.repository;

import com.hrms.cms.entity.OfficeCodeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfficeCodeMasterRepository extends JpaRepository<OfficeCodeMaster, Integer> {

    Optional<OfficeCodeMaster> findByOfficeNameAndIsActiveTrue(String officeName);

    Optional<OfficeCodeMaster> findByOfficeNameAndOfficeTypeAndIsActiveTrue(String officeName, String officeType);

    Optional<OfficeCodeMaster> findByOfficeCodeAndIsActiveTrue(String officeCode);

    /**
     * Offices of one type, for the transfer-destination pickers (UST556, 563).
     *
     * <p>Needed because {@code officeType} was 'BO' on every seeded row, so "the list of CEPC offices" had no
     * data source and the CEPC branch of the transfer form could not be populated at all. CEPC offices are
     * now rows with {@code officeType = 'CEPC'}; the pre-existing 'BO' rows are untouched, so every current
     * reader behaves exactly as before.
     */
    List<OfficeCodeMaster> findByOfficeTypeAndIsActiveTrueOrderByOfficeNameAsc(String officeType);

    List<OfficeCodeMaster> findByIsActiveTrueOrderByOfficeNameAsc();
}
