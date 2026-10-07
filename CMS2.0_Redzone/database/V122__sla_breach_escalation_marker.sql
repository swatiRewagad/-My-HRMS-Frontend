-- V122: SLA breach escalation — the once-only marker
-- MySQL version. Oracle twin: database/oracle/V120__sla_breach_escalation_marker.sql
--
-- Closes a go-live blocker: NO scheduled job anywhere scanned COMPLAINTS.SLA_DEADLINE. The column was
-- written (CepcSlaService.applySlaDeadline:132, RbioSlaService.applyStageSla:124) and read only
-- per-row on demand by controllers and dashboards. Verified:
--     grep -rn "slaDeadline\|sla_deadline" cms-backend/src/main/java --include=*.java \
--       | grep -iE "@Query|findBy|select "
-- returned nothing. SLA breach escalation for portal-filed complaints was therefore zero.
--
-- ═══════════════════════════════════════════════════════════════════════════════════════════════════
-- WHY A NEW COLUMN AND NOT ESCALATED_AT
-- ═══════════════════════════════════════════════════════════════════════════════════════════════════
-- ESCALATED_AT already exists (167 non-null rows at the time of writing) but it is the MANUAL/workflow
-- escalation stamp, with four live writers: WorkflowController:824, CepcWorkflowService:449,
-- ComplaintService:306, RbioWorkflowService:979. Reusing it would fail in both directions:
--   * a complaint an officer escalated by hand on day 3 would be permanently excluded from the breach
--     sweep, so its day-30 SLA breach would never be raised; and
--   * the sweep's own writes would be indistinguishable from an officer's deliberate action wherever
--     ESCALATED_AT is displayed (PastComplaintService:248).
-- Two different facts, two different columns. Follows the dedicated-marker precedent already set by
-- AA_ASSIGNMENT_RECORD.ESCALATED_AT (AaWorkloadRepository:59-66, AaEscalationSweepService:68).
--
-- ═══════════════════════════════════════════════════════════════════════════════════════════════════
-- WHY THE MARKER IS THE CONCURRENCY CONTROL TOO
-- ═══════════════════════════════════════════════════════════════════════════════════════════════════
-- There is no ShedLock table, so the sweep runs on EVERY replica. The marker is stamped by a
-- conditional UPDATE (ComplaintRepository.claimSlaBreachEscalation) whose WHERE clause carries
-- "AND SLA_BREACH_ESCALATED_AT IS NULL". Exactly one replica's UPDATE can affect a row; the losers
-- see 0 affected and send nothing. That is a compare-and-swap, so the officer notification is
-- once-only even when two replicas tick simultaneously — not merely once-only across sequential runs,
-- which excluding stamped rows from the SELECT alone would NOT have guaranteed.
--
-- NULLABLE with no default and no backfill: NULL means "never escalated for breach", which is the
-- correct reading of every pre-existing row. The 518 complaints that are open and already past their
-- deadline WILL escalate on the first run, by operator ruling — silently backfilling them as
-- already-handled would hide the exact backlog this fix exists to surface.

ALTER TABLE complaints
    ADD COLUMN sla_breach_escalated_at DATETIME(6) NULL;

-- Serves the sweep's steady-state predicate:
--     sla_breach_escalated_at IS NULL AND sla_deadline < :now AND LOWER(status) NOT IN (:closed)
--
-- NOT strictly required — measured on 6720 rows the sweep already plans as a covering index scan over
-- idx_complaints_officer_status_deadline (type=index, 6478 entries examined, sub-10ms). This index
-- makes the DEADLINE the leading predicate so the scan stops being proportional to table size as the
-- register grows, and it is the shape both engines want. Leading sla_deadline also means Oracle omits
-- rows where BOTH indexed columns are NULL, which is exactly the 417 open complaints with no deadline
-- that the sweep must skip anyway.
CREATE INDEX idx_complaints_sla_breach_sweep
    ON complaints (sla_deadline, sla_breach_escalated_at);
