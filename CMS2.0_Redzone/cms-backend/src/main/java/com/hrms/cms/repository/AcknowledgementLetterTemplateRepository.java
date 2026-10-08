package com.hrms.cms.repository;

import com.hrms.cms.entity.AcknowledgementLetterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcknowledgementLetterTemplateRepository extends JpaRepository<AcknowledgementLetterTemplate, Long> {

    List<AcknowledgementLetterTemplate> findByActiveTrue();

    Optional<AcknowledgementLetterTemplate> findFirstByDepartmentAndLanguageAndActiveTrueOrderByVersionDesc(
            String department, String language);

    boolean existsByDepartmentAndLanguage(String department, String language);
}
