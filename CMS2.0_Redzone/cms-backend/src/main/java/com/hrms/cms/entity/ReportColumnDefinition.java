package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One column a report may emit, as DATA rather than as a {@code row.put(...)} call (UST613).
 *
 * <h2>Why the registry exists</h2>
 * UST613 requires 62 report columns, each sourcing its value per a defined Field Name mapping in the
 * "BRD Format and Logic Sheet". {@code QueryCompiler.executeList} emitted exactly ten, hardcoded in a
 * Java stream, so every new column was a code change and a release.
 *
 * <h2>What is deliberately NOT done here</h2>
 * The BRD Format and Logic Sheet is NOT in this repository. Only the eleven columns that demonstrably
 * exist today are seeded, each mapped to the {@code Complaint} property the compiler already read.
 * Inventing the other ~51 definitions would be fabricating a specification for a regulator-facing
 * report, and a report column that names the wrong source field is worse than a missing column
 * because it looks authoritative. When the sheet arrives, each remaining column is an INSERT and
 * {@code FIELD_NAME} corrections are UPDATEs; no Java moves.
 *
 * <h2>JPA_PATH is an allow-list, not free text</h2>
 * {@code jpaPath} is interpolated into a Criteria {@code root.get(...)} call, so a bad value is a way
 * to read a column the report was never meant to expose. {@code ReportColumnRegistry} therefore
 * validates every path against the {@code Complaint} metamodel at load time and drops the ones that
 * do not resolve, rather than discovering the problem per request.
 */
@Entity
@Table(name = "REPORT_COLUMN_REGISTRY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_REPORT_COLUMN_KEY", columnNames = "COLUMN_KEY"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportColumnDefinition {

    private static final String YES = "Y";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The key emitted in the JSON result row, e.g. {@code complaintNumber}. */
    @Column(name = "COLUMN_KEY", nullable = false, length = 80)
    private String columnKey;

    /** The BRD "Field Name" — the label the report displays. */
    @Column(name = "FIELD_NAME", nullable = false, length = 120)
    private String fieldName;

    /** The {@code Complaint} entity property this column sources from. */
    @Column(name = "JPA_PATH", nullable = false, length = 120)
    private String jpaPath;

    /** {@code STRING | DATETIME | NUMBER}. Decides how the value is rendered into JSON. */
    @Column(name = "VALUE_TYPE", nullable = false, length = 20)
    @Builder.Default
    private String valueType = "STRING";

    /**
     * Column order in the output. This matters beyond aesthetics: the compiler builds rows into a
     * {@code LinkedHashMap} and the Angular table derives its headers from
     * {@code Object.keys(results[0])}, so insertion order IS the on-screen column order.
     */
    @Column(name = "DISPLAY_ORDER", nullable = false)
    @Builder.Default
    private Integer displayOrder = 999;

    /** Included when the caller selects no explicit column set. */
    @Column(name = "IS_DEFAULT", nullable = false, length = 1)
    @Builder.Default
    private String isDefault = YES;

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    @Builder.Default
    private String isActive = YES;

    @Transient
    public boolean isDefaultColumn() {
        return YES.equalsIgnoreCase(isDefault);
    }

    @Transient
    public boolean isActiveColumn() {
        return YES.equalsIgnoreCase(isActive);
    }

    @Transient
    public boolean isDateTime() {
        return "DATETIME".equalsIgnoreCase(valueType);
    }
}
