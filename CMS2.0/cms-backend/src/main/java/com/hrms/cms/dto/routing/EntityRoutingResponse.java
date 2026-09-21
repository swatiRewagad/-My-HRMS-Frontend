package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The result of matching a free-text entity name against the regulated-entity lists, as the reviewer
 * assessment screen uses it to pre-fill the department.
 *
 * <p>Not {@code @JsonInclude(NON_NULL)}: a miss leaves {@link #matchedEntityName} and
 * {@link #entityType} null and the screen binds to them directly, so the keys keep appearing with a
 * null value as they did when this was a {@code LinkedHashMap}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityRoutingResponse {

    /** The name as the caller spelled it, echoed back. */
    private String entityName;
    private String department;
    /** The official list name that matched, or null on a miss. */
    private String matchedEntityName;
    private String entityType;
    /** {@code EXACT}, {@code PARTIAL} or {@code NOT_FOUND}. */
    private String matchType;
    /** How many list entries matched — above one, the match is ambiguous rather than wrong. */
    private int matchCount;
    private String reason;
    private String assignedRole;
}
