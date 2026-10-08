package com.hrms.cms.service;

import com.hrms.cms.dto.cepc.CepcLegalCaseRequest;
import com.hrms.cms.entity.CepcLegalCase;
import com.hrms.cms.repository.CepcLegalCaseRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Legal Case panel's read and upsert path: one dossier per complaint, in a table of its own.
 *
 * <p>Read and write for {@code CEPC_LEGAL_CASE} and nothing else. It does not touch the complaint, its
 * status or its workflow stage — recording a legal case is a note, not a transition, and the complaint
 * write path ({@code CepcWorkflowService}, {@code InterOfficeTransferService}) is off limits for CEPC
 * work. The complaint is loaded only to reject a dossier aimed at a complaint number that does not exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CepcLegalCaseService {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CepcLegalCaseRepository legalCaseRepository;
    private final ComplaintRepository complaintRepository;
    private final OfficeCodeMasterRepository officeCodeMasterRepository;

    /** The complaint number is not a known complaint. */
    public static class ComplaintNotFoundException extends RuntimeException {
        public ComplaintNotFoundException(String message) { super(message); }
    }

    /** The region does not match any active RBI regional/branch office. */
    public static class InvalidRegionException extends RuntimeException {
        public InvalidRegionException(String message) { super(message); }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> find(String complaintNumber) {
        return legalCaseRepository.findByComplaintNumber(complaintNumber)
                .map(CepcLegalCaseService::toDto)
                .orElse(null);
    }

    /**
     * Creates or overwrites the one legal-case row for this complaint. Find-or-build rather than separate
     * create/update methods — the panel has a single "Update Details" button, so there is no create-vs-edit
     * distinction for a caller to get wrong.
     */
    @Transactional
    public Map<String, Object> save(String complaintNumber, CepcLegalCaseRequest request, String actor) {
        requireComplaint(complaintNumber);
        requireValidRegion(request.getRegionOfLegalTeam());

        CepcLegalCase legalCase = legalCaseRepository.findByComplaintNumber(complaintNumber)
                .orElseGet(() -> CepcLegalCase.builder()
                        .complaintNumber(complaintNumber)
                        .createdBy(actor)
                        .build());

        legalCase.setCaseNumber(trim(request.getCaseNumber()));
        legalCase.setCourtName(trim(request.getCourtName()));
        legalCase.setPartiesOfCase(trim(request.getPartiesOfCase()));
        legalCase.setRegionOfLegalTeam(trim(request.getRegionOfLegalTeam()));
        legalCase.setRbiFirstRespondent(request.getRbiFirstRespondent());
        legalCase.setAppearanceRequired(request.getAppearanceRequired());
        legalCase.setSubjectMatter(trim(request.getSubjectMatter()));
        legalCase.setAdvocateName(trim(request.getAdvocateName()));
        legalCase.setAssistantLegalAdvisor(trim(request.getAssistantLegalAdvisor()));
        legalCase.setNextHearingDate(request.getNextHearingDate());
        legalCase.setPresentStatus(trim(request.getPresentStatus()));
        legalCase.setActionTakenSoFar(trim(request.getActionTakenSoFar()));
        legalCase.setActionToBeTaken(trim(request.getActionToBeTaken()));
        legalCase.setMonetaryClaimDetails(trim(request.getMonetaryClaimDetails()));
        legalCase.setLastModifiedBy(actor);

        CepcLegalCase saved = legalCaseRepository.save(legalCase);
        log.info("CEPC legal case {} saved for complaint {} by {}", saved.getId(), complaintNumber, actor);
        return toDto(saved);
    }

    private void requireComplaint(String complaintNumber) {
        if (complaintRepository.findByComplaintNumber(complaintNumber).isEmpty()) {
            throw new ComplaintNotFoundException("Complaint " + complaintNumber + " was not found.");
        }
    }

    /** Blank is fine — the field is optional. A non-blank value must name an active RBI regional/branch
     * office, matching what the frontend's dropdown offers, so a tampered request can't sneak in a value
     * the UI would never have produced. */
    private void requireValidRegion(String regionOfLegalTeam) {
        if (regionOfLegalTeam == null || regionOfLegalTeam.isBlank()) {
            return;
        }
        String trimmed = regionOfLegalTeam.strip();
        boolean valid = officeCodeMasterRepository.findByOfficeTypeAndIsActiveTrueOrderByOfficeNameAsc("BO")
                .stream()
                .anyMatch(office -> trimmed.equals(office.getOfficeName()));
        if (!valid) {
            throw new InvalidRegionException("Region of Legal Team Involved must be a valid RBI regional/branch office.");
        }
    }

    private static Map<String, Object> toDto(CepcLegalCase legalCase) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", legalCase.getId());
        out.put("complaintNumber", legalCase.getComplaintNumber());
        out.put("caseNumber", legalCase.getCaseNumber());
        out.put("courtName", legalCase.getCourtName());
        out.put("partiesOfCase", legalCase.getPartiesOfCase());
        out.put("regionOfLegalTeam", legalCase.getRegionOfLegalTeam());
        out.put("rbiFirstRespondent", legalCase.getRbiFirstRespondent());
        out.put("appearanceRequired", legalCase.getAppearanceRequired());
        out.put("subjectMatter", legalCase.getSubjectMatter());
        out.put("advocateName", legalCase.getAdvocateName());
        out.put("assistantLegalAdvisor", legalCase.getAssistantLegalAdvisor());
        out.put("nextHearingDate", legalCase.getNextHearingDate() == null ? null : legalCase.getNextHearingDate().toString());
        out.put("presentStatus", legalCase.getPresentStatus());
        out.put("actionTakenSoFar", legalCase.getActionTakenSoFar());
        out.put("actionToBeTaken", legalCase.getActionToBeTaken());
        out.put("monetaryClaimDetails", legalCase.getMonetaryClaimDetails());
        out.put("createdBy", legalCase.getCreatedBy());
        out.put("lastModifiedBy", legalCase.getLastModifiedBy());
        out.put("createdAt", format(legalCase.getCreatedAt()));
        out.put("lastModifiedAt", format(legalCase.getLastModifiedAt()));
        return out;
    }

    private static String format(LocalDateTime at) {
        return at == null ? null : at.format(DISPLAY);
    }

    /** Empty stays empty rather than becoming {@code ""}, so a cleared field reads as absent. Also strips
     * HTML tag markup — this is a free-form dossier with no other sanitization layer, so this is the one
     * point every string field passes through before it reaches the database. */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String withoutTags = value.replaceAll("<[^>]*>", "");
        String trimmed = withoutTags.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
