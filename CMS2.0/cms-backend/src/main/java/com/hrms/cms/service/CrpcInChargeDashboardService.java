package com.hrms.cms.service;

import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.EmailDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CrpcInChargeDashboardService {

    private final ComplaintRepository complaintRepository;
    private final EmailDraftRepository draftRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getPanIndiaSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalDrafts", draftRepository.count());
        summary.put("pendingApproval", draftRepository.countByStatus("SENT_FOR_APPROVAL"));
        summary.put("approvedPending", draftRepository.countByStatus("APPROVED"));
        summary.put("notAComplaint", draftRepository.countByStatus("NOT_A_COMPLAINT"));
        summary.put("converted", draftRepository.countByStatus("NEW_COMPLAINT"));
        summary.put("sentBack", draftRepository.countByStatus("SENT_BACK"));
        summary.put("totalComplaints", complaintRepository.count());
        summary.put("pendingComplaints", complaintRepository.countByStatus("pending"));
        summary.put("resolvedComplaints", complaintRepository.countByStatus("resolved"));
        summary.put("closedComplaints", complaintRepository.countByStatus("closed"));
        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOfficeWiseStats(String officeId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("officeId", officeId);
        stats.put("totalDrafts", draftRepository.countByTargetOffice(officeId));
        stats.put("pendingApproval", draftRepository.countByTargetOfficeAndStatus(officeId, "SENT_FOR_APPROVAL"));
        stats.put("notAComplaint", draftRepository.countByTargetOfficeAndStatus(officeId, "NOT_A_COMPLAINT"));
        stats.put("converted", draftRepository.countByTargetOfficeAndStatus(officeId, "NEW_COMPLAINT"));
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDeoWorkload() {
        Map<String, Object> workload = new HashMap<>();
        workload.put("deoStats", draftRepository.getDeoWorkloadStats());
        return workload;
    }
}
