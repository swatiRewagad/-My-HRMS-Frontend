package com.hrms.cms.service;

import com.hrms.cms.entity.RbioActionOverride;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RbioActionOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Field-level override history (UST474-475, 479-480, 483-484, 639-642).
 *
 * <p>The frontend has recorded overrides since it was written — {@code rbio-complaint-detail.component.ts}
 * calls {@code recordActionOverride} on every proposed-action and proposed-clause change — but the
 * endpoint did not exist, so every POST 404'd into a {@code catchError} and the History tab rendered an
 * empty list from {@code of([])}. This is the missing half.
 *
 * <p>Read-and-append only, by design: see {@code RbioActionOverride}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioActionOverrideService {

    private final RbioActionOverrideRepository overrideRepository;
    private final ComplaintRepository complaintRepository;

    public List<Map<String, Object>> list(String complaintNumber) {
        return overrideRepository.findByComplaintNumberOrderByOverriddenAtDesc(complaintNumber).stream()
                .map(RbioActionOverrideService::toPayload)
                .toList();
    }

    /**
     * Records an override, or returns null when nothing actually changed.
     *
     * <p>A no-change POST is ignored rather than refused. The client fires on every field blur, so
     * rejecting equal values would surface an error for an event the user did not cause — while STORING
     * them would fill the History tab with rows showing "X → X" and bury the real overrides.
     */
    @Transactional
    public Map<String, Object> record(String complaintNumber, Map<String, Object> request,
                                      String actor, String actorRole) {
        if (!complaintRepository.findByComplaintNumber(complaintNumber).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found: " + complaintNumber);
        }

        String fieldName = trimmed(request.get("fieldName"));
        if (fieldName == null || fieldName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldName is required");
        }

        String oldValue = trimmed(request.get("oldValue"));
        String newValue = trimmed(request.get("newValue"));
        if (Objects.equals(oldValue, newValue)) {
            return null;
        }

        // The actor is taken from the resolved identity, falling back to the body only when the caller
        // could not be identified from the token. An override record whose author is whatever the client
        // claimed would be worthless as evidence of who departed from an earlier recommendation.
        String author = (actor != null && !actor.isBlank()) ? actor : trimmed(request.get("overriddenBy"));
        String authorRole = (actorRole != null && !actorRole.isBlank())
                ? actorRole : trimmed(request.get("overriddenByRole"));

        RbioActionOverride saved = overrideRepository.save(RbioActionOverride.builder()
                .complaintNumber(complaintNumber)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .overriddenBy(author)
                .overriddenByRole(authorRole)
                .overriddenAt(LocalDateTime.now())
                .build());

        return toPayload(saved);
    }

    private static String trimmed(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** Shaped to the frontend's existing {@code ActionOverride} interface (rbio-workflow.service.ts:7-16). */
    private static Map<String, Object> toPayload(RbioActionOverride override) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", String.valueOf(override.getId()));
        payload.put("complaintId", override.getComplaintNumber());
        payload.put("fieldName", override.getFieldName());
        payload.put("oldValue", override.getOldValue());
        payload.put("newValue", override.getNewValue());
        payload.put("overriddenBy", override.getOverriddenBy());
        payload.put("overriddenByRole", override.getOverriddenByRole());
        // Named "timestamp" because that is the field the declared interface reads; renaming it here would
        // leave the History tab showing blank dates.
        payload.put("timestamp", override.getOverriddenAt() != null ? override.getOverriddenAt().toString() : null);
        return payload;
    }
}
