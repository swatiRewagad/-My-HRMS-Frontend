package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An in-progress staff form, owned by the user who saved it (UST673, UST674).
 *
 * <h2>Why a new table rather than reusing EMAIL_DRAFTS</h2>
 * UST674 requires "save in draft" at five milestones — Register, Assessment, Conciliation, Forward and
 * Final Decision. {@code EMAIL_DRAFTS} is an email-INTAKE record: keyed to an inbound message and
 * carrying ~60 intake-specific columns. Three of the five milestones have no inbound email at all, so
 * they have no row to borrow.
 *
 * <p>The Register milestone's existing "save draft" is worse than missing: it posts to
 * {@code /workflow/rbio/create-complaint}, and {@code WorkflowController} overrides the submitted
 * {@code status: 'DRAFT'} to {@code "assigned"}, applies an SLA clock and writes a CREATED timeline row.
 * Clicking "save draft" created a live, assigned complaint with a real complaint number.
 *
 * <h2>OWNER_USER_ID is written from the token, never from the payload</h2>
 * "Visible only to the user who saved it" cannot be built on a value the caller supplies. The existing
 * draft listing took its owner as a query parameter and returned EVERY draft in the system when the
 * parameter was omitted, so the filter was a suggestion rather than a control.
 */
@Entity
@Table(name = "STAFF_DRAFT",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_STAFF_DRAFT_OWNER_MILESTONE",
                columnNames = {"MILESTONE", "OWNER_USER_ID", "COMPLAINT_NUMBER"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffDraft {

    /** The five UST674 milestones. */
    public enum Milestone {
        REGISTER, ASSESSMENT, CONCILIATION, FORWARD, FINAL_DECISION
    }

    /** How the draft came to be saved. An AUTOSAVE row is discarded on save-and-proceed (UST673). */
    public enum SaveSource {
        MANUAL, AUTOSAVE
    }

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "MILESTONE", nullable = false, length = 30)
    private String milestone;

    /** Resolved from the JWT server-side. */
    @Column(name = "OWNER_USER_ID", nullable = false, length = 200)
    private String ownerUserId;

    /**
     * Null for {@code REGISTER} — no complaint exists at that milestone yet.
     *
     * <p>Both MySQL and Oracle treat NULLs as distinct in a unique constraint, so REGISTER drafts are
     * not constrained by the unique key above; that path is keyed on owner alone in the service.
     */
    @Column(name = "COMPLAINT_NUMBER", length = 50)
    private String complaintNumber;

    /**
     * The in-progress field values, restored verbatim on resume.
     *
     * <p>{@code columnDefinition} is explicit and load-bearing. With a bare {@code @Lob String} this
     * dialect resolves the type to {@code tinytext} — 255 bytes — and {@code ddl-auto=update} duly issued
     * {@code modify column form_data_json tinytext} against the LONGTEXT the migration created. A staff
     * draft holding a complaint description would have been silently truncated at 255 characters, which
     * is the kind of loss that only shows up when someone tries to resume. Caught in the startup log, not
     * theorised.
     */
    @Column(name = "FORM_DATA_JSON", nullable = false, columnDefinition = "LONGTEXT")
    private String formDataJson;

    @Column(name = "DRAFT_STATUS", nullable = false, length = 20)
    @Builder.Default
    private String draftStatus = STATUS_DRAFT;

    @Column(name = "SAVE_SOURCE", nullable = false, length = 20)
    @Builder.Default
    private String saveSource = SaveSource.MANUAL.name();

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Transient
    public boolean isAutosaved() {
        return SaveSource.AUTOSAVE.name().equalsIgnoreCase(saveSource);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
