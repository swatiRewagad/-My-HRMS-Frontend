package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One field-level override of a previous role's proposal (UST474-475, 479-480, 483-484, 639-642).
 *
 * <p>The ladder auto-populates the next role's proposed action and clause from the previous role's
 * selection, and the next role may EDIT them. This table preserves what the earlier role actually chose,
 * so an Ombudsman's departure from a Dealing Official's recommendation remains visible instead of being
 * overwritten in place.
 *
 * <p><b>Append-only, and never updated.</b> The point of the record is that the original survives; an
 * UPDATE path would defeat it. {@code RbioActionOverrideService} exposes no mutator.
 *
 * <p><b>Scope boundary.</b> This is FIELD-level history. Status-change history lives in
 * {@code ComplaintTimeline} and is owned by S6. Both surface in the same History tab; they are not the
 * same record, and neither is derivable from the other — a status change is not a field edit, and an
 * overridden clause on an unchanged status produces no timeline row at all.
 */
@Entity
@Table(name = "RBIO_ACTION_OVERRIDE", indexes = {
        @Index(name = "IDX_RBIO_OVERRIDE_CN", columnList = "COMPLAINT_NUMBER")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioActionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_NUMBER", nullable = false, length = 50)
    private String complaintNumber;

    /** The field that was changed, e.g. "Proposed Action" or "Proposed Clause". */
    @Column(name = "FIELD_NAME", nullable = false, length = 100)
    private String fieldName;

    /**
     * The value before the edit.
     *
     * <p>Nullable: the FIRST role to set a field overrides nothing, and recording an empty string as
     * though it were a prior decision would invent a proposal nobody made.
     */
    @Column(name = "OLD_VALUE", length = 500)
    private String oldValue;

    @Column(name = "NEW_VALUE", length = 500)
    private String newValue;

    @Column(name = "OVERRIDDEN_BY", length = 100)
    private String overriddenBy;

    @Column(name = "OVERRIDDEN_BY_ROLE", length = 50)
    private String overriddenByRole;

    @Column(name = "OVERRIDDEN_AT", nullable = false)
    private LocalDateTime overriddenAt;
}
