package com.rbi.cms.search.service;

import com.rbi.cms.common.enums.DepartmentConstants;
import com.rbi.cms.common.enums.RoleConstants;
import com.rbi.cms.common.exception.CmsException;
import com.rbi.cms.search.dto.OfficerPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfficerScopePolicyTest {

    private final OfficerScopePolicy policy = new OfficerScopePolicy();

    private static OfficerPrincipal officer(String department, String... roles) {
        return OfficerPrincipal.builder()
                .userName("officer1")
                .subject("sub-1")
                .displayName("Officer One")
                .roles(List.of(roles))
                .department(department)
                .build();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("A missing or blank department claim is a 403, never an unscoped search")
    void blankDepartmentIsForbidden(String department) {
        assertThatThrownBy(() -> policy.requireDepartment(officer(department)))
                .isInstanceOf(CmsException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("A null principal is a 403 rather than a NullPointerException")
    void nullOfficerIsForbidden() {
        assertThatThrownBy(() -> policy.requireDepartment(null))
                .isInstanceOf(CmsException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RBIO_ADMIN", "NOT_A_DEPARTMENT", "RBI", "rbio-east"})
    @DisplayName("An unrecognised department is a 403, not a term filter that matches nothing")
    void unknownDepartmentIsForbidden(String department) {
        assertThatThrownBy(() -> policy.requireDepartment(officer(department)))
                .isInstanceOf(CmsException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rbio", "RBIO", "  Rbio  "})
    @DisplayName("The claim is canonicalised, because the index stores the exact constant")
    void departmentIsCanonicalised(String claimed) {
        assertThat(policy.requireDepartment(officer(claimed)))
                .isEqualTo(DepartmentConstants.DEPT_RBIO);
    }

    @Test
    @DisplayName("RBIO_ADMIN gets no exemption from department scoping")
    void adminIsNotExempt() {
        assertThatThrownBy(() -> policy.requireDepartment(officer(null, RoleConstants.RBIO_ADMIN)))
                .isInstanceOf(CmsException.class);

        assertThat(policy.requireDepartment(officer(DepartmentConstants.DEPT_CEPC, RoleConstants.RBIO_ADMIN)))
                .isEqualTo(DepartmentConstants.DEPT_CEPC);
    }

    @Test
    @DisplayName("The clause lands in filter, not must, so it neither scores nor can be OR-ed away")
    void clauseLandsInFilter() {
        var qb = new ComplaintQueryBuilder();
        qb.termFilter("status.keyword", "NEW_COMPLAINT");

        policy.apply(qb, officer(DepartmentConstants.DEPT_CRPC));
        BoolQuery built = qb.build();

        assertThat(built.must()).noneMatch(OfficerScopePolicyTest::isDepartmentTerm);
        assertThat(built.should()).isEmpty();
        assertThat(built.filter()).anyMatch(OfficerScopePolicyTest::isDepartmentTerm);
    }

    @Test
    @DisplayName("A caller-supplied department filter narrows but cannot widen the scope")
    void callerCannotWidenScope() {
        var qb = new ComplaintQueryBuilder();
        // Simulates a request body trying to select another department.
        qb.termFilter(OfficerScopePolicy.DEPARTMENT_FIELD, DepartmentConstants.DEPT_APPELLATE);

        policy.apply(qb, officer(DepartmentConstants.DEPT_CRPC));

        // Both clauses survive as AND-ed filters, so the result is the empty intersection rather
        // than the caller's choice replacing the policy's.
        List<Query> departmentClauses = qb.build().filter().stream()
                .filter(OfficerScopePolicyTest::isDepartmentTerm)
                .toList();

        assertThat(departmentClauses).hasSize(2);
        assertThat(departmentClauses).anyMatch(q -> DepartmentConstants.DEPT_CRPC
                .equals(q.term().value().stringValue()));
    }

    @Test
    @DisplayName("scoped() AND-s the aggregation's own filter with the tenancy filter")
    void scopedWrapsAggregationFilter() {
        Query own = Query.of(q -> q.term(t -> t.field("status.keyword").value(v -> v.stringValue("DRAFT"))));

        BoolQuery result = policy.scoped(officer(DepartmentConstants.DEPT_CEPC), own).bool();

        assertThat(result.filter()).hasSize(2);
        assertThat(result.filter()).anyMatch(OfficerScopePolicyTest::isDepartmentTerm);
        assertThat(result.should()).isEmpty();
        assertThat(result.must()).isEmpty();
    }

    @Test
    @DisplayName("scopeQuery targets the keyword subfield, or a term filter would never match")
    void scopeQueryTargetsKeywordSubfield() {
        Query scope = policy.scopeQuery(officer(DepartmentConstants.DEPT_RBIO));

        assertThat(scope.term().field()).isEqualTo("department.keyword");
        assertThat(scope.term().value().stringValue()).isEqualTo(DepartmentConstants.DEPT_RBIO);
    }

    private static boolean isDepartmentTerm(Query query) {
        return query.isTerm() && OfficerScopePolicy.DEPARTMENT_FIELD.equals(query.term().field());
    }
}
