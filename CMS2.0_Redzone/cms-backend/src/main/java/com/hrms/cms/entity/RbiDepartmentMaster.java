package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An internal RBI department a complaint may be forwarded to (UST761, 534, 527-528).
 *
 * <p><b>Why a new master.</b> The only list of RBI departments in the product was a hardcoded nine-element
 * array in {@code cepc-complaint-detail.component.ts}, including a literal 'Other'.
 * {@code DEPARTMENT_ROUTING_MASTER} cannot serve as the picker: its rows are BANKS mapped to an owning
 * department (it answers "who owns a complaint about HDFC"), and it carries no contact column at all.
 *
 * <p>{@link #assignRoleGroup} is the role the destination department's round-robin rotates over, so UST761's
 * "assignment inside the target department follows CRPC Head Round-Robin logic" is configuration rather than a
 * compiled-in role name.
 */
@Entity
@Table(name = "RBI_DEPARTMENT_MASTER", indexes = {
    @Index(name = "idx_rbi_dept_code", columnList = "deptCode"),
    @Index(name = "idx_rbi_dept_active", columnList = "isActive")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbiDepartmentMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "DEPT_CODE", nullable = false, length = 30)
    private String deptCode;

    @Column(name = "DEPT_NAME", nullable = false, length = 250)
    private String deptName;

    @Column(name = "CONTACT_EMAIL", length = 320)
    private String contactEmail;

    /** The role group whose round-robin picks an officer inside this department. */
    @Column(name = "ASSIGN_ROLE_GROUP", length = 50)
    private String assignRoleGroup;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "IS_ACTIVE", length = 1)
    private String isActive;

    @Column(name = "DISPLAY_ORDER")
    private Integer displayOrder;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public boolean active() {
        return "Y".equalsIgnoreCase(isActive);
    }
}
