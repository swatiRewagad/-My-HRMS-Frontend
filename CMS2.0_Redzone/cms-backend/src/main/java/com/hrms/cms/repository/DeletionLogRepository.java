package com.hrms.cms.repository;

import com.hrms.cms.entity.DeletionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeletionLogRepository extends JpaRepository<DeletionLog, Long> {

    Page<DeletionLog> findByOrderByExecutedAtDesc(Pageable pageable);

    List<DeletionLog> findByCategoryOrderByExecutedAtDesc(String category);
}
