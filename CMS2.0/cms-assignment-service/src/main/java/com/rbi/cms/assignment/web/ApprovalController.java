package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.domain.entity.AsgnRuleSetVersion;
import com.rbi.cms.assignment.service.GovernanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assignment/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final GovernanceService governanceService;

    @GetMapping("/pending")
    public ResponseEntity<List<AsgnRuleSetVersion>> getPendingApprovals() {
        return ResponseEntity.ok(governanceService.getPendingApprovals());
    }
}
