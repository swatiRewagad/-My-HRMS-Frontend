package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintRbioFormData;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.dto.NodalAssessmentRequest;
import com.hrms.cms.repository.ComplaintRbioFormDataRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.service.triage.ReResponsivenessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the per-complaint nodal officer record that the RE portal and the staleness reminder
 * scheduler read. Contact details are snapshotted rather than joined so a later change to the
 * regulated entity's nodal officer does not rewrite history on complaints already in flight.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodalOfficerRecordService {

    // dd-MM-yyyy is what every other RBIO screen renders dates as.
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final String ADVISORY_ISSUED = "ADVISORY_ISSUED";
    private static final String NOTICE_131 = "13_1_NOTICE";

    // The nodal officer record's own status vocabulary, which is not the complaint workflow's — these
    // are the four codes the officer screen offers. Kept as an allow-list so an unrecognised code is a
    // 400 rather than a row nothing downstream knows how to read.
    private static final Set<String> ALLOWED_STATUSES =
            Set.of("INFORMATION_REQUIRED", ADVISORY_ISSUED, "AWARD_PASS", NOTICE_131);

    // Clause 13(1) gives the nodal officer 15 days to furnish the information requisitioned.
    private static final int NOTICE_131_COMPLY_DAYS = 15;

    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintRbioFormDataRepository formDataRepository;
    private final ReResponseTrackerRepository trackerRepository;
    private final RbioCompensationService compensationService;
    private final ReResponsivenessService reResponsivenessService;

    @Transactional
    public NodalOfficerRecord createForComplaint(Complaint complaint) {
        String complaintNumber = complaint.getComplaintNumber();
        if (complaintNumber == null || complaintNumber.isBlank()) {
            log.warn("Skipping nodal officer record for complaint id={} — no complaint number", complaint.getId());
            return null;
        }

        List<NodalOfficerRecord> existing = nodalOfficerRecordRepository.findByComplaintNumber(complaintNumber);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        RegulatedEntity entity = resolveEntity(complaint).orElse(null);

        NodalOfficerRecord record = NodalOfficerRecord.builder()
                .complaintNumber(complaintNumber)
                .entityName(entity != null ? entity.getName() : complaint.getEntityName())
                .nodalOfficerName(entity != null ? entity.getNodalOfficerName() : null)
                .pnoName(entity != null ? entity.getPnoName() : null)
                .pnoEmail(entity != null ? entity.getPnoEmail() : null)
                .pnoPhone(entity != null ? entity.getPnoPhone() : null)
                .designation(entity != null ? entity.getNodalOfficerDesignation() : null)
                .email(entity != null ? entity.getNodalOfficerEmail() : null)
                .phone(entity != null ? entity.getNodalOfficerPhone() : null)
                .assignedTo(complaint.getAssignedOfficer())
                .build();

        NodalOfficerRecord saved = nodalOfficerRecordRepository.save(record);
        // Needs the identity id, so it can only be set once the insert has happened. The second save
        // is folded into the same transaction's flush.
        saved.setRecordNumber(String.format("%07d", saved.getId()));
        saved = nodalOfficerRecordRepository.save(saved);
        log.info("Created nodal officer record {} for complaint {} (entity={}, matchedRegulatedEntity={})",
                saved.getRecordNumber(), complaintNumber, saved.getEntityName(), entity != null);
        return saved;
    }

    /**
     * The officer-facing worklist. Only the nodal officer contact details are snapshotted on the
     * record itself; everything else is joined live off the complaint so the worklist reflects edits
     * made on the complaint rather than a copy taken when it was forwarded.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listWorklist() {
        return nodalOfficerRecordRepository.findAllByOrderByLastModifiedAtDesc().stream()
                .map(this::toWorklistRow)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toWorklistRow(NodalOfficerRecord record) {
        Complaint complaint = complaintRepository.findByComplaintNumber(record.getComplaintNumber()).orElse(null);
        ComplaintRbioFormData formData = complaint != null
                ? formDataRepository.findByComplaintId(complaint.getId()).orElse(null)
                : null;

        LocalDate receiptDate = formData != null && formData.getReceiptDate() != null
                ? formData.getReceiptDate()
                : complaint != null && complaint.getCreatedAt() != null
                        ? complaint.getCreatedAt().toLocalDate()
                        : null;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.getId());
        row.put("recordNumber", record.getRecordNumber());
        row.put("complaintNumber", record.getComplaintNumber());
        row.put("status", record.getStatus());
        row.put("assignedTo", record.getAssignedTo());
        // Age of the complaint in the officer's hands, which is what the worklist's breach badge reads.
        row.put("slaDays", receiptDate != null ? ChronoUnit.DAYS.between(receiptDate, LocalDate.now()) : null);
        row.put("receiptDate", receiptDate != null ? receiptDate.format(DISPLAY_DATE) : null);

        row.put("subject", complaint != null ? complaint.getSubject() : null);
        row.put("complainant", complaint != null ? complaint.getComplainantName() : null);
        row.put("mobile", complaint != null ? complaint.getComplainantPhone() : null);
        row.put("email", complaint != null ? complaint.getComplainantEmail() : null);
        row.put("bankName", record.getEntityName());
        row.put("bankCategory", complaint != null ? complaint.getEntityCategory() : null);
        row.put("branchCategory", complaint != null ? complaint.getEntityBranchCategory() : null);
        row.put("branchName", complaint != null ? complaint.getEntityBranchName() : null);
        row.put("pincode", complaint != null ? complaint.getEntityPincode() : null);
        row.put("city", complaint != null ? complaint.getEntityCity() : null);
        row.put("district", complaint != null ? complaint.getEntityDistrict() : null);
        row.put("state", complaint != null ? complaint.getEntityState() : null);
        row.put("designatedOffice", complaint != null ? complaint.getForwardedOfficeCode() : null);
        row.put("processingOffice", complaint != null ? complaint.getRegionalOffice() : null);

        row.put("moduleName", formData != null ? formData.getModuleName() : null);
        row.put("country", formData != null ? formData.getEntityCountry() : null);
        row.put("atmComplaint", formData != null ? formData.getAtmCreditDebitCard() : null);

        row.put("noName", record.getNodalOfficerName());
        row.put("noMobile", record.getPhone());
        row.put("noEmail", record.getEmail());
        row.put("noDesignation", record.getDesignation());
        row.put("pnoName", record.getPnoName());
        row.put("pnoMobile", record.getPnoPhone());
        row.put("pnoEmail", record.getPnoEmail());

        // The assessment, so reopening a record shows what was actually saved. The date inputs on the
        // screen are native <input type="date">, which only accepts ISO, so these stay unformatted
        // while notice131ComplyDate is display-only and follows the rest of the screen.
        row.put("advisoryComplianceDate", toIso(record.getAdvisoryComplianceDate()));
        row.put("disputeAmount", record.getDisputeAmount());
        row.put("compensationLoss", record.getCompensationLoss());
        row.put("compensationMental", record.getCompensationMental());
        row.put("awardImplementationDate", toIso(record.getAwardImplementationDate()));
        row.put("awardAcceptanceDate", toIso(record.getAwardAcceptanceDate()));
        row.put("notice131ComplyDate", record.getNotice131ComplyDate() != null
                ? record.getNotice131ComplyDate().format(DISPLAY_DATE) : null);
        row.put("forwardedToReAt", record.getForwardedToReAt() != null
                ? record.getForwardedToReAt().toString() : null);
        return row;
    }

    private static String toIso(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    /**
     * Persists the officer's assessment and forwards the record to the regulated entity. This is one
     * operation rather than a save and a separate send because the screen offers a single action: the
     * assessment only becomes meaningful once the RE has been told about it.
     */
    @Transactional
    public Map<String, Object> forwardToRegulatedEntity(String recordNumber, NodalAssessmentRequest request,
                                                        String actor) {
        NodalOfficerRecord record = nodalOfficerRecordRepository.findByRecordNumber(recordNumber)
                .orElseThrow(() -> new IllegalArgumentException("No nodal officer record " + recordNumber));

        String status = request.getStatus().trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "Unknown status code '" + status + "'. Valid codes are: " + String.join(", ", ALLOWED_STATUSES));
        }

        validateAssessment(status, request);

        record.setStatus(status);
        record.setAdvisoryComplianceDate(request.getAdvisoryComplianceDate());
        record.setDisputeAmount(request.getDisputeAmount());
        record.setCompensationLoss(request.getCompensationLoss());
        record.setCompensationMental(request.getCompensationMental());
        record.setAwardImplementationDate(request.getAwardImplementationDate());
        record.setAwardAcceptanceDate(request.getAwardAcceptanceDate());
        // Fixed at the moment the notice is issued. Recomputing it on later saves would quietly extend
        // a deadline the nodal officer has already been served with.
        if (NOTICE_131.equals(status) && record.getNotice131ComplyDate() == null) {
            record.setNotice131ComplyDate(LocalDate.now().plusDays(NOTICE_131_COMPLY_DAYS));
        }
        record.setForwardedToReAt(LocalDateTime.now());

        NodalOfficerRecord saved = nodalOfficerRecordRepository.save(record);
        startReResponseWindow(saved);

        log.info("Nodal record {} forwarded to RE with status {} by {}", recordNumber, status, actor);
        return toWorklistRow(saved);
    }

    private void validateAssessment(String status, NodalAssessmentRequest request) {
        // The caps are the Ombudsman Scheme's, so they are read off the service that owns them rather
        // than restated here.
        if (request.getCompensationLoss() != null) {
            compensationService.validateAward(request.getCompensationLoss(), "CONSEQUENTIAL_LOSS");
        }
        if (request.getCompensationMental() != null) {
            compensationService.validateAward(request.getCompensationMental(), "TIME_HARASSMENT");
        }
        if (request.getCompensationLoss() != null && request.getCompensationMental() != null) {
            compensationService.validateAward(
                    request.getCompensationLoss().add(request.getCompensationMental()), "COMBINED");
        }

        if (ADVISORY_ISSUED.equals(status)) {
            if (request.getAdvisoryComplianceDate() == null) {
                throw new IllegalArgumentException(
                        "A compliance date is required when the status is Advisory Issued");
            }
            if (request.getAdvisoryComplianceDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException(
                        "The advisory compliance date must not be in the past");
            }
        }

        // Both award dates record something that has already happened, so a future date is a typo.
        rejectFutureDate(request.getAwardImplementationDate(), "award implementation date");
        rejectFutureDate(request.getAwardAcceptanceDate(), "award acceptance date");
    }

    private void rejectFutureDate(LocalDate date, String label) {
        if (date != null && date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The " + label + " must not be in the future");
        }
    }

    /**
     * RE_RESPONSE_TRACKER holds at most one row per complaint — it is read back through an Optional
     * finder — so a re-send must not open a second window. The first forward is the one the response
     * clock runs from.
     */
    private void startReResponseWindow(NodalOfficerRecord record) {
        Complaint complaint = complaintRepository.findByComplaintNumber(record.getComplaintNumber()).orElse(null);
        if (complaint == null) {
            log.warn("Nodal record {} has no complaint {}, skipping RE response window",
                    record.getRecordNumber(), record.getComplaintNumber());
            return;
        }
        if (trackerRepository.findByComplaintId(complaint.getId()).isPresent()) {
            return;
        }
        reResponsivenessService.trackForwarding(complaint, complaint.getRegulatedEntityId());
    }

    private Optional<RegulatedEntity> resolveEntity(Complaint complaint) {
        if (complaint.getRegulatedEntityId() != null) {
            Optional<RegulatedEntity> byId = regulatedEntityRepository.findById(complaint.getRegulatedEntityId());
            if (byId.isPresent()) {
                return byId;
            }
        }

        String entityName = complaint.getEntityName();
        if (entityName == null || entityName.isBlank()) {
            return Optional.empty();
        }

        String normalized = RegulatedEntity.normalize(entityName);
        Optional<RegulatedEntity> exact = regulatedEntityRepository.findByNameNormalized(normalized);
        if (exact.isPresent()) {
            return exact;
        }

        return regulatedEntityRepository.searchByNormalizedName(normalized).stream().findFirst();
    }
}
