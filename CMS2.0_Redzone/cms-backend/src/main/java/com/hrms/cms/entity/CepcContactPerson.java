package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A person at the regulated entity that CEPC has actually been dealing with on one complaint.
 *
 * <p>Deliberately a table of its own rather than extra rows on {@code NODAL_OFFICER_RECORDS}, which was the
 * first idea. That table carries {@code uk_no_complaint} — one row per complaint — and a unique
 * {@code record_number} derived from the complaint number, so a second row per complaint is rejected twice
 * over. Beyond the keys, eight queries read it with no notion of a row that is not a nodal record: RBIO's
 * by-complaint endpoint, the dashboard's NO/PNO columns, the auto-create existence check, the RE
 * reassignment workload counts and the staleness sweep that chases entities. Contact rows would surface in
 * all of them.
 *
 * <p>Distinct from the nodal officer in kind, not just in storage. The nodal officer is who the entity has
 * <em>designated</em>, drawn from master data and snapshotted onto the record when the complaint is
 * forwarded; a contact person is whoever the dealing officer is in touch with, entered by hand, and there
 * may be several per complaint. So: no unique constraint on {@code complaintNumber}, and no defaulting from
 * the entity master.
 */
@Entity
@Table(name = "CEPC_CONTACT_PERSONS",
    indexes = {
        // The only read this table serves is "the contacts for one complaint, oldest first".
        @Index(name = "idx_cepc_cp_complaint", columnList = "complaintNumber")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CepcContactPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The complaint these contacts belong to, by number rather than by a foreign key to {@code COMPLAINTS}.
     *
     * <p>Matches how {@code NodalOfficerRecord} scopes itself, so the two can be resolved side by side from
     * the one complaint number the Contact Entity tab already has.
     */
    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String designation;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String phone;

    /** Free text for how this contact came to be recorded — a call, an email thread, a site visit. */
    @Column(length = 500)
    private String remarks;

    /**
     * Who entered the contact, and who last touched it.
     *
     * <p>Kept on the row rather than left to the audit trail: the Contact Entity tab shows "added by" so a
     * reviewer can tell a dealing officer's own contact from one carried over, and reading that off an audit
     * query per row would be a join the list does not otherwise need.
     */
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
