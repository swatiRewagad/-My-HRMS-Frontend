package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * UST773: the NO/PNO that answers for a Regulated Entity <em>at a particular Ombudsman office</em>.
 *
 * <p><b>Why a new table rather than reusing an existing one.</b> Three candidates already exist and all
 * three are the wrong shape:
 * <ul>
 *   <li>{@code REGULATED_ENTITIES} carries exactly one NO and one PNO per entity. A bank the size of SBI
 *       does not route Ahmedabad complaints to the same officer as Guwahati, so a single pair per entity
 *       cannot express the requirement at all. That table stays as the national/default fallback.</li>
 *   <li>{@code DEPARTMENT_ROUTING_MASTER} has (entityName, targetOffice) and is tempting, but it exists to
 *       decide <em>which department/office</em> a complaint goes to. Hanging contact details off it would
 *       mean one row serving two unrelated purposes: an operator editing a phone number would be editing
 *       a routing rule, and deactivating a stale contact would silently stop routing complaints. Those
 *       two lifecycles genuinely differ and must not share a row.</li>
 *   <li>{@code OMBUDSMAN_OFFICE_MASTER} has no entity linkage and is a jurisdiction reference table.</li>
 * </ul>
 * A dedicated, narrow table is also what makes this safely shareable with the admin CRUD screen being
 * built elsewhere: the screen owns whole rows here and cannot break routing by touching them.
 *
 * <p><b>Join key.</b> {@code COMPLAINTS.entity_code} holds entity <em>names</em>, not short codes, and
 * {@code REGULATED_ENTITIES} has no code column. Matching on a raw name is unsafe — a fuzzy or
 * case-different match would hand one bank's officer contacts to a complaint against a different bank.
 * So the lookup key is {@code entityNameNormalized}, produced by {@link RegulatedEntity#normalize(String)},
 * and it is matched with <em>equality only</em>. That is deterministic: "State Bank of India" and
 * "STATE BANK OF INDIA." collapse to the same key, while "State Bank of India" and "State Bank of
 * Travancore" stay distinct. No LIKE, no prefix matching.
 *
 * <p><b>Office key.</b> {@code processingOffice} stores the office as the rest of the system already
 * names it — the {@code OMBUDSMAN_OFFICE_MASTER.officeName} / {@code OFFICE_CODE_MASTER.officeName} form
 * (e.g. "Mumbai I", "New Delhi I"), which is what the complaint-number generator resolves and what the
 * workflow layer passes around as {@code targetOffice}. A NULL office means "applies to every office for
 * this entity", letting an entity with one national desk be configured with a single row.
 */
@Entity
@Table(name = "ENTITY_OFFICE_NODAL_OFFICER",
    uniqueConstraints = {
        // One active contact set per (entity, office). Without this, two admin edits racing would leave
        // two rows and the resolver would pick arbitrarily — i.e. the officer who gets the complaint
        // would depend on row order.
        @UniqueConstraint(name = "uk_eono_entity_office",
                          columnNames = {"entityNameNormalized", "processingOffice"})
    },
    indexes = {
        @Index(name = "idx_eono_entity", columnList = "entityNameNormalized"),
        @Index(name = "idx_eono_office", columnList = "processingOffice")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EntityOfficeNodalOfficer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display form, as the operator typed it. Never used for matching. */
    @Column(nullable = false, length = 300)
    private String entityName;

    /** Lookup key. Always {@link RegulatedEntity#normalize(String)} of {@link #entityName}. */
    @Column(nullable = false, length = 300)
    private String entityNameNormalized;

    /**
     * Ombudsman office this contact set serves, in OFFICE_CODE_MASTER.officeName form.
     * NULL is deliberate and meaningful: it is the entity-wide default used when no office-specific
     * row exists.
     */
    @Column(length = 100)
    private String processingOffice;

    @Column(length = 200)
    private String nodalOfficerName;

    @Column(length = 100)
    private String nodalOfficerDesignation;

    @Column(length = 200)
    private String nodalOfficerEmail;

    @Column(length = 20)
    private String nodalOfficerPhone;

    @Column(length = 200)
    private String pnoName;

    @Column(length = 200)
    private String pnoEmail;

    @Column(length = 20)
    private String pnoPhone;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 100)
    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (entityNameNormalized == null && entityName != null) {
            entityNameNormalized = RegulatedEntity.normalize(entityName);
        }
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (entityName != null) {
            entityNameNormalized = RegulatedEntity.normalize(entityName);
        }
        updatedAt = LocalDateTime.now();
    }
}
