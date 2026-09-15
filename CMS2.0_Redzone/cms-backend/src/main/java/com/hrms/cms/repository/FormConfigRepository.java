package com.hrms.cms.repository;

import com.hrms.cms.entity.FormConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FormConfigRepository extends JpaRepository<FormConfig, Long> {
    Optional<FormConfig> findByFormKeyAndActiveTrue(String formKey);
}
