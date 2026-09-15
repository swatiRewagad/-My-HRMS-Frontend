package com.hrms.cms.repository;

import com.hrms.cms.entity.CommunicationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunicationTemplateRepository extends JpaRepository<CommunicationTemplate, Long> {
    List<CommunicationTemplate> findByActiveTrue();
    List<CommunicationTemplate> findByModeAndActiveTrue(String mode);
    List<CommunicationTemplate> findByTriggerConditionAndActiveTrue(String triggerCondition);
    List<CommunicationTemplate> findByTriggerConditionAndModeAndActiveTrue(String triggerCondition, String mode);
    List<CommunicationTemplate> findBySchemeVersionInAndActiveTrue(List<String> schemeVersions);
    List<CommunicationTemplate> findByTriggerConditionAndSchemeVersionInAndActiveTrue(String triggerCondition, List<String> schemeVersions);
    List<CommunicationTemplate> findByCategoryAndActiveTrue(String category);
}
