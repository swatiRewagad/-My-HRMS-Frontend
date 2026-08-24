package com.rbi.cms.assignment.service;

import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.domain.entity.*;
import com.rbi.cms.assignment.domain.enums.VersionStatus;
import com.rbi.cms.assignment.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GovernanceService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleSetVersionRepository versionRepository;
    private final PublicationRepository publicationRepository;
    private final AuditEventRepository auditEventRepository;
    private final RuleSetCacheService cacheService;
    private final TenantContext tenantContext;

    @Transactional
    public AsgnRuleSetVersion submit(Long ruleSetId, Long versionId, String actorId, String remarks) {
        AsgnRuleSetVersion version = getVersion(versionId);
        assertStatus(version, VersionStatus.DRAFT);
        assertMakerNotChecker(version, actorId);

        version.setStatus(VersionStatus.PENDING_APPROVAL);
        version.setMakerId(actorId);
        version.setMakerAt(Instant.now());
        version.setMakerRemarks(remarks);
        version = versionRepository.save(version);

        audit("SUBMITTED", ruleSetId, versionId, actorId, remarks);
        log.info("Version {} submitted for approval by {}", versionId, actorId);
        return version;
    }

    @Transactional
    public AsgnRuleSetVersion approve(Long ruleSetId, Long versionId, String actorId, String remarks) {
        AsgnRuleSetVersion version = getVersion(versionId);
        assertStatus(version, VersionStatus.PENDING_APPROVAL);
        assertNotSameAsMaker(version, actorId);

        version.setStatus(VersionStatus.APPROVED);
        version.setCheckerId(actorId);
        version.setCheckerAt(Instant.now());
        version.setCheckerRemarks(remarks);
        version = versionRepository.save(version);

        audit("APPROVED", ruleSetId, versionId, actorId, remarks);
        log.info("Version {} approved by {}", versionId, actorId);
        return version;
    }

    @Transactional
    public AsgnRuleSetVersion reject(Long ruleSetId, Long versionId, String actorId, String remarks) {
        AsgnRuleSetVersion version = getVersion(versionId);
        assertStatus(version, VersionStatus.PENDING_APPROVAL);
        assertNotSameAsMaker(version, actorId);

        version.setStatus(VersionStatus.DRAFT);
        version.setCheckerId(actorId);
        version.setCheckerAt(Instant.now());
        version.setCheckerRemarks(remarks);
        version = versionRepository.save(version);

        audit("REJECTED", ruleSetId, versionId, actorId, remarks);
        log.info("Version {} rejected by {}, returned to DRAFT", versionId, actorId);
        return version;
    }

    @Transactional
    public AsgnRuleSetVersion publish(Long ruleSetId, Long versionId, String actorId, Instant effectiveFrom) {
        String tenant = tenantContext.getCurrentTenant();
        AsgnRuleSetVersion version = getVersion(versionId);
        assertStatus(version, VersionStatus.APPROVED);

        AsgnRuleSet ruleSet = ruleSetRepository.findById(ruleSetId)
                .orElseThrow(() -> new NoSuchElementException("RuleSet not found: " + ruleSetId));

        // Supersede any currently published version for this ruleset
        final int versionNo = version.getVersionNo();
        versionRepository.findByRuleSetIdAndStatus(ruleSetId, VersionStatus.PUBLISHED)
                .ifPresent(prev -> {
                    prev.setStatus(VersionStatus.SUPERSEDED);
                    prev.setEffectiveTo(Instant.now());
                    versionRepository.save(prev);
                    audit("SUPERSEDED", ruleSetId, prev.getId(), actorId, "Replaced by version " + versionNo);
                });

        // Mark this version as published
        version.setStatus(VersionStatus.PUBLISHED);
        version.setPublishedBy(actorId);
        version.setPublishedAt(Instant.now());
        version.setEffectiveFrom(effectiveFrom != null ? effectiveFrom : Instant.now());
        version = versionRepository.save(version);

        // Upsert publication pointer
        AsgnRuleSetPublication pub = publicationRepository
                .findByTenantIdAndDecisionPoint(tenant, ruleSet.getDecisionPoint())
                .orElse(AsgnRuleSetPublication.builder()
                        .tenantId(tenant)
                        .decisionPoint(ruleSet.getDecisionPoint())
                        .build());
        pub.setActiveVersionId(versionId);
        pub.setPublishedAt(Instant.now());
        publicationRepository.save(pub);

        // Evict cache so polling picks up the new version immediately
        cacheService.evict(tenant, ruleSet.getDecisionPoint());

        audit("PUBLISHED", ruleSetId, versionId, actorId, null);
        log.info("Version {} published for decision point '{}' by {}", versionId, ruleSet.getDecisionPoint(), actorId);
        return version;
    }

    @Transactional
    public AsgnRuleSetVersion archive(Long ruleSetId, Long versionId, String actorId) {
        AsgnRuleSetVersion version = getVersion(versionId);
        if (version.getStatus() == VersionStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot archive a PUBLISHED version — supersede it first by publishing a newer version");
        }

        version.setStatus(VersionStatus.ARCHIVED);
        version = versionRepository.save(version);

        audit("ARCHIVED", ruleSetId, versionId, actorId, null);
        return version;
    }

    public List<AsgnRuleSetVersion> getPendingApprovals() {
        return versionRepository.findByStatus(VersionStatus.PENDING_APPROVAL);
    }

    public List<AsgnAuditEvent> getAuditTrail(Long versionId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("VERSION", versionId);
    }

    private AsgnRuleSetVersion getVersion(Long versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found: " + versionId));
    }

    private void assertStatus(AsgnRuleSetVersion version, VersionStatus expected) {
        if (version.getStatus() != expected) {
            throw new IllegalStateException(
                    "Version status must be " + expected + " but is " + version.getStatus());
        }
    }

    private void assertMakerNotChecker(AsgnRuleSetVersion version, String actorId) {
        // Maker can submit their own draft — no restriction here
    }

    private void assertNotSameAsMaker(AsgnRuleSetVersion version, String actorId) {
        if (actorId != null && actorId.equals(version.getMakerId())) {
            throw new IllegalStateException("Checker cannot be the same person as the maker (four-eyes principle)");
        }
    }

    private void audit(String action, Long ruleSetId, Long versionId, String actorId, String remarks) {
        AsgnAuditEvent event = AsgnAuditEvent.builder()
                .tenantId(tenantContext.getCurrentTenant())
                .entityType("VERSION")
                .entityId(versionId)
                .action(action)
                .actor(actorId)
                .afterJson(remarks)
                .build();
        auditEventRepository.save(event);
    }
}
