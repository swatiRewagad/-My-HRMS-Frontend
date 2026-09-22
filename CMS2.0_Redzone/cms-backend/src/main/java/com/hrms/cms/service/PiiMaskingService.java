package com.hrms.cms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.security.RequestIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central masking of complainant PII (UST875).
 *
 * Masking is applied on the way out of the server, not in the browser: a client-side mask still
 * ships the full value over the wire, so anyone reading the network response sees the PII.
 *
 * This is distinct from encryption at rest ({@code EncryptionKeyService} / {@code PiiDecryptionFilter}).
 * Those protect stored bytes; this limits what an authenticated staff caller sees by default.
 *
 * Which fields are masked and which roles may reveal are read from SYSTEM_CONFIG so the field set
 * can change without a redeploy.
 */
@Service
@RequiredArgsConstructor
public class PiiMaskingService {

    public static final String CFG_MASKED_FIELDS = "cms.security.pii.masked_fields";
    public static final String CFG_REVEAL_ROLES = "cms.security.pii.reveal_roles";
    public static final String CFG_MASKING_ENABLED = "cms.security.pii.masking_enabled";

    private static final Set<String> DEFAULT_MASKED_FIELDS =
            Set.of("complainantName", "complainantPhone", "complainantEmail",
                   "complainantAddress", "accountNumber");

    private static final Set<String> DEFAULT_REVEAL_ROLES =
            Set.of("ADMIN", "CEPC_DO", "CEPC_OFFICER", "CEPC_INCHARGE", "CEPC_SUPERVISOR",
                   "CEPC_CLOSING_AUTHORITY", "RBIO_OFFICER", "RBIO_SUPERVISOR",
                   "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR");

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    public boolean isMaskingEnabled() {
        return systemConfigService.getBoolean(CFG_MASKING_ENABLED, true);
    }

    public Set<String> maskedFields() {
        return systemConfigService.getSet(CFG_MASKED_FIELDS, DEFAULT_MASKED_FIELDS);
    }

    public boolean isFieldMasked(String field) {
        return maskedFields().contains(field);
    }

    /**
     * Whether this caller is allowed to ask for unmasked values.
     *
     * Being permitted to reveal is not the same as seeing PII by default: a permitted caller still
     * receives masked values until an explicit reveal, and every reveal is recorded.
     */
    public boolean canReveal(RequestIdentity identity) {
        if (identity == null) {
            return false;
        }
        // RE staff belong to the bank being complained about, so they never get complainant PII.
        if (identity.isRe()) {
            return false;
        }
        Set<String> allowed = systemConfigService.getSet(CFG_REVEAL_ROLES, DEFAULT_REVEAL_ROLES);
        return identity.getRoles() != null && identity.getRoles().stream().anyMatch(allowed::contains);
    }

    public String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String trimmed = name.trim();
        return trimmed.charAt(0) + "*****";
    }

    public String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.trim();
        if (digits.length() < 4) {
            return "****";
        }
        return "******" + digits.substring(digits.length() - 4);
    }

    public String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) {
            return "****";
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at);
        return local.charAt(0) + "***" + domain;
    }

    /**
     * Shows the FIRST four and LAST four digits, masking the middle.
     *
     * The first four are retained because AA and RE staff reconcile an appeal against the regulated
     * entity's own records, where the leading digits identify the branch/product series; last-four
     * alone is not enough to match a disputed account and forced staff to request a reveal for routine
     * work, which defeats the point of default masking.
     *
     * A value with 8 or fewer characters would leak entirely under first4+last4 (the two windows meet
     * or overlap), so it stays fully masked. This is the same reasoning as the old <=4 guard, applied
     * to the wider window.
     */
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return accountNumber;
        }
        String trimmed = accountNumber.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        String first = trimmed.substring(0, 4);
        String last = trimmed.substring(trimmed.length() - 4);
        return first + "*".repeat(trimmed.length() - 8) + last;
    }

    /**
     * Card numbers use the same first4+last4 window as account numbers.
     *
     * Separate method because the field is separate and its acceptable disclosure could diverge: PCI
     * practice happens to permit exactly first-six/last-four, so if that is ever adopted it changes
     * here without touching account numbers.
     */
    public String maskCardNumber(String cardNumber) {
        return maskAccountNumber(cardNumber);
    }

    /** Address is not partially useful, so it collapses to a fixed marker. */
    public String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        return "[address hidden]";
    }

    /** Dispatches on field name so callers do not duplicate the field-to-algorithm mapping. */
    public String maskField(String field, String value) {
        if (value == null) {
            return null;
        }
        return switch (field) {
            case "complainantName", "repName", "withdrawnBy" -> maskName(value);
            case "complainantPhone", "repPhone", "complainantMobile" -> maskPhone(value);
            case "complainantEmail", "repEmail" -> maskEmail(value);
            case "accountNumber" -> maskAccountNumber(value);
            case "cardNumber" -> maskCardNumber(value);
            case "complainantAddress", "repAddress" -> maskAddress(value);
            default -> "****";
        };
    }

    /**
     * Applies masking only when the field is configured as sensitive and the caller has not been
     * granted an explicit reveal for this response.
     */
    public String apply(String field, String value, boolean revealed) {
        if (revealed || !isMaskingEnabled() || !isFieldMasked(field)) {
            return value;
        }
        return maskField(field, value);
    }

    /**
     * Masks a complaint for endpoints that serialise the JPA entity directly.
     *
     * Returns a detached map rather than mutating the entity: a managed entity whose fields were
     * overwritten with masked values would be flushed back by the persistence context at
     * transaction commit, permanently destroying the real PII.
     *
     * Field names absent from the map are left alone, so this stays correct as the entity evolves.
     */
    public Map<String, Object> maskComplaint(Complaint complaint, boolean revealed) {
        Map<String, Object> view = objectMapper.convertValue(
                complaint, new TypeReference<LinkedHashMap<String, Object>>() {});
        if (revealed || !isMaskingEnabled()) {
            return view;
        }
        for (String field : maskedFields()) {
            Object current = view.get(field);
            if (current instanceof String s) {
                view.put(field, maskField(field, s));
            }
        }
        view.put("piiMasked", true);
        return view;
    }

    public List<Map<String, Object>> maskComplaints(List<Complaint> complaints, boolean revealed) {
        return complaints.stream().map(c -> maskComplaint(c, revealed)).toList();
    }
}
