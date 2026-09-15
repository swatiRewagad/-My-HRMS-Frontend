package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.FormConfig;
import com.hrms.cms.service.FormConfigService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FormConfigController.class)
class FormConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private FormConfigService formConfigService;

    @Nested
    class GetFormSchema {

        @Test
        void shouldReturnSchema() throws Exception {
            JsonNode schema = objectMapper.readTree("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
            when(formConfigService.getFormSchema("complaint-form")).thenReturn(schema);

            mockMvc.perform(get("/api/form-config/complaint-form"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("object"))
                    .andExpect(jsonPath("$.properties.name.type").value("string"));
        }

        @Test
        void shouldReturnErrorWhenNotFound() throws Exception {
            when(formConfigService.getFormSchema("missing"))
                    .thenThrow(new RuntimeException("Form config not found: missing"));

            mockMvc.perform(get("/api/form-config/missing"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    class UpdateFormSchema {

        @Test
        void shouldUpdateAndReturnSchema() throws Exception {
            String newSchema = "{\"type\":\"object\",\"properties\":{\"email\":{\"type\":\"string\"}}}";

            FormConfig updated = FormConfig.builder()
                    .id(1L).formKey("complaint-form").schemaJson(newSchema).active(true).build();
            when(formConfigService.updateSchema(eq("complaint-form"), any(String.class)))
                    .thenReturn(updated);

            mockMvc.perform(put("/api/form-config/complaint-form")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(newSchema))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("object"))
                    .andExpect(jsonPath("$.properties.email.type").value("string"));
        }
    }
}
