package com.hrms.cms.service.report;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supplies the Nodal-Officer-record drill-down behind a report row (UST620-623).
 *
 * <h2>Scoping is the security requirement, not a nicety</h2>
 * UST622 requires the drill-down to be filtered by the SAME role and territory rules as the parent
 * report and to be unreachable by any other path. Both halves matter: a drill-down that applies its own
 * (or no) scope is a way to read a complaint the parent report would have withheld, and because it takes
 * a complaint number directly it is trivially enumerable. So this re-resolves the caller's
 * {@link ReportScope} rather than trusting that the parent report already checked — the caller reaches
 * this endpoint independently, and "the previous request was authorised" is not a fact about this one.
 *
 * <h2>A known contradiction between the story and the schema, surfaced not smoothed</h2>
 * {@code NODAL_OFFICER_RECORDS} carries a UNIQUE constraint on {@code complaint_number} (uk_no_complaint,
 * added by V81), so there is at most ONE nodal-officer record per complaint. The story frames the
 * drill-down as expanding a COUNT, which implies many. A list is returned regardless, because that is the
 * client's declared contract and because dropping a uniqueness constraint to make a UI shape true would
 * be the wrong direction — but against the current schema that list holds 0 or 1 rows. Flagged rather
 * than papered over.
 *
 * <h2>One of the six requested columns has no backing column</h2>
 * The client asks for {@code nodalOfficeName}. No such column exists. The nearest is
 * {@code processingOffice}, which is (a) deliberately nullable and frequently null, and (b) semantically
 * the RBI OMBUDSMAN office that processed the complaint, not the regulated entity's nodal office. It is
 * mapped through because leaving the cell permanently blank would be worse, but the mismatch is real and
 * needs a product decision.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportDrillDownService {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;
    private final ComplaintRepository complaintRepository;
    private final ReportAccessService reportAccessService;

    /**
     * The nodal-officer records for one complaint, scoped to the caller.
     *
     * @param complaintNumber the complaint the user clicked
     * @throws ReportAccessDeniedException when the caller may not see reports, or may not see THIS
     *                                     complaint's department
     */
    public List<Map<String, Object>> noRecordsFor(String complaintNumber) {
        if (complaintNumber == null || complaintNumber.isBlank()) {
            throw new IllegalArgumentException("A complaint number is required for the drill-down.");
        }

        ReportScope scope = reportAccessService.resolveScope(null);
        if (!scope.canView()) {
            throw new ReportAccessDeniedException(
                    "You do not have access to reports, so this drill-down is unavailable.");
        }

        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber).orElse(null);
        if (complaint == null) {
            // Same response as an out-of-scope complaint, below: an empty list. Distinguishing "no such
            // complaint" from "not yours" would let a caller enumerate which complaint numbers exist.
            log.debug("Drill-down requested for unknown complaint {}", complaintNumber);
            return List.of();
        }

        if (!isWithinScope(scope, complaint)) {
            log.warn("Drill-down denied for '{}' on complaint {}: outside the caller's territory",
                    scope.username(), complaintNumber);
            throw new ReportAccessDeniedException(
                    "This complaint is outside the territory your role may report on.");
        }

        List<NodalOfficerRecord> records =
                nodalOfficerRecordRepository.findByComplaintNumber(complaintNumber);

        return records.stream().map(this::toRow).toList();
    }

    /**
     * Applies the parent report's territory rule to a single complaint.
     *
     * <p>Mirrors {@code QueryCompiler.buildAuthScope} deliberately: the same three outcomes, in the same
     * order, so the drill-down cannot admit a complaint the parent report would have excluded. Two copies
     * of an authorisation decision is how they come to disagree, so if either changes, both must.
     */
    private boolean isWithinScope(ReportScope scope, Complaint complaint) {
        if (scope.matchesNothing()) {
            return false;
        }
        if (scope.isUnrestricted()) {
            return true;
        }
        return scope.departmentScope() != null
                && scope.departmentScope().equalsIgnoreCase(complaint.getDepartment());
    }

    /**
     * Maps to the six keys the client's {@code NoRecordDrillDownRow} interface declares, all as strings
     * (including the id, which the interface types as {@code string}).
     */
    private Map<String, Object> toRow(NodalOfficerRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entityName", record.getEntityName());
        row.put("noRecordId", record.getId() == null ? null : String.valueOf(record.getId()));
        row.put("noRecordCreatedOn", format(record.getCreatedAt()));
        // See the class comment: processingOffice is the RBI Ombudsman office, which is not strictly the
        // "Nodal Office Name" the column header promises. No column for the latter exists.
        row.put("nodalOfficeName", record.getProcessingOffice());
        row.put("noRecordStatus", record.getStatus());
        row.put("noRecordLastModifiedOn", format(record.getLastModifiedAt()));
        return row;
    }

    private String format(java.time.LocalDateTime value) {
        return value == null ? null : value.format(DISPLAY);
    }

    /**
     * The count the report row displays, so the drill-down link has something to hang off.
     *
     * <p>Without this the popup was literally unreachable: the client attaches the link to a column whose
     * name contains both "norecord" and "count", and no such column was ever emitted.
     */
    public long noRecordCountFor(String complaintNumber) {
        return nodalOfficerRecordRepository.findByComplaintNumber(complaintNumber).size();
    }
}
