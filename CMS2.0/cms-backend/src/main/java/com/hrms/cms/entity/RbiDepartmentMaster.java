package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Internal RBI departments a complaint can be forwarded to from the RBIO Forward tab.
 *
 * <p>Distinct from {@code DEPARTMENT_ROUTING_MASTER}, which maps a regulated entity to the handling
 * department (RBIO/CEPC/CEPD) for intake routing and carries no contact address. This table is the
 * addressable list of departments with the mailbox a forward is actually sent to.
 */
@Entity
@Table(name = "RBI_DEPARTMENT_MASTER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbiDepartmentMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RBI's own department abbreviation, e.g. "DoS". Unique so the seeder can upsert on it. */
    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** Nullable: an admin may add a department before its mailbox is confirmed. */
    @Column(length = 150)
    private String email;

    private boolean active;

    private int sortOrder;
}
