package com.hrms.cms.controller;

import com.hrms.cms.service.mre.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mre")
@RequiredArgsConstructor
public class MreController {

    private final MaintainabilityRulesEngine mre;
    private final MreProperties mreProperties;

    @PostMapping("/evaluate")
    public ResponseEntity<MreVerdict> evaluate(@RequestBody Map<String, Object> request) {
        ComplaintFacts facts = ComplaintFacts.builder()
                .entityCode((String) request.getOrDefault("entityCode", ""))
                .entityType((String) request.getOrDefault("entityType", ""))
                .categoryCode((String) request.getOrDefault("categoryCode", ""))
                .priorReComplaint(Boolean.TRUE.equals(request.get("priorReComplaint")))
                .reComplaintDate(parseDate(request.get("reComplaintDate")))
                .reComplaintReference((String) request.getOrDefault("reComplaintReference", ""))
                .reRepliedAndDissatisfied(Boolean.TRUE.equals(request.get("reRepliedAndDissatisfied")))
                .filingDate(parseDate(request.getOrDefault("filingDate", LocalDate.now().toString())))
                .sameGrievancePendingOrDecided(Boolean.TRUE.equals(request.get("sameGrievancePendingOrDecided")))
                .reLastCommunicationDate(parseDate(request.get("reLastCommunicationDate")))
                .build();

        MreVerdict verdict = mre.evaluate(facts);
        return ResponseEntity.ok(verdict);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "version", mreProperties.getVersion(),
                "reWindowDays", mreProperties.getReWindowDays(),
                "npciWindowDays", mreProperties.getNpciWindowDays(),
                "cardNetworkWindowDays", mreProperties.getCardNetworkWindowDays(),
                "filingDeadlineDays", mreProperties.getFilingDeadlineDays(),
                "limitationPeriodYears", mreProperties.getLimitationPeriodYears(),
                "windowBasis", mreProperties.getWindowBasis().name()
        ));
    }

    private LocalDate parseDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        String s = value.toString();
        if (s.isBlank()) return null;
        return LocalDate.parse(s);
    }
}
