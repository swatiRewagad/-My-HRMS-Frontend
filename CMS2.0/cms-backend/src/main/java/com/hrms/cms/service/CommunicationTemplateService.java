package com.hrms.cms.service;

import com.hrms.cms.entity.CommunicationTemplate;
import com.hrms.cms.repository.CommunicationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CommunicationTemplateService {

    private final CommunicationTemplateRepository templateRepository;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    @Transactional(readOnly = true)
    public List<CommunicationTemplate> getAll() {
        return templateRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CommunicationTemplate> getActive() {
        return templateRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public CommunicationTemplate getById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CommunicationTemplate> getByTrigger(String triggerCondition) {
        return templateRepository.findByTriggerConditionAndActiveTrue(triggerCondition);
    }

    @Transactional(readOnly = true)
    public List<CommunicationTemplate> getByTriggerAndMode(String triggerCondition, String mode) {
        return templateRepository.findByTriggerConditionAndModeAndActiveTrue(triggerCondition, mode);
    }

    @Transactional(readOnly = true)
    public List<CommunicationTemplate> getForScheme(String triggerCondition, String schemeVersion) {
        List<String> versions = Arrays.asList(schemeVersion, "BOTH");
        return templateRepository.findByTriggerConditionAndSchemeVersionInAndActiveTrue(triggerCondition, versions);
    }

    @Transactional
    public CommunicationTemplate create(CommunicationTemplate template) {
        return templateRepository.save(template);
    }

    @Transactional
    public CommunicationTemplate update(Long id, CommunicationTemplate updates) {
        CommunicationTemplate existing = getById(id);
        existing.setTemplateName(updates.getTemplateName());
        existing.setMode(updates.getMode());
        existing.setTriggerCondition(updates.getTriggerCondition());
        existing.setSchemeVersion(updates.getSchemeVersion());
        existing.setSubjectTemplate(updates.getSubjectTemplate());
        existing.setBodyTemplate(updates.getBodyTemplate());
        existing.setDescription(updates.getDescription());
        existing.setCategory(updates.getCategory());
        return templateRepository.save(existing);
    }

    @Transactional
    public void deactivate(Long id) {
        CommunicationTemplate template = getById(id);
        template.setActive(false);
        templateRepository.save(template);
    }

    @Transactional
    public void activate(Long id) {
        CommunicationTemplate template = getById(id);
        template.setActive(true);
        templateRepository.save(template);
    }

    public String renderSubject(CommunicationTemplate template, Map<String, String> variables) {
        return replaceVariables(template.getSubjectTemplate(), variables);
    }

    public String renderBody(CommunicationTemplate template, Map<String, String> variables) {
        return replaceVariables(template.getBodyTemplate(), variables);
    }

    private String replaceVariables(String templateText, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(templateText);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.getOrDefault(key, "{{" + key + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
