package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per party impleaded into a complaint by the Ombudsman (UST544-546, 767).
 *
 * <p>WHY A TABLE RATHER THAN THE EXISTING CSV COLUMN. Impleading previously appended a name to
 * {@code COMPLAINTS.IMPLEADED_PARTIES}, a {@code VARCHAR(1000)} comma-joined string. The stories require
 * each impleaded entity to carry its own Nodal Officer record, its own compensation and clause coverage,
 * and its own completeness state that closure must validate. None of that can hang off a substring of a
 * shared column, and the {@code partyType} the UI already collected was being discarded because there was
 * nowhere to put it.
 *
 * <p>THE CSV COLUMN IS NOT DROPPED. This runs on a shared database under {@code ddl-auto: update}, where
 * six other sessions read the same schema and nothing reverts. {@code Complaint.impleadedParties} keeps
 * being maintained alongside these rows so existing readers and tests are unaffected; this table is the
 * authority for anything that needs per-party facts. Removing the column is a separate, coordinated change.
 *
 * <p>IMPLEADED PARTIES ARE FORMALLY DISTINCT FROM ADDITIONAL ENTITIES (UST767). An additional entity is an
 * optional co-respondent added for completeness; an impleaded party is a deliberate quasi-judicial act by
 * the Ombudsman that brings a new party into the proceeding and creates obligations towards it. They are
 * separate tables for that reason — but they share ONE six-per-complaint cap, enforced through
 * {@code RbioAdditionalEntityService.assertCapAllowsOneMore}, because the cap is about how many parties a
 * single complaint can carry in total.
 */
@Entity
@Table(name = "IMPLEADED_PARTY", indexes = {
        @Index(name = "idx_implead_complaint", columnList = "complaintNumber"),
        @Index(name = "idx_implead_status", columnList = "dataStatus")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpleadedParty {

    /** Required details are still outstanding, so closure must not finalise. */
    public static final String STATUS_INFORMATION_REQUIRED = "INFORMATION_REQUIRED";

    /** Everything closure needs for this party is present. */
    public static final String STATUS_COMPLETE = "COMPLETE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_number", nullable = false, length = 50)
    private String complaintNumber;

    @Column(name = "party_name", nullable = false, length = 200)
    private String partyName;

    /**
     * The classification the impleading screen already collected and the server previously discarded.
     * Nullable because the existing UI does not always supply it and losing the impleading itself would be
     * worse than losing the classification.
     */
    @Column(name = "party_type", length = 100)
    private String partyType;

    /** Resolved against REGULATED_ENTITIES where possible; null when the party is not a known RE. */
    @Column(name = "regulated_entity_id")
    private Long regulatedEntityId;

    /** The NODAL_OFFICER_RECORDS row raised for this party, when one could be created. */
    @Column(name = "nodal_officer_record_id")
    private Long nodalOfficerRecordId;

    /** INFORMATION_REQUIRED until this party's closure data is complete. */
    @Column(name = "data_status", nullable = false, length = 40)
    private String dataStatus;

    /** The closure clause cited for this specific party, which UST546 requires closure to verify. */
    @Column(name = "closure_clause", length = 40)
    private String closureClause;

    /** Compensation attributed to this party, if any. */
    @Column(name = "compensation_amount", precision = 15, scale = 2)
    private java.math.BigDecimal compensationAmount;

    /** The Ombudsman's stated reason for impleading — UST545 requires it in the history entry. */
    @Column(name = "implead_reason", length = 1000)
    private String impleadReason;

    @Column(name = "impleaded_by", length = 200)
    private String impleadedBy;

    @Column(name = "impleaded_by_role", length = 100)
    private String impleadedByRole;

    @Column(name = "impleaded_at", updatable = false)
    private LocalDateTime impleadedAt;

    @PrePersist
    protected void onCreate() {
        if (impleadedAt == null) {
            impleadedAt = LocalDateTime.now();
        }
        if (dataStatus == null || dataStatus.isBlank()) {
            // Defaults to incomplete on purpose: a new party starts out owing information, and defaulting
            // to COMPLETE would let a closure finalise over a party nobody had actually served.
            dataStatus = STATUS_INFORMATION_REQUIRED;
        }
    }

    /** Whether this party still blocks closure. */
    public boolean isIncomplete() {
        return !STATUS_COMPLETE.equalsIgnoreCase(dataStatus);
    }
}
