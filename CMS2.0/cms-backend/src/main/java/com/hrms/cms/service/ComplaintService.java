package com.hrms.cms.service;

import com.hrms.cms.dto.*;
import com.hrms.cms.entity.*;
import com.hrms.cms.event.ComplaintEventPublisher;
import com.hrms.cms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final BankRepository bankRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final ComplaintAttachmentRepository attachmentRepository;
    private final ComplaintEventPublisher eventPublisher;
    private final ComplaintRoutingService routingService;

    @Cacheable(value = "dashboard", unless = "#result == null")
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        return DashboardResponse.builder()
                .totalComplaints(complaintRepository.count())
                .pendingComplaints(complaintRepository.countByStatus("pending"))
                .inProgressComplaints(complaintRepository.countByStatus("in_progress"))
                .resolvedComplaints(complaintRepository.countByStatus("resolved"))
                .closedComplaints(complaintRepository.countByStatus("closed"))
                .escalatedComplaints(complaintRepository.countByStatus("escalated"))
                .highPriority(complaintRepository.countByPriority("high"))
                .mediumPriority(complaintRepository.countByPriority("medium"))
                .lowPriority(complaintRepository.countByPriority("low"))
                .build();
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaints() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<Complaint> getAllComplaintsPaged(Pageable pageable) {
        return complaintRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Complaint> getByStatusPaged(String status, Pageable pageable) {
        return complaintRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Complaint> searchComplaintsPaged(String query, Pageable pageable) {
        return complaintRepository.searchPaged(query, pageable);
    }

    @Transactional(readOnly = true)
    public Complaint getComplaint(Long id) {
        return complaintRepository.findById(id).orElseThrow(() -> new RuntimeException("Complaint not found"));
    }

    @Transactional(readOnly = true)
    public Complaint getByComplaintNumber(String number) {
        return complaintRepository.findByComplaintNumber(number).orElseThrow(() -> new RuntimeException("Complaint not found"));
    }

    @Transactional(readOnly = true)
    public List<Complaint> getByComplainantPhone(String phone) {
        return complaintRepository.findByComplainantPhoneOrderByCreatedAtDesc(phone);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getByStatus(String status) {
        return complaintRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public List<Complaint> searchComplaints(String query) {
        return complaintRepository.search(query);
    }

    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional
    public Complaint fileComplaint(FileComplaintRequest req) {
        validatePriorReComplaintFields(req);

        Complaint complaint = Complaint.builder()
                .complaintNumber(generateComplaintNumber())
                .complainantName(req.getComplainantName())
                .complainantEmail(req.getComplainantEmail())
                .complainantPhone(req.getComplainantPhone())
                .complainantAddress(req.getComplainantAddress())
                .bankId(req.getBankId())
                .bankBranch(req.getBankBranch())
                .accountNumber(req.getAccountNumber())
                .categoryId(req.getCategoryId())
                .subject(req.getSubject())
                .description(req.getDescription())
                .reliefSought(req.getReliefSought())
                .priority(req.getPriority())
                .filingType(req.getFilingType())
                .bankComplaintReference(req.getBankComplaintReference())
                .bankComplaintDate(req.getBankComplaintDate() != null ? LocalDateTime.parse(req.getBankComplaintDate() + "T00:00:00") : null)
                .priorReComplaint(req.getPriorReComplaint())
                .reComplaintDate(req.getReComplaintDate())
                .reComplaintReference(req.getReComplaintReference())
                .reRepliedAndDissatisfied(req.getReRepliedAndDissatisfied())
                .build();

        // Apply routing based on filing type and entity (mirrors jBPM DepartmentRoutingTask)
        String entityCode = "";
        if (complaint.getBankId() != null) {
            entityCode = bankRepository.findById(complaint.getBankId())
                    .map(Bank::getCode).orElse("");
        }
        complaint.setEntityCode(entityCode);
        ComplaintRoutingService.RoutingDecision routing = routingService.routeComplaint(complaint, entityCode);
        complaint.setDepartment(routing.getDepartment());
        complaint.setAssignedRole(routing.getAssignedRole());
        complaint.setAssignedOfficer(routing.getAssignedOfficer());
        complaint.setWorkflowStage(routing.getStage());

        Complaint saved = complaintRepository.save(complaint);

        addTimeline(saved.getId(), "filed", "System",
                "Complaint filed and routed to " + routing.getDepartment() + " (" + routing.getReason() + ")",
                null, "pending");

        eventPublisher.publishComplaintIngested(saved);

        return saved;
    }

    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional
    public Complaint updateComplaint(Long id, UpdateComplaintRequest req) {
        Complaint complaint = getComplaint(id);
        String oldStatus = complaint.getStatus();

        if (req.getStatus() != null) {
            complaint.setStatus(req.getStatus());
            if ("resolved".equals(req.getStatus())) complaint.setResolvedAt(LocalDateTime.now());
            if ("closed".equals(req.getStatus())) complaint.setClosedAt(LocalDateTime.now());
            if ("escalated".equals(req.getStatus())) complaint.setEscalatedAt(LocalDateTime.now());
        }
        if (req.getPriority() != null) complaint.setPriority(req.getPriority());
        if (req.getAssignedOfficer() != null) complaint.setAssignedOfficer(req.getAssignedOfficer());

        Complaint saved = complaintRepository.save(complaint);

        String action = req.getStatus() != null ? "status_change" : "update";
        addTimelineAsync(id, action, req.getAssignedOfficer() != null ? req.getAssignedOfficer() : "System",
                req.getRemarks(), oldStatus, complaint.getStatus());

        return saved;
    }

    @Transactional
    public void deleteComplaint(Long id) {
        complaintRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ComplaintTimeline> getTimeline(Long complaintId) {
        return timelineRepository.findByComplaintIdOrderByPerformedAtDesc(complaintId);
    }

    @Async("taskExecutor")
    @Transactional
    public void addTimelineAsync(Long complaintId, String action, String performedBy, String remarks, String fromStatus, String toStatus) {
        ComplaintTimeline entry = ComplaintTimeline.builder()
                .complaintId(complaintId)
                .action(action)
                .performedBy(performedBy)
                .remarks(remarks)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .build();
        timelineRepository.save(entry);
    }

    public void addTimeline(Long complaintId, String action, String performedBy, String remarks, String fromStatus, String toStatus) {
        ComplaintTimeline entry = ComplaintTimeline.builder()
                .complaintId(complaintId)
                .action(action)
                .performedBy(performedBy)
                .remarks(remarks)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .build();
        timelineRepository.save(entry);
    }

    @Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<ComplaintCategory> getAllCategories() {
        return categoryRepository.findByStatus("active");
    }

    @Cacheable(value = "categories-root", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<ComplaintCategory> getRootCategories() {
        return categoryRepository.findByParentIdIsNullOrderBySortOrder();
    }

    @Cacheable(value = "categories-sub", key = "#parentId", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<ComplaintCategory> getSubCategories(Long parentId) {
        return categoryRepository.findByParentIdOrderBySortOrder(parentId);
    }

    @Cacheable(value = "banks", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<Bank> getAllBanks() {
        return bankRepository.findByStatus("active");
    }

    @Cacheable(value = "banks-by-type", key = "#type", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<Bank> getBanksByType(String type) {
        return bankRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<ComplaintAttachment> getAttachments(Long complaintId) {
        return attachmentRepository.findByComplaintId(complaintId);
    }

    @Transactional
    public ComplaintAttachment saveAttachment(ComplaintAttachment attachment) {
        return attachmentRepository.save(attachment);
    }

    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional
    public Complaint updateMaintainability(Complaint complaint) {
        Complaint saved = complaintRepository.save(complaint);
        addTimelineAsync(saved.getId(), "maintainability_decision", complaint.getMaintainabilityDeterminedBy(),
                "Determination: " + complaint.getMaintainabilityDetermination(),
                null, complaint.getStatus());
        return saved;
    }

    private void validatePriorReComplaintFields(FileComplaintRequest req) {
        if (Boolean.TRUE.equals(req.getPriorReComplaint())) {
            if (req.getReComplaintDate() == null) {
                throw new IllegalArgumentException("RE complaint date is required when prior complaint to RE is indicated");
            }
            if (req.getReComplaintReference() == null || req.getReComplaintReference().isBlank()) {
                throw new IllegalArgumentException("RE complaint reference is required when prior complaint to RE is indicated");
            }
            if (req.getReComplaintDate().isAfter(java.time.LocalDate.now())) {
                throw new IllegalArgumentException("RE complaint date cannot be in the future");
            }
        }
    }

    private String generateComplaintNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "CMS-" + date + "-" + uuid;
    }
}
