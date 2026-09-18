package com.hrms.cms.service;

import com.hrms.cms.dto.DashboardResponse;
import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.dto.UpdateComplaintRequest;
import com.hrms.cms.entity.*;
import com.hrms.cms.event.ComplaintEventPublisher;
import com.hrms.cms.repository.*;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintCategoryRepository categoryRepository;
    @Mock private BankRepository bankRepository;
    @Mock private ComplaintTimelineRepository timelineRepository;
    @Mock private ComplaintAttachmentRepository attachmentRepository;
    @Mock private ComplaintEventPublisher eventPublisher;
    @Mock private ComplaintRoutingService routingService;
    @Mock private ComplaintNumberGeneratorService complaintNumberGenerator;
    @Mock private OfficeRoutingService officeRoutingService;
    @Mock private NodalOfficerRecordService nodalOfficerRecordService;
    @Mock private OfficeAssignmentStrategyService officeAssignmentStrategyService;

    @InjectMocks
    private ComplaintService complaintService;

    private Complaint sampleComplaint;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMS-20260515-ABC123")
                .complainantName("John Doe")
                .complainantEmail("john@example.com")
                .complainantPhone("9876543210")
                .subject("ATM Issue")
                .description("Money not dispensed")
                .status("pending")
                .priority("medium")
                .bankId(1L)
                .categoryId(1L)
                .build();
    }

    @Nested
    class GetDashboard {

        @Test
        void shouldReturnDashboardWithAllCounts() {
            when(complaintRepository.count()).thenReturn(100L);
            when(complaintRepository.countByStatus("pending")).thenReturn(30L);
            when(complaintRepository.countByStatus("in_progress")).thenReturn(20L);
            when(complaintRepository.countByStatus("resolved")).thenReturn(25L);
            when(complaintRepository.countByStatus("closed")).thenReturn(15L);
            when(complaintRepository.countByStatus("escalated")).thenReturn(10L);
            when(complaintRepository.countByPriority("high")).thenReturn(20L);
            when(complaintRepository.countByPriority("medium")).thenReturn(50L);
            when(complaintRepository.countByPriority("low")).thenReturn(30L);

            DashboardResponse dashboard = complaintService.getDashboard();

            assertThat(dashboard.getTotalComplaints()).isEqualTo(100L);
            assertThat(dashboard.getPendingComplaints()).isEqualTo(30L);
            assertThat(dashboard.getInProgressComplaints()).isEqualTo(20L);
            assertThat(dashboard.getResolvedComplaints()).isEqualTo(25L);
            assertThat(dashboard.getClosedComplaints()).isEqualTo(15L);
            assertThat(dashboard.getEscalatedComplaints()).isEqualTo(10L);
            assertThat(dashboard.getHighPriority()).isEqualTo(20L);
            assertThat(dashboard.getMediumPriority()).isEqualTo(50L);
            assertThat(dashboard.getLowPriority()).isEqualTo(30L);
        }

        @Test
        void shouldReturnZerosWhenNoComplaints() {
            when(complaintRepository.count()).thenReturn(0L);
            when(complaintRepository.countByStatus(anyString())).thenReturn(0L);
            when(complaintRepository.countByPriority(anyString())).thenReturn(0L);

            DashboardResponse dashboard = complaintService.getDashboard();

            assertThat(dashboard.getTotalComplaints()).isZero();
            assertThat(dashboard.getPendingComplaints()).isZero();
        }
    }

    @Nested
    class GetAllComplaints {

        @Test
        void shouldReturnAllComplaints() {
            when(complaintRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(List.of(sampleComplaint));

            List<Complaint> result = complaintService.getAllComplaints();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getComplaintNumber()).isEqualTo("CMS-20260515-ABC123");
        }

        @Test
        void shouldReturnEmptyListWhenNone() {
            when(complaintRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(Collections.emptyList());

            List<Complaint> result = complaintService.getAllComplaints();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetAllComplaintsPaged {

        @Test
        void shouldReturnPagedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), pageable, 1);
            when(complaintRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

            Page<Complaint> result = complaintService.getAllComplaintsPaged(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0)).isEqualTo(sampleComplaint);
        }
    }

    @Nested
    class GetByStatusPaged {

        @Test
        void shouldFilterByStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), pageable, 1);
            when(complaintRepository.findByStatusOrderByCreatedAtDesc("pending", pageable)).thenReturn(page);

            Page<Complaint> result = complaintService.getByStatusPaged("pending", pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    class SearchComplaintsPaged {

        @Test
        void shouldSearchByQuery() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), pageable, 1);
            when(complaintRepository.searchPaged("ATM", pageable)).thenReturn(page);

            Page<Complaint> result = complaintService.searchComplaintsPaged("ATM", pageable);

            assertThat(result.getContent().get(0).getSubject()).contains("ATM");
        }
    }

    @Nested
    class GetComplaint {

        @Test
        void shouldReturnComplaintById() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));

            Complaint result = complaintService.getComplaint(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getSubject()).isEqualTo("ATM Issue");
        }

        @Test
        void shouldThrowWhenNotFound() {
            when(complaintRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> complaintService.getComplaint(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Complaint not found");
        }
    }

    @Nested
    class GetByComplaintNumber {

        @Test
        void shouldReturnByNumber() {
            when(complaintRepository.findByComplaintNumber("CMS-20260515-ABC123"))
                    .thenReturn(Optional.of(sampleComplaint));

            Complaint result = complaintService.getByComplaintNumber("CMS-20260515-ABC123");

            assertThat(result.getComplainantName()).isEqualTo("John Doe");
        }

        @Test
        void shouldThrowWhenNumberNotFound() {
            when(complaintRepository.findByComplaintNumber("INVALID"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> complaintService.getByComplaintNumber("INVALID"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Complaint not found");
        }
    }

    @Nested
    class GetByStatus {

        @Test
        void shouldReturnByStatus() {
            when(complaintRepository.findByStatusOrderByCreatedAtDesc("pending"))
                    .thenReturn(List.of(sampleComplaint));

            List<Complaint> result = complaintService.getByStatus("pending");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class SearchComplaints {

        @Test
        void shouldSearchByText() {
            when(complaintRepository.search("John")).thenReturn(List.of(sampleComplaint));

            List<Complaint> result = complaintService.searchComplaints("John");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class FileComplaint {

        /**
         * fileComplaint() always delegates number generation, department routing and office capacity
         * routing, so every test in this class needs all three collaborators stubbed (a bare mock
         * would return null and NPE on routing.getDepartment()).
         */
        @BeforeEach
        void stubRoutingAndNumbering() {
            when(routingService.resolveDepartment(anyString())).thenReturn("RBIO");
            when(complaintNumberGenerator.generateForOffice(anyString(), any(), any()))
                    .thenReturn(new ComplaintNumberGeneratorService.NumberedComplaint(
                            "CMS-20260515-XYZ789", "Mumbai-I", "013"));
            when(routingService.routeComplaint(any(Complaint.class), anyString()))
                    .thenReturn(ComplaintRoutingService.RoutingDecision.builder()
                            .department("RBIO")
                            .assignedRole("RBIO_OFFICER")
                            .assignedOfficer("rbio-officer-1")
                            .stage("INITIAL_REVIEW")
                            .reason("Default routing for unit test")
                            .build());
            when(officeRoutingService.routeToOffice(anyString(), anyBoolean()))
                    .thenReturn(Map.of("officeId", "013",
                            "status", OfficeRoutingService.STATUS_ASSIGNED,
                            "currentCount", 1));
            // Per-office strategy defaults to rotation in these tests, matching an unconfigured office.
            when(officeAssignmentStrategyService.resolveOfficer(anyString(), any(), any()))
                    .thenReturn(new OfficeAssignmentStrategyService.Resolution(
                            "rbio-officer-1", OfficeAssignmentStrategy.ROUND_ROBIN,
                            OfficeAssignmentStrategyService.Resolution.OUTCOME_ROTATED, "rotated"));
        }

        @Test
        void shouldLetTheOfficeStrategyDecideTheOfficer() {
            // UST468-472. The generic router picks an officer knowing nothing about per-office policy, so
            // the office's own logic must be able to override it — otherwise configuring an office to route
            // by entity would change nothing and the feature would be inert.
            when(officeAssignmentStrategyService.resolveOfficer(anyString(), any(), any()))
                    .thenReturn(new OfficeAssignmentStrategyService.Resolution(
                            "rbio.officer.mum", OfficeAssignmentStrategy.ENTITY_MAPPING,
                            OfficeAssignmentStrategyService.Resolution.OUTCOME_MAPPED,
                            "Mapped HDFC Bank to rbio.officer.mum"));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Complaint result = complaintService.fileComplaint(minimalRequest());

            assertThat(result.getAssignedOfficer()).isEqualTo("rbio.officer.mum");
        }

        @Test
        void shouldKeepTheRoutedOfficerWhenTheOfficeStrategyCannotAssign() {
            // An unassignable office strategy must not blank an officer the router already found: that
            // would take a complaint away from someone able to act on it.
            when(officeAssignmentStrategyService.resolveOfficer(anyString(), any(), any()))
                    .thenReturn(new OfficeAssignmentStrategyService.Resolution(
                            null, OfficeAssignmentStrategy.ENTITY_MAPPING,
                            OfficeAssignmentStrategyService.Resolution.OUTCOME_UNASSIGNED, "no admin"));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Complaint result = complaintService.fileComplaint(minimalRequest());

            assertThat(result.getAssignedOfficer()).isEqualTo("rbio-officer-1");
        }

        @Test
        void shouldPersistTheOfficeThatAcceptedTheComplaint() {
            FileComplaintRequest request = minimalRequest();
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Complaint result = complaintService.fileComplaint(request);

            // The office must land on the row, not merely inside the complaint number string.
            assertThat(result.getRbioOfficeCode()).isEqualTo("013");
        }

        @Test
        void shouldRecordTheOverflowOfficeWhenPrimaryOfficeIsFull() {
            // An overflow diversion changes which Ombudsman office holds the case, so the complaint
            // must carry the office that ACCEPTED it, not the one it was numbered against.
            when(officeRoutingService.routeToOffice(anyString(), anyBoolean()))
                    .thenReturn(Map.of("officeId", "014",
                            "status", OfficeRoutingService.STATUS_OVERFLOW,
                            "primaryOfficeId", "013",
                            "currentCount", 1));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(7L);
                return c;
            });

            Complaint result = complaintService.fileComplaint(minimalRequest());

            assertThat(result.getRbioOfficeCode()).isEqualTo("014");
            assertThat(result.getComplaintNumber()).isEqualTo("CMS-20260515-XYZ789");
        }

        @Test
        void shouldFallBackToTheNumberedOfficeWhenCapacityRowIsMissing() {
            // Fail-closed: an unconfigured office must not silently place the complaint elsewhere.
            when(officeRoutingService.routeToOffice(anyString(), anyBoolean()))
                    .thenReturn(Map.of("officeId", "013",
                            "status", OfficeRoutingService.STATUS_NOT_FOUND,
                            "reason", "Office not configured or inactive"));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Complaint result = complaintService.fileComplaint(minimalRequest());

            assertThat(result.getRbioOfficeCode()).isEqualTo("013");
        }

        @Test
        void shouldEnsureANodalOfficerRecordExistsForTheFiledComplaint() {
            // UST569. Asserted here rather than only in NodalOfficerRecordServiceTest because the defect
            // being guarded is the HOOK going missing: the service can be perfectly correct and still never
            // be called, leaving the complaint invisible to the staleness escalations that chase the entity.
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(11L);
                return c;
            });

            Complaint result = complaintService.fileComplaint(minimalRequest());

            // The office passed must be the one the complaint was numbered against, so the NO/PNO lookup
            // is scoped to the office that will actually process it (UST773).
            verify(nodalOfficerRecordService).ensureRecordExists(
                    eq(11L), eq(result.getComplaintNumber()), anyString(), eq("Mumbai-I"));
        }

        private FileComplaintRequest minimalRequest() {
            FileComplaintRequest request = new FileComplaintRequest();
            request.setComplainantName("Jane Doe");
            request.setComplainantEmail("jane@example.com");
            request.setComplainantPhone("9876543211");
            request.setSubject("Loan Issue");
            request.setDescription("Incorrect interest rate");
            request.setPriority("high");
            request.setBankId(2L);
            request.setCategoryId(3L);
            request.setFilingType("online");
            return request;
        }

        @Test
        void shouldCreateComplaintWithGeneratedNumber() {
            FileComplaintRequest request = new FileComplaintRequest();
            request.setComplainantName("Jane Doe");
            request.setComplainantEmail("jane@example.com");
            request.setComplainantPhone("9876543211");
            request.setSubject("Loan Issue");
            request.setDescription("Incorrect interest rate");
            request.setPriority("high");
            request.setBankId(2L);
            request.setCategoryId(3L);
            request.setFilingType("online");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(2L);
                return c;
            });

            Complaint result = complaintService.fileComplaint(request);

            assertThat(result.getComplaintNumber()).startsWith("CMS-");
            assertThat(result.getComplainantName()).isEqualTo("Jane Doe");
            assertThat(result.getPriority()).isEqualTo("high");
            assertThat(result.getBankId()).isEqualTo(2L);
            verify(complaintRepository).save(any(Complaint.class));
        }

        @Test
        void shouldParseBankComplaintDate() {
            FileComplaintRequest request = new FileComplaintRequest();
            request.setComplainantName("Test");
            request.setSubject("Test");
            request.setBankComplaintDate("2026-01-15");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Complaint result = complaintService.fileComplaint(request);

            assertThat(result.getBankComplaintDate()).isNotNull();
            assertThat(result.getBankComplaintDate().toLocalDate().toString()).isEqualTo("2026-01-15");
        }

        @Test
        void shouldHandleNullBankComplaintDate() {
            FileComplaintRequest request = new FileComplaintRequest();
            request.setComplainantName("Test");
            request.setSubject("Test");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Complaint result = complaintService.fileComplaint(request);

            assertThat(result.getBankComplaintDate()).isNull();
        }

        @Test
        void shouldAddTimelineEntry() {
            FileComplaintRequest request = new FileComplaintRequest();
            request.setComplainantName("Test");
            request.setSubject("Test");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(5L);
                return c;
            });

            complaintService.fileComplaint(request);

            verify(timelineRepository).save(argThat(timeline ->
                    timeline.getComplaintId().equals(5L) &&
                    "filed".equals(timeline.getAction()) &&
                    "pending".equals(timeline.getToStatus())
            ));
        }
    }

    @Nested
    class UpdateComplaint {

        @Test
        void shouldUpdateStatus() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setStatus("in_progress");
            request.setRemarks("Assigned for review");

            Complaint result = complaintService.updateComplaint(1L, request);

            assertThat(result.getStatus()).isEqualTo("in_progress");
        }

        @Test
        void shouldSetResolvedAtWhenResolved() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setStatus("resolved");

            Complaint result = complaintService.updateComplaint(1L, request);

            assertThat(result.getResolvedAt()).isNotNull();
        }

        @Test
        void shouldSetClosedAtWhenClosed() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setStatus("closed");

            Complaint result = complaintService.updateComplaint(1L, request);

            assertThat(result.getClosedAt()).isNotNull();
        }

        @Test
        void shouldSetEscalatedAtWhenEscalated() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setStatus("escalated");

            Complaint result = complaintService.updateComplaint(1L, request);

            assertThat(result.getEscalatedAt()).isNotNull();
        }

        @Test
        void shouldUpdatePriority() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setPriority("high");

            Complaint result = complaintService.updateComplaint(1L, request);

            assertThat(result.getPriority()).isEqualTo("high");
        }

        @Test
        void shouldUpdateAssignedOfficer() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setAssignedOfficer("Officer Smith");
            request.setRemarks("Assigned");

            Complaint result = complaintService.updateComplaint(1L, request);

            assertThat(result.getAssignedOfficer()).isEqualTo("Officer Smith");
        }

        @Test
        void shouldRecordTimelineOnStatusChange() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setStatus("resolved");
            request.setRemarks("Issue fixed");

            complaintService.updateComplaint(1L, request);

            verify(timelineRepository).save(argThat(timeline ->
                    "status_change".equals(timeline.getAction()) &&
                    "pending".equals(timeline.getFromStatus()) &&
                    "resolved".equals(timeline.getToStatus())
            ));
        }

        @Test
        void shouldRecordTimelineAsUpdateWhenNoStatusChange() {
            when(complaintRepository.findById(1L)).thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setPriority("high");
            request.setRemarks("Priority escalated");

            complaintService.updateComplaint(1L, request);

            verify(timelineRepository).save(argThat(timeline ->
                    "update".equals(timeline.getAction())
            ));
        }
    }

    @Nested
    class DeleteComplaint {

        @Test
        void shouldDeleteById() {
            doNothing().when(complaintRepository).deleteById(1L);

            complaintService.deleteComplaint(1L);

            verify(complaintRepository).deleteById(1L);
        }
    }

    @Nested
    class GetTimeline {

        @Test
        void shouldReturnTimeline() {
            ComplaintTimeline entry = ComplaintTimeline.builder()
                    .id(1L).complaintId(1L).action("filed").performedBy("System").build();
            when(timelineRepository.findByComplaintIdOrderByPerformedAtDesc(1L))
                    .thenReturn(List.of(entry));

            List<ComplaintTimeline> result = complaintService.getTimeline(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAction()).isEqualTo("filed");
        }
    }

    @Nested
    class GetAllCategories {

        @Test
        void shouldReturnActiveCategories() {
            ComplaintCategory cat = ComplaintCategory.builder()
                    .id(1L).name("ATM").status("active").build();
            when(categoryRepository.findByStatus("active")).thenReturn(List.of(cat));

            List<ComplaintCategory> result = complaintService.getAllCategories();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("ATM");
        }
    }

    @Nested
    class GetRootCategories {

        @Test
        void shouldReturnRootCategories() {
            ComplaintCategory cat = ComplaintCategory.builder().id(1L).name("Banking").build();
            when(categoryRepository.findByParentIdIsNullOrderBySortOrder()).thenReturn(List.of(cat));

            List<ComplaintCategory> result = complaintService.getRootCategories();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class GetSubCategories {

        @Test
        void shouldReturnSubCategories() {
            ComplaintCategory sub = ComplaintCategory.builder().id(2L).name("ATM").parentId(1L).build();
            when(categoryRepository.findByParentIdOrderBySortOrder(1L)).thenReturn(List.of(sub));

            List<ComplaintCategory> result = complaintService.getSubCategories(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getParentId()).isEqualTo(1L);
        }
    }

    @Nested
    class GetAllBanks {

        @Test
        void shouldReturnActiveBanks() {
            Bank bank = Bank.builder().id(1L).name("SBI").status("active").build();
            when(bankRepository.findByStatus("active")).thenReturn(List.of(bank));

            List<Bank> result = complaintService.getAllBanks();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("SBI");
        }
    }

    @Nested
    class GetBanksByType {

        @Test
        void shouldReturnBanksByType() {
            Bank bank = Bank.builder().id(1L).name("SBI").type("public").build();
            when(bankRepository.findByType("public")).thenReturn(List.of(bank));

            List<Bank> result = complaintService.getBanksByType("public");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class GetAttachments {

        @Test
        void shouldReturnAttachmentsForComplaint() {
            ComplaintAttachment att = ComplaintAttachment.builder()
                    .id(1L).complaintId(1L).originalName("doc.pdf").build();
            when(attachmentRepository.findByComplaintId(1L)).thenReturn(List.of(att));

            List<ComplaintAttachment> result = complaintService.getAttachments(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOriginalName()).isEqualTo("doc.pdf");
        }
    }

    @Nested
    class SaveAttachment {

        @Test
        void shouldSaveAttachment() {
            ComplaintAttachment att = ComplaintAttachment.builder()
                    .complaintId(1L).fileName("stored.pdf").originalName("doc.pdf").build();
            when(attachmentRepository.save(att)).thenReturn(att);

            ComplaintAttachment result = complaintService.saveAttachment(att);

            assertThat(result.getOriginalName()).isEqualTo("doc.pdf");
            verify(attachmentRepository).save(att);
        }
    }

    @Nested
    class AddTimeline {

        @Test
        void shouldSaveTimelineEntry() {
            complaintService.addTimeline(1L, "filed", "System", "Filed", null, "pending");

            verify(timelineRepository).save(argThat(t ->
                    t.getComplaintId().equals(1L) &&
                    "filed".equals(t.getAction()) &&
                    "System".equals(t.getPerformedBy())
            ));
        }
    }
}
