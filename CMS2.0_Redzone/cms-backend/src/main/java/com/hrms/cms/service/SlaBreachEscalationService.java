package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The SLA breach sweep: finds open complaints that have blown {@code COMPLAINTS.SLA_DEADLINE} and
 * escalates each one exactly once, to the officer who owns it.
 *
 * <p><b>Why this exists.</b> Nothing scanned {@code sla_deadline}. The column was written
 * ({@link CepcSlaService#applySlaDeadline} and {@code RbioSlaService.applyStageSla}) and then read
 * only per-row, on demand, by controllers and dashboards — so a deadline could pass and the only
 * person who would ever find out was whoever happened to open that complaint's screen. SLA breach
 * escalation for portal-filed complaints was genuinely zero.
 *
 * <p><b>Why this lives in cms-backend rather than cms-sla-monitor-service</b>, following the
 * reasoning already recorded on {@link ReActivitySweepService}: that service queries
 * {@code COMPLAINT_MASTER}, a table owned by cms-ingestion holding an uppercase {@code ComplaintStatus}
 * enum, which does not exist in this datasource at all. It cannot see {@code COMPLAINTS} rows, let
 * alone write them, and it holds only a raw EntityManager and a KafkaTemplate with no transaction.
 * Here the detection, the marker write and the notification happen against one datasource in one
 * transaction, so a complaint can never be marked escalated without the officer being told.
 *
 * <h2>WHY THIS DOES NOT PUBLISH TO KAFKA — DO NOT "IMPROVE" THIS BY ADDING THE PUBLISH</h2>
 *
 * <p>The obvious-looking change is to call
 * {@code ComplaintEventPublisher.publishComplaintEscalated} so the three existing consumers of
 * {@code complaint.escalated} light up. <b>That would re-arm a self-feeding event loop in
 * production.</b> The cycle, verified by reading the code:
 *
 * <ol>
 *   <li>cms-backend publishes {@code complaint.escalated}
 *       ({@code ComplaintEventPublisher:46});</li>
 *   <li>{@code cms-workflow-service ComplaintEscalatedListener:27} consumes that topic and calls
 *       {@code escalateComplaint} at its line 45;</li>
 *   <li>the production implementation {@code KogitoWorkflowService.escalateComplaint:178} then calls
 *       {@code eventPublisher.publishEscalated} at <b>its line 208</b>;</li>
 *   <li>{@code KafkaWorkflowEventPublisher:47-48} publishes to
 *       {@code KafkaTopics.COMPLAINT_ESCALATED} — <b>the same topic the listener in step 2
 *       consumes</b> (both resolve to the literal {@code "complaint.escalated"});</li>
 *   <li>back to step 2, without bound.</li>
 * </ol>
 *
 * <p>Nothing breaks the cycle. {@code escalateComplaint}'s only early returns are at its lines 182
 * and 189, both BEFORE the republish; it writes {@code status = ESCALATED} but never checks that
 * status first, so there is no "already escalated, stop" guard. The topic has no idempotency key
 * either — {@code ComplaintEscalatedListener} does not consult {@code ProcessedEventRepository}
 * (only {@code ComplaintIngestedListener:90} does). Every lap also sends the citizen another email
 * ({@code ComplaintEventNotificationListener:77-88}) and triggers another reindex. The loop is
 * bounded by Kafka throughput, not by this sweep's cron, so the once-only marker below CANNOT
 * contain it: the loop is downstream of the first publish and does not involve this class at all.
 * One published event is enough to start it.
 *
 * <p>Note also that publishing is not merely a notification: step 3 reassigns the complaint to
 * {@code {DEPT}_SUPERVISOR} at stage {@code SUPERVISOR_REVIEW}. Publishing for a backlog of breached
 * complaints would forcibly move all of them off their current officers.
 *
 * <p>All three participants are {@code @Profile("!dev-local")}, so none of this reproduces on a
 * developer machine — a dev-local run looks completely clean and proves nothing.
 *
 * <p><b>What would have to change before this could publish:</b> (1) the republish at
 * {@code KogitoWorkflowService:208} must stop feeding the topic its own service consumes, or be
 * guarded so an already-ESCALATED workflow instance does not re-emit; (2) {@code complaint.escalated}
 * needs an idempotency key honoured by all three consumers, as {@code complaint.ingested} already
 * has; and (3) the reassignment-to-supervisor side effect needs an explicit product ruling, because
 * it changes who owns the complaint. Until all three hold, escalation stays in-process. This was an
 * operator decision, not an oversight.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlaBreachEscalationService {

    static final String CONFIG_ENABLED = "cms.sla.breach_escalation_enabled";
    static final String CONFIG_BATCH_SIZE = "cms.sla.breach_sweep_batch_size";

    /**
     * Caps one tick's work. The sweep shares an unconfigured scheduler pool with 48 other
     * {@code @Scheduled} methods and the WebSocket broker, and one existing job is known to hold a
     * thread for 18 minutes — so this one must not become the second. 500 notification rows is well
     * inside a single tick, and anything left over is picked up on the next one because the marker
     * means progress is never lost.
     */
    static final int FALLBACK_BATCH_SIZE = 500;

    private final ComplaintRepository complaintRepository;
    private final RbioStatusVocabulary statusVocabulary;
    private final SystemConfigRepository systemConfigRepository;
    private final NotificationService notificationService;

    /**
     * Escalates every open, past-deadline complaint that has not already been escalated for breach.
     * Returns the number escalated on this tick.
     *
     * <p>Once-only is enforced in two layers, and both are needed:
     * <ul>
     *   <li>the SELECT excludes rows whose marker is set, so repeated sweeps do not re-find a
     *       complaint already dealt with — this is what makes a second tick a no-op; and</li>
     *   <li>the marker is then written by a CONDITIONAL UPDATE whose affected-row count decides
     *       whether to notify, so two replicas ticking at the same instant cannot both notify about
     *       the same row. Without this second layer the first would still permit a double-send,
     *       because both replicas can SELECT a row before either writes.</li>
     * </ul>
     */
    @Transactional
    public int sweepBreaches() {
        if (!booleanConfig(CONFIG_ENABLED, true)) {
            log.debug("SLA breach sweep disabled by {}", CONFIG_ENABLED);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        int batchSize = intConfig(CONFIG_BATCH_SIZE).orElse(FALLBACK_BATCH_SIZE);

        // The canonical vocabulary, never a hardcoded list. Lowercase, as COMPLAINTS stores it; the
        // query lowercases the column so Oracle agrees with MySQL.
        List<String> closedStatuses = statusVocabulary.closedStatuses();
        if (closedStatuses.isEmpty()) {
            // An empty "closed" vocabulary would mean nothing counts as closed, so EVERY complaint
            // past its deadline would escalate — including long-settled ones. Refuse rather than
            // notify thousands of officers about decided cases.
            log.error("Closed-status vocabulary resolved empty — skipping SLA breach sweep rather "
                    + "than treating closed complaints as open");
            return 0;
        }

        List<Complaint> breached = complaintRepository.findSlaBreachCandidates(
                now, closedStatuses, PageRequest.of(0, batchSize));

        int escalated = 0;
        for (Complaint complaint : breached) {
            if (complaint.getSlaDeadline() == null) {
                // Unreachable through findSlaBreachCandidates, which filters NULL deadlines out.
                // Kept as belt-and-braces because notifyOwner() dereferences the deadline to report
                // how late the complaint is: if a later edit ever loosened that predicate, the sweep
                // would NPE on the first deadline-less row and the whole tick would be lost — taking
                // the genuine breaches in the same batch down with it. Skipping is also the right
                // answer on the merits: no deadline is an unknown, not a breach.
                log.warn("Complaint {} reached the breach sweep with no SLA deadline — skipping",
                        complaint.getComplaintNumber());
                continue;
            }

            // Compare-and-swap: only the caller that actually flips the marker gets to notify.
            if (complaintRepository.claimSlaBreachEscalation(complaint.getId(), now) == 0) {
                log.debug("Complaint {} was escalated concurrently — not notifying twice",
                        complaint.getComplaintNumber());
                continue;
            }
            notifyOwner(complaint, now);
            escalated++;
        }

        if (escalated > 0) {
            log.info("SLA breach sweep escalated {} complaint(s) of {} candidate(s)",
                    escalated, breached.size());
        }
        if (breached.size() == batchSize) {
            log.warn("SLA breach sweep hit its batch cap of {} — more breaches remain and will be "
                    + "escalated on the next tick", batchSize);
        }
        return escalated;
    }

    /**
     * Tells the officer who owns the complaint. Mirrors {@link ReActivitySweepService}'s escalate():
     * an in-app notification through {@link NotificationService}, which also fans out to the
     * configured channels, with no Kafka involved.
     *
     * <p>An unassigned complaint is logged loudly instead. Breaching with nobody to tell is itself a
     * finding, and silently dropping it would make the sweep's count disagree with the number of
     * officers who heard anything. The marker is still stamped by the caller, so the row does not
     * come back every 15 minutes to log the same warning forever.
     */
    private void notifyOwner(Complaint complaint, LocalDateTime now) {
        String owner = complaint.getAssignedOfficer();
        if (owner == null || owner.isBlank()) {
            log.warn("Complaint {} breached its SLA deadline ({}) with no assigned officer to "
                            + "escalate to", complaint.getComplaintNumber(), complaint.getSlaDeadline());
            return;
        }

        long daysLate = java.time.Duration.between(complaint.getSlaDeadline(), now).toDays();
        notificationService.send(owner, "SLA_BREACH_ESCALATION",
                "SLA deadline missed",
                "Complaint " + complaint.getComplaintNumber() + " passed its SLA deadline on "
                        + complaint.getSlaDeadline().toLocalDate() + " (" + daysLate
                        + " day(s) ago) and is still open at status " + complaint.getStatus() + ".",
                complaint.getComplaintNumber(), "COMPLAINT",
                "/complaint/" + complaint.getComplaintNumber());
    }

    private boolean booleanConfig(String key, boolean fallback) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .map(value -> Boolean.parseBoolean(value.trim()))
                .orElse(fallback);
    }

    private Optional<Integer> intConfig(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .flatMap(value -> {
                    try {
                        return Optional.of(Integer.parseInt(value.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("Config {} is not a number: {} — ignoring", key, value);
                        return Optional.empty();
                    }
                });
    }
}
