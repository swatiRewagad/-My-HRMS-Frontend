package com.hrms.cms.service.mre;

import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Covers the Scheme-coverage determination (UST473).
 *
 * <p>This class had NO test at all, which is how it came to answer a substring existence query that
 * ignored {@code department} — reporting a CEPC entity as covered by the Ombudsman Scheme — while the
 * engine's own tests passed because they mocked it out.
 *
 * <p>Every assertion here names the routing error it prevents. Coverage decides which forum may hear a
 * citizen's complaint, so "wrong" means the complaint is heard by a body without jurisdiction, or refused
 * by one that had it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MreEntityCoverageServiceTest {

    @Mock private RegulatedEntityRepository regulatedEntityRepo;

    @InjectMocks private MreEntityCoverageService service;

    @BeforeEach
    void injectSchemeConfig() {
        // @Value fields are not populated outside a Spring context; the Scheme name reaches citizen-facing
        // reason text so it must be realistic rather than null.
        ReflectionTestUtils.setField(service, "schemeVersion", "RBIOS_2021");
        ReflectionTestUtils.setField(service, "schemeName",
                "Reserve Bank - Integrated Ombudsman Scheme, 2021");
    }

    private RegulatedEntity entity(String name, String department) {
        RegulatedEntity e = new RegulatedEntity();
        e.setName(name);
        e.setNameNormalized(RegulatedEntity.normalize(name));
        e.setDepartment(department);
        return e;
    }

    private void exactMatch(String normalized, RegulatedEntity result) {
        when(regulatedEntityRepo.findByNameNormalized(normalized)).thenReturn(Optional.of(result));
    }

    private void noExactMatch() {
        when(regulatedEntityRepo.findByNameNormalized(anyString())).thenReturn(Optional.empty());
    }

    private void partialMatches(RegulatedEntity... results) {
        when(regulatedEntityRepo.searchByNormalizedName(anyString())).thenReturn(List.of(results));
    }

    @Nested
    @DisplayName("Exact identification")
    class Exact {

        @Test
        void anRbioEntityIsCoveredByTheScheme() {
            exactMatch("STATE BANK OF INDIA", entity("State Bank of India", "RBIO"));

            var coverage = service.resolveCoverage("State Bank of India");

            assertThat(coverage.status()).isEqualTo(MreEntityCoverageService.CoverageStatus.COVERED);
            assertThat(coverage.department()).isEqualTo("RBIO");
            assertThat(coverage.isCovered()).isTrue();
        }

        @Test
        void aCepcEntityIsNotCoveredAndBelongsToCepc() {
            // THE original defect. The substring check ignored `department`, so this returned "covered" and
            // the citizen was told their complaint was within the Ombudsman Scheme when it was not.
            exactMatch("BAJAJ FINANCE", entity("Bajaj Finance", "CEPC"));

            var coverage = service.resolveCoverage("Bajaj Finance");

            assertThat(coverage.status()).isEqualTo(MreEntityCoverageService.CoverageStatus.NOT_COVERED);
            assertThat(coverage.department()).isEqualTo("CEPC");
            assertThat(coverage.isCovered()).isFalse();
        }

        @Test
        void matchesRegardlessOfPunctuationAndCase() {
            exactMatch("HDFC BANK", entity("HDFC Bank", "RBIO"));

            assertThat(service.resolveCoverage("  h.d.f.c.  bank  ").isCovered()).isTrue();
        }

        @Test
        void recordsTheSchemeTheDeterminationWasMadeUnder() {
            // A complaint must be able to say which Scheme's rules were applied to it; the column had no
            // writer at all before this.
            exactMatch("STATE BANK OF INDIA", entity("State Bank of India", "RBIO"));

            assertThat(service.resolveCoverage("State Bank of India").schemeVersion())
                    .isEqualTo("RBIOS_2021");
        }

        @Test
        void namesTheSchemeInTheCitizenFacingReason() {
            exactMatch("BAJAJ FINANCE", entity("Bajaj Finance", "CEPC"));

            assertThat(service.resolveCoverage("Bajaj Finance").reason())
                    .contains("Reserve Bank - Integrated Ombudsman Scheme, 2021");
        }
    }

    @Nested
    @DisplayName("Partial identification must not guess")
    class Partial {

        @Test
        void refusesWhenCandidatesDisagreeOnDepartment() {
            // The live data really is like this: 'HDFC' matches HDFC Credila (CEPC) AND HDFC Bank (RBIO),
            // with the CEPC row returned FIRST. Taking matches.get(0) routed a complaint against a
            // Scheme-covered bank to CEPC purely by row order, and the citizen lost their statutory forum
            // with nothing visibly wrong.
            noExactMatch();
            partialMatches(
                    entity("HDFC Credila Financial Services Limited", "CEPC"),
                    entity("HDFC Bank Limited", "RBIO"),
                    entity("HDFC Bank", "RBIO"));

            var coverage = service.resolveCoverage("HDFC");

            assertThat(coverage.status()).isEqualTo(MreEntityCoverageService.CoverageStatus.AMBIGUOUS);
            assertThat(coverage.needsReview()).isTrue();
            assertThat(coverage.isCovered()).isFalse();
            // The reason must name the conflict so a reviewer knows to pick a specific entity.
            assertThat(coverage.reason()).contains("different departments");
        }

        @Test
        void acceptsAPartialMatchWhenEveryCandidateAgrees() {
            // 'State Bank' matching only RBIO entities is not ambiguous in any way that matters: whichever
            // row is chosen, the forum is the same. Refusing here would send genuinely covered complaints
            // to review for no benefit.
            noExactMatch();
            partialMatches(
                    entity("State Bank of India", "RBIO"),
                    entity("State Bank of India - Mumbai", "RBIO"));

            var coverage = service.resolveCoverage("State Bank");

            assertThat(coverage.status()).isEqualTo(MreEntityCoverageService.CoverageStatus.COVERED);
            assertThat(coverage.department()).isEqualTo("RBIO");
        }
    }

    @Nested
    @DisplayName("Fail closed")
    class FailClosed {

        @Test
        void anUnknownEntityIsNotCovered() {
            // The old routing returned RBIO here — default-ALLOW on a maintainability determination, so an
            // entity nobody regulates was admitted to the Ombudsman Scheme.
            noExactMatch();
            when(regulatedEntityRepo.searchByNormalizedName(anyString())).thenReturn(List.of());

            var coverage = service.resolveCoverage("Totally Unregulated Ltd");

            assertThat(coverage.status()).isEqualTo(MreEntityCoverageService.CoverageStatus.UNKNOWN);
            assertThat(coverage.isCovered()).isFalse();
            assertThat(coverage.needsReview()).isTrue();
        }

        @Test
        void aBlankEntityNameIsNotCovered() {
            var coverage = service.resolveCoverage("   ");

            assertThat(coverage.status()).isEqualTo(MreEntityCoverageService.CoverageStatus.UNKNOWN);
            assertThat(coverage.isCovered()).isFalse();
        }

        @Test
        void aNullEntityNameIsNotCovered() {
            assertThat(service.resolveCoverage(null).isCovered()).isFalse();
        }

        @Test
        void anEntityWithNoDepartmentRecordedIsNotTreatedAsCovered() {
            // A row with a blank department cannot establish that the Ombudsman has jurisdiction.
            exactMatch("MYSTERY LTD", entity("Mystery Ltd", null));

            assertThat(service.resolveCoverage("Mystery Ltd").isCovered()).isFalse();
        }
    }

    @Nested
    @DisplayName("Legacy boolean contract")
    class LegacyContract {

        @Test
        void ambiguousAnswersFalseSoTheEngineRaisesItsGround() {
            // MaintainabilityRulesEngine asks a yes/no question. Answering false for AMBIGUOUS is the
            // fail-closed direction: the engine raises ENTITY_NOT_COVERED and a human decides, rather than
            // the complaint being admitted on a name that matched several different entities.
            noExactMatch();
            partialMatches(entity("A Bank", "RBIO"), entity("A Finance", "CEPC"));

            assertThat(service.isEntityCovered("A", null)).isFalse();
        }

        @Test
        void aCoveredEntityStillAnswersTrue() {
            exactMatch("STATE BANK OF INDIA", entity("State Bank of India", "RBIO"));

            assertThat(service.isEntityCovered("State Bank of India", null)).isTrue();
        }

        @Test
        void theIgnoredEntityTypeArgumentDoesNotChangeTheAnswer() {
            // entityType was accepted and silently ignored, which is part of why a CEPC entity could answer
            // "covered". Coverage comes from the entity's own department, so passing a contradictory type
            // must not move the verdict.
            exactMatch("BAJAJ FINANCE", entity("Bajaj Finance", "CEPC"));

            assertThat(service.isEntityCovered("Bajaj Finance", "RBIO")).isFalse();
            assertThat(service.isEntityCovered("Bajaj Finance", "ANYTHING")).isFalse();
        }
    }
}
