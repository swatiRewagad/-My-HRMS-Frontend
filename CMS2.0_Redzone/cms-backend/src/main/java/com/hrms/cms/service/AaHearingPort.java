package com.hrms.cms.service;

import com.hrms.cms.dto.AaHearingRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hearing persistence for an appeal.
 *
 * Published by S3A as a seam so the state machine can schedule a hearing without owning how hearings
 * are stored; S3C implements it. S3A ships a minimal implementation that keeps the existing
 * Appeal.hearingDate/hearingVenue behaviour working, so the workflow functions before S3C lands.
 *
 * Why this exists at all: SCHEDULE_HEARING currently OVERWRITES hearingDate and hearingVenue in place.
 * A rescheduled statutory hearing therefore leaves no trace that the earlier sitting was ever fixed or
 * vacated, which is an audit failure rather than a missing feature. Every call here appends a record.
 *
 * {@code when} is a LocalDateTime, not a String. The existing code parses a client-supplied string with
 * LocalDateTime.parse and throws DateTimeParseException — a RuntimeException the controller does not
 * catch — on a date-only value like "2026-09-30", producing a 500. Parsing belongs at the edge, so this
 * seam takes an already-valid value.
 */
public interface AaHearingPort {

    /**
     * Fixes a first hearing.
     *
     * @param partiesToNotify recipients a notice is owed to; may be empty, never null
     * @return the persisted record
     */
    AaHearingRecord schedule(String appealNumber, LocalDateTime when, String venue, String mode,
                             List<String> partiesToNotify);

    /**
     * Vacates the current sitting and fixes a new one, preserving the old record.
     *
     * @param reason why it moved — required, because a vacated hearing without a reason is not
     *               auditable
     */
    AaHearingRecord reschedule(String appealNumber, LocalDateTime when, String venue, String mode,
                               String reason, List<String> partiesToNotify);

    /** Records what happened at the sitting. */
    AaHearingRecord recordOutcome(String appealNumber, String outcome, String remarks);

    /** Full history, newest first. Empty when no hearing was ever fixed — never null. */
    List<AaHearingRecord> history(String appealNumber);

    /** The sitting currently in force, or null when none is fixed or the last one was completed. */
    AaHearingRecord current(String appealNumber);
}
