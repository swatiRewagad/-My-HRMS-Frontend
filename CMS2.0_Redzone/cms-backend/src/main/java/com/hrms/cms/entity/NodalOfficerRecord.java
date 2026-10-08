package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "NODAL_OFFICER_RECORDS",
    uniqueConstraints = {
        // UST569 auto-creates a record on complaint registration, so two concurrent registrations for
        // the SAME complaint — a retried POST, a duplicated Kafka delivery — would both find "no record"
        // and both insert. Duplicate rows each get their own 15/20-day staleness clock, so the entity is
        // chased twice for one complaint and a reassignment could move only one of them. There is no
        // other guard: the create-if-absent check cannot be atomic without this key.
        // Safe to add now because the table is empty; it would not be once rows exist.
        @UniqueConstraint(name = "uk_no_complaint", columnNames = {"complaintNumber"})
    },
    indexes = {
    @Index(name = "idx_no_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_no_entity", columnList = "entityName"),
    @Index(name = "idx_no_status", columnList = "status"),
    @Index(name = "idx_no_last_modified", columnList = "lastModifiedAt"),
    @Index(name = "idx_no_entity_code_assigned", columnList = "entityCode,assignedTo"),
    @Index(name = "idx_no_entity_code_status", columnList = "entityCode,status"),
    @Index(name = "idx_no_processing_office", columnList = "processingOffice")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NodalOfficerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic lock guarding concurrent reassignment (UST839).
     *
     * <p>Two PNOs looking at the same stale list must not both succeed: without this, the second
     * write silently overwrites the first and the record ends up with an owner neither of them
     * chose, while both see a success message. The client echoes the version it read back on the
     * reassign call, and a mismatch is reported as a conflict naming the record.
     *
     * <p>This is the first {@code @Version} column in cms-backend. Existing rows start NULL;
     * Hibernate treats a NULL version as unversioned and seeds it on first write, so no backfill is
     * required and legacy writers (NotificationScheduledTasks) keep working unchanged.
     */
    @Version
    private Long version;

    /**
     * The scoping key for server-side authorisation, added because {@code entityName} is free text.
     *
     * <p>{@code RequestIdentity} supplies an entity <em>code</em> only, so authorising an RE caller
     * against {@code entityName} is not possible — near-identical entity names would let one entity
     * read and reassign another's records. Nullable because historical rows predate the column; a
     * row with no code is never visible to an entity-scoped caller.
     */
    @Column(length = 50)
    private String entityCode;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    /**
     * The record's own identifier, shown in the Contact Entity list and used to key its comment thread.
     *
     * <p>Derived from the complaint number rather than drawn from a sequence, which is sound here because
     * {@code uk_no_complaint} makes the relationship exactly one record per complaint — so the complaint
     * number is already a unique key for the record, and a separate counter would only add a second
     * identifier to keep in step.
     *
     * <p><b>Slashes are replaced.</b> It appears as a path segment in
     * {@code /api/v1/complaints/nodal-records/{recordNumber}/comments}, and the client interpolates it
     * unencoded, so a slash would split the segment and the request would match no mapping at all.
     *
     * <p>Nullable because rows written before this column existed have none; those rows simply will not be
     * found by record number, which is preferable to inventing an identifier for them at read time and
     * having it change the next time the derivation does.
     */
    @Column(length = 60, unique = true)
    private String recordNumber;

    @Column(length = 200)
    private String entityName;

    @Column(length = 200)
    private String nodalOfficerName;

    @Column(length = 200)
    private String pnoName;

    /**
     * The NODAL officer's designation, email and phone.
     *
     * <p>Unprefixed because they predate the Contact Entity list, which asks for them as
     * {@code noDesignation}/{@code noEmail}/{@code noMobile}. Renamed at the DTO boundary rather than here:
     * these three already carry data and several writers populate them, so a rename would be a migration
     * plus a sweep for the sake of a naming convention the wire format can supply on its own.
     */
    @Column(length = 100)
    private String designation;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String phone;

    /** The PRINCIPAL nodal officer's contact. The entity had a name for them but no way to reach them. */
    @Column(length = 200)
    private String pnoEmail;

    @Column(length = 20)
    private String pnoMobile;

    /**
     * UST773: the Ombudsman office whose (entity, office) mapping supplied these contacts.
     *
     * <p>Nullable on purpose, twice over. Some complaint-creation paths have no office in scope, and a
     * record with an unknown office is still worth having — no record at all means nobody is chasing the
     * entity. And the shared dev database runs ddl-auto=update, where adding a NOT NULL column would
     * fail against existing rows and would be permanent for every other session.
     */
    @Column(length = 100)
    private String processingOffice;

    /**
     * UST780: the date this entity must answer the current communication by.
     *
     * <p>Held here as well as on the complaint because the staleness escalations read the NO record, and a
     * deadline only on the complaint would be invisible to the job meant to chase the entity. The two are
     * written together whenever a notice or information request sets one.
     *
     * <p>A LocalDate, not a timestamp: the officer picks a calendar day and the Scheme speaks in days, so
     * storing a time would invent a precision nobody decided. Nullable — most complaints have no
     * outstanding communication.
     */
    @Column(name = "re_response_deadline")
    private java.time.LocalDate reResponseDeadline;

    /** Which communication set the deadline — a 13(1) Notice, or an information request. */
    @Column(length = 50)
    private String deadlineCommunication;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "INFORMATION_REQUIRED";

    @Column(length = 200)
    private String assignedTo;

    // ── The dealing officer's assessment of the record, saved when it is forwarded to the entity ──
    //
    // Every column below is nullable, and not only because ddl-auto=update on the shared dev database
    // forbids anything else: a record is created automatically when the complaint is registered, long
    // before an officer has looked at it, so an unassessed record is the normal state rather than an
    // incomplete one. A zero default on the amounts would be worse than null — it would assert that the
    // officer had considered compensation and settled on nothing.

    /** How long this record has been with the entity, for the list's ageing column. */
    private Integer slaDays;

    /** By when the entity must confirm it has complied with the advisory. */
    private java.time.LocalDate advisoryComplianceDate;

    @Column(precision = 15, scale = 2)
    private java.math.BigDecimal disputeAmount;

    /** Compensation for actual loss, and separately for mental agony — the Scheme caps them differently. */
    @Column(precision = 15, scale = 2)
    private java.math.BigDecimal compensationLoss;

    @Column(precision = 15, scale = 2)
    private java.math.BigDecimal compensationMental;

    private java.time.LocalDate awardImplementationDate;

    private java.time.LocalDate awardAcceptanceDate;

    /**
     * The Clause 13(1) compliance date, set once when the notice is actually issued.
     *
     * <p>Stored rather than computed. It was previously rendered client-side as "today + 15 days", which
     * moved every time the page was opened, so the date the entity was told to comply by was not the date
     * anybody could later read off the screen.
     */
    private java.time.LocalDate notice131ComplyDate;

    /** When this record was forwarded to the entity. Null means it has not been. */
    private LocalDateTime forwardedToReAt;

    /** The office designated to handle the entity, as distinct from the office processing the complaint. */
    @Column(length = 100)
    private String designatedOffice;

    /** {@code Yes}/{@code No} — whether the grievance concerns an ATM transaction. */
    @Column(length = 10)
    private String atmComplaint;

    @Column(length = 120)
    private String moduleName;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    /** {@code NOR-} plus the complaint number with its slashes replaced. See {@link #recordNumber}. */
    public static String recordNumberFor(String complaintNumber) {
        return complaintNumber == null ? null : "NOR-" + complaintNumber.replace('/', '-');
    }

    /**
     * Assigns only what is still absent.
     *
     * <p>The dates used to be overwritten unconditionally, which flattened any record created with a date
     * of its own — a seeded or migrated record — to the moment of insert, so a record could appear newer
     * than the complaint it belongs to and the staleness clock started from the wrong day.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.lastModifiedAt == null) {
            this.lastModifiedAt = this.createdAt;
        }
        if (this.recordNumber == null) {
            this.recordNumber = recordNumberFor(this.complaintNumber);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = LocalDateTime.now();
    }
}
