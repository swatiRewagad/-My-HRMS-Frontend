package com.hrms.cms.controller;

import com.hrms.cms.entity.Bank;
import com.hrms.cms.service.ComplaintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BankController.class)
class BankControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ComplaintService complaintService;

    @Test
    void shouldReturnAllBanks() throws Exception {
        Bank bank = Bank.builder().id(1L).name("SBI").code("SBIN").type("public").status("active").build();
        when(complaintService.getAllBanks()).thenReturn(List.of(bank));

        mockMvc.perform(get("/api/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("SBI"));
    }

    @Test
    void shouldFilterByType() throws Exception {
        Bank bank = Bank.builder().id(1L).name("SBI").type("public").build();
        when(complaintService.getBanksByType("public")).thenReturn(List.of(bank));

        mockMvc.perform(get("/api/banks").param("type", "public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("public"));
    }
}
