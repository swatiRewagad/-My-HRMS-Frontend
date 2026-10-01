package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Maps the internal ComplaintStatus values to 4 citizen-facing stages
 * for the public Track Complaint view.
 *
 * Stage 1 "Registered"           : pending, new
 * Stage 2 "Under Review (RBI)"   : assigned, in_progress, under_review, escalated
 * Stage 3 "With Bank"            : approved, sent_back
 * Stage 4 "Closed"               : resolved, rejected, closed, withdrawn
 */
public final class CitizenStageMapper {

    private CitizenStageMapper() {}

    private static final Map<Integer, String> STAGE_LABELS = Map.of(
            1, "Registered",
            2, "Under Review (RBI)",
            3, "With Bank",
            4, "Closed"
    );

    private static final Set<String> STAGE_1_STATUSES = Set.of("pending", "new");
    private static final Set<String> STAGE_2_STATUSES = Set.of("assigned", "in_progress", "under_review", "escalated");
    private static final Set<String> STAGE_3_STATUSES = Set.of("approved", "sent_back");
    private static final Set<String> STAGE_4_STATUSES = Set.of("resolved", "rejected", "closed", "withdrawn");

    /**
     * Determines which citizen-facing stage a given internal status belongs to.
     */
    private static int stageForStatus(String status) {
        if (status == null) return 1;
        String s = status.toLowerCase().trim();
        if (STAGE_1_STATUSES.contains(s)) return 1;
        if (STAGE_2_STATUSES.contains(s)) return 2;
        if (STAGE_3_STATUSES.contains(s)) return 3;
        if (STAGE_4_STATUSES.contains(s)) return 4;
        return 1; // fallback
    }

    /**
     * Computes the 4-stage timeline map from the complaint and its raw timeline entries.
     *
     * @param complaint the complaint entity
     * @param timeline  the list of ComplaintTimeline entries (any order)
     * @return a map with keys "currentStage" (int) and "stages" (list of stage maps)
     */
    public static Map<String, Object> mapToStages(Complaint complaint, List<ComplaintTimeline> timeline) {
        int currentStage = stageForStatus(complaint.getStatus());

        // For each stage, find the earliest timeline entry whose toStatus transitions INTO that stage.
        // Stage 1 date falls back to complaint.createdAt since the initial filing may not have a
        // toStatus of "pending" in the timeline.
        Map<Integer, LocalDateTime> stageFirstDate = new LinkedHashMap<>();

        // Default: stage 1 always gets the complaint creation date
        stageFirstDate.put(1, complaint.getCreatedAt());

        if (timeline != null) {
            for (ComplaintTimeline entry : timeline) {
                if (entry.getToStatus() == null || entry.getPerformedAt() == null) continue;
                int entryStage = stageForStatus(entry.getToStatus());
                LocalDateTime existing = stageFirstDate.get(entryStage);
                if (existing == null || entry.getPerformedAt().isBefore(existing)) {
                    stageFirstDate.put(entryStage, entry.getPerformedAt());
                }
            }
        }

        List<Map<String, Object>> stages = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("stage", i);
            stage.put("label", STAGE_LABELS.get(i));

            String stageStatus;
            if (i < currentStage) {
                stageStatus = "completed";
            } else if (i == currentStage) {
                stageStatus = "current";
            } else {
                stageStatus = "pending";
            }
            stage.put("status", stageStatus);

            LocalDateTime date = stageFirstDate.get(i);
            stage.put("date", date != null ? date.toString() : null);

            stages.add(stage);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentStage", currentStage);
        result.put("stages", stages);
        return result;
    }
}
