package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnRuleSetVersion;
import com.rbi.cms.assignment.domain.enums.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleSetVersionRepository extends JpaRepository<AsgnRuleSetVersion, Long> {

    List<AsgnRuleSetVersion> findByRuleSetIdOrderByVersionNoDesc(Long ruleSetId);

    Optional<AsgnRuleSetVersion> findByRuleSetIdAndVersionNo(Long ruleSetId, Integer versionNo);

    Optional<AsgnRuleSetVersion> findByRuleSetIdAndStatus(Long ruleSetId, VersionStatus status);

    Optional<AsgnRuleSetVersion> findTopByRuleSetIdOrderByVersionNoDesc(Long ruleSetId);

    List<AsgnRuleSetVersion> findByStatus(VersionStatus status);
}
