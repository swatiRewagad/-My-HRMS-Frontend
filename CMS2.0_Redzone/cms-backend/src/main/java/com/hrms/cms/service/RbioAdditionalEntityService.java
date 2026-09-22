package com.hrms.cms.service;

import com.hrms.cms.entity.RbioAdditionalEntity;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RbioAdditionalEntityRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
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

/**
 * Additional regulated entities on a complaint, and THE authority on the six-entity cap (UST487-495).
 *
 * <p><b>This service owns the cap and publishes it as {@link #assertCapAllowsOneMore}.</b> S4's impleading
 * and S5's meeting participants must call that rather than re-counting, because a cap enforced in three
 * places is a cap that will eventually be enforced differently in three places — and the version that
 * disagrees will be the one a citizen's complaint hits.
 *
 * <p>The cap is enforced HERE rather than in the browser. {@code rbio-add-entity.component.ts:20} already
 * disables its own control at six, which is correct as an affordance and worthless as a control: a direct
 * POST bypasses it entirely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioAdditionalEntityService {

    private final RbioAdditionalEntityRepository entityRepository;
    private final ComplaintRepository complaintRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;

    /** Every additional entity on the complaint, oldest first. */
    public List<Map<String, Object>> list(String complaintNumber) {
        return entityRepository.findByComplaintNumberOrderByCreatedAtAsc(complaintNumber).stream()
                .map(RbioAdditionalEntityService::toPayload)
                .toList();
    }

    /**
     * How many more entities may be added — what the UI needs to disable its control honestly.
     */
    public Map<String, Object> capState(String complaintNumber) {
        long used = entityRepository.countByComplaintNumber(complaintNumber);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("used", used);
        state.put("maximum", RbioAdditionalEntity.MAX_PER_COMPLAINT);
        state.put("remaining", Math.max(0, RbioAdditionalEntity.MAX_PER_COMPLAINT - used));
        state.put("canAddMore", used < RbioAdditionalEntity.MAX_PER_COMPLAINT);
        return state;
    }

    /**
     * Refuses when the complaint already holds the maximum number of additional entities.
     *
     * <p>Public because S4 and S5 attach parties through their own flows and must hit the SAME cap. A
     * 409 rather than a 400: the request is well-formed, it conflicts with the current state.
     */
    public void assertCapAllowsOneMore(String complaintNumber) {
        long used = entityRepository.countByComplaintNumber(complaintNumber);
        if (used >= RbioAdditionalEntity.MAX_PER_COMPLAINT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A complaint may have at most " + RbioAdditionalEntity.MAX_PER_COMPLAINT
                            + " additional entities; this complaint already has " + used + ".");
        }
    }

    /**
     * Adds an entity after validating the complaint exists, the mandatory fields are present, and the cap
     * permits it.
     *
     * <p>Mandatory fields mirror the primary entity's set (UST487): name, branch, type and category. They
     * are validated on the SERVER because UST478 requires mandatory-ness to be enforced, not merely
     * indicated in red.
     */
    @Transactional
    public Map<String, Object> add(String complaintNumber, Map<String, Object> request,
                                   String actor, String actorRole) {
        if (!complaintRepository.findByComplaintNumber(complaintNumber).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint not found: " + complaintNumber);
        }

        String entityName = trimmed(request.get("entityName"));
        String entityBranch = trimmed(request.get("entityBranch"));
        String entityType = trimmed(request.get("entityType"));
        String entityCategory = trimmed(request.get("entityCategory"));

        requireField("entityName", entityName);
        requireField("entityBranch", entityBranch);
        requireField("entityType", entityType);
        requireField("entityCategory", entityCategory);

        assertCapAllowsOneMore(complaintNumber);

        if (entityRepository.existsByComplaintNumberAndEntityNameIgnoreCase(complaintNumber, entityName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + entityName + "' is already recorded as an additional entity on this complaint.");
        }

        RbioAdditionalEntity saved = entityRepository.save(RbioAdditionalEntity.builder()
                .complaintNumber(complaintNumber)
                .entityName(entityName)
                .entityBranch(entityBranch)
                .entityType(entityType)
                .entityCategory(entityCategory)
                .regulatedEntityId(resolveRegulatedEntityId(entityName))
                .createdBy(actor)
                .createdByRole(actorRole)
                .createdAt(LocalDateTime.now())
                .build());

        return toPayload(saved);
    }

    @Transactional
    public void remove(String complaintNumber, Long id) {
        RbioAdditionalEntity existing = entityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Additional entity not found"));
        // Checked rather than assumed: an id from another complaint would otherwise delete a row the
        // caller has no business touching.
        if (!existing.getComplaintNumber().equals(complaintNumber)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Additional entity not found on this complaint");
        }
        entityRepository.delete(existing);
    }

    /**
     * The REGULATED_ENTITIES id for this name, or null when it does not resolve.
     *
     * <p>Never fabricated and never blocking — see the field comment on
     * {@code RbioAdditionalEntity.regulatedEntityId}.
     */
    private Long resolveRegulatedEntityId(String entityName) {
        try {
            // Matched on nameNormalized via the entity's own normalisation rule, not on a case-insensitive
            // comparison of the raw name: the master table stores "HDFC Bank Ltd." and a user types
            // "HDFC BANK LTD", which are the same entity and differ by punctuation. Reusing
            // RegulatedEntity.normalize keeps this lookup agreeing with how the column was populated, and
            // findByNameNormalized is indexed where a findAll() scan would read the whole table.
            return regulatedEntityRepository.findByNameNormalized(RegulatedEntity.normalize(entityName))
                    .map(RegulatedEntity::getId)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve regulated entity '{}': {}", entityName, e.getMessage());
            return null;
        }
    }

    private static void requireField(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }

    private static String trimmed(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /** Shaped to the frontend's existing {@code AdditionalEntity} interface so the component binds unchanged. */
    private static Map<String, Object> toPayload(RbioAdditionalEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", String.valueOf(entity.getId()));
        payload.put("complaintId", entity.getComplaintNumber());
        payload.put("entityName", entity.getEntityName());
        payload.put("entityBranch", entity.getEntityBranch());
        payload.put("entityType", entity.getEntityType());
        payload.put("entityCategory", entity.getEntityCategory());
        payload.put("regulatedEntityId", entity.getRegulatedEntityId());
        payload.put("createdBy", entity.getCreatedBy());
        payload.put("createdByRole", entity.getCreatedByRole());
        payload.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return payload;
    }
}
