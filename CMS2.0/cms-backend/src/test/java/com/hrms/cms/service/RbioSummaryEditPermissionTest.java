package com.hrms.cms.service;

import com.hrms.cms.dto.complaint.RbioComplaintSummaryResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintAdditionalDetailRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintEligibilityAnswerRepository;
import com.hrms.cms.repository.ComplaintRbioFormDataRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.rbi.cms.common.enums.RoleConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The summary is editable only by the officer the complaint is assigned to; everybody else who can
 * reach the endpoint gets read access.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RbioSummaryEditPermissionTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintEligibilityAnswerRepository eligibilityRepository;
    @Mock private ComplaintAdditionalDetailRepository additionalDetailRepository;
    @Mock private ComplaintRbioFormDataRepository formDataRepository;
    @Mock private RegulatedEntityRepository regulatedEntityRepository;
    @Mock private ComplaintCategoryRepository categoryRepository;
    @Mock private RbioSlaService rbioSlaService;
    @Mock private ComplaintService complaintService;
    @Mock private CepcAuditService auditService;
    @Spy private RbioHierarchyService rbioHierarchyService = new RbioHierarchyService();

    @InjectMocks
    private RbioComplaintSummaryService service;

    private Complaint complaint;

    private static final Map<String, Object> EDIT =
            Map.of("basicDetailsDto", Map.of("cpgramNumber", "CP-9"));

    @BeforeEach
    void setUp() {
        complaint = Complaint.builder()
                .id(92L)
                .complaintNumber("CMS-PNB-1234")
                .department("RBIO")
                .status("SENT_TO_REVIEWER")
                .assignedOfficer("reviewer1")
                .assignedOfficerName("Reviewer One")
                .assignedRole(RoleConstants.RBIO_REVIEWER)
                .build();
        when(complaintRepository.findById(92L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(complaint)).thenReturn(complaint);
    }

    @Nested
    class UpdateSummary {

        @Test
        void theAssignedOfficerMayEdit() {
            service.updateSummary(92L, EDIT, "reviewer1", Set.of(RoleConstants.RBIO_REVIEWER));

            verify(complaintRepository).save(complaint);
        }

        @Test
        void matchesTheAssignedOfficerIgnoringCaseAndPadding() {
            service.updateSummary(92L, EDIT, "  Reviewer1 ", Set.of(RoleConstants.RBIO_REVIEWER));

            verify(complaintRepository).save(complaint);
        }

        @Test
        void anotherOfficerIsRejectedWithForbidden() {
            assertThatThrownBy(() -> service.updateSummary(92L, EDIT, "do1", Set.of(RoleConstants.RBIO_DO)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN))
                    .hasMessageContaining("reviewer1");

            verify(complaintRepository, never()).save(complaint);
        }

        /** A senior role is still not the holder — seniority forwards a complaint, it does not edit one. */
        @Test
        void aHigherRankingOfficerIsStillOnlyAViewer() {
            assertThatThrownBy(() -> service.updateSummary(92L, EDIT, "ombudsman1",
                    Set.of(RoleConstants.RBIO_OMBUDSMAN)))
                    .isInstanceOf(ResponseStatusException.class);

            verify(complaintRepository, never()).save(complaint);
        }

        @Test
        void rbioAdminMayEditOnAnAbsentOfficersBehalf() {
            service.updateSummary(92L, EDIT, "admin1", Set.of(RoleConstants.RBIO_ADMIN));

            verify(complaintRepository).save(complaint);
        }

        @Test
        void anUnknownCallerCannotEditAnAssignedComplaint() {
            assertThatThrownBy(() -> service.updateSummary(92L, EDIT, null, Set.of()))
                    .isInstanceOf(ResponseStatusException.class);
        }

        /** Nothing to protect before the complaint is picked up, so registration-time edits still work. */
        @Test
        void anUnassignedComplaintIsEditableByAnyone() {
            complaint.setAssignedOfficer(null);

            service.updateSummary(92L, EDIT, "someone", Set.of());

            verify(complaintRepository).save(complaint);
        }

        @Test
        void theNotFoundCheckStillRunsBeforeThePermissionCheck() {
            when(complaintRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateSummary(404L, EDIT, "nobody", Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Complaint not found");
        }
    }

    @Nested
    class Assignment {

        /** The screens read these to decide between an editable form and a read-only view. */
        @Test
        void summaryCarriesTheAssignmentSoTheUiCanGoReadOnly() {
            RbioComplaintSummaryResponse summary = service.getSummary(92L);

            assertThat(summary.getAssignedOfficer()).isEqualTo("reviewer1");
            assertThat(summary.getAssignedOfficerName()).isEqualTo("Reviewer One");
            assertThat(summary.getAssignedRole()).isEqualTo(RoleConstants.RBIO_REVIEWER);
        }
    }

    @Nested
    class CanEdit {

        @Test
        void recognisesTheAdminRoleWhateverItsSpelling() {
            assertThat(rbioHierarchyService.canEdit("reviewer1", "admin1", List.of("ROLE_RBIO_ADMIN"))).isTrue();
            assertThat(rbioHierarchyService.canEdit("reviewer1", "admin1", List.of("rbio_admin"))).isTrue();
            assertThat(rbioHierarchyService.canEdit("reviewer1", "admin1", List.of("CEPC_ADMIN"))).isFalse();
        }

        @Test
        void missingRolesAreNotTreatedAsAdmin() {
            assertThat(rbioHierarchyService.canEdit("reviewer1", "reviewer1", null)).isTrue();
            assertThat(rbioHierarchyService.canEdit("reviewer1", "do1", null)).isFalse();
        }
    }
}
