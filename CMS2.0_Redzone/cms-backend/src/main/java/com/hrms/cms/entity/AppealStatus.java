package com.hrms.cms.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The status vocabulary for an appeal or representation.
 *
 * Appeal.status remains a String column for backward compatibility — existing rows and the CEPC/RBIO
 * convention both use lowercase snake_case, and re-typing the column would break them. This enum is
 * the validation and lookup authority for that column: the vocabulary was previously implicit,
 * scattered across a switch statement, with no way to reject a typo.
 *
 * There is deliberately no DRAFT here. It was added for email- and letter-origin records, but nothing
 * ever set it — intake keeps its own DraftStatus enum for pre-registration records, and both creation
 * paths write "filed" directly. An unreachable status in the authoritative vocabulary is worse than
 * absent: it invites the UI to gate on a state the server can never produce, which is exactly the class
 * of bug that left AA officers looking at screens with no available actions.
 */
public enum AppealStatus {

    FILED("filed"),
    UNDER_REVIEW("under_review"),
    HEARING_SCHEDULED("hearing_scheduled"),
    ORDER_PASSED("order_passed"),
    CLOSED("closed"),
    REJECTED("rejected");

    private final String code;

    AppealStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** Statuses in which no further ordinary workflow action is possible. */
    public static final List<String> TERMINAL_CODES =
            List.of(CLOSED.code, REJECTED.code, ORDER_PASSED.code);

    /** Statuses treated as "open" by the AA home views. */
    public static final Set<String> OPEN_CODES =
            Set.of(FILED.code, UNDER_REVIEW.code, HEARING_SCHEDULED.code);

    public static AppealStatus fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Appeal status is required");
        }
        String normalized = code.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(s -> s.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown appeal status: " + code));
    }

    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.trim().toLowerCase();
        return Arrays.stream(values()).anyMatch(s -> s.code.equals(normalized));
    }
}
