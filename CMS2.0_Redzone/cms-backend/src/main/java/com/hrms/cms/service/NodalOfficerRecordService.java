package com.hrms.cms.service;

import com.hrms.cms.dto.CreateNodalOfficerRecordRequest;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.NodalOfficerRecordStatus;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * UST569-575: Nodal Officer record lifecycle.
 *
 * <p>A NO record is the system's answer to "who at the bank do we write to about this complaint, and do we
 * trust that contact?". Before this service existed, {@code NODAL_OFFICER_RECORDS} was written by exactly
 * one code path — the reassignment executor, which only ever <em>updated</em> a row. Nothing created rows,
 * so the table was permanently empty, which in turn meant the 15/20-day staleness escalations in
 * {@code NotificationScheduledTasks} could never fire for any complaint. The scheduled job looked healthy
 * and logged zero every night.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodalOfficerRecordService {

    private final NodalOfficerRecordRepository recordRepository;
    private final NodalOfficerResolver resolver;
    /**
     * Written directly rather than through {@code ComplaintService.addTimeline}: ComplaintService calls this
     * service from fileComplaint, so depending back on it would be a constructor-injection cycle and the
     * context would refuse to start. The row built here is identical to the one addTimeline builds.
     */
    private final ComplaintTimelineRepository timelineRepository;
    private final NotificationService notificationService;

    private static final String RELATED_ENTITY_TYPE = "NO_RECORD";

    /**
     * UST569: ensure a NO record exists for a freshly registered complaint.
     *
     * <p>Called synchronously from the complaint-creation paths, inside their transaction, matching the
     * house pattern of saving the parent then the timeline child. Deliberately <em>not</em> async: if this
     * failed silently on a background thread the complaint would exist with no NO record and no trace of
     * why, which is exactly the invisible-gap failure this story exists to close.
     *
     * <p>Returns the existing record untouched when one is already present. Re-deriving contacts on every
     * call would overwrite a Dealing Officer's manual correction with whatever the master data says, and
     * bump {@code lastModifiedAt} — which would reset the staleness clock and permanently suppress the
     * escalation the record is supposed to trigger.
     *
     * @param processingOffice may be null; see {@link NodalOfficerRecord#getProcessingOffice()}
     */
    @Transactional
    public NodalOfficerRecord ensureRecordExists(Long complaintId,
                                                 String complaintNumber,
                                                 String entityName,
                                                 String processingOffice) {
        if (complaintNumber == null || complaintNumber.isBlank()) {
            // Nothing to key the record on. A record with a blank complaint number could never be found
            // again by the officer working the complaint, so skipping is better than inserting an orphan.
            log.warn("Cannot create NO record: complaintNumber is blank (complaintId={})", complaintId);
            return null;
        }

        Optional<NodalOfficerRecord> existing = recordRepository.findFirstByComplaintNumber(complaintNumber);
        if (existing.isPresent()) {
            log.debug("NO record already exists for complaint {} — leaving it untouched", complaintNumber);
            return existing.get();
        }

        NodalOfficerResolver.Resolution resolution = resolver.resolve(entityName, processingOffice);

        // UST570/UST574 vs. "copy the contacts we already hold": both, deliberately.
        //
        // REGULATED_ENTITIES and the new (entity, office) mapping already carry NO/PNO contacts, so leaving
        // the new record blank would throw away usable data and force an officer to look it up by hand.
        // But the status still starts at INFORMATION_REQUIRED, because the status does not describe whether
        // fields are populated — it describes whether *the entity has confirmed these contacts are current
        // for this complaint*. Master data can be years stale. Marking a copied-from-master record
        // CONFIRMED would assert a confirmation that never happened and would exclude the record from the
        // staleness sweep, so nobody would ever ask the entity to verify it.
        NodalOfficerRecord record = NodalOfficerRecord.builder()
                .complaintNumber(complaintNumber)
                .entityName(entityName)
                .processingOffice(processingOffice)
                .nodalOfficerName(resolution.getNodalOfficerName())
                .pnoName(resolution.getPnoName())
                .designation(resolution.getDesignation())
                .email(resolution.getEmail())
                .phone(resolution.getPhone())
                .assignedTo(resolution.getAssignedTo())
                .status(NodalOfficerRecordStatus.INFORMATION_REQUIRED)
                .build();

        NodalOfficerRecord saved;
        try {
            saved = recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // The unique key on complaintNumber fired, so a concurrent registration won the race. That is
            // success for this method's contract: the caller asked for a record to exist, and one does.
            // Rethrowing would fail an otherwise valid complaint registration over a duplicate we did not
            // want anyway.
            log.info("Concurrent NO record creation for complaint {} — using the row that won", complaintNumber);
            return recordRepository.findFirstByComplaintNumber(complaintNumber).orElseThrow(() -> e);
        }

        String reason = describeResolution(resolution);

        // UST569: log against BOTH the complaint and the NO record. The complaint timeline is the officer's
        // audit trail; the record's own assignedTo/status is what the RE-facing screens read.
        recordHistory(complaintId, "no_record_created",
                "System",
                "Nodal Officer record auto-created for " + safeEntity(entityName) + ". " + reason,
                resolution);

        notifyRecordCreated(saved, resolution);

        log.info("UST569: auto-created NO record {} for complaint {} (entity='{}', office='{}', source={})",
                saved.getId(), complaintNumber, entityName, processingOffice, resolution.getSource());

        return saved;
    }

    /**
     * UST572-575: a Dealing Officer adds a NO record by hand.
     *
     * <p>Mandatory-field enforcement lives here rather than only on the request DTO so that it holds for
     * every caller, not just the ones that remember {@code @Valid}. The Angular form marks the same five
     * fields required, but a form check is a convenience, not a control — anything holding a session
     * cookie can POST past it.
     */
    @Transactional
    public NodalOfficerRecord addRecord(CreateNodalOfficerRecordRequest request, String performedBy) {
        List<String> missing = new ArrayList<>();
        requireField(missing, "entityName", request.getEntityName());
        requireField(missing, "nodalOfficerName", request.getNodalOfficerName());
        requireField(missing, "designation", request.getDesignation());
        requireField(missing, "email", request.getEmail());
        requireField(missing, "phone", request.getPhone());

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "The following fields are mandatory for a Nodal Officer record: " + String.join(", ", missing));
        }

        String complaintNumber = trimToNull(request.getComplaintNumber());
        if (complaintNumber == null) {
            throw new IllegalArgumentException(
                    "complaintNumber is mandatory: a Nodal Officer record is always held against a complaint");
        }

        if (recordRepository.existsByComplaintNumber(complaintNumber)) {
            // Surfaced as a clear conflict rather than letting the unique key throw a raw SQL error, which
            // the global handler would turn into an opaque 500 for the officer.
            throw new IllegalArgumentException(
                    "A Nodal Officer record already exists for complaint " + complaintNumber
                            + ". Edit the existing record instead of adding a second one.");
        }

        NodalOfficerRecord record = NodalOfficerRecord.builder()
                .complaintNumber(complaintNumber)
                .entityName(trimToNull(request.getEntityName()))
                .processingOffice(trimToNull(request.getProcessingOffice()))
                .nodalOfficerName(trimToNull(request.getNodalOfficerName()))
                .pnoName(trimToNull(request.getPnoName()))
                .designation(trimToNull(request.getDesignation()))
                .email(trimToNull(request.getEmail()))
                .phone(trimToNull(request.getPhone()))
                .assignedTo(trimToNull(request.getEmail()))
                // UST574: even a hand-entered record starts unconfirmed. The DO is recording what they were
                // told, which is not the same as the entity having confirmed it.
                .status(NodalOfficerRecordStatus.INFORMATION_REQUIRED)
                .build();

        NodalOfficerRecord saved;
        try {
            saved = recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                    "A Nodal Officer record already exists for complaint " + complaintNumber, e);
        }

        recordHistory(request.getComplaintId(), "no_record_added",
                performedBy != null ? performedBy : "System",
                "Nodal Officer record added manually for " + safeEntity(request.getEntityName())
                        + " (status " + NodalOfficerRecordStatus.INFORMATION_REQUIRED + ")",
                null);

        notifyRecordCreated(saved, null);

        log.info("UST572-575: NO record {} added manually for complaint {} by {}",
                saved.getId(), complaintNumber, performedBy);

        return saved;
    }

    /**
     * Fires the standard NO-record notifications.
     *
     * <p>Recipients are role tokens, not usernames: an individual officer may be on leave the day a
     * complaint is registered, and a notification addressed to them alone would go unread with nothing to
     * escalate it. When the resolver fell through to an Ombudsman Admin (UST571) the admin is notified too,
     * because that case needs a human to go and get the entity's contacts.
     */
    private void notifyRecordCreated(NodalOfficerRecord record, NodalOfficerResolver.Resolution resolution) {
        String actionUrl = "/complaint/" + record.getComplaintNumber();
        String entity = safeEntity(record.getEntityName());

        notificationService.send(
                "RBIO_SUPERVISOR",
                "NO_RECORD_CREATED",
                "Nodal Officer record awaiting information",
                "Nodal Officer record for complaint " + record.getComplaintNumber() + " (entity: " + entity
                        + ") has been created with status " + NodalOfficerRecordStatus.INFORMATION_REQUIRED + ".",
                record.getComplaintNumber(),
                RELATED_ENTITY_TYPE,
                actionUrl
        );

        if (record.getAssignedTo() != null && !record.getAssignedTo().isBlank()) {
            notificationService.send(
                    record.getAssignedTo(),
                    "NO_RECORD_ASSIGNED",
                    "Nodal Officer record assigned to you",
                    "You are recorded as the contact for complaint " + record.getComplaintNumber()
                            + " (entity: " + entity + "). Please confirm your details.",
                    record.getComplaintNumber(),
                    RELATED_ENTITY_TYPE,
                    actionUrl
            );
        }

        if (resolution != null && resolution.isFallbackToAdmin()) {
            notificationService.send(
                    "RBIO_ADMIN",
                    "NO_RECORD_NO_CONTACT",
                    "No Nodal Officer on record for entity",
                    "Complaint " + record.getComplaintNumber() + " is against " + entity
                            + ", which has no Nodal Officer or Principal Nodal Officer on record. "
                            + "It has been routed to the Ombudsman Admin for the office.",
                    record.getComplaintNumber(),
                    RELATED_ENTITY_TYPE,
                    actionUrl
            );
        }
    }

    /**
     * UST569/UST575: the complaint-side history entry.
     *
     * <p>COMPLAINT_TIMELINE has no column for the resolution source or the office, so both are folded into
     * the remarks text rather than dropped — an officer reading the history needs to know a regional admin
     * was chosen because the entity had no contacts, not just that "a record was created".
     *
     * <p>Callers that reach here with a null complaintId (creation paths where the complaint row was never
     * given an id) get a warning instead of an NPE part-way through registering a complaint.
     */
    private void recordHistory(Long complaintId, String action, String performedBy, String remarks,
                               NodalOfficerResolver.Resolution resolution) {
        if (complaintId == null) {
            log.warn("Skipping NO record timeline entry: no complaintId supplied for action {}", action);
            return;
        }
        String detail = remarks;
        if (resolution != null && resolution.getProcessingOffice() != null) {
            detail = detail + " Processing office: " + resolution.getProcessingOffice() + ".";
        }
        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaintId)
                .action(action)
                .performedBy(performedBy)
                .remarks(detail)
                .toStatus(NodalOfficerRecordStatus.INFORMATION_REQUIRED)
                .build());
    }

    private String describeResolution(NodalOfficerResolver.Resolution resolution) {
        return switch (resolution.getSource()) {
            case ENTITY_OFFICE -> "Contacts resolved from the entity/office mapping.";
            case ENTITY_DEFAULT -> "Contacts resolved from the entity's default (all-office) mapping.";
            case REGULATED_ENTITY -> "Contacts copied from the Regulated Entity master.";
            case PNO_FALLBACK -> "No Nodal Officer on record; Principal Nodal Officer used instead.";
            case OMBUDSMAN_ADMIN ->
                    "No Nodal Officer or Principal Nodal Officer on record; routed to Ombudsman Admin "
                            + resolution.getAssignedTo() + ".";
        };
    }

    private void requireField(List<String> missing, String fieldName, String value) {
        if (trimToNull(value) == null) {
            missing.add(fieldName);
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeEntity(String entityName) {
        return entityName == null || entityName.isBlank() ? "unknown entity" : entityName;
    }
}
