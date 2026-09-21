package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One regulated entity as the typeahead dropdowns show it.
 *
 * <p>Deliberately narrower than {@link RegulatedEntityDetailResponse} and deliberately a separate type
 * rather than the same class with the extra fields left null: the search can return thousands of rows,
 * so the split is what stops every keystroke paying for the nodal officer block. Keeping them as two
 * types means the narrowness is enforced by the compiler instead of by remembering not to set fields.
 *
 * <p>{@link #moduleName}, {@link #entityCategory} and {@link #entityTypeDisplay} are derived from the
 * entity type rather than stored, and are carried here because they are what the officer reads before
 * committing to a row.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatedEntityListItem {

    private Long id;
    private String name;
    /** {@code CEPC} or {@code RBIO} — which department owns complaints against this entity. */
    private String department;
    private String entityType;
    private String moduleName;
    private String entityCategory;
    private String entityTypeDetail;
    private String entityTypeDisplay;
    private String city;
    private String state;
}
