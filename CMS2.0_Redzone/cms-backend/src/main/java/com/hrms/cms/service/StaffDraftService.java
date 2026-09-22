package com.hrms.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.ComplaintEditPresence;
import com.hrms.cms.entity.StaffDraft;
import com.hrms.cms.repository.ComplaintEditPresenceRepository;
import com.hrms.cms.repository.StaffDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owner-scoped staff drafts, auto-save and edit presence (UST673, UST674, UST675).
 *
 * <h2>Ownership is enforced here, not filtered in the client</h2>
 * Every method takes the owner as a resolved identity and every query is scoped by it. No method accepts
 * an owner from a request body. The pattern being avoided is the existing draft listing, where the owner
 * arrives as a query parameter and omitting it returns every draft in the system.
 *
 * <h2>Auto-save and manual save share one row per milestone</h2>
 * A 2-minute auto-save over an 8-hour shift would otherwise leave ~240 rows per officer per complaint.
 * The unique key on (milestone, owner, complaint) makes a repeated save an UPDATE. {@code SAVE_SOURCE}
 * records which kind of save last touched the row, so {@link #discardAutosave} can honour UST673's
 * requirement to drop the auto-saved draft on a successful save-and-proceed without also deleting a
 * draft the user deliberately saved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffDraftService {

    private static final String CFG_AUTOSAVE_INTERVAL = "cms.draft.autosave_interval_seconds";
    private static final String CFG_PRESENCE_STALE = "cms.draft.edit_presence_stale_seconds";

    private static final int DEFAULT_AUTOSAVE_INTERVAL_SECONDS = 120;
    private static final int DEFAULT_PRESENCE_STALE_SECONDS = 90;

    private final StaffDraftRepository draftRepository;
    private final ComplaintEditPresenceRepository presenceRepository;
    private final SystemConfigService systemConfig;
    private final ObjectMapper objectMapper;

    /**
     * The auto-save interval in seconds, from SYSTEM_CONFIG (UST673).
     *
     * <p>Read through {@link SystemConfigService#getInt} rather than {@code TimelineConfigService}: the
     * latter rejects any key not prefixed {@code timeline.}, is uncached, returns a String, and its
     * fallback map returns {@code "30"} for ANY unknown key. A wrong-but-plausible number is the worst
     * possible failure mode for a timer, so a mechanism that invents one is unusable here.
     *
     * <p>0 disables auto-save. Negative values are clamped to 0 rather than becoming a runaway timer.
     */
    public int autosaveIntervalSeconds() {
        int configured = systemConfig.getInt(CFG_AUTOSAVE_INTERVAL, DEFAULT_AUTOSAVE_INTERVAL_SECONDS);
        return Math.max(0, configured);
    }

    public int presenceStaleSeconds() {
        int configured = systemConfig.getInt(CFG_PRESENCE_STALE, DEFAULT_PRESENCE_STALE_SECONDS);
        return configured <= 0 ? DEFAULT_PRESENCE_STALE_SECONDS : configured;
    }

    /**
     * Saves or replaces the caller's draft for one milestone.
     *
     * @param milestone       one of {@link StaffDraft.Milestone}
     * @param ownerUserId     the RESOLVED caller; never a request field
     * @param complaintNumber null for {@code REGISTER}
     * @param formData        the in-progress values, stored verbatim so a resume restores everything
     * @param autosave        true when written by the timer rather than by a button
     */
    @Transactional
    public StaffDraft save(String milestone, String ownerUserId, String complaintNumber,
                           Map<String, Object> formData, boolean autosave) {

        StaffDraft.Milestone parsed = parseMilestone(milestone);

        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalArgumentException("A draft cannot be saved without a resolved owner.");
        }
        if (formData == null || formData.isEmpty()) {
            // An empty draft would overwrite a good one with nothing — the timer firing on a form the
            // user has not touched yet must not destroy what they saved a moment ago.
            throw new IllegalArgumentException("A draft with no field values cannot be saved.");
        }
        if (parsed != StaffDraft.Milestone.REGISTER
                && (complaintNumber == null || complaintNumber.isBlank())) {
            throw new IllegalArgumentException(
                    "Milestone " + parsed + " requires a complaint number.");
        }

        String normalisedComplaint = (complaintNumber == null || complaintNumber.isBlank())
                ? null : complaintNumber.trim();

        StaffDraft draft = findOwn(parsed, ownerUserId, normalisedComplaint)
                .orElseGet(() -> StaffDraft.builder()
                        .milestone(parsed.name())
                        .ownerUserId(ownerUserId)
                        .complaintNumber(normalisedComplaint)
                        .build());

        draft.setFormDataJson(serialise(formData));
        draft.setSaveSource(autosave
                ? StaffDraft.SaveSource.AUTOSAVE.name()
                : StaffDraft.SaveSource.MANUAL.name());
        draft.setDraftStatus(StaffDraft.STATUS_IN_PROGRESS);

        StaffDraft saved = draftRepository.save(draft);
        log.debug("Staff draft {} saved for '{}' at {} ({})",
                saved.getId(), ownerUserId, parsed, autosave ? "autosave" : "manual");
        return saved;
    }

    /** The caller's own draft for a milestone, or empty. Never another user's. */
    public Optional<StaffDraft> findOwn(StaffDraft.Milestone milestone, String ownerUserId,
                                        String complaintNumber) {
        if (complaintNumber == null) {
            return draftRepository.findFirstByMilestoneAndOwnerUserIdAndComplaintNumberIsNull(
                    milestone.name(), ownerUserId);
        }
        return draftRepository.findByMilestoneAndOwnerUserIdAndComplaintNumber(
                milestone.name(), ownerUserId, complaintNumber);
    }

    /** Every draft belonging to the caller. */
    public List<Map<String, Object>> listOwn(String ownerUserId) {
        return draftRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Deletes the caller's own draft by id.
     *
     * @return false when the draft does not exist OR belongs to somebody else — the two are deliberately
     *         indistinguishable, so this cannot be used to discover which draft ids exist.
     */
    @Transactional
    public boolean deleteOwn(Long id, String ownerUserId) {
        return draftRepository.findByIdAndOwnerUserId(id, ownerUserId).map(draft -> {
            draftRepository.delete(draft);
            return true;
        }).orElse(false);
    }

    /**
     * UST673: drops the AUTO-SAVED draft after a successful save-and-proceed.
     *
     * <p>Only auto-saved rows are removed. A draft the user explicitly saved is theirs to keep or delete;
     * silently discarding it because they later submitted something would lose work they chose to retain.
     */
    @Transactional
    public int discardAutosave(String ownerUserId, String complaintNumber) {
        List<StaffDraft> drafts = complaintNumber == null
                ? draftRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId)
                : draftRepository.findByOwnerUserIdAndComplaintNumber(ownerUserId, complaintNumber);

        List<StaffDraft> autosaved = drafts.stream().filter(StaffDraft::isAutosaved).toList();
        draftRepository.deleteAll(autosaved);
        if (!autosaved.isEmpty()) {
            log.debug("Discarded {} auto-saved draft(s) for '{}' after save-and-proceed",
                    autosaved.size(), ownerUserId);
        }
        return autosaved.size();
    }

    // ── Edit presence (UST675), advisory only ────────────────────────────────────────────────────

    /**
     * Records that this user has the complaint open, and reports who ELSE does.
     *
     * <p>Advisory. It never blocks a save — enforcement is the {@code @Version} check on
     * {@code Complaint}, which surfaces as a 409. This exists because optimistic locking is
     * detect-on-write: without it the second user learns about the conflict only after typing.
     */
    @Transactional
    public List<Map<String, Object>> heartbeatAndListOthers(String complaintNumber, String userId,
                                                           String displayName) {
        LocalDateTime now = LocalDateTime.now();

        ComplaintEditPresence presence = presenceRepository
                .findByComplaintNumberAndUserId(complaintNumber, userId)
                .orElseGet(() -> ComplaintEditPresence.builder()
                        .complaintNumber(complaintNumber)
                        .userId(userId)
                        .build());
        presence.setDisplayName(displayName);
        presence.setHeartbeatAt(now);
        presenceRepository.save(presence);

        LocalDateTime freshSince = now.minusSeconds(presenceStaleSeconds());
        return presenceRepository
                .findOtherActiveEditors(complaintNumber, userId, freshSince).stream()
                .map(other -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("userId", other.getUserId());
                    dto.put("displayName", other.getDisplayName());
                    dto.put("since", other.getHeartbeatAt() == null
                            ? null : other.getHeartbeatAt().toString());
                    return dto;
                })
                .toList();
    }

    /** Releases the caller's presence when they navigate away. */
    @Transactional
    public void releasePresence(String complaintNumber, String userId) {
        presenceRepository.findByComplaintNumberAndUserId(complaintNumber, userId)
                .ifPresent(presenceRepository::delete);
    }

    private StaffDraft.Milestone parseMilestone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("A draft milestone is required.");
        }
        try {
            return StaffDraft.Milestone.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown draft milestone '" + raw + "'. Valid values: "
                    + Arrays.toString(StaffDraft.Milestone.values()));
        }
    }

    /** The stored field values for one draft, ready to repopulate a form. */
    public Map<String, Object> formDataOf(StaffDraft draft) {
        return deserialise(draft.getFormDataJson());
    }

    public Map<String, Object> toDto(StaffDraft draft) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", draft.getId());
        dto.put("milestone", draft.getMilestone());
        dto.put("complaintNumber", draft.getComplaintNumber());
        dto.put("draftStatus", draft.getDraftStatus());
        dto.put("saveSource", draft.getSaveSource());
        dto.put("formData", deserialise(draft.getFormDataJson()));
        dto.put("updatedAt", draft.getUpdatedAt() == null ? null : draft.getUpdatedAt().toString());
        return dto;
    }

    private String serialise(Map<String, Object> formData) {
        try {
            return objectMapper.writeValueAsString(formData);
        } catch (Exception e) {
            // Refused rather than stored as "{}". Persisting an empty object would report a successful
            // save of a draft that restores nothing — the precise dishonesty this work exists to remove.
            throw new IllegalArgumentException("The draft could not be stored: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialise(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Stored draft JSON could not be parsed: {}", e.getMessage());
            return Map.of();
        }
    }
}
