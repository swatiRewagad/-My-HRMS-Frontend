package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An immutable appeal order (decision). One row per REVISION.
 *
 * <p>Why not the {@code APPEALS.order_*} columns: PASS_ORDER wrote them in place with no terminal
 * guard, so an AA_SECRETARIAT caller could re-POST the action and silently replace the outcome,
 * summary, amount and date of an order that had already issued. An issued order is a legal
 * instrument; it cannot be edited away.
 *
 * <p>A correction therefore appends revision N+1 carrying {@code supersedesOrderId} and a mandatory
 * {@code correctionReason}, and stamps {@code supersededAt} on the prior revision. The operative
 * order is the single row with {@code supersededAt IS NULL}. Every column is {@code updatable=false}
 * except the supersession stamps, so JPA itself refuses to rewrite an issued order's substance.
 */
@Entity
@Table(name = "APPEAL_ORDER", indexes = {
    @Index(name = "idx_ao_appeal", columnList = "appealNumber"),
    @Index(name = "idx_ao_operative", columnList = "appealNumber,supersededAt"),
    @Index(name = "idx_ao_order_date", columnList = "orderDate")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppealOrder {

    public static final String OUTCOME_UPHELD = "UPHELD";
    public static final String OUTCOME_MODIFIED = "MODIFIED";
    public static final String OUTCOME_SET_ASIDE = "SET_ASIDE";
    public static final String OUTCOME_REMANDED = "REMANDED";
    public static final String OUTCOME_DISMISSED = "DISMISSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String appealNumber;

    /** 1 = the original order; 2+ = successive corrections. */
    @Column(nullable = false, updatable = false)
    private Integer revisionNo;

    /** The revision this one corrects. NULL on revision 1. */
    @Column(updatable = false)
    private Long supersedesOrderId;

    /** Mandatory when revisionNo > 1 -- a correction without a stated reason is not auditable. */
    @Column(length = 1000, updatable = false)
    private String correctionReason;

    /** UPHELD | MODIFIED | SET_ASIDE | REMANDED | DISMISSED */
    @Column(nullable = false, length = 30, updatable = false)
    private String outcome;

    @Lob
    @Column(nullable = false, updatable = false)
    private String orderSummary;

    @Column(precision = 15, scale = 2, updatable = false)
    private BigDecimal awardAmount;

    /**
     * The clause relied on, validated against CLOSURE_CLAUSE_MASTER rather than free text, so the
     * ground cited on an order is always a real clause of the Scheme in force.
     */
    @Column(length = 40, updatable = false)
    private String clauseCode;

    /** Narrative ground, where the clause alone does not express the reasoning. */
    @Column(length = 300, updatable = false)
    private String ground;

    @Column(nullable = false, length = 200, updatable = false)
    private String issuingAuthority;

    @Column(length = 50, updatable = false)
    private String issuingAuthorityRole;

    @Column(nullable = false, updatable = false)
    private LocalDateTime orderDate;

    /**
     * ED approval as it stood WHEN THE ORDER ISSUED, copied onto the order rather than read live off
     * the appeal. The appeal's columns can be re-registered; what approved this order cannot.
     */
    @Column(updatable = false)
    private Boolean edApprovalGiven;

    @Column(length = 200, updatable = false)
    private String edApprovalBy;

    @Column(updatable = false)
    private LocalDateTime edApprovalAt;

    /** NULL means this is the operative order. */
    private LocalDateTime supersededAt;

    private Long supersededById;

    @Column(nullable = false, length = 200, updatable = false)
    private String performedBy;

    @Column(length = 50, updatable = false)
    private String performedByRole;

    @Column(nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        if (performedAt == null) performedAt = LocalDateTime.now();
        if (orderDate == null) orderDate = performedAt;
    }
}
