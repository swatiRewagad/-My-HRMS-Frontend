package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A person who can hold RE-side work for one regulated entity — the candidate directory the
 * reassignment popup picks from (UST841).
 *
 * <p>Why this table exists rather than a Keycloak lookup: the {@code cms} realm contains no
 * {@code RE_*} users and no {@code RE_*} roles at all, so there is nothing to enumerate. Sourcing
 * candidates from the identity provider would return an empty list and the feature would appear
 * built but be permanently unusable. Realm seeding remains the prerequisite for real SSO; this table
 * is the master-data source of record for who exists and what they may hold.
 *
 * <p>{@code entityCode} is the scoping key, deliberately not the free-text {@code entityName} that
 * {@link NodalOfficerRecord} historically joined on. {@code RequestIdentity} only ever supplies an
 * entity <em>code</em>, so a name-based join cannot be authorised server-side — two entities with
 * similar names would silently see each other's officers.
 *
 * <p>{@code reRole} is a column, not a Java enum or a hardcoded list: the realm has no
 * "Contact Person" role to mirror, and the NO-vs-Contact-Person distinction is master data that
 * operations staff must be able to correct without a redeploy.
 */
@Entity
@Table(name = "ENTITY_USERS", indexes = {
    @Index(name = "idx_eu_entity_code", columnList = "entityCode"),
    @Index(name = "idx_eu_user_id", columnList = "userId"),
    @Index(name = "idx_eu_entity_active", columnList = "entityCode,active"),
    @Index(name = "idx_eu_role", columnList = "reRole")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EntityUser {

    /** NODAL_OFFICER — holds complaints directly. */
    public static final String ROLE_NODAL_OFFICER = "NODAL_OFFICER";
    /** CONTACT_PERSON — a named contact who may receive work but is not the nodal officer of record. */
    public static final String ROLE_CONTACT_PERSON = "CONTACT_PERSON";
    /** PNO — Principal Nodal Officer, approves reassignments within the entity. */
    public static final String ROLE_PNO = "PNO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The login id this person authenticates as; matched against RequestIdentity.getUserId(). */
    @Column(nullable = false, length = 200)
    private String userId;

    @Column(nullable = false, length = 50)
    private String entityCode;

    @Column(length = 200)
    private String displayName;

    @Column(length = 200)
    private String email;

    @Column(length = 100)
    private String designation;

    /** NODAL_OFFICER | CONTACT_PERSON | PNO — seeded from master data, never hardcoded in Java. */
    @Column(nullable = false, length = 30)
    private String reRole;

    /**
     * Inactive users stay in the table so historical reassignments still resolve to a name, but are
     * never offered as a reassignment target. Deleting the row instead would leave the history
     * report showing a bare user id (UST844).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Optional territory label. There is no region hierarchy anywhere in the entity model, so this
     * is a flat, nullable annotation used only for display and optional narrowing — candidate
     * authorisation is by entityCode alone. Inventing a hierarchy here would let the popup imply a
     * scoping rule the server does not actually enforce.
     */
    @Column(length = 100)
    private String territory;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = LocalDateTime.now();
    }
}
