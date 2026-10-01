package com.hrms.cms.repository;

import com.hrms.cms.entity.AppealHearing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppealHearingRepository extends JpaRepository<AppealHearing, Long> {

    /** Full history, oldest first -- what the frontend's hearing table renders. */
    List<AppealHearing> findByAppealNumberOrderBySequenceNoAscIdAsc(String appealNumber);

    /**
     * The operative hearing: the LIVE LISTING, if there is one.
     *
     * <p>Restricted to SCHEDULED/RESCHEDULED. A COMPLETED or ADJOURNED row is itself never superseded
     * (nothing replaces it), so matching on {@code supersededAt IS NULL} alone would report a hearing
     * that has already been heard as still listed -- which would keep the officer's slot occupied for
     * ever and make the next listing look like a reschedule of a concluded hearing.
     *
     * <p>Ordered and limited rather than expecting exactly one, so a historical data anomaly surfaces
     * as "the latest wins" instead of an exception on a read path.
     */
    @Query("""
            SELECT h FROM AppealHearing h
             WHERE h.appealNumber = :appealNumber
               AND h.supersededAt IS NULL
               AND h.eventType IN ('SCHEDULED', 'RESCHEDULED')
             ORDER BY h.sequenceNo DESC, h.id DESC
            """)
    List<AppealHearing> findOperative(@Param("appealNumber") String appealNumber);

    default Optional<AppealHearing> findOperativeOne(String appealNumber) {
        return findOperative(appealNumber).stream().findFirst();
    }

    @Query("SELECT COALESCE(MAX(h.sequenceNo), 0) FROM AppealHearing h WHERE h.appealNumber = :appealNumber")
    int maxSequenceNo(@Param("appealNumber") String appealNumber);

    /**
     * Live bookings for an officer inside a window, used for the double-booking control.
     *
     * <p>Excludes superseded rows (a hearing that was moved no longer occupies the slot) and
     * ADJOURNED/CANCELLED/COMPLETED events (those slots are spent, not reserved). Excludes the appeal
     * being scheduled so that rescheduling a hearing never collides with itself.
     */
    @Query("""
            SELECT h FROM AppealHearing h
             WHERE h.presidingOfficer = :officer
               AND h.supersededAt IS NULL
               AND h.eventType IN ('SCHEDULED', 'RESCHEDULED')
               AND h.appealNumber <> :excludeAppeal
               AND h.hearingDate >= :windowStart
               AND h.hearingDate < :windowEnd
            """)
    List<AppealHearing> findOfficerBookingsInWindow(@Param("officer") String officer,
                                                    @Param("excludeAppeal") String excludeAppeal,
                                                    @Param("windowStart") LocalDateTime windowStart,
                                                    @Param("windowEnd") LocalDateTime windowEnd);

    /**
     * Operative, still-future hearings -- the source for hearing reminder notices.
     *
     * <p>Only SCHEDULED/RESCHEDULED rows: there is nothing to remind anyone about once a hearing has
     * been heard or adjourned.
     */
    @Query("""
            SELECT h FROM AppealHearing h
             WHERE h.supersededAt IS NULL
               AND h.eventType IN ('SCHEDULED', 'RESCHEDULED')
               AND h.hearingDate >= :from
               AND h.hearingDate < :to
             ORDER BY h.hearingDate ASC
            """)
    List<AppealHearing> findUpcoming(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    void deleteByAppealNumber(String appealNumber);
}
