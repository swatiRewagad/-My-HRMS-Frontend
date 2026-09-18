package com.hrms.cms.service;

import com.hrms.cms.entity.BankBranch;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.entity.Pincode;
import com.hrms.cms.repository.BankBranchRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.repository.PincodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves the Ombudsman office that owns a complaint. No single source of geography is present on
 * every filing path — the public wizard collects only entity state/district, while the CRPC and
 * officer screens collect an entity pincode — so the resolution walks an ordered chain and reports
 * which source won, making a wrong office traceable to its input.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintOfficeResolutionService {

    private final BankBranchRepository bankBranchRepository;
    private final PincodeRepository pincodeRepository;
    private final OfficeCodeMasterRepository officeCodeRepository;
    private final ComplaintNumberGeneratorService complaintNumberGenerator;

    public record OfficeResolution(String officeName, String officeCode,
                                   String district, String state, String source) {
    }

    public OfficeResolution resolve(Complaint complaint, String department) {
        Locality locality = resolveLocality(complaint);

        String officeName = complaintNumberGenerator.resolveOfficeName(
                department, locality.state(), locality.district());
        String officeCode = officeName == null ? null
                : officeCodeRepository.findByOfficeNameAndIsActiveTrue(officeName)
                        .map(OfficeCodeMaster::getOfficeCode)
                        .orElse(null);

        log.info("Resolved office for complaint {}: office={}, code={}, district={}, state={}, source={}",
                complaint.getComplaintNumber(), officeName, officeCode,
                locality.district(), locality.state(), locality.source());

        return new OfficeResolution(officeName, officeCode, locality.district(), locality.state(), locality.source());
    }

    private record Locality(String district, String state, String source) {
    }

    private Locality resolveLocality(Complaint c) {
        String entityPincode = trimToNull(c.getEntityPincode());
        if (entityPincode != null) {
            Locality fromBranch = fromBankBranch(c, entityPincode);
            if (fromBranch != null) {
                return fromBranch;
            }
            Locality fromPincode = fromPincodeTable(entityPincode, "ENTITY_PINCODE");
            if (fromPincode != null) {
                return fromPincode;
            }
        }

        if (trimToNull(c.getEntityState()) != null) {
            return new Locality(trimToNull(c.getEntityDistrict()), trimToNull(c.getEntityState()), "ENTITY_LOCATION");
        }

        String complainantPincode = trimToNull(c.getComplainantPincode());
        if (complainantPincode != null) {
            Locality fromPincode = fromPincodeTable(complainantPincode, "COMPLAINANT_PINCODE");
            if (fromPincode != null) {
                return fromPincode;
            }
        }

        if (trimToNull(c.getComplainantState()) != null) {
            return new Locality(trimToNull(c.getComplainantDistrict()), trimToNull(c.getComplainantState()),
                    "COMPLAINANT_LOCATION");
        }

        return new Locality(null, null, "DEFAULT");
    }

    /**
     * The branch row is preferred over the postal pincode table because it carries the district and
     * state the RE actually operates in, sourced from the IFSC registry rather than postal circles.
     */
    private Locality fromBankBranch(Complaint c, String pincode) {
        List<BankBranch> branches = List.of();
        if (c.getBankId() != null) {
            branches = bankBranchRepository.findByBankIdAndPincode(c.getBankId(), pincode);
        }
        if (branches.isEmpty() && trimToNull(c.getEntityCode()) != null) {
            branches = bankBranchRepository.findByBankCodeIgnoreCaseAndPincode(c.getEntityCode().trim(), pincode);
        }
        if (branches.isEmpty()) {
            return null;
        }
        BankBranch branch = branches.get(0);
        if (trimToNull(branch.getState()) == null) {
            return null;
        }
        return new Locality(trimToNull(branch.getDistrict()), trimToNull(branch.getState()), "BANK_BRANCH");
    }

    private Locality fromPincodeTable(String pincode, String source) {
        List<Pincode> rows = pincodeRepository.findByPincode(pincode);
        if (rows.isEmpty()) {
            return null;
        }
        Pincode row = rows.get(0);
        return new Locality(trimToNull(row.getDistrict()), trimToNull(row.getState()), source);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
