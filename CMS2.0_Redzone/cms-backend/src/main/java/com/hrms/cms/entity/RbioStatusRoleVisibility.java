package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Which status filters a given role sees, and in what order (UST426-433).
 *
 * <p>A child table rather than a boolean column per role: nine roles would be nine columns and a
 * schema change per new rank, and — the deciding reason — {@code displayOrder} is per-role. A Dealing
 * Official and an Ombudsman want the same statuses in a different order, which one shared column on
 * the master cannot express.
 */
@Entity
@Table(name = "RBIO_STATUS_ROLE_VISIBILITY")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioStatusRoleVisibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "STATUS_CODE", nullable = false, length = 50)
    private String statusCode;

    @Column(name = "ROLE_NAME", nullable = false, length = 50)
    private String roleName;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    @Builder.Default
    private Integer displayOrder = 999;

    /**
     * The tab this role lands on with no filter chosen. At most one per role — not expressible as a
     * unique key, so the seeder asserts it and the service falls back to lowest displayOrder.
     */
    @Column(name = "IS_DEFAULT", nullable = false, length = 1)
    @Builder.Default
    private String isDefault = "N";

    public boolean isDefaultFilter() {
        return "Y".equalsIgnoreCase(isDefault);
    }
}
