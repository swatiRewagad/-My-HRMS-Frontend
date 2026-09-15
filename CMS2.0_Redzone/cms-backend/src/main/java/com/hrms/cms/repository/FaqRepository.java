package com.hrms.cms.repository;

import com.hrms.cms.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaqRepository extends JpaRepository<Faq, Long> {

    List<Faq> findByIsActiveTrueOrderBySortOrderAsc();

    List<Faq> findByIsActiveTrueAndCategoryOrderBySortOrderAsc(String category);
}
