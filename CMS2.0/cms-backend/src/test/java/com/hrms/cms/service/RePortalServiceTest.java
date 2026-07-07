package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RePortalService.
 */
@ExtendWith(MockitoExtension.class)
class RePortalServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintTimelineRepository timelineRepository;
    @Mock private ReResponseTrackerRepository trackerRepository;
    @Mock private RegulatedEntityRepository regulatedEntityRepository;

    @InjectMocks
    private RePortalService rePortalService;

    private Complaint sampleComplaint;
    private Complaint oldComplaint;
    private RegulatedEntity sampleEntity;
    private ReResponseTracker sampleTracker;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260706-100001")
                .complainantName("Test Citizen")
                .complainantEmail("citizen@example.com")
                .subject("Service Deficiency")
                .description("Bank failed to process refund")
                .status("assigned")
                .priority("MEDIUM")
                .department("RBIO")
                .entityCode("HDFC001")
                .assignedRole("RBIO_OFFICER")
                .assignedOfficer("rbio-officer-1")
                .workflowStage("CREATED")
                .createdAt(LocalDateTime.now().minusDays(5))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        oldComplaint = Complaint.builder()
                .id(2L)
                .complaintNumber("CMP-20260620-200001")
                .complainantName("Old Citizen")
                .complainantEmail("old@example.com")
                .subject("Old Complaint")
                .status("assigned")
                .priority("HIGH")
                .department("RBIO")
                .entityCode("HDFC001")
                .assignedRole("RBIO_OFFICER")
                .createdAt(LocalDateTime.now().minusDays(20))
                .updatedAt(LocalDateTime.now().minusDays(16))
                .build();

        sampleEntity = RegulatedEntity.builder()
                .id(10L)
                .name("HDFC Bank Ltd")
                .nameNormalized("HDFC BANK LTD")
                .department("RBIO")
                .entityType("BANK")
                .city("Mumbai")
                .state("Maharashtra")
                .status("active")
                .nodalOfficerName("John Doe")
                .nodalOfficerEmail("nodal@hdfc.com")
                .nodalOfficerPhone("9876543210")
                .nodalOfficerDesignation("VP Compliance")
                .portalEnabled(true)
                .build();

        sampleTracker = ReResponseTracker.builder()
                .id(1L)
                .complaintId(1L)
                .regulatedEntityId(10L)
                .forwardedAt(LocalDateTime.now().minusDays(5))
                .windowDays(15)
                .breached(false)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // getComplaintsForEntity()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getComplaintsForEntity()")
    class GetComplaintsForEntity {

        @Test
        @DisplayName("should return paginated complaints matching entity code")
        void shouldReturnPaginatedComplaintsMatchingEntityCode() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), pageable, 1);

            when(complaintRepository.findByEntityCodeOrderByCreatedAtDesc(eq("HDFC001"), any(Pageable.class)))
                    .thenReturn(page);

            Page<Complaint> result = rePortalService.getComplaintsForEntity("HDFC001", null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEntityCode()).isEqualTo("HDFC001");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return empty page for unknown entity code")
        void shouldReturnEmptyForUnknownEntity() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Complaint> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(complaintRepository.findByEntityCodeOrderByCreatedAtDesc(eq("UNKNOWN"), any(Pageable.class)))
                    .thenReturn(emptyPage);

            Page<Complaint> result = rePortalService.getComplaintsForEntity("UNKNOWN", null, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("should filter by status when provided")
        void shouldFilterByStatusWhenProvided() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), pageable, 1);

            when(complaintRepository.findByEntityCodeAndStatusOrderByCreatedAtDesc(eq("HDFC001"), eq("assigned"), any(Pageable.class)))
                    .thenReturn(page);

            Page<Complaint> result = rePortalService.getComplaintsForEntity("HDFC001", "assigned", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("assigned");
            verify(complaintRepository).findByEntityCodeAndStatusOrderByCreatedAtDesc(eq("HDFC001"), eq("assigned"), any(Pageable.class));
        }

        @Test
        @DisplayName("should use findByEntityCode when status is blank")
        void shouldUseFindByEntityCodeWhenStatusBlank() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), pageable, 1);

            when(complaintRepository.findByEntityCodeOrderByCreatedAtDesc(eq("HDFC001"), any(Pageable.class)))
                    .thenReturn(page);

            Page<Complaint> result = rePortalService.getComplaintsForEntity("HDFC001", "  ", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(complaintRepository).findByEntityCodeOrderByCreatedAtDesc(eq("HDFC001"), any(Pageable.class));
            verify(complaintRepository, never()).findByEntityCodeAndStatusOrderByCreatedAtDesc(any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getComplaintDetail()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getComplaintDetail()")
    class GetComplaintDetail {

        @Test
        @DisplayName("should return complaint when entity code matches")
        void shouldReturnComplaintWhenEntityCodeMatches() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));

            Complaint result = rePortalService.getComplaintDetail("CMP-20260706-100001", "HDFC001");

            assertThat(result.getComplaintNumber()).isEqualTo("CMP-20260706-100001");
            assertThat(result.getEntityCode()).isEqualTo("HDFC001");
        }

        @Test
        @DisplayName("should throw NoSuchElementException when complaint not found")
        void shouldThrowWhenComplaintNotFound() {
            when(complaintRepository.findByComplaintNumber("NON-EXISTENT"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> rePortalService.getComplaintDetail("NON-EXISTENT", "HDFC001"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Complaint not found");
        }

        @Test
        @DisplayName("should throw SecurityException when entity code does not match")
        void shouldThrowSecurityExceptionWhenEntityCodeMismatch() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));

            assertThatThrownBy(() -> rePortalService.getComplaintDetail("CMP-20260706-100001", "OTHER_ENTITY"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Access denied");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // respondToComplaint()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("respondToComplaint()")
    class RespondToComplaint {

        @Test
        @DisplayName("should submit response and update tracker")
        void shouldSubmitResponseAndUpdateTracker() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(trackerRepository.findByComplaintId(1L))
                    .thenReturn(Optional.of(sampleTracker));
            when(trackerRepository.save(any(ReResponseTracker.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(complaintRepository.save(any(Complaint.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ReResponseTracker result = rePortalService.respondToComplaint("CMP-20260706-100001", "Issue resolved. Amount credited.", "nodal-officer-1");

            assertThat(result.getRespondedAt()).isNotNull();
            assertThat(result.getResponseText()).isEqualTo("Issue resolved. Amount credited.");
            verify(complaintRepository).save(any(Complaint.class));
            verify(timelineRepository).save(any(ComplaintTimeline.class));
        }

        @Test
        @DisplayName("should throw when complaint already responded to")
        void shouldThrowWhenAlreadyResponded() {
            ReResponseTracker respondedTracker = ReResponseTracker.builder()
                    .id(1L)
                    .complaintId(1L)
                    .regulatedEntityId(10L)
                    .forwardedAt(LocalDateTime.now().minusDays(5))
                    .respondedAt(LocalDateTime.now().minusDays(2))
                    .windowDays(15)
                    .breached(false)
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(trackerRepository.findByComplaintId(1L))
                    .thenReturn(Optional.of(respondedTracker));

            assertThatThrownBy(() ->
                    rePortalService.respondToComplaint("CMP-20260706-100001", "Duplicate response", "officer"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already responded");
        }

        @Test
        @DisplayName("should throw when complaint not found")
        void shouldThrowWhenComplaintNotFound() {
            when(complaintRepository.findByComplaintNumber("NON-EXISTENT"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    rePortalService.respondToComplaint("NON-EXISTENT", "text", "officer"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Complaint not found");
        }

        @Test
        @DisplayName("should update complaint status to re_responded")
        void shouldUpdateComplaintStatusToReResponded() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(trackerRepository.findByComplaintId(1L))
                    .thenReturn(Optional.of(sampleTracker));
            when(trackerRepository.save(any(ReResponseTracker.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(complaintRepository.save(any(Complaint.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            rePortalService.respondToComplaint("CMP-20260706-100001", "Response text", "officer");

            verify(complaintRepository).save(argThat(c -> "re_responded".equals(c.getStatus())));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getDashboardStats()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getDashboardStats()")
    class GetDashboardStats {

        @Test
        @DisplayName("should compute dashboard stats from trackers")
        void shouldComputeDashboardStatsFromTrackers() {
            ReResponseTracker respondedTracker = ReResponseTracker.builder()
                    .id(2L).complaintId(2L).regulatedEntityId(10L)
                    .forwardedAt(LocalDateTime.now().minusDays(10))
                    .respondedAt(LocalDateTime.now().minusDays(7))
                    .windowDays(15).breached(false).build();

            ReResponseTracker breachedTracker = ReResponseTracker.builder()
                    .id(3L).complaintId(3L).regulatedEntityId(10L)
                    .forwardedAt(LocalDateTime.now().minusDays(20))
                    .windowDays(15).breached(true).build();

            when(regulatedEntityRepository.findByNameNormalized(any()))
                    .thenReturn(Optional.of(sampleEntity));
            when(trackerRepository.findByRegulatedEntityIdOrderByForwardedAtDesc(10L))
                    .thenReturn(List.of(sampleTracker, respondedTracker, breachedTracker));

            Map<String, Object> stats = rePortalService.getDashboardStats("HDFC001");

            assertThat(stats).containsKey("totalForwarded");
            assertThat(stats.get("totalForwarded")).isEqualTo(3L);
            assertThat(stats).containsKey("responded");
            assertThat(stats.get("responded")).isEqualTo(1L);
            assertThat(stats).containsKey("breached");
            assertThat(stats.get("breached")).isEqualTo(1L);
        }

        @Test
        @DisplayName("should return zero stats when entity not found")
        void shouldReturnZeroStatsWhenEntityNotFound() {
            when(regulatedEntityRepository.findByNameNormalized(any()))
                    .thenReturn(Optional.empty());

            Map<String, Object> stats = rePortalService.getDashboardStats("UNKNOWN_ENTITY");

            assertThat(stats.get("totalForwarded")).isEqualTo(0L);
            assertThat(stats.get("pending")).isEqualTo(0L);
            assertThat(stats.get("responded")).isEqualTo(0L);
            assertThat(stats.get("breached")).isEqualTo(0L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // raiseQuery()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("raiseQuery()")
    class RaiseQuery {

        @Test
        @DisplayName("should save query and add timeline entry for clarification")
        void shouldSaveQueryAndAddTimelineForClarification() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(trackerRepository.findByComplaintId(1L))
                    .thenReturn(Optional.of(sampleTracker));
            when(trackerRepository.save(any(ReResponseTracker.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            rePortalService.raiseQuery("CMP-20260706-100001", "Need transaction date", "CLARIFICATION");

            verify(trackerRepository).save(argThat(t -> "Need transaction date".equals(t.getQueryText())));
            verify(timelineRepository).save(argThat(t -> "RE_CLARIFICATION".equals(t.getAction())));
        }

        @Test
        @DisplayName("should add timeline entry with RE_EXTENSION_REQUEST for extension type")
        void shouldAddTimelineForExtensionRequest() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(trackerRepository.findByComplaintId(1L))
                    .thenReturn(Optional.of(sampleTracker));
            when(trackerRepository.save(any(ReResponseTracker.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            rePortalService.raiseQuery("CMP-20260706-100001", "Need 7 more days", "EXTENSION_REQUEST");

            verify(timelineRepository).save(argThat(t -> "RE_EXTENSION_REQUEST".equals(t.getAction())));
        }

        @Test
        @DisplayName("should throw when complaint not found for query")
        void shouldThrowWhenComplaintNotFoundForQuery() {
            when(complaintRepository.findByComplaintNumber("NON-EXISTENT"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    rePortalService.raiseQuery("NON-EXISTENT", "query text", "CLARIFICATION"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Complaint not found");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getTimeline()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getTimeline()")
    class GetTimeline {

        @Test
        @DisplayName("should return timeline entries for valid complaint and entity")
        void shouldReturnTimelineEntries() {
            ComplaintTimeline entry = ComplaintTimeline.builder()
                    .id(1L)
                    .complaintId(1L)
                    .action("RE_RESPONDED")
                    .performedBy("nodal-officer-1")
                    .remarks("Response submitted")
                    .fromStatus("assigned")
                    .toStatus("re_responded")
                    .performedAt(LocalDateTime.now())
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(timelineRepository.findByComplaintIdOrderByPerformedAtDesc(1L))
                    .thenReturn(List.of(entry));

            List<ComplaintTimeline> result = rePortalService.getTimeline("CMP-20260706-100001", "HDFC001");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAction()).isEqualTo("RE_RESPONDED");
        }

        @Test
        @DisplayName("should throw SecurityException for mismatched entity code")
        void shouldThrowForMismatchedEntityCode() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));

            assertThatThrownBy(() -> rePortalService.getTimeline("CMP-20260706-100001", "OTHER_ENTITY"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Access denied");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getEntityProfile()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getEntityProfile()")
    class GetEntityProfile {

        @Test
        @DisplayName("should return entity profile for valid entity code")
        void shouldReturnProfileForValidEntity() {
            when(regulatedEntityRepository.findByNameNormalized(any()))
                    .thenReturn(Optional.of(sampleEntity));

            RegulatedEntity result = rePortalService.getEntityProfile("HDFC001");

            assertThat(result.getName()).isEqualTo("HDFC Bank Ltd");
            assertThat(result.getNodalOfficerName()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("should throw NoSuchElementException for unknown entity code")
        void shouldThrowForUnknownEntityCode() {
            when(regulatedEntityRepository.findByNameNormalized(any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> rePortalService.getEntityProfile("UNKNOWN"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Regulated entity not found");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // updateNodalOfficer()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateNodalOfficer()")
    class UpdateNodalOfficer {

        @Test
        @DisplayName("should update all nodal officer fields")
        void shouldUpdateAllNodalOfficerFields() {
            when(regulatedEntityRepository.findByNameNormalized(any()))
                    .thenReturn(Optional.of(sampleEntity));
            when(regulatedEntityRepository.save(any(RegulatedEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            RegulatedEntity result = rePortalService.updateNodalOfficer("HDFC001", "Jane Smith", "jane@hdfc.com", "9999888877", "Senior VP");

            assertThat(result.getNodalOfficerName()).isEqualTo("Jane Smith");
            assertThat(result.getNodalOfficerEmail()).isEqualTo("jane@hdfc.com");
            assertThat(result.getNodalOfficerPhone()).isEqualTo("9999888877");
            assertThat(result.getNodalOfficerDesignation()).isEqualTo("Senior VP");
            verify(regulatedEntityRepository).save(any(RegulatedEntity.class));
        }

        @Test
        @DisplayName("should throw when entity not found for update")
        void shouldThrowWhenEntityNotFoundForUpdate() {
            when(regulatedEntityRepository.findByNameNormalized(any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> rePortalService.updateNodalOfficer("UNKNOWN", "Name", "e@x.com", "1234", "Mgr"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Regulated entity not found");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // isWithinResponseWindow()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isWithinResponseWindow()")
    class IsWithinResponseWindow {

        @Test
        @DisplayName("should return true when within window (forwardedAt + windowDays)")
        void shouldReturnTrueWhenWithinWindow() {
            ReResponseTracker withinWindow = ReResponseTracker.builder()
                    .id(1L).complaintId(1L)
                    .forwardedAt(LocalDateTime.now().minusDays(5))
                    .windowDays(15)
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(trackerRepository.findByComplaintId(1L))
                    .thenReturn(Optional.of(withinWindow));

            boolean result = rePortalService.isWithinResponseWindow("CMP-20260706-100001");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when past window expiry")
        void shouldReturnFalseWhenPastExpiry() {
            ReResponseTracker expiredTracker = ReResponseTracker.builder()
                    .id(2L).complaintId(2L)
                    .forwardedAt(LocalDateTime.now().minusDays(20))
                    .windowDays(15)
                    .build();

            Complaint c = Complaint.builder().id(2L).complaintNumber("CMP-OLD").entityCode("HDFC001").build();

            when(complaintRepository.findByComplaintNumber("CMP-OLD"))
                    .thenReturn(Optional.of(c));
            when(trackerRepository.findByComplaintId(2L))
                    .thenReturn(Optional.of(expiredTracker));

            boolean result = rePortalService.isWithinResponseWindow("CMP-OLD");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should use windowExpiresAt when set")
        void shouldUseWindowExpiresAtWhenSet() {
            ReResponseTracker trackerWithExpiry = ReResponseTracker.builder()
                    .id(3L).complaintId(1L)
                    .forwardedAt(LocalDateTime.now().minusDays(20))
                    .windowDays(15)
                    .windowExpiresAt(LocalDateTime.now().plusDays(2))
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-20260706-100001"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(trackerRepository.findByComplaintId(1L))
                    .thenReturn(Optional.of(trackerWithExpiry));

            boolean result = rePortalService.isWithinResponseWindow("CMP-20260706-100001");

            assertThat(result).isTrue();
        }
    }
}
