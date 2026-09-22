package com.hrms.cms.service;

import com.hrms.cms.entity.RbioLegalCase;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RbioLegalCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Court proceedings recorded against a complaint (UST553).
 *
 * <p>Replaces a phantom: {@code rbio-legal-case} has always read and written
 * {@code /complaints/{id}/legal-case}, which no controller served. The GET's {@code catchError} yielded
 * null, so the form opened blank every time and a saved case vanished on reload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioLegalCaseService {

    private final RbioLegalCaseRepository legalCaseRepository;
    private final ComplaintRepository complaintRepository;

    /** The complaint's legal case, or null when none is recorded. */
    public Map<String, Object> find(String complaintNumber) {
        return legalCaseRepository.findByComplaintNumber(complaintNumber)
                .map(RbioLegalCaseService::toPayload)
                .orElse(null);
    }

    /** Whether this matter is before a court — the question the statutory sub-judice guard asks. */
    public boolean hasOpenLegalCase(String complaintNumber) {
        return legalCaseRepository.existsByComplaintNumber(complaintNumber);
    }

    /**
     * Creates or replaces the complaint's legal case.
     *
     * <p>One method serves both POST and PUT. The frontend sends POST for a new record and PUT for an
     * edit, but the record is unique per complaint, so treating them differently would mean a POST over an
     * existing case had to either fail or duplicate — and duplicating would break the uniqueness the
     * sub-judice check depends on.
     */
    @Transactional
    public Map<String, Object> save(String complaintNumber, Map<String, Object> request, String actor) {
        if (!complaintRepository.findByComplaintNumber(complaintNumber).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found: " + complaintNumber);
        }

        String caseNumber = trimmed(request.get("caseNumber"));
        String courtName = trimmed(request.get("courtName"));
        requireField("caseNumber", caseNumber);
        requireField("courtName", courtName);

        LocalDate filingDate = parseDate(request.get("filingDate"), "filingDate");
        LocalDate nextHearingDate = parseDate(request.get("nextHearingDate"), "nextHearingDate");

        // A hearing cannot precede the filing that produced it. Refused rather than stored, because a
        // legal-case record with impossible dates is evidence of nothing.
        if (filingDate != null && nextHearingDate != null && nextHearingDate.isBefore(filingDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The next hearing date cannot fall before the filing date");
        }

        LocalDateTime now = LocalDateTime.now();
        RbioLegalCase legalCase = legalCaseRepository.findByComplaintNumber(complaintNumber)
                .orElseGet(() -> RbioLegalCase.builder()
                        .complaintNumber(complaintNumber)
                        .createdBy(actor)
                        .createdAt(now)
                        .build());

        legalCase.setCaseNumber(caseNumber);
        legalCase.setCourtName(courtName);
        legalCase.setCaseStatus(trimmed(request.get("caseStatus")));
        legalCase.setFilingDate(filingDate);
        legalCase.setNextHearingDate(nextHearingDate);
        legalCase.setRemarks(trimmed(request.get("remarks")));
        legalCase.setUpdatedBy(actor);
        legalCase.setUpdatedAt(now);

        return toPayload(legalCaseRepository.save(legalCase));
    }

    /**
     * A blank date is absent, not zero. Returning null for unparseable input would silently drop a date
     * the user typed, so a malformed value is refused instead.
     */
    private static LocalDate parseDate(Object raw, String field) {
        String text = trimmed(raw);
        if (text == null) return null;
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must be a valid date in YYYY-MM-DD format");
        }
    }

    private static void requireField(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }

    private static String trimmed(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** Shaped to the frontend's existing {@code LegalCase} interface (rbio-workflow.service.ts:39-50). */
    private static Map<String, Object> toPayload(RbioLegalCase legalCase) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", String.valueOf(legalCase.getId()));
        payload.put("complaintId", legalCase.getComplaintNumber());
        payload.put("caseNumber", legalCase.getCaseNumber());
        payload.put("courtName", legalCase.getCourtName());
        payload.put("caseStatus", legalCase.getCaseStatus());
        payload.put("filingDate", legalCase.getFilingDate() != null ? legalCase.getFilingDate().toString() : null);
        payload.put("nextHearingDate",
                legalCase.getNextHearingDate() != null ? legalCase.getNextHearingDate().toString() : null);
        payload.put("remarks", legalCase.getRemarks());
        payload.put("updatedBy", legalCase.getUpdatedBy());
        payload.put("updatedAt", legalCase.getUpdatedAt() != null ? legalCase.getUpdatedAt().toString() : null);
        return payload;
    }
}
