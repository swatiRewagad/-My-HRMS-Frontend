package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RbioMeetingRepository extends JpaRepository<RbioMeeting, Long> {

    /**
     * The full meeting history for a complaint, oldest first — including superseded rows.
     *
     * <p>Superseded rows are returned deliberately: UST502 requires the PREVIOUS meeting details to remain
     * visible after a reschedule, so a query that filtered them out would defeat the reason the table is
     * append-only.
     */
    List<RbioMeeting> findByComplaintNumberOrderByPerformedAtAscIdAsc(String complaintNumber);

    /**
     * The operative meeting: the one row not yet superseded.
     *
     * <p>Ordered and limited rather than assumed-unique. A unique constraint on
     * (complaintNumber, supersededAt) cannot be expressed in MySQL because NULLs do not collide, so
     * "exactly one operative row" is maintained by the service, and this query stays correct even if a
     * concurrent write briefly produced two.
     */
    @Query("SELECT m FROM RbioMeeting m WHERE m.complaintNumber = :complaintNumber "
            + "AND m.supersededAt IS NULL ORDER BY m.id DESC")
    List<RbioMeeting> findOperative(@Param("complaintNumber") String complaintNumber);

    default Optional<RbioMeeting> findOperativeMeeting(String complaintNumber) {
        List<RbioMeeting> rows = findOperative(complaintNumber);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** The most recent COMPLETED row, which is what the MOM letter renders. */
    @Query("SELECT m FROM RbioMeeting m WHERE m.complaintNumber = :complaintNumber "
            + "AND m.eventType = 'COMPLETED' ORDER BY m.performedAt DESC, m.id DESC")
    List<RbioMeeting> findCompleted(@Param("complaintNumber") String complaintNumber);

    default Optional<RbioMeeting> findLatestCompleted(String complaintNumber) {
        List<RbioMeeting> rows = findCompleted(complaintNumber);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    long countByComplaintNumber(String complaintNumber);
}
