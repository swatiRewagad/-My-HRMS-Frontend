package com.hrms.cms.controller;

import com.hrms.cms.entity.ComplaintCategory;
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

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ComplaintService complaintService;

    @Test
    void shouldReturnAllCategories() throws Exception {
        ComplaintCategory cat = ComplaintCategory.builder()
                .id(1L).name("ATM").status("active").build();
        when(complaintService.getAllCategories()).thenReturn(List.of(cat));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("ATM"));
    }

    @Test
    void shouldReturnRootCategories() throws Exception {
        ComplaintCategory cat = ComplaintCategory.builder()
                .id(1L).name("Banking").build();
        when(complaintService.getRootCategories()).thenReturn(List.of(cat));

        mockMvc.perform(get("/api/categories/root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Banking"));
    }

    @Test
    void shouldReturnSubCategories() throws Exception {
        ComplaintCategory sub = ComplaintCategory.builder()
                .id(2L).name("ATM").parentId(1L).build();
        when(complaintService.getSubCategories(1L)).thenReturn(List.of(sub));

        mockMvc.perform(get("/api/categories/1/sub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parentId").value(1));
    }
}
