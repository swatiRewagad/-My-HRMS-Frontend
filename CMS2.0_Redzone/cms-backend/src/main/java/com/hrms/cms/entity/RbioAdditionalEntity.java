package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An additional regulated entity attached to a complaint (UST487-495).
 *
 * <p>A complaint names one primary entity, but a grievance can implicate several — a card transaction
 * disputed across an issuer and an acquirer, for instance. Up to SIX additional entities may be added.
 *
 * <p><b>Why a table and not a delimited column.</b> {@code Complaint.impleadedParties} is a
 * comma-joined VARCHAR(1000) that {@code FX_IMPLEAD} appends to. That cannot carry the branch, type and
 * category this form collects, cannot be counted reliably for a six-cap (a party whose name contains a
 * comma would count twice), and cannot record who added a row or when. The cap is a rule about a count,
 * so the count has to be trustworthy.
 *
 * <p><b>The six-cap is enforced in the SERVICE, not here.</b> A row limit is not expressible as a column
 * constraint, and enforcing it only in the browser — as
 * {@code rbio-add-entity.component.ts:20} currently does — is not a control at all.
 */
@Entity
@Table(name = "RBIO_ADDITIONAL_ENTITY", indexes = {
        @Index(name = "IDX_RBIO_ADDL_ENTITY_CN", columnList = "COMPLAINT_NUMBER")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioAdditionalEntity {

    /** The maximum number of ADDITIONAL entities per complaint, over and above the primary entity. */
    public static final int MAX_PER_COMPLAINT = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_NUMBER", nullable = false, length = 50)
    private String complaintNumber;

    @Column(name = "ENTITY_NAME", nullable = false, length = 200)
    private String entityName;

    @Column(name = "ENTITY_BRANCH", length = 200)
    private String entityBranch;

    @Column(name = "ENTITY_TYPE", length = 100)
    private String entityType;

    @Column(name = "ENTITY_CATEGORY", length = 100)
    private String entityCategory;

    /**
     * The REGULATED_ENTITIES row this refers to, when the name resolved to one.
     *
     * <p>Nullable because the form accepts a free-typed name: refusing an entity absent from the master
     * table would block a legitimate complaint against a newly licensed entity. The link is recorded when
     * it can be established rather than fabricated when it cannot.
     */
    @Column(name = "REGULATED_ENTITY_ID")
    private Long regulatedEntityId;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "CREATED_BY_ROLE", length = 50)
    private String createdByRole;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
