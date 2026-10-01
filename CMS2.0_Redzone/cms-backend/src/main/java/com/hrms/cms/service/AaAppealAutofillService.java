package com.hrms.cms.service;

import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.PincodeRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the pre-filled Register-milestone form from a parent complaint (stories 8, 9, 10).
 *
 * THE CENTRAL RULE HERE IS THAT AN UNKNOWN VALUE STAYS UNKNOWN. Several fields the stories ask us to
 * "auto-fill" have no source anywhere in the system, verified field by field against the schema and the
 * live data:
 *
 *   - complainant CITY, COUNTRY, PINCODE: COMPLAINTS has complainant_state and complainant_district
 *     only. rep_city/rep_pincode belong to the authorised REPRESENTATIVE, who is frequently a
 *     different person at a different address — copying them into the appellant block would fabricate
 *     the appellant's address. PINCODE is keyed BY pincode, so it resolves district/state FROM a
 *     pincode and cannot invent one from a district.
 *   - ENTITY REGION: REGULATED_ENTITIES has no region column. pincodes.region is a POSTAL region, a
 *     different taxonomy from an RBI entity region.
 *   - BSR / IFSC: no IFSC or BSR column exists on BANKS; banks.code is a short mnemonic ('SBI').
 *   - CARD NUMBER: does not exist on Complaint at all.
 *   - NODAL OFFICER: regulated_entities.nodal_officer_name is NULL in all 145 rows and
 *     nodal_officer_records is empty, so the schema exists but there is no data to resolve.
 *
 * Each such field is reported in `unresolvedFields` with a reason, so the form shows an empty input the
 * officer must complete rather than a confident-looking guess. A wrong address or account identifier on
 * an appeal record is a defect in a legal document — an honest blank is strictly better.
 *
 * MASKING INTERACTION: autofill returns RAW values, not masked ones. maskAddress() collapses to the
 * literal "[address hidden]" and maskAccountNumber() to "1234****5678"; persisting either as the
 * appeal's address or account number would write the mask into the record as though it were data. The
 * masked view is a presentation concern applied when reading an appeal back out.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaAppealAutofillService {

    /** Intake channel to Mode of Receipt (story 10). Derived server-side from the channel, not sent. */
    public static final String MODE_PORTAL = "PORTAL";
    public static final String MODE_RE_PNO = "RE_PNO";
    public static final String MODE_AA_MANUAL = "AA_MANUAL";

    private final RegulatedEntityRepository regulatedEntityRepository;
    private final BankRepository bankRepository;
    private final PincodeRepository pincodeRepository;

    /**
     * A field that could not be auto-filled, and why.
     *
     * The reason is a translation KEY plus the field name, so the UI can explain the blank in the
     * user's language instead of showing an unexplained empty box.
     */
    public record UnresolvedField(String field, String reasonKey) {}

    public record AutofillResult(Map<String, Object> complainant,
                                 Map<String, Object> entity,
                                 String modeOfReceipt,
                                 List<UnresolvedField> unresolvedFields) {}

    public AutofillResult buildFrom(Complaint parent, String modeOfReceipt) {
        Map<String, Object> complainant = new LinkedHashMap<>();
        Map<String, Object> entity = new LinkedHashMap<>();
        List<UnresolvedField> unresolved = new ArrayList<>();

        // ── Complainant block (story 8) ──────────────────────────────────────────────
        complainant.put("appellantName", parent.getComplainantName());
        complainant.put("appellantEmail", parent.getComplainantEmail());
        complainant.put("appellantPhone", parent.getComplainantPhone());
        complainant.put("appellantAddress1", parent.getComplainantAddress());
        // Address line 2 does not exist on Complaint; a single 500-char address is stored. Splitting it
        // on a comma would invent a structure the citizen never entered.
        complainant.put("appellantAddress2", null);
        complainant.put("appellantState", parent.getComplainantState());
        complainant.put("appellantDistrict", parent.getComplainantDistrict());
        complainant.put("categoryId", parent.getCategoryId());

        // City / country / pincode: no source. Left null and flagged.
        complainant.put("appellantCity", null);
        complainant.put("appellantCountry", null);
        complainant.put("appellantPincode", null);
        unresolved.add(new UnresolvedField("appellantCity", "aa.register.unresolved_no_source"));
        unresolved.add(new UnresolvedField("appellantCountry", "aa.register.unresolved_no_source"));
        unresolved.add(new UnresolvedField("appellantPincode", "aa.register.unresolved_no_source"));

        // ── Entity block (story 9) ───────────────────────────────────────────────────
        String entityCode = parent.getEntityCode();
        entity.put("entityCode", entityCode);
        entity.put("entityBranch", parent.getBankBranch());
        entity.put("accountNumber", parent.getAccountNumber());

        // Card number has no column on Complaint anywhere in cms-backend.
        entity.put("cardNumber", null);
        unresolved.add(new UnresolvedField("cardNumber", "aa.register.unresolved_no_source"));

        Optional<RegulatedEntity> resolved = resolveEntity(entityCode);
        entity.put("entityName", resolved.map(RegulatedEntity::getName).orElse(entityCode));

        // entityType is the nearest thing to an entity category (NBFC / Cooperative Bank / ...).
        // CATEGORY_MASTER is empty, so it is not a usable source.
        String entityCategory = resolved.map(RegulatedEntity::getEntityType).orElse(null);
        entity.put("entityCategory", entityCategory);
        if (entityCategory == null) {
            unresolved.add(new UnresolvedField("entityCategory", "aa.register.unresolved_not_in_master"));
        }

        // Region: no column on REGULATED_ENTITIES. Never substituted with a postal region.
        entity.put("entityRegion", null);
        unresolved.add(new UnresolvedField("entityRegion", "aa.register.unresolved_no_source"));

        // BSR / IFSC: BANKS has no such column; banks.code is a mnemonic and must not be passed off as
        // an IFSC, which is a validated 11-character format used to move money.
        entity.put("bsrIfscCode", null);
        unresolved.add(new UnresolvedField("bsrIfscCode", "aa.register.unresolved_no_source"));

        // Nodal officer (story 9: "auto-links to the PNO for later assignment"). Schema exists but is
        // unpopulated, so this resolves only when the data is actually there.
        String nodalOfficer = resolved.map(RegulatedEntity::getNodalOfficerName)
                .filter(n -> !n.isBlank())
                .orElse(null);
        entity.put("nodalOfficerName", nodalOfficer);
        if (nodalOfficer == null) {
            unresolved.add(new UnresolvedField("nodalOfficerName", "aa.register.unresolved_not_in_master"));
        }

        return new AutofillResult(complainant, entity, modeOfReceipt, unresolved);
    }

    /**
     * Resolves COMPLAINTS.entity_code to a regulated entity.
     *
     * entity_code is dirty: it holds full names ('Punjab National Bank') for some rows and short codes
     * ('PNB', 'SBI') for others. Name-normalised matching is tried first because that is what
     * RePortalService uses and what most rows contain; the BANKS mnemonic is the fallback for
     * code-shaped values. An unmatched value yields empty rather than a wrong entity — attributing an
     * appeal to the wrong bank is worse than leaving the officer to pick.
     */
    private Optional<RegulatedEntity> resolveEntity(String entityCode) {
        if (entityCode == null || entityCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = RegulatedEntity.normalize(entityCode);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        List<RegulatedEntity> byName = regulatedEntityRepository.searchByNormalizedName(normalized);
        for (RegulatedEntity candidate : byName) {
            if (normalized.equals(candidate.getNameNormalized())) {
                return Optional.of(candidate);
            }
        }

        // Code-shaped value: translate the BANKS mnemonic to its name, then retry by name.
        Optional<Bank> bank = bankRepository.findAll().stream()
                .filter(b -> b.getCode() != null && b.getCode().equalsIgnoreCase(entityCode.trim()))
                .findFirst();
        if (bank.isPresent()) {
            String bankNormalized = RegulatedEntity.normalize(bank.get().getName());
            for (RegulatedEntity candidate : regulatedEntityRepository.searchByNormalizedName(bankNormalized)) {
                if (bankNormalized.equals(candidate.getNameNormalized())) {
                    return Optional.of(candidate);
                }
            }
        }

        return byName.isEmpty() ? Optional.empty() : Optional.of(byName.get(0));
    }
}
