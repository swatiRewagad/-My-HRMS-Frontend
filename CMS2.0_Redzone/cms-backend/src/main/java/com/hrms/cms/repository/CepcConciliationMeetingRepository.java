package com.hrms.cms.repository;

import com.hrms.cms.entity.CepcConciliationMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CepcConciliationMeetingRepository extends JpaRepository<CepcConciliationMeeting, Long> {

    /** The whole series, oldest first — the order the history panel reverses for display. */
    List<CepcConciliationMeeting> findByComplaintNumberOrderBySequenceNoAsc(String complaintNumber);

    /** The live meeting, or the last one held if the series has run its course. */
    Optional<CepcConciliationMeeting> findFirstByComplaintNumberOrderBySequenceNoDesc(String complaintNumber);

    /** The whole series for one contact person's own meetings, oldest first. */
    List<CepcConciliationMeeting> findByContactPersonIdOrderBySequenceNoAsc(Long contactPersonId);

    /** The live meeting in a contact person's own series, or the last one held if it has run its course. */
    Optional<CepcConciliationMeeting> findFirstByContactPersonIdOrderBySequenceNoDesc(Long contactPersonId);
}
