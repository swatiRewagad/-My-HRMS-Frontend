package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Creates the per-complaint nodal officer record that the RE portal and the staleness reminder
 * scheduler read. Contact details are snapshotted rather than joined so a later change to the
 * regulated entity's nodal officer does not rewrite history on complaints already in flight.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodalOfficerRecordService {

    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;

    public NodalOfficerRecord createForComplaint(Complaint complaint) {
        String complaintNumber = complaint.getComplaintNumber();
        if (complaintNumber == null || complaintNumber.isBlank()) {
            log.warn("Skipping nodal officer record for complaint id={} — no complaint number", complaint.getId());
            return null;
        }

        List<NodalOfficerRecord> existing = nodalOfficerRecordRepository.findByComplaintNumber(complaintNumber);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        RegulatedEntity entity = resolveEntity(complaint).orElse(null);

        NodalOfficerRecord record = NodalOfficerRecord.builder()
                .complaintNumber(complaintNumber)
                .entityName(entity != null ? entity.getName() : complaint.getEntityName())
                .nodalOfficerName(entity != null ? entity.getNodalOfficerName() : null)
                .pnoName(entity != null ? entity.getPnoName() : null)
                .designation(entity != null ? entity.getNodalOfficerDesignation() : null)
                .email(entity != null ? entity.getNodalOfficerEmail() : null)
                .phone(entity != null ? entity.getNodalOfficerPhone() : null)
                .assignedTo(complaint.getAssignedOfficer())
                .build();

        NodalOfficerRecord saved = nodalOfficerRecordRepository.save(record);
        log.info("Created nodal officer record for complaint {} (entity={}, matchedRegulatedEntity={})",
                complaintNumber, saved.getEntityName(), entity != null);
        return saved;
    }

    private Optional<RegulatedEntity> resolveEntity(Complaint complaint) {
        if (complaint.getRegulatedEntityId() != null) {
            Optional<RegulatedEntity> byId = regulatedEntityRepository.findById(complaint.getRegulatedEntityId());
            if (byId.isPresent()) {
                return byId;
            }
        }

        String entityName = complaint.getEntityName();
        if (entityName == null || entityName.isBlank()) {
            return Optional.empty();
        }

        String normalized = RegulatedEntity.normalize(entityName);
        Optional<RegulatedEntity> exact = regulatedEntityRepository.findByNameNormalized(normalized);
        if (exact.isPresent()) {
            return exact;
        }

        return regulatedEntityRepository.searchByNormalizedName(normalized).stream().findFirst();
    }
}
