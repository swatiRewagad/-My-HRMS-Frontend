package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintEditPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComplaintEditPresenceRepository extends JpaRepository<ComplaintEditPresence, Long> {

    Optional<ComplaintEditPresence> findByComplaintNumberAndUserId(String complaintNumber, String userId);

    /**
     * Everyone OTHER than the caller with a fresh heartbeat on this complaint.
     *
     * <p>Excluding the caller server-side matters: a user who reopens a form in a second tab would
     * otherwise be warned that they themselves are editing it, which trains people to dismiss the warning.
     */
    @Query("SELECT p FROM ComplaintEditPresence p "
            + "WHERE p.complaintNumber = :complaintNumber "
            + "AND p.userId <> :excludeUserId "
            + "AND p.heartbeatAt >= :freshSince "
            + "ORDER BY p.heartbeatAt DESC")
    List<ComplaintEditPresence> findOtherActiveEditors(
            @Param("complaintNumber") String complaintNumber,
            @Param("excludeUserId") String excludeUserId,
            @Param("freshSince") LocalDateTime freshSince);

    @Modifying
    @Query("DELETE FROM ComplaintEditPresence p WHERE p.heartbeatAt < :cutoff")
    int deleteStale(@Param("cutoff") LocalDateTime cutoff);
}
