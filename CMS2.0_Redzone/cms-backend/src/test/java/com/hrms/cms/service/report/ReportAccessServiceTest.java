package com.hrms.cms.service.report;

import com.hrms.cms.entity.RbioStaffProfile;
import com.hrms.cms.entity.ReportAccessRole;
import com.hrms.cms.repository.RbioStaffProfileRepository;
import com.hrms.cms.repository.ReportAccessRoleRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proves the report scope FAILS CLOSED.
 *
 * <h2>What these tests are really defending</h2>
 * The previous implementation took the caller's role and department from {@code X-User-Role} (default
 * {@code "SENIOR"}) and {@code X-User-Department} (default {@code ""}), and granted unrestricted access
 * for either value. A request with no headers therefore read every complaint in the database — up to
 * 5000 complainants' names, addresses and grievances per response. Every test here asserts the inverse:
 * absence of information produces absence of access.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReportAccessService — fails closed")
class ReportAccessServiceTest {

    @Mock private ReportAccessRoleRepository accessRoleRepository;
    @Mock private RbioStaffProfileRepository staffProfileRepository;
    @Mock private RequestIdentityResolver identityResolver;

    private ReportAccessService service;

    @BeforeEach
    void setUp() {
        service = new ReportAccessService(accessRoleRepository, staffProfileRepository, identityResolver);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        // A leaked RequestContextHolder is thread-local and would corrupt sibling tests in this JVM.
        RequestContextHolder.resetRequestAttributes();
    }

    private void givenCaller(String username, String... roles) {
        when(identityResolver.resolve(any())).thenReturn(RequestIdentity.builder()
                .userId(username)
                .displayName(username)
                .roles(Set.of(roles))
                .primaryRole(roles.length > 0 ? roles[0] : null)
                .side("RBI")
                .build());
    }

    private ReportAccessRole row(String type, String role, boolean view, boolean export) {
        ReportAccessRole r = ReportAccessRole.builder().reportType(type).roleName(role).build();
        r.setViewAllowed(view);
        r.setExportAllowed(export);
        return r;
    }

    @Nested
    @DisplayName("Unidentified callers")
    class UnidentifiedTests {

        @Test
        @DisplayName("no identity at all is DENIED, not unrestricted")
        void noIdentityIsDenied() {
            when(identityResolver.resolve(any())).thenReturn(null);

            ReportScope scope = service.resolveScope(null);

            assertThat(scope.canView()).isFalse();
            assertThat(scope.canExport()).isFalse();
            // The critical assertion: NOT unrestricted. Under the old code this was the "SENIOR" default
            // and read the entire database.
            assertThat(scope.isUnrestricted()).isFalse();
            assertThat(scope.matchesNothing()).isTrue();
        }

        @Test
        @DisplayName("a token granting no roles is DENIED")
        void noRolesIsDenied() {
            givenCaller("someone");

            ReportScope scope = service.resolveScope(null);

            assertThat(scope.canView()).isFalse();
            assertThat(scope.isUnrestricted()).isFalse();
        }

        @Test
        @DisplayName("no bound request (the scheduler thread) is DENIED")
        void noRequestIsDenied() {
            RequestContextHolder.resetRequestAttributes();

            ReportScope scope = service.resolveScope(null);

            // This is the case that made every scheduled report unscoped.
            assertThat(scope.canView()).isFalse();
            assertThat(scope.isUnrestricted()).isFalse();
        }
    }

    @Nested
    @DisplayName("The access table governs, and its emptiness denies")
    class AccessTableTests {

        @Test
        @DisplayName("an EMPTY access table denies a real authenticated user")
        void emptyTableDenies() {
            givenCaller("rbio_officer_001", "RBIO_OFFICER");
            when(accessRoleRepository.findByRoleNameIn(anyList())).thenReturn(List.of());

            ReportScope scope = service.resolveScope(null);

            // The Angular screen read an empty list as "everyone may export" because its computed fell
            // through to `return true`. The server must read it as "nobody is configured yet".
            assertThat(scope.canView()).isFalse();
            assertThat(scope.canExport()).isFalse();
        }

        @Test
        @DisplayName("a view-only row grants view but refuses export (UST669 CEPD/AA Admin)")
        void viewOnlyRefusesExport() {
            givenCaller("cepd_admin_001", "CEPD_ADMIN");
            when(accessRoleRepository.findByRoleNameIn(anyList()))
                    .thenReturn(List.of(row("ALL", "CEPD_ADMIN", true, false)));

            ReportScope scope = service.resolveScope(null);

            assertThat(scope.canView()).isTrue();
            assertThat(scope.canExport()).isFalse();
        }

        @Test
        @DisplayName("a row for another report type does not grant access to this one")
        void otherReportTypeDoesNotGrant() {
            givenCaller("x", "RBIO_OFFICER");
            when(accessRoleRepository.findByRoleNameIn(anyList()))
                    .thenReturn(List.of(row("CEPC", "RBIO_OFFICER", true, true)));

            ReportScope scope = service.resolveScope("RBIO");

            assertThat(scope.canView()).isFalse();
        }

        @Test
        @DisplayName("capabilities union across roles, so any exporting role is enough")
        void capabilitiesUnion() {
            givenCaller("multi", "CEPD_ADMIN", "AA_SECRETARIAT");
            when(accessRoleRepository.findByRoleNameIn(anyList())).thenReturn(List.of(
                    row("ALL", "CEPD_ADMIN", true, false),
                    row("ALL", "AA_SECRETARIAT", true, true)));

            ReportScope scope = service.resolveScope(null);

            assertThat(scope.canExport()).isTrue();
        }
    }

    @Nested
    @DisplayName("Territory comes from the staff profile, not the request")
    class TerritoryTests {

        @Test
        @DisplayName("an unrestricted role sees every department")
        void unrestrictedRoleSeesAll() {
            givenCaller("admin_001", "ADMIN");
            when(accessRoleRepository.findByRoleNameIn(anyList()))
                    .thenReturn(List.of(row("ALL", "ADMIN", true, true)));

            ReportScope scope = service.resolveScope(null);

            assertThat(scope.isUnrestricted()).isTrue();
        }

        @Test
        @DisplayName("an ordinary officer is confined to their posted office")
        void officerConfinedToOffice() {
            givenCaller("rbio_do_001", "RBIO_OFFICER");
            when(accessRoleRepository.findByRoleNameIn(anyList()))
                    .thenReturn(List.of(row("ALL", "RBIO_OFFICER", true, false)));
            RbioStaffProfile profile = new RbioStaffProfile();
            profile.setUserId("rbio_do_001");
            profile.setOfficeCode("RBIO");
            when(staffProfileRepository.findByUserId("rbio_do_001")).thenReturn(Optional.of(profile));

            ReportScope scope = service.resolveScope(null);

            assertThat(scope.isUnrestricted()).isFalse();
            assertThat(scope.departmentScope()).isEqualTo("RBIO");
        }

        @Test
        @DisplayName("an officer with NO staff profile matches nothing, rather than seeing everything")
        void officerWithoutProfileMatchesNothing() {
            givenCaller("ghost", "RBIO_OFFICER");
            when(accessRoleRepository.findByRoleNameIn(anyList()))
                    .thenReturn(List.of(row("ALL", "RBIO_OFFICER", true, false)));
            when(staffProfileRepository.findByUserId("ghost")).thenReturn(Optional.empty());

            ReportScope scope = service.resolveScope(null);

            // Deliberately inconvenient. Treating "no posting on file" as "sees everything" is precisely
            // the blank-department bug that made the old check bypassable.
            assertThat(scope.matchesNothing()).isTrue();
            assertThat(scope.isUnrestricted()).isFalse();
        }
    }

    @Nested
    @DisplayName("Scheduled reports resolve from stored state and fail closed")
    class ScheduledOwnerTests {

        @Test
        @DisplayName("an owner with no staff profile is DENIED, so the report is skipped")
        void ownerWithoutProfileDenied() {
            when(staffProfileRepository.findByUserId(anyString())).thenReturn(Optional.empty());

            ReportScope scope = service.resolveScopeForOwner("gone_away", null);

            // Previously this path passed the unrestricted sentinel, so EVERY scheduled report ran across
            // all departments and emailed the output off-site.
            assertThat(scope.canView()).isFalse();
            assertThat(scope.isUnrestricted()).isFalse();
        }

        @Test
        @DisplayName("a null owner is DENIED")
        void nullOwnerDenied() {
            assertThat(service.resolveScopeForOwner(null, null).canView()).isFalse();
        }

        @Test
        @DisplayName("an owner whose role lost report access is DENIED")
        void ownerWithoutAccessRowDenied() {
            RbioStaffProfile profile = new RbioStaffProfile();
            profile.setUserId("rbio_do_001");
            profile.setPrimaryRole("RBIO_OFFICER");
            profile.setOfficeCode("RBIO");
            when(staffProfileRepository.findByUserId("rbio_do_001")).thenReturn(Optional.of(profile));
            when(accessRoleRepository.findByRoleNameIn(anyList())).thenReturn(List.of());

            ReportScope scope = service.resolveScopeForOwner("rbio_do_001", null);

            assertThat(scope.canView()).isFalse();
        }

        @Test
        @DisplayName("an eligible owner is scoped to their own office, not unrestricted")
        void eligibleOwnerScopedToOffice() {
            RbioStaffProfile profile = new RbioStaffProfile();
            profile.setUserId("rbio_do_001");
            profile.setPrimaryRole("RBIO_OFFICER");
            profile.setOfficeCode("RBIO");
            when(staffProfileRepository.findByUserId("rbio_do_001")).thenReturn(Optional.of(profile));
            when(accessRoleRepository.findByRoleNameIn(anyList()))
                    .thenReturn(List.of(row("ALL", "RBIO_OFFICER", true, true)));

            ReportScope scope = service.resolveScopeForOwner("rbio_do_001", null);

            assertThat(scope.canView()).isTrue();
            assertThat(scope.isUnrestricted()).isFalse();
            assertThat(scope.departmentScope()).isEqualTo("RBIO");
        }
    }
}
