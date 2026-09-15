package com.hrms.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.FormConfig;
import com.hrms.cms.repository.FormConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormConfigServiceTest {

    @Mock private FormConfigRepository formConfigRepo;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FormConfigService formConfigService;

    private FormConfig sampleConfig;

    @BeforeEach
    void setUp() {
        sampleConfig = FormConfig.builder()
                .id(1L)
                .formKey("complaint-form")
                .formName("Complaint Filing Form")
                .schemaJson("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}")
                .active(true)
                .version("1.0")
                .build();
    }

    @Nested
    class GetFormSchema {

        @Test
        void shouldReturnParsedJsonNode() {
            when(formConfigRepo.findByFormKeyAndActiveTrue("complaint-form"))
                    .thenReturn(Optional.of(sampleConfig));

            JsonNode result = formConfigService.getFormSchema("complaint-form");

            assertThat(result.get("type").asText()).isEqualTo("object");
            assertThat(result.get("properties").has("name")).isTrue();
        }

        @Test
        void shouldThrowWhenFormKeyNotFound() {
            when(formConfigRepo.findByFormKeyAndActiveTrue("nonexistent"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> formConfigService.getFormSchema("nonexistent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Form config not found");
        }

        @Test
        void shouldThrowWhenInvalidJson() {
            FormConfig invalidConfig = FormConfig.builder()
                    .id(2L).formKey("bad-form").schemaJson("not valid json{{{").active(true).build();
            when(formConfigRepo.findByFormKeyAndActiveTrue("bad-form"))
                    .thenReturn(Optional.of(invalidConfig));

            assertThatThrownBy(() -> formConfigService.getFormSchema("bad-form"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid JSON schema");
        }
    }

    @Nested
    class SaveFormConfig {

        @Test
        void shouldSaveConfig() {
            when(formConfigRepo.save(sampleConfig)).thenReturn(sampleConfig);

            FormConfig result = formConfigService.saveFormConfig(sampleConfig);

            assertThat(result.getFormKey()).isEqualTo("complaint-form");
            verify(formConfigRepo).save(sampleConfig);
        }
    }

    @Nested
    class UpdateSchema {

        @Test
        void shouldUpdateExistingSchema() {
            String newSchema = "{\"type\":\"object\",\"properties\":{\"email\":{\"type\":\"string\"}}}";
            when(formConfigRepo.findByFormKeyAndActiveTrue("complaint-form"))
                    .thenReturn(Optional.of(sampleConfig));
            when(formConfigRepo.save(any(FormConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            FormConfig result = formConfigService.updateSchema("complaint-form", newSchema);

            assertThat(result.getSchemaJson()).isEqualTo(newSchema);
        }

        @Test
        void shouldThrowWhenUpdatingNonExistentForm() {
            when(formConfigRepo.findByFormKeyAndActiveTrue("missing"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> formConfigService.updateSchema("missing", "{}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Form config not found");
        }
    }
}
