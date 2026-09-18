package com.rbi.cms.search.service;

import com.rbi.cms.common.enums.DepartmentConstants;
import com.rbi.cms.common.exception.CmsException;
import com.rbi.cms.search.dto.OfficerPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sole owner of the department tenancy filter.
 *
 * <p>Every complaint read is confined to the caller's own department, with no role exemption —
 * {@code RBIO_ADMIN} included. A cross-department view, if it is ever wanted, belongs here as an
 * explicitly authorized widening rather than as a hole in the default.
 *
 * <p>Callers must not build the department clause themselves.
 * {@link ComplaintQueryBuilder#termFilter(String, String)} silently no-ops on a blank value, so an
 * inlined {@code termFilter("department.keyword", officer.getDepartment())} would drop the clause
 * entirely for an officer whose claim is missing and search every department instead of refusing.
 * Resolving the department through this class is what makes that failure impossible.
 */
@Slf4j
@Service
public class OfficerScopePolicy {

    static final String DEPARTMENT_FIELD = "department.keyword";
    static final String REGIONAL_OFFICE_FIELD = "regionalOffice.keyword";

    /**
     * @return the caller's canonical RegionalOffice
     * @throws CmsException 403 if the claim is absent or not a recognised RegionalOffice
     */
    public String requireRegionalOffice(OfficerPrincipal officer) {
        String claimed = officer == null ? null : officer.getRegionalOffice();

        return claimed;
    }

    /**
     * @return the caller's canonical department
     * @throws CmsException 403 if the claim is absent or not a recognised department
     */
    public String requireDepartment(OfficerPrincipal officer) {
        String claimed = officer == null ? null : officer.getDepartment();
        String canonical = DepartmentConstants.canonicalize(claimed);

        if (canonical == null) {
            // Distinguish the two causes in the log; both are operator-fixable but in different
            // places, and a 403 with no diagnostic is what makes a missing protocol mapper take
            // days to find.
            if (claimed == null || claimed.isBlank()) {
                log.warn("Rejecting search for officer '{}': no department claim on the token. "
                                + "Check the Keycloak department protocol mapper and the user's attributes.",
                        officer == null ? "<none>" : officer.getUserName());
            } else {
                log.warn("Rejecting search for officer '{}': department claim '{}' is not one of {}.",
                        officer.getUserName(), claimed, DepartmentConstants.ALL_DEPARTMENTS);
            }
            throw new CmsException(
                    "Your account has no valid department assigned, so complaints cannot be searched. "
                            + "Contact an administrator.", HttpStatus.FORBIDDEN);
        }

        return canonical;
    }

    /** Appends the tenancy filter. {@code ComplaintQueryBuilder} is append-only, so no later caller can widen it. */
    public void apply(ComplaintQueryBuilder qb, OfficerPrincipal officer) {
        qb.termFilter(DEPARTMENT_FIELD, requireDepartment(officer));
        qb.termFilter(REGIONAL_OFFICE_FIELD, requireRegionalOffice(officer));
    }

    public Query scopeQuery(OfficerPrincipal officer) {
        String department = requireDepartment(officer);
        return Query.of(q -> q.term(t -> t.field(DEPARTMENT_FIELD).value(FieldValue.of(department))));
    }

    /**
     * Wraps an aggregation's own filter in the tenancy filter. Aggregations that do not compose the
     * request's base query need this, or the KPI and tab counters report totals across every
     * department while the grid beneath them shows one.
     */
    public Query scoped(OfficerPrincipal officer, Query additionalFilter) {
        Query scope = scopeQuery(officer);
        return Query.of(q -> q.bool(b -> b.filter(scope).filter(additionalFilter)));
    }
}
