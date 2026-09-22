package com.hrms.cms.repository;

import com.hrms.cms.entity.EntityOfficeNodalOfficer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntityOfficeNodalOfficerRepository extends JpaRepository<EntityOfficeNodalOfficer, Long> {

    /**
     * Office-specific contacts. Equality on the normalised name only — see the class comment on
     * {@link EntityOfficeNodalOfficer} for why a LIKE here would be a cross-entity data leak.
     */
    Optional<EntityOfficeNodalOfficer> findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(
            String entityNameNormalized, String processingOffice);

    /** Entity-wide default: the row an operator saves with no office, meaning "one desk for the country". */
    Optional<EntityOfficeNodalOfficer> findFirstByEntityNameNormalizedAndProcessingOfficeIsNullAndActiveTrue(
            String entityNameNormalized);

    List<EntityOfficeNodalOfficer> findByEntityNameNormalizedAndActiveTrue(String entityNameNormalized);
}
