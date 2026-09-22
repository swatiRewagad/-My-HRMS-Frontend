package com.hrms.cms.service;

import com.hrms.cms.entity.OfficeThresholdConfig;
import com.hrms.cms.repository.OfficeThresholdConfigRepository;
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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers office capacity routing and the overflow chain.
 *
 * <p>Written because this service had NO test of any kind despite deciding which Ombudsman office
 * holds a citizen's complaint — and therefore which office may lawfully hear it and its appeal.
 *
 * <p>The assertions target the behaviours that were previously wrong rather than restating the
 * implementation: that capacity is claimed atomically (never read-then-write), that the configured
 * {@code overflowTargetOffice} chain is actually followed instead of ignored, that a saturated
 * department does NOT have every counter reset, and that an unconfigured office fails closed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfficeRoutingServiceTest {

    @Mock private OfficeThresholdConfigRepository thresholdRepo;
    @Mock private SystemConfigService systemConfigService;

    @InjectMocks private OfficeRoutingService service;

    @BeforeEach
    void enableEnforcementByDefault() {
        when(systemConfigService.getBoolean(anyString(), anyBoolean())).thenReturn(true);
    }

    private OfficeThresholdConfig office(String id, String name, int max, int current, String overflowTo) {
        return OfficeThresholdConfig.builder()
                .officeId(id).officeName(name).department("RBIO")
                .maxThreshold(max).currentCount(current)
                .overflowTargetOffice(overflowTo)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        void assignsToTheTerritorialOfficeWhenItHasCapacity() {
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 10, "014")));
            when(thresholdRepo.claimCapacity("013")).thenReturn(1);

            Map<String, Object> result = service.routeToOffice("013", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_ASSIGNED);
            assertThat(result.get("officeId")).isEqualTo("013");
            // Capacity must be claimed through the atomic guarded UPDATE, never a read-modify-write.
            verify(thresholdRepo).claimCapacity("013");
            verify(thresholdRepo, never()).save(any());
        }

        @Test
        void doesNotConsultTheOverflowChainWhileTheOfficeHasCapacity() {
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 0, "014")));
            when(thresholdRepo.claimCapacity("013")).thenReturn(1);

            service.routeToOffice("013", false);

            verify(thresholdRepo, never()).claimCapacity("014");
        }
    }

    @Nested
    @DisplayName("Overflow chain")
    class Overflow {

        @Test
        void followsTheConfiguredOverflowTargetWhenPrimaryIsFull() {
            // The chain column was previously ignored entirely: the service re-scanned the whole
            // department by sequence order, so the configured topology was unenforced.
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 500, "014")));
            when(thresholdRepo.findByOfficeId("014")).thenReturn(Optional.of(office("014", "New Delhi-I", 500, 3, "015")));
            when(thresholdRepo.claimCapacity("013")).thenReturn(0);
            when(thresholdRepo.claimCapacity("014")).thenReturn(1);

            Map<String, Object> result = service.routeToOffice("013", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_OVERFLOW);
            assertThat(result.get("officeId")).isEqualTo("014");
            assertThat(result.get("primaryOfficeId")).isEqualTo("013");
        }

        @Test
        void walksPastAFullOverflowOfficeToTheNextHop() {
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 500, "014")));
            when(thresholdRepo.findByOfficeId("014")).thenReturn(Optional.of(office("014", "New Delhi-I", 500, 500, "015")));
            when(thresholdRepo.findByOfficeId("015")).thenReturn(Optional.of(office("015", "Thiruvananthapuram", 500, 1, null)));
            when(thresholdRepo.claimCapacity("013")).thenReturn(0);
            when(thresholdRepo.claimCapacity("014")).thenReturn(0);
            when(thresholdRepo.claimCapacity("015")).thenReturn(1);

            Map<String, Object> result = service.routeToOffice("013", false);

            assertThat(result.get("officeId")).isEqualTo("015");
            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_OVERFLOW);
        }

        @Test
        void terminatesOnAMisconfiguredCycleInsteadOfSpinningForever() {
            // A -> B -> A. Nothing prevents an operator seeding this, and an infinite loop inside a
            // @Transactional registration would hang the filing request.
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 500, "014")));
            when(thresholdRepo.findByOfficeId("014")).thenReturn(Optional.of(office("014", "New Delhi-I", 500, 500, "013")));
            when(thresholdRepo.claimCapacity(anyString())).thenReturn(0);

            Map<String, Object> result = service.routeToOffice("013", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_AT_CAPACITY);
        }

        @Test
        void stopsAtAnInactiveOverflowTargetRatherThanRoutingIntoIt() {
            OfficeThresholdConfig inactive = office("014", "New Delhi-I", 500, 0, null);
            inactive.setActive(false);
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 500, "014")));
            when(thresholdRepo.findByOfficeId("014")).thenReturn(Optional.of(inactive));
            when(thresholdRepo.claimCapacity("013")).thenReturn(0);

            Map<String, Object> result = service.routeToOffice("013", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_AT_CAPACITY);
            verify(thresholdRepo, never()).claimCapacity("014");
        }

        @Test
        void doesNotResetEveryCounterWhenTheWholeDepartmentIsSaturated() {
            // The previous implementation zeroed EVERY counter in the department here, discarding the
            // real load of every other office (each still holding maxThreshold live complaints) and
            // admitting the complaint past a limit it had just declared breached.
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 500, null)));
            when(thresholdRepo.claimCapacity("013")).thenReturn(0);

            Map<String, Object> result = service.routeToOffice("013", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_AT_CAPACITY);
            // Complaint stays at its territorially correct office...
            assertThat(result.get("officeId")).isEqualTo("013");
            verify(thresholdRepo).incrementUnconditionally("013");
            // ...and no counter is wiped.
            verify(thresholdRepo, never()).resetCountersForDepartment(anyString());
        }
    }

    @Nested
    @DisplayName("Vernacular override (UST467)")
    class Vernacular {

        @Test
        void retainsTheComplaintAtTheLanguageCapableOfficeEvenWhenFull() {
            // Diverting would leave the citizen with an office that cannot read their complaint.
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 500, "014")));

            Map<String, Object> result = service.routeToOffice("013", true);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_VERNACULAR);
            assertThat(result.get("officeId")).isEqualTo("013");
            verify(thresholdRepo).incrementUnconditionally("013");
            verify(thresholdRepo, never()).claimCapacity(anyString());
        }
    }

    @Nested
    @DisplayName("Fail closed")
    class FailClosed {

        @Test
        void refusesToRouteWhenTheOfficeHasNoCapacityRow() {
            when(thresholdRepo.findByOfficeId("999")).thenReturn(Optional.empty());

            Map<String, Object> result = service.routeToOffice("999", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_NOT_FOUND);
            verify(thresholdRepo, never()).claimCapacity(anyString());
            verify(thresholdRepo, never()).incrementUnconditionally(anyString());
        }

        @Test
        void refusesToRouteToAnInactiveOffice() {
            OfficeThresholdConfig inactive = office("013", "Mumbai-I", 500, 0, null);
            inactive.setActive(false);
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(inactive));

            Map<String, Object> result = service.routeToOffice("013", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_NOT_FOUND);
        }

        @Test
        void refusesABlankTargetOffice() {
            Map<String, Object> result = service.routeToOffice("  ", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_NOT_FOUND);
            verifyNoInteractions(thresholdRepo);
        }
    }

    @Nested
    @DisplayName("Administrative transfers and thresholds")
    class Administrative {

        @Test
        void transferInIsRefusedWhenTheReceivingOfficeIsAtCapacity() {
            // Previously this incremented unconditionally with no return value, making it the one live
            // write path that could push any office past its declared capacity.
            when(thresholdRepo.claimCapacity("013")).thenReturn(0);

            assertThat(service.incrementOffice("013")).isFalse();
        }

        @Test
        void transferInSucceedsWhenCapacityRemains() {
            when(thresholdRepo.claimCapacity("013")).thenReturn(1);

            assertThat(service.incrementOffice("013")).isTrue();
        }

        @Test
        void releaseIsFlooredSoDoubleReleaseCannotGrantUnlimitedCapacity() {
            service.decrementOffice("013");

            // The floor lives in the SQL predicate (currentCount > 0), so the service must delegate
            // rather than compute the new value itself.
            verify(thresholdRepo).releaseCapacity("013");
        }

        @Test
        void rejectsANonPositiveThreshold() {
            assertThatThrownBy(() -> service.updateThreshold("013", 0, "admin"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Enforcement toggle")
    class EnforcementToggle {

        @Test
        void recordsTheOfficeWithoutDivertingWhenEnforcementIsDisabled() {
            when(systemConfigService.getBoolean(anyString(), anyBoolean())).thenReturn(false);
            when(thresholdRepo.findByOfficeId("013")).thenReturn(Optional.of(office("013", "Mumbai-I", 500, 500, "014")));

            Map<String, Object> result = service.routeToOffice("013", false);

            assertThat(result.get("status")).isEqualTo(OfficeRoutingService.STATUS_ASSIGNED);
            assertThat(result.get("officeId")).isEqualTo("013");
            verify(thresholdRepo, never()).claimCapacity(anyString());
        }
    }
}
