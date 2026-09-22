package com.hrms.cms.service;

import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.entity.OfficeAssignmentMapping;
import com.hrms.cms.entity.OfficeAssignmentStrategy;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import com.hrms.cms.repository.OfficeAssignmentMappingRepository;
import com.hrms.cms.repository.OfficeAssignmentStrategyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers per-office assignment logic (UST468-472).
 *
 * <p>The assertions target the behaviours that decide whether this feature is real: that an office's
 * chosen logic is actually honoured, that a mapping which cannot answer falls back to the office's own
 * Ombudsman Admin rather than quietly rotating to someone else, and that the reason is carried back so
 * it can be recorded against the complaint.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeAssignmentStrategyServiceTest {

    private static final String OFFICE = "013";
    private static final String ROLE = "RBIO_OFFICER";

    @Mock private OfficeAssignmentStrategyRepository strategyRepository;
    @Mock private OfficeAssignmentMappingRepository mappingRepository;
    @Mock private AaOfficerPoolRepository officerPoolRepository;
    @Mock private DurableRoundRobinAssigner roundRobinAssigner;

    @InjectMocks private OfficeAssignmentStrategyService service;

    private void officeUses(String strategy) {
        when(strategyRepository.findByOfficeId(OFFICE)).thenReturn(Optional.of(
                OfficeAssignmentStrategy.builder()
                        .officeId(OFFICE).strategy(strategy).roleGroup(ROLE).build()));
    }

    private void mappingExists(String type, String key, String officer) {
        when(mappingRepository.findByOfficeIdAndMappingTypeAndSubjectKeyAndActiveTrue(OFFICE, type, key))
                .thenReturn(Optional.of(OfficeAssignmentMapping.builder()
                        .officeId(OFFICE).mappingType(type).subjectKey(key)
                        .targetOfficerId(officer).active(true).build()));
    }

    private AaOfficerPool poolRow(String userId, String roleGroup, String office,
                                  boolean active, boolean onLeave) {
        AaOfficerPool row = new AaOfficerPool();
        row.setUserId(userId);
        row.setRoleGroup(roleGroup);
        row.setRegionalOffice(office);
        row.setActive(active);
        row.setOnLeave(onLeave);
        return row;
    }

    private void rotationReturns(String officer) {
        when(roundRobinAssigner.assignNext(ROLE))
                .thenReturn(new DurableRoundRobinAssigner.Assignment(officer, null));
    }

    private void rotationExhausted() {
        when(roundRobinAssigner.assignNext(ROLE))
                .thenReturn(new DurableRoundRobinAssigner.Assignment(null, "pool empty"));
    }

    private void adminAtOffice(String userId, String office) {
        when(officerPoolRepository.findByRoleGroupOrderByUserIdAsc("RBIO_ADMIN"))
                .thenReturn(List.of(poolRow(userId, "RBIO_ADMIN", office, true, false)));
    }

    @Nested
    @DisplayName("Strategy selection")
    class Selection {

        @Test
        void anUnconfiguredOfficeRotates() {
            // Refusing to assign because nobody configured the office would stall every complaint filed
            // there — a far worse failure than defaulting to the behaviour offices already have.
            when(strategyRepository.findByOfficeId(OFFICE)).thenReturn(Optional.empty());
            rotationReturns("rbio.officer");

            var result = service.resolveOfficer(OFFICE, "HDFC Bank", 1L);

            assertThat(result.strategy()).isEqualTo(OfficeAssignmentStrategy.ROUND_ROBIN);
            assertThat(result.officerId()).isEqualTo("rbio.officer");
        }

        @Test
        void anUnrecognisedStrategyRotatesRatherThanGuessing() {
            // A typo in configuration must not silently become a working strategy; it rotates, loudly.
            when(strategyRepository.findByOfficeId(OFFICE)).thenReturn(Optional.of(
                    OfficeAssignmentStrategy.builder()
                            .officeId(OFFICE).strategy("ENTITY_MAPPINGS_TYPO").roleGroup(ROLE).build()));
            rotationReturns("rbio.officer");

            assertThat(service.resolveOfficer(OFFICE, "HDFC Bank", 1L).officerId())
                    .isEqualTo("rbio.officer");
        }

        @Test
        void entityMappingDoesNotConsultTheRotation() {
            // An office that chose entity mapping did so because named officers must handle named banks.
            // Rotating would break that arrangement while looking like it worked.
            officeUses(OfficeAssignmentStrategy.ENTITY_MAPPING);
            mappingExists(OfficeAssignmentMapping.TYPE_ENTITY, "HDFC BANK", "rbio.officer.mum");

            var result = service.resolveOfficer(OFFICE, "HDFC Bank", 1L);

            assertThat(result.officerId()).isEqualTo("rbio.officer.mum");
            verify(roundRobinAssigner, never()).assignNext(anyString());
        }
    }

    @Nested
    @DisplayName("Entity mapping (UST469)")
    class EntityMapping {

        @Test
        void matchesRegardlessOfPunctuationAndSpacing() {
            // The stored key is normalised, so 'H.D.F.C.  Bank.' must find the same mapping as 'HDFC BANK'.
            // Without normalisation a missed mapping silently falls back to the admin, and nothing looks wrong.
            officeUses(OfficeAssignmentStrategy.ENTITY_MAPPING);
            mappingExists(OfficeAssignmentMapping.TYPE_ENTITY, "HDFC BANK", "rbio.officer.mum");

            assertThat(service.resolveOfficer(OFFICE, "H.D.F.C.  Bank.", 1L).officerId())
                    .isEqualTo("rbio.officer.mum");
        }

        @Test
        void reportsTheReasonWhenNoMappingExists() {
            officeUses(OfficeAssignmentStrategy.ENTITY_MAPPING);
            when(mappingRepository.findByOfficeIdAndMappingTypeAndSubjectKeyAndActiveTrue(
                    eq(OFFICE), eq(OfficeAssignmentMapping.TYPE_ENTITY), anyString()))
                    .thenReturn(Optional.empty());
            adminAtOffice("orbio_admin_001", OFFICE);

            var result = service.resolveOfficer(OFFICE, "Some Unmapped Bank", 1L);

            // UST469/472: the REASON must be specific enough to act on. "no active mapping" tells an
            // administrator to add one; a generic failure would not.
            assertThat(result.outcome())
                    .isEqualTo(OfficeAssignmentStrategyService.Resolution.OUTCOME_ADMIN_FALLBACK);
            assertThat(result.reason()).contains("no active mapping");
        }

        @Test
        void fallsBackWhenTheComplaintNamesNoEntity() {
            officeUses(OfficeAssignmentStrategy.ENTITY_MAPPING);
            adminAtOffice("orbio_admin_001", OFFICE);

            var result = service.resolveOfficer(OFFICE, "  ", 1L);

            // A data gap, not an officer's absence — the reason must say so or an administrator will go
            // looking for a missing mapping that was never the problem.
            assertThat(result.reason()).contains("no entity on the complaint");
            assertThat(result.officerId()).isEqualTo("orbio_admin_001");
        }
    }

    @Nested
    @DisplayName("Category mapping")
    class CategoryMapping {

        @Test
        void routesByCategoryId() {
            officeUses(OfficeAssignmentStrategy.CATEGORY_MAPPING);
            mappingExists(OfficeAssignmentMapping.TYPE_CATEGORY, "4", "rbio.officer.del");

            assertThat(service.resolveOfficer(OFFICE, "Any Bank", 4L).officerId())
                    .isEqualTo("rbio.officer.del");
        }

        @Test
        void fallsBackWhenTheComplaintHasNoCategory() {
            officeUses(OfficeAssignmentStrategy.CATEGORY_MAPPING);
            adminAtOffice("orbio_admin_001", OFFICE);

            assertThat(service.resolveOfficer(OFFICE, "Any Bank", null).outcome())
                    .isEqualTo(OfficeAssignmentStrategyService.Resolution.OUTCOME_ADMIN_FALLBACK);
        }
    }

    @Nested
    @DisplayName("Ombudsman Admin fallback (UST470/472)")
    class AdminFallback {

        @Test
        void aMappedOfficerOnLeaveFallsBackToTheAdminRatherThanAnotherOfficer() {
            officeUses(OfficeAssignmentStrategy.ENTITY_MAPPING);
            mappingExists(OfficeAssignmentMapping.TYPE_ENTITY, "HDFC BANK", "rbio.officer.mum");
            when(officerPoolRepository.findByRoleGroupOrderByUserIdAsc(ROLE))
                    .thenReturn(List.of(poolRow("rbio.officer.mum", ROLE, OFFICE, true, true)));
            adminAtOffice("orbio_admin_001", OFFICE);

            var result = service.resolveOfficer(OFFICE, "HDFC Bank", 1L);

            assertThat(result.officerId()).isEqualTo("orbio_admin_001");
            assertThat(result.reason()).contains("on leave");
            // Critically NOT a substitute officer: the mapping is a deliberate assignment of this bank to
            // this person, and silently rotating past it would break the arrangement invisibly.
            verify(roundRobinAssigner, never()).assignNext(anyString());
        }

        @Test
        void usesOnlyTheAdminBelongingToThisOffice() {
            // An admin in another office has no jurisdiction over this complaint, so borrowing one would
            // place the file with someone who cannot lawfully act on it.
            officeUses(OfficeAssignmentStrategy.ENTITY_MAPPING);
            when(mappingRepository.findByOfficeIdAndMappingTypeAndSubjectKeyAndActiveTrue(
                    anyString(), anyString(), anyString())).thenReturn(Optional.empty());
            when(officerPoolRepository.findByRoleGroupOrderByUserIdAsc("RBIO_ADMIN"))
                    .thenReturn(List.of(poolRow("other_admin", "RBIO_ADMIN", "014", true, false)));

            var result = service.resolveOfficer(OFFICE, "Unmapped Bank", 1L);

            assertThat(result.isAssigned()).isFalse();
            assertThat(result.outcome())
                    .isEqualTo(OfficeAssignmentStrategyService.Resolution.OUTCOME_UNASSIGNED);
        }

        @Test
        void skipsAnAdminWhoIsOnLeave() {
            officeUses(OfficeAssignmentStrategy.ENTITY_MAPPING);
            when(mappingRepository.findByOfficeIdAndMappingTypeAndSubjectKeyAndActiveTrue(
                    anyString(), anyString(), anyString())).thenReturn(Optional.empty());
            when(officerPoolRepository.findByRoleGroupOrderByUserIdAsc("RBIO_ADMIN"))
                    .thenReturn(List.of(poolRow("orbio_admin_001", "RBIO_ADMIN", OFFICE, true, true)));

            assertThat(service.resolveOfficer(OFFICE, "Unmapped Bank", 1L).isAssigned()).isFalse();
        }

        @Test
        void anExhaustedRotationAlsoFallsBackToTheAdmin() {
            // Every officer inactive or on leave is exactly when the office's admin should hold the file.
            officeUses(OfficeAssignmentStrategy.ROUND_ROBIN);
            rotationExhausted();
            adminAtOffice("orbio_admin_001", OFFICE);

            var result = service.resolveOfficer(OFFICE, "Any Bank", 1L);

            assertThat(result.officerId()).isEqualTo("orbio_admin_001");
            assertThat(result.outcome())
                    .isEqualTo(OfficeAssignmentStrategyService.Resolution.OUTCOME_ADMIN_FALLBACK);
        }
    }

    @Nested
    @DisplayName("Preview must not disturb the rota")
    class DryRun {

        @Test
        void aDryRunDoesNotAdvanceTheRotationPointer() {
            // The admin preview is a GET. Advancing the pointer to answer "who would get this?" would
            // change who the NEXT real complaint goes to, so merely inspecting configuration would
            // silently reorder the rota.
            officeUses(OfficeAssignmentStrategy.ROUND_ROBIN);
            when(roundRobinAssigner.currentPointer(ROLE)).thenReturn(Optional.of("rbio.officer"));

            var result = service.resolveOfficer(OFFICE, "Any Bank", 1L, true);

            verify(roundRobinAssigner, never()).assignNext(anyString());
            assertThat(result.reason()).contains("Rotation is currently at rbio.officer");
        }
    }

    @Nested
    @DisplayName("Administration")
    class Administration {

        @Test
        void rejectsAnUnknownStrategy() {
            assertThatThrownBy(() ->
                    service.setStrategy(OFFICE, "MAGIC", ROLE, "cms.admin", "because"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown assignment strategy");
        }

        @Test
        void requiresAReasonForThePolicyChange() {
            // An assignment-policy change with no recorded rationale cannot be reviewed later, and this is
            // exactly the setting somebody asks about when complaints start landing unexpectedly.
            assertThatThrownBy(() ->
                    service.setStrategy(OFFICE, OfficeAssignmentStrategy.ENTITY_MAPPING, ROLE, "cms.admin", " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason is required");
        }

        @Test
        void recordsWhoChangedItAndWhy() {
            when(strategyRepository.findByOfficeId(OFFICE)).thenReturn(Optional.empty());
            when(strategyRepository.save(any(OfficeAssignmentStrategy.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var saved = service.setStrategy(OFFICE, OfficeAssignmentStrategy.CATEGORY_MAPPING,
                    ROLE, "cms.admin", "Pilot for Mumbai-I");

            assertThat(saved.getStrategy()).isEqualTo(OfficeAssignmentStrategy.CATEGORY_MAPPING);
            assertThat(saved.getUpdatedBy()).isEqualTo("cms.admin");
            assertThat(saved.getReason()).isEqualTo("Pilot for Mumbai-I");
        }

        @Test
        void normalisesAnEntitySubjectOnWriteSoLookupsMatch() {
            when(mappingRepository.findByOfficeIdAndMappingTypeAndSubjectKeyAndActiveTrue(
                    anyString(), anyString(), anyString())).thenReturn(Optional.empty());
            when(mappingRepository.save(any(OfficeAssignmentMapping.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var saved = service.upsertMapping(OFFICE, OfficeAssignmentMapping.TYPE_ENTITY,
                    "  H.D.F.C. Bank  ", "rbio.officer.mum", "cms.admin");

            assertThat(saved.getSubjectKey()).isEqualTo("HDFC BANK");
            // The label keeps what was typed, so an admin screen shows the entity as entered.
            assertThat(saved.getSubjectLabel()).isEqualTo("H.D.F.C. Bank");
        }

        @Test
        void rejectsAMappingWithNoTargetOfficer() {
            assertThatThrownBy(() -> service.upsertMapping(OFFICE,
                    OfficeAssignmentMapping.TYPE_ENTITY, "HDFC Bank", " ", "cms.admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetOfficerId is required");
        }

        @Test
        void rejectsAnUnknownMappingType() {
            assertThatThrownBy(() -> service.upsertMapping(OFFICE,
                    "BRANCH", "HDFC Bank", "rbio.officer.mum", "cms.admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mappingType must be ENTITY or CATEGORY");
        }
    }
}
