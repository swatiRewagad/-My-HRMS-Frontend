package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributeRepository extends JpaRepository<AsgnAttribute, Long> {

    List<AsgnAttribute> findByTenantIdAndActiveOrderByDisplayOrder(String tenantId, boolean active);

    Optional<AsgnAttribute> findByTenantIdAndCode(String tenantId, String code);
}
