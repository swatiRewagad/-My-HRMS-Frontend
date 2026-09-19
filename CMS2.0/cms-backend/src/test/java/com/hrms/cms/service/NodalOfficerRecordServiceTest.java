package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintRbioFormData;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.dto.NodalAssessmentRequest;
import com.hrms.cms.repository.ComplaintRbioFormDataRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.service.triage.ReResponsivenessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodalOfficerRecordServiceTest {

    @Mock private NodalOfficerRecordRepository nodalOfficerRecordRepository;
    @Mock private RegulatedEntityRepository regulatedEntityRepository;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintRbioFormDataRepository formDataRepository;
    @Mock private ReResponseTrackerRepository trackerRepository;
    @Mock private ReResponsivenessService reResponsivenessService;
    // The real one, not a mock: the Ombudsman Scheme caps are the behaviour under test here, and a
    // stubbed validator would assert nothing about them.
    @Spy private RbioCompensationService compensationService = new RbioCompensationService();

    @InjectMocks
    private NodalOfficerRecordService service;

    private Complaint complaint;

    @BeforeEach
    void setUp() {
        complaint = Complaint.builder()
                .id(11L)
                .complaintNumber("N2526001000042")
                .entityName("Punjab National Bank")
                .regulatedEntityId(5L)
                .assignedOfficer("officer-1")
                .build();

        when(nodalOfficerRecordRepository.findByComplaintNumber(anyString())).thenReturn(List.of());
        // Stand in for the identity column: the service derives recordNumber from the id the insert
        // assigns, so a stub that never assigns one would not exercise that path.
        when(nodalOfficerRecordRepository.save(any(NodalOfficerRecord.class)))
                .thenAnswer(inv -> {
                    NodalOfficerRecord r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(42L);
                    return r;
                });
    }

    private static RegulatedEntity pnb() {
        return RegulatedEntity.builder()
                .id(5L)
                .name("Punjab National Bank")
                .department("RBIO")
                .nodalOfficerName("A. Sharma")
                .nodalOfficerEmail("nodal@pnb.example")
                .nodalOfficerPhone("9876543210")
                .nodalOfficerDesignation("AGM")
                .pnoName("B. Rao")
                .pnoEmail("pno@pnb.example")
                .pnoPhone("9123456780")
                .build();
    }

    // Creation saves twice: once to get the identity id, then again to store the recordNumber derived
    // from it. The last capture is the row as it ends up.
    private NodalOfficerRecord captureSaved() {
        ArgumentCaptor<NodalOfficerRecord> captor = ArgumentCaptor.forClass(NodalOfficerRecord.class);
        verify(nodalOfficerRecordRepository, times(2)).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void snapshotsTheRegulatedEntitysNodalContactDetails() {
        when(regulatedEntityRepository.findById(5L)).thenReturn(Optional.of(pnb()));

        service.createForComplaint(complaint);

        NodalOfficerRecord saved = captureSaved();
        assertThat(saved.getComplaintNumber()).isEqualTo("N2526001000042");
        assertThat(saved.getEntityName()).isEqualTo("Punjab National Bank");
        assertThat(saved.getNodalOfficerName()).isEqualTo("A. Sharma");
        assertThat(saved.getEmail()).isEqualTo("nodal@pnb.example");
        assertThat(saved.getPhone()).isEqualTo("9876543210");
        assertThat(saved.getDesignation()).isEqualTo("AGM");
        assertThat(saved.getPnoName()).isEqualTo("B. Rao");
        assertThat(saved.getPnoEmail()).isEqualTo("pno@pnb.example");
        assertThat(saved.getPnoPhone()).isEqualTo("9123456780");
        assertThat(saved.getAssignedTo()).isEqualTo("officer-1");
        assertThat(saved.getStatus()).isEqualTo("INFORMATION_REQUIRED");
    }

    @Test
    void assignsTheRecordNumberTheCommentEndpointsAddressRecordsBy() {
        when(regulatedEntityRepository.findById(5L)).thenReturn(Optional.of(pnb()));

        // Must match the V19 backfill (LPAD(id, 7, '0')), or comments already keyed to a record
        // number would stop resolving to their record.
        assertThat(service.createForComplaint(complaint).getRecordNumber()).isEqualTo("0000042");
    }

    @Test
    void fallsBackToTheNormalizedNameWhenTheEntityIdDoesNotResolve() {
        complaint.setRegulatedEntityId(null);
        when(regulatedEntityRepository.findByNameNormalized("PUNJAB NATIONAL BANK"))
                .thenReturn(Optional.of(pnb()));

        service.createForComplaint(complaint);

        assertThat(captureSaved().getNodalOfficerName()).isEqualTo("A. Sharma");
    }

    @Test
    void fallsBackToAFuzzyNameMatchWhenTheExactNameMisses() {
        complaint.setRegulatedEntityId(null);
        when(regulatedEntityRepository.findByNameNormalized(anyString())).thenReturn(Optional.empty());
        when(regulatedEntityRepository.searchByNormalizedName(anyString())).thenReturn(List.of(pnb()));

        service.createForComplaint(complaint);

        assertThat(captureSaved().getEmail()).isEqualTo("nodal@pnb.example");
    }

    @Test
    void stillWritesARowWhenNoRegulatedEntityMatches() {
        complaint.setRegulatedEntityId(null);
        when(regulatedEntityRepository.findByNameNormalized(anyString())).thenReturn(Optional.empty());
        when(regulatedEntityRepository.searchByNormalizedName(anyString())).thenReturn(List.of());

        service.createForComplaint(complaint);

        // INFORMATION_REQUIRED is precisely the state the reminder scheduler chases, so an
        // otherwise-empty row is the useful outcome here, not noise.
        NodalOfficerRecord saved = captureSaved();
        assertThat(saved.getEntityName()).isEqualTo("Punjab National Bank");
        assertThat(saved.getNodalOfficerName()).isNull();
        assertThat(saved.getStatus()).isEqualTo("INFORMATION_REQUIRED");
    }

    @Test
    void isIdempotentSoARetryDoesNotDuplicateTheRow() {
        NodalOfficerRecord existing = NodalOfficerRecord.builder()
                .id(3L).complaintNumber("N2526001000042").entityName("Punjab National Bank").build();
        when(nodalOfficerRecordRepository.findByComplaintNumber("N2526001000042")).thenReturn(List.of(existing));

        NodalOfficerRecord result = service.createForComplaint(complaint);

        assertThat(result).isSameAs(existing);
        verify(nodalOfficerRecordRepository, never()).save(any());
    }

    @Test
    void skipsComplaintsThatHaveNoNumberToKeyTheRecordBy() {
        complaint.setComplaintNumber(null);

        assertThat(service.createForComplaint(complaint)).isNull();
        verify(nodalOfficerRecordRepository, never()).save(any());
    }

    @Nested
    class ListWorklist {

        private NodalOfficerRecord record;

        @BeforeEach
        void stubRecord() {
            record = NodalOfficerRecord.builder()
                    .id(42L)
                    .recordNumber("0000042")
                    .complaintNumber("N2526001000042")
                    .entityName("Punjab National Bank")
                    .status("INFORMATION_REQUIRED")
                    .assignedTo("officer-1")
                    .nodalOfficerName("A. Sharma")
                    .email("nodal@pnb.example")
                    .phone("9876543210")
                    .pnoName("B. Rao")
                    .pnoEmail("pno@pnb.example")
                    .pnoPhone("9123456780")
                    .build();
            when(nodalOfficerRecordRepository.findAllByOrderByLastModifiedAtDesc())
                    .thenReturn(List.of(record));
        }

        @Test
        void joinsTheComplaintAndFormDataOntoTheRecord() {
            complaint.setSubject("Loan not disbursed");
            complaint.setComplainantName("Ramesh Kumar");
            complaint.setEntityCity("Mumbai");
            complaint.setRegionalOffice("RBIO Mumbai");
            when(complaintRepository.findByComplaintNumber("N2526001000042"))
                    .thenReturn(Optional.of(complaint));
            when(formDataRepository.findByComplaintId(11L)).thenReturn(Optional.of(
                    ComplaintRbioFormData.builder()
                            .complaintId(11L)
                            .receiptDate(LocalDate.now().minusDays(5))
                            .moduleName("Loan")
                            .entityCountry("India")
                            .build()));

            Map<String, Object> row = service.listWorklist().get(0);

            assertThat(row.get("recordNumber")).isEqualTo("0000042");
            assertThat(row.get("subject")).isEqualTo("Loan not disbursed");
            assertThat(row.get("complainant")).isEqualTo("Ramesh Kumar");
            assertThat(row.get("city")).isEqualTo("Mumbai");
            assertThat(row.get("processingOffice")).isEqualTo("RBIO Mumbai");
            assertThat(row.get("moduleName")).isEqualTo("Loan");
            assertThat(row.get("country")).isEqualTo("India");
            assertThat(row.get("pnoEmail")).isEqualTo("pno@pnb.example");
            assertThat(row.get("slaDays")).isEqualTo(5L);
        }

        @Test
        void agesTheRecordFromTheComplaintDateWhenNoReceiptDateWasKeyedIn() {
            complaint.setCreatedAt(LocalDateTime.now().minusDays(12));
            when(complaintRepository.findByComplaintNumber("N2526001000042"))
                    .thenReturn(Optional.of(complaint));
            when(formDataRepository.findByComplaintId(11L)).thenReturn(Optional.empty());

            assertThat(service.listWorklist().get(0).get("slaDays")).isEqualTo(12L);
        }

        @Test
        void exposesTheSavedAssessmentSoReopeningARecordShowsIt() {
            record.setAdvisoryComplianceDate(LocalDate.of(2026, 10, 1));
            record.setDisputeAmount(new BigDecimal("15000.00"));
            record.setNotice131ComplyDate(LocalDate.of(2026, 10, 4));
            when(complaintRepository.findByComplaintNumber("N2526001000042")).thenReturn(Optional.empty());

            Map<String, Object> row = service.listWorklist().get(0);

            // ISO for the date inputs, which are native <input type="date"> and accept nothing else.
            assertThat(row.get("advisoryComplianceDate")).isEqualTo("2026-10-01");
            assertThat(row.get("disputeAmount")).isEqualTo(new BigDecimal("15000.00"));
            // Display-only, so it follows the rest of the screen.
            assertThat(row.get("notice131ComplyDate")).isEqualTo("04-10-2026");
        }

        @Test
        void stillListsTheRecordWhenItsComplaintCannotBeFound() {
            // The record is keyed on a complaint number with no foreign key behind it, so a missing
            // complaint has to degrade to a sparse row rather than drop the record from the worklist.
            when(complaintRepository.findByComplaintNumber("N2526001000042")).thenReturn(Optional.empty());

            Map<String, Object> row = service.listWorklist().get(0);

            assertThat(row.get("recordNumber")).isEqualTo("0000042");
            assertThat(row.get("subject")).isNull();
            assertThat(row.get("slaDays")).isNull();
            assertThat(row.get("noName")).isEqualTo("A. Sharma");
        }
    }

    @Nested
    class ForwardToRegulatedEntity {

        private NodalOfficerRecord record;

        @BeforeEach
        void stubRecord() {
            record = NodalOfficerRecord.builder()
                    .id(42L)
                    .recordNumber("0000042")
                    .complaintNumber("N2526001000042")
                    .status("INFORMATION_REQUIRED")
                    .build();
            when(nodalOfficerRecordRepository.findByRecordNumber("0000042")).thenReturn(Optional.of(record));
            when(complaintRepository.findByComplaintNumber("N2526001000042")).thenReturn(Optional.of(complaint));
            when(trackerRepository.findByComplaintId(11L)).thenReturn(Optional.empty());
        }

        private NodalAssessmentRequest advisory() {
            NodalAssessmentRequest request = new NodalAssessmentRequest();
            request.setStatus("ADVISORY_ISSUED");
            request.setAdvisoryComplianceDate(LocalDate.now().plusDays(10));
            request.setDisputeAmount(new BigDecimal("50000"));
            request.setCompensationLoss(new BigDecimal("25000"));
            request.setCompensationMental(new BigDecimal("5000"));
            return request;
        }

        @Test
        void persistsTheAssessmentAndStampsTheForward() {
            service.forwardToRegulatedEntity("0000042", advisory(), "officer-1");

            ArgumentCaptor<NodalOfficerRecord> captor = ArgumentCaptor.forClass(NodalOfficerRecord.class);
            verify(nodalOfficerRecordRepository).save(captor.capture());
            NodalOfficerRecord saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("ADVISORY_ISSUED");
            assertThat(saved.getDisputeAmount()).isEqualByComparingTo("50000");
            assertThat(saved.getCompensationLoss()).isEqualByComparingTo("25000");
            assertThat(saved.getCompensationMental()).isEqualByComparingTo("5000");
            assertThat(saved.getForwardedToReAt()).isNotNull();
        }

        @Test
        void returnsTheUpdatedRowSoTheScreenDoesNotHaveToRefetch() {
            Map<String, Object> row = service.forwardToRegulatedEntity("0000042", advisory(), "officer-1");

            assertThat(row.get("recordNumber")).isEqualTo("0000042");
            assertThat(row.get("status")).isEqualTo("ADVISORY_ISSUED");
        }

        @Test
        void rejectsAStatusCodeTheScreenDoesNotOffer() {
            NodalAssessmentRequest request = advisory();
            request.setStatus("AWARD_PASSED");

            // The complaint workflow's own vocabulary is not this record's — AWARD_PASS is the code the
            // radio sends, and accepting a near-miss would write a status nothing downstream reads.
            assertThatThrownBy(() -> service.forwardToRegulatedEntity("0000042", request, "officer-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown status code");
            verify(nodalOfficerRecordRepository, never()).save(any());
        }

        @Test
        void rejectsAnAdvisoryWithNoComplianceDate() {
            NodalAssessmentRequest request = advisory();
            request.setAdvisoryComplianceDate(null);

            assertThatThrownBy(() -> service.forwardToRegulatedEntity("0000042", request, "officer-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("compliance date is required");
        }

        @Test
        void rejectsAnAdvisoryComplianceDateInThePast() {
            NodalAssessmentRequest request = advisory();
            request.setAdvisoryComplianceDate(LocalDate.now().minusDays(1));

            assertThatThrownBy(() -> service.forwardToRegulatedEntity("0000042", request, "officer-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be in the past");
        }

        @Test
        void enforcesTheSchemeCapOnCompensationForMentalHarassment() {
            NodalAssessmentRequest request = advisory();
            // The harassment cap is 3 Lakh, well below the 30 Lakh consequential-loss cap, so a figure
            // between the two must still be refused.
            request.setCompensationMental(new BigDecimal("400000"));

            assertThatThrownBy(() -> service.forwardToRegulatedEntity("0000042", request, "officer-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TIME_HARASSMENT");
            verify(nodalOfficerRecordRepository, never()).save(any());
        }

        @Test
        void enforcesTheSchemeCapOnTheTwoCompensationsCombined() {
            NodalAssessmentRequest request = advisory();
            // Each is individually under its own cap; together they breach the combined one.
            request.setCompensationLoss(new BigDecimal("2900000"));
            request.setCompensationMental(new BigDecimal("200000"));

            assertThatThrownBy(() -> service.forwardToRegulatedEntity("0000042", request, "officer-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("COMBINED");
        }

        @Test
        void rejectsAnAwardDateThatHasNotHappenedYet() {
            NodalAssessmentRequest request = new NodalAssessmentRequest();
            request.setStatus("AWARD_PASS");
            request.setAwardImplementationDate(LocalDate.now().plusDays(3));

            assertThatThrownBy(() -> service.forwardToRegulatedEntity("0000042", request, "officer-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be in the future");
        }

        @Test
        void setsTheClause131ComplyDateFifteenDaysOut() {
            NodalAssessmentRequest request = new NodalAssessmentRequest();
            request.setStatus("13_1_NOTICE");

            service.forwardToRegulatedEntity("0000042", request, "officer-1");

            assertThat(record.getNotice131ComplyDate()).isEqualTo(LocalDate.now().plusDays(15));
        }

        @Test
        void leavesAnAlreadyIssuedComplyDateWhereItIs() {
            LocalDate served = LocalDate.now().minusDays(4);
            record.setNotice131ComplyDate(served);
            NodalAssessmentRequest request = new NodalAssessmentRequest();
            request.setStatus("13_1_NOTICE");

            service.forwardToRegulatedEntity("0000042", request, "officer-1");

            // Recomputing would silently extend a deadline the nodal officer has already been served.
            assertThat(record.getNotice131ComplyDate()).isEqualTo(served);
        }

        @Test
        void opensTheReResponseWindow() {
            service.forwardToRegulatedEntity("0000042", advisory(), "officer-1");

            verify(reResponsivenessService).trackForwarding(complaint, 5L);
        }

        @Test
        void doesNotOpenASecondWindowWhenTheRecordIsSentAgain() {
            when(trackerRepository.findByComplaintId(11L))
                    .thenReturn(Optional.of(ReResponseTracker.builder().complaintId(11L).build()));

            service.forwardToRegulatedEntity("0000042", advisory(), "officer-1");

            // RE_RESPONSE_TRACKER is read back through an Optional finder, so a second row would make
            // every later read of it blow up.
            verify(reResponsivenessService, never()).trackForwarding(any(), any());
        }

        @Test
        void stillSavesTheAssessmentWhenTheComplaintCannotBeFound() {
            when(complaintRepository.findByComplaintNumber("N2526001000042")).thenReturn(Optional.empty());

            service.forwardToRegulatedEntity("0000042", advisory(), "officer-1");

            verify(nodalOfficerRecordRepository).save(any(NodalOfficerRecord.class));
            verify(reResponsivenessService, never()).trackForwarding(any(), any());
        }

        @Test
        void rejectsARecordNumberThatDoesNotExist() {
            when(nodalOfficerRecordRepository.findByRecordNumber("9999999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.forwardToRegulatedEntity("9999999", advisory(), "officer-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No nodal officer record 9999999");
        }
    }
}
