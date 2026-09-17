package com.hrms.cms.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Intake-draft lifecycle. Deliberately NOT AppealStatus: an intake draft is pre-appeal and its
 * live values (ASSIGNED / SENT_TO_REVIEWER / APPROVED_ROUTED) have no AppealStatus counterpart.
 */
public enum DraftStatus {

    ASSIGNED,
    IN_PROGRESS,
    SENT_TO_REVIEWER,
    APPROVED_ROUTED,
    CONVERTED,
    DUPLICATE,
    IGNORED,
    REJECTED,
    DRAFT,
    PENDING_MANUAL_ENTRY;

    public static boolean isValid(String value) {
        return value != null && Arrays.stream(values()).anyMatch(s -> s.name().equals(value));
    }

    public static DraftStatus fromCode(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Unknown draft status: " + value);
        }
        return valueOf(value);
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }

    /** Drafts that still need human action; used by the queue and the stats endpoint. */
    public static final Set<DraftStatus> OPEN = Set.of(
            ASSIGNED, IN_PROGRESS, SENT_TO_REVIEWER, DRAFT, PENDING_MANUAL_ENTRY);
}
