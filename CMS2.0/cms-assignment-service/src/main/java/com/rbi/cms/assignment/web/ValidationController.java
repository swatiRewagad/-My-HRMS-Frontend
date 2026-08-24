package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.service.SimulationService;
import com.rbi.cms.assignment.service.ValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assignment/rulesets/{ruleSetId}/versions/{versionId}")
@RequiredArgsConstructor
public class ValidationController {

    private final ValidationService validationService;
    private final SimulationService simulationService;

    @PostMapping("/validate")
    public ResponseEntity<ValidationService.ValidationReport> validate(@PathVariable Long versionId) {
        return ResponseEntity.ok(validationService.validate(versionId));
    }

    @PostMapping("/simulate")
    public ResponseEntity<SimulationService.SimulationResponse> simulate(
            @PathVariable Long ruleSetId,
            @PathVariable Long versionId,
            @RequestBody List<Map<String, Object>> testCases) {
        return ResponseEntity.ok(simulationService.simulate(ruleSetId, versionId, testCases));
    }
}
