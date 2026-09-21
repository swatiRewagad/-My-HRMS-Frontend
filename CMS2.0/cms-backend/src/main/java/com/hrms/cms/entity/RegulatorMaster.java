package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * External statutory regulators a complaint can be forwarded to from the RBIO Forward tab when the
 * subject matter falls outside RBI's remit. Kept separate from {@link RbiDepartmentMaster} because a
 * regulator is a different organisation entirely, whereas an RBI department is internal.
 */
@Entity
@Table(name = "REGULATOR_MASTER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegulatorMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short form the officer recognises, e.g. "SEBI". Unique so the seeder can upsert on it. */
    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** Nullable: an admin may add a regulator before its grievance mailbox is confirmed. */
    @Column(length = 150)
    private String email;

    private boolean active;

    private int sortOrder;
}
