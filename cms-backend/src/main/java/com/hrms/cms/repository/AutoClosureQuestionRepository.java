package com.hrms.cms.repository;

import com.hrms.cms.entity.AutoClosureQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AutoClosureQuestionRepository extends JpaRepository<AutoClosureQuestion, Long> {
    List<AutoClosureQuestion> findBySchemeVersionAndEntityTypeAndActiveTrueOrderByQuestionNumberAsc(String schemeVersion, String entityType);
    List<AutoClosureQuestion> findBySchemeVersionAndActiveTrueOrderByQuestionNumberAsc(String schemeVersion);
}
