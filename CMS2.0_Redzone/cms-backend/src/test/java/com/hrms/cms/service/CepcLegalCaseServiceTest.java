package com.hrms.cms.service;

import com.hrms.cms.dto.cepc.CepcLegalCaseRequest;
import com.hrms.cms.entity.CepcLegalCase;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.repository.CepcLegalCaseRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for CepcLegalCaseService — the region lookup against OFFICE_CODE_MASTER, and the HTML-tag
 * stripping that is this service's only XSS defense on the free-form dossier fields.
 */
@ExtendWith(MockitoExtension.class)
class CepcLegalCaseServiceTest {

    @Mock private CepcLegalCaseRepository legalCaseRepository;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private OfficeCodeMasterRepository officeCodeMasterRepository;

    @InjectMocks
    private CepcLegalCaseService cepcLegalCaseService;

    private static final String COMPLAINT_NUMBER = "CMP-20260706-123456";

    @BeforeEach
    void setUp() {
        lenient().when(complaintRepository.findByComplaintNumber(COMPLAINT_NUMBER))
                .thenReturn(Optional.of(Complaint.builder().complaintNumber(COMPLAINT_NUMBER).build()));
        lenient().when(legalCaseRepository.findByComplaintNumber(COMPLAINT_NUMBER))
                .thenReturn(Optional.empty());
        lenient().when(legalCaseRepository.save(any(CepcLegalCase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CepcLegalCaseRequest.CepcLegalCaseRequestBuilder validRequest() {
        return CepcLegalCaseRequest.builder()
                .caseNumber("WP/123/2026")
                .courtName("High Court of Delhi")
                .advocateName("John Doe");
    }

    @Test
    @DisplayName("save() throws ComplaintNotFoundException when the complaint does not exist")
    void saveThrowsWhenComplaintMissing() {
        when(complaintRepository.findByComplaintNumber("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cepcLegalCaseService.save("UNKNOWN", validRequest().build(), "actor"))
                .isInstanceOf(CepcLegalCaseService.ComplaintNotFoundException.class);

        verify(legalCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() throws InvalidRegionException when the region does not match an active BO office")
    void saveThrowsWhenRegionInvalid() {
        when(officeCodeMasterRepository.findByOfficeTypeAndIsActiveTrueOrderByOfficeNameAsc("BO"))
                .thenReturn(List.of(office("Mumbai I"), office("New Delhi II")));

        CepcLegalCaseRequest request = validRequest().regionOfLegalTeam("Atlantis").build();

        assertThatThrownBy(() -> cepcLegalCaseService.save(COMPLAINT_NUMBER, request, "actor"))
                .isInstanceOf(CepcLegalCaseService.InvalidRegionException.class);

        verify(legalCaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() succeeds when the region matches an active BO office name exactly")
    void saveSucceedsWhenRegionValid() {
        when(officeCodeMasterRepository.findByOfficeTypeAndIsActiveTrueOrderByOfficeNameAsc("BO"))
                .thenReturn(List.of(office("Mumbai I"), office("New Delhi II")));

        CepcLegalCaseRequest request = validRequest().regionOfLegalTeam("New Delhi II").build();

        cepcLegalCaseService.save(COMPLAINT_NUMBER, request, "actor");

        verify(legalCaseRepository).save(any(CepcLegalCase.class));
    }

    @Test
    @DisplayName("save() skips the region lookup entirely when the field is blank")
    void saveSkipsRegionCheckWhenBlank() {
        CepcLegalCaseRequest request = validRequest().regionOfLegalTeam("  ").build();

        cepcLegalCaseService.save(COMPLAINT_NUMBER, request, "actor");

        verify(officeCodeMasterRepository, never()).findByOfficeTypeAndIsActiveTrueOrderByOfficeNameAsc(any());
        verify(legalCaseRepository).save(any(CepcLegalCase.class));
    }

    @Test
    @DisplayName("save() strips HTML tag markup from narrative fields before persisting")
    void saveStripsHtmlTags() {
        CepcLegalCaseRequest request = validRequest()
                .subjectMatter("<script>alert(1)</script>note")
                .build();

        cepcLegalCaseService.save(COMPLAINT_NUMBER, request, "actor");

        ArgumentCaptor<CepcLegalCase> captor = ArgumentCaptor.forClass(CepcLegalCase.class);
        verify(legalCaseRepository).save(captor.capture());
        assertThat(captor.getValue().getSubjectMatter()).isEqualTo("alert(1)note");
    }

    @Test
    @DisplayName("save() nulls out a whitespace-only string field rather than persisting empty text")
    void saveNullsBlankFields() {
        CepcLegalCaseRequest request = validRequest().partiesOfCase("   ").build();

        cepcLegalCaseService.save(COMPLAINT_NUMBER, request, "actor");

        ArgumentCaptor<CepcLegalCase> captor = ArgumentCaptor.forClass(CepcLegalCase.class);
        verify(legalCaseRepository).save(captor.capture());
        assertThat(captor.getValue().getPartiesOfCase()).isNull();
    }

    private static OfficeCodeMaster office(String name) {
        return OfficeCodeMaster.builder().officeType("BO").officeName(name).officeCode(name).isActive(true).build();
    }
}
