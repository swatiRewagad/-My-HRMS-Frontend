package com.hrms.cms.repository;

import com.hrms.cms.entity.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {

    Optional<RetentionPolicy> findByCategory(String category);

    List<RetentionPolicy> findByEnabledTrue();
}
