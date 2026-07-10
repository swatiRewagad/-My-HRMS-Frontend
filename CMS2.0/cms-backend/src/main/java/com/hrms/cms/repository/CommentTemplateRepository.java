package com.hrms.cms.repository;

import com.hrms.cms.entity.CommentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentTemplateRepository extends JpaRepository<CommentTemplate, Long> {
    List<CommentTemplate> findByActiveTrue();
    List<CommentTemplate> findByCategoryAndActiveTrue(String category);
    List<CommentTemplate> findByModeOfReceiptInAndActiveTrue(List<String> modes);
    List<CommentTemplate> findByCategoryAndModeOfReceiptInAndActiveTrue(String category, List<String> modes);
}
