package com.hrms.cms.service.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.service.mre.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntakeTriageService {

    private final MaintainabilityRulesEngine mre;
    private final ComplaintRepository complaintRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public MreVerdict triageOnRegistration(Complaint complaint) {
        ComplaintFacts facts = buildFactsFromComplaint(complaint);
        MreVerdict verdict = mre.evaluate(facts);

        complaint.setTriageSignal(verdict.getTriageSignal());
        complaint.setTriageFlags(serializeFlags(verdict));
        complaint.setEligibilityTimeline(serializeTimeline(verdict));

        complaintRepository.save(complaint);

        log.info("Triage completed for complaint {}: signal={}, failedGrounds={}",
                complaint.getComplaintNumber(), verdict.getTriageSignal(), verdict.getFailedGrounds().size());

        return verdict;
    }

    public MreVerdict evaluateWithoutPersisting(Complaint complaint) {
        ComplaintFacts facts = buildFactsFromComplaint(complaint);
        return mre.evaluate(facts);
    }

    private ComplaintFacts buildFactsFromComplaint(Complaint complaint) {
        boolean hasDuplicate = checkForDuplicateGrievance(complaint);

        return ComplaintFacts.builder()
                .entityCode(complaint.getEntityCode() != null ? complaint.getEntityCode() : "")
                .entityType(null)
                .categoryCode(complaint.getCategoryId() != null ? String.valueOf(complaint.getCategoryId()) : "")
                .priorReComplaint(Boolean.TRUE.equals(complaint.getPriorReComplaint()))
                .reComplaintDate(complaint.getReComplaintDate())
                .reComplaintReference(complaint.getReComplaintReference())
                .reRepliedAndDissatisfied(Boolean.TRUE.equals(complaint.getReRepliedAndDissatisfied()))
                .filingDate(complaint.getFiledAt() != null ? complaint.getFiledAt().toLocalDate() : LocalDate.now())
                .sameGrievancePendingOrDecided(hasDuplicate)
                .reLastCommunicationDate(null)
                .build();
    }

    private boolean checkForDuplicateGrievance(Complaint complaint) {
        if (complaint.getComplainantEmail() == null || complaint.getComplainantEmail().isBlank()) {
            return false;
        }
        List<Complaint> existing = complaintRepository.findByComplainantEmailOrderByCreatedAtDesc(
                complaint.getComplainantEmail());
        return existing.stream()
                .filter(c -> !c.getId().equals(complaint.getId()))
                .filter(c -> "pending".equals(c.getStatus()) || "in_progress".equals(c.getStatus()))
                .anyMatch(c -> c.getBankId() != null && c.getBankId().equals(complaint.getBankId())
                        && c.getCategoryId() != null && c.getCategoryId().equals(complaint.getCategoryId()));
    }

    private String serializeFlags(MreVerdict verdict) {
        try {
            List<Map<String, String>> flags = verdict.getGroundVerdicts().stream()
                    .filter(g -> g.getStatus() == GroundVerdict.Status.FAIL || g.getStatus() == GroundVerdict.Status.NEEDS_REVIEW)
                    .map(g -> Map.of(
                            "ground", g.getGround().name(),
                            "status", g.getStatus().name(),
                            "clause", g.getClause(),
                            "reason", g.getReason()
                    ))
                    .toList();
            return objectMapper.writeValueAsString(flags);
        } catch (Exception e) {
            log.error("Failed to serialize triage flags", e);
            return "[]";
        }
    }

    private String serializeTimeline(MreVerdict verdict) {
        try {
            return objectMapper.writeValueAsString(verdict.getTimeline());
        } catch (Exception e) {
            log.error("Failed to serialize eligibility timeline", e);
            return "{}";
        }
    }
}
