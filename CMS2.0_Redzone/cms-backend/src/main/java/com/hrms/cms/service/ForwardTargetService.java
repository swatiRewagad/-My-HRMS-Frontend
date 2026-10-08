package com.hrms.cms.service;

import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.entity.RbiDepartmentMaster;
import com.hrms.cms.entity.RegulatoryBodyMaster;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.repository.RbiDepartmentMasterRepository;
import com.hrms.cms.repository.RegulatoryBodyMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * THE authority on where a complaint may be forwarded: offices, RBI departments and external regulators
 * (UST556, 563, 761, 766, 534, 527-528).
 *
 * <p><b>Why one service for all three.</b> Every forwarding destination in this codebase was previously either
 * a hardcoded array in a component, a free-text input, or an endpoint that did not exist. Validating each kind
 * in the place that happens to need it is how three different notions of "a valid destination" arise; a
 * complaint routed to a destination one path accepts and another rejects is stuck.
 *
 * <p><b>Everything here fails CLOSED.</b> An unknown office, department or body is REFUSED rather than
 * accepted as free text. Territorial and subject-matter jurisdiction decide who may lawfully handle a
 * complaint, so forwarding to a plausible-looking string is worse than refusing: the complaint leaves the
 * office that could act on it and arrives nowhere.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForwardTargetService {

    /** The layout (owning module) values. See the V96 migration header for what "layout" was ruled to mean. */
    public static final String LAYOUT_RBIO = "RBIO";
    public static final String LAYOUT_CEPC = "CEPC";
    public static final String LAYOUT_CEPD = "CEPD";

    /** {@code OFFICE_CODE_MASTER.officeType} values. 'BO' is the pre-existing value on every RBIO office. */
    private static final String OFFICE_TYPE_RBIO = "BO";
    private static final String OFFICE_TYPE_CEPC = "CEPC";

    private static final String CFG_REQUIRE_VERIFIED_EMAIL = "forward.require_verified_body_email";

    private final OfficeCodeMasterRepository officeRepository;
    private final RbiDepartmentMasterRepository departmentRepository;
    private final RegulatoryBodyMasterRepository regulatoryBodyRepository;
    private final SystemConfigService systemConfigService;

    /**
     * The durable officer assigner, used for department routing (UST761).
     *
     * <p>Optional so this service can be constructed in unit tests without the Keycloak-backed assignment
     * stack. When absent no officer is resolved and the forward records the department without an owner, which
     * is visible in the timeline rather than silently wrong.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DurableRoundRobinAssigner roundRobinAssigner;

    // ══════════════════════════════════════════════════════════════════
    // Offices
    // ══════════════════════════════════════════════════════════════════

    /** Every active RBIO (branch) office, for the "target RBIO office" picker (UST559, 556). */
    public List<Map<String, Object>> rbioOffices() {
        return officeRepository.findByOfficeTypeAndIsActiveTrueOrderByOfficeNameAsc(OFFICE_TYPE_RBIO).stream()
                .map(ForwardTargetService::toOfficePayload)
                .toList();
    }

    /**
     * Every active CEPC office, for the picker UST563 requires when Transfer Office = CEPC.
     *
     * <p>Returns empty rather than falling back to the RBIO list when no CEPC office is configured. A caller
     * must show "no destinations available" and refuse, because offering an RBIO office where a CEPC one was
     * asked for would route the complaint into the wrong module entirely.
     */
    public List<Map<String, Object>> cepcOffices() {
        return officeRepository.findByOfficeTypeAndIsActiveTrueOrderByOfficeNameAsc(OFFICE_TYPE_CEPC).stream()
                .map(ForwardTargetService::toOfficePayload)
                .toList();
    }

    /** Refuses an office code that is not in the master. */
    public void assertOfficeExists(String officeCode) {
        if (findOffice(officeCode).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.forward.error.office_unknown: '" + officeCode
                            + "' is not an active office in the office master.");
        }
    }

    /**
     * The layout (owning module) an office belongs to.
     *
     * <p><b>This replaces a prefix match that was provably wrong.</b> The previous {@code resolveDepartment}
     * tested {@code officeId.startsWith("CEPC")} / {@code "CEPD"} and otherwise returned "RBIO" — but offices
     * are keyed by a NUMERIC OFFICE_CODE ("013"), so every real office fell off the end and resolved RBIO.
     * An RBIO-to-CEPC conversion therefore recorded itself as RBIO-to-RBIO, and UST633's layout conversion
     * could never happen.
     *
     * <p>Read from the master's own {@code officeType}, so the answer comes from data rather than from the
     * shape of a string.
     */
    public String layoutForOffice(String officeCode) {
        return findOffice(officeCode)
                .map(office -> OFFICE_TYPE_CEPC.equalsIgnoreCase(office.getOfficeType())
                        ? LAYOUT_CEPC : LAYOUT_RBIO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "rbio.forward.error.office_unknown: cannot determine the layout of unknown office '"
                                + officeCode + "'."));
    }

    /** The office master row for a code or a name — callers legitimately hold either. */
    public Optional<OfficeCodeMaster> findOffice(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) return Optional.empty();
        String value = codeOrName.trim();
        Optional<OfficeCodeMaster> byCode = officeRepository.findByOfficeCodeAndIsActiveTrue(value);
        if (byCode.isPresent()) return byCode;
        return officeRepository.findByOfficeNameAndIsActiveTrue(value);
    }

    /** The canonical OFFICE_CODE for whatever the caller supplied, or a refusal. */
    public String resolveOfficeCode(String codeOrName) {
        return findOffice(codeOrName)
                .map(OfficeCodeMaster::getOfficeCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "rbio.forward.error.office_unknown: '" + codeOrName
                                + "' is not an active office in the office master."));
    }

    // ══════════════════════════════════════════════════════════════════
    // RBI departments
    // ══════════════════════════════════════════════════════════════════

    public List<Map<String, Object>> departments() {
        return departmentRepository.findByIsActiveOrderByDisplayOrderAsc("Y").stream()
                .map(ForwardTargetService::toDepartmentPayload)
                .toList();
    }

    /**
     * The canonical department name, refusing anything not in the master.
     *
     * <p>Accepts either the code or the name because the existing component sends a display name.
     */
    public String resolveDepartmentName(String codeOrName) {
        return findDepartment(codeOrName)
                .map(RbiDepartmentMaster::getDeptName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "rbio.forward.error.department_unknown: '" + codeOrName
                                + "' is not an active RBI department in the department master."));
    }

    public Optional<RbiDepartmentMaster> findDepartment(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) return Optional.empty();
        String value = codeOrName.trim();
        Optional<RbiDepartmentMaster> byCode = departmentRepository.findByDeptCodeIgnoreCase(value);
        if (byCode.isPresent() && byCode.get().active()) return byCode;
        Optional<RbiDepartmentMaster> byName = departmentRepository.findByDeptNameIgnoreCase(value);
        return byName.filter(RbiDepartmentMaster::active);
    }

    /**
     * An officer inside the destination department, chosen by the durable round-robin (UST761).
     *
     * <p>Uses {@code DurableRoundRobinAssigner}, whose pointer is a persisted userId — NOT one of the four
     * in-memory counters elsewhere in this codebase, each of which is per-pod and so rotates differently on
     * every instance.
     *
     * <p>Returns null rather than throwing when no officer can be found: the department is a real destination
     * and the forward should still be recorded, with the absent owner visible in the timeline. Refusing the
     * whole forward because a role group has no members would strand the complaint in the source office.
     */
    public String resolveDepartmentOfficer(String departmentCodeOrName) {
        if (roundRobinAssigner == null) return null;

        String roleGroup = findDepartment(departmentCodeOrName)
                .map(RbiDepartmentMaster::getAssignRoleGroup)
                .orElse(null);
        if (roleGroup == null || roleGroup.isBlank()) {
            log.warn("Department '{}' has no assignRoleGroup configured — forwarded without an owner",
                    departmentCodeOrName);
            return null;
        }

        try {
            DurableRoundRobinAssigner.Assignment assignment = roundRobinAssigner.assignNext(roleGroup);
            if (assignment.isAssigned()) {
                return assignment.officerId();
            }
            log.warn("Round-robin found no available officer in role group {} for department {}: {}",
                    roleGroup, departmentCodeOrName, assignment.reason());
        } catch (Exception e) {
            log.warn("Could not resolve an officer for department {}: {}", departmentCodeOrName, e.getMessage());
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════
    // External regulatory bodies (UST766)
    // ══════════════════════════════════════════════════════════════════

    /**
     * The bodies a complaint may be referred to.
     *
     * <p>Only forwardable bodies are returned when verification is required, so the dropdown cannot offer a
     * destination the server will then refuse. {@code emailVerified} is exposed either way, so an
     * administrative screen can show which bodies still need verifying.
     */
    public List<Map<String, Object>> regulatoryBodies() {
        boolean requireVerified = requireVerifiedEmail();
        return regulatoryBodyRepository.findByIsActiveOrderByBodyNameAsc("Y").stream()
                .filter(body -> !requireVerified || body.forwardable())
                .map(ForwardTargetService::toRegulatoryBodyPayload)
                .toList();
    }

    /** Every active body regardless of verification, for administration. */
    public List<Map<String, Object>> allRegulatoryBodies() {
        return regulatoryBodyRepository.findByIsActiveOrderByBodyNameAsc("Y").stream()
                .map(ForwardTargetService::toRegulatoryBodyPayload)
                .toList();
    }

    /**
     * The canonical body name, refusing an unknown body and — when configured — an unverified one.
     *
     * <p>UST766 restricts forwarding to the validated master list WITH VERIFIED EMAIL IDS. Before this, any
     * free-text string was accepted because the master did not exist, so a citizen's complaint could be
     * recorded as referred to an organisation nobody had contact details for.
     */
    public String resolveRegulatoryBodyName(String codeNameOrId) {
        RegulatoryBodyMaster body = findRegulatoryBody(codeNameOrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "rbio.forward.error.body_unknown: '" + codeNameOrId
                                + "' is not an active regulatory body in the master list."));

        if (requireVerifiedEmail() && !body.forwardable()) {
            // 409 rather than 400: the request names a real body, but its state does not permit a referral.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "rbio.forward.error.body_email_unverified: '" + body.getBodyName() + "' cannot receive a "
                            + "referral until its contact email has been verified.");
        }
        return body.getBodyName();
    }

    /** The contact address a referral notification goes to, or null when none is recorded. */
    public String regulatoryBodyEmail(String codeNameOrId) {
        return findRegulatoryBody(codeNameOrId).map(RegulatoryBodyMaster::getContactEmail).orElse(null);
    }

    /** Accepts a code, a name or a numeric id — the forwarding screen sends whichever it has. */
    public Optional<RegulatoryBodyMaster> findRegulatoryBody(String codeNameOrId) {
        if (codeNameOrId == null || codeNameOrId.isBlank()) return Optional.empty();
        String value = codeNameOrId.trim();

        Optional<RegulatoryBodyMaster> byCode = regulatoryBodyRepository.findByBodyCodeIgnoreCase(value);
        if (byCode.isPresent() && byCode.get().active()) return byCode;

        Optional<RegulatoryBodyMaster> byName = regulatoryBodyRepository.findByBodyNameIgnoreCase(value);
        if (byName.isPresent() && byName.get().active()) return byName;

        // A bare number is an id. Tried last so a body whose CODE is numeric is not shadowed by an id lookup.
        if (value.matches("\\d+")) {
            try {
                return regulatoryBodyRepository.findById(Long.parseLong(value))
                        .filter(RegulatoryBodyMaster::active);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Whether an unverified body may receive a referral.
     *
     * <p>Defaults to TRUE (verification required) when the config row is missing, which is the fail-closed
     * direction: every seeded body starts unverified, so defaulting the other way would permit referrals to
     * unconfirmed addresses on a database where nobody had made that decision.
     */
    private boolean requireVerifiedEmail() {
        try {
            return systemConfigService.getBoolean(CFG_REQUIRE_VERIFIED_EMAIL, true);
        } catch (Exception e) {
            log.debug("Could not read {}, requiring verification: {}", CFG_REQUIRE_VERIFIED_EMAIL, e.getMessage());
            return true;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Payloads
    // ══════════════════════════════════════════════════════════════════

    private static Map<String, Object> toOfficePayload(OfficeCodeMaster office) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("officeCode", office.getOfficeCode());
        payload.put("officeName", office.getOfficeName());
        payload.put("officeType", office.getOfficeType());
        payload.put("layout", OFFICE_TYPE_CEPC.equalsIgnoreCase(office.getOfficeType())
                ? LAYOUT_CEPC : LAYOUT_RBIO);
        return payload;
    }

    /**
     * Carries each field under BOTH names its two callers read.
     *
     * <p>The RBIO service types this as {@code {deptCode, deptName}} while the CEPC Forward tab reads
     * {@code {id, code, name, email}} — and that tab REFUSES the forward when the email is empty, telling the
     * officer "This department has no email on record" about a department whose email is right here. One
     * response satisfying both is the alternative to a coordinated release, and dropping either set would
     * break a working screen to tidy a payload.
     */
    private static Map<String, Object> toDepartmentPayload(RbiDepartmentMaster dept) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", dept.getId());
        payload.put("deptCode", dept.getDeptCode());
        payload.put("deptName", dept.getDeptName());
        payload.put("code", dept.getDeptCode());
        payload.put("name", dept.getDeptName());
        payload.put("contactEmail", dept.getContactEmail());
        payload.put("email", dept.getContactEmail());
        payload.put("displayOrder", dept.getDisplayOrder());
        return payload;
    }

    /**
     * Shaped to the frontend's existing {@code RegulatoryBody} interface so the component binds unchanged,
     * with {@code emailVerified} added — the field the screen has always claimed to enforce.
     */
    private static Map<String, Object> toRegulatoryBodyPayload(RegulatoryBodyMaster body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", String.valueOf(body.getId()));
        payload.put("code", body.getBodyCode());
        payload.put("name", body.getBodyName());
        payload.put("contactEmail", body.getContactEmail());
        // Same address under the name the CEPC Forward tab reads; see toDepartmentPayload's comment.
        payload.put("email", body.getContactEmail());
        payload.put("emailVerified", "Y".equalsIgnoreCase(body.getEmailVerified()));
        payload.put("forwardable", body.forwardable());
        payload.put("address", body.getAddress());
        payload.put("jurisdiction", body.getJurisdiction());
        return payload;
    }
}
