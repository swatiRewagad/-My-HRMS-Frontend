package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "NODAL_OFFICER_RECORDS", indexes = {
    @Index(name = "idx_no_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_no_entity", columnList = "entityName"),
    @Index(name = "idx_no_status", columnList = "status"),
    @Index(name = "idx_no_last_modified", columnList = "lastModifiedAt"),
    @Index(name = "idx_no_entity_code_assigned", columnList = "entityCode,assignedTo"),
    @Index(name = "idx_no_entity_code_status", columnList = "entityCode,status")
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
