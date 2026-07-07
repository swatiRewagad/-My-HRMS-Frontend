package com.hrms.cms.service.copilot;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.SimilarCasesService;
import com.hrms.cms.service.mre.*;
import com.hrms.cms.service.triage.CompensationPrecedentService;
import com.hrms.cms.service.triage.IntakeTriageService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintainabilityCopilotService {

    private final MaintainabilityRulesEngine mre;
    private final SimilarCasesService similarCasesService;
    private final ComplaintService complaintService;
    private final IntakeTriageService triageService;
    private final CompensationPrecedentService compensationService;

    @Cacheable(value = "copilot-precedent", key = "'copilot-' + #complaintId")
    public CopilotResponse generateSuggestion(Long complaintId) {
        Complaint complaint = complaintService.getComplaint(complaintId);

        MreVerdict verdict = triageService.evaluateWithoutPersisting(complaint);

        List<Map<String, Object>> precedents = findPrecedentCases(complaint);

        String suggestedDetermination = deriveSuggestion(verdict, precedents);
        String draftRationale = buildDraftRationale(complaint, verdict, precedents, suggestedDetermination);

        CompensationPrecedentService.CompensationBand compensationBand =
                compensationService.getCompensationBand(complaint);

        return CopilotResponse.builder()
                .complaintId(complaintId)
                .complaintNumber(complaint.getComplaintNumber())
                .mreVerdict(verdict)
                .precedentCases(precedents)
                .suggestedDetermination(suggestedDetermination)
                .draftRationale(draftRationale)
                .compensationBand(compensationBand)
                .generatedAt(java.time.LocalDateTime.now())
                .build();
    }

    private List<Map<String, Object>> findPrecedentCases(Complaint complaint) {
        String searchText = (complaint.getSubject() != null ? complaint.getSubject() : "") + " " +
                (complaint.getDescription() != null ? complaint.getDescription() : "");

        String category = complaint.getCategoryId() != null ? String.valueOf(complaint.getCategoryId()) : null;

        List<Map<String, Object>> similar = similarCasesService.findSimilar(searchText.trim(), category, 5);

        return similar.stream().map(s -> {
            Map<String, Object> enriched = new LinkedHashMap<>(s);
            enriched.putIfAbsent("outcome", "UNKNOWN");
            enriched.putIfAbsent("ground", "—");
            enriched.putIfAbsent("closureAction", "—");
            return enriched;
        }).collect(Collectors.toList());
    }

    private String deriveSuggestion(MreVerdict verdict, List<Map<String, Object>> precedents) {
        if (verdict.getOverallSignal() == MreVerdict.OverallSignal.OBJECTIVELY_NON_MAINTAINABLE) {
            return "NON_MAINTAINABLE";
        }
        if (verdict.getOverallSignal() == MreVerdict.OverallSignal.OBJECTIVELY_CLEAR) {
            long closedAsMaintainable = precedents.stream()
                    .filter(p -> "CLOSED".equals(p.get("status")) || "SETTLED".equals(p.get("outcome")))
                    .count();
            if (closedAsMaintainable > precedents.size() / 2) {
                return "LIKELY_MAINTAINABLE";
            }
            return "MAINTAINABLE";
        }
        return "NEEDS_REVIEW";
    }

    private String buildDraftRationale(Complaint complaint, MreVerdict verdict,
                                       List<Map<String, Object>> precedents, String suggestion) {
        StringBuilder rationale = new StringBuilder();

        rationale.append("MAINTAINABILITY ASSESSMENT\n");
        rationale.append("Complaint: ").append(complaint.getComplaintNumber()).append("\n");
        rationale.append("Date of Assessment: ").append(LocalDate.now()).append("\n\n");

        rationale.append("1. OBJECTIVE GROUNDS (MRE v").append(verdict.getRuleVersion()).append(")\n");
        for (GroundVerdict g : verdict.getGroundVerdicts()) {
            if (g.getStatus() == GroundVerdict.Status.NOT_APPLICABLE) continue;
            String icon = switch (g.getStatus()) {
                case PASS -> "[PASS]";
                case FAIL -> "[FAIL]";
                case NEEDS_REVIEW -> "[REVIEW]";
                default -> "[—]";
            };
            rationale.append("   ").append(icon).append(" ")
                    .append(g.getGround().getDescription())
                    .append(" (").append(g.getClause()).append(")")
                    .append(" — ").append(g.getReason()).append("\n");
        }

        rationale.append("\n2. PRECEDENT ANALYSIS\n");
        if (precedents.isEmpty()) {
            rationale.append("   No closely matching precedent cases found.\n");
        } else {
            rationale.append("   ").append(precedents.size()).append(" similar cases found:\n");
            for (int i = 0; i < Math.min(precedents.size(), 3); i++) {
                Map<String, Object> p = precedents.get(i);
                rationale.append("   - ").append(p.getOrDefault("complaintNumber", "—"))
                        .append(": outcome=").append(p.getOrDefault("outcome", "—"))
                        .append(", ground=").append(p.getOrDefault("ground", "—"))
                        .append("\n");
            }
        }

        rationale.append("\n3. SUGGESTED DETERMINATION: ").append(suggestion).append("\n");
        rationale.append("\n4. RATIONALE:\n");
        rationale.append("   Based on the objective analysis under RB-IOS 2026 and precedent from similar cases, ");
        rationale.append("the complaint ").append(
                "NON_MAINTAINABLE".equals(suggestion) ?
                        "does not meet the maintainability criteria on objective grounds." :
                        "appears to meet the basic maintainability criteria on objective grounds."
        );
        rationale.append("\n\n[Officer to review, edit, and confirm this determination]");

        return rationale.toString();
    }

    @Getter
    @Builder
    @NoArgsConstructor(force = true)
    @AllArgsConstructor
    public static class CopilotResponse implements Serializable {
        private final Long complaintId;
        private final String complaintNumber;
        private final MreVerdict mreVerdict;
        private final List<Map<String, Object>> precedentCases;
        private final String suggestedDetermination;
        private final String draftRationale;
        private final CompensationPrecedentService.CompensationBand compensationBand;
        private final java.time.LocalDateTime generatedAt;
    }
}
