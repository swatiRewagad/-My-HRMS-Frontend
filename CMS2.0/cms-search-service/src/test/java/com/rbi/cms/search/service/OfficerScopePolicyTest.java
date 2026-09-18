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
                .regionalOffice("Mumbai")
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("A missing or blank regionalOffice claim is a 403, never an unscoped search")
    void blankRegionalOfficeIsForbidden(String regionalOffice) {
        OfficerPrincipal off = OfficerPrincipal.builder()
                .userName("officer1").subject("sub-1").displayName("Officer One")
                .roles(List.of()).department(DepartmentConstants.DEPT_RBIO)
                .regionalOffice(regionalOffice)
                .build();
        assertThatThrownBy(() -> policy.requireRegionalOffice(off))
                .isInstanceOf(CmsException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("A null principal in requireRegionalOffice is a 403")
    void nullOfficerRegionalOfficeIsForbidden() {
        assertThatThrownBy(() -> policy.requireRegionalOffice(null))
                .isInstanceOf(CmsException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("A valid regionalOffice is returned stripped")
    void validRegionalOfficeIsReturned() {
        OfficerPrincipal off = OfficerPrincipal.builder()
                .userName("officer1").subject("sub-1").displayName("Officer One")
                .roles(List.of()).department(DepartmentConstants.DEPT_RBIO)
                .regionalOffice("  Mumbai  ")
                .build();
        assertThat(policy.requireRegionalOffice(off)).isEqualTo("Mumbai");
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
        // scopeQuery() now returns a bool with department + regionalOffice filters
        assertThat(result.filter()).anyMatch(q -> q.isBool()
                && q.bool().filter().stream().anyMatch(OfficerScopePolicyTest::isDepartmentTerm));
        assertThat(result.filter()).anyMatch(q -> q.isTerm()
                && "status.keyword".equals(q.term().field()));
        assertThat(result.should()).isEmpty();
        assertThat(result.must()).isEmpty();
    }

    @Test
    @DisplayName("scopeQuery includes both department and regionalOffice keyword subfields")
    void scopeQueryTargetsBothKeywordSubfields() {
        Query scope = policy.scopeQuery(officer(DepartmentConstants.DEPT_RBIO));

        assertThat(scope.isBool()).isTrue();
        List<Query> filters = scope.bool().filter();
        assertThat(filters).hasSize(2);
        assertThat(filters).anyMatch(q -> q.isTerm()
                && OfficerScopePolicy.DEPARTMENT_FIELD.equals(q.term().field())
                && DepartmentConstants.DEPT_RBIO.equals(q.term().value().stringValue()));
        assertThat(filters).anyMatch(q -> q.isTerm()
                && OfficerScopePolicy.REGIONAL_OFFICE_FIELD.equals(q.term().field())
                && "Mumbai".equals(q.term().value().stringValue()));
    }

    private static boolean isDepartmentTerm(Query query) {
        return query.isTerm() && OfficerScopePolicy.DEPARTMENT_FIELD.equals(query.term().field());
    }
}
