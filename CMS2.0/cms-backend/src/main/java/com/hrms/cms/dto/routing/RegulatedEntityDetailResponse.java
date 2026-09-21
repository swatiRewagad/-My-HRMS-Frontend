package com.hrms.cms.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One regulated entity in full, for when an officer changes the entity on a complaint and the dependent
 * fields have to be reloaded.
 *
 * <p>A superset of {@link RegulatedEntityListItem}; see that class for why the two are separate types
 * rather than one. The nodal officer block is the reason this endpoint exists at all — those contacts
 * are what gets snapshotted onto the nodal officer record, so changing the entity changes who the
 * complaint is forwarded to.
 *
 * <p>Not {@code @JsonInclude(NON_NULL)}: an entity with no nodal officer on file still has to emit the
 * keys as null, because the contact panel binds to them and uses their emptiness to decide it has
 * nothing to show.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatedEntityDetailResponse {

    private Long id;
    private String name;
    private String department;
    private String entityType;
    private String moduleName;
    private String entityCategory;
    private String entityTypeDetail;
    private String entityTypeDisplay;
    private String city;
    private String state;

    private String status;
    /** Whether the entity can log in to answer complaints itself. */
    private Boolean portalEnabled;

    private String nodalOfficerName;
    private String nodalOfficerEmail;
    private String nodalOfficerPhone;
    private String nodalOfficerDesignation;

    /** Principal Nodal Officer — the escalation contact above the nodal officer. */
    private String pnoName;
    private String pnoEmail;
    private String pnoPhone;
}
