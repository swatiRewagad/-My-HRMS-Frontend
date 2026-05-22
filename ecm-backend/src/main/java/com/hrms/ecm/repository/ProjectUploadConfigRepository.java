package com.hrms.ecm.repository;

import com.hrms.ecm.entity.ProjectUploadConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProjectUploadConfigRepository extends JpaRepository<ProjectUploadConfig, Long> {
    Optional<ProjectUploadConfig> findByProjectId(Long projectId);
}
