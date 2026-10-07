package com.hrms.cms.service;

import com.hrms.cms.dto.ClosureClauseRecommendationResponse.ClauseRecommendation;
import com.hrms.cms.dto.ClosureClauseRecommendationResponse.ClauseRecommendations;
import com.hrms.cms.entity.AssistanceClauseAffinity;
import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.AssistanceClauseAffinityRepository;
import com.hrms.cms.repository.ClosureClauseMasterRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ClosureClauseRecommendationService}: the rules that decide what an officer is shown, and the
 * three things this feature must never do.
 *
 * <h2>What is actually at stake</h2>
 * The thing this service reorders is the {@code <select>} that COMMITS A CLOSURE. Three failures here
 * are worse than the feature not existing:
 * <ol>
 *   <li>Offering a clause the caller's role may not cite. {@code 15(1)(a)} is restricted to
 *       {@code OMBUDSMAN,RBIO_ADMIN,ADMIN} and carries 833 of the 877 clause-bearing closures, so the
 *       ranking's single strongest signal is precisely the clause most callers must not be offered.
 *       A role filter that failed open would therefore fail open on the MOST-RANKED row.</li>
 *   <li>Auto-selecting. The DTO has no {@code selected} field so this is structurally inexpressible,
 *       but the ORDER is still a nudge, which is why the share floor exists.</li>
 *   <li>Annotating a count that the cohort does not support. "cited in 1 of 2 comparable closures" is
 *       noise wearing the costume of evidence.</li>
 * </ol>
 *
 * <h2>The access service is REAL here, not mocked, and that is the point</h2>
 * {@code restricted_to_roles} is honoured by CONSTRUCTION: the recommendation service walks the
 * PERMITTED list and consults the rollup, never the other way round, and the permitted list comes from
 * {@link ClosureClauseAccessService}. Mocking that service would let the role negatives pass while the
 * actual restriction column was never read, so the real collaborator is wired over a mocked
 * {@link ClosureClauseMasterRepository} and the master rows carry real {@code restrictedToRoles}
 * values. The role negatives below therefore fail if EITHER half breaks — the whole-token split in the
 * access service, or the intersection direction in the service under test.
 *
 * <h2>Mocked, not sliced</h2>
 * H2 is not a dependency of this module, so a {@code @DataJpaTest} cannot run and a JPA slice needs the
 * live MySQL. Every rule under test here is Java — the cohort ladder, the role union, the floors, the
 * sort — so mocks prove them exactly. The one thing mocks cannot prove is the index the query uses;
 * that is proven by the {@code EXPLAIN} recorded alongside the migration instead.
 *
 * <h2>Ported from {@code ClauseRecommendationServiceTest}</h2>
 * That test pinned a {@code ClauseRecommendationService} / {@code AssistanceClausePrior} /
 * {@code ClauseRecommendationRepository} triple that no longer exists. Every case it carried is below,
 * adapted to the affinity rollup's six-dimension cohort ladder, except two whose SUBJECT the rewrite
 * removed:
 * <ul>
 *   <li>"an unknown complaint number yields the scheme's clauses" — the service no longer resolves a
 *       complaint number at all. It takes a {@link Complaint}, so "unknown" collapses into the null
 *       complaint, which {@link Degradation#noComplaintYieldsSchemeClauses} covers.
 *   <li>"a null role set yields NO clauses" — the new design DELIBERATELY answers a blank role with the
 *       UNRESTRICTED subset, documented on {@code permittedClauses}. The legally load-bearing half of
 *       that case is preserved and strengthened: see
 *       {@link RoleFilter#unresolvedIdentityGetsOnlyUnrestrictedClauses}, which asserts the restricted
 *       clause is absent even when the rollup ranks it first.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClosureClauseRecommendationService: ranking, role filter and the floors")
class ClosureClauseRecommendationServiceTest {

    private static final String SCHEME = "RBIOS_2021";
    private static final String DEPT = "RBIO";
    private static final String ANY_TEXT = AssistanceClauseAffinity.TEXT_ANY;
    private static final long ANY_NUM = AssistanceClauseAffinity.NUMERIC_ANY;

    /** The seeded literal, verbatim. Whole tokens, no spaces, upper-case. */
    private static final String OMBUDSMAN_ONLY = "OMBUDSMAN,RBIO_ADMIN,ADMIN";

    @Mock private AssistanceClauseAffinityRepository affinityRepository;
    @Mock private ClosureClauseMasterRepository clauseMasterRepository;
    @Mock private RegulatedEntityRepository regulatedEntityRepository;

    private ClosureClauseRecommendationService service;

    @BeforeEach
    void setUp() {
        // REAL, over a mocked master repository, so restricted_to_roles is genuinely read and split.
        ClosureClauseAccessService accessService = new ClosureClauseAccessService(clauseMasterRepository);
        ReflectionTestUtils.setField(accessService, "defaultSchemeVersion", SCHEME);

        service = new ClosureClauseRecommendationService(
                affinityRepository, accessService, regulatedEntityRepository);
        // Both are @Value FIELD-injected (the class is @RequiredArgsConstructor and Lombok does not copy
        // @Value onto generated constructor parameters), so a unit test has to set them here. ON for
        // most tests: each OFF path has its own case in Degradation.
        ReflectionTestUtils.setField(service, "assistanceEnabled", true);
        ReflectionTestUtils.setField(service, "clauseRankingEnabled", true);

        // Default: no cohort anywhere on the ladder. Each test stubs only the rung it is about, and a
        // later specific stub wins over this catch-all.
        when(affinityRepository.findCohortClauses(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    // ---------------------------------------------------------------- fixtures

    private static ClosureClauseMaster clause(String code, String restrictedToRoles) {
        return ClosureClauseMaster.builder()
                .schemeVersion(SCHEME)
                .clauseCode(code)
                .label("Clause " + code)
                .labelKey("closure.clause." + code)
                .category("CLOSURE")
                .restrictedToRoles(restrictedToRoles)
                .active(true)
                .build();
    }

    /** What CLOSURE_CLAUSE_MASTER holds in force. The access service does the role filtering for real. */
    private void givenMaster(ClosureClauseMaster... clauses) {
        when(clauseMasterRepository.findAllInForce(eq(SCHEME), any(LocalDate.class)))
                .thenReturn(List.of(clauses));
    }

    /** A complaint with a department, a category and a possibly dirty entity code. */
    private static Complaint complaint(Long categoryId, String entityCode) {
        return Complaint.builder()
                .complaintNumber("RBIO/2026/000123")
                .schemeVersion(SCHEME)
                .department(DEPT)
                .categoryId(categoryId)
                .entityCode(entityCode)
                .build();
    }

    private static AssistanceClauseAffinity row(String clauseCode, long occurrences, long cohortTotal) {
        return row(clauseCode, occurrences, cohortTotal, ANY_NUM, ANY_TEXT);
    }

    private static AssistanceClauseAffinity row(String clauseCode, long occurrences, long cohortTotal,
                                                Long categoryKey, String entityType) {
        return AssistanceClauseAffinity.builder()
                .schemeVersion(SCHEME)
                .department(DEPT)
                .categoryKey(categoryKey)
                .groundKey(ANY_NUM)
                .entityType(entityType)
                .resolutionPath(ANY_TEXT)
                .clauseCode(clauseCode)
                .occurrences(occurrences)
                .cohortTotal(cohortTotal)
                .refreshedAt(LocalDateTime.now())
                .build();
    }

    /** The cohort at an exact rung of the ladder. */
    private void givenCohort(String department, Long categoryKey, String entityType,
                             AssistanceClauseAffinity... rows) {
        when(affinityRepository.findCohortClauses(eq(SCHEME), eq(department), eq(categoryKey),
                eq(ANY_NUM), eq(entityType), eq(ANY_TEXT), any(Pageable.class)))
                .thenReturn(List.of(rows));
    }

    /**
     * The department-wide rung, which is where a complaint with no category and no resolvable entity
     * lands — every more specific rung collapses onto it because all four of their extra dimensions are
     * already wildcards.
     */
    private void givenDepartmentCohort(AssistanceClauseAffinity... rows) {
        givenCohort(DEPT, ANY_NUM, ANY_TEXT, rows);
    }

    /** {@code entityCode} resolves through the register to this type. */
    private void givenEntityType(String normalisedName, String entityType) {
        when(regulatedEntityRepository.findByNameNormalized(normalisedName))
                .thenReturn(Optional.of(RegulatedEntity.builder().entityType(entityType).build()));
    }

    private static List<String> codes(ClauseRecommendations response) {
        return response.clauses().stream().map(ClauseRecommendation::clauseCode).toList();
    }

    // ---------------------------------------------------------------- the role filter

    @Nested
    @DisplayName("restricted_to_roles is honoured, including on the strongest row")
    class RoleFilter {

        /**
         * The measured shape of the live data, as a test. {@code 15(1)(a)} dominates the rollup; an
         * RBIO_OFFICER may not cite it. The ranking must not surface it REGARDLESS of its occurrence
         * count — the rollup is not role-aware and never should be, so the intersection against the
         * permitted list is the only guard.
         */
        @Test
        @DisplayName("a clause the caller may not cite is absent even when it tops the rollup")
        void restrictedClauseIsNotOfferedEvenWhenHighestRanked() {
            givenMaster(clause("15(1)(a)", OMBUDSMAN_ONLY), clause("15(1)(b)", null));
            // The rollup still carries 15(1)(a) with an overwhelming count.
            givenDepartmentCohort(row("15(1)(a)", 833, 870), row("15(1)(b)", 30, 870));

            ClauseRecommendations response =
                    service.recommend(complaint(null, null), Set.of("RBIO_OFFICER"));

            assertThat(codes(response))
                    .as("15(1)(a) is restricted to OMBUDSMAN/RBIO_ADMIN/ADMIN and must never be offered here")
                    .containsExactly("15(1)(b)");
        }

        /**
         * The multi-role officer. {@code RequestIdentity.getPrimaryRole()} is
         * {@code roles.iterator().next()} over a {@code HashSet}, so had the service consulted one role
         * the same officer would get different clause sets on different pods. The union is what makes
         * the answer stable — and it is a UNION rather than an intersection because holding two roles
         * means being allowed what either allows.
         */
        @Test
        @DisplayName("a multi-role caller gets the UNION of every role's clauses, de-duplicated")
        void multiRoleCallerGetsTheUnion() {
            givenMaster(clause("15(1)(a)", OMBUDSMAN_ONLY), clause("15(1)(b)", null));
            givenDepartmentCohort(row("15(1)(a)", 833, 870), row("15(1)(b)", 30, 870));

            ClauseRecommendations response =
                    service.recommend(complaint(null, null), Set.of("RBIO_OFFICER", "OMBUDSMAN"));

            assertThat(codes(response))
                    .as("both roles' clauses, 15(1)(b) appearing once not twice")
                    .containsExactly("15(1)(a)", "15(1)(b)");
        }

        /**
         * The fail-CLOSED direction for an unresolved identity.
         *
         * <p>BEHAVIOUR CHANGE, deliberate and documented on {@code permittedClauses}: a blank role set no
         * longer yields an EMPTY list, it yields the UNRESTRICTED subset — the same answer the picker
         * endpoint gives an unidentified caller. What must not change, and is what this case exists to
         * pin, is that a RESTRICTED clause is still refused to a blank role, even when the rollup ranks
         * it first. The old assertion ("no clauses at all") was the mechanism; this is the guarantee.
         */
        @Test
        @DisplayName("a null role set gets only the unrestricted clauses, never a restricted one")
        void unresolvedIdentityGetsOnlyUnrestrictedClauses() {
            givenMaster(clause("15(1)(a)", OMBUDSMAN_ONLY), clause("15(1)(b)", null));
            givenDepartmentCohort(row("15(1)(a)", 833, 870), row("15(1)(b)", 30, 870));

            ClauseRecommendations response = service.recommend(complaint(null, null), null);

            assertThat(codes(response))
                    .as("an unidentified caller must not be handed the appealable set")
                    .containsExactly("15(1)(b)");
        }

        @Test
        @DisplayName("an empty role set is the same refusal as a null one")
        void emptyRoleSetIsTheSameRefusalAsANullOne() {
            givenMaster(clause("15(1)(a)", OMBUDSMAN_ONLY), clause("15(1)(b)", null));
            givenDepartmentCohort(row("15(1)(a)", 833, 870), row("15(1)(b)", 30, 870));

            assertThat(codes(service.recommend(complaint(null, null), Set.of())))
                    .containsExactly("15(1)(b)");
        }

        /**
         * Whole-token comparison, not substring. A clause restricted to {@code ADMIN} must NOT reach an
         * {@code RBIO_ADMIN}: that is the defect a SQL {@code LIKE '%ADMIN%'} filter — or a Java
         * {@code contains} — would have introduced, and it widens every administrator-only clause.
         * Asserted in both directions in one case, because "nothing is offered" would pass the negative
         * half for the wrong reason.
         */
        @Test
        @DisplayName("role tokens are compared whole, so RBIO_ADMIN is not treated as ADMIN")
        void roleTokensAreComparedWholeSoRbioAdminIsNotTreatedAsAdmin() {
            givenMaster(clause("15(1)(a)", "ADMIN"), clause("15(1)(b)", "RBIO_ADMIN"));
            givenDepartmentCohort(row("15(1)(a)", 833, 870), row("15(1)(b)", 30, 870));

            ClauseRecommendations response =
                    service.recommend(complaint(null, null), Set.of("RBIO_ADMIN"));

            assertThat(codes(response))
                    .as("ADMIN must not be matched inside RBIO_ADMIN, while RBIO_ADMIN's own clause is kept")
                    .containsExactly("15(1)(b)");
        }
    }

    // ---------------------------------------------------------------- the floors

    @Nested
    @DisplayName("the sample and share floors")
    class Floors {

        /**
         * MIN_COHORT_SAMPLE = 5. Four closures is an anecdote; ordering a legal picker by it would
         * dress coincidence as precedent.
         */
        @Test
        @DisplayName("a cohort below the 5-closure sample floor is rejected, answer is unranked")
        void cohortBelowSampleFloorIsRejected() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));
            givenDepartmentCohort(row("15(1)(a)", 3, 4), row("15(1)(b)", 1, 4));

            ClauseRecommendations response =
                    service.recommend(complaint(null, null), Set.of("OMBUDSMAN"));

            assertThat(response.ranked())
                    .as("cohortTotal 4 < MIN_COHORT_SAMPLE 5")
                    .isFalse();
            assertThat(response.clauses())
                    .as("the floor governs the EVIDENCE, not the entitlement")
                    .hasSize(2);
        }

        @Test
        @DisplayName("a cohort exactly at the floor is accepted — the boundary is inclusive")
        void cohortAtTheFloorIsAccepted() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));
            givenDepartmentCohort(row("15(1)(a)", 4, 5), row("15(1)(b)", 1, 5));

            assertThat(service.recommend(complaint(null, null), Set.of("OMBUDSMAN")).ranked()).isTrue();
        }

        /**
         * The ladder WALK does not stop at a thin rung, it skips it. A specific cohort written under an
         * older, lower floor must not shadow a thick general one — the officer would get no ordering at
         * all when a perfectly good one existed one rung down.
         */
        @Test
        @DisplayName("a thin specific cohort is skipped and the walk continues to the thick general one")
        void thinSpecificCohortDoesNotShadowAThickGeneralOne() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));
            givenEntityType("PNB", "BANK");
            // Most specific rung: qualifying on specificity, disqualified on sample size.
            givenCohort(DEPT, 7L, "BANK", row("15(1)(b)", 3, 4, 7L, "BANK"));
            // Department-wide rung: thick, and therefore the answer.
            givenDepartmentCohort(row("15(1)(a)", 80, 100), row("15(1)(b)", 20, 100));

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "PNB"), Set.of("OMBUDSMAN"));

            assertThat(response.ranked()).isTrue();
            assertThat(response.cohortTotal())
                    .as("the thick cohort's denominator, not the thin one's")
                    .isEqualTo(100L);
            assertThat(codes(response)).containsExactly("15(1)(a)", "15(1)(b)");
        }

        /**
         * MIN_ANNOTATION_SHARE = 0.05. The ORDER may still carry a weak row — a clause with two
         * citations is genuinely less cited than one with forty — but the printed count is withheld,
         * because "cited in 2 of 100" invites a reader to treat 2% as a pattern. The flag is
         * {@code recommended}, and the client shows a count only when it is true.
         */
        @Test
        @DisplayName("a clause under the 5% share floor is ordered but NOT annotated")
        void rowUnderShareFloorIsNotAnnotated() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));
            // 40/100 = 40% clears; 2/100 = 2% does not.
            givenDepartmentCohort(row("15(1)(a)", 40, 100), row("15(1)(b)", 2, 100));

            ClauseRecommendations response =
                    service.recommend(complaint(null, null), Set.of("OMBUDSMAN"));

            assertThat(codes(response))
                    .as("ordered by occurrences, so the 40% row leads")
                    .containsExactly("15(1)(a)", "15(1)(b)");
            assertThat(response.clauses().get(0).recommended())
                    .as("40% of 100 is evidence worth printing")
                    .isTrue();
            assertThat(response.clauses().get(1).recommended())
                    .as("2% of 100 must not be printed as a count")
                    .isFalse();
            assertThat(response.clauses().get(1).occurrences())
                    .as("the count is still CARRIED; it is the advertising that is withheld")
                    .isEqualTo(2L);
        }
    }

    // ---------------------------------------------------------------- the ordering

    @Nested
    @DisplayName("the ordering and the cohort choice")
    class Ordering {

        @Test
        @DisplayName("clauses are ordered by occurrence count, descending")
        void orderedByOccurrencesDescending() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null), clause("15(1)(c)", null));
            givenDepartmentCohort(row("15(1)(a)", 10, 100), row("15(1)(b)", 50, 100),
                    row("15(1)(c)", 30, 100));

            ClauseRecommendations response =
                    service.recommend(complaint(null, null), Set.of("OMBUDSMAN"));

            assertThat(codes(response)).containsExactly("15(1)(b)", "15(1)(c)", "15(1)(a)");
        }

        /**
         * A tie must not be resolved by {@code HashMap} iteration order, or the same complaint would
         * present two different orderings on two pods and an officer would reasonably conclude the
         * feature is random.
         */
        @Test
        @DisplayName("an occurrence tie breaks on clause code, so the order is stable across pods")
        void tiesBreakDeterministically() {
            givenMaster(clause("15(1)(b)", null), clause("15(1)(a)", null));
            givenDepartmentCohort(row("15(1)(b)", 20, 100), row("15(1)(a)", 20, 100));

            assertThat(codes(service.recommend(complaint(null, null), Set.of("OMBUDSMAN"))))
                    .containsExactly("15(1)(a)", "15(1)(b)");
        }

        /**
         * A permitted clause with NO history is still offered, last. Dropping it would turn an ordering
         * aid into a filter on a legal entitlement — the officer would be unable to cite a clause the
         * scheme grants them merely because nobody had cited it yet.
         */
        @Test
        @DisplayName("a permitted clause absent from the rollup is kept, placed last")
        void unseenPermittedClauseIsKeptLast() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null), clause("16(2)(d)", null));
            givenDepartmentCohort(row("15(1)(a)", 40, 100), row("15(1)(b)", 20, 100));

            ClauseRecommendations response =
                    service.recommend(complaint(null, null), Set.of("OMBUDSMAN"));

            assertThat(codes(response))
                    .as("every permitted clause survives; the unseen one simply ranks last")
                    .containsExactly("15(1)(a)", "15(1)(b)", "16(2)(d)");
            assertThat(response.clauses().get(2).recommended()).isFalse();
            assertThat(response.clauses().get(2).occurrences())
                    .as("no history is 0, and the client renders it with no annotation")
                    .isEqualTo(0L);
        }

        /**
         * Specificity beats volume. A cohort keyed on this complaint's actual category and entity type
         * is a statement about complaints LIKE THIS ONE; the department-wide cohort is a statement about
         * the register. The narrower one wins even though it is always the smaller sample, because the
         * sample floor has already established it is large enough to mean something. Enforced by the
         * ladder STOPPING at the first rung that clears the floor, never by merging rungs.
         */
        @Test
        @DisplayName("the most specific qualifying cohort wins over a larger vaguer one")
        void mostSpecificCohortWins() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));
            givenEntityType("PNB", "BANK");
            // department-wide cohort: huge, says 15(1)(a)
            givenDepartmentCohort(row("15(1)(a)", 800, 870), row("15(1)(b)", 70, 870));
            // category+entity-type cohort: small but qualifying, says 15(1)(b)
            givenCohort(DEPT, 7L, "BANK",
                    row("15(1)(b)", 8, 10, 7L, "BANK"), row("15(1)(a)", 2, 10, 7L, "BANK"));

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "PNB"), Set.of("OMBUDSMAN"));

            assertThat(response.cohortTotal())
                    .as("the specific cohort's denominator, not the register's")
                    .isEqualTo(10L);
            assertThat(response.clauses().get(0).clauseCode())
                    .as("what comparable complaints did, not what the register did")
                    .isEqualTo("15(1)(b)");
            assertThat(response.clauses().get(0).specificity())
                    .as("department + category + entity type are specific; ground and path are not")
                    .isEqualTo(3);
        }

        /**
         * The documented data dirt, handled where it is READ. {@code entity_code} holds both
         * {@code 'Punjab National Bank'} and {@code 'PNB'}; {@code RegulatedEntity.normalize} is the
         * normalisation the register itself applied on write, so the lookup must apply it identically or
         * the specific cohort is simply never found. The resolved TYPE is then upper-cased before it
         * reaches SQL — normalising on write and on read, never inside a predicate, which would defeat
         * the index.
         */
        @Test
        @DisplayName("a lower-case padded entity code still matches the cohort the refresh wrote")
        void dirtyEntityCodeIsNormalisedAtTheLookup() {
            givenMaster(clause("15(1)(b)", null));
            // normalize("  pnb  ") is "PNB"; the register answers with a mixed-case type.
            givenEntityType("PNB", "Bank");
            givenCohort(DEPT, 7L, "BANK", row("15(1)(b)", 8, 10, 7L, "BANK"));

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "  pnb  "), Set.of("OMBUDSMAN"));

            assertThat(response.ranked())
                    .as("'  pnb  ' must resolve to the 'BANK' cohort or the dirt silently disables ranking")
                    .isTrue();
            assertThat(response.cohortTotal()).isEqualTo(10L);
        }

        /**
         * An unresolvable entity code costs SPECIFICITY, not the recommendation. The register read is
         * absorbed to the wildcard, so the ladder answers from a less specific rung instead of the whole
         * feature going dark on a data-migration problem owned elsewhere.
         */
        @Test
        @DisplayName("an unresolvable entity code costs specificity, not the recommendation")
        void unresolvableEntityCodeCostsSpecificityOnly() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));
            when(regulatedEntityRepository.findByNameNormalized(anyString()))
                    .thenThrow(new RuntimeException("REGULATED_ENTITIES unavailable"));
            givenDepartmentCohort(row("15(1)(a)", 80, 100), row("15(1)(b)", 20, 100));

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "Test Bank Ltd"), Set.of("OMBUDSMAN"));

            assertThat(response.ranked()).isTrue();
            assertThat(response.cohortTotal()).isEqualTo(100L);
        }
    }

    // ---------------------------------------------------------------- degradation

    @Nested
    @DisplayName("the kill switches and every degradation path")
    class Degradation {

        /**
         * The switch stops the WORK, not just the display. A kill switch that still ran the query would
         * leave the operator unable to shed the load they turned it off to shed.
         */
        @Test
        @DisplayName("the feature switch off returns the full permitted list unranked and never queries the rollup")
        void featureSwitchOffSkipsTheRollupEntirely() {
            ReflectionTestUtils.setField(service, "clauseRankingEnabled", false);
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));
            givenEntityType("PNB", "BANK");

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "PNB"), Set.of("OMBUDSMAN"));

            assertThat(response.ranked()).isFalse();
            assertThat(response.clauses())
                    .as("the switch governs the EVIDENCE, not the entitlement")
                    .hasSize(2);
            assertThat(response.clauses()).allMatch(r -> !r.recommended());
            verify(affinityRepository, never())
                    .findCohortClauses(any(), any(), any(), any(), any(), any(), any());
        }

        /**
         * The global lever still stops this feature too, which is the property a kill switch exists to
         * have. Both flags are ANDed; neither alone is sufficient.
         */
        @Test
        @DisplayName("the global assistance switch off also stops the rollup read")
        void globalAssistanceSwitchOffSkipsTheRollupEntirely() {
            ReflectionTestUtils.setField(service, "assistanceEnabled", false);
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));

            ClauseRecommendations response =
                    service.recommend(complaint(null, null), Set.of("OMBUDSMAN"));

            assertThat(response.ranked()).isFalse();
            assertThat(response.clauses()).hasSize(2);
            verify(affinityRepository, never())
                    .findCohortClauses(any(), any(), any(), any(), any(), any(), any());
        }

        /**
         * THE LIVE SHIPPED STATE. The migration is written but deliberately unapplied, so
         * {@code ASSISTANCE_CLAUSE_AFFINITY} does not exist and this read throws. The picker must remain
         * fully usable; an exception reaching the officer would make a missing convenience look like a
         * broken closure screen.
         */
        @Test
        @DisplayName("a missing rollup table degrades to unranked, not to an error")
        void missingRollupTableDegradesQuietly() {
            givenMaster(clause("15(1)(a)", null), clause("15(1)(b)", null));
            when(affinityRepository.findCohortClauses(any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException(
                            "Table 'cms_db.ASSISTANCE_CLAUSE_AFFINITY' doesn't exist"));

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "PNB"), Set.of("OMBUDSMAN"));

            assertThat(response.ranked()).isFalse();
            assertThat(response.clauses()).hasSize(2);
        }

        @Test
        @DisplayName("an empty rollup degrades to unranked with the clauses intact")
        void emptyRollupDegradesQuietly() {
            givenMaster(clause("15(1)(a)", null));
            // The setUp catch-all already answers every rung with no rows.

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "PNB"), Set.of("OMBUDSMAN"));

            assertThat(response.ranked()).isFalse();
            assertThat(response.clauses()).hasSize(1);
        }

        /**
         * MEASURED: 20 of 1422 open complaints carry no department. Department is a KEY column, so the
         * ladder keys it on the {@code '*'} sentinel and the answer comes from the scheme-wide rung.
         *
         * <p>BEHAVIOUR CHANGE from the pre-rewrite service, which refused to rank at all without a
         * department. The affinity rollup STORES a scheme-wide cohort as a row, so the fallback is a
         * keyed read of a cohort that was deliberately written rather than a register-wide aggregate
         * invented at request time — and it is still labelled, because {@code specificity} is 0 on it.
         * What this case pins is that no department name is ever INVENTED: the sentinel is used.
         */
        @Test
        @DisplayName("a complaint with no department is keyed on the wildcard, never on an invented one")
        void noDepartmentIsKeyedOnTheWildcard() {
            givenMaster(clause("15(1)(a)", null));
            Complaint noDept = Complaint.builder()
                    .complaintNumber("RBIO/2026/000123").schemeVersion(SCHEME).categoryId(7L).build();
            when(affinityRepository.findCohortClauses(eq(SCHEME), eq(ANY_TEXT), any(), eq(ANY_NUM),
                    eq(ANY_TEXT), eq(ANY_TEXT), any(Pageable.class)))
                    .thenReturn(List.of(AssistanceClauseAffinity.builder()
                            .schemeVersion(SCHEME).department(ANY_TEXT).categoryKey(ANY_NUM)
                            .groundKey(ANY_NUM).entityType(ANY_TEXT).resolutionPath(ANY_TEXT)
                            .clauseCode("15(1)(a)").occurrences(40L).cohortTotal(100L)
                            .refreshedAt(LocalDateTime.now()).build()));

            ClauseRecommendations response = service.recommend(noDept, Set.of("OMBUDSMAN"));

            verify(affinityRepository, never()).findCohortClauses(any(), eq(DEPT), any(), any(), any(),
                    any(), any());
            assertThat(response.clauses().get(0).specificity())
                    .as("a wildcard department scores nothing, so the UI cannot overstate the evidence")
                    .isEqualTo(0);
        }

        /**
         * The picker opens with no complaint in scope. The answer is the configured scheme's clause set
         * in its own order — not an error, and not an empty list. No complaint means no cohort key, and
         * falling back to a scheme-wide ordering here would be a claim about every complaint dressed as
         * a claim about this one.
         */
        @Test
        @DisplayName("no complaint yields the scheme's clauses unranked, with no rollup read")
        void noComplaintYieldsSchemeClauses() {
            givenMaster(clause("15(1)(a)", null));

            ClauseRecommendations response = service.recommend(null, Set.of("OMBUDSMAN"));

            assertThat(response.ranked()).isFalse();
            assertThat(response.clauses()).hasSize(1);
            verify(affinityRepository, never())
                    .findCohortClauses(any(), any(), any(), any(), any(), any(), any());
        }

        /**
         * The measured RBIO_OFFICER case: every clause with history is restricted away, so the access
         * service returns nothing and there is no entitlement to rank. An empty list is the honest
         * answer; inventing one would be the §4 failure. The rollup is not even read — there is nothing
         * for its rows to intersect with.
         */
        @Test
        @DisplayName("a caller permitted no clauses gets an empty list, never a fabricated one")
        void noPermittedClausesYieldsEmpty() {
            givenMaster(clause("15(1)(a)", OMBUDSMAN_ONLY));

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "PNB"), Set.of("RBIO_OFFICER"));

            assertThat(response.clauses()).isEmpty();
            assertThat(response.ranked()).isFalse();
            verify(affinityRepository, never())
                    .findCohortClauses(any(), any(), any(), any(), any(), any(), any());
        }

        /**
         * The access service's own failure is absorbed to "nothing", not propagated. This service's
         * contract is to return a list, and a master table that cannot be read leaves nothing to offer
         * and nothing to rank — but still no exception on a closure form.
         */
        @Test
        @DisplayName("an unreadable clause master yields an empty list, not an error")
        void unreadableClauseMasterYieldsEmpty() {
            when(clauseMasterRepository.findAllInForce(anyString(), any(LocalDate.class)))
                    .thenThrow(new RuntimeException("CLOSURE_CLAUSE_MASTER unavailable"));

            ClauseRecommendations response =
                    service.recommend(complaint(7L, "PNB"), Set.of("OMBUDSMAN"));

            assertThat(response.clauses()).isEmpty();
            assertThat(response.ranked()).isFalse();
        }
    }
}
