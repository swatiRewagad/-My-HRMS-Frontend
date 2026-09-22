package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioStatusMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RbioStatusMasterRepository extends JpaRepository<RbioStatusMaster, String> {

    List<RbioStatusMaster> findByIsActiveOrderByDisplayOrderAsc(String isActive);

    /**
     * The legacy status strings that mean "closed". THE authoritative closed-status list, replacing the
     * hardcoded copies in WorkflowController and NotificationScheduledTasks.
     *
     * <p>DISTINCT because several status codes map to one legacy value, and a duplicated value in a
     * {@code NOT IN} clause is harmless but makes the generated SQL confusing to read in a slow-query
     * log.
     */
    @Query("""
           SELECT DISTINCT s.legacyValue FROM RbioStatusMaster s
           WHERE s.isClosed = 'Y' AND s.isActive = 'Y' AND s.legacyValue IS NOT NULL
           """)
    List<String> findClosedLegacyValues();

    /**
     * The legacy status strings from which a conciliation meeting may NOT be scheduled (UST497).
     *
     * <p>Returns LEGACY values because that is what {@code COMPLAINTS.status} actually holds — comparing a
     * live complaint against STATUS_CODEs would match nothing.
     *
     * <p>Note that two of the six excluded statuses ({@code OMBUDSMAN_DECISION}, {@code DY_OMB_DECISION})
     * have a NULL legacy value, so no live complaint can currently be in them; they are still flagged so
     * the exclusion holds the moment a session starts writing them.
     */
    @Query("""
           SELECT DISTINCT s.legacyValue FROM RbioStatusMaster s
           WHERE s.blocksMeeting = 'Y' AND s.isActive = 'Y' AND s.legacyValue IS NOT NULL
           """)
    List<String> findMeetingBlockedLegacyValues();

    /**
     * Status codes flagged as blocking a meeting, whether or not they have a legacy value.
     *
     * <p>Used to tell "the master has been seeded and nothing blocks meetings" apart from "the master has
     * never been configured", which the service must distinguish in order to fail closed on the latter.
     */
    @Query("SELECT COUNT(s) FROM RbioStatusMaster s WHERE s.blocksMeeting IS NOT NULL")
    long countWithMeetingRuleConfigured();
}
