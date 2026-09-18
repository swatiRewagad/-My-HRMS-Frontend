package com.hrms.cms.service;

import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.entity.OfficeAssignmentMapping;
import com.hrms.cms.entity.OfficeAssignmentStrategy;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import com.hrms.cms.repository.OfficeAssignmentMappingRepository;
import com.hrms.cms.repository.OfficeAssignmentStrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Picks the Dealing Officer for a complaint using the logic its office is configured to use
 * (UST468-472).
 *
 * <h2>The three logics, and what happens when one cannot answer</h2>
 * An office runs exactly one of ROUND_ROBIN, ENTITY_MAPPING or CATEGORY_MAPPING. The mapping logics can
 * fail to produce an officer — no mapping row, or a mapped officer who is on leave — and UST470/472
 * require that to fall back to the Ombudsman Admin rather than silently doing something else.
 *
 * <p>There are therefore three rungs, tried in order: the configured logic, then the office's Ombudsman
 * Admin, then nobody. It deliberately does NOT fall back from a mapping to round-robin: an office that
 * chose entity mapping did so because particular officers must handle particular banks, and quietly
 * rotating to someone else would break that arrangement while looking like it worked. The Ombudsman
 * Admin is a person who will notice and act; a rotation is not.
 *
 * <h2>Every lookup is recorded</h2>
 * UST469/472 require the lookup to be traceable, and the reason is practical: when a complaint lands on
 * an admin instead of the named officer, somebody has to be able to tell whether the mapping was
 * missing, the officer was on leave, or the office was never configured. The returned
 * {@link Resolution} carries that reason so the caller can put it on the complaint timeline — a log
 * line alone is not traceability, because nobody reads logs per complaint.
 *
 * <h2>Changes are never retrospective</h2>
 * The strategy row is read on EVERY assignment and never cached here. So a Super Admin's change affects
 * only complaints assigned after it, which is exactly UST468's requirement, and it needs no restart —
 * there is no startup snapshot to invalidate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeAssignmentStrategyService {

    private static final String ROLE_OMBUDSMAN_ADMIN = "RBIO_ADMIN";

    private final OfficeAssignmentStrategyRepository strategyRepository;
    private final OfficeAssignmentMappingRepository mappingRepository;
    private final AaOfficerPoolRepository officerPoolRepository;
    private final DurableRoundRobinAssigner roundRobinAssigner;

    /**
     * Who the complaint goes to, which logic decided it, and why.
     *
     * @param officerId  the chosen officer, or null when nobody could be found
     * @param strategy   the logic that was in force
     * @param outcome    how the officer was arrived at — see the OUTCOME_ constants
     * @param reason     human-readable, intended for the complaint timeline
     */
    public record Resolution(String officerId, String strategy, String outcome, String reason) {

        public static final String OUTCOME_MAPPED = "MAPPED";
        public static final String OUTCOME_ROTATED = "ROTATED";
        public static final String OUTCOME_ADMIN_FALLBACK = "OMBUDSMAN_ADMIN_FALLBACK";
        public static final String OUTCOME_UNASSIGNED = "UNASSIGNED";

        public boolean isAssigned() {
            return officerId != null && !officerId.isBlank();
        }
    }

    /**
     * Resolves the officer for one complaint.
     *
     * @param officeId    OFFICE_CODE of the office processing the complaint
     * @param entityName  the regulated entity the complaint is against; may be blank
     * @param categoryId  the complaint category id; may be null
     */
    @Transactional
    public Resolution resolveOfficer(String officeId, String entityName, Long categoryId) {
        return resolveOfficer(officeId, entityName, categoryId, false);
    }

    /**
     * Resolves the officer for one complaint, optionally without side effects.
     *
     * @param dryRun when true, the rotation pointer is NOT advanced. This exists because the admin preview
     *               endpoint is a GET: advancing the pointer to answer "who would get this?" would change
     *               who the NEXT real complaint goes to, so simply looking at the configuration would
     *               quietly reorder the rota. Mapping lookups have no side effects either way, so a dry-run
     *               preview is exact for the mapping strategies and reports the rota's current position
     *               rather than consuming a turn for ROUND_ROBIN.
     */
    @Transactional
    public Resolution resolveOfficer(String officeId, String entityName, Long categoryId, boolean dryRun) {
        OfficeAssignmentStrategy config = strategyRepository.findByOfficeId(officeId).orElse(null);

        // An unconfigured office rotates. Refusing to assign because nobody has configured the office
        // would stall every complaint filed there, which is a worse failure than a default.
        String strategy = config != null ? config.getStrategy() : OfficeAssignmentStrategy.ROUND_ROBIN;
        String roleGroup = config != null ? config.getRoleGroup() : "RBIO_OFFICER";

        if (!OfficeAssignmentStrategy.isKnownStrategy(strategy)) {
            // A strategy string nothing understands must not silently become round-robin: that would hide
            // a typo in configuration behind plausible behaviour. Rotate, but say so loudly.
            log.error("Office {} is configured with unknown assignment strategy '{}' — rotating instead",
                    officeId, strategy);
            strategy = OfficeAssignmentStrategy.ROUND_ROBIN;
        }

        return switch (strategy) {
            case OfficeAssignmentStrategy.ENTITY_MAPPING ->
                    byMapping(officeId, roleGroup, OfficeAssignmentMapping.TYPE_ENTITY,
                            OfficeAssignmentMapping.normaliseSubject(entityName),
                            entityName, OfficeAssignmentStrategy.ENTITY_MAPPING);
            case OfficeAssignmentStrategy.CATEGORY_MAPPING ->
                    byMapping(officeId, roleGroup, OfficeAssignmentMapping.TYPE_CATEGORY,
                            categoryId != null ? String.valueOf(categoryId) : "",
                            categoryId != null ? "category " + categoryId : null,
                            OfficeAssignmentStrategy.CATEGORY_MAPPING);
            default -> byRotation(officeId, roleGroup, dryRun);
        };
    }

    private Resolution byRotation(String officeId, String roleGroup, boolean dryRun) {
        if (dryRun) {
            // Report where the rota stands without consuming a turn.
            return roundRobinAssigner.currentPointer(roleGroup)
                    .map(last -> new Resolution(last, OfficeAssignmentStrategy.ROUND_ROBIN,
                            Resolution.OUTCOME_ROTATED,
                            "Rotation is currently at " + last + "; the next complaint goes to the officer "
                                    + "after them in " + roleGroup))
                    .orElseGet(() -> new Resolution(null, OfficeAssignmentStrategy.ROUND_ROBIN,
                            Resolution.OUTCOME_ROTATED,
                            "Rotation has no history yet; the next complaint goes to the first "
                                    + roleGroup + " in order"));
        }

        DurableRoundRobinAssigner.Assignment assignment = roundRobinAssigner.assignNext(roleGroup);
        if (assignment.isAssigned()) {
            return new Resolution(assignment.officerId(), OfficeAssignmentStrategy.ROUND_ROBIN,
                    Resolution.OUTCOME_ROTATED,
                    "Rotated to " + assignment.officerId() + " among " + roleGroup);
        }
        // Rotation exhausted means every officer is inactive or on leave, which is precisely when an
        // office's admin should be holding the file.
        return fallbackToOmbudsmanAdmin(officeId, OfficeAssignmentStrategy.ROUND_ROBIN,
                "no available " + roleGroup + " to rotate to");
    }

    private Resolution byMapping(String officeId, String roleGroup, String mappingType,
                                 String subjectKey, String subjectLabel, String strategy) {
        if (subjectKey == null || subjectKey.isBlank()) {
            // No subject to map on — a complaint with no entity name under ENTITY_MAPPING, for instance.
            // This is a data gap, not an officer's absence, so it must not look like an exhausted rota.
            return fallbackToOmbudsmanAdmin(officeId, strategy,
                    "no " + mappingType.toLowerCase() + " on the complaint to map on");
        }

        Optional<OfficeAssignmentMapping> mapping = mappingRepository
                .findByOfficeIdAndMappingTypeAndSubjectKeyAndActiveTrue(officeId, mappingType, subjectKey);

        if (mapping.isEmpty()) {
            return fallbackToOmbudsmanAdmin(officeId, strategy,
                    "no active mapping for " + (subjectLabel != null ? subjectLabel : subjectKey)
                            + " at office " + officeId);
        }

        String mapped = mapping.get().getTargetOfficerId();
        if (isUnavailable(mapped, roleGroup)) {
            // UST470/472: the mapped officer exists but cannot take work today. The complaint goes to the
            // admin rather than to a substitute officer, because the mapping expresses a deliberate
            // assignment of that bank or category to that person.
            return fallbackToOmbudsmanAdmin(officeId, strategy,
                    "mapped officer " + mapped + " is inactive or on leave");
        }

        return new Resolution(mapped, strategy, Resolution.OUTCOME_MAPPED,
                "Mapped " + (subjectLabel != null ? subjectLabel : subjectKey) + " to " + mapped);
    }

    /**
     * True when the officer pool explicitly says this officer cannot take work.
     *
     * <p>Absence of a pool row means available, matching {@link DurableRoundRobinAssigner}: the pool is an
     * exclusion list, and treating a missing row as unavailable would make every mapping fall through to
     * the admin until somebody seeded the pool.
     */
    private boolean isUnavailable(String officerId, String roleGroup) {
        try {
            return officerPoolRepository.findByRoleGroupOrderByUserIdAsc(roleGroup).stream()
                    .filter(row -> officerId.equals(row.getUserId()))
                    .anyMatch(row -> !row.isActive() || row.isOnLeave());
        } catch (Exception e) {
            // A pool read failure must not route the complaint to an admin — that would turn a transient
            // database problem into a visible change in who handles complaints.
            log.error("Could not check availability of {} in {}: {}", officerId, roleGroup, e.getMessage());
            return false;
        }
    }

    /**
     * UST470/UST472: the office's own Ombudsman Admin takes the file when the configured logic cannot.
     *
     * <p>"Regional" is expressed through {@code WF_OFFICER_POOL.regional_office}, because there is no
     * other office dimension on any user record — the RBIO_ADMIN role itself is global. An office with no
     * admin row falls through to unassigned rather than borrowing another office's admin: an admin in a
     * different office has no jurisdiction over this complaint.
     */
    private Resolution fallbackToOmbudsmanAdmin(String officeId, String strategy, String why) {
        Optional<String> admin = findOmbudsmanAdmin(officeId);

        if (admin.isPresent()) {
            log.info("Office {} assignment fell back to Ombudsman Admin {} ({})",
                    officeId, admin.get(), why);
            return new Resolution(admin.get(), strategy, Resolution.OUTCOME_ADMIN_FALLBACK,
                    "Assigned to Ombudsman Admin " + admin.get() + " because " + why);
        }

        log.warn("Office {} has no Ombudsman Admin to fall back to ({}) — complaint left unassigned",
                officeId, why);
        return new Resolution(null, strategy, Resolution.OUTCOME_UNASSIGNED,
                "Left unassigned: " + why + ", and office " + officeId + " has no Ombudsman Admin");
    }

    private Optional<String> findOmbudsmanAdmin(String officeId) {
        try {
            List<AaOfficerPool> admins =
                    officerPoolRepository.findByRoleGroupOrderByUserIdAsc(ROLE_OMBUDSMAN_ADMIN);
            return admins.stream()
                    .filter(AaOfficerPool::isActive)
                    .filter(row -> !row.isOnLeave())
                    .filter(row -> officeId.equals(row.getRegionalOffice()))
                    .map(AaOfficerPool::getUserId)
                    .findFirst();
        } catch (Exception e) {
            log.error("Could not resolve the Ombudsman Admin for office {}: {}", officeId, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Administration (Super Admin only; guarded at the controller) ─────────────────────────────────

    /**
     * Sets an office's assignment logic.
     *
     * <p>Validates the strategy name rather than storing whatever arrives: an unrecognised value would sit
     * in the table looking configured while behaving as round-robin, and the office would believe it was
     * routing by entity when it was not.
     *
     * @param reason why the change was made — required, because an assignment-policy change with no
     *               recorded rationale cannot be reviewed later
     */
    @Transactional
    public OfficeAssignmentStrategy setStrategy(String officeId, String strategy, String roleGroup,
                                                String updatedBy, String reason) {
        if (officeId == null || officeId.isBlank()) {
            throw new IllegalArgumentException("officeId is required");
        }
        if (!OfficeAssignmentStrategy.isKnownStrategy(strategy)) {
            throw new IllegalArgumentException("Unknown assignment strategy: " + strategy
                    + ". Expected one of ROUND_ROBIN, ENTITY_MAPPING, CATEGORY_MAPPING");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when changing an office's assignment strategy");
        }

        OfficeAssignmentStrategy config = strategyRepository.findByOfficeId(officeId)
                .orElseGet(() -> OfficeAssignmentStrategy.builder().officeId(officeId).build());

        String previous = config.getStrategy();
        config.setStrategy(strategy);
        if (roleGroup != null && !roleGroup.isBlank()) {
            config.setRoleGroup(roleGroup);
        }
        config.setUpdatedBy(updatedBy);
        config.setReason(reason);
        config.setUpdatedAt(LocalDateTime.now());

        OfficeAssignmentStrategy saved = strategyRepository.save(config);

        // Logged at INFO with the actor and both values: this is a national routing-policy change, and
        // "who changed it from what to what" is the first question anyone asks afterwards.
        log.info("Office {} assignment strategy changed from {} to {} by {} ({}). Applies to complaints "
                        + "assigned from now on; existing assignments are unchanged.",
                officeId, previous, strategy, updatedBy, reason);

        if (saved.requiresMappings()) {
            String type = OfficeAssignmentStrategy.ENTITY_MAPPING.equals(strategy)
                    ? OfficeAssignmentMapping.TYPE_ENTITY : OfficeAssignmentMapping.TYPE_CATEGORY;
            long mappings = mappingRepository.countByOfficeIdAndMappingTypeAndActiveTrue(officeId, type);
            if (mappings == 0) {
                // Not refused: an office may legitimately configure the strategy before loading its
                // mappings. But every complaint until then goes to the admin, so it is said plainly.
                log.warn("Office {} now uses {} but has NO active {} mappings — every complaint will fall "
                                + "back to the Ombudsman Admin until mappings are added",
                        officeId, strategy, type);
            }
        }
        return saved;
    }

    @Transactional
    public OfficeAssignmentMapping upsertMapping(String officeId, String mappingType, String subject,
                                                 String targetOfficerId, String updatedBy) {
        if (officeId == null || officeId.isBlank()) {
            throw new IllegalArgumentException("officeId is required");
        }
        if (!OfficeAssignmentMapping.TYPE_ENTITY.equals(mappingType)
                && !OfficeAssignmentMapping.TYPE_CATEGORY.equals(mappingType)) {
            throw new IllegalArgumentException("mappingType must be ENTITY or CATEGORY, got: " + mappingType);
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("A subject (entity name or category id) is required");
        }
        if (targetOfficerId == null || targetOfficerId.isBlank()) {
            throw new IllegalArgumentException("targetOfficerId is required");
        }

        String key = OfficeAssignmentMapping.TYPE_ENTITY.equals(mappingType)
                ? OfficeAssignmentMapping.normaliseSubject(subject)
                : subject.trim();

        OfficeAssignmentMapping mapping = mappingRepository
                .findByOfficeIdAndMappingTypeAndSubjectKeyAndActiveTrue(officeId, mappingType, key)
                .orElseGet(() -> OfficeAssignmentMapping.builder()
                        .officeId(officeId).mappingType(mappingType).subjectKey(key).build());

        mapping.setSubjectLabel(subject.trim());
        mapping.setTargetOfficerId(targetOfficerId.trim());
        mapping.setActive(true);
        mapping.setUpdatedBy(updatedBy);

        return mappingRepository.save(mapping);
    }

    @Transactional(readOnly = true)
    public List<OfficeAssignmentStrategy> allStrategies() {
        return strategyRepository.findAllByOrderByOfficeIdAsc();
    }

    @Transactional(readOnly = true)
    public List<OfficeAssignmentMapping> mappingsFor(String officeId) {
        return mappingRepository.findByOfficeIdOrderByMappingTypeAscSubjectKeyAsc(officeId);
    }
}
