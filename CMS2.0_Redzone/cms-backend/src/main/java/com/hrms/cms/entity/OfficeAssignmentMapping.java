package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single "this subject goes to that officer at this office" rule (UST469, UST471, UST472).
 *
 * <p>One table serves both the entity->officer and category->officer strategies, keyed by
 * {@link #mappingType}, because the two are structurally identical: an office, a subject key, and a
 * target officer. Splitting them into two tables would duplicate the same admin CRUD, the same
 * uniqueness rule and the same audit trail twice over.
 *
 * <p><b>The subject key is stored, not referenced.</b> There is no foreign key to REGULATED_ENTITIES or
 * COMPLAINT_CATEGORIES, for two different reasons. Entities have no stable code column at all — the
 * complaint itself carries a free-text entity NAME in {@code entity_code} — so the only join key
 * available is a normalised name. Categories do have ids, but they are stored here as strings so one
 * column can serve both types. Normalisation on write is what makes the entity lookup reliable; see
 * {@link #normaliseSubject(String)}.
 */
@Entity
@Table(name = "OFFICE_ASSIGNMENT_MAPPING",
    uniqueConstraints = {
        // UST468's "exactly one active logic" has a counterpart here: one subject must not resolve to two
        // officers at the same office. Without this key an admin could add a second row for the same bank
        // and the winner would be whichever the database returned first — so the same complaint could land
        // on different officers on different pods.
        @UniqueConstraint(name = "uk_oam_office_type_subject",
                columnNames = {"officeId", "mappingType", "subjectKey"})
    },
    indexes = {
        @Index(name = "idx_oam_lookup", columnList = "officeId,mappingType,subjectKey,active"),
        @Index(name = "idx_oam_officer", columnList = "targetOfficerId")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficeAssignmentMapping {

    public static final String TYPE_ENTITY = "ENTITY";
    public static final String TYPE_CATEGORY = "CATEGORY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** OFFICE_CODE_MASTER.OFFICE_CODE, matching OFFICE_ASSIGNMENT_STRATEGY.officeId. */
    @Column(nullable = false, length = 20)
    private String officeId;

    @Column(nullable = false, length = 20)
    private String mappingType;

    /**
     * The normalised regulated-entity name, or the complaint category id as a string.
     *
     * <p>Entity names are normalised on write and on read so that "H D F C Bank." and "HDFC BANK" are the
     * same key. Storing the raw name would make the mapping silently miss on any punctuation or spacing
     * difference, and a missed mapping falls through to the Ombudsman Admin — so the complaint would
     * quietly land on an admin instead of the named officer, with nothing obviously wrong.
     */
    @Column(nullable = false, length = 250)
    private String subjectKey;

    /** Kept for display, so an admin screen can show what was typed rather than the normalised form. */
    @Column(length = 250)
    private String subjectLabel;

    /**
     * The Keycloak username of the officer this subject routes to.
     *
     * <p>A Keycloak username, NOT a WF_OFFICER_POOL user_id: those two vocabularies do not currently
     * agree (the pool is seeded with rbio.officer1..4 while the realm has rbio.officer,
     * rbio_officer_002 and so on), and an officer who cannot log in cannot act on the complaint.
     */
    @Column(nullable = false, length = 200)
    private String targetOfficerId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 100)
    private String updatedBy;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Upper-cases and strips everything that is not a letter, digit or single space.
     *
     * <p>Mirrors {@code RegulatedEntity.normalize} so a mapping keyed on an entity name matches the same
     * way the routing lookup does. Kept as a static helper rather than a database function because the
     * same normalisation has to happen on write, and MySQL and Oracle do not agree on regex syntax.
     */
    public static String normaliseSubject(String raw) {
        if (raw == null) return "";
        return raw.toUpperCase()
                .replaceAll("[^A-Z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
