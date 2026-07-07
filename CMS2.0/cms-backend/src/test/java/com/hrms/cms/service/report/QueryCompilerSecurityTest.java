package com.hrms.cms.service.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryCompiler Security & Adversarial Tests")
class QueryCompilerSecurityTest {

    @Mock
    private EntityManager entityManager;

    private SemanticModelRegistry semanticModel;
    private QueryCompiler compiler;

    @BeforeEach
    void setup() throws Exception {
        semanticModel = new SemanticModelRegistry();
        Method init = SemanticModelRegistry.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(semanticModel);
        compiler = new QueryCompiler(entityManager, semanticModel);
    }

    @Nested
    @DisplayName("Validation Security")
    class ValidationTests {

        @Test
        void rejectsNullSubject() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId(null)
                    .filters(List.of())
                    .build();
            assertThatThrownBy(() -> compiler.validate(query))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsUnknownSubject() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("DROP_TABLE_COMPLAINTS")
                    .filters(List.of())
                    .build();
            assertThatThrownBy(() -> compiler.validate(query))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNonWhitelistedFilterField() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(List.of(
                            ReportQuery.QueryFilter.builder()
                                    .field("password")
                                    .operator("=")
                                    .value("admin")
                                    .build()
                    ))
                    .build();
            assertThatThrownBy(() -> compiler.validate(query))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void rejectsSqlInjectionInFilterValue_fieldStillValidated() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(List.of(
                            ReportQuery.QueryFilter.builder()
                                    .field("status")
                                    .operator("=")
                                    .value("pending'; DROP TABLE complaints; --")
                                    .build()
                    ))
                    .build();
            assertThatCode(() -> compiler.validate(query)).doesNotThrowAnyException();
        }

        @Test
        void rejectsNonWhitelistedGroupBy() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(List.of())
                    .groupByField("social_security_number")
                    .build();
            assertThatThrownBy(() -> compiler.validate(query))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void acceptsValidQuery() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(List.of(
                            ReportQuery.QueryFilter.builder()
                                    .field("status")
                                    .operator("=")
                                    .value("pending")
                                    .build()
                    ))
                    .groupByField("department")
                    .sentence("Show all complaints where status is pending grouped by department")
                    .build();
            assertThatCode(() -> compiler.validate(query)).doesNotThrowAnyException();
        }

        @Test
        void acceptsEmptyFilters() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(List.of())
                    .build();
            assertThatCode(() -> compiler.validate(query)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Adversarial Inputs")
    class AdversarialTests {

        @Test
        void extremelyLongFilterValue_doesNotCrash() {
            String longValue = "x".repeat(10000);
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(List.of(
                            ReportQuery.QueryFilter.builder()
                                    .field("status")
                                    .operator("=")
                                    .value(longValue)
                                    .build()
                    ))
                    .build();
            assertThatCode(() -> compiler.validate(query)).doesNotThrowAnyException();
        }

        @Test
        void hundredsOfFilters_doesNotCrash() {
            List<ReportQuery.QueryFilter> filters = new java.util.ArrayList<>();
            for (int i = 0; i < 200; i++) {
                filters.add(ReportQuery.QueryFilter.builder()
                        .field("status")
                        .operator("=")
                        .value("pending")
                        .build());
            }
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(filters)
                    .build();
            assertThatCode(() -> compiler.validate(query)).doesNotThrowAnyException();
        }

        @Test
        void unicodeInFilterValue_doesNotCrash() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(List.of(
                            ReportQuery.QueryFilter.builder()
                                    .field("status")
                                    .operator("=")
                                    .value("\u0938\u094D\u0925\u093F\u0924\u093F' OR 1=1 --")
                                    .build()
                    ))
                    .build();
            assertThatCode(() -> compiler.validate(query)).doesNotThrowAnyException();
        }

        @Test
        void emptyStringSubject_rejected() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("")
                    .filters(List.of())
                    .build();
            assertThatThrownBy(() -> compiler.validate(query))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void blankGroupByField_rejected() {
            ReportQuery query = ReportQuery.builder()
                    .subjectId("list")
                    .filters(List.of())
                    .groupByField("   ")
                    .build();
            assertThatThrownBy(() -> compiler.validate(query))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
