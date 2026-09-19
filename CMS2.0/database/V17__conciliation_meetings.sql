-- V17: CONCILIATION_MEETINGS (MySQL)
--
-- Backs GET/PUT /api/complaints/rbio/{id}/conciliation, the RBIO Conciliation tab. Until now the
-- only conciliation state a complaint carried was COMPLAINTS.CONCILIATION_DATE and
-- CONCILIATION_OUTCOME, set by the SCHEDULE_MEETING / CONCILIATION_SUCCESS workflow actions. That
-- left the tab's own fields - who accepted the meeting, whether it ran over VC, and the minutes -
-- with nowhere to go.
--
-- A history table rather than the one-row-per-complaint shape the sibling tabs use (V15
-- COMPLAINT_RBIO_FORM_DATA, V14 COMPLAINT_ADDITIONAL_DETAILS): RBIO-US-018 allows a meeting to be
-- rescheduled repeatedly and requires the earlier attempts to survive, so COMPLAINT_ID is
-- deliberately NOT unique. The highest ID per complaint is the live meeting.
--
-- MEETING_TIME is a VARCHAR(5) 'HH:mm' and not a TIME column so the Oracle set can mirror it
-- column-for-column; RbioConciliationService validates the format on write.
--
-- Oracle equivalent: database/oracle/V17__conciliation_meetings.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing table
-- fails startup rather than misbehaving later.
-- Not idempotent: re-running fails with "table already exists".

CREATE TABLE CONCILIATION_MEETINGS (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_id            BIGINT        NOT NULL,
    -- SCHEDULED and RESCHEDULED are live and editable in place; COMPLETED and CANCELLED freeze the
    -- row so the next save opens a new meeting.
    meeting_status          VARCHAR(30)   NULL,
    meeting_date            DATE          NULL,
    meeting_time            VARCHAR(5)    NULL,
    accepted_by_complainant VARCHAR(10)   NULL,
    accepted_by_entity      VARCHAR(10)   NULL,
    conducted_through_vc    VARCHAR(10)   NULL,
    -- Minutes of the meeting, kept apart from the officer's own remarks in comments.
    meeting_comments        VARCHAR(4000) NULL,
    comments                VARCHAR(4000) NULL,
    created_by              VARCHAR(200)  NULL,
    updated_by              VARCHAR(200)  NULL,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NULL
);

CREATE INDEX idx_cm_complaint ON CONCILIATION_MEETINGS(complaint_id);
CREATE INDEX idx_cm_status ON CONCILIATION_MEETINGS(meeting_status);
