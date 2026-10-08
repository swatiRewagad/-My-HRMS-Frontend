package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.ComplaintDraft;
import com.hrms.cms.repository.ComplaintDraftRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.hrms.cms.support.ControllerSliceTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ControllerSliceTest(ComplaintDraftController.class)
@AutoConfigureMockMvc(addFilters = false)
class ComplaintDraftControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ComplaintDraftRepository draftRepository;

    private ComplaintDraft existingDraft() {
        ComplaintDraft d = new ComplaintDraft();
        d.setId(1L);
        d.setDraftId("DRF-EXISTING");
        d.setPhone("9876500011");
        d.setCurrentStep(4);
        d.setHighestStepReached(5);
        d.setPhase("form");
        d.setFormDataJson("{\"complainantDetails\":{\"firstName\":\"Ravi\"}}");
        return d;
    }

    /**
     * Regression: the save used to upsert on phone alone, so a blank step-1 payload
     * overwrote whichever draft that phone owned. The draft row survived — which is why
     * My Complaints still listed it — but its form data was gone, so resuming it
     * restarted the wizard from scratch.
     */
    @Test
    void shouldUpdateTheDraftIdentifiedByDraftIdRatherThanTheNewestForThePhone() throws Exception {
        ComplaintDraft target = existingDraft();
        when(draftRepository.findByDraftId("DRF-EXISTING")).thenReturn(Optional.of(target));
        when(draftRepository.save(any(ComplaintDraft.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> body = Map.of(
                "draftId", "DRF-EXISTING",
                "phone", "9876500011",
                "currentStep", 6,
                "phase", "form",
                "formData", Map.of("complainantDetails", Map.of("firstName", "Ravi")));

        mockMvc.perform(post("/api/v1/complaints/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.draftId").value("DRF-EXISTING"));

        // Resolved by draftId, never by falling back to the phone lookup.
        verify(draftRepository).findByDraftId("DRF-EXISTING");
        verify(draftRepository, never()).findByPhoneOrderByUpdatedAtDesc(anyString());
    }

    @Test
    void shouldRejectADraftIdBelongingToAnotherPhone() throws Exception {
        ComplaintDraft other = existingDraft();
        other.setPhone("9000000000");
        when(draftRepository.findByDraftId("DRF-EXISTING")).thenReturn(Optional.of(other));

        Map<String, Object> body = Map.of(
                "draftId", "DRF-EXISTING",
                "phone", "9876500011",
                "currentStep", 1,
                "phase", "eligibility",
                "formData", Map.of());

        mockMvc.perform(post("/api/v1/complaints/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());

        verify(draftRepository, never()).save(any(ComplaintDraft.class));
    }

    @Test
    void shouldFallBackToThePhoneLookupWhenNoDraftIdIsSupplied() throws Exception {
        ComplaintDraft target = existingDraft();
        when(draftRepository.findByPhoneOrderByUpdatedAtDesc("9876500011")).thenReturn(List.of(target));
        when(draftRepository.save(any(ComplaintDraft.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> body = Map.of(
                "phone", "9876500011",
                "currentStep", 2,
                "phase", "form",
                "formData", Map.of("complainantDetails", Map.of("firstName", "Ravi")));

        mockMvc.perform(post("/api/v1/complaints/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.draftId").value("DRF-EXISTING"));
    }

    @Test
    void shouldCreateANewDraftWhenThePhoneHasNone() throws Exception {
        when(draftRepository.findByPhoneOrderByUpdatedAtDesc("9876500011")).thenReturn(List.of());
        when(draftRepository.save(any(ComplaintDraft.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> body = Map.of(
                "phone", "9876500011",
                "currentStep", 1,
                "phase", "eligibility",
                "formData", Map.of());

        mockMvc.perform(post("/api/v1/complaints/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.draftId").exists());

        ArgumentCaptor<ComplaintDraft> saved = ArgumentCaptor.forClass(ComplaintDraft.class);
        verify(draftRepository).save(saved.capture());
        assertThat(saved.getValue().getDraftId()).startsWith("DRF-");
    }
}
