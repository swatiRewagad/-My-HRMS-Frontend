package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "REGULATED_ENTITIES", indexes = {
    @Index(name = "idx_re_department", columnList = "department"),
    @Index(name = "idx_re_name", columnList = "name"),
    @Index(name = "idx_re_entity_type", columnList = "entityType")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegulatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(length = 500)
    private String nameNormalized;

    @Column(nullable = false, length = 10)
    private String department;

    @Column(length = 100)
    private String entityType;

    /** RBI's sub-classification below the category — "Loan Company", "Housing Finance Company" and the
     *  like. Only NBFCs have one, so it is null for a bank rather than empty by omission. */
    @Column(length = 100)
    private String entityTypeDetail;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 20)
    private String status;

    // ═══ Nodal Officer details ═══
    @Column(length = 200)
    private String nodalOfficerName;

    @Column(length = 200)
    private String nodalOfficerEmail;

    @Column(length = 20)
    private String nodalOfficerPhone;

    @Column(length = 100)
    private String nodalOfficerDesignation;

    // ═══ Principal Nodal Officer ═══
    @Column(length = 200)
    private String pnoName;

    @Column(length = 200)
    private String pnoEmail;

    @Column(length = 20)
    private String pnoPhone;

    // ═══ Portal metadata ═══
    private LocalDateTime registrationDate;

    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean portalEnabled = true;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "active";
        if (this.nameNormalized == null && this.name != null) {
            this.nameNormalized = normalize(this.name);
        }
    }

    public static String normalize(String name) {
        if (name == null) return "";
        return name.toUpperCase()
                .replaceAll("[^A-Z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * The complaint screens show Module Name, Entity Category and Entity Type as three fields, but they
     * are one hierarchy and only the middle level was ever stored: {@code entityType} holds the category
     * ("Public Sector Bank"). The module above it is derived here rather than stored, because a category
     * determines it; the type below it cannot be derived and lives in {@code entityTypeDetail}.
     */
    private static final Map<String, String> MODULE_BY_CATEGORY = Map.ofEntries(
            Map.entry("Public Sector Bank", "Bank"),
            Map.entry("Private Sector Bank", "Bank"),
            Map.entry("Foreign Bank", "Bank"),
            Map.entry("Cooperative Bank", "Bank"),
            Map.entry("Regional Rural Bank", "Bank"),
            Map.entry("Small Finance Bank", "Bank"),
            Map.entry("Payments Bank", "Bank"),
            Map.entry("NBFC", "NBFC"),
            Map.entry("Payment Infrastructure", "Payment System Operator"));

    /** Where the master's wording differs from the label the complaint screens offer. */
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "Public Sector Bank", "Nationalised Bank",
            "Private Sector Bank", "Private Bank",
            "Payments Bank", "Payment Bank");

    /** Null for a category that places the entity in no module, rather than a guess from the name. */
    public static String moduleNameFor(String entityType) {
        return entityType == null ? null : MODULE_BY_CATEGORY.get(entityType);
    }

    public static String entityCategoryFor(String entityType) {
        return entityType == null ? null : CATEGORY_LABELS.getOrDefault(entityType, entityType);
    }
}
