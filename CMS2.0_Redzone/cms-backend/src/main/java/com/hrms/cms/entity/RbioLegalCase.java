package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Court proceedings recorded against a complaint (UST553).
 *
 * <p>One row per complaint: the question the record answers is "is this matter before a court", which has
 * a single answer at a time. Updates replace the details rather than appending, and the last editor is
 * stamped.
 *
 * <p><b>Why this matters beyond record-keeping.</b> A matter that is sub-judice constrains what the
 * Ombudsman may do with it, and a statutory guard already exists for that
 * ({@code RbioOrderStatutoryGuardService}). Until now the legal-case screen wrote to an endpoint that did
 * not exist, so the guard could never see a court case the staff had recorded — the UI showed the case
 * back from its own local state and persisted nothing.
 */
@Entity
@Table(name = "RBIO_LEGAL_CASE", indexes = {
        @Index(name = "IDX_RBIO_LEGAL_CASE_CN", columnList = "COMPLAINT_NUMBER")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioLegalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique: one legal-case record per complaint. */
    @Column(name = "COMPLAINT_NUMBER", nullable = false, unique = true, length = 50)
    private String complaintNumber;

    @Column(name = "CASE_NUMBER", nullable = false, length = 100)
    private String caseNumber;

    @Column(name = "COURT_NAME", nullable = false, length = 200)
    private String courtName;

    /** Free text rather than an enum — the vocabulary of court statuses is not ours to fix. */
    @Column(name = "CASE_STATUS", length = 50)
    private String caseStatus;

    @Column(name = "FILING_DATE")
    private LocalDate filingDate;

    @Column(name = "NEXT_HEARING_DATE")
    private LocalDate nextHearingDate;

    @Column(name = "REMARKS", length = 2000)
    private String remarks;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY", length = 100)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
