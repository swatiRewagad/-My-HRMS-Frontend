-- V120: SLA breach escalation — the once-only marker
-- Oracle version (mirrors database/V122__sla_breach_escalation_marker.sql; the two directories'
-- V-numbers are not in sync — this is the twin of MySQL V122, not of any V120 there)
--
-- Closes a go-live blocker: NO scheduled job anywhere scanned COMPLAINTS.SLA_DEADLINE. The column was
-- written (CepcSlaService.applySlaDeadline:132, RbioSlaService.applyStageSla:124) and read only
-- per-row on demand by controllers and dashboards, so SLA breach escalation for portal-filed
-- complaints was zero. The separate cms-sla-monitor-service could never have covered this: it reads
-- COMPLAINT_MASTER, a different table owned by cms-ingestion.
--
-- WHY A NEW COLUMN AND NOT ESCALATED_AT — ESCALATED_AT is the MANUAL/workflow escalation stamp, with
-- four live writers (WorkflowController:824, CepcWorkflowService:449, ComplaintService:306,
-- RbioWorkflowService:979). Reusing it would permanently hide a hand-escalated complaint's later SLA
-- breach, and would make the sweep's writes indistinguishable from an officer's action wherever
-- ESCALATED_AT is displayed. Follows the dedicated-marker precedent of
-- AA_ASSIGNMENT_RECORD.ESCALATED_AT (AaWorkloadRepository:59-66, AaEscalationSweepService:68).
--
-- WHY THE MARKER IS ALSO THE CONCURRENCY CONTROL — there is no ShedLock table, so the sweep runs on
-- every replica. The stamp is applied by a conditional UPDATE carrying
-- "AND SLA_BREACH_ESCALATED_AT IS NULL" (ComplaintRepository.claimSlaBreachEscalation); exactly one
-- replica's UPDATE can affect a row and the losers see 0 affected and notify nobody. A
-- compare-and-swap, so the notification is once-only even under simultaneous ticks.
--
-- NULLABLE, no default, NO BACKFILL: NULL correctly reads as "never escalated for breach" for every
-- pre-existing row. The complaints already open and past deadline WILL escalate on the first run, by
-- operator ruling — backfilling them as already-handled would hide the very backlog this surfaces.

DECLARE
    v_count NUMBER;

    PROCEDURE add_col(p_name VARCHAR2, p_type VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = p_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD ' || p_name || ' ' || p_type;
        END IF;
    END;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'COMPLAINTS';
    IF v_count = 0 THEN
        RETURN;
    END IF;

    add_col('SLA_BREACH_ESCALATED_AT', 'TIMESTAMP(6)');
END;
/

-- Serves the sweep's steady-state predicate:
--     SLA_BREACH_ESCALATED_AT IS NULL AND SLA_DEADLINE < :now AND LOWER(STATUS) NOT IN (:closed)
--
-- Leading SLA_DEADLINE keeps the scan proportional to the past-due set rather than to table size as
-- the register grows. It also exploits an Oracle-specific property deliberately: Oracle omits an index
-- entry only when ALL indexed columns are NULL, so the open complaints that have no SLA_DEADLINE at
-- all are simply absent from this index — which is precisely the set the sweep must skip.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES
     WHERE INDEX_NAME = 'IDX_COMPLAINTS_SLA_BREACH_SWEEP';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_COMPLAINTS_SLA_BREACH_SWEEP '
                       || 'ON COMPLAINTS(SLA_DEADLINE, SLA_BREACH_ESCALATED_AT)';
    END IF;
END;
/
