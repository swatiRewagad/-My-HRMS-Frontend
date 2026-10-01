package com.hrms.cms.security;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalogue of REST-API-level authorities this application enforces.
 *
 * <h2>Why authorities and not roles</h2>
 * User administration is DELEGATED to the SSO. Users — both RBI staff and Regulated Entity users — are
 * created there, and the user→role→authority mapping is maintained there. The application's job is to
 * DECLARE the authorities it enforces and to check them; it does not own accounts, roles, or the mapping
 * between them.
 *
 * <p>That inverts the usual temptation. Guarding an endpoint with a role list (as
 * {@code @RbioRoleGuard} does) hardcodes an authorisation DECISION into the application: adding a rank
 * or splitting a role then needs a code change and a release. An authority names the CAPABILITY instead,
 * and the SSO decides which roles hold it. So a new RBIO rank is an SSO mapping change and nothing here
 * moves.
 *
 * <h2>Why this class exists rather than bare strings</h2>
 * The authority names are a published contract with whoever configures the SSO. A typo in a string
 * literal at a call site is a silent hole — the authority is never granted to anybody, so the endpoint
 * refuses everyone, or worse a mistyped check passes because nothing matches the intended name and a
 * fallback admits. Declaring them in one enum means the compiler catches the typo and
 * {@link #catalogue()} can publish the exact list the SSO must map.
 *
 * <h2>Naming</h2>
 * {@code <MODULE>_<RESOURCE>_<ACTION>}, upper snake case, no {@code ROLE_} prefix. The prefix is a Spring
 * convention for roles specifically, and these are deliberately not roles.
 */
public enum CmsAuthority {

    // ── Master data (UST456-459) ─────────────────────────────────────────────
    MASTER_DATA_READ("Read reference and master data"),
    MASTER_DATA_WRITE("Create, amend or deactivate master data"),
    MASTER_ROLE_MAPPING_WRITE("Amend role and authority mappings surfaced in the application"),
    MASTER_NODAL_MAPPING_WRITE("Amend the entity/office to Nodal Officer and PNO mapping"),
    CONFIG_AUDIT_READ("Retrieve the configuration change audit trail"),

    // ── RBIO complaint handling ──────────────────────────────────────────────
    RBIO_COMPLAINT_READ("View RBIO complaints"),
    RBIO_COMPLAINT_WRITE("Act on an RBIO complaint"),
    RBIO_COMPLAINT_REASSIGN("Change the owner of an RBIO complaint"),

    /**
     * Take a FINAL DECISION that closes a complaint.
     *
     * <p>This is UST629-631's closure eligibility, expressed as an authority. The stories describe it as
     * a per-user flag an Ombudsman Admin toggles, which would make the application a second source of
     * truth for a permission the SSO already owns — and the two would diverge. As an authority it is one
     * source: the SSO grants it to the Reviewers who may finally close, and UST629's "defaults to No" is
     * satisfied by simply not granting it.
     */
    RBIO_COMPLAINT_CLOSE_FINAL("Take the final decision that closes a complaint"),

    // ── Staff administration that REMAINS in the application ─────────────────
    //
    // Accounts and roles live in the SSO. What stays here is operational state the SSO has no concept
    // of: who is on leave, and which office an officer is posted to for routing.
    RBIO_STAFF_LEAVE_WRITE("Mark a staff member on or off leave"),
    RBIO_STAFF_PROFILE_READ("View staff operational profiles"),
    RBIO_STAFF_PROFILE_WRITE("Amend a staff member's office posting or designation"),

    // ── Reports (UST669-670) ─────────────────────────────────────────────────
    REPORT_VIEW("View reports"),
    REPORT_EXPORT("Export report output"),

    // ── Regulated Entity surfaces ────────────────────────────────────────────
    //
    // RE users arrive through the same SSO and are distinguished by the token's entity_code claim. These
    // authorities are separate from the RBIO ones so an RE user cannot inherit a supervisory capability
    // by holding a similarly named role.
    RE_COMPLAINT_READ("View complaints forwarded to the signed-in entity"),
    RE_COMPLAINT_RESPOND("Submit the entity's response to a complaint"),
    RE_NODAL_PROFILE_WRITE("Amend the entity's own Nodal Officer details");

    private final String description;

    CmsAuthority(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** The exact string the SSO must grant. Named rather than relying on {@code name()} at call sites. */
    public String authority() {
        return name();
    }

    /**
     * Whether this authority is only meaningful for a Regulated Entity principal.
     *
     * <p>Used to reject a nonsensical grant early: an RBI staff token carrying
     * {@code RE_COMPLAINT_RESPOND} is a mapping mistake, and answering it would let RBI staff file the
     * entity's own reply.
     */
    public boolean isRegulatedEntityAuthority() {
        return name().startsWith("RE_");
    }

    public static Optional<CmsAuthority> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String needle = raw.trim().toUpperCase();
        return Arrays.stream(values()).filter(a -> a.name().equals(needle)).findFirst();
    }

    /**
     * The published catalogue, for the SSO administrator to map roles against.
     *
     * <p>Served by an endpoint rather than kept in a document because a document drifts. If an authority
     * is added here and never mapped, every holder is refused — which is visible — whereas a stale
     * document produces a mapping nobody notices is missing.
     */
    public static List<Map<String, Object>> catalogue() {
        return Arrays.stream(values()).map(a -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("authority", a.name());
            item.put("description", a.description());
            item.put("principalType", a.isRegulatedEntityAuthority() ? "REGULATED_ENTITY" : "RBI");
            return item;
        }).toList();
    }
}
