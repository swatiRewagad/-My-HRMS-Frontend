package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The legal-case dossier for one CEPC complaint, shown behind the "Legal Case" sidebar icon on the
 * complaint detail view.
 *
 * <p>One row per complaint — unlike {@link CepcContactPerson}, which is deliberately many-per-complaint.
 * The panel this backs has a single "Update Details" button and one set of fields, not an add/list UI, so
 * {@code complaintNumber} is unique here and every save is an upsert of that one row.
 *
 * <p>Scoped by complaint number rather than a foreign key to {@code COMPLAINTS}, matching
 * {@link CepcContactPerson} and {@code NodalOfficerRecord}: the service rejects a number that is not a
 * known complaint, and nothing here needs a delete rule this table's rows never trigger.
 *
 * <p>Nothing on this entity is server-mandated — it is a record-keeping form an officer fills in over
 * time, not a validated legal filing, so every field is nullable.
 */
@Entity
@Table(name = "CEPC_LEGAL_CASE",
    indexes = {
        @Index(name = "idx_cepc_legal_case_complaint", columnList = "complaintNumber", unique = true)
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CepcLegalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String complaintNumber;

    @Column(length = 100)
    private String caseNumber;

    @Column(length = 200)
    private String courtName;

    @Column(length = 500)
    private String partiesOfCase;

    @Column(length = 100)
    private String regionOfLegalTeam;

    private Boolean rbiFirstRespondent;

    private Boolean appearanceRequired;

    @Column(columnDefinition = "TEXT")
    private String subjectMatter;

    @Column(length = 200)
    private String advocateName;

    @Column(length = 200)
    private String assistantLegalAdvisor;

    private LocalDate nextHearingDate;

    @Column(columnDefinition = "TEXT")
    private String presentStatus;

    @Column(columnDefinition = "TEXT")
    private String actionTakenSoFar;

    @Column(columnDefinition = "TEXT")
    private String actionToBeTaken;

    @Column(columnDefinition = "TEXT")
    private String monetaryClaimDetails;

    @Column(length = 200)
    private String createdBy;

    @Column(length = 200)
    private String lastModifiedBy;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.lastModifiedAt == null) {
            this.lastModifiedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = LocalDateTime.now();
    }
}
