package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioMeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RbioMeetingParticipantRepository extends JpaRepository<RbioMeetingParticipant, Long> {

    List<RbioMeetingParticipant> findByComplaintNumberOrderByCreatedAtAsc(String complaintNumber);

    List<RbioMeetingParticipant> findByMeetingIdOrderByCreatedAtAsc(Long meetingId);

    boolean existsByComplaintNumberAndParticipantNameIgnoreCase(String complaintNumber, String participantName);

    /**
     * Additional ENTITY participants only.
     *
     * <p>The cap in UST498 is on ADDITIONAL ENTITY participants, not on every row: the complainant and the
     * presiding officer are participants too and must not consume the allowance.
     */
    long countByComplaintNumberAndParticipantType(String complaintNumber, String participantType);
}
