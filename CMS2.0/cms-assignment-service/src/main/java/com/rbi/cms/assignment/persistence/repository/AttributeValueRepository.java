package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttributeValueRepository extends JpaRepository<AsgnAttributeValue, Long> {

    List<AsgnAttributeValue> findByTenantIdAndAttributeCodeAndActiveOrderBySortOrder(
            String tenantId, String attributeCode, boolean active);

    List<AsgnAttributeValue> findByTenantIdAndAttributeCodeAndValueLabelContainingIgnoreCaseAndActive(
            String tenantId, String attributeCode, String valueLabelPart, boolean active);
}
