package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioCaseAssignmentHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RbioCaseAssignmentHistoryRepository extends JpaRepository<RbioCaseAssignmentHistory, Long> {

    List<RbioCaseAssignmentHistory> findByComplaintNumberOrderByAssignedAtDesc(String complaintNumber);

    /**
     * The officer currently holding the file, if any.
     *
     * <p>Returns a list rather than an Optional because a torn write could leave two open rows; the
     * caller closes all of them. Modelling it as an Optional would throw on the very state that needs
     * repairing.
     */
    @Query("""
           SELECT h FROM RbioCaseAssignmentHistory h
           WHERE h.complaintNumber = :complaintNumber AND h.releasedAt IS NULL
           ORDER BY h.assignedAt DESC
           """)
    List<RbioCaseAssignmentHistory> findOpenHolders(@Param("complaintNumber") String complaintNumber);

    /**
     * The most recent officer to have held this file in this role AND released it.
     *
     * <p>{@code releasedAt IS NOT NULL} is the load-bearing clause. Without it, a send-back would resolve
     * to the CURRENT holder whenever the sender happens to occupy the target role, which is how a file
     * ends up "sent back" to the person who sent it.
     *
     * <p>Ordered by {@code releasedAt} rather than {@code assignedAt}: after a file has bounced up and
     * down the ladder, the officer who held the role MOST RECENTLY is the one to return to, and that is
     * the order in which they let go of it, not the order in which they picked it up.
     */
    @Query("""
           SELECT h FROM RbioCaseAssignmentHistory h
           WHERE h.complaintNumber = :complaintNumber
             AND UPPER(h.roleName) = UPPER(:roleName)
             AND h.releasedAt IS NOT NULL
           ORDER BY h.releasedAt DESC
           """)
    List<RbioCaseAssignmentHistory> findPreviousHolders(@Param("complaintNumber") String complaintNumber,
                                                       @Param("roleName") String roleName,
                                                       Pageable pageable);

    /**
     * The FIRST officer ever to hold this file in this role.
     *
     * <p>UST552 needs this and it is NOT the same query as {@link #findPreviousHolders}: a reopen returns
     * the case to the officer who ORIGINALLY processed it, which after several send-backs is the earliest
     * holder, not the latest.
     */
    @Query("""
           SELECT h FROM RbioCaseAssignmentHistory h
           WHERE h.complaintNumber = :complaintNumber
             AND UPPER(h.roleName) = UPPER(:roleName)
           ORDER BY h.assignedAt ASC
           """)
    List<RbioCaseAssignmentHistory> findOriginalHolders(@Param("complaintNumber") String complaintNumber,
                                                       @Param("roleName") String roleName,
                                                       Pageable pageable);

    Optional<RbioCaseAssignmentHistory> findFirstByComplaintNumberAndOfficerIdOrderByAssignedAtDesc(
            String complaintNumber, String officerId);
}
