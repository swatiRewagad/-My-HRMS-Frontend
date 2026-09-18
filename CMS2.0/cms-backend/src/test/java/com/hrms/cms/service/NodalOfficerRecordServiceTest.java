package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodalOfficerRecordServiceTest {

    @Mock private NodalOfficerRecordRepository nodalOfficerRecordRepository;
    @Mock private RegulatedEntityRepository regulatedEntityRepository;

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
        when(nodalOfficerRecordRepository.save(any(NodalOfficerRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
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
                .build();
    }

    private NodalOfficerRecord captureSaved() {
        ArgumentCaptor<NodalOfficerRecord> captor = ArgumentCaptor.forClass(NodalOfficerRecord.class);
        verify(nodalOfficerRecordRepository).save(captor.capture());
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
        assertThat(saved.getAssignedTo()).isEqualTo("officer-1");
        assertThat(saved.getStatus()).isEqualTo("INFORMATION_REQUIRED");
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
}
