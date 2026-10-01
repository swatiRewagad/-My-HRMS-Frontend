package com.rbi.cms.search.reindex;

import com.rbi.cms.search.index.ComplaintDocument;
import com.rbi.cms.search.index.LanguageDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@Component
@RequiredArgsConstructor
public class ComplaintRowMapper {

    private final LanguageDetector languageDetector;

    public ComplaintDocument map(ResultSet rs) throws SQLException {
        ComplaintDocument doc = new ComplaintDocument();

        doc.setComplaintId(String.valueOf(rs.getLong("id")));
        doc.setComplaintNumber(rs.getString("complaint_number"));

        doc.setSubject(rs.getString("subject"));
        doc.setDescription(rs.getString("description"));
        doc.setAdvisoryText(rs.getString("advisory_text"));
        doc.setWithdrawalReason(rs.getString("withdrawal_reason"));
        doc.setSchemeCoverageReason(rs.getString("scheme_coverage_reason"));
        doc.setReopenJustification(rs.getString("reopen_justification"));

        doc.setClosureClause(rs.getString("closure_clause"));
        doc.setReopenReason(rs.getString("reopen_reason"));

        doc.setCategoryId(nullableLong(rs, "category_id"));
        doc.setEntityCode(rs.getString("entity_code"));
        doc.setDepartment(rs.getString("department"));
        doc.setStatus(rs.getString("status"));
        doc.setWorkflowStage(rs.getString("workflow_stage"));
        doc.setMilestone(rs.getString("milestone"));
        doc.setRbioOfficeCode(rs.getString("rbio_office_code"));
        doc.setGroundOfComplaintId(nullableLong(rs, "ground_of_complaint_id"));
        doc.setCompensationType(rs.getString("compensation_type"));
        doc.setMaintainabilityDetermination(rs.getString("maintainability_determination"));
        doc.setPriority(rs.getString("priority"));
        doc.setAssignedOfficer(rs.getString("assigned_officer"));
        doc.setAssignedRole(rs.getString("assigned_role"));

        double award = rs.getDouble("award_amount");
        doc.setAwardAmount(rs.wasNull() ? null : award);

        doc.setCreatedAt(isoOrNull(rs.getTimestamp("created_at")));
        doc.setUpdatedAt(isoOrNull(rs.getTimestamp("updated_at")));

        // Detected once, at index time, and stored. Detecting per request would run a script-range
        // scan over the query on every single search for no gain: the analyzed subfields are all
        // queried regardless.
        doc.setDetectedLanguage(languageDetector.detect(doc.getSubject(), doc.getDescription()));

        return doc;
    }

    public ComplaintDocument.TimelineEntry mapTimeline(ResultSet rs) throws SQLException {
        ComplaintDocument.TimelineEntry entry = new ComplaintDocument.TimelineEntry();
        entry.setAction(rs.getString("action"));
        entry.setFromStatus(rs.getString("from_status"));
        entry.setToStatus(rs.getString("to_status"));
        entry.setPerformedByRole(rs.getString("performed_by_role"));
        entry.setPerformedAt(isoOrNull(rs.getTimestamp("performed_at")));
        entry.setRemarks(rs.getString("remarks"));
        return entry;
    }

    public ComplaintDocument.AppealOrderEntry mapAppealOrder(ResultSet rs) throws SQLException {
        ComplaintDocument.AppealOrderEntry entry = new ComplaintDocument.AppealOrderEntry();
        entry.setAppealNumber(rs.getString("appeal_number"));
        entry.setClauseCode(rs.getString("clause_code"));
        entry.setOutcome(rs.getString("outcome"));
        entry.setOrderSummary(rs.getString("order_summary"));
        entry.setGround(rs.getString("ground"));
        entry.setCorrectionReason(rs.getString("correction_reason"));
        return entry;
    }

    private String nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : String.valueOf(value);
    }

    private String isoOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }
}
