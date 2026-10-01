package com.hrms.cms.service;

import com.hrms.cms.entity.EntityOfficeNodalOfficer;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.EntityOfficeNodalOfficerRepository;
import com.hrms.cms.repository.OfficeThresholdConfigRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * UST773 + UST571: works out who actually answers for a Regulated Entity at a given Ombudsman office,
 * and who to fall back to when nobody does.
 *
 * <p>Resolution is a strict ladder, most specific first. Each rung is recorded on the result so the
 * timeline and the NO record can state <em>why</em> a particular officer was chosen — without that, an
 * officer looking at a complaint routed to a regional admin has no way to tell whether the entity has no
 * contacts on file or whether the office mapping is simply missing a row.
 *
 * <ol>
 *   <li>{@code ENTITY_OFFICE_NODAL_OFFICER} row for (entity, this office) — the UST773 answer.</li>
 *   <li>{@code ENTITY_OFFICE_NODAL_OFFICER} row for (entity, NULL office) — the entity's national desk.</li>
 *   <li>{@code REGULATED_ENTITIES} NO contacts — the pre-existing single-pair-per-entity data. Kept as a
 *       rung because it is the only NO data that exists today; dropping it would regress every entity
 *       that has contacts there to the admin fallback.</li>
 *   <li>PNO from the same sources, in the same order. UST571 treats the PNO as the escalation contact
 *       when no NO is on file, not as a co-equal.</li>
 *   <li>UST571 last resort: the regional Ombudsman Admin for the processing office.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodalOfficerResolver {

    private final EntityOfficeNodalOfficerRepository entityOfficeRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final OfficeThresholdConfigRepository officeThresholdRepository;
    private final SystemConfigRepository systemConfigRepository;

    /**
     * Config key for the global Ombudsman Admin token used when an office has no regional admin on
     * record. Held in SYSTEM_CONFIG rather than inlined so that an operator can repoint it without a
     * release — the alternative, a hardcoded role name, silently misroutes every unmapped office until
     * someone ships a patch.
     */
    static final String GLOBAL_ADMIN_CONFIG_KEY = "nodal.officer.fallback.global.admin.role";

    static final String DEFAULT_GLOBAL_ADMIN_ROLE = "RBIO_ADMIN";

    /** How the contacts on a {@link Resolution} were arrived at. Surfaced in the timeline and logs. */
    public enum Source {
        /** UST773 exact hit: a row for this entity at this specific office. */
        ENTITY_OFFICE,
        /** A row for this entity with no office set — the entity's national desk. */
        ENTITY_DEFAULT,
        /** Legacy single NO pair held on REGULATED_ENTITIES. */
        REGULATED_ENTITY,
        /** No NO anywhere, but a PNO was found. UST571's first fallback. */
        PNO_FALLBACK,
        /** UST571 last resort: nobody on file for the entity, routed to an Ombudsman Admin. */
        OMBUDSMAN_ADMIN
    }

    @Getter
    @Builder
    public static class Resolution {
        private final String nodalOfficerName;
        private final String designation;
        private final String email;
        private final String phone;
        private final String pnoName;
        private final String assignedTo;
        private final Source source;
        private final String processingOffice;

        /**
         * True when the entity has no usable contact at all. Callers use this to decide whether the
         * record needs chasing rather than re-deriving the condition from the source enum.
         */
        public boolean isFallbackToAdmin() {
            return source == Source.OMBUDSMAN_ADMIN;
        }
    }

    /**
     * @param entityName     the complaint's entity_code, which in this schema holds a display name
     * @param processingOffice office name in OFFICE_CODE_MASTER form; may be null when the creating path
     *                         has no office in scope
     */
    @Transactional(readOnly = true)
    public Resolution resolve(String entityName, String processingOffice) {
        String normalized = RegulatedEntity.normalize(entityName);

        if (normalized.isBlank()) {
            // No entity identifier means no entity-specific lookup is even possible. Falling back is the
            // only honest answer; guessing from complainant geography would attribute the complaint to a
            // bank nobody named.
            log.warn("No entity name supplied, falling back to Ombudsman Admin for office '{}'", processingOffice);
            return adminFallback(processingOffice);
        }

        Optional<EntityOfficeNodalOfficer> officeSpecific = processingOffice == null || processingOffice.isBlank()
                ? Optional.empty()
                : entityOfficeRepository
                    .findFirstByEntityNameNormalizedAndProcessingOfficeAndActiveTrue(normalized, processingOffice);

        if (officeSpecific.isPresent() && hasNodalOfficer(officeSpecific.get())) {
            return fromMapping(officeSpecific.get(), Source.ENTITY_OFFICE, processingOffice);
        }

        Optional<EntityOfficeNodalOfficer> entityDefault =
                entityOfficeRepository.findFirstByEntityNameNormalizedAndProcessingOfficeIsNullAndActiveTrue(normalized);

        if (entityDefault.isPresent() && hasNodalOfficer(entityDefault.get())) {
            return fromMapping(entityDefault.get(), Source.ENTITY_DEFAULT, processingOffice);
        }

        Optional<RegulatedEntity> regulatedEntity = regulatedEntityRepository.findByNameNormalized(normalized);

        if (regulatedEntity.isPresent() && isPresent(regulatedEntity.get().getNodalOfficerName())) {
            RegulatedEntity re = regulatedEntity.get();
            return Resolution.builder()
                    .nodalOfficerName(re.getNodalOfficerName())
                    .designation(re.getNodalOfficerDesignation())
                    .email(re.getNodalOfficerEmail())
                    .phone(re.getNodalOfficerPhone())
                    .pnoName(re.getPnoName())
                    .assignedTo(re.getNodalOfficerEmail() != null ? re.getNodalOfficerEmail() : re.getNodalOfficerName())
                    .source(Source.REGULATED_ENTITY)
                    .processingOffice(processingOffice)
                    .build();
        }

        // UST571 step 1: no NO anywhere. The PNO is the designated escalation contact, so try every
        // source again for a PNO before giving up on the entity entirely.
        Resolution pno = pnoFallback(officeSpecific, entityDefault, regulatedEntity, processingOffice);
        if (pno != null) {
            return pno;
        }

        log.warn("No NO or PNO on record for entity '{}' at office '{}' — falling back to Ombudsman Admin",
                entityName, processingOffice);
        return adminFallback(processingOffice);
    }

    private Resolution pnoFallback(Optional<EntityOfficeNodalOfficer> officeSpecific,
                                   Optional<EntityOfficeNodalOfficer> entityDefault,
                                   Optional<RegulatedEntity> regulatedEntity,
                                   String processingOffice) {
        for (Optional<EntityOfficeNodalOfficer> candidate : java.util.List.of(officeSpecific, entityDefault)) {
            if (candidate.isPresent() && isPresent(candidate.get().getPnoName())) {
                EntityOfficeNodalOfficer m = candidate.get();
                return Resolution.builder()
                        .pnoName(m.getPnoName())
                        .email(m.getPnoEmail())
                        .phone(m.getPnoPhone())
                        .assignedTo(m.getPnoEmail() != null ? m.getPnoEmail() : m.getPnoName())
                        .source(Source.PNO_FALLBACK)
                        .processingOffice(processingOffice)
                        .build();
            }
        }

        if (regulatedEntity.isPresent() && isPresent(regulatedEntity.get().getPnoName())) {
            RegulatedEntity re = regulatedEntity.get();
            return Resolution.builder()
                    .pnoName(re.getPnoName())
                    .email(re.getPnoEmail())
                    .phone(re.getPnoPhone())
                    .assignedTo(re.getPnoEmail() != null ? re.getPnoEmail() : re.getPnoName())
                    .source(Source.PNO_FALLBACK)
                    .processingOffice(processingOffice)
                    .build();
        }

        return null;
    }

    /**
     * UST571: route to the Ombudsman Admin for the processing office.
     *
     * <p>RBIO_ADMIN is a global Keycloak role with no region attribute, so "regional admin" cannot be
     * resolved from roles. {@code OFFICE_THRESHOLD_CONFIG} is the only table that enumerates offices with
     * an active flag, so an active row for the office is taken as proof the office exists and its admin
     * token is derived from its {@code officeId} (e.g. RBIO-MUM → RBIO-MUM_ADMIN). When the office is
     * unknown or inactive there is no regional admin to name, and the global token from SYSTEM_CONFIG is
     * used instead — logged at WARN, because a complaint sitting in a global queue is a complaint nobody
     * owns.
     */
    private Resolution adminFallback(String processingOffice) {
        String assignee = null;

        if (processingOffice != null && !processingOffice.isBlank()) {
            assignee = officeThresholdRepository.findByActiveTrueOrderByOverflowSequenceOrderAsc().stream()
                    .filter(o -> processingOffice.equalsIgnoreCase(o.getOfficeId())
                              || processingOffice.equalsIgnoreCase(o.getOfficeName()))
                    .map(o -> o.getOfficeId() + "_ADMIN")
                    .findFirst()
                    .orElse(null);
        }

        if (assignee == null) {
            assignee = globalAdminRole();
            log.warn("No regional Ombudsman Admin found for office '{}' — using global role '{}'",
                    processingOffice, assignee);
        }

        return Resolution.builder()
                .assignedTo(assignee)
                .source(Source.OMBUDSMAN_ADMIN)
                .processingOffice(processingOffice)
                .build();
    }

    private String globalAdminRole() {
        return systemConfigRepository.findByConfigKey(GLOBAL_ADMIN_CONFIG_KEY)
                .map(c -> c.getConfigValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(DEFAULT_GLOBAL_ADMIN_ROLE);
    }

    private Resolution fromMapping(EntityOfficeNodalOfficer m, Source source, String processingOffice) {
        return Resolution.builder()
                .nodalOfficerName(m.getNodalOfficerName())
                .designation(m.getNodalOfficerDesignation())
                .email(m.getNodalOfficerEmail())
                .phone(m.getNodalOfficerPhone())
                .pnoName(m.getPnoName())
                .assignedTo(m.getNodalOfficerEmail() != null ? m.getNodalOfficerEmail() : m.getNodalOfficerName())
                .source(source)
                .processingOffice(processingOffice)
                .build();
    }

    private boolean hasNodalOfficer(EntityOfficeNodalOfficer m) {
        return isPresent(m.getNodalOfficerName());
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
