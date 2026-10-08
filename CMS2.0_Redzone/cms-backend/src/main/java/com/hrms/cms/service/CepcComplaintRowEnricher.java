package com.hrms.cms.service;

import com.hrms.cms.dto.cepc.CepcComplaintRow;
import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.CepcContactPerson;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.CepcContactPersonRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintReadStateRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns {@link Complaint} entities into the grid rows the CEPC dashboard renders.
 *
 * <p>Four of the nineteen columns — entity name, category, nodal officer (used only for the entity-name
 * fallback below) and contact person — live in other tables, and {@code Complaint} declares no JPA
 * relationships at all. Every lookup here is therefore a <b>batch</b> over the whole page: four queries per
 * page rather than four per row. A per-row lookup would be 800 queries on a 200-row page, which is the
 * difference between a dashboard and a timeout.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CepcComplaintRowEnricher {

    private final BankRepository bankRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;
    private final CepcContactPersonRepository contactPersonRepository;
    private final ComplaintReadStateRepository readStateRepository;

    public List<CepcComplaintRow> enrich(List<Complaint> complaints, String callerUserId) {
        if (complaints.isEmpty()) {
            return List.of();
        }

        Map<Long, String> bankNames = batchBankNames(complaints);
        Map<Long, String> categoryNames = batchCategoryNames(complaints);
        Map<String, NodalOfficerRecord> nodalRecords = batchNodalRecords(complaints);
        Map<String, CepcContactPerson> contactPersons = batchContactPersons(complaints);
        Set<Long> readIds = batchReadIds(complaints, callerUserId);

        LocalDateTime now = LocalDateTime.now();
        return complaints.stream()
                .map(c -> toRow(c, bankNames, categoryNames, nodalRecords, contactPersons, readIds, now))
                .toList();
    }

    private CepcComplaintRow toRow(Complaint c, Map<Long, String> bankNames,
                                   Map<Long, String> categoryNames,
                                   Map<String, NodalOfficerRecord> nodalRecords,
                                   Map<String, CepcContactPerson> contactPersons,
                                   Set<Long> readIds, LocalDateTime now) {
        NodalOfficerRecord nor = c.getComplaintNumber() == null
                ? null : nodalRecords.get(c.getComplaintNumber());
        CepcContactPerson cp = c.getComplaintNumber() == null
                ? null : contactPersons.get(c.getComplaintNumber());

        return new CepcComplaintRow(
                c.getId(),
                c.getComplaintNumber(),
                c.getAssignedOfficer(),
                slaBreachIn(c.getSlaDeadline(), now),
                c.getFilingType(),
                c.getComplainantName(),
                c.getStatus(),
                entityName(c, bankNames, nor),
                c.getCategoryId() == null ? null : categoryNames.get(c.getCategoryId()),
                iso(c.getCreatedAt()),
                iso(c.getUpdatedAt()),
                priority(c.getPriority()),
                c.getSubject(),
                cp == null ? null : cp.getName(),
                complaintColor(c.getStatus(), c.getFiledAt(), now),
                readIds.contains(c.getId()),
                slaColor(c.getSlaDeadline(), now),
                statusColor(c.getStatus()),
                CepcStatus.label(c.getStatus(), c.getWorkflowStage()),
                CepcStatus.labelKey(c.getStatus(), c.getWorkflowStage()));
    }

    /**
     * Entity name, preferring the master record over the free-text column.
     *
     * <p>{@code COMPLAINTS.ENTITY_CODE} holds entity NAMES rather than codes, inconsistently — "HDFC Bank"
     * on some rows, "PNB" on others, null on many. So the bank master is tried first, then the nodal record's
     * own entity name, and the raw column is the last resort rather than the first.
     */
    private String entityName(Complaint c, Map<Long, String> bankNames, NodalOfficerRecord nor) {
        if (c.getBankId() != null) {
            String fromMaster = bankNames.get(c.getBankId());
            if (fromMaster != null) {
                return fromMaster;
            }
        }
        if (nor != null && nor.getEntityName() != null && !nor.getEntityName().isBlank()) {
            return nor.getEntityName();
        }
        return c.getEntityCode();
    }

    // ─────────────────────────────── batch lookups ───────────────────────────────

    private Map<Long, String> batchBankNames(List<Complaint> complaints) {
        Set<Long> ids = complaints.stream().map(Complaint::getBankId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bankRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Bank::getId, Bank::getName, (a, b) -> a));
    }

    private Map<Long, String> batchCategoryNames(List<Complaint> complaints) {
        Set<Long> ids = complaints.stream().map(Complaint::getCategoryId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ComplaintCategory::getId, ComplaintCategory::getName, (a, b) -> a));
    }

    /**
     * Nodal officer records keyed by complaint number.
     *
     * <p>Keyed on the number rather than an id because {@code NodalOfficerRecord} has no FK to
     * {@code Complaint}; it carries the complaint number as a loose string. A complaint can have more than
     * one record, and the newest wins — an older record names the officer the complaint has since moved
     * away from.
     */
    private Map<String, NodalOfficerRecord> batchNodalRecords(List<Complaint> complaints) {
        List<String> numbers = complaints.stream().map(Complaint::getComplaintNumber)
                .filter(n -> n != null && !n.isBlank()).distinct().toList();
        if (numbers.isEmpty()) {
            return Map.of();
        }
        Map<String, NodalOfficerRecord> out = new HashMap<>();
        for (NodalOfficerRecord r : nodalOfficerRecordRepository.findByComplaintNumberIn(numbers)) {
            out.merge(r.getComplaintNumber(), r, (existing, candidate) ->
                    newer(candidate.getLastModifiedAt(), existing.getLastModifiedAt()) ? candidate : existing);
        }
        return out;
    }

    private static boolean newer(LocalDateTime candidate, LocalDateTime existing) {
        if (candidate == null) {
            return false;
        }
        return existing == null || candidate.isAfter(existing);
    }

    /**
     * The grid's Contact Person column, keyed by complaint number.
     *
     * <p>A complaint can carry several contact persons — unlike the nodal officer, there is no
     * {@code uk_no_complaint}-style constraint here — so the most recently added one wins, as the person the
     * dealing officer is currently in touch with rather than whoever was logged first.
     */
    private Map<String, CepcContactPerson> batchContactPersons(List<Complaint> complaints) {
        List<String> numbers = complaints.stream().map(Complaint::getComplaintNumber)
                .filter(n -> n != null && !n.isBlank()).distinct().toList();
        if (numbers.isEmpty()) {
            return Map.of();
        }
        Map<String, CepcContactPerson> out = new HashMap<>();
        for (CepcContactPerson cp : contactPersonRepository.findByComplaintNumberIn(numbers)) {
            out.merge(cp.getComplaintNumber(), cp, (existing, candidate) ->
                    newer(candidate.getCreatedAt(), existing.getCreatedAt()) ? candidate : existing);
        }
        return out;
    }

    private Set<Long> batchReadIds(List<Complaint> complaints, String callerUserId) {
        if (callerUserId == null || callerUserId.isBlank()) {
            // An unidentified caller has read nothing. Reporting everything as read would hide the unread
            // highlight on a whole page of genuinely new work.
            return Set.of();
        }
        List<Long> ids = complaints.stream().map(Complaint::getId)
                .filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(readStateRepository.findReadComplaintIds(callerUserId, ids));
    }

    // ─────────────────────────────── display values ───────────────────────────────

    /**
     * Time to the SLA deadline, as a phrase.
     *
     * <p>Composed here rather than in the browser so that one clock decides it. The grid sorts this column
     * on {@code slaDeadline}, and a client-side calculation against a differently-skewed clock would order
     * rows inconsistently with the labels shown on them.
     */
    static String slaBreachIn(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) {
            return null;
        }
        long days = Duration.between(now, deadline).toDays();
        if (days < 0) {
            long overdue = Math.abs(days);
            return overdue == 1 ? "Overdue by 1 day" : "Overdue by " + overdue + " days";
        }
        if (days == 0) {
            return "Due today";
        }
        return days == 1 ? "1 day" : days + " days";
    }

    /**
     * The SLA cell's colour.
     *
     * <p><b>A bare CSS colour word, never a class name.</b> The template interpolates this straight into
     * {@code color-mix(in srgb, …)}; handed a class name, {@code color-mix} fails to parse and the cell
     * renders fully transparent — so the most urgent complaints become the invisible ones.
     */
    static String slaColor(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) {
            return null;
        }
        long days = Duration.between(now, deadline).toDays();
        if (days < 0) {
            return "red";
        }
        return days <= 7 ? "orange" : "green";
    }

    /**
     * The row tint's colour, from the complaint's status.
     *
     * <p><b>A bare colour word, never a class name.</b> The grid appends it to {@code cc-row-}, and the only
     * tints the stylesheet defines are red, yellow, green, pink and blue — anything else lands on a selector
     * that styles nothing.
     *
     * <p><b>Null means no tint, and most rows get none.</b> Only the five conditions below are meant to stand
     * out; a fallback colour here would tint the whole grid again and leave the colour carrying no signal,
     * which is what it did while this was driven by priority.
     *
     * <p>Both spellings of each status are matched — the write path persists the legacy lowercase token while
     * the seed writes the canonical name (see {@link CepcStatus}), and matching only one leaves live rows
     * untinted.
     *
     * <p>Blue is the one condition that is not a status alone: a complaint still sitting in New Complaint more
     * than three days after it was filed. There is no assigned-date column to compare against — CEPC never
     * writes {@code stageAssignedAt} — so the age is measured from {@code filedAt}, which is the question the
     * colour is really asking: has anyone picked this up yet.
     */
    static String complaintColor(String status, LocalDateTime filedAt, LocalDateTime now) {
        if (status == null) {
            return null;
        }
        return switch (status.strip().toLowerCase(Locale.ROOT)) {
            case "information_required", "info_requested" -> "red";
            case "sent_to_rbi" -> "green";
            case "sent_back" -> "yellow";
            case "complaint_withdrawn", "withdrawn" -> "pink";
            case "new_complaint", "pending" -> filedAt != null
                    && Duration.between(filedAt, now).toDays() > 3 ? "blue" : null;
            default -> null;
        };
    }

    /**
     * CSS class for the status chip, applied verbatim.
     *
     * <p>Each value is a rule the dashboard's stylesheet defines; a name it does not know renders as an
     * unstyled chip, so this switch and {@code .custom-status-badge} have to be changed together.
     *
     * <p>Never null. The old search service omitted this key entirely, so the chip rendered unstyled on every
     * row; an unrecognised status gets a neutral class rather than nothing.
     */
    static String statusColor(String status) {
        if (status == null) {
            return "status-neutral";
        }
        // Keyed on the canonical names CEPC now writes. The legacy spellings that used to sit alongside them
        // (pending, info_requested, reviewer_review, incharge_review, awaiting_closure, closed, withdrawn)
        // were removed once CepcWorkflowService stopped writing them — see CepcStatus. The statuses that
        // replaced them must keep an arm here, or the chip silently falls through to an unstyled neutral one.
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "new_complaint" -> "status-new-complaint";
            case "assigned" -> "status-assigned";
            case "in_progress" -> "status-in-progress";
            case "information_required" -> "status-info-required";
            case "sent_to_reviewer" -> "status-under-review";
            case "pending_office_head_approval", "sent_to_incharge" -> "status-office-head";
            case "complaint_settled", "resolved" -> "status-settled";
            case "escalated" -> "status-escalated";
            case "sent_back" -> "status-back-deo";
            case "forwarded", "forwarded_external", "forwarded_to_contact",
                 "sent_to_other", "sent_to_other_departments",
                 "sent_to_other_regulated_bodies", "sent_to_other_office" -> "status-sent-office";
            case "complaint_closed" -> "status-closed";
            case "complaint_withdrawn" -> "status-withdrawn";
            case "draft" -> "status-draft";
            case "rejected", "complaint_rejected" -> "status-rejected";
            // Written as a workflow stage rather than a status by CEPC, but present in the shared status
            // master, so a row carrying one still gets its designed chip.
            case "ombudsman_decision", "deputy_ombudsman_decision" -> "status-ombudsman";
            case "meeting_scheduled" -> "status-meeting";
            case "complaint_reopen" -> "status-reopen";
            default -> "status-neutral";
        };
    }

    private static String priority(String raw) {
        return raw == null ? "MEDIUM" : raw.toUpperCase(Locale.ROOT);
    }

    private static String iso(LocalDateTime t) {
        return t == null ? null : t.toString();
    }

}
