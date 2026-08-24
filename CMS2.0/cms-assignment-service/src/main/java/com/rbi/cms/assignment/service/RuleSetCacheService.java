package com.rbi.cms.assignment.service;

import com.rbi.cms.assignment.config.TenantContext;
import com.rbi.cms.assignment.domain.entity.AsgnRuleSet;
import com.rbi.cms.assignment.domain.entity.AsgnRuleSetPublication;
import com.rbi.cms.assignment.domain.entity.AsgnRuleSetVersion;
import com.rbi.cms.assignment.engine.compiler.CompiledRuleSet;
import com.rbi.cms.assignment.engine.compiler.RuleSetCompiler;
import com.rbi.cms.assignment.persistence.repository.PublicationRepository;
import com.rbi.cms.assignment.persistence.repository.RuleSetRepository;
import com.rbi.cms.assignment.persistence.repository.RuleSetVersionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RuleSetCacheService {

    private final PublicationRepository publicationRepository;
    private final RuleSetRepository ruleSetRepository;
    private final RuleSetVersionRepository versionRepository;
    private final RuleSetCompiler compiler;
    private final TenantContext tenantContext;

    private final Cache<String, CompiledRuleSet> cache;
    private final ConcurrentHashMap<String, Long> knownVersions = new ConcurrentHashMap<>();

    public RuleSetCacheService(
            PublicationRepository publicationRepository,
            RuleSetRepository ruleSetRepository,
            RuleSetVersionRepository versionRepository,
            RuleSetCompiler compiler,
            TenantContext tenantContext,
            @Value("${cms.assignment.cache.max-size:100}") int maxSize) {
        this.publicationRepository = publicationRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.versionRepository = versionRepository;
        this.compiler = compiler;
        this.tenantContext = tenantContext;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    public CompiledRuleSet getCompiledRuleSet(String decisionPoint) {
        String tenantId = tenantContext.getCurrentTenant();
        String cacheKey = tenantId + "::" + decisionPoint;

        return cache.get(cacheKey, key -> loadAndCompile(tenantId, decisionPoint));
    }

    public void refresh(String decisionPoint) {
        String tenantId = tenantContext.getCurrentTenant();
        evict(tenantId, decisionPoint);
    }

    public void evict(String tenantId, String decisionPoint) {
        String cacheKey = tenantId + "::" + decisionPoint;
        cache.invalidate(cacheKey);
        knownVersions.remove(cacheKey);
        log.info("Cache invalidated for {}", cacheKey);
    }

    public void refreshAll() {
        cache.invalidateAll();
        knownVersions.clear();
        log.info("Full cache invalidation");
    }

    @Scheduled(fixedDelayString = "${cms.assignment.cache.refresh-interval-seconds:30}000")
    public void pollForUpdates() {
        String tenantId = tenantContext.getCurrentTenant();
        publicationRepository.findByTenantIdAndDecisionPoint(tenantId, null); // list all - will refine
        // For each publication, check if version changed
        for (var entry : knownVersions.entrySet()) {
            String key = entry.getKey();
            String dp = key.substring(key.indexOf("::") + 2);
            publicationRepository.findByTenantIdAndDecisionPoint(tenantId, dp).ifPresent(pub -> {
                if (!pub.getActiveVersionId().equals(entry.getValue())) {
                    log.info("Version change detected for {}, refreshing", dp);
                    cache.invalidate(key);
                    knownVersions.put(key, pub.getActiveVersionId());
                }
            });
        }
    }

    private CompiledRuleSet loadAndCompile(String tenantId, String decisionPoint) {
        AsgnRuleSetPublication pub = publicationRepository
                .findByTenantIdAndDecisionPoint(tenantId, decisionPoint)
                .orElse(null);

        if (pub == null) {
            log.warn("No published ruleset for tenant={} decisionPoint={}", tenantId, decisionPoint);
            return null;
        }

        AsgnRuleSetVersion version = versionRepository.findById(pub.getActiveVersionId()).orElse(null);
        if (version == null) {
            log.error("Publication references non-existent version {}", pub.getActiveVersionId());
            return null;
        }

        AsgnRuleSet ruleSet = ruleSetRepository.findById(version.getRuleSetId()).orElse(null);
        if (ruleSet == null) {
            log.error("Version references non-existent ruleset {}", version.getRuleSetId());
            return null;
        }

        CompiledRuleSet compiled = compiler.compile(ruleSet, version);
        knownVersions.put(tenantId + "::" + decisionPoint, pub.getActiveVersionId());
        return compiled;
    }
}
