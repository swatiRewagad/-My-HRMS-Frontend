package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tier 0 of the assistance rail: what ONE officer was doing on ONE complaint, last time (Brief 21).
 *
 * <h2>This is per-user state, and the user is never a request field</h2>
 * The row is keyed on (OWNER_USER_ID, COMPLAINT_NUMBER) and the owner is always the identity
 * {@link com.hrms.cms.security.RequestIdentityResolver} derived from the token. There is deliberately no
 * finder on complaint number alone — see {@link com.hrms.cms.repository.AssistanceRailMemoryRepository}.
 * The failure being designed out is the one {@code StaffDraft} documents: the older draft surface took
 * its owner as a query parameter, so the ownership filter was a suggestion rather than a control.
 *
 * <h2>Why both keys are NORMALISED in Java rather than left to the database</h2>
 * {@code COMPLAINTS} and this table live in {@code utf8mb4_0900_ai_ci} on MySQL, so a unique key over
 * {@code (OWNER_USER_ID, COMPLAINT_NUMBER)} folds case — {@code jdoe} and {@code JDOE} would collide on
 * ONE row, which for Tier 0 means officer A reading officer B's memory. Oracle, which the deployed
 * profiles run, is case-SENSITIVE by default and would NOT collide. Left alone the two databases would
 * disagree about who owns a row.
 *   The fix cannot be a {@code columnDefinition} here: {@code "... COLLATE utf8mb4_bin"} is MySQL-only
 * syntax and {@code ddl-auto: update} would emit it against OracleDialect. So
 * {@link #normaliseOwner(String)} and {@link #normaliseComplaint(String)} are applied on EVERY read and
 * write path, which makes the folding explicit, identical on both engines, and visible here rather than
 * an emergent property of a charset.
 *
 * <p>{@code database/V112__assistance_rail.sql} ALSO declares those two columns {@code utf8mb4_bin}.
 * That is not a contradiction of the paragraph above — a hand-applied MySQL file may use MySQL syntax
 * where a JPA annotation may not — and it is a backstop rather than the control. It is only a backstop
 * because {@code ddl-auto: update} is what actually builds the schema in dev-local: a developer who
 * drops this table and lets Hibernate recreate it gets the {@code ai_ci} default and NO case-sensitive
 * key, at which point the normalisation in this class is the only thing standing between officer A and
 * officer B's unsaved text. Verified both ways: with the migration's collation, {@code alice} and
 * {@code ALICE} insert as two distinct rows; with normalisation, they converge to one key before the
 * database is ever consulted.
 *
 * <h2>Nothing here is inference</h2>
 * Two facts only: the section the officer last looked at and the text they left in a box. Both are
 * reported verbatim. No ranking, no summarisation and no judgement about whether they matter — that
 * would be the Tier 2 the brief excludes.
 */
@Entity
@Table(name = "ASSISTANCE_RAIL_MEMORY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ARM_OWNER_COMPLAINT",
                columnNames = {"OWNER_USER_ID", "COMPLAINT_NUMBER"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistanceRailMemory {

    /**
     * How much unsaved text is kept.
     *
     * <p>The rail shows a reminder that a draft exists, not the draft itself — {@code STAFF_DRAFT} is
     * where real in-progress form state belongs, and duplicating it here would give an officer two
     * places to resume from that can disagree. 4000 characters is enough for the reminder to be
     * recognisable and is inside {@code VARCHAR2(4000)}, Oracle's limit before a CLOB.
     */
    public static final int MAX_DRAFT_CHARS = 4000;

    /** Section keys are opaque to the server; it stores what the screen reports. */
    public static final int MAX_SECTION_CHARS = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Resolved from the token server-side, then lower-cased. Never read from a payload. */
    @Column(name = "OWNER_USER_ID", nullable = false, length = 200)
    private String ownerUserId;

    /** Upper-cased on write. Not a foreign key: a memory row must survive if the complaint is purged. */
    @Column(name = "COMPLAINT_NUMBER", nullable = false, length = 50)
    private String complaintNumber;

    /** The tab or section the officer last had open, as the screen names it. Nullable. */
    @Column(name = "LAST_SECTION", length = MAX_SECTION_CHARS)
    private String lastSection;

    /** Unsaved text the officer left behind, truncated to {@link #MAX_DRAFT_CHARS}. Nullable. */
    @Column(name = "DRAFT_TEXT", length = MAX_DRAFT_CHARS)
    private String draftText;

    /**
     * When this officer last had the complaint open.
     *
     * <p>Server-assigned. A client-supplied timestamp would let a screen claim a visit that never
     * happened, and "you were last here on..." is only useful if it is true.
     */
    @Column(name = "LAST_VIEWED_AT", nullable = false)
    private LocalDateTime lastViewedAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** Lower-case, trimmed. Returns null for a blank input so callers fail closed rather than key on "". */
    public static String normaliseOwner(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Upper-case, trimmed. Complaint numbers are generated upper-case; this makes that assumption explicit. */
    public static String normaliseComplaint(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Truncates rather than rejects: losing the tail of a reminder beats losing the reminder. */
    public static String clampDraft(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= MAX_DRAFT_CHARS ? trimmed : trimmed.substring(0, MAX_DRAFT_CHARS);
    }

    public static String clampSection(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.length() <= MAX_SECTION_CHARS ? trimmed : trimmed.substring(0, MAX_SECTION_CHARS);
    }

    @PrePersist
    void onCreate() {
        if (lastViewedAt == null) {
            lastViewedAt = LocalDateTime.now();
        }
        updatedAt = lastViewedAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
