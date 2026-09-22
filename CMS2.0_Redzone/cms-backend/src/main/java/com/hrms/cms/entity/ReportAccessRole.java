package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Which roles may view and export which reports (UST615, UST669, UST670).
 *
 * <h2>Why this is a table and not a Java list</h2>
 * UST615/669 enumerate the report audience — Secretary, Deputy Ombudsman, Ombudsman, Ombudsman Admin,
 * CEPD Admin, AA Admin, AA Secretariat — and mark some of them view-only. Hardcoding that list would
 * put an authorisation DECISION in the application, so adding a rank or changing who may export would
 * need a release. It would also force a silent choice on a live contradiction: UST615 lists Secretary
 * as a report viewer while UST655 removes Secretary as an assignment target. As rows, resolving that
 * is an UPDATE.
 *
 * <h2>The absence of rows must not grant access</h2>
 * The Angular screen for this has existed for some time against endpoints that did not, and it
 * swallowed the 404 (report-builder.component.ts:247) so its {@code canExport} fell through to
 * {@code return true}. An empty table therefore READ as "everyone may export", behind a reassuring
 * "No access roles configured" empty state. {@code ReportAccessService} treats an empty table as
 * deny-all for exactly that reason.
 *
 * <h2>Flags are CHAR(1) 'Y'/'N'</h2>
 * Matching {@code RBIO_STATUS_ROLE_VISIBILITY} and the rest of this schema rather than a MySQL
 * BOOLEAN, because the two dialects disagree about what BOOLEAN is. The accessors expose real
 * booleans so the JSON the Angular client already expects ({@code canExport: true}) is unchanged.
 */
@Entity
@Table(name = "REPORT_ACCESS_ROLE",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_REPORT_ACCESS_ROLE",
                columnNames = {"REPORT_TYPE", "ROLE_NAME"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportAccessRole {

    /** Report type meaning "every report type". */
    public static final String TYPE_ALL = "ALL";

    private static final String YES = "Y";
    private static final String NO = "N";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code RBIO | CEPC | CRPC | AA | ALL}. */
    @Column(name = "REPORT_TYPE", nullable = false, length = 50)
    private String reportType;

    /** Keycloak realm role name, without a {@code ROLE_} prefix. */
    @Column(name = "ROLE_NAME", nullable = false, length = 50)
    private String roleName;

    @Column(name = "CAN_VIEW", nullable = false, length = 1)
    @Builder.Default
    private String canView = YES;

    @Column(name = "CAN_EXPORT", nullable = false, length = 1)
    @Builder.Default
    private String canExport = NO;

    @Column(name = "CREATED_BY", length = 200)
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY", length = 200)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Transient
    public boolean isViewAllowed() {
        return YES.equalsIgnoreCase(canView);
    }

    @Transient
    public boolean isExportAllowed() {
        return YES.equalsIgnoreCase(canExport);
    }

    public void setViewAllowed(boolean allowed) {
        this.canView = allowed ? YES : NO;
    }

    public void setExportAllowed(boolean allowed) {
        this.canExport = allowed ? YES : NO;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
