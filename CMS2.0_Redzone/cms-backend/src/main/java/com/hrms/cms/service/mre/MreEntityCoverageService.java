package com.hrms.cms.service.mre;

import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Decides whether a regulated entity is covered by the Scheme, and therefore which department may
 * lawfully handle a complaint against it (UST473).
 *
 * <h2>What was wrong</h2>
 * This class previously answered:
 *
 * <pre>
 *   existsByNameNormalizedContainingIgnoreCase(entityCode) || existsByNameContainingIgnoreCase(entityCode)
 * </pre>
 *
 * A substring existence check that ignored {@code department} entirely — and ignored its own
 * {@code entityType} parameter. So a CEPC-listed entity, which is NOT under the Ombudsman Scheme,
 * returned "covered", and the maintainability engine told the citizen their complaint was within the
 * Scheme when it was not. Because the answer was {@code @Cacheable} keyed only on the entity name, the
 * wrong answer was then served from cache.
 *
 * <p>The substring test is itself unsafe here. Searching "HDFC" against the live data matches
 * {@code HDFC Credila Financial Services Limited} (CEPC) as well as {@code HDFC Bank} and
 * {@code HDFC Bank Limited} (RBIO) — three rows, in no defined order, with the CEPC one returned first.
 * Any code taking the first row was choosing a citizen's department by row order.
 *
 * <h2>Fail closed, and never on a guess</h2>
 * Coverage decides a citizen's statutory forum. Three rules follow from that:
 *
 * <ol>
 *   <li>An <b>exact</b> normalised-name match is authoritative and is tried first.</li>
 *   <li>A <b>partial</b> match is accepted only when every candidate agrees on the department. If "HDFC"
 *       matches both a CEPC and an RBIO entity the answer is AMBIGUOUS, not the first row. Guessing would
 *       route a complaint against a Scheme-covered bank to CEPC, where the Ombudsman cannot hear it, and
 *       the citizen would lose their statutory recourse without being told.</li>
 *   <li>An <b>unknown</b> entity is NOT covered. The old routing defaulted unmatched entities to RBIO,
 *       which is default-allow on a maintainability determination: an entity nobody regulates would be
 *       admitted to the Ombudsman Scheme.</li>
 * </ol>
 *
 * <p>Neither AMBIGUOUS nor UNKNOWN is silent — both come back as distinct verdicts so the caller can put
 * the complaint in front of a human and record why, instead of proceeding on an assumption.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MreEntityCoverageService {

    /** Entities under the Ombudsman Scheme are handled by RBIO; everything else belongs to CEPC. */
    public static final String DEPARTMENT_RBIO = "RBIO";
    public static final String DEPARTMENT_CEPC = "CEPC";

    private final RegulatedEntityRepository regulatedEntityRepo;

    /**
     * The Scheme this determination is made under.
     *
     * <p>Injected rather than hardcoded so a Scheme change is a configuration change. Several controllers
     * previously carried a hardcoded "RBIOS_2026" literal naming a Scheme not in force; those were
     * removed, and this must not reintroduce the same class of error.
     */
    @Value("${cms.eligibility.scheme-version:RBIOS_2021}")
    private String schemeVersion;

    @Value("${cms.eligibility.scheme-name:Reserve Bank - Integrated Ombudsman Scheme, 2021}")
    private String schemeName;

    /** How confidently the entity was identified, and what follows for routing. */
    public enum CoverageStatus {
        /** Named entity is under the Scheme. Handled by RBIO. */
        COVERED,
        /** Named entity exists but is outside the Scheme. Handled by CEPC (UST473). */
        NOT_COVERED,
        /** The name matched several entities that disagree on department. Needs a human. */
        AMBIGUOUS,
        /** No entity matched, or no name was supplied. Needs a human; never treated as covered. */
        UNKNOWN
    }

    /**
     * The determination, the department that follows from it, and the reason.
     *
     * @param matchedName the entity actually matched, so a reviewer can see what the name resolved to
     */
    public record Coverage(CoverageStatus status, String department, String matchedName,
                           String reason, String schemeVersion) {

        public boolean isCovered() {
            return status == CoverageStatus.COVERED;
        }

        /** True when no automated determination should be relied on. */
        public boolean needsReview() {
            return status == CoverageStatus.AMBIGUOUS || status == CoverageStatus.UNKNOWN;
        }
    }

    /**
     * Resolves coverage for an entity name.
     *
     * <p>Deliberately NOT cached. The previous {@code @Cacheable} was keyed on the entity name alone and
     * outlived edits to REGULATED_ENTITIES, so correcting an entity's department left the old verdict
     * being served. A stale citizen-facing legal determination is worse than a repeated indexed lookup
     * against a 145-row table.
     */
    public Coverage resolveCoverage(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return new Coverage(CoverageStatus.UNKNOWN, null, null,
                    "No regulated entity named on the complaint", schemeVersion);
        }

        String normalized = RegulatedEntity.normalize(entityName);

        Optional<RegulatedEntity> exact = regulatedEntityRepo.findByNameNormalized(normalized);
        if (exact.isPresent()) {
            return fromEntity(exact.get(), "Exact match on " + exact.get().getName());
        }

        List<RegulatedEntity> partial = regulatedEntityRepo.searchByNormalizedName(normalized);
        if (partial.isEmpty()) {
            return new Coverage(CoverageStatus.UNKNOWN, null, null,
                    "'" + entityName + "' is not in the regulated entity list", schemeVersion);
        }

        // Every candidate must agree, or the name has not actually identified one entity.
        List<String> departments = partial.stream()
                .map(RegulatedEntity::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .toList();

        if (departments.size() == 1) {
            RegulatedEntity first = partial.get(0);
            return fromEntity(first, "Matched " + partial.size() + " entities, all "
                    + departments.get(0) + " (e.g. " + first.getName() + ")");
        }

        String names = partial.stream().map(RegulatedEntity::getName).limit(4).toList().toString();
        log.warn("Entity '{}' matches {} entities across departments {} — refusing to guess coverage",
                entityName, partial.size(), departments);
        return new Coverage(CoverageStatus.AMBIGUOUS, null, null,
                "'" + entityName + "' matches " + partial.size() + " entities in different departments "
                        + departments + ", e.g. " + names + ". A specific entity must be selected.",
                schemeVersion);
    }

    private Coverage fromEntity(RegulatedEntity entity, String how) {
        boolean covered = DEPARTMENT_RBIO.equalsIgnoreCase(entity.getDepartment());
        return new Coverage(
                covered ? CoverageStatus.COVERED : CoverageStatus.NOT_COVERED,
                covered ? DEPARTMENT_RBIO : DEPARTMENT_CEPC,
                entity.getName(),
                covered
                        ? how + "; covered under the " + schemeName
                        : how + "; not covered under the " + schemeName + ", handled by CEPC",
                schemeVersion);
    }

    /**
     * Whether the entity is under the Scheme.
     *
     * <p>Signature retained for {@link MaintainabilityRulesEngine}, which asks a yes/no question and has
     * its own NEEDS_REVIEW handling for a blank entity. AMBIGUOUS and UNKNOWN both answer {@code false}
     * here, which is the fail-closed direction: the engine raises its ENTITY_NOT_COVERED ground and a
     * human decides, rather than the complaint being admitted on an unverified name.
     *
     * @param entityType currently unused. It was silently ignored before, which is part of why a CEPC
     *                   entity could answer "covered"; coverage comes from the entity's own department,
     *                   not from a caller-supplied type. Kept so the caller need not change.
     */
    public boolean isEntityCovered(String entityCode, String entityType) {
        return resolveCoverage(entityCode).isCovered();
    }
}
