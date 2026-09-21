package com.hrms.cms.repository;

import com.hrms.cms.entity.WfOfficerPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WfOfficerPoolRepository extends JpaRepository<WfOfficerPool, Long> {

    /**
     * {@code USER_ID} carries no unique constraint and one officer may sit in several role groups, so
     * this takes the first match rather than risking an {@code IncorrectResultSizeDataAccessException}
     * on a caller that only wants the person's name or office.
     */
    Optional<WfOfficerPool> findFirstByUserIdIgnoreCase(String userId);
}
