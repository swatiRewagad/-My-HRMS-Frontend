package com.rbi.cms.ingestion.email.service;

import com.rbi.cms.ingestion.email.dto.IgnoreListRequest;
import com.rbi.cms.ingestion.email.dto.IgnoreListResponse;
import com.rbi.cms.ingestion.email.entity.EmailIgnoreList;
import com.rbi.cms.ingestion.email.entity.EmailIgnoreList.PatternType;
import com.rbi.cms.ingestion.email.repository.EmailIgnoreListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IgnoreListService {

    private final EmailIgnoreListRepository repository;

    public List<IgnoreListResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<IgnoreListResponse> getActive() {
        return repository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public IgnoreListResponse add(IgnoreListRequest request, String addedBy) {
        PatternType type = request.getPatternType();
        if (type == null) {
            type = detectPatternType(request.getEmailPattern());
        }

        EmailIgnoreList entry = EmailIgnoreList.builder()
                .emailPattern(request.getEmailPattern().toLowerCase().trim())
                .patternType(type)
                .reason(request.getReason())
                .addedBy(addedBy)
                .isActive(true)
                .build();

        entry = repository.save(entry);
        log.info("Added to ignore list: {} (type: {}) by {}", entry.getEmailPattern(), type, addedBy);
        return toResponse(entry);
    }

    @Transactional
    public List<IgnoreListResponse> bulkAdd(List<IgnoreListRequest> requests, String addedBy) {
        List<IgnoreListResponse> results = new ArrayList<>();
        for (IgnoreListRequest request : requests) {
            try {
                results.add(add(request, addedBy));
            } catch (Exception e) {
                log.warn("Failed to add ignore entry: {} - {}", request.getEmailPattern(), e.getMessage());
            }
        }
        return results;
    }

    @Transactional
    public IgnoreListResponse update(Long id, IgnoreListRequest request, String updatedBy) {
        EmailIgnoreList entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ignore list entry not found: " + id));

        entry.setEmailPattern(request.getEmailPattern().toLowerCase().trim());
        if (request.getPatternType() != null) {
            entry.setPatternType(request.getPatternType());
        }
        if (request.getReason() != null) {
            entry.setReason(request.getReason());
        }

        entry = repository.save(entry);
        log.info("Updated ignore list entry {} by {}", id, updatedBy);
        return toResponse(entry);
    }

    @Transactional
    public void remove(Long id) {
        EmailIgnoreList entry = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ignore list entry not found: " + id));
        entry.setIsActive(false);
        repository.save(entry);
        log.info("Deactivated ignore list entry: {}", entry.getEmailPattern());
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private PatternType detectPatternType(String pattern) {
        if (pattern.startsWith("*@")) return PatternType.DOMAIN;
        if (pattern.contains("*")) return PatternType.WILDCARD;
        return PatternType.EXACT;
    }

    private IgnoreListResponse toResponse(EmailIgnoreList entry) {
        return IgnoreListResponse.builder()
                .id(entry.getId())
                .emailPattern(entry.getEmailPattern())
                .patternType(entry.getPatternType())
                .reason(entry.getReason())
                .addedBy(entry.getAddedBy())
                .isActive(entry.getIsActive())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
