package com.hrms.cms.controller;

import com.hrms.cms.security.RbioIdentityResolver;
import com.hrms.cms.service.RbioComplaintListService;
import com.hrms.cms.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the RBIO list endpoint's REFUSALS and its empty-state contract.
 *
 * <h2>Why a plain unit test and not a slice test</h2>
 * These assertions are about what the controller does BEFORE it reaches the service — which criteria it
 * refuses, and which message key it attaches to an empty result. Calling the method directly keeps the
 * test independent of the rest of the application compiling, which matters in a repository where seven
 * sessions share one {@code target/} directory.
 *
 * <h2>What would otherwise go unnoticed</h2>
 * The dangerous outcome here is not an exception — it is a 200 whose result set silently answers a
 * different question than the one asked, because a criterion the client sent was never read. So the
 * central test is {@link Refusals#nodalOfficerSearchIsRefusedRatherThanSilentlyDropped}: it asserts the
 * service is NEVER CALLED, which is the only way to prove the criterion was not quietly discarded.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RbioComplaintListControllerTest {

    @Mock private RbioComplaintListService listService;
    @Mock private RbioIdentityResolver identityResolver;
    @Mock private SystemConfigService systemConfigService;
    @Mock private com.hrms.cms.security.CmsPrincipalResolver principalResolver;

    private RbioComplaintListController controller;

    @BeforeEach
    void setUp() {
        controller = new RbioComplaintListController(
                listService, identityResolver, systemConfigService, principalResolver);
        // A caller holding nothing, so canCloseFinal is false unless a test grants it. Defaulting to
        // granted would make the closure flag look permissive in every unrelated assertion.
        when(principalResolver.resolve()).thenReturn(new com.hrms.cms.security.CmsPrincipalResolver.Principal(
                "rbio_do_001", null, java.util.Set.of(),
                com.hrms.cms.security.CmsPrincipalResolver.PrincipalType.RBI));
        when(identityResolver.resolveRbioRole()).thenReturn("RBIO_DEALING_OFFICIAL");
        when(identityResolver.resolveActor()).thenReturn("rbio_do_001");
        when(systemConfigService.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        when(systemConfigService.getString(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        when(listService.search(any())).thenReturn(emptyPage());
    }

    private Page<com.hrms.cms.entity.Complaint> emptyPage() {
        return new PageImpl<>(List.of());
    }

    /** Calls the endpoint with only the parameters a test cares about. */
    private ResponseEntity<Map<String, Object>> list(String complainantName, String nodalOfficerName,
                                                     String complaintNumber) {
        return controller.listComplaints(
                null, null, null, null, null, null, null, null,
                0, 20, "createdAt", "desc",
                complaintNumber, complainantName, null, null, null, null, null, null, null,
                nodalOfficerName, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(ResponseEntity<Map<String, Object>> res) {
        return (Map<String, Object>) res.getBody().get("data");
    }

    @Nested
    @DisplayName("Criteria the schema cannot answer are refused, not ignored")
    class Refusals {

        @Test
        void nodalOfficerSearchIsRefusedRatherThanSilentlyDropped() {
            // THE test in this class. COMPLAINTS has no nodal-officer column. The tempting alternative was
            // to accept the parameter and ignore it, which answers 200 with every complaint the other
            // criteria matched — an answer to a question the officer did not ask. Verifying the service is
            // never called is what distinguishes "refused" from "silently dropped"; asserting only on the
            // status code would pass if the controller returned 400 AND still ran the query.
            var res = list(null, "Ramesh Kumar", null);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(res.getBody().get("success")).isEqualTo(false);
            assertThat(res.getBody().get("messageKey"))
                    .isEqualTo("rbio.search.error_nodal_officer_unsupported");
            verify(listService, never()).search(any());
        }

        @Test
        void aSingleCharacterPartialTermIsRefused() {
            var res = list("Z", null, null);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(res.getBody().get("messageKey")).isEqualTo("rbio.search.error_term_too_short");
            verify(listService, never()).search(any());
        }

        @Test
        void theRefusalNamesTheOffendingField() {
            // So the UI can highlight the right box. A generic "bad request" makes the officer re-check
            // every field.
            var res = list("Z", null, null);

            assertThat((String) res.getBody().get("message")).contains("complainantName");
        }

        @Test
        void aTwoCharacterTermIsAccepted() {
            var res = list("Ze", null, null);

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            verify(listService).search(any());
        }

        @Test
        void aBlankNodalOfficerParameterIsNotARefusal() {
            // An empty query string arrives as "" from many HTTP clients. Refusing that would break a
            // search whose other criteria are perfectly valid.
            var res = list("Zephyrine", "   ", null);

            assertThat(res.getStatusCode().value()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Empty results distinguish 'no match' from 'no work' (UST442)")
    class EmptyState {

        @Test
        void anEmptySearchResultSaysNoMatch() {
            var res = list("Zephyrine", null, null);

            assertThat(dataOf(res).get("searchApplied")).isEqualTo(true);
            assertThat(dataOf(res).get("emptyMessageKey")).isEqualTo("rbio.search.no_results");
        }

        @Test
        void anEmptyQueueWithNoSearchSaysNoComplaints() {
            // Telling an officer "no complaints match your search" when they never searched, or worse
            // "your queue is empty" when their search simply missed, are both ways of hiding real work.
            var res = list(null, null, null);

            assertThat(dataOf(res).get("searchApplied")).isEqualTo(false);
            assertThat(dataOf(res).get("emptyMessageKey")).isEqualTo("rbio.grid.no_complaints");
        }

        @Test
        void closureCapabilityIsFalseWithoutTheSsoAuthority() {
            // UST631. The flag only tells the UI what to hide; the control is @RequiresAuthority on the
            // close action. It must default to FALSE so a reviewer who was never granted the authority is
            // not offered an option the server would then refuse.
            var res = list(null, null, null);

            assertThat(dataOf(res).get("canCloseFinal")).isEqualTo(false);
        }

        @Test
        void closureCapabilityIsTrueOnlyWhenTheAuthorityIsHeld() {
            when(principalResolver.resolve()).thenReturn(
                    new com.hrms.cms.security.CmsPrincipalResolver.Principal(
                            "rbio_reviewer_001", null,
                            java.util.Set.of(com.hrms.cms.security.CmsAuthority
                                    .RBIO_COMPLAINT_CLOSE_FINAL.authority()),
                            com.hrms.cms.security.CmsPrincipalResolver.PrincipalType.RBI));

            var res = list(null, null, null);

            assertThat(dataOf(res).get("canCloseFinal")).isEqualTo(true);
        }

        @Test
        void theCallerRoleIsEchoedFromTheTokenNotTheRequest() {
            // The grid uses this to pick its filter list. If it came from a parameter, an officer could
            // request another rank's tabs.
            var res = list(null, null, null);

            assertThat(dataOf(res).get("callerRole")).isEqualTo("RBIO_DEALING_OFFICIAL");
        }
    }

    @Nested
    @DisplayName("Grid configuration (UST436)")
    class GridConfig {

        @Test
        void allSixBandsAreServedWithAColourAndALabelKey() {
            var res = controller.gridConfig();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bands = (List<Map<String, Object>>) dataOf(res).get("bands");

            assertThat(bands).hasSize(6);
            assertThat(bands.stream().map(b -> b.get("code")))
                    .containsExactlyInAnyOrder("WHITE", "RED", "GREEN", "YELLOW", "PINK", "BLUE");
            for (Map<String, Object> band : bands) {
                assertThat((String) band.get("colour")).matches("^#[0-9a-fA-F]{6}$");
                assertThat((String) band.get("labelKey")).startsWith("rbio.band.");
            }
        }

        @Test
        void theCrpcDelayThresholdComesFromConfiguration() {
            when(systemConfigService.getInt(
                    RbioComplaintListController.CFG_CRPC_DELAY_DAYS, 3)).thenReturn(7);

            var res = controller.gridConfig();

            // Proves the value is READ from config rather than returned as a literal. Asserting the
            // default of 3 would pass against a hardcoded 3.
            assertThat(dataOf(res).get("crpcDelayDays")).isEqualTo(7);
        }

        @Test
        void bandColoursAreOverridableFromConfiguration() {
            when(systemConfigService.getString(
                    RbioComplaintListController.CFG_BAND_PREFIX + "red", "#fee2e2")).thenReturn("#aa0000");

            var res = controller.gridConfig();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bands = (List<Map<String, Object>>) dataOf(res).get("bands");
            var red = bands.stream().filter(b -> "RED".equals(b.get("code"))).findFirst().orElseThrow();
            assertThat(red.get("colour")).isEqualTo("#aa0000");
        }
    }
}
