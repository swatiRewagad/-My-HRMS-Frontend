package com.rbi.cms.eligibility.repository;

import com.rbi.cms.eligibility.entity.QuestionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionMasterRepository extends JpaRepository<QuestionMaster, Long> {

    List<QuestionMaster> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<QuestionMaster> findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(String category);
}
