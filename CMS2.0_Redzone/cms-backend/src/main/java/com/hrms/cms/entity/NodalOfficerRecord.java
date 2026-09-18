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

    @Column(length = 200)
    private String entityName;

    @Column(length = 200)
    private String nodalOfficerName;

    @Column(length = 200)
    private String pnoName;

    @Column(length = 100)
    private String designation;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String phone;

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

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "INFORMATION_REQUIRED";

    @Column(length = 200)
    private String assignedTo;

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
