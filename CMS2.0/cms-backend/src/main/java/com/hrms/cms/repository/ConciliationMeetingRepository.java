package com.hrms.cms.repository;

import com.hrms.cms.entity.ConciliationMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConciliationMeetingRepository extends JpaRepository<ConciliationMeeting, Long> {

    /** Oldest first, so the response reads as a reschedule trail. */
    List<ConciliationMeeting> findByComplaintIdOrderByIdAsc(Long complaintId);

    /** The live meeting: the most recently opened row, whether or not it is still open. */
    Optional<ConciliationMeeting> findFirstByComplaintIdOrderByIdDesc(Long complaintId);
}
