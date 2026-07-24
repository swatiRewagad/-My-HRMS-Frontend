package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.InterOfficeTransfer;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.InterOfficeTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterOfficeTransferService {

    private final InterOfficeTransferRepository transferRepo;
    private final ComplaintRepository complaintRepository;
    private final OfficeRoutingService officeRoutingService;
    private final NotificationService notificationService;

    @Transactional
    public InterOfficeTransfer requestTransfer(String complaintNumber, String fromOffice, String toOffice,
                                                String transferType, String reason, String requestedBy) {
        InterOfficeTransfer transfer = InterOfficeTransfer.builder()
                .complaintNumber(complaintNumber)
                .fromOffice(fromOffice)
                .toOffice(toOffice)
                .transferType(transferType)
                .reason(reason)
                .requestedBy(requestedBy)
                .status("PENDING")
                .build();
        transfer = transferRepo.save(transfer);

        notificationService.send("CRPC_HEAD", "TRANSFER_REQUEST",
                "Inter-office transfer request",
                "Transfer request for " + complaintNumber + " from " + fromOffice + " to " + toOffice,
                complaintNumber, "COMPLAINT", "/crpc/ops-head");

        return transfer;
    }

    @Transactional
    public InterOfficeTransfer approveTransfer(Long transferId, String approvedBy, String overrideToOffice) {
        InterOfficeTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        if (!"PENDING".equals(transfer.getStatus())) {
            throw new IllegalStateException("Transfer already resolved");
        }

        String targetOffice = (overrideToOffice != null && !overrideToOffice.isBlank())
                ? overrideToOffice : transfer.getToOffice();

        transfer.setToOffice(targetOffice);
        transfer.setStatus("APPROVED");
        transfer.setApprovedBy(approvedBy);
        transfer.setResolvedAt(LocalDateTime.now());
        transferRepo.save(transfer);

        // Update complaint assignment
        Complaint complaint = complaintRepository.findByComplaintNumber(transfer.getComplaintNumber()).orElse(null);
        if (complaint != null) {
            transfer.setPreviousOwner(complaint.getAssignedOfficer());
            complaint.setDepartment(resolveDepartment(targetOffice));
            complaint.setAssignedOfficer(targetOffice);
            complaintRepository.save(complaint);
        }

        // Update threshold counters
        officeRoutingService.decrementOffice(transfer.getFromOffice());
        officeRoutingService.incrementOffice(targetOffice);

        log.info("Transfer {} approved: {} → {}", transferId, transfer.getFromOffice(), targetOffice);
        return transfer;
    }

    @Transactional
    public InterOfficeTransfer rejectTransfer(Long transferId, String rejectedBy, String rejectionComment) {
        InterOfficeTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        if (!"PENDING".equals(transfer.getStatus())) {
            throw new IllegalStateException("Transfer already resolved");
        }

        transfer.setStatus("REJECTED");
        transfer.setApprovedBy(rejectedBy);
        transfer.setRejectionComment(rejectionComment);
        transfer.setResolvedAt(LocalDateTime.now());
        transferRepo.save(transfer);

        // Revert complaint to previous owner
        Complaint complaint = complaintRepository.findByComplaintNumber(transfer.getComplaintNumber()).orElse(null);
        if (complaint != null && transfer.getPreviousOwner() != null) {
            complaint.setAssignedOfficer(transfer.getPreviousOwner());
            complaintRepository.save(complaint);
        }

        log.info("Transfer {} rejected by {}: {}", transferId, rejectedBy, rejectionComment);
        return transfer;
    }

    public List<InterOfficeTransfer> getPendingTransfers() {
        return transferRepo.findByStatusOrderByRequestedAtDesc("PENDING");
    }

    public List<InterOfficeTransfer> getTransferHistory(String complaintNumber) {
        return transferRepo.findByComplaintNumberOrderByRequestedAtDesc(complaintNumber);
    }

    public long getPendingCount() {
        return transferRepo.countByStatus("PENDING");
    }

    private String resolveDepartment(String officeId) {
        if (officeId == null) return "RBIO";
        if (officeId.startsWith("CEPC")) return "CEPC";
        if (officeId.startsWith("CEPD")) return "CEPD";
        return "RBIO";
    }
}
