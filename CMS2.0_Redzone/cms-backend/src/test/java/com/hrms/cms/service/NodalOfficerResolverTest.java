package com.hrms.cms.service;

import com.hrms.cms.entity.EntityOfficeNodalOfficer;
import com.hrms.cms.entity.OfficeThresholdConfig;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.EntityOfficeNodalOfficerRepository;
import com.hrms.cms.repository.OfficeThresholdConfigRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.repository.SystemConfigRepository;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NodalOfficerResolver} — UST773's (entity + office) resolution and UST571's
 * PNO / Ombudsman-Admin fallbacks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodalOfficerResolverTest {

    private static final String SBI = "State Bank of India";
    private static final String SBI_NORM = "STATE BANK OF INDIA";
    private static final String MUMBAI = "Mumbai I";

    @Mock private EntityOfficeNodalOfficerRepository entityOfficeRepository;
    @Mock private RegulatedEntityRepository regulatedEntityRepository;
    @Mock private OfficeThresholdConfigRepository officeThresholdRepository;
    @Mock private SystemConfigRepository systemConfigRepository;

    @InjectMocks
    private NodalOfficerResolver resolver;

    private EntityOfficeNodalOfficer mapping(String office, String noName, String pnoName) {
        return EntityOfficeNodalOfficer.builder()
                .entityName(SBI)
                .entityNameNormalized(SBI_NORM)
                .processingOffice(office)
                .nodalOfficerName(noName)
                .nodalOfficerDesignation(noName == null ? null : "Deputy General Manager")
                .nodalOfficerEmail(noName == null ? null : "no.mumbai@sbi.example")
                .nodalOfficerPhone(noName == null ? null : "9876543210")
                .pnoName(pnoName)
                .pnoEmail(pnoName == null ? null : "pno@sbi.example")
                .pnoPhone(pnoName == null ? null : "9811111111")
                .active(true)
                .build();
    }

    private void noOfficeSpecificRow() {
        when(entityOfficeRepository.findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
    }

    private void noEntityDefaultRow() {
        when(entityOfficeRepository.findFirstByEntityNameNormalizedAndProcessingOfficeIsNullAndActiveTrue(any()))
                .thenReturn(Optional.empty());
    }

    private void noRegulatedEntity() {
        when(regulatedEntityRepository.findByNameNormalized(any())).thenReturn(Optional.empty());
    }

    // ═══════════════════════════════════════════════════════════════════
    // UST773: (entity + office) resolution
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UST773 — resolution from (entity + office)")
    class EntityOfficeResolution {

        @Test
        @DisplayName("office-specific row wins, so a Mumbai complaint does not get the national desk")
        void officeSpecificRowWins() {
            when(entityOfficeRepository
                    .findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(SBI_NORM, MUMBAI))
                    .thenReturn(Optional.of(mapping(MUMBAI, "Mumbai Nodal Officer", "National PNO")));

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, MUMBAI);

            assertThat(result.getSource())
                    .as("an office-specific mapping exists; using any lower rung would route a Mumbai "
                        + "complaint to the wrong officer")
                    .isEqualTo(NodalOfficerResolver.Source.ENTITY_OFFICE);
            assertThat(result.getNodalOfficerName()).isEqualTo("Mumbai Nodal Officer");
            assertThat(result.getProcessingOffice()).isEqualTo(MUMBAI);
            assertThat(result.isFallbackToAdmin()).isFalse();

            // The entity-wide and master-data rungs must not even be consulted once the specific row hits.
            verify(entityOfficeRepository, never())
                    .findFirstByEntityNameNormalizedAndProcessingOfficeIsNullAndActiveTrue(any());
            verify(regulatedEntityRepository, never()).findByNameNormalized(any());
        }

        @Test
        @DisplayName("the entity name is normalised before lookup, so punctuation/case differences still match")
        void normalisesEntityNameBeforeLookup() {
            when(entityOfficeRepository
                    .findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(SBI_NORM, MUMBAI))
                    .thenReturn(Optional.of(mapping(MUMBAI, "Mumbai Nodal Officer", null)));

            NodalOfficerResolver.Resolution result = resolver.resolve("  state bank of india.  ", MUMBAI);

            assertThat(result.getSource())
                    .as("COMPLAINTS.entity_code is free text; if the resolver passed it through raw, a "
                        + "trailing full stop would silently lose the entity's NO mapping")
                    .isEqualTo(NodalOfficerResolver.Source.ENTITY_OFFICE);
        }

        @Test
        @DisplayName("falls to the entity-wide (null office) row when no office-specific row exists")
        void fallsBackToEntityWideRow() {
            noOfficeSpecificRow();
            when(entityOfficeRepository
                    .findFirstByEntityNameNormalizedAndProcessingOfficeIsNullAndActiveTrue(SBI_NORM))
                    .thenReturn(Optional.of(mapping(null, "National Nodal Officer", null)));

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, MUMBAI);

            assertThat(result.getSource()).isEqualTo(NodalOfficerResolver.Source.ENTITY_DEFAULT);
            assertThat(result.getNodalOfficerName()).isEqualTo("National Nodal Officer");
        }

        @Test
        @DisplayName("a mapping row with a blank NO name does not halt the ladder")
        void blankNameInMappingDoesNotHaltLadder() {
            when(entityOfficeRepository
                    .findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(SBI_NORM, MUMBAI))
                    .thenReturn(Optional.of(mapping(MUMBAI, "   ", null)));
            noEntityDefaultRow();
            when(regulatedEntityRepository.findByNameNormalized(SBI_NORM))
                    .thenReturn(Optional.of(RegulatedEntity.builder()
                            .name(SBI).nameNormalized(SBI_NORM).department("RBIO")
                            .nodalOfficerName("Master NO")
                            .nodalOfficerEmail("master.no@sbi.example")
                            .build()));

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, MUMBAI);

            assertThat(result.getSource())
                    .as("a half-filled admin row must not shadow usable master data, otherwise saving a "
                        + "blank mapping would strand every complaint for that entity")
                    .isEqualTo(NodalOfficerResolver.Source.REGULATED_ENTITY);
            assertThat(result.getNodalOfficerName()).isEqualTo("Master NO");
        }

        @Test
        @DisplayName("a null processing office skips the office-specific query rather than querying on null")
        void nullOfficeSkipsOfficeSpecificQuery() {
            noEntityDefaultRow();
            noRegulatedEntity();
            when(officeThresholdRepository.findByActiveTrueOrderByOverflowSequenceOrderAsc())
                    .thenReturn(List.of());

            resolver.resolve(SBI, null);

            verify(entityOfficeRepository, never())
                    .findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Contacts already held on REGULATED_ENTITIES
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("REGULATED_ENTITIES contacts")
    class RegulatedEntityContacts {

        @Test
        @DisplayName("copies NO name, designation, email and phone from the entity master")
        void copiesAllContactFieldsFromMaster() {
            noOfficeSpecificRow();
            noEntityDefaultRow();
            when(regulatedEntityRepository.findByNameNormalized(SBI_NORM))
                    .thenReturn(Optional.of(RegulatedEntity.builder()
                            .name(SBI).nameNormalized(SBI_NORM).department("RBIO")
                            .nodalOfficerName("Master NO")
                            .nodalOfficerDesignation("Chief Manager")
                            .nodalOfficerEmail("master.no@sbi.example")
                            .nodalOfficerPhone("9822222222")
                            .pnoName("Master PNO")
                            .build()));

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, MUMBAI);

            assertThat(result.getSource()).isEqualTo(NodalOfficerResolver.Source.REGULATED_ENTITY);
            assertThat(result.getNodalOfficerName()).isEqualTo("Master NO");
            assertThat(result.getDesignation())
                    .as("designation must be carried across; dropping it leaves the officer unable to "
                        + "address correspondence properly")
                    .isEqualTo("Chief Manager");
            assertThat(result.getEmail()).isEqualTo("master.no@sbi.example");
            assertThat(result.getPhone()).isEqualTo("9822222222");
            assertThat(result.getPnoName()).isEqualTo("Master PNO");
            assertThat(result.getAssignedTo()).isEqualTo("master.no@sbi.example");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UST571: PNO fallback, then Ombudsman Admin
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UST571 — fallbacks when no Nodal Officer is on record")
    class Ust571Fallbacks {

        @Test
        @DisplayName("uses the PNO from the entity/office mapping when that row has no NO")
        void pnoFromMappingUsedWhenNoNodalOfficer() {
            when(entityOfficeRepository
                    .findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(SBI_NORM, MUMBAI))
                    .thenReturn(Optional.of(mapping(MUMBAI, null, "Mumbai PNO")));
            noEntityDefaultRow();
            noRegulatedEntity();

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, MUMBAI);

            assertThat(result.getSource())
                    .as("a PNO is on record, so falling straight through to the Ombudsman Admin would "
                        + "make RBI answer a complaint the bank still has a contact for")
                    .isEqualTo(NodalOfficerResolver.Source.PNO_FALLBACK);
            assertThat(result.getPnoName()).isEqualTo("Mumbai PNO");
            assertThat(result.getAssignedTo()).isEqualTo("pno@sbi.example");
            assertThat(result.isFallbackToAdmin()).isFalse();
        }

        @Test
        @DisplayName("uses the PNO held on REGULATED_ENTITIES when no mapping row has one")
        void pnoFromRegulatedEntityMaster() {
            noOfficeSpecificRow();
            noEntityDefaultRow();
            when(regulatedEntityRepository.findByNameNormalized(SBI_NORM))
                    .thenReturn(Optional.of(RegulatedEntity.builder()
                            .name(SBI).nameNormalized(SBI_NORM).department("RBIO")
                            .pnoName("Master PNO")
                            .pnoEmail("master.pno@sbi.example")
                            .build()));

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, MUMBAI);

            assertThat(result.getSource()).isEqualTo(NodalOfficerResolver.Source.PNO_FALLBACK);
            assertThat(result.getAssignedTo()).isEqualTo("master.pno@sbi.example");
        }

        @Test
        @DisplayName("falls back to the regional Ombudsman Admin derived from the active office row")
        void regionalOmbudsmanAdminFallback() {
            noOfficeSpecificRow();
            noEntityDefaultRow();
            noRegulatedEntity();
            when(officeThresholdRepository.findByActiveTrueOrderByOverflowSequenceOrderAsc())
                    .thenReturn(List.of(OfficeThresholdConfig.builder()
                            .officeId("RBIO-MUM").officeName(MUMBAI).department("RBIO").active(true).build()));

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, MUMBAI);

            assertThat(result.getSource()).isEqualTo(NodalOfficerResolver.Source.OMBUDSMAN_ADMIN);
            assertThat(result.isFallbackToAdmin()).isTrue();
            assertThat(result.getAssignedTo())
                    .as("UST571 wants the REGIONAL admin; the global token would drop the complaint into "
                        + "a national queue that no office owns")
                    .isEqualTo("RBIO-MUM_ADMIN");
            assertThat(result.getNodalOfficerName()).isNull();
        }

        @Test
        @DisplayName("an inactive office does not yield a regional admin")
        void inactiveOfficeIsNotUsedAsRegionalAdmin() {
            noOfficeSpecificRow();
            noEntityDefaultRow();
            noRegulatedEntity();
            // The repository method already filters on active, so an inactive office simply is not returned.
            when(officeThresholdRepository.findByActiveTrueOrderByOverflowSequenceOrderAsc())
                    .thenReturn(List.of());
            when(systemConfigRepository.findByConfigKey(NodalOfficerResolver.GLOBAL_ADMIN_CONFIG_KEY))
                    .thenReturn(Optional.empty());

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, MUMBAI);

            assertThat(result.getAssignedTo()).isEqualTo(NodalOfficerResolver.DEFAULT_GLOBAL_ADMIN_ROLE);
        }

        @Test
        @DisplayName("the global admin token comes from SYSTEM_CONFIG, not from a hardcoded role name")
        void globalAdminTokenIsConfigurable() {
            noOfficeSpecificRow();
            noEntityDefaultRow();
            noRegulatedEntity();
            when(officeThresholdRepository.findByActiveTrueOrderByOverflowSequenceOrderAsc())
                    .thenReturn(List.of());
            when(systemConfigRepository.findByConfigKey(NodalOfficerResolver.GLOBAL_ADMIN_CONFIG_KEY))
                    .thenReturn(Optional.of(SystemConfig.builder()
                            .configKey(NodalOfficerResolver.GLOBAL_ADMIN_CONFIG_KEY)
                            .configValue("RBIO_CENTRAL_ADMIN")
                            .build()));

            NodalOfficerResolver.Resolution result = resolver.resolve(SBI, "Unknown Office");

            assertThat(result.getAssignedTo())
                    .as("operators must be able to repoint the fallback without a release")
                    .isEqualTo("RBIO_CENTRAL_ADMIN");
        }

        @Test
        @DisplayName("a blank entity name falls straight to the admin instead of guessing an entity")
        void blankEntityNameFallsToAdmin() {
            when(officeThresholdRepository.findByActiveTrueOrderByOverflowSequenceOrderAsc())
                    .thenReturn(List.of());

            NodalOfficerResolver.Resolution result = resolver.resolve("   ", MUMBAI);

            assertThat(result.getSource()).isEqualTo(NodalOfficerResolver.Source.OMBUDSMAN_ADMIN);
            // No entity key exists, so no entity lookup may be attempted — a fuzzy guess here would
            // attribute the complaint to a bank nobody named.
            verify(entityOfficeRepository, never())
                    .findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(any(), any());
            verify(regulatedEntityRepository, never()).findByNameNormalized(any());
        }
    }
}
