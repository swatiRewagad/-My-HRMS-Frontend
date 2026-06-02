package com.hrms.cms.service;

import com.hrms.cms.entity.FormConfig;
import com.hrms.cms.repository.FormConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FormConfigService {

    private final FormConfigRepository formConfigRepo;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "form-config", key = "#formKey", unless = "#result == null")
    @Transactional(readOnly = true)
    public JsonNode getFormSchema(String formKey) {
        FormConfig config = formConfigRepo.findByFormKeyAndActiveTrue(formKey)
                .orElseThrow(() -> new RuntimeException("Form config not found: " + formKey));
        try {
            return objectMapper.readTree(config.getSchemaJson());
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON schema for form: " + formKey, e);
        }
    }

    @Transactional
    public FormConfig saveFormConfig(FormConfig config) {
        return formConfigRepo.save(config);
    }

    @CacheEvict(value = "form-config", key = "#formKey")
    @Transactional
    public FormConfig updateSchema(String formKey, String schemaJson) {
        FormConfig config = formConfigRepo.findByFormKeyAndActiveTrue(formKey)
                .orElseThrow(() -> new RuntimeException("Form config not found: " + formKey));
        config.setSchemaJson(schemaJson);
        return formConfigRepo.save(config);
    }
}
