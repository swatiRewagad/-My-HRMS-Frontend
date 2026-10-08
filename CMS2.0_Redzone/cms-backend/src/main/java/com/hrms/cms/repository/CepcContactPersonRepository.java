package com.hrms.cms.repository;

import com.hrms.cms.entity.CepcContactPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads for the Contact Entity tab's contact-person list.
 *
 * <p>Only CEPC reads this table. Nothing in RBIO, the RE portal or the dashboard queries it, which is the
 * property that made a separate table preferable to a flag on {@code NODAL_OFFICER_RECORDS}.
 */
@Repository
public interface CepcContactPersonRepository extends JpaRepository<CepcContactPerson, Long> {

    /** Oldest first, so the list does not reshuffle when a contact is edited. */
    List<CepcContactPerson> findByComplaintNumberOrderByCreatedAtAsc(String complaintNumber);

    long countByComplaintNumber(String complaintNumber);

    /** Batched for the dashboard grid's Contact Person column — one query per page, not one per row. */
    List<CepcContactPerson> findByComplaintNumberIn(List<String> complaintNumbers);
}
