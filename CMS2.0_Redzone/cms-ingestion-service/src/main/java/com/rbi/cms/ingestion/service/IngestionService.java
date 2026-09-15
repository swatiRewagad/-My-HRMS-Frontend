package com.rbi.cms.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.config.KafkaTopics;
import com.rbi.cms.common.enums.*;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.common.exception.ResourceNotFoundException;
import com.rbi.cms.common.util.ComplaintIdGenerator;
import com.rbi.cms.ingestion.dto.*;
import com.rbi.cms.ingestion.entity.*;
import com.rbi.cms.ingestion.mapper.ComplaintMapper;
import com.rbi.cms.ingestion.repository.*;
import com.rbi.cms.ingestion.validator.ComplaintValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final ComplaintMasterRepository complaintRepository;
    private final ComplaintHistoryRepository historyRepository;
    private final AttachmentMetadataRepository attachmentRepository;
    private final EventPublisher eventPublisher;
    private final ComplaintMapper complaintMapper;
    private final ComplaintValidator validator;
    private final StorageClient storageClient;
    private final ObjectMapper objectMapper;

    private static final int SLA_DAYS = 30;

    @Transactional
    public ComplaintRegistrationResponse registerComplaint(ComplaintRegistrationRequest request) {
        log.info("Registering new complaint from channel: {}, category: {}", request.getChannel(), request.getCategory());

        validator.validate(request);

        ComplaintMaster complaint = complaintMapper.toEntity(request);
        complaint.setComplaintId(ComplaintIdGenerator.generate());
        complaint.setChannel(Channel.valueOf(request.getChannel()));
        complaint.setCategory(ComplaintCategory.valueOf(request.getCategory()));
        complaint.setStatus(ComplaintStatus.NEW);
        complaint.setPriority(determinePriority(request));
        complaint.setSlaDueDate(Instant.now().plus(SLA_DAYS, ChronoUnit.DAYS));

        ComplaintMaster saved = complaintRepository.save(complaint);

        recordHistory(saved.getComplaintId(), null, ComplaintStatus.NEW, "COMPLAINT_REGISTERED", "SYSTEM");

        insertOutboxEvent(saved);

        log.info("Complaint registered successfully: {}", saved.getComplaintId());

        return ComplaintRegistrationResponse.builder()
                .complaintId(saved.getComplaintId())
                .status(saved.getStatus())
                .registeredAt(saved.getCreatedAt())
                .slaDueDate(saved.getSlaDueDate())
                .acknowledgement("Your complaint has been registered successfully. " +
                        "Reference number: " + saved.getComplaintId())
                .build();
    }

    @Transactional
    public void addAttachments(String complaintId, List<MultipartFile> files) {
        log.info("Adding {} attachments to complaint: {}", files.size(), complaintId);

        if (!complaintRepository.existsByComplaintId(complaintId)) {
            throw new ResourceNotFoundException("Complaint", complaintId);
        }

        for (MultipartFile file : files) {
            String storagePath = storageClient.store(complaintId, file);

            AttachmentMetadata metadata = AttachmentMetadata.builder()
                    .complaintId(complaintId)
                    .fileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .storagePath(storagePath)
                    .build();

            attachmentRepository.save(metadata);
        }
    }

    @Transactional(readOnly = true)
    public ComplaintDetailResponse getComplaint(String complaintId) {
        ComplaintMaster complaint = complaintRepository.findByComplaintId(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        ComplaintDetailResponse response = complaintMapper.toDetailResponse(complaint);
        List<AttachmentMetadata> attachments = attachmentRepository.findByComplaintId(complaintId);
        response.setAttachments(complaintMapper.toAttachmentResponseList(attachments));

        List<ComplaintHistory> historyList = historyRepository.findByComplaintIdOrderByPerformedAtAsc(complaintId);
        List<ComplaintDetailResponse.TimelineEntry> timeline = historyList.stream()
                .map(h -> ComplaintDetailResponse.TimelineEntry.builder()
                        .fromStatus(h.getPreviousStatus() != null ? h.getPreviousStatus().name() : null)
                        .toStatus(h.getNewStatus().name())
                        .action(h.getAction())
                        .remarks(h.getRemarks())
                        .timestamp(h.getPerformedAt())
                        .build())
                .toList();
        response.setTimeline(timeline);

        return response;
    }

    @Transactional
    public void updateComplaintStatus(String complaintId, ComplaintStatus newStatus, String remarks, String performedBy) {
        ComplaintMaster complaint = complaintRepository.findByComplaintId(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        ComplaintStatus previousStatus = complaint.getStatus();
        complaint.setStatus(newStatus);

        if (newStatus == ComplaintStatus.RESOLVED) {
            complaint.setResolvedAt(Instant.now());
            if (remarks != null) complaint.setResolutionSummary(remarks);
        } else if (newStatus == ComplaintStatus.CLOSED) {
            complaint.setClosedAt(Instant.now());
        }

        complaintRepository.save(complaint);
        recordHistory(complaintId, previousStatus, newStatus,
                "STATUS_TRANSITION: " + previousStatus + " → " + newStatus, performedBy);

        publishStatusChangeEvent(complaint, previousStatus, newStatus, remarks);

        log.info("Complaint {} transitioned: {} → {} by {}", complaintId, previousStatus, newStatus, performedBy);
    }

    private void publishStatusChangeEvent(ComplaintMaster complaint, ComplaintStatus previousStatus,
                                          ComplaintStatus newStatus, String remarks) {
        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of(
                    "complainantEmail", complaint.getComplainantEmail() != null ? complaint.getComplainantEmail() : "",
                    "complainantPhone", complaint.getComplainantPhone() != null ? complaint.getComplainantPhone() : "",
                    "category", complaint.getCategory() != null ? complaint.getCategory().name() : "GENERAL",
                    "remarks", remarks != null ? remarks : ""
            ));

            ComplaintEvent event = ComplaintEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .complaintId(complaint.getComplaintId())
                    .previousStatus(previousStatus)
                    .currentStatus(newStatus)
                    .payload(payload)
                    .correlationId(UUID.randomUUID().toString())
                    .occurredAt(Instant.now())
                    .build();

            String topic = switch (newStatus) {
                case RESOLVED -> KafkaTopics.COMPLAINT_RESOLVED;
                case ESCALATED -> KafkaTopics.COMPLAINT_ESCALATED;
                case CLOSED -> KafkaTopics.COMPLAINT_CLOSED;
                case IN_PROGRESS -> KafkaTopics.COMPLAINT_IN_PROGRESS;
                case ASSIGNED -> KafkaTopics.COMPLAINT_ASSIGNED;
                default -> null;
            };

            if (topic != null) {
                eventPublisher.publishComplaintEvent(topic, complaint.getComplaintId(), event);
            }
        } catch (Exception e) {
            log.warn("Failed to publish status change event for {}: {}", complaint.getComplaintId(), e.getMessage());
        }
    }

    @Transactional
    public void updateAssignment(String complaintId, String assignedTeam, String assignedTo) {
        ComplaintMaster complaint = complaintRepository.findByComplaintId(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        complaint.setAssignedTeam(assignedTeam);
        complaint.setAssignedTo(assignedTo);
        complaint.setStatus(ComplaintStatus.ASSIGNED);
        complaintRepository.save(complaint);

        recordHistory(complaintId, ComplaintStatus.NEW, ComplaintStatus.ASSIGNED,
                "ASSIGNED to team: " + assignedTeam, "SYSTEM");

        log.info("Complaint {} assigned to team: {}", complaintId, assignedTeam);
    }

    private Priority determinePriority(ComplaintRegistrationRequest request) {
        if (request.getAmountInvolved() != null && request.getAmountInvolved() > 1000000) {
            return Priority.HIGH;
        }
        if ("CREDIT_CARD".equals(request.getCategory()) || "LOAN".equals(request.getCategory())) {
            return Priority.HIGH;
        }
        return Priority.MEDIUM;
    }

    private void recordHistory(String complaintId, ComplaintStatus previous, ComplaintStatus newStatus,
                               String action, String performedBy) {
        ComplaintHistory history = ComplaintHistory.builder()
                .complaintId(complaintId)
                .previousStatus(previous)
                .newStatus(newStatus)
                .action(action)
                .performedBy(performedBy)
                .build();
        historyRepository.save(history);
    }

    private void insertOutboxEvent(ComplaintMaster complaint) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(java.util.Map.of(
                    "category", complaint.getCategory() != null ? complaint.getCategory().name() : "GENERAL",
                    "priority", complaint.getPriority() != null ? complaint.getPriority().name() : "MEDIUM"
            ));
        } catch (Exception e) {
            payload = "{\"category\":\"GENERAL\",\"priority\":\"MEDIUM\"}";
        }

        ComplaintEvent event = ComplaintEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .complaintId(complaint.getComplaintId())
                .previousStatus(null)
                .currentStatus(ComplaintStatus.NEW)
                .payload(payload)
                .correlationId(UUID.randomUUID().toString())
                .occurredAt(Instant.now())
                .build();

        eventPublisher.publishComplaintEvent(KafkaTopics.COMPLAINT_INGESTED, complaint.getComplaintId(), event);
    }
}
