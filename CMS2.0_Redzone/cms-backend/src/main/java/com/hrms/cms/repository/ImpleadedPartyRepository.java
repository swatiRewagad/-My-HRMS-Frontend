package com.hrms.cms.repository;

import com.hrms.cms.entity.ImpleadedParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImpleadedPartyRepository extends JpaRepository<ImpleadedParty, Long> {

    List<ImpleadedParty> findByComplaintNumberOrderByImpleadedAtAsc(String complaintNumber);

    long countByComplaintNumber(String complaintNumber);

    /** Used to reject impleading the same party twice into one complaint. */
    Optional<ImpleadedParty> findByComplaintNumberAndPartyNameIgnoreCase(String complaintNumber,
                                                                        String partyName);

    /** The closure gate: any row here means a party's required data is still outstanding. */
    List<ImpleadedParty> findByComplaintNumberAndDataStatusNot(String complaintNumber, String dataStatus);
}
