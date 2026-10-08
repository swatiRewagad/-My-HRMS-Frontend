package com.hrms.cms.service;

import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.CepcComplaintAssessment;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.EntityOfficeNodalOfficer;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.CepcComplaintAssessmentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.EntityOfficeNodalOfficerRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Contact Entity tab: the nodal-officer worklist and the action that forwards a record to the entity.
 *
 * <p>The list is assembled from five rows per record — the record itself, its complaint, the complaint's
 * bank, the officer's assessment of the complaint, and the entity's contact roster — because only the
 * contact fields live on the record and the screen shows the complaint alongside them. All five are loaded
 * in batches keyed by the page of records, so the query count does not grow with the row count.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CepcNodalRecordService {

    /**
     * The most records the worklist will return.
     *
     * <p>The client asks for this list with no paging parameters at all and filters what it gets in the
     * browser, so an unbounded query is one registration drive away from returning the whole table into a
     * single response. Capped rather than paged because adding paging the client does not use would leave
     * it silently showing only the first page with no way to reach the rest.
     */
    private static final int MAX_ROWS = 500;

    /**
     * Compensation ceilings from the Scheme, in rupees.
     *
     * <p>Enforced here because the form has no ceiling of its own and the officer's figure becomes an award:
     * a mis-typed extra digit on mental agony is a twenty-fold error that nothing downstream would question.
     */
    private static final BigDecimal MAX_COMPENSATION_LOSS = new BigDecimal("2000000");
    private static final BigDecimal MAX_COMPENSATION_MENTAL = new BigDecimal("100000");

    /**
     * The four status codes the Assessment panel offers, as radio values.
     *
     * <p>Closed rather than free text: an unrecognised code would be stored and then shown back as a blank
     * radio group, so the officer would see an assessment with no status and no way to tell which of the
     * four had been saved.
     */
    private static final String STATUS_INFORMATION_REQUIRED = "INFORMATION_REQUIRED";
    private static final String STATUS_ADVISORY_ISSUED = "ADVISORY_ISSUED";
    private static final String STATUS_AWARD_PASS = "AWARD_PASS";
    private static final String STATUS_NOTICE_13_1 = "13_1_NOTICE";

    private static final Set<String> STATUS_CODES = Set.of(
            STATUS_INFORMATION_REQUIRED, STATUS_ADVISORY_ISSUED, STATUS_AWARD_PASS, STATUS_NOTICE_13_1);

    /**
     * The statuses that are a communication TO the entity, and so put the complaint in its court.
     *
     * <p>{@code AWARD_PASS} is excluded deliberately. It records what this office decided after the entity
     * had its say; pushing the complaint back to the entity at that point would put an adjudicated case back
     * on the entity's pending list and restart a response clock nobody is waiting on.
     */
    private static final Set<String> COMMUNICATES_TO_ENTITY =
            Set.of(STATUS_INFORMATION_REQUIRED, STATUS_ADVISORY_ISSUED, STATUS_NOTICE_13_1);

    /** Clause 13(1) gives the entity fifteen days to comply. */
    private static final int NOTICE_131_DAYS = 15;

    /** The 13(1) date is display-only on the screen, so it is sent already formatted. */
    private static final DateTimeFormatter DISPLAY_DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final NodalOfficerRecordRepository recordRepository;
    private final ComplaintRepository complaintRepository;
    private final CepcComplaintAssessmentRepository assessmentRepository;
    private final BankRepository bankRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final EntityOfficeNodalOfficerRepository rosterRepository;
    private final CepcWorkflowService workflowService;

    public static class RecordNotFoundException extends RuntimeException {
        public RecordNotFoundException(String message) {
            super(message);
        }
    }

    public static class NotEditableException extends RuntimeException {
        public NotEditableException(String message) {
            super(message);
        }
    }

    /** Thrown for an assessment the Scheme does not allow, so the officer sees the reason on the form. */
    public static class InvalidAssessmentException extends RuntimeException {
        public InvalidAssessmentException(String message) {
            super(message);
        }
    }

    /** Thrown when the record changed under the caller between read and save. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<NodalOfficerRecord> records = recordRepository.findAll(
                PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        if (records.isEmpty()) {
            return List.of();
        }

        Context context = loadContext(records);
        List<Map<String, Object>> rows = new ArrayList<>(records.size());
        for (NodalOfficerRecord record : records) {
            rows.add(toDto(record, context));
        }
        return rows;
    }

    /**
     * Saves the officer's assessment and sends the record to the entity.
     *
     * <p>The status change is applied through {@link CepcWorkflowService}'s {@code FORWARD_TO_RE} arm rather
     * than by writing {@code assignedRole} here, because that arm is what the dashboard's "Sent to RE",
     * "Pending with RE" and "Response from RE" tabs select on. A local write would save the assessment and
     * leave the complaint invisible to all three — the record would look forwarded on this tab and forwarded
     * nowhere else.
     */
    @Transactional
    public Map<String, Object> forwardToRe(String recordNumber, Map<String, Object> payload,
                                           String callerUserId, boolean admin) {
        NodalOfficerRecord record = recordRepository.findByRecordNumber(recordNumber)
                .orElseThrow(() -> new RecordNotFoundException(
                        "No nodal officer record found for " + recordNumber + "."));

        Complaint complaint = complaintRepository.findByComplaintNumber(record.getComplaintNumber())
                .orElseThrow(() -> new RecordNotFoundException(
                        "The complaint behind record " + recordNumber + " is missing."));
        if (!mayForward(complaint, record, callerUserId, admin)) {
            throw new NotEditableException("This complaint is not open for you to edit.");
        }

        String status = requireStatus(payload.get("status"));
        BigDecimal loss = amount(payload.get("compensationLoss"), "compensation for loss");
        BigDecimal mental = amount(payload.get("compensationMental"), "compensation for mental agony");
        if (loss != null && loss.compareTo(MAX_COMPENSATION_LOSS) > 0) {
            throw new InvalidAssessmentException(
                    "Compensation for loss may not exceed Rs. 20,00,000 under the Scheme.");
        }
        if (mental != null && mental.compareTo(MAX_COMPENSATION_MENTAL) > 0) {
            throw new InvalidAssessmentException(
                    "Compensation for mental agony may not exceed Rs. 1,00,000 under the Scheme.");
        }

        LocalDate advisoryDate = date(payload.get("advisoryComplianceDate"), "advisory compliance date");
        if (STATUS_ADVISORY_ISSUED.equals(status) && advisoryDate == null) {
            // The only required field on the panel. An advisory with no date to comply by cannot be chased,
            // and the staleness sweep has nothing to measure it against, so it would sit there forever.
            throw new InvalidAssessmentException(
                    "A date by which the advisory must be complied with is required.");
        }

        record.setStatus(status);
        record.setAdvisoryComplianceDate(advisoryDate);
        record.setDisputeAmount(amount(payload.get("disputeAmount"), "disputed amount"));
        record.setCompensationLoss(loss);
        record.setCompensationMental(mental);
        record.setAwardImplementationDate(date(payload.get("awardImplementationDate"),
                "award implementation date"));
        record.setAwardAcceptanceDate(date(payload.get("awardAcceptanceDate"), "award acceptance date"));
        record.setAssignedTo(callerUserId);

        // Set once, and only by the notice that actually creates the obligation. Re-issuing or moving on to
        // another status must not shift a date the entity has already been told to comply by.
        if (STATUS_NOTICE_13_1.equals(status) && record.getNotice131ComplyDate() == null) {
            record.setNotice131ComplyDate(LocalDate.now().plusDays(NOTICE_131_DAYS));
        }

        if (COMMUNICATES_TO_ENTITY.contains(status)) {
            if (record.getForwardedToReAt() == null) {
                record.setForwardedToReAt(LocalDateTime.now());
            }
            advanceComplaintToRe(complaint, record, status, callerUserId);
        }
        recordRepository.save(record);

        return toDto(record, loadContext(List.of(record)));
    }

    /**
     * Saves the DO's edits to the fields the Contact Entity tab's Summary panel owns on the record
     * itself — module, ATM flag, offices, and the NO/PNO contact snapshot.
     *
     * <p>{@code assignedTo} is deliberately not among the keys this accepts. That column already has its
     * own audited reassignment path elsewhere; a second, unaudited writer here would race it and leave no
     * trail of who changed the owner.
     */
    @Transactional
    public Map<String, Object> updateContactFields(String recordNumber, Map<String, Object> payload,
                                                    String callerUserId, boolean admin) {
        NodalOfficerRecord record = recordRepository.findByRecordNumber(recordNumber)
                .orElseThrow(() -> new RecordNotFoundException(
                        "No nodal officer record found for " + recordNumber + "."));
        Complaint complaint = complaintRepository.findByComplaintNumber(record.getComplaintNumber())
                .orElseThrow(() -> new RecordNotFoundException(
                        "The complaint behind record " + recordNumber + " is missing."));
        if (!CepcEditPolicy.canEdit(complaint, callerUserId, admin)) {
            throw new NotEditableException("This complaint is not open for you to edit.");
        }

        str(payload, "moduleName", record::setModuleName);
        str(payload, "atmComplaint", record::setAtmComplaint);
        str(payload, "designatedOffice", record::setDesignatedOffice);
        str(payload, "processingOffice", record::setProcessingOffice);
        str(payload, "nodalOfficerName", record::setNodalOfficerName);
        str(payload, "phone", record::setPhone);
        str(payload, "email", record::setEmail);
        str(payload, "pnoName", record::setPnoName);
        str(payload, "pnoMobile", record::setPnoMobile);
        str(payload, "pnoEmail", record::setPnoEmail);

        try {
            recordRepository.saveAndFlush(record);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("This record was changed by someone else. Reload and try again.");
        }

        return toDto(record, loadContext(List.of(record)));
    }

    /** Sets the field only when the caller actually sent the key, so a partial payload cannot blank the rest. */
    private static void str(Map<String, Object> block, String key, java.util.function.Consumer<String> setter) {
        if (block.containsKey(key)) {
            setter.accept(text(block.get(key)));
        }
    }

    /**
     * Runs the shared FORWARD_TO_RE transition and mirrors the deadline it chose back onto the record.
     *
     * <p>The deadline is mirrored rather than computed twice because the staleness escalations read the
     * record while the dashboard reads the complaint; two independent calculations of the same window would
     * eventually disagree and the officer would be shown one date while the entity was chased against another.
     */
    private void advanceComplaintToRe(Complaint complaint, NodalOfficerRecord record,
                                      String status, String callerUserId) {
        Map<String, String> params = new HashMap<>();
        params.put("actor", callerUserId == null ? "system" : callerUserId);
        params.put("communication", communicationFor(status));
        params.put("remarks", "Forwarded to the regulated entity: " + communicationFor(status));
        if (STATUS_NOTICE_13_1.equals(status) && record.getNotice131ComplyDate() != null) {
            params.put("responseDeadline", record.getNotice131ComplyDate().toString());
        } else if (STATUS_ADVISORY_ISSUED.equals(status) && record.getAdvisoryComplianceDate() != null) {
            params.put("responseDeadline", record.getAdvisoryComplianceDate().toString());
        }

        try {
            workflowService.performAction(complaint.getComplaintNumber(), "FORWARD_TO_RE", params);
        } catch (ResponseStatusException e) {
            // The deadline service refuses a weekend or holiday, and its reason is the one thing that tells
            // the officer what to do about it. Rethrown as an assessment failure so it travels in the same
            // envelope as the other refusals — otherwise this single case arrives in a different shape, with
            // an internal message key in front of the sentence the form displays.
            throw new InvalidAssessmentException(humanReason(e));
        }

        // performAction saved the complaint itself, so this instance is behind; re-read the one field the
        // record has to agree with rather than trusting a stale copy.
        complaintRepository.findByComplaintNumber(complaint.getComplaintNumber()).ifPresent(fresh -> {
            if (fresh.getReResponseDeadline() != null) {
                record.setReResponseDeadline(fresh.getReResponseDeadline());
                record.setDeadlineCommunication(communicationFor(status));
            }
        });
    }

    /** The sentence without the {@code some.message.key:} prefix the workflow layer puts in front of it. */
    private static String humanReason(ResponseStatusException e) {
        String reason = e.getReason();
        if (reason == null || reason.isBlank()) {
            return "The regulated entity could not be notified of this assessment.";
        }
        int split = reason.indexOf(": ");
        String key = split < 0 ? "" : reason.substring(0, split);
        return key.contains(".") && !key.contains(" ") ? reason.substring(split + 2) : reason;
    }

    private static String communicationFor(String status) {
        return switch (status) {
            case STATUS_NOTICE_13_1 -> "13(1) Notice";
            case STATUS_ADVISORY_ISSUED -> "Advisory";
            default -> "Information Request";
        };
    }

    /**
     * Whether this caller may take the record further.
     *
     * <p>Falls back to the record's own officer because the forward transition replaces the complaint's
     * assigned officer with the entity's code: after the first forward the CEPC officer working the entity
     * side matches nothing on the complaint, and the ordinary ownership test would lock them out of the very
     * record they just sent. Closure still binds — a closed complaint is refused either way.
     */
    private static boolean mayForward(Complaint complaint, NodalOfficerRecord record,
                                      String callerUserId, boolean admin) {
        if (CepcEditPolicy.canEdit(complaint, callerUserId, admin)) {
            return true;
        }
        if (CepcEditPolicy.isTerminal(complaint)) {
            return false;
        }
        String holder = record.getAssignedTo();
        return holder != null && !holder.isBlank()
                && callerUserId != null && holder.trim().equalsIgnoreCase(callerUserId.trim());
    }

    private static String requireStatus(Object raw) {
        String status = text(raw);
        if (status == null) {
            throw new InvalidAssessmentException("A status code is required to forward this record.");
        }
        String upper = status.toUpperCase();
        if (!STATUS_CODES.contains(upper)) {
            throw new InvalidAssessmentException("Unknown status code: " + status);
        }
        return upper;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Assembly
    // ════════════════════════════════════════════════════════════════════════

    /** Everything the rows join to, loaded in batches rather than per row. */
    private record Context(Map<String, Complaint> complaints,
                           Map<String, CepcComplaintAssessment> assessments,
                           Map<Long, Bank> banks,
                           Map<String, RegulatedEntity> entities,
                           Map<String, EntityOfficeNodalOfficer> roster) {}

    private Context loadContext(List<NodalOfficerRecord> records) {
        Set<String> numbers = new HashSet<>();
        for (NodalOfficerRecord record : records) {
            if (record.getComplaintNumber() != null) {
                numbers.add(record.getComplaintNumber());
            }
        }

        Map<String, Complaint> complaints = new HashMap<>();
        if (!numbers.isEmpty()) {
            for (Complaint complaint : complaintRepository.findByComplaintNumberIn(numbers)) {
                complaints.put(complaint.getComplaintNumber(), complaint);
            }
        }

        Map<String, CepcComplaintAssessment> assessments = new HashMap<>();
        if (!numbers.isEmpty()) {
            for (CepcComplaintAssessment a : assessmentRepository.findByComplaintNumberIn(numbers)) {
                assessments.put(a.getComplaintNumber(), a);
            }
        }

        Set<Long> bankIds = new HashSet<>();
        for (Complaint complaint : complaints.values()) {
            if (complaint.getBankId() != null) {
                bankIds.add(complaint.getBankId());
            }
        }
        Map<Long, Bank> banks = new HashMap<>();
        if (!bankIds.isEmpty()) {
            for (Bank bank : bankRepository.findAllById(bankIds)) {
                banks.put(bank.getId(), bank);
            }
        }

        // The entity master and the office roster are keyed on a normalised name, so they are resolved from
        // whichever name the record or the complaint carries rather than by id — no record holds an id.
        Map<String, RegulatedEntity> entities = new HashMap<>();
        Map<String, EntityOfficeNodalOfficer> roster = new HashMap<>();
        for (NodalOfficerRecord record : records) {
            String name = firstNonBlank(record.getEntityName(),
                    bankName(complaints.get(record.getComplaintNumber()), banks));
            if (name == null) {
                continue;
            }
            String normalized = RegulatedEntity.normalize(name);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!entities.containsKey(normalized)) {
                regulatedEntityRepository.findByNameNormalized(normalized)
                        .ifPresent(entity -> entities.put(normalized, entity));
            }
            String key = rosterKey(normalized, record.getProcessingOffice());
            if (!roster.containsKey(key)) {
                rosterFor(normalized, record.getProcessingOffice())
                        .ifPresent(row -> roster.put(key, row));
            }
        }

        return new Context(complaints, assessments, banks, entities, roster);
    }

    /**
     * The roster row for this entity at this office, falling back to the entity's default row.
     *
     * <p>The office-specific row comes first because that is the point of the (entity, office) mapping: a
     * bank names a different nodal officer to each Ombudsman office, and answering with the default would
     * address the notice to an officer who does not handle this office's complaints.
     */
    private Optional<EntityOfficeNodalOfficer> rosterFor(String normalized, String processingOffice) {
        if (processingOffice != null && !processingOffice.isBlank()) {
            Optional<EntityOfficeNodalOfficer> scoped = rosterRepository
                    .findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(
                            normalized, processingOffice);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return rosterRepository
                .findFirstByEntityNameNormalizedAndProcessingOfficeIsNullAndActiveTrue(normalized);
    }

    private static String rosterKey(String normalized, String processingOffice) {
        return normalized + '|' + (processingOffice == null ? "" : processingOffice);
    }

    private Map<String, Object> toDto(NodalOfficerRecord r, Context ctx) {
        Complaint c = ctx.complaints().get(r.getComplaintNumber());
        CepcComplaintAssessment a = ctx.assessments().get(r.getComplaintNumber());
        Bank bank = c == null || c.getBankId() == null ? null : ctx.banks().get(c.getBankId());

        String entityName = firstNonBlank(r.getEntityName(), bank == null ? null : bank.getName());
        String normalized = entityName == null ? "" : RegulatedEntity.normalize(entityName);
        RegulatedEntity entity = ctx.entities().get(normalized);
        EntityOfficeNodalOfficer rosterRow =
                ctx.roster().get(rosterKey(normalized, r.getProcessingOffice()));

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", r.getId());
        dto.put("recordNumber", r.getRecordNumber());
        dto.put("complaintNumber", r.getComplaintNumber());
        dto.put("status", r.getStatus());
        dto.put("assignedTo", blankIfNull(r.getAssignedTo()));
        dto.put("slaDays", slaDays(r));
        dto.put("receiptDate", c == null || c.getFiledAt() == null ? ""
                : c.getFiledAt().toLocalDate().toString());
        dto.put("subject", blankIfNull(c == null ? null : c.getSubject()));
        dto.put("complainant", blankIfNull(c == null ? null : c.getComplainantName()));
        dto.put("mobile", blankIfNull(c == null ? null : c.getComplainantPhone()));
        dto.put("email", blankIfNull(c == null ? null : c.getComplainantEmail()));

        dto.put("bankName", blankIfNull(entityName));
        dto.put("bankCategory", blankIfNull(firstNonBlank(
                a == null ? null : a.getEntityCategory(),
                entity == null ? null : entity.getEntityType(),
                bank == null ? null : bank.getType())));
        dto.put("branchCategory", blankIfNull(a == null ? null : a.getEntityBranchCategory()));
        dto.put("branchName", blankIfNull(firstNonBlank(
                a == null ? null : a.getEntityBranchName(), c == null ? null : c.getBankBranch())));

        // The BRANCH's location, never the complainant's. The complaint carries a rep_* address for the
        // person who filed, and showing that under a column headed by the bank would read as the branch
        // being wherever the complainant happens to live.
        dto.put("pincode", blankIfNull(a == null ? null : a.getEntityPincode()));
        dto.put("city", blankIfNull(firstNonBlank(
                a == null ? null : a.getEntityCity(), entity == null ? null : entity.getCity())));
        dto.put("district", blankIfNull(a == null ? null : a.getEntityDistrict()));
        dto.put("state", blankIfNull(firstNonBlank(
                a == null ? null : a.getEntityState(), entity == null ? null : entity.getState())));
        dto.put("country", blankIfNull(a == null ? null : a.getEntityCountry()));
        dto.put("moduleName", blankIfNull(firstNonBlank(
                r.getModuleName(), a == null ? null : a.getModuleName())));
        dto.put("atmComplaint", atmComplaint(r, a));
        dto.put("designatedOffice", blankIfNull(r.getDesignatedOffice()));
        dto.put("processingOffice", blankIfNull(r.getProcessingOffice()));

        // The record's own snapshot wins over the roster and the master. It is what the contacts were when
        // this complaint was sent to the entity, and a later change to the roster must not rewrite the
        // history of who was actually written to.
        dto.put("noName", blankIfNull(firstNonBlank(r.getNodalOfficerName(),
                rosterRow == null ? null : rosterRow.getNodalOfficerName(),
                entity == null ? null : entity.getNodalOfficerName())));
        dto.put("noMobile", blankIfNull(firstNonBlank(r.getPhone(),
                rosterRow == null ? null : rosterRow.getNodalOfficerPhone(),
                entity == null ? null : entity.getNodalOfficerPhone())));
        dto.put("noEmail", blankIfNull(firstNonBlank(r.getEmail(),
                rosterRow == null ? null : rosterRow.getNodalOfficerEmail(),
                entity == null ? null : entity.getNodalOfficerEmail())));
        dto.put("noDesignation", blankIfNull(firstNonBlank(r.getDesignation(),
                rosterRow == null ? null : rosterRow.getNodalOfficerDesignation(),
                entity == null ? null : entity.getNodalOfficerDesignation())));
        dto.put("pnoName", blankIfNull(firstNonBlank(r.getPnoName(),
                rosterRow == null ? null : rosterRow.getPnoName(),
                entity == null ? null : entity.getPnoName())));
        dto.put("pnoMobile", blankIfNull(firstNonBlank(r.getPnoMobile(),
                rosterRow == null ? null : rosterRow.getPnoPhone(),
                entity == null ? null : entity.getPnoPhone())));
        dto.put("pnoEmail", blankIfNull(firstNonBlank(r.getPnoEmail(),
                rosterRow == null ? null : rosterRow.getPnoEmail(),
                entity == null ? null : entity.getPnoEmail())));

        dto.put("advisoryComplianceDate", isoOrNull(r.getAdvisoryComplianceDate()));
        dto.put("disputeAmount", r.getDisputeAmount());
        dto.put("compensationLoss", r.getCompensationLoss());
        dto.put("compensationMental", r.getCompensationMental());
        dto.put("awardImplementationDate", isoOrNull(r.getAwardImplementationDate()));
        dto.put("awardAcceptanceDate", isoOrNull(r.getAwardAcceptanceDate()));
        dto.put("notice131ComplyDate", r.getNotice131ComplyDate() == null ? null
                : r.getNotice131ComplyDate().format(DISPLAY_DAY));
        dto.put("forwardedToReAt", r.getForwardedToReAt() == null ? null
                : r.getForwardedToReAt().toString());
        return dto;
    }

    /**
     * How long the entity has had this record.
     *
     * <p>Counted from the forward rather than from creation, and null before it: a record nobody has sent
     * anywhere has not been waiting on the entity for any number of days, and reporting its age as if it had
     * would make the whole worklist look overdue on the day it was registered.
     */
    private static Integer slaDays(NodalOfficerRecord record) {
        if (record.getSlaDays() != null) {
            return record.getSlaDays();
        }
        if (record.getForwardedToReAt() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(record.getForwardedToReAt().toLocalDate(), LocalDate.now());
    }

    /** Stored if an officer has said so, otherwise inferred from the assessment's own ATM flag. */
    private static String atmComplaint(NodalOfficerRecord record, CepcComplaintAssessment assessment) {
        if (record.getAtmComplaint() != null && !record.getAtmComplaint().isBlank()) {
            return record.getAtmComplaint();
        }
        if (assessment == null || assessment.getAtmCreditDebitCard() == null) {
            return "";
        }
        return Boolean.TRUE.equals(assessment.getAtmCreditDebitCard()) ? "Yes" : "No";
    }

    private static String bankName(Complaint complaint, Map<Long, Bank> banks) {
        if (complaint == null || complaint.getBankId() == null) {
            return null;
        }
        Bank bank = banks.get(complaint.getBankId());
        return bank == null ? null : bank.getName();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Parsing
    // ════════════════════════════════════════════════════════════════════════

    private static String text(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * An amount, refused rather than dropped when it cannot be read.
     *
     * <p>Unlike a date on the Summary form, a silently ignored amount here is an award figure the officer
     * believes they entered and the entity never sees.
     */
    private static BigDecimal amount(Object raw, String field) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.replace(",", ""));
            if (parsed.signum() < 0) {
                throw new InvalidAssessmentException("The " + field + " cannot be negative.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new InvalidAssessmentException("The " + field + " could not be read: " + value);
        }
    }

    private static LocalDate date(Object raw, String field) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        String candidate = value.length() > 10 && (value.charAt(10) == 'T' || value.charAt(10) == ' ')
                ? value.substring(0, 10) : value;
        try {
            return LocalDate.parse(candidate);
        } catch (Exception e) {
            throw new InvalidAssessmentException("The " + field + " could not be read: " + value);
        }
    }

    private static String isoOrNull(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /**
     * Empty string rather than null for the descriptive columns.
     *
     * <p>The client declares them as plain strings and pipes several straight into a case-insensitive
     * filter, so a null arrives as the text "null" in the search index and matches on the letter n.
     */
    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
